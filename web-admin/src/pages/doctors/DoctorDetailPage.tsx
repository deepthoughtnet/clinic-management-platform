import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Alert, Autocomplete, Box, Button, Card, CardContent, Chip, CircularProgress, FormControlLabel, Grid, Stack, Switch, TextField, Typography } from "@mui/material";

import { doctorUpdateSchema, firstZodError, mapZodErrors, normalizeIndianMobileInput } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { getDoctorProfile, updateDoctorProfile, updateDoctorProfileWithPhoto, type DoctorProfile, type DoctorProfileInput } from "../../api/clinicApi";
import DoctorAvatar from "../../components/doctor/DoctorAvatar";
import RequiredLabel from "../../components/forms/RequiredLabel";
import { formatFileSize, ImageUploadError, optimizeAvatarUpload } from "../../utils/imageUpload";

type FormState = {
  mobile: string;
  specializations: string[];
  specializationsInput: string;
  qualifications: string[];
  qualificationOther: string;
  registrationNumber: string;
  consultationRoom: string;
  consultationFee: string;
  opdFee: string;
  followUpFee: string;
  emergencyFee: string;
  yearsOfExperience: string;
  dateOfBirth: string;
  active: boolean;
  publicListingEnabled: boolean;
  slug: string;
};

const SPECIALIZATION_OPTIONS = [
  "General Medicine",
  "Pediatrics",
  "Gynecology",
  "Orthopedics",
  "Dermatology",
  "ENT",
  "Ophthalmology",
  "Cardiology",
  "Neurology",
  "Psychiatry",
  "General Surgery",
  "Anesthesiology",
  "Radiology",
  "Pathology",
  "Gastroenterology",
  "Pulmonology",
  "Nephrology",
  "Endocrinology",
  "Urology",
  "Oncology",
  "Other",
] as const;

const QUALIFICATION_OPTIONS = [
  "MBBS",
  "MD",
  "MS",
  "DNB",
  "DM",
  "MCh",
  "Diploma",
  "BDS",
  "MDS",
  "BAMS",
  "BHMS",
  "Other",
] as const;

const OTHER_OPTION = "Other";

function normalizeText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function sanitizeIndianMobileInput(value: string): string {
  const normalized = normalizeIndianMobileInput(value);
  return typeof normalized === "string" ? normalized.slice(0, 10) : "";
}

function sanitizeMoneyInput(value: string): string {
  const stripped = value.replace(/[^\d.]/g, "");
  if (!stripped) {
    return "";
  }
  const firstDotIndex = stripped.indexOf(".");
  if (firstDotIndex < 0) {
    return stripped.replace(/^0+(?=\d)/, "") || stripped;
  }
  const wholePart = stripped.slice(0, firstDotIndex).replace(/^0+(?=\d)/, "") || "0";
  const decimalPart = stripped
    .slice(firstDotIndex + 1)
    .replace(/\./g, "")
    .slice(0, 2);
  return `${wholePart}.${decimalPart}`;
}

function normalizeNumber(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isNaN(parsed) ? null : parsed;
}

function normalizeSpecializations(values: string[], draft: string): string[] {
  const committed = values.map((value) => value.trim()).filter(Boolean);
  const deduped: string[] = [];
  const seen = new Set<string>();
  for (const value of committed) {
    const normalized = value.toLowerCase();
    if (seen.has(normalized)) {
      continue;
    }
    seen.add(normalized);
    deduped.push(value);
  }
  const otherIndex = deduped.findIndex((value) => value.toLowerCase() === OTHER_OPTION.toLowerCase());
  if (otherIndex >= 0) {
    const trimmedDraft = draft.trim();
    if (trimmedDraft) {
      deduped.splice(otherIndex, 1, trimmedDraft);
    }
  }
  return deduped.filter(Boolean);
}

