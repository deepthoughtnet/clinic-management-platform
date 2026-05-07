import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import VisibilityRoundedIcon from "@mui/icons-material/VisibilityRounded";
import StoreRoundedIcon from "@mui/icons-material/StoreRounded";
import { useNavigate } from "react-router-dom";

import { useAuth } from "../../auth/useAuth";
import {
  createPlatformTenant,
  getPlatformPlans,
  getPlatformTenants,
  type PlatformPlan,
  type PlatformTenant,
  updatePlatformTenantStatus,
} from "../../api/clinicApi";

const MODULE_CODES = ["APPOINTMENTS", "CONSULTATION", "PRESCRIPTION", "BILLING", "VACCINATION", "INVENTORY", "AI_COPILOT"] as const;

type CreateTenantForm = {
  clinicName: string;
  tenantCode: string;
  displayName: string;
  city: string;
  state: string;
  country: string;
  postalCode: string;
  phone: string;
  clinicEmail: string;
  addressLine1: string;
  addressLine2: string;
  planId: string;
  adminEmail: string;
  adminFirstName: string;
  adminLastName: string;
  tempPassword: string;
  modules: Record<string, boolean>;
};

const EMPTY_FORM: CreateTenantForm = {
  clinicName: "",
  tenantCode: "",
  displayName: "",
  city: "",
  state: "Maharashtra",
  country: "India",
  postalCode: "",
  phone: "",
  clinicEmail: "",
  addressLine1: "",
  addressLine2: "",
  planId: "FREE",
  adminEmail: "",
  adminFirstName: "",
  adminLastName: "",
  tempPassword: "",
  modules: Object.fromEntries(MODULE_CODES.map((code) => [code, code !== "AI_COPILOT"])) as Record<string, boolean>,
};

function formatDate(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString();
}

function moduleCount(modules?: Record<string, boolean> | null) {
  if (!modules) return 0;
  return Object.values(modules).filter(Boolean).length;
}

