import os
import subprocess
import tempfile
import urllib.request
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel

app = FastAPI(title="clinic-piper-tts")


class SynthesisRequest(BaseModel):
    text: str
    voice: str | None = None


def configured_voice() -> str:
    return os.getenv("PIPER_VOICE", "en_US-lessac-medium")


def model_dir() -> Path:
    return Path(os.getenv("PIPER_MODEL_DIR", "/models/piper"))


def download_base() -> str:
    return os.getenv("PIPER_VOICE_DOWNLOAD_BASE", "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0").rstrip("/")


def voice_parts(voice: str) -> tuple[str, str, str, str]:
    locale, speaker, quality = voice.split("-", 2)
    language = locale.split("_", 1)[0]
    return language, locale, speaker, quality


def ensure_voice_files(voice: str) -> tuple[Path, Path]:
    target_dir = model_dir()
    target_dir.mkdir(parents=True, exist_ok=True)
    model_path = target_dir / f"{voice}.onnx"
    config_path = target_dir / f"{voice}.onnx.json"
    if model_path.exists() and config_path.exists():
        return model_path, config_path

    language, locale, speaker, quality = voice_parts(voice)
    remote_prefix = f"{download_base()}/{language}/{locale}/{speaker}/{quality}/{voice}.onnx"
    urllib.request.urlretrieve(remote_prefix, model_path)
    urllib.request.urlretrieve(f"{remote_prefix}.json", config_path)
    return model_path, config_path


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "provider": "piper",
        "voice": configured_voice(),
    }


@app.post("/synthesize")
def synthesize(request: SynthesisRequest) -> Response:
    text = (request.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="Text is required.")

    voice = request.voice or configured_voice()
    try:
        model_path, _ = ensure_voice_files(voice)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"Piper voice unavailable: {exc}") from exc

    with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as output_file:
        output_path = Path(output_file.name)

    try:
        completed = subprocess.run(
            ["piper", "--model", str(model_path), "--output_file", str(output_path)],
            input=text.encode("utf-8"),
            capture_output=True,
            check=False,
        )
        if completed.returncode != 0:
            stderr = completed.stderr.decode("utf-8", errors="ignore").strip()
            raise HTTPException(status_code=503, detail=f"Piper synthesis failed: {stderr or 'unknown error'}")
        audio_bytes = output_path.read_bytes()
        return Response(content=audio_bytes, media_type="audio/wav")
    finally:
        if output_path.exists():
            output_path.unlink()
