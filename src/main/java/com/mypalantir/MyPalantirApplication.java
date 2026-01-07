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

import java.io.IOException;

@SpringBootApplication
@EnableConfigurationProperties(Config.class)
@Import(EnvConfig.class)
public class MyPalantirApplication {
    private static final Logger logger = LoggerFactory.getLogger(MyPalantirApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MyPalantirApplication.class);
        // 注册 .env 文件加载器
        app.addListeners(new EnvConfig());
        app.run(args);
    }

    @Bean
    public Loader schemaLoader(Config config) {
        String filePath = config.getSchemaFilePath();
        String systemSchemaPath = config.getSystemSchemaFilePath();
        Loader loader = new Loader(filePath, systemSchemaPath);
        try {
            loader.load();
            logger.info("Schema loaded successfully: {} object types, {} link types", 
                loader.getSchema() != null && loader.getSchema().getObjectTypes() != null ? loader.getSchema().getObjectTypes().size() : 0,
                loader.getSchema() != null && loader.getSchema().getLinkTypes() != null ? loader.getSchema().getLinkTypes().size() : 0);
            if (systemSchemaPath != null && !systemSchemaPath.isEmpty()) {
                logger.info("System schema merged from: {}", systemSchemaPath);
            }
        } catch (IOException | Validator.ValidationException e) {
            logger.error("Failed to load schema from: {}", filePath, e);
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
    public QueryService queryService(@Lazy Loader loader, IInstanceStorage instanceStorage,
                                    com.mypalantir.service.MappingService mappingService,
                                    com.mypalantir.service.DatabaseMetadataService databaseMetadataService) {
        return new QueryService(loader, instanceStorage, mappingService, databaseMetadataService);
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

