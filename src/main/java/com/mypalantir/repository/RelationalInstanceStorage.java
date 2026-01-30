package com.mypalantir.repository;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.service.DatabaseMetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 关系型数据库实例存储实现
 * 从关系型数据库查询实例详细数据
 */
@Component
public class RelationalInstanceStorage implements IInstanceStorage {
    private static final Logger logger = LoggerFactory.getLogger(RelationalInstanceStorage.class);

    @Autowired
    private Loader loader;

    @Autowired
    @Lazy
    private DatabaseMetadataService databaseMetadataService;

    @Autowired
    private Environment environment;


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

    /**
     * 检查是否为系统对象类型（table, database, mapping等）
     * 系统对象类型不应该通过 RelationalInstanceStorage 查询，避免递归调用
     */
    private boolean isSystemObjectType(String objectType) {
        if (objectType == null) {
            return false;
        }
        String lowerType = objectType.toLowerCase();
        return "table".equals(lowerType) 
            || "database".equals(lowerType) 
            || "mapping".equals(lowerType)
            || "column".equals(lowerType)
            || "workspace".equals(lowerType);
    }

    @Override
    public Map<String, Object> getInstance(String objectType, String id) throws IOException {
        logger.info("[RelationalInstanceStorage] getInstance called for objectType: {}, id: {}", objectType, id);
        
        // 系统对象类型不应该通过 RelationalInstanceStorage 查询，避免递归调用
        if (isSystemObjectType(objectType)) {
            logger.warn("[RelationalInstanceStorage] System object type {} should not be queried through RelationalInstanceStorage, this may cause recursion. Throwing IOException.", objectType);
            throw new IOException("System object type '" + objectType + "' should not be queried through RelationalInstanceStorage to avoid recursion. Please use Neo4j storage directly.");
        }
        
        try {
            ObjectType objectTypeDef = loader.getObjectType(objectType);
            
            // 1. 优先尝试查询同步表（表名 = 模型名，在默认数据库中）
            String syncTableName = objectType.toLowerCase();
            logger.info("[RelationalInstanceStorage] Attempting to query sync table: tableName = {}, databaseId = null (using default database from application.properties: host={}, port={}, name={})", 
                syncTableName,
                environment.getProperty("db.host", "localhost"),
                environment.getProperty("db.port", "3306"),
                environment.getProperty("db.name", ""));
            
            try {
                Map<String, Object> instance = getInstanceFromSyncTable(syncTableName, id, objectTypeDef);
                logger.info("[RelationalInstanceStorage] Successfully retrieved instance from sync table {}", syncTableName);
                return instance;
            } catch (SQLException e) {
                // 如果查询失败（表不存在或其他SQL错误），直接返回空结果，不查询映射表
                // 同步表不存在或查询结果为空是正常情况，不需要回退到映射表
                logger.info("[RelationalInstanceStorage] Failed to query sync table {} for instance {} (table may not exist or query returned empty): {}, returning empty result", 
                    syncTableName, id, e.getMessage());
                throw new IOException("instance not found");
            } catch (IOException e) {
                // getInstanceFromSyncTable 内部可能抛出 IOException（instance not found）
                // 这也是正常情况，直接抛出
                logger.info("[RelationalInstanceStorage] Instance not found in sync table {}: {}", syncTableName, e.getMessage());
                throw e;
            } catch (Exception e) {
                // 其他异常也直接返回空结果，不查询映射表
                logger.warn("[RelationalInstanceStorage] Error querying sync table {} for instance {}: {}, returning empty result", 
                    syncTableName, id, e.getMessage());
                throw new IOException("instance not found: " + e.getMessage(), e);
            }
        } catch (Loader.NotFoundException e) {
            logger.error("[RelationalInstanceStorage] Object type not found: {}", objectType);
            throw new IOException("Object type not found: " + objectType, e);
        } catch (Exception e) {
            logger.error("[RelationalInstanceStorage] Failed to get instance from relational database: {}", e.getMessage(), e);
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

    /**
     * 查询实例存储（同步数据）
     * 严格查询界限：只查询同步表，不查询原始表（映射表）
     * 同步表：表名 = 模型名（小写），在默认数据库中
     */
    @Override
    public InstanceStorage.ListResult listInstances(String objectType, int offset, int limit) throws IOException {
        logger.info("[RelationalInstanceStorage] ========== INSTANCE STORAGE QUERY (同步数据查询) ==========");
        logger.info("[RelationalInstanceStorage] Query mode: INSTANCE_STORAGE (同步表查询)");
        logger.info("[RelationalInstanceStorage] listInstances called for objectType: {}, offset: {}, limit: {}", 
            objectType, offset, limit);
        logger.info("[RelationalInstanceStorage] Data source: SYNC TABLE (同步表) only, NOT querying ORIGINAL TABLE (原始表)");
        
        try {
            ObjectType objectTypeDef = loader.getObjectType(objectType);
            
            // 严格查询界限：只查询同步表（表名 = 模型名，在默认数据库中）
            // 不查询原始表（原始表查询应该通过 MappedDataService.queryMappedInstances() 进行）
            // 使用 null 表示默认数据库，会从 application.properties 的 db.* 配置读取
            String syncTableName = objectType.toLowerCase();
            logger.info("[RelationalInstanceStorage] Querying SYNC TABLE: tableName = {}, databaseId = null (using default database from application.properties: host={}, port={}, name={})", 
                syncTableName,
                environment.getProperty("db.host", "localhost"),
                environment.getProperty("db.port", "3306"),
                environment.getProperty("db.name", ""));
            
            // 直接尝试查询同步表，如果查询失败直接返回空结果
            // 不查询原始表（映射表）
            try {
                InstanceStorage.ListResult result = listInstancesFromSyncTable(syncTableName, objectTypeDef, offset, limit);
                logger.info("[RelationalInstanceStorage] Successfully queried SYNC TABLE {}, returned {} instances (items: {}, total: {})", 
                    syncTableName, result.getItems().size(), result.getItems().size(), result.getTotal());
                
                // 详细分析返回的数据
                if (result.getItems().isEmpty()) {
                    logger.info("[RelationalInstanceStorage] DATA SOURCE ANALYSIS: SYNC TABLE {} is EMPTY for objectType={} - no data in sync table", 
                        syncTableName, objectType);
                } else {
                    logger.info("[RelationalInstanceStorage] DATA SOURCE ANALYSIS: SYNC TABLE {} has {} instances for objectType={} - data EXISTS in sync table, first instance id={}", 
                        syncTableName, result.getItems().size(), objectType,
                        result.getItems().isEmpty() ? "N/A" : result.getItems().get(0).get("id"));
                }
                logger.info("[RelationalInstanceStorage] ========== INSTANCE STORAGE QUERY END ==========");
                
                return result;
            } catch (SQLException e) {
                // 如果查询失败（表不存在或其他SQL错误），直接返回空结果
                // 严格查询界限：不查询原始表（映射表）
                // 同步表不存在或查询结果为空是正常情况，不需要回退到原始表
                logger.info("[RelationalInstanceStorage] Failed to query SYNC TABLE {} (table may not exist or query returned empty): {}, returning empty result", 
                    syncTableName, e.getMessage());
                logger.info("[RelationalInstanceStorage] DATA SOURCE ANALYSIS: SYNC TABLE {} does NOT EXIST or query failed for objectType={}, returning EMPTY result, NOT querying ORIGINAL TABLE", 
                    syncTableName, objectType);
                logger.info("[RelationalInstanceStorage] ========== INSTANCE STORAGE QUERY END ==========");
                return new InstanceStorage.ListResult(new ArrayList<>(), 0);
            } catch (Exception e) {
                // 其他异常也直接返回空结果，不查询原始表
                logger.warn("[RelationalInstanceStorage] Error querying SYNC TABLE {}: {}, returning empty result", 
                    syncTableName, e.getMessage());
                logger.info("[RelationalInstanceStorage] DATA SOURCE ANALYSIS: Exception querying SYNC TABLE {} for objectType={}, returning EMPTY result, NOT querying ORIGINAL TABLE", 
                    syncTableName, objectType);
                logger.info("[RelationalInstanceStorage] ========== INSTANCE STORAGE QUERY END ==========");
                return new InstanceStorage.ListResult(new ArrayList<>(), 0);
            }
        } catch (Loader.NotFoundException e) {
            logger.error("[RelationalInstanceStorage] Object type not found: {}", objectType);
            throw new IOException("Object type not found: " + objectType, e);
        } catch (Exception e) {
            logger.error("[RelationalInstanceStorage] Failed to list instances from relational database: {}", 
                e.getMessage(), e);
            throw new IOException("Failed to list instances: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> searchInstances(String objectType, Map<String, Object> filters) throws IOException {
        logger.info("[RelationalInstanceStorage] searchInstances called for objectType: {}, filters: {}", objectType, filters);
        
        try {
            ObjectType objectTypeDef = loader.getObjectType(objectType);
            
            // 1. 优先尝试查询同步表（表名 = 模型名，在默认数据库中）
            String syncTableName = objectType.toLowerCase();
            logger.info("[RelationalInstanceStorage] Attempting to search sync table: tableName = {}, databaseId = null (using default database from application.properties: host={}, port={}, name={})", 
                syncTableName,
                environment.getProperty("db.host", "localhost"),
                environment.getProperty("db.port", "3306"),
                environment.getProperty("db.name", ""));
            
            try {
                List<Map<String, Object>> results = searchInstancesFromSyncTable(syncTableName, objectTypeDef, filters);
                logger.info("[RelationalInstanceStorage] Successfully searched sync table {}, returned {} instances", 
                    syncTableName, results.size());
                return results;
            } catch (SQLException e) {
                // 如果查询失败（表不存在或其他SQL错误），直接返回空结果，不查询映射表
                // 同步表不存在或查询结果为空是正常情况，不需要回退到映射表
                logger.info("[RelationalInstanceStorage] Failed to search sync table {} (table may not exist or query returned empty): {}, returning empty result", 
                    syncTableName, e.getMessage());
                return new ArrayList<>();
            } catch (Exception e) {
                // 其他异常也直接返回空结果，不查询映射表
                logger.warn("[RelationalInstanceStorage] Error searching sync table {}: {}, returning empty result", 
                    syncTableName, e.getMessage());
                return new ArrayList<>();
            }
        } catch (Loader.NotFoundException e) {
            logger.error("[RelationalInstanceStorage] Object type not found: {}", objectType);
            throw new IOException("Object type not found: " + objectType, e);
        } catch (Exception e) {
            logger.error("[RelationalInstanceStorage] Failed to search instances from relational database: {}", e.getMessage(), e);
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
     * 从同步表获取单个实例
     * 同步表：表名 = 模型名（小写），在默认数据库中
     */
    private Map<String, Object> getInstanceFromSyncTable(String tableName, String id, ObjectType objectTypeDef) 
            throws IOException, SQLException {
        logger.info("[RelationalInstanceStorage] getInstanceFromSyncTable: tableName = {}, id = {}, databaseId = null (default database)", 
            tableName, id);
        
        Connection conn = databaseMetadataService.getConnectionForDatabase(null); // 默认数据库
        logger.info("[RelationalInstanceStorage] Got connection to default database for sync table query");
        
        try {
            String sql = "SELECT * FROM `" + tableName + "` WHERE `id` = ?";
            logger.info("[RelationalInstanceStorage] Executing SQL: {} with parameter id = {}", sql, id);
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> instance = new HashMap<>();
                        // 获取所有列
                        java.sql.ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        logger.info("[RelationalInstanceStorage] ResultSet has {} columns", columnCount);
                        
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            instance.put(columnName, value);
                        }
                        
                        logger.info("[RelationalInstanceStorage] Successfully retrieved instance from sync table {} with id {}", 
                            tableName, id);
                        return instance;
                    } else {
                        logger.warn("[RelationalInstanceStorage] Instance not found in sync table {} with id {}", tableName, id);
                        throw new IOException("instance not found");
                    }
                }
            }
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                logger.info("[RelationalInstanceStorage] Closed connection to default database");
            }
        }
    }

    /**
     * 从同步表列表查询实例
     * 严格查询界限：只查询同步表，不查询原始表
     */
    private InstanceStorage.ListResult listInstancesFromSyncTable(String tableName, ObjectType objectTypeDef, 
                                                                  int offset, int limit) 
            throws IOException, SQLException {
        logger.info("[RelationalInstanceStorage] ========== listInstancesFromSyncTable START ==========");
        logger.info("[RelationalInstanceStorage] listInstancesFromSyncTable: tableName = {}, offset = {}, limit = {}", 
            tableName, offset, limit);
        logger.info("[RelationalInstanceStorage] STRICT QUERY BOUNDARY: Querying SYNC TABLE only, NOT querying ORIGINAL TABLE");
        logger.info("[RelationalInstanceStorage] Sync table name: {} (derived from objectType: {})", 
            tableName, objectTypeDef != null ? objectTypeDef.getName() : "unknown");
        logger.info("[RelationalInstanceStorage] Database: default database (from application.properties)");
        
        Connection conn = databaseMetadataService.getConnectionForDatabase(null); // 默认数据库
        logger.info("[RelationalInstanceStorage] Got connection to default database for sync table query");
        
        try {
            // 查询数据（严格查询界限：只查询同步表）
            String sql = "SELECT * FROM `" + tableName + "` LIMIT ? OFFSET ?";
            logger.info("[RelationalInstanceStorage] Executing SQL on SYNC TABLE: {}", sql);
            logger.info("[RelationalInstanceStorage] Table name verification: {} (should match objectType.toLowerCase())", tableName);
            
            List<Map<String, Object>> instances = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                pstmt.setInt(2, offset);
                logger.info("[RelationalInstanceStorage] PreparedStatement parameters: limit = {}, offset = {}", limit, offset);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    java.sql.ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    logger.info("[RelationalInstanceStorage] ResultSet has {} columns from SYNC TABLE {}", columnCount, tableName);
                    
                    int rowCount = 0;
                    while (rs.next()) {
                        Map<String, Object> instance = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            instance.put(columnName, value);
                        }
                        instances.add(instance);
                        rowCount++;
                    }
                    logger.info("[RelationalInstanceStorage] Retrieved {} rows from SYNC TABLE {}", rowCount, tableName);
                    
                    // 详细分析查询结果
                    if (rowCount == 0) {
                        logger.info("[RelationalInstanceStorage] DATA SOURCE ANALYSIS: SYNC TABLE {} query returned 0 rows - table exists but is EMPTY", tableName);
                        logger.info("[RelationalInstanceStorage] DATA SOURCE VERIFICATION: No data in SYNC TABLE {}, will return empty result", tableName);
                    } else {
                        logger.info("[RelationalInstanceStorage] DATA SOURCE ANALYSIS: SYNC TABLE {} query returned {} rows - DATA EXISTS in sync table, first row id={}", 
                            tableName, rowCount, instances.isEmpty() ? "N/A" : instances.get(0).get("id"));
                        logger.info("[RelationalInstanceStorage] DATA SOURCE VERIFICATION: Data confirmed from SYNC TABLE {}, NOT from ORIGINAL TABLE", tableName);
                    }
                }
            }
            
