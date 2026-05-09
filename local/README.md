# Local Development

Use the scripts in `local/scripts` to manage the local stack.

## Start

```bash
cd /home/iadmin/code/clinic-management-platform/local
./scripts/up.sh
```

## Reset

```bash
cd /home/iadmin/code/clinic-management-platform/local
./scripts/reset.sh
```

The local Postgres container now auto-creates the `keycloak` database on first initialization through `local/init-sql/001_create_keycloak_db.sql`, so no manual `psql` step is needed after a reset.

## Notes

- `clinic_management` remains the primary application database.
- `keycloak` is created automatically for the Keycloak container.
- If the local Postgres data directory is partially initialized, reset the local stack before starting it again.

## AI Runtime Configuration

AI is environment-driven and safe by default. The local default is `CLINIC_AI_ENABLED=false` and `CLINIC_AI_PROVIDER=DISABLED`, so the backend can start without real Gemini or Groq keys.

Runtime diagnostics are available at `GET /api/ai/status` (authenticated + tenant scoped). This endpoint reports tenant module enablement, runtime/provider configuration state, and user-level AI permission without exposing secrets.

Use `local/.env.example` as the no-secrets reference. Do not commit real API keys.

Run without external AI:

```bash
export CLINIC_AI_ENABLED=false
export CLINIC_AI_PROVIDER=DISABLED
```

Run with mock AI responses for local UI/API testing:

```bash
export CLINIC_AI_ENABLED=true
export CLINIC_AI_PROVIDER=MOCK

# verifies runtime + tenant/module + role gates for AI
# expected: effectiveStatus=READY
# GET /api/ai/status
```

Run with Gemini:

```bash
export CLINIC_AI_ENABLED=true
export CLINIC_AI_PROVIDER=GEMINI
export CLINIC_GEMINI_ENABLED=true
export GEMINI_API_KEY=replace-with-local-secret
export GEMINI_MODEL=gemini-1.5-flash
export GEMINI_TIMEOUT_SECONDS=60
export CLINIC_AI_MAX_ATTEMPTS=3
export CLINIC_AI_RETRY_BACKOFF_MS=60000
```

OCR uses Tesseract when enabled:

```bash
export CLINIC_OCR_ENABLED=true
export CLINIC_OCR_PROVIDER=TESSERACT
export TESSERACT_EXECUTABLE_PATH=tesseract
export TESSERACT_DATA_PATH=/usr/share/tesseract-ocr/5/tessdata
export TESSERACT_LANGUAGE=eng
export TESSERACT_RENDER_DPI=200
export TESSERACT_PAGE_SEG_MODE=1
export TESSERACT_OCR_ENGINE_MODE=1
```

If Tesseract native dependencies are unavailable locally, disable OCR:

```bash
export CLINIC_OCR_ENABLED=false
```

The `local/docker-compose.yml` file includes an `x-clinic-ai-env` placeholder block for future backend container wiring. The current local compose stack runs infrastructure only, so export these variables in the shell that starts the backend.
Never commit real `GEMINI_API_KEY`/`GROQ_API_KEY` values to source control.
