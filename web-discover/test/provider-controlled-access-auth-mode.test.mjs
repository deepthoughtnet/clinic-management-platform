import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("provider controlled access resolves explicit build-time mode before dev fallback", () => {
  const config = read("src/config.ts");
  const dockerfile = read("Dockerfile");
  const compose = read("../local/docker-compose.yml");
  const composeUat = read("../local/docker-compose.uat.yml");
  const env = read("../local/.env.full-docker");
  const envExample = read("../local/.env.uat-arogia.example");

  assert.ok(config.includes('VITE_PROVIDER_PORTAL_AUTH_MODE'));
  assert.ok(config.includes('function resolveProviderPortalAuthMode(): ProviderPortalAuthMode'));
  assert.ok(config.includes('const configuredMode = normalizeProviderPortalAuthMode(import.meta.env.VITE_PROVIDER_PORTAL_AUTH_MODE);'));
  assert.ok(config.includes('if (configuredMode) {'));
  assert.ok(config.includes('return import.meta.env.DEV ? "DEV_OTP" : "OTP";'));
  assert.ok(config.includes('providerPortalAuthMode: resolveProviderPortalAuthMode()'));
  assert.ok(dockerfile.includes('ARG VITE_PROVIDER_PORTAL_AUTH_MODE='));
  assert.ok(dockerfile.includes('ENV VITE_PROVIDER_PORTAL_AUTH_MODE=${VITE_PROVIDER_PORTAL_AUTH_MODE}'));
  assert.ok(compose.includes('CLINIC_PROVIDER_PORTAL_AUTH_MODE'));
  assert.ok(compose.includes('CLINIC_PROVIDER_PORTAL_EXPOSE_DEV_OTP'));
  assert.ok(compose.includes('VITE_PROVIDER_PORTAL_AUTH_MODE'));
  assert.ok(composeUat.includes('CLINIC_PROVIDER_PORTAL_AUTH_MODE'));
  assert.ok(composeUat.includes('VITE_PROVIDER_PORTAL_AUTH_MODE'));
  assert.ok(env.includes('CLINIC_PROVIDER_PORTAL_AUTH_MODE=ACCESS_APPROVAL'));
  assert.ok(env.includes('VITE_PROVIDER_PORTAL_AUTH_MODE=ACCESS_APPROVAL'));
  assert.ok(env.includes('CLINIC_PROVIDER_PORTAL_EXPOSE_DEV_OTP=false'));
  assert.ok(envExample.includes('CLINIC_PROVIDER_PORTAL_AUTH_MODE=ACCESS_APPROVAL'));
  assert.ok(envExample.includes('VITE_PROVIDER_PORTAL_AUTH_MODE=ACCESS_APPROVAL'));
});

test("provider login and access request pages render ACCESS_APPROVAL controls only", () => {
  const loginPage = read("src/pages/provider/ProviderLoginPage.tsx");
  const accessPage = read("src/pages/provider/ProviderAccessApprovalLoginPage.tsx");
  const requestPage = read("src/pages/provider/ProviderRequestAccessPage.tsx");
  const routes = read("src/routes.ts");
  const api = read("src/api/providerAuth.ts");

  assert.ok(loginPage.includes('providerPortalAuthMode === "ACCESS_APPROVAL"'));
  assert.ok(loginPage.includes("ProviderAccessApprovalLoginPage"));
  assert.ok(accessPage.includes("Friends &amp; Family access to Jeevanam Provider"));
  assert.ok(accessPage.includes("Request Provider Access"));
  assert.ok(accessPage.includes("Temporary access code"));
  assert.ok(accessPage.includes("Enter the 8-digit access code"));
  assert.ok(accessPage.includes("Already approved?"));
  assert.ok(accessPage.includes("returnTo"));
  assert.ok(accessPage.includes("loginProviderAccess"));
  assert.ok(requestPage.includes("Request access to Jeevanam Provider"));
  assert.ok(requestPage.includes("Submit access request"));
  assert.ok(requestPage.includes("Provider application reference"));
  assert.ok(routes.includes('providerRequestAccess: { path: "/provider/request-access"'));
  assert.ok(api.includes("requestProviderAccess"));
  assert.ok(api.includes("loginProviderAccess"));

  assert.ok(!accessPage.includes("Send Verification Code"));
  assert.ok(!accessPage.includes("Development verification code"));
  assert.ok(!accessPage.includes("Resend Code"));
  assert.ok(!accessPage.includes("Verify and Continue"));
  assert.ok(!accessPage.includes("development auth tools"));
});
