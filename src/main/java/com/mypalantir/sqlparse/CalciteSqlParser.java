package com.mypalantir.sqlparse;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;

@Component
public class CalciteSqlParser {

    private static final Pattern AGGREGATION_PATTERN = Pattern.compile(
        "(SUM|AVG|COUNT|MAX|MIN|COUNT_DISTINCT)\\s*\\(\\s*(?:DISTINCT\\s+)?([^)]+)\\)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ALIAS_PATTERN = Pattern.compile(
        "(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TIME_FUNCTION_PATTERN = Pattern.compile(
        "(DATE|YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|WEEK|QUARTER|DAYOFWEEK|DAYOFMONTH|DAYOFYEAR)\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );

    public CalciteSqlParseResult parse(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 语句不能为空");
        }

        CalciteSqlParseResult result = new CalciteSqlParseResult();
        result.setOriginalSql(sql);
        
        // 预处理SQL：去除分号、注释等
        String cleanedSql = preprocessSql(sql);
        result.setNormalizedSql(normalizeSql(cleanedSql));

        try {
            SqlParser.Config config = SqlParser.config();
            SqlParser parser = SqlParser.create(cleanedSql, config);
            SqlNode sqlNode = parser.parseStmt();
            result.setSqlNode(sqlNode);

            if (sqlNode instanceof SqlSelect) {
                SqlSelect sqlSelect = (SqlSelect) sqlNode;
                extractFromSqlSelect(sqlSelect, result);
            }

        } catch (SqlParseException e) {
            throw new RuntimeException("SQL 解析失败: " + e.getMessage(), e);
        }

        return result;
    }

