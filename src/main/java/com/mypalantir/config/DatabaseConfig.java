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
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                    config.getDbHost(), config.getDbPort(), config.getDbName());
            return DriverManager.getConnection(url, config.getDbUser(), config.getDbPassword());
        }

        public String getJdbcUrl() {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                    config.getDbHost(), config.getDbPort(), config.getDbName());
        }

        public Config getConfig() {
            return config;
        }
    }
}
