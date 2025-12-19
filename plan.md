# Palantir Foundry Ontology 仿制项目 - 实现计划

## 项目目标

构建一个数据模型管理平台，实现类似 Palantir Foundry Ontology 的核心功能：
- 元模型层：定义 ObjectType、Property、LinkType 的元模型结构
- 用户模型层：通过 YAML DSL 定义业务对象类型、属性和关系类型
- 实例数据层：基于定义的模型存储和管理实例数据（文件系统）
- API 层：提供完整的 RESTful API 用于模型查询和实例数据管理

## 核心概念

### 元模型（Meta-model）
系统内置的模型定义结构，用于描述如何定义用户模型。

### 用户模型（User-defined Models）
用户通过 YAML DSL 文件定义的业务模型，系统启动时加载并解析。

### 实例数据（Instance Data）
基于定义的模型创建的实例数据，存储在文件系统中（JSON 格式）。

### DSL（Domain Specific Language）
使用 YAML 格式定义模型，存储在文件系统中，便于版本控制和代码审查。

## 技术栈

- **语言**: Go 1.21+
- **Web 框架**: Gin
- **存储**: 文件系统（YAML + JSON）
- **YAML 解析**: gopkg.in/yaml.v3
- **JSON 处理**: encoding/json
- **API 文档**: Swagger/OpenAPI（可选）

## 已实现功能

### ✅ Phase 1: 项目初始化
- [x] 项目结构创建
- [x] 配置文件管理
- [x] 依赖管理

### ✅ Phase 2: DSL 解析器
- [x] YAML 解析器
- [x] 模型验证器（语法、语义、约束）
- [x] Schema 加载器
- [x] 内存模型管理

### ✅ Phase 3: 文件系统存储
- [x] 路径管理器
- [x] 实例数据存储（CRUD）
- [x] 关系数据存储（CRUD）
- [x] 文件并发安全（文件锁）

### ✅ Phase 4: 业务逻辑层
- [x] Schema 服务
- [x] 实例服务
- [x] 关系服务
- [x] 数据验证服务

### ✅ Phase 5: API 层
- [x] Schema 查询 API
- [x] 实例 CRUD API
- [x] 关系 CRUD API
- [x] 统一响应格式

### ✅ Phase 6: 中间件
- [x] CORS 中间件
- [x] 日志中间件
- [x] 错误恢复中间件

### ✅ Phase 7: 主程序
- [x] 应用入口
- [x] 路由配置
- [x] 服务初始化

## API 端点

### Schema 查询 API（只读）
```
GET    /api/v1/schema/object-types          # 列出所有对象类型
GET    /api/v1/schema/object-types/:name     # 获取对象类型详情
GET    /api/v1/schema/object-types/:name/properties  # 获取对象类型的所有属性
GET    /api/v1/schema/link-types             # 列出所有关系类型
GET    /api/v1/schema/link-types/:name        # 获取关系类型详情
GET    /api/v1/schema/object-types/:name/outgoing-links  # 获取出边关系
GET    /api/v1/schema/object-types/:name/incoming-links   # 获取入边关系
```

### Instance CRUD API
```
POST   /api/v1/instances/:object_type       # 创建实例
GET    /api/v1/instances/:object_type       # 列表查询（支持分页、过滤）
GET    /api/v1/instances/:object_type/:id   # 获取实例详情
PUT    /api/v1/instances/:object_type/:id   # 更新实例
DELETE /api/v1/instances/:object_type/:id   # 删除实例
```

### Link CRUD API
```
POST   /api/v1/links/:link_type             # 创建关系
GET    /api/v1/links/:link_type              # 列表查询
GET    /api/v1/links/:link_type/:id          # 获取关系详情
PUT    /api/v1/links/:link_type/:id          # 更新关系属性
DELETE /api/v1/links/:link_type/:id          # 删除关系
GET    /api/v1/instances/:object_type/:id/links/:link_type  # 查询实例的关系
GET    /api/v1/instances/:object_type/:id/connected/:link_type  # 查询关联的实例
```

## 使用说明

### 1. 配置环境变量

创建 `.env` 文件（参考 `.env.example`）：
```bash
SERVER_PORT=8080
DSL_FILE_PATH=./ontology/schema.yaml
DATA_ROOT_PATH=./data
```

### 2. 定义模型

在 `ontology/schema.yaml` 中定义你的模型（参考示例文件）。

### 3. 运行服务

```bash
go run cmd/server/main.go
```

### 4. 测试 API

```bash
# 查询对象类型
curl http://localhost:8080/api/v1/schema/object-types

# 创建实例
curl -X POST http://localhost:8080/api/v1/instances/Person \
  -H "Content-Type: application/json" \
  -d '{"name": "张三", "age": 30, "email": "zhangsan@example.com"}'

# 查询实例
curl http://localhost:8080/api/v1/instances/Person
```

## 后续优化方向

1. **性能优化**：
   - 索引文件支持（快速查询）
   - 缓存机制
   - 批量操作

2. **功能增强**：
   - 高级搜索（全文搜索）
   - 数据导入导出
   - Schema 版本管理
   - 热重载（开发环境）

3. **用户体验**：
   - API 文档（Swagger）
   - 错误信息优化
   - 数据验证提示优化

4. **扩展功能**：
   - 权限控制
   - 多租户支持
   - 审计日志
   - 图查询支持

