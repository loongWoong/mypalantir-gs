package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class LinkType {
    @JsonProperty("name")
    private String name;

    @JsonProperty("display_name")
    private String displayName;

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

    @JsonProperty("transformation_mappings")
    private List<TransformationMapping> transformationMappings;

    @JsonProperty("data_source")
    private DataSourceMapping dataSource;

    @JsonProperty("url")
    private String url;

    @JsonIgnore
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonGetter("display_name")
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

    @JsonGetter("properties")
    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    @JsonGetter("property_mappings")
    public Map<String, String> getPropertyMappings() {
        return propertyMappings;
    }

    public void setPropertyMappings(Map<String, String> propertyMappings) {
        this.propertyMappings = propertyMappings;
    }

    @JsonGetter("data_source")
    public DataSourceMapping getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSourceMapping dataSource) {
        this.dataSource = dataSource;
    }

    @JsonIgnore
    public boolean hasDataSource() {
        return dataSource != null && dataSource.isConfigured();
    }
    
    @JsonGetter("url")
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @JsonGetter("transformation_mappings")
    public List<TransformationMapping> getTransformationMappings() {
        return transformationMappings;
    }
    
    public void setTransformationMappings(List<TransformationMapping> transformationMappings) {
        this.transformationMappings = transformationMappings;
    }
    
    /**
     * 检查是否有转换映射配置
     */
    @JsonIgnore
    public boolean hasTransformationMappings() {
        return transformationMappings != null && !transformationMappings.isEmpty();
    }
}
