package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.query.OntologyQuery;
import com.mypalantir.query.QueryExecutor;
import com.mypalantir.query.QueryParser;
import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

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
    private QueryExecutor executor;

    @Autowired
    public QueryService(Loader loader, IInstanceStorage instanceStorage,
                       MappingService mappingService, DatabaseMetadataService databaseMetadataService) {
        this.loader = loader;
        this.instanceStorage = instanceStorage;
        this.mappingService = mappingService;
        this.databaseMetadataService = databaseMetadataService;
        this.parser = new QueryParser();
    }

    /**
     * 执行查询
     */
    public QueryExecutor.QueryResult executeQuery(Map<String, Object> queryMap) throws Exception {
        // 解析查询
        OntologyQuery query = parser.parseMap(queryMap);
        
        // 调试：打印解析后的查询
        System.out.println("=== Parsed OntologyQuery ===");
        System.out.println("From: " + query.getFrom());
        System.out.println("Select: " + query.getSelect());
        if (query.getLinks() != null && !query.getLinks().isEmpty()) {
            System.out.println("Links:");
            for (OntologyQuery.LinkQuery linkQuery : query.getLinks()) {
                System.out.println("  - Name: " + linkQuery.getName());
                System.out.println("    Select: " + linkQuery.getSelect());
            }
        }
        System.out.println("Limit: " + query.getLimit());
        System.out.println("============================");
        
        // 验证查询
        validateQuery(query);
        
        // 执行查询
        if (executor == null) {
            executor = new QueryExecutor(loader, instanceStorage, mappingService, databaseMetadataService);
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

