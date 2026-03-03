import { test, expect } from '@playwright/test';

test.describe('前端烟雾测试', () => {
  test('首页加载并重定向到 /schema', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/MyPalantir/i);
    await expect(page).toHaveURL(/\/(schema)?$/);
  });

  test('根节点存在，应用已挂载', async ({ page }) => {
    await page.goto('/');
    const root = page.locator('#root');
    await expect(root).toBeVisible({ timeout: 10000 });
  });
});

test.describe('主导航', () => {
  test('Schema 页可访问', async ({ page }) => {
    await page.goto('/schema');
    await expect(page).toHaveURL(/\/schema/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('Links 页可访问', async ({ page }) => {
    await page.goto('/links');
    await expect(page).toHaveURL(/\/links/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('Query 页可访问', async ({ page }) => {
    await page.goto('/query');
    await expect(page).toHaveURL(/\/query/);
    await expect(page.locator('#root')).toBeVisible({ timeout: 10000 });
  });

  test('从首页可导航到 Schema', async ({ page }) => {
    await page.goto('/');
    await page.waitForURL(/\/(schema)?$/, { timeout: 5000 });
    const root = page.locator('#root');
    await expect(root).toBeVisible({ timeout: 10000 });
  });
});
