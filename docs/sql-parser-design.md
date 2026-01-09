# SQL 解析与血缘分析功能设计文档

## 一、需求概述

### 1.1 目标
解析用户粘贴的复杂物理SQL查询，以图形化方式展示SQL的逻辑结构、层级关系和字段血缘。

### 1.2 输入输出

**输入**：
- 纯SQL查询语句（包含多层子查询、函数、JOIN等）
- 或基于查询结果的INSERT语句
- **可执行的查询SQL**（将CLEARDATE占位符替换为实际值）
- **现金汇总查询**等复杂业务SQL

**输出**：
- SQL层级结构树形展示
- 每个层级的对象、字段、函数、表达式
- 最终宽表字段的完整血缘路径
- 分析结果JSON（供后续指标定义使用）

### 1.3 应用场景
- 复杂SQL语句的结构化解析
- 字段血缘追踪
- 指标口径溯源
- 数据质量分析

---

## 二、技术方案

### 2.1 方案选择：后端 Calcite + 前端图形化

| 组件 | 技术选型 | 理由 |
|------|----------|------|
| SQL解析器 | **Apache Calcite** | 项目已依赖(v1.37.0)，支持复杂SQL，RelNode可还原SQL结构 |
| 血缘分析 | **自定义遍历算法** | 基于Calcite RelNode构建依赖图 |
| 前端展示 | **React** | 与现有前端架构一致，使用内联样式实现快速原型 |
| 通信协议 | REST API | 与现有架构一致 |

### 2.2 架构流程

```
用户SQL → Calcite解析 → RelNode树 → 层级结构分析 → 血缘追踪 → 前端可视化
                                                    ↓
                                              分析结果JSON
```

### 2.3 复杂SQL处理能力

本解析器需要支持以下复杂场景：

#### 2.3.1 多层嵌套子查询
```sql
FROM (
    SELECT ... FROM (
        SELECT ... FROM TBL_EXCLEARRESULTCASH
        WHERE ...
    ) SUB1
    GROUP BY ...
) A
LEFT JOIN (...) B ON ...
```

#### 2.3.2 相关子查询
```sql
SELECT CONCAT(T2.BL_ROAD, '0000')
FROM TBL_GBSECTIONDIC T1, T_ORGCODE T2
WHERE T2.ORGCODE = CONCAT(T1.ROADID, '00')
  AND T2.LASTVER = (SELECT MAX(LASTVER) FROM T_ORGCODE WHERE VERUSETIME < CONCAT(CLEARDATE, ' 23:59:59') AND ORGTYPE = 40)
```

#### 2.3.3 复杂CASE表达式
```sql
CASE WHEN LENGTH(TOLLSECTIONID) = 6 THEN SUBSTR(TOLLSECTIONID, 1, 2)
     ELSE (SELECT ... FROM ...) END
```

#### 2.3.4 多重JOIN和LEFT JOIN
```sql
FROM (...) A LEFT JOIN (...) B
  ON A.SPLITORG = B.SPLITORG AND A.PAYCARDTYPE = B.PAYCARDTYPE
```

---

## 三、后端设计

### 3.1 模块结构

```
src/main/java/com/mypalantir/sql/
├── SqlParseController.java      # API入口
├── SqlParserService.java        # 解析服务（主逻辑）
├── SqlParseResult.java          # 解析结果封装
├── SqlNodeTree.java             # SQL层级结构模型
├── FieldLineage.java            # 字段血缘模型
├── TableReference.java          # 表引用模型
├── FieldInfo.java               # 字段信息模型
├── ExpressionInfo.java          # 表达式信息模型
├── ParseStatistics.java         # 解析统计信息
├── SubqueryContext.java         # 子查询上下文（新增）
└── NestedExpressionParser.java  # 嵌套表达式解析器（新增）
```

### 3.2 API 设计

#### 3.2.1 SQL解析接口

**POST** `/api/v1/sql/parse`

请求体：
```json
{
  "sql": "SELECT ...",
  "options": {
    "replacePlaceholders": true,
    "placeholderValues": {
      "CLEARDATE": "20250108"
    }
  }
}
```

响应体：
```json
{
  "success": true,
  "originalSql": "SELECT ...",
  "replacedSql": "SELECT ... WHERE CLEARDATE = '20250108'",
  "tree": {
    "id": "uuid",
    "type": "ROOT",
    "level": 0,
    "tables": [{"name": "table_a", "alias": "a"}, {"name": "table_b", "alias": "b"}],
    "fields": [{"name": "id", "isAggregated": false}, {"name": "amount_sum", "isAggregated": true}],
    "children": [...]
  },
  "lineage": [
    {
      "outputField": "amount_sum",
      "outputTable": "RESULT",
      "expression": "SUM(b.amount)",
      "function": "SUM",
      "path": [{"from": "b.amount", "to": "SUM(b.amount)", "operation": "SUM"}]
    }
  ],
  "statistics": {
    "totalLevels": 3,
    "totalTables": 2,
    "totalJoins": 1,
    "totalSubqueries": 1,
    "totalFields": 5
  }
}
```

### 3.3 核心解析逻辑

#### 3.3.1 层级结构构建（更新）

