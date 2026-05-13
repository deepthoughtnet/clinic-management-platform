import * as React from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
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
  getReceiptPdf,
  listPaymentsLedger,
  sendReceipt,
  type PaymentLedgerRow,
  type PaymentMode,
} from "../../api/clinicApi";

const PAYMENT_MODES: PaymentMode[] = ["CASH", "CARD", "UPI", "PAYTM", "PHONEPE", "GOOGLE_PAY", "BANK_TRANSFER", "CHEQUE", "OTHER"];

function formatMoney(value: number) {
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(value || 0);
}

export default function PaymentsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [rows, setRows] = React.useState<PaymentLedgerRow[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [fromDate, setFromDate] = React.useState("");
  const [toDate, setToDate] = React.useState("");
  const [paymentMode, setPaymentMode] = React.useState("");
  const [search, setSearch] = React.useState("");
  const [billNumber, setBillNumber] = React.useState("");
  const [workingId, setWorkingId] = React.useState<string | null>(null);

  const canSendReceipt = auth.hasPermission("payment.collect") || auth.hasPermission("notification.send");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await listPaymentsLedger(auth.accessToken, auth.tenantId, {
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        paymentMode: paymentMode ? (paymentMode as PaymentMode) : null,
        search: search.trim() || undefined,
        billNumber: billNumber.trim() || undefined,
        size: 300,
      });
      setRows(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load payments");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, fromDate, toDate, paymentMode, search, billNumber]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const today = new Date().toISOString().slice(0, 10);
  const paymentsToday = rows.filter((row) => row.paymentDate === today);
  const totalCollected = rows.reduce((sum, row) => sum + (row.amount || 0), 0);
  const pendingAmount = rows.reduce((sum, row) => sum + (row.billDueAmount || 0), 0);
  const partiallyPaid = rows.filter((row) => row.billStatus === "PARTIALLY_PAID").length;

  async function handleSendReceipt(row: PaymentLedgerRow) {
    if (!auth.accessToken || !auth.tenantId || !row.receiptId) return;
    setWorkingId(row.id);
    setError(null);
    setSuccess(null);
    try {
      await sendReceipt(auth.accessToken, auth.tenantId, row.receiptId, "EMAIL");
      setSuccess("Receipt email request submitted");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to send receipt");
    } finally {
      setWorkingId(null);
    }
  }

  async function handleDownloadReceipt(row: PaymentLedgerRow) {
    if (!auth.accessToken || !auth.tenantId || !row.receiptId) return;
    setWorkingId(row.id);
    setError(null);
    try {
      const file = await getReceiptPdf(auth.accessToken, auth.tenantId, row.receiptId);
      const url = URL.createObjectURL(file.blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file.filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to download receipt");
    } finally {
      setWorkingId(null);
    }
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>Payments</Typography>
      {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
      {success && <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert>}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 3 }}><Card><CardContent><Typography variant="caption">Payments Today</Typography><Typography variant="h6">{paymentsToday.length}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 3 }}><Card><CardContent><Typography variant="caption">Total Collected</Typography><Typography variant="h6">{formatMoney(totalCollected)}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 3 }}><Card><CardContent><Typography variant="caption">Pending Amount</Typography><Typography variant="h6">{formatMoney(pendingAmount)}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 3 }}><Card><CardContent><Typography variant="caption">Partially Paid Bills</Typography><Typography variant="h6">{partiallyPaid}</Typography></CardContent></Card></Grid>
      </Grid>

      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 2 }}><TextField type="date" label="From" value={fromDate} onChange={(e) => setFromDate(e.target.value)} fullWidth InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 2 }}><TextField type="date" label="To" value={toDate} onChange={(e) => setToDate(e.target.value)} fullWidth InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <FormControl fullWidth>
                <InputLabel id="pay-mode-label">Payment Mode</InputLabel>
                <Select labelId="pay-mode-label" label="Payment Mode" value={paymentMode} onChange={(e) => setPaymentMode(e.target.value)}>
                  <MenuItem value="">All</MenuItem>
                  {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField label="Patient / Bill Search" value={search} onChange={(e) => setSearch(e.target.value)} fullWidth /></Grid>
            <Grid size={{ xs: 12, md: 2 }}><TextField label="Bill Number" value={billNumber} onChange={(e) => setBillNumber(e.target.value)} fullWidth /></Grid>
            <Grid size={{ xs: 12, md: 1 }}><Button variant="contained" onClick={() => void load()} fullWidth>Apply</Button></Grid>
          </Grid>
        </CardContent>
      </Card>

      <Card>
        <CardContent sx={{ p: 0 }}>
          {loading ? (
            <Box sx={{ p: 3 }}><Typography color="text.secondary">Loading payments…</Typography></Box>
          ) : rows.length === 0 ? (
            <Box sx={{ p: 3 }}><Typography color="text.secondary">No payments found for current filters.</Typography></Box>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Payment Date</TableCell>
                  <TableCell>Patient</TableCell>
                  <TableCell>Bill Number</TableCell>
                  <TableCell align="right">Amount</TableCell>
                  <TableCell>Payment Mode</TableCell>
                  <TableCell>Reference</TableCell>
                  <TableCell>Received By</TableCell>
                  <TableCell>Bill Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id} hover>
                    <TableCell>{row.paymentDate}</TableCell>
                    <TableCell>{row.patientName || "—"}</TableCell>
                    <TableCell>{row.billNumber}</TableCell>
                    <TableCell align="right">{formatMoney(row.amount)}</TableCell>
                    <TableCell><Chip size="small" label={row.paymentMode} /></TableCell>
                    <TableCell>{row.referenceNumber || "—"}</TableCell>
                    <TableCell>{row.receivedBy || "—"}</TableCell>
                    <TableCell><Chip size="small" label={row.billStatus} color={row.billStatus === "PAID" ? "success" : row.billStatus === "PARTIALLY_PAID" ? "warning" : "default"} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" onClick={() => navigate("/billing")}>Open Bill</Button>
                        {row.receiptId && <Button size="small" onClick={() => void handleDownloadReceipt(row)} disabled={workingId === row.id}>Receipt</Button>}
                        {canSendReceipt && row.receiptId && <Button size="small" onClick={() => void handleSendReceipt(row)} disabled={workingId === row.id}>Send Email</Button>}
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
