package com.saasbasics.platform.common.tenant;

/** An application contributed by a trusted Spring Boot module on the host classpath. */
public interface TenantApplication {
    record Endpoint(String action, String name, String method, String path) {}

    record WorkspaceField(String key, String label, String type, boolean required) {}

    /** Fields supported by the installed revision. Older extensions keep their custom UI. */
    default java.util.List<WorkspaceField> workspaceFields(int revision) { return java.util.List.of(); }

    java.util.List<Endpoint> endpoints();
    String code();
    String name();
    String description();
    String componentPath();
    int latestRevision();

    /** Minimum host platform SemVer (inclusive), or null if unconstrained. */
    default String minHostVersion() { return null; }

    /** Maximum host platform SemVer (inclusive), or null if unconstrained. */
    default String maxHostVersion() { return null; }

    /** Required extension dependencies mapped as appCode -> minimum installed revision. */
    default java.util.Map<String, Integer> dependencies() { return java.util.Map.of(); }

    /**
     * Apply one consecutive revision in the install transaction. Only use tenant-scoped DML
     * here. Shared schema migrations belong to the application's deployment.
     */
    void migrate(long tenantId, int revision);
}
