package com.mypalantir.controller;

import com.mypalantir.service.ETLLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ETLLinkControllerTest {

    private MockMvc mockMvc;
    private ETLLinkService etlLinkService;

    @BeforeEach
    void setUp() {
        etlLinkService = mock(ETLLinkService.class);
        ETLLinkController controller = new ETLLinkController();
        ReflectionTestUtils.setField(controller, "etlLinkService", etlLinkService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getLinks_returns200() throws Exception {
        when(etlLinkService.getLinks(eq("owns"), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(List.of(Map.of("id", "l1", "source_id", "s1", "target_id", "t1")));

        mockMvc.perform(get("/api/v1/etl/links/owns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("l1"));
    }

    @Test
    void createLinksBatch_success_returns200() throws Exception {
        ETLLinkService.BatchCreateResult result = new ETLLinkService.BatchCreateResult();
        result.addSuccess("link-1", "s1", "t1");
        result.addSuccess("link-2", "s2", "t2");
        when(etlLinkService.createLinksBatch(eq("owns"), anyList())).thenReturn(result);

        mockMvc.perform(post("/api/v1/etl/links/owns/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"sourceId\":\"s1\",\"targetId\":\"t1\"},{\"sourceId\":\"s2\",\"targetId\":\"t2\"}]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success_count").value(2));
    }

    @Test
    void createLinksBatch_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/etl/links/owns/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }
}
