# ETL模型集成实现方案（优化版）

## 一、需求分析

### 1.1 目标
基于 mypalantir-gs 项目中的：
- 数据源管理模块（通过 dome-datasource 接口）
- 本体模型与物理表的 mapping 关系

在 Instances 页面添加"ETL数据同步"按钮，点击后自动构建 ETL 模型定义数据（nodes、edges、frontFields等），并调用 dome-scheduler 的 ETL 定义接口创建 ETL 模型。

### 1.2 关键变更
1. **database 对象新增 database_id 属性**：用于关联外部 ETL 工具模块（dome-datasource）的数据源ID
2. **前端添加 ETL 数据同步按钮**：在同步数据按钮旁边添加
3. **后端构建 ETL 模型**：根据 mapping 关系自动构建符合 dome-scheduler 要求的 ETL 模型结构

### 1.2 核心组件

#### 数据源管理模块 (dome-datasource)
- **接口位置**: `DatasourceController.java`
- **关键接口**:
  - `GET /datasourceManager/list` - 获取数据源列表
  - `GET /datasourceManager/showTables?datasourceId={id}` - 获取数据源表列表
  - `GET /datasourceManager/getFieldInfo?datasourceId={id}&tableName={name}` - 获取表字段信息
  - `GET /datasourceManager/getIndexType?datasourceId={id}` - 获取支持的索引类型（第352行）

#### ETL模型构建 (dome-scheduler)
- **接口位置**: `EtlDefinitionController.java:73-85`
- **接口**: `POST /etlDefinition`
- **参数格式**: `Map<String, Object>` 包含：
  - `name`: ETL模型名称
  - `description`: 描述
  - `engineType`: 引擎类型（如 "SeaTunnel"）
  - `nodes`: 节点列表（源表、目标表、转换节点等）
  - `edges`: 边列表（节点之间的连接关系）
  - `environment`: 环境配置

#### mypalantir-gs Mapping 关系
- **存储位置**: `mapping` 对象类型
- **关键字段**:
  - `object_type`: 本体对象类型名称
  - `table_id`: 物理表ID
  - `table_name`: 物理表名
  - `column_property_mappings`: 列到属性的映射关系（JSON格式）
  - `primary_key_column`: 主键列名

## 二、实现方案

### 2.1 架构设计

```
┌─────────────────────────────────────────────────────────┐
│              mypalantir-gs 项目                          │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │  EtlModelBuilderService                          │  │
│  │  - 获取 mapping 关系                              │  │
│  │  - 调用数据源管理接口获取表结构信息                │  │
│  │  - 构建 ETL nodes 和 edges                        │  │
│  │  - 调用 ETL 定义接口创建模型                       │  │
│  └──────────────────────────────────────────────────┘  │
│                          │                               │
│                          ▼                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │  DatasourceIntegrationService                     │  │
│  │  - 封装 dome-datasource API 调用                  │  │
│  │  - 数据源信息获取                                 │  │
│  │  - 表结构信息获取                                 │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│          dome-datasource (数据源管理服务)                  │
│  - 数据源列表查询                                        │
│  - 表结构查询                                            │
│  - 字段信息查询                                          │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│          dome-scheduler (ETL调度服务)                       │
│  - ETL模型创建接口                                       │
│  - ETL脚本生成                                           │
└─────────────────────────────────────────────────────────┘
```

### 2.2 数据流转

```
1. 获取 Mapping 关系
   MappingService.getMappingsByObjectType(objectType)
   ↓
2. 获取表信息
   InstanceStorage.getInstance("table", tableId)
   ↓
3. 获取数据源ID
   table.get("database_id")
   ↓
4. 调用数据源管理接口获取表结构
   DatasourceController.getFieldInfo(datasourceId, tableName)
   ↓
5. 构建 ETL 模型
   - 源表节点（Source Table Node）
   - 字段映射转换节点（Transform Node）
   - 目标表节点（Sink Table Node）
   - 节点连接关系（Edges）
   ↓
6. 调用 ETL 定义接口
   EtlDefinitionController.submit(etlModelMap)
```

### 2.3 核心实现类

#### 2.3.1 DatasourceIntegrationService

**职责**: 封装与 dome-datasource 服务的交互

```java
@Service
public class DatasourceIntegrationService {
    
    @Value("${dome.datasource.base-url:http://localhost:8080}")
    private String datasourceBaseUrl;
    
    /**
     * 获取数据源列表
     */
    public List<BaseDatasourceDTO> getDatasourceList();
    
    /**
     * 根据数据源ID获取数据源详情
     */
    public BaseDatasourceDTO getDatasourceById(Long datasourceId);
    
    /**
     * 获取数据源的表列表
     */
    public List<String> getTableList(Long datasourceId);
    
    /**
     * 获取表的字段信息
     */
    public List<Map<String, Object>> getTableFieldInfo(Long datasourceId, String tableName);
    
    /**
     * 获取数据源支持的索引类型
     */
    public List<String> getSupportIndexType(Long datasourceId);
}
```

