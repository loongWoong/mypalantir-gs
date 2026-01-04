package com.mypalantir.query;

import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.query.schema.JdbcOntologyTable;
import com.mypalantir.query.schema.OntologyTable;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.service.MappingService;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.rel2sql.RelToSqlConverter;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义的 RelToSqlConverter
 * 在将 RelNode 转换为 SQL 时，自动将 Ontology 表名和列名映射为数据库实际表名和列名
 * 
 * 设计理念：
 * - RelNode 保持使用 Ontology 概念（逻辑层）
 * - SQL 生成时映射为数据库名称（物理层）
 * - 现在基于映射关系（mapping）进行转换
 */
public class OntologyRelToSqlConverter extends RelToSqlConverter {
    private final Loader loader;
    private final SqlDialect dialect;
    private final IInstanceStorage instanceStorage;
    private final MappingService mappingService;
    // 缓存 ObjectType 名称到 DataSourceMapping 的映射
    private final Map<String, DataSourceMapping> objectTypeMappingCache = new HashMap<>();
    
    public OntologyRelToSqlConverter(SqlDialect dialect, Loader loader, 
                                     IInstanceStorage instanceStorage, MappingService mappingService) {
        super(dialect);
        this.dialect = dialect;
        this.loader = loader;
        this.instanceStorage = instanceStorage;
        this.mappingService = mappingService;
    }
    
    /**
     * 重写 visit 方法，在访问 TableScan 时缓存映射信息
     */
    @Override
    public Result visit(RelNode e) {
        // 如果是 TableScan，尝试获取映射信息
        if (e instanceof TableScan) {
            TableScan scan = (TableScan) e;
            org.apache.calcite.plan.RelOptTable relOptTable = scan.getTable();
            if (relOptTable != null) {
                org.apache.calcite.schema.Table calciteTable = relOptTable.unwrap(org.apache.calcite.schema.Table.class);
                
                // 如果是 OntologyTable，获取映射信息并缓存
                if (calciteTable instanceof OntologyTable) {
                    OntologyTable ontologyTable = (OntologyTable) calciteTable;
                    ObjectType objectType = ontologyTable.getObjectType();
                    String objectTypeName = objectType.getName();
                    
                    // 获取 DataSourceMapping（优先从映射关系获取）
                    DataSourceMapping mapping = getDataSourceMappingFromMapping(objectType);
                    if (mapping == null || !mapping.isConfigured()) {
                        // 如果没有映射关系，尝试从 JdbcOntologyTable 或 schema 中获取
                        if (ontologyTable instanceof JdbcOntologyTable) {
                            mapping = ((JdbcOntologyTable) ontologyTable).getMapping();
                        } else {
                            mapping = objectType.getDataSource();
                        }
                    }
                    
                    if (mapping != null && mapping.isConfigured()) {
                        // 缓存映射信息，供后续使用
                        objectTypeMappingCache.put(objectTypeName, mapping);
                    }
                }
            }
        }
        
        // 调用父类方法
        return super.visit(e);
    }
    
    /**
     * 获取替换后的 SQL
     * 在调用 visitRoot 后，使用此方法获取映射后的 SQL
     */
    public String getMappedSql(Result result) {
        return getMappedSql(result, null);
    }
    
