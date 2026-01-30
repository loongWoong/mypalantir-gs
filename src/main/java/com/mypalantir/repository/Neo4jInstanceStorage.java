package com.mypalantir.repository;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;

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

    @PostConstruct
    public void init() {
        logger.info("========== Neo4jInstanceStorage 初始化 ==========");
        if (neo4jDriver != null) {
            logger.info("Neo4j Driver 已注入，连接状态: 可用");
            try {
                // 尝试获取连接信息
                neo4jDriver.verifyConnectivity();
                logger.info("Neo4j 连接验证成功");
            } catch (Exception e) {
                logger.warn("Neo4j 连接验证失败: {}", e.getMessage());
            }
        } else {
            logger.warn("Neo4j Driver 未注入，Neo4jInstanceStorage 将无法使用");
            logger.warn("请检查 Neo4j 配置是否正确");
        }
        logger.info("===============================================");
    }

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
                params.put(key, convertToNeo4jValue(entry.getValue()));
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
            // 如果存在，抛出异常
            throw new IOException("instance with id '" + id + "' already exists");
        } catch (IOException e) {
            // 如果异常消息是 "instance not found"，说明不存在，继续创建
            if (!"instance not found".equals(e.getMessage())) {
                // 其他异常重新抛出
                throw e;
            }
            // instance not found，继续创建
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
                params.put(key, convertToNeo4jValue(entry.getValue()));
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

        // 验证连接（带重试机制）
        verifyAndRetryConnection();

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
                instance.put(key, convertValue(value));
            });
            
            return instance;
        } catch (org.neo4j.driver.exceptions.NoSuchRecordException e) {
            throw new IOException("instance not found", e);
        } catch (IOException e) {
            // 如果是 "instance not found"，直接抛出，不记录错误日志
            if ("instance not found".equals(e.getMessage())) {
                throw e;
            }
            logger.error("Failed to get instance from Neo4j: {}", e.getMessage(), e);
            throw e;
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

        // 验证连接（带重试机制）
        verifyAndRetryConnection();

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
                    params.put(key, convertToNeo4jValue(entry.getValue()));
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

        // 验证连接（带重试机制）
        verifyAndRetryConnection();

        try (Session session = neo4jDriver.session()) {
            String label = normalizeLabel(objectType);
            
            // 先检查节点是否存在
            String checkCypher = "MATCH (n:" + label + " {id: $id}) RETURN n.id AS id";
            var checkResult = session.run(checkCypher, Values.parameters("id", id));
            if (!checkResult.hasNext()) {
                throw new IOException("instance not found");
            }
            
            // 删除节点及其所有关系
            String deleteCypher = "MATCH (n:" + label + " {id: $id}) DETACH DELETE n";
            var deleteResult = session.run(deleteCypher, Values.parameters("id", id));
            
            // 检查删除是否成功
            long nodesDeleted = deleteResult.consume().counters().nodesDeleted();
            if (nodesDeleted == 0) {
                throw new IOException("instance not found");
            }
            
            logger.debug("Deleted instance {} of type {} from Neo4j", id, objectType);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to delete instance from Neo4j: {}", e.getMessage(), e);
            throw new IOException("Failed to delete instance: " + e.getMessage(), e);
        }
    }

    @Override
    public InstanceStorage.ListResult listInstances(String objectType, int offset, int limit) throws IOException {
        logger.info("[Neo4jInstanceStorage] listInstances called: objectType={}, offset={}, limit={}, driverNull={}", 
            objectType, offset, limit, neo4jDriver == null);
        
        if (neo4jDriver == null) {
            logger.error("[Neo4jInstanceStorage] Neo4j driver is not initialized - cannot query Neo4j");
            throw new IOException("Neo4j driver is not initialized");
        }

        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log", true);
            fw.write(java.util.Map.of("id","log_" + System.currentTimeMillis(),"timestamp",System.currentTimeMillis(),"location","Neo4jInstanceStorage.java:233","message","listInstances entry","data",java.util.Map.of("objectType",objectType,"offset",offset,"limit",limit,"driverNull",neo4jDriver == null),"sessionId","debug-session","runId","run1","hypothesisId","A").toString() + "\n");
            fw.close();
        } catch (Exception ignored) {}
        // #endregion

        try {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log", true);
                fw.write(java.util.Map.of("id","log_" + System.currentTimeMillis(),"timestamp",System.currentTimeMillis(),"location","Neo4jInstanceStorage.java:240","message","Before session creation","data",java.util.Map.of("driverClass",neo4jDriver.getClass().getName()),"sessionId","debug-session","runId","run1","hypothesisId","B").toString() + "\n");
                fw.close();
            } catch (Exception ignored) {}
            // #endregion

            // 验证连接（带重试机制）
            // #region agent log
            try {
                verifyAndRetryConnection();
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log", true);
                fw.write(java.util.Map.of("id","log_" + System.currentTimeMillis(),"timestamp",System.currentTimeMillis(),"location","Neo4jInstanceStorage.java:245","message","Connectivity verified","data",java.util.Map.of(),"sessionId","debug-session","runId","run1","hypothesisId","C").toString() + "\n");
                fw.close();
            } catch (Exception e) {
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log", true);
                    fw.write(java.util.Map.of("id","log_" + System.currentTimeMillis(),"timestamp",System.currentTimeMillis(),"location","Neo4jInstanceStorage.java:250","message","Connectivity verification failed","data",java.util.Map.of("error",e.getClass().getName(),"message",e.getMessage()),"sessionId","debug-session","runId","run1","hypothesisId","C").toString() + "\n");
                    fw.close();
                } catch (Exception ignored) {}
                throw e; // 重新抛出异常
            }
            // #endregion

            try (Session session = neo4jDriver.session()) {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log", true);
                    fw.write(java.util.Map.of("id","log_" + System.currentTimeMillis(),"timestamp",System.currentTimeMillis(),"location","Neo4jInstanceStorage.java:254","message","Session created","data",java.util.Map.of("sessionClass",session.getClass().getName()),"sessionId","debug-session","runId","run1","hypothesisId","D").toString() + "\n");
                    fw.close();
                } catch (Exception ignored) {}
                // #endregion

                String label = normalizeLabel(objectType);
                
                // 先获取总数
                String countCypher = "MATCH (n:" + label + ") RETURN count(n) AS total";
                
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log", true);
                    fw.write(java.util.Map.of("id","log_" + System.currentTimeMillis(),"timestamp",System.currentTimeMillis(),"location","Neo4jInstanceStorage.java:262","message","Before count query","data",java.util.Map.of("cypher",countCypher),"sessionId","debug-session","runId","run1","hypothesisId","E").toString() + "\n");
                    fw.close();
                } catch (Exception ignored) {}
                // #endregion

                long total = session.run(countCypher).single().get("total").asLong();
                logger.info("[Neo4jInstanceStorage] Neo4j count query result: objectType={}, total={}", objectType, total);
                
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log", true);
                    fw.write(java.util.Map.of("id","log_" + System.currentTimeMillis(),"timestamp",System.currentTimeMillis(),"location","Neo4jInstanceStorage.java:268","message","Count query succeeded","data",java.util.Map.of("total",total),"sessionId","debug-session","runId","run1","hypothesisId","E").toString() + "\n");
                    fw.close();
                } catch (Exception ignored) {}
                // #endregion

                // 获取分页数据
                String cypher = "MATCH (n:" + label + ") RETURN n ORDER BY n.created_at DESC SKIP $offset LIMIT $limit";
                var result = session.run(cypher, Values.parameters("offset", offset, "limit", limit));
                
                List<Map<String, Object>> instances = new ArrayList<>();
                while (result.hasNext()) {
                    var record = result.next();
                    var node = record.get("n").asNode();
                    Map<String, Object> instance = new HashMap<>();
                    node.asMap().forEach((key, value) -> {
                        instance.put(key, convertValue(value));
                    });
                    instances.add(instance);
                }
                
                logger.info("[Neo4jInstanceStorage] Neo4j query result: objectType={}, instancesRetrieved={}, total={}", 
                    objectType, instances.size(), total);
                
                // 详细分析返回的数据
                if (instances.isEmpty()) {
                    logger.info("[Neo4jInstanceStorage] DATA SOURCE ANALYSIS: Neo4j returned EMPTY result for objectType={} - no data in Neo4j", objectType);
                } else {
                    logger.warn("[Neo4jInstanceStorage] DATA SOURCE ANALYSIS: Neo4j returned {} instances for objectType={} - DATA EXISTS in Neo4j, first instance id={}", 
                        instances.size(), objectType, instances.isEmpty() ? "N/A" : instances.get(0).get("id"));
                }
                
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log", true);
                    fw.write(java.util.Map.of("id","log_" + System.currentTimeMillis(),"timestamp",System.currentTimeMillis(),"location","Neo4jInstanceStorage.java:285","message","listInstances success","data",java.util.Map.of("instanceCount",instances.size(),"total",total),"sessionId","debug-session","runId","run1","hypothesisId","F").toString() + "\n");
                    fw.close();
                } catch (Exception ignored) {}
                // #endregion

                return new InstanceStorage.ListResult(instances, total);
            }
        } catch (Exception e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log", true);
                fw.write(java.util.Map.of("id","log_" + System.currentTimeMillis(),"timestamp",System.currentTimeMillis(),"location","Neo4jInstanceStorage.java:293","message","listInstances exception","data",java.util.Map.of("errorClass",e.getClass().getName(),"message",e.getMessage(),"cause",e.getCause() != null ? e.getCause().getClass().getName() : "null"),"sessionId","debug-session","runId","run1","hypothesisId","G").toString() + "\n");
                fw.close();
            } catch (Exception ignored) {}
            // #endregion

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
                    instance.put(key, convertValue(value));
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

    /**
     * 规范化标签名称（Neo4j 标签不能包含特殊字符）
     */
    private String normalizeLabel(String objectType) {
        // 将对象类型名称转换为有效的 Neo4j 标签
        // 移除特殊字符，使用驼峰命名
        return objectType.replaceAll("[^a-zA-Z0-9]", "");
    }

    /**
     * 将 Java 对象转换为 Neo4j 支持的类型
     * Neo4j 只支持基本类型（String、Number、Boolean）或这些类型的数组
     * 对于 Map 和 List 等复杂对象，将其序列化为 JSON 字符串
     */
    private Object convertToNeo4jValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // 基本类型直接返回
        if (value instanceof String || 
            value instanceof Number || 
            value instanceof Boolean ||
            value instanceof Character) {
            return value;
        }
        
        // 处理数组
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            List<Object> list = new ArrayList<>();
            for (Object item : array) {
                list.add(convertToNeo4jValue(item));
            }
            return list.toArray();
        }
        
        // 处理 List
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(convertToNeo4jValue(item));
            }
            return result;
        }
        
        // 处理 Map - 序列化为 JSON 字符串
        if (value instanceof Map) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.writeValueAsString(value);
            } catch (Exception e) {
                logger.warn("Failed to serialize Map to JSON, using toString(): {}", e.getMessage());
                return value.toString();
            }
        }
        
        // 其他复杂对象序列化为 JSON 字符串
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            logger.warn("Failed to serialize object to JSON, using toString(): {}", e.getMessage());
            return value.toString();
        }
    }

    /**
     * 转换 Neo4j Value 对象或 Java 对象为 Java 对象
     * 如果值是 JSON 字符串（之前序列化的 Map/List），尝试反序列化
     */
    private Object convertValue(Object value) {
        // 如果已经是 Java 对象，需要递归转换
        if (!(value instanceof Value)) {
            if (value == null) {
                return null;
            }
            
            // 如果是字符串，尝试解析为 JSON（可能是之前序列化的 Map/List）
            if (value instanceof String) {
                String str = (String) value;
                // 检查是否是 JSON 格式（以 { 或 [ 开头）
                if ((str.startsWith("{") && str.endsWith("}")) || 
                    (str.startsWith("[") && str.endsWith("]"))) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        Object parsed = mapper.readValue(str, Object.class);
                        // 递归转换解析后的对象
                        return convertValue(parsed);
                    } catch (Exception e) {
                        // 解析失败，返回原始字符串
                        return value;
                    }
                }
                return value;
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
