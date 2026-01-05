package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.query.OntologyQuery;
import com.mypalantir.query.QueryExecutor;
import com.mypalantir.query.QueryParser;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 查询服务
 */
@Service
public class QueryService {
    private final Loader loader;
    private final QueryParser parser;
    private QueryExecutor executor;

    public QueryService(Loader loader) {
        this.loader = loader;
        this.parser = new QueryParser();
    }

    /**
     * 执行查询
     */
    public QueryExecutor.QueryResult executeQuery(Map<String, Object> queryMap) throws Exception {
        // 解析查询
        OntologyQuery query = parser.parseMap(queryMap);
        
        // 调试：打印解析后的查询
        System.out.println("\n" + "=".repeat(80));
        System.out.println("=== Parsed OntologyQuery ===");
        System.out.println("Object/From: " + query.getFrom());
        System.out.println("Select: " + query.getSelect());
        
        if (query.getFilter() != null && !query.getFilter().isEmpty()) {
            System.out.println("Filter: " + query.getFilter());
        }
        if (query.getWhere() != null && !query.getWhere().isEmpty()) {
            System.out.println("Where: " + query.getWhere());
        }
        
        if (query.getGroupBy() != null && !query.getGroupBy().isEmpty()) {
            System.out.println("Group By: " + query.getGroupBy());
        }
        if (query.getMetrics() != null && !query.getMetrics().isEmpty()) {
            System.out.println("Metrics: " + query.getMetrics());
        }
        
        if (query.getLinks() != null && !query.getLinks().isEmpty()) {
            System.out.println("Links:");
            for (OntologyQuery.LinkQuery linkQuery : query.getLinks()) {
                System.out.println("  - Name: " + linkQuery.getName());
                System.out.println("    Object: " + linkQuery.getObject());
                System.out.println("    Select: " + linkQuery.getSelect());
            }
        }
        
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            System.out.println("Order By: " + query.getOrderBy());
        }
        System.out.println("Limit: " + query.getLimit());
        System.out.println("Offset: " + query.getOffset());
        System.out.println("=".repeat(80) + "\n");
        
        // 验证查询
        validateQuery(query);
        
        // 执行查询
        if (executor == null) {
            executor = new QueryExecutor(loader);
            executor.initialize();
        }
        
        return executor.execute(query);
    }

    /**
     * 验证查询
     */
    private void validateQuery(OntologyQuery query) throws Exception {
        if (query.getFrom() == null || query.getFrom().isEmpty()) {
            throw new IllegalArgumentException("Query must specify 'from' object type");
        }
        
        // 验证对象类型是否存在
        try {
            loader.getObjectType(query.getFrom());
        } catch (Loader.NotFoundException e) {
            throw new IllegalArgumentException("Object type '" + query.getFrom() + "' not found");
        }
    }
}

