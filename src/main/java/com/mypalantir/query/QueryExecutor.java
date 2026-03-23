package com.mypalantir.query;

import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.Loader;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.service.MappingService;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(QueryExecutor.class);
    private final Loader loader;
    private final IInstanceStorage instanceStorage;
    private final MappingService mappingService;
    private final com.mypalantir.service.DatabaseMetadataService databaseMetadataService;
    private final RelNodeBuilder relNodeBuilder;
    private SchemaPlus rootSchema;
    private Connection calciteConnection;

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
        relNodeBuilder.initialize();
        rootSchema = relNodeBuilder.rootSchema;

        Connection connection = DriverManager.getConnection("jdbc:calcite:");
        try {
            CalciteConnection calciteConn = connection.unwrap(CalciteConnection.class);
            SchemaPlus calciteRootSchema = calciteConn.getRootSchema();

            if (rootSchema != null) {
                for (String tableName : rootSchema.getTableNames()) {
                    calciteRootSchema.add(tableName, rootSchema.getTable(tableName));
                }
            }

            this.calciteConnection = calciteConn;
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    /**
     * 执行查询
     */
    public QueryResult execute(OntologyQuery query) throws Exception {
        if (rootSchema == null) {
            initialize();
        }

        // 直接构建 RelNode
        logger.debug("Building RelNode for query object: {}", query.getFrom());
        org.apache.calcite.rel.RelNode relNode = relNodeBuilder.buildRelNode(query);

        // 打印 RelNode 信息（递归显示整个树）
        if (logger.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n").append("=".repeat(80)).append("\n");
            sb.append("=== RelNode Tree Structure ===\n");
            printRelNodeTree(relNode, 0, sb);
            sb.append("=".repeat(80));
            logger.debug(sb.toString());
        }
        
        // 执行 RelNode（传入原始查询以便回退）
        return executeRelNode(relNode, query);
    }
    
    /**
     * 递归打印 RelNode 树结构
     */
    private void printRelNodeTree(org.apache.calcite.rel.RelNode relNode, int indent, StringBuilder sb) {
        String indentStr = "  ".repeat(indent);
        sb.append(indentStr).append("└─ ").append(relNode.getClass().getSimpleName()).append("\n");
        sb.append(indentStr).append("   RowType: ").append(relNode.getRowType()).append("\n");
        sb.append(indentStr).append("   Fields:\n");
        for (int i = 0; i < relNode.getRowType().getFieldCount(); i++) {
            org.apache.calcite.rel.type.RelDataTypeField field = relNode.getRowType().getFieldList().get(i);
            sb.append(indentStr).append("     [").append(i).append("] ").append(field.getName())
              .append(" : ").append(field.getType()).append("\n");
        }
        if (relNode.getInputs() != null && !relNode.getInputs().isEmpty()) {
            sb.append(indentStr).append("   Inputs:\n");
            for (int i = 0; i < relNode.getInputs().size(); i++) {
                printRelNodeTree(relNode.getInputs().get(i), indent + 1, sb);
            }
        }
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
        
        // 这个转换器会自动处理表名和列名的映射（基于映射关系）
        OntologyRelToSqlConverter converter =
            new OntologyRelToSqlConverter(unicodeDialect, loader, instanceStorage, mappingService);

        logger.debug("Converting RelNode to SQL: object={}, hasLinks={}, hasGroupBy={}, hasMetrics={}",
            originalQuery.getFrom(),
            originalQuery.getLinks() != null && !originalQuery.getLinks().isEmpty(),
            originalQuery.getGroupBy() != null && !originalQuery.getGroupBy().isEmpty(),
            originalQuery.getMetrics() != null && !originalQuery.getMetrics().isEmpty());

        org.apache.calcite.rel.rel2sql.RelToSqlConverter.Result converterResult = converter.visitRoot(relNode);

        // 获取映射后的 SQL（传入原始查询以支持 JOIN）
        String sql = converter.getMappedSql(converterResult, originalQuery);

        // 兼容部分方言生成的字符串字面量前缀（如 _UTF-8'xxx'），在 MySQL 会报语法错
        sql = sql.replace("_UTF-8'", "'").replace("_UTF8'", "'");

        logger.debug("Generated SQL: {}", sql);
        
        // 获取 ObjectType 和 DataSourceMapping（用于结果映射）
        com.mypalantir.meta.ObjectType objectType;
        try {
            objectType = loader.getObjectType(originalQuery.getFrom());
        } catch (com.mypalantir.meta.Loader.NotFoundException e) {
            throw new IllegalArgumentException("Object type '" + originalQuery.getFrom() + "' not found");
        }
        
        // 根据 dataSourceType 选择：sync=同步表（默认库），raw=映射的原始表
        com.mypalantir.meta.DataSourceMapping dataSourceMapping;
        if ("sync".equalsIgnoreCase(originalQuery.getDataSourceType())) {
            dataSourceMapping = getSyncTableDataSourceMapping(objectType);
        } else {
            dataSourceMapping = getDataSourceMappingFromMapping(objectType);
        }
        
        // 执行 SQL（这是 Calcite 的标准执行方式）
        // 注意：需要将结果中的数据库列名映射回属性名
        QueryResult result = executeSql(sql, originalQuery, objectType, dataSourceMapping);
        result.setSql(sql);
        return result;
    }
    
    /**
     * 从映射关系获取 DataSourceMapping
     * 强制要求必须配置 Mapping，不再回退到 ObjectType 内部的 data_source
     * 数据源获取链路：Mapping → table_id → Table → database_id → Database → 连接信息
     */
    private com.mypalantir.meta.DataSourceMapping getDataSourceMappingFromMapping(com.mypalantir.meta.ObjectType objectType) {
        try {
            List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectType.getName());
            if (mappings == null || mappings.isEmpty()) {
                // 强制要求配置 Mapping，不再回退到 schema 中的 data_source
                throw new IllegalArgumentException(
                    "Object type '" + objectType.getName() + "' does not have a mapping configured. " +
                    "Please configure a mapping in the Data Mapping page."
                );
            }
            
            // 使用第一个映射关系
            Map<String, Object> mappingData = mappings.get(0);
            String tableId = (String) mappingData.get("table_id");
            if (tableId == null) {
                throw new IllegalArgumentException(
                    "Mapping for object type '" + objectType.getName() + "' does not have table_id configured."
                );
            }
            
            // 获取表信息
            Map<String, Object> table = instanceStorage.getInstance("table", tableId);
            if (table == null) {
                throw new IllegalArgumentException(
                    "Table with id '" + tableId + "' not found for object type '" + objectType.getName() + "'."
                );
            }
            
            String tableName = (String) table.get("name");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException(
                    "Table with id '" + tableId + "' does not have a name."
                );
            }
            
            // 关键：从 Table 对象获取 database_id（数据源连接ID）
            String databaseId = (String) table.get("database_id");
            if (databaseId == null || databaseId.isEmpty()) {
                throw new IllegalArgumentException(
                    "Table '" + tableName + "' (id: '" + tableId + "') does not have database_id configured. " +
                    "Please ensure the table is associated with a database."
                );
            }
            
            // 支持新格式（数组）和旧格式（单个字符串）
            @SuppressWarnings("unchecked")
            List<String> primaryKeyColumns = (List<String>) mappingData.get("primary_key_columns");
            String primaryKeyColumn = (String) mappingData.get("primary_key_column");
            
            // 如果新格式存在，使用第一个主键列；否则使用旧格式
            if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
                primaryKeyColumn = primaryKeyColumns.get(0);
            }
            
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
            
            // 构造 DataSourceMapping，包含完整的数据源连接信息
            com.mypalantir.meta.DataSourceMapping dataSourceMapping = new com.mypalantir.meta.DataSourceMapping();
            dataSourceMapping.setConnectionId(databaseId);  // 设置数据源连接ID
            dataSourceMapping.setTable(tableName);
            dataSourceMapping.setIdColumn(primaryKeyColumn != null ? primaryKeyColumn : "id");
            dataSourceMapping.setPrimaryKeyColumns(primaryKeyColumns);
            dataSourceMapping.setFieldMapping(fieldMapping);
            
            System.out.println("[QueryExecutor.getDataSourceMappingFromMapping] Successfully loaded mapping for '" + 
                objectType.getName() + "':");
            System.out.println("  → table: " + tableName + " (table_id: " + tableId + ")");
            System.out.println("  → database_id (connectionId): " + databaseId);
            System.out.println("  → primaryKey: " + dataSourceMapping.getIdColumn());
            
            return dataSourceMapping;
        } catch (IllegalArgumentException e) {
            // 直接抛出参数异常，不做任何回退
            throw e;
        } catch (Exception e) {
            // 其他异常也转换为明确的错误信息
            throw new IllegalArgumentException(
                "Failed to get DataSourceMapping from mapping for object type '" + objectType.getName() + "': " + 
                e.getMessage() + ". Please ensure the mapping is correctly configured.",
                e
            );
        }
    }
    
    /**
     * 获取同步表 DataSourceMapping
     * 同步表：表名 = 对象类型名小写，在默认数据库中，列名与本体属性名一致
     */
    private com.mypalantir.meta.DataSourceMapping getSyncTableDataSourceMapping(com.mypalantir.meta.ObjectType objectType) {
        String tableName = objectType.getName().toLowerCase();
        com.mypalantir.meta.DataSourceMapping dataSourceMapping = new com.mypalantir.meta.DataSourceMapping();
        dataSourceMapping.setConnectionId("default");  // 系统默认数据源（同步表）
        dataSourceMapping.setTable(tableName);
        dataSourceMapping.setIdColumn("id");
        Map<String, String> fieldMapping = new HashMap<>();
        if (objectType.getProperties() != null) {
            for (com.mypalantir.meta.Property prop : objectType.getProperties()) {
                fieldMapping.put(prop.getName(), prop.getName());
            }
        }
        fieldMapping.put("id", "id");
        dataSourceMapping.setFieldMapping(fieldMapping);
        return dataSourceMapping;
    }
    
    /**
     * 执行 SQL 查询，并将数据库列名映射回属性名（支持 JOIN 查询）
     */
    private QueryResult executeSql(String sql, OntologyQuery query, 
                                    com.mypalantir.meta.ObjectType objectType, 
                                    com.mypalantir.meta.DataSourceMapping dataSourceMapping) throws SQLException {
        // 根据 mapping 获取实际的数据库连接（从 HikariCP 连接池获取，必须 close 归还池）
        String databaseId = dataSourceMapping != null ? dataSourceMapping.getConnectionId() : null;
        if (databaseId == null || databaseId.isEmpty() || databaseId.equals("default")) {
            databaseId = null;
        }
        
        Connection dbConnection;
        try {
            dbConnection = databaseMetadataService.getConnectionForDatabase(databaseId);
        } catch (IOException e) {
            throw new SQLException("Failed to get database connection: " + e.getMessage(), e);
        }
        
        System.out.println("[executeSql] Using database connection for databaseId: " + databaseId);
        System.out.println("[executeSql] Executing SQL: " + sql);
        
        try (Connection conn = dbConnection;
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(databaseMetadataService.getQueryTimeoutSeconds());
            try (ResultSet rs = stmt.executeQuery(sql)) {
                
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
                                        // 如果列名冲突，使用带表名前缀的名称
                                        if (columnToPropertyMap.containsKey(upperColumnName)) {
                                            columnToPropertyMap.put(
                                                linkType.getTargetType() + "." + upperColumnName,
                                                targetProp.getName());
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

