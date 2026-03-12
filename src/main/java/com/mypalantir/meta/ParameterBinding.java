package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ParameterBinding {
    @JsonProperty("parameter_name")
    private String parameterName;

    @JsonProperty("source_type")
    private String sourceType;

    @JsonProperty("link_name")
    private String linkName;

    @JsonProperty("object_type")
    private String objectType;

    @JsonProperty("attribute_name")
    private String attributeName;

    @JsonIgnore
    public String getParameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = parameterName; }

    @JsonIgnore
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    @JsonIgnore
    public String getLinkName() { return linkName; }
    public void setLinkName(String linkName) { this.linkName = linkName; }

    @JsonIgnore
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    @JsonIgnore
    public String getAttributeName() { return attributeName; }
    public void setAttributeName(String attributeName) { this.attributeName = attributeName; }
}
