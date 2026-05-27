import { useEffect, useMemo, useRef, useState } from "react";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import MicRoundedIcon from "@mui/icons-material/MicRounded";
import StopRoundedIcon from "@mui/icons-material/StopRounded";
import UploadFileRoundedIcon from "@mui/icons-material/UploadFileRounded";
import SendRoundedIcon from "@mui/icons-material/SendRounded";
import AutorenewRoundedIcon from "@mui/icons-material/AutorenewRounded";
import LinkRoundedIcon from "@mui/icons-material/LinkRounded";
import LinkOffRoundedIcon from "@mui/icons-material/LinkOffRounded";
import { getVoiceLiveStatus, getVoiceTestStatus, runVoiceSttDebug, runVoiceTest, type VoiceDebugTraceEntry, type VoiceLiveStatusResponse, type VoiceProviderTrace, type VoiceStatusResponse, type VoiceSttDebugResponse, type VoiceTestResponse, type VoiceWorkflowMode, type VoiceWorkflowSummary } from "../../api/clinicApi";
import { ApiClientError } from "../../api/restClient";
import { useAuth } from "../../auth/useAuth";

const SUPPORTED_AUDIO_TYPES = ["audio/wav", "audio/webm", "audio/ogg", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/aac"];
const LIVE_AUDIO_BASE64_CHUNK_SIZE = 24 * 1024;
const FILE_SILENCE_THRESHOLD = 0.015;
const LIVE_SPEECH_START_THRESHOLD = 0.03;
const LIVE_SPEECH_END_THRESHOLD = 0.015;
const LIVE_MIN_SPEECH_MS = 300;
const LIVE_SILENCE_TIMEOUT_MS = 1500;
const LIVE_MAX_UTTERANCE_MS = 30000;
const LIVE_LISTEN_RESUME_DELAY_MS = 350;
const LIVE_HEARTBEAT_INTERVAL_MS = 15000;
const LIVE_STALE_AFTER_MS = 45000;

type VoiceTab = "file" | "live";
type LiveStatus =
  | "idle"
  | "connecting"
  | "session_started"
  | "listening"
  | "speech_detected"
  | "finalizing_audio"
  | "processing"
  | "playing_response"
  | "ending"
  | "ended"
  | "error";
type LiveConversationTurn = {
  turnIndex: number;
  transcript: string;
  assistantText: string;
  providerTrace: VoiceProviderTrace | null;
  requestId?: string | null;
  metrics?: LiveTurnMetrics | null;
  workflowSummary?: VoiceWorkflowSummary | null;
};

type LiveTurnMetrics = {
  captureDurationMs: number;
  sttDurationMs: number;
  llmDurationMs: number;
  ttsDurationMs: number;
  totalDurationMs: number;
  language: string | null;
  sttProvider: string | null;
  llmProvider: string | null;
  ttsProvider: string | null;
  fallbackReason: string | null;
  audioBytes: number;
};

type CapturedAudioDebug = {
  deviceLabel: string;
  recorderMimeType: string;
  chunkCount: number;
  chunkSizes: number[];
  finalBlobSize: number;
  finalBlobType: string;
  uploadFilename: string;
  uploadMimeType: string;
  durationMs: number;
  averageRms: number;
  peakLevel: number;
  silent: boolean;
};

type LiveCapturedAudioDebug = {
  recorderMimeType: string;
  chunkCount: number;
  chunkSizes: number[];
  finalBlobSize: number;
  finalBlobType: string;
  uploadFilename: string;
  uploadMimeType: string;
};

type AudioInputDevice = {
  deviceId: string;
  label: string;
};

function formatProvider(value: string | null | undefined) {
  if (!value) return "Not used";
  return value.replace(/[_-]+/g, " ").replace(/\b\w/g, (char) => char.toUpperCase());
}

function formatWorkflowField(field: string) {
  switch (field) {
    case "patientIdentity":
      return "Patient identity";
    case "doctorName":
      return "Preferred doctor";
    case "preferredDate":
      return "Preferred date";
    case "preferredTimeWindow":
      return "Preferred time";
    default:
      return field;
  }
}

function ResultBlock({ label, value, empty }: { label: string; value: string | null | undefined; empty: string }) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }} color={value ? "text.primary" : "text.secondary"}>
        {value || empty}
      </Typography>
    </Paper>
  );
}

function liveStatusTone(status: LiveStatus) {
  switch (status) {
    case "session_started":
      return "success";
    case "listening":
      return "warning";
    case "speech_detected":
      return "warning";
    case "playing_response":
      return "secondary";
    case "processing":
    case "finalizing_audio":
      return "info";
    case "connecting":
      return "info";
    case "ending":
      return "warning";
    case "error":
      return "error";
    default:
      return "default";
  }
}

function liveStatusLabel(status: LiveStatus) {
  switch (status) {
    case "idle":
      return "Disconnected";
    case "connecting":
      return "Connecting…";
    case "session_started":
      return "Session started";
    case "listening":
      return "Listening… speak now";
    case "speech_detected":
      return "Speech detected";
    case "finalizing_audio":
      return "Finalizing audio…";
    case "processing":
      return "Processing…";
    case "playing_response":
      return "Playing assistant response…";
    case "ending":
      return "Ending session…";
    case "ended":
      return "Session ended";
    case "error":
      return "Error";
    default:
      return status;
  }
}

function isMockProvider(value: string | null | undefined) {
  return (value || "").toLowerCase().includes("mock");
}

function ProviderTraceBlock({ trace, requestId }: { trace: VoiceProviderTrace | null | undefined; requestId?: string | null }) {
  const hasMockFallback =
    isMockProvider(trace?.sttProvider) ||
    isMockProvider(trace?.llmProvider) ||
    isMockProvider(trace?.ttsProvider);

  return (
    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
        Provider trace
      </Typography>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Chip size="small" label={`STT: ${formatProvider(trace?.sttProvider || "faster-whisper")}`} />
        <Chip size="small" label={`LLM: ${formatProvider(trace?.llmProvider || "gemini")}`} />
        <Chip size="small" label={`TTS: ${formatProvider(trace?.ttsProvider || "piper")}`} />
      </Stack>
      {hasMockFallback ? (
        <Alert severity="warning" sx={{ mt: 1 }}>
          Mock fallback was used for part of this voice loop. Check the local Faster-Whisper or Piper service if you expected fully local audio processing.
        </Alert>
      ) : null}
      {requestId ? (
        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: "block" }}>
          Request ID: {requestId}
        </Typography>
      ) : null}
    </Paper>
  );
}

function WorkflowSummaryBlock({ summary }: { summary: VoiceWorkflowSummary | null | undefined }) {
  if (!summary || summary.mode !== "appointment-booking") {
    return null;
  }
  const suggestedSlot = summary.suggestedSlot;
  return (
    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
        Appointment workflow
      </Typography>
      <Typography variant="body2" color="text.secondary">
        State: {summary.intentState || "Collecting details"} • Language: {summary.language || "auto"}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        Patient: {summary.patientName || summary.patientPhone || "Pending"} • Doctor: {summary.doctorName || "Pending"}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        Date: {summary.preferredDate || "Pending"} • Time: {summary.preferredTimeWindow || "Pending"}
      </Typography>
      {summary.reason ? (
        <Typography variant="body2" color="text.secondary">
          Reason: {summary.reason}
        </Typography>
      ) : null}
      {summary.missingFields && summary.missingFields.length > 0 ? (
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
          Missing: {summary.missingFields.map((field) => formatWorkflowField(field)).join(", ")}
        </Typography>
      ) : null}
      {suggestedSlot ? (
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
          Suggested slot: {suggestedSlot.doctorName || "Doctor"} on {suggestedSlot.appointmentDate || "date"} at {suggestedSlot.slotTime || "time"}
        </Typography>
      ) : null}
      {summary.confirmationRequested ? (
        <Alert severity="info" sx={{ mt: 1 }}>
          Confirmation is required before any appointment can be finalized.
        </Alert>
      ) : null}
      {summary.handoffRequired ? (
        <Alert severity="warning" sx={{ mt: 1 }}>
          Human receptionist follow-up is recommended{summary.handoffReason ? `: ${summary.handoffReason}` : "."}
        </Alert>
      ) : null}
    </Paper>
  );
}

function formatSize(sizeBytes: number | null | undefined) {
  if (typeof sizeBytes !== "number" || Number.isNaN(sizeBytes)) return "Unknown";
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  return `${Math.round(sizeBytes / 1024)} KB`;
}

function formatDuration(durationMs: number | null | undefined) {
  if (typeof durationMs !== "number" || Number.isNaN(durationMs) || durationMs <= 0) return "Unknown";
  if (durationMs < 1000) return `${durationMs} ms`;
  return `${(durationMs / 1000).toFixed(1)} s`;
}

function formatLevel(level: number | null | undefined) {
  if (typeof level !== "number" || Number.isNaN(level)) return "0.000";
  return level.toFixed(3);
}

function DebugTracePanel({ trace }: { trace: VoiceDebugTraceEntry[] | null | undefined }) {
  if (!trace || trace.length === 0) return null;
  return (
    <Accordion disableGutters>
      <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
          Debug Trace
        </Typography>
      </AccordionSummary>
      <AccordionDetails>
        <Stack spacing={1}>
          {trace.map((entry, index) => (
            <Paper key={`${entry.stage}-${index}`} variant="outlined" sx={{ p: 1.25, bgcolor: "background.default" }}>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1} justifyContent="space-between">
                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                  {entry.stage}
                </Typography>
                <Chip size="small" color={entry.ok ? "success" : "warning"} label={entry.ok ? "OK" : "Check"} />
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {[
                  entry.provider ? `provider=${entry.provider}` : null,
                  entry.from ? `from=${entry.from}` : null,
                  entry.to ? `to=${entry.to}` : null,
                  entry.filename ? `file=${entry.filename}` : null,
                  entry.contentType ? `type=${entry.contentType}` : null,
                  typeof entry.sizeBytes === "number" ? `size=${formatSize(entry.sizeBytes)}` : null,
                  entry.multipartField ? `field=${entry.multipartField}` : null,
                  typeof entry.status === "number" ? `status=${entry.status}` : null,
                  typeof entry.durationMs === "number" ? `duration=${entry.durationMs}ms` : null,
                  typeof entry.transcriptLength === "number" ? `transcriptLength=${entry.transcriptLength}` : null,
                  entry.reason ? `reason=${entry.reason}` : null,
                  entry.url ? `url=${entry.url}` : null,
                  entry.savedPath ? `savedPath=${entry.savedPath}` : null,
                ].filter(Boolean).join(" • ")}
              </Typography>
              {entry.bodyPreview ? (
                <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block", whiteSpace: "pre-wrap" }}>
                  {entry.bodyPreview}
                </Typography>
              ) : null}
            </Paper>
          ))}
        </Stack>
      </AccordionDetails>
    </Accordion>
  );
}

