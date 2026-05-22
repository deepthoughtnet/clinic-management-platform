import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Drawer,
  Grid,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import AttachFileRoundedIcon from "@mui/icons-material/AttachFileRounded";
import CameraAltRoundedIcon from "@mui/icons-material/CameraAltRounded";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import HistoryRoundedIcon from "@mui/icons-material/HistoryRounded";
import LocalPrintshopOutlinedIcon from "@mui/icons-material/LocalPrintshopOutlined";
import PauseCircleOutlineRoundedIcon from "@mui/icons-material/PauseCircleOutlineRounded";
import PointOfSaleRoundedIcon from "@mui/icons-material/PointOfSaleRounded";
import PreviewRoundedIcon from "@mui/icons-material/PreviewRounded";
import RestartAltRoundedIcon from "@mui/icons-material/RestartAltRounded";
import ShoppingCartCheckoutRoundedIcon from "@mui/icons-material/ShoppingCartCheckoutRounded";
import VisibilityRoundedIcon from "@mui/icons-material/VisibilityRounded";
import {
  addPharmacyPosPayment,
  createPharmacyPosSale,
  getPharmacyPosAvailableBatches,
  getPharmacyPosPrescriptionDownloadUrl,
  getPharmacyPosReceiptPdf,
  listPharmacyPosSales,
  returnPharmacyPosSale,
  searchPatients,
  searchPharmacyPosMedicines,
  uploadPharmacyPosPrescription,
  type Patient,
  type PaymentMode,
  type PharmacyPosBatch,
  type PharmacyPosMedicine,
  type PharmacyPosPrescriptionUpload,
  type PharmacyPosSale,
} from "../../api/clinicApi";
import { useAuth } from "../../auth/useAuth";

type CartLine = {
  medicineId: string;
  medicineName: string;
  quantity: string;
  unitPrice: string;
  discount: string;
  taxRate: string;
  availableQuantity: number;
  earliestExpiryDate: string | null;
};

type HeldDraft = {
  cart: CartLine[];
  customerName: string;
  customerMobile: string;
  notes: string;
  paidAmount: string;
  paymentMode: PaymentMode;
  paymentReference: string;
  patientId: string | null;
  patientLabel: string | null;
  prescription: PharmacyPosPrescriptionUpload | null;
};

type ReturnLineDraft = {
  selected: boolean;
  quantity: string;
  reusable: boolean;
};

const PAYMENT_MODES: PaymentMode[] = ["CASH", "UPI", "CARD", "PHONEPE", "GOOGLE_PAY", "PAYTM", "BANK_TRANSFER", "CHEQUE", "OTHER"];
const POS_ROLES = new Set(["CLINIC_ADMIN", "PHARMACIST", "PHARMACY", "PHARMA"]);
const HELD_CART_STORAGE_KEY = "pharmacy-pos-held-cart";
const STICKY_TOP = 88;

const panelSx = {
  border: "1px solid",
  borderColor: "divider",
  borderRadius: 2,
  bgcolor: "background.paper",
  p: 2,
};

function money(value: number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(value);
}

