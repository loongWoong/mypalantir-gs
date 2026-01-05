-- 初始化 H2 数据库表结构
-- 创建车辆表和通行介质表，并建立一对多关系（通过中间表）

-- 创建通行介质表
CREATE TABLE IF NOT EXISTS media (
    media_id VARCHAR(50) PRIMARY KEY,
    media_number VARCHAR(50) NOT NULL UNIQUE COMMENT '介质编号',
    media_type VARCHAR(50) NOT NULL COMMENT '介质类型（ETC卡、OBU等）',
    issue_date DATE COMMENT '发行日期',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建车辆表（移除 obu_id 字段，关系通过中间表维护）
CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id VARCHAR(50) PRIMARY KEY,
    plate_number VARCHAR(20) NOT NULL UNIQUE COMMENT '车牌号',
    vehicle_type VARCHAR(50) COMMENT '车辆类型',
    owner_name VARCHAR(100) COMMENT '车主姓名',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建车辆-通行介质关联表（中间表，支持一对多关系）
-- 存储"持有" link type 的数据
CREATE TABLE IF NOT EXISTS vehicle_media (
    link_id VARCHAR(50) PRIMARY KEY,
    vehicle_id VARCHAR(50) NOT NULL COMMENT '车辆ID',
    media_id VARCHAR(50) NOT NULL COMMENT '通行介质ID',
    bind_time TIMESTAMP COMMENT '绑定时间（对应 link type 的"绑定时间"属性）',
    bind_status VARCHAR(50) COMMENT '绑定状态（对应 link type 的"绑定状态"属性）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vm_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id) ON DELETE CASCADE,
    CONSTRAINT fk_vm_media FOREIGN KEY (media_id) REFERENCES media(media_id) ON DELETE CASCADE,
    CONSTRAINT uk_vm_vehicle_media UNIQUE (vehicle_id, media_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_vehicles_plate_number ON vehicles(plate_number);
CREATE INDEX IF NOT EXISTS idx_media_number ON media(media_number);
CREATE INDEX IF NOT EXISTS idx_media_type ON media(media_type);
CREATE INDEX IF NOT EXISTS idx_vehicle_media_vehicle_id ON vehicle_media(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_vehicle_media_media_id ON vehicle_media(media_id);

-- 插入测试数据：通行介质（OBU）
INSERT INTO media (media_id, media_number, media_type, issue_date) VALUES
('OBU001', 'OBU-2024-001', 'OBU', '2024-01-15'),
('OBU002', 'OBU-2024-002', 'OBU', '2024-02-20'),
('OBU003', 'OBU-2024-003', 'OBU', '2024-03-10'),
('OBU004', 'OBU-2024-004', 'OBU', '2024-04-05'),
('OBU005', 'OBU-2024-005', 'OBU', '2024-05-12'),
('ETC001', 'ETC-2024-001', 'ETC卡', '2024-01-10'),
('ETC002', 'ETC-2024-002', 'ETC卡', '2024-02-15'),
('ETC003', 'ETC-2024-003', 'ETC卡', '2024-03-20');

-- 插入测试数据：车辆
INSERT INTO vehicles (vehicle_id, plate_number, vehicle_type, owner_name) VALUES
('VEH001', '苏A12345', '小型客车', '张三'),
('VEH002', '苏B67890', '小型客车', '李四'),
('VEH003', '苏C11111', '大型客车', '王五'),
('VEH004', '苏D22222', '小型货车', '赵六'),
('VEH005', '苏E33333', '小型客车', '钱七'),
('VEH006', '苏F44444', '小型客车', '孙八'),
('VEH007', '苏G55555', '小型客车', '周九'),
('VEH008', '苏H66666', '小型客车', '吴十'),
-- 添加收费查询使用的车辆ID（与 vehicles_en 对应）
('VEH_EN001', '苏A12345', '小型客车', '张三'),
('VEH_EN002', '苏B67890', '小型客车', '李四'),
('VEH_EN003', '苏C11111', '大型客车', '王五'),
('VEH_EN004', '苏D22222', '小型货车', '赵六'),
('VEH_EN005', '苏E33333', '小型客车', '钱七'),
('VEH_EN006', '苏F44444', '小型客车', '孙八'),
('VEH_EN007', '苏G55555', '小型客车', '周九'),
('VEH_EN008', '苏H66666', '小型客车', '吴十'),
('VEH_EN009', '浙A99999', '小型客车', '浙江车主'),
('VEH_EN010', '沪A88888', '小型客车', '上海车主');

-- 插入测试数据：车辆-通行介质关联（一对多关系）
-- 一个车辆可以持有多个通行介质
INSERT INTO vehicle_media (link_id, vehicle_id, media_id, bind_time, bind_status) VALUES
('LINK001', 'VEH001', 'OBU001', '2024-01-20 10:00:00', '已激活'),
('LINK002', 'VEH001', 'ETC001', '2024-01-25 14:30:00', '已激活'),  -- VEH001 持有两个通行介质
('LINK003', 'VEH002', 'OBU002', '2024-02-25 09:15:00', '已激活'),
('LINK004', 'VEH003', 'OBU003', '2024-03-15 11:20:00', '已激活'),
('LINK005', 'VEH004', 'OBU004', '2024-04-10 16:45:00', '已激活'),
('LINK006', 'VEH005', 'OBU005', '2024-05-18 08:30:00', '已激活'),
('LINK007', 'VEH005', 'ETC002', '2024-05-20 13:00:00', '已激活'),  -- VEH005 持有两个通行介质
('LINK008', 'VEH006', 'ETC003', '2024-03-25 15:30:00', '已激活');

-- ============================================
-- 收费查询相关表（用于聚合查询测试）
-- ============================================

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
    CONSTRAINT fk_tr_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id) ON DELETE CASCADE
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

-- 验证数据（注释掉，因为会在 Java 程序中执行）
-- SELECT '车辆数据' AS table_name, COUNT(*) AS count FROM vehicles
-- UNION ALL
-- SELECT '通行介质数据', COUNT(*) FROM media
-- UNION ALL
-- SELECT '收费站数据', COUNT(*) FROM toll_stations
-- UNION ALL
-- SELECT '收费记录数据', COUNT(*) FROM toll_records
-- UNION ALL
-- SELECT '车辆英文数据', COUNT(*) FROM vehicles_en;

