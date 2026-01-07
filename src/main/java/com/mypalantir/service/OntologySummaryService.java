package com.mypalantir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.meta.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Ontology Schema 摘要服务
 * 用于生成 Ontology Schema 的 JSON 摘要，供 LLM 使用
 */
@Service
public class OntologySummaryService {
    private final Loader loader;
    private final ObjectMapper objectMapper;
    private String cachedSummary;
    private long lastCacheTime;
    private static final long CACHE_TTL = 5 * 60 * 1000; // 5分钟缓存

    public OntologySummaryService(Loader loader) {
        this.loader = loader;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 生成 Ontology Schema 的 JSON 摘要（用于 LLM Prompt）
     * 带缓存机制，避免频繁生成
     */
    public String generateOntologySummary() throws Exception {
        long currentTime = System.currentTimeMillis();
        
        // 检查缓存是否有效
        if (cachedSummary != null && (currentTime - lastCacheTime) < CACHE_TTL) {
            return cachedSummary;
        }
        
        // 生成新的摘要
        OntologySchema schema = loader.getSchema();
        if (schema == null) {
            throw new IllegalStateException("Schema not loaded");
        }
        
        Map<String, Object> summary = new HashMap<>();
        
        // Object Types 摘要
        List<Map<String, Object>> objectTypesSummary = new ArrayList<>();
        if (schema.getObjectTypes() != null) {
            objectTypesSummary = schema.getObjectTypes().stream()
                .map(ot -> {
                    Map<String, Object> obj = new HashMap<>();
                    obj.put("name", ot.getName());
                    obj.put("description", ot.getDescription() != null ? ot.getDescription() : "");
                    
                    // Properties 摘要（只包含关键信息）
                    List<Map<String, Object>> props = new ArrayList<>();
                    if (ot.getProperties() != null) {
                        props = ot.getProperties().stream()
                            .map(p -> {
                                Map<String, Object> prop = new HashMap<>();
                                prop.put("name", p.getName());
                                prop.put("data_type", p.getDataType() != null ? p.getDataType() : "string");
                                prop.put("description", p.getDescription() != null ? p.getDescription() : "");
                                return prop;
                            })
                            .collect(Collectors.toList());
                    }
                    obj.put("properties", props);
                    
                    return obj;
                })
                .collect(Collectors.toList());
        }
        summary.put("object_types", objectTypesSummary);
        
        // Link Types 摘要
        List<Map<String, Object>> linkTypesSummary = new ArrayList<>();
        if (schema.getLinkTypes() != null) {
            linkTypesSummary = schema.getLinkTypes().stream()
                .map(lt -> {
                    Map<String, Object> link = new HashMap<>();
                    link.put("name", lt.getName());
                    link.put("description", lt.getDescription() != null ? lt.getDescription() : "");
                    link.put("source_type", lt.getSourceType());
                    link.put("target_type", lt.getTargetType());
                    link.put("cardinality", lt.getCardinality() != null ? lt.getCardinality() : "many-to-many");
                    link.put("direction", lt.getDirection() != null ? lt.getDirection() : "directed");
                    
                    // LinkType 的属性（如果有）
                    if (lt.getProperties() != null && !lt.getProperties().isEmpty()) {
                        List<Map<String, Object>> props = lt.getProperties().stream()
                            .map(p -> {
                                Map<String, Object> prop = new HashMap<>();
                                prop.put("name", p.getName());
                                prop.put("data_type", p.getDataType() != null ? p.getDataType() : "string");
                                prop.put("description", p.getDescription() != null ? p.getDescription() : "");
                                return prop;
                            })
                            .collect(Collectors.toList());
                        link.put("properties", props);
                    }
                    
                    return link;
                })
                .collect(Collectors.toList());
        }
        summary.put("link_types", linkTypesSummary);
        
        // 序列化为 JSON
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
        
        // 更新缓存
        cachedSummary = json;
        lastCacheTime = currentTime;
        
        return json;
    }

    /**
     * 清除缓存（当 Schema 更新时调用）
     */
    public void clearCache() {
        cachedSummary = null;
        lastCacheTime = 0;
    }
}

