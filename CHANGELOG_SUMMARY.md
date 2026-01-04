# 功能变动和关键改动说明（简洁版）

## 文档概述

本文档简洁总结从提交 `4989e74c521b154c12ea922f2a737438e45141a8`（车辆-卡模型）之后的所有功能变动和关键修改。

**文档生成时间：** 2026-01-04  
**起始提交：** 4989e74c521b154c12ea922f2a737438e45141a8  
**最新提交：** 44895e969b06d7e16addc5c7e9779a78aae46992

---

## 一、功能变动总览

| 提交版本 | 功能模块 | 关键改动 |
|---------|---------|---------|
| 095e730 | 数据库集成 | 支持 MySQL 数据库连接，实现表结构同步和数据映射功能 |
| c5b426f | 数据映射增强 | 添加表名支持，实现数据库与表之间的链接关系 |
| 1920799 | 实例创建优化 | 支持指定 ID 创建实例，优化名称匹配逻辑 |
| 28f547d | 关系自动同步 | 实现基于属性映射的自动关系创建功能 |
| fc743af | UI 显示优化 | 添加 display_name 支持，显示中文名称 |
| 9a6dbfb | 工作空间管理 | 实现工作空间功能，分离系统模型和业务模型 |
| 847094a | 性能优化 | 实现批量 API，优化图形视图加载性能 |
| 053c18b | 血缘查询 | 实现正向、反向、全链血缘查询功能 |
| 9234bdb | 查询引擎 | 实现基于 Calcite 的查询执行引擎 |
| 88d949e | 查询修复 | 修复查询功能，添加 id 字段到查询结果 |
| e64ee4b | 查询优化 | 修复 JOIN 条件错误，改进 SQL 生成和结果映射 |
| f3c181e | 文档更新 | 添加查询架构文档到 README |
| 98a34aa | Neo4j 适配 | 修复接入 Neo4j 后数据格式变更引起的查询展示问题 |
| 662ef92 | Schema 增强 | 增强 Schema 定义和数据源合并逻辑 |
| 23bf138 | 数据映射 | 更新 Schema 并增强数据映射功能 |
| 44895e9 | Schema 重构 | 重构 Schema 并增强数据源映射功能 |

---

## 二、核心功能模块

### 2.1 数据库集成与数据映射

**主要功能：**
- 支持外部 MySQL 数据库连接（通过 `.env` 配置）
- 自动同步数据库表结构和字段信息
- 建立数据库字段与模型属性的映射关系
- 从数据库抽取数据到模型实例

**关键文件：**
- 后端：`DatabaseService.java`、`DatabaseMetadataService.java`、`MappingService.java`、`MappedDataService.java`、`TableSyncService.java`
- 前端：`DataMappingDialog.tsx`、`DataMapping.tsx`

### 2.2 工作空间管理

**主要功能：**
- 支持对对象类型和关系类型进行分组管理
- 分离系统模型（`schema-system.yaml`）和业务模型（`schema.yaml`）
- 根据工作空间过滤显示内容

**关键文件：**
- `WorkspaceContext.tsx`、`WorkspaceDialog.tsx`、`Loader.java`

### 2.3 关系自动同步

**主要功能：**
- 基于 `property_mappings` 自动创建关系实例
- 支持多属性映射关系

**关键文件：**
- `LinkSyncService.java`、`LinkController.java`

### 2.4 血缘查询

**主要功能：**
- 正向血缘：从当前节点向后递归查询所有下游节点
- 反向血缘：从当前节点向前递归查询所有上游节点
- 全链血缘：从当前节点前后递归查询所有相关节点

**关键文件：**
- `GraphView.tsx`（`loadForwardLineage`、`loadBackwardLineage` 方法）

### 2.5 性能优化

**主要功能：**
- 批量获取实例 API（`/api/v1/instances/{objectType}/batch`、`/api/v1/instances/batch`）
- 优化图形视图加载逻辑，减少 HTTP 请求数

**性能提升：**
- HTTP 请求数：从 20-30 减少到 5-10（减少 70%）
- 加载时间：从 3-5 秒减少到 1-2 秒（减少 60%）

### 2.6 查询执行引擎

