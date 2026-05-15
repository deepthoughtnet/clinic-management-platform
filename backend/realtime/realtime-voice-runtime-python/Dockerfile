FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    VOICE_GATEWAY_HOST=0.0.0.0 \
    VOICE_GATEWAY_PORT=8091 \
    VOICE_STT_PROVIDER=whisper-cpp-server \
    VOICE_STT_URL=http://whisper-stt:8080 \
    VOICE_STT_MODEL=base.en \
    VOICE_STT_DEVICE=cpu \
    VOICE_TTS_PROVIDER=piper \
    VOICE_TTS_VOICE=en_US-lessac-medium \
    VOICE_TTS_MODEL_DIR=/var/lib/voice-models/piper \
    VOICE_MODEL_CACHE_DIR=/var/lib/voice-models

RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    libsndfile1 \
    libespeak-ng1 \
    ca-certificates \
    curl \
    tini \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    mkdir -p /opt/piper; \
    for url in \
      "https://github.com/rhasspy/piper/releases/download/v1.2.0/piper_linux_x86_64.tar.gz" \
      "https://github.com/rhasspy/piper/releases/download/v1.2.0/piper_amd64.tar.gz" \
      "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz" \
      "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_amd64.tar.gz"; do \
      if curl -fL "$url" -o /tmp/piper.tar.gz; then \
        break; \
      fi; \
    done; \
    test -s /tmp/piper.tar.gz; \
    tar -xzf /tmp/piper.tar.gz -C /opt/piper; \
    find /opt/piper -type f -name piper -exec chmod +x {} \;; \
    PIPER_BIN="$(find /opt/piper -type f -name piper | head -n 1)"; \
    test -n "$PIPER_BIN"; \
    ln -sf "$PIPER_BIN" /usr/local/bin/piper; \
    rm -f /tmp/piper.tar.gz

RUN useradd --create-home --uid 10001 appuser
WORKDIR /app

COPY requirements.txt /app/requirements.txt
RUN pip install --upgrade pip && pip install -r /app/requirements.txt

COPY app.py /app/app.py
RUN mkdir -p /var/lib/voice-models/piper && chown -R appuser:appuser /app /var/lib/voice-models

USER appuser
EXPOSE 8091
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD curl -fsS http://127.0.0.1:${VOICE_GATEWAY_PORT}/health || exit 1
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8091", "--workers", "1"]
