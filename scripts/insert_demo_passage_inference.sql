-- ============================================================
-- 演示数据：PASS_LATE_001
-- 用于推理引擎得到 5 cycles, 6 rules fired 的结果：
--   1) 通行路径完整性正常  2) OBU拆分分析范围  3) OBU拆分路径不一致
--   4) 根因-ETC门架路径不完整  5) 收费站存在OBU拆分异常  6) 根因-门架延迟上传
-- 表与列名严格对应 scripts/DDL.sql 中的定义（全小写）
-- ============================================================

-- 1) 收费路段（tollsection：DDL 需提供 NOT NULL 列）
INSERT IGNORE INTO tollsection (
  id, name, road_id, section_owner_id, toll_roads, start_stake_num, end_stake_num,
  start_lat, start_lng, end_lat, end_lng, length, start_time, end_time,
  tax, tax_rate, charge_type, type, operation
) VALUES (
  'ST_DEMO_001', '演示收费站', 'RD001', 'OWN001', '演示路段',
  'K0+000', 'K10+000', '36.0', '117.0', '36.1', '117.1',
  10000, '2020-01-01 00:00:00', '2099-12-31 23:59:59',
  0, '0.00', 1, 1, 1
);

-- 2) 通行路径主表（path）
INSERT IGNORE INTO path (id, pass_id, plate_num, plate_color, en_time, ex_time, en_toll_lane_id, ex_toll_lane_id, en_toll_station_id, ex_toll_station_id)
VALUES ('PASS_LATE_001', 'PASS_LATE_001', '鲁A12345', 0, '2024-01-15 08:00:00', '2024-01-15 09:30:00', 'L001', 'L002', 'ST_DEMO_001', 'ST_DEMO_001');

-- 3) 入口交易（media_type=1→OBU, card_net=3701→山东省，供 is_single_province_etc / is_obu_billing_mode1 判定）
INSERT IGNORE INTO entrytransaction (id, pass_id, en_time, receive_time, en_toll_lane_id, media_type, card_net, vlp, vlpc)
VALUES ('ENTRY_PASS_LATE_001', 'PASS_LATE_001', '2024-01-15 08:00:00', '2024-01-15 08:00:10', 'L001', 1, '3701', '鲁A12345', 0);

-- 4) 出口交易（multi_province=0→单省, pay_type=1→ETC，供 is_single_province_etc 判定）
INSERT IGNORE INTO exittransaction (id, pass_id, ex_time, receive_time, multi_province, pay_type, fee, pay_fee, discount_fee)
VALUES ('EXIT_PASS_LATE_001', 'PASS_LATE_001', '2024-01-15 09:30:00', '2024-01-15 09:30:10', 0, 1, 1000, 950, 50);

-- 5) 路径明细 2 条（与拆分明细数量、收费单元、费用一致 → Path_integrity_normal）
INSERT IGNORE INTO pathdetail (id, pass_id, plate_num, plate_color, identify_point_id, fee, pay_fee, discount_fee, trans_time)
VALUES
  ('PD_PASS_LATE_001_1', 'PASS_LATE_001', '鲁A12345', 0, 'G001', '500', '475', '25', '2024-01-15 08:30:00'),
  ('PD_PASS_LATE_001_2', 'PASS_LATE_001', '鲁A12345', 0, 'G002', '500', '475', '25', '2024-01-15 09:00:00');

-- 6) 拆分明细 2 条（interval_id=G001,G002，与 pathdetail 一致；与门架 G001,G003 不一致 → check_route_consistency=false）
INSERT IGNORE INTO splitdetail (id, pass_id, transaction_id, interval_id, toll_interval_fee, toll_interval_pay_fee, toll_interval_discount_fee, split_flag, pro_split_time, pro_split_type, split_remark)
VALUES
  ('SD_PASS_LATE_001_1', 'PASS_LATE_001', 'TXN_PASS_LATE_001', 'G001', '500', '475', '25', 1, '2024-01-15 10:00:00', 1, 'demo'),
  ('SD_PASS_LATE_001_2', 'PASS_LATE_001', 'TXN_PASS_LATE_001', 'G002', '500', '475', '25', 1, '2024-01-15 10:00:00', 1, 'demo');

-- 7) 门架交易 2 条
-- 收费单元 G001,G003 与拆分明细 G001,G002 不一致 → check_route_consistency=false
-- last_gantry_hex 不衔接（01→99 断裂）→ check_gantry_hex_continuity=false → 根因-ETC门架不完整
-- 门架1 receive_time 晚于 pro_split_time(10:00) → detect_late_upload=true → 根因-门架延迟上传
INSERT IGNORE INTO gantrytransaction (id, trade_id, pass_id, gantry_hex, last_gantry_hex, toll_interval_id, fee, pay_fee, discount_fee, trans_time, receive_time)
VALUES
  ('GT_PASS_LATE_001_1', 'GT_PASS_LATE_001_1', 'PASS_LATE_001', '01', '00', 'G001', 475, 475, 25, '2024-01-15 08:30:00', '2024-01-15 11:00:00'),
  ('GT_PASS_LATE_001_2', 'GT_PASS_LATE_001_2', 'PASS_LATE_001', '02', '99', 'G003', 475, 475, 25, '2024-01-15 09:00:00', '2024-01-15 09:00:30');

SELECT 'Demo data for PASS_LATE_001 inserted.' AS result;
