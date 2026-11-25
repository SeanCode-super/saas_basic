package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.MenuResponse;
import com.saasbasics.platform.modules.iam.entity.MenuEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MenuMapper extends BaseMapper<MenuEntity> {

    List<MenuResponse> selectMenuList();

    MenuResponse selectMenuById(@Param("id") Long id);
}
