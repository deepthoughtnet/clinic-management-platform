import { useEffect, useMemo, useRef, useState } from "react";
import {
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
import MicRoundedIcon from "@mui/icons-material/MicRounded";
import StopRoundedIcon from "@mui/icons-material/StopRounded";
import UploadFileRoundedIcon from "@mui/icons-material/UploadFileRounded";
import SendRoundedIcon from "@mui/icons-material/SendRounded";
import AutorenewRoundedIcon from "@mui/icons-material/AutorenewRounded";
import LinkRoundedIcon from "@mui/icons-material/LinkRounded";
import LinkOffRoundedIcon from "@mui/icons-material/LinkOffRounded";
import { getVoiceLiveStatus, getVoiceTestStatus, runVoiceTest, type VoiceLiveStatusResponse, type VoiceProviderTrace, type VoiceStatusResponse, type VoiceTestResponse } from "../../api/clinicApi";
import { ApiClientError } from "../../api/restClient";
import { useAuth } from "../../auth/useAuth";

const SUPPORTED_AUDIO_TYPES = ["audio/wav", "audio/webm", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/aac"];

type VoiceTab = "file" | "live";
type LiveStatus = "disconnected" | "connecting" | "connected" | "recording" | "processing";

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
  const [fileError, setFileError] = useState<string | null>(null);
  const [fileInfo, setFileInfo] = useState<string | null>(null);
  const [fileLoading, setFileLoading] = useState(false);
  const [fileRecording, setFileRecording] = useState(false);
  const [statusLoading, setStatusLoading] = useState(false);
  const [processingStage, setProcessingStage] = useState<string | null>(null);
  const [voiceStatus, setVoiceStatus] = useState<VoiceStatusResponse | null>(null);
  const [liveStatusInfo, setLiveStatusInfo] = useState<VoiceLiveStatusResponse | null>(null);

  const [liveStatus, setLiveStatus] = useState<LiveStatus>("disconnected");
  const [liveInfo, setLiveInfo] = useState<string | null>(null);
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

  const liveMediaRecorderRef = useRef<MediaRecorder | null>(null);
  const liveMediaStreamRef = useRef<MediaStream | null>(null);
  const liveSocketRef = useRef<WebSocket | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const fileAudioUrl = useMemo(() => {
    if (!fileResult?.audioBase64 || !fileResult.audioContentType) return null;
    return `data:${fileResult.audioContentType};base64,${fileResult.audioBase64}`;
  }, [fileResult]);

  useEffect(() => {
    return () => {
      stopFileStream();
      stopLiveStream();
      closeLiveSession(false);
    };
  }, []);

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
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        setFileError("Microphone recording is not available in this browser. Upload an audio file instead.");
        return;
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = MediaRecorder.isTypeSupported("audio/webm") ? "audio/webm" : "";
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      fileChunksRef.current = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) fileChunksRef.current.push(event.data);
      };
      recorder.onstop = () => {
        const type = recorder.mimeType || "audio/webm";
        const extension = type.includes("mpeg") ? "mp3" : type.includes("wav") ? "wav" : "webm";
        const blob = new Blob(fileChunksRef.current, { type });
        setSelectedFile(new File([blob], `voice-test-${Date.now()}.${extension}`, { type }));
        setFileInfo("Microphone recording captured. Review and send it to the backend voice harness.");
        setFileRecording(false);
        stopFileStream();
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
      extension.endsWith(".mp3") ||
      extension.endsWith(".m4a");
    if (!supported) {
      setFileError("Unsupported audio file. Use wav, webm, mp3, or m4a.");
      return;
    }
    setSelectedFile(file);
    setFileResult(null);
    setFileError(null);
    setFileInfo(`Loaded ${file.name}. Send it to run the STT to LLM to TTS loop.`);
  }

  async function submitFileTest() {
    if (!accessToken || !tenantId || !selectedFile) return;
    setFileLoading(true);
    setFileError(null);
    setFileInfo(null);
    setFileResult(null);
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

  function resetFileTest() {
    setSelectedFile(null);
    setFileResult(null);
    setFileError(null);
    setFileInfo(null);
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
    appendLiveEvent(`CONNECTING • ${websocketUrl}`);
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
      const mimeType = MediaRecorder.isTypeSupported("audio/webm") ? "audio/webm" : "";
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      recorder.ondataavailable = async (event) => {
        if (!event.data || event.data.size === 0) return;
        if (socket.readyState !== WebSocket.OPEN) return;
        const audioBase64 = await blobToBase64(event.data);
        socket.send(JSON.stringify({
          type: "audio.chunk",
          audioBase64,
          contentType: event.data.type || "audio/webm",
        }));
      };
      recorder.onstop = () => {
        stopLiveStream();
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: "audio.end" }));
        }
        setLiveStatus("processing");
        appendLiveEvent("Audio stream ended. Waiting for transcript and response.");
      };
      liveMediaStreamRef.current = stream;
      liveMediaRecorderRef.current = recorder;
      recorder.start(750);
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
    if (liveSocketRef.current?.readyState === WebSocket.OPEN) {
      liveSocketRef.current.send(JSON.stringify({ type: "audio.end" }));
    }
    setLiveStatus("processing");
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
                        accept=".wav,.webm,.mp3,.m4a,audio/*"
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
                    </Paper>

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
