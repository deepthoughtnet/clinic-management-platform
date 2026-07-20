import * as React from "react";
import { useNavigate } from "react-router-dom";
import { firstZodError, mapZodErrors, userCreateSchema } from "@deepthoughtnet/form-validation-kit";
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
import RequiredLabel from "../../components/forms/RequiredLabel";
import {
  createTenantUser,
  getClinicRoles,
  getClinicUsers,
  resetTenantUserPassword,
  updateTenantUserProfile,
  type ClinicRole,
  type ClinicUser,
} from "../../api/clinicApi";

const ASSIGNABLE_ROLES = [
  "CLINIC_ADMIN",
  "ENGAGE_MANAGER",
  "ENGAGE_EXECUTIVE",
  "DOCTOR",
  "RECEPTIONIST",
  "BILLING_USER",
  "AUDITOR",
  "SERVICE_AGENT",
  "LAB_TECHNICIAN",
  "LAB_ASSISTANT",
  "LAB_APPROVER",
  "LAB_FRONT_DESK",
  "PHARMACIST",
  "PHARMACY_INVENTORY_MANAGER",
  "PHARMACY_POS_USER",
] as const;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

type CreateForm = {
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  role: string;
  tempPassword: string;
  employeeCode: string;
  mobile: string;
  department: string;
  active: boolean;
};

type EditForm = {
  displayName: string;
  employeeCode: string;
  mobile: string;
  department: string;
  role: string;
  active: boolean;
};

const EMPTY_CREATE_FORM: CreateForm = {
  firstName: "",
  lastName: "",
  username: "",
  email: "",
  role: "DOCTOR",
  tempPassword: "",
  employeeCode: "",
  mobile: "",
  department: "",
  active: true,
};

const EMPTY_EDIT_FORM: EditForm = {
  displayName: "",
  employeeCode: "",
  mobile: "",
  department: "",
  role: "",
  active: true,
};

const DEPARTMENT_OPTIONS = [
  "Reception",
  "Doctor",
  "Billing",
  "Pharmacy",
  "Laboratory",
  "Administration",
  "Inventory",
  "Management",
  "Other",
] as const;

