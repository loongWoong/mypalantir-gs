# MyPalantir - 基于 Ontology 的数据模型管理平台

一个仿照 Palantir Foundry Ontology 设计理念的数据模型管理平台，通过 Ontology（本体）抽象层实现业务概念与物理数据源的解耦，提供统一的查询接口、语义化的数据访问能力以及智能化的数据分析工具。

## 核心理念

### Ontology 驱动的数据模型

MyPalantir 的核心思想是**将业务概念与物理存储解耦**，通过 Ontology（本体）层建立业务语义与底层数据源的映射关系。

```
业务概念层 (Ontology)
    ↓ 映射
物理数据层 (Database/File System)
```

**核心优势：**
- **语义化查询**：使用业务概念（如"车辆"、"收费站"）而非表名、列名进行查询
- **数据源无关**：同一业务概念可以映射到不同的物理数据源（PostgreSQL、MySQL、H2、Neo4j、文件系统等）
- **关系抽象**：通过 LinkType 抽象对象间的关系，支持多种物理实现模式
- **统一接口**：提供统一的查询 DSL，屏蔽底层数据源的差异
- **智能化增强**：集成 LLM 实现自然语言查询 (Text-to-DSL)
- **指标体系**：内置原子指标与派生指标引擎，支持多维分析
- **数据治理**：提供跨数据源的数据一致性对比工具

### 设计原则

1. **概念优先**：查询和操作都基于 Ontology 中定义的概念，而非物理表结构
2. **映射灵活**：支持多种数据源映射模式，适应不同的数据库设计
3. **查询优化**：基于 Apache Calcite 的查询优化器，自动生成高效的 SQL
4. **类型安全**：完整的 Schema 验证机制，确保数据模型的一致性

## 系统架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     应用层 (Application Layer)                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │  Web UI      │  │  REST API    │  │  Query DSL   │        │
│  │  (React)     │  │  (Spring)    │  │  (JSON)      │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   智能服务层 (Intelligence Layer)             │
│  ┌──────────────────────┐    ┌──────────────────────────┐    │
│  │  Metric Engine       │    │  LLM Service             │    │
│  │  (指标计算引擎)        │    │  (自然语言转换)           │    │
│  └──────────────────────┘    └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    Ontology 层 (Ontology Layer)              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  Schema Definition (YAML)                          │    │
│  │  - ObjectType (对象类型)                            │    │
│  │  - LinkType (关系类型)                              │    │
│  │  - Property (属性定义)                              │    │
│  │  - DataSourceMapping (数据源映射)                   │    │
│  └──────────────────────────────────────────────────────┘    │
│                            ↓                                 │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  Query Engine (查询引擎)                             │    │
│  │  - OntologyQuery DSL → RelNode → SQL                │    │
│  │  - Apache Calcite 优化器                             │    │
│  │  - 自动 JOIN 优化                                    │    │
│  └──────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   数据源层 (Data Source Layer)                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │  JDBC        │  │  File System  │  │  Neo4j       │        │
│  │  (Database)  │  │  (JSON)      │  │  (Graph DB)  │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
└─────────────────────────────────────────────────────────────┘
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
   - 支持有向关系的单向查询和无向关系的双向查询

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

#### 3. 关系方向与查询支持

系统支持两种关系方向：

**有向关系（Directed）**：
- 只能从源对象类型（source_type）查询到目标对象类型（target_type）
- 示例：收费站 → 收费记录（收费站可以查询收费记录，但收费记录不能反向查询收费站）

**无向关系（Undirected）**：
- 支持双向查询，可以从任意一端查询到另一端
- 示例：车辆 ↔ 通行介质（可以从车辆查询通行介质，也可以从通行介质查询车辆）
- 在查询构建器中，无向关系会自动显示为双向箭头（↔）

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
- **react-force-graph-2d**：力导向图可视化库
- **Heroicons**：图标库

### 核心模块

