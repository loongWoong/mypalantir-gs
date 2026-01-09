package com.mypalantir.sql;

import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlCase;
import org.apache.calcite.sql.fun.SqlCaseOperator;
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
    private static final int MAX_SQL_LENGTH = 50000;

    private final NestedExpressionParser nestedExpressionParser;

    public SqlParserService() {
        this.parserConfig = SqlParser.config()
                .withLex(Lex.MYSQL)
                .withConformance(SqlConformanceEnum.MYSQL_5);
        this.nestedExpressionParser = new NestedExpressionParser();
    }

    public SqlParseResult parse(String sql) {
        try {
            if (sql == null || sql.trim().isEmpty()) {
                return SqlParseResult.error("", "SQL不能为空");
            }

            if (sql.length() > MAX_SQL_LENGTH) {
                return SqlParseResult.error(sql, "SQL长度超过限制(" + MAX_SQL_LENGTH + "字符)");
            }

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
            SqlNodeTree node = new SqlNodeTree("SELECT", level);
            node.setDescription("达到最大递归深度");
            return node;
        }

        String nodeId = "level_" + level + "_" + select.hashCode();
        if (visited.contains(nodeId)) {
            SqlNodeTree node = new SqlNodeTree("SELECT", level);
            node.setDescription("检测到循环引用");
            return node;
        }
        visited.add(nodeId);

        SqlNodeTree node = new SqlNodeTree("SELECT", level);

        // 解析FROM子句 - 支持子查询
        List<TableReference> tables = extractTablesFromFrom(select.getFrom(), level);
        node.setTables(tables);

        // 解析JOIN信息
        List<JoinInfo> joins = extractJoinInfo(select.getFrom(), level);
        node.setJoins(joins);

        // 解析WHERE条件
        if (select.getWhere() != null) {
            node.setWhereCondition(safeToString(select.getWhere()));
        }

        // 解析GROUP BY
        if (select.getGroup() != null) {
            List<String> groupBy = new ArrayList<>();
            for (SqlNode groupNode : select.getGroup()) {
                groupBy.add(extractFieldName(groupNode));
            }
            node.setGroupBy(groupBy);
        }

        // 解析SELECT字段 - 支持复杂表达式
        List<FieldInfo> fields = extractFieldsFromSelect(select.getSelectList(), level);
        node.setFields(fields);

        // 提取表达式 - 支持嵌套表达式解析
        List<ExpressionInfo> expressions = extractExpressionsFromSelect(select.getSelectList());
        node.setExpressions(expressions);

        // 递归处理子查询 - 支持多层嵌套
        List<SqlNodeTree> children = new ArrayList<>();
        System.out.println("[Level " + level + "] 开始提取子查询, from=" + (select.getFrom() != null ? select.getFrom().getClass().getSimpleName() : "null"));
        List<SqlNodeTree> subqueries = extractSubqueries(select, level + 1, visited);
        System.out.println("[Level " + level + "] 提取到 " + subqueries.size() + " 个子查询");
        children.addAll(subqueries);

        // 处理WHERE中的相关子查询
        if (select.getWhere() != null) {
            children.addAll(extractCorrelatedSubqueries(select.getWhere(), level + 1, visited));
        }

        node.setChildren(children);

        // For SUBQUERY_PARENT, inherit fields and other info from the deepest SELECT
        if ("SUBQUERY_PARENT".equals(node.getType())) {
            SqlNodeTree deepestSelect = findDeepestSelect(node);
            if (deepestSelect != null) {
                node.setFields(deepestSelect.getFields());
                node.setTables(deepestSelect.getTables());
                node.setExpressions(deepestSelect.getExpressions());
                node.setWhereCondition(deepestSelect.getWhereCondition());
                node.setGroupBy(deepestSelect.getGroupBy());
                node.setOrderBy(deepestSelect.getOrderBy());
            }
        }

        visited.remove(nodeId);

        return node;
    }

    private String extractFieldName(SqlNode node) {
        if (node == null) return "";
        String name = node.toString().replace("`", "").replace("\"", "");
        return name.length() > 50 ? name.substring(0, 50) + "..." : name;
    }

    /**
     * 从 FROM 子句中提取真正的表名 - 支持子查询
     */
    private List<TableReference> extractTablesFromFrom(SqlNode fromNode, int level) {
        List<TableReference> tables = new ArrayList<>();

        if (fromNode == null) {
            return tables;
        }

        // 递归遍历 FROM 节点树，提取表
        extractTablesRecursive(fromNode, tables, level, new HashSet<>());

        // 去重
        List<TableReference> deduplicated = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TableReference table : tables) {
            String key = table.getName().toLowerCase();
            if (!seen.contains(key)) {
                seen.add(key);
                deduplicated.add(table);
            }
        }

        return deduplicated;
    }

    private void extractTablesRecursive(SqlNode node, List<TableReference> tables, int level, Set<String> seenTables) {
        if (node == null) return;

        if (node instanceof SqlIdentifier) {
            // 简单表名
            String tableName = node.toString().replace("`", "").replace("\"", "");
            if (isValidTableName(tableName) && !seenTables.contains(tableName.toLowerCase())) {
                seenTables.add(tableName.toLowerCase());
                TableReference table = new TableReference(tableName, null);
                tables.add(table);
                System.out.println("[extractTablesRecursive] 添加表: " + tableName);
            }
        } else if (node instanceof SqlSelect) {
            // 子查询: SELECT (...) AS alias
            SqlSelect subSelect = (SqlSelect) node;
            TableReference subqueryRef = new TableReference("SUBQUERY_" + level, "SUBQUERY");
            subqueryRef.setSubquery(subSelect.toString());
            subqueryRef.setJoinType("FROM_SUBQUERY");
            tables.add(subqueryRef);
            System.out.println("[extractTablesRecursive] 发现子查询");
        } else if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;
            String operator = call.getOperator().getKind().toString();
            System.out.println("[extractTablesRecursive] SqlBasicCall, operator=" + operator + ", operandCount=" + call.operandCount());

            if ("JOIN".equals(operator) || "INNER".equals(operator) ||
                "LEFT".equals(operator) || "RIGHT".equals(operator) ||
                "FULL".equals(operator) || "CROSS".equals(operator)) {
                // JOIN 结构：遍历所有操作数
                for (int i = 0; i < call.operandCount(); i++) {
                    SqlNode operand = call.operand(i);
                    if (operand != null) {
                        // 检查是否是ON条件
                        if (operand instanceof SqlBasicCall) {
                            SqlBasicCall operandCall = (SqlBasicCall) operand;
                            String opKind = operandCall.getOperator().getKind().toString();
                            if ("ON".equals(opKind)) {
                                continue; // 跳过ON条件
                            }
                        }
                        extractTablesRecursive(operand, tables, level, seenTables);
                    }
                }
            } else if ("AS".equals(operator)) {
                // 别名结构: table AS alias 或 (SELECT ...) AS alias
                if (call.operandCount() >= 2) {
                    SqlNode leftOperand = call.operand(0);
                    if (leftOperand instanceof SqlSelect) {
                        // 子查询别名
                        SqlSelect subSelect = (SqlSelect) leftOperand;
                        String alias = safeToString(call.operand(1));
                        TableReference subqueryRef = new TableReference("SUBQUERY_" + level, alias);
                        subqueryRef.setSubquery(subSelect.toString());
                        tables.add(subqueryRef);
                        System.out.println("[extractTablesRecursive] 发现(SELECT ...) AS " + alias);
                    } else {
                        extractTablesRecursive(leftOperand, tables, level, seenTables);
                        // 设置最后一个表的别名
                        if (!tables.isEmpty()) {
                            TableReference lastTable = tables.get(tables.size() - 1);
                            lastTable.setAlias(safeToString(call.operand(1)));
                        }
                    }
                }
            } else {
                // 其他操作符，递归处理所有操作数
                for (SqlNode operand : call.getOperandList()) {
                    extractTablesRecursive(operand, tables, level, seenTables);
                }
            }
        }
    }

    /**
     * 提取JOIN信息 - 新增
     */
    private List<JoinInfo> extractJoinInfo(SqlNode fromNode, int level) {
        List<JoinInfo> joins = new ArrayList<>();
        extractJoinRecursive(fromNode, joins, level);
        return joins;
    }

    private void extractJoinRecursive(SqlNode node, List<JoinInfo> joins, int level) {
        if (node == null) return;

        if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;
            String operator = call.getOperator().getKind().toString();

            if ("LEFT".equals(operator) || "RIGHT".equals(operator) ||
                "INNER".equals(operator) || "JOIN".equals(operator)) {
                JoinInfo join = new JoinInfo();
                join.setJoinType(operator);

                // 提取JOIN条件（通常是最后一个操作数）
                if (call.operandCount() > 0) {
                    SqlNode lastOperand = call.operand(call.operandCount() - 1);
                    if (lastOperand instanceof SqlBasicCall) {
                        SqlBasicCall onCall = (SqlBasicCall) lastOperand;
                        String onOperator = onCall.getOperator().getKind().toString();
                        if ("ON".equals(onOperator)) {
                            join.setCondition(safeToString(onCall.operand(1)));
                        }
                    }
                }

                // 提取左右表
                if (call.operandCount() >= 2) {
                    join.setLeftTable(extractTableNameFromNode(call.operand(0)));
                    join.setRightTable(extractTableNameFromNode(call.operand(1)));
                }

                joins.add(join);
            } else {
                // 递归处理
                for (SqlNode operand : call.getOperandList()) {
                    extractJoinRecursive(operand, joins, level);
                }
            }
        }
    }

    private String extractTableNameFromNode(SqlNode node) {
        if (node == null) return "UNKNOWN";
        if (node instanceof SqlIdentifier) {
            return node.toString().replace("`", "").replace("\"", "");
        } else if (node instanceof SqlSelect) {
            return "SUBQUERY";
        } else if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;
            String opKind = call.getOperator().getKind().toString();
            if ("AS".equals(opKind) && call.operandCount() >= 2) {
                return safeToString(call.operand(1));
            }
        }
        return "UNKNOWN";
    }

    private boolean isValidTableName(String name) {
        if (name == null || name.isEmpty()) return false;
        String upper = name.toUpperCase();
        if (upper.equals("SELECT") || upper.equals("FROM") || upper.equals("WHERE") ||
            upper.equals("AND") || upper.equals("OR") || upper.equals("ON") ||
            upper.equals("GROUP") || upper.equals("BY") || upper.equals("ORDER") ||
            upper.equals("HAVING") || upper.equals("CASE") || upper.equals("WHEN") ||
            upper.equals("THEN") || upper.equals("ELSE") || upper.equals("END") ||
            upper.equals("NULL") || upper.equals("IN") || upper.equals("NOT") ||
            upper.equals("EXISTS") || upper.equals("JOIN") || upper.equals("LEFT") ||
            upper.equals("RIGHT") || upper.equals("INNER") || upper.equals("OUTER")) {
            return false;
        }
        if (name.contains(" ") || name.contains("(") || name.contains(")")) return false;
        return true;
    }

    private List<FieldInfo> extractFieldsFromSelect(SqlNodeList selectList, int level) {
        List<FieldInfo> fields = new ArrayList<>();

        if (selectList == null) {
            return fields;
        }

        for (SqlNode node : selectList) {
            FieldInfo field = parseFieldNode(node);
            if (field != null) {
                fields.add(field);
            }
        }

        return fields;
    }

    private FieldInfo parseFieldNode(SqlNode node) {
        if (node == null) return null;

        FieldInfo field = new FieldInfo();
        String expr = safeToString(node);

        field.setAggregated(isAggregateFunction(node));
        field.setCaseWhen(node instanceof SqlCase || expr.toUpperCase().contains("CASE"));
        field.setIfNull(expr.toUpperCase().contains("IFNULL(") || expr.toUpperCase().contains("COALESCE("));

        if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;
            String opKind = call.getOperator().getKind().toString();
            if ("AS".equals(opKind)) {
                if (call.operandCount() >= 2) {
                    SqlNode right = call.operand(1);
                    String alias = safeToString(right);
                    field.setName(alias);
                    field.setAlias(alias);
                    field.setExpression(safeToString(call.operand(0)));
                    field.setAggregated(isAggregateFunction(call.operand(0)));
                }
            } else if (call.getOperator() instanceof SqlCaseOperator) {
                field.setName(expr);
                field.setExpression(expr);
                field.setAggregated(true);
                field.setCaseWhen(true);
            } else if (isFunctionCall(call)) {
                String funcName = call.getOperator().getName();
                field.setName(funcName);
                field.setExpression(expr);
            } else {
                field.setName(expr.length() > 50 ? expr.substring(0, 50) + "..." : expr);
                field.setExpression(expr);
            }
        } else if (node instanceof SqlIdentifier) {
            field.setName(expr);
            field.setExpression(expr);
        } else if (node instanceof SqlLiteral) {
            field.setName("CONSTANT");
            field.setExpression(expr);
        }

        // 解析嵌套表达式，提取源字段
        ExpressionInfo nestedInfo = nestedExpressionParser.parse(node);
        for (String sourceField : nestedInfo.getSourceFields()) {
            field.addSourceField(sourceField);
        }

        return field;
    }

    private String safeToString(SqlNode node) {
        if (node == null) return "";
        try {
            String str = node.toString();
            str = str.replace("`", "").replace("\"", "");
            return str.length() > 200 ? str.substring(0, 200) + "..." : str;
        } catch (Exception e) {
            return "[复杂表达式]";
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

    private boolean isFunctionCall(SqlBasicCall call) {
        String name = call.getOperator().getName().toUpperCase();
        Set<String> functions = Set.of(
            "SUM", "AVG", "COUNT", "MIN", "MAX", "IFNULL", "COALESCE", "NVL",
            "SUBSTR", "SUBSTRING", "CONCAT", "LENGTH", "ABS", "ROUND",
            "FLOOR", "CEIL", "SQRT", "POWER", "UPPER", "LOWER", "TRIM"
        );
        return functions.contains(name);
    }

    private List<ExpressionInfo> extractExpressionsFromSelect(SqlNodeList selectList) {
        List<ExpressionInfo> expressions = new ArrayList<>();

        if (selectList == null) {
            return expressions;
        }

        for (SqlNode node : selectList) {
            if (node == null) continue;

            // 使用NestedExpressionParser解析复杂表达式
            ExpressionInfo nestedInfo = nestedExpressionParser.parse(node);
            if (nestedInfo.getType() != null) {
                expressions.add(nestedInfo);
            }

            // 同时使用原有逻辑进行兼容
            String expr = safeToString(node).toUpperCase();

            if (node instanceof SqlCase || expr.contains("CASE")) {
                ExpressionInfo caseExpr = new ExpressionInfo("CASE_WHEN", safeToString(node));
                caseExpr.setFunction("CASE");
                caseExpr.setDepth(1);
                expressions.add(caseExpr);
            }

            detectAndAddAggregate(expr, "SUM", expressions, node);
            detectAndAddAggregate(expr, "AVG", expressions, node);
            detectAndAddAggregate(expr, "COUNT", expressions, node);
            detectAndAddAggregate(expr, "MAX", expressions, node);
            detectAndAddAggregate(expr, "MIN", expressions, node);

            if (expr.contains("IFNULL(") || expr.contains("COALESCE(")) {
                ExpressionInfo funcExpr = new ExpressionInfo("FUNCTION", safeToString(node));
                funcExpr.setFunction(expr.contains("IFNULL") ? "IFNULL" : "COALESCE");
                funcExpr.setDepth(1);
                expressions.add(funcExpr);
            }
        }

        return expressions;
    }

    private void detectAndAddAggregate(String expr, String funcName, List<ExpressionInfo> expressions, SqlNode node) {
        if (expr.contains(funcName + "(")) {
            ExpressionInfo exprInfo = new ExpressionInfo("AGGREGATE", safeToString(node));
            exprInfo.setFunction(funcName);
            exprInfo.setDepth(1);
            expressions.add(exprInfo);
        }
    }

    /**
     * 提取子查询 - 支持多层嵌套
     */
    private List<SqlNodeTree> extractSubqueries(SqlSelect select, int level, Set<String> visited) {
        List<SqlNodeTree> subqueries = new ArrayList<>();

        if (level > MAX_RECURSION_DEPTH) {
            return subqueries;
        }

        if (select.getFrom() != null) {
            scanForSubqueriesInFrom(select.getFrom(), level, subqueries, visited);
        }

        return subqueries;
    }

    private void scanForSubqueriesInFrom(SqlNode node, int level, List<SqlNodeTree> subqueries, Set<String> visited) {
        if (node == null) {
            System.out.println("[scanForSubqueriesInFrom] node is null, return");
            return;
        }

        System.out.println("[scanForSubqueriesInFrom] level=" + level + ", node class=" + node.getClass().getSimpleName());

        // 处理 SqlJoin 类型（JOIN 操作的独立类）
        if (node instanceof SqlJoin) {
            SqlJoin join = (SqlJoin) node;
            System.out.println("[scanForSubqueriesInFrom] 发现SqlJoin, 遍历左右操作数");
            // 处理左操作数
            SqlNode leftNode = join.getLeft();
            if (leftNode != null) {
                System.out.println("[scanForSubqueriesInFrom] 处理左操作数: " + leftNode.getClass().getSimpleName());
                scanForSubqueriesInFrom(leftNode, level, subqueries, visited);
            }
            // 处理右操作数
            SqlNode rightNode = join.getRight();
            if (rightNode != null) {
                System.out.println("[scanForSubqueriesInFrom] 处理右操作数: " + rightNode.getClass().getSimpleName());
                scanForSubqueriesInFrom(rightNode, level, subqueries, visited);
            }
            return;
        }

        if (node instanceof SqlSelect) {
            System.out.println("[scanForSubqueriesInFrom] 发现SqlSelect子查询");
            SqlSelect subSelect = (SqlSelect) node;
            SqlNodeTree subTree = buildSelectTree(subSelect, level, visited);
            subTree.setType("SUBQUERY");
            subTree.setAlias("SUB" + subqueries.size());
            subTree.setDescription("FROM子句子查询 #" + subqueries.size());
            subqueries.add(subTree);
        } else if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;
            String operator = call.getOperator().getKind().toString();
            System.out.println("[scanForSubqueriesInFrom] SqlBasicCall, operator=" + operator + ", operandCount=" + call.operandCount());

            if ("JOIN".equals(operator) || "INNER".equals(operator) ||
                "LEFT".equals(operator) || "RIGHT".equals(operator) ||
                "FULL".equals(operator) || "CROSS".equals(operator)) {
                // JOIN结构：遍历所有操作数，包括左表、右表和ON条件
                for (int i = 0; i < call.operandCount(); i++) {
                    SqlNode operand = call.operand(i);
                    if (operand != null) {
                        // 如果是ON条件，需要递归处理其子节点
                        if (operand instanceof SqlBasicCall) {
                            SqlBasicCall operandCall = (SqlBasicCall) operand;
                            String opKind = operandCall.getOperator().getKind().toString();
                            if ("ON".equals(opKind)) {
                                // ON条件不作为子查询处理
                                System.out.println("[scanForSubqueriesInFrom] 跳过ON条件");
                                continue;
                            }
                        }
                        scanForSubqueriesInFrom(operand, level, subqueries, visited);
                    }
                }
            } else if ("AS".equals(operator)) {
                if (call.operandCount() >= 2) {
                    SqlNode leftOperand = call.operand(0);
                    SqlNode rightOperand = call.operand(1);
                    String alias = safeToString(rightOperand);
                    if (leftOperand instanceof SqlSelect) {
                        System.out.println("[scanForSubqueriesInFrom] 发现(SELECT ...) AS结构, 创建父节点 " + alias);
                        // 创建父节点表示别名
                        SqlNodeTree parentNode = new SqlNodeTree("SUBQUERY_PARENT", level);
                        parentNode.setAlias(alias);
                        parentNode.setType("SUBQUERY_PARENT");
                        parentNode.setDescription("子查询别名: " + alias);

                        // 构建子查询树作为子节点
                        SqlSelect subSelect = (SqlSelect) leftOperand;
                        SqlNodeTree subTree = buildSelectTree(subSelect, level + 1, visited);
                        subTree.setType("SUBQUERY");
                        subTree.setAlias("SUB" + subqueries.size());
                        subTree.setDescription("FROM子句子查询 #" + subqueries.size());

                        // 将子查询的子节点添加到父节点，而不是直接添加子查询本身
                        parentNode.getChildren().addAll(subTree.getChildren());
                        // 调整子节点的level
                        for (SqlNodeTree child : parentNode.getChildren()) {
                            child.setLevel(child.getLevel() - 1);
                        }
                        subqueries.add(parentNode);
                    } else {
                        System.out.println("[scanForSubqueriesInFrom] AS左边不是SqlSelect, 继续递归");
                        scanForSubqueriesInFrom(leftOperand, level, subqueries, visited);
                    }
                }
            } else {
                System.out.println("[scanForSubqueriesInFrom] 其他操作符, 遍历所有操作数");
                for (SqlNode operand : call.getOperandList()) {
                    scanForSubqueriesInFrom(operand, level, subqueries, visited);
                }
            }
        } else {
            System.out.println("[scanForSubqueriesInFrom] 未知节点类型: " + node.getClass().getSimpleName());
        }
    }

    /**
     * 从WHERE子句提取子查询
     */
    private void scanForSubqueriesInWhere(SqlNode node, int level, List<SqlNodeTree> subqueries, Set<String> visited) {
        if (node == null) return;

        if (node instanceof SqlSelect) {
            SqlSelect subSelect = (SqlSelect) node;
            SqlNodeTree subTree = buildSelectTree(subSelect, level, visited);
            subTree.setType("WHERE_SUBQUERY");
            subTree.setAlias("WHERE_SUB" + subqueries.size());
            subTree.setDescription("WHERE子句子查询 #" + subqueries.size());
            subqueries.add(subTree);
        } else if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;
            for (SqlNode operand : call.getOperandList()) {
                scanForSubqueriesInWhere(operand, level, subqueries, visited);
            }
        }
    }

    /**
     * 提取相关子查询 - 新增
     */
    private List<SqlNodeTree> extractCorrelatedSubqueries(SqlNode whereNode, int level, Set<String> visited) {
        List<SqlNodeTree> subqueries = new ArrayList<>();

        if (whereNode == null || level > MAX_RECURSION_DEPTH) {
            return subqueries;
        }

        findCorrelatedSubqueries(whereNode, level, subqueries, visited);

        return subqueries;
    }

    private void findCorrelatedSubqueries(SqlNode node, int level, List<SqlNodeTree> subqueries, Set<String> visited) {
        if (node == null) return;

        if (node instanceof SqlSelect) {
            SqlSelect subSelect = (SqlSelect) node;
            SqlNodeTree subTree = buildSelectTree(subSelect, level, visited);
            subTree.setType("CORRELATED_SUBQUERY");
            subTree.setAlias("CORR_SUB" + subqueries.size());
            subTree.setDescription("相关子查询 #" + subqueries.size());
            subqueries.add(subTree);
        } else if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;
            for (SqlNode operand : call.getOperandList()) {
                findCorrelatedSubqueries(operand, level, subqueries, visited);
            }
        }
    }

    private List<FieldLineage> analyzeLineage(SqlNodeTree tree) {
        List<FieldLineage> lineage = new ArrayList<>();
        Map<String, SqlNodeTree> fieldSources = new HashMap<>();
        analyzeLineageRecursive(tree, lineage, fieldSources, 0);
        return lineage;
    }

    private void analyzeLineageRecursive(SqlNodeTree node, List<FieldLineage> lineage,
                                          Map<String, SqlNodeTree> fieldSources, int depth) {
        // 注册当前层的字段来源
        if (node.getFields() != null) {
            for (FieldInfo field : node.getFields()) {
                String key = node.getLevel() + "_" + field.getName();
                fieldSources.put(key, node);
            }
        }

        // 分析当前层的字段血缘
        if (node.getFields() != null) {
            for (FieldInfo field : node.getFields()) {
                FieldLineage fieldLineage = analyzeFieldLineage(field, node, fieldSources);
                lineage.add(fieldLineage);
            }
        }

        // 递归处理子查询
        if (node.getChildren() != null) {
            for (SqlNodeTree child : node.getChildren()) {
                analyzeLineageRecursive(child, lineage, fieldSources, depth + 1);
            }
        }
    }

    private FieldLineage analyzeFieldLineage(FieldInfo field, SqlNodeTree currentNode,
                                              Map<String, SqlNodeTree> fieldSources) {
        FieldLineage fieldLineage = new FieldLineage();
        fieldLineage.setOutputField(field.getName());
        fieldLineage.setOutputTable("Level_" + currentNode.getLevel());
        fieldLineage.setExpression(field.getExpression());

        List<FieldLineage.LineageStep> steps = new ArrayList<>();
        String sourceExpr = field.getExpression() != null ? field.getExpression() : field.getName();
        steps.add(new FieldLineage.LineageStep(sourceExpr, field.getName(), "SELECT"));

        // 解析字段来源
        Set<String> sourceFields = new HashSet<>(field.getSourceFields());
        if (sourceFields.isEmpty()) {
            sourceFields.addAll(extractAllFields(field.getExpression()));
        }

        for (String sourceField : sourceFields) {
            FieldLineage.SourceField srcField = findSourceField(sourceField, fieldSources);
            if (srcField != null) {
                fieldLineage.addSourceField(srcField.getTable(), srcField.getField(), null);

                String operation = detectFunctionType(field.getExpression());
                steps.add(new FieldLineage.LineageStep(
                    srcField.getTable() + "." + srcField.getField(),
                    field.getExpression(),
                    operation
                ));
            } else {
                steps.add(new FieldLineage.LineageStep(
                    sourceField,
                    field.getExpression(),
                    "DIRECT"
                ));
            }
        }

        fieldLineage.setPath(steps);
        return fieldLineage;
    }

    private Set<String> extractAllFields(String expression) {
        Set<String> fields = new HashSet<>();
        if (expression == null) return fields;

        Pattern fieldPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = fieldPattern.matcher(expression);
        while (matcher.find()) {
            String field = matcher.group(1);
            if (isValidFieldName(field)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private boolean isValidFieldName(String name) {
        if (name == null || name.isEmpty() || name.length() > 100) return false;
        String upper = name.toUpperCase();
        Set<String> keywords = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "ON", "IN", "NOT",
            "GROUP", "BY", "ORDER", "HAVING", "CASE", "WHEN", "THEN", "ELSE", "END",
            "NULL", "AS", "IS", "LIKE", "BETWEEN", "EXISTS", "JOIN", "LEFT",
            "RIGHT", "INNER", "OUTER", "CROSS", "FULL", "UNION", "INSERT", "INTO",
            "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER",
            "INDEX", "VIEW", "TRIGGER", "PROCEDURE", "FUNCTION"
        );
        return !keywords.contains(upper) && !name.contains(" ") &&
               !name.contains("(") && !name.contains(")") && !name.contains(",");
    }

    private FieldLineage.SourceField findSourceField(String fieldName, Map<String, SqlNodeTree> fieldSources) {
        for (Map.Entry<String, SqlNodeTree> entry : fieldSources.entrySet()) {
            if (entry.getKey().contains("_" + fieldName.toUpperCase())) {
                SqlNodeTree node = entry.getValue();
                if (node.getTables() != null && !node.getTables().isEmpty()) {
                    TableReference table = node.getTables().get(0);
                    return new FieldLineage.SourceField(
                        table.getAlias() != null ? table.getAlias() : table.getName(),
                        fieldName,
                        null
                    );
                }
            }
        }
        return null;
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
        if (upper.contains("LENGTH(")) return "LENGTH";
        if (upper.contains("SUBSTR(") || upper.contains("SUBSTRING(")) return "SUBSTR";
        if (upper.contains("CONCAT(")) return "CONCAT";
        return "AGGREGATE";
    }

    /**
     * 查找最深层的SELECT节点
     */
    private SqlNodeTree findDeepestSelect(SqlNodeTree node) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return null;
        }
        SqlNodeTree child = node.getChildren().get(0);
        if ("SUBQUERY_PARENT".equals(child.getType())) {
            return findDeepestSelect(child);
        } else {
            return child;
        }
    }
}
