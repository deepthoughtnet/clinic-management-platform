import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import {
  getAdminIntegrationsStatus,
  sendCarePilotProviderTestMessage,
  type AdminIntegrationStatus,
  type AdminIntegrationStatusRow,
} from "../../api/clinicApi";

function statusColor(status: AdminIntegrationStatus) {
  if (status === "READY") return "success" as const;
  if (status === "DISABLED") return "default" as const;
  if (status === "NOT_CONFIGURED") return "warning" as const;
  if (status === "FUTURE") return "info" as const;
  return "error" as const;
}

function categoryOrder(category: string) {
  if (category === "MESSAGING") return 1;
  if (category === "WEBHOOK") return 2;
  if (category === "WEBINAR") return 3;
  if (category === "AI_VOICE") return 4;
  return 9;
}

export default function IntegrationsPage() {
  const auth = useAuth();
  const navigate = useNavigate();

  const canView = auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("AUDITOR")
    || auth.rolesUpper.includes("PLATFORM_ADMIN")
    || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT");
  const canTest = auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("PLATFORM_ADMIN")
    || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT");

  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [rows, setRows] = React.useState<AdminIntegrationStatusRow[]>([]);

  const [testOpen, setTestOpen] = React.useState(false);
  const [testRow, setTestRow] = React.useState<AdminIntegrationStatusRow | null>(null);
  const [recipient, setRecipient] = React.useState("");
  const [subject, setSubject] = React.useState("Integration test message");
  const [body, setBody] = React.useState("This is a test message from Integrations page.");
  const [sending, setSending] = React.useState(false);
  const [testResult, setTestResult] = React.useState<string | null>(null);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await getAdminIntegrationsStatus(auth.accessToken, auth.tenantId);
      setRows([...data.rows].sort((a, b) => categoryOrder(a.category) - categoryOrder(b.category) || a.name.localeCompare(b.name)));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load integrations status");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const grouped = React.useMemo(() => {
    return {
      messaging: rows.filter((r) => r.category === "MESSAGING"),
      webhooks: rows.filter((r) => r.category === "WEBHOOK"),
      webinar: rows.filter((r) => r.category === "WEBINAR"),
      aiVoice: rows.filter((r) => r.category === "AI_VOICE"),
    };
  }, [rows]);

  async function runTest() {
    if (!auth.accessToken || !auth.tenantId || !testRow) return;
    const lower = testRow.key.toLowerCase();
    const channel = lower.includes("email") ? "EMAIL" : lower.includes("sms") ? "SMS" : "WHATSAPP";
    setSending(true);
    setTestResult(null);
    try {
      const result = await sendCarePilotProviderTestMessage(auth.accessToken, auth.tenantId, channel, {
        recipient,
        subject: channel === "EMAIL" ? subject : null,
        body,
      });
      setTestResult(result.success ? `Success: ${result.status}` : `Failed: ${result.status} ${result.errorMessage || ""}`);
    } catch (e) {
      setTestResult(e instanceof Error ? e.message : "Test send failed");
    } finally {
      setSending(false);
    }
  }

  function card(row: AdminIntegrationStatusRow) {
    const showTest = row.category === "MESSAGING" && row.supportsTestAction;
    return (
      <Card key={row.key} variant="outlined">
        <CardContent>
          <Stack spacing={1}>
            <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={1}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>{row.name}</Typography>
              <Chip size="small" color={statusColor(row.status)} label={row.status} />
            </Stack>
            <Typography variant="body2"><b>Provider:</b> {row.providerName || "-"}</Typography>
            <Typography variant="body2"><b>Enabled:</b> {row.enabled ? "Yes" : "No"} • <b>Configured:</b> {row.configured ? "Yes" : "No"}</Typography>
            <Typography variant="body2"><b>Message:</b> {row.message}</Typography>
            {row.missingConfigurationKeys.length > 0 ? <Typography variant="body2"><b>Missing:</b> {row.missingConfigurationKeys.join(", ")}</Typography> : null}
            {row.safeConfigurationHints.length > 0 ? (
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>Guidance</Typography>
                <Stack component="ul" sx={{ m: 0, pl: 2.5 }} spacing={0.25}>
                  {row.safeConfigurationHints.map((hint) => <Typography key={hint} component="li" variant="caption" sx={{ fontFamily: "monospace" }}>{hint}</Typography>)}
                </Stack>
              </Box>
            ) : null}
            <Typography variant="caption" color="text.secondary">Last checked: {new Date(row.lastCheckedAt).toLocaleString()}</Typography>
            <Divider />
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {showTest ? (
                <Button
                  size="small"
                  variant="contained"
                  disabled={!canTest || row.status === "DISABLED" || row.status === "FUTURE"}
                  onClick={() => {
                    setTestRow(row);
                    setRecipient("");
                    setTestResult(null);
                    setTestOpen(true);
                  }}
                >
                  Test
                </Button>
              ) : <Button size="small" disabled>Test not available yet</Button>}
              <Button size="small" variant="outlined" onClick={() => navigate("/carepilot/messaging")}>Open Messaging</Button>
              <Button size="small" variant="outlined" onClick={() => navigate("/admin/notification-settings")}>Notification Settings</Button>
              <Button size="small" variant="outlined" onClick={() => navigate("/admin/templates")}>Templates</Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    );
  }

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to view integrations.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to integrations.</Alert>;

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={1}>
        <Stack>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Integrations</Typography>
          <Typography variant="body2" color="text.secondary">Centralized readiness view for messaging, webhooks, webinar, and AI/voice integrations.</Typography>
        </Stack>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Stack>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Box sx={{ py: 8, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box> : null}

      {!loading ? (
        <Stack spacing={2}>
          <Typography variant="h6">Messaging Providers</Typography>
          {grouped.messaging.map(card)}
          <Typography variant="h6">Delivery Webhooks</Typography>
          {grouped.webhooks.map(card)}
          <Typography variant="h6">Webinar / Meeting Providers</Typography>
          {grouped.webinar.map(card)}
          <Typography variant="h6">AI / Voice Providers</Typography>
          {grouped.aiVoice.map(card)}
        </Stack>
      ) : null}

      <Dialog open={testOpen} onClose={() => setTestOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Test Integration: {testRow?.name}</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 0.5 }}>
            <TextField label="Recipient" value={recipient} onChange={(e) => setRecipient(e.target.value)} fullWidth />
            {testRow?.key.includes("email") ? <TextField label="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} fullWidth /> : null}
            <TextField label="Body" value={body} onChange={(e) => setBody(e.target.value)} multiline minRows={3} fullWidth />
            {testResult ? <Alert severity={testResult.startsWith("Success") ? "success" : "warning"}>{testResult}</Alert> : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTestOpen(false)}>Close</Button>
          <Button variant="contained" onClick={() => void runTest()} disabled={sending || !recipient.trim() || !body.trim() || (testRow?.key.includes("email") && !subject.trim())}>
            {sending ? "Sending..." : "Send Test"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
