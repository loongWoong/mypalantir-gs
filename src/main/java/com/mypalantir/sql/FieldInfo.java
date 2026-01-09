package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FieldInfo {
    @JsonProperty("name")
    private String name;

    @JsonProperty("alias")
    private String alias;

    @JsonProperty("table")
    private String table;

    @JsonProperty("dataType")
    private String dataType;

    @JsonProperty("isAggregated")
    private boolean isAggregated;

    @JsonProperty("expression")
    private String expression;

    public FieldInfo() {}

    public FieldInfo(String name, String alias, String table) {
        this.name = name;
        this.alias = alias;
        this.table = table;
        this.isAggregated = false;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public boolean isAggregated() { return isAggregated; }
    public void setAggregated(boolean aggregated) { isAggregated = aggregated; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
}
