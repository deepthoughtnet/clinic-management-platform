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
import { getVoiceLiveStatus, getVoiceTestStatus, runVoiceSttDebug, runVoiceTest, type VoiceDebugTraceEntry, type VoiceLiveStatusResponse, type VoiceProviderTrace, type VoiceStatusResponse, type VoiceSttDebugResponse, type VoiceTestResponse } from "../../api/clinicApi";
import { ApiClientError } from "../../api/restClient";
import { useAuth } from "../../auth/useAuth";

const SUPPORTED_AUDIO_TYPES = ["audio/wav", "audio/webm", "audio/ogg", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/aac"];
const LIVE_AUDIO_BASE64_CHUNK_SIZE = 32 * 1024;

type VoiceTab = "file" | "live";
type LiveStatus = "disconnected" | "connecting" | "connected" | "recording" | "processing";

type CapturedAudioDebug = {
  recorderMimeType: string;
  chunkCount: number;
  chunkSizes: number[];
  finalBlobSize: number;
  finalBlobType: string;
  uploadFilename: string;
  uploadMimeType: string;
};

function formatProvider(value: string | null | undefined) {
  if (!value) return "Not used";
  return value.replace(/[_-]+/g, " ").replace(/\b\w/g, (char) => char.toUpperCase());
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
    case "connected":
      return "success";
    case "recording":
      return "warning";
    case "processing":
      return "info";
    case "connecting":
      return "info";
    default:
      return "default";
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

function formatSize(sizeBytes: number | null | undefined) {
  if (typeof sizeBytes !== "number" || Number.isNaN(sizeBytes)) return "Unknown";
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  return `${Math.round(sizeBytes / 1024)} KB`;
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

  const [liveStatus, setLiveStatus] = useState<LiveStatus>("disconnected");
  const [liveInfo, setLiveInfo] = useState<string | null>(null);
  const [liveCaptureInfo, setLiveCaptureInfo] = useState<string | null>(null);
  const [liveError, setLiveError] = useState<string | null>(null);
  const [liveTranscript, setLiveTranscript] = useState("");
  const [liveAssistantText, setLiveAssistantText] = useState("");
  const [liveProviderTrace, setLiveProviderTrace] = useState<VoiceProviderTrace | null>(null);
  const [liveAudioDataUrl, setLiveAudioDataUrl] = useState<string | null>(null);
  const [liveEvents, setLiveEvents] = useState<string[]>([]);
  const [liveSessionId, setLiveSessionId] = useState<string | null>(null);

  const fileMediaRecorderRef = useRef<MediaRecorder | null>(null);
  const fileMediaStreamRef = useRef<MediaStream | null>(null);
  const fileChunksRef = useRef<Blob[]>([]);
  const fileRecordingStopPromiseRef = useRef<Promise<void> | null>(null);

  const liveMediaRecorderRef = useRef<MediaRecorder | null>(null);
  const liveMediaStreamRef = useRef<MediaStream | null>(null);
  const liveSocketRef = useRef<WebSocket | null>(null);
  const liveSendQueueRef = useRef<Promise<void>>(Promise.resolve());
  const liveChunkSequenceRef = useRef(0);
  const liveChunkFilenameRef = useRef<string | null>(null);
  const liveChunkContentTypeRef = useRef("audio/webm");
  const liveRecordedBytesRef = useRef(0);
  const liveAssistantAudioChunksRef = useRef<Map<number, string>>(new Map());
  const liveAssistantAudioExpectedChunksRef = useRef(0);

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
      stopFileStream();
      stopLiveStream();
      closeLiveSession(false);
    };
  }, [capturedAudioUrl]);

  useEffect(() => {
    if (!accessToken || !tenantId || !canUseVoiceTest) {
      return;
    }
    void refreshVoiceStatus(false);
    void refreshLiveStatus();
  }, [accessToken, tenantId, canUseVoiceTest]);

  function appendLiveEvent(message: string) {
    setLiveEvents((current) => [new Date().toLocaleTimeString() + " • " + message, ...current].slice(0, 20));
  }

  function stopFileStream() {
    fileMediaRecorderRef.current = null;
    if (fileMediaStreamRef.current) {
      fileMediaStreamRef.current.getTracks().forEach((track) => track.stop());
      fileMediaStreamRef.current = null;
    }
  }

  function stopLiveStream() {
    liveMediaRecorderRef.current = null;
    if (liveMediaStreamRef.current) {
      liveMediaStreamRef.current.getTracks().forEach((track) => track.stop());
      liveMediaStreamRef.current = null;
    }
  }

  async function startFileRecording() {
    setFileError(null);
    setFileInfo(null);
    setCapturedAudioDebug(null);
    if (capturedAudioUrl) {
      URL.revokeObjectURL(capturedAudioUrl);
      setCapturedAudioUrl(null);
    }
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        setFileError("Microphone recording is not available in this browser. Upload an audio file instead.");
        return;
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = selectRecordingMimeType();
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      fileChunksRef.current = [];
      fileRecordingStopPromiseRef.current = new Promise((resolve) => {
        recorder.onstop = () => {
          const type = recorder.mimeType || fileChunksRef.current[0]?.type || "audio/webm";
          const extension = resolveAudioExtensionFromType(type);
          const blob = new Blob(fileChunksRef.current, { type });
          const chunkSizes = fileChunksRef.current.map((chunk) => chunk.size);
          console.info("[voice-test] browser recording captured", {
            mimeType: recorder.mimeType,
            blobType: blob.type,
            extension,
            sizeBytes: blob.size,
            chunkCount: fileChunksRef.current.length,
            chunkSizes,
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
            recorderMimeType: recorder.mimeType || type,
            chunkCount: fileChunksRef.current.length,
            chunkSizes,
            finalBlobSize: blob.size,
            finalBlobType: blob.type || type,
            uploadFilename: recordedFile.name,
            uploadMimeType: type,
          });
          setFileCaptureInfo(`Captured ${recorder.mimeType || blob.type || type} (${formatSize(blob.size)}) • Uploading original ${type || "unknown"} as ${recordedFile.name} (${formatSize(recordedFile.size)}).`);
          setSelectedFile(recordedFile);
          setFileInfo("Microphone recording captured. Review, download if needed, and send it to the backend voice harness.");
          setFileRecording(false);
          stopFileStream();
          resolve();
        };
      });
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) fileChunksRef.current.push(event.data);
      };
      fileMediaStreamRef.current = stream;
      fileMediaRecorderRef.current = recorder;
      recorder.start(1000);
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
      const response = await runVoiceTest(accessToken, tenantId, { audio: selectedFile, context, language });
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
      setLiveStatus("connected");
      appendLiveEvent("Live voice session connected.");
      socket.send(JSON.stringify({
        type: "session.start",
        language,
        context: context.trim() || "clinic receptionist test",
      }));
    };
    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data as string) as Record<string, unknown>;
        const type = String(payload.type || "");
        if (type === "session.started") {
          const sessionId = String(payload.sessionId || "");
          setLiveSessionId(sessionId || null);
          appendLiveEvent("Session started.");
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
          appendLiveEvent(`Chunk acknowledged ${sequence}/${totalChunks}.`);
          return;
        }
        if (type === "audio.buffer.complete") {
          const totalChunks = Number(payload.totalChunks || 0);
          const sizeBytes = Number(payload.sizeBytes || 0);
          appendLiveEvent(`Audio buffered ${totalChunks} chunks • ${formatSize(sizeBytes)}.`);
          return;
        }
        if (type === "transcript.final") {
          setLiveTranscript(String(payload.text || ""));
          setLiveStatus("connected");
          appendLiveEvent("Transcript finalized.");
          return;
        }
        if (type === "assistant.text") {
          setLiveAssistantText(String(payload.text || ""));
          setLiveProviderTrace((payload.providerTrace as VoiceProviderTrace | null) ?? null);
          appendLiveEvent("Assistant text received.");
          return;
        }
        if (type === "assistant.audio.chunk") {
          const sequence = Number(payload.sequence || 0);
          const totalChunks = Number(payload.totalChunks || 0);
          const audioBase64Chunk = String(payload.audioBase64Chunk || "");
          if (sequence > 0 && audioBase64Chunk) {
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
          liveAssistantAudioChunksRef.current.clear();
          liveAssistantAudioExpectedChunksRef.current = 0;
          setLiveProviderTrace((payload.providerTrace as VoiceProviderTrace | null) ?? null);
          appendLiveEvent(`Assistant audio reconstructed from ${totalChunks} chunk${totalChunks === 1 ? "" : "s"}.`);
          return;
        }
        if (type === "assistant.audio") {
          const audioBase64 = String(payload.audioBase64 || "");
          const contentType = String(payload.contentType || "audio/mpeg");
          if (audioBase64) {
            setLiveAudioDataUrl(`data:${contentType};base64,${audioBase64}`);
          }
          setLiveProviderTrace((payload.providerTrace as VoiceProviderTrace | null) ?? null);
          appendLiveEvent("Assistant audio received.");
          return;
        }
        if (type === "session.closed") {
          setLiveStatus("disconnected");
          appendLiveEvent("Session closed.");
          return;
        }
        if (type === "error") {
          setLiveStatus("connected");
          setLiveError(String(payload.message || "Live voice test failed."));
          appendLiveEvent("Error received from websocket server.");
          return;
        }
      } catch {
        setLiveError("Live websocket returned an unreadable message.");
      }
    };
    socket.onerror = () => {
      setLiveStatus("disconnected");
      const details = liveStatusInfo
        ? `Expected path ${liveStatusInfo.websocketPath} with ${liveStatusInfo.authMode} and ${liveStatusInfo.tenantMode}.`
        : "Check tenant selection and authentication.";
      setLiveError(`WebSocket connection failed. ${details}`);
      appendLiveEvent("CONNECT_FAILED");
    };
    socket.onclose = (event) => {
      stopLiveStream();
      liveSocketRef.current = null;
      setLiveStatus("disconnected");
      liveAssistantAudioChunksRef.current.clear();
      liveAssistantAudioExpectedChunksRef.current = 0;
      if (event.code === 1009) {
        setLiveError("WebSocket message too large. Audio/response chunking failed.");
        appendLiveEvent(`DISCONNECTED • code=${event.code} reason=message too large`);
        return;
      }
      if (event.reason) {
        setLiveError(`WebSocket closed: ${event.reason}`);
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
    setLiveStatus("connecting");
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
    setLiveProviderTrace(null);
    setLiveEvents([]);
    setLiveCaptureInfo(null);
    liveChunkSequenceRef.current = 0;
    liveChunkFilenameRef.current = null;
    liveChunkContentTypeRef.current = "audio/webm";
    liveRecordedBytesRef.current = 0;
    liveSendQueueRef.current = Promise.resolve();
    liveAssistantAudioChunksRef.current.clear();
    liveAssistantAudioExpectedChunksRef.current = 0;
    ensureLiveSocket();
  }

  async function startLiveMic() {
    setLiveError(null);
    setLiveInfo(null);
    try {
      const socket = ensureLiveSocket();
      if (!navigator.mediaDevices?.getUserMedia) {
        setLiveError("Microphone recording is not available in this browser.");
        return;
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = selectRecordingMimeType();
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      const recordingType = recorder.mimeType || mimeType || "audio/webm";
      const filename = `voice-live-${Date.now()}.${resolveAudioExtensionFromType(recordingType)}`;
      liveChunkSequenceRef.current = 0;
      liveChunkFilenameRef.current = filename;
      liveChunkContentTypeRef.current = recordingType;
      liveRecordedBytesRef.current = 0;
      liveSendQueueRef.current = Promise.resolve();
      setLiveCaptureInfo(`Captured ${recordingType} • Uploading original ${recordingType} as ${filename}.`);
      recorder.ondataavailable = (event) => {
        if (!event.data || event.data.size === 0) return;
        if (socket.readyState !== WebSocket.OPEN) return;
        liveRecordedBytesRef.current += event.data.size;
        const capturedType = event.data.type || recordingType || "audio/webm";
        liveChunkContentTypeRef.current = capturedType;
        setLiveCaptureInfo(`Captured ${capturedType} • Uploading original ${capturedType} as ${filename} (${formatSize(liveRecordedBytesRef.current)}).`);
        liveSendQueueRef.current = liveSendQueueRef.current.then(async () => {
          const audioBase64 = await blobToBase64(event.data);
          const base64Chunks = splitBase64Chunks(audioBase64);
          base64Chunks.forEach((audioBase64Chunk) => {
            liveChunkSequenceRef.current += 1;
            socket.send(JSON.stringify({
              type: "audio.chunk",
              sequence: liveChunkSequenceRef.current,
              contentType: capturedType,
              filename,
              audioBase64Chunk,
            }));
          });
          appendLiveEvent(`Uploaded ${base64Chunks.length} audio chunk${base64Chunks.length === 1 ? "" : "s"} from recorder slice • ${formatSize(event.data.size)}.`);
        }).catch(() => {
          setLiveError("Live microphone audio could not be prepared for upload.");
        });
      };
      recorder.onstop = () => {
        console.info("[voice-live] browser recording captured", {
          mimeType: recorder.mimeType,
          blobType: liveChunkContentTypeRef.current,
          filename,
          sizeBytes: liveRecordedBytesRef.current,
        });
        stopLiveStream();
        setLiveStatus("processing");
        void liveSendQueueRef.current.then(() => {
          if (socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({
              type: "audio.end",
              filename,
              contentType: liveChunkContentTypeRef.current || recordingType || "audio/webm",
              totalChunks: liveChunkSequenceRef.current,
            }));
          }
          appendLiveEvent(`Audio stream ended. Uploaded ${liveChunkSequenceRef.current} websocket chunk${liveChunkSequenceRef.current === 1 ? "" : "s"} • waiting for transcript and response.`);
        }).catch(() => {
          setLiveStatus("connected");
        });
      };
      liveMediaStreamRef.current = stream;
      liveMediaRecorderRef.current = recorder;
      recorder.start(500);
      setLiveStatus("recording");
      appendLiveEvent("Microphone streaming started.");
    } catch {
      stopLiveStream();
      setLiveStatus("disconnected");
      setLiveError("Microphone access was denied. You can still use the file-based voice test.");
    }
  }

  function stopLiveMic() {
    if (liveMediaRecorderRef.current && liveMediaRecorderRef.current.state !== "inactive") {
      liveMediaRecorderRef.current.stop();
      return;
    }
    stopLiveStream();
    liveSendQueueRef.current = Promise.resolve();
    setLiveStatus("connected");
  }

  function closeLiveSession(resetState = true) {
    stopLiveStream();
    if (liveSocketRef.current && liveSocketRef.current.readyState === WebSocket.OPEN) {
      liveSocketRef.current.send(JSON.stringify({ type: "session.close" }));
      liveSocketRef.current.close();
    }
    liveSocketRef.current = null;
    setLiveStatus("disconnected");
    if (resetState) {
      setLiveInfo(null);
      setLiveCaptureInfo(null);
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
          <Chip size="small" variant="outlined" label={`STT: ${formatProvider(voiceStatus?.providerTrace?.sttProvider || "faster-whisper")} / Mock`} />
          <Chip size="small" variant="outlined" label={`LLM: ${formatProvider(voiceStatus?.providerTrace?.llmProvider || "gemini")} / Groq / Mock`} />
          <Chip size="small" variant="outlined" label={`TTS: ${formatProvider(voiceStatus?.providerTrace?.ttsProvider || "piper")} / Mock`} />
          <Chip size="small" variant="outlined" label={`Live: ${liveStatus}`} color={liveStatusTone(liveStatus)} />
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
                      size="small"
                      label="Optional context"
                      multiline
                      minRows={3}
                      value={context}
                      onChange={(event) => setContext(event.target.value)}
                      placeholder="Add short context for the assistant, such as booking intent, clinic FAQ, or follow-up scenario."
                    />

                    <TextField
                      size="small"
                      label="Language"
                      value={language}
                      onChange={(event) => setLanguage(event.target.value)}
                      helperText="Simple language hint passed to STT/TTS where supported."
                      sx={{ maxWidth: 220 }}
                    />

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
                  Live websocket path: <strong>{liveStatusInfo.websocketPath}</strong> • auth: <strong>{liveStatusInfo.authMode}</strong> • tenant: <strong>{liveStatusInfo.tenantMode}</strong>
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
                      size="small"
                      label="Language"
                      value={language}
                      onChange={(event) => setLanguage(event.target.value)}
                      sx={{ maxWidth: 220 }}
                    />
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                      <Button variant="contained" startIcon={<LinkRoundedIcon />} onClick={startLiveSession} disabled={liveStatus === "connecting" || liveStatus === "connected" || liveStatus === "recording" || liveStatus === "processing"}>
                        Start session
                      </Button>
                      <Button variant="contained" color="secondary" startIcon={<MicRoundedIcon />} onClick={startLiveMic} disabled={liveStatus === "recording" || liveStatus === "processing"}>
                        Start mic
                      </Button>
                      <Button variant="outlined" color="warning" startIcon={<StopRoundedIcon />} onClick={stopLiveMic} disabled={liveStatus !== "recording"}>
                        Stop mic
                      </Button>
                      <Button variant="text" startIcon={<LinkOffRoundedIcon />} onClick={() => closeLiveSession()} disabled={liveStatus === "disconnected"}>
                        Close session
                      </Button>
                    </Stack>
                    <Paper variant="outlined" sx={{ p: 1.5, bgcolor: "background.default" }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                        Connection status
                      </Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip size="small" color={liveStatusTone(liveStatus)} label={liveStatus} />
                        <Chip size="small" variant="outlined" label={`Session: ${liveSessionId ?? "Pending"}`} />
                      </Stack>
                    </Paper>
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
                        Playback
                      </Typography>
                      {liveAudioDataUrl ? (
                        <audio controls src={liveAudioDataUrl} style={{ width: "100%" }} />
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          No playable TTS audio was returned yet. Mock TTS fallback will still return assistant text.
                        </Typography>
                      )}
                    </Paper>
                    <ProviderTraceBlock trace={liveProviderTrace} />
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
