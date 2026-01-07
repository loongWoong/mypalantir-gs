package com.mypalantir.query;

import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.Loader;
import com.mypalantir.query.schema.OntologySchemaFactory;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.service.MappingService;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorImpl;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询执行器
 * 将 OntologyQuery 转换为 Calcite RelNode 并执行
 */
public class QueryExecutor {
    private final Loader loader;
    private final IInstanceStorage instanceStorage;
    private final MappingService mappingService;
    private final com.mypalantir.service.DatabaseMetadataService databaseMetadataService;
    private final RelNodeBuilder relNodeBuilder;
    private SchemaPlus rootSchema;
    private Connection calciteConnection;
    private org.apache.calcite.tools.FrameworkConfig frameworkConfig;

    public QueryExecutor(Loader loader, IInstanceStorage instanceStorage,
                         com.mypalantir.service.MappingService mappingService,
                         com.mypalantir.service.DatabaseMetadataService databaseMetadataService) {
        this.loader = loader;
        this.instanceStorage = instanceStorage;
        this.mappingService = mappingService;
        this.databaseMetadataService = databaseMetadataService;
        this.relNodeBuilder = new RelNodeBuilder(loader, instanceStorage, mappingService, databaseMetadataService);
    }

    /**
     * 初始化 Calcite Schema
     */
    public void initialize() throws SQLException {
        // 初始化 RelNodeBuilder（会创建 Schema 和 FrameworkConfig）
        relNodeBuilder.initialize();
        rootSchema = relNodeBuilder.rootSchema;
        frameworkConfig = relNodeBuilder.frameworkConfig;
        
        // 创建 Calcite 连接用于执行
        Connection connection = DriverManager.getConnection("jdbc:calcite:");
        CalciteConnection calciteConn = connection.unwrap(CalciteConnection.class);
        SchemaPlus calciteRootSchema = calciteConn.getRootSchema();
        
        // 将 rootSchema 中的表复制到 calciteRootSchema
        if (rootSchema != null) {
            for (String tableName : rootSchema.getTableNames()) {
                calciteRootSchema.add(tableName, rootSchema.getTable(tableName));
            }
        }
        
        this.calciteConnection = calciteConn;
    }

    /**
     * 执行查询
     */
    public QueryResult execute(OntologyQuery query) throws Exception {
        if (rootSchema == null) {
            initialize();
        }

        // 直接构建 RelNode
        System.out.println("\n" + "=".repeat(80));
        System.out.println("=== Building RelNode ===");
        org.apache.calcite.rel.RelNode relNode = relNodeBuilder.buildRelNode(query);
        
        // 打印 RelNode 信息（递归显示整个树）
        System.out.println("RelNode Tree Structure:");
        printRelNodeTree(relNode, 0);
        System.out.println("=".repeat(80) + "\n");
        
        // 优化 RelNode
        org.apache.calcite.rel.RelNode optimizedRelNode = optimizeRelNode(relNode);
        
        // 执行 RelNode（传入原始查询以便回退）
        return executeRelNode(optimizedRelNode, query);
    }
    
    /**
     * 递归打印 RelNode 树结构
     */
    private void printRelNodeTree(org.apache.calcite.rel.RelNode relNode, int indent) {
        String indentStr = "  ".repeat(indent);
        System.out.println(indentStr + "└─ " + relNode.getClass().getSimpleName());
        System.out.println(indentStr + "   RowType: " + relNode.getRowType());
        System.out.println(indentStr + "   Fields:");
        for (int i = 0; i < relNode.getRowType().getFieldCount(); i++) {
            org.apache.calcite.rel.type.RelDataTypeField field = relNode.getRowType().getFieldList().get(i);
            System.out.println(indentStr + "     [" + i + "] " + field.getName() + " : " + field.getType());
        }
        System.out.println(indentStr + "   Description: " + relNode.toString());
        
        // 递归打印输入节点
        if (relNode.getInputs() != null && !relNode.getInputs().isEmpty()) {
            System.out.println(indentStr + "   Inputs:");
            for (int i = 0; i < relNode.getInputs().size(); i++) {
                printRelNodeTree(relNode.getInputs().get(i), indent + 1);
            }
        }
    }
    
    /**
     * 优化 RelNode（暂时简化，直接返回）
     * TODO: 实现完整的优化器
     */
    private org.apache.calcite.rel.RelNode optimizeRelNode(org.apache.calcite.rel.RelNode relNode) {
        // 暂时不优化，直接返回
        // TODO: 实现完整的 VolcanoPlanner 优化
        return relNode;
    }
    
