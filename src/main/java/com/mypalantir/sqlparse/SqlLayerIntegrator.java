package com.mypalantir.sqlparse;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.service.MappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SQL层级整合器
 * 职责：整合各层解析结果，建立原子→派生→复合的依赖关系，
 *      处理公共维度，生成businessProcess映射
 */
@Component
public class SqlLayerIntegrator {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlLayerIntegrator.class);
    
    private final Loader loader;
    private final MappingService mappingService;
    
    public SqlLayerIntegrator(Loader loader, MappingService mappingService) {
        this.loader = loader;
        this.mappingService = mappingService;
    }
    
    /**
     * 整合指标结果
     */
    public static class IntegratedMetrics {
        private List<ExtractedMetric> allMetrics = new ArrayList<>();
        private Map<String, ExtractedMetric> metricMap = new HashMap<>();
        private Map<String, List<String>> dependencies = new HashMap<>();  // metricId -> [依赖的metricIds]
        private List<String> commonDimensions = new ArrayList<>();
        private Map<String, String> businessProcessMap = new HashMap<>();  // metricName -> businessProcess
        
        public List<ExtractedMetric> getAllMetrics() { return allMetrics; }
        public void setAllMetrics(List<ExtractedMetric> allMetrics) { this.allMetrics = allMetrics; }
        public Map<String, ExtractedMetric> getMetricMap() { return metricMap; }
        public void setMetricMap(Map<String, ExtractedMetric> metricMap) { this.metricMap = metricMap; }
        public Map<String, List<String>> getDependencies() { return dependencies; }
        public void setDependencies(Map<String, List<String>> dependencies) { this.dependencies = dependencies; }
        public List<String> getCommonDimensions() { return commonDimensions; }
        public void setCommonDimensions(List<String> commonDimensions) { this.commonDimensions = commonDimensions; }
        public Map<String, String> getBusinessProcessMap() { return businessProcessMap; }
        public void setBusinessProcessMap(Map<String, String> businessProcessMap) { this.businessProcessMap = businessProcessMap; }
        
        public void addMetric(ExtractedMetric metric) {
            allMetrics.add(metric);
            metricMap.put(metric.getName(), metric);
            if (metric.getId() != null) {
                metricMap.put(metric.getId(), metric);
            }
        }
    }
    
    /**
     * 整合多层指标
     */
    public IntegratedMetrics integrate(
            ReportMetricExtractor.LayeredMetrics layered,
            List<ComplexSqlStructureAnalyzer.SqlLayer> layers,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        logger.info("[integrate] 开始整合多层指标");
        
        IntegratedMetrics result = new IntegratedMetrics();
        
        // 步骤1: 收集所有指标
        logger.info("[integrate] 步骤1: 收集所有指标");
        for (ExtractedMetric metric : layered.getAllMetrics()) {
            result.addMetric(metric);
        }
        logger.info("[integrate] 收集到 {} 个指标", result.getAllMetrics().size());
        
        // 步骤2: 建立原子→派生依赖关系
        logger.info("[integrate] 步骤2: 建立原子→派生依赖关系");
        linkAtomicToDerived(layered.getAtomics(), layered.getDerived(), result);
        
        // 步骤3: 建立派生→复合依赖关系
        logger.info("[integrate] 步骤3: 建立派生→复合依赖关系");
        linkDerivedToComposite(layered.getDerived(), layered.getComposite(), result);
        
        // 步骤4: 提取公共维度
        logger.info("[integrate] 步骤4: 提取公共维度");
        extractCommonDimensions(layers, result);
        
        // 步骤5: 映射businessProcess
        logger.info("[integrate] 步骤5: 映射businessProcess");
        mapBusinessProcess(result, alignmentResult);
        
        // 步骤6: 传播businessProcess（从原子→派生→复合）
        logger.info("[integrate] 步骤6: 传播businessProcess");
        propagateBusinessProcess(result);
        
        logger.info("[integrate] 整合完成");
        return result;
    }
    
    /**
     * 建立原子→派生的依赖关系
     */
    private void linkAtomicToDerived(
            List<ExtractedMetric> atomics,
            List<ExtractedMetric> derived,
            IntegratedMetrics result) {
        
        logger.info("[linkAtomicToDerived] 开始建立原子→派生依赖");
        
        for (ExtractedMetric derivedMetric : derived) {
            // 如果派生指标已有atomicMetricId，跳过
            if (derivedMetric.getAtomicMetricId() != null) {
                logger.info("[linkAtomicToDerived] 派生指标 {} 已有atomicMetricId: {}", 
                    derivedMetric.getName(), derivedMetric.getAtomicMetricId());
                continue;
            }
            
            // 从aggregationField查找对应的原子指标
            String aggField = derivedMetric.getAggregationField();
            if (aggField != null) {
                ExtractedMetric atomicMetric = findAtomicByField(aggField, atomics);
                if (atomicMetric != null) {
                    derivedMetric.setAtomicMetricId(atomicMetric.getId());
                    
                    // 记录依赖关系
                    if (!result.getDependencies().containsKey(derivedMetric.getId())) {
                        result.getDependencies().put(derivedMetric.getId(), new ArrayList<>());
                    }
                    result.getDependencies().get(derivedMetric.getId()).add(atomicMetric.getId());
                    
                    logger.info("[linkAtomicToDerived] 派生指标 {} 关联原子指标 {}", 
                        derivedMetric.getName(), atomicMetric.getName());
                }
            }
        }
        
        logger.info("[linkAtomicToDerived] 完成，建立 {} 个派生指标的依赖", 
            result.getDependencies().size());
    }
    
    /**
     * 建立派生→复合的依赖关系
     */
    private void linkDerivedToComposite(
            List<ExtractedMetric> derived,
            List<ExtractedMetric> composite,
            IntegratedMetrics result) {
        
        logger.info("[linkDerivedToComposite] 开始建立派生→复合依赖");
        
        for (ExtractedMetric compositeMetric : composite) {
            // 如果复合指标已有baseMetricIds，跳过
            if (compositeMetric.getBaseMetricIds() != null && !compositeMetric.getBaseMetricIds().isEmpty()) {
                logger.info("[linkDerivedToComposite] 复合指标 {} 已有baseMetricIds: {}", 
                    compositeMetric.getName(), compositeMetric.getBaseMetricIds());
                continue;
            }
            
            // 从derivedFormula查找引用的派生指标
            String formula = compositeMetric.getDerivedFormula();
            if (formula != null) {
                List<String> referencedMetricIds = findReferencedDerivedMetrics(formula, derived);
                if (!referencedMetricIds.isEmpty()) {
                    compositeMetric.setBaseMetricIds(referencedMetricIds);
                    
                    // 记录依赖关系
                    result.getDependencies().put(compositeMetric.getId(), referencedMetricIds);
                    
                    logger.info("[linkDerivedToComposite] 复合指标 {} 依赖 {} 个派生指标", 
                        compositeMetric.getName(), referencedMetricIds.size());
                }
            }
        }
        
        logger.info("[linkDerivedToComposite] 完成");
    }
    
    /**
     * 提取公共维度（从GROUP BY中识别）
     */
    private void extractCommonDimensions(
            List<ComplexSqlStructureAnalyzer.SqlLayer> layers,
            IntegratedMetrics result) {
        
        logger.info("[extractCommonDimensions] 开始提取公共维度");
        
        Set<String> allDimensions = new HashSet<>();
        
        // 收集所有层级的GROUP BY字段
        for (ComplexSqlStructureAnalyzer.SqlLayer layer : layers) {
            for (String groupByField : layer.getGroupByFields()) {
                String cleanField = cleanFieldName(groupByField);
                allDimensions.add(cleanField);
                logger.info("[extractCommonDimensions] 发现维度字段: {}", cleanField);
            }
        }
        
        // 识别公共维度（出现在多个层级中的维度）
        Map<String, Integer> dimensionCount = new HashMap<>();
        for (ComplexSqlStructureAnalyzer.SqlLayer layer : layers) {
            Set<String> layerDimensions = layer.getGroupByFields().stream()
                .map(this::cleanFieldName)
                .collect(Collectors.toSet());
            
            for (String dim : layerDimensions) {
                dimensionCount.put(dim, dimensionCount.getOrDefault(dim, 0) + 1);
            }
        }
        
        // 出现次数≥2的为公共维度
        List<String> commonDimensions = dimensionCount.entrySet().stream()
            .filter(e -> e.getValue() >= 2)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        result.setCommonDimensions(commonDimensions);
        
        logger.info("[extractCommonDimensions] 提取到 {} 个公共维度: {}", 
            commonDimensions.size(), commonDimensions);
    }
    
    /**
     * 映射businessProcess
     */
    private void mapBusinessProcess(
            IntegratedMetrics result,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        logger.info("[mapBusinessProcess] 开始映射businessProcess");
        
        // 策略1: 从fieldMappings中获取
        for (ExtractedMetric metric : result.getAllMetrics()) {
            if (metric.getBusinessProcess() != null) {
                continue; // 已有businessProcess，跳过
            }
            
            // 对于原子指标，从aggregationField查找映射
            if (metric.getCategory() == ExtractedMetric.MetricCategory.ATOMIC) {
                String field = metric.getSourceSql();
                String businessProcess = findBusinessProcessFromMapping(field, alignmentResult);
                if (businessProcess != null) {
                    metric.setBusinessProcess(businessProcess);
                    result.getBusinessProcessMap().put(metric.getName(), businessProcess);
                    logger.info("[mapBusinessProcess] 原子指标 {} 映射到 businessProcess: {}", 
                        metric.getName(), businessProcess);
                }
            }
        }
        
        // 策略2: 兜底 - 使用involvedObjectTypes
        if (alignmentResult != null && 
            alignmentResult.getInvolvedObjectTypes() != null && 
            !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
            
            String defaultBusinessProcess = alignmentResult.getInvolvedObjectTypes().get(0);
            
            for (ExtractedMetric metric : result.getAllMetrics()) {
                if (metric.getBusinessProcess() == null) {
                    metric.setBusinessProcess(defaultBusinessProcess);
                    result.getBusinessProcessMap().put(metric.getName(), defaultBusinessProcess);
                    logger.info("[mapBusinessProcess] 指标 {} 使用默认 businessProcess: {}", 
                        metric.getName(), defaultBusinessProcess);
                }
            }
        }
        
        // 策略3: 最终兜底 - 从系统对象类型中选择第一个业务对象
        for (ExtractedMetric metric : result.getAllMetrics()) {
            if (metric.getBusinessProcess() == null) {
                String fallbackBusinessProcess = getFirstBusinessObjectType();
                if (fallbackBusinessProcess != null) {
                    metric.setBusinessProcess(fallbackBusinessProcess);
                    result.getBusinessProcessMap().put(metric.getName(), fallbackBusinessProcess);
                    logger.warn("[mapBusinessProcess] 指标 {} 使用兜底 businessProcess: {}", 
                        metric.getName(), fallbackBusinessProcess);
                }
            }
        }
        
        logger.info("[mapBusinessProcess] 完成，共映射 {} 个指标", result.getBusinessProcessMap().size());
    }
    
    /**
     * 传播businessProcess（从原子→派生→复合）
     */
    private void propagateBusinessProcess(IntegratedMetrics result) {
        logger.info("[propagateBusinessProcess] 开始传播businessProcess");
        
        int propagatedCount = 0;
        
        // 遍历所有指标
        for (ExtractedMetric metric : result.getAllMetrics()) {
            if (metric.getBusinessProcess() != null) {
                continue; // 已有businessProcess，跳过
            }
            
            // 策略1: 从atomicMetricId获取
            if (metric.getAtomicMetricId() != null) {
                ExtractedMetric atomicMetric = result.getMetricMap().get(metric.getAtomicMetricId());
                if (atomicMetric != null && atomicMetric.getBusinessProcess() != null) {
                    metric.setBusinessProcess(atomicMetric.getBusinessProcess());
                    propagatedCount++;
                    logger.info("[propagateBusinessProcess] 派生指标 {} 继承原子指标 {} 的 businessProcess: {}", 
                        metric.getName(), atomicMetric.getName(), metric.getBusinessProcess());
                    continue;
                }
            }
            
            // 策略2: 从baseMetricIds获取（复合指标）
            if (metric.getBaseMetricIds() != null && !metric.getBaseMetricIds().isEmpty()) {
                String firstBaseMetricId = metric.getBaseMetricIds().get(0);
                ExtractedMetric baseMetric = result.getMetricMap().get(firstBaseMetricId);
                if (baseMetric != null && baseMetric.getBusinessProcess() != null) {
                    metric.setBusinessProcess(baseMetric.getBusinessProcess());
                    propagatedCount++;
                    logger.info("[propagateBusinessProcess] 复合指标 {} 继承基础指标 {} 的 businessProcess: {}", 
                        metric.getName(), baseMetric.getName(), metric.getBusinessProcess());
                    continue;
                }
            }
            
            // 策略3: 从依赖关系获取
            List<String> dependencies = result.getDependencies().get(metric.getId());
            if (dependencies != null && !dependencies.isEmpty()) {
                for (String depId : dependencies) {
                    ExtractedMetric depMetric = result.getMetricMap().get(depId);
                    if (depMetric != null && depMetric.getBusinessProcess() != null) {
                        metric.setBusinessProcess(depMetric.getBusinessProcess());
                        propagatedCount++;
                        logger.info("[propagateBusinessProcess] 指标 {} 从依赖指标 {} 继承 businessProcess: {}", 
                            metric.getName(), depMetric.getName(), metric.getBusinessProcess());
                        break;
                    }
                }
            }
        }
        
        logger.info("[propagateBusinessProcess] 完成，传播 {} 个指标的 businessProcess", propagatedCount);
    }
    
    /**
     * 根据字段查找原子指标
     */
    private ExtractedMetric findAtomicByField(String field, List<ExtractedMetric> atomics) {
        String cleanField = cleanFieldName(field).toLowerCase();
        
        for (ExtractedMetric atomic : atomics) {
            String atomicName = atomic.getName().toLowerCase();
            String atomicSource = atomic.getSourceSql() != null ? 
                                  cleanFieldName(atomic.getSourceSql()).toLowerCase() : "";
            
            if (atomicName.equals(cleanField) || atomicSource.equals(cleanField)) {
                return atomic;
            }
        }
        
        return null;
    }
    
    /**
     * 从公式中查找引用的派生指标
     */
    private List<String> findReferencedDerivedMetrics(String formula, List<ExtractedMetric> derived) {
        List<String> metricIds = new ArrayList<>();
        
        if (formula == null) {
            return metricIds;
        }
        
        String lowerFormula = formula.toLowerCase();
        
        for (ExtractedMetric derivedMetric : derived) {
            String metricName = derivedMetric.getName().toLowerCase();
            
            // 使用单词边界匹配
            if (lowerFormula.matches(".*\\b" + metricName + "\\b.*")) {
                metricIds.add(derivedMetric.getId());
            }
        }
        
        return metricIds;
    }
    
    /**
     * 从映射中查找businessProcess
     */
    private String findBusinessProcessFromMapping(
            String field, 
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        if (alignmentResult == null || alignmentResult.getFieldMappings() == null) {
            return null;
        }
        
        String cleanField = cleanFieldName(field);
        
        for (MappingResolver.FieldMapping mapping : alignmentResult.getFieldMappings()) {
            String mappingSqlField = mapping.getSqlField();
            if (mappingSqlField != null && cleanFieldName(mappingSqlField).equalsIgnoreCase(cleanField)) {
                return mapping.getObjectType();
            }
        }
        
        return null;
    }
    
    /**
     * 获取第一个业务对象类型（过滤系统虚拟对象）
     */
    private String getFirstBusinessObjectType() {
        try {
            List<ObjectType> allTypes = loader.listObjectTypes();
            
            List<ObjectType> businessTypes = allTypes.stream()
                .filter(t -> !isSystemVirtualObject(t.getName()))
                .collect(Collectors.toList());
            
            if (!businessTypes.isEmpty()) {
                return businessTypes.get(0).getName();
            }
        } catch (Exception e) {
            logger.error("[getFirstBusinessObjectType] 获取对象类型失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 判断是否为系统虚拟对象
     */
    private boolean isSystemVirtualObject(String objectTypeName) {
        return "workspace".equals(objectTypeName) ||
               "database".equals(objectTypeName) ||
               "table".equals(objectTypeName) ||
               "column".equals(objectTypeName) ||
               "mapping".equals(objectTypeName) ||
               "AtomicMetric".equals(objectTypeName) ||
               "MetricDefinition".equals(objectTypeName);
    }
    
    /**
     * 清理字段名
     */
    private String cleanFieldName(String field) {
        if (field == null) {
            return "";
        }
        
        String cleaned = field.trim();
        
        // 去除表前缀
        if (cleaned.contains(".")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf('.') + 1);
        }
        
        // 去除反引号
        cleaned = cleaned.replaceAll("`", "");
        
        return cleaned.trim();
    }
}
