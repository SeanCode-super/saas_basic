package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.DataPermissionRuleResponse;
import com.saasbasics.platform.modules.iam.entity.DataPermissionRuleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DataPermissionRuleMapper extends BaseMapper<DataPermissionRuleEntity> {

    List<DataPermissionRuleResponse> selectRuleList();

    DataPermissionRuleResponse selectRuleById(@Param("id") Long id);
}