```java
private SqlNodeTree buildSelectTree(SqlSelect select, int level, Set<String> visited) {
    // 1. 处理常量占位符替换（如果需要）
    if (options.isReplacePlaceholders()) {
        select = replacePlaceholders(select, options.getPlaceholderValues());
    }
    
    // 2. 解析FROM子句中的表（支持子查询）
    List<TableReference> tables = extractTablesFromFrom(select.getFrom(), level);
    
    // 3. 解析JOIN信息
    List<JoinInfo> joins = extractJoinInfo(select.getFrom(), level);
    
    // 4. 解析SELECT字段（支持复杂表达式）
    List<FieldInfo> fields = extractFieldsFromSelect(select.getSelectList(), level);
    
    // 5. 提取表达式（聚合函数、CASE WHEN、IFNULL等）
    List<ExpressionInfo> expressions = extractExpressionsFromSelect(select.getSelectList());
    
    // 6. 递归处理子查询（支持多层嵌套）
    List<SqlNodeTree> children = extractSubqueries(select, level + 1, visited);
    
    // 7. 处理WHERE中的相关子查询
    List<SqlNodeTree> whereSubqueries = extractCorrelatedSubqueries(select.getWhere(), level + 1, visited);
    children.addAll(whereSubqueries);
    
    // 8. 构建层级节点
    SqlNodeTree node = new SqlNodeTree("SELECT", level);
    node.setTables(tables);
    node.setJoins(joins);
    node.setFields(fields);
    node.setExpressions(expressions);
    node.setChildren(children);
    
    // 9. 解析GROUP BY
    if (select.getGroup() != null) {
        node.setGroupBy(extractGroupByFields(select.getGroup()));
    }
    
    return node;
}
```

#### 3.3.2 表提取逻辑（更新，支持子查询）

```java
private List<TableReference> extractTablesFromFrom(SqlNode fromNode, int level) {
    List<TableReference> tables = new ArrayList<>();
    
    if (fromNode == null) {
        return tables;
    }
    
    // 递归遍历 FROM 节点树，提取表和子查询
    extractTablesRecursive(fromNode, tables, level, new HashSet<>());
    
    return deduplicateTables(tables);
}

private void extractTablesRecursive(SqlNode node, List<TableReference> tables, int level, Set<String> seenTables) {
    if (node == null) return;
    
    if (node instanceof SqlIdentifier) {
        // 简单表名
        String tableName = node.toString().replace("`", "").replace("\"", "");
        if (isValidTableName(tableName) && !seenTables.contains(tableName.toLowerCase())) {
            seenTables.add(tableName.toLowerCase());
            tables.add(new TableReference(tableName, null));
        }
    } else if (node instanceof SqlSelect) {
        // 子查询：SELECT (...) AS alias
        SqlSelect subSelect = (SqlSelect) node;
        TableReference subqueryRef = new TableReference("SUBQUERY_" + level, "SUBQUERY");
        subqueryRef.setSubquery(subSelect.toString());
        subqueryRef.setJoinType("FROM_SUBQUERY");
        tables.add(subqueryRef);
    } else if (node instanceof SqlBasicCall) {
        SqlBasicCall call = (SqlBasicCall) node;
        String operator = call.getOperator().getKind().toString();
        
        if ("JOIN".equals(operator) || "INNER".equals(operator) ||
            "LEFT".equals(operator) || "RIGHT".equals(operator) ||
            "FULL".equals(operator) || "CROSS".equals(operator)) {
            // JOIN 结构
            for (int i = 0; i < call.operandCount() - 1; i++) {
                SqlNode operand = call.operand(i);
                if (operand != null) {
                    extractTablesRecursive(operand, tables, level, seenTables);
                }
            }
            // 提取JOIN类型
            TableReference lastTable = tables.isEmpty() ? null : tables.get(tables.size() - 1);
            if (lastTable != null) {
                lastTable.setJoinType(operator);
            }
        } else if ("AS".equals(operator)) {
            // 别名结构: table AS alias 或 (SELECT ...) AS alias
            if (call.operandCount() >= 1) {
                SqlNode leftOperand = call.operand(0);
                if (leftOperand instanceof SqlSelect) {
                    // 子查询别名
                    TableReference subqueryRef = new TableReference("SUBQUERY_" + level, safeToString(call.operand(1)));
                    subqueryRef.setSubquery(leftOperand.toString());
                    tables.add(subqueryRef);
                } else {
                    extractTablesRecursive(leftOperand, tables, level, seenTables);
                    // 设置最后一个表的别名
                    if (!tables.isEmpty()) {
                        TableReference lastTable = tables.get(tables.size() - 1);
                        lastTable.setAlias(safeToString(call.operand(1)));
                    }
                }
            }
        } else {
            // 其他操作符，递归处理所有操作数
            for (SqlNode operand : call.getOperandList()) {
                extractTablesRecursive(operand, tables, level, seenTables);
            }
        }
    }
}
```

#### 3.3.3 JOIN信息提取（新增）

```java
private List<JoinInfo> extractJoinInfo(SqlNode fromNode, int level) {
    List<JoinInfo> joins = new ArrayList<>();
    extractJoinRecursive(fromNode, joins, level);
    return joins;
}

