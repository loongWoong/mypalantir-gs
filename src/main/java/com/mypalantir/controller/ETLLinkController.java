package com.mypalantir.controller;

import com.mypalantir.meta.Loader;
import com.mypalantir.service.ETLLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * ETL关系API控制器
 * 供ETL系统调用，用于批量操作Links关系
 */
@RestController
@RequestMapping("/api/v1/etl/links")
public class ETLLinkController {
    @Autowired
    private ETLLinkService etlLinkService;

    /**
     * 获取指定关系类型的所有Links关系
     * GET /api/v1/etl/links/{linkType}?sourceType=xxx&targetType=xxx&limit=1000&offset=0
     */
    @GetMapping("/{linkType}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLinks(
            @PathVariable String linkType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        try {
            List<Map<String, Object>> links = etlLinkService.getLinks(linkType, sourceType, targetType, limit, offset);
            return ResponseEntity.ok(ApiResponse.success(links));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to get links: " + e.getMessage()));
        }
    }

    /**
     * 批量创建Links关系
     * POST /api/v1/etl/links/{linkType}/batch
     * Body: [{"sourceId": "xxx", "targetId": "yyy", "properties": {...}}, ...]
     */
    @PostMapping("/{linkType}/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createLinksBatch(
            @PathVariable String linkType,
            @RequestBody List<ETLLinkService.LinkCreateRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(400, "Requests list cannot be empty"));
            }
            
            ETLLinkService.BatchCreateResult result = etlLinkService.createLinksBatch(linkType, requests);
            return ResponseEntity.ok(ApiResponse.success(result.toMap()));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to create links: " + e.getMessage()));
        }
    }

    /**
     * 根据属性匹配查找Links关系
     * POST /api/v1/etl/links/{linkType}/match
     * Body: {
     *   "sourceType": "xxx",
     *   "targetType": "yyy",
     *   "sourceFilters": {...},
     *   "targetFilters": {...}
     * }
     */
    @PostMapping("/{linkType}/match")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> matchLinks(
            @PathVariable String linkType,
            @RequestBody Map<String, Object> request) {
        try {
            String sourceType = (String) request.get("sourceType");
            String targetType = (String) request.get("targetType");
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceFilters = (Map<String, Object>) request.get("sourceFilters");
            @SuppressWarnings("unchecked")
            Map<String, Object> targetFilters = (Map<String, Object>) request.get("targetFilters");

            if (sourceType == null || targetType == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(400, "sourceType and targetType are required"));
            }

            List<Map<String, Object>> links = etlLinkService.matchLinks(linkType, sourceType, targetType, 
                                                                        sourceFilters, targetFilters);
            return ResponseEntity.ok(ApiResponse.success(links));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to match links: " + e.getMessage()));
        }
    }

    /**
     * 批量删除Links关系
     * DELETE /api/v1/etl/links/{linkType}/batch
     * Body: ["linkId1", "linkId2", ...]
     */
    @DeleteMapping("/{linkType}/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteLinksBatch(
            @PathVariable String linkType,
            @RequestBody List<String> linkIds) {
        try {
            if (linkIds == null || linkIds.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(400, "Link IDs list cannot be empty"));
            }
            
            ETLLinkService.BatchDeleteResult result = etlLinkService.deleteLinksBatch(linkType, linkIds);
            return ResponseEntity.ok(ApiResponse.success(result.toMap()));
        } catch (Loader.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to delete links: " + e.getMessage()));
        }
    }
}

