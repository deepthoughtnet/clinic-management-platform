import { useEffect, useMemo, useState } from "react";
import { Alert, Box, Button, Card, CardContent, Chip, Divider, MenuItem, Stack, Tab, Tabs, TextField, Typography } from "@mui/material";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import SendRoundedIcon from "@mui/icons-material/SendRounded";
import { useAuth } from "../../auth/useAuth";
import { getCommunicationTestHealth, runCommunicationTest, type CommunicationTestChannel, type CommunicationTestHealth, type CommunicationTestMode, type CommunicationTestResult } from "../../api/clinicApi";

const channels: CommunicationTestChannel[] = ["EMAIL", "VOICE", "WHATSAPP"];
const scenarios = ["APPOINTMENT_REMINDER", "FOLLOW_UP_CONSULTATION", "MEDICATION_REFILL_REVIEW", "LAB_REMINDER", "LAB_REPORT_READY"] as const;

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

  const channelHealth = useMemo(() => health.filter((row) => row.channel === channel), [health, channel]);
  const selectedHealth = useMemo(() => channelHealth.find((row) => row.provider === provider) ?? channelHealth[0], [channelHealth, provider]);
  const recipient = channel === "EMAIL" ? emailRecipient : phoneRecipient;
  const setRecipient = channel === "EMAIL" ? setEmailRecipient : setPhoneRecipient;
  const validRecipient = channel === "EMAIL" ? /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(recipient.trim()) : /^\+?[0-9][0-9()\-\s]{6,19}$/.test(recipient.trim());

  useEffect(() => {
    if (channel === "VOICE") setMessage("This is a test reminder from Jeevanam Healthcare.");
    if (channel === "EMAIL") setMessage("This is a test email from Jeevanam Healthcare.");
  }, [channel]);

  const loadHealth = async () => {
    if (!auth.accessToken) return;
    setLoadingHealth(true); setHealthError(null);
    try { const response = await getCommunicationTestHealth(auth.accessToken); setHealth(response.channels); setCapabilities(response.capabilities ?? []); }
    catch (reason) { setHealthError(reason instanceof Error ? reason.message : "Could not load provider health"); }
    finally { setLoadingHealth(false); }
  };
  useEffect(() => { void loadHealth(); }, [auth.accessToken]);
  useEffect(() => { if (channelHealth.length > 0 && !channelHealth.some((row) => row.provider === provider)) setProvider(channelHealth[0].provider); }, [channelHealth, provider]);

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
    <Card sx={{ mb: 3 }}><Tabs value={channel} onChange={(_, value) => { setChannel(value); setResult(null); }} aria-label="communication channel"><Tab value="EMAIL" label="Email" /><Tab value="VOICE" label="Voice" /><Tab value="WHATSAPP" label="WhatsApp" /></Tabs></Card>
    <Stack direction={{ xs: "column", lg: "row" }} spacing={3} alignItems="stretch">
      <Card sx={{ flex: 1 }}><CardContent><Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Provider test</Typography>
        <Stack spacing={2}>
          {channelHealth.length > 0 && <TextField select label="Provider" value={provider} onChange={(event) => setProvider(event.target.value)}>{channelHealth.map((row) => <MenuItem key={row.provider} value={row.provider}>{row.provider}</MenuItem>)}</TextField>}
          <TextField label={channel === "EMAIL" ? "Recipient email" : "Destination phone"} value={recipient} onChange={(event) => setRecipient(event.target.value)} placeholder={channel === "EMAIL" ? "you@example.com" : "+919876543210"} required />
          {channel === "EMAIL" && <TextField label="Subject" value={subject} onChange={(event) => setSubject(event.target.value)} required />}
          <TextField select label="Test mode" value={mode} onChange={(event) => setMode(event.target.value as CommunicationTestMode)}><MenuItem value="PROVIDER_ONLY">Provider only</MenuItem><MenuItem value="REMINDER_SIMULATION">Reminder simulation</MenuItem></TextField>
          <TextField select label="Language" value={language} onChange={(event) => setLanguage(event.target.value)}><MenuItem value="en-IN">English</MenuItem><MenuItem value="hi-IN">Hindi</MenuItem><MenuItem value="hinglish">Hinglish</MenuItem></TextField>
          <TextField select label="Scenario" value={scenario} onChange={(event) => setScenario(event.target.value as typeof scenarios[number])}>{scenarios.map((item) => <MenuItem key={item} value={item}>{item.replaceAll("_", " ")}</MenuItem>)}</TextField>
          <TextField label="Message" value={message} onChange={(event) => setMessage(event.target.value)} multiline minRows={5} required />
          <Typography variant="caption" color="text.secondary">{channel === "VOICE" ? (mode === "REMINDER_SIMULATION" ? "Reminder playback uses deterministic TTS and DotVoice media streaming. " : "PSTN call-connectivity test only. ") : ""}Provider-only test. No appointment, prescription, lab, pharmacy, campaign, or reminder state is changed.</Typography>
          <Button variant="contained" startIcon={<SendRoundedIcon />} onClick={() => void send()} disabled={sending || !validRecipient || !message.trim() || !selectedHealth || selectedHealth.status === "NOT_CONFIGURED" || selectedHealth.status === "ERROR"}>{sending ? "Sending…" : "Send test"}</Button>
        </Stack>
      </CardContent></Card>
      <Stack sx={{ flex: 1 }} spacing={3}>
        {channel === "VOICE" && <Card><CardContent><Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Reminder playback readiness</Typography><Stack spacing={1}>{capabilities.map((item) => <Stack key={item.capability} direction="row" justifyContent="space-between" spacing={2}><Typography variant="body2">{item.capability === "REMINDER_TTS" ? "TTS" : item.capability === "DOTVOICE_STREAM" ? "DotVoice Stream" : item.capability}</Typography><Stack direction="row" spacing={1} alignItems="center"><Chip size="small" color={tone(item.status) as any} label={item.status} /><Typography variant="caption" color="text.secondary">{item.provider ?? item.message}</Typography></Stack></Stack>)}</Stack></CardContent></Card>}
        <Card><CardContent><Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Provider health</Typography>{selectedHealth ? <Stack spacing={1}><Stack direction="row" justifyContent="space-between"><Typography>Provider</Typography><Typography fontWeight={700}>{selectedHealth.provider}</Typography></Stack><Stack direction="row" justifyContent="space-between"><Typography>Status</Typography><Chip size="small" color={tone(selectedHealth.status) as any} label={selectedHealth.status} /></Stack><Typography variant="body2" color="text.secondary">{selectedHealth.message}</Typography>{selectedHealth.missingConfigurationKeys.length > 0 && <Typography variant="caption" color="text.secondary">Missing: {selectedHealth.missingConfigurationKeys.join(", ")}</Typography>}</Stack> : <Typography color="text.secondary">Loading provider health…</Typography>}</CardContent></Card>
        <Card><CardContent><Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Execution result</Typography>{error && <Alert severity="error">{error}</Alert>}{!error && !result && <Typography color="text.secondary">No test sent yet.</Typography>}{result && <Stack spacing={1}><Chip color={result.success ? "success" : "error"} label={result.status} sx={{ alignSelf: "flex-start" }} /><Typography variant="body2">Provider: {result.provider}</Typography><Typography variant="body2">Request: {result.requestId}</Typography>{result.providerRequestId && <Typography variant="body2">Provider ID: {result.providerRequestId}</Typography>}{result.failureMessage && <Alert severity="warning">{result.failureMessage}</Alert>}</Stack>}</CardContent></Card>
      </Stack>
    </Stack>
  </Box>;
}
