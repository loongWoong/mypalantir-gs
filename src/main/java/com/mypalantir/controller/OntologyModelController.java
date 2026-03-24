package com.mypalantir.controller;

import com.mypalantir.config.Config;
import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Validator;
import com.mypalantir.service.MappingService;
import com.mypalantir.service.OntologyModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

    @Autowired
    private MappingService mappingService;

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
     * 获取指定模型的对象类型（附带 mapping 信息）
     * 用于数据对账等需要同时知道本体结构和数据源映射的场景。
     * 对每个 ObjectType，若 YAML 中没有 data_source，则从 MappingService 查询并注入。
     */
    @GetMapping("/{modelId}/object-types-with-mapping")
    public ResponseEntity<ApiResponse<List<ObjectType>>> getModelObjectTypesWithMapping(
            @PathVariable String modelId) {
        try {
            List<ObjectType> objectTypes = modelService.getObjectTypes(modelId);
            List<ObjectType> enriched = new ArrayList<>();
            for (ObjectType ot : objectTypes) {
                if (ot.getDataSource() != null && ot.getDataSource().isConfigured()) {
                    enriched.add(ot);
                    continue;
                }
                // 从 MappingService 查询 mapping，注入 data_source
                try {
                    List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(ot.getName());
                    if (mappings != null && !mappings.isEmpty()) {
                        Map<String, Object> m = mappings.get(0);
                        DataSourceMapping ds = buildDataSourceFromMapping(m);
                        if (ds != null) {
                            ot.setDataSource(ds);
                        }
                    }
                } catch (Exception ignored) {
                    // mapping 查询失败不影响返回
                }
                enriched.add(ot);
            }
            return ResponseEntity.ok(ApiResponse.success(enriched));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "Failed to load model: " + e.getMessage()));
        }
    }

    /**
     * 从 mapping Map 构建 DataSourceMapping。
     * mapping 中只存了 table_id 和 table_name，需要通过 instanceStorage 查 table 节点拿 database_id。
     */
    private DataSourceMapping buildDataSourceFromMapping(Map<String, Object> m) {
        String tableId = (String) m.get("table_id");
        String tableName = (String) m.get("table_name");
        if (tableId == null && tableName == null) return null;

        // 通过 table_id 查 table 实例，获取 database_id（即 connection_id）
        String connectionId = null;
        try {
            if (tableId != null) {
                Map<String, Object> tableInstance = mappingService.getTableInstance(tableId);
                if (tableInstance != null) {
                    connectionId = (String) tableInstance.get("database_id");
                    if (tableName == null) {
                        tableName = (String) tableInstance.get("name");
                    }
                }
            }
        } catch (Exception ignored) {}

        if (connectionId == null || tableName == null) return null;

        // 主键列
        @SuppressWarnings("unchecked")
        List<String> pkCols = (List<String>) m.get("primary_key_columns");
        String pkCol = (String) m.get("primary_key_column");
        String idColumn = (pkCols != null && !pkCols.isEmpty()) ? pkCols.get(0) : pkCol;

        // 字段映射（column -> property）反转为（property -> column）
        @SuppressWarnings("unchecked")
        Map<String, String> colPropMap = (Map<String, String>) m.get("column_property_mappings");
        Map<String, String> fieldMapping = new HashMap<>();
        if (colPropMap != null) {
            colPropMap.forEach((col, prop) -> {
                if (prop != null) fieldMapping.put(prop, col);
            });
        }

        DataSourceMapping ds = new DataSourceMapping();
        ds.setConnectionId(connectionId);
        ds.setTable(tableName);
        ds.setIdColumn(idColumn);
        ds.setPrimaryKeyColumns(pkCols);
        ds.setFieldMapping(fieldMapping);
        return ds;
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