#### 2.3.2 EtlModelBuilderService

**职责**: 基于 mapping 关系构建 ETL 模型

```java
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
    
    /**
     * 为指定的对象类型构建 ETL 模型
     * 
     * @param objectType 对象类型名称
     * @param targetDatasourceId 目标数据源ID（可选，如果不指定则使用源数据源）
     * @param targetTableName 目标表名（可选，默认：{objectType}_sync）
     * @return ETL模型Map，可直接用于调用 EtlDefinitionController.submit()
     */
    public Map<String, Object> buildEtlModel(
        String objectType, 
        Long targetDatasourceId, 
        String targetTableName
    ) throws Exception {
        
        // 1. 获取 mapping 关系
        List<Map<String, Object>> mappings = mappingService.getMappingsByObjectType(objectType);
        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException("No mapping found for object type: " + objectType);
        }
        
        Map<String, Object> mapping = mappings.get(0);
        String tableId = (String) mapping.get("table_id");
        String tableName = (String) mapping.get("table_name");
        Map<String, String> columnPropertyMappings = 
            (Map<String, String>) mapping.get("column_property_mappings");
        String primaryKeyColumn = (String) mapping.get("primary_key_column");
        
        // 2. 获取表信息
        Map<String, Object> table = instanceStorage.getInstance("table", tableId);
        String databaseId = (String) table.get("database_id");
        
        // 3. 获取数据源ID（需要将 databaseId 转换为 dome-datasource 的数据源ID）
        Long sourceDatasourceId = convertDatabaseIdToDatasourceId(databaseId);
        
        // 4. 获取源表字段信息
        List<Map<String, Object>> sourceFields = 
            datasourceIntegrationService.getTableFieldInfo(sourceDatasourceId, tableName);
        
        // 5. 确定目标数据源和表
        Long targetDsId = targetDatasourceId != null ? targetDatasourceId : sourceDatasourceId;
        String targetTable = targetTableName != null ? targetTableName : objectType + "_sync";
        
        // 6. 获取对象类型定义
        ObjectType objectTypeDef = loader.getObjectType(objectType);
        
        // 7. 构建 ETL nodes
        List<Map<String, Object>> nodes = buildEtlNodes(
            sourceDatasourceId, tableName, sourceFields,
            targetDsId, targetTable,
            objectTypeDef, columnPropertyMappings, primaryKeyColumn
        );
        
        // 8. 构建 ETL edges
        List<Map<String, Object>> edges = buildEtlEdges(nodes);
        
        // 9. 构建 ETL 模型 Map
        Map<String, Object> etlModel = new HashMap<>();
        etlModel.put("name", "ETL_" + objectType);
        etlModel.put("description", "Auto-generated ETL model for " + objectType);
        etlModel.put("engineType", "SeaTunnel");
        etlModel.put("nodes", nodes);
        etlModel.put("edges", edges);
        etlModel.put("environment", buildEnvironmentConfig());
        
        return etlModel;
    }
    
    /**
     * 构建 ETL nodes
     */
    private List<Map<String, Object>> buildEtlNodes(
        Long sourceDatasourceId, String sourceTableName, 
        List<Map<String, Object>> sourceFields,
        Long targetDatasourceId, String targetTableName,
        ObjectType objectType, 
        Map<String, String> columnPropertyMappings,
        String primaryKeyColumn
    ) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        
        // 1. Source Node (源表节点)
        Map<String, Object> sourceNode = buildSourceNode(
            sourceDatasourceId, sourceTableName, sourceFields
        );
        nodes.add(sourceNode);
        
        // 2. Transform Node (字段映射转换节点)
        Map<String, Object> transformNode = buildTransformNode(
            objectType, columnPropertyMappings, primaryKeyColumn, sourceFields
        );
        nodes.add(transformNode);
        
        // 3. Sink Node (目标表节点)
        Map<String, Object> sinkNode = buildSinkNode(
            targetDatasourceId, targetTableName, objectType, sourceFields
        );
        nodes.add(sinkNode);
        
        return nodes;
    }
    
    /**
     * 构建源表节点
     */
    private Map<String, Object> buildSourceNode(
        Long datasourceId, String tableName, 
        List<Map<String, Object>> fields
    ) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", UUID.randomUUID().toString());
        node.put("type", "source");
        node.put("name", "Source_" + tableName);
        
        Map<String, Object> config = new HashMap<>();
        config.put("datasourceId", datasourceId);
        config.put("tableName", tableName);
        config.put("schema", buildSchemaFromFields(fields));
        
        node.put("config", config);
        return node;
    }
    
    /**
     * 构建转换节点
     */
    private Map<String, Object> buildTransformNode(
        ObjectType objectType,
        Map<String, String> columnPropertyMappings,
        String primaryKeyColumn,
        List<Map<String, Object>> sourceFields
    ) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", UUID.randomUUID().toString());
        node.put("type", "transform");
        node.put("name", "Transform_" + objectType.getName());
        
        Map<String, Object> config = new HashMap<>();
        
        // 构建字段映射规则
        List<Map<String, Object>> fieldMappings = new ArrayList<>();
        for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
            String propertyName = entry.getKey();
            String columnName = entry.getValue();
            
            Map<String, Object> mapping = new HashMap<>();
            mapping.put("source", columnName);
            mapping.put("target", propertyName);
            fieldMappings.add(mapping);
        }
        
        config.put("fieldMappings", fieldMappings);
        config.put("primaryKeyColumn", primaryKeyColumn);
        
        node.put("config", config);
        return node;
    }
    
    /**
     * 构建目标表节点
     */
    private Map<String, Object> buildSinkNode(
        Long datasourceId, String tableName,
        ObjectType objectType,
        List<Map<String, Object>> sourceFields
    ) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", UUID.randomUUID().toString());
        node.put("type", "sink");
        node.put("name", "Sink_" + tableName);
        
        Map<String, Object> config = new HashMap<>();
        config.put("datasourceId", datasourceId);
        config.put("tableName", tableName);
        config.put("schema", buildTargetSchema(objectType, sourceFields));
        
        node.put("config", config);
        return node;
    }
    
    /**
     * 构建 ETL edges（节点连接关系）
     */
    private List<Map<String, Object>> buildEtlEdges(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> edges = new ArrayList<>();
        
        // Source -> Transform
        Map<String, Object> edge1 = new HashMap<>();
        edge1.put("source", nodes.get(0).get("id"));
        edge1.put("target", nodes.get(1).get("id"));
        edges.add(edge1);
        
        // Transform -> Sink
        Map<String, Object> edge2 = new HashMap<>();
        edge2.put("source", nodes.get(1).get("id"));
        edge2.put("target", nodes.get(2).get("id"));
        edges.add(edge2);
        
        return edges;
    }
    
    /**
     * 将 databaseId 转换为 dome-datasource 的数据源ID
     * 需要根据实际的数据源管理方式实现
     */
    private Long convertDatabaseIdToDatasourceId(String databaseId) throws Exception {
        // 方案1: 如果 databaseId 就是数据源ID，直接转换
        // return Long.parseLong(databaseId);
        
        // 方案2: 通过查询数据库实例获取关联的数据源ID
        Map<String, Object> database = instanceStorage.getInstance("database", databaseId);
        String datasourceIdStr = (String) database.get("datasource_id");
        if (datasourceIdStr != null) {
            return Long.parseLong(datasourceIdStr);
        }
        
        // 方案3: 通过数据源名称或其他方式匹配
        // 需要根据实际业务逻辑实现
        
        throw new IllegalArgumentException("Cannot convert databaseId to datasourceId: " + databaseId);
    }
}
```

