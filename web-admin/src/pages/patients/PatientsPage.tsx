import * as React from "react";
import { useNavigate } from "react-router-dom";
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
  TextField,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { searchPatients, type Patient } from "../../api/clinicApi";

const activeOptions = [
  { label: "All", value: "" },
  { label: "Active", value: "true" },
  { label: "Inactive", value: "false" },
];

export default function PatientsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [patients, setPatients] = React.useState<Patient[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [filters, setFilters] = React.useState({
    patientNumber: "",
    mobile: "",
    name: "",
    active: "",
  });

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const rows = await searchPatients(auth.accessToken, auth.tenantId, {
        patientNumber: filters.patientNumber.trim() || undefined,
        mobile: filters.mobile.trim() || undefined,
        name: filters.name.trim() || undefined,
        active: filters.active === "" ? null : filters.active === "true",
      });
      setPatients(rows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load patients");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, filters]);

  React.useEffect(() => {
    void load();
  }, [load]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Patients
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Tenant-scoped enrollment, search, and profile management.
          </Typography>
        </Box>
        <Button variant="contained" onClick={() => navigate("/patients/new")}>
          New Patient
        </Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Search
            </Typography>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth label="Patient number" value={filters.patientNumber} onChange={(e) => setFilters((c) => ({ ...c, patientNumber: e.target.value }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth label="Mobile" value={filters.mobile} onChange={(e) => setFilters((c) => ({ ...c, mobile: e.target.value }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth label="Name" value={filters.name} onChange={(e) => setFilters((c) => ({ ...c, name: e.target.value }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <FormControl fullWidth>
                  <InputLabel id="patient-active-label">Status</InputLabel>
                  <Select
                    labelId="patient-active-label"
                    label="Status"
                    value={filters.active}
                    onChange={(e) => setFilters((c) => ({ ...c, active: String(e.target.value) }))}
                  >
                    {activeOptions.map((option) => (
                      <MenuItem key={option.value || "all"} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            </Grid>
            <Box sx={{ display: "flex", gap: 1, justifyContent: "flex-end" }}>
              <Button onClick={() => setFilters({ patientNumber: "", mobile: "", name: "", active: "" })}>Clear</Button>
              <Button variant="contained" onClick={() => void load()}>
                Search
              </Button>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          {loading ? (
            <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
              <CircularProgress />
            </Box>
          ) : patients.length === 0 ? (
            <Alert severity="info">No patients found for the selected filters.</Alert>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Patient</TableCell>
                  <TableCell>Mobile</TableCell>
                  <TableCell>Gender</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {patients.map((patient) => (
                  <TableRow key={patient.id} hover>
                    <TableCell>
                      <Stack spacing={0.25}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {patient.firstName} {patient.lastName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {patient.patientNumber}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{patient.mobile}</TableCell>
                    <TableCell>{patient.gender}</TableCell>
                    <TableCell>
                      <Chip size="small" label={patient.active ? "Active" : "Inactive"} color={patient.active ? "success" : "default"} />
                    </TableCell>
                    <TableCell>{new Date(patient.createdAt).toLocaleString()}</TableCell>
                    <TableCell align="right">
                      <Button size="small" onClick={() => navigate(`/patients/${patient.id}`)}>
                        View
                      </Button>
                      <Button size="small" onClick={() => navigate(`/patients/${patient.id}/edit`)}>
                        Edit
                      </Button>
                    </TableCell>
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
