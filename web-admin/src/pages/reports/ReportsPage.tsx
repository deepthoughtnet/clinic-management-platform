import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
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
import DownloadRoundedIcon from "@mui/icons-material/DownloadRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";

import { firstZodError, reportFilterSchema } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, compactChipSx } from "../../components/compact/CompactUi";
import {
  getCashierShiftsReport,
  getDailySalesReport,
  getDoctorConsultationsReport,
  getFollowUpsReport,
  getLabOperationsReport,
  getLowStockReport,
  getMedicineSalesReport,
  getPatientVisitsReport,
  getPaymentModesReport,
  getPendingDuesReport,
  getPrescriptionsReport,
  getRevenueReport,
  getVaccinationsDueReport,
  getClinicUsers,
  searchPatients,
  type ClinicUser,
  type Patient,
  type ReportRow,
} from "../../api/clinicApi";

type ReportKey =
  | "patient-visits"
  | "doctor-consultations"
  | "revenue"
  | "daily-sales"
  | "medicine-sales"
  | "payment-modes"
  | "cashier-shifts"
  | "pending-dues"
  | "vaccinations-due"
  | "follow-ups"
  | "low-stock"
  | "lab-operations"
  | "prescriptions";

const REPORTS: Array<{ key: ReportKey; label: string }> = [
  { key: "patient-visits", label: "Patient visits" },
  { key: "doctor-consultations", label: "Doctor consultations" },
  { key: "revenue", label: "Revenue" },
  { key: "daily-sales", label: "Daily sales" },
  { key: "medicine-sales", label: "Medicine sales" },
  { key: "payment-modes", label: "Payment modes" },
  { key: "cashier-shifts", label: "Cashier shifts" },
  { key: "pending-dues", label: "Pending dues" },
  { key: "vaccinations-due", label: "Vaccinations due" },
  { key: "follow-ups", label: "Follow-ups" },
  { key: "low-stock", label: "Low stock" },
  { key: "lab-operations", label: "Lab operations" },
  { key: "prescriptions", label: "Prescriptions" },
];

function toCsv(rows: ReportRow[]) {
  if (rows.length === 0) {
    return "";
  }
  const headers = Object.keys(rows[0]);
  const escape = (value: unknown) => {
    const text = value === null || value === undefined ? "" : String(value);
    return `"${text.replace(/"/g, '""')}"`;
  };
  return [headers.map(escape).join(","), ...rows.map((row) => headers.map((header) => escape(row[header])).join(","))].join("\n");
}

function downloadCsv(filename: string, rows: ReportRow[]) {
  const blob = new Blob([toCsv(rows)], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 60000);
}

function humanizeHeader(header: string) {
  return header
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/_/g, " ")
    .replace(/\bupi\b/gi, "UPI")
    .replace(/\bpo\b/gi, "PO")
    .replace(/\bgrn\b/gi, "GRN")
    .replace(/\bid\b/gi, "ID")
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isUuidLike(value: unknown) {
  return typeof value === "string" && UUID_RE.test(value.trim());
}

function formatReportValue(header: string, value: unknown) {
  if (value === null || value === undefined || value === "") {
    return "—";
  }
  if (isUuidLike(value)) {
    if (/cashier|doctor|patient|reference/i.test(header)) {
      return "Mapped in source record";
    }
    return "—";
  }
  return String(value);
}

function mapReportError(err: unknown) {
  const message = err instanceof Error ? err.message : "";
  if (!message) return "Report data could not be loaded. Try refreshing the filters.";
  if (/internal server error/i.test(message)) {
    return "Report data could not be loaded right now. Please refresh and try again.";
  }
  return message;
}

