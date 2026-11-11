import { describe, expect, it } from "vitest";
import { messages } from "./messages";

describe("i18n message catalog parity", () => {
  it("maintains symmetric translation keys between zh-CN and en-US", () => {
    const zhKeys = Object.keys(messages["zh-CN"]).sort();
    const enKeys = Object.keys(messages["en-US"]).sort();
    expect(zhKeys).toEqual(enKeys);
    expect(zhKeys.length).toBeGreaterThanOrEqual(25);
  });
});
