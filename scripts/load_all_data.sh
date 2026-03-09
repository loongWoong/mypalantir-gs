#!/bin/bash
# 批量加载所有数据到 H2 数据库
# 用法: ./scripts/load_all_data.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JDBC_URL="jdbc:h2:file:${PROJECT_DIR}/data/h2/mypalantir"
CLASSPATH="$PROJECT_DIR/target/classes:$(cd "$PROJECT_DIR" && mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null)"

# 临时目录
TMP_DIR=$(mktemp -d)
trap "rm -rf $TMP_DIR" EXIT

echo "=== 加载数据到 H2 数据库 ==="
echo "JDBC URL: $JDBC_URL"
echo ""

# 需要加载的文件（按依赖顺序）
FILES=(
    "TBL_ENTRYWASTE.sql"
    "TBL_EXITWASTE.sql"
    "TBL_GANTRYWASTE.sql"
    "TBL_PATH.sql"
    "TBL_PATHDETAIL.sql"
    "TBL_SPLITDETAIL.sql"
)

for FILE in "${FILES[@]}"; do
    SRC="$SCRIPT_DIR/$FILE"
    if [ ! -f "$SRC" ]; then
        echo "⚠ 跳过不存在的文件: $FILE"
        continue
    fi

    echo "→ 处理 $FILE ..."

    # 去掉 HIGHLINK. 前缀，修正 VALUES' 为 VALUES '
    TMP_FILE="$TMP_DIR/$FILE"
    sed -e 's/HIGHLINK\.//g' -e "s/VALUES'/VALUES '/g" "$SRC" > "$TMP_FILE"

    # 通过 H2 Shell 执行
    java -cp "$CLASSPATH" org.h2.tools.Shell \
        -url "$JDBC_URL" -user sa -password "" \
        -sql "RUNSCRIPT FROM '$TMP_FILE';" 2>&1 | tail -3

    echo "  ✓ $FILE 加载完成"
done

echo ""
echo "=== 验证数据 ==="
java -cp "$CLASSPATH" org.h2.tools.Shell \
    -url "$JDBC_URL" -user sa -password "" \
    -sql "
SELECT 'TBL_PATH' AS TBL, COUNT(*) AS CNT FROM TBL_PATH UNION ALL
SELECT 'TBL_ENTRYWASTE', COUNT(*) FROM TBL_ENTRYWASTE UNION ALL
SELECT 'TBL_EXITWASTE', COUNT(*) FROM TBL_EXITWASTE UNION ALL
SELECT 'TBL_GANTRYWASTE', COUNT(*) FROM TBL_GANTRYWASTE UNION ALL
SELECT 'TBL_PATHDETAIL', COUNT(*) FROM TBL_PATHDETAIL UNION ALL
SELECT 'TBL_SPLITDETAIL', COUNT(*) FROM TBL_SPLITDETAIL;
" 2>&1

echo ""
echo "✓ 数据加载完成！"
