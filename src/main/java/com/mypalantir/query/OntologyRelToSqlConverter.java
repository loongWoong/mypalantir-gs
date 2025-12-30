package com.mypalantir.query;

import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.query.schema.JdbcOntologyTable;
import com.mypalantir.query.schema.OntologyTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.rel2sql.RelToSqlConverter;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlNode;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义的 RelToSqlConverter
 * 在将 RelNode 转换为 SQL 时，自动将 Ontology 表名和列名映射为数据库实际表名和列名
 * 
 * 设计理念：
 * - RelNode 保持使用 Ontology 概念（逻辑层）
 * - SQL 生成时映射为数据库名称（物理层）
 */
public class OntologyRelToSqlConverter extends RelToSqlConverter {
    private final Loader loader;
    private final SqlDialect dialect;
    // 缓存 ObjectType 名称到 DataSourceMapping 的映射
    private final Map<String, DataSourceMapping> objectTypeMappingCache = new HashMap<>();
    
    public OntologyRelToSqlConverter(SqlDialect dialect, Loader loader) {
        super(dialect);
        this.dialect = dialect;
        this.loader = loader;
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
                    
                    // 获取 DataSourceMapping
                    DataSourceMapping mapping = null;
                    if (ontologyTable instanceof JdbcOntologyTable) {
                        mapping = ((JdbcOntologyTable) ontologyTable).getMapping();
                    } else {
                        mapping = objectType.getDataSource();
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
        
        // 调试：打印原始 SQL
        System.out.println("=== Original SQL from Calcite ===");
        System.out.println(sql);
        System.out.println("================================");
        
        // 替换所有缓存的列名（主表）
        // 注意：不替换表名，因为 Calcite Schema 中的表名是对象类型名称
        // JdbcOntologyTable.scan() 会使用正确的数据库表名来执行查询
        for (Map.Entry<String, DataSourceMapping> entry : objectTypeMappingCache.entrySet()) {
            String objectTypeName = entry.getKey();
            DataSourceMapping mapping = entry.getValue();
            
            // 只替换列名（需要 ObjectType 信息，传入对象类型名称作为表名）
            try {
                ObjectType objectType = loader.getObjectType(objectTypeName);
                sql = replaceColumnNames(sql, objectType, mapping, objectTypeName, objectTypeName);
            } catch (Loader.NotFoundException ex) {
                // 忽略
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
                        // 例如："车辆"."MEDIA_ID" 应该是 "车辆"."id" 或 "车辆"."VEHICLE_ID"（车辆表的 id 字段）
                        String sourceObjectTypeName = linkType.getSourceType();
                        try {
                            ObjectType sourceObjectType = loader.getObjectType(sourceObjectTypeName);
                            if (sourceObjectType.getDataSource() != null && sourceObjectType.getDataSource().isConfigured()) {
                                DataSourceMapping sourceMapping = sourceObjectType.getDataSource();
                                String sourceIdColumn = sourceMapping.getIdColumn();  // 保持原始大小写
                                String sourceTableName = sourceMapping.getTable();  // 保持原始大小写
                                
                                // 修复第一个 JOIN 条件：将错误的字段名替换为正确的 id 列名
                                // 问题：Calcite 可能使用目标表的 ID 列名（如 "MEDIA_ID"）而不是源表的 ID 列名
                                // 解决方案：替换所有可能的错误模式
                                
                                // 修复源表错误使用目标表ID列名的问题
                                // 模式1: "车辆"."MEDIA_ID" -> "车辆"."VEHICLE_ID" (使用对象类型名)
                                String targetIdUpper = linkMapping.getTargetIdColumn().toUpperCase();
                                sql = sql.replaceAll(
                                    "(?i)\"" + java.util.regex.Pattern.quote(sourceObjectTypeName) + "\"\\.\"" + 
                                    java.util.regex.Pattern.quote(linkMapping.getTargetIdColumn()) + "\"",
                                    "\"" + sourceObjectTypeName + "\".\"" + sourceIdColumn + "\""
                                );
                                sql = sql.replaceAll(
                                    "(?i)\"" + java.util.regex.Pattern.quote(sourceObjectTypeName) + "\"\\.\"" + 
                                    java.util.regex.Pattern.quote(targetIdUpper) + "\"",
                                    "\"" + sourceObjectTypeName + "\".\"" + sourceIdColumn + "\""
                                );
                                
                                // 模式2: "vehicles"."MEDIA_ID" -> "vehicles"."VEHICLE_ID" (使用数据库表名)
                                sql = sql.replaceAll(
                                    "(?i)\"" + java.util.regex.Pattern.quote(sourceTableName) + "\"\\.\"" + 
                                    java.util.regex.Pattern.quote(linkMapping.getTargetIdColumn()) + "\"",
                                    "\"" + sourceTableName + "\".\"" + sourceIdColumn + "\""
                                );
                                sql = sql.replaceAll(
                                    "(?i)\"" + java.util.regex.Pattern.quote(sourceTableName) + "\"\\.\"" + 
                                    java.util.regex.Pattern.quote(targetIdUpper) + "\"",
                                    "\"" + sourceTableName + "\".\"" + sourceIdColumn + "\""
                                );
                                
                                // 模式3: 如果 Calcite 使用了 "id" 字段名，但我们需要确保它映射到正确的数据库列名
                                // 这个已经在 replaceColumnNames 中处理了
                            }
                        } catch (Loader.NotFoundException ex) {
                            // 忽略
                        }
                        
                        // 替换中间表的列名引用（只替换中间表的列名，避免影响其他表）
                        // 注意：不替换表名，保持使用 linkTableNameInSchema
                        if (linkSourceIdColumn != null) {
                            // 保持列名原始大小写（H2区分大小写）
                            // 只替换中间表的 source_id_column（匹配表名+列名）
                            // 匹配格式：表名.列名 或 "表名"."列名"
                            sql = sql.replaceAll(
                                "(?i)\"" + java.util.regex.Pattern.quote(linkTableNameInSchema) + "\"\\.\"" + 
                                java.util.regex.Pattern.quote(linkSourceIdColumn) + "\"",
                                "\"" + linkTableNameInSchema + "\".\"" + linkSourceIdColumn + "\""
                            );
                            // 替换不带引号的格式（使用单词边界，但需要确保是中间表的列）
                            sql = sql.replaceAll(
                                "(?i)\\b" + java.util.regex.Pattern.quote(linkTableNameInSchema) + "\\." + 
                                java.util.regex.Pattern.quote(linkSourceIdColumn) + "\\b",
                                "\"" + linkTableNameInSchema + "\".\"" + linkSourceIdColumn + "\""
                            );
                        }
                        if (linkTargetIdColumn != null) {
                            // 保持列名原始大小写（H2区分大小写）
                            // 只替换中间表的 target_id_column（匹配表名+列名）
                            // 匹配格式：表名.列名 或 "表名"."列名"
                            sql = sql.replaceAll(
                                "(?i)\"" + java.util.regex.Pattern.quote(linkTableNameInSchema) + "\"\\.\"" + 
                                java.util.regex.Pattern.quote(linkTargetIdColumn) + "\"",
                                "\"" + linkTableNameInSchema + "\".\"" + linkTargetIdColumn + "\""
                            );
                            // 替换不带引号的格式（使用单词边界，但需要确保是中间表的列）
                            sql = sql.replaceAll(
                                "(?i)\\b" + java.util.regex.Pattern.quote(linkTableNameInSchema) + "\\." + 
                                java.util.regex.Pattern.quote(linkTargetIdColumn) + "\\b",
                                "\"" + linkTableNameInSchema + "\".\"" + linkTargetIdColumn + "\""
                            );
                        }
                        
                        // 替换 link type 属性的列名
                        if (linkType.getProperties() != null) {
                            for (com.mypalantir.meta.Property linkProp : linkType.getProperties()) {
                                String columnName = linkMapping.getColumnName(linkProp.getName());
                                if (columnName != null) {
                                    String dbColumnName = columnName.toUpperCase();
                                    sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(linkProp.getName()) + "\\b", 
                                                        "\"" + dbColumnName + "\"");
                                }
                            }
                        }
                        
                        // 替换目标表的列名（不替换表名，保持使用对象类型名称）
                        ObjectType targetObjectType = loader.getObjectType(linkType.getTargetType());
                        if (targetObjectType.getDataSource() != null && targetObjectType.getDataSource().isConfigured()) {
                            DataSourceMapping targetMapping = targetObjectType.getDataSource();
                            // 不替换表名，因为 Calcite Schema 中的表名是对象类型名称
                            sql = replaceColumnNames(sql, targetObjectType, targetMapping, 
                                                     linkType.getTargetType(), linkType.getTargetType());
                        }
                    }
                } catch (Loader.NotFoundException ex) {
                    // 忽略
                }
            }
        }
        
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
     * @param sql SQL 语句
     * @param objectType 对象类型
     * @param mapping 数据源映射
     * @param objectTypeName 对象类型名称（用于精确匹配表名）
     * @param dbTableName 数据库表名（用于精确匹配表名）
     */
    private String replaceColumnNames(String sql, ObjectType objectType, DataSourceMapping mapping, 
                                      String objectTypeName, String dbTableName) {
        // 注意：不替换属性列名！
        // 因为 Calcite Schema 中的字段名就是属性名（如 "介质编号"），Calcite 执行 SQL 时查找的是 Schema 中的字段名
        // 数据库列名映射在 JdbcOntologyTable.scan() 中处理（通过 AS 别名）
        // 所以 SQL 中的字段名应该保持为属性名，而不是数据库列名（如 "MEDIA_NUMBER"）
        
        // 注意：不替换 ID 列名！
        // 因为 Calcite Schema 中的字段名是 "id"，Calcite 执行 SQL 时查找的是 Schema 中的字段名
        // 数据库列名映射在 JdbcOntologyTable.scan() 中处理
        // 所以 SQL 中的字段名应该保持为 "id"，而不是数据库列名（如 "media_id"）
        
        return sql;
    }
}
