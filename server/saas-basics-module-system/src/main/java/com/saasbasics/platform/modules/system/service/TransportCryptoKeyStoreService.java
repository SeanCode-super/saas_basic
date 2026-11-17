package com.saasbasics.platform.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.saasbasics.platform.modules.system.entity.TransportCryptoKeyEntity;
import com.saasbasics.platform.modules.system.mapper.TransportCryptoKeyMapper;
import com.saasbasics.platform.security.transport.TransportCryptoKeyRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 传输加密 X25519 密钥环数据库存储适配层（独立于 KeyRing 与业务编排层，避免启动循环依赖）。
 */
@Service
public class TransportCryptoKeyStoreService implements TransportCryptoKeyRepository {

    private final ObjectProvider<TransportCryptoKeyMapper> keyMapperProvider;

    public TransportCryptoKeyStoreService(ObjectProvider<TransportCryptoKeyMapper> keyMapperProvider) {
        this.keyMapperProvider = keyMapperProvider;
    }

    @Override
    public List<PersistedKeyRecord> loadRecentKeys(int maxCount) {
        TransportCryptoKeyMapper mapper = keyMapperProvider.getIfAvailable();
        if (mapper == null) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(20, maxCount));
        List<TransportCryptoKeyEntity> rows = mapper.selectList(
                new LambdaQueryWrapper<TransportCryptoKeyEntity>()
                        .eq(TransportCryptoKeyEntity::getDeleted, 0)
                        .orderByDesc(TransportCryptoKeyEntity::getIsActive)
                        .orderByDesc(TransportCryptoKeyEntity::getIssuedAt)
                        .last("LIMIT " + safeLimit)
        );
        return rows.stream()
                .map(row -> new PersistedKeyRecord(
                        row.getKeyId(),
                        row.getKeySource(),
                        row.getSealedPrivateKey(),
                        row.getPublicKeyBase64(),
                        row.getFingerprintSha256(),
                        Integer.valueOf(1).equals(row.getIsActive()),
                        row.getIssuedAt() != null ? row.getIssuedAt().toInstant(ZoneOffset.UTC) : Instant.now(),
                        row.getRemark()
                ))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveActiveKey(PersistedKeyRecord record) {
        TransportCryptoKeyMapper mapper = keyMapperProvider.getIfAvailable();
        if (mapper == null || record == null) {
            return;
        }
        mapper.update(
                null,
                new LambdaUpdateWrapper<TransportCryptoKeyEntity>()
                        .eq(TransportCryptoKeyEntity::getDeleted, 0)
                        .eq(TransportCryptoKeyEntity::getIsActive, 1)
                        .set(TransportCryptoKeyEntity::getIsActive, 0)
        );
        TransportCryptoKeyEntity existing = mapper.selectOne(
                new LambdaQueryWrapper<TransportCryptoKeyEntity>()
                        .eq(TransportCryptoKeyEntity::getKeyId, record.keyId())
                        .eq(TransportCryptoKeyEntity::getDeleted, 0)
                        .last("LIMIT 1")
        );
        LocalDateTime issuedUtc = record.issuedAt() != null
                ? LocalDateTime.ofInstant(record.issuedAt(), ZoneOffset.UTC)
                : LocalDateTime.now(ZoneOffset.UTC);

        if (existing == null) {
            TransportCryptoKeyEntity entity = new TransportCryptoKeyEntity();
            entity.setKeyId(record.keyId());
            entity.setKeySource(record.keySource());
            entity.setSealedPrivateKey(record.sealedPrivateKey());
            entity.setPublicKeyBase64(record.publicKeyBase64());
            entity.setFingerprintSha256(record.fingerprintSha256());
            entity.setIsActive(1);
            entity.setIssuedAt(issuedUtc);
            entity.setRemark(record.remark());
            mapper.insert(entity);
        } else {
            existing.setKeySource(record.keySource());
            existing.setSealedPrivateKey(record.sealedPrivateKey());
            existing.setPublicKeyBase64(record.publicKeyBase64());
            existing.setFingerprintSha256(record.fingerprintSha256());
            existing.setIsActive(1);
            existing.setIssuedAt(issuedUtc);
            existing.setRemark(record.remark());
            mapper.updateById(existing);
        }
    }
}
