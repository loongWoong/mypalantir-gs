package com.mypalantir.service;

import com.mypalantir.config.Config;
import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.repository.IInstanceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * ETL模型构建服务
 * 根据本体模型与物理表的mapping映射关系，构建scheduler模块所需的ETL模型定义数据
 */
@Service
public class EtlModelBuilderService {
    
    @Autowired
    private MappingService mappingService;
    
    @Autowired
    private DatasourceIntegrationService datasourceIntegrationService;
    
    @Autowired
    private IInstanceStorage instanceStorage;
    
    @Autowired
    private Loader loader;
    
    @Autowired
    private Config config;
    
    /**
     * 为指定的对象类型构建 ETL 模型定义数据
     * 
     * @param objectType 对象类型名称
     * @param mappingId 映射关系ID（可选，如果不提供则使用第一个映射）
     * @param targetDatasourceId 目标数据源ID（可选，如果不指定则使用源数据源）
     * @param targetTableName 目标表名（可选，默认：{objectType}_sync）
     * @return ETL模型Map，包含 nodes、edges、frontScript 等
     */
    public Map<String, Object> buildEtlModel(
        String objectType, 
        String mappingId,
        String targetDatasourceId, 
        String targetTableName
    ) throws Exception {
        
        // 1. 获取 mapping 关系
        Map<String, Object> mapping;
        if (mappingId != null && !mappingId.isEmpty()) {
            mapping = mappingService.getMapping(mappingId);
        } else {
            List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectType);
            if (mappings == null || mappings.isEmpty()) {
                throw new IllegalArgumentException("No mapping found for object type: " + objectType);
            }
            mapping = mappings.get(0);
        }
        
        String tableId = (String) mapping.get("table_id");
        String sourceTableName = (String) mapping.get("table_name");
        @SuppressWarnings("unchecked")
        Map<String, String> columnPropertyMappings = (Map<String, String>) mapping.get("column_property_mappings");
        String primaryKeyColumn = (String) mapping.get("primary_key_column");
        
        // 2. 获取表信息
        Map<String, Object> table = instanceStorage.getInstance("table", tableId);
        String databaseId = (String) table.get("database_id");
        
        // 3. 获取数据库信息，获取外部数据源ID
        Map<String, Object> database = instanceStorage.getInstance("database", databaseId);
        String sourceDatasourceId = (String) database.get("database_id"); // 外部ETL工具的数据源ID
        
        if (sourceDatasourceId == null || sourceDatasourceId.isEmpty()) {
            throw new IllegalArgumentException("Database " + databaseId + " does not have database_id configured. " +
                "Please configure database_id in the database instance to link with external ETL tool.");
        }
        
        // 4. 获取源表字段信息
        List<Map<String, Object>> sourceFields = datasourceIntegrationService.getTableFieldInfo(
            Long.parseLong(sourceDatasourceId), sourceTableName);
        
        // 5. 确定目标数据源和表
        // 重要提示：
        // - 目标数据源应该对应默认数据库（application.properties中的db.*配置）
        // - 这样查询时（RelationalInstanceStorage）才能正确找到ETL同步的数据
        // - 如果目标数据源不是默认数据库，需要在dome-datasource中配置对应的数据源ID
        String targetDsId;
        if (targetDatasourceId != null && !targetDatasourceId.isEmpty()) {
            // 使用指定的目标数据源
            targetDsId = targetDatasourceId;
        } else {
            // 目标数据源不存在时，优先使用配置的默认数据源
            String defaultTargetDsId = config.getDefaultTargetDatasourceId();
            if (defaultTargetDsId != null && !defaultTargetDsId.isEmpty()) {
                targetDsId = defaultTargetDsId;
            } else {
                // 如果默认数据源也未配置，则使用源数据源
                targetDsId = sourceDatasourceId;
            }
        }
        // 默认目标表名：使用 objectType.toLowerCase()，与 Instances 同步抽取保持一致
        // 这样查询时（RelationalInstanceStorage.listInstances）能够正确找到 ETL 同步的数据
        // 注意：之前使用 objectType.toLowerCase() + "_sync" 会导致查询不到数据
        String targetTable = targetTableName != null && !targetTableName.isEmpty() 
            ? targetTableName 
            : objectType.toLowerCase();
        
        // 6. 获取目标表字段信息（如果目标表已存在）
        List<Map<String, Object>> targetFields = null;
        try {
            targetFields = datasourceIntegrationService.getTableFieldInfo(
                Long.parseLong(targetDsId), targetTable);
        } catch (Exception e) {
            // 目标表不存在，使用源表字段信息构建
            targetFields = sourceFields;
        }
        
