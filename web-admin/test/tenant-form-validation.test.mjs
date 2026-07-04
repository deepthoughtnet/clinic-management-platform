import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("tenant form wires shared validation and disables submit until valid", () => {
  const source = readSource("pages/platform/TenantsPage.tsx");
  assert.ok(source.includes("resolver: zodFormResolver(createTenantSchema)"));
  assert.ok(source.includes('mode: "onChange"'));
  assert.ok(source.includes("const canCreateTenant = isValid && !isSubmitting;"));
  assert.ok(source.includes('label="Tenant Name"'));
  assert.ok(source.includes("disabled={!canCreateTenant}"));
  assert.ok(source.includes("await load();"));
  assert.ok(source.includes('navigate("/")'));
});