```
src/main/java/com/mypalantir/
├── meta/              # Ontology 元数据层
│   ├── OntologySchema.java    # Schema 定义
│   ├── ObjectType.java        # 对象类型
│   ├── LinkType.java          # 关系类型
│   └── ...
│
├── metric/            # 指标体系
│   ├── AtomicMetric.java      # 原子指标定义
│   ├── MetricDefinition.java  # 指标定义
│   └── ...
│
├── query/             # 查询引擎层
│   ├── OntologyQuery.java              # 查询 DSL 定义
│   ├── RelNodeBuilder.java             # RelNode 构建器
│   ├── OntologyRelToSqlConverter.java  # SQL 转换器
│   └── ...
│
├── service/           # 业务逻辑层
│   ├── QueryService.java      # 查询服务
│   ├── MetricService.java     # 指标服务
│   ├── LLMService.java        # LLM 服务
│   ├── DataComparisonService.java # 数据对比服务
│   └── ...
│
├── controller/        # REST API 层
│   ├── QueryController.java           # 查询 API
│   ├── MetricController.java          # 指标 API
│   ├── NaturalLanguageQueryController.java # NLQ API
│   └── ...
│
└── repository/        # 数据存储层
    ├── InstanceStorage.java   # 实例存储接口
    └── Neo4jInstanceStorage.java # Neo4j 实现
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
schema.system.file.path=./ontology/schema-system.yaml
data.root.path=./data
web.static.path=./web/dist

# LLM 配置 (用于自然语言查询)
llm.api.key=your-api-key
llm.api.url=https://api.deepseek.com/v1/chat/completions
llm.model=deepseek-chat
```

## 核心功能特性

### 1. 智能指标管理 (Metric Management)

提供完整的指标定义与计算引擎，支持从原子指标到复杂复合指标的构建。

**主要特性**：
- **原子指标 (Atomic Metrics)**：基于 SQL 直接查询的基础指标
- **派生指标 (Derived Metrics)**：基于公式计算的衍生指标
- **复合指标 (Composite Metrics)**：组合多个指标的多维分析
- **可视化构建器**：提供图形化的指标构建界面
- **批量计算**：支持基于时间范围和维度的批量指标计算

### 2. 自然语言查询 (Natural Language Query)

集成 LLM 能力，降低数据查询门槛，实现"对话即查询"。

**主要特性**：
- **Text-to-DSL**：将自然语言自动转换为 OntologyQuery DSL
- **智能上下文**：基于当前 Ontology Schema 自动生成 Prompt
- **交互式界面**：提供聊天式查询界面，实时预览转换结果和数据结果
- **调试模式**：支持查看转换过程的中间结果

### 3. 数据一致性对比 (Data Reconciliation)

用于验证不同数据源或不同模型之间的数据一致性，保障数据质量。

**主要特性**：
- **双模式对比**：
  - **基于数据表 (By Table)**：直接对比两个物理表的差异
  - **基于对象模型 (By Model)**：对比不同工作空间/模型下的对象数据
- **差异报告**：详细展示完全匹配、值不一致、仅源表存在、仅目标表存在的数据
- **字段映射**：支持自定义源字段与目标字段的映射关系

### 4. 模型与数据源管理

**模型管理 (Model Management)**：
- 支持动态切换 Ontology 模型文件
- 支持多版本模型共存

**数据源管理 (Data Source Management)**：
- 可视化配置 JDBC 数据源
- 内置连接测试与元数据探测功能

### 5. 工作空间管理

工作空间功能允许对对象类型和关系类型进行分组管理，实现系统模型与业务模型的分离。

**主要特性**：
- 创建工作空间，指定包含的对象类型和关系类型
- 根据工作空间过滤显示内容（Schema 浏览器、查询构建器等）
- 系统工作空间（包含 `workspace`、`database`、`table`、`column`、`mapping`）自动隐藏管理功能
- 支持多个工作空间切换

**使用场景**：
- 分离系统元数据（数据库、表、列）和业务数据（车辆、收费站等）
- 按业务领域分组管理（如"收费管理"、"车辆管理"等）
- 简化界面，只显示相关的内容

### 数据映射功能

支持将外部数据库的表和字段映射到 Ontology 对象类型和属性。

**主要功能**：
- 连接外部数据库（MySQL、PostgreSQL 等）
- 自动同步数据库表结构
- 可视化配置表字段与对象属性的映射关系（ER 图形式）
- 从数据库抽取数据到模型实例
- 支持主键映射，保持数据一致性

### 关系自动同步

基于属性映射规则自动创建关系实例。

**工作原理**：
- 在 LinkType 中定义 `property_mappings`（属性映射规则）
- 系统根据映射规则自动匹配对象实例
- 当源对象和目标对象的属性值匹配时，自动创建关系

