package com.mypalantir.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Configuration
public class DatabaseConfig {
    @Autowired
    private Config config;

    @Bean
    public DatabaseConnectionManager databaseConnectionManager() {
        return new DatabaseConnectionManager(config);
    }

    public static class DatabaseConnectionManager {
        private final Config config;

        public DatabaseConnectionManager(Config config) {
            this.config = config;
        }

        public Connection getConnection() throws SQLException {
            String dbName = config.getDbName();
            String url;
            // 如果数据库名为空，不指定数据库名，让MySQL使用默认数据库或当前连接的数据库
            if (dbName == null || dbName.trim().isEmpty()) {
                url = String.format("jdbc:mysql://%s:%d?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                        config.getDbHost(), config.getDbPort());
            } else {
                url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                        config.getDbHost(), config.getDbPort(), dbName);
            }
            return DriverManager.getConnection(url, config.getDbUser(), config.getDbPassword());
        }

        public String getJdbcUrl() {
            String dbName = config.getDbName();
            // 如果数据库名为空，不指定数据库名，让MySQL使用默认数据库或当前连接的数据库
            if (dbName == null || dbName.trim().isEmpty()) {
                return String.format("jdbc:mysql://%s:%d?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                        config.getDbHost(), config.getDbPort());
            } else {
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                        config.getDbHost(), config.getDbPort(), dbName);
            }
        }

        public Config getConfig() {
            return config;
        }
    }
}
