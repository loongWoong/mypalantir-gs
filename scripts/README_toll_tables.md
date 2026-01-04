# 收费查询表初始化说明

## 执行步骤

由于应用正在运行时会锁定数据库，需要按以下步骤执行：

### 方法1：停止应用后执行（推荐）

1. 停止正在运行的 Spring Boot 应用
2. 执行初始化脚本：
   ```bash
   cd /home/chun/Develop/mypalantir
   mvn exec:java -Dexec.mainClass="com.mypalantir.util.H2DatabaseInitializer" -Dexec.args="scripts/init_toll_tables.sql" -q
   ```
3. 重新启动应用

### 方法2：使用 H2 Console（如果可用）

1. 访问 H2 Console（如果已配置）
2. 连接数据库：`jdbc:h2:file:./data/h2/mypalantir`
3. 复制 `scripts/init_toll_tables.sql` 的内容并执行

## 创建的表

- `toll_stations`: 收费站表
- `toll_records`: 收费记录表
- `vehicles_en`: 车辆英文表（用于收费查询）

## 测试数据

- **收费站**：3个江苏省收费站（南京、苏州、无锡）+ 2个其他省份（用于对比）
- **车辆**：10辆车（8辆江苏省 + 2辆其他省份）
- **收费记录**：
  - 23条记录
  - 其中21条是2024年1月江苏省的记录
  - 2条非江苏省记录（用于验证过滤）
  - 2条非1月记录（用于验证时间过滤）

## 验证查询

执行以下查询验证数据：

```sql
-- 查看江苏省收费站
SELECT * FROM toll_stations WHERE province = '江苏';

-- 查看2024年1月的收费记录
SELECT COUNT(*) FROM toll_records 
WHERE charge_time >= '2024-01-01' AND charge_time < '2024-02-01';

-- 查看江苏省收费站2024年1月的收费总额（按收费站分组）
SELECT 
    ts.name,
    SUM(tr.amount) as total_fee
FROM toll_stations ts
JOIN toll_records tr ON ts.station_id = tr.station_id
WHERE ts.province = '江苏'
  AND tr.charge_time >= '2024-01-01' 
  AND tr.charge_time < '2024-02-01'
GROUP BY ts.name
ORDER BY total_fee DESC;
```

预期结果：
- 南京收费站：约 251.00
- 苏州收费站：约 260.50
- 无锡收费站：约 194.50

