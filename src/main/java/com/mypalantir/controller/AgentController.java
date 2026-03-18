package com.mypalantir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.agent.AgentResponse;
import com.mypalantir.agent.AgentService;
import com.mypalantir.repository.AgentMessageRepository;
import com.mypalantir.repository.AgentConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    private final AgentService agentService;
    private final AgentMessageRepository messageRepository;
    private final AgentConversationRepository conversationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AgentController(AgentService agentService,
                           AgentMessageRepository messageRepository,
                           AgentConversationRepository conversationRepository) {
        this.agentService = agentService;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    /**
     * Agent 对话（同步版本，保留向后兼容）
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isEmpty()) {
            return ApiResponse.error(400, "message is required");
        }
        try {
            AgentResponse response = agentService.chat(message);
            return ApiResponse.success(response.toMap());
        } catch (Exception e) {
            return ApiResponse.error(500, "Agent error: " + e.getMessage());
        }
    }

    /**
     * Agent 对话（SSE 流式版本）
     * GET /api/v1/agent/chat/stream?message=xxx&conversationId=uuid
     */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestParam String message,
                                 @RequestParam String conversationId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        if (conversationId != null && !conversationId.isBlank()) {
            // 首条用户消息到达时，如果标题还是默认值，则用该问题更新会话标题
            try {
                conversationRepository.updateTitleIfDefault(conversationId, message);
            } catch (Exception e) {
                logger.warn("Failed to update conversation title: {}", e.getMessage());
            }
            messageRepository.saveMessage(conversationId, "user", message);
        }

        executor.execute(() -> {
            try {
                agentService.chatStream(conversationId, message, event -> {
                    try {
                        String json = objectMapper.writeValueAsString(event.data());
                        emitter.send(SseEmitter.event()
                                .name(event.type())
                                .data(json));

                        if ("answer".equals(event.type()) && conversationId != null && !conversationId.isBlank()) {
                            String answer = (String) event.data().get("answer");
                            messageRepository.saveMessage(conversationId, "assistant", answer);
                            String preview = answer != null && answer.length() > 100 ? answer.substring(0, 100) : answer;
                            conversationRepository.updateConversationPreview(conversationId, preview);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to send SSE event: {}", e.getMessage());
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                logger.error("Agent stream error: {}", e.getMessage());
                try {
                    String errorJson = objectMapper.writeValueAsString(Map.of("message", e.getMessage()));
                    emitter.send(SseEmitter.event().name("error").data(errorJson));
                } catch (Exception ignored) {}
                emitter.complete();
            }
        });

        return emitter;
    }
}
