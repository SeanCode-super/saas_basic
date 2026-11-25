package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.iam.dto.EmployeeResponse;
import com.saasbasics.platform.modules.iam.entity.EmployeeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmployeeMapper extends BaseMapper<EmployeeEntity> {

    @DataScope(ignore = true)
    List<EmployeeResponse> selectEmployeeList(@Param("spec") DataPermissionSqlSpec spec);

    EmployeeResponse selectEmployeeById(@Param("id") Long id);
}
