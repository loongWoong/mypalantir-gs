package com.mypalantir.controller;

import com.mypalantir.meta.DataSourceConfig;
import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;
import com.mypalantir.service.DataSourceTestService;
import com.mypalantir.service.SchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SchemaController 单元测试（独立 MockMvc，Mock SchemaService / DataSourceTestService）
 */
class SchemaControllerTest {

    private MockMvc mockMvc;
    private SchemaService schemaService;
    private DataSourceTestService testService;

    @BeforeEach
    void setUp() {
        schemaService = mock(SchemaService.class);
        testService = mock(DataSourceTestService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new SchemaController(schemaService, testService)
        ).build();
    }

    @Test
    void listObjectTypes_returnsOkAndList() throws Exception {
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        ot.setDisplayName("车辆");
        when(schemaService.listObjectTypes()).thenReturn(List.of(ot));

        mockMvc.perform(get("/api/v1/schema/object-types").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].name").value("Vehicle"));

        verify(schemaService).listObjectTypes();
    }

    @Test
    void getObjectType_whenFound_returnsOk() throws Exception {
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        when(schemaService.getObjectType("Vehicle")).thenReturn(ot);

        mockMvc.perform(get("/api/v1/schema/object-types/Vehicle").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Vehicle"));

        verify(schemaService).getObjectType("Vehicle");
    }

    @Test
    void getObjectType_whenNotFound_returns404() throws Exception {
        when(schemaService.getObjectType(anyString())).thenThrow(new Loader.NotFoundException("object type 'Missing' not found"));

        mockMvc.perform(get("/api/v1/schema/object-types/Missing").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void getObjectTypeProperties_whenFound_returnsOk() throws Exception {
        Property p = new Property();
        p.setName("id");
        p.setDataType("string");
        p.setRequired(true);
        when(schemaService.getObjectTypeProperties("Vehicle")).thenReturn(List.of(p));

        mockMvc.perform(get("/api/v1/schema/object-types/Vehicle/properties").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("id"));
    }

    @Test
    void listLinkTypes_returnsOk() throws Exception {
        LinkType lt = new LinkType();
        lt.setName("owns");
        lt.setSourceType("Vehicle");
        lt.setTargetType("Person");
        when(schemaService.listLinkTypes()).thenReturn(List.of(lt));

        mockMvc.perform(get("/api/v1/schema/link-types").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("owns"));
    }

    @Test
    void listDataSources_returnsOk() throws Exception {
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId("ds1");
        ds.setType("jdbc");
        when(schemaService.listDataSources()).thenReturn(List.of(ds));

        mockMvc.perform(get("/api/v1/schema/data-sources").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("ds1"));
    }

    @Test
    void testConnection_whenSuccess_returnsOk() throws Exception {
        when(testService.testConnection("ds1"))
            .thenReturn(DataSourceTestService.TestResult.success("OK", null));

        mockMvc.perform(post("/api/v1/schema/data-sources/ds1/test").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(true));
    }
}
