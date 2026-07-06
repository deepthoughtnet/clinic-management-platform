import * as React from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  Stack,
  Typography,
} from "@mui/material";

import type { AuthContextValue } from "../../auth/AuthContext";
import {
  completeTenantOnboarding,
  getClinicProfile,
  getClinicUsers,
  getTenantOnboardingStatus,
  skipTenantOnboarding,
  type ClinicProfile,
  type ClinicUser,
  type TenantOnboardingStatus,
} from "../../api/clinicApi";

type ChecklistItem = {
  label: string;
  done: boolean;
  helper: string;
};

type SetupAction = {
  title: string;
  description: string;
  path: string;
  tone: "primary" | "secondary" | "info" | "success";
};

export type TenantOnboardingWizardDialogProps = {
  open: boolean;
  auth: Pick<AuthContextValue, "accessToken" | "tenantId" | "tenantName" | "tenantRole" | "enabledTenantModules">;
  onClose: () => void;
  onCompleted?: (status: TenantOnboardingStatus) => void;
};

function hasModule(auth: TenantOnboardingWizardDialogProps["auth"], moduleCode: string) {
  return Boolean(auth.enabledTenantModules && auth.enabledTenantModules[moduleCode]);
}

function isRole(user: ClinicUser, role: string) {
  return (user.membershipRole || "").toUpperCase() === role;
}

function checklistFor(profile: ClinicProfile | null, users: ClinicUser[], onboarding: TenantOnboardingStatus | null, auth: TenantOnboardingWizardDialogProps["auth"]): ChecklistItem[] {
  const doctorCount = users.filter((user) => isRole(user, "DOCTOR")).length;
  const receptionistCount = users.filter((user) => isRole(user, "RECEPTIONIST")).length;
  return [
    {
      label: "Clinic basics configured",
      done: Boolean(profile && profile.clinicName && profile.addressLine1 && profile.phone),
      helper: profile ? profile.clinicName : "Add the clinic profile and contact details.",
    },
    {
      label: "Doctors added",
      done: doctorCount > 0,
      helper: doctorCount > 0 ? `${doctorCount} doctor${doctorCount === 1 ? "" : "s"} configured.` : "Create at least one doctor profile.",
    },
    {
      label: "Reception users added",
      done: receptionistCount > 0,
      helper: receptionistCount > 0 ? `${receptionistCount} receptionist${receptionistCount === 1 ? "" : "s"} configured.` : "Add at least one receptionist user.",
    },
    {
      label: "Billing and services ready",
      done: Boolean(profile && profile.registrationNumber),
      helper: profile?.registrationNumber ? `Registration number ${profile.registrationNumber} saved.` : "Review fees and receipt settings before go-live.",
    },
    {
      label: "Pharmacy enabled",
      done: hasModule(auth, "INVENTORY") || hasModule(auth, "PRESCRIPTION"),
      helper: hasModule(auth, "INVENTORY") ? "Pharmacy module is enabled." : "Enable pharmacy if this clinic dispenses medicines.",
    },
    {
      label: "Laboratory enabled",
      done: hasModule(auth, "LABORATORY"),
      helper: hasModule(auth, "LABORATORY") ? "Laboratory module is enabled." : "Enable laboratory if this clinic runs lab workflows.",
    },
    {
      label: "Onboarding completed",
      done: Boolean(onboarding?.completed),
      helper: onboarding?.completed ? "Wizard completed." : "Mark setup complete when ready.",
    },
  ];
}

function setupActions(): SetupAction[] {
  return [
    { title: "Add Doctor", description: "Create the first doctor user and profile.", path: "/settings/users-roles", tone: "primary" },
    { title: "Add Receptionist", description: "Create front-desk users for appointments and queues.", path: "/settings/users-roles", tone: "secondary" },
    { title: "Configure Availability", description: "Set working days, slots, and breaks.", path: "/doctors/availability", tone: "info" },
    { title: "Add Services / Fees", description: "Review clinic profile and billing setup.", path: "/settings/clinic-profile", tone: "info" },
    { title: "Import Medicines", description: "Open the medicine master and load inventory items.", path: "/pharmacy/medicines", tone: "success" },
    { title: "Import Lab Tests", description: "Open the lab workspace and load the test catalog.", path: "/lab", tone: "success" },
    { title: "Book First Appointment", description: "Create the first outpatient appointment.", path: "/appointments", tone: "primary" },
  ];
}