function parseQualificationValues(value: string | null | undefined): { qualifications: string[]; qualificationOther: string } {
  const raw = value || "";
  const parts = raw.split(",").map((item) => item.trim()).filter(Boolean);
  if (!parts.length) {
    return { qualifications: [], qualificationOther: "" };
  }
  const qualifications: string[] = [];
  const unknownParts: string[] = [];
  const seen = new Set<string>();
  for (const part of parts) {
    const normalized = part.toLowerCase();
    if (seen.has(normalized)) {
      continue;
    }
    seen.add(normalized);
    if (QUALIFICATION_OPTIONS.some((option) => option.toLowerCase() === normalized && option.toLowerCase() !== OTHER_OPTION.toLowerCase())) {
      qualifications.push(part);
    } else if (normalized !== OTHER_OPTION.toLowerCase()) {
      unknownParts.push(part);
    }
  }
  if (unknownParts.length > 0) {
    qualifications.push(OTHER_OPTION);
  }
  return {
    qualifications,
    qualificationOther: unknownParts.join(", "),
  };
}

function normalizeQualificationValues(values: string[], otherDraft: string): string {
  const deduped: string[] = [];
  const seen = new Set<string>();
  for (const value of values) {
    const trimmed = value.trim();
    if (!trimmed) {
      continue;
    }
    const normalized = trimmed.toLowerCase();
    if (seen.has(normalized)) {
      continue;
    }
    seen.add(normalized);
    if (normalized === OTHER_OPTION.toLowerCase()) {
      const custom = otherDraft.trim();
      if (custom) {
        deduped.push(custom);
      }
      continue;
    }
    deduped.push(trimmed);
  }
  return deduped.join(", ");
}

