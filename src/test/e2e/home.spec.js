import { test, expect } from '@playwright/test';

test('home renders and key sections are visible', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/k|K|lotto/i);
  await expect(page.locator('#recommend-result')).toBeVisible();
  await expect(page.locator('#latest-result')).toBeVisible();
  await expect(page.locator('#list-result')).toBeVisible();
});
