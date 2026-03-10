# 为什么 Path 推理仍只触发一条规则

对齐 schema 的 link 关系后，若推理结果仍是「2 cycles, 1 rule fired」（只有「通行路径完整性正常」），常见原因如下。

## 1. Path 缺少出口/入口与 _media（已通过代码修复）

**原因**：规则 `obu_split_scope` 依赖衍生/内置：

- `is_single_province_etc(?p) == true`
- `is_obu_billing_mode1(?p) == true`

这两个内置函数依赖实例上的 **`_exit_transaction`** 和 **`_media`**（出口交易、介质类型/卡网络号）。  
之前只为 **Passage** 做了「出口/入口 + _media」的补充，**Path** 没有，因此：

- `is_single_province_etc` / `is_obu_billing_mode1` 恒为 `false`
- `in_obu_split_scope(?p, true)` 永远不会被推出
- 所有依赖 `in_obu_split_scope` 的 OBU 规则（路径不一致、根因、站/车传播等）都不会触发

**处理**：已在 `ReasoningService` 中为 **Path** 增加与 Passage 类似的补充逻辑：

- 通过 **入边** `exit_to_path`、`entry_to_path`（target=Path）查出该 Path 的出口交易、入口交易
- 将出口交易挂到实例的 `_exit_transaction`，并从入口交易构造 `_media`（media_type、card_net）
- 若 schema 中存在 `entry_involves_vehicle`、`entry_at_station`（source=EntryTransaction），也会一并查并写入 linkedData

**你需要做的**：在 schema 中为 **exit_to_path**、**entry_to_path** 配置好 **data_source**（connection_id、table、target_id_column 等），否则入边查询为空，Path 仍拿不到出口/入口和 _media。

---

## 2. Path_has_gantry_transactions 或 gantry_to_path 未配置 data_source

**原因**：  
OBU 规则和衍生属性会用到 `links(?p, Path_has_gantry_transactions)`。门架数据要么来自：

- **出边**：link 类型 `Path_has_gantry_transactions`（Path → GantryTransaction）配置了 data_source，或  
- **入边**：link 类型 `gantry_to_path`（GantryTransaction → Path）配置了 data_source，由推理服务自动按「目标=Path」反查门架并填入 linkedData。

若两者都未配置或配置错误，门架列表为空，依赖门架的规则（路径一致性、根因、金额一致性等）不会触发。

**处理**：  
在 schema 中二选一或两者都配好：

- 为 **Path_has_gantry_transactions** 配置 data_source（表、source_id_column/target_id_column 等），或  
- 为 **gantry_to_path** 配置 data_source（含 target_id_column，指向 Gantry 表中表示 Path 的列，如 pass_id），并保证该表里确实有该 Path 的门架流水。

---

## 3. schema 中缺少 entry_involves_vehicle / entry_at_station

**原因**：  
以下规则依赖「从 Path 经入口交易到车辆/收费站」的遍历：

- `vehicle_has_abnormal_Path`：`entry_involves_vehicle(?p, ?v)`
- `station_has_abnormal_traffic`：`entry_at_station(?p, ?s)`
- `vehicle_has_obu_split_abnormal` / `station_has_obu_split_abnormal`：同样依赖这两类 link

若 schema 的 **link_types** 里没有 **entry_involves_vehicle**（EntryTransaction → Vehicle）和 **entry_at_station**（EntryTransaction → TollStation），或没有配置 data_source，则这些规则永远无法匹配。

**处理**：  
若希望触发「车辆存在异常」「收费站存在OBU拆分异常」等链式传播规则，需要在 schema 中：

- 定义 link 类型 **entry_involves_vehicle**、**entry_at_station**（source_type=EntryTransaction），并  
- 配置对应的 data_source，使推理时能根据当前 Path 的入口交易查到车辆和收费站。

---

## 4. 衍生属性或内置函数字段名与表结构不一致

**原因**：  
衍生属性或内置函数里使用的字段名（如 `multi_province`、`pay_type`、`card_net`、`pro_split_time`、`toll_interval_fee` 等）若与 schema 中 ObjectType 的 property 名或实际表列不一致，会导致：

- 衍生属性求值失败或得到空/错误值  
- `detail_count_matched`、`interval_set_matched`、`fee_matched` 等为 false，连第一条「通行路径完整性正常」都可能不触发；或  
- 内置函数 is_single_province_etc / is_obu_billing_mode1 判断错误，OBU 规则仍不触发。

**处理**：  
对照实际表结构和 schema 中的 property/field_mapping，确保：

- Path 的衍生属性中使用的 link 名与 link_types 一致（如 path_has_path_details、path_has_split_details、Path_has_gantry_transactions）；  
- 使用的字段名（如 identify_point_id、interval_id、toll_interval_fee、pro_split_time、fee、pay_fee）与 PathDetail、SplitDetail、GantryTransaction 等类型的属性名一致；  
- 出口交易、入口交易、_media 中使用的字段（multi_province、pay_type、media_type、card_net）与 ExitTransaction/EntryTransaction 的 property 或数据源列一致。

---

## 检查清单（对齐 link 后仍只有一条规则时）

1. **Path 的出口/入口与 _media**  
   - [ ] schema 中 **exit_to_path**、**entry_to_path** 已配置 **data_source**（含 target_id_column 等）。  
   - [ ] 推理服务已更新（含 Path 的 enrichPathEntryExitAndMedia），并重启应用。

2. **门架数据**  
   - [ ] **Path_has_gantry_transactions** 或 **gantry_to_path** 至少一个配置了 **data_source**，且表中确有该 Path 的门架数据。

3. **车辆/收费站链式规则**（若需要）  
   - [ ] schema 中定义了 **entry_involves_vehicle**、**entry_at_station**，并配置了 data_source。

4. **字段与 link 名**  
   - [ ] 衍生属性、规则中的 link 名与 link_types 一致；字段名与各 ObjectType 的 property/表列一致。

完成以上配置并保证数据存在后，再跑 Path 推理，应能出现多 cycle、多规则触发（OBU 拆分范围、路径不一致、根因、站/车传播等）。