export default function ReportsPage() {
  const auth = useAuth();
  const [reportKey, setReportKey] = React.useState<ReportKey>("patient-visits");
  const [rows, setRows] = React.useState<ReportRow[]>([]);
  const [doctors, setDoctors] = React.useState<ClinicUser[]>([]);
  const [patients, setPatients] = React.useState<Patient[]>([]);
  const [loadingMeta, setLoadingMeta] = React.useState(true);
  const [loadingReport, setLoadingReport] = React.useState(true);
  const [exporting, setExporting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [filters, setFilters] = React.useState({
    from: new Date(new Date().setDate(new Date().getDate() - 30)).toISOString().slice(0, 10),
    to: new Date().toISOString().slice(0, 10),
    doctorUserId: "",
    patientId: "",
    status: "",
    paymentMode: "",
    source: "ALL",
  });
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const canViewFinanceReports =
    tenantRole === "CLINIC_ADMIN" ||
    tenantRole === "BILLING_USER" ||
    tenantRole === "AUDITOR" ||
    (!!auth.tenantId && auth.rolesUpper.includes("PLATFORM_ADMIN"));

  const showDoctorFilter = reportKey === "patient-visits" || reportKey === "doctor-consultations" || reportKey === "prescriptions";
  const showPatientFilter = reportKey === "patient-visits" || reportKey === "doctor-consultations" || reportKey === "revenue" || reportKey === "prescriptions";
  const showStatusFilter = reportKey === "doctor-consultations";
  const showPaymentModeFilter = reportKey === "revenue" || reportKey === "daily-sales" || reportKey === "medicine-sales" || reportKey === "payment-modes";
  const showSourceFilter = reportKey === "revenue" || reportKey === "daily-sales" || reportKey === "payment-modes";

  const loadMeta = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setLoadingMeta(true);
    try {
      const [doctorRows, patientRows] = await Promise.all([
        getClinicUsers(auth.accessToken, auth.tenantId),
        searchPatients(auth.accessToken, auth.tenantId, { active: null }),
      ]);
      setDoctors(doctorRows);
      setPatients(patientRows);
    } finally {
      setLoadingMeta(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  const loadReport = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const parsed = reportFilterSchema.safeParse({
      ...filters,
      from: filters.from || undefined,
      to: filters.to || undefined,
      doctorUserId: filters.doctorUserId || undefined,
      patientId: filters.patientId || undefined,
      status: filters.status || undefined,
      paymentMode: filters.paymentMode || undefined,
      source: filters.source || undefined,
    });
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      setLoadingReport(false);
      return;
    }
    setLoadingReport(true);
    setError(null);
    try {
      const params = {
        from: parsed.data.from || null,
        to: parsed.data.to || null,
        doctorUserId: parsed.data.doctorUserId || null,
        patientId: parsed.data.patientId || null,
        status: parsed.data.status || null,
        paymentMode: parsed.data.paymentMode || null,
        source: parsed.data.source || null,
      };
      let value: ReportRow[] = [];
      switch (reportKey) {
        case "patient-visits":
          value = await getPatientVisitsReport(auth.accessToken, auth.tenantId, params);
          break;
        case "doctor-consultations":
          value = await getDoctorConsultationsReport(auth.accessToken, auth.tenantId, params);
          break;
        case "revenue":
          value = await getRevenueReport(auth.accessToken, auth.tenantId, params);
          break;
        case "daily-sales":
          value = await getDailySalesReport(auth.accessToken, auth.tenantId, params);
          break;
        case "medicine-sales":
          value = await getMedicineSalesReport(auth.accessToken, auth.tenantId, params);
          break;
        case "payment-modes":
          value = await getPaymentModesReport(auth.accessToken, auth.tenantId, params);
          break;
        case "cashier-shifts":
          value = await getCashierShiftsReport(auth.accessToken, auth.tenantId, params);
          break;
        case "pending-dues":
          value = await getPendingDuesReport(auth.accessToken, auth.tenantId);
          break;
        case "vaccinations-due":
          value = await getVaccinationsDueReport(auth.accessToken, auth.tenantId);
          break;
        case "follow-ups":
          value = await getFollowUpsReport(auth.accessToken, auth.tenantId);
          break;
        case "low-stock":
          value = await getLowStockReport(auth.accessToken, auth.tenantId);
          break;
        case "lab-operations":
          value = await getLabOperationsReport(auth.accessToken, auth.tenantId, { from: filters.from || null, to: filters.to || null });
          break;
        case "prescriptions":
          value = await getPrescriptionsReport(auth.accessToken, auth.tenantId, params);
          break;
      }
      setRows(value);
    } catch (err) {
      setError(mapReportError(err));
    } finally {
      setLoadingReport(false);
    }
  }, [auth.accessToken, auth.tenantId, filters, reportKey]);

  const exportRows = React.useCallback(() => {
    setExporting(true);
    try {
      downloadCsv(`${reportKey}.csv`, rows);
    } finally {
      window.setTimeout(() => setExporting(false), 250);
    }
  }, [reportKey, rows]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrapMeta() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoadingMeta(false);
        return;
      }
      try {
        await loadMeta();
      } catch {
        if (!cancelled) {
          setError("Filter options could not be loaded. You can still run reports without those filters.");
        }
      }
      if (cancelled) {
        return;
      }
    }
    void bootstrapMeta();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, loadMeta]);

  React.useEffect(() => {
    void loadReport();
  }, [loadReport]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  if (!canViewFinanceReports) {
    return <Alert severity="error">Finance reports are available to billing users, auditors, and clinic admins only.</Alert>;
  }

  const headers = rows.length > 0 ? Object.keys(rows[0]) : [];

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Reports
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Operational, financial, and clinical reporting with CSV export.
          </Typography>
        </Box>
        <Chip label="Tenant-scoped" variant="outlined" size="small" sx={compactChipSx} />
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          <Tabs value={reportKey} onChange={(_, next) => setReportKey(next)} variant="scrollable" scrollButtons="auto">
            {REPORTS.map((report) => (
              <Tab key={report.key} value={report.key} label={report.label} />
            ))}
          </Tabs>
        </CardContent>
      </Card>

      <CompactFilterCard
        title="Filters"
        subtitle={loadingMeta ? "Loading doctors and patients for filter options…" : "Use compact filters to match the table and CSV export."}
        actions={
          <Box sx={{ display: "flex", gap: 1, justifyContent: "flex-end", flexWrap: "wrap" }}>
            <Button
              size="small"
              onClick={() => setFilters({ from: new Date(new Date().setDate(new Date().getDate() - 30)).toISOString().slice(0, 10), to: new Date().toISOString().slice(0, 10), doctorUserId: "", patientId: "", status: "", paymentMode: "", source: "ALL" })}
            >
              Reset
            </Button>
            <Button size="small" variant="contained" startIcon={<RefreshRoundedIcon />} onClick={() => void loadReport()} disabled={loadingReport}>
              {loadingReport ? "Refreshing..." : "Refresh"}
            </Button>
            <Button size="small" variant="outlined" startIcon={<DownloadRoundedIcon />} disabled={rows.length === 0 || exporting} onClick={exportRows}>
              {exporting ? "Preparing CSV..." : "Export CSV"}
            </Button>
          </Box>
        }
      >
        <Grid container spacing={1.25}>
          <Grid size={{ xs: 12, md: 3 }}>
            <TextField size="small" fullWidth label="From" type="date" value={filters.from} onChange={(e) => setFilters((current) => ({ ...current, from: e.target.value }))} InputLabelProps={{ shrink: true }} />
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}>
            <TextField size="small" fullWidth label="To" type="date" value={filters.to} onChange={(e) => setFilters((current) => ({ ...current, to: e.target.value }))} InputLabelProps={{ shrink: true }} />
          </Grid>
              {showDoctorFilter ? (
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="report-doctor-label">Doctor</InputLabel>
                    <Select
                      labelId="report-doctor-label"
                      label="Doctor"
                      value={filters.doctorUserId}
                      disabled={loadingMeta}
                      onChange={(e) => setFilters((current) => ({ ...current, doctorUserId: String(e.target.value) }))}
                    >
                      <MenuItem value="">All</MenuItem>
                      {doctors
                        .filter((doctor) => (doctor.membershipRole || "").toUpperCase() === "DOCTOR")
                        .map((doctor) => (
                          <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                            {doctor.displayName || doctor.email || "Doctor record"}
                          </MenuItem>
                        ))}
                    </Select>
                  </FormControl>
                </Grid>
              ) : null}
              {showPatientFilter ? (
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="report-patient-label">Patient</InputLabel>
                    <Select
                      labelId="report-patient-label"
                      label="Patient"
                      value={filters.patientId}
                      disabled={loadingMeta}
                      onChange={(e) => setFilters((current) => ({ ...current, patientId: String(e.target.value) }))}
                    >
                      <MenuItem value="">All</MenuItem>
                      {patients.map((patient) => (
                        <MenuItem key={patient.id} value={patient.id}>
                          {patient.firstName} {patient.lastName} • {patient.patientNumber}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
              ) : null}
              {showStatusFilter ? (
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField size="small" fullWidth label="Status" value={filters.status} onChange={(e) => setFilters((current) => ({ ...current, status: e.target.value }))} helperText="Used for consultation and status-based reports" />
                </Grid>
              ) : null}
              {showPaymentModeFilter ? (
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="report-payment-mode-label">Payment mode</InputLabel>
                    <Select
                      labelId="report-payment-mode-label"
                      label="Payment mode"
                      value={filters.paymentMode}
                      onChange={(e) => setFilters((current) => ({ ...current, paymentMode: String(e.target.value) }))}
                    >
                      <MenuItem value="">All</MenuItem>
                      <MenuItem value="CASH">Cash</MenuItem>
                      <MenuItem value="UPI">UPI</MenuItem>
                      <MenuItem value="CARD">Card</MenuItem>
                      <MenuItem value="OTHER">Other</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
              ) : null}
              {showSourceFilter ? (
                <Grid size={{ xs: 12, md: 3 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="report-source-label">Source</InputLabel>
                    <Select
                      labelId="report-source-label"
                      label="Source"
                      value={filters.source}
                      onChange={(e) => setFilters((current) => ({ ...current, source: String(e.target.value) }))}
                    >
                      <MenuItem value="ALL">All</MenuItem>
                      <MenuItem value="CLINIC">Clinic</MenuItem>
                      <MenuItem value="PHARMACY">Pharmacy</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
              ) : null}
        </Grid>
      </CompactFilterCard>

      <Card>
        <CardContent>
          {loadingReport ? (
            <Box sx={{ display: "grid", placeItems: "center", minHeight: 240 }}>
              <Stack spacing={1} alignItems="center">
                <CircularProgress />
                <Typography variant="body2" color="text.secondary">
                  Loading report data...
                </Typography>
              </Stack>
            </Box>
          ) : rows.length === 0 ? (
            <CompactEmptyState
              title="No report rows found"
              subtitle="Try widening the date range, clearing one or more filters, or switching to a different report tab."
            />
          ) : (
            <Box sx={{ overflowX: "auto", overflowY: "auto", maxHeight: 560 }}>
              <Table stickyHeader size="small" sx={{ minWidth: 960 }}>
                <TableHead>
                  <TableRow>
                    {headers.map((header) => (
                      <TableCell key={header} sx={{ whiteSpace: "nowrap" }}>{humanizeHeader(header)}</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row, index) => (
                    <TableRow key={index} hover>
                      {headers.map((header) => (
                        <TableCell key={header}>{formatReportValue(header, row[header])}</TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
