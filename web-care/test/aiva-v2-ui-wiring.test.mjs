import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const page = fs.readFileSync(path.join(root, "src/pages/patient/PatientPortalPages.tsx"), "utf8");
const api = fs.readFileSync(path.join(root, "src/api/patientPortal.ts"), "utf8");
const config = fs.readFileSync(path.join(root, "src/config.ts"), "utf8");

test("care page keeps legacy as the default and exposes opt-in V2 only when enabled", () => {
  assert.match(page, /useState<"legacy" \| "v2">\("legacy"\)/);
  assert.match(page, /careConfig\.aivaV2Enabled/);
  assert.match(config, /VITE_AIVA_V2_ENABLED/);
});

test("V2 text uses its HTTP endpoint and carries the stable conversation id", () => {
  assert.match(page, /postPatientPortalAivaV2Message\(/);
  assert.match(page, /conversationId: v2ConversationId/);
  assert.match(api, /\/api\/patient-portal\/aiva-v2\/message/);
  assert.doesNotMatch(api.slice(api.indexOf("postPatientPortalAivaV2Message")), /sessionToken.*searchParams/);
});

test("legacy text remains on the CareAI HTTP path and V2 never falls back silently", () => {
  assert.match(page, /\/api\/patient-portal\/careai\/message/);
  assert.match(page, /AIVA V2 is temporarily unavailable/);
  assert.match(page, /AIVA V2 is not enabled/);
  assert.match(page, /aivaEngine === "v2"/);
});

test("V2 mode is text-only and unsupported quick actions are not sent through V2", () => {
  assert.match(page, /voiceDisabledForV2/);
  assert.match(page, /Voice is not enabled for AIVA V2 POC yet/);
  assert.match(page, /AIVA_CHAT_QUICK_ACTIONS\.filter\(\(action\) => action\.label === "Book appointment"\)/);
});

test("V2 reset discards the conversation identity and projection", () => {
  assert.match(page, /setV2Messages\(\[\]\)/);
  assert.match(page, /setV2ConversationId\(null\)/);
  assert.match(page, /setV2Technical\(null\)/);
});
