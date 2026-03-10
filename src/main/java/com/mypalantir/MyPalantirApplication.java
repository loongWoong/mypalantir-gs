package com.mypalantir;

import com.mypalantir.config.Config;
import com.mypalantir.config.EnvConfig;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.Validator;
import com.mypalantir.service.AtomicMetricService;
import com.mypalantir.service.DataValidator;
import com.mypalantir.service.InstanceService;
import com.mypalantir.service.MetricService;
import com.mypalantir.service.QueryService;
import com.mypalantir.service.SchemaService;
import com.mypalantir.service.LinkService;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.repository.ILinkStorage;
import com.mypalantir.repository.InstanceStorage;
import com.mypalantir.repository.LinkStorage;
import com.mypalantir.repository.PathManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@SpringBootApplication
@EnableConfigurationProperties(Config.class)
@Import(EnvConfig.class)
@EnableScheduling
public class MyPalantirApplication {
    private static final Logger logger = LoggerFactory.getLogger(MyPalantirApplication.class);

    public static void main(String[] args) {
        loadEnvFile();
        SpringApplication.run(MyPalantirApplication.class, args);
    }

    /**
     * 从项目根目录加载 .env 到系统属性，供 application.properties 中的 ${LLM_API_KEY} 等使用。
     * Agent 与自然语言查询统一使用 .env 中的大模型 API 配置。
     * 已存在的环境变量优先，不会被 .env 覆盖。
     */
    private static void loadEnvFile() {
        Path[] candidates = {
            Path.of(".env"),
            Path.of("..", ".env")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try {
                    int count = 0;
                    Pattern assign = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        var m = assign.matcher(line);
                        if (m.matches()) {
                            String key = m.group(1).trim();
                            String value = m.group(2).trim();
                            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                                value = value.substring(1, value.length() - 1);
                            else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)
                                value = value.substring(1, value.length() - 1);
                            if (System.getenv(key) == null)
                                System.setProperty(key, value);
                            count++;
                        }
                    }
                    logger.info("Loaded {} variables from .env: {}", count, p.toAbsolutePath().normalize());
                } catch (IOException e) {
                    logger.warn("Failed to read .env file: {}", e.getMessage());
                }
                return;
            }
        }
    }

    @Bean
    public Loader schemaLoader(Config config) {
        String filePath = config.getSchemaFilePath();
        String systemSchemaPath = config.getSystemSchemaFilePath();
        String modelId = config.getOntologyModel();
        
        // 支持两种模式：如果配置了 systemSchemaPath，使用双文件模式；否则使用单文件模式
        Loader loader = (systemSchemaPath != null && !systemSchemaPath.isEmpty())
            ? new Loader(filePath, systemSchemaPath)
            : new Loader(filePath);
        
        try {
            loader.load();
            logger.info("Ontology model '{}' loaded successfully from: {} ({} object types, {} link types)", 
                modelId != null ? modelId : "default",
                filePath,
                loader.getSchema() != null && loader.getSchema().getObjectTypes() != null ? loader.getSchema().getObjectTypes().size() : 0,
                loader.getSchema() != null && loader.getSchema().getLinkTypes() != null ? loader.getSchema().getLinkTypes().size() : 0);
            if (systemSchemaPath != null && !systemSchemaPath.isEmpty()) {
                logger.info("System schema merged from: {}", systemSchemaPath);
            }
        } catch (IOException | Validator.ValidationException e) {
            logger.error("Failed to load ontology model '{}' from: {}", modelId != null ? modelId : "default", filePath, e);
            logger.error("Schema validation error details: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.error("Caused by: ", e.getCause());
            }
            throw new RuntimeException("Failed to load schema: " + e.getMessage(), e);
        }
        return loader;
    }

    @Bean
    public PathManager pathManager(Config config, @Lazy Loader loader) {
        String namespace = loader.getSchema() != null && loader.getSchema().getNamespace() != null
            ? loader.getSchema().getNamespace()
            : "default";
        return new PathManager(config.getDataRootPath(), namespace);
    }

    @Bean
    public InstanceStorage fileInstanceStorage(PathManager pathManager) {
        return new InstanceStorage(pathManager);
    }

    @Bean
    public LinkStorage fileLinkStorage(PathManager pathManager) {
        return new LinkStorage(pathManager);
    }

    @Bean
    public DataValidator dataValidator(@Lazy Loader loader) {
        return new DataValidator(loader);
    }

    @Bean
    public SchemaService schemaService(@Lazy Loader loader) {
        return new SchemaService(loader);
    }

    @Bean
    public QueryService queryService(@Lazy Loader loader, @Lazy IInstanceStorage instanceStorage,
                                    com.mypalantir.service.MappingService mappingService,
                                    com.mypalantir.service.DatabaseMetadataService databaseMetadataService,
                                    com.mypalantir.query.ExecutionRouter executionRouter) {
        return new QueryService(loader, instanceStorage, mappingService, databaseMetadataService, executionRouter);
    }

    @Bean
    public InstanceService instanceService(IInstanceStorage instanceStorage, @Lazy Loader loader, DataValidator validator, QueryService queryService) {
        return new InstanceService(instanceStorage, loader, validator, queryService);
    }

    @Bean
    public LinkService linkService(ILinkStorage linkStorage, IInstanceStorage instanceStorage, @Lazy Loader loader, DataValidator validator) {
        return new LinkService(linkStorage, instanceStorage, loader, validator);
    }

    @Bean
    public AtomicMetricService atomicMetricService(IInstanceStorage instanceStorage, @Lazy Loader loader) {
        return new AtomicMetricService(instanceStorage, loader);
    }

    /**
     * 配置服务之间的依赖关系（在应用启动完成后执行，避免循环依赖）
     */
    @Bean
    public ApplicationRunner configureServiceDependencies(
            LinkService linkService,
            AtomicMetricService atomicMetricService,
            MetricService metricService,
            DataValidator dataValidator) {
        return args -> {
            // 配置 AtomicMetricService 的依赖
            atomicMetricService.setLinkService(linkService);
            atomicMetricService.setDataValidator(dataValidator);

            // 配置 MetricService 的依赖
            metricService.setLinkService(linkService);
            
            logger.info("Service dependencies configured successfully");
        };
    }
}

