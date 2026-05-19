import * as React from "react";
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  GlobalStyles,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";

import type {
  Appointment,
  Bill,
  ClinicProfile,
  Consultation,
  Patient,
  Payment,
  PaymentLedgerRow,
  Receipt,
} from "../../api/clinicApi";

type BasePrintData = {
  clinicProfile: ClinicProfile | null;
  patient: Patient | null;
  appointment: Appointment | null;
  consultation: Consultation | null;
};

export type InvoicePrintData = BasePrintData & {
  bill: Bill;
};

export type ReceiptPrintData = BasePrintData & {
  bill: Bill;
  receipt: Receipt | null;
  payment: Payment | PaymentLedgerRow | null;
};

type PrintableDialogProps = {
  open: boolean;
  loading: boolean;
  onClose: () => void;
  onPrint: () => void;
};

function currency(value: number | null | undefined) {
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(value || 0);
}

function dateText(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "2-digit" }).format(date);
}

function dateTimeText(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(date);
}

function timeText(value: string | null | undefined) {
  if (!value) return "—";
  const candidate = value.length <= 8 ? `1970-01-01T${value}` : value;
  const date = new Date(candidate);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(date);
}

function text(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === "") return "—";
  return String(value);
}

function compactAddress(profile: ClinicProfile | null) {
  if (!profile) return ["—"];
  const lines = [
    profile.addressLine1,
    profile.addressLine2,
    [profile.city, profile.state, profile.postalCode].filter(Boolean).join(", "),
    profile.country,
  ].filter((line): line is string => Boolean(line && line.trim()));
  return lines.length > 0 ? lines : ["—"];
}

function phoneLine(profile: ClinicProfile | null) {
  if (!profile) return "—";
  const pieces = [profile.phone, profile.email].filter(Boolean);
  return pieces.length > 0 ? pieces.join("  |  ") : "—";
}

function clinicTitle(profile: ClinicProfile | null) {
  if (!profile) return "Clinic";
  return profile.displayName || profile.clinicName || "Clinic";
}

function appointmentSummary(appointment: Appointment | null, consultation: Consultation | null) {
  if (!appointment && !consultation) return "—";
  const date = dateText(appointment?.appointmentDate || consultation?.completedAt || consultation?.createdAt || null);
  const time = timeText(appointment?.appointmentTime || null);
  const doctor = appointment?.doctorName || consultation?.doctorName || "—";
  const status = appointment?.status || consultation?.status || null;
  const parts = [date, time, doctor, status].filter((part) => part && part !== "—");
  return parts.length > 0 ? parts.join(" · ") : "—";
}

function taxShare(bill: Bill, lineTotal: number) {
  if (!bill.lines.length || bill.taxAmount <= 0) return null;
  const subtotal = bill.subtotalAmount || bill.lines.reduce((sum, line) => sum + (line.totalPrice || 0), 0);
  if (subtotal <= 0) return null;
  const ratio = lineTotal / subtotal;
  return Math.max(0, bill.taxAmount * ratio);
}

function invoiceSummaryRows(bill: Bill) {
  return [
    { label: "Subtotal", value: currency(bill.subtotalAmount) },
    { label: "Discount", value: currency(bill.discountAmount) },
    { label: "Tax", value: currency(bill.taxAmount) },
    { label: "Grand Total", value: currency(bill.totalAmount), emphasize: true },
    { label: "Paid Amount", value: currency(bill.paidAmount) },
    { label: "Due Amount", value: currency(bill.dueAmount), emphasize: true },
  ];
}

function receiptSummaryRows(receipt: Receipt | null, payment: Payment | PaymentLedgerRow | null, bill: Bill) {
  const amountPaid = receipt?.amount ?? payment?.amount ?? 0;
  const remainingDue = Math.max(0, bill.dueAmount);
  return [
    { label: "Receipt No.", value: receipt?.receiptNumber || payment?.receiptNumber || "—" },
    { label: "Payment Date", value: dateTimeText(payment?.paymentDateTime || payment?.paymentDate || receipt?.receiptDate || null) },
    { label: "Payment Mode", value: text(payment?.paymentMode) },
    { label: "Amount Paid", value: currency(amountPaid), emphasize: true },
    { label: "Remaining Due", value: currency(remainingDue), emphasize: true },
    { label: "Received By", value: text(payment?.receivedBy) },
  ];
}

function CompactDetails({
  rows,
}: {
  rows: Array<{ label: string; value: React.ReactNode }>;
}) {
  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "1fr", md: "repeat(2, minmax(0, 1fr))" },
        columnGap: 2,
        rowGap: 0.85,
      }}
    >
      {rows.map((row) => (
        <Box
          key={row.label}
          sx={{
            display: "flex",
            gap: 0.75,
            alignItems: "flex-start",
            py: 0.25,
            minWidth: 0,
          }}
        >
          <Typography variant="body2" sx={{ fontWeight: 800, whiteSpace: "nowrap", flexShrink: 0 }}>
            {row.label}:
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 500, minWidth: 0, wordBreak: "break-word" }}>
            {row.value}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}

