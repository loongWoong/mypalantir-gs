package com.mypalantir.service;

import com.mypalantir.metric.*;
import com.mypalantir.query.OntologyQuery;
import com.mypalantir.query.QueryExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 指标计算服务
 */
@Service
public class MetricCalculator {
    private final QueryService queryService;
    private final AtomicMetricService atomicMetricService;
    private final MetricService metricService;
    private QueryExecutor queryExecutor;

    public MetricCalculator(QueryService queryService, AtomicMetricService atomicMetricService, MetricService metricService) {
        this.queryService = queryService;
        this.atomicMetricService = atomicMetricService;
        this.metricService = metricService;
    }

    /**
     * 计算原子指标
     */
    public MetricResult calculateAtomicMetric(AtomicMetric atomicMetric, MetricQuery query) throws Exception {
        // 构建OntologyQuery（原子指标没有时间粒度、维度等配置）
        OntologyQuery ontologyQuery = buildAtomicMetricQuery(atomicMetric, query);

        // 执行查询
        Map<String, Object> queryMap = ontologyQueryToMap(ontologyQuery);
        QueryExecutor.QueryResult queryResult = queryService.executeQuery(queryMap);

        // 转换为MetricResult
        MetricResult result = convertAtomicMetricToResult(queryResult, atomicMetric);

        return result;
    }

    /**
     * 计算指标
     */
    public MetricResult calculateMetric(MetricDefinition metricDefinition, MetricQuery query) throws Exception {
        if ("derived".equals(metricDefinition.getMetricType())) {
            return calculateDerivedMetric(metricDefinition, query);
        } else if ("composite".equals(metricDefinition.getMetricType())) {
            return calculateCompositeMetric(metricDefinition, query);
        } else {
            throw new IllegalArgumentException("不支持的指标类型: " + metricDefinition.getMetricType());
        }
    }

    /**
     * 计算派生指标
     */
    private MetricResult calculateDerivedMetric(MetricDefinition metricDefinition, MetricQuery query) throws Exception {
        System.out.println("[calculateDerivedMetric] Calculating derived metric: " + metricDefinition.getId());
        System.out.println("[calculateDerivedMetric] Metric name: " + metricDefinition.getName());
        System.out.println("[calculateDerivedMetric] Query: " + query);
            
        // 获取原子指标
        AtomicMetric atomicMetric = atomicMetricService.getAtomicMetric(metricDefinition.getAtomicMetricId());
        System.out.println("[calculateDerivedMetric] Atomic metric: " + atomicMetric.getName());
    
        // 构建 OntologyQuery
        OntologyQuery ontologyQuery = buildOntologyQuery(metricDefinition, atomicMetric, query);
    
        // 执行查询
        Map<String, Object> queryMap = ontologyQueryToMap(ontologyQuery);
        System.out.println("[calculateDerivedMetric] Executing query for object: " + ontologyQuery.getFrom());
        QueryExecutor.QueryResult queryResult = queryService.executeQuery(queryMap);
        System.out.println("[calculateDerivedMetric] Query returned " + queryResult.getRows().size() + " rows");
    
        // 转换为 MetricResult
        MetricResult result = convertToMetricResult(queryResult, metricDefinition, atomicMetric);
    
        // 处理时间粒度转换
        if (metricDefinition.getTimeGranularity() != null) {
            result = applyTimeGranularity(result, metricDefinition);
        }
    
        // 计算对比指标（同比、环比）
        if (metricDefinition.getComparisonType() != null && !metricDefinition.getComparisonType().isEmpty()) {
            result = addComparisons(result, metricDefinition, query);
        }
            
        System.out.println("[calculateDerivedMetric] Calculation completed successfully");
        return result;
    }

    /**
     * 计算复合指标
     */
    private MetricResult calculateCompositeMetric(MetricDefinition metricDefinition, MetricQuery query) throws Exception {
        // 获取基础指标并计算结果
        List<MetricResult> baseResults = new ArrayList<>();
        for (String id : metricDefinition.getBaseMetricIds()) {
            try {
                // 先尝试获取指标定义
                MetricResult result;
                try {
                    MetricDefinition baseMetric = metricService.getMetricDefinition(id);
                    // 对于派生指标，需要传递时间范围和维度参数
                    MetricQuery baseQuery = new MetricQuery();
                    baseQuery.setMetricId(id);
                    baseQuery.setTimeRange(query.getTimeRange());
                    baseQuery.setDimensions(query.getDimensions());
                    baseQuery.setUseCache(query.isUseCache());
                    result = calculateMetric(baseMetric, baseQuery);
                } catch (IOException e) {
                    // 如果不是指标定义，尝试获取原子指标
                    try {
                        AtomicMetric atomicMetric = atomicMetricService.getAtomicMetric(id);
                        // 原子指标也需要传递查询参数
                        MetricQuery baseQuery = new MetricQuery();
                        baseQuery.setMetricId(id);
                        baseQuery.setTimeRange(query.getTimeRange());
                        baseQuery.setDimensions(query.getDimensions());
                        baseQuery.setUseCache(query.isUseCache());
                        result = calculateAtomicMetric(atomicMetric, baseQuery);
                    } catch (IOException ex) {
                        throw new RuntimeException("无法获取基础指标: " + id + " (既不是指标定义也不是原子指标)", ex);
                    }
                }
                baseResults.add(result);
            } catch (RuntimeException e) {
                // 重新抛出 RuntimeException
                throw e;
            } catch (Exception e) {
                // 将其他检查异常包装为 RuntimeException，并添加更详细的错误信息
                System.err.println("[calculateCompositeMetric] Failed to calculate base metric: " + id);
                System.err.println("[calculateCompositeMetric] Query: " + query);
                e.printStackTrace();
                throw new RuntimeException("无法计算基础指标: " + id + ", 错误: " + e.getMessage(), e);
            }
        }

        // 根据公式计算结果
        return calculateByFormula(metricDefinition, baseResults);
    }

