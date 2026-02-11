package com.mypalantir.controller;

import com.mypalantir.config.Config;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Validator;
import com.mypalantir.service.OntologyModelService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Ontology 模型管理控制器
 */
@RestController
@RequestMapping("/api/v1/models")
public class OntologyModelController {
    private final OntologyModelService modelService;
    private final Config config;
    
    public OntologyModelController(OntologyModelService modelService, Config config) {
        this.modelService = modelService;
        this.config = config;
    }
    
    /**
     * 列出所有可用的模型
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OntologyModelService.ModelInfo>>> listModels() {
        return ResponseEntity.ok(ApiResponse.success(modelService.listAvailableModels()));
    }
    
    /**
     * 获取指定模型的对象类型
     */
    @GetMapping("/{modelId}/object-types")
    public ResponseEntity<ApiResponse<List<ObjectType>>> getModelObjectTypes(@PathVariable String modelId) {
        try {
            List<ObjectType> objectTypes = modelService.getObjectTypes(modelId);
            return ResponseEntity.ok(ApiResponse.success(objectTypes));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "Failed to load model: " + e.getMessage()));
        }
    }
    
    /**
     * 获取当前使用的模型
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCurrentModel() {
        String currentModel = modelService.getCurrentModelId();
        String filePath = modelService.getCurrentModelPath();
        Map<String, String> result = Map.of(
            "modelId", currentModel != null ? currentModel : config.getOntologyModel(),
            "filePath", filePath != null ? filePath : config.getSchemaFilePath()
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 切换模型（热更新）
     */
    @PostMapping("/{modelId}/switch")
    public ResponseEntity<ApiResponse<Map<String, String>>> switchModel(@PathVariable String modelId) {
        try {
            modelService.switchModel(modelId);
            String filePath = modelService.getCurrentModelPath();
            Map<String, String> result = Map.of(
                "modelId", modelId,
                "filePath", filePath != null ? filePath : ""
            );
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "Model file not found: " + e.getMessage()));
        } catch (Validator.ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "Schema validation failed: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to switch model: " + e.getMessage()));
        }
    }
}

