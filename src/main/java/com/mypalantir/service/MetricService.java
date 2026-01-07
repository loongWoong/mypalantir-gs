package com.mypalantir.service;

import com.mypalantir.meta.Loader;
import com.mypalantir.metric.AtomicMetric;
import com.mypalantir.metric.MetricDefinition;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 指标定义服务
 */
@Service
public class MetricService {
    private final IInstanceStorage storage;
    private final Loader loader;
    private final DataValidator validator;
    private final AtomicMetricService atomicMetricService;
    private LinkService linkService;

    public MetricService(IInstanceStorage storage, Loader loader, DataValidator validator, AtomicMetricService atomicMetricService) {
        this.storage = storage;
        this.loader = loader;
        this.validator = validator;
        this.atomicMetricService = atomicMetricService;
    }

    // 使用 setter 注入 LinkService，避免循环依赖
    public void setLinkService(LinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * 创建指标定义
     */
    public String createMetricDefinition(MetricDefinition metricDefinition) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        return createMetricDefinition(metricDefinition, null);
    }

    /**
     * 创建指标定义（支持指定工作空间）
     */
    public String createMetricDefinition(MetricDefinition metricDefinition, List<String> workspaceIds) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 验证派生指标必须引用原子指标
        if ("derived".equals(metricDefinition.getMetricType())) {
            if (metricDefinition.getAtomicMetricId() == null || metricDefinition.getAtomicMetricId().isEmpty()) {
                throw new IllegalArgumentException("派生指标必须引用原子指标");
            }
            // 验证原子指标存在
            atomicMetricService.getAtomicMetric(metricDefinition.getAtomicMetricId());
        }

        // 验证复合指标必须引用基础指标
        if ("composite".equals(metricDefinition.getMetricType())) {
            if (metricDefinition.getBaseMetricIds() == null || metricDefinition.getBaseMetricIds().isEmpty()) {
                throw new IllegalArgumentException("复合指标必须引用基础指标");
            }
            if (metricDefinition.getDerivedFormula() == null || metricDefinition.getDerivedFormula().isEmpty()) {
                throw new IllegalArgumentException("复合指标必须提供计算公式");
            }
        }

        // 验证业务范围
        if (metricDefinition.getBusinessScope() != null) {
            validateBusinessScope(metricDefinition.getBusinessScope());
        }

        // 生成ID
        if (metricDefinition.getId() == null || metricDefinition.getId().isEmpty()) {
            metricDefinition.setId(UUID.randomUUID().toString());
        }

        // 设置默认状态
        if (metricDefinition.getStatus() == null || metricDefinition.getStatus().isEmpty()) {
            metricDefinition.setStatus("active");
        }

        // 自动生成指标名称（如果未提供）
        if (metricDefinition.getName() == null || metricDefinition.getName().isEmpty()) {
            metricDefinition.setName(generateMetricName(metricDefinition));
        }

        // 验证数据（过滤掉空值，避免可选字符串字段触发类型校验错误）
        Map<String, Object> data = metricDefinition.toMap();
        data.entrySet().removeIf(entry -> entry.getValue() == null);
        validator.validateInstanceData("MetricDefinition", data);

        // 创建实例
        String metricId = storage.createInstanceWithId("MetricDefinition", metricDefinition.getId(), data);

        // 创建归属关系（如果提供了工作空间ID列表）
        if (workspaceIds != null && !workspaceIds.isEmpty() && linkService != null) {
            for (String workspaceId : workspaceIds) {
                try {
                    linkService.createLink("metric_definition_belongs_to_workspace", metricId, workspaceId, new HashMap<>());
                } catch (Exception e) {
                    // 记录错误但不中断创建流程
                    System.err.println("Failed to create workspace link for metric definition " + metricId + " to workspace " + workspaceId + ": " + e.getMessage());
                }
            }
        }

        return metricId;
    }

    /**
     * 获取指标定义
     */
    public MetricDefinition getMetricDefinition(String id) throws IOException {
        Map<String, Object> data = storage.getInstance("MetricDefinition", id);
        return new MetricDefinition(data);
    }

    /**
     * 更新指标定义
     */
    public void updateMetricDefinition(String id, MetricDefinition metricDefinition) throws Loader.NotFoundException, DataValidator.ValidationException, IOException {
        // 验证数据
        Map<String, Object> data = metricDefinition.toMap();
        validator.validateInstanceData("MetricDefinition", data);

        // 更新实例
        storage.updateInstance("MetricDefinition", id, data);
    }

    /**
     * 删除指标定义
     */
    public void deleteMetricDefinition(String id) throws IOException {
        storage.deleteInstance("MetricDefinition", id);
    }

    /**
     * 列出所有指标定义
     */
    public List<MetricDefinition> listMetricDefinitions() throws IOException {
        InstanceStorage.ListResult result = storage.listInstances("MetricDefinition", 0, Integer.MAX_VALUE);
        return result.getItems().stream()
                .map(MetricDefinition::new)
                .collect(Collectors.toList());
    }

    /**
     * 根据类型查找指标
     */
    public List<MetricDefinition> findMetricsByType(String metricType) throws IOException {
        Map<String, Object> filters = new HashMap<>();
        filters.put("metric_type", metricType);
        List<Map<String, Object>> instances = storage.searchInstances("MetricDefinition", filters);
        return instances.stream()
                .map(MetricDefinition::new)
                .collect(Collectors.toList());
    }

    /**
     * 验证业务范围配置
     */
    private void validateBusinessScope(Map<String, Object> businessScope) throws Loader.NotFoundException {
        String type = (String) businessScope.get("type");
        if ("single".equals(type)) {
            String baseObjectType = (String) businessScope.get("base_object_type");
            if (baseObjectType != null) {
                loader.getObjectType(baseObjectType);
            }
        } else if ("multi".equals(type)) {
            String from = (String) businessScope.get("from");
            if (from != null) {
                loader.getObjectType(from);
            }
            // 验证links中的关联类型
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) businessScope.get("links");
            if (links != null) {
                for (Map<String, Object> link : links) {
                    String linkTypeName = (String) link.get("name");
                    loader.getLinkType(linkTypeName);
                }
            }
        }
    }

    /**
     * 自动生成指标名称
     */
    private String generateMetricName(MetricDefinition metricDefinition) throws IOException {
        if ("derived".equals(metricDefinition.getMetricType())) {
            // 派生指标：时间周期 + 原子指标名称
            AtomicMetric atomicMetric = atomicMetricService.getAtomicMetric(metricDefinition.getAtomicMetricId());
            String timePrefix = getTimePrefix(metricDefinition.getTimeGranularity());
            return timePrefix + atomicMetric.getName();
        } else if ("composite".equals(metricDefinition.getMetricType())) {
            // 复合指标：基于公式生成名称
            return metricDefinition.getDisplayName() != null ? metricDefinition.getDisplayName() : "复合指标";
        }
        return "指标";
    }

    /**
     * 获取时间前缀
     */
    private String getTimePrefix(String timeGranularity) {
        if (timeGranularity == null) {
            return "";
        }
        switch (timeGranularity) {
            case "day":
                return "日";
            case "week":
                return "周";
            case "month":
                return "月";
            case "quarter":
                return "季度";
            case "year":
                return "年";
            default:
                return "";
        }
    }
}
