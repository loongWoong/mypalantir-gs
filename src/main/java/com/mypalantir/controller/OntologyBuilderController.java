package com.mypalantir.controller;

import com.mypalantir.meta.OntologySchema;
import com.mypalantir.service.OntologyBuilderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        Map<String, Object> payload = Map.of(
            "valid", result.isValid(),
            "errors", result.getErrors(),
            "yaml", result.getYaml()
        );
        return ResponseEntity.ok(ApiResponse.success(payload));
    }
}
