# 前后端集成说明

现在前端和后端已经集成到同一个服务中，只需要启动一个服务即可。

## 配置说明

### 环境变量

在项目根目录创建或编辑 `.env` 文件：

```bash
# 服务器配置
SERVER_PORT=8080
SERVER_MODE=debug

# DSL 配置
DSL_FILE_PATH=./ontology/schema.yaml

# 数据存储配置
DATA_ROOT_PATH=./data

# 前端静态文件路径（默认：./frontend/dist）
FRONTEND_STATIC_PATH=./frontend/dist

# 日志配置
LOG_LEVEL=info
LOG_FILE=./logs/app.log
```

## 使用步骤

### 1. 构建前端

首先需要构建前端：

```bash
cd frontend
npm install
npm run build
```

构建产物会在 `frontend/dist` 目录。

### 2. 启动服务

在项目根目录运行：

```bash
go run cmd/server/main.go
```

或者先构建再运行：

```bash
go build -o bin/server ./cmd/server
./bin/server
```

### 3. 访问应用

- **前端界面**：http://localhost:8080
- **API 端点**：http://localhost:8080/api/v1
- **健康检查**：http://localhost:8080/health

## 路由说明

- `/api/v1/*` - 所有 API 请求
- `/assets/*` - 前端静态资源（CSS、JS 等）
- `/*` - 其他所有请求返回前端 SPA 的 index.html

## 开发模式

### 方式 1：前后端分离（开发时推荐）

开发时，可以分别启动前后端，便于热重载：

**终端 1 - 后端：**
```bash
go run cmd/server/main.go
```

**终端 2 - 前端：**
```bash
cd frontend
npm run dev
```

前端开发服务器会在 `http://localhost:5173` 启动，通过代理访问后端 API。

### 方式 2：集成模式（生产环境）

生产环境使用集成模式，只需启动一个服务：

```bash
# 1. 构建前端
cd frontend && npm run build && cd ..

# 2. 启动服务
go run cmd/server/main.go
```

## 注意事项

1. **前端构建**：每次修改前端代码后，需要重新构建才能在生产环境看到变化
2. **静态文件路径**：确保 `FRONTEND_STATIC_PATH` 指向正确的前端构建目录
3. **API 路径**：前端代码中 API 路径已配置为相对路径 `/api/v1`，会自动使用当前域名

## 故障排查

### 前端页面无法加载

1. 检查前端是否已构建：`ls frontend/dist/index.html`
2. 检查环境变量 `FRONTEND_STATIC_PATH` 是否正确
3. 查看服务器日志，确认静态文件路径

### API 请求失败

1. 检查后端服务是否正常运行
2. 检查 API 路径是否正确（应该是 `/api/v1/...`）
3. 查看浏览器控制台的网络请求

### CORS 错误

如果使用分离模式开发，确保后端 CORS 中间件已启用（默认已启用）。

