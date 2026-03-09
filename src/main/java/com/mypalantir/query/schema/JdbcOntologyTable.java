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

import java.sql.Connection;
import java.sql.ResultSet;
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
    private final Connection connection;

    public JdbcOntologyTable(ObjectType objectType, DataSourceMapping mapping, Connection connection) {
        super(objectType);
        this.mapping = mapping;
        this.connection = connection;
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
        String idCol = (mapping != null && mapping.getIdColumn() != null) ? mapping.getIdColumn().toUpperCase() : "";
        if (objectType.getProperties() != null) {
            for (Property prop : objectType.getProperties()) {
                if (prop.isDerived()) {
                    continue; // 衍生属性不在数据库中，跳过
                }
                // 跳过与ID列重复的id属性
                if ("id".equals(prop.getName()) && mapping != null) {
                    String colName = mapping.getColumnName(prop.getName());
                    if (colName != null && colName.toUpperCase().equals(idCol)) {
                        continue;
                    }
                }
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
     * 获取 JDBC 连接
     */
    public Connection getConnection() {
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
     */
    @Override
    public Enumerable<Object[]> scan(DataContext root) {
        try {
            // 构建 SQL 查询
            // 注意：这里使用数据库表名和列名，然后通过 AS 别名映射回 Ontology 属性名
            String sql = buildSelectSql();
            
            // 执行查询
            List<Object[]> rows = new ArrayList<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    Object[] row = buildRow(rs);
                    rows.add(row);
                }
            }
            
            return Linq4j.asEnumerable(rows);
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan table: " + e.getMessage(), e);
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
        if (objectType.getProperties() != null) {
            for (Property prop : objectType.getProperties()) {
                if (prop.isDerived()) {
                    continue; // 衍生属性不在数据库中，跳过
                }
                // 如果属性映射到的列就是ID列，且属性名也是id，跳过避免重复
                String columnName = mapping.getColumnName(prop.getName());
                if ("id".equals(prop.getName()) && columnName != null
                    && columnName.toUpperCase().equals(idColumn)) {
                    continue;
                }
                if (columnName != null) {
                    // 使用映射中指定的列名（保持原始大小写，因为H2使用引号时大小写敏感）
                    sql.append(", ").append(quoteIdentifier(columnName))
                       .append(" AS ").append(quoteIdentifier(prop.getName()));
                } else {
                    // 如果没有映射，直接使用属性名作为列名（用于外键列，如 vehicle_id, media_id）
                    sql.append(", ").append(quoteIdentifier(prop.getName().toUpperCase()))
                       .append(" AS ").append(quoteIdentifier(prop.getName()));
                }
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
        
        // 2. 属性（按 objectType.getProperties() 的顺序，排除衍生属性和已作为ID的属性）
        String idColumnUpper = (mapping != null && mapping.getIdColumn() != null) ? mapping.getIdColumn().toUpperCase() : "";
        if (objectType.getProperties() != null) {
            for (Property prop : objectType.getProperties()) {
                if (prop.isDerived()) {
                    continue; // 衍生属性不在数据库中，跳过
                }
                // 跳过与ID列重复的id属性
                if ("id".equals(prop.getName()) && mapping != null) {
                    String colName = mapping.getColumnName(prop.getName());
                    if (colName != null && colName.toUpperCase().equals(idColumnUpper)) {
                        continue;
                    }
                }
                String columnName = mapping.getColumnName(prop.getName());
                Object value = null;
                
                if (columnName != null) {
                    // 使用属性名作为别名从 ResultSet 中获取值
                    value = rs.getObject(prop.getName());
                } else {
                    // 如果没有映射，可能是外键列（如 vehicle_id, media_id）
                    // 尝试直接使用属性名作为列名
                    try {
                        value = rs.getObject(prop.getName());
                    } catch (Exception e) {
                        // 如果获取失败，返回 null
                        value = null;
                    }
                }
                
                // 根据属性类型转换值
                // 对于日期/时间类型，Calcite 期望特定的数值类型
                if (value != null) {
                    String dataType = prop.getDataType() != null ? prop.getDataType().toLowerCase() : "";
                    if ("datetime".equals(dataType) || "timestamp".equals(dataType)) {
                        if (value instanceof java.sql.Timestamp) {
                            value = ((java.sql.Timestamp) value).getTime();
                        } else if (value instanceof java.sql.Date) {
                            value = ((java.sql.Date) value).getTime();
                        }
                    } else if ("date".equals(dataType)) {
                        // Calcite DATE 类型期望 int（从 epoch 开始的天数）
                        if (value instanceof java.sql.Timestamp) {
                            value = (int)(((java.sql.Timestamp) value).getTime() / 86400000L);
                        } else if (value instanceof java.sql.Date) {
                            value = (int)(((java.sql.Date) value).getTime() / 86400000L);
                        } else if (value instanceof String) {
                            // 如果是字符串类型的日期，直接返回字符串（交给前端处理）
                            // 不做转换
                        }
                    }
                }
                
                // 对于数值类型，如果 schema 中定义为 float/double，但数据库返回的是 BigDecimal，
                // 需要转换为 Double，避免 Calcite 聚合时类型转换错误
                // 注意：Calcite 对于 DOUBLE/FLOAT 类型期望 Double，而不是 BigDecimal
                if (value != null && (value instanceof java.math.BigDecimal)) {
                    String dataType = prop.getDataType();
                    // 如果属性类型是 float 或 double，转换为 Double
                    // 这很重要，因为 Calcite 在执行聚合时，如果字段类型是 DOUBLE，期望的是 Double，而不是 BigDecimal
                    if ("float".equalsIgnoreCase(dataType) || "double".equalsIgnoreCase(dataType)) {
                        // 转换为 Double
                        value = ((java.math.BigDecimal) value).doubleValue();
                    }
                }

                // 对于 int/integer 类型，确保值为 Integer（数据库可能返回 Long/BigDecimal）
                if (value != null && ("int".equalsIgnoreCase(prop.getDataType()) || "integer".equalsIgnoreCase(prop.getDataType()))) {
                    if (value instanceof Long) {
                        value = ((Long) value).intValue();
                    } else if (value instanceof java.math.BigDecimal) {
                        value = ((java.math.BigDecimal) value).intValue();
                    }
                }
                
                row.add(value);
            }
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

