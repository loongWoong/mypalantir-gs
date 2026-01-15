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
    try {
      const response = await apiClient.post<ApiResponse<{ id: string }>>(
        '/profile-templates',
        request
      );
      // 响应拦截器已经处理了错误码，这里只需要检查数据
      if (!response.data?.data) {
        throw new Error('创建模板失败：服务器返回数据为空');
      }
      if (!response.data.data.id) {
        throw new Error('创建模板失败：返回数据缺少ID');
      }
      return response.data.data;
    } catch (error) {
      // 重新抛出错误，让调用者处理
      throw error;
    }
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
    // 确保返回数组，即使 data 为 null 或 undefined
    return response.data?.data || [];
  },

  /**
   * 获取模板详情
   */
  get: async (templateId: string): Promise<ProfileTemplate> => {
    const response = await apiClient.get<ApiResponse<ProfileTemplate>>(
      `/profile-templates/${templateId}`
    );
    if (!response.data || !response.data.data) {
      throw new Error('模板不存在');
    }
    return response.data.data;
  },

  /**
   * 删除模板
   */
  delete: async (templateId: string): Promise<void> => {
    await apiClient.delete(`/profile-templates/${templateId}`);
  },
};
