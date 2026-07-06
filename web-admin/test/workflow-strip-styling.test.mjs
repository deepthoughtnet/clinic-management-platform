import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? path.join(process.cwd(), "web-admin") : process.cwd();
}

function readSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "src", ...relPath.split("/")), "utf8");
}

test("shared workflow strip uses uniform sizing and label typography", () => {
  const source = readSource("components/compact/CompactUi.tsx");

  assert.ok(source.includes('gridAutoColumns: "minmax(108px, 1fr)"'));
  assert.ok(source.includes('minWidth: 108'));
  assert.ok(source.includes('width: 28'));
  assert.ok(source.includes('height: 28'));
  assert.ok(source.includes('fontWeight: 800'));
  assert.ok(source.includes('lineHeight: 1.2'));
  assert.ok(source.includes('WebkitLineClamp: 2'));
  assert.ok(source.includes('left: "calc(50% + 14px)"'));
  assert.ok(source.includes('bgcolor: state === "completed" ? "success.main" : "divider"'));
});
