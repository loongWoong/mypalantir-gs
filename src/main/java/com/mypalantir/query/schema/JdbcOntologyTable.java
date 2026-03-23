package com.mypalantir.query.schema;

import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 JDBC 的 Ontology Table
 * 将 ObjectType 映射到数据库表
 * 实现 ScannableTable 接口，支持扫描查询
 */
public class JdbcOntologyTable extends OntologyTable implements ScannableTable {
    private final DataSourceMapping mapping;
    /** 长期持有的连接（会泄漏，仅用于 createTableFromMapping 等场景） */
    private final Connection connection;
    /** 数据源：使用时按需取连接并归还池，推荐用于同步表等高频场景 */
    private final DataSource dataSource;

    /**
     * 使用 Connection 构造（连接会被长期持有，存在泄漏风险）
     */
    public JdbcOntologyTable(ObjectType objectType, DataSourceMapping mapping, Connection connection) {
        super(objectType);
        this.mapping = mapping;
        this.connection = connection;
        this.dataSource = null;
    }

    /**
     * 使用 DataSource 构造（推荐）：scan 时按需取连接并归还，避免连接池耗尽
     */
    public JdbcOntologyTable(ObjectType objectType, DataSourceMapping mapping, DataSource dataSource) {
        super(objectType);
        this.mapping = mapping;
        this.connection = null;
        this.dataSource = dataSource;
    }

    /**
     * 获取有效属性列表（排除衍生属性和与 ID 列重复的 id 属性）
     * buildRowType、buildSelectSql、buildRow 统一使用此方法
     */
    private List<Property> getEffectiveProperties() {
        List<Property> result = new ArrayList<>();
        if (objectType.getProperties() == null) return result;
        String idCol = (mapping != null && mapping.getIdColumn() != null) ? mapping.getIdColumn().toUpperCase() : "";
        for (Property prop : objectType.getProperties()) {
            if (prop.isDerived()) continue;
            if ("id".equals(prop.getName()) && mapping != null) {
                String colName = mapping.getColumnName(prop.getName());
                if (colName != null && colName.toUpperCase().equals(idCol)) continue;
            }
            result.add(prop);
        }
        return result;
    }

    @Override
    protected RelDataType buildRowType(RelDataTypeFactory typeFactory) {
        List<RelDataTypeFactory.Builder> builder = new ArrayList<>();
        RelDataTypeFactory.Builder typeBuilder = typeFactory.builder();

        // 定义 UTF-8 字符集和排序规则（复用）
        org.apache.calcite.sql.SqlCollation collation = org.apache.calcite.sql.SqlCollation.COERCIBLE;

        // 添加 ID 字段（VARCHAR 类型，指定 UTF-8 字符集）
        RelDataType varcharType = typeFactory.createSqlType(
            SqlTypeName.VARCHAR,
            org.apache.calcite.rel.type.RelDataType.PRECISION_NOT_SPECIFIED
        );
        // 为 VARCHAR 类型指定 UTF-8 字符集和排序规则
        RelDataType idType = typeFactory.createTypeWithCharsetAndCollation(
            varcharType,
            java.nio.charset.StandardCharsets.UTF_8,
            collation
        );
        typeBuilder.add("id", idType);

        // 添加所有属性字段（排除衍生属性和已作为ID的属性）
        for (Property prop : getEffectiveProperties()) {
            SqlTypeName sqlType = mapPropertyTypeToSqlType(prop.getDataType());
            RelDataType fieldType;

            // 对于字符串类型，指定 UTF-8 字符集和排序规则
            if (sqlType == SqlTypeName.VARCHAR || sqlType == SqlTypeName.CHAR) {
                RelDataType baseType = typeFactory.createSqlType(
                    sqlType,
                    org.apache.calcite.rel.type.RelDataType.PRECISION_NOT_SPECIFIED
                );
                fieldType = typeFactory.createTypeWithCharsetAndCollation(
                    baseType,
                    java.nio.charset.StandardCharsets.UTF_8,
                    collation
                );
            } else {
                fieldType = typeFactory.createSqlType(sqlType);
            }

            typeBuilder.add(prop.getName(), fieldType);
        }

        return typeBuilder.build();
    }

