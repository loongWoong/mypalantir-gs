# 功能变动和关键修改说明文档

## 文档概述

本文档详细记录了从提交 `4989e74c521b154c12ea922f2a737438e45141a8`（车辆-卡模型）之后的所有功能变动和关键修改内容。

**文档生成时间：** 2025-12-31  
**起始提交：** 4989e74c521b154c12ea922f2a737438e45141a8  
**最新提交：** 053c18ba5f26f0a36ecbe229fb8577178e6fa025

---

## 一、功能变动总览

### 1.1 核心功能模块

| 功能模块 | 提交版本 | 状态 | 说明 |
|---------|---------|------|------|
| 数据库集成 | 095e730 | ✅ 完成 | 支持外部数据库连接和数据同步 |
| 数据映射 | 095e730, c5b426f | ✅ 完成 | 数据库表字段与模型属性映射 |
| 工作空间管理 | 9a6dbfb | ✅ 完成 | 支持模型分组和过滤 |
| 关系自动同步 | 28f547d | ✅ 完成 | 基于属性映射的自动关系创建 |
| 批量数据获取 | 847094a | ✅ 完成 | 优化图形视图加载性能 |
| 血缘查询 | 053c18b | ✅ 完成 | 支持正向、反向、全链血缘查询 |
| UI 优化 | fc743af | ✅ 完成 | 支持中文显示名称 |

### 1.2 统计数据

- **总提交数：** 9 个功能提交
- **新增文件：** 15+ 个新文件
- **代码变更：** 约 8000+ 行新增代码
- **新增 API 接口：** 20+ 个
- **新增前端组件：** 5+ 个

---

## 二、详细功能变动

### 2.1 数据库集成与数据映射功能（提交 095e730）

**提交时间：** 2025-12-30 13:17:37  
**提交哈希：** 095e730947010b6939e9852779bccba4f3965bed

#### 功能描述

实现了完整的外部数据库集成功能，支持从 MySQL 数据库读取表结构信息，并将数据库表字段映射到本体模型的属性，实现数据的自动抽取和同步。

#### 关键修改

**后端修改：**

1. **依赖管理（pom.xml）**
   - 添加 `mysql-connector-j` (8.0.33) - MySQL JDBC 驱动
   - 添加 `dotenv-java` (3.0.0) - 环境变量管理

2. **配置管理**
   - **EnvConfig.java**（新增）：加载 `.env` 文件，支持环境变量配置
   - **DatabaseConfig.java**（新增）：数据库连接管理
   - **Config.java**：添加数据库连接相关配置属性
   - **application.properties**：添加数据库配置占位符

3. **元数据模型扩展（schema.yaml）**
   - 新增 `database` 对象类型：数据源定义
   - 新增 `table` 对象类型：数据库表定义
   - 新增 `column` 对象类型：表字段定义
   - 新增 `mapping` 对象类型：字段与属性映射关系
   - 新增关系类型：
     - `database_has_table`：数据源包含表
     - `table_has_column`：表包含列
     - `mapping_links_table`：映射关联表

4. **服务层（Service）**
   - **DatabaseService.java**（新增）：数据库实例管理
   - **DatabaseMetadataService.java**（新增）：数据库元数据查询
   - **MappingService.java**（新增）：映射关系管理
   - **MappedDataService.java**（新增）：基于映射的数据抽取
   - **TableSyncService.java**（新增）：表结构同步

5. **控制器层（Controller）**
   - **DatabaseController.java**（新增）：数据库相关 API
   - **MappingController.java**（新增）：映射相关 API
   - **InstanceController.java**：新增 `sync-from-mapping` 接口

**前端修改：**

1. **数据映射组件**
   - **DataMappingDialog.tsx**（新增）：数据映射对话框组件
   - **DataMapping.tsx**（新增）：数据映射页面

