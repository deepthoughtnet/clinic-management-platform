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
  MenuItem,
  Select,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import DownloadRoundedIcon from "@mui/icons-material/DownloadRounded";
import EditRoundedIcon from "@mui/icons-material/EditRounded";
import PaidRoundedIcon from "@mui/icons-material/PaidRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import { firstZodError, labResultEntrySchema, labTestMasterSchema, paymentSchema } from "@deepthoughtnet/form-validation-kit";

import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactStatCard, compactCardContentSx } from "../../components/compact/CompactUi";
import RequiredLabel from "../../components/forms/RequiredLabel";
import {
  collectLabOrderPayment,
  collectLabOrderSample,
  createLabTest,
  deactivateLabTest,
  enterLabOrderResults,
  getLabCategories,
  getLabOrderPdf,
  getLabOrders,
  getLabTests,
  reviewLabOrder,
  updateLabTest,
  type LabOrder,
  type LabOrderResult,
  type LabOrderStatus,
  type LabTest,
  type LabTestInput,
  type LabTestParameterInput,
  type PaymentMode,
} from "../../api/clinicApi";

const emptyTestForm: LabTestInput = {
  testCode: "",
  testName: "",
  category: "Hematology",
  department: "",
  sampleType: "",
  unit: "",
  referenceRange: "",
  turnaroundTime: "",
  price: 0,
  active: true,
  parameters: [],
};

type ResultComponentForm = {
  parameterName: string;
  componentName: string;
  resultValue: string;
  unit: string;
  referenceRange: string;
};

type LabParameterForm = LabTestParameterInput;

type ResultItemForm = {
  labOrderItemId: string;
  testName: string;
  testCode: string;
  resultValue: string;
  unit: string;
  referenceRange: string;
  componentResults: ResultComponentForm[];
};

function formatMoney(value: number | null | undefined) {
  if (typeof value !== "number" || Number.isNaN(value)) return "-";
  return value.toFixed(2);
}

function statusTone(status: LabOrderStatus | string) {
  switch (status) {
    case "READY_FOR_COLLECTION":
      return "info";
    case "SAMPLE_COLLECTED":
    case "PROCESSING":
      return "warning";
    case "RESULT_ENTERED":
    case "REPORT_READY":
    case "REPORT_GENERATED":
    case "DOCTOR_REVIEWED":
    case "DELIVERED":
    case "PAID":
      return "success";
    case "PAYMENT_PENDING":
      return "warning";
    case "ORDERED":
      return "default";
    case "CANCELLED":
      return "default";
    default:
      return "default";
  }
}

function resultTone(flag: string | null | undefined) {
  switch ((flag || "").toUpperCase()) {
    case "CRITICAL":
      return "error";
    case "LOW":
    case "HIGH":
      return "warning";
    case "NORMAL":
      return "success";
    default:
      return "default";
  }
}

function toDatetimeLocal(value: string | null | undefined) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60_000);
  return local.toISOString().slice(0, 16);
}

function toIsoFromDatetimeLocal(value: string) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
}

