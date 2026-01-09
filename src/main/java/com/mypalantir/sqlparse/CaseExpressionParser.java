package com.mypalantir.sqlparse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CASE表达式解析器
 * 职责：专门处理SQL中的CASE WHEN复杂逻辑，转换为系统可识别的过滤条件
 */
@Component
public class CaseExpressionParser {
    
    private static final Logger logger = LoggerFactory.getLogger(CaseExpressionParser.class);
    
    /**
     * CASE结构
     */
    public static class CaseStructure {
        private List<WhenThenPair> whenThenPairs = new ArrayList<>();
        private String elseValue;
        private String targetField; // CASE表达式作用的字段
        
        public static class WhenThenPair {
            private String condition;   // WHEN条件
            private String value;       // THEN值
            
            public WhenThenPair(String condition, String value) {
                this.condition = condition;
                this.value = value;
            }
            
            public String getCondition() { return condition; }
            public void setCondition(String condition) { this.condition = condition; }
            public String getValue() { return value; }
            public void setValue(String value) { this.value = value; }
        }
        
        public List<WhenThenPair> getWhenThenPairs() { return whenThenPairs; }
        public void setWhenThenPairs(List<WhenThenPair> whenThenPairs) { this.whenThenPairs = whenThenPairs; }
        public String getElseValue() { return elseValue; }
        public void setElseValue(String elseValue) { this.elseValue = elseValue; }
        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
        
        public void addWhenThen(String condition, String value) {
            whenThenPairs.add(new WhenThenPair(condition, value));
        }
    }
    
