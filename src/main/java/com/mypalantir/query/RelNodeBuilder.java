package com.mypalantir.query;

import com.mypalantir.meta.DataSourceMapping;
import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.Property;
import com.mypalantir.meta.TransformationMapping;
import com.mypalantir.query.schema.OntologySchemaFactory;
import com.mypalantir.repository.IInstanceStorage;
import com.mypalantir.service.MappingService;
import com.mypalantir.service.DatabaseMetadataService;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.avatica.util.TimeUnit;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.sql2rel.RelDecorrelator;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.config.CalciteConnectionConfigImpl;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.avatica.util.Casing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 OntologyQuery 直接构建为 RelNode
 */
public class RelNodeBuilder {
    private final Loader loader;
    private final IInstanceStorage instanceStorage;
    private final MappingService mappingService;
    private final OntologySchemaFactory schemaFactory;
    SchemaPlus rootSchema;  // package-private for access from QueryExecutor
    FrameworkConfig frameworkConfig;  // package-private for access from QueryExecutor
    private RelBuilder relBuilder;

    public RelNodeBuilder(Loader loader, IInstanceStorage instanceStorage, 
                          MappingService mappingService, DatabaseMetadataService databaseMetadataService) {
        this.loader = loader;
        this.instanceStorage = instanceStorage;
        this.mappingService = mappingService;
        this.schemaFactory = new OntologySchemaFactory(loader, instanceStorage, mappingService, databaseMetadataService);
    }

    /**
     * 初始化
     */
    public void initialize() throws SQLException {
        // 创建 Calcite Schema
        rootSchema = schemaFactory.createCalciteSchema();
        
        // 创建 FrameworkConfig
        frameworkConfig = Frameworks.newConfigBuilder()
            .defaultSchema(rootSchema)
            .build();
        
        // 创建 RelBuilder
        relBuilder = RelBuilder.create(frameworkConfig);
    }

    /**
     * 将 OntologyQuery 构建为 RelNode
     */
    public RelNode buildRelNode(OntologyQuery query) throws Exception {
        if (rootSchema == null) {
            initialize();
        }

        // 获取 ObjectType
        ObjectType objectType;
        try {
            objectType = loader.getObjectType(query.getFrom());
        } catch (Loader.NotFoundException e) {
            throw new IllegalArgumentException("Object type '" + query.getFrom() + "' not found");
        }

        // 从映射关系获取 DataSourceMapping（如果存在），否则使用 schema 中定义的
        DataSourceMapping dataSourceMapping = getDataSourceMappingFromMapping(objectType);

        // 1. 构建 TableScan
        RelNode scan = buildTableScan(query.getFrom());
        
        // 2. 先构建 JOIN（如果有 links 查询）- 必须在 filter 之前，因为 filter 可能需要访问关联表的字段
        if (query.getLinks() != null && !query.getLinks().isEmpty()) {
            for (OntologyQuery.LinkQuery linkQuery : query.getLinks()) {
                scan = buildJoin(scan, linkQuery, objectType);
            }
        }
        
        // 3. 构建 Filter（WHERE 或 filter 表达式）- 在 JOIN 之后执行，可以访问关联表的字段
        if (query.getFilter() != null && !query.getFilter().isEmpty()) {
            // 新格式：表达式数组
            scan = buildExpressionFilter(scan, query.getFilter(), objectType, query.getLinks());
        } else if (query.getWhere() != null && !query.getWhere().isEmpty()) {
            // 旧格式：Map<String, Object>
            scan = buildFilter(scan, query.getWhere(), objectType, dataSourceMapping);
        }
        
        // 4. 构建 Aggregate（如果有 group_by 或 metrics）
        if ((query.getGroupBy() != null && !query.getGroupBy().isEmpty()) || 
            (query.getMetrics() != null && !query.getMetrics().isEmpty())) {
            scan = buildAggregate(scan, query.getGroupBy(), query.getMetrics(), objectType, query.getLinks());
        } else {
            // 5. 构建 Project（SELECT）- 仅在非聚合查询时执行
            // 注意：如果有 JOIN，需要处理来自多个表的字段
            // 收集所有要选择的字段：主表的字段 + 关联表的字段
            List<String> allSelectFields = new ArrayList<>();
            if (query.getSelect() != null && !query.getSelect().isEmpty()) {
                allSelectFields.addAll(query.getSelect());
            }
            // 添加关联表的 select 字段
            if (query.getLinks() != null && !query.getLinks().isEmpty()) {
                for (OntologyQuery.LinkQuery linkQuery : query.getLinks()) {
                    if (linkQuery.getSelect() != null && !linkQuery.getSelect().isEmpty()) {
                        allSelectFields.addAll(linkQuery.getSelect());
                    }
                }
            }
            if (!allSelectFields.isEmpty()) {
                scan = buildProject(scan, allSelectFields, objectType, query.getLinks());
            }
        }
        
        // 6. 构建 Sort（ORDER BY）
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            scan = buildSort(scan, query.getOrderBy(), objectType, dataSourceMapping);
        }
        
        // 7. 构建 Limit
        if (query.getLimit() != null && query.getLimit() > 0) {
            scan = buildLimit(scan, query.getLimit(), query.getOffset());
        }
        
