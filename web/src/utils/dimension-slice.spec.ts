import { describe, expect, it } from "vitest";
import {
  computeDimensionFacets,
  filterRowsByDimensions,
  type DimensionAxisConfig
} from "./dimension-slice";
import { resolveRouteTagTitle } from "./tag-title";

describe("dimension-slice engine", () => {
  const rows = [
    { id: 1, warehouse: "WH-SH", category: "IT", status: "OK" },
    { id: 2, warehouse: "WH-SH", category: "Power", status: "LOW" },
    { id: 3, warehouse: "WH-SZ", category: "IT", status: "LOW" },
    { id: 4, warehouse: "WH-BJ", category: "Power", status: "OK" }
  ];

  const axes: DimensionAxisConfig[] = [
    { key: "warehouse", label: "仓库" },
    {
      key: "status",
      label: "健康度",
      options: [
        { label: "充足", value: "OK" },
        { label: "预警", value: "LOW" }
      ]
    }
  ];

  it("filters rows using intersection across active axes", () => {
    const filtered = filterRowsByDimensions(rows, axes, {
      warehouse: "WH-SH",
      status: "LOW"
    });
    expect(filtered.map((item) => item.id)).toEqual([2]);
  });

  it("computes cross-faceted counts excluding current axis filter", () => {
    const facets = computeDimensionFacets(rows, axes, { warehouse: "WH-SH" });
    const warehouseFacet = facets.find((f) => f.key === "warehouse");
    const statusFacet = facets.find((f) => f.key === "status");

    expect(warehouseFacet?.totalCount).toBe(4);
    expect(warehouseFacet?.options.find((o) => o.value === "WH-SH")?.count).toBe(2);
    expect(statusFacet?.totalCount).toBe(2);
    expect(statusFacet?.options.find((o) => o.value === "LOW")?.count).toBe(1);
    expect(statusFacet?.options.find((o) => o.value === "OK")?.count).toBe(1);
  });
});

describe("resolveRouteTagTitle", () => {
  it("resolves home route and disambiguates multi-section routes", () => {
    expect(resolveRouteTagTitle({ path: "/dashboard" })).toBe("控制台首页");
    expect(resolveRouteTagTitle({ path: "/tenant/package" })).toBe("租户 · 套餐配额");
    expect(resolveRouteTagTitle({ path: "/iam/data-scope" })).toBe("IAM · 数据权限");
    expect(resolveRouteTagTitle({ path: "/erp", query: { tab: "purchase" } })).toBe("ERP · 采购履约管理");
  });
});
