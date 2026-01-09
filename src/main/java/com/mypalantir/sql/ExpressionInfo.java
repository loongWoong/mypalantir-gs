package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class ExpressionInfo {
    @JsonProperty("type")
    private String type;

    @JsonProperty("expression")
    private String expression;

    @JsonProperty("function")
    private String function;

    @JsonProperty("condition")
    private String condition;

    @JsonProperty("nestedExpressions")
    private List<ExpressionInfo> nestedExpressions;

    @JsonProperty("sourceFields")
    private List<String> sourceFields;

    @JsonProperty("depth")
    private int depth;

    public ExpressionInfo() {
        this.nestedExpressions = new ArrayList<>();
        this.sourceFields = new ArrayList<>();
        this.depth = 0;
    }

    public ExpressionInfo(String type, String expression) {
        this();
        this.type = type;
        this.expression = expression;
    }

    public void addNestedExpression(ExpressionInfo expr) {
        this.nestedExpressions.add(expr);
    }

    public void addSourceField(String field) {
        if (!this.sourceFields.contains(field)) {
            this.sourceFields.add(field);
        }
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public List<ExpressionInfo> getNestedExpressions() { return nestedExpressions; }
    public void setNestedExpressions(List<ExpressionInfo> nestedExpressions) { this.nestedExpressions = nestedExpressions; }
    public List<String> getSourceFields() { return sourceFields; }
    public void setSourceFields(List<String> sourceFields) { this.sourceFields = sourceFields; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
}
