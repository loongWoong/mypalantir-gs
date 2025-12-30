package com.mypalantir.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties
public class Config {
    private int serverPort = 8080;
    private String serverMode = "debug";
    
    @Value("${schema.file.path:./ontology/schema.yaml}")
    private String schemaFilePath;
    
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

    public int getServerPort() {
        return serverPort;
    }

    public String getServerMode() {
        return serverMode;
    }

    public String getSchemaFilePath() {
        return schemaFilePath;
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
}

