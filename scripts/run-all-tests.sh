#!/usr/bin/env bash
# MyPalantir 自动化测试一键运行脚本 (Bash)
# 顺序执行：后端 UT/API -> 前端 UT -> E2E/UI，报告输出到 test-reports/

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "=== 1/3 后端测试 (Maven) ==="
mvn test -q

echo ""
echo "=== 2/3 前端单元测试与覆盖率 (Vitest) ==="
cd "$ROOT/web"
npm run test:coverage

echo ""
echo "=== 3/3 E2E/UI 测试 (Playwright) ==="
npm run test:e2e

cd "$ROOT"
echo ""
echo "全部测试通过。报告目录: $ROOT/test-reports"
echo "打开汇总页: test-reports/index.html"
