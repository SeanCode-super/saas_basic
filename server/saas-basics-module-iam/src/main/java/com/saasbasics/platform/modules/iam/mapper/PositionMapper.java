package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.iam.dto.PositionResponse;
import com.saasbasics.platform.modules.iam.entity.PositionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PositionMapper extends BaseMapper<PositionEntity> {

    @DataScope(ignore = true)
    List<PositionResponse> selectPositionList(@Param("spec") DataPermissionSqlSpec spec);

    PositionResponse selectPositionById(@Param("id") Long id);
}
