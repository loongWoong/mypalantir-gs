# SQL指标提取功能实现分析

## 1. 概述

SQL指标提取功能是一个复杂的多层解析系统，用于从SQL语句中自动识别和提取业务指标。系统采用多策略路由架构，支持从简单查询到复杂报表SQL的全场景覆盖。

### 核心目标
- 自动识别SQL中的原子指标、派生指标、复合指标
- 建立物理表字段与业务对象属性的映射关系
- 支持多层嵌套查询、JOIN关联、CASE表达式等复杂场景
- 提供血缘追踪和语义增强能力

### 技术栈
- **SQL解析**: Apache Calcite
- **结构分析**: 正则表达式 + 括号平衡算法
- **血缘追踪**: RexNode表达式树遍历
- **语义增强**: LLM集成（可选）

---

## 2. 整体架构

### 2.1 核心组件关系

```plantuml
@startuml
package "入口层" {
  [SqlPasteMetricService]
}

package "解析层" {
  [CalciteSqlParser]
  [ComplexSqlStructureAnalyzer]
  [RexNodeLineageExtractor]
}

package "映射层" {
  [MappingResolver]
  [LLMAlignment]
}

package "提取层" {
  [ReportMetricExtractor]
  [ReportJoinMetricHandler]
  [RexMetricParser]
  [MultiDimensionAggregationHandler]
}

package "整合层" {
  [SqlLayerIntegrator]
  [MetricValidator]
}

[SqlPasteMetricService] --> [CalciteSqlParser]
[SqlPasteMetricService] --> [ComplexSqlStructureAnalyzer]
[SqlPasteMetricService] --> [RexNodeLineageExtractor]
[SqlPasteMetricService] --> [MappingResolver]
[SqlPasteMetricService] --> [LLMAlignment]
[SqlPasteMetricService] --> [ReportMetricExtractor]
[SqlPasteMetricService] --> [ReportJoinMetricHandler]
[SqlPasteMetricService] --> [RexMetricParser]
[SqlPasteMetricService] --> [MetricValidator]

[ReportMetricExtractor] --> [MultiDimensionAggregationHandler]
[ReportMetricExtractor] --> [SqlLayerIntegrator]
@enduml
```

### 2.2 数据流转

```plantuml
@startuml
start
:输入SQL;
:步骤1: Calcite语法解析;
:步骤2: 获取相关对象类型;
:步骤3: 映射关系对齐;
if (启用LLM?) then (是)
  :步骤4: LLM语义增强;
else (否)
  :使用默认语义分析;
endif
:步骤5: 多策略提取指标;
note right
  优先级:
  1. RexNode血缘解析
  2. LEFT JOIN报表解析
  3. 多层报表解析
  4. 标准提取
end note
:步骤6: 验证指标;
:步骤7: 生成建议;
:返回提取结果;
stop
@enduml
```

---

## 3. 核心流程详解

### 3.1 主流程：parseAndExtract

这是系统的核心入口方法，位于 `SqlPasteMetricService.parseAndExtract()`。

```plantuml
@startuml
start
:接收SQL字符串;
:创建SqlPasteParseResult;

partition "步骤1: SQL语法解析" {
  :CalciteSqlParser.parse();
  :提取表、字段、聚合、WHERE、GROUP BY;
}

partition "步骤2: 获取相关对象类型" {
  :getRelevantObjectTypes();
  :过滤有映射配置的对象类型;
}

partition "步骤3: 映射关系对齐" {
  :MappingResolver.alignWithMappings();
  :建立表→对象类型映射;
  :建立字段→属性映射;
}

partition "步骤4: LLM语义增强(可选)" {
  if (enableLLM?) then (是)
    :LLMAlignment.enhanceSemantic();
  else (否)
    :createDefaultSemanticResult();
  endif
}

partition "步骤5: 提取指标(多策略)" {
  if (RexNode解析成功?) then (是)
    :extractMetricsByRex();
    stop
  endif
  
  if (LEFT JOIN结构?) then (是)
    :extractMetricsForJoinReport();
    stop
  endif
  
  if (多层报表SQL?) then (是)
    :extractMetricsForComplexReport();
    stop
  endif
  
  :extractMetrics(标准提取);
}

partition "步骤6: 验证指标" {
  :validateMetrics();
  :检查必填字段;
}

partition "步骤7: 生成建议" {
  :generateSuggestions();
}

:返回SqlPasteParseResult;
stop
@enduml
```

