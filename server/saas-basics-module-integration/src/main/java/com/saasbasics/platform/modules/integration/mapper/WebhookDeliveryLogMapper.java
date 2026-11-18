package com.saasbasics.platform.modules.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.integration.entity.WebhookDeliveryLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookDeliveryLogMapper extends BaseMapper<WebhookDeliveryLogEntity> {
}
