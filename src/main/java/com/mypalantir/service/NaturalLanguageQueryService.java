package com.mypalantir.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.meta.Loader;
import com.mypalantir.query.OntologyQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 自然语言查询服务
 * 将自然语言查询转换为 OntologyQuery
 */
@Service
public class NaturalLanguageQueryService {
    private static final Logger logger = LoggerFactory.getLogger(NaturalLanguageQueryService.class);
    
    private final OntologySummaryService ontologySummaryService;
    private final LLMService llmService;
    private final Loader loader;
    private final ObjectMapper objectMapper;
    
    public NaturalLanguageQueryService(
            OntologySummaryService ontologySummaryService,
            LLMService llmService,
            Loader loader) {
        this.ontologySummaryService = ontologySummaryService;
        this.llmService = llmService;
        this.loader = loader;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 将自然语言查询转换为 OntologyQuery
     * @param naturalLanguageQuery 自然语言查询文本
     * @return OntologyQuery 对象
     * @throws NaturalLanguageQueryException 转换失败时抛出
     */
    public OntologyQuery convertToQuery(String naturalLanguageQuery) throws NaturalLanguageQueryException {
        if (naturalLanguageQuery == null || naturalLanguageQuery.trim().isEmpty()) {
            throw new NaturalLanguageQueryException("自然语言查询不能为空");
        }
        
        try {
            // 1. 获取 Ontology 摘要
            String ontologySummary = ontologySummaryService.generateOntologySummary();
            
            // 2. 构建 Prompt
            String systemPrompt = buildSystemPrompt(ontologySummary);
            String userPrompt = "用户查询：" + naturalLanguageQuery;
            
            logger.info("Converting natural language query: {}", naturalLanguageQuery);
            
            // 3. 调用 LLM
            String jsonResponse;
            try {
                jsonResponse = llmService.chat(systemPrompt, userPrompt);
            } catch (LLMService.LLMException e) {
                throw new NaturalLanguageQueryException("LLM 调用失败: " + e.getMessage(), e);
            }
            
            // 4. 清理响应（移除可能的 markdown 代码块标记）
            jsonResponse = cleanJsonResponse(jsonResponse);
            
            logger.debug("LLM response: {}", jsonResponse);
            
            // 5. 解析 JSON 响应为 OntologyQuery
            OntologyQuery query;
            try {
                query = parseJsonToQuery(jsonResponse);
            } catch (Exception e) {
                throw new NaturalLanguageQueryException("解析 LLM 响应失败: " + e.getMessage() + "\n响应内容: " + jsonResponse, e);
            }
            
            // 6. 验证查询
            validateQuery(query);
            
            logger.info("Successfully converted natural language query to OntologyQuery: object={}", query.getObject());
            
            return query;
            
        } catch (NaturalLanguageQueryException e) {
            throw e;
        } catch (Exception e) {
            throw new NaturalLanguageQueryException("转换自然语言查询失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 清理 JSON 响应，移除可能的 markdown 代码块标记
     */
    private String cleanJsonResponse(String response) {
        if (response == null) {
            return null;
        }
        
        String cleaned = response.trim();
        
        // 移除 markdown 代码块标记
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }
    
    /**
     * 解析 JSON 字符串为 OntologyQuery
     */
    private OntologyQuery parseJsonToQuery(String json) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);
        
        OntologyQuery query = new OntologyQuery();
        
        // object
        if (rootNode.has("object")) {
            query.setObject(rootNode.get("object").asText());
        } else if (rootNode.has("from")) {
            query.setFrom(rootNode.get("from").asText());
        }
        
        // select
        if (rootNode.has("select") && rootNode.get("select").isArray()) {
            List<String> select = new ArrayList<>();
            for (JsonNode item : rootNode.get("select")) {
                select.add(item.asText());
            }
            query.setSelect(select);
        }
        
        // links
        if (rootNode.has("links") && rootNode.get("links").isArray()) {
            List<OntologyQuery.LinkQuery> links = new ArrayList<>();
            for (JsonNode linkNode : rootNode.get("links")) {
                OntologyQuery.LinkQuery linkQuery = new OntologyQuery.LinkQuery();
                if (linkNode.has("name")) {
                    linkQuery.setName(linkNode.get("name").asText());
                }
                if (linkNode.has("object")) {
                    linkQuery.setObject(linkNode.get("object").asText());
                }
                if (linkNode.has("select") && linkNode.get("select").isArray()) {
                    List<String> select = new ArrayList<>();
                    for (JsonNode item : linkNode.get("select")) {
                        select.add(item.asText());
                    }
                    linkQuery.setSelect(select);
                }
                links.add(linkQuery);
            }
            query.setLinks(links);
        }
        
        // group_by
        if (rootNode.has("group_by") && rootNode.get("group_by").isArray()) {
            List<String> groupBy = new ArrayList<>();
            for (JsonNode item : rootNode.get("group_by")) {
                groupBy.add(item.asText());
            }
            query.setGroupBy(groupBy);
        }
        
        // metrics
        if (rootNode.has("metrics") && rootNode.get("metrics").isArray()) {
            List<Object> metrics = new ArrayList<>();
            for (JsonNode metricNode : rootNode.get("metrics")) {
                if (metricNode.isArray()) {
                    List<Object> metric = new ArrayList<>();
                    for (JsonNode item : metricNode) {
                        if (item.isTextual()) {
                            metric.add(item.asText());
                        } else if (item.isNumber()) {
                            metric.add(item.asDouble());
                        } else {
                            metric.add(item.asText());
                        }
                    }
                    metrics.add(metric);
                }
            }
            query.setMetrics(metrics);
        }
        
        // filter
        if (rootNode.has("filter") && rootNode.get("filter").isArray()) {
            List<Object> filter = new ArrayList<>();
            for (JsonNode filterNode : rootNode.get("filter")) {
                if (filterNode.isArray()) {
                    List<Object> filterItem = new ArrayList<>();
                    for (JsonNode item : filterNode) {
                        if (item.isTextual()) {
                            filterItem.add(item.asText());
                        } else if (item.isNumber()) {
                            filterItem.add(item.asDouble());
                        } else {
                            filterItem.add(item.asText());
                        }
                    }
                    filter.add(filterItem);
                }
            }
            query.setFilter(filter);
        }
        
        // orderBy
        if (rootNode.has("orderBy") && rootNode.get("orderBy").isArray()) {
            List<OntologyQuery.OrderBy> orderBy = new ArrayList<>();
            for (JsonNode orderNode : rootNode.get("orderBy")) {
                OntologyQuery.OrderBy order = new OntologyQuery.OrderBy();
                if (orderNode.has("field")) {
                    order.setField(orderNode.get("field").asText());
                }
                if (orderNode.has("direction")) {
                    order.setDirection(orderNode.get("direction").asText());
                }
                orderBy.add(order);
            }
            query.setOrderBy(orderBy);
        }
        
        // limit
        if (rootNode.has("limit")) {
            query.setLimit(rootNode.get("limit").asInt());
        }
        
        // offset
        if (rootNode.has("offset")) {
            query.setOffset(rootNode.get("offset").asInt());
        }
        
        return query;
    }
    
    /**
     * 验证查询的有效性
     */
    private void validateQuery(OntologyQuery query) throws NaturalLanguageQueryException {
        // 验证 object
        String objectName = query.getObject();
        if (objectName == null || objectName.isEmpty()) {
            throw new NaturalLanguageQueryException("查询必须指定 object 字段");
        }
        
        // 验证对象类型是否存在
        try {
            loader.getObjectType(objectName);
        } catch (Loader.NotFoundException e) {
            throw new NaturalLanguageQueryException("对象类型 '" + objectName + "' 不存在");
        }
        
        // 验证 links
        if (query.getLinks() != null) {
            for (OntologyQuery.LinkQuery linkQuery : query.getLinks()) {
                if (linkQuery.getName() == null || linkQuery.getName().isEmpty()) {
                    throw new NaturalLanguageQueryException("LinkQuery 必须指定 name 字段");
                }
                
                try {
                    loader.getLinkType(linkQuery.getName());
                } catch (Loader.NotFoundException e) {
                    throw new NaturalLanguageQueryException("LinkType '" + linkQuery.getName() + "' 不存在");
                }
            }
        }
    }
    
    /**
     * 构建系统 Prompt
     */
    private String buildSystemPrompt(String ontologyJson) {
        return String.format("""
            你是一个数据查询助手，负责将自然语言查询转换为结构化的 Ontology 查询 DSL。
            
            ## Ontology Schema
            
            %s
            
            ## 查询 DSL 格式
            
            查询 DSL 是一个 JSON 对象，包含以下字段：
            
            1. **object** (必需): 查询的根对象类型名称
            2. **links** (可选): 关联查询数组，每个元素包含：
               - **name**: LinkType 名称
               - **select** (可选): 要选择的字段列表
            3. **select** (可选): 要选择的字段列表（普通查询）
            4. **group_by** (可选): 分组字段列表（聚合查询）
            5. **metrics** (可选): 聚合指标数组，格式为 ["function", "field_path", "alias"]
               - function: sum, avg, count, min, max
               - field_path: 字段路径，如 "拥有收费记录.金额"
               - alias: 可选别名
            6. **filter** (可选): 过滤条件数组，格式为 ["operator", "field", "value"] 或 ["operator", "field", "value1", "value2"] (for between)
               - operator: =, !=, >, <, >=, <=, between, in, like
            7. **orderBy** (可选): 排序数组，格式为 [{"field": "field_path", "direction": "ASC|DESC"}]
            8. **limit** (可选): 限制返回数量
            9. **offset** (可选): 偏移量
            
            ## 字段路径规则
            
            - 对象属性：直接使用属性名，如 "名称"
            - 关联属性：使用 "LinkType名称.属性名"，如 "拥有收费记录.金额"
            - 多层关联：使用 "LinkType1.LinkType2.属性名"
            
            ## 转换规则

            1. 识别查询的主对象类型（object）——必须使用 Schema 中的英文名称
            2. 识别需要关联的 LinkType（links）——object 必须是该 link 的 source_type
            3. 识别聚合需求（group_by, metrics）
            4. 识别过滤条件（filter）
            5. 识别排序需求（orderBy）

            ## Link 方向规则（极其重要）

            Link 是有方向的：source_type -> target_type。
            查询的 object 必须是 link 的 source_type。如果你想查"每个收费站有多少入口交易"，
            不能用 object=TollStation + link=entry_at_station（因为 entry_at_station 的 source 是 EntryTransaction，不是 TollStation）。
            正确做法是 object=EntryTransaction + link=entry_at_station，然后 group_by 收费站属性。

            ## 示例

            ### 示例 1: 简单查询
            用户查询："显示所有收费站"
            转换结果：
            {
              "object": "TollStation",
              "select": ["station_name", "station_id"]
            }

            ### 示例 2: 统计各收费站的通行数量
            用户查询："展示各收费站的通行数量"
            分析：entry_at_station 的 source 是 EntryTransaction，target 是 TollStation，所以 object 必须是 EntryTransaction
            转换结果：
            {
              "object": "EntryTransaction",
              "links": [{"name": "entry_at_station", "select": ["station_name"]}],
              "group_by": ["entry_at_station.station_name"],
              "metrics": [["count", "id", "通行数量"]]
            }

            ### 示例 3: 统计各收费站的车道数量
            用户查询："统计各收费站的车道数量"
            分析：station_has_lanes 的 source 是 TollStation，target 是 TollLane，所以 object 是 TollStation
            转换结果：
            {
              "object": "TollStation",
              "links": [{"name": "station_has_lanes"}],
              "group_by": ["station_name"],
              "metrics": [["count", "station_has_lanes.lane_id", "车道数量"]]
            }

            ### 示例 4: 带过滤条件的查询
            用户查询："显示2024年1月的入口交易"
            转换结果：
            {
              "object": "EntryTransaction",
              "select": ["trans_fee", "trans_time"],
              "filter": [["between", "trans_time", "2024-01-01", "2024-01-31"]]
            }

            ## 重要提示

            1. 只返回 JSON 对象，不要包含任何解释文字或 markdown 代码块标记
            2. object、link name、属性名必须使用 Schema 中的英文名称，严格匹配
            3. object 必须是所用 link 的 source_type，绝不能是 target_type
            4. 如果不确定如何转换，返回一个包含 "error" 字段的 JSON 对象，说明原因
            """, ontologyJson);
    }
    
    /**
     * 将 OntologyQuery 转换为 Map 格式（只包含非 null 字段）
     */
    public Map<String, Object> convertToMap(OntologyQuery query) {
        Map<String, Object> map = new HashMap<>();

        if (query.getObject() != null) {
            map.put("object", query.getObject());
        } else if (query.getFrom() != null) {
            map.put("from", query.getFrom());
        }

        if (query.getSelect() != null) {
            map.put("select", query.getSelect());
        }

        if (query.getLinks() != null) {
            map.put("links", query.getLinks().stream().map(link -> {
                Map<String, Object> linkMap = new HashMap<>();
                linkMap.put("name", link.getName());
                if (link.getObject() != null) {
                    linkMap.put("object", link.getObject());
                }
                if (link.getSelect() != null) {
                    linkMap.put("select", link.getSelect());
                }
                return linkMap;
            }).toList());
        }

        if (query.getGroupBy() != null) {
            map.put("group_by", query.getGroupBy());
        }

        if (query.getMetrics() != null) {
            map.put("metrics", query.getMetrics());
        }

        if (query.getFilter() != null) {
            map.put("filter", query.getFilter());
        }

        if (query.getOrderBy() != null) {
            map.put("orderBy", query.getOrderBy().stream().map(order -> {
                Map<String, Object> orderMap = new HashMap<>();
                orderMap.put("field", order.getField());
                orderMap.put("direction", order.getDirection());
                return orderMap;
            }).toList());
        }

        if (query.getLimit() != null) {
            map.put("limit", query.getLimit());
        }

        if (query.getOffset() != null) {
            map.put("offset", query.getOffset());
        }

        return map;
    }

    /**
     * 自然语言查询异常类
     */
    public static class NaturalLanguageQueryException extends Exception {
        public NaturalLanguageQueryException(String message) {
            super(message);
        }
        
        public NaturalLanguageQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

