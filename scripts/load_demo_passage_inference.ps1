# ============================================================
# 读取 .env 中的 DB_* 配置，连接 MySQL 并执行演示数据 SQL
# 插入 PASS_LATE_001 等数据，使推理引擎得到 5 cycles, 6 rules fired
# ============================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $ProjectRoot ".env"
$SqlFile = Join-Path $PSScriptRoot "insert_demo_passage_inference.sql"

if (-not (Test-Path $EnvFile)) {
    Write-Error ".env not found at $EnvFile"
    exit 1
}

$dbHost = "127.0.0.1"
$dbPort = "3306"
$dbName = "gsdb"
$dbUser = "root"
$dbPassword = ""
$mysqlBin = ""   # 可选：.env 中 MYSQL_BIN 或 MYSQL_HOME，未设置则用 PATH 中的 mysql

Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*#') { return }
    if ($_ -match '^\s*$') { return }
    if ($_ -match '^DB_HOST=(.+)$') { $script:dbHost = $Matches[1].Trim() }
    if ($_ -match '^DB_PORT=(.+)$') { $script:dbPort = $Matches[1].Trim() }
    if ($_ -match '^DB_NAME=(.+)$') { $script:dbName = $Matches[1].Trim() }
    if ($_ -match '^DB_USER=(.+)$') { $script:dbUser = $Matches[1].Trim() }
    if ($_ -match '^DB_PASSWORD=(.+)$') { $script:dbPassword = $Matches[1].Trim() }
    if ($_ -match '^MYSQL_BIN=(.+)$') { $script:mysqlBin = $Matches[1].Trim() }
    if ($_ -match '^MYSQL_HOME=(.+)$') { $script:mysqlBin = (Join-Path $Matches[1].Trim() "bin\mysql.exe") }
}

# 确定 mysql 可执行文件路径（未在 PATH 时需在 .env 中设置 MYSQL_BIN 或 MYSQL_HOME）
$mysqlExe = "mysql"
if ($mysqlBin) {
    if (Test-Path $mysqlBin -PathType Leaf) {
        $mysqlExe = $mysqlBin
    } elseif (Test-Path (Join-Path $mysqlBin "mysql.exe") -PathType Leaf) {
        $mysqlExe = Join-Path $mysqlBin "mysql.exe"
    }
} else {
    $found = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $found) {
        Write-Host "mysql not found. Either:" -ForegroundColor Yellow
        Write-Host "  1) Add MySQL bin directory to system PATH"
        Write-Host "  2) In .env set MYSQL_BIN=path\to\mysql.exe or MYSQL_HOME=MySQL install dir"
        Write-Host "     e.g. MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
        exit 1
    }
}

Write-Host "Connecting to MySQL: ${dbUser}@${dbHost}:${dbPort}/${dbName}"
$sql = Get-Content $SqlFile -Raw
if ($dbPassword) {
    $sql | & $mysqlExe -h $dbHost -P $dbPort -u $dbUser "-p$dbPassword" $dbName
} else {
    $sql | & $mysqlExe -h $dbHost -P $dbPort -u $dbUser $dbName
}
Write-Host "Done. Run inference on Passage ID: PASS_LATE_001" -ForegroundColor Green
