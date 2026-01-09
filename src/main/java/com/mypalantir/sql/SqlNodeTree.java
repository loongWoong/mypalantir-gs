package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlNodeTree {
    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("level")
    private int level;

    @JsonProperty("sql")
    private String sql;

    @JsonProperty("alias")
    private String alias;

    @JsonProperty("description")
    private String description;

    @JsonProperty("tables")
    private List<TableReference> tables;

    @JsonProperty("joins")
    private List<JoinInfo> joins;

    @JsonProperty("fields")
    private List<FieldInfo> fields;

    @JsonProperty("expressions")
    private List<ExpressionInfo> expressions;

    @JsonProperty("children")
    private List<SqlNodeTree> children;

    @JsonProperty("joinCondition")
    private String joinCondition;

    @JsonProperty("whereCondition")
    private String whereCondition;

    @JsonProperty("groupBy")
    private List<String> groupBy;

    @JsonProperty("orderBy")
    private List<String> orderBy;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    public SqlNodeTree() {
        this.id = java.util.UUID.randomUUID().toString();
        this.tables = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.expressions = new ArrayList<>();
        this.children = new ArrayList<>();
        this.joins = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    public SqlNodeTree(String type, int level) {
        this();
        this.type = type;
        this.level = level;
    }

    public void addChild(SqlNodeTree child) {
        this.children.add(child);
    }

    public void addJoin(JoinInfo join) {
        this.joins.add(join);
    }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }

    // getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<TableReference> getTables() { return tables; }
    public void setTables(List<TableReference> tables) { this.tables = tables; }
    public List<JoinInfo> getJoins() { return joins; }
    public void setJoins(List<JoinInfo> joins) { this.joins = joins; }
    public List<FieldInfo> getFields() { return fields; }
    public void setFields(List<FieldInfo> fields) { this.fields = fields; }
    public List<ExpressionInfo> getExpressions() { return expressions; }
    public void setExpressions(List<ExpressionInfo> expressions) { this.expressions = expressions; }
    public List<SqlNodeTree> getChildren() { return children; }
    public void setChildren(List<SqlNodeTree> children) { this.children = children; }
    public String getJoinCondition() { return joinCondition; }
    public void setJoinCondition(String joinCondition) { this.joinCondition = joinCondition; }
    public String getWhereCondition() { return whereCondition; }
    public void setWhereCondition(String whereCondition) { this.whereCondition = whereCondition; }
    public List<String> getGroupBy() { return groupBy; }
    public void setGroupBy(List<String> groupBy) { this.groupBy = groupBy; }
    public List<String> getOrderBy() { return orderBy; }
    public void setOrderBy(List<String> orderBy) { this.orderBy = orderBy; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
