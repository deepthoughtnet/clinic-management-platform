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
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  Switch,
  Tab,
  Tabs,
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
  assignTenantUserRole,
  createTenantUser,
  getClinicRoles,
  getClinicUsers,
  resetTenantUserPassword,
  updateTenantUser,
  type ClinicRole,
  type ClinicUser,
} from "../../api/clinicApi";

const ASSIGNABLE_ROLES = ["CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "BILLING_USER", "AUDITOR", "SERVICE_AGENT", "LAB_ASSISTANT", "PHARMACIST"] as const;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

type CreateForm = {
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  tempPassword: string;
  active: boolean;
};

const EMPTY_CREATE_FORM: CreateForm = {
  firstName: "",
  lastName: "",
  email: "",
  role: "DOCTOR",
  tempPassword: "",
  active: true,
};

function roleLabel(role: string) {
  if (role === "SERVICE_AGENT") return "Service Agent (non-human)";
  return role
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function isInvalidSelectedClinic(tenant: { id: string; code: string; name: string } | null): boolean {
  if (!tenant || !UUID_RE.test(tenant.id)) return true;
  const values = [tenant.id, tenant.code, tenant.name].map((value) => value.toUpperCase());
  return values.some((value) => value.startsWith("DEFAULT-ROLES") || value.includes("DEFAULT-ROLES-"));
}

export default function UsersRolesPage() {
  const auth = useAuth();
  const canManageUsers = auth.hasPermission("user.manage") || auth.hasPermission("tenant.users.manage");
  const canAssignRoles = auth.hasPermission("tenant.users.role.assign") || canManageUsers;
  const canResetPasswords = auth.hasPermission("tenant.users.reset.password") || canManageUsers;
  const invalidSelectedClinic = isInvalidSelectedClinic(auth.selectedTenant);

  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [roles, setRoles] = React.useState<ClinicRole[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [savingUserId, setSavingUserId] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [toast, setToast] = React.useState<string | null>(null);
  const [activeTab, setActiveTab] = React.useState<"users" | "roles">("users");
  const [openCreate, setOpenCreate] = React.useState(false);
  const [createSubmitting, setCreateSubmitting] = React.useState(false);
  const [createForm, setCreateForm] = React.useState<CreateForm>(EMPTY_CREATE_FORM);

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId || invalidSelectedClinic) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const [userRows, roleRows] = await Promise.all([
        getClinicUsers(auth.accessToken, auth.tenantId),
        getClinicRoles(auth.accessToken, auth.tenantId),
      ]);
      setUsers(userRows);
      setRoles(roleRows);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load users and roles");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, invalidSelectedClinic]);

  React.useEffect(() => {
    void load();
  }, [load]);

  if (!auth.tenantId || invalidSelectedClinic) {
    return (
      <Stack spacing={2}>
        <Alert severity="warning">Invalid selected clinic. Please reselect tenant.</Alert>
        <Button variant="outlined" onClick={() => auth.selectTenant(null)}>Clear selected tenant</Button>
      </Stack>
    );
  }

  const createUser = async () => {
    if (!auth.accessToken || !auth.tenantId || invalidSelectedClinic) {
      setError("Invalid selected clinic. Please reselect tenant.");
      return;
    }
    if (!createForm.email.trim()) {
      setError("Email is required.");
      return;
    }
    if (!ASSIGNABLE_ROLES.includes(createForm.role as (typeof ASSIGNABLE_ROLES)[number])) {
      setError("Unable to create user. Please verify role and tenant selection.");
      return;
    }
    setCreateSubmitting(true);
    setError(null);
    try {
      await createTenantUser(auth.accessToken, auth.tenantId, {
        email: createForm.email.trim().toLowerCase(),
        firstName: createForm.firstName.trim() || null,
        lastName: createForm.lastName.trim() || null,
        role: createForm.role,
        temporaryPassword: createForm.tempPassword.trim() || null,
        active: createForm.active,
      });
      setToast("User created successfully.");
      setOpenCreate(false);
      setCreateForm(EMPTY_CREATE_FORM);
      await load();
    } catch (err) {
      console.error("Tenant user creation failed", err);
      setError("Unable to create user. Please verify role and tenant selection.");
    } finally {
      setCreateSubmitting(false);
    }
  };

  const setUserActive = async (user: ClinicUser, active: boolean) => {
    if (!auth.accessToken || !auth.tenantId || !canManageUsers) return;
    setSavingUserId(user.appUserId);
    try {
      await updateTenantUser(auth.accessToken, auth.tenantId, user.appUserId, { active });
      setToast(`User ${active ? "activated" : "deactivated"}.`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update user status");
    } finally {
      setSavingUserId(null);
    }
  };

  const updateUserRole = async (user: ClinicUser, role: string) => {
    if (!auth.accessToken || !auth.tenantId || !canAssignRoles) return;
    setSavingUserId(user.appUserId);
    try {
      await assignTenantUserRole(auth.accessToken, auth.tenantId, user.appUserId, role);
      setToast("Role updated.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update role");
    } finally {
      setSavingUserId(null);
    }
  };

  const resetPassword = async (user: ClinicUser) => {
    if (!auth.accessToken || !auth.tenantId || !canResetPasswords) return;
    const generated = `Clinic@${Math.random().toString(36).slice(2, 8)}!`;
    setSavingUserId(user.appUserId);
    try {
      await resetTenantUserPassword(auth.accessToken, auth.tenantId, user.appUserId, generated, true);
      setToast(`Temporary password set for ${user.email || user.appUserId}: ${generated}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reset password");
    } finally {
      setSavingUserId(null);
    }
  };

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
          Users & Roles
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Manage tenant users and role permissions for the selected clinic.
        </Typography>
      </Box>

      {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
      {!canManageUsers ? <Alert severity="info">You can view users, but user management is restricted.</Alert> : null}

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 2, flexWrap: "wrap" }}>
              <Tabs value={activeTab} onChange={(_, value) => setActiveTab(value)}>
                <Tab value="users" label="Users" />
                <Tab value="roles" label="Roles & Permissions" />
              </Tabs>
              {activeTab === "users" && canManageUsers ? (
                <Button variant="contained" onClick={() => setOpenCreate(true)}>Create User</Button>
              ) : null}
            </Box>

            {loading ? (
              <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                <CircularProgress />
              </Box>
            ) : activeTab === "users" ? (
              users.length === 0 ? (
                <Alert severity="info">No clinic users were found for this tenant.</Alert>
              ) : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Email</TableCell>
                      <TableCell>Role</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Created</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {users.map((user) => {
                      const active = (user.membershipStatus || "ACTIVE").toUpperCase() === "ACTIVE";
                      return (
                        <TableRow key={user.appUserId}>
                          <TableCell sx={{ fontWeight: 700 }}>{user.displayName || user.email || user.appUserId}</TableCell>
                          <TableCell>{user.email || "-"}</TableCell>
                          <TableCell>
                            {canAssignRoles ? (
                              <FormControl size="small" sx={{ minWidth: 190 }}>
                                <Select
                                  value={user.membershipRole || "VIEWER"}
                                  onChange={(e) => void updateUserRole(user, String(e.target.value))}
                                  disabled={savingUserId === user.appUserId}
                                >
                                  {ASSIGNABLE_ROLES.map((role) => (
                                    <MenuItem key={role} value={role}>{roleLabel(role)}</MenuItem>
                                  ))}
                                </Select>
                              </FormControl>
                            ) : (
                              <Chip size="small" label={user.membershipRole || "Unassigned"} />
                            )}
                          </TableCell>
                          <TableCell>
                            <Chip size="small" color={active ? "success" : "default"} label={active ? "ACTIVE" : "DISABLED"} />
                          </TableCell>
                          <TableCell>{new Date(user.createdAt).toLocaleString()}</TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end" alignItems="center">
                              {canManageUsers ? (
                                <Switch
                                  size="small"
                                  checked={active}
                                  disabled={savingUserId === user.appUserId}
                                  onChange={(e) => void setUserActive(user, e.target.checked)}
                                />
                              ) : null}
                              {canResetPasswords ? (
                                <Button size="small" variant="outlined" disabled={savingUserId === user.appUserId} onClick={() => void resetPassword(user)}>
                                  Reset Password
                                </Button>
                              ) : null}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              )
            ) : (
              <Stack spacing={1.5}>
                {roles.map((role) => (
                  <Card key={role.role} variant="outlined">
                    <CardContent>
                      <Stack spacing={1.25}>
                        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1.5 }}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{role.displayName}</Typography>
                          <Chip size="small" label={role.role} />
                        </Box>
                        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
                          {role.permissions.map((permission) => (
                            <Chip key={permission} size="small" variant="outlined" label={permission} />
                          ))}
                        </Box>
                      </Stack>
                    </CardContent>
                  </Card>
                ))}
                {roles.length === 0 ? <Alert severity="info">No role definitions were returned.</Alert> : null}
              </Stack>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Dialog open={openCreate} onClose={() => setOpenCreate(false)} fullWidth maxWidth="sm">
        <DialogTitle>Create Tenant User</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 0.5 }}>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.25}>
              <TextField label="First Name" value={createForm.firstName} onChange={(e) => setCreateForm((s) => ({ ...s, firstName: e.target.value }))} fullWidth />
              <TextField label="Last Name" value={createForm.lastName} onChange={(e) => setCreateForm((s) => ({ ...s, lastName: e.target.value }))} fullWidth />
            </Stack>
            <TextField label="Email" type="email" value={createForm.email} onChange={(e) => setCreateForm((s) => ({ ...s, email: e.target.value }))} required fullWidth />
            <FormControl fullWidth>
              <InputLabel id="create-role-label">Role</InputLabel>
              <Select
                labelId="create-role-label"
                label="Role"
                value={createForm.role}
                onChange={(e) => setCreateForm((s) => ({ ...s, role: String(e.target.value) }))}
              >
                {ASSIGNABLE_ROLES.map((role) => (
                  <MenuItem key={role} value={role}>{roleLabel(role)}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Temporary Password (optional)"
              value={createForm.tempPassword}
              onChange={(e) => setCreateForm((s) => ({ ...s, tempPassword: e.target.value }))}
              helperText="If blank, user can use IdP-managed flow."
              fullWidth
            />
            <Stack direction="row" alignItems="center" justifyContent="space-between">
              <Typography variant="body2">Active</Typography>
              <Switch checked={createForm.active} onChange={(e) => setCreateForm((s) => ({ ...s, active: e.target.checked }))} />
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenCreate(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void createUser()} disabled={createSubmitting || !createForm.email.trim()}>
            {createSubmitting ? "Creating..." : "Create User"}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={Boolean(toast)} autoHideDuration={3000} onClose={() => setToast(null)}>
        <Alert severity="success" onClose={() => setToast(null)}>{toast}</Alert>
      </Snackbar>
    </Stack>
  );
}
