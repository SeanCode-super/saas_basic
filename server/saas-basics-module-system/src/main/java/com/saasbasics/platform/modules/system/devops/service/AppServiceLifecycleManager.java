package com.saasbasics.platform.modules.system.devops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.system.devops.driver.ServiceDriver;
import com.saasbasics.platform.modules.system.devops.driver.ServiceRuntimeInfo;
import com.saasbasics.platform.modules.system.devops.dto.AppArtifactDTO;
import com.saasbasics.platform.modules.system.devops.dto.AppBuildRecordDTO;
import com.saasbasics.platform.modules.system.devops.dto.AppServiceActionResult;
import com.saasbasics.platform.modules.system.devops.dto.AppServiceDTO;
import com.saasbasics.platform.modules.system.devops.dto.AppServiceSaveRequest;
import com.saasbasics.platform.modules.system.devops.dto.PortCheckResultDTO;
import com.saasbasics.platform.modules.system.devops.entity.AppArtifactEntity;
import com.saasbasics.platform.modules.system.devops.entity.AppBuildRecordEntity;
import com.saasbasics.platform.modules.system.devops.entity.AppServiceEntity;
import com.saasbasics.platform.modules.system.devops.mapper.AppArtifactMapper;
import com.saasbasics.platform.modules.system.devops.mapper.AppBuildRecordMapper;
import com.saasbasics.platform.modules.system.devops.mapper.AppServiceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AppServiceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(AppServiceLifecycleManager.class);
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AppServiceMapper serviceMapper;
    private final AppBuildRecordMapper buildRecordMapper;
    private final AppArtifactMapper artifactMapper;
    private final ObjectProvider<AuditRecorder> auditRecorderProvider;
    private final Map<String, ServiceDriver> drivers = new ConcurrentHashMap<>();

    public AppServiceLifecycleManager(AppServiceMapper serviceMapper,
                                     AppBuildRecordMapper buildRecordMapper,
                                     AppArtifactMapper artifactMapper,
                                     ObjectProvider<AuditRecorder> auditRecorderProvider,
                                     List<ServiceDriver> driverList) {
        this.serviceMapper = serviceMapper;
        this.buildRecordMapper = buildRecordMapper;
        this.artifactMapper = artifactMapper;
        this.auditRecorderProvider = auditRecorderProvider;
        for (ServiceDriver d : driverList) {
            drivers.put(d.getDriverType().toUpperCase(), d);
        }
    }

    private void recordAudit(String opType, String serviceCode, String details, boolean success) {
        try {
            AuditRecorder recorder = auditRecorderProvider.getIfAvailable();
            if (recorder != null) {
                recorder.record("SYSTEM_DEVOPS", "SERVICE_CONTROL", serviceCode, opType, details, success ? "SUCCESS" : "FAIL", success);
            }
        } catch (Exception e) {
            log.warn("Failed to record devops audit: {}", e.getMessage());
        }
    }

    public List<AppServiceDTO> listServices() {
        List<AppServiceEntity> entities = serviceMapper.selectList(
                new LambdaQueryWrapper<AppServiceEntity>().orderByAsc(AppServiceEntity::getSortNo)
        );

        return entities.parallelStream().map(entity -> {
            AppServiceDTO dto = convertToDto(entity);
            ServiceDriver driver = resolveDriver(entity);
            try {
                // 如果处于瞬态中间状态（如 STARTING、STOPPING、COMPILING），优先尊重当前过渡态，防止轮询提前冲掉中间态
                String currentDbStatus = entity.getStatus();
                if ("STARTING".equalsIgnoreCase(currentDbStatus) ||
                    "STOPPING".equalsIgnoreCase(currentDbStatus) ||
                    "COMPILING".equalsIgnoreCase(currentDbStatus)) {
                    dto.setStatus(currentDbStatus);
                    return dto;
                }

                ServiceRuntimeInfo info = driver.inspect(entity);
                dto.setStatus(info.getStatus());
                dto.setCurrentPid(info.getPid());
                dto.setUptimeSeconds(info.getUptimeSeconds());
                dto.setCpuUsagePercent(info.getCpuUsagePercent());
                dto.setMemoryRssBytes(info.getMemoryRssBytes());
                dto.setThreadCount(info.getThreadCount());
                dto.setPortListening(info.getPortListening());
                dto.setHealthOk(info.getHealthOk());

                // If DB status is out of sync, sync it silently
                if (!info.getStatus().equalsIgnoreCase(entity.getStatus())) {
                    entity.setStatus(info.getStatus());
                    entity.setCurrentPid(info.getPid());
                    serviceMapper.updateById(entity);
                }
            } catch (Exception e) {
                log.warn("Inspect failed for service {}: {}", entity.getServiceCode(), e.getMessage());
            }
            return dto;
        }).sorted(Comparator.comparingInt(s -> s.getSortNo() != null ? s.getSortNo() : 100))
          .collect(Collectors.toList());
    }

    public AppServiceDTO getService(Long id) {
        AppServiceEntity entity = serviceMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("未找到服务 ID: " + id);
        }
        AppServiceDTO dto = convertToDto(entity);
        ServiceDriver driver = resolveDriver(entity);
        ServiceRuntimeInfo info = driver.inspect(entity);
        dto.setStatus(info.getStatus());
        dto.setCurrentPid(info.getPid());
        dto.setUptimeSeconds(info.getUptimeSeconds());
        dto.setCpuUsagePercent(info.getCpuUsagePercent());
        dto.setMemoryRssBytes(info.getMemoryRssBytes());
        dto.setThreadCount(info.getThreadCount());
        dto.setPortListening(info.getPortListening());
        dto.setHealthOk(info.getHealthOk());
        return dto;
    }

    public AppServiceActionResult startService(Long id) {
        AppServiceEntity entity = serviceMapper.selectById(id);
        if (entity == null) {
            return AppServiceActionResult.fail("START", "未找到服务 ID: " + id, "ERROR");
        }

        ServiceDriver driver = resolveDriver(entity);
        try {
            // 立即落库 STARTING 状态，防止轮询误判
            entity.setStatus("STARTING");
            entity.setLastErrorMsg(null);
            serviceMapper.updateById(entity);

            int pid = driver.start(entity);
            entity.setStatus("RUNNING");
            entity.setCurrentPid(pid);
            entity.setLastStartedAt(LocalDateTime.now());
            entity.setLastErrorMsg(null);
            serviceMapper.updateById(entity);

            recordAudit("START", entity.getServiceCode(), "成功启动服务 (PID: " + pid + ")", true);
            String portMsg = entity.getPort() != null ? "，端口 :" + entity.getPort() + " 已正常就绪" : "";
            return AppServiceActionResult.ok("START", "服务已成功启动并就绪 (PID: " + pid + portMsg + ")", "RUNNING", pid);
        } catch (Exception e) {
            log.error("Start service failed: {}", e.getMessage(), e);
            entity.setStatus("ERROR");
            entity.setLastErrorMsg(e.getMessage());
            serviceMapper.updateById(entity);
            recordAudit("START", entity.getServiceCode(), "启动失败: " + e.getMessage(), false);
            return AppServiceActionResult.fail("START", "启动失败: " + e.getMessage(), "ERROR");
        }
    }

    public AppServiceActionResult stopService(Long id, int timeoutSeconds) {
        AppServiceEntity entity = serviceMapper.selectById(id);
        if (entity == null) {
            return AppServiceActionResult.fail("STOP", "未找到服务 ID: " + id, "ERROR");
        }

        // Safety Guardrail: backend-main suicide prevention
        if ("backend-main".equalsIgnoreCase(entity.getServiceCode())) {
            recordAudit("STOP", entity.getServiceCode(), "在线停机被平台安全机制拦截", false);
            return AppServiceActionResult.fail("STOP",
                    "【安全防护】核心主服务 (backend-main) 承载平台控制面，禁止在线直接停机以防控制台失联！如需更新部署，请使用【安全重启】或服务器外部守护脚本",
                    entity.getStatus());
        }

        ServiceDriver driver = resolveDriver(entity);
        try {
            // 立即落库 STOPPING 状态，防止轮询并发拉取旧状态
            entity.setStatus("STOPPING");
            serviceMapper.updateById(entity);

            driver.stop(entity, timeoutSeconds);
            entity.setStatus("STOPPED");
            entity.setCurrentPid(null);
            entity.setLastStoppedAt(LocalDateTime.now());
            serviceMapper.updateById(entity);

            recordAudit("STOP", entity.getServiceCode(), "服务安全停止", true);
            return AppServiceActionResult.ok("STOP", "服务已完全停止，端口与系统资源已回收", "STOPPED", null);
        } catch (Exception e) {
            log.error("Stop service failed: {}", e.getMessage(), e);
            entity.setStatus("ERROR");
            entity.setLastErrorMsg(e.getMessage());
            serviceMapper.updateById(entity);
            recordAudit("STOP", entity.getServiceCode(), "停止异常: " + e.getMessage(), false);
            return AppServiceActionResult.fail("STOP", "停止服务异常: " + e.getMessage(), entity.getStatus());
        }
    }

    public AppServiceActionResult restartService(Long id, int timeoutSeconds) {
        AppServiceEntity entity = serviceMapper.selectById(id);
        if (entity == null) {
            return AppServiceActionResult.fail("RESTART", "未找到服务 ID: " + id, "ERROR");
        }

        // Special handling for core backend-main self-restart
        if ("backend-main".equalsIgnoreCase(entity.getServiceCode())) {
            log.warn("Self-restart triggered for core service [backend-main]. Scheduling graceful restart...");
            entity.setStatus("RESTARTING");
            entity.setLastErrorMsg(null);
            serviceMapper.updateById(entity);
            recordAudit("RESTART", entity.getServiceCode(), "触发核心主服务安全自愈重启", true);

            // Execute async restart so the current HTTP response completes and reaches the browser
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1200); // 1.2s delay to allow HTTP response flush
                    log.info("Executing self-restart for backend-main...");
                    ServiceDriver driver = resolveDriver(entity);
                    driver.restart(entity, timeoutSeconds);
                } catch (Exception e) {
                    log.error("Async self-restart failed: {}", e.getMessage(), e);
                }
            });

            return AppServiceActionResult.ok("RESTART", "核心主服务安全重启指令已下发，系统将在数秒内平滑重载，请稍候刷新控制台...", "RESTARTING", entity.getCurrentPid());
        }

        ServiceDriver driver = resolveDriver(entity);
        try {
            driver.restart(entity, timeoutSeconds);
            ServiceRuntimeInfo info = driver.inspect(entity);

            entity.setStatus("RUNNING");
            entity.setCurrentPid(info.getPid());
            entity.setLastStartedAt(LocalDateTime.now());
            entity.setLastErrorMsg(null);
            serviceMapper.updateById(entity);

            recordAudit("RESTART", entity.getServiceCode(), "服务成功重启 (PID: " + info.getPid() + ")", true);
            return AppServiceActionResult.ok("RESTART", "服务已成功重启", "RUNNING", info.getPid());
        } catch (Exception e) {
            log.error("Restart service failed: {}", e.getMessage(), e);
            entity.setStatus("ERROR");
            entity.setLastErrorMsg(e.getMessage());
            serviceMapper.updateById(entity);
            recordAudit("RESTART", entity.getServiceCode(), "重启失败: " + e.getMessage(), false);
            return AppServiceActionResult.fail("RESTART", "重启失败: " + e.getMessage(), "ERROR");
        }
    }

    public AppServiceActionResult killService(Long id) {
        AppServiceEntity entity = serviceMapper.selectById(id);
        if (entity == null) {
            return AppServiceActionResult.fail("KILL", "未找到服务 ID: " + id, "ERROR");
        }

        // Safety Guardrail: backend-main suicide prevention
        if ("backend-main".equalsIgnoreCase(entity.getServiceCode())) {
            recordAudit("KILL", entity.getServiceCode(), "在线强杀被平台安全机制拦截", false);
            return AppServiceActionResult.fail("KILL",
                    "【安全防护】核心主服务 (backend-main) 承载平台控制面，禁止在线强杀！如需强制关停请登录服务器操作系统执行操作",
                    entity.getStatus());
        }

        ServiceDriver driver = resolveDriver(entity);
        try {
            driver.kill(entity);
            entity.setStatus("STOPPED");
            entity.setCurrentPid(null);
            entity.setLastStoppedAt(LocalDateTime.now());
            serviceMapper.updateById(entity);

            recordAudit("KILL", entity.getServiceCode(), "进程已被强制终止 (SIGKILL)", true);
            return AppServiceActionResult.ok("KILL", "服务进程已强制终止 (SIGKILL)", "STOPPED", null);
        } catch (Exception e) {
            recordAudit("KILL", entity.getServiceCode(), "强杀失败: " + e.getMessage(), false);
            return AppServiceActionResult.fail("KILL", "强杀失败: " + e.getMessage(), entity.getStatus());
        }
    }

    public Long saveService(AppServiceSaveRequest req, Long id) {
        validateCommands(req.getStartCmd(), req.getBuildCmd());

        AppServiceEntity entity;
        if (id != null && id > 0) {
            entity = serviceMapper.selectById(id);
            if (entity == null) {
                throw new IllegalArgumentException("未找到服务 ID: " + id);
            }
        } else {
            entity = new AppServiceEntity();
            entity.setStatus("STOPPED");
            entity.setCreatedAt(LocalDateTime.now());
        }

        BeanUtils.copyProperties(req, entity);
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.getId() != null) {
            serviceMapper.updateById(entity);
        } else {
            serviceMapper.insert(entity);
        }
        return entity.getId();
    }

    public PortCheckResultDTO checkPortStatus(int port, Long excludeServiceId) {
        if (port <= 0 || port > 65535) {
            PortCheckResultDTO dto = new PortCheckResultDTO();
            dto.setPort(port);
            dto.setAvailable(false);
            dto.setMessage("端口必须在 1 ~ 65535 范围内");
            return dto;
        }

        // 1. 检查数据库中是否已分配给其他已注册服务
        LambdaQueryWrapper<AppServiceEntity> qw = new LambdaQueryWrapper<AppServiceEntity>()
                .eq(AppServiceEntity::getPort, port);
        if (excludeServiceId != null && excludeServiceId > 0) {
            qw.ne(AppServiceEntity::getId, excludeServiceId);
        }
        AppServiceEntity inDb = serviceMapper.selectOne(qw);
        if (inDb != null) {
            return PortCheckResultDTO.occupiedByService(port, inDb.getServiceName(), inDb.getServiceCode());
        }

        // 2. 探测操作系统物理端口当前是否已被外部进程监听
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 120);
            return PortCheckResultDTO.occupiedBySystem(port);
        } catch (Exception ignored) {
        }

        return PortCheckResultDTO.available(port);
    }

    private void validateCommands(String startCmd, String buildCmd) {
        String combined = ((startCmd != null ? startCmd : "") + " " + (buildCmd != null ? buildCmd : "")).toLowerCase();
        String[] dangerous = {
            "rm -rf /", "rm -rf *", "mkfs", ":(){ :|:& };:", "dd if=", "> /dev/sda",
            "chmod -r 777 /", "shutdown -h", "init 0"
        };
        for (String d : dangerous) {
            if (combined.contains(d)) {
                throw new IllegalArgumentException("【安全防御】检测到包含高危破坏性指令: " + d);
            }
        }
    }

    public void deleteService(Long id) {
        AppServiceEntity entity = serviceMapper.selectById(id);
        if (entity == null) {
            return;
        }
        try {
            stopService(id, 5);
        } catch (Exception ignored) {
        }
        serviceMapper.deleteById(id);
    }

    public List<AppBuildRecordDTO> listBuildRecords(Long serviceId) {
        List<AppBuildRecordEntity> records = buildRecordMapper.selectList(
                new LambdaQueryWrapper<AppBuildRecordEntity>()
                        .eq(AppBuildRecordEntity::getServiceId, serviceId)
                        .orderByDesc(AppBuildRecordEntity::getCreatedAt)
        );
        return records.stream().map(r -> {
            AppBuildRecordDTO dto = new AppBuildRecordDTO();
            BeanUtils.copyProperties(r, dto);
            return dto;
        }).collect(Collectors.toList());
    }

    public List<AppArtifactDTO> listArtifacts(Long serviceId) {
        List<AppArtifactEntity> artifacts = artifactMapper.selectList(
                new LambdaQueryWrapper<AppArtifactEntity>()
                        .eq(AppArtifactEntity::getServiceId, serviceId)
                        .orderByDesc(AppArtifactEntity::getCreatedAt)
        );
        return artifacts.stream().map(a -> {
            AppArtifactDTO dto = new AppArtifactDTO();
            BeanUtils.copyProperties(a, dto);
            return dto;
        }).collect(Collectors.toList());
    }

    public AppArtifactDTO uploadArtifact(Long serviceId, MultipartFile file, String remark) throws Exception {
        AppServiceEntity service = serviceMapper.selectById(serviceId);
        if (service == null) {
            throw new IllegalArgumentException("未找到服务 ID: " + serviceId);
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "artifact.jar";
        String backupDirStr = System.getProperty("user.home") + "/.tianshu/artifacts/" + service.getServiceCode();
        File backupDir = new File(backupDirStr);
        backupDir.mkdirs();

        String versionTag = "upload-" + LocalDateTime.now().format(NO_FMT);
        Path targetPath = Paths.get(backupDirStr, service.getServiceCode() + "-" + versionTag + "-" + originalName);

        file.transferTo(targetPath.toFile());

        String md5 = calculateMd5(targetPath.toFile());
        long size = targetPath.toFile().length();

        // Copy to current deploy target if artifact_path configured
        if (service.getArtifactPath() != null && !service.getArtifactPath().isBlank()) {
            File currentDeployFile = new File(service.getWorkDir(), service.getArtifactPath());
            if (!currentDeployFile.getParentFile().exists()) {
                currentDeployFile.getParentFile().mkdirs();
            }
            Files.copy(targetPath, currentDeployFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("Overwritten active deployment artifact for [{}] with newly uploaded file.", service.getServiceCode());
        }

        // Set previous active to 0
        artifactMapper.selectList(new LambdaQueryWrapper<AppArtifactEntity>().eq(AppArtifactEntity::getServiceId, serviceId))
                .forEach(a -> {
                    if (a.getIsCurrent() == 1) {
                        a.setIsCurrent(0);
                        artifactMapper.updateById(a);
                    }
                });

        AppArtifactEntity artifact = new AppArtifactEntity();
        artifact.setServiceId(serviceId);
        artifact.setServiceCode(service.getServiceCode());
        artifact.setVersionTag(versionTag);
        artifact.setFileName(originalName);
        artifact.setFilePath(targetPath.toString());
        artifact.setFileSizeBytes(size);
        artifact.setFileMd5(md5);
        artifact.setIsCurrent(1);
        artifact.setRemark(remark != null && !remark.isBlank() ? remark : "手动上传制品部署");
        artifact.setCreatedAt(LocalDateTime.now());
        artifactMapper.insert(artifact);

        AppArtifactDTO dto = new AppArtifactDTO();
        BeanUtils.copyProperties(artifact, dto);
        return dto;
    }

    public AppServiceActionResult rollbackArtifact(Long serviceId, Long artifactId) throws Exception {
        AppServiceEntity service = serviceMapper.selectById(serviceId);
        AppArtifactEntity targetArtifact = artifactMapper.selectById(artifactId);

        if (service == null || targetArtifact == null) {
            return AppServiceActionResult.fail("ROLLBACK", "未找到服务或对应版本制品", "ERROR");
        }

        File backupFile = new File(targetArtifact.getFilePath());
        if (!backupFile.exists()) {
            return AppServiceActionResult.fail("ROLLBACK", "备份制品物理文件不存在: " + targetArtifact.getFilePath(), "ERROR");
        }

        // Overwrite active artifact
        if (service.getArtifactPath() != null && !service.getArtifactPath().isBlank()) {
            File currentDeployFile = new File(service.getWorkDir(), service.getArtifactPath());
            if (!currentDeployFile.getParentFile().exists()) {
                currentDeployFile.getParentFile().mkdirs();
            }
            Files.copy(backupFile.toPath(), currentDeployFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // Mark as current
        artifactMapper.selectList(new LambdaQueryWrapper<AppArtifactEntity>().eq(AppArtifactEntity::getServiceId, serviceId))
                .forEach(a -> {
                    a.setIsCurrent(a.getId().equals(artifactId) ? 1 : 0);
                    artifactMapper.updateById(a);
                });

        // Restart service
        restartService(serviceId, 15);

        recordAudit("ROLLBACK", service.getServiceCode(), "制品回滚至版本: " + targetArtifact.getVersionTag(), true);
        return AppServiceActionResult.ok("ROLLBACK", "已回滚至版本: " + targetArtifact.getVersionTag() + " 并成功平滑重启", "RUNNING", service.getCurrentPid());
    }

    private ServiceDriver resolveDriver(AppServiceEntity service) {
        String type = service.getDriverType() != null ? service.getDriverType().toUpperCase() : "HOST_PROCESS";
        ServiceDriver driver = drivers.get(type);
        if (driver == null) {
            driver = drivers.get("HOST_PROCESS");
        }
        return driver;
    }

    private AppServiceDTO convertToDto(AppServiceEntity entity) {
        AppServiceDTO dto = new AppServiceDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private String calculateMd5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
