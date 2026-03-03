package com.mypalantir.meta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据源映射单元测试（JUnit 5）
 */
class DataSourceMappingTest {

    private String getResourcePath(String name) throws URISyntaxException {
        return Paths.get(getClass().getResource(name).toURI()).toString();
    }

    @Test
    void schemaWithDataSources_parsesMappings() throws IOException, URISyntaxException {
        String path = getResourcePath("/ontology/schema-mini.yaml");
        Parser parser = new Parser(path);
        OntologySchema schema = parser.parse();

        assertNotNull(schema.getDataSources());
        assertEquals(1, schema.getDataSources().size());
        DataSourceConfig ds = schema.getDataSources().get(0);
        assertEquals("ds1", ds.getId());
        assertEquals("jdbc", ds.getType());
        assertEquals("localhost", ds.getHost());
        assertEquals(3306, ds.getPort());
        assertEquals("testdb", ds.getDatabase());
    }

    @Test
    void objectTypeWithDataSource_hasMapping() throws IOException, URISyntaxException {
        String path = getResourcePath("/ontology/schema-mini.yaml");
        Parser parser = new Parser(path);
        OntologySchema schema = parser.parse();

        ObjectType vehicle = schema.getObjectTypes().stream()
            .filter(ot -> "Vehicle".equals(ot.getName()))
            .findFirst()
            .orElseThrow();
        assertTrue(vehicle.hasDataSource());
        DataSourceMapping mapping = vehicle.getDataSource();
        assertEquals("ds1", mapping.getConnectionId());
        assertEquals("vehicles", mapping.getTable());
        assertEquals("id", mapping.getIdColumn());
        assertNotNull(mapping.getFieldMapping());
        assertEquals("id", mapping.getFieldMapping().get("id"));
        assertEquals("plate_no", mapping.getFieldMapping().get("plate"));
    }
}
