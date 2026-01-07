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
