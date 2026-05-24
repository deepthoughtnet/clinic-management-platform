import json
import logging
import os
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from faster_whisper import WhisperModel

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
logger = logging.getLogger("clinic-faster-whisper")

app = FastAPI(title="clinic-faster-whisper")

_model_cache: dict[str, WhisperModel] = {}
_ready_cache: dict[str, bool] = {}
SUPPORTED_TYPES = {
    "audio/webm",
    "audio/ogg",
    "audio/wav",
    "audio/x-wav",
    "audio/mpeg",
    "audio/mp3",
    "audio/mp4",
    "audio/x-m4a",
    "audio/aac",
    "application/octet-stream",
}
SUPPORTED_EXTENSIONS = {".webm", ".ogg", ".wav", ".mp3", ".m4a", ".mp4", ".aac"}


def configured_model_name() -> str:
    return os.getenv("FASTER_WHISPER_MODEL", "base")


def configured_language() -> str:
    return os.getenv("FASTER_WHISPER_LANGUAGE", "en")


def configured_device() -> str:
    return os.getenv("FASTER_WHISPER_DEVICE", "cpu")


def configured_compute_type() -> str:
    return os.getenv("FASTER_WHISPER_COMPUTE_TYPE", "int8")


def model_cache_key(model_name: str) -> str:
    return f"{model_name}:{configured_device()}:{configured_compute_type()}"


def get_model(model_name: str) -> WhisperModel:
    cache_key = model_cache_key(model_name)
    if cache_key not in _model_cache:
        logger.info("faster_whisper.model.load.start model=%s device=%s computeType=%s", model_name, configured_device(), configured_compute_type())
        started = time.perf_counter()
        _model_cache[cache_key] = WhisperModel(
            model_name,
            device=configured_device(),
            compute_type=configured_compute_type(),
            download_root="/models",
        )
        _ready_cache[cache_key] = True
        logger.info("faster_whisper.model.load.complete model=%s durationMs=%s", model_name, int((time.perf_counter() - started) * 1000))
    return _model_cache[cache_key]


def model_loaded(model_name: str) -> bool:
    return _ready_cache.get(model_cache_key(model_name), False)


def normalize_content_type(value: Optional[str]) -> str:
    raw = (value or "").strip().lower()
    if not raw:
        return ""
    return raw.split(";", 1)[0].strip()


def validate_upload(file: UploadFile, audio_bytes: bytes) -> tuple[str, str]:
    if not audio_bytes:
        raise HTTPException(status_code=400, detail={
            "error": "INVALID_AUDIO_UPLOAD",
            "detail": "Audio file is empty.",
            "filename": file.filename,
            "contentType": file.content_type,
            "sizeBytes": 0,
        })
    content_type = normalize_content_type(file.content_type)
    suffix = Path(file.filename or "voice-test.webm").suffix.lower()
    if content_type and content_type not in SUPPORTED_TYPES:
        raise HTTPException(
            status_code=422,
            detail={
                "error": "INVALID_AUDIO_UPLOAD",
                "detail": f"Unsupported audio content type: {content_type}",
                "filename": file.filename,
                "contentType": file.content_type,
                "sizeBytes": len(audio_bytes),
            },
        )
    if suffix not in SUPPORTED_EXTENSIONS:
        raise HTTPException(
            status_code=422,
            detail={
                "error": "INVALID_AUDIO_UPLOAD",
                "detail": f"Unsupported audio file extension: {suffix or 'unknown'}",
                "filename": file.filename,
                "contentType": file.content_type,
                "sizeBytes": len(audio_bytes),
            },
        )
    return content_type, suffix


def run_ffprobe(path: str) -> dict[str, object]:
    command = [
        "ffprobe",
        "-v",
        "error",
        "-show_entries",
        "format=format_name,duration:stream=codec_name,sample_rate,channels",
        "-of",
        "json",
        path,
    ]
    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    stdout = (completed.stdout or "").strip()
    stderr = (completed.stderr or "").strip()
    parsed = None
    if stdout:
        try:
            parsed = json.loads(stdout)
        except json.JSONDecodeError:
            parsed = None
    return {
        "ok": completed.returncode == 0,
        "returnCode": completed.returncode,
        "stdout": stdout,
        "stderr": stderr,
        "parsed": parsed,
    }


def should_convert_to_wav(content_type: str, suffix: str) -> bool:
    return content_type in {"audio/webm", "audio/ogg"} or suffix in {".webm", ".ogg"}


def convert_to_wav(input_path: str) -> str:
    with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as output_file:
        output_path = output_file.name
    command = ["ffmpeg", "-y", "-i", input_path, output_path]
    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    if completed.returncode != 0:
        raise RuntimeError((completed.stderr or completed.stdout or "ffmpeg conversion failed").strip())
    return output_path


@app.on_event("startup")
def startup() -> None:
    try:
        completed = subprocess.run(["ffmpeg", "-version"], capture_output=True, text=True, check=False)
        first_line = (completed.stdout or completed.stderr or "").splitlines()[0] if (completed.stdout or completed.stderr) else "unknown"
        logger.info("faster_whisper.ffmpeg.startup version=%s", first_line)
    except Exception:
        logger.exception("faster_whisper.ffmpeg.startup.failed")


