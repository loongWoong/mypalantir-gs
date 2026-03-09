-- ================================================
-- 测试数据：OBU 拆分金额不一致场景
-- 预期推理链：
--   Cycle 1: obu_split_scope → in_obu_split_scope=true
--   Cycle 2: obu_split_fee_mismatch → obu_split_status="金额不一致"
--   Cycle 3: obu_fee_cause_rounding + obu_fee_cause_balance_anomaly
--            + vehicle_has_obu_split_abnormal + station_has_obu_split_abnormal
--   Cycle 4: 无新事实 → 终止
-- ================================================

-- 1. Passage 记录
INSERT INTO "TBL_PATH" ("ID", "PASSID", "VLP", "VLPC", "ENTIME", "EXTIME", "ENTOLLSTATIONID", "EXTOLLSTATIONID", "ENTOLLLANEID", "EXTOLLLANEID")
VALUES (
  'PASS_FEE_TEST_001',
  'PASS_FEE_TEST_001',
  '鲁B35B6J', 0,
  '2024-07-08 08:00:00',
  '2024-07-08 10:30:00',
  'S0085370010030',
  'S0027370030090',
  'S00853700100302010010',
  'S00273700300902020090'
);

-- 2. 出口交易（pay_type=1 → ETC, multi_province=0 → 单省）
INSERT INTO "TBL_EXITWASTE" (
  "ID", "PASSID", "EXTIME", "ENTIME", "LDATE", "RECEIVETIME",
  "ENTOLLSTATIONNAME", "EXTOLLSTATIONNAME", "EXTOLLLANE", "OPERNAME",
  "EXVLP", "EXVLPC", "EXVEHICLETYPE",
  "FEE", "PAYFEE", "DISCOUNTFEE", "TRANSFEE",
  "TOLLDISTANCE", "REALDISTANCE", "OVERTIME",
  "PAYTYPE", "PAYCARDTYPE", "MULTIPROVINCE",
  "TRANSCODE", "TRANSTYPE"
) VALUES (
  'EXIT_FEE_TEST_001', 'PASS_FEE_TEST_001',
  '2024-07-08 10:30:00', '2024-07-08 08:00:00', '2024-07-08', '2024-07-09',
  '山东海湾大桥站', '沂源北', '91', '收费员',
  '鲁B35B6J', 0, 1,
  670, 700, 30, 670,
  150000, 150000, 0,
  1, 2, 0,
  '0201', '09'
);

-- 3. 入口交易（media_type=1 → OBU, card_net=3701 → 山东省）
INSERT INTO "TBL_ENTRYWASTE" (
  "ID", "PASSID", "ENTIME", "ENTOLLSTATIONID", "ENTOLLLANE",
  "VLP", "VLPC", "VEHICLETYPE",
  "MEDIATYPE", "CARDNET",
  "TRANSCODE", "TRANSTYPE"
) VALUES (
  'ENTRY_FEE_TEST_001', 'PASS_FEE_TEST_001',
  '2024-07-08 08:00:00', 'S0085370010030', '01',
  '鲁B35B6J', 0, 1,
  1, '3701',
  '09', '09'
);

-- 4. 门架交易（3条，收费单元与拆分明细一致，但金额不一致）
-- Gantry 1: interval=G370101001, fee=240, pay_fee=250
INSERT INTO "TBL_GANTRYWASTE" (
  "ID", "TRADEID", "PASSID",
  "TRANSTIME", "LASTGANTRYTIME", "RECEIVETIME",
  "SNAPSHOTGANTRYHEX", "SNAPSHOTLASTGANTRYHEX",
  "SNAPSHOTPLATENUM", "SNAPSHOTPLATECOLOR", "SNAPSHOTVEHICLETYPE",
  "PAYFEE", "FEE", "DISCOUNTFEE", "TRANSFEE", "FEEMILEAGE",
  "TOLLINTERVALID", "GANTRYORDERNUM",
  "BALANCEBEFORE", "BALANCEAFTER",
  "TRANSTYPE", "MATCHSTATUS", "VALIDSTATUS", "DEALSTATUS"
) VALUES (
  'GANTRY_FEE_001', 'GANTRY_FEE_001', 'PASS_FEE_TEST_001',
  '2024-07-08 08:30:00', '2024-07-08 08:00:00', '2024-07-09',
  'A10001', '000000',
  '鲁B35B6J', 0, 1,
  250, 240, 10, 0, 5000,
  'G370101001', 1,
  10000, 9760,
  '09', 0, 0, 1
);

-- Gantry 2: interval=G370101002, fee=280, pay_fee=300
INSERT INTO "TBL_GANTRYWASTE" (
  "ID", "TRADEID", "PASSID",
  "TRANSTIME", "LASTGANTRYTIME", "RECEIVETIME",
  "SNAPSHOTGANTRYHEX", "SNAPSHOTLASTGANTRYHEX",
  "SNAPSHOTPLATENUM", "SNAPSHOTPLATECOLOR", "SNAPSHOTVEHICLETYPE",
  "PAYFEE", "FEE", "DISCOUNTFEE", "TRANSFEE", "FEEMILEAGE",
  "TOLLINTERVALID", "GANTRYORDERNUM",
  "BALANCEBEFORE", "BALANCEAFTER",
  "TRANSTYPE", "MATCHSTATUS", "VALIDSTATUS", "DEALSTATUS"
) VALUES (
  'GANTRY_FEE_002', 'GANTRY_FEE_002', 'PASS_FEE_TEST_001',
  '2024-07-08 09:00:00', '2024-07-08 08:30:00', '2024-07-09',
  'A10002', 'A10001',
  '鲁B35B6J', 0, 1,
  300, 280, 20, 0, 5500,
  'G370101002', 2,
  9760, 9480,
  '09', 0, 0, 1
);

