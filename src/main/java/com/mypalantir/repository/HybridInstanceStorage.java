package com.mypalantir.repository;

import com.mypalantir.config.Config;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * 混合存储实现
 * 组合关系型数据库存储（详细数据）和Neo4j存储（关键字段和关系）
 */
@Component
public class HybridInstanceStorage implements IInstanceStorage {
    private static final Logger logger = LoggerFactory.getLogger(HybridInstanceStorage.class);

    @Autowired
    private RelationalInstanceStorage relationalStorage;

    @Autowired
    private Neo4jInstanceStorage neo4jStorage;

    @Autowired
    private Loader loader;

    @Autowired
    private Environment environment;

    /**
     * 获取需要存储在Neo4j的字段列表
     * 优先级：
     * 1. 从配置文件读取 storage.neo4j.fields.{objectType}
     * 2. 从配置文件读取 storage.neo4j.fields.default
     * 3. 从ObjectType定义中查找（name, display_name）
     * 4. 使用默认字段：id, name, display_name
     */
    private List<String> getNeo4jFields(String objectType) {
        // 1. 优先从配置文件读取指定对象类型的字段
        String configKey = "storage.neo4j.fields." + objectType.toLowerCase();
        String fieldsConfig = environment.getProperty(configKey);
        
        if (fieldsConfig != null && !fieldsConfig.trim().isEmpty()) {
            List<String> fields = new ArrayList<>();
            String[] fieldArray = fieldsConfig.split(",");
            for (String field : fieldArray) {
                String trimmed = field.trim();
                if (!trimmed.isEmpty()) {
                    fields.add(trimmed);
                }
            }
            if (!fields.isEmpty()) {
                logger.debug("Using configured Neo4j fields for {}: {}", objectType, fields);
                return fields;
            }
        }
        
        // 2. 尝试读取默认配置
        String defaultFieldsConfig = environment.getProperty("storage.neo4j.fields.default");
        if (defaultFieldsConfig != null && !defaultFieldsConfig.trim().isEmpty()) {
            List<String> fields = new ArrayList<>();
            String[] fieldArray = defaultFieldsConfig.split(",");
            for (String field : fieldArray) {
                String trimmed = field.trim();
                if (!trimmed.isEmpty()) {
                    fields.add(trimmed);
                }
            }
            if (!fields.isEmpty()) {
                logger.debug("Using default Neo4j fields for {}: {}", objectType, fields);
                return fields;
            }
        }
        
        // 3. 从ObjectType定义中查找（兼容旧逻辑）
        try {
            ObjectType objectTypeDef = loader.getObjectType(objectType);
            List<String> fields = new ArrayList<>();
            
            // id必须包含
            fields.add("id");
            
            // 从属性定义中查找标记为neo4j_field的字段
            if (objectTypeDef.getProperties() != null) {
                for (com.mypalantir.meta.Property prop : objectTypeDef.getProperties()) {
                    String propName = prop.getName();
                    if ("name".equals(propName) || "display_name".equals(propName)) {
                        fields.add(propName);
                    }
                }
            }
            
            // 如果没有找到，使用默认字段
            if (fields.size() == 1) { // 只有id
                fields.add("name");
                fields.add("display_name");
            }
            
            logger.debug("Using ObjectType-based Neo4j fields for {}: {}", objectType, fields);
            return fields;
        } catch (Loader.NotFoundException e) {
            // 4. 如果找不到对象类型定义，返回默认字段
            logger.debug("ObjectType not found for {}, using default fields", objectType);
            return Arrays.asList("id", "name", "display_name");
        }
    }

    /**
     * 提取关键字段（用于Neo4j存储）
     */
    private Map<String, Object> extractSummaryFields(String objectType, Map<String, Object> data) {
        List<String> neo4jFields = getNeo4jFields(objectType);
        
        Map<String, Object> summary = new HashMap<>();
        
        // id必须包含
        if (data.containsKey("id")) {
            summary.put("id", data.get("id"));
        }
        
        // 提取配置的字段
        for (String field : neo4jFields) {
            if (!"id".equals(field) && data.containsKey(field)) {
                summary.put(field, data.get(field));
            }
        }
        
        return summary;
    }

    /**
     * 检查对象类型是否配置了关系型数据库映射
     */
    private boolean hasRelationalMapping(String objectType) {
        try {
            ObjectType objectTypeDef = loader.getObjectType(objectType);
            return objectTypeDef.getDataSource() != null && objectTypeDef.getDataSource().isConfigured();
        } catch (Loader.NotFoundException e) {
            return false;
        }
    }

