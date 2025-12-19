package dsl

import (
	"fmt"
	"regexp"
	"strings"
)

// Validator DSL 验证器
type Validator struct {
	schema *OntologySchema
}

// NewValidator 创建新的验证器
func NewValidator(schema *OntologySchema) *Validator {
	return &Validator{
		schema: schema,
	}
}

// Validate 验证 Schema
func (v *Validator) Validate() error {
	if err := v.validateSyntax(); err != nil {
		return err
	}

	if err := v.validateSemantics(); err != nil {
		return err
	}

	if err := v.validateConstraints(); err != nil {
		return err
	}

	return nil
}

// validateSyntax 语法验证
func (v *Validator) validateSyntax() error {
	if v.schema.Version == "" {
		return fmt.Errorf("version is required")
	}

	// 验证对象类型
	objectTypeNames := make(map[string]bool)
	for i, ot := range v.schema.ObjectTypes {
		if ot.Name == "" {
			return fmt.Errorf("object_types[%d]: name is required", i)
		}

		// 检查名称格式（只允许字母、数字、下划线）
		if !isValidName(ot.Name) {
			return fmt.Errorf("object_types[%d]: invalid name format '%s'", i, ot.Name)
		}

		// 检查名称唯一性
		if objectTypeNames[ot.Name] {
			return fmt.Errorf("duplicate object type name: %s", ot.Name)
		}
		objectTypeNames[ot.Name] = true

		// 验证属性
		propertyNames := make(map[string]bool)
		for j, prop := range ot.Properties {
			if prop.Name == "" {
				return fmt.Errorf("object_types[%s].properties[%d]: name is required", ot.Name, j)
			}

			if !isValidName(prop.Name) {
				return fmt.Errorf("object_types[%s].properties[%d]: invalid name format '%s'", ot.Name, j, prop.Name)
			}

			// 检查属性名称唯一性
			if propertyNames[prop.Name] {
				return fmt.Errorf("duplicate property name '%s' in object type '%s'", prop.Name, ot.Name)
			}
			propertyNames[prop.Name] = true

			// 验证数据类型
			if !isValidDataType(prop.DataType) {
				return fmt.Errorf("object_types[%s].properties[%s]: invalid data_type '%s'", ot.Name, prop.Name, prop.DataType)
			}
		}
	}

	// 验证关系类型
	linkTypeNames := make(map[string]bool)
	for i, lt := range v.schema.LinkTypes {
		if lt.Name == "" {
			return fmt.Errorf("link_types[%d]: name is required", i)
		}

		if !isValidName(lt.Name) {
			return fmt.Errorf("link_types[%d]: invalid name format '%s'", i, lt.Name)
		}

		if linkTypeNames[lt.Name] {
			return fmt.Errorf("duplicate link type name: %s", lt.Name)
		}
		linkTypeNames[lt.Name] = true

		if lt.SourceType == "" {
			return fmt.Errorf("link_types[%s]: source_type is required", lt.Name)
		}

		if lt.TargetType == "" {
			return fmt.Errorf("link_types[%s]: target_type is required", lt.Name)
		}

		// 验证基数
		validCardinalities := map[string]bool{
			"one-to-one":   true,
			"one-to-many":  true,
			"many-to-one":  true,
			"many-to-many": true,
		}
		if !validCardinalities[lt.Cardinality] {
			return fmt.Errorf("link_types[%s]: invalid cardinality '%s'", lt.Name, lt.Cardinality)
		}

		// 验证方向
		if lt.Direction != "directed" && lt.Direction != "undirected" {
			return fmt.Errorf("link_types[%s]: invalid direction '%s'", lt.Name, lt.Direction)
		}
	}

	return nil
}

