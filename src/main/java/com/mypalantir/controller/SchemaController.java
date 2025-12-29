package com.mypalantir.controller;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;
import com.mypalantir.service.SchemaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schema")
public class SchemaController {
    private final SchemaService schemaService;

    public SchemaController(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping("/object-types")
    public ResponseEntity<ApiResponse<List<ObjectType>>> listObjectTypes() {
        List<ObjectType> objectTypes = schemaService.listObjectTypes();
        return ResponseEntity.ok(ApiResponse.success(objectTypes));
    }

    @GetMapping("/object-types/{name}")
    public ResponseEntity<ApiResponse<ObjectType>> getObjectType(@PathVariable String name) {
        try {
            ObjectType objectType = schemaService.getObjectType(name);
            return ResponseEntity.ok(ApiResponse.success(objectType));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @GetMapping("/object-types/{name}/properties")
    public ResponseEntity<ApiResponse<List<Property>>> getObjectTypeProperties(@PathVariable String name) {
        try {
            List<Property> properties = schemaService.getObjectTypeProperties(name);
            return ResponseEntity.ok(ApiResponse.success(properties));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @GetMapping("/object-types/{name}/outgoing-links")
    public ResponseEntity<ApiResponse<List<LinkType>>> getOutgoingLinks(@PathVariable String name) {
        List<LinkType> links = schemaService.getOutgoingLinks(name);
        return ResponseEntity.ok(ApiResponse.success(links));
    }

    @GetMapping("/object-types/{name}/incoming-links")
    public ResponseEntity<ApiResponse<List<LinkType>>> getIncomingLinks(@PathVariable String name) {
        List<LinkType> links = schemaService.getIncomingLinks(name);
        return ResponseEntity.ok(ApiResponse.success(links));
    }

    @GetMapping("/link-types")
    public ResponseEntity<ApiResponse<List<LinkType>>> listLinkTypes() {
        List<LinkType> linkTypes = schemaService.listLinkTypes();
        return ResponseEntity.ok(ApiResponse.success(linkTypes));
    }

    @GetMapping("/link-types/{name}")
    public ResponseEntity<ApiResponse<LinkType>> getLinkType(@PathVariable String name) {
        try {
            LinkType linkType = schemaService.getLinkType(name);
            return ResponseEntity.ok(ApiResponse.success(linkType));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
        }
    }
}

