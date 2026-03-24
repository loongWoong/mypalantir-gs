package com.mypalantir.repository;

import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.graph_entities.Edge;
import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FalkorDB 实例存储实现
 * 使用 OpenCypher 语法，与 Neo4j 兼容
 */
@Component
@ConditionalOnProperty(name = "storage.graph.type", havingValue = "falkordb")
public class FalkorDBInstanceStorage implements IInstanceStorage {
    private static final Logger logger = LoggerFactory.getLogger(FalkorDBInstanceStorage.class);

    @Autowired(required = false)
    private Graph falkordbGraph;

    @PostConstruct
    public void init() {
        logger.info("========== FalkorDBInstanceStorage 初始化 ==========");
        if (falkordbGraph != null) {
            logger.info("FalkorDB Graph 已注入，连接状态: 可用");
        } else {
            logger.warn("FalkorDB Graph 未注入，FalkorDBInstanceStorage 将无法使用");
        }
        logger.info("===============================================");
    }

    @Override
    public String createInstance(String objectType, Map<String, Object> data) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        Map<String, Object> instance = new HashMap<>();
        instance.put("id", id);
        instance.put("created_at", now);
        instance.put("updated_at", now);
        instance.putAll(data);

        String label = normalizeLabel(objectType);
        String cypher = buildCreateCypher(label, instance);
        Map<String, Object> params = new HashMap<>(instance);
        for (Map.Entry<String, Object> e : instance.entrySet()) {
            params.put(e.getKey(), convertToGraphValue(e.getValue()));
        }

