package com.mypalantir.controller;

import com.mypalantir.entity.ProfileTemplate;
import com.mypalantir.service.ProfileTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 画像模板控制器
 */
@RestController
@RequestMapping("/api/v1/profile-templates")
@CrossOrigin
public class ProfileTemplateController {
    
    private final ProfileTemplateService templateService;
    
    public ProfileTemplateController(ProfileTemplateService templateService) {
        this.templateService = templateService;
    }
    
    /**
     * 创建模板
     */
    @PostMapping
    public ApiResponse<Map<String, String>> createTemplate(@RequestBody Map<String, Object> requestData) {
        try {
            // 手动转换 Map 到 ProfileTemplate，确保类型正确
            ProfileTemplate template = new ProfileTemplate();
            
            // 安全地转换字符串字段
            template.setId(convertToString(requestData.get("id")));
            template.setName(convertToString(requestData.get("name")));
            template.setDisplayName(convertToString(requestData.get("displayName")));
            template.setDescription(convertToString(requestData.get("description")));
            template.setEntityType(convertToString(requestData.get("entityType")));
            
            // craftState 可能是对象，需要转换为字符串
            Object craftStateObj = requestData.get("craftState");
            if (craftStateObj != null) {
                if (craftStateObj instanceof String) {
                    template.setCraftState((String) craftStateObj);
                } else {
                    // 如果是对象，转换为 JSON 字符串
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    template.setCraftState(mapper.writeValueAsString(craftStateObj));
                }
            }
            
            Object gridLayoutObj = requestData.get("gridLayout");
            if (gridLayoutObj != null) {
                if (gridLayoutObj instanceof String) {
                    template.setGridLayout((String) gridLayoutObj);
                } else {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    template.setGridLayout(mapper.writeValueAsString(gridLayoutObj));
                }
            }
            
            Object isPublicObj = requestData.get("isPublic");
            if (isPublicObj instanceof Boolean) {
                template.setIsPublic((Boolean) isPublicObj);
            } else if (isPublicObj instanceof String) {
                template.setIsPublic(Boolean.parseBoolean((String) isPublicObj));
            }
            
            template.setCreatorId(convertToString(requestData.get("creatorId")));
            
            ProfileTemplate created = templateService.createTemplate(template);
            return ApiResponse.success(Map.of("id", created.getId()));
        } catch (java.io.IOException e) {
            return ApiResponse.error(500, "Failed to create template: " + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    /**
     * 安全地将对象转换为字符串
     */
    private String convertToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        // 对于其他类型，转换为字符串
        return value.toString();
    }
    
    /**
     * 更新模板
     */
    @PutMapping("/{templateId}")
    public ApiResponse<ProfileTemplate> updateTemplate(
            @PathVariable String templateId,
            @RequestBody ProfileTemplate updates) {
        try {
            ProfileTemplate updated = templateService.updateTemplate(templateId, updates);
            return ApiResponse.success(updated);
        } catch (java.io.IOException e) {
            return ApiResponse.error(500, "Failed to update template: " + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    /**
     * 获取模板列表
     */
    @GetMapping
    public ApiResponse<List<ProfileTemplate>> listTemplates(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Boolean isPublic) {
        try {
            System.out.println("ProfileTemplateController.listTemplates - entityType: " + entityType + ", isPublic: " + isPublic);
            List<ProfileTemplate> templates = templateService.listTemplates(entityType, isPublic);
            System.out.println("ProfileTemplateController.listTemplates - 返回模板数量: " + templates.size());
            return ApiResponse.success(templates);
        } catch (java.io.IOException e) {
            System.err.println("ProfileTemplateController.listTemplates - IOException: " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.error(500, "Failed to list templates: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("ProfileTemplateController.listTemplates - Exception: " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.error(500, e.getMessage());
        }
    }
    
    /**
     * 获取模板详情
     */
    @GetMapping("/{templateId}")
    public ApiResponse<ProfileTemplate> getTemplate(@PathVariable String templateId) {
        try {
            ProfileTemplate template = templateService.getTemplateById(templateId);
            if (template == null) {
                return ApiResponse.error(404, "Template not found");
            }
            return ApiResponse.success(template);
        } catch (java.io.IOException e) {
            return ApiResponse.error(500, "Failed to get template: " + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }
    
    /**
     * 删除模板
     */
    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable String templateId) {
        try {
            templateService.deleteTemplate(templateId);
            return ApiResponse.success(null);
        } catch (java.io.IOException e) {
            return ApiResponse.error(500, "Failed to delete template: " + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
