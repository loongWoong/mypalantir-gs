# MyPalantir - Foundry Ontology 仿制项目

一个基于 Java (Spring Boot) 实现的数据模型管理平台，仿照 Palantir Foundry Ontology 的核心功能。

## 功能特性

### 核心功能
- **元数据模型定义**：使用 YAML 格式定义对象类型、属性和关系类型
- **双 Schema 架构**：系统 Schema（内置模型）和用户 Schema（业务模型）分离管理
- **文件系统存储**：所有数据存储在文件系统中，便于版本控制
- **RESTful API**：提供完整的 API 用于模型查询和实例数据管理
- **数据验证**：完整的模型验证和数据验证机制

### 数据集成功能
- **数据库连接管理**：支持 MySQL 等数据库连接配置
- **数据源管理**：管理多个数据源（database 对象类型）
- **表结构同步**：从数据库自动同步表（table）和列（column）信息
- **数据映射**：建立数据库表列与对象属性的映射关系
- **数据抽取**：基于映射关系从数据库抽取数据到本体实例

### 工作空间功能
- **工作空间管理**：创建和管理多个工作空间
- **对象分组**：将对象类型和关系类型分组到不同工作空间
- **界面过滤**：根据工作空间过滤显示对象和关系
- **系统工作空间**：内置系统工作空间，包含系统对象和关系

### 前端功能
- **Schema 浏览器**：图形化查看对象类型、关系类型及其属性映射
- **实例管理**：创建、查看、编辑、删除实例数据
- **多条件检索**：支持多属性条件过滤查询实例
- **关系管理**：查看和管理对象之间的关系，支持关系同步
- **数据映射界面**：ER 图形式配置数据库表列与对象属性的映射
- **实例关系图**：力导向图可视化展示实例及其关系
- **交互式过滤**：在 Schema 浏览器中点击对象/关系进行关联过滤

## 项目结构

```
mypalantir-gs/
├── src/                    # Maven 标准目录结构
│   ├── main/
│   │   ├── java/          # Java 源代码
│   │   │   └── com/mypalantir/
│   │   │       ├── config/         # 配置管理（包括数据库配置、环境变量加载）
│   │   │       ├── meta/           # 元数据模型和解析器
│   │   │       ├── repository/     # 数据访问层（文件系统存储）
│   │   │       ├── service/        # 业务逻辑层（包括数据库服务、映射服务、同步服务）
│   │   │       └── controller/      # REST 控制器
│   │   └── resources/     # 资源文件
│   │       ├── application.properties
│   │       └── static/     # Web UI 构建产物（生产模式，由 Maven 自动复制）
│   └── test/               # 测试代码
├── ontology/               # 元数据定义文件目录
│   ├── schema-system.yaml  # 系统 Schema（内置对象和关系）
│   └── schema.yaml         # 用户 Schema（业务对象和关系）
├── web/                  # Web UI 源代码
│   ├── src/
│   │   ├── api/          # API 客户端
│   │   ├── components/   # React 组件
│   │   ├── pages/        # 页面组件
│   │   └── WorkspaceContext.tsx  # 工作空间上下文
│   └── dist/            # Web UI 构建产物（开发模式使用）
├── scripts/               # 脚本文件
├── data/                  # 实例数据目录（运行时生成）
├── .env                   # 环境变量配置（数据库连接信息等）
└── pom.xml                # Maven 配置文件
```

### Web UI 静态文件服务

项目支持两种模式：

1. **开发模式**：使用外部路径 `./web/dist`
   - Web UI 构建后，Spring Boot 直接从 `web/dist` 目录提供静态文件
   - 适合开发环境，支持热重载

2. **生产模式**：使用 classpath 资源 `classpath:/static`
   - Maven 构建时自动将 `web/dist` 复制到 `target/classes/static/`
   - 打包到 JAR 文件中，适合生产部署
   - 修改 `application.properties` 中的 `web.static.path` 为 `classpath:/static`

## 技术栈

### 后端
- **Java 17**
- **Spring Boot 3.2.0**
- **Jackson** (JSON/YAML 处理)
- **SnakeYAML** (YAML 解析)
- **MySQL Connector** (数据库连接)
- **dotenv-java** (环境变量加载)
- **Maven** (构建工具)

### 前端
- **React 18** + **TypeScript**
- **Vite** (构建工具)
- **Tailwind CSS** (样式框架)
- **React Router** (路由)
- **Axios** (HTTP 客户端)
- **Heroicons** (图标库)
- **ForceGraph2D** (关系图可视化)

## 前置要求

