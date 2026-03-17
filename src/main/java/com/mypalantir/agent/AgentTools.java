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
    private final ObjectMapper objectMapper;

    public AgentTools(ReasoningService reasoningService, QueryService queryService,
                      NaturalLanguageQueryService nlqService, ObjectMapper objectMapper) {
        this.reasoningService = reasoningService;
        this.queryService = queryService;
        this.nlqService = nlqService;
        this.objectMapper = objectMapper;
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
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Communications link failure") || msg.contains("Connection refused"))) {
                return "工具执行错误: 数据库连接失败，无法访问 MySQL。" +
                    " 请检查：1) MySQL 服务是否已启动 2) .env 或数据映射中的 host/port 是否正确 3) 网络/防火墙是否可达。" +
                    " 详见 docs/TROUBLESHOOTING_AGENT_QUERY_DATABASE.md 原始错误: " + msg;
            }
            return "工具执行错误: " + msg;
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
     *
     * 委托给 ReasoningService.testFunctionWithInstance，与推理引擎使用完全一致的调用链：
     *   buildInstanceContext（含 enrichGantryTransactionWithTollItems）
     *   → resolveFunctionArgsFromContext（按 schema 函数定义动态解析参数，兼容 JS 脚本函数）
     *   → functionRegistry.call
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

        try {
            Object result = reasoningService.testFunctionWithInstance(funcName, objectType, instanceId);
            return funcName + " → " + toReadableString(result);
        } catch (IllegalArgumentException e) {
            return objectType + " " + instanceId + " 不存在";
        }
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
     * 将函数返回值转为可读字符串，兼容 Nashorn ScriptObjectMirror（JS 数组/对象）。
     * ScriptObjectMirror 直接 toString() 输出 "[object Array]"，需要遍历转换。
     * 使用反射判断类型，避免对 nashorn-core 产生编译期强依赖。
     */
    private String toReadableString(Object result) {
        if (result == null) return "null";
        // 通过类名反射判断是否为 Nashorn ScriptObjectMirror，避免编译期强依赖
        String className = result.getClass().getName();
        if (className.contains("ScriptObjectMirror")) {
            try {
                // isArray()
                boolean isArray = (boolean) result.getClass().getMethod("isArray").invoke(result);
                if (isArray) {
                    int size = (int) result.getClass().getMethod("size").invoke(result);
                    List<Object> list = new ArrayList<>();
                    java.lang.reflect.Method getSlot = result.getClass().getMethod("getSlot", int.class);
                    for (int i = 0; i < size; i++) {
                        list.add(getSlot.invoke(result, i));
                    }
                    return list.toString();
                }
            } catch (Exception ignored) {
                // 反射失败时降级为 toString
            }
            return result.toString();
        }
        // 普通 Map/List 用 JSON 序列化
        if (result instanceof Map || result instanceof List) {
            try {
                return objectMapper.writeValueAsString(result);
            } catch (Exception e) {
                return result.toString();
            }
        }
        return result.toString();
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

        // 触发 schema 懒解析，确保脚本函数已注册到 functionRegistry
        reasoningService.getParsedRules();

        // 动态生成函数列表：优先从 schema 定义取描述，以 functionRegistry 中实际注册的函数为准
        StringBuilder funcList = new StringBuilder();
        Map<String, String> displayNames = reasoningService.getFunctionDisplayNames();
        List<FunctionDef> schemaDefs = loader.listFunctions();
        Map<String, FunctionDef> defMap = new LinkedHashMap<>();
        for (FunctionDef fd : schemaDefs) {
            defMap.put(fd.getName(), fd);
        }
        Set<String> registered = reasoningService.getRegisteredFunctions();
        if (!registered.isEmpty()) {
            for (String fn : registered) {
                funcList.append("           - ").append(fn);
                FunctionDef fd = defMap.get(fn);
                String desc = null;
                if (fd != null) {
                    desc = fd.getDescription() != null && !fd.getDescription().isBlank()
                            ? fd.getDescription()
                            : (fd.getDisplayName() != null && !fd.getDisplayName().isBlank() ? fd.getDisplayName() : null);
                }
                if (desc == null) desc = displayNames.get(fn);
                if (desc != null) funcList.append(": ").append(desc);
                funcList.append("\n");
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