    /**
     * 执行 RelNode
     * 
     * 说明：为什么中间还有 SQL？
     * 
     * 1. **我们构建了 RelNode**（关系代数表达式）
     *    - 这是查询的逻辑表示
     *    - 包含了 TableScan、Filter、Project、Sort、Limit 等操作
     * 
     * 2. **但 Calcite 的执行机制是**：
     *    - RelNode → SQL → 执行
     *    - 这是因为我们的 Table 实现（JdbcOntologyTable）的 scan() 方法
     *      最终还是执行 SQL 查询数据库
     * 
     * 3. **为什么不能直接执行 RelNode？**
     *    - 理论上可以，但需要将 Logical RelNode 转换为 Enumerable RelNode
     *    - 这需要配置规则引擎和 DataContext，比较复杂
     *    - 对于我们的场景（JDBC 数据源），SQL 执行是更自然的方式
     * 
     * 4. **RelNode 的价值**：
     *    - 提供了查询的逻辑表示（关系代数）
     *    - 可以进行查询优化（虽然当前未启用）
     *    - 为未来支持 JOIN、聚合等复杂查询打下基础
     *    - 可以转换为不同数据库的 SQL（SQL 下推）
     * 
     * 所以流程是：OntologyQuery → RelNode（逻辑计划）→ SQL（执行计划）→ 执行
     */
    private QueryResult executeRelNode(org.apache.calcite.rel.RelNode relNode, OntologyQuery originalQuery) throws Exception {
        // 将 RelNode 转换回 SQL 执行
        // 使用自定义的 UnicodeH2SqlDialect，它正确处理 Unicode 字符（如中文）
        UnicodeH2SqlDialect unicodeDialect = UnicodeH2SqlDialect.DEFAULT;
        
        // 使用自定义的 OntologyRelToSqlConverter 将 RelNode 转换为 SQL
        // 这个转换器会自动处理表名和列名的映射（基于映射关系）
        OntologyRelToSqlConverter converter = 
            new OntologyRelToSqlConverter(unicodeDialect, loader, instanceStorage, mappingService);
        
        // 调试：打印查询信息
        System.out.println("\n" + "=".repeat(80));
        System.out.println("=== Converting RelNode to SQL ===");
        System.out.println("Query Object: " + originalQuery.getFrom());
        System.out.println("Query has Links: " + (originalQuery.getLinks() != null && !originalQuery.getLinks().isEmpty()));
        System.out.println("Query has GroupBy: " + (originalQuery.getGroupBy() != null && !originalQuery.getGroupBy().isEmpty()));
        System.out.println("Query has Metrics: " + (originalQuery.getMetrics() != null && !originalQuery.getMetrics().isEmpty()));
        System.out.println("Query has Filter: " + (originalQuery.getFilter() != null && !originalQuery.getFilter().isEmpty()));
        System.out.println("Query has Where: " + (originalQuery.getWhere() != null && !originalQuery.getWhere().isEmpty()));
        if (originalQuery.getFilter() != null && !originalQuery.getFilter().isEmpty()) {
            System.out.println("Filter expressions: " + originalQuery.getFilter());
        }
        
        org.apache.calcite.rel.rel2sql.RelToSqlConverter.Result converterResult = converter.visitRoot(relNode);
        
        // 获取映射后的 SQL（传入原始查询以支持 JOIN）
        String sql = converter.getMappedSql(converterResult, originalQuery);

        // 兼容部分方言生成的字符串字面量前缀（如 _UTF-8'xxx'），在 MySQL 会报语法错
        // 简单移除非法的 charset introducer，保持普通字符串字面量
        sql = sql.replace("_UTF-8'", "'").replace("_UTF8'", "'");
        
        // 调试：打印生成的 SQL
        System.out.println("=== Generated SQL ===");
        System.out.println(sql);
        System.out.println("=".repeat(80) + "\n");
        
        // 获取 ObjectType 和 DataSourceMapping（用于结果映射）
        com.mypalantir.meta.ObjectType objectType;
        try {
            objectType = loader.getObjectType(originalQuery.getFrom());
        } catch (com.mypalantir.meta.Loader.NotFoundException e) {
            throw new IllegalArgumentException("Object type '" + originalQuery.getFrom() + "' not found");
        }
        
        // 从映射关系获取 DataSourceMapping
        com.mypalantir.meta.DataSourceMapping dataSourceMapping = getDataSourceMappingFromMapping(objectType);
        
        // 执行 SQL（这是 Calcite 的标准执行方式）
        // 注意：需要将结果中的数据库列名映射回属性名
        QueryResult result = executeSql(sql, originalQuery, objectType, dataSourceMapping);
        result.setSql(sql);
        return result;
    }
    
