# OBU 拆分异常诊断：模型定义与推理机理

## 1. 业务背景

### 1.1 高速公路门架制收费

2020 年全国取消省界收费站后，高速公路通行费采用**门架制**计费：

1. 车辆在入口收费站入站，产生**入口交易**（EntryTransaction）
2. 车辆每经过一个门架，OBU/CPC 与门架交互，产生一条**门架交易**（GantryTransaction），记录收费单元、金额、时间等
3. 车辆在出口收费站出站，产生**出口交易**（ExitTransaction），系统汇总各门架费用，完成扣费
4. 出站后，后台系统根据门架流水进行**拆分**（Split），将总费用按收费单元拆分到各省，生成**拆分明细**（SplitDetail）

```
入口站 ──→ 门架1 ──→ 门架2 ──→ 门架3 ──→ ... ──→ 门架N ──→ 出口站
  │          │         │         │                  │         │
  ▼          ▼         ▼         ▼                  ▼         ▼
入口交易   门架交易1  门架交易2  门架交易3  ...   门架交易N   出口交易
                                                              │
                                                              ▼
                                                         拆分（Split）
                                                              │
                                                              ▼
                                                    拆分明细1, 2, 3 ...
```

### 1.2 拆分异常问题

正常情况下，**拆分结果应与门架流水一致**——收费单元对应、金额对应。但实际运营中，由于门架设备故障、数据延迟、系统计算误差等原因，拆分结果经常出现异常。

传统方式下，拆分异常分析是一个**硬编码的多步工作流**：编写代码查询数据、循环比较、逐级判断、手动传播结论。每增加一个诊断维度，都需要修改工作流代码。

MyPalantir 将这个分析过程转化为**声明式的业务模型定义**，通过衍生属性、函数和规则的三层协作，让推理引擎或智能体自动完成诊断。

## 2. 领域模型：Ontology 定义

### 2.1 核心对象类型

诊断过程涉及以下对象类型及其关系：

```
                    TollStation（收费站）
                    ╱           ╲
          entry_at_station    exit_at_station
                ╱                     ╲
   EntryTransaction（入口交易）  ExitTransaction（出口交易）
           │                              │
    passage_has_entry              passage_has_exit
           │                              │
           ▼                              ▼
              Passage（通行路径·聚合根）
             ╱          │           ╲
   passage_has_     passage_has_   passage_has_
   gantry_trans     details        split_details
          ╱              │              ╲
GantryTransaction   PathDetail     SplitDetail
（门架交易）       （路径明细）    （拆分明细）
          │
   entry_involves_vehicle
          │
          ▼
     Vehicle（车辆）── vehicle_has_media ──→ Media（通行介质）
```

**Passage（通行路径）** 是诊断的核心聚合根，它聚合了一次完整通行的所有数据：入口交易、出口交易、门架交易序列、路径明细、拆分明细。

### 2.2 衍生属性：用户可见的业务指标

衍生属性定义在 Passage 上，由 CEL（Common Expression Language）表达式计算。它们是用户在界面上直接看到的数据，同时也为多条规则提供缓存：

```yaml
# 基础统计指标
path_detail_count:         "size(links.passage_has_details)"          # 路径明细数量
split_detail_count:        "size(links.passage_has_split_details)"    # 拆分明细数量
gantry_transaction_count:  "size(links.passage_has_gantry_transactions)" # 门架交易数量

# 费用汇总指标
path_fee_total:   "links.passage_has_details.map(d, double(d.fee)).sum()"            # 路径费用合计
split_fee_total:  "links.passage_has_split_details.map(s, s.toll_interval_fee).sum()" # 拆分费用合计
gantry_fee_total: "links.passage_has_gantry_transactions.map(g, double(g.fee)).sum()" # 门架费用合计

# 简单等值判断（多条规则共用）
detail_count_matched:  "count(路径明细) == count(拆分明细)"
interval_set_matched:  "路径明细.标识点集合 == 拆分明细.收费单元集合"
fee_matched:           "路径费用合计 == 拆分费用合计"
```

**设计原则**：衍生属性只放「用户需要看到的数据」和「被多条规则引用的简单判断」。复杂算法不放在这里。

### 2.3 函数/工具：可调用的算法

