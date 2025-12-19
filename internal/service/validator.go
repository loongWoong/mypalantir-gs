package service

import (
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"time"

	"mypalantir/internal/dsl"
)

// DataValidator 数据验证器
type DataValidator struct {
	loader *dsl.Loader
}

// NewDataValidator 创建数据验证器
func NewDataValidator(loader *dsl.Loader) *DataValidator {
	return &DataValidator{
		loader: loader,
	}
}

// ValidateInstanceData 验证实例数据
func (v *DataValidator) ValidateInstanceData(objectTypeName string, data map[string]interface{}) error {
	objectType, err := v.loader.GetObjectType(objectTypeName)
	if err != nil {
		return err
	}

	// 验证必填字段
	for _, prop := range objectType.Properties {
		if prop.Required {
			if _, exists := data[prop.Name]; !exists {
				// 检查是否有默认值
				if prop.DefaultValue != nil {
					data[prop.Name] = prop.DefaultValue
				} else {
					return fmt.Errorf("required field '%s' is missing", prop.Name)
				}
			}
		} else if _, exists := data[prop.Name]; !exists && prop.DefaultValue != nil {
			// 非必填但有默认值
			data[prop.Name] = prop.DefaultValue
		}
	}

	// 验证数据类型和约束
	for _, prop := range objectType.Properties {
		value, exists := data[prop.Name]
		if !exists {
			continue
		}

		if err := v.validatePropertyValue(prop, value); err != nil {
			return fmt.Errorf("field '%s': %w", prop.Name, err)
		}
	}

	return nil
}

// ValidateLinkData 验证关系数据
func (v *DataValidator) ValidateLinkData(linkTypeName string, sourceID, targetID string, properties map[string]interface{}) error {
	linkType, err := v.loader.GetLinkType(linkTypeName)
	if err != nil {
		return err
	}

	// 验证源对象和目标对象存在（这里简化处理，实际应该检查实例是否存在）
	if sourceID == "" {
		return fmt.Errorf("source_id is required")
	}
	if targetID == "" {
		return fmt.Errorf("target_id is required")
	}

	// 验证关系属性
	for _, prop := range linkType.Properties {
		value, exists := properties[prop.Name]
		if !exists {
			if prop.Required {
				return fmt.Errorf("required field '%s' is missing", prop.Name)
			}
			if prop.DefaultValue != nil {
				properties[prop.Name] = prop.DefaultValue
			}
			continue
		}

		if err := v.validatePropertyValue(prop, value); err != nil {
			return fmt.Errorf("field '%s': %w", prop.Name, err)
		}
	}

	return nil
}

// validatePropertyValue 验证属性值
func (v *DataValidator) validatePropertyValue(prop dsl.Property, value interface{}) error {
	// 类型验证
	if err := v.validateType(prop.DataType, value); err != nil {
		return err
	}

	// 约束验证
	if prop.Constraints != nil {
		if err := v.validateConstraints(prop, value); err != nil {
			return err
		}
	}

	return nil
}

// validateType 验证数据类型
func (v *DataValidator) validateType(dataType string, value interface{}) error {
	// 处理数组类型
	if strings.HasPrefix(dataType, "array<") {
		// 简化处理，实际应该验证数组元素类型
		_, ok := value.([]interface{})
		if !ok {
			return fmt.Errorf("expected array type")
		}
		return nil
	}

	switch dataType {
	case "string":
		_, ok := value.(string)
		if !ok {
			return fmt.Errorf("expected string type")
		}
	case "int":
		switch v := value.(type) {
		case int:
			return nil
		case float64:
			// JSON 数字可能是 float64
			if v == float64(int64(v)) {
				return nil
			}
		}
		return fmt.Errorf("expected int type")
	case "float":
		switch value.(type) {
		case float64, float32, int, int64:
			return nil
		}
		return fmt.Errorf("expected float type")
	case "bool":
		_, ok := value.(bool)
		if !ok {
			return fmt.Errorf("expected bool type")
		}
	case "date":
		str, ok := value.(string)
		if !ok {
			return fmt.Errorf("expected date string")
		}
		_, err := time.Parse("2006-01-02", str)
		if err != nil {
			return fmt.Errorf("invalid date format, expected YYYY-MM-DD")
		}
	case "datetime":
		str, ok := value.(string)
		if !ok {
			return fmt.Errorf("expected datetime string")
		}
		_, err := time.Parse(time.RFC3339, str)
		if err != nil {
			return fmt.Errorf("invalid datetime format, expected RFC3339")
		}
	case "json":
		// JSON 类型可以是任何类型
		return nil
	default:
		return fmt.Errorf("unknown data type: %s", dataType)
	}

	return nil
}

