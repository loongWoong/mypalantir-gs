# 同步抽取功能说明

## 功能概述

当点击Instances列表的"同步抽取"按钮时，系统会：
1. 根据模型定义的属性与原始表的映射关系
2. 根据原始表字段获取表字段类型、字段长度
3. 构建同步表（目标表）
4. 从源表抽取数据到目标表

## 实现流程

### 1. 获取映射关系

从Mapping配置中获取：
- 源表信息（table_id, table_name）
- 列到属性的映射关系（column_property_mappings）
- 主键列（primary_key_column）

### 2. 获取源表字段信息

通过 `DatabaseMetadataService.getColumns()` 获取源表的字段信息：
- 字段类型（data_type）
- 字段长度（column_size）
- 小数位数（decimal_digits）
- 是否可空（nullable）
- 是否主键（is_primary_key）

### 3. 构建目标表结构

根据以下信息构建CREATE TABLE SQL：
- **字段名**：使用模型属性名（property name）
- **字段类型**：根据源表字段类型和长度转换
- **字段长度**：使用源表字段的长度
- **是否可空**：根据模型属性定义（required）和源表字段定义
- **主键**：使用id字段作为主键

### 4. 创建目标表

执行CREATE TABLE IF NOT EXISTS语句，创建同步表。

表名格式：`{objectType}_sync`
例如：`person_sync`, `vehicle_sync`

### 5. 抽取数据

执行INSERT INTO ... SELECT ... FROM语句，从源表抽取数据到目标表。

使用 `ON DUPLICATE KEY UPDATE` 处理重复数据。

## API接口

### 同步抽取接口

**POST** `/api/v1/instances/{objectType}/sync-from-mapping/{mappingId}?targetDatabaseId={databaseId}`

**参数：**
- `objectType`: 对象类型名称
- `mappingId`: 映射配置ID
- `targetDatabaseId` (可选): 目标数据库ID，如果不指定则使用源数据库

**响应：**
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

### 兼容性

如果不提供 `targetDatabaseId` 参数，会使用旧的同步方法（只同步到实例存储，不创建表）。

## 数据类型映射

源表字段类型到MySQL类型的映射：

| 源类型 | MySQL类型 | 说明 |
|--------|-----------|------|
| VARCHAR, CHAR | VARCHAR(n) | n为源字段长度 |
| INT, INTEGER | INT | - |
| BIGINT | BIGINT | - |
| SMALLINT | SMALLINT | - |
| TINYINT | TINYINT | - |
| DOUBLE, FLOAT | DOUBLE 或 DECIMAL(n,m) | 如果有小数位，使用DECIMAL |
| TEXT | TEXT | - |
| DATE | DATE | - |
| DATETIME, TIMESTAMP | DATETIME | - |
| BOOLEAN, BOOL | TINYINT(1) | - |
| 其他 | VARCHAR(255) | 默认类型 |

## 示例

### 场景：同步Person对象

**源表结构：**
```sql
CREATE TABLE source_persons (
  person_id INT PRIMARY KEY,
  full_name VARCHAR(100),
  email VARCHAR(255),
  age INT
);
```

**映射关系：**
```json
{
  "column_property_mappings": {
    "person_id": "id",
    "full_name": "name",
    "email": "email",
    "age": "age"
  },
  "primary_key_column": "person_id"
}
```

**生成的目标表：**
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

**数据抽取SQL：**
```sql
INSERT INTO `person_sync` (`id`, `name`, `email`, `age`)
SELECT `person_id` AS `id`, `full_name` AS `name`, `email` AS `email`, `age` AS `age`
FROM `source_persons`
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;
```

## 注意事项

1. **表名冲突**：如果目标表已存在，不会重新创建，直接抽取数据
2. **数据去重**：使用 `ON DUPLICATE KEY UPDATE` 处理主键冲突
3. **字段映射**：只有配置了映射的字段才会被抽取
4. **数据类型**：目标表字段类型基于源表字段类型，但会转换为MySQL兼容类型
5. **主键要求**：目标表必须有id字段作为主键

## 错误处理

- 如果源表不存在，会抛出SQLException
- 如果映射关系不存在，会抛出IOException
- 如果对象类型不存在，会抛出Loader.NotFoundException
- 如果目标数据库连接失败，会抛出SQLException

## 日志

操作过程会记录日志：
- 表创建：`Created sync table: {tableName}`
- 表已存在：`Sync table already exists: {tableName}`
- 数据抽取：`Extracted {count} rows from {sourceTable} to {targetTable}`

