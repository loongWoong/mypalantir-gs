package com.mypalantir.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataSourceConfigTest {

    @Test
    void buildJdbcUrl_whenJdbcUrlSet_returnsIt() {
        DataSourceConfig config = new DataSourceConfig();
        config.setJdbcUrl("jdbc:custom://host/db");
        config.setType("mysql");
        config.setHost("localhost");
        config.setPort(3306);
        config.setDatabase("mydb");
        assertEquals("jdbc:custom://host/db", config.buildJdbcUrl());
    }

    @Test
    void buildJdbcUrl_mysql_buildsCorrectUrl() {
        DataSourceConfig config = new DataSourceConfig();
        config.setType("mysql");
        config.setHost("localhost");
        config.setPort(3306);
        config.setDatabase("testdb");
        String url = config.buildJdbcUrl();
        assertTrue(url.startsWith("jdbc:mysql://"));
        assertTrue(url.contains("localhost:3306/testdb"));
    }

    @Test
    void buildJdbcUrl_doris_buildsCorrectUrl() {
        DataSourceConfig config = new DataSourceConfig();
        config.setType("doris");
        config.setHost("doris-host");
        config.setPort(9030);
        config.setDatabase("mydb");
        String url = config.buildJdbcUrl();
        assertTrue(url.startsWith("jdbc:mysql://"));
        assertTrue(url.contains("doris-host:9030/mydb"));
    }

    @Test
    void buildJdbcUrl_postgresql_buildsCorrectUrl() {
        DataSourceConfig config = new DataSourceConfig();
        config.setType("postgresql");
        config.setHost("pg.example.com");
        config.setPort(5432);
        config.setDatabase("mydb");
        String url = config.buildJdbcUrl();
        assertTrue(url.startsWith("jdbc:postgresql://"));
        assertTrue(url.contains("pg.example.com:5432/mydb"));
    }

    @Test
    void buildJdbcUrl_postgres_alias_buildsCorrectUrl() {
        DataSourceConfig config = new DataSourceConfig();
        config.setType("postgres");
        config.setHost("localhost");
        config.setPort(5432);
        config.setDatabase("db");
        assertTrue(config.buildJdbcUrl().startsWith("jdbc:postgresql://"));
    }

    @Test
    void buildJdbcUrl_h2File_buildsFileUrl() {
        DataSourceConfig config = new DataSourceConfig();
        config.setType("h2");
        config.setHost("file");
        config.setDatabase("mydb");
        config.setPort(0);
        String url = config.buildJdbcUrl();
        assertTrue(url.contains("jdbc:h2:file:"));
        assertTrue(url.contains("mydb"));
    }

    @Test
    void buildJdbcUrl_h2Mem_buildsMemUrl() {
        DataSourceConfig config = new DataSourceConfig();
        config.setType("h2");
        config.setHost("mem");
        config.setDatabase("test");
        String url = config.buildJdbcUrl();
        assertTrue(url.contains("jdbc:h2:mem:"));
    }

    @Test
    void buildJdbcUrl_unsupportedType_throws() {
        DataSourceConfig config = new DataSourceConfig();
        config.setType("unknown_db");
        config.setHost("localhost");
        config.setPort(9999);
        config.setDatabase("db");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::buildJdbcUrl);
        assertTrue(ex.getMessage().contains("Unsupported") || ex.getMessage().contains("unknown_db"));
    }
}
