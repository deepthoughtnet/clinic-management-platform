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
import { Controller, useForm } from "react-hook-form";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import VisibilityRoundedIcon from "@mui/icons-material/VisibilityRounded";
import StoreRoundedIcon from "@mui/icons-material/StoreRounded";
import { useNavigate } from "react-router-dom";

import {
  getCitySuggestions,
  getCountrySuggestions,
  getIndiaStateSuggestions,
  createTenantSchema,
  normalizeIndianMobileInput,
  type CreateTenantFormValues,
  zodFormResolver,
} from "@deepthoughtnet/form-validation-kit";

import { useAuth } from "../../auth/useAuth";
import {
  createPlatformTenant,
  getPlatformPlans,
  getPlatformTenants,
  type PlatformPlan,
  type PlatformTenant,
  updatePlatformTenantStatus,
} from "../../api/clinicApi";
import AutocompleteTextInput from "../../components/forms/AutocompleteTextInput";

const MODULE_CODES = ["APPOINTMENTS", "CONSULTATION", "PRESCRIPTION", "BILLING", "VACCINATION", "INVENTORY", "AI_COPILOT", "CAREPILOT"] as const;

const EMPTY_FORM: CreateTenantFormValues = {
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
  modules: Object.fromEntries(MODULE_CODES.map((code) => [code, !["AI_COPILOT", "CAREPILOT"].includes(code)])) as Record<string, boolean>,
  publicListingEnabled: false,
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
  const [createFormError, setCreateFormError] = React.useState<string | null>(null);

  const {
    control,
    register,
    handleSubmit,
    reset,
    clearErrors,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<CreateTenantFormValues>({
    resolver: zodFormResolver(createTenantSchema),
    defaultValues: EMPTY_FORM,
    mode: "onSubmit",
  });

  const requiredCreateFields = watch(["clinicName", "tenantCode", "city", "country", "adminEmail"]);
  const canCreateTenant = requiredCreateFields.every((value: string | undefined) => typeof value === "string" ? value.trim().length > 0 : Boolean(value));
  const countryValue = watch("country") || "";
  const cityValue = watch("city") || "";
  const stateValue = watch("state") || "";
  const countrySuggestions = getCountrySuggestions(countryValue);
  const stateSuggestions = countryValue.trim().toLowerCase() === "india" ? getIndiaStateSuggestions(stateValue) : [];
  const citySuggestions = getCitySuggestions(cityValue, countryValue);

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
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tenants");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken]);

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

  const closeCreateDialog = React.useCallback(() => {
    setOpenCreate(false);
    setCreateFormError(null);
    clearErrors();
    reset(EMPTY_FORM);
  }, [clearErrors, reset]);

  const onCreateTenant = React.useCallback(async (values: CreateTenantFormValues) => {
    if (!auth.accessToken) return;
    setCreateFormError(null);
    try {
      const created = await createPlatformTenant(auth.accessToken, {
        clinicName: values.clinicName.trim(),
        tenantCode: values.tenantCode.trim(),
        displayName: values.displayName?.trim() || null,
        city: values.city.trim(),
        state: values.state?.trim() || null,
        country: values.country.trim(),
        postalCode: values.postalCode?.trim() || null,
        phone: values.phone ? (normalizeIndianMobileInput(values.phone) as string) : null,
        clinicEmail: values.clinicEmail || null,
        addressLine1: values.addressLine1?.trim() || null,
        addressLine2: values.addressLine2?.trim() || null,
        planId: values.planId?.trim() || null,
        modules: values.modules,
        adminEmail: values.adminEmail.trim(),
        adminFirstName: values.adminFirstName?.trim() || null,
        adminLastName: values.adminLastName?.trim() || null,
        tempPassword: values.tempPassword?.trim() || null,
      });
      setError(null);
      setMessage("Tenant created successfully.");
      auth.selectTenant({
        id: created.tenant.id,
        code: created.tenant.code,
        name: created.tenant.name,
      });
      closeCreateDialog();
      await load();
      navigate("/");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to create tenant";
      setCreateFormError(message);
    }
  }, [auth.accessToken, auth.selectTenant, closeCreateDialog, load, navigate]);

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
          <Button
            variant="contained"
            startIcon={<AddRoundedIcon />}
            onClick={() => {
              setCreateFormError(null);
              clearErrors();
              setOpenCreate(true);
            }}
          >
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
                          onClick={() => {
                            auth.selectTenant({ id: tenant.id, code: tenant.code, name: tenant.name });
                            navigate("/");
                          }}
                        >
                          {isSelected ? "Open" : "Open Tenant"}
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
        onClose={closeCreateDialog}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Create Tenant</DialogTitle>
        <DialogContent>
          <Box component="form" id="create-tenant-form" onSubmit={handleSubmit(onCreateTenant)} noValidate>
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                <TextField
                  label="Clinic Name"
                  fullWidth
                  required
                  error={Boolean(errors.clinicName)}
                  helperText={errors.clinicName?.message || " "}
                  {...register("clinicName")}
                />
                <TextField
                  label="Tenant Code"
                  fullWidth
                  required
                  error={Boolean(errors.tenantCode)}
                  helperText={errors.tenantCode?.message || " "}
                  {...register("tenantCode", {
                    setValueAs: (value) => {
                      if (typeof value !== "string") return value;
                      return value.toLowerCase().replace(/\s+/g, "-");
                    },
                  })}
                />
              </Stack>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                <TextField label="Display Name" fullWidth {...register("displayName")} />
                <TextField label="Plan" select fullWidth {...register("planId")}>
                  {plans.map((plan) => (
                    <MenuItem key={plan.id} value={plan.id}>
                      {plan.id} - {plan.name}
                    </MenuItem>
                  ))}
                </TextField>
              </Stack>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                <Controller
                  control={control}
                  name="city"
                  render={({ field }) => (
                    <AutocompleteTextInput
                      label="City"
                      value={field.value || ""}
                      onChange={field.onChange}
                      suggestions={citySuggestions}
                      error={Boolean(errors.city)}
                      helperText={errors.city?.message || " "}
                    />
                  )}
                />
                <Controller
                  control={control}
                  name="state"
                  render={({ field }) => (
                    <AutocompleteTextInput
                      label="State"
                      value={field.value || ""}
                      onChange={field.onChange}
                      suggestions={stateSuggestions}
                      helperText=" "
                    />
                  )}
                />
                <Controller
                  control={control}
                  name="country"
                  render={({ field }) => (
                    <AutocompleteTextInput
                      label="Country"
                      value={field.value || ""}
                      onChange={field.onChange}
                      suggestions={countrySuggestions}
                      error={Boolean(errors.country)}
                      helperText={errors.country?.message || " "}
                    />
                  )}
                />
              </Stack>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                <TextField
                  label="Clinic Email"
                  fullWidth
                  error={Boolean(errors.clinicEmail)}
                  helperText={errors.clinicEmail?.message || " "}
                  {...register("clinicEmail")}
                />
                <TextField
                  label="Phone"
                  fullWidth
                  error={Boolean(errors.phone)}
                  helperText={errors.phone?.message || " "}
                  inputProps={{ inputMode: "tel" }}
                  {...register("phone")}
                />
                <TextField label="Postal Code" fullWidth {...register("postalCode")} />
              </Stack>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                <TextField label="Address Line 1" fullWidth {...register("addressLine1")} />
                <TextField label="Address Line 2" fullWidth {...register("addressLine2")} />
              </Stack>

              <Stack direction="row" spacing={1} alignItems="center">
                <StoreRoundedIcon fontSize="small" />
                <Typography variant="subtitle2">Default Modules</Typography>
              </Stack>
              <Controller
                control={control}
                name="modules"
                render={({ field }) => (
                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                    {MODULE_CODES.map((moduleCode) => (
                      <FormControlLabel
                        key={moduleCode}
                        control={
                          <Switch
                            checked={Boolean(field.value?.[moduleCode])}
                            onChange={(event) => {
                              field.onChange({
                                ...(field.value || {}),
                                [moduleCode]: event.target.checked,
                              });
                            }}
                          />
                        }
                        label={moduleCode}
                      />
                    ))}
                  </Stack>
                )}
              />

              <Typography variant="subtitle2">Clinic Admin</Typography>
              {createFormError ? <Alert severity="warning">{createFormError}</Alert> : null}
              <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
                <TextField
                  label="Admin Email"
                  fullWidth
                  required
                  error={Boolean(errors.adminEmail)}
                  helperText={errors.adminEmail?.message || " "}
                  {...register("adminEmail")}
                />
                <TextField label="First Name" fullWidth {...register("adminFirstName")} />
                <TextField label="Last Name" fullWidth {...register("adminLastName")} />
              </Stack>
              <TextField label="Temporary Password (optional)" fullWidth {...register("tempPassword")} />
            </Stack>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeCreateDialog}>Cancel</Button>
          <Button
            type="submit"
            form="create-tenant-form"
            variant="contained"
            disabled={isSubmitting || !canCreateTenant}
          >
            {isSubmitting ? "Creating..." : "Create Tenant"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
