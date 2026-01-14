-- ============================================
-- 基于关系 splitdetail_to_exclearresultcash 更新 ExClearResultCash 表数据
-- 关系定义：
--   - source_type: SplitDetail (表: TBL_SPLITDETAIL)
--   - target_type: ExClearResultCash (表: TBL_EXCLEARRESULTCASH)
--   - cardinality: one-to-many
--   - property_mappings:
--       interval_id -> toll_interval_id
--       toll_interval_fee -> amount
--       toll_interval_pay_fee -> charge_amount
--       toll_interval_discount_fee -> discount_amount
-- ============================================

-- 注意：H2数据库字段名大小写敏感，根据实际表结构调整字段名大小写
-- TBL_SPLITDETAIL 使用小写字段名：interval_id, toll_interval_fee 等
-- TBL_EXCLEARRESULTCASH 可能使用大写字段名：TOLL_INTERVAL_ID, AMOUNT 等

-- 方法1: 使用 UPDATE 语句更新已存在的 ExClearResultCash 记录（兼容H2）
-- 对于一对多关系，使用 SUM 聚合同一个 interval_id 的多条记录
UPDATE TBL_EXCLEARRESULTCASH
SET 
    AMOUNT = (
        SELECT COALESCE(SUM(CAST(sd.toll_interval_fee AS DECIMAL(18,2))), 0)
        FROM TBL_SPLITDETAIL sd 
        WHERE sd.interval_id = TBL_EXCLEARRESULTCASH.TOLL_INTERVAL_ID
    ),
    CHARGE_AMOUNT = (
        SELECT COALESCE(SUM(CAST(sd.toll_interval_pay_fee AS DECIMAL(18,2))), 0)
        FROM TBL_SPLITDETAIL sd 
        WHERE sd.interval_id = TBL_EXCLEARRESULTCASH.TOLL_INTERVAL_ID
    ),
    DISCOUNT_AMOUNT = (
        SELECT COALESCE(SUM(CAST(sd.toll_interval_discount_fee AS DECIMAL(18,2))), 0)
        FROM TBL_SPLITDETAIL sd 
        WHERE sd.interval_id = TBL_EXCLEARRESULTCASH.TOLL_INTERVAL_ID
    ),
    LAST_TIME = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1 
    FROM TBL_SPLITDETAIL sd 
    WHERE sd.interval_id = TBL_EXCLEARRESULTCASH.TOLL_INTERVAL_ID
);

-- 如果字段名是小写的，使用以下版本：
-- UPDATE TBL_EXCLEARRESULTCASH
-- SET 
--     amount = (
--         SELECT COALESCE(SUM(CAST(sd.toll_interval_fee AS DECIMAL(18,2))), 0)
--         FROM TBL_SPLITDETAIL sd 
--         WHERE sd.interval_id = TBL_EXCLEARRESULTCASH.toll_interval_id
--     ),
--     charge_amount = (
--         SELECT COALESCE(SUM(CAST(sd.toll_interval_pay_fee AS DECIMAL(18,2))), 0)
--         FROM TBL_SPLITDETAIL sd 
--         WHERE sd.interval_id = TBL_EXCLEARRESULTCASH.toll_interval_id
--     ),
--     discount_amount = (
--         SELECT COALESCE(SUM(CAST(sd.toll_interval_discount_fee AS DECIMAL(18,2))), 0)
--         FROM TBL_SPLITDETAIL sd 
--         WHERE sd.interval_id = TBL_EXCLEARRESULTCASH.toll_interval_id
--     ),
--     last_time = CURRENT_TIMESTAMP
-- WHERE EXISTS (
--     SELECT 1 
--     FROM TBL_SPLITDETAIL sd 
--     WHERE sd.interval_id = TBL_EXCLEARRESULTCASH.toll_interval_id
-- );

-- ============================================
-- 验证更新结果
-- ============================================

