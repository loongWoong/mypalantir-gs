package com.mypalantir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dome-Auth 认证服务
 * 根据第三方接入规范，自动获取和刷新 token
 */
@Service
public class DomeAuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(DomeAuthService.class);
    
    // SM2 公钥（未压缩格式，以04开头）
    private static final String SM2_PUBLIC_KEY = "046c1a7406af8e5b3dcfbaf79dd1c4528ec9236679ee34c2605fea2c2b9b1978b7d3cdb798a406a8ced59c6080106b1f0726423c69be95615262c9ae961b7f6ac0";
    
    static {
        // 注册 BouncyCastle 提供者
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
    
    @Value("${dome.auth.base-url:}")
    private String authBaseUrl;
    
    @Value("${dome.datasource.base-url:}")
    private String datasourceBaseUrl;
    
    @Value("${dome.scheduler.base-url:}")
    private String schedulerBaseUrl;
    
    @Value("${dome.auth.client-id:sword}")
    private String clientId;
    
    @Value("${dome.auth.client-secret:sword_secret}")
    private String clientSecret;
    
    @Value("${dome.auth.username:admin}")
    private String username;
    
    @Value("${dome.auth.password:}")
    private String password;
    
    @Value("${dome.auth.tenant-id:}")
    private String tenantId;
    
    @Value("${dome.auth.enabled:true}")
    private boolean authEnabled;
    
    @Value("${dome.auth.token:}")
    private String staticToken;
    
    private String accessToken;
    private String refreshToken;
    private String tokenType = "bearer";
    private long tokenExpiresAt = 0;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock = new ReentrantLock();
    
    public DomeAuthService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void init() {
        // 如果配置了静态 token，优先使用静态 token
        if (staticToken != null && !staticToken.isEmpty()) {
            logger.info("[DomeAuthService] 使用配置的静态 token");
            this.accessToken = staticToken.startsWith("bearer ") 
                ? staticToken.substring(7) 
                : staticToken;
            return;
        }
        
        // 如果禁用了自动认证，直接返回
        if (!authEnabled) {
            logger.warn("[DomeAuthService] 自动认证已禁用，将使用空 token");
            return;
        }
        
        // 如果没有配置 authBaseUrl，尝试从 datasourceBaseUrl 或 schedulerBaseUrl 中提取
        if (authBaseUrl == null || authBaseUrl.isEmpty()) {
            authBaseUrl = extractBaseUrl(datasourceBaseUrl);
            if (authBaseUrl == null || authBaseUrl.isEmpty()) {
                authBaseUrl = extractBaseUrl(schedulerBaseUrl);
            }
        }
        
        // 检查必要的配置
        if (authBaseUrl == null || authBaseUrl.isEmpty()) {
            logger.warn("[DomeAuthService] dome.auth.base-url 未配置，且无法从 dome.datasource.base-url 或 dome.scheduler.base-url 中提取，无法自动获取 token");
            return;
        }
        
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            logger.warn("[DomeAuthService] dome.auth.username 或 dome.auth.password 未配置，无法自动获取 token");
            return;
        }
        
        logger.info("[DomeAuthService] 认证服务地址: {}", authBaseUrl);
        
        // 启动时自动获取 token
        try {
            login();
        } catch (Exception e) {
            logger.error("[DomeAuthService] 启动时获取 token 失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 从服务URL中提取基础URL（去掉路径部分）
     * 例如：http://192.168.67.132:1888/api/dome-datasource -> http://192.168.67.132:1888
     */
    private String extractBaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        try {
            // 移除末尾的斜杠
            url = url.replaceAll("/+$", "");
            
            // 查找 /api/ 的位置
            int apiIndex = url.indexOf("/api/");
            if (apiIndex > 0) {
                return url.substring(0, apiIndex);
            }
            
            // 如果没有找到 /api/，返回原URL（可能是直接的基础URL）
            return url;
        } catch (Exception e) {
            logger.warn("[DomeAuthService] 提取基础URL失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取有效的 token（自动刷新）
     */
    public String getToken() {
        // 如果配置了静态 token，直接返回
        if (staticToken != null && !staticToken.isEmpty()) {
            return staticToken;
        }
        
        // 如果禁用了自动认证，返回空
        if (!authEnabled) {
            return "";
        }
        
        lock.lock();
        try {
            // 检查 token 是否即将过期（提前60秒刷新）
            if (accessToken == null || accessToken.isEmpty() || 
                System.currentTimeMillis() / 1000 + 60 >= tokenExpiresAt) {
                // Token 不存在或即将过期，尝试刷新
                if (refreshToken != null && !refreshToken.isEmpty()) {
                    try {
                        refreshAccessToken();
                    } catch (Exception e) {
                        logger.warn("[DomeAuthService] 刷新 token 失败，尝试重新登录: {}", e.getMessage());
                        try {
                            login();
                        } catch (Exception loginEx) {
                            logger.error("[DomeAuthService] 重新登录失败: {}", loginEx.getMessage());
                        }
                    }
                } else {
                    // 没有 refresh token，尝试登录
                    try {
                        login();
                    } catch (Exception e) {
                        logger.error("[DomeAuthService] 登录失败: {}", e.getMessage());
                    }
                }
            }
            
            // 返回格式化的 token（token_type + " " + access_token）
            if (accessToken != null && !accessToken.isEmpty()) {
                return tokenType + " " + accessToken;
            }
            
            return "";
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 登录获取 token
     */
    private void login() throws Exception {
        if (authBaseUrl == null || authBaseUrl.isEmpty()) {
            throw new IllegalStateException("dome.auth.base-url 未配置");
        }
        
        String url = authBaseUrl.replaceAll("/+$", "") + "/api/dome-auth/oauth/token";
        logger.info("[DomeAuthService] 开始登录，URL: {}", url);
        
        // 准备请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        // Basic 认证
        String credentials = clientId + ":" + clientSecret;
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + basicAuth);
        
        // 租户ID（如果配置了）
        if (tenantId != null && !tenantId.isEmpty()) {
            headers.set("Tenant-Id", tenantId);
        }
        
        // 准备请求体
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("username", username);
        
        // SM2 加密密码（先MD5，再SM2加密）
        String passwordMd5 = md5(password);
        String passwordEncrypted = sm2Encrypt(passwordMd5);
        body.add("password", passwordEncrypted);
        body.add("scope", "all");
        
        if (tenantId != null && !tenantId.isEmpty()) {
            body.add("tenantId", tenantId);
        }
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, request, Map.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("登录失败，HTTP状态码: " + response.getStatusCode());
            }
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("登录响应为空");
            }
            
            // 解析响应
            this.accessToken = (String) responseBody.get("access_token");
            this.refreshToken = (String) responseBody.get("refresh_token");
            this.tokenType = (String) responseBody.getOrDefault("token_type", "bearer");
            
            // 计算过期时间（expires_in 是秒数）
            Object expiresInObj = responseBody.get("expires_in");
            if (expiresInObj != null) {
                long expiresIn = 0;
                if (expiresInObj instanceof Number) {
                    expiresIn = ((Number) expiresInObj).longValue();
                } else {
                    try {
                        expiresIn = Long.parseLong(expiresInObj.toString());
                    } catch (NumberFormatException e) {
                        logger.warn("[DomeAuthService] 无法解析 expires_in，使用默认值 3600 秒");
                        expiresIn = 3600;
                    }
                }
                this.tokenExpiresAt = System.currentTimeMillis() / 1000 + expiresIn;
            } else {
                // 如果没有 expires_in，假设 1 小时后过期
                this.tokenExpiresAt = System.currentTimeMillis() / 1000 + 3600;
            }
            
            logger.info("[DomeAuthService] 登录成功，token 将在 {} 秒后过期", 
                this.tokenExpiresAt - System.currentTimeMillis() / 1000);
            
        } catch (Exception e) {
            logger.error("[DomeAuthService] 登录失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 刷新 access token
     */
    private void refreshAccessToken() throws Exception {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IllegalStateException("没有可用的 refresh_token");
        }
        
        if (authBaseUrl == null || authBaseUrl.isEmpty()) {
            throw new IllegalStateException("dome.auth.base-url 未配置");
        }
        
        String url = authBaseUrl.replaceAll("/+$", "") + "/api/dome-auth/oauth/token";
        logger.info("[DomeAuthService] 开始刷新 token，URL: {}", url);
        
        // 准备请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        // Basic 认证
        String credentials = clientId + ":" + clientSecret;
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + basicAuth);
        
        // 租户ID（如果配置了）
        if (tenantId != null && !tenantId.isEmpty()) {
            headers.set("Tenant-Id", tenantId);
        }
        
        // 准备请求体
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        body.add("scope", "all");
        
        if (tenantId != null && !tenantId.isEmpty()) {
            body.add("tenantId", tenantId);
        }
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, request, Map.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("刷新 token 失败，HTTP状态码: " + response.getStatusCode());
            }
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("刷新 token 响应为空");
            }
            
            // 更新 token
            this.accessToken = (String) responseBody.get("access_token");
            String newRefreshToken = (String) responseBody.get("refresh_token");
            if (newRefreshToken != null && !newRefreshToken.isEmpty()) {
                this.refreshToken = newRefreshToken;
            }
            this.tokenType = (String) responseBody.getOrDefault("token_type", "bearer");
            
            // 计算过期时间
            Object expiresInObj = responseBody.get("expires_in");
            if (expiresInObj != null) {
                long expiresIn = 0;
                if (expiresInObj instanceof Number) {
                    expiresIn = ((Number) expiresInObj).longValue();
                } else {
                    try {
                        expiresIn = Long.parseLong(expiresInObj.toString());
                    } catch (NumberFormatException e) {
                        logger.warn("[DomeAuthService] 无法解析 expires_in，使用默认值 3600 秒");
                        expiresIn = 3600;
                    }
                }
                this.tokenExpiresAt = System.currentTimeMillis() / 1000 + expiresIn;
            } else {
                this.tokenExpiresAt = System.currentTimeMillis() / 1000 + 3600;
            }
            
            logger.info("[DomeAuthService] Token 刷新成功，token 将在 {} 秒后过期", 
                this.tokenExpiresAt - System.currentTimeMillis() / 1000);
            
        } catch (Exception e) {
            logger.error("[DomeAuthService] 刷新 token 失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * MD5 加密
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 加密失败", e);
        }
    }
    
    /**
     * SM2 加密
     * @param plainText 明文（通常是MD5后的字符串）
     * @return 十六进制编码的加密结果（与前端sm-crypto库保持一致）
     */
    private String sm2Encrypt(String plainText) {
        try {
            // 获取SM2曲线参数
            X9ECParameters sm2ECParameters = GMNamedCurves.getByName("sm2p256v1");
            ECDomainParameters domainParameters = new ECDomainParameters(
                sm2ECParameters.getCurve(),
                sm2ECParameters.getG(),
                sm2ECParameters.getN(),
                sm2ECParameters.getH()
            );
            
            // 解析公钥（未压缩格式，以04开头）
            byte[] publicKeyBytes = Hex.decode(SM2_PUBLIC_KEY);
            ECPoint publicKeyPoint = sm2ECParameters.getCurve().decodePoint(publicKeyBytes);
            ECPublicKeyParameters publicKeyParameters = new ECPublicKeyParameters(
                publicKeyPoint,
                domainParameters
            );
            
            // 创建SM2加密引擎
            SM2Engine sm2Engine = new SM2Engine();
            sm2Engine.init(true, new ParametersWithRandom(publicKeyParameters));
            
            // 加密
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBytes = sm2Engine.processBlock(plainTextBytes, 0, plainTextBytes.length);
            
            // 返回十六进制编码的结果（与前端sm-crypto库保持一致）
            return Hex.toHexString(encryptedBytes);
        } catch (Exception e) {
            logger.error("[DomeAuthService] SM2 加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("SM2 加密失败", e);
        }
    }
    
    /**
     * 定期检查并刷新 token（每30分钟执行一次）
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // 30分钟
    public void scheduledRefresh() {
        if (!authEnabled || staticToken != null && !staticToken.isEmpty()) {
            return;
        }
        
        lock.lock();
        try {
            // 如果 token 即将过期（提前5分钟刷新）
            if (accessToken != null && !accessToken.isEmpty() && 
                System.currentTimeMillis() / 1000 + 300 >= tokenExpiresAt) {
                if (refreshToken != null && !refreshToken.isEmpty()) {
                    try {
                        logger.info("[DomeAuthService] 定时任务：开始刷新 token");
                        refreshAccessToken();
                    } catch (Exception e) {
                        logger.warn("[DomeAuthService] 定时刷新 token 失败: {}", e.getMessage());
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }
}