private void extractJoinRecursive(SqlNode node, List<JoinInfo> joins, int level) {
    if (node == null) return;
    
    if (node instanceof SqlBasicCall) {
        SqlBasicCall call = (SqlBasicCall) node;
        String operator = call.getOperator().getKind().toString();
        
        if ("LEFT".equals(operator) || "RIGHT".equals(operator) || 
            "INNER".equals(operator) || "JOIN".equals(operator)) {
            JoinInfo join = new JoinInfo();
            join.setJoinType(operator);
            
            // 提取JOIN条件（通常是最后一个操作数）
            if (call.operandCount() > 0) {
                SqlNode lastOperand = call.operand(call.operandCount() - 1);
                if (lastOperand instanceof SqlBasicCall) {
                    SqlBasicCall onCall = (SqlBasicCall) lastOperand;
                    if (onCall.getOperator().getKind() == SqlKind.ON) {
                        join.setCondition(safeToString(onCall.operand(1)));
                    }
                }
            }
            
            // 提取左右表
            if (call.operandCount() >= 2) {
                join.setLeftTable(extractTableNameFromNode(call.operand(0)));
                join.setRightTable(extractTableNameFromNode(call.operand(1)));
            }
            
            joins.add(join);
        } else {
            // 递归处理
            for (SqlNode operand : call.getOperandList()) {
                extractJoinRecursive(operand, joins, level);
            }
        }
    }
}
```

#### 3.3.4 字段提取逻辑（更新，支持复杂表达式）

```java
private List<FieldInfo> extractFieldsFromSelect(SqlNodeList selectList, int level) {
    List<FieldInfo> fields = new ArrayList<>();
    
    if (selectList == null) {
        return fields;
    }
    
    for (SqlNode node : selectList) {
        FieldInfo field = parseFieldNode(node);
        if (field != null) {
            fields.add(field);
        }
    }
    
    return fields;
}

private FieldInfo parseFieldNode(SqlNode node) {
    if (node == null) return null;
    
    FieldInfo field = new FieldInfo();
    String expr = safeToString(node);
    
    field.setAggregated(isAggregateFunction(node));
    
    if (node instanceof SqlBasicCall) {
        SqlBasicCall call = (SqlBasicCall) node;
        if (call.getOperator().getKind() == SqlKind.AS) {
            // 有别名: expr AS alias
            if (call.operandCount() >= 2) {
                SqlNode left = call.operand(0);
                SqlNode right = call.operand(1);
                String alias = safeToString(right);
                field.setName(alias);
                field.setAlias(alias);
                field.setExpression(safeToString(left));
                field.setAggregated(isAggregateFunction(left));
            }
        } else if (call.getOperator() instanceof SqlCaseOperator) {
            // CASE WHEN ... END
            field.setName(expr);
            field.setExpression(expr);
            field.setAggregated(true);
        } else if (call.getOperator() instanceof SqlFunction) {
            // 函数调用
            SqlFunction func = (SqlFunction) call.getOperator();
            field.setName(func.getName());
            field.setExpression(expr);
            field.setAggregated(isAggregateFunction(node));
        } else {
            field.setName(expr);
            field.setExpression(expr);
        }
    } else if (node instanceof SqlIdentifier) {
        field.setName(expr);
        field.setExpression(expr);
    } else if (node instanceof SqlLiteral) {
        field.setName("CONSTANT");
        field.setExpression(expr);
    }
    
    return field;
}
```

#### 3.3.5 嵌套表达式解析（新增）

```java
/**
 * 解析复杂嵌套表达式，提取所有嵌套的字段和子查询
 */
private ExpressionInfo parseNestedExpression(SqlNode node) {
    ExpressionInfo exprInfo = new ExpressionInfo();
    
    if (node == null) return exprInfo;
    
    // 递归解析
    parseExpressionRecursive(node, exprInfo);
    
    return exprInfo;
}

private void parseExpressionRecursive(SqlNode node, ExpressionInfo parentInfo) {
    if (node == null) return;
    
    if (node instanceof SqlCase) {
        // CASE WHEN ... THEN ... ELSE ... END
        SqlCase caseNode = (SqlCase) node;
        ExpressionInfo caseExpr = new ExpressionInfo();
        caseExpr.setType("CASE_WHEN");
        caseExpr.setExpression(safeToString(node));
        caseExpr.setFunction("CASE");
        
        // 解析WHEN条件
        for (SqlNode whenNode : caseNode.getWhenOperands()) {
            parseExpressionRecursive(whenNode, caseExpr);
        }
        
        // 解析THEN值
        for (SqlNode thenNode : caseNode.getThenOperands()) {
            parseExpressionRecursive(thenNode, caseExpr);
        }
        
        // 解析ELSE值
        if (caseNode.getElseOperand() != null) {
            parseExpressionRecursive(caseNode.getElseOperand(), caseExpr);
        }
        
        parentInfo.getNestedExpressions().add(caseExpr);
    } else if (node instanceof SqlBasicCall) {
        SqlBasicCall call = (SqlBasicCall) node;
        
        if (call.getOperator() instanceof SqlFunction) {
            // 函数调用
            ExpressionInfo funcExpr = new ExpressionInfo();
            funcExpr.setType("FUNCTION");
            funcExpr.setFunction(call.getOperator().getName());
            funcExpr.setExpression(safeToString(node));
            
            // 递归解析函数参数
            for (SqlNode operand : call.getOperandList()) {
                parseExpressionRecursive(operand, funcExpr);
            }
            
            parentInfo.getNestedExpressions().add(funcExpr);
        } else if (call.getOperator().getKind() == SqlKind.AS) {
            // 别名，跳过
            parseExpressionRecursive(call.operand(0), parentInfo);
        } else {
            // 其他操作符
            for (SqlNode operand : call.getOperandList()) {
                parseExpressionRecursive(operand, parentInfo);
            }
        }
    } else if (node instanceof SqlSelect) {
        // 子查询
        ExpressionInfo subqueryExpr = new ExpressionInfo();
        subqueryExpr.setType("SUBQUERY");
        subqueryExpr.setExpression(safeToString(node));
        parentInfo.getNestedExpressions().add(subqueryExpr);
    } else if (node instanceof SqlIdentifier) {
        // 字段引用
        String fieldName = safeToString(node);
        if (isValidFieldName(fieldName)) {
            parentInfo.getSourceFields().add(fieldName);
        }
    }
}
```

#### 3.3.6 子查询提取逻辑（更新，支持多层嵌套）

```java
private List<SqlNodeTree> extractSubqueries(SqlSelect select, int level, Set<String> visited) {
    List<SqlNodeTree> subqueries = new ArrayList<>();
    
    if (level > MAX_RECURSION_DEPTH) {
        return subqueries;
    }
    
    // 1. 从FROM子句提取子查询
    if (select.getFrom() != null) {
        scanForSubqueriesInFrom(select.getFrom(), level, subqueries, visited);
    }
    
    // 2. 从WHERE子句提取子查询
    if (select.getWhere() != null) {
        scanForSubqueriesInWhere(select.getWhere(), level, subqueries, visited);
    }
    
    return subqueries;
}

