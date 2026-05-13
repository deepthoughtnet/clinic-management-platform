import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
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
import { useAuth } from "../../auth/useAuth";
import {
  getInventoryTransactions,
  getMedicines,
  type InventoryTransaction,
  type InventoryTransactionType,
  type Medicine,
} from "../../api/clinicApi";

const MOVEMENT_TYPES: InventoryTransactionType[] = [
  "OPENING",
  "PURCHASE",
  "ADJUSTMENT",
  "SALE",
  "RETURN",
  "DISPENSED",
  "EXPIRED",
  "CANCELLED_DISPENSE",
  "STOCK_IN",
  "ADJUSTMENT_IN",
  "ADJUSTMENT_OUT",
];

function movementChip(type: string) {
  if (["OPENING", "PURCHASE", "STOCK_IN", "RETURN", "ADJUSTMENT_IN"].includes(type)) return "success" as const;
  if (["SALE", "DISPENSED", "ADJUSTMENT_OUT", "EXPIRED", "CANCELLED_DISPENSE"].includes(type)) return "warning" as const;
  return "default" as const;
}

export default function StockMovementsPage() {
  const auth = useAuth();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [rows, setRows] = React.useState<InventoryTransaction[]>([]);
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [medicineId, setMedicineId] = React.useState("");
  const [movementType, setMovementType] = React.useState<string>("");
  const [fromDate, setFromDate] = React.useState("");
  const [toDate, setToDate] = React.useState("");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [trx, meds] = await Promise.all([
        getInventoryTransactions(auth.accessToken, auth.tenantId),
        getMedicines(auth.accessToken, auth.tenantId),
      ]);
      setRows(trx);
      setMedicines(meds);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load stock movements");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => { void load(); }, [load]);

  const filtered = React.useMemo(() => {
    return rows.filter((row) => {
      if (medicineId && row.medicineId !== medicineId) return false;
      if (movementType && row.transactionType !== movementType) return false;
      const created = new Date(row.createdAt);
      if (fromDate && created < new Date(`${fromDate}T00:00:00`)) return false;
      if (toDate && created > new Date(`${toDate}T23:59:59`)) return false;
      return true;
    });
  }, [rows, medicineId, movementType, fromDate, toDate]);

  const medicineById = React.useMemo(() => new Map(medicines.map((m) => [m.id, m])), [medicines]);

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to access Stock Movements.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Stock Movements</Typography>
          <Typography variant="body2" color="text.secondary">Track stock in/out activity and movement references across dispensing, adjustments, returns, and expiry.</Typography>
        </Box>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField select size="small" fullWidth label="Medicine" value={medicineId} onChange={(e) => setMedicineId(e.target.value)}>
                <MenuItem value="">All</MenuItem>
                {medicines.map((m) => <MenuItem key={m.id} value={m.id}>{m.medicineName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField select size="small" fullWidth label="Movement type" value={movementType} onChange={(e) => setMovementType(e.target.value)}>
                <MenuItem value="">All</MenuItem>
                {MOVEMENT_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 2.5 }}><TextField size="small" fullWidth type="date" label="From" value={fromDate} onChange={(e) => setFromDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 2.5 }}><TextField size="small" fullWidth type="date" label="To" value={toDate} onChange={(e) => setToDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 1 }}><Button size="small" onClick={() => { setMedicineId(""); setMovementType(""); setFromDate(""); setToDate(""); }}>Reset</Button></Grid>
          </Grid>
        </CardContent>
      </Card>

      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card>
          <CardContent>
            {filtered.length === 0 ? <Alert severity="info">No stock movements found for selected filters.</Alert> : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Medicine</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Batch</TableCell>
                    <TableCell align="right">Qty</TableCell>
                    <TableCell>Reference</TableCell>
                    <TableCell>Notes</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filtered.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>{new Date(row.createdAt).toLocaleString()}</TableCell>
                      <TableCell>{medicineById.get(row.medicineId)?.medicineName || row.medicineId}</TableCell>
                      <TableCell><Chip size="small" label={row.transactionType} color={movementChip(row.transactionType)} /></TableCell>
                      <TableCell>{row.stockBatchId || "-"}</TableCell>
                      <TableCell align="right">{row.quantity}</TableCell>
                      <TableCell>
                        {row.referenceType || "-"}
                        {row.referenceId ? <Typography variant="caption" display="block" color="text.secondary">{row.referenceId}</Typography> : null}
                      </TableCell>
                      <TableCell>{row.notes || row.reason || "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      ) : null}
    </Stack>
  );
}
