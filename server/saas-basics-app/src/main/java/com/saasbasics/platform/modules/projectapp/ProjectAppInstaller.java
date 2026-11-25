package com.saasbasics.platform.modules.projectapp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.codegen.project.ProjectDesign;
import com.saasbasics.platform.modules.codegen.project.ProjectWorkbench;
import com.saasbasics.platform.modules.tenant.dto.TenantAppSubscribeRequest;
import com.saasbasics.platform.modules.tenant.entity.*;
import com.saasbasics.platform.modules.tenant.mapper.*;
import com.saasbasics.platform.modules.tenant.service.*;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAppInstaller {
    public record Result(String code,String name,int revision,String tenantCode,String path) {}
    private final ProjectWorkbench workbench;
    private final ProjectConnection connections;
    private final IndustryWorkspace workspace;
    private final IndustryServiceMapper catalog;
    private final TenantAppService apps;
    private final TenantAppMapper subscriptions;
    private final TenantMapper tenants;
    private final ObjectMapper json;
    public ProjectAppInstaller(ProjectWorkbench workbench, ProjectConnection connections, IndustryWorkspace workspace,
            IndustryServiceMapper catalog,TenantAppService apps,TenantAppMapper subscriptions,TenantMapper tenants,ObjectMapper json) {
        this.workbench=workbench; this.connections=connections; this.workspace=workspace; this.catalog=catalog;
        this.apps=apps; this.subscriptions=subscriptions; this.tenants=tenants; this.json=json;
    }
    @Transactional(rollbackFor=Exception.class)
    public Result install(Long tenantId,ProjectDesign.Review review) throws IOException {
        var actor=AuthContext.get();
        if (actor==null || !actor.isPlatformTenant() || !actor.isSuperAdmin() || !"PLATFORM".equalsIgnoreCase(actor.userType()))
            throw new BizException("AUTH_FORBIDDEN","需要平台管理员权限");
        var manifest=json.convertValue(workbench.reviewedManifest(review),IndustryManifest.class);
        var connection=connections.load(review.design());
        if (tenantId==null || connection.members().keySet().stream().noneMatch(k->k.startsWith(tenantId+":")))
            throw new BizException("PROJECT_BINDING_REQUIRED","请先为目标租户配置业务账号绑定");
        var tenant=tenants.selectOne(new LambdaQueryWrapper<TenantEntity>().eq(TenantEntity::getId,tenantId).last("FOR UPDATE"));
        if (tenant==null) throw new BizException("TENANT_NOT_FOUND","目标租户不存在");
        var stored=catalog.selectOne(new LambdaQueryWrapper<IndustryServiceEntity>().eq(IndustryServiceEntity::getAppCode,manifest.code())
                .eq(IndustryServiceEntity::getRevision,manifest.revision()));
        if (stored==null) workspace.publishGenerated(manifest,json.writeValueAsString(review.design()));
        else if (stored.getProjectDesignJson()==null || !review.design().equals(json.readValue(stored.getProjectDesignJson(),ProjectDesign.class)))
            throw new BizException("APP_REVISION_INVALID","该修订已用于其他蓝图，请递增修订号");
        var current=subscriptions.selectOne(new LambdaQueryWrapper<TenantAppEntity>().eq(TenantAppEntity::getTenantId,tenantId)
                .eq(TenantAppEntity::getAppCode,manifest.code()));
        boolean same=current!=null && Integer.valueOf(manifest.revision()).equals(current.getInstalledRevision()) && "ENABLED".equals(current.getStatus());
        if (!same) {
            apps.subscribeApp(tenantId,new TenantAppSubscribeRequest(manifest.code(),null,"工坊生成",manifest.revision()));
            try (var ignored=TenantAccessContextHolder.openTenant(tenantId,tenant.getTenantCode(),actor.userId())) {
                subscriptions.grantGeneratedMenu(tenantId,tenant.getTenantCode(),manifest.code(),actor.userId());
                subscriptions.grantGeneratedActions(tenantId,tenant.getTenantCode(),manifest.code(),actor.userId());
            }
        }
        return new Result(manifest.code(),manifest.name(),manifest.revision(),tenant.getTenantCode(),"/apps/"+manifest.code());
    }
}
