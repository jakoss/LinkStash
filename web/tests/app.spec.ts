import { test, expect } from "@playwright/test";

test.beforeEach(({}, testInfo) => {
  if (!process.env.E2E_BYPASS_TOKEN) {
    testInfo.skip("Set E2E_BYPASS_TOKEN to run authenticated tests.");
  }
});

const signIn = async (page: import("@playwright/test").Page) => {
  await page.goto("/");
  await page.getByRole("button", { name: "E2E sign in" }).click();
  await page.getByRole("button", { name: "New space" }).waitFor();
};

const createSpace = async (page: import("@playwright/test").Page, name: string) => {
  await page.getByRole("button", { name: "New space" }).click();
  await page.getByPlaceholder("Space name").fill(name);
  await page.getByRole("button", { name: "Create" }).click();
  await page.getByText(name).first().click();
};

const deleteSpace = async (page: import("@playwright/test").Page, name: string) => {
  const spaceItem = page.locator(".space-list").getByText(name).first();
  if ((await spaceItem.count()) === 0) {
    return;
  }
  await spaceItem.click();
  await page
    .locator(".space-item.is-active")
    .getByRole("button", { name: "Delete" })
    .click();

  const modal = page.locator(".modal");
  const modalDelete = modal.getByRole("button", { name: "Delete" });
  await modal.getByPlaceholder(name).fill(name);
  await expect(modalDelete).toBeEnabled();
  await modalDelete.click();
  await expect(page.locator(".space-list").getByText(name)).toHaveCount(0);
};