private void scanForSubqueriesInFrom(SqlNode node, int level, List<SqlNodeTree> subqueries, Set<String> visited) {
    if (node == null) return;
    
    if (node instanceof SqlSelect) {
        // 发现子查询
        SqlSelect subSelect = (SqlSelect) node;
        SqlNodeTree subTree = buildSelectTree(subSelect, level, visited);
        subTree.setType("SUBQUERY");
        subTree.setAlias("SUB" + subqueries.size());
        subTree.setDescription("FROM子句子查询 #" + subqueries.size());
        subqueries.add(subTree);
    } else if (node instanceof SqlBasicCall) {
        SqlBasicCall call = (SqlBasicCall) node;
        String operator = call.getOperator().getKind().toString();
        
        if ("JOIN".equals(operator) || "INNER".equals(operator) ||
            "LEFT".equals(operator) || "RIGHT".equals(operator)) {
            // JOIN 结构 - 跳过 ON 条件，处理其他操作数
            for (int i = 0; i < call.operandCount() - 1; i++) {
                scanForSubqueriesInFrom(call.operand(i), level, subqueries, visited);
            }
        } else if ("AS".equals(operator)) {
            // 别名结构
            if (call.operandCount() >= 1) {
                SqlNode leftOperand = call.operand(0);
                if (leftOperand instanceof SqlSelect) {
                    // (SELECT ...) AS alias
                    SqlSelect subSelect = (SqlSelect) leftOperand;
                    SqlNodeTree subTree = buildSelectTree(subSelect, level, visited);
                    subTree.setType("SUBQUERY");
                    subTree.setAlias(safeToString(call.operand(1)));
                    subTree.setDescription("FROM子句子查询 #" + subqueries.size());
                    subqueries.add(subTree);
                } else {
                    scanForSubqueriesInFrom(leftOperand, level, subqueries, visited);
                }
            }
        } else {
            // 其他操作符 - 递归处理所有操作数
            for (SqlNode operand : call.getOperandList()) {
                scanForSubqueriesInFrom(operand, level, subqueries, visited);
            }
        }
    }
}

private void scanForSubqueriesInWhere(SqlNode node, int level, List<SqlNodeTree> subqueries, Set<String> visited) {
    if (node == null) return;
    
    if (node instanceof SqlSelect) {
        // WHERE中的子查询
        SqlSelect subSelect = (SqlSelect) node;
        SqlNodeTree subTree = buildSelectTree(subSelect, level, visited);
        subTree.setType("WHERE_SUBQUERY");
        subTree.setAlias("WHERE_SUB" + subqueries.size());
        subTree.setDescription("WHERE子句子查询 #" + subqueries.size());
        subqueries.add(subTree);
    } else if (node instanceof SqlBasicCall) {
        SqlBasicCall call = (SqlBasicCall) node;
        
        // 递归遍历所有操作数
        for (SqlNode operand : call.getOperandList()) {
            scanForSubqueriesInWhere(operand, level, subqueries, visited);
        }
    }
}
```

#### 3.3.7 相关子查询提取（新增）

```java
private List<SqlNodeTree> extractCorrelatedSubqueries(SqlNode whereNode, int level, Set<String> visited) {
    List<SqlNodeTree> subqueries = new ArrayList<>();
    
    if (whereNode == null || level > MAX_RECURSION_DEPTH) {
        return subqueries;
    }
    
    findCorrelatedSubqueries(whereNode, level, subqueries, visited);
    
    return subqueries;
}

private void findCorrelatedSubqueries(SqlNode node, int level, List<SqlNodeTree> subqueries, Set<String> visited) {
    if (node == null) return;
    
    if (node instanceof SqlSelect) {
        SqlSelect subSelect = (SqlSelect) node;
        
        // 检查是否包含外部引用
        if (containsExternalReference(subSelect, getCurrentQueryScope())) {
            SqlNodeTree subTree = buildSelectTree(subSelect, level, visited);
            subTree.setType("CORRELATED_SUBQUERY");
            subTree.setAlias("CORR_SUB" + subqueries.size());
            subTree.setDescription("相关子查询 #" + subqueries.size());
            subqueries.add(subTree);
        }
    } else if (node instanceof SqlBasicCall) {
        SqlBasicCall call = (SqlBasicCall) node;
        for (SqlNode operand : call.getOperandList()) {
            findCorrelatedSubqueries(operand, level, subqueries, visited);
        }
    }
}