function SignatureBlock({ label = "Authorized Signature" }: { label?: string }) {
  return (
    <Box
      sx={{
        minWidth: 220,
        pt: 3,
        mt: 1,
        borderTop: "1px solid",
        borderColor: "divider",
        textAlign: "center",
      }}
    >
      <Box sx={{ height: 42 }} />
      <Typography variant="body2" sx={{ fontWeight: 700 }}>
        {label}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        Clinic seal / sign
      </Typography>
    </Box>
  );
}

function PrintShell({
  title,
  subtitle,
  summaryBlock,
  children,
  footerNote,
  clinicProfile,
}: {
  title: string;
  subtitle: string;
  summaryBlock: React.ReactNode;
  children: React.ReactNode;
  footerNote: string;
  clinicProfile: ClinicProfile | null;
}) {
  return (
    <Box
      sx={{
        position: "relative",
        width: "100%",
        maxWidth: "210mm",
        minHeight: "297mm",
        mx: "auto",
        bgcolor: "#fff",
        color: "#12212b",
        overflow: "hidden",
        boxShadow: "0 24px 60px rgba(15, 23, 42, 0.12)",
        border: "1px solid rgba(13, 148, 136, 0.18)",
        borderRadius: 3,
      }}
      className="print-document-sheet"
    >
      <GlobalStyles
        styles={{
          "@media print": {
            "body": { backgroundColor: "#fff !important" },
            ".no-print": { display: "none !important" },
            ".print-document-sheet": {
              boxShadow: "none !important",
              border: "none !important",
              borderRadius: "0 !important",
              width: "auto !important",
              maxWidth: "none !important",
              minHeight: "auto !important",
            },
            ".print-document-sheet *": {
              WebkitPrintColorAdjust: "exact",
              printColorAdjust: "exact",
            },
            ".print-row-avoid": {
              breakInside: "avoid",
              pageBreakInside: "avoid",
            },
            ".MuiBackdrop-root": { display: "none !important" },
            ".MuiDialog-container": { alignItems: "flex-start !important" },
            ".MuiDialog-paper": {
              margin: "0 !important",
              boxShadow: "none !important",
              maxHeight: "none !important",
            },
            table: { borderCollapse: "collapse !important" },
            th: { backgroundColor: "#eef8f6 !important" },
            tr: { breakInside: "avoid", pageBreakInside: "avoid" },
            "@page": { size: "A4", margin: "12mm" },
          },
        }}
      />

      <Box
        sx={{
          position: "absolute",
          inset: 0,
          pointerEvents: "none",
        }}
      >
        <Box
          sx={{
            position: "absolute",
            top: 0,
            right: 0,
            width: 110,
            height: 110,
            bgcolor: "rgba(14, 165, 233, 0.08)",
            borderBottomLeftRadius: 20,
            clipPath: "polygon(100% 0, 0 0, 100% 100%)",
          }}
        />
        <Box
          sx={{
            position: "absolute",
            bottom: 0,
            left: 0,
            width: 140,
            height: 140,
            bgcolor: "rgba(244, 114, 182, 0.08)",
            clipPath: "polygon(0 0, 0 100%, 100% 100%)",
          }}
        />
      </Box>

      <Box sx={{ position: "relative", p: { xs: 2.25, md: 4 } }}>
        <Stack spacing={1.5}>
          <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, alignItems: "flex-start", flexWrap: "wrap" }}>
            <Box sx={{ maxWidth: 420 }}>
              <Typography variant="overline" sx={{ letterSpacing: 3, color: "secondary.main", fontWeight: 700 }}>
                {clinicTitle(clinicProfile)}
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 900, lineHeight: 1.05, mt: 0.25 }}>
                {clinicProfile?.displayName || clinicProfile?.clinicName || "Clinic Name"}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                {compactAddress(clinicProfile).join(" • ")}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {phoneLine(clinicProfile)}
              </Typography>
            </Box>

            <Box sx={{ textAlign: "right" }}>
              <Typography variant="h2" sx={{ fontWeight: 900, letterSpacing: 4, color: "primary.dark", lineHeight: 0.95, fontSize: { xs: "2.4rem", md: "3rem" } }}>
                {title}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.35 }}>
                {subtitle}
              </Typography>
            </Box>
          </Box>

          {children}

          <Divider sx={{ borderColor: "divider", my: 0.5 }} />

          <Box sx={{ display: "flex", justifyContent: "space-between", gap: 3, flexWrap: "wrap", alignItems: "flex-end" }}>
            <Box sx={{ maxWidth: 460 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.5 }}>
                Notes / Payment Details
              </Typography>
              {summaryBlock}
            </Box>
            <SignatureBlock />
          </Box>

          <Divider sx={{ borderColor: "divider", my: 0.5 }} />
          <Typography variant="caption" color="text.secondary" sx={{ textAlign: "center", pb: 0.5 }}>
            {footerNote}
          </Typography>
        </Stack>
      </Box>
    </Box>
  );
}

