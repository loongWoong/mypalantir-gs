package com.mypalantir.sqlparse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 复杂SQL结构分析器
 * 职责：识别report.sql类型的多层报表SQL特征，拆分子查询层级
 */
@Component
public class ComplexSqlStructureAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(ComplexSqlStructureAnalyzer.class);
    
    /**
     * SQL结构类型枚举
     */
    public enum SqlStructureType {
        SIMPLE_QUERY,           // 简单查询
        SINGLE_AGGREGATION,     // 单层聚合
        MULTI_LAYER_REPORT,     // 多层报表（3层+）
        COMPLEX_JOIN,           // 复杂关联
        UNKNOWN
    }
    
    /**
     * SQL层级结构
     */
    public static class SqlLayer {
        private int depth;                      // 层级深度（0=最外层）
        private String layerSql;                // 该层的SQL片段
        private List<String> selectFields;      // SELECT字段列表
        private List<AggregationInfo> aggregations; // 聚合函数
        private List<CaseInfo> caseExpressions; // CASE表达式
        private List<String> fromTables;        // FROM表列表
        private List<String> groupByFields;     // GROUP BY字段
        private String whereClause;             // WHERE条件
        private boolean hasSubQuery;            // 是否包含子查询
        
        public SqlLayer(int depth) {
            this.depth = depth;
            this.selectFields = new ArrayList<>();
            this.aggregations = new ArrayList<>();
            this.caseExpressions = new ArrayList<>();
            this.fromTables = new ArrayList<>();
            this.groupByFields = new ArrayList<>();
        }
        
        // Getters and Setters
        public int getDepth() { return depth; }
        public void setDepth(int depth) { this.depth = depth; }
        public String getLayerSql() { return layerSql; }
        public void setLayerSql(String layerSql) { this.layerSql = layerSql; }
        public List<String> getSelectFields() { return selectFields; }
        public void setSelectFields(List<String> selectFields) { this.selectFields = selectFields; }
        public List<AggregationInfo> getAggregations() { return aggregations; }
        public void setAggregations(List<AggregationInfo> aggregations) { this.aggregations = aggregations; }
        public List<CaseInfo> getCaseExpressions() { return caseExpressions; }
        public void setCaseExpressions(List<CaseInfo> caseExpressions) { this.caseExpressions = caseExpressions; }
        public List<String> getFromTables() { return fromTables; }
        public void setFromTables(List<String> fromTables) { this.fromTables = fromTables; }
        public List<String> getGroupByFields() { return groupByFields; }
        public void setGroupByFields(List<String> groupByFields) { this.groupByFields = groupByFields; }
        public String getWhereClause() { return whereClause; }
        public void setWhereClause(String whereClause) { this.whereClause = whereClause; }
        public boolean isHasSubQuery() { return hasSubQuery; }
        public void setHasSubQuery(boolean hasSubQuery) { this.hasSubQuery = hasSubQuery; }
    }
    
    /**
     * 聚合函数信息
     */
    public static class AggregationInfo {
        private String function;        // SUM/COUNT/AVG/MAX/MIN
        private String field;           // 聚合字段
        private String expression;      // 完整表达式
        private String alias;           // 别名
        
        public AggregationInfo(String function, String field, String expression, String alias) {
            this.function = function;
            this.field = field;
            this.expression = expression;
            this.alias = alias;
        }
        
        public String getFunction() { return function; }
        public String getField() { return field; }
        public String getExpression() { return expression; }
        public String getAlias() { return alias; }
    }
    
    /**
     * CASE表达式信息
     */
    public static class CaseInfo {
        private String fullExpression;          // 完整CASE表达式
        private List<String> whenConditions;    // WHEN条件列表
        private List<String> thenValues;        // THEN值列表
        private String elseValue;               // ELSE默认值
        private String alias;                   // 别名
        
        public CaseInfo() {
            this.whenConditions = new ArrayList<>();
            this.thenValues = new ArrayList<>();
        }
        
        public String getFullExpression() { return fullExpression; }
        public void setFullExpression(String fullExpression) { this.fullExpression = fullExpression; }
        public List<String> getWhenConditions() { return whenConditions; }
        public void setWhenConditions(List<String> whenConditions) { this.whenConditions = whenConditions; }
        public List<String> getThenValues() { return thenValues; }
        public void setThenValues(List<String> thenValues) { this.thenValues = thenValues; }
        public String getElseValue() { return elseValue; }
        public void setElseValue(String elseValue) { this.elseValue = elseValue; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
    }
    
    /**
     * 分析SQL结构类型
     */
    public SqlStructureType analyzeStructure(String sql) {
        logger.info("[analyzeStructure] 开始分析SQL结构");
        
        int subQueryDepth = calculateSubQueryDepth(sql);
        int caseCount = countCaseStatements(sql);
        int aggregationCount = countAggregations(sql);
        int joinCount = countJoins(sql);
        
        logger.info("[analyzeStructure] 统计: 子查询深度={}, CASE数量={}, 聚合函数数量={}, JOIN数量={}", 
            subQueryDepth, caseCount, aggregationCount, joinCount);
        
        // 判定逻辑
        if (subQueryDepth >= 2 && (caseCount > 5 || aggregationCount > 10)) {
            logger.info("[analyzeStructure] 识别为多层报表SQL");
            return SqlStructureType.MULTI_LAYER_REPORT;
        } else if (joinCount >= 2 && aggregationCount > 5) {
            logger.info("[analyzeStructure] 识别为复杂关联查询");
            return SqlStructureType.COMPLEX_JOIN;
        } else if (aggregationCount > 0) {
            logger.info("[analyzeStructure] 识别为单层聚合查询");
            return SqlStructureType.SINGLE_AGGREGATION;
        } else if (subQueryDepth == 0 && aggregationCount == 0) {
            logger.info("[analyzeStructure] 识别为简单查询");
            return SqlStructureType.SIMPLE_QUERY;
        }
        
        logger.info("[analyzeStructure] 无法明确识别，标记为未知类型");
        return SqlStructureType.UNKNOWN;
    }
    
    /**
     * 提取SQL层级结构（从外到内）
     */
    public List<SqlLayer> extractLayers(String sql) {
        logger.info("[extractLayers] 开始提取SQL层级");
        
        List<SqlLayer> layers = new ArrayList<>();
        extractLayersRecursive(sql, 0, layers);
        
        logger.info("[extractLayers] 共提取 {} 层", layers.size());
        return layers;
    }
    
    /**
     * 递归提取层级
     */
    private void extractLayersRecursive(String sql, int depth, List<SqlLayer> layers) {
        if (sql == null || sql.trim().isEmpty()) {
            return;
        }
        
        logger.info("[extractLayersRecursive] 处理第 {} 层", depth);
        
        SqlLayer layer = new SqlLayer(depth);
        layer.setLayerSql(sql);
        
        // 解析SELECT字段
        layer.setSelectFields(extractSelectFields(sql));
        logger.info("[extractLayersRecursive] 第{}层SELECT字段数: {}", depth, layer.getSelectFields().size());
        
        // 解析聚合函数
        layer.setAggregations(extractAggregations(sql));
        logger.info("[extractLayersRecursive] 第{}层聚合函数数: {}", depth, layer.getAggregations().size());
        
        // 解析CASE表达式
        layer.setCaseExpressions(extractCaseExpressions(sql));
        logger.info("[extractLayersRecursive] 第{}层CASE表达式数: {}", depth, layer.getCaseExpressions().size());
        
        // 解析FROM表
        layer.setFromTables(extractFromTables(sql));
        logger.info("[extractLayersRecursive] 第{}层FROM表数: {}", depth, layer.getFromTables().size());
        
        // 解析GROUP BY
        layer.setGroupByFields(extractGroupByFields(sql));
        logger.info("[extractLayersRecursive] 第{}层GROUP BY字段数: {}", depth, layer.getGroupByFields().size());
        
        // 解析WHERE
        layer.setWhereClause(extractWhereClause(sql));
        
        layers.add(layer);
        
        // 递归处理子查询
        List<String> subQueries = extractSubQueries(sql);
        layer.setHasSubQuery(!subQueries.isEmpty());
        
        if (!subQueries.isEmpty()) {
            logger.info("[extractLayersRecursive] 第{}层包含 {} 个子查询", depth, subQueries.size());
            for (String subQuery : subQueries) {
                extractLayersRecursive(subQuery, depth + 1, layers);
            }
        }
    }
    
    /**
     * 计算子查询深度
     */
    private int calculateSubQueryDepth(String sql) {
        int maxDepth = 0;
        int currentDepth = 0;
        
        // 简化算法：统计嵌套括号深度
        for (char c : sql.toCharArray()) {
            if (c == '(') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == ')') {
                currentDepth--;
            }
        }
        
        // 子查询深度 = 括号深度 - 1（普通函数也会用括号）
        return Math.max(0, maxDepth / 2);
    }
    
    /**
     * 统计CASE语句数量
     */
    private int countCaseStatements(String sql) {
        Pattern pattern = Pattern.compile("\\bCASE\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
    
    /**
     * 统计聚合函数数量
     */
    private int countAggregations(String sql) {
        String[] aggFunctions = {"SUM", "COUNT", "AVG", "MAX", "MIN"};
        int count = 0;
        for (String func : aggFunctions) {
            Pattern pattern = Pattern.compile("\\b" + func + "\\s*\\(", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(sql);
            while (matcher.find()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 统计JOIN数量
     */
    private int countJoins(String sql) {
        Pattern pattern = Pattern.compile("\\b(LEFT|RIGHT|INNER|FULL|CROSS)?\\s*JOIN\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
    
    /**
     * 提取SELECT字段
     */
    private List<String> extractSelectFields(String sql) {
        List<String> fields = new ArrayList<>();
        
        // 匹配SELECT...FROM之间的内容
        Pattern pattern = Pattern.compile("SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        
        if (matcher.find()) {
            String selectClause = matcher.group(1);
            // 简单分割（实际应处理嵌套括号）
            String[] parts = selectClause.split(",(?![^()]*\\))");
            for (String part : parts) {
                fields.add(part.trim());
            }
        }
        
        return fields;
    }
    
    /**
     * 提取聚合函数
     */
    private List<AggregationInfo> extractAggregations(String sql) {
        List<AggregationInfo> aggregations = new ArrayList<>();
        String[] aggFunctions = {"SUM", "COUNT", "AVG", "MAX", "MIN"};
        
        for (String func : aggFunctions) {
            // 匹配: SUM(field) AS alias 或 SUM(field)
            Pattern pattern = Pattern.compile(
                "\\b(" + func + ")\\s*\\(([^)]+)\\)(?:\\s+AS\\s+([\\w_]+))?", 
                Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(sql);
            
            while (matcher.find()) {
                String function = matcher.group(1).toUpperCase();
                String field = matcher.group(2).trim();
                String expression = matcher.group(0);
                String alias = matcher.group(3);
                
                aggregations.add(new AggregationInfo(function, field, expression, alias));
            }
        }
        
        return aggregations;
    }
    
    /**
     * 提取CASE表达式
     */
    private List<CaseInfo> extractCaseExpressions(String sql) {
        List<CaseInfo> caseExpressions = new ArrayList<>();
        
        // 匹配CASE...END块
        Pattern pattern = Pattern.compile(
            "CASE\\b(.+?)\\bEND(?:\\s+AS\\s+([\\w_]+))?", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(sql);
        
        while (matcher.find()) {
            CaseInfo caseInfo = new CaseInfo();
            caseInfo.setFullExpression(matcher.group(0));
            caseInfo.setAlias(matcher.group(2));
            
            String caseBody = matcher.group(1);
            
            // 提取WHEN...THEN对
            Pattern whenThenPattern = Pattern.compile(
                "WHEN\\s+(.+?)\\s+THEN\\s+(.+?)(?=\\s+WHEN|\\s+ELSE|$)", 
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            Matcher whenThenMatcher = whenThenPattern.matcher(caseBody);
            
            while (whenThenMatcher.find()) {
                caseInfo.getWhenConditions().add(whenThenMatcher.group(1).trim());
                caseInfo.getThenValues().add(whenThenMatcher.group(2).trim());
            }
            
            // 提取ELSE
            Pattern elsePattern = Pattern.compile("ELSE\\s+(.+?)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher elseMatcher = elsePattern.matcher(caseBody);
            if (elseMatcher.find()) {
                caseInfo.setElseValue(elseMatcher.group(1).trim());
            }
            
            caseExpressions.add(caseInfo);
        }
        
        return caseExpressions;
    }
    
    /**
     * 提取FROM表
     */
    private List<String> extractFromTables(String sql) {
        List<String> tables = new ArrayList<>();
        
        // 匹配FROM...WHERE/GROUP/ORDER之间
        Pattern pattern = Pattern.compile(
            "FROM\\s+(.+?)(?=\\s+WHERE|\\s+GROUP|\\s+ORDER|\\s+LIMIT|$)", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(sql);
        
        if (matcher.find()) {
            String fromClause = matcher.group(1).trim();
            // 提取表名（忽略JOIN条件）
            String[] parts = fromClause.split("(?i)\\s+(LEFT|RIGHT|INNER|FULL|CROSS)?\\s*JOIN\\s+");
            for (String part : parts) {
                // 提取表名或别名
                String tableName = part.replaceAll("(?i)\\s+ON\\s+.+", "").trim();
                if (!tableName.isEmpty()) {
                    tables.add(tableName);
                }
            }
        }
        
        return tables;
    }
    
    /**
     * 提取GROUP BY字段
     */
    private List<String> extractGroupByFields(String sql) {
        List<String> fields = new ArrayList<>();
        
        Pattern pattern = Pattern.compile(
            "GROUP\\s+BY\\s+(.+?)(?=\\s+HAVING|\\s+ORDER|\\s+LIMIT|$)", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(sql);
        
        if (matcher.find()) {
            String groupByClause = matcher.group(1).trim();
            String[] parts = groupByClause.split(",");
            for (String part : parts) {
                fields.add(part.trim());
            }
        }
        
        return fields;
    }
    
    /**
     * 提取WHERE子句
     */
    private String extractWhereClause(String sql) {
        Pattern pattern = Pattern.compile(
            "WHERE\\s+(.+?)(?=\\s+GROUP|\\s+ORDER|\\s+LIMIT|$)", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(sql);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
    
    /**
     * 提取子查询（FROM子句中的子查询）
     */
    private List<String> extractSubQueries(String sql) {
        List<String> subQueries = new ArrayList<>();
        
        // 预处理：去除INSERT INTO部分，只保留SELECT部分
        String cleanedSql = sql;
        if (sql.toUpperCase().trim().startsWith("INSERT")) {
            // 匹配 INSERT INTO ... SELECT ...
            Pattern insertPattern = Pattern.compile(
                "INSERT\\s+INTO.+?\\)\\s*(SELECT\\b.+)", 
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            Matcher insertMatcher = insertPattern.matcher(sql);
            if (insertMatcher.find()) {
                cleanedSql = insertMatcher.group(1);
                logger.info("[extractSubQueries] 检测到INSERT语句，提取SELECT部分");
            }
        }
        
        // 策略1: 查找FROM子句中的括号包裹的SELECT（使用括号平衡算法）
        subQueries.addAll(extractSubQueriesWithBalancedParentheses(cleanedSql, "FROM"));
        
        // 策略2: 查找LEFT JOIN中的子查询
        subQueries.addAll(extractSubQueriesWithBalancedParentheses(cleanedSql, "JOIN"));
        
        logger.info("[extractSubQueries] 共提取 {} 个子查询", subQueries.size());
        return subQueries;
    }
    
    /**
     * 使用括号平衡算法提取子查询
     */
    private List<String> extractSubQueriesWithBalancedParentheses(String sql, String keyword) {
        List<String> subQueries = new ArrayList<>();
        
        // 查找所有keyword的位置
        Pattern keywordPattern = Pattern.compile(
            "\\b" + keyword + "\\s+\\(", 
            Pattern.CASE_INSENSITIVE
        );
        Matcher keywordMatcher = keywordPattern.matcher(sql);
        
        while (keywordMatcher.find()) {
            int startPos = keywordMatcher.end() - 1; // 指向左括号
            int depth = 0;
            int endPos = -1;
            
            // 括号平衡算法
            for (int i = startPos; i < sql.length(); i++) {
                char c = sql.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        endPos = i;
                        break;
                    }
                }
            }
            
            if (endPos > startPos) {
                String subQuery = sql.substring(startPos + 1, endPos).trim();
                // 只提取SELECT开头的子查询
                if (subQuery.toUpperCase().startsWith("SELECT")) {
                    subQueries.add(subQuery);
                    logger.info("[extractSubQueriesWithBalancedParentheses] 提取到子查询: 长度={}", subQuery.length());
                }
            }
        }
        
        return subQueries;
    }
}
