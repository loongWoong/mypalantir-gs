# Neo4j存储架构重构方案

## 1. 问题分析

### 1.1 当前架构问题

当前系统将所有模型实例数据完整存储在Neo4j中，存在以下问题：

1. **异构转换问题**：当接入ETL作为同步单元后，需要将关系型数据库的数据转换为Neo4j格式，存在数据转换和同步的复杂性
2. **数据冗余**：实例的详细数据既在关系型数据库中，又在Neo4j中存储，造成数据冗余
3. **同步维护成本**：需要维护两套数据的一致性，增加了系统复杂度
4. **性能问题**：Neo4j存储大量详细数据，影响图查询性能

### 1.2 当前存储架构

```
关系型数据库 (RDBMS)
    ↓ (ETL同步)
Neo4j (完整实例数据 + Links)
    ↓
前端展示
```

**问题**：数据在Neo4j中完整存储，但详细数据应该从关系型数据库查询

## 2. 目标架构

### 2.1 新架构设计

```
关系型数据库 (RDBMS)
    ├─ 实例详细数据 (通过ETL同步或手动抽取)
    └─ 通过QueryService查询详细数据
    
Neo4j (图数据库)
    ├─ Links关系 (source_id, target_id, link_type, properties)
    └─ 节点关键展示信息 (id, name, type等用于图展示的字段)
    
ETL系统
    ├─ 从关系型数据库抽取数据
    └─ 调用API获取/更新Links关系
```

### 2.2 数据分层

1. **实例详细数据层**：存储在关系型数据库中，通过现有的QueryService查询
2. **图关系层**：存储在Neo4j中，只包含：
   - Links关系（source_id, target_id, link_type, properties）
   - 节点关键字段（id, name, display_name等用于图展示的字段）

## 3. 改造方案

### 3.1 存储层重构

#### 3.1.1 创建混合存储实现

**新增类：`HybridInstanceStorage`**

```java
public class HybridInstanceStorage implements IInstanceStorage {
    // 关系型数据库存储（详细数据）
    private RelationalInstanceStorage relationalStorage;
    
    // Neo4j存储（关键字段和关系）
    private Neo4jInstanceStorage neo4jStorage;
    
    // 配置：哪些字段存储在Neo4j中（用于图展示）
    private Map<String, List<String>> neo4jFieldsConfig;
}
```

**职责分离：**
- `RelationalInstanceStorage`：负责实例详细数据的CRUD，通过QueryService查询关系型数据库
- `Neo4jInstanceStorage`：只存储关键展示字段和Links关系

#### 3.1.2 实例数据存储策略

**创建实例时：**
1. 详细数据存储到关系型数据库（通过ETL或手动插入）
2. 关键展示字段同步到Neo4j（id, name等）
3. Links关系存储到Neo4j

**查询实例时：**
1. 优先从关系型数据库查询详细数据
2. 如果关系型数据库没有，从Neo4j查询（兼容旧数据）
3. 合并关键展示字段（从Neo4j获取，用于图展示）

#### 3.1.3 Links关系存储

**保持不变**：Links关系继续存储在Neo4j中，因为图查询是Neo4j的核心能力

### 3.2 接口设计

#### 3.2.1 ETL调用接口

**新增Controller：`ETLLinkController`**

```java
@RestController
@RequestMapping("/api/v1/etl/links")
public class ETLLinkController {
    
    /**
     * 获取指定对象类型的所有Links关系
     * 供ETL系统调用，用于同步关系
     */
    @GetMapping("/{linkType}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLinks(
            @PathVariable String linkType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String targetType);
    
    /**
     * 批量创建Links关系
     * 供ETL系统调用，用于批量同步关系
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createLinksBatch(
            @RequestBody List<LinkCreateRequest> requests);
    
    /**
     * 根据属性匹配获取Links关系
     * 供ETL系统调用，用于基于属性匹配创建关系
     */
    @PostMapping("/match")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> matchLinks(
            @RequestBody LinkMatchRequest request);
}
```

#### 3.2.2 实例查询接口增强

**修改`InstanceController`：**

