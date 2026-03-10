package com.mypalantir.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.meta.*;
import com.mypalantir.reasoning.ReasoningService;
import com.mypalantir.reasoning.engine.InferenceResult;
import com.mypalantir.reasoning.function.FunctionRegistry;
import com.mypalantir.service.QueryService;
import com.mypalantir.query.QueryExecutor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent 工具集：提供 ReAct agent 可调用的工具。
 * 类型名、关联名、规则均随当前加载的本体（右上角选择的 schema.yaml / toll.yaml 等）变化。
 */
@Component
public class AgentTools {

    private final ReasoningService reasoningService;
    private final QueryService queryService;
    private final Loader loader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentTools(ReasoningService reasoningService, QueryService queryService, Loader loader) {
        this.reasoningService = reasoningService;
        this.queryService = queryService;
        this.loader = loader;
    }

    /**
     * 执行工具调用
     */
    public String executeTool(String toolName, Map<String, Object> args) {
        try {
            return switch (toolName) {
                case "query_instance" -> queryInstance(args);
                case "query_links" -> queryLinks(args);
                case "search_rules" -> searchRules(args);
                case "call_function" -> callFunction(args);
                case "run_inference" -> runInference(args);
                default -> "未知工具: " + toolName;
            };
        } catch (Exception e) {
            return "工具执行错误: " + e.getMessage();
        }
    }

    /**
     * 查询实例数据（type 为当前本体的对象类型，如 Path、Passage）
     * args: { "type": "类型名", "id": "实例ID" }
     */
    private String queryInstance(Map<String, Object> args) throws Exception {
        String type = (String) args.get("type");
        String id = (String) args.get("id");
        if (type == null || id == null) return "缺少参数 type 或 id";

        Map<String, Object> instance = reasoningService.queryInstance(type, id);
        if (instance == null) return type + " " + id + " 不存在";
        return objectMapper.writeValueAsString(instance);
    }

    /**
     * 查询关联数据（type、link 为当前本体的类型名与关联名）
     * args: { "type": "类型名", "id": "实例ID", "link": "关联名" }
     */
    private String queryLinks(Map<String, Object> args) throws Exception {
        String type = (String) args.get("type");
        String id = (String) args.get("id");
        String link = (String) args.get("link");
        if (type == null || id == null || link == null) return "缺少参数 type, id 或 link";

        List<Map<String, Object>> data = reasoningService.queryLinkedInstances(type, id, link);
        return objectMapper.writeValueAsString(data);
    }

    /**
     * 搜索规则
     * args: { "keyword": "obu_split" }
     */
    private String searchRules(Map<String, Object> args) throws Exception {
        String keyword = (String) args.get("keyword");
        if (keyword == null) return "缺少参数 keyword";

        List<Map<String, Object>> rules = reasoningService.searchRules(keyword);
        if (rules.isEmpty()) return "未找到匹配的规则";
        return objectMapper.writeValueAsString(rules);
    }

    /**
     * 调用诊断函数（object_type、instance_id 为当前本体的类型与实例，与 query_instance 一致）
     * args: { "object_type": "Path", "instance_id": "PASS_LATE_001", "function": "check_route_consistency" }
     * 兼容: { "passage_id": "PASS_LATE_001", "function": "..." } 视为 Passage + instance_id
     */
    private String callFunction(Map<String, Object> args) throws Exception {
        String objectType = (String) args.get("object_type");
        String instanceId = (String) args.get("instance_id");
        String passageId = (String) args.get("passage_id");
        String funcName = (String) args.get("function");
        if (funcName == null) return "缺少参数 function";
        if (instanceId == null && passageId != null) {
            instanceId = passageId;
            if (objectType == null) objectType = "Passage";
        }
        if (objectType == null || instanceId == null) return "缺少参数 object_type 与 instance_id（或 passage_id）";

        FunctionRegistry registry = reasoningService.getFunctionRegistry();
        if (!registry.hasFunction(funcName)) return "函数 " + funcName + " 未注册";

        Map<String, Object> instance = reasoningService.queryInstance(objectType, instanceId);
        if (instance == null) return objectType + " " + instanceId + " 不存在";

        // 按当前本体的出边查询关联数据，用于函数参数
        List<Map<String, Object>> gantryTxs = new ArrayList<>();
        List<Map<String, Object>> splitDetails = new ArrayList<>();
        List<Map<String, Object>> exitTxList = new ArrayList<>();
        List<Map<String, Object>> entryTxList = new ArrayList<>();
        if (loader.getSchema() != null && loader.getSchema().getLinkTypes() != null) {
            for (LinkType lt : loader.getSchema().getLinkTypes()) {
                if (!objectType.equals(lt.getSourceType())) continue;
                List<Map<String, Object>> list = reasoningService.queryLinkedInstances(objectType, instanceId, lt.getName());
                String target = lt.getTargetType() != null ? lt.getTargetType() : "";
                if (target.contains("GantryTransaction")) gantryTxs = list;
                else if (target.contains("SplitDetail")) splitDetails = list;
                else if (target.contains("ExitTransaction")) exitTxList = list;
                else if (target.contains("EntryTransaction")) entryTxList = list;
            }
        }
        if (!exitTxList.isEmpty()) instance.put("_exit_transaction", exitTxList.get(0));
        if (!entryTxList.isEmpty()) {
            Map<String, Object> entryTx = entryTxList.get(0);
            Map<String, Object> mediaPseudo = new LinkedHashMap<>();
            mediaPseudo.put("media_type", entryTx.get("media_type"));
            mediaPseudo.put("card_net", entryTx.get("card_net"));
            instance.put("_media", mediaPseudo);
        }

        List<Object> funcArgs = buildFunctionArgs(funcName, instance, gantryTxs, splitDetails);
        Object result = registry.call(funcName, funcArgs);
        return funcName + " → " + result;
    }