复杂的检测逻辑抽象为函数（Function），在 YAML 中声明签名，由引擎内置实现或外部服务提供：

| 函数名 | 输入 | 输出 | 说明 | 实现 |
| ------ | ---- | ---- | ---- | ---- |
| `is_single_province_etc` | Passage | boolean | 判定单省ETC交易 | builtin |
| `is_obu_billing_mode1` | Passage | boolean | 判定OBU计费方式1 | builtin |
| `check_route_consistency` | list\<SplitDetail\>, list\<GantryTransaction\> | boolean | 拆分路径与门架路径是否一致 | builtin |
| `detect_duplicate_intervals` | list\<GantryTransaction\> | boolean | 门架收费单元是否重复 | builtin |
| `check_gantry_hex_continuity` | list\<GantryTransaction\> | boolean | 相邻门架HEX是否首尾衔接 | builtin |
| `check_gantry_count_complete` | list\<GantryTransaction\> | boolean | 门架数量是否等于交易成功数 | builtin |
| `detect_late_upload` | list\<GantryTransaction\>, date | boolean | 是否存在门架延迟上传 | builtin |
| `check_fee_detail_consistency` | list\<SplitDetail\>, list\<GantryTransaction\> | boolean | 逐收费单元金额是否一致 | builtin |
| `check_rounding_mismatch` | list\<GantryTransaction\>, float | boolean | 取整是否导致金额差异 | builtin |
| `check_balance_continuity` | list\<GantryTransaction\> | boolean | 卡内余额是否连续 | builtin |
| `fit_actual_route` | Passage | boolean | 牌识拟合的实际路径与计费路径是否一致 | external |

**为什么抽为函数而非衍生属性？** 因为这些检测涉及序列遍历、相邻元素比较、外部服务调用等**算法性**操作，用 CEL 表达式描述会复杂且脆弱。函数封装算法细节，只暴露语义化的布尔结果。

### 2.4 推理规则：SWRL 声明式推理

规则使用 SWRL（Semantic Web Rule Language）语法，通过命题组合实现逻辑推导。以下严格按照 `toll.yaml` 中的规则定义逐条说明。

#### 第一层：前置条件筛选（1 条规则）

```
规则 obu_split_scope:
  Passage(?p)
    ∧ is_single_province_etc(?p) == true
    ∧ is_obu_billing_mode1(?p) == true
    → in_obu_split_scope(?p, true)
```

这条规则调用两个函数判定前置条件，产生事实 `in_obu_split_scope(?p, true)`。只有这个事实存在，后续所有 OBU 诊断规则才有可能被触发。

#### 第二层：三维度异常检测（4 条规则，互斥并列）

以下 4 条规则都以 `in_obu_split_scope(?p, true)` 为前件，通过不同的函数调用组合实现互斥——对于同一个 Passage，有且仅有一条能匹配：

```
规则 obu_split_normal:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ check_route_consistency(
        links(?p, passage_has_split_details),
        links(?p, passage_has_gantry_transactions)) == true
    ∧ check_fee_detail_consistency(
        links(?p, passage_has_split_details),
        links(?p, passage_has_gantry_transactions)) == true
    ∧ fit_actual_route(?p) == true
    → obu_split_status(?p, "正常")

规则 obu_split_route_mismatch:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ check_route_consistency(
        links(?p, passage_has_split_details),
        links(?p, passage_has_gantry_transactions)) == false
    → obu_split_status(?p, "路径不一致")

规则 obu_split_fee_mismatch:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ check_route_consistency(
        links(?p, passage_has_split_details),
        links(?p, passage_has_gantry_transactions)) == true
    ∧ check_fee_detail_consistency(
        links(?p, passage_has_split_details),
        links(?p, passage_has_gantry_transactions)) == false
    → obu_split_status(?p, "金额不一致")

规则 obu_split_actual_route_mismatch:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ check_route_consistency(
        links(?p, passage_has_split_details),
        links(?p, passage_has_gantry_transactions)) == true
    ∧ check_fee_detail_consistency(
        links(?p, passage_has_split_details),
        links(?p, passage_has_gantry_transactions)) == true
    ∧ fit_actual_route(?p) == false
    → obu_split_status(?p, "实际路径偏差")
```

