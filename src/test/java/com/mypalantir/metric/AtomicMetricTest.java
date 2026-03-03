package com.mypalantir.metric;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AtomicMetricTest {

    @Test
    void constructorFromMap_populatesFields() {
        Map<String, Object> data = Map.of(
            "id", "m1",
            "name", "total_fee",
            "display_name", "总费用",
            "business_process", "toll",
            "aggregation_function", "sum",
            "aggregation_field", "amount",
            "status", "active"
        );
        AtomicMetric m = new AtomicMetric(data);

        assertEquals("m1", m.getId());
        assertEquals("total_fee", m.getName());
        assertEquals("总费用", m.getDisplayName());
        assertEquals("toll", m.getBusinessProcess());
        assertEquals("sum", m.getAggregationFunction());
        assertEquals("amount", m.getAggregationField());
        assertEquals("active", m.getStatus());
    }

    @Test
    void toMap_containsSetFields() {
        AtomicMetric m = new AtomicMetric();
        m.setId("m1");
        m.setName("fee");
        m.setBusinessProcess("toll");
        m.setAggregationFunction("sum");
        m.setStatus("active");

        Map<String, Object> map = m.toMap();

        assertEquals("m1", map.get("id"));
        assertEquals("fee", map.get("name"));
        assertEquals("toll", map.get("business_process"));
        assertEquals("sum", map.get("aggregation_function"));
        assertEquals("active", map.get("status"));
    }

    @Test
    void toMap_optionalFieldsOmittedWhenNull() {
        AtomicMetric m = new AtomicMetric();
        m.setName("x");
        m.setStatus("active");

        Map<String, Object> map = m.toMap();

        assertFalse(map.containsKey("display_name"));
        assertFalse(map.containsKey("description"));
        assertFalse(map.containsKey("unit"));
    }

    @Test
    void toMap_displayNameIncludedWhenSet() {
        AtomicMetric m = new AtomicMetric();
        m.setName("x");
        m.setDisplayName("显示名");
        m.setStatus("active");

        Map<String, Object> map = m.toMap();

        assertEquals("显示名", map.get("display_name"));
    }
}
