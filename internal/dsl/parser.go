package dsl

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

// Parser DSL 解析器
type Parser struct {
	filePath string
}

// NewParser 创建新的解析器
func NewParser(filePath string) *Parser {
	return &Parser{
		filePath: filePath,
	}
}

// Parse 解析 YAML 文件
func (p *Parser) Parse() (*OntologySchema, error) {
	data, err := os.ReadFile(p.filePath)
	if err != nil {
		return nil, fmt.Errorf("failed to read DSL file: %w", err)
	}

	var schema OntologySchema
	if err := yaml.Unmarshal(data, &schema); err != nil {
		return nil, fmt.Errorf("failed to parse YAML: %w", err)
	}

	return &schema, nil
}
