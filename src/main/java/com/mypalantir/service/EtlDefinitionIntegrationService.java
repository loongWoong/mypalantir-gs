package com.mypalantir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * ETL定义集成服务
 * 封装与 dome-scheduler 服务的交互
 */
@Service
public class EtlDefinitionIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(EtlDefinitionIntegrationService.class);
    
    @Value("${dome.scheduler.base-url:http://localhost:8080}")
    private String schedulerBaseUrl;
    
    @Autowired(required = false)
    private DomeAuthService domeAuthService;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public EtlDefinitionIntegrationService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        // 配置错误处理器，不抛出异常，而是返回ResponseEntity以便手动处理
        this.restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                // 返回false表示不将任何响应视为错误，由调用方手动处理
                return false;
            }
            
            @Override
            public void handleError(ClientHttpResponse response) {
                // 不做任何处理，因为我们会在调用方手动检查状态码
            }
        });
        this.objectMapper = objectMapper;
    }
    
    /**
     * 创建 ETL 定义
     */
    public Map<String, Object> createEtlDefinition(Map<String, Object> etlModel) {
        String url = schedulerBaseUrl + "/etlDefinition";
        
        // 在发送请求前，处理id字段以避免外部服务的NPE问题
        // 如果id存在但可能不存在于数据库中，移除id字段，让外部服务将其视为新创建
        Map<String, Object> processedEtlModel = new java.util.HashMap<>(etlModel);
        Object idObj = processedEtlModel.get("id");
        if (idObj != null) {
            try {
                // 尝试将id转换为Long，如果转换失败或id无效，则移除id字段
                Long id = Long.valueOf(idObj.toString());
                //TODO 直接删除
                processedEtlModel.remove("id");
                // 如果id存在，尝试先查询该ID是否存在，避免外部服务NPE
                // 如果查询失败或ID不存在，移除id字段
                if (!isEtlDefinitionExists(id)) {
                    logger.info("ETL定义ID {} 不存在，将作为新创建处理，移除id字段", id);
                    processedEtlModel.remove("id");
                } else {
                    logger.info("ETL定义ID {} 存在，将作为更新处理", id);
                }
            } catch (NumberFormatException e) {
                // id格式无效，移除id字段
                logger.warn("ETL定义ID格式无效: {}，将作为新创建处理，移除id字段", idObj);
                processedEtlModel.remove("id");
            } catch (Exception e) {
                // 查询过程中出现异常，为安全起见移除id字段
                logger.warn("检查ETL定义ID {} 是否存在时发生异常: {}，将作为新创建处理，移除id字段", idObj, e.getMessage());
                processedEtlModel.remove("id");
            }
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 使用 DomeAuthService 自动获取 token
        if (domeAuthService != null) {
            String token = domeAuthService.getToken();
            if (token != null && !token.isEmpty()) {
                headers.set("dome-auth", token);
            }
        }
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(processedEtlModel, headers);
        
        // 打印ETL模型JSON格式，方便检查数据格式和负载数据问题
        try {
            String etlModelJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(processedEtlModel);
            logger.info("=== ETL模型请求信息 ===");
            logger.info("请求URL: {}", url);
            logger.info("请求方法: POST");
            logger.info("请求头: Content-Type={}, dome-auth={}", 
                headers.getContentType(), 
                headers.get("dome-auth") != null ? "已设置" : "未设置");
            logger.info("ETL模型JSON负载:\n{}", etlModelJson);
            logger.info("====================");
        } catch (Exception e) {
            logger.warn("无法将ETL模型转换为JSON字符串: {}", e.getMessage());
            logger.info("ETL模型原始数据: {}", processedEtlModel);
        }
        
        try {
            // 使用exchange方法，以便更好地控制错误处理
            ResponseEntity<String> stringResponse = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);
            
            // 检查响应状态码
            if (!stringResponse.getStatusCode().is2xxSuccessful()) {
                String errorBody = stringResponse.getBody();
                String contentType = stringResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                logger.error("dome-scheduler返回错误响应: status={}, contentType={}, body={}", 
                    stringResponse.getStatusCode(), contentType, errorBody);
                
                throw new RuntimeException(String.format(
                    "dome-scheduler服务返回错误: HTTP %s, Content-Type: %s, 响应内容: %s",
                    stringResponse.getStatusCode(), contentType, 
                    errorBody != null && errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody
                ));
            }
            
            // 如果响应是JSON格式，尝试解析为Map
            String contentType = stringResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            if (contentType != null && contentType.contains("application/json")) {
                try {
                    // 使用ObjectMapper解析JSON响应体
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseBody = objectMapper.readValue(
                        stringResponse.getBody(), Map.class);
                    return responseBody != null ? responseBody : Map.of();
                } catch (Exception e) {
                    logger.warn("无法将JSON响应解析为Map: {}", e.getMessage());
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("rawResponse", stringResponse.getBody());
                    return result;
                }
            } else {
                // 非JSON响应，返回原始内容
                logger.warn("dome-scheduler返回非JSON响应: contentType={}", contentType);
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("rawResponse", stringResponse.getBody());
                result.put("contentType", contentType);
                return result;
            }
            
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // 处理HTTP 4xx/5xx错误
            String errorBody = e.getResponseBodyAsString();
            String contentType = e.getResponseHeaders() != null ? 
                e.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE) : "unknown";
            
            logger.error("调用dome-scheduler接口失败: status={}, contentType={}, url={}, body={}", 
                e.getStatusCode(), contentType, url, errorBody);
            
            throw new RuntimeException(String.format(
                "dome-scheduler服务调用失败: HTTP %s, Content-Type: %s, URL: %s, 错误信息: %s",
                e.getStatusCode(), contentType, url,
                errorBody != null && errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody
            ), e);
            
        } catch (RestClientException e) {
            // 处理其他RestTemplate异常（网络错误、超时等）
            logger.error("调用dome-scheduler接口时发生异常: url={}, error={}", url, e.getMessage(), e);
            throw new RuntimeException("调用dome-scheduler服务失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查ETL定义是否存在
     * @param id ETL定义ID
     * @return 如果存在返回true，否则返回false
     */
    private boolean isEtlDefinitionExists(Long id) {
        try {
            String url = schedulerBaseUrl + "/etlDefinition/" + id;
            HttpHeaders headers = new HttpHeaders();
            
            // 使用 DomeAuthService 自动获取 token
            if (domeAuthService != null) {
                String token = domeAuthService.getToken();
                if (token != null && !token.isEmpty()) {
                    headers.set("dome-auth", token);
                }
            }
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class);
            
            // 如果返回2xx状态码，说明ETL定义存在
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            // 查询失败（可能是404或其他错误），认为不存在
            logger.debug("查询ETL定义ID {} 是否存在时发生异常: {}", id, e.getMessage());
            return false;
        }
    }
}

