package com.mypalantir.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 会话记忆管理
 * 基于 LangChain4j ChatMemory 接口，按消息条数自动淘汰旧消息
 * 每个 sessionId 对应一个独立的记忆窗口，30 分钟无访问自动清理
 */
@Service
public class AgentMemoryService {
    private static final Logger logger = LoggerFactory.getLogger(AgentMemoryService.class);
    private static final int MAX_MESSAGES = 20; // 10 轮对话
    private static final long SESSION_TTL_MS = 30 * 60 * 1000; // 30 min

    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private volatile Instant lastCleanup = Instant.now();

    public void addUserMessage(String sessionId, String message) {
        getEntry(sessionId).memory.add(UserMessage.from(message));
    }

    public void addAiMessage(String sessionId, String message) {
        getEntry(sessionId).memory.add(AiMessage.from(message));
    }

    public List<ChatMessage> getMessages(String sessionId) {
        SessionEntry entry = sessions.get(sessionId);
        if (entry == null) return List.of();
        entry.lastAccess = Instant.now();
        return entry.memory.messages();
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    private SessionEntry getEntry(String sessionId) {
        cleanupIfNeeded();
        SessionEntry entry = sessions.computeIfAbsent(sessionId,
            id -> new SessionEntry(new WindowChatMemory(sessionId, MAX_MESSAGES)));
        entry.lastAccess = Instant.now();
        return entry;
    }

    private void cleanupIfNeeded() {
        Instant now = Instant.now();
        if (now.isAfter(lastCleanup.plusSeconds(60))) {
            lastCleanup = now;
            Instant cutoff = now.minusMillis(SESSION_TTL_MS);
            int before = sessions.size();
            sessions.entrySet().removeIf(e -> e.getValue().lastAccess.isBefore(cutoff));
            int removed = before - sessions.size();
            if (removed > 0) {
                logger.info("Cleaned up {} expired agent sessions", removed);
            }
        }
    }

    /**
     * 简单的滑动窗口 ChatMemory 实现
     * 保留最近 maxMessages 条消息，超出时淘汰最旧的
     */
    static class WindowChatMemory implements ChatMemory {
        private final Object id;
        private final int maxMessages;
        private final List<ChatMessage> messages = new ArrayList<>();

        WindowChatMemory(Object id, int maxMessages) {
            this.id = id;
            this.maxMessages = maxMessages;
        }

        @Override
        public Object id() { return id; }

        @Override
        public void add(ChatMessage message) {
            messages.add(message);
            while (messages.size() > maxMessages) {
                messages.remove(0);
            }
        }

        @Override
        public List<ChatMessage> messages() {
            return Collections.unmodifiableList(new ArrayList<>(messages));
        }

        @Override
        public void clear() {
            messages.clear();
        }
    }

    private static class SessionEntry {
        final ChatMemory memory;
        volatile Instant lastAccess;

        SessionEntry(ChatMemory memory) {
            this.memory = memory;
            this.lastAccess = Instant.now();
        }
    }
}
