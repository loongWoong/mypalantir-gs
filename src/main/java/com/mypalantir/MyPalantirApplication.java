package com.mypalantir;

import com.mypalantir.config.Config;
import com.mypalantir.config.EnvConfig;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.Validator;
import com.mypalantir.service.DataValidator;
import com.mypalantir.service.InstanceService;
import com.mypalantir.service.LinkService;
import com.mypalantir.service.SchemaService;
import com.mypalantir.repository.InstanceStorage;
import com.mypalantir.repository.LinkStorage;
import com.mypalantir.repository.PathManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.io.IOException;

@SpringBootApplication
@EnableConfigurationProperties(Config.class)
@Import(EnvConfig.class)
public class MyPalantirApplication {
    private static final Logger logger = LoggerFactory.getLogger(MyPalantirApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MyPalantirApplication.class, args);
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
    public PathManager pathManager(Config config, Loader loader) {
        String namespace = loader.getSchema() != null && loader.getSchema().getNamespace() != null
            ? loader.getSchema().getNamespace()
            : "default";
        return new PathManager(config.getDataRootPath(), namespace);
    }

    @Bean
    public InstanceStorage instanceStorage(PathManager pathManager) {
        return new InstanceStorage(pathManager);
    }

    @Bean
    public LinkStorage linkStorage(PathManager pathManager) {
        return new LinkStorage(pathManager);
    }

    @Bean
    public DataValidator dataValidator(Loader loader) {
        return new DataValidator(loader);
    }

    @Bean
    public SchemaService schemaService(Loader loader) {
        return new SchemaService(loader);
    }

    @Bean
    public InstanceService instanceService(InstanceStorage instanceStorage, Loader loader, DataValidator validator) {
        return new InstanceService(instanceStorage, loader, validator);
    }

    @Bean
    public LinkService linkService(LinkStorage linkStorage, InstanceStorage instanceStorage, Loader loader, DataValidator validator) {
        return new LinkService(linkStorage, instanceStorage, loader, validator);
    }
}

