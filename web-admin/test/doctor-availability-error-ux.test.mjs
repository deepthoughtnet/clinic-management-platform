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

test("doctor availability page surfaces backend business error text", () => {
  const source = readWebAdminSource("pages/doctors/DoctorAvailabilityPage.tsx");
  const restClient = readWebAdminSource("api/restClient.ts");

  assert.ok(source.includes('setError(e instanceof Error ? e.message : "Failed to add availability")'));
  assert.ok(source.includes('setError(e instanceof Error ? e.message : "Failed to update availability status")'));
  assert.ok(source.includes('{error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}'));
  assert.ok(restClient.includes("sanitizeErrorMessage"));
  assert.ok(restClient.includes("payload?.message?.trim()"));
});
