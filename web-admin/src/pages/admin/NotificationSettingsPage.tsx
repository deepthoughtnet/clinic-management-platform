import * as React from "react";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Skeleton,
  Tooltip,
  Typography,
} from "@mui/material";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import CampaignRoundedIcon from "@mui/icons-material/CampaignRounded";
import EventAvailableRoundedIcon from "@mui/icons-material/EventAvailableRounded";
import MedicalServicesRoundedIcon from "@mui/icons-material/MedicalServicesRounded";
import ReceiptLongRoundedIcon from "@mui/icons-material/ReceiptLongRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import SettingsRoundedIcon from "@mui/icons-material/SettingsRounded";
import VaccinesRoundedIcon from "@mui/icons-material/VaccinesRounded";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { hasTenantModule } from "../../auth/moduleEntitlements";
import {
  getAdminNotificationSettings,
  listAdminTemplates,
  updateAdminNotificationSettings,
  type AdminNotificationChannel,
  type AdminNotificationSettings,
  type AdminNotificationSettingsUpdateInput,
  type AdminTemplate,
} from "../../api/clinicApi";
import {
  CHANNEL_ORDER,
  DEFAULT_COMPLIANCE,
  NOTIFICATION_POLICY_SECTIONS,
  QUIET_HOUR_SCOPE_OPTIONS,
  createDefaultNotificationPolicy,
  notificationTypeFilterParams,
  parseNotificationPolicy,
  selectCurrentTemplate,
  serializeNotificationPolicy,
  type NotificationPolicyConfig,
} from "./notificationSettingsModel";

const CHANNEL_LABELS: Record<AdminNotificationChannel, string> = {
  IN_APP: "In-App",
  EMAIL: "Email",
  SMS: "SMS",
  WHATSAPP: "WhatsApp",
};

const SECTION_ICONS: Record<string, React.ReactNode> = {
  appointments: <EventAvailableRoundedIcon fontSize="small" />,
  billing: <ReceiptLongRoundedIcon fontSize="small" />,
  clinical: <MedicalServicesRoundedIcon fontSize="small" />,
  laboratory: <ScienceRoundedIcon fontSize="small" />,
  vaccination: <VaccinesRoundedIcon fontSize="small" />,
  engage: <CampaignRoundedIcon fontSize="small" />,
  system: <SettingsRoundedIcon fontSize="small" />,
};

function clonePolicy(policy: NotificationPolicyConfig): NotificationPolicyConfig {
  return JSON.parse(JSON.stringify(policy)) as NotificationPolicyConfig;
}

function buildSnapshot(row: AdminNotificationSettings, policy: NotificationPolicyConfig): string {
  return JSON.stringify({
    emailEnabled: row.emailEnabled,
    smsEnabled: row.smsEnabled,
    whatsappEnabled: row.whatsappEnabled,
    inAppEnabled: row.inAppEnabled,
    defaultChannel: row.defaultChannel,
    fallbackChannel: row.fallbackChannel,
    quietHoursEnabled: row.quietHoursEnabled,
    quietHoursStart: row.quietHoursStart,
    quietHoursEnd: row.quietHoursEnd,
    timezone: row.timezone,
    allowMarketingMessages: row.allowMarketingMessages,
    requirePatientConsent: row.requirePatientConsent,
    unsubscribeFooterEnabled: row.unsubscribeFooterEnabled,
    maxMessagesPerPatientPerDay: row.maxMessagesPerPatientPerDay,
    notificationPolicyJson: serializeNotificationPolicy(policy),
  });
}

function templatePreview(template: AdminTemplate | null): string {
  if (!template) return "No template linked";
  const body = template.body || template.subject || "";
  return body.length > 96 ? `${body.slice(0, 96).trimEnd()}…` : body || "Template ready";
}