    /**
     * 检查是否为系统对象类型（table, database, mapping等）
     * 系统对象类型应该直接使用 Neo4j 存储，不经过 RelationalInstanceStorage，避免递归调用
     */
    private boolean isSystemObjectType(String objectType) {
        if (objectType == null) {
            return false;
        }
        String lowerType = objectType.toLowerCase();
        return "table".equals(lowerType) 
            || "database".equals(lowerType) 
            || "mapping".equals(lowerType)
            || "column".equals(lowerType)
            || "workspace".equals(lowerType);
    }

    @Override
    public String createInstance(String objectType, Map<String, Object> data) throws IOException {
        // 1. 提取关键字段
        Map<String, Object> summaryFields = extractSummaryFields(objectType, data);
        
        // 2. 关键字段存储到Neo4j
        String id = neo4jStorage.createInstance(objectType, summaryFields);
        
        // 3. 详细数据应该通过ETL或直接SQL插入到关系型数据库
        // 这里不直接插入，由ETL系统处理
        
        logger.debug("Created instance {} of type {} in hybrid storage (Neo4j summary only)", id, objectType);
        return id;
    }

    @Override
    public String createInstanceWithId(String objectType, String id, Map<String, Object> data) throws IOException {
        // 1. 提取关键字段
        Map<String, Object> summaryFields = extractSummaryFields(objectType, data);
        
        // 2. 确保id在summaryFields中
        summaryFields.put("id", id);
        
        // 3. 关键字段存储到Neo4j
        String createdId = neo4jStorage.createInstanceWithId(objectType, id, summaryFields);
        
        // 4. 详细数据应该通过ETL或直接SQL插入到关系型数据库
        // 这里不直接插入，由ETL系统处理
        
        logger.debug("Created instance {} of type {} in hybrid storage (Neo4j summary only)", createdId, objectType);
        return createdId;
    }

    @Override
    public Map<String, Object> getInstance(String objectType, String id) throws IOException {
        logger.info("[HybridInstanceStorage] ========== getInstance (hybrid模式) ==========");
        logger.info("[HybridInstanceStorage] getInstance called for objectType: {}, id: {}", objectType, id);
        logger.info("[HybridInstanceStorage] Storage mode: HYBRID (reading from relational DB and Neo4j, NOT from file storage)");
        
        // 系统对象类型（table, database, mapping等）直接使用 Neo4j，避免递归调用
        if (isSystemObjectType(objectType)) {
            logger.info("[HybridInstanceStorage] System object type {} detected, using Neo4j directly to avoid recursion", objectType);
            logger.info("[HybridInstanceStorage] Data source: NEO4J only (NOT file storage)");
            Map<String, Object> result = neo4jStorage.getInstance(objectType, id);
            logger.info("[HybridInstanceStorage] ========== getInstance END ==========");
            return result;
        }
        
        // 1. 优先从关系型数据库查询详细数据（包括同步表）
        // 即使没有配置数据源映射，也尝试查询默认数据库中的同步表
        // 严格查询界限：只从关系型数据库和Neo4j读取，不从文件读取
        boolean hasMapping = hasRelationalMapping(objectType);
        logger.info("[HybridInstanceStorage] hasRelationalMapping({}) = {}", objectType, hasMapping);
        
        // 尝试从关系型数据库查询（包括同步表）
        // 实例存储查询只查询同步表，如果同步表不存在或查询失败，抛出异常，不回退到 Neo4j
        // 严格查询界限：只从关系型数据库和Neo4j读取，不从文件读取
        try {
            logger.info("[HybridInstanceStorage] Attempting to query from RelationalInstanceStorage (sync table only, NOT file storage)");
            Map<String, Object> instance = relationalStorage.getInstance(objectType, id);
            
            // 合并Neo4j中的关键字段（用于图展示）
            try {
                Map<String, Object> neo4jInstance = neo4jStorage.getInstance(objectType, id);
                // 如果关系型数据库中没有某些字段，从Neo4j补充
                for (Map.Entry<String, Object> entry : neo4jInstance.entrySet()) {
                    if (!instance.containsKey(entry.getKey())) {
                        instance.put(entry.getKey(), entry.getValue());
                    }
                }
                logger.debug("[HybridInstanceStorage] Merged instance data from relational DB and Neo4j");
            } catch (IOException e) {
                // Neo4j中没有，只使用关系型数据库的数据
                logger.debug("[HybridInstanceStorage] Instance {} not found in Neo4j, using relational data only", id);
            }
            
            logger.info("[HybridInstanceStorage] Successfully retrieved instance from RelationalInstanceStorage (relational DB, NOT file storage)");
            logger.info("[HybridInstanceStorage] Data source: RELATIONAL DB (sync table) + NEO4J (key fields), NOT file storage");
            logger.info("[HybridInstanceStorage] ========== getInstance END ==========");
            return instance;
        } catch (IOException e) {
            // 实例存储查询失败（同步表不存在或其他错误），直接抛出异常，不回退到 Neo4j
            // 严格查询界限：不从文件读取
            logger.info("[HybridInstanceStorage] Failed to query sync table for instance {} of type {} (sync table may not exist): {}, throwing exception (NOT reading from file storage)", 
                id, objectType, e.getMessage());
            logger.info("[HybridInstanceStorage] ========== getInstance END ==========");
            throw e;
        }
    }

