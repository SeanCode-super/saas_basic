package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.ai.application.dto.AiPromptDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPromptSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPromptVersionDTO;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiPromptEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiPromptVersionEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiPromptMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiPromptVersionMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI Prompt 元数据与不可变语义版本管理服务。
 */
@Service
public class AiPromptService {

    private final AiPromptMapper promptMapper;
    private final AiPromptVersionMapper versionMapper;

    public AiPromptService(AiPromptMapper promptMapper, AiPromptVersionMapper versionMapper) {
        this.promptMapper = promptMapper;
        this.versionMapper = versionMapper;
    }

    public List<AiPromptDTO> listPrompts() {
        List<AiPromptEntity> prompts = promptMapper.selectList(
                new LambdaQueryWrapper<AiPromptEntity>().orderByAsc(AiPromptEntity::getId)
        );
        return prompts.stream().map(p -> {
            AiPromptVersionEntity activeVer = versionMapper.selectOne(
                    new LambdaQueryWrapper<AiPromptVersionEntity>()
                            .eq(AiPromptVersionEntity::getPromptId, p.getId())
                            .eq(AiPromptVersionEntity::getVersionNo, p.getActiveVersion())
                            .last("LIMIT 1")
            );
            return new AiPromptDTO(
                    p.getId(),
                    p.getTenantId(),
                    p.getPromptCode(),
                    p.getPromptName(),
                    p.getCategory(),
                    p.getActiveVersion(),
                    p.getStatus(),
                    activeVer != null ? activeVer.getSystemPrompt() : "",
                    activeVer != null ? activeVer.getUserTemplate() : "{{question}}",
                    activeVer != null ? activeVer.getModelHint() : "",
                    p.getRemark(),
                    p.getUpdatedAt()
            );
        }).toList();
    }

    public List<AiPromptVersionDTO> listVersions(String promptCode) {
        AiPromptEntity prompt = requirePromptByCode(promptCode);
        return versionMapper.selectList(
                new LambdaQueryWrapper<AiPromptVersionEntity>()
                        .eq(AiPromptVersionEntity::getPromptId, prompt.getId())
                        .orderByDesc(AiPromptVersionEntity::getVersionNo)
        ).stream().map(v -> new AiPromptVersionDTO(
                v.getId(),
                v.getPromptId(),
                v.getVersionNo(),
                v.getSystemPrompt(),
                v.getUserTemplate(),
                v.getVariablesJson(),
                v.getModelHint(),
                v.getChangelog(),
                v.getIsPublished(),
                v.getCreatedAt()
        )).toList();
    }

