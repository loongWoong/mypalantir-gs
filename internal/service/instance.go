package service

import (
	"fmt"

	"mypalantir/internal/dsl"
	"mypalantir/internal/storage"
)

// InstanceService 实例服务
type InstanceService struct {
	storage  *storage.InstanceStorage
	loader   *dsl.Loader
	validator *DataValidator
}

// NewInstanceService 创建实例服务
func NewInstanceService(instanceStorage *storage.InstanceStorage, loader *dsl.Loader, validator *DataValidator) *InstanceService {
	return &InstanceService{
		storage:   instanceStorage,
		loader:    loader,
		validator: validator,
	}
}

// CreateInstance 创建实例
func (s *InstanceService) CreateInstance(objectType string, data map[string]interface{}) (string, error) {
	// 验证对象类型存在
	if _, err := s.loader.GetObjectType(objectType); err != nil {
		return "", fmt.Errorf("object type '%s' not found", objectType)
	}

	// 验证数据
	if err := s.validator.ValidateInstanceData(objectType, data); err != nil {
		return "", err
	}

	// 创建实例
	id, err := s.storage.CreateInstance(objectType, data)
	if err != nil {
		return "", fmt.Errorf("failed to create instance: %w", err)
	}

	return id, nil
}

// GetInstance 获取实例
func (s *InstanceService) GetInstance(objectType string, id string) (map[string]interface{}, error) {
	return s.storage.GetInstance(objectType, id)
}

// UpdateInstance 更新实例
func (s *InstanceService) UpdateInstance(objectType string, id string, data map[string]interface{}) error {
	// 验证对象类型存在
	if _, err := s.loader.GetObjectType(objectType); err != nil {
		return fmt.Errorf("object type '%s' not found", objectType)
	}

	// 验证数据
	if err := s.validator.ValidateInstanceData(objectType, data); err != nil {
		return err
	}

	// 更新实例
	return s.storage.UpdateInstance(objectType, id, data)
}

// DeleteInstance 删除实例
func (s *InstanceService) DeleteInstance(objectType string, id string) error {
	// 检查是否有关系依赖（简化处理，实际应该检查所有关系类型）
	// TODO: 实现关系依赖检查

	return s.storage.DeleteInstance(objectType, id)
}

// ListInstances 列出实例
func (s *InstanceService) ListInstances(objectType string, offset, limit int, filters map[string]interface{}) ([]map[string]interface{}, int64, error) {
	if _, err := s.loader.GetObjectType(objectType); err != nil {
		return nil, 0, fmt.Errorf("object type '%s' not found", objectType)
	}

	if len(filters) > 0 {
		instances, err := s.storage.SearchInstances(objectType, filters)
		if err != nil {
			return nil, 0, err
		}
		total := int64(len(instances))
		// 简单分页
		if offset >= len(instances) {
			return []map[string]interface{}{}, total, nil
		}
		end := offset + limit
		if end > len(instances) {
			end = len(instances)
		}
		return instances[offset:end], total, nil
	}

	return s.storage.ListInstances(objectType, offset, limit)
}