        return scan;
    }

    /**
     * 构建 TableScan
     * 注意：这个方法不会修改 relBuilder 的状态，因为它会先 clear 再 build
     */
    private RelNode buildTableScan(String tableName) {
        // 创建一个临时的 RelBuilder 来构建 TableScan，避免影响当前的 relBuilder 状态
        RelBuilder tempBuilder = RelBuilder.create(frameworkConfig);
        tempBuilder.scan(tableName);
        return tempBuilder.build();
    }

    /**
     * 构建 Filter（WHERE 条件）
     */
    private RelNode buildFilter(RelNode input, Map<String, Object> where, 
                               ObjectType objectType, DataSourceMapping dataSourceMapping) {
        relBuilder.clear();
        relBuilder.push(input);
        
        RelDataType rowType = input.getRowType();
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        List<RexNode> conditions = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : where.entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();
            
            // 跳过空值或空字符串的条件
            if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                continue;
            }
            
            // 找到属性在行类型中的索引
            int fieldIndex = findFieldIndex(propertyName, objectType, rowType);
            if (fieldIndex < 0) {
                continue; // 跳过不存在的字段
            }
            
            // 构建 RexInputRef（列引用）
            RexInputRef inputRef = rexBuilder.makeInputRef(rowType.getFieldList().get(fieldIndex).getType(), fieldIndex);
            
            // 构建 RexNode（常量值）
            RexNode literal = buildLiteral(rexBuilder, value, rowType.getFieldList().get(fieldIndex).getType());
            
            // 构建等值条件
            RexNode condition = rexBuilder.makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                inputRef,
                literal
            );
            
            conditions.add(condition);
        }
        
        // 组合所有条件（AND）
        if (!conditions.isEmpty()) {
            RexNode combinedCondition = conditions.size() == 1 
                ? conditions.get(0)
                : rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND, conditions);
            relBuilder.filter(combinedCondition);
        }
        
        return relBuilder.build();
    }

    /**
     * 构建 Project（SELECT）
     * @param input 输入 RelNode
     * @param selectFields 要选择的字段列表（可能包含主表和关联表的字段）
     * @param objectType 主对象类型
     * @param links 关联查询列表（用于确定字段来自哪个表）
     */
    private RelNode buildProject(RelNode input, List<String> selectFields, ObjectType objectType, 
                                 List<OntologyQuery.LinkQuery> links) {
        relBuilder.clear();
        relBuilder.push(input);
        
        RelDataType rowType = input.getRowType();
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        List<RexNode> projects = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>();
        
        // 如果没有指定 select 字段，直接返回（不进行 Project）
        if (selectFields == null || selectFields.isEmpty()) {
            return input;
        }
        
        // 构建字段名到索引的映射（基于 JOIN 后的行类型）
        Map<String, Integer> fieldIndexMap = buildFieldIndexMap(objectType, links, rowType);
        
        for (String propertyName : selectFields) {
            Integer fieldIndex = fieldIndexMap.get(propertyName);
            if (fieldIndex != null && fieldIndex >= 0 && fieldIndex < rowType.getFieldCount()) {
                try {
                    RexInputRef inputRef = rexBuilder.makeInputRef(
                        rowType.getFieldList().get(fieldIndex).getType(),
                        fieldIndex
                    );
                    projects.add(inputRef);
                    fieldNames.add(propertyName);
                } catch (Exception e) {
                    // 如果字段索引无效，跳过该字段
                    System.err.println("Warning: Failed to create input ref for field '" + propertyName + "' at index " + fieldIndex + ": " + e.getMessage());
                    continue;
                }
            } else {
                // 字段未找到，记录警告
                System.err.println("Warning: Field '" + propertyName + "' not found in row type. Available fields: " + 
                    rowType.getFieldList().stream().map(f -> f.getName()).collect(java.util.stream.Collectors.joining(", ")));
            }
        }
        
        // 如果没有有效的字段，返回所有字段
        if (projects.isEmpty()) {
            // 返回所有字段
            int fieldCount = rowType.getFieldCount();
            if (fieldCount > 0) {
                for (int i = 0; i < fieldCount; i++) {
                    projects.add(rexBuilder.makeInputRef(rowType.getFieldList().get(i).getType(), i));
                    fieldNames.add(rowType.getFieldList().get(i).getName());
                }
            } else {
                // 如果行类型没有字段，直接返回输入（不应该发生，但为了安全）
                System.err.println("Warning: RowType has no fields. Returning input as-is.");
                return input;
            }
        }
        
        // 确保 projects 和 fieldNames 大小一致
        if (projects.size() != fieldNames.size()) {
            System.err.println("Warning: projects size (" + projects.size() + ") != fieldNames size (" + fieldNames.size() + "). Adjusting...");
            // 调整大小，取较小的
            int minSize = Math.min(projects.size(), fieldNames.size());
            projects = projects.subList(0, minSize);
            fieldNames = fieldNames.subList(0, minSize);
        }
        
        if (!projects.isEmpty() && projects.size() == fieldNames.size()) {
            try {
                relBuilder.project(projects, fieldNames);
                return relBuilder.build();
            } catch (Exception e) {
                System.err.println("Error building project: " + e.getMessage());
                e.printStackTrace();
                // 如果 project 失败，返回输入（不进行投影）
                return input;
            }
        } else {
            // 如果仍然没有有效的字段（不应该发生，但为了安全），直接返回输入
            System.err.println("Warning: No valid fields to project. Returning input as-is.");
            return input;
        }
    }
    
    /**
     * 构建字段名到索引的映射
     * 根据 JOIN 后的行类型结构，找到每个字段的索引
     */
    private Map<String, Integer> buildFieldIndexMap(ObjectType objectType, 
                                                    List<OntologyQuery.LinkQuery> links,
                                                    RelDataType rowType) {
        Map<String, Integer> fieldMap = new java.util.HashMap<>();
        List<org.apache.calcite.rel.type.RelDataTypeField> fields = rowType.getFieldList();
        
        // 遍历所有字段，根据字段名匹配属性名
        for (int i = 0; i < fields.size(); i++) {
            String fieldName = fields.get(i).getName();
            if (fieldName == null) {
                continue;
            }
            
            // 主表的 id 字段
            if ("id".equalsIgnoreCase(fieldName) && !fieldMap.containsKey("id")) {
                fieldMap.put("id", i);
                continue;
            }
            
            // 主表属性字段
            if (objectType.getProperties() != null) {
                for (Property prop : objectType.getProperties()) {
                    if (prop.getName().equals(fieldName) && !fieldMap.containsKey(prop.getName())) {
                        fieldMap.put(prop.getName(), i);
                        break;
                    }
                }
            }
            
            // 关联表字段（link type 属性和目标表属性）
            if (links != null) {
                for (OntologyQuery.LinkQuery linkQuery : links) {
                    try {
                        LinkType linkType = loader.getLinkType(linkQuery.getName());
                        
                        // Link type 的属性
                        if (linkType.getProperties() != null) {
                            for (Property linkProp : linkType.getProperties()) {
                                if (linkProp.getName().equals(fieldName) && !fieldMap.containsKey(linkProp.getName())) {
                                    fieldMap.put(linkProp.getName(), i);
                                    break;
                                }
                            }
                        }
                        
                        // 目标表的属性
                        if (linkQuery.getSelect() != null) {
                            ObjectType targetObjectType = loader.getObjectType(linkType.getTargetType());
                            if (targetObjectType.getProperties() != null) {
                                for (Property targetProp : targetObjectType.getProperties()) {
                                    if (targetProp.getName().equals(fieldName) && !fieldMap.containsKey(targetProp.getName())) {
                                        fieldMap.put(targetProp.getName(), i);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Loader.NotFoundException e) {
                        // 跳过无效的 link type
                        continue;
                    }
                }
            }
        }
        
        return fieldMap;
    }

    /**
     * 构建 Sort（ORDER BY）
     */
    private RelNode buildSort(RelNode input, List<OntologyQuery.OrderBy> orderByList,
                             ObjectType objectType, DataSourceMapping dataSourceMapping) {
        relBuilder.clear();
        relBuilder.push(input);
        
        RelDataType rowType = input.getRowType();
        List<RelFieldCollation> fieldCollations = new ArrayList<>();
        
        for (OntologyQuery.OrderBy orderBy : orderByList) {
            String propertyName = orderBy.getField();
            // 首先尝试通过字段名直接匹配（适用于聚合结果的别名）
            int fieldIndex = findFieldIndexByName(propertyName, rowType);
            
            // 如果直接匹配失败，尝试通过对象属性查找
            if (fieldIndex < 0) {
                fieldIndex = findFieldIndex(propertyName, objectType, rowType);
            }
            
            if (fieldIndex >= 0) {
                RelFieldCollation.Direction direction = "DESC".equalsIgnoreCase(orderBy.getDirection())
                    ? RelFieldCollation.Direction.DESCENDING
                    : RelFieldCollation.Direction.ASCENDING;
                
                fieldCollations.add(new RelFieldCollation(fieldIndex, direction));
            } else {
                // 输出警告信息，帮助调试
                String availableFields = rowType.getFieldList().stream()
                    .map(f -> f.getName())
                    .collect(java.util.stream.Collectors.joining(", "));
                System.err.println("Warning: Sort field '" + propertyName + "' not found. Available fields: " + availableFields);
            }
        }
        
        if (!fieldCollations.isEmpty()) {
            RelCollation collation = RelCollations.of(fieldCollations);
            relBuilder.sort(collation);
            return relBuilder.build();
        } else {
            // 如果没有有效的排序字段，直接返回输入（不创建 Sort 节点）
            return input;
        }
    }
    
    /**
     * 通过字段名直接查找字段索引（适用于聚合结果的别名）
     */
    private int findFieldIndexByName(String fieldName, RelDataType rowType) {
        List<org.apache.calcite.rel.type.RelDataTypeField> fields = rowType.getFieldList();
        
        // 首先尝试精确匹配
        for (int i = 0; i < fields.size(); i++) {
            String currentFieldName = fields.get(i).getName();
            if (fieldName.equals(currentFieldName)) {
                return i;
            }
        }
        
        // 如果精确匹配失败，尝试忽略大小写匹配
        for (int i = 0; i < fields.size(); i++) {
            String currentFieldName = fields.get(i).getName();
            if (fieldName.equalsIgnoreCase(currentFieldName)) {
                return i;
            }
        }
        
        return -1;
    }

    /**
     * 构建 JOIN 查询
     * @param leftInput 左表（主表，如车辆表）
     * @param linkQuery 关联查询
     * @param sourceObjectType 源对象类型（如"车辆"）
     * @return JOIN 后的 RelNode
     */
    private RelNode buildJoin(RelNode leftInput, OntologyQuery.LinkQuery linkQuery, 
                             ObjectType sourceObjectType) throws Exception {
        // 1. 获取 LinkType
        LinkType linkType;
        try {
            linkType = loader.getLinkType(linkQuery.getName());
        } catch (Loader.NotFoundException e) {
            throw new IllegalArgumentException("Link type '" + linkQuery.getName() + "' not found");
        }
        
        // 2. 判断查询方向：是从 source 端还是 target 端查询
        boolean isFromSource = linkType.getSourceType().equals(sourceObjectType.getName());
        boolean isFromTarget = linkType.getTargetType().equals(sourceObjectType.getName());
        
        if (!isFromSource && !isFromTarget) {
            throw new IllegalArgumentException("Link type '" + linkQuery.getName() + 
                "' is not connected to object type '" + sourceObjectType.getName() + 
                "'. Link connects '" + linkType.getSourceType() + "' to '" + linkType.getTargetType() + "'");
        }
        
        // 3. 验证 direction：对于 directed link，只能从 source 查询到 target
        if ("directed".equals(linkType.getDirection()) && isFromTarget) {
            throw new IllegalArgumentException("Cannot query directed link '" + linkQuery.getName() + 
                "' from target side. This is a directed link and can only be queried from source type '" + 
                linkType.getSourceType() + "'");
        }
        
        // 4. 确定实际的 source 和 target（根据查询方向）
        ObjectType actualSourceType, actualTargetType;
        DataSourceMapping actualSourceMapping, actualTargetMapping;
        
        if (isFromSource) {
            // 从 source 端查询：source -> target
            actualSourceType = sourceObjectType;
            try {
                actualTargetType = loader.getObjectType(linkType.getTargetType());
            } catch (Loader.NotFoundException e) {
                throw new IllegalArgumentException("Target object type '" + linkType.getTargetType() + "' not found");
            }
        } else {
            // 从 target 端查询：target -> source（仅适用于 undirected link）
            actualSourceType = sourceObjectType;  // 当前查询的起始对象（target）
            try {
                actualTargetType = loader.getObjectType(linkType.getSourceType());  // 要查询到的对象（source）
            } catch (Loader.NotFoundException e) {
                throw new IllegalArgumentException("Source object type '" + linkType.getSourceType() + "' not found");
            }
        }
        
        // 5. 获取数据源映射（从 mapping 动态获取，而不是从 schema 静态配置）
        actualSourceMapping = getDataSourceMappingFromMapping(actualSourceType);
        if (actualSourceMapping == null || !actualSourceMapping.isConfigured()) {
            throw new IllegalArgumentException("Source object type '" + actualSourceType.getName() + 
                "' does not have data source configured. Please configure a mapping for this object type.");
        }
        
        actualTargetMapping = getDataSourceMappingFromMapping(actualTargetType);
        if (actualTargetMapping == null || !actualTargetMapping.isConfigured()) {
            throw new IllegalArgumentException("Target object type '" + actualTargetType.getName() + 
                "' does not have data source configured. Please configure a mapping for this object type.");
        }
        
        // 6. 获取 LinkType 的数据源映射（支持显式配置或从 ObjectType mapping 推导）
        DataSourceMapping linkMapping = getLinkDataSourceMapping(linkType, actualSourceType, actualTargetType, actualTargetMapping);
        
        // 7. 判断 LinkType 映射模式（基于实际的 target）
        boolean isForeignKeyMode = linkMapping.isForeignKeyMode(actualTargetType);
        
        System.out.println("[buildJoin] Link type '" + linkQuery.getName() + "' mode: " + 
            (isForeignKeyMode ? "foreign_key" : "relation_table"));
        System.out.println("[buildJoin] Link mapping table: " + linkMapping.getTable());
        System.out.println("[buildJoin] Source mapping table: " + actualSourceMapping.getTable());
        System.out.println("[buildJoin] Target mapping table: " + actualTargetMapping.getTable());
        
        if (isForeignKeyMode) {
            // 外键模式：直接 JOIN 目标表（目标表中包含外键）
            return buildForeignKeyJoin(leftInput, actualSourceType, actualTargetType, 
                                      linkType, linkMapping, actualTargetMapping, isFromSource);
        } else {
            // 关系表模式：通过中间表 JOIN
            return buildRelationTableJoin(leftInput, actualSourceType, actualTargetType, 
                                         linkType, linkMapping, actualTargetMapping, isFromSource);
        }
    }

    /**
     * 构建外键模式的 JOIN（直接 JOIN 目标表，目标表中包含外键）
     * 例如：收费站 JOIN 收费记录（通过 toll_records.station_id）
     * 
     * @param leftInput 左表（当前查询的起始对象表）
     * @param sourceObjectType 源对象类型（当前查询的起始对象）
     * @param targetObjectType 目标对象类型（要查询到的对象）
     * @param linkType LinkType 定义（用于获取 transformation_mappings）
     * @param linkMapping LinkType 的数据源映射
     * @param targetMapping 目标对象的数据源映射
     * @param isFromSource 是否从 source 端查询（true：从 source 查询到 target，false：从 target 查询到 source）
     * @return JOIN 后的 RelNode
     */
    private RelNode buildForeignKeyJoin(RelNode leftInput, ObjectType sourceObjectType,
                                        ObjectType targetObjectType,
                                        LinkType linkType,
                                        DataSourceMapping linkMapping,
                                        DataSourceMapping targetMapping,
                                        boolean isFromSource) throws Exception {
        relBuilder.clear();
        relBuilder.push(leftInput);
        
        // 扫描目标表（外键就在这个表中）
        RelNode targetTableScan = buildTableScan(targetObjectType.getName());
        relBuilder.push(targetTableScan);
        
        RelDataType leftRowType = leftInput.getRowType();
        RelDataType targetRowType = targetTableScan.getRowType();
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        
        // 源表的 ID 字段（索引 0）
        int leftIdIndex = 0;
        RexNode leftIdRef = rexBuilder.makeInputRef(
            leftRowType.getFieldList().get(leftIdIndex).getType(),
            leftIdIndex
        );
        
        // 检查是否有 transformation_mappings 配置
        if (linkType != null && linkType.hasTransformationMappings()) {
            // 使用 transformation_mappings 构建 JOIN 条件
            return buildJoinWithTransformationMappings(leftInput, sourceObjectType, targetObjectType,
                                                      linkType, targetMapping, isFromSource);
        }
        
        // 根据查询方向确定 JOIN 条件（原有逻辑）
        RexNode joinCondition;
        if (isFromSource) {
            // 从 source 查询到 target：source_table.id = target_table.source_id_column
            String sourceIdColumn = linkMapping.getSourceIdColumn();
            if (sourceIdColumn == null || sourceIdColumn.isEmpty()) {
                throw new IllegalArgumentException("Source ID column is not configured for foreign key mode link type");
            }
            
            // 在目标表的行类型中查找外键字段
            int targetForeignKeyIndex = findFieldIndexInRowType(sourceIdColumn, targetRowType);
            if (targetForeignKeyIndex < 0) {
                String propertyName = targetMapping.getPropertyName(sourceIdColumn);
                if (propertyName != null) {
                    targetForeignKeyIndex = findFieldIndexInRowType(propertyName, targetRowType);
                }
            }
            
            if (targetForeignKeyIndex < 0) {
                throw new IllegalArgumentException("Foreign key column '" + sourceIdColumn + 
                    "' not found in target table '" + targetObjectType.getName() + 
                    "'. Available fields: " + targetRowType.getFieldList().stream()
                        .map(f -> f.getName()).collect(java.util.stream.Collectors.joining(", ")));
            }
            
            RexNode targetForeignKeyRef = rexBuilder.makeInputRef(
                targetRowType.getFieldList().get(targetForeignKeyIndex).getType(),
                leftRowType.getFieldCount() + targetForeignKeyIndex
            );
            
            joinCondition = rexBuilder.makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                leftIdRef,
                targetForeignKeyRef
            );
        } else {
            // 从 target 查询到 source：target_table.id = source_table.source_id_column
            // 注意：在外键模式中，外键在 target 表中，所以从 target 查询到 source 时，
            // 需要找到 source 表中对应的外键字段（实际上 source 表就是 target 表，因为外键在 target 表中）
            // 但这种情况在外键模式中不常见，因为外键模式通常是一对多关系，总是从 source 查询到 target
            // 这里为了完整性，我们假设外键在 target 表中，从 target 查询时，需要找到 source 表
            // 但实际上，外键模式从 target 端查询可能没有意义，因为外键在 target 表中
            throw new IllegalArgumentException("Foreign key mode does not support querying from target side. " +
                "The foreign key is in the target table, so queries should start from the source side.");
        }
        
        relBuilder.join(JoinRelType.LEFT, joinCondition);
        return relBuilder.build();
    }

    /**
     * 使用 transformation_mappings 构建 JOIN（支持复杂的字段转换逻辑）
     * 
     * @param leftInput 左表（当前查询的起始对象表）
     * @param sourceObjectType 源对象类型（当前查询的起始对象）
     * @param targetObjectType 目标对象类型（要查询到的对象）
     * @param linkType LinkType 定义（包含 transformation_mappings）
     * @param targetMapping 目标对象的数据源映射
     * @param isFromSource 是否从 source 端查询
     * @return JOIN 后的 RelNode
     */
    private RelNode buildJoinWithTransformationMappings(RelNode leftInput, ObjectType sourceObjectType,
                                                         ObjectType targetObjectType,
                                                         LinkType linkType,
                                                         DataSourceMapping targetMapping,
                                                         boolean isFromSource) throws Exception {
        relBuilder.clear();
        relBuilder.push(leftInput);
        
        // 扫描目标表
        RelNode targetTableScan = buildTableScan(targetObjectType.getName());
        relBuilder.push(targetTableScan);
        
        RelDataType leftRowType = leftInput.getRowType();
        RelDataType targetRowType = targetTableScan.getRowType();
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        
        // 获取源对象的数据源映射
        DataSourceMapping sourceMapping = getDataSourceMappingFromMapping(sourceObjectType);
        if (sourceMapping == null || !sourceMapping.isConfigured()) {
            throw new IllegalArgumentException("Source object type '" + sourceObjectType.getName() + 
                "' does not have data source configured.");
        }
        
        // 构建 JOIN 条件列表
        List<RexNode> joinConditions = new ArrayList<>();
        
        // 1. 处理 property_mappings 中的直接映射字段（如 clear_date, pay_card_type）
        if (linkType.getPropertyMappings() != null) {
            for (Map.Entry<String, String> entry : linkType.getPropertyMappings().entrySet()) {
                String sourceProperty = entry.getKey();
                String targetProperty = entry.getValue();
                
                // 在源表中查找字段
                int sourceFieldIndex = findFieldIndexInRowType(sourceProperty, leftRowType);
                if (sourceFieldIndex < 0) {
                    // 尝试通过列名查找
                    String sourceColumn = sourceMapping.getColumnName(sourceProperty);
                    if (sourceColumn != null) {
                        sourceFieldIndex = findFieldIndexInRowType(sourceColumn, leftRowType);
                    }
                }
                
                // 在目标表中查找字段
                int targetFieldIndex = findFieldIndexInRowType(targetProperty, targetRowType);
                if (targetFieldIndex < 0) {
                    // 尝试通过列名查找
                    String targetColumn = targetMapping.getColumnName(targetProperty);
                    if (targetColumn != null) {
                        targetFieldIndex = findFieldIndexInRowType(targetColumn, targetRowType);
                    }
                }
                
                if (sourceFieldIndex >= 0 && targetFieldIndex >= 0) {
                    RexNode sourceRef = rexBuilder.makeInputRef(
                        leftRowType.getFieldList().get(sourceFieldIndex).getType(),
                        sourceFieldIndex
                    );
                    RexNode targetRef = rexBuilder.makeInputRef(
                        targetRowType.getFieldList().get(targetFieldIndex).getType(),
                        leftRowType.getFieldCount() + targetFieldIndex
                    );
                    
                    RexNode condition = rexBuilder.makeCall(
                        org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                        sourceRef,
                        targetRef
                    );
                    joinConditions.add(condition);
                    
                    System.out.println("[buildJoinWithTransformationMappings] Added property mapping: " + 
                        sourceProperty + " = " + targetProperty);
                } else {
                    System.out.println("[buildJoinWithTransformationMappings] Warning: Could not find fields for mapping: " + 
                        sourceProperty + " -> " + targetProperty);
                }
            }
        }
        
        // 2. 处理 transformation_mappings 中的转换映射
        if (linkType.getTransformationMappings() != null) {
            for (TransformationMapping transformation : linkType.getTransformationMappings()) {
                String sourceProperty = transformation.getSourceProperty();
                String targetProperty = transformation.getTargetProperty();
                String transformationType = transformation.getTransformationType();
                
                System.out.println("[buildJoinWithTransformationMappings] Processing transformation: " + 
                    sourceProperty + " -> " + targetProperty + " (type: " + transformationType + ")");
                
                // 获取 JOIN 条件（如果配置了）
                String joinConditionSql = transformation.getJoinCondition();
                if (joinConditionSql != null && !joinConditionSql.trim().isEmpty()) {
                    System.out.println("[buildJoinWithTransformationMappings] Found join_condition: " + joinConditionSql);
                    try {
                        // 解析 join_condition SQL 并转换为 RexNode
                        RexNode conditionNode = parseJoinCondition(
                            joinConditionSql, 
                            leftInput, 
                            targetTableScan,
                            sourceObjectType,
                            targetObjectType,
                            sourceMapping,
                            targetMapping
                        );
                        if (conditionNode != null) {
                            joinConditions.add(conditionNode);
                            System.out.println("[buildJoinWithTransformationMappings] Successfully parsed join_condition");
                        } else {
                            System.out.println("[buildJoinWithTransformationMappings] Failed to parse join_condition, skipping");
                        }
                    } catch (Exception e) {
                        System.err.println("[buildJoinWithTransformationMappings] Error parsing join_condition: " + e.getMessage());
                        e.printStackTrace();
                        // 如果解析失败，继续使用其他条件
                    }
                } else {
                    // 如果没有 join_condition，尝试构建简单的转换条件
                    // 根据 transformation_type 构建不同的 JOIN 条件
                    if ("direct".equals(transformationType)) {
                        // 直接映射：source_property = target_property
                        int sourceFieldIndex = findFieldIndexInRowType(sourceProperty, leftRowType);
                        int targetFieldIndex = findFieldIndexInRowType(targetProperty, targetRowType);
                        
                        if (sourceFieldIndex >= 0 && targetFieldIndex >= 0) {
                            RexNode sourceRef = rexBuilder.makeInputRef(
                                leftRowType.getFieldList().get(sourceFieldIndex).getType(),
                                sourceFieldIndex
                            );
                            RexNode targetRef = rexBuilder.makeInputRef(
                                targetRowType.getFieldList().get(targetFieldIndex).getType(),
                                leftRowType.getFieldCount() + targetFieldIndex
                            );
                            
                            RexNode condition = rexBuilder.makeCall(
                                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                                sourceRef,
                                targetRef
                            );
                            joinConditions.add(condition);
                        }
                    } else if ("reverse_derivation".equals(transformationType)) {
                        // 反向推导：需要复杂的转换逻辑
                        // 由于无法在 JOIN 条件中直接使用子查询，这里先记录
                        // 实际实现中，可能需要：
                        // 1. 使用 EXISTS 子查询
                        // 2. 或者先构建 JOIN，然后在 Filter 中应用转换逻辑
                        System.out.println("[buildJoinWithTransformationMappings] reverse_derivation requires complex logic, " +
                            "consider using join_condition in transformation_config");
                    } else if ("lookup".equals(transformationType)) {
                        // 查找表转换：通过查找表进行转换
                        // 配置示例：
                        // transformation_config:
                        //   lookup_table: "TBL_GBSECTIONDIC"
                        //   lookup_key: "ID"
                        //   lookup_value: "ROADID"
                        //   source_key: "split_org"
                        //   target_key: "toll_section_id"
                        if (transformation.getTransformationConfig() != null) {
                            String lookupTable = (String) transformation.getTransformationConfig().get("lookup_table");
                            String lookupKey = (String) transformation.getTransformationConfig().get("lookup_key");
                            String lookupValue = (String) transformation.getTransformationConfig().get("lookup_value");
                            String sourceKey = (String) transformation.getTransformationConfig().get("source_key");
                            String targetKey = (String) transformation.getTransformationConfig().get("target_key");
                            
                            if (lookupTable != null && lookupKey != null && lookupValue != null && 
                                sourceKey != null && targetKey != null) {
                                try {
                                    // 构建查找表的扫描
                                    RelNode lookupTableScan = buildTableScan(lookupTable);
                                    
                                    // 构建 JOIN 条件：source.source_key = lookup_table.lookup_value AND target.target_key = lookup_table.lookup_key
                                    int sourceFieldIndex = findFieldIndexInRowType(sourceKey, leftRowType);
                                    int targetFieldIndex = findFieldIndexInRowType(targetKey, targetRowType);
                                    RelDataType lookupRowType = lookupTableScan.getRowType();
                                    int lookupValueIndex = findFieldIndexInRowType(lookupValue, lookupRowType);
                                    int lookupKeyIndex = findFieldIndexInRowType(lookupKey, lookupRowType);
                                    
                                    if (sourceFieldIndex >= 0 && targetFieldIndex >= 0 && 
                                        lookupValueIndex >= 0 && lookupKeyIndex >= 0) {
                                        // 先 JOIN 查找表到源表
                                        RexNode sourceRef = rexBuilder.makeInputRef(
                                            leftRowType.getFieldList().get(sourceFieldIndex).getType(),
                                            sourceFieldIndex
                                        );
                                        RexNode lookupValueRef = rexBuilder.makeInputRef(
                                            lookupRowType.getFieldList().get(lookupValueIndex).getType(),
                                            leftRowType.getFieldCount() + lookupValueIndex
                                        );
                                        RexNode lookupCondition = rexBuilder.makeCall(
                                            org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                                            sourceRef,
                                            lookupValueRef
                                        );
                                        
                                        // 然后 JOIN 目标表，条件：target.target_key = lookup_table.lookup_key
                                        // 注意：这里需要先构建第一个 JOIN，然后再构建第二个 JOIN
                                        // 为了简化，我们使用子查询方式
                                        String lookupSubquery = String.format(
                                            "SELECT %s FROM %s WHERE %s = ?",
                                            lookupKey, lookupTable, lookupValue
                                        );
                                        
                                        // 使用 EXISTS 子查询方式
                                        String existsCondition = String.format(
                                            "EXISTS (SELECT 1 FROM %s WHERE %s = %s AND %s = %s)",
                                            lookupTable,
                                            lookupValue, sourceKey,
                                            lookupKey, targetKey
                                        );
                                        
                                        RexNode existsNode = parseJoinCondition(
                                            existsCondition,
                                            leftInput,
                                            targetTableScan,
                                            sourceObjectType,
                                            targetObjectType,
                                            sourceMapping,
                                            targetMapping
                                        );
                                        
                                        if (existsNode != null) {
                                            joinConditions.add(existsNode);
                                            System.out.println("[buildJoinWithTransformationMappings] Successfully built lookup transformation");
                                        } else {
                                            System.out.println("[buildJoinWithTransformationMappings] Failed to build lookup transformation, using simple join");
                                            // 回退到简单的 JOIN 条件
                                            joinConditions.add(lookupCondition);
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("[buildJoinWithTransformationMappings] Error building lookup transformation: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            } else {
                                System.out.println("[buildJoinWithTransformationMappings] lookup transformation missing required config");
                            }
                        }
                    } else if ("function".equals(transformationType)) {
                        // 函数转换：使用函数进行转换
                        // 配置示例：
                        // transformation_config:
                        //   sql_expression: "SUBSTR(source_property, 1, 2)"
                        String sqlExpression = transformation.getSqlExpression();
                        if (sqlExpression != null && !sqlExpression.trim().isEmpty()) {
                            try {
                                // 解析 SQL 表达式并构建 JOIN 条件
                                // 例如：SUBSTR(source.split_org, 1, 2) = target.toll_section_id
                                String functionCondition = sqlExpression + " = " + targetProperty;
                                
                                RexNode functionNode = parseJoinCondition(
                                    functionCondition,
                                    leftInput,
                                    targetTableScan,
                                    sourceObjectType,
                                    targetObjectType,
                                    sourceMapping,
                                    targetMapping
                                );
                                
                                if (functionNode != null) {
                                    joinConditions.add(functionNode);
                                    System.out.println("[buildJoinWithTransformationMappings] Successfully built function transformation");
                                } else {
                                    System.out.println("[buildJoinWithTransformationMappings] Failed to parse function expression: " + sqlExpression);
                                }
                            } catch (Exception e) {
                                System.err.println("[buildJoinWithTransformationMappings] Error building function transformation: " + e.getMessage());
                                e.printStackTrace();
                            }
                        } else {
                            System.out.println("[buildJoinWithTransformationMappings] function transformation missing sql_expression");
                        }
                    }
                }
            }
        }
        
        // 组合所有 JOIN 条件（AND）
        if (joinConditions.isEmpty()) {
            throw new IllegalArgumentException("No valid join conditions found for link type '" + 
                linkType.getName() + "' with transformation_mappings");
        }
        
        RexNode combinedCondition = joinConditions.size() == 1 
            ? joinConditions.get(0)
            : rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND, joinConditions);
        
        relBuilder.join(JoinRelType.LEFT, combinedCondition);
        return relBuilder.build();
    }

    /**
     * 构建关系表模式的 JOIN（通过中间表，两次 JOIN）
     * 例如：车辆 JOIN vehicle_media JOIN 通行介质（从 source 查询）
     * 或：通行介质 JOIN vehicle_media JOIN 车辆（从 target 查询）
     * 
     * @param leftInput 左表（当前查询的起始对象表）
     * @param sourceObjectType 源对象类型（当前查询的起始对象）
     * @param targetObjectType 目标对象类型（要查询到的对象）
     * @param linkType LinkType
     * @param linkMapping LinkType 的数据源映射
     * @param targetMapping 目标对象的数据源映射
     * @param isFromSource 是否从 source 端查询（true：从 source 查询到 target，false：从 target 查询到 source）
     * @return JOIN 后的 RelNode
     */
    private RelNode buildRelationTableJoin(RelNode leftInput, ObjectType sourceObjectType,
                                          ObjectType targetObjectType,
                                          LinkType linkType,
                                          DataSourceMapping linkMapping,
                                          DataSourceMapping targetMapping,
                                          boolean isFromSource) throws Exception {
        // 检查是否有 transformation_mappings 配置
        if (linkType != null && linkType.hasTransformationMappings()) {
            // 对于关系表模式，transformation_mappings 主要用于构建额外的 JOIN 条件
            // 基本的关系表 JOIN 逻辑仍然保留
            System.out.println("[buildRelationTableJoin] Link type has transformation_mappings, " +
                "will apply additional conditions after basic join");
        }
        
        // 扫描中间表（例如：vehicle_media）
        String linkTableName = linkMapping.getTable();
        RelNode linkTableScan = buildTableScan(linkTableName);
        
        RelDataType leftRowType = leftInput.getRowType();
        RelDataType linkRowType = linkTableScan.getRowType();
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        
        // 左表的 ID 字段（索引 0）
        int leftIdIndex = 0;
        RexNode leftIdRef = rexBuilder.makeInputRef(
            leftRowType.getFieldList().get(leftIdIndex).getType(),
            leftIdIndex
        );
        
        if (isFromSource) {
            // 从 source 查询到 target：source JOIN link_table JOIN target
            // 第一个 JOIN：source_table.id = link_table.source_id_column
            String sourceIdColumn = linkMapping.getSourceIdColumn();
            int linkSourceIdIndex = findFieldIndexInRowType(sourceIdColumn, linkRowType);
            if (linkSourceIdIndex < 0) {
                throw new IllegalArgumentException("Source ID column '" + sourceIdColumn + "' not found in link table '" + linkTableName + "'");
            }
            
            RexNode linkSourceIdRef = rexBuilder.makeInputRef(
                linkRowType.getFieldList().get(linkSourceIdIndex).getType(),
                leftRowType.getFieldCount() + linkSourceIdIndex
            );
            
            RexNode joinCondition1 = rexBuilder.makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                leftIdRef,
                linkSourceIdRef
            );
            
            relBuilder.clear();
            relBuilder.push(leftInput);
            relBuilder.push(linkTableScan);
            relBuilder.join(JoinRelType.LEFT, joinCondition1);
            RelNode firstJoin = relBuilder.build();
            RelDataType firstJoinRowType = firstJoin.getRowType();
            
            // 第二个 JOIN：link_table.target_id_column = target_table.id
            RelNode targetTableScan = buildTableScan(targetObjectType.getName());
            String targetIdColumn = linkMapping.getTargetIdColumn();
            int linkTargetIdIndex = findFieldIndexInRowType(targetIdColumn, linkRowType);
            if (linkTargetIdIndex < 0) {
                throw new IllegalArgumentException("Target ID column '" + targetIdColumn + "' not found in link table '" + linkTableName + "'");
            }
            
            int linkTargetIdIndexInFirstJoin = findFieldIndexInRowType(targetIdColumn, firstJoinRowType);
            if (linkTargetIdIndexInFirstJoin < 0) {
                throw new IllegalArgumentException("Target ID column '" + targetIdColumn + "' not found in first join result");
            }
            
            RexNode linkTargetIdRef = rexBuilder.makeInputRef(
                firstJoinRowType.getFieldList().get(linkTargetIdIndexInFirstJoin).getType(),
                linkTargetIdIndexInFirstJoin
            );
            
            RelDataType targetRowType = targetTableScan.getRowType();
            int targetIdIndex = 0;
            RexNode targetIdRef = rexBuilder.makeInputRef(
                targetRowType.getFieldList().get(targetIdIndex).getType(),
                firstJoinRowType.getFieldCount() + targetIdIndex
            );
            
            RexNode joinCondition2 = rexBuilder.makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                linkTargetIdRef,
                targetIdRef
            );
            
            relBuilder.clear();
            relBuilder.push(firstJoin);
            relBuilder.push(targetTableScan);
            relBuilder.join(JoinRelType.LEFT, joinCondition2);
            return relBuilder.build();
        } else {
            // 从 target 查询到 source：target JOIN link_table JOIN source
            // 第一个 JOIN：target_table.id = link_table.target_id_column
            String targetIdColumn = linkMapping.getTargetIdColumn();
            int linkTargetIdIndex = findFieldIndexInRowType(targetIdColumn, linkRowType);
            if (linkTargetIdIndex < 0) {
                throw new IllegalArgumentException("Target ID column '" + targetIdColumn + "' not found in link table '" + linkTableName + "'");
            }
            
            RexNode linkTargetIdRef = rexBuilder.makeInputRef(
                linkRowType.getFieldList().get(linkTargetIdIndex).getType(),
                leftRowType.getFieldCount() + linkTargetIdIndex
            );
            
            RexNode joinCondition1 = rexBuilder.makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                leftIdRef,
                linkTargetIdRef
            );
            
            relBuilder.clear();
            relBuilder.push(leftInput);
            relBuilder.push(linkTableScan);
            relBuilder.join(JoinRelType.LEFT, joinCondition1);
            RelNode firstJoin = relBuilder.build();
            RelDataType firstJoinRowType = firstJoin.getRowType();
            
            // 第二个 JOIN：link_table.source_id_column = source_table.id
            RelNode sourceTableScan = buildTableScan(targetObjectType.getName());  // 注意：targetObjectType 实际上是 source（要查询到的对象）
            String sourceIdColumn = linkMapping.getSourceIdColumn();
            int linkSourceIdIndex = findFieldIndexInRowType(sourceIdColumn, linkRowType);
            if (linkSourceIdIndex < 0) {
                throw new IllegalArgumentException("Source ID column '" + sourceIdColumn + "' not found in link table '" + linkTableName + "'");
            }
            
            int linkSourceIdIndexInFirstJoin = findFieldIndexInRowType(sourceIdColumn, firstJoinRowType);
            if (linkSourceIdIndexInFirstJoin < 0) {
                throw new IllegalArgumentException("Source ID column '" + sourceIdColumn + "' not found in first join result");
            }
            
            RexNode linkSourceIdRef = rexBuilder.makeInputRef(
                firstJoinRowType.getFieldList().get(linkSourceIdIndexInFirstJoin).getType(),
                linkSourceIdIndexInFirstJoin
            );
            
            RelDataType sourceRowType = sourceTableScan.getRowType();
            int sourceIdIndex = 0;
            RexNode sourceIdRef = rexBuilder.makeInputRef(
                sourceRowType.getFieldList().get(sourceIdIndex).getType(),
                firstJoinRowType.getFieldCount() + sourceIdIndex
            );
            
            RexNode joinCondition2 = rexBuilder.makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                linkSourceIdRef,
                sourceIdRef
            );
            
            relBuilder.clear();
            relBuilder.push(firstJoin);
            relBuilder.push(sourceTableScan);
            relBuilder.join(JoinRelType.LEFT, joinCondition2);
            return relBuilder.build();
        }
    }
    
    /**
     * 在 RelDataType 中查找字段索引（通过数据库列名）
     */
    private int findFieldIndexInRowType(String columnName, RelDataType rowType) {
        List<org.apache.calcite.rel.type.RelDataTypeField> fields = rowType.getFieldList();
        String upperColumnName = columnName.toUpperCase();
        for (int i = 0; i < fields.size(); i++) {
            String fieldName = fields.get(i).getName();
            if (fieldName != null && fieldName.toUpperCase().equals(upperColumnName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 构建 Limit
     */
    private RelNode buildLimit(RelNode input, Integer limit, Integer offset) {
        relBuilder.clear();
        relBuilder.push(input);
        
        if (offset != null && offset > 0) {
            relBuilder.limit(offset, limit);
        } else {
            relBuilder.limit(0, limit);
        }
        
        return relBuilder.build();
    }

    /**
     * 查找字段在行类型中的索引
     */
    private int findFieldIndex(String propertyName, ObjectType objectType, RelDataType rowType) {
        // 行类型的第一列是 id，然后是属性
        // 需要根据属性名找到对应的索引
        
        List<org.apache.calcite.rel.type.RelDataTypeField> fields = rowType.getFieldList();
        
        // 首先检查是否是 id 字段
        if ("id".equals(propertyName) && !fields.isEmpty()) {
            // id 字段在索引 0
            return 0;
        }
        
        // 然后查找属性字段
        if (objectType.getProperties() != null) {
            int propertyIndex = 0;
            for (Property prop : objectType.getProperties()) {
                if (prop.getName().equals(propertyName)) {
                    // 索引 = 1 (id) + propertyIndex
                    int fieldIndex = 1 + propertyIndex;
                    if (fieldIndex < fields.size()) {
                        return fieldIndex;
                    }
                }
                propertyIndex++;
            }
        }
        
        return -1;
    }

    /**
     * 构建字面量
     */
    private RexNode buildLiteral(RexBuilder rexBuilder, Object value, RelDataType type) {
        if (value == null) {
            return rexBuilder.makeNullLiteral(type);
        }
        
        SqlTypeName sqlTypeName = type.getSqlTypeName();
        
        switch (sqlTypeName) {
            case VARCHAR:
            case CHAR:
                // 使用 NlsString 确保 Unicode 字符（如中文）正确处理
                // 指定字符集为 UTF-8，避免编码错误
                // 使用 COERCIBLE collation 确保与列类型匹配
                org.apache.calcite.sql.SqlCollation collation = org.apache.calcite.sql.SqlCollation.COERCIBLE;
                org.apache.calcite.util.NlsString nlsString = new org.apache.calcite.util.NlsString(
                    value.toString(),
                    "UTF-8",
                    collation
                );
                return rexBuilder.makeLiteral(nlsString, type, false);
            case INTEGER:
            case BIGINT:
                if (value instanceof Number) {
                    return rexBuilder.makeLiteral(
                        ((Number) value).longValue(),
                        type,
                        false
                    );
                } else if (value instanceof String) {
                    // 尝试将字符串解析为数字
                    try {
                        long longValue = Long.parseLong((String) value);
                        return rexBuilder.makeLiteral(longValue, type, false);
                    } catch (NumberFormatException e) {
                        // 如果解析失败，抛出异常
                        throw new IllegalArgumentException("Cannot convert string '" + value + "' to integer for field type " + type);
                    }
                }
                break;
            case DOUBLE:
            case FLOAT:
                if (value instanceof Number) {
                    return rexBuilder.makeLiteral(
                        ((Number) value).doubleValue(),
                        type,
                        false
                    );
                } else if (value instanceof String) {
                    // 尝试将字符串解析为数字
                    try {
                        double doubleValue = Double.parseDouble((String) value);
                        return rexBuilder.makeLiteral(doubleValue, type, false);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Cannot convert string '" + value + "' to double for field type " + type);
                    }
                }
                break;
            case BOOLEAN:
                if (value instanceof Boolean) {
                    return rexBuilder.makeLiteral((Boolean) value, type, false);
                } else if (value instanceof String) {
                    // 尝试将字符串解析为布尔值
                    String str = ((String) value).toLowerCase().trim();
                    if ("true".equals(str) || "1".equals(str) || "yes".equals(str)) {
                        return rexBuilder.makeLiteral(true, type, false);
                    } else if ("false".equals(str) || "0".equals(str) || "no".equals(str)) {
                        return rexBuilder.makeLiteral(false, type, false);
                    } else {
                        throw new IllegalArgumentException("Cannot convert string '" + value + "' to boolean");
                    }
                }
                break;
            case DATE:
                // DATE 类型：Calcite 的 makeLiteral 期望 Integer（表示距离 UNIX 纪元 1970-01-01 的天数）
                if (value instanceof String) {
                    String dateStr = ((String) value).trim();
                    // 如果字符串为空，返回 NULL
                    if (dateStr.isEmpty()) {
                        return rexBuilder.makeNullLiteral(type);
                    }
                    try {
                        // 使用 LocalDate 避免时区问题
                        java.time.LocalDate localDate = java.time.LocalDate.parse(dateStr);
                        // 计算距离 1970-01-01 的天数
                        int daysSinceEpoch = (int) localDate.toEpochDay();
                        return rexBuilder.makeLiteral(daysSinceEpoch, type, false);
                    } catch (java.time.format.DateTimeParseException e) {
                        throw new IllegalArgumentException("Invalid date format: " + dateStr + ". Expected format: YYYY-MM-DD");
                    }
                } else if (value instanceof java.sql.Date) {
                    // 转换为 LocalDate 再计算天数，避免时区问题
                    java.time.LocalDate localDate = ((java.sql.Date) value).toLocalDate();
                    int daysSinceEpoch = (int) localDate.toEpochDay();
                    return rexBuilder.makeLiteral(daysSinceEpoch, type, false);
                } else if (value instanceof java.time.LocalDate) {
                    // 如果已经是 LocalDate，直接计算天数
                    int daysSinceEpoch = (int) ((java.time.LocalDate) value).toEpochDay();
                    return rexBuilder.makeLiteral(daysSinceEpoch, type, false);
                } else if (value instanceof Number) {
                    // 如果已经是数字（天数），直接使用
                    return rexBuilder.makeLiteral(((Number) value).intValue(), type, false);
                } else {
                    String valueStr = value.toString().trim();
                    // 如果字符串为空，返回 NULL
                    if (valueStr.isEmpty()) {
                        return rexBuilder.makeNullLiteral(type);
                    }
                    try {
                        // 使用 LocalDate 避免时区问题
                        java.time.LocalDate localDate = java.time.LocalDate.parse(valueStr);
                        // 计算距离 1970-01-01 的天数
                        int daysSinceEpoch = (int) localDate.toEpochDay();
                        return rexBuilder.makeLiteral(daysSinceEpoch, type, false);
                    } catch (java.time.format.DateTimeParseException e) {
                        throw new IllegalArgumentException("Cannot convert '" + value + "' to DATE");
                    }
                }
            case TIMESTAMP:
                // TIMESTAMP 类型：Calcite 的 makeLiteral 期望 Long（时间戳毫秒数）
                // 我们需要将字符串解析为 Timestamp，然后获取毫秒数
                if (value instanceof String) {
                    String dateStr = ((String) value).trim();
                    // 如果字符串为空，返回 NULL
                    if (dateStr.isEmpty()) {
                        return rexBuilder.makeNullLiteral(type);
                    }
                    // 确保日期时间格式正确
                    if (dateStr.length() <= 10) {
                        // 只有日期部分，添加时间部分
                        dateStr = dateStr + " 00:00:00";
                    }
                    // 解析为 Timestamp 对象，然后获取毫秒数
                    try {
                        java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf(dateStr);
                        long timestampMillis = timestamp.getTime();
                        // 使用 Long（时间戳毫秒数）创建字面量，但指定类型为 TIMESTAMP
                        return rexBuilder.makeLiteral(timestampMillis, type, false);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid timestamp format: " + dateStr + 
                            ". Expected format: YYYY-MM-DD or YYYY-MM-DD HH:MM:SS. Error: " + e.getMessage());
                    }
                } else if (value instanceof java.sql.Timestamp) {
                    // 获取时间戳毫秒数
                    long timestampMillis = ((java.sql.Timestamp) value).getTime();
                    return rexBuilder.makeLiteral(timestampMillis, type, false);
                } else if (value instanceof java.sql.Date) {
                    // 获取时间戳毫秒数
                    long timestampMillis = ((java.sql.Date) value).getTime();
                    return rexBuilder.makeLiteral(timestampMillis, type, false);
                } else if (value instanceof Number) {
                    // 如果已经是数字，直接使用
                    long timestampMillis = ((Number) value).longValue();
                    return rexBuilder.makeLiteral(timestampMillis, type, false);
                } else {
                    // 转换为字符串再解析
                    String dateStr = value.toString().trim();
                    // 如果字符串为空，返回 NULL
                    if (dateStr.isEmpty()) {
                        return rexBuilder.makeNullLiteral(type);
                    }
                    if (dateStr.length() <= 10) {
                        dateStr = dateStr + " 00:00:00";
                    }
                    try {
                        java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf(dateStr);
                        long timestampMillis = timestamp.getTime();
                        return rexBuilder.makeLiteral(timestampMillis, type, false);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Cannot convert '" + value + "' to TIMESTAMP: " + e.getMessage());
                    }
                }
            default:
                // 对于未知类型，尝试使用字符串
                // 但需要检查类型是否支持字符串
                if (sqlTypeName == SqlTypeName.VARCHAR || sqlTypeName == SqlTypeName.CHAR) {
                    // 字符串类型，使用 NlsString
                    org.apache.calcite.sql.SqlCollation defaultCollation = org.apache.calcite.sql.SqlCollation.COERCIBLE;
                    org.apache.calcite.util.NlsString defaultNlsString = new org.apache.calcite.util.NlsString(
                        value.toString(),
                        "UTF-8",
                        defaultCollation
                    );
                    return rexBuilder.makeLiteral(defaultNlsString, type, false);
                } else {
                    // 对于其他类型，尝试使用字符串（Calcite 可能会自动转换）
                    // 但如果类型不匹配，会抛出异常
                    try {
                        return rexBuilder.makeLiteral(value.toString(), type, false);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Cannot convert value '" + value + "' (type: " + 
                            value.getClass().getSimpleName() + ") to field type " + sqlTypeName + 
                            ". Original error: " + e.getMessage());
                    }
                }
        }
        
        // 如果所有 case 都 break 了（没有返回），说明类型不匹配
        throw new IllegalArgumentException("Cannot convert value '" + value + "' (type: " + 
            value.getClass().getSimpleName() + ") to field type " + type.getSqlTypeName());
    }

    /**
     * 构建表达式过滤器（新格式：filter 表达式数组）
     * 例如：[["=", "province", "江苏"], ["between", "hasTollRecords.charge_time", "2024-01-01", "2024-01-31"]]
     */
    private RelNode buildExpressionFilter(RelNode input, List<Object> filterExpressions,
                                         ObjectType rootObjectType, List<OntologyQuery.LinkQuery> links) throws Exception {
        relBuilder.clear();
        relBuilder.push(input);
        
        RelDataType rowType = input.getRowType();
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        FieldPathResolver pathResolver = new FieldPathResolver(loader);
        List<RexNode> conditions = new ArrayList<>();
        
        System.out.println("[buildExpressionFilter] Processing " + filterExpressions.size() + " filter expressions");
        
        for (Object expr : filterExpressions) {
            if (!(expr instanceof List)) {
                throw new IllegalArgumentException("Filter expression must be an array: " + expr);
            }
            
            @SuppressWarnings("unchecked")
            List<Object> exprList = (List<Object>) expr;
            if (exprList.isEmpty()) {
                continue;
            }
            
            System.out.println("[buildExpressionFilter] Processing expression: " + exprList);
            
            RexNode condition = buildFilterExpression(exprList, rowType, rootObjectType, links, pathResolver, rexBuilder);
            if (condition != null) {
                conditions.add(condition);
                System.out.println("[buildExpressionFilter] Condition added successfully");
            } else {
                System.err.println("[buildExpressionFilter] Failed to build condition for: " + exprList);
            }
        }
        
        // 组合所有条件（AND）
        if (!conditions.isEmpty()) {
            System.out.println("[buildExpressionFilter] Combining " + conditions.size() + " conditions with AND");
            RexNode combinedCondition = conditions.size() == 1 
                ? conditions.get(0)
                : rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND, conditions);
            relBuilder.filter(combinedCondition);
            System.out.println("[buildExpressionFilter] Filter applied successfully");
        } else {
            System.err.println("[buildExpressionFilter] No valid conditions to apply");
        }
        
        RelNode result = relBuilder.build();
        System.out.println("[buildExpressionFilter] Built Filter RelNode: " + result.getClass().getSimpleName());
        return result;
    }
    
    /**
     * 构建单个过滤表达式
     */
    private RexNode buildFilterExpression(List<Object> expr, RelDataType rowType, ObjectType rootObjectType,
                                         List<OntologyQuery.LinkQuery> links, FieldPathResolver pathResolver,
                                         RexBuilder rexBuilder) throws Exception {
        if (expr.isEmpty()) {
            return null;
        }
        
        String operator = expr.get(0).toString();
        
        // 处理逻辑运算符
        if ("and".equalsIgnoreCase(operator)) {
            List<RexNode> conditions = new ArrayList<>();
            for (int i = 1; i < expr.size(); i++) {
                if (expr.get(i) instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> subExpr = (List<Object>) expr.get(i);
                    RexNode condition = buildFilterExpression(subExpr, rowType, rootObjectType, links, pathResolver, rexBuilder);
                    if (condition != null) {
                        conditions.add(condition);
                    }
                }
            }
            if (conditions.isEmpty()) {
                return null;
            }
            return conditions.size() == 1 
                ? conditions.get(0)
                : rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND, conditions);
        } else if ("or".equalsIgnoreCase(operator)) {
            List<RexNode> conditions = new ArrayList<>();
            for (int i = 1; i < expr.size(); i++) {
                if (expr.get(i) instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> subExpr = (List<Object>) expr.get(i);
                    RexNode condition = buildFilterExpression(subExpr, rowType, rootObjectType, links, pathResolver, rexBuilder);
                    if (condition != null) {
                        conditions.add(condition);
                    }
                }
            }
            if (conditions.isEmpty()) {
                return null;
            }
            return conditions.size() == 1 
                ? conditions.get(0)
                : rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.OR, conditions);
        }
        
        // 处理比较运算符
        if (expr.size() < 3) {
            throw new IllegalArgumentException("Filter expression must have at least 3 elements: " + expr);
        }
        
        String fieldPath = expr.get(1).toString();
        Object value = expr.size() > 2 ? expr.get(2) : null;
        
        // 解析字段路径
        FieldPathResolver.FieldPath fieldPathResult = pathResolver.resolve(fieldPath, rootObjectType, links);
        
        // 找到字段在行类型中的索引
        int fieldIndex = findFieldIndexByPath(fieldPathResult, rowType);
        if (fieldIndex < 0) {
            // 输出所有可用字段以便调试
            String availableFields = rowType.getFieldList().stream()
                .map(f -> f.getName())
                .collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalArgumentException("Field '" + fieldPath + "' (property: '" + 
                fieldPathResult.getPropertyName() + "') not found in row type. Available fields: " + availableFields);
        }
        
        RexInputRef inputRef = rexBuilder.makeInputRef(
            rowType.getFieldList().get(fieldIndex).getType(),
            fieldIndex
        );
        
        // 获取字段类型
        RelDataType fieldType = rowType.getFieldList().get(fieldIndex).getType();
        
        // 根据运算符构建条件
        switch (operator.toLowerCase()) {
            case "=":
            case "eq":
                RexNode literal = buildLiteral(rexBuilder, value, fieldType);
                return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, inputRef, literal);
            
            case ">":
            case "gt":
                literal = buildLiteral(rexBuilder, value, rowType.getFieldList().get(fieldIndex).getType());
                return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.GREATER_THAN, inputRef, literal);
            
            case ">=":
            case "gte":
                literal = buildLiteral(rexBuilder, value, rowType.getFieldList().get(fieldIndex).getType());
                return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, inputRef, literal);
            
            case "<":
            case "lt":
                literal = buildLiteral(rexBuilder, value, rowType.getFieldList().get(fieldIndex).getType());
                return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.LESS_THAN, inputRef, literal);
            
            case "<=":
            case "lte":
                literal = buildLiteral(rexBuilder, value, rowType.getFieldList().get(fieldIndex).getType());
                return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.LESS_THAN_OR_EQUAL, inputRef, literal);
            
            case "between":
                if (expr.size() < 4) {
                    throw new IllegalArgumentException("BETWEEN expression requires 4 elements: " + expr);
                }
                Object value1 = expr.get(2);
                Object value2 = expr.get(3);
                RelDataType fieldTypeForBetween = rowType.getFieldList().get(fieldIndex).getType();
                RexNode literal1 = buildLiteral(rexBuilder, value1, fieldTypeForBetween);
                RexNode literal2 = buildLiteral(rexBuilder, value2, fieldTypeForBetween);
                RexNode geCondition = rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, inputRef, literal1);
                RexNode leCondition = rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.LESS_THAN_OR_EQUAL, inputRef, literal2);
                return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND, geCondition, leCondition);
            
            case "like":
                // 构建 LIKE 表达式
                RexNode literalForLike = buildLiteral(rexBuilder, value, fieldType);
                return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.LIKE, inputRef, literalForLike);
            
            default:
                throw new IllegalArgumentException("Unsupported filter operator: " + operator);
        }
    }
    
    /**
     * 根据字段路径找到字段在行类型中的索引
     * 注意：JOIN 后的字段名就是属性名，不是完整路径
     */
    private int findFieldIndexByPath(FieldPathResolver.FieldPath fieldPath, RelDataType rowType) {
        String propertyName = fieldPath.getPropertyName();
        List<org.apache.calcite.rel.type.RelDataTypeField> fields = rowType.getFieldList();
        
        // 首先尝试精确匹配属性名
        for (int i = 0; i < fields.size(); i++) {
            String fieldName = fields.get(i).getName();
            if (propertyName.equals(fieldName)) {
                return i;
            }
        }
        
        // 如果精确匹配失败，尝试忽略大小写匹配
        for (int i = 0; i < fields.size(); i++) {
            String fieldName = fields.get(i).getName();
            if (propertyName.equalsIgnoreCase(fieldName)) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * 构建聚合查询（GROUP BY + 聚合函数）
     */
    private RelNode buildAggregate(RelNode input, List<String> groupBy, List<Object> metrics,
                                   ObjectType rootObjectType, List<OntologyQuery.LinkQuery> links) throws Exception {
        relBuilder.clear();
        relBuilder.push(input);
        
        RelDataType rowType = input.getRowType();
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        FieldPathResolver pathResolver = new FieldPathResolver(loader);
        
        // 构建分组字段
        List<RexNode> groupByNodes = new ArrayList<>();
        List<String> groupByNames = new ArrayList<>();
        if (groupBy != null && !groupBy.isEmpty()) {
            for (String fieldPath : groupBy) {
                FieldPathResolver.FieldPath fieldPathResult = pathResolver.resolve(fieldPath, rootObjectType, links);
                int fieldIndex = findFieldIndexByPath(fieldPathResult, rowType);
                if (fieldIndex < 0) {
                    throw new IllegalArgumentException("Group by field '" + fieldPath + "' not found");
                }
                RexInputRef inputRef = rexBuilder.makeInputRef(
                    rowType.getFieldList().get(fieldIndex).getType(),
                    fieldIndex
                );
                groupByNodes.add(inputRef);
                groupByNames.add(fieldPathResult.getPropertyName());
            }
        }
        
        // 构建聚合函数
        List<RelBuilder.AggCall> aggregateCalls = new ArrayList<>();
        List<String> aggregateNames = new ArrayList<>();
        if (metrics != null && !metrics.isEmpty()) {
            for (Object metricObj : metrics) {
                if (!(metricObj instanceof List)) {
                    throw new IllegalArgumentException("Metric must be an array: " + metricObj);
                }
                
                @SuppressWarnings("unchecked")
                List<Object> metric = (List<Object>) metricObj;
                if (metric.size() < 2) {
                    throw new IllegalArgumentException("Metric must have at least 2 elements: " + metric);
                }
                
                // 检查并获取函数名
                Object functionObj = metric.get(0);
                if (functionObj == null) {
                    throw new IllegalArgumentException("Metric function (first element) cannot be null: " + metric);
                }
                String function = functionObj.toString().toLowerCase();
                
                // 检查并获取字段路径
                Object fieldPathObj = metric.get(1);
                if (fieldPathObj == null) {
                    throw new IllegalArgumentException("Metric field path (second element) cannot be null: " + metric);
                }
                String fieldPath = fieldPathObj.toString();
                
                // 获取别名（可选）
                String alias = null;
                if (metric.size() > 2) {
                    Object aliasObj = metric.get(2);
                    if (aliasObj != null) {
                        alias = aliasObj.toString();
                    }
                }
                
                // 处理 COUNT(*) 和 COUNT(DISTINCT ...)
                boolean isCountStar = "count".equals(function) && "*".equals(fieldPath);
                boolean isCountDistinct = "count_distinct".equals(function);
                boolean isCountDistinctStar = isCountDistinct && "*".equals(fieldPath);
                
                org.apache.calcite.rex.RexNode fieldNode;
                RelDataType fieldType = null;
                String finalAlias;
                FieldPathResolver.FieldPath fieldPathResult = null;
                
                if (isCountStar || isCountDistinctStar) {
                    // COUNT(*) 或 COUNT(DISTINCT *) - 使用常量表达式
                    fieldNode = relBuilder.literal(1);
                    fieldType = relBuilder.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.INTEGER);
                    if (isCountDistinctStar) {
                        finalAlias = alias != null ? alias : "count_distinct_star";
                    } else {
                        finalAlias = alias != null ? alias : "count_star";
                    }
                } else {
                    // 解析字段路径（包括 COUNT(DISTINCT field) 的情况，但 field 不是 "*"）
                    fieldPathResult = pathResolver.resolve(fieldPath, rootObjectType, links);
                    int fieldIndex = findFieldIndexByPath(fieldPathResult, rowType);
                    if (fieldIndex < 0) {
                        throw new IllegalArgumentException("Metric field '" + fieldPath + "' not found");
                    }
                    
                    // 使用 field() 方法获取字段引用
                    fieldNode = relBuilder.field(fieldIndex);
                    
                    // 获取字段类型
                    fieldType = rowType.getFieldList().get(fieldIndex).getType();
                    
                    if (isCountDistinct) {
                        finalAlias = alias != null ? alias : "count_distinct_" + fieldPathResult.getPropertyName();
                    } else {
                        finalAlias = alias != null ? alias : function + "_" + fieldPathResult.getPropertyName();
                    }
                }
                
                // 构建聚合函数调用
                RelBuilder.AggCall aggregateCall;
                if (isCountDistinct) {
                    // COUNT(DISTINCT field)
                    // aggregateCall(SqlAggFunction, boolean distinct, RexNode filter, String alias, RexNode... operands)
                    aggregateCall = relBuilder.aggregateCall(
                        org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT,
                        true, // distinct = true
                        null, // filter = null
                        null, // alias = null (will be set later)
                        fieldNode // operands
                    );
                } else {
                    switch (function) {
                        case "sum":
                            // 对于 SUM 函数，如果字段类型是 DECIMAL 或可能返回 DECIMAL（如数据库列是 DECIMAL），
                            // 先转换为 DOUBLE 再求和，避免 BigDecimal 无法转换为 Double 的错误
                            org.apache.calcite.rex.RexNode sumFieldNode = fieldNode;
                            SqlTypeName fieldSqlType = fieldType.getSqlTypeName();
                            if (fieldSqlType == SqlTypeName.DECIMAL || fieldSqlType == SqlTypeName.DOUBLE || fieldSqlType == SqlTypeName.FLOAT) {
                                // 将数值类型转换为 DOUBLE，确保 SUM 返回 DOUBLE 而不是 DECIMAL
                                RelDataType doubleType = relBuilder.getTypeFactory().createSqlType(SqlTypeName.DOUBLE);
                                sumFieldNode = rexBuilder.makeCast(doubleType, fieldNode, false);
                            }
                            aggregateCall = relBuilder.aggregateCall(
                                org.apache.calcite.sql.fun.SqlStdOperatorTable.SUM,
                                sumFieldNode
                            );
                            break;
                        case "avg":
                            // 对于 AVG 函数，也转换为 DOUBLE
                            org.apache.calcite.rex.RexNode avgFieldNode = fieldNode;
                            SqlTypeName avgFieldSqlType = fieldType.getSqlTypeName();
                            if (avgFieldSqlType == SqlTypeName.DECIMAL || avgFieldSqlType == SqlTypeName.DOUBLE || avgFieldSqlType == SqlTypeName.FLOAT) {
                                RelDataType doubleType = relBuilder.getTypeFactory().createSqlType(SqlTypeName.DOUBLE);
                                avgFieldNode = rexBuilder.makeCast(doubleType, fieldNode, false);
                            }
                            aggregateCall = relBuilder.aggregateCall(
                                org.apache.calcite.sql.fun.SqlStdOperatorTable.AVG,
                                avgFieldNode
                            );
                            break;
                        case "count":
                            aggregateCall = relBuilder.aggregateCall(
                                org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT,
                                fieldNode
                            );
                            break;
                        case "min":
                            aggregateCall = relBuilder.aggregateCall(
                                org.apache.calcite.sql.fun.SqlStdOperatorTable.MIN,
                                fieldNode
                            );
                            break;
                        case "max":
                            aggregateCall = relBuilder.aggregateCall(
                                org.apache.calcite.sql.fun.SqlStdOperatorTable.MAX,
                                fieldNode
                            );
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported aggregate function: " + function);
                    }
                }
                
                // 如果有别名，使用 as() 方法设置别名
                aggregateCall = aggregateCall.as(finalAlias);
                
                aggregateCalls.add(aggregateCall);
                aggregateNames.add(finalAlias);
            }
        }
        
        // 如果没有分组字段，只有聚合函数，也需要构建 Aggregate
        if (!groupByNodes.isEmpty() || !aggregateCalls.isEmpty()) {
            if (!groupByNodes.isEmpty()) {
                // 有分组字段
                relBuilder.aggregate(
                    relBuilder.groupKey(groupByNodes),
                    aggregateCalls.toArray(new RelBuilder.AggCall[0])
                );
            } else {
                // 只有聚合函数，没有分组
                relBuilder.aggregate(
                    relBuilder.groupKey(),
                    aggregateCalls.toArray(new RelBuilder.AggCall[0])
                );
            }
        }
        
        return relBuilder.build();
    }

    /**
     * 从映射关系获取 DataSourceMapping
     * 强制要求必须配置 Mapping，不再回退到 ObjectType 内部的 data_source
     * 数据源获取链路：Mapping → table_id → Table → database_id → Database → 连接信息
     */
    private DataSourceMapping getDataSourceMappingFromMapping(ObjectType objectType) {
        // 检查是否为系统虚拟对象类型（不需要数据映射）
        String objectTypeName = objectType.getName();
        if (isSystemVirtualObjectType(objectTypeName)) {
            throw new IllegalArgumentException(
                "Object type '" + objectTypeName + "' is a system virtual object and cannot be queried directly. " +
                "Please use business object types (e.g., EntryTransaction, Vehicle) instead."
            );
        }
        
        try {
            List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectType.getName());
            if (mappings == null || mappings.isEmpty()) {
                // 强制要求配置 Mapping，不再回退到 schema 中的 data_source
                throw new IllegalArgumentException(
                    "Object type '" + objectType.getName() + "' does not have a mapping configured. " +
                    "Please configure a mapping in the Data Mapping page."
                );
            }
            
            // 使用第一个映射关系
            Map<String, Object> mappingData = mappings.get(0);
            String tableId = (String) mappingData.get("table_id");
            if (tableId == null) {
                throw new IllegalArgumentException(
                    "Mapping for object type '" + objectType.getName() + "' does not have table_id configured."
                );
            }
            
            // 获取表信息
            Map<String, Object> table = instanceStorage.getInstance("table", tableId);
            if (table == null) {
                throw new IllegalArgumentException(
                    "Table with id '" + tableId + "' not found for object type '" + objectType.getName() + "'."
                );
            }
            
            String tableName = (String) table.get("name");
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalArgumentException(
                    "Table with id '" + tableId + "' does not have a name."
                );
            }
            
            // 关键：从 Table 对象获取 database_id（数据源连接ID）
            String databaseId = (String) table.get("database_id");
            if (databaseId == null || databaseId.isEmpty()) {
                throw new IllegalArgumentException(
                    "Table '" + tableName + "' (id: '" + tableId + "') does not have database_id configured. " +
                    "Please ensure the table is associated with a database."
                );
            }
            
            String primaryKeyColumn = (String) mappingData.get("primary_key_column");
            
            @SuppressWarnings("unchecked")
            Map<String, String> columnPropertyMappings = (Map<String, String>) mappingData.get("column_property_mappings");
            
            // column_property_mappings 的结构是 {列名: 属性名}，需要反转为 {属性名: 列名}
            Map<String, String> fieldMapping = new java.util.HashMap<>();
            if (columnPropertyMappings != null) {
                for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                    String columnName = entry.getKey();
                    String propertyName = entry.getValue();
                    fieldMapping.put(propertyName, columnName);
                }
            }
            
            // 构造 DataSourceMapping，包含完整的数据源连接信息
            DataSourceMapping dataSourceMapping = new DataSourceMapping();
            dataSourceMapping.setConnectionId(databaseId);  // 设置数据源连接ID
            dataSourceMapping.setTable(tableName);
            dataSourceMapping.setIdColumn(primaryKeyColumn != null ? primaryKeyColumn : "id");
            dataSourceMapping.setFieldMapping(fieldMapping);
            
            System.out.println("[getDataSourceMappingFromMapping] Successfully loaded mapping for '" + 
                objectType.getName() + "':");
            System.out.println("  → table: " + tableName + " (table_id: " + tableId + ")");
            System.out.println("  → database_id (connectionId): " + databaseId);
            System.out.println("  → primaryKey: " + dataSourceMapping.getIdColumn());
            
            return dataSourceMapping;
        } catch (IllegalArgumentException e) {
            // 直接抛出参数异常，不做任何回退
            throw e;
        } catch (Exception e) {
            // 其他异常也转换为明确的错误信息
            throw new IllegalArgumentException(
                "Failed to get DataSourceMapping from mapping for object type '" + objectType.getName() + "': " + 
                e.getMessage() + ". Please ensure the mapping is correctly configured.",
                e
            );
        }
    }
    
    /**
     * 获取 LinkType 的数据源映射
     * 主要从 ObjectType 的 Mapping 推导，不依赖 LinkType 内部的 data_source 配置
     * 
     * 支持两种方式：
     * 1. （可选）LinkType 显式配置了 data_source（向后兼容，用于关系表模式）
     * 2. （默认）从 ObjectType mapping 推导：
     *    - 外键模式：使用目标表的 mapping，外键字段命名为 {source_type}_id
     *    - 关系表模式：需要 LinkType 显式配置独立的中间表
     * 
     * @param linkType LinkType 定义
     * @param sourceType 源对象类型
     * @param targetType 目标对象类型
     * @param targetMapping 目标对象的数据源映射（从 Mapping 获取）
     * @return LinkType 的数据源映射
     */
    private DataSourceMapping getLinkDataSourceMapping(LinkType linkType, ObjectType sourceType, 
                                                       ObjectType targetType, DataSourceMapping targetMapping) {
        // 1. （可选）如果 LinkType 显式配置了 data_source，直接使用（向后兼容，用于关系表模式）
        if (linkType.getDataSource() != null && linkType.getDataSource().isConfigured()) {
            System.out.println("[getLinkDataSourceMapping] Using explicit data_source from LinkType (for relation_table mode): " + linkType.getName());
            return linkType.getDataSource();
        }
        
        // 2. （默认）从 ObjectType 的 Mapping 推导外键模式
        // 外键字段命名规则：{source_type_name}_id
        String sourceIdColumnName = sourceType.getName().toLowerCase() + "_id";
        
        // 构造 DataSourceMapping，指向目标表（外键在目标表中）
        DataSourceMapping inferredMapping = new DataSourceMapping();
        inferredMapping.setTable(targetMapping.getTable());
        inferredMapping.setIdColumn(targetMapping.getIdColumn());
        inferredMapping.setSourceIdColumn(sourceIdColumnName);  // 外键字段名
        inferredMapping.setTargetIdColumn(targetMapping.getIdColumn());  // 目标表的主键
        inferredMapping.setLinkMode("foreign_key");  // 显式标记为外键模式
        
        System.out.println("[getLinkDataSourceMapping] Inferred foreign_key mode from ObjectType mapping for link: " + linkType.getName());
        System.out.println("[getLinkDataSourceMapping] Inferred source_id_column: " + sourceIdColumnName);
        System.out.println("[getLinkDataSourceMapping] Using target table from mapping: " + targetMapping.getTable());
        
        return inferredMapping;
    }
    
    /**
     * 解析 join_condition SQL 表达式并转换为 RexNode
     * 支持：等值条件、AND/OR 组合、函数调用、IN 子查询
     * 
     * @param joinConditionSql JOIN 条件的 SQL 表达式
     * @param leftInput 左表 RelNode
     * @param rightInput 右表 RelNode
     * @param sourceObjectType 源对象类型
     * @param targetObjectType 目标对象类型
     * @param sourceMapping 源对象的数据源映射
     * @param targetMapping 目标对象的数据源映射
     * @return 解析后的 RexNode，如果解析失败返回 null
     */
    private RexNode parseJoinCondition(String joinConditionSql, RelNode leftInput, RelNode rightInput,
                                       ObjectType sourceObjectType, ObjectType targetObjectType,
                                       DataSourceMapping sourceMapping, DataSourceMapping targetMapping) {
        try {
            RelDataType leftRowType = leftInput.getRowType();
            RelDataType rightRowType = rightInput.getRowType();
            RexBuilder rexBuilder = relBuilder.getRexBuilder();
            
            // 递归解析条件表达式（支持 AND/OR 嵌套）
            return parseConditionExpression(joinConditionSql.trim(), leftInput, rightInput,
                                          sourceObjectType, targetObjectType,
                                          sourceMapping, targetMapping);
                
        } catch (Exception e) {
            System.err.println("[parseJoinCondition] Error parsing join condition: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 递归解析条件表达式（支持 AND/OR 嵌套）
     */
    private RexNode parseConditionExpression(String expr, RelNode leftInput, RelNode rightInput,
                                            ObjectType sourceObjectType, ObjectType targetObjectType,
                                            DataSourceMapping sourceMapping, DataSourceMapping targetMapping) {
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        expr = expr.trim();
        
        // 移除外层括号
        while (expr.startsWith("(") && expr.endsWith(")") && 
               isBalanced(expr.substring(1, expr.length() - 1))) {
            expr = expr.substring(1, expr.length() - 1).trim();
        }
        
        // 1. 检查是否有顶层 AND
        int andPos = findTopLevelOperator(expr, "AND");
        if (andPos > 0) {
            String leftPart = expr.substring(0, andPos).trim();
            String rightPart = expr.substring(andPos + 3).trim();
            RexNode leftNode = parseConditionExpression(leftPart, leftInput, rightInput,
                                                       sourceObjectType, targetObjectType,
                                                       sourceMapping, targetMapping);
            RexNode rightNode = parseConditionExpression(rightPart, leftInput, rightInput,
                                                        sourceObjectType, targetObjectType,
                                                        sourceMapping, targetMapping);
            if (leftNode != null && rightNode != null) {
                return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND, 
                                         leftNode, rightNode);
            }
        }
        
        // 2. 检查是否有顶层 OR
        int orPos = findTopLevelOperator(expr, "OR");
        if (orPos > 0) {
            String leftPart = expr.substring(0, orPos).trim();
            String rightPart = expr.substring(orPos + 2).trim();
            RexNode leftNode = parseConditionExpression(leftPart, leftInput, rightInput,
                                                       sourceObjectType, targetObjectType,
                                                       sourceMapping, targetMapping);
            RexNode rightNode = parseConditionExpression(rightPart, leftInput, rightInput,
                                                        sourceObjectType, targetObjectType,
                                                        sourceMapping, targetMapping);
            if (leftNode != null && rightNode != null) {
                return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.OR, 
                                         leftNode, rightNode);
            }
        }
        
        // 3. 处理单个条件
        return parseSingleCondition(expr, leftInput, rightInput,
                                   sourceObjectType, targetObjectType,
                                   sourceMapping, targetMapping);
    }
    
    /**
     * 解析单个条件（不包含 AND/OR）
     */
    private RexNode parseSingleCondition(String expr, RelNode leftInput, RelNode rightInput,
                                        ObjectType sourceObjectType, ObjectType targetObjectType,
                                        DataSourceMapping sourceMapping, DataSourceMapping targetMapping) {
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        expr = expr.trim();
        
        // 移除外层括号
        while (expr.startsWith("(") && expr.endsWith(")") && 
               isBalanced(expr.substring(1, expr.length() - 1))) {
            expr = expr.substring(1, expr.length() - 1).trim();
        }
        
        // 1. 处理等值条件：left = right
        if (expr.contains("=") && !expr.toUpperCase().contains(" IN ") && 
            !expr.toUpperCase().contains("EXISTS")) {
            int eqPos = findOperatorPosition(expr, "=");
            if (eqPos > 0) {
                String leftExpr = expr.substring(0, eqPos).trim();
                String rightExpr = expr.substring(eqPos + 1).trim();
                
                RexNode leftNode = parseExpression(leftExpr, leftInput, rightInput,
                                                  sourceObjectType, targetObjectType,
                                                  sourceMapping, targetMapping);
                RexNode rightNode = parseExpression(rightExpr, leftInput, rightInput,
                                                   sourceObjectType, targetObjectType,
                                                   sourceMapping, targetMapping);
                
                if (leftNode != null && rightNode != null) {
                    return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                                             leftNode, rightNode);
                }
            }
        }
        
        // 2. 处理 EXISTS 子查询：EXISTS (SELECT ...)
        if (expr.toUpperCase().trim().startsWith("EXISTS")) {
            String subqueryExpr = expr.substring(6).trim();
            // 移除括号
            if (subqueryExpr.startsWith("(") && subqueryExpr.endsWith(")")) {
                subqueryExpr = subqueryExpr.substring(1, subqueryExpr.length() - 1).trim();
            }
            
            if (subqueryExpr.toUpperCase().startsWith("SELECT")) {
                try {
                    RelNode subqueryRelNode = buildSubqueryRelNode(subqueryExpr);
                    if (subqueryRelNode != null) {
                        return rexBuilder.makeCall(
                            org.apache.calcite.sql.fun.SqlStdOperatorTable.EXISTS,
                            RexSubQuery.scalar(subqueryRelNode)
                        );
                    }
                } catch (Exception e) {
                    System.err.println("[parseSingleCondition] Error building EXISTS subquery: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        // 3. 处理 IN 子查询：field IN (SELECT ...)
        if (expr.toUpperCase().contains(" IN ")) {
            int inPos = findOperatorPosition(expr.toUpperCase(), " IN ");
            if (inPos > 0) {
                String fieldExpr = expr.substring(0, inPos).trim();
                String subqueryExpr = expr.substring(inPos + 4).trim();
                
                // 移除括号
                if (subqueryExpr.startsWith("(") && subqueryExpr.endsWith(")")) {
                    subqueryExpr = subqueryExpr.substring(1, subqueryExpr.length() - 1).trim();
                }
                
                RexNode fieldNode = parseExpression(fieldExpr, leftInput, rightInput,
                                                   sourceObjectType, targetObjectType,
                                                   sourceMapping, targetMapping);
                
                if (fieldNode != null && subqueryExpr.toUpperCase().startsWith("SELECT")) {
                    try {
                        RelNode subqueryRelNode = buildSubqueryRelNode(subqueryExpr);
                        if (subqueryRelNode != null) {
                            return rexBuilder.makeCall(
                                org.apache.calcite.sql.fun.SqlStdOperatorTable.IN,
                                fieldNode,
                                RexSubQuery.scalar(subqueryRelNode)
                            );
                        }
                    } catch (Exception e) {
                        System.err.println("[parseSingleCondition] Error building IN subquery: " + e.getMessage());
                        e.printStackTrace();
                        // 如果解析失败，记录日志但继续使用其他条件
                        System.out.println("[parseSingleCondition] Failed to parse IN subquery, will try other conditions");
                    }
                }
            }
        }
        
        // 4. 处理其他比较运算符：>, <, >=, <=, !=
        String[] operators = {">=", "<=", "!=", "<>", ">", "<"};
        for (String op : operators) {
            int opPos = findOperatorPosition(expr, op);
            if (opPos > 0) {
                String leftExpr = expr.substring(0, opPos).trim();
                String rightExpr = expr.substring(opPos + op.length()).trim();
                
                RexNode leftNode = parseExpression(leftExpr, leftInput, rightInput,
                                                  sourceObjectType, targetObjectType,
                                                  sourceMapping, targetMapping);
                RexNode rightNode = parseExpression(rightExpr, leftInput, rightInput,
                                                   sourceObjectType, targetObjectType,
                                                   sourceMapping, targetMapping);
                
                if (leftNode != null && rightNode != null) {
                    org.apache.calcite.sql.SqlOperator sqlOp;
                    switch (op) {
                        case ">": sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.GREATER_THAN; break;
                        case "<": sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.LESS_THAN; break;
                        case ">=": sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.GREATER_THAN_OR_EQUAL; break;
                        case "<=": sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.LESS_THAN_OR_EQUAL; break;
                        case "!=":
                        case "<>": sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.NOT_EQUALS; break;
                        default: return null;
                    }
                    return rexBuilder.makeCall(sqlOp, leftNode, rightNode);
                }
            }
        }
        
        System.out.println("[parseSingleCondition] Unsupported condition: " + expr);
        return null;
    }
    
    /**
     * 解析表达式（字段引用、函数调用、字面量等）
     */
    private RexNode parseExpression(String expr, RelNode leftInput, RelNode rightInput,
                                    ObjectType sourceObjectType, ObjectType targetObjectType,
                                    DataSourceMapping sourceMapping, DataSourceMapping targetMapping) {
        expr = expr.trim();
        
        // 移除引号
        expr = expr.replaceAll("^[\"`']|[\"`']$", "");
        
        // 1. 处理函数调用（如 LENGTH(field), SUBSTR(field, 1, 2)）
        if (expr.contains("(") && expr.contains(")")) {
            return parseFunctionCall(expr, leftInput, rightInput,
                                   sourceObjectType, targetObjectType,
                                   sourceMapping, targetMapping);
        }
        
        // 2. 处理字段引用
        RexNode fieldRef = parseFieldReference(expr, leftInput, rightInput,
                                              sourceObjectType, targetObjectType,
                                              sourceMapping, targetMapping);
        if (fieldRef != null) {
            return fieldRef;
        }
        
        // 3. 处理字面量（数字、字符串）
        return parseLiteral(expr);
    }
    
    /**
     * 解析函数调用（如 LENGTH(field), SUBSTR(field, 1, 2)）
     */
    private RexNode parseFunctionCall(String expr, RelNode leftInput, RelNode rightInput,
                                     ObjectType sourceObjectType, ObjectType targetObjectType,
                                     DataSourceMapping sourceMapping, DataSourceMapping targetMapping) {
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        
        // 提取函数名和参数
        int openParen = expr.indexOf('(');
        if (openParen < 0) return null;
        
        String funcName = expr.substring(0, openParen).trim().toUpperCase();
        String argsStr = expr.substring(openParen + 1, expr.lastIndexOf(')')).trim();
        
        // 解析参数列表
        List<String> args = parseFunctionArguments(argsStr);
        List<RexNode> rexArgs = new ArrayList<>();
        
        for (String arg : args) {
            RexNode argNode = parseExpression(arg.trim(), leftInput, rightInput,
                                            sourceObjectType, targetObjectType,
                                            sourceMapping, targetMapping);
            if (argNode != null) {
                rexArgs.add(argNode);
            } else {
                // 如果参数解析失败，尝试作为字面量
                RexNode literal = parseLiteral(arg.trim());
                if (literal != null) {
                    rexArgs.add(literal);
                } else {
                    return null; // 参数解析失败
                }
            }
        }
        
        // 根据函数名创建对应的 RexCall
        org.apache.calcite.sql.SqlOperator sqlOp = null;
        switch (funcName) {
            // 字符串函数
            case "LENGTH":
            case "CHAR_LENGTH":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.CHAR_LENGTH;
                break;
            case "SUBSTR":
            case "SUBSTRING":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.SUBSTRING;
                break;
            case "CONCAT":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.CONCAT;
                break;
            case "UPPER":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.UPPER;
                break;
            case "LOWER":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.LOWER;
                break;
            case "TRIM":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.TRIM;
                break;
            case "REPLACE":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.REPLACE;
                break;
            case "LEFT":
                // LEFT(str, n) 等价于 SUBSTRING(str, 1, n)
                if (rexArgs.size() == 2) {
                    // 创建 SUBSTRING(str, 1, n)
                    RexNode startPos = rexBuilder.makeLiteral(1L, relBuilder.getTypeFactory().createSqlType(SqlTypeName.BIGINT), false);
                    List<RexNode> substrArgs = new ArrayList<>();
                    substrArgs.add(rexArgs.get(0)); // str
                    substrArgs.add(startPos); // start position
                    substrArgs.add(rexArgs.get(1)); // length
                    return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.SUBSTRING, substrArgs);
                }
                return null;
            case "RIGHT":
                // RIGHT(str, n) 等价于 SUBSTRING(str, LENGTH(str) - n + 1, n)
                if (rexArgs.size() == 2) {
                    // 计算 LENGTH(str) - n + 1
                    RexNode lengthCall = rexBuilder.makeCall(
                        org.apache.calcite.sql.fun.SqlStdOperatorTable.CHAR_LENGTH,
                        rexArgs.get(0)
                    );
                    RexNode one = rexBuilder.makeLiteral(1L, relBuilder.getTypeFactory().createSqlType(SqlTypeName.BIGINT), false);
                    RexNode startPos = rexBuilder.makeCall(
                        org.apache.calcite.sql.fun.SqlStdOperatorTable.MINUS,
                        rexBuilder.makeCall(
                            org.apache.calcite.sql.fun.SqlStdOperatorTable.MINUS,
                            lengthCall,
                            rexArgs.get(1)
                        ),
                        one
                    );
                    List<RexNode> substrArgs = new ArrayList<>();
                    substrArgs.add(rexArgs.get(0)); // str
                    substrArgs.add(startPos); // start position
                    substrArgs.add(rexArgs.get(1)); // length
                    return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.SUBSTRING, substrArgs);
                }
                return null;
            // 数学函数
            case "ABS":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.ABS;
                break;
            case "ROUND":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.ROUND;
                break;
            case "FLOOR":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.FLOOR;
                break;
            case "CEIL":
            case "CEILING":
                sqlOp = org.apache.calcite.sql.fun.SqlStdOperatorTable.CEIL;
                break;
            // 日期函数
            case "DATE":
                // DATE(expr) - 提取日期部分 - 使用 CAST AS DATE
                if (rexArgs.size() == 1) {
                    RelDataType dateType = rexBuilder.getTypeFactory().createSqlType(SqlTypeName.DATE);
                    return rexBuilder.makeCast(dateType, rexArgs.get(0));
                }
                return null;
            case "YEAR":
                // YEAR(expr) - 提取年份
                if (rexArgs.size() == 1) {
                    // 使用 EXTRACT(YEAR FROM expr)
                    RexNode yearFlag = rexBuilder.makeFlag(TimeUnit.YEAR);
                    return rexBuilder.makeCall(
                        org.apache.calcite.sql.fun.SqlStdOperatorTable.EXTRACT,
                        yearFlag,
                        rexArgs.get(0)
                    );
                }
                return null;
            case "MONTH":
                // MONTH(expr) - 提取月份
                if (rexArgs.size() == 1) {
                    // 使用 EXTRACT(MONTH FROM expr)
                    RexNode monthFlag = rexBuilder.makeFlag(TimeUnit.MONTH);
                    return rexBuilder.makeCall(
                        org.apache.calcite.sql.fun.SqlStdOperatorTable.EXTRACT,
                        monthFlag,
                        rexArgs.get(0)
                    );
                }
                return null;
            case "DAY":
            case "DAYOFMONTH":
                // DAY(expr) - 提取日期
                if (rexArgs.size() == 1) {
                    // 使用 EXTRACT(DAY FROM expr)
                    RexNode dayFlag = rexBuilder.makeFlag(TimeUnit.DAY);
                    return rexBuilder.makeCall(
                        org.apache.calcite.sql.fun.SqlStdOperatorTable.EXTRACT,
                        dayFlag,
                        rexArgs.get(0)
                    );
                }
                return null;
            default:
                System.out.println("[parseFunctionCall] Unsupported function: " + funcName);
                return null;
        }
        
        if (sqlOp != null && !rexArgs.isEmpty()) {
            return rexBuilder.makeCall(sqlOp, rexArgs);
        }
        
        return null;
    }
    
    /**
     * 解析函数参数列表（处理嵌套括号和逗号）
     */
    private List<String> parseFunctionArguments(String argsStr) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = 0;
        
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                args.add(argsStr.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < argsStr.length()) {
            args.add(argsStr.substring(start).trim());
        }
        
        return args;
    }
    
    /**
     * 解析字面量（数字、字符串）
     */
    private RexNode parseLiteral(String expr) {
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        expr = expr.trim();
        
        // 移除引号
        String unquoted = expr.replaceAll("^[\"`']|[\"`']$", "");
        
        // 尝试解析为数字
        try {
            if (unquoted.matches("^-?\\d+$")) {
                // 整数
                return rexBuilder.makeLiteral(
                    Long.parseLong(unquoted),
                    relBuilder.getTypeFactory().createSqlType(SqlTypeName.BIGINT),
                    false
                );
            } else if (unquoted.matches("^-?\\d+\\.\\d+$")) {
                // 浮点数
                return rexBuilder.makeLiteral(
                    Double.parseDouble(unquoted),
                    relBuilder.getTypeFactory().createSqlType(SqlTypeName.DOUBLE),
                    false
                );
            }
        } catch (NumberFormatException e) {
            // 不是数字，继续处理字符串
        }
        
        // 字符串字面量
        if (!unquoted.equals(expr)) {
            // 有引号，是字符串
            return rexBuilder.makeLiteral(
                unquoted,
                relBuilder.getTypeFactory().createSqlType(SqlTypeName.VARCHAR),
                false
            );
        }
        
        return null;
    }
    
    /**
     * 查找顶层操作符的位置（不在括号内）
     */
    private int findTopLevelOperator(String expr, String operator) {
        int depth = 0;
        String upperExpr = expr.toUpperCase();
        String upperOp = operator.toUpperCase();
        
        for (int i = 0; i < upperExpr.length() - upperOp.length() + 1; i++) {
            char c = upperExpr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && upperExpr.substring(i).startsWith(upperOp)) {
                // 检查前后是否是单词边界
                if ((i == 0 || !Character.isLetterOrDigit(upperExpr.charAt(i - 1))) &&
                    (i + upperOp.length() >= upperExpr.length() || 
                     !Character.isLetterOrDigit(upperExpr.charAt(i + upperOp.length())))) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * 查找操作符位置（考虑括号）
     */
    private int findOperatorPosition(String expr, String operator) {
        int depth = 0;
        String upperExpr = expr.toUpperCase();
        String upperOp = operator.toUpperCase();
        
        for (int i = 0; i < upperExpr.length() - upperOp.length() + 1; i++) {
            char c = upperExpr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && upperExpr.substring(i).startsWith(upperOp)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 检查括号是否平衡
     */
    private boolean isBalanced(String expr) {
        int depth = 0;
        for (char c : expr.toCharArray()) {
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth < 0) return false;
            }
        }
        return depth == 0;
    }
    
    /**
     * 解析字段引用（如 table.field 或 field）
     * 
     * @param fieldExpr 字段表达式（可能包含表名前缀）
     * @param leftInput 左表 RelNode
     * @param rightInput 右表 RelNode
     * @param sourceObjectType 源对象类型
     * @param targetObjectType 目标对象类型
     * @param sourceMapping 源对象的数据源映射
     * @param targetMapping 目标对象的数据源映射
     * @return 对应的 RexInputRef，如果找不到返回 null
     */
    private RexNode parseFieldReference(String fieldExpr, RelNode leftInput, RelNode rightInput,
                                       ObjectType sourceObjectType, ObjectType targetObjectType,
                                       DataSourceMapping sourceMapping, DataSourceMapping targetMapping) {
        fieldExpr = fieldExpr.trim();
        
        // 移除可能的引号
        fieldExpr = fieldExpr.replaceAll("^[\"`']|[\"`']$", "");
        
        RelDataType leftRowType = leftInput.getRowType();
        RelDataType rightRowType = rightInput.getRowType();
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        
        // 检查是否包含表名前缀（如 table.field）
        if (fieldExpr.contains(".")) {
            String[] parts = fieldExpr.split("\\.", 2);
            String tableName = parts[0].trim().toLowerCase();
            String fieldName = parts[1].trim();
            
            // 移除可能的引号
            tableName = tableName.replaceAll("^[\"`']|[\"`']$", "");
            fieldName = fieldName.replaceAll("^[\"`']|[\"`']$", "");
            
            // 判断是左表还是右表
            String sourceTableName = sourceMapping.getTable().toLowerCase();
            String targetTableName = targetMapping.getTable().toLowerCase();
            
            // 尝试匹配表名（支持部分匹配，如 v_split_data_sub 匹配 VSplitDataSub）
            if (tableName.contains(sourceTableName) || sourceTableName.contains(tableName) ||
                tableName.equals(sourceObjectType.getName().toLowerCase())) {
                // 左表
                int fieldIndex = findFieldIndexInRowType(fieldName, leftRowType);
                if (fieldIndex < 0) {
                    // 尝试通过列名查找
                    String columnName = sourceMapping.getColumnName(fieldName);
                    if (columnName != null) {
                        fieldIndex = findFieldIndexInRowType(columnName, leftRowType);
                    }
                }
                if (fieldIndex >= 0) {
                    return rexBuilder.makeInputRef(
                        leftRowType.getFieldList().get(fieldIndex).getType(),
                        fieldIndex
                    );
                }
            } else if (tableName.contains(targetTableName) || targetTableName.contains(tableName) ||
                       tableName.equals(targetObjectType.getName().toLowerCase())) {
                // 右表
                int fieldIndex = findFieldIndexInRowType(fieldName, rightRowType);
                if (fieldIndex < 0) {
                    // 尝试通过列名查找
                    String columnName = targetMapping.getColumnName(fieldName);
                    if (columnName != null) {
                        fieldIndex = findFieldIndexInRowType(columnName, rightRowType);
                    }
                }
                if (fieldIndex >= 0) {
                    return rexBuilder.makeInputRef(
                        rightRowType.getFieldList().get(fieldIndex).getType(),
                        leftRowType.getFieldCount() + fieldIndex
                    );
                }
            }
        } else {
            // 没有表名前缀，尝试在两个表中查找
            // 先尝试左表
            int fieldIndex = findFieldIndexInRowType(fieldExpr, leftRowType);
            if (fieldIndex >= 0) {
                return rexBuilder.makeInputRef(
                    leftRowType.getFieldList().get(fieldIndex).getType(),
                    fieldIndex
                );
            }
            
            // 再尝试右表
            fieldIndex = findFieldIndexInRowType(fieldExpr, rightRowType);
            if (fieldIndex >= 0) {
                return rexBuilder.makeInputRef(
                    rightRowType.getFieldList().get(fieldIndex).getType(),
                    leftRowType.getFieldCount() + fieldIndex
                );
            }
        }
        
        return null;
    }
    
    /**
     * 关闭资源
     */
    public void close() throws SQLException {
        schemaFactory.closeConnections();
    }
    
    /**
     * 判断是否为系统虚拟对象类型（不需要数据映射）
     * @param objectTypeName 对象类型名称
     * @return true 表示是系统虚拟对象
     */
    private boolean isSystemVirtualObjectType(String objectTypeName) {
        // 系统虚拟对象类型列表（来自 schema-system.yaml）
        return "workspace".equals(objectTypeName) ||
               "database".equals(objectTypeName) ||
               "table".equals(objectTypeName) ||
               "column".equals(objectTypeName) ||
               "mapping".equals(objectTypeName) ||
               "AtomicMetric".equals(objectTypeName) ||
               "MetricDefinition".equals(objectTypeName);
    }
    
    /**
     * 构建子查询 RelNode
     * 使用 Calcite 的 SQL 解析器将 SQL 字符串转换为 RelNode
     * 
     * @param subquerySql 子查询 SQL 字符串
     * @return 子查询的 RelNode，如果解析失败返回 null
     */
    private RelNode buildSubqueryRelNode(String subquerySql) throws Exception {
        if (rootSchema == null) {
            initialize();
        }
        
        try {
            // 使用 Calcite 的 SQL 解析器解析 SQL
            SqlParser.Config parserConfig = SqlParser.config()
                .withCaseSensitive(false)
                .withQuotedCasing(Casing.UNCHANGED)
                .withUnquotedCasing(Casing.TO_UPPER);
            
            SqlParser parser = SqlParser.create(subquerySql, parserConfig);
            SqlNode sqlNode = parser.parseQuery();
            
            // 创建 CatalogReader
            RelDataTypeFactory typeFactory = relBuilder.getTypeFactory();
            
            // 创建 CalciteConnectionConfig
            java.util.Properties configProperties = new java.util.Properties();
            configProperties.setProperty(CalciteConnectionProperty.CASE_SENSITIVE.camelName(), "false");
            CalciteConnectionConfig connectionConfig = new CalciteConnectionConfigImpl(configProperties);
            
            CalciteCatalogReader catalogReader = new CalciteCatalogReader(
                CalciteSchema.from(rootSchema),
                CalciteSchema.from(rootSchema).path(null),
                typeFactory,
                connectionConfig
            );
            
            // #region agent log
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log"),
                    ("{\"location\":\"RelNodeBuilder.java:2633\",\"message\":\"Before SqlValidator creation\",\"data\":{\"operatorTable\":\"" + frameworkConfig.getOperatorTable().getClass().getName() + "\",\"catalogReader\":\"" + catalogReader.getClass().getName() + "\",\"typeFactory\":\"" + typeFactory.getClass().getName() + "\"},\"timestamp\":" + System.currentTimeMillis() + ",\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"A\"}\n").getBytes(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {}
            // #endregion
            
            // 创建 SqlValidator - 使用 SqlValidatorUtil.newValidator 而不是 SqlValidatorImpl
            // 根据 Calcite 1.37.0 文档，应该使用 SqlValidatorUtil.newValidator
            SqlValidator validator = SqlValidatorUtil.newValidator(
                frameworkConfig.getOperatorTable(),
                catalogReader,
                typeFactory,
                SqlValidator.Config.DEFAULT.withIdentifierExpansion(true)
            );
            
            // #region agent log
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log"),
                    ("{\"location\":\"RelNodeBuilder.java:2651\",\"message\":\"After SqlValidator creation\",\"data\":{\"validatorClass\":\"" + validator.getClass().getName() + "\",\"success\":true},\"timestamp\":" + System.currentTimeMillis() + ",\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"A\"}\n").getBytes(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {}
            // #endregion
            
            // 验证 SQL
            // #region agent log
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log"),
                    ("{\"location\":\"RelNodeBuilder.java:2658\",\"message\":\"Before SQL validation\",\"data\":{\"sqlNode\":\"" + sqlNode.toString().replace("\"", "'") + "\"},\"timestamp\":" + System.currentTimeMillis() + ",\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"B\"}\n").getBytes(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {}
            // #endregion
            
            SqlNode validatedNode = validator.validate(sqlNode);
            
            // #region agent log
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get("c:\\Users\\leon\\Downloads\\mypalantir-gs\\.cursor\\debug.log"),
                    ("{\"location\":\"RelNodeBuilder.java:2672\",\"message\":\"After SQL validation\",\"data\":{\"validatedNode\":\"" + validatedNode.toString().replace("\"", "'") + "\",\"success\":true},\"timestamp\":" + System.currentTimeMillis() + ",\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"B\"}\n").getBytes(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {}
            // #endregion
            
            // 转换为 RelNode
            SqlToRelConverter.Config converterConfig = SqlToRelConverter.config()
                .withTrimUnusedFields(false)
                .withExpand(false);
            
            SqlToRelConverter converter = new SqlToRelConverter(
                null,  // RelOptTable.ViewExpander - not needed for basic queries
                validator,
                catalogReader,
                relBuilder.getCluster(),
                frameworkConfig.getConvertletTable(),
                converterConfig
            );
            
            RelNode relNode = converter.convertQuery(validatedNode, false, true).rel;
            return relNode;
            
        } catch (Exception e) {
            System.err.println("[buildSubqueryRelNode] Error parsing subquery SQL: " + subquerySql);
            System.err.println("[buildSubqueryRelNode] Error: " + e.getMessage());
            throw e;
        }
    }
}