    @Transactional
    public AiPromptVersionDTO saveNewVersion(AiPromptSaveRequest req) {
        if (req.promptCode() == null || req.promptCode().isBlank()) {
            throw new BizException("VALIDATION_ERROR", "Prompt 编码不能为空");
        }
        if (req.systemPrompt() == null || req.systemPrompt().isBlank()) {
            throw new BizException("VALIDATION_ERROR", "System Prompt 指令内容不能为空");
        }
        Long tenantId = currentTenantId();
        AiPromptEntity prompt = promptMapper.selectOne(
                new LambdaQueryWrapper<AiPromptEntity>()
                        .eq(AiPromptEntity::getPromptCode, req.promptCode().trim())
                        .last("LIMIT 1")
        );
        int nextVersionNo = 1;
        boolean publish = req.publishImmediately() == null || req.publishImmediately();

        if (prompt == null) {
            prompt = new AiPromptEntity();
            prompt.setTenantId(tenantId);
            prompt.setPromptCode(req.promptCode().trim());
            prompt.setPromptName(req.promptName() != null && !req.promptName().isBlank()
                    ? req.promptName().trim() : req.promptCode().trim());
            prompt.setCategory(req.category() != null ? req.category() : "GENERAL");
            prompt.setActiveVersion(1);
            prompt.setStatus("ENABLED");
            promptMapper.insert(prompt);
        } else {
            List<AiPromptVersionEntity> versions = versionMapper.selectList(
                    new LambdaQueryWrapper<AiPromptVersionEntity>()
                            .eq(AiPromptVersionEntity::getPromptId, prompt.getId())
                            .orderByDesc(AiPromptVersionEntity::getVersionNo)
            );
            if (!versions.isEmpty()) {
                nextVersionNo = versions.get(0).getVersionNo() + 1;
            }
            if (req.promptName() != null && !req.promptName().isBlank()) {
                prompt.setPromptName(req.promptName().trim());
            }
            if (publish) {
                prompt.setActiveVersion(nextVersionNo);
            }
            promptMapper.updateById(prompt);
        }

        AiPromptVersionEntity ver = new AiPromptVersionEntity();
        ver.setTenantId(prompt.getTenantId());
        ver.setPromptId(prompt.getId());
        ver.setVersionNo(nextVersionNo);
        ver.setSystemPrompt(req.systemPrompt());
        ver.setUserTemplate(req.userTemplate() != null ? req.userTemplate() : "{{question}}");
        ver.setVariablesJson(req.variablesJson() != null ? req.variablesJson() : "[]");
        ver.setModelHint(req.modelHint());
        ver.setChangelog(req.changelog() != null ? req.changelog() : ("版本 v" + nextVersionNo));
        ver.setIsPublished(publish ? 1 : 0);
        versionMapper.insert(ver);

        return new AiPromptVersionDTO(
                ver.getId(),
                ver.getPromptId(),
                ver.getVersionNo(),
                ver.getSystemPrompt(),
                ver.getUserTemplate(),
                ver.getVariablesJson(),
                ver.getModelHint(),
                ver.getChangelog(),
                ver.getIsPublished(),
                ver.getCreatedAt()
        );
    }

    @Transactional
    public AiPromptDTO publishVersion(String promptCode, int versionNo) {
        AiPromptEntity prompt = requirePromptByCode(promptCode);
        List<AiPromptVersionEntity> allVersions = versionMapper.selectList(
                new LambdaQueryWrapper<AiPromptVersionEntity>().eq(AiPromptVersionEntity::getPromptId, prompt.getId())
        );
        AiPromptVersionEntity target = allVersions.stream()
                .filter(v -> Objects.equals(v.getVersionNo(), versionNo))
                .findFirst()
                .orElseThrow(() -> new BizException("AI_PROMPT_VERSION_NOT_FOUND", "未找到 Prompt 版本 v" + versionNo));

        target.setIsPublished(1);
        versionMapper.updateById(target);
        prompt.setActiveVersion(versionNo);
        promptMapper.updateById(prompt);

        return new AiPromptDTO(
                prompt.getId(),
                prompt.getTenantId(),
                prompt.getPromptCode(),
                prompt.getPromptName(),
                prompt.getCategory(),
                prompt.getActiveVersion(),
                prompt.getStatus(),
                target.getSystemPrompt(),
                target.getUserTemplate(),
                target.getModelHint(),
                prompt.getRemark(),
                prompt.getUpdatedAt()
        );
    }

    public AiPromptVersionEntity resolveActiveVersion(String promptCode) {
        if (promptCode == null || promptCode.isBlank()) {
            return null;
        }
        AiPromptEntity prompt = promptMapper.selectOne(
                new LambdaQueryWrapper<AiPromptEntity>()
                        .eq(AiPromptEntity::getPromptCode, promptCode.trim())
                        .last("LIMIT 1")
        );
        if (prompt == null) {
            return null;
        }
        return versionMapper.selectOne(
                new LambdaQueryWrapper<AiPromptVersionEntity>()
                        .eq(AiPromptVersionEntity::getPromptId, prompt.getId())
                        .eq(AiPromptVersionEntity::getVersionNo, prompt.getActiveVersion())
                        .last("LIMIT 1")
        );
    }

    private AiPromptEntity requirePromptByCode(String code) {
        AiPromptEntity prompt = promptMapper.selectOne(
                new LambdaQueryWrapper<AiPromptEntity>().eq(AiPromptEntity::getPromptCode, code).last("LIMIT 1")
        );
        if (prompt == null) {
            throw new BizException("AI_PROMPT_NOT_FOUND", "未找到 Prompt 定义: " + code);
        }
        return prompt;
    }

    private Long currentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }
}
