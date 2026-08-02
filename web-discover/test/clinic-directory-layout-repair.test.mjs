import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function read(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("clinic and hospital directory cards use a stable horizontal layout and dedupe location text", () => {
  const components = read("components/directory/DirectoryComponents.tsx");
  const styles = read("styles.css");

  assert.ok(components.includes("formatDirectoryLocationLabel"));
  assert.ok(components.includes("line-clamp-3"));
  assert.ok(components.includes("BookingCapabilityBadge"));
  assert.ok(styles.includes("grid-template-columns: minmax(180px, 220px) minmax(0, 1fr)"));
  assert.ok(styles.includes("grid-template-columns: minmax(220px, 280px) minmax(0, 1fr)"));
  assert.ok(styles.includes("min-height: 188px"));
  assert.ok(styles.includes(".directory-card--clinic .directory-card__summary"));
  assert.ok(styles.includes(".directory-card--clinic .directory-card__actions"));
});
