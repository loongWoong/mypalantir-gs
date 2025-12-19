package storage

import (
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"path/filepath"
	"regexp"
	"strings"
)

// PathManager 路径管理器
type PathManager struct {
	dataRoot string
	namespace string
}

// NewPathManager 创建路径管理器
func NewPathManager(dataRoot, namespace string) *PathManager {
	return &PathManager{
		dataRoot:  dataRoot,
		namespace: normalizeNamespace(namespace),
	}
}

// GetInstancePath 获取实例文件路径
func (pm *PathManager) GetInstancePath(objectType string, id string) string {
	objectType = normalizeName(objectType)
	return filepath.Join(pm.dataRoot, pm.namespace, objectType, fmt.Sprintf("%s.json", id))
}

// GetInstanceDir 获取实例目录
func (pm *PathManager) GetInstanceDir(objectType string) string {
	objectType = normalizeName(objectType)
	return filepath.Join(pm.dataRoot, pm.namespace, objectType)
}

// GetLinkPath 获取关系文件路径
func (pm *PathManager) GetLinkPath(linkType string, id string) string {
	linkType = normalizeName(linkType)
	return filepath.Join(pm.dataRoot, pm.namespace, "links", linkType, fmt.Sprintf("%s.json", id))
}

// GetLinkDir 获取关系目录
func (pm *PathManager) GetLinkDir(linkType string) string {
	linkType = normalizeName(linkType)
	return filepath.Join(pm.dataRoot, pm.namespace, "links", linkType)
}

// normalizeNamespace 规范化命名空间
func normalizeNamespace(namespace string) string {
	if namespace == "" {
		return "default"
	}
	// 将命名空间中的点、冒号等替换为下划线
	re := regexp.MustCompile(`[^a-zA-Z0-9_]`)
	return re.ReplaceAllString(strings.ToLower(namespace), "_")
}

// normalizeName 规范化名称（用于文件名）
// 对于包含非ASCII字符的名称，使用MD5哈希；对于纯ASCII名称，转换为小写并替换特殊字符
func normalizeName(name string) string {
	// 检查是否包含非ASCII字符
	hasNonASCII := false
	for _, r := range name {
		if r > 127 {
			hasNonASCII = true
			break
		}
	}
	
	if hasNonASCII {
		// 对于包含中文等非ASCII字符的名称，使用MD5哈希
		hash := md5.Sum([]byte(name))
		return hex.EncodeToString(hash[:])
	}
	
	// 对于纯ASCII名称，转换为小写，特殊字符替换为下划线
	re := regexp.MustCompile(`[^a-zA-Z0-9_]`)
	return re.ReplaceAllString(strings.ToLower(name), "_")
}

