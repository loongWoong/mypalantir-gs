package com.mypalantir.metric;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 指标计算结果
 */
public class MetricResult {
    private String metricId;
    private String metricName;
    private String timeGranularity;
    private List<MetricDataPoint> results;
    private LocalDateTime calculatedAt;
    private String sql;

    public MetricResult() {
    }

    public String getMetricId() {
        return metricId;
    }

    public void setMetricId(String metricId) {
        this.metricId = metricId;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getTimeGranularity() {
        return timeGranularity;
    }

    public void setTimeGranularity(String timeGranularity) {
        this.timeGranularity = timeGranularity;
    }

    public List<MetricDataPoint> getResults() {
        return results;
    }

    public void setResults(List<MetricDataPoint> results) {
        this.results = results;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    /**
     * 指标数据点
     */
    public static class MetricDataPoint {
        private String timeValue;
        private Map<String, Object> dimensionValues;
        private Number metricValue;
        private String unit;
        private Map<String, ComparisonValue> comparisons;

        public MetricDataPoint() {
        }

        public String getTimeValue() {
            return timeValue;
        }

        public void setTimeValue(String timeValue) {
            this.timeValue = timeValue;
        }

        public Map<String, Object> getDimensionValues() {
            return dimensionValues;
        }

        public void setDimensionValues(Map<String, Object> dimensionValues) {
            this.dimensionValues = dimensionValues;
        }

        public Number getMetricValue() {
            return metricValue;
        }

        public void setMetricValue(Number metricValue) {
            this.metricValue = metricValue;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public Map<String, ComparisonValue> getComparisons() {
            return comparisons;
        }

        public void setComparisons(Map<String, ComparisonValue> comparisons) {
            this.comparisons = comparisons;
        }
    }

    /**
     * 对比值
     */
    public static class ComparisonValue {
        private double value;
        private String display;
        private String description;

        public ComparisonValue() {
        }

        public ComparisonValue(double value, String display, String description) {
            this.value = value;
            this.display = display;
            this.description = description;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
