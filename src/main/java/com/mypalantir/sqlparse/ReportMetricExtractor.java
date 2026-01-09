package com.mypalantir.sqlparse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表指标提取器
 * 职责：针对多层报表SQL进行分层指标提取（原子→派生→复合）
 */
@Component
public class ReportMetricExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportMetricExtractor.class);
    
    private final CaseExpressionParser caseExpressionParser;
    
    public ReportMetricExtractor(CaseExpressionParser caseExpressionParser) {
        this.caseExpressionParser = caseExpressionParser;
    }
    
    /**
     * 分层指标结果
     */
    public static class LayeredMetrics {
        private List<ExtractedMetric> atomics = new ArrayList<>();
        private List<ExtractedMetric> derived = new ArrayList<>();
        private List<ExtractedMetric> composite = new ArrayList<>();
        private Map<String, ExtractedMetric> metricMap = new HashMap<>(); // name -> metric
        
        public List<ExtractedMetric> getAtomics() { return atomics; }
        public void setAtomics(List<ExtractedMetric> atomics) { this.atomics = atomics; }
        public List<ExtractedMetric> getDerived() { return derived; }
        public void setDerived(List<ExtractedMetric> derived) { this.derived = derived; }
        public List<ExtractedMetric> getComposite() { return composite; }
        public void setComposite(List<ExtractedMetric> composite) { this.composite = composite; }
        public Map<String, ExtractedMetric> getMetricMap() { return metricMap; }
        public void setMetricMap(Map<String, ExtractedMetric> metricMap) { this.metricMap = metricMap; }
        
        public void addMetric(ExtractedMetric metric) {
            if (metric.getCategory() == ExtractedMetric.MetricCategory.ATOMIC) {
                atomics.add(metric);
            } else if (metric.getCategory() == ExtractedMetric.MetricCategory.DERIVED) {
                derived.add(metric);
            } else if (metric.getCategory() == ExtractedMetric.MetricCategory.COMPOSITE) {
                composite.add(metric);
            }
            metricMap.put(metric.getName(), metric);
        }
        
        public List<ExtractedMetric> getAllMetrics() {
            List<ExtractedMetric> all = new ArrayList<>();
            all.addAll(atomics);
            all.addAll(derived);
            all.addAll(composite);
            return all;
        }
    }
    
    /**
     * 分层提取指标
     */
    public LayeredMetrics extractByLayer(List<ComplexSqlStructureAnalyzer.SqlLayer> layers) {
        logger.info("[extractByLayer] 开始分层提取指标，总层数: {}", layers.size());
        
        LayeredMetrics result = new LayeredMetrics();
        
        if (layers.isEmpty()) {
            logger.warn("[extractByLayer] 无可用层级");
            return result;
        }
        
        // 按深度排序（从内到外）
        List<ComplexSqlStructureAnalyzer.SqlLayer> sortedLayers = layers.stream()
            .sorted(Comparator.comparingInt(ComplexSqlStructureAnalyzer.SqlLayer::getDepth).reversed())
            .collect(Collectors.toList());
        
        // 策略调整：对于report.sql这种聚合在最外层的结构，直接从最外层提取
        ComplexSqlStructureAnalyzer.SqlLayer outermost = sortedLayers.get(sortedLayers.size() - 1);
        logger.info("[extractByLayer] 最外层(depth={})聚合数量: {}", outermost.getDepth(), outermost.getAggregations().size());
        
        if (outermost.getAggregations().size() > 0) {
            // 情况1: 最外层包含聚合，直接从外层提取
            logger.info("[extractByLayer] 使用策略：从最外层直接提取所有指标");
            return extractFromOuterLayerDirectly(outermost);
        }
        
        // 情况2: 标准分层结构（原逻辑）
        logger.info("[extractByLayer] 使用策略：标准分层提取");
        
        // 第1步：从最内层提取原子指标
        ComplexSqlStructureAnalyzer.SqlLayer innermost = sortedLayers.get(0);
        logger.info("[extractByLayer] 步骤1: 从最内层(depth={})提取原子指标", innermost.getDepth());
        List<ExtractedMetric> atomics = extractAtomicMetrics(innermost);
        logger.info("[extractByLayer] 提取到 {} 个原子指标", atomics.size());
        atomics.forEach(result::addMetric);
        
        // 第2步：从中间层提取派生指标
        if (sortedLayers.size() > 1) {
            for (int i = 1; i < sortedLayers.size() - 1; i++) {
                ComplexSqlStructureAnalyzer.SqlLayer middleLayer = sortedLayers.get(i);
                logger.info("[extractByLayer] 步骤2: 从中间层(depth={})提取派生指标", middleLayer.getDepth());
                List<ExtractedMetric> derivedMetrics = extractDerivedMetrics(middleLayer, result);
                logger.info("[extractByLayer] 提取到 {} 个派生指标", derivedMetrics.size());
                derivedMetrics.forEach(result::addMetric);
            }
        }
        
        // 第3步：从最外层提取复合指标
        if (sortedLayers.size() > 1) {
            logger.info("[extractByLayer] 步骤3: 从最外层(depth={})提取复合指标", outermost.getDepth());
            List<ExtractedMetric> compositeMetrics = extractCompositeMetrics(outermost, result);
            logger.info("[extractByLayer] 提取到 {} 个复合指标", compositeMetrics.size());
            compositeMetrics.forEach(result::addMetric);
        }
        
        logger.info("[extractByLayer] 提取完成: 原子={}, 派生={}, 复合={}", 
            result.getAtomics().size(), result.getDerived().size(), result.getComposite().size());
        
        return result;
    }
    
    /**
     * 从最外层直接提取所有指标（适用于report.sql这种复杂报表）
     */
    private LayeredMetrics extractFromOuterLayerDirectly(ComplexSqlStructureAnalyzer.SqlLayer layer) {
        logger.info("[extractFromOuterLayerDirectly] 开始从最外层直接提取");
        
        LayeredMetrics result = new LayeredMetrics();
        Set<String> addedNames = new HashSet<>();
        
        // 逐个处理聚合函数，根据别名特征判断指标类型
        for (ComplexSqlStructureAnalyzer.AggregationInfo agg : layer.getAggregations()) {
            String alias = agg.getAlias();
            if (alias == null || alias.isEmpty()) {
                continue;
            }
            
            String aliasLower = alias.toLowerCase();
            
            // 跳过已添加的指标
            if (addedNames.contains(aliasLower)) {
                continue;
            }
            
            // 判断指标类型：如果聚合字段不是*，则为派生指标；否则是原子指标
            ExtractedMetric metric = new ExtractedMetric();
            metric.setId(UUID.randomUUID().toString());
            metric.setName(aliasLower);
            metric.setDisplayName(formatDisplayName(aliasLower));
            metric.setSourceSql(agg.getExpression());
            metric.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);
            
            if (agg.getField() != null && !agg.getField().equals("*")) {
                // 派生指标：SUM(field)
                metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
                metric.setAggregationFunction(agg.getFunction());
                metric.setAggregationField(cleanFieldName(agg.getField()));
                metric.setDescription("派生指标: " + agg.getFunction() + "(" + agg.getField() + ")");
                
                logger.info("[extractFromOuterLayerDirectly] 添加派生指标: name={}, aggFunc={}, aggField={}", 
                    aliasLower, agg.getFunction(), agg.getField());
            } else {
                // 原子指标：COUNT(*)
                metric.setCategory(ExtractedMetric.MetricCategory.ATOMIC);
                metric.setAggregationFunction(agg.getFunction());
                metric.setAggregationField("*");
                metric.setDescription("原子指标: " + agg.getFunction() + "(*)");
                
                logger.info("[extractFromOuterLayerDirectly] 添加原子指标: name={}, aggFunc=COUNT(*)", aliasLower);
            }
            
            result.addMetric(metric);
            addedNames.add(aliasLower);
        }
        
        logger.info("[extractFromOuterLayerDirectly] 提取完成: 原子={}, 派生={}, 总计={}",
            result.getAtomics().size(), result.getDerived().size(), result.getAllMetrics().size());
        
        return result;
    }
    
    /**
     * 提取原子指标（最内层的聚合字段）
     */
    private List<ExtractedMetric> extractAtomicMetrics(ComplexSqlStructureAnalyzer.SqlLayer layer) {
        List<ExtractedMetric> atomics = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();
        
        logger.info("[extractAtomicMetrics] 开始提取原子指标");
        
        // 方式1: 从聚合函数中提取
        for (ComplexSqlStructureAnalyzer.AggregationInfo agg : layer.getAggregations()) {
            String field = agg.getField();
            
            // 过滤COUNT(*)
            if (field == null || field.trim().equals("*")) {
                continue;
            }
            
            // 提取实际字段名（去除表前缀和IFNULL等函数）
            String cleanField = cleanFieldName(field);
            String atomicName = cleanField.toLowerCase();
            
            if (!addedNames.contains(atomicName)) {
                ExtractedMetric atomic = new ExtractedMetric();
                atomic.setId(UUID.randomUUID().toString());
                atomic.setName(atomicName);
                atomic.setDisplayName(formatDisplayName(atomicName));
                atomic.setCategory(ExtractedMetric.MetricCategory.ATOMIC);
                atomic.setSourceSql(cleanField);
                atomic.setDescription("原子指标: " + cleanField);
                atomic.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);
                // businessProcess将在后续整合阶段设置
                
                atomics.add(atomic);
                addedNames.add(atomicName);
                logger.info("[extractAtomicMetrics] 添加原子指标: name={}, field={}", atomicName, cleanField);
            }
        }
        
        // 方式2: 从SELECT字段中提取（无聚合函数的情况）
        if (layer.getAggregations().isEmpty()) {
            for (String selectField : layer.getSelectFields()) {
                // 去除AS别名
                String field = selectField.replaceAll("(?i)\\s+AS\\s+[\\w_]+$", "").trim();
                String cleanField = cleanFieldName(field);
                String atomicName = cleanField.toLowerCase();
                
                // 跳过常量和函数
                if (atomicName.matches("\\d+|'.*'|\".*\"") || atomicName.startsWith("case")) {
                    continue;
                }
                
                if (!addedNames.contains(atomicName)) {
                    ExtractedMetric atomic = new ExtractedMetric();
                    atomic.setId(UUID.randomUUID().toString());
                    atomic.setName(atomicName);
                    atomic.setDisplayName(formatDisplayName(atomicName));
                    atomic.setCategory(ExtractedMetric.MetricCategory.ATOMIC);
                    atomic.setSourceSql(cleanField);
                    atomic.setDescription("原子指标: " + cleanField);
                    atomic.setConfidence(ExtractedMetric.ConfidenceLevel.MEDIUM);
                    
                    atomics.add(atomic);
                    addedNames.add(atomicName);
                    logger.info("[extractAtomicMetrics] 添加原子指标(非聚合): name={}, field={}", atomicName, cleanField);
                }
            }
        }
        
        logger.info("[extractAtomicMetrics] 共提取 {} 个原子指标", atomics.size());
        return atomics;
    }
    
    /**
     * 提取派生指标（中间层的聚合+条件）
     */
    private List<ExtractedMetric> extractDerivedMetrics(
            ComplexSqlStructureAnalyzer.SqlLayer layer, 
            LayeredMetrics existingMetrics) {
        
        List<ExtractedMetric> derivedMetrics = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();
        
        logger.info("[extractDerivedMetrics] 开始提取派生指标");
        
        // 方式1: 从聚合函数提取
        for (ComplexSqlStructureAnalyzer.AggregationInfo agg : layer.getAggregations()) {
            String derivedName = agg.getAlias() != null ? agg.getAlias().toLowerCase() : 
                                 (agg.getField() + "_" + agg.getFunction().toLowerCase());
            
            if (!addedNames.contains(derivedName)) {
                ExtractedMetric derived = new ExtractedMetric();
                derived.setId(UUID.randomUUID().toString());
                derived.setName(derivedName);
                derived.setDisplayName(formatDisplayName(derivedName));
                derived.setCategory(ExtractedMetric.MetricCategory.DERIVED);
                derived.setAggregationFunction(agg.getFunction());
                derived.setAggregationField(cleanFieldName(agg.getField()));
                derived.setSourceSql(agg.getExpression());
                derived.setDescription("派生指标: " + agg.getFunction() + "(" + agg.getField() + ")");
                derived.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);
                
                // 关联原子指标
                String atomicName = cleanFieldName(agg.getField()).toLowerCase();
                ExtractedMetric atomicMetric = existingMetrics.getMetricMap().get(atomicName);
                if (atomicMetric != null) {
                    derived.setAtomicMetricId(atomicMetric.getId());
                    derived.setBusinessProcess(atomicMetric.getBusinessProcess());
                    logger.info("[extractDerivedMetrics] 派生指标 {} 关联原子指标 {}", derivedName, atomicName);
                }
                
                // 提取WHERE条件作为过滤条件
                if (layer.getWhereClause() != null && !layer.getWhereClause().isEmpty()) {
                    Map<String, Object> filterConditions = parseWhereConditions(layer.getWhereClause());
                    if (!filterConditions.isEmpty()) {
                        derived.setFilterConditions(filterConditions);
                    }
                }
                
                // 提取GROUP BY作为维度
                if (!layer.getGroupByFields().isEmpty()) {
                    List<String> dimensions = layer.getGroupByFields().stream()
                        .map(this::cleanFieldName)
                        .collect(Collectors.toList());
                    derived.setDimensions(dimensions);
                }
                
                derivedMetrics.add(derived);
                addedNames.add(derivedName);
                logger.info("[extractDerivedMetrics] 添加派生指标: name={}, aggFunc={}, aggField={}", 
                    derivedName, derived.getAggregationFunction(), derived.getAggregationField());
            }
        }
        
        // 方式2: 从CASE表达式提取条件派生指标
        for (ComplexSqlStructureAnalyzer.CaseInfo caseInfo : layer.getCaseExpressions()) {
            if (caseInfo.getAlias() != null) {
                String derivedName = caseInfo.getAlias().toLowerCase();
                
                if (!addedNames.contains(derivedName)) {
                    ExtractedMetric derived = new ExtractedMetric();
                    derived.setId(UUID.randomUUID().toString());
                    derived.setName(derivedName);
                    derived.setDisplayName(formatDisplayName(derivedName));
                    derived.setCategory(ExtractedMetric.MetricCategory.DERIVED);
                    derived.setSourceSql(caseInfo.getFullExpression());
                    derived.setDescription("条件派生指标: " + derivedName);
                    derived.setConfidence(ExtractedMetric.ConfidenceLevel.MEDIUM);
                    
                    // 解析CASE条件为过滤条件
                    Map<String, Object> filterConditions = 
                        caseExpressionParser.convertToFilterConditions(caseInfo);
                    if (!filterConditions.isEmpty()) {
                        derived.setFilterConditions(filterConditions);
                    }
                    
                    derivedMetrics.add(derived);
                    addedNames.add(derivedName);
                    logger.info("[extractDerivedMetrics] 添加CASE派生指标: name={}", derivedName);
                }
            }
        }
        
        logger.info("[extractDerivedMetrics] 共提取 {} 个派生指标", derivedMetrics.size());
        return derivedMetrics;
    }
    
    /**
     * 提取复合指标（最外层的运算表达式）
     */
    private List<ExtractedMetric> extractCompositeMetrics(
            ComplexSqlStructureAnalyzer.SqlLayer layer, 
            LayeredMetrics existingMetrics) {
        
        List<ExtractedMetric> compositeMetrics = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();
        
        logger.info("[extractCompositeMetrics] 开始提取复合指标");
        
        // 从SELECT字段中识别复合运算表达式
        for (String selectField : layer.getSelectFields()) {
            // 提取别名
            String alias = extractAlias(selectField);
            String expression = selectField.replaceAll("(?i)\\s+AS\\s+[\\w_]+$", "").trim();
            
            // 判断是否为复合运算（包含算术运算符或NULLIF）
            boolean isComposite = expression.matches(".*[+\\-*/].*") || 
                                  expression.toUpperCase().contains("NULLIF") ||
                                  expression.toUpperCase().contains("COALESCE");
            
            if (isComposite && alias != null) {
                String compositeName = alias.toLowerCase();
                
                if (!addedNames.contains(compositeName)) {
                    ExtractedMetric composite = new ExtractedMetric();
                    composite.setId(UUID.randomUUID().toString());
                    composite.setName(compositeName);
                    composite.setDisplayName(formatDisplayName(compositeName));
                    composite.setCategory(ExtractedMetric.MetricCategory.COMPOSITE);
                    composite.setDerivedFormula(expression);
                    composite.setSourceSql(expression);
                    composite.setDescription("复合指标: " + expression);
                    composite.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);
                    
                    // 识别依赖的派生指标
                    List<String> baseMetricIds = findReferencedMetrics(expression, existingMetrics);
                    if (!baseMetricIds.isEmpty()) {
                        composite.setBaseMetricIds(baseMetricIds);
                        
                        // 继承业务过程
                        String firstMetricId = baseMetricIds.get(0);
                        ExtractedMetric baseMetric = findMetricById(firstMetricId, existingMetrics);
                        if (baseMetric != null && baseMetric.getBusinessProcess() != null) {
                            composite.setBusinessProcess(baseMetric.getBusinessProcess());
                        }
                    }
                    
                    compositeMetrics.add(composite);
                    addedNames.add(compositeName);
                    logger.info("[extractCompositeMetrics] 添加复合指标: name={}, formula={}, 依赖指标数={}", 
                        compositeName, expression, baseMetricIds.size());
                }
            }
        }
        
        logger.info("[extractCompositeMetrics] 共提取 {} 个复合指标", compositeMetrics.size());
        return compositeMetrics;
    }
    
    /**
     * 清理字段名（去除表前缀、IFNULL等函数）
     */
    private String cleanFieldName(String field) {
        if (field == null) {
            return "";
        }
        
        String cleaned = field.trim();
        
        // 去除IFNULL(A.field, B.field) → field
        if (cleaned.toUpperCase().startsWith("IFNULL")) {
            cleaned = cleaned.replaceAll("(?i)IFNULL\\s*\\(\\s*([^,]+)\\s*,.*\\)", "$1");
        }
        
        // 去除表前缀 A.field → field
        if (cleaned.contains(".")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf('.') + 1);
        }
        
        // 去除反引号
        cleaned = cleaned.replaceAll("`", "");
        
        return cleaned.trim();
    }
    
    /**
     * 格式化显示名称
     */
    private String formatDisplayName(String name) {
        if (name == null) return "";
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        return result.toString();
    }
    
    /**
     * 提取别名
     */
    private String extractAlias(String selectField) {
        if (selectField == null) return null;
        
        String[] parts = selectField.split("(?i)\\s+AS\\s+");
        if (parts.length >= 2) {
            return parts[parts.length - 1].trim();
        }
        
        return null;
    }
    
    /**
     * 解析WHERE条件
     */
    private Map<String, Object> parseWhereConditions(String whereClause) {
        Map<String, Object> conditions = new HashMap<>();
        
        if (whereClause == null || whereClause.isEmpty()) {
            return conditions;
        }
        
        // 简单解析: field = value
        String[] parts = whereClause.split("(?i)\\s+AND\\s+");
        for (String part : parts) {
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = cleanFieldName(kv[0].trim());
                    String value = kv[1].trim().replaceAll("'", "");
                    conditions.put(key, value);
                }
            }
        }
        
        return conditions;
    }
    
    /**
     * 查找表达式中引用的指标
     */
    private List<String> findReferencedMetrics(String expression, LayeredMetrics existingMetrics) {
        List<String> metricIds = new ArrayList<>();
        
        // 遍历所有已存在的指标，检查表达式中是否引用
        for (ExtractedMetric metric : existingMetrics.getAllMetrics()) {
            String metricName = metric.getName();
            // 使用单词边界匹配
            if (expression.toLowerCase().matches(".*\\b" + metricName + "\\b.*")) {
                metricIds.add(metric.getId());
            }
        }
        
        return metricIds;
    }
    
    /**
     * 根据ID查找指标
     */
    private ExtractedMetric findMetricById(String id, LayeredMetrics existingMetrics) {
        for (ExtractedMetric metric : existingMetrics.getAllMetrics()) {
            if (metric.getId().equals(id)) {
                return metric;
            }
        }
        return null;
    }
}
