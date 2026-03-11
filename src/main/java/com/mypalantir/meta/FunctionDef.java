package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class FunctionDef {
    @JsonProperty("name")
    private String name;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("input")
    @JsonAlias("parameters")
    private List<FunctionParam> input;

    @JsonProperty("output")
    private FunctionOutput output;

    @JsonProperty("implementation")
    private String implementation;

    /** 脚本实现时使用：脚本路径，相对于 functions/script/ 或类路径，如 toll/check_xxx.js */
    @JsonProperty("script_path")
    private String scriptPath;

    @JsonIgnore
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @JsonIgnore
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    @JsonIgnore
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @JsonIgnore
    public List<FunctionParam> getInput() { return input; }
    public void setInput(List<FunctionParam> input) { this.input = input; }

    @JsonIgnore
    public FunctionOutput getOutput() { return output; }
    public void setOutput(FunctionOutput output) { this.output = output; }

    @JsonIgnore
    public String getImplementation() { return implementation; }
    public void setImplementation(String implementation) { this.implementation = implementation; }

    @JsonIgnore
    public String getScriptPath() { return scriptPath; }
    public void setScriptPath(String scriptPath) { this.scriptPath = scriptPath; }
}
