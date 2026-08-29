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
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import EditRoundedIcon from "@mui/icons-material/EditRounded";

import { fileUploadSchema, firstZodError, mapZodErrors, vaccinationMasterSchema } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import RequiredLabel from "../../components/forms/RequiredLabel";
import { CompactEmptyState, CompactTableFrame, compactChipSx } from "../../components/compact/CompactUi";
import {
  createVaccine,
  deactivateVaccine,
  exportVaccinesCsv,
  getMedicines,
  getVaccineImportTemplate,
  getVaccines,
  importVaccinesCsv,
  type Medicine,
  type VaccineCsvImportResponse,
  type VaccineInput,
  type VaccineMaster,
  updateVaccine,
} from "../../api/clinicApi";
import { buildVaccineExportCsv, buildVaccineTemplateCsv, parseVaccineImportPreview, type VaccineImportPreview } from "./vaccinationCsv";

type VaccineFormState = VaccineInput;

const ROUTE_OPTIONS = [
  { value: "IM", label: "IM" },
  { value: "SC", label: "SC" },
  { value: "ORAL", label: "ORAL" },
  { value: "NASAL", label: "NASAL" },
  { value: "ID", label: "ID" },
] as const;

const SCHEDULE_TYPE_OPTIONS = [
  { value: "UIP", label: "UIP" },
  { value: "IAP", label: "IAP" },
  { value: "CLINIC_CUSTOM", label: "CLINIC_CUSTOM" },
  { value: "TRAVEL", label: "TRAVEL" },
  { value: "ADULT", label: "ADULT" },
] as const;

const RECOMMENDATION_POLICY_OPTIONS = [
  { value: "STANDARD_CHILDHOOD", label: "STANDARD_CHILDHOOD" },
  { value: "CHILDHOOD_CATCHUP", label: "CHILDHOOD_CATCHUP" },
  { value: "ADULT_ROUTINE", label: "ADULT_ROUTINE" },
  { value: "ADULT_RISK_BASED", label: "ADULT_RISK_BASED" },
  { value: "PREGNANCY", label: "PREGNANCY" },
  { value: "TRAVEL", label: "TRAVEL" },
  { value: "OCCUPATIONAL", label: "OCCUPATIONAL" },
  { value: "RECURRING", label: "RECURRING" },
  { value: "CLINIC_CUSTOM", label: "CLINIC_CUSTOM" },
] as const;

const CATCH_UP_POLICY_OPTIONS = [
  { value: "NONE", label: "NONE" },
  { value: "ALLOWED_UNTIL_AGE", label: "ALLOWED_UNTIL_AGE" },
  { value: "LIFETIME", label: "LIFETIME" },
  { value: "CLINICIAN_DECISION", label: "CLINICIAN_DECISION" },
] as const;

const APPLICABLE_AGE_GROUP_OPTIONS = [
  { value: "NEWBORN", label: "NEWBORN" },
  { value: "INFANT", label: "INFANT" },
  { value: "TODDLER", label: "TODDLER" },
  { value: "CHILD", label: "CHILD" },
  { value: "ADOLESCENT", label: "ADOLESCENT" },
  { value: "ADULT", label: "ADULT" },
  { value: "OLDER_ADULT", label: "OLDER_ADULT" },
  { value: "ALL", label: "ALL" },
] as const;

function emptyVaccineForm(): VaccineFormState {
  return {
    vaccineName: "",
    description: null,
    manufacturer: null,
    brandName: null,
    vaccineGroup: null,
    doseNumber: null,
    route: null,
    administrationSite: null,
    storageTemperature: null,
    ndcBarcode: null,
    inventoryItemId: null,
    inventoryItemCode: null,
    stockTrackingEnabled: false,
    scheduleType: null,
    ageGroup: null,
    minAgeDays: null,
    recommendedAgeDays: null,
    maxAgeDays: null,
    gapDays: null,
    recommendedGapDays: null,
    defaultPrice: null,
    boosterGapDays: null,
    boosterRules: null,
    recurring: false,
    recurrenceDays: null,
    recommendationPolicy: null,
    catchUpPolicy: null,
    catchUpMaxAgeDays: null,
    applicableAgeGroup: null,
    clinicalIndications: null,
    active: true,
  };
}

