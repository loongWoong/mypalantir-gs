# 实例存储查询问题分析

## 问题描述

前端有两个按钮：
1. **映射数据**：根据mapping关系，查询模型关联的数据表数据（原始表）- ✅ 正常工作
2. **实例存储**：查询配置文件配置的默认数据库，根据模型名查询表数据（同步表）- ❌ 不正常

## 问题分析

### 1. 前端按钮标签和值的对应关系错误

在 `InstanceList.tsx` 第717-718行：
```typescript
{ value: 'storage', label: '映射数据', ... },
{ value: 'mapping', label: '实例存储', ... },
```

**问题**：
- `value: 'storage'` 对应 `label: '映射数据'`，但实际逻辑中 `queryMode === 'mapping'` 才是映射数据查询
- `value: 'mapping'` 对应 `label: '实例存储'`，但实际逻辑中 `queryMode === 'storage'` 才是实例存储查询

### 2. 实例存储查询逻辑问题

在 `loadData` 函数中（第337-421行），当 `queryMode === 'storage'` 时：
- 使用 `queryApi.execute()` 或 `instanceApi.list()` 查询
- 这些API会通过 `QueryService` 查询
- `QueryService` 会查找 Mapping，然后查询**原始表**，而不是**同步表**

**期望行为**：
- 当点击"实例存储"按钮时，应该直接查询同步表（表名=模型名，在默认数据库中）
- 不应该通过 `QueryService` 查询，因为 `QueryService` 会查找 Mapping 并查询原始表

## 解决方案

### 方案1：修复前端按钮标签（推荐）

修正按钮标签和值的对应关系：
- `value: 'mapping'` → `label: '映射数据'`（查询原始表）
- `value: 'storage'` → `label: '实例存储'`（查询同步表）

### 方案2：修改实例存储查询逻辑

当 `queryMode === 'storage'` 时，不通过 `QueryService` 查询，而是：
1. 直接调用后端API查询同步表
2. 或者修改后端API，使其在查询实例存储时优先查询同步表

**注意**：后端已经修改了 `RelationalInstanceStorage`，使其优先查询同步表，但前提是：
- 使用 `hybrid` 存储模式
- `HybridInstanceStorage` 路由到 `RelationalInstanceStorage`
- `RelationalInstanceStorage` 检查同步表是否存在

### 方案3：创建专门的同步表查询API

创建一个新的API端点，专门用于查询同步表：
- `GET /api/v1/instances/{objectType}/sync-table`
- 直接查询默认数据库中的同步表（表名=模型名）

## 推荐实现

1. **修复前端按钮标签**（方案1）
2. **确保后端逻辑正确**：`RelationalInstanceStorage` 已经实现了优先查询同步表的逻辑
3. **验证存储模式**：确保 `storage.type=hybrid` 配置正确