```java
@GetMapping("/{objectType}/{id}")
public ResponseEntity<ApiResponse<Map<String, Object>>> getInstance(
        @PathVariable String objectType,
        @PathVariable String id,
        @RequestParam(defaultValue = "true") boolean includeDetails) {
    // includeDetails=true: 从关系型数据库查询详细数据
    // includeDetails=false: 只返回Neo4j中的关键字段
}
```

### 3.3 服务层改造

#### 3.3.1 InstanceService改造

**修改`InstanceService`：**

```java
@Service
public class InstanceService {
    private final IInstanceStorage storage; // 使用HybridInstanceStorage
    private final QueryService queryService; // 用于查询关系型数据库
    
    /**
     * 获取实例（优先从关系型数据库查询）
     */
    public Map<String, Object> getInstance(String objectType, String id, boolean includeDetails) {
        if (includeDetails) {
            // 1. 尝试从关系型数据库查询
            try {
                return queryService.queryInstanceById(objectType, id);
            } catch (Exception e) {
                // 2. 如果失败，从Neo4j查询（兼容旧数据）
                return storage.getInstance(objectType, id);
            }
        } else {
            // 只返回Neo4j中的关键字段
            return storage.getInstanceSummary(objectType, id);
        }
    }
    
    /**
     * 创建实例（分离详细数据和关键字段）
     */
    public String createInstance(String objectType, Map<String, Object> data) {
        // 1. 提取关键字段（用于Neo4j存储）
        Map<String, Object> summaryFields = extractSummaryFields(objectType, data);
        
        // 2. 详细数据存储到关系型数据库（通过ETL或直接插入）
        // 这里需要根据配置决定是直接插入还是通知ETL
        
        // 3. 关键字段存储到Neo4j
        return storage.createInstance(objectType, summaryFields);
    }
}
```

#### 3.3.2 新增RelationalInstanceStorage

**新增类：`RelationalInstanceStorage`**

```java
public class RelationalInstanceStorage implements IInstanceStorage {
    private final QueryService queryService;
    private final DatabaseMetadataService databaseMetadataService;
    private final MappingService mappingService;
    
    /**
     * 通过QueryService从关系型数据库查询实例
     */
    @Override
    public Map<String, Object> getInstance(String objectType, String id) {
        // 构建查询：SELECT * FROM table WHERE id = ?
        // 使用QueryService执行查询
    }
    
    /**
     * 创建实例到关系型数据库
     */
    @Override
    public String createInstance(String objectType, Map<String, Object> data) {
        // 根据Mapping配置，插入到对应的关系型数据库表
    }
}
```

### 3.4 配置设计

#### 3.4.1 存储配置

**新增配置项：`application.properties`**

```properties
# 存储模式：hybrid（混合模式）
storage.type=hybrid

# Neo4j存储的字段配置（按对象类型）
storage.neo4j.fields.person=id,name,display_name,avatar
storage.neo4j.fields.company=id,name,industry,logo

# 默认关系型数据库使用env配置的数据库（db.host, db.port, db.name等）
# 无需额外配置，系统会自动使用application.properties中的db.*配置

# ETL同步配置
etl.sync.enabled=true
etl.sync.mode=auto  # auto: 自动同步, manual: 手动同步
```

#### 3.4.2 对象类型配置增强

**Ontology YAML配置：**

```yaml
object_types:
  - name: person
    properties:
      - name: id
        type: string
      - name: name
        type: string
        neo4j_field: true  # 标记为Neo4j存储字段
      - name: email
        type: string
        neo4j_field: false  # 详细数据，存储在关系型数据库
    data_source:
      database_id: default
      table_name: persons
```

## 4. 实施步骤

### 阶段1：基础架构搭建（1-2周）

1. **创建RelationalInstanceStorage**
   - 实现基本的CRUD操作
   - 通过QueryService查询关系型数据库
   - 通过MappingService获取表映射信息

2. **创建HybridInstanceStorage**
   - 组合RelationalInstanceStorage和Neo4jInstanceStorage
   - 实现数据路由逻辑（关系型数据库优先）
   - 实现字段分离逻辑（关键字段vs详细字段）

3. **修改StorageFactory**
   - 支持hybrid存储模式
   - 根据配置选择存储实现

### 阶段2：服务层改造（1周）

1. **修改InstanceService**
   - 支持从关系型数据库查询详细数据
   - 支持字段分离存储
   - 保持向后兼容

