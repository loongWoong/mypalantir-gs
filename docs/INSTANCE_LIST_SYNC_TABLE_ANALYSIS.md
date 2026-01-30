# 实例列表同步表查询问题分析

## 问题描述

用户期望实例列表（Instances）应该展示同步表数据，根据默认连接数据源和模型名进行查询。但当前显示的数据不正确。

## 当前数据流分析

### 1. API 入口
- **路径**: `GET /api/v1/instances/{objectType}`
- **控制器**: `InstanceController.listInstances()`
- **服务**: `InstanceService.listInstances()`

### 2. 数据源路由逻辑

#### InstanceService.listInstances()
```java
// 检查是否有数据源映射
if (objectTypeDef.getDataSource() != null && objectTypeDef.getDataSource().isConfigured()) {
    // 使用查询 API 从数据库获取数据
    return listInstancesFromDataSource(objectType, offset, limit, filters);
} else {
    // 使用文件系统存储
    return listInstancesFromFileSystem(objectType, offset, limit, filters);
}
```

#### HybridInstanceStorage.listInstances()
```java
// 优先从关系型数据库查询
if (hasRelationalMapping(objectType)) {
    try {
        return relationalStorage.listInstances(objectType, offset, limit);
    } catch (IOException e) {
        // 回退到Neo4j
    }
}
// 回退到Neo4j
return neo4jStorage.listInstances(objectType, offset, limit);
```

#### RelationalInstanceStorage.listInstances()
```java
// 构建查询 Map
Map<String, Object> queryMap = new HashMap<>();
queryMap.put("from", objectType);
// ... 其他查询参数

// 执行查询
QueryExecutor.QueryResult result = queryService.executeQuery(queryMap);
```

### 3. QueryService 查询逻辑

`QueryService.executeQuery()` → `QueryExecutor.execute()` → `getDataSourceMappingFromMapping()`

**关键问题**：
- `QueryExecutor` 会查找 Mapping，从 Mapping 中获取 `table_id`
- 从 Table 实例中获取 `table_name` 和 `database_id`
- **Mapping 中配置的是原始表的 `table_id`，所以查询的是原始表，而不是同步表**

### 4. 同步表创建逻辑

在 `MappedDataService.syncExtractWithTable()` 中：
- 同步表名：`objectType.toLowerCase()`（例如：`entrytransactionnew001`）
- 目标数据库：默认数据库（`application.properties` 中的 `db.*` 配置）
- 表创建在默认数据库中，而不是原始表所在的数据库

## 问题根源

1. **查询路径错误**：
   - 当前通过 `QueryService` → `QueryExecutor` → `Mapping` → 原始表
   - 应该直接查询同步表（表名 = 模型名，在默认数据库中）

2. **数据源选择错误**：
   - 当前查询的是 Mapping 中配置的原始表所在的数据源
   - 应该查询默认数据源（`application.properties` 中的 `db.*`）

3. **表名确定错误**：
   - 当前从 Mapping → Table 实例 → `table_name`（原始表名）
   - 应该直接使用模型名作为表名（同步表名）

## 解决方案

### 方案1：修改 RelationalInstanceStorage（推荐）

在 `RelationalInstanceStorage` 中优先查询同步表：

1. **检查同步表是否存在**：
   - 表名 = `objectType.toLowerCase()`
   - 数据库 = 默认数据库（`null`，从 `application.properties` 读取）

2. **如果同步表存在**：
   - 直接构建 SQL 查询同步表
   - 不使用 `QueryService`，避免通过 Mapping 查找原始表

3. **如果同步表不存在**：
   - 回退到原有逻辑（通过 `QueryService` 查询原始表）

### 方案2：修改 QueryService

在 `QueryExecutor` 中优先查找同步表：
- 先检查默认数据库中是否存在同步表（表名 = 模型名）
- 如果存在，使用同步表
- 如果不存在，再通过 Mapping 查找原始表

### 方案3：创建新的查询方法

创建一个专门查询同步表的方法：
- `querySyncTable(String objectType, ...)`
- 直接查询默认数据库中的同步表
- 不依赖 Mapping

## 推荐实现（方案1）

修改 `RelationalInstanceStorage.listInstances()`：

```java
@Override
public InstanceStorage.ListResult listInstances(String objectType, int offset, int limit) throws IOException {
    try {
        ObjectType objectTypeDef = loader.getObjectType(objectType);
        
        // 1. 优先检查同步表是否存在（表名 = 模型名，在默认数据库中）
        String syncTableName = objectType.toLowerCase();
        boolean syncTableExists = false;
        try {
            syncTableExists = databaseMetadataService.tableExists(null, syncTableName);
        } catch (Exception e) {
            logger.debug("Failed to check sync table existence: {}", e.getMessage());
        }
        
        if (syncTableExists) {
            // 2. 如果同步表存在，直接查询同步表
            return querySyncTable(objectType, syncTableName, null, offset, limit);
        }
        
        // 3. 如果同步表不存在，使用原有逻辑（通过QueryService查询原始表）
        // ... 原有代码 ...
    } catch (Exception e) {
        // ...
    }
}
```

## 需要修改的文件

1. `src/main/java/com/mypalantir/repository/RelationalInstanceStorage.java`
   - 添加 `querySyncTable()` 方法
   - 修改 `listInstances()`、`getInstance()`、`searchInstances()` 方法

2. 可能需要注入 `DatabaseMetadataService` 到 `RelationalInstanceStorage`

## 注意事项

1. **向后兼容**：如果同步表不存在，应该回退到原有逻辑
2. **性能考虑**：每次查询都检查表是否存在可能有性能开销，可以考虑缓存
3. **数据一致性**：确保同步表和原始表的数据一致性由 ETL 系统维护

