package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.metric.AtomicMetric;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 原子指标服务
 */
@Service
public class AtomicMetricService {
    private final IInstanceStorage storage;
    private final Loader loader;
    private DataValidator validator;
    private LinkService linkService;

    public AtomicMetricService(IInstanceStorage storage, Loader loader) {
        this.storage = storage;
        this.loader = loader;
    }

    // 使用 setter 注入 LinkService，避免循环依赖
    public void setLinkService(LinkService linkService) {
        this.linkService = linkService;
    }

    // 使用 setter 注入 DataValidator，避免循环依赖
    public void setDataValidator(DataValidator validator) {
        this.validator = validator;
    }

    /**
     * 创建原子指标
     */
    public String createAtomicMetric(AtomicMetric atomicMetric) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        return createAtomicMetric(atomicMetric, null);
    }

    /**
     * 创建原子指标（支持指定工作空间）
     */
    public String createAtomicMetric(AtomicMetric atomicMetric, List<String> workspaceIds) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 验证业务过程对象类型存在
        loader.getObjectType(atomicMetric.getBusinessProcess());

        // 生成ID（在验证之前，因为 id 是必填字段）
        if (atomicMetric.getId() == null || atomicMetric.getId().isEmpty()) {
            atomicMetric.setId(UUID.randomUUID().toString());
        }

        // 设置默认状态（在验证之前，因为 status 是必填字段）
        if (atomicMetric.getStatus() == null || atomicMetric.getStatus().isEmpty()) {
            atomicMetric.setStatus("active");
        }

        // 验证数据（在设置默认值之后）
        Map<String, Object> data = atomicMetric.toMap();
        validator.validateInstanceData("AtomicMetric", data);

        // 创建实例
        String metricId = storage.createInstanceWithId("AtomicMetric", atomicMetric.getId(), atomicMetric.toMap());

        // 创建归属关系（如果提供了工作空间ID列表）
        if (workspaceIds != null && !workspaceIds.isEmpty() && linkService != null) {
            for (String workspaceId : workspaceIds) {
                try {
                    linkService.createLink("atomic_metric_belongs_to_workspace", metricId, workspaceId, new HashMap<>());
                } catch (Exception e) {
                    // 记录错误但不中断创建流程
                    System.err.println("Failed to create workspace link for atomic metric " + metricId + " to workspace " + workspaceId + ": " + e.getMessage());
                }
            }
        }

        return metricId;
    }

    /**
     * 获取原子指标
     */
    public AtomicMetric getAtomicMetric(String id) throws IOException {
        Map<String, Object> data = storage.getInstance("AtomicMetric", id);
        return new AtomicMetric(data);
    }

    /**
     * 更新原子指标
     */
    public void updateAtomicMetric(String id, AtomicMetric atomicMetric) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 验证业务过程对象类型存在
        if (atomicMetric.getBusinessProcess() != null) {
            loader.getObjectType(atomicMetric.getBusinessProcess());
        }

        // 验证数据
        Map<String, Object> data = atomicMetric.toMap();
        validator.validateInstanceData("AtomicMetric", data);

        // 更新实例
        storage.updateInstance("AtomicMetric", id, data);
    }

    /**
     * 删除原子指标
     */
    public void deleteAtomicMetric(String id) throws IOException {
        storage.deleteInstance("AtomicMetric", id);
    }

    /**
     * 列出所有原子指标
     */
    public List<AtomicMetric> listAtomicMetrics() throws IOException {
        InstanceStorage.ListResult result = storage.listInstances("AtomicMetric", 0, Integer.MAX_VALUE);
        return result.getItems().stream()
                .map(AtomicMetric::new)
                .collect(Collectors.toList());
    }

    /**
     * 根据业务过程查找原子指标
     */
    public List<AtomicMetric> findAtomicMetricsByBusinessProcess(String businessProcess) throws IOException {
        Map<String, Object> filters = new HashMap<>();
        filters.put("business_process", businessProcess);
        List<Map<String, Object>> instances = storage.searchInstances("AtomicMetric", filters);
        return instances.stream()
                .map(AtomicMetric::new)
                .collect(Collectors.toList());
    }
}
