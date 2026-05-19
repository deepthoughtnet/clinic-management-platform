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
import CodeScannerField from "../../components/pharmacy/CodeScannerField";
import {
  dispensePrescriptionMedicine,
  generateMedicineBillFromDispense,
  getDispensingQueue,
  getPrescriptionDispense,
  getSubstituteSuggestions,
  type DispenseLine,
  type PrescriptionDispense,
  type SubstituteSuggestion,
} from "../../api/clinicApi";

function availabilityColor(status: string) {
  switch (status) {
    case "AVAILABLE":
      return "success";
    case "LOW_STOCK":
      return "warning";
    case "PARTIAL_AVAILABLE":
      return "warning";
    case "OUT_OF_STOCK":
      return "error";
    default:
      return "default";
  }
}

function expiryColor(status: string) {
  switch (status) {
    case "NEAR_EXPIRY":
      return "warning";
    case "EXPIRED":
      return "error";
    case "OK":
      return "success";
    default:
      return "default";
  }
}

function dispenseColor(status: string) {
  switch (status) {
    case "DISPENSED":
      return "success";
    case "PARTIALLY_DISPENSED":
      return "warning";
    case "UNAVAILABLE":
    case "CANCELLED":
      return "error";
    default:
      return "default";
  }
}

function aggregateAvailability(lines: DispenseLine[]) {
  if (lines.some((line) => line.availabilityStatus === "OUT_OF_STOCK")) return "OUT_OF_STOCK";
  if (lines.some((line) => line.availabilityStatus === "PARTIAL_AVAILABLE")) return "PARTIAL_AVAILABLE";
  if (lines.some((line) => line.availabilityStatus === "LOW_STOCK")) return "LOW_STOCK";
  return "AVAILABLE";
}

function aggregateDispense(lines: DispenseLine[]) {
  if (lines.every((line) => line.status === "DISPENSED")) return "DISPENSED";
  if (lines.some((line) => line.status === "PARTIALLY_DISPENSED")) return "PARTIALLY_DISPENSED";
  if (lines.some((line) => line.status === "UNAVAILABLE" || line.status === "CANCELLED")) return "UNAVAILABLE";
  return "NOT_DISPENSED";
}

function formatTimestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : "-";
}

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
  const [substitutes, setSubstitutes] = React.useState<Record<string, SubstituteSuggestion[]>>({});
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
      [row.prescriptionNumber, row.patientName, row.doctorName, row.patientId, row.prescriptionTimestamp]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [rows, search]);

  const openDetails = async (prescriptionId: string) => {
    const accessToken = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!accessToken || !tenantId) return;
    try {
      const details = await getPrescriptionDispense(accessToken, tenantId, prescriptionId);
      setSelected(details);
      setDialogOpen(true);
      const nextQty: Record<string, string> = {};
      for (const line of details.lines) {
        nextQty[line.prescribedMedicineName] = String(Math.max(1, line.pendingQuantity || line.prescribedQuantity || 1));
      }
      setLineQty(nextQty);
      setLineBatch({});
      const substitutePairs = await Promise.allSettled(
        details.lines
          .filter((line) => line.medicineId && (line.availabilityStatus === "OUT_OF_STOCK" || line.availabilityStatus === "PARTIAL_AVAILABLE" || line.availabilityStatus === "LOW_STOCK"))
          .map(async (line) => {
            const medicineId = line.medicineId;
            if (!medicineId) {
              return [line.prescribedMedicineName, []] as const;
            }
            return [line.prescribedMedicineName, await getSubstituteSuggestions(accessToken, tenantId, medicineId)] as const;
          }),
      );
      const resolved = substitutePairs
        .filter((result): result is PromiseFulfilledResult<readonly [string, SubstituteSuggestion[]]> => result.status === "fulfilled")
        .map((result) => result.value);
      setSubstitutes(Object.fromEntries(resolved));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load prescription dispensing details");
    }
  };

  const dispenseLine = async (line: DispenseLine, action: "FULL" | "PARTIAL" | "CANCEL") => {
    if (!selected || !auth.accessToken || !auth.tenantId || !canDispense) return;
    const defaultQty = Math.max(1, line.pendingQuantity || line.prescribedQuantity || 1);
    const quantity = action === "CANCEL" ? null : Number(lineQty[line.prescribedMedicineName] || defaultQty);
    if (action !== "CANCEL" && (!quantity || quantity <= 0)) {
      setError("Enter a positive dispense quantity.");
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
        action,
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

  const summary = {
    queued: rows.length,
    pending: rows.filter((row) => aggregateDispense(row.lines) === "NOT_DISPENSED").length,
    partial: rows.filter((row) => aggregateDispense(row.lines) === "PARTIALLY_DISPENSED").length,
    outOfStock: rows.filter((row) => aggregateAvailability(row.lines) === "OUT_OF_STOCK").length,
  };

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Dispensing</Typography>
          <Typography variant="body2" color="text.secondary">
            Dispense finalized prescriptions using live stock availability. Full, partial, and unavailable actions are tracked per medicine line.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Box>

      <Grid container spacing={1.5}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Queued</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{summary.queued}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Pending</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{summary.pending}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Partial</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{summary.partial}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Out of stock</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{summary.outOfStock}</Typography></CardContent></Card></Grid>
      </Grid>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField size="small" fullWidth label="Search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="prescription / patient / doctor" />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card>
          <CardContent>
            {filtered.length === 0 ? (
              <Alert severity="info">No finalized prescriptions are waiting for pharmacy dispensing.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Prescription</TableCell>
                    <TableCell>Patient / Doctor</TableCell>
                    <TableCell>Medicines</TableCell>
                    <TableCell>Stock availability</TableCell>
                    <TableCell>Dispense status</TableCell>
                    <TableCell>Timestamp</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filtered.map((row) => {
                    const availability = aggregateAvailability(row.lines);
                    const dispenseStatus = aggregateDispense(row.lines);
                    return (
                      <TableRow key={row.prescriptionId}>
                        <TableCell sx={{ fontWeight: 700 }}>
                          <Stack spacing={0.25}>
                            <Typography variant="body2" sx={{ fontWeight: 800 }}>{row.prescriptionNumber}</Typography>
                            <Typography variant="caption" color="text.secondary">{formatTimestamp(row.prescriptionTimestamp)}</Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2">{row.patientName || row.patientId}</Typography>
                            <Typography variant="caption" color="text.secondary">{row.doctorName || "-"}</Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                            {row.lines.slice(0, 3).map((line) => (
                              <Chip key={line.prescribedMedicineName} size="small" variant="outlined" label={line.prescribedMedicineName} />
                            ))}
                            {row.lines.length > 3 ? <Chip size="small" variant="outlined" label={`+${row.lines.length - 3} more`} /> : null}
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                            <Chip size="small" label={availability.replace(/_/g, " ")} color={availabilityColor(availability)} />
                            {row.lines.some((line) => line.expiryStatus !== "NONE" && line.expiryStatus !== "OK") ? (
                              <Chip size="small" variant="outlined" label="Expiry watch" color="warning" />
                            ) : null}
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={dispenseStatus.replace(/_/g, " ")} color={dispenseColor(dispenseStatus)} />
                        </TableCell>
                        <TableCell>{formatTimestamp(row.prescriptionTimestamp)}</TableCell>
                        <TableCell align="right">
                          <Button size="small" onClick={() => void openDetails(row.prescriptionId)}>Open</Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      ) : null}

      <Dialog open={dialogOpen} onClose={() => { setDialogOpen(false); setSubstitutes({}); }} fullWidth maxWidth="lg">
        <DialogTitle>Dispense Prescription {selected?.prescriptionNumber}</DialogTitle>
        <DialogContent>
          {!selected ? null : (
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Patient: <strong>{selected.patientName || selected.patientId}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Doctor: <strong>{selected.doctorName || "-"}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Prescription: <strong>{formatTimestamp(selected.prescriptionTimestamp)}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Billing: <strong>{selected.billingStatus}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}>{selected.billedBillId ? <Typography variant="body2">Bill Id: <strong>{selected.billedBillId}</strong></Typography> : null}</Grid>
              </Grid>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Medicine</TableCell>
                    <TableCell align="right">Qty</TableCell>
                    <TableCell align="right">Pending</TableCell>
                    <TableCell>Stock</TableCell>
                    <TableCell>Expiry</TableCell>
                    <TableCell>Dispense status</TableCell>
                    <TableCell>Batch override</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {selected.lines.map((line) => {
                    const closed = line.status === "DISPENSED" || line.status === "UNAVAILABLE" || line.status === "CANCELLED";
                    return (
                      <TableRow key={line.prescribedMedicineName}>
                        <TableCell sx={{ fontWeight: 700 }}>{line.prescribedMedicineName}</TableCell>
                        <TableCell align="right">{line.prescribedQuantity}</TableCell>
                        <TableCell align="right">{line.pendingQuantity}</TableCell>
                        <TableCell>
                        <Chip size="small" label={(line.availabilityStatus || "OUT_OF_STOCK").replace(/_/g, " ")} color={availabilityColor(line.availabilityStatus)} />
                        <Typography variant="caption" display="block" color="text.secondary">
                          Available: {line.availableQuantity ?? 0}
                        </Typography>
                        {substitutes[line.prescribedMedicineName]?.length ? (
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                            {substitutes[line.prescribedMedicineName].slice(0, 2).map((suggestion) => (
                              <Chip
                                key={suggestion.medicineId}
                                size="small"
                                label={`Substitute: ${suggestion.medicineName}`}
                                variant="outlined"
                                color={availabilityColor(suggestion.availabilityStatus)}
                              />
                            ))}
                          </Stack>
                        ) : null}
                      </TableCell>
                        <TableCell>
                          <Chip size="small" label={(line.expiryStatus || "NONE").replace(/_/g, " ")} color={expiryColor(line.expiryStatus)} />
                          <Typography variant="caption" display="block" color="text.secondary">
                            {line.nearestExpiryDate || "No expiry date"}
                          </Typography>
                        </TableCell>
                        <TableCell><Chip size="small" label={line.status} color={dispenseColor(line.status)} /></TableCell>
                        <TableCell>
                          <CodeScannerField
                            label="Scan or enter batch code"
                            value={lineBatch[line.prescribedMedicineName] || ""}
                            onChange={(value) => setLineBatch((v) => ({ ...v, [line.prescribedMedicineName]: value }))}
                            placeholder="USB scanner input"
                            disabled={!canDispense || closed}
                          />
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="column" spacing={1} alignItems="flex-end">
                            <TextField
                              size="small"
                              type="number"
                              value={lineQty[line.prescribedMedicineName] || ""}
                              onChange={(e) => setLineQty((v) => ({ ...v, [line.prescribedMedicineName]: e.target.value }))}
                              disabled={!canDispense || closed}
                              sx={{ width: 120 }}
                            />
                            <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                              {canDispense ? (
                                <>
                                  <Button size="small" variant="contained" disabled={saving || closed} onClick={() => void dispenseLine(line, "FULL")}>Full</Button>
                                  <Button size="small" disabled={saving || closed} onClick={() => void dispenseLine(line, "PARTIAL")}>Partial</Button>
                                  <Button size="small" color="inherit" disabled={saving || closed} onClick={() => void dispenseLine(line, "CANCEL")}>Unavailable / Cancel</Button>
                                </>
                              ) : (
                                "-"
                              )}
                            </Stack>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setDialogOpen(false); setSubstitutes({}); }}>Close</Button>
          {canBill ? <Button variant="contained" disabled={saving || !selected || selected.billingStatus !== "NOT_BILLED"} onClick={() => void generateBill()}>Generate Bill</Button> : null}
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