2. **API 客户端（client.ts）**
   - 新增 `databaseApi`：数据库相关 API
   - 新增 `mappingApi`：映射相关 API
   - 新增 `instanceApi.listWithMapping`：基于映射查询实例

3. **实例列表页面（InstanceList.tsx）**
   - 添加"关联数据源"按钮
   - 添加"同步抽取"按钮
   - 支持通过映射 ID 查询实例数据

#### 使用场景

1. **数据源配置**：在 Instances 页面为 `database` 对象类型创建数据源实例
2. **表结构同步**：在 `database` 实例列表页面点击"同步表信息"按钮，自动同步数据库中的表和字段信息
3. **数据映射**：在非系统对象类型的 Instances 页面点击"关联数据源"，选择数据源和表，手动建立字段与属性的映射关系
4. **数据抽取**：点击"同步抽取"按钮，根据映射关系从数据库抽取数据到模型实例

---

### 2.2 数据映射功能增强（提交 c5b426f）

**提交时间：** 2025-12-30 14:29:31  
**提交哈希：** c5b426f0cd170baa083a4809b4739bcd46ff95fb

#### 功能描述

增强数据映射功能，添加表名支持，实现数据库与表之间的链接关系，优化图形视图的节点加载逻辑。

#### 关键修改

1. **Schema 扩展（schema.yaml）**
   - 在 `column` 和 `mapping` 对象类型中添加 `table_name` 属性

2. **服务层增强**
   - **MappingService.java**：在创建映射时自动提取并存储表名
   - **TableSyncService.java**：
     - 在同步列信息时添加 `table_name` 属性
     - 实现 `database_has_table` 和 `table_has_column` 链接的自动创建

3. **图形视图优化（GraphView.tsx）**
   - 优化节点加载逻辑，确保加载所有相关节点
   - 过滤无效链接，避免孤立节点显示

---

### 2.3 实例创建和名称匹配功能（提交 1920799）

**提交时间：** 2025-12-30 14:53:05  
**提交哈希：** 1920799f7bb7ddffdf93ae73357dd2905f4a626c

#### 功能描述

增强实例创建功能，支持使用指定 ID 创建实例，优化名称匹配逻辑，提升自动映射的准确性。

#### 关键修改

1. **实例存储增强（InstanceStorage.java）**
   - 新增 `createInstanceWithId` 方法：支持使用指定 ID 创建实例

2. **实例服务增强（InstanceService.java）**
   - 增强 `getInstance` 方法：支持对象类型名称的大小写不敏感匹配

3. **数据映射服务（MappedDataService.java）**
   - 使用 `createInstanceWithId` 方法，确保实例 ID 与数据库主键一致

4. **前端自动映射优化**
   - **DataMappingDialog.tsx**：添加驼峰命名与下划线命名转换工具函数
   - **DataMapping.tsx**：优化自动匹配逻辑，支持多种命名风格的匹配

#### 技术细节

- 支持 `camelCase` 与 `snake_case` 的相互转换
- 支持名称规范化匹配（忽略大小写、下划线、连字符等）

---

### 2.4 属性映射和关系同步功能（提交 28f547d）

**提交时间：** 2025-12-30 16:08:04  
**提交哈希：** 28f547dafc280901d9e539e9f8ca31864fd6fb50

#### 功能描述

实现基于属性映射的自动关系同步功能，支持多属性映射关系，增强 Schema Browser 的交互体验。

#### 关键修改

1. **元数据模型扩展（schema.yaml）**
   - 在 `link_types` 中添加 `property_mappings` 属性
   - 支持多对多的属性映射关系（JSON 格式）

2. **LinkType 类增强（LinkType.java）**
   - 添加 `propertyMappings` 属性（`Map<String, String>`）
   - 移除原有的 `sourceField` 和 `targetField` 单一字段匹配

3. **关系同步服务（LinkSyncService.java）**（新增）
   - 实现 `syncLinksByType` 方法：根据 linkType 的 property_mappings 自动创建关系
   - 支持多属性匹配（所有属性都必须匹配）

