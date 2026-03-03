package com.mypalantir.query;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryParserTest {

    private final QueryParser parser = new QueryParser();

    @Test
    void parseMap_fromAndSelect() {
        Map<String, Object> map = new HashMap<>();
        map.put("from", "Vehicle");
        map.put("select", List.of("id", "plate"));
        map.put("limit", 10);

        OntologyQuery query = parser.parseMap(map);

        assertEquals("Vehicle", query.getFrom());
        assertEquals(List.of("id", "plate"), query.getSelect());
        assertEquals(10, query.getLimit());
    }

    @Test
    void parseMap_objectPreferredOverFrom() {
        Map<String, Object> map = new HashMap<>();
        map.put("from", "Old");
        map.put("object", "New");

        OntologyQuery query = parser.parseMap(map);

        assertEquals("New", query.getFrom());
    }

    @Test
    void parseMap_linksParsed() {
        Map<String, Object> map = new HashMap<>();
        map.put("from", "Vehicle");
        map.put("select", List.of("id"));
        Map<String, Object> link = new HashMap<>();
        link.put("name", "owns");
        link.put("select", List.of("name"));
        map.put("links", List.of(link));

        OntologyQuery query = parser.parseMap(map);

        assertNotNull(query.getLinks());
        assertEquals(1, query.getLinks().size());
        assertEquals("owns", query.getLinks().get(0).getName());
        assertEquals(List.of("name"), query.getLinks().get(0).getSelect());
    }

    @Test
    void parseMap_limitAndOffset() {
        Map<String, Object> map = new HashMap<>();
        map.put("from", "A");
        map.put("limit", 5);
        map.put("offset", 20);

        OntologyQuery query = parser.parseMap(map);

        assertEquals(5, query.getLimit());
        assertEquals(20, query.getOffset());
    }

    @Test
    void parseJson_returnsOntologyQuery() throws IOException {
        String json = "{\"from\":\"Vehicle\",\"select\":[\"id\"],\"limit\":1}";
        OntologyQuery query = parser.parseJson(json);

        assertEquals("Vehicle", query.getFrom());
        assertEquals(List.of("id"), query.getSelect());
        assertEquals(1, query.getLimit());
    }

    @Test
    void parseYaml_returnsOntologyQuery() throws IOException {
        String yaml = "from: Vehicle\nselect:\n  - id\nlimit: 2";
        OntologyQuery query = parser.parseYaml(yaml);

        assertEquals("Vehicle", query.getFrom());
        assertEquals(List.of("id"), query.getSelect());
        assertEquals(2, query.getLimit());
    }
}
