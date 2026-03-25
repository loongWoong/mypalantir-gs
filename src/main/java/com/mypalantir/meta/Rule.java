package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Rule {
    @JsonProperty("name")
    private String name;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("language")
    private String language;

    @JsonProperty("expr")
    private String expr;

    /**
     * 规则在业务树中的层级深度（0=顶层，数字越大越深）。
     * null 表示汇总/传播规则（R8/R9/R10），不参与层级抑制。
     */
    @JsonProperty("level")
    private Integer level;

    /**
     * 父规则名称（null 表示顶层规则）。
     */
    @JsonProperty("parent")
    private String parent;

    /**
     * 当本规则触发时，被抑制的规则名称列表。
     * 被抑制规则的 has_abnormal_reason 输出降级为 has_abnormal_context。
     */
    @JsonProperty("suppresses")
    private List<String> suppresses;

    /**
     * 输出类型：
     * "root_cause" - 叶节点，触发时输出到 has_abnormal_root_cause
     * "context"    - 父节点，触发时输出到 has_abnormal_context（若未被子规则抑制则也输出到 has_abnormal_reason）
     * null         - 汇总/传播规则，不参与层级分类
     */
    @JsonProperty("output_type")
    private String outputType;

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
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @JsonIgnore
    public String getExpr() {
        return expr;
    }

    public void setExpr(String expr) {
        this.expr = expr;
    }

    @JsonIgnore
    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    @JsonIgnore
    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    @JsonIgnore
    public List<String> getSuppresses() {
        return suppresses;
    }

    public void setSuppresses(List<String> suppresses) {
        this.suppresses = suppresses;
    }

    @JsonIgnore
    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    /**
     * 是否为叶节点规则（root_cause）。
     */
    @JsonIgnore
    public boolean isRootCause() {
        return "root_cause".equals(outputType);
    }

    /**
     * 是否为上下文规则（context，即父节点）。
     */
    @JsonIgnore
    public boolean isContext() {
        return "context".equals(outputType);
    }

    /**
     * 是否参与层级抑制机制（level 不为 null）。
     */
    @JsonIgnore
    public boolean isHierarchical() {
        return level != null;
    }
}
