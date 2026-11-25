// Extract declarations in the browser. File bodies and machine paths stay on the developer's computer.
export interface SourceUnit { path: string; facts: string[]; routes: string[] }
export interface Inventory { units: SourceUnit[]; ignored: number; truncated: number }
const excluded = /(?:^|\/)(?:\.[^/]+|node_modules|vendor|target|build|dist|coverage|logs?|uploads?|data|backups?|generated|__pycache__)(?:\/|$)/i;
const sensitive = /(?:credential|secret|private.?key|cookie|token\.json|password|keystore|payment|alipay|wechatpay)/i;
const apiSpec = /(?:^|\/)(?:openapi|swagger)(?:[.-][^/]*)?\.json$/i;
const supported = /\.(?:java|kt|py|ts|tsx|js|jsx|vue|go|sql|md)$/i;
const safePath = /^\/[A-Za-z0-9_/{}.-]*$/;
const clean = (s: string) => s.slice(0, 100).replace(/\s+/g, " ").trim();
export function inspectSource(path: string, source: string): SourceUnit {
  const facts = new Set<string>(), routes = new Set<string>();
  const put = (prefix: string, value: string) => {
    if (facts.size < 36 && !sensitive.test(value)) facts.add(prefix + clean(value));
  };
  const route = (method: string, value: string) => {
    const url = value.replace(/\/{2,}/g, "/").replace(/\/$/, "") || "/";
    if (safePath.test(url) && !url.includes("..") && !sensitive.test(url) && routes.size < 96) routes.add(method.toUpperCase() + " " + url);
  };
  if (apiSpec.test(path)) {
    try {
      const spec = JSON.parse(source);
      if (spec.openapi || spec.swagger) {
        for (const [url, operations] of Object.entries(spec.paths ?? {})) {
          for (const [method, op] of Object.entries(operations as Record<string,{summary?:string;operationId?:string}>)) {
            if (!/^(get|post|put|patch|delete)$/i.test(method)) continue;
            route(method,url);
            if (op?.operationId && /^[a-zA-Z][a-zA-Z0-9_]{0,60}$/.test(op.operationId)) put("操作 ",op.operationId);
            if (op?.summary && /^[\p{L}\p{N} 、_-]{2,50}$/u.test(op.summary)) put("功能 ",op.summary);
          }
        }
      }
    } catch { /* Incomplete OpenAPI documents contribute no endpoint evidence. */ }
  } else if (/\.md$/i.test(path)) {
    for (const m of source.matchAll(/^#{1,3}\s+([\p{L}\p{N} 、与及（）()_-]{2,68})\s*$/gmu)) put("章节 ", m[1]);
  } else {
    const notes: string[] = [];
    for (const m of source.matchAll(/^\s*(?:\/\*\*|\*|\/\/)\s*([\p{Script=Han}][\p{L}\p{N} ，、：；（）()→_-]{3,75})[。.]?\s*(?:\*\/)?$/gmu)) { if (notes.length < 6) notes.push(m[1]); }
    for (const m of source.matchAll(/\b(\w+(?:Service|Repository|Mapper))\.(\w+)\s*\(/g)) { if (notes.length < 14) notes.push(m[1]+"."+m[2]); }
    source = source.replace(/\/\*[\s\S]*?\*\//g, " ").replace(/^\s*\/\/.*$/gm,"");
    for (const m of source.matchAll(/@(Get|Post|Put|Delete|Patch)Mapping[^\n]*\n(?:\s*@[^\n]*\n)*\s*public\s+([\w<>, ?.]+?)\s+(\w+)\s*\(([^)]{0,320})/g)) {
      put("接口 ", m[1]+" "+m[3]+" → "+m[2]);
    }
    for (const m of source.matchAll(/@RequestBody\s+(?:@Valid\s+)?(\w+)\s+\w+/g)) put("请求体 ",m[1]);
    for (const m of source.matchAll(/private\s+(List<\w+>|Boolean|LocalDateTime)\s+(\w+)\s*;/g)) put("字段 ",m[2]+" "+m[1]);
    for (const m of source.matchAll(/(?:class|interface|record|enum|function|def|struct)\s+(\w+)/g)) put("", m[1]);
    const springPath = (args?:string) => {
      if (!args?.trim()) return "";
      // Multi-value and constant-based mappings need manual review or a resolved OpenAPI document.
      return /(?:^\s*|(?:path|value)\s*=\s*)"([^"]*)"/.exec(args)?.[1];
    };
    const baseAnnotation = /@RequestMapping\s*\(([^)]*)\)/.exec(source.split(/\b(?:class|interface)\s+/)[0]);
    const base = springPath(baseAnnotation?.[1]);
    for (const m of source.matchAll(/@(Get|Post|Put|Delete|Patch)Mapping\b(?:\s*\(([^)]*)\))?/g)) {
      const suffix = springPath(m[2]);
      if (base !== undefined && suffix !== undefined) route(m[1], "/" + base + "/" + suffix);
    }
    for (const m of source.matchAll(/(?:\bapp|\brouter)\.(get|post|put|delete|patch)\(\s*["'](\/[^"']*)["']/gi)) route(m[1], m[2].replace(/:(\w+)/g, "{$1}"));
    for (const m of source.matchAll(/\bprivate\s+(?:final\s+)?(?:String|Integer|Long|Boolean|LocalDateTime|LocalDate|BigDecimal|int|long|boolean)\s+(\w+)\s*[;=]/g)) put("字段 ", m[1]);
    for (const m of source.matchAll(/\b(?:row|result|detail)\.put\(\s*"([A-Za-z]\w*)"/g)) put("输出 ", m[1]);
    for (const m of source.matchAll(/(?:RequirePermission|PreAuthorize|RolesAllowed)\s*\(\s*"([\w:. -]+)"/g)) put("权限 ", m[1]);
    for (const m of source.matchAll(/\b(?:title|label)\s*:\s*['"]([\p{L}\p{N} 、_-]{2,40})['"]/gu)) put("界面 ", m[1]);
    for (const m of source.matchAll(/CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`"]?(\w+)/gi)) put("表 ", m[1]);
    for (const m of source.matchAll(/(?:public|private|protected)\s+(?:static\s+)?[\w<>, ?.[\]]+\s+(\w+)\s*\(/g)) put("方法 ", m[1]);
    for (const note of notes) put("依据 ",note);
  }
  return {path, facts: [...facts], routes: [...routes]};
}
export async function indexDirectory(files: ArrayLike<File>): Promise<Inventory> {
  const eligible = Array.from(files).map(file => ({file, path: (file.webkitRelativePath || file.name).split("/").slice(file.webkitRelativePath ? 1 : 0).join("/")}))
    .filter(({path, file}) => (supported.test(path) || apiSpec.test(path)) && !excluded.test(path) && !sensitive.test(path) && file.size <= 512_000 && path.length <= 350)
    .sort((a,b) => a.path.localeCompare(b.path, "en"));
  const units: SourceUnit[] = [];
  // Scan in bounded batches so a large directory never loads all source bodies into memory.
  for (let i=0; i<Math.min(eligible.length, 5000); i+=24) {
    const batch = await Promise.all(eligible.slice(i, Math.min(i+24,5000)).map(async item => inspectSource(item.path, await item.file.text())));
    for (const unit of batch) if (unit.routes.length || unit.facts.length) units.push(unit);
  }
  const priority = (u:SourceUnit) => u.routes.length ? 0 : /(?:entity|model|router|roles|readme|docs|test)/i.test(u.path) ? 1 : 2;
  units.sort((a,b) => priority(a)-priority(b) || a.path.localeCompare(b.path,"en"));
  let size = 0;
  const kept = units.filter(u => {
    const cost = JSON.stringify(u).length;
    if (size+cost > 105000) return false;
    size += cost;
    return true;
  }).slice(0,500);
  return {units:kept, ignored: files.length-eligible.length, truncated: Math.max(0, eligible.length-5000)+units.length-kept.length};
}
