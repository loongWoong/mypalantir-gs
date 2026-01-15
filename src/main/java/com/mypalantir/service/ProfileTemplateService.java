package com.mypalantir.service;

import com.mypalantir.entity.ProfileTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 画像模板服务
 */
@Service
public class ProfileTemplateService {
    
    private final JdbcTemplate jdbcTemplate;
    
    public ProfileTemplateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 创建模板
     */
    public ProfileTemplate createTemplate(ProfileTemplate template) {
        // 检查名称是否重复
        String checkSql = "SELECT COUNT(*) FROM PROFILE_TEMPLATES WHERE entity_type = ? AND name = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, 
            template.getEntityType(), template.getName());
        
        if (count != null && count > 0) {
            throw new RuntimeException("Template name already exists for this entity type");
        }
        
        // 生成ID
        if (template.getId() == null) {
            template.setId(UUID.randomUUID().toString());
        }
        
        // 设置时间戳
        LocalDateTime now = LocalDateTime.now();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        
        // 插入数据
        String sql = "INSERT INTO PROFILE_TEMPLATES " +
                "(id, name, display_name, description, entity_type, craft_state, grid_layout, " +
                "is_public, creator_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(sql,
                template.getId(),
                template.getName(),
                template.getDisplayName(),
                template.getDescription(),
                template.getEntityType(),
                template.getCraftState(),
                template.getGridLayout(),
                template.getIsPublic() != null ? template.getIsPublic() : false,
                template.getCreatorId(),
                Timestamp.valueOf(template.getCreatedAt()),
                Timestamp.valueOf(template.getUpdatedAt())
        );
        
        return template;
    }
    
    /**
     * 更新模板
     */
    public ProfileTemplate updateTemplate(String id, ProfileTemplate updates) {
        // 检查模板是否存在
        ProfileTemplate existing = getTemplateById(id);
        if (existing == null) {
            throw new RuntimeException("Template not found: " + id);
        }
        
        // 更新时间
        updates.setUpdatedAt(LocalDateTime.now());
        
        StringBuilder sql = new StringBuilder("UPDATE PROFILE_TEMPLATES SET updated_at = ?");
        Object[] params = new Object[10];
        params[0] = Timestamp.valueOf(updates.getUpdatedAt());
        int paramIndex = 1;
        
        if (updates.getName() != null) {
            sql.append(", name = ?");
            params[paramIndex++] = updates.getName();
        }
        if (updates.getDisplayName() != null) {
            sql.append(", display_name = ?");
            params[paramIndex++] = updates.getDisplayName();
        }
        if (updates.getDescription() != null) {
            sql.append(", description = ?");
            params[paramIndex++] = updates.getDescription();
        }
        if (updates.getCraftState() != null) {
            sql.append(", craft_state = ?");
            params[paramIndex++] = updates.getCraftState();
        }
        if (updates.getGridLayout() != null) {
            sql.append(", grid_layout = ?");
            params[paramIndex++] = updates.getGridLayout();
        }
        if (updates.getIsPublic() != null) {
            sql.append(", is_public = ?");
            params[paramIndex++] = updates.getIsPublic();
        }
        
        sql.append(" WHERE id = ?");
        params[paramIndex++] = id;
        
        // 复制有效参数
        Object[] finalParams = new Object[paramIndex];
        System.arraycopy(params, 0, finalParams, 0, paramIndex);
        
        jdbcTemplate.update(sql.toString(), finalParams);
        
        return getTemplateById(id);
    }
    
    /**
     * 获取模板列表
     */
    public List<ProfileTemplate> listTemplates(String entityType, Boolean isPublic) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM PROFILE_TEMPLATES WHERE 1=1"
        );
        
        if (entityType != null) {
            sql.append(" AND entity_type = '").append(entityType).append("'");
        }
        if (isPublic != null) {
            sql.append(" AND is_public = ").append(isPublic);
        }
        
        sql.append(" ORDER BY created_at DESC");
        
        return jdbcTemplate.query(sql.toString(), new ProfileTemplateRowMapper());
    }
    
    /**
     * 获取模板详情
     */
    public ProfileTemplate getTemplateById(String id) {
        String sql = "SELECT * FROM PROFILE_TEMPLATES WHERE id = ?";
        List<ProfileTemplate> results = jdbcTemplate.query(sql, new ProfileTemplateRowMapper(), id);
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * 删除模板
     */
    public void deleteTemplate(String id) {
        String sql = "DELETE FROM PROFILE_TEMPLATES WHERE id = ?";
        int rows = jdbcTemplate.update(sql, id);
        if (rows == 0) {
            throw new RuntimeException("Template not found: " + id);
        }
    }
    
    /**
     * RowMapper
     */
    private static class ProfileTemplateRowMapper implements RowMapper<ProfileTemplate> {
        @Override
        public ProfileTemplate mapRow(ResultSet rs, int rowNum) throws SQLException {
            ProfileTemplate template = new ProfileTemplate();
            template.setId(rs.getString("id"));
            template.setName(rs.getString("name"));
            template.setDisplayName(rs.getString("display_name"));
            template.setDescription(rs.getString("description"));
            template.setEntityType(rs.getString("entity_type"));
            template.setCraftState(rs.getString("craft_state"));
            template.setGridLayout(rs.getString("grid_layout"));
            template.setIsPublic(rs.getBoolean("is_public"));
            template.setCreatorId(rs.getString("creator_id"));
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                template.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                template.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            return template;
        }
    }
}