export function InvoicePrintDialog({
  open,
  loading,
  onClose,
  onPrint,
  data,
}: PrintableDialogProps & {
  data: InvoicePrintData | null;
}) {
  const summaryRows = data ? invoiceSummaryRows(data.bill) : [];
  const invoiceMetaRows = data
    ? [
        { label: "Invoice No", value: text(data.bill.billNumber) },
        { label: "Bill Date", value: dateText(data.bill.billDate) },
        { label: "Patient", value: text(data.patient ? `${data.patient.firstName} ${data.patient.lastName}`.trim() : data.bill.patientName) },
        { label: "Patient ID", value: text(data.patient?.patientNumber || data.bill.patientNumber) },
        { label: "Mobile", value: text(data.patient?.mobile) },
        { label: "Doctor", value: text(data.consultation?.doctorName || data.appointment?.doctorName) },
        { label: "Appointment", value: appointmentSummary(data.appointment, data.consultation) },
      ]
    : [];

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xl" scroll="paper" PaperProps={{ sx: { bgcolor: "transparent", boxShadow: "none", overflow: "visible" } }}>
      <DialogTitle className="no-print" sx={{ fontWeight: 900 }}>
        Invoice Preview
      </DialogTitle>
      <DialogContent sx={{ bgcolor: "transparent", p: { xs: 1, md: 2 } }}>
        {loading || !data ? (
          <Box sx={{ minHeight: 300, display: "grid", placeItems: "center", color: "text.secondary" }}>
            <Typography>Loading invoice preview…</Typography>
          </Box>
        ) : (
          <PrintShell
            title="INVOICE"
            subtitle="Professional clinic billing statement"
            clinicProfile={data.clinicProfile}
            footerNote="This invoice is system generated. Please retain it for records and verification."
            summaryBlock={
              <Stack spacing={0.75}>
                {summaryRows.map((row) => (
                  <Box key={row.label} sx={{ display: "flex", justifyContent: "space-between", gap: 2 }}>
                    <Typography variant="body2" sx={{ fontWeight: row.emphasize ? 800 : 500 }}>
                      {row.label}
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: row.emphasize ? 900 : 700 }}>
                      {row.value}
                    </Typography>
                  </Box>
                ))}
              </Stack>
            }
          >
            <Stack spacing={1.5}>
              <CompactDetails rows={invoiceMetaRows} />

              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 0.75 }}>
                  Itemized Charges
                </Typography>
                <Table size="small" sx={{ border: "1px solid", borderColor: "divider", tableLayout: "fixed" }}>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 800, py: 0.75, width: "6%" }}>No</TableCell>
                      <TableCell sx={{ fontWeight: 800, py: 0.75, width: "40%" }}>Description</TableCell>
                      <TableCell sx={{ fontWeight: 800, py: 0.75 }} align="right">
                        Qty
                      </TableCell>
                      <TableCell sx={{ fontWeight: 800, py: 0.75 }} align="right">
                        Unit Price
                      </TableCell>
                      <TableCell sx={{ fontWeight: 800, py: 0.75 }} align="right">
                        Discount
                      </TableCell>
                      <TableCell sx={{ fontWeight: 800, py: 0.75 }} align="right">
                        Tax
                      </TableCell>
                      <TableCell sx={{ fontWeight: 800, py: 0.75 }} align="right">
                        Subtotal
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.bill.lines.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={7}>
                          <Typography variant="body2" color="text.secondary">
                            No line items available.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ) : (
                      data.bill.lines.map((line, index) => {
                        const gross = (line.quantity || 0) * (line.unitPrice || 0);
                        const discount = Math.max(0, gross - (line.totalPrice || 0));
                        const tax = taxShare(data.bill, line.totalPrice);
                        return (
                          <TableRow key={line.id || `${line.itemName}-${index}`} className="print-row-avoid">
                            <TableCell sx={{ py: 0.6, verticalAlign: "top" }}>{index + 1}</TableCell>
                            <TableCell sx={{ py: 0.6, verticalAlign: "top" }}>
                              <Stack spacing={0.1}>
                                <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                                  {text(line.itemName)}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.1 }}>
                                  {text(line.itemType)}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell align="right" sx={{ py: 0.6, verticalAlign: "top" }}>{line.quantity ?? 0}</TableCell>
                            <TableCell align="right" sx={{ py: 0.6, verticalAlign: "top" }}>{currency(line.unitPrice)}</TableCell>
                            <TableCell align="right" sx={{ py: 0.6, verticalAlign: "top" }}>{currency(discount)}</TableCell>
                            <TableCell align="right" sx={{ py: 0.6, verticalAlign: "top" }}>{tax === null ? "—" : currency(tax)}</TableCell>
                            <TableCell align="right" sx={{ py: 0.6, verticalAlign: "top" }}>{currency(line.totalPrice)}</TableCell>
                          </TableRow>
                        );
                      })
                    )}
                  </TableBody>
                </Table>
              </Box>
            </Stack>
          </PrintShell>
        )}
      </DialogContent>
      <DialogActions className="no-print" sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Close</Button>
        <Button variant="contained" onClick={onPrint} disabled={loading || !data}>
          Print Invoice
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function ReceiptPrintDialog({
  open,
  loading,
  onClose,
  onPrint,
  data,
}: PrintableDialogProps & {
  data: ReceiptPrintData | null;
}) {
  const summaryRows = data ? receiptSummaryRows(data.receipt, data.payment, data.bill) : [];
  const receiptMetaRows = data
    ? [
        { label: "Receipt No", value: text(data.receipt?.receiptNumber || data.payment?.receiptNumber) },
        { label: "Payment Date", value: dateTimeText(data.payment?.paymentDateTime || data.payment?.paymentDate || data.receipt?.receiptDate) },
        { label: "Patient", value: text(data.patient ? `${data.patient.firstName} ${data.patient.lastName}`.trim() : data.bill.patientName) },
        { label: "Bill No", value: text(data.bill.billNumber) },
        { label: "Payment Mode", value: text(data.payment?.paymentMode) },
        { label: "Amount Paid", value: currency(data.receipt?.amount ?? data.payment?.amount ?? 0) },
        { label: "Remaining Due", value: currency(data.bill.dueAmount) },
        { label: "Received By", value: text(data.payment?.receivedBy) },
      ]
    : [];

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xl" scroll="paper" PaperProps={{ sx: { bgcolor: "transparent", boxShadow: "none", overflow: "visible" } }}>
      <DialogTitle className="no-print" sx={{ fontWeight: 900 }}>
        Receipt Preview
      </DialogTitle>
      <DialogContent sx={{ bgcolor: "transparent", p: { xs: 1, md: 2 } }}>
        {loading || !data ? (
          <Box sx={{ minHeight: 300, display: "grid", placeItems: "center", color: "text.secondary" }}>
            <Typography>Loading receipt preview…</Typography>
          </Box>
        ) : (
          <PrintShell
            title="RECEIPT"
            subtitle="Payment acknowledgement"
            clinicProfile={data.clinicProfile}
            footerNote="This receipt acknowledges payment against the referenced bill. Please keep it for your records."
            summaryBlock={
              <Stack spacing={0.75}>
                {summaryRows.map((row) => (
                  <Box key={row.label} sx={{ display: "flex", justifyContent: "space-between", gap: 2 }}>
                    <Typography variant="body2" sx={{ fontWeight: row.emphasize ? 800 : 500 }}>
                      {row.label}
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: row.emphasize ? 900 : 700 }}>
                      {row.value}
                    </Typography>
                  </Box>
                ))}
              </Stack>
            }
          >
            <Stack spacing={1.5}>
              <CompactDetails rows={receiptMetaRows} />

              <Box
                sx={{
                  border: "1px solid",
                  borderColor: "divider",
                  borderRadius: 2,
                  p: 2,
                  bgcolor: "rgba(14, 165, 233, 0.03)",
                }}
              >
                <Stack spacing={1}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                      Receipt Details
                    </Typography>
                    <Typography variant="subtitle2" sx={{ fontWeight: 900, color: "primary.dark" }}>
                      {currency(data.receipt?.amount ?? data.payment?.amount ?? 0)}
                    </Typography>
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    Received for bill {text(data.bill.billNumber)} from {text(data.patient ? `${data.patient.firstName} ${data.patient.lastName}`.trim() : data.bill.patientName)}.
                  </Typography>
                  {data.bill.notes ? (
                    <Typography variant="body2">
                      <strong>Bill notes:</strong> {data.bill.notes}
                    </Typography>
                  ) : null}
                  {data.payment?.notes ? (
                    <Typography variant="body2">
                      <strong>Payment notes:</strong> {data.payment.notes}
                    </Typography>
                  ) : null}
                </Stack>
              </Box>
            </Stack>
          </PrintShell>
        )}
      </DialogContent>
      <DialogActions className="no-print" sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Close</Button>
        <Button variant="contained" onClick={onPrint} disabled={loading || !data}>
          Print Receipt
        </Button>
      </DialogActions>
    </Dialog>
  );
}
