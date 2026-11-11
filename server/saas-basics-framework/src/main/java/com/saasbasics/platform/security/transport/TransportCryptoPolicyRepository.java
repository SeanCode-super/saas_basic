package com.saasbasics.platform.security.transport;

/**
 * SPI 接口：由业务系统模块提供持久化实现（避免 framework 反向依赖 module-system）。
 */
public interface TransportCryptoPolicyRepository {

    TransportCryptoPolicySnapshot loadGlobalPolicy();

    TransportCryptoPolicySnapshot saveGlobalPolicy(TransportCryptoPolicySnapshot policy);
}
