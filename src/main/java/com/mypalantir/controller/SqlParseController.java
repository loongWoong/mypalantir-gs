package com.mypalantir.controller;

import com.mypalantir.sql.SqlParseResult;
import com.mypalantir.sql.SqlParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sql")
@CrossOrigin(origins = "*")
public class SqlParseController {

    @Autowired
    private SqlParserService sqlParserService;

    @PostMapping("/parse")
    public ResponseEntity<SqlParseResult> parseSql(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                SqlParseResult.error("", "SQL不能为空")
            );
        }

        SqlParseResult result = sqlParserService.parse(sql);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeMetrics(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "SQL不能为空"));
        }

        SqlParseResult result = sqlParserService.parse(sql);

        // 汇总分析结果用于指标定义
        Map<String, Object> analysis = Map.of(
            "totalFields", result.getStatistics() != null ? result.getStatistics().getTotalFields() : 0,
            "aggregatedFields", countAggregatedFields(result),
            "tables", extractTablesFromResult(result),
            "subqueries", result.getStatistics() != null ? result.getStatistics().getTotalSubqueries() : 0,
            "rawLineage", result.getLineage()
        );

        return ResponseEntity.ok(analysis);
    }

    private int countAggregatedFields(SqlParseResult result) {
        if (result.getTree() == null) return 0;
        return countAggregatedFieldsRecursive(result.getTree());
    }

    private int countAggregatedFieldsRecursive(com.mypalantir.sql.SqlNodeTree node) {
        int count = 0;
        if (node.getFields() != null) {
            for (com.mypalantir.sql.FieldInfo field : node.getFields()) {
                if (field.isAggregated()) count++;
            }
        }
        if (node.getChildren() != null) {
            for (com.mypalantir.sql.SqlNodeTree child : node.getChildren()) {
                count += countAggregatedFieldsRecursive(child);
            }
        }
        return count;
    }

    private java.util.List<String> extractTablesFromResult(SqlParseResult result) {
        java.util.Set<String> tables = new java.util.HashSet<>();
        if (result.getTree() != null) {
            extractTablesRecursive(result.getTree(), tables);
        }
        return new java.util.ArrayList<>(tables);
    }

    private void extractTablesRecursive(com.mypalantir.sql.SqlNodeTree node, java.util.Set<String> tables) {
        if (node.getTables() != null) {
            for (com.mypalantir.sql.TableReference table : node.getTables()) {
                tables.add(table.getName());
            }
        }
        if (node.getChildren() != null) {
            for (com.mypalantir.sql.SqlNodeTree child : node.getChildren()) {
                extractTablesRecursive(child, tables);
            }
        }
    }
}
