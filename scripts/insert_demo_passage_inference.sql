-- ============================================================
-- 演示数据：推理引擎验证
-- 表与列名严格对应 scripts/DDL.sql（小写、下划线）。
-- 数据逻辑参考 test_late_upload.sql（路径不一致 + 门架不完整 + 延迟上传）
--         与 test_fee_mismatch.sql（金额不一致 + 取整/余额根因）。
-- ============================================================

-- ---------------------------------------------------------------------------
-- 场景一：PASS_LATE_001 — 路径不一致 + ETC门架不完整 + 门架延迟上传
-- 预期推理：obu_split_scope → obu_split_route_mismatch → obu_route_cause_etc_incomplete
--           → obu_route_cause_late_upload（+ vehicle/station 传播）
-- ---------------------------------------------------------------------------

-- 1) 通行路径主表 path
INSERT INTO `path` (id, pass_id, plate_num, plate_color, en_time, ex_time, en_toll_lane_id, ex_toll_lane_id, en_toll_station_id, ex_toll_station_id, l_date)
VALUES (
  'PASS_LATE_001', 'PASS_LATE_001', '鲁A12345', 0,
  '2024-07-08 08:00:00', '2024-07-08 10:30:00',
  'S00853700100302010010', 'S00273700300902020090',
  'S0085370010030', 'S0027370030090',
  '2024-07-08'
) ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 2) 入口交易（media_type=1→OBU, card_net=3701→山东省）
INSERT INTO `entrytransaction` (id, pass_id, en_time, receive_time, en_toll_lane_id, vlp, vlpc, media_type, card_net, trans_code, trans_type)
VALUES (
  'ENTRY_LATE_001', 'PASS_LATE_001',
  '2024-07-08 08:00:00', '2024-07-08 08:00:10', 'S00853700100302010010',
  '鲁A12345', 0, 1, '3701', '09', '09'
) ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 3) 出口交易（pay_type=1→ETC, multi_province=0→单省）
INSERT INTO `exittransaction` (id, pass_id, ex_time, en_time, l_date, receive_time, ex_toll_station_name, ex_toll_lane_id, ex_vlp, ex_vlpc, fee, pay_fee, discount_fee, pay_type, pay_card_type, multi_province, trans_code, trans_type)
VALUES (
  'EXIT_LATE_001', 'PASS_LATE_001',
  '2024-07-08 10:30:00', '2024-07-08 08:00:00', '2024-07-08', '2024-07-09 00:00:00',
  '沂源北站', 'S00273700300902020090', '鲁A12345', 0,
  670, 700, 30, 1, 2, 0, '0201', '09'
) ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 4) 门架交易：仅 2 条（缺 G370101002）→ 路径不一致；HEX 不连续；G003 接收晚于拆分时间
-- G001: toll_interval_id=G370101001, gantry_hex=A10001, last_gantry_hex=000000
INSERT INTO `gantrytransaction` (id, trade_id, pass_id, gantry_hex, last_gantry_hex, toll_interval_id, trans_time, last_gantry_time, receive_time, fee, pay_fee, discount_fee, balance_before, balance_after, vlp, vlpc, vehicle_type)
VALUES (
  'GANTRY_LATE_001', 'GANTRY_LATE_001', 'PASS_LATE_001',
  'A10001', '000000', 'G370101001',
  '2024-07-08 08:30:00', '2024-07-08 08:00:00', '2024-07-08 09:00:00',
  240, 250, 10, 10000, 9760, '鲁A12345', 0, 1
) ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- G003: toll_interval_id=G370101003, last_gantry_hex=A10002（与上条 A10001 不衔接）→ ETC门架不完整
-- receive_time=2025-05-13 > pro_split_time=2025-05-12 → 延迟上传
INSERT INTO `gantrytransaction` (id, trade_id, pass_id, gantry_hex, last_gantry_hex, toll_interval_id, trans_time, last_gantry_time, receive_time, fee, pay_fee, discount_fee, balance_before, balance_after, vlp, vlpc, vehicle_type)
VALUES (
  'GANTRY_LATE_003', 'GANTRY_LATE_003', 'PASS_LATE_001',
  'A10003', 'A10002', 'G370101003',
  '2024-07-08 09:30:00', '2024-07-08 09:00:00', '2025-05-13 10:00:00',
  150, 150, 0, 9480, 9330, '鲁A12345', 0, 1
) ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 5) 拆分明细：3 条（G001,G002,G003），门架只有 G001,G003 → check_route_consistency=false
INSERT INTO `splitdetail` (id, pass_id, transaction_id, interval_id, toll_interval_fee, toll_interval_pay_fee, toll_interval_discount_fee, split_flag, pro_split_time, pro_split_type, split_remark)
VALUES
  ('SD_LATE_001_1', 'PASS_LATE_001', 'EXIT_LATE_001', 'G370101001', '240', '250', '10', 1, '2025-05-12 02:54:24', 1, 'demo'),
  ('SD_LATE_001_2', 'PASS_LATE_001', 'EXIT_LATE_001', 'G370101002', '280', '300', '20', 1, '2025-05-12 02:54:24', 1, 'demo'),
  ('SD_LATE_001_3', 'PASS_LATE_001', 'EXIT_LATE_001', 'G370101003', '150', '150', '0', 1, '2025-05-12 02:54:24', 1, 'demo')
ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 6) 路径明细：3 条，与拆分明细一一对应（数量、收费单元、费用一致 → 通行路径完整性正常）
INSERT INTO `pathdetail` (id, pass_id, plate_num, plate_color, identify_point_id, fee, trans_time)
VALUES
  ('PD_LATE_001', 'PASS_LATE_001', '鲁A12345', 0, 'G370101001', '240', '2024-07-08 08:30:00'),
  ('PD_LATE_002', 'PASS_LATE_001', '鲁A12345', 0, 'G370101002', '280', '2024-07-08 09:00:00'),
  ('PD_LATE_003', 'PASS_LATE_001', '鲁A12345', 0, 'G370101003', '150', '2024-07-08 09:30:00')
ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- ---------------------------------------------------------------------------
-- 场景二：PASS_FEE_001 — 金额不一致（逐单元差异 + 余额不连续）
-- 预期推理：obu_split_scope → obu_split_fee_mismatch → obu_fee_cause_rounding / obu_fee_cause_balance_anomaly
-- ---------------------------------------------------------------------------

