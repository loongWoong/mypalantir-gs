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
│  │  (React)     │  │  (Spring)     │  │  (JSON)      │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
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
│  │  JDBC        │  │  File System  │  │  (Future)    │        │
│  │  (Database)  │  │  (JSON)      │  │  API/Stream  │        │
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
│   └── schema.yaml        # Schema 定义文件
├── src/main/java/         # Java 源代码
├── web/                   # React 前端
│   ├── src/               # 源代码
│   └── dist/              # 构建产物
├── scripts/               # 工具脚本
└── data/                  # 数据目录（运行时生成）
```

## 许可证

本项目为仿制项目，仅供学习和研究使用。
