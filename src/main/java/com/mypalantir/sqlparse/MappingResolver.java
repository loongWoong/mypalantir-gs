package com.mypalantir.sqlparse;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.service.MappingService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 映射关系解析器
 * 将 SQL 字段与对象属性进行关联
 */
@Component
public class MappingResolver {

    private final IInstanceStorage instanceStorage;
    private final MappingService mappingService;
    private final Loader loader;

    public MappingResolver(IInstanceStorage instanceStorage, MappingService mappingService, Loader loader) {
        this.instanceStorage = instanceStorage;
        this.mappingService = mappingService;
        this.loader = loader;
    }

    /**
     * 将 SQL 解析结果与系统映射关系进行对齐
     */
    public MappingAlignmentResult alignWithMappings(CalciteSqlParseResult sqlResult) {
        MappingAlignmentResult result = new MappingAlignmentResult();

        List<FieldMapping> fieldMappings = new ArrayList<>();
        List<UnmappedField> unmappedFields = new ArrayList<>();
        Set<String> involvedObjectTypes = new HashSet<>();
        Map<String, String> tableToObjectMap = new HashMap<>();
        List<JoinPath> joinPaths = new ArrayList<>();

        System.out.println("[MappingResolver] ========== 开始映射对齐 ==========");
        System.out.println("[MappingResolver] SQL解析结果:");
        System.out.println("  - 表数量: " + sqlResult.getTables().size());
        System.out.println("  - SELECT字段数量: " + sqlResult.getSelectFields().size());
        System.out.println("  - WHERE条件数量: " + sqlResult.getWhereConditions().size());
        System.out.println("  - GROUP BY字段数量: " + sqlResult.getGroupByFields().size());
        System.out.println("  - 聚合字段数量: " + sqlResult.getAggregations().size());

        // 1. 建立表到对象类型的映射
        Map<String, CalciteSqlParseResult.TableReference> tableMap = buildTableMap(sqlResult);
        System.out.println("[MappingResolver] 表映射:");
        for (Map.Entry<String, CalciteSqlParseResult.TableReference> entry : tableMap.entrySet()) {
            System.out.println("  - key: " + entry.getKey() + ", tableName: " + entry.getValue().getTableName() + ", alias: " + entry.getValue().getAlias());
        }
        
        Map<String, DataSourceMapping> mappingsByTable = loadMappingsForTables(tableMap.keySet());
        System.out.println("[MappingResolver] 加载的映射配置数量: " + mappingsByTable.size());
        for (Map.Entry<String, DataSourceMapping> entry : mappingsByTable.entrySet()) {
            DataSourceMapping mapping = entry.getValue();
            System.out.println("  - key: " + entry.getKey() + ", objectType: " + mapping.getObjectType() + ", tableName: " + mapping.getTableName());
            System.out.println("    列映射: " + mapping.getColumnPropertyMappings());
        }

        for (Map.Entry<String, CalciteSqlParseResult.TableReference> entry : tableMap.entrySet()) {
            String tableKey = entry.getKey();
            CalciteSqlParseResult.TableReference table = entry.getValue();

            DataSourceMapping mapping = mappingsByTable.get(tableKey.toLowerCase());
            if (mapping != null) {
                tableToObjectMap.put(tableKey, mapping.getObjectType());
                involvedObjectTypes.add(mapping.getObjectType());
                result.setMappingId(mapping.getId());
                System.out.println("[MappingResolver] 表 '" + tableKey + "' 映射到对象类型: " + mapping.getObjectType());
            } else {
                System.out.println("[MappingResolver] 警告: 表 '" + tableKey + "' 未找到映射配置");
            }
        }

        // 2. 处理 SELECT 字段的映射
        System.out.println("[MappingResolver] 开始处理SELECT字段映射:");
        for (int i = 0; i < sqlResult.getSelectFields().size(); i++) {
            CalciteSqlParseResult.SelectField selectField = sqlResult.getSelectFields().get(i);
            System.out.println("  [" + i + "] rawExpression=" + selectField.getRawExpression() + 
                ", fieldName=" + selectField.getFieldName() + ", alias=" + selectField.getAlias() + 
                ", isAggregated=" + selectField.isAggregated() + ", aggregationType=" + selectField.getAggregationType());
            
            FieldMapping fieldMapping = resolveFieldMapping(
                selectField,
                tableMap,
                tableToObjectMap,
                mappingsByTable
            );

            if (fieldMapping != null) {
                System.out.println("    -> sqlField=" + fieldMapping.getSqlField() + ", objectType=" + fieldMapping.getObjectType() + 
                    ", objectProperty=" + fieldMapping.getObjectProperty() + ", confidence=" + fieldMapping.getConfidence());
            }

            if (fieldMapping != null && fieldMapping.getConfidence() == MappingConfidence.HIGH) {
                fieldMappings.add(fieldMapping);
                System.out.println("    -> 添加到fieldMappings (HIGH confidence)");
            } else if (fieldMapping != null) {
                unmappedFields.add(createUnmappedField(selectField, fieldMapping));
                System.out.println("    -> 添加到unmappedFields (非HIGH confidence)");
            } else {
                unmappedFields.add(createUnmappedField(selectField, null));
                System.out.println("    -> 添加到unmappedFields (映射为null)");
            }
        }

        // 3. 处理 WHERE 条件中的字段映射
        for (CalciteSqlParseResult.WhereCondition condition : sqlResult.getWhereConditions()) {
            FieldMapping fieldMapping = resolveWhereFieldMapping(
                condition,
                tableMap,
                tableToObjectMap,
                mappingsByTable
            );

            if (fieldMapping != null && fieldMapping.getConfidence() == MappingConfidence.HIGH) {
                fieldMappings.add(fieldMapping);
            } else if (fieldMapping != null) {
                unmappedFields.add(createUnmappedField(condition, fieldMapping));
            } else {
                unmappedFields.add(createUnmappedField(condition, null));
            }
        }

        // 4. 处理 GROUP BY 字段的映射
        for (String groupByField : sqlResult.getGroupByFields()) {
            FieldMapping fieldMapping = resolveGroupByFieldMapping(
                groupByField,
                tableMap,
                tableToObjectMap,
                mappingsByTable
            );

            if (fieldMapping != null && fieldMapping.getConfidence() == MappingConfidence.HIGH) {
                fieldMappings.add(fieldMapping);
            } else if (fieldMapping != null) {
                unmappedFields.add(createUnmappedField(groupByField, fieldMapping));
            } else {
                unmappedFields.add(createUnmappedField(groupByField, null));
            }
        }

        // 5. 分析 JOIN 路径
        joinPaths = analyzeJoinPaths(sqlResult.getJoins(), tableToObjectMap, mappingsByTable);

        result.setFieldMappings(fieldMappings);
        result.setUnmappedFields(unmappedFields);
        result.setInvolvedObjectTypes(new ArrayList<>(involvedObjectTypes));
        result.setTableToObjectMap(tableToObjectMap);
        result.setJoinPaths(joinPaths);

        return result;
    }

