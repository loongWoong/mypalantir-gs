package com.mypalantir.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OntologyQueryTest {

    @Test
    void getFrom_returnsObjectWhenSet() {
        OntologyQuery q = new OntologyQuery();
        q.setFrom("Old");
        q.setObject("New");
        assertEquals("New", q.getFrom());
        assertEquals("New", q.getObject());
    }

    @Test
    void getFrom_returnsFromWhenObjectNull() {
        OntologyQuery q = new OntologyQuery();
        q.setFrom("Vehicle");
        assertEquals("Vehicle", q.getFrom());
        assertEquals("Vehicle", q.getObject());
    }

    @Test
    void getFrom_bothNull_returnsNull() {
        OntologyQuery q = new OntologyQuery();
        assertNull(q.getFrom());
    }

    @Test
    void selectLimitOffset_setAndGet() {
        OntologyQuery q = new OntologyQuery();
        q.setSelect(List.of("id", "name"));
        q.setLimit(10);
        q.setOffset(20);
        assertEquals(List.of("id", "name"), q.getSelect());
        assertEquals(10, q.getLimit());
        assertEquals(20, q.getOffset());
    }

    @Test
    void orderBy_setAndGet() {
        OntologyQuery q = new OntologyQuery();
        OntologyQuery.OrderBy ob = new OntologyQuery.OrderBy("name", "DESC");
        q.setOrderBy(List.of(ob));
        assertEquals(1, q.getOrderBy().size());
        assertEquals("name", q.getOrderBy().get(0).getField());
        assertEquals("DESC", q.getOrderBy().get(0).getDirection());
    }

    @Test
    void linkQuery_setAndGet() {
        OntologyQuery.LinkQuery lq = new OntologyQuery.LinkQuery();
        lq.setName("owns");
        lq.setSelect(List.of("name"));
        assertEquals("owns", lq.getName());
        assertEquals(List.of("name"), lq.getSelect());
    }

    @Test
    void whereAndFilter_setAndGet() {
        OntologyQuery q = new OntologyQuery();
        q.setWhere(Map.of("status", "active"));
        q.setFilter(List.of(List.of("=", "status", "active")));
        assertEquals(Map.of("status", "active"), q.getWhere());
        assertEquals(1, q.getFilter().size());
    }
}
