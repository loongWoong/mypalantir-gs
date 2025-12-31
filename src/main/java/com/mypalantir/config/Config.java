package com.mypalantir.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties
@DependsOn("envConfig")
public class Config {
    private int serverPort = 8080;
    private String serverMode = "debug";

    @Value("${schema.file.path:./ontology/schema.yaml}")
    private String schemaFilePath;
    
    @Value("${schema.system.file.path:./ontology/schema-system.yaml}")
    private String systemSchemaFilePath;

    @Value("${data.root.path:./data}")
    private String dataRootPath;

    private String logLevel = "info";
    private String logFile = "./logs/app.log";

    @Value("${web.static.path:./web/dist}")
    private String webStaticPath;

    @Value("${db.host:localhost}")
    private String dbHost;

    @Value("${db.port:3306}")
    private int dbPort;

    @Value("${db.name:}")
    private String dbName;

    @Value("${db.user:}")
    private String dbUser;

    @Value("${db.password:}")
    private String dbPassword;

    @Value("${db.type:mysql}")
    private String dbType;

    @Value("${storage.type:file}")
    private String storageType;

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${neo4j.user:neo4j}")
    private String neo4jUser;

    @Value("${neo4j.password:}")
    private String neo4jPassword;

    @PostConstruct
    public void init() {
        // 从 .env 文件或环境变量中读取 Neo4j 配置，覆盖默认值
        String envUri = EnvConfig.get("NEO4J_URI");
        if (envUri != null && !envUri.isEmpty()) {
            this.neo4jUri = envUri;
        }
        
        String envUser = EnvConfig.get("NEO4J_USER");
        if (envUser != null && !envUser.isEmpty()) {
            this.neo4jUser = envUser;
        }
        
        String envPassword = EnvConfig.get("NEO4J_PASSWORD");
        if (envPassword != null && !envPassword.isEmpty()) {
            this.neo4jPassword = envPassword;
        }
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getServerMode() {
        return serverMode;
    }

    public String getSchemaFilePath() {
        return schemaFilePath;
    }

    public String getSystemSchemaFilePath() {
        return systemSchemaFilePath;
    }

    public String getDataRootPath() {
        return dataRootPath;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getLogFile() {
        return logFile;
    }

    public String getWebStaticPath() {
        return webStaticPath;
    }

    public String getDbHost() {
        return dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String getDbType() {
        return dbType;
    }

    public String getStorageType() {
        return storageType;
    }

    public String getNeo4jUri() {
        return neo4jUri;
    }

    public String getNeo4jUser() {
        return neo4jUser;
    }

    public String getNeo4jPassword() {
        return neo4jPassword;
    }
}

