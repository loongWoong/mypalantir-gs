package com.mypalantir.sqlparse;

import com.mypalantir.sqlparse.ComplexSqlStructureAnalyzer.SqlLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LEFT JOIN 报表指标处理器
 * 职责：专门处理 LEFT JOIN 多表聚合的复杂报表，完整解析每个指标的链路
 */
@Component
public class ReportJoinMetricHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReportJoinMetricHandler.class);

    private final CaseExpressionParser caseExpressionParser;
    private final CalciteSqlParser calciteSqlParser;

    public ReportJoinMetricHandler(CaseExpressionParser caseExpressionParser, CalciteSqlParser calciteSqlParser) {
        this.caseExpressionParser = caseExpressionParser;
        this.calciteSqlParser = calciteSqlParser;
    }

    /**
     * 判断是否为 LEFT JOIN 报表结构
     */
    public boolean isJoinReportStructure(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        String sqlUpper = sql.toUpperCase();

        boolean hasLeftJoin = sqlUpper.contains("LEFT JOIN") || sqlUpper.contains("LEFT OUTER JOIN");
        boolean hasSubQueryInFrom = sql.contains("(") && sql.contains("SELECT");

        int joinCount = countOccurrences(sqlUpper, " JOIN ");
        int subQueryDepth = calculateSubQueryDepth(sql);

        logger.info("[isJoinReportStructure] 检测: hasLeftJoin={}, hasSubQueryInFrom={}, joinCount={}, subQueryDepth={}",
            hasLeftJoin, hasSubQueryInFrom, joinCount, subQueryDepth);

        return hasLeftJoin && hasSubQueryInFrom && joinCount >= 1;
    }

    /**
     * 处理 LEFT JOIN 报表结构，提取完整指标信息
     */
    public JoinReportMetrics processJoinReport(String sql) {
        logger.info("[processJoinReport] 开始处理 LEFT JOIN 报表 SQL");
        logger.info("[processJoinReport] SQL长度: {}", sql.length());

        JoinReportMetrics result = new JoinReportMetrics();
        result.setOriginalSql(sql);

        try {
            List<JoinTableInfo> tableInfos = parseJoinStructure(sql);
            logger.info("[processJoinReport] 解析到 {} 个关联表", tableInfos.size());

            List<ExtractedMetric> allMetrics = new ArrayList<>();

            for (JoinTableInfo tableInfo : tableInfos) {
                logger.info("[processJoinReport] 处理表: alias={}, type={}", tableInfo.getAlias(), tableInfo.getTableType());

                List<ExtractedMetric> tableMetrics = extractMetricsFromSubQuery(
                    tableInfo.getSubQuery(),
                    tableInfo.getAlias(),
                    tableInfo.getTableType(),
                    tableInfo.getOriginalExpression()
                );

                logger.info("[processJoinReport] 从表 {} 提取到 {} 个指标", tableInfo.getAlias(), tableMetrics.size());
                allMetrics.addAll(tableMetrics);

                result.getTableMetrics().put(tableInfo.getAlias(), tableMetrics);
            }

            List<ExtractedMetric> joinMetrics = extractJoinMetrics(sql, tableInfos, result.getTableMetrics());
            logger.info("[processJoinReport] 提取到 {} 个关联层指标", joinMetrics.size());
            allMetrics.addAll(joinMetrics);

            result.setAllMetrics(allMetrics);
            result.setJoinTables(tableInfos);

            logger.info("[processJoinReport] 处理完成，总计 {} 个指标", allMetrics.size());

        } catch (Exception e) {
            logger.error("[processJoinReport] 处理失败: {}", e.getMessage(), e);
            result.setError("处理失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 解析 LEFT JOIN 结构
     */
    private List<JoinTableInfo> parseJoinStructure(String sql) {
        List<JoinTableInfo> tableInfos = new ArrayList<>();

        String cleanedSql = cleanSql(sql);
        logger.info("[parseJoinStructure] 清理后的SQL: {}", cleanedSql.substring(0, Math.min(200, cleanedSql.length())));

        Pattern fromPattern = Pattern.compile(
            "FROM\\s*\\((.+?)\\s*\\)\\s*(\\w+)\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher fromMatcher = fromPattern.matcher(cleanedSql);

        if (fromMatcher.find()) {
            String firstSubQuery = fromMatcher.group(1);
            String firstAlias = fromMatcher.group(2).trim();

            JoinTableInfo leftInfo = new JoinTableInfo();
            leftInfo.setAlias(firstAlias);
            leftInfo.setTableType("LEFT");
            leftInfo.setSubQuery(firstSubQuery);
            leftInfo.setOriginalExpression(fromMatcher.group(0));
            leftInfo.setJoinCondition(null);
            tableInfos.add(leftInfo);

            logger.info("[parseJoinStructure] 左表: alias={}, 子查询长度={}", firstAlias, firstSubQuery.length());

            String remainingSql = cleanedSql.substring(fromMatcher.end());

            Pattern joinPattern = Pattern.compile(
                "(LEFT\\s*(OUTER)?\\s*JOIN|INNER\\s*JOIN|RIGHT\\s*(OUTER)?\\s*JOIN)\\s*\\((.+?)\\s*\\)\\s*(\\w+)\\s*ON\\s*(.+?)(?=\\s*(?:LEFT\\s*(?:OUTER)?\\s*JOIN|INNER\\s*JOIN|RIGHT\\s*(OUTER)?\\s*JOIN|ORDER|GROUP|LIMIT|$))",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            Matcher joinMatcher = joinPattern.matcher(remainingSql);

            int tableIndex = 1;
            while (joinMatcher.find()) {
                String joinType = joinMatcher.group(1);
                String subQuery = joinMatcher.group(4);
                String alias = joinMatcher.group(5);
                String onCondition = joinMatcher.group(6);

                JoinTableInfo tableInfo = new JoinTableInfo();
                tableInfo.setAlias(alias);
                tableInfo.setTableType(joinType.toUpperCase().contains("LEFT") ? "RIGHT" : joinType.toUpperCase());
                tableInfo.setSubQuery(subQuery);
                tableInfo.setOriginalExpression(joinMatcher.group(0));
                tableInfo.setJoinCondition(onCondition.trim());
                tableInfos.add(tableInfo);

                logger.info("[parseJoinStructure] 右表{}: alias={}, type={}, 子查询长度={}",
                    tableIndex, alias, tableInfo.getTableType(), subQuery.length());

                tableIndex++;
            }
        }

        return tableInfos;
    }

    /**
     * 从子查询中提取指标
     */
    private List<ExtractedMetric> extractMetricsFromSubQuery(
            String subQuery,
            String tableAlias,
            String tableType,
            String originalExpression) {

        List<ExtractedMetric> metrics = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        logger.info("[extractMetricsFromSubQuery] 开始提取子查询指标, alias={}", tableAlias);

        CalciteSqlParseResult parseResult = calciteSqlParser.parse(subQuery);

        for (CalciteSqlParseResult.AggregationInfo agg : parseResult.getAggregations()) {
            String field = agg.getField();
            if (field == null || field.trim().isEmpty() || field.trim().equals("*")) {
                continue;
            }

            String alias = agg.getAlias() != null ? agg.getAlias() : field;
            String metricName = alias.toLowerCase();

            if (addedNames.contains(metricName)) {
                continue;
            }

            String cleanField = cleanFieldName(field);
            String cleanAlias = cleanFieldName(alias);

            ExtractedMetric metric = new ExtractedMetric();
            metric.setId(UUID.randomUUID().toString());
            metric.setName(cleanAlias);
            metric.setDisplayName(formatDisplayName(cleanAlias));
            metric.setCategory(ExtractedMetric.MetricCategory.ATOMIC);
            metric.setAggregationFunction(agg.getType());
            metric.setAggregationField(cleanField);
            metric.setSourceSql(agg.getExpression());

            String sourceTable = tableType.equals("LEFT") ? "A" : "B";
            metric.setDescription(String.format("原子指标: %s表(%s)的%s(%s)",
                tableType, tableAlias, agg.getType(), cleanField));
            metric.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);

            Map<String, Object> sourceInfo = new HashMap<>();
            sourceInfo.put("tableAlias", tableAlias);
            sourceInfo.put("tableType", tableType);
            sourceInfo.put("sourceTable", sourceTable);
            sourceInfo.put("originalExpression", originalExpression);
            metric.setNotes(Collections.singletonList(sourceInfo.toString()));

            metrics.add(metric);
            addedNames.add(metricName);

            logger.info("[extractMetricsFromSubQuery] 添加原子指标: name={}, aggFunc={}, aggField={}, tableType={}",
                cleanAlias, agg.getType(), cleanField, tableType);
        }

        logger.info("[extractMetricsFromSubQuery] 提取完成, 共 {} 个指标", metrics.size());
        return metrics;
    }

    /**
     * 提取关联层的指标（最外层 SELECT 中的 IFNULL、CASE WHEN 等）
     */
    private List<ExtractedMetric> extractJoinMetrics(
            String sql,
            List<JoinTableInfo> tableInfos,
            Map<String, List<ExtractedMetric>> tableMetricsMap) {

        List<ExtractedMetric> joinMetrics = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        logger.info("[extractJoinMetrics] 开始提取关联层指标");

        Pattern selectPattern = Pattern.compile(
            "SELECT\\s+(.+?)\\s+FROM",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher selectMatcher = selectPattern.matcher(sql);

        if (!selectMatcher.find()) {
            logger.warn("[extractJoinMetrics] 无法找到 SELECT 子句");
            return joinMetrics;
        }

        String selectClause = selectMatcher.group(1).trim();
        String[] selectFields = splitSelectFields(selectClause);

        logger.info("[extractJoinMetrics] SELECT 字段数: {}", selectFields.length);

        for (String field : selectFields) {
            String trimmedField = field.trim();
            if (trimmedField.isEmpty()) {
                continue;
            }

            String alias = extractAlias(trimmedField);
            String expression = trimmedField.replaceAll("(?i)\\s+AS\\s+[\\w_]+$", "").trim();

            if (alias == null || alias.isEmpty()) {
                alias = guessAliasFromExpression(expression);
            }

            if (alias == null) {
                continue;
            }

            String metricName = alias.toLowerCase();
            if (addedNames.contains(metricName)) {
                continue;
            }

            ExtractedMetric metric = parseJoinExpression(expression, alias, tableInfos, tableMetricsMap);
            if (metric != null) {
                joinMetrics.add(metric);
                addedNames.add(metricName);
                logger.info("[extractJoinMetrics] 添加关联层指标: name={}, category={}",
                    metricName, metric.getCategory());
            }
        }

        logger.info("[extractJoinMetrics] 提取完成, 共 {} 个指标", joinMetrics.size());
        return joinMetrics;
    }

    /**
     * 解析关联层的表达式（IFNULL, CASE WHEN 等）
     */
    private ExtractedMetric parseJoinExpression(
            String expression,
            String alias,
            List<JoinTableInfo> tableInfos,
            Map<String, List<ExtractedMetric>> tableMetricsMap) {

        ExtractedMetric metric = new ExtractedMetric();
        metric.setId(UUID.randomUUID().toString());
        metric.setName(alias.toLowerCase());
        metric.setDisplayName(formatDisplayName(alias));
        metric.setSourceSql(expression);
        metric.setConfidence(ExtractedMetric.ConfidenceLevel.HIGH);

        expression = expression.trim();

        if (expression.toUpperCase().startsWith("IFNULL")) {
            return parseIfNullExpression(metric, expression, alias, tableInfos, tableMetricsMap);
        } else if (expression.toUpperCase().startsWith("CASE")) {
            return parseCaseWhenExpression(metric, expression, alias, tableInfos, tableMetricsMap);
        } else if (expression.contains("SUM(") || expression.contains("COUNT(")) {
            return parseAggregationExpression(metric, expression, alias, tableInfos, tableMetricsMap);
        } else if (expression.matches("^[A-Z][\\w_]+$")) {
            return parseSimpleFieldExpression(metric, expression, alias, tableInfos, tableMetricsMap);
        }

        logger.warn("[parseJoinExpression] 无法解析表达式: {}", expression);
        return null;
    }

    /**
     * 解析 IFNULL 表达式
     */
    private ExtractedMetric parseIfNullExpression(
            ExtractedMetric metric,
            String expression,
            String alias,
            List<JoinTableInfo> tableInfos,
            Map<String, List<ExtractedMetric>> tableMetricsMap) {

        Pattern ifnullPattern = Pattern.compile(
            "IFNULL\\s*\\(\\s*(.+?)\\s*,\\s*(.+?)\\s*\\)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = ifnullPattern.matcher(expression);

        if (matcher.find()) {
            String innerExpr = matcher.group(1).trim();
            String defaultValue = matcher.group(2).trim();

            logger.info("[parseIfNullExpression] IFNULL解析: inner={}, default={}", innerExpr, defaultValue);

            if ("0".equals(defaultValue) || "0.00".equals(defaultValue)) {
                metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
                metric.setDescription("派生指标: IFNULL(值, 0) - 空值填充为0");

                Map<String, Object> filterConditions = new HashMap<>();
                filterConditions.put("_is_null_filled", true);
                filterConditions.put("_null_default_value", cleanValue(defaultValue));
                metric.setFilterConditions(filterConditions);

                String referencedMetric = findReferencedMetric(innerExpr, tableMetricsMap);
                if (referencedMetric != null) {
                    metric.setAtomicMetricId(referencedMetric);
                    metric.setAggregationFunction("SUM");
                    metric.setAggregationField(innerExpr);
                }
            } else {
                metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
                metric.setDescription("派生指标: IFNULL(值, " + defaultValue + ")");
            }

            metric.setDerivedFormula(expression);
        }

        return metric;
    }

    /**
     * 解析 CASE WHEN 表达式
     */
    private ExtractedMetric parseCaseWhenExpression(
            ExtractedMetric metric,
            String expression,
            String alias,
            List<JoinTableInfo> tableInfos,
            Map<String, List<ExtractedMetric>> tableMetricsMap) {

        metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
        metric.setDerivedFormula(expression);

        CaseExpressionParser.CaseStructure caseStructure = caseExpressionParser.parseCaseExpression(expression);
        if (caseStructure != null) {
            String meaning = caseExpressionParser.extractBusinessMeaning(caseStructure);
            metric.setDescription("派生指标: " + meaning);

            Map<String, Object> filterConditions = caseExpressionParser.convertToFilterConditions(caseStructure);
            if (!filterConditions.isEmpty()) {
                metric.setFilterConditions(filterConditions);
            }
        } else {
            metric.setDescription("派生指标: CASE WHEN 条件分支");
        }

        return metric;
    }

    /**
     * 解析聚合表达式（如 SUM(CASE WHEN ...)）
     */
    private ExtractedMetric parseAggregationExpression(
            ExtractedMetric metric,
            String expression,
            String alias,
            List<JoinTableInfo> tableInfos,
            Map<String, List<ExtractedMetric>> tableMetricsMap) {

        metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
        metric.setDerivedFormula(expression);

        String aggFunc = expression.toUpperCase().startsWith("SUM(") ? "SUM" :
                        expression.toUpperCase().startsWith("COUNT(") ? "COUNT" :
                        expression.toUpperCase().startsWith("AVG(") ? "AVG" : "SUM";

        metric.setAggregationFunction(aggFunc);
        metric.setAggregationField(expression);
        metric.setDescription("派生指标: " + aggFunc + "(CASE WHEN ...) 条件聚合");

        Map<String, Object> filterConditions = new HashMap<>();
        filterConditions.put("_has_condition", true);
        metric.setFilterConditions(filterConditions);

        return metric;
    }

    /**
     * 解析简单字段引用
     */
    private ExtractedMetric parseSimpleFieldExpression(
            ExtractedMetric metric,
            String expression,
            String alias,
            List<JoinTableInfo> tableInfos,
            Map<String, List<ExtractedMetric>> tableMetricsMap) {

        metric.setCategory(ExtractedMetric.MetricCategory.DERIVED);
        metric.setSourceSql(expression);
        metric.setDescription("派生指标: 直接引用字段 " + expression);

        String referencedMetric = findReferencedMetric(expression, tableMetricsMap);
        if (referencedMetric != null) {
            metric.setAtomicMetricId(referencedMetric);
        }

        return metric;
    }

    /**
     * 从表达式中查找引用的指标
     */
    private String findReferencedMetric(String expression, Map<String, List<ExtractedMetric>> tableMetricsMap) {
        String cleanExpr = cleanFieldName(expression).toLowerCase();

        for (List<ExtractedMetric> metrics : tableMetricsMap.values()) {
            for (ExtractedMetric metric : metrics) {
                String metricName = metric.getName().toLowerCase();
                if (cleanExpr.contains(metricName) || metricName.contains(cleanExpr)) {
                    return metric.getId();
                }
            }
        }

        return null;
    }

    /**
     * 生成还原 SQL（根据指标定义重建原始 SQL）
     */
    public String generateRestoreSql(ExtractedMetric metric, List<JoinTableInfo> tableInfos) {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT\n    ");

        List<String> selectParts = new ArrayList<>();

        if (metric.getCategory() == ExtractedMetric.MetricCategory.DERIVED) {
            if (metric.getDerivedFormula() != null) {
                selectParts.add(metric.getDerivedFormula() + " AS " + metric.getName());
            } else if (metric.getAtomicMetricId() != null) {
                selectParts.add("SUM(" + metric.getAggregationField() + ") AS " + metric.getName());
            }
        } else {
            selectParts.add(metric.getAggregationFunction() + "(" + metric.getAggregationField() + ") AS " + metric.getName());
        }

        sql.append(String.join(",\n    ", selectParts));

        sql.append("\nFROM (\n    ");
        if (!tableInfos.isEmpty()) {
            sql.append(tableInfos.get(0).getSubQuery());
        }
        sql.append("\n) A\n");

        for (int i = 1; i < tableInfos.size(); i++) {
            JoinTableInfo info = tableInfos.get(i);
            sql.append(info.getTableType().toUpperCase().replace("OUTER", "") + " JOIN (\n    ");
            sql.append(info.getSubQuery());
            sql.append("\n) ").append(info.getAlias()).append(" ON ").append(info.getJoinCondition());
            sql.append("\n");
        }

        if (metric.getFilterConditions() != null && !metric.getFilterConditions().isEmpty()) {
            sql.append("\nWHERE 1=1");
            for (Map.Entry<String, Object> entry : metric.getFilterConditions().entrySet()) {
                if (!entry.getKey().startsWith("_")) {
                    sql.append("\n    AND ").append(entry.getKey()).append(" = ").append(formatValue(entry.getValue()));
                }
            }
        }

        return sql.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String) {
            return "'" + value + "'";
        }
        return String.valueOf(value);
    }

    private String cleanSql(String sql) {
        String cleaned = sql.replaceAll("(?i)\\s+", " ");
        cleaned = cleaned.replaceAll("\n", " ");
        cleaned = cleaned.replaceAll("\t", " ");
        return cleaned.trim();
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private int calculateSubQueryDepth(String sql) {
        int maxDepth = 0;
        int currentDepth = 0;
        for (char c : sql.toCharArray()) {
            if (c == '(') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == ')') {
                currentDepth--;
            }
        }
        return Math.max(0, maxDepth / 2);
    }

    private String[] splitSelectFields(String selectClause) {
        List<String> fields = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < selectClause.length(); i++) {
            char c = selectClause.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                fields.add(selectClause.substring(start, i).trim());
                start = i + 1;
            }
        }

        if (start < selectClause.length()) {
            fields.add(selectClause.substring(start).trim());
        }

        return fields.toArray(new String[0]);
    }

    private String extractAlias(String selectField) {
        if (selectField == null) return null;

        Pattern pattern = Pattern.compile("(?i)\\s+AS\\s+([\\w_]+)\\s*$");
        Matcher matcher = pattern.matcher(selectField.trim());

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private String guessAliasFromExpression(String expression) {
        String clean = cleanFieldName(expression);

        if (clean.contains(".")) {
            clean = clean.substring(clean.lastIndexOf('.') + 1);
        }

        if (clean.matches("^[A-Z][A-Z0-9_]+$")) {
            return clean.toLowerCase();
        }

        return null;
    }

    private String cleanFieldName(String field) {
        if (field == null) return "";

        String cleaned = field.trim();

        cleaned = cleaned.replaceAll("(?i)IFNULL\\s*\\([^,]+,\\s*[^)]+\\)\\s*", "");
        cleaned = cleaned.replaceAll("(?i)COALESCE\\s*\\([^)]+\\)\\s*", "");
        cleaned = cleaned.replaceAll("(?i)SUM\\s*\\([^)]+\\)\\s*", "");
        cleaned = cleaned.replaceAll("(?i)COUNT\\s*\\([^)]+\\)\\s*", "");
        cleaned = cleaned.replaceAll("(?i)AVG\\s*\\([^)]+\\)\\s*", "");
        cleaned = cleaned.replaceAll("`", "");
        cleaned = cleaned.replaceAll("[\"']", "");

        cleaned = cleaned.replaceAll("^\\w+\\.", "");
        cleaned = cleaned.replaceAll("\\s+AS\\s+\\w+$", "");

        return cleaned.trim();
    }

    private String cleanValue(String value) {
        if (value == null) return "";
        return value.replaceAll("^['\"]", "").replaceAll("['\"]$", "").trim();
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

    public static class JoinTableInfo {
        private String alias;
        private String tableType;
        private String subQuery;
        private String originalExpression;
        private String joinCondition;

        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public String getTableType() { return tableType; }
        public void setTableType(String tableType) { this.tableType = tableType; }
        public String getSubQuery() { return subQuery; }
        public void setSubQuery(String subQuery) { this.subQuery = subQuery; }
        public String getOriginalExpression() { return originalExpression; }
        public void setOriginalExpression(String originalExpression) { this.originalExpression = originalExpression; }
        public String getJoinCondition() { return joinCondition; }
        public void setJoinCondition(String joinCondition) { this.joinCondition = joinCondition; }
    }

    public static class JoinReportMetrics {
        private String originalSql;
        private List<ExtractedMetric> allMetrics = new ArrayList<>();
        private List<JoinTableInfo> joinTables = new ArrayList<>();
        private Map<String, List<ExtractedMetric>> tableMetrics = new HashMap<>();
        private String error;

        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public List<ExtractedMetric> getAllMetrics() { return allMetrics; }
        public void setAllMetrics(List<ExtractedMetric> allMetrics) { this.allMetrics = allMetrics; }
        public List<JoinTableInfo> getJoinTables() { return joinTables; }
        public void setJoinTables(List<JoinTableInfo> joinTables) { this.joinTables = joinTables; }
        public Map<String, List<ExtractedMetric>> getTableMetrics() { return tableMetrics; }
        public void setTableMetrics(Map<String, List<ExtractedMetric>> tableMetrics) { this.tableMetrics = tableMetrics; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
