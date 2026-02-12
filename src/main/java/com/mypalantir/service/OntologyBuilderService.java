package com.mypalantir.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.OntologySchema;
import com.mypalantir.meta.Property;
import com.mypalantir.meta.Validator;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.mypalantir.meta.Parser;

/**
 * 本体可视化建模服务：负责 YML 生成和校验。
 */
@Service
public class OntologyBuilderService {
    private final ObjectMapper yamlMapper;

    public OntologyBuilderService() {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false);
        yamlFactory.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);
        yamlFactory.configure(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR, true);
        this.yamlMapper = new ObjectMapper(yamlFactory);
    }

    public ValidationResult validateAndGenerate(OntologySchema schema) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 第一层：结构校验（使用Validator）
        try {
            Validator validator = new Validator(schema);
            validator.validate();
        } catch (Validator.ValidationException e) {
            errors.add(e.getMessage());
        }

        // 第二层：语义校验（业务规则）
        errors.addAll(validateBusinessRules(schema));

        // 第三层：模型完整性校验（返回错误和警告）
        ModelCompletenessResult completenessResult = validateModelCompleteness(schema);
        errors.addAll(completenessResult.getErrors());
        warnings.addAll(completenessResult.getWarnings());

        // 生成YML
        String yaml = "";
        try {
            yaml = generateFormattedYaml(schema);
        } catch (JsonProcessingException e) {
            errors.add("YAML generation failed: " + e.getMessage());
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings, yaml);
    }

    /**
     * 校验业务规则
     */
    private List<String> validateBusinessRules(OntologySchema schema) {
        List<String> errors = new ArrayList<>();

        if (schema.getLinkTypes() == null) {
            return errors;
        }

        // 构建对象类型名称映射
        Map<String, ObjectType> objectTypeMap = new HashMap<>();
        if (schema.getObjectTypes() != null) {
            for (ObjectType ot : schema.getObjectTypes()) {
                objectTypeMap.put(ot.getName(), ot);
            }
        }

        Set<String> relationUniqueCheck = new HashSet<>();
        for (LinkType linkType : schema.getLinkTypes()) {
            // 检查自环
            if (Objects.equals(linkType.getSourceType(), linkType.getTargetType())) {
                errors.add("link_type '" + linkType.getName() + "': self-loop is not allowed");
            }

            // 检查关系唯一性（同一对实体不能有多个同名关系）
            String uniqKey = String.join("::",
                safe(linkType.getName()),
                safe(linkType.getSourceType()),
                safe(linkType.getTargetType())
            );
            if (!relationUniqueCheck.add(uniqKey)) {
                errors.add("duplicate relation definition: " + uniqKey);
            }

            // 检查关系引用的实体是否存在
            if (!objectTypeMap.containsKey(linkType.getSourceType())) {
                errors.add("link_type '" + linkType.getName() + "': source_type '" + linkType.getSourceType() + "' does not exist");
            }
            if (!objectTypeMap.containsKey(linkType.getTargetType())) {
                errors.add("link_type '" + linkType.getName() + "': target_type '" + linkType.getTargetType() + "' does not exist");
            }
        }

        return errors;
    }

    /**
     * 校验模型完整性
     */
    private ModelCompletenessResult validateModelCompleteness(OntologySchema schema) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (schema.getObjectTypes() == null || schema.getObjectTypes().isEmpty()) {
            errors.add("模型必须至少包含一个对象类型");
            return new ModelCompletenessResult(errors, warnings);
        }

        // 检查每个实体是否有主键
        for (ObjectType ot : schema.getObjectTypes()) {
            if (ot.getProperties() == null || ot.getProperties().isEmpty()) {
                errors.add("object_type '" + ot.getName() + "': must have at least one property");
                continue;
            }

            boolean hasPrimaryKey = false;
            Set<String> propertyNames = new HashSet<>();
            for (Property prop : ot.getProperties()) {
                if (propertyNames.contains(prop.getName())) {
                    errors.add("object_type '" + ot.getName() + "': duplicate property name '" + prop.getName() + "'");
                }
                propertyNames.add(prop.getName());

                // 检查是否为主键：required为true，且constraints中包含unique:true
                boolean isUnique = false;
                if (prop.getConstraints() != null && prop.getConstraints().containsKey("unique")) {
                    Object uniqueValue = prop.getConstraints().get("unique");
                    isUnique = Boolean.TRUE.equals(uniqueValue) || "true".equals(String.valueOf(uniqueValue));
                }
                if (prop.isRequired() && isUnique) {
                    hasPrimaryKey = true;
                }
            }

            // 主键校验改为警告，不影响校验通过状态
            if (!hasPrimaryKey) {
                warnings.add("object_type '" + ot.getName() + "': should have at least one primary key (required and unique property)");
            }
        }

        // 检查孤立实体（没有关系的实体）
        if (schema.getLinkTypes() != null && !schema.getLinkTypes().isEmpty()) {
            Set<String> connectedEntityNames = new HashSet<>();
            for (LinkType lt : schema.getLinkTypes()) {
                connectedEntityNames.add(lt.getSourceType());
                connectedEntityNames.add(lt.getTargetType());
            }

            if (schema.getObjectTypes() != null) {
                for (ObjectType ot : schema.getObjectTypes()) {
                    if (!connectedEntityNames.contains(ot.getName()) && schema.getObjectTypes().size() > 1) {
                        // 只作为警告，不是错误
                        // warnings.add("object_type '" + ot.getName() + "': is isolated (no relations)");
                    }
                }
            }
        }

        return new ModelCompletenessResult(errors, warnings);
    }

    /**
     * 生成格式化的YAML
     */
    private String generateFormattedYaml(OntologySchema schema) throws JsonProcessingException {
        return yamlMapper.writeValueAsString(schema);
    }

    /**
     * 保存本体模型到ontology文件夹
     * @param schema 本体模型
     * @param filename 文件名（不含扩展名）
     * @return 保存的文件路径
     * @throws IOException 文件操作异常
     */
    public String saveToOntologyFolder(OntologySchema schema, String filename) throws IOException {
        // 先校验
        ValidationResult validation = validateAndGenerate(schema);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("模型校验失败，无法保存: " + String.join(", ", validation.getErrors()));
        }

        // 确保文件名以.yaml结尾
        if (!filename.endsWith(".yaml") && !filename.endsWith(".yml")) {
            filename = filename + ".yaml";
        }

        // 构建文件路径：./ontology/filename.yaml
        Path ontologyDir = Paths.get("ontology");
        if (!Files.exists(ontologyDir)) {
            Files.createDirectories(ontologyDir);
        }

        Path filePath = ontologyDir.resolve(filename);

        // 检查文件是否已存在
        if (Files.exists(filePath)) {
            throw new IOException("文件已存在: " + filename + "，请修改文件名后重试");
        }

        // 生成YAML内容
        String yamlContent = generateFormattedYaml(schema);

        // 写入文件
        Files.writeString(filePath, yamlContent);

        return filePath.toString();
    }

    /**
     * 列出ontology文件夹中的所有YAML文件
     * @return 文件名列表
     * @throws IOException 文件操作异常
     */
    public List<String> listOntologyFiles() throws IOException {
        Path ontologyDir = Paths.get("ontology");
        if (!Files.exists(ontologyDir)) {
            return new ArrayList<>();
        }

        try (Stream<Path> paths = Files.list(ontologyDir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
                })
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        }
    }

    /**
     * 从ontology文件夹加载指定的YAML文件
     * @param filename 文件名（可以包含或不包含扩展名）
     * @return 解析后的OntologySchema
     * @throws IOException 文件操作异常
     */
    public OntologySchema loadFromOntologyFolder(String filename) throws IOException {
        // 确保文件名以.yaml或.yml结尾
        if (!filename.endsWith(".yaml") && !filename.endsWith(".yml")) {
            filename = filename + ".yaml";
        }

        // 构建文件路径：./ontology/filename.yaml
        Path ontologyDir = Paths.get("ontology");
        Path filePath = ontologyDir.resolve(filename);

        if (!Files.exists(filePath)) {
            throw new IOException("文件不存在: " + filename);
        }

        // 使用Parser解析文件
        Parser parser = new Parser(filePath.toString());
        return parser.parse();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 模型完整性校验结果
     */
    private static class ModelCompletenessResult {
        private final List<String> errors;
        private final List<String> warnings;

        public ModelCompletenessResult(List<String> errors, List<String> warnings) {
            this.errors = errors;
            this.warnings = warnings;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        private String yaml;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings, String yaml) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
            this.yaml = yaml;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }

        public String getYaml() {
            return yaml;
        }

        public void setYaml(String yaml) {
            this.yaml = yaml;
        }
    }
}
