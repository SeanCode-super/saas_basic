package com.saasbasics.platform.modules.system.devops.service;

import com.saasbasics.platform.modules.system.devops.driver.HostProcessDriver;
import com.saasbasics.platform.modules.system.devops.driver.ServiceDriver;
import com.saasbasics.platform.modules.system.devops.entity.AppServiceEntity;
import com.saasbasics.platform.modules.system.devops.mapper.AppServiceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class AppLogStreamService {

    private static final Logger log = LoggerFactory.getLogger(AppLogStreamService.class);

    private final AppServiceMapper serviceMapper;
    private final Map<String, ServiceDriver> drivers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sseScheduler = Executors.newScheduledThreadPool(4);

    public AppLogStreamService(AppServiceMapper serviceMapper,
                              List<ServiceDriver> driverList) {
        this.serviceMapper = serviceMapper;
        for (ServiceDriver d : driverList) {
            drivers.put(d.getDriverType().toUpperCase(), d);
        }
    }

    public List<String> getRecentLogs(Long serviceId, int maxLines) {
        AppServiceEntity service = serviceMapper.selectById(serviceId);
        if (service == null) {
            throw new IllegalArgumentException("未找到服务 ID: " + serviceId);
        }
        ServiceDriver driver = resolveDriver(service);
        return driver.tailLogs(service, maxLines);
    }

    public SseEmitter createLogEmitter(Long serviceId, boolean includeHistory) {
        AppServiceEntity service = serviceMapper.selectById(serviceId);
        if (service == null) {
            throw new IllegalArgumentException("未找到服务 ID: " + serviceId);
        }

        // 30 minutes timeout
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        String logPathStr = service.getLogPath();

        if (logPathStr == null || logPathStr.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("[SYSTEM] 该服务未配置日志输出路径"));
                emitter.complete();
            } catch (Exception ignored) {
            }
            return emitter;
        }

        File logFile = new File(logPathStr);

        try {
            emitter.send(SseEmitter.event().name("connected").data("CONNECTED"));
            if (includeHistory) {
                ServiceDriver driver = resolveDriver(service);
                List<String> recent = driver.tailLogs(service, 100);
                for (String r : recent) {
                    emitter.send(SseEmitter.event().name("log").data(r));
                }
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // Periodic tail reader
        final long[] filePointer = new long[]{logFile.exists() ? logFile.length() : 0};
        final long[] lastHeartbeat = new long[]{System.currentTimeMillis()};

        var future = sseScheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!logFile.exists() || !logFile.isFile()) {
                    return;
                }
                long currentLen = logFile.length();
                if (currentLen < filePointer[0]) {
                    // File was truncated or rotated
                    filePointer[0] = 0;
                }
                if (currentLen > filePointer[0]) {
                    try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                        raf.seek(filePointer[0]);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            String utf8 = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                            emitter.send(SseEmitter.event().name("log").data(utf8));
                        }
                        filePointer[0] = raf.getFilePointer();
                    }
                } else {
                    if (System.currentTimeMillis() - lastHeartbeat[0] > 15000) {
                        emitter.send(SseEmitter.event().name("ping").data("heartbeat"));
                        lastHeartbeat[0] = System.currentTimeMillis();
                    }
                }
            } catch (Exception ex) {
                // Client probably disconnected
                emitter.complete();
            }
        }, 500, 300, TimeUnit.MILLISECONDS);

        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        emitter.onError(e -> future.cancel(true));

        return emitter;
    }

    private ServiceDriver resolveDriver(AppServiceEntity service) {
        String type = service.getDriverType() != null ? service.getDriverType().toUpperCase() : "HOST_PROCESS";
        ServiceDriver driver = drivers.get(type);
        if (driver == null) {
            driver = drivers.get("HOST_PROCESS");
        }
        return driver;
    }
}
