#!/bin/bash

# 快速验证脚本
BASE_URL="http://localhost:8080/api/v1"

echo "=== 快速功能验证 ==="
echo ""

# 检查服务器
echo "1. 检查服务器..."
if curl -s "$BASE_URL/../health" > /dev/null; then
    echo "✓ 服务器运行正常"
else
    echo "✗ 服务器未运行，请先启动: mvn spring-boot:run"
    echo "  或使用: ./scripts/start.sh"
    exit 1
fi
echo ""

# 测试 Schema API
echo "2. 测试 Schema 查询..."
RESPONSE=$(curl -s "$BASE_URL/schema/object-types")
if echo "$RESPONSE" | grep -q "车辆"; then
    echo "✓ Schema API 正常，找到对象类型"
else
    echo "✗ Schema API 异常"
fi
echo ""

# 测试创建实例
echo "3. 测试创建实例..."
RESPONSE=$(curl -s -X POST "$BASE_URL/instances/车辆" \
    -H "Content-Type: application/json" \
    -d '{"车牌号": "测试001", "车辆类型": "小型客车", "车主姓名": "测试用户"}')
if echo "$RESPONSE" | grep -q "id"; then
    INSTANCE_ID=$(echo "$RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    echo "✓ 实例创建成功，ID: $INSTANCE_ID"
    
    # 测试查询实例
    echo ""
    echo "4. 测试查询实例..."
    QUERY_RESPONSE=$(curl -s "$BASE_URL/instances/车辆")
    if echo "$QUERY_RESPONSE" | grep -q "测试001"; then
        echo "✓ 实例查询成功"
    else
        echo "✗ 实例查询失败"
    fi
else
    echo "✗ 实例创建失败"
fi
echo ""

# 测试数据验证
echo "5. 测试数据验证..."
VALIDATION_RESPONSE=$(curl -s -X POST "$BASE_URL/instances/车辆" \
    -H "Content-Type: application/json" \
    -d '{"车辆类型": "小型客车"}')
if echo "$VALIDATION_RESPONSE" | grep -q "required\|必填"; then
    echo "✓ 数据验证正常（正确拒绝了缺少必填字段的请求）"
else
    echo "✗ 数据验证可能有问题"
fi
echo ""

echo "=== 验证完成 ==="
echo ""
echo "运行完整测试: ./scripts/test_api.sh"
echo "创建测试数据: ./scripts/create_test_data.sh 或 ./scripts/create_test_data.py"

