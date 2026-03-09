package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FunctionOutput {
    @JsonProperty("type")
    private String type;

    @JsonProperty("description")
    private String description;

    @JsonIgnore
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @JsonIgnore
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