// validateConstraints 验证约束
func (v *DataValidator) validateConstraints(prop dsl.Property, value interface{}) error {
	constraints := prop.Constraints

	switch prop.DataType {
	case "string":
		str, ok := value.(string)
		if !ok {
			return fmt.Errorf("value is not a string")
		}

		if minLen, ok := constraints["min_length"].(int); ok {
			if len(str) < minLen {
				return fmt.Errorf("string length must be >= %d", minLen)
			}
		}
		if maxLen, ok := constraints["max_length"].(int); ok {
			if len(str) > maxLen {
				return fmt.Errorf("string length must be <= %d", maxLen)
			}
		}
		if pattern, ok := constraints["pattern"].(string); ok {
			matched, err := regexp.MatchString(pattern, str)
			if err != nil {
				return fmt.Errorf("invalid regex pattern: %w", err)
			}
			if !matched {
				return fmt.Errorf("string does not match pattern")
			}
		}

	case "int":
		var intValue int
		switch val := value.(type) {
		case int:
			intValue = val
		case float64:
			intValue = int(val)
		default:
			return fmt.Errorf("value is not an int")
		}

		if min, ok := constraints["min"].(int); ok {
			if intValue < min {
				return fmt.Errorf("value must be >= %d", min)
			}
		}
		if max, ok := constraints["max"].(int); ok {
			if intValue > max {
				return fmt.Errorf("value must be <= %d", max)
			}
		}

	case "float":
		var floatValue float64
		switch val := value.(type) {
		case float64:
			floatValue = val
		case float32:
			floatValue = float64(val)
		case int:
			floatValue = float64(val)
		default:
			return fmt.Errorf("value is not a float")
		}

		if min, ok := constraints["min"].(float64); ok {
			if floatValue < min {
				return fmt.Errorf("value must be >= %f", min)
			}
		}
		if min, ok := constraints["min"].(int); ok {
			if floatValue < float64(min) {
				return fmt.Errorf("value must be >= %d", min)
			}
		}
		if max, ok := constraints["max"].(float64); ok {
			if floatValue > max {
				return fmt.Errorf("value must be <= %f", max)
			}
		}
		if max, ok := constraints["max"].(int); ok {
			if floatValue > float64(max) {
				return fmt.Errorf("value must be <= %d", max)
			}
		}
	}

	// 枚举值验证
	if enum, ok := constraints["enum"].([]interface{}); ok {
		found := false
		for _, e := range enum {
			if e == value {
				found = true
				break
			}
		}
		if !found {
			enumStr := make([]string, len(enum))
			for i, e := range enum {
				enumStr[i] = fmt.Sprintf("%v", e)
			}
			return fmt.Errorf("value must be one of: %s", strings.Join(enumStr, ", "))
		}
	}

	return nil
}

// ConvertValue 转换值类型（用于从 JSON 读取后的类型转换）
func (v *DataValidator) ConvertValue(dataType string, value interface{}) (interface{}, error) {
	switch dataType {
	case "int":
		switch val := value.(type) {
		case float64:
			return int(val), nil
		case int:
			return val, nil
		case string:
			return strconv.Atoi(val)
		}
		return value, nil
	case "float":
		switch val := value.(type) {
		case float64:
			return val, nil
		case int:
			return float64(val), nil
		case string:
			return strconv.ParseFloat(val, 64)
		}
		return value, nil
	case "bool":
		switch val := value.(type) {
		case bool:
			return val, nil
		case string:
			return strconv.ParseBool(val)
		}
		return value, nil
	}
	return value, nil
}

