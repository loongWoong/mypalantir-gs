package com.mypalantir.entity;

import java.time.LocalDateTime;

/**
 * 画像模板实体
 */
public class ProfileTemplate {
    
    private String id;
    private String name;
    private String displayName;
    private String description;
    private String entityType;  // Gantry/Vehicle/TollStation
    private String craftState;  // Craft.js 序列化的 JSON
    private String gridLayout;  // React-Grid-Layout 配置（可选）
    private Boolean isPublic;
    private String creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getEntityType() {
        return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    
    public String getCraftState() {
        return craftState;
    }
    
    public void setCraftState(String craftState) {
        this.craftState = craftState;
    }
    
    public String getGridLayout() {
        return gridLayout;
    }
    
    public void setGridLayout(String gridLayout) {
        this.gridLayout = gridLayout;
    }
    
    public Boolean getIsPublic() {
        return isPublic;
    }
    
    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
    
    public String getCreatorId() {
        return creatorId;
    }
    
    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
