package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 函数定义：用于规则前件中的算法调用（builtin/external），可与 link/衍生属性绑定参数来源。
 */
public class Function {
    @JsonProperty("name")
    private String name;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("description")
    private String description;

    /** 实现类型：builtin | external */
    @JsonProperty("implementation")
    private String implementation;

    @JsonProperty("inputs")
    private List<FunctionInput> inputs;

    @JsonProperty("output_type")
    private String outputType;

    /** 可选：参数默认数据来源（link 或衍生属性），便于规则引擎/智能体解析 */
    @JsonProperty("parameter_bindings")
    private List<ParameterBinding> parameterBindings;

    @JsonIgnore
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonIgnore
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @JsonIgnore
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonIgnore
    public String getImplementation() {
        return implementation;
    }

    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }

    @JsonIgnore
    public List<FunctionInput> getInputs() {
        return inputs;
    }

    public void setInputs(List<FunctionInput> inputs) {
        this.inputs = inputs;
    }

    @JsonIgnore
    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    @JsonIgnore
    public List<ParameterBinding> getParameterBindings() {
        return parameterBindings;
    }

    public void setParameterBindings(List<ParameterBinding> parameterBindings) {
        this.parameterBindings = parameterBindings;
    }

    public static class FunctionInput {
        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type;

        @JsonIgnore
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonIgnore
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class ParameterBinding {
        @JsonProperty("parameter_name")
        private String parameterName;

        /** link | derived_attribute */
        @JsonProperty("source_type")
        private String sourceType;

        @JsonProperty("link_name")
        private String linkName;

        @JsonProperty("object_type")
        private String objectType;

        @JsonProperty("attribute_name")
        private String attributeName;

        @JsonIgnore
        public String getParameterName() {
            return parameterName;
        }

        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }

        @JsonIgnore
        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        @JsonIgnore
        public String getLinkName() {
            return linkName;
        }

        public void setLinkName(String linkName) {
            this.linkName = linkName;
        }

        @JsonIgnore
        public String getObjectType() {
            return objectType;
        }

        public void setObjectType(String objectType) {
            this.objectType = objectType;
        }

        @JsonIgnore
        public String getAttributeName() {
            return attributeName;
        }

        public void setAttributeName(String attributeName) {
            this.attributeName = attributeName;
        }
    }
}
