-- 验证收费数据

-- 查看江苏省收费站
SELECT '江苏省收费站' AS info, COUNT(*) AS count FROM toll_stations WHERE province = '江苏';

-- 查看车辆数据
SELECT '车辆数据' AS info, COUNT(*) AS count FROM vehicles_en;

-- 查看2024年1月的收费记录总数
SELECT '2024年1月收费记录' AS info, COUNT(*) AS count FROM toll_records 
WHERE charge_time >= '2024-01-01' AND charge_time < '2024-02-01';

-- 查看江苏省收费站2024年1月的收费总额（按收费站分组）
SELECT 
    ts.name AS station_name,
    COUNT(tr.record_id) AS record_count,
    SUM(tr.amount) AS total_fee
FROM toll_stations ts
JOIN toll_records tr ON ts.station_id = tr.station_id
WHERE ts.province = '江苏'
  AND tr.charge_time >= '2024-01-01' 
  AND tr.charge_time < '2024-02-01'
GROUP BY ts.name
ORDER BY total_fee DESC;

