#!/bin/bash

# API 测试脚本
BASE_URL="http://localhost:8080/api/v1"

echo "=========================================="
echo "MyPalantir API 功能验证测试"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试函数
test_api() {
    local name=$1
    local method=$2
    local url=$3
    local data=$4
    
    echo -e "${YELLOW}测试: ${name}${NC}"
    echo "请求: $method $url"
    
    if [ -z "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$url")
    else
        response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$url" \
            -H "Content-Type: application/json" \
            -d "$data")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo -e "${GREEN}✓ 成功 (HTTP $http_code)${NC}"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
    else
        echo -e "${RED}✗ 失败 (HTTP $http_code)${NC}"
        echo "$body"
    fi
    echo ""
}

# 检查服务器是否运行
echo "检查服务器状态..."
if ! curl -s "$BASE_URL/../health" > /dev/null; then
    echo -e "${RED}错误: 服务器未运行，请先启动服务器:${NC}"
    echo "  go run cmd/server/main.go"
    exit 1
fi
echo -e "${GREEN}服务器运行正常${NC}"
echo ""

# ==========================================
# 1. Schema 查询 API 测试
# ==========================================
echo "=========================================="
echo "1. Schema 查询 API 测试"
echo "=========================================="

test_api "列出所有对象类型" "GET" "/schema/object-types"
test_api "获取 Person 对象类型详情" "GET" "/schema/object-types/Person"
test_api "获取 Person 的所有属性" "GET" "/schema/object-types/Person/properties"
test_api "获取 Person 的出边关系" "GET" "/schema/object-types/Person/outgoing-links"
test_api "获取 Person 的入边关系" "GET" "/schema/object-types/Person/incoming-links"
test_api "列出所有关系类型" "GET" "/schema/link-types"
test_api "获取 worksAt 关系类型详情" "GET" "/schema/link-types/worksAt"

# ==========================================
# 2. 实例 CRUD API 测试
# ==========================================
echo "=========================================="
echo "2. 实例 CRUD API 测试"
echo "=========================================="

# 创建 Person 实例
PERSON_DATA1='{"name": "张三", "age": 30, "email": "zhangsan@example.com", "metadata": {"department": "Engineering"}}'
test_api "创建 Person 实例 (张三)" "POST" "/instances/Person" "$PERSON_DATA1"

# 获取刚创建的实例 ID（需要从响应中提取）
echo "等待 1 秒..."
sleep 1

# 列出所有 Person 实例
test_api "列出所有 Person 实例" "GET" "/instances/Person"

# 创建 Company 实例
COMPANY_DATA='{"name": "科技公司", "founded_year": 2010, "address": "北京市"}'
test_api "创建 Company 实例" "POST" "/instances/Company" "$COMPANY_DATA"

echo "等待 1 秒..."
sleep 1

# 获取实例列表以获取 ID
PERSON_LIST=$(curl -s "$BASE_URL/instances/Person")
PERSON_ID=$(echo "$PERSON_LIST" | jq -r '.data.items[0].id' 2>/dev/null)
COMPANY_LIST=$(curl -s "$BASE_URL/instances/Company")
COMPANY_ID=$(echo "$COMPANY_LIST" | jq -r '.data.items[0].id' 2>/dev/null)

if [ -n "$PERSON_ID" ] && [ "$PERSON_ID" != "null" ]; then
    echo -e "${GREEN}获取到 Person ID: $PERSON_ID${NC}"
    test_api "获取 Person 实例详情" "GET" "/instances/Person/$PERSON_ID"
    
    # 更新实例
    UPDATE_DATA='{"age": 31, "metadata": {"department": "Engineering", "level": "Senior"}}'
    test_api "更新 Person 实例" "PUT" "/instances/Person/$PERSON_ID" "$UPDATE_DATA"
    
    # 验证更新
    test_api "验证更新后的 Person 实例" "GET" "/instances/Person/$PERSON_ID"
else
    echo -e "${RED}无法获取 Person ID，跳过更新和删除测试${NC}"
fi

# ==========================================
# 3. 关系 CRUD API 测试
# ==========================================
echo "=========================================="
echo "3. 关系 CRUD API 测试"
echo "=========================================="

if [ -n "$PERSON_ID" ] && [ -n "$COMPANY_ID" ] && [ "$PERSON_ID" != "null" ] && [ "$COMPANY_ID" != "null" ]; then
    # 创建关系
    LINK_DATA="{\"source_id\": \"$PERSON_ID\", \"target_id\": \"$COMPANY_ID\", \"properties\": {\"start_date\": \"2020-01-01\", \"position\": \"Software Engineer\"}}"
    test_api "创建 worksAt 关系" "POST" "/links/worksAt" "$LINK_DATA"
    
    echo "等待 1 秒..."
    sleep 1
    
    # 列出关系
    test_api "列出所有 worksAt 关系" "GET" "/links/worksAt"
    
    # 获取关系 ID
    LINK_LIST=$(curl -s "$BASE_URL/links/worksAt")
    LINK_ID=$(echo "$LINK_LIST" | jq -r '.data.items[0].id' 2>/dev/null)
    
    if [ -n "$LINK_ID" ] && [ "$LINK_ID" != "null" ]; then
        echo -e "${GREEN}获取到 Link ID: $LINK_ID${NC}"
        test_api "获取关系详情" "GET" "/links/worksAt/$LINK_ID"
        
        # 更新关系
        LINK_UPDATE='{"properties": {"position": "Senior Software Engineer"}}'
        test_api "更新关系属性" "PUT" "/links/worksAt/$LINK_ID" "$LINK_UPDATE"
        
        # 查询实例的关系
        test_api "查询 Person 实例的关系" "GET" "/instances/Person/$PERSON_ID/links/worksAt"
        
        # 查询关联的实例
        test_api "查询 Person 关联的 Company 实例" "GET" "/instances/Person/$PERSON_ID/connected/worksAt?direction=outgoing"
    fi
else
    echo -e "${RED}无法获取实例 ID，跳过关系测试${NC}"
fi

# ==========================================
# 4. 数据验证测试
# ==========================================
echo "=========================================="
echo "4. 数据验证测试"
echo "=========================================="

# 测试必填字段验证
test_api "测试缺少必填字段 (缺少 name)" "POST" "/instances/Person" '{"age": 30}'

# 测试数据类型验证
test_api "测试错误数据类型 (age 应该是 int)" "POST" "/instances/Person" '{"name": "测试", "age": "not_a_number", "email": "test@example.com"}'

# 测试约束验证
test_api "测试约束验证 (age 超出范围)" "POST" "/instances/Person" '{"name": "测试", "age": 200, "email": "test@example.com"}'

# 测试邮箱格式验证
test_api "测试邮箱格式验证 (无效邮箱)" "POST" "/instances/Person" '{"name": "测试", "age": 30, "email": "invalid-email"}'

echo "=========================================="
echo "测试完成！"
echo "=========================================="