**关键设计**:
- **多策略路由**: 按优先级依次尝试4种提取策略
- **降级机制**: 高级策略失败后自动降级到标准提取
- **可选LLM**: 支持关闭LLM仍能正常工作

---

### 3.2 Calcite SQL解析

`CalciteSqlParser` 基于Apache Calcite进行SQL语法树解析。

```plantuml
@startuml
start
:输入SQL字符串;
:预处理(去注释、分号);
:SqlParser.create().parseStmt();
:获取SqlNode树;

if (SqlNode类型?) then (SqlSelect)
  partition "提取各部分" {
    :extractSelectFields();
    note right
      解析SELECT列表
      识别聚合函数(COUNT/SUM/AVG/MAX/MIN)
      提取别名(AS)
    end note
    
    :extractFromClause();
    note right
      递归提取表和子查询
      处理JOIN关联
    end note
    
    :extractWhereClause();
    note right
      解析WHERE条件
      识别时间字段
    end note
    
    :extractGroupByClause();
    note right
      提取GROUP BY字段
    end note
  }
else (其他)
  :不支持的类型;
endif

:封装CalciteSqlParseResult;
stop
@enduml
```

**核心逻辑**:
1. **聚合识别**: 通过 `SqlKind` 判断聚合函数类型
2. **别名处理**: 区分 `AS` 节点和实际表达式节点
3. **子查询递归**: 遇到子查询时递归调用 `extractFromSqlSelect()`

---

### 3.3 映射关系对齐

`MappingResolver.alignWithMappings()` 建立SQL与业务对象的映射桥梁。

```plantuml
@startuml
start
:接收CalciteSqlParseResult;

:步骤1: 建立表映射;
note right
  表名 → TableReference
  表别名 → TableReference
end note

:步骤2: 加载表的映射配置;
note right
  从mapping表查询
  表名 → DataSourceMapping
  支持大小写不敏感匹配
end note

partition "字段映射处理" {
  :遍历SELECT字段;
  :resolveFieldMapping();
  if (映射置信度?) then (HIGH)
    :加入fieldMappings;
  else (MEDIUM/LOW)
    :加入unmappedFields;
  endif
  
  :遍历WHERE字段;
  :resolveWhereFieldMapping();
  
  :遍历GROUP BY字段;
  :resolveGroupByFieldMapping();
}

:步骤3: 分析JOIN路径;
:analyzeJoinPaths();

:返回MappingAlignmentResult;
stop
@enduml
```

**映射策略**:
```plantuml
@startuml
start
:输入字段名;
:精确匹配;
if (匹配成功?) then (是)
  :返回HIGH置信度;
  stop
endif

:大小写不敏感匹配;
if (匹配成功?) then (是)
  :返回MEDIUM置信度;
  stop
endif

:下划线转换匹配(snake_case);
if (匹配成功?) then (是)
  :返回MEDIUM置信度;
  stop
endif

:驼峰转换匹配(camelCase);
if (匹配成功?) then (是)
  :返回MEDIUM置信度;
  stop
endif

:返回LOW置信度;
stop
@enduml
```

---

### 3.4 多层报表SQL提取

`extractMetricsForComplexReport()` 专门处理复杂的多层嵌套SQL。

#### 3.4.1 SQL结构识别

