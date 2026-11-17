package com.saasbasics.platform.modules.system.devops.driver;

import com.saasbasics.platform.modules.system.devops.entity.AppServiceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class HostProcessDriver implements ServiceDriver {

    private static final Logger log = LoggerFactory.getLogger(HostProcessDriver.class);
    private final OperatingSystem os = new SystemInfo().getOperatingSystem();
    private final Map<Long, CachedInspect> inspectCache = new ConcurrentHashMap<>();

    private static class CachedInspect {
        final ServiceRuntimeInfo info;
        final long timestamp;
        CachedInspect(ServiceRuntimeInfo info) {
            this.info = info;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Override
    public String getDriverType() {
        return "HOST_PROCESS";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ServiceRuntimeInfo inspect(AppServiceEntity service) {
        if (service.getId() != null) {
            CachedInspect cached = inspectCache.get(service.getId());
            if (cached != null && (System.currentTimeMillis() - cached.timestamp < 1500)) {
                return cached.info;
            }
        }

        ServiceRuntimeInfo info = new ServiceRuntimeInfo();
        Integer port = service.getPort();
        boolean isPortOpen = false;

        if (port != null && port > 0) {
            isPortOpen = checkPortListening(port);
            info.setPortListening(isPortOpen);
        }

        Integer pid = service.getCurrentPid();
        // If stored PID is invalid or null, try discovering it by port
        if ((pid == null || pid <= 0 || !isProcessAlive(pid)) && isPortOpen && port != null) {
            pid = findPidByPort(port);
        }

        if (pid != null && pid > 0 && isProcessAlive(pid)) {
            info.setPid(pid);
            info.setStatus("RUNNING");

            try {
                OSProcess p = os.getProcess(pid);
                if (p != null) {
                    info.setMemoryRssBytes(p.getResidentSetSize());
                    info.setThreadCount(p.getThreadCount());
                    double cpu = p.getProcessCpuLoadCumulative() * 100.0;
                    info.setCpuUsagePercent(Math.round(cpu * 10.0) / 10.0);
                    long upSec = (System.currentTimeMillis() - p.getStartTime()) / 1000L;
                    info.setUptimeSeconds(Math.max(0, upSec));
                }
            } catch (Exception e) {
                log.debug("Failed to get OSHI process metrics for pid {}: {}", pid, e.getMessage());
            }

            // Healthcheck probe
            if (service.getHealthUrl() != null && !service.getHealthUrl().isBlank()) {
                info.setHealthOk(checkHealthUrl(service.getHealthUrl()));
            } else {
                info.setHealthOk(isPortOpen);
            }
        } else {
            info.setStatus("STOPPED");
            info.setHealthOk(false);
            info.setPid(null);
            info.setUptimeSeconds(0L);
            info.setCpuUsagePercent(0.0);
            info.setMemoryRssBytes(0L);
            info.setThreadCount(0);
        }

        if (service.getId() != null) {
            inspectCache.put(service.getId(), new CachedInspect(info));
        }

        return info;
    }

    @Override
    public int start(AppServiceEntity service) throws Exception {
        if (service.getId() != null) {
            inspectCache.remove(service.getId());
        }
        ServiceRuntimeInfo current = inspect(service);
        if ("RUNNING".equalsIgnoreCase(current.getStatus())) {
            throw new IllegalStateException("服务已在运行中 (PID: " + current.getPid() + ", 端口: " + service.getPort() + ")");
        }

        String workDirStr = service.getWorkDir();
        File workDir = new File(workDirStr);
        if (!workDir.exists() || !workDir.isDirectory()) {
            throw new IllegalArgumentException("工作目录不存在: " + workDirStr);
        }

        String logPathStr = service.getLogPath();
        File logFile = null;
        if (logPathStr != null && !logPathStr.isBlank()) {
            logFile = new File(logPathStr);
            File parent = logFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        } else {
            File defaultLogDir = new File(workDir, "logs");
            defaultLogDir.mkdirs();
            logFile = new File(defaultLogDir, service.getServiceCode() + ".log");
        }

        String startCmd = service.getStartCmd();
        if (startCmd == null || startCmd.isBlank()) {
            throw new IllegalArgumentException("启动命令不能为空");
        }

        log.info("Starting service [{}] with cmd: [{}] in [{}]", service.getServiceCode(), startCmd, workDirStr);

        ProcessBuilder pb;
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", startCmd);
        } else {
            // 使用 exec 让目标应用进程直接替换子 shell，确保 PID 和信号直达应用进程本身
            pb = new ProcessBuilder("/bin/bash", "-c", "exec " + startCmd);
        }

        pb.directory(workDir);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

        // Inject current environment PATH and variables
        pb.environment().put("PATH", "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:" + System.getenv("PATH"));
        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword != null && !dbPassword.isBlank()) {
            pb.environment().put("DB_PASSWORD", dbPassword);
        }

        Process process = pb.start();
        long pidLong = process.pid();
        int pid = (int) pidLong;

        // Persist PID to file in workDir
        try {
            Path pidFile = Paths.get(workDirStr, "." + service.getServiceCode() + ".pid");
            Files.writeString(pidFile, String.valueOf(pid));
        } catch (Exception e) {
            log.warn("Failed to write pid file: {}", e.getMessage());
        }

        log.info("Service [{}] spawned with PID: {}. Waiting for readiness...", service.getServiceCode(), pid);

        // 如果配置了端口，等待端口真正就绪（最长 25 秒），未就绪前不提前下发成功响应
        if (service.getPort() != null && service.getPort() > 0) {
            long deadline = System.currentTimeMillis() + 25000L;
            boolean portReady = false;
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive()) {
                    int exitVal = -1;
                    try { exitVal = process.exitValue(); } catch (Exception ignored) {}
                    throw new IllegalStateException("进程异常过早退出 (ExitCode: " + exitVal + ")，请在日志终端查看报错栈");
                }
                if (checkPortListening(service.getPort())) {
                    portReady = true;
                    break;
                }
                Thread.sleep(250);
            }
            if (!portReady && !checkPortListening(service.getPort())) {
                log.warn("Service [{}] did not bind port {} within 25s, but process is alive (PID: {})", service.getServiceCode(), service.getPort(), pid);
            }
        } else {
            Thread.sleep(600);
            if (!process.isAlive()) {
                throw new IllegalStateException("进程异常过早退出");
            }
        }

        if (service.getId() != null) {
            inspectCache.remove(service.getId());
        }

        log.info("Service [{}] successfully started and confirmed ready with PID: {}", service.getServiceCode(), pid);
        return pid;
    }

    @Override
    public void stop(AppServiceEntity service, int timeoutSeconds) throws Exception {
        if (service.getId() != null) {
            inspectCache.remove(service.getId());
        }
        ServiceRuntimeInfo info = inspect(service);
        Integer pid = info.getPid();

        if (pid == null || pid <= 0) {
            // If port is listening, try finding by port
            if (service.getPort() != null) {
                pid = findPidByPort(service.getPort());
            }
        }

        if (pid != null && pid > 0) {
            log.info("Stopping service [{}] (PID: {}) gracefully...", service.getServiceCode(), pid);
            Optional<ProcessHandle> handleOpt = ProcessHandle.of(pid);
            if (handleOpt.isPresent()) {
                ProcessHandle handle = handleOpt.get();
                // Graceful SIGTERM recursively to process tree
                handle.descendants().forEach(ProcessHandle::destroy);
                handle.destroy();

                try {
                    handle.onExit().get(Math.max(5, timeoutSeconds), TimeUnit.SECONDS);
                    log.info("Service [{}] exited gracefully.", service.getServiceCode());
                } catch (Exception timeoutEx) {
                    log.warn("Service [{}] did not exit within timeout, destroying forcibly...", service.getServiceCode());
                    handle.descendants().forEach(ProcessHandle::destroyForcibly);
                    handle.destroyForcibly();
                }
            } else {
                // If not found in JVM handle, use OS kill command
                killPidFallback(pid, false);
                Thread.sleep(1000);
                if (isProcessAlive(pid)) {
                    killPidFallback(pid, true);
                }
            }
        }

        // Double check port
        if (service.getPort() != null && checkPortListening(service.getPort())) {
            Integer stubbornPid = findPidByPort(service.getPort());
            if (stubbornPid != null && stubbornPid > 0) {
                log.warn("Port {} still listening by PID {}, killing forcibly.", service.getPort(), stubbornPid);
                killPidFallback(stubbornPid, true);
            }
        }

        // 等待操作系统完全回收端口与 PID（最长 6 秒，毫秒级轮询，一旦彻底退出立即放行）
        long deadline = System.currentTimeMillis() + 6000L;
        while (System.currentTimeMillis() < deadline) {
            boolean portStillListening = service.getPort() != null && checkPortListening(service.getPort());
            boolean pidStillAlive = pid != null && isProcessAlive(pid);
            if (!portStillListening && !pidStillAlive) {
                break;
            }
            Thread.sleep(150);
        }

        // 彻底清除并刷新内存监控缓存为 STOPPED，防止前端轮询拿到旧缓存导致启动按钮短暂禁用
        if (service.getId() != null) {
            inspectCache.remove(service.getId());
            ServiceRuntimeInfo stoppedInfo = ServiceRuntimeInfo.stopped();
            inspectCache.put(service.getId(), new CachedInspect(stoppedInfo));
        }
    }

    @Override
    public void restart(AppServiceEntity service, int timeoutSeconds) throws Exception {
        if (service.getId() != null) {
            inspectCache.remove(service.getId());
        }
        if ("backend-main".equalsIgnoreCase(service.getServiceCode())) {
            long currentPid = ProcessHandle.current().pid();
            Integer targetPid = service.getCurrentPid();
            if (targetPid == null || targetPid == (int) currentPid) {
                log.info("Self-restart for backend-main (current PID: {}). Spawning successor process detached...", currentPid);
                String startCmd = service.getStartCmd();
                File workDir = new File(service.getWorkDir());
                ProcessBuilder pb;
                if (isWindows()) {
                    pb = new ProcessBuilder("cmd.exe", "/c", "start /b " + startCmd);
                } else {
                    pb = new ProcessBuilder("/bin/bash", "-c", "nohup " + startCmd + " > /dev/null 2>&1 &");
                }
                pb.directory(workDir);
                pb.environment().put("PATH", "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:" + System.getenv("PATH"));
                String dbPassword = System.getenv("DB_PASSWORD");
                if (dbPassword != null && !dbPassword.isBlank()) {
                    pb.environment().put("DB_PASSWORD", dbPassword);
                }
                pb.start();

                log.info("Successor process spawned. Exiting current JVM to free port for successor/watchdog...");
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}
                    System.exit(0);
                }).start();
                return;
            }
        }
        stop(service, timeoutSeconds);
        Thread.sleep(1500);
        start(service);
    }

    @Override
    public void kill(AppServiceEntity service) throws Exception {
        if (service.getId() != null) {
            inspectCache.remove(service.getId());
        }
        ServiceRuntimeInfo info = inspect(service);
        Integer pid = info.getPid();
        if (pid != null && pid > 0) {
            ProcessHandle.of(pid).ifPresent(h -> {
                h.descendants().forEach(ProcessHandle::destroyForcibly);
                h.destroyForcibly();
            });
            killPidFallback(pid, true);
        }
        if (service.getPort() != null) {
            Integer pPid = findPidByPort(service.getPort());
            if (pPid != null && pPid > 0) {
                killPidFallback(pPid, true);
            }
        }

        // 等待操作系统完全回收端口与 PID（最长 3 秒）
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            boolean portStillListening = service.getPort() != null && checkPortListening(service.getPort());
            boolean pidStillAlive = pid != null && isProcessAlive(pid);
            if (!portStillListening && !pidStillAlive) {
                break;
            }
            Thread.sleep(100);
        }

        // 彻底清除并刷新内存监控缓存为 STOPPED
        if (service.getId() != null) {
            inspectCache.remove(service.getId());
            ServiceRuntimeInfo stoppedInfo = new ServiceRuntimeInfo();
            stoppedInfo.setStatus("STOPPED");
            stoppedInfo.setPid(null);
            stoppedInfo.setPortListening(false);
            stoppedInfo.setHealthOk(false);
            stoppedInfo.setUptimeSeconds(0L);
            stoppedInfo.setCpuUsagePercent(0.0);
            stoppedInfo.setMemoryRssBytes(0L);
            stoppedInfo.setThreadCount(0);
            inspectCache.put(service.getId(), new CachedInspect(stoppedInfo));
        }
    }

    @Override
    public List<String> tailLogs(AppServiceEntity service, int maxLines) {
        String logPathStr = service.getLogPath();
        if (logPathStr == null || logPathStr.isBlank()) {
            return Collections.singletonList("[SYSTEM] 未配置日志输出路径");
        }

        File file = new File(logPathStr);
        if (!file.exists() || !file.isFile()) {
            return Collections.singletonList("[SYSTEM] 日志文件尚不存在: " + logPathStr);
        }

        List<String> lines = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length == 0) {
                return Collections.singletonList("[SYSTEM] 日志文件为空");
            }

            long pos = length - 1;
            int lineCount = 0;
            // Seek backwards to find maxLines
            while (pos >= 0 && lineCount < maxLines) {
                raf.seek(pos);
                int c = raf.read();
                if (c == '\n') {
                    lineCount++;
                }
                pos--;
            }

            if (pos < 0) {
                pos = 0;
            } else {
                pos += 2; // skip newline
            }

            raf.seek(pos);
            String line;
            while ((line = raf.readLine()) != null) {
                // Correct ISO-8859-1 to UTF-8 conversion
                String utf8Line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                lines.add(utf8Line);
            }
        } catch (Exception e) {
            log.error("Failed to tail log file {}: {}", logPathStr, e.getMessage());
            lines.add("[SYSTEM ERROR] 读取日志失败: " + e.getMessage());
        }

        return lines;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void killPidFallback(int pid, boolean force) {
        if (isWindows()) {
            runCommand("taskkill " + (force ? "/F " : "") + "/PID " + pid + " /T");
        } else {
            runCommand("kill -" + (force ? "9 " : "15 ") + pid);
        }
    }

    private boolean checkPortListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 120);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isProcessAlive(int pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private Integer findPidByPort(int port) {
        try {
            ProcessBuilder pb;
            if (isWindows()) {
                pb = new ProcessBuilder("cmd.exe", "/c", "netstat -ano -p tcp | findstr :" + port);
            } else {
                pb = new ProcessBuilder("/bin/sh", "-c", "lsof -ti :" + port);
            }
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (isWindows()) {
                        if (line.contains("LISTENING") || line.contains(":" + port)) {
                            String[] parts = line.split("\\s+");
                            if (parts.length > 0) {
                                String pidStr = parts[parts.length - 1];
                                try {
                                    int pid = Integer.parseInt(pidStr);
                                    if (pid > 0) return pid;
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    } else {
                        try {
                            return Integer.parseInt(line);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean checkHealthUrl(String urlStr) {
        try {
            URI uri = URI.create(urlStr);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(400);
            conn.setReadTimeout(400);
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private void runCommand(String cmd) {
        try {
            ProcessBuilder pb;
            if (isWindows()) {
                pb = new ProcessBuilder("cmd.exe", "/c", cmd);
            } else {
                pb = new ProcessBuilder("/bin/sh", "-c", cmd);
            }
            pb.start().waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
