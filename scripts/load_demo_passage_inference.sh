#!/usr/bin/env bash
# ============================================================
# 读取 .env 中的 DB_* 配置，连接 MySQL 并执行演示数据 SQL
# 插入 PASS_LATE_001 等数据，使推理引擎得到 5 cycles, 6 rules fired
# ============================================================

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
SQL_FILE="${SCRIPT_DIR}/insert_demo_passage_inference.sql"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: .env not found at $ENV_FILE"
  exit 1
fi

# 只导出 DB_ 开头的变量，跳过注释和空行
export $(grep -v '^#' "$ENV_FILE" | grep -v '^[[:space:]]*$' | grep '^DB_' | xargs)

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-gsdb}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"

if [[ -z "$DB_NAME" ]]; then
  echo "Error: DB_NAME not set in .env"
  exit 1
fi

echo "Connecting to MySQL: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
if [[ -n "$DB_PASSWORD" ]]; then
  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < "$SQL_FILE"
else
  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" < "$SQL_FILE"
fi
echo "Done. Run inference on Passage ID: PASS_LATE_001"
