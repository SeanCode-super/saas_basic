package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.DataScopeResponse;
import com.saasbasics.platform.modules.iam.entity.DataScopeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DataScopeMapper extends BaseMapper<DataScopeEntity> {

    List<DataScopeResponse> selectDataScopeList();

    DataScopeResponse selectDataScopeById(@Param("id") Long id);
}
