package com.saasbasics.platform.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.config.MybatisPlusConfig;
import com.saasbasics.platform.modules.workflow.dto.AssigneeInfo;
import com.saasbasics.platform.modules.workflow.mapper.WorkflowParticipantMapper;
import com.saasbasics.platform.security.EnterpriseDataPermissionHandler;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkflowParticipantTest {

    @Test
    void requiresAnEnabledMemberWithAnActiveAssignmentInTheSameTenant() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE iam_user(id BIGINT, tenant_id BIGINT, nickname VARCHAR, status VARCHAR, deleted INT, employee_id BIGINT)");
        db.execute("CREATE TABLE iam_role(id BIGINT, tenant_id BIGINT, status VARCHAR, deleted INT)");
        db.execute("CREATE TABLE iam_user_role(user_id BIGINT, role_id BIGINT, tenant_id BIGINT, deleted INT, expire_at TIMESTAMP)");
        db.execute("CREATE TABLE iam_employee(id BIGINT, tenant_id BIGINT, dept_id BIGINT, deleted INT)");
        db.execute("CREATE TABLE iam_department(id BIGINT, tenant_id BIGINT, dept_name VARCHAR, leader_user_id BIGINT, deleted INT)");
        db.update("INSERT INTO iam_user VALUES (1, 7, 'Lead', 'ENABLED', 0, NULL), (2, 7, 'Former', 'ENABLED', 0, 10), "
                + "(3, 7, 'Disabled', 'DISABLED', 0, NULL), (4, 8, 'Other', 'ENABLED', 0, NULL)");
        db.update("INSERT INTO iam_role VALUES (5, 7, 'ENABLED', 0)");
        db.update("INSERT INTO iam_user_role(user_id, role_id, tenant_id, deleted) VALUES (1, 5, 7, 0), (2, 5, 7, 1), (3, 5, 7, 0), (4, 5, 7, 0)");
        db.update("INSERT INTO iam_department VALUES (90, 7, 'Ops', 1, 0)");
        db.update("INSERT INTO iam_employee VALUES (10, 7, 90, 0)");
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(new ClassPathResource("mapper/WorkflowParticipantMapper.xml"));
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor(new EnterpriseDataPermissionHandler(null)));
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(WorkflowParticipantMapper.class);
        var engine = new WorkflowEngine(new ObjectMapper(), mapper);
        try (var scope = TenantAccessContextHolder.openTenant(7L, "example", 2L)) {
            assertThat(mapper.findDepartment(7L, 2L)).isEqualTo(
                    new WorkflowParticipantMapper.InitiatorDepartment(90L, "Ops", 1L));
            assertThat(mapper.findDepartmentLeader(7L, 90L)).containsExactly(new AssigneeInfo(1L, "Lead"));
            assertThat(mapper.findRoleMembers(7L, 5L)).containsExactly(new AssigneeInfo(1L, "Lead"));
            assertThat(mapper.findUser(7L, 4L)).isEmpty();
            String node = """
                    {"next":{"type":"APPROVER","assigneeType":"USER","assigneeValue":"3"}}
                    """;
            assertThatThrownBy(() -> engine.advanceFlow(node, "{}", "root", 7L, 2L, 90L))
                    .isInstanceOf(BizException.class).extracting("code").isEqualTo("WORKFLOW_ASSIGNEE_MISSING");
            assertThatThrownBy(() -> engine.advanceFlow("{", "{}", "root", 7L, 2L, 90L))
                    .isInstanceOf(BizException.class).extracting("code").isEqualTo("INVALID_WORKFLOW_JSON");
        }
    }
}
