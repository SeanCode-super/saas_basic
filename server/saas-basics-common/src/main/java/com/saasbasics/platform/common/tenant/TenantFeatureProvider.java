package com.saasbasics.platform.common.tenant;

import java.util.Set;

/**
 * 租户应用能力与特性提供者接口 (Tenant App Entitlement & Subscription Provider)
 */
public interface TenantFeatureProvider {

    /**
     * 判断指定租户是否拥有指定应用特性
     *
     * @param tenantId 租户ID
     * @param feature 应用特性编码 (如 "workflow", "crm", "erp" 等)
     * @return 是否拥有访问权
     */
    boolean hasFeature(Long tenantId, String feature);

    /**
     * 获取指定租户所有生效的应用特性集合 (包含套餐特性、独立增购、试用特性并排除禁用特性)
     *
     * @param tenantId 租户ID
     * @return 最终生效的特性集合
     */
    Set<String> getEffectiveFeatures(Long tenantId);

    /**
     * 清理/失效指定租户的特性缓存 (实时刷新)
     *
     * @param tenantId 租户ID
     */
    void invalidateCache(Long tenantId);
}
