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
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import SendRoundedIcon from "@mui/icons-material/SendRounded";
import { useAuth } from "../../../auth/useAuth";
import {
  listCarePilotMessagingProviderStatuses,
  sendCarePilotProviderTestMessage,
  type CarePilotMessagingProviderStatus,
  type CarePilotProviderReadinessStatus,
} from "../../../api/clinicApi";
import { ApiClientError } from "../../../api/restClient";

function statusColor(status: CarePilotProviderReadinessStatus) {
  if (status === "READY") return "success" as const;
  if (status === "DISABLED") return "default" as const;
  if (status === "NOT_CONFIGURED") return "warning" as const;
  return "error" as const;
}

function guidance(channel: CarePilotMessagingProviderStatus["channel"]): string[] {
  if (channel === "EMAIL") {
    return [
      "CLINIC_CAREPILOT_MESSAGING_EMAIL_ENABLED=true",
      "CLINIC_CAREPILOT_MESSAGING_EMAIL_FROM_ADDRESS=carepilot@your-clinic.com",
      "CLINIC_MAIL_PROVIDER=smtp",
      "CLINIC_MAIL_ENABLED=true",
    ];
  }
  if (channel === "SMS") {
    return [
      "CLINIC_CAREPILOT_MESSAGING_SMS_ENABLED=true",
      "CLINIC_CAREPILOT_MESSAGING_SMS_PROVIDER=generic-http",
      "CLINIC_CAREPILOT_MESSAGING_SMS_API_URL=https://example-sms-provider/send",
      "CLINIC_CAREPILOT_MESSAGING_SMS_FROM_NUMBER=CLINIC or CLINIC_CAREPILOT_MESSAGING_SMS_SENDER_ID=CLINIC",
      "CLINIC_CAREPILOT_MESSAGING_SMS_API_KEY=<secret>",
    ];
  }
  return [
    "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_ENABLED=true",
    "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_PROVIDER=meta-cloud-api",
    "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_API_URL=https://graph.facebook.com/v18.0/<phone-number-id>/messages",
    "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_PHONE_NUMBER_ID=<id>",
    "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_BUSINESS_ACCOUNT_ID=<id>",
    "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_ACCESS_TOKEN=<secret>",
  ];
}

