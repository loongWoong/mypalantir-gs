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
 * Neo4j 实例存储实现
 */
public class Neo4jInstanceStorage implements IInstanceStorage {
    private static final Logger logger = LoggerFactory.getLogger(Neo4jInstanceStorage.class);

    @Autowired(required = false)
    private Driver neo4jDriver;

    @Override
    public String createInstance(String objectType, Map<String, Object> data) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> instance = new HashMap<>();
        instance.put("id", id);
        instance.put("created_at", now);
        instance.put("updated_at", now);
        instance.putAll(data);

        try (Session session = neo4jDriver.session()) {
            // 创建节点，标签为对象类型名称
            String label = normalizeLabel(objectType);
            
            // 构建参数化查询
            StringBuilder cypher = new StringBuilder("CREATE (n:").append(label).append(" {");
            List<String> propKeys = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : instance.entrySet()) {
                String key = entry.getKey();
                propKeys.add(key + ": $" + key);
                params.put(key, entry.getValue());
            }
            cypher.append(String.join(", ", propKeys));
            cypher.append("}) RETURN n.id AS id");

            session.run(cypher.toString(), params).single();
            
            logger.debug("Created instance {} of type {} in Neo4j", id, objectType);
            return id;
        } catch (Exception e) {
            logger.error("Failed to create instance in Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to create instance: " + e.getMessage(), e);
        }
    }

    @Override
    public String createInstanceWithId(String objectType, String id, Map<String, Object> data) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        // 检查是否已存在
        try {
            getInstance(objectType, id);
            throw new IOException("instance with id '" + id + "' already exists");
        } catch (IOException e) {
            // 不存在，继续创建
        }

        String now = Instant.now().toString();

        Map<String, Object> instance = new HashMap<>();
        instance.put("id", id);
        instance.put("created_at", now);
        instance.put("updated_at", now);
        instance.putAll(data);

        try (Session session = neo4jDriver.session()) {
            String label = normalizeLabel(objectType);
            
            // 构建参数化查询
            StringBuilder cypher = new StringBuilder("CREATE (n:").append(label).append(" {");
            List<String> propKeys = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : instance.entrySet()) {
                String key = entry.getKey();
                propKeys.add(key + ": $" + key);
                params.put(key, entry.getValue());
            }
            cypher.append(String.join(", ", propKeys));
            cypher.append("}) RETURN n.id AS id");

            session.run(cypher.toString(), params).single();
            
            logger.debug("Created instance {} of type {} in Neo4j with specified ID", id, objectType);
            return id;
        } catch (Exception e) {
            logger.error("Failed to create instance in Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to create instance: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getInstance(String objectType, String id) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        try (Session session = neo4jDriver.session()) {
            String label = normalizeLabel(objectType);
            String cypher = "MATCH (n:" + label + " {id: $id}) RETURN n";
            
            var result = session.run(cypher, Values.parameters("id", id));
            if (!result.hasNext()) {
                throw new IOException("instance not found");
            }

            var node = result.single().get("n").asNode();
            Map<String, Object> instance = new HashMap<>();
            node.asMap().forEach((key, value) -> {
                instance.put(key, convertValue((Value) value));
            });
            
            return instance;
        } catch (org.neo4j.driver.exceptions.NoSuchRecordException e) {
            throw new IOException("instance not found", e);
        } catch (Exception e) {
            logger.error("Failed to get instance from Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to get instance: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateInstance(String objectType, String id, Map<String, Object> data) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        try (Session session = neo4jDriver.session()) {
            String label = normalizeLabel(objectType);
            
            // 构建 SET 子句
            List<String> setClauses = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            params.put("id", id);
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                if (!"id".equals(key)) {
                    setClauses.add("n." + key + " = $" + key);
                    params.put(key, entry.getValue());
                }
            }
            setClauses.add("n.updated_at = $updated_at");
            params.put("updated_at", Instant.now().toString());
            
            String cypher = "MATCH (n:" + label + " {id: $id}) SET " + String.join(", ", setClauses);
            
            var result = session.run(cypher, params);
            if (result.consume().counters().nodesCreated() == 0 && 
                result.consume().counters().propertiesSet() == 0) {
                throw new IOException("instance not found");
            }
            
            logger.debug("Updated instance {} of type {} in Neo4j", id, objectType);
        } catch (Exception e) {
            logger.error("Failed to update instance in Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to update instance: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteInstance(String objectType, String id) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        try (Session session = neo4jDriver.session()) {
            String label = normalizeLabel(objectType);
            String cypher = "MATCH (n:" + label + " {id: $id}) DETACH DELETE n RETURN n.id AS id";
            
            var result = session.run(cypher, Values.parameters("id", id));
            if (!result.hasNext()) {
                throw new IOException("instance not found");
            }
            
            logger.debug("Deleted instance {} of type {} from Neo4j", id, objectType);
        } catch (Exception e) {
            logger.error("Failed to delete instance from Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to delete instance: " + e.getMessage(), e);
        }
    }

    @Override
    public InstanceStorage.ListResult listInstances(String objectType, int offset, int limit) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        try (Session session = neo4jDriver.session()) {
            String label = normalizeLabel(objectType);
            
            // 先获取总数
            String countCypher = "MATCH (n:" + label + ") RETURN count(n) AS total";
            long total = session.run(countCypher).single().get("total").asLong();
            
            // 获取分页数据
            String cypher = "MATCH (n:" + label + ") RETURN n ORDER BY n.created_at DESC SKIP $offset LIMIT $limit";
            var result = session.run(cypher, Values.parameters("offset", offset, "limit", limit));
            
            List<Map<String, Object>> instances = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                var node = record.get("n").asNode();
                Map<String, Object> instance = new HashMap<>();
                node.asMap().forEach((key, value) -> {
                    instance.put(key, convertValue((Value) value));
                });
                instances.add(instance);
            }
            
            return new InstanceStorage.ListResult(instances, total);
        } catch (Exception e) {
            logger.error("Failed to list instances from Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to list instances: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> searchInstances(String objectType, Map<String, Object> filters) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        if (filters == null || filters.isEmpty()) {
            InstanceStorage.ListResult result = listInstances(objectType, 0, 10000);
            return result.getItems();
        }

        try (Session session = neo4jDriver.session()) {
            String label = normalizeLabel(objectType);
            
            // 构建 WHERE 子句
            List<String> conditions = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            
            for (Map.Entry<String, Object> filter : filters.entrySet()) {
                String key = filter.getKey();
                conditions.add("n." + key + " = $" + key);
                params.put(key, filter.getValue());
            }
            
            String cypher = "MATCH (n:" + label + ") WHERE " + String.join(" AND ", conditions) + " RETURN n";
            var result = session.run(cypher, params);
            
            List<Map<String, Object>> instances = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                var node = record.get("n").asNode();
                Map<String, Object> instance = new HashMap<>();
                node.asMap().forEach((key, value) -> {
                    instance.put(key, convertValue((Value) value));
                });
                instances.add(instance);
            }
            
            return instances;
        } catch (Exception e) {
            logger.error("Failed to search instances in Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to search instances: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Map<String, Object>> getInstancesBatch(String objectType, List<String> ids) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        Map<String, Map<String, Object>> result = new HashMap<>();
        
        for (String id : ids) {
            try {
                Map<String, Object> instance = getInstance(objectType, id);
                result.put(id, instance);
            } catch (IOException e) {
                result.put(id, null);
            }
        }
        
        return result;
    }

    @Override
    public Map<String, Map<String, Object>> getInstancesBatchMultiType(Map<String, List<String>> typeIdMap) throws IOException {
        if (neo4jDriver == null) {
            throw new IOException("Neo4j driver is not initialized");
        }

        Map<String, Map<String, Object>> result = new HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : typeIdMap.entrySet()) {
            String objectType = entry.getKey();
            List<String> ids = entry.getValue();
            
            Map<String, Map<String, Object>> instances = getInstancesBatch(objectType, ids);
            for (Map.Entry<String, Map<String, Object>> instanceEntry : instances.entrySet()) {
                String key = objectType + ":" + instanceEntry.getKey();
                result.put(key, instanceEntry.getValue());
            }
        }
        
        return result;
    }

    /**
     * 规范化标签名称（Neo4j 标签不能包含特殊字符）
     */
    private String normalizeLabel(String objectType) {
        // 将对象类型名称转换为有效的 Neo4j 标签
        // 移除特殊字符，使用驼峰命名
        return objectType.replaceAll("[^a-zA-Z0-9]", "");
    }

    /**
     * 转换 Neo4j Value 对象为 Java 对象
     */
    private Object convertValue(Value value) {
        if (value.isNull()) {
            return null;
        }
        switch (value.type().name()) {
            case "NULL":
                return null;
            case "BOOLEAN":
                return value.asBoolean();
            case "INTEGER":
                return value.asLong();
            case "FLOAT":
                return value.asDouble();
            case "STRING":
                return value.asString();
            case "LIST":
                return value.asList(this::convertValue);
            case "MAP":
                Map<String, Object> map = new HashMap<>();
                value.asMap().forEach((k, v) -> map.put(k, convertValue((Value) v)));
                return map;
            default:
                return value.asObject();
        }
    }
}
