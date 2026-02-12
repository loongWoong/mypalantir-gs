package com.mypalantir.controller;

import com.mypalantir.meta.OntologySchema;
import com.mypalantir.service.OntologyBuilderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(required = false, defaultValue = "ontology-model") String filename,
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String commitMessage) {
        try {
            String filePath = ontologyBuilderService.saveToOntologyFolder(schema, filename, workspaceId, commitMessage);
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("success", true);
            payload.put("filePath", filePath);
            payload.put("version", schema.getVersion());
            payload.put("message", "文件已成功保存到ontology文件夹");
            return ResponseEntity.ok(ApiResponse.success(payload));
        } catch (Exception e) {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("success", false);
            payload.put("message", "保存失败: " + e.getMessage());
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

    /**
     * 获取版本历史列表
     */
    @GetMapping("/versions/{filename}/history")
    public ResponseEntity<ApiResponse<List<com.mypalantir.meta.OntologyVersion>>> getVersionHistory(
            @PathVariable String filename) {
        try {
            // URL解码文件名（处理特殊字符）
            String decodedFilename = java.net.URLDecoder.decode(filename, "UTF-8");
            List<com.mypalantir.meta.OntologyVersion> history = ontologyBuilderService.getVersionHistory(decodedFilename);
            return ResponseEntity.ok(ApiResponse.success(history));
        } catch (Exception e) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OntologyBuilderController.class);
            logger.error("获取版本历史失败，文件名: {}, 错误: {}", filename, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "获取版本历史失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定版本
     */
    @GetMapping("/versions/{filename}/{version}")
    public ResponseEntity<ApiResponse<OntologySchema>> getVersion(
            @PathVariable String filename,
            @PathVariable String version) {
        try {
            OntologySchema schema = ontologyBuilderService.getVersion(filename, version);
            return ResponseEntity.ok(ApiResponse.success(schema));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "获取版本失败: " + e.getMessage()));
        }
    }

    /**
     * 对比两个版本
     */
    @PostMapping("/versions/{filename}/compare")
    public ResponseEntity<ApiResponse<com.mypalantir.service.VersionComparator.DiffResult>> compareVersions(
            @PathVariable String filename,
            @RequestBody Map<String, String> request) {
        try {
            String version1 = request.get("version1");
            String version2 = request.get("version2");
            if (version1 == null || version2 == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "需要提供version1和version2参数"));
            }
            com.mypalantir.service.VersionComparator.DiffResult diff = 
                ontologyBuilderService.compareVersions(filename, version1, version2);
            return ResponseEntity.ok(ApiResponse.success(diff));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "版本对比失败: " + e.getMessage()));
        }
    }

    /**
     * 回滚到指定版本
     */
    @PostMapping("/versions/{filename}/rollback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollback(
            @PathVariable String filename,
            @RequestParam String version) {
        try {
            String filePath = ontologyBuilderService.rollbackToVersion(filename, version);
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("success", true);
            payload.put("filePath", filePath);
            payload.put("version", version);
            payload.put("message", "已回滚到版本 " + version);
            return ResponseEntity.ok(ApiResponse.success(payload));
        } catch (Exception e) {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("success", false);
            payload.put("message", "回滚失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "回滚失败: " + e.getMessage()));
        }
    }
}
