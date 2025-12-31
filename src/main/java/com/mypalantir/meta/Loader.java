package com.mypalantir.meta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Loader {
    private final Parser parser;
    private final String systemSchemaPath;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private OntologySchema schema;

    public Loader(String filePath) {
        this(filePath, null);
    }

    public Loader(String filePath, String systemSchemaPath) {
        this.parser = new Parser(filePath);
        this.systemSchemaPath = systemSchemaPath;
    }

    public void load() throws IOException, Validator.ValidationException {
        lock.writeLock().lock();
        try {
            OntologySchema parsedSchema = parser.parse();
            
            // 如果指定了系统 schema 路径，加载并合并
            if (systemSchemaPath != null && !systemSchemaPath.isEmpty()) {
                Parser systemParser = new Parser(systemSchemaPath);
                OntologySchema systemSchema = systemParser.parse();
                
                // 合并两个 schema
                parsedSchema = mergeSchemas(systemSchema, parsedSchema);
            }
            
            Validator schemaValidator = new Validator(parsedSchema);
            schemaValidator.validate();
            this.schema = parsedSchema;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 合并系统 schema 和用户 schema
     * 系统 schema 的对象和关系会优先，用户 schema 中的同名对象和关系会被忽略
     */
    private OntologySchema mergeSchemas(OntologySchema systemSchema, OntologySchema userSchema) {
        OntologySchema merged = new OntologySchema();
        
        // 使用用户 schema 的版本和命名空间（如果用户 schema 有的话）
        merged.setVersion(userSchema.getVersion() != null ? userSchema.getVersion() : systemSchema.getVersion());
        merged.setNamespace(userSchema.getNamespace() != null ? userSchema.getNamespace() : systemSchema.getNamespace());
        
        // 合并对象类型：系统 schema 优先，然后添加用户 schema 中不存在的对象类型
        List<ObjectType> mergedObjectTypes = new ArrayList<>();
        if (systemSchema.getObjectTypes() != null) {
            mergedObjectTypes.addAll(systemSchema.getObjectTypes());
        }
        if (userSchema.getObjectTypes() != null) {
            for (ObjectType userOt : userSchema.getObjectTypes()) {
                // 检查是否已存在同名对象类型
                boolean exists = mergedObjectTypes.stream()
                    .anyMatch(ot -> ot.getName().equals(userOt.getName()));
                if (!exists) {
                    mergedObjectTypes.add(userOt);
                }
            }
        }
        merged.setObjectTypes(mergedObjectTypes);
        
        // 合并关系类型：系统 schema 优先，然后添加用户 schema 中不存在的关系类型
        List<LinkType> mergedLinkTypes = new ArrayList<>();
        if (systemSchema.getLinkTypes() != null) {
            mergedLinkTypes.addAll(systemSchema.getLinkTypes());
        }
        if (userSchema.getLinkTypes() != null) {
            for (LinkType userLt : userSchema.getLinkTypes()) {
                // 检查是否已存在同名关系类型
                boolean exists = mergedLinkTypes.stream()
                    .anyMatch(lt -> lt.getName().equals(userLt.getName()));
                if (!exists) {
                    mergedLinkTypes.add(userLt);
                }
            }
        }
        merged.setLinkTypes(mergedLinkTypes);
        
        return merged;
    }

    public OntologySchema getSchema() {
        lock.readLock().lock();
        try {
            return schema;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void reload() throws IOException, Validator.ValidationException {
        load();
    }

    public ObjectType getObjectType(String name) throws NotFoundException {
        OntologySchema currentSchema = getSchema();
        if (currentSchema == null || currentSchema.getObjectTypes() == null) {
            throw new NotFoundException("schema not loaded");
        }

        for (ObjectType ot : currentSchema.getObjectTypes()) {
            if (name.equals(ot.getName())) {
                return ot;
            }
        }

        throw new NotFoundException("object type '" + name + "' not found");
    }

    public List<ObjectType> listObjectTypes() {
        OntologySchema currentSchema = getSchema();
        if (currentSchema == null) {
            return List.of();
        }
        return currentSchema.getObjectTypes() != null ? List.copyOf(currentSchema.getObjectTypes()) : List.of();
    }

    public LinkType getLinkType(String name) throws NotFoundException {
        OntologySchema currentSchema = getSchema();
        if (currentSchema == null || currentSchema.getLinkTypes() == null) {
            throw new NotFoundException("schema not loaded");
        }

        for (LinkType lt : currentSchema.getLinkTypes()) {
            if (name.equals(lt.getName())) {
                return lt;
            }
        }

        throw new NotFoundException("link type '" + name + "' not found");
    }

    public List<LinkType> listLinkTypes() {
        OntologySchema currentSchema = getSchema();
        if (currentSchema == null) {
            return List.of();
        }
        return currentSchema.getLinkTypes() != null ? List.copyOf(currentSchema.getLinkTypes()) : List.of();
    }

    public List<LinkType> getOutgoingLinks(String objectTypeName) {
        OntologySchema currentSchema = getSchema();
        if (currentSchema == null || currentSchema.getLinkTypes() == null) {
            return List.of();
        }

        return currentSchema.getLinkTypes().stream()
            .filter(lt -> objectTypeName.equals(lt.getSourceType()))
            .toList();
    }

    public List<LinkType> getIncomingLinks(String objectTypeName) {
        OntologySchema currentSchema = getSchema();
        if (currentSchema == null || currentSchema.getLinkTypes() == null) {
            return List.of();
        }

        return currentSchema.getLinkTypes().stream()
            .filter(lt -> objectTypeName.equals(lt.getTargetType()))
            .toList();
    }

    public List<DataSourceConfig> listDataSources() {
        OntologySchema currentSchema = getSchema();
        if (currentSchema == null) {
            return List.of();
        }
        return currentSchema.getDataSources() != null ? List.copyOf(currentSchema.getDataSources()) : List.of();
    }

    public DataSourceConfig getDataSourceById(String id) throws NotFoundException {
        OntologySchema currentSchema = getSchema();
        if (currentSchema == null || currentSchema.getDataSources() == null) {
            throw new NotFoundException("schema not loaded");
        }

        DataSourceConfig dataSource = currentSchema.getDataSourceById(id);
        if (dataSource == null) {
            throw new NotFoundException("data source '" + id + "' not found");
        }
        return dataSource;
    }

    public static class NotFoundException extends Exception {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
