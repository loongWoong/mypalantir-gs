package com.mypalantir.sqlparse;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 提取的指标数据模型
 */
public class ExtractedMetric {
    
    public enum MetricCategory {
        ATOMIC, DERIVED, COMPOSITE
    }
    
    public enum ConfidenceLevel {
        HIGH, MEDIUM, LOW
    }

    private String id;
    private String name;
    private String displayName;
    private String description;
    private MetricCategory category;
    private String sourceSql;
    private ConfidenceLevel confidence;
    private List<String> notes = new ArrayList<>();

    private String businessProcess;
    private String aggregationFunction;
    private String aggregationField;

    private String atomicMetricId;
    private Map<String, Object> businessScope;
    private String timeDimension;
    private String timeGranularity;
    private List<String> dimensions = new ArrayList<>();
    private Map<String, Object> filterConditions = new HashMap<>();
    private List<String> comparisonType = new ArrayList<>();

    private String derivedFormula;
    private List<String> baseMetricIds = new ArrayList<>();

    private String unit;
    private String status = "active";

    private List<ColumnSource> sources = new ArrayList<>();
    private String transformType;
    private String rexNodeType;

    public ExtractedMetric() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public MetricCategory getCategory() { return category; }
    public void setCategory(MetricCategory category) { this.category = category; }
    public String getSourceSql() { return sourceSql; }
    public void setSourceSql(String sourceSql) { this.sourceSql = sourceSql; }
    public ConfidenceLevel getConfidence() { return confidence; }
    public void setConfidence(ConfidenceLevel confidence) { this.confidence = confidence; }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }

    public String getBusinessProcess() { return businessProcess; }
    public void setBusinessProcess(String businessProcess) { this.businessProcess = businessProcess; }
    public String getAggregationFunction() { return aggregationFunction; }
    public void setAggregationFunction(String aggregationFunction) { this.aggregationFunction = aggregationFunction; }
    public String getAggregationField() { return aggregationField; }
    public void setAggregationField(String aggregationField) { this.aggregationField = aggregationField; }

    public String getAtomicMetricId() { return atomicMetricId; }
    public void setAtomicMetricId(String atomicMetricId) { this.atomicMetricId = atomicMetricId; }
    public Map<String, Object> getBusinessScope() { return businessScope; }
    public void setBusinessScope(Map<String, Object> businessScope) { this.businessScope = businessScope; }
    public String getTimeDimension() { return timeDimension; }
    public void setTimeDimension(String timeDimension) { this.timeDimension = timeDimension; }
    public String getTimeGranularity() { return timeGranularity; }
    public void setTimeGranularity(String timeGranularity) { this.timeGranularity = timeGranularity; }
    public List<String> getDimensions() { return dimensions; }
    public void setDimensions(List<String> dimensions) { this.dimensions = dimensions; }
    public Map<String, Object> getFilterConditions() { return filterConditions; }
    public void setFilterConditions(Map<String, Object> filterConditions) { this.filterConditions = filterConditions; }
    public List<String> getComparisonType() { return comparisonType; }
    public void setComparisonType(List<String> comparisonType) { this.comparisonType = comparisonType; }

    public String getDerivedFormula() { return derivedFormula; }
    public void setDerivedFormula(String derivedFormula) { this.derivedFormula = derivedFormula; }
    public List<String> getBaseMetricIds() { return baseMetricIds; }
    public void setBaseMetricIds(List<String> baseMetricIds) { this.baseMetricIds = baseMetricIds; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<ColumnSource> getSources() { return sources; }
    public void setSources(List<ColumnSource> sources) { this.sources = sources; }
    public String getTransformType() { return transformType; }
    public void setTransformType(String transformType) { this.transformType = transformType; }
    public String getRexNodeType() { return rexNodeType; }
    public void setRexNodeType(String rexNodeType) { this.rexNodeType = rexNodeType; }

    public static class ColumnSource {
        private String sourceTable;
        private String sourceColumn;
        private int columnOrdinal;
        private List<String> transformations = new ArrayList<>();
        private String fullLineage;

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
        public String getSourceColumn() { return sourceColumn; }
        public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
        public int getColumnOrdinal() { return columnOrdinal; }
        public void setColumnOrdinal(int columnOrdinal) { this.columnOrdinal = columnOrdinal; }
        public List<String> getTransformations() { return transformations; }
        public void setTransformations(List<String> transformations) { this.transformations = transformations; }
        public String getFullLineage() { return fullLineage; }
        public void setFullLineage(String fullLineage) { this.fullLineage = fullLineage; }
    }

    public Map<String, Object> toAtomicMetricMap() {
        Map<String, Object> map = new HashMap<>();
        if (id != null) map.put("id", id);
        if (name != null) map.put("name", name);
        if (displayName != null) map.put("display_name", displayName);
        if (description != null) map.put("description", description);
        if (businessProcess != null) map.put("business_process", businessProcess);
        if (aggregationFunction != null) map.put("aggregation_function", aggregationFunction);
        if (aggregationField != null) map.put("aggregation_field", aggregationField);
        if (unit != null) map.put("unit", unit);
        map.put("status", status);
        return map;
    }

    public Map<String, Object> toMetricDefinitionMap() {
        Map<String, Object> map = new HashMap<>();
        if (id != null) map.put("id", id);
        if (name != null) map.put("name", name);
        if (displayName != null) map.put("display_name", displayName);
        if (description != null) map.put("description", description);
        if (category != null) map.put("metric_type", category.name().toLowerCase());
        if (atomicMetricId != null) map.put("atomic_metric_id", atomicMetricId);
        if (businessScope != null) map.put("business_scope", businessScope);
        if (timeDimension != null) map.put("time_dimension", timeDimension);
        if (timeGranularity != null) map.put("time_granularity", timeGranularity);
        if (dimensions != null) map.put("dimensions", dimensions);
        if (filterConditions != null) map.put("filter_conditions", filterConditions);
        if (comparisonType != null) map.put("comparison_type", comparisonType);
        if (derivedFormula != null) map.put("derived_formula", derivedFormula);
        if (baseMetricIds != null) map.put("base_metric_ids", baseMetricIds);
        if (unit != null) map.put("unit", unit);
        map.put("status", status);
        if (transformType != null) map.put("transform_type", transformType);
        if (rexNodeType != null) map.put("rex_node_type", rexNodeType);
        if (sources != null && !sources.isEmpty()) {
            List<Map<String, Object>> sourceList = sources.stream().map(s -> {
                Map<String, Object> sm = new HashMap<>();
                if (s.getSourceTable() != null) sm.put("source_table", s.getSourceTable());
                if (s.getSourceColumn() != null) sm.put("source_column", s.getSourceColumn());
                sm.put("column_ordinal", s.getColumnOrdinal());
                if (s.getTransformations() != null) sm.put("transformations", s.getTransformations());
                if (s.getFullLineage() != null) sm.put("full_lineage", s.getFullLineage());
                return sm;
            }).collect(Collectors.toList());
            map.put("column_sources", sourceList);
        }
        return map;
    }
}
