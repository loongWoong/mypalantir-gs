package com.mypalantir.sql;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SubqueryContext {
    private String alias;
    private Set<String> outputFields;
    private Set<String> inputFields;
    private Map<String, String> fieldSources;
    private SubqueryContext parent;

    public SubqueryContext() {
        this.outputFields = new HashSet<>();
        this.inputFields = new HashSet<>();
        this.fieldSources = new HashMap<>();
    }

    public SubqueryContext(String alias, SubqueryContext parent) {
        this();
        this.alias = alias;
        this.parent = parent;
    }

    public void addOutputField(String field) {
        outputFields.add(field);
    }

    public void addInputField(String field) {
        inputFields.add(field);
    }

    public void addFieldSource(String field, String source) {
        fieldSources.put(field, source);
    }

    public boolean containsOutputField(String field) {
        return outputFields.contains(field) ||
               (parent != null && parent.containsOutputField(field));
    }

    public boolean containsInputField(String field) {
        return inputFields.contains(field) ||
               (parent != null && parent.containsInputField(field));
    }

    public String getFieldSource(String field) {
        if (fieldSources.containsKey(field)) {
            return fieldSources.get(field);
        }
        if (parent != null) {
            return parent.getFieldSource(field);
        }
        return null;
    }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public Set<String> getOutputFields() { return outputFields; }
    public Set<String> getInputFields() { return inputFields; }
    public Map<String, String> getFieldSources() { return fieldSources; }
    public SubqueryContext getParent() { return parent; }
    public void setParent(SubqueryContext parent) { this.parent = parent; }

    public SubqueryContext createChildContext(String childAlias) {
        return new SubqueryContext(childAlias, this);
    }
}
