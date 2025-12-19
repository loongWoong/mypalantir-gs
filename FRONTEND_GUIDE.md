# 前端使用指南

## 快速开始

### 1. 启动后端服务

```bash
# 在项目根目录
go run cmd/server/main.go
```

后端将在 `http://localhost:8080` 启动。

### 2. 启动前端开发服务器

```bash
# 在 frontend 目录
cd frontend
npm install  # 如果还没安装依赖
npm run dev
```

前端将在 `http://localhost:5173` 启动。

### 3. 访问应用

在浏览器中打开 `http://localhost:5173`

## 功能说明

### Schema 浏览器 (`/schema`)

- **左侧面板**：显示所有对象类型列表
- **中间面板**：显示选中对象类型的详细信息
  - 对象类型名称和描述
  - 所有属性的定义（名称、类型、是否必填、约束等）
- **右侧面板**：显示所有关系类型列表
  - 点击关系类型可查看详细信息

### 实例管理 (`/instances/:objectType`)

- **列表视图**：显示该对象类型的所有实例
  - 表格形式展示实例数据
  - 支持分页浏览
- **创建实例**：点击 "Create Instance" 按钮
  - 弹出表单，根据 Schema 定义自动生成字段
  - 自动验证数据类型和约束
- **编辑实例**：点击表格中的编辑图标
  - 弹出编辑表单
- **删除实例**：点击表格中的删除图标
  - 确认后删除

### 实例详情 (`/instances/:objectType/:id`)

- **属性展示**：显示实例的所有属性值
- **编辑功能**：点击 "Edit" 按钮修改实例
- **删除功能**：点击 "Delete" 按钮删除实例
- **关联实例**：如果有关系连接，显示关联的实例列表

### 关系管理 (`/links/:linkType`)

- **关系列表**：显示该关系类型的所有关系
- **关系信息**：显示源对象、目标对象和关系属性

## 界面特点

1. **左侧导航栏**
   - Schema 总览
   - 所有对象类型（可点击跳转到实例列表）
   - 所有关系类型（可点击跳转到关系列表）

2. **响应式设计**
   - 支持桌面和移动设备
   - 侧边栏可折叠

3. **数据验证**
   - 基于 Schema 定义的自动验证
   - 实时错误提示
   - 必填字段标记

4. **现代化 UI**
   - 使用 Tailwind CSS
   - 清晰的视觉层次
   - 友好的交互体验

## 技术栈

- **React 18** - UI 框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **Tailwind CSS** - 样式框架
- **React Router** - 路由管理
- **Axios** - HTTP 客户端
- **Heroicons** - 图标库

## 开发说明

### 项目结构

```
frontend/
├── src/
│   ├── api/
│   │   └── client.ts          # API 客户端和类型定义
│   ├── components/
│   │   ├── Layout.tsx         # 布局组件（侧边栏+主内容）
│   │   └── InstanceForm.tsx   # 实例创建/编辑表单
│   ├── pages/
│   │   ├── SchemaBrowser.tsx  # Schema 浏览器
│   │   ├── InstanceList.tsx   # 实例列表
│   │   ├── InstanceDetail.tsx # 实例详情
│   │   └── LinkList.tsx       # 关系列表
│   ├── App.tsx                # 主应用组件
│   ├── main.tsx               # 入口文件
│   └── index.css              # 全局样式
├── public/                     # 静态资源
└── package.json
```

### API 集成

所有 API 调用都通过 `src/api/client.ts` 中的客户端进行：

- `schemaApi` - Schema 查询 API
- `instanceApi` - 实例 CRUD API
- `linkApi` - 关系 CRUD API

### 环境变量

在 `frontend/.env` 中配置 API 地址：

```
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

## 构建生产版本

```bash
cd frontend
npm run build
```

构建产物在 `frontend/dist` 目录。

## 注意事项

1. 确保后端服务正在运行
2. 检查 API 地址配置是否正确
3. 如果遇到 CORS 问题，检查后端 CORS 配置

