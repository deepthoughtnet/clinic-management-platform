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
import { CompactEmptyState, CompactFilterCard, CompactStatCard, compactChipSx } from "../../components/compact/CompactUi";
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
  const fullRefunds = rows.filter((row) => row.billStatus === "REFUNDED" || row.billStatus === "CANCELLED_REFUNDED").length;

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

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Today" value={refundsToday.length} helper="Refunds issued today" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Refunded" value={formatMoney(totalRefunded)} tone="warning" helper="Total refunded amount" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Partial" value={partialRefunds} tone="info" helper="Bills with partial refunds" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Full" value={fullRefunds} tone="success" helper="Fully refunded bills" /></Grid>
      </Grid>

      <CompactFilterCard
        title="Filters"
        subtitle="Keep the ledger dense and searchable."
        actions={<Button size="small" variant="outlined" onClick={() => void load()}>Apply</Button>}
      >
        <Grid container spacing={1}>
          <Grid size={{ xs: 12, md: 2 }}><TextField size="small" type="date" label="From" value={fromDate} onChange={(e) => setFromDate(e.target.value)} fullWidth InputLabelProps={{ shrink: true }} /></Grid>
          <Grid size={{ xs: 12, md: 2 }}><TextField size="small" type="date" label="To" value={toDate} onChange={(e) => setToDate(e.target.value)} fullWidth InputLabelProps={{ shrink: true }} /></Grid>
          <Grid size={{ xs: 12, md: 2 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="refund-mode-label">Refund Mode</InputLabel>
              <Select labelId="refund-mode-label" label="Refund Mode" value={refundMode} onChange={(e) => setRefundMode(e.target.value)}>
                <MenuItem value="">All</MenuItem>
                {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}><TextField size="small" label="Patient / Bill Search" value={search} onChange={(e) => setSearch(e.target.value)} fullWidth /></Grid>
          <Grid size={{ xs: 12, md: 2 }}><TextField size="small" label="Bill Number" value={billNumber} onChange={(e) => setBillNumber(e.target.value)} fullWidth /></Grid>
          <Grid size={{ xs: 12, md: 1 }} sx={{ display: "flex", alignItems: "stretch" }}>
            <Button variant="outlined" size="small" onClick={() => { setFromDate(""); setToDate(""); setRefundMode(""); setSearch(""); setBillNumber(""); }}>
              Clear
            </Button>
          </Grid>
        </Grid>
      </CompactFilterCard>

      <Card variant="outlined">
        <CardContent sx={{ p: 0 }}>
          {loading ? (
            <CompactEmptyState title="Loading refunds…" subtitle="Fetching the refund ledger." />
          ) : rows.length === 0 ? (
            <CompactEmptyState title="No refunds found" subtitle="Try widening the filters or clearing the search row." />
          ) : (
            <Box sx={{ overflowX: "auto" }}>
              <Table size="small" sx={{ minWidth: 1040 }}>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ py: 0.8 }}>Refund Date</TableCell>
                    <TableCell sx={{ py: 0.8 }}>Patient</TableCell>
                    <TableCell sx={{ py: 0.8 }}>Bill Number</TableCell>
                    <TableCell sx={{ py: 0.8 }} align="right">Refund Amount</TableCell>
                    <TableCell sx={{ py: 0.8 }}>Mode</TableCell>
                    <TableCell sx={{ py: 0.8 }}>Reason</TableCell>
                    <TableCell sx={{ py: 0.8 }}>Refunded By</TableCell>
                    <TableCell sx={{ py: 0.8 }}>Bill Status</TableCell>
                    <TableCell sx={{ py: 0.8 }} align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id} hover>
                      <TableCell sx={{ py: 0.65 }}>{row.refundedAt?.slice(0, 10)}</TableCell>
                      <TableCell sx={{ py: 0.65 }}>{row.patientName || "—"}</TableCell>
                      <TableCell sx={{ py: 0.65 }}>{row.billNumber}</TableCell>
                      <TableCell sx={{ py: 0.65 }} align="right">{formatMoney(row.amount)}</TableCell>
                      <TableCell sx={{ py: 0.65 }}><Chip size="small" label={row.refundMode || "N/A"} sx={compactChipSx} /></TableCell>
                      <TableCell sx={{ py: 0.65, maxWidth: 170, wordBreak: "break-word" }}>{row.reason || "—"}</TableCell>
                      <TableCell sx={{ py: 0.65 }}>{row.refundedBy || "—"}</TableCell>
                      <TableCell sx={{ py: 0.65 }}><Chip size="small" label={row.billStatus} color={row.billStatus === "REFUNDED" || row.billStatus === "CANCELLED_REFUNDED" ? "success" : row.billStatus === "PARTIALLY_REFUNDED" || row.billStatus === "REFUND_PENDING" ? "warning" : "default"} sx={compactChipSx} /></TableCell>
                      <TableCell sx={{ py: 0.65 }} align="right">
                        <Stack direction="row" spacing={0.75} justifyContent="flex-end" flexWrap="wrap">
                          <Button size="small" variant="text" onClick={() => navigate("/billing")}>Open Bill</Button>
                          {canIssueRefund && row.billRefundableAmount > 0 ? <Button size="small" variant="text" onClick={() => { setIssueBillId(row.billId); setIssueAmount(""); setIssueReason(""); setIssueOpen(true); }}>Issue Refund</Button> : null}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
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
