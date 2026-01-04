# MyPalantir Frontend

仿照 Palantir Foundry Ontology 的前端界面，基于 React + TypeScript 构建的现代化 Web 应用。

## 功能特性

### 核心功能
- **Schema 浏览器**：图形化查看对象类型、关系类型及其属性映射，支持交互式过滤
- **Schema 关系图**：力导向图可视化展示 Schema 定义，支持节点拖动和关系连线
- **实例管理**：创建、查看、编辑、删除实例数据
- **多条件检索**：支持多属性条件过滤查询实例
- **关系管理**：查看和管理对象之间的关系，支持关系同步
- **数据映射界面**：ER 图形式配置数据库表列与对象属性的映射
- **实例关系图**：力导向图可视化展示实例及其关系，支持血缘查询
- **工作空间管理**：创建工作空间，对对象类型和关系类型进行分组管理
- **查询界面**：支持 OntologyQuery DSL 查询，查看查询结果

### 用户体验
- **响应式设计**：适配不同屏幕尺寸
- **实时反馈**：Toast 通知、加载状态、错误处理
- **交互式过滤**：在 Schema 浏览器中点击对象/关系进行关联过滤
- **图形可视化**：使用 ForceGraph2D 实现力导向图
- **中文支持**：支持 display_name 显示中文名称

## 技术栈

### 核心框架
- **React 18** - UI 框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具和开发服务器

### UI 库
- **Tailwind CSS** - 样式框架
- **Heroicons** - 图标库

### 路由和状态
- **React Router** - 路由管理
- **React Context** - 状态管理（工作空间上下文）

### 数据获取
- **Axios** - HTTP 客户端

### 可视化
- **react-force-graph-2d** - 力导向图可视化库

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 配置 API 地址

编辑 `.env` 文件，设置后端 API 地址：

```
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

### 3. 启动开发服务器

```bash
npm run dev
```

前端将在 `http://localhost:5173` 启动。

### 4. 构建生产版本

```bash
npm run build
```

## 项目结构

```
web/
├── src/
│   ├── api/              # API 客户端
│   │   └── client.ts     # API 客户端定义
│   ├── components/       # 可复用组件
│   │   ├── Layout.tsx           # 主布局（包含侧边栏和工作空间选择器）
│   │   ├── InstanceForm.tsx     # 实例表单
│   │   ├── DataMappingDialog.tsx # 数据映射对话框
│   │   └── WorkspaceDialog.tsx  # 工作空间对话框
│   ├── pages/            # 页面组件
│   │   ├── SchemaBrowser.tsx    # Schema 浏览器（图形化展示）
│   │   ├── SchemaGraphView.tsx  # Schema 关系图
│   │   ├── InstanceList.tsx    # 实例列表（支持多条件过滤）
│   │   ├── InstanceDetail.tsx  # 实例详情
│   │   ├── LinkList.tsx         # 关系列表（支持关系同步）
│   │   ├── GraphView.tsx        # 实例关系图（支持血缘查询）
│   │   └── DataMapping.tsx      # 数据映射页面
│   ├── WorkspaceContext.tsx     # 工作空间上下文
│   ├── App.tsx           # 主应用
│   └── main.tsx          # 入口文件
├── public/               # 静态资源
├── dist/                # 构建产物
├── package.json
└── vite.config.ts        # Vite 配置
```

## 页面说明

### Schema Browser (`/schema`)
- 查看所有对象类型和关系类型
- 查看对象类型的属性定义
- 查看关系类型的详细信息，包括属性映射关系图
- 交互式过滤：点击对象类型过滤相关关系类型，点击关系类型过滤相关对象类型
- 查看数据源映射信息

### Schema Graph View (`/schema-graph`)
- 力导向图可视化展示 Schema 定义
- 支持节点拖动和固定位置
- 显示对象类型和关系类型的连接关系
- 点击节点查看详情
- 支持按显示名称展示

### Instance List (`/instances/:objectType`)
- 查看指定对象类型的所有实例
- 创建新实例
- 编辑和删除实例
- 多条件过滤查询
- 从映射数据源查询实例
- 关联数据源和同步抽取功能（非系统对象类型）

