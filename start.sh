#!/bin/bash

# MyPalantir 启动脚本

echo "=========================================="
echo "MyPalantir - 启动服务"
echo "=========================================="
echo ""

# 检查前端构建
if [ ! -f "frontend/dist/index.html" ]; then
    echo "⚠️  前端未构建，正在构建..."
    cd frontend
    npm install
    npm run build
    cd ..
    echo "✓ 前端构建完成"
    echo ""
fi

# 检查 Go 依赖
if [ ! -d "vendor" ] && [ ! -f "go.sum" ]; then
    echo "📦 安装 Go 依赖..."
    go mod download
    echo "✓ Go 依赖安装完成"
    echo ""
fi

# 启动服务
echo "🚀 启动服务器..."
echo "   前端界面: http://localhost:8080"
echo "   API 端点: http://localhost:8080/api/v1"
echo "   健康检查: http://localhost:8080/health"
echo ""
echo "按 Ctrl+C 停止服务"
echo ""

go run cmd/server/main.go