function roleLabel(role: string) {
  if (role === "SERVICE_AGENT") return "Service Agent (non-human)";
  return role
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function permissionLabel(permission: string) {
  return permission
    .replace(/\./g, " ")
    .replace(/_/g, " ")
    .split(" ")
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function permissionModule(permission: string) {
  const normalized = permission.toLowerCase();
  if (normalized.startsWith("appointment.")) return "Appointments";
  if (normalized.startsWith("queue.")) return "Queue";
  if (normalized.startsWith("patient.")) return "Patients";
  if (normalized.startsWith("consultation.")) return "Consultations";
  if (normalized.startsWith("prescription.")) return "Prescriptions";
  if (normalized.startsWith("billing.") || normalized.startsWith("payment.")) return "Billing";
  if (normalized.startsWith("inventory.") || normalized.startsWith("medicine.")) return "Pharmacy";
  if (normalized.startsWith("lab.")) return "Laboratory";
  if (normalized.startsWith("notification.")) return "Notifications";
  if (normalized.startsWith("carepilot.")) return "CarePilot";
  if (normalized.startsWith("engage.")) return "Engage";
  if (normalized.startsWith("ai_") || normalized.startsWith("ai.")) return "AI";
  if (normalized.startsWith("tenant.users.") || normalized.startsWith("user.")) return "Users";
  if (normalized.startsWith("clinic.")) return "Clinic";
  if (normalized.startsWith("report.")) return "Reports";
  if (normalized.startsWith("audit.")) return "Audit";
  return "Other";
}

function formatLocalDateTime(value: string | null | undefined) {
  if (!value) return "Never logged in";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Never logged in";
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function groupPermissions(permissions: string[]) {
  const groups = new Map<string, string[]>();
  for (const permission of permissions) {
    const module = permissionModule(permission);
    const bucket = groups.get(module) || [];
    if (!bucket.includes(permission)) {
      bucket.push(permission);
    }
    groups.set(module, bucket);
  }
  return Array.from(groups.entries()).map(([module, values]) => ({
    module,
    permissions: values.sort((left, right) => permissionLabel(left).localeCompare(permissionLabel(right))),
  })).sort((left, right) => left.module.localeCompare(right.module));
}

function isInvalidSelectedClinic(tenant: { id: string; code: string; name: string } | null): boolean {
  if (!tenant || !UUID_RE.test(tenant.id)) return true;
  const values = [tenant.id, tenant.code, tenant.name].map((value) => value.toUpperCase());
  return values.some((value) => value.startsWith("DEFAULT-ROLES") || value.includes("DEFAULT-ROLES-"));
}

export default function UsersRolesPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const canManageUsers = auth.hasPermission("user.manage") || auth.hasPermission("tenant.users.manage");
  const canAssignRoles = auth.hasPermission("tenant.users.role.assign") || canManageUsers;
  const canResetPasswords = auth.hasPermission("tenant.users.reset.password") || canManageUsers;
  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");
  const isTenantAdminActor = ["CLINIC_ADMIN", "TENANT_ADMIN", "ADMIN"].includes(auth.tenantRole || "")
    || auth.rolesUpper.includes("CLINIC_ADMIN")
    || auth.rolesUpper.includes("TENANT_ADMIN")
    || auth.rolesUpper.includes("ADMIN");
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
  const [createFieldErrors, setCreateFieldErrors] = React.useState<Record<string, string>>({});
  const [openEdit, setOpenEdit] = React.useState(false);
  const [editingUser, setEditingUser] = React.useState<ClinicUser | null>(null);
  const [editSubmitting, setEditSubmitting] = React.useState(false);
  const [editForm, setEditForm] = React.useState<EditForm>(EMPTY_EDIT_FORM);
  const [editFieldErrors, setEditFieldErrors] = React.useState<Record<string, string>>({});

  const editableRoleOptions = React.useMemo(() => {
    const allowedRoles = new Set<string>(isPlatformAdmin ? [...ASSIGNABLE_ROLES, "TENANT_ADMIN", "ADMIN"] : [...ASSIGNABLE_ROLES]);
    return roles.filter((role) => allowedRoles.has(role.role));
  }, [isPlatformAdmin, roles]);

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

  const canEditUser = React.useCallback((user: ClinicUser) => {
    if (!canManageUsers) return false;
    if (isPlatformAdmin) return true;
    if (!isTenantAdminActor) return false;
    return user.appUserId !== auth.appUserId;
  }, [auth.appUserId, canManageUsers, isPlatformAdmin, isTenantAdminActor]);

  const canEditRoleForUser = React.useCallback((user: ClinicUser | null) => {
    if (!user || !canAssignRoles) return false;
    if (isPlatformAdmin) return true;
    const currentRole = (user.membershipRole || "").toUpperCase();
    return currentRole === "" || ASSIGNABLE_ROLES.includes(currentRole as (typeof ASSIGNABLE_ROLES)[number]);
  }, [canAssignRoles, isPlatformAdmin]);

  const openEditDialog = (user: ClinicUser) => {
    const fallbackRole = user.membershipRole || editableRoleOptions[0]?.role || "DOCTOR";
    setEditingUser(user);
    setEditForm({
      displayName: user.displayName || "",
      employeeCode: user.employeeCode || "",
      mobile: user.mobile || "",
      department: user.department || "",
      role: fallbackRole,
      active: (user.membershipStatus || "ACTIVE").toUpperCase() === "ACTIVE",
    });
    setEditFieldErrors({});
    setOpenEdit(true);
  };

  const closeEditDialog = () => {
    setOpenEdit(false);
    setEditingUser(null);
    setEditForm(EMPTY_EDIT_FORM);
    setEditFieldErrors({});
  };

  const validateEditForm = () => {
    const fieldErrors: Record<string, string> = {};
    if (!editForm.displayName.trim()) {
      fieldErrors.displayName = "Name is required.";
    }
    if (editForm.mobile.trim() && !/^[0-9]{10}$/.test(editForm.mobile.trim())) {
      fieldErrors.mobile = "Enter a valid 10-digit mobile number.";
    }
    if (editForm.employeeCode.trim().length > 64) {
      fieldErrors.employeeCode = "Employee code must be 64 characters or fewer.";
    }
    if (editForm.department.trim().length > 128) {
      fieldErrors.department = "Department must be 128 characters or fewer.";
    }
    if (canEditRoleForUser(editingUser) && !editForm.role.trim()) {
      fieldErrors.role = "Role is required.";
    }
    return fieldErrors;
  };

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
    const parsed = userCreateSchema.safeParse({
      firstName: createForm.firstName,
      lastName: createForm.lastName,
      username: createForm.username,
      email: createForm.email,
      role: createForm.role,
      tempPassword: createForm.tempPassword,
      active: createForm.active,
      employeeCode: createForm.employeeCode,
      mobile: createForm.mobile,
      department: createForm.department,
    });
    if (!parsed.success) {
      const fieldErrors = mapZodErrors(parsed.error);
      setCreateFieldErrors(fieldErrors);
      setError(firstZodError(parsed.error) || "Unable to create user. Please verify role and tenant selection.");
      const firstInvalidField = Object.keys(fieldErrors)[0];
      if (firstInvalidField) {
        window.setTimeout(() => document.getElementById(`create-user-${firstInvalidField}`)?.focus(), 0);
      }
      return;
    }
    setCreateFieldErrors({});
    setCreateSubmitting(true);
    setError(null);
    try {
      if (!ASSIGNABLE_ROLES.includes(parsed.data.role as (typeof ASSIGNABLE_ROLES)[number])) {
        setError("Unable to create user. Please verify role and tenant selection.");
        return;
      }
      await createTenantUser(auth.accessToken, auth.tenantId, {
        email: parsed.data.email.trim().toLowerCase(),
        username: parsed.data.username?.trim() || null,
        firstName: parsed.data.firstName.trim() || null,
        lastName: parsed.data.lastName?.trim() || null,
        role: parsed.data.role,
        temporaryPassword: parsed.data.tempPassword?.trim() || null,
        employeeCode: parsed.data.employeeCode?.trim() || null,
        mobile: parsed.data.mobile?.trim() || null,
        department: parsed.data.department?.trim() || null,
        active: parsed.data.active ?? true,
      });
      setToast("User created successfully.");
      setOpenCreate(false);
      setCreateForm(EMPTY_CREATE_FORM);
      setCreateFieldErrors({});
      await load();
    } catch (err) {
      console.error("Tenant user creation failed", err);
      const message = err instanceof Error ? err.message : "Unable to create user. Please verify role and tenant selection.";
      const fieldErrors: Record<string, string> = {};
      if (message.toLowerCase().includes("username already exists")) {
        fieldErrors.username = "Username already exists for this clinic.";
      }
      if (message.toLowerCase().includes("employee code already exists")) {
        fieldErrors.employeeCode = "Employee code already exists for this clinic.";
      }
      if (message.toLowerCase().includes("mobile")) {
        fieldErrors.mobile = message;
      }
      if (Object.keys(fieldErrors).length > 0) {
        setCreateFieldErrors((current) => ({ ...current, ...fieldErrors }));
      }
      setError(message);
    } finally {
      setCreateSubmitting(false);
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

  const saveUserEdits = async () => {
    if (!auth.accessToken || !auth.tenantId || !editingUser || !canEditUser(editingUser)) return;
    const fieldErrors = validateEditForm();
    if (Object.keys(fieldErrors).length > 0) {
      setEditFieldErrors(fieldErrors);
      setError(fieldErrors[Object.keys(fieldErrors)[0]] || "Please correct the highlighted fields.");
      const firstInvalidField = Object.keys(fieldErrors)[0];
      if (firstInvalidField) {
        window.setTimeout(() => document.getElementById(`edit-user-${firstInvalidField}`)?.focus(), 0);
      }
      return;
    }

    setEditFieldErrors({});
    setEditSubmitting(true);
    setSavingUserId(editingUser.appUserId);
    setError(null);
    try {
      const updated = await updateTenantUserProfile(auth.accessToken, auth.tenantId, editingUser.appUserId, {
        displayName: editForm.displayName.trim(),
        employeeCode: editForm.employeeCode.trim() || null,
        mobile: editForm.mobile.trim() || null,
        department: editForm.department.trim() || null,
        role: canEditRoleForUser(editingUser) ? editForm.role.trim() : null,
        active: editForm.active,
      });
      setUsers((current) => current.map((user) => (user.appUserId === updated.appUserId ? updated : user)));
      setToast("User updated successfully.");
      closeEditDialog();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update user details.";
      const nextFieldErrors: Record<string, string> = {};
      if (message.toLowerCase().includes("employee code already exists")) {
        nextFieldErrors.employeeCode = "Employee code already exists for this clinic.";
      }
      if (message.toLowerCase().includes("mobile")) {
        nextFieldErrors.mobile = message;
      }
      if (message.toLowerCase().includes("name is required")) {
        nextFieldErrors.displayName = "Name is required.";
      }
      if (message.toLowerCase().includes("cannot edit your own user details")) {
        nextFieldErrors.displayName = message;
      }
      if (message.toLowerCase().includes("role")) {
        nextFieldErrors.role = message;
      }
      if (Object.keys(nextFieldErrors).length > 0) {
        setEditFieldErrors(nextFieldErrors);
      }
      setError(message);
    } finally {
      setSavingUserId(null);
      setEditSubmitting(false);
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
                      <TableCell>Email / Login ID</TableCell>
                      <TableCell>Role</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Department</TableCell>
                      <TableCell>Employee Code</TableCell>
                      <TableCell>Mobile</TableCell>
                      <TableCell>Last Login</TableCell>
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
                          <TableCell>{user.username || user.email || user.keycloakSub || "-"}</TableCell>
                          <TableCell><Chip size="small" label={roleLabel(user.membershipRole || "Unassigned")} /></TableCell>
                          <TableCell>
                            <Chip size="small" color={active ? "success" : "default"} label={active ? "ACTIVE" : "DISABLED"} />
                          </TableCell>
                          <TableCell>{user.department || "-"}</TableCell>
                          <TableCell>{user.employeeCode || "-"}</TableCell>
                          <TableCell>{user.mobile || "-"}</TableCell>
                          <TableCell>{formatLocalDateTime(user.lastLoginAt)}</TableCell>
                          <TableCell>{new Date(user.createdAt).toLocaleString()}</TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end" alignItems="center">
                              {canEditUser(user) ? (
                                <Button size="small" variant="contained" disabled={savingUserId === user.appUserId} onClick={() => openEditDialog(user)}>
                                  Edit
                                </Button>
                              ) : null}
                              {canResetPasswords ? (
                                <Button size="small" variant="outlined" disabled={savingUserId === user.appUserId} onClick={() => void resetPassword(user)}>
                                  Reset Password
                                </Button>
                              ) : null}
                              {(user.membershipRole || "").toUpperCase() === "DOCTOR" ? (
                                <Button size="small" onClick={() => navigate(`/doctors/${user.appUserId}`)}>
                                  Doctor Details
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
                        {groupPermissions(role.permissions).map((group) => (
                          <Stack key={group.module} spacing={0.75}>
                            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.8 }}>
                              {group.module}
                            </Typography>
                            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
                              {group.permissions.map((permission) => (
                                <Chip key={permission} size="small" variant="outlined" label={permissionLabel(permission)} />
                              ))}
                            </Box>
                          </Stack>
                        ))}
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
              <TextField
                id="create-user-firstName"
                label={<RequiredLabel text="First Name" required />}
                value={createForm.firstName}
                onChange={(e) => setCreateForm((s) => ({ ...s, firstName: e.target.value }))}
                error={Boolean(createFieldErrors.firstName)}
                helperText={createFieldErrors.firstName || "Required."}
                fullWidth
                required
              />
              <TextField
                id="create-user-lastName"
                label="Last Name"
                value={createForm.lastName}
                onChange={(e) => setCreateForm((s) => ({ ...s, lastName: e.target.value }))}
                fullWidth
              />
            </Stack>
            <TextField
              id="create-user-username"
              label="Username / Login ID"
              value={createForm.username}
              onChange={(e) => setCreateForm((s) => ({ ...s, username: e.target.value }))}
              placeholder="e.g. reception01"
              error={Boolean(createFieldErrors.username)}
              helperText={createFieldErrors.username || "User can sign in using username or email if enabled."}
              fullWidth
            />
            <TextField
              id="create-user-email"
              label={<RequiredLabel text="Email" required />}
              type="email"
              value={createForm.email}
              onChange={(e) => setCreateForm((s) => ({ ...s, email: e.target.value }))}
              required
              fullWidth
              error={Boolean(createFieldErrors.email)}
              helperText={createFieldErrors.email || "Required, valid email."}
            />
            <FormControl fullWidth>
              <InputLabel id="create-role-label"><RequiredLabel text="Role" required /></InputLabel>
              <Select
                labelId="create-role-label"
                label="Role"
                value={createForm.role}
                onChange={(e) => setCreateForm((s) => ({ ...s, role: String(e.target.value) }))}
                error={Boolean(createFieldErrors.role)}
              >
                {ASSIGNABLE_ROLES.map((role) => (
                  <MenuItem key={role} value={role}>{roleLabel(role)}</MenuItem>
                ))}
              </Select>
              {createFieldErrors.role ? <Typography variant="caption" color="error">{createFieldErrors.role}</Typography> : null}
            </FormControl>
            <TextField
              id="create-user-employeeCode"
              label="Employee Code"
              value={createForm.employeeCode}
              onChange={(e) => setCreateForm((s) => ({ ...s, employeeCode: e.target.value }))}
              placeholder="EMP-001"
              error={Boolean(createFieldErrors.employeeCode)}
              helperText={createFieldErrors.employeeCode || "Optional staff code, unique within the clinic."}
              fullWidth
            />
            <TextField
              id="create-user-mobile"
              label="Mobile Number"
              value={createForm.mobile}
              onChange={(e) => setCreateForm((s) => ({ ...s, mobile: e.target.value }))}
              placeholder="9876543210"
              fullWidth
              error={Boolean(createFieldErrors.mobile)}
              helperText={createFieldErrors.mobile || "Optional staff mobile number."}
            />
            <FormControl fullWidth>
              <InputLabel id="create-department-label">Department</InputLabel>
              <Select
                labelId="create-department-label"
                label="Department"
                value={createForm.department}
                onChange={(e) => setCreateForm((s) => ({ ...s, department: String(e.target.value) }))}
                error={Boolean(createFieldErrors.department)}
              >
                <MenuItem value="">Select department</MenuItem>
                {DEPARTMENT_OPTIONS.map((department) => (
                  <MenuItem key={department} value={department}>{department}</MenuItem>
                ))}
              </Select>
              {createFieldErrors.department ? <Typography variant="caption" color="error">{createFieldErrors.department}</Typography> : null}
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
          <Button onClick={() => {
            setOpenCreate(false);
            setCreateFieldErrors({});
          }}>Cancel</Button>
          <Button variant="contained" onClick={() => void createUser()} disabled={createSubmitting || !createForm.email.trim() || !createForm.firstName.trim() || !createForm.role.trim()}>
            {createSubmitting ? "Creating..." : "Create User"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={openEdit} onClose={closeEditDialog} fullWidth maxWidth="sm">
        <DialogTitle>Edit User</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 0.5 }}>
            <TextField
              id="edit-user-displayName"
              label={<RequiredLabel text="Name" required />}
              value={editForm.displayName}
              onChange={(e) => setEditForm((current) => ({ ...current, displayName: e.target.value }))}
              error={Boolean(editFieldErrors.displayName)}
              helperText={editFieldErrors.displayName || "Staff display name."}
              fullWidth
              required
            />
            <TextField
              label="Email"
              value={editingUser?.email || "-"}
              fullWidth
              disabled
            />
            <TextField
              label="Login ID"
              value={editingUser?.username || "-"}
              fullWidth
              disabled
            />
            <TextField
              id="edit-user-employeeCode"
              label="Employee Code"
              value={editForm.employeeCode}
              onChange={(e) => setEditForm((current) => ({ ...current, employeeCode: e.target.value }))}
              error={Boolean(editFieldErrors.employeeCode)}
              helperText={editFieldErrors.employeeCode || "Unique within the clinic if provided."}
              fullWidth
            />
            <TextField
              id="edit-user-mobile"
              label="Mobile Number"
              value={editForm.mobile}
              onChange={(e) => setEditForm((current) => ({ ...current, mobile: e.target.value }))}
              error={Boolean(editFieldErrors.mobile)}
              helperText={editFieldErrors.mobile || "Enter a valid 10-digit mobile number."}
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel id="edit-department-label">Department</InputLabel>
              <Select
                id="edit-user-department"
                labelId="edit-department-label"
                label="Department"
                value={editForm.department}
                onChange={(e) => setEditForm((current) => ({ ...current, department: String(e.target.value) }))}
                error={Boolean(editFieldErrors.department)}
              >
                <MenuItem value="">Select department</MenuItem>
                {DEPARTMENT_OPTIONS.map((department) => (
                  <MenuItem key={department} value={department}>{department}</MenuItem>
                ))}
              </Select>
              {editFieldErrors.department ? <Typography variant="caption" color="error">{editFieldErrors.department}</Typography> : null}
            </FormControl>
            {canEditRoleForUser(editingUser) ? (
              <FormControl fullWidth>
                <InputLabel id="edit-role-label"><RequiredLabel text="Role" required /></InputLabel>
                <Select
                  id="edit-user-role"
                  labelId="edit-role-label"
                  label="Role"
                  value={editForm.role}
                  onChange={(e) => setEditForm((current) => ({ ...current, role: String(e.target.value) }))}
                  error={Boolean(editFieldErrors.role)}
                >
                  {editableRoleOptions.map((role) => (
                    <MenuItem key={role.role} value={role.role}>{role.displayName}</MenuItem>
                  ))}
                </Select>
                {editFieldErrors.role ? <Typography variant="caption" color="error">{editFieldErrors.role}</Typography> : null}
              </FormControl>
            ) : (
              <TextField
                label="Role"
                value={roleLabel(editingUser?.membershipRole || "-")}
                fullWidth
                disabled
                helperText="Role changes for this user are restricted."
              />
            )}
            <Stack direction="row" alignItems="center" justifyContent="space-between">
              <Box>
                <Typography variant="body2">Active status</Typography>
                <Typography variant="caption" color="text.secondary">
                  Last Login: {formatLocalDateTime(editingUser?.lastLoginAt)}
                </Typography>
                <Typography variant="caption" color="text.secondary" display="block">
                  Created: {editingUser?.createdAt ? new Date(editingUser.createdAt).toLocaleString() : "-"}
                </Typography>
              </Box>
              <Switch
                checked={editForm.active}
                onChange={(e) => setEditForm((current) => ({ ...current, active: e.target.checked }))}
              />
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeEditDialog}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveUserEdits()} disabled={editSubmitting || !editingUser}>
            {editSubmitting ? "Saving..." : "Save Changes"}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={Boolean(toast)} autoHideDuration={3000} onClose={() => setToast(null)}>
        <Alert severity="success" onClose={() => setToast(null)}>{toast}</Alert>
      </Snackbar>
    </Stack>
  );
}
