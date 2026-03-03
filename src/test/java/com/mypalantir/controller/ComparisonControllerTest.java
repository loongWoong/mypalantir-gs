package com.mypalantir.controller;

import com.mypalantir.service.DataComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComparisonControllerTest {

    private MockMvc mockMvc;
    private DataComparisonService comparisonService;

    @BeforeEach
    void setUp() {
        comparisonService = mock(DataComparisonService.class);
        ComparisonController controller = new ComparisonController();
        ReflectionTestUtils.setField(controller, "comparisonService", comparisonService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void runComparison_success_returns200() throws Exception {
        DataComparisonService.ComparisonResult result = new DataComparisonService.ComparisonResult();
        result.setTaskId("task-1");
        result.setSourceTotal(100);
        result.setTargetTotal(100);
        result.setMatchedCount(98);
        result.setMismatchedCount(2);
        result.setSourceOnlyCount(0);
        result.setTargetOnlyCount(0);
        when(comparisonService.runComparison(any(DataComparisonService.ComparisonRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/comparison/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceTableId\":\"t1\",\"targetTableId\":\"t2\","
                    + "\"sourceKey\":\"id\",\"targetKey\":\"id\",\"columnMapping\":{\"id\":\"id\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskId").value("task-1"))
            .andExpect(jsonPath("$.data.sourceTotal").value(100));
    }

    @Test
    void runComparison_exception_returns500() throws Exception {
        when(comparisonService.runComparison(any())).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(post("/api/v1/comparison/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceTableId\":\"t1\",\"targetTableId\":\"t2\",\"sourceKey\":\"id\",\"targetKey\":\"id\",\"columnMapping\":{}}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500));
    }
}
