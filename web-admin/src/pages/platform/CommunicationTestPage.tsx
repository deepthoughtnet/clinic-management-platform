import { useEffect, useMemo, useState } from "react";
import { Alert, Box, Button, Card, CardContent, Chip, Divider, MenuItem, Stack, Tab, Tabs, TextField, Typography } from "@mui/material";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import SendRoundedIcon from "@mui/icons-material/SendRounded";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { getCommunicationTestHealth, getCommunicationTestInboundVoice, getCommunicationTestVoiceResult, runCommunicationTest, type CommunicationTestChannel, type CommunicationTestHealth, type CommunicationTestInboundVoiceResult, type CommunicationTestMode, type CommunicationTestResult } from "../../api/clinicApi";

const channels: CommunicationTestChannel[] = ["EMAIL", "VOICE", "WHATSAPP"];
const scenarios = ["YES_NO_CONFIRMATION", "APPOINTMENT_REMINDER", "FOLLOW_UP_CONSULTATION", "MEDICATION_REFILL_REVIEW", "LAB_REMINDER", "LAB_REPORT_READY"] as const;

function interactivePrompt(language: string) {
  return language === "hi-IN" || language === "hinglish"
    ? "Namaste. Yeh Jeevanam Healthcare se ek test call hai. Kripya confirm karne ke liye haan kahiye, aur mana karne ke liye nahi kahiye."
    : "This is a Jeevanam Healthcare test call. Please say yes to confirm or no to decline.";
}

function tone(status: string) {
  return status === "READY" ? "success" : status === "PARTIAL" ? "warning" : status === "ERROR" ? "error" : "default";
}

