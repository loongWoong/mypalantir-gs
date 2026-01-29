package com.mypalantir.controller;

import com.mypalantir.meta.Loader;
import com.mypalantir.service.DataValidator;
import com.mypalantir.service.InstanceService;
import com.mypalantir.service.MappedDataService;
import com.mypalantir.repository.InstanceStorage;
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
@RequestMapping("/api/v1/instances")
public class InstanceController {
    private final InstanceService instanceService;

    @Autowired
    private MappedDataService mappedDataService;

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
            @RequestParam(required = false) String mappingId,
            @RequestParam Map<String, String> allParams) {
        try {
            // 如果指定了mappingId，使用映射数据查询
            if (mappingId != null && !mappingId.isEmpty()) {
                InstanceStorage.ListResult result = mappedDataService.queryMappedInstances(objectType, mappingId, offset, limit);
                
                Map<String, Object> response = new HashMap<>();
                response.put("items", result.getItems());
                response.put("total", result.getTotal());
                response.put("offset", offset);
                response.put("limit", limit);
                response.put("from_mapping", true);

                return ResponseEntity.ok(ApiResponse.success(response));
            }

            // 否则使用常规查询
            // 提取过滤参数
            Map<String, Object> filters = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                if (!"offset".equals(key) && !"limit".equals(key) && !"mappingId".equals(key)) {
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
        } catch (IOException | SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to list instances: " + e.getMessage()));
        }
    }

    @PostMapping("/{objectType}/sync-from-mapping/{mappingId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncFromMapping(
            @PathVariable String objectType,
            @PathVariable String mappingId,
            @RequestParam(required = false) String targetDatabaseId) {
        try {
            // 默认使用同步抽取方法（构建表+抽取数据）
            // targetDatabaseId为null时，使用源数据库作为目标数据库
            MappedDataService.SyncExtractResult result = mappedDataService.syncExtractWithTable(
                objectType, mappingId, targetDatabaseId);
            return ResponseEntity.ok(ApiResponse.success(result.toMap()));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException | SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to sync data: " + e.getMessage()));
        }
    }

    /**
     * 批量获取实例
     * @param objectType 对象类型
     * @param requestBody 包含ids数组的请求体
     * @return 实例Map，key为ID，value为实例数据（如果不存在则为null）
     */
    @PostMapping("/{objectType}/batch")
    public ResponseEntity<ApiResponse<Map<String, Map<String, Object>>>> getInstancesBatch(
            @PathVariable String objectType,
            @RequestBody Map<String, Object> requestBody) {
        try {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) requestBody.get("ids");
            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "ids parameter is required and cannot be empty"));
            }

            Map<String, Map<String, Object>> instances = instanceService.getInstancesBatch(objectType, ids);
            return ResponseEntity.ok(ApiResponse.success(instances));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get instances: " + e.getMessage()));
        }
    }

    /**
     * 批量获取多个对象类型的实例
     * @param requestBody 包含queries数组的请求体，每个query包含objectType和ids
     * @return Map，key为"objectType:id"，value为实例数据（如果不存在则为null）
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<String, Map<String, Object>>>> getInstancesBatchMultiType(
            @RequestBody Map<String, Object> requestBody) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> queries = (List<Map<String, Object>>) requestBody.get("queries");
            if (queries == null || queries.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "queries parameter is required and cannot be empty"));
            }

            Map<String, List<String>> typeIdMap = new HashMap<>();
            for (Map<String, Object> query : queries) {
                String objectType = (String) query.get("objectType");
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) query.get("ids");
                if (objectType == null || ids == null || ids.isEmpty()) {
                    continue;
                }
                typeIdMap.put(objectType, ids);
            }

            if (typeIdMap.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "No valid queries found"));
            }

            Map<String, Map<String, Object>> instances = instanceService.getInstancesBatchMultiType(typeIdMap);
            return ResponseEntity.ok(ApiResponse.success(instances));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get instances: " + e.getMessage()));
        }
    }
}

