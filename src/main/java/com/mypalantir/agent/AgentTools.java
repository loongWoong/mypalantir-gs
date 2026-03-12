package com.mypalantir.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.meta.*;
import com.mypalantir.reasoning.ReasoningService;
import com.mypalantir.reasoning.engine.InferenceResult;
import com.mypalantir.reasoning.function.FunctionRegistry;
import com.mypalantir.query.OntologyQuery;
import com.mypalantir.service.NaturalLanguageQueryService;
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
    private final NaturalLanguageQueryService nlqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentTools(ReasoningService reasoningService, QueryService queryService,
                      NaturalLanguageQueryService nlqService) {
        this.reasoningService = reasoningService;
        this.queryService = queryService;
        this.nlqService = nlqService;
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
                case "query_data" -> queryData(args);
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
     *
     * 数据准备逻辑与 ReasoningService.inferInstance 完全一致：
     * 通过 buildInstanceContext 获取 instance、linkedData（出边+入边+别名+enriched）、derivedValues，
     * 确保函数调用与推理引擎使用相同的 schema 和数据。
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

        // 使用与推理引擎完全一致的数据准备逻辑（出边+入边+别名+enrich+衍生属性）
        ReasoningService.InstanceContext ctx;
        try {
            ctx = reasoningService.buildInstanceContext(objectType, instanceId);
        } catch (IllegalArgumentException e) {
            return objectType + " " + instanceId + " 不存在";
        }

        List<Object> funcArgs = buildFunctionArgsFromContext(funcName, ctx, objectType);
        Object result = registry.call(funcName, funcArgs);
        return funcName + " → " + result;
    }

    /**
     * 从 linkedData 中按目标类型后缀匹配关联数据。
     * 例如 targetSuffix="GantryTransaction" 会匹配 key 中包含该后缀的 link。
     */
    private List<Map<String, Object>> resolveLinkedByTargetSuffix(
            Map<String, List<Map<String, Object>>> linkedData,
            String objectType,
            String targetSuffix) {
        if (linkedData == null) return List.of();
        String suffixLower = targetSuffix.toLowerCase();
        for (Map.Entry<String, List<Map<String, Object>>> entry : linkedData.entrySet()) {
            String key = entry.getKey().replace("_", "").toLowerCase();
            if (key.contains(suffixLower.replace("_", "").toLowerCase())) {
                return entry.getValue() != null ? entry.getValue() : List.of();
            }
        }
        return List.of();
    }

    /**
     * 根据函数名和 InstanceContext 动态构建参数列表。
     * 通过 schema 的 LinkType 定义识别关联数据类型，避免硬编码目标类型名称字符串。
     */
    private List<Object> buildFunctionArgsFromContext(String funcName,
                                                       ReasoningService.InstanceContext ctx,
                                                       String objectType) {
        Map<String, Object> instance = ctx.instance;
        Map<String, List<Map<String, Object>>> linkedData = ctx.linkedData;

        // 从 schema 动态识别各类关联数据（按目标类型名后缀匹配，而非硬编码字符串）
        List<Map<String, Object>> gantryTxs = resolveLinkedByTargetSuffix(linkedData, objectType, "GantryTransaction");
        List<Map<String, Object>> splitDetails = resolveLinkedByTargetSuffix(linkedData, objectType, "SplitDetail");

        // 衍生属性中的 pro_split_time 优先从 derivedValues 取，其次从 splitDetails 取
        Object proSplitTime = ctx.derivedValues.get("pro_split_time");
        if (proSplitTime == null && !splitDetails.isEmpty()) {
            proSplitTime = splitDetails.get(0).get("pro_split_time");
        }

        // 按函数签名构建参数（函数签名由 schema 中 functions 定义，此处按已注册的内置函数约定）
        return switch (funcName) {
            case "is_single_province_etc", "is_obu_billing_mode1" ->
                List.of(instance);
            case "check_route_consistency", "check_fee_detail_consistency" ->
                List.of(splitDetails, gantryTxs);
            case "detect_duplicate_intervals", "check_gantry_hex_continuity",
                 "check_gantry_count_complete", "check_balance_continuity" ->
                List.of(gantryTxs);
            case "check_rounding_mismatch" ->
                List.of(gantryTxs, 0.95);
            case "detect_late_upload" ->
                List.of(gantryTxs, proSplitTime != null ? proSplitTime : "");
            default -> List.of(instance);
        };
    }

    /**
     * 自然语言数据查询
     * args: { "query": "查询所有拆分异常的Passage" }
     */
    private String queryData(Map<String, Object> args) throws Exception {
        String query = (String) args.get("query");
        if (query == null) return "缺少参数 query";

        try {
            OntologyQuery ontologyQuery = nlqService.convertToQuery(query);
            // 转换为 Map 执行
            Map<String, Object> queryMap = objectMapper.convertValue(ontologyQuery, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            // 移除 null 值
            queryMap.values().removeIf(Objects::isNull);
            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("columns", result.getColumns());
            resp.put("rowCount", result.getRowCount());
            resp.put("rows", result.getRows());
            return objectMapper.writeValueAsString(resp);
        } catch (NaturalLanguageQueryService.NaturalLanguageQueryException e) {
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 执行完整推理
     * args: { "passage_id": "PASS_LATE_001" }
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
               参数: {"passage_id": "通行路径ID", "function": "函数名"}
               可用函数:
               - is_single_province_etc: 判断是否单省ETC交易
               - is_obu_billing_mode1: 判断OBU计费方式
               - check_route_consistency: 检查拆分路径与门架路径一致性
               - detect_duplicate_intervals: 检测门架收费单元重复
               - check_gantry_hex_continuity: 检查门架HEX编码连续性
               - check_gantry_count_complete: 检查门架数量完整性
               - detect_late_upload: 检测门架延迟上传
               - check_fee_detail_consistency: 检查费用明细一致性
               - check_rounding_mismatch: 检查四舍五入差异
               - check_balance_continuity: 检查卡内余额连续性

            5. run_inference - 执行完整规则引擎推理（一次性返回所有规则结果）
               参数: {"passage_id": "通行路径ID"}

            6. query_data - 用自然语言查询本体数据（支持过滤、聚合、排序等）
               参数: {"query": "自然语言查询，如：查询所有拆分异常的Passage"}
               示例: {"query": "显示入口站为S0085的所有Passage"}
               适用场景: 需要按条件批量查询、统计、筛选数据时使用
            """;
    }
}
