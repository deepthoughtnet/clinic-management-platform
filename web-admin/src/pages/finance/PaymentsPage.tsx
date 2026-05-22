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
import { CompactEmptyState, CompactFilterCard, CompactStatCard, compactChipSx } from "../../components/compact/CompactUi";
import {
  ReceiptPrintDialog,
  type ReceiptPrintData,
} from "../../components/finance/PrintableBillingDocuments";
import {
  getAppointment,
  getBill,
  getClinicProfile,
  getConsultation,
  getReceiptPdf,
  listPaymentsLedger,
  getPatient,
  sendReceipt,
  type ClinicProfile,
  type PaymentLedgerRow,
  type PaymentMode,
  type Receipt,
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
  const [clinicProfile, setClinicProfile] = React.useState<ClinicProfile | null>(null);
  const [receiptPreview, setReceiptPreview] = React.useState<ReceiptPrintData | null>(null);
  const [receiptPreviewLoading, setReceiptPreviewLoading] = React.useState(false);
  const [receiptAutoPrint, setReceiptAutoPrint] = React.useState(false);

  const canSendReceipt = auth.hasPermission("payment.collect") || auth.hasPermission("notification.send");

  React.useEffect(() => {
    let cancelled = false;
    async function loadClinic() {
      if (!auth.accessToken || !auth.tenantId) return;
      try {
        const profile = await getClinicProfile(auth.accessToken, auth.tenantId);
        if (!cancelled) setClinicProfile(profile);
      } catch {
        if (!cancelled) setClinicProfile(null);
      }
    }
    void loadClinic();
    return () => { cancelled = true; };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    if (receiptAutoPrint && receiptPreview && !receiptPreviewLoading) {
      const handle = window.setTimeout(() => window.print(), 60);
      setReceiptAutoPrint(false);
      return () => window.clearTimeout(handle);
    }
    return undefined;
  }, [receiptAutoPrint, receiptPreview, receiptPreviewLoading]);

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

  async function handleSendReceipt(row: PaymentLedgerRow, channel: "EMAIL" | "WHATSAPP") {
    if (!auth.accessToken || !auth.tenantId || !row.receiptId) return;
    setWorkingId(row.id);
    setError(null);
    setSuccess(null);
    try {
      await sendReceipt(auth.accessToken, auth.tenantId, row.receiptId, channel);
      setSuccess(`Receipt ${channel === "EMAIL" ? "email" : "WhatsApp"} request submitted`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to send receipt");
    } finally {
      setWorkingId(null);
    }
  }

  function buildReceiptStub(row: PaymentLedgerRow): Receipt {
    return {
      id: row.receiptId || row.id,
      tenantId: row.tenantId,
      receiptNumber: row.receiptNumber || "—",
      billId: row.billId,
      paymentId: row.id,
      receiptDate: row.receiptDate || row.paymentDate,
      amount: row.amount,
      createdAt: row.createdAt,
    };
  }

  const loadReceiptPreview = React.useCallback(async (row: PaymentLedgerRow, autoPrint = false) => {
    if (!auth.accessToken || !auth.tenantId || !row.receiptId) return;
    setReceiptPreviewLoading(true);
    setReceiptAutoPrint(autoPrint);
    setReceiptPreview(null);
    try {
      const bill = await getBill(auth.accessToken, auth.tenantId, row.billId).catch(() => null);
      if (!bill) {
        throw new Error("Bill not found for receipt");
      }
      const [patient, appointment, consultation] = await Promise.all([
        getPatient(auth.accessToken, auth.tenantId, bill.patientId).then((result) => result.patient).catch(() => null),
        bill.appointmentId ? getAppointment(auth.accessToken, auth.tenantId, bill.appointmentId).catch(() => null) : Promise.resolve(null),
        bill.consultationId ? getConsultation(auth.accessToken, auth.tenantId, bill.consultationId).catch(() => null) : Promise.resolve(null),
      ]);
      setReceiptPreview({
        clinicProfile,
        bill,
        receipt: buildReceiptStub(row),
        payment: row,
        patient,
        appointment,
        consultation,
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load receipt preview");
    } finally {
      setReceiptPreviewLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, clinicProfile]);

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
    <>
    <Stack className="no-print" spacing={2}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>Payments</Typography>
      {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
      {success && <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert>}

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Today" value={paymentsToday.length} helper="Payments collected today" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Collected" value={formatMoney(totalCollected)} tone="success" helper="Total settled amount" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Pending" value={formatMoney(pendingAmount)} tone="warning" helper="Open due across bills" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><CompactStatCard label="Partial" value={partiallyPaid} tone="info" helper="Bills with partial payment" /></Grid>
      </Grid>

      <CompactFilterCard
        title="Filters"
        subtitle="Use a dense date range and search row to narrow the ledger."
        actions={<Button size="small" variant="outlined" onClick={() => void load()}>Apply</Button>}
      >
        <Grid container spacing={1}>
          <Grid size={{ xs: 12, md: 2 }}><TextField size="small" type="date" label="From" value={fromDate} onChange={(e) => setFromDate(e.target.value)} fullWidth InputLabelProps={{ shrink: true }} /></Grid>
          <Grid size={{ xs: 12, md: 2 }}><TextField size="small" type="date" label="To" value={toDate} onChange={(e) => setToDate(e.target.value)} fullWidth InputLabelProps={{ shrink: true }} /></Grid>
          <Grid size={{ xs: 12, md: 2 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="pay-mode-label">Payment Mode</InputLabel>
              <Select labelId="pay-mode-label" label="Payment Mode" value={paymentMode} onChange={(e) => setPaymentMode(e.target.value)}>
                <MenuItem value="">All</MenuItem>
                {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}><TextField size="small" label="Patient / Bill Search" value={search} onChange={(e) => setSearch(e.target.value)} fullWidth /></Grid>
          <Grid size={{ xs: 12, md: 2 }}><TextField size="small" label="Bill Number" value={billNumber} onChange={(e) => setBillNumber(e.target.value)} fullWidth /></Grid>
          <Grid size={{ xs: 12, md: 1 }} sx={{ display: "flex", alignItems: "stretch" }}>
            <Button variant="outlined" size="small" onClick={() => { setFromDate(""); setToDate(""); setPaymentMode(""); setSearch(""); setBillNumber(""); }}>
              Clear
            </Button>
          </Grid>
        </Grid>
      </CompactFilterCard>

      <Card variant="outlined">
        <CardContent sx={{ p: 0 }}>
          {loading ? (
            <CompactEmptyState title="Loading payments…" subtitle="Fetching the payment ledger." />
          ) : rows.length === 0 ? (
            <CompactEmptyState title="No payments found" subtitle="Try widening the filters or clearing the search row." />
          ) : (
            <Box sx={{ overflowX: "auto", maxHeight: 430, overflowY: "auto" }}>
              <Table stickyHeader size="small" sx={{ minWidth: 1120 }}>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ py: 0.65 }}>Payment Date</TableCell>
                    <TableCell sx={{ py: 0.65 }}>Patient</TableCell>
                    <TableCell sx={{ py: 0.65 }}>Bill Number</TableCell>
                    <TableCell sx={{ py: 0.65 }} align="right">Amount</TableCell>
                    <TableCell sx={{ py: 0.65 }}>Mode</TableCell>
                    <TableCell sx={{ py: 0.65 }}>Reference</TableCell>
                    <TableCell sx={{ py: 0.65 }}>Received By</TableCell>
                    <TableCell sx={{ py: 0.65 }}>Bill Status</TableCell>
                    <TableCell sx={{ py: 0.65 }} align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id} hover>
                      <TableCell sx={{ py: 0.65 }}>{row.paymentDate}</TableCell>
                      <TableCell sx={{ py: 0.65 }}>{row.patientName || "—"}</TableCell>
                      <TableCell sx={{ py: 0.65 }}>{row.billNumber}</TableCell>
                      <TableCell sx={{ py: 0.65 }} align="right">{formatMoney(row.amount)}</TableCell>
                      <TableCell sx={{ py: 0.65 }}><Chip size="small" label={row.paymentMode} sx={compactChipSx} /></TableCell>
                      <TableCell sx={{ py: 0.65, maxWidth: 160, wordBreak: "break-word" }}>{row.referenceNumber || "—"}</TableCell>
                      <TableCell sx={{ py: 0.65 }}>{row.receivedBy || "—"}</TableCell>
                      <TableCell sx={{ py: 0.65 }}><Chip size="small" label={row.billStatus} color={row.billStatus === "PAID" ? "success" : row.billStatus === "PARTIALLY_PAID" ? "warning" : "default"} sx={compactChipSx} /></TableCell>
                      <TableCell sx={{ py: 0.65 }} align="right">
                        <Stack direction="row" spacing={0.75} justifyContent="flex-end" flexWrap="wrap">
                          <Button size="small" variant="text" onClick={() => navigate("/billing")}>Open Bill</Button>
                          {row.receiptId && <Button size="small" variant="text" onClick={() => void loadReceiptPreview(row)}>View Receipt</Button>}
                          {row.receiptId && <Button size="small" variant="text" onClick={() => void loadReceiptPreview(row, true)}>Print Receipt</Button>}
                          {row.receiptId && <Button size="small" variant="text" onClick={() => void handleDownloadReceipt(row)} disabled={workingId === row.id}>Download PDF</Button>}
                          {canSendReceipt && row.receiptId && <Button size="small" variant="text" onClick={() => void handleSendReceipt(row, "EMAIL")} disabled={workingId === row.id}>Send Email</Button>}
                          {canSendReceipt && row.receiptId && <Button size="small" variant="text" onClick={() => void handleSendReceipt(row, "WHATSAPP")} disabled={workingId === row.id}>Send WhatsApp</Button>}
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
    </Stack>
    <ReceiptPrintDialog
      open={Boolean(receiptPreview || receiptPreviewLoading)}
      loading={receiptPreviewLoading}
      data={receiptPreview}
      onClose={() => {
        setReceiptPreview(null);
        setReceiptPreviewLoading(false);
        setReceiptAutoPrint(false);
      }}
      onPrint={() => window.print()}
    />
    </>
  );
}
