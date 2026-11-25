package com.saasbasics.platform.modules.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.codegen.dto.CodegenProjectResponse;
import com.saasbasics.platform.modules.codegen.entity.CodegenProjectEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodegenProjectMapper extends BaseMapper<CodegenProjectEntity> {

    List<CodegenProjectResponse> selectProjectList();

    CodegenProjectResponse selectProjectById(@Param("id") Long id);
}
