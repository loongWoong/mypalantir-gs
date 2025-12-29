#!/bin/bash

# MyPalantir 启动脚本

echo "=========================================="
echo "MyPalantir - 启动服务"
echo "=========================================="
echo ""

# 检查 Web 构建
if [ ! -f "web/dist/index.html" ]; then
    echo "⚠️  Web UI 未构建，正在构建..."
    cd web
    npm install
    npm run build
    cd ..
    echo "✓ Web UI 构建完成"
    echo ""
fi

# 启动服务
echo "🚀 启动服务器..."
echo "   Web 界面: http://localhost:8080"
echo "   API 端点: http://localhost:8080/api/v1"
echo "   健康检查: http://localhost:8080/health"
echo ""
echo "按 Ctrl+C 停止服务"
echo ""

mvn spring-boot:run

