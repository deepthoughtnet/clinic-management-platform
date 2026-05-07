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
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { getPrescriptionPdf, getPrescriptions, printPrescription, sendPrescription, type Prescription } from "../../api/clinicApi";

function statusColor(status: Prescription["status"]) {
  switch (status) {
    case "FINALIZED":
    case "PRINTED":
    case "SENT":
      return "success";
    case "DRAFT":
    case "PREVIEWED":
      return "warning";
    case "CANCELLED":
      return "default";
  }
}

export default function PrescriptionsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [rows, setRows] = React.useState<Prescription[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [workingId, setWorkingId] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const value = await getPrescriptions(auth.accessToken, auth.tenantId);
        if (!cancelled) {
          setRows(value);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load prescriptions");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId]);

  const openPdf = async (row: Prescription) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setWorkingId(row.id);
    try {
      const { blob } = await getPrescriptionPdf(auth.accessToken, auth.tenantId, row.id);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
      await printPrescription(auth.accessToken, auth.tenantId, row.id);
      const refreshed = await getPrescriptions(auth.accessToken, auth.tenantId);
      setRows(refreshed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open PDF");
    } finally {
      setWorkingId(null);
    }
  };

  const sendVia = async (row: Prescription, channel: "email" | "whatsapp") => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setWorkingId(row.id);
    try {
      await sendPrescription(auth.accessToken, auth.tenantId, row.id, channel);
      const refreshed = await getPrescriptions(auth.accessToken, auth.tenantId);
      setRows(refreshed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send prescription");
    } finally {
      setWorkingId(null);
    }
  };

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>Prescriptions</Typography>
          <Typography variant="body2" color="text.secondary">
            Recent prescriptions, print status, and send actions.
          </Typography>
        </Box>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          {loading ? (
            <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
              <CircularProgress />
            </Box>
          ) : rows.length === 0 ? (
            <Alert severity="info">No prescriptions were found.</Alert>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Number</TableCell>
                  <TableCell>Patient</TableCell>
                  <TableCell>Doctor</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Consultation</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell>{row.prescriptionNumber}</TableCell>
                    <TableCell>{row.patientName || row.patientNumber || row.patientId}</TableCell>
                    <TableCell>{row.doctorName || row.doctorUserId}</TableCell>
                    <TableCell><Chip size="small" label={row.status} color={statusColor(row.status)} /></TableCell>
                    <TableCell>{row.consultationId}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                        <Button size="small" onClick={() => navigate(`/consultations/${row.consultationId}`)}>Open Workspace</Button>
                        <Button size="small" disabled={workingId === row.id} onClick={() => void openPdf(row)}>PDF</Button>
                        <Button size="small" disabled={workingId === row.id} onClick={() => void sendVia(row, "email")}>Email</Button>
                        <Button size="small" disabled={workingId === row.id} onClick={() => void sendVia(row, "whatsapp")}>WhatsApp</Button>
                      </Stack>
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
