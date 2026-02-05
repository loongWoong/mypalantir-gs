package com.mypalantir.controller;

import com.mypalantir.service.EtlDefinitionIntegrationService;
import com.mypalantir.service.EtlModelBuilderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ETL模型控制器
 * 提供ETL模型构建和创建的REST API
 */
@RestController
@RequestMapping("/api/v1/etl-model")
public class EtlModelController {
    
    @Autowired
    private EtlModelBuilderService etlModelBuilderService;
    
    @Autowired
    private EtlDefinitionIntegrationService etlDefinitionIntegrationService;
    
    /**
     * 为对象类型构建并创建 ETL 模型
     * 
     * @param objectType 对象类型名称
     * @param mappingId 映射关系ID（可选）
     * @param targetDatasourceId 目标数据源ID（可选）
     * @param targetTableName 目标表名（可选）
     */
    @PostMapping("/build")
    public ResponseEntity<ApiResponse<Map<String, Object>>> buildEtlModel(
        @RequestParam String objectType,
        @RequestParam(required = false) String mappingId,
        @RequestParam(required = false) String targetDatasourceId,
        @RequestParam(required = false) String targetTableName
    ) {
        try {
            // 1. 构建 ETL 模型
            Map<String, Object> etlModel = etlModelBuilderService.buildEtlModel(
                objectType, mappingId, targetDatasourceId, targetTableName
            );
            
            // 2. 调用 dome-scheduler 接口创建 ETL 定义
            Map<String, Object> result = etlDefinitionIntegrationService.createEtlDefinition(etlModel);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("etlModel", etlModel);
            responseData.put("createResult", result);
            responseData.put("success", true);
            
            return ResponseEntity.ok(ApiResponse.success(responseData));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to build ETL model: " + e.getMessage()));
        }
    }
    
    /**
     * 批量构建 ETL 模型
     */
    @PostMapping("/build-batch")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> buildEtlModelsBatch(
        @RequestBody List<String> objectTypes,
        @RequestParam(required = false) String targetDatasourceId
    ) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        
        for (String objectType : objectTypes) {
            try {
                Map<String, Object> etlModel = etlModelBuilderService.buildEtlModel(
                    objectType, null, targetDatasourceId, null
                );
                Map<String, Object> result = etlDefinitionIntegrationService.createEtlDefinition(etlModel);
                
                Map<String, Object> resultItem = new HashMap<>();
                resultItem.put("objectType", objectType);
                resultItem.put("success", true);
                resultItem.put("etlModel", etlModel);
                resultItem.put("createResult", result);
                results.add(resultItem);
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("objectType", objectType);
                errorResult.put("success", false);
                errorResult.put("error", e.getMessage());
                results.add(errorResult);
            }
        }
        
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}


