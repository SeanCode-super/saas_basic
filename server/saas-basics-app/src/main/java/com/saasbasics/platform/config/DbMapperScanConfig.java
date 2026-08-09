package com.saasbasics.platform.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("db")
@MapperScan("com.saasbasics.platform.modules")
public class DbMapperScanConfig {
}
