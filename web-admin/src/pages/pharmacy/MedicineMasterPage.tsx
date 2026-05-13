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
import {
  activateMedicine,
  createMedicine,
  deactivateMedicine,
  getMedicines,
  updateMedicine,
  type Medicine,
  type MedicineInput,
} from "../../api/clinicApi";

const emptyForm: MedicineInput = {
  medicineName: "",
  medicineType: "TABLET",
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
      [row.medicineName, row.category, row.dosageForm, row.strength, row.unit, row.manufacturer]
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

  if (!auth.tenantId) return <Alert severity="info">Select a tenant to access Medicine Master.</Alert>;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Medicine Master</Typography>
          <Typography variant="body2" color="text.secondary">Manage medicine catalog with pricing, tax, form, strength, and lifecycle state.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => void load()}>Refresh</Button>
          {canEdit ? <Button variant="contained" onClick={openCreate}>Add Medicine</Button> : null}
        </Stack>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Card>
        <CardContent>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField fullWidth size="small" label="Search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="name/category/form/manufacturer" />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null}

      {!loading ? (
        <Card>
          <CardContent>
            {filtered.length === 0 ? (
              <Alert severity="info">No medicines found.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Form</TableCell>
                    <TableCell>Strength</TableCell>
                    <TableCell>Unit</TableCell>
                    <TableCell>Manufacturer</TableCell>
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
                      <TableCell>{row.category || "-"}</TableCell>
                      <TableCell>{row.dosageForm || row.medicineType}</TableCell>
                      <TableCell>{row.strength || "-"}</TableCell>
                      <TableCell>{row.unit || "-"}</TableCell>
                      <TableCell>{row.manufacturer || "-"}</TableCell>
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
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Category" value={form.category || ""} onChange={(e) => setForm((v) => ({ ...v, category: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Form" value={form.dosageForm || ""} onChange={(e) => setForm((v) => ({ ...v, dosageForm: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Strength" value={form.strength || ""} onChange={(e) => setForm((v) => ({ ...v, strength: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Unit" value={form.unit || ""} onChange={(e) => setForm((v) => ({ ...v, unit: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Manufacturer" value={form.manufacturer || ""} onChange={(e) => setForm((v) => ({ ...v, manufacturer: e.target.value || null }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="number" label="Default price" value={form.defaultPrice ?? ""} onChange={(e) => setForm((v) => ({ ...v, defaultPrice: e.target.value ? Number(e.target.value) : null }))} /></Grid>
            <Grid size={{ xs: 12, md: 3 }}><TextField fullWidth type="number" label="Tax %" value={form.taxRate ?? ""} onChange={(e) => setForm((v) => ({ ...v, taxRate: e.target.value ? Number(e.target.value) : null }))} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditorOpen(false)}>Close</Button>
          <Button variant="contained" disabled={saving} onClick={() => void save()}>{saving ? "Saving..." : "Save"}</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