- **Java 17+**
- **Maven 3.6+**
- **Node.js 18+** 和 **npm**（用于构建 Web UI）
- **MySQL 8+**（可选，用于数据库集成功能）

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd mypalantir-gs
```

### 2. 安装依赖

**后端：**
```bash
mvn clean install
```

**Web UI：**
```bash
cd web
npm install
cd ..
```

### 3. 构建 Web UI

```bash
cd web
npm run build
cd ..
```

### 4. 配置

#### 环境变量配置（数据库连接）

在项目根目录创建 `.env` 文件（可选，如果不需要数据库集成功能可跳过）：

```env
DB_HOST=your_host
DB_PORT=3306
DB_NAME=your_database
DB_USER=your_username
DB_PASSWORD=your_password
DB_TYPE=mysql
```

> **注意**：`.env` 文件包含敏感信息，不应提交到版本控制系统。项目已配置 `.gitignore` 忽略此文件。

#### 应用配置

编辑 `src/main/resources/application.properties`：

**开发模式（推荐）：**
```properties
server.port=8080
schema.file.path=./ontology/schema.yaml
schema.system.file.path=./ontology/schema-system.yaml
data.root.path=./data
web.static.path=./web/dist
```

**生产模式（打包到 JAR）：**
```properties
server.port=8080
schema.file.path=./ontology/schema.yaml
schema.system.file.path=./ontology/schema-system.yaml
data.root.path=./data
web.static.path=classpath:/static
```

> **注意**：生产模式下，Web UI 构建产物会在 Maven 构建时自动复制到 `target/classes/static/`，并打包到 JAR 文件中。

### 5. 运行服务

**方式一：使用启动脚本（推荐）**
```bash
./scripts/start.sh
```

**方式二：使用 Maven**
```bash
mvn spring-boot:run
```

**方式三：生产模式（打包到 JAR）**
```bash
# 构建 Web UI
cd web && npm run build && cd ..

# 修改配置为生产模式（可选，默认使用开发模式）
# 编辑 src/main/resources/application.properties
# 设置 web.static.path=classpath:/static

# 构建并运行
mvn clean package
java -jar target/mypalantir-server-1.0.0.jar
```

服务启动后：
- **Web 界面**：http://localhost:8080
- **API 端点**：http://localhost:8080/api/v1
- **健康检查**：http://localhost:8080/health

> **注意**：Spring Boot 会自动提供 Web UI 静态文件，并支持 SPA 路由回退（所有非 API 请求返回 `index.html`）。

### 6. 创建测试数据（可选）

项目提供了测试数据创建脚本，可以快速创建示例数据：

**使用 Python 脚本：**
```bash
python3 scripts/create_test_data.py
```

**使用 Bash 脚本：**
```bash
bash scripts/create_test_data.sh
```

这些脚本会创建完整的测试数据，包括：
- 路段业主、收费公路、收费站等基础设施
- 车辆、通行介质等业务对象
- 交易流水、通行路径等业务数据
- 各种关系连接

## 开发模式

开发时建议前后端分离运行，便于热重载：

**终端 1 - 后端：**
```bash
mvn spring-boot:run
```

**终端 2 - Web UI：**
```bash
cd web
npm run dev
```

Web UI 开发服务器在 `http://localhost:5173`，会自动代理 API 请求到后端。

> **提示**：开发模式下，前端修改会实时热重载，后端修改需要重启 Spring Boot 应用。

## API 端点

### Schema API
- `GET /api/v1/schema/object-types` - 列出所有对象类型
- `GET /api/v1/schema/object-types/{name}` - 获取对象类型详情
- `GET /api/v1/schema/object-types/{name}/properties` - 获取对象类型属性
- `GET /api/v1/schema/object-types/{name}/outgoing-links` - 获取出边关系
- `GET /api/v1/schema/object-types/{name}/incoming-links` - 获取入边关系
- `GET /api/v1/schema/link-types` - 列出所有关系类型
- `GET /api/v1/schema/link-types/{name}` - 获取关系类型详情

### Instance API
- `POST /api/v1/instances/{objectType}` - 创建实例
- `GET /api/v1/instances/{objectType}` - 列出实例（支持多条件过滤）
- `GET /api/v1/instances/{objectType}?mappingId={mappingId}` - 从映射数据源查询实例
- `GET /api/v1/instances/{objectType}/{id}` - 获取实例详情
- `PUT /api/v1/instances/{objectType}/{id}` - 更新实例
- `DELETE /api/v1/instances/{objectType}/{id}` - 删除实例
- `POST /api/v1/instances/{objectType}/sync-from-mapping/{mappingId}` - 从映射数据源同步抽取数据

### Link API
- `POST /api/v1/links/{linkType}` - 创建关系
- `GET /api/v1/links/{linkType}` - 列出关系
- `GET /api/v1/links/{linkType}/{id}` - 获取关系详情
- `PUT /api/v1/links/{linkType}/{id}` - 更新关系
- `DELETE /api/v1/links/{linkType}/{id}` - 删除关系
- `POST /api/v1/links/{linkType}/sync` - 根据属性映射规则同步关系

