package com.saasbasics.platform.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("db")
@MapperScan(basePackages = {"com.saasbasics.platform.modules", "com.saasbasics.platform.outbox.mapper"},
        markerInterface = BaseMapper.class, annotationClass = Mapper.class)
public class DbMapperScanConfig {
}