function calculateAge(dateOfBirth: string): number | null {
  const trimmed = dateOfBirth.trim();
  if (!trimmed) {
    return null;
  }
  const parsed = new Date(`${trimmed}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  const now = new Date();
  let age = now.getUTCFullYear() - parsed.getUTCFullYear();
  const monthDiff = now.getUTCMonth() - parsed.getUTCMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getUTCDate() < parsed.getUTCDate())) {
    age -= 1;
  }
  return age >= 0 ? age : null;
}

function toForm(profile: DoctorProfile): FormState {
  const specializations = profile.specializations?.length
    ? profile.specializations
    : (profile.specialization ? [profile.specialization] : []);
  const opdFee = profile.opdFee ?? profile.consultationFee;
  const qualification = parseQualificationValues(profile.qualification);
  return {
    mobile: profile.mobile || "",
    specializations,
    specializationsInput: "",
    qualifications: qualification.qualifications,
    qualificationOther: qualification.qualificationOther,
    registrationNumber: profile.registrationNumber || "",
    consultationRoom: profile.consultationRoom || "",
    consultationFee: opdFee == null ? "" : String(opdFee),
    opdFee: opdFee == null ? "" : String(opdFee),
    followUpFee: profile.followUpFee == null ? "" : String(profile.followUpFee),
    emergencyFee: profile.emergencyFee == null ? "" : String(profile.emergencyFee),
    yearsOfExperience: profile.yearsOfExperience == null ? "" : String(profile.yearsOfExperience),
    dateOfBirth: profile.dateOfBirth || "",
    active: profile.active,
    publicListingEnabled: profile.publicListingEnabled,
    slug: profile.slug || "",
  };
}

export default function DoctorDetailPage() {
  const { id = "" } = useParams();
  const auth = useAuth();
  const navigate = useNavigate();
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [profile, setProfile] = React.useState<DoctorProfile | null>(null);
  const [form, setForm] = React.useState<FormState | null>(null);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [photoFile, setPhotoFile] = React.useState<File | null>(null);
  const [photoPreviewUrl, setPhotoPreviewUrl] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [info, setInfo] = React.useState<string | null>(null);

  const replacePhotoPreview = React.useCallback((nextPreviewUrl: string | null) => {
    setPhotoPreviewUrl((current) => {
      if (current && current.startsWith("blob:") && current !== nextPreviewUrl) {
        URL.revokeObjectURL(current);
      }
      return nextPreviewUrl;
    });
  }, []);

  const role = (auth.tenantRole || "").toUpperCase();
  const isDoctor = role === "DOCTOR";
  const isReceptionist = role === "RECEPTIONIST";
  const isAdmin = role === "CLINIC_ADMIN";
  const canEdit = auth.hasPermission("appointment.manage") && (isDoctor || isReceptionist || isAdmin);

  const clearFieldError = React.useCallback((field: string) => {
    setFieldErrors((current) => {
      if (!current[field]) {
        return current;
      }
      const next = { ...current };
      delete next[field];
      return next;
    });
  }, []);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken || !auth.tenantId || !id) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const loaded = await getDoctorProfile(auth.accessToken, auth.tenantId, id);
        if (!cancelled) {
          setProfile(loaded);
          setForm(toForm(loaded));
          setFieldErrors({});
          setPhotoFile(null);
          replacePhotoPreview(loaded.photoUrl || null);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load doctor profile");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, id, replacePhotoPreview]);

  React.useEffect(() => {
    return () => {
      if (photoPreviewUrl && photoPreviewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(photoPreviewUrl);
      }
    };
  }, [photoPreviewUrl]);

  React.useEffect(() => {
    if (!saving) {
      return;
    }
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [saving]);

  if (!auth.tenantId) return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  if (loading) return <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}><CircularProgress /></Box>;
  if (!profile || !form) return <Alert severity="error">{error || "Doctor profile not found"}</Alert>;

  const formReadOnly = !canEdit
    || (isDoctor && profile.doctorUserId !== auth.appUserId);

  const receptionistReadOnlyFields = isReceptionist && !isAdmin;

  const save = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const hasOtherSpecialization = form.specializations.some((value) => value.trim().toLowerCase() === OTHER_OPTION.toLowerCase());
    if (hasOtherSpecialization && !form.specializationsInput.trim()) {
      setFieldErrors((current) => ({ ...current, specializations: "Enter a specialization for Other." }));
      setError("Enter a specialization for Other.");
      return;
    }
    const specializations = normalizeSpecializations(form.specializations, form.specializationsInput);
    const hasOtherQualification = form.qualifications.some((value) => value.trim().toLowerCase() === OTHER_OPTION.toLowerCase());
    if (hasOtherQualification && !form.qualificationOther.trim()) {
      setFieldErrors((current) => ({ ...current, qualifications: "Enter a qualification for Other." }));
      setError("Enter a qualification for Other.");
      return;
    }
    const qualification = normalizeQualificationValues(form.qualifications, form.qualificationOther);
    const payload: DoctorProfileInput = {
      mobile: normalizeText(form.mobile),
      specialization: specializations[0] || null,
      specializations,
      qualification,
      registrationNumber: normalizeText(form.registrationNumber),
      consultationRoom: normalizeText(form.consultationRoom),
      consultationFee: normalizeNumber(form.opdFee),
      opdFee: normalizeNumber(form.opdFee),
      followUpFee: normalizeNumber(form.followUpFee),
      emergencyFee: normalizeNumber(form.emergencyFee),
      yearsOfExperience: normalizeNumber(form.yearsOfExperience),
      dateOfBirth: normalizeText(form.dateOfBirth),
      active: form.active,
      publicListingEnabled: form.publicListingEnabled,
      slug: normalizeText(form.slug),
    };
    const parsed = doctorUpdateSchema.safeParse({
      ...payload,
    });
    if (!parsed.success) {
      setFieldErrors(mapZodErrors(parsed.error));
      setError(firstZodError(parsed.error));
      return;
    }
    setSaving(true);
    setError(null);
    setFieldErrors({});
    try {
      const nextProfile = photoFile
        ? await updateDoctorProfileWithPhoto(auth.accessToken, auth.tenantId, profile.doctorUserId, payload, photoFile)
        : await updateDoctorProfile(auth.accessToken, auth.tenantId, profile.doctorUserId, payload);
      setProfile(nextProfile);
      setForm(toForm(nextProfile));
      setPhotoFile(null);
      replacePhotoPreview(nextProfile.photoUrl || null);
      setInfo("Doctor profile saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save doctor profile");
    } finally {
      setSaving(false);
    }
  };

  const handlePhotoChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] || null;
    event.target.value = "";
    if (!file) {
      return;
    }
    setError(null);
    setInfo(null);
    try {
      const optimized = await optimizeAvatarUpload(file);
      setPhotoFile(optimized.file);
      replacePhotoPreview(optimized.previewUrl);
    } catch (err) {
      setPhotoFile(null);
      replacePhotoPreview(profile.photoUrl || null);
      if (err instanceof ImageUploadError) {
        setError(err.message);
        return;
      }
      setError(err instanceof Error ? err.message : "Failed to prepare doctor profile photo");
    }
  };

  const photoSrc = photoPreviewUrl || profile.photoUrl || null;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>{profile.doctorName || profile.doctorUserId}</Typography>
          <Typography variant="body2" color="text.secondary">{profile.email || "No email"} • {profile.membershipRole || "DOCTOR"}</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigate("/settings/users-roles")}>Back</Button>
          {canEdit ? <Button variant="contained" disabled={saving || formReadOnly} onClick={() => void save()}>Save</Button> : null}
        </Stack>
      </Box>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {info ? <Alert severity="success" onClose={() => setInfo(null)}>{info}</Alert> : null}
      {isReceptionist ? <Alert severity="info">Receptionist can update scheduling/contact details only.</Alert> : null}

      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12 }}>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems={{ xs: "flex-start", sm: "center" }}>
                <DoctorAvatar
                  name={profile.doctorName || profile.email || "Doctor"}
                  photoUrl={photoSrc}
                  alt={profile.doctorName || "Doctor profile"}
                  sx={{ width: 72, height: 72, fontWeight: 800 }}
                />
                <Stack spacing={1} sx={{ flex: 1 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Profile Photo</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Upload a JPG, PNG, or WEBP image for doctor lists and selectors. Images larger than 10 MB are rejected before upload.
                  </Typography>
                  {canEdit ? (
                    <Button variant="outlined" component="label" disabled={saving || formReadOnly}>
                      {photoFile ? "Change Photo" : "Upload Photo"}
                      <input
                        hidden
                        type="file"
                        accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
                        onChange={(event) => void handlePhotoChange(event)}
                      />
                    </Button>
                  ) : null}
                  {photoFile ? <Chip size="small" label={`${photoFile.name} • ${formatFileSize(photoFile.size)}`} /> : null}
                </Stack>
              </Stack>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Name" value={profile.doctorName || ""} disabled /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Email" value={profile.email || ""} disabled /></Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label={<RequiredLabel text="Mobile" />}
                value={form.mobile}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("mobile");
                  setForm((c) => c ? { ...c, mobile: sanitizeIndianMobileInput(e.target.value) } : c);
                }}
                error={Boolean(fieldErrors.mobile)}
                helperText={fieldErrors.mobile || "Enter a valid 10-digit mobile number."}
                inputProps={{ inputMode: "numeric", maxLength: 10, "aria-required": true }}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Autocomplete
                multiple
                options={[...SPECIALIZATION_OPTIONS]}
                value={form.specializations}
                onChange={(_, value) => {
                  clearFieldError("specializations");
                  setForm((c) => c ? {
                    ...c,
                    specializations: value.map((item) => String(item).trim()).filter(Boolean),
                    specializationsInput: value.some((item) => String(item).trim().toLowerCase() === OTHER_OPTION.toLowerCase())
                      ? (c.specializationsInput || "")
                      : "",
                  } : c);
                }}
                isOptionEqualToValue={(option, value) => option === value}
                renderTags={(value, getTagProps) => value.map((option, index) => (
                  <Chip size="small" variant="outlined" label={option} {...getTagProps({ index })} key={option} />
                ))}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label={<RequiredLabel text="Specialization" />}
                    helperText={fieldErrors.specializations || "Select at least one specialization. Choose Other to enter a custom specialization below."}
                    error={Boolean(fieldErrors.specializations)}
                    disabled={formReadOnly}
                    inputProps={{ ...params.inputProps, "aria-required": true }}
                  />
                )}
              />
              {form.specializations.some((value) => value.trim().toLowerCase() === OTHER_OPTION.toLowerCase()) ? (
                <TextField
                  sx={{ mt: 1.5 }}
                  fullWidth
                  label={<RequiredLabel text="Other specialization" />}
                  value={form.specializationsInput}
                  disabled={formReadOnly}
                  onChange={(e) => {
                    clearFieldError("specializations");
                    setForm((c) => c ? { ...c, specializationsInput: e.target.value } : c);
                  }}
                  error={Boolean(fieldErrors.specializations)}
                  helperText={fieldErrors.specializations || "Required when Other is selected."}
                  inputProps={{ maxLength: 128, "aria-required": true }}
                />
              ) : null}
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Stack spacing={1.5}>
                <Autocomplete
                  multiple
                  options={[...QUALIFICATION_OPTIONS]}
                  value={form.qualifications}
                  onChange={(_, value) => {
                    clearFieldError("qualifications");
                    setForm((c) => c ? {
                      ...c,
                      qualifications: value.map((item) => String(item).trim()).filter(Boolean),
                      qualificationOther: value.some((item) => String(item).trim().toLowerCase() === OTHER_OPTION.toLowerCase())
                        ? (c.qualificationOther || "")
                        : "",
                    } : c);
                  }}
                  isOptionEqualToValue={(option, value) => option === value}
                  renderTags={(value, getTagProps) => value.map((option, index) => (
                    <Chip size="small" variant="outlined" label={option} {...getTagProps({ index })} key={option} />
                  ))}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label={<RequiredLabel text="Qualification" />}
                      error={Boolean(fieldErrors.qualifications)}
                      helperText={fieldErrors.qualifications || "Choose one or more qualifications. Select Other for a custom qualification below."}
                      disabled={formReadOnly || receptionistReadOnlyFields}
                      inputProps={{ ...params.inputProps, "aria-required": true }}
                    />
                  )}
                />
                {form.qualifications.some((value) => value.trim().toLowerCase() === OTHER_OPTION.toLowerCase()) ? (
                  <TextField
                    fullWidth
                    label={<RequiredLabel text="Other qualification" />}
                    value={form.qualificationOther}
                    disabled={formReadOnly || receptionistReadOnlyFields}
                    onChange={(e) => {
                      clearFieldError("qualifications");
                      setForm((c) => c ? { ...c, qualificationOther: e.target.value } : c);
                    }}
                    error={Boolean(fieldErrors.qualifications)}
                    helperText={fieldErrors.qualifications || "Required when Other is selected."}
                    inputProps={{ maxLength: 256, "aria-required": true }}
                  />
                ) : null}
              </Stack>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label={<RequiredLabel text="Registration Number" />}
                value={form.registrationNumber}
                disabled={formReadOnly || receptionistReadOnlyFields}
                onChange={(e) => {
                  clearFieldError("registrationNumber");
                  setForm((c) => c ? { ...c, registrationNumber: e.target.value } : c);
                }}
                error={Boolean(fieldErrors.registrationNumber)}
                helperText={fieldErrors.registrationNumber || "Required."}
                inputProps={{ "aria-required": true }}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Consultation Room/Location"
                value={form.consultationRoom}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("consultationRoom");
                  setForm((c) => c ? { ...c, consultationRoom: e.target.value } : c);
                }}
                error={Boolean(fieldErrors.consultationRoom)}
                helperText={fieldErrors.consultationRoom || "Optional."}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                type="text"
                label={<RequiredLabel text="OPD Fee" />}
                value={form.opdFee}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("opdFee");
                  setForm((c) => c ? { ...c, opdFee: sanitizeMoneyInput(e.target.value), consultationFee: sanitizeMoneyInput(e.target.value) } : c);
                }}
                inputProps={{ inputMode: "decimal", "aria-required": true }}
                error={Boolean(fieldErrors.opdFee)}
                helperText={fieldErrors.opdFee || "Required."}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                type="text"
                label={<RequiredLabel text="Follow-up Fee" />}
                value={form.followUpFee}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("followUpFee");
                  setForm((c) => c ? { ...c, followUpFee: sanitizeMoneyInput(e.target.value) } : c);
                }}
                inputProps={{ inputMode: "decimal", "aria-required": true }}
                error={Boolean(fieldErrors.followUpFee)}
                helperText={fieldErrors.followUpFee || "Required."}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                type="text"
                label={<RequiredLabel text="Emergency Fee" />}
                value={form.emergencyFee}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("emergencyFee");
                  setForm((c) => c ? { ...c, emergencyFee: sanitizeMoneyInput(e.target.value) } : c);
                }}
                inputProps={{ inputMode: "decimal", "aria-required": true }}
                error={Boolean(fieldErrors.emergencyFee)}
                helperText={fieldErrors.emergencyFee || "Required."}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                type="number"
                label={<RequiredLabel text="Years of Experience" />}
                value={form.yearsOfExperience}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("yearsOfExperience");
                  setForm((c) => c ? { ...c, yearsOfExperience: e.target.value } : c);
                }}
                inputProps={{ min: 0, step: 1, "aria-required": true }}
                error={Boolean(fieldErrors.yearsOfExperience)}
                helperText={fieldErrors.yearsOfExperience || "Required whole number."}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                type="date"
                label={<RequiredLabel text="Date of Birth" />}
                value={form.dateOfBirth}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("dateOfBirth");
                  setForm((c) => c ? { ...c, dateOfBirth: e.target.value } : c);
                }}
                inputProps={{ "aria-required": true, max: new Date().toISOString().slice(0, 10) }}
                InputLabelProps={{ shrink: true }}
                error={Boolean(fieldErrors.dateOfBirth)}
                helperText={fieldErrors.dateOfBirth || "Required. Use a past date."}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                label="Age (derived)"
                value={calculateAge(form.dateOfBirth) == null ? "" : `${calculateAge(form.dateOfBirth)} years`}
                disabled
                helperText="Calculated from date of birth."
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Stack direction="row" spacing={1} alignItems="center">
                <TextField fullWidth label="Availability/Calendar" value="Open in Appointments" disabled />
                <Button variant="outlined" onClick={() => navigate(`/appointments?doctorUserId=${profile.doctorUserId}`)}>Open</Button>
              </Stack>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={form.publicListingEnabled}
                    disabled={formReadOnly || receptionistReadOnlyFields}
                    onChange={(e) => setForm((c) => c ? { ...c, publicListingEnabled: e.target.checked } : c)}
                  />
                }
                label={form.publicListingEnabled ? "Public listing enabled" : "Public listing disabled"}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label={<RequiredLabel text="Public slug" required={false} />}
                value={form.slug}
                disabled={formReadOnly || receptionistReadOnlyFields}
                onChange={(e) => {
                  clearFieldError("slug");
                  setForm((c) => c ? { ...c, slug: e.target.value } : c);
                }}
                error={Boolean(fieldErrors.slug)}
                helperText={fieldErrors.slug || "Optional. Leave blank to auto-generate from doctor name."}
              />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Alert severity="info">Public Profile settings control whether this doctor appears in public discovery.</Alert>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Chip label={form.active ? "Active" : "Inactive"} color={form.active ? "success" : "default"} />
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    </Stack>
  );
}
