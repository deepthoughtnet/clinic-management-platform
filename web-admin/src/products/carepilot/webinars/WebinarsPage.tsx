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
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../../auth/useAuth";
import { mapZodErrors, engageWebinarRegistrationSchema, normalizeIndianMobileInput } from "@deepthoughtnet/form-validation-kit";
import {
  ENGAGE_WEBINAR_CANCEL,
  ENGAGE_WEBINAR_CREATE,
  ENGAGE_WEBINAR_EDIT,
  ENGAGE_WEBINAR_MANAGE_REGISTRATIONS,
  ENGAGE_WEBINAR_PUBLISH,
  ENGAGE_WEBINAR_RECORD_ATTENDANCE,
  ENGAGE_WEBINAR_RUN_AUTOMATION,
  ENGAGE_WEBINAR_VIEW,
  ENGAGE_WEBINAR_VIEW_ANALYTICS,
  ENGAGE_WEBINAR_VIEW_AUDIT,
} from "../../../auth/permissions";
import {
  createCarePilotWebinar,
  getCarePilotWebinarAnalyticsSummary,
  listCarePilotCampaigns,
  listCarePilotWebinarRegistrations,
  listCarePilotWebinars,
  markCarePilotWebinarAttendance,
  registerCarePilotWebinarAttendee,
  updateCarePilotWebinar,
  updateCarePilotWebinarStatus,
  type CarePilotCampaign,
  type CarePilotWebinar,
  type CarePilotWebinarAnalyticsSummary,
  type CarePilotWebinarRegistration,
  type CarePilotWebinarRegistrationStatus,
  type CarePilotWebinarStatus,
  type CarePilotWebinarType,
} from "../../../api/clinicApi";
import { ApiClientError } from "../../../api/restClient";
import { formatCarePilotDateTime, formatCarePilotDateTimeInput, humanizeCarePilotCode } from "../shared/carepilotFormatting";
import { buildWebinarPayload, getWebinarDateFieldErrors, validateWebinarDraft } from "./webinarFormUtils";

const WEBINAR_TYPES: CarePilotWebinarType[] = ["HEALTH_AWARENESS", "WELLNESS", "CLINIC_EVENT", "MARKETING", "EDUCATIONAL", "OTHER"];
type WebinarTab = "DRAFTS" | "UPCOMING" | "LIVE" | "COMPLETED" | "CANCELLED";

