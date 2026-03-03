package com.mypalantir.controller;

import com.mypalantir.meta.OntologySchema;
import com.mypalantir.service.OntologyBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OntologyBuilderControllerTest {

    private MockMvc mockMvc;
    private OntologyBuilderService ontologyBuilderService;

    @BeforeEach
    void setUp() {
        ontologyBuilderService = mock(OntologyBuilderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new OntologyBuilderController(ontologyBuilderService)
        ).build();
    }

    @Test
    void validate_validSchema_returns200WithValidTrue() throws Exception {
        OntologyBuilderService.ValidationResult result = new OntologyBuilderService.ValidationResult(
            true, List.of(), List.of(), "version: \"1.0\"\nnamespace: test\n");
        when(ontologyBuilderService.validateAndGenerate(any(OntologySchema.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/ontology-builder/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"1.0\",\"namespace\":\"test\",\"object_types\":[],\"link_types\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true))
            .andExpect(jsonPath("$.data.yaml").exists());
    }

    @Test
    void listFiles_returns200() throws Exception {
        when(ontologyBuilderService.listOntologyFiles()).thenReturn(List.of("schema.yaml", "other.yaml"));

        mockMvc.perform(get("/api/v1/ontology-builder/files"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value("schema.yaml"));
    }

    @Test
    void loadFile_success_returns200() throws Exception {
        OntologySchema schema = new OntologySchema();
        schema.setVersion("1.0");
        schema.setNamespace("test");
        when(ontologyBuilderService.loadFromOntologyFolder("schema.yaml")).thenReturn(schema);

        mockMvc.perform(get("/api/v1/ontology-builder/load").param("filename", "schema.yaml"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value("1.0"));
    }

    @Test
    void loadFile_notFound_returns400Or500() throws Exception {
        when(ontologyBuilderService.loadFromOntologyFolder("missing.yaml"))
            .thenThrow(new IllegalArgumentException("File not found"));

        mockMvc.perform(get("/api/v1/ontology-builder/load").param("filename", "missing.yaml"))
            .andExpect(status().isBadRequest());
    }
}
