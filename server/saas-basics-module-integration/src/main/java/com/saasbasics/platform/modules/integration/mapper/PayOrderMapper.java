package com.saasbasics.platform.modules.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.integration.entity.PayOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrderEntity> {

    PayOrderEntity selectByOrderNoOrTradeNo(@Param("orderNoOrTradeNo") String orderNoOrTradeNo);
}