const WEBINAR_TAB_ORDER: WebinarTab[] = ["DRAFTS", "UPCOMING", "LIVE", "COMPLETED", "CANCELLED"];
const WEBINAR_STATUS_OPTIONS: Array<{ value: CarePilotWebinarStatus | ""; label: string }> = [
  { value: "", label: "All" },
  { value: "DRAFT", label: "Draft" },
  { value: "SCHEDULED", label: "Upcoming" },
  { value: "LIVE", label: "Live" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
];

function webinarStatusLabel(status: CarePilotWebinarStatus | "" | null | undefined) {
  if (!status) return "-";
  if (status === "DRAFT") return "Draft";
  if (status === "SCHEDULED") return "Upcoming";
  return humanizeCarePilotCode(status);
}

function webinarTypeLabel(type: CarePilotWebinarType | null | undefined) {
  return humanizeCarePilotCode(type);
}

function webinarTabLabel(tab: WebinarTab) {
  if (tab === "DRAFTS") return "Drafts";
  if (tab === "UPCOMING") return "Upcoming";
  if (tab === "LIVE") return "Live";
  if (tab === "COMPLETED") return "Completed";
  return "Cancelled";
}

function webinarTabEmptyState(tab: WebinarTab) {
  if (tab === "DRAFTS") return "No draft webinars found.";
  if (tab === "UPCOMING") return "No upcoming webinars found.";
  if (tab === "LIVE") return "No live webinars found.";
  if (tab === "COMPLETED") return "No completed webinars found.";
  return "No cancelled webinars found.";
}

function normalizeWebinarStatusQuery(value: string | null | undefined): CarePilotWebinarStatus | "" {
  const raw = (value || "").trim().toLowerCase();
  if (raw === "draft") return "DRAFT";
  if (raw === "scheduled" || raw === "upcoming") return "SCHEDULED";
  if (raw === "live") return "LIVE";
  if (raw === "completed") return "COMPLETED";
  if (raw === "cancelled" || raw === "canceled") return "CANCELLED";
  return "";
}

function normalizeWebinarTabQuery(value: string | null | undefined): WebinarTab {
  const raw = (value || "").trim().toLowerCase();
  if (raw === "draft" || raw === "drafts") return "DRAFTS";
  if (raw === "upcoming" || raw === "scheduled") return "UPCOMING";
  if (raw === "live") return "LIVE";
  if (raw === "completed") return "COMPLETED";
  if (raw === "cancelled" || raw === "canceled") return "CANCELLED";
  return "UPCOMING";
}

function statusToTab(status: CarePilotWebinarStatus): WebinarTab {
  if (status === "DRAFT") return "DRAFTS";
  if (status === "SCHEDULED") return "UPCOMING";
  if (status === "LIVE") return "LIVE";
  if (status === "COMPLETED") return "COMPLETED";
  return "CANCELLED";
}

function tabToStatus(tab: WebinarTab): CarePilotWebinarStatus | "" {
  if (tab === "DRAFTS") return "DRAFT";
  if (tab === "UPCOMING") return "SCHEDULED";
  if (tab === "LIVE") return "LIVE";
  if (tab === "COMPLETED") return "COMPLETED";
  return "CANCELLED";
}

function tabQueryValue(tab: WebinarTab) {
  if (tab === "DRAFTS") return "drafts";
  if (tab === "UPCOMING") return "upcoming";
  if (tab === "LIVE") return "live";
  if (tab === "COMPLETED") return "completed";
  return "cancelled";
}

function statusQueryValue(status: CarePilotWebinarStatus | "") {
  if (status === "DRAFT") return "draft";
  if (status === "SCHEDULED") return "scheduled";
  if (status === "LIVE") return "live";
  if (status === "COMPLETED") return "completed";
  if (status === "CANCELLED") return "cancelled";
  return "";
}

function statusColor(status: CarePilotWebinarStatus) {
  if (status === "SCHEDULED") return "info" as const;
  if (status === "LIVE") return "success" as const;
  if (status === "COMPLETED") return "default" as const;
  if (status === "CANCELLED") return "error" as const;
  return "warning" as const;
}

export default function WebinarsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [pageError, setPageError] = React.useState<string | null>(null);
  const [pageAccessDenied, setPageAccessDenied] = React.useState(false);

  const [tab, setTab] = React.useState<WebinarTab>(() => {
    const explicitStatus = normalizeWebinarStatusQuery(searchParams.get("status"));
    if (explicitStatus) {
      return statusToTab(explicitStatus);
    }
    return normalizeWebinarTabQuery(searchParams.get("tab"));
  });
  const [statusFilter, setStatusFilter] = React.useState<CarePilotWebinarStatus | "">(normalizeWebinarStatusQuery(searchParams.get("status")));
  const [typeFilter, setTypeFilter] = React.useState<CarePilotWebinarType | "">("");

  const [allRows, setAllRows] = React.useState<CarePilotWebinar[]>([]);
  const [campaigns, setCampaigns] = React.useState<CarePilotCampaign[]>([]);
  const [analytics, setAnalytics] = React.useState<CarePilotWebinarAnalyticsSummary | null>(null);

  const [editorOpen, setEditorOpen] = React.useState(false);
  const [editing, setEditing] = React.useState<CarePilotWebinar | null>(null);
  const [form, setForm] = React.useState({
    title: "",
    description: "",
    webinarType: "HEALTH_AWARENESS" as CarePilotWebinarType,
    campaignId: "",
    webinarUrl: "",
    organizerName: "",
    organizerEmail: "",
    scheduledStartAt: "",
    scheduledEndAt: "",
    timezone: "UTC",
    capacity: "",
    registrationEnabled: true,
    reminderEnabled: true,
    followupEnabled: true,
    tags: "",
  });
  const [formErrors, setFormErrors] = React.useState<Record<string, string>>({});
  const [formValidated, setFormValidated] = React.useState(false);

  const [regOpen, setRegOpen] = React.useState(false);
  const [regWebinar, setRegWebinar] = React.useState<CarePilotWebinar | null>(null);
  const [registrations, setRegistrations] = React.useState<CarePilotWebinarRegistration[]>([]);
  const [regForm, setRegForm] = React.useState({ attendeeName: "", attendeeEmail: "", attendeePhone: "", patientId: "", leadId: "" });
  const [regFormErrors, setRegFormErrors] = React.useState<Record<string, string>>({});
  const [regLoading, setRegLoading] = React.useState(false);
  const [regSaving, setRegSaving] = React.useState(false);
  const [regError, setRegError] = React.useState<string | null>(null);

  const canView = auth.hasPermission(ENGAGE_WEBINAR_VIEW)
    || auth.hasPermission(ENGAGE_WEBINAR_VIEW_ANALYTICS)
    || auth.hasPermission(ENGAGE_WEBINAR_VIEW_AUDIT);
  const canViewAnalytics = auth.hasPermission(ENGAGE_WEBINAR_VIEW_ANALYTICS);
  const canViewCampaigns = auth.hasPermission("engage.campaign.view")
    || auth.hasPermission("engage.campaign.manage")
    || auth.hasPermission("engage.audit.view");
  const canCreate = auth.hasPermission(ENGAGE_WEBINAR_CREATE);
  const canEdit = auth.hasPermission(ENGAGE_WEBINAR_EDIT);
  const canPublish = auth.hasPermission(ENGAGE_WEBINAR_PUBLISH);
  const canCancel = auth.hasPermission(ENGAGE_WEBINAR_CANCEL);
  const canManageRegistrations = auth.hasPermission(ENGAGE_WEBINAR_MANAGE_REGISTRATIONS);
  const canRecordAttendance = auth.hasPermission(ENGAGE_WEBINAR_RECORD_ATTENDANCE);
  const canRunAutomation = auth.hasPermission(ENGAGE_WEBINAR_RUN_AUTOMATION);
  const canMutate = canCreate || canEdit || canPublish || canCancel || canManageRegistrations || canRecordAttendance || canRunAutomation;

  React.useEffect(() => {
    const nextStatus = normalizeWebinarStatusQuery(searchParams.get("status"));
    const nextTab = nextStatus ? statusToTab(nextStatus) : normalizeWebinarTabQuery(searchParams.get("tab"));
    setStatusFilter((current) => (current === nextStatus ? current : nextStatus));
    setTab((current) => (current === nextTab ? current : nextTab));
  }, [searchParams]);

  React.useEffect(() => {
    if (searchParams.has("tab") || searchParams.has("status")) {
      return;
    }
    if (loading) {
      return;
    }
    const hasDrafts = allRows.some((row) => row.status === "DRAFT");
    const hasScheduled = allRows.some((row) => row.status === "SCHEDULED");
    if (hasDrafts && !hasScheduled && tab !== "DRAFTS") {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set("tab", tabQueryValue("DRAFTS"));
      nextParams.set("status", statusQueryValue("DRAFT"));
      setTab("DRAFTS");
      setStatusFilter("DRAFT");
      setSearchParams(nextParams, { replace: true });
    }
  }, [allRows, loading, searchParams, setSearchParams, tab]);

  const setTabAndUrl = React.useCallback((nextTab: WebinarTab) => {
    const nextStatus = tabToStatus(nextTab);
    setTab(nextTab);
    setStatusFilter(nextStatus);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", tabQueryValue(nextTab));
    if (nextStatus) {
      nextParams.set("status", statusQueryValue(nextStatus));
    } else {
      nextParams.delete("status");
    }
    setSearchParams(nextParams, { replace: false });
  }, [searchParams, setSearchParams]);

  const setStatusAndMaybeTab = React.useCallback((nextStatus: CarePilotWebinarStatus | "") => {
    setStatusFilter(nextStatus);
    const nextTab = nextStatus ? statusToTab(nextStatus) : tab;
    if (nextStatus) {
      setTab(nextTab);
    }
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", tabQueryValue(nextTab));
    if (nextStatus) {
      nextParams.set("status", statusQueryValue(nextStatus));
    } else {
      nextParams.delete("status");
    }
    setSearchParams(nextParams, { replace: false });
  }, [searchParams, setSearchParams, tab]);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canView) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setPageError(null);
    setPageAccessDenied(false);
    try {
      const webinars = await listCarePilotWebinars(auth.accessToken, auth.tenantId, {
        status: statusFilter || undefined,
        webinarType: typeFilter || undefined,
        size: 100,
      });
      setAllRows(webinars.rows);
      if (canViewAnalytics) {
        try {
          setAnalytics(await getCarePilotWebinarAnalyticsSummary(auth.accessToken, auth.tenantId));
        } catch {
          setAnalytics(null);
        }
      } else {
        setAnalytics(null);
      }
    } catch (err) {
      setAllRows([]);
      setAnalytics(null);
      if (err instanceof ApiClientError && err.status === 403) {
        setPageAccessDenied(true);
      } else {
        setPageError(err instanceof Error ? err.message : "Failed to load webinars");
      }
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canView, canViewAnalytics, statusFilter, typeFilter]);

  React.useEffect(() => { void load(); }, [load]);

  const visibleRows = React.useMemo(() => {
    let filtered = allRows;
    if (tab === "DRAFTS") filtered = filtered.filter((r) => r.status === "DRAFT");
    if (tab === "UPCOMING") filtered = filtered.filter((r) => r.status === "SCHEDULED");
    if (tab === "LIVE") filtered = filtered.filter((r) => r.status === "LIVE");
    if (tab === "CANCELLED") filtered = filtered.filter((r) => r.status === "CANCELLED");
    if (tab === "COMPLETED") filtered = filtered.filter((r) => r.status === "COMPLETED");
    return filtered;
  }, [allRows, tab]);

  const basicSummary = React.useMemo(() => {
    const counts = {
      total: allRows.length,
      upcoming: 0,
      live: 0,
      completed: 0,
      cancelled: 0,
    };
    for (const row of allRows) {
      if (row.status === "SCHEDULED") counts.upcoming += 1;
      else if (row.status === "LIVE") counts.live += 1;
      else if (row.status === "COMPLETED") counts.completed += 1;
      else if (row.status === "CANCELLED") counts.cancelled += 1;
    }
    return counts;
  }, [allRows]);

  const loadCampaigns = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !canViewCampaigns) return;
    try {
      const rows = await listCarePilotCampaigns(auth.accessToken, auth.tenantId);
      setCampaigns(rows);
    } catch {
      setCampaigns([]);
    }
  }, [auth.accessToken, auth.tenantId, canViewCampaigns]);

  const openEditor = (row?: CarePilotWebinar) => {
    void loadCampaigns();
    if (row) {
      setEditing(row);
      setForm({
        title: row.title,
        description: row.description || "",
        webinarType: row.webinarType,
        campaignId: row.campaignId || "",
        webinarUrl: row.webinarUrl || "",
        organizerName: row.organizerName || "",
        organizerEmail: row.organizerEmail || "",
        scheduledStartAt: formatCarePilotDateTimeInput(row.scheduledStartAt, row.timezone),
        scheduledEndAt: formatCarePilotDateTimeInput(row.scheduledEndAt, row.timezone),
        timezone: row.timezone,
        capacity: row.capacity == null ? "" : String(row.capacity),
        registrationEnabled: row.registrationEnabled,
        reminderEnabled: row.reminderEnabled,
        followupEnabled: row.followupEnabled,
        tags: row.tags || "",
      });
    } else {
      setEditing(null);
      setForm({
        title: "",
        description: "",
        webinarType: "HEALTH_AWARENESS",
        campaignId: "",
        webinarUrl: "",
        organizerName: "",
        organizerEmail: "",
        scheduledStartAt: "",
        scheduledEndAt: "",
        timezone: "UTC",
        capacity: "",
        registrationEnabled: true,
        reminderEnabled: true,
        followupEnabled: true,
        tags: "",
      });
    }
    setFormValidated(false);
    setFormErrors({});
    setEditorOpen(true);
  };

  React.useEffect(() => {
    if (!formValidated && !formErrors.scheduledStartAt && !formErrors.scheduledEndAt) {
      return;
    }
    const { scheduledStartAt, scheduledEndAt } = getWebinarDateFieldErrors(form);
    setFormErrors((current) => {
      const next = { ...current };
      if (scheduledStartAt) next.scheduledStartAt = scheduledStartAt;
      else delete next.scheduledStartAt;
      if (scheduledEndAt) next.scheduledEndAt = scheduledEndAt;
      else delete next.scheduledEndAt;
      return next;
    });
  }, [form.scheduledStartAt, form.scheduledEndAt, formValidated]);

  const saveWebinar = async () => {
    if (!auth.accessToken || !auth.tenantId || !canMutate) return;
    setSaving(true);
    try {
      setFormValidated(true);
      const draft = { ...form, status: editing?.status || "DRAFT" };
      const validationErrors = validateWebinarDraft(draft);
      if (Object.keys(validationErrors).length > 0) {
        setFormErrors(validationErrors);
        return;
      }
      const payload = buildWebinarPayload(draft);
      if (payload.scheduledStartAt == null || payload.scheduledEndAt == null) {
        setFormErrors({
          scheduledStartAt: validationErrors.scheduledStartAt || "Start date/time is required.",
          scheduledEndAt: validationErrors.scheduledEndAt || "End date/time is required and must be on or after start.",
        });
        return;
      }
      setFormErrors({});
      if (editing) await updateCarePilotWebinar(auth.accessToken, auth.tenantId, editing.id, payload);
      else await createCarePilotWebinar(auth.accessToken, auth.tenantId, payload);
      setEditorOpen(false);
      await load();
    } catch (err) {
      setPageError(err instanceof Error ? err.message : "Failed to save webinar");
    } finally {
      setSaving(false);
    }
  };

  const openRegistrations = async (row: CarePilotWebinar) => {
    if (!auth.accessToken || !auth.tenantId || !(canManageRegistrations || canRecordAttendance || canView)) return;
    setRegWebinar(row);
    setRegOpen(true);
    setRegistrations([]);
    setRegError(null);
    setRegLoading(true);
    try {
      const list = await listCarePilotWebinarRegistrations(auth.accessToken, auth.tenantId, row.id, 0, 100);
      setRegistrations(list.rows);
    } catch (err) {
      setRegError(err instanceof Error ? err.message : "Failed to load registrations");
    } finally {
      setRegLoading(false);
    }
  };

  const addRegistration = async () => {
    if (!auth.accessToken || !auth.tenantId || !regWebinar || !canManageRegistrations) return;
    setRegError(null);
    setRegSaving(true);
    try {
      const parsed = engageWebinarRegistrationSchema.safeParse(regForm);
      if (!parsed.success) {
        setRegFormErrors(mapZodErrors(parsed.error));
        return;
      }
      setRegFormErrors({});
      await registerCarePilotWebinarAttendee(auth.accessToken, auth.tenantId, regWebinar.id, {
        attendeeName: regForm.attendeeName,
        attendeeEmail: regForm.attendeeEmail || null,
        attendeePhone: regForm.attendeePhone ? (normalizeIndianMobileInput(regForm.attendeePhone) as string) : null,
        patientId: regForm.patientId || null,
        leadId: regForm.leadId || null,
      });
      const [list] = await Promise.all([
        listCarePilotWebinarRegistrations(auth.accessToken, auth.tenantId, regWebinar.id, 0, 100),
        load(),
      ]);
      setRegistrations(list.rows);
      setRegForm({ attendeeName: "", attendeeEmail: "", attendeePhone: "", patientId: "", leadId: "" });
    } catch (err) {
      setRegError(err instanceof Error ? err.message : "Failed to register attendee");
    } finally {
      setRegSaving(false);
    }
  };

  const markAttendance = async (registrationId: string, status: CarePilotWebinarRegistrationStatus) => {
    if (!auth.accessToken || !auth.tenantId || !regWebinar || !canRecordAttendance) return;
    setRegError(null);
    setRegSaving(true);
    try {
      await markCarePilotWebinarAttendance(auth.accessToken, auth.tenantId, regWebinar.id, { registrationId, registrationStatus: status });
      const [list] = await Promise.all([
        listCarePilotWebinarRegistrations(auth.accessToken, auth.tenantId, regWebinar.id, 0, 100),
        load(),
      ]);
      setRegistrations(list.rows);
    } catch (err) {
      setRegError(err instanceof Error ? err.message : "Failed to update attendance");
    } finally {
      setRegSaving(false);
    }
  };

  const quickStatus = async (row: CarePilotWebinar, status: CarePilotWebinarStatus) => {
    if (!auth.accessToken || !auth.tenantId) return;
    if (status === "CANCELLED" && !canCancel) return;
    if (status !== "CANCELLED" && !canPublish) return;
    await updateCarePilotWebinarStatus(auth.accessToken, auth.tenantId, row.id, status);
    await load();
  };

  const openLead = (row: CarePilotWebinarRegistration) => {
    if (!row.leadId) return;
    navigate(`/carepilot/leads?tab=converted&status=converted&leadId=${encodeURIComponent(row.leadId)}`);
  };

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to use Webinar Automation.</Alert>;
  if (!canView) return <Alert severity="error">You do not have access to Webinar Automation.</Alert>;
  if (pageAccessDenied) {
    return (
      <Box sx={{ p: 3, maxWidth: 760 }}>
        <Card variant="outlined" sx={{ borderRadius: 2 }}>
          <CardContent>
            <Stack spacing={1.5}>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>
                Access denied
              </Typography>
              <Typography variant="body2" color="text.secondary">
                You do not have access to Webinar Automation.
              </Typography>
              <Box>
                <Button variant="contained" onClick={() => navigate("/")}>
                  Open Tenant Home
                </Button>
              </Box>
            </Stack>
          </CardContent>
        </Card>
      </Box>
    );
  }
  if (!loading && pageError) {
    return (
      <Box sx={{ p: 3, maxWidth: 760 }}>
        <Card variant="outlined" sx={{ borderRadius: 2 }}>
          <CardContent>
            <Stack spacing={1.5}>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>
                Unable to load Webinar Automation
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {pageError}
              </Typography>
              <Box>
                <Button variant="contained" onClick={() => void load()}>
                  Retry
                </Button>
              </Box>
            </Stack>
          </CardContent>
        </Card>
      </Box>
    );
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Webinar Automation</Typography>
          <Typography variant="body2" color="text.secondary">Create events, track registrations/attendance, and run reminder/follow-up automation.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
          {canCreate ? <Button variant="contained" onClick={() => openEditor()}>New Webinar</Button> : null}
        </Stack>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Total</Typography><Typography variant="h5">{basicSummary.total}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Upcoming</Typography><Typography variant="h5">{basicSummary.upcoming}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Live</Typography><Typography variant="h5">{basicSummary.live}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Completed</Typography><Typography variant="h5">{basicSummary.completed}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 6, md: 2.4 }}><Card><CardContent><Typography variant="caption">Cancelled</Typography><Typography variant="h5">{basicSummary.cancelled}</Typography></CardContent></Card></Grid>
      </Grid>

      <Card><CardContent>
        <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
          <FormControl size="small" sx={{ minWidth: 180 }}><InputLabel>Status</InputLabel><Select value={statusFilter} label="Status" onChange={(e) => setStatusAndMaybeTab(e.target.value as CarePilotWebinarStatus | "")}>{WEBINAR_STATUS_OPTIONS.map((option) => <MenuItem key={option.value || "ALL"} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
          <FormControl size="small" sx={{ minWidth: 180 }}><InputLabel>Type</InputLabel><Select value={typeFilter} label="Type" onChange={(e) => setTypeFilter(e.target.value as CarePilotWebinarType | "")}><MenuItem value="">All</MenuItem>{WEBINAR_TYPES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl>
          <Button variant="outlined" onClick={() => { setStatusFilter(""); setTypeFilter(""); }}>Reset</Button>
        </Stack>
      </CardContent></Card>

      <Tabs value={tab} onChange={(_, next) => setTabAndUrl(next as WebinarTab)} variant="scrollable" allowScrollButtonsMobile>
        {WEBINAR_TAB_ORDER.map((item) => <Tab key={item} value={item} label={webinarTabLabel(item)} />)}
      </Tabs>

      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card><CardContent>
          {visibleRows.length === 0 ? <Alert severity="info">{webinarTabEmptyState(tab)}</Alert> : (
            <Table size="small"><TableHead><TableRow><TableCell>Webinar</TableCell><TableCell>Type</TableCell><TableCell>Schedule</TableCell><TableCell>Capacity</TableCell><TableCell>Status</TableCell><TableCell>Reminder</TableCell><TableCell>URL</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>
              {visibleRows.map((row) => <TableRow key={row.id}><TableCell><Stack spacing={0.25}><Typography variant="body2" sx={{ fontWeight: 700 }}>{row.title}</Typography><Typography variant="caption" color="text.secondary">{row.organizerName || "-"}</Typography><Typography variant="caption" color="text.secondary">{row.campaignName ? `Campaign: ${row.campaignName}` : "Campaign: None"}</Typography></Stack></TableCell><TableCell>{webinarTypeLabel(row.webinarType)}</TableCell><TableCell><Stack spacing={0.25}><Typography variant="body2">{formatCarePilotDateTime(row.scheduledStartAt, row.timezone)}</Typography><Typography variant="caption" color="text.secondary">{formatCarePilotDateTime(row.scheduledEndAt, row.timezone)}</Typography></Stack></TableCell><TableCell>{row.capacity == null ? "Unlimited" : row.capacity}</TableCell><TableCell><Chip size="small" label={webinarStatusLabel(row.status)} color={statusColor(row.status)} /></TableCell><TableCell>{row.reminderEnabled ? <Chip size="small" variant="outlined" label="Enabled" /> : <Chip size="small" label="Off" />}</TableCell><TableCell>{row.webinarUrl ? <Button size="small" href={row.webinarUrl} target="_blank" rel="noreferrer">Open</Button> : "-"}</TableCell><TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end"><Button size="small" onClick={() => void openRegistrations(row)}>Registrations</Button>{canEdit ? <Button size="small" onClick={() => openEditor(row)}>Edit</Button> : null}{canPublish && row.status === "DRAFT" ? <Button size="small" onClick={() => void quickStatus(row, "SCHEDULED")}>Publish</Button> : null}{canPublish && row.status === "SCHEDULED" ? <Button size="small" onClick={() => void quickStatus(row, "LIVE")}>Start</Button> : null}{canPublish && row.status === "LIVE" ? <Button size="small" onClick={() => void quickStatus(row, "COMPLETED")}>Complete</Button> : null}{canCancel && row.status !== "CANCELLED" ? <Button size="small" color="error" onClick={() => void quickStatus(row, "CANCELLED")}>Cancel</Button> : null}</Stack></TableCell></TableRow>)}
            </TableBody></Table>
          )}
        </CardContent></Card>
      ) : null}

      <Dialog open={editorOpen} onClose={() => setEditorOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editing ? "Edit Webinar" : "Create Webinar"}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required inputProps={{ maxLength: 60 }} label="Title *" value={form.title} onChange={(e) => setForm((v) => ({ ...v, title: e.target.value }))} error={Boolean(formErrors.title)} helperText={formErrors.title || "Title must be 60 characters or fewer."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><FormControl fullWidth><InputLabel>Type *</InputLabel><Select value={form.webinarType} label="Type *" onChange={(e) => setForm((v) => ({ ...v, webinarType: e.target.value as CarePilotWebinarType }))}>{WEBINAR_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 6 }}><FormControl fullWidth><InputLabel>Campaign</InputLabel><Select value={form.campaignId} label="Campaign" onChange={(e) => setForm((v) => ({ ...v, campaignId: e.target.value }))}><MenuItem value="">None</MenuItem>{campaigns.map((campaign) => <MenuItem key={campaign.id} value={campaign.id}>{campaign.name}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12 }}><TextField fullWidth multiline minRows={2} inputProps={{ maxLength: 250 }} label="Description" value={form.description} onChange={(e) => setForm((v) => ({ ...v, description: e.target.value }))} error={Boolean(formErrors.description)} helperText={formErrors.description || "Description must be 250 characters or fewer."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required type="datetime-local" label="Start *" value={form.scheduledStartAt} onChange={(e) => setForm((v) => ({ ...v, scheduledStartAt: e.target.value }))} InputLabelProps={{ shrink: true }} error={Boolean(formErrors.scheduledStartAt)} helperText={formErrors.scheduledStartAt || "Start date/time is required."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required type="datetime-local" label="End *" value={form.scheduledEndAt} onChange={(e) => setForm((v) => ({ ...v, scheduledEndAt: e.target.value }))} InputLabelProps={{ shrink: true }} error={Boolean(formErrors.scheduledEndAt)} helperText={formErrors.scheduledEndAt || "End date/time is required and must be on or after start."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth inputProps={{ maxLength: 250 }} label="Webinar URL" value={form.webinarUrl} onChange={(e) => setForm((v) => ({ ...v, webinarUrl: e.target.value }))} error={Boolean(formErrors.webinarUrl)} helperText={formErrors.webinarUrl || "Optional webinar URL."} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth inputProps={{ maxLength: 60 }} label="Organizer" value={form.organizerName} onChange={(e) => setForm((v) => ({ ...v, organizerName: e.target.value }))} error={Boolean(formErrors.organizerName)} helperText={formErrors.organizerName || "Optional organizer name."} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Organizer Email" value={form.organizerEmail} onChange={(e) => setForm((v) => ({ ...v, organizerEmail: e.target.value }))} error={Boolean(formErrors.organizerEmail)} helperText={formErrors.organizerEmail || "Enter a valid email address if provided."} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth required inputProps={{ maxLength: 60 }} label="Timezone *" value={form.timezone} onChange={(e) => setForm((v) => ({ ...v, timezone: e.target.value }))} error={Boolean(formErrors.timezone)} helperText={formErrors.timezone || "Timezone is required."} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="number" label="Capacity" value={form.capacity} onChange={(e) => setForm((v) => ({ ...v, capacity: e.target.value }))} inputProps={{ min: 0 }} error={Boolean(formErrors.capacity)} helperText={formErrors.capacity || "Capacity must be zero or greater."} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth inputProps={{ maxLength: 250 }} label="Tags" value={form.tags} onChange={(e) => setForm((v) => ({ ...v, tags: e.target.value }))} error={Boolean(formErrors.tags)} helperText={formErrors.tags || "Optional tags, comma separated."} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions><Button onClick={() => setEditorOpen(false)}>Close</Button><Button variant="contained" onClick={() => void saveWebinar()} disabled={saving}>{saving ? "Saving..." : "Save"}</Button></DialogActions>
      </Dialog>

      <Dialog open={regOpen} onClose={() => setRegOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>Registrations{regWebinar ? ` · ${regWebinar.title}` : ""}</DialogTitle>
        <DialogContent>
          {regError ? <Alert severity="error" sx={{ mb: 2 }}>{regError}</Alert> : null}
          {canManageRegistrations ? (
            <Grid container spacing={1.5} sx={{ mb: 2, mt: 0.5 }}>
              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" required label="Attendee *" value={regForm.attendeeName} onChange={(e) => setRegForm((v) => ({ ...v, attendeeName: e.target.value }))} error={Boolean(regFormErrors.attendeeName)} helperText={regFormErrors.attendeeName || "Attendee name is required."} /></Grid>
              <Grid size={{ xs: 12, md: 2.5 }}><TextField fullWidth size="small" label="Email" value={regForm.attendeeEmail} onChange={(e) => setRegForm((v) => ({ ...v, attendeeEmail: e.target.value }))} error={Boolean(regFormErrors.attendeeEmail)} helperText={regFormErrors.attendeeEmail || "Enter a valid email address if provided."} /></Grid>
              <Grid size={{ xs: 12, md: 2.5 }}><TextField fullWidth size="small" label="Phone" value={regForm.attendeePhone} onChange={(e) => setRegForm((v) => ({ ...v, attendeePhone: e.target.value }))} inputProps={{ inputMode: "tel" }} error={Boolean(regFormErrors.attendeePhone)} helperText={regFormErrors.attendeePhone || "Enter a valid Indian mobile number if provided."} /></Grid>
              <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Patient Id" value={regForm.patientId} onChange={(e) => setRegForm((v) => ({ ...v, patientId: e.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Lead Id" value={regForm.leadId} onChange={(e) => setRegForm((v) => ({ ...v, leadId: e.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 12 }}><Button variant="contained" onClick={() => void addRegistration()} disabled={regSaving}>{regSaving ? "Registering..." : "Register Attendee"}</Button></Grid>
            </Grid>
          ) : null}
          {regLoading ? <Box sx={{ minHeight: 160, display: "grid", placeItems: "center" }}><CircularProgress size={28} /></Box> : null}
          {!regLoading && registrations.length === 0 ? <Alert severity="info">No registrations yet.</Alert> : null}
          {!regLoading && registrations.length > 0 ? (
            <Table size="small"><TableHead><TableRow><TableCell>Attendee</TableCell><TableCell>Email</TableCell><TableCell>Phone</TableCell><TableCell>Status</TableCell><TableCell>Patient</TableCell><TableCell>Lead</TableCell><TableCell>Campaign</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>
              {registrations.map((row) => <TableRow key={row.id}><TableCell>{row.attendeeName}</TableCell><TableCell>{row.attendeeEmail || "-"}</TableCell><TableCell>{row.attendeePhone || "-"}</TableCell><TableCell><Chip size="small" label={row.registrationStatus} /></TableCell><TableCell>{row.patientId ? "Patient record" : "-"}</TableCell><TableCell>{row.leadId ? <Stack direction="row" spacing={1} alignItems="center"><Typography variant="body2">{row.leadName || "Linked lead"}</Typography><Button size="small" onClick={() => openLead(row)}>Open Lead</Button></Stack> : "-"}</TableCell><TableCell>{row.campaignName || "-"}</TableCell><TableCell align="right">{canRecordAttendance ? <Stack direction="row" spacing={1} justifyContent="flex-end"><Button size="small" onClick={() => void markAttendance(row.id, "ATTENDED")} disabled={regSaving}>Attended</Button><Button size="small" onClick={() => void markAttendance(row.id, "NO_SHOW")} disabled={regSaving}>No-show</Button><Button size="small" color="error" onClick={() => void markAttendance(row.id, "CANCELLED")} disabled={regSaving}>Cancel</Button></Stack> : "-"}</TableCell></TableRow>)}
            </TableBody></Table>
          ) : null}
        </DialogContent>
      </Dialog>
    </Stack>
  );
}
