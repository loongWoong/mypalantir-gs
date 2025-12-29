# MyPalantir - Foundry Ontology 仿制项目

一个基于 Java (Spring Boot) 实现的数据模型管理平台，仿照 Palantir Foundry Ontology 的核心功能。

## 功能特性

- **元数据模型定义**：使用 YAML 格式定义对象类型、属性和关系类型
- **文件系统存储**：所有数据存储在文件系统中，便于版本控制
- **RESTful API**：提供完整的 API 用于模型查询和实例数据管理
- **数据验证**：完整的模型验证和数据验证机制

## 项目结构

```
mypalantir/
├── src/                    # Maven 标准目录结构
│   ├── main/
│   │   ├── java/          # Java 源代码
│   │   │   └── com/mypalantir/
│   │   │       ├── config/         # 配置管理
│   │   │       ├── meta/           # 元数据模型和解析器
│   │   │       ├── repository/     # 数据访问层（文件系统存储）
│   │   │       ├── service/        # 业务逻辑层
│   │   │       └── controller/      # REST 控制器
│   │   └── resources/     # 资源文件
│   │       ├── application.properties
│   │       └── static/     # Web UI 构建产物（生产模式，由 Maven 自动复制）
│   └── test/               # 测试代码
├── ontology/               # 元数据定义文件目录
├── web/                  # Web UI 源代码
│   └── dist/            # Web UI 构建产物（开发模式使用）
├── scripts/               # 脚本文件
├── data/                  # 实例数据目录（运行时生成）
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

- **Java 17**
- **Spring Boot 3.2.0**
- **Jackson** (JSON/YAML 处理)
- **Maven** (构建工具)

## 快速开始

### 1. 安装依赖

**后端：**
```bash
mvn clean install
```

**Web UI：**
```bash
cd web
npm install
```

### 2. 构建 Web UI

```bash
cd web
npm run build
```

### 3. 配置（可选）

编辑 `src/main/resources/application.properties` 或使用环境变量：

**开发模式（推荐）：**
```properties
server.port=8080
schema.file.path=./ontology/schema.yaml
data.root.path=./data
web.static.path=./web/dist
```

**生产模式（打包到 JAR）：**
```properties
server.port=8080
schema.file.path=./ontology/schema.yaml
data.root.path=./data
web.static.path=classpath:/static
```

> **注意**：生产模式下，Web UI 构建产物会在 Maven 构建时自动复制到 `target/classes/static/`，并打包到 JAR 文件中。

### 4. 运行服务

**开发模式：**
```bash
# 确保 Web UI 已构建
cd web && npm run build && cd ..

# 运行 Spring Boot
mvn spring-boot:run
```

**生产模式（打包到 JAR）：**
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
- `GET /api/v1/instances/{objectType}` - 列出实例
- `GET /api/v1/instances/{objectType}/{id}` - 获取实例详情
- `PUT /api/v1/instances/{objectType}/{id}` - 更新实例
- `DELETE /api/v1/instances/{objectType}/{id}` - 删除实例

### Link API
- `POST /api/v1/links/{linkType}` - 创建关系
- `GET /api/v1/links/{linkType}` - 列出关系
- `GET /api/v1/links/{linkType}/{id}` - 获取关系详情
- `PUT /api/v1/links/{linkType}/{id}` - 更新关系
- `DELETE /api/v1/links/{linkType}/{id}` - 删除关系

### Instance Link API
- `GET /api/v1/instances/{objectType}/{id}/links/{linkType}` - 查询实例的关系
- `GET /api/v1/instances/{objectType}/{id}/connected/{linkType}` - 查询关联的实例

## 开发

### 构建

```bash
mvn clean package
```

### 运行测试

```bash
mvn test
```

## 开发计划

详见 `plan.md` 文件。