-- 查询更新后的数据，检查关系是否正确建立
-- 注意：根据实际字段名大小写调整
SELECT 
    sd.interval_id AS split_interval_id,
    sd.toll_interval_fee AS split_fee,
    sd.toll_interval_pay_fee AS split_pay_fee,
    sd.toll_interval_discount_fee AS split_discount_fee,
    ec.TOLL_INTERVAL_ID AS clear_interval_id,
    ec.AMOUNT AS clear_amount,
    ec.CHARGE_AMOUNT AS clear_charge_amount,
    ec.DISCOUNT_AMOUNT AS clear_discount_amount,
    CASE 
        WHEN sd.interval_id = ec.TOLL_INTERVAL_ID 
             AND CAST(sd.toll_interval_fee AS DECIMAL(18,2)) = ec.AMOUNT
             AND CAST(sd.toll_interval_pay_fee AS DECIMAL(18,2)) = ec.CHARGE_AMOUNT
             AND CAST(sd.toll_interval_discount_fee AS DECIMAL(18,2)) = ec.DISCOUNT_AMOUNT
        THEN '匹配成功'
        ELSE '数据不匹配'
    END AS match_status
FROM TBL_SPLITDETAIL sd
LEFT JOIN TBL_EXCLEARRESULTCASH ec ON sd.interval_id = ec.TOLL_INTERVAL_ID
FETCH FIRST 100 ROWS ONLY;

-- 按 interval_id 聚合验证（因为是一对多关系）
SELECT 
    sd.interval_id AS split_interval_id,
    SUM(CAST(sd.toll_interval_fee AS DECIMAL(18,2))) AS total_split_fee,
    SUM(CAST(sd.toll_interval_pay_fee AS DECIMAL(18,2))) AS total_split_pay_fee,
    SUM(CAST(sd.toll_interval_discount_fee AS DECIMAL(18,2))) AS total_split_discount_fee,
    ec.TOLL_INTERVAL_ID AS clear_interval_id,
    ec.AMOUNT AS clear_amount,
    ec.CHARGE_AMOUNT AS clear_charge_amount,
    ec.DISCOUNT_AMOUNT AS clear_discount_amount,
    CASE 
        WHEN ABS(SUM(CAST(sd.toll_interval_fee AS DECIMAL(18,2))) - COALESCE(ec.AMOUNT, 0)) < 0.01
             AND ABS(SUM(CAST(sd.toll_interval_pay_fee AS DECIMAL(18,2))) - COALESCE(ec.CHARGE_AMOUNT, 0)) < 0.01
             AND ABS(SUM(CAST(sd.toll_interval_discount_fee AS DECIMAL(18,2))) - COALESCE(ec.DISCOUNT_AMOUNT, 0)) < 0.01
        THEN '匹配成功'
        ELSE '数据不匹配'
    END AS match_status
FROM TBL_SPLITDETAIL sd
LEFT JOIN TBL_EXCLEARRESULTCASH ec ON sd.interval_id = ec.TOLL_INTERVAL_ID
GROUP BY sd.interval_id, ec.TOLL_INTERVAL_ID, ec.AMOUNT, ec.CHARGE_AMOUNT, ec.DISCOUNT_AMOUNT
FETCH FIRST 100 ROWS ONLY;

-- 统计更新情况
SELECT 
    COUNT(*) AS total_splitdetail_records,
    COUNT(DISTINCT sd.interval_id) AS unique_interval_ids,
    COUNT(DISTINCT ec.TOLL_INTERVAL_ID) AS matched_clear_records,
    COUNT(DISTINCT sd.interval_id) - COUNT(DISTINCT ec.TOLL_INTERVAL_ID) AS unmatched_interval_ids
FROM TBL_SPLITDETAIL sd
LEFT JOIN TBL_EXCLEARRESULTCASH ec ON sd.interval_id = ec.TOLL_INTERVAL_ID;
