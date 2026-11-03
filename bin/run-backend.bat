@echo off
rem ==============================================================================
rem 天枢云基 · 核心业务主服务高可用自愈守护进程 (Windows Server / Local Windows)
rem 用法: 双击运行，或在 cmd / PowerShell 中执行: bin\run-backend.bat
rem ==============================================================================
chcp 65001 >nul
title 天枢云基 · 服务守护进程 (Tianshu Daemon)

set "PROJECT_ROOT=%~dp0.."
set "JAR_FILE=%PROJECT_ROOT%\server\saas-basics-app\target\saas-basics-app-0.1.0-SNAPSHOT.jar"
set "LOG_DIR=%PROJECT_ROOT%\logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if "%DB_PASSWORD%"=="" set "DB_PASSWORD=19990518suo"

echo ==============================================================================
echo [DAEMON] 天枢云基服务自愈守护进程已启动
echo [DAEMON] 项目根目录: %PROJECT_ROOT%
echo [DAEMON] 目标 Jar:   %JAR_FILE%
echo ==============================================================================

set /a RESTART_COUNT=0

:loop
if not exist "%JAR_FILE%" (
    echo [DAEMON ERROR] 未找到可执行 Jar 文件: %JAR_FILE%
    echo [DAEMON] 请先执行: mvn clean package -DskipTests
    timeout /t 5 >nul
    goto loop
)

set /a RESTART_COUNT+=1
echo [%date% %time%] [DAEMON] 正在启动天枢云基主服务 (第 %RESTART_COUNT% 次启动)...

java -Dfile.encoding=UTF-8 -Dserver.port=8080 -DDB_PASSWORD="%DB_PASSWORD%" -jar "%JAR_FILE%"

set EXIT_CODE=%ERRORLEVEL%
echo [%date% %time%] [DAEMON] 服务进程已退出 (退出码: %EXIT_CODE%)
echo [%date% %time%] [DAEMON] 正在自愈拉起，2秒后重新启动...
timeout /t 2 /nobreak >nul

goto loop
