# MyPalantir - 基于 Ontology 的数据模型管理平台

一个仿照 Palantir Foundry Ontology 设计理念的数据模型管理平台，通过 Ontology（本体）抽象层实现业务概念与物理数据源的解耦，提供统一的查询接口和语义化的数据访问能力。

## 核心理念

### Ontology 驱动的数据模型

MyPalantir 的核心思想是**将业务概念与物理存储解耦**，通过 Ontology（本体）层建立业务语义与底层数据源的映射关系。

```
业务概念层（Ontology）
    ↓ 映射
物理数据层（Database/File System）
```

**核心优势：**
- **语义化查询**：使用业务概念（如"车辆"、"收费站"）而非表名、列名进行查询
- **数据源无关**：同一业务概念可以映射到不同的物理数据源（PostgreSQL、MySQL、H2、文件系统等）
- **关系抽象**：通过 LinkType 抽象对象间的关系，支持多种物理实现模式
- **统一接口**：提供统一的查询 DSL，屏蔽底层数据源的差异
- **声明式推理**：通过衍生属性、函数和规则的三层协作，将业务分析逻辑从硬编码转变为声明式定义

### 设计原则

1. **概念优先**：查询和操作都基于 Ontology 中定义的概念，而非物理表结构
2. **映射灵活**：支持多种数据源映射模式，适应不同的数据库设计
3. **查询优化**：基于 Apache Calcite 的查询优化器，自动生成高效的 SQL
4. **类型安全**：完整的 Schema 验证机制，确保数据模型的一致性
5. **数据-算法-推理分离**：衍生属性负责数据计算，函数负责复杂算法，规则负责逻辑推理

## 系统架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     应用层 (Application Layer)                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │  Web UI      │  │  REST API    │  │  AI Agent    │        │
│  │  (React)     │  │  (Spring)     │  │  (Tool Call) │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    Ontology 层 (Ontology Layer)              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  Schema Definition (YAML)                          │    │
│  │  - ObjectType (对象类型)      - LinkType (关系类型)  │    │
│  │  - Property (属性定义)        - DataSourceMapping    │    │
│  │  - DerivedProperty (衍生属性, CEL)                  │    │
│  │  - Function (函数/工具定义)                          │    │
│  │  - Rule (推理规则, SWRL)                            │    │
│  └──────────────────────────────────────────────────────┘    │
│                            ↓                                 │
│  ┌───────────────────────┐  ┌────────────────────────────┐   │
│  │  Query Engine         │  │  Reasoning Engine          │   │
│  │  - OntologyQuery DSL  │  │  - CEL 衍生属性求值        │   │
│  │  - RelNode → SQL      │  │  - Function 调用           │   │
│  │  - Calcite 优化器      │  │  - SWRL 前向链推理         │   │
│  └───────────────────────┘  └────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   数据源层 (Data Source Layer)                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │  JDBC        │  │  File System  │  │  External    │        │
│  │  (Database)  │  │  (JSON)      │  │  API/Service │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### 推理引擎架构：数据-算法-推理三层分离

传统业务分析系统中，分析逻辑通常硬编码为工作流或过程式代码。MyPalantir 采用声明式的方式，将业务分析逻辑分解为三个层次，各司其职：

```
┌─────────────────────────────────────────────────────────────┐
│  衍生属性（CEL 表达式）—— 负责"数据"                          │
│  简单聚合、等值判断、集合比较                                   │
│  例：path_fee_total = sum(路径明细.费用)                      │
│      detail_count_matched = count(路径明细) == count(拆分明细) │
├─────────────────────────────────────────────────────────────┤
│  函数/工具（Function）—— 负责"算法"                            │
│  序列分析、模式匹配、外部计算、外部数据                          │
│  例：check_gantry_hex_continuity(门架列表) → boolean          │
│      check_balance_continuity(门架列表) → boolean             │
├─────────────────────────────────────────────────────────────┤
│  推理规则（SWRL）—— 负责"推理"                                │
│  组合命题、链式推导、根因诊断                                   │
│  例：路径不一致 ∧ 门架HEX不连续 → 根因="ETC门架不完整"         │
└─────────────────────────────────────────────────────────────┘
```

