import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(process.cwd(), "src");

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("auth provider surfaces expired-session messaging and token-expired refresh handling", () => {
  const authProvider = read("auth/AuthProvider.tsx");
  const appSource = read("app/App.tsx");
  const contextSource = read("auth/AuthContext.ts");
  const timeoutSource = read("auth/sessionInactivity.js");
  const dockerfile = fs.readFileSync(new URL("../Dockerfile", import.meta.url), "utf8");

  assert.ok(authProvider.includes("sessionWarning"));
  assert.ok(authProvider.includes("INACTIVITY_WARNING_MESSAGE"));
  assert.ok(authProvider.includes("INACTIVITY_EXPIRED_MESSAGE"));
  assert.ok(authProvider.includes("SESSION_ACTIVITY_STORAGE_KEY"));
  assert.ok(authProvider.includes("SESSION_EVENT_STORAGE_KEY"));
  assert.ok(authProvider.includes("computeInactivityWindow"));
  assert.ok(authProvider.includes("beginTerminalAuthTransition(SESSION_EXPIRED_MESSAGE"));
  assert.ok(authProvider.includes('resetAuthState(INACTIVITY_EXPIRED_MESSAGE, true)'));
  assert.ok(authProvider.includes('function loginRedirectUri()'));
  assert.ok(authProvider.includes('new URL("/login", window.location.origin).toString()'));
  assert.ok(authProvider.includes("getHttpStatus"));
  assert.ok(authProvider.includes('window.location.reload()'));
  assert.ok(authProvider.includes('console.info("[auth] access token expired; attempting refresh")'));
  assert.ok(authProvider.includes('console.warn("[auth] /me request returned 401 during bootstrap"'));
  assert.ok(authProvider.includes("status === 401"));
  assert.ok(authProvider.includes('markSessionExpired("refresh-token-update-failed"'));
  assert.ok(authProvider.includes('markSessionExpired("access-token-expired-refresh-failed"'));
  assert.ok(authProvider.includes('console.info("[auth] user initiated logout")'));
  assert.ok(authProvider.includes('broadcastLogout("explicit")'));
  assert.ok(authProvider.includes('void beginTerminalAuthTransition(INACTIVITY_EXPIRED_MESSAGE, "inactivity", "inactivity");'));
  assert.ok(authProvider.includes('await beginTerminalAuthTransition(SESSION_EXPIRED_MESSAGE, "expired", "expired");'));
  assert.ok(authProvider.includes('await keycloak.logout({ redirectUri: loginRedirectUri() });'));
  assert.ok(authProvider.includes('window.location.assign(loginRedirectUri())'));
  assert.ok(authProvider.includes('if (!keycloak.authenticated || sessionInvalidatedRef.current) return;'));
  assert.ok(authProvider.includes('if (sessionInvalidatedRef.current) {'));
  assert.ok(authProvider.includes('window.addEventListener("storage"'));
  assert.ok(authProvider.includes('pointerdown'));
  assert.ok(authProvider.includes('if (ok && token) {'));
  assert.ok(authProvider.includes('setSessionNotice(null);\n          storeSessionNotice(null);\n          console.info("[auth] tenant bootstrap started");'));
  assert.ok(appSource.includes('Stay signed in'));
  assert.ok(appSource.includes('window.dispatchEvent(new Event("pointerdown"))'));
  assert.ok(appSource.includes("sessionWarning"));
  assert.ok(appSource.includes("auth.sessionNotice"));
  assert.ok(appSource.includes('severity="warning"'));
  assert.ok(contextSource.includes("sessionNotice: string | null"));
  assert.ok(contextSource.includes("sessionWarning: string | null"));
  assert.ok(timeoutSource.includes("Your session will expire soon due to inactivity."));
  assert.ok(timeoutSource.includes("Your session has expired due to inactivity. Please sign in again."));
  assert.ok(timeoutSource.includes("resolveInactivityTimeoutMinutes"));
  assert.ok(timeoutSource.includes("computeInactivityWindow"));
  assert.ok(timeoutSource.includes("parseSharedSessionEvent"));
  assert.ok(dockerfile.includes("VITE_WEB_ADMIN_INACTIVITY_TIMEOUT_MINUTES"));
});
