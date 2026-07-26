import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  FormControlLabel,
  Grid,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";

import {
  firstZodError,
  clinicProfileSchema,
  getCitySuggestions,
  getCountrySuggestions,
  getIndiaStateSuggestions,
  normalizeIndianMobileInput,
  mapZodErrors,
} from "@deepthoughtnet/form-validation-kit";

import { useAuth } from "../../auth/useAuth";
import AutocompleteTextInput from "../../components/forms/AutocompleteTextInput";
import RequiredLabel from "../../components/forms/RequiredLabel";
import TenantOnboardingWizardDialog from "../../components/onboarding/TenantOnboardingWizardDialog";
import { useAuthenticatedImage } from "../../hooks/useAuthenticatedImage";
import {
  getClinicProfile,
  getPrescriptionTemplate,
  getPrescriptionTemplateHistory,
  getTenantOnboardingStatus,
  previewPrescriptionTemplate,
  removePrescriptionTemplateLogo,
  uploadPrescriptionTemplateLogo,
  type TenantOnboardingStatus,
  type ClinicProfileInput,
  type PrescriptionTemplateConfig,
  type PrescriptionTemplateInput,
  updateClinicProfile,
  updatePrescriptionTemplate,
} from "../../api/clinicApi";

type ClinicProfileFormState = {
  clinicName: string;
  displayName: string;
  phone: string;
  email: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  country: string;
  postalCode: string;
  registrationNumber: string;
  gstNumber: string;
  logoDocumentId: string;
  active: boolean;
  publicListingEnabled: boolean;
  slug: string;
};

type TemplateFormState = {
  clinicLogoDocumentId: string;
  logoUrl: string | null;
  headerText: string;
  footerText: string;
  primaryColor: string;
  accentColor: string;
  disclaimer: string;
  doctorSignatureText: string;
  showQrCode: boolean;
  watermarkText: string;
};

function emptyTemplate(): TemplateFormState {
  return {
    clinicLogoDocumentId: "",
    logoUrl: null,
    headerText: "",
    footerText: "",
    primaryColor: "#0f766e",
    accentColor: "#14b8a6",
    disclaimer: "",
    doctorSignatureText: "",
    showQrCode: true,
    watermarkText: "",
  };
}

function templateToForm(template: PrescriptionTemplateConfig): TemplateFormState {
  return {
    clinicLogoDocumentId: template.clinicLogoDocumentId || "",
    logoUrl: template.logoUrl || null,
    headerText: template.headerText || "",
    footerText: template.footerText || "",
    primaryColor: template.primaryColor || "#0f766e",
    accentColor: template.accentColor || "#14b8a6",
    disclaimer: template.disclaimer || "",
    doctorSignatureText: template.doctorSignatureText || "",
    showQrCode: template.showQrCode,
    watermarkText: template.watermarkText || "",
  };
}

function templateInput(form: TemplateFormState): PrescriptionTemplateInput {
  return {
    clinicLogoDocumentId: form.clinicLogoDocumentId.trim() || null,
    headerText: form.headerText.trim() || null,
    footerText: form.footerText.trim() || null,
    primaryColor: normalizeHexColor(form.primaryColor, "#0F766E"),
    accentColor: normalizeHexColor(form.accentColor, "#14B8A6"),
    disclaimer: form.disclaimer.trim() || null,
    doctorSignatureText: form.doctorSignatureText.trim() || null,
    showQrCode: form.showQrCode,
    watermarkText: form.watermarkText.trim() || null,
  };
}

function normalizeHexColor(value: string, fallback: string): string {
  const normalized = value.trim().toUpperCase();
  if (!normalized) {
    return fallback;
  }
  if (!/^#[0-9A-F]{6}$/.test(normalized)) {
    return fallback;
  }
  return normalized;
}

function isValidHexColor(value: string): boolean {
  return /^#[0-9A-Fa-f]{6}$/.test(value.trim());
}

function isSupportedLogoFile(file: File): boolean {
  if (!file || file.size <= 0 || file.size > 2 * 1024 * 1024) {
    return false;
  }
  const contentType = (file.type || "").toLowerCase();
  const fileName = file.name.toLowerCase();
  return (
    contentType === "image/png" ||
    contentType === "image/jpeg" ||
    contentType === "image/webp" ||
    fileName.endsWith(".png") ||
    fileName.endsWith(".jpg") ||
    fileName.endsWith(".jpeg") ||
    fileName.endsWith(".webp")
  );
}

