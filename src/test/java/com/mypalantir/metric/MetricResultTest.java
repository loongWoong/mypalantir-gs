package com.mypalantir.metric;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricResult 及内部类 ComparisonValue、MetricDataPoint 的单元测试。
 */
class MetricResultTest {

    @Test
    void gettersAndSetters_populateAndReturnValues() {
        MetricResult r = new MetricResult();
        r.setMetricId("m1");
        r.setMetricName("总费用");
        r.setTimeGranularity("day");
        r.setColumns(List.of("date", "value"));
        r.setResults(List.of(Map.<String, Object>of("date", "2024-01-01", "value", 100)));
        LocalDateTime at = LocalDateTime.now();
        r.setCalculatedAt(at);
        r.setSql("SELECT 1");

        assertEquals("m1", r.getMetricId());
        assertEquals("总费用", r.getMetricName());
        assertEquals("day", r.getTimeGranularity());
        assertEquals(List.of("date", "value"), r.getColumns());
        assertEquals(1, r.getResults().size());
        assertEquals(100, r.getResults().get(0).get("value"));
        assertEquals(at, r.getCalculatedAt());
        assertEquals("SELECT 1", r.getSql());
    }

    @Test
    void defaultConstructor_allFieldsNull() {
        MetricResult r = new MetricResult();
        assertNull(r.getMetricId());
        assertNull(r.getMetricName());
        assertNull(r.getTimeGranularity());
        assertNull(r.getResults());
        assertNull(r.getColumns());
        assertNull(r.getCalculatedAt());
        assertNull(r.getSql());
    }

    @Test
    void comparisonValue_constructorAndGetters() {
        MetricResult.ComparisonValue cv = new MetricResult.ComparisonValue(1.5, "1.5", "环比");
        assertEquals(1.5, cv.getValue());
        assertEquals("1.5", cv.getDisplay());
        assertEquals("环比", cv.getDescription());
    }

    @Test
    void comparisonValue_setters() {
        MetricResult.ComparisonValue cv = new MetricResult.ComparisonValue(0, null, null);
        cv.setValue(2.0);
        cv.setDisplay("2.0");
        cv.setDescription("同比");
        assertEquals(2.0, cv.getValue());
        assertEquals("2.0", cv.getDisplay());
        assertEquals("同比", cv.getDescription());
    }

    @Test
    void metricDataPoint_gettersAndSetters() {
        MetricResult.MetricDataPoint dp = new MetricResult.MetricDataPoint();
        dp.setTimeValue("2024-01");
        dp.setDimensionValues(Map.of("region", "CN"));
        dp.setMetricValue(100);
        dp.setUnit("元");
        dp.setComparisons(Map.of("yoy", new MetricResult.ComparisonValue(0.1, "10%", "同比")));

        assertEquals("2024-01", dp.getTimeValue());
        assertEquals(Map.of("region", "CN"), dp.getDimensionValues());
        assertEquals(100, dp.getMetricValue());
        assertEquals("元", dp.getUnit());
        assertNotNull(dp.getComparisons());
        assertEquals(0.1, dp.getComparisons().get("yoy").getValue());
    }
}
