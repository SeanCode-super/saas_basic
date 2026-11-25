package com.saasbasics.platform.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.file.TenantFileRestoreCapacity;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantQuotaWriteGuard;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
import com.saasbasics.platform.modules.file.entity.FileUploadSessionEntity;
import com.saasbasics.platform.modules.file.mapper.FileObjectMapper;
import com.saasbasics.platform.modules.file.mapper.FileUploadSessionMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileUploadAccounting implements TenantFileRestoreCapacity {
    private static final Logger log = LoggerFactory.getLogger(FileUploadAccounting.class);
    private final TenantQuotaWriteGuard quotas;
    private final FileUploadSessionMapper sessions;
    private final FileObjectMapper objects;

    public FileUploadAccounting(TenantQuotaWriteGuard quotas, FileUploadSessionMapper sessions, FileObjectMapper objects) {
        this.quotas = quotas;
        this.sessions = sessions;
        this.objects = objects;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public FileUploadSessionEntity reserve(FileUploadSessionEntity proposed, String key, String hash, boolean direct) {
        Long tenantId = proposed.getTenantId();
        long maxMb = quotas.lockLimit(tenantId, "STORAGE_MB");
        if (proposed.getFileSize() == null || proposed.getFileSize() <= 0) {
            throw new BizException("FILE_INVALID_SIZE", "申请的文件大小必须大于零");
        }
        FileUploadSessionEntity existing = null;
        if (key != null) {
            if (!key.matches("[A-Za-z0-9._:-]{1,128}")) {
                throw new BizException("FILE_INVALID_REQUEST_KEY", "上传请求标识格式无效");
            }
            proposed.setRequestKey((direct ? "direct:" : "session:") + key);
            proposed.setRequestHash(hash);
            existing = sessions.selectOne(new LambdaQueryWrapper<FileUploadSessionEntity>()
                    .eq(FileUploadSessionEntity::getTenantId, tenantId)
                    .eq(FileUploadSessionEntity::getRequestKey, proposed.getRequestKey()));
        }
        if (existing != null) {
            if (!Objects.equals(existing.getOwnerUserId(), proposed.getOwnerUserId())
                    || !Objects.equals(existing.getRequestHash(), hash)) {
                throw new BizException("FILE_REQUEST_CONFLICT", "同一请求标识已用于其他上传");
            }
            if ("COMPLETED".equals(existing.getStatus()) || (!direct && active(existing))) return existing;
            if (!"FAILED".equals(existing.getStatus())) {
                throw new BizException("FILE_UPLOAD_CONFLICT", "该请求已在处理或等待清理");
            }
        }
        checkCapacity(tenantId, maxMb, proposed.getFileSize());
        requireUnclaimed(tenantId, proposed.getStorageId(), proposed.getObjectKey());
        proposed.setQuotaManaged(1);
        proposed.setStatus(direct ? "TRANSFERRING" : "INIT");
        proposed.setExpireAt(LocalDateTime.now().plusHours(2));
        if (existing == null) {
            proposed.setSessionCode("UP-" + UUID.randomUUID());
            sessions.insert(proposed);
        } else {
            proposed.setId(existing.getId());
            proposed.setVersion(existing.getVersion());
            proposed.setSessionCode(existing.getSessionCode());
            sessions.updateById(proposed);
        }
        return proposed;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCapacity(Long tenantId, Long fileId) {
        long maxMb = quotas.lockLimit(tenantId, "STORAGE_MB");
        var candidate = sessions.restoreCandidate(tenantId, fileId);
        if (candidate == null) throw new BizException("FILE_OBJECT_NOT_FOUND", "待恢复文件不存在");
        requireUnclaimed(tenantId, candidate.storageId(), candidate.objectKey());
        long extraBytes = candidate.fileSize() - candidate.billedExportBytes();
        if (extraBytes > 0) checkCapacity(tenantId, maxMb, extraBytes);
    }

    private void checkCapacity(Long tenantId, long maxMb, long bytes) {
        BigInteger total = sessions.allocatedBytes(tenantId).add(BigInteger.valueOf(bytes));
        if (total.compareTo(BigInteger.valueOf(maxMb).shiftLeft(20)) > 0) {
            throw new BizException("TENANT_STORAGE_QUOTA_EXCEEDED", "可用存储容量不足，包含在途上传的预留空间");
        }
    }

    private void requireUnclaimed(Long tenantId, Long storageId, String key) {
        if (sessions.claimedObjectKey(tenantId, storageId, key) > 0)
            throw new BizException("FILE_OBJECT_KEY_CONFLICT", "存储路径已有文件或在途上传");
    }

    public FileUploadSessionEntity ownedSession(Long id, Long tenantId) {
        if (!TenantAccessContextHolder.requiredTenantId().equals(tenantId)) {
            throw new BizException("TENANT_ACCESS_DENIED", "租户上下文与上传会话不一致");
        }
        FileUploadSessionEntity session = sessions.selectById(id);
        if (session == null || !tenantId.equals(session.getTenantId())) {
            throw new BizException("FILE_UPLOAD_SESSION_NOT_FOUND", "上传会话不存在");
        }
        return session;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public FileObjectEntity finish(Long tenantId, Long sessionId, Supplier<FileObjectEntity> register) {
        quotas.lockLimit(tenantId, "STORAGE_MB");
        FileUploadSessionEntity session = ownedSession(sessionId, tenantId);
        if ("COMPLETED".equals(session.getStatus())) return completedObject(session);
        if (!active(session)) throw new BizException("FILE_UPLOAD_SESSION_EXPIRED", "上传会话已失效");
        FileObjectEntity object = register.get();
        if (object.getId() == null || !session.getFileSize().equals(object.getFileSize())) {
            throw new BizException("FILE_UPLOAD_SIZE_MISMATCH", "已上传文件与预留大小不一致");
        }
        session.setFileObjectId(object.getId());
        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now());
        sessions.updateById(session);
        return object;
    }

    public FileObjectEntity completedObject(FileUploadSessionEntity session) {
        FileObjectEntity file = session.getFileObjectId() == null
                ? objects.selectOne(new LambdaQueryWrapper<FileObjectEntity>()
                        .eq(FileObjectEntity::getTenantId, session.getTenantId())
                        .eq(FileObjectEntity::getBucketId, session.getBucketId())
                        .eq(FileObjectEntity::getStorageId, session.getStorageId())
                        .eq(FileObjectEntity::getObjectKey, session.getObjectKey())
                        .eq(FileObjectEntity::getVersionNo, 1))
                : objects.selectById(session.getFileObjectId());
        if (file == null) throw new BizException("FILE_OBJECT_NOT_FOUND", "已完成会话的文件不存在");
        return file;
    }

    public FileUploadSessionEntity pendingPut(Long tenantId, Long storageId, String key) {
        if (!TenantAccessContextHolder.requiredTenantId().equals(tenantId)) {
            throw new BizException("TENANT_ACCESS_DENIED", "租户上下文与上传会话不一致");
        }
        FileUploadSessionEntity session = sessions.selectOne(new LambdaQueryWrapper<FileUploadSessionEntity>()
                .eq(FileUploadSessionEntity::getTenantId, tenantId).eq(FileUploadSessionEntity::getStorageId, storageId)
                .eq(FileUploadSessionEntity::getObjectKey, key).eq(FileUploadSessionEntity::getStatus, "INIT")
                .gt(FileUploadSessionEntity::getExpireAt, LocalDateTime.now()));
        if (session == null || !key.equals(session.getObjectKey())) throw new BizException("FILE_UPLOAD_SESSION_REQUIRED", "请先为该对象创建有效的上传会话");
        return session;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void discard(Long tenantId, Long sessionId, boolean onlyExpired, Consumer<FileUploadSessionEntity> removeObject) {
        quotas.lockLimit(tenantId, "STORAGE_MB");
        FileUploadSessionEntity session = ownedSession(sessionId, tenantId);
        if ("COMPLETED".equals(session.getStatus()) || "FAILED".equals(session.getStatus())) return;
        if (onlyExpired && active(session)) return;
        try {
            removeObject.accept(session);
            session.setStatus("FAILED");
        } catch (RuntimeException failure) {
            session.setStatus("CLEANUP");
            session.setCleanupAfter(LocalDateTime.now().plusMinutes(5));
            log.warn("File cleanup pending for session {}", sessionId, failure);
        }
        sessions.updateById(session);
    }

    static String fingerprint(String... fields) {
        StringBuilder data = new StringBuilder();
        for (String field : fields) {
            if (field == null) data.append("-1:");
            else data.append(field.length()).append(':').append(field);
        }
        return contentHash(data.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String contentHash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static boolean active(FileUploadSessionEntity session) {
        return ("INIT".equals(session.getStatus()) || "TRANSFERRING".equals(session.getStatus()))
                && session.getExpireAt() != null && session.getExpireAt().isAfter(LocalDateTime.now());
    }
}
