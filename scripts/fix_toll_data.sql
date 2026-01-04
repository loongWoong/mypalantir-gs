-- 修复收费数据：确保所有车辆数据存在，然后插入收费记录

-- 先确保所有车辆数据存在（使用 INSERT IGNORE 或 MERGE）
-- H2 不支持 INSERT IGNORE，使用 MERGE 或先检查再插入

-- 插入车辆数据（如果不存在）
MERGE INTO vehicles_en (vehicle_id, plate, vehicle_type) VALUES
('VEH_EN001', '苏A12345', '小型客车'),
('VEH_EN002', '苏B67890', '小型客车'),
('VEH_EN003', '苏C11111', '大型客车'),
('VEH_EN004', '苏D22222', '小型货车'),
('VEH_EN005', '苏E33333', '小型客车'),
('VEH_EN006', '苏F44444', '小型客车'),
('VEH_EN007', '苏G55555', '小型客车'),
('VEH_EN008', '苏H66666', '小型客车'),
('VEH_EN009', '浙A99999', '小型客车'),
('VEH_EN010', '沪A88888', '小型客车');

-- 删除已存在的收费记录（如果之前部分插入失败）
DELETE FROM toll_records WHERE record_id LIKE 'REC%';

-- 插入收费记录（2024年1月，江苏省收费站）
-- 南京收费站
INSERT INTO toll_records (record_id, station_id, vehicle_id, amount, charge_time) VALUES
('REC001', 'ST001', 'VEH_EN001', 25.50, '2024-01-05 08:30:00'),
('REC002', 'ST001', 'VEH_EN002', 30.00, '2024-01-05 09:15:00'),
('REC003', 'ST001', 'VEH_EN003', 45.00, '2024-01-10 10:20:00'),
('REC004', 'ST001', 'VEH_EN004', 35.50, '2024-01-15 14:30:00'),
('REC005', 'ST001', 'VEH_EN005', 28.00, '2024-01-20 16:45:00'),
('REC006', 'ST001', 'VEH_EN001', 25.50, '2024-01-25 11:00:00'),
('REC007', 'ST001', 'VEH_EN006', 32.00, '2024-01-28 13:20:00'),
('REC008', 'ST001', 'VEH_EN007', 29.50, '2024-01-30 15:10:00');

-- 苏州收费站
INSERT INTO toll_records (record_id, station_id, vehicle_id, amount, charge_time) VALUES
('REC009', 'ST002', 'VEH_EN001', 40.00, '2024-01-08 09:00:00'),
('REC010', 'ST002', 'VEH_EN002', 42.50, '2024-01-12 10:30:00'),
('REC011', 'ST002', 'VEH_EN003', 60.00, '2024-01-18 12:15:00'),
('REC012', 'ST002', 'VEH_EN005', 38.00, '2024-01-22 14:00:00'),
('REC013', 'ST002', 'VEH_EN006', 45.00, '2024-01-26 16:30:00'),
('REC014', 'ST002', 'VEH_EN008', 35.00, '2024-01-29 08:45:00');

-- 无锡收费站
INSERT INTO toll_records (record_id, station_id, vehicle_id, amount, charge_time) VALUES
('REC015', 'ST003', 'VEH_EN002', 35.00, '2024-01-06 07:20:00'),
('REC016', 'ST003', 'VEH_EN004', 50.00, '2024-01-11 11:40:00'),
('REC017', 'ST003', 'VEH_EN005', 33.00, '2024-01-16 13:50:00'),
('REC018', 'ST003', 'VEH_EN007', 40.50, '2024-01-24 09:30:00'),
('REC019', 'ST003', 'VEH_EN008', 36.00, '2024-01-27 15:20:00');

-- 非江苏省收费站（用于验证过滤条件）
INSERT INTO toll_records (record_id, station_id, vehicle_id, amount, charge_time) VALUES
('REC020', 'ST004', 'VEH_EN009', 55.00, '2024-01-10 10:00:00'),
('REC021', 'ST005', 'VEH_EN010', 48.00, '2024-01-15 12:00:00');

-- 非2024年1月的数据（用于验证时间过滤）
INSERT INTO toll_records (record_id, station_id, vehicle_id, amount, charge_time) VALUES
('REC022', 'ST001', 'VEH_EN001', 25.50, '2023-12-30 08:30:00'),
('REC023', 'ST001', 'VEH_EN002', 30.00, '2024-02-01 09:15:00');

