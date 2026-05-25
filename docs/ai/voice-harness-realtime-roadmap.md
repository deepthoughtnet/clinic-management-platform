# Voice Harness Realtime Roadmap

## Current state
- File test supports local STT -> Gemini -> local TTS.
- Live websocket test uses buffered browser audio chunks and chunked assistant audio playback.
- RBAC, tenant scoping, and mock fallbacks remain intact.

## Current VAD foundation
- Browser-side RMS/peak monitoring drives:
  - microphone level meter
  - speech detected / silence detected indicators
  - silence warning for file recordings
  - live silence timeout auto-end
- Backend exposes a lightweight `VoiceActivityDetector` abstraction with a `NoOpVoiceActivityDetector` placeholder.
- Current live status reports `FRONTEND_RMS_READY` to make the active VAD mode explicit.

## Why Silero VAD next
- Silero VAD is a strong next step for local realtime speech gating.
- It is CPU-friendly, fast, multilingual, and works well for low-latency speech detection.
- It can run as:
  - a local sidecar service
  - an ONNX-backed backend helper
  - a future browser-assisted hybrid flow

## Recommended next phases
1. Add optional browser VAD package integration for better speech start/end confidence.
2. Add a local Silero VAD sidecar and implement a backend `VoiceActivityDetector` adapter.
3. Move live websocket audio transport to binary frames instead of base64 JSON.
4. Add partial transcript events for streaming STT.
5. Add interruption / barge-in handling for live assistant playback.
6. Add telephony adapter integration after the websocket harness is stable.

## Production-minded design notes
- Keep live audio transport chunked even if websocket buffers are increased.
- Do not log JWTs, raw audio bytes, or base64 payloads.
- Keep tenant resolution and voice session authorization at handshake time.
- Preserve mock STT/TTS fallbacks for local testing when providers are unavailable.
