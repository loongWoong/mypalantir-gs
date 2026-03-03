import { test, expect } from '@playwright/test';

/**
 * 主导航 UI 测试：通过侧栏链接点击跳转，验证 URL 与页面标题/内容。
 * 依赖应用已挂载并完成初始加载（可能需后端 schema API）。
 */
test.describe('侧栏导航点击跳转', () => {
  test.setTimeout(45000);

  test.beforeEach(async ({ page }) => {
    await page.goto('/schema');
    await expect(page).toHaveURL(/\/schema$/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 15000 });
    // 等待侧栏链接就绪（使用 href 定位更稳定）
    await page.locator('nav a[href="/query"]').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('点击「查询构建器」跳转到 /query', async ({ page }) => {
    await page.locator('nav a[href="/query"]').click();
    await expect(page).toHaveURL(/\/query$/, { timeout: 10000 });
    await expect(page.locator('#root')).toBeVisible();
  });

  test('点击「指标管理」跳转到 /metrics', async ({ page }) => {
    await page.locator('nav a[href="/metrics"]').click();
    await expect(page).toHaveURL(/\/metrics$/, { timeout: 10000 });
    await expect(page.locator('#root')).toBeVisible();
  });

  test('点击「自然语言查询」跳转到 /natural-language-query', async ({ page }) => {
    await page.locator('nav a[href="/natural-language-query"]').click();
    await expect(page).toHaveURL(/\/natural-language-query/, { timeout: 10000 });
    await expect(page.locator('#root')).toBeVisible();
  });

  test('点击「数据对账」跳转到 /data-comparison', async ({ page }) => {
    await page.locator('nav a[href="/data-comparison"]').click();
    await expect(page).toHaveURL(/\/data-comparison/, { timeout: 10000 });
    await expect(page.locator('#root')).toBeVisible();
  });

  test('点击「本体构建工具」跳转到 /ontology-builder', async ({ page }) => {
    await page.locator('nav a[href="/ontology-builder"]').click();
    await expect(page).toHaveURL(/\/ontology-builder/, { timeout: 10000 });
    await expect(page.locator('#root')).toBeVisible();
  });

  test('点击「本体关系图」跳转到 /schema-graph', async ({ page }) => {
    await page.locator('nav a[href="/schema-graph"]').click();
    await expect(page).toHaveURL(/\/schema-graph/, { timeout: 10000 });
    await expect(page.locator('#root')).toBeVisible();
  });

  test('点击「本体模型」保持在 /schema', async ({ page }) => {
    await page.locator('nav a[href="/schema"]').first().click();
    await expect(page).toHaveURL(/\/schema$/, { timeout: 10000 });
    await expect(page.locator('#root')).toBeVisible();
  });
});

test.describe('页面标题/区域文案', () => {
  test.setTimeout(20000);

  test('Schema 页显示 Schema Browser 标题', async ({ page }) => {
    await page.goto('/schema');
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Schema Browser')).toBeVisible({ timeout: 5000 });
  });

  test('Query 页显示 Query Builder 标题', async ({ page }) => {
    await page.goto('/query');
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Query Builder')).toBeVisible({ timeout: 5000 });
  });

  test('指标页显示 Metric Manage 标题', async ({ page }) => {
    await page.goto('/metrics');
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Metric Manage')).toBeVisible({ timeout: 5000 });
  });
});