    private String preprocessSql(String sql) {
        // 1. 去除注释
        String cleaned = sql.replaceAll("--[^\\n]*", "");
        // 2. 去除末尾分号
        cleaned = cleaned.trim();
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private void extractFromSqlSelect(SqlSelect sqlSelect, CalciteSqlParseResult result) {
        extractSelectFields(sqlSelect, result);
        extractFromClause(sqlSelect, result);
        extractWhereClause(sqlSelect, result);
        extractGroupByClause(sqlSelect, result);
        extractAggregations(result);
    }

    private void extractSelectFields(SqlSelect sqlSelect, CalciteSqlParseResult result) {
        SqlNodeList selectList = sqlSelect.getSelectList();
        if (selectList == null) return;

        for (SqlNode node : selectList) {
            CalciteSqlParseResult.SelectField fieldInfo = parseSelectField(node);
            if (fieldInfo != null) {
                result.getSelectFields().add(fieldInfo);
            }
        }
    }

    private CalciteSqlParseResult.SelectField parseSelectField(SqlNode node) {
        CalciteSqlParseResult.SelectField field = new CalciteSqlParseResult.SelectField();
        String rawExpr = node.toString();
        field.setRawExpression(rawExpr);
        System.out.println("[CalciteSqlParser] 解析SELECT字段: " + rawExpr + ", node类型: " + node.getClass().getSimpleName());

        // 先处理AS别名，提取真实节点和显式别名
        SqlNode actualNode = node;
        String explicitAlias = null;
        
        if (node instanceof SqlCall) {
            SqlCall call = (SqlCall) node;
            System.out.println("[CalciteSqlParser]   -> SqlCall.kind=" + call.getKind() + ", operandCount=" + call.operandCount());
            if (call.getKind() == SqlKind.AS && call.operandCount() > 1) {
                explicitAlias = call.operand(1).toString();
                actualNode = call.operand(0);  // 获取AS之前的真实节点
                System.out.println("[CalciteSqlParser]   -> 提取AS别名: " + explicitAlias + ", actualNode=" + actualNode);
            }
        }

        // 处理真实节点（可能是聚合函数）
        if (actualNode instanceof SqlCall) {
            SqlCall call = (SqlCall) actualNode;
            SqlKind kind = call.getKind();
            String functionName = call.getOperator().getName().toUpperCase();
            System.out.println("[CalciteSqlParser]   -> actualNode是SqlCall, kind=" + kind + ", functionName=" + functionName);
            
            // 判断是否为聚合函数（通过kind或函数名）
            boolean isAggFunc = (kind == SqlKind.COUNT || kind == SqlKind.SUM || 
                kind == SqlKind.AVG || kind == SqlKind.MIN || kind == SqlKind.MAX) ||
                functionName.equals("COUNT") || functionName.equals("SUM") || 
                functionName.equals("AVG") || functionName.equals("MIN") || functionName.equals("MAX");
            
            if (isAggFunc) {
                // 聚合函数
                field.setAggregated(true);
                field.setAggregationType(functionName);
                System.out.println("[CalciteSqlParser]   -> 识别为聚合函数: " + functionName);
                if (call.operandCount() > 0) {
                    SqlNode arg = call.operand(0);
                    String argStr = arg.toString();
                    System.out.println("[CalciteSqlParser]   -> 聚合参数: " + argStr);
                    if ("*".equals(argStr)) {
                        // COUNT(*) 情况
                        field.setFieldName("*");
                        field.setAlias(explicitAlias != null ? explicitAlias : "count_star");
                    } else {
                        // 聚合具体字段
                        String fieldName = extractFieldName(argStr);
                        System.out.println("[CalciteSqlParser]   -> 提取字段名: " + fieldName);
                        field.setFieldName(fieldName);
                        field.setAlias(explicitAlias != null ? explicitAlias : fieldName);
                    }
                } else {
                    field.setFieldName("*");
                    field.setAlias(explicitAlias != null ? explicitAlias : "count_star");
                }
            } else {
                // 非聚合表达式
                field.setAggregated(false);
                if (actualNode instanceof SqlIdentifier) {
                    String fieldName = actualNode.toString();
                    field.setFieldName(fieldName);
                    field.setAlias(explicitAlias != null ? explicitAlias : fieldName);
                }
            }
        } else if (actualNode instanceof SqlIdentifier) {
            // 普通字段
            field.setAggregated(false);
            String fieldName = actualNode.toString();
            field.setFieldName(fieldName);
            field.setAlias(explicitAlias != null ? explicitAlias : fieldName);
        }

        System.out.println("[CalciteSqlParser]   -> 最终结果: fieldName=" + field.getFieldName() + ", alias=" + field.getAlias() + ", isAggregated=" + field.isAggregated());
        field.setFieldType(determineFieldType(rawExpr));
        return field;
    }

    private void extractFromClause(SqlSelect sqlSelect, CalciteSqlParseResult result) {
        SqlNode fromNode = sqlSelect.getFrom();
        if (fromNode == null) return;

        extractTablesFromNode(fromNode, result, true);
        extractJoins(sqlSelect, result);
    }

    private void extractTablesFromNode(SqlNode node, CalciteSqlParseResult result, boolean isMainTable) {
        if (node instanceof SqlIdentifier) {
            CalciteSqlParseResult.TableReference table = new CalciteSqlParseResult.TableReference();
            table.setTableName(node.toString().replace("`", ""));
            table.setMainTable(isMainTable);
            result.getTables().add(table);
        } else if (node instanceof SqlCall) {
            SqlCall call = (SqlCall) node;
            SqlKind kind = call.getKind();
            if (kind == SqlKind.AS) {
                extractTablesFromNode(call.operand(0), result, isMainTable);
            } else if (kind == SqlKind.JOIN) {
                if (call.operandCount() >= 2) {
                    extractTablesFromNode(call.operand(0), result, false);
                }
            } else if (kind == SqlKind.VALUES) {
                for (SqlNode operand : call.getOperandList()) {
                    extractTablesFromNode(operand, result, isMainTable);
                }
            } else if (kind == SqlKind.SELECT) {
                SqlSelect subSelect = (SqlSelect) node;
                System.out.println("[CalciteSqlParser] 检测到子查询，递归解析子查询内容");
                extractFromSqlSelect(subSelect, result);
            }
        } else if (node instanceof SqlSelect) {
            SqlSelect subSelect = (SqlSelect) node;
            System.out.println("[CalciteSqlParser] 检测到子查询(SqlSelect)，递归解析子查询内容");
            extractFromSqlSelect(subSelect, result);
        }
    }

    private void extractJoins(SqlSelect sqlSelect, CalciteSqlParseResult result) {
        SqlNode fromNode = sqlSelect.getFrom();
        extractJoinsRecursive(fromNode, result);
    }

    private void extractJoinsRecursive(SqlNode node, CalciteSqlParseResult result) {
        if (node instanceof SqlCall) {
            SqlCall call = (SqlCall) node;
            SqlKind kind = call.getKind();

            if (kind == SqlKind.JOIN) {
                CalciteSqlParseResult.JoinInfo join = new CalciteSqlParseResult.JoinInfo();
                join.setJoinType("JOIN");
                if (call.operandCount() >= 2) {
                    SqlNode rightTable = call.operand(1);
                    if (rightTable instanceof SqlIdentifier) {
                        join.setJoinedTable(rightTable.toString().replace("`", ""));
                    }
                }
                if (call.operandCount() >= 4) {
                    SqlNode onCondition = call.operand(3);
                    join.setOnCondition(onCondition.toString());
                }
                result.getJoins().add(join);
            }

            for (SqlNode operand : call.getOperandList()) {
                extractJoinsRecursive(operand, result);
            }
        }
    }

    private String getJoinType(SqlKind kind) {
        if (kind == SqlKind.JOIN) return "INNER";
        String kindStr = kind.toString();
        if (kindStr.contains("LEFT")) return "LEFT";
        if (kindStr.contains("RIGHT")) return "RIGHT";
        if (kindStr.contains("FULL")) return "FULL";
        return "JOIN";
    }

    private void extractWhereClause(SqlSelect sqlSelect, CalciteSqlParseResult result) {
        SqlNode whereNode = sqlSelect.getWhere();
        if (whereNode == null) return;

        List<CalciteSqlParseResult.WhereCondition> conditions = parseWhereConditions(whereNode.toString());
        result.setWhereConditions(conditions);
        result.setTimeConditions(extractTimeConditions(conditions));
    }

    private List<CalciteSqlParseResult.WhereCondition> parseWhereConditions(String whereClause) {
        List<CalciteSqlParseResult.WhereCondition> conditions = new ArrayList<>();

        Pattern pattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_.`]*)\\s*(=|!=|<>|>=|<=|>|<|LIKE|NOT\\s+LIKE|IN|NOT\\s+IN|BETWEEN|NOT\\s+BETWEEN|IS\\s+NULL|IS\\s+NOT\\s+NULL)\\s*([^ANDOR]+)(?:\\s+(AND|OR)\\s+)?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(whereClause);
        while (matcher.find()) {
            CalciteSqlParseResult.WhereCondition condition = new CalciteSqlParseResult.WhereCondition();
            condition.setField(matcher.group(1).trim());
            condition.setOperator(matcher.group(2).trim().toUpperCase());
            condition.setValue(matcher.group(3).trim());
            String logicalOp = matcher.group(4);
            condition.setLogicalOperator(logicalOp != null ? logicalOp.toUpperCase() : null);
            conditions.add(condition);
        }

        return conditions;
    }

    private List<CalciteSqlParseResult.TimeCondition> extractTimeConditions(
            List<CalciteSqlParseResult.WhereCondition> conditions) {
        List<CalciteSqlParseResult.TimeCondition> timeConditions = new ArrayList<>();

        for (CalciteSqlParseResult.WhereCondition condition : conditions) {
            if (isTimeField(condition.getField())) {
                CalciteSqlParseResult.TimeCondition timeCondition = new CalciteSqlParseResult.TimeCondition();
                timeCondition.setField(condition.getField());
                timeCondition.setOperator(condition.getOperator());
                timeCondition.setValue(condition.getValue());
                timeCondition.setTimeGranularity(detectTimeGranularity(condition.getField()));
                timeConditions.add(timeCondition);
            }
        }

        return timeConditions;
    }

    private boolean isTimeField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        return lower.contains("time") || lower.contains("date") || lower.contains("day") ||
               lower.contains("month") || lower.contains("year") || lower.contains("hour");
    }

    private String detectTimeGranularity(String fieldName) {
        if (fieldName == null) return "UNKNOWN";
        String lower = fieldName.toLowerCase();
        if (lower.contains("hour")) return "HOUR";
        if (lower.contains("day")) return "DAY";
        if (lower.contains("week")) return "WEEK";
        if (lower.contains("month")) return "MONTH";
        if (lower.contains("quarter")) return "QUARTER";
        if (lower.contains("year")) return "YEAR";
        return "UNKNOWN";
    }

    private void extractGroupByClause(SqlSelect sqlSelect, CalciteSqlParseResult result) {
        SqlNodeList groupBy = sqlSelect.getGroup();
        if (groupBy == null || groupBy.size() == 0) {
            result.setHasGroupBy(false);
            return;
        }

        result.setHasGroupBy(true);
        for (SqlNode node : groupBy) {
            result.getGroupByFields().add(node.toString().replace("`", ""));
        }
    }

    private void extractAggregations(CalciteSqlParseResult result) {
        for (CalciteSqlParseResult.SelectField field : result.getSelectFields()) {
            if (field.isAggregated()) {
                CalciteSqlParseResult.AggregationInfo agg = new CalciteSqlParseResult.AggregationInfo();
                agg.setType(field.getAggregationType());
                agg.setField(field.getFieldName());
                agg.setAlias(field.getAlias());
                agg.setExpression(field.getRawExpression());
                result.getAggregations().add(agg);
            }
        }
    }

    private String extractAlias(String expression) {
        Matcher matcher = ALIAS_PATTERN.matcher(expression);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractFieldName(String expression) {
        if (expression == null) return null;
        String trimmed = expression.trim();
        String[] parts = trimmed.split("\\.");
        String fieldName = parts[parts.length - 1].trim();
        return fieldName.replace("`", "").replace("\"", "");
    }

    private String determineFieldType(String expression) {
        if (TIME_FUNCTION_PATTERN.matcher(expression).find()) {
            return "TIME";
        }
        if (AGGREGATION_PATTERN.matcher(expression).find()) {
            return "AGGREGATION";
        }
        return "FIELD";
    }
}
