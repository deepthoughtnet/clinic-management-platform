import * as React from "react";
import { z } from "zod";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  ButtonBase,
  Card,
  CardContent,
  Chip,
  Autocomplete,
  Drawer,
  FormControl,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TableContainer,
  TextField,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, CompactStatCard, WorkflowGuide, compactCardContentSx } from "../../components/compact/CompactUi";
import { createSupplier, getMedicines, listSuppliers, updateSupplier, type Medicine, type Supplier, type SupplierInput } from "../../api/clinicApi";
import { firstZodError, hasDuplicateSupplierName, indianMobileNumber, mapZodErrors, normalizeIndianMobileInput, optionalGstin, supplierSchema } from "@deepthoughtnet/form-validation-kit";

type Workspace = "suppliers" | "purchase-orders" | "supplier-invoices" | "goods-receipt";
type SupplierStatus = "ALL" | "Active" | "Inactive";
type PurchaseOrderStatusFilter = "ALL" | PurchaseOrderRow["status"];

type SupplierFormState = {
  supplierName: string;
  gstNumber: string;
  contactPerson: string;
  phone: string;
  email: string;
  address: string;
  notes: string;
  active: boolean;
};

type PurchaseOrderRow = {
  id: string;
  poNumber: string;
  supplierId: string;
  supplierName: string;
  orderDate: string;
  expectedDelivery: string;
  status: "Draft" | "Generated" | "Cancelled";
  cancelReason: string | null;
  items: PurchaseOrderLineState[];
  totalQty: number;
  subtotal: number;
  totalGst: number;
  totalValue: number;
  updatedAt: string;
};

type PurchaseOrderLineState = {
  medicineId: string;
  medicineName: string;
  quantity: string;
  unitPrice: string;
  gst: string;
  discount: string;
};

type PurchaseOrderFormState = {
  supplierId: string;
  poNumber: string;
  orderDate: string;
  expectedDelivery: string;
  notes: string;
};

type SupplierInvoiceRow = {
  id: string;
  invoiceNumber: string;
  supplierId: string;
  purchaseOrderId: string;
  amount: number;
  status: "Pending" | "Matched" | "Variance";
};

type GoodsReceiptRow = {
  id: string;
  receiptNumber: string;
  supplierId: string;
  purchaseOrderId: string;
  receivedQty: number;
  status: "Pending" | "Posted";
};

const WORKSPACES: Array<{ value: Workspace; label: string }> = [
  { value: "suppliers", label: "Suppliers" },
  { value: "purchase-orders", label: "Purchase Orders" },
  { value: "supplier-invoices", label: "Supplier Invoices" },
  { value: "goods-receipt", label: "Goods Receipt" },
];

const EMPTY_SUPPLIER: SupplierFormState = {
  supplierName: "",
  gstNumber: "",
  contactPerson: "",
  phone: "",
  email: "",
  address: "",
  notes: "",
  active: true,
};

const emptyPoLine: PurchaseOrderLineState = { medicineId: "", medicineName: "", quantity: "", unitPrice: "", gst: "", discount: "" };
const emptyPoForm: PurchaseOrderFormState = { supplierId: "", poNumber: "", orderDate: "", expectedDelivery: "", notes: "" };
const emptyInvoiceForm = { supplierId: "", purchaseOrderId: "", invoiceNumber: "", invoiceDate: "", amount: "" };
const emptyGrnForm = { supplierId: "", purchaseOrderId: "", receiptNumber: "", receivedAt: "", receivedQty: "", batch: "", expiry: "" };

const poQuantityFieldSx = {
  width: 88,
  "& .MuiInputBase-input": {
    textAlign: "right",
  },
} as const;

const poMoneyFieldSx = {
  "& .MuiInputBase-input": {
    textAlign: "right",
  },
} as const;

const supplierFormSchema = supplierSchema.extend({
  supplierName: z.preprocess(
    (value) => typeof value === "string" ? value.trim() : value,
    z.string()
      .min(3, "Supplier name must be 3 to 80 characters and include a letter or number.")
      .max(80, "Supplier name must be 3 to 80 characters and include a letter or number.")
      .refine((value) => /[A-Za-z0-9]/.test(value), "Supplier name must be 3 to 80 characters and include a letter or number."),
  ),
  gstNumber: optionalGstin(),
  contactPerson: z.preprocess(
    (value) => {
      if (value == null) return null;
      if (typeof value !== "string") return value;
      const trimmed = value.trim();
      return trimmed === "" ? null : trimmed;
    },
    z.string().max(80, "Contact person must be 80 characters or fewer.").nullable(),
  ),
  phone: indianMobileNumber("Phone must be a valid 10-digit Indian mobile number."),
  email: z.preprocess(
    (value) => {
      if (value == null) return null;
      if (typeof value !== "string") return value;
      const trimmed = value.trim();
      return trimmed === "" ? null : trimmed;
    },
    z.string().max(120, "Email must be 120 characters or fewer.").email("Enter a valid email address").nullable(),
  ),
  address: z.preprocess(
    (value) => {
      if (value == null) return null;
      if (typeof value !== "string") return value;
      const trimmed = value.trim();
      return trimmed === "" ? null : trimmed;
    },
    z.string().max(250, "Address must be 250 characters or fewer.").nullable(),
  ),
  notes: z.preprocess(
    (value) => {
      if (value == null) return null;
      if (typeof value !== "string") return value;
      const trimmed = value.trim();
      return trimmed === "" ? null : trimmed;
    },
    z.string().max(500, "Notes must be 500 characters or fewer.").nullable(),
  ),
  active: z.boolean(),
});

function positiveIntegerValue(message: string) {
  return z.preprocess(
    (value) => {
      if (value == null || value === "") return value;
      if (typeof value === "string") {
        const parsed = Number(value.trim());
        return Number.isNaN(parsed) ? value : parsed;
      }
      return value;
    },
    z.number().int(message).min(1, message),
  );
}

function optionalPoText(maxLength: number, message: string) {
  return z.preprocess(
    (value) => {
      if (value == null) return undefined;
      if (typeof value !== "string") return value;
      const trimmed = value.trim();
      return trimmed === "" ? undefined : trimmed;
    },
    z.string().max(maxLength, message).optional(),
  );
}

function moneyValue(max: number, message: string, allowZero = true) {
  return z.preprocess(
    (value) => {
      if (value == null || value === "") return value;
      if (typeof value === "string") {
        const parsed = Number(value.trim());
        return Number.isNaN(parsed) ? value : parsed;
      }
      return value;
    },
    allowZero ? z.number().min(0, message) : z.number().positive(message),
  ).refine((value) => typeof value !== "number" || Number.isNaN(value) || Math.round(value * 100) === value * 100, message)
    .refine((value) => typeof value !== "number" || Number.isNaN(value) || value <= max, message);
}

function optionalMoneyValue(max: number, message: string) {
  return z.preprocess(
    (value) => {
      if (value == null || value === "") return 0;
      if (typeof value === "string") {
        const parsed = Number(value.trim());
        return Number.isNaN(parsed) ? value : parsed;
      }
      return value;
    },
    z.number().min(0, message),
  ).refine((value) => typeof value !== "number" || Number.isNaN(value) || Math.round(value * 100) === value * 100, message)
    .refine((value) => typeof value !== "number" || Number.isNaN(value) || value <= max, message);
}