test("loads the LinkStash shell", async ({ page }) => {
  await signIn(page);
  await expect(page.getByText("LinkStash")).toBeVisible();
  await expect(page.getByRole("button", { name: "New space" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Export active links" })).toBeVisible();
  await expect(page.getByLabel("Paste a link")).toBeVisible();
});

test("creates space, manages links, exports active links", async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await signIn(page);

  const spaceName = `E2E Space ${Date.now()}`;
  await createSpace(page, spaceName);

  const linkOne = "https://example.com/one";
  const linkTwo = "https://example.com/two";

  await page.getByLabel("Paste a link").fill(linkOne);
  await page.getByRole("button", { name: "Add link" }).click();
  await expect(page.getByText(linkOne).first()).toBeVisible();

  await page.getByLabel("Paste a link").fill(linkTwo);
  await page.getByRole("button", { name: "Add link" }).click();
  await expect(page.getByText(linkTwo).first()).toBeVisible();

  const firstItem = page.locator(".link-item").filter({ hasText: linkOne });
  await firstItem.getByRole("button", { name: "Archive" }).click();
  await page.getByRole("button", { name: "Archived" }).click();
  await expect(page.locator(".link-item").filter({ hasText: linkOne })).toBeVisible();

  await page.locator(".toggle").getByRole("button", { name: "Active" }).click();
  await page.getByRole("button", { name: "Export active links" }).click();
  await expect(page.getByText("Copied active links to clipboard.")).toBeVisible();

  const clipboardText = await page.evaluate(async () => navigator.clipboard.readText());
  await expect(clipboardText).toContain(linkTwo);
  await expect(clipboardText).not.toContain(linkOne);

  await deleteSpace(page, spaceName);
});

test("move link between spaces", async ({ page }) => {
  await signIn(page);

  const sourceSpace = `Move From ${Date.now()}`;
  const targetSpace = `Move To ${Date.now()}`;
  await createSpace(page, sourceSpace);
  await createSpace(page, targetSpace);
  await page.getByText(sourceSpace).first().click();

  const linkUrl = "https://example.com/move";
  await page.getByLabel("Paste a link").fill(linkUrl);
  await page.getByRole("button", { name: "Add link" }).click();
  await expect(page.getByText(linkUrl).first()).toBeVisible();

  await page.getByRole("combobox").selectOption({ label: targetSpace });
  await expect(page.getByText(linkUrl).first()).not.toBeVisible();

  await page.getByText(targetSpace).first().click();
  await expect(page.getByText(linkUrl).first()).toBeVisible();

  await deleteSpace(page, sourceSpace);
  await deleteSpace(page, targetSpace);
});

test("archive toggle updates list counts", async ({ page }) => {
  await signIn(page);

  const spaceName = `Archive Space ${Date.now()}`;
  await createSpace(page, spaceName);

  const linkUrl = "https://example.com/archive";
  await page.getByLabel("Paste a link").fill(linkUrl);
  await page.getByRole("button", { name: "Add link" }).click();
  await expect(page.getByText(linkUrl).first()).toBeVisible();
  await expect(page.getByText("1 active link")).toBeVisible();

  const activeItem = page.locator(".link-item").filter({ hasText: linkUrl });
  await activeItem.getByRole("button", { name: "Archive" }).click();

  await page.getByRole("button", { name: "Archived" }).click();
  await expect(page.getByText(linkUrl).first()).toBeVisible();
  await expect(page.getByText("1 archived link")).toBeVisible();

  const archivedItem = page.locator(".link-item").filter({ hasText: linkUrl });
  await expect(archivedItem).toBeVisible();
  await archivedItem.getByRole("button").first().click();
  await page.locator(".toggle").getByRole("button", { name: "Active" }).click();
  await expect(page.getByText(linkUrl).first()).toBeVisible();
  await expect(page.getByText("1 active link")).toBeVisible();

  await deleteSpace(page, spaceName);
});

test("export handles empty active list", async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await signIn(page);

  const spaceName = `Empty Export ${Date.now()}`;
  await createSpace(page, spaceName);
  await page.getByRole("button", { name: "Export active links" }).click();
  await expect(page.getByText("No active links to export.")).toBeVisible();

  await deleteSpace(page, spaceName);
});

test("url input trims whitespace", async ({ page }) => {
  await signIn(page);

  const spaceName = `Trim Space ${Date.now()}`;
  await createSpace(page, spaceName);

  await page.getByLabel("Paste a link").fill("  https://example.com/trim  ");
  await page.getByRole("button", { name: "Add link" }).click();
  await expect(page.getByText("https://example.com/trim").first()).toBeVisible();

  await deleteSpace(page, spaceName);
});

test("newest links appear first", async ({ page }) => {
  await signIn(page);

  const spaceName = `Order Space ${Date.now()}`;
  await createSpace(page, spaceName);

  const firstUrl = "https://example.com/first";
  const secondUrl = "https://example.com/second";

  await page.getByLabel("Paste a link").fill(firstUrl);
  await page.getByRole("button", { name: "Add link" }).click();
  await expect(page.getByText(firstUrl).first()).toBeVisible();

  await page.getByLabel("Paste a link").fill(secondUrl);
  await page.getByRole("button", { name: "Add link" }).click();
  await expect(page.getByText(secondUrl).first()).toBeVisible();

  const firstItem = page.locator(".link-item").first();
  await expect(firstItem).toContainText(secondUrl);

  await deleteSpace(page, spaceName);
});

test("space deletion requires confirmation and blocks default", async ({ page }) => {
  await signIn(page);

  const spaceName = `Delete Space ${Date.now()}`;
  await createSpace(page, spaceName);

  await page.getByText(spaceName).first().click();
  await page
    .locator(".space-item.is-active")
    .getByRole("button", { name: "Delete" })
    .click();

  const modal = page.locator(".modal");
  const modalDelete = modal.getByRole("button", { name: "Delete" });
  await expect(modalDelete).toBeDisabled();
  await modal.getByPlaceholder(spaceName).fill("wrong");
  await expect(modalDelete).toBeDisabled();
  await modal.getByPlaceholder(spaceName).fill(spaceName);
  await expect(modalDelete).toBeEnabled();
  await modalDelete.click();

  await expect(page.locator(".space-list").getByText(spaceName)).toHaveCount(0);

  const defaultDelete = page.locator(".space-item").first().getByRole("button", { name: "Delete" });
  await expect(defaultDelete).toBeDisabled();
});

test("metadata updates link title asynchronously", async ({ page }) => {
  await signIn(page);

  const spaceName = `Metadata Space ${Date.now()}`;
  await createSpace(page, spaceName);

  const linkUrl = "https://example.com";
  await page.getByLabel("Paste a link").fill(linkUrl);
  await page.getByRole("button", { name: "Add link" }).click();
  await expect(page.getByText(linkUrl).first()).toBeVisible();

  await expect
    .poll(
      async () => {
        await page.reload();
        await page.getByText(spaceName).first().click();
        const title = await page
          .locator(".link-item")
          .filter({ hasText: linkUrl })
          .locator(".link-title")
          .textContent();
        return title ?? "";
      },
      { timeout: 15000, intervals: [1000, 2000] },
    )
    .toContain("Example Domain");

  await deleteSpace(page, spaceName);
});
