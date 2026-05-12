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
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
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
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";

import { useAuth } from "../../auth/useAuth";
import {
  addBillPayment,
  addBillRefund,
  cancelBill,
  createBill,
  getBillPdf,
  getReceiptPdf,
  issueBill,
  listBillPayments,
  listBillReceipts,
  listBillRefunds,
  sendBillInvoiceEmail,
  sendReceipt,
  searchBills,
  searchPatients,
  type Bill,
  type BillInput,
  type BillItemType,
  type BillLine,
  type DiscountType,
  type Payment,
  type PaymentMode,
  type Patient,
  type Receipt,
  type Refund,
} from "../../api/clinicApi";

type BillLineForm = {
  itemType: BillItemType;
  itemName: string;
  quantity: string;
  unitPrice: string;
  referenceId: string;
  sortOrder: string;
};

type BillFormState = {
  patientId: string;
  consultationId: string;
  appointmentId: string;
  billDate: string;
  discountType: DiscountType;
  discountValue: string;
  discountReason: string;
  taxAmount: string;
  notes: string;
  lines: BillLineForm[];
};

type PaymentFormState = {
  paymentDate: string;
  amount: string;
  paymentMode: PaymentMode;
  referenceNumber: string;
  notes: string;
};

type RefundFormState = {
  amount: string;
  refundMode: PaymentMode;
  reason: string;
  notes: string;
};

const BILL_ITEM_TYPES: BillItemType[] = ["CONSULTATION", "MEDICINE", "TEST", "VACCINATION", "PROCEDURE", "OTHER"];
const PAYMENT_MODES: PaymentMode[] = ["CASH", "CARD", "UPI", "PAYTM", "PHONEPE", "GOOGLE_PAY", "BANK_TRANSFER", "CHEQUE", "OTHER"];
const DISCOUNT_TYPES: DiscountType[] = ["NONE", "AMOUNT", "PERCENTAGE"];

function emptyBillForm(): BillFormState {
  return {
    patientId: "",
    consultationId: "",
    appointmentId: "",
    billDate: new Date().toISOString().slice(0, 10),
    discountType: "NONE",
    discountValue: "",
    discountReason: "",
    taxAmount: "",
    notes: "",
    lines: [{ itemType: "CONSULTATION", itemName: "", quantity: "1", unitPrice: "", referenceId: "", sortOrder: "1" }],
  };
}

function emptyPaymentForm(): PaymentFormState {
  return { paymentDate: new Date().toISOString().slice(0, 10), amount: "", paymentMode: "CASH", referenceNumber: "", notes: "" };
}

function emptyRefundForm(): RefundFormState {
  return { amount: "", refundMode: "CASH", reason: "", notes: "" };
}

function toBillInput(form: BillFormState): BillInput {
  return {
    patientId: form.patientId,
    consultationId: form.consultationId.trim() || null,
    appointmentId: form.appointmentId.trim() || null,
    billDate: form.billDate,
    discountType: form.discountType,
    discountValue: form.discountType === "NONE" ? null : (form.discountValue.trim() ? Number(form.discountValue) : 0),
    discountReason: form.discountType === "NONE" ? null : (form.discountReason.trim() || null),
    taxAmount: form.taxAmount.trim() ? Number(form.taxAmount) : null,
    notes: form.notes.trim() || null,
    lines: form.lines.filter((row) => row.itemName.trim()).map((row, index) => ({
      itemType: row.itemType,
      itemName: row.itemName.trim(),
      quantity: Number(row.quantity || "1"),
      unitPrice: Number(row.unitPrice || "0"),
      referenceId: row.referenceId.trim() || null,
      sortOrder: row.sortOrder.trim() ? Number(row.sortOrder) : index + 1,
    })),
  };
}

function statusColor(status: Bill["status"]) {
  switch (status) {
    case "PAID": return "success";
    case "PARTIALLY_PAID":
    case "PARTIALLY_REFUNDED": return "warning";
    case "UNPAID":
    case "ISSUED": return "info";
    case "REFUNDED":
    case "CANCELLED":
    case "DRAFT":
    default: return "default";
  }
}

function lineTotal(row: BillLineForm) {
  return (Number(row.quantity || "0") * Number(row.unitPrice || "0")).toFixed(2);
}

