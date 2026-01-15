import apiClient from './client';
import type { ProfileTemplate, CreateTemplateRequest, UpdateTemplateRequest } from '../types/profile';

// API响应类型
interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

/**
 * 画像模板 API
 */
export const profileTemplateApi = {
  /**
   * 创建模板
   */
  create: async (request: CreateTemplateRequest): Promise<{ id: string }> => {
    const response = await apiClient.post<ApiResponse<{ id: string }>>(
      '/profile-templates',
      request
    );
    return response.data.data;
  },

  /**
   * 更新模板
   */
  update: async (templateId: string, request: UpdateTemplateRequest): Promise<ProfileTemplate> => {
    const response = await apiClient.put<ApiResponse<ProfileTemplate>>(
      `/profile-templates/${templateId}`,
      request
    );
    return response.data.data;
  },

  /**
   * 获取模板列表
   */
  list: async (entityType?: string, isPublic?: boolean): Promise<ProfileTemplate[]> => {
    const params: any = {};
    if (entityType) params.entityType = entityType;
    if (isPublic !== undefined) params.isPublic = isPublic;

    const response = await apiClient.get<ApiResponse<ProfileTemplate[]>>(
      '/profile-templates',
      { params }
    );
    return response.data.data;
  },

  /**
   * 获取模板详情
   */
  get: async (templateId: string): Promise<ProfileTemplate> => {
    const response = await apiClient.get<ApiResponse<ProfileTemplate>>(
      `/profile-templates/${templateId}`
    );
    return response.data.data;
  },

  /**
   * 删除模板
   */
  delete: async (templateId: string): Promise<void> => {
    await apiClient.delete(`/profile-templates/${templateId}`);
  },
};