#### 设计决策：什么放在哪一层

| 场景                                     | 放置层         | 原因                   |
| ---------------------------------------- | -------------- | ---------------------- |
| 用户需要看到的中间值（费用合计、数量）   | 衍生属性       | 界面展示需要           |
| 同一计算被多条规则引用                   | 衍生属性       | 作为缓存避免重复计算   |
| 有序遍历、相邻比较、重复检测等算法       | 函数           | 表达式难以清晰表达     |
| 涉及外部服务调用（如牌识拟合）           | 函数           | 需要 external 实现     |
| 多条件组合判断、根因分类、链式传播       | 规则           | 声明式推理的核心价值   |
| 纯判断且只用一次                         | 规则直接调函数 | 无需中间层             |

#### 函数定义

函数在 YAML 中声明输入输出签名，实现方式可以是引擎内置（`builtin`）或外部服务（`external`）：

```yaml
functions:
  - name: check_gantry_hex_continuity
    display_name: 门架HEX连续性检测
    description: "检查相邻门架的HEX编码是否首尾衔接"
    input:
      - name: gantry_transactions
        type: "list<GantryTransaction>"
    output:
      type: boolean
    implementation: builtin

  - name: fit_actual_route
    display_name: 实际路径拟合
    description: "根据牌识流水拟合实际路径，与门架计费路径比对"
    input:
      - name: passage
        type: Passage
    output:
      type: boolean
    implementation: external   # 调用外部牌识服务
```

函数定义天然兼容 **LLM Agent 的 tool schema**——推理引擎批量调用做监控筛查，智能体按需调用做个案诊断，共用同一套函数：

|            | 推理引擎                 | 智能体 (Agent)               |
| ---------- | ------------------------ | ---------------------------- |
| 触发方式   | 规则前件匹配时自动调用   | Agent 根据推理需要主动调用   |
| 调用范围   | 批量、全量扫描           | 单条、按需探查               |
| 适合场景   | 日常监控、批量筛查       | 个案分析、交互式诊断         |

#### 规则如何调用函数

规则中直接通过函数名调用，推理引擎负责参数绑定和执行：

```yaml
rules:
  # 衍生属性驱动的简单规则（基于缓存值）
  - name: passage_integrity_normal
    expr: >
      Passage(?p) ∧ detail_count_matched(?p, true)
        ∧ interval_set_matched(?p, true)
        ∧ fee_matched(?p, true)
        → check_status(?p, "正常")

  # 函数驱动的深度诊断规则（调用算法）
  - name: obu_route_cause_etc_incomplete
    expr: >
      Passage(?p) ∧ obu_split_status(?p, "路径不一致")
        ∧ detect_duplicate_intervals(
            links(?p, passage_has_gantry_transactions)) == false
        ∧ check_gantry_hex_continuity(
            links(?p, passage_has_gantry_transactions)) == false
        → obu_route_cause(?p, "ETC门架不完整")
```

#### 业务示例：OBU 拆分异常诊断

以高速公路 ETC(OBU) 交易拆分异常分析为例，展示三层协作的完整过程。

传统方式下，这个分析过程需要编码为一个多步工作流（查询、循环、比较、赋值、传播）。在 MyPalantir 中，只需声明业务命题和推理关系，引擎自动完成匹配和推导：