4. **控制器增强（LinkController.java）**
   - 新增 `POST /api/v1/links/{linkType}/sync` 接口

5. **前端增强**
   - **LinkList.tsx**：添加"同步关系"按钮，显示同步状态
   - **SchemaBrowser.tsx**：
     - 图形化显示 LinkType 的属性映射关系
     - 高亮显示参与映射的属性
     - 实现交互式过滤（点击 ObjectType 过滤 LinkType，点击 LinkType 过滤 ObjectType）

#### 使用场景

1. **关系同步**：在 Links 页面选择关系类型，点击"同步关系"按钮，系统根据 property_mappings 自动创建关系实例
2. **关系查看**：在 Schema Browser 中点击关系类型，查看详细的属性映射关系图

---

### 2.5 显示名称支持（提交 fc743af）

**提交时间：** 2025-12-30 16:55:21  
**提交哈希：** fc743af35fea4bbadd438009be209c484fc3ade6

#### 功能描述

为对象类型和关系类型添加中文显示名称支持，提升用户体验。

#### 关键修改

1. **元数据模型扩展**
   - **ObjectType.java**：添加 `displayName` 属性
   - **LinkType.java**：添加 `displayName` 属性

2. **前端显示优化**
   - **Layout.tsx**：左侧导航栏显示 `display_name || name`
   - **SchemaBrowser.tsx**：对象类型和关系类型列表显示 `display_name || name`
   - **LinkList.tsx**：关系类型标题显示 `display_name || name`

---

### 2.6 工作空间管理功能（提交 9a6dbfb）

**提交时间：** 2025-12-30 17:27:44  
**提交哈希：** 9a6dbfb706f48b7cea4849c233bb8bed481f700b

#### 功能描述

实现工作空间功能，支持对对象类型和关系类型进行分组管理，分离系统模型和业务模型。

#### 关键修改

1. **Schema 分离**
   - **schema-system.yaml**（新增）：系统相关对象类型和关系类型
     - 对象类型：`workspace`、`database`、`table`、`column`、`mapping`
     - 关系类型：`database_has_table`、`table_has_column`、`mapping_links_table`
   - **schema.yaml**：移除系统相关定义，只保留业务模型

2. **Schema 加载器增强（Loader.java）**
   - 新增构造函数：`Loader(String filePath, String systemFilePath)`
   - 实现 `mergeSchemas` 方法：合并系统 schema 和用户 schema
   - 系统 schema 优先级高于用户 schema

3. **配置管理（Config.java, application.properties）**
   - 添加 `systemSchemaFilePath` 配置项

4. **工作空间上下文（WorkspaceContext.tsx）**（新增）
   - 管理工作空间状态
   - 支持工作空间的创建、选择、更新
   - 从 localStorage 持久化选择的工作空间

5. **工作空间对话框（WorkspaceDialog.tsx）**（新增）
   - 工作空间创建和编辑界面
   - 支持选择对象类型和关系类型添加到工作空间

6. **前端组件增强**
   - **Layout.tsx**：添加工作空间选择器，根据工作空间过滤导航项
   - **SchemaBrowser.tsx**：根据工作空间过滤显示的对象类型和关系类型
   - **InstanceList.tsx**：系统工作空间隐藏"关联数据源"和"同步抽取"按钮
   - **GraphView.tsx**：根据工作空间过滤节点和关系

#### 使用场景

1. **工作空间创建**：在左上角工作空间选择器中点击"+"按钮，创建新工作空间
2. **工作空间管理**：选择对象类型和关系类型添加到工作空间
3. **工作空间切换**：在左上角下拉菜单中选择工作空间，系统自动过滤显示相关内容

---

### 2.7 批量数据获取功能（提交 847094a）

**提交时间：** 2025-12-31 09:34:03  
**提交哈希：** 847094ab530eb2c2db8729f0f4a1874ae684fa7e