function openPdf(blob: Blob) {
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener,noreferrer");
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function defaultResultsForItem(orderItem: LabOrder["items"][number], existingResults: LabOrderResult[]): ResultItemForm {
  const existingForItem = existingResults.filter((row) => row.labOrderItemId === orderItem.id);
  if (existingForItem.length > 0) {
    if (existingForItem.some((row) => row.componentName)) {
      return {
        labOrderItemId: orderItem.id,
        testName: orderItem.testName,
        testCode: orderItem.testCode,
        resultValue: "",
        unit: orderItem.unit || "",
        referenceRange: orderItem.referenceRange || "",
        componentResults: existingForItem.map((row) => ({
          parameterName: row.parameterName || "",
          componentName: row.componentName || "",
          resultValue: row.resultValue || "",
          unit: row.unit || "",
          referenceRange: row.referenceRange || "",
        })),
      };
    }
    const first = existingForItem[0];
    return {
      labOrderItemId: orderItem.id,
      testName: orderItem.testName,
      testCode: orderItem.testCode,
      resultValue: first.resultValue || "",
      unit: first.unit || orderItem.unit || "",
      referenceRange: first.referenceRange || orderItem.referenceRange || "",
      componentResults: [],
    };
  }
  const defaultComponents = orderItem.parameters.length
    ? orderItem.parameters.map((parameter) => ({
        parameterName: parameter.parameterName,
        componentName: parameter.parameterName,
        resultValue: "",
        unit: parameter.unit || "",
        referenceRange: parameter.normalRange || "",
      }))
    : orderItem.testName.toUpperCase().includes("CBC")
      ? ["Hemoglobin", "WBC", "RBC", "Platelets"].map((componentName) => ({
          parameterName: componentName,
          componentName,
          resultValue: "",
          unit: "",
          referenceRange: "",
        }))
      : [];
  return {
    labOrderItemId: orderItem.id,
    testName: orderItem.testName,
    testCode: orderItem.testCode,
    resultValue: "",
    unit: orderItem.unit || "",
    referenceRange: orderItem.referenceRange || "",
    componentResults: defaultComponents,
  };
}

export default function LabPage() {
  const auth = useAuth();
  const [tests, setTests] = React.useState<LabTest[]>([]);
  const [orders, setOrders] = React.useState<LabOrder[]>([]);
  const [categories, setCategories] = React.useState<string[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [tab, setTab] = React.useState(0);
  const [search, setSearch] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState<"ALL" | LabOrderStatus>("ALL");
  const [editorOpen, setEditorOpen] = React.useState(false);
  const [editing, setEditing] = React.useState<LabTest | null>(null);
  const [form, setForm] = React.useState<LabTestInput>(emptyTestForm);
  const [paymentTarget, setPaymentTarget] = React.useState<LabOrder | null>(null);
  const [sampleTarget, setSampleTarget] = React.useState<LabOrder | null>(null);
  const [resultTarget, setResultTarget] = React.useState<LabOrder | null>(null);
  const [reviewTarget, setReviewTarget] = React.useState<LabOrder | null>(null);
  const [paymentMode, setPaymentMode] = React.useState<PaymentMode>("CASH");
  const [paymentReference, setPaymentReference] = React.useState("");
  const [paymentNotes, setPaymentNotes] = React.useState("");
  const [sampleType, setSampleType] = React.useState("");
  const [sampleCollectedBy, setSampleCollectedBy] = React.useState("");
  const [sampleCollectedAt, setSampleCollectedAt] = React.useState(toDatetimeLocal(new Date().toISOString()));
  const [sampleNotes, setSampleNotes] = React.useState("");
  const [resultComments, setResultComments] = React.useState("");
  const [resultItems, setResultItems] = React.useState<ResultItemForm[]>([]);
  const [reviewComments, setReviewComments] = React.useState("");

  const canManageTests = auth.hasPermission("lab.test.manage") || auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN");
  const canViewOrders = auth.hasPermission("lab.order.read")
    || auth.hasPermission("lab.order.collect_payment")
    || auth.hasPermission("lab.order.collect_sample")
    || auth.hasPermission("lab.order.result_entry")
    || auth.hasPermission("lab.order.generate_report")
    || auth.hasPermission("lab.order.create");
  const canCollectPayment = auth.hasPermission("lab.order.collect_payment") || auth.rolesUpper.includes("RECEPTIONIST") || auth.rolesUpper.includes("BILLING_USER") || auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN");
  const canCollectSample = auth.hasPermission("lab.order.collect_sample") || auth.rolesUpper.includes("LAB_TECHNICIAN") || auth.rolesUpper.includes("LAB_ASSISTANT") || auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN");
  const canEnterResults = auth.hasPermission("lab.order.result_entry") || auth.rolesUpper.includes("LAB_TECHNICIAN") || auth.rolesUpper.includes("LAB_ASSISTANT") || auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN");
  const canGenerateReport = auth.hasPermission("lab.order.generate_report") || auth.rolesUpper.includes("LAB_TECHNICIAN") || auth.rolesUpper.includes("LAB_ASSISTANT") || auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN");
  const canReviewReport = auth.hasPermission("lab.order.review") || auth.rolesUpper.includes("DOCTOR") || auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [categoryRows, testRows, orderRows] = await Promise.all([
        getLabCategories(auth.accessToken, auth.tenantId),
        getLabTests(auth.accessToken, auth.tenantId, { active: null }),
        canViewOrders ? getLabOrders(auth.accessToken, auth.tenantId, {}) : Promise.resolve([] as LabOrder[]),
      ]);
      setCategories(categoryRows);
      setTests(testRows);
      setOrders(orderRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load laboratory module");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canViewOrders]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const filteredTests = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    return tests.filter((row) => {
      if (!term) return true;
      return [
        row.testCode,
        row.testName,
        row.category,
        row.department,
        row.sampleType,
        row.unit,
        row.referenceRange,
        row.turnaroundTime,
      ].filter(Boolean).some((value) => String(value).toLowerCase().includes(term));
    });
  }, [search, tests]);

  const pendingSampleOrders = React.useMemo(() => orders.filter((row) => row.status === "READY_FOR_COLLECTION"), [orders]);
  const pendingResultsOrders = React.useMemo(() => orders.filter((row) => row.status === "SAMPLE_COLLECTED" || row.status === "PROCESSING" || row.status === "RESULT_ENTERED"), [orders]);
  const pendingReviewOrders = React.useMemo(() => orders.filter((row) => row.status === "REPORT_READY" || row.status === "REPORT_GENERATED"), [orders]);
  const filteredOrders = React.useMemo(
    () => orders.filter((row) => statusFilter === "ALL" || row.status === statusFilter),
    [orders, statusFilter],
  );

  const activeTestCount = tests.filter((row) => row.active).length;
  const pendingOrders = orders.filter((row) => row.status === "PAYMENT_PENDING").length;
  const readyOrders = orders.filter((row) => row.status === "READY_FOR_COLLECTION").length;

  const openCreate = () => {
    setEditing(null);
    setForm(emptyTestForm);
    setEditorOpen(true);
  };

  const openEdit = (row: LabTest) => {
    setEditing(row);
    setForm({
      testCode: row.testCode,
      testName: row.testName,
      category: row.category,
      department: row.department,
      sampleType: row.sampleType,
      unit: row.unit,
      referenceRange: row.referenceRange,
      turnaroundTime: row.turnaroundTime,
      price: row.price,
      active: row.active,
      parameters: row.parameters.map((parameter, index) => ({
        parameterName: parameter.parameterName,
        unit: parameter.unit || "",
        normalRange: parameter.normalRange || "",
        criticalRange: parameter.criticalRange || "",
        sortOrder: parameter.sortOrder || index + 1,
      })),
    });
    setEditorOpen(true);
  };

  const saveTest = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = labTestMasterSchema.safeParse(form);
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }
    const payload = {
      testCode: parsed.data.testCode || "",
      testName: parsed.data.testName,
      category: parsed.data.category,
      department: parsed.data.department || null,
      sampleType: parsed.data.sampleType || null,
      unit: parsed.data.unit || null,
      referenceRange: parsed.data.referenceRange || null,
      turnaroundTime: parsed.data.turnaroundTime || null,
      price: parsed.data.price,
      active: parsed.data.active ?? true,
      parameters: parsed.data.parameters.map((parameter) => ({
        parameterName: parameter.parameterName,
        unit: parameter.unit || null,
        normalRange: parameter.normalRange || null,
        criticalRange: parameter.criticalRange || null,
        sortOrder: parameter.sortOrder ?? 1,
      })),
    };
    setSaving(true);
    setError(null);
    try {
      if (editing) {
        await updateLabTest(auth.accessToken, auth.tenantId, editing.id, payload);
      } else {
        await createLabTest(auth.accessToken, auth.tenantId, payload);
      }
      setEditorOpen(false);
      setEditing(null);
      setForm(emptyTestForm);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save lab test");
    } finally {
      setSaving(false);
    }
  };

  const deactivate = async (row: LabTest) => {
    if (!auth.accessToken || !auth.tenantId) return;
    if (!window.confirm(`Deactivate ${row.testName}?`)) return;
    setSaving(true);
    setError(null);
    try {
      await deactivateLabTest(auth.accessToken, auth.tenantId, row.id);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deactivate lab test");
    } finally {
      setSaving(false);
    }
  };

  const collectPayment = async () => {
    if (!auth.accessToken || !auth.tenantId || !paymentTarget) return;
    const parsed = paymentSchema.safeParse({
      amount: paymentTarget.billDueAmount ?? 0,
      paymentMethod: paymentMode,
      invoiceNumber: paymentReference.trim() || undefined,
      notes: paymentNotes.trim() || undefined,
    });
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await collectLabOrderPayment(auth.accessToken, auth.tenantId, paymentTarget.id, {
        amount: parsed.data.amount,
        paymentMode: parsed.data.paymentMethod,
        referenceNumber: parsed.data.invoiceNumber || null,
        notes: parsed.data.notes || null,
        receivedBy: auth.appUserId || null,
      });
      setPaymentTarget(null);
      setPaymentReference("");
      setPaymentNotes("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to collect payment");
    } finally {
      setSaving(false);
    }
  };

  const collectSample = async () => {
    if (!auth.accessToken || !auth.tenantId || !sampleTarget) return;
    setSaving(true);
    setError(null);
    try {
      await collectLabOrderSample(auth.accessToken, auth.tenantId, sampleTarget.id, {
        sampleType: sampleType.trim() || null,
        collectedBy: sampleCollectedBy.trim() || null,
        collectedAt: toIsoFromDatetimeLocal(sampleCollectedAt),
        notes: sampleNotes.trim() || null,
      });
      setSampleTarget(null);
      setSampleType("");
      setSampleCollectedBy("");
      setSampleCollectedAt(toDatetimeLocal(new Date().toISOString()));
      setSampleNotes("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to collect sample");
    } finally {
      setSaving(false);
    }
  };

  const openResultsDialog = (row: LabOrder) => {
    setResultTarget(row);
    setResultComments(row.resultComments || "");
    setResultItems(row.items.map((item) => defaultResultsForItem(item, row.results)));
  };

  const openReviewDialog = (row: LabOrder) => {
    setReviewTarget(row);
    setReviewComments(row.doctorComments || "");
  };

  const saveResults = async () => {
    if (!auth.accessToken || !auth.tenantId || !resultTarget) return;
    const parsed = labResultEntrySchema.safeParse({
      comments: resultComments.trim() || undefined,
      items: resultItems.map((item) => ({
        labOrderItemId: item.labOrderItemId,
        resultValue: item.resultValue.trim() || undefined,
        unit: item.unit.trim() || undefined,
        referenceRange: item.referenceRange.trim() || undefined,
        componentResults: item.componentResults.map((component) => ({
          parameterName: component.parameterName.trim() || undefined,
          componentName: component.componentName.trim() || undefined,
          resultValue: component.resultValue.trim() || undefined,
          unit: component.unit.trim() || undefined,
          referenceRange: component.referenceRange.trim() || undefined,
        })),
      })),
    });
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await enterLabOrderResults(auth.accessToken, auth.tenantId, resultTarget.id, {
        comments: parsed.data.comments || null,
        items: parsed.data.items.map((item) => ({
          labOrderItemId: item.labOrderItemId,
          resultValue: item.resultValue || null,
          unit: item.unit || null,
          referenceRange: item.referenceRange || null,
          componentResults: item.componentResults.map((component) => ({
            parameterName: component.parameterName || null,
            componentName: component.componentName || null,
            resultValue: component.resultValue || null,
            unit: component.unit || null,
            referenceRange: component.referenceRange || null,
          })),
        })),
      });
      setResultTarget(null);
      setResultComments("");
      setResultItems([]);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save results");
    } finally {
      setSaving(false);
    }
  };

  const saveReview = async () => {
    if (!auth.accessToken || !auth.tenantId || !reviewTarget) return;
    setSaving(true);
    setError(null);
    try {
      await reviewLabOrder(auth.accessToken, auth.tenantId, reviewTarget.id, {
        comments: reviewComments.trim() || null,
      });
      setReviewTarget(null);
      setReviewComments("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to review lab report");
    } finally {
      setSaving(false);
    }
  };

  const generateReport = async (row: LabOrder) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      const pdf = await getLabOrderPdf(auth.accessToken, auth.tenantId, row.id);
      openPdf(pdf.blob);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate lab report");
    } finally {
      setSaving(false);
    }
  };

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  if (loading) {
    return (
      <Box sx={{ minHeight: 240, display: "grid", placeItems: "center" }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Stack spacing={2.25}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, alignItems: "flex-start", flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Laboratory</Typography>
          <Typography variant="body2" color="text.secondary">Catalog, collection, result entry, and report generation.</Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          {canManageTests ? <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreate}>New Test</Button> : null}
        </Stack>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 3 }}>
          <CompactStatCard label="Catalog items" value={tests.length} helper={`${activeTestCount} active`} tone="info" />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <CompactStatCard label="Pending payment" value={pendingOrders} helper="Awaiting bill settlement" tone="warning" />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <CompactStatCard label="Sample queue" value={pendingSampleOrders.length} helper="Ready for collection" tone="info" />
        </Grid>
        <Grid size={{ xs: 12, md: 3 }}>
          <CompactStatCard label="Results queue" value={pendingResultsOrders.length} helper="Awaiting entry" tone="success" />
        </Grid>
      </Grid>

      <Card variant="outlined" sx={{ boxShadow: "none" }}>
        <CardContent sx={compactCardContentSx}>
          <Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ mb: 1 }}>
            <Tab label="Catalog" />
            <Tab label="Pending Sample Collection" />
            <Tab label="Pending Results" />
            <Tab label="Pending Doctor Review" />
            <Tab label="Orders" />
          </Tabs>

          {tab === 0 ? (
            <Stack spacing={2}>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1} sx={{ alignItems: { xs: "stretch", md: "center" } }}>
                <TextField fullWidth size="small" label="Search tests" value={search} onChange={(e) => setSearch(e.target.value)} />
                <Button variant="outlined" onClick={openCreate} disabled={!canManageTests} startIcon={<AddRoundedIcon />}>Add test</Button>
              </Stack>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {categories.map((category) => <Chip key={category} size="small" label={category} variant="outlined" />)}
              </Stack>
              {!filteredTests.length ? (
                <CompactEmptyState title="No lab tests found" subtitle="Create the first catalog item or adjust the search." action={canManageTests ? <Button variant="contained" onClick={openCreate}>Create test</Button> : null} />
              ) : (
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Code</TableCell>
                        <TableCell>Name</TableCell>
                        <TableCell>Category</TableCell>
                        <TableCell>Sample</TableCell>
                        <TableCell>Price</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredTests.map((row) => (
                        <TableRow key={row.id}>
                          <TableCell sx={{ fontWeight: 700 }}>{row.testCode}</TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.testName}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.department || row.turnaroundTime || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{row.category}</TableCell>
                          <TableCell>{row.sampleType || "-"}</TableCell>
                          <TableCell>{formatMoney(row.price)}</TableCell>
                          <TableCell><Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} variant="outlined" /></TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end">
                              {canManageTests ? <Button size="small" variant="outlined" startIcon={<EditRoundedIcon />} onClick={() => openEdit(row)}>Edit</Button> : null}
                              {canManageTests && row.active ? <Button size="small" variant="text" onClick={() => void deactivate(row)}>Deactivate</Button> : null}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </Stack>
          ) : tab === 1 ? (
            <OrderQueue
              rows={pendingSampleOrders}
              canCollectPayment={canCollectPayment}
              canCollectSample={canCollectSample}
              canEnterResults={canEnterResults}
              canGenerateReport={canGenerateReport}
              canReviewReport={canReviewReport}
              onCollectPayment={(row) => {
                setPaymentTarget(row);
                setPaymentMode("CASH");
                setPaymentReference("");
                setPaymentNotes("");
              }}
              onCollectSample={(row) => {
                setSampleTarget(row);
                setSampleType(row.sampleType || row.items[0]?.sampleType || "");
                setSampleCollectedBy(auth.username || auth.appUserId || "");
                setSampleCollectedAt(toDatetimeLocal(new Date().toISOString()));
                setSampleNotes(row.sampleCollectionNotes || "");
              }}
              onEnterResults={openResultsDialog}
              onReview={openReviewDialog}
              onGenerateReport={generateReport}
            />
          ) : tab === 2 ? (
            <OrderQueue
              rows={pendingResultsOrders}
              canCollectPayment={canCollectPayment}
              canCollectSample={canCollectSample}
              canEnterResults={canEnterResults}
              canGenerateReport={canGenerateReport}
              canReviewReport={canReviewReport}
              onCollectPayment={(row) => {
                setPaymentTarget(row);
                setPaymentMode("CASH");
                setPaymentReference("");
                setPaymentNotes("");
              }}
              onCollectSample={(row) => {
                setSampleTarget(row);
                setSampleType(row.sampleType || row.items[0]?.sampleType || "");
                setSampleCollectedBy(auth.username || auth.appUserId || "");
                setSampleCollectedAt(toDatetimeLocal(new Date().toISOString()));
                setSampleNotes(row.sampleCollectionNotes || "");
              }}
              onEnterResults={openResultsDialog}
              onReview={openReviewDialog}
              onGenerateReport={generateReport}
            />
          ) : tab === 3 ? (
            <OrderQueue
              rows={pendingReviewOrders}
              canCollectPayment={canCollectPayment}
              canCollectSample={canCollectSample}
              canEnterResults={canEnterResults}
              canGenerateReport={canGenerateReport}
              canReviewReport={canReviewReport}
              onCollectPayment={(row) => {
                setPaymentTarget(row);
                setPaymentMode("CASH");
                setPaymentReference("");
                setPaymentNotes("");
              }}
              onCollectSample={(row) => {
                setSampleTarget(row);
                setSampleType(row.sampleType || row.items[0]?.sampleType || "");
                setSampleCollectedBy(auth.username || auth.appUserId || "");
                setSampleCollectedAt(toDatetimeLocal(new Date().toISOString()));
                setSampleNotes(row.sampleCollectionNotes || "");
              }}
              onEnterResults={openResultsDialog}
              onReview={openReviewDialog}
              onGenerateReport={generateReport}
            />
          ) : (
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                {(["ALL", "ORDERED", "PAYMENT_PENDING", "PAID", "READY_FOR_COLLECTION", "SAMPLE_COLLECTED", "PROCESSING", "RESULT_ENTERED", "REPORT_READY", "REPORT_GENERATED", "DOCTOR_REVIEWED", "DELIVERED"] as const).map((status) => (
                  <Chip
                    key={status}
                    clickable
                    label={status.replaceAll("_", " ")}
                    color={statusFilter === status ? "primary" : "default"}
                    variant={statusFilter === status ? "filled" : "outlined"}
                    onClick={() => setStatusFilter(status)}
                  />
                ))}
              </Stack>
              {!filteredOrders.length ? (
                <CompactEmptyState title="No lab orders found" subtitle="Orders created from consultations will appear here." />
              ) : (
                <Box sx={{ overflowX: "auto" }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Order</TableCell>
                        <TableCell>Patient</TableCell>
                        <TableCell>Doctor</TableCell>
                        <TableCell>Tests</TableCell>
                        <TableCell>Bill</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredOrders.map((row) => (
                        <TableRow key={row.id}>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.orderNumber}</Typography>
                              <Typography variant="caption" color="text.secondary">{new Date(row.orderedAt).toLocaleString()}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.patientName || "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.patientNumber || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{row.doctorName || "-"}</TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.items.length} tests</Typography>
                              <Typography variant="caption" color="text.secondary">{row.results.length ? `${row.results.length} result rows` : "No results yet"}</Typography>
                              {row.results.length ? (
                                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                                  {row.results.slice(0, 3).map((result, resultIndex) => (
                                    <Chip
                                      key={`${row.id}-result-${resultIndex}`}
                                      size="small"
                                      label={`${result.parameterName || result.componentName || result.testName}: ${result.resultFlag || "NORMAL"}`}
                                      color={resultTone(result.resultFlag)}
                                      variant="outlined"
                                    />
                                  ))}
                                </Stack>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.billNumber || "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.billDueAmount != null ? `Due ${formatMoney(row.billDueAmount)}` : row.billStatus || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell><Chip size="small" label={row.status.replaceAll("_", " ")} color={statusTone(row.status)} variant="outlined" /></TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                              {canCollectPayment && row.billDueAmount != null && row.billDueAmount > 0 ? (
                                <Button size="small" variant="contained" startIcon={<PaidRoundedIcon />} onClick={() => {
                                  setPaymentTarget(row);
                                  setPaymentMode("CASH");
                                  setPaymentReference("");
                                  setPaymentNotes("");
                                }}>
                                  Collect
                                </Button>
                              ) : null}
                              {canCollectSample && row.status === "READY_FOR_COLLECTION" ? (
                                <Button size="small" variant="outlined" startIcon={<ScienceRoundedIcon />} onClick={() => {
                                  setSampleTarget(row);
                                  setSampleType(row.sampleType || row.items[0]?.sampleType || "");
                                  setSampleCollectedBy(auth.username || auth.appUserId || "");
                                  setSampleCollectedAt(toDatetimeLocal(new Date().toISOString()));
                                  setSampleNotes(row.sampleCollectionNotes || "");
                                }}>
                                  Collect sample
                                </Button>
                              ) : null}
                              {canEnterResults && (row.status === "SAMPLE_COLLECTED" || row.status === "PROCESSING" || row.status === "RESULT_ENTERED") ? (
                                <Button size="small" variant="outlined" onClick={() => openResultsDialog(row)}>Enter results</Button>
                              ) : null}
                              {canGenerateReport && (row.status === "RESULT_ENTERED" || row.status === "REPORT_READY" || row.status === "REPORT_GENERATED" || row.status === "DOCTOR_REVIEWED") ? (
                                <Button size="small" variant="outlined" startIcon={<DownloadRoundedIcon />} onClick={() => void generateReport(row)}>
                                  PDF
                                </Button>
                              ) : null}
                              {canReviewReport && row.status === "REPORT_READY" ? (
                                <Button size="small" variant="outlined" onClick={() => openReviewDialog(row)}>
                                  Review
                                </Button>
                              ) : null}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </Stack>
          )}
        </CardContent>
      </Card>

      <Dialog open={editorOpen} onClose={() => setEditorOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editing ? "Edit Lab Test" : "New Lab Test"}</DialogTitle>
        <DialogContent dividers>
          <Grid container spacing={2} sx={{ pt: 0.5 }}>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth label="Test Code" value={form.testCode} onChange={(e) => setForm((current) => ({ ...current, testCode: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 5 }}><TextField fullWidth label={<RequiredLabel text="Test Name" />} value={form.testName} onChange={(e) => setForm((current) => ({ ...current, testName: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <FormControl fullWidth>
                <InputLabel id="lab-category-label">Category</InputLabel>
                <Select labelId="lab-category-label" label="Category" value={form.category} onChange={(e) => setForm((current) => ({ ...current, category: String(e.target.value) }))}>
                  {categories.length ? categories.map((category) => <MenuItem key={category} value={category}>{category}</MenuItem>) : ["Hematology", "Biochemistry", "Microbiology", "Pathology", "Radiology", "Cardiology", "Other"].map((category) => <MenuItem key={category} value={category}>{category}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Department" value={form.department || ""} onChange={(e) => setForm((current) => ({ ...current, department: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Sample Type" value={form.sampleType || ""} onChange={(e) => setForm((current) => ({ ...current, sampleType: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Unit" value={form.unit || ""} onChange={(e) => setForm((current) => ({ ...current, unit: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Reference Range" value={form.referenceRange || ""} onChange={(e) => setForm((current) => ({ ...current, referenceRange: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Turnaround Time" value={form.turnaroundTime || ""} onChange={(e) => setForm((current) => ({ ...current, turnaroundTime: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth type="number" label={<RequiredLabel text="Price" />} value={form.price} onChange={(e) => setForm((current) => ({ ...current, price: Number(e.target.value) }))} /></Grid>
            <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth label="Status" value={form.active ? "Active" : "Inactive"} disabled /></Grid>
          </Grid>
          <Stack spacing={1.5} sx={{ mt: 3 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Parameters</Typography>
              <Button
                size="small"
                variant="outlined"
                onClick={() => setForm((current) => ({
                  ...current,
                  parameters: [...(current.parameters || []), { parameterName: "", unit: "", normalRange: "", criticalRange: "", sortOrder: (current.parameters?.length || 0) + 1 }],
                }))}
              >
                Add parameter
              </Button>
            </Box>
            {!form.parameters?.length ? (
              <Alert severity="info">Add parameter rows for tests such as CBC or other multi-parameter panels.</Alert>
            ) : (
              <Stack spacing={1}>
                {form.parameters.map((parameter, index) => (
                  <Grid container spacing={1} key={`${parameter.parameterName || "parameter"}-${index}`}>
                    <Grid size={{ xs: 12, md: 4 }}>
                      <TextField fullWidth size="small" label="Parameter Name" value={parameter.parameterName} onChange={(e) => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.map((row, rowIndex) => rowIndex === index ? { ...row, parameterName: e.target.value } : row),
                      }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <TextField fullWidth size="small" label="Unit" value={parameter.unit || ""} onChange={(e) => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.map((row, rowIndex) => rowIndex === index ? { ...row, unit: e.target.value } : row),
                      }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                      <TextField fullWidth size="small" label="Normal Range" value={parameter.normalRange || ""} onChange={(e) => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.map((row, rowIndex) => rowIndex === index ? { ...row, normalRange: e.target.value } : row),
                      }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 2 }}>
                      <TextField fullWidth size="small" label="Critical Range" value={parameter.criticalRange || ""} onChange={(e) => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.map((row, rowIndex) => rowIndex === index ? { ...row, criticalRange: e.target.value } : row),
                      }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 1 }} sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end" }}>
                      <IconButton size="small" onClick={() => setForm((current) => ({
                        ...current,
                        parameters: current.parameters.filter((_, rowIndex) => rowIndex !== index),
                      }))}>
                        <Typography variant="caption">x</Typography>
                      </IconButton>
                    </Grid>
                  </Grid>
                ))}
              </Stack>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditorOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveTest()} disabled={saving}>{editing ? "Update" : "Create"}</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(paymentTarget)} onClose={() => setPaymentTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Collect Payment</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">
              {paymentTarget ? `Collect ${formatMoney(paymentTarget.billDueAmount)} for ${paymentTarget.orderNumber}` : ""}
            </Alert>
            <FormControl fullWidth>
              <InputLabel id="payment-mode-label">Payment Mode</InputLabel>
              <Select labelId="payment-mode-label" label="Payment Mode" value={paymentMode} onChange={(e) => setPaymentMode(e.target.value as PaymentMode)}>
                <MenuItem value="CASH">Cash</MenuItem>
                <MenuItem value="UPI">UPI</MenuItem>
                <MenuItem value="CARD">Card</MenuItem>
                <MenuItem value="CHEQUE">Cheque</MenuItem>
                <MenuItem value="BANK_TRANSFER">Bank Transfer</MenuItem>
                <MenuItem value="OTHER">Other</MenuItem>
              </Select>
            </FormControl>
            <TextField label="Reference Number" value={paymentReference} onChange={(e) => setPaymentReference(e.target.value)} />
            <TextField label="Notes" value={paymentNotes} onChange={(e) => setPaymentNotes(e.target.value)} multiline minRows={2} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPaymentTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={() => void collectPayment()} disabled={saving || !paymentTarget}>Collect Payment</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(sampleTarget)} onClose={() => setSampleTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Collect Sample</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">{sampleTarget ? `${sampleTarget.orderNumber} • ${sampleTarget.patientName || "-"}` : ""}</Alert>
            <TextField label="Sample Type" value={sampleType} onChange={(e) => setSampleType(e.target.value)} />
            <TextField label="Collected By" value={sampleCollectedBy} onChange={(e) => setSampleCollectedBy(e.target.value)} />
            <TextField
              label="Collected At"
              type="datetime-local"
              value={sampleCollectedAt}
              onChange={(e) => setSampleCollectedAt(e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
            <TextField label="Notes" value={sampleNotes} onChange={(e) => setSampleNotes(e.target.value)} multiline minRows={2} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSampleTarget(null)}>Cancel</Button>
          <Button variant="contained" startIcon={<ScienceRoundedIcon />} onClick={() => void collectSample()} disabled={saving || !sampleTarget}>Collect Sample</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(resultTarget)} onClose={() => setResultTarget(null)} fullWidth maxWidth="lg">
        <DialogTitle>Enter Results</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">{resultTarget ? `${resultTarget.orderNumber} • ${resultTarget.patientName || "-"}` : ""}</Alert>
            <TextField label="Comments" value={resultComments} onChange={(e) => setResultComments(e.target.value)} multiline minRows={2} />
            <Stack spacing={1.5}>
              {resultItems.map((item, index) => (
                <Card key={item.labOrderItemId} variant="outlined">
                  <CardContent sx={{ display: "grid", gap: 1.25 }}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                      <Box>
                        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{item.testName}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.testCode}</Typography>
                      </Box>
                      <Button size="small" onClick={() => {
                        setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                          ...row,
                          componentResults: [...row.componentResults, { parameterName: "", componentName: "", resultValue: "", unit: "", referenceRange: "" }],
                        }) : row));
                      }}>
                        Add component
                      </Button>
                    </Box>
                    {item.componentResults.length ? (
                      <Stack spacing={1}>
                        {item.componentResults.map((component, componentIndex) => (
                          <Grid container spacing={1} key={`${item.labOrderItemId}-component-${componentIndex}`}>
                            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" label="Parameter" value={component.parameterName} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, parameterName: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
                            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" label="Component" value={component.componentName} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, componentName: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
                            <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Result Value" value={component.resultValue} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, resultValue: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
                            <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Unit" value={component.unit} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, unit: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
                            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth size="small" label="Reference Range" value={component.referenceRange} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                              ...row,
                              componentResults: row.componentResults.map((currentComponent, currentIndex) => currentIndex === componentIndex ? { ...currentComponent, referenceRange: e.target.value } : currentComponent),
                            }) : row))} /></Grid>
                            <Grid size={{ xs: 12, md: 1 }} sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end" }}>
                              <IconButton size="small" onClick={() => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? ({
                                ...row,
                                componentResults: row.componentResults.filter((_, currentIndex) => currentIndex !== componentIndex),
                              }) : row))}>
                                <Typography variant="caption">x</Typography>
                              </IconButton>
                            </Grid>
                          </Grid>
                        ))}
                      </Stack>
                    ) : (
                      <Grid container spacing={1}>
                        <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth size="small" label="Result Value" value={item.resultValue} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, resultValue: e.target.value } : row))} /></Grid>
                        <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth size="small" label="Unit" value={item.unit} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, unit: e.target.value } : row))} /></Grid>
                        <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth size="small" label="Reference Range" value={item.referenceRange} onChange={(e) => setResultItems((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, referenceRange: e.target.value } : row))} /></Grid>
                      </Grid>
                    )}
                  </CardContent>
                </Card>
              ))}
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResultTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveResults()} disabled={saving || !resultTarget}>Save Results</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(reviewTarget)} onClose={() => setReviewTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Doctor Review</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Alert severity="info">{reviewTarget ? `${reviewTarget.orderNumber} • ${reviewTarget.patientName || "-"}` : ""}</Alert>
            <TextField label="Comments" value={reviewComments} onChange={(e) => setReviewComments(e.target.value)} multiline minRows={3} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReviewTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveReview()} disabled={saving || !reviewTarget}>Approve & Review</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function OrderQueue(props: {
  rows: LabOrder[];
  canCollectPayment: boolean;
  canCollectSample: boolean;
  canEnterResults: boolean;
  canGenerateReport: boolean;
  canReviewReport: boolean;
  onCollectPayment: (row: LabOrder) => void;
  onCollectSample: (row: LabOrder) => void;
  onEnterResults: (row: LabOrder) => void;
  onReview: (row: LabOrder) => void;
  onGenerateReport: (row: LabOrder) => void;
}) {
  const { rows, canCollectPayment, canCollectSample, canEnterResults, canGenerateReport, canReviewReport, onCollectPayment, onCollectSample, onEnterResults, onReview, onGenerateReport } = props;

  if (!rows.length) {
    return <CompactEmptyState title="No lab orders found" subtitle="Orders created from consultations will appear here." />;
  }

  return (
    <Box sx={{ overflowX: "auto" }}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Order</TableCell>
            <TableCell>Patient</TableCell>
            <TableCell>Doctor</TableCell>
            <TableCell>Tests</TableCell>
            <TableCell>Bill</TableCell>
            <TableCell>Status</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id}>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.orderNumber}</Typography>
                  <Typography variant="caption" color="text.secondary">{new Date(row.orderedAt).toLocaleString()}</Typography>
                </Stack>
              </TableCell>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.patientName || "-"}</Typography>
                  <Typography variant="caption" color="text.secondary">{row.patientNumber || "-"}</Typography>
                </Stack>
              </TableCell>
              <TableCell>{row.doctorName || "-"}</TableCell>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.items.length} tests</Typography>
                  <Typography variant="caption" color="text.secondary">{row.results.length ? `${row.results.length} result rows` : "No results yet"}</Typography>
                  {row.results.length ? (
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      {row.results.slice(0, 3).map((result, resultIndex) => (
                        <Chip
                          key={`${row.id}-result-${resultIndex}`}
                          size="small"
                          label={`${result.parameterName || result.componentName || result.testName}: ${result.resultFlag || "NORMAL"}`}
                          color={resultTone(result.resultFlag)}
                          variant="outlined"
                        />
                      ))}
                    </Stack>
                  ) : null}
                </Stack>
              </TableCell>
              <TableCell>
                <Stack spacing={0.25}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.billNumber || "-"}</Typography>
                  <Typography variant="caption" color="text.secondary">{row.billDueAmount != null ? `Due ${formatMoney(row.billDueAmount)}` : row.billStatus || "-"}</Typography>
                </Stack>
              </TableCell>
              <TableCell><Chip size="small" label={row.status.replaceAll("_", " ")} color={statusTone(row.status)} variant="outlined" /></TableCell>
              <TableCell align="right">
                <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap">
                  {canCollectPayment && row.billDueAmount != null && row.billDueAmount > 0 ? (
                    <Button size="small" variant="contained" startIcon={<PaidRoundedIcon />} onClick={() => onCollectPayment(row)}>
                      Collect
                    </Button>
                  ) : null}
                  {canCollectSample && row.status === "READY_FOR_COLLECTION" ? (
                    <Button size="small" variant="outlined" startIcon={<ScienceRoundedIcon />} onClick={() => onCollectSample(row)}>
                      Collect sample
                    </Button>
                  ) : null}
                  {canEnterResults && (row.status === "SAMPLE_COLLECTED" || row.status === "PROCESSING" || row.status === "RESULT_ENTERED") ? (
                    <Button size="small" variant="outlined" onClick={() => onEnterResults(row)}>Enter results</Button>
                  ) : null}
                  {canGenerateReport && (row.status === "RESULT_ENTERED" || row.status === "REPORT_READY" || row.status === "REPORT_GENERATED" || row.status === "DOCTOR_REVIEWED") ? (
                    <Button size="small" variant="outlined" startIcon={<DownloadRoundedIcon />} onClick={() => onGenerateReport(row)}>
                      PDF
                    </Button>
                  ) : null}
                  {canReviewReport && (row.status === "REPORT_READY" || row.status === "REPORT_GENERATED") ? (
                    <Button size="small" variant="outlined" onClick={() => onReview(row)}>
                      Review
                    </Button>
                  ) : null}
                </Stack>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}
