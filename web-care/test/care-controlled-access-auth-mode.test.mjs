import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const TEST_DIR = path.dirname(fileURLToPath(import.meta.url));
const WEB_CARE_ROOT = path.resolve(TEST_DIR, "..");
const REPO_ROOT = path.resolve(WEB_CARE_ROOT, "..");

function resolveCareAuthMode(explicitMode, hostname) {
  const normalized = (explicitMode ?? "").trim();
  if (normalized) {
    const upper = normalized.toUpperCase();
    if (!["DEV_OTP", "ACCESS_APPROVAL", "OTP"].includes(upper)) {
      throw new Error(`Unsupported Care auth mode: ${normalized}`);
    }
    return upper;
  }
  if (hostname === "localhost" || hostname === "127.0.0.1") {
    return "DEV_OTP";
  }
  return "OTP";
}

function read(relPath) {
  return fs.readFileSync(path.join(WEB_CARE_ROOT, relPath), "utf8");
}

test("care auth mode precedence prefers explicit config over localhost fallback", () => {
  assert.equal(resolveCareAuthMode("ACCESS_APPROVAL", "localhost"), "ACCESS_APPROVAL");
  assert.equal(resolveCareAuthMode("access_approval", "localhost"), "ACCESS_APPROVAL");
  assert.equal(resolveCareAuthMode("DEV_OTP", "localhost"), "DEV_OTP");
  assert.equal(resolveCareAuthMode("otp", "localhost"), "OTP");
  assert.equal(resolveCareAuthMode("", "localhost"), "DEV_OTP");
  assert.equal(resolveCareAuthMode(undefined, "example.com"), "OTP");
  assert.throws(() => resolveCareAuthMode("bogus", "localhost"), /Unsupported Care auth mode/);
});

test("care controlled-access mode wires request access and access login paths", () => {
  const app = read("src/App.tsx");
  const pages = read("src/pages/patient/PatientPortalPages.tsx");
  const accessValidation = read("src/pages/patient/patientAccessValidation.js");
  const config = read("src/config.ts");
  const shell = read("src/components/CareShell.tsx");
  const api = read("src/api/patientPortal.ts");
  const dockerfile = read("Dockerfile");
  const styles = read("src/styles.css");
  const devEnv = read(".env.development");
  const prodEnv = read(".env.production");
  const localCompose = fs.readFileSync(path.join(REPO_ROOT, "local", "docker-compose.yml"), "utf8");
  const dockerComposeUat = fs.readFileSync(path.join(REPO_ROOT, "local", "docker-compose.uat.yml"), "utf8");
  const backendDocker = fs.readFileSync(
    path.join(REPO_ROOT, "backend", "api", "api-bff", "src", "main", "resources", "application-docker.yml"),
    "utf8",
  );
  const backendDev = fs.readFileSync(
    path.join(REPO_ROOT, "backend", "api", "api-bff", "src", "main", "resources", "application-dev.yml"),
    "utf8",
  );

  assert.ok(config.includes("careAuthMode"));
  assert.ok(config.includes('resolveCareAuthMode(import.meta.env.VITE_PATIENT_PORTAL_AUTH_MODE)'));
  assert.ok(config.includes('console.info(`Care auth mode: ${resolvedCareAuthMode}`)'));
  assert.ok(dockerfile.includes("ARG VITE_PATIENT_PORTAL_AUTH_MODE"));
  assert.ok(dockerfile.includes("ENV VITE_PATIENT_PORTAL_AUTH_MODE=${VITE_PATIENT_PORTAL_AUTH_MODE}"));
  assert.ok(devEnv.includes("VITE_PATIENT_PORTAL_AUTH_MODE=DEV_OTP"));
  assert.ok(prodEnv.includes("VITE_PATIENT_PORTAL_AUTH_MODE=OTP"));
  assert.ok(localCompose.includes("CLINIC_PATIENT_PORTAL_AUTH_MODE: ${CLINIC_PATIENT_PORTAL_AUTH_MODE:-}"));
  assert.ok(localCompose.includes("VITE_PATIENT_PORTAL_AUTH_MODE: ${VITE_PATIENT_PORTAL_AUTH_MODE:-}"));
  assert.ok(dockerComposeUat.includes("CLINIC_PATIENT_PORTAL_AUTH_MODE: ${CLINIC_PATIENT_PORTAL_AUTH_MODE:-}"));
  assert.ok(dockerComposeUat.includes("VITE_PATIENT_PORTAL_AUTH_MODE: ${VITE_PATIENT_PORTAL_AUTH_MODE:-}"));
  assert.ok(backendDocker.includes("mode: ${CLINIC_PATIENT_PORTAL_AUTH_MODE:DEV_OTP}"));
  assert.ok(backendDev.includes("mode: ${CLINIC_PATIENT_PORTAL_AUTH_MODE:DEV_OTP}"));
  assert.ok(app.includes('path="/patient/request-access"'));
  assert.ok(app.includes('parsed?.mode === "access"'));
  assert.ok(pages.includes("ACCESS_APPROVAL"));
  assert.ok(pages.includes("PatientAccessRequestPage"));
  assert.ok(pages.includes('Request Access'));
  assert.ok(pages.includes('Temporary access code'));
  assert.ok(pages.includes('Invite-controlled access for approved patients. Request access if you have not been approved yet.'));
  assert.ok(accessValidation.includes('sanitizePatientAccessCodeInput'));
  assert.ok(pages.includes('postPatientPortalAccessLogin'));
  assert.ok(pages.includes('postPatientPortalAccessRequest'));
  assert.ok(pages.includes('mode: "access"'));
  assert.ok(pages.includes('CareEntrySecurityStrip mode="access"'));
  assert.ok(pages.includes('isDevOtpMode'));
  assert.ok(shell.includes('Controlled access sign-in'));
  assert.ok(api.includes('mode: "otp" | "access"'));
  assert.ok(api.includes('PatientPortalAccessLoginResponse'));
  assert.ok(api.includes('postPatientPortalAccessLogin'));
  assert.ok(api.includes('postPatientPortalAccessRequest'));
  assert.ok(styles.includes('.care-login-layout > * {'));
  assert.ok(styles.includes('min-width: 0;'));
  assert.ok(styles.includes('.care-login-hero h1 {'));
  assert.ok(styles.includes('white-space: normal;'));
  assert.ok(styles.includes('overflow-wrap: anywhere;'));
  assert.ok(styles.includes('.care-login-illustration__scene {'));
  assert.ok(styles.includes('.care-login-illustration__panel {'));
  assert.ok(styles.includes('.care-login-illustration__mini-panel {'));
});

test("access approval mode keeps OTP controls behind configuration", () => {
  const pages = read("src/pages/patient/PatientPortalPages.tsx");
  const accessValidation = read("src/pages/patient/patientAccessValidation.js");
  assert.ok(pages.includes('setAccessAttempted(true)'));
  assert.ok(accessValidation.includes('already pending'));
  assert.ok(accessValidation.includes('Select the correct clinic or hospital before signing in.'));
  assert.ok(pages.includes('Invite-controlled access'));
});