    /**
     * 将 Property 的 data_type 映射到 SQL 类型
     */
    SqlTypeName mapPropertyTypeToSqlType(String dataType) {
        if (dataType == null) {
            return SqlTypeName.VARCHAR;
        }
        
        switch (dataType.toLowerCase()) {
            case "string":
                return SqlTypeName.VARCHAR;
            case "int":
            case "integer":
                return SqlTypeName.INTEGER;
            case "float":
            case "double":
                return SqlTypeName.DOUBLE;
            case "date":
                return SqlTypeName.DATE;
            case "datetime":
            case "timestamp":
                return SqlTypeName.TIMESTAMP;
            case "boolean":
            case "bool":
                return SqlTypeName.BOOLEAN;
            default:
                return SqlTypeName.VARCHAR;
        }
    }

    /**
     * 获取数据库表名
     */
    public String getTableName() {
        return mapping.getTable();
    }

    /**
     * 获取 ID 列名
     */
    public String getIdColumnName() {
        return mapping.getIdColumn();
    }

    /**
     * 根据属性名获取数据库列名
     */
    public String getColumnName(String propertyName) {
        return mapping.getColumnName(propertyName);
    }

    /**
     * 获取 JDBC 连接。
     * 若使用 DataSource 构造，每次调用会新建连接，调用方须负责关闭。
     * 若使用 Connection 构造，返回持有的连接。
     */
    public Connection getConnection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        return connection;
    }

    /**
     * 获取数据源映射
     */
    public DataSourceMapping getMapping() {
        return mapping;
    }

    /**
     * 实现 ScannableTable 接口，扫描表数据
     * 
     * 使用 DataSource 时：每次 scan 按需取连接并归还池，避免连接泄漏。
     * 使用 Connection 时：复用持有的连接（存在泄漏风险）。
     * connection 和 dataSource 均为 null 时（延迟连接）会失败。
     */
    @Override
    public Enumerable<Object[]> scan(DataContext root) {
        if (connection == null && dataSource == null) {
            String databaseId = mapping != null ? mapping.getConnectionId() : "unknown";
            String tableName = mapping != null ? mapping.getTable() : "unknown";
            String objectTypeName = objectType != null ? objectType.getName() : "unknown";
            throw new RuntimeException(
                "Database connection is not available for object type '" + objectTypeName + 
                "'. Table mapping: " + objectTypeName + " -> " + tableName + 
                ", databaseId: " + databaseId + 
                ". Please check the database connection configuration."
            );
        }
        
        try {
            String sql = buildSelectSql();
            System.out.println("[JdbcOntologyTable] Scanning table: " + objectType.getName() + 
                             " -> " + mapping.getTable() + 
                             ", SQL: " + sql);
            
            List<Object[]> rows = new ArrayList<>();
            if (dataSource != null) {
                // 按需取连接，用毕归还池，避免同步表初始化时耗尽连接池
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        rows.add(buildRow(rs));
                    }
                }
            } else {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        rows.add(buildRow(rs));
                    }
                }
            }
            return Linq4j.asEnumerable(rows);
        } catch (Exception e) {
            String objectTypeName = objectType != null ? objectType.getName() : "unknown";
            String tableName = mapping != null ? mapping.getTable() : "unknown";
            throw new RuntimeException(
                "Failed to scan table for object type '" + objectTypeName + 
                "' (mapped to database table '" + tableName + "'): " + e.getMessage(), 
                e
            );
        }
    }

    /**
     * 构建 SELECT SQL 语句
     */
    private String buildSelectSql() {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // H2 数据库默认将未加引号的标识符转换为大写
        // 但如果我们使用引号，则保持原始大小写
        // 为了兼容，我们使用大写列名（H2 的默认行为）
        String idColumn = mapping.getIdColumn();
        sql.append(quoteIdentifier(idColumn)).append(" AS id");
        
        // 添加所有属性列（排除衍生属性和已作为ID的属性）
        for (Property prop : getEffectiveProperties()) {
            String columnName = mapping.getColumnName(prop.getName());
            if (columnName != null) {
                sql.append(", ").append(quoteIdentifier(columnName))
                   .append(" AS ").append(quoteIdentifier(prop.getName()));
            } else {
                sql.append(", ").append(quoteIdentifier(prop.getName().toUpperCase()))
                   .append(" AS ").append(quoteIdentifier(prop.getName()));
            }
        }
        
        // H2 数据库默认将未加引号的标识符转换为大写
        // 但如果我们使用引号，则保持原始大小写
        // 处理表名：如果包含 schema（如 "HIGHLINK.TOLL_STATIONS"），需要分开引用
        String tableName = mapping.getTable();
        if (tableName.contains(".")) {
            // 包含 schema，分开处理
            String[] parts = tableName.split("\\.", 2);
            sql.append(" FROM ").append(quoteIdentifier(parts[0]))
               .append(".").append(quoteIdentifier(parts[1]));
        } else {
            // 不包含 schema，直接使用
            sql.append(" FROM ").append(quoteIdentifier(tableName));
        }
        
        return sql.toString();
    }

    /**
     * 构建行数据
     * 注意：返回的数组顺序必须与 buildRowType() 中定义的字段顺序完全一致
     * 
     * 重要：对于 TIMESTAMP 类型，Calcite 期望 Long（时间戳毫秒数），而不是 java.sql.Timestamp
     */
    private Object[] buildRow(ResultSet rs) throws Exception {
        List<Object> row = new ArrayList<>();
        
        // 1. ID（索引 0）
        row.add(rs.getString("id"));
        
        // 2. 属性（按 getEffectiveProperties() 的顺序）
        for (Property prop : getEffectiveProperties()) {
            String columnName = mapping.getColumnName(prop.getName());
            Object value = null;

            if (columnName != null) {
                value = rs.getObject(prop.getName());
            } else {
                try {
                    value = rs.getObject(prop.getName());
                } catch (Exception e) {
                    value = null;
                }
            }

            // 根据属性类型转换值
            if (value != null) {
                String dataType = prop.getDataType() != null ? prop.getDataType().toLowerCase() : "";
                if ("datetime".equals(dataType) || "timestamp".equals(dataType)) {
                    if (value instanceof java.sql.Timestamp) {
                        value = ((java.sql.Timestamp) value).getTime();
                    } else if (value instanceof java.sql.Date) {
                        value = ((java.sql.Date) value).getTime();
                    }
                } else if ("date".equals(dataType)) {
                    if (value instanceof java.sql.Timestamp) {
                        value = (int)(((java.sql.Timestamp) value).getTime() / 86400000L);
                    } else if (value instanceof java.sql.Date) {
                        value = (int)(((java.sql.Date) value).getTime() / 86400000L);
                    }
                }
            }

            if (value != null && (value instanceof java.math.BigDecimal)) {
                String dataType = prop.getDataType();
                if ("float".equalsIgnoreCase(dataType) || "double".equalsIgnoreCase(dataType)) {
                    value = ((java.math.BigDecimal) value).doubleValue();
                }
            }

            if (value != null && ("int".equalsIgnoreCase(prop.getDataType()) || "integer".equalsIgnoreCase(prop.getDataType()))) {
                if (value instanceof Long) {
                    value = ((Long) value).intValue();
                } else if (value instanceof java.math.BigDecimal) {
                    value = ((java.math.BigDecimal) value).intValue();
                }
            }

            row.add(value);
        }

        return row.toArray();
    }

    /**
     * 引用标识符（根据数据库类型）
     */
    private String quoteIdentifier(String identifier) {
        // H2 使用双引号，并且标识符会被转换为大写（除非使用引号）
        // 为了保持大小写，我们使用双引号
        return "\"" + identifier + "\"";
    }
}

