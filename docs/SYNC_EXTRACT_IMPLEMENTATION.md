# 同步抽取功能实现总结

## 实现概述

已实现完整的同步抽取功能，当点击Instances列表的"同步抽取"按钮时，系统会：
1. ✅ 根据模型定义的属性与原始表的映射关系
2. ✅ 根据原始表字段获取表字段类型、字段长度
3. ✅ 构建同步表（目标表）
4. ✅ 从源表抽取数据到目标表

## 实现细节

### 1. 新增方法

#### DatabaseMetadataService
- `executeUpdate(String sql, String databaseId)`: 执行DDL/DML语句（CREATE TABLE, INSERT等）
- `tableExists(String databaseId, String tableName)`: 检查表是否存在

#### MappedDataService
- `syncExtractWithTable(String objectType, String mappingId, String targetDatabaseId)`: 完整的同步抽取流程
- `buildSyncTableSql(...)`: 构建CREATE TABLE SQL
- `buildColumnDefinition(...)`: 构建列定义
- `buildDataType(...)`: 数据类型映射
- `buildExtractSql(...)`: 构建数据抽取SQL

### 2. 工作流程

```
1. 获取映射关系
   ↓
2. 获取源表信息（表名、数据库ID）
   ↓
3. 获取源表字段信息（类型、长度、是否可空等）
   ↓
4. 获取对象类型定义（模型属性）
   ↓
5. 构建目标表结构SQL
   ↓
6. 创建目标表（如果不存在）
   ↓
7. 构建数据抽取SQL
   ↓
8. 执行数据抽取
   ↓
9. 返回结果
```

### 3. 表结构构建逻辑

#### 字段映射
- **字段名**：使用模型属性名（property name）
- **字段类型**：根据源表字段类型转换
- **字段长度**：使用源表字段的长度
- **是否可空**：根据模型属性定义（required）和源表字段定义
- **注释**：使用模型属性描述或源表字段备注

#### 主键处理
- 目标表必须有id字段作为主键
- 如果映射中有id字段，使用它
- 如果没有，自动添加 `id VARCHAR(255) NOT NULL`

#### 时间戳字段
- 自动添加 `created_at` 和 `updated_at` 字段

### 4. 数据抽取逻辑

#### ID字段映射优先级
1. 映射到id的源列
2. 主键列（primary_key_column）
3. 第一个映射列
4. UUID()（如果都没有）

#### 数据去重
- 使用 `ON DUPLICATE KEY UPDATE` 处理主键冲突
- 冲突时更新 `updated_at` 字段

### 5. 数据类型映射

| 源类型 | MySQL类型 | 说明 |
|--------|-----------|------|
| VARCHAR, CHAR | VARCHAR(n) | n为源字段长度 |
| INT, INTEGER | INT | - |
| BIGINT | BIGINT | - |
| DOUBLE, FLOAT | DOUBLE 或 DECIMAL(n,m) | 有小数位时用DECIMAL |
| TEXT | TEXT | - |
| DATE | DATE | - |
| DATETIME, TIMESTAMP | DATETIME | - |
| BOOLEAN | TINYINT(1) | - |
| 其他 | VARCHAR(255) | 默认 |

## API变更

### 修改的接口

**POST** `/api/v1/instances/{objectType}/sync-from-mapping/{mappingId}`

**变更：**
- 现在默认执行表结构同步和数据抽取
- 支持可选的 `targetDatabaseId` 参数（如果不指定，使用源数据库）

**响应格式：**
```json
{
  "success": true,
  "data": {
    "table_created": true,
    "rows_extracted": 1000,
    "target_table_name": "person_sync",
    "target_database_id": "default"
  }
}
```

## 使用示例

### 前端调用（无需修改）

前端代码无需修改，现有的调用方式：
```typescript
await instanceApi.syncFromMapping(objectType, mappingId);
```

会自动执行：
1. 构建同步表
2. 抽取数据

### 后端调用

```java
// 使用源数据库作为目标数据库
SyncExtractResult result = mappedDataService.syncExtractWithTable(
    "person", "mapping-id-123", null);

// 或指定目标数据库
SyncExtractResult result = mappedDataService.syncExtractWithTable(
    "person", "mapping-id-123", "target-db-id");
```

## 生成的SQL示例

### CREATE TABLE SQL
```sql
CREATE TABLE IF NOT EXISTS `person_sync` (
  `id` INT NOT NULL,
  `name` VARCHAR(100),
  `email` VARCHAR(255),
  `age` INT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员同步表';
```

### INSERT ... SELECT SQL
```sql
INSERT INTO `person_sync` (`id`, `name`, `email`, `age`)
SELECT `person_id` AS `id`, `full_name` AS `name`, `email` AS `email`, `age` AS `age`
FROM `source_persons`
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;
```

## 注意事项

1. **表名格式**：目标表名为 `{objectType}_sync`
2. **表已存在**：如果表已存在，不会重新创建，直接抽取数据
3. **数据去重**：使用主键去重，冲突时更新 `updated_at`
4. **字段映射**：只有配置了映射的字段才会被抽取
5. **数据类型**：基于源表字段类型，转换为MySQL兼容类型
6. **主键要求**：目标表必须有id字段作为主键

## 测试建议

1. **测试表创建**：验证表结构是否正确
2. **测试数据抽取**：验证数据是否正确映射
3. **测试数据类型**：验证各种数据类型的转换
4. **测试主键冲突**：验证ON DUPLICATE KEY UPDATE是否正常工作
5. **测试空值处理**：验证NULL值的处理

## 后续优化

1. **增量同步**：支持基于时间戳的增量同步
2. **数据转换**：支持数据格式转换（如日期格式）
3. **错误处理**：更详细的错误信息和回滚机制
4. **性能优化**：大批量数据的分批处理
5. **进度反馈**：支持同步进度查询

