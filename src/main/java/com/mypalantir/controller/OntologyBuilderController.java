package com.mypalantir.controller;

import com.mypalantir.meta.OntologySchema;
import com.mypalantir.service.OntologyBuilderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 本体可视化构建控制器。
 */
@RestController
@RequestMapping("/api/v1/ontology-builder")
public class OntologyBuilderController {
    private final OntologyBuilderService ontologyBuilderService;

    public OntologyBuilderController(OntologyBuilderService ontologyBuilderService) {
        this.ontologyBuilderService = ontologyBuilderService;
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validate(@RequestBody OntologySchema schema) {
        OntologyBuilderService.ValidationResult result = ontologyBuilderService.validateAndGenerate(schema);
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("valid", result.isValid());
        payload.put("errors", result.getErrors());
        payload.put("warnings", result.getWarnings() != null ? result.getWarnings() : java.util.Collections.emptyList());
        payload.put("yaml", result.getYaml());
        return ResponseEntity.ok(ApiResponse.success(payload));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(
            @RequestBody OntologySchema schema,
            @RequestParam(required = false, defaultValue = "ontology-model") String filename) {
        try {
            String filePath = ontologyBuilderService.saveToOntologyFolder(schema, filename);
            Map<String, Object> payload = Map.of(
                "success", true,
                "filePath", filePath,
                "message", "文件已成功保存到ontology文件夹"
            );
            return ResponseEntity.ok(ApiResponse.success(payload));
        } catch (Exception e) {
            Map<String, Object> payload = Map.of(
                "success", false,
                "message", "保存失败: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "保存失败: " + e.getMessage()));
        }
    }

    /**
     * 列出ontology文件夹中的所有YAML文件
     */
    @GetMapping("/files")
    public ResponseEntity<ApiResponse<List<String>>> listFiles() {
        try {
            List<String> files = ontologyBuilderService.listOntologyFiles();
            return ResponseEntity.ok(ApiResponse.success(files));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "获取文件列表失败: " + e.getMessage()));
        }
    }

    /**
     * 加载指定的YAML文件
     */
    @GetMapping("/load")
    public ResponseEntity<ApiResponse<OntologySchema>> loadFile(
            @RequestParam String filename) {
        try {
            OntologySchema schema = ontologyBuilderService.loadFromOntologyFolder(filename);
            return ResponseEntity.ok(ApiResponse.success(schema));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "加载文件失败: " + e.getMessage()));
        }
    }
}
