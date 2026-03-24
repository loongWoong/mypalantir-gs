package com.mypalantir.repository;

import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.graph_entities.Edge;
import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Property;
import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FalkorDB 关系存储实现
 * 使用 OpenCypher 语法，与 Neo4j 兼容
 */
@Component
@ConditionalOnProperty(name = "storage.graph.type", havingValue = "falkordb")
public class FalkorDBLinkStorage implements ILinkStorage {
    private static final Logger logger = LoggerFactory.getLogger(FalkorDBLinkStorage.class);

    @Autowired(required = false)
    private Graph falkordbGraph;

    @Autowired(required = false)
    private Loader loader;

    @Autowired(required = false)
    private IInstanceStorage instanceStorage;

    @Autowired(required = false)
    private FalkorDBInstanceStorage falkordbInstanceStorage;

    @Autowired(required = false)
    private Environment environment;

    @Override
    public String createLink(String linkType, String sourceID, String targetID, Map<String, Object> properties) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String sourceLabel;
        String targetLabel;
        if (loader != null) {
            try {
                LinkType lt = loader.getLinkType(linkType);
                sourceLabel = normalizeLabel(lt.getSourceType());
                targetLabel = normalizeLabel(lt.getTargetType());
            } catch (Loader.NotFoundException e) {
                sourceLabel = getNodeLabel(sourceID);
                targetLabel = getNodeLabel(targetID);
            }
        } else {
            sourceLabel = getNodeLabel(sourceID);
            targetLabel = getNodeLabel(targetID);
        }

        ensureNodeExists(sourceID, sourceLabel, loader != null ? getSourceType(linkType) : null);
        ensureNodeExists(targetID, targetLabel, loader != null ? getTargetType(linkType) : null);

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String relType = normalizeRelType(linkType);

        Map<String, Object> relProps = new HashMap<>();
        relProps.put("id", id);
        relProps.put("created_at", now);
        relProps.put("updated_at", now);
        if (properties != null) relProps.putAll(properties);

        StringBuilder cypher = new StringBuilder();
        cypher.append("MATCH (source:").append(sourceLabel).append(" {id: $sourceId}) ");
        cypher.append("MATCH (target:").append(targetLabel).append(" {id: $targetId}) ");
        cypher.append("CREATE (source)-[r:").append(relType).append(" {");
        List<String> propKeys = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("sourceId", sourceID);
        params.put("targetId", targetID);
        for (Map.Entry<String, Object> e : relProps.entrySet()) {
            propKeys.add(e.getKey() + ": $" + e.getKey());
            params.put(e.getKey(), e.getValue());
        }
        cypher.append(String.join(", ", propKeys)).append("}]->(target) RETURN r.id AS id");

        try {
            ResultSet rs = falkordbGraph.query(cypher.toString(), params);
            for (Record r : rs) {
                Object idVal = r.getValue("id");
                return idVal != null ? idVal.toString() : id;
            }
            return id;
        } catch (Exception e) {
            logger.error("Failed to create link in FalkorDB: {}", e.getMessage(), e);
            throw new IOException("Failed to create link: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getLink(String linkType, String id) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String relType = normalizeRelType(linkType);
        String cypher = "MATCH (a)-[r:" + relType + " {id: $id}]->(b) RETURN r, a.id AS source_id, b.id AS target_id";
        ResultSet rs = falkordbGraph.query(cypher, Collections.singletonMap("id", id));

        for (Record record : rs) {
            Object rVal = record.getValue("r");
            Map<String, Object> link = new HashMap<>();
            if (rVal instanceof Edge) {
                Edge e = (Edge) rVal;
                for (String name : e.getEntityPropertyNames()) {
                    Property p = e.getProperty(name);
                    if (p != null) link.put(name, p.getValue());
                }
            }
            Object src = record.getValue("source_id");
            Object tgt = record.getValue("target_id");
            if (src != null) link.put("source_id", src.toString());
            if (tgt != null) link.put("target_id", tgt.toString());
            return link;
        }
        throw new IOException("link not found");
    }

    @Override
    public void updateLink(String linkType, String id, Map<String, Object> properties) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String relType = normalizeRelType(linkType);
        List<String> setClauses = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            if ("id".equals(e.getKey()) || "source_id".equals(e.getKey()) || "target_id".equals(e.getKey())) continue;
            setClauses.add("r." + e.getKey() + " = $" + e.getKey());
            params.put(e.getKey(), e.getValue());
        }
        setClauses.add("r.updated_at = $updated_at");
        params.put("updated_at", Instant.now().toString());

