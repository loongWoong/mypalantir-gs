@echo off
REM DBT 运行脚本（解决 Windows 编码问题）
REM 设置 UTF-8 编码环境变量
set PYTHONIOENCODING=utf-8
set PYTHONUTF8=1

REM 切换到脚本所在目录
cd /d %~dp0

REM 运行 DBT 命令，传递所有参数
dbt %*
