# MyPalantir 架构总览与模块边界

## 1. 目标与范围

本文聚焦“代码中已经实现的能力”，用架构视角解释：
- 系统有哪些子项目与核心模块，它们如何协作
- 关键运行时依赖与配置入口
- 影响架构演进的核心约束与风险点

对应代码与配置入口：
- 后端入口：`src/main/java/com/mypalantir/MyPalantirApplication.java`
- 后端配置：`src/main/resources/application.properties` + 根目录 `.env`（由 `EnvConfig` 注入）
- 前端入口：`web/src/main.tsx`、`web/src/App.tsx`
- Ontology/YAML：`ontology/`
- DBT：`DBT/`
- 脚本：`scripts/`

## 2. 仓库结构（按职责分层）

### 2.1 后端（Spring Boot）

主要包路径：`src/main/java/com/mypalantir/`
- `controller/`：HTTP API 层（`/api/v1/*`），做请求转发、参数校验、响应封装
- `service/`：业务服务层（Schema、Query、Instance、Link、Mapping、Metric、NLQ、DataComparison 等）
- `meta/`：Ontology 元数据加载、合并、校验（Loader/Parser/Validator）
- `query/`：查询引擎（OntologyQuery → RelNode → SQL → JDBC）
- `repository/`：实例/关系存储抽象（文件存储或 Neo4j），并提供工厂按配置切换
- `config/`：CORS、环境变量注入、外部连接配置等

### 2.2 前端（React + Vite + TypeScript）

主要目录：`web/src/`
- `pages/`：按路由组织的功能页面（Schema、Instances、Graph、Query、Metrics、NLQ、DataComparison 等）
- `components/`：通用组件与布局
- `api/`：axios 客户端与后端 API 封装（默认 baseURL `/api/v1`）

### 2.3 Ontology / 模型

目录：`ontology/`
- 以 YAML 描述 ObjectType、LinkType、DataSource 等
- 后端启动时加载并校验，生成内存中的 Schema 供查询、校验、UI 展示使用

### 2.4 数据建模与脚本

- `DBT/`：dbt 模型与中间层加工（与在线查询引擎解耦）
- `scripts/`：数据库初始化、demo 数据生成/导入（Python/SQL）

## 3. 运行时架构（组件视图）

```plantuml
@startuml
skinparam componentStyle rectangle
skinparam shadowing false

actor "用户" as User
component "Web UI\n(React + Vite)" as Web
component "API\n(Spring Boot)\n/api/v1/*" as Api
component "Schema Loader\n(meta/*)" as Meta
component "Query Engine\n(query/*)" as Qe
component "Instance/Link Storage\n(repository/*)" as Repo
database "RDBMS\n(MySQL/H2/...)\nJDBC" as Rdbms
database "Neo4j\n(可选)" as Neo4j
cloud "LLM API\n(OpenAI-compatible)\nDeepSeek 等" as Llm
folder "Ontology YAML\nontology/*.yaml" as Yaml
folder "Local Files\n(dataRoot)" as Fs

User --> Web : 浏览器访问
Web --> Api : HTTP(JSON)\n/api/v1

Api --> Meta : 读 Schema/类型/校验
Meta --> Yaml : 读取/合并/校验

Api --> Qe : OntologyQuery 执行
Qe --> Rdbms : JDBC 查询

Api --> Repo : 实例/关系 CRUD\n(部分能力)
Repo --> Fs : 文件存储模式
Repo --> Neo4j : Neo4j 存储模式

Api --> Llm : NLQ: prompt + user query\nHTTPS
@enduml
```

## 4. 模块边界与依赖方向（约束）

建议将以下依赖方向视为“稳定约束”，便于后续演进与测试：
- `controller -> service`：Controller 不直接触达 repository/DB/LLM
- `service -> (meta/query/repository)`：Service 组合多个能力完成业务用例
- `query -> service(DatabaseMetadataService/MappingService)`：查询执行依赖数据库连接与映射元数据
- `meta`：只负责模型加载/校验/只读访问，不承载业务规则

当前代码存在的“边界侵入”现象（会影响可维护性）：
- 多处 `System.out.println` 与 `e.printStackTrace()` 出现在 service/controller/query 中，导致日志体系割裂，且可能泄露 SQL/敏感数据
- `QueryExecutor` 同时承担“计划构建、SQL 生成、连接选择、结果映射”等职责，单类过重

## 5. 关键决策点（决策树）

### 5.1 存储后端选择（File vs Neo4j）

```plantuml
@startuml
start
:读取配置 storage.type;
if (storage.type == "neo4j"?) then (yes)
  :检查 Neo4j Driver 是否可用;
  if (Driver 可用?) then (yes)
    :使用 Neo4jInstanceStorage/Neo4jLinkStorage;
  else (no)
    :启动失败\n抛 IllegalStateException;
  endif
else (no)
  :使用 InstanceStorage/LinkStorage(文件存储);
endif
stop
@enduml
```

### 5.2 NLQ（自然语言查询）能力启用条件

```plantuml
@startuml
start
:收到自然语言问题;
if (LLM_API_KEY 已配置?) then (yes)
  :生成 Ontology 摘要;
  :构建 system/user prompt;
  :调用 LLM chat/completions;
  if (返回可解析 JSON?) then (yes)
    :解析为 OntologyQuery;
    :校验 object/link 是否存在;
    if (校验通过?) then (yes)
      :进入标准 QueryEngine 执行;
    else (no)
      :返回 NLQ 校验错误;
    endif
  else (no)
    :返回解析失败（含响应内容）;
  endif
else (no)
  :返回 LLM 未配置错误;
endif
stop
@enduml
```

## 6. 架构级问题与方向（摘录）

- 无统一鉴权与权限体系：API 默认“内部/演示”可用，CORS 全放开，生产风险极高
- 多数据源/多存储并存但能力不对齐：SQL 查询链路与 Neo4j 存储链路并未形成统一的查询抽象
- 查询链路缺少限流、分页兜底与流式输出：大结果集易造成内存压力
- 映射（Mapping）为强依赖：未配置 mapping 的对象类型将直接无法查询（对迭代期不友好）

后续详细展开见：
- `docs/ANALYSIS_02_SCHEMA_PIPELINE.md`
- `docs/ANALYSIS_03_QUERY_ENGINE.md`
- `docs/ANALYSIS_04_NLQ_LLM.md`
- `docs/ANALYSIS_05_FRONTEND_FLOWS.md`
- `docs/ANALYSIS_06_RISKS_OPTIMIZATION.md`