function resolveWebSocketUrl(token: string, tenantId: string) {
  const explicitBase = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim();
  const base = explicitBase && explicitBase.length > 0 ? explicitBase : window.location.origin;
  const url = new URL(base);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws/voice/test";
  url.searchParams.set("token", token);
  url.searchParams.set("tenantId", tenantId);
  return url.toString();
}

async function blobToBase64(blob: Blob) {
  const buffer = await blob.arrayBuffer();
  let binary = "";
  const bytes = new Uint8Array(buffer);
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
  }
  return btoa(binary);
}

function splitBase64Chunks(base64: string, chunkSize = LIVE_AUDIO_BASE64_CHUNK_SIZE) {
  const chunks: string[] = [];
  for (let index = 0; index < base64.length; index += chunkSize) {
    chunks.push(base64.slice(index, index + chunkSize));
  }
  return chunks;
}

function maskWebSocketUrl(rawUrl: string) {
  try {
    const url = new URL(rawUrl);
    if (url.searchParams.has("token")) {
      url.searchParams.set("token", "***");
    }
    return url.toString();
  } catch {
    return rawUrl.replace(/token=[^&]+/i, "token=***");
  }
}

function selectRecordingMimeType() {
  const candidates = [
    "audio/webm;codecs=opus",
    "audio/webm",
    "audio/ogg;codecs=opus",
    "audio/ogg",
  ];
  return candidates.find((candidate) => MediaRecorder.isTypeSupported(candidate)) || "";
}

function resolveAudioExtensionFromType(type: string) {
  const normalized = type.toLowerCase();
  if (normalized.includes("mpeg") || normalized.includes("mp3")) return "mp3";
  if (normalized.includes("wav")) return "wav";
  if (normalized.includes("ogg")) return "ogg";
  if (normalized.includes("mp4") || normalized.includes("m4a")) return "m4a";
  return "webm";
}

function toVoiceError(err: unknown) {
  if (err instanceof ApiClientError && err.status === 504) {
    return "Voice processing timed out. First local model load can take longer; please retry.";
  }
  return err instanceof Error ? err.message : "Voice test failed.";
}

