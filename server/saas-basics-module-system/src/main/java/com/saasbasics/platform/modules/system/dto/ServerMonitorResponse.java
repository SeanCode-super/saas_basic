package com.saasbasics.platform.modules.system.dto;

import java.util.List;

public record ServerMonitorResponse(
        CpuInfo cpu,
        MemoryInfo memory,
        JvmInfo jvm,
        DbPoolInfo dbPool,
        List<DiskInfo> disks,
        ServerSysInfo sys,
        RedisSummary redis,
        SlowSqlSummary slowSql,
        AppStatusInfo appStatus
) {
    public record CpuInfo(
            int cpuNum,
            double totalUsage,
            double sysUsage,
            double userUsage,
            double idleRate,
            double loadAverage,
            String cpuModel
    ) {}

    public record MemoryInfo(
            long totalBytes,
            long usedBytes,
            long freeBytes,
            double usageRate
    ) {}

    public record JvmInfo(
            String name,
            String version,
            String vendor,
            String startTime,
            long uptimeSeconds,
            long heapMaxBytes,
            long heapCommittedBytes,
            long heapUsedBytes,
            double heapUsageRate,
            long nonHeapUsedBytes,
            long nonHeapMaxBytes,
            int threadCount,
            int peakThreadCount,
            int daemonThreadCount,
            boolean hasDeadlock,
            List<String> deadlockedThreads,
            List<GcMetric> gcMetrics,
            List<String> jvmArguments
    ) {
        public record GcMetric(String name, long count, long timeMillis) {}
    }

    public record DbPoolInfo(
            String poolName,
            int activeConnections,
            int idleConnections,
            int totalConnections,
            int threadsAwaitingConnection,
            int maxPoolSize,
            double poolUsageRate
    ) {}

    public record DiskInfo(
            String dirName,
            String sysTypeName,
            String typeName,
            long totalBytes,
            long freeBytes,
            long usedBytes,
            double usageRate
    ) {}

    public record ServerSysInfo(
            String computerName,
            String serverIp,
            String osName,
            String osArch,
            String userDir
    ) {}

    public record RedisSummary(
            boolean available,
            String version,
            int connectedClients,
            String usedMemory,
            int keysCount
    ) {}

    public record SlowSqlSummary(
            long thresholdMillis,
            int totalSlowQueries
    ) {}

    public record AppStatusInfo(
            String deploymentMode,
            String monolithStatus,
            WorkflowEngineStatus workflow,
            List<ModuleHealthItem> modules
    ) {
        public record WorkflowEngineStatus(
                String status,
                String mode,
                String engineVersion,
                String transactionType,
                long definitionCount,
                long instanceCount,
                long pendingTaskCount,
                long totalTaskCount
        ) {}

        public record ModuleHealthItem(
                String moduleName,
                String moduleCode,
                String status,
                String mode,
                String description
        ) {}
    }
}
