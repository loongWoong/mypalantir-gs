package com.mypalantir.reasoning;

import com.mypalantir.meta.*;
import com.mypalantir.reasoning.cel.engine.CelEvalContext;
import com.mypalantir.reasoning.cel.engine.CelEvaluationService;
import com.mypalantir.reasoning.cel.engine.CelFunctionRegistry;
import com.mypalantir.reasoning.cel.engine.ConfigurableCelFunctionRegistry;
import com.mypalantir.reasoning.cel.engine.RegistryBackedCelFunction;
import com.mypalantir.reasoning.engine.Fact;
import com.mypalantir.reasoning.engine.ForwardChainEngine;
import com.mypalantir.reasoning.engine.InferenceResult;
import com.mypalantir.reasoning.function.FunctionRegistry;
import com.mypalantir.reasoning.function.builtin.*;
import com.mypalantir.reasoning.function.script.ScriptFunctionRunner;
import com.mypalantir.reasoning.function.script.ScriptOntologyFunction;

import java.nio.file.Paths;
import com.mypalantir.reasoning.swrl.SWRLParser;
import com.mypalantir.reasoning.swrl.SWRLRule;
import com.mypalantir.service.QueryService;
import com.mypalantir.query.QueryExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 推理服务：整合 CEL 求值器、函数框架、SWRL 解析器和前向链引擎。
 * 按当前加载的本体模型（Loader）动态解析规则与关联，支持 schema.yaml / toll.yaml 等任意模型。
 * 规则在首次推理或获取规则时按当前 schema 懒解析，切换本体后自动使用新 schema 的规则。
 */
@Service
public class ReasoningService {

    private final Loader loader;
    private final QueryService queryService;
    private final FunctionRegistry functionRegistry;
    private final CelEvaluationService celEvaluationService;
    private final ConfigurableCelFunctionRegistry celFunctionRegistry;
    private final ScriptFunctionRunner scriptFunctionRunner;

    /** 按当前本体文件路径缓存：切换 schema 后重新解析 */
    private volatile String lastParsedSchemaPath;
    private volatile List<SWRLRule> parsedRules = List.of();
    private volatile Map<String, String> functionDisplayNames = Map.of();

    public ReasoningService(Loader loader, QueryService queryService, FunctionRegistry functionRegistry,
                            CelEvaluationService celEvaluationService,
                            ConfigurableCelFunctionRegistry celFunctionRegistry,
                            ScriptFunctionRunner scriptFunctionRunner) {
        this.loader = loader;
        this.queryService = queryService;
        this.functionRegistry = functionRegistry;
        this.celEvaluationService = celEvaluationService;
        this.celFunctionRegistry = celFunctionRegistry;
        this.scriptFunctionRunner = scriptFunctionRunner != null ? scriptFunctionRunner : new ScriptFunctionRunner();
    }

    @PostConstruct
    public void initialize() {
        registerBuiltinFunctions();
        registerCelBridgeFunctions();
    }

    /** 将 FunctionRegistry 中已注册的本体函数暴露为 CEL 可调用的扩展（多参数重载） */
    private void registerCelBridgeFunctions() {
        celFunctionRegistry.clear();
        for (String name : functionRegistry.getFunctionNames()) {
            for (int arity = 1; arity <= RegistryBackedCelFunction.MAX_ARITY; arity++) {
                celFunctionRegistry.registerOverload(name, new RegistryBackedCelFunction(name, functionRegistry, arity));
            }
        }
        System.out.println("[ReasoningService] Registered " + functionRegistry.getFunctionNames().size() + " functions to CEL: " + functionRegistry.getFunctionNames());
    }

    /** 构建 CEL 求值环境：instance 属性 + links + 已计算的衍生属性 + this */
    private static Map<String, Object> buildCelEnv(Map<String, Object> instance,
                                                    Map<String, List<Map<String, Object>>> linkedData,
                                                    Map<String, Object> derivedValues) {
        Map<String, Object> env = new HashMap<>(instance != null ? instance : Map.of());
        if (linkedData != null) env.put("links", linkedData);
        if (derivedValues != null) env.putAll(derivedValues);
        Map<String, Object> thisObj = new HashMap<>(instance != null ? instance : Map.of());
        if (linkedData != null) thisObj.put("links", linkedData);
        if (derivedValues != null) thisObj.putAll(derivedValues);
        env.put("this", thisObj);
        return env;
    }

    /**
     * 按当前加载的 schema 懒解析规则与函数显示名；切换本体（右上角切换）后会自动用新 schema 重新解析。
     */
    private synchronized void ensureRulesParsedForCurrentSchema() {
        OntologySchema schema = loader.getSchema();
        String path = loader.getFilePath() != null ? loader.getFilePath() : "";
        if (path.equals(lastParsedSchemaPath)) {
            boolean allFunctionsRegistered = true;
            if (schema != null && schema.getFunctions() != null) {
                for (FunctionDef fd : schema.getFunctions()) {
                    if (!functionRegistry.hasFunction(fd.getName())) {
                        allFunctionsRegistered = false;
                        System.out.println("[ReasoningService] Function not registered: " + fd.getName() + ", forcing re-parse");
                        break;
                    }
                }
            }
            if (allFunctionsRegistered) return;
            lastParsedSchemaPath = null;
        }
        System.out.println("[ReasoningService] Parsing schema for: " + path);
        lastParsedSchemaPath = path;
        functionRegistry.clearScriptFunctions();
        if (schema == null) {
            parsedRules = List.of();
            functionDisplayNames = Map.of();
            return;
        }
        try {
            java.nio.file.Path ontologyBaseDir = (path != null && !path.isBlank()) ? Paths.get(path).getParent() : null;
            String ontologyFileName = (path != null && !path.isBlank()) ? Paths.get(path).getFileName().toString().replace(".yaml", "").replace(".yml", "") : null;
            if (schema.getFunctions() != null) {
                for (FunctionDef fd : schema.getFunctions()) {
                    String scriptPath = fd.getScriptPath();
                    boolean isScript = "script".equalsIgnoreCase(fd.getImplementation());
                    if (isScript && scriptPath != null && !scriptPath.isBlank()) {
                        // 显式 script 声明：强制注册脚本版本，覆盖同名 builtin
                        try {
                            functionRegistry.registerScript(new ScriptOntologyFunction(fd.getName(), scriptPath, ontologyBaseDir, scriptFunctionRunner));
                            System.out.println("[ReasoningService] Registered script function: " + fd.getName() + " -> " + scriptPath);
                        } catch (Exception e) {
                            System.err.println("WARNING: Failed to register script function " + fd.getName() + " (" + scriptPath + "): " + e.getMessage());
                        }
                    } else if (!functionRegistry.hasFunction(fd.getName()) && ontologyBaseDir != null && ontologyFileName != null) {
                        // 自动注册：仅当函数尚未注册时，按 ontologyFileName/funcName.js 查找
                        String autoScriptPath = ontologyFileName + "/" + fd.getName() + ".js";
                        java.nio.file.Path fullPath = ontologyBaseDir.resolve("functions/script").resolve(autoScriptPath);
                        if (java.nio.file.Files.isRegularFile(fullPath)) {
                            try {
                                functionRegistry.registerScript(new ScriptOntologyFunction(fd.getName(), autoScriptPath, ontologyBaseDir, scriptFunctionRunner));
                                System.out.println("[ReasoningService] Auto-registered script function: " + fd.getName() + " -> " + autoScriptPath);
                            } catch (Exception e) {
                                System.err.println("WARNING: Failed to auto-register script function " + fd.getName() + " (" + autoScriptPath + "): " + e.getMessage());
                            }
                        } else {
                            System.out.println("[ReasoningService] No script found for function: " + fd.getName() + " (tried " + fullPath + ")");
                        }
                    }
                }
            }
            registerCelBridgeFunctions();
            SWRLParser parser = new SWRLParser(schema, functionRegistry);
            parsedRules = parser.parseAll(schema);
            Map<String, String> names = new HashMap<>();
            if (schema.getFunctions() != null) {
                for (FunctionDef fd : schema.getFunctions()) {
                    if (fd.getDisplayName() != null)
                        names.put(fd.getName(), fd.getDisplayName());
                }
            }
            functionDisplayNames = names;
            List<String> missing = functionRegistry.getMissingBuiltins(schema);
            if (!missing.isEmpty())
                System.err.println("WARNING: Missing builtin functions for current schema: " + missing);
        } catch (Exception e) {
            System.err.println("Failed to parse rules for current schema: " + e.getMessage());
            parsedRules = List.of();
            functionDisplayNames = Map.of();
        }
    }

