package com.mypalantir.controller;

import com.mypalantir.meta.Loader;
import com.mypalantir.service.DataValidator;
import com.mypalantir.service.LinkService;
import com.mypalantir.service.LinkSyncService;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {
    private final LinkService linkService;

    @Autowired
    private LinkSyncService linkSyncService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping("/{linkType}")
    public ResponseEntity<ApiResponse<Map<String, String>>> createLink(
            @PathVariable String linkType,
            @RequestBody Map<String, Object> request) {
        try {
            String sourceID = (String) request.get("source_id");
            String targetID = (String) request.get("target_id");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) request.getOrDefault("properties", new HashMap<>());

            if (sourceID == null || targetID == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "source_id and target_id are required"));
            }

            String id = linkService.createLink(linkType, sourceID, targetID, properties);
            Map<String, String> result = new HashMap<>();
            result.put("id", id);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Loader.NotFoundException | DataValidator.ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to create link: " + e.getMessage()));
        }
    }

    @GetMapping("/{linkType}/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLink(
            @PathVariable String linkType,
            @PathVariable String id) {
        try {
            Map<String, Object> link = linkService.getLink(linkType, id);
            return ResponseEntity.ok(ApiResponse.success(link));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @PutMapping("/{linkType}/{id}")
    public ResponseEntity<ApiResponse<Void>> updateLink(
            @PathVariable String linkType,
            @PathVariable String id,
            @RequestBody Map<String, Object> properties) {
        try {
            linkService.updateLink(linkType, id, properties);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Loader.NotFoundException | DataValidator.ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to update link: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{linkType}/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLink(
            @PathVariable String linkType,
            @PathVariable String id) {
        try {
            linkService.deleteLink(linkType, id);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @GetMapping("/{linkType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listLinks(
            @PathVariable String linkType,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            InstanceStorage.ListResult result = linkService.listLinks(linkType, offset, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("items", result.getItems());
            response.put("total", result.getTotal());
            response.put("offset", offset);
            response.put("limit", limit);

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to list links: " + e.getMessage()));
        }
    }

    @PostMapping("/{linkType}/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncLinks(
            @PathVariable String linkType) {
        try {
            LinkSyncService.SyncResult result = linkSyncService.syncLinksByType(linkType);
            return ResponseEntity.ok(ApiResponse.success(result.toMap()));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to sync links: " + e.getMessage()));
        }
    }

}

