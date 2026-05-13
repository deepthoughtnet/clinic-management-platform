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
  Grid,
  Stack,
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
  dispensePrescriptionMedicine,
  generateMedicineBillFromDispense,
  getDispensingQueue,
  getPrescriptionDispense,
  type DispenseLine,
  type PrescriptionDispense,
} from "../../api/clinicApi";

export default function DispensingPage() {
  const auth = useAuth();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const [rows, setRows] = React.useState<PrescriptionDispense[]>([]);
  const [selected, setSelected] = React.useState<PrescriptionDispense | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [lineQty, setLineQty] = React.useState<Record<string, string>>({});
  const [lineBatch, setLineBatch] = React.useState<Record<string, string>>({});
  const [saving, setSaving] = React.useState(false);

  const canDispense = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PHARMACIST") || auth.rolesUpper.includes("PHARMA") || auth.rolesUpper.includes("PHARMACY") || auth.rolesUpper.includes("BILLING_USER");
  const canBill = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("BILLING_USER") || auth.rolesUpper.includes("RECEPTIONIST");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      setRows(await getDispensingQueue(auth.accessToken, auth.tenantId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load dispensing queue");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const filtered = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return rows;
    return rows.filter((row) =>
      [row.prescriptionNumber, row.patientName, row.patientId].filter(Boolean).some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [rows, search]);

  const openDetails = async (prescriptionId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const details = await getPrescriptionDispense(auth.accessToken, auth.tenantId, prescriptionId);
      setSelected(details);
      setDialogOpen(true);
      const nextQty: Record<string, string> = {};
      for (const line of details.lines) {
        nextQty[line.prescribedMedicineName] = String(Math.max(0, line.prescribedQuantity - line.dispensedQuantity));
      }
      setLineQty(nextQty);
      setLineBatch({});
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load prescription dispensing details");
    }
  };

  const dispenseLine = async (line: DispenseLine) => {
    if (!selected || !auth.accessToken || !auth.tenantId || !canDispense) return;
    const quantity = Number(lineQty[line.prescribedMedicineName] || "0");
    if (quantity <= 0) {
      setError("Dispense quantity must be positive.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await dispensePrescriptionMedicine(auth.accessToken, auth.tenantId, selected.prescriptionId, {
        prescribedMedicineName: line.prescribedMedicineName,
        medicineId: line.medicineId,
        quantity,
        batchId: lineBatch[line.prescribedMedicineName] || null,
        allowBatchOverride: Boolean(lineBatch[line.prescribedMedicineName]),
      });
      setSelected(updated);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to dispense medicine");
    } finally {
      setSaving(false);
    }
  };

  const generateBill = async () => {
    if (!selected || !auth.accessToken || !auth.tenantId || !canBill) return;
    setSaving(true);
    setError(null);
    try {
      await generateMedicineBillFromDispense(auth.accessToken, auth.tenantId, selected.prescriptionId);
      const refreshed = await getPrescriptionDispense(auth.accessToken, auth.tenantId, selected.prescriptionId);
      setSelected(refreshed);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate medicine bill");
    } finally {
      setSaving(false);
    }
  };

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to access Dispensing.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Dispensing</Typography>
          <Typography variant="body2" color="text.secondary">Dispense finalized prescriptions using current stock availability. Supports full and partial dispense.</Typography>
        </Box>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="prescription / patient" /></Grid>
          </Grid>
        </CardContent>
      </Card>

      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card>
          <CardContent>
            {filtered.length === 0 ? <Alert severity="info">No finalized prescriptions in dispensing queue.</Alert> : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Prescription</TableCell>
                    <TableCell>Patient</TableCell>
                    <TableCell>Lines</TableCell>
                    <TableCell>Billing</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filtered.map((row) => {
                    const fullyDispensed = row.lines.every((line) => line.status === "DISPENSED");
                    return (
                      <TableRow key={row.prescriptionId}>
                        <TableCell sx={{ fontWeight: 700 }}>{row.prescriptionNumber}</TableCell>
                        <TableCell>{row.patientName || row.patientId}</TableCell>
                        <TableCell>{row.lines.length}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={row.billingStatus}
                            color={row.billingStatus === "PAID" ? "success" : row.billingStatus === "BILLED" ? "info" : "default"}
                          />
                          {fullyDispensed ? <Chip size="small" variant="outlined" sx={{ ml: 1 }} label="Dispensed" /> : null}
                        </TableCell>
                        <TableCell align="right"><Button size="small" onClick={() => void openDetails(row.prescriptionId)}>Open</Button></TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      ) : null}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>Dispense Prescription {selected?.prescriptionNumber}</DialogTitle>
        <DialogContent>
          {!selected ? null : (
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Patient: <strong>{selected.patientName || selected.patientId}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Billing: <strong>{selected.billingStatus}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}>{selected.billedBillId ? <Typography variant="body2">Bill Id: <strong>{selected.billedBillId}</strong></Typography> : null}</Grid>
              </Grid>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Medicine</TableCell>
                    <TableCell align="right">Prescribed</TableCell>
                    <TableCell align="right">Dispensed</TableCell>
                    <TableCell align="right">Available</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Batch Override</TableCell>
                    <TableCell>Qty</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {selected.lines.map((line) => (
                    <TableRow key={line.prescribedMedicineName}>
                      <TableCell sx={{ fontWeight: 700 }}>{line.prescribedMedicineName}</TableCell>
                      <TableCell align="right">{line.prescribedQuantity}</TableCell>
                      <TableCell align="right">{line.dispensedQuantity}</TableCell>
                      <TableCell align="right">{line.availableQuantity ?? "-"}</TableCell>
                      <TableCell><Chip size="small" label={line.status} color={line.status === "DISPENSED" ? "success" : line.status === "PARTIALLY_DISPENSED" ? "warning" : "default"} /></TableCell>
                      <TableCell>
                        <TextField
                          size="small"
                          placeholder="optional batch id"
                          value={lineBatch[line.prescribedMedicineName] || ""}
                          onChange={(e) => setLineBatch((v) => ({ ...v, [line.prescribedMedicineName]: e.target.value }))}
                          disabled={!canDispense}
                        />
                      </TableCell>
                      <TableCell>
                        <TextField
                          size="small"
                          type="number"
                          value={lineQty[line.prescribedMedicineName] || ""}
                          onChange={(e) => setLineQty((v) => ({ ...v, [line.prescribedMedicineName]: e.target.value }))}
                          disabled={!canDispense || line.status === "DISPENSED"}
                        />
                      </TableCell>
                      <TableCell align="right">
                        {canDispense ? (
                          <Button size="small" disabled={saving || line.status === "DISPENSED"} onClick={() => void dispenseLine(line)}>Dispense</Button>
                        ) : (
                          "-"
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Close</Button>
          {canBill ? <Button variant="contained" disabled={saving || !selected || selected.billingStatus !== "NOT_BILLED"} onClick={() => void generateBill()}>Generate Bill</Button> : null}
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
