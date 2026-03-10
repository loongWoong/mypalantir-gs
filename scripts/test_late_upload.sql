-- ================================================
-- 测试数据：OBU 拆分路径不一致 + ETC门架不完整 + 门架延迟上传
-- 预期推理链：
--   Cycle 1: obu_split_scope → in_obu_split_scope=true
--   Cycle 2: obu_split_route_mismatch → obu_split_status="路径不一致"
--   Cycle 3: obu_route_cause_etc_incomplete → obu_route_cause="ETC门架不完整"
--   Cycle 4: obu_route_cause_late_upload → obu_incomplete_reason="门架延迟上传"
--            + vehicle/station 传播
--   Cycle 5: 无新事实 → 终止
-- ================================================

-- 1. Passage（通行路径）
INSERT INTO "TBL_PATH" ("ID", "PASSID", "PLATENUM", "PLATECOLOR",
  "ENTIME", "EXTIME", "ENTOLLSTATIONID", "EXTOLLSTATIONID",
  "ENTOLLLANEID", "EXTOLLLANEID")
VALUES (
  'PASS_LATE_001', 'PASS_LATE_001', '鲁A12345', 0,
  '2024-07-08 08:00:00', '2024-07-08 10:30:00',
  'S0085370010030', 'S0027370030090',
  'S00853700100302010010', 'S00273700300902020090'
);

-- 2. 入口交易（MEDIATYPE=1 → OBU, CARDNET='3701' → 山东省）
INSERT INTO "TBL_ENTRYWASTE" (
  "ID", "PASSID", "ENTIME", "ENTOLLSTATIONID", "ENTOLLLANE",
  "VLP", "VLPC", "VEHICLETYPE",
  "MEDIATYPE", "CARDNET",
  "TRANSCODE", "TRANSTYPE"
) VALUES (
  'ENTRY_LATE_001', 'PASS_LATE_001',
  '2024-07-08 08:00:00', 'S0085370010030', '01',
  '鲁A12345', 0, 1,
  1, '3701',
  '09', '09'
);

-- 3. 出口交易（PAYTYPE=1 → ETC, MULTIPROVINCE=0 → 单省）
INSERT INTO "TBL_EXITWASTE" (
  "ID", "PASSID", "EXTIME", "ENTIME", "LDATE", "RECEIVETIME",
  "ENTOLLSTATIONNAME", "EXTOLLSTATIONNAME", "EXTOLLLANE", "OPERNAME",
  "EXVLP", "EXVLPC", "EXVEHICLETYPE",
  "FEE", "PAYFEE", "DISCOUNTFEE", "TRANSFEE",
  "TOLLDISTANCE", "REALDISTANCE", "OVERTIME",
  "PAYTYPE", "PAYCARDTYPE", "MULTIPROVINCE",
  "TRANSCODE", "TRANSTYPE"
) VALUES (
  'EXIT_LATE_001', 'PASS_LATE_001',
  '2024-07-08 10:30:00', '2024-07-08 08:00:00', '2024-07-08', '2024-07-09',
  '青岛站', '沂源北站', '91', '收费员',
  '鲁A12345', 0, 1,
  670, 700, 30, 670,
  150000, 150000, 0,
  1, 2, 0,
  '0201', '09'
);

-- 4. 门架交易：只有 2 条（缺少 G002），且 G003 延迟上传
--
-- 数据设计要点：
--   - 只有 order=1 和 order=3，缺少 order=2 → 门架不完整
--   - G001.GANTRYHEX=A10001, G003.LASTGANTRYHEX=A10002 → HEX不连续（A10001≠A10002）
--   - G003.RECEIVETIME=2025-05-13 > pro_split_time=2025-05-12 → 延迟上传

-- Gantry 1: interval=G370101001, order=1
INSERT INTO "TBL_GANTRYWASTE" (
  "TRADEID", "PASSID",
  "TRANSTIME", "LASTGANTRYTIME", "RECEIVETIME",
  "GANTRYHEX", "LASTGANTRYHEX",
  "VLP", "VLPC", "VEHICLETYPE",
  "PAYFEE", "FEE", "DISCOUNTFEE", "TRANSFEE", "FEEMILEAGE",
  "TOLLINTERVALID", "GANTRYORDERNUM",
  "BALANCEBEFORE", "BALANCEAFTER",
  "TRANSTYPE", "MATCHSTATUS", "VALIDSTATUS", "DEALSTATUS"
) VALUES (
  'GANTRY_LATE_001', 'PASS_LATE_001',
  '2024-07-08 08:30:00', '2024-07-08 08:00:00',
  '2024-07-08 09:00:00',
  'A10001', '000000',
  '鲁A12345', 0, 1,
  250, 240, 10, 0, 5000,
  'G370101001', 1,
  10000, 9760,
  '09', 0, 0, 1
);

