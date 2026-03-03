package com.mypalantir.controller;

import com.mypalantir.query.QueryExecutor;
import com.mypalantir.service.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QueryControllerTest {

    private MockMvc mockMvc;
    private QueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(QueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new QueryController(queryService)).build();
    }

    @Test
    void executeQuery_success_returns200AndResult() throws Exception {
        QueryExecutor.QueryResult result = new QueryExecutor.QueryResult(
            List.of(Map.<String, Object>of("id", "1", "name", "a")),
            List.of("id", "name")
        );
        when(queryService.executeQuery(anyMap())).thenReturn(result);

        mockMvc.perform(post("/api/v1/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"Vehicle\",\"select\":[\"id\"],\"limit\":10}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.columns").isArray())
            .andExpect(jsonPath("$.data.rows").isArray())
            .andExpect(jsonPath("$.data.rowCount").value(1));

        verify(queryService).executeQuery(anyMap());
    }

    @Test
    void executeQuery_illegalArgument_returns400() throws Exception {
        when(queryService.executeQuery(anyMap())).thenThrow(new IllegalArgumentException("from is required"));

        mockMvc.perform(post("/api/v1/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("from is required"));
    }
}
