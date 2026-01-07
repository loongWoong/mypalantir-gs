package com.mypalantir.metric;

import java.util.List;
import java.util.Map;

/**
 * 指标定义模型（派生指标和复合指标）
 */
public class MetricDefinition {
    private String id;
    private String name;
    private String displayName;
    private String description;
    private String metricType; // derived, composite
    private String atomicMetricId; // 派生指标使用
    private Map<String, Object> businessScope; // 业务范围配置
    private String timeDimension;
    private String timeGranularity; // day, week, month, quarter, year
    private List<String> dimensions; // 维度字段列表
    private Map<String, Object> filterConditions; // 条件限定
    private List<String> comparisonType; // YoY, MoM, WoW, QoQ
    private String derivedFormula; // 复合指标使用
    private List<String> baseMetricIds; // 复合指标使用
    private String unit;
    private String status;

    public MetricDefinition() {
    }

    public MetricDefinition(Map<String, Object> data) {
        this.id = (String) data.get("id");
        this.name = (String) data.get("name");
        this.displayName = (String) data.get("display_name");
        this.description = (String) data.get("description");
        this.metricType = (String) data.get("metric_type");
        this.atomicMetricId = (String) data.get("atomic_metric_id");
        this.businessScope = (Map<String, Object>) data.get("business_scope");
        this.timeDimension = (String) data.get("time_dimension");
        this.timeGranularity = (String) data.get("time_granularity");
        this.dimensions = (List<String>) data.get("dimensions");
        this.filterConditions = (Map<String, Object>) data.get("filter_conditions");
        this.comparisonType = (List<String>) data.get("comparison_type");
        this.derivedFormula = (String) data.get("derived_formula");
        this.baseMetricIds = (List<String>) data.get("base_metric_ids");
        this.unit = (String) data.get("unit");
        this.status = (String) data.get("status");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        // 必填字段：仅在非 null 时放入，避免校验器对 null 做字符串校验
        if (id != null) {
            map.put("id", id);
        }
        if (name != null) {
            map.put("name", name);
        }
        if (metricType != null) {
            map.put("metric_type", metricType);
        }
        if (status != null) {
            map.put("status", status);
        }

        // 可选字段：非 null 时才放入，避免触发错误的类型校验
        if (displayName != null) {
            map.put("display_name", displayName);
        }
        if (description != null) {
            map.put("description", description);
        }
        if (atomicMetricId != null) {
            map.put("atomic_metric_id", atomicMetricId);
        }
        if (businessScope != null) {
            map.put("business_scope", businessScope);
        }
        if (timeDimension != null) {
            map.put("time_dimension", timeDimension);
        }
        if (timeGranularity != null) {
            map.put("time_granularity", timeGranularity);
        }
        if (dimensions != null) {
            map.put("dimensions", dimensions);
        }
        if (filterConditions != null) {
            map.put("filter_conditions", filterConditions);
        }
        if (comparisonType != null) {
            map.put("comparison_type", comparisonType);
        }
        if (derivedFormula != null) {
            map.put("derived_formula", derivedFormula);
        }
        if (baseMetricIds != null) {
            map.put("base_metric_ids", baseMetricIds);
        }
        if (unit != null) {
            map.put("unit", unit);
        }
        return map;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public String getAtomicMetricId() {
        return atomicMetricId;
    }

    public void setAtomicMetricId(String atomicMetricId) {
        this.atomicMetricId = atomicMetricId;
    }

    public Map<String, Object> getBusinessScope() {
        return businessScope;
    }

    public void setBusinessScope(Map<String, Object> businessScope) {
        this.businessScope = businessScope;
    }

    public String getTimeDimension() {
        return timeDimension;
    }

    public void setTimeDimension(String timeDimension) {
        this.timeDimension = timeDimension;
    }

    public String getTimeGranularity() {
        return timeGranularity;
    }

    public void setTimeGranularity(String timeGranularity) {
        this.timeGranularity = timeGranularity;
    }

    public List<String> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<String> dimensions) {
        this.dimensions = dimensions;
    }

    public Map<String, Object> getFilterConditions() {
        return filterConditions;
    }

    public void setFilterConditions(Map<String, Object> filterConditions) {
        this.filterConditions = filterConditions;
    }

    public List<String> getComparisonType() {
        return comparisonType;
    }

    public void setComparisonType(List<String> comparisonType) {
        this.comparisonType = comparisonType;
    }

    public String getDerivedFormula() {
        return derivedFormula;
    }

    public void setDerivedFormula(String derivedFormula) {
        this.derivedFormula = derivedFormula;
    }

    public List<String> getBaseMetricIds() {
        return baseMetricIds;
    }

    public void setBaseMetricIds(List<String> baseMetricIds) {
        this.baseMetricIds = baseMetricIds;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
