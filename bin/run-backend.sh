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

RUNTIME_ROOT="${PROJECT_ROOT}/data/backend-runtime"
mkdir -p "${LOG_DIR}" "${RUNTIME_ROOT}" || exit 1
# The shell keeps this open file description for its lifetime on macOS and Linux.
# A second launcher must leave the running daemon's PID file untouched.
exec 9>"${RUNTIME_ROOT}/backend-daemon.lock" || exit 1
python3 -c '
import fcntl, sys
try:
    fcntl.flock(9, fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    sys.exit("天枢后台守护进程已在运行")
' || exit 1
RUN_DIR="$(mktemp -d "${RUNTIME_ROOT}/run.XXXXXXXX")" || exit 1
RUN_JAR="${RUN_DIR}/saas-basics-app.jar"
APP_PID=""

export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"

echo "$$" > "${PID_FILE}"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 天枢服务守护进程已就绪 (PID: $$), 项目根目录: ${PROJECT_ROOT}"

cleanup() {
    if [ -n "${APP_PID}" ]; then
        kill -TERM "${APP_PID}" 2>/dev/null || true
        wait "${APP_PID}" 2>/dev/null || true
    fi
    rm -f "${RUN_JAR}"
    rmdir "${RUN_DIR}"
    if [ -f "${PID_FILE}" ] && [ "$(cat "${PID_FILE}")" = "$$" ]; then
        rm -f "${PID_FILE}"
    fi
}
trap cleanup EXIT
trap 'exit 0' SIGINT SIGTERM

RESTART_COUNT=0

while true; do
    if [ ! -f "${JAR_FILE}" ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON ERROR] 未找到可执行 Jar 文件: ${JAR_FILE}"
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 请先在项目根目录运行 make backend-build"
        sleep 5
        continue
    fi

    # Spring Boot 按需读取嵌套 JAR。每个 JVM 使用独立副本，避免重新打包影响正在提供服务的进程。
    if ! cp "${JAR_FILE}" "${RUN_JAR}" ||
       ! unzip -tqq "${RUN_JAR}" >/dev/null ||
       ! unzip -p "${RUN_JAR}" META-INF/MANIFEST.MF | grep -q '^Start-Class: com\.saasbasics\.platform\.SaasBasicsApplication[[:space:]]*$'; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 构建包尚未就绪，稍后重试。" >&2
        sleep 5
        continue
    fi
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 正在启动天枢云基主服务 (第 $((RESTART_COUNT + 1)) 次启动)..."
    START_TIME=$(date +%s)

    # 启动 Spring Boot 主应用并等待其退出
    "${PROJECT_ROOT}/bin/with-jdk" java -Dfile.encoding=UTF-8 \
         "-Dspring.config.additional-location=optional:file:${PROJECT_ROOT}/.env[.properties]" \
         -Dserver.port=8080 \
         -jar "${RUN_JAR}" 9>&- &
    APP_PID=$!
    wait "${APP_PID}"

    EXIT_CODE=$?
    APP_PID=""
    END_TIME=$(date +%s)
    RUN_DURATION=$((END_TIME - START_TIME))

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 主服务进程已退出，退出码: ${EXIT_CODE}，运行持续时间: ${RUN_DURATION}秒"

    RESTART_COUNT=$((RESTART_COUNT + 1))
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DAEMON] 自愈机制生效，2秒后自动重新拉起新实例..."
    sleep 2
done
