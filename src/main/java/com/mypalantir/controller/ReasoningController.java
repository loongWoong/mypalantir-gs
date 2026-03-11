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
     * 对当前本体模型下指定对象类型与实例执行推理（按右上角所选本体模型）。
     * POST /api/reasoning/infer
     * Body: { "object_type": "Path", "instance_id": "xxx" } 或兼容 { "passage_id": "xxx" }（等价 object_type=Passage）
     */
    @PostMapping("/infer")
    public ApiResponse<Map<String, Object>> infer(@RequestBody Map<String, String> request) {
        String objectType = request.get("object_type");
        String instanceId = request.get("instance_id");
        String passageId = request.get("passage_id");
        if (instanceId == null && passageId != null && !passageId.isEmpty()) {
            objectType = "Passage";
            instanceId = passageId;
        }
        if (objectType == null || objectType.isEmpty() || instanceId == null || instanceId.isEmpty()) {
            return ApiResponse.error(400, "object_type and instance_id are required (or passage_id for backward compat)");
        }
        try {
            InferenceResult result = reasoningService.inferInstance(objectType, instanceId);
            return ApiResponse.success(result.toMap());
        } catch (Exception e) {
            return ApiResponse.error(500, "Inference failed: " + e.getMessage());
        }
    }

    /**
     * 批量推理：对当前模型下指定类型的实例执行推理
     * POST /api/reasoning/batch
     * Body: { "object_type": "Path", "limit": 10 } 或 { "limit": 10 }（默认 object_type=Passage）
     */
    @PostMapping("/batch")
    public ApiResponse<List<Map<String, Object>>> inferBatch(@RequestBody Map<String, Object> request) {
        String objectType = request.containsKey("object_type") ? String.valueOf(request.get("object_type")) : "Passage";
        int limit = request.containsKey("limit") ? ((Number) request.get("limit")).intValue() : 10;
        try {
            List<Map<String, Object>> results = reasoningService.inferBatch(objectType, limit);
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.error(500, "Batch inference failed: " + e.getMessage());
        }
    }

    /**
     * 获取当前本体中有规则的对象类型（推理根类型），供前端按当前模型选择
     * GET /api/reasoning/root-types
     */
    @GetMapping("/root-types")
    public ApiResponse<List<String>> rootTypes() {
        return ApiResponse.success(reasoningService.listInferenceRootTypes());
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

    /**
     * CEL 表达式校验（用于前端查询验证、脚本编辑）
     * POST /api/v1/reasoning/cel/validate
     * Body: { "expr": "size(links.xxx) == 1" }
     */
    @PostMapping("/cel/validate")
    public ApiResponse<Map<String, Object>> validateCel(@RequestBody Map<String, Object> request) {
        String expr = request != null && request.get("expr") != null ? String.valueOf(request.get("expr")) : null;
        try {
            Map<String, Object> result = reasoningService.validateCel(expr);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "校验失败: " + e.getMessage());
        }
    }

    /**
     * CEL 表达式求值（用于脚本编辑、测试，传入样本上下文）
     * POST /api/v1/reasoning/cel/evaluate
     * Body: { "expr": "...", "properties": {}, "linked_data": {} }
     */
    @PostMapping("/cel/evaluate")
    @SuppressWarnings("unchecked")
    public ApiResponse<Object> evaluateCel(@RequestBody Map<String, Object> request) {
        if (request == null) return ApiResponse.error(400, "请求体不能为空");
        String expr = request.get("expr") != null ? String.valueOf(request.get("expr")) : null;
        Map<String, Object> properties = request.get("properties") instanceof Map
            ? (Map<String, Object>) request.get("properties") : Map.of();
        Map<String, List<Map<String, Object>>> linkedData = new java.util.LinkedHashMap<>();
        if (request.get("linked_data") instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) request.get("linked_data")).entrySet()) {
                if (e.getValue() instanceof List) {
                    List<?> list = (List<?>) e.getValue();
                    List<Map<String, Object>> maps = new java.util.ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map) maps.add((Map<String, Object>) item);
                    }
                    linkedData.put(e.getKey(), maps);
                }
            }
        }
        try {
            Object result = reasoningService.evaluateCel(expr, properties, linkedData);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "求值失败: " + e.getMessage());
        }
    }

    /**
     * 函数测试：使用给定参数调用已注册函数
     * POST /api/v1/reasoning/functions/test
     * Body: { "name": "is_single_province_etc", "args": [ {...}, [...] ] }
     */
    @PostMapping("/functions/test")
    public ApiResponse<Object> testFunction(@RequestBody Map<String, Object> request) {
        if (request == null) return ApiResponse.error(400, "请求体不能为空");
        String name = request.get("name") != null ? String.valueOf(request.get("name")) : null;
        List<Object> args = request.get("args") instanceof List ? (List<Object>) request.get("args") : List.of();
        try {
            Object result = reasoningService.testFunction(name, args);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(400, "调用失败: " + e.getMessage());
        }
    }
}
