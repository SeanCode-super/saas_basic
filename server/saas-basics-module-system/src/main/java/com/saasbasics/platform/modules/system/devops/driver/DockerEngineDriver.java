package com.saasbasics.platform.modules.system.devops.driver;

import com.saasbasics.platform.modules.system.devops.entity.AppServiceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DockerEngineDriver implements ServiceDriver {

    private static final Logger log = LoggerFactory.getLogger(DockerEngineDriver.class);

    @Override
    public String getDriverType() {
        return "DOCKER";
    }

    @Override
    public boolean isAvailable() {
        try {
            Process p = new ProcessBuilder("docker", "version").start();
            boolean finished = p.waitFor(2, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ServiceRuntimeInfo inspect(AppServiceEntity service) {
        ServiceRuntimeInfo info = new ServiceRuntimeInfo();
        if (!isAvailable()) {
            info.setStatus("STOPPED");
            info.setErrorMsg("Docker 运行环境不可用 (未检测到 Docker Daemon 或 docker CLI)");
            return info;
        }

        String containerName = getContainerIdentifier(service);
        try {
            // docker inspect -f '{{.State.Status}}|{{.State.Pid}}|{{.State.Health.Status}}' <container>
            Process p = new ProcessBuilder("docker", "inspect", "-f",
                    "{{.State.Status}}|{{.State.Pid}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}",
                    containerName).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    String[] parts = line.split("\\|");
                    String dockerStatus = parts[0];
                    int pid = parts.length > 1 && !parts[1].isBlank() ? Integer.parseInt(parts[1]) : 0;
                    String health = parts.length > 2 ? parts[2] : "none";

                    if ("running".equalsIgnoreCase(dockerStatus)) {
                        info.setStatus("RUNNING");
                        info.setPid(pid > 0 ? pid : null);
                        info.setHealthOk("healthy".equalsIgnoreCase(health) || "none".equalsIgnoreCase(health));
                    } else {
                        info.setStatus("STOPPED");
                        info.setHealthOk(false);
                    }
                } else {
                    info.setStatus("STOPPED");
                }
            }

            if (service.getPort() != null && service.getPort() > 0) {
                info.setPortListening(checkPortListening(service.getPort()));
            }

        } catch (Exception e) {
            info.setStatus("STOPPED");
            info.setErrorMsg("容器检查异常: " + e.getMessage());
        }

        return info;
    }

    @Override
    public int start(AppServiceEntity service) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Docker 运行环境不可用，请先启动 Docker 服务");
        }
        String container = getContainerIdentifier(service);
        runDockerCmd("docker start " + container, 15);
        ServiceRuntimeInfo info = inspect(service);
        return info.getPid() != null ? info.getPid() : 0;
    }

    @Override
    public void stop(AppServiceEntity service, int timeoutSeconds) throws Exception {
        if (!isAvailable()) {
            return;
        }
        String container = getContainerIdentifier(service);
        runDockerCmd("docker stop -t " + Math.max(5, timeoutSeconds) + " " + container, timeoutSeconds + 5);
    }

    @Override
    public void restart(AppServiceEntity service, int timeoutSeconds) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Docker 运行环境不可用");
        }
        String container = getContainerIdentifier(service);
        runDockerCmd("docker restart -t " + Math.max(5, timeoutSeconds) + " " + container, timeoutSeconds + 10);
    }

    @Override
    public void kill(AppServiceEntity service) throws Exception {
        if (!isAvailable()) {
            return;
        }
        String container = getContainerIdentifier(service);
        runDockerCmd("docker kill " + container, 5);
    }

    @Override
    public List<String> tailLogs(AppServiceEntity service, int maxLines) {
        if (!isAvailable()) {
            return Collections.singletonList("[SYSTEM] Docker 运行环境不可用");
        }
        String container = getContainerIdentifier(service);
        List<String> lines = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("docker", "logs", "--tail", String.valueOf(maxLines), container).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
        } catch (Exception e) {
            lines.add("[SYSTEM ERROR] 获取容器日志失败: " + e.getMessage());
        }
        return lines;
    }

    private String getContainerIdentifier(AppServiceEntity service) {
        if (service.getArtifactPath() != null && !service.getArtifactPath().isBlank()) {
            return service.getArtifactPath().trim();
        }
        return service.getServiceCode();
    }

    private void runDockerCmd(String cmd, int timeoutSeconds) throws Exception {
        Process p = new ProcessBuilder("/bin/sh", "-c", cmd).start();
        boolean ok = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!ok || p.exitValue() != 0) {
            throw new RuntimeException("Docker 命令执行失败: " + cmd);
        }
    }

    private boolean checkPortListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