export default function TenantOnboardingWizardDialog({ open, auth, onClose, onCompleted }: TenantOnboardingWizardDialogProps) {
  const navigate = useNavigate();
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState<"complete" | "skip" | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [profile, setProfile] = React.useState<ClinicProfile | null>(null);
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [status, setStatus] = React.useState<TenantOnboardingStatus | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!open || !auth.accessToken || !auth.tenantId) return;
      setLoading(true);
      setError(null);
      try {
        const [onboarding, clinicProfile, clinicUsers] = await Promise.all([
          getTenantOnboardingStatus(auth.accessToken, auth.tenantId),
          getClinicProfile(auth.accessToken, auth.tenantId).catch(() => null),
          getClinicUsers(auth.accessToken, auth.tenantId).catch(() => []),
        ]);
        if (cancelled) return;
        setStatus(onboarding);
        setProfile(clinicProfile);
        setUsers(clinicUsers);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load setup status");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, open]);

  const items = React.useMemo(() => checklistFor(profile, users, status, auth), [auth, profile, status, users]);
  const actions = React.useMemo(() => setupActions(), []);

  const handleComplete = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving("complete");
    setError(null);
    try {
      const next = await completeTenantOnboarding(auth.accessToken, auth.tenantId);
      setStatus(next);
      onCompleted?.(next);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to complete setup");
    } finally {
      setSaving(null);
    }
  };

  const handleSkip = async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving("skip");
    setError(null);
    try {
      const next = await skipTenantOnboarding(auth.accessToken, auth.tenantId);
      setStatus(next);
      onCompleted?.(next);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to skip setup");
    } finally {
      setSaving(null);
    }
  };

  return (
    <Dialog open={open} onClose={() => {
      if (!saving) {
        onClose();
      }
    }} fullWidth maxWidth="md">
      <DialogTitle sx={{ pb: 1 }}>
        <Stack spacing={0.5}>
          <Typography variant="h6" sx={{ fontWeight: 900 }}>Tenant setup wizard</Typography>
          <Typography variant="body2" color="text.secondary">
            Complete the clinic basics, users, and core modules before opening operations.
          </Typography>
        </Stack>
      </DialogTitle>
      <DialogContent dividers sx={{ bgcolor: "background.default" }}>
        <Stack spacing={2}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Box>
            <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Setup checklist</Typography>
            <Stack spacing={1}>
              {items.map((item) => (
                <Card key={item.label} variant="outlined" sx={{ borderColor: item.done ? "success.main" : "divider" }}>
                  <CardContent sx={{ py: 1.1, "&:last-child": { pb: 1.1 } }}>
                    <Stack direction="row" spacing={1.5} alignItems="center" justifyContent="space-between">
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 800 }}>{item.label}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.helper}</Typography>
                      </Box>
                      <Chip size="small" color={item.done ? "success" : "default"} label={item.done ? "Done" : "Pending"} />
                    </Stack>
                  </CardContent>
                </Card>
              ))}
            </Stack>
          </Box>

          <Divider />

          <Box>
            <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Quick actions</Typography>
            <Grid container spacing={1.25}>
              {actions.map((action) => (
                <Grid key={action.title} size={{ xs: 12, sm: 6 }}>
                  <Card
                    variant="outlined"
                    sx={{
                      minHeight: 116,
                      display: "flex",
                      flexDirection: "column",
                      justifyContent: "space-between",
                      borderColor: action.tone === "primary" ? "primary.main" : action.tone === "success" ? "success.main" : action.tone === "info" ? "info.main" : "divider",
                    }}
                  >
                    <CardContent sx={{ pb: 0.5 }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{action.title}</Typography>
                      <Typography variant="caption" color="text.secondary">{action.description}</Typography>
                    </CardContent>
                    <Box sx={{ px: 1.5, pb: 1.5 }}>
                      <Button
                        variant={action.tone === "secondary" ? "outlined" : "contained"}
                        size="small"
                        onClick={() => {
                          navigate(action.path);
                          onClose();
                        }}
                        fullWidth
                      >
                        Open
                      </Button>
                    </Box>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Box>
          {loading ? <Alert severity="info">Loading setup progress...</Alert> : null}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={Boolean(saving)}>Close</Button>
        <Button onClick={() => void handleSkip()} disabled={Boolean(saving)} color="inherit">
          {saving === "skip" ? "Skipping..." : "Skip for now"}
        </Button>
        <Button onClick={() => void handleComplete()} variant="contained" disabled={Boolean(saving)}>
          {saving === "complete" ? "Completing..." : "Mark setup complete"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
