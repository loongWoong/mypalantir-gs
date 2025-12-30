package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class LinkType {
    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("source_type")
    private String sourceType;

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("cardinality")
    private String cardinality;

    @JsonProperty("direction")
    private String direction;

    @JsonProperty("properties")
    private List<Property> properties;

    @JsonProperty("property_mappings")
    private Map<String, String> propertyMappings;

    @JsonIgnore
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonIgnore
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonIgnore
    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    @JsonIgnore
    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    @JsonIgnore
    public String getCardinality() {
        return cardinality;
    }

    public void setCardinality(String cardinality) {
        this.cardinality = cardinality;
    }

    @JsonIgnore
    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    @JsonIgnore
    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    @JsonIgnore
    public Map<String, String> getPropertyMappings() {
        return propertyMappings;
    }

    public void setPropertyMappings(Map<String, String> propertyMappings) {
        this.propertyMappings = propertyMappings;
    }
}
