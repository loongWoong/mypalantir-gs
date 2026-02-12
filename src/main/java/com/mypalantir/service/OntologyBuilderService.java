package com.mypalantir.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.OntologySchema;
import com.mypalantir.meta.OntologyVersion;
import com.mypalantir.meta.Property;
import com.mypalantir.meta.Validator;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.mypalantir.meta.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本体可视化建模服务：负责 YML 生成和校验。
 */
@Service
public class OntologyBuilderService {
    private static final Logger logger = LoggerFactory.getLogger(OntologyBuilderService.class);
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
     * 保存本体模型到ontology文件夹（支持版本历史）
     * @param schema 本体模型
     * @param filename 文件名（不含扩展名）
     * @param workspaceId 工作空间ID（可选）
     * @param commitMessage 提交说明（可选）
     * @return 保存的文件路径
     * @throws IOException 文件操作异常
     */
    public String saveToOntologyFolder(OntologySchema schema, String filename, String workspaceId, String commitMessage) throws IOException {
        // 先校验
        ValidationResult validation = validateAndGenerate(schema);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("模型校验失败，无法保存: " + String.join(", ", validation.getErrors()));
        }

        // 确保文件名以.yaml结尾
        String baseFilename = filename;
        if (!filename.endsWith(".yaml") && !filename.endsWith(".yml")) {
            baseFilename = filename + ".yaml";
        }

        // 构建目录结构
        // 使用基础文件名（不含版本号）作为版本目录，确保所有版本保存在同一目录下
        Path ontologyDir = Paths.get("ontology");
        Path modelsDir = ontologyDir.resolve("models");
        String baseName = extractBaseFilename(filename);
        Path versionDir = modelsDir.resolve(baseName);
        
        if (!Files.exists(versionDir)) {
            Files.createDirectories(versionDir);
        }

        // 获取当前版本（如果存在）
        // 使用基础文件名查找版本历史，确保能找到所有相关版本
        // baseName 已在上面定义，直接使用
        OntologyVersion currentVersion = loadCurrentVersion(baseName);
        String previousVersion = currentVersion != null ? currentVersion.getVersion() : null;
        
        // 确定新版本号
        String newVersion = schema.getVersion();
        if (newVersion == null || newVersion.isEmpty()) {
            // 如果版本号为空，自动递增
            if (previousVersion != null) {
                // 自动递增补丁版本
                newVersion = VersionManager.generateNextVersion(previousVersion, VersionManager.VersionType.PATCH);
            } else {
                newVersion = "1.0.0";
            }
            schema.setVersion(newVersion);
        } else if (previousVersion != null && newVersion.equals(previousVersion)) {
            // 如果传入的版本号与最新历史版本相同，自动递增补丁版本
            newVersion = VersionManager.generateNextVersion(previousVersion, VersionManager.VersionType.PATCH);
            schema.setVersion(newVersion);
        }
        // 如果传入的版本号与历史版本不同，使用传入的版本号（前端已根据用户选择的递增类型生成）

        // 保存版本快照
        String versionFileName = "v" + newVersion.replace(".", "_") + ".yaml";
        Path versionFilePath = versionDir.resolve(versionFileName);
        String yamlContent = generateFormattedYaml(schema);
        Files.writeString(versionFilePath, yamlContent);
        
        // 计算变更摘要（简化版）
        List<String> changes = new ArrayList<>();
        if (previousVersion != null) {
            try {
                // 使用基础文件名查找上一个版本
                OntologySchema previousSchema = getVersion(baseName, previousVersion);
                VersionComparator comparator = new VersionComparator();
                VersionComparator.DiffResult diff = comparator.compare(previousSchema, schema);
                if (diff.hasChanges()) {
                    if (!diff.objectTypeDiffs.isEmpty()) {
                        changes.add("对象类型变更: " + diff.objectTypeDiffs.size() + " 项");
                    }
                    if (!diff.linkTypeDiffs.isEmpty()) {
                        changes.add("关系类型变更: " + diff.linkTypeDiffs.size() + " 项");
                    }
                    // 添加元数据变更，但过滤掉版本变更信息（因为我们会单独添加）
                    if (!diff.metadataChanges.isEmpty()) {
                        for (String metadataChange : diff.metadataChanges) {
                            // 跳过版本变更信息，避免重复
                            if (!metadataChange.startsWith("版本:")) {
                                changes.add(metadataChange);
                            }
                        }
                    }
                }
                // 添加版本变更信息（只添加一次）
                changes.add("版本: " + previousVersion + " → " + newVersion);
            } catch (Exception e) {
                logger.warn("无法对比版本差异: " + e.getMessage());
                // 如果无法对比，至少记录版本变更
                if (previousVersion != null) {
                    changes.add("版本: " + previousVersion + " → " + newVersion);
                }
            }
        } else {
            changes.add("初始版本");
        }

