package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.tenant.entity.IndustryServiceEntity;
import com.saasbasics.platform.modules.tenant.mapper.IndustryServiceMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IndustryRegistry {
    private final IndustryServiceMapper mapper;
    private final ObjectMapper json;

    public IndustryRegistry(IndustryServiceMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public List<IndustryManifest> catalog() {
        return mapper.selectLatestCatalog().stream()
                .map(entity -> decode(entity.getManifestJson()))
                .toList();
    }

    public IndustryManifest latest(String code) {
        IndustryServiceEntity entity = mapper.selectOne(new LambdaQueryWrapper<IndustryServiceEntity>()
                .eq(IndustryServiceEntity::getAppCode, code)
                .orderByDesc(IndustryServiceEntity::getRevision)
                .last("LIMIT 1"));
        return entity != null ? decode(entity.getManifestJson()) : null;
    }

    public IndustryManifest revision(String code, int rev) {
        IndustryServiceEntity entity = mapper.selectOne(new LambdaQueryWrapper<IndustryServiceEntity>()
                .eq(IndustryServiceEntity::getAppCode, code)
                .eq(IndustryServiceEntity::getRevision, rev));
        if (entity == null) {
            throw new BizException("APP_NOT_FOUND", "应用版本不存在");
        }
        return decode(entity.getManifestJson());
    }

    IndustryManifest decode(String raw) {
        try { return json.readValue(raw, IndustryManifest.class); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored industry manifest", e); }
    }
}
