# AIVA V2 Sarvam semantic fallback

## Scope

Add Sarvam chat completion as the final real AIVA V2 semantic provider after
Gemini and Groq. Sarvam voice STT/TTS remains independently configured and
unchanged.

## Contract

The Sarvam adapter sends the existing provider-neutral AIVA V2 schema using
Sarvam's OpenAI-compatible `/v1/chat/completions` JSON-schema response format.
The response content is returned to the existing AIVA V2 normalization and
semantic-validation path.

## Runtime policy

The UAT/live chain is `GEMINI,GROQ,SARVAM`; MOCK is excluded unless explicitly
allowed by the existing test-only runtime guard. Each provider receives one
bounded attempt, with controlled unavailable behavior after Sarvam fails.