### Instance Link API
- `GET /api/v1/instances/{objectType}/{id}/links/{linkType}` - 查询实例的关系
- `GET /api/v1/instances/{objectType}/{id}/connected/{linkType}` - 查询关联的实例

### Database API
- `GET /api/v1/database/default-id` - 获取默认数据源 ID
- `GET /api/v1/database/{databaseId}/tables` - 获取数据源的所有表
- `GET /api/v1/database/{databaseId}/tables/{tableName}/columns` - 获取表的列信息
- `GET /api/v1/database/{databaseId}/tables/{tableName}` - 获取表详细信息
- `POST /api/v1/database/sync-tables?databaseId={databaseId}` - 同步表结构和列信息

### Mapping API
- `POST /api/v1/mappings` - 创建映射关系
- `GET /api/v1/mappings/{mappingId}` - 获取映射详情
- `PUT /api/v1/mappings/{mappingId}` - 更新映射
- `DELETE /api/v1/mappings/{mappingId}` - 删除映射
- `GET /api/v1/mappings/by-object-type/{objectType}` - 根据对象类型查询映射
- `GET /api/v1/mappings/by-table/{tableId}` - 根据表查询映射

## 开发

### 构建

```bash
mvn clean package
```

### 运行测试

```bash
mvn test
```

### 脚本工具

项目提供了多个便捷脚本：

- **`scripts/start.sh`** - 启动服务（自动检查并构建 Web UI）
- **`scripts/create_test_data.sh`** - 创建测试数据（Bash 版本）
- **`scripts/create_test_data.py`** - 创建测试数据（Python 版本）
- **`scripts/test_api.sh`** - API 功能测试
- **`scripts/quick_test.sh`** - 快速功能验证

## 项目架构

### 目录结构说明

```
mypalantir-gs/
├── src/main/java/com/mypalantir/
│   ├── config/          # 配置类
│   │   ├── Config.java           # 应用配置
│   │   ├── EnvConfig.java        # 环境变量加载（.env 文件）
│   │   ├── DatabaseConfig.java   # 数据库连接配置
│   │   ├── CorsConfig.java       # CORS 配置
│   │   ├── JacksonConfig.java    # Jackson JSON 配置
│   │   └── WebConfig.java        # Web MVC 配置（静态资源、SPA 路由）
│   ├── controller/      # REST 控制器
│   │   ├── SchemaController.java      # Schema 查询 API
│   │   ├── InstanceController.java    # 实例 CRUD API
│   │   ├── LinkController.java       # 关系 CRUD API
│   │   ├── InstanceLinkController.java # 实例关系查询 API
│   │   ├── DatabaseController.java    # 数据库操作 API
│   │   └── MappingController.java     # 映射管理 API
│   ├── meta/            # 元数据模型
│   │   ├── OntologySchema.java   # Schema 根对象
│   │   ├── ObjectType.java       # 对象类型
│   │   ├── LinkType.java        # 关系类型（支持属性映射）
│   │   ├── Property.java        # 属性定义
│   │   ├── Parser.java          # YAML 解析器
│   │   ├── Validator.java       # Schema 验证器
│   │   └── Loader.java          # Schema 加载器（支持多 Schema 合并）
│   ├── repository/      # 数据访问层
│   │   ├── PathManager.java     # 路径管理
│   │   ├── InstanceStorage.java # 实例存储
│   │   └── LinkStorage.java     # 关系存储
│   ├── service/         # 业务逻辑层
│   │   ├── SchemaService.java   # Schema 服务
│   │   ├── InstanceService.java # 实例服务
│   │   ├── LinkService.java    # 关系服务
│   │   ├── LinkSyncService.java # 关系同步服务
│   │   ├── DataValidator.java  # 数据验证服务
│   │   ├── DatabaseService.java # 数据库服务
│   │   ├── DatabaseMetadataService.java # 数据库元数据服务
│   │   ├── TableSyncService.java # 表结构同步服务
│   │   ├── MappingService.java # 映射服务
│   │   └── MappedDataService.java # 映射数据服务
│   └── MyPalantirApplication.java # Spring Boot 主类
├── ontology/            # 元数据定义
│   ├── schema-system.yaml  # 系统 Schema（workspace, database, table, column, mapping）
│   └── schema.yaml      # 用户 Schema（业务对象和关系）
├── web/                 # Web UI 源代码
│   ├── src/
│   │   ├── api/        # API 客户端
│   │   ├── components/ # React 组件
│   │   │   ├── Layout.tsx           # 主布局（包含侧边栏和工作空间选择器）
│   │   │   ├── InstanceForm.tsx     # 实例表单
│   │   │   ├── DataMappingDialog.tsx # 数据映射对话框
│   │   │   └── WorkspaceDialog.tsx  # 工作空间对话框
│   │   ├── pages/      # 页面组件
│   │   │   ├── SchemaBrowser.tsx    # Schema 浏览器（图形化展示）
│   │   │   ├── InstanceList.tsx     # 实例列表（支持多条件过滤）
│   │   │   ├── InstanceDetail.tsx   # 实例详情
│   │   │   ├── LinkList.tsx        # 关系列表（支持关系同步）
│   │   │   ├── GraphView.tsx       # 实例关系图
│   │   │   └── SchemaGraphView.tsx # Schema 关系图
│   │   └── WorkspaceContext.tsx    # 工作空间上下文
│   └── dist/            # 构建产物
├── scripts/             # 脚本文件
├── data/                # 实例数据（运行时生成）
└── pom.xml              # Maven 配置
```