互斥关系分析：

- `route==false` → 只有 `obu_split_route_mismatch` 匹配（路径都不对，不再看金额和实际路径）
- `route==true ∧ fee==false` → 只有 `obu_split_fee_mismatch` 匹配
- `route==true ∧ fee==true ∧ actual==false` → 只有 `obu_split_actual_route_mismatch` 匹配
- `route==true ∧ fee==true ∧ actual==true` → 只有 `obu_split_normal` 匹配

每条规则的后件都产生 `obu_split_status(?p, ...)` 事实，供第三层规则消费。

#### 第三层：根因诊断

**路径不一致的根因**（3 条规则，互斥并列）：

以下 3 条规则都以 `obu_split_status(?p, "路径不一致")` 为前件。通过函数返回值的不同组合实现互斥：

```
规则 obu_route_cause_duplicate_gantry:
  Passage(?p) ∧ obu_split_status(?p, "路径不一致")
    ∧ detect_duplicate_intervals(
        links(?p, passage_has_gantry_transactions)) == true
    → obu_route_cause(?p, "门架收费单元重复")

规则 obu_route_cause_etc_incomplete:
  Passage(?p) ∧ obu_split_status(?p, "路径不一致")
    ∧ detect_duplicate_intervals(
        links(?p, passage_has_gantry_transactions)) == false
    ∧ check_gantry_hex_continuity(
        links(?p, passage_has_gantry_transactions)) == false
    → obu_route_cause(?p, "ETC门架不完整")

规则 obu_route_cause_cpc_incomplete:
  Passage(?p) ∧ obu_split_status(?p, "路径不一致")
    ∧ detect_duplicate_intervals(
        links(?p, passage_has_gantry_transactions)) == false
    ∧ check_gantry_hex_continuity(
        links(?p, passage_has_gantry_transactions)) == true
    ∧ check_gantry_count_complete(
        links(?p, passage_has_gantry_transactions)) == false
    → obu_route_cause(?p, "CPC门架不完整")
```

互斥关系分析：

- `duplicate==true` → 只匹配 `duplicate_gantry`
- `duplicate==false ∧ hex_continuous==false` → 只匹配 `etc_incomplete`
- `duplicate==false ∧ hex_continuous==true ∧ count_complete==false` → 只匹配 `cpc_incomplete`

**延迟上传检测**（1 条规则，依赖上面的推导结论形成前后链）：

```
规则 obu_route_cause_late_upload:
  Passage(?p)
    ∧ (obu_route_cause(?p, "ETC门架不完整")
       ∨ obu_route_cause(?p, "CPC门架不完整"))
    ∧ detect_late_upload(
        links(?p, passage_has_gantry_transactions),
        links(?p, passage_has_split_details)[0].pro_split_time) == true
    → obu_incomplete_reason(?p, "门架延迟上传")
```

这条规则的前件包含 `obu_route_cause(?p, ...)`，这是上面 3 条规则推导出的事实。因此它必须在上面的规则触发之后才能匹配——这就是前向链推理中的规则链。

**金额不一致的根因**（2 条规则，并列但不互斥）：

```
规则 obu_fee_cause_rounding:
  Passage(?p) ∧ obu_split_status(?p, "金额不一致")
    ∧ check_rounding_mismatch(
        links(?p, passage_has_gantry_transactions), 0.95) == true
    → obu_fee_cause(?p, "四舍五入取整差异")

规则 obu_fee_cause_balance_anomaly:
  Passage(?p) ∧ obu_split_status(?p, "金额不一致")
    ∧ check_balance_continuity(
        links(?p, passage_has_gantry_transactions)) == false
    → obu_fee_cause(?p, "卡内累计金额异常")
```

注意：这两条规则**不互斥**。一笔交易可能同时存在取整差异和余额异常，此时两条规则都会触发，产生两个 `obu_fee_cause` 事实。

#### 第四层：链式传播（2 条规则，并列）

```
规则 vehicle_has_obu_split_abnormal:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ obu_split_status(?p, ?status) ∧ (?status != "正常")
    ∧ entry_involves_vehicle(?p, ?v)
    → has_obu_split_abnormal(?v, true)

规则 station_has_obu_split_abnormal:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ obu_split_status(?p, ?status) ∧ (?status != "正常")
    ∧ entry_at_station(?p, ?s)
    → has_obu_split_abnormal(?s, true)
```

