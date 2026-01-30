package com.mypalantir.service;

import com.mypalantir.config.DatabaseConfig;
import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseMetadataService {
    @Autowired
    private DatabaseConfig.DatabaseConnectionManager connectionManager;

    @Autowired
    @Lazy
    private IInstanceStorage instanceStorage;

    @Autowired
    @Lazy
    private com.mypalantir.repository.Neo4jInstanceStorage neo4jInstanceStorage;

    // 线程本地变量，用于防止递归调用
    private static final ThreadLocal<Boolean> isGettingDatabaseInstance = ThreadLocal.withInitial(() -> false);

    public List<Map<String, Object>> getTables(String databaseId) throws SQLException, IOException {
        List<Map<String, Object>> tables = new ArrayList<>();
        
        // 根据databaseId获取数据库连接信息
        Connection conn = getConnectionForDatabase(databaseId);
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = getDatabaseName(databaseId);
            
            try (ResultSet rs = metaData.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    Map<String, Object> table = new HashMap<>();
                    table.put("name", rs.getString("TABLE_NAME"));
                    table.put("type", rs.getString("TABLE_TYPE"));
                    table.put("schema", rs.getString("TABLE_SCHEM"));
                    table.put("catalog", rs.getString("TABLE_CAT"));
                    table.put("database_id", databaseId);
                    tables.add(table);
                }
            }
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
        
        return tables;
    }

    public List<Map<String, Object>> getColumns(String databaseId, String tableName) throws SQLException, IOException {
        List<Map<String, Object>> columns = new ArrayList<>();
        
        // 根据databaseId获取数据库连接信息
        Connection conn = getConnectionForDatabase(databaseId);
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = getDatabaseName(databaseId);
            
            try (ResultSet rs = metaData.getColumns(catalog, null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", rs.getString("COLUMN_NAME"));
                    column.put("data_type", rs.getString("TYPE_NAME"));
                    column.put("data_type_code", rs.getInt("DATA_TYPE"));
                    column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    column.put("column_size", rs.getInt("COLUMN_SIZE"));
                    column.put("decimal_digits", rs.getInt("DECIMAL_DIGITS"));
                    column.put("remarks", rs.getString("REMARKS"));
                    column.put("table_name", tableName);
                    column.put("database_id", databaseId);
                    columns.add(column);
                }
            }
            
            // 获取主键信息
            try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, null, tableName)) {
                List<String> primaryKeys = new ArrayList<>();
                while (pkRs.next()) {
                    primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
                // 标记主键列
                for (Map<String, Object> column : columns) {
                    column.put("is_primary_key", primaryKeys.contains(column.get("name")));
                }
            }
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
        
        return columns;
    }

    public Map<String, Object> getTableInfo(String databaseId, String tableName) throws SQLException, IOException {
        Map<String, Object> tableInfo = new HashMap<>();
        tableInfo.put("name", tableName);
        tableInfo.put("database_id", databaseId);
        tableInfo.put("columns", getColumns(databaseId, tableName));
        return tableInfo;
    }

    public List<Map<String, Object>> executeQuery(String sql) throws SQLException {
        try {
            return executeQuery(sql, null);
        } catch (IOException e) {
            throw new SQLException("Failed to get database connection", e);
        }
    }

    public List<Map<String, Object>> executeQuery(String sql, String databaseId) throws SQLException, IOException {
        List<Map<String, Object>> results = new ArrayList<>();
        
        Connection conn = getConnectionForDatabase(databaseId);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    // 转换 SQL 类型为基本类型，以便保存到 Neo4j
                    value = convertSqlValue(value);
                    row.put(columnName, value);
                }
                results.add(row);
            }
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
        
        return results;
    }

    /**
     * 获取数据库连接（公开方法，供其他服务使用）
     */
    public Connection getConnectionForDatabase(String databaseId) throws SQLException, IOException {
        return getDataSourceForDatabase(databaseId).getConnection();
    }

    /**
     * 获取数据源（DataSource）
     */
    public javax.sql.DataSource getDataSourceForDatabase(String databaseId) throws IOException {
        // 如果是默认数据库
        if (databaseId == null || databaseId.isEmpty()) {
            return new org.springframework.jdbc.datasource.DriverManagerDataSource(
                connectionManager.getJdbcUrl(),
                connectionManager.getConfig().getDbUser(),
                connectionManager.getConfig().getDbPassword()
            );
        }

        // 获取数据库实例信息
        // 注意：直接使用 Neo4jInstanceStorage 避免循环调用
        // 因为 HybridInstanceStorage.hasRelationalMapping() 会调用 tableExists()，而 tableExists() 又会调用这里
        // 使用线程本地变量防止递归调用
        if (isGettingDatabaseInstance.get()) {
            throw new IOException("Recursive call detected while getting database instance: " + databaseId);
        }
        Map<String, Object> database;
        try {
            isGettingDatabaseInstance.set(true);
            database = neo4jInstanceStorage.getInstance("database", databaseId);
        } finally {
            isGettingDatabaseInstance.set(false);
        }
        
        String host = (String) database.get("host");
        Integer port = database.get("port") instanceof Number 
            ? ((Number) database.get("port")).intValue() 
            : 3306;
        String dbName = (String) database.get("database_name");
        String username = (String) database.get("username");
        
        // 从配置获取密码（因为密码不存储在实例中）
        String password = connectionManager.getConfig().getDbPassword();
        
        // 如果数据库实例有密码字段，使用它
        if (database.containsKey("password") && database.get("password") != null) {
            password = database.get("password").toString();
        }

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                host, port, dbName);
        
        return new org.springframework.jdbc.datasource.DriverManagerDataSource(url, username, password);
    }

    private String getDatabaseName(String databaseId) throws IOException {
        if (databaseId == null || databaseId.isEmpty()) {
            // 使用默认数据库配置（从 application.properties 读取）
            return connectionManager.getConfig().getDbName();
        }
        
        // 注意：直接使用 Neo4jInstanceStorage 避免循环调用
        // 使用线程本地变量防止递归调用
        if (isGettingDatabaseInstance.get()) {
            throw new IOException("Recursive call detected while getting database name: " + databaseId);
        }
        try {
            isGettingDatabaseInstance.set(true);
            Map<String, Object> database = neo4jInstanceStorage.getInstance("database", databaseId);
            return (String) database.get("database_name");
        } finally {
            isGettingDatabaseInstance.set(false);
        }
    }

    /**
     * 执行更新SQL（INSERT, UPDATE, DELETE, CREATE TABLE等）
     */
    public int executeUpdate(String sql, String databaseId) throws SQLException, IOException {
        Connection conn = getConnectionForDatabase(databaseId);
        try (Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
    }

    /**
     * 检查表是否存在
     * 使用 SQL 查询 INFORMATION_SCHEMA 来检查表是否存在，这比 DatabaseMetaData.getTables 更可靠
     */
    public boolean tableExists(String databaseId, String tableName) throws SQLException, IOException {
        Connection conn = getConnectionForDatabase(databaseId);
        try {
            // 方法1：使用 SQL 查询 INFORMATION_SCHEMA（更可靠）
            String dbName = getDatabaseName(databaseId);
            String sql;
            PreparedStatement stmt;
            
            if (dbName != null && !dbName.isEmpty()) {
                // 如果指定了数据库名，查询指定数据库
                sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, dbName);
                stmt.setString(2, tableName);
            } else {
                // 如果没有指定数据库名，查询当前连接的数据库
                sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, tableName);
            }
            
            try (stmt) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        return count > 0;
                    }
                }
            }
            
            return false;
        } catch (SQLException e) {
            // 如果 SQL 查询失败，回退到 DatabaseMetaData 方法
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                // 当连接已经指定了数据库时，catalog 应该使用 null 或连接的实际数据库
                // 尝试使用 null（查询当前连接的数据库）
                try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
                    if (rs.next()) {
                        return true;
                    }
                }
                
                // 如果使用 null 没找到，尝试使用数据库名（不区分大小写）
                String catalog = getDatabaseName(databaseId);
                if (catalog != null && !catalog.isEmpty()) {
                    try (ResultSet rs = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
                        return rs.next();
                    }
                }
            } catch (SQLException e2) {
                // 如果两种方法都失败，抛出原始异常
                throw new SQLException("Failed to check table existence: " + e.getMessage(), e);
            }
            
            return false;
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
    }

    /**
     * 将 SQL 类型转换为基本类型，以便保存到 Neo4j
     */
    private Object convertSqlValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // 处理日期时间类型
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate().toString();
        }
        if (value instanceof java.sql.Time) {
            return ((java.sql.Time) value).toLocalTime().toString();
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toInstant().toString();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant().toString();
        }
        
        // 处理数值类型
        if (value instanceof java.math.BigDecimal) {
            // 尝试转换为 Long 或 Double
            java.math.BigDecimal bd = (java.math.BigDecimal) value;
            try {
                return bd.longValueExact();
            } catch (ArithmeticException e) {
                return bd.doubleValue();
            }
        }
        if (value instanceof java.math.BigInteger) {
            return ((java.math.BigInteger) value).longValue();
        }
        
        // 处理字节数组
        if (value instanceof byte[]) {
            return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
        }
        
        // 其他类型直接返回
        return value;
    }
}
