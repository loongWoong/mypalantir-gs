package com.mypalantir.sqlparse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 多维度聚合处理器
 * 职责：处理report.sql中的多支付方式、多区域等多维度聚合场景
 * 从别名模式识别维度组合，为每个维度生成独立指标
 */
@Component
public class MultiDimensionAggregationHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiDimensionAggregationHandler.class);
    
    /**
     * 维度组
     */
    public static class DimensionGroup {
        private String dimensionName;           // 维度名称（如：payment_type）
        private List<String> dimensionValues;   // 维度值（如：CASH, UNION, ALIPAY）
        private String fieldPattern;            // 字段模式（如：*SPLITMONEY）
        
        public DimensionGroup(String dimensionName, String fieldPattern) {
            this.dimensionName = dimensionName;
            this.fieldPattern = fieldPattern;
            this.dimensionValues = new ArrayList<>();
        }
        
        public String getDimensionName() { return dimensionName; }
        public void setDimensionName(String dimensionName) { this.dimensionName = dimensionName; }
        public List<String> getDimensionValues() { return dimensionValues; }
        public void setDimensionValues(List<String> dimensionValues) { this.dimensionValues = dimensionValues; }
        public String getFieldPattern() { return fieldPattern; }
        public void setFieldPattern(String fieldPattern) { this.fieldPattern = fieldPattern; }
        
        public void addDimensionValue(String value) {
            if (!dimensionValues.contains(value)) {
                dimensionValues.add(value);
            }
        }
    }
    
    /**
     * 维度化指标
     */
    public static class DimensionalizedMetric {
        private String baseMetricName;              // 基础指标名
        private Map<String, String> dimensions;     // 维度键值对
        private String metricName;                  // 维度化后的指标名
        private String sourceField;                 // 源字段名
        
        public DimensionalizedMetric(String baseMetricName) {
            this.baseMetricName = baseMetricName;
            this.dimensions = new HashMap<>();
        }
        
        public String getBaseMetricName() { return baseMetricName; }
        public void setBaseMetricName(String baseMetricName) { this.baseMetricName = baseMetricName; }
        public Map<String, String> getDimensions() { return dimensions; }
        public void setDimensions(Map<String, String> dimensions) { this.dimensions = dimensions; }
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        public String getSourceField() { return sourceField; }
        public void setSourceField(String sourceField) { this.sourceField = sourceField; }
        
        public void addDimension(String key, String value) {
            dimensions.put(key, value);
        }
    }
    
    /**
     * 识别维度组合
     * 从SELECT字段的别名模式中识别维度
     */
    public List<DimensionGroup> identifyDimensions(ComplexSqlStructureAnalyzer.SqlLayer layer) {
        logger.info("[identifyDimensions] 开始识别维度组合");
        
        List<DimensionGroup> dimensionGroups = new ArrayList<>();
        Map<String, DimensionGroup> patternGroups = new HashMap<>();
        
        // 分析SELECT字段的别名模式
        for (String selectField : layer.getSelectFields()) {
            String alias = extractAlias(selectField);
            if (alias == null) {
                continue;
            }
            
            // 识别模式：CASHSPLITMONEY, OTHERSPLITMONEY, UNIONSPLITMONEY, ...
            // 提取前缀（CASH, OTHER, UNION）和后缀（SPLITMONEY）
            DimensionPattern pattern = extractDimensionPattern(alias);
            if (pattern != null) {
                String key = pattern.getSuffix();
                
                if (!patternGroups.containsKey(key)) {
                    DimensionGroup group = new DimensionGroup("payment_type", pattern.getSuffix());
                    patternGroups.put(key, group);
                    logger.info("[identifyDimensions] 发现新维度模式: {}", key);
                }
                
                DimensionGroup group = patternGroups.get(key);
                group.addDimensionValue(pattern.getPrefix());
                logger.info("[identifyDimensions] 添加维度值: {} -> {}", key, pattern.getPrefix());
            }
        }
        
        dimensionGroups.addAll(patternGroups.values());
        
        logger.info("[identifyDimensions] 共识别 {} 个维度组", dimensionGroups.size());
        for (DimensionGroup group : dimensionGroups) {
            logger.info("[identifyDimensions] 维度组: name={}, pattern={}, values={}", 
                group.getDimensionName(), group.getFieldPattern(), group.getDimensionValues());
        }
        
        return dimensionGroups;
    }
    
    /**
     * 维度模式
     */
    private static class DimensionPattern {
        private String prefix;      // 前缀（如：CASH）
        private String suffix;      // 后缀（如：SPLITMONEY）
        
        public DimensionPattern(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
        
        public String getPrefix() { return prefix; }
        public String getSuffix() { return suffix; }
    }
    
    /**
     * 提取维度模式
     * 规则：
     * 1. CASHSPLITMONEY → prefix=CASH, suffix=SPLITMONEY
     * 2. CASHOPSPLITMONEY → prefix=CASHOP, suffix=SPLITMONEY
     * 3. UNIONTOLLMONEY → prefix=UNION, suffix=TOLLMONEY
     */
    private DimensionPattern extractDimensionPattern(String alias) {
        if (alias == null || alias.isEmpty()) {
            return null;
        }
        
        String upper = alias.toUpperCase();
        
        // 定义已知的后缀模式
        String[] suffixPatterns = {
            "SPLITMONEY", "TOLLMONEY", "RETURNMONEY", 
            "OPSPLITMONEY", "OPTOLLMONEY"
        };
        
        for (String suffix : suffixPatterns) {
            if (upper.endsWith(suffix)) {
                String prefix = upper.substring(0, upper.length() - suffix.length());
                if (!prefix.isEmpty()) {
                    return new DimensionPattern(prefix, suffix);
                }
            }
        }
        
        return null;
    }
    
    /**
     * 为每个维度组合生成独立指标
     */
    public List<ExtractedMetric> generateMetricsPerDimension(
            String baseMetricName, 
            List<DimensionGroup> dimensionGroups,
            ComplexSqlStructureAnalyzer.SqlLayer layer) {
        
        logger.info("[generateMetricsPerDimension] 开始为基础指标 {} 生成维度化指标", baseMetricName);
        
        List<ExtractedMetric> metrics = new ArrayList<>();
        
        if (dimensionGroups.isEmpty()) {
            logger.info("[generateMetricsPerDimension] 无维度组，返回空列表");
            return metrics;
        }
        
        // 为每个维度组的每个值生成指标
        for (DimensionGroup group : dimensionGroups) {
            for (String dimensionValue : group.getDimensionValues()) {
                // 构造维度化指标名：baseMetricName_dimensionValue_suffix
                String dimensionalizedName = (dimensionValue + group.getFieldPattern()).toLowerCase();
                
                // 从SELECT字段中查找对应的源字段
                String sourceField = findSourceField(dimensionalizedName, layer);
                
                if (sourceField != null) {
                    ExtractedMetric metric = new ExtractedMetric();
                    metric.setId(UUID.randomUUID().toString());
                    metric.setName(dimensionalizedName);
                    metric.setDisplayName(formatDisplayName(dimensionalizedName));
                    metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
                    metric.setSourceSql(sourceField);
                    metric.setDescription(String.format("维度指标: %s (%s=%s)", 
                        baseMetricName, group.getDimensionName(), dimensionValue));
                    metric.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);
                    
                    // 设置维度信息
                    List<String> dimensions = new ArrayList<>();
                    dimensions.add(group.getDimensionName());
                    metric.setDimensions(dimensions);
                    
                    // 设置过滤条件（维度值）
                    Map<String, Object> filterConditions = new HashMap<>();
                    filterConditions.put(group.getDimensionName(), dimensionValue);
                    metric.setFilterConditions(filterConditions);
                    
                    metrics.add(metric);
                    logger.info("[generateMetricsPerDimension] 生成维度指标: name={}, dimension={}={}", 
                        dimensionalizedName, group.getDimensionName(), dimensionValue);
                }
            }
        }
        
        logger.info("[generateMetricsPerDimension] 共生成 {} 个维度化指标", metrics.size());
        return metrics;
    }
    
    /**
     * 批量处理所有维度化指标
     */
    public List<ExtractedMetric> processAllDimensionalizedMetrics(
            List<ExtractedMetric> baseMetrics,
            ComplexSqlStructureAnalyzer.SqlLayer layer) {
        
        logger.info("[processAllDimensionalizedMetrics] 开始批量处理维度化指标");
        
        List<ExtractedMetric> allMetrics = new ArrayList<>();
        
        // 识别维度组
        List<DimensionGroup> dimensionGroups = identifyDimensions(layer);
        
        if (dimensionGroups.isEmpty()) {
            logger.info("[processAllDimensionalizedMetrics] 未识别到维度组，返回原始指标");
            return baseMetrics;
        }
        
        // 为每个基础指标生成维度化指标
        for (ExtractedMetric baseMetric : baseMetrics) {
            List<ExtractedMetric> dimensionalizedMetrics = 
                generateMetricsPerDimension(baseMetric.getName(), dimensionGroups, layer);
            allMetrics.addAll(dimensionalizedMetrics);
        }
        
        // 如果没有生成任何维度化指标，返回原始指标
        if (allMetrics.isEmpty()) {
            logger.info("[processAllDimensionalizedMetrics] 未生成维度化指标，返回原始指标");
            return baseMetrics;
        }
        
        logger.info("[processAllDimensionalizedMetrics] 共生成 {} 个维度化指标", allMetrics.size());
        return allMetrics;
    }
    
    /**
     * 从层级中查找源字段
     */
    private String findSourceField(String targetAlias, ComplexSqlStructureAnalyzer.SqlLayer layer) {
        String targetLower = targetAlias.toLowerCase();
        
        for (String selectField : layer.getSelectFields()) {
            String alias = extractAlias(selectField);
            if (alias != null && alias.equalsIgnoreCase(targetAlias)) {
                // 返回完整的SELECT字段表达式
                return selectField;
            }
        }
        
        // 也尝试从聚合函数中查找
        for (ComplexSqlStructureAnalyzer.AggregationInfo agg : layer.getAggregations()) {
            if (agg.getAlias() != null && agg.getAlias().equalsIgnoreCase(targetAlias)) {
                return agg.getExpression();
            }
        }
        
        return null;
    }
    
    /**
     * 提取别名
     */
    private String extractAlias(String selectField) {
        if (selectField == null) {
            return null;
        }
        
        // 匹配AS alias
        Pattern pattern = Pattern.compile("(?i)\\s+AS\\s+([\\w_]+)\\s*$");
        Matcher matcher = pattern.matcher(selectField);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
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
     * 聚合同类维度指标
     * 将多个维度化指标按基础指标分组
     */
    public Map<String, List<ExtractedMetric>> groupByBaseMetric(List<ExtractedMetric> dimensionalizedMetrics) {
        logger.info("[groupByBaseMetric] 开始聚合同类维度指标");
        
        Map<String, List<ExtractedMetric>> grouped = new HashMap<>();
        
        for (ExtractedMetric metric : dimensionalizedMetrics) {
            // 提取基础指标名（去除维度前缀）
            String baseMetricName = extractBaseMetricName(metric.getName());
            
            if (!grouped.containsKey(baseMetricName)) {
                grouped.put(baseMetricName, new ArrayList<>());
            }
            
            grouped.get(baseMetricName).add(metric);
        }
        
        logger.info("[groupByBaseMetric] 共分组 {} 个基础指标", grouped.size());
        return grouped;
    }
    
    /**
     * 提取基础指标名
     * 规则：cashsplitmoney → splitmoney
     */
    private String extractBaseMetricName(String dimensionalizedName) {
        if (dimensionalizedName == null) {
            return "";
        }
        
        String upper = dimensionalizedName.toUpperCase();
        
        // 去除已知的维度前缀
        String[] prefixes = {"CASH", "OTHER", "UNION", "ETC", "ALIPAY", "WEPAY", "CASHOP", "OTHEROP", "UNIONOP", "ETCOP", "ALIPAYOP", "WEPAYOP"};
        
        for (String prefix : prefixes) {
            if (upper.startsWith(prefix)) {
                return dimensionalizedName.substring(prefix.length()).toLowerCase();
            }
        }
        
        return dimensionalizedName;
    }
    
    /**
     * 推断维度名称
     * 根据前缀推断维度类型
     */
    public String inferDimensionName(String prefix) {
        if (prefix == null) {
            return "unknown_dimension";
        }
        
        String upper = prefix.toUpperCase();
        
        // 支付方式维度
        if (upper.matches("CASH|OTHER|UNION|ETC|ALIPAY|WEPAY")) {
            return "payment_type";
        }
        
        // 省内外维度
        if (upper.endsWith("OP")) {
            return "province_type";
        }
        
        // 业务类型维度
        if (upper.contains("SPLIT") || upper.contains("TOLL")) {
            return "business_type";
        }
        
        return "custom_dimension";
    }
    
    /**
     * 生成维度值的中文说明
     */
    public String getDimensionValueDescription(String dimensionName, String dimensionValue) {
        if ("payment_type".equals(dimensionName)) {
            switch (dimensionValue.toUpperCase()) {
                case "CASH": return "现金";
                case "OTHER": return "其他";
                case "UNION": return "银联";
                case "ETC": return "ETC";
                case "ALIPAY": return "支付宝";
                case "WEPAY": return "微信";
                default: return dimensionValue;
            }
        } else if ("province_type".equals(dimensionName)) {
            if (dimensionValue.toUpperCase().contains("OP")) {
                return "外省";
            } else {
                return "本省";
            }
        } else if ("business_type".equals(dimensionName)) {
            if (dimensionValue.toUpperCase().contains("SPLIT")) {
                return "拆账";
            } else if (dimensionValue.toUpperCase().contains("TOLL")) {
                return "通行费";
            } else if (dimensionValue.toUpperCase().contains("RETURN")) {
                return "退费";
            }
        }
        
        return dimensionValue;
    }
}