    /**
     * 构建原子指标的OntologyQuery
     */
    private OntologyQuery buildAtomicMetricQuery(AtomicMetric atomicMetric, MetricQuery query) {
        OntologyQuery ontologyQuery = new OntologyQuery();

        // 设置FROM（业务过程对应的对象类型）
        ontologyQuery.setFrom(atomicMetric.getBusinessProcess());

        // 设置聚合指标（metrics格式：["function", "field", "alias"]）
        List<Object> metrics = new ArrayList<>();
        String aggregationFunction = atomicMetric.getAggregationFunction();
        String aggregationField = atomicMetric.getAggregationField();
        
        if ("COUNT".equals(aggregationFunction) || "DISTINCT_COUNT".equals(aggregationFunction)) {
            // COUNT(*) 或 COUNT(DISTINCT field)
            // 处理 aggregation_field 为空、null、"-" 的情况，都使用 "*"
            String field = "*";
            if (aggregationField != null && !aggregationField.isEmpty() && !"-".equals(aggregationField.trim())) {
                field = aggregationField;
            }
            // 确保 field 不为 null
            if (field == null) {
                field = "*";
            }
            if ("DISTINCT_COUNT".equals(aggregationFunction)) {
                // 使用 "count_distinct" 作为函数名，在 buildAggregate 中特殊处理
                metrics.add(java.util.Arrays.asList("count_distinct", field, null));
            } else {
                // COUNT(*) 使用 "*" 作为字段
                metrics.add(java.util.Arrays.asList("count", "*", null));
            }
        } else if (aggregationField != null && !aggregationField.isEmpty() && !"-".equals(aggregationField.trim())) {
            // 其他聚合函数：SUM, AVG, MIN, MAX
            String function = aggregationFunction.toLowerCase();
            if (function == null || function.isEmpty()) {
                throw new IllegalArgumentException("Aggregation function cannot be null or empty");
            }
            metrics.add(java.util.Arrays.asList(function, aggregationField, null));
        } else {
            // 如果没有有效的聚合字段，抛出异常
            throw new IllegalArgumentException("Invalid aggregation configuration: function=" + aggregationFunction + ", field=" + aggregationField);
        }
        ontologyQuery.setMetrics(metrics);

        // 设置WHERE（查询条件中的维度）
        Map<String, Object> where = new HashMap<>();
        if (query.getDimensions() != null) {
            where.putAll(query.getDimensions());
        }
        if (!where.isEmpty()) {
            ontologyQuery.setWhere(where);
        }

        // 设置GROUP BY（如果有维度）
        if (query.getDimensions() != null && !query.getDimensions().isEmpty()) {
            ontologyQuery.setGroupBy(new ArrayList<>(query.getDimensions().keySet()));
        }

        return ontologyQuery;
    }

