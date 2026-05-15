from __future__ import annotations

import base64
import io
import json
import logging
import os
import subprocess
import threading
import time
import uuid
import wave
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from urllib import error, request

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

LOG_LEVEL = os.getenv("VOICE_LOG_LEVEL", "INFO").upper()
logging.basicConfig(level=LOG_LEVEL, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("voice-runtime")


class RuntimeConfig(BaseModel):
    host: str = Field(default=os.getenv("VOICE_GATEWAY_HOST", "0.0.0.0"))
    port: int = Field(default=int(os.getenv("VOICE_GATEWAY_PORT", "8091")))
    stt_provider: str = Field(default=os.getenv("VOICE_STT_PROVIDER", "whisper-cpp-server"))
    stt_url: str = Field(default=os.getenv("VOICE_STT_URL", "http://whisper-stt:8080"))
    stt_model: str = Field(default=os.getenv("VOICE_STT_MODEL", "base.en"))
    stt_language: str = Field(default=os.getenv("VOICE_STT_LANGUAGE", "en"))
    stt_device: str = Field(default=os.getenv("VOICE_STT_DEVICE", "cpu"))
    stt_timeout_seconds: int = Field(default=int(os.getenv("VOICE_STT_TIMEOUT_SECONDS", "30")))
    tts_provider: str = Field(default=os.getenv("VOICE_TTS_PROVIDER", "piper"))
    tts_voice: str = Field(default=os.getenv("VOICE_TTS_VOICE", "en_US-lessac-medium"))
    tts_model_dir: str = Field(default=os.getenv("VOICE_TTS_MODEL_DIR", "/var/lib/voice-models/piper"))
    tts_auto_download: bool = Field(default=os.getenv("VOICE_TTS_AUTO_DOWNLOAD", "true").lower() == "true")
    ai_orchestration_url: str = Field(default=os.getenv("VOICE_AI_ORCHESTRATION_URL", "http://api-bff:8089/actuator/health"))
    ws_max_sessions: int = Field(default=int(os.getenv("VOICE_WS_MAX_SESSIONS", "200")))
    max_active_sessions: int = Field(default=int(os.getenv("VOICE_MAX_ACTIVE_SESSIONS", "100")))
    max_transcript_buffer: int = Field(default=int(os.getenv("VOICE_MAX_TRANSCRIPT_BUFFER", "96")))
    session_idle_timeout: int = Field(default=int(os.getenv("VOICE_SESSION_IDLE_TIMEOUT", "300")))
    max_audio_chunk_bytes: int = Field(default=int(os.getenv("VOICE_MAX_AUDIO_CHUNK_BYTES", str(2 * 1024 * 1024))))
    model_cache_dir: str = Field(default=os.getenv("VOICE_MODEL_CACHE_DIR", "/var/lib/voice-models"))
    enable_gpu: bool = Field(default=os.getenv("VOICE_ENABLE_GPU", "false").lower() == "true")
    worker_threads: int = Field(default=int(os.getenv("VOICE_WORKER_THREADS", "2")))


CONFIG = RuntimeConfig()


@dataclass
class SessionBuffer:
    chunks: List[bytes] = field(default_factory=list)
    transcripts: List[str] = field(default_factory=list)
    last_touch: float = field(default_factory=time.time)


@dataclass
class RuntimeState:
    started_at: float = field(default_factory=time.time)
    status: str = "STARTING"
    stt_ready: bool = False
    tts_ready: bool = False
    model_ready: bool = False
    ai_orchestration_reachable: bool = False
    startup_warnings: List[str] = field(default_factory=list)
    startup_errors: List[str] = field(default_factory=list)
    total_sessions_created: int = 0
    total_disconnects: int = 0
    stt_latency_ms: List[int] = field(default_factory=list)
    tts_latency_ms: List[int] = field(default_factory=list)
    stt_failures: int = 0
    tts_failures: int = 0
    model_load_ms: int = 0
    stt_detail: str = ""
    tts_detail: str = ""


SESSIONS: Dict[str, SessionBuffer] = defaultdict(SessionBuffer)
SESSIONS_LOCK = threading.Lock()
STATE = RuntimeState()
SHUTDOWN = False

app = FastAPI(title="Realtime Voice Runtime", version="1.2")


def _bounded_append(values: List[int], v: int, max_len: int = 1000) -> None:
    values.append(max(0, int(v)))
    if len(values) > max_len:
        del values[: len(values) - max_len]


def _avg(values: List[int]) -> int:
    return int(sum(values) / len(values)) if values else 0


def _validate_config() -> None:
    if CONFIG.max_active_sessions <= 0:
        STATE.startup_errors.append("VOICE_MAX_ACTIVE_SESSIONS must be > 0")
    if CONFIG.max_transcript_buffer <= 0:
        STATE.startup_errors.append("VOICE_MAX_TRANSCRIPT_BUFFER must be > 0")
    if CONFIG.session_idle_timeout < 30:
        STATE.startup_warnings.append("VOICE_SESSION_IDLE_TIMEOUT is very low; expected >= 30 seconds")
    if CONFIG.enable_gpu and CONFIG.stt_device == "cpu":
        STATE.startup_warnings.append("VOICE_ENABLE_GPU=true but VOICE_STT_DEVICE=cpu; CPU fallback active")


def _prepare_cache() -> None:
    root = Path(CONFIG.model_cache_dir)
    root.mkdir(parents=True, exist_ok=True)
    if not os.access(root, os.W_OK):
        STATE.startup_errors.append(f"Model cache dir not writable: {root}")
    tts_dir = Path(CONFIG.tts_model_dir)
    tts_dir.mkdir(parents=True, exist_ok=True)
    if not os.access(tts_dir, os.W_OK):
        STATE.startup_errors.append(f"TTS model dir not writable: {tts_dir}")


def _decode_pcm_wav(audio_b64: str) -> np.ndarray:
    raw = base64.b64decode(audio_b64)
    if raw[:4] == b"RIFF":
        with wave.open(io.BytesIO(raw), "rb") as wf:
            frames = wf.readframes(wf.getnframes())
            return np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0


def _pcm_to_wav_bytes(samples: np.ndarray, sample_rate: int = 16000) -> bytes:
    pcm = (samples * 32768.0).clip(-32768, 32767).astype(np.int16).tobytes()
    with io.BytesIO() as out_wav:
        with wave.open(out_wav, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(sample_rate)
            wf.writeframes(pcm)
        return out_wav.getvalue()


def _json_get(url: str, timeout: int) -> Tuple[int, Dict[str, Any]]:
    req = request.Request(url, method="GET")
    with request.urlopen(req, timeout=timeout) as resp:  # noqa: S310
        payload = resp.read().decode("utf-8") if resp.length != 0 else "{}"
        return resp.status, json.loads(payload) if payload else {}


def _post_multipart(url: str, field_name: str, filename: str, content: bytes, content_type: str,
                    form_fields: Dict[str, str], timeout: int) -> Dict[str, Any]:
    boundary = f"voice-runtime-{uuid.uuid4().hex}"
    body = io.BytesIO()

    for key, value in form_fields.items():
        body.write(f"--{boundary}\r\n".encode("utf-8"))
        body.write(f'Content-Disposition: form-data; name="{key}"\r\n\r\n'.encode("utf-8"))
        body.write(str(value).encode("utf-8"))
        body.write(b"\r\n")

    body.write(f"--{boundary}\r\n".encode("utf-8"))
    body.write(
        f'Content-Disposition: form-data; name="{field_name}"; filename="{filename}"\r\n'.encode("utf-8")
    )
    body.write(f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"))
    body.write(content)
    body.write(b"\r\n")
    body.write(f"--{boundary}--\r\n".encode("utf-8"))

    req = request.Request(url, data=body.getvalue(), method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    try:
        with request.urlopen(req, timeout=timeout) as resp:  # noqa: S310
            payload = resp.read().decode("utf-8")
            return json.loads(payload) if payload else {}
    except error.HTTPError as http_error:
        payload = http_error.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"STT HTTP {http_error.code}: {payload[:200]}") from http_error


def _check_ai_orchestration() -> bool:
    try:
        req = request.Request(CONFIG.ai_orchestration_url, method="GET")
        with request.urlopen(req, timeout=2) as resp:  # noqa: S310
            return 200 <= resp.status < 500
    except Exception:
        return False


def _faster_whisper_ready() -> bool:
    try:
        from faster_whisper import WhisperModel  # type: ignore

        started = time.time()
        WhisperModel(
            CONFIG.stt_model,
            device=CONFIG.stt_device,
            compute_type="int8",
            download_root=CONFIG.model_cache_dir,
            cpu_threads=max(1, CONFIG.worker_threads),
        )
        STATE.model_load_ms = int((time.time() - started) * 1000)
        STATE.stt_detail = "faster-whisper loaded"
        return True
    except Exception as ex:
        STATE.stt_detail = f"faster-whisper unavailable: {ex}"
        return False


def _whisper_server_ready() -> bool:
    try:
        status, payload = _json_get(f"{CONFIG.stt_url.rstrip('/')}/health", timeout=3)
        ok = status == 200
        STATE.stt_detail = f"whisper-cpp health={payload}" if ok else f"whisper-cpp http={status}"
        return ok
    except Exception as ex:
        STATE.stt_detail = f"whisper-cpp unavailable: {ex}"
        return False


def _piper_model_candidates(voice_model: str) -> List[Path]:
    model_dir = Path(CONFIG.tts_model_dir)
    name = Path(voice_model).name
    return [
        model_dir / f"{name}.onnx",
        model_dir / name,
        model_dir / f"{name}.onnx.json",
    ]


def _resolve_piper_model() -> Optional[str]:
    configured = os.getenv("PIPER_MODEL", CONFIG.tts_voice)
    if configured.endswith(".onnx") and Path(configured).exists():
        return configured
    for candidate in _piper_model_candidates(configured):
        if candidate.exists() and candidate.suffix == ".onnx":
            return str(candidate)
    return None


def _download_file(url: str, target: Path, timeout: int = 30) -> bool:
    try:
        target.parent.mkdir(parents=True, exist_ok=True)
        req = request.Request(url, method="GET")
        with request.urlopen(req, timeout=timeout) as response:  # noqa: S310
            if response.status != 200:
                return False
            with target.open("wb") as fh:
                fh.write(response.read())
        return True
    except Exception:
        return False


def _ensure_piper_model() -> Optional[str]:
    resolved = _resolve_piper_model()
    if resolved:
        return resolved
    if not CONFIG.tts_auto_download:
        return None

    configured = os.getenv("PIPER_MODEL", CONFIG.tts_voice)
    model_name = Path(configured).name
    if model_name.endswith(".onnx"):
        model_name = model_name[:-5]
    model_path = Path(CONFIG.tts_model_dir) / f"{model_name}.onnx"
    model_config_path = Path(f"{model_path}.json")

    model_url = os.getenv(
        "VOICE_TTS_MODEL_URL",
        f"https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/{model_name}.onnx",
    )
    model_config_url = os.getenv(
        "VOICE_TTS_MODEL_CONFIG_URL",
        f"https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/{model_name}.onnx.json",
    )

    if not model_path.exists() and not _download_file(model_url, model_path):
        return None
    if not model_config_path.exists():
        _download_file(model_config_url, model_config_path)
    return str(model_path) if model_path.exists() else None


def _piper_ready() -> bool:
    piper_cmd = os.getenv("PIPER_CLI", "piper")
    has_cli = subprocess.run(["/bin/sh", "-lc", f"command -v {piper_cmd}"], capture_output=True).returncode == 0
    if not has_cli:
        STATE.tts_detail = f"piper CLI not found in PATH (PIPER_CLI={piper_cmd})"
        return False
    model = _ensure_piper_model()
    if not model:
        configured = os.getenv("PIPER_MODEL", CONFIG.tts_voice)
        STATE.tts_detail = (
            f"piper model not found for '{configured}'. Expected .onnx under {CONFIG.tts_model_dir} "
            "or absolute file path in PIPER_MODEL. Auto-download can be enabled with VOICE_TTS_AUTO_DOWNLOAD=true."
        )
        return False
    STATE.tts_detail = f"piper ready model={model}"
    return True


def _warmup_models() -> None:
    provider = CONFIG.stt_provider.strip().lower()
    if provider == "whisper-cpp-server":
        STATE.stt_ready = _whisper_server_ready()
    elif provider == "faster-whisper":
        STATE.stt_ready = _faster_whisper_ready()
    else:
        STATE.stt_ready = False
        STATE.stt_detail = f"unsupported STT provider: {CONFIG.stt_provider}"

    STATE.tts_ready = CONFIG.tts_provider == "piper" and _piper_ready()
    STATE.model_ready = STATE.stt_ready and STATE.tts_ready


def _status() -> str:
    if STATE.startup_errors:
        return "FAILED"
    if STATE.stt_ready and STATE.tts_ready and STATE.ai_orchestration_reachable:
        return "HEALTHY"
    if STATE.stt_ready or STATE.tts_ready:
        return "DEGRADED"
    return "FAILED" if not STATE.ai_orchestration_reachable else "STARTING"


def _cleanup_worker() -> None:
    while not SHUTDOWN:
        cutoff = time.time() - CONFIG.session_idle_timeout
        with SESSIONS_LOCK:
            stale = [sid for sid, buf in SESSIONS.items() if buf.last_touch < cutoff]
            for sid in stale:
                SESSIONS.pop(sid, None)
                STATE.total_disconnects += 1
        time.sleep(5)


@app.on_event("startup")
def startup() -> None:
    _validate_config()
    _prepare_cache()
    STATE.ai_orchestration_reachable = _check_ai_orchestration()
    _warmup_models()
    if not STATE.ai_orchestration_reachable:
        STATE.startup_warnings.append("AI orchestration health endpoint not reachable at startup")
    STATE.status = _status()
    threading.Thread(target=_cleanup_worker, daemon=True).start()
    log.info(
        "voice-runtime-startup status=%s stt=%s tts=%s model=%s provider=%s sttDetail=%s ttsDetail=%s",
        STATE.status,
        STATE.stt_ready,
        STATE.tts_ready,
        STATE.model_ready,
        CONFIG.stt_provider,
        STATE.stt_detail,
        STATE.tts_detail,
    )


@app.on_event("shutdown")
def shutdown() -> None:
    global SHUTDOWN
    SHUTDOWN = True
    with SESSIONS_LOCK:
        SESSIONS.clear()


class HealthResponse(BaseModel):
    status: str
    sttReady: bool
    ttsReady: bool
    modelReady: bool
    activeSessions: int


class ReadyResponse(BaseModel):
    status: str
    ready: bool
    checks: Dict[str, bool]
    details: Dict[str, str]
    warnings: List[str]
    errors: List[str]


class MetricsSummary(BaseModel):
    status: str
    uptimeSeconds: int
    activeSessions: int
    maxActiveSessions: int
    sttProvider: str
    sttUrl: str
    ttsProvider: str
    sttModel: str
    ttsVoice: str
    sttReady: bool
    ttsReady: bool
    modelReady: bool
    aiOrchestrationReachable: bool
    avgSttLatencyMs: int
    avgTtsLatencyMs: int
    sttFailures: int
    ttsFailures: int
    modelLoadMs: int
    totalSessionsCreated: int
    totalDisconnects: int
    startupWarnings: List[str]
    sttDetail: str
    ttsDetail: str


class SttChunkRequest(BaseModel):
    tenantId: str
    sessionId: str
    audioBase64: str
    language: Optional[str] = "en"
    model: Optional[str] = "base.en"
    device: Optional[str] = "cpu"
    finalize: Optional[bool] = False


class SttChunkResponse(BaseModel):
    text: str
    confidence: Optional[float] = None
    finalChunk: bool = False


class SttFinalizeRequest(BaseModel):
    tenantId: str
    sessionId: str
    language: Optional[str] = "en"
    model: Optional[str] = "base.en"
    device: Optional[str] = "cpu"


class SttFinalizeResponse(BaseModel):
    text: str
    confidence: Optional[float] = None


class TtsRequest(BaseModel):
    tenantId: str
    text: str
    voice: str
    locale: Optional[str] = "en"


class TtsResponse(BaseModel):
    audioBase64: str
    durationMs: int


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    STATE.status = _status()
    with SESSIONS_LOCK:
        active = len(SESSIONS)
    return HealthResponse(
        status=STATE.status,
        sttReady=STATE.stt_ready,
        ttsReady=STATE.tts_ready,
        modelReady=STATE.model_ready,
        activeSessions=active,
    )


@app.get("/ready", response_model=ReadyResponse)
def ready() -> ReadyResponse:
    STATE.ai_orchestration_reachable = _check_ai_orchestration()
    _warmup_models()
    STATE.status = _status()
    checks = {
        "stt": STATE.stt_ready,
        "tts": STATE.tts_ready,
        "model": STATE.model_ready,
        "aiOrchestration": STATE.ai_orchestration_reachable,
        "cacheWritable": len(STATE.startup_errors) == 0,
    }
    return ReadyResponse(
        status=STATE.status,
        ready=all(checks.values()),
        checks=checks,
        details={
            "sttProvider": CONFIG.stt_provider,
            "sttUrl": CONFIG.stt_url,
            "sttDetail": STATE.stt_detail,
            "ttsProvider": CONFIG.tts_provider,
            "ttsDetail": STATE.tts_detail,
            "aiOrchestrationUrl": CONFIG.ai_orchestration_url,
        },
        warnings=STATE.startup_warnings,
        errors=STATE.startup_errors,
    )


@app.get("/metrics/summary", response_model=MetricsSummary)
def metrics_summary() -> MetricsSummary:
    STATE.ai_orchestration_reachable = _check_ai_orchestration()
    STATE.status = _status()
    with SESSIONS_LOCK:
        active = len(SESSIONS)
    return MetricsSummary(
        status=STATE.status,
        uptimeSeconds=int(time.time() - STATE.started_at),
        activeSessions=active,
        maxActiveSessions=CONFIG.max_active_sessions,
        sttProvider=CONFIG.stt_provider,
        sttUrl=CONFIG.stt_url,
        ttsProvider=CONFIG.tts_provider,
        sttModel=CONFIG.stt_model,
        ttsVoice=CONFIG.tts_voice,
        sttReady=STATE.stt_ready,
        ttsReady=STATE.tts_ready,
        modelReady=STATE.model_ready,
        aiOrchestrationReachable=STATE.ai_orchestration_reachable,
        avgSttLatencyMs=_avg(STATE.stt_latency_ms),
        avgTtsLatencyMs=_avg(STATE.tts_latency_ms),
        sttFailures=STATE.stt_failures,
        ttsFailures=STATE.tts_failures,
        modelLoadMs=STATE.model_load_ms,
        totalSessionsCreated=STATE.total_sessions_created,
        totalDisconnects=STATE.total_disconnects,
        startupWarnings=STATE.startup_warnings,
        sttDetail=STATE.stt_detail,
        ttsDetail=STATE.tts_detail,
    )


@app.get("/v1/voice/health")
def legacy_health() -> Dict[str, bool]:
    return {"ok": _status() in {"HEALTHY", "DEGRADED"}}


@app.post("/v1/stt/chunk", response_model=SttChunkResponse)
def stt_chunk(req: SttChunkRequest) -> SttChunkResponse:
    if len(req.audioBase64) > CONFIG.max_audio_chunk_bytes * 2:
        raise HTTPException(status_code=413, detail="Audio chunk too large")
    with SESSIONS_LOCK:
        if req.sessionId not in SESSIONS and len(SESSIONS) >= CONFIG.max_active_sessions:
            raise HTTPException(status_code=429, detail="Max active sessions reached")
        session = SESSIONS[req.sessionId]
        session.last_touch = time.time()
    samples = _decode_pcm_wav(req.audioBase64)
    with SESSIONS_LOCK:
        session = SESSIONS[req.sessionId]
        session.chunks.append((samples * 32768.0).astype(np.int16).tobytes())
    return SttChunkResponse(text="", confidence=None, finalChunk=False)


def _transcribe_whisper_cpp(samples: np.ndarray, language: str) -> Tuple[str, Optional[float]]:
    wav = _pcm_to_wav_bytes(samples)
    payload = _post_multipart(
        f"{CONFIG.stt_url.rstrip('/')}/inference",
        "file",
        "chunk.wav",
        wav,
        "audio/wav",
        {"language": language},
        timeout=CONFIG.stt_timeout_seconds,
    )
    text = str(payload.get("text") or payload.get("transcription") or "").strip()
    confidence_raw = payload.get("confidence")
    confidence = float(confidence_raw) if isinstance(confidence_raw, (int, float)) else None
    return text, confidence


def _transcribe_faster_whisper(samples: np.ndarray, language: str) -> Tuple[str, Optional[float]]:
    try:
        from faster_whisper import WhisperModel  # type: ignore

        started = time.time()
        model = WhisperModel(
            CONFIG.stt_model,
            device=CONFIG.stt_device,
            compute_type="int8",
            download_root=CONFIG.model_cache_dir,
            cpu_threads=max(1, CONFIG.worker_threads),
        )
        STATE.model_load_ms = int((time.time() - started) * 1000)
        segments, info = model.transcribe(samples, language=language, vad_filter=True)
        text = " ".join(seg.text.strip() for seg in segments if seg.text).strip()
        confidence = None
        if info is not None and getattr(info, "language_probability", None) is not None:
            confidence = float(info.language_probability)
        return text, confidence
    except Exception as ex:
        raise RuntimeError(f"faster-whisper transcription failed: {ex}") from ex


@app.post("/v1/stt/finalize", response_model=SttFinalizeResponse)
def stt_finalize(req: SttFinalizeRequest) -> SttFinalizeResponse:
    with SESSIONS_LOCK:
        buf = SESSIONS.get(req.sessionId)
        if not buf or not buf.chunks:
            return SttFinalizeResponse(text="", confidence=None)
        pcm = b"".join(buf.chunks)
        SESSIONS.pop(req.sessionId, None)
    samples = np.frombuffer(pcm, dtype=np.int16).astype(np.float32) / 32768.0
    language = req.language or CONFIG.stt_language

    started = time.time()
    try:
        if CONFIG.stt_provider == "whisper-cpp-server":
            text, confidence = _transcribe_whisper_cpp(samples, language)
        elif CONFIG.stt_provider == "faster-whisper":
            text, confidence = _transcribe_faster_whisper(samples, language)
        else:
            raise RuntimeError(f"unsupported STT provider: {CONFIG.stt_provider}")
        _bounded_append(STATE.stt_latency_ms, int((time.time() - started) * 1000))
        STATE.total_sessions_created += 1
        return SttFinalizeResponse(text=text, confidence=confidence)
    except Exception as ex:
        STATE.stt_failures += 1
        STATE.stt_detail = str(ex)
        raise HTTPException(status_code=503, detail=f"STT unavailable: {ex}") from ex


@app.post("/v1/tts/synthesize", response_model=TtsResponse)
def tts_synthesize(req: TtsRequest) -> TtsResponse:
    piper_cmd = os.getenv("PIPER_CLI", "piper")
    voice_model = _resolve_piper_model()
    if not voice_model:
        raise HTTPException(status_code=503, detail=STATE.tts_detail or "Piper model not available")
    started = time.time()
    try:
        with io.BytesIO() as out_wav:
            proc = subprocess.run(
                [piper_cmd, "--model", voice_model, "--output_raw"],
                input=req.text.encode("utf-8"),
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=True,
                timeout=20,
            )
            raw_audio = proc.stdout
            with wave.open(out_wav, "wb") as wf:
                wf.setnchannels(1)
                wf.setsampwidth(2)
                wf.setframerate(22050)
                wf.writeframes(raw_audio)
            audio_bytes = out_wav.getvalue()
            duration_ms = int((len(raw_audio) / 2) / 22050 * 1000)
        _bounded_append(STATE.tts_latency_ms, int((time.time() - started) * 1000))
        return TtsResponse(audioBase64=base64.b64encode(audio_bytes).decode("ascii"), durationMs=duration_ms)
    except Exception as ex:
        STATE.tts_failures += 1
        STATE.tts_detail = f"Piper synthesis failed: {ex}"
        raise HTTPException(status_code=503, detail=f"TTS unavailable: {ex}") from ex


@app.get("/v1/tts/validate")
def tts_validate() -> Dict[str, Any]:
    probe = TtsRequest(tenantId="system", text="Hello from clinic voice gateway.", voice=CONFIG.tts_voice)
    response = tts_synthesize(probe)
    return {"ok": True, "durationMs": response.durationMs}
