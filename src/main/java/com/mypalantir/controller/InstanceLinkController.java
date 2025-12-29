package com.mypalantir.controller;

import com.mypalantir.meta.Loader;
import com.mypalantir.service.LinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/instances")
public class InstanceLinkController {
    private final LinkService linkService;

    public InstanceLinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping("/{objectType}/{id}/links/{linkType}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getInstanceLinks(
            @PathVariable String objectType,
            @PathVariable String id,
            @PathVariable String linkType) {
        try {
            List<Map<String, Object>> links = linkService.getLinksBySource(linkType, id);
            return ResponseEntity.ok(ApiResponse.success(links));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get instance links: " + e.getMessage()));
        }
    }

    @GetMapping("/{objectType}/{id}/connected/{linkType}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getConnectedInstances(
            @PathVariable String objectType,
            @PathVariable String id,
            @PathVariable String linkType,
            @RequestParam(defaultValue = "outgoing") String direction) {
        try {
            List<Map<String, Object>> instances = linkService.getConnectedInstances(objectType, linkType, id, direction);
            return ResponseEntity.ok(ApiResponse.success(instances));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to get connected instances: " + e.getMessage()));
        }
    }
}