```plantuml
@startuml
start
:ComplexSqlStructureAnalyzer.analyzeStructure();

:统计SQL特征;
note right
  - 子查询深度(括号平衡算法)
  - CASE语句数量
  - 聚合函数数量
  - JOIN数量
end note

if (子查询深度>=2 AND\n(CASE>5 OR 聚合>10)?) then (是)
  :识别为MULTI_LAYER_REPORT;
elseif (JOIN>=2 AND 聚合>5?) then (是)
  :识别为COMPLEX_JOIN;
elseif (聚合>0?) then (是)
  :识别为SINGLE_AGGREGATION;
else (否)
  :识别为SIMPLE_QUERY;
endif

stop
@enduml
```

#### 3.4.2 层级提取

```plantuml
@startuml
start
:ComplexSqlStructureAnalyzer.extractLayers();

:递归提取SQL层级;
partition "每层提取内容" {
  :SELECT字段列表;
  :聚合函数信息;
  :CASE表达式;
  :FROM表列表;
  :GROUP BY字段;
  :WHERE条件;
}

:使用括号平衡算法提取子查询;
note right
  从FROM和JOIN中查找
  SELECT开头的子查询
end note

:递归处理子查询;
:返回SqlLayer列表;
stop
@enduml
```

**括号平衡算法**:
```java
// 核心逻辑示例
int depth = 0;
for (int i = startPos; i < sql.length(); i++) {
    char c = sql.charAt(i);
    if (c == '(') depth++;
    else if (c == ')') {
        depth--;
        if (depth == 0) {
            // 找到匹配的右括号
            extractSubQuery(startPos, i);
            break;
        }
    }
}
```

#### 3.4.3 分层指标提取

```plantuml
@startuml
start
:ReportMetricExtractor.extractByLayer();

:按深度排序层级(从内到外);

if (最外层包含聚合?) then (是)
  :extractFromOuterLayerDirectly();
  note right
    适用于聚合在最外层的报表
    直接从最外层提取所有指标
  end note
  stop
endif

partition "标准分层提取" {
  :步骤1: 从最内层提取原子指标;
  note right
    extractAtomicMetrics()
    - 从聚合函数参数提取字段
    - 过滤COUNT(*)
    - 设置category=ATOMIC
  end note
  
  :步骤2: 从中间层提取派生指标;
  note right
    extractDerivedMetrics()
    - 聚合函数 + 条件
    - 关联原子指标
    - 提取WHERE作为filterConditions
    - 提取GROUP BY作为dimensions
  end note
  
  :步骤3: 从最外层提取复合指标;
  note right
    extractCompositeMetrics()
    - 识别算术运算表达式
    - 查找依赖的基础指标
    - 设置derivedFormula
  end note
}

:返回LayeredMetrics;
stop
@enduml
```

**指标分类规则**:
- **原子指标**: 最内层聚合字段(如 `SUM(amount)` 中的 `amount`)
- **派生指标**: 中间层的聚合+条件组合(如 `SUM(amount) WHERE status='paid'`)
- **复合指标**: 最外层的运算表达式(如 `metric1 / metric2 * 100`)

---

### 3.5 RexNode血缘解析

`RexMetricParser` 基于Calcite的RexNode进行精确的字段血缘追踪。

```plantuml
@startuml
start
:RexMetricParser.parse(sql);

:Calcite解析SQL;
:获取RelNode关系树;

partition "遍历RelNode" {
  :提取Project节点;
  note right
    对应SELECT列表
  end note
  
  :获取每列的RexNode表达式;
  
  :递归遍历RexNode树;
  note right
    RexInputRef → 输入列引用
    RexCall → 函数调用
    RexLiteral → 常量
  end note
  
  :追踪到底层TableScan;
  :记录完整血缘路径;
}

:构建ColumnLineage;
note right
  包含:
  - sourceTable
  - sourceColumn
  - transformations列表
  - fullLineage路径
end note

:RexMetricParser.extractMetrics();
:根据RexNode类型判断指标类型;
note right
  - 聚合函数 → DERIVED
  - 简单引用 → ATOMIC
  - 算术运算 → COMPOSITE
end note

:返回ExtractedMetric列表;
stop
@enduml
```