这两条规则使用了**变量绑定** `?status` 和**不等式约束** `?status != "正常"`，匹配所有非正常状态的 Passage。通过关系 `entry_involves_vehicle` 和 `entry_at_station` 遍历关联的 Vehicle 和 TollStation，将异常标记传播上去。

两条规则**并列独立**：一条负责传播到车辆，一条负责传播到收费站，对同一个 Passage 会同时触发。

## 3. 推理机理

### 3.1 规则引擎模式：批量自动推理

规则引擎采用**前向链推理**（Forward Chaining）模式。核心是一个 **match → fire → match → fire** 的循环，每产生新事实就重新扫描所有规则，直到没有新事实产生为止：

```
┌──────────────────────────────────────────────────────────────┐
│                    前向链推理循环                               │
│                                                              │
│  ┌──────────┐     ┌──────────────┐     ┌──────────────┐      │
│  │ 物理数据源 │────→│  CEL 求值器   │────→│  工作内存      │      │
│  └──────────┘     │ 计算衍生属性  │     │ (事实集合)     │      │
│                   └──────────────┘     └──────┬───────┘      │
│                                               │              │
│                                               ▼              │
│                                        ┌────────────┐        │
│                              ┌────────→│ 规则匹配    │        │
│                              │         │ 扫描所有规则 │        │
│                              │         │ 的前件      │        │
│                              │         └──────┬─────┘        │
│                              │                │              │
│                              │     ┌──────────┴──────────┐   │
│                              │     │有规则匹配？          │   │
│                              │     ├─ 是：触发规则        │   │
│                              │     │  ├─ 调用函数(如需要) │   │
│                              │     │  └─ 产生新事实       │   │
│                              │     │     加入工作内存 ─────┼───┘
│                              │     │                      │
│                              │     └─ 否：推理终止        │
│                              │                            │
│                              └────────────────────────────┘
└──────────────────────────────────────────────────────────────┘
```

以一个具体 Passage 为例，追踪完整的推理过程：

```
循环1  工作内存: { Passage(?p) }
       匹配:   obu_split_scope 前件满足（函数调用: is_single_province_etc→true, is_obu_billing_mode1→true）
       产生:   + in_obu_split_scope(?p, true)

循环2  工作内存: { Passage(?p), in_obu_split_scope(?p, true) }
       匹配:   obu_split_route_mismatch 前件满足（函数调用: check_route_consistency→false）
       产生:   + obu_split_status(?p, "路径不一致")

循环3  工作内存: { ..., obu_split_status(?p, "路径不一致") }
       匹配:   obu_route_cause_etc_incomplete 前件满足
               （函数调用: detect_duplicate_intervals→false, check_gantry_hex_continuity→false）
       产生:   + obu_route_cause(?p, "ETC门架不完整")

循环4  工作内存: { ..., obu_route_cause(?p, "ETC门架不完整") }
       匹配:   obu_route_cause_late_upload 前件满足（函数调用: detect_late_upload→true）
       产生:   + obu_incomplete_reason(?p, "门架延迟上传")

       同时匹配: vehicle_has_obu_split_abnormal 前件满足（关系遍历: entry_involves_vehicle→?v）
       产生:   + has_obu_split_abnormal(?v, true)

       同时匹配: station_has_obu_split_abnormal 前件满足（关系遍历: entry_at_station→?s）
       产生:   + has_obu_split_abnormal(?s, true)

循环5  工作内存: { ..., 所有上述事实 }
       匹配:   无新规则可触发
       → 推理终止
```

**关键点：**

- 引擎不知道规则之间的"层级"或"顺序"，它只做一件事：扫描所有规则，触发前件满足的规则
- 规则之间的依赖关系（第一层→第二层→第三层→第四层）完全由**事实的产生和消费**自然形成
- 同一循环中可以有多条规则同时触发（如循环4中的传播规则）
- 衍生属性在初始化时一次性计算完毕，不参与循环

### 3.2 智能体模式：按需交互推理

