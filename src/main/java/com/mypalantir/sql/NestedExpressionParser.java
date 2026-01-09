package com.mypalantir.sql;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlCaseOperator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NestedExpressionParser {

    private static final int MAX_DEPTH = 10;
    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
        Pattern.CASE_INSENSITIVE
    );

    public ExpressionInfo parse(SqlNode node) {
        ExpressionInfo rootInfo = new ExpressionInfo();
        rootInfo.setExpression(safeToString(node));
        rootInfo.setDepth(0);
        parseRecursive(node, rootInfo, 1);
        return rootInfo;
    }

    private void parseRecursive(SqlNode node, ExpressionInfo parentInfo, int depth) {
        if (node == null || depth > MAX_DEPTH) return;

        String nodeStr = node.toString().toUpperCase();

        if (node instanceof SqlBasicCall) {
            SqlBasicCall call = (SqlBasicCall) node;

            if (call.getOperator() instanceof SqlCaseOperator ||
                nodeStr.startsWith("CASE")) {
                parseCaseExpression(call, parentInfo, depth);
            } else if (isFunctionOperator(call.getOperator())) {
                String funcName = call.getOperator().getName().toUpperCase();
                parseFunctionCall(funcName, call, parentInfo, depth);
            } else {
                SqlKind kind = call.getOperator().getKind();
                if (kind == SqlKind.AS) {
                    parseRecursive(call.operand(0), parentInfo, depth);
                } else if (kind == SqlKind.AND || kind == SqlKind.OR) {
                    parseLogicalExpression(call, parentInfo, depth);
                } else {
                    for (SqlNode operand : call.getOperandList()) {
                        parseRecursive(operand, parentInfo, depth + 1);
                    }
                }
            }
        } else if (node instanceof SqlIdentifier) {
            parseIdentifier((SqlIdentifier) node, parentInfo);
        } else if (node instanceof SqlSelect) {
            parseSubquery((SqlSelect) node, parentInfo);
        }
    }

    private boolean isFunctionOperator(SqlOperator op) {
        String name = op.getName().toUpperCase();
        return name.equals("SUM") || name.equals("AVG") || name.equals("COUNT") ||
               name.equals("MIN") || name.equals("MAX") || name.equals("IFNULL") ||
               name.equals("COALESCE") || name.equals("NVL") || name.equals("SUBSTR") ||
               name.equals("SUBSTRING") || name.equals("CONCAT") || name.equals("LENGTH") ||
               name.equals("ABS") || name.equals("ROUND") || name.equals("FLOOR") ||
               name.equals("CEIL") || name.equals("SQRT") || name.equals("POWER");
    }

    private void parseCaseExpression(SqlBasicCall call, ExpressionInfo parentInfo, int depth) {
        ExpressionInfo caseExpr = new ExpressionInfo();
        caseExpr.setType("CASE_WHEN");
        caseExpr.setExpression(safeToString(call));
        caseExpr.setFunction("CASE");
        caseExpr.setDepth(depth);

        for (SqlNode operand : call.getOperandList()) {
            parseRecursive(operand, caseExpr, depth + 1);
        }

        parentInfo.getNestedExpressions().add(caseExpr);
        extractSourceFieldsFromExpression(call.toString(), parentInfo);
    }

    private void parseFunctionCall(String funcName, SqlBasicCall call, ExpressionInfo parentInfo, int depth) {
        ExpressionInfo funcExpr = new ExpressionInfo();
        funcExpr.setType(getExpressionType(funcName));
        funcExpr.setFunction(funcName);
        funcExpr.setExpression(safeToString(call));
        funcExpr.setDepth(depth);

        for (SqlNode operand : call.getOperandList()) {
            parseRecursive(operand, funcExpr, depth + 1);
        }

        parentInfo.getNestedExpressions().add(funcExpr);

        if (isAggregateOrTransform(funcName)) {
            extractSourceFieldsFromExpression(call.toString(), parentInfo);
        }
    }

    private void parseLogicalExpression(SqlBasicCall call, ExpressionInfo parentInfo, int depth) {
        ExpressionInfo logicalExpr = new ExpressionInfo();
        logicalExpr.setType("LOGICAL");
        logicalExpr.setExpression(safeToString(call));
        logicalExpr.setFunction(call.getOperator().getKind().toString());
        logicalExpr.setDepth(depth);

        for (SqlNode operand : call.getOperandList()) {
            parseRecursive(operand, logicalExpr, depth + 1);
        }

        parentInfo.getNestedExpressions().add(logicalExpr);
    }

    private void parseIdentifier(SqlIdentifier identifier, ExpressionInfo parentInfo) {
        String fieldName = safeToString(identifier);
        if (isValidFieldName(fieldName) && !parentInfo.getSourceFields().contains(fieldName)) {
            parentInfo.getSourceFields().add(fieldName);
        }
    }

    private void parseSubquery(SqlSelect select, ExpressionInfo parentInfo) {
        ExpressionInfo subqueryExpr = new ExpressionInfo();
        subqueryExpr.setType("SUBQUERY");
        subqueryExpr.setExpression(safeToString(select));
        subqueryExpr.setDepth(1);
        parentInfo.getNestedExpressions().add(subqueryExpr);
    }

    private void extractSourceFieldsFromExpression(String exprStr, ExpressionInfo parentInfo) {
        Set<String> fields = new HashSet<>();

        Matcher matcher = FIELD_PATTERN.matcher(exprStr);
        while (matcher.find()) {
            String field = matcher.group(1);
            if (isValidFieldName(field) && !isSQLKeyword(field)) {
                fields.add(field);
            }
        }

        for (String field : fields) {
            if (!parentInfo.getSourceFields().contains(field)) {
                parentInfo.getSourceFields().add(field);
            }
        }
    }

    private String getExpressionType(String funcName) {
        switch (funcName) {
            case "SUM": case "AVG": case "COUNT": case "MIN": case "MAX":
                return "AGGREGATE";
            case "IFNULL": case "COALESCE": case "NVL":
                return "FUNCTION";
            case "CASE":
                return "CASE_WHEN";
            default:
                return "FUNCTION";
        }
    }

    private boolean isAggregateOrTransform(String funcName) {
        String upper = funcName.toUpperCase();
        return upper.equals("SUM") || upper.equals("AVG") || upper.equals("COUNT") ||
               upper.equals("MIN") || upper.equals("MAX") || upper.equals("IFNULL") ||
               upper.equals("COALESCE") || upper.equals("NVL") || upper.equals("CASE") ||
               upper.equals("SUBSTR") || upper.equals("SUBSTRING") || upper.equals("CONCAT") ||
               upper.equals("LENGTH");
    }

    private boolean isValidFieldName(String name) {
        if (name == null || name.isEmpty() || name.length() > 100) return false;
        String upper = name.toUpperCase();
        return !isSQLKeyword(upper) && !name.contains(" ") && !name.contains("(") &&
               !name.contains(")") && !name.contains(",");
    }

    private boolean isSQLKeyword(String word) {
        Set<String> keywords = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "ON", "IN", "NOT",
            "GROUP", "BY", "ORDER", "HAVING", "CASE", "WHEN", "THEN", "ELSE", "END",
            "NULL", "AS", "IS", "LIKE", "BETWEEN", "EXISTS", "JOIN", "LEFT",
            "RIGHT", "INNER", "OUTER", "CROSS", "FULL", "UNION", "INSERT", "INTO",
            "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER",
            "INDEX", "VIEW", "TRIGGER", "PROCEDURE", "FUNCTION", "DATABASE", "SCHEMA",
            "TRUE", "FALSE"
        );
        return keywords.contains(word.toUpperCase());
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

    public List<String> extractAllFields(String expression) {
        List<String> fields = new ArrayList<>();
        if (expression == null) return fields;

        Matcher matcher = FIELD_PATTERN.matcher(expression);
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String field = matcher.group(1);
            if (isValidFieldName(field) && !seen.contains(field.toLowerCase())) {
                seen.add(field.toLowerCase());
                fields.add(field);
            }
        }
        return fields;
    }
}
