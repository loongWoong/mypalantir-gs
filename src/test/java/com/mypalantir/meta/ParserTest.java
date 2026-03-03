package com.mypalantir.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser 单元测试（JUnit 5）
 */
class ParserTest {

    private String getResourcePath(String name) throws URISyntaxException {
        return Paths.get(getClass().getResource(name).toURI()).toString();
    }

    @Test
    void parse_returnsSchemaFromExistingFile() throws IOException, URISyntaxException {
        String path = getResourcePath("/ontology/schema-mini.yaml");
        Parser parser = new Parser(path);
        OntologySchema schema = parser.parse();

        assertNotNull(schema);
        assertEquals("1.0", schema.getVersion());
        assertEquals("test", schema.getNamespace());
        assertNotNull(schema.getObjectTypes());
        assertEquals(2, schema.getObjectTypes().size());
        assertTrue(schema.getObjectTypes().stream().anyMatch(ot -> "Vehicle".equals(ot.getName())));
        assertTrue(schema.getObjectTypes().stream().anyMatch(ot -> "Person".equals(ot.getName())));
        assertNotNull(schema.getLinkTypes());
        assertEquals(1, schema.getLinkTypes().size());
        assertEquals("owns", schema.getLinkTypes().get(0).getName());
    }

    @Test
    void parse_throwsWhenFileNotFound() {
        Parser parser = new Parser("/nonexistent/schema.yaml");
        assertThrows(IOException.class, parser::parse);
    }

    @Test
    void parse_objectTypeHasPropertiesAndDataSource(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("schema.yaml");
        Files.writeString(yaml, ""
            + "version: \"1.0\"\n"
            + "namespace: ut\n"
            + "object_types:\n"
            + "  - name: A\n"
            + "    display_name: A\n"
            + "    description: \"\"\n"
            + "    base_type: null\n"
            + "    properties:\n"
            + "      - name: id\n"
            + "        data_type: string\n"
            + "        required: true\n"
            + "    data_source:\n"
            + "      connection_id: c1\n"
            + "      table: t1\n"
            + "      id_column: id\n"
            + "      field_mapping: { id: id }\n"
            + "link_types: []\n");

        Parser parser = new Parser(yaml.toString());
        OntologySchema schema = parser.parse();

        ObjectType ot = schema.getObjectTypes().get(0);
        assertEquals("A", ot.getName());
        assertTrue(ot.hasDataSource());
        assertEquals("c1", ot.getDataSource().getConnectionId());
        assertEquals("t1", ot.getDataSource().getTable());
        assertEquals(1, ot.getProperties().size());
        assertEquals("id", ot.getProperties().get(0).getName());
    }
}
