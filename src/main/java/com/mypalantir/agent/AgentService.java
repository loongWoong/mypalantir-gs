package com.mypalantir.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.service.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct 诊断 Agent 服务。
 * 核心循环：Thought → Action → Observation → Thought → ... → Answer
 * 支持流式回调，每步完成时通知调用方。
 */
@Service
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_STEPS = 15;
    private static final int MIN_TOOL_CALLS = 3;

    private final LLMService llmService;
    private final AgentTools agentTools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 会话上下文：按 conversationId 维护 LLM 侧的对话历史，支持多轮对话。
     * key 为前端生成的 conversationId（每个浏览器 Tab 独立），值为当前对话串。
     * 不跨 conversationId 共享，天然支持多用户并发访问。
     */
    private final Map<String, String> conversationStore = new ConcurrentHashMap<>();

    public AgentService(LLMService llmService, AgentTools agentTools) {
        this.llmService = llmService;
        this.agentTools = agentTools;
    }

    /**
     * SSE 事件类型
     */
    public record AgentEvent(String type, Map<String, Object> data) {
        public static AgentEvent step(AgentResponse.AgentStep step, int stepIndex) {
            Map<String, Object> d = new LinkedHashMap<>(step.toMap());
            d.put("step", stepIndex);
            return new AgentEvent("step", d);
        }

        public static AgentEvent answer(String answer) {
            return new AgentEvent("answer", Map.of("answer", answer));
        }

        public static AgentEvent error(String message) {
            return new AgentEvent("error", Map.of("message", message));
        }
    }

    /**
     * 流式执行 ReAct 循环，每步通过回调通知。
     * 支持按 conversationId 维护多轮对话上下文；不同 conversationId 彼此隔离。
     *
     * @param conversationId 会话ID；为 null/空 时按单轮对话处理（不保留历史）
     * @param userMessage    本轮用户输入
     * @param onEvent        SSE 事件回调
     */
    public void chatStream(String conversationId, String userMessage, Consumer<AgentEvent> onEvent) {
        String systemPrompt = buildSystemPrompt();
        StringBuilder conversation = new StringBuilder();

        boolean hasConversationId = conversationId != null && !conversationId.isBlank();
        if (hasConversationId) {
            String history = conversationStore.get(conversationId);
            if (history != null && !history.isBlank()) {
                conversation.append(history);
                if (!history.endsWith("\n\n")) {
                    conversation.append("\n\n");
                }
            }
        }

        conversation.append("用户问题: ").append(userMessage).append("\n\n");
        conversation.append("请开始你的推理。\n");

        long totalStart = System.currentTimeMillis();
        logger.info("=== Agent start === systemPrompt length={}, userMessage length={}",
                systemPrompt.length(), userMessage.length());

        int stepIndex = 0;
        int toolCallCount = 0;
        // 基于关键词的简单意图预判：查询/统计 → 数据查询；为什么/原因/诊断/排查/异常 → 诊断
        boolean looksLikeDataQuery = userMessage != null &&
                (userMessage.contains("查询") || userMessage.contains("统计")
                        || userMessage.contains("查看") || userMessage.contains("显示") || userMessage.contains("列出"))
                && !(userMessage.contains("为什么") || userMessage.contains("原因")
                     || userMessage.contains("诊断") || userMessage.contains("排查") || userMessage.contains("异常"));
        boolean looksLikeDiagnostic = !looksLikeDataQuery && userMessage != null &&
                (userMessage.contains("为什么") || userMessage.contains("原因")
                        || userMessage.contains("诊断") || userMessage.contains("排查") || userMessage.contains("异常"));
        // 是否已经使用过除 query_data 之外的诊断类工具（用来区分“单纯数据查询”与“真正进入诊断流程”）
        boolean usedNonQueryDataTool = false;
        // 诊断类请求的最终结论必须严格以 run_inference 的结果为准
        boolean usedRunInference = false;
        for (int i = 0; i < MAX_STEPS; i++) {
            String llmResponse;
            long llmStart = System.currentTimeMillis();
            try {
                logger.info("Step {}: calling LLM, conversation length={}", i + 1, conversation.length());
                llmResponse = llmService.chat(systemPrompt, conversation.toString());
            } catch (LLMService.LLMException e) {
                logger.error("LLM call failed at step {}: {} ({}ms)", i + 1, e.getMessage(),
                        System.currentTimeMillis() - llmStart);
                onEvent.accept(AgentEvent.error("LLM 调用失败: " + e.getMessage()));
                return;
            }
            long llmElapsed = System.currentTimeMillis() - llmStart;

            logger.info("Step {}: LLM response length={}, LLM耗时={}ms", i + 1, llmResponse.length(), llmElapsed);

            String thought = extractSection(llmResponse, "Thought");
            String answer = extractSection(llmResponse, "Answer");

            if (answer != null) {
                // 诊断类请求：最终结论必须以 run_inference 的推理结果为准。
                // 这里做硬性门槛，避免模型“只用 query_instance/query_links/call_function 拼结论”但未执行规则引擎推理。
                boolean shouldEnforceInference = looksLikeDiagnostic || usedNonQueryDataTool;
                if (shouldEnforceInference && !usedRunInference) {
                    logger.info("Step {}: LLM tried to answer without run_inference, rejecting", i + 1);
                    List<String> rootTypes = agentTools.listRootTypes();
                    String typeExamples = rootTypes.isEmpty() ? "推理对象" : String.join("/", rootTypes);
                    String reject = "诊断结论必须严格来源于 `run_inference` 的推理结果（包含触发规则与 has_abnormal_reason 异常原因事实）。"
                            + (toolCallCount == 0
                            ? "请先用 query_instance 获取当前本体（" + typeExamples + "）的 instance_id/主键信息，然后调用 run_inference。"
                            : "你似乎已经调用过一些工具。请现在补充调用 run_inference 获取最终推理结论，再输出 Answer。");
                    // 构造与真实工具调用一致的虚拟 Step，便于前端完整展示推理过程
                    stepIndex++;
                    AgentResponse.AgentStep guardStep = new AgentResponse.AgentStep(
                            thought,
                            "system_guard_must_run_inference",
                            Map.of("reason", "diagnosis_answer_without_run_inference"));
                    guardStep.setObservation(reject);
                    onEvent.accept(AgentEvent.step(guardStep, stepIndex));

                    conversation.append(llmResponse).append("\nObservation: ").append(reject).append("\n\n");
                    continue;
                }

                // 如果已经调过工具（诊断模式）但次数不够，拒绝给结论
                // 如果从未调过工具（非诊断问题，如"你是谁"），直接放行
                // 对于纯数据查询（看起来是查询/统计类且只使用了 query_data），不强制最小工具次数，避免无意义的“继续排查”
                boolean isDiagnosticMode = (looksLikeDiagnostic || usedNonQueryDataTool)
                        && (toolCallCount > 0 || extractSection(llmResponse, "Action") != null);
                if (isDiagnosticMode && toolCallCount < MIN_TOOL_CALLS) {
                    logger.info("Step {}: LLM tried to answer early (toolCalls={}), rejecting", i + 1, toolCallCount);
                    List<String> rootTypes = agentTools.listRootTypes();
                    String typeExamples = rootTypes.isEmpty() ? "推理对象" : String.join("/", rootTypes);
                    String reject = "你还没有充分调查。请继续按诊断步骤调用工具排查：" +
                            (toolCallCount == 0 ? "先用 query_instance 查询当前本体的推理对象（" + typeExamples + "）基本信息。" :
                             toolCallCount == 1 ? "接下来调用 run_inference 获取推理引擎的诊断结论。" :
                             "你已有 run_inference 的推理结论，但\\\"三、分析依据\\\"需要关联数据支撑。请调用 query_links 获取门架交易明细（如 has_gantry_transaction）和拆分明细（如 has_split_detail）的原始数据，再输出完整的 Answer。");
                    // 构造虚拟 Step，反映“守门规则”这一轮的决策与提示
                    stepIndex++;
                    AgentResponse.AgentStep guardStep = new AgentResponse.AgentStep(
                            thought,
                            "system_guard_min_tool_calls",
                            Map.of("toolCallCount", toolCallCount, "minToolCalls", MIN_TOOL_CALLS));
                    guardStep.setObservation(reject);
                    onEvent.accept(AgentEvent.step(guardStep, stepIndex));

                    conversation.append(llmResponse).append("\nObservation: ").append(reject).append("\n\n");
                    continue;
                }
                // 发送最终思考步骤
                if (thought != null) {
                    stepIndex++;
                    AgentResponse.AgentStep step = new AgentResponse.AgentStep(thought, null, null);
                    onEvent.accept(AgentEvent.step(step, stepIndex));
                }
                long totalElapsed = System.currentTimeMillis() - totalStart;
                logger.info("=== Agent finished === 总耗时={}ms, 步数={}, 工具调用={}次", totalElapsed, i + 1, toolCallCount);
                String normalizedAnswer = normalizeDiagnosisAnswer(answer, looksLikeDiagnostic || usedNonQueryDataTool);
                onEvent.accept(AgentEvent.answer(normalizedAnswer));
                if (hasConversationId) {
                    conversation.append(llmResponse).append("\n\n");
                    conversationStore.put(conversationId, conversation.toString());
                }
                return;
            }

            String actionJson = extractSection(llmResponse, "Action");
            if (actionJson == null) {
                onEvent.accept(AgentEvent.answer(llmResponse));
                return;
            }

            // 解析工具调用
            String toolName = null;
            Map<String, Object> toolArgs = null;
            try {
                Map<String, Object> action = objectMapper.readValue(actionJson, new TypeReference<>() {});
                toolName = (String) action.get("tool");
                @SuppressWarnings("unchecked")
                Map<String, Object> args = (Map<String, Object>) action.get("args");
                toolArgs = args;
            } catch (Exception e) {
                logger.warn("Failed to parse action JSON: {}", actionJson);
                stepIndex++;
                AgentResponse.AgentStep step = new AgentResponse.AgentStep(thought, "parse_error", Map.of("raw", actionJson));
                step.setObservation("Action JSON 格式错误，请按格式输出。");
                onEvent.accept(AgentEvent.step(step, stepIndex));
                conversation.append(llmResponse).append("\nObservation: Action JSON 格式错误，请按格式输出。\n\n");
                continue;
            }

            // 发送 thought+action 事件（observation 待填充）
            stepIndex++;
            AgentResponse.AgentStep step = new AgentResponse.AgentStep(thought, toolName, toolArgs);

            // 先发送 thought + action（标记 observation 为 pending）
            onEvent.accept(AgentEvent.step(step, stepIndex));

            // 执行工具
            toolCallCount++;
            long toolStart = System.currentTimeMillis();
            if (toolName != null && !"query_data".equals(toolName)) {
                usedNonQueryDataTool = true;
            }
            if ("run_inference".equals(toolName)) {
                usedRunInference = true;
            }
            String observation = agentTools.executeTool(toolName, toolArgs != null ? toolArgs : Map.of());
            long toolElapsed = System.currentTimeMillis() - toolStart;
            logger.info("Step {}: tool={}, observation length={}, 工具耗时={}ms", i + 1, toolName, observation.length(), toolElapsed);

            if (observation.length() > 3000) {
                observation = observation.substring(0, 3000) + "\n...(结果已截断)";
            }

            step.setObservation(observation);
            // 发送带 observation 的完整步骤
            onEvent.accept(AgentEvent.step(step, stepIndex));

            conversation.append(llmResponse).append("\nObservation: ").append(observation).append("\n\n");
        }

        long totalElapsed = System.currentTimeMillis() - totalStart;
        logger.info("=== Agent finished === 总耗时={}ms, 达到最大步数", totalElapsed);
        onEvent.accept(AgentEvent.error("达到最大推理步数(" + MAX_STEPS + ")"));
    }

    /**
     * 同步版本（保留向后兼容）
     */
    public AgentResponse chat(String userMessage) {
        List<AgentResponse.AgentStep> steps = new ArrayList<>();
        final String[] finalAnswer = {null};

        chatStream(null, userMessage, event -> {
            switch (event.type()) {
                case "step" -> {
                    String thought = (String) event.data().get("thought");
                    String tool = (String) event.data().get("tool");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = (Map<String, Object>) event.data().get("args");
                    String obs = (String) event.data().get("observation");
                    if (obs != null) {
                        // 只在有 observation 时添加（避免重复）
                        AgentResponse.AgentStep s = new AgentResponse.AgentStep(thought, tool, args);
                        s.setObservation(obs);
                        // 替换或添加
                        int idx = (int) event.data().get("step") - 1;
                        while (steps.size() <= idx) steps.add(null);
                        steps.set(idx, s);
                    }
                }
                case "answer" -> finalAnswer[0] = (String) event.data().get("answer");
                case "error" -> finalAnswer[0] = (String) event.data().get("message");
            }
        });

        steps.removeIf(Objects::isNull);
        return new AgentResponse(finalAnswer[0] != null ? finalAnswer[0] : "未能得出结论", steps);
    }

    /**
     * 对 LLM 输出的 Answer 进行格式规范化：
     *   - 诊断类：确保 DIAGNOSIS_START/END 标记正确，节标题统一，自动包裹等
     *   - 查询类：确保 QUERY_TABLE_START/END 标记正确；若缺失标记但含 Markdown 表格则自动包裹
     *
     * 处理的常见 LLM 输出偏差：
     * 1. 标记变体：<!-- DIAGNOSIS_START --> / <DIAGNOSIS_START> / [DIAGNOSIS_START] 等
     * 2. 节标题变体：### 一、基本信息 / **一、基本信息** / 一、基本信息 → ## 一、基本信息
     * 3. QUERY_HINT / QUERY_TABLE 标记变体或缺失
     * 4. 诊断块完全没有标记，但内容符合诊断结构 → 自动包裹
     * 5. 查询块完全没有标记，但含 Markdown 表格 → 自动包裹
     *
     * @param answer         LLM 原始 answer 文本
     * @param isDiagnostic   是否为诊断类请求（由意图判断决定）
     */
    private String normalizeDiagnosisAnswer(String answer, boolean isDiagnostic) {
        if (answer == null || answer.isBlank()) return answer;

        // ── 0. 查询表格标记规范化（优先处理，避免与诊断标记混淆）────────────────
        String text = answer;
        text = text.replaceAll("(?i)<!--\\s*QUERY_TABLE_START\\s*-->", "<!--QUERY_TABLE_START-->");
        text = text.replaceAll("(?i)<!--\\s*QUERY_TABLE_END\\s*-->",   "<!--QUERY_TABLE_END-->");
        text = text.replaceAll("(?i)<QUERY_TABLE_START>",  "<!--QUERY_TABLE_START-->");
        text = text.replaceAll("(?i)<QUERY_TABLE_END>",    "<!--QUERY_TABLE_END-->");
        text = text.replaceAll("(?i)\\[QUERY_TABLE_START]","<!--QUERY_TABLE_START-->");
        text = text.replaceAll("(?i)\\[QUERY_TABLE_END]",  "<!--QUERY_TABLE_END-->");

        boolean hasQueryStart = text.contains("<!--QUERY_TABLE_START-->");
        boolean hasQueryEnd   = text.contains("<!--QUERY_TABLE_END-->");

        // 补全缺失的结束标记
        if (hasQueryStart && !hasQueryEnd) {
            text = text + "\n<!--QUERY_TABLE_END-->";
            hasQueryEnd = true;
        }

        // 查询类但没有标记：若含 Markdown 表格（行首 |）则自动包裹
        if (!isDiagnostic && !hasQueryStart) {
            boolean hasTable = Pattern.compile("(?m)^\\|.+\\|\\s*$").matcher(text).find();
            if (hasTable) {
                logger.info("normalizeDiagnosisAnswer: query result without markers, auto-wrapping QUERY_TABLE");
                text = "<!--QUERY_TABLE_START-->\n" + text.trim() + "\n<!--QUERY_TABLE_END-->";
                hasQueryStart = true;
            }
        }

        // 若已有查询表格标记，直接返回（不做诊断块处理）
        if (hasQueryStart) return text;

        // ── 1. 统一诊断标记写法 ───────────────────────────────────────────────────
        text = text.replaceAll("(?i)<!--\\s*DIAGNOSIS_START\\s*-->", "<!--DIAGNOSIS_START-->");
        text = text.replaceAll("(?i)<!--\\s*DIAGNOSIS_END\\s*-->",   "<!--DIAGNOSIS_END-->");
        text = text.replaceAll("(?i)<!--\\s*QUERY_HINT\\s*-->",      "<!--QUERY_HINT-->");
        // <DIAGNOSIS_START> / [DIAGNOSIS_START] 变体
        text = text.replaceAll("(?i)<DIAGNOSIS_START>",  "<!--DIAGNOSIS_START-->");
        text = text.replaceAll("(?i)</DIAGNOSIS_END>",   "<!--DIAGNOSIS_END-->");
        text = text.replaceAll("(?i)<DIAGNOSIS_END>",    "<!--DIAGNOSIS_END-->");
        text = text.replaceAll("(?i)\\[DIAGNOSIS_START]","<!--DIAGNOSIS_START-->");
        text = text.replaceAll("(?i)\\[DIAGNOSIS_END]",  "<!--DIAGNOSIS_END-->");
        text = text.replaceAll("(?i)<QUERY_HINT>",       "<!--QUERY_HINT-->");
        text = text.replaceAll("(?i)\\[QUERY_HINT]",     "<!--QUERY_HINT-->");

        boolean hasStart = text.contains("<!--DIAGNOSIS_START-->");
        boolean hasEnd   = text.contains("<!--DIAGNOSIS_END-->");

        // ── 2. 如果是诊断类但完全没有标记，尝试自动包裹 ────────────────────────
        if (isDiagnostic && !hasStart) {
            boolean looksLikeDiag = text.contains("基本信息") && text.contains("分析结论");
            if (looksLikeDiag) {
                logger.info("normalizeDiagnosisAnswer: no markers found, auto-wrapping diagnosis block");
                text = "<!--DIAGNOSIS_START-->\n" + text.trim() + "\n<!--DIAGNOSIS_END-->";
                hasStart = true;
                hasEnd   = true;
            }
        }

        // ── 3. 补全缺失的结束标记 ────────────────────────────────────────────────
        if (hasStart && !hasEnd) {
            text = text + "\n<!--DIAGNOSIS_END-->";
        }

        if (!hasStart) return text; // 非诊断块，原样返回

        // ── 4. 对标记内部的内容做节标题规范化 ───────────────────────────────────
        int startIdx = text.indexOf("<!--DIAGNOSIS_START-->") + "<!--DIAGNOSIS_START-->".length();
        int endIdx   = text.indexOf("<!--DIAGNOSIS_END-->");
        if (startIdx >= endIdx) return text;

        String before = text.substring(0, startIdx);
        String inner  = text.substring(startIdx, endIdx);
        String after  = text.substring(endIdx);

        // 节标题规范化：把 ###/####/**...** 等变体统一成 ## 标题
        // 匹配：可选的 # 序列 + 空格 + 中文序号（一/二/三/四）+ 、+ 任意文字
        inner = inner.replaceAll("(?m)^#{1,6}\\s+((?:一|二|三|四)、[^\n]+)$", "## $1");
        // **一、基本信息** 变体
        inner = inner.replaceAll("(?m)^\\*{1,2}((?:一|二|三|四)、[^*\n]+)\\*{1,2}\\s*$", "## $1");
        // 裸标题（行首直接是中文序号，没有 ## 前缀）
        inner = inner.replaceAll("(?m)^((?:一|二|三|四)、[^\n|]+)$", "## $1");

        // 一句话总结：去除 **...** 包裹 或 > 引用块前缀
        // 总结是 DIAGNOSIS_START 之后、第一个 ## 之前的内容
        int firstSection = inner.indexOf("\n## ");
        if (firstSection != -1) {
            String summaryPart = inner.substring(0, firstSection);
            String restPart    = inner.substring(firstSection);
            // 去掉 **...**
            summaryPart = summaryPart.replaceAll("(?s)\\*{1,2}([^*]+)\\*{1,2}", "$1");
            // 去掉行首 >
            summaryPart = summaryPart.replaceAll("(?m)^>\\s*", "");
            inner = summaryPart + restPart;
        }

        // ── 5. 确保 QUERY_HINT 存在（若缺失则追加占位，避免前端按钮消失）────────
        if (!inner.contains("<!--QUERY_HINT-->")) {
            // 在 DIAGNOSIS_END 之前追加，内容留空（前端判断 queryHint 非空才显示按钮）
            logger.debug("normalizeDiagnosisAnswer: QUERY_HINT missing, skipping button");
        }

        String normalized = before + inner + after;
        if (!normalized.equals(answer)) {
            logger.info("normalizeDiagnosisAnswer: answer was normalized (length {} -> {})",
                    answer.length(), normalized.length());
        }
        return normalized;
    }

    private String extractSection(String text, String sectionName) {
        Pattern pattern = Pattern.compile(sectionName + ":\\s*(.*?)(?=\\n(?:Thought|Action|Answer|Observation):|$)",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String buildSystemPrompt() {
        String dataModel = agentTools.getDataModelDescription();
        return """
            你是高速公路收费拆分异常诊断助手。你可以回答用户的一般性问题，也可以帮助诊断拆分异常。

            ## 判断用户意图

            根据用户的**动词/意图**判断模式，而不是仅看是否提到了ID：

            1. **一般性问题**（"你是谁"、"什么是拆分"等）→ 直接用 Answer 回答，不调用工具。
            2. **数据查询**（"查询"、"查看"、"显示"、"列出"、"统计" + 数据）→ **这是纯数据查询，不是诊断**，不需要解释异常原因。
               - **硬性要求：凡是以"查询"/"统计"开头的请求，如果可以用 `query_data` 完成，就必须**优先调用一次 `query_data` 获取数据。
               - 只有在 `query_data` 明显无法满足该请求时，才可以再考虑使用 `query_instance` / `query_links` 等其他工具补充。
               - 例："查询某通行ID的路径明细"、"查询某通行ID的门架交易明细"、"查看所有通行记录"、"显示门架交易数据"、"统计每个入口站的通行数量"
               - 特别强调：当用户说“查询 某通行ID 的门架交易明细”时，**首先用 `query_data` 查询该通行ID相关的门架交易数据**；如果 `query_data` 返回为空或模型判断语义不匹配，再酌情调用 `query_links` 等工具。
            3. **诊断排查**（"为什么"、"诊断"、"排查"、"分析异常原因"）→ 进入诊断模式，按诊断步骤逐步调用工具。
               - 例："帮我查一下某通行ID为什么拆分异常"、"排查某通行ID的异常原因"

            **关键区别：** 提到具体ID不等于要诊断。"查询某通行ID的门架交易"是数据查询，"某通行ID为什么异常"才是诊断。

            ## 可用工具

            """ + agentTools.getToolDescriptions() + """

            ## 输出格式

            每次回复只包含以下格式之一：

            格式A - 调用工具：
            Thought: 分析当前已知信息，说明发现了什么、为什么要做下一步（2-3句）
            Action: {"tool": "工具名", "args": {参数}}

            格式B - 回答/结论（一般性问题）：
            Thought: 总结
            Answer: 回答内容

            格式D - 数据查询结果（数据查询类请求必须使用此格式）：
            Thought: 总结
            Answer:
            <!--QUERY_TABLE_START-->
            <一句话说明查询了什么，如"以下是通行 XXXX 的门架交易明细">
            | 列1 | 列2 | 列3 | ...
            |-----|-----|-----|
            | 值  | 值  | 值  |
            <!--QUERY_TABLE_END-->

            格式C - 诊断结论（诊断类请求必须使用此格式）：
            Thought: 总结
            Answer:
            <!--DIAGNOSIS_START-->
            <一句话总结诊断结论，如"通行 XXXX 诊断为异常，存在路径不一致等问题">

            ## 一、基本信息
            | 字段 | 值 |
            |------|-----|
            | 通行ID | ... |
            | 车牌号 | ... |
            | 车牌颜色 | ... |
            | 入口站 | ... |
            | 出口站 | ... |
            | 拆分时间 | ... |

            ## 二、分析结论
            <逐条列出 run_inference 返回的异常原因，每条用 - 开头>

            ## 三、分析依据
            针对"二、分析结论"中的每条异常原因，逐条写出推导过程，格式如下：
            **[异常原因名称]**
            - 依据规则：<规则名 | 规则描述>
            - 判断条件：<来自 run_inference「二、触发的规则（含推理链路）」中该规则的条件列表>
            - 关键数据：<用表格或列表列出条件对应的实际值，数据来自 run_inference 的基础事实/关键序列，或 query_links 的原始返回值>
            - 推导结论：<一句话说明为什么上述数据满足该规则条件，从而触发该异常>

            <!--QUERY_HINT-->
            <在这里用 1-3 条自然语言问题，给出用户可能关心的后续查询，优先基于你在"三、分析依据"中实际用到的业务数据。
            例如当前场景，可输出：
            - "查询 通行 XXXX 的拆分明细"
            - "查询 通行 XXXX 的门架交易明细"
            这样用户在"问数"界面可以直接按这些问题查询到与你分析依据一致的真实业务数据，从而增强信任度。>
            <!--DIAGNOSIS_END-->

            ## 数据模型（用于理解工具参数，来自当前加载的本体）

            """ + dataModel + """

            ## 推理要求

            - 根据用户意图自主决定调用哪些工具、调用顺序和调用次数
            - 数据查询类请求：**必须优先尝试使用 `query_data` 获取数据**，然后用**格式D**展示结果（包含 `<!--QUERY_TABLE_START-->` 与 `<!--QUERY_TABLE_END-->` 标记，一句话说明 + Markdown 表格），不得使用格式C（诊断卡片格式）
            - 诊断类请求：必须先调用 `run_inference` 获取规则引擎的推理结论；最终异常原因列表必须严格使用 `run_inference` 返回内容中对应的 has_abnormal_reason 事实（或其“异常原因列表”部分）。`search_rules` / `call_function` 仅用于补充解释与验证，不得替代 `run_inference` 的最终结论
            - 诊断类请求的 Answer 必须严格使用**格式C**，包含 `<!--DIAGNOSIS_START-->` 与 `<!--DIAGNOSIS_END-->` 标记，以及完整的四节内容（一句话总结、一、基本信息、二、分析结论、三、分析依据）；基本信息从 query_instance 结果中提取；**分析依据必须针对每条异常原因逐条写出推导过程**：依据规则来自 `run_inference` 的「二、触发的规则（含推理链路）」，判断条件和实际值来自该规则的推理链路条目，路径序列来自 `run_inference` 的「四、推理引擎计算的关键序列」，数量类数据来自 `query_links` 实际返回值；严禁根据 query_links 的原始 JSON 字段（如 gantry_id、gantry_hex 等）自行推算任何序列或结论
            - Thought 中要体现分析推理过程（发现了什么、推测什么原因、为什么要做下一步），而不仅仅是"我要调用某工具"
            - 每次只输出一个 Action
            - 给出最终结论前：如果还没有 `run_inference` 的结果（诊断类请求场景），不得输出 `Answer`，必须继续调用工具直到拿到 `run_inference` 结论
            - 诊断类请求的标准工具调用顺序：① `query_instance` 获取实例基本信息 → ② `run_inference` 获取推理结论 → ③ `query_links` 获取门架交易明细（has_gantry_transaction）和拆分明细（has_split_detail）原始数据 → 最后输出 Answer；**在完成步骤③之前不得输出 Answer**
            """;
    }
}