    /**
     * 构建OntologyQuery
     */
    private OntologyQuery buildOntologyQuery(MetricDefinition metricDefinition, AtomicMetric atomicMetric, MetricQuery query) {
        OntologyQuery ontologyQuery = new OntologyQuery();

        // 设置FROM
        Map<String, Object> businessScope = metricDefinition.getBusinessScope();
        if (businessScope != null) {
            String type = (String) businessScope.get("type");
            if ("single".equals(type)) {
                String baseObjectType = (String) businessScope.get("base_object_type");
                ontologyQuery.setFrom(baseObjectType);
            } else if ("multi".equals(type)) {
                String from = (String) businessScope.get("from");
                ontologyQuery.setFrom(from);

                // 设置links
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> linksConfig = (List<Map<String, Object>>) businessScope.get("links");
                if (linksConfig != null) {
                    List<OntologyQuery.LinkQuery> links = new ArrayList<>();
                    for (Map<String, Object> linkConfig : linksConfig) {
                        OntologyQuery.LinkQuery linkQuery = new OntologyQuery.LinkQuery();
                        linkQuery.setName((String) linkConfig.get("name"));
                        @SuppressWarnings("unchecked")
                        List<String> select = (List<String>) linkConfig.get("select");
                        linkQuery.setSelect(select);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> where = (Map<String, Object>) linkConfig.get("where");
                        linkQuery.setWhere(where);
                        links.add(linkQuery);
                    }
                    ontologyQuery.setLinks(links);
                }
            }
        }

        // 设置聚合指标（metrics格式：["function", "field", "alias"]）
        List<Object> metrics = new ArrayList<>();
        String aggregationFunction = atomicMetric.getAggregationFunction();
        String aggregationField = atomicMetric.getAggregationField();
        
        if ("COUNT".equals(aggregationFunction) || "DISTINCT_COUNT".equals(aggregationFunction)) {
            // COUNT(*) 或 COUNT(DISTINCT field)
            // 处理 aggregation_field 为空、null、"-" 的情况，都使用 "*"
            String field = "*";
            if (aggregationField != null && !aggregationField.isEmpty() && !"-".equals(aggregationField.trim())) {
                field = aggregationField;
            }
            // 确保 field 不为 null
            if (field == null) {
                field = "*";
            }
            if ("DISTINCT_COUNT".equals(aggregationFunction)) {
                // 使用 "count_distinct" 作为函数名，在 buildAggregate 中特殊处理
                metrics.add(java.util.Arrays.asList("count_distinct", field, null));
            } else {
                // COUNT(*) 使用 "*" 作为字段
                metrics.add(java.util.Arrays.asList("count", "*", null));
            }
        } else if (aggregationField != null && !aggregationField.isEmpty() && !"-".equals(aggregationField.trim())) {
            // 其他聚合函数：SUM, AVG, MIN, MAX
            String function = aggregationFunction.toLowerCase();
            if (function == null || function.isEmpty()) {
                throw new IllegalArgumentException("Aggregation function cannot be null or empty");
            }
            metrics.add(java.util.Arrays.asList(function, aggregationField, null));
        } else {
            // 如果没有有效的聚合字段，抛出异常
            throw new IllegalArgumentException("Invalid aggregation configuration: function=" + aggregationFunction + ", field=" + aggregationField);
        }
        ontologyQuery.setMetrics(metrics);

        // 设置过滤条件（使用 filter 表达式格式）
        List<Object> filterExpressions = new ArrayList<>();
        // 使用 Set 来跟踪已添加的字段，避免重复
        java.util.Set<String> addedFields = new java.util.HashSet<>();
        
        // 添加派生指标的过滤条件
        if (metricDefinition.getFilterConditions() != null && !metricDefinition.getFilterConditions().isEmpty()) {
            for (Map.Entry<String, Object> entry : metricDefinition.getFilterConditions().entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                // 跳过空值
                if (value != null && !(value instanceof String && ((String) value).trim().isEmpty())) {
                    String fieldKey = "=" + field; // 用于跟踪等值条件
                    if (!addedFields.contains(fieldKey)) {
                        filterExpressions.add(java.util.Arrays.asList("=", field, value));
                        addedFields.add(fieldKey);
                    }
                }
            }
        }
        
        // 添加查询维度条件（如果字段还没有被添加）
        if (query.getDimensions() != null && !query.getDimensions().isEmpty()) {
            for (Map.Entry<String, Object> entry : query.getDimensions().entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                // 跳过空值
                if (value != null && !(value instanceof String && ((String) value).trim().isEmpty())) {
                    String fieldKey = "=" + field; // 用于跟踪等值条件
                    if (!addedFields.contains(fieldKey)) {
                        filterExpressions.add(java.util.Arrays.asList("=", field, value));
                        addedFields.add(fieldKey);
                    }
                }
            }
        }
        
        // 添加时间范围条件（使用 >= 和 <= 操作符）
        if (query.getTimeRange() != null && metricDefinition.getTimeDimension() != null) {
            String timeField = metricDefinition.getTimeDimension();
            String startDate = query.getTimeRange().getStart();
            String endDate = query.getTimeRange().getEnd();
            
            if (startDate != null && !startDate.trim().isEmpty()) {
                filterExpressions.add(java.util.Arrays.asList(">=", timeField, startDate));
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                filterExpressions.add(java.util.Arrays.asList("<=", timeField, endDate));
            }
        }
        
        System.out.println("[buildOntologyQuery] Filter expressions count: " + filterExpressions.size());
        System.out.println("[buildOntologyQuery] Filter expressions: " + filterExpressions);
        
        if (!filterExpressions.isEmpty()) {
            ontologyQuery.setFilter(filterExpressions);
        }

        // 设置GROUP BY（考虑时间粒度转换）
        List<String> groupBy = new ArrayList<>();
        if (metricDefinition.getTimeDimension() != null) {
            String timeGroupField = getTimeGroupField(metricDefinition.getTimeDimension(), metricDefinition.getTimeGranularity());
            groupBy.add(timeGroupField);
        }
        if (metricDefinition.getDimensions() != null) {
            groupBy.addAll(metricDefinition.getDimensions());
        }
        if (!groupBy.isEmpty()) {
            ontologyQuery.setGroupBy(groupBy);
        }

        // 设置ORDER BY
        if (metricDefinition.getTimeDimension() != null) {
            List<OntologyQuery.OrderBy> orderBy = new ArrayList<>();
            orderBy.add(new OntologyQuery.OrderBy(metricDefinition.getTimeDimension(), "ASC"));
            ontologyQuery.setOrderBy(orderBy);
        }

        return ontologyQuery;
    }

    /**
     * 从Map中获取值，忽略键的大小写
     */
    private Object getValueIgnoreCase(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        
        // 首先尝试精确匹配
        Object value = map.get(key);
        if (value != null) {
            return value;
        }
        
        // 如果精确匹配失败，尝试忽略大小写匹配
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return null;
    }

    /**
     * 获取时间分组字段
     */
    private String getTimeGroupField(String timeDimension, String granularity) {
        // 当前查询引擎只支持字段名分组，不支持表达式分组，
        // 直接使用原始时间字段，避免出现 "Property 'DATE(x)' not found" 错误
        if (granularity == null || "day".equals(granularity)) {
            return timeDimension;
        } else if ("week".equals(granularity)
                || "month".equals(granularity)
                || "quarter".equals(granularity)
                || "year".equals(granularity)) {
            // TODO: 如需支持周/月/季度/年粒度，请在查询层实现对应的时间截断表达式或新增派生字段
            return timeDimension;
        }
        return timeDimension;
    }

