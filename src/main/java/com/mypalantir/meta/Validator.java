package com.mypalantir.meta;

import java.util.*;
import java.util.regex.Pattern;

public class Validator {
    private final OntologySchema schema;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L}\\p{N}_]*$");
    private static final Set<String> VALID_DATA_TYPES = Set.of(
        "string", "int", "long", "float", "bool", "date", "datetime", "json", "array"
    );
    private static final Set<String> VALID_CARDINALITIES = Set.of(
        "one-to-one", "one-to-many", "many-to-one", "many-to-many"
    );

    public Validator(OntologySchema schema) {
        this.schema = schema;
    }

    public void validate() throws ValidationException {
        validateSyntax();
        validateSemantics();
        validateConstraints();
    }

    private void validateSyntax() throws ValidationException {
        if (schema.getVersion() == null || schema.getVersion().isEmpty()) {
            throw new ValidationException("version is required");
        }

        // 验证对象类型
        Set<String> objectTypeNames = new HashSet<>();
        List<ObjectType> objectTypes = schema.getObjectTypes();
        if (objectTypes != null) {
            for (int i = 0; i < objectTypes.size(); i++) {
                ObjectType ot = objectTypes.get(i);
                if (ot.getName() == null || ot.getName().isEmpty()) {
                    throw new ValidationException("object_types[" + i + "]: name is required");
                }

                if (!isValidName(ot.getName())) {
                    throw new ValidationException("object_types[" + i + "]: invalid name format '" + ot.getName() + "'");
                }

                if (objectTypeNames.contains(ot.getName())) {
                    throw new ValidationException("duplicate object type name: " + ot.getName());
                }
                objectTypeNames.add(ot.getName());

                // 验证属性
                Set<String> propertyNames = new HashSet<>();
                List<Property> properties = ot.getProperties();
                if (properties != null) {
                    for (int j = 0; j < properties.size(); j++) {
                        Property prop = properties.get(j);
                        if (prop.getName() == null || prop.getName().isEmpty()) {
                            throw new ValidationException("object_types[" + ot.getName() + "].properties[" + j + "]: name is required");
                        }

                        if (!isValidName(prop.getName())) {
                            throw new ValidationException("object_types[" + ot.getName() + "].properties[" + j + "]: invalid name format '" + prop.getName() + "'");
                        }

                        if (propertyNames.contains(prop.getName())) {
                            throw new ValidationException("duplicate property name '" + prop.getName() + "' in object type '" + ot.getName() + "'");
                        }
                        propertyNames.add(prop.getName());

                        if (!isValidDataType(prop.getDataType())) {
                            throw new ValidationException("object_types[" + ot.getName() + "].properties[" + prop.getName() + "]: invalid data_type '" + prop.getDataType() + "'");
                        }
                    }
                }
            }
        }

        // 验证关系类型
        Set<String> linkTypeNames = new HashSet<>();
        List<LinkType> linkTypes = schema.getLinkTypes();
        if (linkTypes != null) {
            for (int i = 0; i < linkTypes.size(); i++) {
                LinkType lt = linkTypes.get(i);
                if (lt.getName() == null || lt.getName().isEmpty()) {
                    throw new ValidationException("link_types[" + i + "]: name is required");
                }

                if (!isValidName(lt.getName())) {
                    throw new ValidationException("link_types[" + i + "]: invalid name format '" + lt.getName() + "'");
                }

                if (linkTypeNames.contains(lt.getName())) {
                    throw new ValidationException("duplicate link type name: " + lt.getName());
                }
                linkTypeNames.add(lt.getName());

                if (lt.getSourceType() == null || lt.getSourceType().isEmpty()) {
                    throw new ValidationException("link_types[" + lt.getName() + "]: source_type is required");
                }

                if (lt.getTargetType() == null || lt.getTargetType().isEmpty()) {
                    throw new ValidationException("link_types[" + lt.getName() + "]: target_type is required");
                }

                if (!VALID_CARDINALITIES.contains(lt.getCardinality())) {
                    throw new ValidationException("link_types[" + lt.getName() + "]: invalid cardinality '" + lt.getCardinality() + "'");
                }

                if (!"directed".equals(lt.getDirection()) && !"undirected".equals(lt.getDirection())) {
                    throw new ValidationException("link_types[" + lt.getName() + "]: invalid direction '" + lt.getDirection() + "'");
                }
            }
        }
    }

    private void validateSemantics() throws ValidationException {
        // 构建对象类型名称映射
        Map<String, ObjectType> objectTypeMap = new HashMap<>();
        if (schema.getObjectTypes() != null) {
            for (ObjectType ot : schema.getObjectTypes()) {
                objectTypeMap.put(ot.getName(), ot);
            }
        }

        // 验证基础类型引用
        if (schema.getObjectTypes() != null) {
            for (ObjectType ot : schema.getObjectTypes()) {
                if (ot.getBaseType() != null && !ot.getBaseType().isEmpty()) {
                    if (!objectTypeMap.containsKey(ot.getBaseType())) {
                        throw new ValidationException("object_type '" + ot.getName() + "': base_type '" + ot.getBaseType() + "' does not exist");
                    }
                }
            }
        }

        // 验证关系类型引用的对象类型
        if (schema.getLinkTypes() != null) {
            for (LinkType lt : schema.getLinkTypes()) {
                if (!objectTypeMap.containsKey(lt.getSourceType())) {
                    throw new ValidationException("link_type '" + lt.getName() + "': source_type '" + lt.getSourceType() + "' does not exist");
                }

                if (!objectTypeMap.containsKey(lt.getTargetType())) {
                    throw new ValidationException("link_type '" + lt.getName() + "': target_type '" + lt.getTargetType() + "' does not exist");
                }
            }
        }
    }

    private void validateConstraints() throws ValidationException {
        if (schema.getObjectTypes() != null) {
            for (ObjectType ot : schema.getObjectTypes()) {
                if (ot.getProperties() != null) {
                    for (Property prop : ot.getProperties()) {
                        validatePropertyConstraints(ot.getName(), prop);
                    }
                }
            }
        }
    }

    private void validatePropertyConstraints(String objectTypeName, Property prop) throws ValidationException {
        Map<String, Object> constraints = prop.getConstraints();
        if (constraints == null) {
            return;
        }

        String dataType = prop.getDataType();
        if ("string".equals(dataType)) {
            if (constraints.containsKey("min_length")) {
                Object minLenObj = constraints.get("min_length");
                if (minLenObj instanceof Number) {
                    int minLen = ((Number) minLenObj).intValue();
                    if (minLen < 0) {
                        throw new ValidationException("object_type '" + objectTypeName + "'.property '" + prop.getName() + "': min_length must be >= 0");
                    }
                }
            }
            if (constraints.containsKey("max_length")) {
                Object maxLenObj = constraints.get("max_length");
                if (maxLenObj instanceof Number) {
                    int maxLen = ((Number) maxLenObj).intValue();
                    if (maxLen < 0) {
                        throw new ValidationException("object_type '" + objectTypeName + "'.property '" + prop.getName() + "': max_length must be >= 0");
                    }
                }
            }
            if (constraints.containsKey("min_length") && constraints.containsKey("max_length")) {
                Object minLenObj = constraints.get("min_length");
                Object maxLenObj = constraints.get("max_length");
                if (minLenObj instanceof Number && maxLenObj instanceof Number) {
                    int minLen = ((Number) minLenObj).intValue();
                    int maxLen = ((Number) maxLenObj).intValue();
                    if (maxLen < minLen) {
                        throw new ValidationException("object_type '" + objectTypeName + "'.property '" + prop.getName() + "': max_length must be >= min_length");
                    }
                }
            }
            if (constraints.containsKey("pattern")) {
                Object patternObj = constraints.get("pattern");
                if (patternObj instanceof String) {
                    try {
                        Pattern.compile((String) patternObj);
                    } catch (Exception e) {
                        throw new ValidationException("object_type '" + objectTypeName + "'.property '" + prop.getName() + "': invalid regex pattern: " + e.getMessage());
                    }
                }
            }
        } else if ("int".equals(dataType) || "integer".equals(dataType) || "long".equals(dataType) || "float".equals(dataType)) {
            if (constraints.containsKey("min") && constraints.containsKey("max")) {
                Object minObj = constraints.get("min");
                Object maxObj = constraints.get("max");
                if (minObj instanceof Number && maxObj instanceof Number) {
                    double min = ((Number) minObj).doubleValue();
                    double max = ((Number) maxObj).doubleValue();
                    if (max < min) {
                        throw new ValidationException("object_type '" + objectTypeName + "'.property '" + prop.getName() + "': max must be >= min");
                    }
                }
            }
        }
    }

    private boolean isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return NAME_PATTERN.matcher(name).matches();
    }

    private boolean isValidDataType(String dataType) {
        if (dataType == null) {
            return false;
        }
        if (dataType.startsWith("array<") && dataType.endsWith(">")) {
            String innerType = dataType.substring(6, dataType.length() - 1);
            return isValidDataType(innerType);
        }
        // 支持 integer 作为 int 的别名（向后兼容）
        if ("integer".equals(dataType)) {
            return true;
        }
        return VALID_DATA_TYPES.contains(dataType);
    }

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}

