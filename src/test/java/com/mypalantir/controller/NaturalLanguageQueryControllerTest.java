package com.mypalantir.controller;

import com.mypalantir.query.OntologyQuery;
import com.mypalantir.query.QueryExecutor;
import com.mypalantir.service.NaturalLanguageQueryService;
import com.mypalantir.service.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NaturalLanguageQueryControllerTest {

    private MockMvc mockMvc;
    private NaturalLanguageQueryService naturalLanguageQueryService;
    private QueryService queryService;

    @BeforeEach
    void setUp() {
        naturalLanguageQueryService = mock(NaturalLanguageQueryService.class);
        queryService = mock(QueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new NaturalLanguageQueryController(naturalLanguageQueryService, queryService)
        ).build();
    }

    @Test
    void execute_emptyQuery_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/query/natural-language")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("查询文本不能为空"));
    }

    @Test
    void execute_success_returns200WithResult() throws Exception {
        OntologyQuery ontologyQuery = new OntologyQuery();
        ontologyQuery.setFrom("Vehicle");
        ontologyQuery.setSelect(List.of("id"));
        when(naturalLanguageQueryService.convertToQuery("查询所有车辆")).thenReturn(ontologyQuery);

        QueryExecutor.QueryResult execResult = new QueryExecutor.QueryResult(
            List.of(Map.of("id", "1")),
            List.of("id")
        );
        when(queryService.executeQuery(anyMap())).thenReturn(execResult);

        mockMvc.perform(post("/api/v1/query/natural-language")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"查询所有车辆\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.query").value("查询所有车辆"))
            .andExpect(jsonPath("$.data.columns").isArray())
            .andExpect(jsonPath("$.data.rowCount").value(1));
    }

    @Test
    void convert_emptyQuery_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/query/natural-language/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void convert_success_returns200WithConvertedQuery() throws Exception {
        OntologyQuery ontologyQuery = new OntologyQuery();
        ontologyQuery.setFrom("Vehicle");
        ontologyQuery.setSelect(List.of("id", "plate"));
        when(naturalLanguageQueryService.convertToQuery("列出车辆")).thenReturn(ontologyQuery);

        mockMvc.perform(post("/api/v1/query/natural-language/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"列出车辆\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.query").value("列出车辆"))
            .andExpect(jsonPath("$.data.convertedQuery").exists());
    }
}