function formForVaccine(vaccine: VaccineMaster): VaccineFormState {
  return {
    vaccineName: vaccine.vaccineName,
    description: vaccine.description,
    manufacturer: vaccine.manufacturer,
    brandName: vaccine.brandName,
    vaccineGroup: vaccine.vaccineGroup,
    doseNumber: vaccine.doseNumber,
    route: vaccine.route,
    administrationSite: vaccine.administrationSite,
    storageTemperature: vaccine.storageTemperature,
    ndcBarcode: vaccine.ndcBarcode,
    inventoryItemId: vaccine.inventoryItemId,
    inventoryItemCode: vaccine.inventoryItemCode,
    stockTrackingEnabled: vaccine.stockTrackingEnabled,
    scheduleType: vaccine.scheduleType,
    ageGroup: vaccine.ageGroup,
    minAgeDays: vaccine.minAgeDays,
    recommendedAgeDays: vaccine.recommendedAgeDays,
    maxAgeDays: vaccine.maxAgeDays,
    gapDays: vaccine.gapDays ?? vaccine.recommendedGapDays,
    recommendedGapDays: vaccine.recommendedGapDays ?? vaccine.gapDays,
    defaultPrice: vaccine.defaultPrice,
    boosterGapDays: vaccine.boosterGapDays,
    boosterRules: vaccine.boosterRules,
    recurring: vaccine.recurring,
    recurrenceDays: vaccine.recurrenceDays,
    recommendationPolicy: vaccine.recommendationPolicy,
    catchUpPolicy: vaccine.catchUpPolicy,
    catchUpMaxAgeDays: vaccine.catchUpMaxAgeDays,
    applicableAgeGroup: vaccine.applicableAgeGroup,
    clinicalIndications: vaccine.clinicalIndications,
    active: vaccine.active,
  };
}

