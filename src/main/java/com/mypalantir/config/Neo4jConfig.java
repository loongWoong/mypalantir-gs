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
    private com.mypalantir.config.Config config;

    @Bean
    public Driver neo4jDriver() {
        String uri = config.getNeo4jUri();
        String user = config.getNeo4jUser();
        String password = config.getNeo4jPassword();

        // ========== 打印 Neo4j 连接参数 ==========
        logger.info("========== Neo4j 连接参数配置 ==========");
        logger.info("Neo4j URI: {}", uri != null ? uri : "未配置");
        logger.info("Neo4j User: {}", user != null ? user : "未配置");
        logger.info("Neo4j Password: {}", password != null && !password.isEmpty() ? "***已配置***" : "未配置");
        
        // 解析URI获取详细信息
        if (uri != null && !uri.isEmpty()) {
            try {
                java.net.URI parsedUri = java.net.URI.create(uri);
                logger.info("Neo4j 连接协议: {}", parsedUri.getScheme());
                logger.info("Neo4j 主机地址: {}", parsedUri.getHost());
                logger.info("Neo4j 端口: {}", parsedUri.getPort() > 0 ? parsedUri.getPort() : "默认端口");
                logger.info("Neo4j 完整URI: {}", uri);
            } catch (Exception e) {
                logger.warn("无法解析Neo4j URI: {}", uri);
            }
        }
        logger.info("=========================================");

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
            // 配置连接池参数以解决长时间运行后的连接问题
            logger.info("========== Neo4j 连接池配置 ==========");
            logger.info("连接超时: 30秒");
            logger.info("最大连接池大小: 50");
            logger.info("连接获取超时: 60秒");
            logger.info("连接最大生命周期: 1小时");
            logger.info("连接活跃性检查超时: 30分钟");
            logger.info("日志级别: SLF4J");
            logger.info("=====================================");
            
            org.neo4j.driver.Config.ConfigBuilder configBuilder = org.neo4j.driver.Config.builder()
                    // 连接超时：30秒
                    .withConnectionTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    // 最大连接池大小：50
                    .withMaxConnectionPoolSize(50)
                    // 连接获取超时：60秒
                    .withConnectionAcquisitionTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    // 连接最大生命周期：1小时（防止连接长时间使用后失效）
                    .withMaxConnectionLifetime(1, java.util.concurrent.TimeUnit.HOURS)
                    // 连接活跃性检查超时：30分钟（空闲连接在重用前会被测试）
                    .withConnectionLivenessCheckTimeout(30, java.util.concurrent.TimeUnit.MINUTES)
                    // 启用连接健康检查日志
                    .withLogging(org.neo4j.driver.Logging.slf4j());

            logger.info("正在创建 Neo4j Driver 连接...");
            Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password), configBuilder.build());
            
            // 测试连接
            logger.info("正在验证 Neo4j 连接...");
            driver.verifyConnectivity();
            logger.info("========== Neo4j 连接成功 ==========");
            logger.info("Neo4j driver initialized successfully");
            logger.info("URI: {}", uri);
            logger.info("User: {}", user);
            logger.info("Connection pool: maxPoolSize=50, maxLifetime=1h, idleTimeout=30m");
            logger.info("=====================================");
            
            // 执行一个简单的查询来验证连接
            logger.info("正在执行 Neo4j 连接测试查询...");
            try (org.neo4j.driver.Session session = driver.session()) {
                var result = session.run("RETURN 1 AS test");
                result.consume();
                logger.info("Neo4j 连接测试查询执行成功");
            }
            
            return driver;
        } catch (Exception e) {
            logger.error("========== Neo4j 连接失败 ==========");
            logger.error("Failed to initialize Neo4j driver");
            logger.error("URI: {}", uri);
            logger.error("User: {}", user);
            logger.error("Error: {}", e.getMessage(), e);
            logger.error("Please ensure Neo4j is running and accessible at {}", uri);
            logger.error("Check if Neo4j is listening on the correct port and if authentication credentials are correct");
            logger.error("=====================================");
            return null;
        }
    }
}