#### 策略依据：规则即知识

智能体的诊断策略**不是大模型的即兴发挥**，而是来自 toll.yaml 中的两类定义：

1. **SWRL 规则 → 诊断知识**：规则的前件结构编码了"什么条件下该做什么检查"。大模型将规则定义作为 system prompt 或知识库加载后，从中提取出依赖关系：
   - `in_obu_split_scope` 是所有诊断规则的前提 → 先判断范围
   - `obu_split_status` 的前件包含 `in_obu_split_scope` → 范围确认后再检测异常类型
   - `obu_route_cause` 的前件包含 `obu_split_status("路径不一致")` → 确认路径异常后再查根因
   - `obu_incomplete_reason` 的前件包含 `obu_route_cause(...)` → 确认根因后再查进一步原因

2. **函数定义 → 工具清单**：functions 部分的每个函数直接映射为 Agent 的 tool schema。大模型从规则前件中知道当前状态下哪些函数相关，而不是盲目遍历所有工具。

**规则引擎和智能体使用的是同一套规则定义，区别仅在于执行方式：**

| | 规则引擎 | 智能体 |
| --- | --- | --- |
| 策略来源 | 规则前件的模式匹配（自动） | 大模型阅读规则后理解依赖关系（推理） |
| 执行方式 | 盲扫所有规则，匹配即触发 | 根据中间结果选择性调用下一个函数 |
| 等价性 | 对同一数据，两种模式得出相同结论 | |

#### 诊断过程示例

以一个具体案例展示智能体如何基于规则知识进行推理：

```
用户："帮我查一下 pass_id=ABC123 这笔交易为什么拆分异常"
                    │
                    ▼
            ┌──────────────────────────────────────────────────┐
            │   LLM Agent 加载诊断知识                          │
            │                                                  │
            │   System Prompt 包含：                            │
            │   - 20 条 SWRL 规则定义（依赖关系）                │
            │   - 11 个函数的 tool schema（可调用工具）           │
            │                                                  │
            │   从规则中提取诊断路径：                            │
            │   scope → status → cause → reason                │
            └──────────────────┬────────────��──────────────────┘
                               │
       ┌───────────────────────┼──────────────────────────────┐
       │  依据: obu_split_scope 规则                           │
       │  前件需要 is_single_province_etc ∧ is_obu_billing_mode1│
       │  → 调用这两个函数确认范围                              │
       └───────────────────────┼──────────────────────────────┘
                               ▼
            ┌──────────────┐
            │  Tool Call 1  │  is_single_province_etc → true
            │  Tool Call 2  │  is_obu_billing_mode1 → true
            │               │  → 在诊断范围内
            └──────┬───────┘
                   │
       ┌───────────┼──────────────────────────────────────────┐
       │  依据: obu_split_route_mismatch 规则                  │
       │  前件需要 check_route_consistency == false             │
       │  → 调用该函数检测路径一致性                            │
       └───────────┼──────────────────────────────────────────┘
                   ▼
            ┌──────────────┐
            │  Tool Call 3  │  check_route_consistency → false
            │               │  → 路径不一致
            └──────┬───────┘
                   │
       ┌───────────┼──────────────────────────────────────────┐
       │  依据: obu_route_cause 的 3 条互斥规则                 │
       │  需要依次检查 duplicate / hex / count                 │
       │  → 按条件组合逐步调用                                 │
       └───────────┼──────────────────────────────────────────┘
                   ▼
            ┌──────────────┐
            │  Tool Call 4  │  detect_duplicate_intervals → false
            │  Tool Call 5  │  check_gantry_hex_continuity → false
            │               │  → 匹配 obu_route_cause_etc_incomplete
            │               │  → 根因：ETC门架不完整
            └──────┬───────┘
                   │
       ┌───────────┼──────────────────────────────────────────┐
       │  依据: obu_route_cause_late_upload 规则                │
       │  前件需要 obu_route_cause == "ETC门架不完整"（已确认）  │
       │  ∧ detect_late_upload == true                         │
       │  → 调用该函数进一步探查                                │
       └───────────┼──────────────────────────────────────────┘
                   ▼
            ┌──────────────┐
            │  Tool Call 6  │  detect_late_upload → true
            │               │  → 存在延迟上传
            └──────┬───────┘
                   │ 所有相关规则链已走完，综合输出
                   ▼
            ┌──────────────────────────────────────────┐
            │  Agent 回答：                             │
            │  该笔交易拆分异常的原因是：                │
            │  1. 拆分路径与门架流水路径不一致            │
            │  2. 根因：ETC门架路径不完整（HEX不连续）    │
            │  3. 进一步原因：存在门架流水延迟上传        │
            │     （门架3的接收时间晚于拆分时间15分钟）    │
            │  建议：等待延迟门架数据到达后重新拆分        │
            └──────────────────────────────────────────┘
```

