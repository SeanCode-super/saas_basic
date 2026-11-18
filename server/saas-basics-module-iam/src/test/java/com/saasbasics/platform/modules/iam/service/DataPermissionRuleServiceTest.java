package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.dto.AccessSubjectProfile;
import com.saasbasics.platform.modules.iam.dto.DataPermissionSqlPreviewRequest;
import com.saasbasics.platform.modules.iam.dto.DataPermissionSqlPreviewResponse;
import com.saasbasics.platform.modules.iam.entity.DataPermissionRuleEntity;
import com.saasbasics.platform.modules.iam.entity.RoleEntity;
import com.saasbasics.platform.modules.iam.mapper.DataPermissionRuleMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPermissionRuleServiceTest {

    @Mock
    private DataPermissionRuleMapper dataPermissionRuleMapper;

    @Mock
    private AccessSubjectProfileService accessSubjectProfileService;

    @Mock
    private AuditTrailService auditTrailService;

    @Mock
    private RoleMapper roleMapper;

    private DataPermissionRuleService service;
    private DataPermissionRuleService roleAwareService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("dataPermissionRuleMapper", dataPermissionRuleMapper);
        beanFactory.addBean("roleMapper", roleMapper);
        service = new DataPermissionRuleService(
                beanFactory.getBeanProvider(DataPermissionRuleMapper.class),
                accessSubjectProfileService,
                auditTrailService,
                objectMapper
        );
        roleAwareService = new DataPermissionRuleService(
                beanFactory.getBeanProvider(DataPermissionRuleMapper.class),
                accessSubjectProfileService,
                auditTrailService,
                objectMapper,
                beanFactory.getBeanProvider(RoleMapper.class)
        );
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private AuthPrincipal createPrincipal(Long userId, String userType, List<String> permissions, List<Long> roleIds) {
        return new AuthPrincipal(
                1L,
                1001L,
                "tenant_test",
                userId,
                "test_user",
                "Tester",
                userType,
                "ACTIVE",
                LocalDateTime.now().plusHours(2),
                permissions,
                List.of(),
                roleIds,
                List.of()
        );
    }

    @Test
    @DisplayName("未登录会话直接拒绝所有访问")
    void deniesWhenNoPrincipal() {
        DataPermissionSqlSpec spec = service.resolveDataPermission("iam:user");
        assertThat(spec.isDenyAll()).isTrue();
    }

    @Test
    @DisplayName("平台管理员拥有全部数据权限")
    void allowsAllForPlatformOperator() {
        AuthContext.set(createPrincipal(1L, "PLATFORM", List.of(), List.of()));
        DataPermissionSqlSpec spec = service.resolveDataPermission("iam:user");
        assertThat(spec.isAllowAll()).isTrue();
        assertThat(spec.getTenantId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("具备全局通配符权限的超级管理员拥有全部数据权限")
    void allowsAllForSuperAdminWithWildcard() {
        AuthContext.set(createPrincipal(2L, "TENANT", List.of("*"), List.of(10L)));
        DataPermissionSqlSpec spec = service.resolveDataPermission("iam:user");
        assertThat(spec.isAllowAll()).isTrue();
    }

    @Test
    @DisplayName("租户内未配置任何规则的资源默认租户内放行")
    void allowsAllWhenNoRulesConfiguredForResource() {
        AuthContext.set(createPrincipal(3L, "TENANT", List.of("iam:user:view"), List.of(20L)));
        when(accessSubjectProfileService.load(1001L, 3L))
                .thenReturn(new AccessSubjectProfile(1001L, 3L, 30L, 100L, 10L, 50L, List.of(20L)));
        when(dataPermissionRuleMapper.selectList(any()))
                .thenReturn(List.of());

        DataPermissionSqlSpec spec = service.resolveDataPermission("biz:custom_resource");
        assertThat(spec.isAllowAll()).isTrue();
        assertThat(spec.getTenantId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("存在规则但当前角色未匹配任何规则时全部拒绝")
    void deniesWhenRulesExistButUserNotMatched() {
        AuthContext.set(createPrincipal(4L, "TENANT", List.of("iam:user:view"), List.of(99L)));
        when(accessSubjectProfileService.load(1001L, 4L))
                .thenReturn(new AccessSubjectProfile(1001L, 4L, 40L, 100L, 10L, 50L, List.of(99L)));

        DataPermissionRuleEntity otherRule = new DataPermissionRuleEntity();
        otherRule.setTenantId(1001L);
        otherRule.setResourceCode("biz:order");
        otherRule.setSubjectType("ROLE");
        otherRule.setSubjectValue("88"); // user role is 99, doesn't match
        otherRule.setScopeType("DEPT_ONLY");

        when(dataPermissionRuleMapper.selectList(any()))
                .thenReturn(List.of(otherRule));

        DataPermissionSqlSpec spec = service.resolveDataPermission("biz:order");
        assertThat(spec.isDenyAll()).isTrue();
    }

    @Test
    @DisplayName("命中 SELF 个人范围规则时改写为仅本人")
    void resolvesSelfScope() {
        AuthContext.set(createPrincipal(5L, "TENANT", List.of(), List.of(10L)));
        when(accessSubjectProfileService.load(1001L, 5L))
                .thenReturn(new AccessSubjectProfile(1001L, 5L, 55L, 100L, 10L, 50L, List.of(10L)));

        DataPermissionRuleEntity selfRule = new DataPermissionRuleEntity();
        selfRule.setTenantId(1001L);
        selfRule.setResourceCode("biz:order");
        selfRule.setSubjectType("ROLE");
        selfRule.setSubjectValue("10");
        selfRule.setScopeType("SELF");

        when(dataPermissionRuleMapper.selectList(any()))
                .thenReturn(List.of(selfRule));

        DataPermissionSqlSpec spec = service.resolveDataPermission("biz:order");
        assertThat(spec.isAllowAll()).isFalse();
        assertThat(spec.isDenyAll()).isFalse();
        assertThat(spec.getUserIds()).containsExactly(5L);
        assertThat(spec.getEmployeeIds()).containsExactly(55L);
    }

    @Test
    @DisplayName("命中 DEPT_AND_CHILDREN 部门及子部门范围时级联展开部门树")
    void resolvesDeptAndChildrenScope() {
        AuthContext.set(createPrincipal(6L, "TENANT", List.of(), List.of(15L)));
        when(accessSubjectProfileService.load(1001L, 6L))
                .thenReturn(new AccessSubjectProfile(1001L, 6L, 66L, 200L, 10L, 50L, List.of(15L)));
        when(accessSubjectProfileService.expandDepartmentTree(1001L, List.of(200L)))
                .thenReturn(List.of(200L, 201L, 202L));

        DataPermissionRuleEntity deptRule = new DataPermissionRuleEntity();
        deptRule.setTenantId(1001L);
        deptRule.setResourceCode("biz:project");
        deptRule.setSubjectType("ROLE");
        deptRule.setSubjectValue("15");
        deptRule.setScopeType("DEPT_AND_CHILDREN");

        when(dataPermissionRuleMapper.selectList(any()))
                .thenReturn(List.of(deptRule));

        DataPermissionSqlSpec spec = service.resolveDataPermission("biz:project");
        assertThat(spec.getDepartmentIds()).containsExactlyInAnyOrder(200L, 201L, 202L);
        assertThat(spec.getOrgUnitIds()).containsExactlyInAnyOrder(200L, 201L, 202L);
    }

    @Test
    @DisplayName("多角色多规则并集计算（OR union）")
    void resolvesMultiRoleUnionScope() {
        // 用户兼具两个角色：角色 21（仅本部门 300）与 角色 22（自定义扩展员工 999 及部门 400）
        AuthContext.set(createPrincipal(7L, "TENANT", List.of(), List.of(21L, 22L)));
        when(accessSubjectProfileService.load(1001L, 7L))
                .thenReturn(new AccessSubjectProfile(1001L, 7L, 77L, 300L, 10L, 50L, List.of(21L, 22L)));

        DataPermissionRuleEntity rule1 = new DataPermissionRuleEntity();
        rule1.setTenantId(1001L);
        rule1.setResourceCode("biz:contract");
        rule1.setSubjectType("ROLE");
        rule1.setSubjectValue("21");
        rule1.setScopeType("DEPT_ONLY");

        DataPermissionRuleEntity rule2 = new DataPermissionRuleEntity();
        rule2.setTenantId(1001L);
        rule2.setResourceCode("biz:contract");
        rule2.setSubjectType("ROLE");
        rule2.setSubjectValue("22");
        rule2.setScopeType("CUSTOM");
        rule2.setConfigJson("{\"departmentIds\":[400],\"userIds\":[999],\"treePaths\":[\"/300/\"]}");

        when(dataPermissionRuleMapper.selectList(any()))
                .thenReturn(List.of(rule1, rule2));

        DataPermissionSqlSpec spec = service.resolveDataPermission("biz:contract");
        assertThat(spec.isAllowAll()).isFalse();
        assertThat(spec.getDepartmentIds()).containsExactlyInAnyOrder(300L, 400L);
        assertThat(spec.getUserIds()).containsExactly(999L);
        assertThat(spec.getTreePaths()).containsExactly("/300/");
    }

    @Test
    @DisplayName(":query 动作后缀归一化命中领域资源规则（crm:customer），并按 AND 生成部门成员与 ABAC 过滤")
    void normalizesActionSuffixToCanonicalDomainResource() {
        AuthContext.set(createPrincipal(8L, "TENANT", List.of("crm:customer:query"), List.of(30L)));
        when(accessSubjectProfileService.load(1001L, 8L))
                .thenReturn(new AccessSubjectProfile(1001L, 8L, 88L, 500L, 12L, 50L, List.of(30L)));
        when(accessSubjectProfileService.resolveUserIdsByDepartments(1001L, List.of(500L)))
                .thenReturn(List.of(8L, 9L));

        DataPermissionRuleEntity rule = new DataPermissionRuleEntity();
        rule.setTenantId(1001L);
        rule.setResourceCode("crm:customer");
        rule.setSubjectType("ROLE");
        rule.setSubjectValue("30");
        rule.setScopeType("DEPT_ONLY");
        rule.setConfigJson("{\"conditions\":[{\"field\":\"deal_status\",\"operator\":\"EQ\",\"value\":1}]}");

        when(dataPermissionRuleMapper.selectList(any())).thenReturn(List.of(rule));

        DataPermissionSqlSpec spec = roleAwareService.resolveDataPermission("crm:customer:query");
        assertThat(spec.isAllowAll()).isFalse();
        assertThat(spec.getDepartmentIds()).containsExactly(500L);
        assertThat(spec.getAbacConditions()).hasSize(1);
        assertThat(spec.toSqlExpression(new net.sf.jsqlparser.schema.Table("crm_customer"), "", "", "owner_user_id").toString())
                .isEqualTo("(crm_customer.owner_user_id IN (8, 9) AND crm_customer.deal_status = 1)");
    }

    @Test
    @DisplayName("未配置显式规则时按 L3 RoleEntity.dataScopeType 兜底；无有效角色时收敛为仅本人（Fail-Closed）")
    void fallsBackToRoleDataScopeAndSelfWhenNoRulesExist() {
        AuthContext.set(createPrincipal(10L, "TENANT", List.of(), List.of(40L)));
        when(accessSubjectProfileService.load(1001L, 10L))
                .thenReturn(new AccessSubjectProfile(1001L, 10L, 100L, 600L, 15L, 50L, List.of(40L)));
        when(dataPermissionRuleMapper.selectList(any())).thenReturn(List.of());

        RoleEntity selfRole = new RoleEntity();
        selfRole.setId(40L);
        selfRole.setTenantId(1001L);
        selfRole.setDataScopeType("SELF");
        when(roleMapper.selectList(any())).thenReturn(List.of(selfRole));

        DataPermissionSqlSpec spec = roleAwareService.resolveDataPermission("crm:receivable");
        assertThat(spec.isAllowAll()).isFalse();
        assertThat(spec.getUserIds()).containsExactly(10L);

        // 无角色分配时同样失败关闭至 SELF，绝不 allowAll
        when(accessSubjectProfileService.load(1001L, 10L))
                .thenReturn(new AccessSubjectProfile(1001L, 10L, 100L, 600L, 15L, 50L, List.of()));
        DataPermissionSqlSpec noRoleSpec = roleAwareService.resolveDataPermission("crm:receivable");
        assertThat(noRoleSpec.isAllowAll()).isFalse();
        assertThat(noRoleSpec.getUserIds()).containsExactly(10L);
    }

    @Test
    @DisplayName("SELF_AND_SUBORDINATES 展开负责人管辖部门下属成员列表")
    void expandsSubordinatesForLeader() {
        AuthContext.set(createPrincipal(12L, "TENANT", List.of(), List.of(50L)));
        when(accessSubjectProfileService.load(1001L, 12L))
                .thenReturn(new AccessSubjectProfile(1001L, 12L, 120L, 700L, 18L, 50L, List.of(50L)));
        when(accessSubjectProfileService.resolveSubordinateUserIds(1001L, 12L))
                .thenReturn(List.of(12L, 13L, 14L));

        DataPermissionRuleEntity rule = new DataPermissionRuleEntity();
        rule.setTenantId(1001L);
        rule.setResourceCode("*");
        rule.setSubjectType("USER");
        rule.setSubjectValue("12");
        rule.setScopeType("SELF_AND_SUBORDINATES");

        when(dataPermissionRuleMapper.selectList(any())).thenReturn(List.of(rule));

        DataPermissionSqlSpec spec = roleAwareService.resolveDataPermission("erp:order");
        assertThat(spec.getUserIds()).containsExactlyInAnyOrder(12L, 13L, 14L);
    }

    @Test
    @DisplayName("AST SQL 改写预览注入 tenant_id 且支持拒绝非 SELECT 语句")
    void previewsAstTenantAndDataScopeRewrite() {
        AuthContext.set(createPrincipal(1L, "PLATFORM", List.of("*"), List.of(1L)));
        DataPermissionSqlPreviewResponse preview = roleAwareService.previewSqlRewrite(
                new DataPermissionSqlPreviewRequest("SELECT * FROM iam_user WHERE status = 'ENABLED';", "iam:user:list", 1001L)
        );
        assertThat(preview.rewrittenSql()).contains("iam_user.tenant_id = 1001");
        assertThat(preview.rewrittenSql()).contains("status = 'ENABLED'");
        assertThat(preview.tenantId()).isEqualTo(1001L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                roleAwareService.previewSqlRewrite(new DataPermissionSqlPreviewRequest("DELETE FROM iam_user", "iam:user:list", 1001L))
        ).isInstanceOf(com.saasbasics.platform.common.exception.BizException.class);
    }
}
