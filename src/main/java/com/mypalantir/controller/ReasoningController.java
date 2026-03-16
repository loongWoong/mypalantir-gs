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
            var withContext = reasoningService.inferInstanceWithLinkedData(objectType, instanceId);
            Map<String, Object> response = new LinkedHashMap<>(withContext.result().toMap());
            response.put("linkedDataSummary", withContext.linkedDataSummary());
            return ApiResponse.success(response);
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
     * 批量推理（全量同步）：遍历所有实例，同步返回每条实例的规则摘要，供前端分组展示。
     * POST /api/reasoning/batch-all-sync
     * Body: { "object_type": "Passage" }
     */
    @PostMapping("/batch-all-sync")
    public ApiResponse<List<Map<String, Object>>> inferBatchAllSync(@RequestBody Map<String, Object> request) {
        String objectType = request.containsKey("object_type") ? String.valueOf(request.get("object_type")) : "Passage";
        try {
            List<Map<String, Object>> results = reasoningService.inferBatchAllSync(objectType);
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.error(500, "Batch-all-sync inference failed: " + e.getMessage());
        }
    }

    /**
     * 批量推理（全量）：异步遍历指定类型的所有实例，结果写入 logs/Reasoning.log
     * POST /api/reasoning/batch-all
     * Body: { "object_type": "Passage" }
     */
    @PostMapping("/batch-all")
    public ApiResponse<Map<String, Object>> inferBatchAll(@RequestBody Map<String, Object> request) {
        String objectType = request.containsKey("object_type") ? String.valueOf(request.get("object_type")) : "Passage";
        try {
            reasoningService.inferBatchAllAsync(objectType);
            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("status", "started");
            resp.put("object_type", objectType);
            resp.put("log", "logs/Reasoning.log");
            resp.put("message", "批量推理已在后台启动，结果将写入 logs/Reasoning.log");
            return ApiResponse.success(resp);
        } catch (Exception e) {
            return ApiResponse.error(500, "Batch-all inference failed: " + e.getMessage());
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
     * CEL 表达式按实例求值：使用当前本体模型下指定根对象类型与实例 ID 构建上下文（实例+关联数据+衍生属性），并求值。
     * 调用前需已切换到目标本体模型；数据来自该模型绑定的数据存储。
     * POST /api/v1/reasoning/cel/evaluate-with-instance
     * Body: { "expr": "...", "object_type": "Path", "instance_id": "xxx" }
     */
    @PostMapping("/cel/evaluate-with-instance")
    public ApiResponse<Object> evaluateCelWithInstance(@RequestBody Map<String, Object> request) {
        if (request == null) return ApiResponse.error(400, "请求体不能为空");
        String expr = request.get("expr") != null ? String.valueOf(request.get("expr")) : null;
        String objectType = request.get("object_type") != null ? String.valueOf(request.get("object_type")) : null;
        String instanceId = request.get("instance_id") != null ? String.valueOf(request.get("instance_id")) : null;
        if (expr == null || expr.isBlank() || objectType == null || objectType.isBlank() || instanceId == null || instanceId.isBlank()) {
            return ApiResponse.error(400, "expr、object_type、instance_id 不能为空");
        }
        try {
            Object result = reasoningService.evaluateCelWithInstance(expr, objectType, instanceId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(400, "按实例求值失败: " + e.getMessage());
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

    /**
     * 按实例测试函数：使用当前本体模型下指定根对象类型与实例 ID 构建上下文（实例+关联数据），按函数入参解析后调用。
     * 调用前需已切换到目标本体模型；数据来自该模型绑定的数据存储。
     * POST /api/v1/reasoning/functions/test-with-instance
     * Body: { "name": "is_obu_billing_mode1", "object_type": "Path", "instance_id": "xxx" }
     */
    @PostMapping("/functions/test-with-instance")
    public ApiResponse<Object> testFunctionWithInstance(@RequestBody Map<String, Object> request) {
        if (request == null) return ApiResponse.error(400, "请求体不能为空");
        String name = request.get("name") != null ? String.valueOf(request.get("name")) : null;
        String objectType = request.get("object_type") != null ? String.valueOf(request.get("object_type")) : null;
        String instanceId = request.get("instance_id") != null ? String.valueOf(request.get("instance_id")) : null;
        if (name == null || name.isBlank() || objectType == null || objectType.isBlank() || instanceId == null || instanceId.isBlank()) {
            return ApiResponse.error(400, "name、object_type、instance_id 不能为空");
        }
        try {
            Object result = reasoningService.testFunctionWithInstance(name, objectType, instanceId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(400, "按实例测试失败: " + e.getMessage());
        }
    }
}
