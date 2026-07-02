import * as React from "react";
import { z } from "zod";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
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
  Tooltip,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, CompactStatCard, WorkflowGuide, compactCardContentSx } from "../../components/compact/CompactUi";
import {
  approveSupplierInvoiceForPayment,
  cancelSupplierInvoice,
  createSupplierInvoice,
  createPurchaseOrder,
  createGoodsReceipt,
  cancelPurchaseOrder,
  confirmGoodsReceipt,
  createMedicine,
  createSupplier,
  getGoodsReceipts,
  getInventoryLocations,
  getMedicines,
  getPurchaseOrder,
  getPurchaseOrders,
  getSupplierInvoices,
  listSuppliers,
  matchSupplierInvoice,
  updateSupplierInvoice,
  updateSupplier,
  uploadSupplierInvoiceAttachment,
  type GoodsReceipt,
  type GoodsReceiptInput,
  type InventoryLocation,
  type Medicine,
  type ProcurementLineInput,
  type PurchaseOrder,
  type PurchaseOrderInput,
  type SupplierInvoice,
  type SupplierInvoiceInput,
  type SupplierInvoiceStatus,
  type Supplier,
  type SupplierInput,
  type MedicineInput,
  type MedicineType,
} from "../../api/clinicApi";
import { firstZodError, hasDuplicateMedicineMaster, hasDuplicateSupplierName, indianMobileNumber, mapZodErrors, medicineMasterSchema, normalizeIndianMobileInput, optionalGstin, supplierSchema } from "@deepthoughtnet/form-validation-kit";

type Workspace = "suppliers" | "purchase-orders" | "supplier-invoices" | "goods-receipt";
type SupplierStatus = "ALL" | "Active" | "Inactive";
type PurchaseOrderStatusFilter = "ALL" | PurchaseOrderRow["status"];
type PurchaseOrderEditorMode = "view" | "edit";

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
  status: "Draft" | "Generated" | "Sent" | "Partially Received" | "Received" | "Closed" | "Cancelled";
  cancelReason: string | null;
  items: PurchaseOrderLineState[];
  approvalNote: string | null;
  totalQty: number;
  subtotal: number;
  totalGst: number;
  totalValue: number;
  updatedAt: string;
};

type PurchaseOrderLineState = {
  medicineId: string;
  medicineName: string;
  unit: string;
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
  supplierName: string;
  purchaseOrderId: string | null;
  invoiceDate: string;
  invoiceAmount: number;
  gstAmount: number;
  discountAmount: number;
  totalAmount: number;
  status: SupplierInvoiceStatus;
  matchingStatus: string;
  varianceAmount: number;
  varianceReason: string | null;
  varianceSummary: string | null;
  cancelReason: string | null;
  attachmentFileName: string | null;
  attachmentMediaType: string | null;
  attachmentSizeBytes: number | null;
  notes: string | null;
};

type SupplierInvoiceFormState = {
  supplierId: string;
  purchaseOrderId: string;
  invoiceNumber: string;
  invoiceDate: string;
  invoiceAmount: string;
  gstAmount: string;
  discount: string;
  varianceReason: string;
  notes: string;
};

type SupplierInvoiceEditorMode = "create" | "view" | "edit";

type GoodsReceiptRow = {
  id: string;
  receiptNumber: string;
  supplierId: string;
  supplierName: string;
  purchaseOrderId: string;
  supplierInvoiceId: string | null;
  poNumber: string;
  receivedAt: string;
  itemsReceived: number;
  totalReceivedQty: number;
  status: "Pending" | "Posted" | "Reversed";
  locationName: string;
  lines: GoodsReceiptLineState[];
  confirmedAt: string | null;
  createdAt: string;
};

type GoodsReceiptLineState = {
  medicineId: string;
  medicineName: string;
  unit: string;
  orderedQty: number;
  alreadyReceivedQty: number;
  pendingQty: number;
  receiveQty: string;
  batchNumber: string;
  expiryDate: string;
  locationId: string;
  locationName: string;
  remarks: string;
  lineStatus: "Pending" | "Ready" | "Partial" | "Complete";
  unitPrice: number | null;
  gstPercent: number | null;
  sellingPrice: number | null;
};