#### 2.3.3 EtlModelController

**职责**: 提供 REST API 接口

```java
@RestController
@RequestMapping("/api/v1/etl-model")
public class EtlModelController {
    
    @Autowired
    private EtlModelBuilderService etlModelBuilderService;
    
    @Autowired
    private EtlDefinitionIntegrationService etlDefinitionIntegrationService;
    
    /**
     * 为对象类型构建并创建 ETL 模型
     */
    @PostMapping("/build")
    public ResponseEntity<ApiResponse<Map<String, Object>>> buildEtlModel(
        @RequestParam String objectType,
        @RequestParam(required = false) Long targetDatasourceId,
        @RequestParam(required = false) String targetTableName
    ) {
        try {
            // 1. 构建 ETL 模型
            Map<String, Object> etlModel = etlModelBuilderService.buildEtlModel(
                objectType, targetDatasourceId, targetTableName
            );
            
            // 2. 调用 dome-scheduler 接口创建 ETL 定义
            Map<String, Object> result = etlDefinitionIntegrationService.createEtlDefinition(etlModel);
            
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Failed to build ETL model: " + e.getMessage()));
        }
    }
    
    /**
     * 批量构建 ETL 模型
     */
    @PostMapping("/build-batch")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> buildEtlModelsBatch(
        @RequestBody List<String> objectTypes,
        @RequestParam(required = false) Long targetDatasourceId
    ) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (String objectType : objectTypes) {
            try {
                Map<String, Object> etlModel = etlModelBuilderService.buildEtlModel(
                    objectType, targetDatasourceId, null
                );
                Map<String, Object> result = etlDefinitionIntegrationService.createEtlDefinition(etlModel);
                result.put("objectType", objectType);
                result.put("success", true);
                results.add(result);
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("objectType", objectType);
                errorResult.put("success", false);
                errorResult.put("error", e.getMessage());
                results.add(errorResult);
            }
        }
        
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
```

