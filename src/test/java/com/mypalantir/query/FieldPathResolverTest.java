package com.mypalantir.query;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FieldPathResolverTest {

    private FieldPathResolver resolver;
    private Loader loader;

    @BeforeEach
    void setUp() {
        loader = mock(Loader.class);
        resolver = new FieldPathResolver(loader);
    }

    private ObjectType objectType(String name, String... propertyNames) {
        ObjectType ot = new ObjectType();
        ot.setName(name);
        List<Property> props = new ArrayList<>();
        for (String pn : propertyNames) {
            Property p = new Property();
            p.setName(pn);
            p.setDataType("string");
            p.setRequired(false);
            props.add(p);
        }
        ot.setProperties(props);
        return ot;
    }

    @Test
    void resolve_emptyPath_throws() {
        ObjectType root = objectType("Vehicle", "id");
        assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve("", root, null));
        assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve(null, root, null));
    }

    @Test
    void resolve_simpleField_noDot_returnsRootProperty() throws Exception {
        ObjectType root = objectType("Vehicle", "id", "plate");
        FieldPathResolver.FieldPath result = resolver.resolve("plate", root, null);

        assertNotNull(result);
        assertEquals(root, result.getObjectType());
        assertEquals("plate", result.getPropertyName());
        assertNull(result.getLinkType());
        assertFalse(result.isFromLinkedObject());
    }

    @Test
    void resolve_propertyNotFound_throws() {
        ObjectType root = objectType("Vehicle", "id");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve("nonexistent", root, null));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void resolve_pathWithDot_linkNotInQuery_throws() {
        ObjectType root = objectType("Vehicle", "id");
        List<OntologyQuery.LinkQuery> links = new ArrayList<>();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve("owns.name", root, links));
        assertTrue(ex.getMessage().contains("Link") && ex.getMessage().contains("not found"));
    }
}
