import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Grid, Stack, TextField, Typography } from "@mui/material";

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
    const consultationFee = form.consultationFee.trim() === "" ? null : Number(form.consultationFee);
    const yearsOfExperience = form.yearsOfExperience.trim() === "" ? null : Number(form.yearsOfExperience);
    const age = form.age.trim() === "" ? null : Number(form.age);
    if (consultationFee != null && (!Number.isFinite(consultationFee) || consultationFee < 0)) {
      setError("Consultation fee must be zero or greater.");
      return;
    }
    if (yearsOfExperience != null && (!Number.isInteger(yearsOfExperience) || yearsOfExperience < 0)) {
      setError("Years of experience must be zero or greater.");
      return;
    }
    if (age != null && (!Number.isInteger(age) || age < 0 || age > 120)) {
      setError("Age must be between 0 and 120.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const saved = await updateDoctorProfile(auth.accessToken, auth.tenantId, profile.doctorUserId, {
        mobile: form.mobile.trim() || null,
        specialization: form.specialization.trim() || null,
        qualification: form.qualification.trim() || null,
        registrationNumber: form.registrationNumber.trim() || null,
        consultationRoom: form.consultationRoom.trim() || null,
        consultationFee,
        yearsOfExperience,
        age,
        active: form.active,
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
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Mobile" value={form.mobile} disabled={formReadOnly} onChange={(e) => setForm((c) => c ? { ...c, mobile: e.target.value } : c)} /></Grid>
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
            <Grid size={{ xs: 12 }}>
              <Chip label={form.active ? "Active" : "Inactive"} color={form.active ? "success" : "default"} />
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    </Stack>
  );
}
