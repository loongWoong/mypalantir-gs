package com.mypalantir.query.schema;

import com.mypalantir.meta.DataSourceConfig;
import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 将 Ontology Schema 转换为 Calcite Schema
 */
public class OntologySchemaFactory {
    private final Loader loader;
    private final Map<String, Connection> dataSourceConnections = new HashMap<>();

    public OntologySchemaFactory(Loader loader) {
        this.loader = loader;
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

        // 为每个 ObjectType 创建 Table
        if (loader.getSchema() != null && loader.getSchema().getObjectTypes() != null) {
            for (ObjectType objectType : loader.getSchema().getObjectTypes()) {
                OntologyTable table = createTable(objectType);
                if (table != null) {
                    // 直接将表添加到 rootSchema
                    rootSchema.add(objectType.getName(), table);
                }
            }
        }
        
        // 为每个有 data_source 的 LinkType 创建中间表
        if (loader.getSchema() != null && loader.getSchema().getLinkTypes() != null) {
            for (LinkType linkType : loader.getSchema().getLinkTypes()) {
                if (linkType.getDataSource() != null && linkType.getDataSource().isConfigured()) {
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
     * 为 ObjectType 创建 Table
     */
    private OntologyTable createTable(ObjectType objectType) {
        DataSourceMapping mapping = objectType.getDataSource();
        
        if (mapping != null && mapping.isConfigured()) {
            // 从数据库读取
            Connection connection = dataSourceConnections.get(mapping.getConnectionId());
            if (connection != null) {
                return new JdbcOntologyTable(objectType, mapping, connection);
            }
        } else {
            // 从文件系统读取（后续实现）
            // return new FileSystemOntologyTable(objectType);
        }
        
        return null;
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
    }
}

