package com.mypalantir.controller;

import com.mypalantir.reasoning.ReasoningService;
import com.mypalantir.reasoning.engine.InferenceResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/reasoning")
public class ReasoningController {

    private final ReasoningService reasoningService;

    public ReasoningController(ReasoningService reasoningService) {
        this.reasoningService = reasoningService;
    }

    /**
     * 对单个 Passage 执行推理
     * POST /api/reasoning/infer
     * Body: { "passage_id": "xxx" }
     */
    @PostMapping("/infer")
    public ApiResponse<Map<String, Object>> infer(@RequestBody Map<String, String> request) {
        String passageId = request.get("passage_id");
        if (passageId == null || passageId.isEmpty()) {
            return ApiResponse.error(400, "passage_id is required");
        }
        try {
            InferenceResult result = reasoningService.inferPassage(passageId);
            return ApiResponse.success(result.toMap());
        } catch (Exception e) {
            return ApiResponse.error(500, "Inference failed: " + e.getMessage());
        }
    }

    /**
     * 批量推理
     * POST /api/reasoning/batch
     * Body: { "limit": 10 }
     */
    @PostMapping("/batch")
    public ApiResponse<List<Map<String, Object>>> inferBatch(@RequestBody Map<String, Integer> request) {
        int limit = request.getOrDefault("limit", 10);
        try {
            List<Map<String, Object>> results = reasoningService.inferBatch(limit);
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.error(500, "Batch inference failed: " + e.getMessage());
        }
    }

    /**
     * 获取推理引擎状态
     * GET /api/reasoning/status
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("parsedRules", reasoningService.getParsedRuleCount());
        status.put("registeredFunctions", reasoningService.getRegisteredFunctions());
        return ApiResponse.success(status);
    }
}
