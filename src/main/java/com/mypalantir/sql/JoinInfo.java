package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class JoinInfo {
    @JsonProperty("joinType")
    private String joinType;

    @JsonProperty("leftTable")
    private String leftTable;

    @JsonProperty("rightTable")
    private String rightTable;

    @JsonProperty("condition")
    private String condition;

    @JsonProperty("columns")
    private List<String> columns;

    public JoinInfo() {
        this.columns = new ArrayList<>();
    }

    public JoinInfo(String joinType, String leftTable, String rightTable, String condition) {
        this();
        this.joinType = joinType;
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.condition = condition;
    }

    public String getJoinType() { return joinType; }
    public void setJoinType(String joinType) { this.joinType = joinType; }
    public String getLeftTable() { return leftTable; }
    public void setLeftTable(String leftTable) { this.leftTable = leftTable; }
    public String getRightTable() { return rightTable; }
    public void setRightTable(String rightTable) { this.rightTable = rightTable; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
}
