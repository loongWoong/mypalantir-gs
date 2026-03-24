package com.mypalantir.repository;

import com.mypalantir.config.Config;
import com.falkordb.Graph;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 存储工厂
 * 根据配置选择文件存储、Neo4j 存储或 FalkorDB 存储
 */
@Configuration
public class StorageFactory {
    private static final Logger logger = LoggerFactory.getLogger(StorageFactory.class);

    @Autowired
    private Config config;

    @Autowired(required = false)
    private Driver neo4jDriver;

    @Autowired(required = false)
    private Graph falkordbGraph;

    @Bean
    @ConditionalOnProperty(name = "storage.graph.type", havingValue = "neo4j", matchIfMissing = true)
    public Neo4jInstanceStorage neo4jInstanceStorage() {
        return new Neo4jInstanceStorage();
    }

    @Bean
    @ConditionalOnProperty(name = "storage.graph.type", havingValue = "neo4j", matchIfMissing = true)
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

    /** 图实例存储：Neo4j 或 FalkorDB，供 HybridInstanceStorage 和 DatabaseMetadataService 使用 */
    @Bean("graphInstanceStorage")
    @ConditionalOnBean(Neo4jInstanceStorage.class)
    public IInstanceStorage graphInstanceStorageNeo4j(Neo4jInstanceStorage neo4j) {
        return neo4j;
    }

    @Bean("graphInstanceStorage")
    @ConditionalOnBean(FalkorDBInstanceStorage.class)
    public IInstanceStorage graphInstanceStorageFalkorDB(FalkorDBInstanceStorage falkordb) {
        return falkordb;
    }

    /** 图关系存储：Neo4j 或 FalkorDB */
    @Bean("graphLinkStorage")
    @ConditionalOnBean(Neo4jLinkStorage.class)
    public ILinkStorage graphLinkStorageNeo4j(Neo4jLinkStorage neo4j) {
        return neo4j;
    }

    @Bean("graphLinkStorage")
    @ConditionalOnBean(FalkorDBLinkStorage.class)
    public ILinkStorage graphLinkStorageFalkorDB(FalkorDBLinkStorage falkordb) {
        return falkordb;
    }

    @Bean
    @Primary
    public IInstanceStorage instanceStorage(PathManager pathManager, InstanceStorage fileStorage,
                                           RelationalInstanceStorage relationalStorage,
                                           HybridInstanceStorage hybridStorage,
                                           @org.springframework.beans.factory.annotation.Autowired(required = false)
                                           @org.springframework.beans.factory.annotation.Qualifier("graphInstanceStorage")
                                           IInstanceStorage graphStorage) {
        String storageType = config.getStorageType();
        String graphType = config.getStorageGraphType();
        logger.info("Initializing instance storage with type: {}, graph: {}", storageType, graphType);

        if ("hybrid".equalsIgnoreCase(storageType)) {
            if (graphStorage == null) {
                throw new IllegalStateException(buildGraphStorageError("Hybrid storage requires graph database (Neo4j or FalkorDB) but none is configured. " +
                    "Set storage.graph.type=neo4j (default) or storage.graph.type=falkordb, and configure the corresponding connection."));
            }
            logger.info("Using Hybrid instance storage (Relational DB + " + graphType + ")");
            return hybridStorage;
        } else if ("neo4j".equalsIgnoreCase(storageType)) {
            if (graphStorage == null) {
                throw new IllegalStateException(buildGraphStorageError("Neo4j storage is configured but graph driver is not available."));
            }
            if ("falkordb".equalsIgnoreCase(graphType)) {
                logger.info("Using FalkorDB instance storage (storage.type=neo4j means graph-only mode)");
            } else {
                logger.info("Using Neo4j instance storage");
            }
            return graphStorage;
        } else {
            logger.info("Using file instance storage");
            return fileStorage;
        }
    }

    @Bean
    @Primary
    public ILinkStorage linkStorage(PathManager pathManager, LinkStorage fileStorage,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   @org.springframework.beans.factory.annotation.Qualifier("graphLinkStorage")
                                   ILinkStorage graphLink) {
        String storageType = config.getStorageType();
        logger.info("Initializing link storage with type: {}", storageType);

        if ("neo4j".equalsIgnoreCase(storageType) || "hybrid".equalsIgnoreCase(storageType)) {
            if (graphLink == null) {
                throw new IllegalStateException(buildGraphStorageError("Storage type '" + storageType + "' requires graph database (Neo4j or FalkorDB) but none is configured."));
            }
            logger.info("Using " + config.getStorageGraphType() + " link storage");
            return graphLink;
        } else {
            logger.info("Using file link storage");
            return fileStorage;
        }
    }

    private String buildGraphStorageError(String prefix) {
        return prefix + "\nFor Neo4j: storage.graph.type=neo4j, set NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD.\n" +
            "For FalkorDB: storage.graph.type=falkordb, set FALKORDB_HOST (default localhost), FALKORDB_PORT (default 6379).";
    }
}