function emptyForm(): ClinicProfileFormState {
  return {
    clinicName: "",
    displayName: "",
    phone: "",
    email: "",
    addressLine1: "",
    addressLine2: "",
    city: "",
    state: "",
    country: "",
    postalCode: "",
    registrationNumber: "",
    gstNumber: "",
    logoDocumentId: "",
    active: true,
    publicListingEnabled: false,
    slug: "",
  };
}

function toFormState(profile: ClinicProfileInput): ClinicProfileFormState {
  return {
    clinicName: profile.clinicName,
    displayName: profile.displayName,
    phone: profile.phone || "",
    email: profile.email || "",
    addressLine1: profile.addressLine1,
    addressLine2: profile.addressLine2 || "",
    city: profile.city,
    state: profile.state,
    country: profile.country,
    postalCode: profile.postalCode,
    registrationNumber: profile.registrationNumber || "",
    gstNumber: profile.gstNumber || "",
    logoDocumentId: profile.logoDocumentId || "",
    active: profile.active,
    publicListingEnabled: profile.publicListingEnabled,
    slug: profile.slug || "",
  };
}

function toInput(form: ClinicProfileFormState): ClinicProfileInput {
  return {
    clinicName: form.clinicName.trim(),
    displayName: form.displayName.trim(),
    phone: form.phone.trim() ? (normalizeIndianMobileInput(form.phone) as string) : null,
    email: form.email.trim(),
    addressLine1: form.addressLine1.trim(),
    addressLine2: form.addressLine2.trim() || null,
    city: form.city.trim(),
    state: form.state.trim(),
    country: form.country.trim(),
    postalCode: form.postalCode.trim(),
    registrationNumber: form.registrationNumber.trim() || null,
    gstNumber: form.gstNumber.trim() || null,
    logoDocumentId: form.logoDocumentId.trim() || null,
    active: form.active,
    publicListingEnabled: form.publicListingEnabled,
    slug: form.slug.trim() || null,
  };
}

