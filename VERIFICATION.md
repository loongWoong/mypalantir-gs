# 功能验证指南

本文档说明如何验证项目是否满足需求。

## 需求回顾

1. ✅ **元模型定义**：系统内置 ObjectType、Property、LinkType 的元模型结构
2. ✅ **用户模型定义**：通过 YAML DSL 定义业务对象类型、属性和关系类型
3. ✅ **文件系统存储**：模型定义和实例数据都存储在文件系统中
4. ✅ **API 接口**：提供完整的 RESTful API 用于模型查询和实例数据管理

## 验证步骤

### 1. 启动服务器

```bash
go run cmd/server/main.go
```

服务器将在 `http://localhost:8080` 启动。

### 2. 运行自动化测试脚本

```bash
./test_api.sh
```

这个脚本会测试所有核心功能：
- Schema 查询 API
- 实例 CRUD API
- 关系 CRUD API
- 数据验证功能

### 3. 手动验证步骤

#### 3.1 验证 DSL 模型定义

检查 `ontology/schema.yaml` 文件，确认：
- ✅ 定义了 ObjectType（Person, Company）
- ✅ 每个 ObjectType 有 Properties（name, age, email 等）
- ✅ 定义了 LinkType（worksAt, knows）
- ✅ LinkType 有 source_type 和 target_type

#### 3.2 验证 Schema 查询 API

```bash
# 查询所有对象类型
curl http://localhost:8080/api/v1/schema/object-types

# 查询 Person 对象类型详情
curl http://localhost:8080/api/v1/schema/object-types/Person

# 查询 Person 的所有属性
curl http://localhost:8080/api/v1/schema/object-types/Person/properties

# 查询 Person 的出边关系
curl http://localhost:8080/api/v1/schema/object-types/Person/outgoing-links
```

**预期结果**：
- 返回 JSON 格式的对象类型和属性定义
- 包含完整的元数据信息

#### 3.3 验证实例数据管理

```bash
# 创建 Person 实例
curl -X POST http://localhost:8080/api/v1/instances/Person \
  -H "Content-Type: application/json" \
  -d '{
    "name": "张三",
    "age": 30,
    "email": "zhangsan@example.com",
    "metadata": {"department": "Engineering"}
  }'

# 查询所有 Person 实例
curl http://localhost:8080/api/v1/instances/Person

# 获取特定实例（替换 {id} 为实际 ID）
curl http://localhost:8080/api/v1/instances/Person/{id}

# 更新实例
curl -X PUT http://localhost:8080/api/v1/instances/Person/{id} \
  -H "Content-Type: application/json" \
  -d '{"age": 31}'

# 删除实例
curl -X DELETE http://localhost:8080/api/v1/instances/Person/{id}
```

**预期结果**：
- 成功创建实例，返回 ID
- 实例数据存储在 `data/{namespace}/{object_type}/{id}.json`
- 可以查询、更新、删除实例

#### 3.4 验证关系数据管理

```bash
# 创建关系（需要先有 Person 和 Company 实例）
curl -X POST http://localhost:8080/api/v1/links/worksAt \
  -H "Content-Type: application/json" \
  -d '{
    "source_id": "{person_id}",
    "target_id": "{company_id}",
    "properties": {
      "start_date": "2020-01-01",
      "position": "Software Engineer"
    }
  }'

# 查询所有关系
curl http://localhost:8080/api/v1/links/worksAt

# 查询实例的关系
curl http://localhost:8080/api/v1/instances/Person/{id}/links/worksAt

# 查询关联的实例
curl http://localhost:8080/api/v1/instances/Person/{id}/connected/worksAt?direction=outgoing
```

**预期结果**：
- 成功创建关系，返回 ID
- 关系数据存储在 `data/{namespace}/links/{link_type}/{id}.json`
- 可以查询关系，并通过关系查询关联的实例

#### 3.5 验证数据验证功能

```bash
# 测试必填字段验证
curl -X POST http://localhost:8080/api/v1/instances/Person \
  -H "Content-Type: application/json" \
  -d '{"age": 30}'
# 预期：返回 400 错误，提示缺少必填字段

# 测试数据类型验证
curl -X POST http://localhost:8080/api/v1/instances/Person \
  -H "Content-Type: application/json" \
  -d '{"name": "测试", "age": "not_a_number", "email": "test@example.com"}'
# 预期：返回 400 错误，提示数据类型错误

# 测试约束验证（age 超出范围）
curl -X POST http://localhost:8080/api/v1/instances/Person \
  -H "Content-Type: application/json" \
  -d '{"name": "测试", "age": 200, "email": "test@example.com"}'
# 预期：返回 400 错误，提示约束验证失败

# 测试邮箱格式验证
curl -X POST http://localhost:8080/api/v1/instances/Person \
  -H "Content-Type: application/json" \
  -d '{"name": "测试", "age": 30, "email": "invalid-email"}'
# 预期：返回 400 错误，提示邮箱格式不正确
```

**预期结果**：
- 所有验证错误都返回 400 状态码
- 错误信息清晰，指出具体问题

### 4. 验证文件系统存储

检查数据目录结构：

```bash
# 查看数据目录
ls -la data/

# 查看实例文件
ls -la data/com_example_ontology/person/
cat data/com_example_ontology/person/*.json

# 查看关系文件
ls -la data/com_example_ontology/links/works_at/
cat data/com_example_ontology/links/works_at/*.json
```

**预期结果**：
- 数据目录按 namespace 和 object_type 组织
- 每个实例是一个独立的 JSON 文件
- 关系数据存储在 links 目录下

## 功能检查清单

### 核心功能

- [x] **DSL 解析**：能够解析 YAML 格式的模型定义
- [x] **模型验证**：验证模型定义的语法、语义和约束
- [x] **Schema 查询**：通过 API 查询模型定义
- [x] **实例创建**：基于模型定义创建实例数据
- [x] **实例查询**：查询实例列表和详情
- [x] **实例更新**：更新实例数据
- [x] **实例删除**：删除实例数据
- [x] **关系创建**：创建对象之间的关系
- [x] **关系查询**：查询关系和关联实例
- [x] **数据验证**：验证数据类型、必填字段、约束条件

### 存储功能

- [x] **文件系统存储**：所有数据存储在文件系统中
- [x] **目录自动创建**：按需创建数据目录
- [x] **JSON 格式**：实例数据以 JSON 格式存储
- [x] **路径管理**：合理的文件路径组织

### API 功能

- [x] **RESTful API**：符合 REST 规范的 API 设计
- [x] **统一响应格式**：所有 API 返回统一的 JSON 格式
- [x] **错误处理**：友好的错误信息和状态码
- [x] **CORS 支持**：支持跨域请求

## 性能考虑

当前实现适合中小规模数据。如果需要处理大量数据，可以考虑：

1. **索引文件**：为常用查询字段创建索引
2. **缓存机制**：缓存 Schema 定义和常用查询结果
3. **批量操作**：支持批量创建和更新

## 总结

项目已实现所有核心需求：

✅ **元模型管理**：通过代码定义，支持 ObjectType、Property、LinkType  
✅ **用户模型定义**：通过 YAML DSL 定义业务模型  
✅ **文件系统存储**：模型定义和实例数据都存储在文件系统中  
✅ **完整 API**：提供 RESTful API 用于所有操作  
✅ **数据验证**：完整的数据类型和约束验证  

可以开始实现前端 UI 界面了！

