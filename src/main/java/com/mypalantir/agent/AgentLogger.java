package com.mypalantir.agent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 调用过程日志工具：将 Agent 的工具调用、参数和结果追加写入 logs/Agent.log，方便排查 ReAct 逻辑问题。
 *
 * 使用方式：
 *   try (AgentLogger log = AgentLogger.open()) {
 *       log.beginCall("run_inference", args);
 *       log.result(resultString);
 *   }
 */
public class AgentLogger implements AutoCloseable {

    private static final Path LOG_PATH = Paths.get("logs", "Agent.log");

    private final BufferedWriter writer;

    private AgentLogger(BufferedWriter writer) {
        this.writer = writer;
    }

    /** 打开日志文件（追加模式），调用方用 try-with-resources 确保关闭 */
    public static AgentLogger open() throws IOException {
        Files.createDirectories(LOG_PATH.getParent());
        BufferedWriter w = Files.newBufferedWriter(
            LOG_PATH,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
        return new AgentLogger(w);
    }

    /** 写一行（同时输出到控制台），自动换行 */
    public void line(String text) {
        System.out.println(text);
        try {
            writer.write(text);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("[AgentLogger] write error: " + e.getMessage());
        }
    }

    /** 写分隔线 */
    public void separator() {
        line("================================================================================");
    }

    /** 写细分隔线 */
    public void subSeparator() {
        line("--------------------------------------------------------------------------------");
    }

    /** 记录一次工具调用开始 */
    public void beginCall(String toolName, Map<String, Object> args) {
        separator();
        line("[Agent] " + LocalDateTime.now() + " 调用工具: " + toolName);
        if (args != null && !args.isEmpty()) {
            line("[Agent] 参数: " + args);
        } else {
            line("[Agent] 参数: {}");
        }
    }

    /** 记录正常返回结果（截断过长输出） */
    public void result(String result) {
        if (result == null) {
            line("[Agent] 结果: null");
            return;
        }
        String trimmed = result.length() > 4000 ? result.substring(0, 4000) + " ... (truncated)" : result;
        line("[Agent] 结果: " + trimmed);
    }

    /** 记录异常信息和堆栈摘要 */
    public void error(Throwable e) {
        if (e == null) return;
        line("[Agent] 异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        for (int i = 0; i < Math.min(5, e.getStackTrace().length); i++) {
            line("    at " + e.getStackTrace()[i]);
        }
    }

    @Override
    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.err.println("[AgentLogger] close error: " + e.getMessage());
        }
    }
}