每一步 Tool Call 前的"依据"框标注了对应的规则名称和前件条件。大模型的推理链与规则引擎的前向链在逻辑上等价，区别在于：

- **规则引擎**盲扫所有规则，由匹配结果驱动，无需"理解"
- **智能体**阅读规则后理解依赖结构，根据中间结果**选择性**调用下一个函数，跳过不相关的分支（如 `duplicate==false` 后不需要调用 `check_gantry_count_complete`，因为 `hex==false` 已经足够匹配 `etc_incomplete`）

#### 智能体模式的优势

- **按需调用**：不必执行所有函数，根据中间结果决定下一步（如检测到重复就不再检查连续性）
- **自然语言交互**：用户不需要了解规则定义，直接用自然语言提问
- **解释能力**：大模型可以用自然语言解释每一步推理的**规则依据**
- **灵活追问**：用户可以追问"门架3延迟了多久？""历史上这个门架延迟频率高吗？"
- **策略可审计**：诊断策略来源于可读的 YAML 规则定义，而非黑盒模型权重

### 3.3 两种模式的对比与协作

| 维度 | 规则引擎 | 智能体 + 大模型 |
| ---- | -------- | --------------- |
| 触发方式 | 自动，规则前件匹配即触发 | 按需，用户提问或事件驱动 |
| 调用范围 | 批量，全量扫描所有实例 | 单条，针对具体个案 |
| 推理过程 | 确定性，完全由规则定义 | 启发式，大模型决定调用顺序 |
| 结果形式 | 结构化标签（属性赋值） | 自然语言解释 + 结构化标签 |
| 适合场景 | 日常监控、批量筛查、告警 | 个案分析、交互式诊断、根因追溯 |
| 性能 | 高吞吐，适合大规模数据 | 单次延迟较高，适合深度分析 |

**典型协作模式：**

```
                日常运行
                  │
         规则引擎批量扫描
        ╱         │          ╲
  正常(98%)   路径异常(1.5%)  金额异常(0.5%)
                  │                │
         标记 Passage/Vehicle/Station
                  │
            ┌─────┴─────┐
            │  告警推送   │
            └─────┬─────┘
                  │
          业务人员收到告警
                  │
      "帮我分析一下这批异常"
                  │
          智能体介入诊断
                  │
    按需调用函数，逐条分析根因
                  │
    输出诊断报告 + 处理建议
```

规则引擎负责**发现问题**（大海捞针），智能体负责**分析问题**（深度诊断）。两者共用同一套函数定义，数据和算法完全复用。

## 4. 从工作流到声明式模型的范式转换

### 4.1 传统工作流方式

```python
# 伪代码 —— 传统拆分异常分析工作流
def analyze_obu_split(passage):
    exit_tx = query_exit_transaction(passage.pass_id)
    media = query_media(exit_tx.media_id)

    # 步骤1：前置条件判断
    if media.card_net != "3701" or exit_tx.multi_province != 0:
        return "不在分析范围"
    if media.media_type != 1:
        return "不在分析范围"

    # 步骤2：查询拆分和门架数据
    split_details = query_split_details(passage.pass_id)
    gantry_txs = query_gantry_transactions(passage.pass_id)

    # 步骤3：路径一致性检查
    split_intervals = sorted([s.interval_id for s in split_details])
    gantry_intervals = sorted([g.toll_interval_id for g in gantry_txs])
    if split_intervals != gantry_intervals:
        # 步骤3a：根因诊断 - 重复检测
        if len(gantry_intervals) != len(set(gantry_intervals)):
            cause = "门架收费单元重复"
        # 步骤3b：根因诊断 - 连续性检测
        elif not check_hex_continuity(gantry_txs):
            cause = "ETC门架不完整"
            if any(g.receive_time > split_details[0].pro_split_time for g in gantry_txs):
                cause += " (门架延迟上传)"
        # ...更多分支
        update_passage_status(passage, "路径不一致", cause)
        update_vehicle_flag(passage.vehicle_id, "has_abnormal")
        update_station_flag(passage.station_id, "has_abnormal")
        return

    # 步骤4：金额一致性检查（类似结构，省略）
    # 步骤5：实际路径检查（类似结构，省略）
    # 步骤6：传播结论（手动编码）
```