    /**
     * 将原子指标查询结果转换为MetricResult
     * 直接返回 SQL 执行结果，不进行结构转换
     */
    private MetricResult convertAtomicMetricToResult(QueryExecutor.QueryResult queryResult, AtomicMetric atomicMetric) {
        MetricResult result = new MetricResult();
        result.setMetricId(atomicMetric.getId());
        result.setMetricName(atomicMetric.getDisplayName() != null ? atomicMetric.getDisplayName() : atomicMetric.getName());
        result.setCalculatedAt(LocalDateTime.now());

        // 直接使用查询结果的原始数据
        result.setResults(queryResult.getRows());
        result.setColumns(queryResult.getColumns());
        
        // 设置 SQL
        if (queryResult.getSql() != null) {
            result.setSql(queryResult.getSql());
        }

        return result;
    }

    /**
     * 转换为MetricResult
     * 直接返回 SQL 执行结果，不进行结构转换
     */
    private MetricResult convertToMetricResult(QueryExecutor.QueryResult queryResult, MetricDefinition metricDefinition, AtomicMetric atomicMetric) {
        MetricResult result = new MetricResult();
        result.setMetricId(metricDefinition.getId());
        result.setMetricName(metricDefinition.getDisplayName() != null ? metricDefinition.getDisplayName() : metricDefinition.getName());
        result.setTimeGranularity(metricDefinition.getTimeGranularity());
        result.setCalculatedAt(LocalDateTime.now());

        // 直接使用查询结果的原始数据
        result.setResults(queryResult.getRows());
        result.setColumns(queryResult.getColumns());
        
        // 设置 SQL
        if (queryResult.getSql() != null) {
            result.setSql(queryResult.getSql());
        }

        return result;
    }

    /**
     * 应用时间粒度转换
     */
    private MetricResult applyTimeGranularity(MetricResult result, MetricDefinition metricDefinition) {
        // 时间粒度转换逻辑已在SQL查询中处理
        return result;
    }

    /**
     * 添加对比数据
     */
    private MetricResult addComparisons(MetricResult result, MetricDefinition metricDefinition, MetricQuery query) throws Exception {
        for (Map<String, Object> point : result.getResults()) {
            Map<String, MetricResult.ComparisonValue> comparisons = new HashMap<>();
            
            for (String comparisonType : metricDefinition.getComparisonType()) {
                MetricResult.ComparisonValue comparison = calculateComparison(point, metricDefinition, comparisonType, query);
                if (comparison != null) {
                    comparisons.put(comparisonType, comparison);
                }
            }
            
            point.put("comparisons", comparisons);
        }
        
        return result;
    }

    /**
     * 计算对比值
     */
    private MetricResult.ComparisonValue calculateComparison(Map<String, Object> point, MetricDefinition metricDefinition, String comparisonType, MetricQuery query) throws Exception {
        String timeValue = (String) point.get(metricDefinition.getTimeDimension());
        if (timeValue == null) {
            return null;
        }

        LocalDate currentDate = parseTimeValue(timeValue, metricDefinition.getTimeGranularity());
        LocalDate compareDate = null;

        if ("YoY".equals(comparisonType)) {
            compareDate = currentDate.minusYears(1);
        } else if ("MoM".equals(comparisonType)) {
            if ("day".equals(metricDefinition.getTimeGranularity())) {
                compareDate = currentDate.minusDays(1);
            } else if ("week".equals(metricDefinition.getTimeGranularity())) {
                compareDate = currentDate.minusWeeks(1);
            } else if ("month".equals(metricDefinition.getTimeGranularity())) {
                compareDate = currentDate.minusMonths(1);
            }
        } else if ("WoW".equals(comparisonType)) {
            compareDate = currentDate.minusWeeks(1);
        } else if ("QoQ".equals(comparisonType)) {
            compareDate = currentDate.minusMonths(3);
        }

        if (compareDate == null) {
            return null;
        }

        // 构建维度值映射
        Map<String, Object> dimensionValues = new HashMap<>();
        if (metricDefinition.getDimensions() != null) {
            for (String dim : metricDefinition.getDimensions()) {
                if (point.containsKey(dim)) {
                    dimensionValues.put(dim, point.get(dim));
                }
            }
        }

        // 查询对比时间的数据
        Number compareValue = queryMetricValue(metricDefinition, compareDate, dimensionValues, query);
        if (compareValue == null || compareValue.doubleValue() == 0) {
            return null;
        }

        // 获取当前指标值（从结果列中查找聚合字段）
        Object metricValueObj = null;
        for (Map.Entry<String, Object> entry : point.entrySet()) {
            String key = entry.getKey();
            // 跳过时间和维度字段，找到聚合指标字段
            if (!key.equals(metricDefinition.getTimeDimension()) && 
                (metricDefinition.getDimensions() == null || !metricDefinition.getDimensions().contains(key))) {
                metricValueObj = entry.getValue();
                break;
            }
        }
        
        if (metricValueObj == null || !(metricValueObj instanceof Number)) {
            return null;
        }
        
        // 计算增长率
        double currentValue = ((Number) metricValueObj).doubleValue();
        double changeRate = (currentValue - compareValue.doubleValue()) / compareValue.doubleValue();

        String typeName = comparisonType.equals("YoY") ? "去年同期" : "上一周期";
        String direction = changeRate > 0 ? "增长" : "下降";
        String description = String.format("较%s%s%.2f%%", typeName, direction, Math.abs(changeRate) * 100);

        return new MetricResult.ComparisonValue(changeRate, String.format("%+.2f%%", changeRate * 100), description);
    }