### 核心组件

1. **Meta（元数据）层**：负责加载和验证 YAML Schema 定义，支持系统 Schema 和用户 Schema 合并
2. **Repository（存储）层**：文件系统存储实现，使用 JSON 格式
3. **Service（服务）层**：业务逻辑，包括数据验证、数据库集成、数据映射、数据同步
4. **Controller（控制器）层**：RESTful API 端点

## 数据模型

### Schema 架构

项目使用双 Schema 架构：

1. **系统 Schema** (`ontology/schema-system.yaml`)：包含系统内置对象和关系
   - 对象类型：`workspace`（工作空间）、`database`（数据源）、`table`（数据表）、`column`（数据列）、`mapping`（映射关系）
   - 关系类型：`database_has_table`、`table_has_column`、`mapping_links_table`

2. **用户 Schema** (`ontology/schema.yaml`)：包含业务对象和关系
   - 业务对象：如 `EntryTransaction`、`ExitTransaction`、`Path` 等
   - 业务关系：如 `entry_to_path`、`exit_to_path`、`path_has_details` 等

系统启动时会自动加载并合并两个 Schema，系统 Schema 优先（同名对象/关系会被系统版本覆盖）。

### Schema 定义

Schema 定义包括：
- **对象类型（Object Types）**：定义业务对象及其属性
- **关系类型（Link Types）**：定义对象之间的关系，支持属性映射（`property_mappings`）
- **属性（Properties）**：包含数据类型、约束、验证规则

### 关系属性映射

关系类型支持 `property_mappings`，用于定义源对象和目标对象之间的属性匹配规则：

```yaml
link_types:
  - name: entry_to_path
    display_name: 入口交易关联路径
    source_type: EntryTransaction
    target_type: Path
    property_mappings:
      pass_id: pass_id
      vlp: plate_num
      vlpc: plate_color
```

这表示：当 `EntryTransaction.pass_id == Path.pass_id` 且 `EntryTransaction.vlp == Path.plate_num` 且 `EntryTransaction.vlpc == Path.plate_color` 时，可以建立关系。

## 使用指南

### 工作空间管理

1. **创建工作空间**：在左侧导航栏点击工作空间下拉菜单，选择"创建工作空间"
2. **添加对象和关系**：在创建工作空间时，勾选需要包含的对象类型和关系类型
3. **切换工作空间**：在左侧导航栏顶部选择工作空间，界面会自动过滤显示
4. **系统工作空间**：系统内置工作空间包含系统对象和关系，不可编辑

### 数据库集成

1. **配置数据源**：
   - 在 Instances 页面选择 `database` 对象类型
   - 创建新的数据源实例，填写数据库连接信息

2. **同步表结构**：
   - 在 `database` 对象类型的 Instances 页面
   - 点击"同步表信息"按钮
   - 系统会自动从数据库同步表（table）和列（column）信息，并创建相应的关系

3. **建立数据映射**：
   - 在业务对象类型（非系统对象）的 Instances 页面
   - 点击"关联数据源"按钮
   - 选择数据源和表
   - 在 ER 图界面中手动连接表列和对象属性
   - 保存映射关系

4. **同步抽取数据**：
   - 在业务对象类型的 Instances 页面
   - 点击"同步抽取"按钮
   - 选择已建立的映射关系
   - 系统会根据映射关系从数据库抽取数据并创建实例

### 关系同步

1. **定义属性映射**：在 Schema 中为关系类型定义 `property_mappings`
2. **同步关系**：
   - 在 Links 页面选择关系类型
   - 点击"同步关系"按钮
   - 系统会根据属性映射规则自动创建关系

### 多条件检索

在 Instances 页面：
1. 点击"Filter"按钮打开过滤面板
2. 添加过滤条件（属性名和值）
3. 系统会根据所有条件进行 AND 查询

## 许可证

本项目为仿制项目，仅供学习和研究使用。
