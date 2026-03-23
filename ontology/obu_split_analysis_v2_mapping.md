# OBU拆分异常分析本体 V2 - 业务表映射说明

基于 `obu_split_analysis_v2.ontology.yaml` 与 `gsddl.sql`、设计文档V1.7 的字段对标。

## 核心实体与表映射

| 本体实体 | 业务表 | 说明 |
|---------|--------|------|
| Passage | a_tbl_exinetcwaste, tbl_etcsplitresult_ext, tbl_othersplitresult_ext, tbl_exincpcwaste | 通行记录 |
| SplitItem | a_tbl_split_detail | 拆分明细（由 splitOwnerGroup/splitOwnerfeeGroup 解析） |
| GantryTransaction | a_tbl_gantrywaste + a_tbl_gantryetcwaste | 门架交易（inSplitFlag 来自 GantryETCWaste） |
| GantryTollItem | a_tbl_gantrywaste_detail | 门架计费明细（由 tollIntervalId/feeGroup 解析） |
| OnlineChargeRecord | tbl_SpecialFCalRec_2026 | 在线计费记录（设计文档提及，gsddl 中未定义） |
| TollUnit | a_tbl_tollintervaldic | 收费单元字典 |
| Vehicle | a_tbl_vehicle | 车辆 |
| TollStation | a_tbl_tollstation | 收费站 |

## Passage 属性与表字段

| 本体属性 | 单省表字段 | 跨省表字段 |
|---------|------------|------------|
| passage_id | id | id / exTransactionId |
| pass_id | passId | passId |
| vlp | vlp | exVlp / vlp |
| vlpc | vlpc | exVlpc / vlpc |
| en_toll_station_id | enTollStationId | 截取 enTollLaneId 前14位 |
| ex_toll_station_id | exTollStationId | 截取 exTollLaneId 前14位 |
| split_time | proSplitTime | proSplitTime |
| exit_fee_type | exitFeeType | exitFeeType |
| ex_time | exTime | exTime |
| split_base | - | 通过 tbl_ETCSplitTaskHis 关联获取 |

## GantryTransaction 新增有效性属性（V1.7）

| 本体属性 | 表字段 | 表名 |
|---------|--------|------|
| mediatype | mediaType | tbl_GantryWaste |
| traderesult | tradeResult | tbl_GantryWaste |
| feecalcresult | feeCalcResult | tbl_GantryWaste |
| validstatus | validStatus | tbl_GantryWaste |
| tac | TAC | tbl_GantryWaste |

注：`a_tbl_gantrywaste` 当前 DDL 中未包含上述字段，需在生产表 `tbl_GantryWaste` 中确认后补齐映射。
