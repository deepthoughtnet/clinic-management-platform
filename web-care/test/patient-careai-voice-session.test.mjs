import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("Care AIVA Talk starts the actual voice turn and surfaces microphone or websocket failures", () => {
  const pages = readSource("pages/patient/PatientPortalPages.tsx");

  assert.match(pages, /await startVoiceMic\(\{ automatic: true, reason: "talk" \}\);/);
  assert.match(pages, /async function resumeVoiceMicFromActiveStream\(/);
  assert.match(pages, /function beginVoiceRecorderSession\(/);
  assert.match(pages, /setVoiceConversationMode\("continuous"\);/);
  assert.match(pages, /setVoiceConversationMode\("manual"\);/);
  assert.match(pages, /function handleVoiceAudioPlaybackStarted\(\) \{[\s\S]*voiceBargeInRequestedRef\.current = false;[\s\S]*\}/);
  assert.match(pages, /async function handleVoiceAudioPlaybackPaused\(\) \{[\s\S]*appendVoiceEvent\("BARGE_IN_RECOVERED"\);[\s\S]*resumeVoiceMicFromActiveStream\(\{ automatic: true, reason: "barge_in" \}\);[\s\S]*\}/);
  assert.match(pages, /function handleVoiceAudioPlaybackEnded\(\) \{[\s\S]*setVoiceConversationMode\("continuous"\);[\s\S]*scheduleVoiceListeningResume\("assistant_audio_complete", 0\);[\s\S]*\}/);
  assert.match(pages, /function handleVoiceStartTurn\(\) \{[\s\S]*setVoiceConversationMode\("manual"\);[\s\S]*await resumeVoiceMicFromActiveStream\(\{ automatic: true, reason: "manual_turn" \}\);[\s\S]*\}/);
  assert.match(pages, /const unlockAudio = new Audio\(PATIENT_VOICE_AUDIO_UNLOCK_SRC\);/);
  assert.match(pages, /unlockAudio\.muted = true;/);
  assert.match(pages, /voiceAudioElementRef\.current\.muted = false;/);
  assert.match(pages, /voiceAudioElementRef\.current\.volume = 1;/);
  assert.match(pages, /ref=\{voiceAudioElementRef\}/);
  assert.match(pages, /onPlay=\{\(\) => \{[\s\S]*voiceAudioKindRef\.current === "progress"[\s\S]*handleVoiceProgressAudioPlaybackStarted\(\)[\s\S]*handleVoiceAudioPlaybackStarted\(\)[\s\S]*\}\}/);
  assert.match(pages, /onPause=\{handleVoiceAudioPlaybackPaused\}/);
  assert.match(pages, /onEnded=\{handleVoiceAudioPlaybackEnded\}/);
  assert.match(pages, /onError=\{handleVoiceAudioPlaybackError\}/);
  assert.match(pages, /maybeResumeListeningAfterUnmute\(\);/);
  assert.match(pages, /Conversation mode/);
  assert.match(pages, /Turn ID/);
  assert.match(pages, /Interruptions/);
  assert.match(pages, /AIVA response is ready\./);
  assert.match(pages, /Playing AIVA response…/);
  assert.match(pages, /Interrupting AIVA so you can continue\./);
  assert.match(pages, /Microphone permission is required for voice conversations\./);
  assert.match(pages, /No microphone device was found on this browser\./);
  assert.match(pages, /Microphone access is busy or unavailable right now\./);
  assert.match(pages, /Voice service is currently unavailable\./);
  assert.match(pages, /Voice conversation could not start\./);
  assert.match(pages, /Voice reply is unavailable\. You can still read AIVA's response\./);
  assert.match(pages, /BARGE_IN_REQUESTED/);
  assert.match(pages, /BARGE_IN_RECOVERED/);
  assert.match(pages, /AUDIO_PLAY_PAUSED/);
  assert.match(pages, /type === "turn\.progress"/);
  assert.match(pages, /updateVoiceStatus\("waiting_for_tool"\)/);
  assert.match(pages, /TURN_PROGRESS/);
  assert.match(pages, /assistant\.progress\.audio\.chunk/);
  assert.match(pages, /assistant\.progress\.audio\.end/);
  assert.match(pages, /PROGRESS_AUDIO_SUPPRESSED_FINAL_READY/);
  assert.match(pages, /FINAL_AUDIO_QUEUED_AFTER_PROGRESS/);
  assert.match(pages, /voiceAudioKindRef\.current === "progress"/);
  assert.match(pages, /speechEndToTranscriptMs/);
  assert.match(pages, /decisionToTtsFirstAudioMs/);
  assert.match(pages, /bargeInStopLatencyMs/);
});
