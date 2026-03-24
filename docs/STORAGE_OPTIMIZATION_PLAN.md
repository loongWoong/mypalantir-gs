# 混合存储优化修改计划

> **实施状态**：已完成（2025-03）

## 目标

将混合存储实现扩展为可配置多后端：
- **关系型存储**：MySQL（现有）→ 可选 **Doris**
- **图存储**：Neo4j（现有）→ 可选 **FalkorDB**

---

## 一、现状架构分析

### 1.1 关系型存储（Relational Storage）

| 组件 | 职责 | 当前实现 |
|------|------|----------|
| `RelationalInstanceStorage` | 从同步表查询实例 | 通过 `DatabaseMetadataService.getConnectionForDatabase(null)` 获取默认库连接 |
| `DatabaseMetadataService` | 管理动态数据源、构建 JDBC URL | 硬编码 `jdbc:mysql://...` |
| `DatabaseConfig` | 默认库连接 | 仅支持 MySQL URL |
| `DataSourceConfig` | 映射库的 JDBC URL | 支持 mysql/postgresql，无 Doris |
| `Config` | db.type | 已有 `db.type`，默认 mysql |
| `SqlDialectAdapter` | SQL 方言转换 | 支持 MYSQL，无 DORIS |

### 1.2 图存储（Graph Storage）

| 组件 | 职责 | 当前实现 |
|------|------|----------|
| `Neo4jInstanceStorage` | 实例的图节点 CRUD | 直接依赖 `org.neo4j.driver.Driver`，执行 Cypher |
| `Neo4jLinkStorage` | 关系的图边 CRUD | 同上 |
| `Neo4jConfig` | 创建 Neo4j Driver | 单一 Neo4j Driver Bean |
| `StorageFactory` | 根据 storage.type 选择存储 | 依赖 `Driver neo4jDriver`，hybrid/neo4j 模式强制 Neo4j |
| `HybridInstanceStorage` | 组合关系型 + 图 | 硬依赖 `Neo4jInstanceStorage`、`RelationalInstanceStorage` |
| `MappedDataService` | 同步表 → 图 | 硬依赖 `Neo4jInstanceStorage.syncSyncTableToNeo4j()` |
| `LinkSyncService` | 关系同步到图 | 通过 `ILinkStorage` 注入，实际为 Neo4jLinkStorage |
| `DatabaseMetadataService` | 获取 database 实例 | 直接依赖 `Neo4jInstanceStorage.getInstance("database", ...)` |

**技术差异**：
- **Neo4j**：Bolt 协议，`org.neo4j.driver`，Cypher
- **FalkorDB**：Redis 协议，`io.falkordb:falkordb` (JFalkorDB)，OpenCypher，API 不同

---

## 二、修改方案概览

### 2.1 关系型存储：MySQL / Doris 可选

- Doris 使用 **MySQL 协议**，可用 MySQL JDBC 驱动连接（Doris FE 查询端口默认 9030）
- 主要改动：扩展 `db.type`、统一 JDBC URL 构建、SQL 方言适配

### 2.2 图存储：Neo4j / FalkorDB 可选

- FalkorDB 使用 **OpenCypher**，Cypher 语法与 Neo4j 高度兼容
- 驱动不同：需抽象图存储接口，实现 Neo4j / FalkorDB 两套实现

---

## 三、详细修改计划

### 阶段一：关系型存储 - Doris 支持

#### 3.1.1 配置扩展

**文件**：`application.properties`、`Config.java`、`.env.example`

- 已有 `db.type`，扩展取值：`mysql` | `doris`
- Doris 默认端口 9030，可新增 `db.doris.query-port`（可选）
- 保持不变：`db.host`、`db.name`、`db.user`、`db.password`

```properties
# 新增
db.type=${DB_TYPE:mysql}
# Doris 使用 MySQL 协议，端口通常为 9030
# 当 db.type=doris 时，db.port 建议设为 9030
```

#### 3.1.2 JDBC URL 构建

**涉及文件**：
- `DatabaseConfig.java`：`getConnection()`、`getJdbcUrl()` 按 `db.type` 选择 URL 模板
- `DatabaseMetadataService.java`：`createDataSource()` 中默认库和映射库的 URL 构建
- `DataSourceConfig.java`：`buildJdbcUrl()` 增加 `doris` 分支
- `DataSourceTestService.java`：增加 Doris 连接测试

**Doris JDBC URL 格式**：
```
jdbc:mysql://host:9030/database?useSSL=false&...
```
与 MySQL 相同，仅端口和部分参数可能不同。

#### 3.1.3 Spring DataSource 配置

**文件**：`application.properties`

