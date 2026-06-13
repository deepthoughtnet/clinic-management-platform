import * as React from "react";
import {
  Autocomplete,
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
  FormControl,
  Grid,
  IconButton,
  InputAdornment,
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
  TableContainer,
  TextField,
  Typography,
} from "@mui/material";
import ExpandMoreRounded from "@mui/icons-material/ExpandMoreRounded";
import CameraAltRoundedIcon from "@mui/icons-material/CameraAltRounded";
import { useNavigate } from "react-router-dom";

import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, CompactStatCard, compactCardContentSx } from "../../components/compact/CompactUi";
import CodeScannerField from "../../components/pharmacy/CodeScannerField";
import CodeScannerDialog from "../../components/pharmacy/CodeScannerDialog";
import {
  createInventoryTransaction,
  createMedicine,
  createStock,
  getInventoryLocations,
  getInventoryTransactions,
  getLowStock,
  getMedicines,
  listPharmacyPosSales,
  getStocks,
  returnPharmacyPosSale,
  transferInventoryStock,
  updateStock,
  type InventoryLocation,
  type InventoryTransaction,
  type InventoryTransactionInput,
  type InventoryTransactionType,
  type LowStockItem,
  type Medicine,
  type MedicineInput,
  type MedicineType,
  type PharmacyPosSale,
  type Stock,
  type StockInput,
  type PaymentMode,
} from "../../api/clinicApi";

type StockFormState = {
  medicineId: string;
  locationId: string;
  batchNumber: string;
  purchaseReferenceNumber: string;
  barcode: string;
  qrCode: string;
  externalCode: string;
  expiryDate: string;
  quantityOnHand: string;
  lowStockThreshold: string;
  unitCost: string;
  sellingPrice: string;
  active: boolean;
};

type TransactionFormState = {
  medicineId: string;
  stockBatchId: string;
  transactionType: InventoryTransactionType;
  quantity: string;
  referenceType: string;
  referenceId: string;
  notes: string;
};

type StockCountFormState = {
  medicineId: string;
  locationId: string;
  stockBatchId: string;
  countedQuantity: string;
  reason: string;
};

const TABS = [
  { value: "stocks", label: "Stock" },
  { value: "count", label: "Physical count" },
  { value: "expiry-report", label: "Expiry report" },
  { value: "low-stock", label: "Low stock" },
  { value: "returns", label: "Returns & write-offs" },
] as const;

const TRANSACTION_TYPES: InventoryTransactionType[] = [
  "OPENING",
  "PURCHASE",
  "SALE",
  "ADJUSTMENT",
  "RETURN",
  "CUSTOMER_RETURN_IN",
  "CUSTOMER_RETURN_NON_SELLABLE",
  "VENDOR_RETURN_OUT",
  "WRITE_OFF",
  "EXPIRED",
  "CANCELLED_DISPENSE",
  "STOCK_IN",
  "ADJUSTMENT_IN",
  "ADJUSTMENT_OUT",
];
const MEDICINE_TYPES: MedicineType[] = ["TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROP", "OINTMENT", "OTHER"];

type MedicineAutocompleteOption =
  | { kind: "existing"; medicine: Medicine }
  | { kind: "create"; inputValue: string };

function emptyStockForm(): StockFormState {
  return {
    medicineId: "",
    locationId: "",
    batchNumber: "",
    purchaseReferenceNumber: "",
    barcode: "",
    qrCode: "",
    externalCode: "",
    expiryDate: "",
    quantityOnHand: "",
    lowStockThreshold: "",
    unitCost: "",
    sellingPrice: "",
    active: true,
  };
}

function emptyTransactionForm(): TransactionFormState {
  return {
    medicineId: "",
    stockBatchId: "",
    transactionType: "OPENING",
    quantity: "",
    referenceType: "",
    referenceId: "",
    notes: "",
  };
}

function emptyStockCountForm(): StockCountFormState {
  return {
    medicineId: "",
    locationId: "",
    stockBatchId: "",
    countedQuantity: "",
    reason: "",
  };
}

