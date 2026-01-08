package com.mypalantir.sqlparse;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * SQL 语法解析器
 * 从 SQL 查询语句中提取表名、字段、聚合函数、WHERE 条件等关键元素
 */
@Component
public class SqlParser {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "\\bSELECT\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FROM_PATTERN = Pattern.compile(
        "\\bFROM\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WHERE_PATTERN = Pattern.compile(
        "\\bWHERE\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile(
        "\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
        "\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
        "\\bLIMIT\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JOIN_PATTERN = Pattern.compile(
        "\\b(INNER|LEFT|RIGHT|FULL|CROSS)?\\s*JOIN\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ON_PATTERN = Pattern.compile(
        "\\bON\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AGGREGATION_PATTERN = Pattern.compile(
        "(SUM|AVG|COUNT|MAX|MIN|COUNT_DISTINCT)\\s*\\(\\s*(?:DISTINCT\\s+)?([^)]+)\\)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ALIAS_PATTERN = Pattern.compile(
        "(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
        "([a-zA-Z_][a-zA-Z0-9_]*)"
    );
    private static final Pattern TABLE_ALIAS_PATTERN = Pattern.compile(
        "([a-zA-Z_][a-zA-Z0-9_]*)\\s+AS\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TIME_FUNCTION_PATTERN = Pattern.compile(
        "(DATE|YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|WEEK|QUARTER|DAYOFWEEK|DAYOFMONTH|DAYOFYEAR)\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
        "([a-zA-Z_][a-zA-Z0-9_.`]*)\\s*(=|!=|<>|>=|<=|>|<|LIKE|IN|NOT\\s+IN|BETWEEN|NOT\\s+BETWEEN|IS\\s+NULL|IS\\s+NOT\\s+NULL)\\s*(.+)"
    );
    private static final Pattern AND_OR_PATTERN = Pattern.compile(
        "\\b(AND|OR)\\b", Pattern.CASE_INSENSITIVE
    );

    /**
     * 解析 SQL 查询语句
     */
    public SqlParseResult parse(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 语句不能为空");
        }

        SqlParseResult result = new SqlParseResult();
        result.setOriginalSql(sql);
        result.setNormalizedSql(normalizeSql(sql));

        String normalizedSql = result.getNormalizedSql();

        // 提取 SELECT 子句
        result.setSelectFields(extractSelectFields(normalizedSql));

        // 提取 FROM 子句和表信息
        extractFromAndTables(normalizedSql, result);

        // 提取 JOIN 信息
        result.setJoins(extractJoins(normalizedSql));

        // 提取 WHERE 条件
        result.setWhereConditions(extractWhereConditions(normalizedSql));

        // 提取时间条件
        result.setTimeConditions(extractTimeConditions(result.getWhereConditions()));

        // 提取 GROUP BY 子句
        extractGroupBy(normalizedSql, result);

        // 提取聚合信息
        result.setAggregations(extractAggregations(result.getSelectFields()));

        // 提取 ORDER BY 和 LIMIT
        extractOrderByAndLimit(normalizedSql, result);

        return result;
    }

    /**
     * 标准化 SQL 语句（移除多余空格、转小写等）
     */
    private String normalizeSql(String sql) {
        String normalized = sql.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    /**
     * 提取 SELECT 子句中的字段
     */
    private List<SelectField> extractSelectFields(String sql) {
        List<SelectField> fields = new ArrayList<>();

        int selectStart = findClauseStart(sql, SELECT_PATTERN);
        int fromStart = findClauseStart(sql, FROM_PATTERN);

        if (selectStart == -1 || fromStart == -1) {
            return fields;
        }

        String selectClause = sql.substring(selectStart + 6, fromStart).trim();
        String[] fieldParts = splitSelectClause(selectClause);

        for (String part : fieldParts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            SelectField field = parseSelectField(part);
            if (field != null) {
                fields.add(field);
            }
        }

        return fields;
    }

    /**
     * 解析单个 SELECT 字段
     */
    private SelectField parseSelectField(String expression) {
        SelectField field = new SelectField();
        field.setRawExpression(expression);

        // 检查是否有别名
        String alias = extractAlias(expression);
        field.setAlias(alias);

        // 提取表别名
        String tableAlias = extractTableAlias(expression);
        field.setTableAlias(tableAlias);

        // 检查是否为聚合函数
        Matcher aggMatcher = AGGREGATION_PATTERN.matcher(expression);
        if (aggMatcher.find()) {
            field.setAggregated(true);
            field.setAggregationType(parseAggregationType(aggMatcher.group(1)));
            String fieldContent = aggMatcher.group(2).trim();
            if (!"*".equals(fieldContent)) {
                field.setFieldName(extractFieldName(fieldContent));
            }
        } else {
            field.setAggregated(false);
            field.setAggregationType(AggregationType.NONE);
            // 尝试提取字段名
            String fieldName = extractFieldName(expression);
            if (fieldName != null && !fieldName.equals("*")) {
                field.setFieldName(fieldName);
            }
        }

        // 判断字段类型
        field.setFieldType(determineFieldType(expression));

        return field;
    }

    /**
     * 分割 SELECT 子句（处理括号和逗号）
     */
    private String[] splitSelectClause(String selectClause) {
        List<String> parts = new ArrayList<>();
        int parenDepth = 0;
        int lastSplit = 0;

        for (int i = 0; i < selectClause.length(); i++) {
            char c = selectClause.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == ',' && parenDepth == 0) {
                parts.add(selectClause.substring(lastSplit, i));
                lastSplit = i + 1;
            }
        }

        if (lastSplit < selectClause.length()) {
            parts.add(selectClause.substring(lastSplit));
        }

        return parts.toArray(new String[0]);
    }

    /**
     * 提取 FROM 子句和表信息
     */
    private void extractFromAndTables(String sql, SqlParseResult result) {
        List<TableReference> tables = new ArrayList<>();

        int fromStart = findClauseStart(sql, FROM_PATTERN);
        int whereStart = findClauseStart(sql, WHERE_PATTERN);
        int groupByStart = findClauseStart(sql, GROUP_BY_PATTERN);
        int orderByStart = findClauseStart(sql, ORDER_BY_PATTERN);
        int limitStart = findClauseStart(sql, LIMIT_PATTERN);

        int clauseEnd;
        if (whereStart != -1) clauseEnd = whereStart;
        else if (groupByStart != -1) clauseEnd = groupByStart;
        else if (orderByStart != -1) clauseEnd = orderByStart;
        else if (limitStart != -1) clauseEnd = limitStart;
        else clauseEnd = sql.length();

        String fromClause = sql.substring(fromStart + 4, clauseEnd).trim();
        fromClause = removeOrderByFromJoin(fromClause);

        String[] tableParts = splitFromClause(fromClause);

        for (int i = 0; i < tableParts.length; i++) {
            String part = tableParts[i].trim();
            if (part.isEmpty()) continue;

            TableReference table = parseTableReference(part, i == 0);
            if (table != null) {
                tables.add(table);
            }
        }

        result.setTables(tables);
    }

    /**
     * 移除 FROM 子句中 ORDER BY 后面可能的部分
     */
    private String removeOrderByFromJoin(String fromClause) {
        Pattern orderPattern = Pattern.compile("\\s+ORDER\\s+BY\\s+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = orderPattern.matcher(fromClause);
        if (matcher.find()) {
            return fromClause.substring(0, matcher.start()).trim();
        }
        return fromClause;
    }

    /**
     * 分割 FROM 子句
     */
    private String[] splitFromClause(String fromClause) {
        List<String> parts = new ArrayList<>();
        int parenDepth = 0;
        int lastSplit = 0;

        for (int i = 0; i < fromClause.length(); i++) {
            char c = fromClause.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == ',' && parenDepth == 0) {
                parts.add(fromClause.substring(lastSplit, i));
                lastSplit = i + 1;
            }
        }

        if (lastSplit < fromClause.length()) {
            parts.add(fromClause.substring(lastSplit));
        }

        return parts.toArray(new String[0]);
    }

    /**
     * 解析表引用
     */
    private TableReference parseTableReference(String expression, boolean isMainTable) {
        TableReference table = new TableReference();
        table.setMainTable(isMainTable);

        // 移除 JOIN 关键字（如果存在）
        expression = expression.replaceAll("(?i)^\\s*(INNER|LEFT|RIGHT|FULL|CROSS)?\\s*JOIN\\s+", "").trim();

        // 检查是否有 AS 别名
        Matcher aliasMatcher = TABLE_ALIAS_PATTERN.matcher(expression);
        String tableName;
        if (aliasMatcher.find()) {
            tableName = aliasMatcher.group(1).trim();
            table.setAlias(aliasMatcher.group(2).trim());
        } else {
            // 简单的表名提取
            String[] words = expression.split("\\s+");
            tableName = words[0].trim();
            if (words.length > 1) {
                table.setAlias(words[1].trim());
            }
        }

        // 移除反引号和点号包围的表名
        tableName = tableName.replaceAll("^`|`$", "").replaceAll("^\"|\"$", "");
        table.setTableName(tableName);

        return table;
    }

    /**
     * 提取 JOIN 信息
     */
    private List<JoinInfo> extractJoins(String sql) {
        List<JoinInfo> joins = new ArrayList<>();

        Matcher joinMatcher = JOIN_PATTERN.matcher(sql);
        int lastEnd = 0;

        while (joinMatcher.find()) {
            JoinInfo join = new JoinInfo();
            join.setJoinType(joinMatcher.group(1) != null ? joinMatcher.group(1).toUpperCase() : "INNER");

            int joinStart = joinMatcher.start();
            String beforeJoin = sql.substring(lastEnd, joinStart).trim();

            // 提取 ON 条件
            int onStart = sql.indexOf(" ON ", joinStart);
            int nextClauseStart = findNextClauseStart(sql, joinStart);

            if (onStart != -1 && (nextClauseStart == -1 || onStart < nextClauseStart)) {
                String joinTable = sql.substring(joinMatcher.end(), onStart).trim();
                join.setJoinedTable(joinTable.replaceAll("(?i)^(INNER|LEFT|RIGHT|FULL|CROSS)?\\s*JOIN\\s+", ""));

                // 提取 ON 条件
                int onEnd = findNextClauseStart(sql, onStart);
                if (onEnd == -1) onEnd = sql.length();
                String onCondition = sql.substring(onStart + 4, onEnd).trim();
                join.setOnCondition(onCondition);
            } else {
                String joinTable = sql.substring(joinMatcher.end(), nextClauseStart != -1 ? nextClauseStart : sql.length()).trim();
                join.setJoinedTable(joinTable);
            }

            joins.add(join);
            lastEnd = joinMatcher.end();
        }

        return joins;
    }

    /**
     * 提取 WHERE 条件
     */
    private List<WhereCondition> extractWhereConditions(String sql) {
        List<WhereCondition> conditions = new ArrayList<>();

        int whereStart = findClauseStart(sql, WHERE_PATTERN);
        if (whereStart == -1) {
            return conditions;
        }

        int groupByStart = findClauseStart(sql, GROUP_BY_PATTERN);
        int orderByStart = findClauseStart(sql, ORDER_BY_PATTERN);
        int limitStart = findClauseStart(sql, LIMIT_PATTERN);
        int clauseEnd;

        if (groupByStart != -1) clauseEnd = groupByStart;
        else if (orderByStart != -1) clauseEnd = orderByStart;
        else if (limitStart != -1) clauseEnd = limitStart;
        else clauseEnd = sql.length();

        String whereClause = sql.substring(whereStart + 5, clauseEnd).trim();
        conditions = parseWhereConditions(whereClause);

        return conditions;
    }

    /**
     * 解析 WHERE 条件
     */
    private List<WhereCondition> parseWhereConditions(String whereClause) {
        List<WhereCondition> conditions = new ArrayList<>();

        // 分割条件（处理括号和 AND/OR）
        List<ConditionToken> tokens = tokenizeWhereClause(whereClause);

        WhereCondition currentCondition = new WhereCondition();
        StringBuilder currentExpr = new StringBuilder();

        for (int i = 0; i < tokens.size(); i++) {
            ConditionToken token = tokens.get(i);

            if (token.getType() == ConditionTokenType.FIELD ||
                token.getType() == ConditionTokenType.VALUE) {
                currentExpr.append(token.getText()).append(" ");
            } else if (token.getType() == ConditionTokenType.OPERATOR) {
                currentExpr.append(token.getText()).append(" ");
            } else if (token.getType() == ConditionTokenType.AND) {
                if (currentExpr.length() > 0) {
                    currentCondition = parseSingleCondition(currentExpr.toString().trim());
                    if (currentCondition != null) {
                        currentCondition.setLogicalOperator("AND");
                        conditions.add(currentCondition);
                    }
                    currentExpr = new StringBuilder();
                }
            } else if (token.getType() == ConditionTokenType.OR) {
                if (currentExpr.length() > 0) {
                    currentCondition = parseSingleCondition(currentExpr.toString().trim());
                    if (currentCondition != null) {
                        currentCondition.setLogicalOperator("OR");
                        conditions.add(currentCondition);
                    }
                    currentExpr = new StringBuilder();
                }
            }
        }

        // 处理最后一个条件
        if (currentExpr.length() > 0) {
            currentCondition = parseSingleCondition(currentExpr.toString().trim());
            if (currentCondition != null) {
                conditions.add(currentCondition);
            }
        }

        return conditions;
    }

    /**
     * WHERE 子句分词
     */
    private List<ConditionToken> tokenizeWhereClause(String clause) {
        List<ConditionToken> tokens = new ArrayList<>();

        Pattern pattern = Pattern.compile(
            "\\s*(=|!=|<>|>=|<=|>|<|LIKE|NOT\\s+LIKE|IN|NOT\\s+IN|BETWEEN|NOT\\s+BETWEEN|IS\\s+NULL|IS\\s+NOT\\s+NULL|AND|OR)\\s*|\\s+",
            Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(clause);
        int lastEnd = 0;

        while (matcher.find()) {
            String text = clause.substring(lastEnd, matcher.start()).trim();
            if (!text.isEmpty()) {
                tokens.add(new ConditionToken(ConditionTokenType.FIELD, text));
            }

            String operator = matcher.group(1);
            if (operator != null) {
                String op = operator.trim().toUpperCase();
                if ("AND".equals(op)) {
                    tokens.add(new ConditionToken(ConditionTokenType.AND, op));
                } else if ("OR".equals(op)) {
                    tokens.add(new ConditionToken(ConditionTokenType.OR, op));
                } else {
                    tokens.add(new ConditionToken(ConditionTokenType.OPERATOR, op));
                }
            }

            lastEnd = matcher.end();
        }

        String remaining = clause.substring(lastEnd).trim();
        if (!remaining.isEmpty()) {
            tokens.add(new ConditionToken(ConditionTokenType.VALUE, remaining));
        }

        return tokens;
    }

    /**
     * 解析单个条件表达式
     */
    private WhereCondition parseSingleCondition(String expression) {
        Matcher matcher = COMPARISON_PATTERN.matcher(expression);
        if (!matcher.find()) {
            return null;
        }

        WhereCondition condition = new WhereCondition();
        condition.setField(matcher.group(1).trim());
        condition.setOperator(matcher.group(2).trim().toUpperCase());
        condition.setValue(matcher.group(3).trim());

        return condition;
    }

    /**
     * 提取时间条件
     */
    private List<TimeCondition> extractTimeConditions(List<WhereCondition> whereConditions) {
        List<TimeCondition> timeConditions = new ArrayList<>();

        for (WhereCondition condition : whereConditions) {
            if (isTimeField(condition.getField())) {
                TimeCondition timeCondition = new TimeCondition();
                timeCondition.setField(condition.getField());
                timeCondition.setOperator(condition.getOperator());
                timeCondition.setValue(condition.getValue());
                timeCondition.setTimeGranularity(detectTimeGranularity(condition.getField()));
                timeConditions.add(timeCondition);
            }
        }

        return timeConditions;
    }

    /**
     * 提取 GROUP BY 子句
     */
    private void extractGroupBy(String sql, SqlParseResult result) {
        int groupByStart = findClauseStart(sql, GROUP_BY_PATTERN);
        if (groupByStart == -1) {
            result.setHasGroupBy(false);
            return;
        }

        result.setHasGroupBy(true);

        int orderByStart = findClauseStart(sql, ORDER_BY_PATTERN);
        int limitStart = findClauseStart(sql, LIMIT_PATTERN);
        int clauseEnd;

        if (orderByStart != -1) clauseEnd = orderByStart;
        else if (limitStart != -1) clauseEnd = limitStart;
        else clauseEnd = sql.length();

        String groupByClause = sql.substring(groupByStart + 8, clauseEnd).trim();
        String[] groupByFields = groupByClause.split(",");

        List<String> fields = Arrays.stream(groupByFields)
            .map(f -> f.trim().replaceAll("(?i)^(GROUP\\s+BY)\\s+", ""))
            .filter(f -> !f.isEmpty())
            .collect(Collectors.toList());

        result.setGroupByFields(fields);
    }

    /**
     * 提取聚合信息
     */
    private List<AggregationInfo> extractAggregations(List<SelectField> selectFields) {
        List<AggregationInfo> aggregations = new ArrayList<>();

        for (SelectField field : selectFields) {
            if (field.isAggregated()) {
                AggregationInfo info = new AggregationInfo();
                info.setType(field.getAggregationType());
                info.setField(field.getFieldName());
                info.setAlias(field.getAlias());
                info.setExpression(field.getRawExpression());
                aggregations.add(info);
            }
        }

        return aggregations;
    }

    /**
     * 提取 ORDER BY 和 LIMIT
     */
    private void extractOrderByAndLimit(String sql, SqlParseResult result) {
        int orderByStart = findClauseStart(sql, ORDER_BY_PATTERN);
        int limitStart = findClauseStart(sql, LIMIT_PATTERN);

        if (orderByStart != -1) {
            List<OrderByClause> orderByList = new ArrayList<>();
            int clauseEnd = limitStart != -1 ? limitStart : sql.length();
            String orderByClause = sql.substring(orderByStart + 8, clauseEnd).trim();

            String[] orderParts = orderByClause.split(",");
            for (String part : orderParts) {
                part = part.trim();
                if (part.isEmpty()) continue;

                OrderByClause orderBy = new OrderByClause();
                String[] words = part.split("\\s+");
                orderBy.setField(words[0]);
                if (words.length > 1) {
                    orderBy.setDirection(words[1].toUpperCase());
                } else {
                    orderBy.setDirection("ASC");
                }
                orderByList.add(orderBy);
            }

            result.setOrderBy(orderByList);
        }

        if (limitStart != -1) {
            String limitClause = sql.substring(limitStart + 5).trim();
            try {
                result.setLimit(Integer.parseInt(limitClause.split("\\s+")[0]));
            } catch (NumberFormatException e) {
                // 忽略无效的 LIMIT 值
            }
        }
    }

    /**
     * 辅助方法：查找子句起始位置
     */
    private int findClauseStart(String sql, Pattern pattern) {
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.start();
        }
        return -1;
    }

    /**
     * 辅助方法：查找下一个子句起始位置
     */
    private int findNextClauseStart(String sql, int after) {
        int[] positions = {
            findClauseStart(sql.substring(after), WHERE_PATTERN),
            findClauseStart(sql.substring(after), GROUP_BY_PATTERN),
            findClauseStart(sql.substring(after), ORDER_BY_PATTERN),
            findClauseStart(sql.substring(after), LIMIT_PATTERN)
        };

        int minPos = Integer.MAX_VALUE;
        for (int pos : positions) {
            if (pos != -1 && pos < minPos) {
                minPos = pos;
            }
        }

        return minPos == Integer.MAX_VALUE ? -1 : minPos + after;
    }

    /**
     * 辅助方法：提取别名
     */
    private String extractAlias(String expression) {
        Pattern pattern = Pattern.compile("\\bAS\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 检查是否有无 AS 的别名
        String[] words = expression.trim().split("\\s+");
        if (words.length >= 2) {
            String lastWord = words[words.length - 1];
            if (!lastWord.contains("(") && !lastWord.contains(")")) {
                return lastWord;
            }
        }

        return null;
    }

    /**
     * 辅助方法：提取表别名
     */
    private String extractTableAlias(String expression) {
        Pattern pattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\.", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 辅助方法：提取字段名
     */
    private String extractFieldName(String expression) {
        String cleaned = expression.trim();
        cleaned = cleaned.replaceAll("(?i)^(SUM|AVG|COUNT|MAX|MIN|COUNT_DISTINCT)\\s*\\(\\s*(?:DISTINCT\\s+)?", "");
        cleaned = cleaned.replaceAll("\\s*\\)$", "");
        cleaned = cleaned.replaceAll("^`|`$", "").replaceAll("^\"|\"$", "");
        cleaned = cleaned.replaceAll("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\..*", "$1");

        return cleaned;
    }

    /**
     * 辅助方法：解析聚合类型
     */
    private AggregationType parseAggregationType(String type) {
        switch (type.toUpperCase()) {
            case "SUM": return AggregationType.SUM;
            case "AVG": return AggregationType.AVG;
            case "COUNT": return AggregationType.COUNT;
            case "MAX": return AggregationType.MAX;
            case "MIN": return AggregationType.MIN;
            case "COUNT_DISTINCT":
            case "DISTINCT_COUNT":
                return AggregationType.COUNT_DISTINCT;
            default: return AggregationType.NONE;
        }
    }

    /**
     * 辅助方法：判断字段类型
     */
    private FieldType determineFieldType(String expression) {
        if (AGGREGATION_PATTERN.matcher(expression).find()) {
            return FieldType.METRIC;
        }
        if (TIME_FUNCTION_PATTERN.matcher(expression).find()) {
            return FieldType.TIME;
        }
        return FieldType.DIMENSION;
    }

    /**
     * 辅助方法：判断是否为时间字段
     */
    private boolean isTimeField(String fieldName) {
        if (fieldName == null) return false;

        String lower = fieldName.toLowerCase();
        return lower.contains("time") ||
               lower.contains("date") ||
               lower.contains("day") ||
               lower.contains("month") ||
               lower.contains("year") ||
               lower.contains("created_at") ||
               lower.contains("updated_at") ||
               lower.contains("start_date") ||
               lower.contains("end_date");
    }

    /**
     * 辅助方法：检测时间粒度
     */
    private String detectTimeGranularity(String fieldName) {
        if (fieldName == null) return null;

        String lower = fieldName.toLowerCase();
        if (lower.contains("year") || lower.contains("YEAR")) return "year";
        if (lower.contains("quarter") || lower.contains("QUARTER")) return "quarter";
        if (lower.contains("month") || lower.contains("MONTH")) return "month";
        if (lower.contains("week") || lower.contains("WEEK")) return "week";
        if (lower.contains("day") || lower.contains("DATE(") || lower.contains("DAY(")) return "day";
        if (lower.contains("hour") || lower.contains("HOUR")) return "hour";
        if (lower.contains("minute") || lower.contains("MINUTE")) return "minute";

        return null;
    }

    // ==================== 内部类定义 ====================

    public static class SqlParseResult {
        private String originalSql;
        private String normalizedSql;
        private List<TableReference> tables = new ArrayList<>();
        private List<SelectField> selectFields = new ArrayList<>();
        private List<AggregationInfo> aggregations = new ArrayList<>();
        private boolean hasGroupBy;
        private List<String> groupByFields = new ArrayList<>();
        private List<WhereCondition> whereConditions = new ArrayList<>();
        private List<TimeCondition> timeConditions = new ArrayList<>();
        private List<JoinInfo> joins = new ArrayList<>();
        private List<OrderByClause> orderBy = new ArrayList<>();
        private Integer limit;

        // Getters and Setters
        public String getOriginalSql() { return originalSql; }
        public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
        public String getNormalizedSql() { return normalizedSql; }
        public void setNormalizedSql(String normalizedSql) { this.normalizedSql = normalizedSql; }
        public List<TableReference> getTables() { return tables; }
        public void setTables(List<TableReference> tables) { this.tables = tables; }
        public List<SelectField> getSelectFields() { return selectFields; }
        public void setSelectFields(List<SelectField> selectFields) { this.selectFields = selectFields; }
        public List<AggregationInfo> getAggregations() { return aggregations; }
        public void setAggregations(List<AggregationInfo> aggregations) { this.aggregations = aggregations; }
        public boolean isHasGroupBy() { return hasGroupBy; }
        public void setHasGroupBy(boolean hasGroupBy) { this.hasGroupBy = hasGroupBy; }
        public List<String> getGroupByFields() { return groupByFields; }
        public void setGroupByFields(List<String> groupByFields) { this.groupByFields = groupByFields; }
        public List<WhereCondition> getWhereConditions() { return whereConditions; }
        public void setWhereConditions(List<WhereCondition> whereConditions) { this.whereConditions = whereConditions; }
        public List<TimeCondition> getTimeConditions() { return timeConditions; }
        public void setTimeConditions(List<TimeCondition> timeConditions) { this.timeConditions = timeConditions; }
        public List<JoinInfo> getJoins() { return joins; }
        public void setJoins(List<JoinInfo> joins) { this.joins = joins; }
        public List<OrderByClause> getOrderBy() { return orderBy; }
        public void setOrderBy(List<OrderByClause> orderBy) { this.orderBy = orderBy; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }

    public static class TableReference {
        private String tableName;
        private String alias;
        private boolean isMainTable;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public boolean isMainTable() { return isMainTable; }
        public void setMainTable(boolean mainTable) { isMainTable = mainTable; }
    }

    public static class SelectField {
        private String rawExpression;
        private String fieldName;
        private String tableAlias;
        private String alias;
        private AggregationType aggregationType = AggregationType.NONE;
        private boolean isAggregated;
        private FieldType fieldType = FieldType.DIMENSION;

        public String getRawExpression() { return rawExpression; }
        public void setRawExpression(String rawExpression) { this.rawExpression = rawExpression; }
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getTableAlias() { return tableAlias; }
        public void setTableAlias(String tableAlias) { this.tableAlias = tableAlias; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public AggregationType getAggregationType() { return aggregationType; }
        public void setAggregationType(AggregationType aggregationType) { this.aggregationType = aggregationType; }
        public boolean isAggregated() { return isAggregated; }
        public void setAggregated(boolean aggregated) { isAggregated = aggregated; }
        public FieldType getFieldType() { return fieldType; }
        public void setFieldType(FieldType fieldType) { this.fieldType = fieldType; }
    }

    public static class AggregationInfo {
        private AggregationType type;
        private String field;
        private String alias;
        private String expression;

        public AggregationType getType() { return type; }
        public void setType(AggregationType type) { this.type = type; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }
    }

    public static class WhereCondition {
        private String field;
        private String operator;
        private String value;
        private String logicalOperator;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getLogicalOperator() { return logicalOperator; }
        public void setLogicalOperator(String logicalOperator) { this.logicalOperator = logicalOperator; }
    }

    public static class TimeCondition {
        private String field;
        private String operator;
        private String value;
        private String timeGranularity;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getTimeGranularity() { return timeGranularity; }
        public void setTimeGranularity(String timeGranularity) { this.timeGranularity = timeGranularity; }
    }

    public static class JoinInfo {
        private String joinType;
        private String joinedTable;
        private String onCondition;

        public String getJoinType() { return joinType; }
        public void setJoinType(String joinType) { this.joinType = joinType; }
        public String getJoinedTable() { return joinedTable; }
        public void setJoinedTable(String joinedTable) { this.joinedTable = joinedTable; }
        public String getOnCondition() { return onCondition; }
        public void setOnCondition(String onCondition) { this.onCondition = onCondition; }
    }

    public static class OrderByClause {
        private String field;
        private String direction;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
    }

    public enum AggregationType {
        SUM, AVG, COUNT, COUNT_DISTINCT, MAX, MIN, NONE
    }

    public enum FieldType {
        METRIC, DIMENSION, TIME
    }

    private enum ConditionTokenType {
        FIELD, OPERATOR, VALUE, AND, OR
    }

    private static class ConditionToken {
        private final ConditionTokenType type;
        private final String text;

        public ConditionToken(ConditionTokenType type, String text) {
            this.type = type;
            this.text = text;
        }

        public ConditionTokenType getType() { return type; }
        public String getText() { return text; }
    }
}