function dateValue(message: string) {
  return z.preprocess(
    (value) => typeof value === "string" ? value.trim() : "",
    z.string().regex(/^\d{4}-\d{2}-\d{2}$/, message),
  );
}

const purchaseOrderLineSchema = z.object({
  medicineId: z.string().trim().min(1, "Medicine is required."),
  medicineName: z.string().trim().min(1, "Medicine is required.").max(200, "Medicine is required."),
  quantity: positiveIntegerValue("Quantity must be a whole number greater than zero."),
  unitPrice: moneyValue(999999, "Unit price must be greater than zero.", false),
  gst: moneyValue(28, "GST must be between 0 and 28.", true),
  discount: optionalMoneyValue(999999, "Discount must be zero or greater."),
});

const purchaseOrderFormSchema = z.object({
  supplierId: z.string().trim().min(1, "Supplier is required."),
  poNumber: z.string().trim().max(60, "PO number must be 60 characters or fewer.").optional().transform((value) => value || ""),
  orderDate: dateValue("PO date is required."),
  expectedDelivery: dateValue("Expected delivery date is required."),
  notes: optionalPoText(500, "Notes must be 500 characters or fewer."),
  cancelReason: optionalPoText(250, "Cancel reason must be 250 characters or fewer."),
  items: z.array(purchaseOrderLineSchema).min(1, "Add at least one item."),
}).superRefine((value, ctx) => {
  const seen = new Map<string, number>();
  value.items.forEach((item, index) => {
    const key = item.medicineId.trim().toLowerCase();
    if (!key) return;
    const firstIndex = seen.get(key);
    if (firstIndex != null) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["items", index, "medicineId"],
        message: "Duplicate medicine rows are not allowed.",
      });
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["items", firstIndex, "medicineId"],
        message: "Duplicate medicine rows are not allowed.",
      });
      return;
    }
    seen.set(key, index);
  });
});

function parseWorkspace(value: string | null): Workspace {
  if (value === "purchase-orders" || value === "supplier-invoices" || value === "goods-receipt") return value;
  return "suppliers";
}

function toSupplierFormState(supplier: Supplier): SupplierFormState {
  return {
    supplierName: supplier.supplierName || "",
    gstNumber: supplier.gstNumber || "",
    contactPerson: supplier.contactPerson || "",
    phone: supplier.phone || "",
    email: supplier.email || "",
    address: supplier.address || "",
    notes: supplier.notes || "",
    active: supplier.active,
  };
}

function parsePurchaseOrderLine(line: PurchaseOrderLineState) {
  const parsed = purchaseOrderLineSchema.safeParse({
    medicineId: line.medicineId,
    medicineName: line.medicineName,
    quantity: line.quantity,
    unitPrice: line.unitPrice,
    gst: line.gst,
    discount: line.discount,
  });
  return parsed;
}

function computePurchaseOrderTotals(lines: PurchaseOrderLineState[]) {
  return lines.reduce((acc, line) => {
    const parsed = parsePurchaseOrderLine(line);
    if (!parsed.success) return acc;
    const qty = parsed.data.quantity;
    const unitPrice = parsed.data.unitPrice;
    const gstRate = parsed.data.gst;
    const discount = parsed.data.discount;
    const lineSubTotal = qty * unitPrice;
    const lineGst = lineSubTotal * (gstRate / 100);
    const lineTotal = Math.max(0, lineSubTotal - discount + lineGst);
    acc.totalQty += qty;
    acc.subtotal += lineSubTotal;
    acc.totalGst += lineGst;
    acc.totalDiscount += discount;
    acc.totalValue += lineTotal;
    return acc;
  }, { totalQty: 0, subtotal: 0, totalGst: 0, totalDiscount: 0, totalValue: 0 });
}

function computePurchaseOrderLineTotal(line: PurchaseOrderLineState) {
  const parsed = parsePurchaseOrderLine(line);
  if (!parsed.success) return null;
  const subtotal = parsed.data.quantity * parsed.data.unitPrice;
  const gstAmount = subtotal * (parsed.data.gst / 100);
  return Math.max(0, subtotal - parsed.data.discount + gstAmount);
}

function normalizePurchaseOrderLine(item: unknown): PurchaseOrderLineState {
  const source = item as Record<string, unknown> | null | undefined;
  return {
    medicineId: typeof source?.medicineId === "string" ? source.medicineId : "",
    medicineName: typeof source?.medicineName === "string"
      ? source.medicineName
      : typeof source?.medicine === "string"
        ? source.medicine
        : "",
    quantity: String(typeof source?.quantity === "number" ? source.quantity : typeof source?.qty === "number" ? source.qty : source?.quantity ?? source?.qty ?? ""),
    unitPrice: String(typeof source?.unitPrice === "number" ? source.unitPrice : typeof source?.unitCost === "number" ? source.unitCost : source?.unitPrice ?? source?.unitCost ?? ""),
    gst: String(typeof source?.gst === "number" ? source.gst : source?.gst ?? ""),
    discount: String(typeof source?.discount === "number" ? source.discount : source?.discount ?? ""),
  };
}

function poFormToLines(lines: PurchaseOrderLineState[]) {
  return lines.map((line) => ({
    medicineId: line.medicineId,
    medicineName: line.medicineName,
    quantity: line.quantity,
    unitPrice: line.unitPrice,
    gst: line.gst,
    discount: line.discount,
  }));
}

function medicineMatchesQuery(medicine: Medicine, query: string) {
  const term = query.trim().toLowerCase();
  if (!term) return true;
  return [
    medicine.medicineName,
    medicine.genericName,
    medicine.brandName,
    medicine.barcode,
    medicine.externalCode,
    medicine.category,
    medicine.strength,
    medicine.unit,
    medicine.manufacturer,
  ]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(term));
}

function isSupplierDuplicate(values: z.infer<typeof supplierFormSchema>, suppliers: Supplier[], editingSupplierId: string | null) {
  const normalizedPhone = normalizeIndianMobileInput(values.phone) as string;
  const normalizedEmail = (values.email || "").trim().toLowerCase();
  const normalizedGst = (values.gstNumber || "").trim().toUpperCase();
  const duplicateName = hasDuplicateSupplierName(values.supplierName, suppliers, editingSupplierId);
  if (duplicateName) return true;

  return suppliers.some((supplier) => {
    if (supplier.id === editingSupplierId) return false;
    const supplierPhone = normalizeIndianMobileInput(supplier.phone || "") as string;
    const supplierEmail = (supplier.email || "").trim().toLowerCase();
    const supplierGst = (supplier.gstNumber || "").trim().toUpperCase();
    return (normalizedGst && supplierGst === normalizedGst)
      || (normalizedPhone && supplierPhone === normalizedPhone)
      || (normalizedEmail && supplierEmail === normalizedEmail);
  });
}

