package com.saasbasics.platform.modules.system.trash;

import java.util.List;
import java.util.Set;

/** SQL identifiers originate from the tenant resource registry and the database column catalog. */
public final class TrashTarget {
    record CascadeRule(String childTable, String foreignKeyColumn) {}
    record TableMeta(String tableName, String appCode, String appName, String moduleName, String titleColumn,
                     List<CascadeRule> cascadeRules) {}

    final TableMeta meta;
    final Set<String> columns;
    private final String title;
    private final String key;

    TrashTarget(TableMeta meta, Set<String> columns, String key) {
        this.meta = meta;
        this.columns = Set.copyOf(columns);
        this.key = key;
        if (!validName(meta.tableName()) || !validName(key) || !columns.contains(key)) {
            throw new IllegalArgumentException("Unregistered trash table or key");
        }
        this.title = columns.contains(meta.titleColumn()) ? meta.titleColumn()
                : List.of("name", "title", "code", "remark", "id").stream().filter(columns::contains).findFirst().orElseThrow();
        if (!validName(title)) throw new IllegalArgumentException("Invalid trash title column");
    }

    public String getTable() { return meta.tableName(); }
    public String getKey() { return key; }
    public String getTitle() { return title; }
    public boolean isDated() { return columns.contains("deleted_at"); }
    public boolean isUpdated() { return columns.contains("updated_at"); }
    public boolean isAttributed() { return columns.contains("updated_by"); }

    static boolean validName(String name) {
        return name != null && name.matches("[a-z][a-z0-9_]{0,63}");
    }
}
