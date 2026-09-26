import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const page = fs.readFileSync(path.join(root, "src/pages/patient/PatientPortalPages.tsx"), "utf8");
const api = fs.readFileSync(path.join(root, "src/api/patientPortal.ts"), "utf8");
const config = fs.readFileSync(path.join(root, "src/config.ts"), "utf8");

test("care page uses V2 by default with an internal configuration-only rollback", () => {
  assert.match(page, /const aivaEngine: "legacy" \| "v2" = careConfig\.aivaV2Enabled \? "v2" : "legacy"/);
  assert.match(page, /VITE_AIVA_V2_ENABLED=false/);
  assert.match(page, /careConfig\.aivaV2Enabled/);
  assert.match(config, /VITE_AIVA_V2_ENABLED/);
  assert.match(config, /VITE_AIVA_V2_VOICE_ENABLED/);
  assert.match(config, /trimEnv\(import\.meta\.env\.VITE_AIVA_V2_ENABLED\)\.toLowerCase\(\) !== "false"/);
  assert.match(config, /aivaV2VoiceEnabled:/);
  assert.doesNotMatch(page, /AIVA Engine|V2 POC/);
  assert.doesNotMatch(page, /selectAivaEngine/);
});

test("V2 is the default build-time engine and voice capability", () => {
  const dockerfile = fs.readFileSync(path.join(root, "Dockerfile"), "utf8");
  assert.match(dockerfile, /ARG VITE_AIVA_V2_ENABLED=true/);
  assert.match(dockerfile, /ARG VITE_AIVA_V2_VOICE_ENABLED=true/);
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

test("V2 voice is independently feature-gated and unsupported quick actions are not sent through V2", () => {
  assert.match(page, /const voiceDisabledForV2 = aivaEngine === "v2" && !careConfig\.aivaV2VoiceEnabled/);
  assert.match(page, /Voice is currently unavailable/);
  assert.match(page, /AIVA_CHAT_QUICK_ACTIONS\.filter\(\(action\) => action\.label === "Book appointment"\)/);
});

test("Care voice uses the existing socket and sends the selected engine only at session start", () => {
  assert.match(page, /buildPatientPortalVoiceWebSocketUrl\(portalSession\)/);
  assert.match(page, /type: "session\.start"[\s\S]*engine: aivaEngine[\s\S]*resumeSessionId/);
  assert.match(page, /appendVoicePatientMessage[\s\S]*aivaEngine === "v2" \? setV2Messages : setLegacyMessages/);
  assert.match(page, /appendVoiceAssistantMessage[\s\S]*aivaEngine === "v2" \? setV2Messages : setLegacyMessages/);
});

test("V2 reset discards the conversation identity and projection", () => {
  assert.match(page, /setV2Messages\(\[\]\)/);
  assert.match(page, /setV2ConversationId\(null\)/);
  assert.match(page, /setV2Technical\(null\)/);
});

test("V2 waiting feedback is delayed, transient, and operation-aware", () => {
  assert.match(page, /const AIVA_V2_TEXT_PROGRESS_DELAY_MS = 800/);
  assert.match(page, /setV2WaitingMessage\(aivaV2WaitingCopy\(trimmed\)\)/);
  assert.match(page, /role=\"status\"/);
  assert.match(page, /Let me check available doctors\./);
  assert.match(page, /I’m checking available slots\./);
  assert.match(page, /मैं उपलब्ध स्लॉट देख रही हूँ।/);
  assert.match(page, /setV2WaitingMessage\(null\)/);
});

test("voice assistant text clears processing independently of audio playback", () => {
  assert.match(page, /if \(type === "assistant\.text"\)[\s\S]*?updateVoiceStatus\("session_started"\)/);
  assert.match(page, /final assistant response is already visible/);
});

test("terminal voice responses wait for playback then close without reconnecting", () => {
  assert.match(page, /conversation\.terminal/);
  assert.match(page, /audio\.playback\.complete/);
  assert.match(page, /TERMINAL_AUDIO_PLAYBACK_COMPLETE/);
  assert.match(page, /voiceServerClosedRef/);
  assert.match(page, /if \(voiceServerClosedRef\.current\)/);
});