- 根据 `db.type` 动态选择 `spring.datasource.url` 和 `driver-class-name`
- 或用 `@Configuration` 编程式创建 DataSource，按 `db.type` 选择

#### 3.1.4 SQL 方言适配

**文件**：`SqlDialectAdapter.java`、`OntologyRelToSqlConverter.java`

- `DatabaseType` 增加 `DORIS`
- Doris 与 MySQL 高度兼容，可复用 MySQL 适配逻辑，必要时单独处理差异

#### 3.1.5 ontology-database 映射

**文件**：`ontology/*.yaml`、`DataSourceConfig` 等

- 映射中的 `type` 支持 `doris`
- `OntologySchemaFactory`、`DatabaseMetadataService` 中加载驱动时支持 `doris`

---

### 阶段二：图存储 - FalkorDB 支持

#### 3.2.1 抽象图存储接口

**新建**：`IGraphStorage` 或拆分为 `IGraphInstanceStorage`、`IGraphLinkStorage`

目标：对上层屏蔽 Neo4j / FalkorDB 差异。

**方案 A：接口适配器（推荐）**

- `IInstanceStorage`、`ILinkStorage` 保持不变
- 新增 `FalkorDBInstanceStorage implements IInstanceStorage`
- 新增 `FalkorDBLinkStorage implements ILinkStorage`
- 通过配置选择注入 Neo4j 或 FalkorDB 实现

**方案 B：图驱动抽象**

- 定义 `IGraphDriver`：`runCypher(String cypher, Map params)`、`session()` 等
- `Neo4jGraphDriver` 封装 Neo4j Driver
- `FalkorDBGraphDriver` 封装 JFalkorDB
- `Neo4jInstanceStorage`、`FalkorDBInstanceStorage` 共用一套 Cypher 逻辑，仅切换 Driver

建议：先采用 **方案 A**，实现简单；若 Cypher 逻辑重复较多，再抽 `IGraphDriver`。

#### 3.2.2 FalkorDB 驱动与依赖

**文件**：`pom.xml`

```xml
<!-- FalkorDB Java Client (可选，与 Neo4j 二选一) -->
<dependency>
    <groupId>io.falkordb</groupId>
    <artifactId>falkordb</artifactId>
    <version>0.7.0</version>
    <optional>true</optional>
</dependency>
```

或使用 `io.falkordb:jfalkordb`（以 Maven 中央仓库为准）。

#### 3.2.3 配置扩展

**文件**：`Config.java`、`application.properties`、`.env.example`

```properties
# 图数据库类型：neo4j | falkordb
storage.graph.type=${STORAGE_GRAPH_TYPE:neo4j}

# Neo4j（storage.graph.type=neo4j 时使用）
neo4j.uri=${NEO4J_URI:bolt://localhost:7687}
neo4j.user=${NEO4J_USER:neo4j}
neo4j.password=${NEO4J_PASSWORD:}

# FalkorDB（storage.graph.type=falkordb 时使用）
# FalkorDB 使用 Redis 协议，默认 6379
falkordb.host=${FALKORDB_HOST:localhost}
falkordb.port=${FALKORDB_PORT:6379}
falkordb.password=${FALKORDB_PASSWORD:}
```

#### 3.2.4 FalkorDB 实现类

**新建**：`FalkorDBInstanceStorage.java`、`FalkorDBLinkStorage.java`

- 实现 `IInstanceStorage`、`ILinkStorage`
- 使用 JFalkorDB 执行 Cypher（`GRAPH.QUERY` 等）
- 将 Neo4j 的 `Session.run(cypher, params)` 转为 FalkorDB 的查询调用
- 注意：FalkorDB 的参数化查询、结果结构可能与 Neo4j 略有差异，需做适配

#### 3.2.5 图存储配置与 Bean

**新建**：`FalkorDBConfig.java`

- 根据 `storage.graph.type` 创建 FalkorDB 客户端 Bean
- 仅当 `storage.graph.type=falkordb` 时创建，避免与 Neo4j 冲突

**修改**：`StorageFactory.java`

- 新增 `storage.graph.type` 判断
- `instanceStorage()`、`linkStorage()` 根据配置注入 `Neo4j*` 或 `FalkorDB*`
- `@DependsOn` 改为依赖“当前启用的图驱动 Bean”，而非固定的 `neo4jDriver`

#### 3.2.6 HybridInstanceStorage 解耦

**修改**：`HybridInstanceStorage.java`

- 将 `Neo4jInstanceStorage` 改为 `IInstanceStorage graphInstanceStorage`（或专用接口）
- 或通过 `@Qualifier` 注入当前启用的图实例存储
- 确保 hybrid 模式下使用的图存储与 `linkStorage` 一致

