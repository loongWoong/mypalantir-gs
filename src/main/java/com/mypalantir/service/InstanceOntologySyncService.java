package com.mypalantir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.mypalantir.meta.Loader;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 实例与本体文件双向同步服务
 * 实现图数据库（Neo4j）与 ontology 本地文件夹之间的数据迁移
 * 用于 database、workspace、mapping 等系统模型的双向同步
 */
@Service
public class InstanceOntologySyncService {
    private static final Logger logger = LoggerFactory.getLogger(InstanceOntologySyncService.class);
    private static final String ONTOLOGY_DIR = "ontology";
    private static final String SYSTEM_PREFIX = "system-";

    private final IInstanceStorage storage;
    private final IInstanceStorage graphStorage;
    private final Loader loader;
    private final ObjectMapper yamlMapper;

    private static final Set<String> SYSTEM_SYNC_TYPES = Set.of("database", "workspace", "mapping");

    public InstanceOntologySyncService(IInstanceStorage storage,
                                      @Autowired(required = false) @Qualifier("graphInstanceStorage") IInstanceStorage graphStorage,
                                      Loader loader) {
        this.storage = storage;
        this.graphStorage = graphStorage != null ? graphStorage : storage;
        this.loader = loader;
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false);
        yamlFactory.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);
        yamlFactory.configure(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR, true);
        this.yamlMapper = new ObjectMapper(yamlFactory);
    }

    /**
     * 获取默认文件名：前缀 + 对象类型名
     * 如 system-database.yaml, system-workspace.yaml, system-mapping.yaml
     */
    public String getDefaultFilename(String objectType) {
        return SYSTEM_PREFIX + objectType + ".yaml";
    }

    /**
     * 图数据库 → 本体：将指定对象类型的所有实例从图数据库导出到 ontology 本地文件
     */
    public ExportResult exportToOntology(String objectType, String filename) throws IOException, Loader.NotFoundException {
        loader.getObjectType(objectType);

        String normalizedFilename = normalizeFilename(filename);
        if (!normalizedFilename.endsWith(".yaml") && !normalizedFilename.endsWith(".yml")) {
            normalizedFilename = normalizedFilename + ".yaml";
        }

        // 分页获取所有实例（系统类型用 Neo4j 直接读取完整数据，其他用 storage）
        List<Map<String, Object>> allInstances = new ArrayList<>();
        int offset = 0;
        int limit = 500;
        InstanceStorage.ListResult result;
        IInstanceStorage sourceStorage = SYSTEM_SYNC_TYPES.contains(objectType.toLowerCase()) ? graphStorage : storage;
        do {
            result = sourceStorage.listInstances(objectType, offset, limit);
            allInstances.addAll(result.getItems());
            offset += limit;
        } while (result.getItems().size() == limit && offset < result.getTotal());

        // 构建导出结构（移除 created_at/updated_at 等内部字段，保留业务数据）
        Map<String, Object> exportData = new LinkedHashMap<>();
        exportData.put("object_type", objectType);
        exportData.put("exported_at", java.time.Instant.now().toString());
        exportData.put("count", allInstances.size());

        List<Map<String, Object>> cleanedInstances = new ArrayList<>();
        for (Map<String, Object> inst : allInstances) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : inst.entrySet()) {
                String key = e.getKey();
                if (!"created_at".equals(key) && !"updated_at".equals(key)) {
                    cleaned.put(key, e.getValue());
                }
            }
            cleanedInstances.add(cleaned);
        }
        exportData.put("instances", cleanedInstances);

        Path ontologyDir = Paths.get(ONTOLOGY_DIR);
        if (!Files.exists(ontologyDir)) {
            Files.createDirectories(ontologyDir);
        }
        Path filePath = ontologyDir.resolve(normalizedFilename);
        String yamlContent = yamlMapper.writeValueAsString(exportData);
        Files.writeString(filePath, yamlContent);

        logger.info("Exported {} instances of type {} to {}", allInstances.size(), objectType, filePath);
        return new ExportResult(filePath.toString(), allInstances.size());
    }

    /**
     * 本体 → 图数据库：将 ontology 本地文件中的实例导入到图数据库
     */
    public ImportResult importFromOntology(String objectType, String filename) throws IOException, Loader.NotFoundException {
        loader.getObjectType(objectType);

        String normalizedFilename = normalizeFilename(filename);
        if (!normalizedFilename.endsWith(".yaml") && !normalizedFilename.endsWith(".yml")) {
            normalizedFilename = normalizedFilename + ".yaml";
        }

        Path ontologyDir = Paths.get(ONTOLOGY_DIR);
        Path filePath = ontologyDir.resolve(normalizedFilename);
        if (!Files.exists(filePath)) {
            throw new IOException("文件不存在: " + normalizedFilename);
        }

        String yamlContent = Files.readString(filePath);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = yamlMapper.readValue(yamlContent, Map.class);

        Object instancesObj = data.get("instances");
        if (!(instancesObj instanceof List)) {
            throw new IOException("YAML 格式错误：缺少 instances 数组");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> instances = (List<Map<String, Object>>) instancesObj;
        int created = 0;
        int updated = 0;

        for (Map<String, Object> inst : instances) {
            Object idObj = inst.get("id");
            if (idObj == null) {
                logger.warn("跳过无 id 的实例: {}", inst);
                continue;
            }
            String id = idObj.toString();

            // 构建更新数据（排除 id）
            Map<String, Object> updateData = new HashMap<>(inst);
            updateData.remove("id");

            // 系统类型使用 Neo4j 直接读写，保留完整字段
            IInstanceStorage targetStorage = SYSTEM_SYNC_TYPES.contains(objectType.toLowerCase()) ? graphStorage : storage;
            try {
                targetStorage.getInstance(objectType, id);
                // 存在则更新
                targetStorage.updateInstance(objectType, id, updateData);
                updated++;
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("not found")) {
                    // 不存在则创建（保留原 id）
                    updateData.put("id", id);
                    try {
                        targetStorage.createInstanceWithId(objectType, id, updateData);
                        created++;
                    } catch (IOException ex) {
                        logger.error("创建实例失败 objectType={}, id={}: {}", objectType, id, ex.getMessage());
                    }
                } else {
                    logger.error("检查实例失败 objectType={}, id={}: {}", objectType, id, e.getMessage());
                }
            }
        }

        logger.info("Imported {} instances of type {} from {} (created={}, updated={})",
                instances.size(), objectType, filePath, created, updated);
        return new ImportResult(created, updated);
    }

    private static String normalizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "system-instances.yaml";
        }
        return filename.trim();
    }

    public static class ExportResult {
        public final String filePath;
        public final int count;

        public ExportResult(String filePath, int count) {
            this.filePath = filePath;
            this.count = count;
        }
    }

    public static class ImportResult {
        public final int created;
        public final int updated;

        public ImportResult(int created, int updated) {
            this.created = created;
            this.updated = updated;
        }
    }
}
