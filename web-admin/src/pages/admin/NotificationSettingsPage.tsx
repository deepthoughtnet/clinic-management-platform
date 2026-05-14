import * as React from "react";
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import {
  getAdminNotificationSettings,
  updateAdminNotificationSettings,
  type AdminNotificationChannel,
  type AdminNotificationSettings,
  type AdminNotificationSettingsUpdateInput,
} from "../../api/clinicApi";

const CHANNELS: AdminNotificationChannel[] = ["EMAIL", "SMS", "WHATSAPP", "IN_APP"];

export default function NotificationSettingsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const canMutate = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN") || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT");
  const canView = canMutate || auth.rolesUpper.includes("AUDITOR") || auth.rolesUpper.includes("RECEPTIONIST");

  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [row, setRow] = React.useState<AdminNotificationSettings | null>(null);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) return;
    setLoading(true);
    setError(null);
    try {
      const data = await getAdminNotificationSettings(auth.accessToken, auth.tenantId);
      setRow(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load settings");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const setFlag = (key: keyof AdminNotificationSettingsUpdateInput, value: boolean) => {
    setRow((current) => (current ? ({ ...current, [key]: value } as AdminNotificationSettings) : current));
  };

  const setValue = (key: keyof AdminNotificationSettingsUpdateInput, value: string | number | null) => {
    setRow((current) => (current ? ({ ...current, [key]: value } as AdminNotificationSettings) : current));
  };

  async function save() {
    if (!auth.accessToken || !auth.tenantId || !row) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const payload: AdminNotificationSettingsUpdateInput = {
        emailEnabled: row.emailEnabled,
        smsEnabled: row.smsEnabled,
        whatsappEnabled: row.whatsappEnabled,
        inAppEnabled: row.inAppEnabled,
        appointmentRemindersEnabled: row.appointmentRemindersEnabled,
        appointmentReminder24hEnabled: row.appointmentReminder24hEnabled,
        appointmentReminder2hEnabled: row.appointmentReminder2hEnabled,
        followUpRemindersEnabled: row.followUpRemindersEnabled,
        billingRemindersEnabled: row.billingRemindersEnabled,
        refillRemindersEnabled: row.refillRemindersEnabled,
        vaccinationRemindersEnabled: row.vaccinationRemindersEnabled,
        leadFollowUpRemindersEnabled: row.leadFollowUpRemindersEnabled,
        webinarRemindersEnabled: row.webinarRemindersEnabled,
        birthdayWellnessEnabled: row.birthdayWellnessEnabled,
        quietHoursEnabled: row.quietHoursEnabled,
        quietHoursStart: row.quietHoursStart,
        quietHoursEnd: row.quietHoursEnd,
        timezone: row.timezone,
        defaultChannel: row.defaultChannel,
        fallbackChannel: row.fallbackChannel,
        allowMarketingMessages: row.allowMarketingMessages,
        requirePatientConsent: row.requirePatientConsent,
        unsubscribeFooterEnabled: row.unsubscribeFooterEnabled,
        maxMessagesPerPatientPerDay: row.maxMessagesPerPatientPerDay,
      };
      const updated = await updateAdminNotificationSettings(auth.accessToken, auth.tenantId, payload);
      setRow(updated);
      setSuccess("Notification settings saved");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to save settings");
    } finally {
      setSaving(false);
    }
  }

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to configure notification settings.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to notification settings.</Alert>;
  if (loading || !row) return <Alert severity="info">Loading notification settings...</Alert>;

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={1}>
        <Stack>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Notification Settings</Typography>
          <Typography variant="body2" color="text.secondary">Tenant-level communication defaults for Clinic and CarePilot reminders/campaigns.</Typography>
        </Stack>
        <Stack direction="row" gap={1}>
          <Button variant="outlined" onClick={() => navigate("/carepilot/messaging")}>Open Messaging</Button>
          {canMutate ? <Button variant="contained" onClick={() => void save()} disabled={saving}>{saving ? "Saving..." : "Save"}</Button> : null}
        </Stack>
      </Stack>

      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
      {success ? <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert> : null}
      {!canMutate ? <Alert severity="info">Read-only mode for your role.</Alert> : null}
      {row.warnings.length ? <Alert severity="warning">{row.warnings.join(" | ")}</Alert> : null}

      <Card>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Channel Defaults</Typography>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 3 }}><FormControlLabel control={<Switch checked={row.emailEnabled} onChange={(e) => setFlag("emailEnabled", e.target.checked)} disabled={!canMutate} />} label="Email enabled" /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControlLabel control={<Switch checked={row.smsEnabled} onChange={(e) => setFlag("smsEnabled", e.target.checked)} disabled={!canMutate} />} label="SMS enabled" /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControlLabel control={<Switch checked={row.whatsappEnabled} onChange={(e) => setFlag("whatsappEnabled", e.target.checked)} disabled={!canMutate} />} label="WhatsApp enabled" /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><FormControlLabel control={<Switch checked={row.inAppEnabled} onChange={(e) => setFlag("inAppEnabled", e.target.checked)} disabled={!canMutate} />} label="In-app enabled" /></Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <FormControl fullWidth>
                <InputLabel>Default channel</InputLabel>
                <Select value={row.defaultChannel} label="Default channel" onChange={(e) => setValue("defaultChannel", e.target.value)} disabled={!canMutate}>
                  {CHANNELS.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <FormControl fullWidth>
                <InputLabel>Fallback channel</InputLabel>
                <Select value={row.fallbackChannel || ""} label="Fallback channel" onChange={(e) => setValue("fallbackChannel", e.target.value || null)} disabled={!canMutate}>
                  <MenuItem value="">None</MenuItem>
                  {CHANNELS.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip size="small" label={`Email ${row.emailReady ? "Ready" : "Not configured"}`} color={row.emailReady ? "success" : "warning"} />
                <Chip size="small" label={`SMS ${row.smsReady ? "Ready" : "Not configured"}`} color={row.smsReady ? "success" : "warning"} />
                <Chip size="small" label={`WhatsApp ${row.whatsappReady ? "Ready" : "Not configured"}`} color={row.whatsappReady ? "success" : "warning"} />
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Reminder Types</Typography>
          <Grid container spacing={1}>
            {["appointmentRemindersEnabled", "appointmentReminder24hEnabled", "appointmentReminder2hEnabled", "followUpRemindersEnabled", "billingRemindersEnabled", "refillRemindersEnabled", "vaccinationRemindersEnabled", "leadFollowUpRemindersEnabled", "webinarRemindersEnabled", "birthdayWellnessEnabled"].map((k) => (
              <Grid key={k} size={{ xs: 12, sm: 6, md: 4 }}>
                <FormControlLabel
                  control={<Switch checked={Boolean((row as any)[k])} onChange={(e) => setFlag(k as keyof AdminNotificationSettingsUpdateInput, e.target.checked)} disabled={!canMutate} />}
                  label={k.replace(/Enabled$/, "").replace(/([A-Z])/g, " $1").trim()}
                />
              </Grid>
            ))}
          </Grid>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Quiet Hours</Typography>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 3 }}><FormControlLabel control={<Switch checked={row.quietHoursEnabled} onChange={(e) => setFlag("quietHoursEnabled", e.target.checked)} disabled={!canMutate} />} label="Enabled" /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Timezone" value={row.timezone || ""} onChange={(e) => setValue("timezone", e.target.value)} disabled={!canMutate} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="time" label="Start" value={row.quietHoursStart || ""} onChange={(e) => setValue("quietHoursStart", e.target.value)} disabled={!canMutate} InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="time" label="End" value={row.quietHoursEnd || ""} onChange={(e) => setValue("quietHoursEnd", e.target.value)} disabled={!canMutate} InputLabelProps={{ shrink: true }} /></Grid>
          </Grid>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Compliance / Consent</Typography>
          <Stack direction={{ xs: "column", md: "row" }} gap={2}>
            <FormControlLabel control={<Switch checked={row.requirePatientConsent} onChange={(e) => setFlag("requirePatientConsent", e.target.checked)} disabled={!canMutate} />} label="Require patient consent" />
            <FormControlLabel control={<Switch checked={row.allowMarketingMessages} onChange={(e) => setFlag("allowMarketingMessages", e.target.checked)} disabled={!canMutate} />} label="Allow marketing messages" />
            <FormControlLabel control={<Switch checked={row.unsubscribeFooterEnabled} onChange={(e) => setFlag("unsubscribeFooterEnabled", e.target.checked)} disabled={!canMutate} />} label="Unsubscribe footer" />
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Rate Limits</Typography>
          <TextField
            type="number"
            label="Max messages per patient per day"
            value={row.maxMessagesPerPatientPerDay}
            onChange={(e) => setValue("maxMessagesPerPatientPerDay", Number(e.target.value || 0))}
            disabled={!canMutate}
            sx={{ maxWidth: 280 }}
          />
        </CardContent>
      </Card>
    </Stack>
  );
}
