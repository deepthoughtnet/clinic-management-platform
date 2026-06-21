import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, FormControlLabel, Grid, Stack, Switch, TextField, Typography } from "@mui/material";

import { doctorUpdateSchema, normalizeIndianMobileInput } from "@deepthoughtnet/form-validation-kit";
import { useAuth } from "../../auth/useAuth";
import { getDoctorProfile, updateDoctorProfile, type DoctorProfile } from "../../api/clinicApi";

type FormState = {
  mobile: string;
  specialization: string;
  qualification: string;
  registrationNumber: string;
  consultationRoom: string;
  consultationFee: string;
  yearsOfExperience: string;
  age: string;
  active: boolean;
  publicListingEnabled: boolean;
  slug: string;
};

function toForm(profile: DoctorProfile): FormState {
  return {
    mobile: profile.mobile || "",
    specialization: profile.specialization || "",
    qualification: profile.qualification || "",
    registrationNumber: profile.registrationNumber || "",
    consultationRoom: profile.consultationRoom || "",
    consultationFee: profile.consultationFee == null ? "" : String(profile.consultationFee),
    yearsOfExperience: profile.yearsOfExperience == null ? "" : String(profile.yearsOfExperience),
    age: profile.age == null ? "" : String(profile.age),
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
  const [error, setError] = React.useState<string | null>(null);
  const [info, setInfo] = React.useState<string | null>(null);

  const role = (auth.tenantRole || "").toUpperCase();
  const isDoctor = role === "DOCTOR";
  const isReceptionist = role === "RECEPTIONIST";
  const isAdmin = role === "CLINIC_ADMIN";
  const canEdit = auth.hasPermission("appointment.manage") && (isDoctor || isReceptionist || isAdmin);

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
  }, [auth.accessToken, auth.tenantId, id]);

  if (!auth.tenantId) return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  if (loading) return <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}><CircularProgress /></Box>;
  if (!profile || !form) return <Alert severity="error">{error || "Doctor profile not found"}</Alert>;

  const formReadOnly = !canEdit
    || (isDoctor && profile.doctorUserId !== auth.appUserId);

  const receptionistReadOnlyFields = isReceptionist && !isAdmin;

  const save = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    const parsed = doctorUpdateSchema.safeParse({
      mobile: form.mobile,
      specialization: form.specialization,
      qualification: form.qualification,
      registrationNumber: form.registrationNumber,
      consultationRoom: form.consultationRoom,
      consultationFee: form.consultationFee,
      yearsOfExperience: form.yearsOfExperience,
      age: form.age,
      active: form.active,
      publicListingEnabled: form.publicListingEnabled,
      slug: form.slug,
    });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message || "Failed to save doctor profile");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const saved = await updateDoctorProfile(auth.accessToken, auth.tenantId, profile.doctorUserId, {
        mobile: parsed.data.mobile ? (normalizeIndianMobileInput(parsed.data.mobile) as string) : null,
        specialization: parsed.data.specialization?.trim() || null,
        qualification: parsed.data.qualification?.trim() || null,
        registrationNumber: parsed.data.registrationNumber?.trim() || null,
        consultationRoom: parsed.data.consultationRoom?.trim() || null,
        consultationFee: parsed.data.consultationFee == null ? null : Number(parsed.data.consultationFee),
        yearsOfExperience: parsed.data.yearsOfExperience == null ? null : Number(parsed.data.yearsOfExperience),
        age: parsed.data.age == null ? null : Number(parsed.data.age),
        active: parsed.data.active ?? form.active,
        publicListingEnabled: parsed.data.publicListingEnabled ?? form.publicListingEnabled,
        slug: parsed.data.slug?.trim() || null,
      });
      setProfile(saved);
      setForm(toForm(saved));
      setInfo("Doctor profile saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save doctor profile");
    } finally {
      setSaving(false);
    }
  };

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
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Name" value={profile.doctorName || ""} disabled /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Email" value={profile.email || ""} disabled /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Mobile" value={form.mobile} disabled={formReadOnly} onChange={(e) => setForm((c) => c ? { ...c, mobile: e.target.value } : c)} inputProps={{ inputMode: "tel" }} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Specialization" value={form.specialization} disabled={formReadOnly} onChange={(e) => setForm((c) => c ? { ...c, specialization: e.target.value } : c)} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Qualification" value={form.qualification} disabled={formReadOnly || receptionistReadOnlyFields} onChange={(e) => setForm((c) => c ? { ...c, qualification: e.target.value } : c)} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Registration Number" value={form.registrationNumber} disabled={formReadOnly || receptionistReadOnlyFields} onChange={(e) => setForm((c) => c ? { ...c, registrationNumber: e.target.value } : c)} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Consultation Room/Location" value={form.consultationRoom} disabled={formReadOnly} onChange={(e) => setForm((c) => c ? { ...c, consultationRoom: e.target.value } : c)} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="number" label="Consultation Fee" value={form.consultationFee} disabled={formReadOnly} onChange={(e) => setForm((c) => c ? { ...c, consultationFee: e.target.value } : c)} inputProps={{ min: 0, step: "0.01" }} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="number" label="Years of Experience" value={form.yearsOfExperience} disabled={formReadOnly} onChange={(e) => setForm((c) => c ? { ...c, yearsOfExperience: e.target.value } : c)} inputProps={{ min: 0, step: 1 }} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="number" label="Age" value={form.age} disabled={formReadOnly} onChange={(e) => setForm((c) => c ? { ...c, age: e.target.value } : c)} inputProps={{ min: 0, max: 120, step: 1 }} /></Grid>
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
                label="Public slug"
                value={form.slug}
                disabled={formReadOnly || receptionistReadOnlyFields}
                onChange={(e) => setForm((c) => c ? { ...c, slug: e.target.value } : c)}
                helperText="Optional. Leave blank to auto-generate from doctor name."
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
