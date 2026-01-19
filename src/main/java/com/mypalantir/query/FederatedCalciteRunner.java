package com.mypalantir.query;

import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.service.DatabaseMetadataService;
import com.mypalantir.service.MappingService;
import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.ViewTable;
import org.apache.calcite.tools.Frameworks;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 联邦查询执行器
 * 基于 Calcite JdbcSchema 实现跨数据源查询
 */
public class FederatedCalciteRunner {

    private final Loader loader;
    private final IInstanceStorage instanceStorage;
    private final MappingService mappingService;
    private final DatabaseMetadataService databaseMetadataService;
    private final RelNodeBuilder relNodeBuilder;

    public FederatedCalciteRunner(Loader loader, IInstanceStorage instanceStorage,
                                  MappingService mappingService, DatabaseMetadataService databaseMetadataService) {
        this.loader = loader;
        this.instanceStorage = instanceStorage;
        this.mappingService = mappingService;
        this.databaseMetadataService = databaseMetadataService;
        this.relNodeBuilder = new RelNodeBuilder(loader, instanceStorage, mappingService, databaseMetadataService);
    }

    public QueryExecutor.QueryResult execute(OntologyQuery query) throws Exception {
        // 1. 分析查询涉及的所有 ObjectType，获取它们对应的 DatabaseId
        Set<String> databaseIds = new HashSet<>();
        Map<String, DataSourceMapping> objectTypeMappings = new HashMap<>();

        // 分析 FROM
        collectDatabaseIds(query.getFrom(), databaseIds, objectTypeMappings);

        // 分析 LINKS
        if (query.getLinks() != null) {
            for (OntologyQuery.LinkQuery link : query.getLinks()) {
                // Link 的目标对象
                String targetType = getTargetTypeFromLink(link.getName());
                if (targetType != null) {
                    collectDatabaseIds(targetType, databaseIds, objectTypeMappings);
                }
                // Link 本身如果是关系表，也需要处理（暂略，假设外键模式或已包含）
            }
        }

        // 2. 初始化 Calcite 连接和 RootSchema
        Connection connection = DriverManager.getConnection("jdbc:calcite:");
        CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
        SchemaPlus rootSchema = calciteConnection.getRootSchema();

        // 3. 挂载 JdbcSchema (DB_<id>)
        for (String dbId : databaseIds) {
            DataSource dataSource = databaseMetadataService.getDataSourceForDatabase(dbId);
            // 使用 Calcite 的 JdbcSchema
            // 注意：这里假设数据库方言会自动识别，或者需要手动指定 dialect
            org.apache.calcite.schema.Schema dbSchema = JdbcSchema.create(rootSchema, "DB_" + dbId, dataSource, null, null);
            rootSchema.add("DB_" + dbId, dbSchema);
        }

        // 4. 创建 Views (ObjectTypeName -> SELECT ... FROM DB_<id>.<table>)
        for (Map.Entry<String, DataSourceMapping> entry : objectTypeMappings.entrySet()) {
            String objectTypeName = entry.getKey();
            DataSourceMapping mapping = entry.getValue();
            String dbId = mapping.getConnectionId();
            
            // 构建 View SQL
            String viewSql = buildViewSql("DB_" + dbId, mapping, objectTypeName);
            
            // 添加 View 到 rootSchema
            // ViewTable.viewMacro 需要 list of schema path, view sql, schema, boolean modifiable
            rootSchema.add(objectTypeName, 
                ViewTable.viewMacro(rootSchema, viewSql, Collections.emptyList(), Collections.emptyList(), false));
        }

        // 5. 生成查询 SQL
        // 这里我们要利用现有的 RelNodeBuilder + Converter 生成 SQL
        // 但生成的 SQL 是基于 ObjectType 名称的，正好对应我们的 View
        
        // 重新初始化 relNodeBuilder 确保它准备好
        // 注意：这里我们用 RelNodeBuilder 只是为了生成 SQL，它内部创建的 Schema 不会被执行
        // 这是一个“影子”构建过程
        org.apache.calcite.rel.RelNode relNode = relNodeBuilder.buildRelNode(query);
        
        // 转换回 SQL
        // 使用标准 RelToSqlConverter，它会保留 RelNode 中的表名（ObjectType名）和字段名（属性名）
        // 这正是我们需要的，因为我们已经创建了对应名称的 View
        org.apache.calcite.rel.rel2sql.RelToSqlConverter converter = 
            new org.apache.calcite.rel.rel2sql.RelToSqlConverter(UnicodeH2SqlDialect.DEFAULT);
        
        org.apache.calcite.rel.rel2sql.RelToSqlConverter.Result converterResult = converter.visitRoot(relNode);
        String sql = converterResult.asStatement().toSqlString(UnicodeH2SqlDialect.DEFAULT).getSql();
        
        // 清理 SQL：移除可能存在的 _UTF-8 前缀等，虽然 Calcite 可能支持，但为了保险
        sql = sql.replace("_UTF-8'", "'").replace("_UTF8'", "'");

        System.out.println("=== Federated SQL Execution ===");
        System.out.println("Generated SQL: " + sql);

        // 6. 执行 SQL
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            int columnCount = rs.getMetaData().getColumnCount();
            
            for (int i = 1; i <= columnCount; i++) {
                columns.add(rs.getMetaData().getColumnLabel(i));
            }
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (String col : columns) {
                    row.put(col, rs.getObject(col));
                }
                rows.add(row);
            }
            
