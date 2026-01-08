package com.mypalantir.sqlparse;

import java.util.*;

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
        return map;
    }
}
