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
        // 1. 优先从关系型数据库查询详细数据
        if (hasRelationalMapping(objectType)) {
            try {
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
                } catch (IOException e) {
                    // Neo4j中没有，只使用关系型数据库的数据
                    logger.debug("Instance {} not found in Neo4j, using relational data only", id);
                }
                
                return instance;
            } catch (IOException e) {
                // 如果关系型数据库查询失败，回退到Neo4j（兼容旧数据）
                logger.warn("Failed to query from relational DB for instance {} of type {}, falling back to Neo4j: {}", 
                    id, objectType, e.getMessage());
            }
        }
        
        // 2. 从Neo4j查询（兼容旧数据或未配置映射的对象类型）
        return neo4jStorage.getInstance(objectType, id);
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

    @Override
    public InstanceStorage.ListResult listInstances(String objectType, int offset, int limit) throws IOException {
        // 优先从关系型数据库查询
        if (hasRelationalMapping(objectType)) {
            try {
                return relationalStorage.listInstances(objectType, offset, limit);
            } catch (IOException e) {
                logger.warn("Failed to list from relational DB for type {}, falling back to Neo4j: {}", 
                    objectType, e.getMessage());
            }
        }
        
        // 回退到Neo4j
        return neo4jStorage.listInstances(objectType, offset, limit);
    }

    @Override
    public List<Map<String, Object>> searchInstances(String objectType, Map<String, Object> filters) throws IOException {
        // 优先从关系型数据库查询
        if (hasRelationalMapping(objectType)) {
            try {
                return relationalStorage.searchInstances(objectType, filters);
            } catch (IOException e) {
                logger.warn("Failed to search from relational DB for type {}, falling back to Neo4j: {}", 
                    objectType, e.getMessage());
            }
        }
        
        // 回退到Neo4j
        return neo4jStorage.searchInstances(objectType, filters);
    }

    @Override
    public Map<String, Map<String, Object>> getInstancesBatch(String objectType, List<String> ids) throws IOException {
        // 优先从关系型数据库查询
        if (hasRelationalMapping(objectType)) {
            try {
                return relationalStorage.getInstancesBatch(objectType, ids);
            } catch (IOException e) {
                logger.warn("Failed to get batch from relational DB for type {}, falling back to Neo4j: {}", 
                    objectType, e.getMessage());
            }
        }
        
        // 回退到Neo4j
        return neo4jStorage.getInstancesBatch(objectType, ids);
    }

    @Override
    public Map<String, Map<String, Object>> getInstancesBatchMultiType(Map<String, List<String>> typeIdMap) throws IOException {
        // 优先从关系型数据库查询
        Map<String, Map<String, Object>> result = new HashMap<>();
        
        // 按对象类型分组处理
        for (Map.Entry<String, List<String>> entry : typeIdMap.entrySet()) {
            String objectType = entry.getKey();
            List<String> ids = entry.getValue();
            
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