#### 功能描述

实现批量获取实例的 API，大幅优化图形视图的加载性能，减少 HTTP 请求数量。

#### 关键修改

1. **后端 API 增强（InstanceController.java）**
   - 新增 `POST /api/v1/instances/{objectType}/batch`：批量获取单个对象类型的实例
   - 新增 `POST /api/v1/instances/batch`：批量获取多个对象类型的实例

2. **实例存储增强（InstanceStorage.java）**
   - 新增 `getInstancesBatch` 方法：批量获取单个对象类型的实例
   - 新增 `getInstancesBatchMultiType` 方法：批量获取多个对象类型的实例

3. **实例服务增强（InstanceService.java）**
   - 新增 `getInstancesBatch` 方法
   - 新增 `getInstancesBatchMultiType` 方法

4. **前端 API 客户端（client.ts）**
   - 新增 `instanceApi.getBatch` 方法
   - 新增 `instanceApi.getBatchMultiType` 方法

5. **图形视图优化**
   - **GraphView.tsx**：使用批量 API 加载节点，减少 HTTP 请求数
   - **SchemaGraphView.tsx**：集成批量获取功能

6. **性能分析文档（PERFORMANCE_ANALYSIS.md）**（新增）
   - 记录性能优化过程和结果
   - 优化前：20-30 个 HTTP 请求，3-5 秒加载时间
   - 优化后：5-10 个 HTTP 请求，1-2 秒加载时间

#### 性能提升

- **HTTP 请求数：** 从 20-30 减少到 5-10（减少 70%）
- **加载时间：** 从 3-5 秒减少到 1-2 秒（减少 60%）
- **内存占用：** 减少 50-70%

---

### 2.8 时间处理和血缘查询功能（提交 053c18b）

**提交时间：** 2025-12-31 13:14:59  
**提交哈希：** 053c18ba5f26f0a36ecbe229fb8577178e6fa025

#### 功能描述

完善时间类型处理，确保数据一致性；实现完整的血缘查询功能，支持正向、反向、全链血缘查询。

#### 关键修改

1. **时间处理增强**
   - **pom.xml**：添加 `jackson-datatype-jsr310` 依赖
   - **JacksonConfig.java**：配置 ObjectMapper 支持 Java 8 时间类型
   - **InstanceStorage.java**：注册 Java 8 时间模块
   - **LinkStorage.java**：注册 Java 8 时间模块

2. **Schema 扩展（schema.yaml）**
   - 在相关对象类型中添加 `pass_id` 字段

3. **血缘查询功能（GraphView.tsx）**
   - 实现 `loadForwardLineage` 方法：正向血缘查询（从当前节点向后递归）
   - 实现 `loadBackwardLineage` 方法：反向血缘查询（从当前节点向前递归）
   - 支持四种查询模式：
     - **直接关系**：只显示直接连接的节点
     - **正向血缘**：从当前节点向后递归查询所有下游节点
     - **反向血缘**：从当前节点向前递归查询所有上游节点
     - **全链血缘**：从当前节点前后递归查询所有相关节点
   - 添加血缘查询模式选择 UI

#### 使用场景

1. **血缘查询**：在 Instance Graph 界面选择血缘查询模式
   - 正向血缘：查看数据流向（当前节点影响哪些节点）
   - 反向血缘：查看数据来源（哪些节点影响当前节点）
   - 全链血缘：查看完整的数据链路

---

## 三、技术架构变化

### 3.1 后端架构

#### 新增服务层

```
com.mypalantir.service/
├── DatabaseService.java          # 数据库实例管理
├── DatabaseMetadataService.java   # 数据库元数据查询
├── MappingService.java            # 映射关系管理
├── MappedDataService.java         # 基于映射的数据抽取
├── TableSyncService.java          # 表结构同步
└── LinkSyncService.java           # 关系自动同步
```

#### 新增控制器层

