package com.saasbasics.platform.modules.tenant.mapper;

import com.saasbasics.platform.common.exception.BizException;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantBootstrapMapper {

    final class GeneratedId {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long require(String message) {
            if (id == null) {
                throw new BizException("TENANT_BOOTSTRAP_FAILED", message);
            }
            return id;
        }
    }

    int cloneAllTenantApps(@Param("tenantId") Long tenantId);

    List<Map<String, Object>> selectTemplateApps();

    int upsertPackageApp(@Param("tenantId") Long tenantId,
                         @Param("appCode") String appCode,
                         @Param("appName") String appName,
                         @Param("status") String status);

    List<Map<String, Object>> selectPackageFeatures(@Param("packageId") Long packageId);

    int cloneProcessDefinitions(@Param("tenantId") Long tenantId);

    Map<String, Object> selectLoginTemplate();

    int insertLoginPolicy(@Param("key") GeneratedId key,
                          @Param("tenantId") Long tenantId,
                          @Param("tenantCode") String tenantCode,
                          @Param("tenantName") String tenantName,
                          @Param("source") Map<String, Object> source);

    record PasswordTemplate(int minLength, int maxLength, boolean requireUppercase, boolean requireLowercase,
                            boolean requireNumber, boolean requireSpecial, int passwordHistoryLimit,
                            int passwordExpireDays, int tempPasswordExpireHours) {}

    PasswordTemplate selectPasswordTemplate();

    int insertPasswordPolicy(@Param("key") GeneratedId key,
                             @Param("tenantId") Long tenantId,
                             @Param("tenantCode") String tenantCode,
                             @Param("tenantName") String tenantName,
                             @Param("source") PasswordTemplate source);

    int insertAdminRole(@Param("key") GeneratedId key,
                        @Param("tenantId") Long tenantId,
                        @Param("tenantCode") String tenantCode,
                        @Param("tenantName") String tenantName);

    int cloneApiResources(@Param("tenantId") Long tenantId);

    int assignRoleApis(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    int cloneMenus(@Param("tenantId") Long tenantId);

    int restoreMenuParents(@Param("tenantId") Long tenantId);

    int assignMenuPermissions(@Param("tenantId") Long tenantId,
                              @Param("roleId") Long roleId,
                              @Param("actorId") Long actorId);

    int assignRoleMenus(@Param("tenantId") Long tenantId,
                        @Param("roleId") Long roleId,
                        @Param("actorId") Long actorId);

    int insertDepartment(@Param("key") GeneratedId key,
                         @Param("tenantId") Long tenantId,
                         @Param("tenantCode") String tenantCode,
                         @Param("tenantName") String tenantName);

    int updateDepartmentPath(@Param("tenantId") Long tenantId, @Param("deptId") Long deptId);

    int insertPosition(@Param("key") GeneratedId key,
                       @Param("tenantId") Long tenantId,
                       @Param("tenantCode") String tenantCode);

    int insertEmployee(@Param("key") GeneratedId key,
                       @Param("tenantId") Long tenantId,
                       @Param("tenantName") String tenantName,
                       @Param("deptId") Long deptId,
                       @Param("positionId") Long positionId);

    int insertDataPermissionRule(@Param("tenantId") Long tenantId,
                                 @Param("resource") Map<String, Object> resource,
                                 @Param("roleId") Long roleId);

    int insertAdminUser(@Param("key") GeneratedId key,
                        @Param("tenantId") Long tenantId,
                        @Param("publicId") String publicId,
                        @Param("tenantName") String tenantName,
                        @Param("employeeId") Long employeeId,
                        @Param("tenantCode") String tenantCode,
                        @Param("passwordHash") String passwordHash);

    int cloneDictTypes(@Param("tenantId") Long tenantId);

    int cloneDictItems(@Param("tenantId") Long tenantId);

    int cloneSystemConfigs(@Param("tenantId") Long tenantId);

    int cloneMessages(@Param("tenantId") Long tenantId);

    int cloneDataScopes(@Param("tenantId") Long tenantId);

    int cloneSequenceRules(@Param("tenantId") Long tenantId);

    int cloneRiskRules(@Param("tenantId") Long tenantId);

    int cloneNotifyTemplates(@Param("tenantId") Long tenantId);

    int insertDefaultStorage(@Param("tenantId") Long tenantId);

    int insertDefaultBucket(@Param("tenantId") Long tenantId, @Param("storageId") Long storageId);

    int cloneUploadRoutes(@Param("tenantId") Long tenantId);

    int insertDefaultWarehouse(@Param("tenantId") Long tenantId, @Param("tenantName") String tenantName);

    int insertDefaultExecutor(@Param("tenantId") Long tenantId);

    int cloneAiProviders(@Param("tenantId") Long tenantId);

    int cloneAiModels(@Param("tenantId") Long tenantId);

    int cloneAiPrompts(@Param("tenantId") Long tenantId);

    int cloneAiPromptVersions(@Param("tenantId") Long tenantId);

    int cloneAiTools(@Param("tenantId") Long tenantId);

    int bindUserRole(@Param("tenantId") Long tenantId,
                     @Param("userId") Long userId,
                     @Param("roleId") Long roleId);

    Map<String, Object> selectClientTemplate();

    int insertPortalClient(@Param("key") GeneratedId key,
                           @Param("tenantId") Long tenantId,
                           @Param("tenantCode") String tenantCode,
                           @Param("tenantName") String tenantName,
                           @Param("clientSource") Map<String, Object> clientSource,
                           @Param("loginPolicyId") Long loginPolicyId,
                           @Param("passwordPolicyId") Long passwordPolicyId);

    Map<String, Object> selectTerminalTemplate();

    int insertPortalTerminal(@Param("tenantId") Long tenantId,
                             @Param("portalClientId") Long portalClientId,
                             @Param("tenantName") String tenantName,
                             @Param("terminalSource") Map<String, Object> terminalSource,
                             @Param("loginPolicyId") Long loginPolicyId,
                             @Param("passwordPolicyId") Long passwordPolicyId);

    Long selectDefaultStorageId(@Param("tenantId") Long tenantId);
}
