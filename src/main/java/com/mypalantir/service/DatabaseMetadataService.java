package com.mypalantir.service;

import com.mypalantir.config.DatabaseConfig;
import com.mypalantir.repository.IInstanceStorage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DatabaseMetadataService {
    private static final String DEFAULT_DB_KEY = "default";

    @Autowired
    private DatabaseConfig.DatabaseConnectionManager connectionManager;

    @Autowired
    @Lazy
    private IInstanceStorage instanceStorage;

    @Autowired(required = false)
    @Lazy
    @org.springframework.beans.factory.annotation.Qualifier("graphInstanceStorage")
    private IInstanceStorage graphInstanceStorage;

    @Value("${db.dynamic-pool.maximum-pool-size:20}")
    private int poolMaxSize;
    @Value("${db.dynamic-pool.minimum-idle:2}")
    private int poolMinIdle;
    @Value("${db.dynamic-pool.idle-timeout:600000}")
    private long poolIdleTimeoutMs;
    @Value("${db.dynamic-pool.max-lifetime:1800000}")
    private long poolMaxLifetimeMs;
    @Value("${db.dynamic-pool.connection-timeout:5000}")
    private long poolConnectionTimeoutMs;
    @Value("${db.dynamic-pool.query-timeout:30}")
    private int queryTimeoutSeconds;
    @Value("${db.dynamic-pool.socket-timeout:30000}")
    private int socketTimeoutMs;
    @Value("${db.dynamic-pool.connect-timeout:5000}")
    private int connectTimeoutMs;
    @Value("${db.dynamic-pool.leak-detection-threshold:0}")
    private long leakDetectionThresholdMs;

    /** 按 databaseId 缓存的 HikariCP 数据源，避免重复创建连接池 */
    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    // 线程本地变量，用于防止递归调用
    private static final ThreadLocal<Boolean> isGettingDatabaseInstance = ThreadLocal.withInitial(() -> false);

    public List<Map<String, Object>> getTables(String databaseId) throws SQLException, IOException {
        try (Connection conn = getConnectionForDatabase(databaseId)) {
            return getTables(conn, databaseId);
        }
    }

    /**
     * 使用已有连接获取表列表（供批量同步时复用连接）。
     * Oracle 使用 ALL_TABLES/ALL_VIEWS 直接 SQL，比 DatabaseMetaData 快得多。
     */
    public List<Map<String, Object>> getTables(Connection conn, String databaseId) throws SQLException, IOException {
        List<Map<String, Object>> tables = new ArrayList<>();
        String product = conn.getMetaData().getDatabaseProductName();
        boolean isOracle = product != null && product.toUpperCase().contains("ORACLE");

        if (isOracle) {
            // Oracle 快速路径：直接查询 ALL_TABLES + ALL_VIEWS，避免 DatabaseMetaData 慢速扫描
            int timeout = Math.max(getQueryTimeoutSeconds(), 120);
            String sql = "SELECT OWNER, TABLE_NAME, 'TABLE' AS TABLE_TYPE FROM ALL_TABLES " +
                    "UNION ALL SELECT OWNER, VIEW_NAME AS TABLE_NAME, 'VIEW' AS TABLE_TYPE FROM ALL_VIEWS";
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(timeout);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        Map<String, Object> table = new HashMap<>();
                        table.put("name", rs.getString("TABLE_NAME"));
                        table.put("type", rs.getString("TABLE_TYPE"));
                        table.put("schema", rs.getString("OWNER"));
                        table.put("catalog", null);
                        table.put("database_id", databaseId);
                        tables.add(table);
                    }
                }
            }
        } else {
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
        }
        return tables;
    }

    public List<Map<String, Object>> getColumns(String databaseId, String tableName) throws SQLException, IOException {
        try (Connection conn = getConnectionForDatabase(databaseId)) {
            return getColumns(conn, databaseId, tableName);
        }
    }

    /**
     * 使用已有连接获取单表列信息（供批量同步时复用连接）。
     */
    public List<Map<String, Object>> getColumns(Connection conn, String databaseId, String tableName) throws SQLException, IOException {
        List<Map<String, Object>> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        String catalog = getDatabaseName(databaseId);
        if (metaData.getDatabaseProductName() != null && metaData.getDatabaseProductName().toUpperCase().contains("ORACLE")) {
            catalog = null;
        }
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
        try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, null, tableName)) {
            List<String> primaryKeys = new ArrayList<>();
            while (pkRs.next()) {
                primaryKeys.add(pkRs.getString("COLUMN_NAME"));
            }
            for (Map<String, Object> column : columns) {
                column.put("is_primary_key", primaryKeys.contains(column.get("name")));
            }
        }
        return columns;
    }

    /**
     * 批量获取多表列信息（Oracle 使用单次 SQL，大幅减少往返）。
     * @param tables 表列表，每项含 "name" 和 "schema"（Oracle 需要 schema 区分不同用户下的同表名）
     * @return Map: 表的查找键 -> List<column map>。键为 schema!=null 时 "schema.name"，否则 "name"
     */
    public Map<String, List<Map<String, Object>>> getAllColumnsBatch(Connection conn, String databaseId, List<Map<String, Object>> tables) throws SQLException, IOException {
        if (tables == null || tables.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> tableNames = new ArrayList<>();
        for (Map<String, Object> t : tables) {
            tableNames.add((String) t.get("name"));
        }
        String product = conn.getMetaData().getDatabaseProductName();
        boolean isOracle = product != null && product.toUpperCase().contains("ORACLE");

        if (isOracle) {
            return getAllColumnsBatchOracle(conn, databaseId, tables);
        }
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (Map<String, Object> t : tables) {
            String tableName = (String) t.get("name");
            String schema = (String) t.get("schema");
            String key = (schema != null && !schema.isEmpty()) ? schema + "." + tableName : tableName;
            result.put(key, getColumns(conn, databaseId, tableName));
        }
        return result;
    }

    private static final int ORACLE_IN_CLAUSE_BATCH = 500;

    /** Oracle：分批查询 ALL_TAB_COLUMNS + ALL_CONS_COLUMNS（IN 子句限制约 1000 项） */
    private Map<String, List<Map<String, Object>>> getAllColumnsBatchOracle(Connection conn, String databaseId, List<Map<String, Object>> tables) throws SQLException {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        if (tables.isEmpty()) return result;

        for (int start = 0; start < tables.size(); start += ORACLE_IN_CLAUSE_BATCH) {
            int end = Math.min(start + ORACLE_IN_CLAUSE_BATCH, tables.size());
            List<Map<String, Object>> batch = tables.subList(start, end);
            Map<String, List<Map<String, Object>>> batchResult = getAllColumnsBatchOracleChunk(conn, databaseId, batch);
            batchResult.forEach((k, v) -> result.merge(k, v, (a, b) -> { a.addAll(b); return a; }));
        }
        return result;
    }

    private Map<String, List<Map<String, Object>>> getAllColumnsBatchOracleChunk(Connection conn, String databaseId, List<Map<String, Object>> tables) throws SQLException {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        int timeout = Math.max(getQueryTimeoutSeconds(), 180);

        List<String> names = new ArrayList<>();
        for (Map<String, Object> t : tables) {
            names.add(((String) t.get("name")).toUpperCase());
        }
        String placeholders = names.stream().map(n -> "?").collect(Collectors.joining(","));

        Map<String, Set<String>> pkMap = new HashMap<>();
        String pkSql = "SELECT c.OWNER, c.TABLE_NAME, c.COLUMN_NAME FROM ALL_CONS_COLUMNS c " +
                "JOIN ALL_CONSTRAINTS k ON c.OWNER=k.OWNER AND c.CONSTRAINT_NAME=k.CONSTRAINT_NAME AND c.TABLE_NAME=k.TABLE_NAME " +
                "WHERE k.CONSTRAINT_TYPE='P' AND (c.OWNER, c.TABLE_NAME) IN (SELECT OWNER, TABLE_NAME FROM ALL_TABLES WHERE TABLE_NAME IN (" + placeholders + ") " +
                "UNION SELECT OWNER, VIEW_NAME FROM ALL_VIEWS WHERE VIEW_NAME IN (" + placeholders + "))";

        try (PreparedStatement pkStmt = conn.prepareStatement(pkSql)) {
            pkStmt.setQueryTimeout(timeout);
            int idx = 1;
            for (String n : names) { pkStmt.setString(idx++, n); }
            for (String n : names) { pkStmt.setString(idx++, n); }
            try (ResultSet rs = pkStmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("OWNER") + "." + rs.getString("TABLE_NAME");
                    pkMap.computeIfAbsent(key, k -> new HashSet<>()).add(rs.getString("COLUMN_NAME"));
                }
            }
        }

        String colSql = "SELECT OWNER, TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE, COLUMN_ID " +
                "FROM ALL_TAB_COLUMNS WHERE (OWNER, TABLE_NAME) IN (" +
                "SELECT OWNER, TABLE_NAME FROM ALL_TABLES WHERE TABLE_NAME IN (" + placeholders + ") " +
                "UNION SELECT OWNER, VIEW_NAME FROM ALL_VIEWS WHERE VIEW_NAME IN (" + placeholders + ")) " +
                "ORDER BY TABLE_NAME, COLUMN_ID";

        try (PreparedStatement colStmt = conn.prepareStatement(colSql)) {
            colStmt.setQueryTimeout(timeout);
            int idx = 1;
            for (String n : names) { colStmt.setString(idx++, n); }
            for (String n : names) { colStmt.setString(idx++, n); }
            try (ResultSet rs = colStmt.executeQuery()) {
                while (rs.next()) {
                    String owner = rs.getString("OWNER");
                    String tableName = rs.getString("TABLE_NAME");
                    String key = owner + "." + tableName;
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", rs.getString("COLUMN_NAME"));
                    column.put("data_type", rs.getString("DATA_TYPE"));
                    column.put("data_type_code", -1);
                    column.put("nullable", "Y".equalsIgnoreCase(rs.getString("NULLABLE")));
                    int prec = rs.getInt("DATA_PRECISION");
                    int scale = rs.getInt("DATA_SCALE");
                    column.put("column_size", prec > 0 ? prec : rs.getInt("DATA_LENGTH"));
                    column.put("decimal_digits", scale);
                    column.put("remarks", null);
                    column.put("table_name", tableName);
                    column.put("database_id", databaseId);
                    column.put("is_primary_key", pkMap.getOrDefault(key, Collections.emptySet()).contains(rs.getString("COLUMN_NAME")));
                    result.computeIfAbsent(key, k -> new ArrayList<>()).add(column);
                }
            }
        }
        return result;
    }

    /**
     * 获取表的主键列名列表，按 KEY_SEQ 顺序返回（支持联合主键）
     * @param databaseId 数据库 ID（null 表示默认数据库）
     * @param tableName 表名
     * @return 主键列名列表，无主键或失败时返回空列表
     */
    public List<String> getPrimaryKeyColumns(String databaseId, String tableName) {
        List<String> ordered = new ArrayList<>();
        try (Connection conn = getConnectionForDatabase(databaseId)) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = getDatabaseName(databaseId);
            if (metaData.getDatabaseProductName() != null && metaData.getDatabaseProductName().toUpperCase().contains("ORACLE")) {
                catalog = null;
            }
            try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, null, tableName)) {
                List<Object[]> rows = new ArrayList<>();
                while (pkRs.next()) {
                    int keySeq = pkRs.getInt("KEY_SEQ");
                    String columnName = pkRs.getString("COLUMN_NAME");
                    rows.add(new Object[]{ Integer.valueOf(keySeq), columnName });
                }
                rows.sort((a, b) -> ((Integer) a[0]).compareTo((Integer) b[0]));
                for (Object[] row : rows) {
                    ordered.add((String) row[1]);
                }
            }
        } catch (SQLException | IOException e) {
            // 返回空列表，调用方会回退到默认 id 列
        }
        return ordered;
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
        
        try (Connection conn = getConnectionForDatabase(databaseId);
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(getQueryTimeoutSeconds());
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        value = convertSqlValue(value);
                        row.put(columnName, value);
                    }
                    results.add(row);
                }
            }
        }
        
        return results;
    }

    /**
     * 获取数据库类型（mysql、doris、oracle、postgresql 等），用于生成兼容的 DDL。
     * 默认数据库使用 Config.db.type，动态数据源从实例的 type 字段读取。
     */
    public String getDatabaseType(String databaseId) {
        if (databaseId == null || databaseId.isEmpty() || DEFAULT_DB_KEY.equals(databaseId)) {
            return connectionManager.getConfig().getDbType() != null
                    ? connectionManager.getConfig().getDbType().toLowerCase() : "mysql";
        }
        try {
            if (isGettingDatabaseInstance.get()) {
                return "mysql";
            }
            isGettingDatabaseInstance.set(true);
            try {
                Map<String, Object> database = (graphInstanceStorage != null ? graphInstanceStorage : instanceStorage).getInstance("database", databaseId);
                Object type = database.get("type");
                return type != null ? type.toString().toLowerCase() : "mysql";
            } finally {
                isGettingDatabaseInstance.set(false);
            }
        } catch (Exception e) {
            return "mysql";
        }
    }

    /**
     * 获取数据库连接（公开方法，供其他服务使用）
     */
    public Connection getConnectionForDatabase(String databaseId) throws SQLException, IOException {
        return getDataSourceForDatabase(databaseId).getConnection();
    }

    /**
     * 获取数据源（DataSource），使用 HikariCP 连接池并缓存，避免长时间空闲未释放
     */
    public DataSource getDataSourceForDatabase(String databaseId) throws IOException {
        String cacheKey = (databaseId == null || databaseId.isEmpty()) ? DEFAULT_DB_KEY : databaseId;
        DataSource ds = dataSourceCache.get(cacheKey);
        if (ds == null) {
            ds = createPooledDataSource(cacheKey);
            DataSource existing = dataSourceCache.putIfAbsent(cacheKey, ds);
            if (existing != null) {
                if (ds instanceof HikariDataSource) {
                    ((HikariDataSource) ds).close();
                }
                ds = existing;
            }
        }
        return ds;
    }

    /**
     * 创建 HikariCP 连接池数据源
     */
    private DataSource createPooledDataSource(String cacheKey) throws IOException {
        String url;
        String username;
        String password;
        String dbType = "mysql";

        if (DEFAULT_DB_KEY.equals(cacheKey)) {
            url = connectionManager.getJdbcUrl();
            username = connectionManager.getConfig().getDbUser();
            password = connectionManager.getConfig().getDbPassword();
            String configuredType = connectionManager.getConfig().getDbType();
            if (configuredType != null) dbType = configuredType.toLowerCase();
        } else {
            if (isGettingDatabaseInstance.get()) {
                throw new IOException("Recursive call detected while getting database instance: " + cacheKey);
            }
            Map<String, Object> database;
            try {
                isGettingDatabaseInstance.set(true);
                database = (graphInstanceStorage != null ? graphInstanceStorage : instanceStorage).getInstance("database", cacheKey);
            } finally {
                isGettingDatabaseInstance.set(false);
            }

            String host = (String) database.get("host");
            Integer port = database.get("port") instanceof Number
                    ? ((Number) database.get("port")).intValue()
                    : 3306;
            String dbName = (String) database.get("database_name");
            dbType = database.get("type") != null ? database.get("type").toString().toLowerCase() : "mysql";
            username = (String) database.get("username");
            password = connectionManager.getConfig().getDbPassword();
            if (database.containsKey("password") && database.get("password") != null) {
                password = database.get("password").toString();
            }
            url = buildJdbcUrlForType(host, port, dbName, dbType);
        }

        url = appendJdbcTimeoutParams(url);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolMaxSize);
        config.setMinimumIdle(poolMinIdle);
        config.setIdleTimeout(poolIdleTimeoutMs);
        config.setMaxLifetime(poolMaxLifetimeMs);
        config.setConnectionTimeout(poolConnectionTimeoutMs);
        if (leakDetectionThresholdMs > 0) {
            config.setLeakDetectionThreshold(leakDetectionThresholdMs);
        }
        // 对 MySQL/Doris 连接统一设置 collation，避免跨表 JOIN 时排序规则冲突
        if ("mysql".equals(dbType) || "doris".equals(dbType)) {
            config.setConnectionInitSql("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        config.setPoolName("mypalantir-ds-" + cacheKey);
        return new HikariDataSource(config);
    }

    @PreDestroy
    public void destroy() {
        for (DataSource ds : dataSourceCache.values()) {
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }
        }
        dataSourceCache.clear();
    }

    /**
     * 根据数据库类型构建 JDBC URL
     * 支持 mysql、doris（MySQL 协议）、postgresql、oracle 等
     */
    private String buildJdbcUrlForType(String host, int port, String dbName, String dbType) {
        String baseParams = "useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true";
        if ("postgresql".equals(dbType) || "postgres".equals(dbType)) {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
        }
        if ("oracle".equals(dbType)) {
            // SID 格式: jdbc:oracle:thin:@host:port:SID（仅限字母数字下划线）
            // Service Name 格式: jdbc:oracle:thin:@//host:port/service_name（含 #、.、/ 如 C##clear 等必须用此格式）
            if (dbName != null && needsOracleServiceNameFormat(dbName)) {
                return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, dbName);
            }
            return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, dbName);
        }
        // mysql、doris 均使用 MySQL 协议（Doris 默认端口 9030）
        return String.format("jdbc:mysql://%s:%d/%s?%s", host, port, dbName, baseParams);
    }

    /** Oracle SID 不允许 #、.、/，含这些字符时使用 Service Name 格式 */
    private static boolean needsOracleServiceNameFormat(String dbName) {
        return dbName != null && (dbName.contains("#") || dbName.contains(".") || dbName.contains("/"));
    }

    /**
     * 在 JDBC URL 后追加 socket/connect 超时参数，防止 MySQL 无响应时无限阻塞占用连接。
     * Oracle 使用不同机制，不追加此类参数。
     */
    private String appendJdbcTimeoutParams(String url) {
        if (url == null) return url;
        if (url.startsWith("jdbc:oracle:")) {
            return url;
        }
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "socketTimeout=" + socketTimeoutMs + "&connectTimeout=" + connectTimeoutMs;
    }

    /**
     * 获取配置的查询超时秒数（用于 Statement.setQueryTimeout）
     */
    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds > 0 ? queryTimeoutSeconds : 30;
    }

    /**
     * 根据 databaseId 获取实际数据库名（catalog），供 Calcite JdbcSchema 使用。
     * databaseId 为 null 或空时返回默认数据库名。
     */
    public String getDatabaseNameById(String databaseId) throws IOException {
        return getDatabaseName(databaseId);
    }

    private String getDatabaseName(String databaseId) throws IOException {
        if (databaseId == null || databaseId.isEmpty()) {
            // 使用默认数据库配置（从 application.properties 读取）
            return connectionManager.getConfig().getDbName();
        }
        
        // 注意：直接使用 Neo4jInstanceStorage 避免循环调用
        // 使用线程本地变量防止递归调用
        if (isGettingDatabaseInstance.get()) {
            throw new IOException("Recursive call detected while getting database name: " + databaseId);
        }
        try {
            isGettingDatabaseInstance.set(true);
            Map<String, Object> database = (graphInstanceStorage != null ? graphInstanceStorage : instanceStorage).getInstance("database", databaseId);
            return (String) database.get("database_name");
        } finally {
            isGettingDatabaseInstance.set(false);
        }
    }

    /**
     * 执行更新SQL（INSERT, UPDATE, DELETE, CREATE TABLE等）
     */
    public int executeUpdate(String sql, String databaseId) throws SQLException, IOException {
        try (Connection conn = getConnectionForDatabase(databaseId);
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(getQueryTimeoutSeconds());
            return stmt.executeUpdate(sql);
        }
    }

    /**
     * 检查表是否存在
     * MySQL/PostgreSQL: 使用 INFORMATION_SCHEMA
     * Oracle: 使用 ALL_TABLES（根据连接用户可见的表）
     */
    public boolean tableExists(String databaseId, String tableName) throws SQLException, IOException {
        try (Connection conn = getConnectionForDatabase(databaseId)) {
            DatabaseMetaData metaData = conn.getMetaData();
            String product = metaData.getDatabaseProductName();
            if (product != null && product.toUpperCase().contains("ORACLE")) {
                // Oracle: 使用 ALL_TABLES，表名转为大写（Oracle 默认大写）
                String sql = "SELECT COUNT(*) FROM ALL_TABLES WHERE TABLE_NAME = UPPER(?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setQueryTimeout(getQueryTimeoutSeconds());
                    stmt.setString(1, tableName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() && rs.getInt(1) > 0;
                    }
                }
            }
            // MySQL/PostgreSQL/Doris: 使用 INFORMATION_SCHEMA
            String dbName = getDatabaseName(databaseId);
            String sql;
            PreparedStatement stmt;
            if (dbName != null && !dbName.isEmpty()) {
                sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'";
                stmt = conn.prepareStatement(sql);
                stmt.setQueryTimeout(getQueryTimeoutSeconds());
                stmt.setString(1, dbName);
                stmt.setString(2, tableName);
            } else {
                sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'";
                stmt = conn.prepareStatement(sql);
                stmt.setQueryTimeout(getQueryTimeoutSeconds());
                stmt.setString(1, tableName);
            }
            try (stmt) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            // 回退到 DatabaseMetaData 方法
            try (Connection conn = getConnectionForDatabase(databaseId)) {
                DatabaseMetaData metaData = conn.getMetaData();
                try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
                    if (rs.next()) return true;
                }
                String catalog = getDatabaseName(databaseId);
                if (catalog != null && !catalog.isEmpty()) {
                    try (ResultSet rs = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
                        return rs.next();
                    }
                }
            } catch (SQLException e2) {
                throw new SQLException("Failed to check table existence: " + e.getMessage(), e);
            }
            return false;
        }
    }

    /**
     * 将 SQL 类型转换为基本类型，以便保存到 Neo4j
     */
    private Object convertSqlValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // 处理日期时间类型
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate().toString();
        }
        if (value instanceof java.sql.Time) {
            return ((java.sql.Time) value).toLocalTime().toString();
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toInstant().toString();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant().toString();
        }
        
        // 处理数值类型
        if (value instanceof java.math.BigDecimal) {
            // 尝试转换为 Long 或 Double
            java.math.BigDecimal bd = (java.math.BigDecimal) value;
            try {
                return bd.longValueExact();
            } catch (ArithmeticException e) {
                return bd.doubleValue();
            }
        }
        if (value instanceof java.math.BigInteger) {
            return ((java.math.BigInteger) value).longValue();
        }
        
        // 处理字节数组
        if (value instanceof byte[]) {
            return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
        }
        
        // 其他类型直接返回
        return value;
    }
}