```
com.mypalantir.controller/
├── DatabaseController.java        # 数据库相关 API
└── MappingController.java         # 映射相关 API
```

#### 配置管理增强

```
com.mypalantir.config/
├── EnvConfig.java                 # 环境变量加载
└── DatabaseConfig.java            # 数据库连接管理
```

### 3.2 前端架构

#### 新增组件

```
web/src/
├── components/
│   ├── DataMappingDialog.tsx      # 数据映射对话框
│   └── WorkspaceDialog.tsx        # 工作空间对话框
├── pages/
│   └── DataMapping.tsx            # 数据映射页面
└── WorkspaceContext.tsx           # 工作空间上下文
```

#### API 客户端扩展

```
web/src/api/client.ts
├── databaseApi                    # 数据库相关 API
├── mappingApi                     # 映射相关 API
└── instanceApi
    ├── getBatch                   # 批量获取单个类型
    └── getBatchMultiType          # 批量获取多个类型
```

### 3.3 数据模型扩展

#### 系统对象类型

- `workspace`：工作空间
- `database`：数据源
- `table`：数据表
- `column`：数据列
- `mapping`：映射关系

#### 系统关系类型

- `database_has_table`：数据源包含表
- `table_has_column`：表包含列
- `mapping_links_table`：映射关联表

---

## 四、API 接口变更

### 4.1 新增接口

#### 数据库相关

- `GET /api/v1/database/default-id` - 获取默认数据库 ID
- `GET /api/v1/database/tables` - 获取数据库表列表
- `GET /api/v1/database/columns` - 获取表字段列表
- `GET /api/v1/database/table-info` - 获取表详细信息
- `POST /api/v1/database/sync-tables` - 同步表结构

#### 映射相关

- `POST /api/v1/mappings` - 创建映射
- `GET /api/v1/mappings/{id}` - 获取映射详情
- `PUT /api/v1/mappings/{id}` - 更新映射
- `DELETE /api/v1/mappings/{id}` - 删除映射
- `GET /api/v1/mappings/by-object-type/{objectType}` - 根据对象类型查询映射
- `GET /api/v1/mappings/by-table/{tableId}` - 根据表查询映射

#### 实例相关

- `POST /api/v1/instances/{objectType}/batch` - 批量获取单个对象类型的实例
- `POST /api/v1/instances/batch` - 批量获取多个对象类型的实例
- `POST /api/v1/instances/{objectType}/sync-from-mapping/{mappingId}` - 从映射同步数据

#### 关系相关

- `POST /api/v1/links/{linkType}/sync` - 同步关系

### 4.2 接口变更

- `GET /api/v1/instances/{objectType}` - 新增 `mappingId` 查询参数
- `GET /api/v1/instances/{objectType}` - 支持多条件过滤

---

## 五、配置文件变更

### 5.1 application.properties

```properties
# 新增配置项
schema.system.file.path=./ontology/schema-system.yaml

# 数据库配置（从环境变量读取）
db.host=${DB_HOST:localhost}
db.port=${DB_PORT:3306}
db.name=${DB_NAME:}
db.user=${DB_USER:}
db.password=${DB_PASSWORD:}
db.type=${DB_TYPE:mysql}
```

### 5.2 .env 文件（新增）

```env
DB_HOST=49.233.67.173
DB_PORT=3306
DB_NAME=gs
DB_USER=ump
DB_PASSWORD=ump@2025
DB_TYPE=mysql
```

### 5.3 pom.xml

