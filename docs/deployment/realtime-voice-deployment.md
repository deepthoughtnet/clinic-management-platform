# Realtime Voice Deployment v1

## Deployment architecture
- Java API/BFF remains control plane and system-of-record.
- Python runtime (`realtime-voice-gateway`) handles STT/TTS execution.
- AI text generation still flows through existing AI orchestration layer.

## Docker image
Build context:
- `backend/realtime/realtime-voice-runtime-python`

Runtime properties:
- non-root user
- healthcheck enabled
- model cache volume (`/var/lib/voice-models`)
- CPU-first defaults

## Docker Compose
Service added in `local/docker-compose.yml`:
- `whisper-stt` (whisper.cpp HTTP server, CPU-first)
- `realtime-voice-gateway` on port `8091`

Key environment variables:
- `VOICE_STT_PROVIDER`, `VOICE_STT_URL`, `VOICE_STT_MODEL`, `VOICE_STT_DEVICE`
- `VOICE_TTS_PROVIDER`, `VOICE_TTS_VOICE`
- `VOICE_TTS_MODEL_DIR`, `VOICE_TTS_AUTO_DOWNLOAD`, `PIPER_MODEL`, `PIPER_CLI`
- `VOICE_AI_ORCHESTRATION_URL`
- `VOICE_MAX_ACTIVE_SESSIONS`, `VOICE_SESSION_IDLE_TIMEOUT`
- `VOICE_MODEL_CACHE_DIR`, `VOICE_ENABLE_GPU`

Recommended local defaults:
- `VOICE_STT_PROVIDER=whisper-cpp-server`
- `VOICE_STT_URL=http://whisper-stt:8080`
- `VOICE_STT_MODEL=base.en`
- `VOICE_TTS_PROVIDER=piper`
- `PIPER_MODEL=/var/lib/voice-models/piper/en_US-lessac-medium.onnx`

## Reverse proxy guidance
- Route `/ws/voice/session/*` to Java API/BFF websocket endpoint.
- Route runtime internal calls only within private network where possible.
- Expose runtime externally only if operationally required.

For local Docker host -> Java backend routing:
- add `extra_hosts: ["host.docker.internal:host-gateway"]`
- use `VOICE_AI_ORCHESTRATION_URL=http://host.docker.internal:8089/actuator/health`

## CPU sizing guidance
- Small clinic: 2 vCPU, 4 GB RAM, `base` model, <= 10 concurrent sessions.
- Medium clinic: 4 vCPU, 8 GB RAM, `base/small`, <= 30 concurrent sessions.
- Multi-tenant node: 8+ vCPU, 16+ GB RAM, shard by tenant groups.

## Latency expectations (CPU)
- STT finalize: typically 300ms-1800ms depending on chunk size/model.
- TTS synthesis: typically 150ms-1200ms depending on voice/model and text length.
- End-to-end roundtrip depends on AI orchestration latency.

## GPU optional guidance
- Enable with `VOICE_ENABLE_GPU=true`, `VOICE_STT_DEVICE=cuda`.
- Keep CPU fallback enabled for safety.
- Validate CUDA container/runtime before production cutover.

## Model cache handling
- Persist `VOICE_MODEL_CACHE_DIR` volume.
- Ensure Piper model directory exists (`VOICE_TTS_MODEL_DIR`).
- Keep `VOICE_TTS_AUTO_DOWNLOAD=true` for automatic model bootstrap when cache is empty.
- Runtime reports explicit missing-model errors when Piper `.onnx` is absent.
- Startup fails readiness if cache/model dirs are not writable.

## Operational safety
- Idle session cleanup worker enabled.
- Max active sessions enforced.
- Max chunk size enforced.
- Graceful shutdown clears runtime sessions.

## Troubleshooting
- `libctranslate2 ... cannot enable executable stack`:
  - use `whisper-cpp-server` provider instead of embedded `faster-whisper`.
- `exec: "piper": executable file not found`:
  - ensure Piper binary is present and `PIPER_CLI` resolves in container.
- `STARTING` forever:
  - inspect `/ready` details for `sttDetail` and `ttsDetail`.
- `aiOrchestrationReachable=false`:
  - verify Java backend on `8089` and `host.docker.internal` mapping.

## Backup considerations
- Runtime is stateless aside from model cache; back up cache optionally for faster cold starts.
- Session/transcript system-of-record remains in Java persistence layer.