    private void registerBuiltinFunctions() {
        functionRegistry.register(new IsSingleProvinceEtc());
        functionRegistry.register(new IsObuBillingMode1());
        functionRegistry.register(new CheckRouteConsistency());
        functionRegistry.register(new DetectDuplicateIntervals());
        functionRegistry.register(new CheckGantryHexContinuity());
        functionRegistry.register(new CheckGantryCountComplete());
        functionRegistry.register(new DetectLateUpload());
        functionRegistry.register(new CheckFeeDetailConsistency());
        functionRegistry.register(new CheckRoundingMismatch());
        functionRegistry.register(new CheckBalanceContinuity());
    }

    /** 推理结果与关联数据摘要，供 API 返回前端展示。linkedDataSummary 每项为 { count, displayName } */
    public record InferResultWithContext(InferenceResult result, Map<String, Map<String, Object>> linkedDataSummary) {}

    /**
     * 对当前本体模型下指定对象类型与实例执行推理（按右上角所选本体模型）。
     * 关联数据从当前 schema 动态获取，支持 Passage(toll) / Path(schema) 等任意类型。
     */
    public InferenceResult inferInstance(String objectType, String instanceId) throws Exception {
        try (ReasoningLogger log = ReasoningLogger.open()) {
            return inferInstanceWithLogger(objectType, instanceId, log).result();
        }
    }

    /**
     * 执行推理并返回完整上下文（含 linkedData 摘要），供 /reasoning/infer API 使用。
     */
    public InferResultWithContext inferInstanceWithLinkedData(String objectType, String instanceId) throws Exception {
        try (ReasoningLogger log = ReasoningLogger.open()) {
            return inferInstanceWithLogger(objectType, instanceId, log);
        }
    }

    /**
     * 带外部 logger 的推理实现，供批量推理复用同一文件句柄，避免重复打开日志文件。
     */
    private InferResultWithContext inferInstanceWithLogger(String objectType, String instanceId,
                                                     ReasoningLogger log) throws Exception {
        log.separator();
        log.line("[Reasoning] ========== 开始推理 ==========");
        log.line("[Reasoning] 对象类型: " + objectType + ", 实例ID: " + instanceId);
        log.line("[Reasoning] 当前本体: " + (loader.getSchema() != null ? loader.getSchema().getNamespace() : "null"));
        log.flush();

        log.line("[Reasoning] --- 步骤1: 查询主实例 ---");
        Map<String, Object> instance = queryInstance(objectType, instanceId);
        if (instance == null) {
            log.line("[Reasoning] ✗ 实例未找到! objectType=" + objectType + ", id=" + instanceId);
            log.flush();
            throw new IllegalArgumentException("Instance not found: " + objectType + " id=" + instanceId);
        }
        log.line("[Reasoning] 实例查询成功, 属性数量: " + instance.size());
        for (Map.Entry<String, Object> ep : instance.entrySet()) {
            log.line("[Reasoning]   " + ep.getKey() + " = " + ep.getValue());
        }

        log.line("[Reasoning] --- 步骤2: 加载关联数据 (linkedData) ---");
        Map<String, List<Map<String, Object>>> linkedData = new LinkedHashMap<>();
        Map<String, String> keyToDisplayName = new LinkedHashMap<>();
        OntologySchema schema = loader.getSchema();
        if (schema != null && schema.getLinkTypes() != null) {
            for (LinkType lt : schema.getLinkTypes()) {
                if (!objectType.equals(lt.getSourceType())) continue;
                List<Map<String, Object>> list = queryLinkedInstances(objectType, instanceId, lt.getName());
                linkedData.put(lt.getName(), list);
                keyToDisplayName.put(lt.getName(), lt.getDisplayName() != null && !lt.getDisplayName().isBlank() ? lt.getDisplayName() : lt.getName());
                log.line("[Reasoning]   outgoing " + lt.getName() + " (" + lt.getSourceType() + " -> " + lt.getTargetType() + "): " + list.size() + " records");
            }
            // incoming links (入边)
            for (LinkType lt : schema.getLinkTypes()) {
                if (!objectType.equals(lt.getTargetType())) continue;
                List<Map<String, Object>> incoming = queryIncomingLinkInstances(lt, instanceId);
                if (incoming.isEmpty()) {
                    log.line("[Reasoning]   incoming " + lt.getName() + " (" + lt.getSourceType() + " -> " + lt.getTargetType() + "): 0 (skip)");
                    continue;
                }
                String canonicalKey = inferIncomingLinkKey(objectType, lt);
                linkedData.put(canonicalKey, incoming);
                keyToDisplayName.put(canonicalKey, lt.getDisplayName() != null && !lt.getDisplayName().isBlank() ? lt.getDisplayName() : canonicalKey);
                log.line("[Reasoning]   incoming " + lt.getName() + " -> key=" + canonicalKey + ": " + incoming.size() + " records");
            }
            addLinkedDataAliases(linkedData, objectType);
            addLinkedDataDisplayNameAliases(keyToDisplayName);
        } else {
            log.line("[Reasoning]   !! schema or linkTypes is null");
        }

        log.line("[Reasoning]   linkedData summary (" + linkedData.size() + " keys):");
        for (Map.Entry<String, List<Map<String, Object>>> el : linkedData.entrySet()) {
            log.line("[Reasoning]     " + el.getKey() + " -> " + el.getValue().size() + " records");
        }

        enrichEntryExitAndMedia(objectType, instanceId, instance, linkedData);
        // Path：同样需要出口/入口和 _media，否则 is_single_province_etc / is_obu_billing_mode1 恒为 false，OBU 规则不会触发
        enrichPathEntryExitAndMedia(objectType, instanceId, instance, linkedData);
        // OBU：为每个 GantryTransaction 加载 has_gantry_toll_item，供 get_actual_count / get_actual_sequence 使用
        enrichGantryTransactionWithTollItems(objectType, linkedData);

        logLinkedDataDetails(log, linkedData, "has_split_item", "has_gantry_transaction");

        log.line("[Reasoning] --- Step 3: Compute derived properties ---");
        Map<String, Object> derivedValues = computeDerivedProperties(objectType, instance, linkedData, log);
        log.line("[Reasoning]   Derived properties (" + derivedValues.size() + "):");
        for (Map.Entry<String, Object> ed : derivedValues.entrySet()) {
            log.line("[Reasoning]     " + ed.getKey() + " = " + ed.getValue()
                + " (" + (ed.getValue() != null ? ed.getValue().getClass().getSimpleName() : "null") + ")");
        }

        log.line("[Reasoning] --- Step 4: Forward chaining ---");
        List<SWRLRule> rulesForInfer = getParsedRules();
        log.line("[Reasoning]   Parsed rules: " + rulesForInfer.size());
        for (SWRLRule r : rulesForInfer) {
            log.line("[Reasoning]     " + r.getName() + " | antecedents: " + r.getAntecedents().size()
                + " | " + (r.getDisplayName() != null ? r.getDisplayName() : ""));
        }
        log.flush();

        ForwardChainEngine engine = new ForwardChainEngine(functionRegistry, rulesForInfer, getFunctionDisplayNames());
        InferenceResult result = engine.infer(instance, linkedData, derivedValues);

        Map<String, Map<String, Object>> linkedDataSummary = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> el : linkedData.entrySet()) {
            int count = el.getValue() != null ? el.getValue().size() : 0;
            String displayName = keyToDisplayName != null ? keyToDisplayName.getOrDefault(el.getKey(), el.getKey()) : el.getKey();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("count", count);
            entry.put("displayName", displayName);
            linkedDataSummary.put(el.getKey(), entry);
        }