### Instance Detail (`/instances/:objectType/:id`)
- 查看实例的详细信息
- 编辑实例
- 查看关联的实例
- 查看实例图谱（跳转到 Graph View）
- Toast 通知反馈

### Link List (`/links/:linkType`)
- 查看指定关系类型的所有关系
- 查看关系的详细信息
- 关系同步功能（基于属性映射规则）

### Graph View (`/graph/:objectType/:id`)
- 力导向图可视化展示实例及其关系
- 支持血缘查询模式：
  - **直接关系**：只显示直接连接的节点
  - **正向血缘**：从当前节点向后递归查询所有下游节点
  - **反向血缘**：从当前节点向前递归查询所有上游节点
  - **全链血缘**：从当前节点前后递归查询所有相关节点
- 支持节点拖动和固定位置
- 点击节点查看详情
- 支持按显示名称展示

### Data Mapping (`/data-mapping`)
- ER 图形式配置数据库表列与对象属性的映射
- 支持自动匹配建议
- 支持手动调整映射关系
- Toast 通知反馈

## 使用说明

### 开发模式

1. **安装依赖**：
   ```bash
   npm install
   ```

2. **配置 API 地址**（可选，默认使用 `http://localhost:8080/api/v1`）：
   编辑 `.env` 文件：
   ```
   VITE_API_BASE_URL=http://localhost:8080/api/v1
   ```

3. **启动开发服务器**：
   ```bash
   npm run dev
   ```
   前端将在 `http://localhost:5173` 启动，支持热重载。

4. **确保后端服务正在运行**：
   ```bash
   mvn spring-boot:run
   # 或
   ./scripts/start.sh
   ```

### 生产构建

1. **构建生产版本**：
   ```bash
   npm run build
   ```
   构建产物将输出到 `dist/` 目录。

2. **集成到 Spring Boot**：
   - 开发模式：Spring Boot 从 `./web/dist` 提供静态文件
   - 生产模式：Maven 构建时自动复制到 `target/classes/static/`，打包到 JAR 文件

3. **访问应用**：
   - 开发模式：`http://localhost:5173`（前端开发服务器）或 `http://localhost:8080`（Spring Boot）
   - 生产模式：`http://localhost:8080`（Spring Boot 集成）

### 工作空间使用

1. **创建工作空间**：
   - 在左侧导航栏点击工作空间下拉菜单
   - 选择"创建工作空间"
   - 填写工作空间信息，选择包含的对象类型和关系类型

2. **切换工作空间**：
   - 在左侧导航栏顶部选择工作空间
   - 界面会自动过滤显示相关内容

3. **系统工作空间**：
   - 系统内置工作空间包含系统对象和关系
   - 不可编辑，自动隐藏"关联数据源"和"同步抽取"按钮

### 数据映射使用

1. **关联数据源**：
   - 在业务对象类型的 Instances 页面
   - 点击"关联数据源"按钮
   - 选择数据源和表

2. **配置映射**：
   - 在 ER 图界面中手动连接表列和对象属性
   - 系统提供自动匹配建议
   - 保存映射关系

3. **同步抽取**：
   - 点击"同步抽取"按钮
   - 选择已建立的映射关系
   - 系统会根据映射关系从数据库抽取数据

## 开发指南

### 代码规范

- 使用 TypeScript 严格模式
- 遵循 React Hooks 最佳实践
- 使用 Tailwind CSS 进行样式设计
- 组件命名使用 PascalCase
- 文件命名使用 PascalCase（组件）或 camelCase（工具函数）

### 常见问题

1. **API 请求失败**：
   - 检查后端服务是否运行
   - 检查 `VITE_API_BASE_URL` 配置是否正确
   - 检查 CORS 配置

2. **构建错误**：
   - 确保 Node.js 版本 >= 18
   - 删除 `node_modules` 和 `package-lock.json`，重新安装依赖

3. **热重载不工作**：
   - 检查文件是否在 `src/` 目录下
   - 检查 Vite 配置是否正确

## 相关文档

- [主项目 README](../README.md) - 系统整体说明
- [CHANGELOG](../CHANGELOG.md) - 详细功能变动记录
- [CHANGELOG_SUMMARY](../CHANGELOG_SUMMARY.md) - 功能变动摘要
