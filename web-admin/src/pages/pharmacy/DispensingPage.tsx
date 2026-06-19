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
  Grid,
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
import CodeScannerField from "../../components/pharmacy/CodeScannerField";
import RequiredLabel from "../../components/forms/RequiredLabel";
import CommentSuggestions from "../../shared/components/comment-suggestions/CommentSuggestions";
import {
  appendDispensingAuditEntry,
  readDispensingAuditEntries,
  readDispensingState,
  setPrescriptionDispensingState,
  isTerminalDispensingState,
  shouldHideFromActiveQueue,
} from "../../shared/components/comment-suggestions/dispensingAuditStore.js";
import {
  dispensePrescriptionMedicine,
  generateMedicineBillFromDispense,
  getDispensingQueue,
  getPrescriptionDispense,
  getSubstituteSuggestions,
  type DispenseLine,
  type PrescriptionDispense,
  type SubstituteSuggestion,
} from "../../api/clinicApi";
import { createDispenseActionInputSchema, firstZodError, mapZodErrors } from "@deepthoughtnet/form-validation-kit";

function availabilityColor(status: string) {
  switch (status) {
    case "NO_INVENTORY":
      return "error";
    case "AVAILABLE":
      return "success";
    case "LOW_STOCK":
      return "warning";
    case "PARTIAL_AVAILABLE":
      return "warning";
    case "OUT_OF_STOCK":
      return "error";
    default:
      return "default";
  }
}

function expiryColor(status: string) {
  switch (status) {
    case "NEAR_EXPIRY":
      return "warning";
    case "EXPIRED":
      return "error";
    case "OK":
      return "success";
    default:
      return "default";
  }
}

function dispenseColor(status: string) {
  switch (status) {
    case "DISPENSED":
    case "FULLY_DISPENSED":
      return "success";
    case "READY_FOR_DISPENSE":
      return "info";
    case "PARTIALLY_DISPENSED":
      return "warning";
    case "UNAVAILABLE":
    case "CANCELLED":
    case "BOUGHT_EXTERNALLY":
    case "PATIENT_DECLINED":
    case "UNAVAILABLE_CLOSED":
    case "EXPIRED":
      return "error";
    default:
      return "default";
  }
}

function aggregateAvailability(lines: DispenseLine[]) {
  if (lines.some((line) => line.availabilityStatus === "NO_INVENTORY")) return "NO_INVENTORY";
  if (lines.some((line) => line.availabilityStatus === "OUT_OF_STOCK")) return "OUT_OF_STOCK";
  if (lines.some((line) => line.availabilityStatus === "PARTIAL_AVAILABLE")) return "PARTIAL_AVAILABLE";
  if (lines.some((line) => line.availabilityStatus === "LOW_STOCK")) return "LOW_STOCK";
  return "AVAILABLE";
}

function aggregateDispense(lines: DispenseLine[]) {
  const statuses = lines.map((line) => line.status);
  if (statuses.length > 0 && statuses.every((status) => status === "DISPENSED")) return "FULLY_DISPENSED";
  if (statuses.some((status) => status === "PARTIALLY_DISPENSED")) return "PARTIALLY_DISPENSED";
  if (statuses.some((status) => status === "DISPENSED") && statuses.some((status) => status === "NOT_DISPENSED" || status === "READY_FOR_DISPENSE")) {
    return "PARTIALLY_DISPENSED";
  }
  if (statuses.some((status) => status === "UNAVAILABLE" || status === "CANCELLED")) {
    if (!statuses.some((status) => status === "NOT_DISPENSED" || status === "READY_FOR_DISPENSE" || status === "PARTIALLY_DISPENSED")) {
      return "UNAVAILABLE_CLOSED";
    }
  }
  return "NOT_DISPENSED";
}

function formatTimestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : "-";
}

function patientLabel(name: string | null | undefined) {
  return name?.trim() || "Linked patient";
}

type DispenseWorkflowAction =
  | "FULL_DISPENSE"
  | "PARTIAL_DISPENSE"
  | "MARK_UNAVAILABLE"
  | "BUY_OUTSIDE"
  | "PATIENT_DECLINED"
  | "UNAVAILABLE_CLOSED"
  | "CANCEL_PRESCRIPTION"
  | "EXPIRED";

type DispenseActionTarget = {
  prescription: PrescriptionDispense;
  line?: DispenseLine;
  action: DispenseWorkflowAction;
  backendAction: "FULL" | "PARTIAL" | "CANCEL";
  category:
    | "DISPENSING_BOUGHT_EXTERNALLY"
    | "DISPENSING_UNAVAILABLE"
    | "DISPENSING_PATIENT_DECLINED"
    | "DISPENSING_CANCELLED";
  title: string;
  reasonRequired: boolean;
};