            // 获取总数（严格查询界限：只查询同步表）
            logger.info("[RelationalInstanceStorage] Getting total count from SYNC TABLE {}", tableName);
            long total = getTotalCountFromSyncTable(tableName);
            logger.info("[RelationalInstanceStorage] Total count from SYNC TABLE {}: {} (retrieved instances: {})", 
                tableName, total, instances.size());
            
            // 最终数据源分析
            logger.info("[RelationalInstanceStorage] DATA SOURCE ANALYSIS FINAL: objectType={}, syncTable={}, instancesRetrieved={}, totalCount={}, dataSource=SYNC_TABLE_ONLY", 
                objectTypeDef != null ? objectTypeDef.getName() : "unknown", tableName, instances.size(), total);
            logger.info("[RelationalInstanceStorage] DATA SOURCE VERIFICATION: All data from SYNC TABLE {}, NOT from ORIGINAL TABLE", tableName);
            logger.info("[RelationalInstanceStorage] ========== listInstancesFromSyncTable END ==========");
            
            return new InstanceStorage.ListResult(instances, total);
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                logger.info("[RelationalInstanceStorage] Closed connection to default database");
            }
        }
    }

    /**
     * 从同步表搜索实例
     */
    private List<Map<String, Object>> searchInstancesFromSyncTable(String tableName, ObjectType objectTypeDef, 
                                                                     Map<String, Object> filters) 
            throws IOException, SQLException {
        logger.info("[RelationalInstanceStorage] searchInstancesFromSyncTable: tableName = {}, filters = {}, databaseId = null (default database)", 
            tableName, filters);
        
        Connection conn = databaseMetadataService.getConnectionForDatabase(null); // 默认数据库
        logger.info("[RelationalInstanceStorage] Got connection to default database for sync table search");
        
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM `").append(tableName).append("`");
            
            // 构建WHERE条件
            if (filters != null && !filters.isEmpty()) {
                sql.append(" WHERE ");
                List<String> conditions = new ArrayList<>();
                int paramIndex = 1;
                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    conditions.add("`" + entry.getKey() + "` = ?");
                }
                sql.append(String.join(" AND ", conditions));
            }
            
            sql.append(" LIMIT 10000"); // 搜索时限制最大数量
            
            logger.info("[RelationalInstanceStorage] Executing SQL: {}", sql.toString());
            
            List<Map<String, Object>> instances = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                // 设置参数
                if (filters != null && !filters.isEmpty()) {
                    int paramIndex = 1;
                    for (Map.Entry<String, Object> entry : filters.entrySet()) {
                        pstmt.setObject(paramIndex++, entry.getValue());
                        logger.info("[RelationalInstanceStorage] Set parameter {}: {} = {}", paramIndex - 1, entry.getKey(), entry.getValue());
                    }
                }
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    java.sql.ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    logger.info("[RelationalInstanceStorage] ResultSet has {} columns", columnCount);
                    
                    int rowCount = 0;
                    while (rs.next()) {
                        Map<String, Object> instance = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            instance.put(columnName, value);
                        }
                        instances.add(instance);
                        rowCount++;
                    }
                    logger.info("[RelationalInstanceStorage] Retrieved {} rows from sync table {} with filters", rowCount, tableName);
                }
            }
            
            return instances;
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                logger.info("[RelationalInstanceStorage] Closed connection to default database");
            }
        }
    }

    /**
     * 获取同步表的总记录数
     */
    private long getTotalCountFromSyncTable(String tableName) throws SQLException, IOException {
        String sql = "SELECT COUNT(*) FROM `" + tableName + "`";
        logger.info("[RelationalInstanceStorage] getTotalCountFromSyncTable: executing SQL: {}", sql);
        
        Connection conn = databaseMetadataService.getConnectionForDatabase(null); // 默认数据库
        try {
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long count = rs.getLong(1);
                    logger.info("[RelationalInstanceStorage] getTotalCountFromSyncTable: count = {}", count);
                    return count;
                }
                logger.warn("[RelationalInstanceStorage] getTotalCountFromSyncTable: no result returned");
                return 0;
            }
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
    }
}

