package com.mypalantir.repository;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        // 验证连接（带重试机制）
        verifyAndRetryConnection();

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        try (Session session = neo4jDriver.session()) {
            // 通过查询节点获取标签（类型）
            String sourceLabel = getNodeLabel(session, sourceID);
            String targetLabel = getNodeLabel(session, targetID);
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
            String sourceLabel = getNodeLabel(session, sourceID);
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
            String targetLabel = getNodeLabel(session, targetID);
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
        
        if (!result.hasNext()) {
            throw new IOException("Node with id '" + nodeId + "' not found");
        }
        
        var labels = result.single().get("labels").asList();
        if (labels.isEmpty()) {
            throw new IOException("Node with id '" + nodeId + "' has no label");
        }
        
        // 返回第一个标签
        return labels.get(0).toString();
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
