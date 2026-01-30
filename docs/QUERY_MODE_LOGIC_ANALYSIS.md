# 查询模式逻辑分析

## 当前代码逻辑

### loadData 函数中的查询逻辑（第324-421行）

1. **映射数据查询**（`queryMode === 'mapping'`）：
   - 使用 `instanceApi.listWithMapping(objectType, targetMappingId, ...)`
   - 查询原始表（通过 mappingId）

2. **实例存储查询**（`queryMode === 'storage'`）：
   - 使用 `queryApi.execute()` 或 `instanceApi.list()`
   - 查询同步表（表名=模型名，在默认数据库中）

### 按钮配置（第716-718行）

```typescript
options={[
  { value: 'mapping', label: '映射数据', ... },
  { value: 'storage', label: '实例存储', ... },
]}
```

### onChange 回调逻辑（第695-714行）

```typescript
if (mode === 'storage') {
  // 切换到实例存储查询模式
  setQueryMode('storage');
  newParams.delete('mappingId');  // 删除 mappingId
} else {  // mode === 'mapping'
  // 切换到映射数据查询模式
  setQueryMode('mapping');
  newParams.set('mappingId', targetMappingId);  // 设置 mappingId
}
```

## 问题分析

逻辑看起来是正确的：
- `value: 'mapping'` → `label: '映射数据'` → `queryMode = 'mapping'` → 查询映射数据（原始表）
- `value: 'storage'` → `label: '实例存储'` → `queryMode = 'storage'` → 查询实例存储（同步表）

但是，用户说"映射数据和实例存储查询逻辑弄反了"，可能的原因是：

1. **按钮显示和实际查询逻辑不匹配**：虽然 value 和 label 的对应关系是对的，但可能用户期望的是：
   - 点击"映射数据"按钮 → 应该查询同步表（因为这是映射后的数据）
   - 点击"实例存储"按钮 → 应该查询原始表（因为这是存储的原始数据）

2. **或者，按钮的 value 和 label 应该反过来**：
   - `value: 'mapping'` → `label: '实例存储'`
   - `value: 'storage'` → `label: '映射数据'`

## 解决方案

根据用户的需求描述：
- "映射数据"：根据mapping关系，查询模型关联的数据表数据（原始表）✅ 当前正确
- "实例存储"：查询配置文件配置的默认数据库，根据模型名查询表数据（同步表）✅ 当前正确

所以逻辑应该是正确的。但用户说"弄反了"，可能是：
1. 按钮的 value 和 label 需要反过来
2. 或者 onChange 中的逻辑需要反过来

让我检查一下是否有其他地方的逻辑不一致。

