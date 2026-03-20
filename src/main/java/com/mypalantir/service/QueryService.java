package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.query.ExecutionRouter;
import com.mypalantir.query.FederatedCalciteRunner;
import com.mypalantir.query.OntologyQuery;
import com.mypalantir.query.QueryExecutor;
import com.mypalantir.query.QueryParser;
import com.mypalantir.repository.IInstanceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(QueryService.class);
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
     * 执行查询（Map 形式的 DSL）
     */
    public QueryExecutor.QueryResult executeQuery(Map<String, Object> queryMap) throws Exception {
        OntologyQuery query = parser.parseMap(queryMap);
        return executeQuery(query);
    }

    /**
     * 执行查询（直接使用 OntologyQuery，避免 Map → OntologyQuery 的信息丢失）
     */
    public QueryExecutor.QueryResult executeQuery(OntologyQuery query) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Parsed OntologyQuery: object={}, select={}, filter={}, links={}, groupBy={}, metrics={}, limit={}",
                query.getFrom(), query.getSelect(), query.getFilter(),
                query.getLinks() != null ? query.getLinks().size() : 0,
                query.getGroupBy(), query.getMetrics(), query.getLimit());
        }

        validateQuery(query);

        // 路由决策
        ExecutionRouter.ExecutionMode mode = executionRouter.route(query);

        if (mode == ExecutionRouter.ExecutionMode.FEDERATED) {
            if (federatedRunner == null) {
                federatedRunner = new FederatedCalciteRunner(loader, instanceStorage, mappingService, databaseMetadataService);
            }
            return federatedRunner.execute(query);
        } else {
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
