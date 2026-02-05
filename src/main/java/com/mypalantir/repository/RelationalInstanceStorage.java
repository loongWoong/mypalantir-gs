package com.mypalantir.repository;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.service.DatabaseMetadataService;
import com.mypalantir.service.MappingService;
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

    @Autowired
    @Lazy
    private MappingService mappingService;


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
            // 动态获取主键列名
            String primaryKeyColumn = getPrimaryKeyColumnFromTable(null, tableName);
            if (primaryKeyColumn == null || primaryKeyColumn.isEmpty()) {
                // 如果无法从表结构获取主键，尝试从 mapping 配置获取
                primaryKeyColumn = getPrimaryKeyColumn(objectTypeDef);
                if (primaryKeyColumn == null || primaryKeyColumn.isEmpty()) {
                    // 最后回退到默认的 'id'
                    primaryKeyColumn = "id";
                    logger.warn("[RelationalInstanceStorage] Could not determine primary key column for table {}, using default 'id'", tableName);
                } else {
                    logger.info("[RelationalInstanceStorage] Using primary key column from mapping: {}", primaryKeyColumn);
                }
            } else {
                logger.info("[RelationalInstanceStorage] Using primary key column from table metadata: {}", primaryKeyColumn);
            }
            
            String sql = "SELECT * FROM `" + tableName + "` WHERE `" + primaryKeyColumn + "` = ?";
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
                        
                        // 构建列名到属性名的映射
                        Map<String, String> columnToPropertyMap = buildColumnToPropertyMap(objectTypeDef, tableName);
                        
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            
                            // 将列名转换为属性名
                            String propertyName = convertColumnNameToPropertyName(columnName, columnToPropertyMap, objectTypeDef);
                            instance.put(propertyName, value);
                        }
                        
                        // 如果 ID 不存在或为空，优先通过 mapping 设置的主键作为 ID
                        Object idValue = instance.get("id");
                        if (idValue == null || (idValue instanceof String && ((String) idValue).trim().isEmpty())) {
                            String idFromPrimaryKey = getIdFromPrimaryKey(instance, primaryKeyColumn, columnToPropertyMap);
                            if (idFromPrimaryKey != null && !idFromPrimaryKey.trim().isEmpty()) {
                                instance.put("id", idFromPrimaryKey);
                                logger.info("[RelationalInstanceStorage] ID not found in instance data, using primary key column '{}' value as ID: {}", 
                                    primaryKeyColumn, idFromPrimaryKey);
                            } else {
                                // 如果主键列也无法获取ID，使用查询参数中的id
                                instance.put("id", id);
                                logger.info("[RelationalInstanceStorage] ID not found in instance data and primary key, using query parameter as ID: {}", id);
                            }
                        }
                        
                        logger.info("[RelationalInstanceStorage] Successfully retrieved instance from sync table {} with id {}", 
                            tableName, instance.get("id"));
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
     * 从数据库表元数据中获取主键列名
     * @param databaseId 数据库ID（null表示默认数据库）
     * @param tableName 表名
     * @return 主键列名，如果不存在或无法获取则返回 null
     */
    private String getPrimaryKeyColumnFromTable(String databaseId, String tableName) {
        try {
            List<Map<String, Object>> columns = databaseMetadataService.getColumns(databaseId, tableName);
            for (Map<String, Object> column : columns) {
                Boolean isPrimaryKey = (Boolean) column.get("is_primary_key");
                if (isPrimaryKey != null && isPrimaryKey) {
                    String columnName = (String) column.get("name");
                    logger.debug("[RelationalInstanceStorage] Found primary key column: {} for table {}", columnName, tableName);
                    return columnName;
                }
            }
            logger.debug("[RelationalInstanceStorage] No primary key column found for table {}", tableName);
            return null;
        } catch (Exception e) {
            logger.warn("[RelationalInstanceStorage] Failed to get primary key column from table {}: {}", tableName, e.getMessage());
            return null;
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
        
        // 记录当前连接的数据库信息，用于诊断
        String currentDb = null;
        try {
            currentDb = conn.getCatalog();
            logger.info("[RelationalInstanceStorage] Current database (catalog): {}", currentDb);
        } catch (SQLException e) {
            logger.warn("[RelationalInstanceStorage] Failed to get current database: {}", e.getMessage());
        }
        
        // 确保在正确的数据库中查询（显式使用 USE DATABASE）
        if (currentDb != null && !currentDb.isEmpty()) {
            try {
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("USE `" + currentDb + "`");
                    logger.info("[RelationalInstanceStorage] Explicitly switched to database: {}", currentDb);
                }
            } catch (SQLException e) {
                logger.warn("[RelationalInstanceStorage] Failed to switch to database {}: {}", currentDb, e.getMessage());
            }
        }
        
        // 诊断：检查表是否存在（尝试不同的表名大小写组合）
        try {
            try (java.sql.Statement stmt = conn.createStatement()) {
                // 检查表是否存在（使用 SHOW TABLES）
                String showTablesSql = "SHOW TABLES LIKE '" + tableName + "'";
                logger.info("[RelationalInstanceStorage] Checking if table exists: {}", showTablesSql);
                try (java.sql.ResultSet rs = stmt.executeQuery(showTablesSql)) {
                    if (rs.next()) {
                        String actualTableName = rs.getString(1);
                        logger.info("[RelationalInstanceStorage] Table found with name: {} (requested: {})", actualTableName, tableName);
                        // 如果实际表名与请求的表名不同（大小写不同），使用实际表名
                        if (!actualTableName.equals(tableName)) {
                            logger.warn("[RelationalInstanceStorage] Table name case mismatch! Using actual table name: {}", actualTableName);
                            tableName = actualTableName;
                        }
                    } else {
                        logger.warn("[RelationalInstanceStorage] Table '{}' not found in database '{}' using SHOW TABLES", tableName, currentDb);
                        // 尝试查找所有表（用于诊断）
                        try (java.sql.ResultSet allTables = stmt.executeQuery("SHOW TABLES")) {
                            java.util.List<String> existingTables = new java.util.ArrayList<>();
                            while (allTables.next()) {
                                existingTables.add(allTables.getString(1));
                            }
                            logger.warn("[RelationalInstanceStorage] Existing tables in database '{}': {}", currentDb, existingTables);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.debug("[RelationalInstanceStorage] Failed to check table existence: {}", e.getMessage());
        }
        
        try {
            // 查询数据（严格查询界限：只查询同步表）
            // 使用实际表名（可能已根据大小写调整）
            String sql = "SELECT * FROM `" + tableName + "` LIMIT ? OFFSET ?";
            logger.info("[RelationalInstanceStorage] Executing SQL on SYNC TABLE: {}", sql);
            logger.info("[RelationalInstanceStorage] Table name verification: {} (should match objectType.toLowerCase())", tableName);
            
            // 打印最终执行的SQL（参数替换后）
            String finalSql = "SELECT * FROM `" + tableName + "` LIMIT " + limit + " OFFSET " + offset;
            logger.info("[RelationalInstanceStorage] Final executed SQL (for verification): {}", finalSql);
            
            List<Map<String, Object>> instances = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                pstmt.setInt(2, offset);
                logger.info("[RelationalInstanceStorage] PreparedStatement parameters: limit = {}, offset = {}", limit, offset);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    java.sql.ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    logger.info("[RelationalInstanceStorage] ResultSet has {} columns from SYNC TABLE {}", columnCount, tableName);
                    
                    // 构建列名到属性名的映射
                    Map<String, String> columnToPropertyMap = buildColumnToPropertyMap(objectTypeDef, tableName);
                    
                    // 获取主键列名（用于在 ID 不存在时获取 ID）
                    String primaryKeyColumn = getPrimaryKeyColumn(objectTypeDef);
                    
                    int rowCount = 0;
                    while (rs.next()) {
                        Map<String, Object> instance = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            
                            // 将列名转换为属性名
                            String propertyName = convertColumnNameToPropertyName(columnName, columnToPropertyMap, objectTypeDef);
                            instance.put(propertyName, value);
                        }
                        
                        // 如果 ID 不存在或为空，通过主键映射获取 ID
                        Object idValue = instance.get("id");
                        if (idValue == null || (idValue instanceof String && ((String) idValue).trim().isEmpty())) {
                            String idFromPrimaryKey = getIdFromPrimaryKey(instance, primaryKeyColumn, columnToPropertyMap);
                            if (idFromPrimaryKey != null && !idFromPrimaryKey.trim().isEmpty()) {
                                instance.put("id", idFromPrimaryKey);
                                logger.debug("[RelationalInstanceStorage] ID not found, using primary key column '{}' value as ID: {}", 
                                    primaryKeyColumn, idFromPrimaryKey);
                            }
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
                    
                    // 构建列名到属性名的映射
                    Map<String, String> columnToPropertyMap = buildColumnToPropertyMap(objectTypeDef, tableName);
                    
                    // 获取主键列名（用于在 ID 不存在时获取 ID）
                    String primaryKeyColumn = getPrimaryKeyColumn(objectTypeDef);
                    
                    int rowCount = 0;
                    while (rs.next()) {
                        Map<String, Object> instance = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            
                            // 将列名转换为属性名
                            String propertyName = convertColumnNameToPropertyName(columnName, columnToPropertyMap, objectTypeDef);
                            instance.put(propertyName, value);
                        }
                        
                        // 如果 ID 不存在或为空，通过主键映射获取 ID
                        Object idValue = instance.get("id");
                        if (idValue == null || (idValue instanceof String && ((String) idValue).trim().isEmpty())) {
                            String idFromPrimaryKey = getIdFromPrimaryKey(instance, primaryKeyColumn, columnToPropertyMap);
                            if (idFromPrimaryKey != null && !idFromPrimaryKey.trim().isEmpty()) {
                                instance.put("id", idFromPrimaryKey);
                                logger.debug("[RelationalInstanceStorage] ID not found, using primary key column '{}' value as ID: {}", 
                                    primaryKeyColumn, idFromPrimaryKey);
                            }
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

    /**
     * 构建列名到属性名的映射
     * 从映射关系中获取列名到属性名的对应关系
     */
    private Map<String, String> buildColumnToPropertyMap(ObjectType objectTypeDef, String tableName) {
        Map<String, String> columnToPropertyMap = new HashMap<>();
        
        if (objectTypeDef == null) {
            logger.warn("[RelationalInstanceStorage] objectTypeDef is null, cannot build column to property map");
            return columnToPropertyMap;
        }
        
        try {
            // 获取对象类型的所有映射关系
            List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectTypeDef.getName());
            
            if (mappings != null && !mappings.isEmpty()) {
                // 使用第一个映射关系（通常一个对象类型只有一个映射）
                Map<String, Object> mapping = mappings.get(0);
                @SuppressWarnings("unchecked")
                Map<String, String> columnPropertyMappings = (Map<String, String>) mapping.get("column_property_mappings");
                
                if (columnPropertyMappings != null) {
                    // column_property_mappings 的结构是 {列名: 属性名}
                    // 需要构建反向映射，同时处理大小写不敏感的情况
                    for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                        String columnName = entry.getKey();
                        String propertyName = entry.getValue();
                        
                        // 添加原始列名映射
                        columnToPropertyMap.put(columnName.toLowerCase(), propertyName);
                        columnToPropertyMap.put(columnName.toUpperCase(), propertyName);
                        columnToPropertyMap.put(columnName, propertyName);
                    }
                    
                    logger.info("[RelationalInstanceStorage] Built column to property map with {} entries for objectType {}", 
                        columnToPropertyMap.size(), objectTypeDef.getName());
                } else {
                    logger.info("[RelationalInstanceStorage] No column_property_mappings found in mapping for objectType {}", 
                        objectTypeDef.getName());
                }
            } else {
                logger.info("[RelationalInstanceStorage] No mappings found for objectType {}", objectTypeDef.getName());
            }
        } catch (Exception e) {
            logger.warn("[RelationalInstanceStorage] Failed to build column to property map: {}", e.getMessage());
        }
        
        return columnToPropertyMap;
    }

    /**
     * 获取主键列名（从 mapping 配置中）
     * @param objectTypeDef 对象类型定义
     * @return 主键列名，如果不存在则返回 null
     */
    private String getPrimaryKeyColumn(ObjectType objectTypeDef) {
        if (objectTypeDef == null) {
            return null;
        }
        
        try {
            // 获取对象类型的所有映射关系
            List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectTypeDef.getName());
            
            if (mappings != null && !mappings.isEmpty()) {
                // 使用第一个映射关系（通常一个对象类型只有一个映射）
                Map<String, Object> mapping = mappings.get(0);
                String primaryKeyColumn = (String) mapping.get("primary_key_column");
                
                if (primaryKeyColumn != null && !primaryKeyColumn.isEmpty()) {
                    logger.debug("[RelationalInstanceStorage] Found primary_key_column: {} for objectType {}", 
                        primaryKeyColumn, objectTypeDef.getName());
                    return primaryKeyColumn;
                }
            }
        } catch (Exception e) {
            logger.warn("[RelationalInstanceStorage] Failed to get primary key column: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * 从主键列获取 ID 值
     * @param instance 实例数据
     * @param primaryKeyColumn 主键列名
     * @param columnToPropertyMap 列名到属性名的映射
     * @return ID 值，如果不存在则返回 null
     */
    private String getIdFromPrimaryKey(Map<String, Object> instance, String primaryKeyColumn, 
                                       Map<String, String> columnToPropertyMap) {
        if (primaryKeyColumn == null || primaryKeyColumn.isEmpty()) {
            return null;
        }
        
        // 查找主键列对应的属性名
        String propertyName = columnToPropertyMap.get(primaryKeyColumn.toLowerCase());
        if (propertyName == null) {
            propertyName = columnToPropertyMap.get(primaryKeyColumn.toUpperCase());
        }
        if (propertyName == null) {
            propertyName = columnToPropertyMap.get(primaryKeyColumn);
        }
        // 如果映射中没有找到，直接使用主键列名作为属性名
        if (propertyName == null) {
            propertyName = primaryKeyColumn;
        }
        
        // 从实例中获取主键值
        Object primaryKeyValue = instance.get(propertyName);
        if (primaryKeyValue != null) {
            return String.valueOf(primaryKeyValue);
        }
        
        return null;
    }

    /**
     * 将数据库列名转换为对象类型的属性名
     */
    private String convertColumnNameToPropertyName(String columnName, Map<String, String> columnToPropertyMap, ObjectType objectTypeDef) {
        if (columnName == null || columnName.isEmpty()) {
            return columnName;
        }
        
        // 1. 首先尝试从映射中查找
        String propertyName = columnToPropertyMap.get(columnName.toLowerCase());
        if (propertyName != null) {
            return propertyName;
        }
        
        propertyName = columnToPropertyMap.get(columnName.toUpperCase());
        if (propertyName != null) {
            return propertyName;
        }
        
        propertyName = columnToPropertyMap.get(columnName);
        if (propertyName != null) {
            return propertyName;
        }
        
        // 2. 如果映射中没有找到，检查列名是否已经是属性名
        if (objectTypeDef != null && hasProperty(objectTypeDef, columnName)) {
            return columnName;
        }
        
        // 3. 尝试大小写不敏感匹配属性名
        String foundProperty = findPropertyNameByColumnName(objectTypeDef, columnName);
        if (foundProperty != null) {
            return foundProperty;
        }
        
        // 4. 如果都找不到，返回原始列名（可能已经是属性名，或者没有对应的属性）
        return columnName;
    }

    /**
     * 检查对象类型是否有指定的属性
     */
    private boolean hasProperty(ObjectType objectTypeDef, String propertyName) {
        if (objectTypeDef == null || propertyName == null) {
            return false;
        }
        
        if (objectTypeDef.getProperties() != null) {
            for (com.mypalantir.meta.Property prop : objectTypeDef.getProperties()) {
                if (propertyName.equals(prop.getName())) {
                    return true;
                }
            }
        }
        
        // 检查id属性（所有对象类型都有id属性）
        if ("id".equals(propertyName)) {
            return true;
        }
        
        return false;
    }

    /**
     * 通过列名查找属性名（大小写不敏感）
     */
    private String findPropertyNameByColumnName(ObjectType objectTypeDef, String columnName) {
        if (objectTypeDef == null || columnName == null) {
            return null;
        }
        
        if (objectTypeDef.getProperties() != null) {
            String lowerColumnName = columnName.toLowerCase();
            String upperColumnName = columnName.toUpperCase();
            
            for (com.mypalantir.meta.Property prop : objectTypeDef.getProperties()) {
                String propName = prop.getName();
                // 大小写不敏感匹配
                if (propName.equalsIgnoreCase(columnName)) {
                    return propName;
                }
                // 尝试下划线转驼峰匹配（如 trade_id -> tradeId）
                if (propName.equalsIgnoreCase(convertSnakeToCamel(columnName)) || 
                    columnName.equalsIgnoreCase(convertCamelToSnake(propName))) {
                    return propName;
                }
            }
        }
        
        return null;
    }

    /**
     * 将下划线命名转换为驼峰命名（如 trade_id -> tradeId）
     */
    private String convertSnakeToCamel(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }
        
        StringBuilder camelCase = new StringBuilder();
        boolean nextUpperCase = false;
        
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    camelCase.append(Character.toUpperCase(c));
                    nextUpperCase = false;
                } else {
                    camelCase.append(Character.toLowerCase(c));
                }
            }
        }
        
        return camelCase.toString();
    }

    /**
     * 将驼峰命名转换为下划线命名（如 tradeId -> trade_id）
     */
    private String convertCamelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        
        StringBuilder snakeCase = new StringBuilder();
        
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    snakeCase.append('_');
                }
                snakeCase.append(Character.toLowerCase(c));
            } else {
                snakeCase.append(c);
            }
        }
        
        return snakeCase.toString();
    }
}

