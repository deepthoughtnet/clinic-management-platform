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
import {
  createInventoryTransaction,
  createMedicine,
  createStock,
  deactivateMedicine,
  getInventoryTransactions,
  getLowStock,
  getMedicines,
  getStocks,
  updateMedicine,
  updateStock,
  type InventoryTransaction,
  type InventoryTransactionInput,
  type InventoryTransactionType,
  type LowStockItem,
  type Medicine,
  type MedicineInput,
  type MedicineType,
  type Stock,
  type StockInput,
  type Timing,
} from "../../api/clinicApi";

type MedicineFormState = {
  medicineName: string;
  medicineType: MedicineType;
  strength: string;
  defaultDosage: string;
  defaultFrequency: string;
  defaultDurationDays: string;
  defaultTiming: Timing | "";
  defaultInstructions: string;
  defaultPrice: string;
  active: boolean;
};

type StockFormState = {
  medicineId: string;
  batchNumber: string;
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

const TABS = [
  { value: "medicines", label: "Medicines" },
  { value: "stocks", label: "Stock" },
  { value: "low-stock", label: "Low stock" },
] as const;

const MEDICINE_TYPES: MedicineType[] = ["TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROP", "OINTMENT", "OTHER"];
const TRANSACTION_TYPES: InventoryTransactionType[] = ["OPENING", "PURCHASE", "SALE", "ADJUSTMENT", "RETURN"];
const TIMING_OPTIONS: Array<{ label: string; value: Timing }> = [
  { label: "Before food", value: "BEFORE_FOOD" },
  { label: "After food", value: "AFTER_FOOD" },
  { label: "With food", value: "WITH_FOOD" },
  { label: "Anytime", value: "ANYTIME" },
];

function emptyMedicineForm(): MedicineFormState {
  return {
    medicineName: "",
    medicineType: "TABLET",
    strength: "",
    defaultDosage: "",
    defaultFrequency: "",
    defaultDurationDays: "",
    defaultTiming: "",
    defaultInstructions: "",
    defaultPrice: "",
    active: true,
  };
}

function emptyStockForm(): StockFormState {
  return {
    medicineId: "",
    batchNumber: "",
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

function medicineInput(form: MedicineFormState): MedicineInput {
  return {
    medicineName: form.medicineName.trim(),
    medicineType: form.medicineType,
    genericName: null,
    brandName: null,
    category: null,
    dosageForm: null,
    strength: form.strength.trim() || null,
    unit: null,
    manufacturer: null,
    defaultDosage: form.defaultDosage.trim() || null,
    defaultFrequency: form.defaultFrequency.trim() || null,
    defaultDurationDays: form.defaultDurationDays.trim() ? Number(form.defaultDurationDays) : null,
    defaultTiming: form.defaultTiming || null,
    defaultInstructions: form.defaultInstructions.trim() || null,
    defaultPrice: form.defaultPrice.trim() ? Number(form.defaultPrice) : null,
    taxRate: null,
    active: form.active,
  };
}

function stockInput(form: StockFormState): StockInput {
  return {
    medicineId: form.medicineId,
    batchNumber: form.batchNumber.trim() || null,
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

export default function InventoryPage() {
  const auth = useAuth();
  const [tab, setTab] = React.useState<(typeof TABS)[number]["value"]>("medicines");
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [stocks, setStocks] = React.useState<Stock[]>([]);
  const [transactions, setTransactions] = React.useState<InventoryTransaction[]>([]);
  const [lowStock, setLowStock] = React.useState<LowStockItem[]>([]);
  const [medicineForm, setMedicineForm] = React.useState<MedicineFormState>(emptyMedicineForm());
  const [stockForm, setStockForm] = React.useState<StockFormState>(emptyStockForm());
  const [transactionForm, setTransactionForm] = React.useState<TransactionFormState>(emptyTransactionForm());
  const [selectedMedicineId, setSelectedMedicineId] = React.useState<string | null>(null);
  const [selectedStockId, setSelectedStockId] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);

  const medicineById = React.useMemo(() => new Map(medicines.map((medicine) => [medicine.id, medicine])), [medicines]);

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const [medicineRows, stockRows, transactionRows, lowStockRows] = await Promise.all([
      getMedicines(auth.accessToken, auth.tenantId),
      getStocks(auth.accessToken, auth.tenantId),
      getInventoryTransactions(auth.accessToken, auth.tenantId),
      getLowStock(auth.accessToken, auth.tenantId),
    ]);
    setMedicines(medicineRows);
    setStocks(stockRows);
    setTransactions(transactionRows);
    setLowStock(lowStockRows);
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

  const saveMedicine = async () => {
    if (!auth.accessToken || !auth.tenantId || !medicineForm.medicineName.trim()) {
      setError("Enter a medicine name.");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const body = medicineInput(medicineForm);
      if (selectedMedicineId) {
        await updateMedicine(auth.accessToken, auth.tenantId, selectedMedicineId, body);
      } else {
        await createMedicine(auth.accessToken, auth.tenantId, body);
      }
      setMedicineForm(emptyMedicineForm());
      setSelectedMedicineId(null);
      await loadAll();
      setSuccess("Medicine saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save medicine");
    } finally {
      setSaving(false);
    }
  };

  const editMedicine = (medicine: Medicine) => {
    setSelectedMedicineId(medicine.id);
    setMedicineForm({
      medicineName: medicine.medicineName,
      medicineType: medicine.medicineType,
      strength: medicine.strength || "",
      defaultDosage: medicine.defaultDosage || "",
      defaultFrequency: medicine.defaultFrequency || "",
      defaultDurationDays: medicine.defaultDurationDays?.toString() || "",
      defaultTiming: medicine.defaultTiming || "",
      defaultInstructions: medicine.defaultInstructions || "",
      defaultPrice: medicine.defaultPrice?.toString() || "",
      active: medicine.active,
    });
  };

  const deactivateSelectedMedicine = async (medicine: Medicine) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await deactivateMedicine(auth.accessToken, auth.tenantId, medicine.id);
      await loadAll();
      if (selectedMedicineId === medicine.id) {
        setMedicineForm(emptyMedicineForm());
        setSelectedMedicineId(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deactivate medicine");
    } finally {
      setSaving(false);
    }
  };

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
      await loadAll();
      setSuccess("Stock saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save stock");
    } finally {
      setSaving(false);
    }
  };

  const editStock = (stock: Stock) => {
    setSelectedStockId(stock.id);
    setStockForm({
      medicineId: stock.medicineId,
      batchNumber: stock.batchNumber || "",
      expiryDate: stock.expiryDate || "",
      quantityOnHand: stock.quantityOnHand.toString(),
      lowStockThreshold: stock.lowStockThreshold?.toString() || "",
      unitCost: stock.unitCost?.toString() || "",
      sellingPrice: stock.sellingPrice?.toString() || "",
      active: stock.active,
    });
  };

  const saveTransaction = async () => {
    if (!auth.accessToken || !auth.tenantId || !transactionForm.medicineId || !transactionForm.quantity.trim()) {
      setError("Select a medicine and quantity before saving a transaction.");
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

  const currentMedicine = selectedMedicineId ? medicines.find((medicine) => medicine.id === selectedMedicineId) || null : null;
  const currentStock = selectedStockId ? stocks.find((stock) => stock.id === selectedStockId) || null : null;

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Inventory
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Medicine catalogue, stock control, transactions, and low-stock visibility.
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

      {tab === "medicines" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    {selectedMedicineId ? "Edit medicine" : "Add medicine"}
                  </Typography>
                  <TextField label="Medicine name" value={medicineForm.medicineName} onChange={(e) => setMedicineForm((current) => ({ ...current, medicineName: e.target.value }))} />
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth>
                        <InputLabel id="medicine-type-label">Type</InputLabel>
                        <Select labelId="medicine-type-label" label="Type" value={medicineForm.medicineType} onChange={(e) => setMedicineForm((current) => ({ ...current, medicineType: String(e.target.value) as MedicineType }))}>
                          {MEDICINE_TYPES.map((type) => (
                            <MenuItem key={type} value={type}>
                              {type}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField label="Strength" value={medicineForm.strength} onChange={(e) => setMedicineForm((current) => ({ ...current, strength: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField label="Default dosage" value={medicineForm.defaultDosage} onChange={(e) => setMedicineForm((current) => ({ ...current, defaultDosage: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField label="Default frequency" value={medicineForm.defaultFrequency} onChange={(e) => setMedicineForm((current) => ({ ...current, defaultFrequency: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField label="Default duration (days)" value={medicineForm.defaultDurationDays} onChange={(e) => setMedicineForm((current) => ({ ...current, defaultDurationDays: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth>
                        <InputLabel id="medicine-timing-label">Default timing</InputLabel>
                        <Select labelId="medicine-timing-label" label="Default timing" value={medicineForm.defaultTiming} onChange={(e) => setMedicineForm((current) => ({ ...current, defaultTiming: String(e.target.value) as Timing | "" }))}>
                          <MenuItem value="">None</MenuItem>
                          {TIMING_OPTIONS.map((item) => (
                            <MenuItem key={item.value} value={item.value}>
                              {item.label}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                    <Grid size={12}>
                      <TextField label="Default instructions" value={medicineForm.defaultInstructions} onChange={(e) => setMedicineForm((current) => ({ ...current, defaultInstructions: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField label="Default price" value={medicineForm.defaultPrice} onChange={(e) => setMedicineForm((current) => ({ ...current, defaultPrice: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth>
                        <InputLabel id="medicine-active-label">Active</InputLabel>
                        <Select labelId="medicine-active-label" label="Active" value={medicineForm.active ? "true" : "false"} onChange={(e) => setMedicineForm((current) => ({ ...current, active: String(e.target.value) === "true" }))}>
                          <MenuItem value="true">Active</MenuItem>
                          <MenuItem value="false">Inactive</MenuItem>
                        </Select>
                      </FormControl>
                    </Grid>
                  </Grid>
                  <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                    <Button variant="contained" onClick={() => void saveMedicine()} disabled={saving}>
                      {selectedMedicineId ? "Update" : "Create"}
                    </Button>
                    <Button
                      onClick={() => {
                        setMedicineForm(emptyMedicineForm());
                        setSelectedMedicineId(null);
                      }}
                    >
                      Reset
                    </Button>
                    {currentMedicine ? (
                      <Button color="error" variant="outlined" onClick={() => void deactivateSelectedMedicine(currentMedicine)} disabled={saving || !currentMedicine.active}>
                        Deactivate
                      </Button>
                    ) : null}
                  </Box>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 8 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Medicine catalogue
                  </Typography>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Strength</TableCell>
                        <TableCell>Default price</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {medicines.map((medicine) => (
                        <TableRow key={medicine.id} hover selected={medicine.id === selectedMedicineId}>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {medicine.medicineName}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {medicine.defaultDosage || "No default dosage"}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{medicine.medicineType}</TableCell>
                          <TableCell>{medicine.strength || "-"}</TableCell>
                          <TableCell>{medicine.defaultPrice ? medicine.defaultPrice.toFixed(2) : "-"}</TableCell>
                          <TableCell>
                            <Chip size="small" label={medicine.active ? "Active" : "Inactive"} color={medicine.active ? "success" : "default"} />
                          </TableCell>
                          <TableCell align="right">
                            <Button size="small" onClick={() => editMedicine(medicine)}>
                              Edit
                            </Button>
                            <Button size="small" color="error" onClick={() => void deactivateSelectedMedicine(medicine)} disabled={!medicine.active}>
                              Deactivate
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      {tab === "stocks" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    {selectedStockId ? "Edit stock" : "Add stock"}
                  </Typography>
                  <FormControl fullWidth>
                    <InputLabel id="stock-medicine-label">Medicine</InputLabel>
                    <Select labelId="stock-medicine-label" label="Medicine" value={stockForm.medicineId} onChange={(e) => setStockForm((current) => ({ ...current, medicineId: String(e.target.value) }))}>
                      <MenuItem value="">Select medicine</MenuItem>
                      {medicines.map((medicine) => (
                        <MenuItem key={medicine.id} value={medicine.id}>
                          {medicine.medicineName}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <TextField label="Batch number" value={stockForm.batchNumber} onChange={(e) => setStockForm((current) => ({ ...current, batchNumber: e.target.value }))} />
                  <TextField label="Expiry date" type="date" value={stockForm.expiryDate} onChange={(e) => setStockForm((current) => ({ ...current, expiryDate: e.target.value }))} InputLabelProps={{ shrink: true }} />
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField label="Quantity on hand" value={stockForm.quantityOnHand} onChange={(e) => setStockForm((current) => ({ ...current, quantityOnHand: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField label="Low stock threshold" value={stockForm.lowStockThreshold} onChange={(e) => setStockForm((current) => ({ ...current, lowStockThreshold: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField label="Unit cost" value={stockForm.unitCost} onChange={(e) => setStockForm((current) => ({ ...current, unitCost: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField label="Selling price" value={stockForm.sellingPrice} onChange={(e) => setStockForm((current) => ({ ...current, sellingPrice: e.target.value }))} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControl fullWidth>
                        <InputLabel id="stock-active-label">Active</InputLabel>
                        <Select labelId="stock-active-label" label="Active" value={stockForm.active ? "true" : "false"} onChange={(e) => setStockForm((current) => ({ ...current, active: String(e.target.value) === "true" }))}>
                          <MenuItem value="true">Active</MenuItem>
                          <MenuItem value="false">Inactive</MenuItem>
                        </Select>
                      </FormControl>
                    </Grid>
                  </Grid>
                  <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                    <Button variant="contained" onClick={() => void saveStock()} disabled={saving}>
                      {selectedStockId ? "Update" : "Create"}
                    </Button>
                    <Button
                      onClick={() => {
                        setStockForm(emptyStockForm());
                        setSelectedStockId(null);
                      }}
                    >
                      Reset
                    </Button>
                    {currentStock ? (
                      <Chip size="small" label={`${currentStock.medicineName} • ${currentStock.batchNumber || "No batch"}`} variant="outlined" />
                    ) : null}
                  </Box>
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ mt: 2 }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Inventory transaction
                  </Typography>
                  <FormControl fullWidth>
                    <InputLabel id="transaction-medicine-label">Medicine</InputLabel>
                    <Select labelId="transaction-medicine-label" label="Medicine" value={transactionForm.medicineId} onChange={(e) => setTransactionForm((current) => ({ ...current, medicineId: String(e.target.value) }))}>
                      <MenuItem value="">Select medicine</MenuItem>
                      {medicines.map((medicine) => (
                        <MenuItem key={medicine.id} value={medicine.id}>
                          {medicine.medicineName}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <TextField label="Stock batch ID" value={transactionForm.stockBatchId} onChange={(e) => setTransactionForm((current) => ({ ...current, stockBatchId: e.target.value }))} />
                  <FormControl fullWidth>
                    <InputLabel id="transaction-type-label">Type</InputLabel>
                    <Select labelId="transaction-type-label" label="Type" value={transactionForm.transactionType} onChange={(e) => setTransactionForm((current) => ({ ...current, transactionType: String(e.target.value) as InventoryTransactionType }))}>
                      {TRANSACTION_TYPES.map((type) => (
                        <MenuItem key={type} value={type}>
                          {type}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <TextField label="Quantity" value={transactionForm.quantity} onChange={(e) => setTransactionForm((current) => ({ ...current, quantity: e.target.value }))} />
                  <TextField label="Reference type" value={transactionForm.referenceType} onChange={(e) => setTransactionForm((current) => ({ ...current, referenceType: e.target.value }))} />
                  <TextField label="Reference ID" value={transactionForm.referenceId} onChange={(e) => setTransactionForm((current) => ({ ...current, referenceId: e.target.value }))} />
                  <TextField label="Notes" value={transactionForm.notes} onChange={(e) => setTransactionForm((current) => ({ ...current, notes: e.target.value }))} multiline minRows={2} />
                  <Button variant="contained" onClick={() => void saveTransaction()} disabled={saving}>
                    Save transaction
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 8 }}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Stock list
                  </Typography>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Medicine</TableCell>
                        <TableCell>Batch</TableCell>
                        <TableCell>Expiry</TableCell>
                        <TableCell align="right">Qty</TableCell>
                        <TableCell align="right">Threshold</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {stocks.map((stock) => (
                        <TableRow key={stock.id} hover selected={stock.id === selectedStockId}>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {stock.medicineName}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {stock.medicineType}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{stock.batchNumber || "-"}</TableCell>
                          <TableCell>{stock.expiryDate || "-"}</TableCell>
                          <TableCell align="right">{stock.quantityOnHand}</TableCell>
                          <TableCell align="right">{stock.lowStockThreshold ?? "-"}</TableCell>
                          <TableCell>
                            <Chip size="small" label={stock.active ? "Active" : "Inactive"} color={stock.active ? statusColor(stock.quantityOnHand, stock.lowStockThreshold) : "default"} />
                          </TableCell>
                          <TableCell align="right">
                            <Button size="small" onClick={() => editStock(stock)}>
                              Edit
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ mt: 2 }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    Inventory transactions
                  </Typography>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Medicine</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell align="right">Quantity</TableCell>
                        <TableCell>Reference</TableCell>
                        <TableCell>Notes</TableCell>
                        <TableCell>Created</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {transactions.map((transaction) => (
                        <TableRow key={transaction.id}>
                          <TableCell>{medicineById.get(transaction.medicineId)?.medicineName || transaction.medicineId}</TableCell>
                          <TableCell>{transaction.transactionType}</TableCell>
                          <TableCell align="right">{transaction.quantity}</TableCell>
                          <TableCell>{transaction.referenceType || "-"}</TableCell>
                          <TableCell>{transaction.notes || "-"}</TableCell>
                          <TableCell>{new Date(transaction.createdAt).toLocaleString()}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
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
                    <Alert severity="info">No low-stock items were found.</Alert>
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
                          <TableRow key={row.stockId}>
                            <TableCell>{row.medicineName}</TableCell>
                            <TableCell>{row.batchNumber || "-"}</TableCell>
                            <TableCell>{row.expiryDate || "-"}</TableCell>
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
                    Inventory transactions and low-stock checks run tenant-side only. Use the stock tab to add batches and adjust quantities.
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
    </Stack>
  );
}
