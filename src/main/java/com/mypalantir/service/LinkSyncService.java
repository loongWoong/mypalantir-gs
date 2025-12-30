package com.mypalantir.service;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.repository.InstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LinkSyncService {
    @Autowired
    private LinkService linkService;

    @Autowired
    private InstanceStorage instanceStorage;

    @Autowired
    private Loader loader;

    /**
     * 根据模型定义同步关系
     * 基于linkType的property_mappings定义，支持多属性匹配（所有属性都必须匹配）
     * property_mappings格式：{"source_property1": "target_property1", "source_property2": "target_property2", ...}
     */
    public SyncResult syncLinksByType(String linkTypeName) throws Loader.NotFoundException, IOException {
        SyncResult result = new SyncResult();
        
        // 获取关系类型定义
        LinkType linkType = loader.getLinkType(linkTypeName);
        String sourceType = linkType.getSourceType();
        String targetType = linkType.getTargetType();
        Map<String, String> propertyMappings = linkType.getPropertyMappings();
        
        // 如果没有定义属性匹配，无法自动同步
        if (propertyMappings == null || propertyMappings.isEmpty()) {
            throw new IllegalArgumentException("Link type '" + linkTypeName + "' does not define property_mappings");
        }
        
        // 获取所有源实例
        InstanceStorage.ListResult sourceInstances = instanceStorage.listInstances(sourceType, 0, 10000);
        
        // 获取所有目标实例
        InstanceStorage.ListResult targetInstances = instanceStorage.listInstances(targetType, 0, 10000);
        
        // 遍历源实例，查找匹配的目标实例并创建关系
        for (Map<String, Object> source : sourceInstances.getItems()) {
            // 查找所有属性都匹配的目标实例
            List<Map<String, Object>> matchedTargets = findMatchingTargets(source, targetInstances.getItems(), propertyMappings);
            
            for (Map<String, Object> matchedTarget : matchedTargets) {
                String sourceId = (String) source.get("id");
                String targetId = (String) matchedTarget.get("id");
                
                // 检查关系是否已存在
                if (!linkExists(linkTypeName, sourceId, targetId)) {
                    try {
                        linkService.createLink(linkTypeName, sourceId, targetId, new HashMap<>());
                        result.linksCreated++;
                    } catch (Exception e) {
                        // 忽略创建失败的关系（可能已存在）
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * 查找匹配的目标实例
     * 所有属性匹配规则都必须满足（AND关系）
     * propertyMappings格式：{"source_property1": "target_property1", "source_property2": "target_property2", ...}
     */
    private List<Map<String, Object>> findMatchingTargets(
            Map<String, Object> source,
            List<Map<String, Object>> targets,
            Map<String, String> propertyMappings) {
        List<Map<String, Object>> matched = new ArrayList<>();
        
        for (Map<String, Object> target : targets) {
            boolean allMatch = true;
            
            // 检查所有属性匹配规则
            for (Map.Entry<String, String> mapping : propertyMappings.entrySet()) {
                String sourceProperty = mapping.getKey();
                String targetProperty = mapping.getValue();
                
                if (sourceProperty == null || targetProperty == null) {
                    continue;
                }
                
                Object sourceValue = source.get(sourceProperty);
                Object targetValue = target.get(targetProperty);
                
                // 如果任一属性值为null，不匹配
                if (sourceValue == null || targetValue == null) {
                    allMatch = false;
                    break;
                }
                
                // 比较属性值（支持不同类型的比较）
                if (!valuesMatch(sourceValue, targetValue)) {
                    allMatch = false;
                    break;
                }
            }
            
            if (allMatch) {
                matched.add(target);
            }
        }
        
        return matched;
    }

    /**
     * 比较两个值是否匹配（支持字符串和数值类型）
     */
    private boolean valuesMatch(Object value1, Object value2) {
        if (value1 == null && value2 == null) {
            return true;
        }
        if (value1 == null || value2 == null) {
            return false;
        }
        
        // 转换为字符串进行比较（支持不同类型的值）
        return String.valueOf(value1).equals(String.valueOf(value2));
    }

    /**
     * 检查关系是否已存在
     */
    private boolean linkExists(String linkType, String sourceId, String targetId) {
        try {
            List<Map<String, Object>> existingLinks = linkService.getLinksBySource(linkType, sourceId);
            for (Map<String, Object> link : existingLinks) {
                if (targetId.equals(link.get("target_id"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static class SyncResult {
        public int linksCreated = 0;
        
        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("links_created", linksCreated);
            return result;
        }
    }
}
