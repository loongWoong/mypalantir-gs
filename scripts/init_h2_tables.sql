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
('VEH008', '苏H66666', '小型客车', '吴十');

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

-- 验证数据（注释掉，因为会在 Java 程序中执行）
-- SELECT '车辆数据' AS table_name, COUNT(*) AS count FROM vehicles
-- UNION ALL
-- SELECT '通行介质数据', COUNT(*) FROM media;

