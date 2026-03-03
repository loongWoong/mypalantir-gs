package com.mypalantir.controller;

import com.mypalantir.service.MappingService;
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

class MappingControllerTest {

    private MockMvc mockMvc;
    private MappingService mappingService;

    @BeforeEach
    void setUp() {
        mappingService = mock(MappingService.class);
        MappingController controller = new MappingController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "mappingService", mappingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createMapping_success_returns200AndId() throws Exception {
        when(mappingService.createMapping(eq("Vehicle"), eq("table-1"), anyMap(), any()))
            .thenReturn("mapping-1");

        mockMvc.perform(post("/api/v1/mappings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"object_type\":\"Vehicle\",\"table_id\":\"table-1\","
                    + "\"column_property_mappings\":{\"id\":\"id\",\"plate\":\"plate_no\"},"
                    + "\"primary_key_columns\":[\"id\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("mapping-1"));
    }

    @Test
    void getMapping_success_returns200() throws Exception {
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("id", "mapping-1");
        mapping.put("object_type", "Vehicle");
        when(mappingService.getMapping("mapping-1")).thenReturn(mapping);

        mockMvc.perform(get("/api/v1/mappings/mapping-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("mapping-1"));
    }

    @Test
    void getMapping_notFound_returns404() throws Exception {
        when(mappingService.getMapping("nonexistent")).thenThrow(new java.io.IOException("not found"));

        mockMvc.perform(get("/api/v1/mappings/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void getMappingsByObjectType_returns200() throws Exception {
        when(mappingService.getMappingsByObjectType("Vehicle")).thenReturn(List.of(Map.of("id", "m1")));

        mockMvc.perform(get("/api/v1/mappings/by-object-type/Vehicle"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("m1"));
    }

    @Test
    void deleteMapping_success_returns200() throws Exception {
        doNothing().when(mappingService).deleteMapping("mapping-1");

        mockMvc.perform(delete("/api/v1/mappings/mapping-1"))
            .andExpect(status().isOk());
    }
}
