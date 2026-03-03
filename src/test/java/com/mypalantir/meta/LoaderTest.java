package com.mypalantir.meta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loader 加载 schema 文件后 getObjectType、listObjectTypes、getLinkType、listLinkTypes、
 * getDataSourceById、listDataSources、getOutgoingLinks、getIncomingLinks 等行为的单元测试。
 * 使用 src/test/resources/ontology/schema-mini.yaml。
 */
class LoaderTest {

    private String schemaPath;

    @BeforeEach
    void setUp() throws Exception {
        URI uri = getClass().getClassLoader().getResource("ontology/schema-mini.yaml").toURI();
        Path path = Paths.get(uri);
        schemaPath = path.toAbsolutePath().toString();
    }

    @Test
    void load_validSchema_succeeds() throws Exception {
        Loader loader = new Loader(schemaPath);
        assertDoesNotThrow(loader::load);
        assertNotNull(loader.getSchema());
    }

    @Test
    void getObjectType_afterLoad_returnsType() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        ObjectType vehicle = loader.getObjectType("Vehicle");
        assertNotNull(vehicle);
        assertEquals("Vehicle", vehicle.getName());
        assertEquals("车辆", vehicle.getDisplayName());

        ObjectType person = loader.getObjectType("Person");
        assertNotNull(person);
        assertEquals("Person", person.getName());
    }

    @Test
    void getObjectType_notFound_throwsNotFoundException() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        assertThrows(Loader.NotFoundException.class, () -> loader.getObjectType("NotExist"));
    }

    @Test
    void listObjectTypes_afterLoad_returnsAll() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        List<ObjectType> list = loader.listObjectTypes();
        assertNotNull(list);
        assertTrue(list.size() >= 2);
        assertTrue(list.stream().anyMatch(ot -> "Vehicle".equals(ot.getName())));
        assertTrue(list.stream().anyMatch(ot -> "Person".equals(ot.getName())));
    }

    @Test
    void getLinkType_afterLoad_returnsType() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        LinkType owns = loader.getLinkType("owns");
        assertNotNull(owns);
        assertEquals("owns", owns.getName());
        assertEquals("Vehicle", owns.getSourceType());
        assertEquals("Person", owns.getTargetType());
    }

    @Test
    void getLinkType_notFound_throwsNotFoundException() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        assertThrows(Loader.NotFoundException.class, () -> loader.getLinkType("NotExist"));
    }

    @Test
    void listLinkTypes_afterLoad_returnsAll() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        List<LinkType> list = loader.listLinkTypes();
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(lt -> "owns".equals(lt.getName())));
    }

    @Test
    void getOutgoingLinks_forVehicle_returnsOwns() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        List<LinkType> outgoing = loader.getOutgoingLinks("Vehicle");
        assertNotNull(outgoing);
        assertTrue(outgoing.stream().anyMatch(lt -> "owns".equals(lt.getName())));
    }

    @Test
    void getIncomingLinks_forPerson_returnsOwns() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        List<LinkType> incoming = loader.getIncomingLinks("Person");
        assertNotNull(incoming);
        assertTrue(incoming.stream().anyMatch(lt -> "owns".equals(lt.getName())));
    }

    @Test
    void listDataSources_afterLoad_returnsAll() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        List<DataSourceConfig> list = loader.listDataSources();
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(ds -> "ds1".equals(ds.getId())));
    }

    @Test
    void getDataSourceById_afterLoad_returnsConfig() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        DataSourceConfig ds = loader.getDataSourceById("ds1");
        assertNotNull(ds);
        assertEquals("ds1", ds.getId());
    }

    @Test
    void getDataSourceById_notFound_throwsNotFoundException() throws Exception {
        Loader loader = new Loader(schemaPath);
        loader.load();

        assertThrows(Loader.NotFoundException.class, () -> loader.getDataSourceById("notExist"));
    }

    @Test
    void listObjectTypes_beforeLoad_returnsEmpty() {
        Loader loader = new Loader(schemaPath);
        List<ObjectType> list = loader.listObjectTypes();
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void getObjectType_beforeLoad_throwsNotFoundException() {
        Loader loader = new Loader(schemaPath);
        assertThrows(Loader.NotFoundException.class, () -> loader.getObjectType("Vehicle"));
    }
}