        String cypher = "MATCH ()-[r:" + relType + " {id: $id}]-() SET " + String.join(", ", setClauses);
        falkordbGraph.query(cypher, params);
    }

    @Override
    public void deleteLink(String linkType, String id) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String relType = normalizeRelType(linkType);
        String cypher = "MATCH ()-[r:" + relType + " {id: $id}]-() DELETE r";
        falkordbGraph.query(cypher, Collections.singletonMap("id", id));
    }

    @Override
    public List<Map<String, Object>> getLinksBySource(String linkType, String sourceID) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String sourceLabel = getSourceLabel(linkType, sourceID);
        String relType = normalizeRelType(linkType);
        String cypher = "MATCH (source:" + sourceLabel + " {id: $sourceId})-[r:" + relType + "]->(target) RETURN r, source.id AS source_id, target.id AS target_id";
        return queryLinks(cypher, Collections.singletonMap("sourceId", sourceID), sourceID, null);
    }

    @Override
    public List<Map<String, Object>> getLinksByTarget(String linkType, String targetID) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String targetLabel = getTargetLabel(linkType, targetID);
        String relType = normalizeRelType(linkType);
        String cypher = "MATCH (source)-[r:" + relType + "]->(target:" + targetLabel + " {id: $targetId}) RETURN r, source.id AS source_id, target.id AS target_id";
        return queryLinks(cypher, Collections.singletonMap("targetId", targetID), null, targetID);
    }

    @Override
    public InstanceStorage.ListResult listLinks(String linkType, int offset, int limit) throws IOException {
        if (falkordbGraph == null) throw new IOException("FalkorDB graph is not initialized");

        String relType = normalizeRelType(linkType);
        String countCypher = "MATCH ()-[r:" + relType + "]->() RETURN count(r) AS total";
        long total = 0;
        for (Record r : falkordbGraph.query(countCypher)) {
            Object t = r.getValue("total");
            if (t instanceof Number) total = ((Number) t).longValue();
            break;
        }

        String cypher = "MATCH (source)-[r:" + relType + "]->(target) RETURN r, source.id AS source_id, target.id AS target_id ORDER BY r.created_at DESC SKIP $offset LIMIT $limit";
        Map<String, Object> params = new HashMap<>();
        params.put("offset", offset);
        params.put("limit", limit);
        List<Map<String, Object>> links = queryLinks(cypher, params, null, null);
        return new InstanceStorage.ListResult(links, total);
    }

    private List<Map<String, Object>> queryLinks(String cypher, Map<String, Object> params, String defaultSourceId, String defaultTargetId) throws IOException {
        List<Map<String, Object>> links = new ArrayList<>();
        ResultSet rs = falkordbGraph.query(cypher, params);
        for (Record record : rs) {
            Map<String, Object> link = new HashMap<>();
            Object rVal = record.getValue("r");
            if (rVal instanceof Edge) {
                Edge e = (Edge) rVal;
                for (String name : e.getEntityPropertyNames()) {
                    Property p = e.getProperty(name);
                    if (p != null) link.put(name, p.getValue());
                }
            }
            Object src = record.getValue("source_id");
            Object tgt = record.getValue("target_id");
            link.put("source_id", src != null ? src.toString() : defaultSourceId);
            link.put("target_id", tgt != null ? tgt.toString() : defaultTargetId);
            links.add(link);
        }
        return links;
    }

    private void ensureNodeExists(String nodeId, String label, String objectType) throws IOException {
        String checkCypher = "MATCH (n:" + label + " {id: $id}) RETURN n.id AS id";
        for (Record r : falkordbGraph.query(checkCypher, Collections.singletonMap("id", nodeId))) {
            return;
        }
        if (objectType != null && instanceStorage != null && falkordbInstanceStorage != null) {
            try {
                Map<String, Object> data = instanceStorage.getInstance(objectType, nodeId);
                List<String> fields = getGraphFields(objectType);
                Map<String, Object> summary = new HashMap<>();
                for (String f : fields) {
                    if (data.containsKey(f)) summary.put(f, data.get(f));
                }
                if (!summary.containsKey("id")) summary.put("id", nodeId);
                falkordbInstanceStorage.createInstanceWithId(objectType, nodeId, summary);
            } catch (Exception e) {
                throw new IOException("Node not found and cannot create: " + e.getMessage(), e);
            }
        } else {
            throw new IOException("Node with id '" + nodeId + "' not found");
        }
    }

    private String getNodeLabel(String nodeId) throws IOException {
        String cypher = "MATCH (n {id: $id}) RETURN labels(n) AS labels";
        for (Record r : falkordbGraph.query(cypher, Collections.singletonMap("id", nodeId))) {
            Object labels = r.getValue("labels");
            if (labels instanceof List && !((List<?>) labels).isEmpty()) {
                return ((List<?>) labels).get(0).toString();
            }
        }
        throw new IOException("Node with id '" + nodeId + "' not found");
    }

    private String getSourceLabel(String linkType, String sourceID) throws IOException {
        if (loader != null) {
            try {
                return normalizeLabel(loader.getLinkType(linkType).getSourceType());
            } catch (Loader.NotFoundException ignored) {}
        }
        return getNodeLabel(sourceID);
    }

    private String getTargetLabel(String linkType, String targetID) throws IOException {
        if (loader != null) {
            try {
                return normalizeLabel(loader.getLinkType(linkType).getTargetType());
            } catch (Loader.NotFoundException ignored) {}
        }
        return getNodeLabel(targetID);
    }

    private String getSourceType(String linkType) {
        try {
            return loader.getLinkType(linkType).getSourceType();
        } catch (Exception e) {
            return null;
        }
    }

    private String getTargetType(String linkType) {
        try {
            return loader.getLinkType(linkType).getTargetType();
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> getGraphFields(String objectType) {
        if (environment != null) {
            String cfg = environment.getProperty("storage.neo4j.fields." + objectType.toLowerCase());
            if (cfg == null) cfg = environment.getProperty("storage.neo4j.fields.default");
            if (cfg != null) {
                return Arrays.stream(cfg.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            }
        }
        return Arrays.asList("id", "name", "display_name");
    }

    private String normalizeRelType(String linkType) {
        return linkType.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
    }

    private String normalizeLabel(String objectType) {
        return objectType.replaceAll("[^a-zA-Z0-9]", "");
    }
}
