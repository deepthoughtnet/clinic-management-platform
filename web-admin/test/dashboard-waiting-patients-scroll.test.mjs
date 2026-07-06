import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? process.cwd() : path.join(process.cwd(), "..");
}

function readWebAdminSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

test("dashboard waiting patients list scrolls internally without clipping journey", () => {
  const source = readWebAdminSource("pages/DashboardPage.tsx");

  assert.ok(source.includes("waitingPatientListMaxHeight"));
  assert.ok(source.includes('overflowY: waitingPatientListMaxHeight ? "auto" : "visible"'));
  assert.ok(source.includes('overflowX: "hidden"'));
  assert.ok(source.includes('overflow: "visible"'));
  assert.ok(source.includes('Collapse in={isExpanded} timeout="auto" unmountOnExit sx={{ overflow: "visible" }}'));
  assert.ok(source.includes('visibleCards = waitingPatientFilter === "ALL" ? 4 : 3'));
});
