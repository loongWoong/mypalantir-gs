package com.mypalantir.service;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.repository.ILinkStorage;
import com.mypalantir.repository.InstanceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class LinkSyncService {
    private static final Logger log = LoggerFactory.getLogger(LinkSyncService.class);

    /** 并发线程数（可按需调整） */
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    /** 每批处理的源实例数量 */
    private static final int BATCH_SIZE = 200;
    /** 单次全量加载的最大实例数（超出则分页） */
    private static final int MAX_LOAD_SIZE = 100_000;

    @Autowired
    private LinkService linkService;

    @Autowired
    private IInstanceStorage instanceStorage;

    @Autowired
    private ILinkStorage linkStorage;

    @Autowired
    private Loader loader;

    /**
     * 根据模型定义同步关系（多线程批量优化版）
     *
     * <p>优化点：
     * <ol>
     *   <li>将目标实例按匹配属性组合键建立 HashMap 索引，查找从 O(N×M) 降为 O(1)</li>
     *   <li>一次性加载所有已存在关系到 Set，避免逐条查询数据库</li>
     *   <li>将源实例分批，使用线程池并发处理，充分利用多核</li>
     * </ol>
     */
    public SyncResult syncLinksByType(String linkTypeName) throws Loader.NotFoundException, IOException {
        LinkType linkType = loader.getLinkType(linkTypeName);
        String sourceType = linkType.getSourceType();
        String targetType = linkType.getTargetType();
        Map<String, String> propertyMappings = linkType.getPropertyMappings();

        if (propertyMappings == null || propertyMappings.isEmpty()) {
            throw new IllegalArgumentException("Link type '" + linkTypeName + "' does not define property_mappings");
        }

        // ── 1. 加载所有源实例 ──────────────────────────────────────────────
        List<Map<String, Object>> allSources = loadAllInstances(sourceType);
        // ── 2. 加载所有目标实例并建立复合键索引 ──────────────────────────────
        List<Map<String, Object>> allTargets = loadAllInstances(targetType);
        Map<String, List<Map<String, Object>>> targetIndex = buildTargetIndex(allTargets, propertyMappings);

        // ── 3. 一次性加载所有已存在关系，构建 "sourceId:targetId" Set ────────
        Set<String> existingLinkKeys = loadExistingLinkKeys(linkTypeName);

        log.info("[LinkSync] {} sources={}, targets={}, existingLinks={}, threads={}",
                linkTypeName, allSources.size(), allTargets.size(), existingLinkKeys.size(), THREAD_POOL_SIZE);

        // ── 4. 计算待创建关系列表（单线程，纯内存操作）─────────────────────
        // 先在内存中确定所有需要创建的 (sourceId, targetId) 对，
        // 避免多线程并发写 Neo4j 时产生重复检查和连接风暴
        List<String[]> toCreate = new ArrayList<>();
        for (Map<String, Object> source : allSources) {
            String sourceId = (String) source.get("id");
            if (sourceId == null) continue;

            String lookupKey = buildSourceLookupKey(source, propertyMappings);
            if (lookupKey == null) continue;

            List<Map<String, Object>> matchedTargets = targetIndex.getOrDefault(lookupKey, Collections.emptyList());
            for (Map<String, Object> target : matchedTargets) {
                String targetId = (String) target.get("id");
                if (targetId == null) continue;
                String linkKey = sourceId + ":" + targetId;
                if (!existingLinkKeys.contains(linkKey)) {
                    toCreate.add(new String[]{sourceId, targetId});
                    existingLinkKeys.add(linkKey); // 去重，防止重复入队
                }
            }
        }

        log.info("[LinkSync] {} toCreate={}", linkTypeName, toCreate.size());

        // ── 5. 分批并发写入 Neo4j ────────────────────────────────────────────
        // 控制并发度：过高的并发会让 Neo4j 连接池耗尽并导致 CPU 100%
        // 建议并发数 ≤ Neo4j 连接池大小（默认 100），此处保守取 CPU 核数
        int writeConcurrency = Math.max(1, Runtime.getRuntime().availableProcessors());
        AtomicInteger linksCreated = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(writeConcurrency);
        List<Future<?>> futures = new ArrayList<>();

        List<List<String[]>> batches = partition(toCreate, BATCH_SIZE);
        for (List<String[]> batch : batches) {
            futures.add(executor.submit(() -> {
                for (String[] pair : batch) {
                    try {
                        linkService.createLink(linkTypeName, pair[0], pair[1], new HashMap<>());
                        linksCreated.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        log.debug("[LinkSync] Failed to create link {}->{}: {}", pair[0], pair[1], e.getMessage());
                    }
                }
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LinkSync] Sync interrupted for {}", linkTypeName);
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (ExecutionException | InterruptedException e) {
                log.error("[LinkSync] Batch task failed", e);
            }
        }

        SyncResult result = new SyncResult();
        result.linksCreated = linksCreated.get();
        result.linksSkipped = existingLinkKeys.size() - toCreate.size(); // 原有已存在数量
        result.errors = errors.get();
        result.sourceCount = allSources.size();
        result.targetCount = allTargets.size();

        log.info("[LinkSync] {} done: created={}, skipped={}, errors={}",
                linkTypeName, result.linksCreated, result.linksSkipped, result.errors);
        return result;
    }

    // ── 私有辅助方法 ─────────────────────────────────────────────────────────

    /**
     * 分页加载全量实例（避免单次加载过多导致 OOM）
     * 用 offset 与 total 控制终止，不依赖 list.size()
     */
    private List<Map<String, Object>> loadAllInstances(String objectType) throws IOException {
        List<Map<String, Object>> all = new ArrayList<>();
        int offset = 0;
        int pageSize = 5000;
        long total = -1;
        while (true) {
            InstanceStorage.ListResult page = instanceStorage.listInstances(objectType, offset, pageSize);
            if (total < 0) total = page.getTotal();
            all.addAll(page.getItems());
            offset += page.getItems().size();
            if (page.getItems().isEmpty() || offset >= total || all.size() >= MAX_LOAD_SIZE) {
                break;
            }
        }
        return all;
    }

    /**
     * 一次性加载所有已存在关系，构建 "sourceId:targetId" 的 HashSet
     * 使用 offset 与 total 控制分页，避免因 Set 去重导致 size() < total 的无限循环
     */
    private Set<String> loadExistingLinkKeys(String linkTypeName) throws IOException {
        Set<String> keys = new HashSet<>();
        int offset = 0;
        int pageSize = 5000;
        long total = -1;
        while (true) {
            InstanceStorage.ListResult page = linkStorage.listLinks(linkTypeName, offset, pageSize);
            if (total < 0) total = page.getTotal();
            for (Map<String, Object> link : page.getItems()) {
                String src = (String) link.get("source_id");
                String tgt = (String) link.get("target_id");
                if (src != null && tgt != null) {
                    keys.add(src + ":" + tgt);
                }
            }
            offset += page.getItems().size();
            // 用 offset（已读取条数）与 total 对比，不依赖 Set.size()
            if (page.getItems().isEmpty() || offset >= total) {
                break;
            }
        }
        return keys;
    }

    /**
     * 将目标实例列表按「目标属性值组合键」建立索引
     * key 格式：targetProp1Value|targetProp2Value|...（按 propertyMappings 的 value 顺序）
     */
    private Map<String, List<Map<String, Object>>> buildTargetIndex(
            List<Map<String, Object>> targets,
            Map<String, String> propertyMappings) {
        // 固定顺序的目标属性名列表
        List<String> targetProps = new ArrayList<>(propertyMappings.values());

        Map<String, List<Map<String, Object>>> index = new HashMap<>();
        for (Map<String, Object> target : targets) {
            String key = buildCompositeKey(target, targetProps);
            if (key != null) {
                index.computeIfAbsent(key, k -> new ArrayList<>()).add(target);
            }
        }
        return index;
    }

    /**
     * 用源实例的属性值构造与目标索引相同格式的查找键
     * key 格式：sourceValue1|sourceValue2|...（按 propertyMappings 的 key 顺序，与目标 value 顺序一致）
     */
    private String buildSourceLookupKey(Map<String, Object> source, Map<String, String> propertyMappings) {
        List<String> sourceProps = new ArrayList<>(propertyMappings.keySet());
        return buildCompositeKey(source, sourceProps);
    }

    /**
     * 从实例中按指定属性列表构造复合键，任意属性为 null 则返回 null（不参与匹配）
     */
    private String buildCompositeKey(Map<String, Object> instance, List<String> props) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < props.size(); i++) {
            Object val = instance.get(props.get(i));
            if (val == null) return null;
            if (i > 0) sb.append('|');
            sb.append(val);
        }
        return sb.toString();
    }

    /**
     * 将列表均匀分成若干批次
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    // ── 结果类 ────────────────────────────────────────────────────────────────

    public static class SyncResult {
        public int linksCreated = 0;
        public int linksSkipped = 0;
        public int errors = 0;
        public int sourceCount = 0;
        public int targetCount = 0;

        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("links_created", linksCreated);
            result.put("links_skipped", linksSkipped);
            result.put("errors", errors);
            result.put("source_count", sourceCount);
            result.put("target_count", targetCount);
            return result;
        }
    }
}
