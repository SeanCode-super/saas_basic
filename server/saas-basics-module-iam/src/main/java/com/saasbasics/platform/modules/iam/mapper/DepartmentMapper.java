package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.modules.iam.dto.DepartmentResponse;
import com.saasbasics.platform.modules.iam.entity.DepartmentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentEntity> {

    List<DepartmentResponse> selectDepartmentList(@Param("spec") DataPermissionSqlSpec spec);

    DepartmentResponse selectDepartmentById(@Param("id") Long id);
}
