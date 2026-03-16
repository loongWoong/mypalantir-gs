package com.mypalantir.reasoning;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * 推理过程日志工具：将推理过程信息追加写入 logs/Reasoning.log，同时输出到控制台。
 *
 * 使用方式：
 *   try (ReasoningLogger log = ReasoningLogger.open()) {
 *       log.section("开始推理", "Passage", "id-xxx");
 *       log.line("  属性: " + props);
 *       log.flush();
 *   }
 */
public class ReasoningLogger implements AutoCloseable {

    private static final Path LOG_PATH = Paths.get("logs", "Reasoning.log");

    private final BufferedWriter writer;

    private ReasoningLogger(BufferedWriter writer) {
        this.writer = writer;
    }

    /** 打开日志文件（追加模式），调用方用 try-with-resources 确保关闭 */
    public static ReasoningLogger open() throws IOException {
        Files.createDirectories(LOG_PATH.getParent());
        BufferedWriter w = Files.newBufferedWriter(
            LOG_PATH,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
        return new ReasoningLogger(w);
    }

    /** 写一行（不含换行），同时打印到控制台 */
    public void line(String text) {
        System.out.println(text);
        try {
            writer.write(text);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("[ReasoningLogger] write error: " + e.getMessage());
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

    /** 写带时间戳的标题行 */
    public void header(String tag, String objectType, String instanceId) {
        line("[" + LocalDateTime.now() + "] [" + tag + "] " + objectType + " | id=" + instanceId);
    }

    /** 写空行 */
    public void blank() {
        line("");
    }

    /** 刷新缓冲区 */
    public void flush() {
        try {
            writer.flush();
        } catch (IOException e) {
            System.err.println("[ReasoningLogger] flush error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.err.println("[ReasoningLogger] close error: " + e.getMessage());
        }
    }
}
