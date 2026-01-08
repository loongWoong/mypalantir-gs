package com.mypalantir.controller;

import com.mypalantir.sqlparse.*;
import com.mypalantir.sqlparse.SqlPasteMetricService.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SQL 粘贴指标提取控制器
 */
@RestController
@RequestMapping("/api/v1/sql-paste")
public class SqlPasteController {

    private final SqlPasteMetricService sqlPasteMetricService;

    public SqlPasteController(SqlPasteMetricService sqlPasteMetricService) {
        this.sqlPasteMetricService = sqlPasteMetricService;
    }

    /**
     * 解析 SQL 并提取指标
     */
    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<SqlPasteParseResultDTO>> parseAndExtract(
            @RequestBody Map<String, Object> request) {
        try {
            String sql = (String) request.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "SQL 语句不能为空"));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> optionsMap = (Map<String, Object>) request.get("options");
            SqlPasteOptions options = parseOptions(optionsMap);

            SqlPasteParseResult result = sqlPasteMetricService.parseAndExtract(sql, options);
            SqlPasteParseResultDTO dto = convertToDTO(result);

            return ResponseEntity.ok(ApiResponse.success(dto));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "SQL 解析失败: " + e.getMessage()));
        }
    }

    /**
     * 存储提取的指标
     */
    @PostMapping("/save")
    public ResponseEntity<ApiResponse<SaveResultDTO>> saveMetrics(
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metricsData = (List<Map<String, Object>>) request.get("metrics");
            if (metricsData == null || metricsData.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "指标列表不能为空"));
            }

            boolean createNew = request.containsKey("createNew") && (Boolean) request.get("createNew");

            @SuppressWarnings("unchecked")
            List<String> existingMetricIds = request.containsKey("existingMetricIds") ?
                (List<String>) request.get("existingMetricIds") : Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<String> workspaceIds = request.containsKey("workspaceIds") ?
                (List<String>) request.get("workspaceIds") : Collections.emptyList();

            List<ExtractedMetric> metrics = metricsData.stream()
                .map(this::convertToExtractedMetric)
                .collect(Collectors.toList());

            SaveResult saveResult = sqlPasteMetricService.saveExtractedMetrics(
                metrics, createNew, existingMetricIds, workspaceIds);

            SaveResultDTO dto = convertToSaveResultDTO(saveResult);

            if (saveResult.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success(dto));
            } else {
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .body(ApiResponse.success(dto));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "保存指标失败: " + e.getMessage()));
        }
    }

    /**
     * 仅验证指标定义
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<ValidationOnlyResultDTO>> validateOnly(
            @RequestBody Map<String, Object> request) {
        try {
            String sql = (String) request.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "SQL 语句不能为空"));
            }

            ValidationOnlyResult result = sqlPasteMetricService.validateOnly(sql);
            ValidationOnlyResultDTO dto = convertToValidationResultDTO(result);

            return ResponseEntity.ok(ApiResponse.success(dto));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "验证失败: " + e.getMessage()));
        }
    }

    /**
     * 解析请求选项
     */
    private SqlPasteOptions parseOptions(Map<String, Object> optionsMap) {
        SqlPasteOptions options = new SqlPasteOptions();
        if (optionsMap != null) {
            if (optionsMap.containsKey("enableLLM")) {
                options.setEnableLLM((Boolean) optionsMap.get("enableLLM"));
            }
            if (optionsMap.containsKey("suggestMetrics")) {
                options.setSuggestMetrics((Boolean) optionsMap.get("suggestMetrics"));
            }
            if (optionsMap.containsKey("workspaceId")) {
                options.setWorkspaceId((String) optionsMap.get("workspaceId"));
            }
        }
        return options;
    }

    /**
     * 转换为 DTO
     */
    private SqlPasteParseResultDTO convertToDTO(SqlPasteParseResult result) {
        SqlPasteParseResultDTO dto = new SqlPasteParseResultDTO();
        dto.setOriginalSql(result.getOriginalSql());
        dto.setExtractedMetrics(convertExtractedMetrics(result.getExtractedMetrics()));
        dto.setValidations(convertValidations(result.getValidations()));
        dto.setMappingResult(convertMappingResult(result.getMappingResult()));
        dto.setSemanticResult(convertSemanticResult(result.getSemanticResult()));
        dto.setSuggestions(result.getSuggestions());
        dto.setErrors(result.getErrors());
        return dto;
    }

    /**
     * 转换提取的指标列表
     */
    private List<ExtractedMetricDTO> convertExtractedMetrics(List<ExtractedMetric> metrics) {
        return metrics.stream()
            .map(this::convertToExtractedMetricDTO)
            .collect(Collectors.toList());
    }

    /**
     * 转换单个提取的指标
     */
    private ExtractedMetricDTO convertToExtractedMetricDTO(ExtractedMetric metric) {
        ExtractedMetricDTO dto = new ExtractedMetricDTO();
        dto.setSuggestedId(metric.getId());
        dto.setName(metric.getName());
        dto.setDisplayName(metric.getDisplayName());
        dto.setDescription(metric.getDescription());
        dto.setCategory(metric.getCategory() != null ? metric.getCategory().name() : null);
        dto.setSourceSql(metric.getSourceSql());
        dto.setConfidence(metric.getConfidence() != null ? metric.getConfidence().name() : null);
        dto.setNotes(metric.getNotes());
        dto.setUnit(metric.getUnit());

        Map<String, Object> definition = new HashMap<>();
        definition.put("business_process", metric.getBusinessProcess());
        definition.put("aggregation_function", metric.getAggregationFunction());
        definition.put("aggregation_field", metric.getAggregationField());
        definition.put("atomic_metric_id", metric.getAtomicMetricId());
        definition.put("time_dimension", metric.getTimeDimension());
        definition.put("time_granularity", metric.getTimeGranularity());
        definition.put("dimensions", metric.getDimensions());
        definition.put("filter_conditions", metric.getFilterConditions());
        definition.put("comparison_type", metric.getComparisonType());
        definition.put("derived_formula", metric.getDerivedFormula());
        definition.put("base_metric_ids", metric.getBaseMetricIds());
        definition.put("status", metric.getStatus());
        dto.setDefinition(definition);

        return dto;
    }

    /**
     * 转换验证结果列表
     */
    private List<MetricValidationDTO> convertValidations(List<MetricValidator.ValidationResult> validations) {
        return validations.stream()
            .map(this::convertToValidationDTO)
            .collect(Collectors.toList());
    }

    /**
     * 转换验证结果
     */
    private MetricValidationDTO convertToValidationDTO(MetricValidator.ValidationResult result) {
        MetricValidationDTO dto = new MetricValidationDTO();
        dto.setValid(result.isValid());
        dto.setErrorCount(result.getErrors().size());
        dto.setWarningCount(result.getWarnings().size());

        dto.setErrors(result.getErrors().stream()
            .map(e -> new ValidationMessageDTO(e.getCode(), e.getMessage()))
            .collect(Collectors.toList()));

        dto.setWarnings(result.getWarnings().stream()
            .map(w -> new ValidationMessageDTO(w.getCode(), w.getMessage()))
            .collect(Collectors.toList()));

        dto.setInfos(result.getInfos().stream()
            .map(i -> new ValidationMessageDTO(i.getCode(), i.getMessage()))
            .collect(Collectors.toList()));

        return dto;
    }

    /**
     * 转换映射对齐结果
     */
    private MappingAlignmentDTO convertMappingResult(MappingResolver.MappingAlignmentResult result) {
        if (result == null) return null;

        MappingAlignmentDTO dto = new MappingAlignmentDTO();
        dto.setMappingId(result.getMappingId());

        dto.setFieldMappings(result.getFieldMappings().stream()
            .map(fm -> {
                FieldMappingDTO fmDto = new FieldMappingDTO();
                fmDto.setSqlField(fm.getSqlField());
                fmDto.setSqlTable(fm.getSqlTable());
                fmDto.setObjectProperty(fm.getObjectProperty());
                fmDto.setObjectType(fm.getObjectType());
                fmDto.setColumnName(fm.getColumnName());
                fmDto.setConfidence(fm.getConfidence() != null ? fm.getConfidence().name() : null);
                return fmDto;
            })
            .collect(Collectors.toList()));

        dto.setUnmappedFields(result.getUnmappedFields().stream()
            .map(uf -> {
                UnmappedFieldDTO ufDto = new UnmappedFieldDTO();
                ufDto.setSqlExpression(uf.getSqlExpression());
                ufDto.setFieldType(uf.getFieldType());
                ufDto.setAggregated(uf.isAggregated());
                ufDto.setSuggestedObjectType(uf.getSuggestedObjectType());
                ufDto.setConfidence(uf.getConfidence() != null ? uf.getConfidence().name() : null);
                return ufDto;
            })
            .collect(Collectors.toList()));

        dto.setInvolvedObjectTypes(result.getInvolvedObjectTypes());
        dto.setTableToObjectMap(result.getTableToObjectMap());

        return dto;
    }

    /**
     * 转换语义对齐结果
     */
    private SemanticAlignmentDTO convertSemanticResult(LLMAlignment.SemanticAlignmentResult result) {
        if (result == null) return null;

        SemanticAlignmentDTO dto = new SemanticAlignmentDTO();

        dto.setMetrics(result.getMetrics().stream()
            .map(m -> {
                SemanticMetricDTO mDto = new SemanticMetricDTO();
                mDto.setSqlField(m.getSqlField());
                mDto.setBusinessMeaning(m.getBusinessMeaning());
                mDto.setRecommendedName(m.getRecommendedName());
                mDto.setAggregationType(m.getAggregationType());
                mDto.setSuggestedMetricType(m.getSuggestedMetricType());
                mDto.setUnit(m.getUnit());
                mDto.setConfidence(m.getConfidence());
                return mDto;
            })
            .collect(Collectors.toList()));

        dto.setDimensions(result.getDimensions().stream()
            .map(d -> {
                SemanticDimensionDTO dDto = new SemanticDimensionDTO();
                dDto.setSqlField(d.getSqlField());
                dDto.setBusinessMeaning(d.getBusinessMeaning());
                dDto.setTimeDimension(d.isTimeDimension());
                dDto.setEnumDimension(d.isEnumDimension());
                dDto.setEnumValues(d.getEnumValues());
                return dDto;
            })
            .collect(Collectors.toList()));

        if (result.getTimeAnalysis() != null) {
            TimeAnalysisDTO taDto = new TimeAnalysisDTO();
            taDto.setTimeField(result.getTimeAnalysis().getTimeField());
            taDto.setTimeGranularity(result.getTimeAnalysis().getTimeGranularity());
            if (result.getTimeAnalysis().getTimeRange() != null) {
                TimeRangeDTO trDto = new TimeRangeDTO();
                trDto.setStart(result.getTimeAnalysis().getTimeRange().getStart());
                trDto.setEnd(result.getTimeAnalysis().getTimeRange().getEnd());
                taDto.setTimeRange(trDto);
            }
            dto.setTimeAnalysis(taDto);
        }

        dto.setFilterAnalysis(result.getFilterAnalysis().stream()
            .map(f -> {
                FilterAnalysisDTO fDto = new FilterAnalysisDTO();
                fDto.setField(f.getField());
                fDto.setOperator(f.getOperator());
                fDto.setValue(f.getValue());
                fDto.setBusinessScope(f.isBusinessScope());
                return fDto;
            })
            .collect(Collectors.toList()));

        return dto;
    }

    /**
     * 转换保存结果
     */
    private SaveResultDTO convertToSaveResultDTO(SaveResult result) {
        SaveResultDTO dto = new SaveResultDTO();
        dto.setSuccess(result.isSuccess());
        dto.setSavedIds(result.getSavedIds());

        dto.setResults(result.getSavedMetrics().stream()
            .map(sm -> new SavedMetricResultDTO(sm.getMetricName(), sm.getSavedId(), sm.getStatus()))
            .collect(Collectors.toList()));

        dto.setErrors(result.getErrors().stream()
            .map(e -> new SaveErrorDTO(e.getMetricName(), e.getMessage()))
            .collect(Collectors.toList()));

        return dto;
    }

    /**
     * 转换验证结果
     */
    private ValidationOnlyResultDTO convertToValidationResultDTO(ValidationOnlyResult result) {
        ValidationOnlyResultDTO dto = new ValidationOnlyResultDTO();
        dto.setOriginalSql(result.getOriginalSql());
        dto.setExtractedMetrics(convertExtractedMetrics(result.getExtractedMetrics()));
        dto.setValidations(convertValidations(result.getValidations()));
        dto.setSuggestions(result.getSuggestions());
        dto.setAllValid(result.isAllValid());
        return dto;
    }

    /**
     * 将请求数据转换为 ExtractedMetric
     */
    @SuppressWarnings("unchecked")
    private ExtractedMetric convertToExtractedMetric(Map<String, Object> data) {
        ExtractedMetric metric = new ExtractedMetric();
        metric.setId((String) data.get("id"));
        metric.setName((String) data.get("name"));
        metric.setDisplayName((String) data.get("displayName"));
        metric.setDescription((String) data.get("description"));

        String category = (String) data.get("category");
        if (category != null) {
            metric.setCategory(ExtractedMetric.MetricCategory.valueOf(category.toUpperCase()));
        }

        metric.setSourceSql((String) data.get("sourceSql"));
        metric.setUnit((String) data.get("unit"));

        String confidence = (String) data.get("confidence");
        if (confidence != null) {
            metric.setConfidence(ExtractedMetric.ConfidenceLevel.valueOf(confidence.toUpperCase()));
        }

        metric.setBusinessProcess((String) data.get("businessProcess"));
        metric.setAggregationFunction((String) data.get("aggregationFunction"));
        metric.setAggregationField((String) data.get("aggregationField"));
        metric.setAtomicMetricId((String) data.get("atomicMetricId"));
        metric.setTimeDimension((String) data.get("timeDimension"));
        metric.setTimeGranularity((String) data.get("timeGranularity"));

        if (data.get("dimensions") instanceof List) {
            metric.setDimensions((List<String>) data.get("dimensions"));
        }

        if (data.get("filterConditions") instanceof Map) {
            metric.setFilterConditions((Map<String, Object>) data.get("filterConditions"));
        }

        if (data.get("comparisonType") instanceof List) {
            metric.setComparisonType((List<String>) data.get("comparisonType"));
        }

        metric.setDerivedFormula((String) data.get("derivedFormula"));

        if (data.get("baseMetricIds") instanceof List) {
            metric.setBaseMetricIds((List<String>) data.get("baseMetricIds"));
        }

        metric.setStatus((String) data.get("status"));

        return metric;
    }

    // ==================== DTO 内部类 ====================

    public static class SqlPasteParseResultDTO {
        private String originalSql;
        private List<ExtractedMetricDTO> extractedMetrics;
        private List<MetricValidationDTO> validations;
        private MappingAlignmentDTO mappingResult;
        private SemanticAlignmentDTO semanticResult;
        private List<String> suggestions;
        private List<String> errors;

        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public List<ExtractedMetricDTO> getExtractedMetrics() { return extractedMetrics; }
        public void setExtractedMetrics(List<ExtractedMetricDTO> extractedMetrics) { this.extractedMetrics = extractedMetrics; }
        public List<MetricValidationDTO> getValidations() { return validations; }
        public void setValidations(List<MetricValidationDTO> validations) { this.validations = validations; }
        public MappingAlignmentDTO getMappingResult() { return mappingResult; }
        public void setMappingResult(MappingAlignmentDTO mappingResult) { this.mappingResult = mappingResult; }
        public SemanticAlignmentDTO getSemanticResult() { return semanticResult; }
        public void setSemanticResult(SemanticAlignmentDTO semanticResult) { this.semanticResult = semanticResult; }
        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }

    public static class ExtractedMetricDTO {
        private String suggestedId;
        private String name;
        private String displayName;
        private String description;
        private String category;
        private String sourceSql;
        private String confidence;
        private List<String> notes;
        private String unit;
        private Map<String, Object> definition;

        public String getSuggestedId() { return suggestedId; }
        public void setSuggestedId(String suggestedId) { this.suggestedId = suggestedId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSourceSql() { return sourceSql; }
        public void setSourceSql(String sourceSql) { this.sourceSql = sourceSql; }
        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }
        public List<String> getNotes() { return notes; }
        public void setNotes(List<String> notes) { this.notes = notes; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Map<String, Object> getDefinition() { return definition; }
        public void setDefinition(Map<String, Object> definition) { this.definition = definition; }
    }

    public static class MetricValidationDTO {
        private boolean valid;
        private int errorCount;
        private int warningCount;
        private List<ValidationMessageDTO> errors;
        private List<ValidationMessageDTO> warnings;
        private List<ValidationMessageDTO> infos;

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
        public int getWarningCount() { return warningCount; }
        public void setWarningCount(int warningCount) { this.warningCount = warningCount; }
        public List<ValidationMessageDTO> getErrors() { return errors; }
        public void setErrors(List<ValidationMessageDTO> errors) { this.errors = errors; }
        public List<ValidationMessageDTO> getWarnings() { return warnings; }
        public void setWarnings(List<ValidationMessageDTO> warnings) { this.warnings = warnings; }
        public List<ValidationMessageDTO> getInfos() { return infos; }
        public void setInfos(List<ValidationMessageDTO> infos) { this.infos = infos; }
    }

    public static class ValidationMessageDTO {
        private String code;
        private String message;

        public ValidationMessageDTO(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class MappingAlignmentDTO {
        private String mappingId;
        private List<FieldMappingDTO> fieldMappings;
        private List<UnmappedFieldDTO> unmappedFields;
        private List<String> involvedObjectTypes;
        private Map<String, String> tableToObjectMap;

        public String getMappingId() { return mappingId; }
        public void setMappingId(String mappingId) { this.mappingId = mappingId; }
        public List<FieldMappingDTO> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(List<FieldMappingDTO> fieldMappings) { this.fieldMappings = fieldMappings; }
        public List<UnmappedFieldDTO> getUnmappedFields() { return unmappedFields; }
        public void setUnmappedFields(List<UnmappedFieldDTO> unmappedFields) { this.unmappedFields = unmappedFields; }
        public List<String> getInvolvedObjectTypes() { return involvedObjectTypes; }
        public void setInvolvedObjectTypes(List<String> involvedObjectTypes) { this.involvedObjectTypes = involvedObjectTypes; }
        public Map<String, String> getTableToObjectMap() { return tableToObjectMap; }
        public void setTableToObjectMap(Map<String, String> tableToObjectMap) { this.tableToObjectMap = tableToObjectMap; }
    }

    public static class FieldMappingDTO {
        private String sqlField;
        private String sqlTable;
        private String objectProperty;
        private String objectType;
        private String columnName;
        private String confidence;

        public String getSqlField() { return sqlField; }
        public void setSqlField(String sqlField) { this.sqlField = sqlField; }
        public String getSqlTable() { return sqlTable; }
        public void setSqlTable(String sqlTable) { this.sqlTable = sqlTable; }
        public String getObjectProperty() { return objectProperty; }
        public void setObjectProperty(String objectProperty) { this.objectProperty = objectProperty; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }
    }

    public static class UnmappedFieldDTO {
        private String sqlExpression;
        private String fieldType;
        private boolean aggregated;
        private String suggestedObjectType;
        private String confidence;

        public String getSqlExpression() { return sqlExpression; }
        public void setSqlExpression(String sqlExpression) { this.sqlExpression = sqlExpression; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public boolean isAggregated() { return aggregated; }
        public void setAggregated(boolean aggregated) { this.aggregated = aggregated; }
        public String getSuggestedObjectType() { return suggestedObjectType; }
        public void setSuggestedObjectType(String suggestedObjectType) { this.suggestedObjectType = suggestedObjectType; }
        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }
    }

    public static class SemanticAlignmentDTO {
        private List<SemanticMetricDTO> metrics;
        private List<SemanticDimensionDTO> dimensions;
        private TimeAnalysisDTO timeAnalysis;
        private List<FilterAnalysisDTO> filterAnalysis;

        public List<SemanticMetricDTO> getMetrics() { return metrics; }
        public void setMetrics(List<SemanticMetricDTO> metrics) { this.metrics = metrics; }
        public List<SemanticDimensionDTO> getDimensions() { return dimensions; }
        public void setDimensions(List<SemanticDimensionDTO> dimensions) { this.dimensions = dimensions; }
        public TimeAnalysisDTO getTimeAnalysis() { return timeAnalysis; }
        public void setTimeAnalysis(TimeAnalysisDTO timeAnalysis) { this.timeAnalysis = timeAnalysis; }
        public List<FilterAnalysisDTO> getFilterAnalysis() { return filterAnalysis; }
        public void setFilterAnalysis(List<FilterAnalysisDTO> filterAnalysis) { this.filterAnalysis = filterAnalysis; }
    }

    public static class SemanticMetricDTO {
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

    public static class SemanticDimensionDTO {
        private String sqlField;
        private String businessMeaning;
        private boolean timeDimension;
        private boolean enumDimension;
        private List<String> enumValues;

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

    public static class TimeAnalysisDTO {
        private String timeField;
        private String timeGranularity;
        private TimeRangeDTO timeRange;

        public String getTimeField() { return timeField; }
        public void setTimeField(String timeField) { this.timeField = timeField; }
        public String getTimeGranularity() { return timeGranularity; }
        public void setTimeGranularity(String timeGranularity) { this.timeGranularity = timeGranularity; }
        public TimeRangeDTO getTimeRange() { return timeRange; }
        public void setTimeRange(TimeRangeDTO timeRange) { this.timeRange = timeRange; }
    }

    public static class TimeRangeDTO {
        private String start;
        private String end;

        public String getStart() { return start; }
        public void setStart(String start) { this.start = start; }
        public String getEnd() { return end; }
        public void setEnd(String end) { this.end = end; }
    }

    public static class FilterAnalysisDTO {
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

    public static class SaveResultDTO {
        private boolean success;
        private List<String> savedIds;
        private List<SavedMetricResultDTO> results;
        private List<SaveErrorDTO> errors;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public List<String> getSavedIds() { return savedIds; }
        public void setSavedIds(List<String> savedIds) { this.savedIds = savedIds; }
        public List<SavedMetricResultDTO> getResults() { return results; }
        public void setResults(List<SavedMetricResultDTO> results) { this.results = results; }
        public List<SaveErrorDTO> getErrors() { return errors; }
        public void setErrors(List<SaveErrorDTO> errors) { this.errors = errors; }
    }

    public static class SavedMetricResultDTO {
        private String metricName;
        private String savedId;
        private String status;

        public SavedMetricResultDTO(String metricName, String savedId, String status) {
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

    public static class SaveErrorDTO {
        private String metricName;
        private String message;

        public SaveErrorDTO(String metricName, String message) {
            this.metricName = metricName;
            this.message = message;
        }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class ValidationOnlyResultDTO {
        private String originalSql;
        private List<ExtractedMetricDTO> extractedMetrics;
        private List<MetricValidationDTO> validations;
        private List<String> suggestions;
        private boolean allValid;

        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public List<ExtractedMetricDTO> getExtractedMetrics() { return extractedMetrics; }
        public void setExtractedMetrics(List<ExtractedMetricDTO> extractedMetrics) { this.extractedMetrics = extractedMetrics; }
        public List<MetricValidationDTO> getValidations() { return validations; }
        public void setValidations(List<MetricValidationDTO> validations) { this.validations = validations; }
        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        public boolean isAllValid() { return allValid; }
        public void setAllValid(boolean allValid) { this.allValid = allValid; }
    }
}
