package com.mypalantir.query.schema;

import com.mypalantir.meta.DataSourceConfig;
import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.service.MappingService;
import com.mypalantir.service.DatabaseMetadataService;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Ontology Schema 转换为 Calcite Schema
 * 现在基于映射关系（mapping）创建表，而不是从 ObjectType.getDataSource()
 */
public class OntologySchemaFactory {
    private final Loader loader;
    private final IInstanceStorage instanceStorage;
    private final MappingService mappingService;
    private final DatabaseMetadataService databaseMetadataService;
    private final Map<String, Connection> dataSourceConnections = new HashMap<>();
    private final Map<String, Connection> databaseConnections = new HashMap<>(); // 基于 databaseId 的连接

    public OntologySchemaFactory(Loader loader, IInstanceStorage instanceStorage, 
                                 MappingService mappingService, DatabaseMetadataService databaseMetadataService) {
        this.loader = loader;
        this.instanceStorage = instanceStorage;
        this.mappingService = mappingService;
        this.databaseMetadataService = databaseMetadataService;
    }

    /**
     * 创建 Calcite Schema
     * @return SchemaPlus 包含所有 ObjectType 表的 Schema
     */
    public SchemaPlus createCalciteSchema() throws SQLException {
        // 创建 Calcite 连接
        Connection connection = DriverManager.getConnection("jdbc:calcite:");
        CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
        SchemaPlus rootSchema = calciteConnection.getRootSchema();
        
        // 注意：这个连接会被关闭，所以我们需要返回一个独立的 Schema
        // 实际上，我们应该返回 rootSchema，但表已经添加进去了

        // 为每个数据源创建连接（但不创建子 Schema，直接在 rootSchema 中添加表）
        Map<String, Connection> dataSourceConnections = new HashMap<>();
        if (loader.getSchema() != null && loader.getSchema().getDataSources() != null) {
            for (DataSourceConfig dsConfig : loader.getSchema().getDataSources()) {
                try {
                    Connection jdbcConnection = createJdbcConnection(dsConfig);
                    if (jdbcConnection != null) {
                        this.dataSourceConnections.put(dsConfig.getId(), jdbcConnection);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to create connection for data source " + dsConfig.getId() + ": " + e.getMessage());
                }
            }
        }

        // 为每个 ObjectType 创建 Table（基于映射关系）
        if (loader.getSchema() != null && loader.getSchema().getObjectTypes() != null) {
            for (ObjectType objectType : loader.getSchema().getObjectTypes()) {
                OntologyTable table = createTableFromMapping(objectType);
                if (table != null) {
                    // 直接将表添加到 rootSchema
                    rootSchema.add(objectType.getName(), table);
                }
            }
        }
        
        // 为每个有 data_source 的 LinkType 创建中间表（仅限关系表模式）
        if (loader.getSchema() != null && loader.getSchema().getLinkTypes() != null) {
            for (LinkType linkType : loader.getSchema().getLinkTypes()) {
                if (linkType.getDataSource() != null && linkType.getDataSource().isConfigured()) {
                    // 获取目标 ObjectType 以判断映射模式
                    ObjectType targetObjectType = null;
                    try {
                        targetObjectType = loader.getObjectType(linkType.getTargetType());
                    } catch (Loader.NotFoundException e) {
                        // 如果目标类型不存在，跳过
                        continue;
                    }
                    
                    // 外键模式：不需要创建中间表（目标表已存在）
                    if (linkType.getDataSource().isForeignKeyMode(targetObjectType)) {
                        continue;
                    }
                    
                    // 关系表模式：创建中间表
                    // 使用表名作为 Schema 中的名称（而不是 link type 名称）
                    // 这样在 JOIN 时可以通过表名访问
                    String tableName = linkType.getDataSource().getTable();
                    if (tableName != null && !tableName.isEmpty()) {
                        // 检查是否已经添加（避免重复）
                        if (rootSchema.getTable(tableName) == null) {
                            OntologyTable linkTable = createLinkTable(linkType);
                            if (linkTable != null) {
                                rootSchema.add(tableName, linkTable);
                            }
                        }
                    }
                }
            }
        }

        return rootSchema;
    }


    /**
     * 创建 JDBC 连接
     */
    private Connection createJdbcConnection(DataSourceConfig dsConfig) throws SQLException, ClassNotFoundException {
        String jdbcUrl = dsConfig.getJdbcUrl() != null && !dsConfig.getJdbcUrl().isEmpty()
            ? dsConfig.getJdbcUrl()
            : dsConfig.buildJdbcUrl();

        // 加载驱动
        loadDriver(dsConfig.getType());

        // 处理环境变量
        String username = resolveEnvironmentVariable(dsConfig.getUsername());
        String password = resolveEnvironmentVariable(dsConfig.getPassword() != null ? dsConfig.getPassword() : "");

        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * 加载 JDBC 驱动
     */
    private void loadDriver(String dbType) throws ClassNotFoundException {
        switch (dbType != null ? dbType.toLowerCase() : "") {
            case "postgresql":
            case "postgres":
                Class.forName("org.postgresql.Driver");
                break;
            case "mysql":
                Class.forName("com.mysql.cj.jdbc.Driver");
                break;
            case "h2":
                Class.forName("org.h2.Driver");
                break;
            case "oracle":
                Class.forName("oracle.jdbc.driver.OracleDriver");
                break;
            case "sqlserver":
            case "mssql":
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                break;
            default:
                throw new ClassNotFoundException("Unsupported database type: " + dbType);
        }
    }

    /**
     * 解析环境变量
     */
    private String resolveEnvironmentVariable(String value) {
        if (value == null) {
            return "";
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            String varName = value.substring(2, value.length() - 1);
            String envValue = System.getenv(varName);
            return envValue != null ? envValue : value;
        }
        return value;
    }

    /**
     * 为 ObjectType 创建 Table（基于映射关系）
     */
    private OntologyTable createTableFromMapping(ObjectType objectType) {
        try {
            // 从映射关系获取表和数据源信息
            List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectType.getName());
            if (mappings == null || mappings.isEmpty()) {
                // 如果没有映射关系，尝试使用 schema 中定义的 data_source（向后兼容）
                DataSourceMapping mapping = objectType.getDataSource();
                if (mapping != null && mapping.isConfigured()) {
                    Connection connection = dataSourceConnections.get(mapping.getConnectionId());
                    if (connection != null) {
                        return new JdbcOntologyTable(objectType, mapping, connection);
                    }
                }
                return null;
            }
            
            // 使用第一个映射关系（如果有多个，可以选择第一个）
            Map<String, Object> mappingData = mappings.get(0);
            String tableId = (String) mappingData.get("table_id");
            if (tableId == null) {
                return null;
            }
            
            // 获取表信息
            Map<String, Object> table = instanceStorage.getInstance("table", tableId);
            String tableName = (String) table.get("name");
            String databaseId = (String) table.get("database_id");
            
            if (tableName == null || databaseId == null) {
                return null;
            }
            
            // 获取数据库连接
            Connection connection = getDatabaseConnection(databaseId);
            if (connection == null) {
                return null;
            }
            
            // 构建 DataSourceMapping 对象
            @SuppressWarnings("unchecked")
            Map<String, String> columnPropertyMappings = (Map<String, String>) mappingData.get("column_property_mappings");
            String primaryKeyColumn = (String) mappingData.get("primary_key_column");
            
            // column_property_mappings 的结构是 {列名: 属性名}，需要反转为 {属性名: 列名}
            Map<String, String> fieldMapping = new HashMap<>();
            if (columnPropertyMappings != null) {
                for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                    String columnName = entry.getKey();
                    String propertyName = entry.getValue();
                    fieldMapping.put(propertyName, columnName);
                }
            }
            
            DataSourceMapping dataSourceMapping = new DataSourceMapping();
            dataSourceMapping.setTable(tableName);
            dataSourceMapping.setIdColumn(primaryKeyColumn != null ? primaryKeyColumn : "id");
            dataSourceMapping.setFieldMapping(fieldMapping);
            // connection_id 可以设置为 databaseId，用于标识连接
            dataSourceMapping.setConnectionId(databaseId);
            
            return new JdbcOntologyTable(objectType, dataSourceMapping, connection);
        } catch (Exception e) {
            System.err.println("Failed to create table from mapping for " + objectType.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取数据库连接（基于 databaseId）
     */
    private Connection getDatabaseConnection(String databaseId) {
        // 检查缓存
        if (databaseConnections.containsKey(databaseId)) {
            Connection conn = databaseConnections.get(databaseId);
            try {
                if (conn != null && !conn.isClosed()) {
                    return conn;
                }
            } catch (SQLException e) {
                // 连接已关闭，从缓存中移除
                databaseConnections.remove(databaseId);
            }
        }
        
        try {
            // 获取数据库实例信息
            Map<String, Object> database = instanceStorage.getInstance("database", databaseId);
            String host = (String) database.get("host");
            Integer port = database.get("port") instanceof Number 
                ? ((Number) database.get("port")).intValue() 
                : 3306;
            String dbName = (String) database.get("database_name");
            String username = (String) database.get("username");
            String password = (String) database.get("password");
            
            if (host == null || dbName == null || username == null) {
                System.err.println("Missing required database connection info for " + databaseId);
                return null;
            }
            
            // 加载 MySQL 驱动
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                    host, port, dbName);
            Connection conn = DriverManager.getConnection(url, username, password != null ? password : "");
            databaseConnections.put(databaseId, conn);
            return conn;
        } catch (Exception e) {
            System.err.println("Failed to get connection for database " + databaseId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 为 LinkType 创建中间表
     */
    private OntologyTable createLinkTable(LinkType linkType) {
        DataSourceMapping mapping = linkType.getDataSource();
        
        if (mapping != null && mapping.isConfigured()) {
            // 从数据库读取
            Connection connection = dataSourceConnections.get(mapping.getConnectionId());
            if (connection != null) {
                // 创建一个临时的 ObjectType 来表示中间表
                // 注意：这里我们使用 linkType 的名称作为 ObjectType 名称，但实际上表名是 mapping.getTable()
                ObjectType tempObjectType = new ObjectType();
                tempObjectType.setName(linkType.getName());
                
                // 复制 link type 的属性
                if (linkType.getProperties() != null) {
                    tempObjectType.setProperties(new java.util.ArrayList<>(linkType.getProperties()));
                } else {
                    tempObjectType.setProperties(new java.util.ArrayList<>());
                }
                
                // 为中间表添加外键列作为属性（这样它们会出现在行类型中）
                // 添加 source_id_column
                if (mapping.getSourceIdColumn() != null) {
                    com.mypalantir.meta.Property sourceIdProp = new com.mypalantir.meta.Property();
                    sourceIdProp.setName(mapping.getSourceIdColumn());
                    sourceIdProp.setDataType("string");
                    tempObjectType.getProperties().add(sourceIdProp);
                }
                
                // 添加 target_id_column
                if (mapping.getTargetIdColumn() != null) {
                    com.mypalantir.meta.Property targetIdProp = new com.mypalantir.meta.Property();
                    targetIdProp.setName(mapping.getTargetIdColumn());
                    targetIdProp.setDataType("string");
                    tempObjectType.getProperties().add(targetIdProp);
                }
                
                tempObjectType.setDataSource(mapping);
                
                return new JdbcOntologyTable(tempObjectType, mapping, connection);
            }
        }
        
        return null;
    }

    /**
     * 关闭所有数据源连接
     */
    public void closeConnections() {
        for (Connection conn : dataSourceConnections.values()) {
            try {
                conn.close();
            } catch (SQLException e) {
                // 忽略关闭错误
            }
        }
        dataSourceConnections.clear();
        
        for (Connection conn : databaseConnections.values()) {
            try {
                conn.close();
            } catch (SQLException e) {
                // 忽略关闭错误
            }
        }
        databaseConnections.clear();
    }
}

