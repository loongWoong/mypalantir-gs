package com.mypalantir.query;

import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段路径解析器
 * 解析类似 "hasTollRecords.charge_time" 这样的路径，确定字段来自哪个对象类型
 */
public class FieldPathResolver {
    private final Loader loader;
    
    public FieldPathResolver(Loader loader) {
        this.loader = loader;
    }
    
    /**
     * 解析字段路径
     * @param fieldPath 字段路径，如 "province" 或 "hasTollRecords.charge_time"
     * @param rootObjectType 根对象类型
     * @param links 已定义的 links（用于解析路径中的 link 名称）
     * @return 解析结果，包含对象类型和属性名
     */
    public FieldPath resolve(String fieldPath, ObjectType rootObjectType, 
                            List<OntologyQuery.LinkQuery> links) throws Exception {
        if (fieldPath == null || fieldPath.isEmpty()) {
            throw new IllegalArgumentException("Field path cannot be empty");
        }
        
        // 如果路径不包含 "."，说明是根对象的属性
        if (!fieldPath.contains(".")) {
            // 验证属性是否存在
            Property property = findProperty(rootObjectType, fieldPath);
            if (property == null) {
                throw new IllegalArgumentException("Property '" + fieldPath + "' not found in object type '" + 
                    rootObjectType.getName() + "'");
            }
            return new FieldPath(rootObjectType, fieldPath, null);
        }
        
        // 解析路径：linkName.propertyName
        String[] parts = fieldPath.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid field path: " + fieldPath);
        }
        
        String linkName = parts[0];
        String propertyName = parts[1];
        
        // 查找对应的 link
        OntologyQuery.LinkQuery linkQuery = findLink(links, linkName);
        if (linkQuery == null) {
            throw new IllegalArgumentException("Link '" + linkName + "' not found in query links");
        }
        
        // 获取 link type
        LinkType linkType;
        try {
            linkType = loader.getLinkType(linkName);
        } catch (Loader.NotFoundException e) {
            throw new IllegalArgumentException("Link type '" + linkName + "' not found in schema");
        }
        
        // 确定目标对象类型
        ObjectType targetObjectType;
        if (linkType.getSourceType().equals(rootObjectType.getName())) {
            // 从 source 到 target
            try {
                targetObjectType = loader.getObjectType(linkType.getTargetType());
            } catch (Loader.NotFoundException e) {
                throw new IllegalArgumentException("Target object type '" + linkType.getTargetType() + 
                    "' not found for link '" + linkName + "'");
            }
        } else if (linkType.getTargetType().equals(rootObjectType.getName())) {
            // 从 target 到 source（仅适用于 undirected link）
            if ("directed".equals(linkType.getDirection())) {
                throw new IllegalArgumentException("Cannot query directed link '" + linkName + 
                    "' from target side");
            }
            try {
                targetObjectType = loader.getObjectType(linkType.getSourceType());
            } catch (Loader.NotFoundException e) {
                throw new IllegalArgumentException("Source object type '" + linkType.getSourceType() + 
                    "' not found for link '" + linkName + "'");
            }
        } else {
            throw new IllegalArgumentException("Link '" + linkName + "' is not connected to object type '" + 
                rootObjectType.getName() + "'");
        }
        
        // 验证属性是否存在
        Property property = findProperty(targetObjectType, propertyName);
        if (property == null) {
            throw new IllegalArgumentException("Property '" + propertyName + "' not found in object type '" + 
                targetObjectType.getName() + "'");
        }
        
        return new FieldPath(targetObjectType, propertyName, linkType);
    }
    
    /**
     * 在对象类型中查找属性
     */
    private Property findProperty(ObjectType objectType, String propertyName) {
        if (objectType.getProperties() == null) {
            return null;
        }
        for (Property prop : objectType.getProperties()) {
            if (prop.getName().equals(propertyName)) {
                return prop;
            }
        }
        return null;
    }
    
    /**
     * 在 links 中查找 link query
     */
    private OntologyQuery.LinkQuery findLink(List<OntologyQuery.LinkQuery> links, String linkName) {
        if (links == null) {
            return null;
        }
        for (OntologyQuery.LinkQuery link : links) {
            if (linkName.equals(link.getName())) {
                return link;
            }
        }
        return null;
    }
    
    /**
     * 字段路径解析结果
     */
    public static class FieldPath {
        private final ObjectType objectType;
        private final String propertyName;
        private final LinkType linkType;  // 如果字段来自关联对象，这里是对应的 link type
        
        public FieldPath(ObjectType objectType, String propertyName, LinkType linkType) {
            this.objectType = objectType;
            this.propertyName = propertyName;
            this.linkType = linkType;
        }
        
        public ObjectType getObjectType() {
            return objectType;
        }
        
        public String getPropertyName() {
            return propertyName;
        }
        
        public LinkType getLinkType() {
            return linkType;
        }
        
        /**
         * 判断字段是否来自关联对象
         */
        public boolean isFromLinkedObject() {
            return linkType != null;
        }
    }
}

