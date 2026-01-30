package com.mypalantir.repository;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j 关系存储实现
 */
public class Neo4jLinkStorage implements ILinkStorage {
    private static final Logger logger = LoggerFactory.getLogger(Neo4jLinkStorage.class);

    @Autowired(required = false)
    private Driver neo4jDriver;

    @Autowired(required = false)
    private Loader loader;

    @Autowired(required = false)
    private IInstanceStorage instanceStorage; // 用于查询节点数据（在hybrid模式下会查询同步表）

    @Autowired(required = false)
    private Neo4jInstanceStorage neo4jInstanceStorage; // 用于创建节点到Neo4j

    @Autowired(required = false)
    private Environment environment; // 用于读取Neo4j字段配置

    /**
     * 验证并重试连接（如果连接失败）
     */
    private void verifyAndRetryConnection() throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }
        
        int maxRetries = 3;
        int retryDelayMs = 1000;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                neo4jDriver.verifyConnectivity();
                return; // 连接成功
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    logger.error("Failed to verify Neo4j connectivity after {} retries: {}", maxRetries, e.getMessage());
                    throw new IOException("Connection to Neo4j failed: " + e.getMessage(), e);
                }
                logger.warn("Neo4j connectivity check failed, retrying ({}/{})...", i + 1, maxRetries);
                try {
                    Thread.sleep(retryDelayMs * (i + 1)); // 递增延迟
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Connection retry interrupted", ie);
                }
            }
        }
    }

    @Override
    public String createLink(String linkType, String sourceID, String targetID, Map<String, Object> properties) throws IOException {
        logger.debug("[Neo4jLinkStorage] Creating link: linkType={}, sourceID={}, targetID={}", linkType, sourceID, targetID);
        logger.debug("[Neo4jLinkStorage] Storage mode: NEO4J (links stored in Neo4j, NOT in file storage)");
        
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        // 验证连接（带重试机制）
        verifyAndRetryConnection();

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        try (Session session = neo4jDriver.session()) {
            // 优先使用 linkType 定义中的类型，如果 loader 不可用则回退到查询节点标签
            String sourceType;
            String targetType;
            String sourceLabel;
            String targetLabel;
            
            if (loader != null) {
                try {
                    LinkType linkTypeDef = loader.getLinkType(linkType);
                    sourceType = linkTypeDef.getSourceType();
                    targetType = linkTypeDef.getTargetType();
                    sourceLabel = normalizeLabel(sourceType);
                    targetLabel = normalizeLabel(targetType);
                    logger.debug("Using linkType definition: source={}, target={}", sourceLabel, targetLabel);
                } catch (Loader.NotFoundException e) {
                    // 如果找不到 linkType 定义，回退到查询节点标签
                    logger.warn("LinkType '{}' not found, falling back to querying node labels", linkType);
                    sourceLabel = getNodeLabel(session, sourceID);
                    targetLabel = getNodeLabel(session, targetID);
                    sourceType = null;
                    targetType = null;
                }
            } else {
                // 如果 loader 不可用，回退到查询节点标签
                logger.warn("Loader not available, falling back to querying node labels");
                sourceLabel = getNodeLabel(session, sourceID);
                targetLabel = getNodeLabel(session, targetID);
                sourceType = null;
                targetType = null;
            }
            
            // 确保源节点和目标节点在Neo4j中存在
            // 如果节点不存在，从同步表查询并创建到Neo4j
            ensureNodeExists(session, sourceID, sourceType, sourceLabel);
            ensureNodeExists(session, targetID, targetType, targetLabel);
            
            String relType = normalizeRelType(linkType);
            
            // 构建关系属性
            Map<String, Object> relProps = new HashMap<>();
            relProps.put("id", id);
            relProps.put("created_at", now);
            relProps.put("updated_at", now);
            if (properties != null) {
                relProps.putAll(properties);
            }
            
            // 构建 Cypher 查询
            StringBuilder cypher = new StringBuilder();
            cypher.append("MATCH (source:").append(sourceLabel).append(" {id: $sourceId}) ");
            cypher.append("MATCH (target:").append(targetLabel).append(" {id: $targetId}) ");
            cypher.append("CREATE (source)-[r:").append(relType).append(" {");
            
            List<String> propKeys = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            params.put("sourceId", sourceID);
            params.put("targetId", targetID);
            
            for (Map.Entry<String, Object> entry : relProps.entrySet()) {
                String key = entry.getKey();
                propKeys.add(key + ": $" + key);
                params.put(key, entry.getValue());
            }
            cypher.append(String.join(", ", propKeys));
            cypher.append("}]->(target) RETURN r.id AS id");
            
            session.run(cypher.toString(), params).single();
            
            logger.debug("Created link {} of type {} in Neo4j", id, linkType);
            return id;
        } catch (Exception e) {
            logger.error("Failed to create link in Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to create link: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getLink(String linkType, String id) throws IOException {
        logger.debug("[Neo4jLinkStorage] Getting link: linkType={}, id={}", linkType, id);
        logger.debug("[Neo4jLinkStorage] Storage mode: NEO4J (reading from Neo4j, NOT from file storage)");
        
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        // 验证连接（带重试机制）
        verifyAndRetryConnection();

        try (Session session = neo4jDriver.session()) {
            String relType = normalizeRelType(linkType);
            String cypher = "MATCH ()-[r:" + relType + " {id: $id}]-() RETURN r, startNode(r).id AS source_id, endNode(r).id AS target_id";
            
            var result = session.run(cypher, Values.parameters("id", id));
            if (!result.hasNext()) {
                throw new IOException("link not found");
            }

            var record = result.single();
            var relationship = record.get("r").asRelationship();
            
            Map<String, Object> link = new HashMap<>();
            relationship.asMap().forEach((key, value) -> {
                link.put(key, convertValue((Value) value));
            });
            link.put("source_id", record.get("source_id").asString());
            link.put("target_id", record.get("target_id").asString());
            
            return link;
        } catch (org.neo4j.driver.exceptions.NoSuchRecordException e) {
            throw new IOException("link not found", e);
        } catch (Exception e) {
            logger.error("Failed to get link from Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to get link: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateLink(String linkType, String id, Map<String, Object> properties) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        try (Session session = neo4jDriver.session()) {
            String relType = normalizeRelType(linkType);
            
            // 构建 SET 子句
            List<String> setClauses = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            params.put("id", id);
            
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                if (!"id".equals(key) && !"source_id".equals(key) && !"target_id".equals(key)) {
                    setClauses.add("r." + key + " = $" + key);
                    params.put(key, entry.getValue());
                }
            }
            setClauses.add("r.updated_at = $updated_at");
            params.put("updated_at", Instant.now().toString());
            
            String cypher = "MATCH ()-[r:" + relType + " {id: $id}]-() SET " + String.join(", ", setClauses);
            
            var result = session.run(cypher, params);
            if (result.consume().counters().propertiesSet() == 0) {
                throw new IOException("link not found");
            }
            
            logger.debug("Updated link {} of type {} in Neo4j", id, linkType);
        } catch (Exception e) {
            logger.error("Failed to update link in Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to update link: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteLink(String linkType, String id) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        try (Session session = neo4jDriver.session()) {
            String relType = normalizeRelType(linkType);
            
            // 先检查关系是否存在
            String checkCypher = "MATCH ()-[r:" + relType + " {id: $id}]-() RETURN r.id AS id";
            var checkResult = session.run(checkCypher, Values.parameters("id", id));
            if (!checkResult.hasNext()) {
                throw new IOException("link not found");
            }
            
            // 删除关系
            String deleteCypher = "MATCH ()-[r:" + relType + " {id: $id}]-() DELETE r";
            var deleteResult = session.run(deleteCypher, Values.parameters("id", id));
            
            // 检查删除是否成功
            long relationshipsDeleted = deleteResult.consume().counters().relationshipsDeleted();
            if (relationshipsDeleted == 0) {
                throw new IOException("link not found");
            }
            
            logger.debug("Deleted link {} of type {} from Neo4j", id, linkType);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to delete link from Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to delete link: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getLinksBySource(String linkType, String sourceID) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        try (Session session = neo4jDriver.session()) {
            // 优先使用 linkType 定义中的类型，如果 loader 不可用则回退到查询节点标签
            String sourceLabel;
            if (loader != null) {
                try {
                    LinkType linkTypeDef = loader.getLinkType(linkType);
                    sourceLabel = normalizeLabel(linkTypeDef.getSourceType());
                    logger.debug("Using linkType definition for getLinksBySource: source={}", sourceLabel);
                } catch (Loader.NotFoundException e) {
                    logger.warn("LinkType '{}' not found, falling back to querying node label", linkType);
                    sourceLabel = getNodeLabel(session, sourceID);
                }
            } else {
                logger.warn("Loader not available, falling back to querying node label");
                sourceLabel = getNodeLabel(session, sourceID);
            }
            
            String relType = normalizeRelType(linkType);
            String cypher = "MATCH (source:" + sourceLabel + " {id: $sourceId})-[r:" + relType + "]->(target) RETURN r, target.id AS target_id";
            
            var result = session.run(cypher, Values.parameters("sourceId", sourceID));
            
            List<Map<String, Object>> links = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                var relationship = record.get("r").asRelationship();
                Map<String, Object> link = new HashMap<>();
                relationship.asMap().forEach((key, value) -> {
                    link.put(key, convertValue(value));
                });
                link.put("source_id", sourceID);
                link.put("target_id", record.get("target_id").asString());
                links.add(link);
            }
            
            return links;
        } catch (Exception e) {
            logger.error("Failed to get links by source from Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to get links by source: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getLinksByTarget(String linkType, String targetID) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        try (Session session = neo4jDriver.session()) {
            // 优先使用 linkType 定义中的类型，如果 loader 不可用则回退到查询节点标签
            String targetLabel;
            if (loader != null) {
                try {
                    LinkType linkTypeDef = loader.getLinkType(linkType);
                    targetLabel = normalizeLabel(linkTypeDef.getTargetType());
                    logger.debug("Using linkType definition for getLinksByTarget: target={}", targetLabel);
                } catch (Loader.NotFoundException e) {
                    logger.warn("LinkType '{}' not found, falling back to querying node label", linkType);
                    targetLabel = getNodeLabel(session, targetID);
                }
            } else {
                logger.warn("Loader not available, falling back to querying node label");
                targetLabel = getNodeLabel(session, targetID);
            }
            
            String relType = normalizeRelType(linkType);
            String cypher = "MATCH (source)-[r:" + relType + "]->(target:" + targetLabel + " {id: $targetId}) RETURN r, source.id AS source_id";
            
            var result = session.run(cypher, Values.parameters("targetId", targetID));
            
            List<Map<String, Object>> links = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                var relationship = record.get("r").asRelationship();
                Map<String, Object> link = new HashMap<>();
                relationship.asMap().forEach((key, value) -> {
                    link.put(key, convertValue(value));
                });
                link.put("source_id", record.get("source_id").asString());
                link.put("target_id", targetID);
                links.add(link);
            }
            
            return links;
        } catch (Exception e) {
            logger.error("Failed to get links by target from Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to get links by target: " + e.getMessage(), e);
        }
    }

    @Override
    public InstanceStorage.ListResult listLinks(String linkType, int offset, int limit) throws IOException {
        logger.debug("[Neo4jLinkStorage] Listing links: linkType={}, offset={}, limit={}", linkType, offset, limit);
        logger.debug("[Neo4jLinkStorage] Storage mode: NEO4J (reading from Neo4j, NOT from file storage)");
        
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        // 验证连接（带重试机制）
        verifyAndRetryConnection();

        try (Session session = neo4jDriver.session()) {
            String relType = normalizeRelType(linkType);
            
            // 先获取总数
            String countCypher = "MATCH ()-[r:" + relType + "]-() RETURN count(r) AS total";
            long total = session.run(countCypher).single().get("total").asLong();
            
            // 获取分页数据
            String cypher = "MATCH (source)-[r:" + relType + "]->(target) RETURN r, source.id AS source_id, target.id AS target_id ORDER BY r.created_at DESC SKIP $offset LIMIT $limit";
            var result = session.run(cypher, Values.parameters("offset", offset, "limit", limit));
            
            List<Map<String, Object>> links = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                var relationship = record.get("r").asRelationship();
                Map<String, Object> link = new HashMap<>();
                relationship.asMap().forEach((key, value) -> {
                    link.put(key, convertValue(value));
                });
                link.put("source_id", record.get("source_id").asString());
                link.put("target_id", record.get("target_id").asString());
                links.add(link);
            }
            
            return new InstanceStorage.ListResult(links, total);
        } catch (Exception e) {
            logger.error("Failed to list links from Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to list links: " + e.getMessage(), e);
        }
    }

    /**
     * 规范化关系类型名称（Neo4j 关系类型不能包含特殊字符）
     */
    private String normalizeRelType(String linkType) {
        // 将关系类型名称转换为有效的 Neo4j 关系类型
        // 移除特殊字符，使用大写
        return linkType.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
    }

    /**
     * 规范化标签名称（Neo4j 标签不能包含特殊字符）
     */
    private String normalizeLabel(String objectType) {
        return objectType.replaceAll("[^a-zA-Z0-9]", "");
    }

    /**
     * 通过节点 ID 获取节点标签（类型）
     */
    private String getNodeLabel(Session session, String nodeId) throws IOException {
        String cypher = "MATCH (n {id: $id}) RETURN labels(n) AS labels";
        var result = session.run(cypher, Values.parameters("id", nodeId));
        
        // 使用 list() 获取所有结果，避免 single() 在多条记录时报错
        var records = result.list();
        
        if (records.isEmpty()) {
            throw new IOException("Node with id '" + nodeId + "' not found");
        }
        
        // 如果有多个节点具有相同ID，取第一个
        var labels = records.get(0).get("labels").asList();
        if (labels.isEmpty()) {
            throw new IOException("Node with id '" + nodeId + "' has no label");
        }
        
        // 返回第一个标签
        return labels.get(0).toString();
    }

    /**
     * 检查节点是否在Neo4j中存在，如果不存在则从同步表查询并创建
     * 
     * @param session Neo4j会话
     * @param nodeId 节点ID
     * @param objectType 对象类型（如果已知）
     * @param label 节点标签（如果已知）
     */
    private void ensureNodeExists(Session session, String nodeId, String objectType, String label) throws IOException {
        // 检查节点是否已存在
        String checkCypher = "MATCH (n {id: $id}) RETURN n.id AS id";
        var checkResult = session.run(checkCypher, Values.parameters("id", nodeId));
        var checkRecords = checkResult.list();
        
        if (!checkRecords.isEmpty()) {
            // 节点已存在，无需创建
            logger.debug("[Neo4jLinkStorage] Node {} already exists in Neo4j", nodeId);
            return;
        }
        
        // 节点不存在，需要从同步表查询并创建
        logger.info("[Neo4jLinkStorage] Node {} not found in Neo4j, querying from sync table to create", nodeId);
        
        if (instanceStorage == null || neo4jInstanceStorage == null) {
            logger.warn("[Neo4jLinkStorage] instanceStorage or neo4jInstanceStorage not available, cannot create missing node");
            throw new IOException("Node with id '" + nodeId + "' not found in Neo4j and cannot create from sync table (instanceStorage not available)");
        }
        
        // 如果objectType未知，尝试从Neo4j查询标签（如果节点存在但查询失败，说明节点真的不存在）
        if (objectType == null || objectType.isEmpty()) {
            logger.warn("[Neo4jLinkStorage] Object type unknown for node {}, cannot query from sync table", nodeId);
            throw new IOException("Node with id '" + nodeId + "' not found in Neo4j and object type is unknown");
        }
        
        try {
            // 从instanceStorage查询节点数据（在hybrid模式下会查询同步表）
            logger.info("[Neo4jLinkStorage] Querying node {} of type {} from sync table (via instanceStorage)", nodeId, objectType);
            Map<String, Object> instanceData = instanceStorage.getInstance(objectType, nodeId);
            
            // 提取关键字段用于Neo4j存储
            // 使用配置的字段列表（与HybridInstanceStorage保持一致）
            List<String> neo4jFields = getNeo4jFields(objectType);
            Map<String, Object> summaryFields = new HashMap<>();
            
            // 提取配置的字段
            for (String field : neo4jFields) {
                if (instanceData.containsKey(field)) {
                    summaryFields.put(field, instanceData.get(field));
                }
            }
            
            // 确保id字段存在
            if (!summaryFields.containsKey("id")) {
                summaryFields.put("id", instanceData.get("id"));
            }
            
            logger.debug("[Neo4jLinkStorage] Extracted {} key fields for Neo4j storage: {}", 
                summaryFields.size(), summaryFields.keySet());
            
            // 使用Neo4jInstanceStorage创建节点到Neo4j
            logger.info("[Neo4jLinkStorage] Creating node {} of type {} in Neo4j with key fields from sync table", nodeId, objectType);
            try {
                neo4jInstanceStorage.createInstanceWithId(objectType, nodeId, summaryFields);
                logger.info("[Neo4jLinkStorage] Successfully created node {} of type {} in Neo4j from sync table", nodeId, objectType);
            } catch (IOException e) {
                // 如果节点已存在（可能在其他地方已经创建），忽略异常
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    logger.debug("[Neo4jLinkStorage] Node {} of type {} already exists in Neo4j (created elsewhere), continuing", nodeId, objectType);
                } else {
                    // 其他异常重新抛出
                    logger.error("[Neo4jLinkStorage] Failed to create node {} of type {} in Neo4j: {}", 
                        nodeId, objectType, e.getMessage());
                    throw e;
                }
            }
            
        } catch (IOException e) {
            logger.error("[Neo4jLinkStorage] Failed to query or create node {} of type {} from sync table: {}", 
                nodeId, objectType, e.getMessage());
            throw new IOException("Node with id '" + nodeId + "' not found in Neo4j and failed to create from sync table: " + e.getMessage(), e);
        }
    }

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
        if (environment != null) {
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
                    logger.debug("[Neo4jLinkStorage] Using configured Neo4j fields for {}: {}", objectType, fields);
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
                    logger.debug("[Neo4jLinkStorage] Using default Neo4j fields for {}: {}", objectType, fields);
                    return fields;
                }
            }
        }
        
        // 3. 从ObjectType定义中查找（兼容旧逻辑）
        if (loader != null) {
            try {
                com.mypalantir.meta.ObjectType objectTypeDef = loader.getObjectType(objectType);
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
                
                logger.debug("[Neo4jLinkStorage] Using ObjectType-based Neo4j fields for {}: {}", objectType, fields);
                return fields;
            } catch (com.mypalantir.meta.Loader.NotFoundException e) {
                // 4. 如果找不到对象类型定义，返回默认字段
                logger.debug("[Neo4jLinkStorage] ObjectType not found for {}, using default fields", objectType);
                return Arrays.asList("id", "name", "display_name");
            }
        }
        
        // 4. 如果找不到对象类型定义，返回默认字段
        return Arrays.asList("id", "name", "display_name");
    }

    /**
     * 转换 Neo4j Value 对象或 Java 对象为 Java 对象
     */
    private Object convertValue(Object value) {
        // 如果已经是 Java 对象，需要递归转换
        if (!(value instanceof Value)) {
            if (value == null) {
                return null;
            }
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                return list.stream().map(this::convertValue).collect(Collectors.toList());
            }
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                Map<String, Object> result = new HashMap<>();
                map.forEach((k, v) -> result.put(k.toString(), convertValue(v)));
                return result;
            }
            // 基本类型直接返回
            return value;
        }
        
        // 如果是 Neo4j Value 对象，进行转换
        Value neo4jValue = (Value) value;
        if (neo4jValue.isNull()) {
            return null;
        }
        switch (neo4jValue.type().name()) {
            case "NULL":
                return null;
            case "BOOLEAN":
                return neo4jValue.asBoolean();
            case "INTEGER":
                return neo4jValue.asLong();
            case "FLOAT":
                return neo4jValue.asDouble();
            case "STRING":
                return neo4jValue.asString();
            case "LIST":
                return neo4jValue.asList(this::convertValue);
            case "MAP":
                Map<String, Object> map = new HashMap<>();
                neo4jValue.asMap().forEach((k, v) -> map.put(k, convertValue(v)));
                return map;
            default:
                return neo4jValue.asObject();
        }
    }
}
