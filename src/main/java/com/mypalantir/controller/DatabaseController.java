package com.mypalantir.controller;

import com.mypalantir.service.DatabaseMetadataService;
import com.mypalantir.service.DatabaseService;
import com.mypalantir.service.TableSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/database")
public class DatabaseController {
    @Autowired
    private DatabaseMetadataService databaseMetadataService;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private TableSyncService tableSyncService;

    @GetMapping("/default-id")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDefaultDatabaseId() {
        try {
            String databaseId = databaseService.getOrCreateDefaultDatabase();
            Map<String, String> result = new HashMap<>();
            result.put("id", databaseId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get default database: " + e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listDatabases() {
        try {
            List<Map<String, Object>> databases = databaseService.getAllDatabases();
            return ResponseEntity.ok(ApiResponse.success(databases));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to list databases: " + e.getMessage()));
        }
    }

    @GetMapping("/tables")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTables(
            @RequestParam(required = false) String databaseId) {
        try {
            // 如果没有提供databaseId，使用默认数据库
            if (databaseId == null || databaseId.isEmpty()) {
                databaseId = databaseService.getOrCreateDefaultDatabase();
            }
            
            List<Map<String, Object>> tables = databaseMetadataService.getTables(databaseId);
            
            // 为每个表创建或获取table实例，并添加table_id
            for (Map<String, Object> table : tables) {
                String tableName = (String) table.get("name");
                String schemaName = (String) table.get("schema");
                String tableId = databaseService.getOrCreateTable(databaseId, tableName, schemaName);
                table.put("id", tableId);
            }
            
            return ResponseEntity.ok(ApiResponse.success(tables));
        } catch (SQLException | IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get tables: " + e.getMessage()));
        }
    }

    @GetMapping("/tables/{tableName}/columns")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getColumns(
            @RequestParam(required = false) String databaseId,
            @PathVariable String tableName) {
        try {
            // 如果没有提供databaseId，使用默认数据库
            if (databaseId == null || databaseId.isEmpty()) {
                databaseId = databaseService.getOrCreateDefaultDatabase();
            }
            
            List<Map<String, Object>> columns = databaseMetadataService.getColumns(databaseId, tableName);
            return ResponseEntity.ok(ApiResponse.success(columns));
        } catch (SQLException | IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get columns: " + e.getMessage()));
        }
    }

    @GetMapping("/tables/{tableName}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTableInfo(
            @RequestParam(required = false) String databaseId,
            @PathVariable String tableName) {
        try {
            // 如果没有提供databaseId，使用默认数据库
            if (databaseId == null || databaseId.isEmpty()) {
                databaseId = databaseService.getOrCreateDefaultDatabase();
            }
            
            Map<String, Object> tableInfo = databaseMetadataService.getTableInfo(databaseId, tableName);
            return ResponseEntity.ok(ApiResponse.success(tableInfo));
        } catch (SQLException | IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get table info: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/sync-tables", produces = "application/json")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncTables(
            @RequestParam String databaseId) {
        try {
            TableSyncService.SyncResult result = tableSyncService.syncTablesFromDatabase(databaseId);
            return ResponseEntity.ok(ApiResponse.success(result.toMap()));
        } catch (SQLException | IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to sync tables: " + e.getMessage()));
        }
    }
}
