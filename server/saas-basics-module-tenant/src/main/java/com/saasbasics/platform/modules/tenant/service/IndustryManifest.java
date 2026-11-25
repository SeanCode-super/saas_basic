package com.saasbasics.platform.modules.tenant.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantApplication;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable contract for one independently published application revision. */
public record IndustryManifest(String code, String name, String description, int revision, String kind,
        boolean pc, boolean mobile, String pcUrl, String mobileUrl, List<Field> columns, List<Action> actions, List<Page> pages)
        implements TenantApplication {
    public record Page(String key, String label, List<Field> columns, String query, List<String> actions) {}

    public IndustryManifest {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public IndustryManifest(String code, String name, String description, int revision, String kind,
            boolean pc, boolean mobile, String pcUrl, String mobileUrl, List<Field> columns, List<Action> actions) {
        this(code, name, description, revision, kind, pc, mobile, pcUrl, mobileUrl, columns, actions, List.of());
    }

    public Page owner(String action) {
        return pages.stream().filter(p -> p.query().equals(action) || p.actions().contains(action)).findFirst().orElse(null);
    }

    public record Field(String key, String label, String type, boolean required) {}
    public record Action(String key, String label, List<Field> fields, String rowKey) {}

    @Override public int latestRevision() { return revision; }
    @Override public String componentPath() { return "@/views/industry/IndustryApplication.vue"; }
    @Override public void migrate(long tenantId, int version) { /* Service migrations run on its own database. */ }
    @Override public List<Endpoint> endpoints() {
        return actions.stream().map(a -> new Endpoint(a.key(), a.label(), "POST",
                "/api/industry/" + code + "/actions/" + a.key())).toList();
    }

    public void validate() {
        if (!validKey(code) || !label(name) || description == null || description.length() > 500
                || revision < 1 || (!pc && !mobile) || !Set.of("NATIVE", "EMBED").contains(kind == null ? "" : kind)) throw invalid();
        if ("EMBED".equals(kind)) {
            if (pc) https(pcUrl);
            if (mobile) https(mobileUrl);
        } else if (pcUrl != null || mobileUrl != null) throw invalid();
        fields(columns);
        if (actions == null || actions.isEmpty() || actions.size() > (pages.isEmpty() ? 24 : 128)) throw invalid();
        Set<String> keys = new HashSet<>();
        if (!pages.isEmpty()) validatePages();
        for (Action a : actions) {
            if (a == null || !validKey(a.key()) || !label(a.label()) || !keys.add(a.key())) throw invalid();
            fields(a.fields());
            if (a.rowKey() != null && ((pages.isEmpty() ? columns.stream() : pages.stream().flatMap(p -> p.columns().stream())).noneMatch(f -> f.key().equals(a.rowKey()))
                    || a.fields().stream().noneMatch(f -> f.required() && f.key().equals(a.rowKey())))) throw invalid();
            if ("query".equals(a.key()) && !a.fields().isEmpty()) throw invalid();
        }
        if (!keys.contains("query") || ("EMBED".equals(kind) && (keys.size() != 1 || !columns.isEmpty()))) throw invalid();
    }

    private void validatePages() {
        if (!"NATIVE".equals(kind) || !columns.isEmpty() || pages.size() > 32) throw invalid();
        var pageKeys = new HashSet<String>();
        var routed = new HashSet<String>();
        routed.add("query");
        for (Page page : pages) {
            if (page == null || !validKey(page.key()) || !pageKeys.add(page.key()) || !label(page.label())
                    || !validKey(page.query()) || !routed.add(page.query()) || page.actions() == null) throw invalid();
            fields(page.columns());
            for (String action : page.actions()) {
                if (!validKey(action) || !routed.add(action)) throw invalid();
            }
        }
        for (Action action : actions) {
            if (action == null || !routed.remove(action.key())) throw invalid();
            Page page = owner(action.key());
            if (page != null && page.query().equals(action.key()) && (action.fields() == null || !action.fields().isEmpty())) throw invalid();
            if (action.rowKey() != null && (page == null || page.columns().stream().noneMatch(f -> f.key().equals(action.rowKey())))) throw invalid();
        }
        if (!routed.isEmpty()) throw invalid();
    }

    private static void fields(List<Field> fields) {
        if (fields == null || fields.size() > 32) throw invalid();
        Set<String> keys = new HashSet<>();
        for (Field f : fields) {
            if (f == null || !validKey(f.key()) || !label(f.label()) || !keys.add(f.key()) || f.type() == null
                    || !Set.of("text","number","date").contains(f.type())) throw invalid();
        }
    }

    private static void https(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw invalid();
        } catch (IllegalArgumentException | NullPointerException ex) { throw invalid(); }
    }

    private static boolean label(String v) { return v != null && !v.isBlank() && v.length() <= 100; }
    private static boolean validKey(String v) {
        return v != null && v.matches("[a-z][a-z0-9_]{0,39}") && !Set.of("constructor","prototype").contains(v);
    }
    private static BizException invalid() { return new BizException("INVALID_PARAM","请检查应用清单的编码、终端、字段和操作配置"); }
}
