# Realtime AI Voice Gateway

## Architecture
1. Clinic Platform APIs (tenant and RBAC system-of-record)
2. AI orchestration platform (Gemini/Groq abstracted)
3. Realtime voice runtime (Python STT/TTS worker)
4. STT/TTS adapters (open-source first)
5. Future telephony/WebRTC/SIP edge

## Runtime Packaging
- Python runtime located at `backend/realtime/realtime-voice-runtime-python`
- Deterministic dependencies pinned in `requirements.txt`
- Dockerized with non-root execution and healthcheck support
- Model cache persisted via `VOICE_MODEL_CACHE_DIR`

## STT/TTS Runtime
- Default STT provider: `whisper-cpp-server` (external process, HTTP call from runtime)
- Optional STT provider: `faster-whisper` (lazy-loaded only when explicitly selected)
- TTS provider: `piper` via CLI (`/v1/tts/synthesize`)
- Java gateway invokes runtime via provider SPI wrappers, not direct model code

### Why whisper.cpp server is default
- Avoids `ctranslate2` native runtime instability in constrained Docker hosts.
- Keeps runtime process lightweight and isolates STT model execution.
- Enables independent STT scaling and replacement without changing Java orchestration contracts.

## Health and Readiness
Runtime endpoints:
- `GET /health`
- `GET /ready`
- `GET /metrics/summary`
- Backward compatible `GET /v1/voice/health`

Status values:
- `HEALTHY`
- `DEGRADED`
- `STARTING`
- `FAILED`

Readiness checks include:
- STT provider readiness (including `VOICE_STT_URL` for whisper.cpp)
- TTS provider readiness (piper binary + model presence)
- model availability
- model cache writability
- AI orchestration reachability

`/ready` includes details:
- `sttProvider`, `sttUrl`, `sttDetail`
- `ttsProvider`, `ttsDetail`
- `aiOrchestrationUrl`

## Model Lifecycle
- Startup validation ensures cache and TTS model directories are writable.
- whisper.cpp readiness checks health endpoint at `VOICE_STT_URL`.
- Piper requires an accessible `.onnx` model path (from `PIPER_MODEL` or `VOICE_TTS_MODEL_DIR`).
- If `VOICE_TTS_AUTO_DOWNLOAD=true`, missing Piper model files are downloaded during startup.
- Runtime reports exact missing model/binary details; no fake-ready behavior.

## CPU-first and GPU-readiness
CPU defaults:
- `VOICE_STT_MODEL=base`
- `VOICE_STT_DEVICE=cpu`
- bounded worker threads

GPU hooks:
- `VOICE_ENABLE_GPU=true`
- `VOICE_STT_DEVICE=cuda`
- graceful fallback to CPU on unavailable GPU

## Resource Governance
- `VOICE_MAX_ACTIVE_SESSIONS`
- `VOICE_WS_MAX_SESSIONS`
- `VOICE_SESSION_IDLE_TIMEOUT`
- `VOICE_MAX_TRANSCRIPT_BUFFER`
- `VOICE_MAX_AUDIO_CHUNK_BYTES`

Runtime enforces bounded active sessions and chunk size limits, plus idle cleanup.

## Observability
Runtime summary includes:
- status and uptime
- active sessions
- STT/TTS readiness
- model readiness
- STT/TTS avg latency
- STT/TTS failure counts
- model load latency
- disconnect counters

Java `GET /api/realtime-ai/summary` now includes runtime diagnostics for Administration -> Realtime AI.

## Security and Safety
- no raw audio persistence by default
- bounded upload size
- no provider secrets logged
- graceful shutdown clears in-memory runtime sessions
- tenant isolation maintained in Java persistence and websocket auth paths

## Runtime Status Semantics
- `HEALTHY`: STT + TTS + model + AI orchestration reachable.
- `DEGRADED`: runtime alive, but one or more external dependencies unavailable.
- `FAILED`: fatal startup config issues (for example non-writable model dirs).
- `STARTING`: transient warmup state only; runtime should not remain stuck indefinitely.

## Scaling Guidance
- Small clinic: 2 vCPU / 4 GB, base model, ~10 active sessions
- Medium clinic: 4 vCPU / 8 GB, base/small, ~30 active sessions
- Multi-tenant node: 8+ vCPU / 16+ GB, horizontal shard or runtime pool

## Future Roadmap
- isolated STT and TTS microservices (optional split)
- streaming TTS chunk playback
- telephony bridges (LiveKit/Asterisk/FreeSWITCH)
- multilingual runtime routing