    /**
     * 获取替换后的 SQL（支持 JOIN 查询）
     * @param result RelToSqlConverter 的结果
     * @param query 原始查询（用于处理 JOIN 中的多个表）
     */
    public String getMappedSql(Result result, OntologyQuery query) {
        SqlNode sqlNode = result.asStatement();
        String sql = sqlNode.toSqlString(dialect).getSql();
        
        System.out.println("[OntologyRelToSqlConverter] Original SQL: " + sql);
        System.out.println("[OntologyRelToSqlConverter] Cache size: " + objectTypeMappingCache.size());
        
        // 替换所有缓存的表名和列名（主表）
        // 根据 mapping 的映射关系，将对象类型名称替换为映射表名，将属性名替换为映射列名
        for (Map.Entry<String, DataSourceMapping> entry : objectTypeMappingCache.entrySet()) {
            String objectTypeName = entry.getKey();
            DataSourceMapping mapping = entry.getValue();
            
            System.out.println("[OntologyRelToSqlConverter] Processing cached mapping for: " + objectTypeName + " -> " + mapping.getTable());
            
            // 替换表名和列名（需要 ObjectType 信息）
            try {
                ObjectType objectType = loader.getObjectType(objectTypeName);
                sql = replaceColumnNames(sql, objectType, mapping, objectTypeName, mapping.getTable());
            } catch (Loader.NotFoundException ex) {
                System.err.println("[OntologyRelToSqlConverter] ObjectType not found: " + objectTypeName);
            }
        }
        
        // 如果缓存为空，但查询不为空，直接从 mapping 获取映射关系并替换
        if (objectTypeMappingCache.isEmpty() && query != null && query.getFrom() != null) {
            System.out.println("[OntologyRelToSqlConverter] Cache is empty, getting mapping for: " + query.getFrom());
            try {
                ObjectType objectType = loader.getObjectType(query.getFrom());
                DataSourceMapping mapping = getDataSourceMappingFromMapping(objectType);
                if (mapping == null) {
                    System.out.println("[OntologyRelToSqlConverter] No mapping found, using schema data_source");
                    mapping = objectType.getDataSource();
                }
                
                // 对于 SQL 替换，我们只需要表名和字段映射，不需要 connectionId
                // 所以检查表名和字段映射是否存在，而不是 isConfigured()
                if (mapping != null && mapping.getTable() != null && !mapping.getTable().isEmpty() 
                    && mapping.getFieldMapping() != null && !mapping.getFieldMapping().isEmpty()) {
                    System.out.println("[OntologyRelToSqlConverter] Using mapping: " + mapping.getTable() + ", fieldMapping: " + mapping.getFieldMapping());
                    // 替换表名和列名
                    sql = replaceColumnNames(sql, objectType, mapping, query.getFrom(), mapping.getTable());
                } else {
                    System.err.println("[OntologyRelToSqlConverter] Mapping is null or missing table/fieldMapping");
                    if (mapping != null) {
                        System.err.println("[OntologyRelToSqlConverter] Mapping details: table=" + mapping.getTable() + ", fieldMapping=" + mapping.getFieldMapping());
                    }
                }
            } catch (Loader.NotFoundException ex) {
                System.err.println("[OntologyRelToSqlConverter] ObjectType not found: " + query.getFrom());
            } catch (Exception ex) {
                System.err.println("[OntologyRelToSqlConverter] Error getting mapping: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        
        // 处理 JOIN 查询中的表名和列名
        if (query != null && query.getLinks() != null && !query.getLinks().isEmpty()) {
            for (OntologyQuery.LinkQuery linkQuery : query.getLinks()) {
                try {
                    com.mypalantir.meta.LinkType linkType = loader.getLinkType(linkQuery.getName());
                    if (linkType.getDataSource() != null && linkType.getDataSource().isConfigured()) {
                        DataSourceMapping linkMapping = linkType.getDataSource();
                        
                        // 1. 不替换中间表名，因为 Calcite Schema 中的表名就是 linkMapping.getTable()
                        // JdbcOntologyTable.scan() 会使用正确的数据库表名来执行查询
                        String linkTableNameInSchema = linkMapping.getTable();  // 小写，如 "vehicle_media"
                        
                        // 2. 替换中间表的列名（source_id_column, target_id_column）
                        String linkSourceIdColumn = linkMapping.getSourceIdColumn();
                        String linkTargetIdColumn = linkMapping.getTargetIdColumn();
                        
                        // 3. 修复 JOIN 条件中的字段名错误
                        // 问题：Calcite 生成的 SQL 中，JOIN 条件可能使用了错误的字段名
                        // 例如："车辆"."MEDIA_ID" 应该是 "车辆"."id"（车辆表的 id 字段）
                        // 注意：SQL 中的字段名应该是 Ontology 概念（"id"），而不是数据库列名
                        String sourceObjectTypeName = linkType.getSourceType();
                        try {
                            ObjectType sourceObjectType = loader.getObjectType(sourceObjectTypeName);
                            // 从映射关系获取源表的映射
                            DataSourceMapping sourceMapping = getDataSourceMappingFromMapping(sourceObjectType);
                            if (sourceMapping == null || !sourceMapping.isConfigured()) {
                                sourceMapping = sourceObjectType.getDataSource();
                            }
                            
                            if (sourceMapping != null && sourceMapping.isConfigured()) {
                                // 替换源表的表名和列名
                                sql = replaceColumnNames(sql, sourceObjectType, sourceMapping, 
                                                        sourceObjectTypeName, sourceMapping.getTable());
                            }
                        } catch (Loader.NotFoundException ex) {
                            // 忽略
                        }
                        
                        // 注意：中间表的列名（source_id_column, target_id_column）在 Calcite Schema 中
                        // 就是这些列名本身（如 "vehicle_id", "media_id"），不需要替换
                        // 因为 JdbcOntologyTable.scan() 会通过 AS 别名映射回这些列名
                        
                        // 替换目标表的表名和列名（基于映射关系）
                        try {
                            ObjectType targetObjectType = loader.getObjectType(linkType.getTargetType());
                            // 从映射关系获取目标表的映射
                            DataSourceMapping targetMapping = getDataSourceMappingFromMapping(targetObjectType);
                            if (targetMapping == null || !targetMapping.isConfigured()) {
                                targetMapping = targetObjectType.getDataSource();
                            }
                            
                            if (targetMapping != null && targetMapping.isConfigured()) {
                                // 替换目标表的表名和列名
                                sql = replaceColumnNames(sql, targetObjectType, targetMapping, 
                                                        linkType.getTargetType(), targetMapping.getTable());
                            }
                        } catch (Loader.NotFoundException ex) {
                            // 忽略
                        }
                    }
                } catch (Loader.NotFoundException ex) {
                    // 忽略
                }
            }
        }
        
        System.out.println("[OntologyRelToSqlConverter] Mapped SQL (before dialect adaptation): " + sql);
        
        // 根据数据库类型适配 SQL 语法
        SqlDialectAdapter.DatabaseType dbType = SqlDialectAdapter.DatabaseType.MYSQL; // 默认 MySQL
        if (query != null && query.getFrom() != null) {
            try {
                ObjectType objectType = loader.getObjectType(query.getFrom());
                DataSourceMapping mapping = getDataSourceMappingFromMapping(objectType);
                if (mapping != null && mapping.getConnectionId() != null) {
                    // 从 databaseId 获取数据库类型
                    String databaseId = mapping.getConnectionId();
                    if (!databaseId.equals("default")) {
                        try {
                            Map<String, Object> database = instanceStorage.getInstance("database", databaseId);
                            String databaseType = (String) database.get("type");
                            if (databaseType == null || databaseType.isEmpty()) {
                                // 尝试从 JDBC URL 判断
                                String jdbcUrl = (String) database.get("jdbc_url");
                                if (jdbcUrl != null && !jdbcUrl.isEmpty()) {
                                    dbType = SqlDialectAdapter.getDatabaseTypeFromJdbcUrl(jdbcUrl);
                                }
                            } else {
                                dbType = SqlDialectAdapter.getDatabaseType(databaseType);
                            }
                        } catch (Exception e) {
                            System.err.println("[OntologyRelToSqlConverter] Failed to get database type, using MySQL: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[OntologyRelToSqlConverter] Error getting database type, using MySQL: " + e.getMessage());
            }
        }
        
        // 适配 SQL 语法（FETCH -> LIMIT 等）
        sql = SqlDialectAdapter.adaptSql(sql, dbType);
        
        // 对于 MySQL，移除所有标识符的引号
        if (dbType == SqlDialectAdapter.DatabaseType.MYSQL) {
            // 移除双引号：只替换标识符的引号，不替换字符串字面量的引号
            sql = sql.replaceAll("\"([A-Za-z_][A-Za-z0-9_]*)\"", "$1");
            // 移除反引号
            sql = sql.replaceAll("`([A-Za-z_][A-Za-z0-9_]*)`", "$1");
        }
        
        System.out.println("[OntologyRelToSqlConverter] Final SQL (after dialect adaptation): " + sql);
        
        return sql;
    }
    
    /**
     * 替换 SQL 中的表名
     */
    private String replaceTableName(String sql, String ontologyTableName, String dbTableName) {
        // 替换带引号的表名（大小写不敏感）
        String quotedOntologyName = "\"" + ontologyTableName + "\"";
        String quotedDbName = "\"" + dbTableName + "\"";
        // 使用大小写不敏感匹配
        sql = sql.replaceAll("(?i)" + java.util.regex.Pattern.quote(quotedOntologyName), quotedDbName);
        // 替换不带引号的表名（在 FROM、JOIN 子句中）
        sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(ontologyTableName) + "\\b", 
                            "\"" + dbTableName + "\"");
        // 处理可能的双引号问题（如果表名已经被引号包围）
        sql = sql.replaceAll("\"\"+", "\"");  // 将多个连续的双引号替换为单个双引号
        return sql;
    }
    
    /**
     * 替换列名
     * 根据 mapping 的映射关系，将 Ontology 属性名替换为数据库列名
     * 
     * @param sql SQL 语句
     * @param objectType 对象类型
     * @param mapping 数据源映射
     * @param objectTypeName 对象类型名称（用于精确匹配表名）
     * @param dbTableName 数据库表名（用于精确匹配表名）
     */
    private String replaceColumnNames(String sql, ObjectType objectType, DataSourceMapping mapping, 
                                      String objectTypeName, String dbTableName) {
        if (mapping == null || !mapping.isConfigured()) {
            System.err.println("[replaceColumnNames] Mapping is null or not configured for: " + objectTypeName);
            return sql;
        }
        
        // 使用传入的 dbTableName（应该是映射表名），如果没有则使用 mapping.getTable()
        String actualDbTableName = (dbTableName != null && !dbTableName.isEmpty()) ? dbTableName : mapping.getTable();
        
        System.out.println("[replaceColumnNames] Replacing " + objectTypeName + " -> " + actualDbTableName);
        
        // 获取数据库类型，决定是否使用引号
        SqlDialectAdapter.DatabaseType dbType = SqlDialectAdapter.DatabaseType.MYSQL; // 默认 MySQL
        String quoteChar = ""; // MySQL 不使用引号
        
        if (mapping.getConnectionId() != null && !mapping.getConnectionId().equals("default")) {
            try {
                Map<String, Object> database = instanceStorage.getInstance("database", mapping.getConnectionId());
                String databaseType = (String) database.get("type");
                if (databaseType == null || databaseType.isEmpty()) {
                    // 尝试从 JDBC URL 判断
                    String jdbcUrl = (String) database.get("jdbc_url");
                    if (jdbcUrl != null && !jdbcUrl.isEmpty()) {
                        dbType = SqlDialectAdapter.getDatabaseTypeFromJdbcUrl(jdbcUrl);
                    }
                } else {
                    dbType = SqlDialectAdapter.getDatabaseType(databaseType);
                }
                
                // 根据数据库类型设置引号字符
                switch (dbType) {
                    case MYSQL:
                        quoteChar = ""; // MySQL 不使用引号
                        break;
                    case POSTGRESQL:
                        quoteChar = "\""; // PostgreSQL 使用双引号
                        break;
                    case ORACLE:
                        quoteChar = "\""; // Oracle 使用双引号
                        break;
                    case SQLSERVER:
                        quoteChar = "["; // SQL Server 使用方括号，但也可以用双引号
                        break;
                    case H2:
                        quoteChar = "\""; // H2 使用双引号
                        break;
                    default:
                        quoteChar = ""; // 默认不使用引号
                }
            } catch (Exception e) {
                System.err.println("[replaceColumnNames] Failed to get database type, using MySQL (no quotes): " + e.getMessage());
            }
        }
        
        System.out.println("[replaceColumnNames] Database type: " + dbType + ", quoteChar: '" + quoteChar + "'");
        
        // 替换表名：将对象类型名称替换为映射表名
        String dbTableNameQuoted = quoteChar.isEmpty() ? actualDbTableName : (quoteChar + actualDbTableName + quoteChar);
        // 替换带引号的表名（双引号或反引号）
        String beforeTableReplace = sql;
        sql = sql.replaceAll("(?i)\"" + java.util.regex.Pattern.quote(objectTypeName) + "\"", dbTableNameQuoted);
        sql = sql.replaceAll("(?i)`" + java.util.regex.Pattern.quote(objectTypeName) + "`", dbTableNameQuoted);
        // 替换不带引号的表名（在 FROM、JOIN 子句中）
        sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(objectTypeName) + "\\b", dbTableNameQuoted);
        if (!beforeTableReplace.equals(sql)) {
            System.out.println("[replaceColumnNames] Table name replaced: " + objectTypeName + " -> " + dbTableNameQuoted);
        }
        
        // 替换列名：将属性名替换为数据库列名
        // 注意：先替换带表名前缀的，再替换不带表名前缀的，避免误替换
        if (objectType.getProperties() != null) {
            System.out.println("[replaceColumnNames] Processing " + objectType.getProperties().size() + " properties");
            for (com.mypalantir.meta.Property prop : objectType.getProperties()) {
                String propertyName = prop.getName();
                String columnName = mapping.getColumnName(propertyName);
                System.out.println("[replaceColumnNames] Property: " + propertyName + " -> Column: " + columnName);
                if (columnName != null && !columnName.equals(propertyName)) {
                    // 根据数据库类型决定是否使用引号
                    String dbColumnNameQuoted = quoteChar.isEmpty() ? columnName : (quoteChar + columnName + quoteChar);
                    String beforeReplace = sql;
                    // 1. 替换带引号的列名（带表名前缀）："Path"."pass_id" -> TBL_PATH.PASSID 或 "TBL_PATH"."PASSID"
                    sql = sql.replaceAll("(?i)\"" + java.util.regex.Pattern.quote(objectTypeName) + "\"\\.\"" + 
                                        java.util.regex.Pattern.quote(propertyName) + "\"",
                                        dbTableNameQuoted + "." + dbColumnNameQuoted);
                    sql = sql.replaceAll("(?i)`" + java.util.regex.Pattern.quote(objectTypeName) + "`\\.`" + 
                                        java.util.regex.Pattern.quote(propertyName) + "`",
                                        dbTableNameQuoted + "." + dbColumnNameQuoted);
                    // 2. 替换不带引号的列名（带表名前缀）：Path.pass_id -> TBL_PATH.PASSID 或 "TBL_PATH"."PASSID"
                    sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(objectTypeName) + "\\." + 
                                        java.util.regex.Pattern.quote(propertyName) + "\\b",
                                        dbTableNameQuoted + "." + dbColumnNameQuoted);
                    // 3. 替换带引号的列名（不带表名前缀）："pass_id" -> PASSID 或 "PASSID"
                    // 注意：只在 SELECT、WHERE、ORDER BY 等子句中，不在表名后面
                    sql = sql.replaceAll("(?i)\"" + java.util.regex.Pattern.quote(propertyName) + "\"", 
                                        dbColumnNameQuoted);
                    sql = sql.replaceAll("(?i)`" + java.util.regex.Pattern.quote(propertyName) + "`", 
                                        dbColumnNameQuoted);
                    // 4. 替换不带引号的列名（不带表名前缀）：pass_id -> PASSID 或 "PASSID"
                    // 使用单词边界确保只替换列名，不替换表名中的部分
                    sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(propertyName) + "\\b", 
                                        dbColumnNameQuoted);
                    if (!beforeReplace.equals(sql)) {
                        System.out.println("[replaceColumnNames] Column replaced: " + propertyName + " -> " + dbColumnNameQuoted);
                    }
                } else if (columnName == null) {
                    System.err.println("[replaceColumnNames] No column mapping found for property: " + propertyName);
                }
            }
        } else {
            System.err.println("[replaceColumnNames] ObjectType has no properties");
        }
        
        // 替换 ID 列名
        String idColumnName = mapping.getIdColumn();
        if (idColumnName != null) {
            String idColumnNameQuoted = quoteChar.isEmpty() ? idColumnName : (quoteChar + idColumnName + quoteChar);
            // 替换带引号的 id（带表名前缀）
            sql = sql.replaceAll("(?i)\"" + java.util.regex.Pattern.quote(objectTypeName) + "\"\\.\"id\"",
                                dbTableNameQuoted + "." + idColumnNameQuoted);
            sql = sql.replaceAll("(?i)`" + java.util.regex.Pattern.quote(objectTypeName) + "`\\.`id`",
                                dbTableNameQuoted + "." + idColumnNameQuoted);
            // 替换带引号的 id（不带表名前缀）
            sql = sql.replaceAll("(?i)\"id\"", idColumnNameQuoted);
            sql = sql.replaceAll("(?i)`id`", idColumnNameQuoted);
            // 替换不带引号的 id（带表名前缀）
            sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(objectTypeName) + "\\.id\\b",
                                dbTableNameQuoted + "." + idColumnNameQuoted);
            // 替换不带引号的 id（不带表名前缀）
            sql = sql.replaceAll("(?i)\\bid\\b", idColumnNameQuoted);
        }
        
        // 最后，根据数据库类型清理剩余的引号
        // 对于 MySQL，移除所有标识符的引号（双引号和反引号）
        if (dbType == SqlDialectAdapter.DatabaseType.MYSQL) {
            // 移除双引号：只替换标识符的引号，不替换字符串字面量的引号
            sql = sql.replaceAll("\"([A-Za-z_][A-Za-z0-9_]*)\"", "$1");
            // 移除反引号
            sql = sql.replaceAll("`([A-Za-z_][A-Za-z0-9_]*)`", "$1");
        } else if (!quoteChar.isEmpty()) {
            // 对于其他数据库，将双引号和反引号统一为目标引号
            if (quoteChar.equals("\"")) {
                // 目标使用双引号，将反引号替换为双引号
                sql = sql.replaceAll("`([A-Za-z_][A-Za-z0-9_]*)`", "\"$1\"");
            } else {
                // 目标使用其他引号，将双引号和反引号替换为目标引号
                sql = sql.replaceAll("[\"`]([A-Za-z_][A-Za-z0-9_]*)[\"`]", quoteChar + "$1" + quoteChar);
            }
        }
        
        return sql;
    }
    
    /**
     * 从映射关系获取 DataSourceMapping
     */
    private DataSourceMapping getDataSourceMappingFromMapping(ObjectType objectType) {
        try {
            System.out.println("[getDataSourceMappingFromMapping] Getting mapping for: " + objectType.getName());
            List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectType.getName());
            if (mappings == null || mappings.isEmpty()) {
                System.out.println("[getDataSourceMappingFromMapping] No mappings found for: " + objectType.getName());
                return null;
            }
            
            System.out.println("[getDataSourceMappingFromMapping] Found " + mappings.size() + " mapping(s)");
            
            // 使用第一个映射关系
            Map<String, Object> mappingData = mappings.get(0);
            String tableId = (String) mappingData.get("table_id");
            if (tableId == null) {
                System.err.println("[getDataSourceMappingFromMapping] table_id is null");
                return null;
            }
            
            System.out.println("[getDataSourceMappingFromMapping] table_id: " + tableId);
            
            // 获取表信息
            Map<String, Object> table = instanceStorage.getInstance("table", tableId);
            String tableName = (String) table.get("name");
            String databaseId = (String) table.get("database_id");
            String primaryKeyColumn = (String) mappingData.get("primary_key_column");
            
            System.out.println("[getDataSourceMappingFromMapping] table_name: " + tableName + ", database_id: " + databaseId + ", primary_key_column: " + primaryKeyColumn);
            
            // 获取数据库类型（用于 SQL 方言适配）
            String databaseType = null;
            if (databaseId != null) {
                try {
                    Map<String, Object> database = instanceStorage.getInstance("database", databaseId);
                    databaseType = (String) database.get("type");
                    if (databaseType == null || databaseType.isEmpty()) {
                        // 如果没有 type 字段，尝试从 JDBC URL 判断
                        String jdbcUrl = (String) database.get("jdbc_url");
                        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
                            // 尝试构建 JDBC URL
                            String host = (String) database.get("host");
                            if (host != null && host.contains("mysql")) {
                                databaseType = "mysql";
                            }
                        } else {
                            databaseType = SqlDialectAdapter.getDatabaseTypeFromJdbcUrl(jdbcUrl).name().toLowerCase();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[getDataSourceMappingFromMapping] Failed to get database type: " + e.getMessage());
                }
            }
            
            @SuppressWarnings("unchecked")
            Map<String, String> columnPropertyMappings = (Map<String, String>) mappingData.get("column_property_mappings");
            
            // column_property_mappings 的结构是 {列名: 属性名}，需要反转为 {属性名: 列名}
            Map<String, String> fieldMapping = new HashMap<>();
            if (columnPropertyMappings != null) {
                System.out.println("[getDataSourceMappingFromMapping] column_property_mappings size: " + columnPropertyMappings.size());
                for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                    String columnName = entry.getKey();
                    String propertyName = entry.getValue();
                    fieldMapping.put(propertyName, columnName);
                    System.out.println("[getDataSourceMappingFromMapping] Mapping: " + propertyName + " -> " + columnName);
                }
            } else {
                System.err.println("[getDataSourceMappingFromMapping] column_property_mappings is null");
            }
            
            DataSourceMapping dataSourceMapping = new DataSourceMapping();
            dataSourceMapping.setTable(tableName);
            dataSourceMapping.setIdColumn(primaryKeyColumn != null ? primaryKeyColumn : "id");
            dataSourceMapping.setFieldMapping(fieldMapping);
            // 设置 connectionId（使用 databaseId，如果为 null 则使用 "default"）
            dataSourceMapping.setConnectionId(databaseId != null ? databaseId : "default");
            
            // 将数据库类型存储到 mapping 的 properties 中（如果 DataSourceMapping 支持的话）
            // 或者我们可以创建一个扩展的 DataSourceMapping 来存储额外信息
            // 暂时先存储到 connectionId 的注释中，或者创建一个新的字段
            
            System.out.println("[getDataSourceMappingFromMapping] Created DataSourceMapping: table=" + tableName + ", idColumn=" + dataSourceMapping.getIdColumn() + ", connectionId=" + dataSourceMapping.getConnectionId() + ", databaseType=" + databaseType + ", fieldMapping size=" + fieldMapping.size());
            
            // 将数据库类型存储到对象中（通过扩展 DataSourceMapping 或使用临时存储）
            // 暂时使用一个 Map 来存储额外的元数据
            if (databaseType != null) {
                // 我们可以将 databaseType 存储到 DataSourceMapping 的某个字段中
                // 但 DataSourceMapping 可能没有这个字段，所以我们需要在调用时传递
            }
            
            return dataSourceMapping;
        } catch (Exception e) {
            System.err.println("[getDataSourceMappingFromMapping] Failed to get DataSourceMapping from mapping for " + objectType.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
