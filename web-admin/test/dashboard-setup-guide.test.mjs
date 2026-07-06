import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("dashboard setup guide can collapse, hide, and restore with persisted preferences", () => {
  const source = readSource("pages/DashboardPage.tsx");
  assert.ok(source.includes("Clinic setup"));
  assert.ok(source.includes("Hide setup guide"));
  assert.ok(source.includes("Show setup guide"));
  assert.ok(source.includes("setupGuidePreferenceKey"));
  assert.ok(source.includes("window.localStorage"));
  assert.ok(source.includes("<Collapse"));
  assert.ok(source.includes("setupProgress.percent"));
  assert.ok(source.includes("Setup complete"));
});
