package com.saasbasics.platform.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("db")
@MapperScan(value = "com.saasbasics.platform.modules", markerInterface = BaseMapper.class)
public class DbMapperScanConfig {
}
