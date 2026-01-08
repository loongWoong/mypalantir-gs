package com.mypalantir.sqlparse;

import org.apache.calcite.sql.SqlNode;
import java.util.ArrayList;
import java.util.List;

public class CalciteSqlParseResult {

    public enum AggregationType {
        NONE, SUM, AVG, COUNT, MIN, MAX, COUNT_DISTINCT
    }

    public enum FieldType {
        DIMENSION, MEASURE, TIME, METRIC
    }

    public static class TableReference {
        private String tableName;
        private String alias;
        private boolean mainTable;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public boolean isMainTable() { return mainTable; }
        public void setMainTable(boolean mainTable) { this.mainTable = mainTable; }
    }
    private String originalSql;
    private String normalizedSql;
    private SqlNode sqlNode;
    private List<TableReference> tables = new ArrayList<>();
    private List<AggregationInfo> aggregations = new ArrayList<>();
    private List<WhereCondition> whereConditions = new ArrayList<>();
    private List<TimeCondition> timeConditions = new ArrayList<>();
    private List<String> groupByFields = new ArrayList<>();
    private List<SelectField> selectFields = new ArrayList<>();
    private List<JoinInfo> joins = new ArrayList<>();
    private boolean hasGroupBy = false;
    private List<ValidationInfo> validations = new ArrayList<>();



    public static class AggregationInfo {
        private String type;
        private String field;
        private String alias;
        private String expression;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
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

    public static class SelectField {
        private String rawExpression;
        private String fieldName;
        private String tableAlias;
        private String alias;
        private String aggregationType = null;
        private boolean isAggregated;
        private String fieldType = "DIMENSION";

        public String getRawExpression() { return rawExpression; }
        public void setRawExpression(String rawExpression) { this.rawExpression = rawExpression; }
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getTableAlias() { return tableAlias; }
        public void setTableAlias(String tableAlias) { this.tableAlias = tableAlias; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public String getAggregationType() { return aggregationType; }
        public void setAggregationType(String aggregationType) { this.aggregationType = aggregationType; }
        public boolean isAggregated() { return isAggregated; }
        public void setAggregated(boolean aggregated) { isAggregated = aggregated; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
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

    public static class ValidationInfo {
        private String field;
        private boolean hasErrors;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public boolean hasErrors() { return hasErrors; }
        public void setHasErrors(boolean hasErrors) { this.hasErrors = hasErrors; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }

    public String getOriginalSql() { return originalSql; }
    public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
    public String getNormalizedSql() { return normalizedSql; }
    public void setNormalizedSql(String normalizedSql) { this.normalizedSql = normalizedSql; }
    public SqlNode getSqlNode() { return sqlNode; }
    public void setSqlNode(SqlNode sqlNode) { this.sqlNode = sqlNode; }
    public List<TableReference> getTables() { return tables; }
    public void setTables(List<TableReference> tables) { this.tables = tables; }
    public List<AggregationInfo> getAggregations() { return aggregations; }
    public void setAggregations(List<AggregationInfo> aggregations) { this.aggregations = aggregations; }
    public List<WhereCondition> getWhereConditions() { return whereConditions; }
    public void setWhereConditions(List<WhereCondition> whereConditions) { this.whereConditions = whereConditions; }
    public List<TimeCondition> getTimeConditions() { return timeConditions; }
    public void setTimeConditions(List<TimeCondition> timeConditions) { this.timeConditions = timeConditions; }
    public List<String> getGroupByFields() { return groupByFields; }
    public void setGroupByFields(List<String> groupByFields) { this.groupByFields = groupByFields; }
    public List<SelectField> getSelectFields() { return selectFields; }
    public void setSelectFields(List<SelectField> selectFields) { this.selectFields = selectFields; }
    public List<JoinInfo> getJoins() { return joins; }
    public void setJoins(List<JoinInfo> joins) { this.joins = joins; }
    public boolean isHasGroupBy() { return hasGroupBy; }
    public void setHasGroupBy(boolean hasGroupBy) { this.hasGroupBy = hasGroupBy; }
    public List<ValidationInfo> getValidations() { return validations; }
    public void setValidations(List<ValidationInfo> validations) { this.validations = validations; }
}
