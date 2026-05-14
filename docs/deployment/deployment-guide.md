# Deployment Guide

## 1. Local Development Stack
Use `local/docker-compose.yml` to run infrastructure:
- PostgreSQL (5437)
- Redis (6383)
- MinIO (9005/9006)
- Keycloak (8182)

Scripts:
- `local/scripts/up.sh`
- `local/scripts/down.sh`
- `local/scripts/reset.sh`

## 2. Backend Runtime
- Spring Boot app reads `application.yml` + env overrides.
- Flyway runs on startup (`classpath:db/migration`).

## 3. Keycloak Setup
- Realm import from `local/keycloak/realm-export.json`.
- Resource server issuer URI configured in app config.

## 4. PostgreSQL
- Primary DB: `clinic_management`.
- Keycloak DB auto-created by init SQL (`001_create_keycloak_db.sql`).

## 5. Required Environment Areas
- AI provider configs (Gemini/Groq/Mock)
- OCR/Tesseract configs
- CarePilot messaging configs (email/sms/whatsapp)
- CarePilot webhook verify/secrets
- Storage (MinIO)
- Notification dispatcher/scheduler toggles
- AI calls scheduler/retry/failover/webhook settings

## 6. Provider Configuration Notes
- Do not commit real secrets.
- Use readiness APIs to validate configuration post-deploy.
- Test-send endpoints should be part of smoke tests.

## 7. Production Guidance
- Deploy API behind reverse proxy with TLS.
- Restrict actuator exposure network-wise.
- Store secrets in vault/secret manager.
- Enable structured log aggregation and retention.

## 8. Scaling
- Horizontally scale API with shared DB/Redis.
- Ensure scheduler behavior is safe for multi-instance deployment (idempotent claims/locks where implemented).
- Monitor queue and retry growth trends.

## 9. Backup Strategy
- Daily DB backups + PITR strategy.
- MinIO bucket backups/versioning.
- Keycloak config export backups.