function emptyQuickMedicineForm(): MedicineInput {
  return {
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
}

function stockInput(form: StockFormState): StockInput {
  return {
    medicineId: form.medicineId,
    locationId: form.locationId || null,
    barcode: form.barcode.trim() || null,
    qrCode: form.qrCode.trim() || null,
    externalCode: form.externalCode.trim() || null,
    batchNumber: form.batchNumber.trim() || null,
    purchaseReferenceNumber: form.purchaseReferenceNumber.trim() || null,
    expiryDate: form.expiryDate || null,
    purchaseDate: null,
    supplierName: null,
    quantityReceived: null,
    quantityOnHand: Number(form.quantityOnHand || "0"),
    lowStockThreshold: form.lowStockThreshold.trim() ? Number(form.lowStockThreshold) : null,
    unitCost: form.unitCost.trim() ? Number(form.unitCost) : null,
    purchasePrice: null,
    sellingPrice: form.sellingPrice.trim() ? Number(form.sellingPrice) : null,
    active: form.active,
  };
}

function transactionInput(form: TransactionFormState): InventoryTransactionInput {
  return {
    medicineId: form.medicineId,
    stockBatchId: form.stockBatchId.trim() || null,
    transactionType: form.transactionType,
    quantity: Number(form.quantity || "0"),
    reason: form.notes.trim() || null,
    referenceType: form.referenceType.trim() || null,
    referenceId: form.referenceId.trim() || null,
    notes: form.notes.trim() || null,
  };
}

function statusColor(quantity: number, threshold: number | null) {
  if (threshold === null) return "default";
  return quantity <= threshold ? "error" : "success";
}

function transactionLabel(type: InventoryTransactionType) {
  const labels: Record<InventoryTransactionType, string> = {
    OPENING: "Opening",
    PURCHASE: "Stock In",
    SALE: "Sale",
    ADJUSTMENT: "Adjustment",
    RETURN: "Patient Return",
    CUSTOMER_RETURN_IN: "Customer Return In",
    CUSTOMER_RETURN_NON_SELLABLE: "Customer Return Non-sellable",
    VENDOR_RETURN_OUT: "Vendor Return Out",
    WRITE_OFF: "Write-off",
    DISPENSED: "Dispensed",
    EXPIRED: "Expired",
    CANCELLED_DISPENSE: "Cancelled Dispense",
    STOCK_IN: "Stock In",
    ADJUSTMENT_IN: "Adjustment In",
    ADJUSTMENT_OUT: "Adjustment Out",
    TRANSFER_IN: "Transfer In",
    TRANSFER_OUT: "Transfer Out",
  };
  return labels[type] || type;
}

function utcDayNumber(dateValue: string) {
  const [year, month, day] = dateValue.split("-").map((value) => Number(value));
  return Date.UTC(year, month - 1, day);
}

function todayUtcDayNumber() {
  const now = new Date();
  return Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
}

function daysUntil(dateValue: string | null) {
  if (!dateValue) return Number.POSITIVE_INFINITY;
  return Math.floor((utcDayNumber(dateValue) - todayUtcDayNumber()) / (1000 * 60 * 60 * 24));
}

function expiryState(expiryDate: string | null) {
  if (!expiryDate) {
    return { label: "No expiry", color: "default" as const, bucket: "No expiry" as const };
  }
  const diffDays = daysUntil(expiryDate);
  if (diffDays < 0) return { label: "EXPIRED", color: "error" as const, bucket: "Expired" as const };
  if (diffDays <= 30) return { label: "0-30 days", color: "warning" as const, bucket: "0-30" as const };
  if (diffDays <= 60) return { label: "31-60 days", color: "info" as const, bucket: "31-60" as const };
  if (diffDays <= 90) return { label: "61-90 days", color: "secondary" as const, bucket: "61-90" as const };
  return { label: "91+ days", color: "success" as const, bucket: "91+" as const };
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(value);
}

export default function InventoryPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [tab, setTab] = React.useState<(typeof TABS)[number]["value"]>("stocks");
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [stocks, setStocks] = React.useState<Stock[]>([]);
  const [transactions, setTransactions] = React.useState<InventoryTransaction[]>([]);
  const [lowStock, setLowStock] = React.useState<LowStockItem[]>([]);
  const [locations, setLocations] = React.useState<InventoryLocation[]>([]);
  const [stockForm, setStockForm] = React.useState<StockFormState>(emptyStockForm());
  const [transactionForm, setTransactionForm] = React.useState<TransactionFormState>(emptyTransactionForm());
  const [selectedStockId, setSelectedStockId] = React.useState<string | null>(null);
  const [selectedLocationId, setSelectedLocationId] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [stockSearch, setStockSearch] = React.useState("");
  const [stockSearchScannerOpen, setStockSearchScannerOpen] = React.useState(false);
  const [transferForm, setTransferForm] = React.useState({ medicineId: "", stockBatchId: "", fromLocationId: "", toLocationId: "", quantity: "", reason: "" });
  const [stockCountForm, setStockCountForm] = React.useState<StockCountFormState>(emptyStockCountForm());
  const [expiryReportMedicineId, setExpiryReportMedicineId] = React.useState("");
  const [stockActionPanel, setStockActionPanel] = React.useState<"add" | "transaction" | "transfer" | "count">("add");
  const [medicineSearchInput, setMedicineSearchInput] = React.useState("");
  const [quickMedicineOpen, setQuickMedicineOpen] = React.useState(false);
  const [quickMedicineForm, setQuickMedicineForm] = React.useState<MedicineInput>(emptyQuickMedicineForm());
  const [sales, setSales] = React.useState<PharmacyPosSale[]>([]);
  const [saleSearch, setSaleSearch] = React.useState("");
  const [customerReturnSaleId, setCustomerReturnSaleId] = React.useState("");
  const [customerReturnLineId, setCustomerReturnLineId] = React.useState("");
  const [customerReturnQuantity, setCustomerReturnQuantity] = React.useState("1");
  const [customerReturnReusable, setCustomerReturnReusable] = React.useState(true);
  const [customerReturnReason, setCustomerReturnReason] = React.useState("");
  const [customerReturnMode, setCustomerReturnMode] = React.useState<PaymentMode>("CASH");
  const [customerReturnReference, setCustomerReturnReference] = React.useState("");
  const [vendorReturnForm, setVendorReturnForm] = React.useState({ medicineId: "", stockBatchId: "", quantity: "", supplierReference: "", reason: "", notes: "" });
  const [writeOffForm, setWriteOffForm] = React.useState({ medicineId: "", stockBatchId: "", quantity: "", reason: "", notes: "" });

  const medicineById = React.useMemo(() => new Map(medicines.map((medicine) => [medicine.id, medicine])), [medicines]);
  const medicineAutocompleteOptions = React.useMemo<MedicineAutocompleteOption[]>(
    () => medicines.map((medicine) => ({ kind: "existing", medicine })),
    [medicines],
  );
  const selectedMedicineOption = React.useMemo<MedicineAutocompleteOption | null>(
    () => medicineAutocompleteOptions.find((option) => option.kind === "existing" && option.medicine.id === stockForm.medicineId) ?? null,
    [medicineAutocompleteOptions, stockForm.medicineId],
  );
  const visibleStocks = React.useMemo(() => {
    const term = stockSearch.trim().toLowerCase();
    return stocks.filter((stock) => {
      const matchesLocation = !selectedLocationId || stock.locationId === selectedLocationId;
      const matchesTerm = !term || [stock.medicineName, stock.batchNumber, stock.purchaseReferenceNumber, stock.barcode, stock.qrCode, stock.externalCode, stock.supplierName]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
      return matchesLocation && matchesTerm;
    });
  }, [stocks, stockSearch, selectedLocationId]);
  const expiringSoonCount = React.useMemo(() => stocks.filter((stock) => {
    const diff = daysUntil(stock.expiryDate);
    return diff >= 0 && diff <= 30;
  }).length, [stocks]);
  const countableStocks = React.useMemo(() => stocks.filter((stock) => {
    const matchesMedicine = !stockCountForm.medicineId || stock.medicineId === stockCountForm.medicineId;
    const matchesLocation = !stockCountForm.locationId || stock.locationId === stockCountForm.locationId;
    return matchesMedicine && matchesLocation;
  }), [stockCountForm.locationId, stockCountForm.medicineId, stocks]);
  const selectedCountStock = React.useMemo(
    () => stocks.find((stock) => stock.id === stockCountForm.stockBatchId) || null,
    [stockCountForm.stockBatchId, stocks],
  );
  const countVariance = React.useMemo(() => {
    if (!selectedCountStock || !stockCountForm.countedQuantity.trim()) {
      return null;
    }
    const countedQuantityValue = Number(stockCountForm.countedQuantity);
    if (Number.isNaN(countedQuantityValue)) {
      return null;
    }
    return countedQuantityValue - selectedCountStock.quantityOnHand;
  }, [selectedCountStock, stockCountForm.countedQuantity]);
  const expiryReportRows = React.useMemo(() => {
    const term = expiryReportMedicineId.trim().toLowerCase();
    return stocks
      .filter((stock) => !!stock.expiryDate)
      .filter((stock) => !selectedLocationId || stock.locationId === selectedLocationId)
      .filter((stock) => !term || [stock.medicineName, stock.batchNumber, stock.purchaseReferenceNumber, stock.locationName]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)))
      .map((stock) => {
        const state = expiryState(stock.expiryDate);
        return {
          ...stock,
          expiryBucket: state.bucket,
          expiryLabel: state.label,
          expiryColor: state.color,
          expiryDays: daysUntil(stock.expiryDate),
        };
      })
      .sort((a, b) => a.expiryDays - b.expiryDays || a.medicineName.localeCompare(b.medicineName));
  }, [expiryReportMedicineId, selectedLocationId, stocks]);
  const expiryBucketCounts = React.useMemo(() => expiryReportRows.reduce((acc, row) => {
    acc[row.expiryBucket] = (acc[row.expiryBucket] || 0) + 1;
    return acc;
  }, { "Expired": 0, "0-30": 0, "31-60": 0, "61-90": 0, "91+": 0 } as Record<string, number>), [expiryReportRows]);
  const totalQuantity = React.useMemo(() => stocks.reduce((sum, stock) => sum + stock.quantityOnHand, 0), [stocks]);
  const estimatedStockValue = React.useMemo(() => stocks.reduce((sum, stock) => {
    const rate = stock.sellingPrice ?? stock.unitCost ?? 0;
    return sum + (stock.quantityOnHand * rate);
  }, 0), [stocks]);
  const todayMovementCount = React.useMemo(() => {
    const todayKey = new Date().toISOString().slice(0, 10);
    return transactions.filter((transaction) => transaction.createdAt.slice(0, 10) === todayKey).length;
  }, [transactions]);
  const paidLocationsLabel = React.useMemo(
    () => locations.find((location) => location.id === selectedLocationId)?.locationName || "All locations",
    [locations, selectedLocationId],
  );
  const filteredTransactionStocks = React.useMemo(
    () => stocks.filter((stock) => !transactionForm.medicineId || stock.medicineId === transactionForm.medicineId),
    [stocks, transactionForm.medicineId],
  );
  const filteredTransferStocks = React.useMemo(
    () => stocks.filter((stock) => !transferForm.medicineId || stock.medicineId === transferForm.medicineId),
    [stocks, transferForm.medicineId],
  );
  const filteredCustomerSales = React.useMemo(() => {
    const term = saleSearch.trim().toLowerCase();
    if (!term) {
      return sales;
    }
    return sales.filter((sale) =>
      [sale.saleNumber, sale.patientName, sale.customerName, sale.customerMobile, sale.items.map((item) => item.medicineName).join(" ")]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [saleSearch, sales]);
  const selectedCustomerSale = React.useMemo(
    () => sales.find((sale) => sale.id === customerReturnSaleId) ?? null,
    [customerReturnSaleId, sales],
  );
  const selectedCustomerReturnItem = React.useMemo(
    () => selectedCustomerSale?.items.find((item) => item.id === customerReturnLineId) ?? null,
    [customerReturnLineId, selectedCustomerSale],
  );
  const customerReturnHistory = React.useMemo(
    () => transactions.filter((row) => ["RETURN", "CUSTOMER_RETURN_IN", "CUSTOMER_RETURN_NON_SELLABLE"].includes(row.transactionType)),
    [transactions],
  );
  const vendorReturnHistory = React.useMemo(
    () => transactions.filter((row) => row.transactionType === "VENDOR_RETURN_OUT"),
    [transactions],
  );
  const writeOffHistory = React.useMemo(
    () => transactions.filter((row) => row.transactionType === "WRITE_OFF"),
    [transactions],
  );
  const vendorReturnBatches = React.useMemo(
    () => stocks.filter((stock) => !vendorReturnForm.medicineId || stock.medicineId === vendorReturnForm.medicineId),
    [stocks, vendorReturnForm.medicineId],
  );
  const writeOffBatches = React.useMemo(
    () => stocks.filter((stock) => !writeOffForm.medicineId || stock.medicineId === writeOffForm.medicineId),
    [stocks, writeOffForm.medicineId],
  );

  React.useEffect(() => {
    if (selectedCountStock) {
      setStockCountForm((current) => ({
        ...current,
        medicineId: selectedCountStock.medicineId,
        locationId: selectedCountStock.locationId || "",
      }));
    }
  }, [selectedCountStock]);

  React.useEffect(() => {
    if (stockCountForm.stockBatchId && !countableStocks.some((stock) => stock.id === stockCountForm.stockBatchId)) {
      setStockCountForm((current) => ({ ...current, stockBatchId: "", countedQuantity: "" }));
    }
  }, [countableStocks, stockCountForm.stockBatchId]);

  const openQuickCreateMedicine = React.useCallback((seedText = "") => {
    setQuickMedicineForm((current) => ({
      ...emptyQuickMedicineForm(),
      medicineName: seedText.trim(),
      barcode: stockForm.barcode.trim() || current.barcode,
      qrCode: stockForm.qrCode.trim() || current.qrCode,
      externalCode: stockForm.externalCode.trim() || current.externalCode,
      defaultPrice: stockForm.sellingPrice.trim() ? Number(stockForm.sellingPrice) : null,
      active: true,
    }));
    setQuickMedicineOpen(true);
  }, [stockForm.barcode, stockForm.externalCode, stockForm.qrCode, stockForm.sellingPrice]);

  const saveQuickMedicine = async () => {
    if (!auth.accessToken || !auth.tenantId || !quickMedicineForm.medicineName.trim()) {
      setError("Enter a medicine name before saving.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const created = await createMedicine(auth.accessToken, auth.tenantId, {
        ...quickMedicineForm,
        medicineName: quickMedicineForm.medicineName.trim(),
        genericName: quickMedicineForm.genericName?.trim() || null,
        brandName: quickMedicineForm.brandName?.trim() || null,
        category: quickMedicineForm.category?.trim() || null,
        dosageForm: quickMedicineForm.dosageForm?.trim() || null,
        strength: quickMedicineForm.strength?.trim() || null,
        unit: quickMedicineForm.unit?.trim() || null,
        manufacturer: quickMedicineForm.manufacturer?.trim() || null,
        barcode: quickMedicineForm.barcode?.trim() || null,
        qrCode: quickMedicineForm.qrCode?.trim() || null,
        externalCode: quickMedicineForm.externalCode?.trim() || null,
      });
      await loadAll();
      setStockForm((current) => ({ ...current, medicineId: created.id }));
      setMedicineSearchInput(created.medicineName);
      setQuickMedicineOpen(false);
      setSuccess("Medicine created. Continue adding the stock batch.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create medicine");
    } finally {
      setSaving(false);
    }
  };

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const [medicineRows, stockRows, transactionRows, lowStockRows, locationRows, saleRows] = await Promise.all([
      getMedicines(auth.accessToken, auth.tenantId),
      getStocks(auth.accessToken, auth.tenantId),
      getInventoryTransactions(auth.accessToken, auth.tenantId),
      getLowStock(auth.accessToken, auth.tenantId),
      getInventoryLocations(auth.accessToken, auth.tenantId),
      listPharmacyPosSales(auth.accessToken, auth.tenantId).catch(() => [] as PharmacyPosSale[]),
    ]);
    setMedicines(medicineRows);
    setStocks(stockRows);
    setTransactions(transactionRows);
    setLowStock(lowStockRows);
    setLocations(locationRows);
    setSales(saleRows);
    setSelectedLocationId((current) => current || locationRows.find((location) => location.defaultLocation)?.id || null);
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        await loadAll();
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load inventory");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, loadAll]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  const saveStock = async () => {
    if (!auth.accessToken || !auth.tenantId || !stockForm.medicineId) {
      setError("Select a medicine before saving stock.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const body = stockInput(stockForm);
      if (selectedStockId) {
        await updateStock(auth.accessToken, auth.tenantId, selectedStockId, body);
      } else {
        await createStock(auth.accessToken, auth.tenantId, body);
      }
      setStockForm(emptyStockForm());
      setSelectedStockId(null);
      setMedicineSearchInput("");
      await loadAll();
      setSuccess("Stock saved");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save stock";
      setError(message.includes("Stock batch already exists")
        ? "Stock batch already exists for this medicine and location. Edit existing batch or use a different batch number."
        : message);
    } finally {
      setSaving(false);
    }
  };

  const editStock = (stock: Stock) => {
    setSelectedStockId(stock.id);
    setMedicineSearchInput(stock.medicineName);
    setStockForm({
      medicineId: stock.medicineId,
      locationId: stock.locationId || "",
      batchNumber: stock.batchNumber || "",
      purchaseReferenceNumber: stock.purchaseReferenceNumber || "",
      barcode: stock.barcode || "",
      qrCode: stock.qrCode || "",
      externalCode: stock.externalCode || "",
      expiryDate: stock.expiryDate || "",
      quantityOnHand: stock.quantityOnHand.toString(),
      lowStockThreshold: stock.lowStockThreshold?.toString() || "",
      unitCost: stock.unitCost?.toString() || "",
      sellingPrice: stock.sellingPrice?.toString() || "",
      active: stock.active,
    });
  };

  React.useEffect(() => {
    if (!stockForm.medicineId) {
      return;
    }
    const medicine = medicines.find((row) => row.id === stockForm.medicineId);
    if (medicine && medicine.medicineName !== medicineSearchInput) {
      setMedicineSearchInput(medicine.medicineName);
    }
  }, [medicines, medicineSearchInput, stockForm.medicineId]);

  const saveTransaction = async () => {
    if (!auth.accessToken || !auth.tenantId || !transactionForm.medicineId || !transactionForm.quantity.trim()) {
      setError("Select a medicine and quantity before saving a transaction.");
      return;
    }
    if (["ADJUSTMENT", "ADJUSTMENT_IN", "ADJUSTMENT_OUT", "CUSTOMER_RETURN_IN", "CUSTOMER_RETURN_NON_SELLABLE", "VENDOR_RETURN_OUT", "WRITE_OFF"].includes(transactionForm.transactionType) && !transactionForm.notes.trim()) {
      setError("Reason is required for this stock movement.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await createInventoryTransaction(auth.accessToken, auth.tenantId, transactionInput(transactionForm));
      setTransactionForm(emptyTransactionForm());
      await loadAll();
      setSuccess("Inventory transaction saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save inventory transaction");
    } finally {
      setSaving(false);
    }
  };

  const submitCustomerReturn = async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedCustomerSale || !selectedCustomerReturnItem) {
      setError("Select a sale line before submitting a customer return.");
      return;
    }
    const quantity = Number(customerReturnQuantity);
    const returnable = selectedCustomerReturnItem.quantity - selectedCustomerReturnItem.returnedQuantity;
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setError("Return quantity must be positive.");
      return;
    }
    if (quantity > returnable) {
      setError("Return quantity exceeds the remaining sold quantity.");
      return;
    }
    if (!customerReturnReason.trim()) {
      setError("Reason is required for customer returns.");
      return;
    }
    if (customerReturnReusable && selectedCustomerReturnItem.expiryDate && new Date(selectedCustomerReturnItem.expiryDate) < new Date()) {
      setError("Batch expired and cannot be sold or dispensed.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await returnPharmacyPosSale(auth.accessToken, auth.tenantId, selectedCustomerSale.id, {
        reason: customerReturnReason.trim(),
        refundMode: customerReturnMode,
        referenceNumber: customerReturnReference.trim() || null,
        notes: `Customer return from Inventory: ${customerReturnReusable ? "Reusable" : "Non-sellable"} - ${customerReturnReason.trim()}`,
        items: [{
          saleItemId: selectedCustomerReturnItem.id,
          quantity,
          reusable: customerReturnReusable,
        }],
      });
      await loadAll();
      setCustomerReturnQuantity("1");
      setCustomerReturnReason("");
      setCustomerReturnReference("");
      setSuccess(selectedCustomerSale.paidAmount > 0
        ? "Customer return processed. Refund can be processed from Billing / Refunds."
        : "Customer return processed.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Customer return could not be processed.");
    } finally {
      setSaving(false);
    }
  };

  const submitVendorReturn = async () => {
    if (!auth.accessToken || !auth.tenantId || !vendorReturnForm.stockBatchId || !vendorReturnForm.medicineId) {
      setError("Select a medicine batch before submitting a vendor return.");
      return;
    }
    const batch = stocks.find((stock) => stock.id === vendorReturnForm.stockBatchId) ?? null;
    const quantity = Number(vendorReturnForm.quantity);
    if (!batch) {
      setError("Select a valid batch.");
      return;
    }
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setError("Return quantity must be positive.");
      return;
    }
    if (quantity > batch.quantityOnHand) {
      setError("Insufficient stock available.");
      return;
    }
    if (!vendorReturnForm.reason.trim()) {
      setError("Reason is required for vendor returns.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await createInventoryTransaction(auth.accessToken, auth.tenantId, {
        medicineId: batch.medicineId,
        stockBatchId: batch.id,
        transactionType: "VENDOR_RETURN_OUT",
        quantity,
        reason: vendorReturnForm.reason.trim(),
        referenceType: "VENDOR_RETURN",
        referenceId: batch.id,
        notes: [
          batch.supplierName ? `Supplier ${batch.supplierName}` : null,
          batch.purchaseReferenceNumber ? `Invoice/GRN ${batch.purchaseReferenceNumber}` : null,
          vendorReturnForm.supplierReference.trim() || null,
          vendorReturnForm.notes.trim() || null,
        ].filter(Boolean).join(" • "),
      });
      await loadAll();
      setVendorReturnForm({ medicineId: "", stockBatchId: "", quantity: "", supplierReference: "", reason: "", notes: "" });
      setSuccess("Vendor return posted and stock reduced.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Vendor return could not be processed.");
    } finally {
      setSaving(false);
    }
  };

  const submitWriteOff = async () => {
    if (!auth.accessToken || !auth.tenantId || !writeOffForm.stockBatchId || !writeOffForm.medicineId) {
      setError("Select a medicine batch before posting a write-off.");
      return;
    }
    const batch = stocks.find((stock) => stock.id === writeOffForm.stockBatchId) ?? null;
    const quantity = Number(writeOffForm.quantity);
    if (!batch) {
      setError("Select a valid batch.");
      return;
    }
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setError("Write-off quantity must be positive.");
      return;
    }
    if (quantity > batch.quantityOnHand) {
      setError("Insufficient stock available.");
      return;
    }
    if (!writeOffForm.reason.trim()) {
      setError("Reason is required for write-offs.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await createInventoryTransaction(auth.accessToken, auth.tenantId, {
        medicineId: batch.medicineId,
        stockBatchId: batch.id,
        transactionType: "WRITE_OFF",
        quantity,
        reason: writeOffForm.reason.trim(),
        referenceType: "WRITE_OFF",
        referenceId: batch.id,
        notes: [
          `Batch ${batch.batchNumber || "NA"}`,
          batch.locationName || null,
          writeOffForm.notes.trim() || null,
        ].filter(Boolean).join(" • "),
      });
      await loadAll();
      setWriteOffForm({ medicineId: "", stockBatchId: "", quantity: "", reason: "", notes: "" });
      setSuccess("Write-off posted and stock reduced.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Write-off could not be processed.");
    } finally {
      setSaving(false);
    }
  };

  const currentStock = selectedStockId ? stocks.find((stock) => stock.id === selectedStockId) || null : null;

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Inventory
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Physical stock control, batch visibility, expiry monitoring, and inventory movements.
          </Typography>
        </Box>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}

      <Card>
        <CardContent>
          <Tabs value={tab} onChange={(_, next) => setTab(next)} variant="scrollable" scrollButtons="auto">
            {TABS.map((item) => (
              <Tab key={item.value} value={item.value} label={item.label} />
            ))}
          </Tabs>
        </CardContent>
      </Card>

      {loading ? (
        <Box sx={{ display: "grid", placeItems: "center", minHeight: 240 }}>
          <CircularProgress />
        </Box>
      ) : null}

      {tab === "stocks" ? (
        <Grid container spacing={2}>
          <Grid size={12}>
            <Grid container spacing={1.5}>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Stock batches" value={stocks.length} helper={`${paidLocationsLabel} workspace`} />
              </Grid>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Low stock" value={lowStock.length} tone={lowStock.length ? "error" : "success"} helper="Needs replenishment" />
              </Grid>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Expiring soon" value={expiringSoonCount} tone={expiringSoonCount ? "warning" : "success"} helper="Within 30 days" />
              </Grid>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Total quantity" value={totalQuantity} helper={`Est. value ${formatCurrency(estimatedStockValue)}`} />
              </Grid>
              <Grid size={{ xs: 6, md: 2.4 }}>
                <CompactStatCard label="Today movements" value={todayMovementCount} helper="Transactions posted today" />
              </Grid>
            </Grid>
          </Grid>

          <Grid size={12}>
            <CompactFilterCard
              title="Stock operations"
              subtitle="Inventory manages batch-wise physical stock. Medicine catalogue is maintained in Medicine Master."
              actions={(
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>
                  <Button size="small" variant="outlined" onClick={() => navigate("/pharmacy/operations?tab=reconciliation")}>Open Reconciliation</Button>
                </Stack>
              )}
            >
              {medicines.length === 0 ? (
                <CompactEmptyState
                  title="No medicines available."
                  subtitle="Create or upload medicines in Medicine Master before adding stock."
                  action={<Button size="small" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>}
                />
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Use this workspace for batch numbers, expiry, quantity, thresholds, stock adjustments, and transfers.
                </Typography>
              )}
            </CompactFilterCard>
          </Grid>

          <Grid size={{ xs: 12, lg: 4.2 }}>
            <Stack spacing={1.5}>
              <Accordion expanded={stockActionPanel === "add"} onChange={(_, expanded) => setStockActionPanel(expanded ? "add" : "add")} disableGutters sx={{ "&:before": { display: "none" }, borderRadius: 4, overflow: "hidden" }}>
                <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 2, py: 0.5 }}>
                  <Stack spacing={0.4}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      {selectedStockId ? "Edit batch" : "Add batch"}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Batch, expiry, and quantity are managed here. Medicine catalogue is maintained in Medicine Master.
                    </Typography>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ px: 2, pb: 2, pt: 0 }}>
                  <Stack spacing={1.5}>
                    <Alert severity="info" sx={{ py: 0 }}>
                      Medicine not in catalogue? Create it here and continue adding this stock batch.
                    </Alert>
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 8 }}>
                        <Autocomplete<MedicineAutocompleteOption, false, false, false>
                          options={medicineAutocompleteOptions}
                          value={selectedMedicineOption}
                          inputValue={medicineSearchInput}
                          onInputChange={(_, value, reason) => {
                            if (reason !== "reset") {
                              setMedicineSearchInput(value);
                            }
                          }}
                          filterOptions={(options, state) => {
                            const term = state.inputValue.trim().toLowerCase();
                            const filtered = !term
                              ? options
                              : options.filter((option) => {
                                  if (option.kind !== "existing") return false;
                                  const medicine = option.medicine;
                                  return [
                                    medicine.medicineName,
                                    medicine.genericName,
                                    medicine.brandName,
                                    medicine.barcode,
                                    medicine.qrCode,
                                    medicine.externalCode,
                                  ]
                                    .filter(Boolean)
                                    .some((value) => String(value).toLowerCase().includes(term));
                                });
                            const hasExactMatch = options.some((option) => option.kind === "existing" && option.medicine.medicineName.toLowerCase() === term);
                            if (term && !hasExactMatch) {
                              filtered.push({ kind: "create", inputValue: state.inputValue });
                            }
                            return filtered.slice(0, 20);
                          }}
                          onChange={(_, value) => {
                            if (!value) {
                              setStockForm((current) => ({ ...current, medicineId: "" }));
                              return;
                            }
                            if (value.kind === "create") {
                              openQuickCreateMedicine(value.inputValue);
                              return;
                            }
                            setStockForm((current) => ({ ...current, medicineId: value.medicine.id }));
                            setMedicineSearchInput(value.medicine.medicineName);
                          }}
                          getOptionLabel={(option) => option.kind === "create" ? `Create new medicine: ${option.inputValue}` : option.medicine.medicineName}
                          isOptionEqualToValue={(option, value) => {
                            if (option.kind === "create" && value.kind === "create") {
                              return option.inputValue === value.inputValue;
                            }
                            if (option.kind === "existing" && value.kind === "existing") {
                              return option.medicine.id === value.medicine.id;
                            }
                            return false;
                          }}
                          noOptionsText="No medicines found"
                          renderOption={(props, option) => (
                            <Box component="li" {...props}>
                              {option.kind === "create" ? (
                                <Stack spacing={0.2}>
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                    Create new medicine: {option.inputValue}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    Quick add to Medicine Master and continue this stock batch.
                                  </Typography>
                                </Stack>
                              ) : (
                                <Stack spacing={0.25}>
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                    {option.medicine.medicineName}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {[option.medicine.genericName, option.medicine.brandName].filter(Boolean).join(" / ") || "No generic or brand"}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {[option.medicine.medicineType, option.medicine.strength, option.medicine.defaultPrice != null ? formatCurrency(option.medicine.defaultPrice) : null].filter(Boolean).join(" • ")}
                                  </Typography>
                                </Stack>
                              )}
                            </Box>
                          )}
                          renderInput={(params) => (
                            <TextField
                              {...params}
                              size="small"
                              label="Medicine"
                              placeholder="Search by name, brand, generic, barcode, QR, or code"
                              helperText="Search medicine name, generic, brand, barcode, QR code, or external code."
                            />
                          )}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <Button fullWidth variant="outlined" sx={{ height: 40 }} onClick={() => openQuickCreateMedicine(medicineSearchInput)} disabled={saving}>
                          Quick Add Medicine
                        </Button>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="stock-location-label">Location</InputLabel>
                          <Select labelId="stock-location-label" label="Location" value={stockForm.locationId} onChange={(e) => setStockForm((current) => ({ ...current, locationId: String(e.target.value) }))}>
                            <MenuItem value="">Default location</MenuItem>
                            {locations.map((location) => (
                              <MenuItem key={location.id} value={location.id}>
                                {location.locationName}{location.defaultLocation ? " (Default)" : ""}
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField size="small" fullWidth label="Batch number" value={stockForm.batchNumber} onChange={(e) => setStockForm((current) => ({ ...current, batchNumber: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField size="small" fullWidth label="Purchase reference" value={stockForm.purchaseReferenceNumber} onChange={(e) => setStockForm((current) => ({ ...current, purchaseReferenceNumber: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <Grid container spacing={1.25}>
                          <Grid size={{ xs: 12, md: 4 }}>
                            <CodeScannerField label="Barcode" value={stockForm.barcode} onChange={(value) => setStockForm((current) => ({ ...current, barcode: value }))} placeholder="Scan or enter barcode" helperText="Use batch label code if available." />
                          </Grid>
                          <Grid size={{ xs: 12, md: 4 }}>
                            <CodeScannerField label="QR code" value={stockForm.qrCode} onChange={(value) => setStockForm((current) => ({ ...current, qrCode: value }))} placeholder="Scan or enter QR code" />
                          </Grid>
                          <Grid size={{ xs: 12, md: 4 }}>
                            <CodeScannerField label="External code" value={stockForm.externalCode} onChange={(value) => setStockForm((current) => ({ ...current, externalCode: value }))} placeholder="Scan or enter code" />
                          </Grid>
                        </Grid>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField size="small" fullWidth label="Expiry date" type="date" value={stockForm.expiryDate} onChange={(e) => setStockForm((current) => ({ ...current, expiryDate: e.target.value }))} InputLabelProps={{ shrink: true }} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField size="small" fullWidth label="Quantity on hand" value={stockForm.quantityOnHand} onChange={(e) => setStockForm((current) => ({ ...current, quantityOnHand: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField size="small" fullWidth label="Low stock threshold" value={stockForm.lowStockThreshold} onChange={(e) => setStockForm((current) => ({ ...current, lowStockThreshold: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="stock-active-label">Active</InputLabel>
                          <Select labelId="stock-active-label" label="Active" value={stockForm.active ? "true" : "false"} onChange={(e) => setStockForm((current) => ({ ...current, active: String(e.target.value) === "true" }))}>
                            <MenuItem value="true">Active</MenuItem>
                            <MenuItem value="false">Inactive</MenuItem>
                          </Select>
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField size="small" fullWidth label="Unit cost" value={stockForm.unitCost} onChange={(e) => setStockForm((current) => ({ ...current, unitCost: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField size="small" fullWidth label="Selling price" value={stockForm.sellingPrice} onChange={(e) => setStockForm((current) => ({ ...current, sellingPrice: e.target.value }))} />
                      </Grid>
                    </Grid>
                    <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                      <Button
                        onClick={async () => {
                          await saveStock();
                        }}
                        disabled={saving}
                      >
                        {selectedStockId ? "Update batch" : "Add batch"}
                      </Button>
                      <Button
                        variant="text"
                        onClick={() => {
                          setStockForm(emptyStockForm());
                          setSelectedStockId(null);
                          setMedicineSearchInput("");
                        }}
                      >
                        Clear
                      </Button>
                      {currentStock ? <Chip size="small" label={`${currentStock.medicineName} • ${currentStock.batchNumber || "No batch"}`} variant="outlined" /> : null}
                    </Box>
                  </Stack>
                </AccordionDetails>
              </Accordion>

              <Accordion expanded={stockActionPanel === "transaction"} onChange={(_, expanded) => setStockActionPanel(expanded ? "transaction" : "add")} disableGutters sx={{ "&:before": { display: "none" }, borderRadius: 4, overflow: "hidden" }}>
                <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 2, py: 0.5 }}>
                  <Stack spacing={0.4}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      Stock adjustment
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Post opening, purchase, adjustment, return, and cancellation transactions without leaving the stock workspace.
                    </Typography>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ px: 2, pb: 2, pt: 0 }}>
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transaction-medicine-label">Medicine</InputLabel>
                        <Select labelId="transaction-medicine-label" label="Medicine" value={transactionForm.medicineId} onChange={(e) => setTransactionForm((current) => ({ ...current, medicineId: String(e.target.value), stockBatchId: "" }))}>
                          <MenuItem value="">Select medicine</MenuItem>
                          {medicines.map((medicine) => (
                            <MenuItem key={medicine.id} value={medicine.id}>
                              {medicine.medicineName}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transaction-stock-label">Stock batch</InputLabel>
                        <Select labelId="transaction-stock-label" label="Stock batch" value={transactionForm.stockBatchId} onChange={(e) => setTransactionForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}>
                          <MenuItem value="">Select batch</MenuItem>
                          {filteredTransactionStocks.map((stock) => (
                            <MenuItem key={stock.id} value={stock.id}>
                              {(stock.batchNumber || "No batch")} • {stock.locationName || "Main Pharmacy"}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transaction-type-label">Type</InputLabel>
                        <Select labelId="transaction-type-label" label="Type" value={transactionForm.transactionType} onChange={(e) => setTransactionForm((current) => ({ ...current, transactionType: String(e.target.value) as InventoryTransactionType }))}>
                          {TRANSACTION_TYPES.map((type) => (
                            <MenuItem key={type} value={type}>
                              {transactionLabel(type)}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth label="Quantity" value={transactionForm.quantity} onChange={(e) => setTransactionForm((current) => ({ ...current, quantity: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth label="Reference type" value={transactionForm.referenceType} onChange={(e) => setTransactionForm((current) => ({ ...current, referenceType: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth label="Reference ID" value={transactionForm.referenceId} onChange={(e) => setTransactionForm((current) => ({ ...current, referenceId: e.target.value }))} />
                    </Grid>
                    <Grid size={12}>
                      <TextField size="small" fullWidth label="Notes" value={transactionForm.notes} onChange={(e) => setTransactionForm((current) => ({ ...current, notes: e.target.value }))} multiline minRows={2} />
                    </Grid>
                    <Grid size={12}>
                      <Button onClick={() => void saveTransaction()} disabled={saving}>
                        Save transaction
                      </Button>
                    </Grid>
                  </Grid>
                </AccordionDetails>
              </Accordion>
              <Accordion expanded={stockActionPanel === "count"} onChange={(_, expanded) => setStockActionPanel(expanded ? "count" : "add")} disableGutters sx={{ "&:before": { display: "none" }, borderRadius: 4, overflow: "hidden" }}>
                <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 2, py: 0.5 }}>
                  <Stack spacing={0.4}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      Physical stock count
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Compare system quantity with a counted quantity and post a variance adjustment with audit reason.
                    </Typography>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ px: 2, pb: 2, pt: 0 }}>
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="count-medicine-label">Medicine</InputLabel>
                        <Select
                          labelId="count-medicine-label"
                          label="Medicine"
                          value={stockCountForm.medicineId}
                          onChange={(e) => setStockCountForm((current) => ({ ...current, medicineId: String(e.target.value), stockBatchId: "" }))}
                        >
                          <MenuItem value="">Select medicine</MenuItem>
                          {medicines.map((medicine) => (
                            <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="count-location-label">Location</InputLabel>
                        <Select
                          labelId="count-location-label"
                          label="Location"
                          value={stockCountForm.locationId}
                          onChange={(e) => setStockCountForm((current) => ({ ...current, locationId: String(e.target.value), stockBatchId: "" }))}
                        >
                          <MenuItem value="">All locations</MenuItem>
                          {locations.map((location) => (
                            <MenuItem key={location.id} value={location.id}>{location.locationName}</MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="count-batch-label">Batch</InputLabel>
                        <Select
                          labelId="count-batch-label"
                          label="Batch"
                          value={stockCountForm.stockBatchId}
                          onChange={(e) => setStockCountForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}
                        >
                          <MenuItem value="">Select batch</MenuItem>
                          {countableStocks.map((stock) => (
                            <MenuItem key={stock.id} value={stock.id}>
                              {(stock.batchNumber || "No batch")} • {stock.locationName || "Main Pharmacy"} • Qty {stock.quantityOnHand}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        size="small"
                        fullWidth
                        label="System quantity"
                        value={selectedCountStock ? selectedCountStock.quantityOnHand : ""}
                        InputProps={{ readOnly: true }}
                        helperText="Quantity currently recorded in the system."
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        size="small"
                        fullWidth
                        type="number"
                        label="Counted quantity"
                        value={stockCountForm.countedQuantity}
                        onChange={(e) => setStockCountForm((current) => ({ ...current, countedQuantity: e.target.value }))}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        size="small"
                        fullWidth
                        label="Reason"
                        value={stockCountForm.reason}
                        onChange={(e) => setStockCountForm((current) => ({ ...current, reason: e.target.value }))}
                        helperText="Reason is required for audit trail."
                      />
                    </Grid>
                    <Grid size={12}>
                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                        <Chip
                          size="small"
                          label={countVariance === null ? "Variance: -" : `Variance: ${countVariance > 0 ? "+" : ""}${countVariance}`}
                          color={countVariance === null ? "default" : countVariance === 0 ? "default" : countVariance > 0 ? "success" : "warning"}
                        />
                        <Button
                          variant="contained"
                          disabled={saving || !selectedCountStock || !stockCountForm.reason.trim() || stockCountForm.countedQuantity.trim() === "" || countVariance === 0}
                          onClick={async () => {
                            if (!auth.accessToken || !auth.tenantId || !selectedCountStock) {
                              setError("Select a batch before posting the stock count.");
                              return;
                            }
                            const counted = Number(stockCountForm.countedQuantity);
                            if (!Number.isFinite(counted) || counted < 0) {
                              setError("Enter a valid counted quantity.");
                              return;
                            }
                            const variance = counted - selectedCountStock.quantityOnHand;
                            if (variance === 0) {
                              setError("No variance to post for this stock count.");
                              return;
                            }
                            if (!stockCountForm.reason.trim()) {
                              setError("Reason is required for stock count adjustments.");
                              return;
                            }
                            setSaving(true);
                            setError(null);
                            setSuccess(null);
                            try {
                              await createInventoryTransaction(auth.accessToken, auth.tenantId, {
                                medicineId: selectedCountStock.medicineId,
                                stockBatchId: selectedCountStock.id,
                                transactionType: variance > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT",
                                quantity: Math.abs(variance),
                                reason: `Physical stock count: ${stockCountForm.reason.trim()}`,
                                referenceType: "PHYSICAL_STOCK_COUNT",
                                referenceId: selectedCountStock.id,
                                notes: `System ${selectedCountStock.quantityOnHand}, counted ${counted}, variance ${variance > 0 ? "+" : ""}${variance}`,
                              });
                              await loadAll();
                              setSuccess(`Physical stock count posted. Variance ${variance > 0 ? "+" : ""}${variance}.`);
                              setStockCountForm(emptyStockCountForm());
                            } catch (err) {
                              setError(err instanceof Error ? err.message : "Failed to post stock count");
                            } finally {
                              setSaving(false);
                            }
                          }}
                        >
                          Post count
                        </Button>
                      </Stack>
                    </Grid>
                  </Grid>
                </AccordionDetails>
              </Accordion>

              <Accordion expanded={stockActionPanel === "transfer"} onChange={(_, expanded) => setStockActionPanel(expanded ? "transfer" : "add")} disableGutters sx={{ "&:before": { display: "none" }, borderRadius: 4, overflow: "hidden" }}>
                <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 2, py: 0.5 }}>
                  <Stack spacing={0.4}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                      Transfer stock
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Move available stock between locations with a short reason and keep the transaction log in sync.
                    </Typography>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ px: 2, pb: 2, pt: 0 }}>
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transfer-medicine-label">Medicine</InputLabel>
                        <Select labelId="transfer-medicine-label" label="Medicine" value={transferForm.medicineId} onChange={(e) => setTransferForm((current) => ({ ...current, medicineId: String(e.target.value), stockBatchId: "" }))}>
                          <MenuItem value="">Select medicine</MenuItem>
                          {medicines.map((medicine) => (
                            <MenuItem key={medicine.id} value={medicine.id}>
                              {medicine.medicineName}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transfer-stock-label">Batch</InputLabel>
                        <Select labelId="transfer-stock-label" label="Batch" value={transferForm.stockBatchId} onChange={(e) => setTransferForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}>
                          <MenuItem value="">Select batch</MenuItem>
                          {filteredTransferStocks.map((stock) => (
                            <MenuItem key={stock.id} value={stock.id}>
                              {(stock.batchNumber || "No batch")} • {stock.locationName || "Main Pharmacy"}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transfer-from-label">From location</InputLabel>
                        <Select labelId="transfer-from-label" label="From location" value={transferForm.fromLocationId} onChange={(e) => setTransferForm((current) => ({ ...current, fromLocationId: String(e.target.value) }))}>
                          <MenuItem value="">Select location</MenuItem>
                          {locations.map((location) => (
                            <MenuItem key={location.id} value={location.id}>
                              {location.locationName}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="transfer-to-label">To location</InputLabel>
                        <Select labelId="transfer-to-label" label="To location" value={transferForm.toLocationId} onChange={(e) => setTransferForm((current) => ({ ...current, toLocationId: String(e.target.value) }))}>
                          <MenuItem value="">Select location</MenuItem>
                          {locations.map((location) => (
                            <MenuItem key={location.id} value={location.id}>
                              {location.locationName}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth label="Quantity" value={transferForm.quantity} onChange={(e) => setTransferForm((current) => ({ ...current, quantity: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField size="small" fullWidth label="Reason" value={transferForm.reason} onChange={(e) => setTransferForm((current) => ({ ...current, reason: e.target.value }))} />
                    </Grid>
                    <Grid size={12}>
                      <Button
                        variant="outlined"
                        onClick={async () => {
                          if (!auth.accessToken || !auth.tenantId) return;
                          if (!transferForm.medicineId || !transferForm.fromLocationId || !transferForm.toLocationId || !transferForm.quantity.trim()) {
                            setError("Select medicine, source location, destination location, and quantity.");
                            return;
                          }
                          setSaving(true);
                          setError(null);
                          try {
                            await transferInventoryStock(auth.accessToken, auth.tenantId, {
                              medicineId: transferForm.medicineId,
                              stockBatchId: transferForm.stockBatchId || null,
                              fromLocationId: transferForm.fromLocationId,
                              toLocationId: transferForm.toLocationId,
                              quantity: Number(transferForm.quantity),
                              reason: transferForm.reason.trim() || null,
                            });
                            setTransferForm({ medicineId: "", stockBatchId: "", fromLocationId: "", toLocationId: "", quantity: "", reason: "" });
                            await loadAll();
                            setSuccess("Stock transfer recorded");
                          } catch (err) {
                            setError(err instanceof Error ? err.message : "Failed to transfer stock");
                          } finally {
                            setSaving(false);
                          }
                        }}
                        disabled={saving}
                      >
                        Transfer
                      </Button>
                    </Grid>
                  </Grid>
                </AccordionDetails>
              </Accordion>
            </Stack>
          </Grid>

          <Grid size={{ xs: 12, lg: 7.8 }}>
            <Stack spacing={1.5}>
              <CompactFilterCard
                title="Stock workspace"
                subtitle="Batches, quantities, expiry, and activity in one compact view."
              >
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl size="small" fullWidth>
                      <InputLabel id="stock-filter-location-label">Location</InputLabel>
                      <Select
                        labelId="stock-filter-location-label"
                        label="Location"
                        value={selectedLocationId || ""}
                        onChange={(e) => setSelectedLocationId(String(e.target.value) || null)}
                      >
                        <MenuItem value="">All locations</MenuItem>
                        {locations.map((location) => (
                          <MenuItem key={location.id} value={location.id}>
                            {location.locationName}{location.defaultLocation ? " (Default)" : ""}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 8 }}>
                    <TextField
                      size="small"
                      fullWidth
                      label="Scan or enter code"
                      value={stockSearch}
                      onChange={(e) => setStockSearch(e.target.value)}
                      placeholder="barcode / QR / batch / reference"
                      InputProps={{
                        endAdornment: (
                          <InputAdornment position="end">
                            <IconButton size="small" onClick={() => setStockSearchScannerOpen(true)} aria-label="Scan stock code">
                              <CameraAltRoundedIcon fontSize="small" />
                            </IconButton>
                          </InputAdornment>
                        ),
                      }}
                    />
                  </Grid>
                </Grid>
              </CompactFilterCard>
              <CodeScannerDialog
                open={stockSearchScannerOpen}
                title="Scan stock code"
                description="Scan a barcode or QR code to fill the stock workspace search field."
                value={stockSearch}
                onClose={() => setStockSearchScannerOpen(false)}
                onDetected={(code) => setStockSearch(code)}
                manualLabel="Enter stock code"
                manualPlaceholder="barcode / QR / batch / reference"
              />

              <Card>
                <CardContent sx={compactCardContentSx}>
                  <Stack spacing={1.25}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                        Stock list
                      </Typography>
                      <Chip size="small" label={`${visibleStocks.length} visible batches`} variant="outlined" />
                    </Box>
                    {visibleStocks.length === 0 ? (
                      <CompactEmptyState title="No stock batches match this filter." subtitle="Adjust the location or code filter, or add a fresh batch from the quick actions panel." />
                    ) : (
                      <TableContainer sx={{ maxHeight: 432 }}>
                        <Table size="small" stickyHeader>
                          <TableHead>
                            <TableRow>
                              <TableCell>Medicine</TableCell>
                              <TableCell>Location</TableCell>
                              <TableCell>Batch</TableCell>
                              <TableCell>Expiry</TableCell>
                              <TableCell align="right">Qty</TableCell>
                              <TableCell align="right">Threshold</TableCell>
                              <TableCell>Status</TableCell>
                              <TableCell align="right">Actions</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {visibleStocks.map((stock) => (
                              <TableRow key={stock.id} hover selected={stock.id === selectedStockId} sx={{ "& td": { py: 0.85, verticalAlign: "top" } }}>
                                <TableCell>
                                  <Stack spacing={0.2}>
                                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                      {stock.medicineName}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      {stock.medicineType}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      {stock.barcode || stock.externalCode || stock.qrCode || stock.purchaseReferenceNumber || "-"}
                                    </Typography>
                                  </Stack>
                                </TableCell>
                                <TableCell>{stock.locationName || "Main Pharmacy"}</TableCell>
                                <TableCell>
                                  <Stack spacing={0.2}>
                                    <Typography variant="body2">{stock.batchNumber || "-"}</Typography>
                                    <Typography variant="caption" color="text.secondary">{stock.purchaseReferenceNumber || "-"}</Typography>
                                  </Stack>
                                </TableCell>
                                <TableCell>
                                  <Stack spacing={0.3}>
                                    <Chip size="small" label={expiryState(stock.expiryDate).label} color={expiryState(stock.expiryDate).color} />
                                    <Typography variant="caption" color="text.secondary">{stock.expiryDate || "No expiry date"}</Typography>
                                  </Stack>
                                </TableCell>
                                <TableCell align="right">{stock.quantityOnHand}</TableCell>
                                <TableCell align="right">{stock.lowStockThreshold ?? "-"}</TableCell>
                                <TableCell>
                                  <Chip
                                    size="small"
                                    label={stock.expiryDate && daysUntil(stock.expiryDate) < 0 ? "EXPIRED" : stock.active ? "Active" : "Inactive"}
                                    color={stock.expiryDate && daysUntil(stock.expiryDate) < 0 ? "error" : stock.active ? statusColor(stock.quantityOnHand, stock.lowStockThreshold) : "default"}
                                  />
                                </TableCell>
                                <TableCell align="right">
                                  <Button size="small" onClick={() => { editStock(stock); setStockActionPanel("add"); }}>
                                    Edit
                                  </Button>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </Stack>
                </CardContent>
              </Card>

              <Card>
                <CardContent sx={compactCardContentSx}>
                  <Stack spacing={1.25}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                        Inventory transactions
                      </Typography>
                      <Chip size="small" label={`${transactions.length} logged`} variant="outlined" />
                    </Box>
                    {transactions.length === 0 ? (
                      <CompactEmptyState title="No inventory transactions yet." subtitle="Adjustments, purchases, dispenses, returns, and transfers will appear here once posted." />
                    ) : (
                      <TableContainer sx={{ maxHeight: 360 }}>
                        <Table size="small" stickyHeader>
                          <TableHead>
                            <TableRow>
                              <TableCell>Medicine</TableCell>
                              <TableCell>Type</TableCell>
                              <TableCell align="right">Before</TableCell>
                              <TableCell align="right">After</TableCell>
                              <TableCell align="right">Quantity</TableCell>
                              <TableCell>Reference</TableCell>
                              <TableCell>Adjusted by</TableCell>
                              <TableCell>Notes</TableCell>
                              <TableCell>Created</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {transactions.map((transaction) => (
                              <TableRow key={transaction.id} sx={{ "& td": { py: 0.8, verticalAlign: "top" } }}>
                                <TableCell>{medicineById.get(transaction.medicineId)?.medicineName || transaction.medicineId}</TableCell>
                                <TableCell>{transactionLabel(transaction.transactionType)}</TableCell>
                                <TableCell align="right">{transaction.beforeQuantity ?? "-"}</TableCell>
                                <TableCell align="right">{transaction.afterQuantity ?? "-"}</TableCell>
                                <TableCell align="right">{transaction.quantity}</TableCell>
                                <TableCell>
                                  <Stack spacing={0.2}>
                                    <Typography variant="caption" color="text.secondary">{transaction.referenceType || "-"}</Typography>
                                    <Typography variant="body2">{transaction.referenceId || "-"}</Typography>
                                  </Stack>
                                </TableCell>
                                <TableCell>{transaction.adjustedByName || transaction.createdBy || "-"}</TableCell>
                                <TableCell sx={{ maxWidth: 240 }}>{transaction.notes || "-"}</TableCell>
                                <TableCell>{new Date(transaction.createdAt).toLocaleString()}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      {tab === "expiry-report" ? (
        <Grid container spacing={2}>
          <Grid size={12}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        Near expiry / expired report
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Buckets are grouped by expiry date so you can move medicine before it becomes unusable.
                      </Typography>
                    </Box>
                    <Chip size="small" label={`${expiryReportRows.length} batches`} variant="outlined" />
                  </Box>
                  <Grid container spacing={1.25}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth size="small">
                        <InputLabel id="expiry-report-location-label">Location</InputLabel>
                        <Select
                          labelId="expiry-report-location-label"
                          label="Location"
                          value={selectedLocationId || ""}
                          onChange={(e) => setSelectedLocationId(String(e.target.value) || null)}
                        >
                          <MenuItem value="">All locations</MenuItem>
                          {locations.map((location) => (
                            <MenuItem key={location.id} value={location.id}>
                              {location.locationName}{location.defaultLocation ? " (Default)" : ""}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField
                        size="small"
                        fullWidth
                        label="Search medicine / batch"
                        value={expiryReportMedicineId}
                        onChange={(e) => setExpiryReportMedicineId(e.target.value)}
                        placeholder="medicine name / batch / reference"
                      />
                    </Grid>
                  </Grid>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip size="small" color="error" label={`Expired ${expiryBucketCounts.Expired || 0}`} />
                    <Chip size="small" color="warning" label={`0-30 days ${expiryBucketCounts["0-30"] || 0}`} />
                    <Chip size="small" color="info" label={`31-60 days ${expiryBucketCounts["31-60"] || 0}`} />
                    <Chip size="small" color="secondary" label={`61-90 days ${expiryBucketCounts["61-90"] || 0}`} />
                    <Chip size="small" color="success" label={`91+ days ${expiryBucketCounts["91+"] || 0}`} />
                  </Stack>
                  {expiryReportRows.length === 0 ? (
                    <CompactEmptyState title="No expiry report rows found." subtitle="Adjust the medicine or location filter to inspect a different slice of inventory." />
                  ) : (
                    <TableContainer sx={{ maxHeight: 420 }}>
                      <Table size="small" stickyHeader>
                        <TableHead>
                          <TableRow>
                            <TableCell>Medicine</TableCell>
                            <TableCell>Batch</TableCell>
                            <TableCell>Location</TableCell>
                            <TableCell>Expiry</TableCell>
                            <TableCell align="right">Qty</TableCell>
                            <TableCell>Bucket</TableCell>
                            <TableCell>Status</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {expiryReportRows.map((row) => (
                            <TableRow key={row.id} sx={{ "& td": { py: 0.8, verticalAlign: "top" } }}>
                              <TableCell>{row.medicineName}</TableCell>
                              <TableCell>{row.batchNumber || "-"}</TableCell>
                              <TableCell>{row.locationName || "Main Pharmacy"}</TableCell>
                              <TableCell>{row.expiryDate || "-"}</TableCell>
                              <TableCell align="right">{row.quantityOnHand}</TableCell>
                              <TableCell>
                                <Chip size="small" label={row.expiryLabel} color={row.expiryColor} />
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={row.expiryBucket === "Expired" ? "EXPIRED" : "AVAILABLE"} color={row.expiryBucket === "Expired" ? "error" : "success"} />
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {tab === "low-stock" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Low stock medicines
                  </Typography>
                  {lowStock.length === 0 ? (
                    <Alert severity="info">No low stock items are currently blocking dispensing. Add or replenish stock batches to keep the queue moving.</Alert>
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Medicine</TableCell>
                          <TableCell>Batch</TableCell>
                          <TableCell>Expiry</TableCell>
                          <TableCell align="right">Qty</TableCell>
                          <TableCell align="right">Threshold</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {lowStock.map((row) => (
                          <TableRow key={row.stockId} hover sx={{ "& td": { fontWeight: row.quantityOnHand <= (row.lowStockThreshold ?? 5) ? 700 : 400 } }}>
                            <TableCell>{row.medicineName}</TableCell>
                            <TableCell>{row.batchNumber || "-"}</TableCell>
                            <TableCell>
                              <Chip size="small" label={expiryState(row.expiryDate).label} color={expiryState(row.expiryDate).color} />
                            </TableCell>
                            <TableCell align="right">{row.quantityOnHand}</TableCell>
                            <TableCell align="right">{row.lowStockThreshold ?? "-"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Quick summary
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Inventory transactions and low-stock checks run tenant-side only. Use the stock tab to add batches, monitor expiry, and keep medicine availability current.
                  </Typography>
                  <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                    <Chip label={`${medicines.length} medicines`} />
                    <Chip label={`${stocks.length} stock records`} />
                    <Chip label={`${lowStock.length} low stock`} color={lowStock.length ? "error" : "success"} />
                  </Box>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {tab === "returns" ? (
        <Stack spacing={2}>
          <Card>
            <CardContent>
              <Stack spacing={1.5}>
                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>Customer Returns</Typography>
                    <Typography variant="body2" color="text.secondary">Search a completed pharmacy sale, choose the line, and record a reusable or non-sellable return.</Typography>
                  </Box>
                  <Chip size="small" label={`${customerReturnHistory.length} return movements`} variant="outlined" />
                </Box>
                <Grid container spacing={1.25}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField size="small" fullWidth label="Search sale / receipt" value={saleSearch} onChange={(e) => setSaleSearch(e.target.value)} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="customer-return-sale-label">Sale / receipt</InputLabel>
                      <Select
                        labelId="customer-return-sale-label"
                        label="Sale / receipt"
                        value={customerReturnSaleId}
                        onChange={(e) => {
                          const value = String(e.target.value);
                          setCustomerReturnSaleId(value);
                          setCustomerReturnLineId("");
                        }}
                      >
                        <MenuItem value="">Select sale</MenuItem>
                        {filteredCustomerSales.map((sale) => (
                          <MenuItem key={sale.id} value={sale.id}>
                            {sale.saleNumber} • {sale.patientName || sale.customerName || "Walk-in"} • Due {sale.dueAmount}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="customer-return-line-label">Medicine line</InputLabel>
                      <Select
                        labelId="customer-return-line-label"
                        label="Medicine line"
                        value={customerReturnLineId}
                        onChange={(e) => setCustomerReturnLineId(String(e.target.value))}
                      >
                        <MenuItem value="">Select line</MenuItem>
                        {selectedCustomerSale?.items.map((item) => {
                          const remaining = item.quantity - item.returnedQuantity;
                          return (
                            <MenuItem key={item.id} value={item.id} disabled={remaining <= 0}>
                              {item.medicineName} • Sold {item.quantity} • Returned {item.returnedQuantity} • Remaining {remaining}
                            </MenuItem>
                          );
                        })}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField size="small" fullWidth type="number" label="Return quantity" value={customerReturnQuantity} onChange={(e) => setCustomerReturnQuantity(e.target.value)} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="customer-return-mode-label">Refund mode</InputLabel>
                      <Select labelId="customer-return-mode-label" label="Refund mode" value={customerReturnMode} onChange={(e) => setCustomerReturnMode(e.target.value as PaymentMode)}>
                        <MenuItem value="CASH">CASH</MenuItem>
                        <MenuItem value="UPI">UPI</MenuItem>
                        <MenuItem value="CARD">CARD</MenuItem>
                        <MenuItem value="OTHER">OTHER</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField size="small" fullWidth label="Reason" value={customerReturnReason} onChange={(e) => setCustomerReturnReason(e.target.value)} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField size="small" fullWidth label="Reference number" value={customerReturnReference} onChange={(e) => setCustomerReturnReference(e.target.value)} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="customer-return-reusable-label">Condition</InputLabel>
                      <Select labelId="customer-return-reusable-label" label="Condition" value={customerReturnReusable ? "reusable" : "non_sellable"} onChange={(e) => setCustomerReturnReusable(String(e.target.value) === "reusable")}>
                        <MenuItem value="reusable">Reusable</MenuItem>
                        <MenuItem value="non_sellable">Damaged / Expired / Non-sellable</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <Alert severity="info" sx={{ height: "100%", alignItems: "center" }}>
                      {selectedCustomerSale?.paidAmount && selectedCustomerSale.paidAmount > 0
                        ? "Refund can be processed from Billing / Refunds."
                        : "No payment recorded. Return will only adjust inventory and history."}
                    </Alert>
                  </Grid>
                </Grid>
                <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                  <Button
                    variant="contained"
                    disabled={saving || !selectedCustomerSale || !selectedCustomerReturnItem}
                    onClick={() => void submitCustomerReturn()}
                  >
                    Process Customer Return
                  </Button>
                  <Button variant="outlined" onClick={() => {
                    setCustomerReturnSaleId("");
                    setCustomerReturnLineId("");
                    setCustomerReturnQuantity("1");
                    setCustomerReturnReason("");
                    setCustomerReturnReference("");
                    setSaleSearch("");
                    setCustomerReturnReusable(true);
                  }}>
                    Clear
                  </Button>
                </Box>
                {customerReturnHistory.length ? (
                  <TableContainer sx={{ maxHeight: 260 }}>
                    <Table size="small" stickyHeader>
                      <TableHead>
                        <TableRow>
                          <TableCell>Medicine</TableCell>
                          <TableCell>Type</TableCell>
                          <TableCell align="right">Qty</TableCell>
                          <TableCell>Reference</TableCell>
                          <TableCell>Reason</TableCell>
                          <TableCell>Adjusted by</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {customerReturnHistory.slice(0, 8).map((row) => (
                          <TableRow key={row.id}>
                            <TableCell>{medicineById.get(row.medicineId)?.medicineName || row.medicineId}</TableCell>
                            <TableCell>{transactionLabel(row.transactionType)}</TableCell>
                            <TableCell align="right">{row.quantity}</TableCell>
                            <TableCell>{row.businessReference || row.referenceType || "-"}</TableCell>
                            <TableCell>{row.reason || "-"}</TableCell>
                            <TableCell>{row.adjustedByName || row.createdBy || "-"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Card>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                      <Box>
                        <Typography variant="h6" sx={{ fontWeight: 800 }}>Vendor Returns</Typography>
                        <Typography variant="body2" color="text.secondary">Return supplied stock back to the vendor and record the movement for audit.</Typography>
                      </Box>
                      <Chip size="small" label={`${vendorReturnHistory.length} movements`} variant="outlined" />
                    </Box>
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 5 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="vendor-return-medicine-label">Medicine</InputLabel>
                          <Select
                            labelId="vendor-return-medicine-label"
                            label="Medicine"
                            value={vendorReturnForm.medicineId}
                            onChange={(e) => setVendorReturnForm((current) => ({ ...current, medicineId: String(e.target.value), stockBatchId: "" }))}
                          >
                            <MenuItem value="">Select medicine</MenuItem>
                            {medicines.map((medicine) => <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>)}
                          </Select>
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 7 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="vendor-return-batch-label">Batch</InputLabel>
                          <Select
                            labelId="vendor-return-batch-label"
                            label="Batch"
                            value={vendorReturnForm.stockBatchId}
                            onChange={(e) => setVendorReturnForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}
                          >
                            <MenuItem value="">Select batch</MenuItem>
                            {vendorReturnBatches.map((stock) => (
                              <MenuItem key={stock.id} value={stock.id}>
                                {stock.batchNumber || "No batch"} • {stock.locationName || "Main Pharmacy"} • Qty {stock.quantityOnHand}
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <TextField size="small" fullWidth type="number" label="Return quantity" value={vendorReturnForm.quantity} onChange={(e) => setVendorReturnForm((current) => ({ ...current, quantity: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 8 }}>
                        <TextField size="small" fullWidth label="Supplier / invoice reference" value={vendorReturnForm.supplierReference} onChange={(e) => setVendorReturnForm((current) => ({ ...current, supplierReference: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField size="small" fullWidth label="Reason" value={vendorReturnForm.reason} onChange={(e) => setVendorReturnForm((current) => ({ ...current, reason: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <TextField size="small" fullWidth label="Notes" value={vendorReturnForm.notes} onChange={(e) => setVendorReturnForm((current) => ({ ...current, notes: e.target.value }))} />
                      </Grid>
                    </Grid>
                    <Button variant="contained" disabled={saving} onClick={() => void submitVendorReturn()}>Post Vendor Return</Button>
                    {vendorReturnHistory.length ? (
                      <TableContainer sx={{ maxHeight: 240 }}>
                        <Table size="small" stickyHeader>
                          <TableHead>
                            <TableRow>
                              <TableCell>Medicine</TableCell>
                              <TableCell>Qty</TableCell>
                              <TableCell>Reference</TableCell>
                              <TableCell>Reason</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {vendorReturnHistory.slice(0, 6).map((row) => (
                              <TableRow key={row.id}>
                                <TableCell>{medicineById.get(row.medicineId)?.medicineName || row.medicineId}</TableCell>
                                <TableCell align="right">{row.quantity}</TableCell>
                                <TableCell>{row.businessReference || row.referenceType || "-"}</TableCell>
                                <TableCell>{row.reason || "-"}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Card>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
                      <Box>
                        <Typography variant="h6" sx={{ fontWeight: 800 }}>Write-Offs</Typography>
                        <Typography variant="body2" color="text.secondary">Remove damaged, lost, or expired stock with a clear audit movement.</Typography>
                      </Box>
                      <Chip size="small" label={`${writeOffHistory.length} movements`} variant="outlined" />
                    </Box>
                    <Grid container spacing={1.25}>
                      <Grid size={{ xs: 12, md: 5 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="writeoff-medicine-label">Medicine</InputLabel>
                          <Select
                            labelId="writeoff-medicine-label"
                            label="Medicine"
                            value={writeOffForm.medicineId}
                            onChange={(e) => setWriteOffForm((current) => ({ ...current, medicineId: String(e.target.value), stockBatchId: "" }))}
                          >
                            <MenuItem value="">Select medicine</MenuItem>
                            {medicines.map((medicine) => <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>)}
                          </Select>
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 7 }}>
                        <FormControl fullWidth size="small">
                          <InputLabel id="writeoff-batch-label">Batch</InputLabel>
                          <Select
                            labelId="writeoff-batch-label"
                            label="Batch"
                            value={writeOffForm.stockBatchId}
                            onChange={(e) => setWriteOffForm((current) => ({ ...current, stockBatchId: String(e.target.value) }))}
                          >
                            <MenuItem value="">Select batch</MenuItem>
                            {writeOffBatches.map((stock) => (
                              <MenuItem key={stock.id} value={stock.id}>
                                {stock.batchNumber || "No batch"} • {stock.locationName || "Main Pharmacy"} • Qty {stock.quantityOnHand}
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <TextField size="small" fullWidth type="number" label="Write-off quantity" value={writeOffForm.quantity} onChange={(e) => setWriteOffForm((current) => ({ ...current, quantity: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 8 }}>
                        <TextField size="small" fullWidth label="Reason" value={writeOffForm.reason} onChange={(e) => setWriteOffForm((current) => ({ ...current, reason: e.target.value }))} />
                      </Grid>
                      <Grid size={{ xs: 12 }}>
                        <TextField size="small" fullWidth label="Notes" value={writeOffForm.notes} onChange={(e) => setWriteOffForm((current) => ({ ...current, notes: e.target.value }))} />
                      </Grid>
                    </Grid>
                    <Button variant="contained" disabled={saving} onClick={() => void submitWriteOff()}>Post Write-Off</Button>
                    {writeOffHistory.length ? (
                      <TableContainer sx={{ maxHeight: 240 }}>
                        <Table size="small" stickyHeader>
                          <TableHead>
                            <TableRow>
                              <TableCell>Medicine</TableCell>
                              <TableCell>Qty</TableCell>
                              <TableCell>Reference</TableCell>
                              <TableCell>Reason</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {writeOffHistory.slice(0, 6).map((row) => (
                              <TableRow key={row.id}>
                                <TableCell>{medicineById.get(row.medicineId)?.medicineName || row.medicineId}</TableCell>
                                <TableCell align="right">{row.quantity}</TableCell>
                                <TableCell>{row.businessReference || row.referenceType || "-"}</TableCell>
                                <TableCell>{row.reason || "-"}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Stack>
      ) : null}

      <Dialog open={quickMedicineOpen} onClose={() => setQuickMedicineOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Quick Add Medicine</DialogTitle>
        <DialogContent>
          <Grid container spacing={1.25} sx={{ mt: 0.25 }}>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Medicine name" value={quickMedicineForm.medicineName} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, medicineName: e.target.value }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <FormControl fullWidth size="small">
                <InputLabel id="quick-medicine-type-label">Type</InputLabel>
                <Select labelId="quick-medicine-type-label" label="Type" value={quickMedicineForm.medicineType} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, medicineType: String(e.target.value) as MedicineType }))}>
                  {MEDICINE_TYPES.map((type) => (
                    <MenuItem key={type} value={type}>{type}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <FormControl fullWidth size="small">
                <InputLabel id="quick-medicine-active-label">Active</InputLabel>
                <Select labelId="quick-medicine-active-label" label="Active" value={quickMedicineForm.active ? "true" : "false"} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, active: String(e.target.value) === "true" }))}>
                  <MenuItem value="true">Active</MenuItem>
                  <MenuItem value="false">Inactive</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Generic name" value={quickMedicineForm.genericName || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, genericName: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Brand name" value={quickMedicineForm.brandName || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, brandName: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Category" value={quickMedicineForm.category || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, category: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" label="Strength" value={quickMedicineForm.strength || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, strength: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" label="Unit" value={quickMedicineForm.unit || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, unit: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField fullWidth size="small" label="Manufacturer" value={quickMedicineForm.manufacturer || ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, manufacturer: e.target.value || null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CodeScannerField size="small" label="Barcode" value={quickMedicineForm.barcode || ""} onChange={(value) => setQuickMedicineForm((current) => ({ ...current, barcode: value || null }))} placeholder="Scan or enter barcode" />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CodeScannerField size="small" label="QR code" value={quickMedicineForm.qrCode || ""} onChange={(value) => setQuickMedicineForm((current) => ({ ...current, qrCode: value || null }))} placeholder="Scan or enter QR code" />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CodeScannerField size="small" label="External code" value={quickMedicineForm.externalCode || ""} onChange={(value) => setQuickMedicineForm((current) => ({ ...current, externalCode: value || null }))} placeholder="Scan or enter code" />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" type="number" label="Default price" value={quickMedicineForm.defaultPrice ?? ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, defaultPrice: e.target.value ? Number(e.target.value) : null }))} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" type="number" label="Tax %" value={quickMedicineForm.taxRate ?? ""} onChange={(e) => setQuickMedicineForm((current) => ({ ...current, taxRate: e.target.value ? Number(e.target.value) : null }))} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setQuickMedicineOpen(false)}>Cancel</Button>
          <Button variant="outlined" onClick={() => navigate("/pharmacy/medicines")}>Open Medicine Master</Button>
          <Button disabled={saving} onClick={() => void saveQuickMedicine()}>
            {saving ? "Saving..." : "Create Medicine"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