**问题：**
- 查询、判断、传播全部混杂在一个过程中
- 每增加一个诊断维度，需要修改流程代码
- 诊断逻辑不可复用（智能体无法调用其中的检测步骤）
- 难以理解全貌——需要读完所有代码才能理解诊断树

### 4.2 声明式模型方式

```yaml
# 衍生属性 —— 声明数据指标
path_fee_total:
  expr: "links.passage_has_details.map(d, double(d.fee)).sum()"

# 函数 —— 声明算法能力
functions:
  - name: check_gantry_hex_continuity
    input: [{ name: gantry_transactions, type: "list<GantryTransaction>" }]
    output: { type: boolean }

# 规则 —— 声明推理逻辑
rules:
  - name: obu_route_cause_etc_incomplete
    expr: >
      Passage(?p) ∧ obu_split_status(?p, "路径不一致")
        ∧ detect_duplicate_intervals(...) == false
        ∧ check_gantry_hex_continuity(...) == false
        → obu_route_cause(?p, "ETC门架不完整")
```

**优势：**
- 数据、算法、推理三层分离，各自独立演化
- 新增诊断维度只需添加函数 + 规则，不影响已有逻辑
- 函数可被规则引擎和智能体双重复用
- YAML 定义就是业务文档，业务人员可直接审阅诊断逻辑

### 4.3 对应关系

| 传统工作流中的代码 | 声明式模型中的定义 |
| ------------------ | ------------------ |
| SQL 查询 + 结果集遍历 | ObjectType + LinkType（数据源映射） |
| `len(list_a) == len(list_b)` | 衍生属性 `detail_count_matched`（CEL） |
| `sum(fees)` | 衍生属性 `path_fee_total`（CEL） |
| 有序遍历 + 相邻比较算法 | 函数 `check_gantry_hex_continuity`（builtin） |
| 外部服务调用 | 函数 `fit_actual_route`（external） |
| `if ... elif ... else` 分支 | SWRL 规则命题组合 |
| 手动 `update_xxx_flag()` | SWRL 链式传播规则 |
| 整个工作流的编排顺序 | 推理引擎自动前向链推理 |

## 5. 完整规则清单

以下是 OBU 拆分异常诊断的全部 20 条 SWRL 规则。每条规则独立声明，引擎通过前向链推理自动确定执行顺序。

### 范围界定（1 条）

```
obu_split_scope:
  Passage(?p)
    ∧ is_single_province_etc(links(?p, passage_has_path_details)) == true
    ∧ is_obu_billing_mode1(links(?p, passage_has_split_details)) == true
    → in_obu_split_scope(?p, true)
```

### 异常检测（4 条，互斥）

```
obu_split_route_mismatch:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ check_route_consistency(...) == false
    → obu_split_status(?p, "路径不一致")

obu_split_fee_mismatch:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ detail_count_matched(?p, true) ∧ interval_set_matched(?p, true)
    ∧ check_fee_detail_consistency(...) == false
    → obu_split_status(?p, "金额不一致")

obu_split_route_deviation:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ detail_count_matched(?p, true) ∧ interval_set_matched(?p, true)
    ∧ fee_matched(?p, true) ∧ fit_actual_route(?p) == false
    → obu_split_status(?p, "实际路径偏差")

obu_split_normal:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ detail_count_matched(?p, true) ∧ interval_set_matched(?p, true)
    ∧ fee_matched(?p, true) ∧ fit_actual_route(?p) == true
    → obu_split_status(?p, "正常")
```

