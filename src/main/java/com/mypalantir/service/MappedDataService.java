package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.repository.Neo4jInstanceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class MappedDataService {
    private static final Logger logger = LoggerFactory.getLogger(MappedDataService.class);

    @Autowired
    private MappingService mappingService;

    @Autowired
    private DatabaseMetadataService databaseMetadataService;

    @Autowired
    private IInstanceStorage instanceStorage;

    @Autowired
    private Loader loader;

    /** 混合存储模式下用于同步抽取后写入 Neo4j 节点 */
    @Autowired(required = false)
    private Neo4jInstanceStorage neo4jStorage;

    @Autowired(required = false)
    private Environment environment;

    /**
     * 查询映射数据（原始数据）
     * 严格查询界限：只查询根据mapping映射的原始表，不查询同步表
     * 
     * @param objectType 对象类型
     * @param mappingId 映射ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @param filters 查询条件（属性名 -> 值）
     * @return 查询结果
     */
    public com.mypalantir.repository.InstanceStorage.ListResult queryMappedInstances(String objectType, String mappingId, int offset, int limit, Map<String, Object> filters) throws IOException, SQLException, Loader.NotFoundException {
        // filters 可以为 null，表示无查询条件
        logger.info("[MappedDataService] ========== MAPPED DATA QUERY (原始数据查询) ==========");
        logger.info("[MappedDataService] Query mode: MAPPED_DATA (原始表查询)");
        logger.info("[MappedDataService] objectType={}, mappingId={}, offset={}, limit={}, filters={}", 
            objectType, mappingId, offset, limit, filters);
        logger.info("[MappedDataService] Data source: ORIGINAL TABLE (根据mapping映射的原始表，不查询同步表)");
        
        // 获取映射关系
        Map<String, Object> mapping = mappingService.getMapping(mappingId);
        String tableId = (String) mapping.get("table_id");
        
        // 获取表信息
        Map<String, Object> table = instanceStorage.getInstance("table", tableId);
        String tableName = (String) table.get("name");
        
        // 获取数据库ID
        String databaseId = (String) table.get("database_id");
        
        logger.info("[MappedDataService] Original table: tableName={}, databaseId={}", tableName, databaseId);
        
        // 获取列到属性的映射
        @SuppressWarnings("unchecked")
        Map<String, String> columnPropertyMappings = (Map<String, String>) mapping.get("column_property_mappings");
        
        // 构建查询SQL（查询原始表）
        String sql = buildSelectQuery(tableName, columnPropertyMappings, offset, limit, filters);
        logger.info("[MappedDataService] Executing SQL on ORIGINAL TABLE: {}", sql);
        
        // 执行查询（只查询原始表，不查询同步表）
        List<Map<String, Object>> dbRows = databaseMetadataService.executeQuery(sql, databaseId);
        logger.info("[MappedDataService] Retrieved {} rows from ORIGINAL TABLE", dbRows.size());
        
        // 转换为实例对象
        List<Map<String, Object>> instances = new ArrayList<>();
        String primaryKeyColumn = (String) mapping.get("primary_key_column");
        
        for (Map<String, Object> row : dbRows) {
            Map<String, Object> instance = new HashMap<>();
            
            // 使用主键列作为ID，如果没有则生成UUID
            if (primaryKeyColumn != null && row.containsKey(primaryKeyColumn)) {
                instance.put("id", String.valueOf(row.get(primaryKeyColumn)));
            } else {
                instance.put("id", UUID.randomUUID().toString());
            }
            
            // 映射列到属性
            for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                String columnName = entry.getKey();
                String propertyName = entry.getValue();
                if (row.containsKey(columnName)) {
                    instance.put(propertyName, row.get(columnName));
                }
            }
            
            // 添加时间戳
            String now = java.time.Instant.now().toString();
            instance.put("created_at", now);
            instance.put("updated_at", now);
            
            instances.add(instance);
        }
        
        // 获取总数（需要执行COUNT查询，查询原始表，包含相同的WHERE条件）
        String countSql = buildCountQuery(tableName, columnPropertyMappings, filters);
        logger.info("[MappedDataService] Executing COUNT SQL on ORIGINAL TABLE: {}", countSql);
        List<Map<String, Object>> countResult = databaseMetadataService.executeQuery(countSql, databaseId);
        long total = countResult.isEmpty() ? 0 : ((Number) countResult.get(0).get("total")).longValue();
        
        logger.info("[MappedDataService] Mapped data query result: objectType={}, itemsCount={}, total={}, dataSource=ORIGINAL_TABLE", 
            objectType, instances.size(), total);
        logger.info("[MappedDataService] ========== MAPPED DATA QUERY END ==========");
        
        return new com.mypalantir.repository.InstanceStorage.ListResult(instances, total);
    }

    private String buildSelectQuery(String tableName, Map<String, String> columnPropertyMappings, int offset, int limit, Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // 添加所有映射的列
        List<String> columns = new ArrayList<>(columnPropertyMappings.keySet());
        if (columns.isEmpty()) {
            sql.append("*");
        } else {
            // 转义列名，防止SQL注入
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("`").append(columns.get(i)).append("`");
            }
        }
        
        // 转义表名
        sql.append(" FROM `").append(tableName).append("`");
        
        // 添加WHERE条件
        String whereClause = buildWhereClause(columnPropertyMappings, filters);
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        
        // MySQL分页语法：LIMIT offset, limit
        sql.append(" LIMIT ").append(offset).append(", ").append(limit);
        
        return sql.toString();
    }
    
    /**
     * 构建COUNT查询SQL
     */
    private String buildCountQuery(String tableName, Map<String, String> columnPropertyMappings, Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as total FROM `").append(tableName).append("`");
        
        // 添加WHERE条件
        String whereClause = buildWhereClause(columnPropertyMappings, filters);
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        
        return sql.toString();
    }
    
    /**
     * 构建WHERE子句
     * 将属性名转换为列名，并构建等值查询条件
     * 
     * @param columnPropertyMappings 列名到属性名的映射
     * @param filters 查询条件（属性名 -> 值）
     * @return WHERE子句（不包含WHERE关键字）
     */
    private String buildWhereClause(Map<String, String> columnPropertyMappings, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        
        // 构建属性名到列名的反向映射
        Map<String, String> propertyColumnMappings = new HashMap<>();
        for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
            propertyColumnMappings.put(entry.getValue(), entry.getKey());
        }
        
        List<String> conditions = new ArrayList<>();
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            String propertyName = filter.getKey();
            Object value = filter.getValue();
            
            // 跳过空值
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                continue;
            }
            
            // 查找对应的列名
            String columnName = propertyColumnMappings.get(propertyName);
            if (columnName == null) {
                logger.warn("[MappedDataService] Property '{}' not found in column mappings, skipping filter", propertyName);
                continue;
            }
            
            // 构建条件：列名 = 值（转义值以防止SQL注入）
            // 注意：这里使用简单的字符串拼接，实际生产环境应该使用PreparedStatement
            String escapedValue = escapeSqlValue(value);
            conditions.add("`" + columnName + "` = " + escapedValue);
        }
        
        if (conditions.isEmpty()) {
            return null;
        }
        
        return String.join(" AND ", conditions);
    }
    
    /**
     * 转义SQL值（简单实现，防止SQL注入）
     * 注意：这是简化版本，实际生产环境应该使用PreparedStatement
     */
    private String escapeSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        
        if (value instanceof Number) {
            return value.toString();
        }
        
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "1" : "0";
        }
        
        // 字符串值：转义单引号并添加引号
        String str = value.toString();
        str = str.replace("'", "''");  // 转义单引号
        str = str.replace("\\", "\\\\");  // 转义反斜杠
        return "'" + str + "'";
    }

    /**
     * 同步抽取：先构建同步表，然后抽取数据
     * 1. 根据映射关系获取源表字段信息（类型、长度等）
     * 2. 根据模型定义的属性与原始表的映射关系，构建目标表结构
     * 3. 创建目标同步表
     * 4. 从源表抽取数据到目标表
     */
    public SyncExtractResult syncExtractWithTable(String objectType, String mappingId, String targetDatabaseId) 
            throws IOException, SQLException, Loader.NotFoundException {
        SyncExtractResult result = new SyncExtractResult();
        
        // 1. 获取映射关系
        Map<String, Object> mapping = mappingService.getMapping(mappingId);
        String sourceTableId = (String) mapping.get("table_id");
        
        // 2. 获取源表信息
        Map<String, Object> sourceTable = instanceStorage.getInstance("table", sourceTableId);
        String sourceTableName = (String) sourceTable.get("name");
        String sourceDatabaseId = (String) sourceTable.get("database_id");
        
        // 3. 获取对象类型定义
        ObjectType objectTypeDef = loader.getObjectType(objectType);
        
        // 4. 获取列到属性的映射
        @SuppressWarnings("unchecked")
        Map<String, String> columnPropertyMappings = (Map<String, String>) mapping.get("column_property_mappings");
        
        // 5. 获取源表字段信息（类型、长度等）
        List<Map<String, Object>> sourceColumns = databaseMetadataService.getColumns(sourceDatabaseId, sourceTableName);
        Map<String, Map<String, Object>> sourceColumnMap = new HashMap<>();
        for (Map<String, Object> col : sourceColumns) {
            sourceColumnMap.put((String) col.get("name"), col);
        }
        
        // 6. 构建目标表名（直接使用对象类型名称作为表名，不加_sync后缀）
        String targetTableName = objectType.toLowerCase();
        
        // 7. 如果目标数据库ID为空，使用默认数据库（项目配置的db.*数据源）
        // 使用null表示默认数据库，DatabaseMetadataService会从Config读取db.*配置
        if (targetDatabaseId == null || targetDatabaseId.isEmpty()) {
            targetDatabaseId = null; // null表示使用默认数据库（application.properties中的db.*配置）
        }
        
        // 8. 构建目标表结构
        String createTableSql = buildSyncTableSql(targetTableName, objectTypeDef, columnPropertyMappings, sourceColumnMap);
        
        // 9. 创建目标表（如果不存在）
        boolean tableCreated = false;
        if (!databaseMetadataService.tableExists(targetDatabaseId, targetTableName)) {
            databaseMetadataService.executeUpdate(createTableSql, targetDatabaseId);
            tableCreated = true;
            result.tableCreated = true;
            logger.info("Created sync table: {}", targetTableName);
        } else {
            logger.info("Sync table already exists: {}", targetTableName);
        }
        
        // 10. 抽取数据
        // 如果源数据库和目标数据库不同，需要分别查询和插入
        // 支持新格式（数组）和旧格式（单个字符串）
        @SuppressWarnings("unchecked")
        List<String> primaryKeyColumns = (List<String>) mapping.get("primary_key_columns");
        String primaryKeyColumn = (String) mapping.get("primary_key_column");
        
        // 如果新格式不存在，使用旧格式
        if (primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
            if (primaryKeyColumn != null && !primaryKeyColumn.isEmpty()) {
                primaryKeyColumns = java.util.Arrays.asList(primaryKeyColumn);
            }
        } else {
            // 如果新格式存在，也设置旧格式以兼容旧代码
            if (primaryKeyColumn == null || primaryKeyColumn.isEmpty()) {
                primaryKeyColumn = primaryKeyColumns.get(0);
            }
        }
        
        // 记录主键配置信息
        logger.info("[MappedDataService] Data extraction for objectType={}, primaryKeyColumns={}, primaryKeyColumn={}", 
            objectType, primaryKeyColumns, primaryKeyColumn);
        
        // 警告：如果只使用 V_STAT_DATE 作为主键，可能不是唯一标识符
        if (primaryKeyColumns != null && primaryKeyColumns.size() == 1 && 
            "V_STAT_DATE".equalsIgnoreCase(primaryKeyColumns.get(0))) {
            logger.warn("[MappedDataService] WARNING: Using V_STAT_DATE as single primary key may cause data loss! " +
                "V_STAT_DATE (报表期次) is not a unique identifier. " +
                "Consider using composite primary keys (e.g., [V_LOAN_ID, V_STAT_DATE] for loan, " +
                "[V_CUST_CD, V_STAT_DATE] for customer, etc.)");
        }
        
        int rowsInserted;
        
        // 判断是否需要跨数据库抽取
        // targetDatabaseId为null表示使用默认数据库（项目配置的db.*）
        boolean isCrossDatabase = (sourceDatabaseId != null && !sourceDatabaseId.isEmpty()) && 
                                  (targetDatabaseId == null || !sourceDatabaseId.equals(targetDatabaseId));
        
        if (isCrossDatabase) {
            // 跨数据库抽取：先从源数据库查询，再插入到目标数据库（默认数据库）
            rowsInserted = extractDataCrossDatabase(sourceTableName, targetTableName, 
                sourceDatabaseId, targetDatabaseId, columnPropertyMappings, primaryKeyColumns, primaryKeyColumn);
        } else {
            // 同数据库抽取：使用INSERT INTO ... SELECT ... FROM
            String extractSql = buildExtractSql(sourceTableName, targetTableName, columnPropertyMappings, primaryKeyColumns, primaryKeyColumn);
            rowsInserted = databaseMetadataService.executeUpdate(extractSql, targetDatabaseId);
        }
        
        result.rowsExtracted = rowsInserted;
        
        String targetDbDisplay = (targetDatabaseId == null || targetDatabaseId.isEmpty()) 
            ? "default (project config)" : targetDatabaseId;
        logger.info("Extracted {} rows from {} (db: {}) to {} (db: {})", 
            rowsInserted, sourceTableName, sourceDatabaseId, targetTableName, targetDbDisplay);
        
        // 11. 保存目标表信息到实例存储（可选）
        result.targetTableName = targetTableName;
        // 如果targetDatabaseId为null，表示使用默认数据库（项目配置的db.*）
        // 保存为"default"以便于识别
        result.targetDatabaseId = (targetDatabaseId == null || targetDatabaseId.isEmpty()) 
            ? "default" : targetDatabaseId;
        
        // 12. 混合存储模式：同步到 Neo4j（按 storage.neo4j.fields.default 等配置建立节点）
        if (neo4jStorage != null && rowsInserted > 0) {
            try {
                int neo4jSynced = syncSyncTableToNeo4j(objectType, targetTableName, targetDatabaseId);
                result.neo4jNodesSynced = neo4jSynced;
                logger.info("Synced {} rows to Neo4j for object type {}", neo4jSynced, objectType);
            } catch (Exception e) {
                logger.warn("Failed to sync to Neo4j (sync table data is OK): {}", e.getMessage());
            }
        }
        
        return result;
    }

    /**
     * 构建同步表的CREATE TABLE SQL
     */
    private String buildSyncTableSql(String tableName, ObjectType objectType, 
                                     Map<String, String> columnPropertyMappings,
                                     Map<String, Map<String, Object>> sourceColumnMap) {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n");
        
        List<String> columnDefinitions = new ArrayList<>();
        
        // 添加id字段（主键）
        String primaryKeyColumn = null;
        boolean hasIdColumn = false;
        
        // 遍历映射关系，构建列定义
        for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
            String sourceColumnName = entry.getKey();
            String propertyName = entry.getValue();
            
            // 检查是否是id字段
            if ("id".equals(propertyName)) {
                hasIdColumn = true;
                primaryKeyColumn = "id";
            }
            
            // 获取源列信息
            Map<String, Object> sourceColumn = sourceColumnMap.get(sourceColumnName);
            if (sourceColumn == null) {
                logger.warn("Source column {} not found, skipping", sourceColumnName);
                continue;
            }
            
            // 获取属性定义
            com.mypalantir.meta.Property property = findProperty(objectType, propertyName);
            
            // 构建列定义
            String columnDef = buildColumnDefinition(propertyName, sourceColumn, property);
            columnDefinitions.add(columnDef);
        }
        
        // 如果没有id字段，添加一个
        if (!hasIdColumn) {
            columnDefinitions.add(0, "`id` VARCHAR(255) NOT NULL");
            primaryKeyColumn = "id";
        }
        
        // 添加时间戳字段
        columnDefinitions.add("`created_at` DATETIME DEFAULT CURRENT_TIMESTAMP");
        columnDefinitions.add("`updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        
        sql.append("  ").append(String.join(",\n  ", columnDefinitions));
        
        // 添加主键
        if (primaryKeyColumn != null) {
            sql.append(",\n  PRIMARY KEY (`").append(primaryKeyColumn).append("`)");
        }
        
        sql.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='").append(objectType.getDisplayName()).append("同步表';");
        
        return sql.toString();
    }

    /**
     * 构建列定义
     */
    private String buildColumnDefinition(String propertyName, Map<String, Object> sourceColumn, 
                                        com.mypalantir.meta.Property property) {
        StringBuilder def = new StringBuilder("`").append(propertyName).append("` ");
        
        // 获取源列类型和长度
        String sourceType = (String) sourceColumn.get("data_type");
        Integer columnSize = sourceColumn.get("column_size") instanceof Number 
            ? ((Number) sourceColumn.get("column_size")).intValue() : null;
        Integer decimalDigits = sourceColumn.get("decimal_digits") instanceof Number 
            ? ((Number) sourceColumn.get("decimal_digits")).intValue() : null;
        Boolean nullable = (Boolean) sourceColumn.get("nullable");
        
        // 构建数据类型
        String dataType = buildDataType(sourceType, columnSize, decimalDigits);
        // MySQL 不允许对 BLOB/TEXT 列建主键（需指定 key length）。主键 id 强制使用 VARCHAR(255)
        if ("id".equals(propertyName) && isTextOrBlobType(dataType)) {
            dataType = "VARCHAR(255)";
        }
        def.append(dataType);
        
        // 添加NOT NULL约束
        if (property != null && property.isRequired()) {
            nullable = false;
        }
        if (nullable != null && !nullable) {
            def.append(" NOT NULL");
        }
        
        // 添加注释
        String comment = property != null ? property.getDescription() : null;
        if (comment == null || comment.isEmpty()) {
            comment = (String) sourceColumn.get("remarks");
        }
        if (comment != null && !comment.isEmpty()) {
            def.append(" COMMENT '").append(comment.replace("'", "''")).append("'");
        }
        
        return def.toString();
    }

    /**
     * 判断数据类型是否为 TEXT 或 BLOB 类型
     * MySQL 不允许对 BLOB/TEXT 列建主键（需指定 key length）
     */
    private boolean isTextOrBlobType(String dataType) {
        if (dataType == null) return false;
        String upper = dataType.toUpperCase();
        return upper.contains("TEXT") || upper.contains("BLOB");
    }

    /**
     * 构建数据类型字符串
     */
    private String buildDataType(String sourceType, Integer columnSize, Integer decimalDigits) {
        if (sourceType == null) {
            return "VARCHAR(255)";
        }
        
        String typeUpper = sourceType.toUpperCase();
        
        // 处理数值类型
        if (typeUpper.contains("INT")) {
            if (typeUpper.contains("BIG")) {
                return "BIGINT";
            } else if (typeUpper.contains("SMALL")) {
                return "SMALLINT";
            } else if (typeUpper.contains("TINY")) {
                return "TINYINT";
            } else {
                return "INT";
            }
        }
        
        // 处理浮点类型
        if (typeUpper.contains("DOUBLE") || typeUpper.contains("FLOAT")) {
            if (decimalDigits != null && decimalDigits > 0) {
                return String.format("DECIMAL(%d, %d)", columnSize != null ? columnSize : 10, decimalDigits);
            }
            return "DOUBLE";
        }
        
        // 处理字符串类型
        if (typeUpper.contains("VARCHAR") || typeUpper.contains("CHAR")) {
            if (columnSize != null && columnSize > 0) {
                return String.format("VARCHAR(%d)", columnSize);
            }
            return "VARCHAR(255)";
        }
        
        // 处理文本类型
        if (typeUpper.contains("TEXT")) {
            return "TEXT";
        }
        
        // 处理日期时间类型
        if (typeUpper.contains("DATE") && !typeUpper.contains("TIME")) {
            return "DATE";
        }
        if (typeUpper.contains("TIME") || typeUpper.contains("TIMESTAMP")) {
            return "DATETIME";
        }
        
        // 处理布尔类型
        if (typeUpper.contains("BOOL")) {
            return "TINYINT(1)";
        }
        
        // 默认返回VARCHAR
        if (columnSize != null && columnSize > 0) {
            return String.format("VARCHAR(%d)", columnSize);
        }
        return "VARCHAR(255)";
    }

    /**
     * 跨数据库数据抽取
     * 先从源数据库查询数据，再插入到目标数据库
     * 支持组合主键：如果配置了多个主键列，使用下划线连接它们的值作为ID
     */
    private int extractDataCrossDatabase(String sourceTableName, String targetTableName,
                                         String sourceDatabaseId, String targetDatabaseId,
                                         Map<String, String> columnPropertyMappings,
                                         List<String> primaryKeyColumns, String primaryKeyColumn) throws SQLException, IOException {
        // 1. 从源数据库查询数据（确保包含所有主键列）
        String selectSql = buildSelectSql(sourceTableName, columnPropertyMappings, primaryKeyColumns);
        List<Map<String, Object>> sourceRows = databaseMetadataService.executeQuery(selectSql, sourceDatabaseId);
        
        if (sourceRows.isEmpty()) {
            logger.info("No data to extract from source table: {}", sourceTableName);
            return 0;
        }
        
        // 2. 构建插入SQL（去重：多个源列映射到同一目标属性时只保留一次）
        List<String> targetColumns = new ArrayList<>();
        targetColumns.add("id");
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        seen.add("id");
        for (String propertyName : columnPropertyMappings.values()) {
            if (!"id".equals(propertyName) && seen.add(propertyName)) {
                targetColumns.add(propertyName);
            }
        }
        
        // 3. 批量插入到目标数据库（JDBC batch + ON DUPLICATE KEY UPDATE，每 1000 条提交一次）
        int rowsInserted = 0;
        String upsertSql = buildInsertOrUpdateSql(targetTableName, targetColumns);
        final int BATCH_SIZE = 1000;
        
        Connection targetConn = databaseMetadataService.getConnectionForDatabase(targetDatabaseId);
        try {
            targetConn.setAutoCommit(false);
            try (PreparedStatement pstmt = targetConn.prepareStatement(upsertSql)) {
                int batchCount = 0;
                for (Map<String, Object> row : sourceRows) {
                    setInsertParameters(pstmt, row, columnPropertyMappings, primaryKeyColumns, primaryKeyColumn, targetColumns);
                    pstmt.addBatch();
                    batchCount++;
                    if (batchCount >= BATCH_SIZE) {
                        int[] counts = pstmt.executeBatch();
                        for (int c : counts) rowsInserted += (c > 0 ? 1 : 0);
                        batchCount = 0;
                    }
                }
                if (batchCount > 0) {
                    int[] counts = pstmt.executeBatch();
                    for (int c : counts) rowsInserted += (c > 0 ? 1 : 0);
                }
            }
            targetConn.commit();
        } catch (SQLException e) {
            if (targetConn != null) targetConn.rollback();
            throw e;
        } finally {
            if (targetConn != null) {
                targetConn.setAutoCommit(true);
                if (!targetConn.isClosed()) targetConn.close();
            }
        }
        
        return rowsInserted;
    }

    /**
     * 构建SELECT SQL（用于跨数据库查询）
     * 确保包含所有主键列，即使它们不在映射关系中
     */
    private String buildSelectSql(String sourceTableName, Map<String, String> columnPropertyMappings, 
                                  List<String> primaryKeyColumns) {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // 收集需要查询的列
        Set<String> selectColumnsSet = new HashSet<>();
        
        // 添加映射的列
        for (String sourceColumn : columnPropertyMappings.keySet()) {
            selectColumnsSet.add(sourceColumn);
        }
        
        // 添加主键列（如果不在映射关系中）
        if (primaryKeyColumns != null) {
            for (String pkCol : primaryKeyColumns) {
                selectColumnsSet.add(pkCol);
            }
        }
        
        List<String> selectColumns = new ArrayList<>(selectColumnsSet);
        sql.append(String.join(", ", selectColumns.stream().map(c -> "`" + c + "`").toArray(String[]::new)));
        sql.append(" FROM `").append(sourceTableName).append("`");
        
        return sql.toString();
    }

    /**
     * 构建INSERT SQL
     */
    private String buildInsertSql(String tableName, List<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(tableName).append("` (");
        sql.append(String.join(", ", columns.stream().map(c -> "`" + c + "`").toArray(String[]::new)));
        sql.append(") VALUES (");
        sql.append(String.join(", ", java.util.Collections.nCopies(columns.size(), "?")));
        sql.append(")");
        
        return sql.toString();
    }

    /**
     * 构建INSERT ... ON DUPLICATE KEY UPDATE SQL
     */
    private String buildInsertOrUpdateSql(String tableName, List<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(tableName).append("` (");
        sql.append(String.join(", ", columns.stream().map(c -> "`" + c + "`").toArray(String[]::new)));
        sql.append(") VALUES (");
        sql.append(String.join(", ", java.util.Collections.nCopies(columns.size(), "?")));
        sql.append(") ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP");
        
        return sql.toString();
    }

    /**
     * 设置INSERT参数
     * 支持组合主键：如果配置了多个主键列，使用下划线连接它们的值作为ID
     */
    private void setInsertParameters(PreparedStatement pstmt, Map<String, Object> row,
                                    Map<String, String> columnPropertyMappings,
                                    List<String> primaryKeyColumns, String primaryKeyColumn,
                                    List<String> targetColumns) throws SQLException {
        int paramIndex = 1;
        
        for (String targetColumn : targetColumns) {
            Object value = null;
            
            if ("id".equals(targetColumn)) {
                // 查找id值
                // 优先级1：查找映射到id的源列
                String idSourceColumn = null;
                for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                    if ("id".equals(entry.getValue())) {
                        idSourceColumn = entry.getKey();
                        break;
                    }
                }
                
                // 优先级2：使用组合主键列（多个主键列用下划线连接）
                if (idSourceColumn == null && primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
                    if (primaryKeyColumns.size() == 1) {
                        // 单个主键列
                        idSourceColumn = primaryKeyColumns.get(0);
                        if (row.containsKey(idSourceColumn)) {
                            value = row.get(idSourceColumn);
                        }
                    } else {
                        // 多个主键列：使用下划线连接它们的值
                        List<String> idParts = new ArrayList<>();
                        boolean allKeysPresent = true;
                        for (String pkCol : primaryKeyColumns) {
                            if (row.containsKey(pkCol) && row.get(pkCol) != null) {
                                idParts.add(String.valueOf(row.get(pkCol)));
                            } else {
                                allKeysPresent = false;
                                break;
                            }
                        }
                        if (allKeysPresent && !idParts.isEmpty()) {
                            value = idParts.size() == 1 ? idParts.get(0) : String.join("_", idParts);
                        }
                    }
                }
                
                // 优先级3：使用单个主键列（兼容旧格式）
                if (value == null && idSourceColumn == null && primaryKeyColumn != null && !primaryKeyColumn.isEmpty()) {
                    idSourceColumn = primaryKeyColumn;
                }
                
                if (value == null && idSourceColumn != null && row.containsKey(idSourceColumn)) {
                    value = row.get(idSourceColumn);
                }
                
                // 优先级4：使用第一个映射列
                if (value == null && !columnPropertyMappings.isEmpty()) {
                    idSourceColumn = columnPropertyMappings.keySet().iterator().next();
                    if (row.containsKey(idSourceColumn)) {
                        value = row.get(idSourceColumn);
                    }
                }
            } else {
                // 查找对应的源列
                for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                    if (targetColumn.equals(entry.getValue())) {
                        value = row.get(entry.getKey());
                        break;
                    }
                }
            }
            
            pstmt.setObject(paramIndex++, value);
        }
    }

    /**
     * 构建数据抽取SQL（同数据库）
     * 使用INSERT INTO ... SELECT ... FROM，支持数据去重
     * 支持组合主键：如果配置了多个主键列，使用CONCAT连接它们的值作为ID
     */
    private String buildExtractSql(String sourceTableName, String targetTableName, 
                                   Map<String, String> columnPropertyMappings,
                                   List<String> primaryKeyColumns, String primaryKeyColumn) {
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(targetTableName).append("` (");
        
        // 添加目标列（去重：多个源列映射到同一目标属性时只保留一次）
        List<String> targetColumns = new ArrayList<>();
        targetColumns.add("id"); // id字段
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        seen.add("id");
        for (String propertyName : columnPropertyMappings.values()) {
            if (!"id".equals(propertyName) && seen.add(propertyName)) {
                targetColumns.add(propertyName);
            }
        }
        
        sql.append(String.join(", ", targetColumns.stream().map(c -> "`" + c + "`").toArray(String[]::new)));
        sql.append(") SELECT ");
        
        // 构建SELECT子句
        List<String> selectColumns = new ArrayList<>();
        
        // id字段：优先级
        // 1. 映射到id的源列
        // 2. 主键列（primary_key_columns，支持组合主键）
        // 3. 单个主键列（primary_key_column，兼容旧格式）
        // 4. 第一个映射列
        // 5. UUID()
        String idSourceExpression = null;
        
        // 优先级1：查找映射到id的源列
        for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
            if ("id".equals(entry.getValue())) {
                idSourceExpression = "`" + entry.getKey() + "`";
                break;
            }
        }
        
        // 优先级2：使用组合主键列（多个主键列用下划线连接）
        if (idSourceExpression == null && primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
            if (primaryKeyColumns.size() == 1) {
                // 单个主键列
                idSourceExpression = "`" + primaryKeyColumns.get(0) + "`";
            } else {
                // 多个主键列：使用CONCAT连接（MySQL语法）
                // CONCAT(expr1, '_', expr2, '_', expr3) 格式
                List<String> concatParts = new ArrayList<>();
                for (String pkCol : primaryKeyColumns) {
                    concatParts.add("COALESCE(`" + pkCol + "`, '')");
                }
                // 构建 CONCAT(expr1, '_', expr2, '_', expr3) 格式
                StringBuilder concatBuilder = new StringBuilder("CONCAT(");
                for (int i = 0; i < concatParts.size(); i++) {
                    if (i > 0) {
                        concatBuilder.append(", '_', ");
                    }
                    concatBuilder.append(concatParts.get(i));
                }
                concatBuilder.append(")");
                idSourceExpression = concatBuilder.toString();
            }
        }
        
        // 优先级3：使用单个主键列（兼容旧格式）
        if (idSourceExpression == null && primaryKeyColumn != null && !primaryKeyColumn.isEmpty()) {
            idSourceExpression = "`" + primaryKeyColumn + "`";
        }
        
        // 优先级4：使用第一个映射列
        if (idSourceExpression == null && !columnPropertyMappings.isEmpty()) {
            idSourceExpression = "`" + columnPropertyMappings.keySet().iterator().next() + "`";
        }
        
        // 构建id字段的SELECT
        if (idSourceExpression != null) {
            selectColumns.add(idSourceExpression + " AS `id`");
        } else {
            // 优先级5：使用UUID
            selectColumns.add("UUID() AS `id`");
        }
        
        // 其他映射列（去重：多个源列映射到同一目标属性时只取第一个）
        java.util.Set<String> selectedProperties = new java.util.HashSet<>();
        selectedProperties.add("id");
        for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
            String sourceColumn = entry.getKey();
            String propertyName = entry.getValue();
            if (!"id".equals(propertyName) && selectedProperties.add(propertyName)) {
                selectColumns.add("`" + sourceColumn + "` AS `" + propertyName + "`");
            }
        }
        
        sql.append(String.join(", ", selectColumns));
        sql.append(" FROM `").append(sourceTableName).append("`");
        
        // 添加ON DUPLICATE KEY UPDATE来处理重复数据（如果主键冲突）
        sql.append(" ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP");
        
        logger.info("[MappedDataService] Built extract SQL with primary key columns: {}, id expression: {}", 
            primaryKeyColumns, idSourceExpression);
        
        return sql.toString();
    }

    /**
     * 查找属性定义
     */
    private com.mypalantir.meta.Property findProperty(ObjectType objectType, String propertyName) {
        if (objectType.getProperties() == null) {
            return null;
        }
        for (com.mypalantir.meta.Property prop : objectType.getProperties()) {
            if (propertyName.equals(prop.getName())) {
                return prop;
            }
        }
        return null;
    }

    /**
     * 同步抽取结果
     */
    public static class SyncExtractResult {
        public boolean tableCreated = false;
        public int rowsExtracted = 0;
        public int neo4jNodesSynced = 0;
        public String targetTableName;
        public String targetDatabaseId;

        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("table_created", tableCreated);
            result.put("rows_extracted", rowsExtracted);
            result.put("neo4j_nodes_synced", neo4jNodesSynced);
            result.put("target_table_name", targetTableName);
            result.put("target_database_id", targetDatabaseId);
            return result;
        }
    }

    private static final int NEO4J_BATCH_SIZE = 500;
    private static final int NEO4J_PARALLEL_THREADS = 4;

    /**
     * 将同步表数据同步到 Neo4j（混合存储模式）
     * 批量 MERGE + 多线程并行，按 storage.neo4j.fields 提取字段建立节点
     */
    private int syncSyncTableToNeo4j(String objectType, String targetTableName, String targetDatabaseId) throws IOException, SQLException {
        String selectSql = "SELECT * FROM `" + targetTableName + "`";
        List<Map<String, Object>> rows = databaseMetadataService.executeQuery(selectSql, targetDatabaseId);
        if (rows.isEmpty()) return 0;
        List<String> neo4jFields = getNeo4jFields(objectType);
        List<Map<String, Object>> summaries = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> summary = extractSummaryFields(objectType, new HashMap<>(row), neo4jFields);
            if (summary.get("id") != null) summaries.add(summary);
        }
        if (summaries.isEmpty()) return 0;

        ExecutorService executor = Executors.newFixedThreadPool(NEO4J_PARALLEL_THREADS);
        try {
            List<List<Map<String, Object>>> batches = new ArrayList<>();
            for (int i = 0; i < summaries.size(); i += NEO4J_BATCH_SIZE) {
                batches.add(new ArrayList<>(summaries.subList(i, Math.min(i + NEO4J_BATCH_SIZE, summaries.size()))));
            }
            List<Future<Integer>> futures = new ArrayList<>();
            for (List<Map<String, Object>> batch : batches) {
                futures.add(executor.submit(() -> {
                    try {
                        return neo4jStorage.batchMergeInstances(objectType, batch);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }));
            }
            int synced = 0;
            for (Future<Integer> f : futures) {
                synced += f.get();
            }
            return synced;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Neo4j batch sync failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Neo4j sync interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    private List<String> getNeo4jFields(String objectType) {
        if (environment == null) {
            return Arrays.asList("id", "name", "display_name");
        }
        String configKey = "storage.neo4j.fields." + objectType.toLowerCase();
        String fieldsConfig = environment.getProperty(configKey);
        if (fieldsConfig != null && !fieldsConfig.trim().isEmpty()) {
            List<String> fields = new ArrayList<>();
            for (String f : fieldsConfig.split(",")) {
                String trimmed = f.trim();
                if (!trimmed.isEmpty()) fields.add(trimmed);
            }
            if (!fields.isEmpty()) return fields;
        }
        String defaultConfig = environment.getProperty("storage.neo4j.fields.default");
        if (defaultConfig != null && !defaultConfig.trim().isEmpty()) {
            List<String> fields = new ArrayList<>();
            for (String f : defaultConfig.split(",")) {
                String trimmed = f.trim();
                if (!trimmed.isEmpty()) fields.add(trimmed);
            }
            if (!fields.isEmpty()) return fields;
        }
        return Arrays.asList("id", "name", "display_name");
    }

    private Map<String, Object> extractSummaryFields(String objectType, Map<String, Object> data, List<String> neo4jFields) {
        Map<String, Object> summary = new HashMap<>();
        if (data.containsKey("id")) summary.put("id", data.get("id"));
        for (String field : neo4jFields) {
            if (!"id".equals(field) && data.containsKey(field)) {
                summary.put(field, data.get(field));
            }
        }
        return summary;
    }

    public void syncMappedDataToInstances(String objectType, String mappingId) throws IOException, SQLException, Loader.NotFoundException {
        // 获取映射关系
        Map<String, Object> mapping = mappingService.getMapping(mappingId);
        String tableId = (String) mapping.get("table_id");
        
        // 获取表信息
        Map<String, Object> table = instanceStorage.getInstance("table", tableId);
        String tableName = (String) table.get("name");
        
        // 获取数据库ID
        String databaseId = (String) table.get("database_id");
        
        // 获取列到属性的映射
        @SuppressWarnings("unchecked")
        Map<String, String> columnPropertyMappings = (Map<String, String>) mapping.get("column_property_mappings");
        
        // 构建查询SQL（查询所有数据，不使用分页）
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> columns = new ArrayList<>(columnPropertyMappings.keySet());
        if (columns.isEmpty()) {
            sql.append("*");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("`").append(columns.get(i)).append("`");
            }
        }
        sql.append(" FROM `").append(tableName).append("`");
        
        // 执行查询
        List<Map<String, Object>> dbRows = databaseMetadataService.executeQuery(sql.toString(), databaseId);
        
        // 转换为实例并保存
        // 支持新格式（数组）和旧格式（单个字符串）
        @SuppressWarnings("unchecked")
        List<String> primaryKeyColumns = (List<String>) mapping.get("primary_key_columns");
        String primaryKeyColumn = (String) mapping.get("primary_key_column");
        
        // 如果新格式不存在，使用旧格式
        if (primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
            if (primaryKeyColumn != null && !primaryKeyColumn.isEmpty()) {
                primaryKeyColumns = java.util.Arrays.asList(primaryKeyColumn);
            }
        }
        
        for (Map<String, Object> row : dbRows) {
            Map<String, Object> instanceData = new HashMap<>();
            
            // 映射列到属性
            for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                String columnName = entry.getKey();
                String propertyName = entry.getValue();
                if (row.containsKey(columnName)) {
                    instanceData.put(propertyName, row.get(columnName));
                }
            }
            
            // 如果指定了主键列，使用它作为ID，否则创建新实例
            String instanceId = null;
            if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
                // 支持多个主键列：组合它们的值
                List<String> idParts = new ArrayList<>();
                boolean allKeysPresent = true;
                for (String pkCol : primaryKeyColumns) {
                    if (row.containsKey(pkCol)) {
                        idParts.add(String.valueOf(row.get(pkCol)));
                    } else {
                        allKeysPresent = false;
                        break;
                    }
                }
                if (allKeysPresent && !idParts.isEmpty()) {
                    instanceId = idParts.size() == 1 ? idParts.get(0) : String.join("_", idParts);
                }
            } else if (primaryKeyColumn != null && row.containsKey(primaryKeyColumn)) {
                // 兼容旧格式：单个主键列
                instanceId = String.valueOf(row.get(primaryKeyColumn));
            }
            
            if (instanceId != null) {
                try {
                    // 尝试更新现有实例
                    Map<String, Object> existing = instanceStorage.getInstance(objectType, instanceId);
                    instanceStorage.updateInstance(objectType, instanceId, instanceData);
                } catch (IOException e) {
                    // 不存在则创建新实例，使用主键列的值作为ID
                    instanceStorage.createInstanceWithId(objectType, instanceId, instanceData);
                }
            } else {
                // 创建新实例
                instanceStorage.createInstance(objectType, instanceData);
            }
        }
    }
}