-- Gantry 3: interval=G370101003, order=3（跳过了 order=2）
-- LASTGANTRYHEX=A10002 ≠ 上一条 GANTRYHEX=A10001 → HEX不连续
-- RECEIVETIME=2025-05-13 > pro_split_time=2025-05-12 → 延迟上传
INSERT INTO "TBL_GANTRYWASTE" (
  "TRADEID", "PASSID",
  "TRANSTIME", "LASTGANTRYTIME", "RECEIVETIME",
  "GANTRYHEX", "LASTGANTRYHEX",
  "VLP", "VLPC", "VEHICLETYPE",
  "PAYFEE", "FEE", "DISCOUNTFEE", "TRANSFEE", "FEEMILEAGE",
  "TOLLINTERVALID", "GANTRYORDERNUM",
  "BALANCEBEFORE", "BALANCEAFTER",
  "TRANSTYPE", "MATCHSTATUS", "VALIDSTATUS", "DEALSTATUS"
) VALUES (
  'GANTRY_LATE_003', 'PASS_LATE_001',
  '2024-07-08 09:30:00', '2024-07-08 09:00:00',
  '2025-05-13 10:00:00',
  'A10003', 'A10002',
  '鲁A12345', 0, 1,
  150, 150, 0, 0, 4000,
  'G370101003', 3,
  9480, 9330,
  '09', 0, 0, 1
);

-- 5. 拆分明细：3 条（包含 G002，但门架交易里缺少 G002 → 路径不一致）
INSERT INTO "TBL_SPLITDETAIL" (
  "pass_id", "transaction_id", "interval_id",
  "toll_interval_fee", "toll_interval_pay_fee", "toll_interval_discount_fee",
  "split_flag", "pro_split_time", "pro_split_type"
) VALUES
  ('PASS_LATE_001', 'EXIT_LATE_001', 'G370101001',
   '240', '250', '10', 1, '2025-05-12 02:54:24', 1),
  ('PASS_LATE_001', 'EXIT_LATE_001', 'G370101002',
   '280', '300', '20', 1, '2025-05-12 02:54:24', 1),
  ('PASS_LATE_001', 'EXIT_LATE_001', 'G370101003',
   '150', '150', '0', 1, '2025-05-12 02:54:24', 1);

-- 6. 路径明细（与拆分明细对应）
INSERT INTO "TBL_PATHDETAIL" (
  "ID", "PASSID", "PLATENUM", "IDENTIFYPOINTID", "TRANSTIME", "FEE"
) VALUES
  ('PATH_LATE_001', 'PASS_LATE_001', '鲁A12345', 'G370101001', '2024-07-08 08:30:00', '240'),
  ('PATH_LATE_002', 'PASS_LATE_001', '鲁A12345', 'G370101002', '2024-07-08 09:00:00', '280'),
  ('PATH_LATE_003', 'PASS_LATE_001', '鲁A12345', 'G370101003', '2024-07-08 09:30:00', '150');

-- ================================================
-- 数据校验（预期值）：
--
-- 函数返回值：
--   is_single_province_etc:     MEDIATYPE=1, CARDNET='3701', PAYTYPE=1, MULTIPROVINCE=0 → true
--   is_obu_billing_mode1:       MEDIATYPE=1 → true
--   check_route_consistency:    splits=[G370101001,G370101002,G370101003]
--                               gantry=[G370101001,G370101003] → 不等 → false
--   detect_duplicate_intervals: [G370101001,G370101003] 无重复 → false
--   check_gantry_hex_continuity:
--     按 order 排序: [order=1, order=3]
--     G001.GANTRYHEX=A10001 vs G003.LASTGANTRYHEX=A10002
--     A10001 ≠ A10002 → false
--   detect_late_upload:
--     G003.RECEIVETIME=2025-05-13 10:00:00
--     pro_split_time=2025-05-12 02:54:24
--     2025-05-13 > 2025-05-12 → true
--
-- 推理链：
--   Cycle 1: obu_split_scope         → in_obu_split_scope(?p, true)
--   Cycle 2: obu_split_route_mismatch → obu_split_status(?p, "路径不一致")
--   Cycle 3: obu_route_cause_etc_incomplete → obu_route_cause(?p, "ETC门架不完整")
--   Cycle 4: obu_route_cause_late_upload → obu_incomplete_reason(?p, "门架延迟上传")
--            vehicle_has_obu_split_abnormal → has_obu_split_abnormal(?v, true)
--            station_has_obu_split_abnormal → has_obu_split_abnormal(?s, true)
--   Cycle 5: 无新事实 → 终止
-- ================================================
