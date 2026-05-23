import * as React from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, CompactStatCard, compactCardContentSx, compactChipSx } from "../../components/compact/CompactUi";
import CodeScannerField from "../../components/pharmacy/CodeScannerField";
import {
  approveReconciliation,
  confirmGoodsReceipt,
  createReconciliation,
  createGoodsReceipt,
  createPurchaseOrder,
  createStockInward,
  createSupplier,
  createSupplierInvoice,
  getGoodsReceipts,
  getInventoryLocations,
  getMedicines,
  getPharmacyAnalytics,
  getPharmacyDashboard,
  getPurchaseOrders,
  getStocks,
  getSupplierInvoices,
  listReconciliations,
  listSuppliers,
  postReconciliation,
  rejectReconciliation,
  reviewReconciliationSheet,
  submitReconciliation as submitReconciliationApi,
  updateSupplier,
  uploadReconciliationSheet,
  type GoodsReceipt,
  type GoodsReceiptInput,
  type InventoryLocation,
  type Medicine,
  type OcrExtractionRow,
  type PharmacyAnalytics,
  type PharmacyDashboard,
  type PharmacyReconciliation,
  type PurchaseOrder,
  type PurchaseOrderInput,
  type Stock,
  type StockInwardInput,
  type Supplier,
  type SupplierInput,
  type SupplierInvoice,
  type SupplierInvoiceInput,
} from "../../api/clinicApi";

type OpsTab = "suppliers" | "inward" | "reconciliation" | "procurement" | "analytics";
type ProcurementTab = "po" | "invoice" | "grn";
type MedicineOption = { medicine: Medicine };

const emptySupplier: SupplierInput = {
  supplierName: "",
  contactPerson: null,
  phone: null,
  email: null,
  gstNumber: null,
  address: null,
  active: true,
};

const emptyInward: StockInwardInput = {
  medicineId: "",
  supplierId: null,
  locationId: null,
  purchaseReferenceNumber: null,
  batchNumber: null,
  barcode: null,
  qrCode: null,
  externalCode: null,
  expiryDate: null,
  purchaseDate: null,
  quantity: 0,
  unitCost: null,
  sellingPrice: null,
  lowStockThreshold: null,
};

const emptyReconciliation = {
  medicineId: "",
  stockBatchId: "",
  supplierId: "",
  physicalQuantity: "",
  reason: "",
};

const stickyTableSx = {
  maxHeight: 420,
  borderRadius: 2,
  border: "1px solid",
  borderColor: "divider",
  "& .MuiTableCell-root": {
    py: 0.85,
    verticalAlign: "top",
  },
  "& .MuiTableHead-root .MuiTableCell-root": {
    position: "sticky",
    top: 0,
    zIndex: 1,
    bgcolor: "background.paper",
  },
} as const;

