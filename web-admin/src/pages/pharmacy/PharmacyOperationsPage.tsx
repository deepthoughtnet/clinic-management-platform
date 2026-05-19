import * as React from "react";
import {
  Alert,
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
  Tabs,
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
import {
  confirmReconciliation,
  createReconciliation,
  createStockInward,
  createSupplier,
  getInventoryLocations,
  getMedicines,
  getPharmacyAnalytics,
  getPharmacyDashboard,
  getStocks,
  listReconciliations,
  listSuppliers,
  type InventoryLocation,
  type Medicine,
  type PharmacyAnalytics,
  type PharmacyDashboard,
  type PharmacyReconciliation,
  type Supplier,
  type SupplierInput,
  type StockInwardInput,
  type Stock,
  type PurchaseOrder,
  type SupplierInvoice,
  type GoodsReceipt,
  type PurchaseOrderInput,
  type SupplierInvoiceInput,
  type GoodsReceiptInput,
  createPurchaseOrder,
  createSupplierInvoice,
  createGoodsReceipt,
  getPurchaseOrders,
  getSupplierInvoices,
  getGoodsReceipts,
  confirmGoodsReceipt,
  reviewReconciliationSheet,
  uploadReconciliationSheet,
  updateSupplier,
} from "../../api/clinicApi";

type OpsTab = "dashboard" | "suppliers" | "inward" | "reconciliation" | "procurement" | "analytics";

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

function money(value: number | null | undefined) {
  return (value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function badgeTone(text: string) {
  if (text === "OUT_OF_STOCK" || text === "EXPIRED") return "error" as const;
  if (text === "LOW_STOCK" || text === "NEAR_EXPIRY") return "warning" as const;
  if (text === "AVAILABLE" || text === "OK") return "success" as const;
  return "default" as const;
}

export default function PharmacyOperationsPage() {
  const auth = useAuth();
  const [tab, setTab] = React.useState<OpsTab>("dashboard");
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
  const [inwardForm, setInwardForm] = React.useState<StockInwardInput>(emptyInward);
  const [reconForm, setReconForm] = React.useState(emptyReconciliation);
  const [sheetFile, setSheetFile] = React.useState<File | null>(null);
  const [sheetUpload, setSheetUpload] = React.useState<{ reconciliationId: string; extractedRows: Array<{ rowNumber: number; medicineCode: string | null; medicineName: string | null; batchNumber: string | null; physicalQuantity: number | null; expiryDate: string | null; notes: string | null; confidence: number | null; needsReview: boolean; }> } | null>(null);
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
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load pharmacy operations");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => { void loadAll(); }, [loadAll]);

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
      setSuccess("Stock inward recorded");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record stock inward");
    } finally {
      setSaving(false);
    }
  };

  const submitReconciliation = async () => {
    if (!auth.accessToken || !auth.tenantId || !reconForm.medicineId) return;
    setSaving(true);
    setError(null);
    try {
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
        setSheetUpload({ reconciliationId: uploaded.reconciliationId, extractedRows: uploaded.extractedRows });
      }
      setReconForm(emptyReconciliation);
      setSheetFile(null);
      setSheetUpload(null);
      setSuccess("Reconciliation session created");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create reconciliation session");
    } finally {
      setSaving(false);
    }
  };

  const confirmRecon = async (row: PharmacyReconciliation) => {
    if (!auth.accessToken || !auth.tenantId) return;
    const physical = row.physicalQuantity ?? row.systemQuantity;
    setSaving(true);
    setError(null);
    try {
      await confirmReconciliation(auth.accessToken, auth.tenantId, row.id, {
        physicalQuantity: physical,
        reason: row.reason || "Stock count adjustment",
        adjustStock: true,
      });
      setSuccess("Reconciliation confirmed");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to confirm reconciliation");
    } finally {
      setSaving(false);
    }
  };

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

  const activeMedicines = React.useMemo(() => medicines.filter((m) => m.active), [medicines]);

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to access Pharmacy Operations.</Alert>;

  return (
    <Stack spacing={2.5}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Pharmacy Operations</Typography>
          <Typography variant="body2" color="text.secondary">Supplier master, inward stock, reconciliation, stock sheet upload, substitutions, and analytics in one place.</Typography>
        </Box>
        <Button variant="outlined" onClick={() => void loadAll()}>Refresh</Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert> : null}

      <Grid container spacing={1.5}>
        <Grid size={{ xs: 12, sm: 6, md: 2 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Medicines</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{dashboard?.medicinesCount ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 2 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Stock batches</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{dashboard?.stockBatchesCount ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 2 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Low stock</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{dashboard?.lowStockCount ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 2 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Expired</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{dashboard?.expiredCount ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 2 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Near expiry</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{dashboard?.nearExpiryCount ?? 0}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 2 }}><Card><CardContent><Typography variant="caption" color="text.secondary">Today dispensed</Typography><Typography variant="h5" sx={{ fontWeight: 900 }}>{dashboard?.todayDispensedCount ?? 0}</Typography></CardContent></Card></Grid>
      </Grid>

      <Card>
        <CardContent>
          <Tabs value={tab} onChange={(_, value) => setTab(value as OpsTab)}>
            <Tab value="dashboard" label="Dashboard" />
            <Tab value="suppliers" label="Suppliers" />
            <Tab value="inward" label="Stock Inward" />
            <Tab value="reconciliation" label="Reconciliation" />
            <Tab value="procurement" label="Procurement" />
            <Tab value="analytics" label="Analytics" />
          </Tabs>
        </CardContent>
      </Card>

      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading && tab === "dashboard" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Recent stock movements</Typography>
              {dashboard?.recentStockMovements?.length ? (
                <Table size="small">
                  <TableHead><TableRow><TableCell>Time</TableCell><TableCell>Medicine</TableCell><TableCell>Type</TableCell><TableCell align="right">Qty</TableCell></TableRow></TableHead>
                  <TableBody>
                    {dashboard.recentStockMovements.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>{new Date(row.createdAt).toLocaleString()}</TableCell>
                        <TableCell>{medicines.find((m) => m.id === row.medicineId)?.medicineName || row.medicineId}</TableCell>
                        <TableCell>{row.transactionType.replace(/_/g, " ")}</TableCell>
                        <TableCell align="right">{row.quantity}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : <Alert severity="info">No recent stock movements yet.</Alert>}
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Dispensing summary</Typography>
              <Stack spacing={1}>
                <Chip label={`Pending dispensing: ${dashboard?.pendingDispensingCount ?? 0}`} color="warning" />
                <Chip label={`Partially dispensed: ${dashboard?.partiallyDispensedCount ?? 0}`} color="warning" />
                <Chip label={`Near expiry: ${dashboard?.nearExpiryCount ?? 0}`} color="warning" />
              </Stack>
            </CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "suppliers" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Card><CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>{supplierId ? "Edit supplier" : "Add supplier"}</Typography>
                <TextField label="Supplier name" value={supplierForm.supplierName} onChange={(e) => setSupplierForm((s) => ({ ...s, supplierName: e.target.value }))} />
                <TextField label="Contact person" value={supplierForm.contactPerson || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, contactPerson: e.target.value || null }))} />
                <TextField label="Phone" value={supplierForm.phone || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, phone: e.target.value || null }))} />
                <TextField label="Email" value={supplierForm.email || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, email: e.target.value || null }))} />
                <TextField label="GST / tax number" value={supplierForm.gstNumber || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, gstNumber: e.target.value || null }))} />
                <TextField label="Address" value={supplierForm.address || ""} onChange={(e) => setSupplierForm((s) => ({ ...s, address: e.target.value || null }))} multiline minRows={2} />
                <FormControl fullWidth>
                  <InputLabel id="supplier-active-label">Active</InputLabel>
                  <Select labelId="supplier-active-label" label="Active" value={supplierForm.active ? "true" : "false"} onChange={(e) => setSupplierForm((s) => ({ ...s, active: String(e.target.value) === "true" }))}>
                    <MenuItem value="true">Active</MenuItem>
                    <MenuItem value="false">Inactive</MenuItem>
                  </Select>
                </FormControl>
                <Box sx={{ display: "flex", gap: 1 }}>
                  <Button variant="contained" onClick={() => void submitSupplier()} disabled={saving}>{supplierId ? "Update" : "Create"}</Button>
                  <Button onClick={() => { setSupplierForm(emptySupplier); setSupplierId(null); }}>Reset</Button>
                </Box>
              </Stack>
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 8 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Suppliers</Typography>
              {suppliers.length === 0 ? <Alert severity="info">No suppliers yet. Add one to use inward stock flows.</Alert> : (
                <Table size="small">
                  <TableHead><TableRow><TableCell>Name</TableCell><TableCell>Contact</TableCell><TableCell>Phone</TableCell><TableCell>Status</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead>
                  <TableBody>
                    {suppliers.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>{row.supplierName}</TableCell>
                        <TableCell>{row.contactPerson || "-"}</TableCell>
                        <TableCell>{row.phone || "-"}</TableCell>
                        <TableCell><Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} /></TableCell>
                        <TableCell align="right">
                          <Button size="small" onClick={() => { setSupplierId(row.id); setSupplierForm({ supplierName: row.supplierName, contactPerson: row.contactPerson, phone: row.phone, email: row.email, gstNumber: row.gstNumber, address: row.address, active: row.active }); }}>Edit</Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "inward" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <Card><CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Stock inward / GRN</Typography>
                <FormControl fullWidth>
                  <InputLabel id="inward-medicine-label">Medicine</InputLabel>
                  <Select labelId="inward-medicine-label" label="Medicine" value={inwardForm.medicineId} onChange={(e) => setInwardForm((s) => ({ ...s, medicineId: String(e.target.value) }))}>
                    <MenuItem value="">Select medicine</MenuItem>
                    {activeMedicines.map((medicine) => <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>)}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="inward-location-label">Location</InputLabel>
                  <Select labelId="inward-location-label" label="Location" value={inwardForm.locationId || ""} onChange={(e) => setInwardForm((s) => ({ ...s, locationId: String(e.target.value) || null }))}>
                    <MenuItem value="">Default location</MenuItem>
                    {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}{location.defaultLocation ? " (Default)" : ""}</MenuItem>)}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="inward-supplier-label">Supplier</InputLabel>
                  <Select labelId="inward-supplier-label" label="Supplier" value={inwardForm.supplierId || ""} onChange={(e) => setInwardForm((s) => ({ ...s, supplierId: String(e.target.value) || null }))}>
                    <MenuItem value="">No supplier</MenuItem>
                    {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField label="Purchase reference number" value={inwardForm.purchaseReferenceNumber || ""} onChange={(e) => setInwardForm((s) => ({ ...s, purchaseReferenceNumber: e.target.value || null }))} />
                <TextField label="Batch number" value={inwardForm.batchNumber || ""} onChange={(e) => setInwardForm((s) => ({ ...s, batchNumber: e.target.value || null }))} />
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 6 }}><CodeScannerField label="Barcode" value={inwardForm.barcode || ""} onChange={(value) => setInwardForm((s) => ({ ...s, barcode: value || null }))} placeholder="Scan or enter barcode" /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><CodeScannerField label="External code" value={inwardForm.externalCode || ""} onChange={(value) => setInwardForm((s) => ({ ...s, externalCode: value || null }))} placeholder="Scan or enter code" /></Grid>
                </Grid>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Expiry date" type="date" InputLabelProps={{ shrink: true }} value={inwardForm.expiryDate || ""} onChange={(e) => setInwardForm((s) => ({ ...s, expiryDate: e.target.value || null }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Purchase date" type="date" InputLabelProps={{ shrink: true }} value={inwardForm.purchaseDate || ""} onChange={(e) => setInwardForm((s) => ({ ...s, purchaseDate: e.target.value || null }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="number" label="Quantity" value={inwardForm.quantity} onChange={(e) => setInwardForm((s) => ({ ...s, quantity: Number(e.target.value || "0") }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="number" label="Unit cost" value={inwardForm.unitCost ?? ""} onChange={(e) => setInwardForm((s) => ({ ...s, unitCost: e.target.value ? Number(e.target.value) : null }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="number" label="Selling price" value={inwardForm.sellingPrice ?? ""} onChange={(e) => setInwardForm((s) => ({ ...s, sellingPrice: e.target.value ? Number(e.target.value) : null }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="number" label="Low stock threshold" value={inwardForm.lowStockThreshold ?? ""} onChange={(e) => setInwardForm((s) => ({ ...s, lowStockThreshold: e.target.value ? Number(e.target.value) : null }))} /></Grid>
                </Grid>
                <Button variant="contained" onClick={() => void submitInward()} disabled={saving}>Confirm inward</Button>
              </Stack>
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Stock batches</Typography>
              <Alert severity="info" sx={{ mb: 2 }}>Use the main Inventory screen for detailed batch editing. This workflow records inward receipts and new batches.</Alert>
              <Table size="small">
                <TableHead><TableRow><TableCell>Medicine</TableCell><TableCell>Batch</TableCell><TableCell align="right">On hand</TableCell><TableCell>Expiry</TableCell></TableRow></TableHead>
                <TableBody>
                  {dashboard?.recentStockMovements?.slice(0, 5).map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>{medicines.find((m) => m.id === row.medicineId)?.medicineName || row.medicineId}</TableCell>
                      <TableCell>{row.stockBatchId || "-"}</TableCell>
                      <TableCell align="right">{row.afterQuantity ?? "-"}</TableCell>
                      <TableCell>{row.createdAt ? new Date(row.createdAt).toLocaleDateString() : "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "reconciliation" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <Card><CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Start stock count</Typography>
                <FormControl fullWidth>
                  <InputLabel id="recon-location-label">Location</InputLabel>
                  <Select labelId="recon-location-label" label="Location" value={selectedLocationId} onChange={(e) => setSelectedLocationId(String(e.target.value))}>
                    <MenuItem value="">Default location</MenuItem>
                    {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}{location.defaultLocation ? " (Default)" : ""}</MenuItem>)}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="recon-medicine-label">Medicine</InputLabel>
                  <Select labelId="recon-medicine-label" label="Medicine" value={reconForm.medicineId} onChange={(e) => setReconForm((s) => ({ ...s, medicineId: String(e.target.value) }))}>
                    <MenuItem value="">Select medicine</MenuItem>
                    {activeMedicines.map((medicine) => <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>)}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
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
                          {stock.medicineName} - {stock.batchNumber || stock.id}
                        </MenuItem>
                      ))}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="recon-supplier-label">Supplier</InputLabel>
                  <Select labelId="recon-supplier-label" label="Supplier" value={reconForm.supplierId} onChange={(e) => setReconForm((s) => ({ ...s, supplierId: String(e.target.value) }))}>
                    <MenuItem value="">No supplier</MenuItem>
                    {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField label="Physical quantity" value={reconForm.physicalQuantity} onChange={(e) => setReconForm((s) => ({ ...s, physicalQuantity: e.target.value }))} />
                <TextField label="Reason" value={reconForm.reason} onChange={(e) => setReconForm((s) => ({ ...s, reason: e.target.value }))} multiline minRows={2} />
                <Button variant="outlined" component="label">
                  Upload stock sheet
                  <input hidden type="file" accept=".pdf,image/*" onChange={(e) => setSheetFile(e.target.files?.[0] || null)} />
                </Button>
                {sheetFile ? <Typography variant="body2" color="text.secondary">Attached: {sheetFile.name} for OCR extraction and manual review.</Typography> : null}
                {sheetUpload ? (
                  <Stack spacing={1}>
                    <Typography variant="subtitle2">Extracted rows</Typography>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Row</TableCell>
                          <TableCell>Medicine</TableCell>
                          <TableCell>Batch</TableCell>
                          <TableCell align="right">Qty</TableCell>
                          <TableCell>Expiry</TableCell>
                          <TableCell>Needs review</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {sheetUpload.extractedRows.map((row, index) => (
                          <TableRow key={`${row.rowNumber}-${index}`}>
                            <TableCell>{row.rowNumber}</TableCell>
                            <TableCell>
                              <Stack spacing={0.5}>
                                <TextField size="small" value={row.medicineName || ""} onChange={(e) => setSheetUpload((current) => current ? { ...current, extractedRows: current.extractedRows.map((item, idx) => idx === index ? { ...item, medicineName: e.target.value } : item) } : current)} />
                                <Typography variant="caption" color="text.secondary">{row.medicineCode || "-"}</Typography>
                              </Stack>
                            </TableCell>
                            <TableCell><TextField size="small" value={row.batchNumber || ""} onChange={(e) => setSheetUpload((current) => current ? { ...current, extractedRows: current.extractedRows.map((item, idx) => idx === index ? { ...item, batchNumber: e.target.value } : item) } : current)} /></TableCell>
                            <TableCell align="right"><TextField size="small" type="number" value={row.physicalQuantity ?? ""} onChange={(e) => setSheetUpload((current) => current ? { ...current, extractedRows: current.extractedRows.map((item, idx) => idx === index ? { ...item, physicalQuantity: e.target.value ? Number(e.target.value) : null } : item) } : current)} /></TableCell>
                            <TableCell><TextField size="small" type="date" InputLabelProps={{ shrink: true }} value={row.expiryDate || ""} onChange={(e) => setSheetUpload((current) => current ? { ...current, extractedRows: current.extractedRows.map((item, idx) => idx === index ? { ...item, expiryDate: e.target.value || null } : item) } : current)} /></TableCell>
                            <TableCell><Chip size="small" label={row.needsReview ? "Yes" : "No"} color={row.needsReview ? "warning" : "success"} /></TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                    <Button
                      variant="outlined"
                      onClick={async () => {
                        if (!auth.accessToken || !auth.tenantId || !sheetUpload) return;
                        setSaving(true);
                        setError(null);
                        try {
                          await reviewReconciliationSheet(auth.accessToken, auth.tenantId, sheetUpload.reconciliationId, { rows: sheetUpload.extractedRows, reviewNote: "Manual review completed" });
                          setSuccess("OCR rows reviewed");
                          await loadAll();
                        } catch (err) {
                          setError(err instanceof Error ? err.message : "Failed to save OCR review");
                        } finally {
                          setSaving(false);
                        }
                      }}
                    >
                      Review rows
                    </Button>
                  </Stack>
                ) : null}
                <Button variant="contained" onClick={() => void submitReconciliation()} disabled={saving}>Start stock count</Button>
              </Stack>
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Active reconciliation sessions</Typography>
              {reconciliations.length === 0 ? <Alert severity="info">No reconciliation sessions yet.</Alert> : (
                <Table size="small">
                  <TableHead><TableRow><TableCell>Medicine</TableCell><TableCell>Batch</TableCell><TableCell align="right">System</TableCell><TableCell align="right">Physical</TableCell><TableCell align="right">Variance</TableCell><TableCell>Status</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead>
                  <TableBody>
                    {reconciliations.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>{row.medicineName || row.medicineId}</TableCell>
                        <TableCell>{row.batchNumber || "-"}</TableCell>
                        <TableCell align="right">{row.systemQuantity}</TableCell>
                        <TableCell align="right">{row.physicalQuantity ?? "-"}</TableCell>
                        <TableCell align="right">{row.varianceQuantity ?? "-"}</TableCell>
                        <TableCell><Chip size="small" label={row.status} color={badgeTone(row.status)} /></TableCell>
                        <TableCell align="right">
                          {row.status === "PENDING" ? <Button size="small" onClick={() => void confirmRecon(row)}>Confirm adjustment</Button> : null}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "procurement" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Purchase order</Typography>
                <FormControl fullWidth>
                  <InputLabel id="po-supplier-label">Supplier</InputLabel>
                  <Select labelId="po-supplier-label" label="Supplier" value={poForm.supplierId} onChange={(e) => setPoForm((s) => ({ ...s, supplierId: String(e.target.value) }))}>
                    <MenuItem value="">Select supplier</MenuItem>
                    {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField label="PO number" value={poForm.poNumber} onChange={(e) => setPoForm((s) => ({ ...s, poNumber: e.target.value }))} />
                <TextField label="Order date" type="date" InputLabelProps={{ shrink: true }} value={poForm.orderDate} onChange={(e) => setPoForm((s) => ({ ...s, orderDate: e.target.value }))} />
                <TextField label="Expected delivery" type="date" InputLabelProps={{ shrink: true }} value={poForm.expectedDeliveryDate || ""} onChange={(e) => setPoForm((s) => ({ ...s, expectedDeliveryDate: e.target.value || null }))} />
                <TextField label="Item medicine name" value={procurementLine.medicineName} onChange={(e) => setProcurementLine((s) => ({ ...s, medicineName: e.target.value }))} />
                <FormControl fullWidth>
                  <InputLabel id="po-medicine-label">Medicine master</InputLabel>
                  <Select labelId="po-medicine-label" label="Medicine master" value={procurementLine.medicineId} onChange={(e) => setProcurementLine((s) => ({ ...s, medicineId: String(e.target.value) }))}>
                    <MenuItem value="">Manual line</MenuItem>
                    {medicines.map((medicine) => <MenuItem key={medicine.id} value={medicine.id}>{medicine.medicineName}</MenuItem>)}
                  </Select>
                </FormControl>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="number" label="Qty" value={procurementLine.quantity} onChange={(e) => setProcurementLine((s) => ({ ...s, quantity: e.target.value }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="number" label="Expected unit cost" value={procurementLine.expectedUnitCost} onChange={(e) => setProcurementLine((s) => ({ ...s, expectedUnitCost: e.target.value }))} /></Grid>
                </Grid>
                <Button variant="contained" onClick={() => void savePurchaseOrder()} disabled={saving}>Save PO</Button>
              </Stack>
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Supplier invoice</Typography>
                <FormControl fullWidth>
                  <InputLabel id="invoice-supplier-label">Supplier</InputLabel>
                  <Select labelId="invoice-supplier-label" label="Supplier" value={invoiceForm.supplierId} onChange={(e) => setInvoiceForm((s) => ({ ...s, supplierId: String(e.target.value) }))}>
                    <MenuItem value="">Select supplier</MenuItem>
                    {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="invoice-po-label">Purchase order</InputLabel>
                  <Select labelId="invoice-po-label" label="Purchase order" value={invoiceForm.purchaseOrderId || ""} onChange={(e) => setInvoiceForm((s) => ({ ...s, purchaseOrderId: String(e.target.value) || null }))}>
                    <MenuItem value="">No PO</MenuItem>
                    {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField label="Invoice number" value={invoiceForm.invoiceNumber} onChange={(e) => setInvoiceForm((s) => ({ ...s, invoiceNumber: e.target.value }))} />
                <TextField label="Invoice date" type="date" InputLabelProps={{ shrink: true }} value={invoiceForm.invoiceDate} onChange={(e) => setInvoiceForm((s) => ({ ...s, invoiceDate: e.target.value }))} />
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="number" label="Tax amount" value={invoiceForm.taxAmount ?? ""} onChange={(e) => setInvoiceForm((s) => ({ ...s, taxAmount: e.target.value ? Number(e.target.value) : null }))} /></Grid>
                  <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth type="number" label="Total amount" value={invoiceForm.totalAmount ?? ""} onChange={(e) => setInvoiceForm((s) => ({ ...s, totalAmount: e.target.value ? Number(e.target.value) : null }))} /></Grid>
                </Grid>
                <Button variant="contained" onClick={() => void saveSupplierInvoice()} disabled={saving}>Save invoice</Button>
              </Stack>
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Goods receipt / GRN</Typography>
                <FormControl fullWidth>
                  <InputLabel id="grn-supplier-label">Supplier</InputLabel>
                  <Select labelId="grn-supplier-label" label="Supplier" value={grnForm.supplierId} onChange={(e) => setGrnForm((s) => ({ ...s, supplierId: String(e.target.value) }))}>
                    <MenuItem value="">Select supplier</MenuItem>
                    {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.supplierName}</MenuItem>)}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="grn-po-label">Purchase order</InputLabel>
                  <Select labelId="grn-po-label" label="Purchase order" value={grnForm.purchaseOrderId || ""} onChange={(e) => setGrnForm((s) => ({ ...s, purchaseOrderId: String(e.target.value) || null }))}>
                    <MenuItem value="">Direct purchase</MenuItem>
                    {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>)}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="grn-invoice-label">Invoice</InputLabel>
                  <Select labelId="grn-invoice-label" label="Invoice" value={grnForm.supplierInvoiceId || ""} onChange={(e) => setGrnForm((s) => ({ ...s, supplierInvoiceId: String(e.target.value) || null }))}>
                    <MenuItem value="">No invoice</MenuItem>
                    {supplierInvoices.map((invoice) => <MenuItem key={invoice.id} value={invoice.id}>{invoice.invoiceNumber}</MenuItem>)}
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="grn-location-select">Location</InputLabel>
                  <Select labelId="grn-location-select" label="Location" value={grnForm.locationId || selectedLocationId} onChange={(e) => setGrnForm((s) => ({ ...s, locationId: String(e.target.value) }))}>
                    <MenuItem value="">Default location</MenuItem>
                    {locations.map((location) => <MenuItem key={location.id} value={location.id}>{location.locationName}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField label="Receipt number" value={grnForm.receiptNumber} onChange={(e) => setGrnForm((s) => ({ ...s, receiptNumber: e.target.value }))} />
                <TextField label="Received at" type="datetime-local" InputLabelProps={{ shrink: true }} value={grnForm.receivedAt} onChange={(e) => setGrnForm((s) => ({ ...s, receivedAt: e.target.value }))} />
                <Button variant="contained" onClick={() => void saveGoodsReceipt()} disabled={saving}>Save GRN</Button>
              </Stack>
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Matching summary</Typography>
              <Stack spacing={1}>
                <Chip label={`POs: ${purchaseOrders.length}`} />
                <Chip label={`Invoices: ${supplierInvoices.length}`} />
                <Chip label={`GRNs: ${goodsReceipts.length}`} />
              </Stack>
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Recent GRNs</Typography>
              {goodsReceipts.length === 0 ? (
                <Alert severity="info">No goods receipts have been saved yet.</Alert>
              ) : (
                <Table size="small">
                  <TableHead><TableRow><TableCell>Receipt</TableCell><TableCell>Location</TableCell><TableCell>Status</TableCell><TableCell>Variance</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead>
                  <TableBody>
                    {goodsReceipts.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>{row.receiptNumber}</TableCell>
                        <TableCell>{row.locationName || row.locationId}</TableCell>
                        <TableCell><Chip size="small" label={row.matchingStatus} color={badgeTone(row.matchingStatus)} /></TableCell>
                        <TableCell>{row.varianceSummary || "-"}</TableCell>
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
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent></Card>
          </Grid>
        </Grid>
      ) : null}

      {!loading && tab === "analytics" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Fast moving medicines</Typography>
              {analytics?.fastMovingMedicines?.length ? (
                <Table size="small">
                  <TableHead><TableRow><TableCell>Medicine</TableCell><TableCell align="right">Dispensed</TableCell><TableCell align="right">Available</TableCell></TableRow></TableHead>
                  <TableBody>
                    {analytics.fastMovingMedicines.map((row) => (
                      <TableRow key={row.medicineId}>
                        <TableCell>{row.medicineName || row.medicineId}</TableCell>
                        <TableCell align="right">{row.dispensedQuantity}</TableCell>
                        <TableCell align="right">{row.availableQuantity}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : <Alert severity="info">Dispensing data will populate fast-moving analytics here.</Alert>}
            </CardContent></Card>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card><CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Expiry and value</Typography>
              <Stack spacing={1.5}>
                <Chip label={`Stock value estimate: ${money(analytics?.stockValueEstimate ?? 0)}`} color="info" />
                <Typography variant="body2" color="text.secondary">Low stock medicines: {analytics?.lowStockMedicines?.length ?? 0}</Typography>
                <Typography variant="body2" color="text.secondary">Expiry-risk batches: {analytics?.expiryRiskMedicines?.length ?? 0}</Typography>
              </Stack>
            </CardContent></Card>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  );
}