```
前置条件筛选（函数判定）
│  is_single_province_etc(?p) ∧ is_obu_billing_mode1(?p)
│
├─ 维度一：路径一致性（函数检测 + 规则推导根因）
│   check_route_consistency(拆分明细, 门架流水) == false
│   ├─ detect_duplicate_intervals() == true    → "门架收费单元重复"
│   ├─ check_gantry_hex_continuity() == false  → "ETC门架不完整"
│   │   └─ detect_late_upload() == true        → "门架延迟上传"
│   └─ check_gantry_count_complete() == false  → "CPC门架不完整"
│       └─ detect_late_upload() == true        → "门架延迟上传"
│
├─ 维度二：金额一致性（函数检测 + 规则推导根因）
│   check_fee_detail_consistency(拆分明细, 门架流水) == false
│   ├─ check_rounding_mismatch() == true       → "四舍五入取整差异"
│   └─ check_balance_continuity() == false     → "卡内累计金额异常"
│
├─ 维度三：实际路径 vs 计费路径（外部函数）
│   fit_actual_route(?p) == false              → "实际路径偏差"
│
└─ 链式传播（规则自动推导）
    异常 Passage → 标记关联 Vehicle → 标记关联 TollStation
```

对应的衍生属性提供用户在界面上看到的中间数据：

```
通行路径详情：
  路径明细数量:    5
  拆分明细数量:    4        ← 用户一眼看到差异
  门架交易数量:    5
  路径费用合计:    158.00
  拆分费用合计:    156.00   ← 用户看到金额差异
  门架费用合计:    158.00
  诊断结论:        路径不一致 → ETC门架不完整 → 门架延迟上传
```

### 查询引擎架构

查询引擎是系统的核心，实现了从 Ontology 查询 DSL 到物理 SQL 的完整转换流程：

```
OntologyQuery (JSON/YAML)
    ↓ [QueryParser]
OntologyQuery (Java Object)
    ↓ [RelNodeBuilder]
Calcite RelNode (关系代数树)
    ↓ [Calcite Optimizer]
Optimized RelNode
    ↓ [OntologyRelToSqlConverter]
SQL (物理数据库查询)
    ↓ [JDBC Execution]
QueryResult (结果集)
```

#### 关键组件

1. **OntologyQuery DSL**
   - GraphQL 风格的查询语言
   - 支持 `object`、`select`、`filter`、`links`、`group_by`、`metrics` 等
   - 完全基于 Ontology 概念，不涉及物理表名、列名

2. **RelNodeBuilder**
   - 将 OntologyQuery 转换为 Calcite RelNode（关系代数树）
   - 处理 JOIN、Filter、Project、Aggregate、Sort、Limit 等操作
   - 自动处理 LinkType 的 JOIN 逻辑

3. **OntologySchemaFactory**
   - 将 Ontology Schema 转换为 Calcite Schema
   - 为每个 ObjectType 和 LinkType 创建 Calcite Table
   - 处理属性名到列名的映射

4. **JdbcOntologyTable**
   - Calcite Table 实现，负责从 JDBC 数据源读取数据
   - 处理 Ontology 属性名与数据库列名的映射
   - 支持类型转换（如 TIMESTAMP → Long）

5. **OntologyRelToSqlConverter**
   - 自定义 SQL 转换器
   - 将 Calcite 生成的 SQL 中的 Ontology 名称映射为数据库物理名称
   - 处理表名、列名的引用

### LinkType 映射模式

系统支持两种 LinkType 映射模式，适应不同的数据库设计：

#### 1. 外键模式 (Foreign Key Mode)

**适用场景**：关系信息存储在目标表中（通过外键）

**示例**：收费站 → 收费记录
- 收费记录表（`toll_records`）包含 `station_id` 外键
- LinkType 的 `table` 与目标表的 `table` 相同
- JOIN 逻辑：`source_table JOIN target_table`（1 次 JOIN）

**配置示例**：
```yaml
link_types:
  - name: 拥有收费记录
    source_type: 收费站
    target_type: 收费记录
    data_source:
      table: toll_records  # 与目标表相同
      source_id_column: station_id
      target_id_column: record_id
      # link_mode: foreign_key  # 可显式指定，或自动检测
```

