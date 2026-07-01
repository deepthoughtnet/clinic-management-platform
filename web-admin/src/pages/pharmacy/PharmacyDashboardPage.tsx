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
import LocalPharmacyRoundedIcon from "@mui/icons-material/LocalPharmacyRounded";
import Inventory2RoundedIcon from "@mui/icons-material/Inventory2Rounded";
import MedicationRoundedIcon from "@mui/icons-material/MedicationRounded";
import AssessmentRoundedIcon from "@mui/icons-material/AssessmentRounded";
import ReceiptLongRoundedIcon from "@mui/icons-material/ReceiptLongRounded";

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
  getCurrentPharmacyPosShift,
  listPharmacyPosSales,
  listSuppliers,
  listReconciliations,
  type Medicine,
  type PharmacyAnalytics,
  type PharmacyDashboard,
  type PharmacyReconciliation,
  type Supplier,
} from "../../api/clinicApi";
import { isActiveDispenseStatus } from "./dispensingPageUtils.js";

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
  const [queue, setQueue] = React.useState<Array<{ prescriptionId: string; prescriptionNumber: string; patientName: string | null; doctorName: string | null; prescriptionTimestamp: string | null; status?: string | null; lines: Array<{ prescribedMedicineName: string; status: string; availabilityStatus: string; expiryStatus: string; }> }>>([]);
  const [reconciliations, setReconciliations] = React.useState<PharmacyReconciliation[]>([]);
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [suppliers, setSuppliers] = React.useState<Supplier[]>([]);
  const [salesCount, setSalesCount] = React.useState(0);
  const [hasOpenShift, setHasOpenShift] = React.useState(false);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [dashboardRow, analyticsRow, queueRows, reconciliationRows, medicineRows, saleRows, supplierRows] = await Promise.all([
        getPharmacyDashboard(auth.accessToken, auth.tenantId),
        getPharmacyAnalytics(auth.accessToken, auth.tenantId),
        getDispensingQueue(auth.accessToken, auth.tenantId),
        listReconciliations(auth.accessToken, auth.tenantId),
        getMedicines(auth.accessToken, auth.tenantId),
        listPharmacyPosSales(auth.accessToken, auth.tenantId),
        listSuppliers(auth.accessToken, auth.tenantId),
      ]);
      const currentShift = await getCurrentPharmacyPosShift(auth.accessToken, auth.tenantId);
      setDashboard(dashboardRow);
      setAnalytics(analyticsRow);
      setQueue(queueRows);
      setReconciliations(reconciliationRows);
      setMedicines(medicineRows);
      setSalesCount(saleRows.length);
      setSuppliers(supplierRows);
      setHasOpenShift(Boolean(currentShift));
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
  const readyToDispense = queue.filter((row) => isActiveDispenseStatus(row.status) || row.lines.some((line) => line.status === "NOT_DISPENSED" || line.status === "READY_FOR_DISPENSE" || line.status === "PARTIALLY_DISPENSED"));
  const lowStockMedicines = analytics?.lowStockMedicines ?? [];
  const expiringSoon = analytics?.expiryRiskMedicines ?? [];
  const pendingReconciliations = reconciliations.filter((row) => ["PENDING", "REVIEWED", "SUBMITTED"].includes((row.status || "").toUpperCase()));
  const pendingApproval = reconciliations.filter((row) => (row.status || "").toUpperCase() === "PENDING").length;
  const reviewReady = reconciliations.filter((row) => (row.status || "").toUpperCase() === "REVIEWED").length;
  const recentMovements = dashboard?.recentStockMovements?.slice(0, 5) ?? [];
  const pendingDispenseCount = queue.filter((row) => isActiveDispenseStatus(row.status) || row.lines.some((line) => line.status === "NOT_DISPENSED" || line.status === "READY_FOR_DISPENSE" || line.status === "PARTIALLY_DISPENSED")).length;
  const dispensedTodayCount = dashboard?.todayDispensedCount ?? 0;
  const isBrandNewTenant = !loading && (medicines.length === 0 || (dashboard?.stockBatchesCount ?? 0) === 0 || salesCount === 0);
  const supplierCount = suppliers.length;
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const setupSteps = React.useMemo(() => ([
    { label: "Create staff", done: Boolean(auth.tenantId), action: () => navigate("/settings/users-roles"), cta: "Users & Roles" },
    { label: "Add medicines", done: medicines.length > 0, action: () => navigate("/pharmacy/medicines"), cta: "Medicine Master" },
    { label: "Receive via Procurement", done: (dashboard?.stockBatchesCount ?? 0) > 0, action: () => navigate("/pharmacy/procurement?workspace=suppliers&focus=supplier"), cta: "Receive via Procurement" },
    { label: "Open POS shift", done: hasOpenShift, action: () => navigate("/pharmacy/pos"), cta: "Open POS" },
    { label: "Complete first sale", done: salesCount > 0, action: () => navigate("/pharmacy/pos"), cta: "POS Sale" },
  ]), [auth.tenantId, dashboard?.stockBatchesCount, hasOpenShift, medicines.length, navigate, salesCount]);
  const setupDoneCount = setupSteps.filter((step) => step.done).length;
  const setupProgress = Math.round((setupDoneCount / setupSteps.length) * 100);
  const quickActions = React.useMemo(() => ([
    { label: "Add Medicine", icon: <MedicationRoundedIcon fontSize="small" />, action: () => navigate("/pharmacy/medicines") },
    { label: "Add Supplier", icon: <Inventory2RoundedIcon fontSize="small" />, action: () => navigate("/pharmacy/procurement?workspace=suppliers&focus=supplier") },
    { label: "Create PO", icon: <AssessmentRoundedIcon fontSize="small" />, action: () => navigate(supplierCount === 0 ? "/pharmacy/procurement?workspace=suppliers&focus=supplier" : "/pharmacy/procurement?workspace=purchase-orders") },
    { label: "Receive via Procurement", icon: <Inventory2RoundedIcon fontSize="small" />, action: () => navigate("/pharmacy/procurement?workspace=purchase-orders") },
    { label: "Direct Goods Receipt", icon: <Inventory2RoundedIcon fontSize="small" />, action: () => navigate("/pharmacy/procurement?workspace=goods-receipt&mode=direct") },
    { label: "POS Sale", icon: <LocalPharmacyRoundedIcon fontSize="small" />, action: () => navigate("/pharmacy/pos") },
    { label: "Procurement", icon: <AssessmentRoundedIcon fontSize="small" />, action: () => navigate("/pharmacy/procurement?workspace=suppliers&focus=supplier") },
    { label: "Reconciliation", icon: <AssessmentRoundedIcon fontSize="small" />, action: () => navigate("/pharmacy/reconciliation") },
    { label: "Reports & Audit", icon: <ReceiptLongRoundedIcon fontSize="small" />, action: () => navigate("/pharmacy/stock-movements") },
  ]), [navigate, supplierCount]);

  return (
    <Stack spacing={1.5}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.25, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.25 }}>
            <LocalPharmacyRoundedIcon fontSize="small" color="primary" />
            <Typography variant="h4" sx={{ fontWeight: 900, lineHeight: 1.1 }}>Pharmacy Dashboard</Typography>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Fast dispensing, stock health, and reconciliation oversight for pharmacy operations.
          </Typography>
        </Box>
        <Button variant="outlined" size="small" onClick={() => void load()}>Refresh</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      {isBrandNewTenant ? (
        <CompactFilterCard
          title="Pharmacy Setup"
          subtitle="Complete these steps to start selling."
          actions={<Chip size="small" label={`${setupDoneCount}/${setupSteps.length} complete • ${setupProgress}%`} variant="outlined" />}
        >
          <Grid container spacing={1}>
            {setupSteps.map((step, index) => (
              <Grid key={step.label} size={{ xs: 12, md: 6 }}>
                <Card variant="outlined">
                  <CardContent sx={{ p: 1 }}>
                    <Stack spacing={0.5}>
                      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
                          <Chip size="small" label={index + 1} color={step.done ? "success" : "primary"} sx={compactChipSx} />
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{step.label}</Typography>
                        </Box>
                        <Chip size="small" label={step.done ? "Done" : "Pending"} color={step.done ? "success" : "default"} variant="outlined" sx={compactChipSx} />
                      </Box>
                      <Button size="small" variant={step.done ? "outlined" : "contained"} onClick={step.action}>
                        {step.cta}
                      </Button>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            <Button size="small" variant="contained" onClick={() => navigate("/pharmacy/medicines")}>Add Medicine</Button>
            <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/procurement?workspace=purchase-orders")}>Receive via Procurement</Button>
            <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/procurement?workspace=goods-receipt&mode=direct")}>Direct Goods Receipt</Button>
            <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/pos")}>Open POS</Button>
          </Stack>
        </CompactFilterCard>
      ) : null}

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Ready to dispense" value={readyToDispense.length} tone="info" helper="Prescriptions waiting for issue" onClick={() => navigate("/pharmacy/dispensing?filter=ACTIVE")} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Pending dispense" value={pendingDispenseCount} tone="warning" helper="Not yet dispensed" onClick={() => navigate("/pharmacy/dispensing?filter=PENDING")} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Today dispensed" value={dispensedTodayCount} tone="success" helper="Dispense transactions today" onClick={() => navigate("/pharmacy/stock-movements?movement=DISPENSED")} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Low stock" value={lowStockMedicines.length} tone="warning" helper="Medicines below threshold" onClick={() => navigate("/inventory?tab=low-stock")} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Expiring soon" value={expiringSoon.length} tone="error" helper="Batches within expiry window" onClick={() => navigate("/inventory?tab=expiry-report")} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 2 }}><CompactStatCard label="Reconciliation" value={pendingApproval + reviewReady} tone="info" helper="Pending approval / review" onClick={() => navigate("/pharmacy/reconciliation")} /></Grid>
      </Grid>

      <CompactFilterCard
        title="Quick actions"
        subtitle="Primary pharmacy workflow: dispense, check stock, review movements, and manage the medicine catalogue."
      >
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
          {quickActions.map((item, index) => (
            <Button
              key={item.label}
              variant={index === 0 ? "contained" : "outlined"}
              size="small"
              startIcon={item.icon}
              onClick={item.action}
            >
              {item.label}
            </Button>
          ))}
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
