package com.mypalantir.sql;

import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlCase;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlParserService {
    private final SqlParser.Config parserConfig;
    private static final int MAX_RECURSION_DEPTH = 20;
    private static final int MAX_SUBQUERY_COUNT = 100;

    public SqlParserService() {
        this.parserConfig = SqlParser.config()
                .withLex(Lex.MYSQL)
                .withConformance(SqlConformanceEnum.MYSQL_5);
    }

    public SqlParseResult parse(String sql) {
        try {
            System.out.println("=".repeat(80));
            System.out.println("开始解析SQL");
            System.out.println("SQL长度: " + sql.length() + " 字符");
            System.out.println("=".repeat(80));

            SqlNode sqlNode = parseSqlNode(sql);
            SqlNodeTree tree = buildNodeTree(sqlNode, sql, 0, new HashSet<>());
            List<FieldLineage> lineage = analyzeLineage(tree);

            System.out.println("解析完成: " + tree.getChildren().size() + " 个子查询/层级");

            return SqlParseResult.success(sql, tree, lineage);

        } catch (SqlParseException e) {
            System.err.println("SQL解析错误: " + e.getMessage());
            return SqlParseResult.error(sql, "SQL解析错误: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("解析过程发生错误: " + e.getMessage());
            e.printStackTrace();
            return SqlParseResult.error(sql, "解析错误: " + e.getMessage());
        }
    }

    private SqlNode parseSqlNode(String sql) throws SqlParseException {
        String cleanedSql = sql.trim();
        if (cleanedSql.endsWith(";")) {
            cleanedSql = cleanedSql.substring(0, cleanedSql.length() - 1).trim();
            System.out.println("[parseSqlNode] 已去除末尾分号");
        }
        SqlParser parser = SqlParser.create(cleanedSql, parserConfig);
        return parser.parseQuery();
    }

    private SqlNodeTree buildNodeTree(SqlNode sqlNode, String originalSql, int level, Set<String> visited) {
        SqlNodeTree node = new SqlNodeTree("ROOT", level);
        node.setSql(originalSql);

        if (sqlNode instanceof SqlSelect) {
            return buildSelectTree((SqlSelect) sqlNode, level, visited);
        }

        return node;
    }

    private SqlNodeTree buildSelectTree(SqlSelect select, int level, Set<String> visited) {
        if (level > MAX_RECURSION_DEPTH) {
            System.err.println("[buildSelectTree] 达到最大递归深度限制");
            SqlNodeTree node = new SqlNodeTree("SELECT", level);
            node.setDescription("达到最大递归深度，截断解析");
            return node;
        }

        String nodeId = "level_" + level + "_" + select.hashCode();
        if (visited.contains(nodeId)) {
            System.err.println("[buildSelectTree] 检测到循环引用，跳过");
            SqlNodeTree node = new SqlNodeTree("SELECT", level);
            node.setDescription("检测到循环引用");
            return node;
        }
        visited.add(nodeId);

        SqlNodeTree node = new SqlNodeTree("SELECT", level);

        // 解析FROM子句中的表
        List<TableReference> tables = extractTablesSimple(select.getFrom(), level);
        node.setTables(tables);
        System.out.println("[Level " + level + "] 提取到 " + tables.size() + " 个表");

        // 解析WHERE条件（限制长度避免栈溢出）
        if (select.getWhere() != null) {
            node.setWhereCondition("[复杂条件，已省略]");
        }

        // 解析GROUP BY
        if (select.getGroup() != null) {
            List<String> groupBy = new ArrayList<>();
            for (SqlNode groupNode : select.getGroup()) {
                groupBy.add(extractSimpleFieldName(groupNode));
            }
            node.setGroupBy(groupBy);
        }

        // 解析SELECT字段
        List<FieldInfo> fields = extractFieldsSmart(select.getSelectList(), level);
        node.setFields(fields);
        System.out.println("[Level " + level + "] 提取到 " + fields.size() + " 个字段");

        // 提取表达式
        List<ExpressionInfo> expressions = extractExpressionsSmart(select.getSelectList());
        node.setExpressions(expressions);

        // 递归处理子查询
        List<SqlNodeTree> children = new ArrayList<>();
        if (children.size() < MAX_SUBQUERY_COUNT) {
            children.addAll(processSubqueries(select, level + 1, visited));
        }

        // ORDER BY
        if (select.getOrderList() != null) {
            node.setOrderBy(Collections.singletonList("[复杂排序，已省略]"));
        }

        node.setChildren(children);
        visited.remove(nodeId);

        return node;
    }

    private String extractSimpleFieldName(SqlNode node) {
        if (node == null) return "";
        String name = node.toString();
        name = name.replace("`", "").replace("\"", "");
        return name.length() > 50 ? name.substring(0, 50) + "..." : name;
    }

    private List<TableReference> extractTablesSimple(SqlNode fromNode, int level) {
        List<TableReference> tables = new ArrayList<>();

        if (fromNode == null) {
            return tables;
        }

        // 使用正则表达式提取表名，避免调用 toString()
        String fromStr = fromNode.toString();
        Pattern tablePattern = Pattern.compile(
            "(?:FROM|JOIN|LEFT\\s+JOIN|RIGHT\\s+JOIN|INNER\\s+JOIN|CROSS\\s+JOIN)\\s+`?([a-zA-Z0-9_]+)`?(?:\\s+(?:AS\\s+)?([a-zA-Z0-9_]+))?",
            Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = tablePattern.matcher(fromStr);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            String alias = matcher.group(2);
            if (tableName != null && !tableName.equalsIgnoreCase("SELECT")) {
                TableReference table = new TableReference(tableName, alias);
                table.setJoinType(determineJoinType(matcher.group(0)));
                tables.add(table);
            }
        }

        // 检测子查询
        if (fromStr.toUpperCase().contains("(SELECT")) {
            System.out.println("[extractTablesSimple] 发现子查询");
        }

        return tables;
    }

    private String determineJoinType(String joinClause) {
        String upper = joinClause.toUpperCase();
        if (upper.contains("LEFT JOIN")) return "LEFT";
        if (upper.contains("RIGHT JOIN")) return "RIGHT";
        if (upper.contains("INNER JOIN")) return "INNER";
        if (upper.contains("CROSS JOIN")) return "CROSS";
        if (upper.contains("JOIN")) return "JOIN";
        return null;
    }

    private List<FieldInfo> extractFieldsSmart(SqlNodeList selectList, int level) {
        List<FieldInfo> fields = new ArrayList<>();

        if (selectList == null) {
            return fields;
        }

        for (SqlNode node : selectList) {
            FieldInfo field = parseField(node);
            if (field != null) {
                fields.add(field);
            }
        }

        return fields;
    }

    private FieldInfo parseField(SqlNode node) {
        if (node == null) return null;

        FieldInfo field = new FieldInfo();
        String expr = safeToString(node);

        field.setAggregated(isAggregateFunction(node));

        if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;
            if (call.getOperator().getKind() == SqlKind.AS) {
                if (call.operandCount() >= 2) {
                    SqlNode right = call.operand(1);
                    String alias = safeToString(right);
                    field.setName(alias);
                    field.setAlias(alias);
                    field.setExpression(safeToString(call.operand(0)));
                }
            } else {
                field.setName(expr);
                field.setExpression(expr);
            }
        } else {
            field.setName(expr);
            field.setExpression(expr);
        }

        return field;
    }

    private String safeToString(SqlNode node) {
        if (node == null) return "";
        try {
            String str = node.toString();
            str = str.replace("`", "").replace("\"", "");
            return str.length() > 100 ? str.substring(0, 100) + "..." : str;
        } catch (Exception e) {
            return "[复杂表达式]";
        }
    }

    private void extractTableFromNode(SqlNode node, FieldInfo field) {
        if (node == null) return;

        String nodeStr = safeToString(node);
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(nodeStr);
        if (matcher.find()) {
            field.setTable(matcher.group(1));
            if (field.getName().equals(matcher.group(2))) {
                field.setName(matcher.group(2));
            }
        }
    }

    private boolean isAggregateFunction(SqlNode node) {
        if (node == null) return false;
        String nodeStr = node.toString().toUpperCase();
        String[] aggregates = {"SUM", "AVG", "COUNT", "MIN", "MAX"};
        for (String agg : aggregates) {
            if (nodeStr.contains(agg + "(")) {
                return true;
            }
        }
        if (nodeStr.contains("IFNULL(") || nodeStr.contains("COALESCE(")) {
            return true;
        }
        return node instanceof SqlCase || nodeStr.contains("CASE");
    }

    private List<ExpressionInfo> extractExpressionsSmart(SqlNodeList selectList) {
        List<ExpressionInfo> expressions = new ArrayList<>();

        if (selectList == null) {
            return expressions;
        }

        for (SqlNode node : selectList) {
            if (node == null) continue;
            String expr = safeToString(node).toUpperCase();

            if (node instanceof SqlCase || expr.contains("CASE")) {
                ExpressionInfo caseExpr = new ExpressionInfo("CASE_WHEN", safeToString(node));
                caseExpr.setFunction("CASE");
                expressions.add(caseExpr);
            }

            detectAndAddAggregate(expr, "SUM", expressions, node);
            detectAndAddAggregate(expr, "AVG", expressions, node);
            detectAndAddAggregate(expr, "COUNT", expressions, node);
            detectAndAddAggregate(expr, "MAX", expressions, node);
            detectAndAddAggregate(expr, "MIN", expressions, node);

            if (expr.contains("IFNULL(") || expr.contains("COALESCECE(")) {
                ExpressionInfo funcExpr = new ExpressionInfo("FUNCTION", safeToString(node));
                funcExpr.setFunction(expr.contains("IFNULL") ? "IFNULL" : "COALESCE");
                expressions.add(funcExpr);
            }
        }

        return expressions;
    }

    private void detectAndAddAggregate(String expr, String funcName, List<ExpressionInfo> expressions, SqlNode node) {
        if (expr.contains(funcName + "(")) {
            ExpressionInfo exprInfo = new ExpressionInfo("AGGREGATE", safeToString(node));
            exprInfo.setFunction(funcName);
            expressions.add(exprInfo);
        }
    }

    private List<SqlNodeTree> processSubqueries(SqlSelect select, int level, Set<String> visited) {
        List<SqlNodeTree> subqueries = new ArrayList<>();

        if (level > MAX_RECURSION_DEPTH) {
            return subqueries;
        }

        // 只扫描 FROM 和 WHERE 中的子查询
        if (select.getFrom() != null) {
            scanForSubqueriesInNode(select.getFrom(), level, subqueries, visited);
        }
        if (select.getWhere() != null) {
            scanForSubqueriesInNode(select.getWhere(), level, subqueries, visited);
        }

        return subqueries;
    }

    private void scanForSubqueriesInNode(SqlNode node, int level, List<SqlNodeTree> subqueries, Set<String> visited) {
        if (node == null || subqueries.size() >= MAX_SUBQUERY_COUNT) return;

        if (node instanceof SqlSelect) {
            SqlSelect subSelect = (SqlSelect) node;
            SqlNodeTree subTree = buildSelectTree(subSelect, level, visited);
            subTree.setType("SUBQUERY");
            subTree.setAlias("SUB" + subqueries.size());
            subTree.setDescription("子查询 #" + subqueries.size());
            subqueries.add(subTree);
        } else if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;
            for (SqlNode operand : call.getOperandList()) {
                scanForSubqueriesInNode(operand, level, subqueries, visited);
            }
        } else if (node instanceof SqlNodeList) {
            for (SqlNode child : (SqlNodeList) node) {
                scanForSubqueriesInNode(child, level, subqueries, visited);
            }
        }
    }

    private List<FieldLineage> analyzeLineage(SqlNodeTree tree) {
        List<FieldLineage> lineage = new ArrayList<>();

        System.out.println("[analyzeLineage] 开始分析字段血缘...");

        if (tree.getFields() != null) {
            for (FieldInfo field : tree.getFields()) {
                FieldLineage fieldLineage = new FieldLineage();
                fieldLineage.setOutputField(field.getName());
                fieldLineage.setOutputTable("RESULT");

                List<FieldLineage.LineageStep> steps = new ArrayList<>();

                String sourceExpr = field.getExpression() != null ? field.getExpression() : field.getName();
                steps.add(new FieldLineage.LineageStep(sourceExpr, field.getName(), "SELECT"));

                if (field.isAggregated() && field.getExpression() != null) {
                    String funcType = detectFunctionType(field.getExpression());
                    steps.add(new FieldLineage.LineageStep(
                        extractSourceFields(field.getExpression()),
                        field.getExpression(),
                        funcType
                    ));
                }

                fieldLineage.setPath(steps);
                fieldLineage.setExpression(field.getExpression());

                lineage.add(fieldLineage);
            }
        }

        if (tree.getChildren() != null) {
            for (SqlNodeTree child : tree.getChildren()) {
                lineage.addAll(analyzeLineage(child));
            }
        }

        System.out.println("[analyzeLineage] 分析完成: " + lineage.size() + " 个字段");
        return lineage;
    }

    private String detectFunctionType(String expression) {
        if (expression == null) return "UNKNOWN";
        String upper = expression.toUpperCase();
        if (upper.contains("SUM(")) return "SUM";
        if (upper.contains("AVG(")) return "AVG";
        if (upper.contains("COUNT(")) return "COUNT";
        if (upper.contains("MAX(")) return "MAX";
        if (upper.contains("MIN(")) return "MIN";
        if (upper.contains("CASE")) return "CASE_WHEN";
        if (upper.contains("IFNULL(") || upper.contains("COALESCE(")) return "IFNULL";
        return "AGGREGATE";
    }

    private String extractSourceFields(String expression) {
        if (expression == null) return "";

        Set<String> fields = new HashSet<>();
        Pattern fieldPattern = Pattern.compile(
            "(?:SUM|AVG|COUNT|MAX|MIN|IFNULL|COALESCE|CASE)\\s*\\(?\\s*(?:[a-zA-Z0-9_]+\\.)?([a-zA-Z0-9_]+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = fieldPattern.matcher(expression);
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }

        if (fields.isEmpty()) {
            return expression.length() > 50 ? expression.substring(0, 50) + "..." : expression;
        }

        return String.join(", ", fields);
    }
}