export default function PharmacyProcurePage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const workspace = parseWorkspace(searchParams.get("workspace"));
  const [suppliers, setSuppliers] = React.useState<Supplier[]>([]);
  const [loadingSuppliers, setLoadingSuppliers] = React.useState(true);
  const [supplierError, setSupplierError] = React.useState<string | null>(null);
  const [supplierSuccess, setSupplierSuccess] = React.useState<string | null>(null);
  const [supplierSearch, setSupplierSearch] = React.useState("");
  const [supplierStatusFilter, setSupplierStatusFilter] = React.useState<SupplierStatus>("ALL");
  const [supplierForm, setSupplierForm] = React.useState<SupplierFormState>(EMPTY_SUPPLIER);
  const [supplierFieldErrors, setSupplierFieldErrors] = React.useState<Record<string, string>>({});
  const [editingSupplierId, setEditingSupplierId] = React.useState<string | null>(null);
  const [savingSupplier, setSavingSupplier] = React.useState(false);

  const [medicineCatalog, setMedicineCatalog] = React.useState<Medicine[]>([]);
  const [loadingMedicines, setLoadingMedicines] = React.useState(false);
  const [purchaseOrders, setPurchaseOrders] = React.useState<PurchaseOrderRow[]>([]);
  const [invoices, setInvoices] = React.useState<SupplierInvoiceRow[]>([]);
  const [grns, setGrns] = React.useState<GoodsReceiptRow[]>([]);
  const [poForm, setPoForm] = React.useState<PurchaseOrderFormState>(emptyPoForm);
  const [poLines, setPoLines] = React.useState<PurchaseOrderLineState[]>([{ ...emptyPoLine }]);
  const [poFieldErrors, setPoFieldErrors] = React.useState<Record<string, string>>({});
  const [editingPoId, setEditingPoId] = React.useState<string | null>(null);
  const [poDrawerOpen, setPoDrawerOpen] = React.useState(false);
  const [poSearch, setPoSearch] = React.useState("");
  const [poStatusFilter, setPoStatusFilter] = React.useState<PurchaseOrderStatusFilter>("ALL");
  const [poSupplierFilter, setPoSupplierFilter] = React.useState<string>("ALL");
  const [cancelDialogOpen, setCancelDialogOpen] = React.useState(false);
  const [cancelReason, setCancelReason] = React.useState("");
  const [cancelError, setCancelError] = React.useState<string | null>(null);
  const [invoiceForm, setInvoiceForm] = React.useState(emptyInvoiceForm);
  const [grnForm, setGrnForm] = React.useState(emptyGrnForm);

  const supplierById = React.useMemo(() => new Map(suppliers.map((supplier) => [supplier.id, supplier])), [suppliers]);
  const poById = React.useMemo(() => new Map(purchaseOrders.map((po) => [po.id, po])), [purchaseOrders]);
  const currentPurchaseOrder = React.useMemo(
    () => (editingPoId ? purchaseOrders.find((row) => row.id === editingPoId) || null : null),
    [editingPoId, purchaseOrders],
  );
  const purchaseOrderTotals = React.useMemo(() => computePurchaseOrderTotals(poLines), [poLines]);
  const purchaseOrderStatus = currentPurchaseOrder?.status || "Draft";
  const purchaseOrderStatusColor = purchaseOrderStatus === "Generated" ? "success" : purchaseOrderStatus === "Cancelled" ? "default" : "warning";
  const purchaseOrderStatusLabel = purchaseOrderStatus === "Generated"
    ? "🟢 Generated"
    : purchaseOrderStatus === "Cancelled"
      ? "⚫ Cancelled"
      : "🟡 Draft";
  const purchaseOrderTimestamp = currentPurchaseOrder?.status === "Generated" ? currentPurchaseOrder.updatedAt : null;
  const filteredPurchaseOrders = React.useMemo(() => {
    const term = poSearch.trim().toLowerCase();
    return purchaseOrders.filter((po) => {
      const matchesStatus = poStatusFilter === "ALL" || po.status === poStatusFilter;
      const matchesSupplier = poSupplierFilter === "ALL" || po.supplierId === poSupplierFilter;
      const matchesSearch = !term || [
        po.poNumber,
        po.supplierName,
        po.orderDate,
        po.expectedDelivery,
        po.status,
        po.cancelReason,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
      return matchesStatus && matchesSupplier && matchesSearch;
    });
  }, [poSearch, poStatusFilter, poSupplierFilter, purchaseOrders]);

  const loadSuppliers = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoadingSuppliers(true);
    setSupplierError(null);
    try {
      const rows = await listSuppliers(auth.accessToken, auth.tenantId);
      setSuppliers(rows);
    } catch (err) {
      setSupplierError(err instanceof Error ? err.message : "Failed to load suppliers");
    } finally {
      setLoadingSuppliers(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void loadSuppliers();
  }, [loadSuppliers]);

  React.useEffect(() => {
    if (!auth.accessToken || !auth.tenantId) return;
    let cancelled = false;
    setLoadingMedicines(true);
    void getMedicines(auth.accessToken, auth.tenantId)
      .then((rows) => {
        if (!cancelled) setMedicineCatalog(rows);
      })
      .catch(() => {
        if (!cancelled) setMedicineCatalog([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingMedicines(false);
      });
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId]);

  const visibleSuppliers = React.useMemo(() => {
    const term = supplierSearch.trim().toLowerCase();
    return suppliers.filter((supplier) => {
      const matchesStatus = supplierStatusFilter === "ALL" || (supplierStatusFilter === "Active" ? supplier.active : !supplier.active);
      const matchesSearch = !term || [
        supplier.supplierName,
        supplier.contactPerson,
        supplier.phone,
        supplier.email,
        supplier.gstNumber,
        supplier.address,
        supplier.notes,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));
      return matchesStatus && matchesSearch;
    });
  }, [supplierSearch, supplierStatusFilter, suppliers]);

  const updateWorkspace = (next: Workspace) => {
    navigate(`/pharmacy/procure?workspace=${next}${next === "suppliers" ? "&focus=supplier" : ""}`);
  };

  const resetSupplierForm = React.useCallback(() => {
    setEditingSupplierId(null);
    setSupplierForm(EMPTY_SUPPLIER);
    setSupplierFieldErrors({});
    setSupplierError(null);
  }, []);

  const handleEditSupplier = React.useCallback((supplier: Supplier) => {
    setEditingSupplierId(supplier.id);
    setSupplierForm(toSupplierFormState(supplier));
    setSupplierFieldErrors({});
    setSupplierError(null);
  }, []);

  const handleViewSupplier = React.useCallback((supplier: Supplier) => {
    handleEditSupplier(supplier);
  }, [handleEditSupplier]);

  const handleToggleSupplierStatus = React.useCallback(async (supplier: Supplier) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      await updateSupplier(auth.accessToken, auth.tenantId, supplier.id, {
        supplierName: supplier.supplierName,
        contactPerson: supplier.contactPerson,
        phone: supplier.phone,
        email: supplier.email,
        gstNumber: supplier.gstNumber,
        address: supplier.address,
        notes: supplier.notes,
        active: !supplier.active,
      });
      await loadSuppliers();
      setSupplierSuccess(supplier.active ? "Supplier deactivated." : "Supplier activated.");
    } catch (err) {
      setSupplierError(err instanceof Error ? err.message : "Failed to update supplier");
    }
  }, [auth.accessToken, auth.tenantId, loadSuppliers]);

  const handleSaveSupplier = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = supplierFormSchema.safeParse(supplierForm);
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setSupplierFieldErrors(errors);
      setSupplierError(firstZodError(parsed.error));
      return;
    }

    if (isSupplierDuplicate(parsed.data, suppliers, editingSupplierId)) {
      const errors = {
        supplierName: "Supplier already exists.",
        gstNumber: "Supplier already exists.",
        phone: "Supplier already exists.",
        email: "Supplier already exists.",
      };
      setSupplierFieldErrors(errors);
      setSupplierError("Supplier already exists.");
      return;
    }

    setSavingSupplier(true);
    setSupplierError(null);
    try {
      const payload: SupplierInput = {
        supplierName: parsed.data.supplierName,
        contactPerson: parsed.data.contactPerson,
        phone: parsed.data.phone,
        email: parsed.data.email,
        gstNumber: parsed.data.gstNumber ?? null,
        address: parsed.data.address,
        notes: parsed.data.notes,
        active: parsed.data.active,
      };
      if (editingSupplierId) {
        await updateSupplier(auth.accessToken, auth.tenantId, editingSupplierId, payload);
      } else {
        await createSupplier(auth.accessToken, auth.tenantId, payload);
      }
      await loadSuppliers();
      resetSupplierForm();
      setSupplierSuccess("Supplier saved successfully.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save supplier";
      if (message.toLowerCase().includes("supplier already exists")) {
        setSupplierFieldErrors({
          supplierName: "Supplier already exists.",
          gstNumber: "Supplier already exists.",
          phone: "Supplier already exists.",
          email: "Supplier already exists.",
        });
        setSupplierError("Supplier already exists.");
      } else {
        setSupplierError(message);
      }
    } finally {
      setSavingSupplier(false);
    }
  }, [auth.accessToken, auth.tenantId, editingSupplierId, loadSuppliers, resetSupplierForm, supplierForm, suppliers]);

  const clearSupplierFilters = () => {
    setSupplierSearch("");
    setSupplierStatusFilter("ALL");
  };

  const resetPurchaseOrderForm = React.useCallback(() => {
    setPoForm(emptyPoForm);
    setPoLines([{ ...emptyPoLine }]);
    setPoFieldErrors({});
    setEditingPoId(null);
    setCancelReason("");
    setCancelError(null);
  }, []);

  const openPurchaseOrderForEdit = React.useCallback((po: PurchaseOrderRow) => {
    setEditingPoId(po.id);
    setPoForm({
      supplierId: po.supplierId,
      poNumber: po.poNumber,
      orderDate: po.orderDate,
      expectedDelivery: po.expectedDelivery,
      notes: "",
    });
    setPoLines(po.items.length ? po.items.map((item) => normalizePurchaseOrderLine(item)) : [{ ...emptyPoLine }]);
    setPoFieldErrors({});
    setCancelError(null);
  }, []);

  const validatePurchaseOrderForm = React.useCallback(() => {
    const parsed = purchaseOrderFormSchema.safeParse({
      ...poForm,
      items: poLines,
    });
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setPoFieldErrors(errors);
      return null;
    }
    return parsed.data;
  }, [poForm, poLines]);

  const upsertPurchaseOrder = React.useCallback((status: PurchaseOrderRow["status"]) => {
    const validated = validatePurchaseOrderForm();
    if (!validated) {
      return null;
    }
    const supplier = suppliers.find((row) => row.id === validated.supplierId);
    if (!supplier) {
      setPoFieldErrors({ supplierId: "Supplier is required." });
      return null;
    }
    if (status === "Generated" && !editingPoId) {
      setPoFieldErrors({ _form: "Save a valid draft before generating a PO." });
      return null;
    }
    const totals = computePurchaseOrderTotals(poLines);
    const nextRow: PurchaseOrderRow = {
      id: editingPoId || `po-${Date.now()}`,
      poNumber: validated.poNumber || `PO-${purchaseOrders.length + 1}`,
      supplierId: validated.supplierId,
      supplierName: supplier.supplierName,
      orderDate: validated.orderDate,
      expectedDelivery: validated.expectedDelivery,
      status,
      cancelReason: status === "Cancelled" ? cancelReason.trim() || null : null,
      items: poFormToLines(poLines),
      totalQty: totals.totalQty,
      subtotal: totals.subtotal,
      totalGst: totals.totalGst,
      totalValue: totals.totalValue,
      updatedAt: new Date().toISOString(),
    };
    setPurchaseOrders((current) => {
      const next = current.filter((row) => row.id !== nextRow.id);
      return [nextRow, ...next];
    });
    setEditingPoId(nextRow.id);
    setPoFieldErrors({});
    return nextRow;
  }, [cancelReason, editingPoId, poForm, poLines, purchaseOrders.length, suppliers, validatePurchaseOrderForm]);

  const savePurchaseOrderDraft = React.useCallback(() => {
    const saved = upsertPurchaseOrder("Draft");
    if (saved) {
      setPoFieldErrors({});
      setCancelError(null);
      setSupplierSuccess("Purchase order draft saved.");
    }
  }, [upsertPurchaseOrder]);

  const generatePurchaseOrder = React.useCallback(() => {
    const validated = validatePurchaseOrderForm();
    if (!validated) return;
    const current = editingPoId ? purchaseOrders.find((row) => row.id === editingPoId) : null;
    if (!current || current.status !== "Draft") {
      setPoFieldErrors({ _form: "Save a valid draft before generating a PO." });
      return;
    }
    const saved = upsertPurchaseOrder("Generated");
    if (saved) {
      setSupplierSuccess("Purchase order generated.");
    }
  }, [editingPoId, purchaseOrders, upsertPurchaseOrder, validatePurchaseOrderForm]);

  const openCancelPurchaseOrder = React.useCallback(() => {
    if (!editingPoId) {
      setPoFieldErrors({ _form: "Save a valid draft before cancelling a PO." });
      return;
    }
    setCancelReason("");
    setCancelError(null);
    setCancelDialogOpen(true);
  }, [editingPoId]);

  const confirmCancelPurchaseOrder = React.useCallback(() => {
    if (!editingPoId) {
      setCancelError("Save a valid draft before cancelling a PO.");
      return;
    }
    if (!cancelReason.trim()) {
      setCancelError("Cancel reason is required.");
      return;
    }
    const validated = validatePurchaseOrderForm();
    if (!validated) return;
    const saved = upsertPurchaseOrder("Cancelled");
    if (saved) {
      setCancelDialogOpen(false);
      setCancelReason("");
      setSupplierSuccess("Purchase order cancelled.");
    }
  }, [cancelReason, editingPoId, upsertPurchaseOrder, validatePurchaseOrderForm]);

  const currentSupplierCount = suppliers.length;
  const currentPurchaseOrderCount = purchaseOrders.length;
  const currentInvoiceCount = invoices.length;
  const currentGrnCount = grns.length;

  const addPoLine = () => setPoLines((current) => [...current, { ...emptyPoLine }]);
  const removePoLine = (index: number) => setPoLines((current) => current.length <= 1 ? current : current.filter((_, rowIndex) => rowIndex !== index));
  const updatePoLine = React.useCallback((index: number, patch: Partial<PurchaseOrderLineState>) => {
    setPoLines((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row));
  }, []);

  const saveInvoice = () => {
    setInvoices((current) => [{
      id: `inv-${Date.now()}`,
      invoiceNumber: invoiceForm.invoiceNumber || `INV-${current.length + 1}`,
      supplierId: invoiceForm.supplierId || suppliers[0]?.id || "",
      purchaseOrderId: invoiceForm.purchaseOrderId || purchaseOrders[0]?.id || "",
      amount: Number(invoiceForm.amount || 0),
      status: "Pending",
    }, ...current]);
    setInvoiceForm(emptyInvoiceForm);
  };

  const saveGrn = () => {
    setGrns((current) => [{
      id: `grn-${Date.now()}`,
      receiptNumber: grnForm.receiptNumber || `GRN-${current.length + 1}`,
      supplierId: grnForm.supplierId || suppliers[0]?.id || "",
      purchaseOrderId: grnForm.purchaseOrderId || purchaseOrders[0]?.id || "",
      receivedQty: Number(grnForm.receivedQty || 0),
      status: "Posted",
    }, ...current]);
    setGrnForm(emptyGrnForm);
  };

  if (!auth.tenantId) {
    return <Alert severity="info">Select a tenant to access procurement.</Alert>;
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Procure</Typography>
          <Typography variant="body2" color="text.secondary">Supplier-first procurement workspace with URL-synced tabs.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigate("/inventory")}>Inventory</Button>
          <Button variant="outlined" onClick={() => navigate("/pharmacy/pos")}>POS Sale</Button>
        </Stack>
      </Box>

      <WorkflowGuide
        title="Workflow guidance"
        subtitle="Supplier → Purchase Order → Generate / Send PO → Invoice → Goods Receipt → Stock Added"
        steps={[
          { label: "Supplier", tone: workspace === "suppliers" ? "primary" : "default" },
          { label: "Purchase Order", tone: workspace === "purchase-orders" ? "primary" : "default" },
          { label: "Invoice", tone: workspace === "supplier-invoices" ? "primary" : "default" },
          { label: "Goods Receipt", tone: workspace === "goods-receipt" ? "primary" : "default" },
        ]}
      />

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Suppliers" value={currentSupplierCount} helper="Master records" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Purchase Orders" value={currentPurchaseOrderCount} helper="PO workflow" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Supplier Invoices" value={currentInvoiceCount} helper="Matching queue" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Goods Receipt" value={currentGrnCount} helper="Receiving queue" />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent sx={compactCardContentSx}>
          <Box role="tablist" aria-label="Procure workspaces" sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
            {WORKSPACES.map((item) => {
              const active = workspace === item.value;
              return (
                <ButtonBase
                  key={item.value}
                  role="tab"
                  aria-selected={active}
                  tabIndex={0}
                  onClick={() => updateWorkspace(item.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      updateWorkspace(item.value);
                    }
                  }}
                  sx={{
                    px: 1.5,
                    py: 1,
                    borderRadius: 999,
                    border: "1px solid",
                    borderColor: active ? "primary.main" : "divider",
                    bgcolor: active ? "action.selected" : "background.paper",
                    color: active ? "primary.main" : "text.secondary",
                    fontWeight: active ? 800 : 600,
                    cursor: "pointer",
                    "&:focus-visible": { outline: "2px solid", outlineColor: "primary.main", outlineOffset: 2 },
                  }}
                >
                  {item.label}
                </ButtonBase>
              );
            })}
          </Box>
        </CardContent>
      </Card>

      {supplierSuccess ? <Alert severity="success" onClose={() => setSupplierSuccess(null)}>{supplierSuccess}</Alert> : null}
      {supplierError ? <Alert severity="error" onClose={() => setSupplierError(null)}>{supplierError}</Alert> : null}

      {workspace === "suppliers" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4 }}>
            <CompactFilterCard
              title={editingSupplierId ? "Edit supplier" : "Add supplier"}
              subtitle="Supplier master."
              actions={(
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={resetSupplierForm}>Clear</Button>
                  <Button size="small" variant="contained" onClick={() => void handleSaveSupplier()} disabled={savingSupplier || loadingSuppliers}>
                    {savingSupplier ? "Saving..." : "Save supplier"}
                  </Button>
                </Stack>
              )}
            >
              <Stack spacing={1}>
                <TextField
                  size="small"
                  label="Supplier name *"
                  value={supplierForm.supplierName}
                  onChange={(e) => setSupplierForm((current) => ({ ...current, supplierName: e.target.value }))}
                  error={Boolean(supplierFieldErrors.supplierName)}
                  helperText={supplierFieldErrors.supplierName}
                />
                <TextField
                  size="small"
                  label="GSTIN"
                  value={supplierForm.gstNumber}
                  onChange={(e) => setSupplierForm((current) => ({ ...current, gstNumber: e.target.value }))}
                  error={Boolean(supplierFieldErrors.gstNumber)}
                  helperText={supplierFieldErrors.gstNumber}
                />
                <TextField
                  size="small"
                  label="Contact person"
                  value={supplierForm.contactPerson}
                  onChange={(e) => setSupplierForm((current) => ({ ...current, contactPerson: e.target.value }))}
                  error={Boolean(supplierFieldErrors.contactPerson)}
                  helperText={supplierFieldErrors.contactPerson}
                />
                <TextField
                  size="small"
                  label="Phone *"
                  value={supplierForm.phone}
                  onChange={(e) => setSupplierForm((current) => ({ ...current, phone: e.target.value }))}
                  error={Boolean(supplierFieldErrors.phone)}
                  helperText={supplierFieldErrors.phone || "Enter a valid Indian mobile number."}
                  inputProps={{ inputMode: "tel" }}
                />
                <TextField
                  size="small"
                  label="Email"
                  value={supplierForm.email}
                  onChange={(e) => setSupplierForm((current) => ({ ...current, email: e.target.value }))}
                  error={Boolean(supplierFieldErrors.email)}
                  helperText={supplierFieldErrors.email}
                />
                <TextField
                  size="small"
                  label="Address"
                  multiline
                  minRows={3}
                  value={supplierForm.address}
                  onChange={(e) => setSupplierForm((current) => ({ ...current, address: e.target.value }))}
                  error={Boolean(supplierFieldErrors.address)}
                  helperText={supplierFieldErrors.address}
                />
                <TextField
                  size="small"
                  label="Notes"
                  multiline
                  minRows={3}
                  value={supplierForm.notes}
                  onChange={(e) => setSupplierForm((current) => ({ ...current, notes: e.target.value }))}
                  error={Boolean(supplierFieldErrors.notes)}
                  helperText={supplierFieldErrors.notes}
                />
                <FormControl size="small">
                  <InputLabel>Status</InputLabel>
                  <Select
                    label="Status"
                    value={supplierForm.active ? "Active" : "Inactive"}
                    onChange={(e) => setSupplierForm((current) => ({ ...current, active: e.target.value === "Active" }))}
                  >
                    <MenuItem value="Active">Active</MenuItem>
                    <MenuItem value="Inactive">Inactive</MenuItem>
                  </Select>
                </FormControl>
              </Stack>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 8 }}>
            <CompactFilterCard
              title="Supplier list"
              subtitle="Search, filter, view, edit, and activate/deactivate suppliers."
              actions={(
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <TextField size="small" label="Search suppliers" value={supplierSearch} onChange={(e) => setSupplierSearch(e.target.value)} />
                  <FormControl size="small" sx={{ minWidth: 140 }}>
                    <InputLabel>Status</InputLabel>
                    <Select label="Status" value={supplierStatusFilter} onChange={(e) => setSupplierStatusFilter(e.target.value as SupplierStatus)}>
                      <MenuItem value="ALL">All</MenuItem>
                      <MenuItem value="Active">Active</MenuItem>
                      <MenuItem value="Inactive">Inactive</MenuItem>
                    </Select>
                  </FormControl>
                  <Button size="small" variant="outlined" onClick={clearSupplierFilters}>Clear filters</Button>
                </Stack>
              )}
            >
              {loadingSuppliers ? (
                <Box sx={{ display: "grid", placeItems: "center", minHeight: 160 }}>
                  <Typography variant="body2" color="text.secondary">Loading suppliers...</Typography>
                </Box>
              ) : visibleSuppliers.length ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Supplier</TableCell>
                      <TableCell>Contact</TableCell>
                      <TableCell>Phone</TableCell>
                      <TableCell>Email</TableCell>
                      <TableCell>GSTIN</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {visibleSuppliers.map((supplier) => (
                      <TableRow key={supplier.id}>
                        <TableCell>{supplier.supplierName}</TableCell>
                        <TableCell>{supplier.contactPerson || "-"}</TableCell>
                        <TableCell>{supplier.phone || "-"}</TableCell>
                        <TableCell>{supplier.email || "-"}</TableCell>
                        <TableCell>{supplier.gstNumber || "-"}</TableCell>
                        <TableCell>{supplier.active ? "Active" : "Inactive"}</TableCell>
                        <TableCell align="right">
                          <Button size="small" onClick={() => handleViewSupplier(supplier)}>View</Button>
                          <Button size="small" onClick={() => handleEditSupplier(supplier)}>Edit</Button>
                          <Button size="small" onClick={() => void handleToggleSupplierStatus(supplier)}>
                            {supplier.active ? "Deactivate" : "Activate"}
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <CompactEmptyState
                  title={suppliers.length === 0 ? "No suppliers yet" : "No suppliers match the current filters."}
                  subtitle={suppliers.length === 0 ? "Create the first supplier to continue." : "Clear or adjust filters to show matching suppliers."}
                  action={suppliers.length === 0 ? <Button size="small" variant="contained" onClick={resetSupplierForm}>Add Supplier</Button> : undefined}
                />
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {workspace === "purchase-orders" ? (
        <Stack spacing={2}>
          <Card variant="outlined">
            <CardContent sx={compactCardContentSx}>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
                <Button size="small" variant="contained" onClick={() => { resetPurchaseOrderForm(); setPoDrawerOpen(false); }}>
                  + New Purchase Order
                </Button>
                <Button size="small" variant="outlined" onClick={() => setPoDrawerOpen(true)}>
                  Open Purchase Orders
                </Button>
                <TextField
                  size="small"
                  label="Search"
                  value={poSearch}
                  onChange={(e) => setPoSearch(e.target.value)}
                  sx={{ minWidth: 220, flex: 1 }}
                />
                <FormControl size="small" sx={{ minWidth: 150 }}>
                  <InputLabel>Status</InputLabel>
                  <Select
                    label="Status"
                    value={poStatusFilter}
                    onChange={(e) => setPoStatusFilter(e.target.value as PurchaseOrderStatusFilter)}
                  >
                    <MenuItem value="ALL">All</MenuItem>
                    <MenuItem value="Draft">Draft</MenuItem>
                    <MenuItem value="Generated">Generated</MenuItem>
                    <MenuItem value="Cancelled">Cancelled</MenuItem>
                  </Select>
                </FormControl>
                <FormControl size="small" sx={{ minWidth: 180 }}>
                  <InputLabel>Supplier</InputLabel>
                  <Select
                    label="Supplier"
                    value={poSupplierFilter}
                    onChange={(e) => setPoSupplierFilter(e.target.value)}
                  >
                    <MenuItem value="ALL">All suppliers</MenuItem>
                    {suppliers.map((supplier) => (
                      <MenuItem key={supplier.id} value={supplier.id}>
                        {supplier.supplierName}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Stack>
            </CardContent>
          </Card>

          <CompactFilterCard
            title={(
              <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                  {editingPoId ? "Purchase order draft" : "Create purchase order draft"}
                </Typography>
                <Chip size="small" label={purchaseOrderStatusLabel} color={purchaseOrderStatusColor} variant="outlined" />
              </Stack>
            )}
            subtitle="Save drafts first, then generate or cancel from the saved draft."
          >
            <Stack spacing={1.5}>
              {poFieldErrors._form ? <Alert severity="error">{poFieldErrors._form}</Alert> : null}
              {purchaseOrderTimestamp ? (
                <Typography variant="caption" color="text.secondary">
                  Generated on {new Date(purchaseOrderTimestamp).toLocaleString()}
                </Typography>
              ) : null}
              <Grid container spacing={1}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <FormControl size="small" error={Boolean(poFieldErrors.supplierId)} fullWidth>
                    <InputLabel>Supplier</InputLabel>
                    <Select label="Supplier" value={poForm.supplierId} onChange={(e) => setPoForm((current) => ({ ...current, supplierId: e.target.value }))}>
                      {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                    </Select>
                    {poFieldErrors.supplierId ? <Typography variant="caption" color="error">{poFieldErrors.supplierId}</Typography> : null}
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    size="small"
                    label="PO number"
                    value={poForm.poNumber}
                    placeholder={purchaseOrderStatus === "Generated" ? "" : "Auto-generated when Purchase Order is Generated"}
                    InputProps={{ readOnly: true }}
                    helperText={poFieldErrors.poNumber || (purchaseOrderStatus === "Generated" ? "Immutable generated PO number." : "Auto-generated when Purchase Order is Generated.")}
                    error={Boolean(poFieldErrors.poNumber)}
                    fullWidth
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="PO date *" type="date" InputLabelProps={{ shrink: true }} value={poForm.orderDate} onChange={(e) => setPoForm((current) => ({ ...current, orderDate: e.target.value }))} helperText={poFieldErrors.orderDate} error={Boolean(poFieldErrors.orderDate)} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Expected delivery date *" type="date" InputLabelProps={{ shrink: true }} value={poForm.expectedDelivery} onChange={(e) => setPoForm((current) => ({ ...current, expectedDelivery: e.target.value }))} helperText={poFieldErrors.expectedDelivery} error={Boolean(poFieldErrors.expectedDelivery)} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Reference / Notes" value={poForm.notes} onChange={(e) => setPoForm((current) => ({ ...current, notes: e.target.value }))} fullWidth />
                </Grid>
              </Grid>

              <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Items</Typography>
                <Button size="small" variant="outlined" onClick={addPoLine}>Add Row</Button>
              </Box>
              {poFieldErrors.items ? <Alert severity="error">{poFieldErrors.items}</Alert> : null}

              <TableContainer sx={{ maxHeight: 420, overflowX: "auto", border: "1px solid", borderColor: "divider", borderRadius: 1 }}>
                <Table size="small" stickyHeader sx={{ tableLayout: "fixed", minWidth: 980 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ width: "35%", py: 0.75 }}>Medicine</TableCell>
                      <TableCell sx={{ width: "8%", py: 0.75, textAlign: "right" }}>Quantity</TableCell>
                      <TableCell sx={{ width: "8%", py: 0.75 }}>Unit</TableCell>
                      <TableCell sx={{ width: "12%", py: 0.75, textAlign: "right" }}>Unit price</TableCell>
                      <TableCell sx={{ width: "8%", py: 0.75, textAlign: "right" }}>GST %</TableCell>
                      <TableCell sx={{ width: "10%", py: 0.75, textAlign: "right" }}>Discount</TableCell>
                      <TableCell sx={{ width: "12%", py: 0.75, textAlign: "right" }}>Line total</TableCell>
                      <TableCell sx={{ width: "7%", py: 0.75, textAlign: "right" }}>Action</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {poLines.map((line, index) => {
                      const medicineOptions = medicineCatalog.filter((medicine) => medicineMatchesQuery(medicine, line.medicineName));
                      const selectedMedicine = medicineCatalog.find((medicine) => medicine.id === line.medicineId) ?? null;
                      const lineTotal = computePurchaseOrderLineTotal(line);
                      const showCreateMedicine = Boolean(line.medicineName.trim()) && medicineOptions.length === 0;
                      return (
                        <TableRow key={index} hover>
                          <TableCell sx={{ py: 0.5 }}>
                            <Stack spacing={0.25}>
                              <Autocomplete
                                options={medicineOptions}
                                value={selectedMedicine}
                                inputValue={line.medicineName}
                                loading={loadingMedicines}
                                onChange={(_, value) => {
                                  updatePoLine(index, {
                                    medicineId: value?.id || "",
                                    medicineName: value?.medicineName || "",
                                  });
                                }}
                                onInputChange={(_, value, reason) => {
                                  if (reason === "clear") {
                                    updatePoLine(index, { medicineId: "", medicineName: "" });
                                    return;
                                  }
                                  if (reason === "input") {
                                    updatePoLine(index, { medicineId: "", medicineName: value });
                                    return;
                                  }
                                  updatePoLine(index, { medicineName: value });
                                }}
                                getOptionLabel={(option) => option.medicineName}
                                isOptionEqualToValue={(option, value) => option.id === value.id}
                                renderInput={(params) => (
                                  <TextField
                                    {...params}
                                    size="small"
                                    label="Medicine *"
                                    placeholder="Type medicine name to search"
                                    error={Boolean(poFieldErrors[`items.${index}.medicineId`])}
                                    helperText={poFieldErrors[`items.${index}.medicineId`] || "Select from Medicine Master."}
                                  />
                                )}
                              />
                              {showCreateMedicine ? (
                                <Button size="small" variant="text" sx={{ alignSelf: "flex-start" }} disabled>
                                  + Create Medicine
                                </Button>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell sx={{ py: 0.5 }}>
                            <TextField
                              size="small"
                              type="number"
                              value={line.quantity}
                              onChange={(e) => updatePoLine(index, { quantity: e.target.value })}
                              error={Boolean(poFieldErrors[`items.${index}.quantity`])}
                              helperText={poFieldErrors[`items.${index}.quantity`]}
                              placeholder="Qty"
                              inputProps={{ min: 1, step: 1 }}
                              sx={poQuantityFieldSx}
                            />
                          </TableCell>
                          <TableCell sx={{ py: 0.5 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700, textAlign: "right" }}>
                              {selectedMedicine?.unit || "-"}
                            </Typography>
                          </TableCell>
                          <TableCell sx={{ py: 0.5 }}>
                            <TextField
                              size="small"
                              type="number"
                              value={line.unitPrice}
                              onChange={(e) => updatePoLine(index, { unitPrice: e.target.value })}
                              error={Boolean(poFieldErrors[`items.${index}.unitPrice`])}
                              helperText={poFieldErrors[`items.${index}.unitPrice`]}
                              placeholder="0.00"
                              inputProps={{ min: 0.01, step: "0.01" }}
                              sx={{ width: 120, ...poMoneyFieldSx }}
                            />
                          </TableCell>
                          <TableCell sx={{ py: 0.5 }}>
                            <TextField
                              size="small"
                              type="number"
                              value={line.gst}
                              onChange={(e) => updatePoLine(index, { gst: e.target.value })}
                              error={Boolean(poFieldErrors[`items.${index}.gst`])}
                              helperText={poFieldErrors[`items.${index}.gst`]}
                              placeholder="GST %"
                              inputProps={{ min: 0, max: 28, step: "0.01" }}
                              sx={{ width: 80, ...poMoneyFieldSx }}
                            />
                          </TableCell>
                            <TableCell sx={{ py: 0.5 }}>
                              <TextField
                                size="small"
                                type="number"
                                value={line.discount}
                                onChange={(e) => updatePoLine(index, { discount: e.target.value })}
                                error={Boolean(poFieldErrors[`items.${index}.discount`])}
                                helperText={poFieldErrors[`items.${index}.discount`]}
                                placeholder="Discount"
                                inputProps={{ min: 0, step: "0.01" }}
                                sx={{ width: 120, ...poMoneyFieldSx }}
                              />
                          </TableCell>
                          <TableCell align="right" sx={{ py: 0.5, whiteSpace: "nowrap" }}>
                            {lineTotal == null ? "-" : `INR ${lineTotal.toFixed(2)}`}
                          </TableCell>
                          <TableCell align="right" sx={{ py: 0.5 }}>
                            <Button size="small" color="inherit" onClick={() => removePoLine(index)} disabled={poLines.length === 1}>Remove</Button>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>

              <Card variant="outlined" sx={{ position: "sticky", bottom: 72, zIndex: 1, bgcolor: "background.paper" }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip size="small" label={`Items: ${poLines.length}`} />
                    <Chip size="small" label={`Qty: ${purchaseOrderTotals.totalQty}`} />
                    <Chip size="small" label={`Subtotal: INR ${purchaseOrderTotals.subtotal.toFixed(2)}`} />
                    <Chip size="small" label={`GST: INR ${purchaseOrderTotals.totalGst.toFixed(2)}`} />
                    <Chip size="small" label={`Discount: INR ${purchaseOrderTotals.totalDiscount.toFixed(2)}`} />
                    <Chip size="small" color="primary" label={`Grand total: INR ${purchaseOrderTotals.totalValue.toFixed(2)}`} />
                  </Stack>
                </CardContent>
              </Card>

              <Card variant="outlined" sx={{ position: "sticky", bottom: 0, zIndex: 1, bgcolor: "background.paper" }}>
                <CardContent sx={{ p: 1.5 }}>
                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center" justifyContent="space-between">
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      <Button size="small" variant="contained" onClick={savePurchaseOrderDraft}>Save Draft</Button>
                      <Button size="small" variant="outlined" onClick={generatePurchaseOrder}>Generate PO</Button>
                      <Button size="small" variant="outlined" color="inherit" onClick={openCancelPurchaseOrder}>Cancel</Button>
                    </Stack>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      <Button size="small" variant="outlined" disabled>Print</Button>
                      <Button size="small" variant="outlined" disabled>Email</Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </CompactFilterCard>
        </Stack>
      ) : null}

      <Drawer
        anchor="right"
        open={poDrawerOpen}
        onClose={() => setPoDrawerOpen(false)}
        PaperProps={{
          sx: {
            width: { xs: "100%", sm: 460 },
            p: 2,
            boxSizing: "border-box",
          },
        }}
      >
        <Stack spacing={2} sx={{ height: "100%" }}>
          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Purchase Orders</Typography>
              <Typography variant="body2" color="text.secondary">Search and open an existing PO.</Typography>
            </Box>
            <Button size="small" onClick={() => setPoDrawerOpen(false)}>Close</Button>
          </Stack>

          <Stack spacing={1}>
            <TextField size="small" label="Search" value={poSearch} onChange={(e) => setPoSearch(e.target.value)} />
            <FormControl size="small" fullWidth>
              <InputLabel>Status</InputLabel>
              <Select label="Status" value={poStatusFilter} onChange={(e) => setPoStatusFilter(e.target.value as PurchaseOrderStatusFilter)}>
                <MenuItem value="ALL">All</MenuItem>
                <MenuItem value="Draft">Draft</MenuItem>
                <MenuItem value="Generated">Generated</MenuItem>
                <MenuItem value="Cancelled">Cancelled</MenuItem>
              </Select>
            </FormControl>
            <FormControl size="small" fullWidth>
              <InputLabel>Supplier</InputLabel>
              <Select label="Supplier" value={poSupplierFilter} onChange={(e) => setPoSupplierFilter(e.target.value)}>
                <MenuItem value="ALL">All suppliers</MenuItem>
                {suppliers.map((supplier) => (
                  <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Stack>

          <Divider />

          <Box sx={{ overflowY: "auto", flex: 1, pr: 0.5 }}>
            {filteredPurchaseOrders.length ? (
              <Stack spacing={1}>
                {filteredPurchaseOrders.map((po) => (
                  <ButtonBase
                    key={po.id}
                    onClick={() => {
                      openPurchaseOrderForEdit(po);
                      setPoDrawerOpen(false);
                    }}
                    sx={{
                      width: "100%",
                      textAlign: "left",
                      display: "block",
                      border: "1px solid",
                      borderColor: po.id === editingPoId ? "primary.main" : "divider",
                      borderRadius: 2,
                      p: 1.25,
                      bgcolor: po.id === editingPoId ? "action.selected" : "background.paper",
                    }}
                  >
                    <Stack spacing={0.5}>
                      <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{po.poNumber}</Typography>
                        <Chip
                          size="small"
                          label={po.status === "Generated" ? "Generated" : po.status === "Cancelled" ? "Cancelled" : "Draft"}
                          color={po.status === "Generated" ? "success" : po.status === "Cancelled" ? "default" : "warning"}
                        />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">{po.supplierName}</Typography>
                      <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                        <Typography variant="caption" color="text.secondary">{po.orderDate}</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>INR {po.totalValue.toFixed(2)}</Typography>
                      </Stack>
                    </Stack>
                  </ButtonBase>
                ))}
              </Stack>
            ) : (
              <CompactEmptyState
                title="No purchase orders"
                subtitle={purchaseOrders.length === 0 ? "Create the first PO to continue." : "Adjust filters to see matching POs."}
              />
            )}
          </Box>
        </Stack>
      </Drawer>

      <Dialog open={cancelDialogOpen} onClose={() => setCancelDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Cancel purchase order</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">Canceling will mark the current draft as cancelled.</Typography>
            <TextField
              size="small"
              label="Cancel reason *"
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              error={Boolean(cancelError)}
              helperText={cancelError || "Reason is required."}
              multiline
              minRows={3}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCancelDialogOpen(false)}>Close</Button>
          <Button variant="contained" color="error" onClick={confirmCancelPurchaseOrder}>Cancel PO</Button>
        </DialogActions>
      </Dialog>

      {workspace === "supplier-invoices" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <CompactFilterCard title="Supplier invoice" subtitle="Match an invoice to an existing PO." actions={<Button size="small" variant="contained" onClick={saveInvoice}>Save invoice</Button>}>
              <Stack spacing={1}>
                <FormControl size="small">
                  <InputLabel>Purchase order</InputLabel>
                  <Select label="Purchase order" value={invoiceForm.purchaseOrderId} onChange={(e) => setInvoiceForm((current) => ({ ...current, purchaseOrderId: e.target.value }))}>
                    {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField size="small" label="Supplier invoice number" value={invoiceForm.invoiceNumber} onChange={(e) => setInvoiceForm((current) => ({ ...current, invoiceNumber: e.target.value }))} />
                <TextField size="small" label="Invoice date" type="date" InputLabelProps={{ shrink: true }} value={invoiceForm.invoiceDate} onChange={(e) => setInvoiceForm((current) => ({ ...current, invoiceDate: e.target.value }))} />
                <TextField size="small" label="Invoice amount" value={invoiceForm.amount} onChange={(e) => setInvoiceForm((current) => ({ ...current, amount: e.target.value }))} />
              </Stack>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            <CompactFilterCard title="Invoice list" subtitle="Matched and pending invoices.">
              {invoices.length ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Invoice</TableCell>
                      <TableCell>Purchase order</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Amount</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {invoices.map((invoice) => (
                      <TableRow key={invoice.id}>
                        <TableCell>{invoice.invoiceNumber}</TableCell>
                        <TableCell>{poById.get(invoice.purchaseOrderId)?.poNumber || "-"}</TableCell>
                        <TableCell>{invoice.status}</TableCell>
                        <TableCell align="right">{invoice.amount}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <CompactEmptyState title="No supplier invoices" subtitle="Create an invoice to match to a PO." />
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {workspace === "goods-receipt" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <CompactFilterCard title="Goods receipt" subtitle="Receive stock against an existing PO." actions={<Button size="small" variant="contained" onClick={saveGrn}>Post GRN</Button>}>
              <Stack spacing={1}>
                <FormControl size="small">
                  <InputLabel>Purchase order</InputLabel>
                  <Select label="Purchase order" value={grnForm.purchaseOrderId} onChange={(e) => setGrnForm((current) => ({ ...current, purchaseOrderId: e.target.value }))}>
                    {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField size="small" label="Receipt number" value={grnForm.receiptNumber} onChange={(e) => setGrnForm((current) => ({ ...current, receiptNumber: e.target.value }))} />
                <TextField size="small" label="Received at" type="date" InputLabelProps={{ shrink: true }} value={grnForm.receivedAt} onChange={(e) => setGrnForm((current) => ({ ...current, receivedAt: e.target.value }))} />
                <TextField size="small" label="Received quantity" value={grnForm.receivedQty} onChange={(e) => setGrnForm((current) => ({ ...current, receivedQty: e.target.value }))} />
                <TextField size="small" label="Batch" value={grnForm.batch} onChange={(e) => setGrnForm((current) => ({ ...current, batch: e.target.value }))} />
                <TextField size="small" label="Expiry" type="date" InputLabelProps={{ shrink: true }} value={grnForm.expiry} onChange={(e) => setGrnForm((current) => ({ ...current, expiry: e.target.value }))} />
              </Stack>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            <CompactFilterCard title="GRN list" subtitle="Posted receipts update inventory later in the real workflow.">
              {grns.length ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Receipt</TableCell>
                      <TableCell>Purchase order</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Qty</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {grns.map((grn) => (
                      <TableRow key={grn.id}>
                        <TableCell>{grn.receiptNumber}</TableCell>
                        <TableCell>{poById.get(grn.purchaseOrderId)?.poNumber || "-"}</TableCell>
                        <TableCell>{grn.status}</TableCell>
                        <TableCell align="right">{grn.receivedQty}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <CompactEmptyState title="No goods receipts" subtitle="Post a GRN to continue." />
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  );
}
