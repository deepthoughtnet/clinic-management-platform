import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Drawer,
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useSearchParams } from "react-router-dom";
import Inventory2RoundedIcon from "@mui/icons-material/Inventory2Rounded";
import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, CompactStatCard, compactChipSx } from "../../components/compact/CompactUi";
import {
  getInventoryTransactions,
  getMedicines,
  getStocks,
  type InventoryTransaction,
  type InventoryTransactionType,
  type Medicine,
  type Stock,
} from "../../api/clinicApi";

const FILTER_TYPES: Array<{
  label: string;
  value: string;
}> = [
  { label: "All", value: "" },
  { label: "Sale", value: "SALE" },
  { label: "Return", value: "RETURN" },
  { label: "Customer Return", value: "CUSTOMER_RETURN_IN" },
  { label: "Purchase", value: "PURCHASE" },
  { label: "Adjustment", value: "ADJUSTMENT" },
  { label: "Transfer", value: "TRANSFER" },
  { label: "Reconciliation", value: "RECONCILIATION" },
  { label: "Write-Off", value: "WRITE_OFF" },
];

const MOVEMENT_TYPES: InventoryTransactionType[] = [
  "OPENING",
  "PURCHASE",
  "ADJUSTMENT",
  "SALE",
  "RETURN",
  "CUSTOMER_RETURN_IN",
  "CUSTOMER_RETURN_NON_SELLABLE",
  "VENDOR_RETURN_OUT",
  "WRITE_OFF",
  "DISPENSED",
  "EXPIRED",
  "CANCELLED_DISPENSE",
  "STOCK_IN",
  "ADJUSTMENT_IN",
  "ADJUSTMENT_OUT",
  "TRANSFER_IN",
  "TRANSFER_OUT",
];

function movementChip(type: string) {
  if (["OPENING", "PURCHASE", "STOCK_IN", "RETURN", "CUSTOMER_RETURN_IN", "ADJUSTMENT_IN", "TRANSFER_IN"].includes(type)) return "success" as const;
  if (["SALE", "DISPENSED", "ADJUSTMENT_OUT", "EXPIRED", "CANCELLED_DISPENSE", "TRANSFER_OUT", "CUSTOMER_RETURN_NON_SELLABLE", "VENDOR_RETURN_OUT", "WRITE_OFF"].includes(type)) return "warning" as const;
  return "default" as const;
}

function movementLabel(type: string) {
  const labels: Record<string, string> = {
    OPENING: "Opening",
    PURCHASE: "Purchase",
    SALE: "Sale",
    ADJUSTMENT: "Adjustment",
    RETURN: "Return",
    CUSTOMER_RETURN_IN: "Customer Return In",
    CUSTOMER_RETURN_NON_SELLABLE: "Customer Return Non-sellable",
    VENDOR_RETURN_OUT: "Vendor Return Out",
    WRITE_OFF: "Write-off",
    DISPENSED: "Dispensed",
    EXPIRED: "Expired",
    CANCELLED_DISPENSE: "Cancelled Dispense",
    STOCK_IN: "Stock In",
    ADJUSTMENT_IN: "Stock Reconciliation In",
    ADJUSTMENT_OUT: "Stock Reconciliation Out",
    TRANSFER_IN: "Transfer In",
    TRANSFER_OUT: "Transfer Out",
  };
  return labels[type] || type;
}

function movementMatchesChip(row: InventoryTransaction, chip: string) {
  switch (chip) {
    case "":
      return true;
    case "TRANSFER":
      return row.transactionType === "TRANSFER_IN" || row.transactionType === "TRANSFER_OUT";
    case "RETURN":
      return row.transactionType === "RETURN" || row.transactionType === "CUSTOMER_RETURN_IN" || row.transactionType === "CUSTOMER_RETURN_NON_SELLABLE";
    case "ADJUSTMENT":
      return row.transactionType === "ADJUSTMENT";
    case "RECONCILIATION":
      return row.transactionType === "ADJUSTMENT_IN" || row.transactionType === "ADJUSTMENT_OUT";
    default:
      return row.transactionType === chip;
  }
}

function formatExpiry(expiryDate: string | null) {
  if (!expiryDate) {
    return "-";
  }
  return new Date(expiryDate).toLocaleDateString();
}

function mapMovementError(err: unknown) {
  const message = err instanceof Error ? err.message : "";
  if (!message) return "Stock movement history could not be loaded. Please refresh the page.";
  if (/internal server error/i.test(message)) {
    return "Stock movement history could not be loaded right now. Please refresh and try again.";
  }
  return message;
}