```xml
<!-- 新增依赖 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.0.33</version>
</dependency>
<dependency>
    <groupId>io.github.cdimascio</groupId>
    <artifactId>dotenv-java</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

---

## 六、使用指南

### 6.1 数据库集成使用流程

1. **配置数据库连接**
   - 在项目根目录创建 `.env` 文件
   - 配置数据库连接信息（参考 `.env.example`）

2. **创建数据源实例**
   - 进入 Instances 页面，选择 `database` 对象类型
   - 点击"创建"按钮，创建数据源实例

3. **同步表结构**
   - 在 `database` 实例列表页面，点击"同步表信息"按钮
   - 系统自动同步数据库中的表和字段信息

4. **建立数据映射**
   - 进入业务对象类型的 Instances 页面
   - 点击"关联数据源"按钮
   - 选择数据源和表
   - 手动建立字段与属性的映射关系

5. **抽取数据**
   - 点击"同步抽取"按钮
   - 系统根据映射关系从数据库抽取数据到模型实例

### 6.2 工作空间使用流程

1. **创建工作空间**
   - 在左上角工作空间选择器中点击"+"按钮
   - 填写工作空间信息（名称、显示名称、描述）
   - 选择要包含的对象类型和关系类型

2. **切换工作空间**
   - 在左上角下拉菜单中选择工作空间
   - 系统自动过滤显示相关内容

3. **管理工作空间**
   - 点击工作空间选择器中的编辑按钮
   - 修改工作空间配置

### 6.3 关系同步使用流程

1. **定义属性映射**
   - 在 `schema.yaml` 中为 link_type 添加 `property_mappings` 属性
   - 定义源对象属性到目标对象属性的映射关系

2. **同步关系**
   - 进入 Links 页面，选择关系类型
   - 点击"同步关系"按钮
   - 系统根据 property_mappings 自动创建关系实例

### 6.4 血缘查询使用流程

1. **进入图形视图**
   - 在 Instances 页面点击实例 ID
   - 点击"View Graph"按钮

2. **选择查询模式**
   - **直接关系**：查看直接连接的节点
   - **正向血缘**：查看所有下游节点
   - **反向血缘**：查看所有上游节点
   - **全链血缘**：查看所有相关节点

---

## 七、注意事项

### 7.1 数据库配置

- `.env` 文件包含敏感信息，不应提交到版本控制系统
- 建议使用 `.env.example` 作为模板

### 7.2 工作空间

- 系统工作空间（包含 `workspace`、`database`、`table`、`column`、`mapping`）会自动隐藏"关联数据源"和"同步抽取"按钮
- 工作空间为空时，导航栏不显示任何对象类型和关系类型

### 7.3 性能优化

- 图形视图默认限制节点数和关系数，可通过设置面板调整
- 工作空间模式下，限制值会自动提高

### 7.4 数据同步

- 同步表结构时，会自动创建 `database_has_table` 和 `table_has_column` 关系
- 同步数据时，如果定义了 `primary_key_column`，实例 ID 会使用数据库主键值

---

## 八、已知问题

1. **反向血缘查询性能**：当关系数据量大时，反向血缘查询可能较慢（需要查询所有关系然后过滤）
2. **批量 API 限制**：批量 API 目前没有分页支持，如果 ID 列表过长可能导致请求超时

---

## 九、后续计划

1. **性能优化**
   - 优化反向血缘查询，使用更高效的查询方式
   - 为批量 API 添加分页支持

2. **功能增强**
   - 支持更多数据库类型（PostgreSQL、Oracle 等）
   - 支持数据映射的导入导出
   - 支持工作空间的导入导出

3. **用户体验**
   - 添加数据映射的自动匹配建议
   - 优化图形视图的交互体验
   - 添加数据同步的进度显示

---

## 十、相关文档

- [README.md](./README.md) - 项目总体说明
- [PERFORMANCE_ANALYSIS.md](./PERFORMANCE_ANALYSIS.md) - 性能优化分析
- [local/README_generate_tables.md](./local/README_generate_tables.md) - 表生成脚本说明
- [local/README_build_related_tables.md](./local/README_build_related_tables.md) - 关联表构建脚本说明
- [local/README_generate_demo_data.md](./local/README_generate_demo_data.md) - 演示数据生成脚本说明

---

**文档维护者：** 开发团队  
**最后更新：** 2025-12-31
