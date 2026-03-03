# MyPalantir 自动化测试一键运行脚本 (PowerShell)
# 顺序执行：后端 UT/API -> 前端 UT -> E2E/UI，报告输出到 test-reports/

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

Write-Host "=== 1/3 后端测试 (Maven) ===" -ForegroundColor Cyan
& mvn test -q
if ($LASTEXITCODE -ne 0) { Write-Host "后端测试失败" -ForegroundColor Red; exit 1 }

Write-Host "`n=== 2/3 前端单元测试与覆盖率 (Vitest) ===" -ForegroundColor Cyan
Set-Location "$root\web"
& npm run test:coverage
if ($LASTEXITCODE -ne 0) { Write-Host "前端单元测试失败" -ForegroundColor Red; exit 1 }

Write-Host "`n=== 3/3 E2E/UI 测试 (Playwright) ===" -ForegroundColor Cyan
& npm run test:e2e
if ($LASTEXITCODE -ne 0) { Write-Host "E2E 测试失败" -ForegroundColor Red; exit 1 }

Set-Location $root
Write-Host "`n全部测试通过。报告目录: $root\test-reports" -ForegroundColor Green
Write-Host "打开汇总页: test-reports\index.html" -ForegroundColor Gray
