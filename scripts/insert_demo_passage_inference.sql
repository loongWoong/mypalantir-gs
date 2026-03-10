-- ============================================================
-- 演示数据：PASS_LATE_001
-- 用于推理引擎得到 5 cycles, 6 rules fired 的结果：
--   1) 通行路径完整性正常  2) OBU拆分分析范围  3) OBU拆分路径不一致
--   4) 根因-ETC门架路径不完整  5) 收费站存在OBU拆分异常  6) 根因-门架延迟上传
-- 表与列名严格对应 scripts/DDL.sql 中的定义（path/entrytransaction 小写，exittransaction/pathdetail/gantrytransaction/tollsection 大写）
-- ============================================================

-- 1) 收费路段（tollsection：DDL 列为 ID, ROADID, NAME 等大写）
INSERT IGNORE INTO tollsection (ID, NAME)
VALUES ('ST_DEMO_001', '演示收费站');

-- 2) 通行路径主表（path：DDL 列为 id, pass_id, plate_num, plate_color, en_time, ex_time, en_toll_lane_id, ex_toll_lane_id, en_toll_station_id, ex_toll_station_id 小写）
INSERT IGNORE INTO path (id, pass_id, plate_num, plate_color, en_time, ex_time, en_toll_lane_id, ex_toll_lane_id, en_toll_station_id, ex_toll_station_id)
VALUES ('PASS_LATE_001', 'PASS_LATE_001', '鲁A12345', 0, '2024-01-15 08:00:00', '2024-01-15 09:30:00', 'L001', 'L002', 'ST_DEMO_001', 'ST_DEMO_001');

-- 3) 入口交易（entrytransaction：DDL 列为 id, pass_id, en_time, receive_time, en_toll_lane_id, media_type, vlp, vlpc 小写）
INSERT IGNORE INTO entrytransaction (id, pass_id, en_time, receive_time, en_toll_lane_id, media_type, vlp, vlpc)
VALUES ('ENTRY_PASS_LATE_001', 'PASS_LATE_001', '2024-01-15 08:00:00', '2024-01-15 08:00:10', 'L001', 1, '鲁A12345', 0);

-- 4) 出口交易（exittransaction：DDL 列为 ID, PASSID, EXTIME, RECEIVETIME, MULTIPROVINCE, PAYTYPE, FEE, PAYFEE, DISCOUNTFEE 大写）
INSERT IGNORE INTO exittransaction (ID, PASSID, EXTIME, RECEIVETIME, MULTIPROVINCE, PAYTYPE, FEE, PAYFEE, DISCOUNTFEE)
VALUES ('EXIT_PASS_LATE_001', 'PASS_LATE_001', '2024-01-15 09:30:00', '2024-01-15 09:30:10', 0, 1, 1000, 950, 50);

-- 5) 路径明细 2 条（pathdetail：DDL 列为 ID, PASSID, PLATENUM, PLATECOLOR, IDENTIFYPOINTID, FEE, PAYFEE, DISCOUNTFEE, TRANSTIME 大写）
INSERT IGNORE INTO pathdetail (ID, PASSID, PLATENUM, PLATECOLOR, IDENTIFYPOINTID, FEE, PAYFEE, DISCOUNTFEE, TRANSTIME)
VALUES
  ('PD_PASS_LATE_001_1', 'PASS_LATE_001', '鲁A12345', 0, 'G001', '500', '475', '25', '2024-01-15 08:30:00'),
  ('PD_PASS_LATE_001_2', 'PASS_LATE_001', '鲁A12345', 0, 'G002', '500', '475', '25', '2024-01-15 09:00:00');

-- 6) 拆分明细 2 条（splitdetail：DDL 列为 id, pass_id, transaction_id, interval_id, toll_interval_fee, toll_interval_pay_fee, toll_interval_discount_fee, split_flag, pro_split_time, pro_split_type, split_remark 小写，主键 id）
INSERT IGNORE INTO splitdetail (id, pass_id, transaction_id, interval_id, toll_interval_fee, toll_interval_pay_fee, toll_interval_discount_fee, split_flag, pro_split_time, pro_split_type, split_remark)
VALUES
  ('SD_PASS_LATE_001_1', 'PASS_LATE_001', 'TXN_PASS_LATE_001', 'G001', '500', '475', '25', 1, '2024-01-15 10:00:00', 1, 'demo'),
  ('SD_PASS_LATE_001_2', 'PASS_LATE_001', 'TXN_PASS_LATE_001', 'G002', '500', '475', '25', 1, '2024-01-15 10:00:00', 1, 'demo');

-- 7) 门架交易 2 条（gantrytransaction：DDL 列为 TRADEID, PASSID, GANTRYHEX, LASTGANTRYHEX, TOLLINTERVALID, FEE, PAYFEE, DISCOUNTFEE, TRANSTIME, RECEIVETIME 大写）
-- 收费单元与拆分明细不一致(G001,G003 vs G001,G002)→check_route_consistency=false；HEX 不连续→check_gantry_hex_continuity=false；门架1 receive_time 晚于 pro_split_time→detect_late_upload=true
INSERT IGNORE INTO gantrytransaction (TRADEID, PASSID, GANTRYHEX, LASTGANTRYHEX, TOLLINTERVALID, FEE, PAYFEE, DISCOUNTFEE, TRANSTIME, RECEIVETIME)
VALUES
  ('GT_PASS_LATE_001_1', 'PASS_LATE_001', '01', '00', 'G001', 475, 475, 25, '2024-01-15 08:30:00', '2024-01-15 11:00:00'),
  ('GT_PASS_LATE_001_2', 'PASS_LATE_001', '02', '99', 'G003', 475, 475, 25, '2024-01-15 09:00:00', '2024-01-15 09:00:30');

SELECT 'Demo data for PASS_LATE_001 inserted.' AS result;
