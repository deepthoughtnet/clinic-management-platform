import os
import tempfile
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from faster_whisper import WhisperModel

app = FastAPI(title="clinic-faster-whisper")

_model_cache: dict[str, WhisperModel] = {}


def configured_model_name() -> str:
    return os.getenv("FASTER_WHISPER_MODEL", "base")


def configured_language() -> str:
    return os.getenv("FASTER_WHISPER_LANGUAGE", "en")


def configured_device() -> str:
    return os.getenv("FASTER_WHISPER_DEVICE", "cpu")


def configured_compute_type() -> str:
    return os.getenv("FASTER_WHISPER_COMPUTE_TYPE", "int8")


def get_model(model_name: str) -> WhisperModel:
    cache_key = f"{model_name}:{configured_device()}:{configured_compute_type()}"
    if cache_key not in _model_cache:
        _model_cache[cache_key] = WhisperModel(
            model_name,
            device=configured_device(),
            compute_type=configured_compute_type(),
            download_root="/models",
        )
    return _model_cache[cache_key]


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "provider": "faster-whisper",
        "model": configured_model_name(),
        "language": configured_language(),
    }


@app.post("/transcribe")
async def transcribe(
    file: UploadFile = File(...),
    language: Optional[str] = Form(None),
    model: Optional[str] = Form(None),
) -> dict[str, str]:
    audio_bytes = await file.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="Audio file is empty.")

    selected_model = model or configured_model_name()
    selected_language = language or configured_language()
    suffix = Path(file.filename or "voice-test.webm").suffix or ".webm"
    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_file.write(audio_bytes)
            temp_path = temp_file.name

        model_instance = get_model(selected_model)
        segments, info = model_instance.transcribe(
            temp_path,
            language=selected_language,
            vad_filter=True,
        )
        transcript = " ".join(segment.text.strip() for segment in segments if segment.text).strip()
        if not transcript:
            raise HTTPException(status_code=422, detail="Audio could not be transcribed.")
        return {
            "text": transcript,
            "provider": "FASTER_WHISPER",
            "language": info.language or selected_language,
            "model": selected_model,
        }
    finally:
        if temp_path and os.path.exists(temp_path):
            os.unlink(temp_path)
