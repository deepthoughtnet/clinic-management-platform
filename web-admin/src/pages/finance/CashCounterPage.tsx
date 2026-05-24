import * as React from "react";
import {
  Alert,
  Box,
  Button,
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
import DownloadRoundedIcon from "@mui/icons-material/DownloadRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";

import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, CompactStatCard, compactChipSx } from "../../components/compact/CompactUi";
import {
  getCashCounterLedger,
  getCashCounterSummary,
  type CashCounterLedgerRow,
  type CashCounterSummary,
} from "../../api/clinicApi";

type SourceFilter = "ALL" | "CLINIC" | "PHARMACY" | "REFUND" | "RETURN";

const SOURCE_FILTERS: Array<{ value: SourceFilter; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "CLINIC", label: "Clinic Bill" },
  { value: "PHARMACY", label: "Pharmacy POS" },
  { value: "REFUND", label: "Refund" },
  { value: "RETURN", label: "Return" },
];

const PAYMENT_MODES = ["ALL", "CASH", "UPI", "CARD", "OTHER"] as const;

function formatMoney(value: number) {
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(value || 0);
}

function formatDateTime(value: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function mapCashCounterError(err: unknown) {
  const message = err instanceof Error ? err.message : "";
  if (!message) return "Cash counter could not be loaded. Please refresh the page.";
  if (/internal server error/i.test(message)) {
    return "Cash counter could not be loaded right now. Please refresh and try again.";
  }
  return message;
}

function escapeCsv(value: unknown) {
  const text = value === null || value === undefined ? "" : String(value);
  return `"${text.replace(/"/g, '""')}"`;
}

function downloadLedgerCsv(rows: CashCounterLedgerRow[]) {
  if (rows.length === 0) return;
  const headers: Array<keyof CashCounterLedgerRow> = [
    "dateTime",
    "source",
    "businessReference",
    "receiptNumber",
    "patientCustomer",
    "paymentMode",
    "grossAmount",
    "refundAmount",
    "netAmount",
    "cashier",
    "shiftReference",
    "status",
  ];
  const csv = [
    headers.map(escapeCsv).join(","),
    ...rows.map((row) => headers.map((header) => escapeCsv(row[header])).join(",")),
  ].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `cash-counter-ledger-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 60000);
}

export default function CashCounterPage() {
  const auth = useAuth();
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const canViewCashCounter =
    tenantRole === "CLINIC_ADMIN"
    || tenantRole === "BILLING_USER"
    || tenantRole === "AUDITOR"
    || (!!auth.tenantId && auth.rolesUpper.includes("PLATFORM_ADMIN"));

  const today = React.useMemo(() => new Date().toISOString().slice(0, 10), []);
  const [filters, setFilters] = React.useState({
    from: today,
    to: today,
    paymentMode: "ALL",
    source: "ALL" as SourceFilter,
    search: "",
  });
  const [summary, setSummary] = React.useState<CashCounterSummary | null>(null);
  const [rows, setRows] = React.useState<CashCounterLedgerRow[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [exporting, setExporting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const params = {
        from: filters.from || null,
        to: filters.to || null,
        paymentMode: filters.paymentMode === "ALL" ? null : filters.paymentMode,
        source: filters.source,
        search: filters.search.trim() || null,
      };
      const [summaryValue, ledgerValue] = await Promise.all([
        getCashCounterSummary(auth.accessToken, auth.tenantId, params),
        getCashCounterLedger(auth.accessToken, auth.tenantId, params),
      ]);
      setSummary(summaryValue);
      setRows(ledgerValue);
    } catch (err) {
      setError(mapCashCounterError(err));
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, filters]);

  const exportLedger = React.useCallback(() => {
    setExporting(true);
    try {
      downloadLedgerCsv(rows);
    } finally {
      window.setTimeout(() => setExporting(false), 250);
    }
  }, [rows]);

  React.useEffect(() => {
    void load();
  }, [load]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  if (!canViewCashCounter) {
    return <Alert severity="error">Cash Counter is available to billing users, auditors, and clinic admins only.</Alert>;
  }

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1} alignItems={{ xs: "flex-start", md: "center" }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>
            Cash Counter
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Unified finance view across clinic billing, Pharmacy POS, refunds, returns, and cashier shifts.
          </Typography>
        </Box>
        <Chip
          size="small"
          variant="outlined"
          label="Unified receipt sequence remains a documented future step"
          sx={compactChipSx}
        />
      </Stack>

      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}

      <CompactFilterCard
        title="Lookup"
        subtitle="Search by receipt number, invoice number, sale number, patient/customer, mobile, or date range."
        actions={
          <Stack direction="row" spacing={1}>
            <Button
              size="small"
              variant="outlined"
              startIcon={<DownloadRoundedIcon />}
              onClick={exportLedger}
              disabled={rows.length === 0 || exporting}
            >
              {exporting ? "Preparing CSV..." : "Export CSV"}
            </Button>
            <Button size="small" variant="outlined" startIcon={<RefreshRoundedIcon />} onClick={() => void load()} disabled={loading}>
              {loading ? "Refreshing..." : "Refresh"}
            </Button>
          </Stack>
        }
      >
        <Grid container spacing={1}>
          <Grid size={{ xs: 12, md: 2 }}>
            <TextField
              size="small"
              type="date"
              label="From"
              value={filters.from}
              onChange={(event) => setFilters((current) => ({ ...current, from: event.target.value }))}
              fullWidth
              InputLabelProps={{ shrink: true }}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 2 }}>
            <TextField
              size="small"
              type="date"
              label="To"
              value={filters.to}
              onChange={(event) => setFilters((current) => ({ ...current, to: event.target.value }))}
              fullWidth
              InputLabelProps={{ shrink: true }}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 2 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="cash-counter-payment-mode">Payment Mode</InputLabel>
              <Select
                labelId="cash-counter-payment-mode"
                label="Payment Mode"
                value={filters.paymentMode}
                onChange={(event) => setFilters((current) => ({ ...current, paymentMode: event.target.value }))}
              >
                {PAYMENT_MODES.map((mode) => (
                  <MenuItem key={mode} value={mode}>{mode}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              size="small"
              label="Receipt / Invoice / Sale / Customer"
              value={filters.search}
              onChange={(event) => setFilters((current) => ({ ...current, search: event.target.value }))}
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
              {SOURCE_FILTERS.map((item) => (
                <Chip
                  key={item.value}
                  label={item.label}
                  size="small"
                  color={filters.source === item.value ? "primary" : "default"}
                  variant={filters.source === item.value ? "filled" : "outlined"}
                  sx={compactChipSx}
                  onClick={() => setFilters((current) => ({ ...current, source: item.value }))}
                />
              ))}
              <Button size="small" onClick={() => setFilters({ from: today, to: today, paymentMode: "ALL", source: "ALL", search: "" })}>
                Reset
              </Button>
            </Stack>
          </Grid>
        </Grid>
      </CompactFilterCard>

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Collected" value={formatMoney(summary?.todayTotalCollected ?? 0)} tone="success" helper="Gross collection in range" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Clinic Billing" value={formatMoney(summary?.clinicBillingCollected ?? 0)} helper="Clinic bill payments" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Pharmacy POS" value={formatMoney(summary?.pharmacyPosCollected ?? 0)} helper="Pharmacy counter collections" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Refunds / Returns" value={formatMoney(summary?.refundsReturns ?? 0)} tone="warning" helper="Money flowing back out" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Net Cash" value={formatMoney(summary?.netCash ?? 0)} helper="Cash after refunds" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Net UPI" value={formatMoney(summary?.netUpi ?? 0)} helper="UPI after returns" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Net Card" value={formatMoney(summary?.netCard ?? 0)} helper="Card after refunds" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Net Other" value={formatMoney(summary?.netOther ?? 0)} helper="All other payment rails" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Open Shifts" value={summary?.openCashierShifts ?? 0} tone="info" helper="Currently open pharmacy cashier shifts" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <CompactStatCard label="Variance Alerts" value={summary?.varianceAlerts ?? 0} tone={(summary?.varianceAlerts ?? 0) > 0 ? "warning" : "default"} helper="Shift variance rows in range" />
        </Grid>
      </Grid>

      <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, overflow: "hidden" }}>
        {loading ? (
          <CompactEmptyState title="Loading cash counter…" subtitle="Fetching combined clinic and pharmacy transactions." />
        ) : rows.length === 0 ? (
          <CompactEmptyState title="No transactions found" subtitle="Try widening the date range or clearing the source and payment filters." />
        ) : (
          <Box sx={{ overflowX: "auto", maxHeight: 560, overflowY: "auto" }}>
            <Table stickyHeader size="small" sx={{ minWidth: 1160 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Date / Time</TableCell>
                  <TableCell>Source</TableCell>
                  <TableCell>Business Ref</TableCell>
                  <TableCell>Receipt</TableCell>
                  <TableCell>Patient / Customer</TableCell>
                  <TableCell>Payment</TableCell>
                  <TableCell align="right">Gross</TableCell>
                  <TableCell align="right">Refund</TableCell>
                  <TableCell align="right">Net</TableCell>
                  <TableCell>Cashier</TableCell>
                  <TableCell>Shift</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row, index) => (
                  <TableRow key={`${row.source}-${row.businessReference}-${row.receiptNumber}-${index}`} hover>
                    <TableCell sx={{ whiteSpace: "nowrap" }}>{formatDateTime(row.dateTime)}</TableCell>
                    <TableCell>
                      <Chip size="small" label={row.source || "UNKNOWN"} variant="outlined" sx={compactChipSx} />
                    </TableCell>
                    <TableCell>{row.businessReference || "—"}</TableCell>
                    <TableCell>{row.receiptNumber || "—"}</TableCell>
                    <TableCell>{row.patientCustomer || "—"}</TableCell>
                    <TableCell>{row.paymentMode || "OTHER"}</TableCell>
                    <TableCell align="right">{formatMoney(row.grossAmount)}</TableCell>
                    <TableCell align="right">{formatMoney(row.refundAmount)}</TableCell>
                    <TableCell align="right">{formatMoney(row.netAmount)}</TableCell>
                    <TableCell>{row.cashier || "—"}</TableCell>
                    <TableCell>{row.shiftReference || "—"}</TableCell>
                    <TableCell>{row.status || "—"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
      </Box>
    </Stack>
  );
}
