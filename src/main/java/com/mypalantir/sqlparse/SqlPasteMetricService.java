package com.mypalantir.sqlparse;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.metric.AtomicMetric;
import com.mypalantir.metric.MetricDefinition;
import com.mypalantir.service.AtomicMetricService;
import com.mypalantir.service.DatabaseMetadataService;
import com.mypalantir.service.LLMService;
import com.mypalantir.service.MetricService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SQL 粘贴指标提取主服务
 * 协调 SQL 解析、映射对齐、LLM 处理、验证和存储流程
 */
@Service
public class SqlPasteMetricService {

    private static final Logger logger = LoggerFactory.getLogger(SqlPasteMetricService.class);

    private final SqlParser sqlParser;
    private final MappingResolver mappingResolver;
    private final LLMAlignment llmAlignment;
    private final MetricValidator metricValidator;
    private final AtomicMetricService atomicMetricService;
    private final MetricService metricService;
    private final DatabaseMetadataService databaseMetadataService;
    private final LLMService llmService;
    private final Loader loader;

    public SqlPasteMetricService(
            SqlParser sqlParser,
            MappingResolver mappingResolver,
            LLMAlignment llmAlignment,
            MetricValidator metricValidator,
            AtomicMetricService atomicMetricService,
            MetricService metricService,
            DatabaseMetadataService databaseMetadataService,
            LLMService llmService,
            Loader loader) {
        this.sqlParser = sqlParser;
        this.mappingResolver = mappingResolver;
        this.llmAlignment = llmAlignment;
        this.metricValidator = metricValidator;
        this.atomicMetricService = atomicMetricService;
        this.metricService = metricService;
        this.databaseMetadataService = databaseMetadataService;
        this.llmService = llmService;
        this.loader = loader;
    }

