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
import {
  activateMedicine,
  createMedicine,
  deactivateMedicine,
  importMedicinesCsv,
  getMedicines,
  updateMedicine,
  type Medicine,
  type MedicineInput,
  type MedicineImportResult,
} from "../../api/clinicApi";

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

export default function MedicineMasterPage() {
  const auth = useAuth();
  const [rows, setRows] = React.useState<Medicine[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const [editorOpen, setEditorOpen] = React.useState(false);
  const [importOpen, setImportOpen] = React.useState(false);
  const [importResult, setImportResult] = React.useState<MedicineImportResult | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const [editing, setEditing] = React.useState<Medicine | null>(null);
  const [form, setForm] = React.useState<MedicineInput>(emptyForm);

  const canEdit = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PHARMACIST") || auth.rolesUpper.includes("PHARMA") || auth.rolesUpper.includes("PHARMACY");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      setRows(await getMedicines(auth.accessToken, auth.tenantId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load medicines");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const filtered = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return rows;
    return rows.filter((row) =>
      [row.medicineName, row.barcode, row.qrCode, row.externalCode, row.genericName, row.brandName, row.category, row.dosageForm, row.strength, row.unit, row.manufacturer, row.defaultDosage, row.defaultFrequency, row.defaultInstructions]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [rows, search]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setEditorOpen(true);
  };

  const openEdit = (row: Medicine) => {
    setEditing(row);
    setForm({
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
    });
    setEditorOpen(true);
  };

  const save = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      if (editing) {
        await updateMedicine(auth.accessToken, auth.tenantId, editing.id, form);
      } else {
        await createMedicine(auth.accessToken, auth.tenantId, form);
      }
      setEditorOpen(false);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save medicine");
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (row: Medicine) => {
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
  };

  const openImportFilePicker = () => fileInputRef.current?.click();

  const downloadTemplate = () => {
    const csv = [
      "medicineName,genericName,brandName,category,form,strength,unit,manufacturer,defaultDosage,defaultFrequency,defaultDurationDays,defaultTiming,defaultInstructions,defaultPrice,taxPercent,batchNumber,expiryDate,quantityOnHand,lowStockThreshold,unitCost,sellingPrice",
      "Paracetamol 650,Paracetamol,Dolo,Analgesic,Tablet,650,mg,Micro Labs,1 tablet,2 times/day,5,After food,Take after meals,2,5,,,,,,",
    ].join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "medicine-import-template.csv";
    link.click();
    URL.revokeObjectURL(url);
  };

  const handleImportFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !auth.accessToken || !auth.tenantId) return;
    setError(null);
    try {
      setImportResult(await importMedicinesCsv(auth.accessToken, auth.tenantId, file));
      setImportOpen(true);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to import medicine CSV");
    } finally {
      event.target.value = "";
    }
  };

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to access Medicine Master.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Medicine Master</Typography>
          <Typography variant="body2" color="text.secondary">Manage operational medicine catalog details, including brand/generic names, dosage defaults, timing, and instructions.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
          {canEdit ? <Button variant="outlined" onClick={downloadTemplate}>Download template</Button> : null}
          {canEdit ? <Button variant="outlined" onClick={openImportFilePicker}>Import CSV</Button> : null}
          {canEdit ? <Button variant="contained" onClick={openCreate}>Add Medicine</Button> : null}
        </Stack>
      </Box>
      <input ref={fileInputRef} type="file" accept=".csv,text/csv" hidden onChange={handleImportFile} />

      {error ? <Alert severity="error">{error}</Alert> : null}
      {importResult ? (
        <Alert severity="info">
          Import processed {importResult.totalRows} rows. Created {importResult.created}, updated {importResult.updated}, skipped {importResult.skipped}, failed {importResult.failed}.
        </Alert>
      ) : null}

      <Card>
        <CardContent>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField fullWidth size="small" label="Search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="name/code/category/form/manufacturer" />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card>
          <CardContent>
            {filtered.length === 0 ? (
              <Alert severity="info">No medicine catalog entries are available yet. Add the first operational medicine record to start dispensing and stock tracking.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Code</TableCell>
                    <TableCell>Generic / Brand</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Form</TableCell>
                    <TableCell>Dosage / Frequency</TableCell>
                    <TableCell>Duration / Timing</TableCell>
                    <TableCell>Instructions</TableCell>
                    <TableCell align="right">Price</TableCell>
                    <TableCell align="right">Tax %</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filtered.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell sx={{ fontWeight: 700 }}>{row.medicineName}</TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2">{row.barcode || row.externalCode || "-"}</Typography>
                          <Typography variant="caption" color="text.secondary">{row.qrCode || "-"}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2">{row.genericName || "-"}</Typography>
                          <Typography variant="caption" color="text.secondary">{row.brandName || "-"}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>{row.category || "-"}</TableCell>
                      <TableCell>{row.dosageForm || row.medicineType}</TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2">{row.defaultDosage || "-"}</Typography>
                          <Typography variant="caption" color="text.secondary">{row.defaultFrequency || "-"}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2">{row.defaultDurationDays != null ? `${row.defaultDurationDays} days` : "-"}</Typography>
                          <Typography variant="caption" color="text.secondary">{row.defaultTiming || "-"}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 220 }}>{row.defaultInstructions || "-"}</TableCell>
                      <TableCell align="right">{row.defaultPrice?.toFixed(2) ?? "-"}</TableCell>
                      <TableCell align="right">{row.taxRate?.toFixed(2) ?? "-"}</TableCell>
                      <TableCell><Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} /></TableCell>
                      <TableCell align="right">
                        {canEdit ? <Button size="small" onClick={() => openEdit(row)}>Edit</Button> : null}
                        {canEdit ? <Button size="small" color={row.active ? "error" : "success"} onClick={() => void toggleActive(row)}>{row.active ? "Deactivate" : "Activate"}</Button> : null}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      ) : null}

      <Dialog open={editorOpen} onClose={() => setEditorOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editing ? "Edit Medicine" : "Add Medicine"}</DialogTitle>
        <DialogContent>
          <Grid container spacing={1.5} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Medicine name" value={form.medicineName} onChange={(e) => setForm((v) => ({ ...v, medicineName: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CodeScannerField label="Barcode" value={form.barcode || ""} onChange={(next) => setForm((v) => ({ ...v, barcode: next || null }))} placeholder="Scan or enter barcode" />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CodeScannerField label="QR code" value={form.qrCode || ""} onChange={(next) => setForm((v) => ({ ...v, qrCode: next || null }))} placeholder="Scan or enter QR code" />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CodeScannerField label="External code" value={form.externalCode || ""} onChange={(next) => setForm((v) => ({ ...v, externalCode: next || null }))} placeholder="Scan or enter code" />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Generic name" value={form.genericName || ""} onChange={(e) => setForm((v) => ({ ...v, genericName: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Brand name" value={form.brandName || ""} onChange={(e) => setForm((v) => ({ ...v, brandName: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Category" value={form.category || ""} onChange={(e) => setForm((v) => ({ ...v, category: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Form" value={form.dosageForm || ""} onChange={(e) => setForm((v) => ({ ...v, dosageForm: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Strength" value={form.strength || ""} onChange={(e) => setForm((v) => ({ ...v, strength: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Unit" value={form.unit || ""} onChange={(e) => setForm((v) => ({ ...v, unit: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Manufacturer" value={form.manufacturer || ""} onChange={(e) => setForm((v) => ({ ...v, manufacturer: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Default dosage" value={form.defaultDosage || ""} onChange={(e) => setForm((v) => ({ ...v, defaultDosage: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Default frequency" value={form.defaultFrequency || ""} onChange={(e) => setForm((v) => ({ ...v, defaultFrequency: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="number" label="Default duration (days)" value={form.defaultDurationDays ?? ""} onChange={(e) => setForm((v) => ({ ...v, defaultDurationDays: e.target.value ? Number(e.target.value) : null }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                fullWidth
                label="Default timing"
                value={form.defaultTiming || ""}
                onChange={(e) =>
                  setForm((v) => ({
                    ...v,
                    defaultTiming: (e.target.value || null) as MedicineInput["defaultTiming"],
                  }))
                }
                placeholder="BEFORE_FOOD / AFTER_FOOD / WITH_FOOD / ANYTIME"
              />
            </Grid>
            <Grid size={{ xs: 12, md: 12 }}><TextField fullWidth multiline minRows={2} label="Default instructions" value={form.defaultInstructions || ""} onChange={(e) => setForm((v) => ({ ...v, defaultInstructions: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="number" label="Default price" value={form.defaultPrice ?? ""} onChange={(e) => setForm((v) => ({ ...v, defaultPrice: e.target.value ? Number(e.target.value) : null }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="number" label="Tax %" value={form.taxRate ?? ""} onChange={(e) => setForm((v) => ({ ...v, taxRate: e.target.value ? Number(e.target.value) : null }))} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditorOpen(false)}>Close</Button>
          <Button variant="contained" disabled={saving} onClick={() => void save()}>{saving ? "Saving..." : "Save"}</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={importOpen} onClose={() => setImportOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Medicine CSV Import Result</DialogTitle>
        <DialogContent>
          {importResult ? (
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Alert severity="success">
                Rows: {importResult.totalRows}. Created {importResult.created}. Updated {importResult.updated}. Skipped {importResult.skipped}. Failed {importResult.failed}.
              </Alert>
              <Typography variant="body2" color="text.secondary">
                Required columns: medicineName, genericName, brandName, category, form, strength, unit, manufacturer, defaultDosage, defaultFrequency, defaultDurationDays, defaultTiming, defaultInstructions, defaultPrice, taxPercent.
              </Typography>
              {importResult.failedRowsCsv ? (
                <Button
                  variant="outlined"
                  onClick={() => {
                    const blob = new Blob([importResult.failedRowsCsv], { type: "text/csv;charset=utf-8" });
                    const url = URL.createObjectURL(blob);
                    const link = document.createElement("a");
                    link.href = url;
                    link.download = "medicine-import-failed-rows.csv";
                    link.click();
                    URL.revokeObjectURL(url);
                  }}
                >
                  Download failed rows CSV
                </Button>
              ) : null}
              <Table size="small">
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
                      <TableCell><Chip size="small" label={row.status} color={row.status === "FAILED" ? "error" : row.status === "SKIPPED" ? "default" : "success"} /></TableCell>
                      <TableCell>{row.message}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setImportOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