        // 创建或更新当前版本链接（保存到models目录）
        Path currentFilePath = modelsDir.resolve(baseFilename);
        Files.writeString(currentFilePath, yamlContent);

        // 保存版本元数据
        // 使用基础文件名作为 filename，确保所有版本保存在同一个元数据文件中
        OntologyVersion version = new OntologyVersion(newVersion, schema.getNamespace(), baseName, versionFilePath.toString());
        version.setPreviousVersion(previousVersion);
        version.setCommitMessage(commitMessage);
        version.setWorkspaceId(workspaceId);
        version.setChanges(changes);
        saveVersionMetadata(versionDir, version);

        // 如果文件已存在且不是首次保存，则更新
        Path oldFilePath = ontologyDir.resolve(baseFilename);
        if (Files.exists(oldFilePath) && previousVersion != null) {
            // 保留旧文件作为备份（可选）
            // Files.move(oldFilePath, ontologyDir.resolve(baseFilename + ".backup"), StandardCopyOption.REPLACE_EXISTING);
        }

        // 更新主文件（向后兼容）
        Files.writeString(oldFilePath, yamlContent);

        return currentFilePath.toString();
    }

    /**
     * 保存本体模型到ontology文件夹（向后兼容方法）
     */
    public String saveToOntologyFolder(OntologySchema schema, String filename) throws IOException {
        return saveToOntologyFolder(schema, filename, null, null);
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

    /**
     * 获取版本历史列表
     */
    public List<OntologyVersion> getVersionHistory(String filename) throws IOException {
        logger.debug("开始查询版本历史，文件名: {}", filename);
        
        // 规范化文件名（移除扩展名）
        String baseName = filename.replaceAll("\\.(yaml|yml)$", "");
        logger.debug("规范化后的文件名: {}", baseName);
        
        Path versionDir = getVersionDir(filename);
        logger.debug("版本目录路径: {}", versionDir.toAbsolutePath());
        
        if (!Files.exists(versionDir)) {
            logger.warn("版本目录不存在: {}", versionDir.toAbsolutePath());
            return new ArrayList<>();
        }

        Path metadataFile = versionDir.resolve("metadata.json");
        logger.debug("元数据文件路径: {}", metadataFile.toAbsolutePath());
        
        if (!Files.exists(metadataFile)) {
            logger.warn("元数据文件不存在: {}", metadataFile.toAbsolutePath());
            return new ArrayList<>();
        }

        try {
            String json = Files.readString(metadataFile);
            logger.debug("读取元数据文件成功，内容长度: {} 字符", json.length());
            
            ObjectMapper jsonMapper = new ObjectMapper();
            VersionMetadata metadata = jsonMapper.readValue(json, VersionMetadata.class);
            
            if (metadata.versions == null || metadata.versions.isEmpty()) {
                logger.warn("元数据文件中没有版本记录");
                return new ArrayList<>();
            }
            
            logger.debug("找到 {} 个版本记录", metadata.versions.size());
            
            // 按时间戳倒序排序
            List<OntologyVersion> sortedVersions = metadata.versions.stream()
                .sorted(Comparator.comparing(OntologyVersion::getTimestamp).reversed())
                .collect(Collectors.toList());
            
            logger.info("成功加载版本历史，文件名: {}, 版本数量: {}", filename, sortedVersions.size());
            return sortedVersions;
        } catch (JsonProcessingException e) {
            logger.error("解析元数据JSON失败，文件名: {}, 错误: {}", filename, e.getMessage(), e);
            throw new IOException("解析版本元数据失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("读取版本历史失败，文件名: {}, 错误: {}", filename, e.getMessage(), e);
            throw new IOException("读取版本历史失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定版本
     */
    public OntologySchema getVersion(String filename, String version) throws IOException {
        // 提取基础文件名（移除版本号和扩展名）
        String baseName = extractBaseFilename(filename);
        Path versionDir = getVersionDir(baseName);
        String versionFileName = "v" + version.replace(".", "_") + ".yaml";
        Path versionFilePath = versionDir.resolve(versionFileName);
        
        if (!Files.exists(versionFilePath)) {
            throw new IOException("版本不存在: " + version + " (文件: " + filename + ", 基础名称: " + baseName + ")");
        }

        Parser parser = new Parser(versionFilePath.toString());
        return parser.parse();
    }

    /**
     * 获取当前版本信息
     */
    public OntologyVersion loadCurrentVersion(String filename) {
        try {
            // 提取基础文件名（移除版本号和扩展名），确保能找到所有相关版本
            String baseName = extractBaseFilename(filename);
            Path versionDir = getVersionDir(baseName);
            Path metadataFile = versionDir.resolve("metadata.json");
            
            if (!Files.exists(metadataFile)) {
                logger.debug("版本元数据文件不存在: {}", metadataFile.toAbsolutePath());
                return null;
            }

            String json = Files.readString(metadataFile);
            ObjectMapper jsonMapper = new ObjectMapper();
            VersionMetadata metadata = jsonMapper.readValue(json, VersionMetadata.class);
            
            if (metadata.versions == null || metadata.versions.isEmpty()) {
                logger.debug("版本元数据中没有版本记录: {}", metadataFile.toAbsolutePath());
                return null;
            }
            
            // 返回最新版本（按时间戳排序）
            OntologyVersion latest = metadata.versions.stream()
                .max(Comparator.comparing(OntologyVersion::getTimestamp))
                .orElse(null);
            
            if (latest != null) {
                logger.debug("找到最新版本: {} (文件: {})", latest.getVersion(), baseName);
            }
            
            return latest;
        } catch (Exception e) {
            logger.error("加载当前版本失败，文件名: {}, 错误: {}", filename, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 对比两个版本
     */
    public VersionComparator.DiffResult compareVersions(String filename, String version1, String version2) throws IOException {
        OntologySchema schema1 = getVersion(filename, version1);
        OntologySchema schema2 = getVersion(filename, version2);
        
        VersionComparator comparator = new VersionComparator();
        return comparator.compare(schema1, schema2);
    }

    /**
     * 回滚到指定版本
     */
    public String rollbackToVersion(String filename, String version) throws IOException {
        OntologySchema schema = getVersion(filename, version);
        return saveToOntologyFolder(schema, filename, null, "回滚到版本 " + version);
    }

    /**
     * 从文件名中提取基础文件名（移除版本号和扩展名）
     */
    private String extractBaseFilename(String filename) {
        // 移除扩展名
        String nameWithoutExt = filename.replaceAll("\\.(yaml|yml)$", "");
        // 移除版本号（格式：-数字.数字.数字）
        String baseName = nameWithoutExt.replaceAll("-\\d+\\.\\d+\\.\\d+$", "");
        return baseName;
    }

    /**
     * 获取版本目录（基于基础文件名，不含版本号）
     */
    private Path getVersionDir(String filename) {
        // 提取基础文件名（移除版本号和扩展名），确保所有版本保存在同一目录下
        String baseName = extractBaseFilename(filename);
        Path modelsDir = Paths.get("ontology", "models");
        Path versionDir = modelsDir.resolve(baseName);
        logger.debug("计算版本目录，输入文件名: {}, 基础名称: {}, 版本目录: {}", 
                    filename, baseName, versionDir.toAbsolutePath());
        return versionDir;
    }

    /**
     * 保存版本元数据
     */
    private void saveVersionMetadata(Path versionDir, OntologyVersion version) throws IOException {
        Path metadataFile = versionDir.resolve("metadata.json");
        VersionMetadata metadata;
        
        if (Files.exists(metadataFile)) {
            try {
                String json = Files.readString(metadataFile);
                ObjectMapper jsonMapper = new ObjectMapper();
                metadata = jsonMapper.readValue(json, VersionMetadata.class);
            } catch (Exception e) {
                metadata = new VersionMetadata();
            }
        } else {
            metadata = new VersionMetadata();
        }
        
        // 检查是否已存在该版本
        metadata.versions.removeIf(v -> v.getVersion().equals(version.getVersion()));
        metadata.versions.add(version);
        
        ObjectMapper jsonMapper = new ObjectMapper();
        String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        Files.writeString(metadataFile, json);
    }

    /**
     * 版本元数据类
     */
    private static class VersionMetadata {
        public List<OntologyVersion> versions = new ArrayList<>();
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