    /**
     * 解析 SQL 并提取指标
     */
    public SqlPasteParseResult parseAndExtract(String sql, SqlPasteOptions options) {
        logger.info("开始解析 SQL: {}", sql.substring(0, Math.min(100, sql.length())));

        SqlPasteParseResult result = new SqlPasteParseResult();
        result.setOriginalSql(sql);

        try {
            // 步骤 1: SQL 语法解析
            logger.info("步骤 1: SQL 语法解析");
            SqlParser.SqlParseResult parseResult = sqlParser.parse(sql);
            result.setSqlAnalysis(parseResult);
            logger.info("解析完成，涉及 {} 个表, {} 个聚合字段", 
                parseResult.getTables().size(), 
                parseResult.getAggregations().size());

            // 步骤 2: 获取相关对象类型
            logger.info("步骤 2: 获取相关对象类型");
            List<ObjectType> relevantObjectTypes = getRelevantObjectTypes(parseResult);
            logger.info("找到 {} 个相关对象类型", relevantObjectTypes.size());

            // 步骤 3: 映射关系对齐
            logger.info("步骤 3: 映射关系对齐");
            MappingResolver.MappingAlignmentResult alignmentResult = mappingResolver.alignWithMappings(parseResult);
            result.setMappingResult(alignmentResult);
            logger.info("映射完成，已映射 {} 个字段, 未映射 {} 个字段",
                alignmentResult.getFieldMappings().size(),
                alignmentResult.getUnmappedFields().size());

            // 步骤 4: LLM 语义增强
            LLMAlignment.SemanticAlignmentResult semanticResult = null;
            if (options.isEnableLLM()) {
                logger.info("步骤 4: LLM 语义增强");
                try {
                    semanticResult = llmAlignment.enhanceSemantic(parseResult, alignmentResult, relevantObjectTypes);
                    result.setSemanticResult(semanticResult);
                    logger.info("LLM 分析完成，找到 {} 个语义指标", semanticResult.getMetrics().size());
                } catch (LLMAlignment.LLMException e) {
                    logger.warn("LLM 调用失败: {}", e.getMessage());
                    result.addSuggestion("LLM 语义分析失败，将使用基础分析结果");
                    semanticResult = createDefaultSemanticResult(parseResult);
                    result.setSemanticResult(semanticResult);
                }
            } else {
                logger.info("跳过 LLM 语义增强（已禁用）");
                semanticResult = createDefaultSemanticResult(parseResult);
                result.setSemanticResult(semanticResult);
            }

            // 步骤 5: 提取指标
            logger.info("步骤 5: 提取指标");
            List<ExtractedMetric> extractedMetrics = extractMetrics(parseResult, alignmentResult, semanticResult);
            result.setExtractedMetrics(extractedMetrics);
            logger.info("提取完成，找到 {} 个指标", extractedMetrics.size());

            // 步骤 6: 验证指标
            logger.info("步骤 6: 验证指标");
            List<MetricValidator.ValidationResult> validationResults = validateMetrics(extractedMetrics);
            result.setValidations(validationResults);
            
            int errorCount = (int) validationResults.stream()
                .filter(v -> v.hasErrors())
                .count();
            logger.info("验证完成，{} 个指标有错误", errorCount);

            // 步骤 7: 生成建议
            logger.info("步骤 7: 生成建议");
            List<String> suggestions = generateSuggestions(extractedMetrics, validationResults);
            result.setSuggestions(suggestions);

            logger.info("SQL 解析完成");

        } catch (Exception e) {
            logger.error("SQL 解析失败: {}", e.getMessage(), e);
            result.addError("PARSE_ERROR", "SQL 解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 存储提取的指标
     */
    public SaveResult saveExtractedMetrics(
            List<ExtractedMetric> metrics,
            boolean createNew,
            List<String> existingMetricIds,
            List<String> workspaceIds) {
        
        SaveResult result = new SaveResult();
        result.setSuccess(true);

        List<String> savedIds = new ArrayList<>();
        List<SaveError> errors = new ArrayList<>();

        for (int i = 0; i < metrics.size(); i++) {
            ExtractedMetric metric = metrics.get(i);
            try {
                // 保存前验证必填字段
                if (metric.getCategory() == ExtractedMetric.MetricCategory.ATOMIC) {
                    if (metric.getBusinessProcess() == null || metric.getBusinessProcess().isEmpty()) {
                        throw new IllegalArgumentException("原子指标缺少必填字段: businessProcess");
                    }
                    if (metric.getAggregationFunction() == null || metric.getAggregationFunction().isEmpty()) {
                        throw new IllegalArgumentException("原子指标缺少必填字段: aggregationFunction");
                    }
                }
                
                String savedId;

                if (metric.getCategory() == ExtractedMetric.MetricCategory.ATOMIC) {
                    savedId = saveAtomicMetric(metric, createNew, existingMetricIds, workspaceIds);
                } else {
                    savedId = saveMetricDefinition(metric, createNew, existingMetricIds, workspaceIds);
                }

                savedIds.add(savedId);
                result.getSavedMetrics().add(new SavedMetricInfo(metric.getName(), savedId, "success"));

            } catch (Exception e) {
                logger.error("保存指标失败: {}", metric.getName(), e);
                errors.add(new SaveError(metric.getName(), e.getMessage()));
                result.setSuccess(false);
            }
        }

        result.setSavedIds(savedIds);
        result.setErrors(errors);

        return result;
    }

    /**
     * 仅验证指标定义
     */
    public ValidationOnlyResult validateOnly(String sql) {
        SqlPasteParseResult parseResult = parseAndExtract(sql, new SqlPasteOptions());

        ValidationOnlyResult result = new ValidationOnlyResult();
        result.setOriginalSql(sql);
        result.setExtractedMetrics(parseResult.getExtractedMetrics());
        result.setValidations(parseResult.getValidations());
        result.setSuggestions(parseResult.getSuggestions());

        boolean allValid = parseResult.getValidations().stream()
            .allMatch(v -> !v.hasErrors());
        result.setAllValid(allValid);

        return result;
    }

    /**
     * 获取相关对象类型
     */
    private List<ObjectType> getRelevantObjectTypes(SqlParser.SqlParseResult parseResult) {
        Set<String> objectTypeNames = new HashSet<>();
        
        // 从映射关系中获取
        try {
            List<ObjectType> allTypes = loader.listObjectTypes();
            return allTypes;
        } catch (Exception e) {
            logger.warn("获取对象类型列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 创建默认的语义分析结果（当 LLM 不可用时）
     */
    private LLMAlignment.SemanticAlignmentResult createDefaultSemanticResult(SqlParser.SqlParseResult parseResult) {
        LLMAlignment.SemanticAlignmentResult result = new LLMAlignment.SemanticAlignmentResult();

        // 从聚合字段创建默认指标
        for (SqlParser.AggregationInfo agg : parseResult.getAggregations()) {
            LLMAlignment.SemanticMetric metric = new LLMAlignment.SemanticMetric();
            metric.setSqlField(agg.getExpression());
            metric.setBusinessMeaning(agg.getField());
            metric.setRecommendedName(agg.getAlias() != null ? agg.getAlias() : agg.getField());
            metric.setAggregationType(agg.getType().name());
            metric.setSuggestedMetricType("ATOMIC");
            metric.setConfidence(0.7);
            result.getMetrics().add(metric);
        }

        // 从 GROUP BY 字段创建默认维度
        for (String groupByField : parseResult.getGroupByFields()) {
            LLMAlignment.SemanticDimension dimension = new LLMAlignment.SemanticDimension();
            dimension.setSqlField(groupByField);
            dimension.setBusinessMeaning(groupByField);
            dimension.setTimeDimension(false);
            dimension.setEnumDimension(false);
            result.getDimensions().add(dimension);
        }

        return result;
    }

    /**
     * 提取指标
     */
    private List<ExtractedMetric> extractMetrics(
            SqlParser.SqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult,
            LLMAlignment.SemanticAlignmentResult semanticResult) {
        
        List<ExtractedMetric> metrics = new ArrayList<>();

        // 从语义分析结果提取指标
        if (semanticResult != null) {
            for (LLMAlignment.SemanticMetric semanticMetric : semanticResult.getMetrics()) {
                ExtractedMetric metric = createExtractedMetric(semanticMetric, parseResult, alignmentResult);
                metrics.add(metric);
            }
        }

        // 如果没有语义结果，从解析结果提取
        if (metrics.isEmpty()) {
            for (SqlParser.AggregationInfo agg : parseResult.getAggregations()) {
                ExtractedMetric metric = createExtractedMetricFromAggregation(agg, parseResult, alignmentResult);
                metrics.add(metric);
            }
        }

        // 处理推荐的派生指标
        if (semanticResult != null && semanticResult.getSuggestedMetrics() != null) {
            for (LLMAlignment.MetricSuggestion suggestion : semanticResult.getSuggestedMetrics()) {
                ExtractedMetric metric = createExtractedMetricFromSuggestion(suggestion, metrics);
                if (metric != null) {
                    metrics.add(metric);
                }
            }
        }

        return metrics;
    }

    /**
     * 从语义指标创建提取的指标
     */
    private ExtractedMetric createExtractedMetric(
            LLMAlignment.SemanticMetric semanticMetric,
            SqlParser.SqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        ExtractedMetric metric = new ExtractedMetric();
        metric.setId(UUID.randomUUID().toString());
        metric.setName(generateMetricName(semanticMetric));
        metric.setDisplayName(semanticMetric.getRecommendedName());
        metric.setDescription(semanticMetric.getBusinessMeaning());
        metric.setSourceSql(semanticMetric.getSqlField());

        // 确定指标类型
        String suggestedType = semanticMetric.getSuggestedMetricType();
        if ("ATOMIC".equalsIgnoreCase(suggestedType)) {
            metric.setCategory(ExtractedMetric.MetricCategory.ATOMIC);
        } else if ("DERIVED".equalsIgnoreCase(suggestedType)) {
            metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
        } else if ("COMPOSITE".equalsIgnoreCase(suggestedType)) {
            metric.setCategory(ExtractedMetric.MetricCategory.COMPOSITE);
        } else {
            metric.setCategory(determineMetricCategory(parseResult, semanticMetric));
        }

        metric.setConfidence(convertConfidence(semanticMetric.getConfidence()));
        metric.setUnit(semanticMetric.getUnit());

        // 设置原子指标属性
        if (metric.getCategory() == ExtractedMetric.MetricCategory.ATOMIC) {
            setAtomicMetricProperties(metric, semanticMetric, alignmentResult);
        }

        // 设置派生指标属性
        if (metric.getCategory() == ExtractedMetric.MetricCategory.DERIVED) {
            setDerivedMetricProperties(metric, semanticMetric, parseResult);
        }

        return metric;
    }

    /**
     * 从聚合信息创建提取的指标
     */
    private ExtractedMetric createExtractedMetricFromAggregation(
            SqlParser.AggregationInfo agg,
            SqlParser.SqlParseResult parseResult,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        ExtractedMetric metric = new ExtractedMetric();
        metric.setId(UUID.randomUUID().toString());
        metric.setName(agg.getAlias() != null ? agg.getAlias() : agg.getField());
        metric.setDisplayName(agg.getField());
        metric.setDescription("从 SQL 提取的指标: " + agg.getExpression());
        metric.setSourceSql(agg.getExpression());
        metric.setCategory(ExtractedMetric.MetricCategory.ATOMIC);
        metric.setConfidence(ExtractedMetric.ConfidenceLevel.MEDIUM);

        setAtomicMetricPropertiesFromAggregation(metric, agg, alignmentResult);

        return metric;
    }

    /**
     * 从建议创建提取的指标
     */
    private ExtractedMetric createExtractedMetricFromSuggestion(
            LLMAlignment.MetricSuggestion suggestion,
            List<ExtractedMetric> existingMetrics) {
        
        // 查找对应的原子指标
        String atomicMetricId = suggestion.getAtomicMetricId();
        ExtractedMetric atomicMetric = findAtomicMetricByName(existingMetrics, atomicMetricId);

        if (atomicMetric == null) {
            return null;
        }

        ExtractedMetric metric = new ExtractedMetric();
        metric.setId(UUID.randomUUID().toString());
        metric.setName(suggestion.getName());
        metric.setDisplayName(suggestion.getName());
        metric.setCategory("derived".equalsIgnoreCase(suggestion.getType()) ? ExtractedMetric.MetricCategory.DERIVED : ExtractedMetric.MetricCategory.COMPOSITE);
        metric.setAtomicMetricId(atomicMetric.getId());
        metric.setTimeGranularity(suggestion.getTimeGranularity());
        metric.setDimensions(suggestion.getDimensions());
        metric.setFilterConditions(suggestion.getFilterConditions());
        metric.setConfidence(ExtractedMetric.ConfidenceLevel.MEDIUM);

        return metric;
    }

    /**
     * 设置原子指标属性
     */
    private void setAtomicMetricProperties(
            ExtractedMetric metric,
            LLMAlignment.SemanticMetric semanticMetric,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        metric.setAggregationFunction(convertAggregationType(semanticMetric.getAggregationType()));

        // 尝试从映射中获取业务过程
        boolean foundMapping = false;
        for (MappingResolver.FieldMapping fieldMapping : alignmentResult.getFieldMappings()) {
            if (fieldMapping.getSqlField().equals(semanticMetric.getSqlField())) {
                metric.setBusinessProcess(fieldMapping.getObjectType());
                metric.setAggregationField(fieldMapping.getObjectProperty());
                foundMapping = true;
                break;
            }
        }
        
        // 兜底策略1: 使用第一个涉及的对象类型
        if (!foundMapping && alignmentResult.getInvolvedObjectTypes() != null 
                && !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
            metric.setBusinessProcess(alignmentResult.getInvolvedObjectTypes().get(0));
            logger.warn("指标 {} 未找到精确映射,使用第一个对象类型: {}", 
                metric.getName(), metric.getBusinessProcess());
            foundMapping = true;
        }
        
        // 兜底策略2: 如果映射完全失败,尝试从所有对象类型中查找第一个
        if (!foundMapping) {
            try {
                List<ObjectType> allTypes = loader.listObjectTypes();
                if (!allTypes.isEmpty()) {
                    metric.setBusinessProcess(allTypes.get(0).getName());
                    logger.error("指标 {} 映射完全失败,使用系统第一个对象类型: {}. 请检查数据映射配置!", 
                        metric.getName(), metric.getBusinessProcess());
                }
            } catch (Exception e) {
                logger.error("无法获取对象类型列表: {}", e.getMessage());
            }
        }
    }

    /**
     * 从聚合信息设置原子指标属性
     */
    private void setAtomicMetricPropertiesFromAggregation(
            ExtractedMetric metric,
            SqlParser.AggregationInfo agg,
            MappingResolver.MappingAlignmentResult alignmentResult) {
        
        metric.setAggregationFunction(agg.getType().name());
        metric.setAggregationField(agg.getField());

        // 尝试从映射中获取业务过程
        boolean foundMapping = false;
        for (MappingResolver.FieldMapping fieldMapping : alignmentResult.getFieldMappings()) {
            if (fieldMapping.getSqlField().contains(agg.getField())) {
                metric.setBusinessProcess(fieldMapping.getObjectType());
                foundMapping = true;
                break;
            }
        }
        
        // 兜底策略1: 使用第一个涉及的对象类型
        if (!foundMapping && alignmentResult.getInvolvedObjectTypes() != null 
                && !alignmentResult.getInvolvedObjectTypes().isEmpty()) {
            metric.setBusinessProcess(alignmentResult.getInvolvedObjectTypes().get(0));
            logger.warn("指标 {} 未找到精确映射,使用第一个对象类型: {}", 
                metric.getName(), metric.getBusinessProcess());
            foundMapping = true;
        }
        
        // 兜底策略2: 如果映射完全失败,尝试从所有对象类型中查找第一个
        if (!foundMapping) {
            try {
                List<ObjectType> allTypes = loader.listObjectTypes();
                if (!allTypes.isEmpty()) {
                    metric.setBusinessProcess(allTypes.get(0).getName());
                    logger.error("指标 {} 映射完全失败,使用系统第一个对象类型: {}. 请检查数据映射配置!", 
                        metric.getName(), metric.getBusinessProcess());
                }
            } catch (Exception e) {
                logger.error("无法获取对象类型列表: {}", e.getMessage());
            }
        }
    }

    /**
     * 设置派生指标属性
     */
    private void setDerivedMetricProperties(
            ExtractedMetric metric,
            LLMAlignment.SemanticMetric semanticMetric,
            SqlParser.SqlParseResult parseResult) {
        
        // 从语义分析获取时间信息
        metric.setTimeDimension("created_at");
        metric.setTimeGranularity("day");

        // 从 WHERE 条件提取过滤条件
        if (!parseResult.getWhereConditions().isEmpty()) {
            Map<String, Object> filterConditions = new HashMap<>();
            for (SqlParser.WhereCondition cond : parseResult.getWhereConditions()) {
                if (!isTimeCondition(cond)) {
                    filterConditions.put(cond.getField(), cond.getValue());
                }
            }
            if (!filterConditions.isEmpty()) {
                metric.setFilterConditions(filterConditions);
            }
        }
    }

    /**
     * 验证指标
     */
    private List<MetricValidator.ValidationResult> validateMetrics(List<ExtractedMetric> metrics) {
        return metrics.stream()
            .map(metricValidator::validate)
            .collect(Collectors.toList());
    }

    /**
     * 生成建议
     */
    private List<String> generateSuggestions(
            List<ExtractedMetric> metrics,
            List<MetricValidator.ValidationResult> validations) {
        
        List<String> suggestions = new ArrayList<>();

        // 检查是否有未映射的字段
        if (metrics.stream().anyMatch(m -> m.getConfidence() == ExtractedMetric.ConfidenceLevel.LOW)) {
            suggestions.add("部分字段未能自动映射，建议检查映射关系配置");
        }

        // 检查是否有错误
        long errorCount = validations.stream()
            .filter(v -> v.hasErrors())
            .count();
        if (errorCount > 0) {
            suggestions.add(String.format("%d 个指标存在验证错误，请修正后保存", errorCount));
        }

        // 检查指标完整性
        long incompleteCount = metrics.stream()
            .filter(m -> m.getName() == null || m.getBusinessProcess() == null)
            .count();
        if (incompleteCount > 0) {
            suggestions.add(String.format("%d 个指标信息不完整，建议补充指标名称和业务过程", incompleteCount));
        }

        if (suggestions.isEmpty()) {
            suggestions.add("所有指标验证通过，可以保存");
        }

        return suggestions;
    }

    /**
     * 保存原子指标
     */
    private String saveAtomicMetric(
            ExtractedMetric metric,
            boolean createNew,
            List<String> existingMetricIds,
            List<String> workspaceIds) throws Exception {
        
        AtomicMetric atomic = metricValidator.convertToAtomicMetric(metric);

        if (!createNew && !existingMetricIds.isEmpty()) {
            String existingId = existingMetricIds.stream()
                .filter(id -> {
                    try {
                        return metric.getName().equals(atomicMetricService.getAtomicMetric(id).getName());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .findFirst()
                .orElse(null);

            if (existingId != null) {
                atomicMetricService.updateAtomicMetric(existingId, atomic);
                return existingId;
            }
        }

        return atomicMetricService.createAtomicMetric(atomic, workspaceIds);
    }

    /**
     * 保存指标定义
     */
    private String saveMetricDefinition(
            ExtractedMetric metric,
            boolean createNew,
            List<String> existingMetricIds,
            List<String> workspaceIds) throws Exception {
        
        MetricDefinition definition = metricValidator.convertToMetricDefinition(metric);

        if (!createNew && !existingMetricIds.isEmpty()) {
            String existingId = existingMetricIds.stream()
                .filter(id -> {
                    try {
                        return metric.getName().equals(metricService.getMetricDefinition(id).getName());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .findFirst()
                .orElse(null);

            if (existingId != null) {
                metricService.updateMetricDefinition(existingId, definition);
                return existingId;
            }
        }

        return metricService.createMetricDefinition(definition, workspaceIds);
    }

    /**
     * 生成指标名称
     */
    private String generateMetricName(LLMAlignment.SemanticMetric semanticMetric) {
        String name = semanticMetric.getRecommendedName();
        if (name == null || name.isEmpty()) {
            name = semanticMetric.getBusinessMeaning();
        }
        return name != null ? name.toLowerCase().replaceAll("\\s+", "_") : "metric";
    }

    /**
     * 确定指标类型
     */
    private ExtractedMetric.MetricCategory determineMetricCategory(
            SqlParser.SqlParseResult parseResult,
            LLMAlignment.SemanticMetric semanticMetric) {
        
        boolean hasTimeCondition = !parseResult.getTimeConditions().isEmpty();
        boolean hasFilterCondition = !parseResult.getWhereConditions().isEmpty();

        if (hasTimeCondition || hasFilterCondition) {
            return ExtractedMetric.MetricCategory.DERIVED;
        }
        return ExtractedMetric.MetricCategory.ATOMIC;
    }

    /**
     * 转换聚合类型
     */
    private String convertAggregationType(String llmType) {
        if (llmType == null) return "SUM";
        switch (llmType.toUpperCase()) {
            case "SUM": return "SUM";
            case "AVG": return "AVG";
            case "COUNT": return "COUNT";
            case "MAX": return "MAX";
            case "MIN": return "MIN";
            default: return "SUM";
        }
    }

    /**
     * 转换置信度
     */
    private ExtractedMetric.ConfidenceLevel convertConfidence(double llmConfidence) {
        if (llmConfidence >= 0.8) return ExtractedMetric.ConfidenceLevel.HIGH;
        if (llmConfidence >= 0.5) return ExtractedMetric.ConfidenceLevel.MEDIUM;
        return ExtractedMetric.ConfidenceLevel.LOW;
    }

    /**
     * 判断是否为时间条件
     */
    private boolean isTimeCondition(SqlParser.WhereCondition cond) {
        String field = cond.getField().toLowerCase();
        return field.contains("time") || field.contains("date") || 
               field.contains("created_at") || field.contains("updated_at");
    }

    /**
     * 根据名称查找原子指标
     */
    private ExtractedMetric findAtomicMetricByName(List<ExtractedMetric> metrics, String name) {
        return metrics.stream()
            .filter(m -> m.getCategory() == ExtractedMetric.MetricCategory.ATOMIC)
            .filter(m -> name != null && (m.getName().equalsIgnoreCase(name) || 
                   m.getDisplayName() != null && m.getDisplayName().equalsIgnoreCase(name)))
            .findFirst()
            .orElse(null);
    }

    // ==================== 内部类定义 ====================

    public static class SqlPasteParseResult {
        private String originalSql;
        private SqlParser.SqlParseResult sqlAnalysis;
        private List<ExtractedMetric> extractedMetrics = new ArrayList<>();
        private List<MetricValidator.ValidationResult> validations = new ArrayList<>();
        private MappingResolver.MappingAlignmentResult mappingResult;
        private LLMAlignment.SemanticAlignmentResult semanticResult;
        private List<String> suggestions = new ArrayList<>();
        private List<String> errors = new ArrayList<>();

        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public SqlParser.SqlParseResult getSqlAnalysis() { return sqlAnalysis; }
        public void setSqlAnalysis(SqlParser.SqlParseResult sqlAnalysis) { this.sqlAnalysis = sqlAnalysis; }
        public List<ExtractedMetric> getExtractedMetrics() { return extractedMetrics; }
        public void setExtractedMetrics(List<ExtractedMetric> extractedMetrics) { this.extractedMetrics = extractedMetrics; }
        public List<MetricValidator.ValidationResult> getValidations() { return validations; }
        public void setValidations(List<MetricValidator.ValidationResult> validations) { this.validations = validations; }
        public MappingResolver.MappingAlignmentResult getMappingResult() { return mappingResult; }
        public void setMappingResult(MappingResolver.MappingAlignmentResult mappingResult) { this.mappingResult = mappingResult; }
        public LLMAlignment.SemanticAlignmentResult getSemanticResult() { return semanticResult; }
        public void setSemanticResult(LLMAlignment.SemanticAlignmentResult semanticResult) { this.semanticResult = semanticResult; }
        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        public void addError(String code, String message) { errors.add(code + ": " + message); }
        public void addSuggestion(String suggestion) { suggestions.add(suggestion); }
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    public static class SqlPasteOptions {
        private boolean enableLLM = true;
        private boolean suggestMetrics = true;
        private String workspaceId;

        public boolean isEnableLLM() { return enableLLM; }
        public void setEnableLLM(boolean enableLLM) { this.enableLLM = enableLLM; }
        public boolean isSuggestMetrics() { return suggestMetrics; }
        public void setSuggestMetrics(boolean suggestMetrics) { this.suggestMetrics = suggestMetrics; }
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    }

    public static class SaveResult {
        private boolean success;
        private List<String> savedIds = new ArrayList<>();
        private List<SavedMetricInfo> savedMetrics = new ArrayList<>();
        private List<SaveError> errors = new ArrayList<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public List<String> getSavedIds() { return savedIds; }
        public void setSavedIds(List<String> savedIds) { this.savedIds = savedIds; }
        public List<SavedMetricInfo> getSavedMetrics() { return savedMetrics; }
        public void setSavedMetrics(List<SavedMetricInfo> savedMetrics) { this.savedMetrics = savedMetrics; }
        public List<SaveError> getErrors() { return errors; }
        public void setErrors(List<SaveError> errors) { this.errors = errors; }
    }

    public static class SavedMetricInfo {
        private String metricName;
        private String savedId;
        private String status;

        public SavedMetricInfo(String metricName, String savedId, String status) {
            this.metricName = metricName;
            this.savedId = savedId;
            this.status = status;
        }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        public String getSavedId() { return savedId; }
        public void setSavedId(String savedId) { this.savedId = savedId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class SaveError {
        private String metricName;
        private String message;

        public SaveError(String metricName, String message) {
            this.metricName = metricName;
            this.message = message;
        }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class ValidationOnlyResult {
        private String originalSql;
        private List<ExtractedMetric> extractedMetrics = new ArrayList<>();
        private List<MetricValidator.ValidationResult> validations = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private boolean allValid;

        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public List<ExtractedMetric> getExtractedMetrics() { return extractedMetrics; }
        public void setExtractedMetrics(List<ExtractedMetric> extractedMetrics) { this.extractedMetrics = extractedMetrics; }
        public List<MetricValidator.ValidationResult> getValidations() { return validations; }
        public void setValidations(List<MetricValidator.ValidationResult> validations) { this.validations = validations; }
        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        public boolean isAllValid() { return allValid; }
        public void setAllValid(boolean allValid) { this.allValid = allValid; }
    }
}
