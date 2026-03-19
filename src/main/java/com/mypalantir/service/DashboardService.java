package com.mypalantir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.OntologySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Dashboard 服务
 * 将用户对话转换为 Widget 操作指令
 */
@Service
public class DashboardService {
    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    private final LLMService llmService;
    private final OntologySummaryService ontologySummaryService;
    private final Loader loader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashboardService(LLMService llmService, OntologySummaryService ontologySummaryService, Loader loader) {
        this.llmService = llmService;
        this.ontologySummaryService = ontologySummaryService;
        this.loader = loader;
    }

    public record DashboardEvent(String type, Object data) {}

    /**
     * 流式处理 Dashboard 对话
     */
    public void chatStream(String userMessage, String currentWidgetsJson,
                           java.util.function.Consumer<DashboardEvent> onEvent) {
        try {
            // Step 1: 分析意图
            onEvent.accept(new DashboardEvent("thinking",
                    java.util.Map.of("step", "analyze", "content", "正在分析用户意图: " + userMessage)));

            String ontologySummary = generateCompactSummary();
            String systemPrompt = buildSystemPrompt(ontologySummary, currentWidgetsJson);
            String userPrompt = "用户指令：" + userMessage;

            logger.info("Dashboard chat: {}", userMessage);

            // Step 2: 调用 LLM
            onEvent.accept(new DashboardEvent("thinking",
                    java.util.Map.of("step", "llm", "content", "正在调用 LLM 生成仪表盘配置（可能需要 10~30 秒）...")));

            String response = llmService.chat(systemPrompt, userPrompt);
            response = cleanJsonResponse(response);

            logger.debug("Dashboard LLM response: {}", response);

            // Step 3: 解析结果
            onEvent.accept(new DashboardEvent("thinking",
                    java.util.Map.of("step", "parse", "content", "正在解析 LLM 响应并生成 Widget 操作指令...")));

            // 解析 JSON
            var jsonNode = objectMapper.readTree(response);

            // 发送 widget_ops 事件
            if (jsonNode.has("operations")) {
                var ops = objectMapper.readValue(jsonNode.get("operations").toString(), Object.class);
                onEvent.accept(new DashboardEvent("thinking",
                        java.util.Map.of("step", "apply", "content",
                                "生成了 " + jsonNode.get("operations").size() + " 个 Widget 操作，正在应用...")));
                onEvent.accept(new DashboardEvent("widget_ops", ops));
            }

            // 发送 message 事件
            String message = jsonNode.has("message") ? jsonNode.get("message").asText() : "已更新仪表盘";
            onEvent.accept(new DashboardEvent("message", java.util.Map.of("message", message)));

        } catch (Exception e) {
            logger.error("Dashboard chat error: {}", e.getMessage());
            onEvent.accept(new DashboardEvent("error", java.util.Map.of("message", e.getMessage())));
        }
    }

    /**
     * 生成精简版 ontology 摘要，只包含对象名称、描述和属性名列表
     * 大幅减少 token 数量，加速 LLM 响应
     */
    private String generateCompactSummary() {
        OntologySchema schema = loader.getSchema();
        StringBuilder sb = new StringBuilder();

        sb.append("对象类型:\n");
        if (schema.getObjectTypes() != null) {
            for (var ot : schema.getObjectTypes()) {
                sb.append("- ").append(ot.getName());
                if (ot.getDescription() != null) sb.append(" (").append(ot.getDescription()).append(")");
                if (ot.getProperties() != null && !ot.getProperties().isEmpty()) {
                    sb.append(": ");
                    sb.append(ot.getProperties().stream()
                            .map(p -> p.getName() + "[" + (p.getDataType() != null ? p.getDataType() : "string") + "]")
                            .collect(Collectors.joining(", ")));
                }
                sb.append("\n");
            }
        }

        sb.append("\n关联类型:\n");
        if (schema.getLinkTypes() != null) {
            for (var lt : schema.getLinkTypes()) {
                sb.append("- ").append(lt.getName()).append(": ")
                        .append(lt.getSourceType()).append(" -> ").append(lt.getTargetType());
                if (lt.getDescription() != null) sb.append(" (").append(lt.getDescription()).append(")");
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String buildSystemPrompt(String ontologySummary, String currentWidgetsJson) {
        return String.format("""
            你是一个数据仪表盘助手。用户描述他们想在仪表盘上看到什么，你输出 widget 操作指令。

            ## 当前仪表盘状态
            %s

            ## Ontology Schema 摘要
            %s

            ## 输出格式
            严格输出 JSON，不要包含任何解释文字或 markdown 代码块标记。格式：
            {
              "operations": [
                {
                  "action": "add",
                  "widgetId": "w1",
                  "spec": {
                    "type": "line",
                    "title": "图表标题",
                    "query": "用于获取数据的自然语言查询",
                    "size": "2x1",
                    "options": {
                      "xField": "X轴字段名",
                      "yField": "Y轴字段名",
                      "seriesField": "系列字段名"
                    }
                  }
                }
              ],
              "message": "回复文字"
            }

            ## 操作类型
            - add: 添加新 widget。必须包含完整 spec。
            - update: 修改已有 widget。spec 中只包含要修改的字段，会与现有 spec 合并。
            - remove: 删除 widget。只需 widgetId。

            ## Widget 类型
            - line: 折线图（需要 xField, yField, 可选 seriesField）
            - bar: 柱状图（需要 xField, yField, 可选 seriesField）
            - pie: 饼图（需要 nameField, valueField）
            - metric: 指标卡（需要 valueField, 可选 labelField）
            - table: 数据表格（无需额外 options）

            ## 尺寸选项
            - "1x1": 半宽矮（仅用于指标卡 metric）
            - "2x1": 全宽矮（仅用于指标卡横排多个时）
            - "1x2": 半宽高（适合饼图、柱状图、折线图并排放置）
            - "2x2": 全宽高（适合大表格、复杂图表独占一行）

            尺寸选择规则：
            - metric 类型默认用 "1x1"
            - 图表类型（line/bar/pie）并排时用 "1x2"，独占一行时用 "2x2"
            - table 类型默认用 "2x2"

            ## widgetId 规则
            - 新增 widget 时，生成 "w" + 递增数字（如 w1, w2, w3）
            - 如果当前仪表盘已有 widget，新 ID 从最大现有数字+1 开始

            ## 重要规则
            1. query 字段必须是可以被自然语言查询服务理解的中文查询语句
            2. 用户说"换成饼图"等修改请求时，使用 update 操作
            3. 用户说"删掉/移除"时，使用 remove 操作
            4. 用户说"加一个/添加"时，使用 add 操作
            5. 一次可以输出多个操作
            6. options 中的字段名应与 query 返回结果的列名一致
            """, currentWidgetsJson != null ? currentWidgetsJson : "空（尚无 widget）", ontologySummary);
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return null;
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }
}
