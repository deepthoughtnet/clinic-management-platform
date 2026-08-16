import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("public informational pages and footer routes are wired to real destinations", () => {
  const app = read("src/App.tsx");
  const routes = read("src/routes.ts");
  const pages = read("src/pages/public/PublicInfoPages.tsx");
  const styles = read("src/styles.css");

  for (const text of [
    'path={DISCOVER_ROUTES.help.path}',
    'path={DISCOVER_ROUTES.accessibility.path}',
    'path={DISCOVER_ROUTES.sitemap.path}',
    'path={DISCOVER_ROUTES.contact.path}',
    'path={DISCOVER_ROUTES.privacy.path}',
    'path={DISCOVER_ROUTES.terms.path}',
    'path={DISCOVER_ROUTES.security.path}',
    'path={DISCOVER_ROUTES.cookies.path}',
  ]) {
    assert.ok(app.includes(text), `${text} should exist in App routes/footer`);
  }

  for (const text of [
    'help: { path: "/help"',
    'accessibility: { path: "/accessibility"',
    'sitemap: { path: "/sitemap"',
    'security: { path: "/security"',
    'cookies: { path: "/cookies"',
  ]) {
    assert.ok(routes.includes(text), `${text} should exist in route definitions`);
  }

  assert.ok(pages.includes("Help and support"));
  assert.ok(pages.includes("Accessibility at Jeevanam Discover"));
  assert.ok(pages.includes("Jeevanam Discover sitemap"));
  assert.ok(pages.includes("Privacy"));
  assert.ok(pages.includes("Terms"));
  assert.ok(pages.includes("Security"));
  assert.ok(pages.includes("Cookies"));
  assert.ok(pages.includes("Support channel"));
  assert.ok(pages.includes("Send enquiry"));
  assert.ok(pages.includes("mailto:"));
  assert.ok(styles.includes(".public-info-page"));
  assert.ok(styles.includes(".public-info-grid"));
  assert.ok(styles.includes(".public-contact-form"));
  assert.ok(styles.includes(".discover-mobile-menu"));
});
