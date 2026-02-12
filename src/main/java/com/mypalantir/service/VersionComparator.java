package com.mypalantir.service;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.OntologySchema;
import com.mypalantir.meta.Property;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 版本对比服务
 */
public class VersionComparator {
    
    /**
     * 对比两个版本的差异
     */
    public DiffResult compare(OntologySchema v1, OntologySchema v2) {
        DiffResult result = new DiffResult();
        
        // 对比对象类型
        result.objectTypeDiffs = compareObjectTypes(
            v1.getObjectTypes() != null ? v1.getObjectTypes() : new ArrayList<>(),
            v2.getObjectTypes() != null ? v2.getObjectTypes() : new ArrayList<>()
        );
        
        // 对比关系类型
        result.linkTypeDiffs = compareLinkTypes(
            v1.getLinkTypes() != null ? v1.getLinkTypes() : new ArrayList<>(),
            v2.getLinkTypes() != null ? v2.getLinkTypes() : new ArrayList<>()
        );
        
        // 对比命名空间和版本
        if (!equals(v1.getNamespace(), v2.getNamespace())) {
            result.metadataChanges.add("命名空间: " + v1.getNamespace() + " → " + v2.getNamespace());
        }
        if (!equals(v1.getVersion(), v2.getVersion())) {
            result.metadataChanges.add("版本: " + v1.getVersion() + " → " + v2.getVersion());
        }
        
        return result;
    }
    
    private List<ObjectTypeDiff> compareObjectTypes(List<ObjectType> v1, List<ObjectType> v2) {
        List<ObjectTypeDiff> diffs = new ArrayList<>();
        
        Map<String, ObjectType> v1Map = v1.stream()
            .collect(Collectors.toMap(ObjectType::getName, ot -> ot));
        Map<String, ObjectType> v2Map = v2.stream()
            .collect(Collectors.toMap(ObjectType::getName, ot -> ot));
        
        // 查找新增和修改的对象类型
        for (ObjectType ot2 : v2) {
            ObjectType ot1 = v1Map.get(ot2.getName());
            if (ot1 == null) {
                diffs.add(new ObjectTypeDiff(ot2.getName(), ChangeType.ADDED, null, ot2));
            } else {
                ObjectTypeDiff diff = compareObjectType(ot1, ot2);
                if (diff != null) {
                    diffs.add(diff);
                }
            }
        }
        
        // 查找删除的对象类型
        for (ObjectType ot1 : v1) {
            if (!v2Map.containsKey(ot1.getName())) {
                diffs.add(new ObjectTypeDiff(ot1.getName(), ChangeType.DELETED, ot1, null));
            }
        }
        
        return diffs;
    }
    
    private ObjectTypeDiff compareObjectType(ObjectType ot1, ObjectType ot2) {
        ObjectTypeDiff diff = new ObjectTypeDiff(ot2.getName(), ChangeType.MODIFIED, ot1, ot2);
        boolean hasChanges = false;
        
        // 对比属性
        if (!equals(ot1.getDisplayName(), ot2.getDisplayName())) {
            diff.changes.add("display_name: " + ot1.getDisplayName() + " → " + ot2.getDisplayName());
            hasChanges = true;
        }
        if (!equals(ot1.getDescription(), ot2.getDescription())) {
            diff.changes.add("description: " + ot1.getDescription() + " → " + ot2.getDescription());
            hasChanges = true;
        }
        if (!equals(ot1.getBaseType(), ot2.getBaseType())) {
            diff.changes.add("base_type: " + ot1.getBaseType() + " → " + ot2.getBaseType());
            hasChanges = true;
        }
        
        // 对比属性列表
        List<PropertyDiff> propertyDiffs = compareProperties(
            ot1.getProperties() != null ? ot1.getProperties() : new ArrayList<>(),
            ot2.getProperties() != null ? ot2.getProperties() : new ArrayList<>()
        );
        if (!propertyDiffs.isEmpty()) {
            diff.propertyDiffs = propertyDiffs;
            hasChanges = true;
        }
        
        return hasChanges ? diff : null;
    }
    
    private List<PropertyDiff> compareProperties(List<Property> p1, List<Property> p2) {
        List<PropertyDiff> diffs = new ArrayList<>();
        
        Map<String, Property> p1Map = p1.stream()
            .collect(Collectors.toMap(Property::getName, prop -> prop));
        Map<String, Property> p2Map = p2.stream()
            .collect(Collectors.toMap(Property::getName, prop -> prop));
        
        for (Property prop2 : p2) {
            Property prop1 = p1Map.get(prop2.getName());
            if (prop1 == null) {
                diffs.add(new PropertyDiff(prop2.getName(), ChangeType.ADDED, null, prop2));
            } else if (!propertiesEqual(prop1, prop2)) {
                diffs.add(new PropertyDiff(prop2.getName(), ChangeType.MODIFIED, prop1, prop2));
            }
        }
        
        for (Property prop1 : p1) {
            if (!p2Map.containsKey(prop1.getName())) {
                diffs.add(new PropertyDiff(prop1.getName(), ChangeType.DELETED, prop1, null));
            }
        }
        
        return diffs;
    }
    