**优势**:
- 最精确的血缘追踪
- 支持复杂表达式解析
- 自动识别转换逻辑

---

### 3.6 整合与验证

#### 3.6.1 层级整合

```plantuml
@startuml
start
:SqlLayerIntegrator.integrate();

:接收LayeredMetrics;

partition "原子指标整合" {
  :遍历原子指标;
  :从MappingResolver查找映射;
  :设置businessProcess;
  note right
    通过表名→对象类型→业务过程
  end note
  :设置aggregationFunction;
  :设置aggregationField;
}

partition "派生指标整合" {
  :遍历派生指标;
  :查找关联的原子指标ID;
  :继承businessProcess;
  :补充filterConditions;
  :补充dimensions;
}

partition "复合指标整合" {
  :遍历复合指标;
  :解析derivedFormula;
  :查找依赖的基础指标ID;
  :设置baseMetricIds;
}

:建立依赖关系图;
:返回IntegratedMetrics;
stop
@enduml
```

#### 3.6.2 指标验证

```plantuml
@startuml
start
:MetricValidator.validate();

partition "原子指标验证" {
  :检查必填字段;
  note right
    - businessProcess
    - aggregationFunction
    - aggregationField
  end note
  
  if (缺少必填字段?) then (是)
    :记录ERROR;
  endif
  
  :检查aggregationFunction有效性;
  note right
    必须是COUNT/SUM/AVG/MAX/MIN之一
  end note
}

partition "派生指标验证" {
  :检查atomicMetricId存在性;
  :检查filterConditions格式;
  :检查dimensions有效性;
}

partition "复合指标验证" {
  :检查derivedFormula合法性;
  :检查baseMetricIds引用;
  :检查循环依赖;
}

:返回ValidationResult列表;
stop
@enduml
```

---

## 4. 关键数据结构

### 4.1 ExtractedMetric

```java
public class ExtractedMetric {
    // 基础信息
    private String id;
    private String name;               // 指标名称
    private String displayName;        // 显示名称
    private MetricCategory category;   // ATOMIC/DERIVED/COMPOSITE
    private ConfidenceLevel confidence; // HIGH/MEDIUM/LOW
    
    // 原子指标专属
    private String businessProcess;    // 业务过程(必填)
    private String aggregationFunction; // 聚合函数(必填)
    private String aggregationField;   // 聚合字段(必填)
    
    // 派生指标专属
    private String atomicMetricId;     // 关联的原子指标ID
    private Map<String, Object> filterConditions; // 过滤条件
    private List<String> dimensions;   // 维度列表
    
    // 复合指标专属
    private String derivedFormula;     // 计算公式
    private List<String> baseMetricIds; // 依赖的基础指标ID列表
    
    // 血缘追踪(RexNode解析结果)
    private List<ColumnSource> sources; // 字段血缘
    private String transformType;       // 转换类型
    private String rexNodeType;        // RexNode类型
}
```

### 4.2 SqlLayer

```java
public class SqlLayer {
    private int depth;                   // 层级深度(0=最外层)
    private String layerSql;             // 该层SQL片段
    private List<String> selectFields;   // SELECT字段
    private List<AggregationInfo> aggregations; // 聚合函数
    private List<CaseInfo> caseExpressions;    // CASE表达式
    private List<String> fromTables;     // FROM表
    private List<String> groupByFields;  // GROUP BY字段
    private String whereClause;          // WHERE条件
    private boolean hasSubQuery;         // 是否有子查询
}
```

### 4.3 CalciteSqlParseResult

```java
public class CalciteSqlParseResult {
    private SqlNode sqlNode;                      // Calcite AST
    private List<TableReference> tables;          // 表列表
    private List<SelectField> selectFields;       // SELECT字段
    private List<AggregationInfo> aggregations;   // 聚合字段
    private List<WhereCondition> whereConditions; // WHERE条件
    private List<String> groupByFields;           // GROUP BY字段
    private List<JoinInfo> joins;                 // JOIN信息
    private List<TimeCondition> timeConditions;   // 时间条件
}
```