type QueueFilter =
  | "ACTIVE"
  | "ALL"
  | "PENDING"
  | "PARTIAL"
  | "FULLY_DISPENSED"
  | "BOUGHT_EXTERNALLY"
  | "PATIENT_DECLINED"
  | "UNAVAILABLE_CLOSED"
  | "CANCELLED"
  | "EXPIRED";

const QUEUE_FILTER_OPTIONS: Array<{ value: QueueFilter; label: string }> = [
  { value: "ACTIVE", label: "Active" },
  { value: "ALL", label: "All" },
  { value: "PENDING", label: "Pending" },
  { value: "PARTIAL", label: "Partial" },
  { value: "FULLY_DISPENSED", label: "Fully Dispensed" },
  { value: "BOUGHT_EXTERNALLY", label: "Bought Outside" },
  { value: "PATIENT_DECLINED", label: "Patient Declined" },
  { value: "UNAVAILABLE_CLOSED", label: "Unavailable Closed" },
  { value: "CANCELLED", label: "Cancelled" },
  { value: "EXPIRED", label: "Expired" },
];

function lineKey(line: DispenseLine) {
  return line.itemId || line.prescribedMedicineName;
}

function isClosedWorkflowStatus(status: string) {
  return ["FULLY_DISPENSED", "BOUGHT_EXTERNALLY", "PATIENT_DECLINED", "UNAVAILABLE_CLOSED", "CANCELLED", "EXPIRED"].includes(status);
}

function isExpiredPrescription(prescription: PrescriptionDispense, expiryDays = 30) {
  const source = prescription.prescriptionTimestamp;
  if (!source) return false;
  const date = new Date(source);
  if (Number.isNaN(date.getTime())) return false;
  const ageDays = (Date.now() - date.getTime()) / (1000 * 60 * 60 * 24);
  return ageDays > expiryDays;
}

function prescriptionStatusWithOverlay(
  prescription: PrescriptionDispense,
  overlay?: { status: string; lineStates?: Record<string, string> | null } | null,
) {
  const lines = prescription.lines.map((line) => {
    const nextStatus = overlay?.lineStates?.[lineKey(line)];
    return nextStatus ? { ...line, status: nextStatus } : line;
  });
  return {
    ...prescription,
    lines,
    status: overlay?.status || (isExpiredPrescription(prescription) ? "EXPIRED" : aggregateDispense(lines)),
  };
}

function actionLabel(action: DispenseWorkflowAction) {
  switch (action) {
    case "FULL_DISPENSE": return "Full Dispense";
    case "PARTIAL_DISPENSE": return "Partial Dispense";
    case "MARK_UNAVAILABLE": return "Mark Unavailable";
    case "BUY_OUTSIDE": return "Bought Outside";
    case "PATIENT_DECLINED": return "Patient Declined";
    case "UNAVAILABLE_CLOSED": return "Close as Medicine Unavailable";
    case "CANCEL_PRESCRIPTION": return "Cancel Prescription";
    case "EXPIRED": return "Expired";
  }
}

function terminalStatusForAction(action: DispenseWorkflowAction) {
  switch (action) {
    case "BUY_OUTSIDE": return "BOUGHT_EXTERNALLY";
    case "PATIENT_DECLINED": return "PATIENT_DECLINED";
    case "UNAVAILABLE_CLOSED":
    case "MARK_UNAVAILABLE":
      return "UNAVAILABLE_CLOSED";
    case "CANCEL_PRESCRIPTION": return "CANCELLED";
    case "EXPIRED": return "EXPIRED";
    default:
      return null;
  }
}

function categoryForAction(action: DispenseWorkflowAction): DispenseActionTarget["category"] {
  switch (action) {
    case "BUY_OUTSIDE": return "DISPENSING_BOUGHT_EXTERNALLY";
    case "PATIENT_DECLINED": return "DISPENSING_PATIENT_DECLINED";
    case "UNAVAILABLE_CLOSED":
    case "MARK_UNAVAILABLE":
    case "EXPIRED":
      return "DISPENSING_UNAVAILABLE";
    case "CANCEL_PRESCRIPTION":
      return "DISPENSING_CANCELLED";
    case "FULL_DISPENSE":
    case "PARTIAL_DISPENSE":
      return "DISPENSING_UNAVAILABLE";
  }
}