type GrnFormState = {
  supplierId: string;
  purchaseOrderId: string;
  supplierInvoiceId: string;
  receiptNumber: string;
  receivedAt: string;
  poNumber: string;
  poDate: string;
  lines: GoodsReceiptLineState[];
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

const emptyPoLine: PurchaseOrderLineState = { medicineId: "", medicineName: "", unit: "", quantity: "", unitPrice: "", gst: "", discount: "" };
const emptyPoForm: PurchaseOrderFormState = { supplierId: "", poNumber: "", orderDate: "", expectedDelivery: "", notes: "" };
const emptyInvoiceForm: SupplierInvoiceFormState = {
  supplierId: "",
  purchaseOrderId: "",
  invoiceNumber: "",
  invoiceDate: "",
  invoiceAmount: "",
  gstAmount: "",
  discount: "",
  varianceReason: "",
  notes: "",
};
const emptyGrnForm: GrnFormState = { supplierId: "", purchaseOrderId: "", supplierInvoiceId: "", receiptNumber: "", receivedAt: "", poNumber: "", poDate: "", lines: [] };
const emptyQuickMedicineForm: MedicineInput = {
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

const medicineTypeOptions: Array<{ value: MedicineType; label: string }> = [
  { value: "TABLET", label: "Tablet" },
  { value: "CAPSULE", label: "Capsule" },
  { value: "SYRUP", label: "Syrup" },
  { value: "INJECTION", label: "Injection" },
  { value: "DROP", label: "Drop" },
  { value: "OINTMENT", label: "Ointment" },
  { value: "OTHER", label: "Other" },
];

const SUPPLIER_INVOICE_VARIANCE_REASONS = [
  "supplier rounding difference",
  "freight/packing charge",
  "item price difference",
  "GST correction",
  "manual correction",
] as const;

const SUPPLIER_INVOICE_UPLOAD_MAX_SIZE_BYTES = 10 * 1024 * 1024;

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

const supplierInvoiceFormSchema = z.object({
  supplierId: z.string().trim().min(1, "Supplier is required."),
  purchaseOrderId: z.string().trim().min(1, "Related PO is required."),
  invoiceNumber: z.preprocess(
    (value) => typeof value === "string" ? value.trim() : "",
    z.string()
      .min(1, "Invoice number is required.")
      .max(60, "Invoice number must be 60 characters or fewer.")
      .regex(/^[A-Za-z0-9/_ -]+$/, "Invoice number can include letters, numbers, dashes, slashes, underscores, and spaces."),
  ),
  invoiceDate: dateValue("Invoice date is required."),
  invoiceAmount: moneyValue(999999999, "Invoice amount must be greater than zero.", false),
  gstAmount: optionalMoneyValue(999999999, "GST amount must be zero or greater."),
  discount: optionalMoneyValue(999999999, "Discount must be zero or greater."),
  varianceReason: optionalPoText(250, "Variance reason must be 250 characters or fewer."),
  notes: optionalPoText(500, "Notes must be 500 characters or fewer."),
}).superRefine((value, ctx) => {
  const variance = Math.round(((value.invoiceAmount + value.gstAmount - value.discount) * 100)) / 100;
  if (!Number.isFinite(variance)) {
    return;
  }
  if (!String(value.purchaseOrderId || "").trim()) {
    return;
  }
  if (value.varianceReason == null) {
    return;
  }
});

function numberFromUnknown(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

function stringFromUnknown(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function goodsReceiptLineStatus(pendingQty: number, receiveQty: number) {
  if (pendingQty <= 0) return "Complete" as const;
  if (receiveQty > 0 && receiveQty >= pendingQty) return "Complete" as const;
  if (receiveQty > 0) return "Partial" as const;
  return "Pending" as const;
}

function parseGoodsReceiptItems(
  itemsJson: string,
  medicineById?: Map<string, Medicine>,
  locationsById?: Map<string, InventoryLocation>,
): GoodsReceiptLineState[] {
  try {
    const parsed = JSON.parse(itemsJson || "[]") as Array<Record<string, unknown>>;
    return parsed.map((item) => {
      const medicineId = stringFromUnknown(item.medicineId);
      const locationId = stringFromUnknown(item.locationId);
      const medicine = medicineId ? medicineById?.get(medicineId) ?? null : null;
      const location = locationId ? locationsById?.get(locationId) ?? null : null;
      const quantity = numberFromUnknown(item.quantity ?? item.qty);
      return {
        medicineId,
        medicineName: stringFromUnknown(item.medicineName) || medicine?.medicineName || "",
        unit: stringFromUnknown(item.unit) || medicine?.unit || "",
        orderedQty: 0,
        alreadyReceivedQty: 0,
        pendingQty: 0,
        receiveQty: String(quantity || ""),
        batchNumber: stringFromUnknown(item.batchNumber),
        expiryDate: stringFromUnknown(item.expiryDate),
        locationId,
        locationName: location?.locationName || "",
        remarks: stringFromUnknown(item.remarks),
        lineStatus: quantity > 0 ? "Complete" : "Pending",
        unitPrice: item.unitCost == null ? numberFromUnknown(item.expectedUnitCost) || null : numberFromUnknown(item.unitCost),
        gstPercent: item.taxPercent == null ? null : numberFromUnknown(item.taxPercent),
        sellingPrice: item.sellingPrice == null ? null : numberFromUnknown(item.sellingPrice),
      };
    });
  } catch {
    return [];
  }
}

function parseWorkspace(value: string | null): Workspace {
  if (value === "purchase-orders" || value === "supplier-invoices" || value === "goods-receipt") return value;
  return "suppliers";
}

function isProcurePath(pathname: string) {
  return pathname === "/pharmacy/procure" || pathname === "/pharmacy/procurement";
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
  const medicineSource = source?.medicine as Record<string, unknown> | string | null | undefined;
  const medicineName = typeof source?.medicineName === "string"
    ? source.medicineName
    : typeof source?.displayName === "string"
      ? source.displayName
      : typeof source?.name === "string"
        ? source.name
        : typeof medicineSource === "string"
          ? medicineSource
          : typeof medicineSource === "object" && medicineSource
            ? String((medicineSource as Record<string, unknown>).medicineName || (medicineSource as Record<string, unknown>).displayName || (medicineSource as Record<string, unknown>).name || "")
            : "";
  return {
    medicineId: typeof source?.medicineId === "string"
      ? source.medicineId
      : typeof medicineSource === "object" && medicineSource && typeof (medicineSource as Record<string, unknown>).id === "string"
        ? String((medicineSource as Record<string, unknown>).id)
        : "",
    medicineName,
    unit: typeof source?.unit === "string"
      ? source.unit
      : typeof source?.uom === "string"
        ? source.uom
        : typeof source?.medicineUnit === "string"
          ? source.medicineUnit
          : "",
    quantity: String(typeof source?.quantity === "number" ? source.quantity : typeof source?.qty === "number" ? source.qty : source?.quantity ?? source?.qty ?? ""),
    unitPrice: String(typeof source?.unitPrice === "number" ? source.unitPrice : typeof source?.unitCost === "number" ? source.unitCost : typeof source?.price === "number" ? source.price : source?.unitPrice ?? source?.unitCost ?? source?.price ?? ""),
    gst: String(typeof source?.gst === "number" ? source.gst : typeof source?.gstPercent === "number" ? source.gstPercent : typeof source?.taxPercent === "number" ? source.taxPercent : source?.gst ?? source?.gstPercent ?? source?.taxPercent ?? ""),
    discount: String(typeof source?.discount === "number" ? source.discount : typeof source?.discountAmount === "number" ? source.discountAmount : source?.discount ?? source?.discountAmount ?? ""),
  };
}

function generatePurchaseOrderNumber(existing: Array<{ poNumber: string }>): string {
  const year = new Date().getFullYear();
  const sequence = existing.length + 1;
  return `PO-${year}-${String(sequence).padStart(6, "0")}`;
}

function encodePurchaseOrderApprovalNote(status: PurchaseOrderRow["status"], cancelReason: string) {
  if (status === "Cancelled") {
    return cancelReason.trim() ? `CANCELLED:${cancelReason.trim()}` : "CANCELLED";
  }
  return status.toUpperCase();
}

function parsePurchaseOrderApprovalNote(note: string | null | undefined): {
  status: PurchaseOrderRow["status"];
  cancelReason: string | null;
} {
  const normalized = (note || "").trim().toUpperCase();
  if (normalized.startsWith("CANCELLED")) {
    const reasonIndex = note?.indexOf(":") ?? -1;
    return {
      status: "Cancelled",
      cancelReason: reasonIndex >= 0 ? (note?.slice(reasonIndex + 1).trim() || null) : null,
    };
  }
  if (normalized === "GENERATED") {
    return { status: "Generated", cancelReason: null };
  }
  if (normalized === "SENT") {
    return { status: "Sent", cancelReason: null };
  }
  if (normalized === "PARTIALLY RECEIVED" || normalized === "PARTIALLY_RECEIVED") {
    return { status: "Partially Received", cancelReason: null };
  }
  if (normalized === "RECEIVED") {
    return { status: "Received", cancelReason: null };
  }
  if (normalized === "CLOSED") {
    return { status: "Closed", cancelReason: null };
  }
  return { status: "Draft", cancelReason: null };
}

function purchaseOrderStatusChipProps(status: PurchaseOrderRow["status"]) {
  if (status === "Generated") {
    return { label: "Generated", color: "info" as const };
  }
  if (status === "Sent") {
    return { label: "Sent", color: "secondary" as const };
  }
  if (status === "Partially Received") {
    return { label: "Partially Received", color: "warning" as const };
  }
  if (status === "Received") {
    return { label: "Received", color: "success" as const };
  }
  if (status === "Closed") {
    return { label: "Closed", color: "default" as const };
  }
  if (status === "Cancelled") {
    return { label: "Cancelled", color: "error" as const };
  }
  return { label: "Draft", color: "default" as const };
}

function grnStatusChipProps(status: GoodsReceiptRow["status"]) {
  if (status === "Posted") {
    return { label: "Posted", color: "success" as const };
  }
  if (status === "Reversed") {
    return { label: "Reversed", color: "default" as const };
  }
  return { label: "Pending", color: "default" as const };
}

function escapePrintHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function formatPurchaseOrderDate(value: string) {
  if (!value) return "-";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleDateString();
}

function renderPurchaseOrderPrintDocument(clinicName: string, purchaseOrder: PurchaseOrderRow) {
  const totals = computePurchaseOrderTotals(purchaseOrder.items);
  const rows = purchaseOrder.items.map((item, index) => {
    const lineTotal = computePurchaseOrderLineTotal(item) ?? 0;
    return `
      <tr>
        <td>${index + 1}</td>
        <td>${escapePrintHtml(item.medicineName || "-")}</td>
        <td>${escapePrintHtml(item.unit || "-")}</td>
        <td style="text-align:right">${escapePrintHtml(item.quantity || "0")}</td>
        <td style="text-align:right">${Number(item.unitPrice || 0).toFixed(2)}</td>
        <td style="text-align:right">${Number(item.gst || 0).toFixed(2)}</td>
        <td style="text-align:right">${Number(item.discount || 0).toFixed(2)}</td>
        <td style="text-align:right">${lineTotal.toFixed(2)}</td>
      </tr>
    `;
  }).join("");

  return `<!doctype html>
  <html>
    <head>
      <meta charset="utf-8" />
      <title>${escapePrintHtml(purchaseOrder.poNumber || "Purchase Order")}</title>
      <style>
        body { font-family: Arial, sans-serif; margin: 24px; color: #111827; }
        h1 { margin: 0 0 8px; font-size: 24px; }
        h2 { margin: 0; font-size: 18px; }
        .meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin: 20px 0; }
        .meta-card { border: 1px solid #d1d5db; border-radius: 8px; padding: 12px; }
        .label { font-size: 12px; color: #6b7280; text-transform: uppercase; letter-spacing: 0.04em; }
        .value { font-size: 14px; font-weight: 700; margin-top: 4px; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { border: 1px solid #d1d5db; padding: 8px; font-size: 12px; vertical-align: top; }
        th { background: #f3f4f6; text-align: left; }
        .summary { margin-top: 20px; margin-left: auto; width: 320px; border: 1px solid #d1d5db; border-radius: 8px; padding: 12px; }
        .summary-row { display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; }
        .summary-row.total { font-weight: 800; font-size: 15px; border-top: 1px solid #d1d5db; margin-top: 8px; padding-top: 10px; }
      </style>
    </head>
    <body>
      <h1>${escapePrintHtml(clinicName)}</h1>
      <h2>Purchase Order</h2>
      <div class="meta">
        <div class="meta-card"><div class="label">Supplier</div><div class="value">${escapePrintHtml(purchaseOrder.supplierName || "-")}</div></div>
        <div class="meta-card"><div class="label">PO Number</div><div class="value">${escapePrintHtml(purchaseOrder.poNumber || "-")}</div></div>
        <div class="meta-card"><div class="label">PO Date</div><div class="value">${escapePrintHtml(formatPurchaseOrderDate(purchaseOrder.orderDate))}</div></div>
        <div class="meta-card"><div class="label">Expected Delivery</div><div class="value">${escapePrintHtml(formatPurchaseOrderDate(purchaseOrder.expectedDelivery))}</div></div>
        <div class="meta-card"><div class="label">Status</div><div class="value">${escapePrintHtml(purchaseOrder.status)}</div></div>
      </div>
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>Medicine</th>
            <th>Unit</th>
            <th style="text-align:right">Qty</th>
            <th style="text-align:right">Unit Price</th>
            <th style="text-align:right">GST %</th>
            <th style="text-align:right">Discount</th>
            <th style="text-align:right">Line Total</th>
          </tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
      <div class="summary">
        <div class="summary-row"><span>Subtotal</span><span>INR ${totals.subtotal.toFixed(2)}</span></div>
        <div class="summary-row"><span>GST</span><span>INR ${totals.totalGst.toFixed(2)}</span></div>
        <div class="summary-row"><span>Discount</span><span>INR ${totals.totalDiscount.toFixed(2)}</span></div>
        <div class="summary-row total"><span>Grand Total</span><span>INR ${totals.totalValue.toFixed(2)}</span></div>
      </div>
    </body>
  </html>`;
}

function mapBackendPurchaseOrderLine(item: unknown, medicineById?: Map<string, Medicine>): PurchaseOrderLineState {
  const source = item as Record<string, unknown> | null | undefined;
  const medicineId = typeof source?.medicineId === "string" ? source.medicineId : "";
  const medicineFromCache = medicineId ? medicineById?.get(medicineId) ?? null : null;
  const medicineLabel = typeof source?.medicineName === "string"
    ? source.medicineName
    : medicineFromCache?.medicineName || "";
  const unit = typeof source?.unit === "string"
    ? source.unit
    : typeof source?.expectedUnit === "string"
      ? source.expectedUnit
      : medicineFromCache?.unit || "";
  const quantity = String(typeof source?.quantity === "number" ? source.quantity : typeof source?.qty === "number" ? source.qty : source?.quantity ?? source?.qty ?? "");
  const unitPrice = String(
    typeof source?.unitCost === "number" ? source.unitCost
      : typeof source?.expectedUnitCost === "number" ? source.expectedUnitCost
        : typeof source?.price === "number" ? source.price
          : source?.unitCost ?? source?.expectedUnitCost ?? source?.price ?? "",
  );
  const gst = String(
    typeof source?.gstPercent === "number" ? source.gstPercent
      : typeof source?.taxPercent === "number" ? source.taxPercent
        : typeof source?.gst === "number" ? source.gst
          : source?.gstPercent ?? source?.taxPercent ?? source?.gst ?? "",
  );
  const discount = String(
    typeof source?.discount === "number" ? source.discount
      : typeof source?.discountAmount === "number" ? source.discountAmount
        : source?.discount ?? source?.discountAmount ?? "",
  );
  return {
    medicineId,
    medicineName: medicineLabel,
    unit,
    quantity,
    unitPrice,
    gst,
    discount,
  };
}

function mapBackendPurchaseOrder(record: PurchaseOrder, medicineById?: Map<string, Medicine>): PurchaseOrderRow {
  let items: PurchaseOrderLineState[] = [];
  try {
    const parsed = JSON.parse(record.itemsJson || "[]") as Array<Record<string, unknown>>;
    items = parsed.map((item) => mapBackendPurchaseOrderLine(item, medicineById));
  } catch {
    items = [];
  }
  const statusInfo = parsePurchaseOrderApprovalNote(record.approvalNote);
  const totals = computePurchaseOrderTotals(items);
  return {
    id: record.id,
    poNumber: record.poNumber,
    supplierId: record.supplierId,
    supplierName: record.supplierName || "",
    orderDate: record.orderDate,
    expectedDelivery: record.expectedDeliveryDate || "",
    status: statusInfo.status,
    cancelReason: statusInfo.cancelReason,
    items,
    approvalNote: record.approvalNote,
    totalQty: totals.totalQty,
    subtotal: totals.subtotal,
    totalGst: totals.totalGst,
    totalValue: totals.totalValue,
    updatedAt: record.updatedAt,
  };
}

function mapInvoiceMatchingStatus(status: string | null | undefined) {
  return (status || "").trim().toUpperCase() || "PENDING";
}

function supplierInvoiceStatusLabel(status: SupplierInvoiceStatus) {
  switch (status) {
    case "DRAFT":
      return { label: "Draft", color: "warning" as const };
    case "MATCHED":
      return { label: "Matched", color: "info" as const };
    case "READY_FOR_PAYMENT":
    case "APPROVED_FOR_PAYMENT":
      return { label: "Ready for Payment", color: "success" as const };
    case "PAID":
      return { label: "Paid", color: "success" as const };
    case "CANCELLED":
      return { label: "Cancelled", color: "default" as const };
    default:
      return { label: status, color: "default" as const };
  }
}

function supplierInvoiceMatchLabel(status: string) {
  if (status === "MATCHED") return { label: "Match OK", color: "success" as const };
  if (status === "REVIEW_REQUIRED") return { label: "Variance", color: "warning" as const };
  if (status === "MISSING_PO") return { label: "Missing PO", color: "default" as const };
  return { label: status || "Pending", color: "default" as const };
}

function mapBackendSupplierInvoice(record: SupplierInvoice): SupplierInvoiceRow {
  const totalAmount = typeof record.totalAmount === "number" ? record.totalAmount : 0;
  const gstAmount = typeof record.taxAmount === "number" ? record.taxAmount : 0;
  const invoiceAmount = typeof record.invoiceAmount === "number" ? record.invoiceAmount : totalAmount;
  const discountAmount = typeof record.discountAmount === "number" ? record.discountAmount : 0;
  const varianceAmount = typeof record.varianceAmount === "number" ? record.varianceAmount : 0;
  return {
    id: record.id,
    invoiceNumber: record.invoiceNumber,
    supplierId: record.supplierId,
    supplierName: record.supplierName || "",
    purchaseOrderId: record.purchaseOrderId,
    invoiceDate: record.invoiceDate,
    invoiceAmount,
    gstAmount,
    discountAmount,
    totalAmount,
    status: record.status,
    matchingStatus: mapInvoiceMatchingStatus(record.matchingStatus),
    varianceAmount,
    varianceReason: record.varianceReason,
    varianceSummary: record.varianceSummary,
    cancelReason: record.cancelReason,
    attachmentFileName: record.attachmentFileName,
    attachmentMediaType: record.attachmentMediaType,
    attachmentSizeBytes: record.attachmentSizeBytes,
    notes: record.approvalNote,
  };
}

function mapBackendGoodsReceipt(
  record: GoodsReceipt,
  purchaseOrders: PurchaseOrderRow[],
  medicineById?: Map<string, Medicine>,
  locationsById?: Map<string, InventoryLocation>,
): GoodsReceiptRow {
  const lines = parseGoodsReceiptItems(record.itemsJson, medicineById, locationsById).map((line) => ({
    ...line,
    locationId: line.locationId || record.locationId,
    locationName: line.locationName || record.locationName || "",
  }));
  const po = purchaseOrders.find((candidate) => candidate.id === record.purchaseOrderId) ?? null;
  return {
    id: record.id,
    receiptNumber: record.receiptNumber,
    supplierId: record.supplierId,
    supplierName: record.supplierName || "",
    purchaseOrderId: record.purchaseOrderId || "",
    supplierInvoiceId: record.supplierInvoiceId,
    poNumber: po?.poNumber || "",
    receivedAt: record.receivedAt,
    itemsReceived: lines.filter((line) => numberFromUnknown(line.receiveQty) > 0).length,
    totalReceivedQty: lines.reduce((sum, line) => sum + numberFromUnknown(line.receiveQty), 0),
    status: record.confirmedAt ? "Posted" : "Pending",
    locationName: record.locationName || "",
    lines,
    confirmedAt: record.confirmedAt,
    createdAt: record.createdAt,
  };
}

function mapPurchaseOrderLineToApi(line: PurchaseOrderLineState): ProcurementLineInput {
  const parsed = purchaseOrderLineSchema.parse(line);
  const unitPrice = parsed.unitPrice;
  const taxPercent = parsed.gst;
  return {
    medicineId: parsed.medicineId || null,
    medicineName: parsed.medicineName || null,
    quantity: parsed.quantity,
    expectedUnitCost: unitPrice,
    unitCost: unitPrice,
    taxPercent,
    batchNumber: null,
    expiryDate: null,
    sellingPrice: null,
    unit: line.unit || null,
    locationId: null,
    remarks: null,
  };
}

function logPo(payloadLabel: string, value: unknown) {
  console.log(`[${payloadLabel}]`, value);
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
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
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
  const [loadingPurchaseOrders, setLoadingPurchaseOrders] = React.useState(true);
  const [purchaseOrderError, setPurchaseOrderError] = React.useState<string | null>(null);
  const [purchaseOrderSuccess, setPurchaseOrderSuccess] = React.useState<string | null>(null);
  const [invoices, setInvoices] = React.useState<SupplierInvoiceRow[]>([]);
  const [loadingInvoices, setLoadingInvoices] = React.useState(true);
  const [invoiceError, setInvoiceError] = React.useState<string | null>(null);
  const [invoiceSuccess, setInvoiceSuccess] = React.useState<string | null>(null);
  const [invoiceFieldErrors, setInvoiceFieldErrors] = React.useState<Record<string, string>>({});
  const [savingInvoice, setSavingInvoice] = React.useState(false);
  const [uploadingInvoiceAttachment, setUploadingInvoiceAttachment] = React.useState(false);
  const [selectedInvoiceId, setSelectedInvoiceId] = React.useState<string | null>(null);
  const [invoiceEditorMode, setInvoiceEditorMode] = React.useState<SupplierInvoiceEditorMode>("create");
  const [invoiceStatusFilter, setInvoiceStatusFilter] = React.useState<"ALL" | SupplierInvoiceStatus>("ALL");
  const [invoiceSupplierFilter, setInvoiceSupplierFilter] = React.useState<string>("ALL");
  const [invoicePoFilter, setInvoicePoFilter] = React.useState<string>("ALL");
  const [invoiceDateFromFilter, setInvoiceDateFromFilter] = React.useState("");
  const [invoiceDateToFilter, setInvoiceDateToFilter] = React.useState("");
  const [invoiceVarianceOnly, setInvoiceVarianceOnly] = React.useState(false);
  const [invoiceCancelDialogOpen, setInvoiceCancelDialogOpen] = React.useState(false);
  const [invoiceCancelReason, setInvoiceCancelReason] = React.useState("");
  const [invoiceCancelError, setInvoiceCancelError] = React.useState<string | null>(null);
  const [grns, setGrns] = React.useState<GoodsReceiptRow[]>([]);
  const [loadingGrns, setLoadingGrns] = React.useState(true);
  const [grnError, setGrnError] = React.useState<string | null>(null);
  const [grnSuccess, setGrnSuccess] = React.useState<string | null>(null);
  const [grnFieldErrors, setGrnFieldErrors] = React.useState<Record<string, string>>({});
  const [savingGrn, setSavingGrn] = React.useState(false);
  const [inventoryLocations, setInventoryLocations] = React.useState<InventoryLocation[]>([]);
  const [selectedGrn, setSelectedGrn] = React.useState<GoodsReceiptRow | null>(null);
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
  const [quickMedicineOpen, setQuickMedicineOpen] = React.useState(false);
  const [quickMedicineForm, setQuickMedicineForm] = React.useState<MedicineInput>(emptyQuickMedicineForm);
  const [quickMedicineFieldErrors, setQuickMedicineFieldErrors] = React.useState<Record<string, string>>({});
  const [quickMedicineRowIndex, setQuickMedicineRowIndex] = React.useState<number | null>(null);
  const [savingQuickMedicine, setSavingQuickMedicine] = React.useState(false);
  const [invoiceForm, setInvoiceForm] = React.useState(emptyInvoiceForm);
  const [grnForm, setGrnForm] = React.useState(emptyGrnForm);
  const invoiceAttachmentInputRef = React.useRef<HTMLInputElement | null>(null);

  const supplierById = React.useMemo(() => new Map(suppliers.map((supplier) => [supplier.id, supplier])), [suppliers]);
  const medicineById = React.useMemo(() => new Map(medicineCatalog.map((medicine) => [medicine.id, medicine])), [medicineCatalog]);
  const poById = React.useMemo(() => new Map(purchaseOrders.map((po) => [po.id, po])), [purchaseOrders]);
  const locationById = React.useMemo(() => new Map(inventoryLocations.map((location) => [location.id, location])), [inventoryLocations]);
  const [selectedPurchaseOrder, setSelectedPurchaseOrder] = React.useState<PurchaseOrderRow | null>(null);
  const [poEditorMode, setPoEditorMode] = React.useState<PurchaseOrderEditorMode>("edit");
  const [deepLinkNotice, setDeepLinkNotice] = React.useState<string | null>(null);
  const handledDeepLinkRef = React.useRef<{ po: string; grn: string; invoice: string }>({ po: "", grn: "", invoice: "" });
  const latestPathnameRef = React.useRef(location.pathname);
  const isMountedRef = React.useRef(true);
  const onProcurePath = isProcurePath(location.pathname);
  React.useEffect(() => {
    latestPathnameRef.current = location.pathname;
  }, [location.pathname]);
  React.useEffect(() => {
    if (onProcurePath) return;
    setPoDrawerOpen(false);
    setCancelDialogOpen(false);
    setQuickMedicineOpen(false);
    setInvoiceCancelDialogOpen(false);
    setSelectedGrn(null);
    setDeepLinkNotice(null);
  }, [onProcurePath]);
  React.useEffect(() => {
    return () => {
      isMountedRef.current = false;
    };
  }, []);
  const currentPurchaseOrder = React.useMemo(
    () => selectedPurchaseOrder || (editingPoId ? purchaseOrders.find((row) => row.id === editingPoId) || null : null),
    [editingPoId, purchaseOrders, selectedPurchaseOrder],
  );
  const purchaseOrderTotals = React.useMemo(() => computePurchaseOrderTotals(poLines), [poLines]);
  const purchaseOrderStatus = currentPurchaseOrder?.status || "Draft";
  const purchaseOrderStatusColor = purchaseOrderStatus === "Generated"
    ? "success"
    : purchaseOrderStatus === "Sent"
      ? "info"
      : purchaseOrderStatus === "Received"
        ? "success"
        : purchaseOrderStatus === "Cancelled" || purchaseOrderStatus === "Closed"
          ? "default"
          : "warning";
  const purchaseOrderStatusLabel = purchaseOrderStatus === "Generated"
    ? "🟢 Generated"
    : purchaseOrderStatus === "Sent"
      ? "🔵 Sent"
      : purchaseOrderStatus === "Received"
        ? "✅ Received"
        : purchaseOrderStatus === "Closed"
          ? "⚪ Closed"
          : purchaseOrderStatus === "Cancelled"
            ? "⚫ Cancelled"
            : "🟡 Draft";
  const purchaseOrderTimestamp = currentPurchaseOrder?.status === "Generated" ? currentPurchaseOrder.updatedAt : null;
  const purchaseOrderNumberDisplay = purchaseOrderStatus === "Generated" || purchaseOrderStatus === "Sent" || purchaseOrderStatus === "Received" || purchaseOrderStatus === "Closed" ? poForm.poNumber : "";
  const purchaseOrderCanEdit = purchaseOrderStatus === "Draft" && poEditorMode === "edit";
  const purchaseOrderCanCancel = purchaseOrderStatus !== "Cancelled" && Boolean(editingPoId);
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

  const loadPurchaseOrders = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoadingPurchaseOrders(true);
    setPurchaseOrderError(null);
    try {
      const rows = await getPurchaseOrders(auth.accessToken, auth.tenantId);
      logPo("PO_LIST_RESPONSE", rows);
      setPurchaseOrders(rows.map((row) => mapBackendPurchaseOrder(row, medicineById)));
    } catch (err) {
      setPurchaseOrders([]);
      setPurchaseOrderError(err instanceof Error ? err.message : "Failed to load purchase orders");
    } finally {
      setLoadingPurchaseOrders(false);
    }
  }, [auth.accessToken, auth.tenantId, medicineById]);

  React.useEffect(() => {
    void loadPurchaseOrders();
  }, [loadPurchaseOrders]);

  const loadSupplierInvoices = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoadingInvoices(true);
    setInvoiceError(null);
    try {
      const rows = await getSupplierInvoices(auth.accessToken, auth.tenantId);
      setInvoices(rows.map(mapBackendSupplierInvoice));
    } catch (err) {
      setInvoices([]);
      setInvoiceError(err instanceof Error ? err.message : "Failed to load supplier invoices");
    } finally {
      setLoadingInvoices(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void loadSupplierInvoices();
  }, [loadSupplierInvoices]);

  const loadInventoryLocations = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const rows = await getInventoryLocations(auth.accessToken, auth.tenantId);
      setInventoryLocations(rows);
    } catch {
      setInventoryLocations([]);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void loadInventoryLocations();
  }, [loadInventoryLocations]);

  const loadGoodsReceiptRows = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoadingGrns(true);
    setGrnError(null);
    try {
      const rows = await getGoodsReceipts(auth.accessToken, auth.tenantId);
      setGrns(rows.map((row) => mapBackendGoodsReceipt(row, purchaseOrders, medicineById, locationById)));
    } catch (err) {
      setGrns([]);
      setGrnError(err instanceof Error ? err.message : "Failed to load goods receipts");
    } finally {
      setLoadingGrns(false);
    }
  }, [auth.accessToken, auth.tenantId, locationById, medicineById, purchaseOrders]);

  React.useEffect(() => {
    void loadGoodsReceiptRows();
  }, [loadGoodsReceiptRows]);

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

  const eligibleInvoicePurchaseOrders = React.useMemo(
    () => purchaseOrders.filter((po) => po.status === "Generated" || po.status === "Sent" || po.status === "Partially Received" || po.status === "Received"),
    [purchaseOrders],
  );
  const selectedInvoice = React.useMemo(
    () => selectedInvoiceId ? invoices.find((invoice) => invoice.id === selectedInvoiceId) ?? null : null,
    [invoices, selectedInvoiceId],
  );
  const invoiceReadOnly = invoiceEditorMode === "view" || selectedInvoice?.status === "READY_FOR_PAYMENT" || selectedInvoice?.status === "APPROVED_FOR_PAYMENT" || selectedInvoice?.status === "PAID" || selectedInvoice?.status === "CANCELLED";
  const invoiceCanEdit = selectedInvoice != null && (selectedInvoice.status === "DRAFT" || selectedInvoice.status === "MATCHED");
  const invoiceCanUpload = selectedInvoice != null && invoiceCanEdit;
  const invoicePurchaseOrderOptions = React.useMemo(() => {
    if (!selectedInvoice?.purchaseOrderId) return eligibleInvoicePurchaseOrders;
    const current = purchaseOrders.find((po) => po.id === selectedInvoice.purchaseOrderId);
    if (!current || eligibleInvoicePurchaseOrders.some((po) => po.id === current.id)) {
      return eligibleInvoicePurchaseOrders;
    }
    return [current, ...eligibleInvoicePurchaseOrders];
  }, [eligibleInvoicePurchaseOrders, purchaseOrders, selectedInvoice?.purchaseOrderId]);

  const eligibleGoodsReceiptPurchaseOrders = React.useMemo(
    () => purchaseOrders.filter((po) => po.status === "Generated" || po.status === "Sent" || po.status === "Partially Received"),
    [purchaseOrders],
  );

  const receivedQtyByPoMedicine = React.useMemo(() => {
    const grouped = new Map<string, number>();
    grns
      .filter((grn) => grn.status === "Posted")
      .forEach((grn) => {
        grn.lines.forEach((line) => {
          if (!line.medicineId) return;
          const key = `${grn.purchaseOrderId}:${line.medicineId}`;
          grouped.set(key, (grouped.get(key) || 0) + numberFromUnknown(line.receiveQty));
        });
      });
    return grouped;
  }, [grns]);

  const selectedInvoicePurchaseOrder = React.useMemo(
    () => invoicePurchaseOrderOptions.find((po) => po.id === invoiceForm.purchaseOrderId) ?? null,
    [invoiceForm.purchaseOrderId, invoicePurchaseOrderOptions],
  );

  const selectedInvoiceItems = React.useMemo(
    () => selectedInvoicePurchaseOrder?.items ?? [],
    [selectedInvoicePurchaseOrder],
  );

  const selectedInvoicePoTotal = React.useMemo(
    () => selectedInvoicePurchaseOrder?.totalValue ?? 0,
    [selectedInvoicePurchaseOrder],
  );

  const invoiceComputedPayable = React.useMemo(() => {
    const amount = Number(invoiceForm.invoiceAmount || 0);
    const gstAmount = Number(invoiceForm.gstAmount || 0);
    const discount = Number(invoiceForm.discount || 0);
    return Math.max(0, amount + gstAmount - discount);
  }, [invoiceForm.discount, invoiceForm.gstAmount, invoiceForm.invoiceAmount]);

  const invoiceVsPoDifference = React.useMemo(() => {
    if (!selectedInvoicePurchaseOrder) return null;
    return invoiceComputedPayable - selectedInvoicePoTotal;
  }, [invoiceComputedPayable, selectedInvoicePoTotal, selectedInvoicePurchaseOrder]);
  const filteredInvoices = React.useMemo(() => {
    return invoices.filter((invoice) => {
      if (
        invoiceStatusFilter !== "ALL"
        && invoice.status !== invoiceStatusFilter
        && !(invoiceStatusFilter === "READY_FOR_PAYMENT" && invoice.status === "APPROVED_FOR_PAYMENT")
      ) return false;
      if (invoiceSupplierFilter !== "ALL" && invoice.supplierId !== invoiceSupplierFilter) return false;
      if (invoicePoFilter !== "ALL" && (invoice.purchaseOrderId || "") !== invoicePoFilter) return false;
      if (invoiceVarianceOnly && Math.abs(invoice.varianceAmount) < 0.005) return false;
      if (invoiceDateFromFilter && invoice.invoiceDate < invoiceDateFromFilter) return false;
      if (invoiceDateToFilter && invoice.invoiceDate > invoiceDateToFilter) return false;
      return true;
    });
  }, [invoiceDateFromFilter, invoiceDateToFilter, invoicePoFilter, invoiceStatusFilter, invoiceSupplierFilter, invoiceVarianceOnly, invoices]);

  const selectedGrnPurchaseOrder = React.useMemo(
    () => eligibleGoodsReceiptPurchaseOrders.find((po) => po.id === grnForm.purchaseOrderId) ?? null,
    [eligibleGoodsReceiptPurchaseOrders, grnForm.purchaseOrderId],
  );
  const selectedGrnInvoice = React.useMemo(
    () => selectedGrn?.supplierInvoiceId ? invoices.find((invoice) => invoice.id === selectedGrn.supplierInvoiceId) ?? null : null,
    [invoices, selectedGrn?.supplierInvoiceId],
  );
  const relatedGrnByInvoiceId = React.useMemo(
    () => new Map(grns.filter((grn) => Boolean(grn.supplierInvoiceId)).map((grn) => [grn.supplierInvoiceId as string, grn])),
    [grns],
  );
  const relatedGrnByPurchaseOrderId = React.useMemo(
    () => new Map(grns.filter((grn) => Boolean(grn.purchaseOrderId)).map((grn) => [grn.purchaseOrderId, grn])),
    [grns],
  );
  const relatedInvoiceByPurchaseOrderId = React.useMemo(
    () => new Map(invoices.filter((invoice) => Boolean(invoice.purchaseOrderId)).map((invoice) => [invoice.purchaseOrderId as string, invoice])),
    [invoices],
  );
  const openInventoryForReference = React.useCallback((reference: string) => {
    navigate(`/inventory?tab=stocks&ref=${encodeURIComponent(reference)}`);
  }, [navigate]);

  const updateWorkspace = React.useCallback((next: Workspace) => {
    if (!isProcurePath(location.pathname)) return;
    const nextParams = new URLSearchParams(searchParams);
    const currentWorkspace = parseWorkspace(searchParams.get("workspace"));
    const currentFocus = searchParams.get("focus");
    if (currentWorkspace === next && (next !== "suppliers" || currentFocus === "supplier")) {
      return;
    }
    nextParams.set("workspace", next);
    if (next === "suppliers") {
      nextParams.set("focus", "supplier");
    } else {
      nextParams.delete("focus");
    }
    nextParams.delete("po");
    nextParams.delete("invoice");
    nextParams.delete("grn");
    nextParams.delete("receipt");
    setSearchParams(nextParams, { replace: false });
  }, [location.pathname, searchParams, setSearchParams]);

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
    setSelectedPurchaseOrder(null);
    setPoEditorMode("edit");
    setCancelReason("");
    setCancelError(null);
    setPurchaseOrderError(null);
    setPurchaseOrderSuccess(null);
  }, []);

  const loadPurchaseOrderDetail = React.useCallback(async (
    id: string,
    selectedListRow?: PurchaseOrderRow | null,
    requestedMode: PurchaseOrderEditorMode = "view",
  ) => {
    if (!auth.accessToken || !auth.tenantId) return null;
    logPo("PO_DETAIL_REQUEST", id);
    try {
      const detail = await getPurchaseOrder(auth.accessToken, auth.tenantId, id);
      logPo("PO_DETAIL_RESPONSE", detail);
      const mapped = mapBackendPurchaseOrder(detail, medicineById);
      setSelectedPurchaseOrder(mapped);
      setEditingPoId(mapped.id);
      setPoEditorMode(mapped.status === "Draft" ? requestedMode : "view");
      setPoForm({
        supplierId: mapped.supplierId,
        poNumber: mapped.poNumber,
        orderDate: mapped.orderDate,
        expectedDelivery: mapped.expectedDelivery,
        notes: "",
      });
      setPoLines(mapped.items.length ? mapped.items : []);
      setPoFieldErrors({});
      setCancelError(null);
      logPo("PO_MAPPED_FORM", {
        selectedListRow,
        mappedEditorState: mapped,
      });
      return mapped;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load purchase order";
      setPurchaseOrderError(message);
      if (selectedListRow) {
        const fallback = mapBackendPurchaseOrder({
          id: selectedListRow.id,
          tenantId: "",
          supplierId: selectedListRow.supplierId,
          supplierName: selectedListRow.supplierName,
          poNumber: selectedListRow.poNumber,
          orderDate: selectedListRow.orderDate,
          expectedDeliveryDate: selectedListRow.expectedDelivery,
          itemsJson: JSON.stringify(poLines.map(mapPurchaseOrderLineToApi)),
          matchingStatus: selectedListRow.status,
          varianceSummary: null,
          approvalNote: selectedListRow.approvalNote,
          createdAt: selectedListRow.updatedAt,
          updatedAt: selectedListRow.updatedAt,
        } as PurchaseOrder, medicineById);
        setSelectedPurchaseOrder(fallback);
        setEditingPoId(fallback.id);
        setPoEditorMode(fallback.status === "Draft" ? requestedMode : "view");
        setPoForm({
          supplierId: fallback.supplierId,
          poNumber: fallback.poNumber,
          orderDate: fallback.orderDate,
          expectedDelivery: fallback.expectedDelivery,
          notes: "",
        });
        setPoLines(fallback.items.length ? fallback.items : []);
        logPo("PO_MAPPED_FORM", {
          selectedListRow,
          mappedEditorState: fallback,
        });
        return fallback;
      }
      return null;
    }
  }, [auth.accessToken, auth.tenantId, medicineById, poLines]);

  const openPurchaseOrderForView = React.useCallback((po: PurchaseOrderRow) => {
    logPo("PO_OPEN_SELECTED", po);
    void loadPurchaseOrderDetail(po.id, po, "view");
  }, [loadPurchaseOrderDetail]);

  const openPurchaseOrderForEdit = React.useCallback((po: PurchaseOrderRow) => {
    logPo("PO_OPEN_SELECTED", po);
    void loadPurchaseOrderDetail(po.id, po, "edit");
  }, [loadPurchaseOrderDetail]);

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

  const persistPurchaseOrder = React.useCallback(async (status: PurchaseOrderRow["status"]) => {
    if (!auth.accessToken || !auth.tenantId) return null;
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
    const existing = editingPoId ? purchaseOrders.find((row) => row.id === editingPoId) : null;
    const poNumber = existing?.poNumber || validated.poNumber || generatePurchaseOrderNumber(purchaseOrders);
    const payload: PurchaseOrderInput = {
      supplierId: validated.supplierId,
      poNumber,
      orderDate: validated.orderDate,
      expectedDeliveryDate: validated.expectedDelivery || null,
      items: poLines.map(mapPurchaseOrderLineToApi),
      approvalNote: encodePurchaseOrderApprovalNote(status, status === "Cancelled" ? cancelReason : ""),
    };
    logPo("PO_SAVE_PAYLOAD", payload);
    setPurchaseOrderError(null);
    try {
      const saved = await createPurchaseOrder(auth.accessToken, auth.tenantId, payload);
      logPo("PO_SAVE_RESPONSE", saved);
      await loadPurchaseOrders();
      await loadPurchaseOrderDetail(saved.id);
      setPurchaseOrderSuccess(status === "Generated" ? "Purchase order generated." : status === "Cancelled" ? "Purchase order cancelled." : "Purchase order draft saved.");
      return saved;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save purchase order";
      setPurchaseOrderError(message);
      return null;
    }
  }, [auth.accessToken, auth.tenantId, cancelReason, loadPurchaseOrderDetail, loadPurchaseOrders, poLines, purchaseOrders, suppliers, validatePurchaseOrderForm]);

  const savePurchaseOrderDraft = React.useCallback(() => {
    void persistPurchaseOrder("Draft");
  }, [persistPurchaseOrder]);

  const generatePurchaseOrder = React.useCallback(() => {
    void persistPurchaseOrder("Generated");
  }, [persistPurchaseOrder]);

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
    if (!auth.accessToken || !auth.tenantId) return;
    setPurchaseOrderError(null);
    logPo("PO_SAVE_PAYLOAD", { id: editingPoId, status: "Cancelled", reason: cancelReason.trim() });
    void cancelPurchaseOrder(auth.accessToken, auth.tenantId, editingPoId, cancelReason.trim())
      .then((response) => {
        logPo("PO_SAVE_RESPONSE", response);
        return loadPurchaseOrders().then(() => loadPurchaseOrderDetail(response.id));
      })
      .then(() => {
        setCancelDialogOpen(false);
        setCancelReason("");
        setPurchaseOrderSuccess("Purchase order cancelled.");
      })
      .catch((err) => {
        setPurchaseOrderError(err instanceof Error ? err.message : "Failed to cancel purchase order");
      });
  }, [auth.accessToken, auth.tenantId, cancelReason, editingPoId, loadPurchaseOrderDetail, loadPurchaseOrders]);

  const clearPurchaseOrderFilters = React.useCallback(() => {
    setPoSearch("");
    setPoStatusFilter("ALL");
    setPoSupplierFilter("ALL");
  }, []);

  const openPrintWindow = React.useCallback((purchaseOrder: PurchaseOrderRow) => {
    const printWindow = window.open("", "_blank", "noopener,noreferrer,width=1024,height=768");
    if (!printWindow) {
      setPurchaseOrderError("Unable to open print preview. Allow popups and try again.");
      return;
    }
    const clinicName = auth.tenantName || auth.selectedTenant?.name || "Clinic";
    printWindow.document.open();
    printWindow.document.write(renderPurchaseOrderPrintDocument(clinicName, purchaseOrder));
    printWindow.document.close();
    printWindow.focus();
    printWindow.print();
  }, [auth.selectedTenant?.name, auth.tenantName]);

  const handleViewPurchaseOrderFromDrawer = React.useCallback((po: PurchaseOrderRow) => {
    openPurchaseOrderForView(po);
    setPoDrawerOpen(false);
  }, [openPurchaseOrderForView]);

  const handleEditPurchaseOrderFromDrawer = React.useCallback((po: PurchaseOrderRow) => {
    openPurchaseOrderForEdit(po);
    setPoDrawerOpen(false);
  }, [openPurchaseOrderForEdit]);

  const handleGeneratePurchaseOrderFromDrawer = React.useCallback(async (po: PurchaseOrderRow) => {
    const mapped = await loadPurchaseOrderDetail(po.id, po, "edit");
    if (!mapped) return;
    setPoDrawerOpen(false);
    const payload: PurchaseOrderInput = {
      supplierId: mapped.supplierId,
      poNumber: mapped.poNumber,
      orderDate: mapped.orderDate,
      expectedDeliveryDate: mapped.expectedDelivery || null,
      items: mapped.items.map(mapPurchaseOrderLineToApi),
      approvalNote: encodePurchaseOrderApprovalNote("Generated", ""),
    };
    logPo("PO_SAVE_PAYLOAD", payload);
    setPurchaseOrderError(null);
    try {
      const saved = await createPurchaseOrder(auth.accessToken!, auth.tenantId!, payload);
      logPo("PO_SAVE_RESPONSE", saved);
      await loadPurchaseOrders();
      await loadPurchaseOrderDetail(saved.id, null, "view");
      setPurchaseOrderSuccess("Purchase order generated.");
    } catch (err) {
      setPurchaseOrderError(err instanceof Error ? err.message : "Failed to generate purchase order");
    }
  }, [auth.accessToken, auth.tenantId, loadPurchaseOrderDetail, loadPurchaseOrders]);

  const handleCancelPurchaseOrderFromDrawer = React.useCallback((po: PurchaseOrderRow) => {
    setEditingPoId(po.id);
    setSelectedPurchaseOrder(po);
    setPoEditorMode("view");
    setCancelReason("");
    setCancelError(null);
    setCancelDialogOpen(true);
  }, []);

  const handlePrintPurchaseOrderFromDrawer = React.useCallback(async (po: PurchaseOrderRow) => {
    const mapped = await loadPurchaseOrderDetail(po.id, po, "view");
    if (!mapped) return;
    openPrintWindow(mapped);
  }, [loadPurchaseOrderDetail, openPrintWindow]);

  const addPoLine = () => setPoLines((current) => [...current, { ...emptyPoLine }]);
  const removePoLine = (index: number) => setPoLines((current) => current.length <= 1 ? current : current.filter((_, rowIndex) => rowIndex !== index));
  const updatePoLine = React.useCallback((index: number, patch: Partial<PurchaseOrderLineState>) => {
    setPoLines((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row));
  }, []);

  const openQuickCreateMedicine = React.useCallback((rowIndex: number, seedText: string) => {
    setQuickMedicineRowIndex(rowIndex);
    setQuickMedicineForm({
      ...emptyQuickMedicineForm,
      medicineName: seedText.trim(),
      active: true,
    });
    setQuickMedicineFieldErrors({});
    setPurchaseOrderError(null);
    setQuickMedicineOpen(true);
  }, []);

  const saveQuickMedicine = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || quickMedicineRowIndex == null) return;
    const parsed = medicineMasterSchema.safeParse(quickMedicineForm);
    if (!parsed.success) {
      const fieldErrors = mapZodErrors(parsed.error);
      setQuickMedicineFieldErrors(fieldErrors);
      setPurchaseOrderError(firstZodError(parsed.error));
      return;
    }
    if (hasDuplicateMedicineMaster(parsed.data, medicineCatalog, null)) {
      setQuickMedicineFieldErrors({ medicineName: "A medicine with this name already exists." });
      setPurchaseOrderError("A medicine with this name already exists.");
      return;
    }
    setSavingQuickMedicine(true);
    setPurchaseOrderError(null);
    try {
      const created = await createMedicine(auth.accessToken, auth.tenantId, parsed.data);
      setMedicineCatalog((current) => [...current, created].sort((left, right) => left.medicineName.localeCompare(right.medicineName)));
      updatePoLine(quickMedicineRowIndex, {
        medicineId: created.id,
        medicineName: created.medicineName,
        unit: created.unit || "",
      });
      setQuickMedicineOpen(false);
      setQuickMedicineRowIndex(null);
      setQuickMedicineForm(emptyQuickMedicineForm);
      setQuickMedicineFieldErrors({});
      setPurchaseOrderSuccess("Medicine created and selected.");
    } catch (err) {
      const message = mapMedicineSaveError(err);
      setPurchaseOrderError(message);
      setQuickMedicineFieldErrors((current) => ({
        ...current,
        medicineName: message.toLowerCase().includes("name") || message.toLowerCase().includes("exists") ? message : current.medicineName || "",
      }));
    } finally {
      setSavingQuickMedicine(false);
    }
  }, [auth.accessToken, auth.tenantId, medicineCatalog, quickMedicineForm, quickMedicineRowIndex, updatePoLine]);

  React.useEffect(() => {
    if (!selectedInvoicePurchaseOrder) {
      setInvoiceForm((current) => current.supplierId ? { ...current, supplierId: "" } : current);
      return;
    }
    setInvoiceForm((current) => current.supplierId === selectedInvoicePurchaseOrder.supplierId
      ? current
      : { ...current, supplierId: selectedInvoicePurchaseOrder.supplierId });
  }, [selectedInvoicePurchaseOrder]);

  const resetInvoiceForm = React.useCallback(() => {
    setInvoiceForm(emptyInvoiceForm);
    setInvoiceFieldErrors({});
    setInvoiceError(null);
    setSelectedInvoiceId(null);
    setInvoiceEditorMode("create");
    setInvoiceSuccess(null);
    setInvoiceCancelDialogOpen(false);
    setInvoiceCancelReason("");
    setInvoiceCancelError(null);
    if (invoiceAttachmentInputRef.current) {
      invoiceAttachmentInputRef.current.value = "";
    }
  }, []);

  const loadInvoiceIntoForm = React.useCallback((invoice: SupplierInvoiceRow, mode: SupplierInvoiceEditorMode) => {
    setSelectedInvoiceId(invoice.id);
    setInvoiceEditorMode(mode);
    setInvoiceFieldErrors({});
    setInvoiceError(null);
    setInvoiceSuccess(null);
    setInvoiceForm({
      supplierId: invoice.supplierId,
      purchaseOrderId: invoice.purchaseOrderId || "",
      invoiceNumber: invoice.invoiceNumber,
      invoiceDate: invoice.invoiceDate,
      invoiceAmount: invoice.invoiceAmount ? String(invoice.invoiceAmount) : "",
      gstAmount: invoice.gstAmount ? String(invoice.gstAmount) : "0",
      discount: invoice.discountAmount ? String(invoice.discountAmount) : "0",
      varianceReason: invoice.varianceReason || "",
      notes: invoice.notes || "",
    });
  }, []);
  const setProcurementWorkspace = React.useCallback((next: Workspace, extraParams?: Record<string, string | null | undefined>) => {
    if (!isProcurePath(location.pathname)) return;
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("workspace", next);
    if (next === "suppliers") {
      nextParams.set("focus", "supplier");
    } else {
      nextParams.delete("focus");
    }
    (["po", "invoice", "grn", "receipt"] as const).forEach((key) => {
      nextParams.delete(key);
    });
    if (extraParams) {
      Object.entries(extraParams).forEach(([key, value]) => {
        if (value == null || value === "") {
          nextParams.delete(key);
        } else {
          nextParams.set(key, value);
        }
      });
    }
    setSearchParams(nextParams, { replace: false });
  }, [location.pathname, searchParams, setSearchParams]);
  const openPurchaseOrderReference = React.useCallback(async (purchaseOrderId: string | null | undefined) => {
    if (!purchaseOrderId) return;
    const po = purchaseOrders.find((candidate) => candidate.id === purchaseOrderId) ?? null;
    if (!po) return;
    await loadPurchaseOrderDetail(po.id, po, "view");
    if (!isMountedRef.current || !isProcurePath(latestPathnameRef.current)) return;
    setPoDrawerOpen(true);
    setProcurementWorkspace("purchase-orders", { po: po.poNumber || po.id });
  }, [loadPurchaseOrderDetail, purchaseOrders, setProcurementWorkspace]);
  const openInvoiceReference = React.useCallback((invoiceId: string | null | undefined) => {
    if (!invoiceId) return;
    const invoice = invoices.find((candidate) => candidate.id === invoiceId) ?? null;
    if (!invoice) return;
    if (!isMountedRef.current || !isProcurePath(latestPathnameRef.current)) return;
    loadInvoiceIntoForm(invoice, "view");
    setProcurementWorkspace("supplier-invoices", { invoice: invoice.invoiceNumber || invoice.id });
  }, [invoices, loadInvoiceIntoForm, setProcurementWorkspace]);

  const resetGrnForm = React.useCallback(() => {
    setGrnForm({
      ...emptyGrnForm,
      receivedAt: new Date().toISOString().slice(0, 10),
    });
    setGrnFieldErrors({});
    setGrnError(null);
    setGrnSuccess(null);
  }, []);

  const syncGrnFromPurchaseOrder = React.useCallback((purchaseOrderId: string) => {
    const po = eligibleGoodsReceiptPurchaseOrders.find((candidate) => candidate.id === purchaseOrderId) ?? null;
    if (!po) {
      setGrnForm((current) => ({ ...current, purchaseOrderId: "", supplierId: "", poNumber: "", poDate: "", lines: [] }));
      return;
    }
    const defaultLocationId = inventoryLocations[0]?.id || "";
    setGrnForm((current) => ({
      ...current,
      purchaseOrderId: po.id,
      supplierId: po.supplierId,
      poNumber: po.poNumber,
      poDate: po.orderDate,
      lines: po.items.map((item) => {
        const orderedQty = numberFromUnknown(item.quantity);
        const alreadyReceivedQty = receivedQtyByPoMedicine.get(`${po.id}:${item.medicineId}`) || 0;
        const pendingQty = Math.max(orderedQty - alreadyReceivedQty, 0);
        return {
          medicineId: item.medicineId,
          medicineName: item.medicineName,
          unit: item.unit,
          orderedQty,
          alreadyReceivedQty,
          pendingQty,
          receiveQty: "",
          batchNumber: "",
          expiryDate: "",
          locationId: defaultLocationId,
          locationName: locationById.get(defaultLocationId)?.locationName || "",
          remarks: "",
          lineStatus: goodsReceiptLineStatus(pendingQty, 0),
          unitPrice: item.unitPrice ? Number(item.unitPrice) : null,
          gstPercent: item.gst ? Number(item.gst) : null,
          sellingPrice: null,
        };
      }),
      receiptNumber: current.receiptNumber,
      receivedAt: current.receivedAt || new Date().toISOString().slice(0, 10),
    }));
    setGrnFieldErrors({});
    setGrnError(null);
  }, [eligibleGoodsReceiptPurchaseOrders, inventoryLocations, locationById, receivedQtyByPoMedicine]);

  const updateGrnLine = React.useCallback((index: number, patch: Partial<GoodsReceiptLineState>) => {
    setGrnForm((current) => ({
      ...current,
      lines: current.lines.map((line, rowIndex) => {
        if (rowIndex !== index) return line;
        const next = { ...line, ...patch };
        const receiveQty = numberFromUnknown(next.receiveQty);
        return {
          ...next,
          locationName: next.locationId ? locationById.get(next.locationId)?.locationName || "" : "",
          lineStatus: goodsReceiptLineStatus(next.pendingQty, receiveQty),
        };
      }),
    }));
  }, [locationById]);

  React.useEffect(() => {
    if (!grnForm.purchaseOrderId || !selectedGrnPurchaseOrder) return;
    syncGrnFromPurchaseOrder(grnForm.purchaseOrderId);
  }, [grnForm.purchaseOrderId, selectedGrnPurchaseOrder, syncGrnFromPurchaseOrder]);

  const validateGrnForm = React.useCallback(() => {
    const errors: Record<string, string> = {};
    if (!grnForm.purchaseOrderId) errors.purchaseOrderId = "Purchase order is required.";
    if (!grnForm.receivedAt) errors.receivedAt = "GRN date is required.";
    const activeLines = grnForm.lines.filter((line) => numberFromUnknown(line.receiveQty) > 0);
    if (!activeLines.length) {
      errors.lines = "At least one row must have receive quantity greater than zero.";
    }
    activeLines.forEach((line, index) => {
      const receiveQty = numberFromUnknown(line.receiveQty);
      if (receiveQty <= 0) errors[`lines.${index}.receiveQty`] = "Receive quantity must be greater than zero.";
      if (receiveQty > line.pendingQty) errors[`lines.${index}.receiveQty`] = "Receive quantity cannot exceed pending quantity.";
      if (!line.batchNumber.trim()) errors[`lines.${index}.batchNumber`] = "Batch number is required.";
      if (!line.expiryDate) errors[`lines.${index}.expiryDate`] = "Expiry date is required.";
      if (line.expiryDate && line.expiryDate < new Date().toISOString().slice(0, 10)) {
        errors[`lines.${index}.expiryDate`] = "Expiry date cannot be in the past.";
      }
      if (!line.locationId) errors[`lines.${index}.locationId`] = "Location is required.";
    });
    setGrnFieldErrors(errors);
    if (Object.keys(errors).length) {
      setGrnError(errors.lines || Object.values(errors)[0] || "Fix the highlighted GRN errors.");
      return null;
    }
    return {
      supplierId: grnForm.supplierId,
      purchaseOrderId: grnForm.purchaseOrderId,
      supplierInvoiceId: null,
      receiptNumber: grnForm.receiptNumber.trim() || `GRN-${Date.now()}`,
      receivedAt: grnForm.receivedAt,
      locationId: activeLines[0]?.locationId || "",
      items: activeLines.map((line) => ({
        medicineId: line.medicineId || null,
        medicineName: line.medicineName || null,
        quantity: numberFromUnknown(line.receiveQty),
        expectedUnitCost: line.unitPrice,
        unitCost: line.unitPrice,
        taxPercent: line.gstPercent,
        batchNumber: line.batchNumber.trim() || null,
        expiryDate: line.expiryDate || null,
        sellingPrice: line.sellingPrice,
        unit: line.unit || null,
        locationId: line.locationId || null,
        remarks: line.remarks.trim() || null,
      })),
      approvalNote: null,
    } satisfies GoodsReceiptInput;
  }, [grnForm]);

  const saveGrn = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const payload = validateGrnForm();
    if (!payload) return;
    setSavingGrn(true);
    setGrnError(null);
    setGrnSuccess(null);
    try {
      const saved = await createGoodsReceipt(auth.accessToken, auth.tenantId, payload);
      await confirmGoodsReceipt(auth.accessToken, auth.tenantId, saved.id, "Posted from procurement");
      await Promise.all([loadGoodsReceiptRows(), loadPurchaseOrders(), loadSupplierInvoices()]);
      resetGrnForm();
      setGrnSuccess("GRN confirmed. Inventory updated. Invoice moved to Ready for Payment.");
    } catch (err) {
      setGrnError(err instanceof Error ? err.message : "Failed to post goods receipt");
    } finally {
      setSavingGrn(false);
    }
  }, [auth.accessToken, auth.tenantId, loadGoodsReceiptRows, loadPurchaseOrders, loadSupplierInvoices, resetGrnForm, validateGrnForm]);

  React.useEffect(() => {
    if (!grnForm.receivedAt) {
      setGrnForm((current) => current.receivedAt ? current : { ...current, receivedAt: new Date().toISOString().slice(0, 10) });
    }
  }, [grnForm.receivedAt]);

  React.useEffect(() => {
    if (!onProcurePath) return;
    if (workspace !== "goods-receipt") return;
    const receiptRef = searchParams.get("grn") || searchParams.get("receipt");
    if (!receiptRef) return;
    const deepLinkKey = `${workspace}:${receiptRef}`;
    if (handledDeepLinkRef.current.grn === deepLinkKey || loadingGrns) return;
    handledDeepLinkRef.current.grn = deepLinkKey;
    const match = grns.find((grn) => grn.id === receiptRef || grn.receiptNumber === receiptRef) ?? null;
    if (match) {
      setSelectedGrn(match);
      setDeepLinkNotice(`Opened from reconciliation for GRN: ${match.receiptNumber}`);
    } else {
      setDeepLinkNotice(`Opened from reconciliation for GRN: ${receiptRef}`);
    }
  }, [grns, loadingGrns, onProcurePath, searchParams, workspace]);

  React.useEffect(() => {
    if (!onProcurePath) return;
    if (workspace !== "purchase-orders") return;
    const poRef = searchParams.get("po");
    if (!poRef) return;
    const deepLinkKey = `${workspace}:${poRef}`;
    if (handledDeepLinkRef.current.po === deepLinkKey || loadingPurchaseOrders) return;
    handledDeepLinkRef.current.po = deepLinkKey;
    const match = purchaseOrders.find((po) => po.id === poRef || po.poNumber === poRef) ?? null;
    if (!match) {
      setDeepLinkNotice(`Selected from reconciliation: ${poRef}`);
      return;
    }
    void loadPurchaseOrderDetail(match.id, match, "view")
      .then(() => {
        setPoDrawerOpen(false);
        setDeepLinkNotice(`Selected from reconciliation: ${match.poNumber}`);
      })
      .catch(() => {
        setDeepLinkNotice(`Selected from reconciliation: ${poRef}`);
      });
  }, [loadPurchaseOrderDetail, loadingPurchaseOrders, onProcurePath, purchaseOrders, searchParams, workspace]);

  React.useEffect(() => {
    if (!onProcurePath) return;
    if (workspace !== "supplier-invoices") return;
    const invoiceRef = searchParams.get("invoice");
    if (!invoiceRef) return;
    const deepLinkKey = `${workspace}:${invoiceRef}`;
    if (handledDeepLinkRef.current.invoice === deepLinkKey || loadingInvoices) return;
    handledDeepLinkRef.current.invoice = deepLinkKey;
    const match = invoices.find((invoice) => invoice.id === invoiceRef || invoice.invoiceNumber === invoiceRef) ?? null;
    if (!match) {
      setDeepLinkNotice(`Selected from reconciliation: ${invoiceRef}`);
      return;
    }
    loadInvoiceIntoForm(match, "view");
    setDeepLinkNotice(`Selected from reconciliation: ${match.invoiceNumber}`);
  }, [invoices, loadInvoiceIntoForm, loadingInvoices, onProcurePath, searchParams, workspace]);

  const currentSupplierCount = suppliers.length;
  const currentPurchaseOrderCount = purchaseOrders.filter((po) => po.status === "Generated" || po.status === "Sent" || po.status === "Partially Received").length;
  const currentInvoiceCount = invoices.filter((invoice) => invoice.status === "READY_FOR_PAYMENT" || invoice.status === "APPROVED_FOR_PAYMENT").length;
  const currentGrnCount = grns.filter((grn) => (grn.receivedAt || "").slice(0, 10) === new Date().toISOString().slice(0, 10)).length;

  const saveInvoice = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const availablePurchaseOrders = invoicePurchaseOrderOptions;
    if (!availablePurchaseOrders.length) {
      setInvoiceError("No purchase orders available for invoice matching.");
      setInvoiceFieldErrors({ purchaseOrderId: "No purchase orders available for invoice matching." });
      return;
    }
    const parsed = supplierInvoiceFormSchema.safeParse(invoiceForm);
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setInvoiceFieldErrors(errors);
      setInvoiceError(firstZodError(parsed.error));
      return;
    }
    const selectedPo = availablePurchaseOrders.find((po) => po.id === parsed.data.purchaseOrderId);
    if (!selectedPo) {
      setInvoiceFieldErrors({ purchaseOrderId: "Select an active purchase order." });
      setInvoiceError("Select an active purchase order.");
      return;
    }
    const supplier = supplierById.get(parsed.data.supplierId);
    if (!supplier) {
      setInvoiceFieldErrors({ supplierId: "Supplier is required." });
      setInvoiceError("Supplier is required.");
      return;
    }
    const totalAmount = Math.max(0, parsed.data.invoiceAmount + parsed.data.gstAmount - parsed.data.discount);
    const variance = Number((totalAmount - selectedPo.totalValue).toFixed(2));
    if (variance !== 0 && !parsed.data.varianceReason?.trim()) {
      setInvoiceFieldErrors({ varianceReason: "Variance reason is required when invoice amount differs from PO amount." });
      setInvoiceError("Variance reason is required when invoice amount differs from PO amount.");
      return;
    }
    const duplicate = invoices.some((invoice) => (
      invoice.id !== selectedInvoiceId
      && invoice.supplierId === parsed.data.supplierId
      && invoice.invoiceNumber.trim().toLowerCase() === parsed.data.invoiceNumber.trim().toLowerCase()
    ));
    if (duplicate) {
      const errors = { invoiceNumber: "Supplier already has an invoice with this number." };
      setInvoiceFieldErrors(errors);
      setInvoiceError("Supplier invoice already exists.");
      return;
    }

    const payload: SupplierInvoiceInput = {
      supplierId: parsed.data.supplierId,
      purchaseOrderId: parsed.data.purchaseOrderId,
      invoiceNumber: parsed.data.invoiceNumber,
      invoiceDate: parsed.data.invoiceDate,
      invoiceAmount: parsed.data.invoiceAmount,
      taxAmount: parsed.data.gstAmount,
      discountAmount: parsed.data.discount,
      totalAmount,
      items: selectedPo.items.map(mapPurchaseOrderLineToApi),
      varianceReason: parsed.data.varianceReason?.trim() || null,
      approvalNote: parsed.data.notes ?? null,
    };

    setSavingInvoice(true);
    setInvoiceError(null);
    setInvoiceSuccess(null);
    try {
      const saved = selectedInvoiceId
        ? await updateSupplierInvoice(auth.accessToken, auth.tenantId, selectedInvoiceId, payload)
        : await createSupplierInvoice(auth.accessToken, auth.tenantId, payload);
      setInvoiceSuccess(selectedInvoiceId ? "Supplier invoice updated." : "Supplier invoice saved.");
      await loadSupplierInvoices();
      loadInvoiceIntoForm(mapBackendSupplierInvoice(saved), selectedInvoiceId ? invoiceEditorMode : "edit");
      setInvoices((current) => {
        const next = current.some((invoice) => invoice.id === saved.id)
          ? current.map((invoice) => invoice.id === saved.id ? mapBackendSupplierInvoice(saved) : invoice)
          : [mapBackendSupplierInvoice(saved), ...current];
        return next;
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save supplier invoice";
      const normalized = message.toLowerCase();
      if (normalized.includes("already exists") || normalized.includes("duplicate")) {
        setInvoiceFieldErrors({ invoiceNumber: "Supplier already has an invoice with this number." });
      }
      setInvoiceError(message);
    } finally {
      setSavingInvoice(false);
    }
  }, [auth.accessToken, auth.tenantId, invoicePurchaseOrderOptions, invoiceForm, invoices, loadSupplierInvoices, loadInvoiceIntoForm, selectedInvoiceId, supplierById, invoiceEditorMode]);

  const handleMatchInvoice = React.useCallback(async (invoice: SupplierInvoiceRow) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const saved = await matchSupplierInvoice(auth.accessToken, auth.tenantId, invoice.id);
      const next = mapBackendSupplierInvoice(saved);
      setInvoices((current) => current.map((item) => item.id === next.id ? next : item));
      loadInvoiceIntoForm(next, "view");
      setInvoiceSuccess("Supplier invoice matched.");
    } catch (err) {
      setInvoiceError(err instanceof Error ? err.message : "Failed to match supplier invoice");
    }
  }, [auth.accessToken, auth.tenantId, loadInvoiceIntoForm]);

  const handleApproveInvoice = React.useCallback(async (invoice: SupplierInvoiceRow) => {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      const saved = await approveSupplierInvoiceForPayment(auth.accessToken, auth.tenantId, invoice.id);
      const next = mapBackendSupplierInvoice(saved);
      setInvoices((current) => current.map((item) => item.id === next.id ? next : item));
      loadInvoiceIntoForm(next, "view");
      setInvoiceSuccess("Supplier invoice moved to Ready for Payment.");
    } catch (err) {
      setInvoiceError(err instanceof Error ? err.message : "Failed to approve supplier invoice");
    }
  }, [auth.accessToken, auth.tenantId, loadInvoiceIntoForm]);

  const handleOpenCancelInvoice = React.useCallback((invoice: SupplierInvoiceRow) => {
    setSelectedInvoiceId(invoice.id);
    setInvoiceCancelDialogOpen(true);
    setInvoiceCancelReason("");
    setInvoiceCancelError(null);
  }, []);

  const handleConfirmCancelInvoice = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !selectedInvoiceId) return;
    if (!invoiceCancelReason.trim()) {
      setInvoiceCancelError("Cancellation reason is required.");
      return;
    }
    try {
      const saved = await cancelSupplierInvoice(auth.accessToken, auth.tenantId, selectedInvoiceId, invoiceCancelReason.trim());
      const next = mapBackendSupplierInvoice(saved);
      setInvoices((current) => current.map((item) => item.id === next.id ? next : item));
      loadInvoiceIntoForm(next, "view");
      setInvoiceCancelDialogOpen(false);
      setInvoiceCancelReason("");
      setInvoiceCancelError(null);
      setInvoiceSuccess("Supplier invoice cancelled.");
    } catch (err) {
      setInvoiceCancelError(err instanceof Error ? err.message : "Failed to cancel supplier invoice");
    }
  }, [auth.accessToken, auth.tenantId, invoiceCancelReason, loadInvoiceIntoForm, selectedInvoiceId]);

  const handleUploadInvoiceAttachment = React.useCallback(async (file: File | null) => {
    if (!auth.accessToken || !auth.tenantId || !selectedInvoiceId || !file) return;
    const lowerName = file.name.toLowerCase();
    const allowed = lowerName.endsWith(".pdf") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png");
    if (!allowed) {
      setInvoiceError("Only PDF, JPG, JPEG, and PNG invoice attachments are allowed.");
      return;
    }
    if (file.size > SUPPLIER_INVOICE_UPLOAD_MAX_SIZE_BYTES) {
      setInvoiceError("Invoice attachment must be 10 MB or smaller.");
      return;
    }
    setUploadingInvoiceAttachment(true);
    try {
      const uploaded = await uploadSupplierInvoiceAttachment(auth.accessToken, auth.tenantId, selectedInvoiceId, file);
      setInvoices((current) => current.map((invoice) => invoice.id === selectedInvoiceId ? {
        ...invoice,
        attachmentFileName: uploaded.fileName,
        attachmentMediaType: uploaded.mediaType,
        attachmentSizeBytes: uploaded.sizeBytes,
      } : invoice));
      setInvoiceSuccess("Invoice attachment uploaded.");
    } catch (err) {
      setInvoiceError(err instanceof Error ? err.message : "Failed to upload invoice attachment");
    } finally {
      setUploadingInvoiceAttachment(false);
      if (invoiceAttachmentInputRef.current) {
        invoiceAttachmentInputRef.current.value = "";
      }
    }
  }, [auth.accessToken, auth.tenantId, selectedInvoiceId]);

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
          <CompactStatCard label="Open Purchase Orders" value={currentPurchaseOrderCount} helper="Generated, sent, partially received" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Invoices Awaiting Payment" value={currentInvoiceCount} helper="Ready for payment" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Today's GRNs" value={currentGrnCount} helper="Goods receipts today" />
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
      {deepLinkNotice ? <Alert severity="info" onClose={() => setDeepLinkNotice(null)}>{deepLinkNotice}</Alert> : null}

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
          {purchaseOrderSuccess ? <Alert severity="success" onClose={() => setPurchaseOrderSuccess(null)}>{purchaseOrderSuccess}</Alert> : null}
          {purchaseOrderError ? <Alert severity="error" onClose={() => setPurchaseOrderError(null)}>{purchaseOrderError}</Alert> : null}
          {!purchaseOrderCanEdit ? (
            <Alert severity={purchaseOrderStatus === "Cancelled" ? "warning" : "info"}>
              {purchaseOrderStatus === "Generated" || purchaseOrderStatus === "Sent" || purchaseOrderStatus === "Received" || purchaseOrderStatus === "Closed"
                ? "This purchase order is in read-only mode. Use the drawer actions for printing, sending, history, or cancellation."
                : purchaseOrderStatus === "Cancelled"
                  ? "Cancelled purchase orders are read-only."
                  : "This draft is open in read-only view mode. Use Edit from the drawer to update it."}
            </Alert>
          ) : null}
          {currentPurchaseOrder ? (
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
              <Chip size="small" {...purchaseOrderStatusChipProps(currentPurchaseOrder.status)} />
              {relatedInvoiceByPurchaseOrderId.get(currentPurchaseOrder.id) ? <Button size="small" variant="outlined" onClick={() => openInvoiceReference(relatedInvoiceByPurchaseOrderId.get(currentPurchaseOrder.id)?.id || null)}>Linked Invoice</Button> : null}
              {relatedGrnByPurchaseOrderId.get(currentPurchaseOrder.id) ? <Button size="small" variant="outlined" onClick={() => setSelectedGrn(relatedGrnByPurchaseOrderId.get(currentPurchaseOrder.id) ?? null)}>Linked GRN</Button> : null}
            </Stack>
          ) : null}
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
                    <Select label="Supplier" value={poForm.supplierId} onChange={(e) => setPoForm((current) => ({ ...current, supplierId: e.target.value }))} disabled={!purchaseOrderCanEdit}>
                      {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                    </Select>
                    {poFieldErrors.supplierId ? <Typography variant="caption" color="error">{poFieldErrors.supplierId}</Typography> : null}
                  </FormControl>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    size="small"
                    label="PO number"
                    value={purchaseOrderNumberDisplay}
                    placeholder={purchaseOrderStatus === "Generated" ? "" : "Auto-generated when Purchase Order is Generated"}
                    InputProps={{ readOnly: true }}
                    helperText={poFieldErrors.poNumber || (purchaseOrderStatus === "Generated" ? "Immutable generated PO number." : "Auto-generated when Purchase Order is Generated.")}
                    error={Boolean(poFieldErrors.poNumber)}
                    fullWidth
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="PO date *" type="date" InputLabelProps={{ shrink: true }} value={poForm.orderDate} onChange={(e) => setPoForm((current) => ({ ...current, orderDate: e.target.value }))} helperText={poFieldErrors.orderDate} error={Boolean(poFieldErrors.orderDate)} fullWidth disabled={!purchaseOrderCanEdit} />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Expected delivery date *" type="date" InputLabelProps={{ shrink: true }} value={poForm.expectedDelivery} onChange={(e) => setPoForm((current) => ({ ...current, expectedDelivery: e.target.value }))} helperText={poFieldErrors.expectedDelivery} error={Boolean(poFieldErrors.expectedDelivery)} fullWidth disabled={!purchaseOrderCanEdit} />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Reference / Notes" value={poForm.notes} onChange={(e) => setPoForm((current) => ({ ...current, notes: e.target.value }))} fullWidth disabled={!purchaseOrderCanEdit} />
                </Grid>
              </Grid>

              <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Items</Typography>
                <Button size="small" variant="outlined" onClick={addPoLine} disabled={!purchaseOrderCanEdit}>Add Row</Button>
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
                                inputValue={line.medicineName || selectedMedicine?.medicineName || ""}
                                loading={loadingMedicines}
                                disabled={!purchaseOrderCanEdit}
                                onChange={(_, value) => {
                                  updatePoLine(index, {
                                    medicineId: value?.id || "",
                                    medicineName: value?.medicineName || "",
                                    unit: value?.unit || "",
                                  });
                                }}
                                onInputChange={(_, value, reason) => {
                                  if (reason === "clear") {
                                    updatePoLine(index, { medicineId: "", medicineName: "", unit: "" });
                                    return;
                                  }
                                  if (reason === "input") {
                                    updatePoLine(index, { medicineId: "", medicineName: value, unit: "" });
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
                                      disabled={!purchaseOrderCanEdit}
                                    />
                                  )}
                                />
                              {showCreateMedicine ? (
                                <Button
                                  size="small"
                                  variant="text"
                                  sx={{ alignSelf: "flex-start" }}
                                  onClick={() => openQuickCreateMedicine(index, line.medicineName)}
                                  disabled={!purchaseOrderCanEdit}
                                >
                                  + Create New Medicine
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
                              disabled={!purchaseOrderCanEdit}
                            />
                          </TableCell>
                          <TableCell sx={{ py: 0.5 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700, textAlign: "right" }}>
                              {selectedMedicine?.unit || line.unit || "-"}
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
                              disabled={!purchaseOrderCanEdit}
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
                              disabled={!purchaseOrderCanEdit}
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
                              disabled={!purchaseOrderCanEdit}
                            />
                          </TableCell>
                          <TableCell align="right" sx={{ py: 0.5, whiteSpace: "nowrap" }}>
                            {lineTotal == null ? "-" : `INR ${lineTotal.toFixed(2)}`}
                          </TableCell>
                          <TableCell align="right" sx={{ py: 0.5 }}>
                            <Button size="small" color="inherit" onClick={() => removePoLine(index)} disabled={!purchaseOrderCanEdit || poLines.length === 1}>Remove</Button>
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
                    {purchaseOrderCanEdit ? (
                      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                        <Button size="small" variant="contained" onClick={savePurchaseOrderDraft}>Save Draft</Button>
                        <Button size="small" variant="outlined" onClick={generatePurchaseOrder}>Generate PO</Button>
                        {purchaseOrderCanCancel ? <Button size="small" variant="outlined" color="inherit" onClick={openCancelPurchaseOrder}>Cancel</Button> : null}
                      </Stack>
                    ) : (
                      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                        <Button size="small" variant="outlined" color="inherit" onClick={() => setPoDrawerOpen(true)}>Open Purchase Orders</Button>
                        {purchaseOrderCanCancel ? <Button size="small" variant="outlined" color="inherit" onClick={openCancelPurchaseOrder}>Cancel</Button> : null}
                      </Stack>
                    )}
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
                <MenuItem value="Sent">Sent</MenuItem>
                <MenuItem value="Received">Received</MenuItem>
                <MenuItem value="Closed">Closed</MenuItem>
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
            <Button size="small" variant="outlined" onClick={clearPurchaseOrderFilters}>Clear filters</Button>
          </Stack>

          <Divider />

          <Box sx={{ overflowY: "auto", flex: 1, pr: 0.5 }}>
            {loadingPurchaseOrders ? (
              <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                <Typography variant="body2" color="text.secondary">Loading purchase orders...</Typography>
              </Box>
            ) : filteredPurchaseOrders.length ? (
              <Stack spacing={1}>
                {filteredPurchaseOrders.map((po) => {
                  const statusChip = purchaseOrderStatusChipProps(po.status);
                  const showDraftActions = po.status === "Draft";
                  const showGeneratedActions = po.status === "Generated";
                  const showSentActions = po.status === "Sent";
                  const showClosedActions = po.status === "Received" || po.status === "Closed";
                  const showCancelledActions = po.status === "Cancelled";
                  const disabledPdfTooltip = "PDF download will be available after document template setup.";
                  const disabledSendTooltip = "Send PO will be available after supplier email setup.";
                  const disabledHistoryTooltip = "Activity history will be available after audit timeline setup.";
                  return (
                    <Card
                      key={po.id}
                      variant="outlined"
                      sx={{
                        borderColor: po.id === editingPoId ? "primary.main" : "divider",
                        bgcolor: po.id === editingPoId ? "action.selected" : "background.paper",
                      }}
                    >
                      <CardContent sx={{ p: 1.25, "&:last-child": { pb: 1.25 } }}>
                        <Stack spacing={1}>
                          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{po.poNumber}</Typography>
                            <Chip size="small" label={statusChip.label} color={statusChip.color} />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">{po.supplierName}</Typography>
                          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                            <Typography variant="caption" color="text.secondary">{po.orderDate}</Typography>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>INR {po.totalValue.toFixed(2)}</Typography>
                          </Stack>
                          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                            <Button size="small" variant="outlined" onClick={() => handleViewPurchaseOrderFromDrawer(po)}>View</Button>
                            {showDraftActions ? (
                              <>
                                <Button size="small" variant="outlined" onClick={() => handleEditPurchaseOrderFromDrawer(po)}>Edit</Button>
                                <Button size="small" variant="contained" onClick={() => void handleGeneratePurchaseOrderFromDrawer(po)}>Generate</Button>
                                <Button size="small" variant="outlined" color="inherit" onClick={() => handleCancelPurchaseOrderFromDrawer(po)}>Cancel</Button>
                              </>
                            ) : null}
                            {showGeneratedActions ? (
                              <>
                                <Button size="small" variant="outlined" onClick={() => void handlePrintPurchaseOrderFromDrawer(po)}>Print</Button>
                                <Tooltip title={disabledPdfTooltip}>
                                  <span><Button size="small" variant="outlined" disabled>Download PDF</Button></span>
                                </Tooltip>
                                <Tooltip title={disabledSendTooltip}>
                                  <span><Button size="small" variant="outlined" disabled>Send</Button></span>
                                </Tooltip>
                                <Button size="small" variant="outlined" color="inherit" onClick={() => handleCancelPurchaseOrderFromDrawer(po)}>Cancel</Button>
                              </>
                            ) : null}
                            {showSentActions ? (
                              <>
                                <Button size="small" variant="outlined" onClick={() => void handlePrintPurchaseOrderFromDrawer(po)}>Print</Button>
                                <Tooltip title={disabledPdfTooltip}>
                                  <span><Button size="small" variant="outlined" disabled>Download PDF</Button></span>
                                </Tooltip>
                                <Button size="small" variant="outlined" color="inherit" onClick={() => handleCancelPurchaseOrderFromDrawer(po)}>Cancel</Button>
                              </>
                            ) : null}
                            {showClosedActions ? (
                              <>
                                <Button size="small" variant="outlined" onClick={() => void handlePrintPurchaseOrderFromDrawer(po)}>Print</Button>
                                <Tooltip title={disabledPdfTooltip}>
                                  <span><Button size="small" variant="outlined" disabled>Download PDF</Button></span>
                                </Tooltip>
                                <Tooltip title={disabledHistoryTooltip}>
                                  <span><Button size="small" variant="outlined" disabled>Activity History</Button></span>
                                </Tooltip>
                              </>
                            ) : null}
                            {showCancelledActions ? (
                              <Tooltip title={disabledHistoryTooltip}>
                                <span><Button size="small" variant="outlined" disabled>Activity History</Button></span>
                              </Tooltip>
                            ) : null}
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>
                  );
                })}
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

      <Dialog open={quickMedicineOpen} onClose={() => !savingQuickMedicine && setQuickMedicineOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Create new medicine</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ pt: 1 }}>
            <TextField
              size="small"
              label="Medicine name *"
              value={quickMedicineForm.medicineName}
              onChange={(e) => setQuickMedicineForm((current) => ({ ...current, medicineName: e.target.value }))}
              error={Boolean(quickMedicineFieldErrors.medicineName)}
              helperText={quickMedicineFieldErrors.medicineName}
              autoFocus
            />
            <FormControl size="small" fullWidth error={Boolean(quickMedicineFieldErrors.medicineType)}>
              <InputLabel>Medicine type</InputLabel>
              <Select
                label="Medicine type"
                value={quickMedicineForm.medicineType}
                onChange={(e) => setQuickMedicineForm((current) => ({ ...current, medicineType: e.target.value as MedicineType }))}
              >
                {medicineTypeOptions.map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <Grid container spacing={1}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  size="small"
                  label="Generic name"
                  value={quickMedicineForm.genericName || ""}
                  onChange={(e) => setQuickMedicineForm((current) => ({ ...current, genericName: e.target.value || null }))}
                  error={Boolean(quickMedicineFieldErrors.genericName)}
                  helperText={quickMedicineFieldErrors.genericName}
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  size="small"
                  label="Brand name"
                  value={quickMedicineForm.brandName || ""}
                  onChange={(e) => setQuickMedicineForm((current) => ({ ...current, brandName: e.target.value || null }))}
                  error={Boolean(quickMedicineFieldErrors.brandName)}
                  helperText={quickMedicineFieldErrors.brandName}
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  size="small"
                  label="Strength"
                  value={quickMedicineForm.strength || ""}
                  onChange={(e) => setQuickMedicineForm((current) => ({ ...current, strength: e.target.value || null }))}
                  error={Boolean(quickMedicineFieldErrors.strength)}
                  helperText={quickMedicineFieldErrors.strength}
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  size="small"
                  label="Unit"
                  value={quickMedicineForm.unit || ""}
                  onChange={(e) => setQuickMedicineForm((current) => ({ ...current, unit: e.target.value || null }))}
                  error={Boolean(quickMedicineFieldErrors.unit)}
                  helperText={quickMedicineFieldErrors.unit}
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  size="small"
                  label="Category"
                  value={quickMedicineForm.category || ""}
                  onChange={(e) => setQuickMedicineForm((current) => ({ ...current, category: e.target.value || null }))}
                  error={Boolean(quickMedicineFieldErrors.category)}
                  helperText={quickMedicineFieldErrors.category}
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  size="small"
                  label="Manufacturer"
                  value={quickMedicineForm.manufacturer || ""}
                  onChange={(e) => setQuickMedicineForm((current) => ({ ...current, manufacturer: e.target.value || null }))}
                  error={Boolean(quickMedicineFieldErrors.manufacturer)}
                  helperText={quickMedicineFieldErrors.manufacturer}
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  size="small"
                  label="Default price"
                  type="number"
                  value={quickMedicineForm.defaultPrice ?? ""}
                  onChange={(e) => setQuickMedicineForm((current) => ({ ...current, defaultPrice: e.target.value === "" ? null : Number(e.target.value) }))}
                  error={Boolean(quickMedicineFieldErrors.defaultPrice)}
                  helperText={quickMedicineFieldErrors.defaultPrice}
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  size="small"
                  label="Tax rate"
                  type="number"
                  value={quickMedicineForm.taxRate ?? ""}
                  onChange={(e) => setQuickMedicineForm((current) => ({ ...current, taxRate: e.target.value === "" ? null : Number(e.target.value) }))}
                  error={Boolean(quickMedicineFieldErrors.taxRate)}
                  helperText={quickMedicineFieldErrors.taxRate}
                  fullWidth
                />
              </Grid>
            </Grid>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setQuickMedicineOpen(false)} disabled={savingQuickMedicine}>Close</Button>
          <Button variant="contained" onClick={() => void saveQuickMedicine()} disabled={savingQuickMedicine}>
            {savingQuickMedicine ? "Saving..." : "Save medicine"}
          </Button>
        </DialogActions>
      </Dialog>

      {workspace === "supplier-invoices" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <CompactFilterCard
              title="Supplier invoice"
              subtitle={invoiceEditorMode === "create" ? "Create a supplier invoice and match it to an active purchase order." : "View or update supplier invoice details before payment approval."}
              actions={(
                <Stack direction="row" spacing={1}>
                  <input
                    ref={invoiceAttachmentInputRef}
                    type="file"
                    hidden
                    accept="application/pdf,image/png,image/jpeg"
                    onChange={(event) => void handleUploadInvoiceAttachment(event.target.files?.[0] ?? null)}
                  />
                  <Tooltip title={
                    invoiceCanUpload
                      ? "Attach a PDF or image copy of the supplier invoice."
                      : selectedInvoice == null
                        ? "Save the invoice first, then upload the attachment."
                        : "Attachments can only be updated while the invoice is in Draft or Matched status."
                  }>
                    <span>
                      <Button
                        size="small"
                        variant="outlined"
                        disabled={!invoiceCanUpload || uploadingInvoiceAttachment}
                        onClick={() => invoiceAttachmentInputRef.current?.click()}
                      >
                        {uploadingInvoiceAttachment ? "Uploading..." : "Upload invoice document"}
                      </Button>
                    </span>
                  </Tooltip>
                  <Button size="small" variant="outlined" onClick={resetInvoiceForm}>New invoice</Button>
                  <Button size="small" variant="contained" onClick={() => void saveInvoice()} disabled={savingInvoice || !invoicePurchaseOrderOptions.length || invoiceReadOnly}>
                    {savingInvoice ? "Saving..." : selectedInvoiceId ? "Save changes" : "Save invoice"}
                  </Button>
                </Stack>
              )}
            >
              <Stack spacing={1.5}>
                {invoiceSuccess ? <Alert severity="success" onClose={() => setInvoiceSuccess(null)}>{invoiceSuccess}</Alert> : null}
                {invoiceError ? <Alert severity="error" onClose={() => setInvoiceError(null)}>{invoiceError}</Alert> : null}
                {!invoicePurchaseOrderOptions.length ? (
                  <CompactEmptyState
                    title="No purchase orders available for invoice matching."
                    subtitle="Only Generated, Sent, Partially Received, or Received POs can be linked to supplier invoices."
                    action={<Button size="small" variant="contained" onClick={() => updateWorkspace("purchase-orders")}>Create PO</Button>}
                  />
                ) : (
                  <>
                    {selectedInvoice ? (
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip size="small" {...supplierInvoiceStatusLabel(selectedInvoice.status)} />
                        <Chip size="small" {...supplierInvoiceMatchLabel(selectedInvoice.matchingStatus)} />
                        {Math.abs(selectedInvoice.varianceAmount) > 0.004 ? <Chip size="small" color="warning" label={`Variance INR ${selectedInvoice.varianceAmount.toFixed(2)}`} /> : null}
                        {selectedInvoice.attachmentFileName ? <Chip size="small" label={selectedInvoice.attachmentFileName} /> : null}
                        {selectedInvoice.purchaseOrderId ? <Button size="small" variant="outlined" onClick={() => void openPurchaseOrderReference(selectedInvoice.purchaseOrderId)}>Linked PO</Button> : null}
                        {relatedGrnByInvoiceId.get(selectedInvoice.id) ? <Button size="small" variant="outlined" onClick={() => setSelectedGrn(relatedGrnByInvoiceId.get(selectedInvoice.id) ?? null)}>Linked GRN</Button> : null}
                      </Stack>
                    ) : null}
                    {selectedInvoice?.status === "READY_FOR_PAYMENT" || selectedInvoice?.status === "APPROVED_FOR_PAYMENT" ? <Alert severity="info">Ready for Payment invoices are read-only until payment posting is available.</Alert> : null}
                    {selectedInvoice?.status === "PAID" ? <Alert severity="info">Paid invoices are read-only.</Alert> : null}
                    {selectedInvoice?.status === "CANCELLED" ? <Alert severity="warning">Cancelled invoices remain visible for audit and cannot be edited.</Alert> : null}
                    <FormControl size="small" fullWidth error={Boolean(invoiceFieldErrors.purchaseOrderId)}>
                      <InputLabel>Related PO</InputLabel>
                      <Select
                        label="Related PO"
                        value={invoiceForm.purchaseOrderId}
                        onChange={(e) => setInvoiceForm((current) => ({ ...current, purchaseOrderId: e.target.value }))}
                        disabled={invoiceReadOnly}
                      >
                        {invoicePurchaseOrderOptions.map((po) => (
                          <MenuItem key={po.id} value={po.id}>{po.poNumber} • {po.supplierName}</MenuItem>
                        ))}
                      </Select>
                      {invoiceFieldErrors.purchaseOrderId ? <Typography variant="caption" color="error">{invoiceFieldErrors.purchaseOrderId}</Typography> : null}
                    </FormControl>

                    <FormControl size="small" fullWidth error={Boolean(invoiceFieldErrors.supplierId)}>
                      <InputLabel>Supplier</InputLabel>
                      <Select label="Supplier" value={invoiceForm.supplierId} disabled>
                        {suppliers.map((supplier) => (
                          <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>
                        ))}
                      </Select>
                      <Typography variant="caption" color={invoiceFieldErrors.supplierId ? "error" : "text.secondary"}>
                        {invoiceFieldErrors.supplierId || "Auto-populated from the selected purchase order."}
                      </Typography>
                    </FormControl>

                    <TextField
                      size="small"
                      label="Invoice number"
                      value={invoiceForm.invoiceNumber}
                      onChange={(e) => setInvoiceForm((current) => ({ ...current, invoiceNumber: e.target.value }))}
                      disabled={invoiceReadOnly}
                      error={Boolean(invoiceFieldErrors.invoiceNumber)}
                      helperText={invoiceFieldErrors.invoiceNumber}
                    />
                    <TextField
                      size="small"
                      label="Invoice date"
                      type="date"
                      InputLabelProps={{ shrink: true }}
                      value={invoiceForm.invoiceDate}
                      onChange={(e) => setInvoiceForm((current) => ({ ...current, invoiceDate: e.target.value }))}
                      disabled={invoiceReadOnly}
                      error={Boolean(invoiceFieldErrors.invoiceDate)}
                      helperText={invoiceFieldErrors.invoiceDate}
                    />
                    <Grid container spacing={1}>
                      <Grid size={{ xs: 12, sm: 4 }}>
                        <TextField
                          size="small"
                          type="number"
                          fullWidth
                          label="Invoice amount"
                          value={invoiceForm.invoiceAmount}
                          onChange={(e) => setInvoiceForm((current) => ({ ...current, invoiceAmount: e.target.value }))}
                          disabled={invoiceReadOnly}
                          error={Boolean(invoiceFieldErrors.invoiceAmount)}
                          helperText={invoiceFieldErrors.invoiceAmount}
                          inputProps={{ min: 0.01, step: "0.01" }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 4 }}>
                        <TextField
                          size="small"
                          type="number"
                          fullWidth
                          label="GST amount"
                          value={invoiceForm.gstAmount}
                          onChange={(e) => setInvoiceForm((current) => ({ ...current, gstAmount: e.target.value }))}
                          disabled={invoiceReadOnly}
                          error={Boolean(invoiceFieldErrors.gstAmount)}
                          helperText={invoiceFieldErrors.gstAmount}
                          inputProps={{ min: 0, step: "0.01" }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 4 }}>
                        <TextField
                          size="small"
                          type="number"
                          fullWidth
                          label="Discount"
                          value={invoiceForm.discount}
                          onChange={(e) => setInvoiceForm((current) => ({ ...current, discount: e.target.value }))}
                          disabled={invoiceReadOnly}
                          error={Boolean(invoiceFieldErrors.discount)}
                          helperText={invoiceFieldErrors.discount || "Optional."}
                          inputProps={{ min: 0, step: "0.01" }}
                        />
                      </Grid>
                    </Grid>
                    <Autocomplete
                      freeSolo
                      options={[...SUPPLIER_INVOICE_VARIANCE_REASONS]}
                      value={invoiceForm.varianceReason}
                      onInputChange={(_, value) => setInvoiceForm((current) => ({ ...current, varianceReason: value }))}
                      disabled={invoiceReadOnly || invoiceVsPoDifference == null || invoiceVsPoDifference === 0}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          size="small"
                          label={invoiceVsPoDifference == null || invoiceVsPoDifference === 0 ? "Variance reason" : "Variance reason *"}
                          error={Boolean(invoiceFieldErrors.varianceReason)}
                          helperText={
                            invoiceFieldErrors.varianceReason
                            || (invoiceVsPoDifference == null || invoiceVsPoDifference === 0
                              ? "Required only when invoice amount differs from the PO amount."
                              : "Required because invoice amount differs from the PO amount.")
                          }
                        />
                      )}
                    />
                    <TextField
                      size="small"
                      label="Notes"
                      multiline
                      minRows={2}
                      value={invoiceForm.notes}
                      onChange={(e) => setInvoiceForm((current) => ({ ...current, notes: e.target.value }))}
                      disabled={invoiceReadOnly}
                      error={Boolean(invoiceFieldErrors.notes)}
                      helperText={invoiceFieldErrors.notes}
                    />

                    {selectedInvoicePurchaseOrder ? (
                      <Card variant="outlined">
                        <CardContent sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}>
                          <Stack spacing={1}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>PO match summary</Typography>
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <Chip size="small" label={`PO total: INR ${selectedInvoicePoTotal.toFixed(2)}`} />
                              <Chip size="small" label={`Invoice amount: INR ${invoiceComputedPayable.toFixed(2)}`} />
                              <Chip
                                size="small"
                                color={invoiceVsPoDifference === 0 ? "success" : "warning"}
                                label={`Variance: INR ${(invoiceVsPoDifference || 0).toFixed(2)}`}
                              />
                            </Stack>
                            {invoiceVsPoDifference !== 0 && invoiceVsPoDifference != null ? (
                              <Typography variant="caption" color="warning.main">
                                Variance is calculated as Invoice Amount - PO Amount. A variance reason is required.
                              </Typography>
                            ) : null}
                            <Typography variant="caption" color="text.secondary">
                              {selectedInvoiceItems.length} PO items will be linked to this supplier invoice.
                            </Typography>
                            {selectedInvoice?.attachmentFileName ? <Typography variant="caption" color="text.secondary">Attachment: {selectedInvoice.attachmentFileName}</Typography> : null}
                            {selectedInvoice?.cancelReason ? <Typography variant="caption" color="text.secondary">Cancellation reason: {selectedInvoice.cancelReason}</Typography> : null}
                          </Stack>
                        </CardContent>
                      </Card>
                    ) : null}
                  </>
                )}
              </Stack>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            <Stack spacing={2}>
              {selectedInvoicePurchaseOrder ? (
                <CompactFilterCard title="Related PO items" subtitle="PO items linked to the selected supplier invoice.">
                  <TableContainer sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
                    <Table size="small" stickyHeader>
                      <TableHead>
                        <TableRow>
                          <TableCell>Medicine</TableCell>
                          <TableCell align="right">Qty</TableCell>
                          <TableCell align="right">Unit Price</TableCell>
                          <TableCell align="right">GST %</TableCell>
                          <TableCell align="right">Line Total</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {selectedInvoiceItems.map((item, index) => {
                          const lineTotal = computePurchaseOrderLineTotal(item) ?? 0;
                          return (
                            <TableRow key={`${item.medicineId || item.medicineName}-${index}`}>
                              <TableCell>{item.medicineName || "-"}</TableCell>
                              <TableCell align="right">{item.quantity || "0"}</TableCell>
                              <TableCell align="right">INR {Number(item.unitPrice || 0).toFixed(2)}</TableCell>
                              <TableCell align="right">{Number(item.gst || 0).toFixed(2)}</TableCell>
                              <TableCell align="right">INR {lineTotal.toFixed(2)}</TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </CompactFilterCard>
              ) : null}
              <CompactFilterCard
                title="Invoice list"
                subtitle="Draft, matched, Ready for Payment, paid, and cancelled supplier invoices."
                actions={<Button size="small" variant="outlined" onClick={() => {
                  setInvoiceStatusFilter("ALL");
                  setInvoiceSupplierFilter("ALL");
                  setInvoicePoFilter("ALL");
                  setInvoiceDateFromFilter("");
                  setInvoiceDateToFilter("");
                  setInvoiceVarianceOnly(false);
                }}>Clear filters</Button>}
              >
                <Grid container spacing={1} sx={{ mb: 1.5 }}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl size="small" fullWidth>
                      <InputLabel>Status</InputLabel>
                      <Select label="Status" value={invoiceStatusFilter} onChange={(e) => setInvoiceStatusFilter(e.target.value as "ALL" | SupplierInvoiceStatus)}>
                        <MenuItem value="ALL">All</MenuItem>
                        <MenuItem value="DRAFT">Draft</MenuItem>
                        <MenuItem value="MATCHED">Matched</MenuItem>
                        <MenuItem value="READY_FOR_PAYMENT">Ready for Payment</MenuItem>
                        <MenuItem value="PAID">Paid</MenuItem>
                        <MenuItem value="CANCELLED">Cancelled</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl size="small" fullWidth>
                      <InputLabel>Supplier</InputLabel>
                      <Select label="Supplier" value={invoiceSupplierFilter} onChange={(e) => setInvoiceSupplierFilter(e.target.value)}>
                        <MenuItem value="ALL">All</MenuItem>
                        {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl size="small" fullWidth>
                      <InputLabel>Related PO</InputLabel>
                      <Select label="Related PO" value={invoicePoFilter} onChange={(e) => setInvoicePoFilter(e.target.value)}>
                        <MenuItem value="ALL">All</MenuItem>
                        {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField size="small" type="date" label="From" InputLabelProps={{ shrink: true }} value={invoiceDateFromFilter} onChange={(e) => setInvoiceDateFromFilter(e.target.value)} fullWidth />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField size="small" type="date" label="To" InputLabelProps={{ shrink: true }} value={invoiceDateToFilter} onChange={(e) => setInvoiceDateToFilter(e.target.value)} fullWidth />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ height: "100%" }}>
                      <Switch checked={invoiceVarianceOnly} onChange={(e) => setInvoiceVarianceOnly(e.target.checked)} />
                      <Typography variant="body2">Variance only</Typography>
                    </Stack>
                  </Grid>
                </Grid>
                {loadingInvoices ? (
                  <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                    <Typography variant="body2" color="text.secondary">Loading supplier invoices...</Typography>
                  </Box>
                ) : filteredInvoices.length ? (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Invoice number</TableCell>
                        <TableCell>Supplier</TableCell>
                        <TableCell>Related PO</TableCell>
                        <TableCell>Invoice date</TableCell>
                        <TableCell align="right">Amount</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Attachment</TableCell>
                        <TableCell>Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredInvoices.map((invoice) => (
                        <TableRow key={invoice.id}>
                          <TableCell>{invoice.invoiceNumber}</TableCell>
                          <TableCell>{invoice.supplierName || supplierById.get(invoice.supplierId)?.supplierName || "-"}</TableCell>
                          <TableCell>{invoice.purchaseOrderId ? poById.get(invoice.purchaseOrderId)?.poNumber || "-" : "-"}</TableCell>
                          <TableCell>{invoice.invoiceDate}</TableCell>
                          <TableCell align="right">
                            <Stack spacing={0.25} sx={{ alignItems: "flex-end" }}>
                              <Typography variant="body2">INR {invoice.totalAmount.toFixed(2)}</Typography>
                              {Math.abs(invoice.varianceAmount) > 0.004 ? <Typography variant="caption" color="warning.main">Var {invoice.varianceAmount.toFixed(2)}</Typography> : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Chip size="small" {...supplierInvoiceStatusLabel(invoice.status)} />
                              <Chip size="small" {...supplierInvoiceMatchLabel(invoice.matchingStatus)} />
                            </Stack>
                          </TableCell>
                          <TableCell>{invoice.attachmentFileName || "-"}</TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <Button size="small" variant="outlined" onClick={() => loadInvoiceIntoForm(invoice, "view")}>View</Button>
                              {(invoice.status === "DRAFT" || invoice.status === "MATCHED") ? <Button size="small" variant="outlined" onClick={() => loadInvoiceIntoForm(invoice, "edit")}>Edit</Button> : null}
                              {invoice.status === "DRAFT" ? <Button size="small" variant="outlined" onClick={() => void handleMatchInvoice(invoice)}>Match</Button> : null}
                              {invoice.status === "MATCHED" ? <Button size="small" variant="outlined" onClick={() => void handleApproveInvoice(invoice)}>Approve for Payment</Button> : null}
                              {invoice.status === "READY_FOR_PAYMENT" || invoice.status === "APPROVED_FOR_PAYMENT" ? (
                                <Tooltip title="Payment posting will be available after billing/payment integration.">
                                  <span><Button size="small" variant="outlined" disabled>Mark Paid</Button></span>
                                </Tooltip>
                              ) : null}
                              {(invoice.status === "DRAFT" || invoice.status === "MATCHED" || invoice.status === "READY_FOR_PAYMENT" || invoice.status === "APPROVED_FOR_PAYMENT") ? <Button size="small" variant="outlined" color="inherit" onClick={() => handleOpenCancelInvoice(invoice)}>Cancel</Button> : null}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : (
                  <CompactEmptyState title="No supplier invoices" subtitle="No invoices match the current filters." />
                )}
              </CompactFilterCard>
            </Stack>
          </Grid>
        </Grid>
      ) : null}

      <Dialog open={invoiceCancelDialogOpen} onClose={() => setInvoiceCancelDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Cancel supplier invoice</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">Cancelled invoices remain visible in filters and audit history.</Typography>
            <TextField
              label="Cancellation reason *"
              value={invoiceCancelReason}
              onChange={(e) => setInvoiceCancelReason(e.target.value)}
              multiline
              minRows={3}
              fullWidth
              error={Boolean(invoiceCancelError)}
              helperText={invoiceCancelError}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setInvoiceCancelDialogOpen(false)}>Close</Button>
          <Button variant="contained" color="error" onClick={() => void handleConfirmCancelInvoice()}>Cancel invoice</Button>
        </DialogActions>
      </Dialog>

      {workspace === "goods-receipt" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12 }}>
            <CompactFilterCard
              title="Goods receipt"
              subtitle="Select an eligible purchase order, capture received line items, and post inventory in one step."
              actions={(
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={resetGrnForm}>Clear</Button>
                  <Button size="small" variant="contained" onClick={() => void saveGrn()} disabled={savingGrn || !eligibleGoodsReceiptPurchaseOrders.length}>
                    {savingGrn ? "Posting..." : "Post GRN"}
                  </Button>
                </Stack>
              )}
            >
              <Stack spacing={1.5}>
                {grnSuccess ? <Alert severity="success" onClose={() => setGrnSuccess(null)}>{grnSuccess}</Alert> : null}
                {grnError ? <Alert severity="error" onClose={() => setGrnError(null)}>{grnError}</Alert> : null}
                {!eligibleGoodsReceiptPurchaseOrders.length ? (
                  <CompactEmptyState
                    title="No purchase orders available for goods receipt."
                    subtitle="Only Generated, Sent, or Partially Received POs can be received."
                    action={<Button size="small" variant="contained" onClick={() => updateWorkspace("purchase-orders")}>Create PO</Button>}
                  />
                ) : (
                  <>
                    <Grid container spacing={1}>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <FormControl size="small" fullWidth error={Boolean(grnFieldErrors.purchaseOrderId)}>
                          <InputLabel>Purchase order</InputLabel>
                          <Select
                            label="Purchase order"
                            value={grnForm.purchaseOrderId}
                            onChange={(e) => syncGrnFromPurchaseOrder(e.target.value)}
                          >
                            {eligibleGoodsReceiptPurchaseOrders.map((po) => (
                              <MenuItem key={po.id} value={po.id}>{po.poNumber} • {po.supplierName}</MenuItem>
                            ))}
                          </Select>
                          {grnFieldErrors.purchaseOrderId ? <Typography variant="caption" color="error">{grnFieldErrors.purchaseOrderId}</Typography> : null}
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 12, md: 3 }}>
                        <TextField
                          size="small"
                          label="GRN number"
                          value={grnForm.receiptNumber}
                          onChange={(e) => setGrnForm((current) => ({ ...current, receiptNumber: e.target.value }))}
                          placeholder="Auto-generated on post"
                          fullWidth
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 2.5 }}>
                        <TextField
                          size="small"
                          label="GRN date"
                          type="date"
                          InputLabelProps={{ shrink: true }}
                          value={grnForm.receivedAt}
                          onChange={(e) => setGrnForm((current) => ({ ...current, receivedAt: e.target.value }))}
                          error={Boolean(grnFieldErrors.receivedAt)}
                          helperText={grnFieldErrors.receivedAt}
                          fullWidth
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 2.5 }}>
                        <TextField size="small" label="Supplier" value={selectedGrnPurchaseOrder?.supplierName || ""} InputProps={{ readOnly: true }} fullWidth />
                      </Grid>
                    </Grid>

                    {selectedGrnPurchaseOrder ? (
                      <>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Chip size="small" label={`PO ${grnForm.poNumber || selectedGrnPurchaseOrder.poNumber}`} />
                          <Chip size="small" label={`PO date ${grnForm.poDate || selectedGrnPurchaseOrder.orderDate}`} />
                          <Chip size="small" color="info" label={`Lines ${grnForm.lines.filter((line) => line.pendingQty > 0).length}`} />
                          <Chip size="small" color="success" label={`Receive qty ${grnForm.lines.reduce((sum, line) => sum + numberFromUnknown(line.receiveQty), 0)}`} />
                        </Stack>

                        {grnFieldErrors.lines ? <Alert severity="error">{grnFieldErrors.lines}</Alert> : null}

                        <TableContainer sx={{ maxHeight: 420, overflowX: "auto", border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
                          <Table size="small" stickyHeader sx={{ minWidth: 1280 }}>
                            <TableHead>
                              <TableRow>
                                <TableCell sx={{ minWidth: 220 }}>Medicine</TableCell>
                                <TableCell align="right">Ordered Qty</TableCell>
                                <TableCell align="right">Already Received</TableCell>
                                <TableCell align="right">Pending Qty</TableCell>
                                <TableCell align="right">Receive Qty</TableCell>
                                <TableCell>Batch No</TableCell>
                                <TableCell>Expiry Date</TableCell>
                                <TableCell>Location</TableCell>
                                <TableCell>Remarks</TableCell>
                                <TableCell>Line Status</TableCell>
                              </TableRow>
                            </TableHead>
                            <TableBody>
                              {grnForm.lines.map((line, index) => (
                                <TableRow key={`${line.medicineId}-${index}`} hover>
                                  <TableCell>
                                    <Stack spacing={0.25}>
                                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{line.medicineName || "-"}</Typography>
                                      <Typography variant="caption" color="text.secondary">{line.unit || "Unit not set"}</Typography>
                                    </Stack>
                                  </TableCell>
                                  <TableCell align="right">{line.orderedQty}</TableCell>
                                  <TableCell align="right">{line.alreadyReceivedQty}</TableCell>
                                  <TableCell align="right">{line.pendingQty}</TableCell>
                                  <TableCell align="right">
                                    <TextField
                                      size="small"
                                      type="number"
                                      value={line.receiveQty}
                                      onChange={(e) => updateGrnLine(index, { receiveQty: e.target.value })}
                                      error={Boolean(grnFieldErrors[`lines.${index}.receiveQty`])}
                                      helperText={grnFieldErrors[`lines.${index}.receiveQty`]}
                                      sx={{ width: 100, ...poMoneyFieldSx }}
                                      inputProps={{ min: 0, max: line.pendingQty, step: 1 }}
                                    />
                                  </TableCell>
                                  <TableCell>
                                    <TextField
                                      size="small"
                                      value={line.batchNumber}
                                      onChange={(e) => updateGrnLine(index, { batchNumber: e.target.value })}
                                      error={Boolean(grnFieldErrors[`lines.${index}.batchNumber`])}
                                      helperText={grnFieldErrors[`lines.${index}.batchNumber`]}
                                    />
                                  </TableCell>
                                  <TableCell>
                                    <TextField
                                      size="small"
                                      type="date"
                                      InputLabelProps={{ shrink: true }}
                                      value={line.expiryDate}
                                      onChange={(e) => updateGrnLine(index, { expiryDate: e.target.value })}
                                      error={Boolean(grnFieldErrors[`lines.${index}.expiryDate`])}
                                      helperText={grnFieldErrors[`lines.${index}.expiryDate`]}
                                    />
                                  </TableCell>
                                  <TableCell>
                                    <FormControl size="small" error={Boolean(grnFieldErrors[`lines.${index}.locationId`])} sx={{ minWidth: 160 }}>
                                      <Select
                                        displayEmpty
                                        value={line.locationId}
                                        onChange={(e) => updateGrnLine(index, { locationId: e.target.value })}
                                      >
                                        <MenuItem value="">Select location</MenuItem>
                                        {inventoryLocations.map((location) => (
                                          <MenuItem key={location.id} value={location.id}>{location.locationName}</MenuItem>
                                        ))}
                                      </Select>
                                    </FormControl>
                                    {grnFieldErrors[`lines.${index}.locationId`] ? <Typography variant="caption" color="error">{grnFieldErrors[`lines.${index}.locationId`]}</Typography> : null}
                                  </TableCell>
                                  <TableCell>
                                    <TextField
                                      size="small"
                                      value={line.remarks}
                                      onChange={(e) => updateGrnLine(index, { remarks: e.target.value })}
                                      placeholder="Optional"
                                    />
                                  </TableCell>
                                  <TableCell>
                                    <Chip size="small" label={line.lineStatus} color={line.lineStatus === "Complete" ? "success" : line.lineStatus === "Partial" ? "info" : "default"} variant="outlined" />
                                  </TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </TableContainer>
                      </>
                    ) : (
                      <CompactEmptyState title="Select a purchase order to load GRN items." subtitle="Ordered quantity, already received quantity, pending quantity, and required stock fields will appear here." />
                    )}
                  </>
                )}
              </Stack>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <CompactFilterCard title="GRN list" subtitle="Posted goods receipts update inventory automatically and keep PO receipt progress in sync.">
              {loadingGrns ? (
                <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                  <Typography variant="body2" color="text.secondary">Loading goods receipts...</Typography>
                </Box>
              ) : grns.length ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>GRN number</TableCell>
                      <TableCell>PO number</TableCell>
                      <TableCell>Supplier</TableCell>
                      <TableCell>GRN date</TableCell>
                      <TableCell align="right">Items received</TableCell>
                      <TableCell align="right">Total qty</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Action</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {grns.map((grn) => (
                      <TableRow key={grn.id}>
                        <TableCell>
                          <Button size="small" onClick={() => setSelectedGrn(grn)} sx={{ px: 0, minWidth: 0 }}>{grn.receiptNumber}</Button>
                        </TableCell>
                        <TableCell>
                          {grn.purchaseOrderId ? <Button size="small" onClick={() => void openPurchaseOrderReference(grn.purchaseOrderId)} sx={{ px: 0, minWidth: 0 }}>{grn.poNumber || poById.get(grn.purchaseOrderId)?.poNumber || "-"}</Button> : "-"}
                        </TableCell>
                        <TableCell>{grn.supplierName || supplierById.get(grn.supplierId)?.supplierName || "-"}</TableCell>
                        <TableCell>{grn.receivedAt ? grn.receivedAt.slice(0, 10) : "-"}</TableCell>
                        <TableCell align="right">{grn.itemsReceived}</TableCell>
                        <TableCell align="right">{grn.totalReceivedQty}</TableCell>
                        <TableCell><Chip size="small" {...grnStatusChipProps(grn.status)} /></TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end" useFlexGap flexWrap="wrap">
                            <Button size="small" onClick={() => setSelectedGrn(grn)}>View GRN</Button>
                            {grn.status === "Posted" ? <Button size="small" variant="outlined" onClick={() => openInventoryForReference(grn.receiptNumber)}>View Inventory</Button> : null}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <CompactEmptyState title="No goods receipts" subtitle="Post a GRN to update inventory and supplier purchase progress." />
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      <Dialog open={Boolean(selectedGrn)} onClose={() => setSelectedGrn(null)} fullWidth maxWidth="lg">
        <DialogTitle>Goods Receipt Detail</DialogTitle>
        <DialogContent dividers>
          {selectedGrn ? (
            <Stack spacing={1.5}>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Chip size="small" {...grnStatusChipProps(selectedGrn.status)} />
                {selectedGrn.purchaseOrderId ? <Button size="small" variant="outlined" onClick={() => void openPurchaseOrderReference(selectedGrn.purchaseOrderId)}>Linked PO</Button> : null}
                {selectedGrnInvoice ? <Button size="small" variant="outlined" onClick={() => openInvoiceReference(selectedGrnInvoice.id)}>Linked Invoice</Button> : null}
                {selectedGrn.status === "Posted" ? <Button size="small" variant="outlined" onClick={() => openInventoryForReference(selectedGrn.receiptNumber)}>View Inventory</Button> : null}
              </Stack>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Supplier" value={selectedGrn.supplierName} InputProps={{ readOnly: true }} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="PO number" value={selectedGrn.poNumber || "-"} InputProps={{ readOnly: true }} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="GRN number" value={selectedGrn.receiptNumber} InputProps={{ readOnly: true }} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="GRN date" value={selectedGrn.receivedAt ? selectedGrn.receivedAt.slice(0, 10) : ""} InputProps={{ readOnly: true }} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Status" value={selectedGrn.status} InputProps={{ readOnly: true }} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Linked invoice" value={selectedGrnInvoice?.invoiceNumber || "-"} InputProps={{ readOnly: true }} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Received by" value="-" InputProps={{ readOnly: true }} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Posted by" value={selectedGrn.confirmedAt ? "System" : "-"} InputProps={{ readOnly: true }} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Created" value={selectedGrn.createdAt ? new Date(selectedGrn.createdAt).toLocaleString() : "-"} InputProps={{ readOnly: true }} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField size="small" label="Posted at" value={selectedGrn.confirmedAt ? new Date(selectedGrn.confirmedAt).toLocaleString() : "-"} InputProps={{ readOnly: true }} fullWidth />
                </Grid>
              </Grid>
              <CompactFilterCard title="Timeline" subtitle="GRN posting milestones remain read-only for audit.">
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Chip size="small" label={`Created ${selectedGrn.createdAt ? new Date(selectedGrn.createdAt).toLocaleString() : "-"}`} variant="outlined" />
                  <Chip size="small" label={`Posted ${selectedGrn.confirmedAt ? new Date(selectedGrn.confirmedAt).toLocaleString() : "Pending"}`} color={selectedGrn.confirmedAt ? "success" : "default"} variant="outlined" />
                  <Chip size="small" label={`Inventory Updated ${selectedGrn.confirmedAt ? new Date(selectedGrn.confirmedAt).toLocaleString() : "Pending"}`} color={selectedGrn.confirmedAt ? "success" : "default"} variant="outlined" />
                </Stack>
              </CompactFilterCard>
              <TableContainer sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Medicine</TableCell>
                      <TableCell align="right">Ordered Qty</TableCell>
                      <TableCell align="right">Received Qty</TableCell>
                      <TableCell>Batch</TableCell>
                      <TableCell>Expiry</TableCell>
                      <TableCell>Location</TableCell>
                      <TableCell>Remarks</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {selectedGrn.lines.map((line, index) => (
                      <TableRow key={`${line.medicineId}-${index}`}>
                        <TableCell>{line.medicineName || "-"}</TableCell>
                        <TableCell align="right">{line.orderedQty}</TableCell>
                        <TableCell align="right">{numberFromUnknown(line.receiveQty)}</TableCell>
                        <TableCell>{line.batchNumber || "-"}</TableCell>
                        <TableCell>{line.expiryDate || "-"}</TableCell>
                        <TableCell>{line.locationName || locationById.get(line.locationId)?.locationName || "-"}</TableCell>
                        <TableCell>{line.remarks || "-"}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Tooltip title="Reverse GRN will be available after approval workflow setup.">
            <span><Button disabled>Reverse GRN</Button></span>
          </Tooltip>
          <Button onClick={() => setSelectedGrn(null)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
