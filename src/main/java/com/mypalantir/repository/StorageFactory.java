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
    public RelationalInstanceStorage relationalInstanceStorage() {
        return new RelationalInstanceStorage();
    }

    @Bean
    public HybridInstanceStorage hybridInstanceStorage() {
        return new HybridInstanceStorage();
    }

    @Bean
    @Primary
    @DependsOn("neo4jDriver")
    public IInstanceStorage instanceStorage(PathManager pathManager, InstanceStorage fileStorage, 
                                           Neo4jInstanceStorage neo4jStorage, 
                                           RelationalInstanceStorage relationalStorage,
                                           HybridInstanceStorage hybridStorage) {
        String storageType = config.getStorageType();
        logger.info("Initializing instance storage with type: {}", storageType);
        
        if ("hybrid".equalsIgnoreCase(storageType)) {
            if (neo4jDriver == null) {
                String uri = config.getNeo4jUri();
                String user = config.getNeo4jUser();
                String password = config.getNeo4jPassword();
                
                StringBuilder errorMsg = new StringBuilder("Hybrid storage requires Neo4j but driver is not available.\n");
                errorMsg.append("Please configure Neo4j in one of the following ways:\n");
                errorMsg.append("1. Set environment variables:\n");
                errorMsg.append("   - NEO4J_URI=bolt://localhost:7687\n");
                errorMsg.append("   - NEO4J_USER=neo4j\n");
                errorMsg.append("   - NEO4J_PASSWORD=your_password\n");
                errorMsg.append("2. Or add to .env file in project root:\n");
                errorMsg.append("   NEO4J_URI=bolt://localhost:7687\n");
                errorMsg.append("   NEO4J_USER=neo4j\n");
                errorMsg.append("   NEO4J_PASSWORD=your_password\n");
                errorMsg.append("3. Or set in application.properties:\n");
                errorMsg.append("   neo4j.uri=bolt://localhost:7687\n");
                errorMsg.append("   neo4j.user=neo4j\n");
                errorMsg.append("   neo4j.password=your_password\n");
                errorMsg.append("\nCurrent configuration:\n");
                errorMsg.append("   neo4j.uri: ").append(uri != null && !uri.isEmpty() ? uri : "(not set)").append("\n");
                errorMsg.append("   neo4j.user: ").append(user != null && !user.isEmpty() ? user : "(not set)").append("\n");
                errorMsg.append("   neo4j.password: ").append(password != null && !password.isEmpty() ? "***" : "(not set)").append("\n");
                
                logger.error(errorMsg.toString());
                throw new IllegalStateException(errorMsg.toString());
            }
            logger.info("Using Hybrid instance storage (Relational DB + Neo4j)");
            return hybridStorage;
        } else if ("neo4j".equalsIgnoreCase(storageType)) {
            if (neo4jDriver == null) {
                String uri = config.getNeo4jUri();
                String user = config.getNeo4jUser();
                String password = config.getNeo4jPassword();
                
                StringBuilder errorMsg = new StringBuilder("Neo4j storage is configured but Neo4j driver is not available.\n");
                errorMsg.append("Please configure Neo4j in one of the following ways:\n");
                errorMsg.append("1. Set environment variables:\n");
                errorMsg.append("   - NEO4J_URI=bolt://localhost:7687\n");
                errorMsg.append("   - NEO4J_USER=neo4j\n");
                errorMsg.append("   - NEO4J_PASSWORD=your_password\n");
                errorMsg.append("2. Or add to .env file in project root:\n");
                errorMsg.append("   NEO4J_URI=bolt://localhost:7687\n");
                errorMsg.append("   NEO4J_USER=neo4j\n");
                errorMsg.append("   NEO4J_PASSWORD=your_password\n");
                errorMsg.append("3. Or set in application.properties:\n");
                errorMsg.append("   neo4j.uri=bolt://localhost:7687\n");
                errorMsg.append("   neo4j.user=neo4j\n");
                errorMsg.append("   neo4j.password=your_password\n");
                errorMsg.append("\nCurrent configuration:\n");
                errorMsg.append("   neo4j.uri: ").append(uri != null && !uri.isEmpty() ? uri : "(not set)").append("\n");
                errorMsg.append("   neo4j.user: ").append(user != null && !user.isEmpty() ? user : "(not set)").append("\n");
                errorMsg.append("   neo4j.password: ").append(password != null && !password.isEmpty() ? "***" : "(not set)").append("\n");
                
                logger.error(errorMsg.toString());
                throw new IllegalStateException(errorMsg.toString());
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
                String uri = config.getNeo4jUri();
                String user = config.getNeo4jUser();
                String password = config.getNeo4jPassword();
                
                StringBuilder errorMsg = new StringBuilder("Neo4j storage is configured but Neo4j driver is not available.\n");
                errorMsg.append("Please configure Neo4j in one of the following ways:\n");
                errorMsg.append("1. Set environment variables:\n");
                errorMsg.append("   - NEO4J_URI=bolt://localhost:7687\n");
                errorMsg.append("   - NEO4J_USER=neo4j\n");
                errorMsg.append("   - NEO4J_PASSWORD=your_password\n");
                errorMsg.append("2. Or add to .env file in project root:\n");
                errorMsg.append("   NEO4J_URI=bolt://localhost:7687\n");
                errorMsg.append("   NEO4J_USER=neo4j\n");
                errorMsg.append("   NEO4J_PASSWORD=your_password\n");
                errorMsg.append("3. Or set in application.properties:\n");
                errorMsg.append("   neo4j.uri=bolt://localhost:7687\n");
                errorMsg.append("   neo4j.user=neo4j\n");
                errorMsg.append("   neo4j.password=your_password\n");
                errorMsg.append("\nCurrent configuration:\n");
                errorMsg.append("   neo4j.uri: ").append(uri != null && !uri.isEmpty() ? uri : "(not set)").append("\n");
                errorMsg.append("   neo4j.user: ").append(user != null && !user.isEmpty() ? user : "(not set)").append("\n");
                errorMsg.append("   neo4j.password: ").append(password != null && !password.isEmpty() ? "***" : "(not set)").append("\n");
                
                logger.error(errorMsg.toString());
                throw new IllegalStateException(errorMsg.toString());
            }
            logger.info("Using Neo4j link storage");
            return neo4jStorage;
        } else {
            logger.info("Using file link storage");
            return fileStorage;
        }
    }
}
