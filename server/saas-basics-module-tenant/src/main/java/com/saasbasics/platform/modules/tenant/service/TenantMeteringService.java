package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import java.util.ArrayList;
import java.security.DigestOutputStream;
import org.springframework.util.StreamUtils;
import org.springframework.transaction.annotation.Isolation;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.tenant.dto.TenantBillResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantMeteringSummaryResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSnapshotResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSnapshotRestoreResponse;
import com.saasbasics.platform.modules.tenant.entity.PlatTenantBillEntity;
import com.saasbasics.platform.modules.tenant.entity.PlatTenantMeteringEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantQuotaEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantSnapshotRecordEntity;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantBillMapper;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantMeteringMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantQuotaMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantSnapshotRecordMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 租户计量计费与灾备快照服务：
 * 基于真实业务库统计 API 调用、文件存储与活跃席位，支持快照校验和租户业务表事务恢复。
 */
@Service
public class TenantMeteringService {

    private static final String FORMAT = "TianshuSaaS-Tenant-DR-Zip-v3";
    private final PlatTenantMeteringMapper meteringMapper;
    private final PlatTenantBillMapper billMapper;
    private final TenantMapper tenantMapper;
    private final TenantQuotaMapper quotaMapper;
    private final TenantSnapshotRecordMapper snapshotRecords;
    private final TenantBusinessSnapshot business;
    private final ObjectMapper objectMapper;
    private final Path snapshotRootDir;

    public TenantMeteringService(
            PlatTenantMeteringMapper meteringMapper,
            PlatTenantBillMapper billMapper,
            TenantMapper tenantMapper,
            TenantQuotaMapper quotaMapper,
            TenantSnapshotRecordMapper snapshotRecords,
            TenantBusinessSnapshot business,
            ObjectMapper objectMapper) {
        this.meteringMapper = meteringMapper;
        this.billMapper = billMapper;
        this.tenantMapper = tenantMapper;
        this.quotaMapper = quotaMapper;
        this.snapshotRecords = snapshotRecords;
        this.business = business;
        this.objectMapper = objectMapper
                .copy()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.snapshotRootDir = Path.of(System.getProperty("java.io.tmpdir"), "saas-basics-snapshots");
    }

    public TenantMeteringSummaryResponse getSummary() {
        Long tenantId = getTenantId();

        int activeSeats = meteringMapper.countActiveSeats(tenantId);
        long storageUsed = meteringMapper.sumStorageUsed(tenantId);
        long auditCalls = meteringMapper.countAuditCalls(tenantId);
        long todayAuditCalls = meteringMapper.countTodayAuditCalls(tenantId);
        long loggedTotalCalls = meteringMapper.sumUsage(tenantId, "API_CALL", false);
        long loggedTodayCalls = meteringMapper.sumUsage(tenantId, "API_CALL", true);

        long totalCalls = Math.max(auditCalls, loggedTotalCalls);
        long todayCalls = Math.max(todayAuditCalls, loggedTodayCalls);

        Map<String, Long> limits = new LinkedHashMap<>();
        for (var quota : quotaMapper.selectList(new LambdaQueryWrapper<TenantQuotaEntity>()
                .eq(TenantQuotaEntity::getTenantId, tenantId)
                .in(TenantQuotaEntity::getQuotaCode, "USER_COUNT", "STORAGE_MB"))) {
            limits.put(quota.getQuotaCode(), quota.getQuotaValue());
        }
        if (limits.get("USER_COUNT") == null || limits.get("STORAGE_MB") == null) {
            throw new BizException("TENANT_QUOTA_NOT_CONFIGURED", "租户的账号席位或文件存储配额尚未配置");
        }
        int seatQuota = Math.toIntExact(limits.get("USER_COUNT"));
        long storageQuota = Math.multiplyExact(limits.get("STORAGE_MB"), 1024L * 1024L);

        double estCost = Math.round((activeSeats * 29.0 + (storageUsed / 1_073_741_824.0) * 1.5 + totalCalls * 0.002) * 100.0) / 100.0;

        return new TenantMeteringSummaryResponse(
                tenantId,
                totalCalls,
                todayCalls,
                storageUsed,
                storageQuota,
                activeSeats,
                seatQuota,
                estCost
        );
    }

