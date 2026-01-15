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
    public ApiResponse<Map<String, String>> createTemplate(@RequestBody ProfileTemplate template) {
        try {
            ProfileTemplate created = templateService.createTemplate(template);
            return ApiResponse.success(Map.of("id", created.getId()));
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
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
            List<ProfileTemplate> templates = templateService.listTemplates(entityType, isPublic);
            return ApiResponse.success(templates);
        } catch (Exception e) {
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
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
