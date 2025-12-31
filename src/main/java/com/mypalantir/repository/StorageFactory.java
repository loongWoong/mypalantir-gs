package com.mypalantir.repository;

import com.mypalantir.config.Config;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

/**
 * 存储工厂
 * 根据配置选择文件存储或 Neo4j 存储
 */
@Configuration
public class StorageFactory {
    private static final Logger logger = LoggerFactory.getLogger(StorageFactory.class);

    @Autowired
    private Config config;

    @Autowired(required = false)
    private Driver neo4jDriver;

    @Bean
    public Neo4jInstanceStorage neo4jInstanceStorage() {
        return new Neo4jInstanceStorage();
    }

    @Bean
    public Neo4jLinkStorage neo4jLinkStorage() {
        return new Neo4jLinkStorage();
    }

    @Bean
    @Primary
    @DependsOn("neo4jDriver")
    public IInstanceStorage instanceStorage(PathManager pathManager, InstanceStorage fileStorage, Neo4jInstanceStorage neo4jStorage) {
        String storageType = config.getStorageType();
        logger.info("Initializing instance storage with type: {}", storageType);
        
        if ("neo4j".equalsIgnoreCase(storageType)) {
            if (neo4jDriver == null) {
                logger.warn("Neo4j driver is not available, falling back to file storage");
                return fileStorage;
            }
            logger.info("Using Neo4j instance storage");
            return neo4jStorage;
        } else {
            logger.info("Using file instance storage");
            return fileStorage;
        }
    }

    @Bean
    @Primary
    @DependsOn("neo4jDriver")
    public ILinkStorage linkStorage(PathManager pathManager, LinkStorage fileStorage, Neo4jLinkStorage neo4jStorage) {
        String storageType = config.getStorageType();
        logger.info("Initializing link storage with type: {}", storageType);
        
        if ("neo4j".equalsIgnoreCase(storageType)) {
            if (neo4jDriver == null) {
                logger.warn("Neo4j driver is not available, falling back to file storage");
                return fileStorage;
            }
            logger.info("Using Neo4j link storage");
            return neo4jStorage;
        } else {
            logger.info("Using file link storage");
            return fileStorage;
        }
    }
}
