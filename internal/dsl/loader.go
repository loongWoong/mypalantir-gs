package dsl

import (
	"fmt"
	"sync"
)

// Loader DSL 加载器
type Loader struct {
	parser    *Parser
	validator *Validator
	schema    *OntologySchema
	mu        sync.RWMutex
}

// NewLoader 创建新的加载器
func NewLoader(filePath string) *Loader {
	return &Loader{
		parser: NewParser(filePath),
	}
}

// Load 加载并验证 Schema
func (l *Loader) Load() error {
	l.mu.Lock()
	defer l.mu.Unlock()

	schema, err := l.parser.Parse()
	if err != nil {
		return fmt.Errorf("failed to parse schema: %w", err)
	}

	validator := NewValidator(schema)
	if err := validator.Validate(); err != nil {
		return fmt.Errorf("schema validation failed: %w", err)
	}

	l.schema = schema
	l.validator = validator

	return nil
}

// GetSchema 获取当前 Schema
func (l *Loader) GetSchema() *OntologySchema {
	l.mu.RLock()
	defer l.mu.RUnlock()
	return l.schema
}

// Reload 重新加载 Schema（用于热重载）
func (l *Loader) Reload() error {
	return l.Load()
}

// GetObjectType 根据名称获取对象类型
func (l *Loader) GetObjectType(name string) (*ObjectType, error) {
	schema := l.GetSchema()
	if schema == nil {
		return nil, fmt.Errorf("schema not loaded")
	}

	for i := range schema.ObjectTypes {
		if schema.ObjectTypes[i].Name == name {
			return &schema.ObjectTypes[i], nil
		}
	}

	return nil, fmt.Errorf("object type '%s' not found", name)
}

// ListObjectTypes 列出所有对象类型
func (l *Loader) ListObjectTypes() []ObjectType {
	schema := l.GetSchema()
	if schema == nil {
		return []ObjectType{}
	}

	result := make([]ObjectType, len(schema.ObjectTypes))
	copy(result, schema.ObjectTypes)
	return result
}

// GetLinkType 根据名称获取关系类型
func (l *Loader) GetLinkType(name string) (*LinkType, error) {
	schema := l.GetSchema()
	if schema == nil {
		return nil, fmt.Errorf("schema not loaded")
	}

	for i := range schema.LinkTypes {
		if schema.LinkTypes[i].Name == name {
			return &schema.LinkTypes[i], nil
		}
	}

	return nil, fmt.Errorf("link type '%s' not found", name)
}

// ListLinkTypes 列出所有关系类型
func (l *Loader) ListLinkTypes() []LinkType {
	schema := l.GetSchema()
	if schema == nil {
		return []LinkType{}
	}

	result := make([]LinkType, len(schema.LinkTypes))
	copy(result, schema.LinkTypes)
	return result
}

// GetOutgoingLinks 获取对象的出边关系
func (l *Loader) GetOutgoingLinks(objectTypeName string) []LinkType {
	schema := l.GetSchema()
	if schema == nil {
		return []LinkType{}
	}

	var result []LinkType
	for _, lt := range schema.LinkTypes {
		if lt.SourceType == objectTypeName {
			result = append(result, lt)
		}
	}
	return result
}

// GetIncomingLinks 获取对象的入边关系
func (l *Loader) GetIncomingLinks(objectTypeName string) []LinkType {
	schema := l.GetSchema()
	if schema == nil {
		return []LinkType{}
	}

	var result []LinkType
	for _, lt := range schema.LinkTypes {
		if lt.TargetType == objectTypeName {
			result = append(result, lt)
		}
	}
	return result
}