#### 2.3.4 EtlDefinitionIntegrationService

**职责**: 封装与 dome-scheduler 服务的交互

```java
@Service
public class EtlDefinitionIntegrationService {
    
    @Value("${dome.scheduler.base-url:http://localhost:8080}")
    private String schedulerBaseUrl;
    
    @Value("${dome.scheduler.project-code:1}")
    private Long projectCode;
    
    /**
     * 创建 ETL 定义
     */
    public Map<String, Object> createEtlDefinition(Map<String, Object> etlModel) {
        // 使用 RestTemplate 或 Feign Client 调用 dome-scheduler 接口
        String url = schedulerBaseUrl + "/etlDefinition";
        
        // 构建请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(etlModel, headers);
        
        // 发送请求
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        
        return response.getBody();
    }
}
```

### 2.4 配置项

在 `application.properties` 中添加：

```properties
# dome-datasource 服务地址
dome.datasource.base-url=http://localhost:8080

# dome-scheduler 服务地址
dome.scheduler.base-url=http://localhost:8080

# ETL 项目代码
dome.scheduler.project-code=1
```

## 三、实现步骤

### 步骤1: 创建数据源集成服务
1. 创建 `DatasourceIntegrationService`
2. 实现数据源管理接口的调用封装
3. 处理数据源ID与 databaseId 的转换逻辑

### 步骤2: 创建 ETL 模型构建服务
1. 创建 `EtlModelBuilderService`
2. 实现 mapping 关系获取逻辑
3. 实现 ETL nodes 和 edges 构建逻辑
4. 实现字段映射转换逻辑

### 步骤3: 创建 ETL 定义集成服务
1. 创建 `EtlDefinitionIntegrationService`
2. 实现与 dome-scheduler 的接口调用

### 步骤4: 创建 REST API 控制器
1. 创建 `EtlModelController`
2. 提供 ETL 模型构建和创建的接口

### 步骤5: 测试验证
1. 单元测试各个服务
2. 集成测试完整流程
3. 验证生成的 ETL 模型是否正确

## 四、关键问题与解决方案

### 4.1 数据源ID转换问题

**问题**: mypalantir-gs 使用 `database_id`，而 dome-datasource 使用 `datasourceId`（Long类型）

**解决方案**:
1. 在 `database` 对象中存储关联的 `datasource_id`
2. 或者通过数据源名称匹配
3. 或者建立映射表

### 4.2 字段类型映射问题

**问题**: 不同数据库的字段类型需要转换为统一的类型

**解决方案**:
- 参考 `MappedDataService.buildDataType()` 方法
- 建立类型映射表

### 4.3 ETL 节点 Schema 构建

**问题**: 需要根据源表字段和目标对象类型构建正确的 Schema

**解决方案**:
- 从源表字段信息中提取类型、长度等信息
- 根据对象类型定义确定目标字段类型
- 构建符合 SeaTunnel 格式的 Schema

### 4.4 目标表创建

**问题**: 目标表可能不存在，需要先创建

**解决方案**:
- 在构建 ETL 模型前，先检查目标表是否存在
- 如果不存在，调用数据源管理接口创建表
- 或者让 ETL 执行时自动创建

## 五、扩展功能

### 5.1 增量同步支持
- 基于时间戳字段的增量同步
- 支持全量和增量两种模式

### 5.2 数据转换规则
- 支持数据格式转换（日期格式、数值格式等）
- 支持字段计算和表达式

### 5.3 错误处理与重试
- 完善的错误处理机制
- 支持失败重试

### 5.4 监控与日志
- ETL 执行状态监控
- 详细的日志记录

## 六、使用示例

### 6.1 为单个对象类型构建 ETL 模型

```bash
POST /api/v1/etl-model/build?objectType=EntryTransaction&targetDatasourceId=2
```

### 6.2 批量构建 ETL 模型

```bash
POST /api/v1/etl-model/build-batch
Content-Type: application/json

["EntryTransaction", "TollStation", "Vehicle"]
```

## 七、后续优化方向

1. **可视化配置**: 提供前端界面配置 ETL 模型
2. **模板管理**: 支持 ETL 模型模板
3. **调度集成**: 与调度系统集成，自动执行 ETL
4. **性能优化**: 大批量数据的优化处理
5. **数据质量**: 集成数据质量检查

