import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("patient lab page keeps a full-width desktop content grid", () => {
  const pageSource = readSource("pages/patient/PatientLabPage.tsx");
  const stylesSource = readSource("styles.css");

  assert.ok(pageSource.includes('className="patient-content-grid patient-lab-page"'));
  assert.ok(pageSource.includes('className="portal-dashboard-grid patient-lab-stats"'));
  assert.ok(pageSource.includes('className="portal-section-grid patient-lab-grid"'));
  assert.ok(pageSource.includes('title={order.orderNumber}'));
  assert.ok(stylesSource.includes(".patient-lab-page {"));
  assert.ok(stylesSource.includes("grid-template-columns: minmax(0, 1fr);"));
  assert.ok(stylesSource.includes(".patient-lab-page .portal-list-card-header strong"));
  assert.ok(stylesSource.includes("text-overflow: ellipsis;"));
  assert.ok(stylesSource.includes(".patient-lab-page .status-pill {"));
  assert.ok(stylesSource.includes("white-space: nowrap;"));
});

test("patient lab page keeps readable order and report metadata wrapping rules", () => {
  const stylesSource = readSource("styles.css");

  assert.ok(stylesSource.includes(".patient-lab-page .portal-list-meta span"));
  assert.ok(stylesSource.includes(".patient-lab-page .portal-inline-list li strong"));
  assert.ok(stylesSource.includes(".patient-lab-page .portal-inline-list li span"));
  assert.ok(stylesSource.includes("word-break: normal;"));
  assert.ok(stylesSource.includes("overflow-wrap: break-word;"));
});
