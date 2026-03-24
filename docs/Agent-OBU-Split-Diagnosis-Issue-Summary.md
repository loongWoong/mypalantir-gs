### Agent 拆分异常诊断问题总结

#### 一、问题现象

- 对同一通行记录（如 `S010137001002020102202026030107000021`），**规则引擎推理结果与 Agent 生成的诊断解释不一致**。
- 引擎日志（`Reasoning.log`）显示：
  - `split_detail_count = 3`
  - `gantry_transaction_count = 3`
  - `count_equal = true`
  - `sequence_equal = false`
  - `participating_gantry_continuous = false`
  - `all_gantry_continuous = false`
  - 触发规则：`R2_sequence_mismatch`、`R4_participating_gantry_discontinuous`、`R5_all_gantry_discontinuous`、`R8/R9/R10` 等
- 但 Agent 在 ReAct 过程中：
  - 一边复述了 `run_inference` 给出的“序列不一致 + 路径不完整 + 存在门架未上传”的结论；
  - 一边又调用 `check_route_consistency` / `check_participating_gantry_continuity` 等底层函数，得到与衍生属性相反的布尔值，试图重新解释，导致逻辑自相矛盾。

#### 二、根因分析

- **权威结论与“自算结果”混用**
  - 规则引擎的权威结论全部来源于：
    - **衍生属性**（如 `count_equal`、`sequence_equal` 等）
    - **SWRL 规则触发产生的事实**：`has_abnormal_reason`、`is_abnormal_passage` 等
  - Agent 一方面调用 `run_inference` 拿到这些权威结论；另一方面又通过 `call_function` 直接调用底层函数，得到另一套判断依据，两套结果不完全一致。

- **函数调用上下文不一致**
  - 在本体中，诊断衍生属性是通过 CEL 表达式调用函数的，例如：
    - `sequence_equal = get_split_sequence(this) == get_actual_sequence(this)`
    - `participating_gantry_continuous = check_participating_gantry_continuity(links.has_gantry_transaction)`
  - 规则引擎内部：
    - 对 `get_split_* / get_actual_*`：传入 `this`（instance + links）。
    - 对 `check_participating_gantry_continuity`：传入的是“门架交易列表”。
  - Agent 侧通过 `call_function`：
    - 由 `resolveFunctionArgsFromContext` 根据 `FunctionDef` 解析参数，很可能给的是 `thisObj` 而不是“门架列表”，导致：
      - 规则里算出的 `participating_gantry_continuous = false`
      - Agent `call_function` 返回 `check_participating_gantry_continuity → true`
    - 同一个 JS 函数在不同参数结构下得到不同结果，引起“规则 vs 函数调用”冲突。

- **LLM 在解释阶段自由发挥**
  - 在只知道“某些规则触发 + 某些函数返回值”的情况下，LLM 会尝试“调和”冲突信息：
    - 既想尊重 `run_inference` 的结论；
    - 又想解释 `call_function` 的返回，结果产生了逻辑不严谨、甚至互相矛盾的自然语言说明。

#### 三、已实施的修复措施

- **统一诊断结论来源，以 `run_inference` 为准**
  - 修改 `AgentTools.runInference`：
    - 调用 `ReasoningService.inferInstance` 获取 `InferenceResult`。
    - 只基于 `InferenceResult.toMap()` 和 `producedFacts` 生成中文诊断说明：
      - 输出基础事实（如 `split_detail_count`、`gantry_transaction_count`、`count_equal`、`sequence_equal`、`is_abnormal_passage`）。
      - 列出触发规则及其 `display_name`、`description`。
      - 从 `has_abnormal_reason` 事实生成“异常原因列表”。
    - 文末明确声明：内容严格来源于 `InferenceResult`，**不包含模型自行推断的路径细节**。

- **将底层函数调用标记为“调试用结果”**
  - 修改 `AgentTools.callFunction` 的返回值：
    - 前缀统一为：  
      `【调试用结果，仅用于查看底层数据，请以 run_inference 的结论为准】...`
  - 目的：让 Agent 在观察 `call_function` 输出时，首先看到“调试用 / 不可推翻结论”的提示，弱化其在最终诊断中的权重。

- **在工具描述中显式约束 Agent 的使用方式**
  - 更新 `getToolDescriptions()` 中各工具的文案：
    - **`search_rules`**：  
      “搜索当前本体的SWRL规则（调试用，帮助你查看有哪些规则与描述）”
    - **`call_function`**：  
      “调用诊断函数（调试/验证用，不应用于推翻 run_inference 的结论）”
    - **`run_inference`**：  
      “执行完整规则引擎推理（一次性返回所有规则结果，诊断结论以此为准）”
  - 这些文案进入 Agent 的 system prompt，明确要求：**最终诊断结论以 `run_inference` 为唯一权威来源**。

- **新增 Agent 级日志，便于排查 ReAct 逻辑**
  - 新增 `AgentLogger`（`logs/Agent.log`）：
    - 记录工具名、时间戳、完整参数、返回结果（长结果截断）、异常类型与堆栈摘要。
  - 在 `AgentTools.executeTool` 中统一使用 `AgentLogger` 包裹实际调用过程，以便对比 `Agent.log` 与 `Reasoning.log` 还原 Agent 的 ReAct 行为。

- **ID 解析与主键字段从本体动态解析**
  - 使用 `resolveIdPropertyName` 基于本体模型（`ObjectType` + `Property`）动态解析主键属性：
    - 取第一个 `derived=false` 且 `required=true` 的属性名。
  - `call_function` / `run_inference` 若未显式传 `instance_id`，会使用该属性名从参数中取值，避免硬编码 `passage_id` / `pass_id`。

#### 四、后续优化建议

- **统一函数签名与参数绑定（可选）**
  - 在 YAML 的 `functions` 段中，严格声明诊断函数的 `input` 类型（如 `list<GantryTransaction>`），确保：
    - 规则中的函数调用与 `call_function` 解析出的参数类型一致；
    - 避免同一函数在“规则里 / Agent 里”看到的是不同的数据结构。

- **限制 Agent 对底层函数结果的依赖（使用规范）**
  - 诊断“结论层”全部引用 `run_inference` 结果；
  - `call_function` 仅用于展示原始序列、门架列表等**调试信息**，而不以其返回的布尔值重新判定是否异常。

#### 五、当前状态结论

- 对 OBU 拆分诊断场景，Agent 的自然语言诊断已被强约束为：**只解释推理引擎的结论，不再基于底层函数结果自造结论**。
- 通过 `logs/Agent.log` + `logs/Reasoning.log` 可以完整还原：
  - Agent 每一步工具调用及其参数/结果；
  - 引擎内部的实例、关联数据、衍生属性和规则触发轨迹。