    /**
     * 根据表名查找对应的映射关系
     */
    public List<DataSourceMapping> findMappingsByTable(String tableName) {
        System.out.println("[MappingResolver] findMappingsByTable: tableName=" + tableName);
        try {
            // 尝试多种大小写组合
            List<Map<String, Object>> mappings = mappingService.getMappingsByTable(tableName);
            if (mappings.isEmpty()) {
                System.out.println("[MappingResolver] 第一次查询为空，尝试大写表名: " + tableName.toUpperCase());
                mappings = mappingService.getMappingsByTable(tableName.toUpperCase());
            }
            if (mappings.isEmpty()) {
                System.out.println("[MappingResolver] 大小写精确查询都为空，尝试获取所有映射进行大小写不敏感匹配");
                mappings = findAllMappingsCaseInsensitive(tableName);
            }
            
            System.out.println("[MappingResolver] getMappingsByTable 返回数量: " + mappings.size());
            for (Map<String, Object> m : mappings) {
                System.out.println("[MappingResolver]   - mapping: id=" + m.get("id") + 
                    ", object_type=" + m.get("object_type") + 
                    ", table_name=" + m.get("table_name"));
            }
            return mappings.stream()
                .map(DataSourceMapping::new)
                .collect(Collectors.toList());
        } catch (Exception e) {
            System.out.println("[MappingResolver] findMappingsByTable 异常: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取所有映射并进行大小写不敏感匹配
     */
    private List<Map<String, Object>> findAllMappingsCaseInsensitive(String tableName) {
        try {
            List<Map<String, Object>> allMappings = instanceStorage.searchInstances("mapping", new HashMap<>());
            String targetName = tableName.toLowerCase();
            List<Map<String, Object>> matched = new ArrayList<>();
            for (Map<String, Object> m : allMappings) {
                String storedTableName = (String) m.get("table_name");
                if (storedTableName != null && storedTableName.equalsIgnoreCase(targetName)) {
                    matched.add(m);
                }
            }
            System.out.println("[MappingResolver] 大小写不敏感匹配结果: " + matched.size());
            return matched;
        } catch (Exception e) {
            System.out.println("[MappingResolver] findAllMappingsCaseInsensitive 异常: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析字段路径为对象属性路径
     */
    public PropertyPath resolvePropertyPath(String tableAlias, String fieldName, Map<String, String> tableToObjectMap) {
        PropertyPath path = new PropertyPath();
        path.setSqlPath(tableAlias + "." + fieldName);

        // 获取对象类型
        String objectType = tableToObjectMap.get(tableAlias);
        if (objectType == null) {
            // 尝试从映射中查找
            for (Map.Entry<String, String> entry : tableToObjectMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(tableAlias)) {
                    objectType = entry.getValue();
                    break;
                }
            }
        }

        if (objectType != null) {
            path.setObjectType(objectType);

            try {
                ObjectType type = loader.getObjectType(objectType);
                Property property = findPropertyByColumnName(type, fieldName);

                if (property != null) {
                    path.setPropertyName(property.getName());
                    path.setObjectPropertyPath(objectType + "." + property.getName());
                    path.setFound(true);
                }
            } catch (Loader.NotFoundException e) {
                // 对象类型不存在
            }
        }

        return path;
    }

    /**
     * 构建表映射表
     */
    private Map<String, CalciteSqlParseResult.TableReference> buildTableMap(CalciteSqlParseResult sqlResult) {
        Map<String, CalciteSqlParseResult.TableReference> tableMap = new HashMap<>();

        for (CalciteSqlParseResult.TableReference table : sqlResult.getTables()) {
            tableMap.put(table.getTableName().toLowerCase(), table);
            if (table.getAlias() != null) {
                tableMap.put(table.getAlias().toLowerCase(), table);
            }
        }

        return tableMap;
    }

    /**
     * 加载表的映射关系
     */
    private Map<String, DataSourceMapping> loadMappingsForTables(Set<String> tableNames) {
        System.out.println("[MappingResolver] loadMappingsForTables: 输入tableNames=" + tableNames);
        Map<String, DataSourceMapping> result = new HashMap<>();

        for (String tableName : tableNames) {
            System.out.println("[MappingResolver] 处理表名: " + tableName);
            List<DataSourceMapping> mappings = findMappingsByTable(tableName);
            System.out.println("[MappingResolver]   -> 找到映射数量: " + mappings.size());
            for (DataSourceMapping mapping : mappings) {
                System.out.println("[MappingResolver]   -> 添加映射: tableName=" + tableName + ", objectType=" + mapping.getObjectType());
                result.put(tableName.toLowerCase(), mapping);
                if (mapping.getTableName() != null) {
                    System.out.println("[MappingResolver]   -> 同时添加表名映射: " + mapping.getTableName().toLowerCase());
                    result.put(mapping.getTableName().toLowerCase(), mapping);
                    result.put(mapping.getTableName().toUpperCase(), mapping);
                }
            }
        }

        System.out.println("[MappingResolver] loadMappingsForTables 完成: result.size=" + result.size());
        return result;
    }

    /**
     * 解析 SELECT 字段的映射
     */
    private FieldMapping resolveFieldMapping(
            CalciteSqlParseResult.SelectField selectField,
            Map<String, CalciteSqlParseResult.TableReference> tableMap,
            Map<String, String> tableToObjectMap,
            Map<String, DataSourceMapping> mappingsByTable) {

        FieldMapping fieldMapping = new FieldMapping();
        String rawExpression = selectField.getRawExpression();
        // 对于聚合字段，sqlField应设置为物理字段名（不含聚合函数），以便后续匹配
        // 例如：COUNT(ID) AS count_star -> sqlField = ID
        String fieldName = selectField.getFieldName();
        if (selectField.isAggregated() && fieldName != null && !"*".equals(fieldName)) {
            fieldMapping.setSqlField(fieldName);
        } else {
            fieldMapping.setSqlField(rawExpression);
        }

        // 提取表别名和字段名
        String tableAlias = selectField.getTableAlias();

        // 如果没有显式的表别名，尝试从表达式中提取
        if (tableAlias == null && rawExpression.contains(".")) {
            String[] parts = rawExpression.split("\\.");
            if (parts.length > 0) {
                tableAlias = parts[0].trim();
            }
        }

        // 确定表名
        String tableName = null;
        if (tableAlias != null) {
            CalciteSqlParseResult.TableReference table = tableMap.get(tableAlias.toLowerCase());
            if (table != null) {
                tableName = table.getTableName();
            }
        } else {
            // 使用主表
            for (CalciteSqlParseResult.TableReference table : tableMap.values()) {
                if (table.isMainTable()) {
                    tableName = table.getTableName();
                    break;
                }
            }
        }

        if (tableName != null) {
            fieldMapping.setSqlTable(tableName);
            fieldMapping.setTableName(tableName);

            // 查找映射
            DataSourceMapping mapping = mappingsByTable.get(tableName.toLowerCase());
            if (mapping != null) {
                fieldMapping.setMappingId(mapping.getId());
                fieldMapping.setObjectType(mapping.getObjectType());

                // 查找字段映射
                String columnName = fieldName;
                if (columnName == null) {
                    // 尝试从别名推导
                    columnName = selectField.getAlias();
                }

                if (columnName != null) {
                    String propertyName = mapping.findPropertyByColumn(columnName);
                    if (propertyName != null) {
                        fieldMapping.setObjectProperty(propertyName);
                        fieldMapping.setColumnName(columnName);
                        fieldMapping.setConfidence(MappingConfidence.HIGH);
                        return fieldMapping;
                    }
                }

                // 尝试模糊匹配
                String matchedProperty = fuzzyMatchProperty(mapping, fieldName != null ? fieldName : selectField.getAlias());
                if (matchedProperty != null) {
                    fieldMapping.setObjectProperty(matchedProperty);
                    fieldMapping.setColumnName(fieldName);
                    fieldMapping.setConfidence(MappingConfidence.MEDIUM);
                    return fieldMapping;
                }
            }
        }

        // 无法映射
        fieldMapping.setConfidence(MappingConfidence.LOW);
        return fieldMapping;
    }

    /**
     * 解析 WHERE 字段的映射
     */
    private FieldMapping resolveWhereFieldMapping(
            CalciteSqlParseResult.WhereCondition condition,
            Map<String, CalciteSqlParseResult.TableReference> tableMap,
            Map<String, String> tableToObjectMap,
            Map<String, DataSourceMapping> mappingsByTable) {

        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setSqlField(condition.getField());

        String[] parts = condition.getField().split("\\.");
        String tableAlias = parts.length > 1 ? parts[0].trim() : null;
        String fieldName = parts.length > 1 ? parts[1].trim() : parts[0].trim();

        String tableName = null;
        if (tableAlias != null) {
            CalciteSqlParseResult.TableReference table = tableMap.get(tableAlias.toLowerCase());
            if (table != null) {
                tableName = table.getTableName();
            }
        }

        if (tableName != null) {
            fieldMapping.setSqlTable(tableName);
            fieldMapping.setTableName(tableName);

            DataSourceMapping mapping = mappingsByTable.get(tableName.toLowerCase());
            if (mapping != null) {
                fieldMapping.setMappingId(mapping.getId());
                fieldMapping.setObjectType(mapping.getObjectType());

                String propertyName = mapping.findPropertyByColumn(fieldName);
                if (propertyName != null) {
                    fieldMapping.setObjectProperty(propertyName);
                    fieldMapping.setColumnName(fieldName);
                    fieldMapping.setConfidence(MappingConfidence.HIGH);
                    return fieldMapping;
                }

                String matchedProperty = fuzzyMatchProperty(mapping, fieldName);
                if (matchedProperty != null) {
                    fieldMapping.setObjectProperty(matchedProperty);
                    fieldMapping.setColumnName(fieldName);
                    fieldMapping.setConfidence(MappingConfidence.MEDIUM);
                    return fieldMapping;
                }
            }
        }

        fieldMapping.setConfidence(MappingConfidence.LOW);
        return fieldMapping;
    }

    /**
     * 解析 GROUP BY 字段的映射
     */
    private FieldMapping resolveGroupByFieldMapping(
            String groupByField,
            Map<String, CalciteSqlParseResult.TableReference> tableMap,
            Map<String, String> tableToObjectMap,
            Map<String, DataSourceMapping> mappingsByTable) {

        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setSqlField(groupByField);

        String[] parts = groupByField.split("\\.");
        String tableAlias = parts.length > 1 ? parts[0].trim() : null;
        String fieldName = parts.length > 1 ? parts[1].trim() : parts[0].trim();

        // 移除时间函数
        fieldName = removeTimeFunction(fieldName);

        String tableName = null;
        if (tableAlias != null) {
            CalciteSqlParseResult.TableReference table = tableMap.get(tableAlias.toLowerCase());
            if (table != null) {
                tableName = table.getTableName();
            }
        }

        if (tableName != null) {
            fieldMapping.setSqlTable(tableName);
            fieldMapping.setTableName(tableName);

            DataSourceMapping mapping = mappingsByTable.get(tableName.toLowerCase());
            if (mapping != null) {
                fieldMapping.setMappingId(mapping.getId());
                fieldMapping.setObjectType(mapping.getObjectType());

                String propertyName = mapping.findPropertyByColumn(fieldName);
                if (propertyName != null) {
                    fieldMapping.setObjectProperty(propertyName);
                    fieldMapping.setColumnName(fieldName);
                    fieldMapping.setConfidence(MappingConfidence.HIGH);
                    return fieldMapping;
                }

                String matchedProperty = fuzzyMatchProperty(mapping, fieldName);
                if (matchedProperty != null) {
                    fieldMapping.setObjectProperty(matchedProperty);
                    fieldMapping.setColumnName(fieldName);
                    fieldMapping.setConfidence(MappingConfidence.MEDIUM);
                    return fieldMapping;
                }
            }
        }

        fieldMapping.setConfidence(MappingConfidence.LOW);
        return fieldMapping;
    }

    /**
     * 创建未映射字段记录
     */
    private UnmappedField createUnmappedField(CalciteSqlParseResult.SelectField selectField, FieldMapping mapping) {
        UnmappedField unmapped = new UnmappedField();
        unmapped.setSqlExpression(selectField.getRawExpression());
        unmapped.setFieldType("SELECT");
        unmapped.setAggregated(selectField.isAggregated());
        unmapped.setAggregationType(selectField.getAggregationType());

        if (mapping != null) {
            unmapped.setTableAlias(selectField.getTableAlias());
            unmapped.setSuggestedObjectType(mapping.getObjectType());
            unmapped.setConfidence(mapping.getConfidence());
        }

        return unmapped;
    }

    /**
     * 创建 WHERE 未映射字段记录
     */
    private UnmappedField createUnmappedField(CalciteSqlParseResult.WhereCondition condition, FieldMapping mapping) {
        UnmappedField unmapped = new UnmappedField();
        unmapped.setSqlExpression(condition.getField() + " " + condition.getOperator() + " " + condition.getValue());
        unmapped.setFieldType("WHERE");
        unmapped.setAggregated(false);

        if (mapping != null) {
            unmapped.setTableAlias(condition.getField().split("\\.")[0]);
            unmapped.setSuggestedObjectType(mapping.getObjectType());
            unmapped.setConfidence(mapping.getConfidence());
        }

        return unmapped;
    }

    /**
     * 创建 GROUP BY 未映射字段记录
     */
    private UnmappedField createUnmappedField(String groupByField, FieldMapping mapping) {
        UnmappedField unmapped = new UnmappedField();
        unmapped.setSqlExpression(groupByField);
        unmapped.setFieldType("GROUP_BY");
        unmapped.setAggregated(false);

        if (mapping != null) {
            unmapped.setTableAlias(groupByField.split("\\.")[0]);
            unmapped.setSuggestedObjectType(mapping.getObjectType());
            unmapped.setConfidence(mapping.getConfidence());
        }

        return unmapped;
    }

    /**
     * 分析 JOIN 路径
     */
    private List<JoinPath> analyzeJoinPaths(
            List<CalciteSqlParseResult.JoinInfo> joins,
            Map<String, String> tableToObjectMap,
            Map<String, DataSourceMapping> mappingsByTable) {

        List<JoinPath> joinPaths = new ArrayList<>();

        for (CalciteSqlParseResult.JoinInfo join : joins) {
            JoinPath path = new JoinPath();
            path.setJoinType(join.getJoinType());
            path.setJoinedTable(join.getJoinedTable());
            path.setOnCondition(join.getOnCondition());

            // 空值校验：跳过未解析的 JOIN
            if (join.getJoinedTable() == null || join.getJoinedTable().trim().isEmpty()) {
                continue;
            }

            // 尝试解析关联的对象类型
            String tableName = join.getJoinedTable().split("\\s+")[0];
            DataSourceMapping mapping = mappingsByTable.get(tableName.toLowerCase());
            if (mapping != null) {
                path.setTargetObjectType(mapping.getObjectType());
            }

            joinPaths.add(path);
        }

        return joinPaths;
    }

    /**
     * 模糊匹配属性
     */
    private String fuzzyMatchProperty(DataSourceMapping mapping, String columnName) {
        if (columnName == null || mapping == null) return null;

        List<String> columns = new ArrayList<>(mapping.getColumnPropertyMappings().keySet());

        // 精确匹配
        if (columns.contains(columnName)) {
            return mapping.getColumnPropertyMappings().get(columnName);
        }

        // 大小写不敏感匹配
        for (String col : columns) {
            if (col.equalsIgnoreCase(columnName)) {
                return mapping.getColumnPropertyMappings().get(col);
            }
        }

        // 下划线匹配
        String snakeColumn = toSnakeCase(columnName);
        for (String col : columns) {
            if (col.equalsIgnoreCase(snakeColumn)) {
                return mapping.getColumnPropertyMappings().get(col);
            }
        }

        // 驼峰匹配
        String camelColumn = toCamelCase(columnName);
        for (String col : columns) {
            if (col.equalsIgnoreCase(camelColumn)) {
                return mapping.getColumnPropertyMappings().get(col);
            }
        }

        return null;
    }

    /**
     * 在对象类型中查找属性（通过列名）
     */
    private Property findPropertyByColumnName(ObjectType objectType, String columnName) {
        for (Property property : objectType.getProperties()) {
            // 假设属性名与列名有映射关系
            if (property.getName().equalsIgnoreCase(columnName)) {
                return property;
            }
        }
        return null;
    }

    /**
     * 移除时间函数
     */
    private String removeTimeFunction(String fieldName) {
        return fieldName.replaceAll("(?i)^(DATE|YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|WEEK|QUARTER)\\s*\\([^)]+\\)\\s*", "").trim();
    }

    /**
     * 转换为蛇形命名
     */
    private String toSnakeCase(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * 转换为驼峰命名
     */
    private String toCamelCase(String str) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '_' || c == '-') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        return result.toString();
    }

    // ==================== 内部类定义 ====================

    public static class MappingAlignmentResult {
        private List<FieldMapping> fieldMappings = new ArrayList<>();
        private List<UnmappedField> unmappedFields = new ArrayList<>();
        private List<String> involvedObjectTypes = new ArrayList<>();
        private Map<String, String> tableToObjectMap = new HashMap<>();
        private List<JoinPath> joinPaths = new ArrayList<>();
        private String mappingId;

        public List<FieldMapping> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(List<FieldMapping> fieldMappings) { this.fieldMappings = fieldMappings; }
        public List<UnmappedField> getUnmappedFields() { return unmappedFields; }
        public void setUnmappedFields(List<UnmappedField> unmappedFields) { this.unmappedFields = unmappedFields; }
        public List<String> getInvolvedObjectTypes() { return involvedObjectTypes; }
        public void setInvolvedObjectTypes(List<String> involvedObjectTypes) { this.involvedObjectTypes = involvedObjectTypes; }
        public Map<String, String> getTableToObjectMap() { return tableToObjectMap; }
        public void setTableToObjectMap(Map<String, String> tableToObjectMap) { this.tableToObjectMap = tableToObjectMap; }
        public List<JoinPath> getJoinPaths() { return joinPaths; }
        public void setJoinPaths(List<JoinPath> joinPaths) { this.joinPaths = joinPaths; }
        public String getMappingId() { return mappingId; }
        public void setMappingId(String mappingId) { this.mappingId = mappingId; }
    }

    public static class FieldMapping {
        private String sqlField;
        private String sqlTable;
        private String objectProperty;
        private String objectType;
        private String columnName;
        private String tableName;
        private MappingConfidence confidence = MappingConfidence.LOW;
        private String mappingId;

        public String getSqlField() { return sqlField; }
        public void setSqlField(String sqlField) { this.sqlField = sqlField; }
        public String getSqlTable() { return sqlTable; }
        public void setSqlTable(String sqlTable) { this.sqlTable = sqlTable; }
        public String getObjectProperty() { return objectProperty; }
        public void setObjectProperty(String objectProperty) { this.objectProperty = objectProperty; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public MappingConfidence getConfidence() { return confidence; }
        public void setConfidence(MappingConfidence confidence) { this.confidence = confidence; }
        public String getMappingId() { return mappingId; }
        public void setMappingId(String mappingId) { this.mappingId = mappingId; }
    }

    public static class UnmappedField {
        private String sqlExpression;
        private String fieldType;
        private boolean aggregated;
        private String aggregationType;
        private String tableAlias;
        private String suggestedObjectType;
        private MappingConfidence confidence = MappingConfidence.LOW;
        private List<String> suggestedProperties = new ArrayList<>();

        public String getSqlExpression() { return sqlExpression; }
        public void setSqlExpression(String sqlExpression) { this.sqlExpression = sqlExpression; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public boolean isAggregated() { return aggregated; }
        public void setAggregated(boolean aggregated) { this.aggregated = aggregated; }
        public String getAggregationType() { return aggregationType; }
        public void setAggregationType(String aggregationType) { this.aggregationType = aggregationType; }
        public String getTableAlias() { return tableAlias; }
        public void setTableAlias(String tableAlias) { this.tableAlias = tableAlias; }
        public String getSuggestedObjectType() { return suggestedObjectType; }
        public void setSuggestedObjectType(String suggestedObjectType) { this.suggestedObjectType = suggestedObjectType; }
        public MappingConfidence getConfidence() { return confidence; }
        public void setConfidence(MappingConfidence confidence) { this.confidence = confidence; }
        public List<String> getSuggestedProperties() { return suggestedProperties; }
        public void setSuggestedProperties(List<String> suggestedProperties) { this.suggestedProperties = suggestedProperties; }
    }

    public static class JoinPath {
        private String joinType;
        private String joinedTable;
        private String onCondition;
        private String targetObjectType;

        public String getJoinType() { return joinType; }
        public void setJoinType(String joinType) { this.joinType = joinType; }
        public String getJoinedTable() { return joinedTable; }
        public void setJoinedTable(String joinedTable) { this.joinedTable = joinedTable; }
        public String getOnCondition() { return onCondition; }
        public void setOnCondition(String onCondition) { this.onCondition = onCondition; }
        public String getTargetObjectType() { return targetObjectType; }
        public void setTargetObjectType(String targetObjectType) { this.targetObjectType = targetObjectType; }
    }

    public static class PropertyPath {
        private String sqlPath;
        private String objectType;
        private String propertyName;
        private String objectPropertyPath;
        private boolean found;

        public String getSqlPath() { return sqlPath; }
        public void setSqlPath(String sqlPath) { this.sqlPath = sqlPath; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public String getPropertyName() { return propertyName; }
        public void setPropertyName(String propertyName) { this.propertyName = propertyName; }
        public String getObjectPropertyPath() { return objectPropertyPath; }
        public void setObjectPropertyPath(String objectPropertyPath) { this.objectPropertyPath = objectPropertyPath; }
        public boolean isFound() { return found; }
        public void setFound(boolean found) { this.found = found; }
    }

    public static class DataSourceMapping {
        private String id;
        private String objectType;
        private String tableId;
        private String tableName;
        private Map<String, String> columnPropertyMappings = new HashMap<>();

        @SuppressWarnings("unchecked")
        public DataSourceMapping(Map<String, Object> data) {
            this.id = (String) data.get("id");
            this.objectType = (String) data.get("object_type");
            this.tableId = (String) data.get("table_id");
            this.tableName = (String) data.get("table_name");

            Object mappings = data.get("column_property_mappings");
            if (mappings instanceof Map) {
                this.columnPropertyMappings = new HashMap<>((Map<String, String>) mappings);
            }
        }

        public String findPropertyByColumn(String columnName) {
            return columnPropertyMappings.get(columnName);
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }
        public String getTableId() { return tableId; }
        public void setTableId(String tableId) { this.tableId = tableId; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public Map<String, String> getColumnPropertyMappings() { return columnPropertyMappings; }
        public void setColumnPropertyMappings(Map<String, String> columnPropertyMappings) { this.columnPropertyMappings = columnPropertyMappings; }
        public boolean isConfigured() { return objectType != null && !objectType.isEmpty(); }
    }

    public enum MappingConfidence {
        HIGH, MEDIUM, LOW
    }
}
