package com.mypalantir.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {
    private static final Logger logger = LoggerFactory.getLogger(Neo4jConfig.class);

    @Autowired
    private Config config;

    @Bean
    public Driver neo4jDriver() {
        String uri = config.getNeo4jUri();
        String user = config.getNeo4jUser();
        String password = config.getNeo4jPassword();

        if (uri == null || uri.isEmpty() || password == null || password.isEmpty()) {
            logger.warn("Neo4j configuration is incomplete. Neo4j driver will not be initialized.");
            return null;
        }

        try {
            Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
            // 测试连接
            driver.verifyConnectivity();
            logger.info("Neo4j driver initialized successfully. URI: {}", uri);
            return driver;
        } catch (Exception e) {
            logger.error("Failed to initialize Neo4j driver: {}", e.getMessage(), e);
            return null;
        }
    }
}