export default function NotificationSettingsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const canMutate = auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("PLATFORM_ADMIN")
    || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT");
  const canView = canMutate || auth.rolesUpper.includes("AUDITOR") || auth.rolesUpper.includes("RECEPTIONIST");
  const carePilotEnabled = hasTenantModule(auth, "carePilot");

  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [row, setRow] = React.useState<AdminNotificationSettings | null>(null);
  const [policy, setPolicy] = React.useState<NotificationPolicyConfig>(createDefaultNotificationPolicy());
  const [templates, setTemplates] = React.useState<AdminTemplate[]>([]);
  const [expandedSection, setExpandedSection] = React.useState<string>(searchParams.get("section") || NOTIFICATION_POLICY_SECTIONS[0].key);
  const initialSnapshotRef = React.useRef<string>("");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) return;
    setLoading(true);
    setError(null);
    try {
      const [settings, templateRows] = await Promise.all([
        getAdminNotificationSettings(auth.accessToken, auth.tenantId),
        listAdminTemplates(auth.accessToken, auth.tenantId, { templateType: "NOTIFICATION", active: true }),
      ]);
      const parsedPolicy = parseNotificationPolicy(settings.notificationPolicyJson);
      setRow(settings);
      setPolicy(parsedPolicy);
      setTemplates(templateRows);
      initialSnapshotRef.current = buildSnapshot(settings, parsedPolicy);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load settings");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView]);

  React.useEffect(() => {
    void load();
  }, [load]);

  React.useEffect(() => {
    const nextSection = searchParams.get("section");
    if (nextSection) {
      setExpandedSection(nextSection);
    }
  }, [searchParams]);

  const dirty = React.useMemo(() => {
    if (!row) return false;
    return buildSnapshot(row, policy) !== initialSnapshotRef.current;
  }, [policy, row]);

  React.useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty]);

  const setFlag = (key: keyof AdminNotificationSettingsUpdateInput, value: boolean) => {
    setRow((current) => (current ? ({ ...current, [key]: value } as AdminNotificationSettings) : current));
  };

  const setValue = (key: keyof AdminNotificationSettingsUpdateInput, value: string | number | null) => {
    setRow((current) => (current ? ({ ...current, [key]: value } as AdminNotificationSettings) : current));
  };

  const setPolicyChannel = (sectionKey: string, rowKey: string, channel: AdminNotificationChannel, enabled: boolean) => {
    setPolicy((current) => {
      const next = clonePolicy(current);
      next.sections[sectionKey][rowKey][channel] = enabled;
      return next;
    });
  };

  const setQuietHoursScope = (scope: string, enabled: boolean) => {
    setPolicy((current) => {
      const next = clonePolicy(current);
      next.quietHoursAppliesTo = enabled
        ? Array.from(new Set([...next.quietHoursAppliesTo, scope]))
        : next.quietHoursAppliesTo.filter((item) => item !== scope);
      return next;
    });
  };

  const setPolicyCompliance = (key: keyof NotificationPolicyConfig["compliance"], value: boolean | number | string) => {
    setPolicy((current) => {
      const next = clonePolicy(current);
      (next.compliance as Record<string, boolean | number | string>)[key] = value;
      return next;
    });
  };

  const setRateLimit = (key: keyof NotificationPolicyConfig["rateLimits"], value: number) => {
    setPolicy((current) => {
      const next = clonePolicy(current);
      next.rateLimits[key] = value;
      return next;
    });
  };

  async function save() {
    if (!auth.accessToken || !auth.tenantId || !row) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const serializedPolicy = serializeNotificationPolicy(policy);
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
        notificationPolicyJson: serializedPolicy,
      };
      const updated = await updateAdminNotificationSettings(auth.accessToken, auth.tenantId, payload);
      const updatedPolicy = parseNotificationPolicy(updated.notificationPolicyJson);
      setRow(updated);
      setPolicy(updatedPolicy);
      initialSnapshotRef.current = buildSnapshot(updated, updatedPolicy);
      setSuccess("Notification settings saved");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to save settings");
    } finally {
      setSaving(false);
    }
  }

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to configure notification settings.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to notification settings.</Alert>;
  if (loading || !row) {
    return (
      <Stack spacing={2}>
        <Skeleton variant="text" width="32%" height={52} />
        <Skeleton variant="text" width="55%" />
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Skeleton variant="rounded" height={220} />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Skeleton variant="rounded" height={220} />
          </Grid>
        </Grid>
        <Skeleton variant="rounded" height={380} />
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Skeleton variant="rounded" height={260} />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Skeleton variant="rounded" height={260} />
          </Grid>
        </Grid>
      </Stack>
    );
  }

  const providerHealth = [
    { label: "In-App", ready: true, description: "Built into the platform." },
    { label: "Email", ready: row.emailReady, description: row.emailReady ? "SMTP and email provider are ready." : "Email notifications are not ready." },
    { label: "SMS", ready: row.smsReady, description: row.smsReady ? "SMS provider is ready." : "SMS notifications are not ready." },
    { label: "WhatsApp", ready: row.whatsappReady, description: row.whatsappReady ? "WhatsApp provider is ready." : "WhatsApp notifications are not ready." },
  ];

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={1} alignItems={{ xs: "stretch", sm: "flex-start" }}>
        <Stack spacing={0.5}>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>Notification Configuration Center</Typography>
          <Typography variant="body2" color="text.secondary">
            Tenant-level communication defaults for clinic notifications, templates, quiet hours, consent, and rate limits.
          </Typography>
        </Stack>
        <Stack direction="row" gap={1} flexWrap="wrap" justifyContent="flex-end">
          {carePilotEnabled ? <Button variant="outlined" onClick={() => navigate("/carepilot/messaging")}>Open Messaging</Button> : null}
          <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
          {canMutate ? <Button variant="contained" onClick={() => void save()} disabled={saving || !dirty}>{saving ? "Saving..." : "Save changes"}</Button> : null}
        </Stack>
      </Stack>

      {dirty ? <Alert severity="warning">Unsaved changes detected.</Alert> : null}
      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
      {row.warnings.length ? <Alert severity="warning">{row.warnings.join(" | ")}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: "100%" }}>
            <CardContent>
              <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1 }}>
                <SettingsRoundedIcon color="primary" />
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Channel Configuration</Typography>
                  <Typography variant="body2" color="text.secondary">Runtime channel enablement and fallback behavior.</Typography>
                  <Typography variant="caption" color="text.secondary">Enable only channels that are ready for tenant use.</Typography>
                </Box>
              </Stack>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormControlLabel
                    control={<Switch checked={row.inAppEnabled} onChange={(e) => setFlag("inAppEnabled", e.target.checked)} disabled={!canMutate} />}
                    label="In-App enabled"
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <FormControlLabel
                      control={<Switch checked={row.emailEnabled} onChange={(e) => setFlag("emailEnabled", e.target.checked)} disabled={!canMutate || (!row.emailReady && !row.emailEnabled)} />}
                      label="Email enabled"
                    />
                    <Chip size="small" color={row.emailReady ? "success" : "default"} variant="outlined" label={row.emailReady ? "Configuration Ready" : "Not configured"} />
                  </Stack>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <FormControlLabel
                      control={<Switch checked={row.smsEnabled} onChange={(e) => setFlag("smsEnabled", e.target.checked)} disabled={!canMutate || (!row.smsReady && !row.smsEnabled)} />}
                      label="SMS enabled"
                    />
                    <Chip size="small" color={row.smsReady ? "success" : "default"} variant="outlined" label={row.smsReady ? "Configuration Ready" : "Not configured"} />
                  </Stack>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <FormControlLabel
                      control={<Switch checked={row.whatsappEnabled} onChange={(e) => setFlag("whatsappEnabled", e.target.checked)} disabled={!canMutate || (!row.whatsappReady && !row.whatsappEnabled)} />}
                      label="WhatsApp enabled"
                    />
                    <Chip size="small" color={row.whatsappReady ? "success" : "default"} variant="outlined" label={row.whatsappReady ? "Configuration Ready" : "Not configured"} />
                  </Stack>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormControl fullWidth>
                    <InputLabel>Default channel</InputLabel>
                    <Select value={row.defaultChannel} label="Default channel" onChange={(e) => setValue("defaultChannel", e.target.value)} disabled={!canMutate}>
                      {CHANNEL_ORDER.map((channel) => <MenuItem key={channel} value={channel}>{CHANNEL_LABELS[channel]}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <FormControl fullWidth>
                    <InputLabel>Fallback channel</InputLabel>
                    <Select value={row.fallbackChannel || ""} label="Fallback channel" onChange={(e) => setValue("fallbackChannel", e.target.value || null)} disabled={!canMutate}>
                      <MenuItem value="">None</MenuItem>
                      {CHANNEL_ORDER.map((channel) => <MenuItem key={channel} value={channel}>{CHANNEL_LABELS[channel]}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: "100%" }}>
            <CardContent>
              <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1 }}>
                <ReceiptLongRoundedIcon color="primary" />
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Provider Health</Typography>
                  <Typography variant="body2" color="text.secondary">Readiness summary for external delivery providers.</Typography>
                  <Typography variant="caption" color="text.secondary">Configuration Ready reflects setup state; live health is shown only when available.</Typography>
                </Box>
              </Stack>
              <Stack spacing={1}>
                {providerHealth.map((item) => (
                  <Stack key={item.label} direction="row" spacing={1.5} alignItems="center" justifyContent="space-between" sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1 }}>
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.label}</Typography>
                      <Typography variant="caption" color="text.secondary">{item.description}</Typography>
                    </Box>
                    <Chip size="small" color={item.ready ? "success" : "default"} label={item.ready ? "Configuration Ready" : "Not configured"} />
                  </Stack>
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Notification Matrix</Typography>
            <Chip size="small" label="Tenant default policy" />
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Default delivery policy for each notification type. Patient-level preferences can override later.
          </Typography>

          <Stack spacing={1.5}>
            {NOTIFICATION_POLICY_SECTIONS.map((section) => (
              <Accordion
                key={section.key}
                expanded={expandedSection === section.key}
                onChange={(_, expanded) => {
                  const next = expanded ? section.key : "";
                  setExpandedSection(next);
                  if (next) {
                    setSearchParams({ section: next }, { replace: true });
                  } else {
                    setSearchParams({}, { replace: true });
                  }
                }}
                disableGutters
                sx={{ borderRadius: 2, overflow: "hidden", "&:before": { display: "none" } }}
              >
                <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />} sx={{ px: 1.5 }}>
                  <Stack direction="row" spacing={1.25} alignItems="center" justifyContent="space-between" sx={{ width: "100%" }}>
                    <Stack direction="row" spacing={1.25} alignItems="center">
                      {SECTION_ICONS[section.key] || <SettingsRoundedIcon fontSize="small" />}
                      <Box>
                        <Typography sx={{ fontWeight: 800 }}>{section.title}</Typography>
                        <Typography variant="body2" color="text.secondary">{section.description}</Typography>
                      </Box>
                    </Stack>
                    <Typography variant="caption" color="text.secondary" sx={{ display: { xs: "none", sm: "block" } }}>
                      Manage templates and tenant policy together
                    </Typography>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ px: 1.5, pb: 1.5 }}>
                  {(() => {
                    const sectionTemplates = section.rows.map((spec) => selectCurrentTemplate(templates, spec)).filter((template): template is AdminTemplate => Boolean(template));
                    const uniqueTemplates = Array.from(new Map(sectionTemplates.map((template) => [template.id, template])).values());
                    const sharedTemplate = uniqueTemplates.length === 1 ? uniqueTemplates[0] : null;
                    const templateUrl = "/admin/templates?templateType=NOTIFICATION";
                    return (
                      <Stack spacing={1.5}>
                        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {sharedTemplate ? `Current template: ${sharedTemplate.name}` : `${uniqueTemplates.length || 0} templates in use`}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              Template changes affect future notifications only. Repeat links are hidden when a section shares one template.
                            </Typography>
                          </Box>
                          <Button
                            size="small"
                            variant="outlined"
                            endIcon={<OpenInNewRoundedIcon fontSize="small" />}
                            onClick={() => navigate(sharedTemplate ? `/admin/templates?templateType=NOTIFICATION&templateId=${sharedTemplate.id}` : templateUrl)}
                          >
                            Manage Template
                          </Button>
                        </Box>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell sx={{ fontWeight: 800 }}>Notification Type</TableCell>
                        {CHANNEL_ORDER.map((channel) => <TableCell key={channel} align="center" sx={{ fontWeight: 800 }}>{CHANNEL_LABELS[channel]}</TableCell>)}
                        <TableCell sx={{ fontWeight: 800 }}>Template</TableCell>
                      </TableRow>
                    </TableHead>
                      <TableBody>
                        {section.rows.map((spec) => {
                          const template = selectCurrentTemplate(templates, spec);
                          const templateUrl = `/admin/templates?${notificationTypeFilterParams(spec)}`;
                          const rowPolicy = policy.sections[section.key]?.[spec.key] || spec.defaultChannels;
                          const repeatAction = uniqueTemplates.length > 1;
                          return (
                            <TableRow key={spec.key} hover>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography sx={{ fontWeight: 700 }}>{spec.label}</Typography>
                                <Typography variant="caption" color="text.secondary">{spec.description}</Typography>
                              </Stack>
                            </TableCell>
                            {CHANNEL_ORDER.map((channel) => (
                              <TableCell key={channel} align="center">
                                <Tooltip title={`${CHANNEL_LABELS[channel]}: ${rowPolicy[channel] ? "Enabled" : "Disabled"}`}>
                                  <span>
                                    <Checkbox
                                      size="small"
                                      checked={Boolean(rowPolicy[channel])}
                                      onChange={(event) => setPolicyChannel(section.key, spec.key, channel, event.target.checked)}
                                      disabled={!canMutate}
                                      inputProps={{ "aria-label": `${spec.label} ${CHANNEL_LABELS[channel]}` }}
                                    />
                                  </span>
                                </Tooltip>
                              </TableCell>
                            ))}
                              <TableCell sx={{ minWidth: 280 }}>
                                <Stack spacing={0.35}>
                                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                      {template ? template.name : "No template linked"}
                                    </Typography>
                                    <Chip size="small" variant="outlined" label={`${spec.templateType} · ${spec.templateCategory}`} />
                                  </Stack>
                                  <Typography variant="caption" color="text.secondary" sx={{ display: "-webkit-box", WebkitBoxOrient: "vertical", WebkitLineClamp: 2, overflow: "hidden" }}>
                                    {templatePreview(template)}
                                  </Typography>
                                  {repeatAction ? (
                                    <Button
                                      size="small"
                                      variant="text"
                                      endIcon={<OpenInNewRoundedIcon fontSize="small" />}
                                      onClick={() => navigate(templateUrl)}
                                    >
                                      Open Template
                                    </Button>
                                  ) : null}
                                </Stack>
                              </TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                      </Table>
                      </Stack>
                    );
                  })()}
                </AccordionDetails>
              </Accordion>
            ))}
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
          <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1 }}>
            <SettingsRoundedIcon color="primary" />
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Quiet Hours</Typography>
              <Typography variant="body2" color="text.secondary">Defers non-critical notifications during tenant quiet hours.</Typography>
              <Typography variant="caption" color="text.secondary">Critical alerts bypass quiet hours.</Typography>
            </Box>
          </Stack>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormControlLabel
                    control={<Switch checked={row.quietHoursEnabled} onChange={(e) => setFlag("quietHoursEnabled", e.target.checked)} disabled={!canMutate} />}
                    label="Enabled"
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField fullWidth label="Timezone" value={row.timezone || ""} onChange={(e) => setValue("timezone", e.target.value)} disabled={!canMutate} />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField fullWidth type="time" label="Start" value={row.quietHoursStart || ""} onChange={(e) => setValue("quietHoursStart", e.target.value)} disabled={!canMutate} InputLabelProps={{ shrink: true }} />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField fullWidth type="time" label="End" value={row.quietHoursEnd || ""} onChange={(e) => setValue("quietHoursEnd", e.target.value)} disabled={!canMutate} InputLabelProps={{ shrink: true }} />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>Apply Quiet Hours To</Typography>
                  <Stack direction="row" flexWrap="wrap" gap={1}>
                    {QUIET_HOUR_SCOPE_OPTIONS.map((option) => (
                      <FormControlLabel
                        key={option.value}
                        control={<Checkbox checked={policy.quietHoursAppliesTo.includes(option.value)} onChange={(event) => setQuietHoursScope(option.value, event.target.checked)} disabled={!canMutate} />}
                        label={option.label}
                      />
                    ))}
                  </Stack>
                  <Typography variant="caption" color="text.secondary">Critical alerts ignore quiet hours.</Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1 }}>
                <MedicalServicesRoundedIcon color="primary" />
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Compliance / Consent</Typography>
                  <Typography variant="body2" color="text.secondary">Tenant defaults for transactional, clinical, and marketing policy.</Typography>
                  <Typography variant="caption" color="text.secondary">Patient-level preferences override tenant defaults.</Typography>
                </Box>
              </Stack>
              <Stack spacing={1.25}>
                <FormControlLabel control={<Switch checked={policy.compliance.transactionalMessagesEnabled} onChange={(e) => setPolicyCompliance("transactionalMessagesEnabled", e.target.checked)} disabled={!canMutate} />} label="Transactional Messages" />
                <FormControlLabel control={<Switch checked={policy.compliance.clinicalNotificationsEnabled} onChange={(e) => setPolicyCompliance("clinicalNotificationsEnabled", e.target.checked)} disabled={!canMutate} />} label="Clinical Notifications" />
                <FormControlLabel control={<Switch checked={policy.compliance.marketingEnabled} onChange={(e) => { setPolicyCompliance("marketingEnabled", e.target.checked); setFlag("allowMarketingMessages", e.target.checked); }} disabled={!canMutate} />} label="Marketing" />
                <FormControlLabel control={<Switch checked={policy.compliance.patientConsentRequired} onChange={(e) => { setPolicyCompliance("patientConsentRequired", e.target.checked); setFlag("requirePatientConsent", e.target.checked); }} disabled={!canMutate} />} label="Patient consent required" />
                <TextField
                  fullWidth
                  type="number"
                  label="Retention period (days)"
                  value={policy.compliance.retentionDays}
                  onChange={(e) => setPolicyCompliance("retentionDays", Number(e.target.value || 0))}
                  disabled={!canMutate}
                  helperText={DEFAULT_COMPLIANCE.helpMessage}
                />
                <FormControlLabel control={<Switch checked={policy.compliance.auditEnabled} onChange={(e) => setPolicyCompliance("auditEnabled", e.target.checked)} disabled={!canMutate} />} label="Audit enabled" />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Rate Limits</Typography>
            <Chip size="small" label="Persisted Only" />
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            These limits define tenant defaults. Enforcement remains an engine concern and is not changed by this batch.
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 2 }}>
            Rate limits define policy. Enforcement depends on provider implementation.
          </Typography>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField fullWidth type="number" label="Overall messages/day" value={policy.rateLimits.overallMessagesPerDay} onChange={(e) => setRateLimit("overallMessagesPerDay", Number(e.target.value || 0))} disabled={!canMutate} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField fullWidth type="number" label="Marketing/day" value={policy.rateLimits.marketingPerDay} onChange={(e) => setRateLimit("marketingPerDay", Number(e.target.value || 0))} disabled={!canMutate} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField fullWidth type="number" label="Reminder/day" value={policy.rateLimits.reminderPerDay} onChange={(e) => setRateLimit("reminderPerDay", Number(e.target.value || 0))} disabled={!canMutate} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField fullWidth type="number" label="Maximum/hour" value={policy.rateLimits.maximumPerHour} onChange={(e) => setRateLimit("maximumPerHour", Number(e.target.value || 0))} disabled={!canMutate} />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField fullWidth type="number" label="Per patient/day" value={policy.rateLimits.perPatientPerDay} onChange={(e) => { setRateLimit("perPatientPerDay", Number(e.target.value || 0)); setValue("maxMessagesPerPatientPerDay", Number(e.target.value || 0)); }} disabled={!canMutate} />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {!canMutate ? <Alert severity="info">Read-only mode for your role.</Alert> : null}

      <Snackbar open={Boolean(success)} autoHideDuration={3000} onClose={() => setSuccess(null)} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        <Alert severity="success" variant="filled" onClose={() => setSuccess(null)}>{success}</Alert>
      </Snackbar>
    </Stack>
  );
}
