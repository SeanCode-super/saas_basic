#!/usr/bin/env bash
# ==============================================================================
# 天枢云基 · 核心业务主服务高可用自愈守护进程 (Tianshu Backend Daemon)
# 支持: macOS / Linux / POSIX Shell
# 用法:
#   后台启动守护: nohup ./bin/run-backend.sh > ./logs/daemon.log 2>&1 &
#   前台运行测试: ./bin/run-backend.sh
# ==============================================================================

set -u

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_FILE="${PROJECT_ROOT}/server/saas-basics-app/target/saas-basics-app-0.1.0-SNAPSHOT.jar"
LOG_DIR="${PROJECT_ROOT}/logs"
PID_FILE="${PROJECT_ROOT}/.backend-daemon.pid"

mkdir -p "${LOG_DIR}"

export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"
export DB_PASSWORD="${DB_PASSWORD:-19990518suo}"

echo "$$" > "${PID_FILE}"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 天枢服务守护进程已就绪 (PID: $$), 项目根目录: ${PROJECT_ROOT}"

cleanup() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 接收到退出信号，正在安全终止守护循环..."
    rm -f "${PID_FILE}"
    exit 0
}
trap cleanup SIGINT SIGTERM

RESTART_COUNT=0

while true; do
    if [ ! -f "${JAR_FILE}" ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON ERROR] 未找到可执行 Jar 文件: ${JAR_FILE}"
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 请先在项目根目录运行: mvn clean package -DskipTests"
        sleep 5
        continue
    fi

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 正在启动天枢云基主服务 (第 $((RESTART_COUNT + 1)) 次启动)..."
    START_TIME=$(date +%s)

    # 启动 Spring Boot 主应用并等待其退出
    java -Dfile.encoding=UTF-8 \
         -Dserver.port=8080 \
         -DDB_PASSWORD="${DB_PASSWORD}" \
         -jar "${JAR_FILE}"

    EXIT_CODE=$?
    END_TIME=$(date +%s)
    RUN_DURATION=$((END_TIME - START_TIME))

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 主服务进程已退出，退出码: ${EXIT_CODE}，运行持续时间: ${RUN_DURATION}秒"

    RESTART_COUNT=$((RESTART_COUNT + 1))
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 自愈机制生效，2秒后自动重新拉起新实例..."
    sleep 2
done
