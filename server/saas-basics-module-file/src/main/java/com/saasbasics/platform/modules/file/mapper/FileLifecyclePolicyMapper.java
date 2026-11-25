package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileLifecyclePolicyResponse;
import com.saasbasics.platform.modules.file.entity.FileLifecyclePolicyEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileLifecyclePolicyMapper extends BaseMapper<FileLifecyclePolicyEntity> {

    List<FileLifecyclePolicyResponse> selectPolicyList();

    FileLifecyclePolicyResponse selectPolicyById(@Param("id") Long id);
}