private boolean containsExternalReference(SqlSelect subSelect, Set<String> externalFields) {
    // 检查子查询是否引用了外部查询的字段
    String subSql = subSelect.toString().toUpperCase();
    for (String field : externalFields) {
        if (subSql.contains(field.toUpperCase())) {
            return true;
        }
    }
    return false;
}
```

#### 3.3.8 常量占位符替换（新增）

```java
private SqlSelect replacePlaceholders(SqlSelect select, Map<String, String> placeholders) {
    if (placeholders == null || placeholders.isEmpty()) {
        return select;
    }
    
    String sql = select.toString();
    String replacedSql = sql;
    
    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
        // 替换 CLEARDATE = 'XXX' 格式的条件
        replacedSql = replacedSql.replaceAll(
            "(?i)\\b" + entry.getKey() + "\\s*=\\s*'[^']*'",
            entry.getKey() + " = '" + entry.getValue() + "'"
        );
    }
    
    try {
        SqlParser parser = SqlParser.create(replacedSql, parserConfig);
        return (SqlSelect) parser.parseQuery();
    } catch (SqlParseException e) {
        log.warn("占位符替换后SQL解析失败，使用原SQL", e);
        return select;
    }
}
```

#### 3.3.9 血缘分析逻辑（更新，支持嵌套追踪）

```java
private List<FieldLineage> analyzeLineage(SqlNodeTree tree) {
    List<FieldLineage> lineage = new ArrayList<>();
    Map<String, SqlNodeTree> fieldSources = new HashMap<>();
    analyzeLineageRecursive(tree, lineage, fieldSources, 0);
    return lineage;
}

private void analyzeLineageRecursive(SqlNodeTree node, List<FieldLineage> lineage, 
                                      Map<String, SqlNodeTree> fieldSources, int depth) {
    // 1. 注册当前层的字段来源
    if (node.getFields() != null) {
        for (FieldInfo field : node.getFields()) {
            String key = node.getLevel() + "_" + field.getName();
            fieldSources.put(key, node);
        }
    }
    
    // 2. 分析当前层的字段血缘
    if (node.getFields() != null) {
        for (FieldInfo field : node.getFields()) {
            FieldLineage fieldLineage = analyzeFieldLineage(field, node, fieldSources);
            lineage.add(fieldLineage);
        }
    }
    
    // 3. 递归处理子查询
    if (node.getChildren() != null) {
        for (SqlNodeTree child : node.getChildren()) {
            analyzeLineageRecursive(child, lineage, fieldSources, depth + 1);
        }
    }
}

private FieldLineage analyzeFieldLineage(FieldInfo field, SqlNodeTree currentNode, 
                                          Map<String, SqlNodeTree> fieldSources) {
    FieldLineage fieldLineage = new FieldLineage();
    fieldLineage.setOutputField(field.getName());
    fieldLineage.setOutputTable("Level_" + currentNode.getLevel());
    fieldLineage.setExpression(field.getExpression());
    
    List<FieldLineage.LineageStep> steps = new ArrayList<>();
    String sourceExpr = field.getExpression() != null ? field.getExpression() : field.getName();
    steps.add(new FieldLineage.LineageStep(sourceExpr, field.getName(), "SELECT"));
    
    // 解析字段来源
    Set<String> sourceFields = extractSourceFields(field.getExpression());
    for (String sourceField : sourceFields) {
        // 查找字段来源
        FieldLineage.SourceField srcField = findSourceField(sourceField, fieldSources);
        if (srcField != null) {
            fieldLineage.addSourceField(srcField.getTable(), srcField.getField(), null);
            
            // 添加血缘步骤
            String operation = detectFunctionType(field.getExpression());
            steps.add(new FieldLineage.LineageStep(
                srcField.getTable() + "." + srcField.getField(),
                field.getExpression(),
                operation
            ));
        } else {
            // 字段来自物理表
            steps.add(new FieldLineage.LineageStep(
                sourceField,
                field.getExpression(),
                "DIRECT"
            ));
        }
    }
    
    fieldLineage.setPath(steps);
    return fieldLineage;
}
```

### 3.4 安全性设计

| 机制 | 配置值 | 说明 |
|------|--------|------|
| 最大递归深度 | `MAX_RECURSION_DEPTH = 20` | 防止无限递归 |
| 最大子查询数 | `MAX_SUBQUERY_COUNT = 100` | 防止过多子查询 |
| 表达式截断 | `100字符` | 防止过长表达式导致内存溢出 |
| 字符串安全转换 | `safeToString()` | 处理复杂节点的字符串转换 |
| SQL长度限制 | `MAX_SQL_LENGTH = 50000` | 防止过大的SQL |

### 3.5 Join信息模型（新增）

```java
public class JoinInfo {
    private String joinType;      // LEFT, RIGHT, INNER, CROSS
    private String leftTable;     // 左表名或别名
    private String rightTable;    // 右表名或别名
    private String condition;     // JOIN条件
    private List<String> columns; // JOIN涉及的字段列表
    
