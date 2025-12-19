package storage

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
)

// InstanceStorage 实例存储
type InstanceStorage struct {
	pathManager *PathManager
	mu          sync.RWMutex
}

// NewInstanceStorage 创建实例存储
func NewInstanceStorage(pathManager *PathManager) *InstanceStorage {
	return &InstanceStorage{
		pathManager: pathManager,
	}
}

// CreateInstance 创建实例
func (s *InstanceStorage) CreateInstance(objectType string, data map[string]interface{}) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	id := uuid.New().String()
	now := time.Now()

	instance := map[string]interface{}{
		"id":         id,
		"created_at": now.Format(time.RFC3339),
		"updated_at": now.Format(time.RFC3339),
	}

	// 合并数据
	for k, v := range data {
		instance[k] = v
	}

	// 确保目录存在
	dir := s.pathManager.GetInstanceDir(objectType)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return "", fmt.Errorf("failed to create directory: %w", err)
	}

	// 写入文件
	filePath := s.pathManager.GetInstancePath(objectType, id)
	file, err := os.Create(filePath)
	if err != nil {
		return "", fmt.Errorf("failed to create file: %w", err)
	}
	defer file.Close()

	encoder := json.NewEncoder(file)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(instance); err != nil {
		return "", fmt.Errorf("failed to encode JSON: %w", err)
	}

	return id, nil
}

// GetInstance 获取实例
func (s *InstanceStorage) GetInstance(objectType string, id string) (map[string]interface{}, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	filePath := s.pathManager.GetInstancePath(objectType, id)
	data, err := os.ReadFile(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("instance not found")
		}
		return nil, fmt.Errorf("failed to read file: %w", err)
	}

	var instance map[string]interface{}
	if err := json.Unmarshal(data, &instance); err != nil {
		return nil, fmt.Errorf("failed to parse JSON: %w", err)
	}

	return instance, nil
}

// UpdateInstance 更新实例
func (s *InstanceStorage) UpdateInstance(objectType string, id string, data map[string]interface{}) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	filePath := s.pathManager.GetInstancePath(objectType, id)

	// 读取现有数据
	existing, err := s.GetInstance(objectType, id)
	if err != nil {
		return err
	}

	// 更新数据
	for k, v := range data {
		existing[k] = v
	}
	existing["updated_at"] = time.Now().Format(time.RFC3339)

	// 写入文件
	file, err := os.Create(filePath)
	if err != nil {
		return fmt.Errorf("failed to create file: %w", err)
	}
	defer file.Close()

	encoder := json.NewEncoder(file)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(existing); err != nil {
		return fmt.Errorf("failed to encode JSON: %w", err)
	}

	return nil
}

// DeleteInstance 删除实例
func (s *InstanceStorage) DeleteInstance(objectType string, id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	filePath := s.pathManager.GetInstancePath(objectType, id)
	if err := os.Remove(filePath); err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("instance not found")
		}
		return fmt.Errorf("failed to delete file: %w", err)
	}

	return nil
}

// ListInstances 列出所有实例
func (s *InstanceStorage) ListInstances(objectType string, offset, limit int) ([]map[string]interface{}, int64, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	dir := s.pathManager.GetInstanceDir(objectType)
	files, err := os.ReadDir(dir)
	if err != nil {
		if os.IsNotExist(err) {
			return []map[string]interface{}{}, 0, nil
		}
		return nil, 0, fmt.Errorf("failed to read directory: %w", err)
	}

	var instances []map[string]interface{}
	for _, file := range files {
		if file.IsDir() || !strings.HasSuffix(file.Name(), ".json") {
			continue
		}

		filePath := filepath.Join(dir, file.Name())
		data, err := os.ReadFile(filePath)
		if err != nil {
			continue
		}

		var instance map[string]interface{}
		if err := json.Unmarshal(data, &instance); err != nil {
			continue
		}

		instances = append(instances, instance)
	}

	total := int64(len(instances))

	// 分页
	if offset >= len(instances) {
		return []map[string]interface{}{}, total, nil
	}

	end := offset + limit
	if end > len(instances) {
		end = len(instances)
	}

	return instances[offset:end], total, nil
}

// SearchInstances 搜索实例（简单实现，基于内存过滤）
func (s *InstanceStorage) SearchInstances(objectType string, filters map[string]interface{}) ([]map[string]interface{}, error) {
	instances, _, err := s.ListInstances(objectType, 0, 10000) // 获取所有实例
	if err != nil {
		return nil, err
	}

	if len(filters) == 0 {
		return instances, nil
	}

	var results []map[string]interface{}
	for _, instance := range instances {
		match := true
		for key, value := range filters {
			if instance[key] != value {
				match = false
				break
			}
		}
		if match {
			results = append(results, instance)
		}
	}

	return results, nil
}