-- 1) path
INSERT INTO `path` (id, pass_id, plate_num, plate_color, en_time, ex_time, en_toll_lane_id, ex_toll_lane_id, en_toll_station_id, ex_toll_station_id, l_date)
VALUES (
  'PASS_FEE_001', 'PASS_FEE_001', '鲁B35B6J', 0,
  '2024-07-08 08:00:00', '2024-07-08 10:30:00',
  'S00853700100302010010', 'S00273700300902020090',
  'S0085370010030', 'S0027370030090', '2024-07-08'
) ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 2) 入口
INSERT INTO `entrytransaction` (id, pass_id, en_time, receive_time, en_toll_lane_id, vlp, vlpc, media_type, card_net, trans_code, trans_type)
VALUES (
  'ENTRY_FEE_001', 'PASS_FEE_001', '2024-07-08 08:00:00', '2024-07-08 08:00:10', 'S00853700100302010010', '鲁B35B6J', 0, 1, '3701', '09', '09'
) ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 3) 出口
INSERT INTO `exittransaction` (id, pass_id, ex_time, en_time, l_date, receive_time, ex_toll_station_name, ex_toll_lane_id, ex_vlp, ex_vlpc, fee, pay_fee, discount_fee, pay_type, pay_card_type, multi_province, trans_code, trans_type)
VALUES (
  'EXIT_FEE_001', 'PASS_FEE_001', '2024-07-08 10:30:00', '2024-07-08 08:00:00', '2024-07-08', '2024-07-09 00:00:00',
  '沂源北', 'S00273700300902020090', '鲁B35B6J', 0, 670, 700, 30, 1, 2, 0, '0201', '09'
) ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 4) 门架 3 条：收费单元与拆分一致，但第三笔 balance_before=9000 ≠ 上笔 balance_after=9480 → check_balance_continuity=false
INSERT INTO `gantrytransaction` (id, trade_id, pass_id, gantry_hex, last_gantry_hex, toll_interval_id, trans_time, last_gantry_time, receive_time, fee, pay_fee, discount_fee, balance_before, balance_after, vlp, vlpc, vehicle_type)
VALUES
  ('GANTRY_FEE_001', 'GANTRY_FEE_001', 'PASS_FEE_001', 'A10001', '000000', 'G370101001', '2024-07-08 08:30:00', '2024-07-08 08:00:00', '2024-07-09 00:00:00', 240, 250, 10, 10000, 9760, '鲁B35B6J', 0, 1),
  ('GANTRY_FEE_002', 'GANTRY_FEE_002', 'PASS_FEE_001', 'A10002', 'A10001', 'G370101002', '2024-07-08 09:00:00', '2024-07-08 08:30:00', '2024-07-09 00:00:00', 280, 300, 20, 9760, 9480, '鲁B35B6J', 0, 1),
  ('GANTRY_FEE_003', 'GANTRY_FEE_003', 'PASS_FEE_001', 'A10003', 'A10002', 'G370101003', '2024-07-08 09:30:00', '2024-07-08 09:00:00', '2024-07-09 00:00:00', 150, 150, 0, 9000, 8850, '鲁B35B6J', 0, 1)
ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 5) 拆分明细：与门架 interval 一致，但 toll_interval_fee 与门架 fee 有差异 → check_fee_detail_consistency=false
INSERT INTO `splitdetail` (id, pass_id, transaction_id, interval_id, toll_interval_fee, toll_interval_pay_fee, toll_interval_discount_fee, split_flag, pro_split_time, pro_split_type, split_remark)
VALUES
  ('SD_FEE_001_1', 'PASS_FEE_001', 'EXIT_FEE_001', 'G370101001', '230', '250', '20', 1, '2025-05-12 02:54:24', 1, 'demo'),
  ('SD_FEE_001_2', 'PASS_FEE_001', 'EXIT_FEE_001', 'G370101002', '270', '300', '30', 1, '2025-05-12 02:54:24', 1, 'demo'),
  ('SD_FEE_001_3', 'PASS_FEE_001', 'EXIT_FEE_001', 'G370101003', '140', '150', '10', 1, '2025-05-12 02:54:24', 1, 'demo')
ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- 6) 路径明细：与拆分明细一致（path_fee_total 与 split 一致，用于衍生属性）
INSERT INTO `pathdetail` (id, pass_id, plate_num, plate_color, identify_point_id, fee, trans_time)
VALUES
  ('PD_FEE_001', 'PASS_FEE_001', '鲁B35B6J', 0, 'G370101001', '230', '2024-07-08 08:30:00'),
  ('PD_FEE_002', 'PASS_FEE_001', '鲁B35B6J', 0, 'G370101002', '270', '2024-07-08 09:00:00'),
  ('PD_FEE_003', 'PASS_FEE_001', '鲁B35B6J', 0, 'G370101003', '140', '2024-07-08 09:30:00')
ON DUPLICATE KEY UPDATE pass_id = VALUES(pass_id);

-- ============================================================
-- 数据校验预期（简要）
-- PASS_LATE_001: detail_count_matched=true, interval_set_matched=true, fee_matched=true
--                check_route_consistency=false, detect_duplicate_intervals=false,
--                check_gantry_hex_continuity=false, detect_late_upload=true
--                → 路径完整性正常 + OBU路径不一致 + 根因ETC门架不完整 + 门架延迟上传
-- PASS_FEE_001:  check_route_consistency=true, check_fee_detail_consistency=false
--                check_balance_continuity=false（第三笔余额不衔接）
--                → OBU金额不一致 + 根因卡内累计金额异常 / 四舍五入取整差异（视 check_rounding_mismatch）
-- ============================================================
SELECT 'Demo data for PASS_LATE_001 and PASS_FEE_001 inserted.' AS result;
