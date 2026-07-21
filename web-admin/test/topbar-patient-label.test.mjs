import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("top bar patient routes use business labels instead of raw ids", () => {
  const source = readSource("layout/TopBar.tsx");

  assert.ok(source.includes('if (pathname === "/patients/new") return "Patients";'));
  assert.ok(source.includes('return "Patient Details";'));
  assert.ok(source.includes('^\\/patients\\/[^/]+\\/edit$'));
  assert.ok(source.includes('^\\/patients\\/[^/]+$'));
});
