# MyPalantir Frontend

仿照 Palantir Foundry Ontology 的前端界面。

## 功能特性

- **Schema 浏览器**：查看对象类型、属性和关系类型的定义
- **实例管理**：创建、查看、编辑、删除实例数据
- **关系管理**：查看和管理对象之间的关系
- **数据验证**：基于 Schema 定义的数据验证

## 技术栈

- React 18
- TypeScript
- Vite
- Tailwind CSS
- React Router
- Axios
- Heroicons

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
frontend/
├── src/
│   ├── api/           # API 客户端
│   ├── components/    # 组件
│   ├── pages/         # 页面
│   ├── App.tsx        # 主应用
│   └── main.tsx       # 入口文件
├── public/            # 静态资源
└── package.json
```

## 页面说明

### Schema Browser (`/schema`)
- 查看所有对象类型和关系类型
- 查看对象类型的属性定义
- 查看关系类型的详细信息

### Instance List (`/instances/:objectType`)
- 查看指定对象类型的所有实例
- 创建新实例
- 编辑和删除实例

### Instance Detail (`/instances/:objectType/:id`)
- 查看实例的详细信息
- 编辑实例
- 查看关联的实例

### Link List (`/links/:linkType`)
- 查看指定关系类型的所有关系
- 查看关系的详细信息

## 使用说明

1. 确保后端服务正在运行（`mvn spring-boot:run` 或 `./scripts/start.sh`）
2. 启动前端开发服务器（`npm run dev`）
3. 在浏览器中访问 `http://localhost:5173`
4. 使用左侧导航栏浏览 Schema 和实例数据

注意：生产环境时，前端已集成到 Spring Boot 应用中，直接访问 `http://localhost:8080` 即可。
