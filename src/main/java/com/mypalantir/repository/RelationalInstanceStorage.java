package com.mypalantir.repository;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.query.QueryExecutor;
import com.mypalantir.service.DatabaseMetadataService;
import com.mypalantir.service.MappingService;
import com.mypalantir.service.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * 关系型数据库实例存储实现
 * 从关系型数据库查询实例详细数据
 */
@Component
public class RelationalInstanceStorage implements IInstanceStorage {
    private static final Logger logger = LoggerFactory.getLogger(RelationalInstanceStorage.class);

    @Autowired
    @Lazy
    private QueryService queryService;

    @Autowired
    private Loader loader;


    @Override
    public String createInstance(String objectType, Map<String, Object> data) throws IOException {
        // 关系型数据库的创建应该通过ETL或直接SQL插入
        // 这里暂时不支持，应该通过ETL系统处理
        throw new IOException("RelationalInstanceStorage does not support direct instance creation. Please use ETL or direct SQL insertion.");
    }

    @Override
    public String createInstanceWithId(String objectType, String id, Map<String, Object> data) throws IOException {
        throw new IOException("RelationalInstanceStorage does not support direct instance creation. Please use ETL or direct SQL insertion.");
    }

    @Override
    public Map<String, Object> getInstance(String objectType, String id) throws IOException {
        try {
            ObjectType objectTypeDef = loader.getObjectType(objectType);
            
            // 检查是否配置了数据源映射
            // 如果没有配置，会使用默认数据库（从env配置读取）
            // QueryService会通过DatabaseMetadataService.getConnectionForDatabase(null)获取默认连接

            // 构建查询：SELECT * FROM table WHERE id = ?
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", objectType);
            
            // 选择所有属性
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
            // 注意：QueryService需要对象类型配置Mapping才能工作
            // 如果没有配置Mapping，QueryService会抛出异常
            // 默认数据库连接通过DatabaseMetadataService.getConnectionForDatabase(null)获取
            // 该连接使用application.properties中的db.*配置（从env读取）
            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            
            if (result.getRows().isEmpty()) {
                throw new IOException("instance not found");
            }

            return result.getRows().get(0);
        } catch (Loader.NotFoundException e) {
            throw new IOException("Object type not found: " + objectType, e);
        } catch (Exception e) {
            logger.error("Failed to get instance from relational database: {}", e.getMessage(), e);
            throw new IOException("Failed to get instance: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateInstance(String objectType, String id, Map<String, Object> data) throws IOException {
        // 关系型数据库的更新应该通过ETL或直接SQL更新
        throw new IOException("RelationalInstanceStorage does not support direct instance update. Please use ETL or direct SQL update.");
    }

    @Override
    public void deleteInstance(String objectType, String id) throws IOException {
        // 关系型数据库的删除应该通过ETL或直接SQL删除
        throw new IOException("RelationalInstanceStorage does not support direct instance deletion. Please use ETL or direct SQL deletion.");
    }

    @Override
    public InstanceStorage.ListResult listInstances(String objectType, int offset, int limit) throws IOException {
        try {
            ObjectType objectTypeDef = loader.getObjectType(objectType);
            
            // 如果没有配置数据源映射，会使用默认数据库（从env配置读取）
            // QueryService会通过DatabaseMetadataService.getConnectionForDatabase(null)获取默认连接

            // 构建查询
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", objectType);
            
            // 选择所有属性
            List<String> selectFields = new ArrayList<>();
            selectFields.add("id");
            if (objectTypeDef.getProperties() != null) {
                for (com.mypalantir.meta.Property prop : objectTypeDef.getProperties()) {
                    selectFields.add(prop.getName());
                }
            }
            queryMap.put("select", selectFields);
            
            queryMap.put("limit", limit);
            queryMap.put("offset", offset);

            // 执行查询
            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            
            // 获取总数（需要执行COUNT查询）
            long total = getTotalCount(objectType);
            
            return new InstanceStorage.ListResult(result.getRows(), total);
        } catch (Loader.NotFoundException e) {
            throw new IOException("Object type not found: " + objectType, e);
        } catch (Exception e) {
            logger.error("Failed to list instances from relational database: {}", e.getMessage(), e);
            throw new IOException("Failed to list instances: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> searchInstances(String objectType, Map<String, Object> filters) throws IOException {
        try {
            ObjectType objectTypeDef = loader.getObjectType(objectType);
            
            // 如果没有配置数据源映射，会使用默认数据库（从env配置读取）
            // QueryService会通过DatabaseMetadataService.getConnectionForDatabase(null)获取默认连接

            // 构建查询
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", objectType);
            
            // 选择所有属性
            List<String> selectFields = new ArrayList<>();
            selectFields.add("id");
            if (objectTypeDef.getProperties() != null) {
                for (com.mypalantir.meta.Property prop : objectTypeDef.getProperties()) {
                    selectFields.add(prop.getName());
                }
            }
            queryMap.put("select", selectFields);
            
            if (filters != null && !filters.isEmpty()) {
                queryMap.put("where", filters);
            }
            
            queryMap.put("limit", 10000); // 搜索时限制最大数量
            queryMap.put("offset", 0);

            // 执行查询
            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            
            return result.getRows();
        } catch (Loader.NotFoundException e) {
            throw new IOException("Object type not found: " + objectType, e);
        } catch (Exception e) {
            logger.error("Failed to search instances from relational database: {}", e.getMessage(), e);
            throw new IOException("Failed to search instances: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Map<String, Object>> getInstancesBatch(String objectType, List<String> ids) throws IOException {
        Map<String, Map<String, Object>> result = new HashMap<>();
        
        for (String id : ids) {
            try {
                Map<String, Object> instance = getInstance(objectType, id);
                result.put(id, instance);
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
            String objectType = entry.getKey();
            List<String> ids = entry.getValue();
            
            Map<String, Map<String, Object>> instances = getInstancesBatch(objectType, ids);
            for (Map.Entry<String, Map<String, Object>> instanceEntry : instances.entrySet()) {
                String key = objectType + ":" + instanceEntry.getKey();
                result.put(key, instanceEntry.getValue());
            }
        }
        
        return result;
    }

    /**
     * 获取实例总数
     * 注意：当前实现使用估算值，实际应该执行COUNT查询
     * TODO: 实现真正的COUNT查询
     */
    private long getTotalCount(String objectType) {
        try {
            // 执行一个查询来估算总数
            // 如果返回的行数等于limit，说明可能还有更多数据
            ObjectType objectTypeDef = loader.getObjectType(objectType);
            
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("from", objectType);
            queryMap.put("select", Collections.singletonList("id"));
            queryMap.put("limit", 10000);  // 使用较大的limit来估算
            queryMap.put("offset", 0);

            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
            int count = result.getRows().size();
            
            // 如果返回的行数等于limit，说明可能还有更多数据
            // 这里返回一个估算值，实际应该执行COUNT查询
            if (count == 10000) {
                // 可能还有更多，返回一个较大的估算值
                return count + 1000;  // 估算值
            }
            
            return count;
        } catch (Exception e) {
            logger.warn("Failed to get total count for object type {}: {}", objectType, e.getMessage());
            return 0;
        }
    }
}

