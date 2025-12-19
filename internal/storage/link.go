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

// LinkStorage 关系存储
type LinkStorage struct {
	pathManager *PathManager
	mu          sync.RWMutex
}

// NewLinkStorage 创建关系存储
func NewLinkStorage(pathManager *PathManager) *LinkStorage {
	return &LinkStorage{
		pathManager: pathManager,
	}
}

// CreateLink 创建关系
func (s *LinkStorage) CreateLink(linkType string, sourceID, targetID string, properties map[string]interface{}) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	id := uuid.New().String()
	now := time.Now()

	link := map[string]interface{}{
		"id":         id,
		"source_id":  sourceID,
		"target_id":  targetID,
		"created_at": now.Format(time.RFC3339),
		"updated_at": now.Format(time.RFC3339),
	}

	// 合并属性
	for k, v := range properties {
		link[k] = v
	}

	// 确保目录存在
	dir := s.pathManager.GetLinkDir(linkType)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return "", fmt.Errorf("failed to create directory: %w", err)
	}

	// 写入文件
	filePath := s.pathManager.GetLinkPath(linkType, id)
	file, err := os.Create(filePath)
	if err != nil {
		return "", fmt.Errorf("failed to create file: %w", err)
	}
	defer file.Close()

	encoder := json.NewEncoder(file)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(link); err != nil {
		return "", fmt.Errorf("failed to encode JSON: %w", err)
	}

	return id, nil
}

// GetLink 获取关系
func (s *LinkStorage) GetLink(linkType string, id string) (map[string]interface{}, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	filePath := s.pathManager.GetLinkPath(linkType, id)
	data, err := os.ReadFile(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("link not found")
		}
		return nil, fmt.Errorf("failed to read file: %w", err)
	}

	var link map[string]interface{}
	if err := json.Unmarshal(data, &link); err != nil {
		return nil, fmt.Errorf("failed to parse JSON: %w", err)
	}

	return link, nil
}

// UpdateLink 更新关系
func (s *LinkStorage) UpdateLink(linkType string, id string, properties map[string]interface{}) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	filePath := s.pathManager.GetLinkPath(linkType, id)

	// 读取现有数据
	existing, err := s.GetLink(linkType, id)
	if err != nil {
		return err
	}

	// 更新属性（不更新 source_id 和 target_id）
	for k, v := range properties {
		if k != "source_id" && k != "target_id" && k != "id" {
			existing[k] = v
		}
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

// DeleteLink 删除关系
func (s *LinkStorage) DeleteLink(linkType string, id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	filePath := s.pathManager.GetLinkPath(linkType, id)
	if err := os.Remove(filePath); err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("link not found")
		}
		return fmt.Errorf("failed to delete file: %w", err)
	}

	return nil
}

// GetLinksBySource 根据源对象查询关系
func (s *LinkStorage) GetLinksBySource(linkType string, sourceID string) ([]map[string]interface{}, error) {
	return s.getLinksByField(linkType, "source_id", sourceID)
}

// GetLinksByTarget 根据目标对象查询关系
func (s *LinkStorage) GetLinksByTarget(linkType string, targetID string) ([]map[string]interface{}, error) {
	return s.getLinksByField(linkType, "target_id", targetID)
}

// getLinksByField 根据字段值查询关系
func (s *LinkStorage) getLinksByField(linkType string, field string, value string) ([]map[string]interface{}, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	dir := s.pathManager.GetLinkDir(linkType)
	files, err := os.ReadDir(dir)
	if err != nil {
		if os.IsNotExist(err) {
			return []map[string]interface{}{}, nil
		}
		return nil, fmt.Errorf("failed to read directory: %w", err)
	}

	var results []map[string]interface{}
	for _, file := range files {
		if file.IsDir() || !strings.HasSuffix(file.Name(), ".json") {
			continue
		}

		filePath := filepath.Join(dir, file.Name())
		data, err := os.ReadFile(filePath)
		if err != nil {
			continue
		}

		var link map[string]interface{}
		if err := json.Unmarshal(data, &link); err != nil {
			continue
		}

		if link[field] == value {
			results = append(results, link)
		}
	}

	return results, nil
}

// ListLinks 列出所有关系
func (s *LinkStorage) ListLinks(linkType string, offset, limit int) ([]map[string]interface{}, int64, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	dir := s.pathManager.GetLinkDir(linkType)
	files, err := os.ReadDir(dir)
	if err != nil {
		if os.IsNotExist(err) {
			return []map[string]interface{}{}, 0, nil
		}
		return nil, 0, fmt.Errorf("failed to read directory: %w", err)
	}

	var links []map[string]interface{}
	for _, file := range files {
		if file.IsDir() || !strings.HasSuffix(file.Name(), ".json") {
			continue
		}

		filePath := filepath.Join(dir, file.Name())
		data, err := os.ReadFile(filePath)
		if err != nil {
			continue
		}

		var link map[string]interface{}
		if err := json.Unmarshal(data, &link); err != nil {
			continue
		}

		links = append(links, link)
	}

	total := int64(len(links))

	// 分页
	if offset >= len(links) {
		return []map[string]interface{}{}, total, nil
	}

	end := offset + limit
	if end > len(links) {
		end = len(links)
	}

	return links[offset:end], total, nil
}

