package com.mypalantir.controller;

import com.mypalantir.metric.AtomicMetric;
import com.mypalantir.service.AtomicMetricService;
import com.mypalantir.service.MetricCalculator;
import com.mypalantir.service.MetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MetricControllerTest {

    private MockMvc mockMvc;
    private AtomicMetricService atomicMetricService;
    private MetricService metricService;
    private MetricCalculator metricCalculator;

    @BeforeEach
    void setUp() {
        atomicMetricService = mock(AtomicMetricService.class);
        metricService = mock(MetricService.class);
        metricCalculator = mock(MetricCalculator.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new MetricController(atomicMetricService, metricService, metricCalculator)
        ).build();
    }

    @Test
    void listAtomicMetrics_returns200() throws Exception {
        AtomicMetric m = new AtomicMetric();
        m.setId("m1");
        m.setName("total_fee");
        m.setBusinessProcess("toll");
        m.setAggregationFunction("sum");
        m.setStatus("active");
        when(atomicMetricService.listAtomicMetrics()).thenReturn(List.of(m));

        mockMvc.perform(get("/api/v1/metrics/atomic-metrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("m1"));
    }

    @Test
    void getAtomicMetric_returns200() throws Exception {
        AtomicMetric m = new AtomicMetric();
        m.setId("m1");
        m.setName("total_fee");
        when(atomicMetricService.getAtomicMetric("m1")).thenReturn(m);

        mockMvc.perform(get("/api/v1/metrics/atomic-metrics/m1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("total_fee"));
    }

    @Test
    void getAtomicMetric_notFound_returns404() throws Exception {
        when(atomicMetricService.getAtomicMetric("nonexistent")).thenThrow(new java.io.IOException("not found"));

        mockMvc.perform(get("/api/v1/metrics/atomic-metrics/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void createAtomicMetric_success_returns200AndId() throws Exception {
        when(atomicMetricService.createAtomicMetric(any(AtomicMetric.class), any())).thenReturn("new-id");

        mockMvc.perform(post("/api/v1/metrics/atomic-metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"fee\",\"businessProcess\":\"toll\","
                    + "\"aggregationFunction\":\"sum\",\"aggregationField\":\"amount\",\"status\":\"active\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("new-id"));
    }

    @Test
    void deleteAtomicMetric_success_returns200() throws Exception {
        doNothing().when(atomicMetricService).deleteAtomicMetric("m1");

        mockMvc.perform(delete("/api/v1/metrics/atomic-metrics/m1"))
            .andExpect(status().isOk());
    }
}
