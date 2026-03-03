package com.mypalantir.service;

import com.mypalantir.meta.DataSourceConfig;
import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchemaServiceTest {

    private SchemaService schemaService;
    private Loader loader;

    @BeforeEach
    void setUp() {
        loader = mock(Loader.class);
        schemaService = new SchemaService(loader);
    }

    @Test
    void listObjectTypes_delegatesToLoader() {
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        when(loader.listObjectTypes()).thenReturn(List.of(ot));

        List<ObjectType> result = schemaService.listObjectTypes();

        assertEquals(1, result.size());
        assertEquals("Vehicle", result.get(0).getName());
        verify(loader).listObjectTypes();
    }

    @Test
    void getObjectType_delegatesToLoader() throws Loader.NotFoundException {
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        when(loader.getObjectType("Vehicle")).thenReturn(ot);

        ObjectType result = schemaService.getObjectType("Vehicle");

        assertEquals("Vehicle", result.getName());
        verify(loader).getObjectType("Vehicle");
    }

    @Test
    void getObjectType_notFound_throws() throws Loader.NotFoundException {
        when(loader.getObjectType("Missing")).thenThrow(new Loader.NotFoundException("not found"));
        assertThrows(Loader.NotFoundException.class, () -> schemaService.getObjectType("Missing"));
    }

    @Test
    void getObjectTypeProperties_delegatesAndReturnsList() throws Exception {
        ObjectType ot = new ObjectType();
        Property p = new Property();
        p.setName("id");
        ot.setProperties(List.of(p));
        when(loader.getObjectType("Vehicle")).thenReturn(ot);

        List<Property> result = schemaService.getObjectTypeProperties("Vehicle");

        assertEquals(1, result.size());
        assertEquals("id", result.get(0).getName());
    }

    @Test
    void getObjectTypeProperties_nullProperties_returnsEmptyList() throws Exception {
        ObjectType ot = new ObjectType();
        ot.setProperties(null);
        when(loader.getObjectType("Empty")).thenReturn(ot);

        List<Property> result = schemaService.getObjectTypeProperties("Empty");

        assertTrue(result.isEmpty());
    }

    @Test
    void listLinkTypes_delegatesToLoader() {
        LinkType lt = new LinkType();
        lt.setName("owns");
        when(loader.listLinkTypes()).thenReturn(List.of(lt));

        List<LinkType> result = schemaService.listLinkTypes();

        assertEquals(1, result.size());
        assertEquals("owns", result.get(0).getName());
    }

    @Test
    void getLinkType_delegatesToLoader() throws Loader.NotFoundException {
        LinkType lt = new LinkType();
        lt.setName("owns");
        when(loader.getLinkType("owns")).thenReturn(lt);

        LinkType result = schemaService.getLinkType("owns");

        assertEquals("owns", result.getName());
    }

    @Test
    void listDataSources_delegatesToLoader() {
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId("ds1");
        when(loader.listDataSources()).thenReturn(List.of(ds));

        List<DataSourceConfig> result = schemaService.listDataSources();

        assertEquals(1, result.size());
        assertEquals("ds1", result.get(0).getId());
    }
}
