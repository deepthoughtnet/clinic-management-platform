import * as React from "react";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
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
  IconButton,
  Menu,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import ExpandMoreRounded from "@mui/icons-material/ExpandMoreRounded";
import MoreHorizRounded from "@mui/icons-material/MoreHorizRounded";
import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, WorkflowGuide, compactAccordionSx, compactCardContentSx, compactFormGridSx, compactFormSx } from "../../components/compact/CompactUi";
import CodeScannerField from "../../components/pharmacy/CodeScannerField";
import RequiredLabel from "../../components/forms/RequiredLabel";
import ConfigurableGrid, { type ConfigurableGridColumn } from "../../shared/components/configurable-grid/ConfigurableGrid";
import ManageColumnsPopover from "../../shared/components/configurable-grid/ManageColumnsPopover";
import { useColumnVisibility } from "../../shared/components/configurable-grid/useColumnVisibility";
import { fileUploadSchema, firstZodError, hasDuplicateMedicineMaster, mapZodErrors, medicineMasterSchema } from "@deepthoughtnet/form-validation-kit";
import {
  activateMedicine,
  createMedicine,
  deactivateMedicine,
  getMedicineImportTemplate,
  getMedicines,
  getStocks,
  importMedicinesCsv,
  updateMedicine,
  type Medicine,
  type MedicineImportResult,
  type MedicineInput,
  type MedicineType,
  type Stock,
} from "../../api/clinicApi";
import {
  buildMedicineTemplateCsv,
  MEDICINE_IMPORT_COLUMNS,
  parseMedicineImportPreview,
  type MedicineImportPreview,
} from "./medicineCsv";

const MEDICINE_MASTER_COLUMNS_STORAGE_KEY = "arogia.pharmacy.medicineMaster.visibleColumns";

const medicineTypeOptions: Array<{ value: MedicineType; label: string }> = [
  { value: "TABLET", label: "Tablet" },
  { value: "CAPSULE", label: "Capsule" },
  { value: "SYRUP", label: "Syrup" },
  { value: "INJECTION", label: "Injection" },
  { value: "DROP", label: "Drop" },
  { value: "OINTMENT", label: "Ointment" },
  { value: "OTHER", label: "Other" },
];

const emptyForm: MedicineInput = {
  medicineName: "",
  medicineType: "TABLET",
  barcode: null,
  qrCode: null,
  externalCode: null,
  genericName: null,
  brandName: null,
  category: null,
  dosageForm: null,
  strength: null,
  unit: null,
  manufacturer: null,
  defaultDosage: null,
  defaultFrequency: null,
  defaultDurationDays: null,
  defaultTiming: null,
  defaultInstructions: null,
  defaultPrice: null,
  taxRate: null,
  active: true,
};

