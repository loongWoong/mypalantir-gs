package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 数据源映射配置
 * 定义 ObjectType 如何映射到数据库表
 */
public class DataSourceMapping {
    /**
     * 数据源连接 ID，引用 data_sources 中的配置
     */
    @JsonProperty("connection_id")
    private String connectionId;

    /**
     * 数据库表名
     */
    @JsonProperty("table")
    private String table;

    /**
     * 主键列名（用于唯一标识实例）
     */
    @JsonProperty("id_column")
    private String idColumn;

    /**
     * 源对象ID列名（用于 LinkType，表示 source_type 的 ID）
     * 例如：对于"持有" link type，source_id_column 为 "vehicle_id"
     */
    @JsonProperty("source_id_column")
    private String sourceIdColumn;

    /**
     * 目标对象ID列名（用于 LinkType，表示 target_type 的 ID）
     * 例如：对于"持有" link type，target_id_column 为 "media_id"
     */
    @JsonProperty("target_id_column")
    private String targetIdColumn;

    /**
     * 属性映射：ObjectType/LinkType 属性名 -> 数据库列名
     * 例如：{"车牌号": "plate_number", "车辆类型": "vehicle_type"}
     * 对于 LinkType，还可以映射 link type 的属性，如 {"绑定时间": "bind_time", "绑定状态": "bind_status"}
     */
    @JsonProperty("field_mapping")
    private Map<String, String> fieldMapping;

    @JsonGetter("connection_id")
    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    @JsonGetter("table")
    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    @JsonGetter("id_column")
    public String getIdColumn() {
        return idColumn;
    }

    public void setIdColumn(String idColumn) {
        this.idColumn = idColumn;
    }

    @JsonGetter("source_id_column")
    public String getSourceIdColumn() {
        return sourceIdColumn;
    }

    public void setSourceIdColumn(String sourceIdColumn) {
        this.sourceIdColumn = sourceIdColumn;
    }

    @JsonGetter("target_id_column")
    public String getTargetIdColumn() {
        return targetIdColumn;
    }

    public void setTargetIdColumn(String targetIdColumn) {
        this.targetIdColumn = targetIdColumn;
    }

    @JsonGetter("field_mapping")
    public Map<String, String> getFieldMapping() {
        return fieldMapping;
    }

    public void setFieldMapping(Map<String, String> fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    /**
     * 根据 ObjectType 属性名获取数据库列名
     */
    public String getColumnName(String propertyName) {
        if (fieldMapping == null) {
            return null;
        }
        return fieldMapping.get(propertyName);
    }

    /**
     * 根据数据库列名获取 ObjectType 属性名（反向查找）
     */
    public String getPropertyName(String columnName) {
        if (fieldMapping == null || columnName == null) {
            return null;
        }
        return fieldMapping.entrySet().stream()
            .filter(entry -> columnName.equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    /**
     * 检查是否有数据源映射配置
     */
    public boolean isConfigured() {
        return connectionId != null && !connectionId.isEmpty() 
            && table != null && !table.isEmpty()
            && idColumn != null && !idColumn.isEmpty();
    }
}

