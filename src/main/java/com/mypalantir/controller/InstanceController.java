package com.mypalantir.controller;

import com.mypalantir.meta.Loader;
import com.mypalantir.service.DataValidator;
import com.mypalantir.service.InstanceService;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/instances")
public class InstanceController {
    private final InstanceService instanceService;

    public InstanceController(InstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @PostMapping("/{objectType}")
    public ResponseEntity<ApiResponse<Map<String, String>>> createInstance(
            @PathVariable String objectType,
            @RequestBody Map<String, Object> data) {
        try {
            String id = instanceService.createInstance(objectType, data);
            Map<String, String> result = new HashMap<>();
            result.put("id", id);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Loader.NotFoundException | DataValidator.ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to create instance: " + e.getMessage()));
        }
    }

    @GetMapping("/{objectType}/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInstance(
            @PathVariable String objectType,
            @PathVariable String id) {
        try {
            Map<String, Object> instance = instanceService.getInstance(objectType, id);
            return ResponseEntity.ok(ApiResponse.success(instance));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @PutMapping("/{objectType}/{id}")
    public ResponseEntity<ApiResponse<Void>> updateInstance(
            @PathVariable String objectType,
            @PathVariable String id,
            @RequestBody Map<String, Object> data) {
        try {
            instanceService.updateInstance(objectType, id, data);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Loader.NotFoundException | DataValidator.ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to update instance: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{objectType}/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteInstance(
            @PathVariable String objectType,
            @PathVariable String id) {
        try {
            instanceService.deleteInstance(objectType, id);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @GetMapping("/{objectType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listInstances(
            @PathVariable String objectType,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam Map<String, String> allParams) {
        try {
            // 提取过滤参数
            Map<String, Object> filters = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                if (!"offset".equals(key) && !"limit".equals(key)) {
                    filters.put(key, entry.getValue());
                }
            }

            InstanceStorage.ListResult result = instanceService.listInstances(objectType, offset, limit, filters);
            
            Map<String, Object> response = new HashMap<>();
            response.put("items", result.getItems());
            response.put("total", result.getTotal());
            response.put("offset", offset);
            response.put("limit", limit);

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to list instances: " + e.getMessage()));
        }
    }
}

