package com.mypalantir.metric;

import java.util.Map;

/**
 * 原子指标模型
 */
public class AtomicMetric {
    private String id;
    private String name;
    private String displayName;
    private String description;
    private String businessProcess;
    private String aggregationFunction;
    private String aggregationField;
    private String unit;
    private String status;

    public AtomicMetric() {
    }

    public AtomicMetric(Map<String, Object> data) {
        this.id = (String) data.get("id");
        this.name = (String) data.get("name");
        this.displayName = (String) data.get("display_name");
        this.description = (String) data.get("description");
        this.businessProcess = (String) data.get("business_process");
        this.aggregationFunction = (String) data.get("aggregation_function");
        this.aggregationField = (String) data.get("aggregation_field");
        this.unit = (String) data.get("unit");
        this.status = (String) data.get("status");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        // 必填字段：始终包含（即使为 null，后端会设置默认值）
        if (id != null) {
            map.put("id", id);
        }
        if (name != null) {
            map.put("name", name);
        }
        if (businessProcess != null) {
            map.put("business_process", businessProcess);
        }
        if (aggregationFunction != null) {
            map.put("aggregation_function", aggregationFunction);
        }
        if (status != null) {
            map.put("status", status);
        }
        // 可选字段：只在非 null 时包含
        if (displayName != null) {
            map.put("display_name", displayName);
        }
        if (description != null) {
            map.put("description", description);
        }
        if (aggregationField != null) {
            map.put("aggregation_field", aggregationField);
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

    public String getBusinessProcess() {
        return businessProcess;
    }

    public void setBusinessProcess(String businessProcess) {
        this.businessProcess = businessProcess;
    }

    public String getAggregationFunction() {
        return aggregationFunction;
    }

    public void setAggregationFunction(String aggregationFunction) {
        this.aggregationFunction = aggregationFunction;
    }

    public String getAggregationField() {
        return aggregationField;
    }

    public void setAggregationField(String aggregationField) {
        this.aggregationField = aggregationField;
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