    public JoinInfo() {}
    
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
```

---

## 四、前端设计

### 4.1 界面布局

```
+----------------------------------------------------------------------+
|  SQL 解析与血缘分析                                                    |
+----------------------------------------------------------------------+
|  [SQL输入区域                                    ] [解析] [清空]      |
+----------------------------------------------------------------------+
|  [统计卡片: 层级 表  JOIN 子查询 字段]                                  |
+----------------------------------------------------------------------+
|                         |                                              |
|   层级结构               |       血缘详情 - Level 0                     |
|   ├─ L0 ROOT           |       Level 0 - ROOT                         |
|   │  [数据源表: ...]    |       数据源: TBL_EXCLEARRESULTCASH          |
|   │  [输出字段: ...]    |                                              |
|   │                    |       字段血缘 (5/37)                        |
|   ├─ L1 SELECT         |       ◉ CASHSPLITMONEY                      |
|   │  [数据源表: ...]    |       IFNULL(A.CASHSPLITMONEY, 0)           |
|   │  [输出字段: ...]    |       → SUM                                 |
|   │                    |       → b.amount                            |
|   └─ L2 SUBQUERY       |                                              |
|      [数据源表: ...]    |       表达式                                 |
|      [输出字段: ...]    |       SUM(amount)                           |
+----------------------------------------------------------------------+
```

### 4.2 组件结构

```
SqlParsePage.tsx
├── StatCard          # 统计信息卡片
├── LevelCard         # 层级卡片（左侧列表）
│   ├── 表信息展示
│   ├── 字段信息展示
│   └── GROUP BY 展示
└── LineagePanel      # 血缘详情面板（右侧）
    ├── 层级信息头
    ├── 字段血缘列表
    └── 表达式列表
```

### 4.3 交互设计

| 交互 | 行为 |
|------|------|
| 点击层级卡片 | 选中该层级，右侧显示对应血缘详情 |
| 展开/折叠 | 点击层级卡片左侧箭头 |
| 全部展开 | 展开所有层级 |
| 折叠 | 只显示第一级 |

---

## 五、数据模型（更新）

### 5.1 核心类定义（更新）

```java
// SQL层级树（更新）
public class SqlNodeTree {
    private String id;
    private String type;        // ROOT, SELECT, SUBQUERY, WHERE_SUBQUERY, CORRELATED_SUBQUERY
    private int level;
    private String sql;
    private String alias;
    private String description;
    private List<TableReference> tables;
    private List<JoinInfo> joins;           // 新增：JOIN信息
    private List<FieldInfo> fields;
    private List<ExpressionInfo> expressions;
    private List<SqlNodeTree> children;
    private List<String> groupBy;
    private List<String> orderBy;
    private String whereCondition;          // WHERE条件
    private Map<String, Object> metadata;   // 元数据
    
    public SqlNodeTree() {
        this.id = UUID.randomUUID().toString();
        this.tables = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.expressions = new ArrayList<>();
        this.children = new ArrayList<>();
        this.joins = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
}

// 字段血缘（更新）
public class FieldLineage {
    private String outputField;
    private String outputTable;
    private String expression;
    private String function;     // SUM, AVG, COUNT, CASE_WHEN, IFNULL等
    private List<SourceField> sourceFields;
    private List<LineageStep> path;
    private List<String> transformations;  // 新增：转换步骤
    private String subqueryAlias;          // 新增：子查询别名
    
    public FieldLineage() {
        this.sourceFields = new ArrayList<>();
        this.path = new ArrayList<>();
        this.transformations = new ArrayList<>();
    }
}

// 表引用（更新）
public class TableReference {
    private String name;
    private String alias;
    private String schema;
    private String subquery;      // 新增：子查询SQL
    private String joinType;      // LEFT, RIGHT, INNER, CROSS
    private String joinedWith;    // 关联的表
    private boolean isSubquery;   // 新增：是否子查询
    
    public TableReference() {
        this.isSubquery = false;
    }
    
    public boolean isSubquery() { return isSubquery; }
    public void setSubquery(String subquery) { 
        this.subquery = subquery; 
        this.isSubquery = true;
    }
}

// 字段信息（更新）
public class FieldInfo {
    private String name;
    private String alias;
    private String table;
    private String dataType;
    private boolean isAggregated;
    private String expression;
    private List<String> sourceFields;   // 新增：源字段列表
    private boolean isCaseWhen;          // 新增：是否CASE表达式
    private boolean isIfNull;            // 新增：是否IFNULL表达式
    
    public FieldInfo() {
        this.sourceFields = new ArrayList<>();
    }
}

// 表达式信息（更新）
public class ExpressionInfo {
    private String type;        // AGGREGATE, CASE_WHEN, FUNCTION, SUBQUERY, LOGICAL
    private String expression;
    private String function;
    private String condition;
    private List<ExpressionInfo> nestedExpressions;  // 新增：嵌套表达式
    private List<String> sourceFields;               // 新增：源字段
    private int depth;                               // 新增：嵌套深度
    
