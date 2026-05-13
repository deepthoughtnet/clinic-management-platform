import * as React from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
  addBillRefund,
  listRefundsLedger,
  type PaymentMode,
  type RefundLedgerRow,
} from "../../api/clinicApi";

const PAYMENT_MODES: PaymentMode[] = ["CASH", "CARD", "UPI", "PAYTM", "PHONEPE", "GOOGLE_PAY", "BANK_TRANSFER", "CHEQUE", "OTHER"];

function formatMoney(value: number) {
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(value || 0);
}

export default function RefundsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [rows, setRows] = React.useState<RefundLedgerRow[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [fromDate, setFromDate] = React.useState("");
  const [toDate, setToDate] = React.useState("");
  const [refundMode, setRefundMode] = React.useState("");
  const [search, setSearch] = React.useState("");
  const [billNumber, setBillNumber] = React.useState("");
  const [issueOpen, setIssueOpen] = React.useState(false);
  const [issueBillId, setIssueBillId] = React.useState<string | null>(null);
  const [issueAmount, setIssueAmount] = React.useState("");
  const [issueReason, setIssueReason] = React.useState("");
  const [issueMode, setIssueMode] = React.useState<PaymentMode>("CASH");
  const [working, setWorking] = React.useState(false);

  const canIssueRefund = auth.hasPermission("billing.create") || auth.hasPermission("payment.collect");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await listRefundsLedger(auth.accessToken, auth.tenantId, {
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        refundMode: refundMode ? (refundMode as PaymentMode) : null,
        search: search.trim() || undefined,
        billNumber: billNumber.trim() || undefined,
        size: 300,
      });
      setRows(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load refunds");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, fromDate, toDate, refundMode, search, billNumber]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const today = new Date().toISOString().slice(0, 10);
  const refundsToday = rows.filter((row) => row.refundedAt?.slice(0, 10) === today);
  const totalRefunded = rows.reduce((sum, row) => sum + (row.amount || 0), 0);
  const partialRefunds = rows.filter((row) => row.billStatus === "PARTIALLY_REFUNDED").length;
  const fullRefunds = rows.filter((row) => row.billStatus === "REFUNDED").length;

  async function submitRefund() {
    if (!auth.accessToken || !auth.tenantId || !issueBillId) return;
    setWorking(true);
    setError(null);
    try {
      await addBillRefund(auth.accessToken, auth.tenantId, issueBillId, {
        paymentId: null,
        amount: Number(issueAmount || "0"),
        reason: issueReason.trim(),
        refundMode: issueMode,
        refundedAt: null,
        notes: null,
      });
      setIssueOpen(false);
      setIssueBillId(null);
      setIssueAmount("");
      setIssueReason("");
      setSuccess("Refund issued");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to issue refund");
    } finally {
      setWorking(false);
    }
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>Refunds</Typography>
      {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
      {success && <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert>}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 3 }}><Card><CardContent><Typography variant="caption">Refunds Today</Typography><Typography variant="h6">{refundsToday.length}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 3 }}><Card><CardContent><Typography variant="caption">Total Refunded</Typography><Typography variant="h6">{formatMoney(totalRefunded)}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 3 }}><Card><CardContent><Typography variant="caption">Partial Refunds</Typography><Typography variant="h6">{partialRefunds}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 3 }}><Card><CardContent><Typography variant="caption">Full Refunds</Typography><Typography variant="h6">{fullRefunds}</Typography></CardContent></Card></Grid>
      </Grid>

      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 2 }}><TextField type="date" label="From" value={fromDate} onChange={(e) => setFromDate(e.target.value)} fullWidth InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 2 }}><TextField type="date" label="To" value={toDate} onChange={(e) => setToDate(e.target.value)} fullWidth InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <FormControl fullWidth>
                <InputLabel id="refund-mode-label">Refund Mode</InputLabel>
                <Select labelId="refund-mode-label" label="Refund Mode" value={refundMode} onChange={(e) => setRefundMode(e.target.value)}>
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
            <Box sx={{ p: 3 }}><Typography color="text.secondary">Loading refunds…</Typography></Box>
          ) : rows.length === 0 ? (
            <Box sx={{ p: 3 }}><Typography color="text.secondary">No refunds found for current filters.</Typography></Box>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Refund Date</TableCell>
                  <TableCell>Patient</TableCell>
                  <TableCell>Bill Number</TableCell>
                  <TableCell align="right">Refund Amount</TableCell>
                  <TableCell>Refund Mode</TableCell>
                  <TableCell>Reason</TableCell>
                  <TableCell>Refunded By</TableCell>
                  <TableCell>Bill Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id} hover>
                    <TableCell>{row.refundedAt?.slice(0, 10)}</TableCell>
                    <TableCell>{row.patientName || "—"}</TableCell>
                    <TableCell>{row.billNumber}</TableCell>
                    <TableCell align="right">{formatMoney(row.amount)}</TableCell>
                    <TableCell><Chip size="small" label={row.refundMode || "N/A"} /></TableCell>
                    <TableCell>{row.reason || "—"}</TableCell>
                    <TableCell>{row.refundedBy || "—"}</TableCell>
                    <TableCell><Chip size="small" label={row.billStatus} color={row.billStatus === "REFUNDED" ? "success" : row.billStatus === "PARTIALLY_REFUNDED" ? "warning" : "default"} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" onClick={() => navigate("/billing")}>Open Bill</Button>
                        {canIssueRefund && <Button size="small" onClick={() => { setIssueBillId(row.billId); setIssueAmount(""); setIssueReason(""); setIssueOpen(true); }}>Issue Refund</Button>}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={issueOpen} onClose={() => !working && setIssueOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Issue Refund</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Refund Amount" type="number" value={issueAmount} onChange={(e) => setIssueAmount(e.target.value)} fullWidth />
            <FormControl fullWidth>
              <InputLabel id="issue-mode-label">Refund Mode</InputLabel>
              <Select labelId="issue-mode-label" label="Refund Mode" value={issueMode} onChange={(e) => setIssueMode(e.target.value as PaymentMode)}>
                {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
              </Select>
            </FormControl>
            <TextField label="Reason" value={issueReason} onChange={(e) => setIssueReason(e.target.value)} fullWidth multiline minRows={2} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setIssueOpen(false)} disabled={working}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => void submitRefund()}
            disabled={working || !issueBillId || Number(issueAmount || "0") <= 0 || !issueReason.trim()}
          >
            Confirm Refund
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
