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
     * 查询实例数据（type 为当前本体的对象类型）
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
     * 调用诊断函数（object_type、instance_id 为当前本体的类型与实例）
     * args: { "object_type": "...", "instance_id": "...", "function": "..." }
     * 兼容旧字段: passage_id → instance_id，object_type 默认取当前本体推理根类型
     */
    private String callFunction(Map<String, Object> args) throws Exception {
        String objectType = (String) args.get("object_type");
        String instanceId = (String) args.get("instance_id");
        String passageId = (String) args.get("passage_id");
        String funcName = (String) args.get("function");
        if (funcName == null) return "缺少参数 function";
        if (instanceId == null && passageId != null) {
            instanceId = passageId;
            if (objectType == null) objectType = guessRootType();
        }
        if (objectType == null || instanceId == null) return "缺少参数 object_type 与 instance_id";

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
     * args: { "query": "自然语言查询" }
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
     * args: { "object_type": "Path", "instance_id": "xxx" }
     */
    private String runInference(Map<String, Object> args) throws Exception {
        String objectType = (String) args.get("object_type");
        String instanceId = (String) args.get("instance_id");
        // 兼容旧字段名 passage_id
        if (instanceId == null) instanceId = (String) args.get("passage_id");
        if (objectType == null) objectType = guessRootType();
        if (objectType == null || instanceId == null)
            return "缺少参数 object_type 与 instance_id";

        InferenceResult result = reasoningService.inferInstance(objectType, instanceId);
        return objectMapper.writeValueAsString(result.toMap());
    }

    /**
     * 获取当前本体中有推理规则的对象类型列表。
     */
    public List<String> listRootTypes() {
        return reasoningService.listInferenceRootTypes();
    }

    /**
     * 生成当前本体的数据模型描述（用于 system prompt），包含推理根类型名称及其关联。
     */
    public String getDataModelDescription() {
        Loader loader = reasoningService.getLoader();
        List<String> rootTypes = reasoningService.listInferenceRootTypes();
        if (rootTypes.isEmpty()) {
            return "- 数据模型以当前加载的本体为准";
        }
        StringBuilder sb = new StringBuilder();
        for (String typeName : rootTypes) {
            List<LinkType> outLinks = loader.getOutgoingLinks(typeName);
            if (!outLinks.isEmpty()) {
                List<String> linkNames = outLinks.stream().map(LinkType::getName).toList();
                sb.append("- ").append(typeName).append("（推理对象）关联：")
                  .append(String.join(", ", linkNames)).append("\n");
            } else {
                sb.append("- ").append(typeName).append("（推理对象）\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    /**
     * 从当前本体推断推理根对象类型（第一个有推理规则的类型），不再硬编码 Passage。
     */
    private String guessRootType() {
        List<String> rootTypes = reasoningService.listInferenceRootTypes();
        return rootTypes.isEmpty() ? null : rootTypes.get(0);
    }

    /**
     * 获取工具描述（用于 system prompt）；类型、关联、函数均从当前加载的本体动态生成。
     */
    public String getToolDescriptions() {
        Loader loader = reasoningService.getLoader();
        List<String> rootTypes = reasoningService.listInferenceRootTypes();
        String typeHint = rootTypes.isEmpty()
                ? "类型名、关联名以当前本体为准"
                : "当前本体推理对象类型: " + String.join(", ", rootTypes);

        String primaryType = rootTypes.isEmpty() ? "对象" : rootTypes.get(0);

        // 动态生成关联名列表
        String linkDesc = "关联名由当前本体定义";
        if (!rootTypes.isEmpty()) {
            List<LinkType> outLinks = loader.getOutgoingLinks(primaryType);
            if (!outLinks.isEmpty()) {
                List<String> linkNames = outLinks.stream().map(LinkType::getName).toList();
                linkDesc = primaryType + " 的关联: " + String.join(", ", linkNames);
            }
        }

        // 动态生成函数列表
        StringBuilder funcList = new StringBuilder();
        List<FunctionDef> functions = loader.listFunctions();
        if (!functions.isEmpty()) {
            for (FunctionDef fd : functions) {
                funcList.append("           - ").append(fd.getName());
                if (fd.getDescription() != null && !fd.getDescription().isBlank()) {
                    funcList.append(": ").append(fd.getDescription());
                } else if (fd.getDisplayName() != null && !fd.getDisplayName().isBlank()) {
                    funcList.append(": ").append(fd.getDisplayName());
                }
                funcList.append("\n");
            }
        } else {
            Set<String> registered = reasoningService.getRegisteredFunctions();
            for (String fn : registered) {
                funcList.append("           - ").append(fn).append("\n");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("1. query_instance - 查询实例基本数据\n");
        sb.append("   参数: {\"type\": \"类型名\", \"id\": \"实例ID\"}\n");
        sb.append("   ").append(typeHint).append("\n\n");

        sb.append("2. query_links - 查询关联数据\n");
        sb.append("   参数: {\"type\": \"类型\", \"id\": \"ID\", \"link\": \"关联名\"}\n");
        sb.append("   ").append(linkDesc).append("\n\n");

        sb.append("3. search_rules - 搜索当前本体的SWRL规则\n");
        sb.append("   参数: {\"keyword\": \"关键词\"}\n\n");

        sb.append("4. call_function - 调用诊断函数\n");
        sb.append("   参数: {\"object_type\": \"").append(primaryType)
          .append("\", \"instance_id\": \"实例ID\", \"function\": \"函数名\"}\n");
        sb.append("   可用函数:\n");
        sb.append(funcList);
        sb.append("\n");

        sb.append("5. run_inference - 执行完整规则引擎推理（一次性返回所有规则结果）\n");
        sb.append("   参数: {\"object_type\": \"").append(primaryType)
          .append("\", \"instance_id\": \"实例ID\"}\n\n");

        sb.append("6. query_data - 用自然语言查询本体数据（支持过滤、聚合、排序等）\n");
        sb.append("   参数: {\"query\": \"自然语言查询\"}\n");
        sb.append("   适用场景: 需要按条件批量查询、统计、筛选数据时使用\n");

        return sb.toString();
    }
}