export default function CommunicationTestPage() {
  const auth = useAuth();
  const [channel, setChannel] = useState<CommunicationTestChannel>("EMAIL");
  const [provider, setProvider] = useState("");
  const [health, setHealth] = useState<CommunicationTestHealth[]>([]);
  const [capabilities, setCapabilities] = useState<{ capability: string; provider: string | null; status: string; message: string }[]>([]);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [loadingHealth, setLoadingHealth] = useState(false);
  const [emailRecipient, setEmailRecipient] = useState("");
  const [phoneRecipient, setPhoneRecipient] = useState("");
  const [subject, setSubject] = useState("Jeevanam Email Test");
  const [message, setMessage] = useState("This is a test email from Jeevanam Healthcare.");
  const [language, setLanguage] = useState("en-IN");
  const [mode, setMode] = useState<CommunicationTestMode>("PROVIDER_ONLY");
  const [scenario, setScenario] = useState<typeof scenarios[number]>("APPOINTMENT_REMINDER");
  const [result, setResult] = useState<CommunicationTestResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const showInboundVoice = searchParams.get("tab") === "inbound-voice";
  const [inboundVoice, setInboundVoice] = useState<CommunicationTestInboundVoiceResult | null>(null);

  const channelHealth = useMemo(() => health.filter((row) => row.channel === channel), [health, channel]);
  const selectedHealth = useMemo(() => channelHealth.find((row) => row.provider === provider) ?? channelHealth[0], [channelHealth, provider]);
  const recipient = channel === "EMAIL" ? emailRecipient : phoneRecipient;
  const setRecipient = channel === "EMAIL" ? setEmailRecipient : setPhoneRecipient;
  const validRecipient = channel === "EMAIL" ? /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(recipient.trim()) : /^\+?[0-9][0-9()\-\s]{6,19}$/.test(recipient.trim());

  useEffect(() => {
    if (channel === "VOICE" && mode === "INTERACTIVE_RESPONSE") setScenario("YES_NO_CONFIRMATION");
    if (channel === "VOICE" && mode === "INTERACTIVE_RESPONSE") setMessage(interactivePrompt(language));
    else if (channel === "VOICE") setMessage("This is a test reminder from Jeevanam Healthcare.");
    if (channel === "EMAIL") setMessage("This is a test email from Jeevanam Healthcare.");
  }, [channel, mode, language]);

  const loadHealth = async () => {
    if (!auth.accessToken) return;
    setLoadingHealth(true); setHealthError(null);
    try { const response = await getCommunicationTestHealth(auth.accessToken); setHealth(response.channels); setCapabilities(response.capabilities ?? []); }
    catch (reason) { setHealthError(reason instanceof Error ? reason.message : "Could not load provider health"); }
    finally { setLoadingHealth(false); }
  };
  useEffect(() => { void loadHealth(); }, [auth.accessToken]);
  useEffect(() => { if (channelHealth.length > 0 && !channelHealth.some((row) => row.provider === provider)) setProvider(channelHealth[0].provider); }, [channelHealth, provider]);
  useEffect(() => {
    if (channel === "VOICE" && mode === "INTERACTIVE_RESPONSE" && channelHealth.some((row) => row.provider.toLowerCase() === "dotvoice")) {
      setProvider("dotvoice");
    }
  }, [channel, mode, channelHealth]);

  useEffect(() => {
    if (!auth.accessToken || channel !== "VOICE" || mode !== "INTERACTIVE_RESPONSE" || !result?.testExecutionId) return;
    const terminal = new Set(["RESPONSE_CAPTURED", "ENDED", "FAILED", "NO_ANSWER", "BUSY"]);
    if (terminal.has(result.status)) return;
    const timer = window.setInterval(() => {
      void getCommunicationTestVoiceResult(auth.accessToken!, result.testExecutionId!).then(setResult).catch(() => undefined);
    }, 2000);
    return () => window.clearInterval(timer);
  }, [auth.accessToken, channel, mode, result?.testExecutionId, result?.status]);

  useEffect(() => {
    if (!auth.accessToken || !showInboundVoice) return;
    void getCommunicationTestInboundVoice(auth.accessToken).then(setInboundVoice).catch(() => undefined);
    const timer = window.setInterval(() => void getCommunicationTestInboundVoice(auth.accessToken!).then(setInboundVoice).catch(() => undefined), 2000);
    return () => window.clearInterval(timer);
  }, [auth.accessToken, showInboundVoice]);

  const send = async () => {
    if (!auth.accessToken || !validRecipient || !message.trim() || selectedHealth?.status === "NOT_CONFIGURED" || selectedHealth?.status === "ERROR") return;
    setSending(true); setError(null); setResult(null);
    try {
      setResult(await runCommunicationTest(auth.accessToken, channel, { recipient, subject: channel === "EMAIL" ? subject : undefined, message, language, mode, scenario, provider }));
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Provider test failed"); }
    finally { setSending(false); }
  };

  return <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1400, mx: "auto" }}>
    <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={2} sx={{ mb: 3 }}>
      <Box><Typography variant="h4" sx={{ fontWeight: 800 }}>Communication Test</Typography><Typography color="text.secondary">Certify configured providers without running production reminder workflows.</Typography></Box>
      <Button startIcon={<RefreshRoundedIcon />} onClick={() => void loadHealth()} disabled={loadingHealth}>Refresh health</Button>
    </Stack>
    {healthError && <Alert severity="error" sx={{ mb: 2 }}>{healthError}</Alert>}
    <Card sx={{ mb: 3 }}><Tabs value={showInboundVoice ? "INBOUND_VOICE" : channel} onChange={(_, value) => { if (value === "INBOUND_VOICE") { setSearchParams({ tab: "inbound-voice" }); } else { setSearchParams({}); setChannel(value as CommunicationTestChannel); setResult(null); } }} aria-label="communication channel"><Tab value="EMAIL" label="Email" /><Tab value="VOICE" label="Voice" /><Tab value="WHATSAPP" label="WhatsApp" /><Tab value="INBOUND_VOICE" label="Inbound voice" /></Tabs></Card>
    <Stack direction={{ xs: "column", lg: "row" }} spacing={3} alignItems="stretch" sx={{ display: showInboundVoice ? "none" : "flex" }}>
      <Card sx={{ flex: 1 }}><CardContent><Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Provider test</Typography>
        <Stack spacing={2}>
          {channelHealth.length > 0 && <TextField select label="Provider" value={provider} onChange={(event) => setProvider(event.target.value)} disabled={channel === "VOICE" && mode === "INTERACTIVE_RESPONSE"}>{channelHealth.map((row) => <MenuItem key={row.provider} value={row.provider}>{row.provider}</MenuItem>)}</TextField>}
          <TextField label={channel === "EMAIL" ? "Recipient email" : "Destination phone"} value={recipient} onChange={(event) => setRecipient(event.target.value)} placeholder={channel === "EMAIL" ? "you@example.com" : "+919876543210"} required />
          {channel === "EMAIL" && <TextField label="Subject" value={subject} onChange={(event) => setSubject(event.target.value)} required />}
          <TextField select label="Test mode" value={mode} onChange={(event) => setMode(event.target.value as CommunicationTestMode)}><MenuItem value="PROVIDER_ONLY">Provider only</MenuItem><MenuItem value="REMINDER_SIMULATION">Reminder simulation</MenuItem><MenuItem value="INTERACTIVE_RESPONSE">Interactive response</MenuItem></TextField>
          <TextField select label="Language" value={language} onChange={(event) => setLanguage(event.target.value)}><MenuItem value="en-IN">English</MenuItem><MenuItem value="hi-IN">Hindi</MenuItem><MenuItem value="hinglish">Hinglish</MenuItem></TextField>
          <TextField select label="Scenario" value={scenario} onChange={(event) => setScenario(event.target.value as typeof scenarios[number])}>{scenarios.map((item) => <MenuItem key={item} value={item}>{item.replaceAll("_", " ")}</MenuItem>)}</TextField>
          <TextField label="Message" value={message} onChange={(event) => setMessage(event.target.value)} multiline minRows={5} required />
          <Typography variant="caption" color="text.secondary">{channel === "VOICE" ? (mode === "REMINDER_SIMULATION" ? "Reminder playback uses deterministic TTS and DotVoice media streaming. " : mode === "INTERACTIVE_RESPONSE" ? "Interactive response captures caller speech through Sarvam STT. " : "PSTN call-connectivity test only. ") : ""}Provider-only test. No appointment, prescription, lab, pharmacy, campaign, or reminder state is changed.</Typography>
          <Button variant="contained" startIcon={<SendRoundedIcon />} onClick={() => void send()} disabled={sending || !validRecipient || !message.trim() || !selectedHealth || selectedHealth.status === "NOT_CONFIGURED" || selectedHealth.status === "ERROR"}>{sending ? "Sending…" : "Send test"}</Button>
        </Stack>
      </CardContent></Card>
      <Stack sx={{ flex: 1 }} spacing={3}>
        {channel === "VOICE" && <Card><CardContent><Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Reminder playback readiness</Typography><Stack spacing={1}>{capabilities.map((item) => <Stack key={item.capability} direction="row" justifyContent="space-between" spacing={2}><Typography variant="body2">{item.capability === "REMINDER_TTS" ? "TTS" : item.capability === "DOTVOICE_STREAM" ? "DotVoice Stream" : item.capability}</Typography><Stack direction="row" spacing={1} alignItems="center"><Chip size="small" color={tone(item.status) as any} label={item.status} /><Typography variant="caption" color="text.secondary">{item.provider ?? item.message}</Typography></Stack></Stack>)}</Stack></CardContent></Card>}
        <Card><CardContent><Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Provider health</Typography>{selectedHealth ? <Stack spacing={1}><Stack direction="row" justifyContent="space-between"><Typography>Provider</Typography><Typography fontWeight={700}>{selectedHealth.provider}</Typography></Stack><Stack direction="row" justifyContent="space-between"><Typography>Status</Typography><Chip size="small" color={tone(selectedHealth.status) as any} label={selectedHealth.status} /></Stack><Typography variant="body2" color="text.secondary">{selectedHealth.message}</Typography>{selectedHealth.missingConfigurationKeys.length > 0 && <Typography variant="caption" color="text.secondary">Missing: {selectedHealth.missingConfigurationKeys.join(", ")}</Typography>}</Stack> : <Typography color="text.secondary">Loading provider health…</Typography>}</CardContent></Card>
        <Card><CardContent><Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Execution result</Typography>{error && <Alert severity="error">{error}</Alert>}{!error && !result && <Typography color="text.secondary">No test sent yet.</Typography>}{result && <Stack spacing={1}><Chip color={result.success ? "success" : "error"} label={result.status} sx={{ alignSelf: "flex-start" }} /><Typography variant="body2">Provider: {result.provider}</Typography><Typography variant="body2">Request: {result.requestId}</Typography>{result.providerRequestId && <Typography variant="body2">Provider ID: {result.providerRequestId}</Typography>}{result.testExecutionId && <Divider sx={{ my: 1 }} />}{result.testExecutionId && <><Typography variant="subtitle2">Speech recognition</Typography><Typography variant="body2">STT provider: {result.sttProvider ?? "Waiting"}</Typography><Typography variant="body2">Transcript: {result.rawTranscript == null ? "Waiting for caller speech…" : result.rawTranscript || "(empty transcript)"}</Typography><Typography variant="body2">Language: {result.detectedLanguage ?? language}</Typography><Typography variant="body2">Confidence: {result.sttConfidence ?? "Not provided"}</Typography><Typography variant="subtitle2">Response</Typography><Typography variant="body2">Normalized intent: {result.normalizedIntent ?? "Waiting"}</Typography><Typography variant="body2">Attempt: {result.attemptNumber ?? 1}</Typography><Typography variant="body2">Would execute: {result.wouldExecuteAction ?? "NONE"}</Typography></>}{result.failureMessage && <Alert severity="warning">{result.failureMessage}</Alert>}</Stack>}</CardContent></Card>
    </Stack>
    </Stack>
    {showInboundVoice && <Stack spacing={3}><Card><CardContent><Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Inbound Voice Test</Typography><Stack spacing={1}><Typography variant="body2">Listener: <b>{inboundVoice?.websocketStatus ?? "DISCONNECTED"}</b></Typography><Typography variant="body2">Caller phone: {inboundVoice?.callerPhoneNumber ?? "Waiting for incoming call…"}</Typography><Typography variant="body2">Jeevanam DID: {inboundVoice?.didNumber ?? "—"}</Typography><Typography variant="body2">Call ID: {inboundVoice?.callId ?? "—"}</Typography><Typography variant="body2">Direction: {inboundVoice?.direction ?? "INBOUND"}</Typography><Typography variant="body2">Status: <b>{inboundVoice?.status ?? "WAITING_FOR_INCOMING_CALL"}</b></Typography><Typography variant="body2">Language: {inboundVoice?.language ?? "hi-IN"}</Typography><Typography variant="body2">STT Provider: {inboundVoice?.sttProvider ?? "Sarvam"}</Typography><Divider sx={{ my: 1 }} /><Typography variant="subtitle2">First Conversation</Typography><Typography variant="body2">First transcript: {inboundVoice?.firstTranscript == null ? "Waiting for caller speech…" : inboundVoice.firstTranscript || "(empty transcript)"}</Typography><Typography variant="caption" color="text.secondary">Received: {inboundVoice?.receivedAt ?? "—"} · Answered: {inboundVoice?.answeredAt ?? "—"} · Captured: {inboundVoice?.capturedAt ?? "—"} · Ended: {inboundVoice?.endedAt ?? "—"}</Typography></Stack></CardContent></Card></Stack>}
  </Box>;
}
