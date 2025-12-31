package com.mypalantir.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class InstanceStorage {
    private final PathManager pathManager;
    private final ObjectMapper objectMapper;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public InstanceStorage(PathManager pathManager) {
        this.pathManager = pathManager;
        this.objectMapper = new ObjectMapper();
        // 注册 Java 8 时间类型支持模块
        this.objectMapper.registerModule(new JavaTimeModule());
        // 禁用将日期写为时间戳，使用 ISO-8601 字符串格式
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String createInstance(String objectType, Map<String, Object> data) throws IOException {
        lock.writeLock().lock();
        try {
            String id = UUID.randomUUID().toString();
            String now = Instant.now().toString();

            Map<String, Object> instance = new HashMap<>();
            instance.put("id", id);
            instance.put("created_at", now);
            instance.put("updated_at", now);
            instance.putAll(data);

            // 确保目录存在
            String dir = pathManager.getInstanceDir(objectType);
            Files.createDirectories(Paths.get(dir));

            // 写入文件
            String filePath = pathManager.getInstancePath(objectType, id);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), instance);

            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String createInstanceWithId(String objectType, String id, Map<String, Object> data) throws IOException {
        lock.writeLock().lock();
        try {
            String now = Instant.now().toString();

            Map<String, Object> instance = new HashMap<>();
            instance.put("id", id);
            instance.put("created_at", now);
            instance.put("updated_at", now);
            instance.putAll(data);

            // 确保目录存在
            String dir = pathManager.getInstanceDir(objectType);
            Files.createDirectories(Paths.get(dir));

            // 写入文件
            String filePath = pathManager.getInstancePath(objectType, id);
            File file = new File(filePath);
            if (file.exists()) {
                throw new IOException("instance with id '" + id + "' already exists");
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, instance);

            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, Object> getInstance(String objectType, String id) throws IOException {
        lock.readLock().lock();
        try {
            String filePath = pathManager.getInstancePath(objectType, id);
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IOException("instance not found");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(file, Map.class);
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateInstance(String objectType, String id, Map<String, Object> data) throws IOException {
        lock.writeLock().lock();
        try {
            Map<String, Object> existing = getInstance(objectType, id);

            // 更新数据
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                existing.put(entry.getKey(), entry.getValue());
            }
            existing.put("updated_at", Instant.now().toString());

            // 写入文件
            String filePath = pathManager.getInstancePath(objectType, id);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), existing);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteInstance(String objectType, String id) throws IOException {
        lock.writeLock().lock();
        try {
            String filePath = pathManager.getInstancePath(objectType, id);
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IOException("instance not found");
            }
            Files.delete(Paths.get(filePath));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ListResult listInstances(String objectType, int offset, int limit) throws IOException {
        lock.readLock().lock();
        try {
            String dir = pathManager.getInstanceDir(objectType);
            Path dirPath = Paths.get(dir);
            if (!Files.exists(dirPath)) {
                return new ListResult(List.of(), 0);
            }

            List<Map<String, Object>> instances = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> instance = objectMapper.readValue(p.toFile(), Map.class);
                            instances.add(instance);
                        } catch (IOException e) {
                            // 忽略读取失败的文件
                        }
                    });
            }

            int total = instances.size();
            int end = Math.min(offset + limit, instances.size());
            if (offset >= instances.size()) {
                return new ListResult(List.of(), total);
            }

            return new ListResult(instances.subList(offset, end), total);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Map<String, Object>> searchInstances(String objectType, Map<String, Object> filters) throws IOException {
        ListResult result = listInstances(objectType, 0, 10000);
        List<Map<String, Object>> instances = result.getItems();

        if (filters == null || filters.isEmpty()) {
            return instances;
        }

        return instances.stream()
            .filter(instance -> {
                for (Map.Entry<String, Object> filter : filters.entrySet()) {
                    Object value = instance.get(filter.getKey());
                    if (!Objects.equals(value, filter.getValue())) {
                        return false;
                    }
                }
                return true;
            })
            .collect(Collectors.toList());
    }

    /**
     * 批量获取实例
     * @param objectType 对象类型
     * @param ids 实例ID列表
     * @return 实例Map，key为ID，value为实例数据（如果不存在则为null）
     */
    public Map<String, Map<String, Object>> getInstancesBatch(String objectType, List<String> ids) throws IOException {
        lock.readLock().lock();
        try {
            Map<String, Map<String, Object>> result = new HashMap<>();
            
            for (String id : ids) {
                try {
                    Map<String, Object> instance = getInstance(objectType, id);
                    result.put(id, instance);
                } catch (IOException e) {
                    // 实例不存在，跳过
                    result.put(id, null);
                }
            }
            
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 批量获取多个对象类型的实例
     * @param typeIdMap key为对象类型，value为该类型的ID列表
     * @return Map，key为"objectType:id"，value为实例数据（如果不存在则为null）
     */
    public Map<String, Map<String, Object>> getInstancesBatchMultiType(Map<String, List<String>> typeIdMap) throws IOException {
        lock.readLock().lock();
        try {
            Map<String, Map<String, Object>> result = new HashMap<>();
            
            for (Map.Entry<String, List<String>> entry : typeIdMap.entrySet()) {
                String objectType = entry.getKey();
                List<String> ids = entry.getValue();
                
                Map<String, Map<String, Object>> instances = getInstancesBatch(objectType, ids);
                for (Map.Entry<String, Map<String, Object>> instanceEntry : instances.entrySet()) {
                    String key = objectType + ":" + instanceEntry.getKey();
                    result.put(key, instanceEntry.getValue());
                }
            }
            
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static class ListResult {
        private final List<Map<String, Object>> items;
        private final long total;

        public ListResult(List<Map<String, Object>> items, long total) {
            this.items = items;
            this.total = total;
        }

        public List<Map<String, Object>> getItems() {
            return items;
        }

        public long getTotal() {
            return total;
        }
    }
}

