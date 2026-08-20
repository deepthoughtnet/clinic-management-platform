import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Alert, Autocomplete, Box, Button, Card, CardContent, Chip, CircularProgress, FormControlLabel, Grid, Stack, Switch, TextField, Typography } from "@mui/material";

import { doctorUpdateSchema, firstZodError, mapZodErrors } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { getDoctorProfile, updateDoctorProfile, updateDoctorProfileWithPhoto, type DoctorProfile, type DoctorProfileInput } from "../../api/clinicApi";
import DoctorAvatar from "../../components/doctor/DoctorAvatar";
import RequiredLabel from "../../components/forms/RequiredLabel";
import { formatFileSize, ImageUploadError, optimizeAvatarUpload } from "../../utils/imageUpload";

type FormState = {
  mobile: string;
  specializations: string[];
  specializationsInput: string;
  qualification: string;
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

function normalizeText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
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
  if (committed.length > 0) {
    return committed;
  }
  const trimmedDraft = draft.trim();
  return trimmedDraft ? [trimmedDraft] : [];
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
  return {
    mobile: profile.mobile || "",
    specializations,
    specializationsInput: "",
    qualification: profile.qualification || "",
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
    const specializations = normalizeSpecializations(form.specializations, form.specializationsInput);
    const payload: DoctorProfileInput = {
      mobile: normalizeText(form.mobile),
      specialization: specializations[0] || null,
      specializations,
      qualification: normalizeText(form.qualification),
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
                label={<RequiredLabel text="Mobile" required />}
                value={form.mobile}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("mobile");
                  setForm((c) => c ? { ...c, mobile: e.target.value } : c);
                }}
                error={Boolean(fieldErrors.mobile)}
                helperText={fieldErrors.mobile || "Enter a valid 10-digit mobile number."}
                required
                inputProps={{ inputMode: "tel", "aria-required": true }}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Autocomplete
                multiple
                freeSolo
                options={[] as string[]}
                value={form.specializations}
                inputValue={form.specializationsInput}
                onInputChange={(_, value) => {
                  clearFieldError("specializations");
                  setForm((c) => c ? { ...c, specializationsInput: value } : c);
                }}
                onChange={(_, value) => {
                  clearFieldError("specializations");
                  setForm((c) => c ? {
                    ...c,
                    specializations: value.map((item) => String(item).trim()).filter(Boolean),
                    specializationsInput: "",
                  } : c);
                }}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label={<RequiredLabel text="Specialization" required />}
                    helperText={fieldErrors.specializations || "Select at least one specialization."}
                    error={Boolean(fieldErrors.specializations)}
                    disabled={formReadOnly}
                    required
                  />
                )}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label={<RequiredLabel text="Qualification" required />}
                value={form.qualification}
                disabled={formReadOnly || receptionistReadOnlyFields}
                onChange={(e) => {
                  clearFieldError("qualification");
                  setForm((c) => c ? { ...c, qualification: e.target.value } : c);
                }}
                error={Boolean(fieldErrors.qualification)}
                helperText={fieldErrors.qualification || "Required."}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label={<RequiredLabel text="Registration Number" required />}
                value={form.registrationNumber}
                disabled={formReadOnly || receptionistReadOnlyFields}
                onChange={(e) => {
                  clearFieldError("registrationNumber");
                  setForm((c) => c ? { ...c, registrationNumber: e.target.value } : c);
                }}
                error={Boolean(fieldErrors.registrationNumber)}
                helperText={fieldErrors.registrationNumber || "Required."}
                required
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
                type="number"
                label={<RequiredLabel text="OPD Fee" required />}
                value={form.opdFee}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("opdFee");
                  setForm((c) => c ? { ...c, opdFee: e.target.value, consultationFee: e.target.value } : c);
                }}
                inputProps={{ min: 0, step: "0.01", "aria-required": true }}
                error={Boolean(fieldErrors.opdFee)}
                helperText={fieldErrors.opdFee || "Required."}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                type="number"
                label={<RequiredLabel text="Follow-up Fee" required />}
                value={form.followUpFee}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("followUpFee");
                  setForm((c) => c ? { ...c, followUpFee: e.target.value } : c);
                }}
                inputProps={{ min: 0, step: "0.01", "aria-required": true }}
                error={Boolean(fieldErrors.followUpFee)}
                helperText={fieldErrors.followUpFee || "Required."}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                type="number"
                label={<RequiredLabel text="Emergency Fee" required />}
                value={form.emergencyFee}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("emergencyFee");
                  setForm((c) => c ? { ...c, emergencyFee: e.target.value } : c);
                }}
                inputProps={{ min: 0, step: "0.01", "aria-required": true }}
                error={Boolean(fieldErrors.emergencyFee)}
                helperText={fieldErrors.emergencyFee || "Required."}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                type="number"
                label={<RequiredLabel text="Years of Experience" required />}
                value={form.yearsOfExperience}
                disabled={formReadOnly}
                onChange={(e) => {
                  clearFieldError("yearsOfExperience");
                  setForm((c) => c ? { ...c, yearsOfExperience: e.target.value } : c);
                }}
                inputProps={{ min: 0, step: 1, "aria-required": true }}
                error={Boolean(fieldErrors.yearsOfExperience)}
                helperText={fieldErrors.yearsOfExperience || "Required whole number."}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                type="date"
                label={<RequiredLabel text="Date of Birth" required />}
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
                required
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
