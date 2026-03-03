package com.mypalantir.controller;

import com.mypalantir.config.Config;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.service.OntologyModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OntologyModelControllerTest {

    private MockMvc mockMvc;
    private OntologyModelService modelService;
    private Config config;

    @BeforeEach
    void setUp() {
        modelService = mock(OntologyModelService.class);
        config = mock(Config.class);
        when(config.getOntologyModel()).thenReturn("default");
        when(config.getSchemaFilePath()).thenReturn("./ontology/schema.yaml");
        mockMvc = MockMvcBuilders.standaloneSetup(
            new OntologyModelController(modelService, config)
        ).build();
    }

    @Test
    void listModels_returns200() throws Exception {
        OntologyModelService.ModelInfo info = new OntologyModelService.ModelInfo("m1", "/path", "Model1");
        when(modelService.listAvailableModels()).thenReturn(List.of(info));

        mockMvc.perform(get("/api/v1/models"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("m1"))
            .andExpect(jsonPath("$.data[0].displayName").value("Model1"));
    }

    @Test
    void getModelObjectTypes_returns200() throws Exception {
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        when(modelService.getObjectTypes("m1")).thenReturn(List.of(ot));

        mockMvc.perform(get("/api/v1/models/m1/object-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("Vehicle"));
    }

    @Test
    void getModelObjectTypes_notFound_returns404() throws Exception {
        when(modelService.getObjectTypes("nonexistent")).thenThrow(new java.io.IOException("file not found"));

        mockMvc.perform(get("/api/v1/models/nonexistent/object-types"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void getCurrentModel_returns200() throws Exception {
        when(modelService.getCurrentModelId()).thenReturn("m1");
        when(modelService.getCurrentModelPath()).thenReturn("/path/schema.yaml");

        mockMvc.perform(get("/api/v1/models/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modelId").value("m1"))
            .andExpect(jsonPath("$.data.filePath").value("/path/schema.yaml"));
    }

    @Test
    void switchModel_success_returns200() throws Exception {
        when(modelService.getCurrentModelPath()).thenReturn("/new/path.yaml");
        doNothing().when(modelService).switchModel("m2");

        mockMvc.perform(post("/api/v1/models/m2/switch"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modelId").value("m2"));
    }

    @Test
    void switchModel_notFound_returns404() throws Exception {
        doThrow(new java.io.IOException("not found")).when(modelService).switchModel("bad");

        mockMvc.perform(post("/api/v1/models/bad/switch"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));
    }
}
