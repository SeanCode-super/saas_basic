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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class HostProcessDriver implements ServiceDriver {

    private static final Logger log = LoggerFactory.getLogger(HostProcessDriver.class);
    private final OperatingSystem os = new SystemInfo().getOperatingSystem();

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

        return info;
    }

    @Override
    public int start(AppServiceEntity service) throws Exception {
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
            // Use bash or sh
            pb = new ProcessBuilder("/bin/bash", "-c", startCmd);
        }

        pb.directory(workDir);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

        // Inject current environment PATH and variables
        pb.environment().put("PATH", "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:" + System.getenv("PATH"));
        pb.environment().put("DB_PASSWORD", "19990518suo");

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

        log.info("Service [{}] started with PID: {}", service.getServiceCode(), pid);
        return pid;
    }

    @Override
    public void stop(AppServiceEntity service, int timeoutSeconds) throws Exception {
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
                runCommand("kill -15 " + pid);
                Thread.sleep(1000);
                if (isProcessAlive(pid)) {
                    runCommand("kill -9 " + pid);
                }
            }
        }

        // Double check port
        if (service.getPort() != null && checkPortListening(service.getPort())) {
            Integer stubbornPid = findPidByPort(service.getPort());
            if (stubbornPid != null && stubbornPid > 0) {
                log.warn("Port {} still listening by PID {}, killing forcibly.", service.getPort(), stubbornPid);
                runCommand("kill -9 " + stubbornPid);
            }
        }
    }

    @Override
    public void restart(AppServiceEntity service, int timeoutSeconds) throws Exception {
        stop(service, timeoutSeconds);
        Thread.sleep(1500);
        start(service);
    }

    @Override
    public void kill(AppServiceEntity service) throws Exception {
        ServiceRuntimeInfo info = inspect(service);
        Integer pid = info.getPid();
        if (pid != null && pid > 0) {
            ProcessHandle.of(pid).ifPresent(h -> {
                h.descendants().forEach(ProcessHandle::destroyForcibly);
                h.destroyForcibly();
            });
            runCommand("kill -9 " + pid);
        }
        if (service.getPort() != null) {
            Integer pPid = findPidByPort(service.getPort());
            if (pPid != null && pPid > 0) {
                runCommand("kill -9 " + pPid);
            }
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

    private boolean checkPortListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
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
            Process p = new ProcessBuilder("/bin/sh", "-c", "lsof -ti :" + port).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return Integer.parseInt(line.trim());
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
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private void runCommand(String cmd) {
        try {
            new ProcessBuilder("/bin/sh", "-c", cmd).start().waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
