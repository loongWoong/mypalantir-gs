package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FunctionParam {
    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("description")
    private String description;

    @JsonProperty("default")
    private Object defaultValue;

    @JsonIgnore
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @JsonIgnore
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @JsonIgnore
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @JsonIgnore
    public Object getDefaultValue() { return defaultValue; }
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
}
