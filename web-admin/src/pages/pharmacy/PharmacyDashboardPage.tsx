import * as React from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  CompactEmptyState,
  CompactFilterCard,
  CompactStatCard,
  compactCardContentSx,
  compactChipSx,
} from "../../components/compact/CompactUi";
import {
  getDispensingQueue,
  getMedicines,
  getPharmacyAnalytics,
  getPharmacyDashboard,
  listReconciliations,
  type Medicine,
  type PharmacyAnalytics,
  type PharmacyDashboard,
  type PharmacyReconciliation,
} from "../../api/clinicApi";

function formatDateTime(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : "-";
}

function formatDate(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleDateString() : "-";
}

function transactionLabel(value: string): string {
  return value.replace(/_/g, " ").replace(/\b\w/g, (char) => char.toUpperCase());
}

function statusTone(status: string): "default" | "success" | "warning" | "error" | "info" {
  if (["APPROVED", "CONFIRMED", "APPLIED", "POSTED"].includes(status)) return "success";
  if (["PENDING", "REVIEWED", "SUBMITTED"].includes(status)) return "warning";
  if (["REJECTED", "CANCELLED", "FAILED"].includes(status)) return "error";
  return "default";
}

export default function PharmacyDashboardPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [dashboard, setDashboard] = React.useState<PharmacyDashboard | null>(null);
  const [analytics, setAnalytics] = React.useState<PharmacyAnalytics | null>(null);
  const [queue, setQueue] = React.useState<Array<{ prescriptionId: string; prescriptionNumber: string; patientName: string | null; doctorName: string | null; prescriptionTimestamp: string | null; lines: Array<{ prescribedMedicineName: string; status: string; availabilityStatus: string; expiryStatus: string; }> }>>([]);
  const [reconciliations, setReconciliations] = React.useState<PharmacyReconciliation[]>([]);
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [dashboardRow, analyticsRow, queueRows, reconciliationRows, medicineRows] = await Promise.all([
        getPharmacyDashboard(auth.accessToken, auth.tenantId),
        getPharmacyAnalytics(auth.accessToken, auth.tenantId),
        getDispensingQueue(auth.accessToken, auth.tenantId),
        listReconciliations(auth.accessToken, auth.tenantId),
        getMedicines(auth.accessToken, auth.tenantId),
      ]);
      setDashboard(dashboardRow);
      setAnalytics(analyticsRow);
      setQueue(queueRows);
      setReconciliations(reconciliationRows);
      setMedicines(medicineRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load pharmacy dashboard");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const medicineById = React.useMemo(() => new Map(medicines.map((medicine) => [medicine.id, medicine] as const)), [medicines]);
  const readyToDispense = queue.filter((row) => row.lines.some((line) => line.status === "NOT_DISPENSED" || line.status === "PARTIALLY_DISPENSED"));
  const lowStockMedicines = analytics?.lowStockMedicines ?? [];
  const expiringSoon = analytics?.expiryRiskMedicines ?? [];
  const pendingReconciliations = reconciliations.filter((row) => ["PENDING", "REVIEWED", "SUBMITTED"].includes((row.status || "").toUpperCase()));
  const pendingApproval = reconciliations.filter((row) => (row.status || "").toUpperCase() === "PENDING").length;
  const reviewReady = reconciliations.filter((row) => (row.status || "").toUpperCase() === "REVIEWED").length;
  const recentMovements = dashboard?.recentStockMovements?.slice(0, 5) ?? [];

  return (
    <Stack spacing={1.5}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.25, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, lineHeight: 1.1 }}>Pharmacy Dashboard</Typography>
          <Typography variant="body2" color="text.secondary">
            Fast dispensing, stock health, and reconciliation oversight for pharmacy operations.
          </Typography>
        </Box>
        <Button variant="outlined" size="small" onClick={() => void load()}>Refresh</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Ready to dispense" value={readyToDispense.length} tone="info" helper="Prescriptions waiting for issue" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Pending dispense" value={dashboard?.pendingDispensingCount ?? 0} tone="warning" helper="Not yet dispensed" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Today dispensed" value={dashboard?.todayDispensedCount ?? 0} tone="success" helper="Dispense transactions today" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Low stock" value={lowStockMedicines.length} tone="warning" helper="Medicines below threshold" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Expiring soon" value={expiringSoon.length} tone="error" helper="Batches within expiry window" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Reconciliation" value={pendingApproval + reviewReady} tone="info" helper="Pending approval / review" /></Grid>
      </Grid>

      <CompactFilterCard
        title="Quick actions"
        subtitle="Primary pharmacy workflow: dispense, check stock, review movements, and manage the medicine catalogue."
      >
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
          <Button variant="contained" size="small" onClick={() => navigate("/pharmacy/dispensing")}>Open Dispensing</Button>
          <Button variant="outlined" size="small" onClick={() => navigate("/inventory")}>Open Inventory</Button>
          <Button variant="outlined" size="small" onClick={() => navigate("/pharmacy/stock-movements")}>Open Stock Movements</Button>
          <Button variant="outlined" size="small" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>
          <Button variant="outlined" size="small" onClick={() => navigate("/pharmacy/operations?tab=reconciliation")}>Open Reconciliation</Button>
        </Stack>
      </CompactFilterCard>

      {loading ? null : (
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12, xl: 7 }}>
            <Card variant="outlined">
              <CardContent sx={compactCardContentSx}>
                <Stack spacing={1}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Dispensing queue</Typography>
                      <Typography variant="body2" color="text.secondary">Ready and partially dispensed prescriptions.</Typography>
                    </Box>
                    <Chip size="small" label={`${readyToDispense.length} rows`} sx={compactChipSx} />
                  </Box>
                  {readyToDispense.length === 0 ? (
                    <CompactEmptyState title="No prescriptions ready for dispensing" subtitle="Prescriptions will appear here when they are finalized and waiting on pharmacy issue." />
                  ) : (
                    <Box sx={{ overflowX: "auto", maxHeight: 320, overflowY: "auto" }}>
                      <Table stickyHeader size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell sx={{ py: 0.65 }}>Prescription</TableCell>
                            <TableCell sx={{ py: 0.65 }}>Patient</TableCell>
                            <TableCell sx={{ py: 0.65 }}>Doctor</TableCell>
                            <TableCell sx={{ py: 0.65 }}>Medicines</TableCell>
                            <TableCell sx={{ py: 0.65 }}>Timestamp</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {readyToDispense.slice(0, 8).map((row) => (
                            <TableRow key={row.prescriptionId} hover>
                              <TableCell sx={{ py: 0.65, fontWeight: 700 }}>{row.prescriptionNumber}</TableCell>
                              <TableCell sx={{ py: 0.65 }}>{row.patientName || row.prescriptionId}</TableCell>
                              <TableCell sx={{ py: 0.65 }}>{row.doctorName || "-"}</TableCell>
                              <TableCell sx={{ py: 0.65 }}>
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  {row.lines.slice(0, 3).map((line) => (
                                    <Chip key={`${row.prescriptionId}-${line.prescribedMedicineName}`} size="small" variant="outlined" label={line.prescribedMedicineName} sx={compactChipSx} />
                                  ))}
                                  {row.lines.length > 3 ? <Chip size="small" variant="outlined" label={`+${row.lines.length - 3} more`} sx={compactChipSx} /> : null}
                                </Stack>
                              </TableCell>
                              <TableCell sx={{ py: 0.65 }}>{formatDateTime(row.prescriptionTimestamp)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </Box>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, xl: 5 }}>
            <Card variant="outlined">
              <CardContent sx={compactCardContentSx}>
                <Stack spacing={1}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Stock health</Typography>
                      <Typography variant="body2" color="text.secondary">Low stock and batches near expiry.</Typography>
                    </Box>
                    <Chip size="small" label={`Value ${analytics?.stockValueEstimate?.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) ?? "0.00"}`} sx={compactChipSx} />
                  </Box>
                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Typography variant="caption" color="text.secondary">Low stock medicines</Typography>
                      <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                        {lowStockMedicines.slice(0, 4).map((row) => (
                          <Box key={row.medicineId} sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", border: "1px solid", borderColor: "divider", borderRadius: 1.5, px: 1, py: 0.75 }}>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>{row.medicineName}</Typography>
                              <Typography variant="caption" color="text.secondary">Available {row.quantityOnHand}</Typography>
                            </Box>
                            <Chip size="small" label={`Low ${row.lowStockThreshold ?? "-"}`} color="warning" variant="outlined" sx={compactChipSx} />
                          </Box>
                        ))}
                        {lowStockMedicines.length === 0 ? <Typography variant="body2" color="text.secondary">No low stock items.</Typography> : null}
                      </Stack>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Typography variant="caption" color="text.secondary">Expiring soon</Typography>
                      <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                        {expiringSoon.slice(0, 4).map((row) => (
                          <Box key={row.id} sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", border: "1px solid", borderColor: "divider", borderRadius: 1.5, px: 1, py: 0.75 }}>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>{row.medicineName}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.batchNumber || "No batch"} • {formatDate(row.expiryDate)}</Typography>
                            </Box>
                            <Chip size="small" label={row.quantityOnHand} color="warning" variant="outlined" sx={compactChipSx} />
                          </Box>
                        ))}
                        {expiringSoon.length === 0 ? <Typography variant="body2" color="text.secondary">No expiring batches in range.</Typography> : null}
                      </Stack>
                    </Grid>
                  </Grid>
                </Stack>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, xl: 7 }}>
            <Card variant="outlined">
              <CardContent sx={compactCardContentSx}>
                <Stack spacing={1}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Recent stock movements</Typography>
                      <Typography variant="body2" color="text.secondary">Latest inventory activity is visible here for quick audit checks.</Typography>
                    </Box>
                    <Chip size="small" label={`${recentMovements.length} recent`} sx={compactChipSx} />
                  </Box>
                  {recentMovements.length === 0 ? (
                    <CompactEmptyState title="No recent stock movements" subtitle="Movement history will show dispensing, adjustment, and return activity here." />
                  ) : (
                    <Box sx={{ overflowX: "auto" }}>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell sx={{ py: 0.65 }}>Date</TableCell>
                            <TableCell sx={{ py: 0.65 }}>Medicine</TableCell>
                            <TableCell sx={{ py: 0.65 }}>Type</TableCell>
                            <TableCell sx={{ py: 0.65 }} align="right">Qty</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {recentMovements.map((row) => (
                            <TableRow key={row.id} hover>
                              <TableCell sx={{ py: 0.65 }}>{formatDateTime(row.createdAt)}</TableCell>
                              <TableCell sx={{ py: 0.65 }}>{medicineById.get(row.medicineId)?.medicineName || row.medicineId}</TableCell>
                              <TableCell sx={{ py: 0.65 }}><Chip size="small" label={transactionLabel(row.transactionType)} variant="outlined" sx={compactChipSx} /></TableCell>
                              <TableCell sx={{ py: 0.65 }} align="right">{row.quantity}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </Box>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, xl: 5 }}>
            <Card variant="outlined">
              <CardContent sx={compactCardContentSx}>
                <Stack spacing={1}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Reconciliation oversight</Typography>
                      <Typography variant="body2" color="text.secondary">Maker-created records wait for a second user before posting adjustments.</Typography>
                    </Box>
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      <Chip size="small" label={`Pending ${pendingApproval}`} color="warning" variant="outlined" sx={compactChipSx} />
                      <Chip size="small" label={`Reviewed ${reviewReady}`} color="info" variant="outlined" sx={compactChipSx} />
                    </Stack>
                  </Box>
                  {pendingReconciliations.length === 0 ? (
                    <CompactEmptyState title="No reconciliation actions pending" subtitle="Stock count and adjustment requests will appear here for approval review." />
                  ) : (
                    <Box sx={{ overflowX: "auto", maxHeight: 320, overflowY: "auto" }}>
                      <Table stickyHeader size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell sx={{ py: 0.65 }}>Medicine</TableCell>
                            <TableCell sx={{ py: 0.65 }}>Batch</TableCell>
                            <TableCell sx={{ py: 0.65 }} align="right">System</TableCell>
                            <TableCell sx={{ py: 0.65 }} align="right">Physical</TableCell>
                            <TableCell sx={{ py: 0.65 }}>Status</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {pendingReconciliations.slice(0, 6).map((row) => (
                            <TableRow key={row.id} hover>
                              <TableCell sx={{ py: 0.65, fontWeight: 700 }}>{row.medicineName || "Medicine"}</TableCell>
                              <TableCell sx={{ py: 0.65 }}>{row.batchNumber || "-"}</TableCell>
                              <TableCell sx={{ py: 0.65 }} align="right">{row.systemQuantity}</TableCell>
                              <TableCell sx={{ py: 0.65 }} align="right">{row.physicalQuantity ?? "-"}</TableCell>
                              <TableCell sx={{ py: 0.65 }}><Chip size="small" label={row.status} color={statusTone((row.status || "").toUpperCase())} sx={compactChipSx} /></TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </Box>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}
    </Stack>
  );
}
