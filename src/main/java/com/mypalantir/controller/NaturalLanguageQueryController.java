package com.mypalantir.controller;

import com.mypalantir.query.OntologyQuery;
import com.mypalantir.query.QueryExecutor;
import com.mypalantir.service.NaturalLanguageQueryService;
import com.mypalantir.service.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 自然语言查询控制器
 */
@RestController
@RequestMapping("/api/v1/query/natural-language")
public class NaturalLanguageQueryController {
    private static final Logger logger = LoggerFactory.getLogger(NaturalLanguageQueryController.class);
    
    private final NaturalLanguageQueryService naturalLanguageQueryService;
    private final QueryService queryService;
    
    public NaturalLanguageQueryController(
            NaturalLanguageQueryService naturalLanguageQueryService,
            QueryService queryService) {
        this.naturalLanguageQueryService = naturalLanguageQueryService;
        this.queryService = queryService;
    }
    
    /**
     * 执行自然语言查询
     * 接受自然语言查询文本，转换为 OntologyQuery 并执行。
     * 支持 dataSourceType：raw=原始数据（映射表），sync=同步数据（同步表），不传时默认 sync。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeNaturalLanguageQuery(
            @RequestBody Map<String, Object> request) {
        try {
            String query = request.get("query") != null ? request.get("query").toString() : null;
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "查询文本不能为空"));
            }
            String dataSourceType = request.get("dataSourceType") != null ? request.get("dataSourceType").toString() : null;
            
            // 转换为 OntologyQuery
            OntologyQuery ontologyQuery = naturalLanguageQueryService.convertToQuery(query);
            if (dataSourceType != null && !dataSourceType.isEmpty()) {
                ontologyQuery.setDataSourceType(dataSourceType);
            }
            
            // 将 OntologyQuery 转换为 Map 格式
            Map<String, Object> queryMap = naturalLanguageQueryService.convertToMap(ontologyQuery);

            // 执行查询
            QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);

            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("convertedQuery", queryMap);
            response.put("columns", result.getColumns());
            response.put("rows", result.getRows());
            response.put("rowCount", result.getRowCount());

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (NaturalLanguageQueryService.NaturalLanguageQueryException e) {
            logger.error("Natural language query conversion failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "自然语言查询转换失败: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            logger.error("Query execution failed", e);
            e.printStackTrace();
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = e.getClass().getSimpleName();
                if (e.getCause() != null) {
                    errorMessage += ": " + e.getCause().getMessage();
                }
            }
            return ResponseEntity.status(500)
                .body(ApiResponse.error(500, "查询执行失败: " + errorMessage));
        }
    }

    /**
     * 仅转换自然语言查询为 OntologyQuery（不执行）
     * 用于调试和验证。支持 dataSourceType：raw=原始数据，sync=同步数据。
     */
    @PostMapping("/convert")
    public ResponseEntity<ApiResponse<Map<String, Object>>> convertNaturalLanguageQuery(
            @RequestBody Map<String, Object> request) {
        try {
            String query = request.get("query") != null ? request.get("query").toString() : null;
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "查询文本不能为空"));
            }
            String dataSourceType = request.get("dataSourceType") != null ? request.get("dataSourceType").toString() : null;
            
            // 转换为 OntologyQuery
            OntologyQuery ontologyQuery = naturalLanguageQueryService.convertToQuery(query);
            if (dataSourceType != null && !dataSourceType.isEmpty()) {
                ontologyQuery.setDataSourceType(dataSourceType);
            }
            // 将 OntologyQuery 转换为 Map 格式
            Map<String, Object> queryMap = naturalLanguageQueryService.convertToMap(ontologyQuery);

            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("convertedQuery", queryMap);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (NaturalLanguageQueryService.NaturalLanguageQueryException e) {
            logger.error("Natural language query conversion failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "自然语言查询转换失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Conversion failed", e);
            e.printStackTrace();
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = e.getClass().getSimpleName();
            }
            return ResponseEntity.status(500)
                .body(ApiResponse.error(500, "转换失败: " + errorMessage));
        }
    }
}

