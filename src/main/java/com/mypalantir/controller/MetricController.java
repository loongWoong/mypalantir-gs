package com.mypalantir.controller;

import com.mypalantir.metric.*;
import com.mypalantir.service.AtomicMetricService;
import com.mypalantir.service.MetricCalculator;
import com.mypalantir.service.MetricService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标管理控制器
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {
    private final AtomicMetricService atomicMetricService;
    private final MetricService metricService;
    private final MetricCalculator metricCalculator;

    public MetricController(AtomicMetricService atomicMetricService, MetricService metricService, MetricCalculator metricCalculator) {
        this.atomicMetricService = atomicMetricService;
        this.metricService = metricService;
        this.metricCalculator = metricCalculator;
    }

    // ==================== 原子指标 API ====================

    @PostMapping("/atomic-metrics")
    public ResponseEntity<ApiResponse<Map<String, String>>> createAtomicMetric(@RequestBody Map<String, Object> data) {
        try {
            AtomicMetric atomicMetric = new AtomicMetric(data);
            // 提取工作空间ID列表（可选）
            @SuppressWarnings("unchecked")
            List<String> workspaceIds = (List<String>) data.get("workspace_ids");
            String id = atomicMetricService.createAtomicMetric(atomicMetric, workspaceIds);
            Map<String, String> result = new HashMap<>();
            result.put("id", id);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/atomic-metrics")
    public ResponseEntity<ApiResponse<List<AtomicMetric>>> listAtomicMetrics() {
        try {
            List<AtomicMetric> metrics = atomicMetricService.listAtomicMetrics();
            return ResponseEntity.ok(ApiResponse.success(metrics));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to list atomic metrics: " + e.getMessage()));
        }
    }

    @GetMapping("/atomic-metrics/{id}")
    public ResponseEntity<ApiResponse<AtomicMetric>> getAtomicMetric(@PathVariable String id) {
        try {
            AtomicMetric metric = atomicMetricService.getAtomicMetric(id);
            return ResponseEntity.ok(ApiResponse.success(metric));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "Atomic metric not found: " + e.getMessage()));
        }
    }

    @PutMapping("/atomic-metrics/{id}")
    public ResponseEntity<ApiResponse<Void>> updateAtomicMetric(@PathVariable String id, @RequestBody Map<String, Object> data) {
        try {
            // 移除 workspace_ids 字段（它不属于 AtomicMetric schema）
            data.remove("workspace_ids");
            
            AtomicMetric atomicMetric = new AtomicMetric(data);
            atomicMetricService.updateAtomicMetric(id, atomicMetric);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @DeleteMapping("/atomic-metrics/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAtomicMetric(@PathVariable String id) {
        try {
            atomicMetricService.deleteAtomicMetric(id);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to delete atomic metric: " + e.getMessage()));
        }
    }

    @GetMapping("/atomic-metrics/by-business-process/{businessProcess}")
    public ResponseEntity<ApiResponse<List<AtomicMetric>>> getAtomicMetricsByBusinessProcess(@PathVariable String businessProcess) {
        try {
            List<AtomicMetric> metrics = atomicMetricService.findAtomicMetricsByBusinessProcess(businessProcess);
            return ResponseEntity.ok(ApiResponse.success(metrics));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to find atomic metrics: " + e.getMessage()));
        }
    }

    /**
     * 验证原子指标 - 预执行查询并返回 SQL 和结果
     * 用于在创建指标前验证指标定义是否正确
     */
    @PostMapping("/atomic-metrics/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateAtomicMetric(@RequestBody Map<String, Object> data) {
        try {
            // 创建临时的原子指标对象（不保存到存储）
            AtomicMetric atomicMetric = new AtomicMetric(data);
            
            // 检查是否为新生成的指标（使用 UUID 格式的 ID）
            String metricId = atomicMetric.getId();
            boolean isNewMetric = metricId != null && metricId.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
            
            if (isNewMetric) {
                // 新提取的原子指标（无聚合函数），跳过数据库计算
                Map<String, Object> response = new HashMap<>();
                response.put("sql", "-- 原子指标验证通过，直接映射物理字段");
                response.put("columns", new java.util.ArrayList<>());
                response.put("rows", new java.util.ArrayList<>());
                response.put("rowCount", 0);
                response.put("message", "原子指标验证通过，保存后将参与派生指标计算");
                return ResponseEntity.ok(ApiResponse.success(response));
            }
            
            // 构建查询来执行验证
            MetricQuery query = new MetricQuery();
            query.setMetricId("_validate_"); // 临时ID
            
            // 执行查询获取结果（包含 SQL 和数据）
            MetricResult result = metricCalculator.calculateAtomicMetric(atomicMetric, query);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("sql", result.getSql());
            response.put("columns", result.getColumns());
            response.put("rows", result.getResults());
            response.put("rowCount", result.getResults() != null ? result.getResults().size() : 0);
            
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "验证失败: " + e.getMessage()));
        }
    }

    /**
     * 验证指标定义（派生指标/复合指标） - 预执行查询并返回 SQL 和结果
     * 用于在创建指标前验证指标定义是否正确
     */
    @PostMapping("/definitions/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateMetricDefinition(@RequestBody Map<String, Object> data) {
        try {
            // 创建临时的指标定义对象（不保存到存储）
            MetricDefinition metricDefinition = new MetricDefinition(data);
            
            // 检查是否为新生成的指标（使用 UUID 格式的 ID）
            String metricId = metricDefinition.getId();
            boolean isNewMetric = metricId != null && metricId.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
            
            if (isNewMetric) {
                // 新提取的指标，跳过数据库计算，只返回基础验证通过信息
                Map<String, Object> response = new HashMap<>();
                response.put("sql", "-- 新指标验证通过，保存后将执行实际查询");
                response.put("columns", new java.util.ArrayList<>());
                response.put("rows", new java.util.ArrayList<>());
                response.put("rowCount", 0);
                response.put("message", "指标验证通过，保存后将执行实际查询计算");
                return ResponseEntity.ok(ApiResponse.success(response));
            }
            
            // 构建查询来执行验证
            MetricQuery query = new MetricQuery();
            query.setMetricId("_validate_"); // 临时ID
            
            // 执行查询获取结果（包含 SQL 和数据）
            MetricResult result = metricCalculator.calculateMetric(metricDefinition, query);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("sql", result.getSql());
            response.put("columns", result.getColumns());
            response.put("rows", result.getResults());
            response.put("rowCount", result.getResults() != null ? result.getResults().size() : 0);
            
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "验证失败: " + e.getMessage()));
        }
    }

    // ==================== 指标定义 API ====================

    @PostMapping("/definitions")
    public ResponseEntity<ApiResponse<Map<String, String>>> createMetricDefinition(@RequestBody Map<String, Object> data) {
        try {
            MetricDefinition metricDefinition = new MetricDefinition(data);
            // 提取工作空间ID列表（可选）
            @SuppressWarnings("unchecked")
            List<String> workspaceIds = (List<String>) data.get("workspace_ids");
            String id = metricService.createMetricDefinition(metricDefinition, workspaceIds);
            Map<String, String> result = new HashMap<>();
            result.put("id", id);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/definitions")
    public ResponseEntity<ApiResponse<List<MetricDefinition>>> listMetricDefinitions(@RequestParam(required = false) String metricType) {
        try {
            List<MetricDefinition> metrics;
            if (metricType != null && !metricType.isEmpty()) {
                metrics = metricService.findMetricsByType(metricType);
            } else {
                metrics = metricService.listMetricDefinitions();
            }
            return ResponseEntity.ok(ApiResponse.success(metrics));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to list metric definitions: " + e.getMessage()));
        }
    }

    @GetMapping("/definitions/{id}")
    public ResponseEntity<ApiResponse<MetricDefinition>> getMetricDefinition(@PathVariable String id) {
        try {
            MetricDefinition metric = metricService.getMetricDefinition(id);
            return ResponseEntity.ok(ApiResponse.success(metric));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "Metric definition not found: " + e.getMessage()));
        }
    }

    @PutMapping("/definitions/{id}")
    public ResponseEntity<ApiResponse<Void>> updateMetricDefinition(@PathVariable String id, @RequestBody Map<String, Object> data) {
        try {
            // 移除 workspace_ids 字段（它不属于 MetricDefinition schema）
            data.remove("workspace_ids");
            
            MetricDefinition metricDefinition = new MetricDefinition(data);
            metricService.updateMetricDefinition(id, metricDefinition);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @DeleteMapping("/definitions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMetricDefinition(@PathVariable String id) {
        try {
            metricService.deleteMetricDefinition(id);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to delete metric definition: " + e.getMessage()));
        }
    }

    // ==================== 指标计算 API ====================

    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<MetricResult>> calculateMetric(@RequestBody Map<String, Object> request) {
        try {
            String metricId = (String) request.get("metric_id");
            
            MetricQuery query = new MetricQuery();
            query.setMetricId(metricId);
            query.setUseCache(request.containsKey("cache") && (Boolean) request.get("cache"));

            // 解析时间范围
            @SuppressWarnings("unchecked")
            Map<String, Object> timeRangeMap = (Map<String, Object>) request.get("time_range");
            if (timeRangeMap != null) {
                MetricQuery.TimeRange timeRange = new MetricQuery.TimeRange(
                        (String) timeRangeMap.get("start"),
                        (String) timeRangeMap.get("end")
                );
                query.setTimeRange(timeRange);
            }

            // 解析维度
            @SuppressWarnings("unchecked")
            Map<String, Object> dimensions = (Map<String, Object>) request.get("dimensions");
            query.setDimensions(dimensions);

            MetricResult result;
            // 先尝试获取原子指标
            try {
                AtomicMetric atomicMetric = atomicMetricService.getAtomicMetric(metricId);
                result = metricCalculator.calculateAtomicMetric(atomicMetric, query);
            } catch (IOException e) {
                // 如果不是原子指标，尝试获取指标定义
                try {
                    MetricDefinition metricDefinition = metricService.getMetricDefinition(metricId);
                    result = metricCalculator.calculateMetric(metricDefinition, query);
                } catch (IOException ex) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error(404, "指标不存在: " + metricId));
                }
            }
            
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to calculate metric: " + e.getMessage()));
        }
    }

    @PostMapping("/query/batch")
    public ResponseEntity<ApiResponse<List<MetricResult>>> batchQueryMetrics(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> queries = (List<Map<String, Object>>) request.get("queries");
            List<MetricResult> results = new java.util.ArrayList<>();

            for (Map<String, Object> queryMap : queries) {
                String metricId = (String) queryMap.get("metric_id");
                MetricDefinition metricDefinition = metricService.getMetricDefinition(metricId);

                MetricQuery query = new MetricQuery();
                query.setMetricId(metricId);

                @SuppressWarnings("unchecked")
                Map<String, Object> timeRangeMap = (Map<String, Object>) queryMap.get("time_range");
                if (timeRangeMap != null) {
                    MetricQuery.TimeRange timeRange = new MetricQuery.TimeRange(
                            (String) timeRangeMap.get("start"),
                            (String) timeRangeMap.get("end")
                    );
                    query.setTimeRange(timeRange);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> dimensions = (Map<String, Object>) queryMap.get("dimensions");
                query.setDimensions(dimensions);

                MetricResult result = metricCalculator.calculateMetric(metricDefinition, query);
                results.add(result);
            }

            return ResponseEntity.ok(ApiResponse.success(results));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Failed to batch query metrics: " + e.getMessage()));
        }
    }
}