互斥性由条件组合保证：`check_route_consistency` 为 false 时只匹配第一条；为 true 时衍生属性 `detail_count_matched` 和 `interval_set_matched` 为 true，再按 `fee_matched` 和 `fit_actual_route` 结果分流到后三条。

### 路径不一致的根因（3 条，互斥）

```
obu_route_cause_duplicate_gantry:
  Passage(?p) ∧ obu_split_status(?p, "路径不一致")
    ∧ detect_duplicate_intervals(...) == true
    → obu_route_cause(?p, "门架收费单元重复")

obu_route_cause_etc_incomplete:
  Passage(?p) ∧ obu_split_status(?p, "路径不一致")
    ∧ detect_duplicate_intervals(...) == false
    ∧ check_gantry_hex_continuity(...) == false
    → obu_route_cause(?p, "ETC门架不完整")

obu_route_cause_cpc_incomplete:
  Passage(?p) ∧ obu_split_status(?p, "路径不一致")
    ∧ detect_duplicate_intervals(...) == false
    ∧ check_gantry_hex_continuity(...) == true
    ∧ check_gantry_count_complete(...) == false
    → obu_route_cause(?p, "CPC门架不完整")
```

互斥性：`duplicate==true` → 第一条；`duplicate==false ∧ hex==false` → 第二条；`duplicate==false ∧ hex==true ∧ count==false` → 第三条。

### 延迟上传检测（1 条，链式依赖上层结论）

```
obu_route_cause_late_upload:
  Passage(?p)
    ∧ (obu_route_cause(?p, "ETC门架不完整") ∨ obu_route_cause(?p, "CPC门架不完整"))
    ∧ detect_late_upload(...) == true
    → obu_incomplete_reason(?p, "门架延迟上传")
```

前件 `obu_route_cause(?p, ...)` 是上面 3 条规则的推导结论，形成规则链。

### 金额不一致的根因（2 条，不互斥）

```
obu_fee_cause_rounding:
  Passage(?p) ∧ obu_split_status(?p, "金额不一致")
    ∧ check_rounding_mismatch(...) == true
    → obu_fee_cause(?p, "四舍五入取整差异")

obu_fee_cause_balance_anomaly:
  Passage(?p) ∧ obu_split_status(?p, "金额不一致")
    ∧ check_balance_continuity(...) == false
    → obu_fee_cause(?p, "卡内累计金额异常")
```

这两条**不互斥**，可同时触发。

### 链式传播（2 条，并列）

```
vehicle_has_obu_split_abnormal:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ obu_split_status(?p, ?status) ∧ (?status != "正常")
    ∧ entry_involves_vehicle(?p, ?v)
    → has_obu_split_abnormal(?v, true)

station_has_obu_split_abnormal:
  Passage(?p) ∧ in_obu_split_scope(?p, true)
    ∧ obu_split_status(?p, ?status) ∧ (?status != "正常")
    ∧ entry_at_station(?p, ?s)
    → has_obu_split_abnormal(?s, true)
```

将异常标记从 Passage 传播到关联的 Vehicle 和 TollStation。

### 原有基础规则（7 条）

```
basic_fee_mismatch:        fee_matched(?p, false) → abnormal_flag(?p, true)
basic_count_mismatch:      detail_count_matched(?p, false) → abnormal_flag(?p, true)
basic_interval_mismatch:   interval_set_matched(?p, false) → abnormal_flag(?p, true)
basic_high_fee_passage:    path_fee_total(?p) > 10000 → high_fee_flag(?p, true)
basic_high_fee_vehicle:    high_fee_flag(?p, true) ∧ entry_involves_vehicle(?p, ?v) → needs_review(?v, true)
basic_station_has_mismatch: abnormal_flag(?p, true) ∧ entry_at_station(?p, ?s) → has_mismatch(?s, true)
basic_vehicle_has_mismatch: abnormal_flag(?p, true) ∧ entry_involves_vehicle(?p, ?v) → has_mismatch(?v, true)
```

**总计 20 条规则**：1（范围）+ 4（检测）+ 3（路径根因）+ 1（延迟上传）+ 2（金额根因）+ 2（传播）+ 7（基础）。全部独立声明，由推理引擎通过前向链自动编排执行。
