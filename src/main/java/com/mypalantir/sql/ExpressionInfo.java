package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExpressionInfo {
    @JsonProperty("type")
    private String type;

    @JsonProperty("expression")
    private String expression;

    @JsonProperty("function")
    private String function;

    @JsonProperty("condition")
    private String condition;

    public ExpressionInfo() {}

    public ExpressionInfo(String type, String expression) {
        this.type = type;
        this.expression = expression;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
}