export default function StockMovementsPage() {
  const auth = useAuth();
  const [searchParams] = useSearchParams();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [rows, setRows] = React.useState<InventoryTransaction[]>([]);
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [stocks, setStocks] = React.useState<Stock[]>([]);
  const [medicineId, setMedicineId] = React.useState("");
  const [movementType, setMovementType] = React.useState<string>("");
  const [chipFilter, setChipFilter] = React.useState<string>("");
  const [fromDate, setFromDate] = React.useState("");
  const [toDate, setToDate] = React.useState("");
  const [selectedBatchId, setSelectedBatchId] = React.useState<string | null>(null);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [trx, meds, stockRows] = await Promise.all([
        getInventoryTransactions(auth.accessToken, auth.tenantId),
        getMedicines(auth.accessToken, auth.tenantId),
        getStocks(auth.accessToken, auth.tenantId),
      ]);
      setRows(trx);
      setMedicines(meds);
      setStocks(stockRows);
    } catch (err) {
      setError(mapMovementError(err));
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  React.useEffect(() => {
    const movement = searchParams.get("movement");
    if (!movement) return;
    if (movement === "DISPENSED" || movement === "SALE" || movement === "ADJUSTMENT" || movement === "RECONCILIATION") {
      setMovementType(movement);
      if (movement === "DISPENSED") setChipFilter("DISPENSED");
      if (movement === "RECONCILIATION") setChipFilter("RECONCILIATION");
    }
  }, [searchParams]);

  const medicineById = React.useMemo(() => new Map(medicines.map((m) => [m.id, m])), [medicines]);
  const stockById = React.useMemo(() => new Map(stocks.map((stock) => [stock.id, stock])), [stocks]);

  const filtered = React.useMemo(() => {
    return rows.filter((row) => {
      if (medicineId && row.medicineId !== medicineId) return false;
      if (movementType && row.transactionType !== movementType) return false;
      if (!movementMatchesChip(row, chipFilter)) return false;
      const created = new Date(row.createdAt);
      if (fromDate && created < new Date(`${fromDate}T00:00:00`)) return false;
      if (toDate && created > new Date(`${toDate}T23:59:59`)) return false;
      return true;
    });
  }, [rows, medicineId, movementType, chipFilter, fromDate, toDate]);

  const filteredSummary = React.useMemo(() => ({
    total: filtered.length,
    adjustments: filtered.filter((row) => row.transactionType.includes("ADJUSTMENT")).length,
    stockIns: filtered.filter((row) => row.transactionType === "STOCK_IN" || row.transactionType === "OPENING" || row.transactionType === "PURCHASE" || row.transactionType === "CUSTOMER_RETURN_IN").length,
    dispenseRelated: filtered.filter((row) => row.transactionType === "DISPENSED" || row.transactionType === "CANCELLED_DISPENSE" || row.transactionType === "SALE").length,
  }), [filtered]);

  const selectedStock = selectedBatchId ? stockById.get(selectedBatchId) ?? null : null;
  const selectedStockMovements = React.useMemo(
    () => filtered.filter((row) => row.stockBatchId === selectedBatchId),
    [filtered, selectedBatchId],
  );

  if (!auth.tenantId) {
    return <Alert severity="info">Select a tenant to access Stock Movements.</Alert>;
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
        <Box>
          <Stack direction="row" spacing={1} alignItems="center">
            <Inventory2RoundedIcon color="primary" />
            <Typography variant="h4" sx={{ fontWeight: 900 }}>Stock Movements</Typography>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Review operational stock activity with business references for sales, returns, purchases, and reconciliation.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Box>

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><CompactStatCard label="Rows" value={filteredSummary.total} helper="Movement rows in the current filter window" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><CompactStatCard label="Adjustments" value={filteredSummary.adjustments} helper="Manual and reconciliation-related adjustments" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><CompactStatCard label="Stock In" value={filteredSummary.stockIns} tone="success" helper="Opening, purchase, and inward movement rows" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><CompactStatCard label="Dispense Related" value={filteredSummary.dispenseRelated} tone="warning" helper="Sale and dispense-linked outward rows" /></Grid>
      </Grid>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <CompactFilterCard
        title="Filters"
        subtitle="Keep the audit trail compact by narrowing to medicine, movement type, and date range."
      >
        <Stack spacing={1.25}>
          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
            {FILTER_TYPES.map((chip) => (
              <Chip
                key={chip.label}
                size="small"
                label={chip.label}
                color={chipFilter === chip.value ? "primary" : "default"}
                variant={chipFilter === chip.value ? "filled" : "outlined"}
                sx={compactChipSx}
                onClick={() => setChipFilter(chip.value)}
              />
            ))}
          </Stack>

          <Grid container spacing={1.25}>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField select size="small" fullWidth label="Medicine" value={medicineId} onChange={(e) => setMedicineId(e.target.value)}>
                <MenuItem value="">All</MenuItem>
                {medicines.map((m) => <MenuItem key={m.id} value={m.id}>{m.medicineName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField select size="small" fullWidth label="Movement type" value={movementType} onChange={(e) => setMovementType(e.target.value)}>
                <MenuItem value="">All</MenuItem>
                {MOVEMENT_TYPES.map((t) => <MenuItem key={t} value={t}>{movementLabel(t)}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 2.5 }}>
              <TextField size="small" fullWidth type="date" label="From" value={fromDate} onChange={(e) => setFromDate(e.target.value)} InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid size={{ xs: 12, md: 2.5 }}>
              <TextField size="small" fullWidth type="date" label="To" value={toDate} onChange={(e) => setToDate(e.target.value)} InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid size={{ xs: 12, md: 1 }}>
              <Button
                size="small"
                onClick={() => {
                  setMedicineId("");
                  setMovementType("");
                  setChipFilter("");
                  setFromDate("");
                  setToDate("");
                }}
              >
                Reset
              </Button>
            </Grid>
          </Grid>
        </Stack>
      </CompactFilterCard>

      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card>
          <CardContent>
            {filtered.length === 0 ? (
              <CompactEmptyState
                title="No stock movements match these filters"
                subtitle="Clear one or more filters to review the full tenant-scoped audit trail."
              />
            ) : (
              <Box sx={{ overflowX: "auto", overflowY: "auto", maxHeight: 560 }}>
                <Table stickyHeader size="small" sx={{ minWidth: 980 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Medicine</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell>Batch</TableCell>
                      <TableCell align="right">Before</TableCell>
                      <TableCell align="right">After</TableCell>
                      <TableCell align="right">Qty</TableCell>
                      <TableCell>Reference</TableCell>
                      <TableCell>Adjusted by</TableCell>
                      <TableCell>Notes</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filtered.map((row) => {
                      const medicine = medicineById.get(row.medicineId);
                      const batchLabel = row.batchNumber?.trim() || "Batch not mapped";
                      return (
                        <TableRow key={row.id} hover>
                          <TableCell>{new Date(row.createdAt).toLocaleString()}</TableCell>
                          <TableCell>{medicine?.medicineName || "Unknown medicine"}</TableCell>
                          <TableCell><Chip size="small" label={movementLabel(row.transactionType)} color={movementChip(row.transactionType)} /></TableCell>
                          <TableCell>
                            {row.stockBatchId ? (
                              <Button size="small" variant="text" sx={{ minWidth: 0, p: 0 }} onClick={() => setSelectedBatchId(row.stockBatchId)}>
                                {batchLabel}
                              </Button>
                            ) : batchLabel}
                          </TableCell>
                          <TableCell align="right">{row.beforeQuantity ?? "-"}</TableCell>
                          <TableCell align="right">{row.afterQuantity ?? "-"}</TableCell>
                          <TableCell align="right">{row.quantity}</TableCell>
                          <TableCell>{row.businessReference || movementLabel(row.transactionType)}</TableCell>
                          <TableCell>{row.adjustedByName || "System action"}</TableCell>
                          <TableCell>{row.notes || row.reason || "-"}</TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </Box>
            )}
          </CardContent>
        </Card>
      ) : null}

      <Drawer anchor="right" open={Boolean(selectedBatchId)} onClose={() => setSelectedBatchId(null)}>
        <Box sx={{ width: { xs: 320, sm: 380 }, p: 2, display: "grid", gap: 1.5 }}>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>Batch Details</Typography>
          {selectedStock ? (
            <>
              <Stack spacing={0.5}>
                <Typography variant="body2" color="text.secondary">Batch number</Typography>
                <Typography sx={{ fontWeight: 700 }}>{selectedStock.batchNumber || "Not recorded"}</Typography>
              </Stack>
              <Stack spacing={0.5}>
                <Typography variant="body2" color="text.secondary">Medicine</Typography>
                <Typography sx={{ fontWeight: 700 }}>{selectedStock.medicineName}</Typography>
              </Stack>
              <Stack spacing={0.5}>
                <Typography variant="body2" color="text.secondary">Expiry</Typography>
                <Typography>{formatExpiry(selectedStock.expiryDate)}</Typography>
              </Stack>
              <Stack spacing={0.5}>
                <Typography variant="body2" color="text.secondary">Available qty</Typography>
                <Typography>{selectedStock.quantityOnHand}</Typography>
              </Stack>
              <Divider />
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Movement history</Typography>
              <Stack spacing={1}>
                {selectedStockMovements.length === 0 ? (
                  <Alert severity="info">No movements found for this batch in the current filter window.</Alert>
                ) : selectedStockMovements.slice(0, 10).map((row) => (
                  <Card key={row.id} variant="outlined">
                    <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                      <Stack spacing={0.5}>
                        <Stack direction="row" justifyContent="space-between" spacing={1}>
                          <Chip size="small" label={movementLabel(row.transactionType)} color={movementChip(row.transactionType)} />
                          <Typography variant="caption" color="text.secondary">{new Date(row.createdAt).toLocaleString()}</Typography>
                        </Stack>
                        <Typography variant="body2">{row.businessReference || movementLabel(row.transactionType)}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          Qty {row.quantity} | Before {row.beforeQuantity ?? "-"} | After {row.afterQuantity ?? "-"}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">{row.adjustedByName || "System"}</Typography>
                      </Stack>
                    </CardContent>
                  </Card>
                ))}
              </Stack>
            </>
          ) : (
            <Alert severity="info">Batch details are not available for this movement.</Alert>
          )}
        </Box>
      </Drawer>
    </Stack>
  );
}
