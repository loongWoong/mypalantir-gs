package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class FieldLineage {
    @JsonProperty("outputField")
    private String outputField;

    @JsonProperty("outputTable")
    private String outputTable;

    @JsonProperty("expression")
    private String expression;

    @JsonProperty("function")
    private String function;

    @JsonProperty("sourceFields")
    private List<SourceField> sourceFields;

    @JsonProperty("path")
    private List<LineageStep> path;

    @JsonProperty("transformations")
    private List<String> transformations;

    public FieldLineage() {
        this.sourceFields = new ArrayList<>();
        this.path = new ArrayList<>();
        this.transformations = new ArrayList<>();
    }

    public void addSourceField(String table, String field, String alias) {
        this.sourceFields.add(new SourceField(table, field, alias));
    }

    public void addStep(String from, String to, String operation) {
        this.path.add(new LineageStep(from, to, operation));
    }

    // getters and setters
    public String getOutputField() { return outputField; }
    public void setOutputField(String outputField) { this.outputField = outputField; }
    public String getOutputTable() { return outputTable; }
    public void setOutputTable(String outputTable) { this.outputTable = outputTable; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }
    public List<SourceField> getSourceFields() { return sourceFields; }
    public void setSourceFields(List<SourceField> sourceFields) { this.sourceFields = sourceFields; }
    public List<LineageStep> getPath() { return path; }
    public void setPath(List<LineageStep> path) { this.path = path; }
    public List<String> getTransformations() { return transformations; }
    public void setTransformations(List<String> transformations) { this.transformations = transformations; }

    public static class SourceField {
        @JsonProperty("table")
        private String table;

        @JsonProperty("field")
        private String field;

        @JsonProperty("alias")
        private String alias;

        public SourceField() {}

        public SourceField(String table, String field, String alias) {
            this.table = table;
            this.field = field;
            this.alias = alias;
        }

        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
    }

    public static class LineageStep {
        @JsonProperty("from")
        private String from;

        @JsonProperty("to")
        private String to;

        @JsonProperty("operation")
        private String operation;

        public LineageStep() {}

        public LineageStep(String from, String to, String operation) {
            this.from = from;
            this.to = to;
            this.operation = operation;
        }

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
    }
}