export default function BillsPage() {
  const auth = useAuth();
  const [bills, setBills] = React.useState<Bill[]>([]);
  const [selectedBill, setSelectedBill] = React.useState<Bill | null>(null);
  const [payments, setPayments] = React.useState<Payment[]>([]);
  const [receipts, setReceipts] = React.useState<Receipt[]>([]);
  const [refunds, setRefunds] = React.useState<Refund[]>([]);
  const [patients, setPatients] = React.useState<Patient[]>([]);
  const [patientQuery, setPatientQuery] = React.useState("");
  const [patientSearchResults, setPatientSearchResults] = React.useState<Patient[]>([]);
  const [billFilterPatient, setBillFilterPatient] = React.useState("");
  const [billFilterStatus, setBillFilterStatus] = React.useState<string>("");
  const [billFilterText, setBillFilterText] = React.useState("");
  const [billFilterMode, setBillFilterMode] = React.useState<string>("");
  const [billFilterFromDate, setBillFilterFromDate] = React.useState("");
  const [billFilterToDate, setBillFilterToDate] = React.useState("");
  const [form, setForm] = React.useState<BillFormState>(emptyBillForm());
  const [paymentForm, setPaymentForm] = React.useState<PaymentFormState>(emptyPaymentForm());
  const [refundForm, setRefundForm] = React.useState<RefundFormState>(emptyRefundForm());
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [paymentOpen, setPaymentOpen] = React.useState(false);
  const [refundOpen, setRefundOpen] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [workingId, setWorkingId] = React.useState<string | null>(null);

  const canCreateBill = auth.hasPermission("billing.create");
  const canUpdateBill = auth.hasPermission("billing.update") || auth.hasPermission("billing.create");
  const canCollectPayment = auth.hasPermission("payment.collect");
  const canSendReceipt = canCollectPayment || auth.hasPermission("notification.send");
  const canRefund = canCreateBill || canCollectPayment;
  const canSendInvoice = canCreateBill || canCollectPayment || auth.hasPermission("notification.send");

  const loadBills = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const rows = await searchBills(auth.accessToken, auth.tenantId, {
      patientId: billFilterPatient.trim() || undefined,
      status: billFilterStatus ? (billFilterStatus as Bill["status"]) : null,
      fromDate: billFilterFromDate || undefined,
      toDate: billFilterToDate || undefined,
      paymentMode: billFilterMode ? (billFilterMode as PaymentMode) : null,
    });
    const filtered = billFilterText.trim().toLowerCase();
    setBills(filtered
      ? rows.filter((b) => `${b.billNumber} ${b.patientName || ""} ${b.patientNumber || ""}`.toLowerCase().includes(filtered))
      : rows);
  }, [auth.accessToken, auth.tenantId, billFilterPatient, billFilterStatus, billFilterFromDate, billFilterToDate, billFilterMode, billFilterText]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!auth.accessToken || !auth.tenantId) { setLoading(false); return; }
      setLoading(true);
      setError(null);
      try {
        const [billRows, patientRows] = await Promise.all([
          searchBills(auth.accessToken, auth.tenantId, {}),
          searchPatients(auth.accessToken, auth.tenantId, { active: true }),
        ]);
        if (!cancelled) { setBills(billRows); setPatients(patientRows); }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load billing data");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void bootstrap();
    return () => { cancelled = true; };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    let cancelled = false;
    const handle = window.setTimeout(async () => {
      if (!auth.accessToken || !auth.tenantId || patientQuery.trim().length < 2) { setPatientSearchResults([]); return; }
      try {
        const term = patientQuery.trim();
        const rows = await searchPatients(auth.accessToken, auth.tenantId, {
          patientNumber: term.toUpperCase().startsWith("PAT-") ? term : undefined,
          mobile: /^\d{6,}$/.test(term) ? term : undefined,
          name: term.toUpperCase().startsWith("PAT-") || /^\d{6,}$/.test(term) ? undefined : term,
          active: true,
        });
        if (!cancelled) setPatientSearchResults(rows);
      } catch {
        if (!cancelled) setPatientSearchResults([]);
      }
    }, 300);
    return () => { cancelled = true; window.clearTimeout(handle); };
  }, [auth.accessToken, auth.tenantId, patientQuery]);

  const refreshSelectedBill = React.useCallback(async (billId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    const [billRows, paymentRows, receiptRows, refundRows] = await Promise.all([
      searchBills(auth.accessToken, auth.tenantId, {
        patientId: billFilterPatient.trim() || undefined,
        status: billFilterStatus ? (billFilterStatus as Bill["status"]) : null,
        fromDate: billFilterFromDate || undefined,
        toDate: billFilterToDate || undefined,
        paymentMode: billFilterMode ? (billFilterMode as PaymentMode) : null,
      }),
      listBillPayments(auth.accessToken, auth.tenantId, billId),
      listBillReceipts(auth.accessToken, auth.tenantId, billId),
      listBillRefunds(auth.accessToken, auth.tenantId, billId),
    ]);
    setBills(billRows);
    setPayments(paymentRows);
    setReceipts(receiptRows);
    setRefunds(refundRows);
    setSelectedBill(billRows.find((bill) => bill.id === billId) || null);
  }, [auth.accessToken, auth.tenantId, billFilterPatient, billFilterStatus, billFilterFromDate, billFilterToDate, billFilterMode]);

  const selectBill = async (bill: Bill) => {
    setSelectedBill(bill);
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const [paymentRows, receiptRows, refundRows] = await Promise.all([
        listBillPayments(auth.accessToken, auth.tenantId, bill.id),
        listBillReceipts(auth.accessToken, auth.tenantId, bill.id),
        listBillRefunds(auth.accessToken, auth.tenantId, bill.id),
      ]);
      setPayments(paymentRows);
      setReceipts(receiptRows);
      setRefunds(refundRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load bill detail");
    }
  };

  const refundableAmount = selectedBill ? Math.max(0, selectedBill.paidAmount - selectedBill.refundedAmount) : 0;

  const createNewBill = async () => {
    if (!auth.accessToken || !auth.tenantId || !form.patientId) { setError("Select a patient before creating a bill."); return; }
    if (form.discountType !== "NONE" && !form.discountReason.trim() && Number(form.discountValue || "0") > 0) {
      setError("Discount reason is required when discount > 0.");
      return;
    }
    setSaving(true); setError(null); setSuccess(null);
    try {
      const saved = await createBill(auth.accessToken, auth.tenantId, toBillInput(form));
      setSuccess("Bill created");
      setForm(emptyBillForm());
      await loadBills();
      await selectBill(saved);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create bill");
    } finally { setSaving(false); }
  };

  const submitRefund = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedBill) return;
    const amount = Number(refundForm.amount || "0");
    if (amount <= 0) { setError("Refund amount must be greater than 0."); return; }
    if (amount > refundableAmount) { setError("Refund amount cannot exceed refundable amount."); return; }
    if (!refundForm.reason.trim()) { setError("Refund reason is required."); return; }
    setSaving(true); setError(null); setSuccess(null);
    try {
      await addBillRefund(auth.accessToken, auth.tenantId, selectedBill.id, {
        paymentId: payments[0]?.id || null,
        amount,
        reason: refundForm.reason.trim(),
        refundMode: refundForm.refundMode,
        refundedAt: null,
        notes: refundForm.notes.trim() || null,
      });
      setRefundOpen(false);
      setRefundForm(emptyRefundForm());
      await refreshSelectedBill(selectedBill.id);
      setSuccess("Refund recorded");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to refund");
    } finally { setSaving(false); }
  };

  const sendInvoiceAction = async (bill: Bill) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setWorkingId(bill.id);
    try {
      const result = await sendBillInvoiceEmail(auth.accessToken, auth.tenantId, bill.id);
      await refreshSelectedBill(bill.id);
      setSuccess(result.message || "Invoice email sent");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send invoice email");
    } finally { setWorkingId(null); }
  };

  const openPaymentDialog = (bill: Bill) => {
    setSelectedBill(bill);
    setPaymentForm({ ...emptyPaymentForm(), amount: bill.dueAmount > 0 ? bill.dueAmount.toFixed(2) : bill.totalAmount.toFixed(2) });
    setPaymentOpen(true);
  };

  const submitPayment = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedBill) return;
    setSaving(true); setError(null); setSuccess(null);
    if (paymentForm.paymentMode !== "CASH" && !paymentForm.referenceNumber.trim()) {
      setSaving(false); setError("Reference number is required for non-cash payments."); return;
    }
    try {
      await addBillPayment(auth.accessToken, auth.tenantId, selectedBill.id, {
        paymentDate: paymentForm.paymentDate,
        amount: Number(paymentForm.amount || "0"),
        paymentMode: paymentForm.paymentMode,
        referenceNumber: paymentForm.referenceNumber.trim() || null,
        notes: paymentForm.notes.trim() || null,
      });
      setPaymentOpen(false);
      await refreshSelectedBill(selectedBill.id);
      setSuccess("Payment collected");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to collect payment");
    } finally { setSaving(false); }
  };

  const issueCurrentBill = async (bill: Bill) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setWorkingId(bill.id);
    try { const updated = await issueBill(auth.accessToken, auth.tenantId, bill.id); await refreshSelectedBill(updated.id); setSuccess("Bill issued"); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to issue bill"); }
    finally { setWorkingId(null); }
  };

  const cancelCurrentBill = async (bill: Bill) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setWorkingId(bill.id);
    try { const updated = await cancelBill(auth.accessToken, auth.tenantId, bill.id); await refreshSelectedBill(updated.id); setSuccess("Bill cancelled"); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to cancel bill"); }
    finally { setWorkingId(null); }
  };

  const openBillPdf = async (bill: Bill) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try { const { blob } = await getBillPdf(auth.accessToken, auth.tenantId, bill.id); const url = URL.createObjectURL(blob); window.open(url, "_blank", "noopener,noreferrer"); window.setTimeout(() => URL.revokeObjectURL(url), 60000); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to open bill PDF"); }
  };

  const openReceiptPdf = async (receipt: Receipt) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try { const { blob } = await getReceiptPdf(auth.accessToken, auth.tenantId, receipt.id); const url = URL.createObjectURL(blob); window.open(url, "_blank", "noopener,noreferrer"); window.setTimeout(() => URL.revokeObjectURL(url), 60000); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to open receipt PDF"); }
  };

  const sendReceiptAction = async (receipt: Receipt, channel: "email" | "whatsapp") => {
    if (!auth.accessToken || !auth.tenantId) return;
    setWorkingId(receipt.id);
    try { await sendReceipt(auth.accessToken, auth.tenantId, receipt.id, channel); setSuccess(`Receipt sent via ${channel}`); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to send receipt"); }
    finally { setWorkingId(null); }
  };

  if (!auth.tenantId) return <Alert severity="warning">No tenant is selected for this session.</Alert>;

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>Billing</Typography>
          <Typography variant="body2" color="text.secondary">Bills, payments, receipts, and clinic-friendly charge capture.</Typography>
        </Box>
        <Button variant="outlined" onClick={() => void loadBills()}>Refresh</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}

      <Grid container spacing={2}>
        {canCreateBill ? <Grid size={{ xs: 12, lg: 5 }}><Card><CardContent><Stack spacing={2}>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>Create bill</Typography>
          <TextField label="Search patient" value={patientQuery} onChange={(e) => setPatientQuery(e.target.value)} helperText="Search by patient number, mobile, or name" />
          {patientSearchResults.length > 0 && !form.patientId ? <Card variant="outlined"><List dense disablePadding>{patientSearchResults.map((patient) => (
            <ListItemButton key={patient.id} onClick={() => setForm((current) => ({ ...current, patientId: patient.id }))}><ListItemText primary={`${patient.firstName} ${patient.lastName}`} secondary={`${patient.patientNumber} • ${patient.mobile}`} /></ListItemButton>
          ))}</List></Card> : null}
          {form.patientId ? <Chip label={patients.find((p) => p.id === form.patientId)?.patientNumber || form.patientId} onDelete={() => setForm((c) => ({ ...c, patientId: "" }))} /> : null}

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Bill date" type="date" value={form.billDate} onChange={(e) => setForm((c) => ({ ...c, billDate: e.target.value }))} InputLabelProps={{ shrink: true }} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <FormControl fullWidth><InputLabel id="discount-type-label">Discount type</InputLabel><Select labelId="discount-type-label" label="Discount type" value={form.discountType} onChange={(e) => setForm((c) => ({ ...c, discountType: e.target.value as DiscountType }))}>{DISCOUNT_TYPES.map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}</Select></FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label={form.discountType === "PERCENTAGE" ? "Discount (%)" : "Discount value"} value={form.discountValue} onChange={(e) => setForm((c) => ({ ...c, discountValue: e.target.value }))} disabled={form.discountType === "NONE"} /></Grid>
            <Grid size={12}><TextField fullWidth label="Discount reason" value={form.discountReason} onChange={(e) => setForm((c) => ({ ...c, discountReason: e.target.value }))} disabled={form.discountType === "NONE"} required={form.discountType !== "NONE" && Number(form.discountValue || "0") > 0} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Tax" value={form.taxAmount} onChange={(e) => setForm((c) => ({ ...c, taxAmount: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 8 }}><TextField fullWidth label="Consultation ID" value={form.consultationId} onChange={(e) => setForm((c) => ({ ...c, consultationId: e.target.value }))} /></Grid>
            <Grid size={12}><TextField fullWidth label="Appointment ID" value={form.appointmentId} onChange={(e) => setForm((c) => ({ ...c, appointmentId: e.target.value }))} /></Grid>
            <Grid size={12}><TextField fullWidth label="Notes" value={form.notes} onChange={(e) => setForm((c) => ({ ...c, notes: e.target.value }))} multiline minRows={2} /></Grid>
          </Grid>

          <Stack spacing={1.5}><Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}><Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Line items</Typography><Button startIcon={<AddRoundedIcon />} onClick={() => setForm((c) => ({ ...c, lines: [...c.lines, { itemType: "OTHER", itemName: "", quantity: "1", unitPrice: "", referenceId: "", sortOrder: String(c.lines.length + 1) }] }))}>Add line</Button></Box>
            {form.lines.map((row, index) => <Card key={`${index}-${row.sortOrder}`} variant="outlined"><CardContent><Stack spacing={1.5}><Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1 }}><Chip size="small" label={`Line ${index + 1}`} /><IconButton size="small" onClick={() => setForm((c) => c.lines.length === 1 ? c : ({ ...c, lines: c.lines.filter((_, i) => i !== index) }))} disabled={form.lines.length === 1}><DeleteOutlineRoundedIcon fontSize="small" /></IconButton></Box>
              <Grid container spacing={1.5}>
                <Grid size={{ xs: 12, md: 4 }}><FormControl fullWidth><InputLabel id={`bill-line-type-${index}`}>Type</InputLabel><Select labelId={`bill-line-type-${index}`} label="Type" value={row.itemType} onChange={(e) => setForm((c) => { const lines = c.lines.slice(); lines[index] = { ...lines[index], itemType: String(e.target.value) as BillItemType }; return { ...c, lines }; })}>{BILL_ITEM_TYPES.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl></Grid>
                <Grid size={{ xs: 12, md: 8 }}><TextField fullWidth label="Item name" value={row.itemName} onChange={(e) => setForm((c) => { const lines = c.lines.slice(); lines[index] = { ...lines[index], itemName: e.target.value }; return { ...c, lines }; })} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Qty" value={row.quantity} onChange={(e) => setForm((c) => { const lines = c.lines.slice(); lines[index] = { ...lines[index], quantity: e.target.value }; return { ...c, lines }; })} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Unit price" value={row.unitPrice} onChange={(e) => setForm((c) => { const lines = c.lines.slice(); lines[index] = { ...lines[index], unitPrice: e.target.value }; return { ...c, lines }; })} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Reference ID" value={row.referenceId} onChange={(e) => setForm((c) => { const lines = c.lines.slice(); lines[index] = { ...lines[index], referenceId: e.target.value }; return { ...c, lines }; })} /></Grid>
                <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Sort order" value={row.sortOrder} onChange={(e) => setForm((c) => { const lines = c.lines.slice(); lines[index] = { ...lines[index], sortOrder: e.target.value }; return { ...c, lines }; })} /></Grid>
              </Grid>
              <Typography variant="caption" color="text.secondary">Line total: {lineTotal(row)}</Typography>
            </Stack></CardContent></Card>)}
          </Stack>
          <Button variant="contained" disabled={saving} onClick={() => void createNewBill()}>{saving ? "Saving..." : "Create Bill"}</Button>
        </Stack></CardContent></Card></Grid> : null}

        <Grid size={{ xs: 12, lg: canCreateBill ? 7 : 12 }}>
          <Card><CardContent><Stack spacing={2}><Typography variant="h6" sx={{ fontWeight: 800 }}>Bills</Typography>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Patient ID" value={billFilterPatient} onChange={(e) => setBillFilterPatient(e.target.value)} /></Grid>
              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Patient/Bill search" value={billFilterText} onChange={(e) => setBillFilterText(e.target.value)} /></Grid>
              <Grid size={{ xs: 12, md: 4 }}><FormControl fullWidth><InputLabel id="bill-status-filter-label">Status</InputLabel><Select labelId="bill-status-filter-label" label="Status" value={billFilterStatus} onChange={(e) => setBillFilterStatus(String(e.target.value))}><MenuItem value="">All</MenuItem>{["DRAFT","UNPAID","ISSUED","PARTIALLY_PAID","PAID","PARTIALLY_REFUNDED","REFUNDED","CANCELLED"].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select></FormControl></Grid>
              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="date" label="From" value={billFilterFromDate} onChange={(e) => setBillFilterFromDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
              <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="date" label="To" value={billFilterToDate} onChange={(e) => setBillFilterToDate(e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
              <Grid size={{ xs: 12, md: 3 }}><FormControl fullWidth><InputLabel id="payment-mode-filter-label">Payment mode</InputLabel><Select labelId="payment-mode-filter-label" label="Payment mode" value={billFilterMode} onChange={(e) => setBillFilterMode(String(e.target.value))}><MenuItem value="">All</MenuItem>{PAYMENT_MODES.map((m) => <MenuItem key={m} value={m}>{m}</MenuItem>)}</Select></FormControl></Grid>
              <Grid size={{ xs: 12, md: 3 }} sx={{ display: "flex", gap: 1 }}>
                <Button variant="outlined" fullWidth onClick={() => void loadBills()}>Filter</Button>
                <Button variant="text" fullWidth onClick={() => { setBillFilterPatient(""); setBillFilterStatus(""); setBillFilterText(""); setBillFilterMode(""); setBillFilterFromDate(""); setBillFilterToDate(""); }}>Clear</Button>
              </Grid>
            </Grid>

            {loading ? <Box sx={{ display: "grid", placeItems: "center", minHeight: 240 }}><CircularProgress /></Box> : bills.length === 0 ? <Alert severity="info">No bills were found.</Alert> : (
              <Table size="small"><TableHead><TableRow><TableCell>Bill</TableCell><TableCell>Patient</TableCell><TableCell>Status</TableCell><TableCell align="right">Total</TableCell><TableCell align="right">Paid</TableCell><TableCell align="right">Refunded</TableCell><TableCell align="right">Due</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>
                {bills.map((bill) => <TableRow key={bill.id} hover selected={selectedBill?.id === bill.id}><TableCell><Stack spacing={0.25}><Typography variant="body2" sx={{ fontWeight: 700 }}>{bill.billNumber}</Typography><Typography variant="caption" color="text.secondary">{bill.billDate}</Typography></Stack></TableCell><TableCell>{bill.patientName || bill.patientNumber || bill.patientId}</TableCell><TableCell><Chip size="small" label={bill.status} color={statusColor(bill.status)} /></TableCell><TableCell align="right">{bill.totalAmount.toFixed(2)}</TableCell><TableCell align="right">{bill.paidAmount.toFixed(2)}</TableCell><TableCell align="right">{bill.refundedAmount.toFixed(2)}</TableCell><TableCell align="right">{bill.dueAmount.toFixed(2)}</TableCell><TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                  <Button size="small" onClick={() => void selectBill(bill)}>View</Button>
                  {canUpdateBill ? <Button size="small" onClick={() => void issueCurrentBill(bill)} disabled={workingId === bill.id || bill.status !== "DRAFT"}>Issue</Button> : null}
                  {canCollectPayment ? <Button size="small" onClick={() => void openPaymentDialog(bill)} disabled={bill.status === "PAID" || bill.status === "CANCELLED" || bill.dueAmount <= 0}>Add payment</Button> : null}
                  {canRefund ? <Button size="small" onClick={() => { setSelectedBill(bill); setRefundOpen(true); }} disabled={bill.status === "CANCELLED" || (bill.paidAmount - bill.refundedAmount) <= 0}>Refund</Button> : null}
                  <Button size="small" onClick={() => void openBillPdf(bill)}>Invoice PDF</Button>
                  {canSendInvoice ? <Button size="small" disabled={workingId === bill.id || !bill.patientId} onClick={() => void sendInvoiceAction(bill)}>Send invoice email</Button> : null}
                  {canUpdateBill ? <Button size="small" onClick={() => void cancelCurrentBill(bill)} disabled={workingId === bill.id || bill.status === "PAID"}>Cancel</Button> : null}
                </Stack></TableCell></TableRow>)}
              </TableBody></Table>
            )}
          </Stack></CardContent></Card>

          {selectedBill ? <Card sx={{ mt: 2 }}><CardContent><Stack spacing={2}><Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}><Box><Typography variant="h6" sx={{ fontWeight: 800 }}>{selectedBill.billNumber}</Typography><Typography variant="body2" color="text.secondary">{selectedBill.patientName || selectedBill.patientNumber || selectedBill.patientId}</Typography></Box><Chip label={selectedBill.status} color={statusColor(selectedBill.status)} /></Box>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 3 }}><Typography variant="body2">Subtotal: {selectedBill.subtotalAmount.toFixed(2)}</Typography></Grid>
              <Grid size={{ xs: 12, md: 3 }}><Typography variant="body2">Discount: {selectedBill.discountType} ({selectedBill.discountValue.toFixed(2)})</Typography></Grid>
              <Grid size={{ xs: 12, md: 3 }}><Typography variant="body2">Discount amount: {selectedBill.discountAmount.toFixed(2)}</Typography></Grid>
              <Grid size={{ xs: 12, md: 3 }}><Typography variant="body2">Final total: {selectedBill.totalAmount.toFixed(2)}</Typography></Grid>
              <Grid size={{ xs: 12, md: 3 }}><Typography variant="body2">Paid amount: {selectedBill.paidAmount.toFixed(2)}</Typography></Grid>
              <Grid size={{ xs: 12, md: 3 }}><Typography variant="body2">Refunded amount: {selectedBill.refundedAmount.toFixed(2)}</Typography></Grid>
              <Grid size={{ xs: 12, md: 3 }}><Typography variant="body2">Net paid: {selectedBill.netPaidAmount.toFixed(2)}</Typography></Grid>
              <Grid size={{ xs: 12, md: 3 }}><Typography variant="body2">Balance: {selectedBill.dueAmount.toFixed(2)}</Typography></Grid>
            </Grid>
            {selectedBill.discountReason ? <Alert severity="info">Discount reason: {selectedBill.discountReason}</Alert> : null}

            <Stack spacing={1}><Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Line items</Typography><Table size="small"><TableHead><TableRow><TableCell>Type</TableCell><TableCell>Name</TableCell><TableCell align="right">Qty</TableCell><TableCell align="right">Unit</TableCell><TableCell align="right">Total</TableCell></TableRow></TableHead><TableBody>{selectedBill.lines.map((line: BillLine) => <TableRow key={line.id || `${line.itemName}-${line.sortOrder}`}><TableCell>{line.itemType}</TableCell><TableCell>{line.itemName}</TableCell><TableCell align="right">{line.quantity}</TableCell><TableCell align="right">{line.unitPrice.toFixed(2)}</TableCell><TableCell align="right">{line.totalPrice.toFixed(2)}</TableCell></TableRow>)}</TableBody></Table></Stack>

            <Stack spacing={1}><Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Payment history</Typography>{payments.length === 0 ? <Alert severity="info">No payments recorded for this bill.</Alert> : <Table size="small"><TableHead><TableRow><TableCell>Date</TableCell><TableCell>Mode</TableCell><TableCell>Reference</TableCell><TableCell>Received by</TableCell><TableCell>Notes</TableCell><TableCell align="right">Amount</TableCell><TableCell align="right">Receipt</TableCell></TableRow></TableHead><TableBody>{payments.map((payment) => <TableRow key={payment.id}><TableCell>{payment.paymentDateTime || payment.paymentDate}</TableCell><TableCell>{payment.paymentMode}</TableCell><TableCell>{payment.referenceNumber || "-"}</TableCell><TableCell>{payment.receivedBy || "-"}</TableCell><TableCell>{payment.notes || "-"}</TableCell><TableCell align="right">{payment.amount.toFixed(2)}</TableCell><TableCell align="right">{payment.receiptId ? <Button size="small" onClick={() => void openReceiptPdf({ id: payment.receiptId!, tenantId: auth.tenantId || "", receiptNumber: payment.receiptNumber || "", billId: payment.billId, paymentId: payment.id, receiptDate: payment.receiptDate || payment.paymentDate, amount: payment.amount, createdAt: payment.createdAt })}>{payment.receiptNumber || "Open"}</Button> : "-"}</TableCell></TableRow>)}</TableBody></Table>}</Stack>

            <Stack spacing={1}><Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Refund history</Typography>{refunds.length === 0 ? <Alert severity="info">No refunds recorded for this bill.</Alert> : <Table size="small"><TableHead><TableRow><TableCell>Time</TableCell><TableCell>Mode</TableCell><TableCell>Reason</TableCell><TableCell>Notes</TableCell><TableCell align="right">Amount</TableCell></TableRow></TableHead><TableBody>{refunds.map((refund) => <TableRow key={refund.id}><TableCell>{refund.refundedAt}</TableCell><TableCell>{refund.refundMode || "-"}</TableCell><TableCell>{refund.reason}</TableCell><TableCell>{refund.notes || "-"}</TableCell><TableCell align="right">{refund.amount.toFixed(2)}</TableCell></TableRow>)}</TableBody></Table>}</Stack>

            <Stack spacing={1}><Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Receipts</Typography>{receipts.length === 0 ? <Alert severity="info">No receipts generated for this bill.</Alert> : <Table size="small"><TableHead><TableRow><TableCell>Receipt</TableCell><TableCell>Date</TableCell><TableCell align="right">Amount</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead><TableBody>{receipts.map((receipt) => <TableRow key={receipt.id}><TableCell>{receipt.receiptNumber}</TableCell><TableCell>{receipt.receiptDate}</TableCell><TableCell align="right">{receipt.amount.toFixed(2)}</TableCell><TableCell align="right"><Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap"><Button size="small" disabled={workingId === receipt.id} onClick={() => void openReceiptPdf(receipt)}>PDF</Button>{canSendReceipt ? <Button size="small" disabled={workingId === receipt.id} onClick={() => void sendReceiptAction(receipt, "email")}>Email</Button> : null}{canSendReceipt ? <Button size="small" disabled={workingId === receipt.id} onClick={() => void sendReceiptAction(receipt, "whatsapp")}>WhatsApp</Button> : null}</Stack></TableCell></TableRow>)}</TableBody></Table>}</Stack>
          </Stack></CardContent></Card> : null}
        </Grid>
      </Grid>

      <Dialog open={paymentOpen} onClose={() => setPaymentOpen(false)} fullWidth maxWidth="sm"><DialogTitle>Collect payment</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}>
        <TextField fullWidth label="Payment date" type="date" value={paymentForm.paymentDate} onChange={(e) => setPaymentForm((c) => ({ ...c, paymentDate: e.target.value }))} InputLabelProps={{ shrink: true }} />
        <TextField fullWidth label="Amount" value={paymentForm.amount} onChange={(e) => setPaymentForm((c) => ({ ...c, amount: e.target.value }))} />
        <FormControl fullWidth><InputLabel id="payment-mode-label">Mode</InputLabel><Select labelId="payment-mode-label" label="Mode" value={paymentForm.paymentMode} onChange={(e) => setPaymentForm((c) => ({ ...c, paymentMode: e.target.value as PaymentMode }))}>{PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}</Select></FormControl>
        <TextField fullWidth label={paymentForm.paymentMode === "CASH" ? "Reference number (optional)" : "Reference number"} required={paymentForm.paymentMode !== "CASH"} value={paymentForm.referenceNumber} onChange={(e) => setPaymentForm((c) => ({ ...c, referenceNumber: e.target.value }))} />
        <TextField fullWidth label="Notes" multiline minRows={2} value={paymentForm.notes} onChange={(e) => setPaymentForm((c) => ({ ...c, notes: e.target.value }))} />
      </Stack></DialogContent><DialogActions><Button onClick={() => setPaymentOpen(false)}>Cancel</Button><Button variant="contained" onClick={() => void submitPayment()} disabled={saving}>{saving ? "Collecting..." : "Collect Payment"}</Button></DialogActions></Dialog>

      <Dialog open={refundOpen} onClose={() => setRefundOpen(false)} fullWidth maxWidth="sm"><DialogTitle>Refund</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}>
        <Alert severity="info">Refundable amount: {refundableAmount.toFixed(2)}</Alert>
        <TextField fullWidth label="Amount" value={refundForm.amount} onChange={(e) => setRefundForm((c) => ({ ...c, amount: e.target.value }))} />
        <FormControl fullWidth><InputLabel id="refund-mode-label">Mode</InputLabel><Select labelId="refund-mode-label" label="Mode" value={refundForm.refundMode} onChange={(e) => setRefundForm((c) => ({ ...c, refundMode: e.target.value as PaymentMode }))}>{PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}</Select></FormControl>
        <TextField fullWidth label="Reason" required value={refundForm.reason} onChange={(e) => setRefundForm((c) => ({ ...c, reason: e.target.value }))} />
        <TextField fullWidth label="Notes" multiline minRows={2} value={refundForm.notes} onChange={(e) => setRefundForm((c) => ({ ...c, notes: e.target.value }))} />
      </Stack></DialogContent><DialogActions><Button onClick={() => setRefundOpen(false)}>Cancel</Button><Button variant="contained" onClick={() => void submitRefund()} disabled={saving}>{saving ? "Saving..." : "Refund"}</Button></DialogActions></Dialog>
    </Stack>
  );
}
