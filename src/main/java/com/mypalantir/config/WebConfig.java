package com.mypalantir.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);
    
    @Autowired
    private Config config;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String webPath = config.getWebStaticPath();
        
        // 如果配置为 classpath，使用 Spring Boot 默认的静态资源处理
        if (webPath.startsWith("classpath:")) {
            logger.info("Using classpath static resources");
            registry.addResourceHandler("/**")
                .addResourceLocations(webPath + "/")
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 如果是 API 请求，返回 null
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        
                        // 尝试获取请求的资源
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        
                        // SPA 路由回退：返回 index.html
                        Resource indexResource = location.createRelative("index.html");
                        if (indexResource.exists() && indexResource.isReadable()) {
                            return indexResource;
                        }
                        
                        return null;
                    }
                });
        } else {
            // 使用外部文件路径（开发模式）
            Path webDir = Paths.get(webPath).toAbsolutePath();
            
            if (Files.exists(webDir) && Files.isDirectory(webDir)) {
                String filePath = "file:" + webDir.toString().replace("\\", "/") + "/";
                
                logger.info("Configuring static resources from external path: {}", filePath);
                
                // 配置静态资源处理器
                registry.addResourceHandler("/assets/**")
                    .addResourceLocations(filePath + "assets/")
                    .setCachePeriod(3600);
                
                // 配置 favicon
                Path faviconPath = webDir.resolve("favicon.ico");
                if (Files.exists(faviconPath)) {
                    registry.addResourceHandler("/favicon.ico")
                        .addResourceLocations(filePath);
                }
                
                // SPA 路由回退
                registry.addResourceHandler("/**")
                    .addResourceLocations(filePath)
                    .setCachePeriod(0)
                    .resourceChain(true)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String resourcePath, Resource location) throws IOException {
                            if (resourcePath.startsWith("api/")) {
                                return null;
                            }
                            
                            Resource requestedResource = location.createRelative(resourcePath);
                            if (requestedResource.exists() && requestedResource.isReadable()) {
                                return requestedResource;
                            }
                            
                            Resource indexResource = location.createRelative("index.html");
                            if (indexResource.exists() && indexResource.isReadable()) {
                                return indexResource;
                            }
                            
                            return null;
                        }
                    });
            } else {
                logger.warn("Web static path not found: {}. Web UI will not be served.", webDir);
                logger.warn("API is available at /api/v1");
            }
        }
    }
}