    @Override
    public void updateInstance(String objectType, String id, Map<String, Object> data) throws IOException {
        // 1. 提取关键字段
        Map<String, Object> summaryFields = extractSummaryFields(objectType, data);
        
        // 2. 更新Neo4j中的关键字段
        neo4jStorage.updateInstance(objectType, id, summaryFields);
        
        // 3. 详细数据的更新应该通过ETL或直接SQL更新
        // 这里不直接更新，由ETL系统处理
        
        logger.debug("Updated instance {} of type {} in hybrid storage (Neo4j summary only)", id, objectType);
    }

    @Override
    public void deleteInstance(String objectType, String id) throws IOException {
        // 1. 从Neo4j删除
        neo4jStorage.deleteInstance(objectType, id);
        
        // 2. 关系型数据库的删除应该通过ETL或直接SQL删除
        // 这里不直接删除，由ETL系统处理
        
        logger.debug("Deleted instance {} of type {} from hybrid storage (Neo4j only)", id, objectType);
    }

    /**
     * 查询实例存储（同步数据）
     * 严格查询界限：只查询同步表和Neo4j数据，不查询原始表
     */
    @Override
    public InstanceStorage.ListResult listInstances(String objectType, int offset, int limit) throws IOException {
        logger.info("[HybridInstanceStorage] ========== INSTANCE STORAGE QUERY (同步数据查询) ==========");
        logger.info("[HybridInstanceStorage] Query mode: INSTANCE_STORAGE (同步表和Neo4j查询)");
        logger.info("[HybridInstanceStorage] Storage mode: HYBRID (reading from relational DB and Neo4j, NOT from file storage)");
        logger.info("[HybridInstanceStorage] listInstances called for objectType: {}, offset: {}, limit: {}", 
            objectType, offset, limit);
        logger.info("[HybridInstanceStorage] Data source: SYNC TABLE (同步表) + NEO4J (图数据库)，不查询原始表，不从文件读取");
        
        // 系统对象类型（table, database, mapping等）直接使用 Neo4j，避免递归调用
        if (isSystemObjectType(objectType)) {
            logger.info("[HybridInstanceStorage] System object type {} detected, using Neo4j directly to avoid recursion", objectType);
            InstanceStorage.ListResult result = neo4jStorage.listInstances(objectType, offset, limit);
            logger.info("[HybridInstanceStorage] ========== INSTANCE STORAGE QUERY END ==========");
            return result;
        }
        
        // 严格查询界限：
        // 1. 优先查询同步表（表名 = 模型名，在默认数据库中）
        // 2. 不查询原始表（原始表查询应该通过 MappedDataService.queryMappedInstances() 进行）
        // 3. 如果同步表查询失败，可以查询Neo4j（但需要明确界限）
        boolean hasMapping = hasRelationalMapping(objectType);
        logger.info("[HybridInstanceStorage] hasRelationalMapping({}) = {}", objectType, hasMapping);
        
        // 尝试从关系型数据库查询同步表
        // 严格查询界限：只查询同步表，不查询原始表
        InstanceStorage.ListResult syncTableResult = null;
        boolean syncTableQuerySucceeded = false;
        
        try {
            logger.info("[HybridInstanceStorage] Attempting to query from RelationalInstanceStorage (SYNC TABLE only, NOT querying ORIGINAL TABLE)");
            syncTableResult = relationalStorage.listInstances(objectType, offset, limit);
            syncTableQuerySucceeded = true;
            logger.info("[HybridInstanceStorage] Successfully queried from RelationalInstanceStorage, returned {} instances (total: {})", 
                syncTableResult.getItems().size(), syncTableResult.getTotal());
            
            // 详细分析返回的数据
            if (syncTableResult.getItems().isEmpty()) {
                logger.info("[HybridInstanceStorage] DATA SOURCE ANALYSIS: RelationalInstanceStorage returned EMPTY result for objectType={} - sync table exists but has no data, will try Neo4j", objectType);
            } else {
                logger.info("[HybridInstanceStorage] DATA SOURCE ANALYSIS: RelationalInstanceStorage returned {} instances for objectType={} - data from SYNC TABLE, first instance id={}", 
                    syncTableResult.getItems().size(), objectType, 
                    syncTableResult.getItems().isEmpty() ? "N/A" : syncTableResult.getItems().get(0).get("id"));
                // 如果同步表有数据，直接返回，不查询Neo4j
                logger.info("[HybridInstanceStorage] ========== INSTANCE STORAGE QUERY END ==========");
                return syncTableResult;
            }
        } catch (IOException e) {
            // 实例存储查询失败（同步表不存在或其他错误）
            // 严格查询界限：不查询原始表，可以查询Neo4j作为备选
            logger.info("[HybridInstanceStorage] Failed to query SYNC TABLE for type {} (sync table may not exist): {}", 
                objectType, e.getMessage());
            logger.info("[HybridInstanceStorage] DATA SOURCE ANALYSIS: SYNC TABLE query failed for objectType={}, trying Neo4j as fallback (NOT querying ORIGINAL TABLE)", objectType);
        }
        
        // 如果同步表查询失败或返回空结果，尝试查询Neo4j
        // 严格查询界限：只查询Neo4j，不查询原始表
        if (!syncTableQuerySucceeded || (syncTableResult != null && syncTableResult.getItems().isEmpty())) {
            try {
                logger.info("[HybridInstanceStorage] Attempting to query from Neo4j (SYNC TABLE query failed or returned empty)");
                InstanceStorage.ListResult neo4jResult = neo4jStorage.listInstances(objectType, offset, limit);
                logger.info("[HybridInstanceStorage] Successfully queried from Neo4j, returned {} instances (total: {})", 
                    neo4jResult.getItems().size(), neo4jResult.getTotal());
                
                if (neo4jResult.getItems().isEmpty()) {
                    logger.info("[HybridInstanceStorage] DATA SOURCE ANALYSIS: Both SYNC TABLE and NEO4J returned EMPTY results for objectType={}", objectType);
                } else {
                    logger.info("[HybridInstanceStorage] DATA SOURCE ANALYSIS: Data from NEO4J for objectType={} (SYNC TABLE had no data)", objectType);
                }
                logger.info("[HybridInstanceStorage] ========== INSTANCE STORAGE QUERY END ==========");
                return neo4jResult;
            } catch (IOException neo4jEx) {
                logger.info("[HybridInstanceStorage] Neo4j query also failed for objectType={}: {}", objectType, neo4jEx.getMessage());
                logger.info("[HybridInstanceStorage] DATA SOURCE ANALYSIS: Both SYNC TABLE and NEO4J queries failed/empty for objectType={}, returning EMPTY result (NOT querying ORIGINAL TABLE)", objectType);
                logger.info("[HybridInstanceStorage] ========== INSTANCE STORAGE QUERY END ==========");
                return new InstanceStorage.ListResult(new ArrayList<>(), 0);
            }
        }
        
        // 理论上不应该到达这里，但为了安全起见
        logger.warn("[HybridInstanceStorage] Unexpected code path reached, returning empty result");
        return new InstanceStorage.ListResult(new ArrayList<>(), 0);
    }

