package com.mypalantir.service;

import com.mypalantir.entity.ProfileTemplate;
import com.mypalantir.meta.Loader;
import com.mypalantir.repository.InstanceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * 画像模板服务
 * 使用 InstanceService 存储，支持 Neo4j 和文件存储
 */
@Service
public class ProfileTemplateService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProfileTemplateService.class);
    
    private final InstanceService instanceService;
    private final Loader loader;
    
    public ProfileTemplateService(InstanceService instanceService, Loader loader) {
        this.instanceService = instanceService;
        this.loader = loader;
    }
    
    /**
     * 创建模板
     */
    public ProfileTemplate createTemplate(ProfileTemplate template) throws IOException {
        // 检查名称是否重复
        Map<String, Object> filters = new HashMap<>();
        filters.put("entity_type", template.getEntityType());
        filters.put("name", template.getName());
        
        try {
            List<Map<String, Object>> existing = instanceService.listInstances(
                "ProfileTemplateInstance", 0, 1, filters
            ).getItems();
            
            if (!existing.isEmpty()) {
                throw new RuntimeException("Template name already exists for this entity type");
            }
        } catch (Loader.NotFoundException e) {
            // 对象类型不存在，继续创建（首次创建）
        }
        
        // 生成ID
        if (template.getId() == null) {
            template.setId(UUID.randomUUID().toString());
        }
        
        // 设置时间戳（date 类型需要 YYYY-MM-DD 格式）
        String now = java.time.LocalDate.now().toString();
        
        // 转换为 Map 格式，确保所有字段类型符合 schema 定义
        Map<String, Object> data = new HashMap<>();
        
        logger.info("=== 开始创建模板 ===");
        logger.info("原始模板数据 - id: {} (type: {}), name: {} (type: {}), displayName: {} (type: {}), entityType: {} (type: {})",
            template.getId(), template.getId() != null ? template.getId().getClass().getSimpleName() : "null",
            template.getName(), template.getName() != null ? template.getName().getClass().getSimpleName() : "null",
            template.getDisplayName(), template.getDisplayName() != null ? template.getDisplayName().getClass().getSimpleName() : "null",
            template.getEntityType(), template.getEntityType() != null ? template.getEntityType().getClass().getSimpleName() : "null");
        logger.info("craftState: {} (type: {})", 
            template.getCraftState() != null ? (template.getCraftState().length() > 100 ? template.getCraftState().substring(0, 100) + "..." : template.getCraftState()) : "null",
            template.getCraftState() != null ? template.getCraftState().getClass().getSimpleName() : "null");
        
        // 所有字符串字段必须确保是 String 类型，不能为 null（对于 required 字段）
        String id = convertToString(template.getId());
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        logger.debug("转换后 id: {} (type: {})", id, id != null ? id.getClass().getSimpleName() : "null");
        data.put("id", id);
        
        String name = convertToString(template.getName());
        if (name == null || name.isEmpty()) {
            throw new RuntimeException("Template name is required");
        }
        logger.debug("转换后 name: {} (type: {})", name, name != null ? name.getClass().getSimpleName() : "null");
        data.put("name", name);
        
        String displayName = convertToString(template.getDisplayName());
        if (displayName == null) {
            displayName = name; // 如果没有显示名称，使用 name
        }
        logger.debug("转换后 display_name: {} (type: {})", displayName, displayName != null ? displayName.getClass().getSimpleName() : "null");
        data.put("display_name", displayName);
        
        // description 是可选的，如果为 null 则不放入 data map
        String description = convertToString(template.getDescription());
        logger.debug("转换后 description: {} (type: {})", description, description != null ? description.getClass().getSimpleName() : "null");
        if (description != null) {
            data.put("description", description);
        }
        
        String entityType = convertToString(template.getEntityType());
        if (entityType == null || entityType.isEmpty()) {
            throw new RuntimeException("Entity type is required");
        }
        logger.debug("转换后 entity_type: {} (type: {})", entityType, entityType != null ? entityType.getClass().getSimpleName() : "null");
        data.put("entity_type", entityType);
        
        // craft_state 必须是字符串类型
        Object craftStateObj = template.getCraftState();
        String craftState = convertToString(craftStateObj);
        if (craftState == null || craftState.isEmpty()) {
            craftState = "{}";
        }
        logger.debug("转换后 craft_state: {} (type: {}, length: {})", 
            craftState.length() > 100 ? craftState.substring(0, 100) + "..." : craftState,
            craftState != null ? craftState.getClass().getSimpleName() : "null",
            craftState != null ? craftState.length() : 0);
        data.put("craft_state", craftState);
        
        // grid_layout 是可选的，如果为 null 则不放入 data map
        Object gridLayoutObj = template.getGridLayout();
        String gridLayout = convertToString(gridLayoutObj);
        logger.debug("转换后 grid_layout: {} (type: {})", gridLayout, gridLayout != null ? gridLayout.getClass().getSimpleName() : "null");
        if (gridLayout != null) {
            data.put("grid_layout", gridLayout);
        }
        
        Boolean isPublic = template.getIsPublic() != null ? template.getIsPublic() : false;
        logger.debug("转换后 is_public: {} (type: {})", isPublic, isPublic != null ? isPublic.getClass().getSimpleName() : "null");
        data.put("is_public", isPublic);
        
        // creator_id 是可选的，如果为 null 则不放入 data map
        String creatorId = convertToString(template.getCreatorId());
        logger.debug("转换后 creator_id: {} (type: {})", creatorId, creatorId != null ? creatorId.getClass().getSimpleName() : "null");
        if (creatorId != null) {
            data.put("creator_id", creatorId);
        }
        
        logger.debug("转换后 created_at: {} (type: {})", now, now != null ? now.getClass().getSimpleName() : "null");
        data.put("created_at", now);
        logger.debug("转换后 updated_at: {} (type: {})", now, now != null ? now.getClass().getSimpleName() : "null");
        data.put("updated_at", now);
        
        // 记录最终 data map 的所有字段和类型
        logger.info("=== 最终 data map 内容 ===");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            String valueType = value != null ? value.getClass().getSimpleName() : "null";
            String valueStr = value != null ? (value instanceof String && ((String) value).length() > 100 
                ? ((String) value).substring(0, 100) + "..." : value.toString()) : "null";
            logger.info("  {}: {} (type: {})", entry.getKey(), valueStr, valueType);
        }
        
        // 使用 InstanceService 创建实例
        try {
            logger.info("调用 instanceService.createInstance...");
            id = instanceService.createInstance("ProfileTemplateInstance", data);
            logger.info("模板创建成功，返回 id: {}", id);
            template.setId(id);
            return template;
        } catch (Loader.NotFoundException e) {
            logger.error("ProfileTemplateInstance 对象类型未找到", e);
            throw new RuntimeException("ProfileTemplateInstance object type not found in schema", e);
        } catch (DataValidator.ValidationException e) {
            logger.error("模板数据验证失败: {}", e.getMessage(), e);
            // 记录验证失败时的详细数据
            logger.error("验证失败时的数据内容:");
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object value = entry.getValue();
                String valueType = value != null ? value.getClass().getSimpleName() : "null";
                logger.error("  {}: {} (type: {})", entry.getKey(), value, valueType);
            }
            throw new RuntimeException("Template data validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 更新模板
     */
    public ProfileTemplate updateTemplate(String id, ProfileTemplate updates) throws IOException {
        // 检查模板是否存在
        ProfileTemplate existing = getTemplateById(id);
        if (existing == null) {
            throw new RuntimeException("Template not found: " + id);
        }
        
        // 构建更新数据
        Map<String, Object> data = new HashMap<>();
        data.put("updated_at", java.time.LocalDate.now().toString());
        
        if (updates.getName() != null) {
            data.put("name", updates.getName());
        }
        if (updates.getDisplayName() != null) {
            data.put("display_name", updates.getDisplayName());
        }
        if (updates.getDescription() != null) {
            data.put("description", updates.getDescription());
        }
        if (updates.getCraftState() != null) {
            data.put("craft_state", convertToString(updates.getCraftState()));
        }
        if (updates.getGridLayout() != null) {
            data.put("grid_layout", convertToString(updates.getGridLayout()));
        }
        if (updates.getIsPublic() != null) {
            data.put("is_public", updates.getIsPublic());
        }
        
        // 使用 InstanceService 更新实例
        try {
            instanceService.updateInstance("ProfileTemplateInstance", id, data);
            return getTemplateById(id);
        } catch (Loader.NotFoundException e) {
            throw new RuntimeException("ProfileTemplateInstance object type not found in schema", e);
        } catch (DataValidator.ValidationException e) {
            throw new RuntimeException("Template data validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取模板列表
     */
    public List<ProfileTemplate> listTemplates(String entityType, Boolean isPublic) throws IOException {
        logger.info("=== 开始查询模板列表 ===");
        logger.info("查询参数 - entityType: {}, isPublic: {}", entityType, isPublic);
        
        Map<String, Object> filters = new HashMap<>();
        if (entityType != null) {
            filters.put("entity_type", entityType);
            logger.info("添加过滤条件: entity_type = {}", entityType);
        }
        if (isPublic != null) {
            filters.put("is_public", isPublic);
            logger.info("添加过滤条件: is_public = {}", isPublic);
        }
        
        try {
            logger.info("调用 instanceService.listInstances with filters: {}", filters);
            InstanceStorage.ListResult result = instanceService.listInstances(
                "ProfileTemplateInstance", 0, 1000, filters.isEmpty() ? null : filters
            );
            
            logger.info("查询结果 - 总数: {}", result.getTotal());
            logger.info("查询结果 - 返回项数: {}", result.getItems().size());
            
            List<ProfileTemplate> templates = new ArrayList<>();
            for (Map<String, Object> instance : result.getItems()) {
                logger.debug("处理模板实例: {}", instance.get("id"));
                templates.add(mapToProfileTemplate(instance));
            }
            
            // 按创建时间降序排序
            templates.sort((a, b) -> {
                String aTime = a.getCreatedAt() != null ? a.getCreatedAt().toString() : "";
                String bTime = b.getCreatedAt() != null ? b.getCreatedAt().toString() : "";
                return bTime.compareTo(aTime);
            });
            
            logger.info("最终返回模板数量: {}", templates.size());
            return templates;
        } catch (Loader.NotFoundException e) {
            logger.warn("ProfileTemplateInstance 对象类型未找到，返回空列表", e);
            // 对象类型不存在，返回空列表
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取模板详情
     */
    public ProfileTemplate getTemplateById(String id) throws IOException {
        try {
            Map<String, Object> instance = instanceService.getInstance("ProfileTemplateInstance", id);
            return mapToProfileTemplate(instance);
        } catch (IOException e) {
            if ("instance not found".equals(e.getMessage())) {
                return null;
            }
            throw e;
        }
    }
    
    /**
     * 删除模板
     */
    public void deleteTemplate(String id) throws IOException {
        try {
            instanceService.deleteInstance("ProfileTemplateInstance", id);
        } catch (IOException e) {
            if ("instance not found".equals(e.getMessage())) {
                throw new RuntimeException("Template not found: " + id);
            }
            throw e;
        }
    }
    
    /**
     * 将 Map 转换为 ProfileTemplate 对象
     */
    private ProfileTemplate mapToProfileTemplate(Map<String, Object> instance) {
        ProfileTemplate template = new ProfileTemplate();
        
        // 安全地转换字符串字段，处理可能的类型不匹配
        template.setId(convertToString(instance.get("id")));
        template.setName(convertToString(instance.get("name")));
        template.setDisplayName(convertToString(instance.get("display_name")));
        template.setDescription(convertToString(instance.get("description")));
        template.setEntityType(convertToString(instance.get("entity_type")));
        
        // craft_state 可能是 Map（从 Neo4j 读取时），需要转换为 JSON 字符串
        Object craftStateObj = instance.get("craft_state");
        if (craftStateObj != null) {
            if (craftStateObj instanceof String) {
                template.setCraftState((String) craftStateObj);
            } else {
                // 如果是 Map 或其他对象，转换为 JSON 字符串
                template.setCraftState(convertToString(craftStateObj));
            }
        }
        
        // grid_layout 同样处理
        Object gridLayoutObj = instance.get("grid_layout");
        if (gridLayoutObj != null) {
            if (gridLayoutObj instanceof String) {
                template.setGridLayout((String) gridLayoutObj);
            } else {
                template.setGridLayout(convertToString(gridLayoutObj));
            }
        }
        
        // 处理 is_public 字段
        Object isPublic = instance.get("is_public");
        if (isPublic instanceof Boolean) {
            template.setIsPublic((Boolean) isPublic);
        } else if (isPublic instanceof String) {
            template.setIsPublic(Boolean.parseBoolean((String) isPublic));
        } else if (isPublic instanceof Number) {
            template.setIsPublic(((Number) isPublic).intValue() != 0);
        }
        
        template.setCreatorId(convertToString(instance.get("creator_id")));
        
        // 处理时间字段（Neo4j 存储为字符串）
        String createdAt = convertToString(instance.get("created_at"));
        if (createdAt != null && !createdAt.isEmpty()) {
            try {
                template.setCreatedAt(java.time.Instant.parse(createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            } catch (Exception e) {
                logger.warn("Failed to parse created_at: {}", createdAt, e);
            }
        }
        
        String updatedAt = convertToString(instance.get("updated_at"));
        if (updatedAt != null && !updatedAt.isEmpty()) {
            try {
                template.setUpdatedAt(java.time.Instant.parse(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            } catch (Exception e) {
                logger.warn("Failed to parse updated_at: {}", updatedAt, e);
            }
        }
        
        return template;
    }
    
    /**
     * 确保值是字符串类型
     */
    private String ensureString(String value) {
        return value != null ? value : null;
    }
    
    /**
     * 将对象转换为字符串（用于 JSON 字段）
     */
    private String convertToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        // 如果是对象（如 Map），转换为 JSON 字符串
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            // 如果转换失败，使用 toString()
            return value.toString();
        }
    }
}