export default function ClinicProfilePage() {
  const auth = useAuth();
  const canEdit = auth.hasPermission("clinic.update");
  const [form, setForm] = React.useState<ClinicProfileFormState>(emptyForm);
  const [templateForm, setTemplateForm] = React.useState<TemplateFormState>(emptyTemplate);
  const [savedTemplateForm, setSavedTemplateForm] = React.useState<TemplateFormState>(emptyTemplate);
  const [templateHistory, setTemplateHistory] = React.useState<PrescriptionTemplateConfig[]>([]);
  const [onboardingStatus, setOnboardingStatus] = React.useState<TenantOnboardingStatus | null>(null);
  const [wizardOpen, setWizardOpen] = React.useState(false);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [logoBusy, setLogoBusy] = React.useState(false);
  const [logoError, setLogoError] = React.useState<string | null>(null);
  const logoInputRef = React.useRef<HTMLInputElement | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const { objectUrl: logoPreviewUrl, loading: logoPreviewLoading, error: logoPreviewError } = useAuthenticatedImage(templateForm.logoUrl);

  React.useEffect(() => {
    let cancelled = false;

    async function load() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);
      try {
        const [profile, template, history, onboarding] = await Promise.all([
          getClinicProfile(auth.accessToken, auth.tenantId).catch(() => null),
          getPrescriptionTemplate(auth.accessToken, auth.tenantId),
          getPrescriptionTemplateHistory(auth.accessToken, auth.tenantId),
          getTenantOnboardingStatus(auth.accessToken, auth.tenantId).catch(() => null),
        ]);
        if (!cancelled) {
          setForm(profile ? toFormState(profile) : emptyForm());
          setTemplateForm(templateToForm(template));
          setSavedTemplateForm(templateToForm(template));
          setTemplateHistory(history);
          setOnboardingStatus(onboarding);
        }
      } catch (err) {
        const message = err instanceof Error ? err.message : "Failed to load clinic profile";
        if (message.includes("HTTP 404")) {
          if (!cancelled) {
            setForm(emptyForm());
          }
        } else if (!cancelled) {
          setError(message);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    if (!loading && onboardingStatus && !onboardingStatus.completed && canEdit) {
      setWizardOpen(true);
    }
  }, [canEdit, loading, onboardingStatus]);

  const updateTextField =
    (field: Exclude<keyof ClinicProfileFormState, "active">) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const value = event.target.value;
      setForm((current) => ({ ...current, [field]: value } as ClinicProfileFormState));
    };

  const updateActive = (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm((current) => ({ ...current, active: event.target.checked }));
  };

  const updatePublicListingEnabled = (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm((current) => ({ ...current, publicListingEnabled: event.target.checked }));
  };

  const countrySuggestions = getCountrySuggestions(form.country);
  const stateSuggestions = form.country.trim().toLowerCase() === "india" ? getIndiaStateSuggestions(form.state) : [];
  const citySuggestions = getCitySuggestions(form.city, form.country);

  const updateTemplateField =
    (field: Exclude<keyof TemplateFormState, "showQrCode">) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const value = event.target.value;
      setTemplateForm((current) => ({ ...current, [field]: value }));
      setError(null);
      setSuccess(null);
    };

  const updateTemplateColor =
    (field: "primaryColor" | "accentColor") =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const value = event.target.value;
      setTemplateForm((current) => ({ ...current, [field]: value }));
      setError(null);
      setSuccess(null);
    };

  const triggerLogoPicker = React.useCallback(() => {
    if (!canEdit || saving || logoBusy) {
      return;
    }
    if (logoInputRef.current) {
      logoInputRef.current.value = "";
      logoInputRef.current.click();
    }
  }, [canEdit, logoBusy, saving]);

  const handleLogoPanelKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      triggerLogoPicker();
    }
  };

  const templateDirty = JSON.stringify(templateInput(templateForm)) !== JSON.stringify(templateInput(savedTemplateForm));

  const handleLogoFileUpload = async (file: File | null) => {
    if (!auth.accessToken || !auth.tenantId || !file) {
      return;
    }
    if (!isSupportedLogoFile(file)) {
      setLogoError("Logo must be PNG, JPG, JPEG, or WEBP and 2 MB or smaller.");
      setError("Logo must be PNG, JPG, JPEG, or WEBP and 2 MB or smaller.");
      return;
    }
    setLogoBusy(true);
    setLogoError(null);
    setError(null);
    setSuccess(null);
    try {
      const saved = await uploadPrescriptionTemplateLogo(auth.accessToken, auth.tenantId, file);
      const nextTemplate = templateToForm(saved);
      setTemplateForm(nextTemplate);
      setSavedTemplateForm(nextTemplate);
      setTemplateHistory(await getPrescriptionTemplateHistory(auth.accessToken, auth.tenantId));
      setSuccess("Logo uploaded");
    } catch (err) {
      setLogoError(err instanceof Error ? err.message : "Failed to upload logo");
      setError(err instanceof Error ? err.message : "Failed to upload logo");
    } finally {
      setLogoBusy(false);
      if (logoInputRef.current) {
        logoInputRef.current.value = "";
      }
    }
  };

  const handleRemoveLogo = async () => {
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }
    setLogoBusy(true);
    setLogoError(null);
    setError(null);
    setSuccess(null);
    try {
      const saved = await removePrescriptionTemplateLogo(auth.accessToken, auth.tenantId);
      const nextTemplate = templateToForm(saved);
      setTemplateForm(nextTemplate);
      setSavedTemplateForm(nextTemplate);
      setTemplateHistory(await getPrescriptionTemplateHistory(auth.accessToken, auth.tenantId));
      setSuccess("Logo removed");
    } catch (err) {
      setLogoError(err instanceof Error ? err.message : "Failed to remove logo");
      setError(err instanceof Error ? err.message : "Failed to remove logo");
    } finally {
      setLogoBusy(false);
      if (logoInputRef.current) {
        logoInputRef.current.value = "";
      }
    }
  };

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!auth.accessToken || !auth.tenantId) {
      return;
    }

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const payload = toInput(form);
      const parsed = clinicProfileSchema.safeParse(payload);
      if (!parsed.success) {
        const nextFieldErrors = mapZodErrors(parsed.error);
        setFieldErrors(nextFieldErrors);
        setError(firstZodError(parsed.error) || "Please correct the highlighted clinic profile fields.");
        const firstInvalidField = Object.keys(nextFieldErrors)[0];
        if (firstInvalidField) {
          window.setTimeout(() => document.getElementById(`clinic-profile-${firstInvalidField}`)?.focus(), 0);
        }
        setSaving(false);
        return;
      }
      setFieldErrors({});
      const saved = await updateClinicProfile(auth.accessToken, auth.tenantId, payload);
      setForm(toFormState(saved));
      setSuccess("Clinic profile saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save clinic profile");
    } finally {
      setSaving(false);
    }
  };

  const saveTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    if (!isValidHexColor(templateForm.primaryColor) || !isValidHexColor(templateForm.accentColor)) {
      setError("Enter a valid color such as #0F766E");
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const saved = await updatePrescriptionTemplate(auth.accessToken, auth.tenantId, templateInput(templateForm));
      const nextTemplate = templateToForm(saved);
      setTemplateForm(nextTemplate);
      setSavedTemplateForm(nextTemplate);
      setTemplateHistory(await getPrescriptionTemplateHistory(auth.accessToken, auth.tenantId));
      setSuccess("Prescription template saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save prescription template");
    } finally {
      setSaving(false);
    }
  };

  const previewTemplate = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setError(null);
    try {
      const { blob } = await previewPrescriptionTemplate(auth.accessToken, auth.tenantId, templateInput(templateForm));
      window.open(URL.createObjectURL(blob), "_blank", "noopener,noreferrer");
    } catch (err) {
      setError("Prescription preview could not be generated. Your branding changes have not been lost.");
    }
  };

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  return (
    <Stack spacing={3} component="form" onSubmit={onSubmit} noValidate>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
          Clinic Profile
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Tenant-scoped clinic settings. Updates are audited and only clinic admins can edit them.
        </Typography>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}
      {!canEdit ? <Alert severity="info">You have read-only access to this profile.</Alert> : null}
      {onboardingStatus && !onboardingStatus.completed ? (
        <Alert severity="info" action={<Button color="inherit" size="small" onClick={() => setWizardOpen(true)}>Resume setup</Button>}>
          Clinic onboarding is incomplete. You can continue the setup wizard from here.
        </Alert>
      ) : null}

      <Card>
        <CardContent>
          {loading ? (
            <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
              <CircularProgress />
            </Box>
          ) : (
            <Stack spacing={3}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    id="clinic-profile-clinicName"
                    fullWidth
                    label={<RequiredLabel text="Clinic name" required />}
                    value={form.clinicName}
                    onChange={updateTextField("clinicName")}
                    disabled={!canEdit || saving}
                    required
                    error={Boolean(fieldErrors.clinicName)}
                    helperText={fieldErrors.clinicName || "Required."}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField fullWidth label="Display name" value={form.displayName} onChange={updateTextField("displayName")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField fullWidth label="Phone" value={form.phone} onChange={updateTextField("phone")} disabled={!canEdit || saving} inputProps={{ inputMode: "tel" }} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField fullWidth label="Email" value={form.email} onChange={updateTextField("email")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={12}>
                  <TextField fullWidth label="Address line 1" value={form.addressLine1} onChange={updateTextField("addressLine1")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={12}>
                  <TextField fullWidth label="Address line 2" value={form.addressLine2} onChange={updateTextField("addressLine2")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <AutocompleteTextInput
                    label="City"
                    value={form.city}
                    onChange={(value) => setForm((current) => ({ ...current, city: value }))}
                    suggestions={citySuggestions}
                    disabled={!canEdit || saving}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <AutocompleteTextInput
                    label="State"
                    value={form.state}
                    onChange={(value) => setForm((current) => ({ ...current, state: value }))}
                    suggestions={stateSuggestions}
                    disabled={!canEdit || saving}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <AutocompleteTextInput
                    label="Country"
                    value={form.country}
                    onChange={(value) => setForm((current) => ({ ...current, country: value }))}
                    suggestions={countrySuggestions}
                    disabled={!canEdit || saving}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField fullWidth label="Postal code" value={form.postalCode} onChange={updateTextField("postalCode")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField fullWidth label="Registration number" value={form.registrationNumber} onChange={updateTextField("registrationNumber")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField fullWidth label="GST number" value={form.gstNumber} onChange={updateTextField("gstNumber")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <FormControlLabel
                    control={
                      <Switch
                        checked={form.active}
                        onChange={updateActive}
                        disabled={!canEdit || saving}
                      />
                    }
                    label={form.active ? "Active" : "Inactive"}
                    sx={{ mt: 1 }}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <FormControlLabel
                    control={
                      <Switch
                        checked={form.publicListingEnabled}
                        onChange={updatePublicListingEnabled}
                        disabled={!canEdit || saving}
                      />
                    }
                    label={form.publicListingEnabled ? "Public listing enabled" : "Public listing disabled"}
                    sx={{ mt: 1 }}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField
                    fullWidth
                    label="Public slug"
                    value={form.slug}
                    onChange={updateTextField("slug")}
                    disabled={!canEdit || saving}
                    helperText="Optional. Leave blank to auto-generate from clinic display name."
                  />
                </Grid>
                <Grid size={12}>
                  <Alert severity="info">
                    Public Profile settings control whether this clinic appears in public discovery. Only public-safe details are exposed.
                  </Alert>
                </Grid>
              </Grid>

              {canEdit ? (
                <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
                  <Button type="submit" disabled={saving}>
                    {saving ? "Saving..." : "Save Profile"}
                  </Button>
                </Box>
              ) : null}
            </Stack>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={3}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>Prescription Template & Branding</Typography>
              <Typography variant="body2" color="text.secondary">
                Tenant-managed branding for prescription PDFs and delivery. Logo uploads are stored as tenant media references and template saves create a new version.
              </Typography>
            </Box>

            {templateDirty ? <Alert severity="warning">Unsaved changes</Alert> : null}
            {logoError ? <Alert severity="error">{logoError}</Alert> : null}
            {logoPreviewError ? <Alert severity="warning">Logo preview could not be loaded.</Alert> : null}
            {logoBusy ? <Alert severity="info">Uploading logo...</Alert> : null}
            {!templateForm.clinicLogoDocumentId ? <Alert severity="info">No logo is configured. The prescription will render normally without a logo.</Alert> : null}

            <Grid container spacing={3}>
              <Grid size={{ xs: 12, md: 5 }}>
                <Stack spacing={1.5}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Clinic Logo</Typography>
                  <Box
                    role={canEdit && !logoBusy ? "button" : undefined}
                    tabIndex={canEdit && !logoBusy ? 0 : -1}
                    aria-label={templateForm.clinicLogoDocumentId ? "Replace clinic logo" : "Upload clinic logo"}
                    onClick={triggerLogoPicker}
                    onKeyDown={handleLogoPanelKeyDown}
                    sx={{
                      border: "1px solid",
                      borderColor: "divider",
                      borderRadius: 2,
                      minHeight: 180,
                      p: 2,
                      display: "grid",
                      placeItems: "center",
                      backgroundColor: "background.paper",
                      cursor: canEdit && !logoBusy ? "pointer" : "default",
                      outline: "none",
                      "&:focus-visible": {
                        boxShadow: (theme) => `0 0 0 2px ${theme.palette.primary.main}`,
                      },
                    }}
                  >
                    {logoPreviewLoading || logoBusy ? (
                      <CircularProgress size={28} />
                    ) : logoPreviewUrl ? (
                      <Box
                        component="img"
                        src={logoPreviewUrl}
                        alt="Clinic logo preview"
                        sx={{ maxWidth: "100%", maxHeight: 120, objectFit: "contain" }}
                      />
                    ) : (
                      <Stack spacing={1} alignItems="center">
                        <Typography variant="body2" color="text.secondary" align="center">
                          Upload a PNG, JPG, JPEG, or WEBP logo up to 2 MB.
                        </Typography>
                        <Typography variant="caption" color="text.secondary" align="center">
                          Recommended: transparent PNG around 400 × 120 px.
                        </Typography>
                      </Stack>
                    )}
                  </Box>
                  <input
                    ref={logoInputRef}
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    hidden
                    aria-hidden="true"
                    onChange={(event) => {
                      const file = event.target.files?.[0] || null;
                      event.target.value = "";
                      void handleLogoFileUpload(file);
                    }}
                  />
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button type="button" variant="outlined" disabled={!canEdit || saving || logoBusy} onClick={triggerLogoPicker}>
                      {templateForm.clinicLogoDocumentId ? "Replace Logo" : "Upload Logo"}
                    </Button>
                    <Button type="button" variant="text" disabled={!canEdit || saving || logoBusy || !templateForm.clinicLogoDocumentId} onClick={() => void handleRemoveLogo()}>
                      Remove Logo
                    </Button>
                  </Stack>
                </Stack>
              </Grid>

              <Grid size={{ xs: 12, md: 7 }}>
                <Stack spacing={2}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Colors</Typography>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Stack spacing={1}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>Primary Color</Typography>
                        <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
                          <TextField
                            type="color"
                            value={normalizeHexColor(templateForm.primaryColor, "#0F766E")}
                            onChange={updateTemplateColor("primaryColor")}
                            disabled={!canEdit || saving}
                            inputProps={{ "aria-label": "Primary color picker" }}
                            sx={{ width: 84 }}
                          />
                          <TextField
                            fullWidth
                            label="Hex value"
                            value={templateForm.primaryColor}
                            onChange={updateTemplateColor("primaryColor")}
                            disabled={!canEdit || saving}
                            error={templateForm.primaryColor.trim() !== "" && !isValidHexColor(templateForm.primaryColor)}
                            helperText={templateForm.primaryColor.trim() && !isValidHexColor(templateForm.primaryColor) ? "Enter a valid color such as #0F766E" : "Two-way synchronized with the picker."}
                          />
                        </Box>
                      </Stack>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Stack spacing={1}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>Accent Color</Typography>
                        <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
                          <TextField
                            type="color"
                            value={normalizeHexColor(templateForm.accentColor, "#14B8A6")}
                            onChange={updateTemplateColor("accentColor")}
                            disabled={!canEdit || saving}
                            inputProps={{ "aria-label": "Accent color picker" }}
                            sx={{ width: 84 }}
                          />
                          <TextField
                            fullWidth
                            label="Hex value"
                            value={templateForm.accentColor}
                            onChange={updateTemplateColor("accentColor")}
                            disabled={!canEdit || saving}
                            error={templateForm.accentColor.trim() !== "" && !isValidHexColor(templateForm.accentColor)}
                            helperText={templateForm.accentColor.trim() && !isValidHexColor(templateForm.accentColor) ? "Enter a valid color such as #14B8A6" : "Two-way synchronized with the picker."}
                          />
                        </Box>
                      </Stack>
                    </Grid>
                  </Grid>

                  <Grid container spacing={2}>
                    <Grid size={12}>
                      <TextField fullWidth multiline minRows={2} label="Clinic header" value={templateForm.headerText} onChange={updateTemplateField("headerText")} disabled={!canEdit || saving} />
                    </Grid>
                    <Grid size={12}>
                      <TextField fullWidth multiline minRows={2} label="Clinic footer" value={templateForm.footerText} onChange={updateTemplateField("footerText")} disabled={!canEdit || saving} helperText="Shown beneath the doctor signature and clinic registration number." />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth multiline minRows={2} label="Disclaimer" value={templateForm.disclaimer} onChange={updateTemplateField("disclaimer")} disabled={!canEdit || saving} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth multiline minRows={2} label="Doctor signature text" value={templateForm.doctorSignatureText} onChange={updateTemplateField("doctorSignatureText")} disabled={!canEdit || saving} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <TextField fullWidth label="Watermark text" value={templateForm.watermarkText} onChange={updateTemplateField("watermarkText")} disabled={!canEdit || saving} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <FormControlLabel
                        control={<Switch checked={templateForm.showQrCode} onChange={(event) => setTemplateForm((current) => ({ ...current, showQrCode: event.target.checked }))} disabled={!canEdit || saving} />}
                        label="Show Prescription Verification QR"
                      />
                      <Typography variant="caption" color="text.secondary" display="block">
                        Display verification QR code in prescription footer.
                      </Typography>
                    </Grid>
                  </Grid>
                </Stack>
              </Grid>
            </Grid>

            <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, overflow: "hidden", background: "#fff" }}>
              <Box sx={{ px: 2, py: 1.5, background: normalizeHexColor(templateForm.primaryColor, "#0F766E"), color: "white" }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
                  Live Branding Preview
                </Typography>
                <Typography variant="body2" sx={{ opacity: 0.92 }}>
                  Updates immediately as you edit the clinic branding fields.
                </Typography>
              </Box>
              <Box sx={{ p: 2, display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", md: "1.15fr 0.85fr" } }}>
                <Stack spacing={1.5}>
                  <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
                    {logoPreviewLoading || logoBusy ? (
                      <CircularProgress size={24} />
                    ) : logoPreviewUrl ? (
                      <Box
                        component="img"
                        src={logoPreviewUrl}
                        alt="Clinic logo preview"
                        sx={{ width: 112, maxHeight: 64, objectFit: "contain" }}
                      />
                    ) : (
                      <Box sx={{ width: 112, height: 64, border: "1px dashed", borderColor: "divider", borderRadius: 1, display: "grid", placeItems: "center", color: "text.secondary" }}>
                        Logo
                      </Box>
                    )}
                    <Stack spacing={0.5}>
                      <Typography variant="h6" sx={{ fontWeight: 900 }}>{form.displayName || form.clinicName || "Clinic Name"}</Typography>
                      <Typography variant="body2" color="text.secondary">{form.addressLine1 || "Clinic address"}</Typography>
                      <Typography variant="body2" color="text.secondary">{form.phone || "Clinic phone"}{form.email ? ` • ${form.email}` : ""}</Typography>
                    </Stack>
                  </Box>
                  <Box sx={{ p: 1.5, borderRadius: 2, background: normalizeHexColor(templateForm.accentColor, "#14B8A6"), color: "#083344" }}>
                    <Typography variant="body2" sx={{ fontWeight: 800 }}>Signature text</Typography>
                    <Typography variant="body2">{templateForm.doctorSignatureText || "Doctor signature text"}</Typography>
                    <Typography variant="caption">{templateForm.footerText || "Clinic footer text"}</Typography>
                  </Box>
                </Stack>
                <Stack spacing={1.5}>
                  <Box sx={{ p: 2, border: "1px solid", borderColor: "divider", borderRadius: 2, minHeight: 120 }}>
                    <Typography variant="body2" sx={{ fontWeight: 800, mb: 1 }}>Footer layout</Typography>
                    <Grid container spacing={1.5}>
                      <Grid size={templateForm.showQrCode ? 5 : 0}>
                        {templateForm.showQrCode ? (
                          <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1, minHeight: 86, display: "grid", placeItems: "center", textAlign: "center" }}>
                            <Box>
                              <Box sx={{ width: 48, height: 48, border: "2px solid", borderColor: "text.primary", mx: "auto", mb: 1 }} />
                              <Typography variant="caption">Verification QR</Typography>
                            </Box>
                          </Box>
                        ) : null}
                      </Grid>
                      <Grid size={templateForm.showQrCode ? 7 : 12}>
                        <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 1.5, minHeight: 86 }}>
                          <Typography variant="body2" sx={{ fontWeight: 800 }}>Doctor Signature</Typography>
                          <Typography variant="body2">{templateForm.doctorSignatureText || "Doctor signature text"}</Typography>
                          <Typography variant="caption">{form.registrationNumber ? `Reg No: ${form.registrationNumber}` : "Registration number"}</Typography>
                        </Box>
                      </Grid>
                    </Grid>
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    The preview mirrors the PDF footer: QR on the left, signature on the right, no generated-at/generated-by text.
                  </Typography>
                </Stack>
              </Box>
            </Box>

            {templateHistory.length ? (
              <Typography variant="caption" color="text.secondary">
                Latest template version: {templateHistory[0]?.templateVersion ?? 0}. Previous versions are retained for audit review.
              </Typography>
            ) : null}
            {canEdit ? (
              <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1, flexWrap: "wrap" }}>
                <Button type="button" variant="outlined" disabled={saving || logoBusy} onClick={() => void previewTemplate()}>Preview PDF</Button>
                <Button type="button" variant="contained" disabled={saving || logoBusy} onClick={() => void saveTemplate()}>{saving ? "Saving..." : "Save Template"}</Button>
              </Box>
            ) : null}
          </Stack>
        </CardContent>
      </Card>
      {canEdit ? (
        <TenantOnboardingWizardDialog
          open={wizardOpen}
          auth={auth}
          onClose={() => setWizardOpen(false)}
          onCompleted={(next) => {
            setOnboardingStatus(next);
            setWizardOpen(false);
          }}
        />
      ) : null}
    </Stack>
  );
}
