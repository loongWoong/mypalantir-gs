package com.mypalantir.controller;

import com.mypalantir.service.DatabaseMetadataService;
import com.mypalantir.service.DatabaseService;
import com.mypalantir.service.TableSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DatabaseControllerTest {

    private MockMvc mockMvc;
    private DatabaseService databaseService;
    private DatabaseMetadataService databaseMetadataService;

    @BeforeEach
    void setUp() {
        databaseService = mock(DatabaseService.class);
        databaseMetadataService = mock(DatabaseMetadataService.class);
        TableSyncService tableSyncService = mock(TableSyncService.class);
        DatabaseController controller = new DatabaseController();
        ReflectionTestUtils.setField(controller, "databaseService", databaseService);
        ReflectionTestUtils.setField(controller, "databaseMetadataService", databaseMetadataService);
        ReflectionTestUtils.setField(controller, "tableSyncService", tableSyncService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getDefaultDatabaseId_returns200AndId() throws Exception {
        when(databaseService.getOrCreateDefaultDatabase()).thenReturn("db-default");

        mockMvc.perform(get("/api/v1/database/default-id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("db-default"));
    }

    @Test
    void listDatabases_returns200() throws Exception {
        when(databaseService.getAllDatabases()).thenReturn(List.of(Map.of("id", "db1", "name", "default")));

        mockMvc.perform(get("/api/v1/database/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("db1"));
    }
}