2. **增强QueryService**
   - 添加按ID查询实例的方法
   - 支持实例创建和更新操作

### 阶段3：ETL接口开发（1周）

1. **创建ETLLinkController**
   - 提供Links关系查询接口
   - 提供批量创建Links接口
   - 提供关系匹配接口

2. **创建ETLLinkService**
   - 实现Links关系的批量操作
   - 实现基于属性匹配的关系创建

### 阶段4：配置和迁移（1周）

1. **配置管理**
   - 添加Neo4j字段配置
   - 添加ETL同步配置
   - 更新Ontology配置格式

2. **数据迁移（可选）**
   - 如果已有Neo4j数据，需要迁移详细数据到关系型数据库
   - 保留Neo4j中的关键字段和Links关系

### 阶段5：测试和优化（1周）

1. **单元测试**
   - 测试HybridInstanceStorage
   - 测试ETL接口
   - 测试数据路由逻辑

2. **集成测试**
   - 测试ETL同步流程
   - 测试查询性能
   - 测试数据一致性

3. **性能优化**
   - 优化关系型数据库查询
   - 优化Neo4j查询（只查询关键字段）
   - 添加缓存机制（可选）

## 5. 技术细节

### 5.1 字段分离策略

**关键字段提取逻辑：**

```java
private Map<String, Object> extractSummaryFields(String objectType, Map<String, Object> data) {
    // 1. 从配置中获取该对象类型需要存储在Neo4j的字段
    List<String> neo4jFields = getNeo4jFields(objectType);
    
    // 2. 提取关键字段
    Map<String, Object> summary = new HashMap<>();
    summary.put("id", data.get("id")); // id必须包含
    for (String field : neo4jFields) {
        if (data.containsKey(field)) {
            summary.put(field, data.get(field));
        }
    }
    
    return summary;
}
```

### 5.2 数据查询路由

**查询优先级：**

```java
public Map<String, Object> getInstance(String objectType, String id) {
    // 1. 检查对象类型是否配置了关系型数据库映射
    ObjectType objectTypeDef = loader.getObjectType(objectType);
    if (objectTypeDef.getDataSource() != null && objectTypeDef.getDataSource().isConfigured()) {
        try {
            // 从关系型数据库查询
            return relationalStorage.getInstance(objectType, id);
        } catch (Exception e) {
            // 如果失败，回退到Neo4j（兼容旧数据）
            logger.warn("Failed to query from relational DB, falling back to Neo4j", e);
        }
    }
    
    // 2. 从Neo4j查询（兼容旧数据或未配置映射的对象类型）
    return neo4jStorage.getInstance(objectType, id);
}
```

### 5.3 Links关系同步

**ETL调用流程：**

```
1. ETL从关系型数据库抽取数据
2. ETL调用 /api/v1/etl/links/match 接口，基于属性匹配查找关系
3. ETL调用 /api/v1/etl/links/batch 接口，批量创建Links关系
4. Links关系存储到Neo4j
```

## 6. 风险和注意事项

### 6.1 兼容性风险

- **旧数据兼容**：需要支持从Neo4j查询旧数据，逐步迁移
- **API兼容**：保持现有API接口不变，内部实现切换

### 6.2 性能风险

- **查询性能**：关系型数据库查询可能比Neo4j慢，需要优化
- **同步延迟**：ETL同步可能存在延迟，需要考虑最终一致性

### 6.3 数据一致性

- **双写问题**：需要确保关系型数据库和Neo4j的数据一致性
- **事务处理**：跨数据库事务需要特殊处理

## 7. 后续优化方向

1. **缓存机制**：添加Redis缓存，提高查询性能
2. **异步同步**：ETL同步改为异步，提高响应速度
3. **数据版本管理**：支持数据版本和历史记录
4. **监控和告警**：添加数据同步监控和告警机制

## 8. 总结

本方案通过分离实例详细数据和图关系数据，解决了异构转换问题，提高了系统的可维护性和性能。关键点：

1. **数据分层**：详细数据在关系型数据库，关系数据在Neo4j
2. **接口设计**：提供ETL专用接口，支持批量操作
3. **向后兼容**：支持旧数据查询，平滑迁移
4. **配置驱动**：通过配置灵活控制存储策略

