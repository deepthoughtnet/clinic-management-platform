# AIVA V2 Voice Language-Aware TTS Routing

## Scope and ownership

This additive change applies only to authorized DotVoice outbound synthesis.
The `api-bff` Voice communication adapter selects a provider order from the
persisted presentation mode, the existing `ReminderVoiceRenderer` performs the
fallback sequence, and the existing Sarvam adapter applies its REST request
configuration. No booking, authorization, tenant, session, STT, Web, alternate
Voice, or persistence behavior is changed. No database migration or frontend
work is required.

## Routing contract

- ENGLISH: ElevenLabs, Sarvam, Piper, mock.
- HINDI: Sarvam, ElevenLabs, Piper, mock.
- HINGLISH: Sarvam, ElevenLabs, Piper, mock.

The persisted Voice presentation mode is the routing input. The existing
presentation locale is passed to the selected provider; utterance text and STT
locale do not override the mode.

## Sarvam REST configuration

Use `bulbul:v4-flash`, `aparna_hi_customer`, pace `1.0`, Hindi target locale
`hi-IN`, and configurable `voice.sarvam.tts-sample-rate` (environment variable
`SARVAM_TTS_SAMPLE_RATE`, default `16000`). On Sarvam REST, the locale field is
named `language_code` and the rate field is `speech_sample_rate`. English
fallback synthesis follows the request locale (`en-IN`).

## Playback and diagnostics

Sarvam returns audio at its configured source rate. The current playback path
normalizes supported WAV/PCM and converts to 8 kHz mono G.711 μ-law frames for
DotVoice. Safe logs report mode, provider order/attempt/selection, readiness,
model/speaker, source/playback rates, conversion status, byte count, format,
and latency. They do not include synthesis text, credentials, or response
bodies.

## Validation

Focused tests cover language ordering, Sarvam fallback, configuration
propagation, provider selection diagnostics, and DotVoice sample-rate handoff.

## Verified configuration and playback path

The local default is Sarvam REST at 16,000 Hz, with configurable values
validated against the REST rates supported by the provider client. The DotVoice
path accepts 16 kHz PCM/WAV as its source and the existing G.711 converter
resamples it to 8 kHz mono μ-law frames; no separate resampling service is
needed. Sarvam response WAV headers are used to report the actual source rate.

Focused verification passed: `ReminderVoiceRendererTest` (7),
`SarvamTextToSpeechProviderTest` (8), `CommunicationTestServiceTest` (30), and
`VoiceOrchestratorServiceTest` (24): **69 tests, 0 failures, 0 errors**.
Coverage includes HINDI/HINGLISH Sarvam priority, ENGLISH ElevenLabs priority,
unavailable/failed Sarvam fallback, Sarvam REST model/speaker/locale/pace/rate
payload, safe provider-selection logs, and 16 kHz source to 8 kHz playback
diagnostics. No live provider call, Docker rebuild, or service restart was
performed.
