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

import { useAuth } from "../../auth/useAuth";
import {
  getClinicProfile,
  getPrescriptionTemplate,
  getPrescriptionTemplateHistory,
  previewPrescriptionTemplate,
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
};

type TemplateFormState = {
  clinicLogoDocumentId: string;
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
    primaryColor: form.primaryColor.trim() || "#0f766e",
    accentColor: form.accentColor.trim() || "#14b8a6",
    disclaimer: form.disclaimer.trim() || null,
    doctorSignatureText: form.doctorSignatureText.trim() || null,
    showQrCode: form.showQrCode,
    watermarkText: form.watermarkText.trim() || null,
  };
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
  };
}

function toInput(form: ClinicProfileFormState): ClinicProfileInput {
  return {
    clinicName: form.clinicName.trim(),
    displayName: form.displayName.trim(),
    phone: form.phone.trim(),
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
  };
}

export default function ClinicProfilePage() {
  const auth = useAuth();
  const canEdit = auth.hasPermission("clinic.update");
  const [form, setForm] = React.useState<ClinicProfileFormState>(emptyForm);
  const [templateForm, setTemplateForm] = React.useState<TemplateFormState>(emptyTemplate);
  const [templateHistory, setTemplateHistory] = React.useState<PrescriptionTemplateConfig[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);

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
        const [profile, template, history] = await Promise.all([
          getClinicProfile(auth.accessToken, auth.tenantId),
          getPrescriptionTemplate(auth.accessToken, auth.tenantId),
          getPrescriptionTemplateHistory(auth.accessToken, auth.tenantId),
        ]);
        if (!cancelled) {
          setForm(toFormState(profile));
          setTemplateForm(templateToForm(template));
          setTemplateHistory(history);
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

  const updateTextField =
    (field: Exclude<keyof ClinicProfileFormState, "active">) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const value = event.target.value;
      setForm((current) => ({ ...current, [field]: value } as ClinicProfileFormState));
    };

  const updateActive = (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm((current) => ({ ...current, active: event.target.checked }));
  };

  const updateTemplateField =
    (field: Exclude<keyof TemplateFormState, "showQrCode">) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const value = event.target.value;
      setTemplateForm((current) => ({ ...current, [field]: value }));
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
      const saved = await updateClinicProfile(auth.accessToken, auth.tenantId, toInput(form));
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
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const saved = await updatePrescriptionTemplate(auth.accessToken, auth.tenantId, templateInput(templateForm));
      setTemplateForm(templateToForm(saved));
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
      setError(err instanceof Error ? err.message : "Failed to preview prescription template");
    }
  };

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  return (
    <Stack spacing={3} component="form" onSubmit={onSubmit}>
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
                  <TextField fullWidth label="Clinic name" value={form.clinicName} onChange={updateTextField("clinicName")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField fullWidth label="Display name" value={form.displayName} onChange={updateTextField("displayName")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField fullWidth label="Phone" value={form.phone} onChange={updateTextField("phone")} disabled={!canEdit || saving} />
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
                  <TextField fullWidth label="City" value={form.city} onChange={updateTextField("city")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField fullWidth label="State" value={form.state} onChange={updateTextField("state")} disabled={!canEdit || saving} />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField fullWidth label="Country" value={form.country} onChange={updateTextField("country")} disabled={!canEdit || saving} />
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
                  <TextField fullWidth label="Logo document ID" value={form.logoDocumentId} onChange={updateTextField("logoDocumentId")} disabled={!canEdit || saving} />
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
                Tenant-managed branding for prescription PDFs and delivery. Saving creates a new template version for auditability.
              </Typography>
            </Box>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth label="Logo document ID" value={templateForm.clinicLogoDocumentId} onChange={updateTemplateField("clinicLogoDocumentId")} disabled={!canEdit || saving} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField fullWidth label="Primary color" value={templateForm.primaryColor} onChange={updateTemplateField("primaryColor")} disabled={!canEdit || saving} />
              </Grid>
              <Grid size={{ xs: 6, md: 3 }}>
                <TextField fullWidth label="Accent color" value={templateForm.accentColor} onChange={updateTemplateField("accentColor")} disabled={!canEdit || saving} />
              </Grid>
              <Grid size={12}>
                <TextField fullWidth multiline minRows={2} label="Clinic header" value={templateForm.headerText} onChange={updateTemplateField("headerText")} disabled={!canEdit || saving} />
              </Grid>
              <Grid size={12}>
                <TextField fullWidth multiline minRows={2} label="Clinic footer" value={templateForm.footerText} onChange={updateTemplateField("footerText")} disabled={!canEdit || saving} />
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
                  label="Show QR code placeholder"
                />
              </Grid>
            </Grid>
            <Box sx={{ p: 2, border: "1px solid", borderColor: "divider", borderRadius: 2, background: `linear-gradient(135deg, ${templateForm.primaryColor || "#0f766e"}, ${templateForm.accentColor || "#14b8a6"})`, color: "white" }}>
              <Typography variant="h6" sx={{ fontWeight: 900 }}>{form.displayName || form.clinicName || "Clinic Name"}</Typography>
              <Typography variant="body2">{templateForm.headerText || "Header text preview"}</Typography>
              <Typography variant="caption">{templateForm.footerText || "Footer text preview"}</Typography>
            </Box>
            {templateHistory.length ? (
              <Typography variant="caption" color="text.secondary">
                Latest template version: {templateHistory[0]?.templateVersion ?? 0}. Previous versions are retained for audit review.
              </Typography>
            ) : null}
            {canEdit ? (
              <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1, flexWrap: "wrap" }}>
                <Button type="button" variant="outlined" disabled={saving} onClick={() => void previewTemplate()}>Preview PDF</Button>
                <Button type="button" variant="contained" disabled={saving} onClick={() => void saveTemplate()}>{saving ? "Saving..." : "Save Template"}</Button>
              </Box>
            ) : null}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
}