export default function TenantsPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [rows, setRows] = React.useState<PlatformTenant[]>([]);
  const [plans, setPlans] = React.useState<PlatformPlan[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [message, setMessage] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState("ALL");
  const [openCreate, setOpenCreate] = React.useState(false);
  const [form, setForm] = React.useState<CreateTenantForm>(EMPTY_FORM);
  const [submitting, setSubmitting] = React.useState(false);
  const [createFormError, setCreateFormError] = React.useState<string | null>(null);

  const load = React.useCallback(async () => {
    if (!auth.accessToken) return;
    setLoading(true);
    setError(null);
    try {
      const [tenants, platformPlans] = await Promise.all([
        getPlatformTenants(auth.accessToken),
        getPlatformPlans(auth.accessToken),
      ]);
      setRows(tenants);
      setPlans(platformPlans);
      if (platformPlans.length > 0 && !form.planId) {
        setForm((prev) => ({ ...prev, planId: platformPlans[0].id }));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tenants");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, form.planId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const filteredRows = rows.filter((tenant) => {
    const hay = `${tenant.code} ${tenant.name} ${tenant.planId || ""}`.toLowerCase();
    const matchesSearch = hay.includes(search.trim().toLowerCase());
    const matchesStatus = statusFilter === "ALL" || (tenant.status || "").toUpperCase() === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const onStatusChange = async (tenant: PlatformTenant, active: boolean) => {
    if (!auth.accessToken) return;
    try {
      const updated = await updatePlatformTenantStatus(auth.accessToken, tenant.id, active);
      setRows((prev) => prev.map((row) => (row.id === updated.id ? updated : row)));
      setMessage(`${updated.name} is now ${active ? "ACTIVE" : "SUSPENDED"}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update tenant status");
    }
  };

  const onCreateTenant = async () => {
    if (!auth.accessToken) return;
    const adminEmail = form.adminEmail.trim();
    if (!adminEmail) {
      setCreateFormError("Admin email is required to provision clinic admin.");
      return;
    }
    setCreateFormError(null);
    setSubmitting(true);
    try {
      const created = await createPlatformTenant(auth.accessToken, {
        clinicName: form.clinicName,
        tenantCode: form.tenantCode,
        displayName: form.displayName || null,
        city: form.city,
        state: form.state || null,
        country: form.country,
        postalCode: form.postalCode || null,
        phone: form.phone || null,
        clinicEmail: form.clinicEmail || null,
        addressLine1: form.addressLine1 || null,
        addressLine2: form.addressLine2 || null,
        planId: form.planId || null,
        modules: form.modules,
        adminEmail,
        adminFirstName: form.adminFirstName || null,
        adminLastName: form.adminLastName || null,
        tempPassword: form.tempPassword || null,
      });
      setError(null);
      setMessage("Tenant created successfully.");
      auth.selectTenant({
        id: created.tenant.id,
        code: created.tenant.code,
        name: created.tenant.name,
      });
      setOpenCreate(false);
      setForm(EMPTY_FORM);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create tenant");
    } finally {
      setSubmitting(false);
    }
  };

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  return (
    <Stack spacing={2.5}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>
            Tenants
          </Typography>
          <Typography color="text.secondary">Create clinics, manage status, and switch into a tenant context for operations.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Tooltip title="Refresh">
            <IconButton onClick={() => void load()}>
              <RefreshRoundedIcon />
            </IconButton>
          </Tooltip>
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setOpenCreate(true)}>
            Create Tenant
          </Button>
        </Stack>
      </Box>

      {message ? <Alert severity="success" onClose={() => setMessage(null)}>{message}</Alert> : null}
      {error ? (
        <Alert
          severity="error"
          onClose={() => setError(null)}
          action={
            <Button color="inherit" size="small" onClick={() => void load()}>
              Retry
            </Button>
          }
        >
          {error}
        </Alert>
      ) : null}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
          <TextField
            size="small"
            label="Search tenant"
            placeholder="code, name, plan"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            fullWidth
          />
          <TextField
            size="small"
            label="Status"
            select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value="ALL">All</MenuItem>
            <MenuItem value="ACTIVE">Active</MenuItem>
            <MenuItem value="SUSPENDED">Suspended</MenuItem>
          </TextField>
        </Stack>
      </Paper>

      <Paper variant="outlined">
        {loading ? (
          <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
            <CircularProgress />
          </Box>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Tenant</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Plan</TableCell>
                <TableCell>Modules</TableCell>
                <TableCell>Created</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredRows.map((tenant) => {
                const isSelected = auth.selectedTenant?.id === tenant.id;
                const isActive = (tenant.status || "").toUpperCase() === "ACTIVE";
                return (
                  <TableRow key={tenant.id} hover>
                    <TableCell>
                      <Stack spacing={0.4}>
                        <Typography sx={{ fontWeight: 700 }}>{tenant.name}</Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: "monospace" }}>
                          {tenant.code}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <Chip size="small" color={isActive ? "success" : "warning"} label={tenant.status || "UNKNOWN"} />
                    </TableCell>
                    <TableCell>
                      <Chip size="small" variant="outlined" label={tenant.planId || "-"} />
                    </TableCell>
                    <TableCell>{moduleCount(tenant.modules)}</TableCell>
                    <TableCell>{formatDate(tenant.createdAt)}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Tooltip title="View details">
                          <IconButton size="small" onClick={() => navigate(`/platform/tenants/${tenant.id}`)}>
                            <VisibilityRoundedIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Button
                          size="small"
                          variant={isSelected ? "contained" : "outlined"}
                          onClick={() => auth.selectTenant({ id: tenant.id, code: tenant.code, name: tenant.name })}
                        >
                          {isSelected ? "Selected" : "Select"}
                        </Button>
                        <Button size="small" onClick={() => onStatusChange(tenant, !isActive)}>
                          {isActive ? "Deactivate" : "Activate"}
                        </Button>
                      </Stack>
                    </TableCell>
                  </TableRow>
                );
              })}
              {filteredRows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6}>
                    <Alert severity="info">No tenants match the current filter.</Alert>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        )}
      </Paper>

      <Dialog
        open={openCreate}
        onClose={() => {
          setOpenCreate(false);
          setCreateFormError(null);
        }}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Create Tenant</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 0.5 }}>
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <TextField label="Clinic Name" value={form.clinicName} onChange={(e) => setForm((s) => ({ ...s, clinicName: e.target.value }))} fullWidth required />
              <TextField label="Tenant Code" value={form.tenantCode} onChange={(e) => setForm((s) => ({ ...s, tenantCode: e.target.value.toLowerCase().replace(/\s+/g, "-") }))} fullWidth required />
            </Stack>
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <TextField label="Display Name" value={form.displayName} onChange={(e) => setForm((s) => ({ ...s, displayName: e.target.value }))} fullWidth />
              <TextField label="Plan" select value={form.planId} onChange={(e) => setForm((s) => ({ ...s, planId: e.target.value }))} fullWidth>
                {plans.map((plan) => <MenuItem key={plan.id} value={plan.id}>{plan.id} - {plan.name}</MenuItem>)}
              </TextField>
            </Stack>
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <TextField label="City" value={form.city} onChange={(e) => setForm((s) => ({ ...s, city: e.target.value }))} fullWidth required />
              <TextField label="State" value={form.state} onChange={(e) => setForm((s) => ({ ...s, state: e.target.value }))} fullWidth />
              <TextField label="Country" value={form.country} onChange={(e) => setForm((s) => ({ ...s, country: e.target.value }))} fullWidth required />
            </Stack>
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <TextField label="Clinic Email" value={form.clinicEmail} onChange={(e) => setForm((s) => ({ ...s, clinicEmail: e.target.value }))} fullWidth />
              <TextField label="Phone" value={form.phone} onChange={(e) => setForm((s) => ({ ...s, phone: e.target.value }))} fullWidth />
              <TextField label="Postal Code" value={form.postalCode} onChange={(e) => setForm((s) => ({ ...s, postalCode: e.target.value }))} fullWidth />
            </Stack>
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <TextField label="Address Line 1" value={form.addressLine1} onChange={(e) => setForm((s) => ({ ...s, addressLine1: e.target.value }))} fullWidth />
              <TextField label="Address Line 2" value={form.addressLine2} onChange={(e) => setForm((s) => ({ ...s, addressLine2: e.target.value }))} fullWidth />
            </Stack>

            <Stack direction="row" spacing={1} alignItems="center">
              <StoreRoundedIcon fontSize="small" />
              <Typography variant="subtitle2">Default Modules</Typography>
            </Stack>
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
              {MODULE_CODES.map((moduleCode) => (
                <FormControlLabel
                  key={moduleCode}
                  control={
                    <Switch
                      checked={Boolean(form.modules[moduleCode])}
                      onChange={(event) => setForm((s) => ({ ...s, modules: { ...s.modules, [moduleCode]: event.target.checked } }))}
                    />
                  }
                  label={moduleCode}
                />
              ))}
            </Stack>

            <Typography variant="subtitle2">Clinic Admin</Typography>
            {createFormError ? <Alert severity="warning">{createFormError}</Alert> : null}
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
              <TextField
                label="Admin Email"
                value={form.adminEmail}
                onChange={(e) => {
                  setCreateFormError(null);
                  setForm((s) => ({ ...s, adminEmail: e.target.value }));
                }}
                fullWidth
                required
              />
              <TextField label="First Name" value={form.adminFirstName} onChange={(e) => setForm((s) => ({ ...s, adminFirstName: e.target.value }))} fullWidth />
              <TextField label="Last Name" value={form.adminLastName} onChange={(e) => setForm((s) => ({ ...s, adminLastName: e.target.value }))} fullWidth />
            </Stack>
            <TextField label="Temporary Password (optional)" value={form.tempPassword} onChange={(e) => setForm((s) => ({ ...s, tempPassword: e.target.value }))} fullWidth />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenCreate(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => void onCreateTenant()}
            disabled={submitting || !form.clinicName || !form.tenantCode || !form.city || !form.country || !form.adminEmail.trim()}
          >
            {submitting ? "Creating..." : "Create Tenant"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
