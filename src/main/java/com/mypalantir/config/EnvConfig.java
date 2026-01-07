package com.mypalantir.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 环境变量配置类
 * 从项目根目录的 .env 文件读取配置并添加到 Spring Environment
 * 在应用启动早期执行，确保 @Value 注解可以读取到这些值
 */
public class EnvConfig implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(EnvConfig.class);
    
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        try {
            // 获取项目根目录
            String projectRoot = System.getProperty("user.dir");
            File envFile = Paths.get(projectRoot, ".env").toFile();
            
            if (!envFile.exists()) {
                logger.debug(".env file not found at: {}", envFile.getAbsolutePath());
                return;
            }
            
            logger.info("Loading .env file from: {}", envFile.getAbsolutePath());
            
            // 读取 .env 文件
            Properties props = new Properties();
            try (FileReader reader = new FileReader(envFile)) {
                props.load(reader);
            }
            
            // 将属性添加到 Spring Environment
            ConfigurableEnvironment environment = event.getEnvironment();
            PropertiesPropertySource propertySource = new PropertiesPropertySource("env", props);
            environment.getPropertySources().addFirst(propertySource);
            
            // 同时设置系统属性（用于 @Value 注解的 ${ENV_VAR} 格式）
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                if (value != null && !value.trim().isEmpty()) {
                    String existingValue = System.getProperty(key);
                    if (existingValue == null || existingValue.isEmpty()) {
                        System.setProperty(key, value);
                        logger.debug("Set system property: {} = {}", key, maskSensitiveValue(key, value));
                    }
                }
            }
            
            logger.info("Successfully loaded .env file with {} properties", props.size());
            
        } catch (IOException e) {
            logger.warn("Failed to load .env file: {}", e.getMessage());
        }
    }
    
    /**
     * 掩码敏感值（用于日志）
     */
    private String maskSensitiveValue(String key, String value) {
        if (key != null && key.toLowerCase().contains("key") && value != null && value.length() > 8) {
            return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
        }
        return value;
    }
}

