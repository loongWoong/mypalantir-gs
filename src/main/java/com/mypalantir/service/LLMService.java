package com.mypalantir.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.*;

/**
 * LLM 服务
 * 支持 OpenAI 兼容的 API
 */
@Service
public class LLMService {
    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);
    
    // 优先从环境变量读取，然后从 application.properties 读取
    @Value("${LLM_API_KEY:${llm.api.key:}}")
    private String apiKey;
    
    @Value("${LLM_API_URL:${llm.api.url:https://api.deepseek.com/v1/chat/completions}}")
    private String apiUrl;
    
    @Value("${LLM_MODEL:${llm.model:deepseek-chat}}")
    private String model;
    
    @Value("${llm.temperature:0.1}")
    private double temperature;
    
    @Value("${llm.max.retries:3}")
    private int maxRetries;
    
    @Value("${llm.timeout:30000}")
    private int timeout;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public LLMService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 调用 LLM API
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return LLM 返回的 JSON 字符串
     */
    public String chat(String systemPrompt, String userPrompt) throws LLMException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new LLMException("LLM API key not configured. Please set llm.api.key in application.properties");
        }
        
        int retries = 0;
        Exception lastException = null;
        
        while (retries < maxRetries) {
            try {
                return doChat(systemPrompt, userPrompt);
            } catch (RestClientException e) {
                lastException = e;
                retries++;
                logger.warn("LLM API call failed (attempt {}/{}): {}", retries, maxRetries, e.getMessage());
                
                if (retries < maxRetries) {
                    try {
                        // 指数退避
                        Thread.sleep(1000 * (long) Math.pow(2, retries - 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LLMException("Interrupted during retry", ie);
                    }
                }
            } catch (Exception e) {
                throw new LLMException("LLM API call failed: " + e.getMessage(), e);
            }
        }
        
        throw new LLMException("LLM API call failed after " + maxRetries + " retries", lastException);
    }
    
    private String doChat(String systemPrompt, String userPrompt) throws LLMException {
        try {
            // 构建请求
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("temperature", temperature);
            request.put("messages", Arrays.asList(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ));
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            logger.debug("Calling LLM API: {}", apiUrl);
            
            // 调用 API
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new LLMException("LLM API returned error: " + response.getStatusCode() + " - " + response.getBody());
            }
            
            // 解析响应
            String responseBody = response.getBody();
            if (responseBody == null) {
                throw new LLMException("LLM API returned empty response");
            }
            
            JsonNode jsonNode;
            try {
                jsonNode = objectMapper.readTree(responseBody);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new LLMException("Failed to parse LLM API response: " + e.getMessage(), e);
            }
            JsonNode choices = jsonNode.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                throw new LLMException("LLM API response missing choices: " + responseBody);
            }
            
            JsonNode message = choices.get(0).get("message");
            if (message == null) {
                throw new LLMException("LLM API response missing message: " + responseBody);
            }
            
            JsonNode content = message.get("content");
            if (content == null) {
                throw new LLMException("LLM API response missing content: " + responseBody);
            }
            
            String result = content.asText();
            logger.debug("LLM response received: {}", result);
            
            return result;
            
        } catch (Exception e) {
            if (e instanceof LLMException) {
                throw e;
            }
            throw new LLMException("Failed to call LLM API: " + e.getMessage(), e);
        }
    }
    
    /**
     * LLM 异常类
     */
    public static class LLMException extends Exception {
        public LLMException(String message) {
            super(message);
        }
        
        public LLMException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

