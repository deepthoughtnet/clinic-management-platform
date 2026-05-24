FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN pip install --no-cache-dir \
    fastapi==0.115.0 \
    uvicorn[standard]==0.30.6 \
    python-multipart==0.0.12 \
    requests==2.32.3 \
    faster-whisper==1.1.0

COPY app.py /app/app.py

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
