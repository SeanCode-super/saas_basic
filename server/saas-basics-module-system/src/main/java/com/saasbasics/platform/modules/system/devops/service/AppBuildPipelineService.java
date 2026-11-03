package com.saasbasics.platform.modules.system.devops.service;

import com.saasbasics.platform.modules.system.devops.entity.AppArtifactEntity;
import com.saasbasics.platform.modules.system.devops.entity.AppBuildRecordEntity;
import com.saasbasics.platform.modules.system.devops.entity.AppServiceEntity;
import com.saasbasics.platform.modules.system.devops.mapper.AppArtifactMapper;
import com.saasbasics.platform.modules.system.devops.mapper.AppBuildRecordMapper;
import com.saasbasics.platform.modules.system.devops.mapper.AppServiceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.system.devops.dto.AppBuildRecordDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class AppBuildPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AppBuildPipelineService.class);
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AppServiceMapper serviceMapper;
    private final AppBuildRecordMapper buildRecordMapper;
    private final AppArtifactMapper artifactMapper;
    private final ObjectProvider<AuditRecorder> auditRecorderProvider;
    private final ExecutorService buildThreadPool = Executors.newFixedThreadPool(3);

    // Active live build log buffer: recordId -> List<String>
    private final Map<Long, List<String>> liveBuildLogs = new ConcurrentHashMap<>();

    public AppBuildPipelineService(AppServiceMapper serviceMapper,
                                  AppBuildRecordMapper buildRecordMapper,
                                  AppArtifactMapper artifactMapper,
                                  ObjectProvider<AuditRecorder> auditRecorderProvider) {
        this.serviceMapper = serviceMapper;
        this.buildRecordMapper = buildRecordMapper;
        this.artifactMapper = artifactMapper;
        this.auditRecorderProvider = auditRecorderProvider;
    }

    public AppBuildRecordDTO triggerBuild(Long serviceId, String customBuildCmd, String triggerType, String createdBy) {
        AppServiceEntity service = serviceMapper.selectById(serviceId);
        if (service == null) {
            throw new IllegalArgumentException("未找到服务 ID: " + serviceId);
        }

        String actualCmd = (customBuildCmd != null && !customBuildCmd.isBlank()) ? customBuildCmd.trim() : service.getBuildCmd();
        if (actualCmd == null || actualCmd.isBlank()) {
            throw new IllegalArgumentException("该服务未配置编译构建命令 (build_cmd)");
        }

        String buildNo = "B" + LocalDateTime.now().format(NO_FMT);
        AppBuildRecordEntity record = new AppBuildRecordEntity();
        record.setServiceId(serviceId);
        record.setServiceCode(service.getServiceCode());
        record.setBuildNo(buildNo);
        record.setBuildCmd(actualCmd);
        record.setStatus("RUNNING");
        record.setTriggerType(triggerType != null ? triggerType : "MANUAL");
        record.setStartTime(LocalDateTime.now());
        record.setCreatedBy(createdBy != null ? createdBy : "admin");
        record.setCreatedAt(LocalDateTime.now());
        buildRecordMapper.insert(record);

        // Update service status
        service.setStatus("COMPILING");
        serviceMapper.updateById(service);

        // Initialize log buffer
        List<String> logLines = new ArrayList<>();
        logLines.add(String.format("[TIANSHU CI] >>> 开始执行构建流水线 [%s] <<<", buildNo));
        logLines.add(String.format("[TIANSHU CI] 服务标识: %s (%s)", service.getServiceCode(), service.getServiceName()));
        logLines.add(String.format("[TIANSHU CI] 工作目录: %s", service.getWorkDir()));
        logLines.add(String.format("[TIANSHU CI] 编译命令: %s", actualCmd));
        logLines.add("--------------------------------------------------------------------------------");
        liveBuildLogs.put(record.getId(), logLines);

        // Execute async
        CompletableFuture.runAsync(() -> executePipeline(service, record, actualCmd), buildThreadPool);

        try {
            AuditRecorder recorder = auditRecorderProvider.getIfAvailable();
            if (recorder != null) {
                recorder.record("SYSTEM_DEVOPS", "SERVICE_BUILD", service.getServiceCode(), "BUILD", "触发编译流水线: " + buildNo + ", 命令: " + actualCmd, "SUBMITTED", true);
            }
        } catch (Exception ignored) {}

        AppBuildRecordDTO dto = new AppBuildRecordDTO();
        BeanUtils.copyProperties(record, dto);
        return dto;
    }

    private void executePipeline(AppServiceEntity service, AppBuildRecordEntity record, String buildCmd) {
        long startMs = System.currentTimeMillis();
        List<String> logBuffer = liveBuildLogs.getOrDefault(record.getId(), new ArrayList<>());

        try {
            File workDir = new File(service.getWorkDir());
            if (!workDir.exists()) {
                throw new IllegalStateException("工作目录不存在: " + service.getWorkDir());
            }

            ProcessBuilder pb;
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (osName.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", buildCmd);
            } else {
                pb = new ProcessBuilder("/bin/bash", "-c", buildCmd);
            }
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            pb.environment().put("PATH", "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:" + System.getenv("PATH"));
            pb.environment().put("DB_PASSWORD", "19990518suo");

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (logBuffer) {
                        logBuffer.add(line);
                        // Limit buffer size to 5000 lines
                        if (logBuffer.size() > 5000) {
                            logBuffer.remove(0);
                        }
                    }
                }
            }

            boolean completed = process.waitFor(15, TimeUnit.MINUTES);
            int exitCode = completed ? process.exitValue() : -1;
            long duration = System.currentTimeMillis() - startMs;

            if (completed && exitCode == 0) {
                logBuffer.add("--------------------------------------------------------------------------------");
                logBuffer.add(String.format("[TIANSHU CI] >>> 构建成功! 耗时: %.2f 秒 <<<", duration / 1000.0));

                record.setStatus("SUCCESS");
                record.setEndTime(LocalDateTime.now());
                record.setDurationMs(duration);

                // Auto archive artifact if generated
                archiveArtifact(service, record, logBuffer);

            } else {
                logBuffer.add("--------------------------------------------------------------------------------");
                logBuffer.add(String.format("[TIANSHU CI] >>> 构建失败! 退出码: %d, 耗时: %.2f 秒 <<<", exitCode, duration / 1000.0));
                record.setStatus("FAILED");
                record.setEndTime(LocalDateTime.now());
                record.setDurationMs(duration);
                record.setErrorMessage("构建命令执行返回非零退出码: " + exitCode);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startMs;
            log.error("Build pipeline failed: {}", e.getMessage(), e);
            logBuffer.add("[TIANSHU CI ERROR] 异常终止: " + e.getMessage());
            record.setStatus("FAILED");
            record.setEndTime(LocalDateTime.now());
            record.setDurationMs(duration);
            record.setErrorMessage(e.getMessage());
        } finally {
            // Save log excerpt to DB (last 200 lines)
            StringBuilder excerpt = new StringBuilder();
            synchronized (logBuffer) {
                int start = Math.max(0, logBuffer.size() - 200);
                for (int i = start; i < logBuffer.size(); i++) {
                    excerpt.append(logBuffer.get(i)).append("\n");
                }
            }
            record.setLogExcerpt(excerpt.toString());
            buildRecordMapper.updateById(record);

            // Restore service status
            AppServiceEntity freshService = serviceMapper.selectById(service.getId());
            if (freshService != null && "COMPILING".equalsIgnoreCase(freshService.getStatus())) {
                freshService.setStatus("SUCCESS".equalsIgnoreCase(record.getStatus()) ? "STOPPED" : "ERROR");
                serviceMapper.updateById(freshService);
            }
        }
    }

    private void archiveArtifact(AppServiceEntity service, AppBuildRecordEntity record, List<String> logBuffer) {
        try {
            String artifactPathStr = service.getArtifactPath();
            if (artifactPathStr == null || artifactPathStr.isBlank()) {
                return;
            }

            File artifactFile = new File(service.getWorkDir(), artifactPathStr);
            if (!artifactFile.exists()) {
                // Try absolute path
                artifactFile = new File(artifactPathStr);
            }

            if (artifactFile.exists() && artifactFile.isFile()) {
                long size = artifactFile.length();
                String md5 = calculateMd5(artifactFile);
                record.setArtifactGenerated(artifactFile.getAbsolutePath());
                record.setArtifactSizeBytes(size);

                // Create backup copy in ~/.tianshu/artifacts/{serviceCode}/
                String backupDirStr = System.getProperty("user.home") + "/.tianshu/artifacts/" + service.getServiceCode();
                File backupDir = new File(backupDirStr);
                backupDir.mkdirs();

                String versionTag = "v" + LocalDateTime.now().format(NO_FMT);
                String backupFileName = service.getServiceCode() + "-" + versionTag + "-" + artifactFile.getName();
                Path backupPath = Paths.get(backupDirStr, backupFileName);

                Files.copy(artifactFile.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);

                AppArtifactEntity artifact = new AppArtifactEntity();
                artifact.setServiceId(service.getId());
                artifact.setServiceCode(service.getServiceCode());
                artifact.setVersionTag(versionTag);
                artifact.setFileName(artifactFile.getName());
                artifact.setFilePath(backupPath.toString());
                artifact.setFileSizeBytes(size);
                artifact.setFileMd5(md5);
                artifact.setIsCurrent(1);
                artifact.setRemark("自动化编译产物归档 (构建序号: " + record.getBuildNo() + ")");
                artifact.setCreatedAt(LocalDateTime.now());
                artifactMapper.insert(artifact);

                logBuffer.add(String.format("[TIANSHU CI] 制品已自动归档: %s (%s, %.2f MB)",
                        versionTag, backupPath.getFileName(), size / (1024.0 * 1024.0)));
            }
        } catch (Exception e) {
            log.warn("Failed to archive artifact: {}", e.getMessage());
            logBuffer.add("[TIANSHU CI WARN] 制品归档跳过: " + e.getMessage());
        }
    }

    public List<String> getLiveBuildLogs(Long recordId) {
        List<String> list = liveBuildLogs.get(recordId);
        if (list != null) {
            synchronized (list) {
                return new ArrayList<>(list);
            }
        }
        // Fallback to DB logExcerpt
        AppBuildRecordEntity record = buildRecordMapper.selectById(recordId);
        if (record != null && record.getLogExcerpt() != null) {
            return List.of(record.getLogExcerpt().split("\n"));
        }
        return List.of("[TIANSHU CI] 暂无构建日志");
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
