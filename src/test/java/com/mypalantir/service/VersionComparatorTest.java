package com.mypalantir.service;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.OntologySchema;
import com.mypalantir.meta.Property;
import com.mypalantir.service.VersionComparator.ChangeType;
import com.mypalantir.service.VersionComparator.DiffResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VersionComparatorTest {

    private OntologySchema schema(String version, String namespace, List<ObjectType> objectTypes, List<LinkType> linkTypes) {
        OntologySchema s = new OntologySchema();
        s.setVersion(version);
        s.setNamespace(namespace);
        s.setObjectTypes(objectTypes != null ? objectTypes : new ArrayList<>());
        s.setLinkTypes(linkTypes != null ? linkTypes : new ArrayList<>());
        return s;
    }

    private ObjectType objectType(String name, String displayName, List<Property> properties) {
        ObjectType ot = new ObjectType();
        ot.setName(name);
        ot.setDisplayName(displayName);
        ot.setDescription("");
        ot.setBaseType(null);
        ot.setProperties(properties != null ? properties : new ArrayList<>());
        return ot;
    }

    private Property property(String name, String dataType, boolean required) {
        Property p = new Property();
        p.setName(name);
        p.setDataType(dataType);
        p.setRequired(required);
        p.setDescription("");
        p.setDefaultValue(null);
        p.setConstraints(null);
        return p;
    }

    private LinkType linkType(String name, String source, String target) {
        LinkType lt = new LinkType();
        lt.setName(name);
        lt.setDisplayName(name);
        lt.setDescription("");
        lt.setSourceType(source);
        lt.setTargetType(target);
        lt.setCardinality("many-to-one");
        lt.setDirection("forward");
        lt.setProperties(new ArrayList<>());
        return lt;
    }

    @Test
    void compare_sameSchema_hasNoChanges() {
        List<ObjectType> types = List.of(
            objectType("A", "实体A", List.of(property("id", "string", true)))
        );
        OntologySchema v1 = schema("1.0", "ns", types, new ArrayList<>());
        OntologySchema v2 = schema("1.0", "ns", types, new ArrayList<>());

        VersionComparator comparator = new VersionComparator();
        DiffResult result = comparator.compare(v1, v2);

        assertFalse(result.hasChanges());
        assertTrue(result.objectTypeDiffs.isEmpty());
        assertTrue(result.linkTypeDiffs.isEmpty());
        assertTrue(result.metadataChanges.isEmpty());
    }

    @Test
    void compare_differentVersion_reportsMetadataChange() {
        OntologySchema v1 = schema("1.0", "ns", new ArrayList<>(), new ArrayList<>());
        OntologySchema v2 = schema("2.0", "ns", new ArrayList<>(), new ArrayList<>());

        VersionComparator comparator = new VersionComparator();
        DiffResult result = comparator.compare(v1, v2);

        assertTrue(result.hasChanges());
        assertTrue(result.metadataChanges.stream().anyMatch(s -> s.contains("版本")));
    }

    @Test
    void compare_addedObjectType_reportsAdded() {
        OntologySchema v1 = schema("1.0", "ns", new ArrayList<>(), new ArrayList<>());
        List<ObjectType> types = List.of(
            objectType("NewType", "新类型", List.of(property("id", "string", true)))
        );
        OntologySchema v2 = schema("1.0", "ns", types, new ArrayList<>());

        VersionComparator comparator = new VersionComparator();
        DiffResult result = comparator.compare(v1, v2);

        assertEquals(1, result.objectTypeDiffs.size());
        assertEquals("NewType", result.objectTypeDiffs.get(0).name);
        assertEquals(ChangeType.ADDED, result.objectTypeDiffs.get(0).type);
    }

    @Test
    void compare_deletedObjectType_reportsDeleted() {
        List<ObjectType> types = List.of(
            objectType("OldType", "旧类型", List.of(property("id", "string", true)))
        );
        OntologySchema v1 = schema("1.0", "ns", types, new ArrayList<>());
        OntologySchema v2 = schema("1.0", "ns", new ArrayList<>(), new ArrayList<>());

        VersionComparator comparator = new VersionComparator();
        DiffResult result = comparator.compare(v1, v2);

        assertEquals(1, result.objectTypeDiffs.size());
        assertEquals("OldType", result.objectTypeDiffs.get(0).name);
        assertEquals(ChangeType.DELETED, result.objectTypeDiffs.get(0).type);
    }

    @Test
    void compare_addedLinkType_reportsAdded() {
        OntologySchema v1 = schema("1.0", "ns", new ArrayList<>(), new ArrayList<>());
        List<LinkType> links = List.of(linkType("conn", "A", "B"));
        OntologySchema v2 = schema("1.0", "ns", new ArrayList<>(), links);

        VersionComparator comparator = new VersionComparator();
        DiffResult result = comparator.compare(v1, v2);

        assertEquals(1, result.linkTypeDiffs.size());
        assertEquals("conn", result.linkTypeDiffs.get(0).name);
        assertEquals(ChangeType.ADDED, result.linkTypeDiffs.get(0).type);
    }

    @Test
    void compare_nullObjectTypes_doesNotThrow() {
        OntologySchema v1 = schema("1.0", "ns", null, null);
        OntologySchema v2 = schema("1.0", "ns", null, null);

        VersionComparator comparator = new VersionComparator();
        assertDoesNotThrow(() -> comparator.compare(v1, v2));
    }
}