export default function MessagingPage() {
  const auth = useAuth();
  const canView = auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("AUDITOR")
    || ((auth.rolesUpper.includes("PLATFORM_ADMIN") || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT")) && Boolean(auth.tenantId));
  const canTestSend = auth.rolesUpper.includes("CLINIC_ADMIN") || ((auth.rolesUpper.includes("PLATFORM_ADMIN") || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT")) && Boolean(auth.tenantId));

  const [loading, setLoading] = React.useState(true);
  const [refreshing, setRefreshing] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [providers, setProviders] = React.useState<CarePilotMessagingProviderStatus[]>([]);

  const [testOpen, setTestOpen] = React.useState(false);
  const [testChannel, setTestChannel] = React.useState<"EMAIL" | "SMS" | "WHATSAPP">("EMAIL");
  const [recipient, setRecipient] = React.useState("");
  const [subject, setSubject] = React.useState("CarePilot test message");
  const [body, setBody] = React.useState("This is a CarePilot provider test message.");
  const [sending, setSending] = React.useState(false);
  const [sendResult, setSendResult] = React.useState<string | null>(null);
  const selectedProvider = providers.find((provider) => provider.channel === testChannel);
  const selectedProviderDisabled = selectedProvider?.status === "DISABLED";

  const load = React.useCallback(async (isRefresh = false) => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    try {
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      const rows = await listCarePilotMessagingProviderStatuses(auth.accessToken, auth.tenantId);
      setProviders(rows.filter((row) => row.channel === "EMAIL" || row.channel === "SMS" || row.channel === "WHATSAPP"));
      setError(null);
    } catch (e) {
      const message = e instanceof ApiClientError ? e.message : e instanceof Error ? e.message : "Failed to load messaging provider status.";
      setError(message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [auth.accessToken, auth.tenantId, canView]);

  React.useEffect(() => {
    void load(false);
  }, [load]);

  async function submitTestSend() {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      setSending(true);
      setSendResult(null);
      const result = await sendCarePilotProviderTestMessage(auth.accessToken, auth.tenantId, testChannel, {
        recipient,
        subject: testChannel === "EMAIL" ? subject : null,
        body,
      });
      const line = result.success
        ? `Success: ${result.status}${result.providerMessageId ? ` • id=${result.providerMessageId}` : ""}`
        : `Failed: ${result.status}${result.errorMessage ? ` • ${result.errorMessage}` : ""}`;
      setSendResult(line);
      await load(true);
    } catch (e) {
      const message = e instanceof ApiClientError ? e.message : e instanceof Error ? e.message : "Test send failed.";
      setSendResult(message);
    } finally {
      setSending(false);
    }
  }

  if (!canView) return <Alert severity="error">You do not have access to CarePilot messaging provider status.</Alert>;

  return (
    <Stack spacing={2.5}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={1}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>Messaging Providers</Typography>
          <Typography variant="body2" color="text.secondary">Visibility for CarePilot provider readiness across EMAIL, SMS, and WhatsApp.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" startIcon={<RefreshRoundedIcon />} onClick={() => void load(true)} disabled={refreshing || loading}>
            {refreshing ? "Refreshing..." : "Refresh"}
          </Button>
          <Button
            variant="contained"
            startIcon={<SendRoundedIcon />}
            onClick={() => {
              setSendResult(null);
              setTestOpen(true);
            }}
            disabled={!canTestSend}
          >
            {canTestSend ? "Test Send" : "Test send coming soon"}
          </Button>
        </Stack>
      </Stack>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? (
        <Box sx={{ py: 8, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box>
      ) : (
        <Stack spacing={2}>
          {providers.map((provider) => (
            <Card key={provider.channel} variant="outlined">
              <CardContent>
                <Stack spacing={1.25}>
                  <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={1}>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>{provider.channel}</Typography>
                    <Chip color={statusColor(provider.status)} label={provider.status} size="small" />
                  </Stack>
                  <Typography variant="body2"><b>Provider:</b> {provider.providerName}</Typography>
                  <Typography variant="body2"><b>Enabled:</b> {provider.enabled ? "Yes" : "No"} • <b>Configured:</b> {provider.configured ? "Yes" : "No"} • <b>Available:</b> {provider.available ? "Yes" : "No"}</Typography>
                  {provider.channel === "EMAIL" ? (
                    <Typography variant="body2"><b>SMTP Host Configured:</b> {provider.smtpHostConfigured ? "Yes" : "No"}</Typography>
                  ) : null}
                  <Typography variant="body2"><b>Message:</b> {provider.message}</Typography>
                  {provider.channel !== "EMAIL" && provider.status !== "READY" ? (
                    <Alert severity="info" sx={{ py: 0.5 }}>Adapter foundation is available; vendor not configured.</Alert>
                  ) : null}
                  {provider.missingConfigurationKeys.length > 0 ? (
                    <Typography variant="body2"><b>Missing:</b> {provider.missingConfigurationKeys.join(", ")}</Typography>
                  ) : null}
                  <Typography variant="body2" color="text.secondary"><b>Last checked:</b> {new Date(provider.lastCheckedAt).toLocaleString()}</Typography>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>Configuration guidance</Typography>
                    <Stack component="ul" sx={{ pl: 2.5, m: 0 }} spacing={0.25}>
                      {guidance(provider.channel).map((row) => (
                        <Typography component="li" variant="caption" key={`${provider.channel}-${row}`} sx={{ fontFamily: "monospace" }}>
                          {row}
                        </Typography>
                      ))}
                    </Stack>
                  </Box>
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}

      <Dialog open={testOpen} onClose={() => setTestOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Send Provider Test Message</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 0.5 }}>
            <TextField
              select
              label="Channel"
              value={testChannel}
              onChange={(e) => setTestChannel(e.target.value as "EMAIL" | "SMS" | "WHATSAPP")}
              SelectProps={{ native: true }}
            >
              <option value="EMAIL">EMAIL</option>
              <option value="SMS">SMS</option>
              <option value="WHATSAPP">WHATSAPP</option>
            </TextField>
            <TextField
              label={testChannel === "EMAIL" ? "Recipient Email" : "Recipient Phone"}
              value={recipient}
              onChange={(e) => setRecipient(e.target.value)}
              fullWidth
            />
            {testChannel === "EMAIL" ? (
              <TextField label="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} fullWidth />
            ) : null}
            <TextField label="Body" value={body} onChange={(e) => setBody(e.target.value)} fullWidth multiline minRows={3} />
            {sendResult ? <Alert severity={sendResult.startsWith("Success") ? "success" : "warning"}>{sendResult}</Alert> : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTestOpen(false)}>Close</Button>
          <Button
            onClick={() => void submitTestSend()}
            variant="contained"
            disabled={sending || !recipient.trim() || (testChannel === "EMAIL" && !subject.trim()) || !body.trim() || !canTestSend || selectedProviderDisabled}
          >
            {sending ? "Sending..." : "Send Test"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
