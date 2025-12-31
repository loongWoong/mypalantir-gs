package com.mypalantir.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Paths;

@Configuration
public class EnvConfig {
    public static final String BEAN_NAME = "envConfig";
    private static final Logger logger = LoggerFactory.getLogger(EnvConfig.class);
    private static Dotenv dotenv;

    @PostConstruct
    public void loadEnv() {
        try {
            // 尝试从多个位置查找.env文件
            File envFile = null;
            String[] possiblePaths = {
                ".env",  // 当前目录
                Paths.get(System.getProperty("user.dir"), ".env").toString(),  // 工作目录
                Paths.get(System.getProperty("user.dir"), "..", ".env").toString()  // 上级目录
            };
            
            for (String path : possiblePaths) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    envFile = file;
                    break;
                }
            }
            
            if (envFile != null && envFile.exists()) {
                String envDir = envFile.getParent();
                if (envDir == null || envDir.isEmpty()) {
                    // 如果getParent()返回null，说明文件在根目录，使用绝对路径的父目录
                    String absolutePath = envFile.getAbsolutePath();
                    int lastSeparator = Math.max(
                        absolutePath.lastIndexOf('/'),
                        absolutePath.lastIndexOf('\\')
                    );
                    if (lastSeparator > 0) {
                        envDir = absolutePath.substring(0, lastSeparator);
                    } else {
                        envDir = System.getProperty("user.dir");
                    }
                }
                
                // 确保目录路径是绝对路径
                File dirFile = new File(envDir);
                if (!dirFile.isAbsolute()) {
                    envDir = dirFile.getAbsolutePath();
                }
                
                logger.info("Loading .env file from directory: {}", envDir);
                
                dotenv = Dotenv.configure()
                    .directory(envDir)
                    .filename(".env")
                    .ignoreIfMissing()
                    .load();
                logger.info("Loaded .env file successfully from: {}", envFile.getAbsolutePath());
                
                // 将.env文件中的变量设置到系统属性中（如果不存在）
                dotenv.entries().forEach(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (System.getProperty(key) == null) {
                        System.setProperty(key, value);
                    }
                });
            } else {
                logger.warn(".env file not found. Using system environment variables or default values.");
                // 即使没有.env文件，也创建一个空的Dotenv实例，避免NPE
                dotenv = Dotenv.configure().ignoreIfMissing().load();
            }
        } catch (Exception e) {
            logger.error("Failed to load .env file", e);
            // 创建空的Dotenv实例，使用系统环境变量
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        }
    }

    public static String get(String key) {
        if (dotenv != null) {
            return dotenv.get(key);
        }
        return System.getenv(key);
    }

    public static String get(String key, String defaultValue) {
        if (dotenv != null) {
            String value = dotenv.get(key);
            return value != null ? value : defaultValue;
        }
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
}
