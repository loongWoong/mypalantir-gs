package com.mypalantir.reasoning;

import com.mypalantir.meta.*;
import com.mypalantir.reasoning.cel.CelEvaluator;
import com.mypalantir.reasoning.engine.ForwardChainEngine;
import com.mypalantir.reasoning.engine.InferenceResult;
import com.mypalantir.reasoning.function.FunctionRegistry;
import com.mypalantir.reasoning.function.builtin.*;
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
 */
@Service
public class ReasoningService {

    private final Loader loader;
    private final QueryService queryService;
    private final FunctionRegistry functionRegistry;
    private final CelEvaluator celEvaluator;

    /** 按当前模型路径缓存已解析规则，切换模型时自动失效 */
    private volatile String cachedRulesPath;
    private volatile List<SWRLRule> cachedParsedRules;

    public ReasoningService(Loader loader, QueryService queryService, FunctionRegistry functionRegistry) {
        this.loader = loader;
        this.queryService = queryService;
        this.functionRegistry = functionRegistry;
        this.celEvaluator = new CelEvaluator();
    }

    @PostConstruct
    public void initialize() {
        registerBuiltinFunctions();
    }

    /**
     * 获取当前模型下已解析的 SWRL 规则，切换模型后会自动重新解析
     */
    private List<SWRLRule> getParsedRules() {
        OntologySchema schema = loader.getSchema();
        if (schema == null) return List.of();
        String path = loader.getFilePath();
        if (path == null) path = "";
        if (!path.equals(cachedRulesPath)) {
            synchronized (this) {
                if (!path.equals(cachedRulesPath)) {
                    try {
                        SWRLParser parser = new SWRLParser(schema, functionRegistry);
                        cachedParsedRules = parser.parseAll(schema);
                        cachedRulesPath = path;
                        List<String> missing = functionRegistry.getMissingBuiltins(schema);
                        if (!missing.isEmpty()) {
                            System.err.println("WARNING: Missing builtin functions: " + missing);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse SWRL rules: " + e.getMessage());
                        cachedParsedRules = List.of();
                        cachedRulesPath = path;
                    }
                }
            }
        }
        return cachedParsedRules != null ? cachedParsedRules : List.of();
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

    /**
     * 对当前本体模型下指定对象类型与实例执行推理（按右上角所选本体模型）。
     * 关联数据从当前 schema 动态获取，支持 Passage(toll) / Path(schema) 等任意类型。
     */
    public InferenceResult inferInstance(String objectType, String instanceId) throws Exception {
        Map<String, Object> instance = queryInstance(objectType, instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("Instance not found: " + objectType + " id=" + instanceId);
        }

        // 从当前 schema 获取该类型的所有出边，动态查询关联数据
        Map<String, List<Map<String, Object>>> linkedData = new LinkedHashMap<>();
        OntologySchema schema = loader.getSchema();
        if (schema != null && schema.getLinkTypes() != null) {
            for (LinkType lt : schema.getLinkTypes()) {
                if (!objectType.equals(lt.getSourceType())) continue;
                List<Map<String, Object>> list = queryLinkedInstances(objectType, instanceId, lt.getName());
                linkedData.put(lt.getName(), list);
            }
            // 入边：如 Path 的 gantry_to_path（GantryTransaction→Path），按“目标=当前实例”查源表，供规则 links(?p, Path_has_gantry_transactions) 使用
            for (LinkType lt : schema.getLinkTypes()) {
                if (!objectType.equals(lt.getTargetType())) continue;
                List<Map<String, Object>> incoming = queryIncomingLinkInstances(lt, instanceId);
                if (incoming.isEmpty()) continue;
                String canonicalKey = inferIncomingLinkKey(objectType, lt);
                linkedData.put(canonicalKey, incoming);
            }
            // 规则/衍生属性中使用的 key 可能与 link type 名不一致（如 Path_has_split_details vs path_has_split_details）
            addLinkedDataAliases(linkedData, objectType);
        }

        // Passage：补充出口/入口、_media、entry_involves_vehicle、entry_at_station
        enrichEntryExitAndMedia(objectType, instanceId, instance, linkedData);
        // Path：同样需要出口/入口和 _media，否则 is_single_province_etc / is_obu_billing_mode1 恒为 false，OBU 规则不会触发
        enrichPathEntryExitAndMedia(objectType, instanceId, instance, linkedData);

        Map<String, Object> derivedValues = computeDerivedProperties(objectType, instance, linkedData);
        ForwardChainEngine engine = new ForwardChainEngine(functionRegistry, getParsedRules());
        return engine.infer(instance, linkedData, derivedValues);
    }

    /**
     * 查询入边：给定 link（如 gantry_to_path，target=Path），按 targetId 查所有 source 实例（如该 Path 下的所有门架交易）
     */
    private List<Map<String, Object>> queryIncomingLinkInstances(LinkType linkType, String targetId) {
        DataSourceMapping linkDs = linkType.getDataSource();
        if (linkDs == null || !linkDs.isConfigured()) return List.of();
        String targetIdColumn = linkDs.getTargetIdColumn();
        if (targetIdColumn == null) return List.of();
        String sourceType = linkType.getSourceType();
        String fkProperty = findPropertyForColumn(sourceType, targetIdColumn);
        if (fkProperty == null) return List.of();
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", sourceType);
            queryMap.put("select", List.of("*"));
            queryMap.put("where", Map.of(fkProperty, targetId));
            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            return result.getRows();
        } catch (Exception e) {
            System.err.println("Failed to query incoming link " + linkType.getName() + ": " + e.getMessage());
            return List.of();
        }
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
     * 从而 in_obu_split_scope 可能为 true，OBU 规则才能触发。依赖 schema 中 exit_to_path、entry_to_path 的 data_source 配置。
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
     * 对单个 Passage 执行推理（兼容旧接口，等价于 inferInstance("Passage", passageId)）
     */
    public InferenceResult inferPassage(String passageId) throws Exception {
        return inferInstance("Passage", passageId);
    }

    /**
     * 批量推理：对当前本体模型下指定对象类型的一批实例执行推理
     */
    public List<Map<String, Object>> inferBatch(String objectType, int limit) throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("from", objectType);
        queryMap.put("select", List.of("id"));
        queryMap.put("limit", limit);

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
     * 计算指定对象类型的衍生属性（按当前 schema）
     */
    private Map<String, Object> computeDerivedProperties(String objectType,
                                                          Map<String, Object> instance,
                                                          Map<String, List<Map<String, Object>>> linkedData) {
        Map<String, Object> derived = new LinkedHashMap<>();
        try {
            OntologySchema schema = loader.getSchema();
            if (schema == null || schema.getObjectTypes() == null) return derived;
            ObjectType ot = schema.getObjectTypes().stream()
                .filter(t -> objectType.equals(t.getName()))
                .findFirst()
                .orElse(null);
            if (ot == null || ot.getProperties() == null) return derived;
            for (Property prop : ot.getProperties()) {
                if (prop.isDerived() && prop.getExpr() != null) {
                    try {
                        Object value = celEvaluator.evaluate(prop.getExpr(), instance, linkedData);
                        derived.put(prop.getName(), value);
                    } catch (Exception e) {
                        System.err.println("Failed to evaluate derived property '" + prop.getName() + "': " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to compute derived properties: " + e.getMessage());
        }
        return derived;
    }

    /**
     * 查询单个实例
     */
    private Map<String, Object> queryInstance(String objectType, String id) {
        try {
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", objectType);
            queryMap.put("select", List.of("*"));
            queryMap.put("where", Map.of("id", id));
            queryMap.put("limit", 1);

            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            return result.getRows().isEmpty() ? null : result.getRows().get(0);
        } catch (Exception e) {
            System.err.println("Failed to query instance: " + e.getMessage());
            return null;
        }
    }

    /**
     * 查询关联实例（直接查询目标表，避免 link API 的结果扁平化问题）
     * @param sourceType 源对象类型名称
     * @param sourceId 源对象ID
     * @param linkName 关联关系名称
     */
    private List<Map<String, Object>> queryLinkedInstances(String sourceType, String sourceId, String linkName) {
        try {
            OntologySchema schema = loader.getSchema();
            LinkType linkType = schema.getLinkTypes().stream()
                .filter(lt -> linkName.equals(lt.getName()))
                .findFirst()
                .orElse(null);

            if (linkType == null) return List.of();

            String targetType = linkType.getTargetType();
            DataSourceMapping linkDs = linkType.getDataSource();
            if (linkDs == null || !linkDs.isConfigured()) return List.of();

            // 判断 cardinality 决定 FK 方向
            if ("many-to-one".equals(linkType.getCardinality())) {
                // FK 在源表中：先查源实例获取 FK 值，再按 target_id_column 查目标
                return queryManyToOneLink(sourceType, sourceId, targetType, linkDs);
            } else {
                // FK 在目标表中（one-to-many）：按 source_id_column 在目标表中查
                String fkProperty = determineForeignKeyProperty(linkType, sourceType);
                if (fkProperty == null) {
                    System.err.println("Cannot determine FK property for link " + linkName);
                    return List.of();
                }

                Map<String, Object> queryMap = new HashMap<>();
                queryMap.put("from", targetType);
                queryMap.put("select", List.of("*"));
                queryMap.put("where", Map.of(fkProperty, sourceId));

                QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
                return result.getRows();
            }
        } catch (Exception e) {
            System.err.println("Failed to query linked instances for " + linkName + ": " + e.getMessage());
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
}