    public List<TenantBillResponse> listBills() {
        Long tenantId = getTenantId();
        return billMapper.selectList(new LambdaQueryWrapper<PlatTenantBillEntity>()
                        .eq(PlatTenantBillEntity::getTenantId, tenantId)
                        .eq(PlatTenantBillEntity::getDeleted, 0)
                        .orderByDesc(PlatTenantBillEntity::getId))
                .stream()
                .map(b -> new TenantBillResponse(
                        b.getId(),
                        b.getTenantId(),
                        b.getBillNo(),
                        b.getBillingPeriod(),
                        b.getTotalAmount(),
                        b.getPaidAmount(),
                        b.getPaymentStatus(),
                        b.getItemBreakdownJson(),
                        b.getRemark(),
                        b.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordUsage(Long tenantId, String type, long value) {
        PlatTenantMeteringEntity entity = new PlatTenantMeteringEntity();
        long current = getTenantId();
        if (tenantId != null && tenantId != current) throw new BizException("TENANT_ACCESS_DENIED", "无权为其他租户写入用量");
        entity.setTenantId(current);
        entity.setMeteringType(type != null ? type.toUpperCase() : "API_CALL");
        entity.setMetricValue(value);
        entity.setRecordTime(LocalDateTime.now());
        entity.setBillable(1);
        meteringMapper.insert(entity);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public TenantSnapshotResponse generateTenantSnapshot(Long tenantId) {
        AuthPrincipal principal = requiredPrincipal();
        Long currentTenantId = principal.tenantId();
        Long effectiveTenantId = tenantId != null ? tenantId : currentTenantId;
        if (tenantId != null && !tenantId.equals(currentTenantId) && !isPlatformAdministrator(principal)) {
            throw new BizException("TENANT_ACCESS_DENIED", "无权跨租户导出灾备快照");
        }
        TenantEntity tenant = tenantMapper.selectById(effectiveTenantId);
        if (tenant == null) {
            throw new BizException("TENANT_NOT_FOUND", "目标租户不存在: " + effectiveTenantId);
        }
        String tenantCode = tenant.getTenantCode();
        String snapshotId = "SNAP_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String fileName = snapshotId + ".zip";

        Map<String, Long> entityCounts = new LinkedHashMap<>();
        Map<String, String> tableHashes = new LinkedHashMap<>();

        Path targetPath = resolveSafeSnapshotPath(fileName);
        try {
            Files.createDirectories(snapshotRootDir);
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetPath), StandardCharsets.UTF_8)) {
                var budget = new TenantSnapshotArchive.Budget();
                var schema = business.schema(effectiveTenantId);
                List<String> tables = new ArrayList<>(TenantBusinessSnapshot.BASE_TABLES);
                tables.addAll(schema.keySet());
                for (String table : tables) {
                    zos.putNextEntry(new ZipEntry("tables/" + table + ".json"));
                    MessageDigest hash = MessageDigest.getInstance("SHA-256");
                    var sink = new DigestOutputStream(budget.limit(StreamUtils.nonClosing(zos)), hash);
                    try (var json = objectMapper.createGenerator(sink)) {
                        entityCounts.put(table, business.exportRows(table, schema.get(table), effectiveTenantId, json));
                    }
                    tableHashes.put(table, HexFormat.of().formatHex(hash.digest()));
                    zos.closeEntry();
                }

                Map<String, Object> manifest = new LinkedHashMap<>();
                manifest.put("format", FORMAT);
                manifest.put("businessSchema", schema);
                manifest.put("snapshotId", snapshotId);
                manifest.put("tenantId", effectiveTenantId);
                manifest.put("tenantCode", tenantCode);
                manifest.put("generatedAt", LocalDateTime.now().toString());
                manifest.put("entityCounts", entityCounts);
                manifest.put("tableChecksums", tableHashes);

                byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
                zos.putNextEntry(new ZipEntry("manifest.json"));
                budget.limit(zos).write(manifestBytes);
                zos.closeEntry();
            }
            long fileSize = Files.size(targetPath);
            if (fileSize > TenantSnapshotArchive.MAX_BYTES) throw TenantBusinessSnapshot.invalid("快照文件超出 500 MB");
            String archiveChecksum = TenantSnapshotArchive.sha256(Files.newInputStream(targetPath));
            LocalDateTime now = LocalDateTime.now();

            TenantSnapshotRecordEntity record = new TenantSnapshotRecordEntity();
            record.setTenantId(effectiveTenantId);
            record.setSnapshotId(snapshotId);
            record.setTenantCode(tenantCode);
            record.setFileName(fileName);
            record.setFileSizeBytes(fileSize);
            record.setChecksumSha256(archiveChecksum);
            record.setStoragePath(targetPath.toAbsolutePath().toString());
            record.setTableCountsJson(objectMapper.writeValueAsString(entityCounts));
            record.setStatus("COMPLETED");
            record.setCreatedBy(principal.username());
            record.setCreatedAt(now);
            snapshotRecords.insert(record);

            return new TenantSnapshotResponse(
                    effectiveTenantId,
                    tenantCode,
                    snapshotId,
                    archiveChecksum,
                    fileSize,
                    entityCounts,
                    now,
                    "/api/tenant/snapshot/download/" + snapshotId
            );
        } catch (Exception ex) {
            try { Files.deleteIfExists(targetPath); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            if (ex instanceof BizException biz) throw biz;
            throw new BizException("SNAPSHOT_EXPORT_FAILED", "生成租户灾备归档失败: " + ex.getMessage());
        }
    }

    public Resource snapshotResource(String snapshotId) {
        if (snapshotId == null || !snapshotId.matches("^[A-Za-z0-9_-]{4,80}$")) {
            throw new BizException("INVALID_SNAPSHOT_ID", "非法的快照编号格式");
        }
        AuthPrincipal principal = requiredPrincipal();
        Path targetPath = resolveSafeSnapshotPath(snapshotId + ".zip");
        TenantSnapshotRecordEntity record = snapshotRecords.selectOne(new LambdaQueryWrapper<TenantSnapshotRecordEntity>()
                .eq(TenantSnapshotRecordEntity::getSnapshotId, snapshotId)
                .last("LIMIT 1"));
        if (record == null) {
            throw new BizException("SNAPSHOT_NOT_FOUND", "灾备快照记录不存在: " + snapshotId);
        }
        if (!record.getTenantId().equals(principal.tenantId()) && !isPlatformAdministrator(principal)) {
            throw new BizException("TENANT_ACCESS_DENIED", "无权下载其他租户的灾备快照");
        }
        if (record.getStoragePath() != null) {
            Path dbPath = Path.of(record.getStoragePath()).normalize();
            if (dbPath.startsWith(snapshotRootDir.normalize()) && Files.exists(dbPath)) {
                targetPath = dbPath;
            }
        }
        if (!Files.exists(targetPath)) {
            throw new BizException("SNAPSHOT_NOT_FOUND", "灾备快照文件不存在或已清理: " + snapshotId);
        }
        return new FileSystemResource(targetPath);
    }

    public byte[] loadSnapshotArchive(String snapshotId) {
        try (var in = snapshotResource(snapshotId).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new BizException("SNAPSHOT_READ_FAILED", "读取归档失败：" + ex.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public TenantSnapshotRestoreResponse restoreTenantSnapshot(MultipartFile file, Long targetTenantId) {
        return restoreTenantSnapshot(file, targetTenantId, false);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public TenantSnapshotRestoreResponse restoreTenantSnapshot(MultipartFile file, Long targetTenantId, boolean apply) {
        AuthPrincipal principal = requiredPrincipal();
        Long currentTenantId = principal.tenantId();
        Long tenantId = targetTenantId != null ? targetTenantId : currentTenantId;
        if (targetTenantId != null && !targetTenantId.equals(currentTenantId) && !isPlatformAdministrator(principal)) {
            throw new BizException("TENANT_ACCESS_DENIED", "无权跨租户恢复灾备快照");
        }
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException("TENANT_NOT_FOUND", "目标租户不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("INVALID_FILE", "快照备份文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".json") && !filename.endsWith(".zip"))) {
            throw new BizException("INVALID_FILE_TYPE", "仅支持平台导出的 .zip 或 .json 灾备归档文件");
        }
        if (file.getSize() > TenantSnapshotArchive.MAX_BYTES) {
            throw new BizException("FILE_TOO_LARGE", "快照文件超出 500 MB 限制");
        }

        try {
            Map<String, Long> restoredCounts = new LinkedHashMap<>();
            String snapshotId = filename.replaceAll("\\.[^.]+$", "");

            if (filename.endsWith(".zip")) {
                try (var archive = new TenantSnapshotArchive()) {
                    archive.unpack(file.getInputStream());
                    Map<String, Path> entries = archive.entries;
                    Path manifestFile = entries.remove("manifest.json");
                    if (manifestFile == null) throw TenantBusinessSnapshot.invalid("归档缺少 manifest.json");
                    JsonNode manifest = objectMapper.readTree(manifestFile.toFile());
                    snapshotId = manifest.path("snapshotId").asText();
                    if (snapshotId.isBlank() || !tenant.getTenantCode().equals(manifest.path("tenantCode").asText())
                            || tenantId != manifest.path("tenantId").asLong(-1)) {
                        throw TenantBusinessSnapshot.invalid("归档与当前租户不匹配");
                    }
                    JsonNode hashes = manifest.path("tableChecksums");
                    JsonNode counts = manifest.path("entityCounts");
                    if (!hashes.isObject() || hashes.isEmpty() || entries.size() != hashes.size() || counts.size() != hashes.size()) {
                        throw TenantBusinessSnapshot.invalid("归档表清单不完整");
                    }
                    if (apply) {
                        if (!FORMAT.equals(manifest.path("format").asText())) throw TenantBusinessSnapshot.invalid("旧版归档仅支持校验");
                        verifyRegisteredArchive(tenantId, snapshotId, TenantSnapshotArchive.sha256(file.getInputStream()));
                        tenantMapper.selectOne(new LambdaQueryWrapper<TenantEntity>()
                                .eq(TenantEntity::getId, tenantId)
                                .last("FOR UPDATE"));
                    }
                    var schema = apply ? business.schema(tenantId) : Map.<String, List<TenantBusinessSnapshot.Column>>of();
                    JsonNode archivedSchema = manifest.path("businessSchema");
                    if (apply) {
                        if (!archivedSchema.isObject() || archivedSchema.isEmpty()) throw TenantBusinessSnapshot.invalid("归档没有可恢复的业务表");
                        Map<String, List<TenantBusinessSnapshot.Column>> selected = new LinkedHashMap<>();
                        for (var it = archivedSchema.fieldNames(); it.hasNext();) {
                            String name = it.next();
                            if (!hashes.has(name) || !schema.containsKey(name) || !archivedSchema.get(name).equals(objectMapper.valueToTree(schema.get(name)))) {
                                throw TenantBusinessSnapshot.invalid("业务表结构已变化或缺失：" + name);
                            }
                            selected.put(name, schema.get(name));
                        }
                        business.checkRestoreTables(selected);
                    }
                    for (var it = hashes.fieldNames(); it.hasNext();) {
                        String table = it.next();
                        Path entry = entries.get("tables/" + table + ".json");
                        if (entry == null || !TenantSnapshotArchive.sha256(Files.newInputStream(entry)).equals(hashes.get(table).asText())) {
                            throw TenantBusinessSnapshot.invalid("归档表校验失败：" + table);
                        }
                        boolean write = apply && archivedSchema.has(table);
                        long count;
                        try (var input = objectMapper.createParser(entry.toFile())) {
                            count = write ? business.replace(table, schema.get(table), input, objectMapper, tenantId)
                                    : TenantBusinessSnapshot.readRows(input, objectMapper, null);
                        }
                        if (count != counts.path(table).asLong(-1)) throw TenantBusinessSnapshot.invalid("归档表行数不匹配：" + table);
                        if (!apply || write) restoredCounts.put(table, count);
                    }
                }
            } else {
                if (apply) throw TenantBusinessSnapshot.invalid("JSON 清单仅支持核对记录数");
                JsonNode root;
                try (var in = file.getInputStream()) { root = objectMapper.readTree(in); }
                JsonNode counts = root.path("entityCounts");
                if (!counts.isObject() || counts.isEmpty() || !root.hasNonNull("snapshotId")) throw TenantBusinessSnapshot.invalid("无效的快照清单");
                if ((root.has("tenantId") && root.path("tenantId").asLong(-1) != tenantId)
                        || (root.has("tenantCode") && !tenant.getTenantCode().equals(root.path("tenantCode").asText()))) {
                    throw TenantBusinessSnapshot.invalid("归档与当前租户不匹配");
                }
                snapshotId = root.get("snapshotId").asText();
                counts.fields().forEachRemaining(e -> restoredCounts.put(e.getKey(), e.getValue().asLong()));
            }

            String restoreBatchNo = "RESTORE_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return new TenantSnapshotRestoreResponse(
                    tenantId,
                    tenant.getTenantCode(),
                    snapshotId,
                    restoreBatchNo,
                    apply ? "RESTORED" : "VALIDATED",
                    restoredCounts,
                    LocalDateTime.now(),
                    apply ? "已恢复 " + restoredCounts.size() + " 张业务表" : "归档已校验，未写入数据"
            );
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("SNAPSHOT_RESTORE_FAILED", "解析或校验灾备快照失败: " + ex.getMessage());
        }
    }

    private void verifyRegisteredArchive(long tenantId, String snapshotId, String checksum) throws Exception {
        TenantSnapshotRecordEntity record = snapshotRecords.selectOne(new LambdaQueryWrapper<TenantSnapshotRecordEntity>()
                .eq(TenantSnapshotRecordEntity::getTenantId, tenantId).eq(TenantSnapshotRecordEntity::getSnapshotId, snapshotId));
        if (record == null || !checksum.equals(record.getChecksumSha256())) {
            throw TenantBusinessSnapshot.invalid("归档与本租户的备份登记不一致");
        }
    }

    private Path resolveSafeSnapshotPath(String fileName) {
        Path normalizedRoot = snapshotRootDir.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(fileName).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new BizException("INVALID_SNAPSHOT_PATH", "非法快照路径");
        }
        return resolved;
    }

    private static boolean isPlatformAdministrator(AuthPrincipal principal) {
        return principal.isPlatformTenant() && principal.isSuperAdmin() && "PLATFORM".equalsIgnoreCase(principal.userType());
    }

    private Long getTenantId() {
        return requiredPrincipal().tenantId();
    }

    private AuthPrincipal requiredPrincipal() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "租户操作需要登录");
        }
        return principal;
    }
}
