# MyPalantir - Foundry Ontology 仿制项目

一个基于 Go 实现的数据模型管理平台，仿照 Palantir Foundry Ontology 的核心功能。

## 功能特性

- **DSL 模型定义**：使用 YAML 格式定义对象类型、属性和关系类型
- **文件系统存储**：所有数据存储在文件系统中，便于版本控制
- **RESTful API**：提供完整的 API 用于模型查询和实例数据管理
- **数据验证**：完整的模型验证和数据验证机制

## 项目结构

```
mypalantir/
├── cmd/server/          # 应用入口
├── internal/            # 内部包
│   ├── config/         # 配置管理
│   ├── dsl/            # DSL 解析器
│   ├── storage/        # 文件系统存储
│   ├── service/        # 业务逻辑层
│   ├── handler/        # HTTP 处理器
│   └── middleware/     # 中间件
├── ontology/           # DSL 文件目录
├── data/               # 实例数据目录
└── pkg/                # 公共包
```

## 快速开始

### 1. 安装依赖

**后端：**
```bash
go mod download
```

**前端：**
```bash
cd frontend
npm install
```

### 2. 构建前端

```bash
cd frontend
npm run build
```

### 3. 配置环境变量（可选）

```bash
cp .env.example .env
# 编辑 .env 文件配置相关参数
```

默认配置：
- 服务器端口：8080
- 前端静态文件路径：./frontend/dist
- DSL 文件路径：./ontology/schema.yaml

### 4. 运行服务

```bash
go run cmd/server/main.go
```

服务启动后：
- **前端界面**：http://localhost:8080
- **API 端点**：http://localhost:8080/api/v1
- **健康检查**：http://localhost:8080/health

## 开发模式

开发时建议前后端分离运行，便于热重载：

**终端 1 - 后端：**
```bash
go run cmd/server/main.go
```

**终端 2 - 前端：**
```bash
cd frontend
npm run dev
```

前端开发服务器在 `http://localhost:5173`，会自动代理 API 请求到后端。

## 开发计划

详见 `plan.md` 文件。