#### 2. 关系表模式 (Relation Table Mode)

**适用场景**：使用独立的中间表存储关系（多对多或需要关系属性）

**示例**：车辆 → 通行介质
- 独立的中间表（`vehicle_media`）存储关系
- LinkType 的 `table` 是独立的中间表
- JOIN 逻辑：`source_table JOIN link_table JOIN target_table`（2 次 JOIN）

**配置示例**：
```yaml
link_types:
  - name: 持有
    source_type: 车辆
    target_type: 通行介质
    data_source:
      table: vehicle_media  # 独立的中间表
      source_id_column: vehicle_id
      target_id_column: media_id
      link_mode: relation_table  # 显式指定
      field_mapping:
        绑定时间: bind_time
        绑定状态: bind_status
    properties:
      - name: 绑定时间
        data_type: datetime
      - name: 绑定状态
        data_type: string
```

**自动检测机制**：
- 如果 `link_type.table == target_type.table` → 外键模式
- 否则 → 关系表模式
- 可通过 `link_mode` 显式指定

### 查询处理流程

#### 1. 查询解析阶段

```json
{
  "object": "收费站",
  "links": [{"name": "拥有收费记录"}],
  "filter": [
    ["=", "省份", "江苏"],
    ["between", "拥有收费记录.收费时间", "2024-01-01", "2024-01-31"]
  ],
  "group_by": ["名称"],
  "metrics": [["sum", "拥有收费记录.金额", "总金额"]]
}
```

**处理步骤**：
1. `QueryParser` 解析 JSON 为 `OntologyQuery` 对象
2. 验证对象类型、关系类型是否存在
3. 解析字段路径（如 `拥有收费记录.收费时间`）

#### 2. RelNode 构建阶段

**操作顺序**：
1. **TableScan**：扫描根对象表
2. **JOIN**：根据 LinkType 映射模式构建 JOIN
   - 外键模式：`source JOIN target`
   - 关系表模式：`source JOIN link_table JOIN target`
3. **Filter**：应用过滤条件（支持字段路径）
4. **Aggregate**：处理分组和聚合（如果有）
5. **Project**：选择输出字段
6. **Sort**：排序（如果有）
7. **Limit**：限制结果数量

#### 3. SQL 生成阶段

**转换过程**：
1. Calcite 优化器优化 RelNode
2. `OntologyRelToSqlConverter` 转换为 SQL
3. 映射 Ontology 名称 → 数据库名称：
   - 对象类型名 → 表名
   - 属性名 → 列名（通过 `field_mapping`）
4. 生成最终 SQL 并执行

**生成的 SQL 示例**：
```sql
SELECT "收费站"."名称", SUM(CAST("收费记录"."金额" AS DOUBLE)) AS "总金额"
FROM "收费站"
LEFT JOIN "收费记录" ON "收费站"."id" = "收费记录"."station_id"
WHERE "收费站"."省份" = '江苏' 
  AND ("收费记录"."收费时间" >= TIMESTAMP '2024-01-01 00:00:00' 
   AND "收费记录"."收费时间" <= TIMESTAMP '2024-01-31 00:00:00')
GROUP BY "收费站"."名称"
```

## 技术架构

### 技术栈

**后端**：
- **Java 17**：现代 Java 特性
- **Spring Boot 3.2.0**：应用框架
- **Apache Calcite 1.37.0**：查询优化引擎
- **Jackson**：JSON/YAML 处理
- **H2 Database**：本地测试数据库

**前端**：
- **React 18 + TypeScript**：现代化 UI 框架
- **Vite**：快速构建工具
- **Tailwind CSS**：实用优先的 CSS 框架
- **React Router**：单页应用路由

### 核心模块

