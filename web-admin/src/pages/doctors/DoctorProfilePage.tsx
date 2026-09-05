import * as React from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Grid, Stack, TextField, Typography } from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { getDoctorProfile, type DoctorProfile } from "../../api/clinicApi";
import DoctorAvatar from "../../components/doctor/DoctorAvatar";
import { resolveTenantLandingPage } from "../../modules/moduleRegistry";

function parseListValue(value: string | null | undefined): string[] {
  return (value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function calculateAge(dateOfBirth: string | null | undefined): number | null {
  const trimmed = dateOfBirth?.trim();
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

function displayValue(value: string | number | null | undefined, fallback = "Not recorded") {
  if (value == null) {
    return fallback;
  }
  const text = String(value).trim();
  return text || fallback;
}

function canEditProfile(auth: ReturnType<typeof useAuth>, profile: DoctorProfile) {
  const role = (auth.tenantRole || "").toUpperCase();
  const isDoctor = role === "DOCTOR";
  const isReceptionist = role === "RECEPTIONIST";
  const isAdmin = role === "CLINIC_ADMIN";
  return auth.hasPermission("appointment.manage") && (isDoctor || isReceptionist || isAdmin) && (!isDoctor || profile.doctorUserId === auth.appUserId);
}

type DoctorProfileReturnTarget = {
  pathname: string;
  search?: string;
  hash?: string;
  state?: unknown;
};

type DoctorProfileNavigationState = {
  returnTo?: DoctorProfileReturnTarget | null;
};

function isSafeInternalPath(pathname: string | undefined): pathname is string {
  const safePath = pathname?.trim();
  if (!safePath) {
    return false;
  }
  return safePath.startsWith("/") && !safePath.startsWith("//") && !safePath.includes("://");
}

function resolveBackTarget(state: unknown): DoctorProfileReturnTarget | null {
  const returnTo = (state as DoctorProfileNavigationState | null | undefined)?.returnTo;
  const pathname = returnTo?.pathname;
  if (!returnTo || !isSafeInternalPath(pathname)) {
    return null;
  }
  return {
    pathname,
    search: returnTo.search || "",
    hash: returnTo.hash || "",
    state: returnTo.state,
  };
}

export default function DoctorProfilePage() {
  const { id = "" } = useParams();
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [loading, setLoading] = React.useState(true);
  const [profile, setProfile] = React.useState<DoctorProfile | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const backTarget = React.useMemo(() => resolveBackTarget(location.state), [location.state]);

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
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load doctor profile");
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
  }, [auth.accessToken, auth.tenantId, id]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  if (loading) {
    return (
      <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!profile) {
    return <Alert severity="error">{error || "Doctor profile not found"}</Alert>;
  }

  const editable = canEditProfile(auth, profile);
  const specializations = profile.specializations?.length ? profile.specializations : (profile.specialization ? [profile.specialization] : []);
  const qualifications = parseListValue(profile.qualification);
  const opdFee = profile.opdFee ?? profile.consultationFee;
  const age = calculateAge(profile.dateOfBirth || null);

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>{profile.doctorName || profile.doctorUserId}</Typography>
          <Typography variant="body2" color="text.secondary">{profile.email || "No email"} • {profile.membershipRole || "DOCTOR"}</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            onClick={() => {
              if (backTarget) {
                navigate(`${backTarget.pathname}${backTarget.search || ""}${backTarget.hash || ""}`, { replace: true, state: backTarget.state });
                return;
              }
              navigate(resolveTenantLandingPage(auth), { replace: true });
            }}
          >
            Back
          </Button>
          {editable ? (
            <Button variant="contained" onClick={() => navigate(`/doctors/${profile.doctorUserId}`)}>
              Edit Profile
            </Button>
          ) : null}
        </Stack>
      </Box>

      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12 }}>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems={{ xs: "flex-start", sm: "center" }}>
                <DoctorAvatar
                  name={profile.doctorName || profile.email || "Doctor"}
                  photoUrl={profile.photoUrl || null}
                  alt={profile.doctorName || "Doctor profile"}
                  sx={{ width: 96, height: 96, fontWeight: 800 }}
                />
                <Stack spacing={0.5}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    {profile.doctorName || profile.doctorUserId}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">{displayValue(profile.email)}</Typography>
                  <Typography variant="body2" color="text.secondary">{editable ? "Edit Profile is available" : "Read-only profile"}</Typography>
                </Stack>
              </Stack>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Name" value={profile.doctorName || ""} disabled /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Email" value={profile.email || ""} disabled /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Mobile" value={profile.mobile || ""} disabled /></Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Specialization(s)"
                value={specializations.join(", ") || ""}
                disabled
                helperText={specializations.length ? "Loaded from doctor profile." : "Not recorded."}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Qualification(s)"
                value={qualifications.join(", ") || ""}
                disabled
                helperText={qualifications.length ? "Loaded from doctor profile." : "Not recorded."}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Registration Number" value={profile.registrationNumber || ""} disabled /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Consultation Room/Location" value={profile.consultationRoom || ""} disabled /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="OPD Fee" value={opdFee == null ? "" : String(opdFee)} disabled /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Follow-up Fee" value={profile.followUpFee == null ? "" : String(profile.followUpFee)} disabled /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Emergency Fee" value={profile.emergencyFee == null ? "" : String(profile.emergencyFee)} disabled /></Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                label="Years of Experience"
                value={profile.yearsOfExperience == null ? "" : String(profile.yearsOfExperience)}
                disabled
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                label="Date of Birth"
                value={profile.dateOfBirth || ""}
                disabled
                helperText={age == null ? "Not recorded." : `${age} years`}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label={profile.publicListingEnabled ? "Public listing enabled" : "Public listing disabled"} color={profile.publicListingEnabled ? "success" : "default"} />
                <Chip label={profile.active ? "Active" : "Inactive"} color={profile.active ? "success" : "default"} />
              </Stack>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                <TextField fullWidth label="Availability/Calendar" value="Open in Appointments" disabled />
                <Button variant="outlined" onClick={() => navigate(`/appointments?doctorUserId=${profile.doctorUserId}`)}>Open</Button>
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    </Stack>
  );
}
