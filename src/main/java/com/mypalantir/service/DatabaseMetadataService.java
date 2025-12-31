package com.mypalantir.service;

import com.mypalantir.config.DatabaseConfig;
import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
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
    private IInstanceStorage instanceStorage;

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

    private Connection getConnectionForDatabase(String databaseId) throws SQLException, IOException {
        // 如果是默认数据库，使用默认连接
        if (databaseId == null || databaseId.isEmpty()) {
            return connectionManager.getConnection();
        }

        // 获取数据库实例信息
        Map<String, Object> database = instanceStorage.getInstance("database", databaseId);
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
        return DriverManager.getConnection(url, username, password);
    }

    private String getDatabaseName(String databaseId) throws IOException {
        if (databaseId == null || databaseId.isEmpty()) {
            return connectionManager.getConfig().getDbName();
        }
        
        Map<String, Object> database = instanceStorage.getInstance("database", databaseId);
        return (String) database.get("database_name");
    }
}