function money(value: number | null | undefined) {
  return (value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function badgeTone(text: string) {
  if (text === "OUT_OF_STOCK" || text === "EXPIRED") return "error" as const;
  if (text === "LOW_STOCK" || text === "NEAR_EXPIRY") return "warning" as const;
  if (text === "AVAILABLE" || text === "OK") return "success" as const;
  if (text === "SUBMITTED") return "warning" as const;
  if (text === "APPROVED") return "info" as const;
  if (text === "POSTED") return "success" as const;
  if (text === "REJECTED") return "error" as const;
  return "default" as const;
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

function formatDate(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleDateString();
}

function actorLabel(actorId: string | null | undefined, currentUserId: string | null, fallback: string) {
  if (!actorId) return "-";
  if (actorId === currentUserId) return "You";
  return fallback;
}

function renderMedicineDescriptor(medicine: Medicine) {
  return [medicine.medicineType, medicine.strength, medicine.defaultPrice != null ? `INR ${money(medicine.defaultPrice)}` : null].filter(Boolean).join(" • ");
}

export default function PharmacyOperationsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = React.useState<OpsTab>((() => {
    const value = (searchParams.get("tab") || "inward") as OpsTab;
    return ["suppliers", "inward", "reconciliation", "procurement", "analytics"].includes(value) ? value : "inward";
  })());
  const [procurementTab, setProcurementTab] = React.useState<ProcurementTab>("po");
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);

  const [dashboard, setDashboard] = React.useState<PharmacyDashboard | null>(null);
  const [analytics, setAnalytics] = React.useState<PharmacyAnalytics | null>(null);
  const [suppliers, setSuppliers] = React.useState<Supplier[]>([]);
  const [reconciliations, setReconciliations] = React.useState<PharmacyReconciliation[]>([]);
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [stocks, setStocks] = React.useState<Stock[]>([]);
  const [locations, setLocations] = React.useState<InventoryLocation[]>([]);
  const [purchaseOrders, setPurchaseOrders] = React.useState<PurchaseOrder[]>([]);
  const [supplierInvoices, setSupplierInvoices] = React.useState<SupplierInvoice[]>([]);
  const [goodsReceipts, setGoodsReceipts] = React.useState<GoodsReceipt[]>([]);

  const [supplierForm, setSupplierForm] = React.useState<SupplierInput>(emptySupplier);
  const [supplierId, setSupplierId] = React.useState<string | null>(null);
  const [supplierSearch, setSupplierSearch] = React.useState("");
  const [inwardForm, setInwardForm] = React.useState<StockInwardInput>(emptyInward);
  const [inwardMedicineSearch, setInwardMedicineSearch] = React.useState("");
  const [reconForm, setReconForm] = React.useState(emptyReconciliation);
  const [sheetFile, setSheetFile] = React.useState<File | null>(null);
  const [reviewRows, setReviewRows] = React.useState<OcrExtractionRow[]>([]);
  const [selectedReconciliationId, setSelectedReconciliationId] = React.useState<string | null>(null);
  const [selectedLocationId, setSelectedLocationId] = React.useState<string>("");
  const [poForm, setPoForm] = React.useState<PurchaseOrderInput>({ supplierId: "", poNumber: "", orderDate: "", expectedDeliveryDate: null, items: [], approvalNote: null });
  const [invoiceForm, setInvoiceForm] = React.useState<SupplierInvoiceInput>({ supplierId: "", purchaseOrderId: null, invoiceNumber: "", invoiceDate: "", taxAmount: null, totalAmount: null, items: [], approvalNote: null });
  const [grnForm, setGrnForm] = React.useState<GoodsReceiptInput>({ supplierId: "", purchaseOrderId: null, supplierInvoiceId: null, receiptNumber: "", receivedAt: "", locationId: "", items: [], approvalNote: null });
  const [procurementLine, setProcurementLine] = React.useState({
    medicineId: "",
    medicineName: "",
    quantity: "",
    expectedUnitCost: "",
    unitCost: "",
    taxPercent: "",
    batchNumber: "",
    expiryDate: "",
    sellingPrice: "",
  });

  const activeMedicines = React.useMemo(() => medicines.filter((m) => m.active), [medicines]);
  const currentUserId = auth.appUserId || null;

  const medicineOptions = React.useMemo<MedicineOption[]>(() => activeMedicines.map((medicine) => ({ medicine })), [activeMedicines]);
  const selectedInwardMedicine = React.useMemo<MedicineOption | null>(
    () => medicineOptions.find((option) => option.medicine.id === inwardForm.medicineId) ?? null,
    [medicineOptions, inwardForm.medicineId],
  );
  const selectedReconciliation = React.useMemo(
    () => reconciliations.find((row) => row.id === selectedReconciliationId) ?? null,
    [reconciliations, selectedReconciliationId],
  );
  const selectedReconciliationRows = reviewRows.length ? reviewRows : selectedReconciliation?.extractedRows ?? [];
  const stockMap = React.useMemo(() => new Map(stocks.map((stock) => [stock.id, stock])), [stocks]);
  const purchaseOrderMap = React.useMemo(() => new Map(purchaseOrders.map((row) => [row.id, row])), [purchaseOrders]);
  const supplierInvoiceMap = React.useMemo(() => new Map(supplierInvoices.map((row) => [row.id, row])), [supplierInvoices]);

  const draftCount = React.useMemo(() => reconciliations.filter((row) => ["DRAFT", "REJECTED"].includes((row.status || "").toUpperCase())).length, [reconciliations]);
  const submittedCount = React.useMemo(() => reconciliations.filter((row) => (row.status || "").toUpperCase() === "SUBMITTED").length, [reconciliations]);
  const approvedCount = React.useMemo(() => reconciliations.filter((row) => (row.status || "").toUpperCase() === "APPROVED").length, [reconciliations]);
  const postedCount = React.useMemo(() => reconciliations.filter((row) => (row.status || "").toUpperCase() === "POSTED").length, [reconciliations]);
  const duplicateReviewRows = React.useMemo(() => {
    const seen = new Set<string>();
    let count = 0;
    selectedReconciliationRows.forEach((row) => {
      const key = `${(row.medicineName || "").toLowerCase()}::${(row.batchNumber || "").toLowerCase()}`;
      if (!row.medicineName && !row.batchNumber) return;
      if (seen.has(key)) {
        count += 1;
      } else {
        seen.add(key);
      }
    });
    return count;
  }, [selectedReconciliationRows]);
  const unresolvedRows = React.useMemo(() => selectedReconciliationRows.filter((row) => row.needsReview).length, [selectedReconciliationRows]);
  const missingMedicineRows = React.useMemo(() => selectedReconciliationRows.filter((row) => !(row.medicineName || "").trim()).length, [selectedReconciliationRows]);

  const filteredSuppliers = React.useMemo(() => {
    const term = supplierSearch.trim().toLowerCase();
    if (!term) return suppliers;
    return suppliers.filter((row) =>
      [row.supplierName, row.contactPerson, row.phone, row.email, row.gstNumber]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [supplierSearch, suppliers]);

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [dashboardRow, analyticsRow, supplierRows, reconRows, medicineRows, stockRows, locationRows, poRows, invoiceRows, grnRows] = await Promise.all([
        getPharmacyDashboard(auth.accessToken, auth.tenantId),
        getPharmacyAnalytics(auth.accessToken, auth.tenantId),
        listSuppliers(auth.accessToken, auth.tenantId),
        listReconciliations(auth.accessToken, auth.tenantId),
        getMedicines(auth.accessToken, auth.tenantId),
        getStocks(auth.accessToken, auth.tenantId),
        getInventoryLocations(auth.accessToken, auth.tenantId),
        getPurchaseOrders(auth.accessToken, auth.tenantId),
        getSupplierInvoices(auth.accessToken, auth.tenantId),
        getGoodsReceipts(auth.accessToken, auth.tenantId),
      ]);
      setDashboard(dashboardRow);
      setAnalytics(analyticsRow);
      setSuppliers(supplierRows);
      setReconciliations(reconRows);
      setMedicines(medicineRows);
      setStocks(stockRows);
      setLocations(locationRows);
      setPurchaseOrders(poRows);
      setSupplierInvoices(invoiceRows);
      setGoodsReceipts(grnRows);
      setSelectedLocationId((current) => current || locationRows.find((location) => location.defaultLocation)?.id || "");
      setGrnForm((current) => ({ ...current, locationId: current.locationId || locationRows.find((location) => location.defaultLocation)?.id || "" }));
      if (!selectedReconciliationId && reconRows.length) {
        setSelectedReconciliationId(reconRows[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load pharmacy operations");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, selectedReconciliationId]);

  React.useEffect(() => { void loadAll(); }, [loadAll]);

  React.useEffect(() => {
    const value = (searchParams.get("tab") || "inward") as OpsTab;
    if (["suppliers", "inward", "reconciliation", "procurement", "analytics"].includes(value)) {
      setTab(value);
    }
  }, [searchParams]);

  React.useEffect(() => {
    if (selectedReconciliation?.extractedRows?.length) {
      setReviewRows(selectedReconciliation.extractedRows);
    } else {
      setReviewRows([]);
    }
  }, [selectedReconciliationId, selectedReconciliation]);

  React.useEffect(() => {
    if (!inwardForm.medicineId) return;
    const match = activeMedicines.find((row) => row.id === inwardForm.medicineId);
    if (match && inwardMedicineSearch !== match.medicineName) {
      setInwardMedicineSearch(match.medicineName);
    }
  }, [activeMedicines, inwardForm.medicineId, inwardMedicineSearch]);

  const buildProcurementItems = React.useCallback(() => {
    if (!procurementLine.medicineId && !procurementLine.medicineName.trim()) {
      return [];
    }
    const quantity = Number(procurementLine.quantity || "0");
    if (quantity <= 0) {
      return [];
    }
    return [{
      medicineId: procurementLine.medicineId || null,
      medicineName: procurementLine.medicineName.trim() || null,
      quantity,
      expectedUnitCost: procurementLine.expectedUnitCost ? Number(procurementLine.expectedUnitCost) : null,
      unitCost: procurementLine.unitCost ? Number(procurementLine.unitCost) : null,
      taxPercent: procurementLine.taxPercent ? Number(procurementLine.taxPercent) : null,
      batchNumber: procurementLine.batchNumber.trim() || null,
      expiryDate: procurementLine.expiryDate || null,
      sellingPrice: procurementLine.sellingPrice ? Number(procurementLine.sellingPrice) : null,
    }];
  }, [procurementLine]);

  const submitSupplier = async () => {
    if (!auth.accessToken || !auth.tenantId || !supplierForm.supplierName.trim()) return;
    setSaving(true);
    setError(null);
    try {
      if (supplierId) {
        await updateSupplier(auth.accessToken, auth.tenantId, supplierId, supplierForm);
      } else {
        await createSupplier(auth.accessToken, auth.tenantId, supplierForm);
      }
      setSupplierForm(emptySupplier);
      setSupplierId(null);
      setSuccess("Supplier saved");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save supplier");
    } finally {
      setSaving(false);
    }
  };

  const submitInward = async () => {
    if (!auth.accessToken || !auth.tenantId || !inwardForm.medicineId || inwardForm.quantity <= 0) {
      setError("Select a medicine and enter a positive quantity.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await createStockInward(auth.accessToken, auth.tenantId, inwardForm);
      setInwardForm(emptyInward);
      setInwardMedicineSearch("");
      setSuccess("Stock inward recorded");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record stock inward");
    } finally {
      setSaving(false);
    }
  };

  const createDraftReconciliation = async () => {
    if (!auth.accessToken || !auth.tenantId || !reconForm.medicineId) {
      setError("Select the primary stock medicine before creating a reconciliation draft.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const created = await createStockReconciliationDraft();
      setSelectedReconciliationId(created.id);
      setReconForm(emptyReconciliation);
      setSheetFile(null);
      setSuccess(sheetFile ? "Reconciliation draft created and vendor sheet uploaded" : "Reconciliation draft created");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create reconciliation draft");
    } finally {
      setSaving(false);
    }
  };

  const createStockReconciliationDraft = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      throw new Error("Missing pharmacy session");
    }
    const created = await createReconciliation(auth.accessToken, auth.tenantId, {
      medicineId: reconForm.medicineId,
      stockBatchId: reconForm.stockBatchId || null,
      supplierId: reconForm.supplierId || null,
      locationId: selectedLocationId || null,
      physicalQuantity: reconForm.physicalQuantity ? Number(reconForm.physicalQuantity) : null,
      reason: reconForm.reason.trim() || null,
    });
    if (sheetFile) {
      const uploaded = await uploadReconciliationSheet(auth.accessToken, auth.tenantId, created.id, sheetFile);
      setReviewRows(uploaded.extractedRows);
    } else {
      setReviewRows([]);
    }
    return created;
  }, [auth.accessToken, auth.tenantId, reconForm.medicineId, reconForm.physicalQuantity, reconForm.reason, reconForm.stockBatchId, reconForm.supplierId, selectedLocationId, sheetFile]);

  const saveReviewedRows = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedReconciliationId || !reviewRows.length) return;
    setSaving(true);
    setError(null);
    try {
      await reviewReconciliationSheet(auth.accessToken, auth.tenantId, selectedReconciliationId, {
        rows: reviewRows,
        reviewNote: "Row review completed from compact reconciliation workspace",
      });
      setSuccess("Reconciliation rows reviewed");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save reviewed rows");
    } finally {
      setSaving(false);
    }
  };

  const submitRecon = async (row: PharmacyReconciliation) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      await submitReconciliationApi(auth.accessToken, auth.tenantId, row.id);
      setSuccess("Reconciliation submitted for review");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to submit reconciliation");
    } finally {
      setSaving(false);
    }
  };

  const approveRecon = async (row: PharmacyReconciliation) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      await approveReconciliation(auth.accessToken, auth.tenantId, row.id, { reason: row.reason || null });
      setSuccess("Reconciliation approved");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to approve reconciliation");
    } finally {
      setSaving(false);
    }
  };

  const rejectRecon = async (row: PharmacyReconciliation) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      await rejectReconciliation(auth.accessToken, auth.tenantId, row.id, { reason: row.reason || "Reconciliation rejected" });
      setSuccess("Reconciliation rejected");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject reconciliation");
    } finally {
      setSaving(false);
    }
  };

  const postRecon = async (row: PharmacyReconciliation) => {
    if (!auth.accessToken || !auth.tenantId) return;
    const physical = row.physicalQuantity ?? row.systemQuantity;
    setSaving(true);
    setError(null);
    try {
      await postReconciliation(auth.accessToken, auth.tenantId, row.id, {
        physicalQuantity: physical,
        reason: row.reason || "Stock count adjustment",
      });
      setSuccess("Reconciliation posted");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to post reconciliation");
    } finally {
      setSaving(false);
    }
  };

  const savePurchaseOrder = async () => {
    if (!auth.accessToken || !auth.tenantId || !poForm.supplierId || !poForm.poNumber.trim() || !poForm.orderDate) return;
    setSaving(true);
    setError(null);
    try {
      await createPurchaseOrder(auth.accessToken, auth.tenantId, { ...poForm, items: buildProcurementItems() });
      setSuccess("Purchase order saved");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save purchase order");
    } finally {
      setSaving(false);
    }
  };

  const saveSupplierInvoice = async () => {
    if (!auth.accessToken || !auth.tenantId || !invoiceForm.supplierId || !invoiceForm.invoiceNumber.trim() || !invoiceForm.invoiceDate) return;
    setSaving(true);
    setError(null);
    try {
      await createSupplierInvoice(auth.accessToken, auth.tenantId, { ...invoiceForm, items: buildProcurementItems() });
      setSuccess("Supplier invoice saved");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save supplier invoice");
    } finally {
      setSaving(false);
    }
  };

  const saveGoodsReceipt = async () => {
    if (!auth.accessToken || !auth.tenantId || !grnForm.supplierId || !grnForm.receiptNumber.trim() || !grnForm.receivedAt || !grnForm.locationId) return;
    setSaving(true);
    setError(null);
    try {
      await createGoodsReceipt(auth.accessToken, auth.tenantId, { ...grnForm, items: buildProcurementItems() });
      setSuccess("Goods receipt saved");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save goods receipt");
    } finally {
      setSaving(false);
    }
  };

  const procurementRecentRows = procurementTab === "po" ? purchaseOrders : procurementTab === "invoice" ? supplierInvoices : goodsReceipts;

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to access Pharmacy Operations.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Pharmacy Operations</Typography>
          <Typography variant="body2" color="text.secondary">
            Compact inward stock, supplier coordination, vendor batch reconciliation, and procurement workflows.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button size="small" variant="outlined" onClick={() => navigate("/inventory")}>Open Inventory</Button>
          <Button size="small" variant="outlined" onClick={() => void loadAll()}>Refresh</Button>
        </Stack>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert> : null}

      <Grid container spacing={1.25}>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Stock batches" value={dashboard?.stockBatchesCount ?? 0} helper="Across locations" />
        </Grid>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Pending dispense" value={dashboard?.pendingDispensingCount ?? 0} tone="warning" helper="Queue for pharmacy" />
        </Grid>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Low stock" value={dashboard?.lowStockCount ?? 0} tone={dashboard?.lowStockCount ? "error" : "success"} helper="Needs reorder" />
        </Grid>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Near expiry" value={dashboard?.nearExpiryCount ?? 0} tone={dashboard?.nearExpiryCount ? "warning" : "success"} helper="Watch batches" />
        </Grid>
        <Grid size={{ xs: 6, md: 2.4 }}>
          <CompactStatCard label="Today dispensed" value={dashboard?.todayDispensedCount ?? 0} helper="Operational output" />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent sx={compactCardContentSx}>
          <Tabs value={tab} onChange={(_, value) => setTab(value as OpsTab)} variant="scrollable" scrollButtons="auto">
            <Tab value="inward" label="Stock Inward" />
            <Tab value="reconciliation" label="Vendor Reconciliation" />
            <Tab value="suppliers" label="Suppliers" />
            <Tab value="procurement" label="Procurement" />
            <Tab value="analytics" label="Analytics" />
          </Tabs>
        </CardContent>
      </Card>

      {loading ? <Box sx={{ minHeight: 240, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading && tab === "suppliers" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4.2 }}>
            <CompactFilterCard
              title={supplierId ? "Edit supplier" : "Add supplier"}
              subtitle="Compact supplier master for procurement and inward stock."
            >
              <Grid container spacing={1.25}>
                <Grid size={12}><TextField size="small" fullWidth label="Supplier name" value={supplierForm.supplierName} onChange={(e) => setSupplierForm((s) => ({ ...s, supplierName: e.target.value }))} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="GSTIN" value={supplierForm.gstNumber || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, gstNumber: e.target.value || null }))} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Contact person" value={supplierForm.contactPerson || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, contactPerson: e.target.value || null }))} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Mobile" value={supplierForm.phone || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, phone: e.target.value || null }))} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Email" value={supplierForm.email || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, email: e.target.value || null }))} /></Grid>
                <Grid size={12}><TextField size="small" fullWidth label="Address" value={supplierForm.address || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, address: e.target.value || null }))} multiline minRows={2} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="supplier-active-label">Active</InputLabel>
                    <Select labelId="supplier-active-label" label="Active" value={supplierForm.active ? "true" : "false"} onChange={(e) => setSupplierForm((s) => ({ ...s, active: String(e.target.value) === "true" }))}>
                      <MenuItem value="true">Active</MenuItem>
                      <MenuItem value="false">Inactive</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
              </Grid>
              <Stack direction="row" spacing={1}>
                <Button size="small" variant="contained" onClick={() => void submitSupplier()} disabled={saving}>{supplierId ? "Update" : "Save supplier"}</Button>
                <Button size="small" onClick={() => { setSupplierForm(emptySupplier); setSupplierId(null); }}>Reset</Button>
              </Stack>
            </CompactFilterCard>
          </Grid>

          <Grid size={{ xs: 12, lg: 7.8 }}>
            <CompactFilterCard
              title="Supplier list"
              subtitle={`${filteredSuppliers.length} visible suppliers`}
              actions={<TextField size="small" placeholder="Search supplier" value={supplierSearch} onChange={(e) => setSupplierSearch(e.target.value)} sx={{ minWidth: 220 }} />}
            >
              {filteredSuppliers.length === 0 ? (
                <CompactEmptyState title="No suppliers found." subtitle="Add a supplier to use inward stock, procurement, and reconciliation workflows." />
              ) : (
                <TableContainer sx={{ ...stickyTableSx, maxHeight: 500 }}>
                  <Table stickyHeader size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Supplier</TableCell>
                        <TableCell>Last procurement</TableCell>
                        <TableCell align="right">Outstanding</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredSuppliers.map((row) => {
                        const lastInvoice = supplierInvoices.find((invoice) => invoice.supplierId === row.id);
                        const lastReceipt = goodsReceipts.find((receipt) => receipt.supplierId === row.id);
                        const lastProcurement = lastReceipt?.receivedAt || lastInvoice?.invoiceDate || null;
                        return (
                          <TableRow key={row.id} hover>
                            <TableCell>
                              <Stack spacing={0.2}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.supplierName}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {[row.contactPerson, row.phone, row.email].filter(Boolean).join(" • ") || "No contact details"}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{formatDate(lastProcurement)}</TableCell>
                            <TableCell align="right">{lastInvoice?.totalAmount != null ? `INR ${money(lastInvoice.totalAmount)}` : "-"}</TableCell>
                            <TableCell><Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} sx={compactChipSx} /></TableCell>
                            <TableCell align="right">
                              <Button
                                size="small"
                                onClick={() => {
                                  setSupplierId(row.id);
                                  setSupplierForm({
                                    supplierName: row.supplierName,
                                    contactPerson: row.contactPerson,
                                    phone: row.phone,
                                    email: row.email,
                                    gstNumber: row.gstNumber,
                                    address: row.address,
                                    active: row.active,
                                  });
                                }}
                              >
                                Edit
                              </Button>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "inward" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4.5 }}>
            <CompactFilterCard
              title="Stock inward"
              subtitle="Compact GRN-style inward entry aligned with the inventory batch workflow."
              actions={<Button size="small" variant="outlined" onClick={() => navigate("/inventory")}>Open Inventory Batch Workspace</Button>}
            >
              <Alert severity="info" sx={{ py: 0 }}>
                Keep supplier, invoice, and inward metadata here. Batch, expiry, quantity, and prices follow the same compact logic as Inventory Add Batch.
              </Alert>
              <Grid container spacing={1.25}>
                <Grid size={12}>
                  <Autocomplete<MedicineOption, false, false, false>
                    options={medicineOptions}
                    value={selectedInwardMedicine}
                    inputValue={inwardMedicineSearch}
                    onInputChange={(_, value, reason) => {
                      if (reason !== "reset") setInwardMedicineSearch(value);
                    }}
                    filterOptions={(options, state) => {
                      const term = state.inputValue.trim().toLowerCase();
                      if (!term) return options.slice(0, 20);
                      return options.filter((option) =>
                        [
                          option.medicine.medicineName,
                          option.medicine.genericName,
                          option.medicine.brandName,
                          option.medicine.barcode,
                          option.medicine.qrCode,
                          option.medicine.externalCode,
                        ]
                          .filter(Boolean)
                          .some((value) => String(value).toLowerCase().includes(term)),
                      ).slice(0, 20);
                    }}
                    onChange={(_, value) => {
                      setInwardForm((current) => ({ ...current, medicineId: value?.medicine.id || "" }));
                      setInwardMedicineSearch(value?.medicine.medicineName || "");
                    }}
                    getOptionLabel={(option) => option.medicine.medicineName}
                    isOptionEqualToValue={(option, value) => option.medicine.id === value.medicine.id}
                    renderOption={(props, option) => (
                      <Box component="li" {...props}>
                        <Stack spacing={0.2}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{option.medicine.medicineName}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {[option.medicine.genericName, option.medicine.brandName].filter(Boolean).join(" / ") || "No generic or brand"}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">{renderMedicineDescriptor(option.medicine)}</Typography>
                        </Stack>
                      </Box>
                    )}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        size="small"
                        label="Medicine"
                        placeholder="Search by name, generic, brand, barcode, QR, code"
                      />
                    )}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="inward-supplier-label">Supplier</InputLabel>
                    <Select labelId="inward-supplier-label" label="Supplier" value={inwardForm.supplierId || ""} onChange={(e) => setInwardForm((s) => ({ ...s, supplierId: String(e.target.value) || null }))}>
                      <MenuItem value="">No supplier</MenuItem>
                      {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="inward-location-label">Location</InputLabel>
                    <Select labelId="inward-location-label" label="Location" value={inwardForm.locationId || ""} onChange={(e) => setInwardForm((s) => ({ ...s, locationId: String(e.target.value) || null }))}>
                      <MenuItem value="">Default location</MenuItem>
                      {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}{location.defaultLocation ? " (Default)" : ""}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Invoice number" value={inwardForm.purchaseReferenceNumber || ""} onChange={(e) => setInwardForm((s) => ({ ...s, purchaseReferenceNumber: e.target.value || null }))} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Inward date" type="date" InputLabelProps={{ shrink: true }} value={inwardForm.purchaseDate || ""} onChange={(e) => setInwardForm((s) => ({ ...s, purchaseDate: e.target.value || null }))} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="GRN number" value={inwardForm.batchNumber || ""} onChange={(e) => setInwardForm((s) => ({ ...s, batchNumber: e.target.value || null }))} helperText="Use supplier batch/GRN identifier if available." /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Purchase reference" value={inwardForm.purchaseReferenceNumber || ""} onChange={(e) => setInwardForm((s) => ({ ...s, purchaseReferenceNumber: e.target.value || null }))} /></Grid>
                <Grid size={12}>
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 4 }}><CodeScannerField size="small" label="Barcode" value={inwardForm.barcode || ""} onChange={(value) => setInwardForm((s) => ({ ...s, barcode: value || null }))} placeholder="Scan or enter barcode" /></Grid>
                    <Grid size={{ xs: 12, md: 4 }}><CodeScannerField size="small" label="QR code" value={inwardForm.qrCode || ""} onChange={(value) => setInwardForm((s) => ({ ...s, qrCode: value || null }))} placeholder="Scan or enter QR code" /></Grid>
                    <Grid size={{ xs: 12, md: 4 }}><CodeScannerField size="small" label="External code" value={inwardForm.externalCode || ""} onChange={(value) => setInwardForm((s) => ({ ...s, externalCode: value || null }))} placeholder="Scan or enter code" /></Grid>
                  </Grid>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Expiry" type="date" InputLabelProps={{ shrink: true }} value={inwardForm.expiryDate || ""} onChange={(e) => setInwardForm((s) => ({ ...s, expiryDate: e.target.value || null }))} /></Grid>
                <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth type="number" label="Qty" value={inwardForm.quantity} onChange={(e) => setInwardForm((s) => ({ ...s, quantity: Number(e.target.value || "0") }))} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth type="number" label="Threshold" value={inwardForm.lowStockThreshold ?? ""} onChange={(e) => setInwardForm((s) => ({ ...s, lowStockThreshold: e.target.value ? Number(e.target.value) : null }))} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth type="number" label="Unit cost" value={inwardForm.unitCost ?? ""} onChange={(e) => setInwardForm((s) => ({ ...s, unitCost: e.target.value ? Number(e.target.value) : null }))} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth type="number" label="Selling price" value={inwardForm.sellingPrice ?? ""} onChange={(e) => setInwardForm((s) => ({ ...s, sellingPrice: e.target.value ? Number(e.target.value) : null }))} /></Grid>
              </Grid>
              <Stack direction="row" spacing={1}>
                <Button size="small" variant="contained" onClick={() => void submitInward()} disabled={saving}>Record inward</Button>
                <Button size="small" onClick={() => { setInwardForm(emptyInward); setInwardMedicineSearch(""); }}>Clear</Button>
              </Stack>
            </CompactFilterCard>
          </Grid>

          <Grid size={{ xs: 12, lg: 7.5 }}>
            <Stack spacing={1.5}>
              <CompactFilterCard
                title="Recent stock inward activity"
                subtitle="Compact operational visibility without switching to the full inventory page."
              >
                <TableContainer sx={{ ...stickyTableSx, maxHeight: 240 }}>
                  <Table stickyHeader size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Medicine</TableCell>
                        <TableCell>Batch</TableCell>
                        <TableCell align="right">On hand</TableCell>
                        <TableCell>Expiry</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(dashboard?.recentStockMovements || []).slice(0, 12).map((row) => (
                        <TableRow key={row.id} hover>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {medicines.find((m) => m.id === row.medicineId)?.medicineName || "Medicine"}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {row.businessReference || row.transactionType.replace(/_/g, " ")}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{row.batchNumber || stockMap.get(row.stockBatchId || "")?.batchNumber || "-"}</TableCell>
                          <TableCell align="right">{row.afterQuantity ?? "-"}</TableCell>
                          <TableCell>{formatDate(row.createdAt)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CompactFilterCard>

              <CompactFilterCard
                title="Operational guidance"
                subtitle="Keep the inward form focused on receipting. Use Inventory for broad batch edits and Medicine Master for catalogue changes."
                actions={<Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>}
              >
                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                  <Chip size="small" label={`Active medicines ${activeMedicines.length}`} sx={compactChipSx} />
                  <Chip size="small" label={`Suppliers ${suppliers.length}`} sx={compactChipSx} />
                  <Chip size="small" label={`Locations ${locations.length}`} sx={compactChipSx} />
                </Stack>
              </CompactFilterCard>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "reconciliation" ? (
        <Stack spacing={2}>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, xl: 4 }}>
              <CompactFilterCard
                title="Upload and draft"
                subtitle="Create a maker draft, attach the vendor sheet, then review extracted rows before submit."
              >
                <Alert severity="warning" sx={{ py: 0 }}>
                  AI/OCR extraction is assistive only. Pharmacist review is mandatory before submit, approve, and post. Uploaded sheets never auto-post stock.
                </Alert>
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="recon-location-label">Location</InputLabel>
                      <Select labelId="recon-location-label" label="Location" value={selectedLocationId} onChange={(e) => setSelectedLocationId(String(e.target.value))}>
                        <MenuItem value="">Default location</MenuItem>
                        {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}{location.defaultLocation ? " (Default)" : ""}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="recon-supplier-label">Supplier</InputLabel>
                      <Select labelId="recon-supplier-label" label="Supplier" value={reconForm.supplierId} onChange={(e) => setReconForm((s) => ({ ...s, supplierId: String(e.target.value) }))}>
                        <MenuItem value="">No supplier</MenuItem>
                        {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={12}>
                    <Autocomplete<MedicineOption, false, false, false>
                      options={medicineOptions}
                      value={medicineOptions.find((option) => option.medicine.id === reconForm.medicineId) ?? null}
                      onChange={(_, value) => setReconForm((current) => ({ ...current, medicineId: value?.medicine.id || "" }))}
                      getOptionLabel={(option) => option.medicine.medicineName}
                      renderInput={(params) => <TextField {...params} size="small" label="Primary stock medicine" placeholder="Required by current reconciliation API" />}
                    />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="recon-stock-label">Stock batch</InputLabel>
                      <Select
                        labelId="recon-stock-label"
                        label="Stock batch"
                        value={reconForm.stockBatchId}
                        onChange={(e) => setReconForm((s) => ({ ...s, stockBatchId: String(e.target.value) }))}
                      >
                        <MenuItem value="">Select batch</MenuItem>
                        {stocks
                          .filter((stock) => (!reconForm.medicineId || stock.medicineId === reconForm.medicineId) && (!selectedLocationId || stock.locationId === selectedLocationId))
                          .map((stock) => (
                            <MenuItem key={stock.id} value={stock.id}>
                              {stock.medicineName} - {stock.batchNumber || "Batch pending"}
                            </MenuItem>
                          ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth label="Physical qty" value={reconForm.physicalQuantity} onChange={(e) => setReconForm((s) => ({ ...s, physicalQuantity: e.target.value }))} />
                  </Grid>
                  <Grid size={12}>
                    <TextField size="small" fullWidth label="Draft note / invoice reference" value={reconForm.reason} onChange={(e) => setReconForm((s) => ({ ...s, reason: e.target.value }))} />
                  </Grid>
                  <Grid size={12}>
                    <Button size="small" variant="outlined" component="label">
                      Upload vendor sheet / invoice
                      <input hidden type="file" accept=".pdf,image/*,.csv,.xls,.xlsx" onChange={(e) => setSheetFile(e.target.files?.[0] || null)} />
                    </Button>
                    {sheetFile ? (
                      <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.5 }}>
                        Attached: {sheetFile.name}
                      </Typography>
                    ) : null}
                  </Grid>
                </Grid>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Button size="small" variant="contained" onClick={() => void createDraftReconciliation()} disabled={saving}>Save draft</Button>
                  <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  Backend gap: the current reconciliation API is draft/session based, not a true vendor-document row ledger. This UI keeps the workflow row-focused, but per-row persisted map/accept/reject actions are not exposed by the backend yet.
                </Typography>
              </CompactFilterCard>
            </Grid>

            <Grid size={{ xs: 12, xl: 8 }}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, lg: 8.6 }}>
                  <CompactFilterCard
                    title="Reconciliation grid"
                    subtitle={selectedReconciliation ? `Reviewing ${selectedReconciliation.status.toLowerCase()} session for ${selectedReconciliation.medicineName || "stock item"}` : "Select a reconciliation session to review rows."}
                    actions={<Button size="small" variant="outlined" onClick={() => void saveReviewedRows()} disabled={saving || !selectedReconciliationId || !reviewRows.length}>Save row review</Button>}
                  >
                    {selectedReconciliationRows.length === 0 ? (
                      <CompactEmptyState title="No extracted rows yet." subtitle="Upload a vendor sheet to start row-based review. Drafts without a sheet still appear in the session list below." />
                    ) : (
                      <TableContainer sx={{ ...stickyTableSx, maxHeight: 460 }}>
                        <Table stickyHeader size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Vendor medicine</TableCell>
                              <TableCell>Matched medicine</TableCell>
                              <TableCell>Batch</TableCell>
                              <TableCell>Expiry</TableCell>
                              <TableCell align="right">Vendor qty</TableCell>
                              <TableCell align="right">System qty</TableCell>
                              <TableCell align="right">Variance</TableCell>
                              <TableCell>Status</TableCell>
                              <TableCell align="right">Actions</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {selectedReconciliationRows.map((row, index) => {
                              const systemQty = index === 0 ? selectedReconciliation?.systemQuantity ?? null : null;
                              const variance = systemQty != null && row.physicalQuantity != null ? row.physicalQuantity - systemQty : null;
                              return (
                                <TableRow key={`${row.rowNumber}-${index}`} hover>
                                  <TableCell>
                                    <TextField
                                      size="small"
                                      fullWidth
                                      value={row.medicineName || ""}
                                      onChange={(e) => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, medicineName: e.target.value } : item))}
                                    />
                                  </TableCell>
                                  <TableCell>
                                    <Stack spacing={0.2}>
                                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                        {row.medicineCode || selectedReconciliation?.medicineName || "Row-level mapping backend pending"}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        Use Medicine Master for missing catalogue items.
                                      </Typography>
                                    </Stack>
                                  </TableCell>
                                  <TableCell>
                                    <TextField size="small" fullWidth value={row.batchNumber || ""} onChange={(e) => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, batchNumber: e.target.value } : item))} />
                                  </TableCell>
                                  <TableCell>
                                    <TextField size="small" fullWidth type="date" InputLabelProps={{ shrink: true }} value={row.expiryDate || ""} onChange={(e) => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, expiryDate: e.target.value || null } : item))} />
                                  </TableCell>
                                  <TableCell align="right">
                                    <TextField size="small" type="number" value={row.physicalQuantity ?? ""} onChange={(e) => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, physicalQuantity: e.target.value ? Number(e.target.value) : null } : item))} sx={{ width: 96 }} />
                                  </TableCell>
                                  <TableCell align="right">{systemQty ?? "-"}</TableCell>
                                  <TableCell align="right">{variance ?? "-"}</TableCell>
                                  <TableCell>
                                    <Chip size="small" label={row.needsReview ? "Needs review" : "Accepted"} color={row.needsReview ? "warning" : "success"} sx={compactChipSx} />
                                  </TableCell>
                                  <TableCell align="right">
                                    <Stack direction="row" spacing={0.5} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                                      <Button size="small" onClick={() => navigate("/pharmacy/medicines")}>Map</Button>
                                      <Button size="small" onClick={() => navigate("/pharmacy/medicines")}>Create</Button>
                                      <Button size="small" onClick={() => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, needsReview: false } : item))}>Accept</Button>
                                      <Button size="small" color="inherit" onClick={() => setReviewRows((current) => current.map((item, idx) => idx === index ? { ...item, needsReview: true, notes: "Rejected during manual review" } : item))}>Reject</Button>
                                    </Stack>
                                  </TableCell>
                                </TableRow>
                              );
                            })}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </CompactFilterCard>
                </Grid>

                <Grid size={{ xs: 12, lg: 3.4 }}>
                  <Stack spacing={1.5} sx={{ position: { lg: "sticky" }, top: { lg: 84 } }}>
                    <CompactFilterCard title="Summary" subtitle="Review progress">
                      <Stack spacing={0.75}>
                        <Chip size="small" label={`Total rows ${selectedReconciliationRows.length}`} sx={compactChipSx} />
                        <Chip size="small" label={`Unresolved ${unresolvedRows}`} color={unresolvedRows ? "warning" : "success"} sx={compactChipSx} />
                        <Chip size="small" label={`Missing medicines ${missingMedicineRows}`} color={missingMedicineRows ? "error" : "success"} sx={compactChipSx} />
                        <Chip size="small" label={`Duplicates ${duplicateReviewRows}`} color={duplicateReviewRows ? "warning" : "success"} sx={compactChipSx} />
                        <Chip size="small" label={`Variance ${selectedReconciliation?.varianceQuantity ?? 0}`} sx={compactChipSx} />
                      </Stack>
                    </CompactFilterCard>

                    <CompactFilterCard
                      title="Selected session"
                      subtitle={
                        selectedReconciliation
                          ? `${selectedReconciliation.batchNumber || "No batch"} • ${selectedReconciliation.locationName || "Default location"}`
                          : "No session selected"
                      }
                    >
                      {selectedReconciliation ? (
                        <Stack spacing={0.45}>
                          <Typography variant="caption" color="text.secondary">Created {formatDateTime(selectedReconciliation.createdAt)}</Typography>
                          <Typography variant="caption" color="text.secondary">Maker: {actorLabel(selectedReconciliation.createdBy, currentUserId, "Pharmacy user")}</Typography>
                          <Typography variant="caption" color="text.secondary">Submitted: {actorLabel(selectedReconciliation.submittedBy, currentUserId, "Pharmacy reviewer")}</Typography>
                          <Typography variant="caption" color="text.secondary">Reviewer: {actorLabel(selectedReconciliation.reviewedBy, currentUserId, "Approving reviewer")}</Typography>
                          <Typography variant="caption" color="text.secondary">Posted: {actorLabel(selectedReconciliation.postedBy, currentUserId, "Posting pharmacist")}</Typography>
                          <Chip size="small" label={selectedReconciliation.status} color={badgeTone(selectedReconciliation.status)} sx={compactChipSx} />
                        </Stack>
                      ) : (
                        <Typography variant="body2" color="text.secondary">Select a row below to review and act on the session.</Typography>
                      )}
                    </CompactFilterCard>
                  </Stack>
                </Grid>
              </Grid>
            </Grid>
          </Grid>

          <CompactFilterCard
            title="Session queue"
            subtitle="Maker-checker sessions remain compact and actionable."
            actions={(
              <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                <Chip size="small" label={`Draft ${draftCount}`} variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`Submitted ${submittedCount}`} color="warning" variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`Approved ${approvedCount}`} color="info" variant="outlined" sx={compactChipSx} />
                <Chip size="small" label={`Posted ${postedCount}`} color="success" variant="outlined" sx={compactChipSx} />
              </Stack>
            )}
          >
            {reconciliations.length === 0 ? (
              <CompactEmptyState title="No reconciliation sessions yet." subtitle="Create a draft, upload the vendor sheet, then review rows here." />
            ) : (
              <TableContainer sx={{ ...stickyTableSx, maxHeight: 280 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Session</TableCell>
                      <TableCell align="right">System</TableCell>
                      <TableCell align="right">Physical</TableCell>
                      <TableCell align="right">Variance</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {reconciliations.map((row) => (
                      <TableRow key={row.id} hover selected={row.id === selectedReconciliationId} onClick={() => setSelectedReconciliationId(row.id)} sx={{ cursor: "pointer" }}>
                        <TableCell>
                          <Stack spacing={0.2}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.medicineName || "Medicine"}</Typography>
                            <Typography variant="caption" color="text.secondary">{row.batchNumber || "No batch"} • {row.locationName || "Default location"}</Typography>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">{row.systemQuantity}</TableCell>
                        <TableCell align="right">{row.physicalQuantity ?? "-"}</TableCell>
                        <TableCell align="right">{row.varianceQuantity ?? "-"}</TableCell>
                        <TableCell>
                          <Chip size="small" label={row.status} color={badgeTone(row.status)} sx={compactChipSx} />
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={0.5} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                            {["DRAFT", "REJECTED"].includes((row.status || "").toUpperCase()) ? (
                              <Button size="small" onClick={(event) => { event.stopPropagation(); void submitRecon(row); }} disabled={saving}>Submit</Button>
                            ) : null}
                            {row.status === "SUBMITTED" ? (
                              currentUserId && row.createdBy === currentUserId ? (
                                <Chip size="small" label="Maker cannot approve own reconciliation" color="warning" variant="outlined" sx={compactChipSx} />
                              ) : (
                                <>
                                  <Button size="small" onClick={(event) => { event.stopPropagation(); void approveRecon(row); }} disabled={saving}>Approve</Button>
                                  <Button size="small" color="inherit" onClick={(event) => { event.stopPropagation(); void rejectRecon(row); }} disabled={saving}>Reject</Button>
                                </>
                              )
                            ) : null}
                            {row.status === "APPROVED" ? (
                              <Button size="small" onClick={(event) => { event.stopPropagation(); void postRecon(row); }} disabled={saving}>Post</Button>
                            ) : null}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </CompactFilterCard>
        </Stack>
      ) : null}

      {!loading && tab === "procurement" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4.8 }}>
            <CompactFilterCard
              title="Procurement workflow"
              subtitle="Compact tabs instead of three oversized forms."
            >
              <Tabs value={procurementTab} onChange={(_, value) => setProcurementTab(value as ProcurementTab)} variant="scrollable" scrollButtons="auto">
                <Tab value="po" label="PO" />
                <Tab value="invoice" label="Invoice" />
                <Tab value="grn" label="GRN" />
              </Tabs>

              {procurementTab === "po" ? (
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="po-supplier-label">Supplier</InputLabel>
                      <Select labelId="po-supplier-label" label="Supplier" value={poForm.supplierId} onChange={(e) => setPoForm((s) => ({ ...s, supplierId: String(e.target.value) }))}>
                        <MenuItem value="">Select supplier</MenuItem>
                        {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="PO number" value={poForm.poNumber} onChange={(e) => setPoForm((s) => ({ ...s, poNumber: e.target.value }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Order date" type="date" InputLabelProps={{ shrink: true }} value={poForm.orderDate} onChange={(e) => setPoForm((s) => ({ ...s, orderDate: e.target.value }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Expected delivery" type="date" InputLabelProps={{ shrink: true }} value={poForm.expectedDeliveryDate || ""} onChange={(e) => setPoForm((s) => ({ ...s, expectedDeliveryDate: e.target.value || null }))} /></Grid>
                </Grid>
              ) : null}

              {procurementTab === "invoice" ? (
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="invoice-supplier-label">Supplier</InputLabel>
                      <Select labelId="invoice-supplier-label" label="Supplier" value={invoiceForm.supplierId} onChange={(e) => setInvoiceForm((s) => ({ ...s, supplierId: String(e.target.value) }))}>
                        <MenuItem value="">Select supplier</MenuItem>
                        {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="invoice-po-label">Purchase order</InputLabel>
                      <Select labelId="invoice-po-label" label="Purchase order" value={invoiceForm.purchaseOrderId || ""} onChange={(e) => setInvoiceForm((s) => ({ ...s, purchaseOrderId: String(e.target.value) || null }))}>
                        <MenuItem value="">No PO</MenuItem>
                        {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Invoice number" value={invoiceForm.invoiceNumber} onChange={(e) => setInvoiceForm((s) => ({ ...s, invoiceNumber: e.target.value }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Invoice date" type="date" InputLabelProps={{ shrink: true }} value={invoiceForm.invoiceDate} onChange={(e) => setInvoiceForm((s) => ({ ...s, invoiceDate: e.target.value }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth type="number" label="Tax amount" value={invoiceForm.taxAmount ?? ""} onChange={(e) => setInvoiceForm((s) => ({ ...s, taxAmount: e.target.value ? Number(e.target.value) : null }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth type="number" label="Total amount" value={invoiceForm.totalAmount ?? ""} onChange={(e) => setInvoiceForm((s) => ({ ...s, totalAmount: e.target.value ? Number(e.target.value) : null }))} /></Grid>
                </Grid>
              ) : null}

              {procurementTab === "grn" ? (
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="grn-supplier-label">Supplier</InputLabel>
                      <Select labelId="grn-supplier-label" label="Supplier" value={grnForm.supplierId} onChange={(e) => setGrnForm((s) => ({ ...s, supplierId: String(e.target.value) }))}>
                        <MenuItem value="">Select supplier</MenuItem>
                        {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="grn-location-select">Location</InputLabel>
                      <Select labelId="grn-location-select" label="Location" value={grnForm.locationId || selectedLocationId} onChange={(e) => setGrnForm((s) => ({ ...s, locationId: String(e.target.value) }))}>
                        <MenuItem value="">Default location</MenuItem>
                        {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Receipt number" value={grnForm.receiptNumber} onChange={(e) => setGrnForm((s) => ({ ...s, receiptNumber: e.target.value }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField size="small" fullWidth label="Received at" type="datetime-local" InputLabelProps={{ shrink: true }} value={grnForm.receivedAt} onChange={(e) => setGrnForm((s) => ({ ...s, receivedAt: e.target.value }))} /></Grid>
                </Grid>
              ) : null}

              <Grid container spacing={1.25}>
                <Grid size={{ xs: 12, md: 7 }}><TextField size="small" fullWidth label="Line item name" value={procurementLine.medicineName} onChange={(e) => setProcurementLine((s) => ({ ...s, medicineName: e.target.value }))} /></Grid>
                <Grid size={{ xs: 12, md: 5 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel id="procurement-medicine-label">Medicine master</InputLabel>
                    <Select labelId="procurement-medicine-label" label="Medicine master" value={procurementLine.medicineId} onChange={(e) => setProcurementLine((s) => ({ ...s, medicineId: String(e.target.value) }))}>
                      <MenuItem value="">Manual line</MenuItem>
                      {medicines.map((medicine) => <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth type="number" label="Qty" value={procurementLine.quantity} onChange={(e) => setProcurementLine((s) => ({ ...s, quantity: e.target.value }))} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth type="number" label="Expected/unit cost" value={procurementTab === "po" ? procurementLine.expectedUnitCost : procurementLine.unitCost} onChange={(e) => setProcurementLine((s) => ({ ...s, [procurementTab === "po" ? "expectedUnitCost" : "unitCost"]: e.target.value }))} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth type="number" label="Tax %" value={procurementLine.taxPercent} onChange={(e) => setProcurementLine((s) => ({ ...s, taxPercent: e.target.value }))} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Batch" value={procurementLine.batchNumber} onChange={(e) => setProcurementLine((s) => ({ ...s, batchNumber: e.target.value }))} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth label="Expiry" type="date" InputLabelProps={{ shrink: true }} value={procurementLine.expiryDate} onChange={(e) => setProcurementLine((s) => ({ ...s, expiryDate: e.target.value }))} /></Grid>
                <Grid size={{ xs: 12, md: 4 }}><TextField size="small" fullWidth type="number" label="Selling price" value={procurementLine.sellingPrice} onChange={(e) => setProcurementLine((s) => ({ ...s, sellingPrice: e.target.value }))} /></Grid>
              </Grid>

              <Stack direction="row" spacing={1}>
                {procurementTab === "po" ? <Button size="small" variant="contained" onClick={() => void savePurchaseOrder()} disabled={saving}>Save PO</Button> : null}
                {procurementTab === "invoice" ? <Button size="small" variant="contained" onClick={() => void saveSupplierInvoice()} disabled={saving}>Save invoice</Button> : null}
                {procurementTab === "grn" ? <Button size="small" variant="contained" onClick={() => void saveGoodsReceipt()} disabled={saving}>Save GRN</Button> : null}
              </Stack>
            </CompactFilterCard>
          </Grid>

          <Grid size={{ xs: 12, lg: 7.2 }}>
            <Stack spacing={1.5}>
              <CompactFilterCard title="Workflow summary" subtitle="Compact counters instead of large procurement blocks.">
                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                  <Chip size="small" label={`POs ${purchaseOrders.length}`} sx={compactChipSx} />
                  <Chip size="small" label={`Invoices ${supplierInvoices.length}`} sx={compactChipSx} />
                  <Chip size="small" label={`GRNs ${goodsReceipts.length}`} sx={compactChipSx} />
                </Stack>
              </CompactFilterCard>

              <CompactFilterCard title="Recent workflow records" subtitle={`Showing recent ${procurementTab.toUpperCase()} entries`}>
                <TableContainer sx={{ ...stickyTableSx, maxHeight: 420 }}>
                  <Table stickyHeader size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Reference</TableCell>
                        <TableCell>Date</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {procurementRecentRows.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={4}>
                            <CompactEmptyState title={`No ${procurementTab.toUpperCase()} records yet.`} subtitle="Save a compact record from the left panel to populate this queue." />
                          </TableCell>
                        </TableRow>
                      ) : procurementTab === "po" ? (
                        purchaseOrders.map((row) => (
                          <TableRow key={row.id} hover>
                            <TableCell>
                              <Stack spacing={0.2}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.poNumber}</Typography>
                                <Typography variant="caption" color="text.secondary">{row.supplierName || "Supplier pending"}</Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{formatDate(row.orderDate)}</TableCell>
                            <TableCell><Chip size="small" label={row.matchingStatus || "Draft"} sx={compactChipSx} /></TableCell>
                            <TableCell align="right">-</TableCell>
                          </TableRow>
                        ))
                      ) : procurementTab === "invoice" ? (
                        supplierInvoices.map((row) => (
                          <TableRow key={row.id} hover>
                            <TableCell>
                              <Stack spacing={0.2}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.invoiceNumber}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {row.supplierName || "Supplier pending"}
                                  {row.purchaseOrderId ? ` • PO ${purchaseOrderMap.get(row.purchaseOrderId)?.poNumber || "linked"}` : ""}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{formatDate(row.invoiceDate)}</TableCell>
                            <TableCell><Chip size="small" label={row.matchingStatus || "Draft"} sx={compactChipSx} /></TableCell>
                            <TableCell align="right">-</TableCell>
                          </TableRow>
                        ))
                      ) : (
                        goodsReceipts.map((row) => (
                          <TableRow key={row.id} hover>
                            <TableCell>
                              <Stack spacing={0.2}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.receiptNumber}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {row.supplierName || "Supplier pending"}
                                  {row.supplierInvoiceId ? ` • Inv ${supplierInvoiceMap.get(row.supplierInvoiceId)?.invoiceNumber || "linked"}` : ""}
                                  {row.purchaseOrderId ? ` • PO ${purchaseOrderMap.get(row.purchaseOrderId)?.poNumber || "linked"}` : ""}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{formatDateTime(row.receivedAt)}</TableCell>
                            <TableCell><Chip size="small" label={row.matchingStatus} color={badgeTone(row.matchingStatus)} sx={compactChipSx} /></TableCell>
                            <TableCell align="right">
                              <Button
                                size="small"
                                disabled={saving || row.confirmedAt !== null}
                                onClick={async () => {
                                  if (!auth.accessToken || !auth.tenantId) return;
                                  setSaving(true);
                                  setError(null);
                                  try {
                                    await confirmGoodsReceipt(auth.accessToken, auth.tenantId, row.id, row.approvalNote || "Variance approved");
                                    await loadAll();
                                    setSuccess("GRN confirmed");
                                  } catch (err) {
                                    setError(err instanceof Error ? err.message : "Failed to confirm GRN");
                                  } finally {
                                    setSaving(false);
                                  }
                                }}
                              >
                                Confirm
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CompactFilterCard>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "analytics" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <CompactFilterCard title="Fast moving medicines" subtitle="Dense pharmacy demand view.">
              {analytics?.fastMovingMedicines?.length ? (
                <TableContainer sx={{ ...stickyTableSx, maxHeight: 340 }}>
                  <Table stickyHeader size="small">
                    <TableHead><TableRow><TableCell>Medicine</TableCell><TableCell align="right">Dispensed</TableCell><TableCell align="right">Available</TableCell></TableRow></TableHead>
                    <TableBody>
                      {analytics.fastMovingMedicines.map((row) => (
                        <TableRow key={row.medicineId}>
                          <TableCell>{row.medicineName || "Medicine"}</TableCell>
                          <TableCell align="right">{row.dispensedQuantity}</TableCell>
                          <TableCell align="right">{row.availableQuantity}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              ) : <CompactEmptyState title="No fast-moving analytics yet." subtitle="Dispensing data will populate this panel." />}
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <CompactFilterCard title="Expiry and value" subtitle="Compact stock risk summary.">
              <Stack spacing={1}>
                <Chip size="small" label={`Stock value estimate INR ${money(analytics?.stockValueEstimate ?? 0)}`} color="info" sx={compactChipSx} />
                <Chip size="small" label={`Low stock medicines ${analytics?.lowStockMedicines?.length ?? 0}`} sx={compactChipSx} />
                <Chip size="small" label={`Expiry-risk batches ${analytics?.expiryRiskMedicines?.length ?? 0}`} sx={compactChipSx} />
              </Stack>
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  );
}
