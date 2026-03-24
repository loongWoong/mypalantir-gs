package com.mypalantir.config;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FalkorDB 配置
 * 仅当 storage.graph.type=falkordb 时启用
 */
@Configuration
@ConditionalOnProperty(name = "storage.graph.type", havingValue = "falkordb")
public class FalkorDBConfig {
    private static final Logger logger = LoggerFactory.getLogger(FalkorDBConfig.class);

    @Autowired
    private Config config;

    @Bean
    public Driver falkordbDriver() {
        String host = config.getFalkordbHost();
        int port = config.getFalkordbPort();
        
        logger.info("========== FalkorDB 连接配置 ==========");
        logger.info("FalkorDB Host: {}", host);
        logger.info("FalkorDB Port: {}", port);
        
        Driver driver = FalkorDB.driver(host, port);
        
        try {
            Graph graph = driver.graph(config.getFalkordbGraphName());
            graph.query("RETURN 1");
            logger.info("========== FalkorDB 连接成功 ==========");
        } catch (Exception e) {
            logger.warn("FalkorDB 连接测试失败: {}，启动继续，首次使用时会重试", e.getMessage());
        }
        
        return driver;
    }

    @Bean
    public Graph falkordbGraph(Driver falkordbDriver) {
        return falkordbDriver.graph(config.getFalkordbGraphName());
    }
}
