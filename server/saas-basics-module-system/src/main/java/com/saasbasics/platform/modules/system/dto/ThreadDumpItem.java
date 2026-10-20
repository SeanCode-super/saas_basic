package com.saasbasics.platform.modules.system.dto;

public record ThreadDumpItem(
        long id,
        String name,
        String state,
        boolean daemon,
        long cpuTimeMillis,
        String lockName,
        String lockOwner,
        String stackTrace
) {}
