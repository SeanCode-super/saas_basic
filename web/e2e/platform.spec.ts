import { expect, test, type Page } from "@playwright/test";

async function loginThroughUi(page: Page, baseUrl: string, username: string, password: string) {
  const pageErrors: string[] = [];
  const serverErrors: string[] = [];
  page.on("pageerror", (err) => pageErrors.push(err.message));
  page.on("response", (res) => {
    if (res.status() >= 500) {
      serverErrors.push(`${res.status()} ${res.url()}`);
    }
  });

  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  const submitBtn = page.locator("button.cyber-submit-btn");
  await expect(submitBtn).toBeVisible();

  // Fill credentials explicitly in case portal defaults differ
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);

  // Wait for SmartShieldCaptcha auto-verification when enabled
  const shieldHint = page.locator(".smart-shield-card, .shield-status-text");
  if ((await shieldHint.count()) > 0) {
    await expect(page.locator(".shield-verified, .smart-shield-card.is-verified").first()).toBeVisible({
      timeout: 15_000
    }).catch(() => {
      // Fallback: proceed to click submit which handles pending verification
    });
  }

  await submitBtn.click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20_000 });
  return { pageErrors, serverErrors };
}

test.describe("Tianshu SaaS Platform Web E2E", () => {
  test("unauthenticated protected route redirects to login (fail-closed)", async ({ page }) => {
    await page.goto("http://localhost:9527/tenant", { waitUntil: "domcontentloaded" });
    await page.waitForURL((url) => url.pathname.startsWith("/login"), { timeout: 15_000 });
    await expect(page.locator("button.cyber-submit-btn")).toBeVisible();
  });

  test("platform admin login, navigation, and core pages render cleanly", async ({ page }) => {
    const { pageErrors, serverErrors } = await loginThroughUi(
      page,
      "http://localhost:9527",
      "admin",
      "Admin@123456"
    );

    await expect(page.locator(".console-shell, .layout-shell, main").first()).toBeVisible();

    const coreRoutes = ["/dashboard", "/tenant", "/iam/users", "/crm/customers", "/erp/inventory", "/ai/models"];
    for (const routePath of coreRoutes) {
      await page.goto(`http://localhost:9527${routePath}`, { waitUntil: "domcontentloaded" });
      await expect(page).not.toHaveURL(/\/login/);
      await page.waitForTimeout(400);
      await expect(page.locator("body")).not.toContainText("Application Error");
    }

    expect(pageErrors, `Uncaught page errors: ${pageErrors.join("; ")}`).toEqual([]);
    expect(serverErrors, `HTTP 5xx responses: ${serverErrors.join("; ")}`).toEqual([]);
  });

  test("tenant subdomain login isolates workspace and hides platform tenant creation", async ({ page }) => {
    const { pageErrors, serverErrors } = await loginThroughUi(
      page,
      "http://juzhenfa.localhost:9527",
      "admin",
      "Admin@123456"
    );

    await page.goto("http://juzhenfa.localhost:9527/dashboard", { waitUntil: "domcontentloaded" });
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.locator("body")).toContainText("工作台");
    await expect(page.locator("body")).not.toContainText("新建租户");

    expect(pageErrors, `Uncaught page errors: ${pageErrors.join("; ")}`).toEqual([]);
    expect(serverErrors, `HTTP 5xx responses: ${serverErrors.join("; ")}`).toEqual([]);
  });
});