function downloadCsv(filename: string, csv: string) {
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

export default function VaccineMasterPage() {
  const auth = useAuth();
  const [vaccines, setVaccines] = React.useState<VaccineMaster[]>([]);
  const [medicines, setMedicines] = React.useState<Medicine[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [vaccineForm, setVaccineForm] = React.useState<VaccineFormState>(emptyVaccineForm());
  const [vaccineFieldErrors, setVaccineFieldErrors] = React.useState<Record<string, string>>({});
  const [editingVaccineId, setEditingVaccineId] = React.useState<string | null>(null);
  const [importPreviewOpen, setImportPreviewOpen] = React.useState(false);
  const [importResultOpen, setImportResultOpen] = React.useState(false);
  const [importPreview, setImportPreview] = React.useState<VaccineImportPreview | null>(null);
  const [importPreviewFile, setImportPreviewFile] = React.useState<File | null>(null);
  const [importResult, setImportResult] = React.useState<VaccineCsvImportResponse | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const inventoryItemRequired = vaccineForm.stockTrackingEnabled;
  const catchUpMaxAgeRequired = vaccineForm.catchUpPolicy === "ALLOWED_UNTIL_AGE";

  const loadAll = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const [vaccineRows, medicineRows] = await Promise.all([
      getVaccines(auth.accessToken, auth.tenantId),
      getMedicines(auth.accessToken, auth.tenantId),
    ]);
    setVaccines(vaccineRows);
    setMedicines(medicineRows);
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
          setError(err instanceof Error ? err.message : "Failed to load vaccine master data");
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

  const openVaccineEditor = React.useCallback((vaccine?: VaccineMaster) => {
    if (!vaccine) {
      setEditingVaccineId(null);
      setVaccineForm(emptyVaccineForm());
    } else {
      setEditingVaccineId(vaccine.id);
      setVaccineForm(formForVaccine(vaccine));
    }
    setVaccineFieldErrors({});
    setError(null);
    setSuccess(null);
  }, []);

  const closeImportDialogs = React.useCallback(() => {
    setImportPreviewOpen(false);
    setImportResultOpen(false);
    setImportPreview(null);
    setImportPreviewFile(null);
    setImportResult(null);
  }, []);

  const saveVaccine = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    const normalized = {
      vaccineName: vaccineForm.vaccineName,
      description: vaccineForm.description ?? null,
      manufacturer: vaccineForm.manufacturer ?? null,
      brandName: vaccineForm.brandName ?? null,
      vaccineGroup: vaccineForm.vaccineGroup ?? null,
      doseNumber: vaccineForm.doseNumber ?? null,
      route: vaccineForm.route ?? null,
      administrationSite: vaccineForm.administrationSite ?? null,
      storageTemperature: vaccineForm.storageTemperature ?? null,
      ndcBarcode: vaccineForm.ndcBarcode ?? null,
      inventoryItemId: vaccineForm.inventoryItemId ?? null,
      inventoryItemCode: vaccineForm.inventoryItemCode ?? null,
      stockTrackingEnabled: vaccineForm.stockTrackingEnabled,
      scheduleType: vaccineForm.scheduleType ?? null,
      ageGroup: vaccineForm.ageGroup ?? null,
      minAgeDays: vaccineForm.minAgeDays ?? null,
      recommendedAgeDays: vaccineForm.recommendedAgeDays ?? null,
      maxAgeDays: vaccineForm.maxAgeDays ?? null,
      gapDays: vaccineForm.gapDays ?? null,
      recommendedGapDays: vaccineForm.recommendedGapDays ?? null,
      boosterGapDays: vaccineForm.boosterGapDays ?? null,
      boosterRules: vaccineForm.boosterRules ?? null,
      recurring: vaccineForm.recurring,
      recurrenceDays: vaccineForm.recurrenceDays ?? null,
      recommendationPolicy: vaccineForm.recommendationPolicy ?? null,
      catchUpPolicy: vaccineForm.catchUpPolicy ?? null,
      catchUpMaxAgeDays: vaccineForm.catchUpMaxAgeDays ?? null,
      applicableAgeGroup: vaccineForm.applicableAgeGroup ?? null,
      clinicalIndications: vaccineForm.clinicalIndications ?? null,
      defaultPrice: vaccineForm.defaultPrice ?? null,
      active: vaccineForm.active,
    } satisfies VaccineInput;
    const parsed = vaccinationMasterSchema.safeParse(normalized);
    if (!parsed.success) {
      const errors = mapZodErrors(parsed.error);
      setVaccineFieldErrors(errors);
      setError(firstZodError(parsed.error));
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const payload: VaccineInput = {
        vaccineName: parsed.data.vaccineName,
        description: parsed.data.description ?? null,
        manufacturer: parsed.data.manufacturer ?? null,
        brandName: parsed.data.brandName ?? null,
        vaccineGroup: parsed.data.vaccineGroup ?? null,
        doseNumber: parsed.data.doseNumber ?? null,
        route: parsed.data.route ?? null,
        administrationSite: parsed.data.administrationSite ?? null,
        storageTemperature: parsed.data.storageTemperature ?? null,
        ndcBarcode: parsed.data.ndcBarcode ?? null,
        inventoryItemId: parsed.data.inventoryItemId ?? null,
        inventoryItemCode: parsed.data.inventoryItemCode ?? null,
        stockTrackingEnabled: parsed.data.stockTrackingEnabled,
        scheduleType: parsed.data.scheduleType ?? null,
        ageGroup: parsed.data.ageGroup ?? null,
        minAgeDays: parsed.data.minAgeDays ?? null,
        recommendedAgeDays: parsed.data.recommendedAgeDays ?? null,
        maxAgeDays: parsed.data.maxAgeDays ?? null,
        gapDays: parsed.data.gapDays ?? null,
        recommendedGapDays: parsed.data.recommendedGapDays ?? null,
        boosterGapDays: parsed.data.boosterGapDays ?? null,
        boosterRules: parsed.data.boosterRules ?? null,
        recurring: parsed.data.recurring,
        recurrenceDays: parsed.data.recurrenceDays ?? null,
        recommendationPolicy: parsed.data.recommendationPolicy ?? null,
        catchUpPolicy: parsed.data.catchUpPolicy ?? null,
        catchUpMaxAgeDays: parsed.data.catchUpMaxAgeDays ?? null,
        applicableAgeGroup: parsed.data.applicableAgeGroup ?? null,
        clinicalIndications: parsed.data.clinicalIndications ?? null,
        defaultPrice: parsed.data.defaultPrice ?? null,
        active: parsed.data.active,
      };
      if (editingVaccineId) {
        await updateVaccine(auth.accessToken, auth.tenantId, editingVaccineId, payload);
      } else {
        await createVaccine(auth.accessToken, auth.tenantId, payload);
      }
      setVaccineForm(emptyVaccineForm());
      setEditingVaccineId(null);
      await loadAll();
      setSuccess("Vaccine saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save vaccine");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, editingVaccineId, loadAll, vaccineForm]);

  const deactivate = React.useCallback(async (id: string) => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await deactivateVaccine(auth.accessToken, auth.tenantId, id);
      await loadAll();
      setSuccess("Vaccine updated");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deactivate vaccine");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, loadAll]);

  const downloadTemplate = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    try {
      const csv = await getVaccineImportTemplate(auth.accessToken, auth.tenantId);
      downloadCsv("vaccine-import-template.csv", csv);
    } catch {
      downloadCsv("vaccine-import-template.csv", buildVaccineTemplateCsv());
    }
  }, [auth.accessToken, auth.tenantId]);

  const downloadExport = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    try {
      const csv = await exportVaccinesCsv(auth.accessToken, auth.tenantId);
      downloadCsv("vaccine-master-export.csv", csv);
    } catch {
      downloadCsv("vaccine-master-export.csv", buildVaccineExportCsv(vaccines));
    }
  }, [auth.accessToken, auth.tenantId, vaccines]);

  const openImportFilePicker = React.useCallback(() => fileInputRef.current?.click(), []);

  const handleImportFile = React.useCallback(async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    try {
      const parsed = fileUploadSchema({
        required: true,
        allowedMimeTypes: ["text/csv", "application/csv", "text/plain", "application/vnd.ms-excel"],
        allowedExtensions: ["csv"],
        maxBytes: 5 * 1024 * 1024,
      }).safeParse(file);
      if (!parsed.success) {
        setError(firstZodError(parsed.error));
        return;
      }
      const text = await file.text();
      setImportPreview(parseVaccineImportPreview(text, vaccines.map((vaccine) => vaccine.vaccineName)));
      setImportPreviewFile(file);
      setImportPreviewOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to read vaccine CSV");
    } finally {
      event.target.value = "";
    }
  }, [vaccines]);

  const confirmImport = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || !importPreviewFile) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const result = await importVaccinesCsv(auth.accessToken, auth.tenantId, importPreviewFile);
      setImportResult(result);
      setImportPreviewOpen(false);
      setImportResultOpen(true);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to import vaccine CSV");
    } finally {
      setSaving(false);
    }
  }, [auth.accessToken, auth.tenantId, importPreviewFile, loadAll]);

  const rowCount = vaccines.length;

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Vaccine Master
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Configure vaccine master data for operational vaccination workflows, CSV import/export, inventory mapping, and scheduling rules.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => void loadAll()} disabled={loading || saving}>
          {loading ? "Refreshing..." : "Refresh"}
        </Button>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}

      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent sx={{ py: 1, "&:last-child": { pb: 1 } }}>
          <Stack direction="row" spacing={1} flexWrap="wrap">
            <Button size="small" startIcon={<AddRoundedIcon />} onClick={() => openVaccineEditor()}>
              {editingVaccineId ? "New vaccine" : "Reset"}
            </Button>
            <Button size="small" variant="outlined" onClick={() => void downloadTemplate()}>
              Download CSV Template
            </Button>
            <Button size="small" variant="outlined" onClick={openImportFilePicker}>
              Upload CSV
            </Button>
            <Button size="small" variant="outlined" onClick={() => void downloadExport()}>
              Export CSV
            </Button>
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 5 }}>
          <Card>
            <CardContent sx={{ p: 1.25 }}>
              <Stack spacing={1.25}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  {editingVaccineId ? "Edit vaccine" : "Create vaccine"}
                </Typography>
                <Grid container spacing={1}>
                  <Grid size={12}>
                    <TextField size="small" fullWidth id="vaccine-vaccineName" label={<RequiredLabel text="Vaccine name" required />} value={vaccineForm.vaccineName} onChange={(e) => setVaccineForm((current) => ({ ...current, vaccineName: e.target.value }))} error={Boolean(vaccineFieldErrors.vaccineName)} helperText={vaccineFieldErrors.vaccineName || "Required, max 100 characters."} />
                  </Grid>
                  <Grid size={12}>
                    <TextField size="small" fullWidth id="vaccine-description" label="Description" value={vaccineForm.description ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, description: e.target.value }))} multiline minRows={2} error={Boolean(vaccineFieldErrors.description)} helperText={vaccineFieldErrors.description || "Optional, max 250 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccine-manufacturer" label="Manufacturer" value={vaccineForm.manufacturer ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, manufacturer: e.target.value }))} error={Boolean(vaccineFieldErrors.manufacturer)} helperText={vaccineFieldErrors.manufacturer || "Optional, max 250 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccine-brandName" label="Brand name" value={vaccineForm.brandName ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, brandName: e.target.value }))} error={Boolean(vaccineFieldErrors.brandName)} helperText={vaccineFieldErrors.brandName || "Optional, max 250 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccine-vaccineGroup" label="Vaccine group" value={vaccineForm.vaccineGroup ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, vaccineGroup: e.target.value }))} error={Boolean(vaccineFieldErrors.vaccineGroup)} helperText={vaccineFieldErrors.vaccineGroup || "Optional, max 128 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccine-doseNumber" label="Dose number" value={vaccineForm.doseNumber ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, doseNumber: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.doseNumber)} helperText={vaccineFieldErrors.doseNumber || "Optional, zero or greater."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField select size="small" fullWidth id="vaccine-route" label="Route" value={vaccineForm.route ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, route: e.target.value || null }))} error={Boolean(vaccineFieldErrors.route)} helperText={vaccineFieldErrors.route || "Optional. Select IM, SC, ORAL, NASAL, or ID."}>
                      <MenuItem value="">Not set</MenuItem>
                      {ROUTE_OPTIONS.map((option) => (
                        <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField size="small" fullWidth id="vaccine-administrationSite" label="Administration site" value={vaccineForm.administrationSite ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, administrationSite: e.target.value }))} error={Boolean(vaccineFieldErrors.administrationSite)} helperText={vaccineFieldErrors.administrationSite || "Optional, max 128 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField size="small" fullWidth id="vaccine-storageTemperature" label="Storage temperature" value={vaccineForm.storageTemperature ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, storageTemperature: e.target.value }))} error={Boolean(vaccineFieldErrors.storageTemperature)} helperText={vaccineFieldErrors.storageTemperature || "Optional, max 128 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccine-ndcBarcode" label="NDC barcode" value={vaccineForm.ndcBarcode ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, ndcBarcode: e.target.value }))} error={Boolean(vaccineFieldErrors.ndcBarcode)} helperText={vaccineFieldErrors.ndcBarcode || "Optional, max 128 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField select size="small" fullWidth id="vaccine-inventoryItemId" label={<RequiredLabel text="Inventory item" required={inventoryItemRequired} />} value={vaccineForm.inventoryItemId ?? ""} onChange={(e) => {
                      const selectedMedicine = medicines.find((medicine) => medicine.id === String(e.target.value)) || null;
                      setVaccineForm((current) => ({
                        ...current,
                        inventoryItemId: selectedMedicine?.id || null,
                        inventoryItemCode: selectedMedicine?.barcode || selectedMedicine?.externalCode || current.inventoryItemCode,
                      }));
                    }} error={Boolean(vaccineFieldErrors.inventoryItemId)} helperText={vaccineFieldErrors.inventoryItemId || (inventoryItemRequired ? "Required when stock tracking is enabled." : "Optional mapping to Inventory item for stock deduction.")}>
                      <MenuItem value="">No inventory mapping</MenuItem>
                      {medicines.map((medicine) => (
                        <MenuItem key={medicine.id} value={medicine.id}>
                          {medicine.medicineName} {medicine.externalCode ? `• ${medicine.externalCode}` : ""}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccine-inventoryItemCode" label="Inventory item code" value={vaccineForm.inventoryItemCode ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, inventoryItemCode: e.target.value || null }))} helperText="Optional code used for inventory mapping." />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <FormControlLabel control={<Switch checked={Boolean(vaccineForm.stockTrackingEnabled)} onChange={(e) => setVaccineForm((current) => ({ ...current, stockTrackingEnabled: e.target.checked }))} />} label="Track stock in inventory" />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField select size="small" fullWidth id="vaccine-scheduleType" label="Schedule type" value={vaccineForm.scheduleType ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, scheduleType: e.target.value || null }))} error={Boolean(vaccineFieldErrors.scheduleType)} helperText={vaccineFieldErrors.scheduleType || "Optional. Select UIP, IAP, CLINIC_CUSTOM, TRAVEL, or ADULT."}>
                      <MenuItem value="">Not set</MenuItem>
                      {SCHEDULE_TYPE_OPTIONS.map((option) => (
                        <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField size="small" fullWidth id="vaccine-ageGroup" label="Age group" value={vaccineForm.ageGroup ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, ageGroup: e.target.value }))} error={Boolean(vaccineFieldErrors.ageGroup)} helperText={vaccineFieldErrors.ageGroup || "Optional, max 60 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField size="small" fullWidth id="vaccine-minAgeDays" label="Min age days" value={vaccineForm.minAgeDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, minAgeDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.minAgeDays)} helperText={vaccineFieldErrors.minAgeDays || "Optional, zero or greater."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField size="small" fullWidth id="vaccine-recommendedAgeDays" label="Recommended age days" value={vaccineForm.recommendedAgeDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, recommendedAgeDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.recommendedAgeDays)} helperText={vaccineFieldErrors.recommendedAgeDays || "Optional, zero or greater."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField size="small" fullWidth id="vaccine-maxAgeDays" label="Max age days" value={vaccineForm.maxAgeDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, maxAgeDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.maxAgeDays)} helperText={vaccineFieldErrors.maxAgeDays || "Optional, zero or greater."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField size="small" fullWidth id="vaccine-gapDays" label="Gap days" value={vaccineForm.gapDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, gapDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.gapDays)} helperText={vaccineFieldErrors.gapDays || "Optional, zero or greater."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField size="small" fullWidth id="vaccine-boosterGapDays" label="Booster gap days" value={vaccineForm.boosterGapDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, boosterGapDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.boosterGapDays)} helperText={vaccineFieldErrors.boosterGapDays || "Optional, zero or greater."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 8 }}>
                    <TextField size="small" fullWidth id="vaccine-boosterRules" label="Booster rules" value={vaccineForm.boosterRules ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, boosterRules: e.target.value }))} error={Boolean(vaccineFieldErrors.boosterRules)} helperText={vaccineFieldErrors.boosterRules || "Optional, max 500 characters."} multiline minRows={2} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField size="small" fullWidth id="vaccine-recurrenceDays" label="Recurrence days" value={vaccineForm.recurrenceDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, recurrenceDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.recurrenceDays)} helperText={vaccineFieldErrors.recurrenceDays || "Optional, zero or greater."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField select size="small" fullWidth id="vaccine-recommendationPolicy" label="Recommendation policy" value={vaccineForm.recommendationPolicy ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, recommendationPolicy: e.target.value || null }))} error={Boolean(vaccineFieldErrors.recommendationPolicy)} helperText={vaccineFieldErrors.recommendationPolicy || "Optional. Select the recommendation policy."}>
                      <MenuItem value="">Not set</MenuItem>
                      {RECOMMENDATION_POLICY_OPTIONS.map((option) => (
                        <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <TextField select size="small" fullWidth id="vaccine-catchUpPolicy" label="Catch-up policy" value={vaccineForm.catchUpPolicy ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, catchUpPolicy: e.target.value || null }))} error={Boolean(vaccineFieldErrors.catchUpPolicy)} helperText={vaccineFieldErrors.catchUpPolicy || "Optional. Select NONE, ALLOWED_UNTIL_AGE, LIFETIME, or CLINICIAN_DECISION."}>
                      <MenuItem value="">Not set</MenuItem>
                      {CATCH_UP_POLICY_OPTIONS.map((option) => (
                        <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField size="small" fullWidth id="vaccine-catchUpMaxAgeDays" label={<RequiredLabel text="Catch-up max age days" required={catchUpMaxAgeRequired} />} value={vaccineForm.catchUpMaxAgeDays ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, catchUpMaxAgeDays: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.catchUpMaxAgeDays)} helperText={vaccineFieldErrors.catchUpMaxAgeDays || (catchUpMaxAgeRequired ? "Required when catch-up policy is ALLOWED_UNTIL_AGE." : "Optional, zero or greater.")} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField select size="small" fullWidth id="vaccine-applicableAgeGroup" label="Applicable age group" value={vaccineForm.applicableAgeGroup ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, applicableAgeGroup: e.target.value || null }))} error={Boolean(vaccineFieldErrors.applicableAgeGroup)} helperText={vaccineFieldErrors.applicableAgeGroup || "Optional. Select a supported age group."}>
                      <MenuItem value="">Not set</MenuItem>
                      {APPLICABLE_AGE_GROUP_OPTIONS.map((option) => (
                        <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField size="small" fullWidth id="vaccine-clinicalIndications" label="Clinical indications" value={vaccineForm.clinicalIndications ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, clinicalIndications: e.target.value || null }))} error={Boolean(vaccineFieldErrors.clinicalIndications)} helperText={vaccineFieldErrors.clinicalIndications || "Optional comma-separated indications, max 1000 characters."} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControlLabel control={<Switch checked={vaccineForm.recurring} onChange={(e) => setVaccineForm((current) => ({ ...current, recurring: e.target.checked }))} />} label="Recurring" />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField size="small" fullWidth id="vaccine-defaultPrice" label="Default price" value={vaccineForm.defaultPrice ?? ""} onChange={(e) => setVaccineForm((current) => ({ ...current, defaultPrice: e.target.value ? Number(e.target.value) : null }))} error={Boolean(vaccineFieldErrors.defaultPrice)} helperText={vaccineFieldErrors.defaultPrice || "Optional, zero or greater, up to 2 decimals."} />
                  </Grid>
                  <Grid size={12}>
                    <FormControlLabel control={<Switch checked={vaccineForm.active} onChange={(e) => setVaccineForm((current) => ({ ...current, active: e.target.checked }))} />} label={vaccineForm.active ? "Active" : "Inactive"} />
                  </Grid>
                </Grid>
                <Button variant="contained" size="small" onClick={() => void saveVaccine()} disabled={saving}>
                  {editingVaccineId ? "Update Vaccine" : "Save Vaccine"}
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 7 }}>
          <Card>
            <CardContent sx={{ p: 1.25 }}>
              <Stack spacing={1.25}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  Vaccine list
                </Typography>
                {loading ? (
                  <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                    <CircularProgress />
                  </Box>
                ) : rowCount === 0 ? (
                  <CompactEmptyState title="No vaccines were found." subtitle="Create a vaccine in the master list to start recording doses." />
                ) : (
                  <CompactTableFrame maxHeight={660}>
                    <Table size="small" stickyHeader sx={{ minWidth: 760 }}>
                      <TableHead>
                        <TableRow>
                          <TableCell sx={{ py: 0.7 }}>Name</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Age group</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Gap days</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Default price</TableCell>
                          <TableCell sx={{ py: 0.7 }}>Status</TableCell>
                          <TableCell sx={{ py: 0.7 }} align="right">Actions</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {vaccines.map((vaccine) => (
                          <TableRow key={vaccine.id}>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>{vaccine.vaccineName}</Typography>
                                <Typography variant="caption" color="text.secondary">{vaccine.description || "No description"}</Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{vaccine.ageGroup || "-"}</TableCell>
                            <TableCell>{vaccine.gapDays ?? vaccine.recommendedGapDays ?? "-"}</TableCell>
                            <TableCell>{vaccine.defaultPrice?.toFixed(2) || "-"}</TableCell>
                            <TableCell>
                              <Chip size="small" label={vaccine.active ? "Active" : "Inactive"} color={vaccine.active ? "success" : "default"} />
                            </TableCell>
                            <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                              <Button size="small" startIcon={<EditRoundedIcon fontSize="small" />} sx={{ whiteSpace: "nowrap" }} onClick={() => openVaccineEditor(vaccine)}>
                                Edit
                              </Button>
                              <Button size="small" sx={{ whiteSpace: "nowrap" }} onClick={() => void deactivate(vaccine.id)} disabled={!vaccine.active || saving}>
                                Deactivate
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </CompactTableFrame>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Dialog open={importPreviewOpen} onClose={() => setImportPreviewOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Preview vaccine CSV import</DialogTitle>
        <DialogContent dividers>
          {importPreview ? (
            <Stack spacing={2}>
              {importPreview.headerWarnings.length > 0 ? (
                <Alert severity="warning">{importPreview.headerWarnings.join(" ")}</Alert>
              ) : null}
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip size="small" label={`${importPreview.summary.totalRows} rows`} />
                <Chip size="small" color="success" variant="outlined" label={`${importPreview.summary.validRows} valid`} />
                <Chip size="small" color={importPreview.summary.invalidRows ? "error" : "default"} variant="outlined" label={`${importPreview.summary.invalidRows} invalid`} />
              </Stack>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Row</TableCell>
                    <TableCell>Vaccine</TableCell>
                    <TableCell>Gap days</TableCell>
                    <TableCell>Default price</TableCell>
                    <TableCell>Active</TableCell>
                    <TableCell>Validation</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {importPreview.rows.map((row) => (
                    <TableRow key={row.rowNumber} hover={!row.errors.length}>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.vaccineName || "-"}</TableCell>
                      <TableCell>{row.gapDays || "-"}</TableCell>
                      <TableCell>{row.defaultPrice || "-"}</TableCell>
                      <TableCell>{row.active || "-"}</TableCell>
                      <TableCell>
                        {row.errors.length > 0 ? (
                          <Stack spacing={0.35}>
                            {row.errors.map((message) => (
                              <Typography key={message} variant="caption" color="error">
                                {message}
                              </Typography>
                            ))}
                          </Stack>
                        ) : (
                          <Chip size="small" color="success" label="Valid" />
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeImportDialogs}>Cancel</Button>
          <Button variant="contained" onClick={() => void confirmImport()} disabled={saving || !importPreview?.summary.validRows}>
            {saving ? "Importing..." : "Import valid rows"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={importResultOpen} onClose={() => setImportResultOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Vaccine CSV import result</DialogTitle>
        <DialogContent dividers>
          {importResult ? (
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip size="small" label={`${importResult.totalRows} rows`} />
                <Chip size="small" color="success" variant="outlined" label={`${importResult.createdCount} created`} />
                <Chip size="small" color={importResult.failedCount ? "error" : "default"} variant="outlined" label={`${importResult.failedCount} failed`} />
              </Stack>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Row</TableCell>
                    <TableCell>Vaccine</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Message</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {importResult.rows.map((row) => (
                    <TableRow key={`${row.rowNumber}-${row.vaccineName}`}>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.vaccineName || "-"}</TableCell>
                      <TableCell>{row.status}</TableCell>
                      <TableCell>{row.message}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeImportDialogs}>Close</Button>
        </DialogActions>
      </Dialog>

      <input ref={fileInputRef} hidden type="file" accept=".csv,text/csv" onChange={(e) => void handleImportFile(e)} />
    </Stack>
  );
}
