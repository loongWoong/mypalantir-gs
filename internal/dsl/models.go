package dsl

import "time"

// OntologySchema 表示完整的 Ontology Schema
type OntologySchema struct {
	Version     string       `yaml:"version"`
	Namespace   string       `yaml:"namespace,omitempty"`
	ObjectTypes []ObjectType `yaml:"object_types"`
	LinkTypes   []LinkType   `yaml:"link_types"`
}

// ObjectType 表示对象类型定义
type ObjectType struct {
	Name        string     `yaml:"name"`
	Description string     `yaml:"description,omitempty"`
	BaseType    string     `yaml:"base_type,omitempty"`
	Properties  []Property `yaml:"properties"`
}

// Property 表示属性定义
type Property struct {
	Name         string                 `yaml:"name"`
	DataType     string                 `yaml:"data_type"`
	Required     bool                   `yaml:"required"`
	Description  string                 `yaml:"description,omitempty"`
	DefaultValue interface{}            `yaml:"default_value,omitempty"`
	Constraints  map[string]interface{} `yaml:"constraints,omitempty"`
}

// LinkType 表示关系类型定义
type LinkType struct {
	Name        string     `yaml:"name"`
	Description string     `yaml:"description,omitempty"`
	SourceType  string     `yaml:"source_type"`
	TargetType  string     `yaml:"target_type"`
	Cardinality string     `yaml:"cardinality"`
	Direction   string     `yaml:"direction"`
	Properties  []Property `yaml:"properties,omitempty"`
}

// ObjectInstance 表示对象实例
type ObjectInstance struct {
	ID        string                 `json:"id"`
	Data      map[string]interface{} `json:"-"`
	CreatedAt time.Time              `json:"created_at"`
	UpdatedAt time.Time              `json:"updated_at"`
}

// LinkInstance 表示关系实例
type LinkInstance struct {
	ID        string                 `json:"id"`
	SourceID  string                 `json:"source_id"`
	TargetID  string                 `json:"target_id"`
	Data      map[string]interface{} `json:"-"`
	CreatedAt time.Time              `json:"created_at"`
	UpdatedAt time.Time              `json:"updated_at"`
}