function downloadCsv(filename: string, csv: string) {
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

function formatPrice(value: number | null | undefined): string {
  if (typeof value !== "number" || Number.isNaN(value)) return "-";
  return value.toFixed(2);
}

function mapMedicineSaveError(error: unknown): string {
  const message = error instanceof Error ? error.message : "Failed to save medicine";
  const normalized = message.toLowerCase();
  if (normalized.includes("medicinename")) return "Medicine name is required.";
  if (normalized.includes("medicinetype")) return "Medicine type is required.";
  if (normalized.includes("medicine already exists")) return "A medicine with this name already exists.";
  if (normalized.includes("barcode already exists")) return "This barcode is already linked to another medicine.";
  if (normalized.includes("external code already exists")) return "This external code is already linked to another medicine.";
  return message;
}

const medicineFieldOrder = [
  "medicineName",
  "medicineType",
  "active",
  "strength",
  "barcode",
  "qrCode",
  "externalCode",
  "genericName",
  "brandName",
  "category",
  "dosageForm",
  "unit",
  "manufacturer",
  "defaultDosage",
  "defaultFrequency",
  "defaultDurationDays",
  "defaultTiming",
  "defaultInstructions",
  "defaultPrice",
  "taxRate",
] as const;

function focusFirstMedicineError(fieldErrors: Record<string, string>, fieldRefs: React.MutableRefObject<Record<string, HTMLElement | null>>) {
  for (const field of medicineFieldOrder) {
    if (fieldErrors[field]) {
      const element = fieldRefs.current[field];
      if (element && typeof element.focus === "function") {
        element.focus();
      }
      break;
    }
  }
}

function formForRow(row: Medicine): MedicineInput {
  return {
    medicineName: row.medicineName,
    medicineType: row.medicineType,
    barcode: row.barcode,
    qrCode: row.qrCode,
    externalCode: row.externalCode,
    genericName: row.genericName,
    brandName: row.brandName,
    category: row.category,
    dosageForm: row.dosageForm,
    strength: row.strength,
    unit: row.unit,
    manufacturer: row.manufacturer,
    defaultDosage: row.defaultDosage,
    defaultFrequency: row.defaultFrequency,
    defaultDurationDays: row.defaultDurationDays,
    defaultTiming: row.defaultTiming,
    defaultInstructions: row.defaultInstructions,
    defaultPrice: row.defaultPrice,
    taxRate: row.taxRate,
    active: row.active,
  };
}

function utcDayNumber(dateValue: string) {
  const [year, month, day] = dateValue.split("-").map((value) => Number(value));
  return Date.UTC(year, month - 1, day);
}

function todayUtcDayNumber() {
  const now = new Date();
  return Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
}

function daysUntil(dateValue: string | null | undefined) {
  if (!dateValue) return Number.POSITIVE_INFINITY;
  return Math.floor((utcDayNumber(dateValue) - todayUtcDayNumber()) / 86_400_000);
}

export default function MedicineMasterPage() {
  const auth = useAuth();
  const [rows, setRows] = React.useState<Medicine[]>([]);
  const [stocks, setStocks] = React.useState<Stock[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const [editorOpen, setEditorOpen] = React.useState(false);
  const [importPreviewOpen, setImportPreviewOpen] = React.useState(false);
  const [importResultOpen, setImportResultOpen] = React.useState(false);
  const [preview, setPreview] = React.useState<MedicineImportPreview | null>(null);
  const [previewFile, setPreviewFile] = React.useState<File | null>(null);
  const [importResult, setImportResult] = React.useState<MedicineImportResult | null>(null);
  const [advancedOpen, setAdvancedOpen] = React.useState(false);
  const [advancedFiltersOpen, setAdvancedFiltersOpen] = React.useState(false);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const [editing, setEditing] = React.useState<Medicine | null>(null);
  const [form, setForm] = React.useState<MedicineInput>(emptyForm);
  const [statusFilter, setStatusFilter] = React.useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
  const [typeFilter, setTypeFilter] = React.useState<"ALL" | MedicineType>("ALL");
  const [categoryFilter, setCategoryFilter] = React.useState<string>("ALL");
  const [inventoryFilter, setInventoryFilter] = React.useState<"ALL" | "LOW_STOCK" | "EXPIRING_SOON" | "OUT_OF_STOCK">("ALL");
  const [barcodeFilter, setBarcodeFilter] = React.useState<"ALL" | "HAS_BARCODE" | "MISSING_BARCODE">("ALL");
  const [quickFilter, setQuickFilter] = React.useState<"NONE" | "ACTIVE" | "LOW_STOCK" | "EXPIRING_SOON" | "MISSING_BARCODE" | "RECENTLY_ADDED">("NONE");
  const [priceMin, setPriceMin] = React.useState("");
  const [priceMax, setPriceMax] = React.useState("");
  const [actionAnchor, setActionAnchor] = React.useState<HTMLElement | null>(null);
  const [actionRow, setActionRow] = React.useState<Medicine | null>(null);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const fieldRefs = React.useRef<Record<string, HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null>>({});

  const canManage = auth.hasPermission("inventory.manage")
    || auth.hasPermission("vaccination.manage")
    || auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("PHARMACIST")
    || auth.rolesUpper.includes("PHARMACY")
    || auth.rolesUpper.includes("PHARMA")
    || auth.rolesUpper.includes("PHARMACY_INVENTORY_MANAGER");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [medicineRows, stockRows] = await Promise.all([
        getMedicines(auth.accessToken, auth.tenantId),
        getStocks(auth.accessToken, auth.tenantId),
      ]);
      setRows(medicineRows);
      setStocks(stockRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load medicines");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  React.useEffect(() => {
    if (editing) {
      setEditorOpen(true);
      return;
    }
    setEditorOpen(false);
  }, [editing, rows.length]);

  const stockByMedicineId = React.useMemo(() => {
    const next = new Map<string, Stock[]>();
    stocks.forEach((stock) => {
      const current = next.get(stock.medicineId) || [];
      current.push(stock);
      next.set(stock.medicineId, current);
    });
    return next;
  }, [stocks]);

  const filtered = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    const minPrice = priceMin.trim() ? Number(priceMin) : null;
    const maxPrice = priceMax.trim() ? Number(priceMax) : null;
    return rows.filter((row) => {
      const medicineStocks = stockByMedicineId.get(row.id) || [];
      const totalStock = medicineStocks.reduce((sum, stock) => sum + stock.quantityOnHand, 0);
      const lowStockHit = medicineStocks.some((stock) => stock.lowStockThreshold != null && stock.quantityOnHand <= stock.lowStockThreshold);
      const expiringSoonHit = medicineStocks.some((stock) => {
        const diffDays = daysUntil(stock.expiryDate);
        return diffDays >= 0 && diffDays <= 30;
      });
      const hasBarcode = Boolean((row.barcode || "").trim());
      const recentlyAdded = Date.now() - new Date(row.createdAt).getTime() <= 30 * 24 * 60 * 60 * 1000;
      const matchesTerm = !term || [
        row.medicineName,
        row.barcode,
        row.qrCode,
        row.externalCode,
        row.genericName,
        row.brandName,
        row.category,
        row.dosageForm,
        row.strength,
        row.unit,
        row.manufacturer,
        row.defaultDosage,
        row.defaultFrequency,
        row.defaultInstructions,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
      const matchesStatus = statusFilter === "ALL" || (statusFilter === "ACTIVE" ? row.active : !row.active);
      const matchesType = typeFilter === "ALL" || row.medicineType === typeFilter;
      const matchesCategory = categoryFilter === "ALL" || (row.category || "Uncategorized") === categoryFilter;
      const matchesInventory = inventoryFilter === "ALL"
        || (inventoryFilter === "LOW_STOCK" && lowStockHit)
        || (inventoryFilter === "EXPIRING_SOON" && expiringSoonHit)
        || (inventoryFilter === "OUT_OF_STOCK" && totalStock <= 0);
      const matchesBarcode = barcodeFilter === "ALL"
        || (barcodeFilter === "HAS_BARCODE" && hasBarcode)
        || (barcodeFilter === "MISSING_BARCODE" && !hasBarcode);
      const matchesPrice = (minPrice == null || (row.defaultPrice ?? 0) >= minPrice)
        && (maxPrice == null || (row.defaultPrice ?? 0) <= maxPrice);
      const matchesQuick = quickFilter === "NONE"
        || (quickFilter === "ACTIVE" && row.active)
        || (quickFilter === "LOW_STOCK" && lowStockHit)
        || (quickFilter === "EXPIRING_SOON" && expiringSoonHit)
        || (quickFilter === "MISSING_BARCODE" && !hasBarcode)
        || (quickFilter === "RECENTLY_ADDED" && recentlyAdded);
      return matchesTerm && matchesStatus && matchesType && matchesCategory && matchesInventory && matchesBarcode && matchesPrice && matchesQuick;
    });
  }, [barcodeFilter, categoryFilter, inventoryFilter, priceMax, priceMin, quickFilter, rows, search, statusFilter, stockByMedicineId, typeFilter]);

  const activeCount = React.useMemo(() => rows.filter((row) => row.active).length, [rows]);
  const inactiveCount = rows.length - activeCount;
  const lowStockCount = React.useMemo(
    () => rows.filter((row) => (stockByMedicineId.get(row.id) || []).some((stock) => stock.lowStockThreshold != null && stock.quantityOnHand <= stock.lowStockThreshold)).length,
    [rows, stockByMedicineId],
  );
  const expiringSoonCount = React.useMemo(
    () => rows.filter((row) => (stockByMedicineId.get(row.id) || []).some((stock) => {
      const diffDays = daysUntil(stock.expiryDate);
      return diffDays >= 0 && diffDays <= 30;
    })).length,
    [rows, stockByMedicineId],
  );
  const missingBarcodeCount = React.useMemo(() => rows.filter((row) => !(row.barcode || "").trim()).length, [rows]);
  const categoryOptions = React.useMemo(
    () => Array.from(new Set(rows.map((row) => row.category || "Uncategorized"))).sort((a, b) => a.localeCompare(b)),
    [rows],
  );

  const openCreate = React.useCallback(() => {
    setEditing(null);
    setForm(emptyForm);
    setFieldErrors({});
    setError(null);
    setAdvancedOpen(false);
    setEditorOpen(true);
  }, []);

  const openEdit = React.useCallback((row: Medicine) => {
    setEditing(row);
    setForm(formForRow(row));
    setFieldErrors({});
    setError(null);
    setAdvancedOpen(true);
    setEditorOpen(true);
  }, []);

  const closeEditor = React.useCallback(() => {
    setEditing(null);
    setForm(emptyForm);
    setFieldErrors({});
    setError(null);
    setAdvancedOpen(false);
    setEditorOpen(false);
  }, []);

  const resetEditor = React.useCallback(() => {
    setForm(editing ? formForRow(editing) : emptyForm);
    setFieldErrors({});
    setError(null);
  }, [editing]);

  const validateMedicineForm = React.useCallback(() => {
    const parsed = medicineMasterSchema.safeParse(form);
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setFieldErrors(errors);
      setError(firstZodError(parsed.error));
      window.setTimeout(() => focusFirstMedicineError(errors, fieldRefs), 0);
      return null;
    }

    const duplicate = hasDuplicateMedicineMaster(parsed.data, rows, editing?.id);
    if (duplicate) {
      const message = "A medicine with this name, strength, and type already exists.";
      const errors = {
        medicineName: message,
        strength: message,
        medicineType: message,
      };
      setFieldErrors(errors);
      setError(message);
      window.setTimeout(() => focusFirstMedicineError(errors, fieldRefs), 0);
      return null;
    }

    return parsed.data;
  }, [editing?.id, form, rows]);

  const save = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const validated = validateMedicineForm();
    if (!validated) return;

    setSaving(true);
    setError(null);
    try {
      if (editing) {
        await updateMedicine(auth.accessToken, auth.tenantId, editing.id, validated);
      } else {
        await createMedicine(auth.accessToken, auth.tenantId, validated);
      }
      closeEditor();
      await load();
    } catch (err) {
      setError(mapMedicineSaveError(err));
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, closeEditor, editing, load, validateMedicineForm]);

  const toggleActive = React.useCallback(async (row: Medicine) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      if (row.active) await deactivateMedicine(auth.accessToken, auth.tenantId, row.id);
      else await activateMedicine(auth.accessToken, auth.tenantId, row.id);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update status");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, load]);

  const downloadTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const csv = await getMedicineImportTemplate(auth.accessToken, auth.tenantId);
      downloadCsv("medicine-import-template.csv", csv);
    } catch {
      downloadCsv("medicine-import-template.csv", buildMedicineTemplateCsv());
    }
  };

  const openImportFilePicker = () => fileInputRef.current?.click();

  const handleImportFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const parsed = fileUploadSchema({
        required: true,
        allowedMimeTypes: ["text/csv", "application/csv", "application/vnd.ms-excel"],
        allowedExtensions: ["csv"],
        maxBytes: 5 * 1024 * 1024,
      }).safeParse(file);
      if (!parsed.success) {
        setPreviewFile(null);
        setPreview(null);
        setError(firstZodError(parsed.error));
        return;
      }
      const text = await file.text();
      setPreview(parseMedicineImportPreview(text));
      setPreviewFile(file);
      setImportPreviewOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to read CSV file");
    } finally {
      event.target.value = "";
    }
  };

  const confirmImport = async () => {
    if (!auth.accessToken || !auth.tenantId || !previewFile) return;
    setSaving(true);
    setError(null);
    try {
      setImportResult(await importMedicinesCsv(auth.accessToken, auth.tenantId, previewFile));
      setImportPreviewOpen(false);
      setImportResultOpen(true);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to import medicine CSV");
    } finally {
      setSaving(false);
    }
  };

  const openRowActions = React.useCallback((event: React.MouseEvent<HTMLElement>, row: Medicine) => {
    setActionAnchor(event.currentTarget);
    setActionRow(row);
  }, []);

  const closeRowActions = React.useCallback(() => {
    setActionAnchor(null);
    setActionRow(null);
  }, []);

  const medicineColumns = React.useMemo<ConfigurableGridColumn<Medicine>[]>(() => [
    {
      id: "name",
      label: "Name",
      mandatory: true,
      defaultVisible: true,
      minWidth: 180,
      render: (row) => (
        <Stack spacing={0.2}>
          <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.medicineName}</Typography>
          <Typography variant="caption" color="text.secondary">{row.strength || row.unit || row.manufacturer || "-"}</Typography>
        </Stack>
      ),
    },
    {
      id: "code",
      label: "Code",
      defaultVisible: false,
      minWidth: 150,
      render: (row) => (
        <Stack spacing={0.2}>
          <Typography variant="body2">{row.barcode || row.externalCode || "-"}</Typography>
          <Typography variant="caption" color="text.secondary">{row.qrCode || "-"}</Typography>
        </Stack>
      ),
    },
    {
      id: "genericBrand",
      label: "Generic / Brand",
      defaultVisible: false,
      minWidth: 180,
      render: (row) => (
        <Stack spacing={0.2}>
          <Typography variant="body2">{row.genericName || "-"}</Typography>
          <Typography variant="caption" color="text.secondary">{row.brandName || "-"}</Typography>
        </Stack>
      ),
    },
    {
      id: "category",
      label: "Category",
      defaultVisible: false,
      minWidth: 120,
      render: (row) => row.category || "-",
    },
    {
      id: "type",
      label: "Type",
      defaultVisible: true,
      minWidth: 100,
      render: (row) => row.dosageForm || row.medicineType,
    },
    {
      id: "dosageFrequency",
      label: "Dosage / Frequency",
      defaultVisible: false,
      minWidth: 160,
      render: (row) => (
        <Stack spacing={0.2}>
          <Typography variant="body2">{row.defaultDosage || "-"}</Typography>
          <Typography variant="caption" color="text.secondary">{row.defaultFrequency || "-"}</Typography>
        </Stack>
      ),
    },
    {
      id: "durationTiming",
      label: "Duration / Timing",
      defaultVisible: false,
      minWidth: 160,
      render: (row) => (
        <Stack spacing={0.2}>
          <Typography variant="body2">{row.defaultDurationDays != null ? `${row.defaultDurationDays} days` : "-"}</Typography>
          <Typography variant="caption" color="text.secondary">{row.defaultTiming || "-"}</Typography>
        </Stack>
      ),
    },
    {
      id: "instructions",
      label: "Instructions",
      defaultVisible: false,
      minWidth: 220,
      render: (row) => <Typography variant="caption" color="text.secondary">{row.defaultInstructions || "-"}</Typography>,
    },
    {
      id: "price",
      label: "Price",
      defaultVisible: true,
      align: "right",
      minWidth: 90,
      render: (row) => formatPrice(row.defaultPrice),
    },
    {
      id: "taxRate",
      label: "Tax %",
      defaultVisible: true,
      align: "right",
      minWidth: 70,
      render: (row) => (row.taxRate != null ? row.taxRate.toFixed(2) : "-"),
    },
    {
      id: "status",
      label: "Status",
      defaultVisible: true,
      minWidth: 90,
      render: (row) => <Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} />,
    },
    {
      id: "actions",
      label: "Actions",
      mandatory: true,
      defaultVisible: true,
      align: "right",
      minWidth: 80,
      render: (row) => (canManage ? (
        <IconButton size="small" onClick={(event) => openRowActions(event, row)}>
          <MoreHorizRounded fontSize="small" />
        </IconButton>
      ) : null),
    },
  ], [canManage, openRowActions]);

  const {
    visibleColumnIds,
    toggleVisibleColumnId,
    resetVisibleColumnIds,
  } = useColumnVisibility(medicineColumns, MEDICINE_MASTER_COLUMNS_STORAGE_KEY);

  const registerFieldRef = React.useCallback(
    (field: string) => (element: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null) => {
      fieldRefs.current[field] = element;
    },
    [],
  );

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to access Medicine Master.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1.5, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Medicine Master</Typography>
          <Typography variant="body2" color="text.secondary">
            Compact operational medicine catalog with barcode, price, and stock metadata for pharmacy and clinic admin workflows.
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: "wrap" }}>
            <Chip size="small" label={`${rows.length} total`} />
            <Chip size="small" color="success" variant="outlined" label={`${activeCount} active`} />
            <Chip size="small" variant="outlined" label={`${inactiveCount} inactive`} />
            <Chip size="small" color={lowStockCount ? "error" : "default"} variant="outlined" label={`${lowStockCount} low stock`} />
            <Chip size="small" color={expiringSoonCount ? "warning" : "default"} variant="outlined" label={`${expiringSoonCount} expiring soon`} />
            <Chip size="small" color={missingBarcodeCount ? "warning" : "default"} variant="outlined" label={`${missingBarcodeCount} missing barcode`} />
            {canManage ? <Chip size="small" color="primary" variant="outlined" label="Manage enabled" /> : <Chip size="small" label="Read only" variant="outlined" />}
          </Stack>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" justifyContent="flex-end">
          <Button variant="outlined" size="small" onClick={() => void load()}>Refresh</Button>
          {canManage ? <Button variant="outlined" size="small" onClick={() => void downloadTemplate()}>Download CSV Template</Button> : null}
          {canManage ? <Button variant="outlined" size="small" onClick={openImportFilePicker}>Upload CSV</Button> : null}
          {canManage ? <Button variant="contained" size="small" onClick={openCreate}>Add Medicine</Button> : null}
        </Stack>
      </Box>
      <input ref={fileInputRef} type="file" accept=".csv,text/csv" hidden onChange={handleImportFile} />

      {error ? <Alert severity="error">{error}</Alert> : null}
      {!canManage ? (
        <Alert severity="info">
          Read-only access is available for auditors and other limited roles. Add Medicine and CSV upload are restricted to Clinic Admin and Pharmacy roles with inventory manage permission.
        </Alert>
      ) : null}

      <WorkflowGuide
        title="Medicine Workflow"
        subtitle="Maintain the catalogue first, then receive stock and dispense against live batches."
        steps={[
          { label: "Search / Filter" },
          { label: "Add Medicine", tone: "primary" },
          { label: "Advanced Details" },
          { label: "Stock Entry" },
          { label: "Dispense / POS" },
        ]}
      />

      <Card>
        <CardContent sx={{ py: 1 }}>
          <Stack spacing={1}>
            <Grid container spacing={1} alignItems="center">
              <Grid size={{ xs: 12, md: 5 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Search medicine catalog"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Name, barcode, brand, form, strength, manufacturer"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 7 }}>
                <Stack direction="row" spacing={0.75} flexWrap="wrap" justifyContent={{ xs: "flex-start", md: "flex-end" }} useFlexGap>
                  <Chip size="small" label="Active" color={quickFilter === "ACTIVE" ? "success" : "default"} variant={quickFilter === "ACTIVE" ? "filled" : "outlined"} onClick={() => setQuickFilter((current) => current === "ACTIVE" ? "NONE" : "ACTIVE")} />
                  <Chip size="small" label="Low stock" color={quickFilter === "LOW_STOCK" ? "error" : "default"} variant={quickFilter === "LOW_STOCK" ? "filled" : "outlined"} onClick={() => setQuickFilter((current) => current === "LOW_STOCK" ? "NONE" : "LOW_STOCK")} />
                  <Chip size="small" label="Expiring soon" color={quickFilter === "EXPIRING_SOON" ? "warning" : "default"} variant={quickFilter === "EXPIRING_SOON" ? "filled" : "outlined"} onClick={() => setQuickFilter((current) => current === "EXPIRING_SOON" ? "NONE" : "EXPIRING_SOON")} />
                  <Chip size="small" label="Missing barcode" color={quickFilter === "MISSING_BARCODE" ? "warning" : "default"} variant={quickFilter === "MISSING_BARCODE" ? "filled" : "outlined"} onClick={() => setQuickFilter((current) => current === "MISSING_BARCODE" ? "NONE" : "MISSING_BARCODE")} />
                  <ManageColumnsPopover columns={medicineColumns} visibleColumnIds={visibleColumnIds} onToggleColumn={toggleVisibleColumnId} onReset={resetVisibleColumnIds} />
                </Stack>
              </Grid>
            </Grid>
            <Accordion expanded={advancedFiltersOpen} onChange={(_, expanded) => setAdvancedFiltersOpen(expanded)} disableGutters sx={compactAccordionSx}>
              <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 1.5, py: 0.25, minHeight: 40 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Advanced filters</Typography>
              </AccordionSummary>
              <AccordionDetails sx={{ px: 1.5, pb: 1.25, pt: 0 }}>
                <Box sx={compactFormSx}>
                  <Grid container spacing={1}>
                    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ pt: 0.25 }}>
                        <Chip size="small" label="Active" color={statusFilter === "ACTIVE" ? "success" : "default"} variant={statusFilter === "ACTIVE" ? "filled" : "outlined"} onClick={() => setStatusFilter((current) => current === "ACTIVE" ? "ALL" : "ACTIVE")} />
                        <Chip size="small" label="Inactive" color={statusFilter === "INACTIVE" ? "warning" : "default"} variant={statusFilter === "INACTIVE" ? "filled" : "outlined"} onClick={() => setStatusFilter((current) => current === "INACTIVE" ? "ALL" : "INACTIVE")} />
                      </Stack>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                      <TextField select size="small" fullWidth label="Type" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as "ALL" | MedicineType)}>
                        <MenuItem value="ALL">All types</MenuItem>
                        {medicineTypeOptions.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                      </TextField>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                      <TextField select size="small" fullWidth label="Category" value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)}>
                        <MenuItem value="ALL">All categories</MenuItem>
                        {categoryOptions.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                      </TextField>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                      <TextField select size="small" fullWidth label="Stock" value={inventoryFilter} onChange={(e) => setInventoryFilter(e.target.value as "ALL" | "LOW_STOCK" | "EXPIRING_SOON" | "OUT_OF_STOCK")}>
                        <MenuItem value="ALL">All stock states</MenuItem>
                        <MenuItem value="LOW_STOCK">Low stock</MenuItem>
                        <MenuItem value="EXPIRING_SOON">Expiring soon</MenuItem>
                        <MenuItem value="OUT_OF_STOCK">Out of stock</MenuItem>
                      </TextField>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                      <TextField select size="small" fullWidth label="Barcode" value={barcodeFilter} onChange={(e) => setBarcodeFilter(e.target.value as "ALL" | "HAS_BARCODE" | "MISSING_BARCODE")}>
                        <MenuItem value="ALL">Any barcode</MenuItem>
                        <MenuItem value="HAS_BARCODE">Has barcode</MenuItem>
                        <MenuItem value="MISSING_BARCODE">Missing barcode</MenuItem>
                      </TextField>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 3, md: 2 }}>
                      <TextField size="small" fullWidth label="Min price" value={priceMin} onChange={(e) => setPriceMin(e.target.value)} inputProps={{ inputMode: "decimal" }} />
                    </Grid>
                    <Grid size={{ xs: 6, sm: 3, md: 2 }}>
                      <TextField size="small" fullWidth label="Max price" value={priceMax} onChange={(e) => setPriceMax(e.target.value)} inputProps={{ inputMode: "decimal" }} />
                    </Grid>
                  </Grid>
                </Box>
              </AccordionDetails>
            </Accordion>
          </Stack>
        </CardContent>
      </Card>

      {canManage ? (
        <Accordion expanded={editorOpen} onChange={(_, expanded) => setEditorOpen(expanded)} disableGutters sx={compactAccordionSx}>
          <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 1.5, py: 0.25, minHeight: 40 }}>
            <Stack spacing={0.35}>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                {editing ? `Editing: ${editing.medicineName}` : "Add Medicine"}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Maintain medicine identity, scan codes, dosage defaults, and price metadata without leaving the catalogue workspace.
              </Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails sx={{ px: 1.5, pb: 1.5, pt: 0 }}>
            <Grid container spacing={1} sx={[compactFormGridSx, compactFormSx]}>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label={<RequiredLabel text="Medicine name" required />}
                  value={form.medicineName}
                  error={Boolean(fieldErrors.medicineName)}
                  helperText={fieldErrors.medicineName}
                  inputRef={registerFieldRef("medicineName")}
                  inputProps={{ required: true, "aria-required": true }}
                  onChange={(e) => setForm((v) => ({ ...v, medicineName: e.target.value }))}
                />
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  select
                  size="small"
                  label={<RequiredLabel text="Type" required />}
                  value={form.medicineType}
                  error={Boolean(fieldErrors.medicineType)}
                  helperText={fieldErrors.medicineType}
                  inputRef={registerFieldRef("medicineType")}
                  inputProps={{ required: true, "aria-required": true }}
                  onChange={(e) => setForm((v) => ({ ...v, medicineType: e.target.value as MedicineType }))}
                >
                  {medicineTypeOptions.map((option) => (
                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  select
                  size="small"
                  label={<RequiredLabel text="Status" required />}
                  value={form.active ? "true" : "false"}
                  error={Boolean(fieldErrors.active)}
                  helperText={fieldErrors.active}
                  inputRef={registerFieldRef("active")}
                  inputProps={{ required: true, "aria-required": true }}
                  onChange={(e) => setForm((v) => ({ ...v, active: e.target.value === "true" }))}
                >
                  <MenuItem value="true">Active</MenuItem>
                  <MenuItem value="false">Inactive</MenuItem>
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label={<RequiredLabel text="Strength" required />}
                  value={form.strength || ""}
                  error={Boolean(fieldErrors.strength)}
                  helperText={fieldErrors.strength}
                  inputRef={registerFieldRef("strength")}
                  inputProps={{ required: true, "aria-required": true }}
                  onChange={(e) => setForm((v) => ({ ...v, strength: e.target.value || null }))}
                />
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Unit"
                  value={form.unit || ""}
                  error={Boolean(fieldErrors.unit)}
                  helperText={fieldErrors.unit}
                  inputRef={registerFieldRef("unit")}
                  onChange={(e) => setForm((v) => ({ ...v, unit: e.target.value || null }))}
                />
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Generic name"
                  value={form.genericName || ""}
                  error={Boolean(fieldErrors.genericName)}
                  helperText={fieldErrors.genericName}
                  inputRef={registerFieldRef("genericName")}
                  onChange={(e) => setForm((v) => ({ ...v, genericName: e.target.value || null }))}
                />
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Brand name"
                  value={form.brandName || ""}
                  error={Boolean(fieldErrors.brandName)}
                  helperText={fieldErrors.brandName}
                  inputRef={registerFieldRef("brandName")}
                  onChange={(e) => setForm((v) => ({ ...v, brandName: e.target.value || null }))}
                />
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Category"
                  value={form.category || ""}
                  error={Boolean(fieldErrors.category)}
                  helperText={fieldErrors.category}
                  inputRef={registerFieldRef("category")}
                  onChange={(e) => setForm((v) => ({ ...v, category: e.target.value || null }))}
                />
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Form"
                  value={form.dosageForm || ""}
                  error={Boolean(fieldErrors.dosageForm)}
                  helperText={fieldErrors.dosageForm}
                  inputRef={registerFieldRef("dosageForm")}
                  onChange={(e) => setForm((v) => ({ ...v, dosageForm: e.target.value || null }))}
                />
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  type="number"
                  label="Default price"
                  value={form.defaultPrice ?? ""}
                  error={Boolean(fieldErrors.defaultPrice)}
                  helperText={fieldErrors.defaultPrice}
                  inputRef={registerFieldRef("defaultPrice")}
                  inputProps={{ min: 0, max: 999999, step: 0.01 }}
                  onChange={(e) => setForm((v) => ({ ...v, defaultPrice: e.target.value ? Number(e.target.value) : null }))}
                />
              </Grid>
              <Grid size={{ xs: 12, lg: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  type="number"
                  label="Tax %"
                  value={form.taxRate ?? ""}
                  error={Boolean(fieldErrors.taxRate)}
                  helperText={fieldErrors.taxRate}
                  inputRef={registerFieldRef("taxRate")}
                  inputProps={{ min: 0, max: 100, step: 0.01 }}
                  onChange={(e) => setForm((v) => ({ ...v, taxRate: e.target.value ? Number(e.target.value) : null }))}
                />
              </Grid>
              <Grid size={12}>
                <Accordion expanded={advancedOpen} onChange={(_, expanded) => setAdvancedOpen(expanded)} disableGutters sx={compactAccordionSx}>
                  <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 1.5, py: 0.25, minHeight: 40 }}>
                    <Stack spacing={0.2}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Advanced details</Typography>
                      <Typography variant="caption" color="text.secondary">Manufacturer, dosage defaults, scan codes, and extended instructions.</Typography>
                    </Stack>
                  </AccordionSummary>
                  <AccordionDetails sx={{ px: 1.5, pb: 1.25, pt: 0 }}>
                    <Grid container spacing={1}>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <CodeScannerField
                          size="small"
                          label="Barcode"
                          value={form.barcode || ""}
                          error={Boolean(fieldErrors.barcode)}
                          helperText={fieldErrors.barcode}
                          inputRef={registerFieldRef("barcode")}
                          onChange={(next) => setForm((v) => ({ ...v, barcode: next || null }))}
                          placeholder="Scan or enter barcode"
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <CodeScannerField
                          size="small"
                          label="QR code"
                          value={form.qrCode || ""}
                          error={Boolean(fieldErrors.qrCode)}
                          helperText={fieldErrors.qrCode}
                          inputRef={registerFieldRef("qrCode")}
                          onChange={(next) => setForm((v) => ({ ...v, qrCode: next || null }))}
                          placeholder="Scan or enter QR code"
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <CodeScannerField
                          size="small"
                          label="External code"
                          value={form.externalCode || ""}
                          error={Boolean(fieldErrors.externalCode)}
                          helperText={fieldErrors.externalCode}
                          inputRef={registerFieldRef("externalCode")}
                          onChange={(next) => setForm((v) => ({ ...v, externalCode: next || null }))}
                          placeholder="Scan or enter code"
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          fullWidth
                          size="small"
                          label="Manufacturer"
                          value={form.manufacturer || ""}
                          error={Boolean(fieldErrors.manufacturer)}
                          helperText={fieldErrors.manufacturer}
                          inputRef={registerFieldRef("manufacturer")}
                          onChange={(e) => setForm((v) => ({ ...v, manufacturer: e.target.value || null }))}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          fullWidth
                          size="small"
                          label="Default dosage"
                          value={form.defaultDosage || ""}
                          error={Boolean(fieldErrors.defaultDosage)}
                          helperText={fieldErrors.defaultDosage}
                          inputRef={registerFieldRef("defaultDosage")}
                          onChange={(e) => setForm((v) => ({ ...v, defaultDosage: e.target.value || null }))}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField
                          fullWidth
                          size="small"
                          label="Default frequency"
                          value={form.defaultFrequency || ""}
                          error={Boolean(fieldErrors.defaultFrequency)}
                          helperText={fieldErrors.defaultFrequency}
                          inputRef={registerFieldRef("defaultFrequency")}
                          onChange={(e) => setForm((v) => ({ ...v, defaultFrequency: e.target.value || null }))}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 3 }}>
                        <TextField
                          fullWidth
                          size="small"
                          type="number"
                          label="Default duration (days)"
                          value={form.defaultDurationDays ?? ""}
                          error={Boolean(fieldErrors.defaultDurationDays)}
                          helperText={fieldErrors.defaultDurationDays}
                          inputRef={registerFieldRef("defaultDurationDays")}
                          inputProps={{ min: 1, max: 365, step: 1 }}
                          onChange={(e) => setForm((v) => ({ ...v, defaultDurationDays: e.target.value ? Number(e.target.value) : null }))}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 3 }}>
                        <TextField
                          fullWidth
                          size="small"
                          label="Default timing"
                          value={form.defaultTiming || ""}
                          error={Boolean(fieldErrors.defaultTiming)}
                          helperText={fieldErrors.defaultTiming}
                          inputRef={registerFieldRef("defaultTiming")}
                          onChange={(e) => setForm((v) => ({ ...v, defaultTiming: (e.target.value || null) as MedicineInput["defaultTiming"] }))}
                          placeholder="BEFORE_FOOD / AFTER_FOOD / WITH_FOOD / ANYTIME"
                        />
                      </Grid>
                      <Grid size={12}>
                        <TextField
                          fullWidth
                          size="small"
                          multiline
                          minRows={2}
                          label="Default instructions"
                          value={form.defaultInstructions || ""}
                          error={Boolean(fieldErrors.defaultInstructions)}
                          helperText={fieldErrors.defaultInstructions}
                          inputRef={registerFieldRef("defaultInstructions")}
                          onChange={(e) => setForm((v) => ({ ...v, defaultInstructions: e.target.value || null }))}
                        />
                      </Grid>
                    </Grid>
                  </AccordionDetails>
                </Accordion>
              </Grid>
              <Grid size={12}>
                <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                  <Button disabled={saving} onClick={() => void save()}>{saving ? "Saving..." : editing ? "Update Medicine" : "Save Medicine"}</Button>
                  <Button variant="text" onClick={closeEditor}>Cancel</Button>
                  <Button variant="text" onClick={resetEditor}>Reset</Button>
                </Box>
              </Grid>
            </Grid>
          </AccordionDetails>
        </Accordion>
      ) : null}

      {loading ? (
        <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}>
          <CircularProgress />
        </Box>
      ) : null}

      {!loading ? (
        <Card>
          <CardContent sx={compactCardContentSx}>
            <ConfigurableGrid
              title="Medicine catalogue"
              subtitle="Configure which catalogue columns you want to keep visible for faster day-to-day use."
              columns={medicineColumns}
              visibleColumnIds={visibleColumnIds}
              rows={filtered}
              getRowKey={(row) => row.id}
              toolbar={<Chip size="small" variant="outlined" label={`${filtered.length} visible medicines`} />}
              emptyState={(
                <CompactEmptyState
                  title={rows.length === 0 ? "Add your first medicine to start building the catalogue." : "No medicines match the current filters."}
                  subtitle={rows.length === 0
                    ? "Create the first medicine manually or upload a CSV to seed the catalogue in bulk."
                    : "Clear or adjust the current search and filter combination to show matching medicine records."}
                  action={canManage ? (
                    <Stack direction="row" spacing={1}>
                      <Button size="small" onClick={openCreate}>Add Medicine</Button>
                      <Button size="small" variant="outlined" onClick={openImportFilePicker}>Upload CSV</Button>
                    </Stack>
                  ) : undefined}
                />
              )}
              rowSx={{ "& td": { py: 0.8, verticalAlign: "top" } }}
            />
          </CardContent>
        </Card>
      ) : null}

      <Menu anchorEl={actionAnchor} open={Boolean(actionAnchor && actionRow)} onClose={closeRowActions}>
        <MenuItem
          onClick={() => {
            if (actionRow) {
              openEdit(actionRow);
            }
            closeRowActions();
          }}
        >
          Edit
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (actionRow) {
              void toggleActive(actionRow);
            }
            closeRowActions();
          }}
        >
          {actionRow?.active ? "Deactivate" : "Activate"}
        </MenuItem>
      </Menu>

      <Dialog open={importPreviewOpen} onClose={() => setImportPreviewOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>Preview CSV import</DialogTitle>
        <DialogContent>
          {preview ? (
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip size="small" label={`${preview.summary.totalRows} data rows`} />
                <Chip size="small" color="success" variant="outlined" label={`${preview.summary.validRows} valid`} />
                <Chip size="small" color={preview.summary.invalidRows > 0 ? "warning" : "default"} variant="outlined" label={`${preview.summary.invalidRows} with issues`} />
              </Stack>
              {preview.headerWarnings.length > 0 ? (
                <Alert severity="warning">
                  Missing expected headers: {preview.headerWarnings.join(", ")}.
                </Alert>
              ) : null}
              <Alert severity="info">
                Partial import is supported. Rows with validation errors will be reported after upload.
              </Alert>
              <Typography variant="body2" color="text.secondary">
                Template columns: {MEDICINE_IMPORT_COLUMNS.join(", ")}
              </Typography>
              <TableContainer sx={{ maxHeight: 420 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ width: 72 }}>Row</TableCell>
                      <TableCell>Medicine</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell>Strength</TableCell>
                      <TableCell align="right">Price</TableCell>
                      <TableCell align="right">Tax %</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Errors</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {preview.rows.map((row) => (
                      <TableRow key={row.rowNumber}>
                        <TableCell>{row.rowNumber}</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>{row.medicineName || "-"}</TableCell>
                        <TableCell>{row.type || "-"}</TableCell>
                        <TableCell>{row.strength || "-"}</TableCell>
                        <TableCell align="right">{row.defaultPrice || "-"}</TableCell>
                        <TableCell align="right">{row.taxPercent || "-"}</TableCell>
                        <TableCell>
                          <Chip size="small" label={row.errors.length === 0 ? "Ready" : "Needs attention"} color={row.errors.length === 0 ? "success" : "warning"} />
                        </TableCell>
                        <TableCell sx={{ maxWidth: 260 }}>
                          {row.errors.length > 0 ? row.errors.join("; ") : "-"}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setImportPreviewOpen(false)}>Cancel</Button>
          <Button variant="outlined" onClick={() => void downloadTemplate()}>Download CSV Template</Button>
          <Button variant="contained" disabled={saving || !previewFile} onClick={() => void confirmImport()}>
            {saving ? "Importing..." : "Import file"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={importResultOpen} onClose={() => setImportResultOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Medicine CSV Import Result</DialogTitle>
        <DialogContent>
          {importResult ? (
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Alert severity="success">
                Rows: {importResult.totalRows}. Created {importResult.created}. Updated {importResult.updated}. Skipped {importResult.skipped}. Failed {importResult.failed}.
              </Alert>
              <Typography variant="body2" color="text.secondary">
                The backend validates rows tenant-safely and returns row-level success or failure details after import.
              </Typography>
              {importResult.failedRowsCsv ? (
                <Button variant="outlined" onClick={() => downloadCsv("medicine-import-failed-rows.csv", importResult.failedRowsCsv)}>
                  Download failed rows CSV
                </Button>
              ) : null}
              <TableContainer sx={{ maxHeight: 420 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Row</TableCell>
                      <TableCell>Medicine</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Message</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {importResult.rows.map((row) => (
                      <TableRow key={`${row.rowNumber}-${row.medicineName}`}>
                        <TableCell>{row.rowNumber}</TableCell>
                        <TableCell>{row.medicineName}</TableCell>
                        <TableCell>
                          <Chip size="small" label={row.status} color={row.status === "FAILED" ? "error" : row.status === "SKIPPED" ? "default" : "success"} />
                        </TableCell>
                        <TableCell>{row.message}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setImportResultOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