function numeric(value: string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function lineGross(line: CartLine) {
  return numeric(line.quantity) * numeric(line.unitPrice);
}

function lineTaxAmount(line: CartLine) {
  const taxable = Math.max(0, lineGross(line) - numeric(line.discount));
  return taxable * (numeric(line.taxRate) / 100);
}

function lineTotal(line: CartLine) {
  return Math.max(0, lineGross(line) - numeric(line.discount) + lineTaxAmount(line));
}

function openPdf(blob: Blob) {
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener,noreferrer");
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function saleDisplayName(sale: PharmacyPosSale) {
  return sale.patientName ?? sale.customerName ?? "Walk-in";
}

function compactBatchPreview(batches: PharmacyPosBatch[] | undefined) {
  if (!batches?.length) return "FEFO allocation auto-selects the earliest non-expired batch.";
  return `FEFO preview: ${batches
    .slice(0, 2)
    .map((batch) => `${batch.batchNumber ?? "NA"} ${batch.expiryDate ?? "NA"} (${batch.availableQuantity})`)
    .join(" | ")}`;
}

function isImageFile(name: string | null | undefined, mediaType?: string | null) {
  if (mediaType?.startsWith("image/")) return true;
  const value = (name || "").toLowerCase();
  return value.endsWith(".jpg") || value.endsWith(".jpeg") || value.endsWith(".png") || value.endsWith(".webp");
}

function expiryWarning(expiryDate: string | null) {
  if (!expiryDate) return null;
  const expiry = new Date(expiryDate);
  const now = new Date();
  expiry.setHours(0, 0, 0, 0);
  now.setHours(0, 0, 0, 0);
  const diffDays = Math.ceil((expiry.getTime() - now.getTime()) / 86_400_000);
  if (diffDays < 0) {
    return { label: "Expired batch excluded from sale.", tone: "error" as const };
  }
  if (diffDays <= 30) {
    return { label: `Expires in ${diffDays} day${diffDays === 1 ? "" : "s"}.`, tone: "warning" as const };
  }
  if (diffDays <= 60) {
    return { label: `Expires within 60 days (${diffDays} left).`, tone: "warning" as const };
  }
  if (diffDays <= 90) {
    return { label: `Expires within 90 days (${diffDays} left).`, tone: "warning" as const };
  }
  return null;
}

function saleUsesMultipleBatches(sale: PharmacyPosSale) {
  const counts = new Map<string, number>();
  sale.items.forEach((item) => {
    counts.set(item.medicineId, (counts.get(item.medicineId) || 0) + 1);
  });
  return Array.from(counts.values()).some((count) => count > 1);
}

function returnableQuantity(item: PharmacyPosSale["items"][number]) {
  return Math.max(0, item.quantity - item.returnedQuantity);
}

function proratedRefundAmount(item: PharmacyPosSale["items"][number], quantity: number) {
  if (item.quantity <= 0 || quantity <= 0) return 0;
  return (item.lineTotal / item.quantity) * quantity;
}

function buildReturnDraft(sale: PharmacyPosSale | null) {
  const next: Record<string, ReturnLineDraft> = {};
  sale?.items.forEach((item) => {
    const remaining = returnableQuantity(item);
    next[item.id] = {
      selected: false,
      quantity: remaining > 0 ? "1" : "0",
      reusable: true,
    };
  });
  return next;
}

export default function PharmacyPosPage() {
  const auth = useAuth();
  const token = auth.accessToken;
  const tenantId = auth.tenantId;
  const canAccessPos = POS_ROLES.has((auth.tenantRole || "").toUpperCase());

  const searchInputRef = React.useRef<HTMLInputElement | null>(null);
  const prescriptionInputRef = React.useRef<HTMLInputElement | null>(null);
  const cameraVideoRef = React.useRef<HTMLVideoElement | null>(null);
  const cameraCanvasRef = React.useRef<HTMLCanvasElement | null>(null);
  const cameraStreamRef = React.useRef<MediaStream | null>(null);

  const [loading, setLoading] = React.useState(true);
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [recentDrawerOpen, setRecentDrawerOpen] = React.useState(false);
  const [scanDialogOpen, setScanDialogOpen] = React.useState(false);
  const [scanError, setScanError] = React.useState<string | null>(null);
  const [capturedImageUrl, setCapturedImageUrl] = React.useState<string | null>(null);
  const [capturedImageBlob, setCapturedImageBlob] = React.useState<Blob | null>(null);

  const [medicineQuery, setMedicineQuery] = React.useState("");
  const [medicineResults, setMedicineResults] = React.useState<PharmacyPosMedicine[]>([]);
  const [batchPreview, setBatchPreview] = React.useState<Record<string, PharmacyPosBatch[]>>({});
  const [cart, setCart] = React.useState<CartLine[]>([]);
  const [heldDraft, setHeldDraft] = React.useState<HeldDraft | null>(null);

  const [patientQuery, setPatientQuery] = React.useState("");
  const [patientResults, setPatientResults] = React.useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = React.useState<Patient | null>(null);
  const [selectedCartMedicineId, setSelectedCartMedicineId] = React.useState<string | null>(null);
  const [customerName, setCustomerName] = React.useState("");
  const [customerMobile, setCustomerMobile] = React.useState("");
  const [prescription, setPrescription] = React.useState<PharmacyPosPrescriptionUpload | null>(null);
  const [prescriptionPreviewUrl, setPrescriptionPreviewUrl] = React.useState<string | null>(null);
  const [paidAmount, setPaidAmount] = React.useState("");
  const [paymentMode, setPaymentMode] = React.useState<PaymentMode>("CASH");
  const [paymentReference, setPaymentReference] = React.useState("");
  const [notes, setNotes] = React.useState("");

  const [sales, setSales] = React.useState<PharmacyPosSale[]>([]);
  const [selectedSaleId, setSelectedSaleId] = React.useState<string | null>(null);
  const [selectedSale, setSelectedSale] = React.useState<PharmacyPosSale | null>(null);

  const [paymentAmount, setPaymentAmount] = React.useState("");
  const paidAmountInputRef = React.useRef<HTMLInputElement | null>(null);
  const [paymentTopupMode, setPaymentTopupMode] = React.useState<PaymentMode>("CASH");
  const [paymentReferenceTopup, setPaymentReferenceTopup] = React.useState("");
  const [saleSearchQuery, setSaleSearchQuery] = React.useState("");
  const [returnDraft, setReturnDraft] = React.useState<Record<string, ReturnLineDraft>>({});
  const [returnReason, setReturnReason] = React.useState("");
  const [returnMode, setReturnMode] = React.useState<PaymentMode>("CASH");
  const [returnReference, setReturnReference] = React.useState("");

  const subtotal = React.useMemo(() => cart.reduce((sum, line) => sum + lineGross(line), 0), [cart]);
  const discountTotal = React.useMemo(() => cart.reduce((sum, line) => sum + numeric(line.discount), 0), [cart]);
  const taxTotal = React.useMemo(() => cart.reduce((sum, line) => sum + lineTaxAmount(line), 0), [cart]);
  const total = React.useMemo(() => cart.reduce((sum, line) => sum + lineTotal(line), 0), [cart]);
  const duePreview = Math.max(0, total - numeric(paidAmount));
  const cartHasStockIssue = cart.some((line) => numeric(line.quantity) > line.availableQuantity);
  const filteredSales = React.useMemo(() => {
    const term = saleSearchQuery.trim().toLowerCase();
    if (!term) return sales;
    return sales.filter((sale) =>
      sale.saleNumber.toLowerCase().includes(term)
      || saleDisplayName(sale).toLowerCase().includes(term)
      || (sale.customerMobile || "").toLowerCase().includes(term),
    );
  }, [saleSearchQuery, sales]);
  const selectedReturnRows = React.useMemo(() => {
    if (!selectedSale) return [];
    return selectedSale.items
      .map((item) => ({ item, draft: returnDraft[item.id] }))
      .filter((entry) => entry.draft?.selected);
  }, [returnDraft, selectedSale]);
  const refundEstimate = React.useMemo(() =>
    selectedReturnRows.reduce((sum, entry) => {
      const qty = Math.min(returnableQuantity(entry.item), Math.max(0, numeric(entry.draft.quantity)));
      return sum + proratedRefundAmount(entry.item, qty);
    }, 0), [selectedReturnRows]);
  const reusableSelectedCount = React.useMemo(() =>
    selectedReturnRows.filter((entry) => entry.draft.reusable).length, [selectedReturnRows]);
  const discardSelectedCount = selectedReturnRows.length - reusableSelectedCount;

  const clearDraft = React.useCallback(() => {
    setCart([]);
    setSelectedCartMedicineId(null);
    setCustomerName("");
    setCustomerMobile("");
    setPaidAmount("");
    setPaymentReference("");
    setNotes("");
    setPatientQuery("");
    setPatientResults([]);
    setSelectedPatient(null);
    setPrescription(null);
    window.setTimeout(() => searchInputRef.current?.focus(), 0);
  }, []);

  const refreshSales = React.useCallback(async () => {
    if (!token || !tenantId || !canAccessPos) return;
    const saleRows = await listPharmacyPosSales(token, tenantId);
    setSales(saleRows);
    setSelectedSale((current) => {
      if (!current) return saleRows[0] ?? null;
      return saleRows.find((row) => row.id === current.id) ?? saleRows[0] ?? null;
    });
    setSelectedSaleId((current) => current ?? saleRows[0]?.id ?? null);
  }, [canAccessPos, tenantId, token]);

  React.useEffect(() => {
    searchInputRef.current?.focus();
    try {
      const raw = window.sessionStorage.getItem(HELD_CART_STORAGE_KEY);
      if (raw) setHeldDraft(JSON.parse(raw) as HeldDraft);
    } catch {
      window.sessionStorage.removeItem(HELD_CART_STORAGE_KEY);
    }
  }, []);

  const stopCameraStream = React.useCallback(() => {
    if (cameraStreamRef.current) {
      cameraStreamRef.current.getTracks().forEach((track) => track.stop());
      cameraStreamRef.current = null;
    }
    if (cameraVideoRef.current) {
      cameraVideoRef.current.srcObject = null;
    }
  }, []);

  const resetCapturedImage = React.useCallback(() => {
    setCapturedImageBlob(null);
    setCapturedImageUrl((current) => {
      if (current) {
        URL.revokeObjectURL(current);
      }
      return null;
    });
  }, []);

  const startCameraStream = React.useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setScanError("No camera available on this device. Please upload prescription file instead.");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: "environment" },
        },
        audio: false,
      });
      cameraStreamRef.current = stream;
      if (cameraVideoRef.current) {
        cameraVideoRef.current.srcObject = stream;
        await cameraVideoRef.current.play();
      }
      setScanError(null);
    } catch (err) {
      if (err instanceof DOMException && (err.name === "NotAllowedError" || err.name === "SecurityError")) {
        setScanError("Camera access denied. Please upload prescription file instead.");
        return;
      }
      if (err instanceof DOMException && (err.name === "NotFoundError" || err.name === "OverconstrainedError" || err.name === "DevicesNotFoundError")) {
        setScanError("No camera available on this device. Please upload prescription file instead.");
        return;
      }
      setScanError("Unable to start camera. Please upload prescription file instead.");
    }
  }, []);

  React.useEffect(() => {
    if (!scanDialogOpen) {
      stopCameraStream();
      return;
    }
    setScanError(null);
    resetCapturedImage();
    void startCameraStream();
    return () => {
      stopCameraStream();
    };
  }, [resetCapturedImage, scanDialogOpen, startCameraStream, stopCameraStream]);

  React.useEffect(() => () => {
    stopCameraStream();
    setCapturedImageUrl((current) => {
      if (current) {
        URL.revokeObjectURL(current);
      }
      return null;
    });
  }, [stopCameraStream]);

  React.useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!token || !tenantId || !canAccessPos) {
        setLoading(false);
        return;
      }
      try {
        setLoading(true);
        const saleRows = await listPharmacyPosSales(token, tenantId);
        if (cancelled) return;
        setSales(saleRows);
        setSelectedSale(saleRows[0] ?? null);
        setSelectedSaleId(saleRows[0]?.id ?? null);
        setError(null);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load Pharmacy POS");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [canAccessPos, tenantId, token]);

  React.useEffect(() => {
    if (!token || !tenantId || !canAccessPos) return;
    const handle = window.setTimeout(async () => {
      try {
        const rows = await searchPharmacyPosMedicines(token, tenantId, medicineQuery);
        setMedicineResults(rows);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to search medicines");
      }
    }, 180);
    return () => window.clearTimeout(handle);
  }, [canAccessPos, medicineQuery, tenantId, token]);

  React.useEffect(() => {
    if (!token || !tenantId || !canAccessPos || patientQuery.trim().length < 2) {
      setPatientResults([]);
      return;
    }
    const handle = window.setTimeout(async () => {
      try {
        const rows = await searchPatients(token, tenantId, { name: patientQuery.trim(), active: true });
        setPatientResults(rows.slice(0, 6));
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to search patients");
      }
    }, 200);
    return () => window.clearTimeout(handle);
  }, [canAccessPos, patientQuery, tenantId, token]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadPrescriptionPreview() {
      if (!prescription?.documentId || !token || !tenantId || !isImageFile(prescription.fileName, prescription.mediaType)) {
        setPrescriptionPreviewUrl(null);
        return;
      }
      try {
        const { url } = await getPharmacyPosPrescriptionDownloadUrl(token, tenantId, prescription.documentId);
        if (!cancelled) {
          setPrescriptionPreviewUrl(url);
        }
      } catch {
        if (!cancelled) {
          setPrescriptionPreviewUrl(null);
        }
      }
    }
    void loadPrescriptionPreview();
    return () => {
      cancelled = true;
    };
  }, [prescription, tenantId, token]);

  React.useEffect(() => {
    setReturnDraft(buildReturnDraft(selectedSale));
  }, [selectedSale]);

  const addMedicine = React.useCallback(async (medicine: PharmacyPosMedicine) => {
    setCart((current) => {
      const existing = current.find((line) => line.medicineId === medicine.medicineId);
      if (existing) {
        return current.map((line) =>
          line.medicineId === medicine.medicineId
            ? { ...line, quantity: String(numeric(line.quantity) + 1) }
            : line,
        );
      }
      return [
        {
          medicineId: medicine.medicineId,
          medicineName: medicine.medicineName,
          quantity: "1",
          unitPrice: String(medicine.defaultUnitPrice ?? 0),
          discount: "0",
          taxRate: String(medicine.taxRate ?? 0),
          availableQuantity: medicine.totalAvailableQuantity,
          earliestExpiryDate: medicine.earliestExpiryDate,
        },
        ...current,
      ];
    });
    setSelectedCartMedicineId(medicine.medicineId);
    setMedicineQuery("");
    window.setTimeout(() => searchInputRef.current?.focus(), 0);
    if (batchPreview[medicine.medicineId] || !token || !tenantId) return;
    try {
      const rows = await getPharmacyPosAvailableBatches(token, tenantId, medicine.medicineId);
      setBatchPreview((current) => ({ ...current, [medicine.medicineId]: rows }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load batches");
    }
  }, [batchPreview, tenantId, token]);

  const updateCartLine = React.useCallback((medicineId: string, patch: Partial<CartLine>) => {
    setSelectedCartMedicineId(medicineId);
    setCart((current) => current.map((line) => (line.medicineId === medicineId ? { ...line, ...patch } : line)));
  }, []);

  const removeCartLine = React.useCallback((medicineId: string) => {
    setCart((current) => {
      const next = current.filter((line) => line.medicineId !== medicineId);
      if (selectedCartMedicineId === medicineId) {
        setSelectedCartMedicineId(next[0]?.medicineId ?? null);
      }
      return next;
    });
  }, [selectedCartMedicineId]);

  const selectSale = React.useCallback((sale: PharmacyPosSale) => {
    setSelectedSaleId(sale.id);
    setSelectedSale(sale);
    setPaymentAmount(sale.dueAmount > 0 ? String(sale.dueAmount) : "");
    setPaymentTopupMode("CASH");
    setReturnDraft(buildReturnDraft(sale));
    setReturnMode("CASH");
    setReturnReason("");
    setReturnReference("");
    setRecentDrawerOpen(false);
  }, []);

  const holdCurrentCart = React.useCallback(() => {
    if (!cart.length) {
      if (!heldDraft) {
        setError("There is no active cart to hold.");
        return;
      }
      const restoredPatient = heldDraft.patientId
        ? {
            id: heldDraft.patientId,
            tenantId: tenantId ?? "",
            patientNumber: "",
            firstName: heldDraft.patientLabel ?? "Patient",
            lastName: "",
            gender: "UNKNOWN",
            dateOfBirth: null,
            ageYears: null,
            mobile: "",
            email: null,
            addressLine1: null,
            addressLine2: null,
            city: null,
            state: null,
            country: null,
            postalCode: null,
            emergencyContactName: null,
            emergencyContactMobile: null,
            bloodGroup: null,
            allergies: null,
            existingConditions: null,
            longTermMedications: null,
            surgicalHistory: null,
            notes: null,
            active: true,
            createdAt: "",
            updatedAt: "",
          } as Patient
        : null;
      setCart(heldDraft.cart);
      setSelectedCartMedicineId(heldDraft.cart[0]?.medicineId ?? null);
      setCustomerName(heldDraft.customerName);
      setCustomerMobile(heldDraft.customerMobile);
      setNotes(heldDraft.notes);
      setPaidAmount(heldDraft.paidAmount);
      setPaymentMode(heldDraft.paymentMode);
      setPaymentReference(heldDraft.paymentReference);
      setSelectedPatient(restoredPatient);
      setPrescription(heldDraft.prescription);
      setHeldDraft(null);
      window.sessionStorage.removeItem(HELD_CART_STORAGE_KEY);
      setSuccess("Held cart restored.");
      window.setTimeout(() => searchInputRef.current?.focus(), 0);
      return;
    }
    const draft: HeldDraft = {
      cart,
      customerName,
      customerMobile,
      notes,
      paidAmount,
      paymentMode,
      paymentReference,
      patientId: selectedPatient?.id ?? null,
      patientLabel: selectedPatient ? `${selectedPatient.firstName} ${selectedPatient.lastName}`.trim() : null,
      prescription,
    };
    setHeldDraft(draft);
    window.sessionStorage.setItem(HELD_CART_STORAGE_KEY, JSON.stringify(draft));
    clearDraft();
    setSuccess("Cart held. Use Hold Cart again on an empty cart to restore it.");
  }, [cart, clearDraft, customerMobile, customerName, heldDraft, notes, paidAmount, paymentMode, paymentReference, prescription, selectedPatient, tenantId]);

  const submitSale = React.useCallback(async () => {
    if (!token || !tenantId) return;
    if (!cart.length) {
      setError("Add at least one medicine to the cart.");
      return;
    }
    if (cartHasStockIssue) {
      setError("Reduce cart quantity for items showing a stock warning before completing the sale.");
      return;
    }
    if (!selectedPatient && !customerName.trim()) {
      setError("Choose a patient or enter a walk-in customer name.");
      return;
    }
    if (!window.confirm("Confirm stock deduction and create this pharmacy sale using FEFO allocation?")) {
      return;
    }
    try {
      setSubmitting(true);
      const sale = await createPharmacyPosSale(token, tenantId, {
        patientId: selectedPatient?.id ?? null,
        customerName: selectedPatient ? null : customerName.trim(),
        customerMobile: customerMobile.trim() || null,
        prescriptionDocumentId: prescription?.documentId ?? null,
        paidAmount: numeric(paidAmount),
        paymentMode: numeric(paidAmount) > 0 ? paymentMode : null,
        paymentReference: paymentReference.trim() || null,
        notes: notes.trim() || null,
        items: cart.map((line) => ({
          medicineId: line.medicineId,
          quantity: numeric(line.quantity),
          unitPrice: numeric(line.unitPrice),
          discount: numeric(line.discount),
          tax: lineTaxAmount(line),
        })),
      });
      clearDraft();
      setSuccess(`Sale ${sale.saleNumber} created. FEFO allocation used the earliest non-expired batches and stock movements were recorded.`);
      await refreshSales();
      selectSale(sale);
      setHeldDraft(null);
      window.sessionStorage.removeItem(HELD_CART_STORAGE_KEY);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create sale");
    } finally {
      setSubmitting(false);
    }
  }, [cart, cartHasStockIssue, clearDraft, customerMobile, customerName, notes, paidAmount, paymentMode, paymentReference, prescription, refreshSales, selectSale, selectedPatient, tenantId, token]);

  React.useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const tag = target?.tagName;
      const inEditable = tag === "INPUT" || tag === "TEXTAREA" || target?.getAttribute("contenteditable") === "true";

      if (event.key === "Escape") {
        if (scanDialogOpen) {
          event.preventDefault();
          setScanDialogOpen(false);
          stopCameraStream();
          resetCapturedImage();
          setScanError(null);
          return;
        }
        if (recentDrawerOpen) {
          event.preventDefault();
          setRecentDrawerOpen(false);
          return;
        }
        if (medicineQuery.trim()) {
          event.preventDefault();
          setMedicineQuery("");
          window.setTimeout(() => searchInputRef.current?.focus(), 0);
        }
        return;
      }

      if (event.key === "F2") {
        event.preventDefault();
        paidAmountInputRef.current?.focus();
        paidAmountInputRef.current?.select();
        return;
      }

      if (event.key === "F4") {
        event.preventDefault();
        if (!submitting && cart.length && !cartHasStockIssue) {
          void submitSale();
        }
        return;
      }

      if (!selectedCartMedicineId || scanDialogOpen || recentDrawerOpen || inEditable) {
        return;
      }

      if ((event.key === "+" || event.key === "=") && !event.ctrlKey && !event.metaKey) {
        event.preventDefault();
        setCart((current) => current.map((line) =>
          line.medicineId === selectedCartMedicineId
            ? { ...line, quantity: String(numeric(line.quantity) + 1) }
            : line,
        ));
        return;
      }

      if (event.key === "-" && !event.ctrlKey && !event.metaKey) {
        event.preventDefault();
        setCart((current) => current.map((line) =>
          line.medicineId === selectedCartMedicineId
            ? { ...line, quantity: String(Math.max(1, numeric(line.quantity) - 1)) }
            : line,
        ));
        return;
      }

      if (tag !== "BUTTON") {
        window.setTimeout(() => searchInputRef.current?.focus(), 0);
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [
    cart.length,
    cartHasStockIssue,
    medicineQuery,
    recentDrawerOpen,
    resetCapturedImage,
    scanDialogOpen,
    selectedCartMedicineId,
    stopCameraStream,
    submitSale,
    submitting,
  ]);

  const printReceipt = React.useCallback(async (saleId: string) => {
    if (!token || !tenantId) return;
    try {
      const pdf = await getPharmacyPosReceiptPdf(token, tenantId, saleId);
      openPdf(pdf.blob);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate receipt");
    }
  }, [tenantId, token]);

  const previewPrescription = React.useCallback(async (documentId: string) => {
    if (!token || !tenantId) return;
    try {
      const { url } = await getPharmacyPosPrescriptionDownloadUrl(token, tenantId, documentId);
      window.open(url, "_blank", "noopener,noreferrer");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open prescription preview");
    }
  }, [tenantId, token]);

  const uploadPrescription = React.useCallback(async (file: File | null) => {
    if (!file || !token || !tenantId) return;
    try {
      setSubmitting(true);
      const uploaded = await uploadPharmacyPosPrescription(token, tenantId, file);
      setPrescription(uploaded);
      setSuccess(`Prescription ${uploaded.fileName} uploaded for this draft sale.`);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to upload prescription");
    } finally {
      setSubmitting(false);
      if (prescriptionInputRef.current) {
        prescriptionInputRef.current.value = "";
      }
    }
  }, [tenantId, token]);

  const capturePrescriptionImage = React.useCallback(() => {
    const video = cameraVideoRef.current;
    const canvas = cameraCanvasRef.current;
    if (!video || !canvas) {
      setScanError("Camera preview is not ready yet.");
      return;
    }
    const width = video.videoWidth || 1280;
    const height = video.videoHeight || 720;
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) {
      setScanError("Unable to capture prescription image.");
      return;
    }
    context.drawImage(video, 0, 0, width, height);
    canvas.toBlob((blob) => {
      if (!blob) {
        setScanError("Unable to capture prescription image.");
        return;
      }
      stopCameraStream();
      resetCapturedImage();
      setCapturedImageBlob(blob);
      setCapturedImageUrl(URL.createObjectURL(blob));
      setScanError(null);
    }, "image/jpeg", 0.92);
  }, [resetCapturedImage, stopCameraStream]);

  const retakePrescriptionImage = React.useCallback(() => {
    resetCapturedImage();
    setScanError(null);
    void startCameraStream();
  }, [resetCapturedImage, startCameraStream]);

  const useCapturedPrescriptionImage = React.useCallback(async () => {
    if (!capturedImageBlob) {
      setScanError("Capture a prescription image before uploading.");
      return;
    }
    const filename = `prescription-scan-${new Date().toISOString().replace(/[:.]/g, "-")}.jpg`;
    const file = new File([capturedImageBlob], filename, { type: "image/jpeg" });
    await uploadPrescription(file);
    setScanDialogOpen(false);
    resetCapturedImage();
  }, [capturedImageBlob, resetCapturedImage, uploadPrescription]);

  const submitPayment = React.useCallback(async () => {
    if (!token || !tenantId || !selectedSale) return;
    try {
      setSubmitting(true);
      const sale = await addPharmacyPosPayment(token, tenantId, selectedSale.id, {
        amount: numeric(paymentAmount),
        paymentMode: paymentTopupMode,
        referenceNumber: paymentReferenceTopup.trim() || null,
      });
      await refreshSales();
      selectSale(sale);
      setPaymentReferenceTopup("");
      setSuccess(`Payment recorded against sale ${sale.saleNumber}.`);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record payment");
    } finally {
      setSubmitting(false);
    }
  }, [paymentAmount, paymentReferenceTopup, paymentTopupMode, refreshSales, selectSale, selectedSale, tenantId, token]);

  const submitReturn = React.useCallback(async () => {
    if (!token || !tenantId || !selectedSale) return;
    const requestItems = selectedSale.items.flatMap((item) => {
      const draft = returnDraft[item.id];
      if (!draft?.selected) return [];
      const quantity = Math.max(0, numeric(draft.quantity));
      if (quantity <= 0) return [];
      return [{
        saleItemId: item.id,
        quantity,
        reusable: draft.reusable,
      }];
    });
    if (!requestItems.length || !returnReason.trim()) {
      setError("Select at least one return item and provide a return reason.");
      return;
    }
    const excessiveItem = requestItems.find((entry) => {
      const item = selectedSale.items.find((row) => row.id === entry.saleItemId);
      return item ? entry.quantity > returnableQuantity(item) : false;
    });
    if (excessiveItem) {
      setError("One or more return quantities exceed the remaining returnable quantity.");
      return;
    }
    try {
      setSubmitting(true);
      const existingReturnIds = new Set(selectedSale.returns.map((item) => item.id));
      const sale = await returnPharmacyPosSale(token, tenantId, selectedSale.id, {
        reason: returnReason.trim(),
        refundMode: returnMode,
        referenceNumber: returnReference.trim() || null,
        items: requestItems,
      });
      await refreshSales();
      selectSale(sale);
      const newReturns = sale.returns.filter((item) => !existingReturnIds.has(item.id));
      const returnNumbers = Array.from(new Set(newReturns.map((item) => item.returnNumber))).join(", ");
      setSuccess(`Return ${returnNumbers || "processed"} for sale ${sale.saleNumber}. Refund recorded ${money(newReturns.reduce((sum, item) => sum + item.refundAmount, 0))}. Reusable items were restocked with RETURN audit movements.`);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to process return");
    } finally {
      setSubmitting(false);
    }
  }, [refreshSales, returnDraft, returnMode, returnReason, returnReference, selectSale, selectedSale, tenantId, token]);

  if (!canAccessPos) {
    return <Alert severity="error">Pharmacy POS is restricted to Clinic Admin and pharmacy counter roles.</Alert>;
  }

  return (
    <Stack spacing={2.25}>
      <Stack direction={{ xs: "column", lg: "row" }} spacing={1.5} alignItems={{ xs: "stretch", lg: "center" }} sx={panelSx}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0, flex: 1 }}>
          <Chip icon={<PointOfSaleRoundedIcon />} label="Pharmacy POS" color="primary" />
          <TextField
            inputRef={searchInputRef}
            size="small"
            fullWidth
            label="Scan / Search medicine"
            value={medicineQuery}
            onChange={(event) => setMedicineQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && medicineResults[0]) {
                event.preventDefault();
                void addMedicine(medicineResults[0]);
              }
            }}
            placeholder="Name, generic, brand, barcode, QR, external code, batch"
          />
        </Stack>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
          <Button size="small" variant="outlined" startIcon={<AttachFileRoundedIcon />} onClick={() => prescriptionInputRef.current?.click()}>
            Upload Prescription
          </Button>
          <Button size="small" variant="outlined" startIcon={<CameraAltRoundedIcon />} onClick={() => setScanDialogOpen(true)}>
            Scan Prescription
          </Button>
          <Button
            size="small"
            variant="outlined"
            startIcon={<PauseCircleOutlineRoundedIcon />}
            onClick={holdCurrentCart}
          >
            {cart.length ? "Hold Cart" : heldDraft ? "Resume Held" : "Hold Cart"}
          </Button>
          <Button size="small" variant="outlined" color="inherit" startIcon={<RestartAltRoundedIcon />} onClick={clearDraft}>
            Clear Cart
          </Button>
          <Button size="small" variant="outlined" startIcon={<HistoryRoundedIcon />} onClick={() => setRecentDrawerOpen(true)}>
            Recent Sales
          </Button>
        </Stack>
        <input
          ref={prescriptionInputRef}
          type="file"
          hidden
          accept=".pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp"
          onChange={(event) => void uploadPrescription(event.target.files?.[0] ?? null)}
        />
      </Stack>

      <Typography variant="body2" color="text.secondary">
        Enter adds the top FEFO-eligible result. Repeated scans increase quantity. Stock deduction is confirmed at checkout and audited as sale movements.
      </Typography>
      <Typography variant="caption" color="text.secondary">
        Shortcuts: `Enter` add top result, `F2` paid amount, `F4` complete sale, `Esc` clear search or close modal, `+` / `-` adjust selected cart row quantity.
      </Typography>

      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
      {success ? <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert> : null}
      {cartHasStockIssue ? <Alert severity="warning">Some cart quantities exceed available stock. Adjust the highlighted rows before completing the sale.</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 8.4 }}>
          <Stack spacing={2}>
            <Box sx={panelSx}>
              <Stack spacing={1}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="subtitle1" fontWeight={700}>Matched Medicines</Typography>
                  <Typography variant="caption" color="text.secondary">{loading ? "Loading..." : `${medicineResults.length} results`}</Typography>
                </Stack>
                <TableContainer sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
                  <Table size="small" sx={{ tableLayout: "fixed" }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>Medicine</TableCell>
                        <TableCell width={150}>Lookup</TableCell>
                        <TableCell width={90}>Stock</TableCell>
                        <TableCell width={110}>Expiry</TableCell>
                        <TableCell width={110}>Rate</TableCell>
                        <TableCell width={70} />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {medicineResults.map((medicine) => {
                        const resultExpiryWarning = expiryWarning(medicine.earliestExpiryDate);
                        return (
                          <TableRow
                            key={medicine.medicineId}
                            hover
                            sx={{ cursor: "pointer" }}
                            onClick={() => void addMedicine(medicine)}
                          >
                            <TableCell>
                              <Typography variant="body2" fontWeight={600}>{medicine.medicineName}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {medicine.genericName || medicine.brandName || "No secondary name"}
                              </Typography>
                              {resultExpiryWarning ? (
                                <Typography variant="caption" color={`${resultExpiryWarning.tone}.main`} sx={{ display: "block" }}>
                                  {resultExpiryWarning.label}
                                </Typography>
                              ) : null}
                            </TableCell>
                            <TableCell>
                              <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                                {medicine.barcode || medicine.qrCode || medicine.externalCode || "Direct search"}
                              </Typography>
                            </TableCell>
                            <TableCell>{medicine.totalAvailableQuantity}</TableCell>
                            <TableCell>{medicine.earliestExpiryDate ?? "NA"}</TableCell>
                            <TableCell>{money(medicine.defaultUnitPrice ?? 0)}</TableCell>
                            <TableCell align="right">
                              <Button size="small" variant="text" onClick={(event) => { event.stopPropagation(); void addMedicine(medicine); }}>
                                Add
                              </Button>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                      {!medicineResults.length ? (
                        <TableRow>
                          <TableCell colSpan={6}>{loading ? "Loading medicines..." : "No sellable medicines found."}</TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Stack>
            </Box>

            <Box sx={panelSx}>
              <Stack spacing={1.25}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="subtitle1" fontWeight={700}>Cart</Typography>
                  <Typography variant="caption" color="text.secondary">Compact FEFO checkout table</Typography>
                </Stack>
                <TableContainer sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
                  <Table size="small" sx={{ tableLayout: "fixed" }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>Medicine</TableCell>
                        <TableCell width={140}>Batch</TableCell>
                        <TableCell width={92}>Expiry</TableCell>
                        <TableCell width={74}>Qty</TableCell>
                        <TableCell width={88}>Rate</TableCell>
                        <TableCell width={88}>Disc</TableCell>
                        <TableCell width={84}>Tax</TableCell>
                        <TableCell width={108}>Total</TableCell>
                        <TableCell width={52} />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {cart.map((line) => {
                        const overLimit = numeric(line.quantity) > line.availableQuantity;
                        const primaryBatch = batchPreview[line.medicineId]?.[0];
                        const splitLikely = Boolean(primaryBatch && numeric(line.quantity) > primaryBatch.availableQuantity && batchPreview[line.medicineId] && batchPreview[line.medicineId]!.length > 1);
                        const nearExpiry = expiryWarning(primaryBatch?.expiryDate ?? line.earliestExpiryDate);
                        return (
                          <TableRow
                            key={line.medicineId}
                            hover
                            selected={selectedCartMedicineId === line.medicineId}
                            onClick={() => setSelectedCartMedicineId(line.medicineId)}
                            sx={{ cursor: "pointer" }}
                          >
                            <TableCell>
                              <Typography variant="body2" fontWeight={600}>{line.medicineName}</Typography>
                              <Typography variant="caption" color={overLimit ? "error.main" : "text.secondary"} sx={{ display: "block" }}>
                                {overLimit
                                  ? `Only ${line.availableQuantity} available across sellable batches.`
                                  : `Available ${line.availableQuantity}. ${compactBatchPreview(batchPreview[line.medicineId])}`}
                              </Typography>
                              {nearExpiry ? (
                                <Typography variant="caption" color={`${nearExpiry.tone}.main`} sx={{ display: "block" }}>
                                  {nearExpiry.label}
                                </Typography>
                              ) : null}
                              {splitLikely ? (
                                <Typography variant="caption" color="info.main" sx={{ display: "block" }}>
                                  Sale fulfilled from multiple batches.
                                </Typography>
                              ) : null}
                            </TableCell>
                            <TableCell>
                              <Typography variant="caption" color="text.secondary">
                                {primaryBatch?.batchNumber ?? "Auto FEFO"}
                              </Typography>
                            </TableCell>
                            <TableCell>{primaryBatch?.expiryDate ?? line.earliestExpiryDate ?? "NA"}</TableCell>
                            <TableCell>
                              <TextField value={line.quantity} onChange={(event) => updateCartLine(line.medicineId, { quantity: event.target.value })} size="small" />
                            </TableCell>
                            <TableCell>
                              <TextField value={line.unitPrice} onChange={(event) => updateCartLine(line.medicineId, { unitPrice: event.target.value })} size="small" />
                            </TableCell>
                            <TableCell>
                              <TextField value={line.discount} onChange={(event) => updateCartLine(line.medicineId, { discount: event.target.value })} size="small" />
                            </TableCell>
                            <TableCell>
                              <TextField value={line.taxRate} onChange={(event) => updateCartLine(line.medicineId, { taxRate: event.target.value })} size="small" />
                            </TableCell>
                            <TableCell>{money(lineTotal(line))}</TableCell>
                            <TableCell align="right">
                              <Tooltip title="Remove">
                                <IconButton size="small" color="error" onClick={() => removeCartLine(line.medicineId)}>
                                  <DeleteOutlineRoundedIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                      {!cart.length ? (
                        <TableRow>
                          <TableCell colSpan={9}>No medicines in the cart.</TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </TableContainer>
                <Typography variant="caption" color="text.secondary">
                  FEFO explanation: checkout deducts stock from the earliest non-expired batch first and spills into the next batch only when required.
                </Typography>
              </Stack>
            </Box>
          </Stack>
        </Grid>

        <Grid size={{ xs: 12, lg: 3.6 }}>
          <Stack spacing={2} sx={{ position: { lg: "sticky" }, top: { lg: STICKY_TOP }, alignSelf: "flex-start" }}>
            <Box sx={panelSx}>
              <Stack spacing={1.25}>
                <Typography variant="subtitle1" fontWeight={700}>Customer</Typography>
                <TextField
                  size="small"
                  label="Search patient"
                  value={patientQuery}
                  onChange={(event) => setPatientQuery(event.target.value)}
                  placeholder="Type 2+ letters"
                  fullWidth
                />
                {patientResults.length ? (
                  <List dense sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, py: 0, maxHeight: 150, overflow: "auto" }}>
                    {patientResults.map((patient) => (
                      <ListItemButton
                        key={patient.id}
                        onClick={() => {
                          setSelectedPatient(patient);
                          setCustomerName(`${patient.firstName} ${patient.lastName}`.trim());
                          setCustomerMobile(patient.mobile);
                          setPatientResults([]);
                          setPatientQuery("");
                        }}
                      >
                        <ListItemText primary={`${patient.firstName} ${patient.lastName}`} secondary={`${patient.patientNumber} | ${patient.mobile}`} />
                      </ListItemButton>
                    ))}
                  </List>
                ) : null}
                {selectedPatient ? (
                  <Chip
                    color="primary"
                    label={`Patient: ${selectedPatient.firstName} ${selectedPatient.lastName}`}
                    onDelete={() => setSelectedPatient(null)}
                  />
                ) : null}
                <TextField size="small" label="Walk-in name" value={customerName} onChange={(event) => setCustomerName(event.target.value)} fullWidth />
                <TextField size="small" label="Mobile" value={customerMobile} onChange={(event) => setCustomerMobile(event.target.value)} fullWidth />
              </Stack>
            </Box>

            <Box sx={panelSx}>
              <Stack spacing={1.25}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="subtitle1" fontWeight={700}>Prescription</Typography>
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="text" onClick={() => prescriptionInputRef.current?.click()}>Upload File</Button>
                    <Button size="small" variant="text" onClick={() => setScanDialogOpen(true)}>Scan Prescription</Button>
                  </Stack>
                </Stack>
                {prescription ? (
                  <Stack spacing={1}>
                    <Chip icon={<AttachFileRoundedIcon />} label={prescription.fileName} variant="outlined" />
                    {prescriptionPreviewUrl && isImageFile(prescription.fileName, prescription.mediaType) ? (
                      <Box
                        component="img"
                        src={prescriptionPreviewUrl}
                        alt={prescription.fileName}
                        sx={{ width: "100%", maxHeight: 180, objectFit: "cover", borderRadius: 2, border: "1px solid", borderColor: "divider" }}
                      />
                    ) : null}
                    <Stack direction="row" spacing={1}>
                      <Button size="small" startIcon={<PreviewRoundedIcon />} onClick={() => void previewPrescription(prescription.documentId)}>
                        Open Preview
                      </Button>
                      <Button size="small" color="inherit" onClick={() => setPrescription(null)}>
                        Remove
                      </Button>
                      <Button size="small" color="inherit" onClick={() => prescriptionInputRef.current?.click()}>
                        Replace
                      </Button>
                    </Stack>
                  </Stack>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    Upload a PDF or image when a medicine or local regulation requires a prescription. OTC sales still remain allowed.
                  </Typography>
                )}
                <Alert severity="info" sx={{ py: 0 }}>
                  Scanned prescription is saved as supporting document. Verify before sale.
                </Alert>
                <Typography variant="caption" color="text.secondary">
                  Upload prescription when required by medicine/regulation.
                </Typography>
              </Stack>
            </Box>

            <Box sx={panelSx}>
              <Stack spacing={1.25}>
                <Typography variant="subtitle1" fontWeight={700}>Payment</Typography>
                <TextField inputRef={paidAmountInputRef} size="small" label="Paid amount" value={paidAmount} onChange={(event) => setPaidAmount(event.target.value)} fullWidth />
                <Select size="small" value={paymentMode} onChange={(event) => setPaymentMode(event.target.value as PaymentMode)} fullWidth>
                  {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
                </Select>
                <TextField size="small" label="Reference" value={paymentReference} onChange={(event) => setPaymentReference(event.target.value)} fullWidth />
              </Stack>
            </Box>

            <Box sx={panelSx}>
              <Stack spacing={1}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="subtitle1" fontWeight={700}>Shift</Typography>
                  <Chip size="small" label="Foundation only" variant="outlined" />
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  Session accounting backend is not active yet. These controls are placeholders for cashier shift open/close workflow.
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" disabled>Open Shift</Button>
                  <Button size="small" variant="outlined" color="inherit" disabled>Close Shift</Button>
                </Stack>
                <Stack direction="row" justifyContent="space-between">
                  <Typography variant="body2">Cash collected</Typography>
                  <Typography variant="body2">{money(0)}</Typography>
                </Stack>
                <Stack direction="row" justifyContent="space-between">
                  <Typography variant="body2">UPI collected</Typography>
                  <Typography variant="body2">{money(0)}</Typography>
                </Stack>
                <Stack direction="row" justifyContent="space-between">
                  <Typography variant="body2">Card collected</Typography>
                  <Typography variant="body2">{money(0)}</Typography>
                </Stack>
                <Stack direction="row" justifyContent="space-between">
                  <Typography variant="body2">Difference</Typography>
                  <Typography variant="body2">{money(0)}</Typography>
                </Stack>
              </Stack>
            </Box>

            <Box sx={panelSx}>
              <Stack spacing={1}>
                <Typography variant="subtitle1" fontWeight={700}>Totals</Typography>
                <Stack direction="row" justifyContent="space-between"><Typography variant="body2">Subtotal</Typography><Typography variant="body2">{money(subtotal)}</Typography></Stack>
                <Stack direction="row" justifyContent="space-between"><Typography variant="body2">Discount</Typography><Typography variant="body2">{money(discountTotal)}</Typography></Stack>
                <Stack direction="row" justifyContent="space-between"><Typography variant="body2">Tax</Typography><Typography variant="body2">{money(taxTotal)}</Typography></Stack>
                <Divider />
                <Stack direction="row" justifyContent="space-between"><Typography fontWeight={700}>Grand Total</Typography><Typography fontWeight={700}>{money(total)}</Typography></Stack>
                <Stack direction="row" justifyContent="space-between"><Typography color={duePreview > 0 ? "warning.main" : "success.main"}>Due</Typography><Typography color={duePreview > 0 ? "warning.main" : "success.main"}>{money(duePreview)}</Typography></Stack>
                <TextField size="small" label="Notes" value={notes} onChange={(event) => setNotes(event.target.value)} multiline minRows={2} fullWidth />
                <Button
                  variant="contained"
                  startIcon={<ShoppingCartCheckoutRoundedIcon />}
                  disabled={submitting || !cart.length || cartHasStockIssue}
                  onClick={() => void submitSale()}
                >
                  Complete Sale
                </Button>
              </Stack>
            </Box>

            {selectedSale ? (
              <Box sx={panelSx}>
                <Stack spacing={1.25}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Box>
                      <Typography variant="subtitle1" fontWeight={700}>{selectedSale.saleNumber}</Typography>
                      <Typography variant="caption" color="text.secondary">{saleDisplayName(selectedSale)} | {selectedSale.status}</Typography>
                    </Box>
                    <Stack direction="row" spacing={1}>
                      <Button size="small" variant="outlined" onClick={() => setRecentDrawerOpen(true)}>
                        Find Sale
                      </Button>
                      <Button size="small" startIcon={<LocalPrintshopOutlinedIcon />} onClick={() => void printReceipt(selectedSale.id)}>
                        Receipt
                      </Button>
                    </Stack>
                  </Stack>
                  <Typography variant="caption" color="text.secondary">{selectedSale.fefoExplanation}</Typography>
                  {saleUsesMultipleBatches(selectedSale) ? (
                    <Alert severity="info" sx={{ py: 0 }}>
                      Sale fulfilled from multiple batches.
                    </Alert>
                  ) : null}
                  {selectedSale.prescriptionDocumentId ? (
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Chip size="small" icon={<AttachFileRoundedIcon />} label={selectedSale.prescriptionFileName ?? "Prescription"} />
                      <IconButton size="small" onClick={() => void previewPrescription(selectedSale.prescriptionDocumentId!)}>
                        <VisibilityRoundedIcon fontSize="small" />
                      </IconButton>
                    </Stack>
                  ) : null}
                  <Divider />
                  <Typography variant="body2" fontWeight={600}>Add payment</Typography>
                  <TextField size="small" label="Amount" value={paymentAmount} onChange={(event) => setPaymentAmount(event.target.value)} fullWidth />
                  <Select size="small" value={paymentTopupMode} onChange={(event) => setPaymentTopupMode(event.target.value as PaymentMode)} fullWidth>
                    {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
                  </Select>
                  <TextField size="small" label="Reference" value={paymentReferenceTopup} onChange={(event) => setPaymentReferenceTopup(event.target.value)} fullWidth />
                  <Button size="small" variant="outlined" disabled={submitting || selectedSale.dueAmount <= 0} onClick={() => void submitPayment()}>
                    Record Payment
                  </Button>
                  <Divider />
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="body2" fontWeight={600}>Return / Refund</Typography>
                    <Chip size="small" label={`${selectedSale.items.length} sale item${selectedSale.items.length === 1 ? "" : "s"}`} variant="outlined" />
                  </Stack>
                  <Stack spacing={1} sx={{ maxHeight: 220, overflow: "auto", pr: 0.5 }}>
                    {selectedSale.items.map((item) => {
                      const draft = returnDraft[item.id] ?? { selected: false, quantity: "1", reusable: true };
                      const remaining = returnableQuantity(item);
                      const selectedQty = Math.min(remaining, Math.max(0, numeric(draft.quantity)));
                      return (
                        <Box key={item.id} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1 }}>
                          <Stack spacing={0.75}>
                            <Stack direction="row" spacing={1} alignItems="center">
                              <Checkbox
                                size="small"
                                checked={draft.selected}
                                disabled={remaining <= 0}
                                onChange={(event) => setReturnDraft((current) => ({
                                  ...current,
                                  [item.id]: {
                                    ...(current[item.id] ?? draft),
                                    selected: event.target.checked,
                                  },
                                }))}
                              />
                              <Box sx={{ minWidth: 0, flex: 1 }}>
                                <Typography variant="body2" fontWeight={600}>{item.medicineName}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  Batch {item.batchNumber ?? "NA"} | Sold {item.quantity} | Returned {item.returnedQuantity} | Returnable {remaining}
                                </Typography>
                              </Box>
                            </Stack>
                            <Stack direction="row" spacing={1}>
                              <TextField
                                size="small"
                                label="Return qty"
                                value={draft.quantity}
                                disabled={!draft.selected || remaining <= 0}
                                onChange={(event) => setReturnDraft((current) => ({
                                  ...current,
                                  [item.id]: {
                                    ...(current[item.id] ?? draft),
                                    quantity: event.target.value,
                                  },
                                }))}
                                fullWidth
                              />
                              <Select
                                size="small"
                                value={draft.reusable ? "true" : "false"}
                                disabled={!draft.selected || remaining <= 0}
                                onChange={(event) => setReturnDraft((current) => ({
                                  ...current,
                                  [item.id]: {
                                    ...(current[item.id] ?? draft),
                                    reusable: event.target.value === "true",
                                  },
                                }))}
                                fullWidth
                              >
                                <MenuItem value="true">Reusable</MenuItem>
                                <MenuItem value="false">Discard</MenuItem>
                              </Select>
                            </Stack>
                            <Typography variant="caption" color="text.secondary">
                              Refund estimate {money(proratedRefundAmount(item, selectedQty))}. {draft.reusable ? "Reusable items restock into inventory." : "Discarded items stay out of stock."}
                            </Typography>
                          </Stack>
                        </Box>
                      );
                    })}
                  </Stack>
                  <Select size="small" value={returnMode} onChange={(event) => setReturnMode(event.target.value as PaymentMode)} fullWidth>
                    {PAYMENT_MODES.map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
                  </Select>
                  <TextField size="small" label="Reason" value={returnReason} onChange={(event) => setReturnReason(event.target.value)} fullWidth />
                  <TextField size="small" label="Refund reference" value={returnReference} onChange={(event) => setReturnReference(event.target.value)} fullWidth />
                  <Alert severity="info" sx={{ py: 0 }}>
                    Refund estimate {money(refundEstimate)}. Reusable lines: {reusableSelectedCount}. Discard lines: {discardSelectedCount}.
                  </Alert>
                  <Button size="small" variant="outlined" color="warning" disabled={submitting || !selectedSale.items.length} onClick={() => void submitReturn()}>
                    Process Return
                  </Button>
                  {selectedSale.returns.length ? (
                    <>
                      <Divider />
                      <Typography variant="body2" fontWeight={600}>Return history</Typography>
                      <Stack spacing={0.75}>
                        {selectedSale.returns.slice().reverse().slice(0, 4).map((returned) => (
                          <Box key={returned.id} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1 }}>
                            <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                              {returned.returnNumber} | Qty {returned.quantity} | Refund {money(returned.refundAmount)}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                              {returned.reusable ? "Reusable restock" : "Discarded"} | {returned.refundMode ?? "No refund mode"} | {returned.reason}
                            </Typography>
                          </Box>
                        ))}
                      </Stack>
                    </>
                  ) : null}
                </Stack>
              </Box>
            ) : null}
          </Stack>
        </Grid>
      </Grid>

      <Drawer anchor="right" open={recentDrawerOpen} onClose={() => setRecentDrawerOpen(false)}>
        <Box sx={{ width: { xs: 320, sm: 380 }, p: 2 }}>
          <Stack spacing={1.25}>
            <Typography variant="h6">Recent Sales</Typography>
            <TextField
              size="small"
              label="Find prior sale"
              value={saleSearchQuery}
              onChange={(event) => setSaleSearchQuery(event.target.value)}
              placeholder="Sale no, customer, mobile"
              fullWidth
            />
            <List dense sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, py: 0 }}>
              {filteredSales.slice(0, 5).map((sale) => (
                <ListItemButton key={sale.id} selected={selectedSaleId === sale.id} onClick={() => selectSale(sale)}>
                  <ListItemText
                    primary={`${sale.saleNumber} | ${sale.status}`}
                    secondary={`${saleDisplayName(sale)} | ${money(sale.total)} | Due ${money(sale.dueAmount)}`}
                  />
                </ListItemButton>
              ))}
              {!filteredSales.length ? <ListItemText sx={{ px: 2, py: 1 }} primary="No matching pharmacy sales." /> : null}
            </List>
          </Stack>
        </Box>
      </Drawer>

      <Dialog
        open={scanDialogOpen}
        onClose={() => {
          setScanDialogOpen(false);
          stopCameraStream();
          resetCapturedImage();
          setScanError(null);
        }}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Scan Prescription</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {scanError ? <Alert severity="warning">{scanError}</Alert> : null}
            <Typography variant="body2" color="text.secondary">
              Capture a clear image of the prescription and upload it through the same tenant-scoped POS prescription API.
            </Typography>
            <Box
              sx={{
                border: "1px solid",
                borderColor: "divider",
                borderRadius: 2,
                overflow: "hidden",
                bgcolor: "grey.100",
                minHeight: 280,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              {capturedImageUrl ? (
                <Box component="img" src={capturedImageUrl} alt="Captured prescription" sx={{ width: "100%", display: "block" }} />
              ) : (
                <Box component="video" ref={cameraVideoRef} muted playsInline autoPlay sx={{ width: "100%", display: "block" }} />
              )}
            </Box>
            <canvas ref={cameraCanvasRef} style={{ display: "none" }} />
            {!capturedImageUrl ? (
              <Stack direction="row" spacing={1}>
                <Button
                  variant="contained"
                  startIcon={<CameraAltRoundedIcon />}
                  onClick={capturePrescriptionImage}
                  disabled={Boolean(scanError)}
                >
                  Capture
                </Button>
              </Stack>
            ) : (
              <Stack direction="row" spacing={1}>
                <Button variant="outlined" onClick={retakePrescriptionImage}>Retake</Button>
                <Button variant="contained" onClick={() => void useCapturedPrescriptionImage()} disabled={submitting}>
                  Use Photo
                </Button>
              </Stack>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setScanDialogOpen(false);
              stopCameraStream();
              resetCapturedImage();
              setScanError(null);
            }}
          >
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