**主要功能：**
- 基于 Apache Calcite 实现查询执行引擎
- 支持 OntologyQuery DSL 查询语言
- 自动映射 Ontology 概念到数据库表名和列名
- 支持 WHERE、SELECT、ORDER BY、LIMIT 等查询功能
- 支持关系查询（JOIN）

**关键文件：**
- `QueryService.java`、`QueryExecutor.java`、`RelNodeBuilder.java`、`OntologyRelToSqlConverter.java`

### 2.7 Schema 重构和数据源映射增强

**主要功能：**
- 从 schema.yaml 中移除已弃用的数据源配置
- 增强数据源映射逻辑，优先使用映射配置
- 改进 SchemaGraphView 和 GraphView，支持按显示名称展示
- 为 SchemaBrowser 添加映射数据的加载状态与错误处理

---

## 三、技术架构变化

### 3.1 新增服务层

- `DatabaseService.java` - 数据库实例管理
- `DatabaseMetadataService.java` - 数据库元数据查询
- `MappingService.java` - 映射关系管理
- `MappedDataService.java` - 基于映射的数据抽取
- `TableSyncService.java` - 表结构同步
- `LinkSyncService.java` - 关系自动同步

### 3.2 新增控制器层

- `DatabaseController.java` - 数据库相关 API
- `MappingController.java` - 映射相关 API

### 3.3 新增前端组件

- `DataMappingDialog.tsx` - 数据映射对话框
- `WorkspaceDialog.tsx` - 工作空间对话框
- `WorkspaceContext.tsx` - 工作空间上下文

### 3.4 数据模型扩展

**系统对象类型：** `workspace`、`database`、`table`、`column`、`mapping`  
**系统关系类型：** `database_has_table`、`table_has_column`、`mapping_links_table`

---

## 四、API 接口变更

### 4.1 新增接口

**数据库相关：**
- `GET /api/v1/database/default-id`
- `GET /api/v1/database/tables`
- `GET /api/v1/database/columns`
- `POST /api/v1/database/sync-tables`

**映射相关：**
- `POST /api/v1/mappings`
- `GET /api/v1/mappings/{id}`
- `PUT /api/v1/mappings/{id}`
- `DELETE /api/v1/mappings/{id}`

**实例相关：**
- `POST /api/v1/instances/{objectType}/batch`
- `POST /api/v1/instances/batch`
- `POST /api/v1/instances/{objectType}/sync-from-mapping/{mappingId}`

**关系相关：**
- `POST /api/v1/links/{linkType}/sync`

---

## 五、配置文件变更

### 5.1 新增依赖（pom.xml）

- `mysql-connector-j` (8.0.33) - MySQL JDBC 驱动
- `dotenv-java` (3.0.0) - 环境变量管理
- `jackson-datatype-jsr310` - Java 8 时间支持

### 5.2 新增配置（application.properties）

```properties
schema.system.file.path=./ontology/schema-system.yaml
db.host=${DB_HOST:localhost}
db.port=${DB_PORT:3306}
db.name=${DB_NAME:}
db.user=${DB_USER:}
db.password=${DB_PASSWORD:}
```

### 5.3 新增文件

- `.env` - 数据库连接配置
- `ontology/schema-system.yaml` - 系统模型定义

---

## 六、统计数据

- **总提交数：** 19 个功能提交
- **新增文件：** 20+ 个
- **代码变更：** 约 12000+ 行新增代码
- **新增 API 接口：** 25+ 个
- **新增前端组件：** 6+ 个

---

## 七、使用要点

1. **数据库集成**：配置 `.env` 文件 → 创建数据源 → 同步表结构 → 建立映射 → 抽取数据
2. **工作空间**：创建工作空间 → 选择对象类型和关系类型 → 切换工作空间过滤内容
3. **关系同步**：在 `schema.yaml` 定义 `property_mappings` → 在 Links 页面点击"同步关系"
4. **血缘查询**：在 Instance Graph 界面选择查询模式（直接/正向/反向/全链）

---

## 八、注意事项

- `.env` 文件包含敏感信息，不应提交到版本控制系统
- 系统工作空间会自动隐藏"关联数据源"和"同步抽取"按钮
- 图形视图默认限制节点数和关系数，可通过设置面板调整
- 同步数据时，如果定义了 `primary_key_column`，实例 ID 会使用数据库主键值

---

**文档维护者：** 开发团队  
**最后更新：** 2026-01-04