        // 7. 获取对象类型定义
        ObjectType objectTypeDef = loader.getObjectType(objectType);
        
        // 8. 获取数据源信息（用于获取数据源名称和类型）
        Map<String, Object> datasourceInfo = datasourceIntegrationService.getDatasourceById(Long.parseLong(sourceDatasourceId));
        
        // 9. 构建 ETL nodes
        List<Map<String, Object>> nodes = buildEtlNodes(
            sourceDatasourceId, sourceTableName, sourceFields,
            targetDsId, targetTable, targetFields,
            objectTypeDef, columnPropertyMappings, primaryKeyColumn,
            datasourceInfo
        );
        
        // 10. 构建 ETL edges
        List<Map<String, Object>> edges = buildEtlEdges(nodes);
        
        // 11. 构建 frontScript（原始脚本，用于前端展示）
        Map<String, Object> frontScript = buildFrontScript(nodes, edges);
        
        // 12. 构建 ETL 模型 Map
        Map<String, Object> etlModel = new HashMap<>();
        // 生成唯一ID（使用时间戳）
        etlModel.put("id", String.valueOf(System.currentTimeMillis()));
        etlModel.put("name", objectType.toLowerCase());
        etlModel.put("description", "Auto-generated ETL model for " + objectType + " based on mapping relationship");
        etlModel.put("engine", "FLINK");
        etlModel.put("jobMode", "BATCH");
        etlModel.put("etlScript", objectType.toLowerCase());
        etlModel.put("nodes", nodes);
        etlModel.put("edges", edges);
        etlModel.put("frontScript", frontScript);
        etlModel.put("environment", buildEnvironmentConfig());
        
