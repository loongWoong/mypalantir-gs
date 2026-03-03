package com.mypalantir.controller;

import com.mypalantir.service.LinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InstanceLinkControllerTest {

    private MockMvc mockMvc;
    private LinkService linkService;

    @BeforeEach
    void setUp() {
        linkService = mock(LinkService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InstanceLinkController(linkService)).build();
    }

    @Test
    void getInstanceLinks_outgoing_returns200() throws Exception {
        when(linkService.getLinksBySource(eq("owns"), eq("inst-1"))).thenReturn(List.of(Map.of("id", "link-1")));

        mockMvc.perform(get("/api/v1/instances/Vehicle/inst-1/links/owns").param("direction", "outgoing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("link-1"));
    }

    @Test
    void getInstanceLinks_incoming_returns200() throws Exception {
        when(linkService.getLinksByTarget(eq("owns"), eq("inst-1"))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/instances/Vehicle/inst-1/links/owns").param("direction", "incoming"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getConnectedInstances_returns200() throws Exception {
        when(linkService.getConnectedInstances(eq("Vehicle"), eq("owns"), eq("inst-1"), eq("outgoing")))
            .thenReturn(List.of(Map.of("id", "other-1")));

        mockMvc.perform(get("/api/v1/instances/Vehicle/inst-1/connected/owns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("other-1"));
    }
}