// validateSemantics 语义验证
func (v *Validator) validateSemantics() error {
	// 构建对象类型名称映射
	objectTypeMap := make(map[string]*ObjectType)
	for i := range v.schema.ObjectTypes {
		ot := &v.schema.ObjectTypes[i]
		objectTypeMap[ot.Name] = ot
	}

	// 验证基础类型引用
	for _, ot := range v.schema.ObjectTypes {
		if ot.BaseType != "" {
			if _, exists := objectTypeMap[ot.BaseType]; !exists {
				return fmt.Errorf("object_type '%s': base_type '%s' does not exist", ot.Name, ot.BaseType)
			}
		}
	}

	// 验证关系类型引用的对象类型
	for _, lt := range v.schema.LinkTypes {
		if _, exists := objectTypeMap[lt.SourceType]; !exists {
			return fmt.Errorf("link_type '%s': source_type '%s' does not exist", lt.Name, lt.SourceType)
		}

		if _, exists := objectTypeMap[lt.TargetType]; !exists {
			return fmt.Errorf("link_type '%s': target_type '%s' does not exist", lt.Name, lt.TargetType)
		}
	}

	return nil
}

// validateConstraints 约束验证
func (v *Validator) validateConstraints() error {
	for _, ot := range v.schema.ObjectTypes {
		for _, prop := range ot.Properties {
			if err := v.validatePropertyConstraints(ot.Name, prop); err != nil {
				return err
			}

			// 验证默认值类型
			if prop.DefaultValue != nil {
				if err := v.validateDefaultValue(ot.Name, prop); err != nil {
					return err
				}
			}
		}
	}

	return nil
}

// validatePropertyConstraints 验证属性约束
func (v *Validator) validatePropertyConstraints(objectTypeName string, prop Property) error {
	constraints := prop.Constraints
	if constraints == nil {
		return nil
	}

	switch prop.DataType {
	case "string":
		if minLen, ok := constraints["min_length"].(int); ok && minLen < 0 {
			return fmt.Errorf("object_type '%s'.property '%s': min_length must be >= 0", objectTypeName, prop.Name)
		}
		if maxLen, ok := constraints["max_length"].(int); ok && maxLen < 0 {
			return fmt.Errorf("object_type '%s'.property '%s': max_length must be >= 0", objectTypeName, prop.Name)
		}
		if minLen, ok1 := constraints["min_length"].(int); ok1 {
			if maxLen, ok2 := constraints["max_length"].(int); ok2 && maxLen < minLen {
				return fmt.Errorf("object_type '%s'.property '%s': max_length must be >= min_length", objectTypeName, prop.Name)
			}
		}
		if pattern, ok := constraints["pattern"].(string); ok {
			if _, err := regexp.Compile(pattern); err != nil {
				return fmt.Errorf("object_type '%s'.property '%s': invalid regex pattern: %w", objectTypeName, prop.Name, err)
			}
		}

	case "int", "float":
		if min, ok := constraints["min"].(int); ok {
			if max, ok2 := constraints["max"].(int); ok2 && max < min {
				return fmt.Errorf("object_type '%s'.property '%s': max must be >= min", objectTypeName, prop.Name)
			}
		}
		if min, ok := constraints["min"].(float64); ok {
			if max, ok2 := constraints["max"].(float64); ok2 && max < min {
				return fmt.Errorf("object_type '%s'.property '%s': max must be >= min", objectTypeName, prop.Name)
			}
		}
	}

	return nil
}

// validateDefaultValue 验证默认值类型
func (v *Validator) validateDefaultValue(objectTypeName string, prop Property) error {
	// 这里可以做更详细的类型检查
	// 暂时只做基本检查
	return nil
}

// isValidName 检查名称格式（字母、数字、下划线、中文字符）
func isValidName(name string) bool {
	if name == "" {
		return false
	}
	// 允许字母、数字、下划线、中文字符（Unicode 范围 \u4e00-\u9fa5）
	re := regexp.MustCompile(`^[\p{L}][\p{L}\p{N}_]*$`)
	return re.MatchString(name)
}

// isValidDataType 检查数据类型是否有效
func isValidDataType(dataType string) bool {
	validTypes := map[string]bool{
		"string":   true,
		"int":      true,
		"float":    true,
		"bool":     true,
		"date":     true,
		"datetime": true,
		"json":     true,
		"array":    true,
	}

	// 支持 array<type> 格式
	if strings.HasPrefix(dataType, "array<") && strings.HasSuffix(dataType, ">") {
		innerType := strings.TrimPrefix(strings.TrimSuffix(dataType, ">"), "array<")
		return isValidDataType(innerType)
	}

	return validTypes[dataType]
}
