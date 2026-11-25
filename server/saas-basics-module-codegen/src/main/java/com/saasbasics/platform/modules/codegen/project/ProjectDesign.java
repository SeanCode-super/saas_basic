package com.saasbasics.platform.modules.codegen.project;

import com.saasbasics.platform.common.exception.BizException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reviewed mappings between a management page and the original project's business API. */
public record ProjectDesign(String code, String name, String description, int revision, List<String> roles,
        List<String> workflow, List<String> decisions, List<Page> pages) {
    public record Unit(String path, List<String> facts, List<String> routes) {}
    public record Sources(List<Unit> units, String model, ProjectDesign previous) {
        public Sources(List<Unit> units, String model) { this(units, model, null); }
    }
    public record Review(Sources sources, ProjectDesign design) {}
    public record Column(String key, String label, String type, String source) {}
    public record Input(String key, String label, String type, boolean required, String target, String location) {}
    public record Action(String key, String label, String method, String path, String evidence, List<Input> fields, String positiveCountAt) {}
    public record Page(String key, String label, String evidence, String path, String rowsAt,
                       List<Column> columns, List<Action> actions) {}

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]{0,25}");
    private static final Pattern PROPERTY = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{0,63}");
    private static final Pattern ROUTE = Pattern.compile("/(?:[a-zA-Z0-9_-]+|\\{[a-zA-Z_][a-zA-Z0-9_]*})(?:/(?:[a-zA-Z0-9_-]+|\\{[a-zA-Z_][a-zA-Z0-9_]*}))*");
    private static final Set<String> TYPES = Set.of("text","date","number");

    public static Map<String,Unit> inventory(Sources sources) {
        require(sources != null && sources.units() != null && !sources.units().isEmpty()
                && sources.units().size() <= 500 && (sources.model() == null || sources.model().length() <= 128), "请选择项目目录");
        Map<String,Unit> files = new HashMap<>();
        int size = 0;
        for (var u : sources.units()) {
            require(u != null && text(u.path(),350) && !u.path().startsWith("/") && !u.path().contains("\\")
                    && !List.of(u.path().split("/")).contains("..") && files.putIfAbsent(u.path(),u) == null, "源码路径无效或重复");
            require(strings(u.facts(),36,130) && strings(u.routes(),96,260), "项目结构索引无效");
            size += u.path().length() + u.facts().stream().mapToInt(String::length).sum()
                    + u.routes().stream().mapToInt(String::length).sum();
        }
        require(size <= 110000, "索引过大，请选择业务项目子目录");
        return files;
    }

    public void verify(Map<String,Unit> files) {
        require(key(code) && text(name,80) && text(description,500) && revision >= 1, "请核对应用编码、名称、说明和修订号");
        require(strings(roles,32,160) && strings(workflow,64,300) && strings(decisions,64,300), "角色、流程或待确认事项格式错误");
        require(pages != null && !pages.isEmpty() && pages.size() <= 32, "应用需要 1 至 32 个管理页面");
        var permissions = new HashSet<>(Set.of("query"));
        var pageKeys = new HashSet<String>();
        for (var p : pages) {
            require(p != null && key(p.key()) && pageKeys.add(p.key()) && text(p.label(),80), "页面编码重复或名称无效");
            checkRoute(files, p.evidence(), "GET", p.path());
            require(!p.path().contains("{") && propertyPath(p.rowsAt(), true), "列表接口须能直接查询，结果路径可留空");
            require(p.columns() != null && !p.columns().isEmpty() && p.columns().size() <= 32, "请为每个页面配置显示列");
            var columns = new HashSet<String>();
            for (var c : p.columns()) {
                require(c != null && key(c.key()) && columns.add(c.key()) && text(c.label(),80)
                        && TYPES.contains(c.type() == null ? "" : c.type()) && propertyPath(c.source(),false), "字段映射无效");
            }
            require(permissions.add(p.key()+"_list") && p.actions() != null && p.actions().size() <= 16, "页面权限或操作数量无效");
            for (var a : p.actions()) {
                require(a != null && key(a.key()) && permissions.add(a.key()) && text(a.label(),80)
                        && Set.of("POST","PUT","PATCH","DELETE").contains(a.method() == null ? "" : a.method()), "操作编码或方法无效");
                require(a.positiveCountAt() == null || propertyPath(a.positiveCountAt(),false), "业务回执计数路径无效");
                checkRoute(files, a.evidence(), a.method(), a.path());
                require(a.fields() != null && a.fields().size() <= 32, "操作字段无效");
                var keys = new HashSet<String>();
                var targets = new HashSet<String>();
                var route = a.path();
                for (var f : a.fields()) {
                    require(f != null && key(f.key()) && keys.add(f.key()) && text(f.label(),80)
                            && (TYPES.contains(f.type() == null ? "" : f.type()) || ("number_list".equals(f.type()) && "body".equals(f.location()))) && property(f.target())
                            && Set.of("body","path","query").contains(f.location() == null ? "" : f.location())
                            && targets.add(f.location()+"."+f.target()), "操作参数映射无效");
                    if ("path".equals(f.location())) {
                        String token = "{"+f.target()+"}";
                        require(f.required() && route.contains(token),"路径参数没有对应必填字段");
                        route = route.replace(token,"");
                    }
                }
                require(!route.contains("{"), "请填写全部路径参数");
            }
        }
        require(permissions.size() <= 128,"应用操作超过上限");
    }

    private static void checkRoute(Map<String,Unit> files, String file, String method, String path) {
        var unit = files.get(file);
        require(unit != null && path != null && path.length() <= 240 && ROUTE.matcher(path).matches()
                && unit.routes().contains(method+" "+path), "接口与所选项目的源码证据不一致：" + method + " " + path);
    }
    private static boolean propertyPath(String path, boolean empty) {
        return path != null && ((empty && path.isEmpty()) || (path.length() <= 200
                && java.util.Arrays.stream(path.split("\\.",-1)).allMatch(ProjectDesign::property)));
    }
    private static boolean property(String value) {
        return value != null && PROPERTY.matcher(value).matches() && !Set.of("__proto__","prototype","constructor").contains(value);
    }
    private static boolean key(String value) { return property(value) && KEY.matcher(value).matches(); }
    private static boolean text(String value,int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private static boolean strings(List<String> items,int max,int length) {
        return items != null && items.size() <= max && items.stream().allMatch(v -> text(v,length));
    }
    static void require(boolean test, String error) { if (!test) throw new BizException("INVALID_PARAM",error); }
}
