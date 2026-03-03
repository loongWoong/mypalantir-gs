package com.mypalantir.meta;

import com.mypalantir.meta.Validator.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {

    private OntologySchema minimalSchema() {
        OntologySchema s = new OntologySchema();
        s.setVersion("1.0");
        s.setNamespace("test");
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        ot.setDisplayName("车辆");
        ot.setDescription("");
        ot.setBaseType(null);
        Property p = new Property();
        p.setName("id");
        p.setDataType("string");
        p.setRequired(true);
        ot.setProperties(List.of(p));
        s.setObjectTypes(List.of(ot));
        s.setLinkTypes(new ArrayList<>());
        return s;
    }

    @Test
    void validate_validSchema_doesNotThrow() {
        Validator v = new Validator(minimalSchema());
        assertDoesNotThrow(v::validate);
    }

    @Test
    void validate_emptyVersion_throws() {
        OntologySchema s = minimalSchema();
        s.setVersion("");
        Validator v = new Validator(s);
        ValidationException ex = assertThrows(ValidationException.class, v::validate);
        assertTrue(ex.getMessage().contains("version"));
    }

    @Test
    void validate_nullVersion_throws() {
        OntologySchema s = minimalSchema();
        s.setVersion(null);
        Validator v = new Validator(s);
        assertThrows(ValidationException.class, v::validate);
    }

    @Test
    void validate_duplicateObjectTypeName_throws() {
        OntologySchema s = minimalSchema();
        ObjectType ot2 = new ObjectType();
        ot2.setName("Vehicle");
        ot2.setProperties(new ArrayList<>());
        s.setObjectTypes(new ArrayList<>(s.getObjectTypes()));
        s.getObjectTypes().add(ot2);
        Validator v = new Validator(s);
        ValidationException ex = assertThrows(ValidationException.class, v::validate);
        assertTrue(ex.getMessage().contains("duplicate"));
    }

    @Test
    void validate_invalidDataType_throws() {
        OntologySchema s = minimalSchema();
        s.getObjectTypes().get(0).getProperties().get(0).setDataType("invalid");
        Validator v = new Validator(s);
        ValidationException ex = assertThrows(ValidationException.class, v::validate);
        assertTrue(ex.getMessage().contains("data_type") || ex.getMessage().contains("invalid"));
    }

    @Test
    void validate_linkTypeInvalidCardinality_throws() {
        OntologySchema s = minimalSchema();
        LinkType lt = new LinkType();
        lt.setName("owns");
        lt.setSourceType("Vehicle");
        lt.setTargetType("Vehicle");
        lt.setCardinality("invalid");
        lt.setDirection("directed");
        lt.setProperties(new ArrayList<>());
        s.setLinkTypes(List.of(lt));
        Validator v = new Validator(s);
        ValidationException ex = assertThrows(ValidationException.class, v::validate);
        assertTrue(ex.getMessage().contains("cardinality"));
    }

    @Test
    void validate_linkTypeSourceNotExist_throws() {
        OntologySchema s = minimalSchema();
        LinkType lt = new LinkType();
        lt.setName("owns");
        lt.setSourceType("NonExistent");
        lt.setTargetType("Vehicle");
        lt.setCardinality("many-to-one");
        lt.setDirection("directed");
        lt.setProperties(new ArrayList<>());
        s.setLinkTypes(List.of(lt));
        Validator v = new Validator(s);
        ValidationException ex = assertThrows(ValidationException.class, v::validate);
        assertTrue(ex.getMessage().contains("source_type") || ex.getMessage().contains("does not exist"));
    }

    @Test
    void validate_baseTypeNotExist_throws() {
        OntologySchema s = minimalSchema();
        ObjectType ot2 = new ObjectType();
        ot2.setName("Car");
        ot2.setBaseType("NonExistent");
        ot2.setProperties(new ArrayList<>());
        s.setObjectTypes(new ArrayList<>(s.getObjectTypes()));
        s.getObjectTypes().add(ot2);
        Validator v = new Validator(s);
        ValidationException ex = assertThrows(ValidationException.class, v::validate);
        assertTrue(ex.getMessage().contains("base_type"));
    }

    @Test
    void validate_stringConstraintMaxLengthNegative_throws() {
        OntologySchema s = minimalSchema();
        Property p = s.getObjectTypes().get(0).getProperties().get(0);
        p.setConstraints(Map.of("max_length", -1));
        Validator v = new Validator(s);
        ValidationException ex = assertThrows(ValidationException.class, v::validate);
        assertTrue(ex.getMessage().contains("max_length"));
    }

    @Test
    void validate_stringConstraintMaxLessThanMin_throws() {
        OntologySchema s = minimalSchema();
        Property p = s.getObjectTypes().get(0).getProperties().get(0);
        p.setConstraints(Map.of("min_length", 10, "max_length", 5));
        Validator v = new Validator(s);
        ValidationException ex = assertThrows(ValidationException.class, v::validate);
        assertTrue(ex.getMessage().contains("max_length") && ex.getMessage().contains("min_length"));
    }
}
