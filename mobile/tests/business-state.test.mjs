import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { runInNewContext } from "node:vm";
import { transformSync } from "esbuild";
import { parse } from "@vue/compiler-sfc";

const require = createRequire(import.meta.url);
function loadTs(source, dependencies, globals = {}) {
  const { code } = transformSync(source, { loader: "ts", format: "cjs" });
  const exports = {};
  const module = { exports };
  runInNewContext(code, { exports, module, require: name => dependencies[name] ?? require(name), ...globals });
  return module.exports;
}
function storage() {
  const data = new Map();
  return {
    getStorageSync: key => data.get(key),
    setStorageSync: (key, value) => data.set(key, value),
    removeStorageSync: key => data.delete(key),
    getStorageInfoSync: () => ({ keys: [...data.keys()] })
  };
}
const read = file => readFileSync(new URL(file, import.meta.url), "utf8");
const cacheSource = read("../src/utils/warm-cache.ts");

test("business cache follows both tenant and user, rejects legacy data and clears only app session data", () => {
  const uni = storage();
  const cache = loadTs(cacheSource, {}, { uni });
  const signIn = (tenantId, userId) => {
    uni.setStorageSync("sb_current_user", JSON.stringify({ tenantId, userId }));
    uni.setStorageSync("sb_access_token", "test-session");
  };
  uni.setStorageSync("TIANSHU_WARM_INVENTORY", "[99]");
  uni.setStorageSync("theme", "light");
  signIn(1, 10);
  assert.equal(cache.getCachedData("INVENTORY", []).length, 0);
  cache.setCachedData("INVENTORY", [7]);
  signIn(1, 11);
  assert.equal(cache.getCachedData("INVENTORY", []).length, 0);
  signIn(2, 10);
  assert.equal(cache.getCachedData("INVENTORY", []).length, 0);
  signIn(1, 10);
  assert.equal(cache.getCachedData("INVENTORY", [])[0], 7);
  cache.clearMobileSession();
  assert.deepEqual(uni.getStorageInfoSync().keys, ["theme"]);
  cache.setCachedData("INVENTORY", [9]);
  assert.equal(cache.getCachedData("INVENTORY", []).length, 0);
});

test("inventory accepts an empty result, preserves rejected edits, and commits a confirmed adjustment once", async () => {
  const { descriptor } = parse(read("../src/pages/erp/inventory.vue"));
  const toasts = [];
  const calls = [];
  let fail = true;
  let rows = [];
  const vue = { ref: value => ({ value }), reactive: value => value, computed: fn => ({ get value() { return fn(); } }), onMounted() {} };
  const api = {
    apiGetInventory: async () => rows,
    apiAdjustStock: async data => {
      calls.push(data);
      if (fail) throw Error("拒绝");
    }
  };
  const page = loadTs(descriptor.scriptSetup.content +
    "\nexport { loadData, inventoryList, openDetailSheet, submitStockAdjustment, detailVisible, adjustForm };", {
    vue, "@dcloudio/uni-app": { onPullDownRefresh() {} },
    "../../api/erp": api,
    "../../utils/warm-cache": { getCachedData: () => [{ stockQuantity: 10 }], setCachedData() {} }
  }, { uni: { stopPullDownRefresh() {}, showToast: t => toasts.push(t) } });
  await page.loadData();
  assert.equal(page.inventoryList.value.length, 0);
  const item = { id: 12, productId: 31, warehouseId: 8, stockQuantity: 10 };
  rows = [item];
  await page.loadData();
  page.openDetailSheet(item);
  page.adjustForm.delta = "2";
  await page.submitStockAdjustment();
  assert.equal(item.stockQuantity, 10);
  assert.equal(page.detailVisible.value, true);
  assert.equal(toasts.some(t => t.icon === "success"), false);
  fail = false;
  rows = [{ ...item, stockQuantity: 12 }];
  await Promise.all([page.submitStockAdjustment(), page.submitStockAdjustment()]);
  assert.equal(calls.length, 2);
  assert.equal(calls[1].productId, 31);
  assert.equal(calls[1].warehouseId, 8);
  assert.equal(page.inventoryList.value[0].stockQuantity, 12);
  assert.equal(page.detailVisible.value, false);
});

test("a late response from a previous session cannot populate the next account", async () => {
  const uni = storage();
  const cache = loadTs(cacheSource, {}, { uni });
  const source = read("../src/utils/request.ts").replace(/\/\/ #ifndef H5[\s\S]*?\/\/ #endif/, "");
  let pending;
  const sent = new Promise(resolve => {
    uni.request = options => {
      if (options.url.includes("/transport/policy")) {
        options.success({ statusCode: 200, data: {} });
      } else {
        pending = options;
        resolve();
      }
    };
  });
  const http = loadTs(source, {
    "./warm-cache": cache,
    "./transport-crypto": { normalizeApiPathname: (_, p) => p, shouldEncryptRequest: () => false }
  }, { uni });
  const first = http.request({ url: "/api/erp/inventory" });
  await sent;
  cache.clearMobileSession();
  uni.setStorageSync("sb_current_user", JSON.stringify({ tenantId: 2, userId: 8 }));
  uni.setStorageSync("sb_access_token", "new-session");
  pending.success({ statusCode: 200, data: { success: true, data: [7] } });
  await assert.rejects(first, /SESSION_CHANGED/);
  assert.equal(cache.getCachedData("INVENTORY", []).length, 0);
});
