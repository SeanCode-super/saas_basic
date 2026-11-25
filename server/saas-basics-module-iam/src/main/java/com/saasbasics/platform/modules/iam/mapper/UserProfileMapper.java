package com.saasbasics.platform.modules.iam.mapper;

import com.saasbasics.platform.modules.iam.dto.UserProfileResponse;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserProfileMapper {
    record EmployeeDetails(String employeeNo, String employeeName, Long deptId, String deptName, String deptFullName,
                           Long positionId, String positionName, LocalDate hireDate, String employeeStatus) {}

    String tenantName(@Param("tenantId") Long tenantId);
    List<String> roleNames(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    List<UserProfileResponse.DataScopeSummaryItem> dataScopes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    List<UserProfileResponse.IdentityBindingItem> identities(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    EmployeeDetails employee(@Param("tenantId") Long tenantId, @Param("employeeId") Long employeeId);
    List<UserProfileResponse.PersonalLoginLogItem> recentLogs(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
