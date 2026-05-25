# Local Open-Source Voice Harness

This harness keeps the clinic voice test fully local for STT and TTS while leaving Gemini as the primary LLM.

## Architecture

`web-admin Voice Test`
-> `clinic-management-api`
-> `faster-whisper` for transcription
-> `Gemini` for assistant response
-> `piper-tts` for speech synthesis
-> browser playback

Both paths reuse the same backend orchestration:

- `POST /api/voice/test`
- `WS /ws/voice/test`

The websocket path buffers microphone chunks until `audio.end`, then reuses the file-based STT -> LLM -> TTS flow.

## Local Docker services

Defined in `local/docker-compose.yml`:

- `faster-whisper`
- `piper-tts`
- `clinic-management-api`
- `web-admin`

## Environment variables

Set in `local/.env.full-docker`:

- `VOICE_STT_PROVIDER_ORDER=faster-whisper,mock`
- `FASTER_WHISPER_BASE_URL=http://faster-whisper:8000`
- `FASTER_WHISPER_MODEL=base`
- `FASTER_WHISPER_LANGUAGE=en`
- `VOICE_LLM_PROVIDER_ORDER=gemini,groq,mock`
- `VOICE_TTS_PROVIDER_ORDER=piper,mock`
- `PIPER_TTS_BASE_URL=http://piper-tts:8001`
- `PIPER_DEFAULT_VOICE=en_US-lessac-medium`
- `PIPER_ENGLISH_VOICE=en_US-lessac-medium`
- `PIPER_HINDI_VOICE=hi_IN-rohan-medium`
- `PIPER_ALLOW_FALLBACK_VOICE=true`
- `PIPER_MODEL_HOST_DIR=./voice/piper/models`

Hosted providers remain supported:

- Deepgram
- ElevenLabs

Mock fallback remains enabled when local services are unavailable.

## Start the stack

From `local/`:

```bash
docker compose \
  --env-file .env.full-docker \
  --profile api \
  --profile frontend \
  up -d --build clinic-management-api web-admin faster-whisper piper-tts
```

## Test the file API

Use the browser page at `/ai/voice-test` and the `File Test` tab.

Expected provider trace in a healthy local run:

- `STT: Faster Whisper`
- `LLM: Gemini`
- `TTS: Piper`

If a local dependency is unavailable, the UI warns when mock fallback is used.

## Test the websocket flow

Use the `Live WebSocket Test` tab:

1. Start session
2. Start mic
3. Stop mic
4. Wait for transcript and assistant response
5. Play returned WAV audio if Piper is available

## Troubleshooting

### Faster-Whisper model download is slow

The first transcription call downloads the configured model into the Docker volume. This is expected.

### CPU transcription is slow

The default configuration uses CPU-safe inference. Reduce latency by using a smaller model or a stronger host CPU.

### Piper voice is missing

The local stack mounts Piper models from:

- host path: `local/voice/piper/models/`
- container path: `/models/piper`

For Hindi TTS, place both files in `local/voice/piper/models/`:

- `hi_IN-rohan-medium.onnx`
- `hi_IN-rohan-medium.onnx.json`

Then set:

- `PIPER_HINDI_VOICE=hi_IN-rohan-medium`

The service can still synthesize English with `PIPER_ENGLISH_VOICE` / `PIPER_DEFAULT_VOICE`.
If `PIPER_HINDI_VOICE` is blank or the Hindi model files are unavailable, the UI shows that Hindi TTS is not configured. Text responses still work, and audio can fall back to the default voice when `PIPER_ALLOW_FALLBACK_VOICE=true`.

`piper-tts` can also download the configured voice model into `/models/piper` on first synthesis request if direct model placement is not used.

Check:

- `PIPER_DEFAULT_VOICE`
- `PIPER_ENGLISH_VOICE`
- `PIPER_HINDI_VOICE`
- container logs for `piper-tts`

### Browser microphone permissions

The websocket harness depends on `MediaRecorder` and browser mic permissions. If mic access is denied, the file-based path still works.

### Local service unavailable

The backend falls back to mock STT or mock TTS instead of failing the entire voice loop when possible.