    private boolean propertiesEqual(Property p1, Property p2) {
        return equals(p1.getName(), p2.getName()) &&
               equals(p1.getDataType(), p2.getDataType()) &&
               p1.isRequired() == p2.isRequired() &&
               equals(p1.getDescription(), p2.getDescription()) &&
               equals(p1.getDefaultValue(), p2.getDefaultValue());
    }
    
    private List<LinkTypeDiff> compareLinkTypes(List<LinkType> v1, List<LinkType> v2) {
        List<LinkTypeDiff> diffs = new ArrayList<>();
        
        Map<String, LinkType> v1Map = v1.stream()
            .collect(Collectors.toMap(
                lt -> lt.getName() + "::" + lt.getSourceType() + "::" + lt.getTargetType(),
                lt -> lt
            ));
        Map<String, LinkType> v2Map = v2.stream()
            .collect(Collectors.toMap(
                lt -> lt.getName() + "::" + lt.getSourceType() + "::" + lt.getTargetType(),
                lt -> lt
            ));
        
        for (LinkType lt2 : v2) {
            String key = lt2.getName() + "::" + lt2.getSourceType() + "::" + lt2.getTargetType();
            LinkType lt1 = v1Map.get(key);
            if (lt1 == null) {
                diffs.add(new LinkTypeDiff(lt2.getName(), ChangeType.ADDED, null, lt2));
            } else {
                LinkTypeDiff diff = compareLinkType(lt1, lt2);
                if (diff != null) {
                    diffs.add(diff);
                }
            }
        }
        
        for (LinkType lt1 : v1) {
            String key = lt1.getName() + "::" + lt1.getSourceType() + "::" + lt1.getTargetType();
            if (!v2Map.containsKey(key)) {
                diffs.add(new LinkTypeDiff(lt1.getName(), ChangeType.DELETED, lt1, null));
            }
        }
        
        return diffs;
    }
    
    private LinkTypeDiff compareLinkType(LinkType lt1, LinkType lt2) {
        LinkTypeDiff diff = new LinkTypeDiff(lt2.getName(), ChangeType.MODIFIED, lt1, lt2);
        boolean hasChanges = false;
        
        if (!equals(lt1.getDisplayName(), lt2.getDisplayName())) {
            diff.changes.add("display_name: " + lt1.getDisplayName() + " → " + lt2.getDisplayName());
            hasChanges = true;
        }
        if (!equals(lt1.getDescription(), lt2.getDescription())) {
            diff.changes.add("description: " + lt1.getDescription() + " → " + lt2.getDescription());
            hasChanges = true;
        }
        if (!equals(lt1.getCardinality(), lt2.getCardinality())) {
            diff.changes.add("cardinality: " + lt1.getCardinality() + " → " + lt2.getCardinality());
            hasChanges = true;
        }
        if (!equals(lt1.getDirection(), lt2.getDirection())) {
            diff.changes.add("direction: " + lt1.getDirection() + " → " + lt2.getDirection());
            hasChanges = true;
        }
        
        return hasChanges ? diff : null;
    }
    
    private boolean equals(Object o1, Object o2) {
        if (o1 == null && o2 == null) return true;
        if (o1 == null || o2 == null) return false;
        return o1.equals(o2);
    }
    
    // 差异结果类
    public static class DiffResult {
        public List<ObjectTypeDiff> objectTypeDiffs = new ArrayList<>();
        public List<LinkTypeDiff> linkTypeDiffs = new ArrayList<>();
        public List<String> metadataChanges = new ArrayList<>();
        
        public boolean hasChanges() {
            return !objectTypeDiffs.isEmpty() || !linkTypeDiffs.isEmpty() || !metadataChanges.isEmpty();
        }
    }
    
    // 变更类型
    public enum ChangeType {
        ADDED, MODIFIED, DELETED
    }
    
    // 对象类型差异
    public static class ObjectTypeDiff {
        public String name;
        public ChangeType type;
        public ObjectType oldValue;
        public ObjectType newValue;
        public List<String> changes = new ArrayList<>();
        public List<PropertyDiff> propertyDiffs = new ArrayList<>();
        
        public ObjectTypeDiff(String name, ChangeType type, ObjectType oldValue, ObjectType newValue) {
            this.name = name;
            this.type = type;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }
    
    // 属性差异
    public static class PropertyDiff {
        public String name;
        public ChangeType type;
        public Property oldValue;
        public Property newValue;
        
        public PropertyDiff(String name, ChangeType type, Property oldValue, Property newValue) {
            this.name = name;
            this.type = type;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }
    
    // 关系类型差异
    public static class LinkTypeDiff {
        public String name;
        public ChangeType type;
        public LinkType oldValue;
        public LinkType newValue;
        public List<String> changes = new ArrayList<>();
        
        public LinkTypeDiff(String name, ChangeType type, LinkType oldValue, LinkType newValue) {
            this.name = name;
            this.type = type;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }
}

