import { test, expect } from '@playwright/test';

/**
 * 各主要页面直接访问可加载（URL 正确、应用挂载）。
 * 不依赖后端返回具体数据，仅验证路由与 #root 可见。
 */
test.describe('主要页面可访问性', () => {
  test('Schema 页 /schema 可加载', async ({ page }) => {
    await page.goto('/schema');
    await expect(page).toHaveURL(/\/schema$/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('本体关系图 /schema-graph 可加载', async ({ page }) => {
    await page.goto('/schema-graph');
    await expect(page).toHaveURL(/\/schema-graph/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('数据源管理 /data-sources 可加载', async ({ page }) => {
    await page.goto('/data-sources');
    await expect(page).toHaveURL(/\/data-sources/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('查询构建器 /query 可加载', async ({ page }) => {
    await page.goto('/query');
    await expect(page).toHaveURL(/\/query$/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('指标管理 /metrics 可加载', async ({ page }) => {
    await page.goto('/metrics');
    await expect(page).toHaveURL(/\/metrics$/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('指标构建器 /metrics/builder 可加载', async ({ page }) => {
    await page.goto('/metrics/builder');
    await expect(page).toHaveURL(/\/metrics\/builder/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('自然语言查询 /natural-language-query 可加载', async ({ page }) => {
    await page.goto('/natural-language-query');
    await expect(page).toHaveURL(/\/natural-language-query/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('数据对账 /data-comparison 可加载', async ({ page }) => {
    await page.goto('/data-comparison');
    await expect(page).toHaveURL(/\/data-comparison/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('本体构建工具 /ontology-builder 可加载', async ({ page }) => {
    await page.goto('/ontology-builder');
    await expect(page).toHaveURL(/\/ontology-builder/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });
});

test.describe('带参数路由可访问', () => {
  test('实例列表 /instances/:objectType 可加载', async ({ page }) => {
    await page.goto('/instances/workspace');
    await expect(page).toHaveURL(/\/instances\/workspace/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('关系列表 /links/:linkType 可加载', async ({ page }) => {
    await page.goto('/links/owns');
    await expect(page).toHaveURL(/\/links\/owns/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });
});
