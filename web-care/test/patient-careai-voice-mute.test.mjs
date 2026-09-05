import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("patient careai voice mute suppresses microphone capture instead of muting playback only", () => {
  const source = readSource("pages/patient/PatientPortalPages.tsx");

  assert.ok(source.includes("voiceMutedRef"));
  assert.ok(source.includes("handleVoiceToggleMute"));
  assert.ok(source.includes("MIC_MUTED_STOPPING_RECORDING"));
  assert.ok(source.includes("MIC_MUTED_START_BLOCKED"));
  assert.ok(source.includes("Microphone muted. Voice session remains connected."));
  assert.ok(source.includes("Mute mic"));
  assert.ok(source.includes("Unmute mic"));
  assert.ok(source.includes("Mic muted"));
  assert.ok(!source.includes("muted={voiceMuted}"));
  assert.ok(!source.includes("voiceAudioElementRef.current.muted = voiceMuted"));
});
