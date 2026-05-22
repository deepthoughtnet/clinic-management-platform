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
import { CompactEmptyState, compactCardContentSx } from "../../components/compact/CompactUi";
import CodeScannerField from "../../components/pharmacy/CodeScannerField";
import {
  activateMedicine,
  createMedicine,
  deactivateMedicine,
  getMedicineImportTemplate,
  getMedicines,
  importMedicinesCsv,
  updateMedicine,
  type Medicine,
  type MedicineImportResult,
  type MedicineInput,
  type MedicineType,
} from "../../api/clinicApi";
import {
  buildMedicineTemplateCsv,
  MEDICINE_IMPORT_COLUMNS,
  parseMedicineImportPreview,
  type MedicineImportPreview,
} from "./medicineCsv";

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

export default function MedicineMasterPage() {
  const auth = useAuth();
  const [rows, setRows] = React.useState<Medicine[]>([]);
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
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const [editing, setEditing] = React.useState<Medicine | null>(null);
  const [form, setForm] = React.useState<MedicineInput>(emptyForm);
  const [statusFilter, setStatusFilter] = React.useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
  const [typeFilter, setTypeFilter] = React.useState<"ALL" | MedicineType>("ALL");
  const [categoryFilter, setCategoryFilter] = React.useState<string>("ALL");
  const [actionAnchor, setActionAnchor] = React.useState<HTMLElement | null>(null);
  const [actionRow, setActionRow] = React.useState<Medicine | null>(null);

  const canManage = auth.hasPermission("inventory.manage")
    || auth.hasPermission("vaccination.manage")
    || auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("PHARMACIST")
    || auth.rolesUpper.includes("PHARMACY")
    || auth.rolesUpper.includes("PHARMA");

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

  React.useEffect(() => {
    if (editing) {
      setEditorOpen(true);
      return;
    }
    setEditorOpen(rows.length === 0);
  }, [editing, rows.length]);

  const filtered = React.useMemo(() => {
    const term = search.trim().toLowerCase();
    return rows.filter((row) => {
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
      return matchesTerm && matchesStatus && matchesType && matchesCategory;
    });
  }, [categoryFilter, rows, search, statusFilter, typeFilter]);

  const activeCount = React.useMemo(() => rows.filter((row) => row.active).length, [rows]);
  const inactiveCount = rows.length - activeCount;
  const categoryOptions = React.useMemo(
    () => Array.from(new Set(rows.map((row) => row.category || "Uncategorized"))).sort((a, b) => a.localeCompare(b)),
    [rows],
  );

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setEditorOpen(true);
  };

  const openEdit = (row: Medicine) => {
    setEditing(row);
    setForm(formForRow(row));
    setEditorOpen(true);
  };

  const closeEditor = () => {
    setEditing(null);
    setForm(emptyForm);
    setEditorOpen(rows.length === 0);
  };

  const resetEditor = () => {
    setForm(editing ? formForRow(editing) : emptyForm);
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
      closeEditor();
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

  const openRowActions = (event: React.MouseEvent<HTMLElement>, row: Medicine) => {
    setActionAnchor(event.currentTarget);
    setActionRow(row);
  };

  const closeRowActions = () => {
    setActionAnchor(null);
    setActionRow(null);
  };

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

      <Card>
        <CardContent sx={{ py: 1.5 }}>
          <Grid container spacing={1.25} alignItems="center">
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                size="small"
                label="Search medicine catalog"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Name, barcode, brand, form, strength, manufacturer"
              />
            </Grid>
            <Grid size={{ xs: 12, md: 8 }}>
              <Stack direction="row" spacing={1} flexWrap="wrap" justifyContent={{ xs: "flex-start", md: "flex-end" }} useFlexGap>
                <Chip
                  size="small"
                  label="Active"
                  color={statusFilter === "ACTIVE" ? "success" : "default"}
                  variant={statusFilter === "ACTIVE" ? "filled" : "outlined"}
                  onClick={() => setStatusFilter((current) => current === "ACTIVE" ? "ALL" : "ACTIVE")}
                />
                <Chip
                  size="small"
                  label="Inactive"
                  color={statusFilter === "INACTIVE" ? "warning" : "default"}
                  variant={statusFilter === "INACTIVE" ? "filled" : "outlined"}
                  onClick={() => setStatusFilter((current) => current === "INACTIVE" ? "ALL" : "INACTIVE")}
                />
                <TextField
                  select
                  size="small"
                  label="Type"
                  value={typeFilter}
                  onChange={(e) => setTypeFilter(e.target.value as "ALL" | MedicineType)}
                  sx={{ minWidth: 130 }}
                >
                  <MenuItem value="ALL">All types</MenuItem>
                  {medicineTypeOptions.map((option) => (
                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                  ))}
                </TextField>
                <TextField
                  select
                  size="small"
                  label="Category"
                  value={categoryFilter}
                  onChange={(e) => setCategoryFilter(e.target.value)}
                  sx={{ minWidth: 150 }}
                >
                  <MenuItem value="ALL">All categories</MenuItem>
                  {categoryOptions.map((option) => (
                    <MenuItem key={option} value={option}>{option}</MenuItem>
                  ))}
                </TextField>
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {canManage ? (
        <Accordion expanded={editorOpen} onChange={(_, expanded) => setEditorOpen(expanded)} disableGutters sx={{ "&:before": { display: "none" }, borderRadius: 4, overflow: "hidden" }}>
          <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ px: 2, py: 0.5 }}>
            <Stack spacing={0.35}>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                {editing ? `Editing: ${editing.medicineName}` : "Add Medicine"}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Maintain medicine identity, scan codes, dosage defaults, and price metadata without leaving the catalogue workspace.
              </Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails sx={{ px: 2, pb: 2, pt: 0 }}>
            <Grid container spacing={1.25} sx={{ mt: 0.25 }}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth size="small" label="Medicine name" value={form.medicineName} onChange={(e) => setForm((v) => ({ ...v, medicineName: e.target.value }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth select size="small" label="Type" value={form.medicineType} onChange={(e) => setForm((v) => ({ ...v, medicineType: e.target.value as MedicineType }))}>
                  {medicineTypeOptions.map((option) => (
                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth select size="small" label="Status" value={form.active ? "true" : "false"} onChange={(e) => setForm((v) => ({ ...v, active: e.target.value === "true" }))}>
                  <MenuItem value="true">Active</MenuItem>
                  <MenuItem value="false">Inactive</MenuItem>
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <CodeScannerField size="small" label="Barcode" value={form.barcode || ""} onChange={(next) => setForm((v) => ({ ...v, barcode: next || null }))} placeholder="Scan or enter barcode" />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <CodeScannerField size="small" label="QR code" value={form.qrCode || ""} onChange={(next) => setForm((v) => ({ ...v, qrCode: next || null }))} placeholder="Scan or enter QR code" />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <CodeScannerField size="small" label="External code" value={form.externalCode || ""} onChange={(next) => setForm((v) => ({ ...v, externalCode: next || null }))} placeholder="Scan or enter code" />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth size="small" label="Generic name" value={form.genericName || ""} onChange={(e) => setForm((v) => ({ ...v, genericName: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth size="small" label="Brand name" value={form.brandName || ""} onChange={(e) => setForm((v) => ({ ...v, brandName: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth size="small" label="Category" value={form.category || ""} onChange={(e) => setForm((v) => ({ ...v, category: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField fullWidth size="small" label="Form" value={form.dosageForm || ""} onChange={(e) => setForm((v) => ({ ...v, dosageForm: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField fullWidth size="small" label="Strength" value={form.strength || ""} onChange={(e) => setForm((v) => ({ ...v, strength: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField fullWidth size="small" label="Unit" value={form.unit || ""} onChange={(e) => setForm((v) => ({ ...v, unit: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth size="small" label="Manufacturer" value={form.manufacturer || ""} onChange={(e) => setForm((v) => ({ ...v, manufacturer: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth size="small" label="Default dosage" value={form.defaultDosage || ""} onChange={(e) => setForm((v) => ({ ...v, defaultDosage: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth size="small" label="Default frequency" value={form.defaultFrequency || ""} onChange={(e) => setForm((v) => ({ ...v, defaultFrequency: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth size="small" type="number" label="Default duration (days)" value={form.defaultDurationDays ?? ""} onChange={(e) => setForm((v) => ({ ...v, defaultDurationDays: e.target.value ? Number(e.target.value) : null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth size="small" label="Default timing" value={form.defaultTiming || ""} onChange={(e) => setForm((v) => ({ ...v, defaultTiming: (e.target.value || null) as MedicineInput["defaultTiming"] }))} placeholder="BEFORE_FOOD / AFTER_FOOD / WITH_FOOD / ANYTIME" />
              </Grid>
              <Grid size={12}>
                <TextField fullWidth size="small" multiline minRows={2} label="Default instructions" value={form.defaultInstructions || ""} onChange={(e) => setForm((v) => ({ ...v, defaultInstructions: e.target.value || null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth size="small" type="number" label="Default price" value={form.defaultPrice ?? ""} onChange={(e) => setForm((v) => ({ ...v, defaultPrice: e.target.value ? Number(e.target.value) : null }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField fullWidth size="small" type="number" label="Tax %" value={form.taxRate ?? ""} onChange={(e) => setForm((v) => ({ ...v, taxRate: e.target.value ? Number(e.target.value) : null }))} />
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
            {filtered.length === 0 ? (
              <CompactEmptyState
                title={rows.length === 0 ? "No medicines in the catalogue yet." : "No medicines match the current filters."}
                subtitle={rows.length === 0
                  ? "Start with Add Medicine for a single entry, or upload the CSV template to seed the catalogue in bulk."
                  : "Clear or adjust the current search and filter combination to show matching medicine records."}
                action={canManage ? (
                  <Stack direction="row" spacing={1}>
                    <Button size="small" onClick={openCreate}>Add Medicine</Button>
                    <Button size="small" variant="outlined" onClick={openImportFilePicker}>Upload CSV</Button>
                  </Stack>
                ) : undefined}
              />
            ) : (
              <Stack spacing={1.25}>
                <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                    Medicine catalogue
                  </Typography>
                  <Chip size="small" variant="outlined" label={`${filtered.length} visible medicines`} />
                </Box>
                <TableContainer sx={{ maxHeight: 520, overflowX: "auto" }}>
                  <Table size="small" stickyHeader>
                    <TableHead>
                      <TableRow>
                        <TableCell sx={{ minWidth: 180 }}>Name</TableCell>
                        <TableCell sx={{ minWidth: 150 }}>Code</TableCell>
                        <TableCell sx={{ minWidth: 180 }}>Generic / Brand</TableCell>
                        <TableCell sx={{ minWidth: 120 }}>Category</TableCell>
                        <TableCell sx={{ minWidth: 100 }}>Type</TableCell>
                        <TableCell sx={{ minWidth: 160 }}>Dosage / Frequency</TableCell>
                        <TableCell sx={{ minWidth: 160 }}>Duration / Timing</TableCell>
                        <TableCell sx={{ minWidth: 220 }}>Instructions</TableCell>
                        <TableCell align="right" sx={{ minWidth: 90 }}>Price</TableCell>
                        <TableCell align="right" sx={{ minWidth: 70 }}>Tax %</TableCell>
                        <TableCell sx={{ minWidth: 90 }}>Status</TableCell>
                        <TableCell align="right" sx={{ minWidth: 80 }}>Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filtered.map((row) => (
                        <TableRow key={row.id} hover sx={{ "& td": { py: 0.8, verticalAlign: "top" } }}>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{row.medicineName}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.strength || row.unit || row.manufacturer || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2">{row.barcode || row.externalCode || "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.qrCode || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2">{row.genericName || "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.brandName || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{row.category || "-"}</TableCell>
                          <TableCell>{row.dosageForm || row.medicineType}</TableCell>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2">{row.defaultDosage || "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.defaultFrequency || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.2}>
                              <Typography variant="body2">{row.defaultDurationDays != null ? `${row.defaultDurationDays} days` : "-"}</Typography>
                              <Typography variant="caption" color="text.secondary">{row.defaultTiming || "-"}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 220 }}>
                            <Typography variant="caption" color="text.secondary">{row.defaultInstructions || "-"}</Typography>
                          </TableCell>
                          <TableCell align="right">{formatPrice(row.defaultPrice)}</TableCell>
                          <TableCell align="right">{row.taxRate?.toFixed(2) ?? "-"}</TableCell>
                          <TableCell><Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} /></TableCell>
                          <TableCell align="right">
                            {canManage ? (
                              <IconButton size="small" onClick={(event) => openRowActions(event, row)}>
                                <MoreHorizRounded fontSize="small" />
                              </IconButton>
                            ) : null}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Stack>
            )}
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
