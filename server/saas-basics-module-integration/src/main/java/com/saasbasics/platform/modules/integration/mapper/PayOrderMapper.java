package com.saasbasics.platform.modules.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.integration.entity.PayOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrderEntity> {

    @Select("""
            SELECT * FROM pay_order
            WHERE (order_no = #{orderNoOrTradeNo} OR pay_trade_no = #{orderNoOrTradeNo})
              AND deleted = 0
            ORDER BY id DESC LIMIT 1
            """)
    PayOrderEntity selectByOrderNoOrTradeNo(@Param("orderNoOrTradeNo") String orderNoOrTradeNo);
}
