# 数据交叉对比模块设计方案 (Data Reconciliation)

## 1. 模块概述
本模块旨在提供灵活的数据对账（Reconciliation）能力，允许用户选择系统内的两个数据表（可以是同源或异源），定义对比规则（主键、对比字段），并生成差异报告。该功能主要用于数据迁移验证、一致性检查及问题排查。

## 2. 功能设计

工作流包含三个阶段：**配置 -> 执行 -> 分析**。

### 2.1 阶段一：对比配置
- **源数据选择**：选择“基准表 (Source Table)”和“对比表 (Target Table)”。
- **主键关联 (Key Association)**：选择一列或多列作为**主键** (Join Keys)，用于在两表之间对齐行记录（例如：订单ID、用户ID）。
- **字段映射 (Field Mapping)**：选择需要对比的列（例如：对比 `source.amount` 和 `target.payment_amt`）。系统支持按名称自动映射。

### 2.2 阶段二：任务执行
- **对比策略**：
    - **存在性检查**：验证主键是否在两表中都存在。
    - **值一致性检查**：对于主键匹配的记录，严格对比映射字段的值。
- **执行模式**：支持立即执行。

### 2.3 阶段三：结果分析
- **概览仪表盘**：
    - 源表/目标表总行数。
    - **匹配数 (Matched)**：主键存在且所有对比字段值一致。
    - **不匹配数 (Mismatched)**：主键存在但字段值不一致。
    - **仅源表存在 (Source Only)**：主键仅在源表中存在。
    - **仅目标表存在 (Target Only)**：主键仅在目标表中存在。
- **差异详情表**：
    - 展示具体的差异记录，列包含：`Key Value`, `Field Name`, `Source Value`, `Target Value`, `Diff Type`。

## 3. 技术架构设计

### 3.1 后端设计 (Java / Spring Boot)

复用 `DatabaseMetadataService` 获取表结构，复用 `QueryExecutor` 原理进行数据访问。

#### 新增 Controller: `ComparisonController`
- `POST /api/v1/comparison/run`: 提交对比任务。
- `GET /api/v1/comparison/{taskId}`: 获取任务状态和概览。
- `GET /api/v1/comparison/{taskId}/details`: 获取分页的差异详情。

#### 新增 Service: `DataComparisonService`
- **核心逻辑**：
    1.  分别查询表 A 和表 B 的数据（按 Key 排序）。
    2.  使用 **流式游标对比算法 (Stream-based Cursor Comparison)** 遍历两个有序流，识别 新增/缺失/修改 的记录。
    3.  生成 `ComparisonResult` 对象。

#### 数据模型 (Draft)
```java
class ComparisonRequest {
    String sourceTableId;
    String targetTableId;
    List<String> keyColumns; // e.g. ["id"]
    Map<String, String> columnMapping; // e.g. {"name": "full_name"}
}

class ComparisonResult {
    String taskId;
    long timestamp;
    ValidationSummary summary; // counts
    List<DiffRecord> diffs;    // details
}

class DiffRecord {
    Map<String, Object> keys;
    String type; // MISSING_IN_TARGET, MISSING_IN_SOURCE, VALUE_MISMATCH
    Map<String, ValueDiff> details;
}
```

### 3.2 前端设计 (React / TypeScript)

#### 新增页面: `DataComparison.tsx`
#### 组件:
- **TableSelector**: 复用现有下拉框选择表。
- **MappingConfig**: 类似现有数据映射页面的 UI，但用于配置表 A 到表 B 的列映射。
- **DiffResultView**:
    - **统计卡片**: 4个色块展示各类统计数。
    - **差异表格**: 高亮显示差异。例如：源值标红，目标值标绿。

## 4. 实施路线图

1.  **后端实现**: 实现 `DataComparisonService` 数据拉取与逻辑比对。
2.  **API 暴露**: 在 `ComparisonController` 中暴露接口。
3.  **前端实现**: 创建 `DataComparison` 页面并对接 API。
