package com.mypalantir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.agent.AgentResponse;
import com.mypalantir.agent.AgentService;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
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
     * GET /api/v1/agent/chat/stream?message=xxx
     */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestParam String message) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        executor.execute(() -> {
            try {
                agentService.chatStream(message, event -> {
                    try {
                        String json = objectMapper.writeValueAsString(event.data());
                        emitter.send(SseEmitter.event()
                                .name(event.type())
                                .data(json));
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
