package com.mypalantir.sqlparse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.service.LLMService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM 语义对齐模块
 * 利用大语言模型对 SQL 语义进行深度理解
 */
@Component
public class LLMAlignment {

    private final LLMService llmService;
    private final Loader loader;
    private final ObjectMapper objectMapper;

    public LLMAlignment(LLMService llmService, Loader loader) {
        this.llmService = llmService;
        this.loader = loader;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 对 SQL 解析结果进行语义增强
     */
    public SemanticAlignmentResult enhanceSemantic(
            SqlParser.SqlParseResult sqlResult,
            MappingResolver.MappingAlignmentResult mappingResult,
            List<ObjectType> objectTypes) throws LLMException {
        
        try {
            // 1. 准备背景信息
            String ontologySummary = buildOntologySummary(objectTypes);
            String tableMetadata = buildTableMetadata(sqlResult);
            String mappingsInfo = buildMappingsInfo(mappingResult);

            // 2. 构建 Prompt
            String systemPrompt = buildSystemPrompt(ontologySummary, tableMetadata, mappingsInfo);
            String userPrompt = buildUserPrompt(sqlResult, mappingResult);

            // 3. 调用 LLM
            String jsonResponse = llmService.chat(systemPrompt, userPrompt);

            // 4. 解析 LLM 响应
            SemanticAlignmentResult result = parseLLMResponse(jsonResponse);

            // 5. 补充默认值和后处理
            postProcessResult(result, sqlResult);

            return result;

        } catch (LLMService.LLMException e) {
            throw new LLMException("LLM 调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new LLMException("语义对齐处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 为未映射字段推荐可能的属性
     */
    public List<PropertyRecommendation> recommendProperties(
            MappingResolver.UnmappedField field,
            List<ObjectType> objectTypes) throws LLMException {
        
        try {
            String systemPrompt = buildPropertyRecommendationPrompt(objectTypes);
            String userPrompt = buildPropertyRecommendationUserPrompt(field);

            String jsonResponse = llmService.chat(systemPrompt, userPrompt);
            return parsePropertyRecommendations(jsonResponse);

        } catch (LLMService.LLMException e) {
            throw new LLMException("LLM 调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new LLMException("属性推荐失败: " + e.getMessage(), e);
        }
    }

    /**
     * 识别 SQL 中的业务指标名称
     */
    public String identifyMetricName(SqlParser.AggregationInfo aggregation, List<ObjectType> objectTypes) throws LLMException {
        try {
            String systemPrompt = buildMetricNameIdentificationPrompt(objectTypes);
            String userPrompt = buildMetricNameUserPrompt(aggregation);

            String jsonResponse = llmService.chat(systemPrompt, userPrompt);
            return parseMetricName(jsonResponse);

        } catch (LLMService.LLMException e) {
            throw new LLMException("LLM 调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new LLMException("指标名称识别失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 Ontology 摘要
     */
    private String buildOntologySummary(List<ObjectType> objectTypes) {
        StringBuilder summary = new StringBuilder();
        summary.append("## 对象模型定义\n\n");

        for (ObjectType objectType : objectTypes) {
            summary.append(String.format("### %s (%s)\n", objectType.getDisplayName(), objectType.getName()));
            summary.append(objectType.getDescription()).append("\n\n");

            summary.append("属性列表:\n");
            for (com.mypalantir.meta.Property property : objectType.getProperties()) {
                summary.append(String.format("- %s: %s (%s)\n", 
                    property.getName(), 
                    property.getDataType(),
                    property.getDescription() != null ? property.getDescription() : "无描述"
                ));
            }
            summary.append("\n");
        }

        return summary.toString();
    }

    /**
     * 构建表元数据信息
     */
    private String buildTableMetadata(SqlParser.SqlParseResult sqlResult) {
        StringBuilder metadata = new StringBuilder();
        metadata.append("## 物理表结构\n\n");

        for (SqlParser.TableReference table : sqlResult.getTables()) {
            metadata.append(String.format("### 表: %s", table.getTableName()));
            if (table.getAlias() != null) {
                metadata.append(String.format(" (别名: %s)", table.getAlias()));
            }
            metadata.append("\n\n");
        }

        return metadata.toString();
    }

    /**
     * 构建映射关系信息
     */
    private String buildMappingsInfo(MappingResolver.MappingAlignmentResult mappingResult) {
        StringBuilder mappings = new StringBuilder();
        mappings.append("## 已识别的映射关系\n\n");

        if (!mappingResult.getFieldMappings().isEmpty()) {
            mappings.append("### 已映射字段\n");
            for (MappingResolver.FieldMapping fieldMapping : mappingResult.getFieldMappings()) {
                mappings.append(String.format("- %s → %s.%s\n", 
                    fieldMapping.getSqlField(),
                    fieldMapping.getObjectType(),
                    fieldMapping.getObjectProperty()
                ));
            }
            mappings.append("\n");
        }

        if (!mappingResult.getUnmappedFields().isEmpty()) {
            mappings.append("### 未映射字段\n");
            for (MappingResolver.UnmappedField field : mappingResult.getUnmappedFields()) {
                mappings.append(String.format("- %s (%s)\n", 
                    field.getSqlExpression(),
                    field.getFieldType()
                ));
            }
            mappings.append("\n");
        }

        return mappings.toString();
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String ontologySummary, String tableMetadata, String mappingsInfo) {
        return String.format("""
            你是一个数据指标专家，负责将 SQL 查询语句与业务模型进行语义对齐。

            ## 背景信息

            ### 物理表结构
            %s

            ### 对象模型定义
            %s

            ### 现有映射关系
            %s

            ## 任务要求

            请对 SQL 查询进行语义分析，返回 JSON 格式的分析结果：

            1. **指标识别**：识别 SQL 中的度量字段，分析其：
               - 业务含义（如 "amount" 可能是 "交易金额"、"消费金额"）
               - 推荐的指标名称
               - 聚合函数的业务意图

            2. **维度识别**：识别分组字段（GROUP BY），分析其：
               - 业务含义（如 "status" 可能是 "订单状态"、"支付状态"）
               - 是否为时间维度
               - 是否为枚举维度

            3. **时间维度检测**：分析 WHERE 条件中的时间字段：
               - 识别时间字段
               - 判断时间粒度（天/周/月/季/年）
               - 提取时间范围

            4. **条件限定识别**：分析过滤条件：
               - 判断是否为业务范围限定
               - 标准化条件表达

            5. **指标分类建议**：建议每个度量的指标类型：
               - ATOMIC：原子指标
               - DERIVED：派生指标
               - COMPOSITE：复合指标

            ## 输出格式

            ```json
            {
              "semantic_analysis": {
                "metrics": [
                  {
                    "sql_field": "SUM(t1.amount)",
                    "business_meaning": "交易金额总和",
                    "recommended_name": "交易金额",
                    "aggregation_type": "SUM",
                    "suggested_metric_type": "ATOMIC",
                    "unit": "元",
                    "confidence": 0.95
                  }
                ],
                "dimensions": [
                  {
                    "sql_field": "t1.status",
                    "business_meaning": "订单状态",
                    "is_time_dimension": false,
                    "is_enum": true,
                    "enum_values": ["已支付", "未支付", "已取消"]
                  }
                ],
                "time_analysis": {
                  "time_field": "t1.created_at",
                  "time_granularity": "day",
                  "time_range": {"start": "2024-01-01", "end": "2024-12-31"}
                },
                "filter_analysis": [
                  {
                    "field": "t1.status",
                    "operator": "=",
                    "value": "已支付",
                    "business_scope": true
                  }
                ],
                "suggested_metrics": [
                  {
                    "type": "derived",
                    "name": "日交易金额",
                    "atomic_metric_id": "交易金额",
                    "time_granularity": "day",
                    "dimensions": [],
                    "filter_conditions": {"status": "已支付"}
                  }
                ]
              }
            }
            ```

            请只返回 JSON 对象，不要包含其他解释文字。
            """, tableMetadata, ontologySummary, mappingsInfo);
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(SqlParser.SqlParseResult sqlResult, MappingResolver.MappingAlignmentResult mappingResult) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## SQL 查询语句\n\n");
        prompt.append(sqlResult.getOriginalSql());
        prompt.append("\n\n");

        // 添加解析结果的摘要
        prompt.append("## 解析结果摘要\n\n");
        prompt.append(String.format("- 涉及的表: %s\n", 
            sqlResult.getTables().stream()
                .map(t -> t.getTableName() + (t.getAlias() != null ? "(" + t.getAlias() + ")" : ""))
                .collect(Collectors.joining(", "))
        ));

        if (!sqlResult.getAggregations().isEmpty()) {
            prompt.append("- 聚合字段: ");
            for (SqlParser.AggregationInfo agg : sqlResult.getAggregations()) {
                prompt.append(String.format("%s(%s) ", agg.getType(), agg.getField()));
            }
            prompt.append("\n");
        }

        if (!sqlResult.getGroupByFields().isEmpty()) {
            prompt.append(String.format("- 分组字段: %s\n", 
                String.join(", ", sqlResult.getGroupByFields())
            ));
        }

        if (!sqlResult.getWhereConditions().isEmpty()) {
            prompt.append("- 过滤条件: ");
            for (SqlParser.WhereCondition cond : sqlResult.getWhereConditions()) {
                prompt.append(String.format("%s %s %s ", cond.getField(), cond.getOperator(), cond.getValue()));
            }
            prompt.append("\n");
        }

        return prompt.toString();
    }

    /**
     * 构建属性推荐提示词
     */
    private String buildPropertyRecommendationPrompt(List<ObjectType> objectTypes) {
        return """
            你是一个数据模型专家，负责为未映射的字段推荐可能的对象属性。

            ## 任务说明
            根据字段名和数据类型，推测可能的业务含义，并推荐合适的对象属性。

            ## 输出格式
            ```json
            {
              "recommendations": [
                {
                  "property_name": "属性名",
                  "object_type": "对象类型",
                  "confidence": 0.9,
                  "reason": "推荐理由"
                }
              ]
            }
            ```
            """;
    }

    /**
     * 构建属性推荐用户提示词
     */
    private String buildPropertyRecommendationUserPrompt(MappingResolver.UnmappedField field) {
        return String.format("""
            ## 未映射字段信息
            - 字段表达式: %s
            - 字段类型: %s
            - 是否聚合: %s
            - 聚合函数: %s

            请为这个字段推荐可能的对象属性。
            """, field.getSqlExpression(), field.getFieldType(), field.isAggregated(), field.getAggregationType());
    }

    /**
     * 构建指标名称识别提示词
     */
    private String buildMetricNameIdentificationPrompt(List<ObjectType> objectTypes) {
        return """
            你是一个业务分析师，负责根据聚合函数和字段含义生成标准的业务指标名称。

            ## 任务说明
            根据聚合类型和字段业务含义，生成简洁明确的指标名称。

            ## 输出格式
            ```json
            {
              "metric_name": "指标名称",
              "display_name": "显示名称",
              "unit": "单位",
              "confidence": 0.9
            }
            ```
            """;
    }

    /**
     * 构建指标名称识别用户提示词
     */
    private String buildMetricNameUserPrompt(SqlParser.AggregationInfo aggregation) {
        return String.format("""
            ## 聚合信息
            - 聚合函数: %s
            - 聚合字段: %s
            - 别名: %s
            - 原始表达式: %s

            请为这个聚合生成合适的业务指标名称。
            """, 
            aggregation.getType(), 
            aggregation.getField(), 
            aggregation.getAlias(), 
            aggregation.getExpression()
        );
    }

    /**
     * 解析 LLM 响应
     */
    private SemanticAlignmentResult parseLLMResponse(String jsonResponse) throws Exception {
        // 清理 LLM 返回的 Markdown 代码块标记
        String cleanedJson = cleanJsonResponse(jsonResponse);
        
        JsonNode rootNode = objectMapper.readTree(cleanedJson);

        SemanticAlignmentResult result = new SemanticAlignmentResult();

        // 解析 semantic_analysis
        JsonNode semanticAnalysis = rootNode.get("semantic_analysis");
        if (semanticAnalysis != null) {
            // 解析指标信息
            if (semanticAnalysis.has("metrics") && semanticAnalysis.get("metrics").isArray()) {
                List<SemanticMetric> metrics = new ArrayList<>();
                for (JsonNode metricNode : semanticAnalysis.get("metrics")) {
                    metrics.add(parseSemanticMetric(metricNode));
                }
                result.setMetrics(metrics);
            }

            // 解析维度信息
            if (semanticAnalysis.has("dimensions") && semanticAnalysis.get("dimensions").isArray()) {
                List<SemanticDimension> dimensions = new ArrayList<>();
                for (JsonNode dimensionNode : semanticAnalysis.get("dimensions")) {
                    dimensions.add(parseSemanticDimension(dimensionNode));
                }
                result.setDimensions(dimensions);
            }

            // 解析时间分析
            if (semanticAnalysis.has("time_analysis")) {
                result.setTimeAnalysis(parseTimeAnalysis(semanticAnalysis.get("time_analysis")));
            }

            // 解析过滤分析
            if (semanticAnalysis.has("filter_analysis") && semanticAnalysis.get("filter_analysis").isArray()) {
                List<FilterAnalysis> filterAnalysis = new ArrayList<>();
                for (JsonNode filterNode : semanticAnalysis.get("filter_analysis")) {
                    filterAnalysis.add(parseFilterAnalysis(filterNode));
                }
                result.setFilterAnalysis(filterAnalysis);
            }

            // 解析推荐的指标
            if (semanticAnalysis.has("suggested_metrics") && semanticAnalysis.get("suggested_metrics").isArray()) {
                List<MetricSuggestion> suggestions = new ArrayList<>();
                for (JsonNode suggestionNode : semanticAnalysis.get("suggested_metrics")) {
                    suggestions.add(parseMetricSuggestion(suggestionNode));
                }
                result.setSuggestedMetrics(suggestions);
            }
        }

        return result;
    }

    /**
     * 解析语义指标
     */
    private SemanticMetric parseSemanticMetric(JsonNode metricNode) {
        SemanticMetric metric = new SemanticMetric();
        metric.setSqlField(getText(metricNode, "sql_field"));
        metric.setBusinessMeaning(getText(metricNode, "business_meaning"));
        metric.setRecommendedName(getText(metricNode, "recommended_name"));
        metric.setAggregationType(getText(metricNode, "aggregation_type"));
        metric.setSuggestedMetricType(getText(metricNode, "suggested_metric_type"));
        metric.setUnit(getText(metricNode, "unit"));
        metric.setConfidence(getDouble(metricNode, "confidence"));
        return metric;
    }

    /**
     * 解析语义维度
     */
    private SemanticDimension parseSemanticDimension(JsonNode dimensionNode) {
        SemanticDimension dimension = new SemanticDimension();
        dimension.setSqlField(getText(dimensionNode, "sql_field"));
        dimension.setBusinessMeaning(getText(dimensionNode, "business_meaning"));
        dimension.setTimeDimension(getBoolean(dimensionNode, "is_time_dimension"));
        dimension.setEnumDimension(getBoolean(dimensionNode, "is_enum"));

        if (dimensionNode.has("enum_values") && dimensionNode.get("enum_values").isArray()) {
            List<String> enumValues = new ArrayList<>();
            for (JsonNode valueNode : dimensionNode.get("enum_values")) {
                enumValues.add(valueNode.asText());
            }
            dimension.setEnumValues(enumValues);
        }

        return dimension;
    }

    /**
     * 解析时间分析
     */
    private TimeAnalysis parseTimeAnalysis(JsonNode timeNode) {
        TimeAnalysis timeAnalysis = new TimeAnalysis();
        timeAnalysis.setTimeField(getText(timeNode, "time_field"));
        timeAnalysis.setTimeGranularity(getText(timeNode, "time_granularity"));

        if (timeNode.has("time_range")) {
            JsonNode timeRangeNode = timeNode.get("time_range");
            TimeRange timeRange = new TimeRange();
            timeRange.setStart(getText(timeRangeNode, "start"));
            timeRange.setEnd(getText(timeRangeNode, "end"));
            timeAnalysis.setTimeRange(timeRange);
        }

        return timeAnalysis;
    }

    /**
     * 解析过滤分析
     */
    private FilterAnalysis parseFilterAnalysis(JsonNode filterNode) {
        FilterAnalysis filterAnalysis = new FilterAnalysis();
        filterAnalysis.setField(getText(filterNode, "field"));
        filterAnalysis.setOperator(getText(filterNode, "operator"));
        filterAnalysis.setValue(getText(filterNode, "value"));
        filterAnalysis.setBusinessScope(getBoolean(filterNode, "business_scope"));
        return filterAnalysis;
    }

    /**
     * 解析指标建议
     */
    private MetricSuggestion parseMetricSuggestion(JsonNode suggestionNode) {
        MetricSuggestion suggestion = new MetricSuggestion();
        suggestion.setType(getText(suggestionNode, "type"));
        suggestion.setName(getText(suggestionNode, "name"));
        suggestion.setAtomicMetricId(getText(suggestionNode, "atomic_metric_id"));
        suggestion.setTimeGranularity(getText(suggestionNode, "time_granularity"));

        if (suggestionNode.has("dimensions") && suggestionNode.get("dimensions").isArray()) {
            List<String> dimensions = new ArrayList<>();
            for (JsonNode dimensionNode : suggestionNode.get("dimensions")) {
                dimensions.add(dimensionNode.asText());
            }
            suggestion.setDimensions(dimensions);
        }

        if (suggestionNode.has("filter_conditions")) {
            JsonNode conditionsNode = suggestionNode.get("filter_conditions");
            Map<String, Object> conditions = new HashMap<>();
            conditionsNode.fields().forEachRemaining(entry -> {
                conditions.put(entry.getKey(), entry.getValue().asText());
            });
            suggestion.setFilterConditions(conditions);
        }

        return suggestion;
    }

    /**
     * 解析属性推荐
     */
    private List<PropertyRecommendation> parsePropertyRecommendations(String jsonResponse) throws Exception {
        String cleanedJson = cleanJsonResponse(jsonResponse);
        JsonNode rootNode = objectMapper.readTree(cleanedJson);
        List<PropertyRecommendation> recommendations = new ArrayList<>();

        if (rootNode.has("recommendations") && rootNode.get("recommendations").isArray()) {
            for (JsonNode recNode : rootNode.get("recommendations")) {
                PropertyRecommendation rec = new PropertyRecommendation();
                rec.setPropertyName(getText(recNode, "property_name"));
                rec.setObjectType(getText(recNode, "object_type"));
                rec.setConfidence(getDouble(recNode, "confidence"));
                rec.setReason(getText(recNode, "reason"));
                recommendations.add(rec);
            }
        }

        return recommendations;
    }

    /**
     * 解析指标名称
     */
    private String parseMetricName(String jsonResponse) throws Exception {
        String cleanedJson = cleanJsonResponse(jsonResponse);
        JsonNode rootNode = objectMapper.readTree(cleanedJson);
        return getText(rootNode, "metric_name");
    }

    /**
     * 后处理结果
     */
    private void postProcessResult(SemanticAlignmentResult result, SqlParser.SqlParseResult sqlResult) {
        // 为没有推荐指标的聚合字段设置默认值
        for (SqlParser.AggregationInfo agg : sqlResult.getAggregations()) {
            if (!result.getMetrics().stream().anyMatch(m -> m.getSqlField().equals(agg.getExpression()))) {
                SemanticMetric defaultMetric = new SemanticMetric();
                defaultMetric.setSqlField(agg.getExpression());
                defaultMetric.setBusinessMeaning(agg.getField());
                defaultMetric.setRecommendedName(generateDefaultMetricName(agg));
                defaultMetric.setAggregationType(agg.getType().name());
                defaultMetric.setSuggestedMetricType("ATOMIC");
                defaultMetric.setConfidence(0.7);
                result.getMetrics().add(defaultMetric);
            }
        }

        // 为没有分析的时间条件设置默认值
        for (SqlParser.TimeCondition timeCond : sqlResult.getTimeConditions()) {
            if (result.getTimeAnalysis() == null) {
                TimeAnalysis timeAnalysis = new TimeAnalysis();
                timeAnalysis.setTimeField(timeCond.getField());
                timeAnalysis.setTimeGranularity(timeCond.getTimeGranularity());
                result.setTimeAnalysis(timeAnalysis);
            }
        }
    }

    /**
     * 生成默认指标名称
     */
    private String generateDefaultMetricName(SqlParser.AggregationInfo agg) {
        String aggType = agg.getType().name().toLowerCase();
        String fieldName = agg.getField() != null ? agg.getField() : "count";
        return aggType + "_" + fieldName;
    }

    /**
     * 辅助方法：获取文本值
     */
    private String getText(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return null;
    }

    /**
     * 辅助方法：获取布尔值
     */
    private boolean getBoolean(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asBoolean();
        }
        return false;
    }

    /**
     * 辅助方法：获取双精度值
     */
    private double getDouble(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asDouble();
        }
        return 0.0;
    }

    // ==================== 内部类定义 ====================

    public static class SemanticAlignmentResult {
        private List<SemanticMetric> metrics = new ArrayList<>();
        private List<SemanticDimension> dimensions = new ArrayList<>();
        private TimeAnalysis timeAnalysis;
        private List<FilterAnalysis> filterAnalysis = new ArrayList<>();
        private List<MetricSuggestion> suggestedMetrics = new ArrayList<>();
        private List<UnresolvedIssue> unresolvedIssues = new ArrayList<>();

        public List<SemanticMetric> getMetrics() { return metrics; }
        public void setMetrics(List<SemanticMetric> metrics) { this.metrics = metrics; }
        public List<SemanticDimension> getDimensions() { return dimensions; }
        public void setDimensions(List<SemanticDimension> dimensions) { this.dimensions = dimensions; }
        public TimeAnalysis getTimeAnalysis() { return timeAnalysis; }
        public void setTimeAnalysis(TimeAnalysis timeAnalysis) { this.timeAnalysis = timeAnalysis; }
        public List<FilterAnalysis> getFilterAnalysis() { return filterAnalysis; }
        public void setFilterAnalysis(List<FilterAnalysis> filterAnalysis) { this.filterAnalysis = filterAnalysis; }
        public List<MetricSuggestion> getSuggestedMetrics() { return suggestedMetrics; }
        public void setSuggestedMetrics(List<MetricSuggestion> suggestedMetrics) { this.suggestedMetrics = suggestedMetrics; }
        public List<UnresolvedIssue> getUnresolvedIssues() { return unresolvedIssues; }
        public void setUnresolvedIssues(List<UnresolvedIssue> unresolvedIssues) { this.unresolvedIssues = unresolvedIssues; }
    }

    public static class SemanticMetric {
        private String sqlField;
        private String businessMeaning;
        private String recommendedName;
        private String aggregationType;
        private String suggestedMetricType;
        private String unit;
        private double confidence;

        public String getSqlField() { return sqlField; }
        public void setSqlField(String sqlField) { this.sqlField = sqlField; }
        public String getBusinessMeaning() { return businessMeaning; }
        public void setBusinessMeaning(String businessMeaning) { this.businessMeaning = businessMeaning; }
        public String getRecommendedName() { return recommendedName; }
        public void setRecommendedName(String recommendedName) { this.recommendedName = recommendedName; }
        public String getAggregationType() { return aggregationType; }
        public void setAggregationType(String aggregationType) { this.aggregationType = aggregationType; }
        public String getSuggestedMetricType() { return suggestedMetricType; }
        public void setSuggestedMetricType(String suggestedMetricType) { this.suggestedMetricType = suggestedMetricType; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    public static class SemanticDimension {
        private String sqlField;
        private String businessMeaning;
        private boolean timeDimension;
        private boolean enumDimension;
        private List<String> enumValues = new ArrayList<>();

        public String getSqlField() { return sqlField; }
        public void setSqlField(String sqlField) { this.sqlField = sqlField; }
        public String getBusinessMeaning() { return businessMeaning; }
        public void setBusinessMeaning(String businessMeaning) { this.businessMeaning = businessMeaning; }
        public boolean isTimeDimension() { return timeDimension; }
        public void setTimeDimension(boolean timeDimension) { this.timeDimension = timeDimension; }
        public boolean isEnumDimension() { return enumDimension; }
        public void setEnumDimension(boolean enumDimension) { this.enumDimension = enumDimension; }
        public List<String> getEnumValues() { return enumValues; }
        public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }
    }

    public static class TimeAnalysis {
        private String timeField;
        private String timeGranularity;
        private TimeRange timeRange;

        public String getTimeField() { return timeField; }
        public void setTimeField(String timeField) { this.timeField = timeField; }
        public String getTimeGranularity() { return timeGranularity; }
        public void setTimeGranularity(String timeGranularity) { this.timeGranularity = timeGranularity; }
        public TimeRange getTimeRange() { return timeRange; }
        public void setTimeRange(TimeRange timeRange) { this.timeRange = timeRange; }
    }

    public static class TimeRange {
        private String start;
        private String end;

        public String getStart() { return start; }
        public void setStart(String start) { this.start = start; }
        public String getEnd() { return end; }
        public void setEnd(String end) { this.end = end; }
    }

    public static class FilterAnalysis {
        private String field;
        private String operator;
        private String value;
        private boolean businessScope;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public boolean isBusinessScope() { return businessScope; }
        public void setBusinessScope(boolean businessScope) { this.businessScope = businessScope; }
    }

    public static class MetricSuggestion {
        private String type;
        private String name;
        private String atomicMetricId;
        private String timeGranularity;
        private List<String> dimensions = new ArrayList<>();
        private Map<String, Object> filterConditions = new HashMap<>();

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAtomicMetricId() { return atomicMetricId; }
        public void setAtomicMetricId(String atomicMetricId) { this.atomicMetricId = atomicMetricId; }
        public String getTimeGranularity() { return timeGranularity; }
        public void setTimeGranularity(String timeGranularity) { this.timeGranularity = timeGranularity; }
        public List<String> getDimensions() { return dimensions; }
        public void setDimensions(List<String> dimensions) { this.dimensions = dimensions; }
        public Map<String, Object> getFilterConditions() { return filterConditions; }
        public void setFilterConditions(Map<String, Object> filterConditions) { this.filterConditions = filterConditions; }
    }

    public static class PropertyRecommendation {
        private String propertyName;
        private String objectType;
        private double confidence;
        private String reason;

        public String getPropertyName() { return propertyName; }
        public void setPropertyName(String propertyName) { this.propertyName = propertyName; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class UnresolvedIssue {
        private String type;
        private String description;
        private String field;
        private String suggestion;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    }

    /**
     * 清理 JSON 响应，移除 Markdown 代码块标记
     */
    private String cleanJsonResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return "{}";
        }
        
        String cleaned = jsonResponse.trim();
        
        // 移除开头的 ```json 或 ```
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        // 移除结尾的 ```
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }

    /**
     * LLM 语义对齐异常
     */
    public static class LLMException extends Exception {
        public LLMException(String message) {
            super(message);
        }

        public LLMException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
