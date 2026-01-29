package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.query.ExecutionRouter;
import com.mypalantir.query.FederatedCalciteRunner;
import com.mypalantir.query.OntologyQuery;
import com.mypalantir.query.QueryExecutor;
import com.mypalantir.query.QueryParser;
import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * 查询服务
 */
@Service
public class QueryService {
    private final Loader loader;
    private final QueryParser parser;
    private final IInstanceStorage instanceStorage;
    private final MappingService mappingService;
    private final DatabaseMetadataService databaseMetadataService;
    private final ExecutionRouter executionRouter;
    private QueryExecutor executor;
    private FederatedCalciteRunner federatedRunner;

    @Autowired
    public QueryService(Loader loader, @Lazy IInstanceStorage instanceStorage,
                       MappingService mappingService, DatabaseMetadataService databaseMetadataService,
                       ExecutionRouter executionRouter) {
        this.loader = loader;
        this.instanceStorage = instanceStorage;
        this.mappingService = mappingService;
        this.databaseMetadataService = databaseMetadataService;
        this.executionRouter = executionRouter;
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
        
        // 路由决策
        ExecutionRouter.ExecutionMode mode = executionRouter.route(query);
        System.out.println("Execution Mode: " + mode);

        if (mode == ExecutionRouter.ExecutionMode.FEDERATED) {
            if (federatedRunner == null) {
                federatedRunner = new FederatedCalciteRunner(loader, instanceStorage, mappingService, databaseMetadataService);
            }
            return federatedRunner.execute(query);
        } else {
            // 单源执行
            if (executor == null) {
                executor = new QueryExecutor(loader, instanceStorage, mappingService, databaseMetadataService);
                executor.initialize();
            }
            
            return executor.execute(query);
        }
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

    /**
     * 根据ID查询实例（用于RelationalInstanceStorage）
     */
    public Map<String, Object> queryInstanceById(String objectType, String id) throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("from", objectType);
        
        // 选择所有属性
        com.mypalantir.meta.ObjectType objectTypeDef = loader.getObjectType(objectType);
        List<String> selectFields = new ArrayList<>();
        selectFields.add("id");
        if (objectTypeDef.getProperties() != null) {
            for (com.mypalantir.meta.Property prop : objectTypeDef.getProperties()) {
                selectFields.add(prop.getName());
            }
        }
        queryMap.put("select", selectFields);
        
        // WHERE条件
        Map<String, Object> where = new HashMap<>();
        where.put("id", id);
        queryMap.put("where", where);
        
        queryMap.put("limit", 1);
        queryMap.put("offset", 0);

        // 执行查询
        QueryExecutor.QueryResult result = executeQuery(queryMap);
        
        if (result.getRows().isEmpty()) {
            throw new IOException("instance not found");
        }

        return result.getRows().get(0);
    }
}

