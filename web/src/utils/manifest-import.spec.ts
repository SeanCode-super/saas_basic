import { describe, expect, it } from "vitest";
import { formatJsonManifest, parseJsonManifest } from "./manifest-import";

describe("manifest import", () => {
  it("parses a JSON array", () => {
    expect(parseJsonManifest<{ code: string }>(' [{"code":"tenant"}] ')).toEqual([{ code: "tenant" }]);
  });

  it("rejects empty and non-array input", () => {
    expect(() => parseJsonManifest("  ")).toThrow("导入清单不能为空");
    expect(() => parseJsonManifest('{"code":"tenant"}')).toThrow("导入清单必须是 JSON 数组");
  });

  it("formats a readable manifest", () => {
    expect(formatJsonManifest([{ code: "tenant" }])).toBe('[\n  {\n    "code": "tenant"\n  }\n]');
  });
});
