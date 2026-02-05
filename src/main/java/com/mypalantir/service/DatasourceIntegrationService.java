package com.mypalantir.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源集成服务
 * 封装与 dome-datasource 服务的交互
 */
@Service
public class DatasourceIntegrationService {
    
    @Value("${dome.datasource.base-url:http://localhost:8080}")
    private String datasourceBaseUrl;
    
    @Autowired(required = false)
    private DomeAuthService domeAuthService;
    
    private final RestTemplate restTemplate;
    
    public DatasourceIntegrationService() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 创建带认证头的 HttpHeaders
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        
        // 使用 DomeAuthService 自动获取 token
        if (domeAuthService != null) {
            String token = domeAuthService.getToken();
            if (token != null && !token.isEmpty()) {
                headers.set("dome-auth", token);
            }
        }
        
        return headers;
    }
    
    /**
     * 获取数据源列表
     */
    public List<Map<String, Object>> getDatasourceList() {
        String url = datasourceBaseUrl + "/datasourceManager/list";
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.get("data") != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            return data;
        }
        return List.of();
    }
    
    /**
     * 根据数据源ID获取数据源详情
     */
    public Map<String, Object> getDatasourceById(Long datasourceId) {
        String url = datasourceBaseUrl + "/datasourceManager/detail?id=" + datasourceId;
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.get("data") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            return data;
        }
        return new HashMap<>();
    }
    
    /**
     * 获取数据源的表列表
     */
    public List<String> getTableList(Long datasourceId) {
        String url = datasourceBaseUrl + "/datasourceManager/showTables?datasourceId=" + datasourceId;
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.get("data") != null) {
            @SuppressWarnings("unchecked")
            List<String> data = (List<String>) responseBody.get("data");
            return data;
        }
        return List.of();
    }
    
    /**
     * 获取表的字段信息
     */
    public List<Map<String, Object>> getTableFieldInfo(Long datasourceId, String tableName) {
        String url = datasourceBaseUrl + "/datasourceManager/getFieldInfo?datasourceId=" + 
                     datasourceId + "&tableName=" + tableName;
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.get("data") != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            return data;
        }
        return List.of();
    }
    
    /**
     * 获取数据源支持的索引类型
     */
    public List<String> getSupportIndexType(Long datasourceId) {
        String url = datasourceBaseUrl + "/datasourceManager/getIndexType?datasourceId=" + datasourceId;
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.get("data") != null) {
            @SuppressWarnings("unchecked")
            List<String> data = (List<String>) responseBody.get("data");
            return data;
        }
        return List.of();
    }
}

