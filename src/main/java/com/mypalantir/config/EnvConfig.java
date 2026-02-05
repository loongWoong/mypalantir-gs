package com.mypalantir.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class EnvConfig {
    public static final String BEAN_NAME = "envConfig";
    private static final Logger logger = LoggerFactory.getLogger(EnvConfig.class);
    private static Dotenv dotenv;

    /**
     * 预处理 .env 文件：移除 BOM 字符并过滤注释行
     * @param envFile 原始 .env 文件
     * @return 处理后的临时文件，如果处理失败则返回 null
     */
    private File preprocessEnvFile(File envFile) {
        try {
            // 读取文件内容
            byte[] fileBytes = Files.readAllBytes(envFile.toPath());
            
            // 移除 BOM (UTF-8 BOM 是 EF BB BF)
            if (fileBytes.length >= 3 && 
                fileBytes[0] == (byte)0xEF && 
                fileBytes[1] == (byte)0xBB && 
                fileBytes[2] == (byte)0xBF) {
                // 移除 BOM
                byte[] withoutBom = new byte[fileBytes.length - 3];
                System.arraycopy(fileBytes, 3, withoutBom, 0, withoutBom.length);
                fileBytes = withoutBom;
            }
            
            // 转换为字符串
            String content = new String(fileBytes, StandardCharsets.UTF_8);
            
            // 按行处理，过滤注释行和空行
            List<String> processedLines = new ArrayList<>();
            String[] lines = content.split("\\r?\\n");
            
            for (String line : lines) {
                String trimmed = line.trim();
                // 跳过空行
                if (trimmed.isEmpty()) {
                    continue;
                }
                // 跳过以 # 开头的注释行
                if (trimmed.startsWith("#")) {
                    continue;
                }
                // 保留其他行（包括行内注释，让 dotenv 库处理）
                processedLines.add(line);
            }
            
            // 创建临时文件
            Path tempFile = Files.createTempFile(".env", ".tmp");
            Files.write(tempFile, processedLines, StandardCharsets.UTF_8);
            
            logger.debug("Preprocessed .env file: removed BOM and {} comment lines", 
                lines.length - processedLines.size());
            
            return tempFile.toFile();
        } catch (Exception e) {
            logger.error("Failed to preprocess .env file", e);
            return null;
        }
    }

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
                
                // 预处理 .env 文件（移除 BOM 和注释行）
                File processedFile = preprocessEnvFile(envFile);
                File tempFileToDelete = null;
                
                try {
                    if (processedFile != null) {
                        // 将处理后的文件复制到 .env 文件所在目录，使用临时文件名
                        Path tempPath = Paths.get(envDir, ".env.processed");
                        Files.copy(processedFile.toPath(), tempPath, 
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        tempFileToDelete = tempPath.toFile();
                        
                        // 使用处理后的文件加载
                        dotenv = Dotenv.configure()
                            .directory(envDir)
                            .filename(".env.processed")
                            .ignoreIfMissing()
                            .load();
                    } else {
                        // 如果预处理失败，尝试直接加载（可能会失败，但至少尝试一下）
                        logger.warn("Preprocessing failed, trying to load original .env file directly");
                        dotenv = Dotenv.configure()
                            .directory(envDir)
                            .filename(".env")
                            .ignoreIfMissing()
                            .load();
                    }
                    
                    logger.info("Loaded .env file successfully from: {}", envFile.getAbsolutePath());
                    
                    // 将.env文件中的变量设置到系统属性中（如果不存在）
                    dotenv.entries().forEach(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (System.getProperty(key) == null) {
                            System.setProperty(key, value);
                        }
                    });
                } finally {
                    // 清理临时文件
                    if (processedFile != null && processedFile.exists()) {
                        try {
                            Files.deleteIfExists(processedFile.toPath());
                        } catch (IOException e) {
                            logger.warn("Failed to delete temporary processed file: {}", processedFile.getAbsolutePath(), e);
                        }
                    }
                    if (tempFileToDelete != null && tempFileToDelete.exists()) {
                        try {
                            Files.deleteIfExists(tempFileToDelete.toPath());
                        } catch (IOException e) {
                            logger.warn("Failed to delete temporary .env.processed file: {}", tempFileToDelete.getAbsolutePath(), e);
                        }
                    }
                }
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
