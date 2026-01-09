package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SqlParseResult {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("originalSql")
    private String originalSql;

    @JsonProperty("tree")
    private SqlNodeTree tree;

    @JsonProperty("lineage")
    private List<FieldLineage> lineage;

    @JsonProperty("statistics")
    private ParseStatistics statistics;

    @JsonProperty("error")
    private String error;

    public SqlParseResult() {}

    public static SqlParseResult success(String sql, SqlNodeTree tree, List<FieldLineage> lineage) {
        SqlParseResult result = new SqlParseResult();
        result.success = true;
        result.originalSql = sql;
        result.tree = tree;
        result.lineage = lineage;
        result.statistics = calculateStatistics(tree, lineage);
        return result;
    }

    public static SqlParseResult error(String sql, String errorMessage) {
        SqlParseResult result = new SqlParseResult();
        result.success = false;
        result.originalSql = sql;
        result.error = errorMessage;
        return result;
    }

    private static ParseStatistics calculateStatistics(SqlNodeTree tree, List<FieldLineage> lineage) {
        ParseStatistics stats = new ParseStatistics();
        if (tree != null) {
            stats.setTotalLevels(countLevels(tree, 0));
            stats.setTotalTables(countTables(tree));
            stats.setTotalJoins(countJoins(tree));
            stats.setTotalSubqueries(countSubqueries(tree));
        }
        if (lineage != null) {
            stats.setTotalFields(lineage.size());
        }
        return stats;
    }

    private static int countLevels(SqlNodeTree node, int depth) {
        if (node == null) return depth;
        int maxDepth = depth;
        if (node.getChildren() != null) {
            for (SqlNodeTree child : node.getChildren()) {
                maxDepth = Math.max(maxDepth, countLevels(child, depth + 1));
            }
        }
        return maxDepth + 1;
    }

    private static int countTables(SqlNodeTree node) {
        if (node == null) return 0;
        int count = node.getTables() != null ? node.getTables().size() : 0;
        if (node.getChildren() != null) {
            for (SqlNodeTree child : node.getChildren()) {
                count += countTables(child);
            }
        }
        return count;
    }

    private static int countJoins(SqlNodeTree node) {
        if (node == null) return 0;
        int count = "JOIN".equalsIgnoreCase(node.getType()) ? 1 : 0;
        if (node.getChildren() != null) {
            for (SqlNodeTree child : node.getChildren()) {
                count += countJoins(child);
            }
        }
        return count;
    }

    private static int countSubqueries(SqlNodeTree node) {
        if (node == null) return 0;
        int count = "SUBQUERY".equalsIgnoreCase(node.getType()) ? 1 : 0;
        if (node.getChildren() != null) {
            for (SqlNodeTree child : node.getChildren()) {
                count += countSubqueries(child);
            }
        }
        return count;
    }

    // getters and setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getOriginalSql() { return originalSql; }
    public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
    public SqlNodeTree getTree() { return tree; }
    public void setTree(SqlNodeTree tree) { this.tree = tree; }
    public List<FieldLineage> getLineage() { return lineage; }
    public void setLineage(List<FieldLineage> lineage) { this.lineage = lineage; }
    public ParseStatistics getStatistics() { return statistics; }
    public void setStatistics(ParseStatistics statistics) { this.statistics = statistics; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
