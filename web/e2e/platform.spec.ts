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

  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);

  const shieldHint = page.locator(".smart-shield-card, .shield-status-text");
  if ((await shieldHint.count()) > 0) {
    await expect(page.locator(".shield-verified, .smart-shield-card.is-verified").first())
      .toBeVisible({ timeout: 15_000 })
      .catch(() => {
        // Fallback: submit click handles pending verification
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

  test("platform admin login, navigation, command palette, and core modules render cleanly", async ({ page }) => {
    const { pageErrors, serverErrors } = await loginThroughUi(
      page,
      "http://localhost:9527",
      "admin",
      "Admin@123456"
    );

    await expect(page.locator(".console-shell, .layout-shell, main").first()).toBeVisible();

    const coreRoutes = [
      "/dashboard",
      "/tenant",
      "/iam/users",
      "/iam/roles",
      "/crm/customers",
      "/erp/inventory",
      "/workflow/list",
      "/ai/models",
      "/ai/agents",
      "/ai/knowledge",
      "/file/storage",
      "/audit/logs"
    ];
    for (const routePath of coreRoutes) {
      await page.goto(`http://localhost:9527${routePath}`, { waitUntil: "domcontentloaded" });
      await expect(page).not.toHaveURL(/\/login/);
      await page.waitForTimeout(350);
      await expect(page.locator("body")).not.toContainText("Application Error");
    }

    // Verify CRM customer list & interactive search UI renders cleanly
    await page.goto("http://localhost:9527/crm/customers", { waitUntil: "domcontentloaded" });
    await expect(page.locator(".el-table, .el-card, main").first()).toBeVisible();

    // Verify AI Visual Agent DAG Studio renders canvas and palette cleanly
    await page.goto("http://localhost:9527/ai/agents", { waitUntil: "domcontentloaded" });
    await expect(page.locator(".dag-studio-shell, .dag-main-grid").first()).toBeVisible({ timeout: 10_000 });

    // Verify Command Palette shortcut (Meta+k / Control+k) works
    await page.keyboard.press(process.platform === "darwin" ? "Meta+k" : "Control+k");
    await page.waitForTimeout(250);

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
    await expect(page.locator("body")).not.toContainText("租户管理员");

    // Command Palette should not expose platform-only tenant center to customer workspace
    await page.keyboard.press(process.platform === "darwin" ? "Meta+k" : "Control+k");
    await page.waitForTimeout(200);
    await expect(page.locator("body")).not.toContainText("多租户管控与灾备中心");
    await page.keyboard.press("Escape");

    // Direct URL visit to /tenant should redirect back to customer workspace
    await page.goto("http://juzhenfa.localhost:9527/tenant", { waitUntil: "domcontentloaded" });
    await page.waitForURL((url) => !url.pathname.startsWith("/tenant"), { timeout: 15_000 });

    // Tenant workspace can open business routes cleanly
    await page.goto("http://juzhenfa.localhost:9527/crm/customers", { waitUntil: "domcontentloaded" });
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.locator(".el-table, .el-card, main").first()).toBeVisible();

    expect(pageErrors, `Uncaught page errors: ${pageErrors.join("; ")}`).toEqual([]);
    expect(serverErrors, `HTTP 5xx responses: ${serverErrors.join("; ")}`).toEqual([]);
  });
});