export default function VoiceTestPage() {
  const { accessToken, tenantId, hasPermission } = useAuth();
  const canUseVoiceTest = hasPermission("ai.voice.test");

  const [tab, setTab] = useState<VoiceTab>("file");
  const [language, setLanguage] = useState("en");
  const [context, setContext] = useState("");
  const [workflowMode, setWorkflowMode] = useState<VoiceWorkflowMode>("generic");

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileResult, setFileResult] = useState<VoiceTestResponse | null>(null);
  const [sttDebugResult, setSttDebugResult] = useState<VoiceSttDebugResponse | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [fileInfo, setFileInfo] = useState<string | null>(null);
  const [fileCaptureInfo, setFileCaptureInfo] = useState<string | null>(null);
  const [capturedAudioDebug, setCapturedAudioDebug] = useState<CapturedAudioDebug | null>(null);
  const [capturedAudioUrl, setCapturedAudioUrl] = useState<string | null>(null);
  const [fileLoading, setFileLoading] = useState(false);
  const [fileRecording, setFileRecording] = useState(false);
  const [statusLoading, setStatusLoading] = useState(false);
  const [processingStage, setProcessingStage] = useState<string | null>(null);
  const [voiceStatus, setVoiceStatus] = useState<VoiceStatusResponse | null>(null);
  const [liveStatusInfo, setLiveStatusInfo] = useState<VoiceLiveStatusResponse | null>(null);
  const [audioInputDevices, setAudioInputDevices] = useState<AudioInputDevice[]>([]);
  const [selectedAudioInputId, setSelectedAudioInputId] = useState("");
  const [micDeviceInfo, setMicDeviceInfo] = useState<string | null>(null);
  const [micLevel, setMicLevel] = useState(0);
  const [micPeak, setMicPeak] = useState(0);
  const [speechDetected, setSpeechDetected] = useState(false);
  const [silenceDetected, setSilenceDetected] = useState(false);
  const [fileSilenceWarning, setFileSilenceWarning] = useState<string | null>(null);

  const [liveStatus, setLiveStatus] = useState<LiveStatus>("idle");
  const [liveInfo, setLiveInfo] = useState<string | null>(null);
  const [liveCaptureInfo, setLiveCaptureInfo] = useState<string | null>(null);
  const [liveError, setLiveError] = useState<string | null>(null);
  const [liveTranscript, setLiveTranscript] = useState("");
  const [liveAssistantText, setLiveAssistantText] = useState("");
  const [liveProviderTrace, setLiveProviderTrace] = useState<VoiceProviderTrace | null>(null);
  const [liveAudioDataUrl, setLiveAudioDataUrl] = useState<string | null>(null);
  const [liveEvents, setLiveEvents] = useState<string[]>([]);
  const [liveSessionId, setLiveSessionId] = useState<string | null>(null);
  const [liveCapturedAudioDebug, setLiveCapturedAudioDebug] = useState<LiveCapturedAudioDebug | null>(null);
  const [liveConversation, setLiveConversation] = useState<LiveConversationTurn[]>([]);
  const [liveWorkflowSummary, setLiveWorkflowSummary] = useState<VoiceWorkflowSummary | null>(null);
  const [livePlaybackError, setLivePlaybackError] = useState<string | null>(null);
  const [liveTurnMetrics, setLiveTurnMetrics] = useState<LiveTurnMetrics | null>(null);

  const fileMediaRecorderRef = useRef<MediaRecorder | null>(null);
  const fileMediaStreamRef = useRef<MediaStream | null>(null);
  const fileChunksRef = useRef<Blob[]>([]);
  const fileRecordingStopPromiseRef = useRef<Promise<void> | null>(null);
  const fileRecordingStatsRef = useRef({ startedAt: 0, rmsTotal: 0, rmsSamples: 0, peakLevel: 0 });

  const liveMediaRecorderRef = useRef<MediaRecorder | null>(null);
  const liveMediaStreamRef = useRef<MediaStream | null>(null);
  const liveChunksRef = useRef<Blob[]>([]);
  const liveSocketRef = useRef<WebSocket | null>(null);
  const liveSendQueueRef = useRef<Promise<void>>(Promise.resolve());
  const liveChunkSequenceRef = useRef(0);
  const liveChunkFilenameRef = useRef<string | null>(null);
  const liveChunkContentTypeRef = useRef("audio/webm");
  const liveRecordedBytesRef = useRef(0);
  const liveAssistantAudioChunksRef = useRef<Map<number, string>>(new Map());
  const liveAssistantAudioExpectedChunksRef = useRef(0);
  const liveCurrentTurnRef = useRef<number | null>(null);
  const livePendingTranscriptRef = useRef("");
  const livePendingAssistantRef = useRef("");
  const livePendingProviderTraceRef = useRef<VoiceProviderTrace | null>(null);
  const livePendingRequestIdRef = useRef<string | null>(null);
  const liveTurnHasAudioRef = useRef(false);
  const liveSpeechStartedAtRef = useRef<number | null>(null);
  const liveFirstSpeechAtRef = useRef<number | null>(null);
  const liveLastSpeechAtRef = useRef<number | null>(null);
  const liveSpeechDetectedRef = useRef(false);
  const liveSpeechFrameActiveRef = useRef(false);
  const liveAutoStopTriggeredRef = useRef(false);
  const liveSessionStartedAtRef = useRef<number | null>(null);
  const monitoringIntervalRef = useRef<number | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const sourceNodeRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const levelDataRef = useRef<Uint8Array<ArrayBuffer> | null>(null);
  const liveAudioElementRef = useRef<HTMLAudioElement | null>(null);
  const liveAutoResumeRef = useRef(false);
  const liveResumeTimerRef = useRef<number | null>(null);
  const liveStartingMicRef = useRef(false);
  const livePendingAutoPlayRef = useRef(false);
  const liveStatusRef = useRef<LiveStatus>("idle");
  const liveEndedByUserRef = useRef(false);
  const liveHeartbeatTimerRef = useRef<number | null>(null);
  const liveStaleCheckTimerRef = useRef<number | null>(null);
  const liveLastHeartbeatAtRef = useRef<number | null>(null);
  const liveCaptureStartedAtRef = useRef<number | null>(null);
  const liveCaptureDurationMsRef = useRef(0);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const fileAudioUrl = useMemo(() => {
    if (!fileResult?.audioBase64 || !fileResult.audioContentType) return null;
    return `data:${fileResult.audioContentType};base64,${fileResult.audioBase64}`;
  }, [fileResult]);

  useEffect(() => {
    return () => {
      if (capturedAudioUrl) {
        URL.revokeObjectURL(capturedAudioUrl);
      }
      stopAudioMonitoring();
      stopFileStream();
      cleanupLiveSessionResources();
      closeLiveSession(false);
    };
  }, [capturedAudioUrl]);

  useEffect(() => {
    if (!liveAudioDataUrl || !livePendingAutoPlayRef.current) {
      return;
    }
    livePendingAutoPlayRef.current = false;
    void playLiveResponse(true);
  }, [liveAudioDataUrl]);

  useEffect(() => {
    if (!accessToken || !tenantId || !canUseVoiceTest) {
      return;
    }
    void refreshVoiceStatus(false);
    void refreshLiveStatus();
  }, [accessToken, tenantId, canUseVoiceTest]);

  useEffect(() => {
    liveStatusRef.current = liveStatus;
  }, [liveStatus]);

  useEffect(() => {
    void refreshAudioInputDevices();
    const handler = () => {
      void refreshAudioInputDevices();
    };
    navigator.mediaDevices?.addEventListener?.("devicechange", handler);
    return () => {
      navigator.mediaDevices?.removeEventListener?.("devicechange", handler);
    };
  }, []);

  function appendLiveEvent(message: string) {
    setLiveEvents((current) => [new Date().toLocaleTimeString() + " • " + message, ...current].slice(0, 20));
  }

  function updateLiveStatus(status: LiveStatus) {
    liveStatusRef.current = status;
    setLiveStatus(status);
  }

  async function refreshAudioInputDevices() {
    try {
      if (!navigator.mediaDevices?.enumerateDevices) return;
      const devices = await navigator.mediaDevices.enumerateDevices();
      const inputs = devices
        .filter((device) => device.kind === "audioinput")
        .map((device, index) => ({
          deviceId: device.deviceId,
          label: device.label || `Microphone ${index + 1}`,
        }));
      setAudioInputDevices(inputs);
      if (!selectedAudioInputId && inputs.length > 0) {
        setSelectedAudioInputId(inputs[0].deviceId);
      }
    } catch {
      setAudioInputDevices([]);
    }
  }

  function selectedMicrophoneLabel() {
    if (!selectedAudioInputId) return "Default microphone";
    return audioInputDevices.find((device) => device.deviceId === selectedAudioInputId)?.label || "Selected microphone";
  }

  function requestedAudioConstraints(): MediaStreamConstraints["audio"] {
    if (!selectedAudioInputId) {
      return true;
    }
    return { deviceId: { exact: selectedAudioInputId } };
  }

  function selectedLanguageLabel() {
    if (language === "hi") return "Hindi";
    if (language === "auto") return "Auto Detect";
    return "English";
  }

  function selectedWorkflowLabel() {
    return workflowMode === "appointment-booking" ? "Appointment Booking Assistant" : "Generic Assistant";
  }

  function isHindiTtsVoiceConfigured() {
    return Boolean(voiceStatus?.ttsHindiConfigured);
  }

  function hindiTtsStatusMessage() {
    const hindiVoice = voiceStatus?.ttsConfiguredVoices?.hi;
    if (isHindiTtsVoiceConfigured() && hindiVoice) {
      return `Hindi Piper voice configured: ${hindiVoice}.`;
    }
    if (voiceStatus?.ttsFallbackVoiceEnabled) {
      const fallbackVoice = voiceStatus?.ttsConfiguredVoices?.en || voiceStatus?.ttsConfiguredVoices?.default || voiceStatus?.ttsConfiguredVoice;
      return `Hindi STT is enabled, but a Hindi Piper voice is not configured. Text responses will still work, and audio may use ${fallbackVoice || "the default voice"}.`;
    }
    return "Hindi STT is enabled, but a Hindi Piper voice is not configured. Text responses will still work, but Hindi audio playback is unavailable.";
  }

  function clearLiveResumeTimer() {
    if (liveResumeTimerRef.current != null) {
      window.clearTimeout(liveResumeTimerRef.current);
      liveResumeTimerRef.current = null;
    }
  }

  function clearLiveHeartbeatTimers() {
    if (liveHeartbeatTimerRef.current != null) {
      window.clearInterval(liveHeartbeatTimerRef.current);
      liveHeartbeatTimerRef.current = null;
    }
    if (liveStaleCheckTimerRef.current != null) {
      window.clearInterval(liveStaleCheckTimerRef.current);
      liveStaleCheckTimerRef.current = null;
    }
  }

  function cleanupLiveSessionResources() {
    clearLiveResumeTimer();
    clearLiveHeartbeatTimers();
    liveAudioElementRef.current?.pause();
    stopLiveStream();
    liveStartingMicRef.current = false;
    liveSendQueueRef.current = Promise.resolve();
    liveAssistantAudioChunksRef.current.clear();
    liveAssistantAudioExpectedChunksRef.current = 0;
    liveChunkSequenceRef.current = 0;
    liveChunksRef.current = [];
  }

  function canAutoResumeListening() {
    if (!liveAutoResumeRef.current) return { ok: false, reason: "auto_resume_disabled" };
    if (liveEndedByUserRef.current) return { ok: false, reason: "session_ending" };
    if (!liveSocketRef.current || liveSocketRef.current.readyState !== WebSocket.OPEN) return { ok: false, reason: "socket_not_open" };
    if (liveStartingMicRef.current) return { ok: false, reason: "mic_start_pending" };
    if (liveMediaRecorderRef.current && liveMediaRecorderRef.current.state !== "inactive") return { ok: false, reason: "recorder_active" };
    if (liveStatusRef.current === "processing" || liveStatusRef.current === "finalizing_audio" || liveStatusRef.current === "playing_response" || liveStatusRef.current === "ending") {
      return { ok: false, reason: `status_${liveStatusRef.current}` };
    }
    return { ok: true, reason: "ok" };
  }

  function heartbeatIntervalMs() {
    return liveStatusInfo?.heartbeatIntervalMs || LIVE_HEARTBEAT_INTERVAL_MS;
  }

  function staleAfterMs() {
    return liveStatusInfo?.staleAfterMs || LIVE_STALE_AFTER_MS;
  }

  function startLiveHeartbeatLoop() {
    clearLiveHeartbeatTimers();
    liveLastHeartbeatAtRef.current = Date.now();
    liveHeartbeatTimerRef.current = window.setInterval(() => {
      const socket = liveSocketRef.current;
      if (!socket || socket.readyState !== WebSocket.OPEN || liveEndedByUserRef.current) {
        return;
      }
      try {
        socket.send(JSON.stringify({ type: "heartbeat" }));
      } catch {
        // noop; stale check handles user-facing state
      }
    }, heartbeatIntervalMs());
    liveStaleCheckTimerRef.current = window.setInterval(() => {
      const socket = liveSocketRef.current;
      if (!socket || socket.readyState !== WebSocket.OPEN || liveEndedByUserRef.current) {
        return;
      }
      const lastHeartbeatAt = liveLastHeartbeatAtRef.current;
      if (lastHeartbeatAt && Date.now() - lastHeartbeatAt > staleAfterMs()) {
        appendLiveEvent("heartbeat_stale");
        setLiveError("WebSocket disconnected. The live voice session became stale.");
        updateLiveStatus("error");
        cleanupLiveSessionResources();
        socket.close();
        liveSocketRef.current = null;
      }
    }, Math.max(3000, Math.floor(heartbeatIntervalMs() / 2)));
  }

  function scheduleLiveListeningResume(reason: string, delayMs = LIVE_LISTEN_RESUME_DELAY_MS) {
    clearLiveResumeTimer();
    const resumeCheck = canAutoResumeListening();
    if (!resumeCheck.ok) {
      appendLiveEvent(`auto_listening_resume_skipped ${resumeCheck.reason}`);
      return;
    }
    liveResumeTimerRef.current = window.setTimeout(() => {
      liveResumeTimerRef.current = null;
      const delayedCheck = canAutoResumeListening();
      if (!delayedCheck.ok) {
        appendLiveEvent(`auto_listening_resume_skipped ${delayedCheck.reason}`);
        return;
      }
      appendLiveEvent(`auto_listening_resume ${reason}`);
      void startLiveMic({ automatic: true, reason });
    }, delayMs);
  }

  async function playLiveResponse(autoTriggered = false) {
    const audioElement = liveAudioElementRef.current;
    if (!audioElement || !liveAudioDataUrl) {
      return;
    }
    try {
      if (liveStatusRef.current === "listening" || liveStatusRef.current === "speech_detected") {
        stopLiveStream();
      }
      audioElement.currentTime = 0;
      setLivePlaybackError(null);
      updateLiveStatus("playing_response");
      appendLiveEvent("response_play_started");
      await audioElement.play();
    } catch {
      const warning = "Assistant audio autoplay was blocked by the browser. Use Play Response to continue.";
      setLivePlaybackError(warning);
      setLiveInfo("Assistant audio is ready. Click Play Response if browser autoplay was blocked.");
      appendLiveEvent("response_play_blocked");
      if (autoTriggered) {
        scheduleLiveListeningResume("autoplay_blocked");
      }
    }
  }

  function handleLiveResponseEnded() {
    appendLiveEvent("response_play_ended");
    if (liveAutoResumeRef.current) {
      updateLiveStatus("session_started");
      setLiveInfo("Listening again…");
      scheduleLiveListeningResume("playback_ended");
    } else {
      updateLiveStatus("session_started");
    }
  }

  function stopAudioMonitoring() {
    if (monitoringIntervalRef.current != null) {
      window.clearInterval(monitoringIntervalRef.current);
      monitoringIntervalRef.current = null;
    }
    sourceNodeRef.current?.disconnect();
    analyserRef.current?.disconnect();
    sourceNodeRef.current = null;
    analyserRef.current = null;
    levelDataRef.current = null;
    if (audioContextRef.current) {
      void audioContextRef.current.close().catch(() => undefined);
      audioContextRef.current = null;
    }
    setMicLevel(0);
    setMicPeak(0);
    setSpeechDetected(false);
    setSilenceDetected(false);
  }

  function startAudioMonitoring(stream: MediaStream, mode: "file" | "live") {
    stopAudioMonitoring();
    const AudioContextCtor = window.AudioContext || (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextCtor) {
      return;
    }
    const contextInstance = new AudioContextCtor();
    const source = contextInstance.createMediaStreamSource(stream);
    const analyser = contextInstance.createAnalyser();
    analyser.fftSize = 2048;
    const data = new Uint8Array(new ArrayBuffer(analyser.fftSize));
    source.connect(analyser);
    audioContextRef.current = contextInstance;
    sourceNodeRef.current = source;
    analyserRef.current = analyser;
    levelDataRef.current = data;
    monitoringIntervalRef.current = window.setInterval(() => {
      if (!analyserRef.current || !levelDataRef.current) return;
      analyserRef.current.getByteTimeDomainData(levelDataRef.current);
      let sumSquares = 0;
      let peak = 0;
      for (const sample of levelDataRef.current) {
        const normalized = (sample - 128) / 128;
        const absolute = Math.abs(normalized);
        sumSquares += normalized * normalized;
        if (absolute > peak) {
          peak = absolute;
        }
      }
      const rms = Math.sqrt(sumSquares / levelDataRef.current.length);
      setMicLevel(rms);
      setMicPeak(peak);

      if (mode === "file") {
        fileRecordingStatsRef.current.rmsTotal += rms;
        fileRecordingStatsRef.current.rmsSamples += 1;
        fileRecordingStatsRef.current.peakLevel = Math.max(fileRecordingStatsRef.current.peakLevel, peak);
        setSilenceDetected(rms < FILE_SILENCE_THRESHOLD);
        setSpeechDetected(rms >= FILE_SILENCE_THRESHOLD);
        return;
      }

      const now = Date.now();
      const isSpeechFrame = rms >= LIVE_SPEECH_START_THRESHOLD;
      if (isSpeechFrame) {
        if (!liveSpeechFrameActiveRef.current && liveSpeechDetectedRef.current) {
          appendLiveEvent("vad_speech_resumed");
        }
        liveSpeechFrameActiveRef.current = true;
        if (!liveSpeechDetectedRef.current) {
          liveSpeechDetectedRef.current = true;
          liveSpeechStartedAtRef.current = now;
          liveFirstSpeechAtRef.current = liveFirstSpeechAtRef.current ?? now;
          appendLiveEvent("vad_speech_started");
        }
        liveLastSpeechAtRef.current = now;
        liveAutoStopTriggeredRef.current = false;
        setSpeechDetected(true);
        setSilenceDetected(false);
        if (liveStatusRef.current === "listening" || liveStatusRef.current === "session_started") {
          updateLiveStatus("speech_detected");
        }
      } else {
        if (liveSpeechFrameActiveRef.current && liveSpeechDetectedRef.current) {
          appendLiveEvent("vad_speech_ended");
        }
        liveSpeechFrameActiveRef.current = false;
        setSpeechDetected(false);
        setSilenceDetected(true);
        if (liveStatusRef.current === "speech_detected") {
          updateLiveStatus("listening");
        }
      }

      if (liveSpeechDetectedRef.current && liveLastSpeechAtRef.current && liveSpeechStartedAtRef.current) {
        const speechDuration = now - liveSpeechStartedAtRef.current;
        const silenceDuration = now - liveLastSpeechAtRef.current;
        if (rms <= LIVE_SPEECH_END_THRESHOLD && speechDuration >= LIVE_MIN_SPEECH_MS && silenceDuration >= LIVE_SILENCE_TIMEOUT_MS && !liveAutoStopTriggeredRef.current) {
          liveAutoStopTriggeredRef.current = true;
          appendLiveEvent("silence_timeout");
          stopLiveMic();
          return;
        }
      }

      if (liveSessionStartedAtRef.current && now - liveSessionStartedAtRef.current >= LIVE_MAX_UTTERANCE_MS && !liveAutoStopTriggeredRef.current) {
        liveAutoStopTriggeredRef.current = true;
        appendLiveEvent("max_utterance_reached");
        stopLiveMic();
      }
    }, 100);
  }

  function stopFileStream() {
    fileMediaRecorderRef.current = null;
    if (fileMediaStreamRef.current) {
      fileMediaStreamRef.current.getTracks().forEach((track) => track.stop());
      fileMediaStreamRef.current = null;
    }
    stopAudioMonitoring();
  }

  function stopLiveStream() {
    liveMediaRecorderRef.current = null;
    if (liveMediaStreamRef.current) {
      liveMediaStreamRef.current.getTracks().forEach((track) => track.stop());
      liveMediaStreamRef.current = null;
    }
    stopAudioMonitoring();
  }

  async function startFileRecording() {
    setFileError(null);
    setFileInfo(null);
    setCapturedAudioDebug(null);
    setFileSilenceWarning(null);
    if (capturedAudioUrl) {
      URL.revokeObjectURL(capturedAudioUrl);
      setCapturedAudioUrl(null);
    }
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        setFileError("Microphone recording is not available in this browser. Upload an audio file instead.");
        return;
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: requestedAudioConstraints() });
      await refreshAudioInputDevices();
      const mimeType = selectRecordingMimeType();
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      const deviceLabel = selectedMicrophoneLabel();
      console.info("[voice-test] starting browser recording", {
        selectedMimeType: mimeType || recorder.mimeType || "browser-default",
        deviceLabel,
      });
      setMicDeviceInfo(deviceLabel);
      fileRecordingStatsRef.current = { startedAt: Date.now(), rmsTotal: 0, rmsSamples: 0, peakLevel: 0 };
      startAudioMonitoring(stream, "file");
      fileChunksRef.current = [];
      fileRecordingStopPromiseRef.current = new Promise((resolve) => {
        recorder.onstop = () => {
          const type = recorder.mimeType || fileChunksRef.current[0]?.type || "audio/webm";
          const extension = resolveAudioExtensionFromType(type);
          const blob = new Blob(fileChunksRef.current, { type });
          const chunkSizes = fileChunksRef.current.map((chunk) => chunk.size);
          const durationMs = Math.max(0, Date.now() - fileRecordingStatsRef.current.startedAt);
          const averageRms = fileRecordingStatsRef.current.rmsSamples > 0
            ? fileRecordingStatsRef.current.rmsTotal / fileRecordingStatsRef.current.rmsSamples
            : 0;
          const peakLevel = fileRecordingStatsRef.current.peakLevel;
          const silent = averageRms < FILE_SILENCE_THRESHOLD && peakLevel < FILE_SILENCE_THRESHOLD * 2;
          console.info("[voice-test] browser recording captured", {
            mimeType: recorder.mimeType,
            blobType: blob.type,
            extension,
            sizeBytes: blob.size,
            chunkCount: fileChunksRef.current.length,
            chunkSizes,
            durationMs,
            averageRms,
            peakLevel,
            deviceLabel,
          });
          const recordedFile = new File([blob], `voice-test-${Date.now()}.${extension}`, { type });
          console.info("[voice-test] prepared upload file", {
            mimeType: type,
            extension,
            sizeBytes: recordedFile.size,
          });
          if (capturedAudioUrl) {
            URL.revokeObjectURL(capturedAudioUrl);
          }
          const objectUrl = URL.createObjectURL(blob);
          setCapturedAudioUrl(objectUrl);
          setCapturedAudioDebug({
            deviceLabel,
            recorderMimeType: recorder.mimeType || type,
            chunkCount: fileChunksRef.current.length,
            chunkSizes,
            finalBlobSize: blob.size,
            finalBlobType: blob.type || type,
            uploadFilename: recordedFile.name,
            uploadMimeType: type,
            durationMs,
            averageRms,
            peakLevel,
            silent,
          });
          setFileSilenceWarning(silent ? "Recorded audio appears silent. Check the selected microphone device, permission, and input level before sending." : null);
          setFileCaptureInfo(`Captured ${recorder.mimeType || blob.type || type} (${formatSize(blob.size)}) • Uploading original ${type || "unknown"} as ${recordedFile.name} (${formatSize(recordedFile.size)}).`);
          setSelectedFile(recordedFile);
          setFileInfo("Microphone recording captured. Review, download if needed, and send it to the backend voice harness.");
          setFileRecording(false);
          fileRecordingStopPromiseRef.current = null;
          stopFileStream();
          resolve();
        };
      });
      recorder.ondataavailable = (event) => {
        if (event.data.size <= 0) return;
        fileChunksRef.current.push(event.data);
        console.info("[voice-test] recorder dataavailable", {
          chunkCount: fileChunksRef.current.length,
          chunkSizeBytes: event.data.size,
          chunkType: event.data.type || recorder.mimeType || "unknown",
        });
      };
      fileMediaStreamRef.current = stream;
      fileMediaRecorderRef.current = recorder;
      recorder.start();
      setFileRecording(true);
    } catch {
      stopFileStream();
      setFileRecording(false);
      setFileError("Microphone access was denied. You can still upload an audio file.");
    }
  }

  function stopFileRecording() {
    if (fileMediaRecorderRef.current && fileMediaRecorderRef.current.state !== "inactive") {
      fileMediaRecorderRef.current.stop();
      void fileRecordingStopPromiseRef.current;
      return;
    }
    stopFileStream();
    setFileRecording(false);
  }

  function handleFileSelected(file: File | null) {
    if (!file) return;
    const contentType = file.type?.toLowerCase?.() ?? "";
    const extension = file.name.toLowerCase();
    const supported =
      SUPPORTED_AUDIO_TYPES.includes(contentType) ||
      extension.endsWith(".wav") ||
      extension.endsWith(".webm") ||
      extension.endsWith(".ogg") ||
      extension.endsWith(".mp3") ||
      extension.endsWith(".m4a");
    if (!supported) {
      setFileError("Unsupported audio file. Use wav, webm, mp3, or m4a.");
      return;
    }
    setSelectedFile(file);
    setFileCaptureInfo(`Selected ${file.type || "unknown"} file for upload (${formatSize(file.size)}).`);
    setCapturedAudioDebug(null);
    setFileSilenceWarning(null);
    if (capturedAudioUrl) {
      URL.revokeObjectURL(capturedAudioUrl);
      setCapturedAudioUrl(null);
    }
    setFileResult(null);
    setSttDebugResult(null);
    setFileError(null);
    setFileInfo(`Loaded ${file.name}. Send it to run the STT to LLM to TTS loop.`);
  }

  async function submitFileTest() {
    if (!accessToken || !tenantId || !selectedFile) return;
    console.info("[voice-test] submitting file", {
      filename: selectedFile.name,
      mimeType: selectedFile.type,
      sizeBytes: selectedFile.size,
    });
    setFileLoading(true);
    setFileError(null);
    setFileInfo(null);
    setFileResult(null);
    setSttDebugResult(null);
    setProcessingStage("Processing STT/LLM/TTS...");
    try {
      const response = await runVoiceTest(accessToken, tenantId, { audio: selectedFile, context, language, workflowMode });
      setFileResult(response);
      setFileInfo(response.audioBase64 ? "Voice loop completed. Transcript, assistant response, and audio are ready." : "Voice loop completed. Text response is available, but no TTS audio was generated.");
      void refreshVoiceStatus(false);
    } catch (err) {
      setFileError(toVoiceError(err));
    } finally {
      setProcessingStage(null);
      setFileLoading(false);
    }
  }

  async function submitSttDebug() {
    if (!accessToken || !tenantId || !selectedFile) return;
    setFileLoading(true);
    setFileError(null);
    setFileInfo(null);
    setSttDebugResult(null);
    setProcessingStage("Running STT debug...");
    try {
      const response = await runVoiceSttDebug(accessToken, tenantId, { audio: selectedFile, language });
      setSttDebugResult(response);
      setFileInfo("STT-only debug completed. Review the trace below.");
    } catch (err) {
      setFileError(toVoiceError(err));
    } finally {
      setProcessingStage(null);
      setFileLoading(false);
    }
  }

  function resetFileTest() {
    if (capturedAudioUrl) {
      URL.revokeObjectURL(capturedAudioUrl);
      setCapturedAudioUrl(null);
    }
    setSelectedFile(null);
    setFileResult(null);
    setSttDebugResult(null);
    setFileError(null);
    setFileInfo(null);
    setFileCaptureInfo(null);
    setCapturedAudioDebug(null);
    setFileSilenceWarning(null);
    setContext("");
    setProcessingStage(null);
  }

  async function refreshVoiceStatus(warmup: boolean) {
    if (!accessToken || !tenantId) return;
    setStatusLoading(true);
    setFileError(null);
    try {
      const response = await getVoiceTestStatus(accessToken, tenantId, warmup);
      setVoiceStatus(response);
      if (warmup) {
        setFileInfo("Local voice services checked. First request should now be faster if models were still loading.");
      }
    } catch (err) {
      setFileError(toVoiceError(err));
    } finally {
      setStatusLoading(false);
    }
  }

  async function refreshLiveStatus() {
    if (!accessToken || !tenantId) return;
    try {
      const response = await getVoiceLiveStatus(accessToken, tenantId);
      setLiveStatusInfo(response);
    } catch {
      setLiveStatusInfo(null);
    }
  }

  function bindLiveSocket(socket: WebSocket) {
    socket.onopen = () => {
      updateLiveStatus("connecting");
      appendLiveEvent("Live voice session connected.");
      appendLiveEvent("session_start");
      socket.send(JSON.stringify({
        type: "session.start",
        language,
        context: context.trim() || "clinic receptionist test",
        workflowMode,
      }));
    };
    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data as string) as Record<string, unknown>;
        const type = String(payload.type || "");
        if (type === "session.started") {
          const sessionId = String(payload.sessionId || "");
          setLiveSessionId(sessionId || null);
          appendLiveEvent("session_started");
          updateLiveStatus("session_started");
          setLiveInfo(`Session started for ${selectedLanguageLabel()} in ${selectedWorkflowLabel()}.`);
          startLiveHeartbeatLoop();
          if (liveAutoResumeRef.current) {
            scheduleLiveListeningResume("session_started", 0);
          }
          return;
        }
        if (type === "heartbeat") {
          liveLastHeartbeatAtRef.current = Date.now();
          return;
        }
        if (type === "session.connected") {
          setLiveInfo("WebSocket authenticated and tenant context resolved.");
          appendLiveEvent("AUTHENTICATED • tenant resolved.");
          return;
        }
        if (type === "transcript.partial") {
          appendLiveEvent(String(payload.text || "Receiving audio"));
          return;
        }
        if (type === "audio.chunk.received") {
          const sequence = Number(payload.sequence || 0);
          const totalChunks = Number(payload.totalChunks || 0);
          appendLiveEvent(`CHUNK_RECEIVED ${sequence}/${totalChunks}.`);
          return;
        }
        if (type === "turn.audio.received") {
          appendLiveEvent(`turn_audio_received ${Number(payload.sequence || 0)}/${Number(payload.totalChunks || 0)}`);
          return;
        }
        if (type === "audio.buffer.complete") {
          const totalChunks = Number(payload.totalChunks || 0);
          const sizeBytes = Number(payload.sizeBytes || 0);
          appendLiveEvent(`Audio buffered ${totalChunks} chunks • ${formatSize(sizeBytes)}.`);
          return;
        }
        if (type === "audio.decoded") {
          const sizeBytes = Number(payload.sizeBytes || 0);
          appendLiveEvent(`AUDIO_DECODED • ${formatSize(sizeBytes)}.`);
          return;
        }
        if (type === "stt.started") {
          updateLiveStatus("processing");
          setLiveInfo("Processing…");
          appendLiveEvent("STT_STARTED");
          return;
        }
        if (type === "turn.started") {
          const turnIndex = Number(payload.turnIndex || 0);
          liveCurrentTurnRef.current = turnIndex > 0 ? turnIndex : null;
          livePendingTranscriptRef.current = "";
          livePendingAssistantRef.current = "";
          livePendingProviderTraceRef.current = null;
          livePendingRequestIdRef.current = null;
          appendLiveEvent(`turn_started ${turnIndex || "?"}`);
          return;
        }
        if (type === "transcript.final") {
          const text = String(payload.text || "");
          const turnIndex = Number(payload.turnIndex || liveCurrentTurnRef.current || 0);
          liveCurrentTurnRef.current = turnIndex > 0 ? turnIndex : liveCurrentTurnRef.current;
          livePendingTranscriptRef.current = text;
          setLiveTranscript(text);
          appendLiveEvent("Transcript finalized.");
          return;
        }
        if (type === "stt.complete") {
          appendLiveEvent(`STT_COMPLETE • provider=${formatProvider(String(payload.provider || ""))}`);
          return;
        }
        if (type === "turn.stt.complete") {
          appendLiveEvent(`turn_stt_complete ${Number(payload.turnIndex || 0) || "?"} • provider=${formatProvider(String(payload.provider || ""))}`);
          return;
        }
        if (type === "assistant.text") {
          const text = String(payload.text || "");
          const trace = (payload.providerTrace as VoiceProviderTrace | null) ?? null;
          const workflowSummary = (payload.workflowSummary as VoiceWorkflowSummary | null) ?? null;
          const requestId = typeof payload.requestId === "string" ? payload.requestId : null;
          livePendingAssistantRef.current = text;
          livePendingProviderTraceRef.current = trace;
          livePendingRequestIdRef.current = requestId;
          setLiveAssistantText(text);
          setLiveProviderTrace(trace);
          setLiveWorkflowSummary(workflowSummary);
          updateLiveStatus("processing");
          setLiveInfo("Processing…");
          appendLiveEvent("Assistant text received.");
          return;
        }
        if (type === "turn.llm.complete") {
          appendLiveEvent(`turn_llm_complete ${Number(payload.turnIndex || 0) || "?"} • provider=${formatProvider(String(payload.provider || ""))}`);
          return;
        }
        if (type === "assistant.audio.chunk") {
          const sequence = Number(payload.sequence || 0);
          const totalChunks = Number(payload.totalChunks || 0);
          const audioBase64Chunk = String(payload.audioBase64Chunk || "");
          if (sequence > 0 && audioBase64Chunk) {
            liveTurnHasAudioRef.current = true;
            liveAssistantAudioChunksRef.current.set(sequence, audioBase64Chunk);
            if (totalChunks > 0) {
              liveAssistantAudioExpectedChunksRef.current = totalChunks;
            }
            appendLiveEvent(`Assistant audio chunk ${sequence}/${totalChunks || "?"} received.`);
          }
          return;
        }
        if (type === "assistant.audio.end") {
          const contentType = String(payload.contentType || "audio/mpeg");
          const totalChunks = liveAssistantAudioExpectedChunksRef.current;
          if (totalChunks <= 0) {
            setLiveError("Assistant audio response was incomplete.");
            appendLiveEvent("Assistant audio ended without chunk metadata.");
            return;
          }
          const chunks: string[] = [];
          for (let index = 1; index <= totalChunks; index += 1) {
            const chunk = liveAssistantAudioChunksRef.current.get(index);
            if (!chunk) {
              setLiveError(`Assistant audio response is missing chunk ${index}.`);
              appendLiveEvent(`Assistant audio missing chunk ${index}.`);
              return;
            }
            chunks.push(chunk);
          }
          setLiveAudioDataUrl(`data:${contentType};base64,${chunks.join("")}`);
          livePendingAutoPlayRef.current = true;
          liveAssistantAudioChunksRef.current.clear();
          liveAssistantAudioExpectedChunksRef.current = 0;
          setLiveProviderTrace((payload.providerTrace as VoiceProviderTrace | null) ?? null);
          setLiveInfo("Playing assistant response…");
          appendLiveEvent("response_audio_received");
          appendLiveEvent(`Assistant audio reconstructed from ${totalChunks} chunk${totalChunks === 1 ? "" : "s"}.`);
          return;
        }
        if (type === "turn.tts.complete") {
          appendLiveEvent(`turn_tts_complete ${Number(payload.turnIndex || 0) || "?"} • provider=${formatProvider(String(payload.provider || ""))}`);
          return;
        }
        if (type === "assistant.audio") {
          const audioBase64 = String(payload.audioBase64 || "");
          const contentType = String(payload.contentType || "audio/mpeg");
          if (audioBase64) {
            liveTurnHasAudioRef.current = true;
            setLiveAudioDataUrl(`data:${contentType};base64,${audioBase64}`);
            livePendingAutoPlayRef.current = true;
            appendLiveEvent("response_audio_received");
          }
          setLiveProviderTrace((payload.providerTrace as VoiceProviderTrace | null) ?? null);
          appendLiveEvent("Assistant audio received.");
          return;
        }
        if (type === "session.completed") {
          appendLiveEvent("Session completed.");
          return;
        }
        if (type === "session.timeout") {
          updateLiveStatus("error");
          const reason = String(payload.reason || "session_timeout");
          setLiveError(reason === "idle_timeout" ? "Session timed out due to inactivity." : "Session timed out.");
          appendLiveEvent(`session_timeout ${reason}`);
          return;
        }
        if (type === "turn.complete") {
          const turnIndex = Number(payload.turnIndex || liveCurrentTurnRef.current || 0);
          const trace = (payload.providerTrace as VoiceProviderTrace | null) ?? livePendingProviderTraceRef.current;
          const workflowSummary = (payload.workflowSummary as VoiceWorkflowSummary | null) ?? liveWorkflowSummary;
          const requestId = typeof payload.requestId === "string"
            ? payload.requestId
            : livePendingRequestIdRef.current;
          const metrics = (payload.metrics as LiveTurnMetrics | null) ?? null;
          const mergedMetrics = metrics
            ? { ...metrics, captureDurationMs: liveCaptureDurationMsRef.current || metrics.captureDurationMs || 0 }
            : null;
          setLiveTurnMetrics(mergedMetrics);
          if (turnIndex > 0) {
            setLiveConversation((current) => [
              ...current,
              {
                turnIndex,
                transcript: livePendingTranscriptRef.current,
                assistantText: livePendingAssistantRef.current,
                providerTrace: trace,
                requestId,
                metrics: mergedMetrics,
                workflowSummary,
              },
            ]);
          }
          setLiveWorkflowSummary(workflowSummary);
          if (!liveTurnHasAudioRef.current) {
            updateLiveStatus("session_started");
            setLiveInfo("Listening again…");
            if (liveAutoResumeRef.current) {
              scheduleLiveListeningResume("turn_complete_no_audio");
            }
          }
          if (mergedMetrics) {
            appendLiveEvent(`turn_metrics total=${mergedMetrics.totalDurationMs}ms stt=${mergedMetrics.sttDurationMs}ms llm=${mergedMetrics.llmDurationMs}ms tts=${mergedMetrics.ttsDurationMs}ms`);
          }
          appendLiveEvent(`turn_complete ${turnIndex || "?"}`);
          return;
        }
        if (type === "session.closed") {
          updateLiveStatus("ended");
          appendLiveEvent("Session closed.");
          return;
        }
        if (type === "session.ended") {
          appendLiveEvent("session_ended");
          setLiveInfo("Session ended.");
          return;
        }
        if (type === "error") {
          updateLiveStatus("error");
          const rawMessage = String(payload.message || "Live voice test failed.");
          const normalizedMessage = rawMessage.toLowerCase();
          if (normalizedMessage.includes("audio") && normalizedMessage.includes("empty")) {
            setLiveError("No speech was captured. Check the microphone and try again.");
          } else if (normalizedMessage.includes("transcribed")) {
            setLiveError("STT failed. Please try again.");
          } else if (normalizedMessage.includes("synth")) {
            setLiveError("TTS failed. Text response is still available.");
          } else if (normalizedMessage.includes("timed out")) {
            setLiveError("Session timed out.");
          } else {
            setLiveError(rawMessage);
          }
          appendLiveEvent("Error received from websocket server.");
          return;
        }
      } catch {
        updateLiveStatus("error");
        setLiveError("Live websocket returned an unreadable message.");
      }
    };
    socket.onerror = () => {
      updateLiveStatus("error");
      const details = liveStatusInfo
        ? `Expected path ${liveStatusInfo.websocketPath} with ${liveStatusInfo.authMode} and ${liveStatusInfo.tenantMode}.`
        : "Check tenant selection and authentication.";
      setLiveError(`WebSocket disconnected. ${details}`);
      appendLiveEvent("CONNECT_FAILED");
    };
    socket.onclose = (event) => {
      cleanupLiveSessionResources();
      liveSocketRef.current = null;
      liveAutoResumeRef.current = false;
      updateLiveStatus(liveEndedByUserRef.current ? "ended" : "idle");
      liveAssistantAudioChunksRef.current.clear();
      liveAssistantAudioExpectedChunksRef.current = 0;
      if (event.code === 1009) {
        updateLiveStatus("error");
        setLiveError("WebSocket message too large. Audio/response chunking failed.");
        appendLiveEvent(`DISCONNECTED • code=${event.code} reason=message too large`);
        return;
      }
      if (event.reason) {
        if (!liveEndedByUserRef.current) {
          updateLiveStatus("error");
        }
        setLiveError(`WebSocket disconnected. ${event.reason}`);
        appendLiveEvent(`DISCONNECTED • code=${event.code} reason=${event.reason}`);
      } else {
        appendLiveEvent(`DISCONNECTED • code=${event.code}`);
      }
    };
  }

  function ensureLiveSocket() {
    if (!accessToken || !tenantId) {
      throw new Error("Select a clinic tenant before starting the live voice test.");
    }
    if (liveSocketRef.current && liveSocketRef.current.readyState === WebSocket.OPEN) {
      return liveSocketRef.current;
    }
    setLiveError(null);
    setLiveInfo(null);
    updateLiveStatus("connecting");
    const websocketUrl = resolveWebSocketUrl(accessToken, tenantId);
    appendLiveEvent(`CONNECTING • ${maskWebSocketUrl(websocketUrl)}`);
    const socket = new WebSocket(websocketUrl);
    liveSocketRef.current = socket;
    bindLiveSocket(socket);
    return socket;
  }

  async function startLiveSession() {
    setLiveTranscript("");
    setLiveAssistantText("");
    setLiveAudioDataUrl(null);
    setLivePlaybackError(null);
    setLiveProviderTrace(null);
    setLiveEvents([]);
    setLiveCaptureInfo(null);
    setLiveCapturedAudioDebug(null);
    setLiveConversation([]);
    setLiveWorkflowSummary(null);
    setLiveTurnMetrics(null);
    liveChunkSequenceRef.current = 0;
    liveChunkFilenameRef.current = null;
    liveChunkContentTypeRef.current = "audio/webm";
    liveRecordedBytesRef.current = 0;
    liveSendQueueRef.current = Promise.resolve();
    liveAssistantAudioChunksRef.current.clear();
    liveAssistantAudioExpectedChunksRef.current = 0;
    liveCurrentTurnRef.current = null;
    livePendingTranscriptRef.current = "";
    livePendingAssistantRef.current = "";
    livePendingProviderTraceRef.current = null;
    livePendingRequestIdRef.current = null;
    liveTurnHasAudioRef.current = false;
    liveSpeechStartedAtRef.current = null;
    liveFirstSpeechAtRef.current = null;
    liveLastSpeechAtRef.current = null;
    liveSpeechDetectedRef.current = false;
    liveSpeechFrameActiveRef.current = false;
    liveAutoStopTriggeredRef.current = false;
    liveSessionStartedAtRef.current = null;
    setSpeechDetected(false);
    setSilenceDetected(false);
    liveAutoResumeRef.current = true;
    liveEndedByUserRef.current = false;
    updateLiveStatus("connecting");
    ensureLiveSocket();
  }

  async function startLiveMic(options?: { automatic?: boolean; reason?: string }) {
    if (liveStartingMicRef.current) {
      return;
    }
    if (
      liveStatusRef.current === "listening" ||
      liveStatusRef.current === "speech_detected" ||
      liveStatusRef.current === "processing" ||
      liveStatusRef.current === "finalizing_audio" ||
      liveStatusRef.current === "playing_response" ||
      liveStatusRef.current === "ending"
    ) {
      return;
    }
    setLiveError(null);
    setLiveInfo(null);
    try {
      liveStartingMicRef.current = true;
      clearLiveResumeTimer();
      const socket = ensureLiveSocket();
      if (!navigator.mediaDevices?.getUserMedia) {
        setLiveError("Microphone recording is not available in this browser.");
        return;
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: requestedAudioConstraints() });
      await refreshAudioInputDevices();
      const mimeType = selectRecordingMimeType();
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      const recordingType = recorder.mimeType || mimeType || "audio/webm";
      const filename = `voice-live-${Date.now()}.${resolveAudioExtensionFromType(recordingType)}`;
      console.info("[voice-live] starting browser recording", {
        selectedMimeType: mimeType || recorder.mimeType || "browser-default",
        deviceLabel: selectedMicrophoneLabel(),
      });
      setMicDeviceInfo(selectedMicrophoneLabel());
      setLiveCapturedAudioDebug(null);
      liveChunkSequenceRef.current = 0;
      liveChunkFilenameRef.current = filename;
      liveChunkContentTypeRef.current = recordingType;
      liveRecordedBytesRef.current = 0;
      liveChunksRef.current = [];
      liveSendQueueRef.current = Promise.resolve();
      livePendingTranscriptRef.current = "";
      livePendingAssistantRef.current = "";
      livePendingProviderTraceRef.current = null;
      livePendingRequestIdRef.current = null;
      liveTurnHasAudioRef.current = false;
      liveSpeechStartedAtRef.current = null;
      liveFirstSpeechAtRef.current = null;
      liveLastSpeechAtRef.current = null;
      liveSpeechDetectedRef.current = false;
      liveSpeechFrameActiveRef.current = false;
      liveAutoStopTriggeredRef.current = false;
      liveSessionStartedAtRef.current = Date.now();
      liveCaptureStartedAtRef.current = Date.now();
      liveCaptureDurationMsRef.current = 0;
      startAudioMonitoring(stream, "live");
      appendLiveEvent(options?.automatic ? "auto_listening_start" : "vad_listening");
      setLiveInfo("Listening… speak now");
      setLiveCaptureInfo(`Recording ${recordingType} from ${selectedMicrophoneLabel()} • final upload will use ${filename}.`);
      recorder.ondataavailable = (event) => {
        if (!event.data || event.data.size === 0) return;
        liveChunksRef.current.push(event.data);
        liveRecordedBytesRef.current += event.data.size;
        const capturedType = event.data.type || recordingType || "audio/webm";
        liveChunkContentTypeRef.current = capturedType;
        setLiveCaptureInfo(`Recording ${capturedType} • buffered ${liveChunksRef.current.length} chunk${liveChunksRef.current.length === 1 ? "" : "s"} (${formatSize(liveRecordedBytesRef.current)}) before final upload.`);
        console.info("[voice-live] recorder dataavailable", {
          chunkCount: liveChunksRef.current.length,
          chunkSizeBytes: event.data.size,
          chunkType: capturedType,
        });
      };
      recorder.onstop = () => {
        const finalType = liveChunkContentTypeRef.current || recorder.mimeType || recordingType || "audio/webm";
        const blob = new Blob(liveChunksRef.current, { type: finalType });
        const chunkSizes = liveChunksRef.current.map((chunk) => chunk.size);
        liveCaptureDurationMsRef.current = liveCaptureStartedAtRef.current ? Math.max(0, Date.now() - liveCaptureStartedAtRef.current) : 0;
        setLiveCapturedAudioDebug({
          recorderMimeType: recorder.mimeType || finalType,
          chunkCount: liveChunksRef.current.length,
          chunkSizes,
          finalBlobSize: blob.size,
          finalBlobType: blob.type || finalType,
          uploadFilename: filename,
          uploadMimeType: finalType,
        });
        console.info("[voice-live] browser recording captured", {
          mimeType: recorder.mimeType,
          chunkCount: liveChunksRef.current.length,
          chunkSizes,
          blobType: blob.type || finalType,
          filename,
          sizeBytes: blob.size,
        });
        stopLiveStream();
        if (blob.size === 0 || liveChunksRef.current.length === 0) {
          updateLiveStatus("session_started");
          setLiveInfo("No recorded audio was captured. Choose the active microphone and speak clearly before stopping.");
          appendLiveEvent("NO_SPEECH_DETECTED");
          if (liveAutoResumeRef.current) {
            scheduleLiveListeningResume("empty_turn");
          }
          return;
        }
        if (!liveSpeechDetectedRef.current) {
          updateLiveStatus("session_started");
          setLiveInfo("No speech detected. Choose the active microphone and speak clearly before stopping.");
          appendLiveEvent("NO_SPEECH_DETECTED");
          if (liveAutoResumeRef.current) {
            scheduleLiveListeningResume("silent_turn");
          }
          return;
        }
        updateLiveStatus("finalizing_audio");
        setLiveInfo("Processing…");
        setLiveCaptureInfo(`Captured ${blob.type || finalType} • Uploading finalized ${blob.type || finalType} as ${filename} (${formatSize(blob.size)}).`);
        appendLiveEvent("audio_finalizing");
        appendLiveEvent(`audio_blob_finalized mime=${blob.type || finalType} size=${formatSize(blob.size)} chunks=${liveChunksRef.current.length}`);
        void liveSendQueueRef.current.then(() => {
          if (socket.readyState !== WebSocket.OPEN) {
            throw new Error("Live websocket session is not connected.");
          }
          updateLiveStatus("processing");
          return blobToBase64(blob).then((audioBase64) => {
            const base64Chunks = splitBase64Chunks(audioBase64);
            base64Chunks.forEach((audioBase64Chunk) => {
              liveChunkSequenceRef.current += 1;
              socket.send(JSON.stringify({
                type: "audio.chunk",
                sequence: liveChunkSequenceRef.current,
                totalChunks: base64Chunks.length,
                contentType: blob.type || finalType,
                filename,
                audioBase64Chunk,
              }));
            });
            appendLiveEvent(`turn_sent chunks=${base64Chunks.length} size=${formatSize(blob.size)} language=${selectedLanguageLabel()}`);
            socket.send(JSON.stringify({
              type: "audio.end",
              filename,
              contentType: blob.type || finalType,
              totalChunks: liveChunkSequenceRef.current,
            }));
            appendLiveEvent("AUDIO_END_SENT");
            appendLiveEvent(`Audio stream ended. Uploaded ${liveChunkSequenceRef.current} websocket chunk${liveChunkSequenceRef.current === 1 ? "" : "s"} • waiting for transcript and response.`);
          });
        }).catch(() => {
          updateLiveStatus("error");
          setLiveError("STT preparation failed. Live microphone audio could not be prepared for upload.");
          if (liveAutoResumeRef.current) {
            scheduleLiveListeningResume("turn_send_failed");
          }
        });
      };
      liveMediaStreamRef.current = stream;
      liveMediaRecorderRef.current = recorder;
      recorder.start();
      updateLiveStatus("listening");
      appendLiveEvent(`recorder_started ${options?.reason || (options?.automatic ? "automatic" : "manual")}`);
    } catch {
      stopLiveStream();
      updateLiveStatus("error");
      setLiveError("Microphone permission was denied. You can still use the file-based voice test.");
    } finally {
      liveStartingMicRef.current = false;
    }
  }

  function stopLiveMic() {
    if (liveMediaRecorderRef.current && liveMediaRecorderRef.current.state !== "inactive") {
      appendLiveEvent("recorder_stopped");
      updateLiveStatus("finalizing_audio");
      liveMediaRecorderRef.current.stop();
      return;
    }
    stopLiveStream();
    liveSendQueueRef.current = Promise.resolve();
    updateLiveStatus("session_started");
  }

  function closeLiveSession(resetState = true) {
    liveAutoResumeRef.current = false;
    liveEndedByUserRef.current = true;
    updateLiveStatus("ending");
    cleanupLiveSessionResources();
    if (liveSocketRef.current && liveSocketRef.current.readyState === WebSocket.OPEN) {
      liveSocketRef.current.send(JSON.stringify({ type: "session.close" }));
      liveSocketRef.current.close();
    }
    liveSocketRef.current = null;
    updateLiveStatus("ended");
    if (resetState) {
      setLiveInfo(null);
      setLiveCaptureInfo(null);
      setLivePlaybackError(null);
      setLiveWorkflowSummary(null);
      appendLiveEvent("session_ended");
      appendLiveEvent("Live voice session closed by user.");
    }
  }

  if (!tenantId) {
    return <Alert severity="info">Select a clinic tenant to run the voice test harness.</Alert>;
  }

  if (!canUseVoiceTest) {
    return <Alert severity="error">You do not have permission to use the AI voice test harness.</Alert>;
  }

  return (
    <Stack spacing={2.5}>
      <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>
            Voice Test
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Phase 1 file harness and Phase 2 websocket microphone simulation for the future telephony voice path.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Chip size="small" color="primary" label="AI Copilot" />
          <Chip size="small" variant="outlined" label={`Mode: ${selectedWorkflowLabel()}`} />
          <Chip size="small" variant="outlined" label={`STT: ${formatProvider(voiceStatus?.providerTrace?.sttProvider || "faster-whisper")} / Mock`} />
          <Chip size="small" variant="outlined" label={`LLM: ${formatProvider(voiceStatus?.providerTrace?.llmProvider || "gemini")} / Groq / Mock`} />
          <Chip size="small" variant="outlined" label={`TTS: ${formatProvider(voiceStatus?.providerTrace?.ttsProvider || "piper")} / Mock`} />
          <Chip size="small" variant="outlined" label={`Live: ${liveStatusLabel(liveStatus)}`} color={liveStatusTone(liveStatus)} />
        </Stack>
      </Stack>

      <Paper variant="outlined" sx={{ overflow: "hidden" }}>
        <Tabs value={tab} onChange={(_, value: VoiceTab) => setTab(value)} variant="fullWidth">
          <Tab value="file" label="File Test" />
          <Tab value="live" label="Live WebSocket Test" />
        </Tabs>
        <Divider />

        {tab === "file" ? (
          <Box sx={{ p: 2 }}>
            <Stack spacing={2.5}>
              {fileError ? <Alert severity="error">{fileError}</Alert> : null}
              {fileInfo ? <Alert severity="info">{fileInfo}</Alert> : null}
              {processingStage ? <Alert severity="info">{processingStage}</Alert> : null}
              {fileSilenceWarning ? <Alert severity="warning">{fileSilenceWarning}</Alert> : null}

              <Stack direction={{ xs: "column", xl: "row" }} spacing={2} alignItems="stretch">
                <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 0 }}>
                  <Stack spacing={2}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>
                      Input
                    </Typography>
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                      <Button variant="contained" startIcon={<MicRoundedIcon />} onClick={startFileRecording} disabled={fileRecording || fileLoading}>
                        Record microphone
                      </Button>
                      <Button variant="outlined" color="warning" startIcon={<StopRoundedIcon />} onClick={stopFileRecording} disabled={!fileRecording}>
                        Stop recording
                      </Button>
                      <Button variant="outlined" startIcon={<UploadFileRoundedIcon />} onClick={() => fileInputRef.current?.click()} disabled={fileLoading}>
                        Upload audio
                      </Button>
                      <input
                        ref={fileInputRef}
                        hidden
                        type="file"
                        accept=".wav,.webm,.ogg,.mp3,.m4a,audio/*"
                        onChange={(event) => handleFileSelected(event.target.files?.[0] ?? null)}
                      />
                    </Stack>

                    <TextField
                      select
                      size="small"
                      label="Microphone device"
                      value={selectedAudioInputId}
                      onChange={(event) => setSelectedAudioInputId(event.target.value)}
                      helperText={micDeviceInfo ? `Active device: ${micDeviceInfo}` : "Choose the microphone used for browser recording."}
                    >
                      {audioInputDevices.length === 0 ? (
                        <MenuItem value="">Default microphone</MenuItem>
                      ) : (
                        audioInputDevices.map((device) => (
                          <MenuItem key={device.deviceId} value={device.deviceId}>
                            {device.label}
                          </MenuItem>
                        ))
                      )}
                    </TextField>

                    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                        Microphone level
                      </Typography>
                      <LinearProgress variant="determinate" value={Math.min(100, micPeak * 100)} sx={{ height: 10, borderRadius: 999 }} />
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 0.75, display: "block" }}>
                        RMS {formatLevel(micLevel)} • Peak {formatLevel(micPeak)} • {speechDetected ? "Speech detected" : silenceDetected ? "Silence detected" : "Listening"}
                      </Typography>
                    </Paper>

                    <TextField
                      size="small"
                      label="Optional context"
                      multiline
                      minRows={3}
                      value={context}
                      onChange={(event) => setContext(event.target.value)}
                      placeholder="Add short context for the assistant, such as booking intent, clinic FAQ, or follow-up scenario."
                    />

                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
                      <TextField
                        select
                        size="small"
                        label="Language"
                        value={language}
                        onChange={(event) => setLanguage(event.target.value)}
                        helperText="Simple language hint passed to STT/TTS where supported."
                        sx={{ maxWidth: 220 }}
                      >
                        <MenuItem value="en">English</MenuItem>
                        <MenuItem value="hi">Hindi</MenuItem>
                        <MenuItem value="auto">Auto Detect</MenuItem>
                      </TextField>
                      <TextField
                        select
                        size="small"
                        label="Workflow mode"
                        value={workflowMode}
                        onChange={(event) => setWorkflowMode(event.target.value as VoiceWorkflowMode)}
                        helperText="Generic keeps the current assistant flow. Appointment mode collects booking details safely."
                        sx={{ minWidth: 260 }}
                      >
                        <MenuItem value="generic">Generic Assistant</MenuItem>
                        <MenuItem value="appointment-booking">Appointment Booking Assistant</MenuItem>
                      </TextField>
                    </Stack>

                    {language === "hi" ? (
                      <Alert severity={isHindiTtsVoiceConfigured() ? "info" : "warning"}>
                        {hindiTtsStatusMessage()}
                      </Alert>
                    ) : null}

                    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                        Selected audio
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {selectedFile ? `${selectedFile.name} • ${Math.ceil(selectedFile.size / 1024)} KB` : "Record or upload an audio sample to begin."}
                      </Typography>
                      {selectedFile ? (
                        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
                          MIME: {selectedFile.type || "unknown"} • Size: {formatSize(selectedFile.size)}
                        </Typography>
                      ) : null}
                      {fileCaptureInfo ? (
                        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
                          {fileCaptureInfo}
                        </Typography>
                      ) : null}
                      {capturedAudioUrl && capturedAudioDebug ? (
                        <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ mt: 1 }}>
                          <Button
                            component="a"
                            href={capturedAudioUrl}
                            download={capturedAudioDebug.uploadFilename}
                            variant="outlined"
                            size="small"
                          >
                            Download captured audio
                          </Button>
                          <Typography variant="caption" color="text.secondary" sx={{ alignSelf: "center" }}>
                            {capturedAudioDebug.uploadFilename}
                          </Typography>
                        </Stack>
                      ) : null}
                      {capturedAudioUrl ? (
                        <Box sx={{ mt: 1.25 }}>
                          <audio controls src={capturedAudioUrl} style={{ width: "100%" }} />
                        </Box>
                      ) : null}
                    </Paper>

                    {capturedAudioDebug ? (
                      <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                          Browser recording debug
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          MediaRecorder MIME: {capturedAudioDebug.recorderMimeType || "unknown"}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Final blob: {capturedAudioDebug.finalBlobType || "unknown"} • {formatSize(capturedAudioDebug.finalBlobSize)}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Upload file: {capturedAudioDebug.uploadFilename} • {capturedAudioDebug.uploadMimeType || "unknown"}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Duration: {formatDuration(capturedAudioDebug.durationMs)} • Device: {capturedAudioDebug.deviceLabel}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          RMS: {formatLevel(capturedAudioDebug.averageRms)} • Peak: {formatLevel(capturedAudioDebug.peakLevel)} • {capturedAudioDebug.silent ? "Silence warning" : "Voice detected"}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Chunk count: {capturedAudioDebug.chunkCount}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.5, whiteSpace: "pre-wrap" }}>
                          Chunk sizes: {capturedAudioDebug.chunkSizes.length > 0 ? capturedAudioDebug.chunkSizes.map((size) => formatSize(size)).join(", ") : "No chunks captured"}
                        </Typography>
                      </Paper>
                    ) : null}

                    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                      <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5} justifyContent="space-between" alignItems={{ xs: "flex-start", sm: "center" }}>
                        <Box>
                          <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                            Local voice services
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            STT: {voiceStatus?.stt?.message || "Status not loaded"} | TTS: {voiceStatus?.tts?.message || "Status not loaded"}
                          </Typography>
                        </Box>
                        <Button
                          variant="outlined"
                          size="small"
                          startIcon={statusLoading ? <CircularProgress size={14} color="inherit" /> : <AutorenewRoundedIcon />}
                          disabled={statusLoading || fileLoading}
                          onClick={() => void refreshVoiceStatus(true)}
                        >
                          {statusLoading ? "Warming..." : "Warm up local voice services"}
                        </Button>
                      </Stack>
                    </Paper>

                    <Stack direction="row" spacing={1}>
                      <Button
                        variant="contained"
                        startIcon={fileLoading ? <CircularProgress size={16} color="inherit" /> : <SendRoundedIcon />}
                        disabled={fileLoading || !selectedFile}
                        onClick={submitFileTest}
                      >
                        {fileLoading ? "Processing..." : "Send to backend"}
                      </Button>
                      <Button variant="outlined" disabled={fileLoading || !selectedFile} onClick={submitSttDebug}>
                        Debug STT only
                      </Button>
                      <Button variant="text" startIcon={<AutorenewRoundedIcon />} disabled={fileLoading} onClick={resetFileTest}>
                        Reset
                      </Button>
                    </Stack>
                  </Stack>
                </Paper>

                <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 0 }}>
                  <Stack spacing={2}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>
                      Result
                    </Typography>

                    <ResultBlock label="Transcript" value={fileResult?.transcript} empty="Transcript will appear after STT completes." />
                    <ResultBlock label="Assistant response" value={fileResult?.assistantText} empty="Assistant text will appear after LLM completion." />

                    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                        Playback
                      </Typography>
                      {fileAudioUrl ? (
                        <audio controls src={fileAudioUrl} style={{ width: "100%" }} />
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          No playable TTS audio was returned. This is expected when the mock TTS fallback is active.
                        </Typography>
                      )}
                    </Paper>

                    <ProviderTraceBlock trace={fileResult?.providerTrace} requestId={fileResult?.requestId ?? null} />
                    <WorkflowSummaryBlock summary={fileResult?.workflowSummary} />
                    <DebugTracePanel trace={fileResult?.voiceDebugTrace ?? sttDebugResult?.voiceDebugTrace} />
                    {sttDebugResult ? (
                      <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                          STT Debug Result
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Provider: {formatProvider(sttDebugResult.sttProvider)} • Transcript: {sttDebugResult.transcript || "No transcript"}
                        </Typography>
                      </Paper>
                    ) : null}
                  </Stack>
                </Paper>
              </Stack>
            </Stack>
          </Box>
        ) : (
          <Box sx={{ p: 2 }}>
            <Stack spacing={2.5}>
              {liveError ? <Alert severity="error">{liveError}</Alert> : null}
              {liveInfo ? <Alert severity="info">{liveInfo}</Alert> : null}
              {liveCaptureInfo ? <Alert severity="info">{liveCaptureInfo}</Alert> : null}
              {liveStatusInfo ? (
                <Alert severity="info">
                  Live websocket path: <strong>{liveStatusInfo.websocketPath}</strong> • auth: <strong>{liveStatusInfo.authMode}</strong> • tenant: <strong>{liveStatusInfo.tenantMode}</strong> • VAD: <strong>{liveStatusInfo.vadMode}</strong> ({liveStatusInfo.vadProvider}) • heartbeat: <strong>{liveStatusInfo.heartbeatIntervalMs} ms</strong> • idle timeout: <strong>{liveStatusInfo.maxIdleSeconds}s</strong>
                </Alert>
              ) : null}

              <Stack direction={{ xs: "column", lg: "row" }} spacing={2} alignItems="stretch">
                <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 0 }}>
                  <Stack spacing={2}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>
                      Live session
                    </Typography>
                    <TextField
                      size="small"
                      label="Context"
                      value={context}
                      onChange={(event) => setContext(event.target.value)}
                      placeholder="clinic receptionist test"
                    />
                    <TextField
                      select
                      size="small"
                      label="Language"
                      value={language}
                      onChange={(event) => setLanguage(event.target.value)}
                      helperText="English, Hindi, or Auto Detect for live STT/TTS."
                      sx={{ maxWidth: 220 }}
                    >
                      <MenuItem value="en">English</MenuItem>
                      <MenuItem value="hi">Hindi</MenuItem>
                      <MenuItem value="auto">Auto Detect</MenuItem>
                    </TextField>
                    <TextField
                      select
                      size="small"
                      label="Workflow mode"
                      value={workflowMode}
                      onChange={(event) => setWorkflowMode(event.target.value as VoiceWorkflowMode)}
                      helperText="Appointment mode keeps collecting booking details across turns."
                      sx={{ maxWidth: 280 }}
                    >
                      <MenuItem value="generic">Generic Assistant</MenuItem>
                      <MenuItem value="appointment-booking">Appointment Booking Assistant</MenuItem>
                    </TextField>
                    <TextField
                      select
                      size="small"
                      label="Microphone device"
                      value={selectedAudioInputId}
                      onChange={(event) => setSelectedAudioInputId(event.target.value)}
                      helperText={micDeviceInfo ? `Active device: ${micDeviceInfo}` : "Choose the microphone used for live voice testing."}
                    >
                      {audioInputDevices.length === 0 ? (
                        <MenuItem value="">Default microphone</MenuItem>
                      ) : (
                        audioInputDevices.map((device) => (
                          <MenuItem key={device.deviceId} value={device.deviceId}>
                            {device.label}
                          </MenuItem>
                        ))
                      )}
                    </TextField>
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                      <Button variant="contained" startIcon={<LinkRoundedIcon />} onClick={startLiveSession} disabled={liveStatus !== "idle" && liveStatus !== "ended"}>
                        Start session
                      </Button>
                      <Button
                        variant="contained"
                        color="secondary"
                        startIcon={<MicRoundedIcon />}
                        onClick={() => void startLiveMic({ automatic: false, reason: "manual_button" })}
                        disabled={
                          liveStatus === "idle" ||
                          liveStatus === "connecting" ||
                          liveStatus === "listening" ||
                          liveStatus === "speech_detected" ||
                          liveStatus === "finalizing_audio" ||
                          liveStatus === "processing" ||
                          liveStatus === "playing_response" ||
                          liveStatus === "ending"
                        }
                      >
                        Manual turn
                      </Button>
                      <Button
                        variant="outlined"
                        color="warning"
                        startIcon={<StopRoundedIcon />}
                        onClick={stopLiveMic}
                        disabled={liveStatus !== "listening" && liveStatus !== "speech_detected"}
                      >
                        Stop turn / Send
                      </Button>
                      <Button variant="text" startIcon={<LinkOffRoundedIcon />} onClick={() => closeLiveSession()} disabled={liveStatus === "idle" || liveStatus === "ended" || liveStatus === "ending"}>
                        End session
                      </Button>
                    </Stack>
                    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                        Connection status
                      </Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip size="small" color={liveStatusTone(liveStatus)} label={liveStatusLabel(liveStatus)} />
                        <Chip size="small" variant="outlined" label={`Session: ${liveSessionId ?? "Pending"}`} />
                        <Chip
                          size="small"
                          variant="outlined"
                          color={speechDetected ? "success" : silenceDetected ? "warning" : "default"}
                          label={
                            speechDetected
                              ? "Speech detected"
                              : silenceDetected
                                ? "Silence detected"
                                : liveStatus === "listening"
                                  ? "Listening"
                                  : liveStatus === "playing_response"
                                    ? "Playing response"
                                    : liveStatus === "processing"
                                      ? "Processing"
                                      : "Mic idle"
                          }
                        />
                      </Stack>
                    </Paper>
                    {language === "hi" ? (
                      <Alert severity={isHindiTtsVoiceConfigured() ? "info" : "warning"}>
                        {hindiTtsStatusMessage()}
                      </Alert>
                    ) : null}
                    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                        Voice activity
                      </Typography>
                      <LinearProgress variant="determinate" value={Math.min(100, micPeak * 100)} sx={{ height: 10, borderRadius: 999 }} />
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 0.75, display: "block" }}>
                        RMS {formatLevel(micLevel)} • Peak {formatLevel(micPeak)} • start {formatLevel(LIVE_SPEECH_START_THRESHOLD)} • end {formatLevel(LIVE_SPEECH_END_THRESHOLD)}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
                        min speech {LIVE_MIN_SPEECH_MS} ms • silence end {LIVE_SILENCE_TIMEOUT_MS} ms • max turn {LIVE_MAX_UTTERANCE_MS} ms
                      </Typography>
                    </Paper>
                    {liveTurnMetrics ? (
                      <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                          Latest turn metrics
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Capture {formatDuration(liveTurnMetrics.captureDurationMs)} • STT {formatDuration(liveTurnMetrics.sttDurationMs)} • LLM {formatDuration(liveTurnMetrics.llmDurationMs)} • TTS {formatDuration(liveTurnMetrics.ttsDurationMs)} • Total {formatDuration(liveTurnMetrics.totalDurationMs)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
                          Language {liveTurnMetrics.language || selectedLanguageLabel()} • STT {formatProvider(liveTurnMetrics.sttProvider)} • LLM {formatProvider(liveTurnMetrics.llmProvider)} • TTS {formatProvider(liveTurnMetrics.ttsProvider)} • Audio {formatSize(liveTurnMetrics.audioBytes)}
                        </Typography>
                        {liveTurnMetrics.fallbackReason ? (
                          <Typography variant="caption" color="warning.main" sx={{ mt: 0.5, display: "block" }}>
                            Fallback: {liveTurnMetrics.fallbackReason}
                          </Typography>
                        ) : null}
                      </Paper>
                    ) : null}
                    {liveCapturedAudioDebug ? (
                      <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                          Captured audio debug
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Recorder MIME: {liveCapturedAudioDebug.recorderMimeType || "unknown"}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Chunks: {liveCapturedAudioDebug.chunkCount} • Sizes: {liveCapturedAudioDebug.chunkSizes.map((size) => formatSize(size)).join(", ") || "None"}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Final blob: {liveCapturedAudioDebug.finalBlobType || "unknown"} • {formatSize(liveCapturedAudioDebug.finalBlobSize)}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Upload file: {liveCapturedAudioDebug.uploadFilename} • {liveCapturedAudioDebug.uploadMimeType || "unknown"}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Selected language: {selectedLanguageLabel()}
                        </Typography>
                      </Paper>
                    ) : null}
                  </Stack>
                </Paper>

                <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 0 }}>
                  <Stack spacing={2}>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>
                      Live result
                    </Typography>
                    <ResultBlock label="Transcript" value={liveTranscript} empty="Transcript events will appear after audio.end is sent." />
                    <ResultBlock label="Assistant response" value={liveAssistantText} empty="Assistant text will appear after the buffered audio is processed." />
                    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                        Conversation turns
                      </Typography>
                      {liveConversation.length === 0 ? (
                        <Typography variant="body2" color="text.secondary">
                          Completed turns will accumulate here for the active websocket session.
                        </Typography>
                      ) : (
                        <Stack spacing={1}>
                          {liveConversation.map((turn) => (
                            <Paper key={`${turn.turnIndex}-${turn.requestId || "turn"}`} variant="outlined" sx={{ p: 1.25 }}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                                Turn {turn.turnIndex}
                              </Typography>
                              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
                                User: {turn.transcript || "No transcript"}
                              </Typography>
                              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", mt: 0.5 }}>
                                Assistant: {turn.assistantText || "No reply"}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
                                Providers: STT {formatProvider(turn.providerTrace?.sttProvider)} • LLM {formatProvider(turn.providerTrace?.llmProvider)} • TTS {formatProvider(turn.providerTrace?.ttsProvider)}
                              </Typography>
                              {turn.workflowSummary?.mode === "appointment-booking" ? (
                                <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
                                  Workflow: {turn.workflowSummary.intentState || "Collecting"} • Missing {turn.workflowSummary.missingFields?.map((field) => formatWorkflowField(field)).join(", ") || "none"}
                                </Typography>
                              ) : null}
                            </Paper>
                          ))}
                        </Stack>
                      )}
                    </Paper>
                    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                        Playback
                      </Typography>
                      {livePlaybackError ? (
                        <Alert severity="warning" sx={{ mb: 1 }}>
                          {livePlaybackError}
                        </Alert>
                      ) : null}
                      {liveAudioDataUrl ? (
                        <Stack spacing={1}>
                          <audio ref={liveAudioElementRef} controls src={liveAudioDataUrl} style={{ width: "100%" }} onEnded={handleLiveResponseEnded} />
                          <Button variant="outlined" onClick={() => void playLiveResponse(false)}>
                            Play response
                          </Button>
                        </Stack>
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          No playable TTS audio was returned yet. Mock TTS fallback will still return assistant text.
                        </Typography>
                      )}
                    </Paper>
                    <ProviderTraceBlock trace={liveProviderTrace} />
                    <WorkflowSummaryBlock summary={liveWorkflowSummary} />
                  </Stack>
                </Paper>
              </Stack>

              <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="h6" sx={{ fontWeight: 800, mb: 1.5 }}>
                  Live event log
                </Typography>
                <Stack spacing={1}>
                  {liveEvents.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                      Start a live session to see websocket events, transcript updates, and provider responses.
                    </Typography>
                  ) : (
                    liveEvents.map((entry) => (
                      <Typography key={entry} variant="body2" sx={{ fontFamily: "monospace" }}>
                        {entry}
                      </Typography>
                    ))
                  )}
                </Stack>
              </Paper>
            </Stack>
          </Box>
        )}
      </Paper>
    </Stack>
  );
}