    /**
     * 从映射关系获取 DataSourceMapping
     */
    private com.mypalantir.meta.DataSourceMapping getDataSourceMappingFromMapping(com.mypalantir.meta.ObjectType objectType) {
        try {
            List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectType.getName());
            if (mappings == null || mappings.isEmpty()) {
                // 如果没有映射关系，返回 schema 中定义的 data_source（向后兼容）
                return objectType.getDataSource();
            }
            
            // 使用第一个映射关系
            Map<String, Object> mappingData = mappings.get(0);
            String tableId = (String) mappingData.get("table_id");
            if (tableId == null) {
                return objectType.getDataSource();
            }
            
            // 获取表信息
            Map<String, Object> table = instanceStorage.getInstance("table", tableId);
            String tableName = (String) table.get("name");
            String primaryKeyColumn = (String) mappingData.get("primary_key_column");
            
            @SuppressWarnings("unchecked")
            Map<String, String> columnPropertyMappings = (Map<String, String>) mappingData.get("column_property_mappings");
            
            // column_property_mappings 的结构是 {列名: 属性名}，需要反转为 {属性名: 列名}
            Map<String, String> fieldMapping = new HashMap<>();
            if (columnPropertyMappings != null) {
                for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                    String columnName = entry.getKey();
                    String propertyName = entry.getValue();
                    fieldMapping.put(propertyName, columnName);
                }
            }
            
            com.mypalantir.meta.DataSourceMapping dataSourceMapping = new com.mypalantir.meta.DataSourceMapping();
            dataSourceMapping.setTable(tableName);
            dataSourceMapping.setIdColumn(primaryKeyColumn != null ? primaryKeyColumn : "id");
            dataSourceMapping.setFieldMapping(fieldMapping);
            
