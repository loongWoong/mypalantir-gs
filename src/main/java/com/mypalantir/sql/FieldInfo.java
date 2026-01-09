package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

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

    @JsonProperty("sourceFields")
    private List<String> sourceFields;

    @JsonProperty("isCaseWhen")
    private boolean isCaseWhen;

    @JsonProperty("isIfNull")
    private boolean isIfNull;

    public FieldInfo() {
        this.sourceFields = new ArrayList<>();
        this.isAggregated = false;
        this.isCaseWhen = false;
        this.isIfNull = false;
    }

    public FieldInfo(String name, String alias, String table) {
        this();
        this.name = name;
        this.alias = alias;
        this.table = table;
    }

    public void addSourceField(String field) {
        if (!this.sourceFields.contains(field)) {
            this.sourceFields.add(field);
        }
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
    public List<String> getSourceFields() { return sourceFields; }
    public void setSourceFields(List<String> sourceFields) { this.sourceFields = sourceFields; }
    public boolean isCaseWhen() { return isCaseWhen; }
    public void setCaseWhen(boolean caseWhen) { isCaseWhen = caseWhen; }
    public boolean isIfNull() { return isIfNull; }
    public void setIfNull(boolean ifNull) { isIfNull = ifNull; }
}