    /**
     * 查询指标值
     */
    private Number queryMetricValue(MetricDefinition metricDefinition, LocalDate date, Map<String, Object> dimensions, MetricQuery originalQuery) throws Exception {
        // 创建新的查询，查询指定日期的数据
        MetricQuery compareQuery = new MetricQuery();
        compareQuery.setMetricId(metricDefinition.getId());
        compareQuery.setDimensions(dimensions);
        
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        MetricQuery.TimeRange timeRange = new MetricQuery.TimeRange(dateStr, dateStr);
        compareQuery.setTimeRange(timeRange);

        MetricResult result = calculateMetric(metricDefinition, compareQuery);
        if (result.getResults() != null && !result.getResults().isEmpty()) {
            Map<String, Object> row = result.getResults().get(0);
            // 获取聚合字段的值（跳过时间和维度字段）
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if (!key.equals(metricDefinition.getTimeDimension()) && 
                    (metricDefinition.getDimensions() == null || !metricDefinition.getDimensions().contains(key))) {
                    Object value = entry.getValue();
                    if (value instanceof Number) {
                        return (Number) value;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 解析时间值
     */
    private LocalDate parseTimeValue(String timeValue, String granularity) {
        if (granularity == null || "day".equals(granularity)) {
            return LocalDate.parse(timeValue);
        } else if ("week".equals(granularity)) {
            // YEARWEEK格式：202401
            int year = Integer.parseInt(timeValue.substring(0, 4));
            int week = Integer.parseInt(timeValue.substring(4));
            // 简化处理：使用第一周的第一天
            return LocalDate.ofYearDay(year, 1).plusWeeks(week - 1);
        } else if ("month".equals(granularity)) {
            // YYYY-MM格式
            String[] parts = timeValue.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            return LocalDate.of(year, month, 1);
        } else if ("quarter".equals(granularity)) {
            // YYYY-Q1格式
            String[] parts = timeValue.split("-Q");
            int year = Integer.parseInt(parts[0]);
            int quarter = Integer.parseInt(parts[1]);
            int month = (quarter - 1) * 3 + 1;
            return LocalDate.of(year, month, 1);
        } else if ("year".equals(granularity)) {
            int year = Integer.parseInt(timeValue);
            return LocalDate.of(year, 1, 1);
        }
        return LocalDate.parse(timeValue);
    }

    /**
     * 根据公式计算复合指标
     */
    private MetricResult calculateByFormula(MetricDefinition metricDefinition, List<MetricResult> baseResults) {
        MetricResult result = new MetricResult();
        result.setMetricId(metricDefinition.getId());
        result.setMetricName(metricDefinition.getDisplayName() != null ? metricDefinition.getDisplayName() : metricDefinition.getName());
        result.setTimeGranularity(metricDefinition.getTimeGranularity());
        result.setCalculatedAt(LocalDateTime.now());

        // 检查基础指标结果是否为空
        if (baseResults == null || baseResults.isEmpty()) {
            result.setResults(new ArrayList<>());
            return result;
        }

        // 生成复合指标的描述性 SQL
        String sql = buildCompositeMetricSql(metricDefinition, baseResults);
        result.setSql(sql);

        // 如果没有公式，直接返回第一个基础指标的结果
        String formula = metricDefinition.getDerivedFormula();
        if (formula == null || formula.trim().isEmpty()) {
            if (!baseResults.isEmpty() && baseResults.get(0).getResults() != null) {
                result.setResults(baseResults.get(0).getResults());
            } else {
                result.setResults(new ArrayList<>());
            }
            return result;
        }

        // 添加调试日志
        System.out.println("[CompositeMetric] Calculating composite metric: " + metricDefinition.getName());
        System.out.println("[CompositeMetric] Formula: " + formula);
        System.out.println("[CompositeMetric] Base metric IDs: " + metricDefinition.getBaseMetricIds());
        System.out.println("[CompositeMetric] Base results count: " + baseResults.size());

        // 解析公式并计算结果
        List<Map<String, Object>> calculatedResults = calculateFormulaResults(
            formula, metricDefinition, baseResults);
        result.setResults(calculatedResults);

        System.out.println("[CompositeMetric] Calculated results count: " + 
            (calculatedResults != null ? calculatedResults.size() : 0));

        return result;
    }

    /**
     * 构建复合指标的描述性 SQL
     */
    private String buildCompositeMetricSql(MetricDefinition metricDefinition, List<MetricResult> baseResults) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("-- Composite Metric: ").append(metricDefinition.getName()).append("\n");
        
        String formula = metricDefinition.getDerivedFormula();
        if (formula != null && !formula.trim().isEmpty()) {
            sqlBuilder.append("-- Formula: ").append(formula).append("\n");
        }
        
        sqlBuilder.append("-- Base Metrics:\n");
        List<String> baseMetricIds = metricDefinition.getBaseMetricIds();
        for (int i = 0; i < baseMetricIds.size() && i < baseResults.size(); i++) {
            String metricId = baseMetricIds.get(i);
            MetricResult baseResult = baseResults.get(i);
            sqlBuilder.append("--   [").append(i + 1).append("] Metric ID: ").append(metricId);
            if (baseResult.getMetricName() != null) {
                sqlBuilder.append(" (").append(baseResult.getMetricName()).append(")");
            }
            sqlBuilder.append("\n");
            if (baseResult.getSql() != null && !baseResult.getSql().isEmpty()) {
                // 缩进基础指标的 SQL
                String[] sqlLines = baseResult.getSql().split("\n");
                for (String line : sqlLines) {
                    sqlBuilder.append("--     ").append(line).append("\n");
                }
            }
        }
        
        // 添加计算说明（无论有多少个基础指标）
        if (formula != null && !formula.trim().isEmpty()) {
            sqlBuilder.append("-- \n");
            sqlBuilder.append("-- Calculation: Composite metric calculated by applying formula to base metric results\n");
            sqlBuilder.append("-- Formula: ").append(formula).append("\n");
            
            // 尝试构建一个示例计算说明（如果有结果）
            if (baseResults.size() > 0) {
                String exampleFormula = formula;
                boolean hasExample = false;
                for (int i = 0; i < baseMetricIds.size() && i < baseResults.size(); i++) {
                    String metricId = baseMetricIds.get(i);
                    MetricResult baseResult = baseResults.get(i);
                    if (baseResult.getResults() != null && !baseResult.getResults().isEmpty()) {
                        Map<String, Object> row = baseResult.getResults().get(0);
                        // 从第一行提取指标值
                        Number value = extractMetricValue(row, metricDefinition);
                        if (value != null) {
                            exampleFormula = exampleFormula.replace("{" + metricId + "}", 
                                "(" + value + ")");
                            hasExample = true;
                        }
                    }
                }
                if (hasExample) {
                    sqlBuilder.append("-- Example evaluation: ").append(exampleFormula).append("\n");
                }
            }
        }
        
        return sqlBuilder.toString();
    }

    /**
     * 根据公式计算数据点
     */
    private List<Map<String, Object>> calculateFormulaResults(
            String formula, MetricDefinition metricDefinition, List<MetricResult> baseResults) {
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        // 确保公式计算逻辑总是被执行
        // 即使只有一个基础指标，也要通过公式计算流程，确保公式逻辑被应用

        // 构建基础指标ID到结果的映射
        Map<String, MetricResult> metricIdToResult = new HashMap<>();
        List<String> baseMetricIds = metricDefinition.getBaseMetricIds();
        for (int i = 0; i < baseMetricIds.size() && i < baseResults.size(); i++) {
            metricIdToResult.put(baseMetricIds.get(i), baseResults.get(i));
        }

        // 如果所有基础指标都没有结果，返回空列表
        boolean hasAnyResults = false;
        for (MetricResult baseResult : baseResults) {
            if (baseResult.getResults() != null && !baseResult.getResults().isEmpty()) {
                hasAnyResults = true;
                break;
            }
        }
        if (!hasAnyResults) {
            return new ArrayList<>();
        }

        // 对齐数据点：按时间值和维度值分组
        Map<String, Map<String, Object>> dataPointKeyMap = new LinkedHashMap<>();
        
        // 收集所有唯一的数据点键（时间值 + 维度值的组合）
        for (MetricResult baseResult : baseResults) {
            if (baseResult.getResults() != null) {
                for (Map<String, Object> point : baseResult.getResults()) {
                    String key = buildDataPointKeyFromRow(point, metricDefinition);
                    if (!dataPointKeyMap.containsKey(key)) {
                        Map<String, Object> pointData = new HashMap<>(point);
                        dataPointKeyMap.put(key, pointData);
                    }
                }
            }
        }

        // 为每个唯一的数据点计算公式值
        for (Map.Entry<String, Map<String, Object>> entry : dataPointKeyMap.entrySet()) {
            Map<String, Object> pointData = entry.getValue();
            
            // 构建指标ID到值的映射
            Map<String, Number> metricValues = new HashMap<>();
            for (Map.Entry<String, MetricResult> metricEntry : metricIdToResult.entrySet()) {
                String metricId = metricEntry.getKey();
                MetricResult baseResult = metricEntry.getValue();
                
                if (baseResult.getResults() != null) {
                    // 查找匹配的数据点
                    Map<String, Object> matchingPoint = findMatchingDataPointInRows(
                        pointData, baseResult.getResults(), metricDefinition);
                    if (matchingPoint != null) {
                        // 找到聚合字段的值
                        Number metricValue = extractMetricValue(matchingPoint, metricDefinition);
                        if (metricValue != null) {
                            metricValues.put(metricId, metricValue);
                        }
                    }
                }
            }

            // 计算公式值
            System.out.println("[CompositeMetric] Evaluating formula for data point: " + entry.getKey());
            System.out.println("[CompositeMetric] Metric values: " + metricValues);
            Number calculatedValue = evaluateFormula(formula, metricValues, baseMetricIds);
            System.out.println("[CompositeMetric] Calculated value: " + calculatedValue);
            
            if (calculatedValue != null) {
                Map<String, Object> calculatedPoint = new HashMap<>(pointData);
                // 将计算结果作为聚合字段添加到结果中
                String metricFieldName = "metric_value";
                calculatedPoint.put(metricFieldName, calculatedValue);
                
                results.add(calculatedPoint);
            }
        }

        return results;
    }

    /**
     * 构建数据点的唯一键（时间值 + 维度值）
     */
    private String buildDataPointKey(MetricResult.MetricDataPoint point) {
        StringBuilder key = new StringBuilder();
        if (point.getTimeValue() != null) {
            key.append("time:").append(point.getTimeValue());
        }
        if (point.getDimensionValues() != null && !point.getDimensionValues().isEmpty()) {
            List<String> sortedKeys = new ArrayList<>(point.getDimensionValues().keySet());
            Collections.sort(sortedKeys);
            for (String dimKey : sortedKeys) {
                key.append("|").append(dimKey).append(":").append(point.getDimensionValues().get(dimKey));
            }
        }
        return key.toString();
    }

    /**
     * 从Map行构建数据点键
     */
    private String buildDataPointKeyFromRow(Map<String, Object> row, MetricDefinition metricDefinition) {
        StringBuilder key = new StringBuilder();
        
        // 添加时间字段
        if (metricDefinition.getTimeDimension() != null) {
            Object timeValue = row.get(metricDefinition.getTimeDimension());
            if (timeValue != null) {
                key.append("time:").append(timeValue);
            }
        }
        
        // 添加维度字段
        if (metricDefinition.getDimensions() != null && !metricDefinition.getDimensions().isEmpty()) {
            List<String> sortedDims = new ArrayList<>(metricDefinition.getDimensions());
            Collections.sort(sortedDims);
            for (String dimKey : sortedDims) {
                Object dimValue = row.get(dimKey);
                if (dimValue != null) {
                    key.append("|").append(dimKey).append(":").append(dimValue);
                }
            }
        }
        
        return key.toString();
    }

    /**
     * 查找匹配的数据点
     */
    private MetricResult.MetricDataPoint findMatchingDataPoint(
            Map<String, Object> targetPointData, List<MetricResult.MetricDataPoint> points) {
        String targetKey = buildDataPointKeyFromMap(targetPointData);
        for (MetricResult.MetricDataPoint point : points) {
            String pointKey = buildDataPointKey(point);
            if (targetKey.equals(pointKey)) {
                return point;
            }
        }
        return null;
    }

    /**
     * 在行列表中查找匹配的数据点
     */
    private Map<String, Object> findMatchingDataPointInRows(
            Map<String, Object> targetRow, List<Map<String, Object>> rows, MetricDefinition metricDefinition) {
        String targetKey = buildDataPointKeyFromRow(targetRow, metricDefinition);
        for (Map<String, Object> row : rows) {
            String rowKey = buildDataPointKeyFromRow(row, metricDefinition);
            if (targetKey.equals(rowKey)) {
                return row;
            }
        }
        return null;
    }

    /**
     * 从行中提取指标值（跳过时间和维度字段）
     */
    private Number extractMetricValue(Map<String, Object> row, MetricDefinition metricDefinition) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            // 跳过时间和维度字段
            if (metricDefinition.getTimeDimension() != null && key.equals(metricDefinition.getTimeDimension())) {
                continue;
            }
            if (metricDefinition.getDimensions() != null && metricDefinition.getDimensions().contains(key)) {
                continue;
            }
            // 找到聚合字段
            Object value = entry.getValue();
            if (value instanceof Number) {
                return (Number) value;
            }
        }
        return null;
    }

    /**
     * 从Map构建数据点键
     */
    private String buildDataPointKeyFromMap(Map<String, Object> pointData) {
        StringBuilder key = new StringBuilder();
        String timeValue = (String) pointData.get("timeValue");
        if (timeValue != null) {
            key.append("time:").append(timeValue);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> dimensionValues = (Map<String, Object>) pointData.get("dimensionValues");
        if (dimensionValues != null && !dimensionValues.isEmpty()) {
            List<String> sortedKeys = new ArrayList<>(dimensionValues.keySet());
            Collections.sort(sortedKeys);
            for (String dimKey : sortedKeys) {
                key.append("|").append(dimKey).append(":").append(dimensionValues.get(dimKey));
            }
        }
        return key.toString();
    }

    /**
     * 计算公式值
     * 公式格式：{metric_id_1} + {metric_id_2} 或 {metric_id_1} / {metric_id_2} 等
     */
    private Number evaluateFormula(String formula, Map<String, Number> metricValues, List<String> baseMetricIds) {
        if (formula == null || formula.trim().isEmpty()) {
            System.out.println("[CompositeMetric] Formula is empty, returning null");
            return null;
        }

        System.out.println("[CompositeMetric] Evaluating formula: " + formula);
        System.out.println("[CompositeMetric] Available metric values: " + metricValues);
        System.out.println("[CompositeMetric] Base metric IDs: " + baseMetricIds);

        // 替换公式中的 {metric_id} 为实际值
        String expression = formula;
        for (String metricId : baseMetricIds) {
            String placeholder = "{" + metricId + "}";
            Number value = metricValues.get(metricId);
            if (value != null) {
                String valueStr = String.valueOf(value.doubleValue());
                expression = expression.replace(placeholder, valueStr);
                System.out.println("[CompositeMetric] Replaced " + placeholder + " with " + valueStr);
            } else {
                // 如果某个基础指标的值缺失，返回 null
                System.out.println("[CompositeMetric] Missing value for metric ID: " + metricId + ", returning null");
                return null;
            }
        }

        System.out.println("[CompositeMetric] Expression after replacement: " + expression);

        // 简单的表达式求值（支持 +, -, *, /, %）
        try {
            // 使用 ScriptEngine 或简单的解析器
            // 这里使用简单的字符串替换和计算
            Number result = evaluateSimpleExpression(expression);
            System.out.println("[CompositeMetric] Formula evaluation result: " + result);
            return result;
        } catch (Exception e) {
            System.err.println("[CompositeMetric] Failed to evaluate formula: " + formula + ", expression: " + expression + ", error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 简单的表达式求值（仅支持基本运算符）
     */
    private Number evaluateSimpleExpression(String expression) {
        expression = expression.trim();
        
        // 移除所有空格
        expression = expression.replaceAll("\\s+", "");
        
        // 使用 ScriptEngineManager 和 JavaScript 引擎
        try {
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = manager.getEngineByName("JavaScript");
            Object result = engine.eval(expression);
            if (result instanceof Number) {
                return (Number) result;
            } else if (result instanceof Double) {
                return (Double) result;
            } else {
                return Double.parseDouble(result.toString());
            }
        } catch (Exception e) {
            // 如果 ScriptEngine 不可用，使用简单的递归下降解析器
            return parseAndEvaluate(expression);
        }
    }

    /**
     * 简单的递归下降解析器（处理 +, -, *, /）
     */
    private Number parseAndEvaluate(String expression) {
        try {
            // 移除所有空格
            expression = expression.replaceAll("\\s+", "");
            
            // 处理乘除
            int mulIndex = expression.indexOf('*');
            int divIndex = expression.indexOf('/');
            if (mulIndex > 0 || divIndex > 0) {
                int opIndex = -1;
                if (mulIndex > 0 && divIndex > 0) {
                    opIndex = Math.min(mulIndex, divIndex);
                } else if (mulIndex > 0) {
                    opIndex = mulIndex;
                } else {
                    opIndex = divIndex;
                }
                
                String left = expression.substring(0, opIndex);
                char op = expression.charAt(opIndex);
                String right = expression.substring(opIndex + 1);
                
                Number leftVal = parseAndEvaluate(left);
                Number rightVal = parseAndEvaluate(right);
                
                if (op == '*') {
                    return leftVal.doubleValue() * rightVal.doubleValue();
                } else {
                    if (rightVal.doubleValue() == 0) {
                        return null;
                    }
                    return leftVal.doubleValue() / rightVal.doubleValue();
                }
            }
            
            // 处理加减
            int addIndex = expression.lastIndexOf('+');
            int subIndex = expression.lastIndexOf('-');
            if (addIndex > 0 || (subIndex > 0 && subIndex < expression.length() - 1)) {
                int opIndex = -1;
                if (addIndex > 0 && subIndex > 0) {
                    opIndex = Math.max(addIndex, subIndex);
                } else if (addIndex > 0) {
                    opIndex = addIndex;
                } else {
                    opIndex = subIndex;
                }
                
                String left = expression.substring(0, opIndex);
                char op = expression.charAt(opIndex);
                String right = expression.substring(opIndex + 1);
                
                Number leftVal = parseAndEvaluate(left);
                Number rightVal = parseAndEvaluate(right);
                
                if (op == '+') {
                    return leftVal.doubleValue() + rightVal.doubleValue();
                } else {
                    return leftVal.doubleValue() - rightVal.doubleValue();
                }
            }
            
            // 解析数字
            return Double.parseDouble(expression);
        } catch (Exception e) {
            System.err.println("Failed to parse expression: " + expression + ", error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 将OntologyQuery转换为Map
     */
    private Map<String, Object> ontologyQueryToMap(OntologyQuery query) {
        Map<String, Object> map = new HashMap<>();
        map.put("from", query.getFrom());
        if (query.getSelect() != null) {
            map.put("select", query.getSelect());
        }
        if (query.getWhere() != null) {
            map.put("where", query.getWhere());
        }
        // 添加 filter 字段转换（新格式表达式数组）
        if (query.getFilter() != null && !query.getFilter().isEmpty()) {
            map.put("filter", query.getFilter());
        }
        if (query.getGroupBy() != null) {
            map.put("group_by", query.getGroupBy());
        }
        // 添加 metrics 字段转换
        if (query.getMetrics() != null && !query.getMetrics().isEmpty()) {
            map.put("metrics", query.getMetrics());
        }
        if (query.getLinks() != null) {
            List<Map<String, Object>> links = new ArrayList<>();
            for (OntologyQuery.LinkQuery link : query.getLinks()) {
                Map<String, Object> linkMap = new HashMap<>();
                linkMap.put("name", link.getName());
                if (link.getSelect() != null) {
                    linkMap.put("select", link.getSelect());
                }
                if (link.getWhere() != null) {
                    linkMap.put("where", link.getWhere());
                }
                links.add(linkMap);
            }
            map.put("links", links);
        }
        if (query.getOrderBy() != null) {
            List<Map<String, Object>> orderBy = new ArrayList<>();
            for (OntologyQuery.OrderBy ob : query.getOrderBy()) {
                Map<String, Object> obMap = new HashMap<>();
                obMap.put("field", ob.getField());
                obMap.put("direction", ob.getDirection());
                orderBy.add(obMap);
            }
            map.put("orderBy", orderBy);
        }
        return map;
    }
}
