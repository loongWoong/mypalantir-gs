package com.mypalantir.query;

import java.util.List;
import java.util.Map;

/**
 * Ontology 查询 DSL 的 Java 表示
 * 类似 GraphQL 的查询结构，支持表达式系统和聚合查询
 */
public class OntologyQuery {
    /**
     * 查询的根对象类型（旧字段，向后兼容）
     */
    private String from;
    
    /**
     * 查询的根对象类型（新字段，推荐使用）
     */
    private String object;
    
    /**
     * 查询条件（where 子句，旧格式，向后兼容）
     */
    private Map<String, Object> where;
    
    /**
     * 查询条件（filter 表达式数组，新格式）
     * 例如：[["=", "province", "江苏"], ["between", "hasTollRecords.charge_time", "2024-01-01", "2024-01-31"]]
     */
    private List<Object> filter;
    
    /**
     * 要选择的字段列表
     */
    private List<String> select;
    
    /**
     * 关联查询（通过 link types）
     */
    private List<LinkQuery> links;
    
    /**
     * 分组字段列表
     * 例如：["name"] 或 ["hasTollRecords.charge_time"]
     */
    private List<String> groupBy;
    
    /**
     * 聚合指标列表
     * 例如：[["sum", "hasTollRecords.amount", "total_fee"]]
     */
    private List<Object> metrics;
    
    /**
     * 限制返回数量
     */
    private Integer limit;
    
    /**
     * 偏移量
     */
    private Integer offset;
    
    /**
     * 排序
     */
    private List<OrderBy> orderBy;

    /**
     * 获取查询的根对象类型（支持 from 和 object 两种方式）
     */
    public String getFrom() {
        return object != null ? object : from;
    }

    public void setFrom(String from) {
        this.from = from;
    }
    
    public String getObject() {
        return object != null ? object : from;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Map<String, Object> getWhere() {
        return where;
    }

    public void setWhere(Map<String, Object> where) {
        this.where = where;
    }

    public List<String> getSelect() {
        return select;
    }

    public void setSelect(List<String> select) {
        this.select = select;
    }

    public List<LinkQuery> getLinks() {
        return links;
    }

    public void setLinks(List<LinkQuery> links) {
        this.links = links;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public List<OrderBy> getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(List<OrderBy> orderBy) {
        this.orderBy = orderBy;
    }

    public List<Object> getFilter() {
        return filter;
    }

    public void setFilter(List<Object> filter) {
        this.filter = filter;
    }

    public List<String> getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(List<String> groupBy) {
        this.groupBy = groupBy;
    }

    public List<Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<Object> metrics) {
        this.metrics = metrics;
    }

    /**
     * 关联查询
     */
    public static class LinkQuery {
        /**
         * Link type 名称
         */
        private String name;
        
        /**
         * 目标对象类型（新字段）
         */
        private String object;
        
        /**
         * 要选择的字段列表
         */
        private List<String> select;
        
        /**
         * 嵌套的关联查询
         */
        private List<LinkQuery> links;
        
        /**
         * 查询条件（旧格式，向后兼容）
         */
        private Map<String, Object> where;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getSelect() {
            return select;
        }

        public void setSelect(List<String> select) {
            this.select = select;
        }

        public List<LinkQuery> getLinks() {
            return links;
        }

        public void setLinks(List<LinkQuery> links) {
            this.links = links;
        }

        public Map<String, Object> getWhere() {
            return where;
        }

        public void setWhere(Map<String, Object> where) {
            this.where = where;
        }

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }
    }
    
    /**
     * 聚合指标
     */
    public static class Metric {
        /**
         * 聚合函数名称（sum, avg, count, min, max）
         */
        private String function;
        
        /**
         * 要聚合的字段路径（如 "hasTollRecords.amount"）
         */
        private String field;
        
        /**
         * 聚合结果的别名
         */
        private String alias;

        public Metric() {
        }

        public Metric(String function, String field, String alias) {
            this.function = function;
            this.field = field;
            this.alias = alias;
        }

        public String getFunction() {
            return function;
        }

        public void setFunction(String function) {
            this.function = function;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }
    }

    /**
     * 排序
     */
    public static class OrderBy {
        private String field;
        private String direction; // ASC or DESC

        public OrderBy() {
        }

        public OrderBy(String field, String direction) {
            this.field = field;
            this.direction = direction;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }
    }
}

