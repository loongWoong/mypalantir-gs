# Agent 模块 query_data 数据库连接故障排查

## 问题现象

Agent 调用 `query_data` 查询通行记录（如「查询所有 Passage 通行记录」）时，返回错误：

```
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago. 
The driver has not received any packets from the server.
```

这是 **MySQL JDBC 连接失败** 时的典型报错。

---

## 调用链路

```
AgentTools.queryData("查询所有Passage通行记录")
  → NaturalLanguageQueryService.convertToQuery()  // LLM 转为 OntologyQuery
  → QueryService.executeQuery()
  → QueryExecutor.execute()
  → getDataSourceMappingFromMapping(Passage)
      // Mapping → table_id → Table → database_id
  → DatabaseMetadataService.getConnectionForDatabase(databaseId)
  → DriverManagerDataSource.getConnection()
      // 此处若 MySQL 不可达，则抛出 Communications link failure
```

---

## 数据库连接来源

查询执行时，实际连接由以下方式决定：

| databaseId | 连接来源 |
|------------|----------|
| null / 空 / "default" | `.env` 或 `application.properties`：`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` |
| 非空（如 `2018252224928497666`） | 工作空间 Neo4j 中的 `database` 实例：`host`, `port`, `database_name`, `username`, `password` |

Passage 的 Mapping 若指向某张表，该表的 `database_id` 决定使用哪一个数据源。

---

## 排查步骤

### 1. 确认实际使用的数据库连接

- 查看应用日志中的 `[executeSql] Using database connection for databaseId: xxx`
- 若为 `null`：使用的是 `.env` 中的 `DB_HOST`（例如 192.168.56.114:3306）
- 若为具体 ID：使用的是 Neo4j 中该 `database` 实例的 host/port

### 2. 网络连通性

```bash
# 测试主机是否可达
ping 192.168.56.114

# 测试 3306 端口是否开放
telnet 192.168.56.114 3306
# 或 Windows PowerShell
Test-NetConnection -ComputerName 192.168.56.114 -Port 3306
```

### 3. MySQL 服务与监听

- 确认 MySQL 已启动
- 确认 `bind-address` 允许外部连接（如 `0.0.0.0` 或对应网卡 IP）
- 检查防火墙是否放行 3306

### 4. Passage Mapping 配置

在 **数据映射** 页面确认：

- `Passage` 对象类型已配置 Mapping
- Mapping 指向的 Table 有 `database_id`
- 对应的 `database` 实例在 Neo4j 中存在，且 `host`, `port`, `database_name`, `username`, `password` 正确

### 5. .env 配置（使用默认库时）

确保 `.env` 中数据库配置与运行环境一致：

```env
DB_HOST=192.168.56.114
DB_PORT=3306
DB_NAME=gs314syn
DB_USER=root
DB_PASSWORD=123456
DB_TYPE=mysql
```

若应用运行在 Docker/远程主机，`DB_HOST` 应填 MySQL 在 **该环境** 下可访问的地址。

---

## 常见原因

| 原因 | 处理建议 |
|------|----------|
| MySQL 未启动 | 启动 MySQL 服务 |
| 网络不通 | 检查网络、防火墙、VPN |
| 主机/端口错误 | 核对 Mapping 中的 database 或 .env 中的 DB_* |
| MySQL 未允许远程 | 修改 `bind-address` 或授权 `'user'@'%'` |
| 密码错误 | 核对 database 实例或 .env 中的密码 |
| .env 未生效 | 确认 .env 在项目根目录，应用启动时已加载 |

---

## 快速自检

在项目根目录执行，验证 `.env` 中的 MySQL 是否可达：

```bash
# Linux/macOS
mysql -h 192.168.56.114 -P 3306 -u root -p gs314syn -e "SELECT 1"

# 或使用 PowerShell (Windows)
# 需安装 mysql 客户端
```

若能正常连接，则更可能是 Mapping 中的 database 实例配置问题；若无法连接，则优先排查网络和 MySQL 配置。
