import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("provider login uses explicit challenge ids and preserves otp strings", () => {
  const page = read("src/pages/provider/ProviderLoginPage.tsx");
  const api = read("src/api/providerAuth.ts");
  const styles = read("src/styles.css");
  const validation = read("src/pages/provider/providerAccessValidation.ts");

  assert.ok(page.includes("type ActiveProviderLoginChallenge = {"));
  assert.ok(page.includes("function toActiveChallenge(response: ProviderLoginChallengeResponse): ActiveProviderLoginChallenge"));
  assert.ok(page.includes("const [activeChallenge, setActiveChallenge] = useState<ActiveProviderLoginChallenge | null>(null);"));
  assert.ok(page.includes("const activeChallengeRef = useRef<ActiveProviderLoginChallenge | null>(null);"));
  assert.ok(page.includes("const challengeRequestSequenceRef = useRef(0);"));
  assert.ok(page.includes("const latestAppliedChallengeSequenceRef = useRef(0);"));
  assert.ok(page.includes("activeChallengeRef.current = nextChallenge;"));
  assert.ok(page.includes("setActiveChallenge(nextChallenge);"));
  assert.ok(page.includes("if (requestSequence < latestAppliedChallengeSequenceRef.current) {"));
  assert.ok(page.includes("clearChallengeState(\"identifier\")"));
  assert.ok(page.includes("verifyProviderLoginCode(currentChallenge.challengeId, codeDigits)"));
  assert.ok(page.includes("maskedRecipient"));
  assert.ok(page.includes("const sendLockRef"));
  assert.ok(page.includes("const verifyLockRef"));
  assert.ok(page.includes("maxLength={6}"));
  assert.ok(page.includes("inputMode=\"numeric\""));
  assert.ok(page.includes("onPaste"));
  assert.ok(page.includes("autoComplete=\"one-time-code\""));
  assert.ok(page.includes("Development verification code"));
  assert.ok(page.includes("Send Verification Code"));
  assert.ok(page.includes("Verify and Continue"));
  assert.ok(page.includes("Resend Code"));
  assert.ok(page.includes("Change Email or Mobile Number"));
  assert.ok(page.includes("List Your Practice"));
  assert.ok(api.includes("/api/provider/auth/challenges"));
  assert.ok(api.includes("/api/provider/auth/challenges/${challengeId}/verify"));
  assert.ok(api.includes("challengeId: string"));
  assert.ok(api.includes("maskedRecipient: string | null"));
  assert.ok(validation.includes("PROVIDER_LOGIN_IDENTIFIER_MAX_LENGTH"));
  assert.ok(validation.includes("PROVIDER_LOGIN_ACCESS_CODE_LENGTH"));
  assert.ok(validation.includes("getProviderLoginIdentifierError"));
  assert.ok(validation.includes("getProviderAccessCodeError"));
  assert.ok(styles.includes(".provider-auth-page"));
  assert.ok(styles.includes(".provider-auth-card"));
  assert.ok(styles.includes(".provider-auth-field"));
  assert.ok(styles.includes(".provider-auth-code-input"));
  assert.ok(styles.includes(".provider-auth-field-error"));
  assert.ok(styles.includes(".provider-field-required"));
  assert.ok(styles.includes(".provider-auth-contact-card"));
  assert.ok(styles.includes(".provider-auth-dev-panel"));
  assert.ok(styles.includes("width: min(680px"));
  assert.ok(styles.includes("min-height: 54px"));
  assert.ok(styles.includes("padding: 108px 0 72px"));
  assert.ok(styles.includes("padding-top: 96px"));

  assert.ok(!page.includes("Local Development Access"));
  assert.ok(!page.includes("Continue in Local Mode"));
  assert.ok(!api.includes("/api/provider/auth/local/accounts"));
  assert.ok(!api.includes("/api/provider/auth/local/session"));
  assert.ok(!styles.includes(".provider-auth-local-card"));
});

test("provider login keeps auth state in memory and does not fake local login in browser storage", () => {
  const page = read("src/pages/provider/ProviderLoginPage.tsx");
  const api = read("src/api/providerAuth.ts");
  const workspace = read("src/pages/provider/ProviderWorkspacePage.tsx");
  const app = read("src/App.tsx");
  const sessionContext = read("src/context/ProviderSessionContext.tsx");

  assert.ok(!page.includes("sessionStorage"));
  assert.ok(!page.includes("localStorage"));
  assert.ok(!page.includes("history.state"));
  assert.ok(!page.includes("setItem("));
  assert.ok(!page.includes("getItem("));
  assert.ok(!page.includes("mock auth"));
  assert.ok(api.includes("cache: \"no-store\""));
  assert.ok(api.includes("credentials: \"include\""));
  assert.ok(page.includes("const { status, refreshSession } = useProviderSession();"));
  assert.ok(page.includes("function resolveSafeReturnTo(rawReturnTo: string | null): string"));
  assert.ok(page.includes("trimmed.startsWith(\"/provider/\")"));
  assert.ok(page.includes("trimmed.startsWith(\"//\")"));
  assert.ok(page.includes("const returnTo = resolveSafeReturnTo(searchParams.get(\"returnTo\"));"));
  assert.ok(page.includes("await refreshSession(true);"));
  assert.ok(page.includes("Restoring your provider session"));
  assert.ok(app.includes("function ProviderProtectedRoute()"));
  assert.ok(app.includes("return <Navigate replace to={`${DISCOVER_ROUTES.providerLogin.path}?returnTo=${encodeURIComponent(returnTo)}`} />;"));
  assert.ok(sessionContext.includes("ex instanceof ProviderAuthError && ex.status === 401"));
  assert.ok(workspace.includes("async function endSession(targetPath: string)"));
  assert.ok(workspace.includes("await logout();"));
  assert.ok(workspace.includes("navigate(targetPath, { replace: true });"));
  assert.ok(workspace.includes("Switch account"));
  assert.ok(!page.includes("localhost:5173"));
});
