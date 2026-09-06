import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function read(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("care dashboard typography stays coherent across summary cards", () => {
  const styles = read("styles.css");

  assert.ok(styles.includes(".patient-sidebar-card h2,"));
  assert.ok(styles.includes(".patient-panel h2,"));
  assert.ok(styles.includes("font-family: inherit;"));
  assert.ok(styles.includes(".patient-stat-card strong {"));
  assert.ok(styles.includes("clamp(1rem, 2.2vw, 1.3rem)"));
  assert.ok(styles.includes(".patient-stat-card__featured-date"));
  assert.ok(styles.includes(".patient-highlight-card__eyebrow"));
});

test("dashboard summary cards remain laid out as responsive equal-width cards", () => {
  const styles = read("styles.css");

  assert.ok(styles.includes(".patient-summary-grid"));
  assert.ok(styles.includes("grid-template-columns: repeat(3, minmax(0, 1fr));"));
  assert.ok(styles.includes("grid-template-columns: repeat(2, minmax(0, 1fr));"));
});