```
src/main/java/com/mypalantir/
├── meta/              # Ontology 元数据层
│   ├── OntologySchema.java    # Schema 定义
│   ├── ObjectType.java        # 对象类型
│   ├── LinkType.java          # 关系类型
│   ├── DataSourceMapping.java # 数据源映射
│   ├── Parser.java            # YAML 解析器
│   ├── Validator.java         # Schema 验证器
│   └── Loader.java            # Schema 加载器
│
├── query/             # 查询引擎层
│   ├── OntologyQuery.java              # 查询 DSL 定义
│   ├── QueryParser.java                # 查询解析器
│   ├── RelNodeBuilder.java             # RelNode 构建器
│   ├── QueryExecutor.java              # 查询执行器
│   ├── OntologyRelToSqlConverter.java  # SQL 转换器
│   ├── FieldPathResolver.java          # 字段路径解析器
│   └── schema/
│       ├── OntologySchemaFactory.java  # Calcite Schema 工厂
│       ├── JdbcOntologyTable.java      # JDBC Table 实现
│       └── OntologyTable.java          # Table 基类
│
├── service/           # 业务逻辑层
│   ├── QueryService.java      # 查询服务
│   ├── SchemaService.java     # Schema 服务
│   └── DataValidator.java     # 数据验证服务
│
├── controller/        # REST API 层
│   ├── QueryController.java   # 查询 API
│   └── SchemaController.java  # Schema API
│
└── config/            # 配置层
    └── WebConfig.java          # Web 配置
```

### 数据流

```
用户查询 (JSON)
    ↓
QueryController
    ↓
QueryService
    ↓
QueryParser → OntologyQuery
    ↓
QueryExecutor
    ├→ RelNodeBuilder → RelNode
    ├→ OntologySchemaFactory → Calcite Schema
    └→ OntologyRelToSqlConverter → SQL
    ↓
JDBC Execution
    ↓
QueryResult → JSON Response
```

## 快速开始

### 前置要求

- **Java 17+**
- **Maven 3.6+**
- **Node.js 18+**（用于构建 Web UI）

### 安装与运行

```bash
# 1. 克隆项目
git clone https://github.com/caochun/mypalantir.git
cd mypalantir

# 2. 构建后端
mvn clean install

# 3. 构建前端
cd web && npm install && npm run build && cd ..

# 4. 运行服务
mvn spring-boot:run
```

访问 http://localhost:8080 查看 Web UI。

### 配置

编辑 `src/main/resources/application.properties`：

```properties
server.port=8080
schema.file.path=./ontology/schema.yaml
data.root.path=./data
web.static.path=./web/dist
```

## 查询示例

### 基础查询

```json
{
  "object": "车辆",
  "select": ["车牌号", "车辆类型", "车主姓名"],
  "filter": [["=", "车辆类型", "小型客车"]],
  "limit": 10
}
```

### 关联查询

```json
{
  "object": "车辆",
  "select": ["车牌号"],
  "links": [{
    "name": "持有",
    "select": ["介质编号", "介质类型", "绑定时间"]
  }]
}
```

### 聚合查询

```json
{
  "object": "收费站",
  "links": [{"name": "拥有收费记录"}],
  "filter": [
    ["=", "省份", "江苏"],
    ["between", "拥有收费记录.收费时间", "2024-01-01", "2024-01-31"]
  ],
  "group_by": ["名称"],
  "metrics": [["sum", "拥有收费记录.金额", "总金额"]]
}
```

## 项目结构

```
mypalantir/
├── ontology/              # Ontology 定义
│   ├── schema.yaml        # 基础 Schema 定义文件
│   └── toll.yaml          # 高速收费业务模型（含衍生属性、函数、规则）
├── src/main/java/         # Java 源代码
├── web/                   # React 前端
│   ├── src/               # 源代码
│   └── dist/              # 构建产物
├── scripts/               # 工具脚本
└── data/                  # 数据目录（运行时生成）
```

## 许可证

本项目为仿制项目，仅供学习和研究使用。
