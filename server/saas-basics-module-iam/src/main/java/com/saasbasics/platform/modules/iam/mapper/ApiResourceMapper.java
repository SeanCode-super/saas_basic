package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.ApiResourceResponse;
import com.saasbasics.platform.modules.iam.entity.ApiResourceEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApiResourceMapper extends BaseMapper<ApiResourceEntity> {

    List<ApiResourceResponse> selectApiResourceList();

    ApiResourceResponse selectApiResourceById(@Param("id") Long id);
}
