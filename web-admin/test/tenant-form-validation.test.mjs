import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("tenant form wires shared validation and disables submit until valid", () => {
  const source = readSource("pages/platform/TenantsPage.tsx");
  assert.ok(source.includes("resolver: zodFormResolver(createTenantSchema)"));
  assert.ok(source.includes('mode: "onChange"'));
  assert.ok(source.includes("formState: { errors, isSubmitting, isValid }"));
  assert.ok(source.includes("const canCreateTenant = isValid && !isSubmitting;"));
  assert.ok(source.includes('label="Tenant Name"'));
  assert.ok(source.includes("errors.displayName"));
  assert.ok(source.includes("errors.planId"));
  assert.ok(source.includes("errors.state"));
  assert.ok(source.includes("errors.adminFirstName"));
  assert.ok(source.includes("errors.adminLastName"));
  assert.ok(source.includes("disabled={!canCreateTenant}"));
  assert.ok(source.includes("await load();"));
  assert.equal(source.includes('navigate("/")'), false);
});
