package service

import (
	"mypalantir/internal/dsl"
)

// SchemaService Schema 服务
type SchemaService struct {
	loader *dsl.Loader
}

// NewSchemaService 创建 Schema 服务
func NewSchemaService(loader *dsl.Loader) *SchemaService {
	return &SchemaService{
		loader: loader,
	}
}

// GetObjectType 获取对象类型
func (s *SchemaService) GetObjectType(name string) (*dsl.ObjectType, error) {
	return s.loader.GetObjectType(name)
}

// ListObjectTypes 列出所有对象类型
func (s *SchemaService) ListObjectTypes() []dsl.ObjectType {
	return s.loader.ListObjectTypes()
}

// GetLinkType 获取关系类型
func (s *SchemaService) GetLinkType(name string) (*dsl.LinkType, error) {
	return s.loader.GetLinkType(name)
}

// ListLinkTypes 列出所有关系类型
func (s *SchemaService) ListLinkTypes() []dsl.LinkType {
	return s.loader.ListLinkTypes()
}

// GetObjectTypeProperties 获取对象类型的所有属性
func (s *SchemaService) GetObjectTypeProperties(objectTypeName string) ([]dsl.Property, error) {
	objectType, err := s.loader.GetObjectType(objectTypeName)
	if err != nil {
		return nil, err
	}
	return objectType.Properties, nil
}

// GetOutgoingLinks 获取对象的出边关系
func (s *SchemaService) GetOutgoingLinks(objectTypeName string) []dsl.LinkType {
	return s.loader.GetOutgoingLinks(objectTypeName)
}

// GetIncomingLinks 获取对象的入边关系
func (s *SchemaService) GetIncomingLinks(objectTypeName string) []dsl.LinkType {
	return s.loader.GetIncomingLinks(objectTypeName)
}

