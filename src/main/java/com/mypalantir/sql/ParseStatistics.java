package com.mypalantir.sql;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ParseStatistics {
    @JsonProperty("totalLevels")
    private int totalLevels;

    @JsonProperty("totalTables")
    private int totalTables;

    @JsonProperty("totalJoins")
    private int totalJoins;

    @JsonProperty("totalSubqueries")
    private int totalSubqueries;

    @JsonProperty("totalFields")
    private int totalFields;

    public ParseStatistics() {}

    public int getTotalLevels() { return totalLevels; }
    public void setTotalLevels(int totalLevels) { this.totalLevels = totalLevels; }
    public int getTotalTables() { return totalTables; }
    public void setTotalTables(int totalTables) { this.totalTables = totalTables; }
    public int getTotalJoins() { return totalJoins; }
    public void setTotalJoins(int totalJoins) { this.totalJoins = totalJoins; }
    public int getTotalSubqueries() { return totalSubqueries; }
    public void setTotalSubqueries(int totalSubqueries) { this.totalSubqueries = totalSubqueries; }
    public int getTotalFields() { return totalFields; }
    public void setTotalFields(int totalFields) { this.totalFields = totalFields; }
}