        return etlModel;
    }
    
    /**
     * 构建 ETL nodes
     */
    private List<Map<String, Object>> buildEtlNodes(
        String sourceDatasourceId, String sourceTableName, 
        List<Map<String, Object>> sourceFields,
        String targetDatasourceId, String targetTableName,
        List<Map<String, Object>> targetFields,
        ObjectType objectType, 
        Map<String, String> columnPropertyMappings,
        String primaryKeyColumn,
        Map<String, Object> datasourceInfo
    ) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        
        // 生成唯一的表名（用于 result_table_name）
        String resultTableName = sourceTableName.toLowerCase() + "_" + System.currentTimeMillis();
        
        // 1. Source Node (源表节点)
        Map<String, Object> sourceNode = buildSourceNode(
            sourceDatasourceId, sourceTableName, sourceFields, 
            resultTableName, datasourceInfo
        );
        nodes.add(sourceNode);
        
        // 2. Sink Node (目标表节点)
        Map<String, Object> sinkNode = buildSinkNode(
            targetDatasourceId, targetTableName, targetFields,
            sourceFields, columnPropertyMappings, primaryKeyColumn,
            resultTableName, datasourceInfo
        );
        nodes.add(sinkNode);
        
        return nodes;
    }
    
    /**
     * 构建源表节点（SOURCE）
     */
    private Map<String, Object> buildSourceNode(
        String datasourceId, String tableName, 
        List<Map<String, Object>> fields,
        String resultTableName,
        Map<String, Object> datasourceInfo
    ) {
        Map<String, Object> node = new HashMap<>();
        String nodeId = UUID.randomUUID().toString();
        String portId = UUID.randomUUID().toString();
        
        node.put("_order", 0);
        node.put("code", generateCode());
        node.put("component", "SOURCE");
        node.put("condition", Map.of("children", Collections.emptyList()));
        node.put("datasourceId", datasourceId);
        // 确保datasourceName不为null，如果获取不到则使用空字符串
        Object datasourceNameObj = datasourceInfo != null ? datasourceInfo.get("name") : null;
        String datasourceName = datasourceNameObj != null ? datasourceNameObj.toString() : "";
        node.put("datasourceName", datasourceName);
        Object datasourceTypeObj = datasourceInfo != null ? datasourceInfo.get("type") : null;
        String datasourceType = datasourceTypeObj != null ? datasourceTypeObj.toString() : "";
        node.put("datasourceType", datasourceType);
        node.put("existingCustomParam", Collections.emptyMap());
        node.put("fieldNames", Collections.emptyList());
        node.put("fields", convertFieldsToEtlFormat(fields));
        node.put("frontFields", buildFrontFields(fields));
        node.put("frontTables", null);
        node.put("height", 36);
        node.put("id", nodeId);
        node.put("inputType", 2);
        node.put("isAdd", 1);
        node.put("isHidden", false);
        node.put("label", "1");
        node.put("module", "JDBC");
        node.put("multiTable", false);
        node.put("nodeType", "SOURCE");
        node.put("parentId", "1");
        node.put("partitionColumn", null);
        node.put("popoverContent", buildPopoverContent("输入组件", "JDBC"));
        node.put("ports", List.of(buildPort(portId, "bottom", "输出桩", "input")));
        node.put("properties", null);
        node.put("query", "");
        node.put("renderKey", "DND_NDOE");
        node.put("resultTableName", resultTableName);
        node.put("rules", null);
        node.put("splitStringSplitMode", "sample");
        node.put("switchSql", false);
        node.put("tableList", Collections.emptyList());
        node.put("tableName", tableName);
        node.put("tableStructure", 1);
        node.put("taskName", "1");
        node.put("taskType", "SEATUNNEL");
        node.put("type", "JDBC_SOURCE");
        node.put("width", 180);
        node.put("x", 80);
        node.put("y", 50);
        
        return node;
    }
    
    /**
     * 构建目标表节点（SINK）
     */
    private Map<String, Object> buildSinkNode(
        String datasourceId, String tableName,
        List<Map<String, Object>> targetFields,
        List<Map<String, Object>> sourceFields,
        Map<String, String> columnPropertyMappings,
        String primaryKeyColumn,
        String sourceResultTableName,
        Map<String, Object> datasourceInfo
    ) {
        Map<String, Object> node = new HashMap<>();
        String nodeId = UUID.randomUUID().toString();
        String portId = UUID.randomUUID().toString();
        String sourceNodeId = ""; // 会在 buildEtlEdges 中设置
        
        // 构建字段映射
        List<Map<String, Object>> fieldMapping = buildFieldMapping(
            sourceResultTableName, sourceFields, columnPropertyMappings);
        
        // 构建主键列表
        List<String> primaryKeys = new ArrayList<>();
        if (primaryKeyColumn != null && !primaryKeyColumn.isEmpty()) {
            // 查找主键列对应的属性名，保持原始列名的大小写
            String foundKey = null;
            for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(primaryKeyColumn)) {
                    // 优先使用属性名，但保持列名的大小写
                    foundKey = primaryKeyColumn.toUpperCase();
                    break;
                }
            }
            if (foundKey != null) {
                primaryKeys.add(foundKey);
            } else {
                // 如果没找到映射，直接使用主键列名（保持原始大小写）
                primaryKeys.add(primaryKeyColumn.toUpperCase());
            }
        }
        // 如果没有找到，尝试从源字段中查找主键
        if (primaryKeys.isEmpty()) {
            for (Map<String, Object> field : sourceFields) {
                String fieldName = (String) (field.get("fieldName") != null ? field.get("fieldName") : 
                                           field.get("name") != null ? field.get("name") : 
                                           field.get("columnName") != null ? field.get("columnName") : "");
                Object pkObj = field.get("isPrimaryKey") != null ? field.get("isPrimaryKey") : 
                              field.get("primaryKey") != null ? field.get("primaryKey") : 
                              field.get("is_primary_key") != null ? field.get("is_primary_key") : false;
                boolean isPrimaryKey = pkObj instanceof Boolean ? (Boolean) pkObj : 
                                      pkObj instanceof Number ? ((Number) pkObj).intValue() != 0 : false;
                if (isPrimaryKey && fieldName != null && !fieldName.isEmpty()) {
                    primaryKeys.add(fieldName.toUpperCase());
                    break;
                }
            }
        }
        // 如果还是没有找到，使用 ID（大写）
        if (primaryKeys.isEmpty()) {
            primaryKeys.add("ID");
        }
        
        node.put("_order", 0);
        node.put("code", generateCode());
        node.put("component", "SINK");
        node.put("createIndex", false);
        node.put("customSql", null);
        node.put("dataSaveMode", null);
        node.put("schemaSaveMode", "RECREATE_SCHEMA");
        node.put("datasourceId", datasourceId);
        // 确保datasourceName不为null，如果获取不到则使用空字符串
        Object datasourceNameObj = datasourceInfo != null ? datasourceInfo.get("name") : null;
        String datasourceName = datasourceNameObj != null ? datasourceNameObj.toString() : "";
        node.put("datasourceName", datasourceName);
        node.put("enableUpsert", true);
        node.put("existingCustomParam", Collections.emptyMap());
        node.put("fieldMapping", fieldMapping);
        node.put("generateSinkSql", true);
        node.put("height", 36);
        node.put("id", nodeId);
        node.put("ignorePostSqlExceptions", null);
        node.put("ignoreUpdateBefore", true);
        node.put("isAdd", 1);
        node.put("label", "2");
        node.put("module", "JDBC");
        node.put("nodeType", "SINK");
        node.put("outputType", 2);
        node.put("parentId", "2");
        node.put("popoverContent", buildPopoverContent("输出组件", "JDBC"));
        node.put("ports", List.of(buildPort(portId, "top", "输入桩", "input")));
        node.put("preTask", sourceNodeId); // 会在 buildEtlEdges 后设置
        node.put("primaryKeys", primaryKeys);
        node.put("properties", null);
        node.put("renderKey", "DND_NDOE");
        node.put("sourceFieldData", buildSourceFieldData(sourceResultTableName, sourceFields));
        node.put("sourceTableName", sourceResultTableName);
        node.put("subjectTableName", null);
        node.put("supportUpdateBeforeToDelete", false);
        node.put("switchSql", false);
        // SINK节点的tableName应该为null（根据成功数据）
        node.put("tableName", null);
        node.put("targetFieldData", convertFieldsToEtlFormat(targetFields));
        node.put("taskName", "2");
        node.put("taskType", "SEATUNNEL");
        node.put("type", "JDBC_SINK");
        node.put("useCopyStatement", false);
        node.put("width", 180);
        node.put("x", 80);
        node.put("y", 180);
        
        return node;
    }
    
    /**
     * 构建字段映射（SINK节点的fieldMapping）
     */
    private List<Map<String, Object>> buildFieldMapping(
        String sourceResultTableName,
        List<Map<String, Object>> sourceFields,
        Map<String, String> columnPropertyMappings
    ) {
        List<Map<String, Object>> fieldMapping = new ArrayList<>();
        
        for (Map<String, Object> field : sourceFields) {
            // 适配不同的字段名格式
            String columnName = (String) (field.get("fieldName") != null ? field.get("fieldName") : 
                                         field.get("name") != null ? field.get("name") : 
                                         field.get("columnName") != null ? field.get("columnName") : "");
            
            if (columnName == null || columnName.isEmpty()) {
                continue;
            }
            
            // 查找对应的属性名
            String propertyName = null;
            for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
                if (entry.getValue().equals(columnName)) {
                    propertyName = entry.getKey();
                    break;
                }
            }
            // 如果没有找到映射，使用列名
            if (propertyName == null) {
                propertyName = columnName;
            }
            
            Map<String, Object> mapping = new HashMap<>();
            mapping.put("sourceField", sourceResultTableName + "." + columnName);
            mapping.put("targetField", propertyName);
            fieldMapping.add(mapping);
        }
        
        return fieldMapping;
    }
    
    /**
     * 构建 frontFields（SOURCE节点的frontFields）
     */
    private List<Map<String, Object>> buildFrontFields(List<Map<String, Object>> fields) {
        List<Map<String, Object>> frontFields = new ArrayList<>();
        
        for (Map<String, Object> field : fields) {
            // 适配不同的字段名格式
            String fieldName = (String) (field.get("fieldName") != null ? field.get("fieldName") : 
                                        field.get("name") != null ? field.get("name") : 
                                        field.get("columnName") != null ? field.get("columnName") : "");
            
            if (fieldName == null || fieldName.isEmpty()) {
                continue;
            }
            
            Map<String, Object> frontField = new HashMap<>();
            frontField.put("sourceField", fieldName);
            frontField.put("targetField", fieldName);
            frontFields.add(frontField);
        }
        
        return frontFields;
    }
    
    /**
     * 构建 sourceFieldData（SINK节点的sourceFieldData）
     */
    private List<Map<String, Object>> buildSourceFieldData(
        String sourceResultTableName, List<Map<String, Object>> sourceFields) {
        List<Map<String, Object>> sourceFieldData = new ArrayList<>();
        
        for (Map<String, Object> field : sourceFields) {
            // 适配不同的字段名格式
            String fieldName = (String) (field.get("fieldName") != null ? field.get("fieldName") : 
                                        field.get("name") != null ? field.get("name") : 
                                        field.get("columnName") != null ? field.get("columnName") : "");
            
            if (fieldName == null || fieldName.isEmpty()) {
                continue;
            }
            
            Map<String, Object> fieldData = new HashMap<>();
            fieldData.put("children", null);
            fieldData.put("text", fieldName);
            fieldData.put("value", sourceResultTableName + "." + fieldName);
            sourceFieldData.add(fieldData);
        }
        
        return sourceFieldData;
    }
    
    /**
     * 将字段信息转换为ETL格式
     * 注意：dome-datasource 返回的字段信息格式可能不同，需要适配
     */
    private List<Map<String, Object>> convertFieldsToEtlFormat(List<Map<String, Object>> fields) {
        List<Map<String, Object>> etlFields = new ArrayList<>();
        
        for (Map<String, Object> field : fields) {
            Map<String, Object> etlField = new HashMap<>();
            etlField.put("aggregationType", "");
            etlField.put("description", field.get("description") != null ? field.get("description") : "");
            
            // 适配不同的字段名格式
            String dataType = (String) (field.get("dataType") != null ? field.get("dataType") : 
                                       field.get("data_type") != null ? field.get("data_type") : 
                                       field.get("type") != null ? field.get("type") : "varchar");
            String fieldName = (String) (field.get("name") != null ? field.get("name") : 
                                        field.get("fieldName") != null ? field.get("fieldName") : 
                                        field.get("columnName") != null ? field.get("columnName") : "");
            
            etlField.put("domeDataType", convertDataTypeToDomeType(dataType));
            
            // 处理 nullable
            Object nullableObj = field.get("nullable") != null ? field.get("nullable") : 
                                field.get("isNullable") != null ? field.get("isNullable") : 
                                field.get("empty") != null ? field.get("empty") : true;
            boolean nullable = nullableObj instanceof Boolean ? (Boolean) nullableObj : 
                             nullableObj instanceof Number ? ((Number) nullableObj).intValue() != 0 : true;
            etlField.put("empty", nullable ? 1 : 0);
            
            etlField.put("fieldCode", fieldName);
            
            // 处理字段长度
            Object lengthObj = field.get("columnSize") != null ? field.get("columnSize") : 
                             field.get("length") != null ? field.get("length") : 
                             field.get("size") != null ? field.get("size") : 
                             field.get("fieldLength") != null ? field.get("fieldLength") : null;
            int length = 0;
            if (lengthObj != null) {
                if (lengthObj instanceof Number) {
                    length = ((Number) lengthObj).intValue();
                } else if (lengthObj instanceof String) {
                    try {
                        length = Integer.parseInt((String) lengthObj);
                    } catch (NumberFormatException e) {
                        length = 0;
                    }
                }
            }
            // 如果长度为0，根据字段类型设置默认值
            if (length == 0 && (dataType.contains("varchar") || dataType.contains("char"))) {
                length = 200; // varchar类型默认长度200
            }
            etlField.put("fieldLength", length);
            
            etlField.put("fieldName", fieldName);
            
            // 处理精度
            Object precisionObj = field.get("decimalDigits") != null ? field.get("decimalDigits") : 
                                field.get("precision") != null ? field.get("precision") : 
                                field.get("fieldPrecision") != null ? field.get("fieldPrecision") : null;
            // 如果精度为0或null，设置为null（与成功数据保持一致）
            Object precision = null;
            if (precisionObj != null) {
                if (precisionObj instanceof Number) {
                    int precValue = ((Number) precisionObj).intValue();
                    precision = precValue != 0 ? precValue : null;
                } else if (precisionObj instanceof String) {
                    try {
                        int precValue = Integer.parseInt((String) precisionObj);
                        precision = precValue != 0 ? precValue : null;
                    } catch (NumberFormatException e) {
                        precision = null;
                    }
                }
            }
            etlField.put("fieldPrecision", precision);
            
            etlField.put("fieldType", dataType);
            etlField.put("foreignKey", 0);
            etlField.put("foreignKeyName", "");
            etlField.put("foreignTable", "");
            etlField.put("foreignTableField", "");
            
            // 处理主键
            Object pkObj = field.get("isPrimaryKey") != null ? field.get("isPrimaryKey") : 
                          field.get("primaryKey") != null ? field.get("primaryKey") : 
                          field.get("is_primary_key") != null ? field.get("is_primary_key") : false;
            boolean isPrimaryKey = pkObj instanceof Boolean ? (Boolean) pkObj : 
                                  pkObj instanceof Number ? ((Number) pkObj).intValue() != 0 : false;
            etlField.put("primaryKey", isPrimaryKey ? 1 : 0);
            
            etlField.put("unique", 0);
            etlFields.add(etlField);
        }
        
        return etlFields;
    }
    
    /**
     * 转换数据类型为Dome类型
     */
    private String convertDataTypeToDomeType(String dataType) {
        if (dataType == null) return "STRING";
        
        String upperType = dataType.toUpperCase();
        if (upperType.contains("INT")) {
            return "INT";  // 注意：成功的数据中使用的是 "INT" 而不是 "INTEGER"
        } else if (upperType.contains("DECIMAL") || upperType.contains("NUMERIC") || 
                   upperType.contains("FLOAT") || upperType.contains("DOUBLE")) {
            return "DOUBLE";
        } else if (upperType.contains("DATE") || upperType.contains("TIME")) {
            return "DATE";
        } else if (upperType.contains("BOOLEAN") || upperType.contains("BOOL")) {
            return "BOOLEAN";
        } else {
            return "STRING";
        }
    }
    
    /**
     * 构建 ETL edges（节点连接关系）
     */
    private List<Map<String, Object>> buildEtlEdges(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> edges = new ArrayList<>();
        
        if (nodes.size() < 2) {
            return edges;
        }
        
        Map<String, Object> sourceNode = nodes.get(0);
        Map<String, Object> sinkNode = nodes.get(1);
        
        String sourceId = (String) sourceNode.get("id");
        String sinkId = (String) sinkNode.get("id");
        
        // 获取端口ID
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sourcePorts = (List<Map<String, Object>>) sourceNode.get("ports");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sinkPorts = (List<Map<String, Object>>) sinkNode.get("ports");
        
        String sourcePortId = sourcePorts != null && !sourcePorts.isEmpty() 
            ? (String) sourcePorts.get(0).get("id") 
            : UUID.randomUUID().toString();
        String sinkPortId = sinkPorts != null && !sinkPorts.isEmpty() 
            ? (String) sinkPorts.get(0).get("id") 
            : UUID.randomUUID().toString();
        
        // 设置 SINK 节点的 preTask
        sinkNode.put("preTask", sourceId);
        
        // 构建 edge
        Map<String, Object> edge = new HashMap<>();
        edge.put("connector", Map.of("name", "rounded"));
        edge.put("id", UUID.randomUUID().toString());
        edge.put("router", Map.of("name", "manhattan"));
        edge.put("source", sourceId);
        edge.put("sourcePort", sourcePortId);
        edge.put("sourcePortId", sourcePortId);
        edge.put("target", sinkId);
        edge.put("targetPort", sinkPortId);
        edge.put("targetPortId", sinkPortId);
        
        edges.add(edge);
        
        return edges;
    }
    
    /**
     * 构建 frontScript（原始脚本，用于前端展示）
     */
    private Map<String, Object> buildFrontScript(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Object> frontScript = new HashMap<>();
        Map<String, Object> originalScript = new HashMap<>();
        originalScript.put("nodes", nodes);
        originalScript.put("edges", edges);
        frontScript.put("originalScript", originalScript);
        return frontScript;
    }
    
    /**
     * 构建环境配置
     */
    private Map<String, Object> buildEnvironmentConfig() {
        Map<String, Object> env = new HashMap<>();
        env.put("jobMode", "BATCH");
        env.put("properties", Collections.emptyMap());
        env.put("jobName", "ETL Job");
        return env;
    }
    
    /**
     * 构建端口
     */
    private Map<String, Object> buildPort(String id, String group, String tooltip, String type) {
        Map<String, Object> port = new HashMap<>();
        port.put("group", group);
        port.put("id", id);
        port.put("tooltip", tooltip);
        port.put("type", type);
        return port;
    }
    
    /**
     * 构建 PopoverContent
     */
    private Map<String, Object> buildPopoverContent(String description, String name) {
        Map<String, Object> popoverContent = new HashMap<>();
        popoverContent.put("_owner", null);
        popoverContent.put("_store", Collections.emptyMap());
        popoverContent.put("key", null);
        Map<String, Object> props = new HashMap<>();
        props.put("_nk", "Us/z2" + (name.equals("JDBC") ? "1" : "v"));
        props.put("description", description);
        props.put("name", name);
        popoverContent.put("props", props);
        popoverContent.put("ref", null);
        return popoverContent;
    }
    
    /**
     * 生成随机code
     */
    private long generateCode() {
        return Math.abs(new Random().nextLong() % 1000000000000L);
    }
}

