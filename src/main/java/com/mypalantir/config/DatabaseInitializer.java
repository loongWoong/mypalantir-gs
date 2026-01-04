package com.mypalantir.config;

import com.mypalantir.meta.DataSourceConfig;
import com.mypalantir.meta.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 应用启动时初始化数据库和创建必要的文件夹
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private Config config;

    @Autowired
    private Loader loader;

    @Override
    public void run(String... args) throws Exception {
        // 创建 data 文件夹
        createDataDirectories();

        // 初始化 H2 数据库
        initializeH2Databases();
    }

    /**
     * 创建必要的 data 文件夹结构
     */
    private void createDataDirectories() {
        try {
            String dataRootPath = config.getDataRootPath();
            File dataDir = new File(dataRootPath);
            if (!dataDir.exists()) {
                dataDir.mkdirs();
                logger.info("Created data directory: {}", dataRootPath);
            }

            // 创建 H2 数据库目录
            File h2Dir = new File(dataRootPath, "h2");
            if (!h2Dir.exists()) {
                h2Dir.mkdirs();
                logger.info("Created H2 database directory: {}", h2Dir.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.error("Failed to create data directories", e);
        }
    }

    /**
     * 初始化所有配置的 H2 数据库
     */
    private void initializeH2Databases() {
        try {
            List<DataSourceConfig> dataSources = loader.listDataSources();
            if (dataSources == null || dataSources.isEmpty()) {
                logger.info("No data sources configured, skipping H2 database initialization");
                return;
            }

            for (DataSourceConfig ds : dataSources) {
                if ("h2".equalsIgnoreCase(ds.getType())) {
                    initializeH2Database(ds);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize H2 databases", e);
        }
    }

    /**
     * 初始化单个 H2 数据库
     */
    private void initializeH2Database(DataSourceConfig ds) {
        try {
            String jdbcUrl = ds.getJdbcUrl();
            if (jdbcUrl == null || jdbcUrl.isEmpty()) {
                logger.warn("H2 data source {} has no JDBC URL, skipping initialization", ds.getId());
                return;
            }

            // 检查是否是文件模式
            if (!jdbcUrl.contains(":file:")) {
                logger.info("H2 data source {} is not in file mode, skipping initialization", ds.getId());
                return;
            }

            // 提取数据库文件路径
            // jdbc:h2:file:./data/h2/mypalantir -> ./data/h2/mypalantir
            String dbPath = jdbcUrl.replaceFirst("jdbc:h2:file:", "");
            File dbFile = new File(dbPath + ".mv.db");
            File dbDir = dbFile.getParentFile();

            // 如果数据库文件已存在，跳过初始化
            if (dbFile.exists()) {
                logger.info("H2 database {} already exists, skipping initialization", dbPath);
                return;
            }

            // 确保目录存在
            if (dbDir != null && !dbDir.exists()) {
                dbDir.mkdirs();
                logger.info("Created H2 database directory: {}", dbDir.getAbsolutePath());
            }

            // 连接数据库（这会自动创建数据库文件）
            String username = ds.getUsername() != null ? ds.getUsername() : "sa";
            String password = ds.getPassword() != null ? ds.getPassword() : "";

            try {
                Class.forName("org.h2.Driver");
                try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                     Statement stmt = conn.createStatement()) {
                    
                    // 执行初始化 SQL（如果存在）
                    String initSqlFile = "scripts/init_h2_tables.sql";
                    File sqlFile = new File(initSqlFile);
                    if (sqlFile.exists()) {
                        logger.info("Initializing H2 database {} with SQL file: {}", ds.getId(), initSqlFile);
                        String sql = readSqlFile(initSqlFile);
                        String[] statements = sql.split(";");
                        for (String statement : statements) {
                            statement = statement.trim();
                            if (!statement.isEmpty() && !statement.startsWith("--")) {
                                try {
                                    stmt.execute(statement);
                                    logger.debug("Executed SQL statement for database {}", ds.getId());
                                } catch (SQLException e) {
                                    // 忽略已存在的表错误
                                    if (!e.getMessage().contains("already exists")) {
                                        logger.warn("Error executing SQL for database {}: {}", ds.getId(), e.getMessage());
                                    }
                                }
                            }
                        }
                        logger.info("H2 database {} initialized successfully", ds.getId());
                    } else {
                        // 即使没有初始化 SQL，也创建数据库连接以确保数据库文件被创建
                        logger.info("H2 database {} created (no initialization SQL file found)", ds.getId());
                    }
                }
            } catch (ClassNotFoundException e) {
                logger.error("H2 Driver not found, cannot initialize database {}", ds.getId());
            } catch (SQLException e) {
                logger.error("Failed to initialize H2 database {}: {}", ds.getId(), e.getMessage(), e);
            }
        } catch (Exception e) {
            logger.error("Error initializing H2 database {}: {}", ds.getId(), e.getMessage(), e);
        }
    }

    /**
     * 读取 SQL 文件内容
     */
    private String readSqlFile(String filePath) throws Exception {
        StringBuilder sql = new StringBuilder();
        try (var reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 跳过注释行和空行
                String trimmed = line.trim();
                if (!trimmed.startsWith("--") && !trimmed.isEmpty()) {
                    sql.append(line).append("\n");
                }
            }
        }
        return sql.toString();
    }
}
