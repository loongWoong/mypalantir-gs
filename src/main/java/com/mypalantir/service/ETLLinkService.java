package com.mypalantir.service;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.repository.ILinkStorage;
import com.mypalantir.repository.InstanceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * ETL关系服务
 * 供ETL系统调用，用于批量操作Links关系
 */
@Service
public class ETLLinkService {
    private static final Logger logger = LoggerFactory.getLogger(ETLLinkService.class);

    @Autowired
    private ILinkStorage linkStorage;

    @Autowired
    private Loader loader;

    /**
     * 获取指定关系类型的所有Links
     */
    public List<Map<String, Object>> getLinks(String linkType, String sourceType, String targetType, 
                                              Integer limit, Integer offset) throws Loader.NotFoundException, IOException {
        // 验证关系类型
        LinkType linkTypeDef = loader.getLinkType(linkType);
        
        // 验证源类型和目标类型
        if (sourceType != null && !sourceType.equals(linkTypeDef.getSourceType())) {
            throw new IllegalArgumentException("Source type mismatch: expected " + linkTypeDef.getSourceType() + ", got " + sourceType);
        }
        if (targetType != null && !targetType.equals(linkTypeDef.getTargetType())) {
            throw new IllegalArgumentException("Target type mismatch: expected " + linkTypeDef.getTargetType() + ", got " + targetType);
        }
        
        // 获取Links
        int limitValue = limit != null ? limit : 1000;
        int offsetValue = offset != null ? offset : 0;
        
        InstanceStorage.ListResult result = linkStorage.listLinks(linkType, offsetValue, limitValue);
        return result.getItems();
    }

    /**
     * 批量创建Links关系
     */
    public BatchCreateResult createLinksBatch(String linkType, List<LinkCreateRequest> requests) 
            throws Loader.NotFoundException, IOException {
        BatchCreateResult result = new BatchCreateResult();
        
        // 验证关系类型
        LinkType linkTypeDef = loader.getLinkType(linkType);
        
        for (LinkCreateRequest request : requests) {
            try {
                String linkId = linkStorage.createLink(linkType, request.getSourceId(), request.getTargetId(), 
                                                       request.getProperties() != null ? request.getProperties() : new HashMap<>());
                result.addSuccess(linkId, request.getSourceId(), request.getTargetId());
            } catch (Exception e) {
                result.addFailure(request.getSourceId(), request.getTargetId(), e.getMessage());
                logger.warn("Failed to create link from {} to {}: {}", request.getSourceId(), request.getTargetId(), e.getMessage());
            }
        }
        
        return result;
    }

    /**
     * 根据属性匹配查找Links关系
     * 基于linkType的property_mappings定义
     */
    public List<Map<String, Object>> matchLinks(String linkType, String sourceType, String targetType,
                                               Map<String, Object> sourceFilters, 
                                               Map<String, Object> targetFilters) 
            throws Loader.NotFoundException, IOException {
        // 验证关系类型
        LinkType linkTypeDef = loader.getLinkType(linkType);
        
        // 验证源类型和目标类型
        if (!sourceType.equals(linkTypeDef.getSourceType())) {
            throw new IllegalArgumentException("Source type mismatch: expected " + linkTypeDef.getSourceType() + ", got " + sourceType);
        }
        if (!targetType.equals(linkTypeDef.getTargetType())) {
            throw new IllegalArgumentException("Target type mismatch: expected " + linkTypeDef.getTargetType() + ", got " + targetType);
        }
        
        // 获取属性映射
        Map<String, String> propertyMappings = linkTypeDef.getPropertyMappings();
        if (propertyMappings == null || propertyMappings.isEmpty()) {
            throw new IllegalArgumentException("Link type '" + linkType + "' does not define property_mappings");
        }
        
        // 这里应该查询源实例和目标实例，然后根据属性匹配
        // 简化实现：返回空列表，实际应该实现匹配逻辑
        logger.warn("matchLinks not fully implemented yet");
        return Collections.emptyList();
    }

    /**
     * 批量删除Links关系
     */
    public BatchDeleteResult deleteLinksBatch(String linkType, List<String> linkIds) 
            throws Loader.NotFoundException, IOException {
        BatchDeleteResult result = new BatchDeleteResult();
        
        // 验证关系类型
        loader.getLinkType(linkType);
        
        for (String linkId : linkIds) {
            try {
                linkStorage.deleteLink(linkType, linkId);
                result.addSuccess(linkId);
            } catch (Exception e) {
                result.addFailure(linkId, e.getMessage());
                logger.warn("Failed to delete link {}: {}", linkId, e.getMessage());
            }
        }
        
        return result;
    }

    /**
     * Link创建请求
     */
    public static class LinkCreateRequest {
        private String sourceId;
        private String targetId;
        private Map<String, Object> properties;

        public String getSourceId() {
            return sourceId;
        }

        public void setSourceId(String sourceId) {
            this.sourceId = sourceId;
        }

        public String getTargetId() {
            return targetId;
        }

        public void setTargetId(String targetId) {
            this.targetId = targetId;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }
    }

    /**
     * 批量创建结果
     */
    public static class BatchCreateResult {
        private List<Map<String, Object>> success = new ArrayList<>();
        private List<Map<String, Object>> failures = new ArrayList<>();

        public void addSuccess(String linkId, String sourceId, String targetId) {
            Map<String, Object> item = new HashMap<>();
            item.put("link_id", linkId);
            item.put("source_id", sourceId);
            item.put("target_id", targetId);
            success.add(item);
        }

        public void addFailure(String sourceId, String targetId, String error) {
            Map<String, Object> item = new HashMap<>();
            item.put("source_id", sourceId);
            item.put("target_id", targetId);
            item.put("error", error);
            failures.add(item);
        }

        public List<Map<String, Object>> getSuccess() {
            return success;
        }

        public List<Map<String, Object>> getFailures() {
            return failures;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("success_count", success.size());
            result.put("failure_count", failures.size());
            result.put("success", success);
            result.put("failures", failures);
            return result;
        }
    }

    /**
     * 批量删除结果
     */
    public static class BatchDeleteResult {
        private List<String> success = new ArrayList<>();
        private List<Map<String, Object>> failures = new ArrayList<>();

        public void addSuccess(String linkId) {
            success.add(linkId);
        }

        public void addFailure(String linkId, String error) {
            Map<String, Object> item = new HashMap<>();
            item.put("link_id", linkId);
            item.put("error", error);
            failures.add(item);
        }

        public List<String> getSuccess() {
            return success;
        }

        public List<Map<String, Object>> getFailures() {
            return failures;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("success_count", success.size());
            result.put("failure_count", failures.size());
            result.put("success", success);
            result.put("failures", failures);
            return result;
        }
    }
}

