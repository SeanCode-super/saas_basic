package com.saasbasics.platform.modules.workflow.mapper;

import com.saasbasics.platform.modules.workflow.dto.AssigneeInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowParticipantMapper {

    record InitiatorDepartment(Long id, String name, Long leaderUserId) {}

    InitiatorDepartment findDepartment(@Param("tenantId") long tenantId, @Param("userId") long userId);

    List<AssigneeInfo> findDepartmentLeader(@Param("tenantId") long tenantId, @Param("id") long id);

    List<AssigneeInfo> findRoleMembers(@Param("tenantId") long tenantId, @Param("id") long id);

    List<AssigneeInfo> findUser(@Param("tenantId") long tenantId, @Param("id") long id);
}
