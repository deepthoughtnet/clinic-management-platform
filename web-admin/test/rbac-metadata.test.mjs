import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("rbac metadata classifies tenant and technical roles", () => {
  const source = readSource("auth/rbacMetadata.ts");
  assert.ok(source.includes('role: "CLINIC_ADMIN"'));
  assert.ok(source.includes('category: "tenant-business"'));
  assert.ok(source.includes('role: "TENANT_ADMIN"'));
  assert.ok(source.includes('category: "tenant-technical"'));
  assert.ok(source.includes('role: "ADMIN"'));
  assert.ok(source.includes('status: "Legacy/Compatibility"'));
  assert.ok(source.includes('role: "SERVICE_AGENT"'));
  assert.ok(source.includes('category: "service-system"'));
  assert.ok(source.includes('BUSINESS_ROLE_KEYS'));
  assert.ok(source.includes('TECHNICAL_ROLE_KEYS'));
});

test("rbac metadata labels sensitive healthcare and platform permissions", () => {
  const source = readSource("auth/rbacMetadata.ts");
  assert.ok(source.includes('"tenant.users.role.assign": "Assign User Roles"'));
  assert.ok(source.includes('"tenant.users.reset.password": "Reset User Password"'));
  assert.ok(source.includes('"payment.collect": "Collect Payment"'));
  assert.ok(source.includes('"prescription.finalize": "Finalize Prescription"'));
  assert.ok(source.includes('"audit.export": "Export Audit Log"'));
  assert.ok(source.includes('"platform.provider_connection.identity_override": "Override Provider Identity"'));
  assert.ok(source.includes('"engage.campaign.approve": "Approve Campaign"'));
  assert.ok(source.includes('SENSITIVE_PERMISSION_KEYS'));
  assert.ok(source.includes('moduleFor(permission: string)'));
});