    /**
     * 根据函数名构建参数列表
     */
    private List<Object> buildFunctionArgs(String funcName, Map<String, Object> passage,
                                            List<Map<String, Object>> gantryTxs,
                                            List<Map<String, Object>> splitDetails) {
        return switch (funcName) {
            case "is_single_province_etc", "is_obu_billing_mode1" ->
                List.of(passage);
            case "check_route_consistency", "check_fee_detail_consistency" ->
                List.of(splitDetails, gantryTxs);
            case "detect_duplicate_intervals", "check_gantry_hex_continuity",
                 "check_gantry_count_complete", "check_balance_continuity" ->
                List.of(gantryTxs);
            case "check_rounding_mismatch" ->
                List.of(gantryTxs, 0.95);
            case "detect_late_upload" -> {
                // 需要 pro_split_time 参数
                Object proSplitTime = null;
                if (!splitDetails.isEmpty()) {
                    proSplitTime = splitDetails.get(0).get("pro_split_time");
                }
                yield List.of(gantryTxs, proSplitTime != null ? proSplitTime : "");
            }
            default -> List.of(passage);
        };
    }

    /**
     * 执行完整推理（使用当前本体的对象类型与实例 ID；规则来自当前加载的 schema）
     * args: { "object_type": "Path", "instance_id": "PASS_LATE_001" }
     * 兼容: { "passage_id": "PASS_LATE_001" } 视为 Passage
     */
    private String runInference(Map<String, Object> args) throws Exception {
        String objectType = (String) args.get("object_type");
        String instanceId = (String) args.get("instance_id");
        String passageId = (String) args.get("passage_id");
        if (instanceId == null && passageId != null) {
            instanceId = passageId;
            if (objectType == null) objectType = "Passage";
        }
        if (objectType == null || instanceId == null)
            return "缺少参数 object_type 与 instance_id（或 passage_id）";

        InferenceResult result = reasoningService.inferInstance(objectType, instanceId);
        return objectMapper.writeValueAsString(result.toMap());
    }

    /**
     * 获取工具描述（用于 system prompt）；类型与关联随当前加载的本体变化。
     */
    public String getToolDescriptions() {
        List<String> rootTypes = reasoningService.listInferenceRootTypes();
        String typeHint = rootTypes.isEmpty() ? "类型名、关联名以当前本体为准" : "推理对象类型示例: " + String.join(", ", rootTypes);
        return """
            1. query_instance - 查询实例基本数据
               参数: {"type": "类型名", "id": "实例ID"}
               """ + typeHint + """

            2. query_links - 查询关联数据
               参数: {"type": "类型", "id": "ID", "link": "关联名"}
               关联名由当前本体定义，可用 search_rules 查看规则中出现的 link 名

            3. search_rules - 搜索当前本体的SWRL规则
               参数: {"keyword": "关键词"}

            4. call_function - 调用诊断函数
               参数: {"object_type": "类型名", "instance_id": "实例ID", "function": "函数名"}
               或: {"passage_id": "实例ID", "function": "函数名"}（等价 object_type=Passage）
               可用函数以当前本体注册为准（如 check_route_consistency, detect_late_upload 等）

            5. run_inference - 执行完整规则引擎推理（使用当前本体的规则）
               参数: {"object_type": "类型名", "instance_id": "实例ID"}
               或: {"passage_id": "实例ID"}（等价 object_type=Passage）
            """;
    }
}