            return dataSourceMapping;
        } catch (Exception e) {
            System.err.println("Failed to get DataSourceMapping from mapping for " + objectType.getName() + ": " + e.getMessage());
            // 回退到 schema 中定义的 data_source
            return objectType.getDataSource();
        }
    }
    
    /**
     * 替换 SQL 中的表名和列名
     * 将 Ontology 属性名替换为数据库列名，将 ObjectType 名称替换为数据库表名
     */
    private String replaceTableAndColumnNames(String sql, OntologyQuery query, 
                                               com.mypalantir.meta.ObjectType objectType,
                                               com.mypalantir.meta.DataSourceMapping dataSourceMapping) {
        // 替换表名：将 ObjectType 名称替换为数据库表名（大写）
        String objectTypeName = query.getFrom();
        String dbTableName = dataSourceMapping.getTable().toUpperCase();
        // 使用正则表达式替换表名，确保只替换表名而不是列名
        sql = sql.replaceAll("(?i)\"" + java.util.regex.Pattern.quote(objectTypeName) + "\"", 
                            "\"" + dbTableName + "\"");
        sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(objectTypeName) + "\\b", 
                            "\"" + dbTableName + "\"");
        
        // 替换列名：将属性名替换为数据库列名
        if (objectType.getProperties() != null) {
            for (com.mypalantir.meta.Property prop : objectType.getProperties()) {
                String propertyName = prop.getName();
                String columnName = dataSourceMapping.getColumnName(propertyName);
                if (columnName != null) {
                    String dbColumnName = columnName.toUpperCase();
                    // 替换带引号的列名
                    sql = sql.replaceAll("\"" + java.util.regex.Pattern.quote(propertyName) + "\"", 
                                        "\"" + dbColumnName + "\"");
                    // 替换不带引号的列名（在 SELECT、WHERE、ORDER BY 等子句中）
                    // 使用单词边界确保只替换列名
                    sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(propertyName) + "\\b", 
                                        "\"" + dbColumnName + "\"");
                }
            }
        }
        
        // 替换 ID 列名
        String idColumnName = dataSourceMapping.getIdColumn().toUpperCase();
        sql = sql.replaceAll("(?i)\"id\"", "\"" + idColumnName + "\"");
        sql = sql.replaceAll("(?i)\\bid\\b", "\"" + idColumnName + "\"");
        
        return sql;
    }

    /**
     * 将 OntologyQuery 转换为 SQL
     */
    private String buildSql(OntologyQuery query) throws Exception {
        // 获取 ObjectType 以进行属性名到列名的映射
        com.mypalantir.meta.ObjectType objectType;
        try {
            objectType = loader.getObjectType(query.getFrom());
        } catch (com.mypalantir.meta.Loader.NotFoundException e) {
            throw new IllegalArgumentException("Object type '" + query.getFrom() + "' not found");
        }
        
        com.mypalantir.meta.DataSourceMapping dataSourceMapping = objectType.getDataSource();
        
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // SELECT 子句 - 需要映射属性名到列名
        if (query.getSelect() != null && !query.getSelect().isEmpty()) {
            List<String> selectColumns = new ArrayList<>();
            for (String propertyName : query.getSelect()) {
                String columnName = mapPropertyToColumn(propertyName, dataSourceMapping);
                selectColumns.add(quoteIdentifier(columnName));
            }
            sql.append(String.join(", ", selectColumns));
        } else {
            sql.append("*");
        }
        
        // FROM 子句 - 使用表名（从 dataSourceMapping 获取）
        String tableName = dataSourceMapping != null && dataSourceMapping.isConfigured() 
            ? dataSourceMapping.getTable().toUpperCase() 
            : query.getFrom();
        sql.append(" FROM ").append(quoteIdentifier(tableName));
        
        // WHERE 子句
        if (query.getWhere() != null && !query.getWhere().isEmpty()) {
            sql.append(" WHERE ");
            List<String> conditions = new ArrayList<>();
            for (Map.Entry<String, Object> entry : query.getWhere().entrySet()) {
                String propertyName = entry.getKey();
                String columnName = mapPropertyToColumn(propertyName, dataSourceMapping);
                conditions.add(quoteIdentifier(columnName) + " = " + quoteValue(entry.getValue()));
            }
            sql.append(String.join(" AND ", conditions));
        }
        
        // ORDER BY 子句
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            List<String> orderByClauses = new ArrayList<>();
            for (OntologyQuery.OrderBy orderBy : query.getOrderBy()) {
                String propertyName = orderBy.getField();
                String columnName = mapPropertyToColumn(propertyName, dataSourceMapping);
                orderByClauses.add(quoteIdentifier(columnName) + " " + 
                    (orderBy.getDirection() != null ? orderBy.getDirection() : "ASC"));
            }
            sql.append(String.join(", ", orderByClauses));
        }
        
        // LIMIT 子句
        if (query.getLimit() != null && query.getLimit() > 0) {
            sql.append(" LIMIT ").append(query.getLimit());
        }
        
        // OFFSET 子句
        if (query.getOffset() != null && query.getOffset() > 0) {
            sql.append(" OFFSET ").append(query.getOffset());
        }
        
        return sql.toString();
    }
    
    /**
     * 将属性名映射为数据库列名
     * 如果对象类型有数据源映射，使用映射；否则直接使用属性名
     */
    private String mapPropertyToColumn(String propertyName, com.mypalantir.meta.DataSourceMapping dataSourceMapping) {
        if (dataSourceMapping != null && dataSourceMapping.isConfigured()) {
            String columnName = dataSourceMapping.getColumnName(propertyName);
            if (columnName != null) {
                // H2 默认创建大写列名，所以转换为大写
                return columnName.toUpperCase();
            }
        }
        // 如果没有映射，直接使用属性名（转换为大写以匹配 H2）
        return propertyName.toUpperCase();
    }

    /**
     * 执行 SQL 查询（保留作为备用方法）
     * @deprecated 使用 executeRelNode 代替
     */
    @Deprecated
    private QueryResult executeSql(String sql) throws SQLException {
        return executeSql(sql, null, null);
    }
    
    /**
     * 执行 SQL 查询，并将数据库列名映射回属性名
     */
    private QueryResult executeSql(String sql, com.mypalantir.meta.ObjectType objectType, 
                                    com.mypalantir.meta.DataSourceMapping dataSourceMapping) throws SQLException {
        return executeSql(sql, null, objectType, dataSourceMapping);
    }
    
    /**
     * 执行 SQL 查询，并将数据库列名映射回属性名（支持 JOIN 查询）
     */
    private QueryResult executeSql(String sql, OntologyQuery query, 
                                    com.mypalantir.meta.ObjectType objectType, 
                                    com.mypalantir.meta.DataSourceMapping dataSourceMapping) throws SQLException {
        // 根据 mapping 获取实际的数据库连接
        Connection dbConnection = null;
        try {
            String databaseId = dataSourceMapping != null ? dataSourceMapping.getConnectionId() : null;
            if (databaseId == null || databaseId.isEmpty() || databaseId.equals("default")) {
                // 使用默认连接
                dbConnection = databaseMetadataService.getConnectionForDatabase(null);
            } else {
                // 使用指定数据库的连接
                dbConnection = databaseMetadataService.getConnectionForDatabase(databaseId);
            }
            
            System.out.println("[executeSql] Using database connection for databaseId: " + databaseId);
            System.out.println("[executeSql] Executing SQL: " + sql);
            
            // 使用实际的数据库连接执行 SQL
            try (Statement stmt = dbConnection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                List<Map<String, Object>> rows = new ArrayList<>();
                
                // 获取列信息
                int columnCount = rs.getMetaData().getColumnCount();
                List<String> propertyNames = new ArrayList<>();  // 使用属性名而不是列名
                
                // 构建列名到属性名的映射（支持多表）
                java.util.Map<String, String> columnToPropertyMap = buildColumnToPropertyMap(
                    query, objectType, dataSourceMapping);
                
                // 获取列名并映射为属性名
                for (int i = 1; i <= columnCount; i++) {
                    String dbColumnName = rs.getMetaData().getColumnName(i);
                    // 如果结果中有别名（如 "车牌号"），直接使用别名
                    String alias = rs.getMetaData().getColumnLabel(i);
                    if (alias != null && !alias.equals(dbColumnName)) {
                        // 使用别名作为属性名
                        propertyNames.add(alias);
                    } else {
                        // 否则，尝试从映射中获取属性名
                        String propertyName = columnToPropertyMap.get(dbColumnName.toUpperCase());
                        if (propertyName != null) {
                            propertyNames.add(propertyName);
                        } else {
                            // 如果列名包含表名（如 "通行介质"."介质编号"），尝试提取属性名
                            // Calcite 生成的 SQL 可能使用 "表名"."列名" 格式
                            if (dbColumnName.contains(".")) {
                                String[] parts = dbColumnName.split("\\.", 2);
                                if (parts.length == 2) {
                                    // 去掉引号
                                    String possiblePropertyName = parts[1].replaceAll("\"", "");
                                    // 检查是否是属性名（在映射中）
                                    if (columnToPropertyMap.containsValue(possiblePropertyName)) {
                                        propertyNames.add(possiblePropertyName);
                                    } else {
                                        propertyNames.add(possiblePropertyName);
                                    }
                                } else {
                                    propertyNames.add(dbColumnName);
                                }
                            } else {
                                // 如果找不到映射，使用原始列名
                                propertyNames.add(dbColumnName);
                            }
                        }
                    }
                }
                
                // 读取数据
                while (rs.next()) {
                    Map<String, Object> row = new java.util.HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String propertyName = propertyNames.get(i - 1);
                        Object value = rs.getObject(i);
                        
                    // 类型转换：Calcite 期望的类型
                    // 如果值是 BigDecimal，根据列类型或列名判断是否需要转换为 Double
                    if (value instanceof java.math.BigDecimal) {
                        int sqlType = rs.getMetaData().getColumnType(i);
                        String columnLabel = rs.getMetaData().getColumnLabel(i);
                        // 检查列类型是否是 DOUBLE 或 FLOAT，或者列名包含聚合函数（如 SUM, AVG）
                        if (sqlType == java.sql.Types.DOUBLE || sqlType == java.sql.Types.FLOAT || 
                            sqlType == java.sql.Types.REAL || sqlType == java.sql.Types.NUMERIC ||
                            (columnLabel != null && (columnLabel.contains("SUM") || columnLabel.contains("AVG") || 
                             columnLabel.equals("$f1") || columnLabel.startsWith("total_")))) {
                            // 转换为 Double
                            value = ((java.math.BigDecimal) value).doubleValue();
                        }
                    }
                    
                    row.put(propertyName, value);
                    }
                    rows.add(row);
                }
                
                return new QueryResult(rows, propertyNames);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to get database connection: " + e.getMessage(), e);
        }
    }

    /**
     * 构建列名到属性名的映射（支持多表 JOIN）
     */
    private java.util.Map<String, String> buildColumnToPropertyMap(
            OntologyQuery query,
            com.mypalantir.meta.ObjectType objectType,
            com.mypalantir.meta.DataSourceMapping dataSourceMapping) {
        
        java.util.Map<String, String> columnToPropertyMap = new java.util.HashMap<>();
        
        // 主表映射
        if (dataSourceMapping != null && dataSourceMapping.isConfigured() && objectType != null) {
            // 映射 ID 列
            String idColumn = dataSourceMapping.getIdColumn().toUpperCase();
            columnToPropertyMap.put(idColumn, "id");
            columnToPropertyMap.put("ID", "id");
            
            // 映射属性列
            if (objectType.getProperties() != null) {
                for (com.mypalantir.meta.Property prop : objectType.getProperties()) {
                    String columnName = dataSourceMapping.getColumnName(prop.getName());
                    if (columnName != null) {
                        String upperColumnName = columnName.toUpperCase();
                        columnToPropertyMap.put(upperColumnName, prop.getName());
                    }
                }
            }
        }
        
        // 关联表映射
        if (query != null && query.getLinks() != null && !query.getLinks().isEmpty()) {
            for (OntologyQuery.LinkQuery linkQuery : query.getLinks()) {
                try {
                    com.mypalantir.meta.LinkType linkType = loader.getLinkType(linkQuery.getName());
                    if (linkType.getDataSource() != null && linkType.getDataSource().isConfigured()) {
                        DataSourceMapping linkMapping = linkType.getDataSource();
                        
                        // 映射 link type 属性的列名
                        if (linkType.getProperties() != null) {
                            for (com.mypalantir.meta.Property linkProp : linkType.getProperties()) {
                                String columnName = linkMapping.getColumnName(linkProp.getName());
                                if (columnName != null) {
                                    String upperColumnName = columnName.toUpperCase();
                                    columnToPropertyMap.put(upperColumnName, linkProp.getName());
                                }
                            }
                        }
                        
                        // 映射目标表的列名
                        com.mypalantir.meta.ObjectType targetObjectType = loader.getObjectType(linkType.getTargetType());
                        if (targetObjectType.getDataSource() != null && targetObjectType.getDataSource().isConfigured()) {
                            DataSourceMapping targetMapping = targetObjectType.getDataSource();
                            
                            // 映射目标表的 ID 列（如果需要）
                            String targetIdColumn = targetMapping.getIdColumn().toUpperCase();
                            // 注意：目标表的 id 可能与其他表冲突，这里先不映射
                            
                            // 映射目标表的属性列
                            if (targetObjectType.getProperties() != null) {
                                for (com.mypalantir.meta.Property targetProp : targetObjectType.getProperties()) {
                                    String columnName = targetMapping.getColumnName(targetProp.getName());
                                    if (columnName != null) {
                                        String upperColumnName = columnName.toUpperCase();
                                        // 如果列名冲突，使用带前缀的名称
                                        if (columnToPropertyMap.containsKey(upperColumnName)) {
                                            // 列名冲突，跳过或使用带前缀的名称
                                            continue;
                                        }
                                        columnToPropertyMap.put(upperColumnName, targetProp.getName());
                                    }
                                }
                            }
                        }
                    }
                } catch (Loader.NotFoundException ex) {
                    // 忽略无效的 link type
                    continue;
                }
            }
        }
        
        return columnToPropertyMap;
    }

    /**
     * 引用标识符
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    /**
     * 引用值
     */
    private String quoteValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String) {
            return "'" + value.toString().replace("'", "''") + "'";
        }
        return value.toString();
    }

    /**
     * 关闭连接
     */
    public void close() throws SQLException {
        if (calciteConnection != null) {
            calciteConnection.close();
        }
        if (relNodeBuilder != null) {
            relNodeBuilder.close();
        }
    }

    /**
     * 查询结果
     */
    public static class QueryResult {
        private final List<Map<String, Object>> rows;
        private final List<String> columns;
        private String sql;

        public QueryResult(List<Map<String, Object>> rows, List<String> columns) {
            this.rows = rows;
            this.columns = columns;
        }

        public QueryResult(List<Map<String, Object>> rows, List<String> columns, String sql) {
            this.rows = rows;
            this.columns = columns;
            this.sql = sql;
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public List<String> getColumns() {
            return columns;
        }

        public int getRowCount() {
            return rows.size();
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }
    }
}

