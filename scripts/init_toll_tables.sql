-- 收费查询相关表初始化脚本
-- 注意：需要先停止应用，然后执行此脚本

-- 创建车辆英文表（用于收费查询，需要先创建，因为 toll_records 表有外键依赖）
CREATE TABLE IF NOT EXISTS vehicles_en (
    vehicle_id VARCHAR(50) PRIMARY KEY,
    plate VARCHAR(20) NOT NULL UNIQUE COMMENT '车牌号',
    vehicle_type VARCHAR(50) COMMENT '车辆类型',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建收费站表
CREATE TABLE IF NOT EXISTS toll_stations (
    station_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '收费站名称',
    province VARCHAR(50) NOT NULL COMMENT '所属省份',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建收费记录表
CREATE TABLE IF NOT EXISTS toll_records (
    record_id VARCHAR(50) PRIMARY KEY,
    station_id VARCHAR(50) NOT NULL COMMENT '收费站ID（外键）',
    vehicle_id VARCHAR(50) NOT NULL COMMENT '车辆ID（外键）',
    amount DECIMAL(10, 2) NOT NULL COMMENT '收费金额',
    charge_time TIMESTAMP NOT NULL COMMENT '收费时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tr_station FOREIGN KEY (station_id) REFERENCES toll_stations(station_id) ON DELETE CASCADE,
    CONSTRAINT fk_tr_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles_en(vehicle_id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_toll_stations_province ON toll_stations(province);
CREATE INDEX IF NOT EXISTS idx_toll_records_station_id ON toll_records(station_id);
CREATE INDEX IF NOT EXISTS idx_toll_records_vehicle_id ON toll_records(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_toll_records_charge_time ON toll_records(charge_time);
CREATE INDEX IF NOT EXISTS idx_vehicles_en_plate ON vehicles_en(plate);

-- 插入测试数据：收费站（江苏省）
INSERT INTO toll_stations (station_id, name, province) VALUES
('ST001', '南京收费站', '江苏'),
('ST002', '苏州收费站', '江苏'),
('ST003', '无锡收费站', '江苏'),
('ST004', '杭州收费站', '浙江'),  -- 非江苏省，用于对比
('ST005', '上海收费站', '上海');  -- 非江苏省，用于对比

-- 插入测试数据：车辆（英文表）
INSERT INTO vehicles_en (vehicle_id, plate, vehicle_type) VALUES
('VEH_EN001', '苏A12345', '小型客车'),
('VEH_EN002', '苏B67890', '小型客车'),
('VEH_EN003', '苏C11111', '大型客车'),
('VEH_EN004', '苏D22222', '小型货车'),
('VEH_EN005', '苏E33333', '小型客车'),
('VEH_EN006', '苏F44444', '小型客车'),
('VEH_EN007', '苏G55555', '小型客车'),
('VEH_EN008', '苏H66666', '小型客车'),
('VEH_EN009', '浙A99999', '小型客车'),  -- 非江苏省车辆
('VEH_EN010', '沪A88888', '小型客车');  -- 非江苏省车辆

-- 插入测试数据：收费记录（2024年1月，江苏省收费站）
-- 南京收费站
INSERT INTO toll_records (record_id, station_id, vehicle_id, amount, charge_time) VALUES
('REC001', 'ST001', 'VEH_EN001', 25.50, '2024-01-05 08:30:00'),
('REC002', 'ST001', 'VEH_EN002', 30.00, '2024-01-05 09:15:00'),
('REC003', 'ST001', 'VEH_EN003', 45.00, '2024-01-10 10:20:00'),
('REC004', 'ST001', 'VEH_EN004', 35.50, '2024-01-15 14:30:00'),
('REC005', 'ST001', 'VEH_EN005', 28.00, '2024-01-20 16:45:00'),
('REC006', 'ST001', 'VEH_EN001', 25.50, '2024-01-25 11:00:00'),  -- 同一车辆再次通过
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
('REC020', 'ST004', 'VEH_EN009', 55.00, '2024-01-10 10:00:00'),  -- 浙江收费站
('REC021', 'ST005', 'VEH_EN010', 48.00, '2024-01-15 12:00:00');  -- 上海收费站

-- 非2024年1月的数据（用于验证时间过滤）
INSERT INTO toll_records (record_id, station_id, vehicle_id, amount, charge_time) VALUES
('REC022', 'ST001', 'VEH_EN001', 25.50, '2023-12-30 08:30:00'),  -- 2023年12月
('REC023', 'ST001', 'VEH_EN002', 30.00, '2024-02-01 09:15:00');  -- 2024年2月

