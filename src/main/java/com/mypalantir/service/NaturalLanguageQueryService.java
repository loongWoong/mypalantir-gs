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
            logger.error("Natural language query is empty");
            throw new NaturalLanguageQueryException("自然语言查询不能为空");
        }

        try {
            // 1. 获取 Ontology 摘要
            String ontologySummary = ontologySummaryService.generateOntologySummary();
            // 2. 构建 Prompt
            String systemPrompt = buildSystemPrompt(ontologySummary);
            String userPrompt = "用户查询：" + naturalLanguageQuery;

            logger.info("Converting natural language query: {}", naturalLanguageQuery);
            logger.debug("System prompt length: {} characters", systemPrompt.length());

            // 3. 调用 LLM
            String jsonResponse;
            try {
                jsonResponse = llmService.chat(systemPrompt, userPrompt);
            } catch (LLMService.LLMException e) {
                logger.error("LLM service call failed: {}", e.getMessage());
                throw new NaturalLanguageQueryException("LLM 调用失败: " + e.getMessage(), e);
            }

            // 4. 清理响应（移除可能的 markdown 代码块标记）
            String originalResponse = jsonResponse;
            jsonResponse = cleanJsonResponse(jsonResponse);

            logger.debug("LLM response: {}", jsonResponse);
            logger.debug("Cleaned JSON response: {}", jsonResponse.length() > 500 ? jsonResponse.substring(0, 500) + "..." : jsonResponse);

            // 5. 解析 JSON 响应为 OntologyQuery
            OntologyQuery query;
            try {
                query = parseJsonToQuery(jsonResponse);
            } catch (Exception e) {
                logger.error("Failed to parse JSON response: {}", e.getMessage());
                logger.error("Response content: {}", jsonResponse);
                throw new NaturalLanguageQueryException("解析 LLM 响应失败: " + e.getMessage() + "\n响应内容: " + jsonResponse, e);
            }

            // 6. 验证查询
            validateQuery(query);

            logger.info("Successfully converted natural language query to OntologyQuery: object={}", query.getObject());
            logger.info("=== Conversion completed successfully ===");
            logger.info("Result - Object: {}, Links: {}, GroupBy: {}, Metrics: {}, Filter: {}",
                    query.getObject(),
                    query.getLinks() != null ? query.getLinks().size() : 0,
                    query.getGroupBy() != null ? query.getGroupBy().size() : 0,
                    query.getMetrics() != null ? query.getMetrics().size() : 0,
                    query.getFilter() != null ? query.getFilter().size() : 0
            );

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
            logger.error("Query validation failed: missing object field");
            throw new NaturalLanguageQueryException("查询必须指定 object 字段");
        }

        // 验证对象类型是否存在
        try {
            loader.getObjectType(objectName);
        } catch (Loader.NotFoundException e) {
            logger.error("Query validation failed: object type '{}' not found", objectName);
            throw new NaturalLanguageQueryException("对象类型 '" + objectName + "' 不存在");
        }

        // 验证 links
        if (query.getLinks() != null) {
            for (OntologyQuery.LinkQuery linkQuery : query.getLinks()) {
                if (linkQuery.getName() == null || linkQuery.getName().isEmpty()) {
                    logger.error("Query validation failed: link query missing name field");
                    throw new NaturalLanguageQueryException("LinkQuery 必须指定 name 字段");
                }

                try {
                    loader.getLinkType(linkQuery.getName());
                } catch (Loader.NotFoundException e) {
                    logger.error("Query validation failed: link type '{}' not found", linkQuery.getName());
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
            
            1. 识别查询的主对象类型（object）
            2. 识别需要关联的 LinkType（links）
            3. 识别聚合需求（group_by, metrics）
            4. 识别过滤条件（filter）
            5. 识别排序需求（orderBy）
            
            ## 示例
            
            ### 示例 1: 简单查询
            用户查询："显示所有收费站"
            用户查询："显示所有车辆通行路径"
            转换结果：
            {
              "object": "收费站",
              "select": ["名称", "省份"]
              "object": "车辆通行路径",
              "select": ["车牌号码", "通行标识ID"]
            }
            
            ### 示例 2: 聚合查询
            用户查询："显示每个收费站的总收费金额，按金额降序排列"
            用户查询："显示每个车的总收费金额，按金额降序排列"
            转换结果：
            {
              "object": "收费站",
              "object": "出口车道流水",
              "links": [{"name": "拥有收费记录"}],
              "group_by": ["名称"],
              "metrics": [["sum", "拥有收费记录.金额", "总金额"]],
              "orderBy": [{"field": "总金额", "direction": "DESC"}]
            }
            
            ### 示例 3: 带过滤条件的查询
            用户查询："显示江苏省的收费站"
            转换结果：
            {
              "object": "收费站",
              "select": ["名称", "省份"],
              "filter": [["=", "省份", "江苏"]]
            }
            
            ### 示例 4: 时间范围查询
            用户查询："显示2024年1月的收费记录"
            转换结果：
            {
              "object": "收费记录",
              "select": ["金额", "收费时间"],
              "filter": [["between", "收费时间", "2024-01-01", "2024-01-31"]]
            }
            
            ### 示例 5: 多关联查询
            用户查询："显示每个车辆的总收费金额"
            转换结果：
            {
              "object": "车辆",
              "links": [{"name": "拥有车辆记录"}],
              "group_by": ["车牌号"],
              "metrics": [["sum", "拥有车辆记录.金额", "总金额"]]
            }
            
            ## 重要提示
            
            1. 只返回 JSON 对象，不要包含任何解释文字或 markdown 代码块标记
            2. 确保所有字段名和值都来自 Ontology Schema
            3. 如果不确定如何转换，返回一个包含 "error" 字段的 JSON 对象，说明原因
            4. 字段路径必须准确匹配 Ontology Schema 中的定义
            """, ontologyJson);
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