export default function DispensingPage() {
  const auth = useAuth();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const [rows, setRows] = React.useState<PrescriptionDispense[]>([]);
  const [selected, setSelected] = React.useState<PrescriptionDispense | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [lineQty, setLineQty] = React.useState<Record<string, string>>({});
  const [lineBatch, setLineBatch] = React.useState<Record<string, string>>({});
  const [substitutes, setSubstitutes] = React.useState<Record<string, SubstituteSuggestion[]>>({});
  const [saving, setSaving] = React.useState(false);
  const [queueFilter, setQueueFilter] = React.useState<QueueFilter>("ACTIVE");
  const [workflowTarget, setWorkflowTarget] = React.useState<DispenseActionTarget | null>(null);
  const [workflowQuantity, setWorkflowQuantity] = React.useState("");
  const [workflowBatch, setWorkflowBatch] = React.useState("");
  const [workflowReason, setWorkflowReason] = React.useState("");
  const [workflowRemarks, setWorkflowRemarks] = React.useState("");
  const [workflowError, setWorkflowError] = React.useState<string | null>(null);
  const [workflowFieldErrors, setWorkflowFieldErrors] = React.useState<Record<string, string>>({});
  const [auditTick, setAuditTick] = React.useState(0);
  const quantityInputRef = React.useRef<HTMLInputElement | null>(null);
  const batchInputRef = React.useRef<HTMLInputElement | null>(null);
  const remarksInputRef = React.useRef<HTMLTextAreaElement | null>(null);

  const canDispense = auth.hasPermission("inventory.manage");
  const canBill = auth.hasPermission("inventory.manage");
  const browserStorage = typeof window === "undefined" ? null : window.localStorage;

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      setRows(await getDispensingQueue(auth.accessToken, auth.tenantId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load dispensing queue");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const dispensingState = React.useMemo(() => {
    if (!browserStorage) {
      return {} as Record<string, { status: string; lineStates?: Record<string, string> | null }>;
    }
    return readDispensingState(browserStorage);
  }, [auditTick, browserStorage]);

  const decoratedRows = React.useMemo(() => rows.map((row) => {
    const overlay = dispensingState[row.prescriptionId];
    const decorated = prescriptionStatusWithOverlay(row, overlay);
    return { row: decorated, overlay, status: decorated.status };
  }), [dispensingState, rows]);

  const selectedOverlay = selected ? dispensingState[selected.prescriptionId] : null;
  const selectedView = React.useMemo(() => {
    if (!selected) return null;
    return prescriptionStatusWithOverlay(selected, selectedOverlay);
  }, [selected, selectedOverlay]);

  const selectedAuditEntries = React.useMemo(() => {
    if (!browserStorage || !selected) return [];
    return readDispensingAuditEntries(browserStorage)
      .filter((entry) => entry.prescriptionId === selected.prescriptionId)
      .slice(-15)
      .reverse();
  }, [auditTick, browserStorage, selected]);

  const filtered = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    return decoratedRows
      .filter(({ row, status }) => {
        const hidden = browserStorage ? shouldHideFromActiveQueue(browserStorage, row.prescriptionId, status) : isTerminalDispensingState(status);
        if (queueFilter === "ACTIVE") return !hidden && (status === "NOT_DISPENSED" || status === "PARTIALLY_DISPENSED" || status === "READY_FOR_DISPENSE");
        if (queueFilter === "ALL") return true;
        if (queueFilter === "PENDING") return !hidden && (status === "NOT_DISPENSED" || status === "READY_FOR_DISPENSE");
        if (queueFilter === "PARTIAL") return status === "PARTIALLY_DISPENSED";
        if (queueFilter === "FULLY_DISPENSED") return status === "FULLY_DISPENSED" || status === "DISPENSED";
        if (queueFilter === "BOUGHT_EXTERNALLY") return status === "BOUGHT_EXTERNALLY";
        if (queueFilter === "PATIENT_DECLINED") return status === "PATIENT_DECLINED";
        if (queueFilter === "UNAVAILABLE_CLOSED") return status === "UNAVAILABLE_CLOSED";
        if (queueFilter === "CANCELLED") return status === "CANCELLED";
        if (queueFilter === "EXPIRED") return status === "EXPIRED";
        return true;
      })
      .filter(({ row }) =>
        !term || [row.prescriptionNumber, row.patientName, row.doctorName, row.prescriptionTimestamp]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(term)),
      )
      .map(({ row }) => row);
  }, [browserStorage, decoratedRows, queueFilter, search]);

  const summary = React.useMemo(() => {
    const statuses = decoratedRows.map(({ status }) => status);
    return {
      queued: decoratedRows.length,
      pending: statuses.filter((status) => status === "NOT_DISPENSED" || status === "READY_FOR_DISPENSE").length,
      partial: statuses.filter((status) => status === "PARTIALLY_DISPENSED").length,
      outOfStock: rows.filter((row) => aggregateAvailability(row.lines) === "OUT_OF_STOCK").length,
    };
  }, [decoratedRows, rows]);

  const openDetails = async (prescriptionId: string) => {
    const accessToken = auth.accessToken;
    const tenantId = auth.tenantId;
    if (!accessToken || !tenantId) return;
    try {
      const details = await getPrescriptionDispense(accessToken, tenantId, prescriptionId);
      setSelected(details);
      setDialogOpen(true);
      const nextQty: Record<string, string> = {};
      for (const line of details.lines) {
        nextQty[lineKey(line)] = String(Math.max(1, line.pendingQuantity || line.prescribedQuantity || 1));
      }
      setLineQty(nextQty);
      setLineBatch({});
      const substitutePairs = await Promise.allSettled(
        details.lines
          .filter((line) => line.medicineId && (line.availabilityStatus === "OUT_OF_STOCK" || line.availabilityStatus === "PARTIAL_AVAILABLE" || line.availabilityStatus === "LOW_STOCK"))
          .map(async (line) => {
            const medicineId = line.medicineId;
            if (!medicineId) {
              return [line.prescribedMedicineName, []] as const;
            }
            return [line.prescribedMedicineName, await getSubstituteSuggestions(accessToken, tenantId, medicineId)] as const;
          }),
      );
      const resolved = substitutePairs
        .filter((result): result is PromiseFulfilledResult<readonly [string, SubstituteSuggestion[]]> => result.status === "fulfilled")
        .map((result) => result.value);
      setSubstitutes(Object.fromEntries(resolved));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load prescription dispensing details");
    }
  };

  const openWorkflowAction = React.useCallback((target: DispenseActionTarget) => {
    setWorkflowTarget(target);
    const key = target.line ? lineKey(target.line) : null;
    setWorkflowQuantity(target.action === "PARTIAL_DISPENSE"
      ? String(lineQty[key || ""] || Math.max(1, target.line?.pendingQuantity || target.line?.prescribedQuantity || 1))
      : "");
    setWorkflowBatch((key && lineBatch[key]) || target.line?.lastBatchId || "");
    setWorkflowReason("");
    setWorkflowRemarks("");
    setWorkflowError(null);
    setWorkflowFieldErrors({});
  }, [lineBatch, lineQty]);

  const closeWorkflowAction = React.useCallback(() => {
    setWorkflowTarget(null);
    setWorkflowQuantity("");
    setWorkflowBatch("");
    setWorkflowReason("");
    setWorkflowRemarks("");
    setWorkflowError(null);
    setWorkflowFieldErrors({});
  }, []);

  const focusWorkflowField = React.useCallback((fieldPath: string) => {
    window.setTimeout(() => {
      if (fieldPath === "quantity") {
        quantityInputRef.current?.focus();
        return;
      }
      if (fieldPath === "batchOverride") {
        batchInputRef.current?.focus();
        return;
      }
      if (fieldPath === "reason") {
        const selector = workflowTarget ? `[aria-labelledby="${categoryForAction(workflowTarget.action)}-reason-label"]` : null;
        const node = selector ? (document.querySelector(selector) as HTMLElement | null) : null;
        node?.focus();
        return;
      }
      if (fieldPath === "remarks") {
        remarksInputRef.current?.focus();
      }
    }, 0);
  }, []);

  const submitWorkflowAction = React.useCallback(async () => {
    if (!workflowTarget || !auth.accessToken || !auth.tenantId || !canDispense) return;
    const line = workflowTarget.line;
    const pendingQuantity = line ? Math.max(1, line.pendingQuantity || line.prescribedQuantity || 1) : 1;
    const availableQuantity = line ? Math.max(0, line.availableQuantity || 0) : 0;
    const schema = createDispenseActionInputSchema({
      pendingQuantity,
      availableQuantity,
      action: workflowTarget.action,
    });
    const parsed = schema.safeParse({
      action: workflowTarget.action,
      quantity: workflowQuantity === "" ? null : Number(workflowQuantity),
      batchOverride: workflowBatch || null,
      reason: workflowReason || null,
      remarks: workflowRemarks || null,
    });
    if (!parsed.success) {
      const mapped = mapZodErrors(parsed.error);
      setWorkflowFieldErrors(mapped);
      setWorkflowError(firstZodError(parsed.error));
      focusWorkflowField(Object.keys(mapped)[0] || "quantity");
      return;
    }
    setWorkflowFieldErrors({});
    setSaving(true);
    setError(null);
    setWorkflowError(null);
    try {
      const isPrescriptionClosure = !workflowTarget.line && workflowTarget.action !== "FULL_DISPENSE" && workflowTarget.action !== "PARTIAL_DISPENSE";
      const storage = browserStorage;
      if (isPrescriptionClosure) {
        const terminalStatus = terminalStatusForAction(workflowTarget.action);
        if (!terminalStatus) {
          throw new Error("Unsupported dispensing action");
        }
        if (!workflowReason.trim()) {
          setWorkflowError("Closure reason is required.");
          focusWorkflowField("reason");
          return;
        }
        const lineStates = Object.fromEntries(workflowTarget.prescription.lines.map((prescriptionLine) => [lineKey(prescriptionLine), terminalStatus]));
        if (storage) {
          setPrescriptionDispensingState(storage, workflowTarget.prescription.prescriptionId, {
            status: terminalStatus,
            lineStates,
          });
          appendDispensingAuditEntry(storage, {
            prescriptionId: workflowTarget.prescription.prescriptionId,
            medicineLineId: null,
            action: workflowTarget.action,
            previousStatus: selectedView?.status || aggregateDispense(workflowTarget.prescription.lines),
            newStatus: terminalStatus,
            quantity: null,
            batch: null,
            reason: workflowReason.trim() || null,
            remarks: workflowRemarks.trim() || null,
            user: auth.username || auth.appUserId || "Unknown",
            timestamp: new Date().toISOString(),
          });
        }
        setAuditTick((tick) => tick + 1);
        closeWorkflowAction();
        await load();
        return;
      }

      const quantity = workflowTarget.action === "FULL_DISPENSE"
        ? pendingQuantity
        : workflowTarget.action === "PARTIAL_DISPENSE"
          ? Number(workflowQuantity)
          : null;
      const updated = await dispensePrescriptionMedicine(auth.accessToken, auth.tenantId, workflowTarget.prescription.prescriptionId, {
        prescribedMedicineName: line?.prescribedMedicineName || workflowTarget.prescription.lines[0]?.prescribedMedicineName || workflowTarget.prescription.prescriptionNumber,
        medicineId: line?.medicineId ?? workflowTarget.prescription.lines[0]?.medicineId ?? null,
        quantity,
        batchId: workflowBatch || null,
        allowBatchOverride: Boolean(workflowBatch),
        action: workflowTarget.backendAction,
      });
      setSelected(updated);
      const nextStatus = aggregateDispense(updated.lines);
      const terminalStatus = nextStatus === "FULLY_DISPENSED" || nextStatus === "UNAVAILABLE_CLOSED" ? nextStatus : null;
      if (terminalStatus && storage) {
        setPrescriptionDispensingState(storage, workflowTarget.prescription.prescriptionId, {
          status: terminalStatus,
          lineStates: Object.fromEntries(updated.lines.map((prescriptionLine) => [lineKey(prescriptionLine), prescriptionLine.status])),
        });
      }
      if (storage) {
        appendDispensingAuditEntry(storage, {
        prescriptionId: workflowTarget.prescription.prescriptionId,
        medicineLineId: line?.itemId || null,
        action: workflowTarget.action,
        previousStatus: line?.status || aggregateDispense(workflowTarget.prescription.lines),
        newStatus: terminalStatus || nextStatus || (line?.status || "NOT_DISPENSED"),
        quantity: quantity ?? null,
        batch: workflowBatch || null,
        reason: workflowReason.trim() || null,
        remarks: workflowRemarks.trim() || null,
        user: auth.username || auth.appUserId || "Unknown",
        timestamp: new Date().toISOString(),
        });
      }
      setAuditTick((tick) => tick + 1);
      closeWorkflowAction();
      await load();
    } catch (err) {
      setWorkflowError(err instanceof Error ? err.message : "Failed to process dispensing action");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.appUserId, auth.tenantId, auth.username, canDispense, closeWorkflowAction, load, workflowBatch, workflowQuantity, workflowReason, workflowRemarks, workflowTarget]);

  const generateBill = async () => {
    if (!selectedView || !auth.accessToken || !auth.tenantId || !canBill) return;
    setSaving(true);
    setError(null);
    try {
      await generateMedicineBillFromDispense(auth.accessToken, auth.tenantId, selectedView.prescriptionId);
      const refreshed = await getPrescriptionDispense(auth.accessToken, auth.tenantId, selectedView.prescriptionId);
      setSelected(refreshed);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate medicine bill");
    } finally {
      setSaving(false);
    }
  };

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to access Dispensing.</Alert>;

  return (
    <Stack spacing={2}>
      {!canDispense ? (
        <Alert severity="info">
          Dispensing is read-only for your role. You can review prescription queue details, but dispensing and bill generation are restricted to inventory-managed pharmacy roles.
        </Alert>
      ) : null}
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Dispensing</Typography>
          <Typography variant="body2" color="text.secondary">
            Dispense finalized prescriptions using live stock availability. Full, partial, and unavailable actions are tracked per medicine line.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
      </Box>

      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
        {QUEUE_FILTER_OPTIONS.map((option) => (
          <Button
            key={option.value}
            size="small"
            variant={queueFilter === option.value ? "contained" : "outlined"}
            onClick={() => setQueueFilter(option.value)}
          >
            {option.label}
          </Button>
        ))}
      </Box>

      <Grid container spacing={1.5}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Queued</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{summary.queued}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Pending</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{summary.pending}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Partial</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{summary.partial}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Out of stock</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{summary.outOfStock}</Typography></CardContent></Card></Grid>
      </Grid>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField size="small" fullWidth label="Search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="prescription / patient / doctor" />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card>
          <CardContent>
            {filtered.length === 0 ? (
              <Alert severity="info">No finalized prescriptions are waiting for pharmacy dispensing.</Alert>
            ) : (
              <Box sx={{ overflowX: "auto" }}>
                <Table size="small" sx={{ minWidth: 980 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Prescription</TableCell>
                      <TableCell>Patient / Doctor</TableCell>
                      <TableCell>Medicines</TableCell>
                      <TableCell>Stock availability</TableCell>
                      <TableCell>Dispense status</TableCell>
                      <TableCell>Timestamp</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filtered.map((row) => {
                      const availability = aggregateAvailability(row.lines);
                      const dispenseStatus = row.status;
                      return (
                        <TableRow key={row.prescriptionId}>
                          <TableCell sx={{ fontWeight: 700 }}>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 800 }}>{row.prescriptionNumber}</Typography>
                              <Typography variant="caption" color="text.secondary">{formatTimestamp(row.prescriptionTimestamp)}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2">{patientLabel(row.patientName)}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.doctorName || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                              {row.lines.slice(0, 3).map((line) => (
                                <Chip key={lineKey(line)} size="small" variant="outlined" label={line.prescribedMedicineName} />
                              ))}
                              {row.lines.length > 3 ? <Chip size="small" variant="outlined" label={`+${row.lines.length - 3} more`} /> : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                              <Chip size="small" label={availability.replace(/_/g, " ")} color={availabilityColor(availability)} />
                              {row.lines.some((line) => line.expiryStatus !== "NONE" && line.expiryStatus !== "OK") ? (
                                <Chip size="small" variant="outlined" label="Expiry watch" color="warning" />
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Chip size="small" label={dispenseStatus.replace(/_/g, " ")} color={dispenseColor(dispenseStatus)} />
                          </TableCell>
                          <TableCell>{formatTimestamp(row.prescriptionTimestamp)}</TableCell>
                          <TableCell align="right">
                            <Button size="small" onClick={() => void openDetails(row.prescriptionId)}>Open</Button>
                          </TableCell>
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

      <Dialog open={dialogOpen} onClose={() => { setDialogOpen(false); setSubstitutes({}); }} fullWidth maxWidth="lg">
        <DialogTitle>Dispense Prescription {selectedView?.prescriptionNumber}</DialogTitle>
        <DialogContent>
          {!selectedView ? null : (
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Patient: <strong>{patientLabel(selectedView.patientName)}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Doctor: <strong>{selectedView.doctorName || "-"}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Prescription: <strong>{formatTimestamp(selectedView.prescriptionTimestamp)}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Billing: <strong>{selectedView.billingStatus}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}><Typography variant="body2">Dispense status: <strong>{selectedView.status}</strong></Typography></Grid>
                <Grid size={{ xs: 12, md: 4 }}>{selectedView.billedBillId ? <Typography variant="body2">Bill: <strong>Generated</strong></Typography> : null}</Grid>
              </Grid>
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Prescription actions</Typography>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Button
                    variant="outlined"
                    disabled={saving || !canDispense || isClosedWorkflowStatus(selectedView.status)}
                    onClick={() => openWorkflowAction({
                      prescription: selectedView,
                      action: "BUY_OUTSIDE",
                      backendAction: "CANCEL",
                      category: "DISPENSING_BOUGHT_EXTERNALLY",
                      title: "Bought Outside",
                      reasonRequired: true,
                    })}
                  >
                    Bought Outside
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={saving || !canDispense || isClosedWorkflowStatus(selectedView.status)}
                    onClick={() => openWorkflowAction({
                      prescription: selectedView,
                      action: "PATIENT_DECLINED",
                      backendAction: "CANCEL",
                      category: "DISPENSING_PATIENT_DECLINED",
                      title: "Patient Declined",
                      reasonRequired: true,
                    })}
                  >
                    Patient Declined
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={saving || !canDispense || isClosedWorkflowStatus(selectedView.status)}
                    onClick={() => openWorkflowAction({
                      prescription: selectedView,
                      action: "UNAVAILABLE_CLOSED",
                      backendAction: "CANCEL",
                      category: "DISPENSING_UNAVAILABLE",
                      title: "Medicine Unavailable",
                      reasonRequired: true,
                    })}
                  >
                    Close as Medicine Unavailable
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={saving || !canDispense || isClosedWorkflowStatus(selectedView.status)}
                    onClick={() => openWorkflowAction({
                      prescription: selectedView,
                      action: "CANCEL_PRESCRIPTION",
                      backendAction: "CANCEL",
                      category: "DISPENSING_CANCELLED",
                      title: "Cancel Prescription",
                      reasonRequired: true,
                    })}
                  >
                    Cancel Prescription
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={saving || !canDispense || isClosedWorkflowStatus(selectedView.status)}
                    onClick={() => openWorkflowAction({
                      prescription: selectedView,
                      action: "EXPIRED",
                      backendAction: "CANCEL",
                      category: "DISPENSING_UNAVAILABLE",
                      title: "Mark Expired",
                      reasonRequired: true,
                    })}
                  >
                    Mark Expired
                  </Button>
                </Stack>
              </Box>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Medicine</TableCell>
                    <TableCell align="right">Qty</TableCell>
                    <TableCell align="right">Pending</TableCell>
                    <TableCell>Stock</TableCell>
                    <TableCell>Expiry</TableCell>
                    <TableCell>Dispense status</TableCell>
                    <TableCell>Batch override</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {selectedView.lines.map((line) => {
                    const closed = line.status === "DISPENSED" || line.status === "UNAVAILABLE" || line.status === "CANCELLED" || isClosedWorkflowStatus(selectedView.status);
                    const rowKey = lineKey(line);
                    return (
                      <TableRow key={rowKey}>
                        <TableCell sx={{ fontWeight: 700 }}>{line.prescribedMedicineName}</TableCell>
                        <TableCell align="right">{line.prescribedQuantity}</TableCell>
                        <TableCell align="right">{line.pendingQuantity}</TableCell>
                        <TableCell>
                        <Chip size="small" label={(line.availabilityStatus || "OUT_OF_STOCK").replace(/_/g, " ")} color={availabilityColor(line.availabilityStatus)} />
                        <Typography variant="caption" display="block" color="text.secondary">
                          Available: {line.availableQuantity ?? 0}
                        </Typography>
                        {line.availabilityStatus === "NO_INVENTORY" ? (
                          <Typography variant="caption" display="block" color="error.main">
                            No inventory batch exists yet. Prescription remains valid; add stock before dispensing.
                          </Typography>
                        ) : null}
                        {substitutes[rowKey]?.length ? (
                          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                            {substitutes[rowKey].slice(0, 2).map((suggestion) => (
                              <Chip
                                key={suggestion.medicineId}
                                size="small"
                                label={`Substitute: ${suggestion.medicineName}`}
                                variant="outlined"
                                color={availabilityColor(suggestion.availabilityStatus)}
                              />
                            ))}
                          </Stack>
                        ) : null}
                          </TableCell>
                        <TableCell>
                          <Chip size="small" label={(line.expiryStatus || "NONE").replace(/_/g, " ")} color={expiryColor(line.expiryStatus)} />
                          <Typography variant="caption" display="block" color="text.secondary">
                            {line.nearestExpiryDate || "No expiry date"}
                          </Typography>
                        </TableCell>
                        <TableCell><Chip size="small" label={line.status} color={dispenseColor(line.status)} /></TableCell>
                        <TableCell>
                          <CodeScannerField
                            label="Scan or enter batch code"
                            value={lineBatch[rowKey] || ""}
                            onChange={(value) => setLineBatch((v) => ({ ...v, [rowKey]: value }))}
                            placeholder="USB scanner input"
                            disabled={!canDispense || closed}
                          />
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="column" spacing={1} alignItems="flex-end">
                            <TextField
                              size="small"
                              type="number"
                              value={lineQty[rowKey] || ""}
                              onChange={(e) => setLineQty((v) => ({ ...v, [rowKey]: e.target.value }))}
                              disabled={!canDispense || closed}
                              sx={{ width: 120 }}
                            />
                            <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                              {canDispense ? (
                                <>
                                  <Button
                                    size="small"
                                    variant="contained"
                                    disabled={saving || closed || line.availabilityStatus === "NO_INVENTORY"}
                                    onClick={() => openWorkflowAction({
                                      prescription: selectedView,
                                      line,
                                      action: "FULL_DISPENSE",
                                      backendAction: "FULL",
                                      category: "DISPENSING_UNAVAILABLE",
                                      title: "Full Dispense",
                                      reasonRequired: false,
                                    })}
                                  >
                                    Full
                                  </Button>
                                  <Button
                                    size="small"
                                    disabled={saving || closed || line.availabilityStatus === "NO_INVENTORY"}
                                    onClick={() => openWorkflowAction({
                                      prescription: selectedView,
                                      line,
                                      action: "PARTIAL_DISPENSE",
                                      backendAction: "PARTIAL",
                                      category: "DISPENSING_UNAVAILABLE",
                                      title: "Partial Dispense",
                                      reasonRequired: false,
                                    })}
                                  >
                                    Partial
                                  </Button>
                                  <Button
                                    size="small"
                                    color="inherit"
                                    disabled={saving || closed}
                                    onClick={() => openWorkflowAction({
                                      prescription: selectedView,
                                      line,
                                      action: "MARK_UNAVAILABLE",
                                      backendAction: "CANCEL",
                                      category: "DISPENSING_UNAVAILABLE",
                                      title: "Mark Unavailable",
                                      reasonRequired: true,
                                    })}
                                  >
                                    Unavailable
                                  </Button>
                                </>
                              ) : (
                                "-"
                              )}
                            </Stack>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
              {selectedAuditEntries.length > 0 ? (
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Recent audit trail</Typography>
                  <Stack spacing={1}>
                    {selectedAuditEntries.map((entry, index) => (
                      <Card key={`${entry.timestamp}-${index}`} variant="outlined">
                        <CardContent sx={{ py: 1.5 }}>
                          <Stack spacing={0.5}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {entry.action} · {entry.newStatus}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {entry.user} · {formatTimestamp(entry.timestamp)}
                            </Typography>
                            <Typography variant="body2">
                              Reason: {entry.reason || "-"}{entry.remarks ? ` · Remarks: ${entry.remarks}` : ""}
                            </Typography>
                          </Stack>
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>
                </Box>
              ) : null}

              {workflowTarget ? (
                <Card variant="outlined">
                  <CardContent>
                    <Stack spacing={2}>
                      <Box>
                        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{workflowTarget.title}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {workflowTarget.line ? workflowTarget.line.prescribedMedicineName : selectedView.prescriptionNumber}
                        </Typography>
                      </Box>
                      {workflowError ? <Alert severity="error">{workflowError}</Alert> : null}
                      <Grid container spacing={1.5}>
                        {workflowTarget.action === "PARTIAL_DISPENSE" ? (
                          <Grid size={{ xs: 12, md: 4 }}>
                            <TextField
                              fullWidth
                              size="small"
                              type="number"
                              label={<RequiredLabel text="Dispense quantity" required />}
                              value={workflowQuantity}
                              onChange={(event) => setWorkflowQuantity(event.target.value)}
                              inputRef={quantityInputRef}
                              required
                              error={Boolean(workflowFieldErrors.quantity)}
                              helperText={workflowFieldErrors.quantity || `Pending: ${workflowTarget.line?.pendingQuantity ?? 1} · Available: ${workflowTarget.line?.availableQuantity ?? 0}`}
                              inputProps={{ min: 1, step: 1, "aria-required": true }}
                            />
                          </Grid>
                        ) : null}
                        {workflowTarget.line ? (
                          <Grid size={{ xs: 12, md: 4 }}>
                            <CodeScannerField
                              label="Batch override"
                              value={workflowBatch}
                              onChange={setWorkflowBatch}
                              placeholder="Batch code"
                              helperText={workflowFieldErrors.batchOverride || "Optional unless a manual batch override is used."}
                              disabled={saving}
                              error={Boolean(workflowFieldErrors.batchOverride)}
                              inputRef={batchInputRef}
                            />
                          </Grid>
                        ) : null}
                        {workflowTarget.reasonRequired ? (
                          <Grid size={{ xs: 12 }}>
                            <CommentSuggestions
                              category={workflowTarget ? categoryForAction(workflowTarget.action) : "DISPENSING_UNAVAILABLE"}
                              selectedReason={workflowReason}
                              remarks={workflowRemarks}
                              onReasonChange={setWorkflowReason}
                              onRemarksChange={setWorkflowRemarks}
                              requiredReason={workflowTarget?.reasonRequired}
                              maxRemarksLength={250}
                              disabled={saving}
                              reasonLabel="Reason"
                              remarksLabel="Remarks"
                              reasonError={Boolean(workflowFieldErrors.reason)}
                              reasonHelperText={workflowFieldErrors.reason || "Select a reason and keep it under 60 characters."}
                              remarksError={Boolean(workflowFieldErrors.remarks)}
                              remarksHelperText={workflowFieldErrors.remarks || `${workflowRemarks.length}/250`}
                              remarksInputRef={remarksInputRef}
                            />
                          </Grid>
                        ) : null}
                      </Grid>
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setDialogOpen(false); setSubstitutes({}); closeWorkflowAction(); }}>Close</Button>
          {canBill ? (
            <Button
              variant="contained"
              disabled={saving || !selectedView || selectedView.billingStatus !== "NOT_BILLED" || !selectedView.lines.some((line) => line.dispensedQuantity > 0)}
              onClick={() => void generateBill()}
            >
              Generate Bill
            </Button>
          ) : null}
          {workflowTarget ? (
            <Button variant="contained" onClick={() => void submitWorkflowAction()} disabled={saving || !canDispense}>
              Save {actionLabel(workflowTarget.action)}
            </Button>
          ) : null}
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
