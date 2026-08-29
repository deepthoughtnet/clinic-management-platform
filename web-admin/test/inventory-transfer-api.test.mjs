import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("inventory transfer stock client posts to the public inventory transfer endpoint", () => {
  const api = readSource("api/clinicApi.ts");
  const page = readSource("pages/inventory/InventoryPage.tsx");

  assert.ok(api.includes('export async function transferInventoryStock(token: string, tenantId: string, body: InventoryTransferInput) {'));
  assert.ok(api.includes('return httpPost("/api/inventory/transfers", body, { token, tenantId });'));
  assert.ok(page.includes("transferInventoryStock(auth.accessToken, auth.tenantId"));
  assert.ok(page.includes('setError("Select medicine, source location, destination location, and quantity.")'));
});
