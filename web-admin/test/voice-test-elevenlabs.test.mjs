import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("voice test page shows selected TTS provider and ElevenLabs diagnostic controls", () => {
  const api = readSource("api/clinicApi.ts");
  const page = readSource("pages/ai/VoiceTestPage.tsx");

  assert.ok(api.includes("runVoiceElevenLabsTest"));
  assert.ok(api.includes("VoiceTtsDiagnosticResponse"));
  assert.ok(api.includes("VoiceTtsProviderStatus"));
  assert.ok(page.includes("Patient/Voice Test Provider Health"));
  assert.ok(page.includes("Test ElevenLabs TTS"));
  assert.ok(page.includes("voiceStatus?.providerTrace?.ttsProvider || \"piper\""));
  assert.ok(page.includes("voiceStatus?.elevenlabs?.lastTest"));
  assert.ok(page.includes("TTS: ${formatProvider(voiceStatus?.providerTrace?.ttsProvider || \"piper\")}"));
});