            return new QueryExecutor.QueryResult(rows, columns, sql);
        }
    }

    private void collectDatabaseIds(String objectTypeName, Set<String> databaseIds, 
                                    Map<String, DataSourceMapping> objectTypeMappings) throws Exception {
        ObjectType objectType = loader.getObjectType(objectTypeName);
        List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectTypeName);
        
        if (mappings != null && !mappings.isEmpty()) {
            // 解析 Mapping 逻辑 (复用 QueryExecutor 中的逻辑)
            // 这里为了简化，直接手动构造或提取
            // 实际上 MappingService 返回的是 Map，我们需要转回 DataSourceMapping 对象
            // 或者我们可以修改 MappingService 让它返回 DataSourceMapping，但现在先手动解析
            
            Map<String, Object> mappingData = mappings.get(0);
            String tableId = (String) mappingData.get("table_id");
            Map<String, Object> table = instanceStorage.getInstance("table", tableId);
            String databaseId = (String) table.get("database_id");
            String tableName = (String) table.get("name");
            
            DataSourceMapping mapping = new DataSourceMapping();
            mapping.setConnectionId(databaseId);
            mapping.setTable(tableName);
            mapping.setIdColumn((String) mappingData.get("primary_key_column"));
            
            @SuppressWarnings("unchecked")
            Map<String, String> colPropMap = (Map<String, String>) mappingData.get("column_property_mappings");
            Map<String, String> fieldMapping = new HashMap<>();
            if (colPropMap != null) {
                for (Map.Entry<String, String> entry : colPropMap.entrySet()) {
                    fieldMapping.put(entry.getValue(), entry.getKey());
                }
            }
            mapping.setFieldMapping(fieldMapping);
            // mapping.setObjectType(objectType); // 设置关联的 ObjectType 以便获取属性列表
            
            databaseIds.add(databaseId);
            objectTypeMappings.put(objectTypeName, mapping);
        }
    }

    private String getTargetTypeFromLink(String linkName) {
        try {
            return loader.getLinkType(linkName).getTargetType();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildViewSql(String dbSchemaName, DataSourceMapping mapping, String objectTypeName) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> selectParts = new ArrayList<>();
        
        // 1. Properties (只添加属性映射的列)
        try {
            ObjectType objectType = loader.getObjectType(objectTypeName);
            if (objectType != null && objectType.getProperties() != null) {
                for (Property prop : objectType.getProperties()) {
                    String colName = mapping.getColumnName(prop.getName());
                    if (colName != null) {
                        selectParts.add(String.format("\"%s\" AS \"%s\"", colName, prop.getName()));
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        
        // 如果没有属性（不应该发生），至少查询 *
        if (selectParts.isEmpty()) {
            selectParts.add("*");
        }
        
        sql.append(String.join(", ", selectParts));
        sql.append(" FROM \"").append(dbSchemaName).append("\".\"").append(mapping.getTable()).append("\"");
        
        return sql.toString();
    }
}
