# Realtime Voice Local Setup

## Prerequisites
- Python 3.12+
- `ffmpeg`
- `piper` binary in PATH (`piper-tts` package in runtime image)
- Optional GPU path: NVIDIA drivers + CUDA runtime

## Setup
```bash
cd backend/realtime/realtime-voice-runtime-python
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
cp .env.example .env
```

## CPU-first run (recommended)
```bash
export VOICE_STT_PROVIDER=whisper-cpp-server
export VOICE_STT_URL=http://localhost:8092
export VOICE_STT_MODEL=base.en
export VOICE_STT_DEVICE=cpu
export VOICE_ENABLE_GPU=false
uvicorn app:app --host 0.0.0.0 --port 8091
```

## Optional GPU run
```bash
export VOICE_ENABLE_GPU=true
export VOICE_STT_DEVICE=cuda
uvicorn app:app --host 0.0.0.0 --port 8091
```
If CUDA is unavailable, runtime falls back to CPU behavior and reports degraded/starting state.

## Local checks
```bash
curl -s http://localhost:8091/health
curl -s http://localhost:8091/ready
curl -s http://localhost:8091/metrics/summary
curl -s http://localhost:8091/v1/tts/validate
```

## Troubleshooting
- `sttReady=false` with `ctranslate2` errors:
  - switch to `VOICE_STT_PROVIDER=whisper-cpp-server` (default) and run `whisper-stt` container.
- `ttsReady=false` / `piper not found`:
  - verify `which piper` inside runtime container.
  - verify `PIPER_CLI` points to installed binary.
- `ttsReady=false` / model missing:
  - set `PIPER_MODEL` to an absolute `.onnx` path, or place model under `VOICE_TTS_MODEL_DIR`.
  - keep `VOICE_TTS_AUTO_DOWNLOAD=true` to allow startup download for missing model files.
- `model cache not writable`: set `VOICE_MODEL_CACHE_DIR` to writable path.
- `aiOrchestrationReachable=false`: verify `VOICE_AI_ORCHESTRATION_URL` and Docker host routing.
