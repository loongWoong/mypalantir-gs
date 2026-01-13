# DBT 运行脚本（解决 Windows 编码问题）
# 设置 UTF-8 编码环境变量
$env:PYTHONIOENCODING = "utf-8"
$env:PYTHONUTF8 = "1"

# 切换到脚本所在目录
Set-Location $PSScriptRoot

# 运行 DBT 命令，传递所有参数
& dbt $args
