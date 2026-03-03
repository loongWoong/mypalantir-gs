package com.mypalantir.controller;

import com.mypalantir.repository.InstanceStorage;
import com.mypalantir.service.InstanceService;
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

class InstanceControllerTest {

    private MockMvc mockMvc;
    private InstanceService instanceService;

    @BeforeEach
    void setUp() {
        instanceService = mock(InstanceService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InstanceController(instanceService)).build();
    }

    @Test
    void createInstance_success_returns200AndId() throws Exception {
        when(instanceService.createInstance(eq("Vehicle"), anyMap())).thenReturn("inst-1");

        mockMvc.perform(post("/api/v1/instances/Vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"京A12345\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("inst-1"));
    }

    @Test
    void getInstance_success_returns200() throws Exception {
        Map<String, Object> instance = new HashMap<>();
        instance.put("id", "inst-1");
        instance.put("plate", "京A12345");
        when(instanceService.getInstance("Vehicle", "inst-1")).thenReturn(instance);

        mockMvc.perform(get("/api/v1/instances/Vehicle/inst-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("inst-1"));
    }

    @Test
    void getInstance_notFound_returns404() throws Exception {
        when(instanceService.getInstance("Vehicle", "nonexistent")).thenThrow(new java.io.IOException("not found"));

        mockMvc.perform(get("/api/v1/instances/Vehicle/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void updateInstance_success_returns200() throws Exception {
        doNothing().when(instanceService).updateInstance(eq("Vehicle"), eq("inst-1"), anyMap());

        mockMvc.perform(put("/api/v1/instances/Vehicle/inst-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"京B67890\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void deleteInstance_success_returns200() throws Exception {
        doNothing().when(instanceService).deleteInstance("Vehicle", "inst-1");

        mockMvc.perform(delete("/api/v1/instances/Vehicle/inst-1"))
            .andExpect(status().isOk());
    }

    @Test
    void listInstances_returns200WithItemsAndTotal() throws Exception {
        InstanceStorage.ListResult listResult = new InstanceStorage.ListResult(List.of(Map.of("id", "1")), 1L);
        when(instanceService.listInstances(eq("Vehicle"), eq(0), eq(20), any())).thenReturn(listResult);

        mockMvc.perform(get("/api/v1/instances/Vehicle?offset=0&limit=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(1));
    }
}
