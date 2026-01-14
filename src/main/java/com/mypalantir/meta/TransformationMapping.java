package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 字段转换映射配置
 * 用于定义视图中的推导字段与源表原始字段之间的转换关系
 */
public class TransformationMapping {
    @JsonProperty("source_property")
    private String sourceProperty;
    
    @JsonProperty("target_property")
    private String targetProperty;
    
    @JsonProperty("transformation_type")
    private String transformationType;  // reverse_derivation, lookup, function, direct
    
    @JsonProperty("transformation_config")
    private Map<String, Object> transformationConfig;
    
    public String getSourceProperty() {
        return sourceProperty;
    }
    
    public void setSourceProperty(String sourceProperty) {
        this.sourceProperty = sourceProperty;
    }
    
    public String getTargetProperty() {
        return targetProperty;
    }
    
    public void setTargetProperty(String targetProperty) {
        this.targetProperty = targetProperty;
    }
    
    public String getTransformationType() {
        return transformationType;
    }
    
    public void setTransformationType(String transformationType) {
        this.transformationType = transformationType;
    }
    
    public Map<String, Object> getTransformationConfig() {
        return transformationConfig;
    }
    
    public void setTransformationConfig(Map<String, Object> transformationConfig) {
        this.transformationConfig = transformationConfig;
    }
    
    /**
     * 获取 JOIN 条件 SQL 表达式
     */
    public String getJoinCondition() {
        if (transformationConfig != null) {
            Object joinCondition = transformationConfig.get("join_condition");
            if (joinCondition != null) {
                return joinCondition.toString();
            }
        }
        return null;
    }
    
    /**
     * 获取 SQL 转换表达式
     */
    public String getSqlExpression() {
        if (transformationConfig != null) {
            Object sqlExpression = transformationConfig.get("sql_expression");
            if (sqlExpression != null) {
                return sqlExpression.toString();
            }
        }
        return null;
    }
    
    /**
     * 是否使用 derivation_rule 中定义的反向转换
     */
    public boolean useDerivationRule() {
        if (transformationConfig != null) {
            Object useRule = transformationConfig.get("use_derivation_rule");
            return useRule != null && Boolean.parseBoolean(useRule.toString());
        }
        return false;
    }
}