@app.exception_handler(HTTPException)
async def http_exception_handler(_: Request, exc: HTTPException) -> JSONResponse:
    detail = exc.detail
    if isinstance(detail, dict):
        payload = detail
    else:
        payload = {"error": "voice_transcription_failed", "detail": str(detail)}
    return JSONResponse(status_code=exc.status_code, content=payload)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
    logger.warning("faster_whisper.request.validation_failed errors=%s", exc.errors())
    return JSONResponse(
        status_code=422,
        content={
            "error": "INVALID_AUDIO_UPLOAD",
            "detail": "Invalid multipart form. Expected field name 'file'.",
        },
    )


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "ok",
        "provider": "faster-whisper",
        "model": configured_model_name(),
        "language": configured_language(),
        "modelLoaded": model_loaded(configured_model_name()),
    }


@app.get("/ready")
def ready() -> dict[str, object]:
    selected_model = configured_model_name()
    try:
        get_model(selected_model)
        return {
            "status": "ready",
            "provider": "faster-whisper",
            "model": selected_model,
            "language": configured_language(),
            "modelLoaded": True,
            "message": "Faster-Whisper model loaded.",
        }
    except Exception as exc:
        logger.exception("faster_whisper.ready.failed model=%s", selected_model)
        return {
            "status": "warming",
            "provider": "faster-whisper",
            "model": selected_model,
            "language": configured_language(),
            "modelLoaded": False,
            "message": f"Model warm-up failed: {exc}",
        }


@app.post("/transcribe")
async def transcribe(
    file: UploadFile = File(...),
    language: Optional[str] = Form(None),
    model: Optional[str] = Form(None),
) -> dict[str, object]:
    audio_bytes = await file.read()
    normalized_content_type, suffix = validate_upload(file, audio_bytes)

    selected_model = model or configured_model_name()
    selected_language = language or configured_language()
    temp_path = None
    transcription_path = None
    ffprobe_result = None
    started = time.perf_counter()
    logger.info(
        "faster_whisper.transcribe.start model=%s language=%s sizeBytes=%s contentType=%s normalizedContentType=%s filename=%s extension=%s",
        selected_model,
        selected_language,
        len(audio_bytes),
        file.content_type,
        normalized_content_type,
        file.filename,
        suffix,
    )
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_file.write(audio_bytes)
            temp_path = temp_file.name

        ffprobe_result = run_ffprobe(temp_path)
        logger.info(
            "faster_whisper.ffprobe filename=%s contentType=%s sizeBytes=%s suffix=%s ok=%s stdout=%s stderr=%s",
            file.filename,
            normalized_content_type,
            len(audio_bytes),
            suffix,
            ffprobe_result["ok"],
            (ffprobe_result["stdout"] or "")[:400],
            (ffprobe_result["stderr"] or "")[:400],
        )
        if not ffprobe_result["ok"]:
            raise HTTPException(
                status_code=422,
                detail={
                    "error": "INVALID_AUDIO_UPLOAD",
                    "detail": "ffprobe could not read the uploaded audio container.",
                    "filename": file.filename,
                    "contentType": file.content_type,
                    "sizeBytes": len(audio_bytes),
                    "ffprobe": ffprobe_result,
                },
            )

        transcription_path = temp_path
        if should_convert_to_wav(normalized_content_type, suffix):
            try:
                transcription_path = convert_to_wav(temp_path)
                logger.info(
                    "faster_whisper.transcode.complete filename=%s sourceSuffix=%s targetSuffix=.wav",
                    file.filename,
                    suffix,
                )
            except Exception as exc:
                raise HTTPException(
                    status_code=422,
                    detail={
                        "error": "INVALID_AUDIO_UPLOAD",
                        "detail": f"ffmpeg decode failed: {exc}",
                        "filename": file.filename,
                        "contentType": file.content_type,
                        "sizeBytes": len(audio_bytes),
                        "ffprobe": ffprobe_result,
                    },
                )

        model_instance = get_model(selected_model)
        segments, info = model_instance.transcribe(
            transcription_path,
            language=selected_language,
            vad_filter=True,
        )
        transcript = " ".join(segment.text.strip() for segment in segments if segment.text).strip()
        if not transcript:
            raise HTTPException(
                status_code=422,
                detail={
                    "error": "INVALID_AUDIO_UPLOAD",
                    "detail": "Audio could not be transcribed.",
                    "filename": file.filename,
                    "contentType": file.content_type,
                    "sizeBytes": len(audio_bytes),
                    "ffprobe": ffprobe_result,
                },
            )
        logger.info(
            "faster_whisper.transcribe.complete model=%s durationMs=%s detectedLanguage=%s",
            selected_model,
            int((time.perf_counter() - started) * 1000),
            info.language or selected_language,
        )
        return {
            "text": transcript,
            "provider": "FASTER_WHISPER",
            "language": info.language or selected_language,
            "model": selected_model,
        }
    except HTTPException as exc:
        logger.warning(
            "faster_whisper.transcribe.validation_failed model=%s filename=%s contentType=%s detail=%s",
            selected_model,
            file.filename,
            normalized_content_type,
            exc.detail,
        )
        raise
    except Exception as exc:
        logger.exception("faster_whisper.transcribe.failed model=%s filename=%s", selected_model, file.filename)
        raise HTTPException(
            status_code=422,
            detail={
                "error": "INVALID_AUDIO_UPLOAD",
                "detail": f"Audio could not be transcribed: {exc}",
                "filename": file.filename,
                "contentType": file.content_type,
                "sizeBytes": len(audio_bytes),
                "ffprobe": ffprobe_result,
            },
        ) from exc
    finally:
        if temp_path and os.path.exists(temp_path):
            os.unlink(temp_path)
        if transcription_path and transcription_path != temp_path and os.path.exists(transcription_path):
            os.unlink(transcription_path)
