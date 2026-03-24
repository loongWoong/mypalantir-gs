package com.mypalantir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypalantir.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestParam String message,
                                  @RequestParam(required = false) String widgets,
                                  @RequestParam(required = false) String modelId) {
        SseEmitter emitter = new SseEmitter(300_000L);

        executor.execute(() -> {
            try {
                dashboardService.chatStream(message, widgets, modelId, event -> {
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
                logger.error("Dashboard stream error: {}", e.getMessage());
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
