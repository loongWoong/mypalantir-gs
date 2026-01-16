# MyPalantir 项目功能实现逻辑分析

## 目录

1. [项目概述](#项目概述)
2. [系统架构](#系统架构)
3. [对象实例列表查询显示逻辑](#对象实例列表查询显示逻辑)
4. [核心组件分析](#核心组件分析)
5. [数据流分析](#数据流分析)
6. [查询路径分析](#查询路径分析)

---

## 项目概述

MyPalantir 是一个基于 Ontology（本体）的数据模型管理平台，通过 Ontology 抽象层实现业务概念与物理数据源的解耦。系统提供统一的查询接口和语义化的数据访问能力。

### 核心特性

- **Ontology 驱动的数据模型**：将业务概念与物理存储解耦
- **统一查询接口**：基于 OntologyQuery DSL 的查询语言
- **多数据源支持**：支持 JDBC 数据库、文件系统等多种数据源
- **关系抽象**：通过 LinkType 抽象对象间的关系
- **工作空间管理**：支持对象类型和关系类型的分组管理

---

## 系统架构

### 整体架构图

```plantuml
@startuml 系统架构图
!theme plain
skinparam componentStyle rectangle

package "前端层 (Frontend)" {
  [React UI] as ReactUI
  [InstanceList 组件] as InstanceList
  [InstanceDetail 组件] as InstanceDetail
  [API Client] as APIClient
}

package "API 层 (REST API)" {
  [InstanceController] as Controller
  [QueryController] as QueryController
}

package "服务层 (Service Layer)" {
  [InstanceService] as InstanceService
  [QueryService] as QueryService
  [MappedDataService] as MappedDataService
}

package "查询引擎层 (Query Engine)" {
  [QueryParser] as QueryParser
  [QueryExecutor] as QueryExecutor
  [RelNodeBuilder] as RelNodeBuilder
  [OntologyRelToSqlConverter] as SQLConverter
}

package "存储层 (Storage Layer)" {
  [InstanceStorage] as InstanceStorage
  [FileSystemStorage] as FileStorage
  [Neo4jStorage] as Neo4jStorage
}

package "数据源层 (Data Source)" {
  [JDBC Database] as Database
  [File System] as FileSystem
}

ReactUI --> InstanceList
InstanceList --> APIClient
APIClient --> Controller
Controller --> InstanceService
Controller --> QueryController
QueryController --> QueryService
InstanceService --> QueryService
InstanceService --> MappedDataService
InstanceService --> InstanceStorage
QueryService --> QueryParser
QueryService --> QueryExecutor
QueryExecutor --> RelNodeBuilder
QueryExecutor --> SQLConverter
MappedDataService --> Database
QueryExecutor --> Database
InstanceStorage --> FileStorage
InstanceStorage --> Neo4jStorage
FileStorage --> FileSystem

@enduml
```

### 技术栈

- **后端**：Java 17 + Spring Boot 3.2.0 + Apache Calcite 1.37.0
- **前端**：React 18 + TypeScript + Vite + Tailwind CSS
- **查询引擎**：Apache Calcite（关系代数优化）
- **存储**：文件系统（JSON）、Neo4j、JDBC 数据库

---

## 对象实例列表查询显示逻辑

### 功能概述

对象实例列表查询显示是系统的核心功能之一，支持多种查询模式：

1. **本地实例查询**：从文件系统或 Neo4j 查询已存储的实例
2. **映射数据查询**：通过数据映射配置从数据库实时查询
3. **OntologyQuery 查询**：使用统一的查询 DSL 进行查询
4. **筛选查询**：支持多条件筛选

### 查询流程 UML 序列图

```plantuml
@startuml 实例列表查询序列图
!theme plain
autonumber

actor 用户 as User
participant "InstanceList\n(React)" as Frontend
participant "API Client" as Client
participant "InstanceController" as Controller
participant "InstanceService" as Service
participant "QueryService" as QueryService
participant "MappedDataService" as MappedService
participant "QueryExecutor" as Executor
participant "InstanceStorage" as Storage
database "Database" as DB

User -> Frontend: 访问实例列表页面
activate Frontend

Frontend -> Frontend: useEffect 触发
Frontend -> Frontend: loadData()

alt 映射数据查询（使用 mappingId）
    note right: 点击"映射数据查询"按钮\nqueryMode = 'mapping'
    Frontend -> Client: instanceApi.listWithMapping(objectType, mappingId, offset, limit)
    Client -> Controller: GET /instances/{objectType}?mappingId=xxx
    Controller -> MappedService: queryMappedInstances(objectType, mappingId, offset, limit)
    activate MappedService
    MappedService -> Storage: getMapping(mappingId)
    MappedService -> Storage: getInstance("table", tableId)
    MappedService -> DB: 执行 SQL 查询
    DB --> MappedService: 返回数据库行
    MappedService -> MappedService: 转换为实例对象
    MappedService --> Controller: ListResult
    deactivate MappedService
    Controller --> Client: API Response
    Client --> Frontend: { items, total }
    
else 实例存储查询（使用 instance）
    note right: 点击"实例存储查询"按钮\nqueryMode = 'storage'
    alt 优先使用 OntologyQuery
    Frontend -> Client: queryApi.execute(queryRequest)
    Client -> Controller: POST /api/v1/query
    Controller -> QueryService: executeQuery(queryMap)
    activate QueryService
    QueryService -> QueryService: parseMap(queryMap)
    QueryService -> Executor: execute(query)
    activate Executor
    Executor -> Executor: buildRelNode(query)
    Executor -> Executor: optimizeRelNode(relNode)
    Executor -> Executor: convertToSql(relNode)
    Executor -> DB: 执行 SQL
    DB --> Executor: 查询结果
    Executor -> Executor: 映射列名到属性名
    Executor --> QueryService: QueryResult
    deactivate Executor
    QueryService --> Controller: QueryResult
    deactivate QueryService
    Controller --> Client: API Response
    Client --> Frontend: { rows, rowCount }
    Frontend -> Frontend: 转换为 Instance[] 格式
    
    else 回退到直接 API 调用
        Frontend -> Client: instanceApi.list(objectType, offset, limit, filters)
        Client -> Controller: GET /instances/{objectType}?offset=0&limit=20&...
        Controller -> Service: listInstances(objectType, offset, limit, filters)
        activate Service
        
        alt 有数据源映射
            Service -> QueryService: executeQuery(queryMap)
            QueryService -> Executor: execute(query)
            Executor -> DB: 执行 SQL
            DB --> Executor: 查询结果
            Executor --> QueryService: QueryResult
            QueryService --> Service: QueryResult
        else 使用文件系统存储
            Service -> Storage: listInstances(objectType, offset, limit)
            Storage --> Service: ListResult
        end
        
        Service --> Controller: ListResult
        deactivate Service
        Controller --> Client: API Response
        Client --> Frontend: { items, total }
    end
end

Frontend -> Frontend: setInstances(instances)
Frontend -> Frontend: setTotal(total)
Frontend -> Frontend: 渲染表格
Frontend --> User: 显示实例列表

@enduml
```

### 前端组件结构

```plantuml
@startuml 前端组件结构图
!theme plain
skinparam componentStyle rectangle

package "InstanceList 组件" {
  component [InstanceList] as MainComponent
  component [状态管理] as State
  component [数据加载] as DataLoad
  component [筛选功能] as Filter
  component [分页功能] as Pagination
  component [同步功能] as Sync
}

component [InstanceForm] as Form
component [DataMappingDialog] as MappingDialog
component [ToastContainer] as Toast

note right of State
  状态管理:
  - instances: Instance[]
  - objectTypeDef: ObjectType
  - loading: boolean
  - offset: number
  - total: number
  - filters: Filter[]
  - fromMapping: boolean
end note

note right of DataLoad
  数据加载:
  + loadData()
  - 构建查询请求
  - 执行查询
  - 处理结果
end note

note right of Filter
  筛选功能:
  + handleAddFilter()
  + handleRemoveFilter()
  + handleFilterChange()
  + handleClearFilters()
end note

note right of Pagination
  分页功能:
  + setOffset()
  + Previous/Next 按钮
end note

note right of Sync
  同步功能:
  + handleSyncClick()
  + handleSyncExtractClick()
  + handleExtract()
end note

MainComponent --> State
MainComponent --> DataLoad
MainComponent --> Filter
MainComponent --> Pagination
MainComponent --> Sync
MainComponent --> Form
MainComponent --> MappingDialog
MainComponent --> Toast

@enduml
```

### 查询路径决策树

```plantuml
@startuml 查询路径决策树
!theme plain

start

:用户访问实例列表;

if (点击"映射数据查询"按钮\nqueryMode === 'mapping'?) then (是)
  :使用 mappingId
  通过 MappedDataService
  直接查询数据库;
  :返回映射数据;
  stop
else (否 - 点击"实例存储查询"按钮\nqueryMode === 'storage')
  if (使用 OntologyQuery?) then (是)
    :构建 QueryRequest;
    :调用 queryApi.execute();
    if (查询成功?) then (是)
      :转换结果为 Instance[];
      :显示结果;
      stop
    else (否)
      :回退到直接 API;
    endif
  endif
  
  if (对象类型有数据源映射?) then (是)
    :使用 QueryService
    从数据库查询;
    :执行 SQL;
    :返回结果;
    stop
  else (否)
    :使用 InstanceStorage
    从文件系统查询;
    if (有筛选条件?) then (是)
      :searchInstances();
    else (否)
      :listInstances();
    endif
    :返回结果;
    stop
  endif
endif

@enduml
```

---

## 核心组件分析

### InstanceService 类图

```plantuml
@startuml InstanceService 类图
!theme plain

class InstanceService {
  - IInstanceStorage storage
  - Loader loader
  - DataValidator validator
  - QueryService queryService
  --
  + createInstance(objectType, data): String
  + getInstance(objectType, id): Map
  + updateInstance(objectType, id, data): void
  + deleteInstance(objectType, id): void
  + listInstances(objectType, offset, limit, filters): ListResult
  + getInstancesBatch(objectType, ids): Map
  + getInstancesBatchMultiType(typeIdMap): Map
  --
  - listInstancesFromDataSource(objectType, offset, limit, filters): ListResult
  - listInstancesFromFileSystem(objectType, offset, limit, filters): ListResult
}

class QueryService {
  - Loader loader
  - QueryParser parser
  - IInstanceStorage instanceStorage
  - QueryExecutor executor
  --
  + executeQuery(queryMap): QueryResult
  --
  - validateQuery(query): void
}

class MappedDataService {
  - MappingService mappingService
  - DatabaseMetadataService databaseMetadataService
  - IInstanceStorage instanceStorage
  - Loader loader
  --
  + queryMappedInstances(objectType, mappingId, offset, limit): ListResult
  + syncMappedDataToInstances(objectType, mappingId): void
  --
  - buildSelectQuery(tableName, mappings, offset, limit): String
}

class QueryExecutor {
  - Loader loader
  - RelNodeBuilder relNodeBuilder
  - OntologySchemaFactory schemaFactory
  --
  + execute(query): QueryResult
  --
  - buildRelNode(query): RelNode
  - optimizeRelNode(relNode): RelNode
  - executeRelNode(relNode, query): QueryResult
  - executeSql(sql, query, objectType, mapping): QueryResult
}

interface IInstanceStorage {
  + createInstance(objectType, data): String
  + getInstance(objectType, id): Map
  + updateInstance(objectType, id, data): void
  + deleteInstance(objectType, id): void
  + listInstances(objectType, offset, limit): ListResult
  + searchInstances(objectType, filters): List<Map>
}

class FileSystemInstanceStorage {
  + createInstance(objectType, data): String
  + getInstance(objectType, id): Map
  + listInstances(objectType, offset, limit): ListResult
  + searchInstances(objectType, filters): List<Map>
}

class Neo4jInstanceStorage {
  + createInstance(objectType, data): String
  + getInstance(objectType, id): Map
  + listInstances(objectType, offset, limit): ListResult
}

InstanceService --> IInstanceStorage
InstanceService --> QueryService
InstanceService --> Loader
InstanceService --> DataValidator
QueryService --> QueryExecutor
QueryService --> QueryParser
MappedDataService --> MappingService
MappedDataService --> DatabaseMetadataService
QueryExecutor --> RelNodeBuilder
QueryExecutor --> OntologySchemaFactory
IInstanceStorage <|.. FileSystemInstanceStorage
IInstanceStorage <|.. Neo4jInstanceStorage

@enduml
```

### 前端 InstanceList 组件状态图

```plantuml
@startuml InstanceList 状态图
!theme plain

[*] --> 初始化

初始化 --> 加载中: useEffect 触发

加载中 --> 获取对象类型定义: schemaApi.getObjectType()

获取对象类型定义 --> 判断查询模式

判断查询模式 --> 映射查询: mappingId 存在
判断查询模式 --> OntologyQuery查询: 优先使用
判断查询模式 --> 直接API查询: 回退方案

映射查询 --> 执行数据库查询
执行数据库查询 --> 转换实例对象
转换实例对象 --> 显示结果

OntologyQuery查询 --> 构建QueryRequest
构建QueryRequest --> 执行查询
执行查询 --> 成功: 查询成功
执行查询 --> 失败: 查询失败
成功 --> 转换结果
失败 --> 直接API查询
转换结果 --> 显示结果

直接API查询 --> 检查数据源映射
检查数据源映射 --> 数据库查询: 有映射
检查数据源映射 --> 文件系统查询: 无映射
数据库查询 --> 显示结果
文件系统查询 --> 显示结果

显示结果 --> 等待用户操作

等待用户操作 --> 筛选: 用户添加筛选
等待用户操作 --> 分页: 用户切换页面
等待用户操作 --> 创建: 用户创建实例
等待用户操作 --> 编辑: 用户编辑实例
等待用户操作 --> 删除: 用户删除实例
等待用户操作 --> 同步: 用户同步数据

筛选 --> 加载中: 重置offset并重新加载
分页 --> 加载中: 更新offset并重新加载
创建 --> 显示表单
编辑 --> 显示表单
删除 --> 加载中: 删除后刷新
同步 --> 加载中: 同步后刷新
显示表单 --> 等待用户操作: 表单关闭

@enduml
```

---

## 数据流分析

### 数据流转图

```plantuml
@startuml 数据流转图
!theme plain

package "前端数据流" {
  [用户输入] as UserInput
  [React State] as ReactState
  [API 请求] as APIRequest
}

package "后端处理流" {
  [Controller] as Controller
  [Service Layer] as Service
  [Query Engine] as QueryEngine
  [Storage Layer] as Storage
}

package "数据源" {
  database "Database" as DB
  storage "File System" as FS
  database "Neo4j" as Neo4j
}

UserInput --> ReactState: 1. 用户操作触发状态更新
ReactState --> APIRequest: 2. 构建 API 请求
APIRequest --> Controller: 3. HTTP 请求

Controller --> Service: 4. 调用服务层
Service --> QueryEngine: 5. 需要查询引擎
Service --> Storage: 6. 直接存储操作

QueryEngine --> DB: 7. 执行 SQL
QueryEngine --> Storage: 8. 查询元数据
Storage --> FS: 9. 文件系统操作
Storage --> Neo4j: 10. Neo4j 操作

DB --> QueryEngine: 11. 返回查询结果
FS --> Storage: 12. 返回文件数据
Neo4j --> Storage: 13. 返回图数据

QueryEngine --> Service: 14. 返回 QueryResult
Storage --> Service: 15. 返回 ListResult

Service --> Controller: 16. 返回处理结果
Controller --> APIRequest: 17. HTTP 响应
APIRequest --> ReactState: 18. 更新组件状态
ReactState --> UserInput: 19. 渲染 UI

@enduml
```

### 查询结果转换流程

```plantuml
@startuml 查询结果转换流程
!theme plain

start

:数据库查询结果
(Map<String, Object>);

:获取 DataSourceMapping;

:遍历查询结果行;

repeat
  :读取一行数据;
  :获取列名;
  
  :查找列名到属性名的映射;
  
  if (映射存在?) then (是)
    :使用映射的属性名;
  else (否)
    :直接使用列名;
  endif
  
  :构建实例对象;
  :设置 id 字段;
  :设置属性值;
  
repeat while (还有更多行?)

:转换为 Instance[] 数组;

:返回给前端;

stop

@enduml
```

---

## 查询路径分析

### 三种查询模式对比

| 查询模式 | 触发条件 | 数据来源 | 查询方式 | 查询参数 | 适用场景 |
|---------|---------|---------|---------|---------|---------|
| **映射数据查询** | 点击"映射数据查询"按钮，`queryMode === 'mapping'` | 外部数据库 | 使用 `mappingId` 通过 `listWithMapping` API | `mappingId` | 实时查看数据库数据 |
| **实例存储查询** | 点击"实例存储查询"按钮，`queryMode === 'storage'` | 本地实例存储 | 使用 `instance` 通过 OntologyQuery 或直接 API | `instance` (objectType) | 查询本地存储的实例数据 |
| **OntologyQuery** | 实例存储查询模式下的优先方案 | 数据库/文件系统 | 通过查询引擎 | `objectType` | 复杂查询、关联查询 |
| **直接 API** | OntologyQuery 失败或回退 | 文件系统/数据库 | 存储层直接查询 | `objectType` | 简单查询、本地数据 |

### 查询路径详细分析

#### 1. 映射查询路径（Mapping Query）

```plantuml
@startuml 映射查询路径
!theme plain

participant Frontend
participant Controller
participant MappedDataService
participant MappingService
participant InstanceStorage
participant DatabaseMetadataService
database Database

Frontend -> Controller: GET /instances/{objectType}?mappingId=xxx
Controller -> MappedDataService: queryMappedInstances()
MappedDataService -> MappingService: getMapping(mappingId)
MappingService --> MappedDataService: mapping配置
MappedDataService -> InstanceStorage: getInstance("table", tableId)
InstanceStorage --> MappedDataService: table信息
MappedDataService -> MappedDataService: 构建SQL查询
MappedDataService -> DatabaseMetadataService: executeQuery(sql, databaseId)
DatabaseMetadataService -> Database: 执行SQL
Database --> DatabaseMetadataService: 查询结果
DatabaseMetadataService --> MappedDataService: 数据库行
MappedDataService -> MappedDataService: 转换为实例对象
note right: 使用 column_property_mappings\n映射列名到属性名
MappedDataService --> Controller: ListResult
Controller --> Frontend: API Response

@enduml
```

**特点**：
- **查询参数**：使用 `mappingId` 作为查询参数
- **触发方式**：点击"映射数据查询"按钮，设置 `queryMode = 'mapping'`
- **查询方式**：调用 `instanceApi.listWithMapping(objectType, mappingId, offset, limit)`
- **数据来源**：直接从数据库查询，不经过查询引擎
- **映射转换**：使用映射配置的 `column_property_mappings` 进行列名到属性名的转换
- **主键支持**：支持主键列映射，使用主键值作为实例 ID
- **实时性**：实时查询，数据始终是最新的

#### 2. OntologyQuery 查询路径

```plantuml
@startuml OntologyQuery查询路径
!theme plain

participant Frontend
participant QueryController
participant QueryService
participant QueryParser
participant QueryExecutor
participant RelNodeBuilder
participant OntologyRelToSqlConverter
database Database

Frontend -> QueryController: POST /api/v1/query
QueryController -> QueryService: executeQuery(queryMap)
QueryService -> QueryParser: parseMap(queryMap)
QueryParser --> QueryService: OntologyQuery对象
QueryService -> QueryExecutor: execute(query)
QueryExecutor -> RelNodeBuilder: buildRelNode(query)
RelNodeBuilder --> QueryExecutor: RelNode (关系代数树)
QueryExecutor -> QueryExecutor: optimizeRelNode(relNode)
QueryExecutor -> OntologyRelToSqlConverter: convertToSql(relNode)
OntologyRelToSqlConverter --> QueryExecutor: SQL语句
QueryExecutor -> Database: 执行SQL
Database --> QueryExecutor: 查询结果
QueryExecutor -> QueryExecutor: 映射列名到属性名
QueryExecutor --> QueryService: QueryResult
QueryService --> QueryController: QueryResult
QueryController --> Frontend: API Response

@enduml
```

**特点**：
- **查询参数**：使用 `instance` (objectType) 作为查询参数
- **触发方式**：点击"实例存储查询"按钮，设置 `queryMode = 'storage'`，优先使用 OntologyQuery
- **查询方式**：调用 `queryApi.execute(queryRequest)` 或回退到 `instanceApi.list()`
- **查询引擎**：使用 Apache Calcite 进行查询优化
- **功能支持**：支持复杂的关联查询、聚合查询
- **自动处理**：自动处理 JOIN 逻辑
- **操作支持**：支持筛选、排序、分页等操作
- **映射机制**：通过 DataSourceMapping 自动映射属性名到列名

#### 3. 直接 API 查询路径

```plantuml
@startuml 直接API查询路径
!theme plain

participant Frontend
participant Controller
participant InstanceService
participant QueryService
participant QueryExecutor
participant InstanceStorage
database Database
database FileSystem

Frontend -> Controller: GET /instances/{objectType}?offset=0&limit=20
Controller -> InstanceService: listInstances(objectType, offset, limit, filters)

alt 对象类型有数据源映射
  InstanceService -> QueryService: executeQuery(queryMap)
  QueryService -> QueryExecutor: execute(query)
  QueryExecutor -> Database: 执行SQL
  Database --> QueryExecutor: 查询结果
  QueryExecutor --> QueryService: QueryResult
  QueryService --> InstanceService: QueryResult
else 使用文件系统存储
  alt 有筛选条件
    InstanceService -> InstanceStorage: searchInstances(objectType, filters)
    InstanceStorage -> FileSystem: 读取JSON文件
    FileSystem --> InstanceStorage: 文件数据
    InstanceStorage -> InstanceStorage: 应用筛选条件
    InstanceStorage -> InstanceStorage: 分页处理
  else 无筛选条件
    InstanceService -> InstanceStorage: listInstances(objectType, offset, limit)
    InstanceStorage -> FileSystem: 读取JSON文件
    FileSystem --> InstanceStorage: 文件数据
    InstanceStorage -> InstanceStorage: 分页处理
  end
  InstanceStorage --> InstanceService: ListResult
end

InstanceService --> Controller: ListResult
Controller --> Frontend: API Response

@enduml
```

**特点**：
- **查询参数**：使用 `instance` (objectType) 作为查询参数
- **触发方式**：实例存储查询模式下，OntologyQuery 失败时的回退方案
- **查询方式**：调用 `instanceApi.list(objectType, offset, limit, filters)`
- **存储选择**：根据对象类型是否有数据源映射选择不同的存储后端
- **功能支持**：文件系统存储支持筛选和分页
- **统一处理**：数据库存储通过 QueryService 统一处理
- **回退机制**：作为 OntologyQuery 失败时的回退方案

---

## 关键实现细节

### 1. 查询模式切换

前端支持两种查询模式的切换：

```typescript
// 查询模式状态
const [queryMode, setQueryMode] = useState<'mapping' | 'storage'>('storage');

// 映射数据查询：使用 mappingId
if (queryMode === 'mapping' && availableMappings.length > 0) {
  const targetMappingId = mappingId || availableMappings[0]?.id;
  const instancesData = await instanceApi.listWithMapping(objectType, targetMappingId, offset, limit);
  // 使用 mappingId 查询数据库
}

// 实例存储查询：使用 instance
if (queryMode === 'storage') {
  // 优先使用 OntologyQuery
  const queryResult = await queryApi.execute(queryRequest);
  // 或回退到直接 API
  const instancesData = await instanceApi.list(objectType, offset, limit, filters);
}
```

**重要说明**：
- **映射数据查询**：点击"映射数据查询"按钮 → 使用 `mappingId` 查询数据库
- **实例存储查询**：点击"实例存储查询"按钮 → 使用 `instance` (objectType) 查询本地存储

### 2. 筛选功能实现

前端筛选功能支持多条件组合：

```typescript
// 筛选条件结构
interface Filter {
  property: string;  // 属性名
  value: string;      // 筛选值
}

// 筛选条件转换为查询参数
filters.forEach(filter => {
  if (filter.property && filter.value) {
    filterParams[filter.property] = filter.value;
  }
});

// OntologyQuery 格式
const queryFilters: Array<[string, string, any]> = [];
Object.entries(filterParams).forEach(([key, value]) => {
  queryFilters.push(['=', key, value]);
});
```

### 3. 分页实现

```typescript
// 前端分页状态
const [offset, setOffset] = useState(0);
const limit = 20;

// 分页控制
<button onClick={() => setOffset(Math.max(0, offset - limit))}>
  Previous
</button>
<button onClick={() => setOffset(offset + limit)}>
  Next
</button>
```

### 4. 数据源映射检测

```java
// InstanceService.java
ObjectType objectTypeDef = loader.getObjectType(objectType);

// 检查是否有数据源映射
if (objectTypeDef.getDataSource() != null && 
    objectTypeDef.getDataSource().isConfigured()) {
    // 使用查询 API 从数据库获取数据
    return listInstancesFromDataSource(objectType, offset, limit, filters);
} else {
    // 使用文件系统存储
    return listInstancesFromFileSystem(objectType, offset, limit, filters);
}
```

### 5. 查询结果映射

```java
// QueryExecutor.java - 执行SQL后映射结果
for (Map<String, Object> row : dbRows) {
    Map<String, Object> instance = new HashMap<>();
    
    // 映射列名到属性名
    for (Map.Entry<String, String> entry : columnPropertyMappings.entrySet()) {
        String columnName = entry.getKey();
        String propertyName = entry.getValue();
        if (row.containsKey(columnName)) {
            instance.put(propertyName, row.get(columnName));
        }
    }
    
    instances.add(instance);
}
```

---

## 总结

### 核心设计模式

1. **策略模式**：根据不同的查询条件选择不同的查询策略（映射查询、OntologyQuery、直接API）
2. **适配器模式**：通过 DataSourceMapping 适配不同的数据源
3. **工厂模式**：OntologySchemaFactory 创建 Calcite Schema
4. **模板方法模式**：QueryExecutor 定义查询执行模板

### 性能优化点

1. **查询优化**：使用 Apache Calcite 进行 SQL 优化
2. **批量查询**：支持批量获取实例，减少 HTTP 请求
3. **分页查询**：避免一次性加载大量数据
4. **缓存机制**：Schema 定义缓存，减少重复加载

### 扩展性设计

1. **多存储后端**：支持文件系统、Neo4j、JDBC 数据库
2. **插件化查询引擎**：可以扩展不同的查询执行器
3. **灵活的数据映射**：支持多种映射模式（外键模式、关系表模式）
4. **工作空间隔离**：支持多工作空间，实现数据隔离

---

## 附录

### 相关文件清单

**前端文件**：
- `web/src/pages/InstanceList.tsx` - 实例列表页面组件
- `web/src/pages/InstanceDetail.tsx` - 实例详情页面组件
- `web/src/api/client.ts` - API 客户端定义

**后端文件**：
- `src/main/java/com/mypalantir/controller/InstanceController.java` - 实例控制器
- `src/main/java/com/mypalantir/service/InstanceService.java` - 实例服务
- `src/main/java/com/mypalantir/service/MappedDataService.java` - 映射数据服务
- `src/main/java/com/mypalantir/service/QueryService.java` - 查询服务
- `src/main/java/com/mypalantir/query/QueryExecutor.java` - 查询执行器

### 关键配置

- **数据源映射配置**：`ontology/schema.yaml` 中的 `data_source` 配置
- **映射关系配置**：通过 Mapping API 创建的数据映射
- **工作空间配置**：`ontology/schema-system.yaml` 中的工作空间定义

---

*文档生成时间：2024年*
*项目版本：基于当前代码库分析*
