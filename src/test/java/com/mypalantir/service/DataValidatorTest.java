package com.mypalantir.service;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;
import com.mypalantir.service.DataValidator.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataValidatorTest {

    private DataValidator validator;
    private Loader loader;

    @BeforeEach
    void setUp() {
        loader = mock(Loader.class);
        validator = new DataValidator(loader);
    }

    @Test
    void validateInstanceData_requiredFieldMissing_throws() throws Loader.NotFoundException {
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        Property p = new Property();
        p.setName("id");
        p.setDataType("string");
        p.setRequired(true);
        p.setDefaultValue(null);
        ot.setProperties(List.of(p));
        when(loader.getObjectType("Vehicle")).thenReturn(ot);

        Map<String, Object> data = new HashMap<>();
        data.put("plate", "京A");

        ValidationException ex = assertThrows(ValidationException.class,
            () -> validator.validateInstanceData("Vehicle", data));
        assertTrue(ex.getMessage().contains("required") && ex.getMessage().contains("id"));
    }

    @Test
    void validateInstanceData_requiredFieldWithDefault_fillsDefault() throws Exception {
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        Property p = new Property();
        p.setName("status");
        p.setDataType("string");
        p.setRequired(true);
        p.setDefaultValue("active");
        ot.setProperties(List.of(p));
        when(loader.getObjectType("Vehicle")).thenReturn(ot);

        Map<String, Object> data = new HashMap<>();
        data.put("id", "1");
        validator.validateInstanceData("Vehicle", data);
        assertEquals("active", data.get("status"));
    }

    @Test
    void validateInstanceData_wrongType_throws() throws Loader.NotFoundException {
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        Property p = new Property();
        p.setName("count");
        p.setDataType("int");
        p.setRequired(false);
        ot.setProperties(List.of(p));
        when(loader.getObjectType("Vehicle")).thenReturn(ot);

        Map<String, Object> data = new HashMap<>();
        data.put("count", "not_a_number");

        ValidationException ex = assertThrows(ValidationException.class,
            () -> validator.validateInstanceData("Vehicle", data));
        assertTrue(ex.getMessage().toLowerCase().contains("int"));
    }

    @Test
    void validateInstanceData_validString_passes() throws Exception {
        ObjectType ot = new ObjectType();
        ot.setName("Vehicle");
        Property p = new Property();
        p.setName("id");
        p.setDataType("string");
        p.setRequired(true);
        ot.setProperties(List.of(p));
        when(loader.getObjectType("Vehicle")).thenReturn(ot);

        Map<String, Object> data = new HashMap<>();
        data.put("id", "v1");
        validator.validateInstanceData("Vehicle", data);
    }

    @Test
    void validateLinkData_missingSourceId_throws() throws Loader.NotFoundException {
        LinkType lt = new LinkType();
        lt.setName("owns");
        lt.setProperties(List.of());
        when(loader.getLinkType("owns")).thenReturn(lt);

        ValidationException ex = assertThrows(ValidationException.class,
            () -> validator.validateLinkData("owns", null, "t1", new HashMap<>()));
        assertTrue(ex.getMessage().contains("source_id"));
    }

    @Test
    void validateLinkData_missingTargetId_throws() throws Loader.NotFoundException {
        LinkType lt = new LinkType();
        lt.setName("owns");
        lt.setProperties(List.of());
        when(loader.getLinkType("owns")).thenReturn(lt);

        ValidationException ex = assertThrows(ValidationException.class,
            () -> validator.validateLinkData("owns", "s1", "", new HashMap<>()));
        assertTrue(ex.getMessage().contains("target_id"));
    }

    @Test
    void validateLinkData_valid_passes() throws Exception {
        LinkType lt = new LinkType();
        lt.setName("owns");
        lt.setProperties(List.of());
        when(loader.getLinkType("owns")).thenReturn(lt);

        validator.validateLinkData("owns", "s1", "t1", new HashMap<>());
    }
}
