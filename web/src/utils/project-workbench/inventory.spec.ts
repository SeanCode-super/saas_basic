import { describe, expect, it } from "vitest";
import { inspectSource, indexDirectory } from "./inventory";

describe("项目结构索引",()=>{
  it("keeps source evidence for Spring routes and does not send literals or commented routes",()=>{
    const unit=inspectSource("src/TaskController.java",`
      @RequestMapping("/api/tasks")
      class TaskController {
        // @GetMapping("/obsolete")
        @GetMapping public List<Task> list() { return service.list(); }
        @PostMapping("/{id}/retry") public void retry(Long id) {}
        private String password = "secret-value";
      }`);
    expect(unit.routes).toEqual(["GET /api/tasks","POST /api/tasks/{id}/retry"]);
    expect(JSON.stringify(unit)).not.toContain("secret-value");
  });
  it("indexes JSON OpenAPI documents and excludes private and vendor files",async()=>{
    function file(path:string,body:string) {
      return {webkitRelativePath:"demo/"+path,name:path,size:body.length,text:async()=>body} as File;
    }
    const result=await indexDirectory([file("openapi.json",JSON.stringify({openapi:"3.1.0",paths:{"/api/items":{get:{operationId:"listItems"}}}})),
      file(".env","private"),file("node_modules/x/router.ts","app.get('/leak')")]);
    expect(result.ignored).toBe(2);
    expect(result.units).toHaveLength(1);
    expect(result.units[0].routes).toEqual(["GET /api/items"]);
  });
});
