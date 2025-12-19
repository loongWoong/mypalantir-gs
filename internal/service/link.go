package service

import (
	"fmt"

	"mypalantir/internal/dsl"
	"mypalantir/internal/storage"
)

// LinkService 关系服务
type LinkService struct {
	storage   *storage.LinkStorage
	instanceStorage *storage.InstanceStorage
	loader    *dsl.Loader
	validator *DataValidator
}

// NewLinkService 创建关系服务
func NewLinkService(linkStorage *storage.LinkStorage, instanceStorage *storage.InstanceStorage, loader *dsl.Loader, validator *DataValidator) *LinkService {
	return &LinkService{
		storage:         linkStorage,
		instanceStorage: instanceStorage,
		loader:          loader,
		validator:       validator,
	}
}

// CreateLink 创建关系
func (s *LinkService) CreateLink(linkType string, sourceID, targetID string, properties map[string]interface{}) (string, error) {
	// 验证关系类型存在
	linkTypeDef, err := s.loader.GetLinkType(linkType)
	if err != nil {
		return "", fmt.Errorf("link type '%s' not found", linkType)
	}

	// 验证源对象和目标对象存在
	// TODO: 实际应该检查实例是否存在
	// 这里简化处理

	// 验证关系数据
	if err := s.validator.ValidateLinkData(linkType, sourceID, targetID, properties); err != nil {
		return "", err
	}

	// 创建关系
	id, err := s.storage.CreateLink(linkType, sourceID, targetID, properties)
	if err != nil {
		return "", fmt.Errorf("failed to create link: %w", err)
	}

	// 验证基数约束（简化处理）
	_ = linkTypeDef

	return id, nil
}

// GetLink 获取关系
func (s *LinkService) GetLink(linkType string, id string) (map[string]interface{}, error) {
	return s.storage.GetLink(linkType, id)
}

// UpdateLink 更新关系
func (s *LinkService) UpdateLink(linkType string, id string, properties map[string]interface{}) error {
	// 验证关系类型存在
	if _, err := s.loader.GetLinkType(linkType); err != nil {
		return fmt.Errorf("link type '%s' not found", linkType)
	}

	// 验证关系属性
	link, err := s.storage.GetLink(linkType, id)
	if err != nil {
		return err
	}

	sourceID, _ := link["source_id"].(string)
	targetID, _ := link["target_id"].(string)

	if err := s.validator.ValidateLinkData(linkType, sourceID, targetID, properties); err != nil {
		return err
	}

	return s.storage.UpdateLink(linkType, id, properties)
}

// DeleteLink 删除关系
func (s *LinkService) DeleteLink(linkType string, id string) error {
	return s.storage.DeleteLink(linkType, id)
}

// GetLinksBySource 根据源对象查询关系
func (s *LinkService) GetLinksBySource(linkType string, sourceID string) ([]map[string]interface{}, error) {
	return s.storage.GetLinksBySource(linkType, sourceID)
}

// GetLinksByTarget 根据目标对象查询关系
func (s *LinkService) GetLinksByTarget(linkType string, targetID string) ([]map[string]interface{}, error) {
	return s.storage.GetLinksByTarget(linkType, targetID)
}

// GetConnectedInstances 查询关联的实例
func (s *LinkService) GetConnectedInstances(objectType, linkType string, instanceID string, direction string) ([]map[string]interface{}, error) {
	linkTypeDef, err := s.loader.GetLinkType(linkType)
	if err != nil {
		return nil, fmt.Errorf("link type '%s' not found", linkType)
	}

	var links []map[string]interface{}
	var err2 error

	if direction == "outgoing" || direction == "" {
		// 查询出边
		if linkTypeDef.SourceType == objectType {
			links, err2 = s.storage.GetLinksBySource(linkType, instanceID)
		}
	} else if direction == "incoming" {
		// 查询入边
		if linkTypeDef.TargetType == objectType {
			links, err2 = s.storage.GetLinksByTarget(linkType, instanceID)
		}
	}

	if err2 != nil {
		return nil, err2
	}

	// 获取关联的实例
	var instances []map[string]interface{}
	var targetObjectType string

	if direction == "outgoing" || direction == "" {
		targetObjectType = linkTypeDef.TargetType
		for _, link := range links {
			if targetID, ok := link["target_id"].(string); ok {
				instance, err := s.instanceStorage.GetInstance(targetObjectType, targetID)
				if err == nil {
					instances = append(instances, instance)
				}
			}
		}
	} else if direction == "incoming" {
		targetObjectType = linkTypeDef.SourceType
		for _, link := range links {
			if sourceID, ok := link["source_id"].(string); ok {
				instance, err := s.instanceStorage.GetInstance(targetObjectType, sourceID)
				if err == nil {
					instances = append(instances, instance)
				}
			}
		}
	}

	return instances, nil
}

// ListLinks 列出所有关系
func (s *LinkService) ListLinks(linkType string, offset, limit int) ([]map[string]interface{}, int64, error) {
	if _, err := s.loader.GetLinkType(linkType); err != nil {
		return nil, 0, fmt.Errorf("link type '%s' not found", linkType)
	}

	return s.storage.ListLinks(linkType, offset, limit)
}

