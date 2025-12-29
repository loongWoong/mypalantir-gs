package com.mypalantir.service;

import com.mypalantir.meta.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class DataValidator {
    private final Loader loader;

    public DataValidator(Loader loader) {
        this.loader = loader;
    }

    public void validateInstanceData(String objectTypeName, Map<String, Object> data) throws ValidationException, Loader.NotFoundException {
        ObjectType objectType = loader.getObjectType(objectTypeName);

        // 验证必填字段
        if (objectType.getProperties() != null) {
            for (Property prop : objectType.getProperties()) {
                if (prop.isRequired()) {
                    if (!data.containsKey(prop.getName())) {
                        if (prop.getDefaultValue() != null) {
                            data.put(prop.getName(), prop.getDefaultValue());
                        } else {
                            throw new ValidationException("required field '" + prop.getName() + "' is missing");
                        }
                    }
                } else if (!data.containsKey(prop.getName()) && prop.getDefaultValue() != null) {
                    data.put(prop.getName(), prop.getDefaultValue());
                }
            }
        }

        // 验证数据类型和约束
        if (objectType.getProperties() != null) {
            for (Property prop : objectType.getProperties()) {
                if (data.containsKey(prop.getName())) {
                    Object value = data.get(prop.getName());
                    validatePropertyValue(prop, value);
                }
            }
        }
    }

    public void validateLinkData(String linkTypeName, String sourceID, String targetID, Map<String, Object> properties) throws ValidationException, Loader.NotFoundException {
        LinkType linkType = loader.getLinkType(linkTypeName);

        if (sourceID == null || sourceID.isEmpty()) {
            throw new ValidationException("source_id is required");
        }
        if (targetID == null || targetID.isEmpty()) {
            throw new ValidationException("target_id is required");
        }

        // 验证关系属性
        if (linkType.getProperties() != null) {
            for (Property prop : linkType.getProperties()) {
                Object value = properties != null ? properties.get(prop.getName()) : null;
                if (value == null) {
                    if (prop.isRequired()) {
                        throw new ValidationException("required field '" + prop.getName() + "' is missing");
                    }
                    if (prop.getDefaultValue() != null) {
                        if (properties == null) {
                            properties = new java.util.HashMap<>();
                        }
                        properties.put(prop.getName(), prop.getDefaultValue());
                    }
                    continue;
                }

                validatePropertyValue(prop, value);
            }
        }
    }

    private void validatePropertyValue(Property prop, Object value) throws ValidationException {
        // 类型验证
        validateType(prop.getDataType(), value);

        // 约束验证
        if (prop.getConstraints() != null) {
            validateConstraints(prop, value);
        }
    }

    private void validateType(String dataType, Object value) throws ValidationException {
        if (dataType == null) {
            return;
        }

        // 处理数组类型
        if (dataType.startsWith("array<")) {
            if (!(value instanceof List)) {
                throw new ValidationException("expected array type");
            }
            return;
        }

        switch (dataType) {
            case "string":
                if (!(value instanceof String)) {
                    throw new ValidationException("expected string type");
                }
                break;
            case "int":
                if (!(value instanceof Integer) && !(value instanceof Long) && !(value instanceof Number)) {
                    throw new ValidationException("expected int type");
                }
                break;
            case "float":
                if (!(value instanceof Float) && !(value instanceof Double) && !(value instanceof Number)) {
                    throw new ValidationException("expected float type");
                }
                break;
            case "bool":
                if (!(value instanceof Boolean)) {
                    throw new ValidationException("expected bool type");
                }
                break;
            case "date":
                if (!(value instanceof String)) {
                    throw new ValidationException("expected date string");
                }
                try {
                    LocalDate.parse((String) value, DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (DateTimeParseException e) {
                    throw new ValidationException("invalid date format, expected YYYY-MM-DD");
                }
                break;
            case "datetime":
                if (!(value instanceof String)) {
                    throw new ValidationException("expected datetime string");
                }
                try {
                    LocalDateTime.parse((String) value, DateTimeFormatter.ISO_DATE_TIME);
                } catch (DateTimeParseException e) {
                    throw new ValidationException("invalid datetime format, expected ISO-8601");
                }
                break;
            case "json":
                // JSON 类型可以是任何类型
                break;
            default:
                throw new ValidationException("unknown data type: " + dataType);
        }
    }

    private void validateConstraints(Property prop, Object value) throws ValidationException {
        Map<String, Object> constraints = prop.getConstraints();
        String dataType = prop.getDataType();

        if ("string".equals(dataType) && value instanceof String) {
            String str = (String) value;

            if (constraints.containsKey("min_length")) {
                Object minLenObj = constraints.get("min_length");
                if (minLenObj instanceof Number) {
                    int minLen = ((Number) minLenObj).intValue();
                    if (str.length() < minLen) {
                        throw new ValidationException("string length must be >= " + minLen);
                    }
                }
            }
            if (constraints.containsKey("max_length")) {
                Object maxLenObj = constraints.get("max_length");
                if (maxLenObj instanceof Number) {
                    int maxLen = ((Number) maxLenObj).intValue();
                    if (str.length() > maxLen) {
                        throw new ValidationException("string length must be <= " + maxLen);
                    }
                }
            }
            if (constraints.containsKey("pattern")) {
                Object patternObj = constraints.get("pattern");
                if (patternObj instanceof String) {
                    Pattern pattern = Pattern.compile((String) patternObj);
                    if (!pattern.matcher(str).matches()) {
                        throw new ValidationException("string does not match pattern");
                    }
                }
            }
        } else if ("int".equals(dataType)) {
            int intValue;
            if (value instanceof Number) {
                intValue = ((Number) value).intValue();
            } else {
                throw new ValidationException("value is not an int");
            }

            if (constraints.containsKey("min")) {
                Object minObj = constraints.get("min");
                if (minObj instanceof Number) {
                    int min = ((Number) minObj).intValue();
                    if (intValue < min) {
                        throw new ValidationException("value must be >= " + min);
                    }
                }
            }
            if (constraints.containsKey("max")) {
                Object maxObj = constraints.get("max");
                if (maxObj instanceof Number) {
                    int max = ((Number) maxObj).intValue();
                    if (intValue > max) {
                        throw new ValidationException("value must be <= " + max);
                    }
                }
            }
        } else if ("float".equals(dataType)) {
            double floatValue;
            if (value instanceof Number) {
                floatValue = ((Number) value).doubleValue();
            } else {
                throw new ValidationException("value is not a float");
            }

            if (constraints.containsKey("min")) {
                Object minObj = constraints.get("min");
                if (minObj instanceof Number) {
                    double min = ((Number) minObj).doubleValue();
                    if (floatValue < min) {
                        throw new ValidationException("value must be >= " + min);
                    }
                }
            }
            if (constraints.containsKey("max")) {
                Object maxObj = constraints.get("max");
                if (maxObj instanceof Number) {
                    double max = ((Number) maxObj).doubleValue();
                    if (floatValue > max) {
                        throw new ValidationException("value must be <= " + max);
                    }
                }
            }
        }

        // 枚举值验证
        if (constraints.containsKey("enum")) {
            Object enumObj = constraints.get("enum");
            if (enumObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> enumList = (List<Object>) enumObj;
                if (!enumList.contains(value)) {
                    throw new ValidationException("value must be one of: " + enumList);
                }
            }
        }
    }

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}

