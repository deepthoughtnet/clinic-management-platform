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

import { useAuth } from "../../auth/useAuth";
import {
  getDoctorConsultationsReport,
  getFollowUpsReport,
  getLowStockReport,
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
  | "payment-modes"
  | "pending-dues"
  | "vaccinations-due"
  | "follow-ups"
  | "low-stock"
  | "prescriptions";

const REPORTS: Array<{ key: ReportKey; label: string }> = [
  { key: "patient-visits", label: "Patient visits" },
  { key: "doctor-consultations", label: "Doctor consultations" },
  { key: "revenue", label: "Revenue" },
  { key: "payment-modes", label: "Payment modes" },
  { key: "pending-dues", label: "Pending dues" },
  { key: "vaccinations-due", label: "Vaccinations due" },
  { key: "follow-ups", label: "Follow-ups" },
  { key: "low-stock", label: "Low stock" },
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

export default function ReportsPage() {
  const auth = useAuth();
  const [reportKey, setReportKey] = React.useState<ReportKey>("patient-visits");
  const [rows, setRows] = React.useState<ReportRow[]>([]);
  const [doctors, setDoctors] = React.useState<ClinicUser[]>([]);
  const [patients, setPatients] = React.useState<Patient[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [filters, setFilters] = React.useState({
    from: new Date(new Date().setDate(new Date().getDate() - 30)).toISOString().slice(0, 10),
    to: new Date().toISOString().slice(0, 10),
    doctorUserId: "",
    patientId: "",
    status: "",
  });

  const loadMeta = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const [doctorRows, patientRows] = await Promise.all([
      getClinicUsers(auth.accessToken, auth.tenantId),
      searchPatients(auth.accessToken, auth.tenantId, { active: null }),
    ]);
    setDoctors(doctorRows);
    setPatients(patientRows);
  }, [auth.accessToken, auth.tenantId]);

  const loadReport = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const params = {
        from: filters.from || null,
        to: filters.to || null,
        doctorUserId: filters.doctorUserId || null,
        patientId: filters.patientId || null,
        status: filters.status || null,
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
        case "payment-modes":
          value = await getPaymentModesReport(auth.accessToken, auth.tenantId, params);
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
        case "prescriptions":
          value = await getPrescriptionsReport(auth.accessToken, auth.tenantId, params);
          break;
      }
      setRows(value);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load report");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, filters, reportKey]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }
      try {
        await loadMeta();
      } catch {
        if (!cancelled) {
          setError("Failed to load report metadata");
        }
      }
      if (!cancelled) {
        await loadReport();
      }
    }
    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, loadMeta, loadReport]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
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
        <Chip label="Tenant-scoped" variant="outlined" />
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

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Filters
            </Typography>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth label="From" type="date" value={filters.from} onChange={(e) => setFilters((current) => ({ ...current, from: e.target.value }))} InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth label="To" type="date" value={filters.to} onChange={(e) => setFilters((current) => ({ ...current, to: e.target.value }))} InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <FormControl fullWidth>
                  <InputLabel id="report-doctor-label">Doctor</InputLabel>
                  <Select
                    labelId="report-doctor-label"
                    label="Doctor"
                    value={filters.doctorUserId}
                    onChange={(e) => setFilters((current) => ({ ...current, doctorUserId: String(e.target.value) }))}
                  >
                    <MenuItem value="">All</MenuItem>
                    {doctors
                      .filter((doctor) => (doctor.membershipRole || "").toUpperCase() === "DOCTOR")
                      .map((doctor) => (
                        <MenuItem key={doctor.appUserId} value={doctor.appUserId}>
                          {doctor.displayName || doctor.email || doctor.appUserId}
                        </MenuItem>
                      ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <FormControl fullWidth>
                  <InputLabel id="report-patient-label">Patient</InputLabel>
                  <Select
                    labelId="report-patient-label"
                    label="Patient"
                    value={filters.patientId}
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
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth label="Status" value={filters.status} onChange={(e) => setFilters((current) => ({ ...current, status: e.target.value }))} helperText="Used for consultation and status-based reports" />
              </Grid>
            </Grid>
            <Box sx={{ display: "flex", gap: 1, justifyContent: "flex-end", flexWrap: "wrap" }}>
              <Button onClick={() => setFilters({ from: new Date(new Date().setDate(new Date().getDate() - 30)).toISOString().slice(0, 10), to: new Date().toISOString().slice(0, 10), doctorUserId: "", patientId: "", status: "" })}>
                Reset
              </Button>
              <Button variant="contained" onClick={() => void loadReport()}>
                Refresh
              </Button>
              <Button variant="outlined" disabled={rows.length === 0} onClick={() => downloadCsv(`${reportKey}.csv`, rows)}>
                Export CSV
              </Button>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          {loading ? (
            <Box sx={{ display: "grid", placeItems: "center", minHeight: 240 }}>
              <CircularProgress />
            </Box>
          ) : rows.length === 0 ? (
            <Alert severity="info">No rows returned for the selected report.</Alert>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  {headers.map((header) => (
                    <TableCell key={header}>{header}</TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row, index) => (
                  <TableRow key={index} hover>
                    {headers.map((header) => (
                      <TableCell key={header}>{row[header] === null || row[header] === undefined ? "-" : String(row[header])}</TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
