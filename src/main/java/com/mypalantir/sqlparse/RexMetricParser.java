package com.mypalantir.sqlparse;

import com.mypalantir.sqlparse.RexNodeLineageExtractor.*;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rex 指标解析器
 * 职责：基于 RexNode 血缘信息提取指标，替换原有的正则解析方案
 */
@Component
public class RexMetricParser {

    private static final Logger logger = LoggerFactory.getLogger(RexMetricParser.class);

    private final RexNodeLineageExtractor lineageExtractor;

    public RexMetricParser(RexNodeLineageExtractor lineageExtractor) {
        this.lineageExtractor = lineageExtractor;
    }

    public RexMetricParseResult parse(String sql) {
        logger.info("[RexMetricParser] 开始解析 SQL");
        return lineageExtractor.parse(sql);
    }

    public List<ExtractedMetric> extractMetrics(RexMetricParseResult parseResult) {
        logger.info("[RexMetricParser] 从解析结果提取指标");
        List<ExtractedMetric> metrics = new ArrayList<>();

        if (parseResult.getError() != null) {
            logger.warn("[RexMetricParser] 解析结果有错误: {}", parseResult.getError());
            return metrics;
        }

        for (ColumnLineage lineage : parseResult.getColumnLineages()) {
            ExtractedMetric metric = convertLineageToMetric(lineage, parseResult.getOriginalSql());
            if (metric != null) {
                metrics.add(metric);
                logger.info("[RexMetricParser] 添加指标: name={}, type={}, sources={}",
                    metric.getName(), metric.getCategory(), metric.getSources().size());
            }
        }

        logger.info("[RexMetricParser] 共提取 {} 个指标", metrics.size());
        return metrics;
    }

    private ExtractedMetric convertLineageToMetric(ColumnLineage lineage, String originalSql) {
        ExtractedMetric metric = new ExtractedMetric();
        metric.setId(UUID.randomUUID().toString());

        String metricName = inferMetricName(lineage);
        metric.setName(metricName);
        metric.setDisplayName(formatDisplayName(metricName));

        metric.setSourceSql(lineage.getRexNode() != null ? lineage.getRexNode().toString() : lineage.getOutputName());
        metric.setCategory(determineMetricType(lineage));
        metric.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);

        if (lineage.getAggregationFunction() != null) {
            metric.setAggregationFunction(convertAggregationFunction(lineage.getAggregationFunction()));
            if (!lineage.getSources().isEmpty()) {
                metric.setAggregationField(lineage.getSources().get(0).getSourceColumn());
            }
        }

        if (lineage.getTransformType() != null) {
            switch (lineage.getTransformType()) {
                case "IFNULL":
                    metric.setDescription("派生指标: IFNULL 空值填充");
                    metric.setFilterConditions(parseIfnullCondition(lineage.getRexNode()));
                    break;
                case "CASE":
                    metric.setDescription("派生指标: CASE WHEN 条件分支");
                    break;
                case "AGGREGATION":
                    metric.setDescription("派生指标: " + lineage.getAggregationFunction() + " 聚合");
                    break;
            }
        } else if (lineage.getSources().size() == 1 && lineage.getSources().get(0).getTransformations().isEmpty()) {
            metric.setDescription("原子指标: 直接引用 " + lineage.getSources().get(0).getSourceTable() + "." + lineage.getSources().get(0).getSourceColumn());
        }

        metric.setSources(convertToMetricSources(lineage.getSources()));

        return metric;
    }

    private String inferMetricName(ColumnLineage lineage) {
        String outputName = lineage.getOutputName();
        if (outputName != null && !outputName.isEmpty()) {
            return outputName.toLowerCase();
        }

        if (lineage.getRexNode() != null) {
            String expr = lineage.getRexNode().toString();
            if (expr.contains(".")) {
                return expr.substring(expr.lastIndexOf('.') + 1).toLowerCase();
            }
        }

        return "metric_" + lineage.getOutputIndex();
    }

    private ExtractedMetric.MetricCategory determineMetricType(ColumnLineage lineage) {
        if (lineage.getAggregationFunction() != null) {
            return ExtractedMetric.MetricCategory.DERIVED;
        }
        if (lineage.getTransformType() != null) {
            return ExtractedMetric.MetricCategory.DERIVED;
        }
        if (lineage.getSources().size() > 1) {
            return ExtractedMetric.MetricCategory.COMPOSITE;
        }
        return ExtractedMetric.MetricCategory.ATOMIC;
    }

    private String convertAggregationFunction(String calciteFunc) {
        if (calciteFunc == null) return "SUM";
        String upper = calciteFunc.toUpperCase();
        if (upper.contains("SUM")) return "SUM";
        if (upper.contains("COUNT")) return "COUNT";
        if (upper.contains("AVG")) return "AVG";
        if (upper.contains("MAX")) return "MAX";
        if (upper.contains("MIN")) return "MIN";
        return "SUM";
    }

    private Map<String, Object> parseIfnullCondition(RexNode rexNode) {
        Map<String, Object> conditions = new HashMap<>();
        if (rexNode == null) return conditions;

        String expr = rexNode.toString();
        Pattern pattern = Pattern.compile("IFNULL\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(expr);

        if (matcher.find()) {
            conditions.put("_is_null_filled", true);
            conditions.put("_null_default_value", matcher.group(2).trim());
            conditions.put("_inner_expression", matcher.group(1).trim());
        }

        return conditions;
    }

    private List<ExtractedMetric.ColumnSource> convertToMetricSources(List<ColumnSource> sources) {
        return sources.stream().map(s -> {
            ExtractedMetric.ColumnSource source = new ExtractedMetric.ColumnSource();
            source.setSourceTable(s.getSourceTable());
            source.setSourceColumn(s.getSourceColumn());
            source.setColumnOrdinal(s.getColumnOrdinal());
            source.setFullLineage(s.getFullLineage());
            return source;
        }).collect(Collectors.toList());
    }

    private String formatDisplayName(String name) {
        if (name == null) return "";
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        return result.toString();
    }

    public String generateRestoreSql(ExtractedMetric metric, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT\n    ");

        if (metric.getAggregationFunction() != null && metric.getAggregationField() != null) {
            sql.append(metric.getAggregationFunction()).append("(");
            sql.append(metric.getAggregationField()).append(") AS ").append(metric.getName());
        } else if (metric.getSourceSql() != null) {
            sql.append(metric.getSourceSql()).append(" AS ").append(metric.getName());
        } else {
            sql.append(metric.getName());
        }

        sql.append("\nFROM ").append(tableName);

        if (metric.getFilterConditions() != null && !metric.getFilterConditions().isEmpty()) {
            sql.append("\nWHERE 1=1");
            for (Map.Entry<String, Object> entry : metric.getFilterConditions().entrySet()) {
                if (!entry.getKey().startsWith("_")) {
                    sql.append("\n    AND ").append(entry.getKey()).append(" = ");
                    if (entry.getValue() instanceof String) {
                        sql.append("'").append(entry.getValue()).append("'");
                    } else {
                        sql.append(entry.getValue());
                    }
                }
            }
        }

        return sql.toString();
    }
}