#### 3.2.7 系统对象类型的图存储

**修改**：`DatabaseMetadataService.java`、`MappedDataService.java`、`LinkSyncService.java`

- `DatabaseMetadataService` 中获取 `database` 实例：改为注入 `IInstanceStorage`（或 graph 专用接口），不再直接依赖 `Neo4jInstanceStorage`
- `MappedDataService.syncSyncTableToNeo4j()`：重命名为 `syncSyncTableToGraph()`，改为调用当前图存储的批量写入接口
- `LinkSyncService`：已通过 `ILinkStorage` 抽象，仅需保证 Bean 选择正确

---

### 阶段三：配置与文档

#### 3.3.1 配置项汇总

| 配置项 | 说明 | 可选值 | 默认 |
|--------|------|--------|------|
| `storage.type` | 存储模式 | file / neo4j / hybrid | file |
| `storage.graph.type` | 图数据库类型 | neo4j / falkordb | neo4j |
| `db.type` | 关系型数据库类型 | mysql / doris | mysql |
| `db.host` | 关系型 DB 主机 | - | localhost |
| `db.port` | 端口（MySQL 3306，Doris 9030） | - | 3306 |
| `neo4j.*` | Neo4j 连接（graph.type=neo4j） | - | - |
| `falkordb.*` | FalkorDB 连接（graph.type=falkordb） | - | - |

#### 3.3.2 有效组合

| storage.type | storage.graph.type | db.type | 说明 |
|--------------|--------------------|---------|------|
| file | - | - | 纯文件存储 |
| neo4j | neo4j | - | 纯 Neo4j |
| neo4j | falkordb | - | 纯 FalkorDB |
| hybrid | neo4j | mysql | 当前默认：MySQL + Neo4j |
| hybrid | neo4j | doris | Doris + Neo4j |
| hybrid | falkordb | mysql | MySQL + FalkorDB |
| hybrid | falkordb | doris | Doris + FalkorDB |

#### 3.3.3 文档更新

- `README.md`：补充 Doris、FalkorDB 配置说明
- `docs/TROUBLESHOOTING_*.md`：增加 Doris、FalkorDB 常见问题
- `.env.example`：增加 `DB_TYPE`、`STORAGE_GRAPH_TYPE`、`FALKORDB_*` 示例

---

## 四、实施顺序建议

1. **阶段一**：Doris 支持（改动集中、风险低）
   - 3.1.1 → 3.1.2 → 3.1.3 → 3.1.4 → 3.1.5

2. **阶段二**：FalkorDB 支持（改动范围大）
   - 3.2.1 → 3.2.2 → 3.2.4 → 3.2.3 → 3.2.5 → 3.2.6 → 3.2.7

3. **阶段三**：配置与文档
   - 与阶段一、二并行更新

---

## 五、风险与注意事项

### 5.1 Doris

- **SQL 兼容性**：Doris 与 MySQL 基本兼容，但部分函数、语法可能不同，需在集成测试中验证
- **驱动**：优先使用 MySQL Connector/J；若需 Doris 特有特性，可考虑 Doris 官方 JDBC 驱动

### 5.2 FalkorDB

- **API 差异**：JFalkorDB 的 API 与 Neo4j Driver 不同，需逐接口适配
- **Cypher 兼容性**：FalkorDB 支持 OpenCypher 子集，复杂查询需验证
- **可选依赖**：建议将 `falkordb` 设为 `optional`，仅在使用时加载

### 5.3 向后兼容

- 默认配置保持：`storage.type=hybrid`、`storage.graph.type=neo4j`、`db.type=mysql`
- 现有部署无需修改配置即可继续使用

---

## 六、工作量估算

| 阶段 | 预计工时 | 说明 |
|------|----------|------|
| 阶段一 Doris | 1–2 人天 | 配置 + URL + 方言，逻辑简单 |
| 阶段二 FalkorDB | 3–5 人天 | 新实现类 + 配置 + 解耦，需充分测试 |
| 阶段三 文档 | 0.5 人天 | 配置说明与故障排查 |

---

## 七、参考资料

- [Apache Doris 官方文档 - JDBC](https://doris.apache.org/docs/dev/ecosystem/jdbc)
- [FalkorDB 文档 - Cypher](https://docs.falkordb.com/cypher/)
- [JFalkorDB GitHub](https://github.com/FalkorDB/JFalkorDB)
- [Neo4j to FalkorDB 迁移指南](https://www.falkordb.com/blog/neo4j-to-falkordb-migration-guide/)
