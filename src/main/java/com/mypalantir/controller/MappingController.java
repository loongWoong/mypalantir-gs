package com.mypalantir.controller;

import com.mypalantir.meta.Loader;
import com.mypalantir.service.MappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mappings")
public class MappingController {
    @Autowired
    private MappingService mappingService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> createMapping(
            @RequestBody Map<String, Object> request) {
        try {
            String objectType = (String) request.get("object_type");
            String tableId = (String) request.get("table_id");
            @SuppressWarnings("unchecked")
            Map<String, String> columnPropertyMappings = (Map<String, String>) request.get("column_property_mappings");
            
            // 支持新格式（数组）和旧格式（单个字符串）
            @SuppressWarnings("unchecked")
            List<String> primaryKeyColumns = (List<String>) request.get("primary_key_columns");
            String primaryKeyColumn = (String) request.get("primary_key_column");
            
            // 调试日志：记录接收到的数据
            System.out.println("[MappingController.createMapping] Received primary_key_columns: " + primaryKeyColumns);
            System.out.println("[MappingController.createMapping] Received primary_key_column: " + primaryKeyColumn);
            
            // 如果提供了新格式的数组，使用它；否则使用旧格式的单个字符串
            if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
                System.out.println("[MappingController.createMapping] Using primary_key_columns array: " + primaryKeyColumns);
                String mappingId = mappingService.createMapping(objectType, tableId, columnPropertyMappings, primaryKeyColumns);
                Map<String, String> result = new HashMap<>();
                result.put("id", mappingId);
                return ResponseEntity.ok(ApiResponse.success(result));
            } else if (primaryKeyColumn != null && !primaryKeyColumn.isEmpty()) {
                // 兼容旧格式：单个主键列转换为数组
                String mappingId = mappingService.createMapping(objectType, tableId, columnPropertyMappings, java.util.Arrays.asList(primaryKeyColumn));
                Map<String, String> result = new HashMap<>();
                result.put("id", mappingId);
                return ResponseEntity.ok(ApiResponse.success(result));
            } else {
                String mappingId = mappingService.createMapping(objectType, tableId, columnPropertyMappings, null);
                Map<String, String> result = new HashMap<>();
                result.put("id", mappingId);
                return ResponseEntity.ok(ApiResponse.success(result));
            }
        } catch (Loader.NotFoundException | IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/{mappingId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMapping(
            @PathVariable String mappingId) {
        try {
            Map<String, Object> mapping = mappingService.getMapping(mappingId);
            return ResponseEntity.ok(ApiResponse.success(mapping));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @GetMapping("/by-object-type/{objectType}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMappingsByObjectType(
            @PathVariable String objectType) {
        try {
            List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectType);
            return ResponseEntity.ok(ApiResponse.success(mappings));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get mappings: " + e.getMessage()));
        }
    }

    @GetMapping("/by-table/{tableId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMappingsByTable(
            @PathVariable String tableId) {
        try {
            List<Map<String, Object>> mappings = mappingService.getMappingsByTable(tableId);
            return ResponseEntity.ok(ApiResponse.success(mappings));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get mappings: " + e.getMessage()));
        }
    }

    @PutMapping("/{mappingId}")
    public ResponseEntity<ApiResponse<Void>> updateMapping(
            @PathVariable String mappingId,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> columnPropertyMappings = (Map<String, String>) request.get("column_property_mappings");
            
            // 支持新格式（数组）和旧格式（单个字符串）
            @SuppressWarnings("unchecked")
            List<String> primaryKeyColumns = (List<String>) request.get("primary_key_columns");
            String primaryKeyColumn = (String) request.get("primary_key_column");
            
            // 调试日志：记录接收到的数据
            System.out.println("[MappingController.updateMapping] Received primary_key_columns: " + primaryKeyColumns);
            System.out.println("[MappingController.updateMapping] Received primary_key_column: " + primaryKeyColumn);
            
            // 如果提供了新格式的数组，使用它；否则使用旧格式的单个字符串
            if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
                System.out.println("[MappingController.updateMapping] Using primary_key_columns array: " + primaryKeyColumns);
                mappingService.updateMapping(mappingId, columnPropertyMappings, primaryKeyColumns);
            } else if (primaryKeyColumn != null && !primaryKeyColumn.isEmpty()) {
                // 兼容旧格式：单个主键列转换为数组
                mappingService.updateMapping(mappingId, columnPropertyMappings, java.util.Arrays.asList(primaryKeyColumn));
            } else {
                mappingService.updateMapping(mappingId, columnPropertyMappings, null);
            }
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to update mapping: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{mappingId}")
    public ResponseEntity<ApiResponse<Void>> deleteMapping(
            @PathVariable String mappingId) {
        try {
            mappingService.deleteMapping(mappingId);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
        }
    }
}
