package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.entity.IndustryServiceEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IndustryServiceMapper extends BaseMapper<IndustryServiceEntity> {

    List<IndustryServiceEntity> selectLatestCatalog();

    List<IndustryServiceEntity> selectInstalledEntries(@Param("tenantId") Long tenantId);

    IndustryServiceEntity selectInstalledApp(@Param("tenantId") Long tenantId, @Param("appCode") String appCode);
}