        try {
            falkordbGraph.query(cypher, params);
            logger.debug("Created instance {} of type {} in FalkorDB", id, objectType);
            return id;
        } catch (Exception e) {
            logger.error("Failed to create instance in FalkorDB: {}", e.getMessage(), e);
            throw new IOException("Failed to create instance: " + e.getMessage(), e);
        }
    }

    @Override
    public String createInstanceWithId(String objectType, String id, Map<String, Object> data) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        try {
            getInstance(objectType, id);
            throw new IOException("instance with id '" + id + "' already exists");
        } catch (IOException e) {
            if (!"instance not found".equals(e.getMessage())) throw e;
        }

        String now = Instant.now().toString();
        Map<String, Object> instance = new HashMap<>();
        instance.put("id", id);
        instance.put("created_at", now);
        instance.put("updated_at", now);
        instance.putAll(data);

        String label = normalizeLabel(objectType);
        String cypher = buildCreateCypher(label, instance);
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, Object> e : instance.entrySet()) {
            params.put(e.getKey(), convertToGraphValue(e.getValue()));
        }

        try {
            falkordbGraph.query(cypher, params);
            logger.debug("Created instance {} of type {} in FalkorDB with specified ID", id, objectType);
            return id;
        } catch (Exception e) {
            logger.error("Failed to create instance in FalkorDB: {}", e.getMessage(), e);
            throw new IOException("Failed to create instance: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getInstance(String objectType, String id) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String label = normalizeLabel(objectType);
        String cypher = "MATCH (n:" + label + " {id: $id}) RETURN n";

        try {
            ResultSet rs = falkordbGraph.query(cypher, Collections.singletonMap("id", id));
            for (Record record : rs) {
                Object val = record.getValue("n");
                if (val instanceof Node) {
                    return nodeToMap((Node) val);
                }
            }
            throw new IOException("instance not found");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to get instance from FalkorDB: {}", e.getMessage(), e);
            throw new IOException("Failed to get instance: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateInstance(String objectType, String id, Map<String, Object> data) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String label = normalizeLabel(objectType);
        List<String> setClauses = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if ("id".equals(e.getKey())) continue;
            setClauses.add("n." + e.getKey() + " = $" + e.getKey());
            params.put(e.getKey(), convertToGraphValue(e.getValue()));
        }
        setClauses.add("n.updated_at = $updated_at");
        params.put("updated_at", Instant.now().toString());

        String cypher = "MATCH (n:" + label + " {id: $id}) SET " + String.join(", ", setClauses);

        try {
            falkordbGraph.query(cypher, params);
            logger.debug("Updated instance {} of type {} in FalkorDB", id, objectType);
        } catch (Exception e) {
            logger.error("Failed to update instance in FalkorDB: {}", e.getMessage(), e);
            throw new IOException("Failed to update instance: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteInstance(String objectType, String id) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String label = normalizeLabel(objectType);
        String cypher = "MATCH (n:" + label + " {id: $id}) DETACH DELETE n";

        try {
            falkordbGraph.query(cypher, Collections.singletonMap("id", id));
            logger.debug("Deleted instance {} of type {} from FalkorDB", id, objectType);
        } catch (Exception e) {
            logger.error("Failed to delete instance from FalkorDB: {}", e.getMessage(), e);
            throw new IOException("Failed to delete instance: " + e.getMessage(), e);
        }
    }

    @Override
    public int batchMergeInstances(String objectType, List<Map<String, Object>> rows) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");
        if (rows == null || rows.isEmpty()) return 0;

        List<Map<String, Object>> validRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> n = new HashMap<>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getValue() != null) {
                    n.put(e.getKey(), convertToGraphValue(e.getValue()));
                }
            }
            if (n.containsKey("id")) validRows.add(n);
        }
        if (validRows.isEmpty()) return 0;

        String label = normalizeLabel(objectType);
        String now = Instant.now().toString();
        String cypher = "UNWIND $rows AS row MERGE (n:" + label + " {id: row.id}) " +
                "ON CREATE SET n += row, n.created_at = $now, n.updated_at = $now " +
                "ON MATCH SET n += row, n.updated_at = $now";

        Map<String, Object> params = new HashMap<>();
        params.put("rows", validRows);
        params.put("now", now);

        try {
            falkordbGraph.query(cypher, params);
            logger.debug("Batch merged {} instances of type {} to FalkorDB", validRows.size(), objectType);
            return validRows.size();
        } catch (Exception e) {
            logger.error("Failed to batch merge instances in FalkorDB: {}", e.getMessage(), e);
            throw new IOException("Failed to batch merge: " + e.getMessage(), e);
        }
    }

    @Override
    public InstanceStorage.ListResult listInstances(String objectType, int offset, int limit) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String label = normalizeLabel(objectType);
        String countCypher = "MATCH (n:" + label + ") RETURN count(n) AS total";

        try {
            ResultSet countRs = falkordbGraph.query(countCypher);
            long total = 0;
            for (Record r : countRs) {
                Object t = r.getValue("total");
                if (t instanceof Number) total = ((Number) t).longValue();
                break;
            }

            String cypher = "MATCH (n:" + label + ") RETURN n ORDER BY n.created_at DESC SKIP $offset LIMIT $limit";
            Map<String, Object> params = new HashMap<>();
            params.put("offset", offset);
            params.put("limit", limit);

            List<Map<String, Object>> instances = new ArrayList<>();
            ResultSet rs = falkordbGraph.query(cypher, params);
            for (Record record : rs) {
                Object val = record.getValue("n");
                if (val instanceof Node) {
                    instances.add(nodeToMap((Node) val));
                }
            }

            return new InstanceStorage.ListResult(instances, total);
        } catch (Exception e) {
            logger.error("Failed to list instances from FalkorDB: {}", e.getMessage(), e);
            throw new IOException("Failed to list instances: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> searchInstances(String objectType, Map<String, Object> filters) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        if (filters == null || filters.isEmpty()) {
            return listInstances(objectType, 0, 10000).getItems();
        }

        String label = normalizeLabel(objectType);
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, Object> e : filters.entrySet()) {
            conditions.add("n." + e.getKey() + " = $" + e.getKey());
            params.put(e.getKey(), convertToGraphValue(e.getValue()));
        }
        String cypher = "MATCH (n:" + label + ") WHERE " + String.join(" AND ", conditions) + " RETURN n";

        try {
            List<Map<String, Object>> instances = new ArrayList<>();
            ResultSet rs = falkordbGraph.query(cypher, params);
            for (Record record : rs) {
                Object val = record.getValue("n");
                if (val instanceof Node) {
                    instances.add(nodeToMap((Node) val));
                }
            }
            return instances;
        } catch (Exception e) {
            logger.error("Failed to search instances in FalkorDB: {}", e.getMessage(), e);
            throw new IOException("Failed to search instances: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Map<String, Object>> getInstancesBatch(String objectType, List<String> ids) throws IOException {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (String id : ids) {
            try {
                result.put(id, getInstance(objectType, id));
            } catch (IOException e) {
                result.put(id, null);
            }
        }
        return result;
    }

    @Override
    public Map<String, Map<String, Object>> getInstancesBatchMultiType(Map<String, List<String>> typeIdMap) throws IOException {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : typeIdMap.entrySet()) {
            Map<String, Map<String, Object>> batch = getInstancesBatch(entry.getKey(), entry.getValue());
            for (Map.Entry<String, Map<String, Object>> e : batch.entrySet()) {
                result.put(entry.getKey() + ":" + e.getKey(), e.getValue());
            }
        }
        return result;
    }

    private String buildCreateCypher(String label, Map<String, Object> instance) {
        List<String> props = new ArrayList<>();
        for (String key : instance.keySet()) {
            props.add(key + ": $" + key);
        }
        return "CREATE (n:" + label + " {" + String.join(", ", props) + "}) RETURN n.id AS id";
    }

    private Map<String, Object> nodeToMap(Node node) {
        Map<String, Object> map = new HashMap<>();
        for (String name : node.getEntityPropertyNames()) {
            Property p = node.getProperty(name);
            if (p != null) {
                map.put(name, convertValue(p.getValue()));
            }
        }
        return map;
    }

    private String normalizeLabel(String objectType) {
        return objectType.replaceAll("[^a-zA-Z0-9]", "");
    }

    private Object convertToGraphValue(Object value) {
        if (value == null) return null;
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof Map) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
            } catch (Exception e) {
                return value.toString();
            }
        }
        if (value instanceof List) {
            return ((List<?>) value).stream().map(this::convertToGraphValue).collect(Collectors.toList());
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    private Object convertValue(Object value) {
        if (value == null) return null;
        if (value instanceof String) {
            String s = (String) value;
            if ((s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"))) {
                try {
                    return new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, Object.class);
                } catch (Exception e) {
                    return value;
                }
            }
        }
        return value;
    }
}
