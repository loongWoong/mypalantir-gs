package com.mypalantir.controller;

import com.mypalantir.repository.InstanceStorage;
import com.mypalantir.service.LinkService;
import com.mypalantir.service.LinkSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LinkControllerTest {

    private MockMvc mockMvc;
    private LinkService linkService;
    private LinkSyncService linkSyncService;

    @BeforeEach
    void setUp() {
        linkService = mock(LinkService.class);
        linkSyncService = mock(LinkSyncService.class);
        LinkController controller = new LinkController(linkService);
        ReflectionTestUtils.setField(controller, "linkSyncService", linkSyncService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createLink_success_returns200AndId() throws Exception {
        when(linkService.createLink(eq("owns"), eq("s1"), eq("t1"), any())).thenReturn("link-1");

        mockMvc.perform(post("/api/v1/links/owns")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source_id\":\"s1\",\"target_id\":\"t1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("link-1"));
    }

    @Test
    void createLink_missingSourceOrTarget_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/links/owns")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source_id\":\"s1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void getLink_success_returns200() throws Exception {
        Map<String, Object> link = new HashMap<>();
        link.put("id", "link-1");
        link.put("source_id", "s1");
        link.put("target_id", "t1");
        when(linkService.getLink("owns", "link-1")).thenReturn(link);

        mockMvc.perform(get("/api/v1/links/owns/link-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("link-1"));
    }

    @Test
    void listLinks_returns200WithItemsAndTotal() throws Exception {
        InstanceStorage.ListResult listResult = new InstanceStorage.ListResult(List.of(), 0);
        when(linkService.listLinks(eq("owns"), eq(0), eq(20))).thenReturn(listResult);

        mockMvc.perform(get("/api/v1/links/owns?offset=0&limit=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void getLinkStats_returns200() throws Exception {
        Map<String, Object> stats = Map.of("source_count", 10, "target_count", 5, "link_count", 3,
            "source_coverage", 0.3, "target_coverage", 0.6);
        when(linkService.getLinkStats("owns")).thenReturn(stats);

        mockMvc.perform(get("/api/v1/links/owns/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.link_count").value(3));
    }

    @Test
    void syncLinks_success_returns200() throws Exception {
        LinkSyncService.SyncResult syncResult = new LinkSyncService.SyncResult();
        syncResult.linksCreated = 5;
        when(linkSyncService.syncLinksByType("owns")).thenReturn(syncResult);

        mockMvc.perform(post("/api/v1/links/owns/sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.links_created").value(5));
    }

    @Test
    void deleteLink_success_returns200() throws Exception {
        doNothing().when(linkService).deleteLink("owns", "link-1");

        mockMvc.perform(delete("/api/v1/links/owns/link-1"))
            .andExpect(status().isOk());
    }
}