        log.line("[Reasoning] --- Result summary ---");
        log.line("[Reasoning]   Cycles: " + result.getCycleCount());
        log.line("[Reasoning]   Trace entries: " + (result.getTrace() != null ? result.getTrace().size() : 0));
        for (InferenceResult.TraceEntry entry : result.getTrace()) {
            log.line("[Reasoning]   [FIRED] " + entry.ruleName() + " -> " + entry.fact());
        }
        log.line("[Reasoning] ========== Reasoning complete ==========");
        log.separator();
        log.flush();
        return new InferResultWithContext(result, linkedDataSummary);
    }

    /**
     * 对指定通行路径（Passage）执行推理，供 Agent 工具 run_inference 调用。
     */
    public InferenceResult inferPassage(String passageId) throws Exception {
        return inferInstance("Passage", passageId);
    }

    /**
     * 获取推理所需的完整实例上下文：instance 属性、linkedData（出边+入边+别名）、derivedValues。
     * 与 inferInstance 使用完全相同的数据准备逻辑，供 Agent callFunction 工具复用，
     * 确保 Agent 的函数调用与推理引擎使用一致的 schema 和数据。
     */
    public InstanceContext buildInstanceContext(String objectType, String instanceId) throws Exception {
        Map<String, Object> instance = queryInstance(objectType, instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("Instance not found: " + objectType + " id=" + instanceId);
        }

        Map<String, List<Map<String, Object>>> linkedData = new LinkedHashMap<>();
        OntologySchema schema = loader.getSchema();
        if (schema != null && schema.getLinkTypes() != null) {
            for (LinkType lt : schema.getLinkTypes()) {
                if (!objectType.equals(lt.getSourceType())) continue;
                List<Map<String, Object>> list = queryLinkedInstances(objectType, instanceId, lt.getName());
                linkedData.put(lt.getName(), list);
            }
            for (LinkType lt : schema.getLinkTypes()) {
                if (!objectType.equals(lt.getTargetType())) continue;
                List<Map<String, Object>> incoming = queryIncomingLinkInstances(lt, instanceId);
                if (incoming.isEmpty()) continue;
                String canonicalKey = inferIncomingLinkKey(objectType, lt);
                linkedData.put(canonicalKey, incoming);
            }
            addLinkedDataAliases(linkedData, objectType);
        }

        enrichEntryExitAndMedia(objectType, instanceId, instance, linkedData);
        enrichPathEntryExitAndMedia(objectType, instanceId, instance, linkedData);
        enrichGantryTransactionWithTollItems(objectType, linkedData);

        Map<String, Object> derivedValues = computeDerivedProperties(objectType, instance, linkedData, null);
        return new InstanceContext(instance, linkedData, derivedValues);
    }

    /**
     * 推理所需的完整实例上下文容器
     */
    public static class InstanceContext {
        public final Map<String, Object> instance;
        public final Map<String, List<Map<String, Object>>> linkedData;
        public final Map<String, Object> derivedValues;

        public InstanceContext(Map<String, Object> instance,
                               Map<String, List<Map<String, Object>>> linkedData,
                               Map<String, Object> derivedValues) {
            this.instance = instance;
            this.linkedData = linkedData;
            this.derivedValues = derivedValues;
        }
    }

    /**
     * 查询入边：给定 link（如 gantry_to_path，target=Path），按 targetId 查所有 source 实例（如该 Path 下的所有门架交易）。
     * 当 link 未配置 data_source 时，与自然语言查询一致：使用 .env 配置的默认数据源（dataSourceType=sync，表名=类型名小写）。
     */
    private List<Map<String, Object>> queryIncomingLinkInstances(LinkType linkType, String targetId) {
        String sourceType = linkType.getSourceType();
        String fkProperty;
        boolean useSyncDefault;

        DataSourceMapping linkDs = linkType.getDataSource();
        if (linkDs != null && linkDs.isConfigured()) {
            String targetIdColumn = linkDs.getTargetIdColumn();
            if (targetIdColumn == null) return List.of();
            fkProperty = findPropertyForColumn(sourceType, targetIdColumn);
            if (fkProperty == null) fkProperty = inferFkPropertyForIncomingLink(linkType);
            useSyncDefault = false;
        } else {
            fkProperty = inferFkPropertyForIncomingLink(linkType);
            useSyncDefault = true;
        }
        if (fkProperty == null) return List.of();
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", sourceType);
            queryMap.put("select", List.of("*"));
            queryMap.put("where", Map.of(fkProperty, targetId));
            if (useSyncDefault) {
                queryMap.put("dataSourceType", "sync");
            }
            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            return result.getRows();
        } catch (Exception e) {
            System.err.println("Failed to query incoming link " + linkType.getName() + ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 当 link 未配置 data_source 或无法从映射解析外键属性时，推断源表指向目标的外键属性名（如 Path 的 pass_id）。
     */
    private String inferFkPropertyForIncomingLink(LinkType linkType) {
        if (linkType.getPropertyMappings() != null && linkType.getPropertyMappings().containsKey("pass_id")) {
            return "pass_id";
        }
        if ("Path".equals(linkType.getTargetType()) && linkType.getName() != null && linkType.getName().contains("path")) {
            return "pass_id";
        }
        return null;
    }

    /** 入边在 linkedData 中的 key：如 gantry_to_path(target=Path) -> path_has_gantry_transactions */
    private String inferIncomingLinkKey(String targetType, LinkType linkType) {
        String source = linkType.getSourceType();
        String name = linkType.getName();
        if (name != null && name.contains("_to_")) {
            String stem = source.replaceAll("Transaction$", "").toLowerCase();
            return targetType.toLowerCase() + "_has_" + stem + "_transactions";
        }
        return "incoming_" + name;
    }

    /**
     * 为 linkedData 增加规则/衍生属性中使用的别名（如 Path_has_split_details 与 path_has_split_details）
     * 规则和 CEL 中常用 PascalCase 的 link 名，而 link_types 定义为小写
     */
    private void addLinkedDataAliases(Map<String, List<Map<String, Object>>> linkedData, String objectType) {
        Map<String, List<Map<String, Object>>> aliases = new HashMap<>();
        for (String key : new ArrayList<>(linkedData.keySet())) {
            List<Map<String, Object>> list = linkedData.get(key);
            if (list == null) continue;
            String pascal = toPascalLinkKey(key);
            if (!key.equals(pascal)) aliases.put(pascal, list);
            if ("path_has_path_details".equals(key)) aliases.put("Path_has_details", list);
        }
        linkedData.putAll(aliases);
    }

    /** 为 keyToDisplayName 增加与 linkedData 别名对应的 displayName 映射 */
    private void addLinkedDataDisplayNameAliases(Map<String, String> keyToDisplayName) {
        Map<String, String> aliases = new HashMap<>();
        for (String key : new ArrayList<>(keyToDisplayName.keySet())) {
            String displayName = keyToDisplayName.get(key);
            if (displayName == null) continue;
            String pascal = toPascalLinkKey(key);
            if (!key.equals(pascal)) aliases.put(pascal, displayName);
            if ("path_has_path_details".equals(key)) aliases.put("Path_has_details", displayName);
        }
        keyToDisplayName.putAll(aliases);
    }

    /** 小写 link 名转成规则中使用的形式：path_has_split_details -> Path_has_split_details */
    private static String toPascalLinkKey(String linkName) {
        if (linkName == null || linkName.isEmpty()) return linkName;
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : linkName.toCharArray()) {
            if (c == '_') { sb.append(c); cap = true; continue; }
            sb.append(cap ? Character.toUpperCase(c) : c);
            cap = false;
        }
        return sb.toString();
    }

    /**
     * 为 Path 补充出口/入口交易和 _media，使 is_single_province_etc、is_obu_billing_mode1 能求值，
     * 从而 in_obu_split_scope 可能为 true，OBU 规则才能触发。
     * 当 exit_to_path、entry_to_path 未配置 data_source 时，使用 .env 默认数据源（与自然语言查询 sync 一致）。
     */
    private void enrichPathEntryExitAndMedia(String objectType, String instanceId,
                                             Map<String, Object> instance,
                                             Map<String, List<Map<String, Object>>> linkedData) {
        if (!"Path".equals(objectType)) return;
        OntologySchema schema = loader.getSchema();
        if (schema == null || schema.getLinkTypes() == null) return;
        for (LinkType lt : schema.getLinkTypes()) {
            if (!"Path".equals(lt.getTargetType())) continue;
            if ("exit_to_path".equals(lt.getName())) {
                List<Map<String, Object>> list = queryIncomingLinkInstances(lt, instanceId);
                if (!list.isEmpty()) instance.put("_exit_transaction", list.get(0));
                break;
            }
        }
        for (LinkType lt : schema.getLinkTypes()) {
            if (!"Path".equals(lt.getTargetType())) continue;
            if ("entry_to_path".equals(lt.getName())) {
                List<Map<String, Object>> list = queryIncomingLinkInstances(lt, instanceId);
                if (!list.isEmpty()) {
                    Map<String, Object> entryTx = list.get(0);
                    Map<String, Object> mediaPseudo = new LinkedHashMap<>();
                    mediaPseudo.put("media_type", entryTx.get("media_type"));
                    mediaPseudo.put("card_net", entryTx.get("card_net"));
                    instance.put("_media", mediaPseudo);
                    String entryTxId = String.valueOf(entryTx.get("id"));
                    if (schema.getLinkTypes().stream().anyMatch(l -> "entry_involves_vehicle".equals(l.getName()) && "EntryTransaction".equals(l.getSourceType())))
                        linkedData.put("entry_involves_vehicle", queryLinkedInstances("EntryTransaction", entryTxId, "entry_involves_vehicle"));
                    if (schema.getLinkTypes().stream().anyMatch(l -> "entry_at_station".equals(l.getName()) && "EntryTransaction".equals(l.getSourceType())))
                        linkedData.put("entry_at_station", queryLinkedInstances("EntryTransaction", entryTxId, "entry_at_station"));
                }
                break;
            }
        }
    }

    /**
     * 兼容 toll 本体：为 Passage 补充出口/入口交易及 entry 侧的车辆、收费站、介质信息
     */
    private void enrichEntryExitAndMedia(String objectType, String instanceId,
                                         Map<String, Object> instance,
                                         Map<String, List<Map<String, Object>>> linkedData) {
        if (!"Passage".equals(objectType)) return;
        List<Map<String, Object>> exitTxList = queryLinkedInstances("Passage", instanceId, "passage_has_exit");
        if (!exitTxList.isEmpty()) {
            instance.put("_exit_transaction", exitTxList.get(0));
        }
        List<Map<String, Object>> entryTxList = queryLinkedInstances("Passage", instanceId, "passage_has_entry");
        if (!entryTxList.isEmpty()) {
            Map<String, Object> entryTx = entryTxList.get(0);
            String entryTxId = String.valueOf(entryTx.get("id"));
            linkedData.put("entry_involves_vehicle", queryLinkedInstances("EntryTransaction", entryTxId, "entry_involves_vehicle"));
            linkedData.put("entry_at_station", queryLinkedInstances("EntryTransaction", entryTxId, "entry_at_station"));
            Map<String, Object> mediaPseudo = new LinkedHashMap<>();
            mediaPseudo.put("media_type", entryTx.get("media_type"));
            mediaPseudo.put("card_net", entryTx.get("card_net"));
            instance.put("_media", mediaPseudo);
        } else {
            linkedData.putIfAbsent("entry_involves_vehicle", List.of());
            linkedData.putIfAbsent("entry_at_station", List.of());
        }
    }

    /**
     * 批量推理：对当前本体模型下指定对象类型的一批实例执行推理
     */
    public List<Map<String, Object>> inferBatch(String objectType, int limit) throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("from", objectType);
        queryMap.put("select", List.of("id"));
        queryMap.put("limit", limit);
        queryMap.put("dataSourceType", "sync");

        QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> row : result.getRows()) {
            String id = String.valueOf(row.get("id"));
            try {
                InferenceResult ir = inferInstance(objectType, id);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("object_type", objectType);
                entry.put("instance_id", id);
                entry.put("result", ir.toMap());
                results.add(entry);
            } catch (Exception e) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("object_type", objectType);
                entry.put("instance_id", id);
                entry.put("error", e.getMessage());
                results.add(entry);
            }
        }
        return results;
    }

    /**
     * 批量推理（全量同步）：遍历指定对象类型的所有实例，同步返回每条实例的推理摘要。
     * 每条摘要包含 instanceId、firedRules（触发规则列表）、facts（产生的事实）。
     * 推理过程写入 logs/Reasoning.log。
     */
    public List<Map<String, Object>> inferBatchAllSync(String objectType) throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("from", objectType);
        queryMap.put("select", List.of("*"));
        queryMap.put("dataSourceType", "sync");
        QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
        List<Map<String, Object>> rows = result.getRows();

        List<Map<String, Object>> summaries = new ArrayList<>();
        try (ReasoningLogger log = ReasoningLogger.open()) {
            log.separator();
            log.line("[BatchSync] ========== 开始批量推理（同步）==========");
            log.line("[BatchSync] objectType=" + objectType + " | time=" + java.time.LocalDateTime.now());
            log.line("[BatchSync] 共查询到 " + rows.size() + " 条实例");
            log.flush();

            int idx = 0;
            for (Map<String, Object> row : rows) {
                String instanceId = resolveRowId(objectType, row);
                if (instanceId == null || instanceId.isBlank() || "null".equals(instanceId)) continue;
                idx++;
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("instanceId", instanceId);
                log.subSeparator();
                log.line("[BatchSync] [" + idx + "/" + rows.size() + "] 推理实例: " + instanceId);
                try {
                    InferenceResult ir = inferInstanceWithLogger(objectType, instanceId, log).result();
                    List<String> firedRules = new ArrayList<>();
                    for (InferenceResult.TraceEntry entry : ir.getTrace()) {
                        if (!firedRules.contains(entry.ruleName())) {
                            firedRules.add(entry.ruleName());
                        }
                    }
                    Map<String, Object> facts = new LinkedHashMap<>();
                    for (Fact f : ir.getProducedFacts()) {
                        facts.put(f.getPredicate(), f.getValue());
                    }
                    summary.put("firedRules", firedRules);
                    summary.put("facts", facts);
                    summary.put("cycleCount", ir.getCycleCount());
                    log.line("[BatchSync] 结果: firedRules=" + firedRules + " cycleCount=" + ir.getCycleCount());
                } catch (Exception e) {
                    summary.put("firedRules", List.of());
                    summary.put("facts", Map.of());
                    summary.put("error", e.getMessage());
                    log.line("[BatchSync] ERROR: " + e.getMessage());
                }
                log.flush();
                summaries.add(summary);
            }

            log.separator();
            log.line("[BatchSync] ========== 批量推理完成 | 共处理 " + idx + " 条 ==========");
            log.flush();
        }
        return summaries;
    }

    /**
     * 批量推理（全量异步）：遍历指定对象类型的所有实例，将每条推理过程及结果写入 Reasoning.log。
     * 异步执行，立即返回。
     */
    public void inferBatchAllAsync(String objectType) {
        Thread thread = new Thread(() -> {
            try (ReasoningLogger log = ReasoningLogger.open()) {
                log.separator();
                log.line("[BatchAsync] ========== 开始批量推理（异步）==========");
                log.line("[BatchAsync] objectType=" + objectType + " | time=" + java.time.LocalDateTime.now());
                log.flush();

                Map<String, Object> queryMap = new HashMap<>();
                queryMap.put("from", objectType);
                queryMap.put("select", List.of("*"));
                queryMap.put("dataSourceType", "sync");
                QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
                List<Map<String, Object>> rows = result.getRows();

                log.line("[BatchAsync] 共查询到 " + rows.size() + " 条实例");
                log.flush();

                int success = 0, failed = 0, idx = 0;
                for (Map<String, Object> row : rows) {
                    String instanceId = resolveRowId(objectType, row);
                    if (instanceId == null || instanceId.isBlank() || "null".equals(instanceId)) continue;
                    idx++;
                    log.subSeparator();
                    log.line("[BatchAsync] [" + idx + "/" + rows.size() + "] 推理实例: " + instanceId);
                    try {
                        InferenceResult ir = inferInstanceWithLogger(objectType, instanceId, log).result();
                        List<String> firedRules = new ArrayList<>();
                        for (InferenceResult.TraceEntry entry : ir.getTrace()) {
                            firedRules.add(entry.ruleName() + "=" + entry.fact().getValue());
                        }
                        Map<String, Object> facts = new LinkedHashMap<>();
                        for (Fact f : ir.getProducedFacts()) {
                            facts.put(f.getPredicate(), f.getValue());
                        }

                        // 每条规则的匹配详情（含未匹配规则，便于分析为何未触发）
                        log.line("  [trace] cycles=" + ir.getCycleCount() + " | fired=" + firedRules.size());
                        for (InferenceResult.CycleDetail cycle : ir.getCycleDetails()) {
                            Map<String, Object> cycleMap = cycle.toMap();
                            int cycleNo = (int) cycleMap.get("cycle");
                            boolean newFacts = (boolean) cycleMap.get("newFactsProduced");
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> ruleList = (List<Map<String, Object>>) cycleMap.get("rules");
                            log.line("    Cycle " + cycleNo + " newFacts=" + newFacts + " rules=" + (ruleList != null ? ruleList.size() : 0) + ":");
                            if (ruleList != null) {
                                for (Map<String, Object> rme : ruleList) {
                                    boolean rMatched = Boolean.TRUE.equals(rme.get("matched"));
                                    String tag = rMatched ? "[FIRED]" : "[SKIP] ";
                                    log.line("      " + tag + " " + rme.get("rule")
                                        + (rme.get("fact") != null ? " -> fact=" + rme.get("fact") : ""));
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> details = (List<Map<String, Object>>) rme.get("matchDetails");
                                    if (details != null) {
                                        for (Map<String, Object> md : details) {
                                            log.line("        [cond] " + md.get("condition")
                                                + " matched=" + md.get("matched")
                                                + " actual=" + md.get("actualValue"));
                                        }
                                    }
                                }
                            }
                        }
                        log.line("[BatchAsync] [OK] id=" + instanceId
                            + " | cycles=" + ir.getCycleCount()
                            + " | fired=" + firedRules.size()
                            + " | rules=" + firedRules
                            + " | facts=" + facts);
                        success++;
                    } catch (Exception e) {
                        log.line("[BatchAsync] [ERR] id=" + instanceId + " | error=" + e.getMessage());
                        failed++;
                    }
                    log.flush();
                }

                log.separator();
                log.line("[BatchAsync] ========== 批量推理完成 | success=" + success + " | failed=" + failed
                    + " | endTime=" + java.time.LocalDateTime.now() + " ==========");
                log.flush();
            } catch (Exception e) {
                System.err.println("[BatchAsync] 写日志失败: " + e.getMessage());
            }
        }, "batch-all-reasoning");
        thread.setDaemon(true);
        thread.start();
    }

    /** 从查询行中解析实例 ID */
    private String resolveRowId(String objectType, Map<String, Object> row) {
        String idProp = resolveInstanceIdProperty(objectType);
        Object val = row.get(idProp);
        if (val != null) return String.valueOf(val);
        val = row.get("id");
        if (val != null) return String.valueOf(val);
        if (!row.isEmpty()) return String.valueOf(row.values().iterator().next());
        return null;
    }

    /**
     * 计算指定对象类型的衍生属性（按当前 schema）。
     * 当 log 非空时，输出详细日志：函数调用结果、衍生属性 expr 与求值结果。
     */
    private Map<String, Object> computeDerivedProperties(String objectType,
                                                          Map<String, Object> instance,
                                                          Map<String, List<Map<String, Object>>> linkedData,
                                                          ReasoningLogger log) {
        Map<String, Object> derived = new LinkedHashMap<>();
        try {
            OntologySchema schema = loader.getSchema();
            if (schema == null || schema.getObjectTypes() == null) return derived;
            ObjectType ot = schema.getObjectTypes().stream()
                .filter(t -> objectType.equals(t.getName()))
                .findFirst()
                .orElse(null);
            if (ot == null || ot.getProperties() == null) return derived;

            // 当有 logger 且为 Passage 时，显式调用并打印关键函数结果
            if (log != null && "Passage".equals(objectType)) {
                Map<String, Object> thisObj = buildThisForPassage(instance, linkedData);
                logFunctionResults(log, thisObj);
            }

            for (Property prop : ot.getProperties()) {
                if (prop.isDerived() && prop.getExpr() != null) {
                    String msg = "[Reasoning]     [expr] " + prop.getName() + " = " + prop.getExpr();
                    if (log != null) log.line(msg); else System.out.println("[DerivedProp] Evaluating: " + prop.getName() + " | expr: " + prop.getExpr());
                    try {
                        Map<String, Object> env = buildCelEnv(instance, linkedData, derived);
                        CelEvalContext ctx = new CelEvalContext();
                        ctx.setInstance(instance);
                        ctx.setLinks(linkedData);
                        Object value = celEvaluationService.evaluate(prop.getExpr(), env, ctx);
                        derived.put(prop.getName(), value);
                        String resultMsg = "[Reasoning]     [result] " + prop.getName() + " = " + value + " (" + (value != null ? value.getClass().getSimpleName() : "null") + ")";
                        if (log != null) log.line(resultMsg); else System.out.println("[DerivedProp]   OK: " + prop.getName() + " = " + value);
                    } catch (Exception e) {
                        String errMsg = "[Reasoning]     [FAILED] " + prop.getName() + " | error: " + e.getMessage();
                        if (log != null) log.line(errMsg); else System.err.println("[DerivedProp]   FAILED: " + prop.getName() + " | error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DerivedProp] Failed to compute derived properties: " + e.getMessage());
            e.printStackTrace();
        }
        return derived;
    }

    /** 构建 Passage 的 this 对象（instance + links），供函数调用 */
    private Map<String, Object> buildThisForPassage(Map<String, Object> instance,
                                                     Map<String, List<Map<String, Object>>> linkedData) {
        Map<String, Object> thisObj = new LinkedHashMap<>(instance != null ? instance : Map.of());
        if (linkedData != null) thisObj.put("links", linkedData);
        return thisObj;
    }

    /** 显式调用 get_split_count、get_actual_count、get_split_sequence、get_actual_sequence 并打印结果 */
    private void logFunctionResults(ReasoningLogger log, Map<String, Object> passageThis) {
        log.line("[Reasoning]   --- 函数调用结果 (用于 count_equal / sequence_equal 分析) ---");
        for (String fn : List.of("get_split_count", "get_actual_count", "get_split_sequence", "get_actual_sequence")) {
            if (!functionRegistry.hasFunction(fn)) continue;
            try {
                Object result = functionRegistry.call(fn, List.of(passageThis));
                log.line("[Reasoning]     " + fn + "(this) = " + result + (result != null ? " (" + result.getClass().getSimpleName() + ")" : ""));
            } catch (Exception e) {
                log.line("[Reasoning]     " + fn + "(this) = ERROR: " + e.getMessage());
            }
        }
    }

    /** 为 Passage 的每个 GantryTransaction 加载 has_gantry_toll_item，供 get_actual_count / get_actual_sequence 使用 */
    private void enrichGantryTransactionWithTollItems(String objectType,
                                                       Map<String, List<Map<String, Object>>> linkedData) {
        if (!"Passage".equals(objectType)) return;
        List<Map<String, Object>> gantryList = linkedData.get("has_gantry_transaction");
        if (gantryList == null) gantryList = linkedData.get("Has_Gantry_Transaction");
        if (gantryList == null || gantryList.isEmpty()) return;

        for (Map<String, Object> tx : gantryList) {
            Object txId = tx.get("transaction_id");
            if (txId == null) txId = tx.get("trade_id");
            if (txId == null) continue;
            List<Map<String, Object>> tollItems = queryLinkedInstances("GantryTransaction", String.valueOf(txId), "has_gantry_toll_item");
            @SuppressWarnings("unchecked")
            Map<String, Object> links = (Map<String, Object>) tx.get("links");
            if (links == null) {
                links = new LinkedHashMap<>();
                tx.put("links", links);
            }
            links.put("has_gantry_toll_item", tollItems);
        }
    }

    /** 打印 linkedData 中指定 key 的详细结构（用于 has_split_item、has_gantry_transaction 分析） */
    private void logLinkedDataDetails(ReasoningLogger log,
                                      Map<String, List<Map<String, Object>>> linkedData,
                                      String... keys) {
        log.line("[Reasoning]   --- linkedData 详细结构 ---");
        for (String key : keys) {
            List<Map<String, Object>> list = linkedData.get(key);
            if (list == null) list = linkedData.get(toPascalLinkKey(key));
            if (list == null) continue;
            log.line("[Reasoning]     " + key + ": " + list.size() + " 条");
            for (int i = 0; i < list.size(); i++) {
                Map<String, Object> item = list.get(i);
                StringBuilder sb = new StringBuilder();
                sb.append("[Reasoning]       [").append(i + 1).append("] ");
                if ("has_split_item".equals(key) || "Has_Split_Item".equals(key)) {
                    sb.append("position=").append(item.get("position"))
                      .append(" toll_interval_id=").append(item.get("toll_interval_id"))
                      .append(" passage_id=").append(item.get("passage_id"));
                } else if ("has_gantry_transaction".equals(key) || "Has_Gantry_Transaction".equals(key)) {
                    sb.append("transaction_id=").append(item.get("transaction_id"))
                      .append(" gantry_hex=").append(item.get("gantry_hex"))
                      .append(" in_split_flag=").append(item.get("in_split_flag"));
                    Object links = item.get("links");
                    if (links instanceof Map) {
                        @SuppressWarnings("unchecked")
                        List<?> tollItems = (List<?>) ((Map<?, ?>) links).get("has_gantry_toll_item");
                        sb.append(" has_gantry_toll_item=").append(tollItems != null ? tollItems.size() : 0);
                    }
                } else {
                    sb.append(item);
                }
                log.line(sb.toString());
            }
        }
    }

    /**
     * 校验 CEL 表达式是否合法（仅编译检查，不求值）。
     * 声明变量 {@code links}，故 {@code size(links.path_has_path_details)} 等可通过校验。
     * 用于前端「查询验证」与脚本编辑时的即时校验。
     */
    public Map<String, Object> validateCel(String expr) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (expr == null || expr.isBlank()) {
            result.put("valid", false);
            result.put("message", "表达式为空");
            return result;
        }
        try {
            celEvaluationService.validateCompile(expr.trim());
            result.put("valid", true);
            result.put("message", "表达式合法");
        } catch (Exception e) {
            result.put("valid", false);
            result.put("message", e.getMessage() != null ? e.getMessage() : "编译失败");
        }
        return result;
    }

    /**
     * 使用给定上下文对 CEL 表达式求值（用于脚本编辑、测试）。
     */
    public Object evaluateCel(String expr, Map<String, Object> properties,
                              Map<String, List<Map<String, Object>>> linkedData) {
        if (expr == null || expr.isBlank()) return null;
        Map<String, Object> env = buildCelEnv(properties != null ? properties : Map.of(),
                linkedData != null ? linkedData : Map.of(), null);
        CelEvalContext ctx = new CelEvalContext();
        ctx.setInstance(properties != null ? properties : Map.of());
        ctx.setLinks(linkedData);
        return celEvaluationService.evaluate(expr.trim(), env, ctx);
    }

    /**
     * 使用当前本体模型下指定实例的上下文数据对 CEL 表达式求值。
     * 与规则执行、函数测试使用相同的 InstanceContext（instance + linkedData + derivedValues）。
     */
    public Object evaluateCelWithInstance(String expr, String objectType, String instanceId) throws Exception {
        if (expr == null || expr.isBlank()) return null;
        InstanceContext ctx = buildInstanceContext(objectType, instanceId);
        Map<String, Object> properties = new HashMap<>(ctx.instance);
        properties.putAll(ctx.derivedValues != null ? ctx.derivedValues : Map.of());
        Map<String, Object> env = buildCelEnv(properties, ctx.linkedData != null ? ctx.linkedData : Map.of(), ctx.derivedValues);
        CelEvalContext celCtx = new CelEvalContext();
        celCtx.setObjectType(objectType);
        celCtx.setInstanceId(instanceId);
        celCtx.setInstance(ctx.instance);
        celCtx.setLinks(ctx.linkedData);
        return celEvaluationService.evaluate(expr.trim(), env, celCtx);
    }

    /**
     * 使用给定参数调用已注册函数（用于函数测试）。
     */
    public Object testFunction(String name, List<Object> args) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("函数名不能为空");
        }
        return functionRegistry.call(name, args != null ? args : List.of());
    }

    /**
     * 使用当前本体模型下指定实例的上下文数据测试函数（与规则执行一致：构建实例上下文后按函数入参解析并调用）。
     * 调用方需先通过模型切换接口切换到目标本体，此处使用当前 Loader 的 schema 与数据源。
     */
    public Object testFunctionWithInstance(String functionName, String objectType, String instanceId) throws Exception {
        InstanceContext ctx = buildInstanceContext(objectType, instanceId);
        List<Object> args = resolveFunctionArgsFromContext(functionName, ctx);
        return functionRegistry.call(functionName, args);
    }

    /**
     * 根据函数定义与实例上下文解析出调用参数。
     * 按参数类型智能匹配：实体类型→instance，list<X>→linkedData，基本类型→默认值或从 instance 属性取值。
     */
    private List<Object> resolveFunctionArgsFromContext(String functionName, InstanceContext ctx) {
        List<Object> args = new ArrayList<>();
        // JS 脚本函数（如 get_split_count、get_actual_count）接收的是 instance+links 合并的 this 对象
        Map<String, Object> thisObj = buildThisForPassage(ctx.instance, ctx.linkedData);
        try {
            FunctionDef fn = loader.getFunction(functionName);
            List<FunctionParam> params = fn.getInput() != null ? fn.getInput() : List.of();
            if (params.isEmpty()) {
                args.add(thisObj);
                return args;
            }
            Set<String> knownObjectTypes = loader.listObjectTypes().stream()
                    .map(ot -> ot.getName()).collect(java.util.stream.Collectors.toSet());
            for (int i = 0; i < params.size(); i++) {
                FunctionParam p = params.get(i);
                String paramType = p.getType() != null ? p.getType().trim() : "";
                if (knownObjectTypes.contains(paramType)) {
                    // 传入 instance+links 合并对象，兼容 JS 脚本函数通过 passage.links.xxx 访问关联数据
                    args.add(thisObj);
                } else if (paramType.startsWith("list<") || paramType.startsWith("List<")) {
                    String linkKey = findLinkKeyForParam(ctx.linkedData, p.getName());
                    if (linkKey != null && ctx.linkedData.get(linkKey) != null) {
                        args.add(ctx.linkedData.get(linkKey));
                    } else {
                        args.add(List.<Map<String, Object>>of());
                    }
                } else if (isBasicType(paramType)) {
                    Object defaultVal = p.getDefaultValue();
                    if (defaultVal != null) {
                        args.add(defaultVal);
                    } else if (ctx.instance.containsKey(p.getName())) {
                        args.add(ctx.instance.get(p.getName()));
                    } else {
                        args.add(getBasicTypeDefault(paramType));
                    }
                } else if (i == 0) {
                    args.add(thisObj);
                } else {
                    String linkKey = findLinkKeyForParam(ctx.linkedData, p.getName());
                    if (linkKey != null && ctx.linkedData.get(linkKey) != null) {
                        args.add(ctx.linkedData.get(linkKey));
                    } else {
                        args.add(List.<Map<String, Object>>of());
                    }
                }
            }
            return args;
        } catch (Loader.NotFoundException e) {
            // schema 中未找到函数定义时，兜底传 this 对象（兼容 JS 脚本函数）
            args.add(thisObj);
            return args;
        }
    }

    private static boolean isBasicType(String type) {
        if (type == null || type.isEmpty()) return false;
        return Set.of("string", "int", "integer", "long", "float", "double", "number", "boolean", "date", "datetime")
                .contains(type.toLowerCase());
    }

    private static Object getBasicTypeDefault(String type) {
        if (type == null) return null;
        return switch (type.toLowerCase()) {
            case "int", "integer", "long" -> 0;
            case "float", "double", "number" -> 0.0;
            case "boolean" -> false;
            case "string" -> "";
            default -> null;
        };
    }

    private String findLinkKeyForParam(Map<String, List<Map<String, Object>>> linkedData, String paramName) {
        if (linkedData == null || paramName == null) return null;
        if (linkedData.containsKey(paramName)) return paramName;
        String snake = camelToSnake(paramName);
        if (linkedData.containsKey(snake)) return snake;
        for (String key : linkedData.keySet()) {
            if (key.replace("_", "").equalsIgnoreCase(paramName.replace("_", ""))) return key;
        }
        return null;
    }

    private static String camelToSnake(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 查询单个实例，按 schema 定义的结构构建查询。
     * - WHERE：使用 schema 中定义的实例标识属性（id 或 pass_id 等）
     * - SELECT：使用 schema 中定义的非衍生属性列表，而非 SELECT *
     * - dataSourceType：使用 sync（同步表），列名与本体属性名一致，避免查询 mapping 原始表
     */
    public Map<String, Object> queryInstance(String objectType, String id) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", objectType);
            queryMap.put("select", getSchemaSelectProperties(objectType));
            queryMap.put("where", Map.of(resolveInstanceIdProperty(objectType), id));
            queryMap.put("limit", 1);
            queryMap.put("dataSourceType", "sync");

            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            return result.getRows().isEmpty() ? null : result.getRows().get(0);
        } catch (Exception e) {
            System.err.println("Failed to query instance: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析对象类型的实例标识属性名。
     * 优先级：
     *   1. 显式 id 属性
     *   2. 类型名转 snake_case + "_id"（如 Passage→passage_id, GantryTransaction→gantry_transaction_id）
     *   3. required=true 且名称以 "_id" 结尾的第一个属性（如 GantryTransaction.transaction_id）
     *   4. pass_id（兼容 Path 等类型）
     *   5. 兜底返回 "id"
     */
    private String resolveInstanceIdProperty(String objectTypeName) {
        OntologySchema schema = loader.getSchema();
        if (schema == null || schema.getObjectTypes() == null) return "id";
        ObjectType ot = schema.getObjectTypes().stream()
            .filter(t -> objectTypeName.equals(t.getName()))
            .findFirst()
            .orElse(null);
        if (ot == null || ot.getProperties() == null) return "id";

        // 1. 显式 id 属性
        boolean hasId = ot.getProperties().stream().anyMatch(p -> "id".equals(p.getName()));
        if (hasId) return "id";

        // 2. 类型名转 snake_case + "_id"（如 Passage → passage_id, GantryTransaction → gantry_transaction_id）
        String typeSnake = camelToSnake(objectTypeName);
        String conventionalId = typeSnake + "_id";
        boolean hasConventionalId = ot.getProperties().stream().anyMatch(p -> conventionalId.equals(p.getName()));
        if (hasConventionalId) return conventionalId;

        // 3. required=true 且名称以 "_id" 结尾的第一个属性（如 GantryTransaction.transaction_id）
        //    避免误将 pass_id 等非主键的 required 字段当作主键
        java.util.Optional<Property> requiredIdProp = ot.getProperties().stream()
            .filter(p -> p.isRequired() && p.getName() != null && p.getName().endsWith("_id"))
            .findFirst();
        if (requiredIdProp.isPresent()) return requiredIdProp.get().getName();

        // 4. pass_id（兼容 Path 等类型）
        boolean hasPassId = ot.getProperties().stream().anyMatch(p -> "pass_id".equals(p.getName()));
        if (hasPassId) return "pass_id";

        return "id";
    }

    /**
     * 按 schema 定义获取 SELECT 属性列表（非衍生属性），保证按 schema 结构查询。
     */
    private List<String> getSchemaSelectProperties(String objectTypeName) {
        OntologySchema schema = loader.getSchema();
        if (schema == null || schema.getObjectTypes() == null) return List.of("*");
        ObjectType ot = schema.getObjectTypes().stream()
            .filter(t -> objectTypeName.equals(t.getName()))
            .findFirst()
            .orElse(null);
        if (ot == null || ot.getProperties() == null) return List.of("*");
        List<String> props = new ArrayList<>();
        for (Property p : ot.getProperties()) {
            if (!p.isDerived()) props.add(p.getName());
        }
        return props.isEmpty() ? List.of("*") : props;
    }

    /**
     * 查询关联实例（直接查询目标表，避免 link API 的结果扁平化问题）
     * @param sourceType 源对象类型名称
     * @param sourceId 源对象ID
     * @param linkName 关联关系名称
     */
    public List<Map<String, Object>> queryLinkedInstances(String sourceType, String sourceId, String linkName) {
        try {
            OntologySchema schema = loader.getSchema();
            LinkType linkType = schema.getLinkTypes().stream()
                .filter(lt -> linkName.equals(lt.getName()))
                .findFirst()
                .orElse(null);

            if (linkType == null) return List.of();

            String targetType = linkType.getTargetType();
            DataSourceMapping linkDs = linkType.getDataSource();

            if (linkDs != null && linkDs.isConfigured()) {
                if ("many-to-one".equals(linkType.getCardinality())) {
                    return queryManyToOneLink(sourceType, sourceId, targetType, linkDs);
                } else {
                    String fkProperty = determineForeignKeyProperty(linkType, sourceType);
                    if (fkProperty == null) {
                        System.err.println("Cannot determine FK property for link " + linkName);
                        return List.of();
                    }
                    Map<String, Object> queryMap = new HashMap<>();
                    queryMap.put("from", targetType);
                    queryMap.put("select", List.of("*"));
                    queryMap.put("where", Map.of(fkProperty, sourceId));
                    queryMap.put("dataSourceType", "sync");
                    QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
                    return result.getRows();
                }
            }

            // data_source 未配置时，通过 property_mappings 推断外键关系，使用同步表查询
            Map<String, String> propMappings = linkType.getPropertyMappings();
            if (propMappings == null || propMappings.isEmpty()) return List.of();

            if ("many-to-one".equals(linkType.getCardinality())) {
                return queryManyToOneLinkViaPropertyMappings(sourceType, sourceId, targetType, propMappings);
            } else {
                return queryOneToManyLinkViaPropertyMappings(sourceType, sourceId, targetType, propMappings);
            }
        } catch (Exception e) {
            System.err.println("Failed to query linked instances for " + linkName + ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * data_source 未配置时的 many-to-one 出边查询：
     * property_mappings 格式为 {源属性: 目标属性}，从源实例取源属性值，在目标同步表按目标属性查询
     */
    private List<Map<String, Object>> queryManyToOneLinkViaPropertyMappings(
            String sourceType, String sourceId, String targetType, Map<String, String> propMappings) {
        Map<String, Object> sourceInstance = queryInstance(sourceType, sourceId);
        if (sourceInstance == null) return List.of();

        Map<String, Object> whereClause = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : propMappings.entrySet()) {
            String sourceProp = entry.getKey();
            String targetProp = entry.getValue();
            Object fkValue = sourceInstance.get(sourceProp);
            if (fkValue == null) return List.of();
            whereClause.put(targetProp, fkValue.toString());
        }

        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", targetType);
            queryMap.put("select", List.of("*"));
            queryMap.put("where", whereClause);
            queryMap.put("dataSourceType", "sync");
            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            return result.getRows();
        } catch (Exception e) {
            System.err.println("Failed to query many-to-one link via property_mappings: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * data_source 未配置时的 one-to-many 出边查询：
     * property_mappings 格式为 {源属性: 目标属性}，取源实例的源属性值，在目标同步表按目标属性查询
     */
    private List<Map<String, Object>> queryOneToManyLinkViaPropertyMappings(
            String sourceType, String sourceId, String targetType, Map<String, String> propMappings) {
        // 对于 one-to-many 关系，property_mappings 的 key 是源对象属性名，value 是目标对象属性名。
        // 当 sourceProp 等于源类型主键时，fkValue 就是 sourceId，无需再查 queryInstance。
        // 对于其他 sourceProp（非主键属性），才需要查源实例获取实际值。
        String sourceIdProp = resolveInstanceIdProperty(sourceType);
        Map<String, Object> sourceInstance = null;

        Map<String, Object> whereClause = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : propMappings.entrySet()) {
            String sourceProp = entry.getKey();
            String targetProp = entry.getValue();
            Object fkValue;
            if (sourceProp.equals(sourceIdProp)) {
                fkValue = sourceId;
            } else {
                if (sourceInstance == null) {
                    sourceInstance = queryInstance(sourceType, sourceId);
                }
                if (sourceInstance == null) return List.of();
                fkValue = sourceInstance.get(sourceProp);
            }
            if (fkValue == null) return List.of();
            whereClause.put(targetProp, fkValue.toString());
        }

        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", targetType);
            queryMap.put("select", List.of("*"));
            queryMap.put("where", whereClause);
            queryMap.put("dataSourceType", "sync");
            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            return result.getRows();
        } catch (Exception e) {
            System.err.println("Failed to query one-to-many link via property_mappings: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 处理 many-to-one 链接：FK 在源表中
     * 1. 查源实例获取 source_id_column 对应的属性值
     * 2. 在目标表中按 target_id_column 查找
     */
    private List<Map<String, Object>> queryManyToOneLink(String sourceType, String sourceId,
                                                          String targetType, DataSourceMapping linkDs) {
        String sourceIdColumn = linkDs.getSourceIdColumn();
        String targetIdColumn = linkDs.getTargetIdColumn();
        if (sourceIdColumn == null || targetIdColumn == null) return List.of();

        // 查找源类型中映射到 source_id_column 的属性名
        String sourceFkProperty = findPropertyForColumn(sourceType, sourceIdColumn);
        if (sourceFkProperty == null) return List.of();

        // 查询源实例获取 FK 值
        Map<String, Object> sourceInstance = queryInstance(sourceType, sourceId);
        if (sourceInstance == null) return List.of();

        Object fkValue = sourceInstance.get(sourceFkProperty);
        if (fkValue == null) return List.of();

        // 查找目标类型中映射到 target_id_column 的属性名
        String targetProperty = findPropertyForColumn(targetType, targetIdColumn);
        if (targetProperty == null) return List.of();

        // 查询目标类型
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", targetType);
            queryMap.put("select", List.of("*"));
            queryMap.put("where", Map.of(targetProperty, fkValue.toString()));
            queryMap.put("dataSourceType", "sync");

            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            return result.getRows();
        } catch (Exception e) {
            System.err.println("Failed to query target for many-to-one link: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 查找对象类型中映射到指定数据库列的属性名
     */
    private String findPropertyForColumn(String typeName, String columnName) {
        try {
            ObjectType objectType = loader.getSchema().getObjectTypes().stream()
                .filter(ot -> typeName.equals(ot.getName()))
                .findFirst()
                .orElse(null);
            if (objectType == null) return null;

            DataSourceMapping ds = objectType.getDataSource();
            if (ds == null) return null;

            // 检查是否是 ID 列
            if (columnName.equalsIgnoreCase(ds.getIdColumn())) {
                return "id";
            }

            // 在属性映射中查找
            if (objectType.getProperties() != null) {
                for (Property prop : objectType.getProperties()) {
                    String colName = ds.getColumnName(prop.getName());
                    if (colName != null && colName.equalsIgnoreCase(columnName)) {
                        return prop.getName();
                    }
                }
                // 属性名直接匹配列名
                for (Property prop : objectType.getProperties()) {
                    if (prop.getName().equalsIgnoreCase(columnName)) {
                        return prop.getName();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding property for column " + columnName + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * 根据 link 定义确定目标表中的外键属性名
     */
    private String determineForeignKeyProperty(LinkType linkType, String sourceType) {
        // 对于 foreign_key 模式，source_id_column 是目标表中引用源表ID的列名
        // 需要将数据库列名映射回目标类型的属性名
        DataSourceMapping linkDs = linkType.getDataSource();
        if (linkDs == null || !linkDs.isConfigured()) return null;

        String sourceIdColumn = linkDs.getSourceIdColumn();
        if (sourceIdColumn == null) return null;

        // 查找目标类型中哪个属性映射到这个列
        String targetTypeName = linkType.getTargetType();
        try {
            ObjectType targetObjectType = loader.getSchema().getObjectTypes().stream()
                .filter(ot -> targetTypeName.equals(ot.getName()))
                .findFirst()
                .orElse(null);

            if (targetObjectType == null) return null;

            DataSourceMapping targetDs = targetObjectType.getDataSource();
            if (targetDs == null) return null;

            // 检查是否是 ID 列
            if (sourceIdColumn.equalsIgnoreCase(targetDs.getIdColumn())) {
                return "id";
            }

            // 在属性映射中查找
            if (targetObjectType.getProperties() != null) {
                for (Property prop : targetObjectType.getProperties()) {
                    String columnName = targetDs.getColumnName(prop.getName());
                    if (columnName != null && columnName.equalsIgnoreCase(sourceIdColumn)) {
                        return prop.getName();
                    }
                }
            }

            // 如果目标表列名与属性名相同（如 SplitDetail 的 pass_id）
            if (targetObjectType.getProperties() != null) {
                for (Property prop : targetObjectType.getProperties()) {
                    if (prop.getName().equalsIgnoreCase(sourceIdColumn)) {
                        return prop.getName();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error determining FK property: " + e.getMessage());
        }
        return null;
    }

    /**
     * 返回当前加载本体下已解析的 SWRL 规则列表（按 schema 懒解析，切换本体后自动更新）。
     */
    public List<SWRLRule> getParsedRules() {
        ensureRulesParsedForCurrentSchema();
        return parsedRules;
    }

    /**
     * 返回当前加载本体下的函数显示名映射（与 getParsedRules 使用同一套懒解析缓存）。
     */
    public Map<String, String> getFunctionDisplayNames() {
        ensureRulesParsedForCurrentSchema();
        return functionDisplayNames != null ? functionDisplayNames : Map.of();
    }

    /**
     * 获取当前模型下已解析的规则数量
     */
    public int getParsedRuleCount() {
        return getParsedRules().size();
    }

    /**
     * 获取当前本体中“有规则”的对象类型，供推理页按当前模型选择类型（如 Passage / Path）
     */
    public List<String> listInferenceRootTypes() {
        OntologySchema schema = loader.getSchema();
        if (schema == null || schema.getRules() == null || schema.getObjectTypes() == null) return List.of();
        Set<String> typeNames = schema.getObjectTypes().stream().map(ObjectType::getName).collect(Collectors.toSet());
        // 从规则 expr 中提取 Type(?var) 中的 Type，且该 Type 在当前 object_types 中存在
        Pattern p = Pattern.compile("\\b([A-Za-z][A-Za-z0-9_]*)\\s*\\(");
        Set<String> rootTypes = new LinkedHashSet<>();
        for (Rule r : schema.getRules()) {
            String expr = r.getExpr();
            if (expr == null) continue;
            Matcher m = p.matcher(expr);
            while (m.find()) {
                String name = m.group(1);
                if (typeNames.contains(name)) rootTypes.add(name);
            }
        }
        return new ArrayList<>(rootTypes);
    }

    /**
     * 获取已注册的函数名
     */
    public Set<String> getRegisteredFunctions() {
        return functionRegistry.getFunctionNames();
    }

    /**
     * 按关键词搜索规则（使用当前加载本体的规则）
     */
    public List<Map<String, Object>> searchRules(String keyword) {
        List<SWRLRule> rules = getParsedRules();
        if (rules.isEmpty()) return List.of();
        String kw = keyword.toLowerCase();
        List<Map<String, Object>> results = new ArrayList<>();
        OntologySchema schema = loader.getSchema();
        List<com.mypalantir.meta.Rule> ruleDefs = schema.getRules();

        for (SWRLRule rule : rules) {
            String name = rule.getName() != null ? rule.getName() : "";
            String displayName = rule.getDisplayName() != null ? rule.getDisplayName() : "";
            // 查找 YAML 中的 description
            String description = "";
            String expr = "";
            if (ruleDefs != null) {
                for (com.mypalantir.meta.Rule rd : ruleDefs) {
                    if (name.equals(rd.getName())) {
                        description = rd.getDescription() != null ? rd.getDescription() : "";
                        expr = rd.getExpr() != null ? rd.getExpr() : "";
                        break;
                    }
                }
            }
            if (name.toLowerCase().contains(kw) ||
                displayName.toLowerCase().contains(kw) ||
                description.toLowerCase().contains(kw)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", name);
                m.put("displayName", displayName);
                m.put("description", description);
                m.put("expr", expr);
                results.add(m);
            }
        }
        return results;
    }

    public FunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    public Loader getLoader() {
        return loader;
    }
}
