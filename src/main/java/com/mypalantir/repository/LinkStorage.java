package com.mypalantir.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

public class LinkStorage implements ILinkStorage {
    private final PathManager pathManager;
    private final ObjectMapper objectMapper;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public LinkStorage(PathManager pathManager) {
        this.pathManager = pathManager;
        this.objectMapper = new ObjectMapper();
        // 注册 Java 8 时间类型支持模块
        this.objectMapper.registerModule(new JavaTimeModule());
        // 禁用将日期写为时间戳，使用 ISO-8601 字符串格式
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String createLink(String linkType, String sourceID, String targetID, Map<String, Object> properties) throws IOException {
        lock.writeLock().lock();
        try {
            String id = UUID.randomUUID().toString();
            String now = Instant.now().toString();

            Map<String, Object> link = new HashMap<>();
            link.put("id", id);
            link.put("source_id", sourceID);
            link.put("target_id", targetID);
            link.put("created_at", now);
            link.put("updated_at", now);
            if (properties != null) {
                link.putAll(properties);
            }

            // 确保目录存在
            String dir = pathManager.getLinkDir(linkType);
            Files.createDirectories(Paths.get(dir));

            // 写入文件
            String filePath = pathManager.getLinkPath(linkType, id);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), link);

            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, Object> getLink(String linkType, String id) throws IOException {
        lock.readLock().lock();
        try {
            String filePath = pathManager.getLinkPath(linkType, id);
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IOException("link not found");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(file, Map.class);
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateLink(String linkType, String id, Map<String, Object> properties) throws IOException {
        lock.writeLock().lock();
        try {
            Map<String, Object> existing = getLink(linkType, id);

            // 更新属性（不更新 source_id 和 target_id）
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                if (!"source_id".equals(key) && !"target_id".equals(key) && !"id".equals(key)) {
                    existing.put(key, entry.getValue());
                }
            }
            existing.put("updated_at", Instant.now().toString());

            // 写入文件
            String filePath = pathManager.getLinkPath(linkType, id);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), existing);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteLink(String linkType, String id) throws IOException {
        lock.writeLock().lock();
        try {
            String filePath = pathManager.getLinkPath(linkType, id);
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IOException("link not found");
            }
            Files.delete(Paths.get(filePath));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Map<String, Object>> getLinksBySource(String linkType, String sourceID) throws IOException {
        return getLinksByField(linkType, "source_id", sourceID);
    }

    public List<Map<String, Object>> getLinksByTarget(String linkType, String targetID) throws IOException {
        return getLinksByField(linkType, "target_id", targetID);
    }

    private List<Map<String, Object>> getLinksByField(String linkType, String field, String value) throws IOException {
        lock.readLock().lock();
        try {
            String dir = pathManager.getLinkDir(linkType);
            Path dirPath = Paths.get(dir);
            if (!Files.exists(dirPath)) {
                return List.of();
            }

            List<Map<String, Object>> results = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> link = objectMapper.readValue(p.toFile(), Map.class);
                            if (value.equals(link.get(field))) {
                                results.add(link);
                            }
                        } catch (IOException e) {
                            // 忽略读取失败的文件
                        }
                    });
            }

            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    public InstanceStorage.ListResult listLinks(String linkType, int offset, int limit) throws IOException {
        lock.readLock().lock();
        try {
            String dir = pathManager.getLinkDir(linkType);
            Path dirPath = Paths.get(dir);
            if (!Files.exists(dirPath)) {
                return new InstanceStorage.ListResult(List.of(), 0);
            }

            List<Map<String, Object>> links = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> link = objectMapper.readValue(p.toFile(), Map.class);
                            links.add(link);
                        } catch (IOException e) {
                            // 忽略读取失败的文件
                        }
                    });
            }

            int total = links.size();
            int end = Math.min(offset + limit, links.size());
            if (offset >= links.size()) {
                return new InstanceStorage.ListResult(List.of(), total);
            }

            return new InstanceStorage.ListResult(links.subList(offset, end), total);
        } finally {
            lock.readLock().unlock();
        }
    }
}