    @Override
    public List<Map<String, Object>> searchInstances(String objectType, Map<String, Object> filters) throws IOException {
        logger.info("[HybridInstanceStorage] searchInstances called for objectType: {}, filters: {}", objectType, filters);
        
        // 系统对象类型（table, database, mapping等）直接使用 Neo4j，避免递归调用
        if (isSystemObjectType(objectType)) {
            logger.info("[HybridInstanceStorage] System object type {} detected, using Neo4j directly to avoid recursion", objectType);
            return neo4jStorage.searchInstances(objectType, filters);
        }
        
        // 优先从关系型数据库查询（包括同步表）
        // 即使没有配置数据源映射，也尝试查询默认数据库中的同步表
        boolean hasMapping = hasRelationalMapping(objectType);
        logger.info("[HybridInstanceStorage] hasRelationalMapping({}) = {}", objectType, hasMapping);
        
        // 尝试从关系型数据库查询（包括同步表）
        // 实例存储查询只查询同步表，如果同步表不存在或查询失败，返回空结果，不回退到 Neo4j
        try {
            logger.info("[HybridInstanceStorage] Attempting to search from RelationalInstanceStorage (sync table only, no mapping table, no Neo4j fallback)");
            List<Map<String, Object>> results = relationalStorage.searchInstances(objectType, filters);
            logger.info("[HybridInstanceStorage] Successfully searched from RelationalInstanceStorage, returned {} instances", 
                results.size());
            return results;
        } catch (IOException e) {
            // 实例存储查询失败（同步表不存在或其他错误），直接返回空结果，不回退到 Neo4j
            logger.info("[HybridInstanceStorage] Failed to search sync table for type {} (sync table may not exist): {}, returning empty result (no Neo4j fallback)", 
                objectType, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public Map<String, Map<String, Object>> getInstancesBatch(String objectType, List<String> ids) throws IOException {
        logger.info("[HybridInstanceStorage] getInstancesBatch called for objectType: {}, ids count: {}", objectType, ids.size());
        
        // 系统对象类型（table, database, mapping等）直接使用 Neo4j，避免递归调用
        if (isSystemObjectType(objectType)) {
            logger.info("[HybridInstanceStorage] System object type {} detected, using Neo4j directly to avoid recursion", objectType);
            return neo4jStorage.getInstancesBatch(objectType, ids);
        }
        
        // 优先从关系型数据库查询（包括同步表）
        // 即使没有配置数据源映射，也尝试查询默认数据库中的同步表
        boolean hasMapping = hasRelationalMapping(objectType);
        logger.info("[HybridInstanceStorage] hasRelationalMapping({}) = {}", objectType, hasMapping);
        
        // 尝试从关系型数据库查询（包括同步表）
        // 实例存储查询只查询同步表，如果同步表不存在或查询失败，返回空结果，不回退到 Neo4j
        try {
            logger.info("[HybridInstanceStorage] Attempting to get batch from RelationalInstanceStorage (sync table only, no mapping table, no Neo4j fallback)");
            Map<String, Map<String, Object>> results = relationalStorage.getInstancesBatch(objectType, ids);
            logger.info("[HybridInstanceStorage] Successfully got batch from RelationalInstanceStorage, returned {} instances", 
                results.size());
            return results;
        } catch (IOException e) {
            // 实例存储查询失败（同步表不存在或其他错误），直接返回空结果，不回退到 Neo4j
            logger.info("[HybridInstanceStorage] Failed to get batch from sync table for type {} (sync table may not exist): {}, returning empty result (no Neo4j fallback)", 
                objectType, e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public Map<String, Map<String, Object>> getInstancesBatchMultiType(Map<String, List<String>> typeIdMap) throws IOException {
        // 优先从关系型数据库查询
        Map<String, Map<String, Object>> result = new HashMap<>();
        
        // 按对象类型分组处理
        for (Map.Entry<String, List<String>> entry : typeIdMap.entrySet()) {
            String objectType = entry.getKey();
            List<String> ids = entry.getValue();
            
            // 系统对象类型（table, database, mapping等）直接使用 Neo4j，避免递归调用
            if (isSystemObjectType(objectType)) {
                logger.info("[HybridInstanceStorage] System object type {} detected, using Neo4j directly to avoid recursion", objectType);
                Map<String, Map<String, Object>> instances = neo4jStorage.getInstancesBatch(objectType, ids);
                for (Map.Entry<String, Map<String, Object>> instanceEntry : instances.entrySet()) {
                    String key = objectType + ":" + instanceEntry.getKey();
                    result.put(key, instanceEntry.getValue());
                }
                continue;
            }
            
            if (hasRelationalMapping(objectType)) {
                try {
                    Map<String, Map<String, Object>> instances = relationalStorage.getInstancesBatch(objectType, ids);
                    for (Map.Entry<String, Map<String, Object>> instanceEntry : instances.entrySet()) {
                        String key = objectType + ":" + instanceEntry.getKey();
                        result.put(key, instanceEntry.getValue());
                    }
                    continue;
                } catch (IOException e) {
                    logger.warn("Failed to get batch from relational DB for type {}, falling back to Neo4j: {}", 
                        objectType, e.getMessage());
                }
            }
            
            // 回退到Neo4j
            Map<String, Map<String, Object>> instances = neo4jStorage.getInstancesBatch(objectType, ids);
            for (Map.Entry<String, Map<String, Object>> instanceEntry : instances.entrySet()) {
                String key = objectType + ":" + instanceEntry.getKey();
                result.put(key, instanceEntry.getValue());
            }
        }
        
        return result;
    }

    /**
     * 获取实例摘要（只包含关键字段，用于图展示）
     */
    public Map<String, Object> getInstanceSummary(String objectType, String id) throws IOException {
        return neo4jStorage.getInstance(objectType, id);
    }
}