**示例**：
```yaml
link_types:
  - name: 拥有收费记录
    source_type: 收费站
    target_type: 收费记录
    property_mappings:
      收费站编号: 收费站编号  # 当收费站的"收费站编号" = 收费记录的"收费站编号"时，自动创建关系
```

### 血缘查询

支持在实例关系图中进行血缘查询，追踪数据流向。

**查询模式**：
- **直接关系**：查看与当前节点直接连接的节点
- **正向血缘**：从当前节点向后递归查询所有下游节点
- **反向血缘**：从当前节点向前递归查询所有上游节点
- **全链血缘**：从当前节点前后递归查询所有相关节点

**应用场景**：
- 数据溯源：追踪数据的来源
- 影响分析：分析数据变更的影响范围
- 关系探索：发现数据之间的复杂关联

### 前端功能特性

**核心业务模块**：
- **Metric Browser**：指标列表管理与筛选
- **Metric Builder**：可视化指标定义构建器
- **NLQ Interface**：自然语言查询对话界面
- **Data Reconciliation**：数据对比任务配置与结果展示
- **Data Source Manager**：数据源连接配置与测试

**Schema 浏览器**：
- 图形化查看对象类型、关系类型及其属性
- 支持交互式过滤（点击对象/关系进行关联过滤）
- 显示属性映射关系图（LinkType 的 property_mappings）
- 支持 Tab 切换查看 Properties 和 Data Source 配置
- 显示数据源映射信息和字段映射

**Schema 关系图**：
- 力导向图可视化展示 Schema 定义
- 支持节点拖动和自动布局
- 自动缩放功能，图加载完成后自动适配显示
- 虚线箭头表示关系，支持有向和无向关系的可视化
- 点击节点和边查看详细信息

**查询构建器**：
- 可视化构建 OntologyQuery 查询
- 支持普通查询和聚合查询两种模式
- 支持关联查询（通过 LinkType）
- 支持复杂过滤条件（包括字段路径过滤）
- 实时 JSON 预览和复制功能
- 根据工作空间过滤可用的对象类型

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

**聚合函数支持**：
- `sum`：求和
- `avg`：平均值
- `count`：计数
- `min`：最小值
- `max`：最大值

**聚合指标别名**：可以为聚合结果指定别名（如示例中的"总金额"），在查询结果中使用别名显示。

### 无向关系查询

无向关系支持从任意一端查询：

**从源端查询**：
```json
{
  "object": "车辆",
  "select": ["车牌号"],
  "links": [{
    "name": "持有",
    "select": ["介质编号", "介质类型"]
  }]
}
```

**从目标端查询**（无向关系支持）：
```json
{
  "object": "通行介质",
  "select": ["介质编号"],
  "links": [{
    "name": "持有",
    "select": ["车牌号", "车辆类型"]
  }]
}
```

注意：有向关系只能从 source_type 查询到 target_type，不能反向查询。

## 项目结构

```
mypalantir/
├── ontology/              # Ontology 定义
│   ├── schema.yaml        # 业务 Schema 定义文件
│   └── schema-system.yaml # 系统 Schema 定义文件（工作空间、数据库等）
├── src/main/java/         # Java 源代码
├── web/                   # React 前端
│   ├── src/               # 源代码
│   │   ├── pages/         # 页面组件
│   │   │   ├── SchemaBrowser.tsx    # Schema 浏览器
│   │   │   ├── SchemaGraphView.tsx  # Schema 关系图
│   │   │   ├── QueryBuilder.tsx     # 查询构建器
│   │   │   ├── InstanceList.tsx     # 实例列表
│   │   │   ├── GraphView.tsx         # 实例关系图
│   │   │   └── LinkList.tsx          # 关系列表
│   │   ├── components/     # 通用组件
│   │   ├── api/           # API 客户端
│   │   └── WorkspaceContext.tsx # 工作空间上下文
│   └── dist/              # 构建产物
├── scripts/               # 工具脚本
└── data/                  # 数据目录（运行时生成）
```

## API 接口

### 查询 API

**POST** `/api/v1/query` - 执行 OntologyQuery 查询

请求体示例（OntologyQuery）：
```json
{
  "object": "车辆",
  "select": ["车牌号", "车辆类型"],
  "filter": [["=", "车辆类型", "小型客车"]],
  "links": [{"name": "持有", "select": ["介质编号"]}],
  "orderBy": [{"field": "车牌号", "direction": "ASC"}],
  "limit": 20,
  "offset": 0
}
```

