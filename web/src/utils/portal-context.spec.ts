import { describe, expect, it } from "vitest";
import { buildPortalLoginQuery, buildPortalLoginUrl, resolvePortalContext } from "./portal-context";

describe("portal context", () => {
  it("normalizes explicit portal query values", () => {
    expect(resolvePortalContext({ clientId: " client-1 ", terminalCode: ["web"] })).toEqual({
      clientId: "client-1",
      terminalCode: "web"
    });
  });

  it("preserves portal context in login redirects", () => {
    expect(buildPortalLoginQuery("/dashboard?tab=overview", { clientId: "client-1", terminalCode: "web" })).toEqual({
      clientId: "client-1",
      terminalCode: "web",
      redirect: "/dashboard?tab=overview"
    });
    expect(buildPortalLoginUrl("/dashboard", { clientId: "client-1", terminalCode: "web" })).toBe(
      "/login?clientId=client-1&terminalCode=web&redirect=%2Fdashboard"
    );
  });

  it("allows a clean login URL when no portal context is known", () => {
    expect(buildPortalLoginUrl()).toBe("/login");
  });
});
