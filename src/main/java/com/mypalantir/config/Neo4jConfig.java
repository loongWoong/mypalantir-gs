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

        // 检查配置完整性
        if (uri == null || uri.isEmpty()) {
            logger.warn("Neo4j URI is not configured. Please set NEO4J_URI environment variable or neo4j.uri in application.properties");
            return null;
        }
        if (user == null || user.isEmpty()) {
            logger.warn("Neo4j user is not configured. Please set NEO4J_USER environment variable or neo4j.user in application.properties");
            return null;
        }
        if (password == null || password.isEmpty()) {
            logger.warn("Neo4j password is not configured. Please set NEO4J_PASSWORD environment variable or neo4j.password in application.properties");
            return null;
        }

        try {
            Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
            // 测试连接
            driver.verifyConnectivity();
            logger.info("Neo4j driver initialized successfully. URI: {}, User: {}", uri, user);
            
            // 执行一个简单的查询来验证连接
            try (org.neo4j.driver.Session session = driver.session()) {
                var result = session.run("RETURN 1 AS test");
                result.consume();
                logger.info("Neo4j connection test query executed successfully");
            }
            
            return driver;
        } catch (Exception e) {
            logger.error("Failed to initialize Neo4j driver. URI: {}, User: {}, Error: {}", uri, user, e.getMessage(), e);
            logger.error("Please ensure Neo4j is running and accessible at {}", uri);
            logger.error("Check if Neo4j is listening on the correct port and if authentication credentials are correct");
            return null;
        }
    }
}