---

## 5. 核心算法

### 5.1 括号平衡算法(子查询提取)

```java
private List<String> extractSubQueriesWithBalancedParentheses(String sql, String keyword) {
    List<String> subQueries = new ArrayList<>();
    Pattern keywordPattern = Pattern.compile("\\b" + keyword + "\\s+\\(", Pattern.CASE_INSENSITIVE);
    Matcher keywordMatcher = keywordPattern.matcher(sql);
    
    while (keywordMatcher.find()) {
        int startPos = keywordMatcher.end() - 1; // 指向左括号
        int depth = 0;
        int endPos = -1;
        
        // 括号平衡计数
        for (int i = startPos; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) {
                    endPos = i;
                    break;
                }
            }
        }
        
        if (endPos > startPos) {
            String subQuery = sql.substring(startPos + 1, endPos).trim();
            if (subQuery.toUpperCase().startsWith("SELECT")) {
                subQueries.add(subQuery);
            }
        }
    }
    return subQueries;
}
```

**时间复杂度**: O(n)，其中n为SQL字符串长度

### 5.2 字段映射匹配算法

```plantuml
@startuml
start
:输入字段名;

:策略1: 精确匹配;
if (成功?) then (是)
  :返回HIGH;
  stop
endif

:策略2: 大小写不敏感;
if (成功?) then (是)
  :返回MEDIUM;
  stop
endif

:策略3: snake_case转换;
note right
  camelCase → camel_case
end note
if (成功?) then (是)
  :返回MEDIUM;
  stop
endif

:策略4: camelCase转换;
note right
  snake_case → snakeCase
end note
if (成功?) then (是)
  :返回MEDIUM;
  stop
endif

:返回LOW(无匹配);
stop
@enduml
```

### 5.3 指标分类决策树

```plantuml
@startuml
start
:输入SQL层级;

if (最外层有聚合?) then (是)
  if (聚合字段是*?) then (是)
    :原子指标(COUNT(*));
  else (否)
    :派生指标(SUM/AVG等);
  endif
  stop
endif

:遍历各层;

if (当前层是最内层?) then (是)
  :提取聚合字段;
  :标记为原子指标;
elseif (当前层是中间层?) then (是)
  :提取聚合+条件;
  :关联原子指标;
  :标记为派生指标;
else (最外层)
  if (包含算术运算?) then (是)
    :解析运算表达式;
    :查找依赖指标;
    :标记为复合指标;
  endif
endif

stop
@enduml
```

---

## 6. 策略路由机制

系统采用4级策略路由，按优先级依次尝试：

```plantuml
@startuml
start
:接收SQL;

:策略1: RexNode血缘解析;
if (解析成功?) then (是)
  :返回指标;
  stop
endif

:策略2: LEFT JOIN报表解析;
if (是JOIN结构?) then (是)
  :extractMetricsForJoinReport();
  :返回指标;
  stop
endif

:策略3: 多层报表解析;
if (是MULTI_LAYER_REPORT?) then (是)
  :extractMetricsForComplexReport();
  if (成功?) then (是)
    :返回指标;
    stop
  else (失败)
    :降级到策略4;
  endif
endif

:策略4: 标准提取;
:extractMetrics();
:返回指标;
stop
@enduml
```

**策略选择依据**:
1. **RexNode**: 精确度最高，优先使用
2. **LEFT JOIN**: 针对关联报表优化
3. **多层报表**: 处理复杂嵌套SQL
4. **标准提取**: 兜底方案

---

## 7. 典型场景处理

### 7.1 简单聚合查询

**输入SQL**:
```sql
SELECT COUNT(*) AS total_count
FROM orders
WHERE status = 'paid'
```

**处理流程**:
1. Calcite解析 → 1个聚合 + 1个WHERE条件
2. 映射对齐 → orders表映射到Order对象
3. 标准提取 → 识别为单层聚合
4. 生成1个原子指标：`total_count (COUNT(*), Order)`

### 7.2 多层嵌套报表