-- Gantry 3: interval=G370101003, fee=150, pay_fee=150
-- balance_before=9000 (不等于上一条的 balance_after=9480) → 余额不连续!
INSERT INTO "TBL_GANTRYWASTE" (
  "ID", "TRADEID", "PASSID",
  "TRANSTIME", "LASTGANTRYTIME", "RECEIVETIME",
  "SNAPSHOTGANTRYHEX", "SNAPSHOTLASTGANTRYHEX",
  "SNAPSHOTPLATENUM", "SNAPSHOTPLATECOLOR", "SNAPSHOTVEHICLETYPE",
  "PAYFEE", "FEE", "DISCOUNTFEE", "TRANSFEE", "FEEMILEAGE",
  "TOLLINTERVALID", "GANTRYORDERNUM",
  "BALANCEBEFORE", "BALANCEAFTER",
  "TRANSTYPE", "MATCHSTATUS", "VALIDSTATUS", "DEALSTATUS"
) VALUES (
  'GANTRY_FEE_003', 'GANTRY_FEE_003', 'PASS_FEE_TEST_001',
  '2024-07-08 09:30:00', '2024-07-08 09:00:00', '2024-07-09',
  'A10003', 'A10002',
  '鲁B35B6J', 0, 1,
  150, 150, 0, 0, 4000,
  'G370101003', 3,
  9000, 8850,
  '09', 0, 0, 1
);

-- 5. 拆分明细（3条，interval_id 与门架交易一致，但 toll_interval_fee 不同）
INSERT INTO "TBL_SPLITDETAIL" (
  "pass_id", "transaction_id", "interval_id",
  "toll_interval_fee", "toll_interval_pay_fee", "toll_interval_discount_fee",
  "split_flag", "pro_split_time", "pro_split_type"
) VALUES
  ('PASS_FEE_TEST_001', 'EXIT_FEE_TEST_001', 'G370101001',
   '230', '250', '20', 1, '2025-05-12 02:54:24', 1),
  ('PASS_FEE_TEST_001', 'EXIT_FEE_TEST_001', 'G370101002',
   '270', '300', '30', 1, '2025-05-12 02:54:24', 1),
  ('PASS_FEE_TEST_001', 'EXIT_FEE_TEST_001', 'G370101003',
   '140', '150', '10', 1, '2025-05-12 02:54:24', 1);

-- 6. 路径明细（3条，identify_point_id 与 SplitDetail 的 interval_id 一致，fee 与 split fee 一致）
INSERT INTO "TBL_PATHDETAIL" (
  "ID", "PASSID", "PLATENUM", "IDENTIFYPOINTID", "TRANSTIME", "FEE"
) VALUES
  ('PATH_FEE_001', 'PASS_FEE_TEST_001', '鲁B35B6J', 'G370101001', '2024-07-08 08:30:00', '230'),
  ('PATH_FEE_002', 'PASS_FEE_TEST_001', '鲁B35B6J', 'G370101002', '2024-07-08 09:00:00', '270'),
  ('PATH_FEE_003', 'PASS_FEE_TEST_001', '鲁B35B6J', 'G370101003', '2024-07-08 09:30:00', '140');

-- ================================================
-- 数据校验（预期值）：
--
-- 衍生属性：
--   path_detail_count = 3, split_detail_count = 3, gantry_transaction_count = 3
--   detail_count_matched = true (3 == 3)
--   interval_set_matched = true ([G370101001,G370101002,G370101003] 一致)
--   fee_matched = true (230+270+140 == 230+270+140 = 640)
--
-- 函数返回值：
--   is_single_province_etc: pay_type=1, multi_province=0, card_net=3701 → true
--   is_obu_billing_mode1: media_type=1 → true
--   check_route_consistency: splits=[G370101001,G370101002,G370101003]
--                            gantry=[G370101001,G370101002,G370101003] → true
--   check_fee_detail_consistency: G370101001: split=230 vs gantry=240 → |10|>0.01 → false
--   check_rounding_mismatch:
--     totalPayFee = 250+300+150 = 700
--     totalFee = 240+280+150 = 670
--     roundedFee = round(700*0.95) = round(665) = 665
--     expectedFee = min(665, 670) = 665
--     665 != 670 → true
--     |665-670|=5 <= |665-700|=35 → true
--     → returns true
--   check_balance_continuity:
--     gantry1.balance_after=9760, gantry2.balance_before=9760 → OK
--     gantry2.balance_after=9480, gantry3.balance_before=9000 → 不等! → false
-- ================================================