    /**
     * 解析CASE WHEN结构
     */
    public CaseStructure parseCaseExpression(String caseExpr) {
        logger.info("[parseCaseExpression] 开始解析CASE表达式");
        
        if (caseExpr == null || caseExpr.trim().isEmpty()) {
            logger.warn("[parseCaseExpression] CASE表达式为空");
            return null;
        }
        
        CaseStructure structure = new CaseStructure();
        
        // 去除外层的CASE和END
        String cleanedExpr = caseExpr.trim();
        cleanedExpr = cleanedExpr.replaceAll("(?i)^CASE\\s+", "");
        cleanedExpr = cleanedExpr.replaceAll("(?i)\\s+END$", "");
        
        logger.info("[parseCaseExpression] 清理后的表达式: {}", cleanedExpr.substring(0, Math.min(100, cleanedExpr.length())));
        
        // 提取WHEN...THEN对
        Pattern whenThenPattern = Pattern.compile(
            "WHEN\\s+(.+?)\\s+THEN\\s+(.+?)(?=\\s+WHEN|\\s+ELSE|$)", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = whenThenPattern.matcher(cleanedExpr);
        
        while (matcher.find()) {
            String condition = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            
            structure.addWhenThen(condition, value);
            logger.info("[parseCaseExpression] 提取WHEN-THEN: condition={}, value={}", condition, value);
        }
        
        // 提取ELSE
        Pattern elsePattern = Pattern.compile("ELSE\\s+(.+?)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher elseMatcher = elsePattern.matcher(cleanedExpr);
        if (elseMatcher.find()) {
            String elseValue = elseMatcher.group(1).trim();
            structure.setElseValue(elseValue);
            logger.info("[parseCaseExpression] 提取ELSE: value={}", elseValue);
        }
        
        // 尝试推断目标字段
        structure.setTargetField(inferTargetField(structure));
        logger.info("[parseCaseExpression] 推断目标字段: {}", structure.getTargetField());
        
        logger.info("[parseCaseExpression] 解析完成: WHEN-THEN对数={}, ELSE={}", 
            structure.getWhenThenPairs().size(), structure.getElseValue());
        
        return structure;
    }
    
    /**
     * 从CaseInfo解析CASE结构
     */
    public CaseStructure parseCaseExpression(ComplexSqlStructureAnalyzer.CaseInfo caseInfo) {
        logger.info("[parseCaseExpression] 从CaseInfo解析CASE结构");
        
        if (caseInfo == null) {
            logger.warn("[parseCaseExpression] CaseInfo为空");
            return null;
        }
        
        CaseStructure structure = new CaseStructure();
        
        // 组装WHEN-THEN对
        for (int i = 0; i < caseInfo.getWhenConditions().size() && i < caseInfo.getThenValues().size(); i++) {
            String condition = caseInfo.getWhenConditions().get(i);
            String value = caseInfo.getThenValues().get(i);
            structure.addWhenThen(condition, value);
        }
        
        structure.setElseValue(caseInfo.getElseValue());
        structure.setTargetField(inferTargetField(structure));
        
        logger.info("[parseCaseExpression] 解析完成: WHEN-THEN对数={}", structure.getWhenThenPairs().size());
        
        return structure;
    }
    
    /**
     * 将CASE结构转换为过滤条件
     * 策略：提取WHEN条件中的字段和值，生成filterConditions
     */
    public Map<String, Object> convertToFilterConditions(CaseStructure structure) {
        logger.info("[convertToFilterConditions] 开始转换为过滤条件");
        
        Map<String, Object> filterConditions = new HashMap<>();
        
        if (structure == null || structure.getWhenThenPairs().isEmpty()) {
            logger.warn("[convertToFilterConditions] CASE结构为空或无WHEN条件");
            return filterConditions;
        }
        
        // 分析每个WHEN条件
        for (CaseStructure.WhenThenPair pair : structure.getWhenThenPairs()) {
            String condition = pair.getCondition();
            
            // 解析不同类型的条件
            Map<String, Object> parsedCondition = parseCondition(condition);
            filterConditions.putAll(parsedCondition);
        }
        
        logger.info("[convertToFilterConditions] 转换完成: 过滤条件数={}", filterConditions.size());
        return filterConditions;
    }
    
    /**
     * 从CaseInfo直接转换为过滤条件
     */
    public Map<String, Object> convertToFilterConditions(ComplexSqlStructureAnalyzer.CaseInfo caseInfo) {
        CaseStructure structure = parseCaseExpression(caseInfo);
        return convertToFilterConditions(structure);
    }
    
    /**
     * 解析单个条件表达式
     * 支持类型：
     * 1. field = value
     * 2. field IN (value1, value2, ...)
     * 3. field > value / field < value
     * 4. LENGTH(field) = value
     * 5. field LIKE 'pattern'
     */
    private Map<String, Object> parseCondition(String condition) {
        Map<String, Object> parsed = new HashMap<>();
        
        if (condition == null || condition.trim().isEmpty()) {
            return parsed;
        }
        
        String trimmed = condition.trim();
        
        // 类型1: field = value
        Pattern equalPattern = Pattern.compile("([\\w_.]+)\\s*=\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher equalMatcher = equalPattern.matcher(trimmed);
        if (equalMatcher.matches()) {
            String field = cleanFieldName(equalMatcher.group(1));
            String value = cleanValue(equalMatcher.group(2));
            parsed.put(field, value);
            logger.info("[parseCondition] 解析等式: {}={}", field, value);
            return parsed;
        }
        
        // 类型2: field IN (value1, value2, ...)
        Pattern inPattern = Pattern.compile("([\\w_.]+)\\s+IN\\s*\\((.+?)\\)", Pattern.CASE_INSENSITIVE);
        Matcher inMatcher = inPattern.matcher(trimmed);
        if (inMatcher.matches()) {
            String field = cleanFieldName(inMatcher.group(1));
            String valuesStr = inMatcher.group(2);
            List<String> values = parseInValues(valuesStr);
            parsed.put(field, values);
            logger.info("[parseCondition] 解析IN条件: {} IN {}", field, values);
            return parsed;
        }
        
        // 类型3: field > value / field < value
        Pattern comparePattern = Pattern.compile("([\\w_.]+)\\s*([<>]=?)\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher compareMatcher = comparePattern.matcher(trimmed);
        if (compareMatcher.matches()) {
            String field = cleanFieldName(compareMatcher.group(1));
            String operator = compareMatcher.group(2);
            String value = cleanValue(compareMatcher.group(3));
            parsed.put(field + "_" + operator, value);
            logger.info("[parseCondition] 解析比较: {}{}{}", field, operator, value);
            return parsed;
        }
        
        // 类型4: LENGTH(field) = value
        Pattern lengthPattern = Pattern.compile("LENGTH\\s*\\(([\\w_.]+)\\)\\s*=\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher lengthMatcher = lengthPattern.matcher(trimmed);
        if (lengthMatcher.matches()) {
            String field = cleanFieldName(lengthMatcher.group(1));
            String value = cleanValue(lengthMatcher.group(2));
            parsed.put(field + "_length", value);
            logger.info("[parseCondition] 解析LENGTH: LENGTH({})={}", field, value);
            return parsed;
        }
        
        // 类型5: field LIKE 'pattern'
        Pattern likePattern = Pattern.compile("([\\w_.]+)\\s+LIKE\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher likeMatcher = likePattern.matcher(trimmed);
        if (likeMatcher.matches()) {
            String field = cleanFieldName(likeMatcher.group(1));
            String pattern = cleanValue(likeMatcher.group(2));
            parsed.put(field + "_like", pattern);
            logger.info("[parseCondition] 解析LIKE: {} LIKE {}", field, pattern);
            return parsed;
        }
        
        logger.warn("[parseCondition] 无法解析条件: {}", trimmed);
        return parsed;
    }
    
    /**
     * 解析IN子句的值列表
     */
    private List<String> parseInValues(String valuesStr) {
        List<String> values = new ArrayList<>();
        
        if (valuesStr == null || valuesStr.trim().isEmpty()) {
            return values;
        }
        
        // 按逗号分割
        String[] parts = valuesStr.split(",");
        for (String part : parts) {
            String cleanValue = cleanValue(part.trim());
            if (!cleanValue.isEmpty()) {
                values.add(cleanValue);
            }
        }
        
        return values;
    }
    
    /**
     * 清理字段名（去除表前缀、反引号等）
     */
    private String cleanFieldName(String field) {
        if (field == null) {
            return "";
        }
        
        String cleaned = field.trim();
        
        // 去除表前缀 A.field → field
        if (cleaned.contains(".")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf('.') + 1);
        }
        
        // 去除反引号
        cleaned = cleaned.replaceAll("`", "");
        
        return cleaned.trim();
    }
    
    /**
     * 清理值（去除引号、括号等）
     */
    private String cleanValue(String value) {
        if (value == null) {
            return "";
        }
        
        String cleaned = value.trim();
        
        // 去除单引号和双引号
        cleaned = cleaned.replaceAll("^['\"]", "").replaceAll("['\"]$", "");
        
        return cleaned.trim();
    }
    
    /**
     * 推断CASE表达式作用的目标字段
     * 策略：
     * 1. 如果所有WHEN条件都针对同一字段，返回该字段
     * 2. 如果ELSE值是字段名，返回该字段
     * 3. 否则返回null
     */
    private String inferTargetField(CaseStructure structure) {
        if (structure == null || structure.getWhenThenPairs().isEmpty()) {
            return null;
        }
        
        Set<String> fields = new HashSet<>();
        
        // 从WHEN条件中提取字段
        for (CaseStructure.WhenThenPair pair : structure.getWhenThenPairs()) {
            String condition = pair.getCondition();
            
            // 尝试匹配字段名
            Pattern fieldPattern = Pattern.compile("([\\w_.]+)\\s*[=<>]", Pattern.CASE_INSENSITIVE);
            Matcher matcher = fieldPattern.matcher(condition);
            if (matcher.find()) {
                String field = cleanFieldName(matcher.group(1));
                fields.add(field);
            }
        }
        
        // 如果所有条件都针对同一字段
        if (fields.size() == 1) {
            return fields.iterator().next();
        }
        
        // 检查ELSE值是否为字段名
        String elseValue = structure.getElseValue();
        if (elseValue != null && !elseValue.matches("'.*'|\".*\"|\\d+")) {
            return cleanFieldName(elseValue);
        }
        
        return null;
    }
    
    /**
     * 判断CASE表达式是否为简单映射（纯值转换，无复杂条件）
     */
    public boolean isSimpleMapping(CaseStructure structure) {
        if (structure == null || structure.getWhenThenPairs().isEmpty()) {
            return false;
        }
        
        // 检查所有WHEN条件是否都是简单等式
        for (CaseStructure.WhenThenPair pair : structure.getWhenThenPairs()) {
            String condition = pair.getCondition();
            if (!condition.matches(".*=.*") || condition.contains("IN") || condition.contains("LIKE")) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 提取CASE表达式的业务含义
     * 用于生成指标描述
     */
    public String extractBusinessMeaning(CaseStructure structure) {
        if (structure == null || structure.getWhenThenPairs().isEmpty()) {
            return "条件分支";
        }
        
        StringBuilder meaning = new StringBuilder();
        
        String targetField = structure.getTargetField();
        if (targetField != null) {
            meaning.append("根据 ").append(targetField).append(" 的不同值");
        } else {
            meaning.append("根据条件");
        }
        
        meaning.append("，分为 ").append(structure.getWhenThenPairs().size()).append(" 种情况");
        
        if (structure.getElseValue() != null) {
            meaning.append("，其他情况为 ").append(structure.getElseValue());
        }
        
        return meaning.toString();
    }
}