**输入SQL**:
```sql
SELECT 
  payment_type,
  SUM(amount) / COUNT(*) AS avg_amount
FROM (
  SELECT payment_type, amount
  FROM orders
  WHERE status = 'paid'
) t
GROUP BY payment_type
```

**处理流程**:
1. 结构识别 → MULTI_LAYER_REPORT(2层)
2. 层级提取:
   - 内层(depth=1): `amount`, `payment_type`
   - 外层(depth=0): `SUM(amount)`, `COUNT(*)`, `GROUP BY payment_type`
3. 分层提取:
   - 原子指标: `amount`
   - 派生指标: `sum_amount (SUM(amount), dimension=payment_type)`
   - 派生指标: `count_star (COUNT(*))`
   - 复合指标: `avg_amount (sum_amount / count_star)`

### 7.3 LEFT JOIN关联查询

**输入SQL**:
```sql
SELECT 
  o.order_id,
  SUM(i.quantity) AS total_qty
FROM orders o
LEFT JOIN order_items i ON o.id = i.order_id
GROUP BY o.order_id
```

**处理流程**:
1. 结构识别 → JOIN结构检测
2. ReportJoinMetricHandler处理
3. 识别跨表聚合
4. 生成派生指标：`total_qty (SUM(OrderItem.quantity), groupBy=Order.order_id)`

---

## 8. 异常处理与降级

```plantuml
@startuml
start
:执行高级策略;

if (解析异常?) then (是)
  :记录错误日志;
  :降级到下一策略;
  
  if (所有策略都失败?) then (是)
    :返回空结果;
    :添加错误信息;
    stop
  endif
endif

:策略执行成功;
stop
@enduml
```

**常见异常**:
- `SqlParseException`: SQL语法错误 → 返回错误详情
- `NullPointerException`: 映射配置缺失 → 添加到unmappedFields
- `IllegalArgumentException`: 指标必填字段缺失 → 验证阶段捕获

---

## 9. 性能优化要点

### 9.1 缓存策略
- 对象类型缓存(Loader)
- 映射配置缓存(MappingService)
- Calcite解析结果复用

### 9.2 复杂度控制
- 最大子查询深度限制(默认5层)
- SELECT字段数量限制
- 超时控制(LLM调用)

### 9.3 并行处理
- 字段映射可并行处理
- 多指标验证可并行

---

## 10. 未来改进方向

1. **智能分类**: 基于机器学习的指标类型自动分类
2. **增量解析**: 支持SQL增量修改的快速重新解析
3. **可视化**: 生成指标依赖关系图
4. **模板匹配**: 常见SQL模式的快速识别
5. **跨库支持**: 扩展到更多SQL方言(PostgreSQL/Oracle)

---

## 11. 关键配置与依赖

### Maven依赖
```xml
<dependency>
    <groupId>org.apache.calcite</groupId>
    <artifactId>calcite-core</artifactId>
    <version>1.32.0</version>
</dependency>
```

### 核心配置
- `mapping表`: 存储表→对象类型映射
- `column_property_mappings`: 字段→属性映射JSON
- LLM配置(可选): API密钥、模型选择

---

## 12. 总结

SQL指标提取功能通过以下技术实现了从SQL到业务指标的自动转换：

1. **Apache Calcite**: 提供SQL语法解析能力
2. **多策略路由**: 根据SQL复杂度选择最优解析策略
3. **血缘追踪**: 基于RexNode实现精确的字段溯源
4. **映射系统**: 打通物理表与业务对象的桥梁
5. **分层提取**: 支持原子→派生→复合的指标体系

**核心优势**:
- ✅ 支持复杂SQL(多层嵌套、JOIN、CASE等)
- ✅ 自动建立指标依赖关系
- ✅ 精确的字段血缘追踪
- ✅ 灵活的降级机制
- ✅ 可扩展的架构设计

**典型应用场景**:
- 数据仓库指标自动化管理
- SQL报表逆向工程
- 数据血缘分析
- 指标口径统一