**POST** `/api/v1/query/natural-language` - 执行自然语言查询

请求体示例（自然语言查询）：
```json
{
  "query": "显示2024年江苏省所有收费站的总收费金额"
}
```

### 指标 API

- **GET** `/api/v1/metrics/definitions` - 获取指标定义列表
- **POST** `/api/v1/metrics/definitions` - 创建/更新指标定义
- **POST** `/api/v1/metrics/calculate` - 计算指标
- **POST** `/api/v1/metrics/atomic-metrics` - 管理原子指标

### 数据对比 API

- **POST** `/api/v1/comparison/run` - 执行数据对比任务
- **GET** `/api/v1/comparison/history` - 获取对比历史

### Schema API

- **GET** `/api/v1/schema/object-types` - 获取所有对象类型
- **GET** `/api/v1/schema/object-types/{name}` - 获取指定对象类型
- **GET** `/api/v1/schema/link-types` - 获取所有关系类型
- **GET** `/api/v1/schema/link-types/{name}` - 获取指定关系类型
- **GET** `/api/v1/schema/data-sources` - 获取所有数据源配置
- **POST** `/api/v1/schema/data-sources/{id}/test` - 测试数据源连接

### 实例 API

- **GET** `/api/v1/instances/{objectType}` - 获取实例列表
- **GET** `/api/v1/instances/{objectType}/{id}` - 获取指定实例
- **POST** `/api/v1/instances/{objectType}` - 创建实例
- **PUT** `/api/v1/instances/{objectType}/{id}` - 更新实例
- **DELETE** `/api/v1/instances/{objectType}/{id}` - 删除实例
- **POST** `/api/v1/instances/{objectType}/batch` - 批量获取实例
- **POST** `/api/v1/instances/batch` - 批量获取多个对象类型的实例

### 关系 API

- **GET** `/api/v1/links/{linkType}` - 获取关系列表
- **POST** `/api/v1/links/{linkType}/sync` - 同步关系（基于属性映射）

### 数据库 API

- **GET** `/api/v1/databases` - 获取数据库列表
- **GET** `/api/v1/databases/{id}/tables` - 获取数据库表列表
- **GET** `/api/v1/tables/{id}/columns` - 获取表字段列表
- **POST** `/api/v1/mappings` - 创建数据映射
- **GET** `/api/v1/mappings/object-type/{objectType}` - 获取对象类型的映射

## 数据存储

系统支持多种数据存储后端：

### Neo4j 图数据库

系统支持使用 Neo4j 作为关系存储后端，提供高性能的图数据查询能力。

**配置**：
- 在 `application.properties` 中配置 Neo4j 连接信息
- 系统会自动适配 Neo4j 的数据格式

**优势**：
- 高效的图遍历查询
- 支持复杂的关系查询
- 适合大规模关系数据

### H2 数据库

默认使用 H2 作为本地测试数据库，支持内存模式和文件模式。

## 注意事项

### 工作空间

- 系统工作空间（包含 `workspace`、`database`、`table`、`column`、`mapping`）会自动隐藏管理功能
- 工作空间为空时，导航栏不显示任何对象类型和关系类型
- 查询构建器会根据工作空间过滤可用的对象类型

### 关系查询限制

- **有向关系**：只能从 source_type 查询到 target_type，不能反向查询
- **无向关系**：支持双向查询，可以从任意一端查询到另一端
- 查询构建器会自动根据关系方向过滤可用的关联类型

### 性能优化

- 图形视图默认限制节点数和关系数，可通过设置面板调整
- 工作空间模式下，限制值会自动提高
- 使用批量 API 可以减少 HTTP 请求数，提升加载性能

### 数据同步

- 同步表结构时，会自动创建 `database_has_table` 和 `table_has_column` 关系
- 同步数据时，如果定义了 `primary_key_column`，实例 ID 会使用数据库主键值
- 关系自动同步基于 `property_mappings` 规则，所有映射条件必须同时满足

## 相关文档

- [CHANGELOG.md](./CHANGELOG.md) - 详细的功能变动记录
- [CHANGELOG_SUMMARY.md](./CHANGELOG_SUMMARY.md) - 功能变动简洁总结
- [web/README.md](./web/README.md) - 前端项目说明

## 许可证

本项目为仿制项目，仅供学习和研究使用。
