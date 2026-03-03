package com.mypalantir.controller;

import com.mypalantir.service.EtlDefinitionIntegrationService;
import com.mypalantir.service.EtlModelBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EtlModelControllerTest {

    private MockMvc mockMvc;
    private EtlModelBuilderService etlModelBuilderService;
    private EtlDefinitionIntegrationService etlDefinitionIntegrationService;

    @BeforeEach
    void setUp() {
        etlModelBuilderService = mock(EtlModelBuilderService.class);
        etlDefinitionIntegrationService = mock(EtlDefinitionIntegrationService.class);
        EtlModelController controller = new EtlModelController();
        ReflectionTestUtils.setField(controller, "etlModelBuilderService", etlModelBuilderService);
        ReflectionTestUtils.setField(controller, "etlDefinitionIntegrationService", etlDefinitionIntegrationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void buildEtlModel_success_returns200() throws Exception {
        Map<String, Object> etlModel = Map.of("objectType", "Vehicle", "mappingId", "m1");
        Map<String, Object> createResult = Map.of("id", "etl-def-1");
        when(etlModelBuilderService.buildEtlModel(eq("Vehicle"), isNull(), isNull(), isNull())).thenReturn(etlModel);
        when(etlDefinitionIntegrationService.createEtlDefinition(any())).thenReturn(createResult);

        mockMvc.perform(post("/api/v1/etl-model/build").param("objectType", "Vehicle"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(true))
            .andExpect(jsonPath("$.data.etlModel.objectType").value("Vehicle"));
    }

    @Test
    void buildEtlModel_illegalArgument_returns400() throws Exception {
        when(etlModelBuilderService.buildEtlModel(eq("Missing"), isNull(), isNull(), isNull()))
            .thenThrow(new IllegalArgumentException("Object type not found"));

        mockMvc.perform(post("/api/v1/etl-model/build").param("objectType", "Missing"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }
}
