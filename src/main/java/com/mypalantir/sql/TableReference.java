package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class TableReference {
    @JsonProperty("name")
    private String name;

    @JsonProperty("alias")
    private String alias;

    @JsonProperty("schema")
    private String schema;

    @JsonProperty("subquery")
    private String subquery;

    @JsonProperty("joinType")
    private String joinType;

    @JsonProperty("joinedWith")
    private String joinedWith;

    public TableReference() {}

    public TableReference(String name, String alias) {
        this.name = name;
        this.alias = alias;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getSubquery() { return subquery; }
    public void setSubquery(String subquery) { this.subquery = subquery; }
    public String getJoinType() { return joinType; }
    public void setJoinType(String joinType) { this.joinType = joinType; }
    public String getJoinedWith() { return joinedWith; }
    public void setJoinedWith(String joinedWith) { this.joinedWith = joinedWith; }
}