    public ExpressionInfo() {
        this.nestedExpressions = new ArrayList<>();
        this.sourceFields = new ArrayList<>();
    }
}
```

### 5.2 TypeScript类型定义（更新）

```typescript
export interface SqlNodeTree {
  id: string;
  type: 'ROOT' | 'SELECT' | 'SUBQUERY' | 'WHERE_SUBQUERY' | 'CORRELATED_SUBQUERY';
  level: number;
  sql: string;
  alias?: string;
  description?: string;
  tables: TableReference[];
  joins: JoinInfo[];           // 新增
  fields: FieldInfo[];
  expressions: ExpressionInfo[];
  children: SqlNodeTree[];
  groupBy?: string[];
  orderBy?: string[];
  whereCondition?: string;     // 新增
}

export interface JoinInfo {    // 新增
  joinType: 'LEFT' | 'RIGHT' | 'INNER' | 'CROSS';
  leftTable: string;
  rightTable: string;
  condition: string;
  columns: string[];
}

export interface FieldLineage {
  outputField: string;
  outputTable: string;
  expression?: string;
  function?: string;
  sourceFields: SourceField[];  // 更新
  path: LineageStep[];
  transformations: string[];    // 新增
  subqueryAlias?: string;       // 新增
}

export interface ExpressionInfo {  // 更新
  type: 'AGGREGATE' | 'CASE_WHEN' | 'FUNCTION' | 'SUBQUERY' | 'LOGICAL';
  expression: string;
  function?: string;
  condition?: string;
  nestedExpressions: ExpressionInfo[];  // 新增
  sourceFields: string[];               // 新增
  depth: number;                        // 新增
}

export interface LineageStep {
  from: string;
  to: string;
  operation: string;
}
```

---

## 六、示例SQL解析（更新）

### 6.1 输入SQL（现金汇总查询）

```sql
SELECT IFNULL(A.CLEARDATE, B.CLEARDATE) AS CLEARDATE,
       IFNULL(A.CASHSPLITMONEY, 0) AS CASHSPLITMONEY,
       SUM(CASE WHEN PAYTYPE = 1 THEN AMOUNT END) AS CASHTOLLMONEY
FROM (
    SELECT CLEARDATE, PAYCARDTYPE,
           SUM(CASHSPLITMONEY) CASHSPLITMONEY
    FROM TBL_EXCLEARRESULTCASH
    WHERE CLEARDATE = '20250108'
    GROUP BY TOLLSECTIONID, PAYCARDTYPE
) A
LEFT JOIN (
    SELECT CLEARDATE, PAYCARDTYPE,
           SUM(CASHTOLLMONEY) CASHTOLLMONEY
    FROM TBL_EXCLEARRESULTCASH
    WHERE CLEARDATE = '20250108'
    GROUP BY SECTIONID, PAYCARDTYPE
) B ON A.CLEARDATE = B.CLEARDATE;
```

### 6.2 解析结果

**层级结构**：
```
Level 0 - ROOT
├── 数据源表: SUBQUERY_A (别名A), SUBQUERY_B (别名B)
├── 输出字段: CLEARDATE, CASHSPLITMONEY, CASHTOLLMONEY
├── JOIN: A LEFT JOIN B ON A.CLEARDATE = B.CLEARDATE
│
├── Level 1 - SUBQUERY (A)
│   ├── 数据源表: TBL_EXCLEARRESULTCASH
│   ├── 输出字段: CLEARDATE, PAYCARDTYPE, CASHSPLITMONEY
│   ├── GROUP BY: TOLLSECTIONID, PAYCARDTYPE
│   └── WHERE: CLEARDATE = '20250108'
│
└── Level 2 - SUBQUERY (B)
    ├── 数据源表: TBL_EXCLEARRESULTCASH
    ├── 输出字段: CLEARDATE, PAYCARDTYPE, CASHTOLLMONEY
    ├── GROUP BY: SECTIONID, PAYCARDTYPE
    └── WHERE: CLEARDATE = '20250108'
```

**字段血缘**：
```
CLEARDATE → IFNULL(A.CLEARDATE, B.CLEARDATE) → A.CLEARDATE, B.CLEARDATE
CASHSPLITMONEY → IFNULL(A.CASHSPLITMONEY, 0) → A.CASHSPLITMONEY
CASHTOLLMONEY → SUM(CASE WHEN PAYTYPE = 1 THEN AMOUNT END) → TBL_EXCLEARRESULTCASH.AMOUNT
```

### 6.3 复杂嵌套SQL解析示例

**输入SQL（多层嵌套）**：
```sql
SELECT
    IFNULL(A.CLEARDATE, B.CLEARDATE) AS CLEARDATE,
    (CASE WHEN A.SPLITORG IS NOT NULL THEN A.SPLITORG ELSE B.SPLITORG END) AS SPLITORG,
    IFNULL(A.CORP, B.CORP) CORP,
    SUM(CASE WHEN LENGTH(TOLLSECTIONID) = 6 THEN SUBSTR(TOLLSECTIONID, 1, 2)
             ELSE (SELECT CONCAT(T2.BL_ROAD, '0000')
                   FROM TBL_GBSECTIONDIC T1, T_ORGCODE T2
                   WHERE T2.ORGCODE = CONCAT(T1.ROADID, '00') AND T2.ORGTYPE = 40
                     AND T1.ID = TOLLSECTIONID
                     AND T2.LASTVER = (SELECT MAX(LASTVER) FROM T_ORGCODE
                                       WHERE VERUSETIME < CONCAT(CLEARDATE, ' 23:59:59')
                                       AND ORGTYPE = 40))
            END) AS ROAD
FROM (...) A
LEFT JOIN (...) B ON ...
```

**解析结果**：

**层级结构**：
```
Level 0 - ROOT
├── 数据源表: SUBQUERY_A, SUBQUERY_B
├── 输出字段: CLEARDATE, SPLITORG, CORP, ROAD
├── JOIN: A LEFT JOIN B ON A.SPLITORG = B.SPLITORG AND A.PAYCARDTYPE = B.PAYCARDTYPE
│
├── Level 1 - SUBQUERY (A)
│   ├── 数据源表: TBL_EXCLEARRESULTCASH
│   ├── 输出字段: CLEARDATE, PAYCARDTYPE, SPLITORG, CORP, CASHSPLITMONEY...
│   └── GROUP BY: SPLITORG, PAYCARDTYPE, CLEARDATE, ROAD, CORP
│
├── Level 2 - SUBQUERY (B)
│   ├── 数据源表: TBL_EXCLEARRESULTCASH
│   ├── 输出字段: CLEARDATE, PAYCARDTYPE, SPLITORG, ROAD, CORP, CASHTOLLMONEY...
│   └── GROUP BY: SPLITORG, PAYCARDTYPE, CLEARDATE, ROAD, CORP
│
├── Level 3 - CORRELATED_SUBQUERY (相关子查询)
│   ├── 位置: SELECT -> CASE -> ELSE -> (SELECT ...)
│   └── 用途: 根据TOLLSECTIONID获取ROAD编码
│
└── Level 4 - CORRELATED_SUBQUERY (相关子查询)
    ├── 位置: WHERE -> (SELECT MAX(LASTVER) ...)
    └── 用途: 获取指定时间点前的最大版本号
```

**字段血缘**：
```
ROAD → CASE WHEN LENGTH(TOLLSECTIONID) = 6 THEN SUBSTR(TOLLSECTIONID, 1, 2)
            ELSE (SELECT CONCAT(T2.BL_ROAD, '0000') FROM ...) END
       → LENGTH(TOLLSECTIONID), SUBSTR(TOLLSECTIONID, 1, 2)
       → T2.BL_ROAD (来自T_ORGCODE表)
       → T1.ROADID (来自TBL_GBSECTIONDIC表)
```

### 6.4 占位符替换示例

**输入SQL（带占位符）**：
```sql
SELECT * FROM TBL_EXCLEARRESULTCASH WHERE CLEARDATE = '${CLEARDATE}'
```

**请求参数**：
```json
{
  "sql": "SELECT * FROM TBL_EXCLEARRESULTCASH WHERE CLEARDATE = '${CLEARDATE}'",
  "options": {
    "replacePlaceholders": true,
    "placeholderValues": {
      "CLEARDATE": "20250108"
    }
  }
}
```

**替换后SQL**：
```sql
SELECT * FROM TBL_EXCLEARRESULTCASH WHERE CLEARDATE = '20250108'
```

---

## 七、扩展功能规划

### 7.1 后续可扩展功能

| 功能 | 优先级 | 说明 |
|------|--------|------|
| CTE支持 | 高 | 支持WITH子句的递归解析 |
| 窗口函数 | 高 | 支持OVER(PARTITION BY)的血缘追踪 |
| INSERT语句 | 中 | 支持INSERT INTO ... SELECT解析 |
| 图形化血缘图 | 中 | 使用D3.js绘制血缘关系图 |
| 指标定义 | 低 | 基于血缘分析结果定义指标口径 |

### 7.2 SQL方言支持

当前支持：MySQL 5.x
后续可扩展：
- PostgreSQL
- Oracle
- SQL Server
- Hive/Spark SQL

---

## 八、已知问题与限制

### 8.1 当前版本限制

1. **相关子查询**：复杂的相关子查询可能无法完整解析
2. **动态SQL**：不支持动态SQL（如EXEC、PREPARE）
3. **存储过程**：不支持存储过程调用
4. **递归深度**：超过20层递归会截断
5. **CTE支持**：当前不支持WITH子句的递归解析
6. **窗口函数**：暂不支持OVER(PARTITION BY)的血缘追踪
7. **INSERT语句**：暂不支持INSERT INTO ... SELECT解析

### 8.2 复杂SQL处理问题（2024-01-09更新）

#### 8.2.1 问题列表

| 问题 | 描述 | 状态 |
|------|------|------|
| 多层嵌套子查询 | FROM后面直接跟多层嵌套的子查询无法正确识别层级 | 待修复 |
| 相关子查询 | SELECT列表或WHERE条件中的相关子查询无法正确解析 | 待修复 |
| 复杂CASE表达式 | 多层嵌套的CASE WHEN表达式中的字段提取不完整 | 待修复 |
| JOIN条件处理 | LEFT JOIN的ON条件解析不完整 | 待修复 |
| 字段来源追踪 | 多层子查询嵌套时，字段血缘追踪丢失 | 待修复 |
| IFNULL嵌套 | IFNULL中嵌套的字段无法正确提取 | 待修复 |
| 子查询别名 | 子查询别名（如`A`, `B`）无法正确关联到子查询 | 待修复 |
| 常量占位符 | SQL中的占位符（如`${CLEARDATE}`）无法替换 | 待修复 |

#### 8.2.2 问题示例

**问题1：多层嵌套子查询**
```sql
-- 当前无法正确解析这种结构
FROM (
    SELECT * FROM (
        SELECT * FROM TBL_EXCLEARRESULTCASH
        WHERE CLEARDATE = '20250108'
    ) SUB1
    GROUP BY ...
) A
```
**现象**：只识别到一层子查询，丢失内部嵌套结构

**问题2：相关子查询**
```sql
-- 当前无法解析SELECT列表中的相关子查询
SELECT (
    SELECT MAX(LASTVER) FROM T_ORGCODE
    WHERE VERUSETIME < CONCAT(CLEARDATE, ' 23:59:59')
    AND ORGTYPE = 40
) AS MAX_VER
FROM TBL_EXCLEARRESULTCASH
```
**现象**：子查询被忽略或解析为普通字段

**问题3：复杂CASE表达式**
```sql
-- 当前无法提取CASE WHEN中的嵌套字段
CASE WHEN LENGTH(TOLLSECTIONID) = 6 THEN SUBSTR(TOLLSECTIONID, 1, 2)
     ELSE (SELECT ... FROM ...) END
```
**现象**：CASE中的字段（如`TOLLSECTIONID`）未被提取为源字段

#### 8.2.3 修复方案

1. **增强子查询识别**：
   - 在`scanForSubqueriesInFrom`方法中添加对`SqlSelect`类型的递归处理
   - 支持识别`(SELECT ...) AS alias`格式的子查询

2. **支持相关子查询**：
   - 新增`extractCorrelatedSubqueries`方法
   - 在SELECT和WHERE子句中扫描子查询

3. **增强表达式解析**：
   - 新增`NestedExpressionParser`类
   - 递归解析CASE WHEN、IFNULL等复杂表达式

4. **完善字段追踪**：
   - 使用`SubqueryContext`维护当前查询的字段列表
   - 在血缘分析时从上下文中查找字段来源

5. **支持占位符替换**：
   - 新增`replacePlaceholders`方法
   - 支持将占位符替换为实际值

---

## 九、版本历史

| 版本 | 日期 | 作者 | 修改内容 |
|------|------|------|----------|
| v1.0 | 2024-01-09 | - | 初始版本，实现基础解析功能 |
| v1.1 | 2024-01-09 | - | 更新设计文档，支持复杂嵌套SQL解析、多层子查询、相关子查询、复杂CASE表达式等 |
