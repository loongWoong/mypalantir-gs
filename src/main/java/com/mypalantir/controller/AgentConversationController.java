package com.mypalantir.controller;

import com.mypalantir.repository.AgentConversationRepository;
import com.mypalantir.repository.AgentMessageRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent/conversations")
public class AgentConversationController {

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;

    public AgentConversationController(AgentConversationRepository conversationRepository,
                                       AgentMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    private String getClientId(String header) {
        // 简单从请求头读取客户端标识；实际项目可替换为登录用户ID
        return (header != null && !header.isBlank()) ? header : "anonymous";
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createConversation(
        @RequestHeader(value = "X-Client-Id", required = false) String clientHeader,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String userId = getClientId(clientHeader);
        String id = java.util.UUID.randomUUID().toString();
        String title = body != null && body.get("title") != null && !body.get("title").isBlank()
            ? body.get("title")
            : "新对话";
        conversationRepository.createConversation(id, userId, title);
        return ApiResponse.success(Map.of("id", id, "title", title));
    }

    @GetMapping
    public ApiResponse<List<AgentConversationRepository.ConversationSummary>> list(
        @RequestHeader(value = "X-Client-Id", required = false) String clientHeader,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        String userId = getClientId(clientHeader);
        return ApiResponse.success(conversationRepository.listByUser(userId, limit, offset));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<Map<String, Object>> messages(
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "200") int limit
    ) {
        var msgs = messageRepository.listMessages(conversationId, limit);
        return ApiResponse.success(Map.of("conversationId", conversationId, "messages", msgs));
    }
}

