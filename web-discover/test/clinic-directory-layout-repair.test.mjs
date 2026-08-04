import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function read(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("clinic and hospital directory cards use a shared vertical list and dedupe location text", () => {
  const pages = read("pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("components/directory/DirectoryComponents.tsx");
  const styles = read("styles.css");

  assert.ok(components.includes("formatDirectoryLocationLabel"));
  assert.ok(components.includes("line-clamp-3"));
  assert.ok(components.includes("BookingCapabilityBadge"));
  assert.ok(components.includes("DirectoryResultList"));
  assert.ok(pages.includes('loadMoreLabel="Load more clinics"'));
  assert.ok(pages.includes('loadMoreLabel="Load more hospitals"'));
  assert.ok(styles.includes(".directory-result-list"));
  assert.ok(styles.includes(".directory-result-list__footer"));
  assert.ok(styles.includes("grid-template-columns: minmax(0, 1fr)"));
  assert.ok(!styles.includes("repeat(auto-fit, minmax(280px, 1fr))"));
  assert.ok(!styles.includes("repeat(auto-fit, minmax(300px, 1fr))"));
  assert.ok(styles.includes("min-height: 188px"));
  assert.ok(styles.includes(".directory-card--clinic .directory-card__summary"));
  assert.ok(styles.includes(".directory-card--clinic .directory-card__actions"));
});
