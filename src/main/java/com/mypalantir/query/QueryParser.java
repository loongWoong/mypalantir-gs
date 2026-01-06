package com.mypalantir.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询 DSL 解析器
 * 支持 JSON 和 YAML 格式
 */
public class QueryParser {
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    public QueryParser() {
        this.jsonMapper = new ObjectMapper();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * 解析 JSON 格式的查询
     */
    public OntologyQuery parseJson(String json) throws IOException {
        return jsonMapper.readValue(json, OntologyQuery.class);
    }

    /**
     * 解析 YAML 格式的查询
     */
    public OntologyQuery parseYaml(String yaml) throws IOException {
        return yamlMapper.readValue(yaml, OntologyQuery.class);
    }

    /**
     * 解析 Map 格式的查询（从 HTTP 请求体）
     */
    public OntologyQuery parseMap(Map<String, Object> map) {
        OntologyQuery query = new OntologyQuery();
        
        // 支持 from 和 object 两种方式（向后兼容）
        if (map.containsKey("object")) {
            query.setObject((String) map.get("object"));
        } else if (map.containsKey("from")) {
            query.setFrom((String) map.get("from"));
        }
        
        // 支持 where 和 filter 两种方式（向后兼容）
        if (map.containsKey("filter")) {
            @SuppressWarnings("unchecked")
            List<Object> filter = (List<Object>) map.get("filter");
            query.setFilter(filter);
        } else if (map.containsKey("where")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> where = (Map<String, Object>) map.get("where");
            query.setWhere(where);
        }
        
        if (map.containsKey("select")) {
            @SuppressWarnings("unchecked")
            List<String> select = (List<String>) map.get("select");
            query.setSelect(select);
        }
        
        if (map.containsKey("links")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) map.get("links");
            query.setLinks(parseLinkQueries(links));
        }
        
        if (map.containsKey("group_by")) {
            @SuppressWarnings("unchecked")
            List<String> groupBy = (List<String>) map.get("group_by");
            query.setGroupBy(groupBy);
        }
        
        if (map.containsKey("metrics")) {
            @SuppressWarnings("unchecked")
            List<Object> metrics = (List<Object>) map.get("metrics");
            query.setMetrics(metrics);
        }
        
        if (map.containsKey("limit")) {
            Object limit = map.get("limit");
            if (limit instanceof Number) {
                query.setLimit(((Number) limit).intValue());
            }
        }
        
        if (map.containsKey("offset")) {
            Object offset = map.get("offset");
            if (offset instanceof Number) {
                query.setOffset(((Number) offset).intValue());
            }
        }
        
        if (map.containsKey("orderBy") || map.containsKey("order_by")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> orderByList = (List<Map<String, Object>>) 
                map.getOrDefault("orderBy", map.get("order_by"));
            query.setOrderBy(parseOrderBy(orderByList));
        }
        
        return query;
    }

    private List<OntologyQuery.LinkQuery> parseLinkQueries(List<Map<String, Object>> links) {
        List<OntologyQuery.LinkQuery> result = new ArrayList<>();
        for (Map<String, Object> linkMap : links) {
            OntologyQuery.LinkQuery linkQuery = new OntologyQuery.LinkQuery();
            
            if (linkMap.containsKey("name")) {
                linkQuery.setName((String) linkMap.get("name"));
            }
            
            if (linkMap.containsKey("object")) {
                linkQuery.setObject((String) linkMap.get("object"));
            }
            
            if (linkMap.containsKey("select")) {
                @SuppressWarnings("unchecked")
                List<String> select = (List<String>) linkMap.get("select");
                linkQuery.setSelect(select);
            }
            
            if (linkMap.containsKey("where")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> where = (Map<String, Object>) linkMap.get("where");
                linkQuery.setWhere(where);
            }
            
            if (linkMap.containsKey("links")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nestedLinks = (List<Map<String, Object>>) linkMap.get("links");
                linkQuery.setLinks(parseLinkQueries(nestedLinks));
            }
            
            result.add(linkQuery);
        }
        return result;
    }

    private List<OntologyQuery.OrderBy> parseOrderBy(List<Map<String, Object>> orderByList) {
        List<OntologyQuery.OrderBy> result = new ArrayList<>();
        for (Map<String, Object> orderByMap : orderByList) {
            String field = (String) orderByMap.get("field");
            String direction = orderByMap.containsKey("direction") 
                ? (String) orderByMap.get("direction") 
                : "ASC";
            result.add(new OntologyQuery.OrderBy(field, direction));
        }
        return result;
    }
}

