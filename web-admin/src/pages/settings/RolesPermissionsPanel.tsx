import * as React from "react";
import {
  Alert,
  Box,
  Button,
  ButtonBase,
  Card,
  CardContent,
  Chip,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Divider,
} from "@mui/material";
import type { ClinicRole } from "../../api/clinicApi";
import {
  BUSINESS_ROLE_KEYS,
  DEFAULT_ROLE_KEY,
  buildPermissionMetadata,
  formatRoleLabel,
  getFilteredPermissionCount,
  getPermissionMetadata,
  getRoleMetadata,
  getSensitivePermissionCount,
  getVisibleRoles,
  groupPermissionsByModule,
} from "../../auth/rbacMetadata";

type ViewMode = "business" | "technical";

type RolePermissionsPanelProps = {
  roles: ClinicRole[];
};

const ALL_BUSINESS_ROLES = "__ALL_BUSINESS_ROLES__";
const ALL_TECHNICAL_ROLES = "__ALL_TECHNICAL_ROLES__";

function matchesRoleSearch(role: ClinicRole, search: string): boolean {
  const normalized = search.trim().toLowerCase();
  if (!normalized) return true;
  const metadata = getRoleMetadata(role.role);
  const haystack = [
    role.role,
    metadata.displayName,
    metadata.summary,
    metadata.description,
    ...role.permissions,
  ].join(" ").toLowerCase();
  return haystack.includes(normalized);
}

function RoleCard({
  role,
  selected,
  onSelect,
}: {
  role: ClinicRole;
  selected: boolean;
  onSelect: (role: string) => void;
}) {
  const metadata = getRoleMetadata(role.role);
  const sensitiveCount = getSensitivePermissionCount(role.permissions);
  const permissionCount = role.permissions.length;

  return (
    <ButtonBase
      onClick={() => onSelect(role.role)}
      sx={{
        display: "block",
        width: "100%",
        borderRadius: 2,
        textAlign: "left",
      }}
    >
      <Card
        variant="outlined"
        sx={{
          width: "100%",
          borderColor: selected ? "primary.main" : "divider",
          boxShadow: selected ? 3 : 0,
          backgroundColor: selected ? "action.selected" : "background.paper",
        }}
      >
        <CardContent sx={{ "&:last-child": { pb: 2 } }}>
          <Stack spacing={1}>
            <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 1 }}>
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                  {metadata.displayName}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {role.role}
                </Typography>
              </Box>
              <Chip size="small" label={metadata.status} color={metadata.businessVisible ? "success" : "default"} />
            </Box>
            <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.4 }}>
              {metadata.summary}
            </Typography>
            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
              <Chip size="small" variant="outlined" label={`${permissionCount} permissions`} />
              <Chip size="small" variant="outlined" color={sensitiveCount > 0 ? "warning" : "default"} label={`${sensitiveCount} sensitive`} />
              <Chip size="small" variant="outlined" label={metadata.category === "tenant-business" ? "Tenant Business" : metadata.category === "tenant-technical" ? "Tenant Technical" : metadata.category === "legacy-compatibility" ? "Legacy" : metadata.category === "platform-internal" ? "Platform" : "Service"} />
              <Chip size="small" variant="outlined" label={metadata.assignableToHuman ? "Assignable" : "Technical only"} />
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </ButtonBase>
  );
}

function PermissionRow({ permission }: { permission: string }) {
  const metadata = getPermissionMetadata(permission);

  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        p: 1.5,
        backgroundColor: "background.paper",
      }}
    >
      <Stack spacing={0.75}>
        <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "flex-start", flexWrap: "wrap" }}>
          <Box>
            <Typography variant="body2" sx={{ fontWeight: 800 }}>
              {metadata.label}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {metadata.description}
            </Typography>
          </Box>
          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap justifyContent="flex-end">
            <Chip size="small" variant="outlined" label={metadata.action} />
            {metadata.sensitive ? <Chip size="small" color="warning" label="Sensitive" /> : null}
            {!metadata.tenantFacing ? <Chip size="small" variant="outlined" label="Technical" /> : null}
          </Stack>
        </Box>
        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: "monospace" }}>
          {metadata.key}
        </Typography>
      </Stack>
    </Box>
  );
}

function RoleDetails({
  role,
  search,
  moduleFilter,
  sensitiveOnly,
  expandedModules,
  onToggleModule,
}: {
  role: ClinicRole | null;
  search: string;
  moduleFilter: string;
  sensitiveOnly: boolean;
  expandedModules: Set<string>;
  onToggleModule: (module: string) => void;
}) {
  if (!role) {
    return (
      <Alert severity="info">
        No role matches the current filters.
      </Alert>
    );
  }

  const metadata = getRoleMetadata(role.role);
  const groups = groupPermissionsByModule(role.permissions, search, moduleFilter, sensitiveOnly);
  const totalShown = getFilteredPermissionCount(role.permissions, search, moduleFilter, sensitiveOnly);
  const totalSensitive = getSensitivePermissionCount(role.permissions);

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={2}>
          <Box>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
              <Chip label={metadata.status} color={metadata.businessVisible ? "success" : "default"} />
              <Chip label={metadata.category === "tenant-business" ? "Tenant Business Role" : metadata.category === "tenant-technical" ? "Tenant Technical Role" : metadata.category === "legacy-compatibility" ? "Legacy/Compatibility Role" : metadata.category === "platform-internal" ? "Platform/Internal Role" : "Service/System Role"} variant="outlined" />
              <Chip label={metadata.assignableToHuman ? "Assignable to human user" : "Not assignable to human user"} variant="outlined" />
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 900 }}>
              {metadata.displayName}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {metadata.summary}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              {metadata.description}
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 2 }}>
              <Chip variant="outlined" label={`Technical key: ${role.role}`} />
              <Chip variant="outlined" label={`Permissions: ${role.permissions.length}`} />
              <Chip variant="outlined" color="warning" label={`Sensitive: ${totalSensitive}`} />
              <Chip variant="outlined" label={`Shown: ${totalShown}`} />
            </Stack>
          </Box>

          <Divider />

          {groups.length === 0 ? (
            <Alert severity="info">No permissions match the current filters.</Alert>
          ) : (
            <Stack spacing={1}>
              {groups.map((group) => {
                const expanded = expandedModules.has(group.module);
                return (
                  <Accordion
                    key={group.module}
                    expanded={expanded}
                    onChange={() => onToggleModule(group.module)}
                    disableGutters
                    sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, "&:before": { display: "none" } }}
                  >
                    <AccordionSummary expandIcon={<span>⌄</span>}>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ width: "100%", pr: 1 }} flexWrap="wrap" useFlexGap>
                        <Typography sx={{ fontWeight: 800 }}>{group.module}</Typography>
                        <Chip size="small" label={group.permissions.length} />
                      </Stack>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Stack spacing={1}>
                        {group.permissions.map((permission) => (
                          <PermissionRow key={permission.key} permission={permission.key} />
                        ))}
                      </Stack>
                    </AccordionDetails>
                  </Accordion>
                );
              })}
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

export default function RolesPermissionsPanel({ roles }: RolePermissionsPanelProps) {
  const [viewMode, setViewMode] = React.useState<ViewMode>("business");
  const [search, setSearch] = React.useState("");
  const [moduleFilter, setModuleFilter] = React.useState("All modules");
  const [roleFilter, setRoleFilter] = React.useState<string>(ALL_BUSINESS_ROLES);
  const [sensitiveOnly, setSensitiveOnly] = React.useState(false);
  const [expandedModules, setExpandedModules] = React.useState<Set<string>>(new Set());
  const [selectedRoleKey, setSelectedRoleKey] = React.useState<string>(DEFAULT_ROLE_KEY);

  const visibleRoles = React.useMemo(() => {
    const catalog = getVisibleRoles(roles, viewMode);
    return catalog.filter((role) => matchesRoleSearch(role, search));
  }, [roles, search, viewMode]);

  const filteredRoles = React.useMemo(() => {
    if (roleFilter === ALL_BUSINESS_ROLES || roleFilter === ALL_TECHNICAL_ROLES) {
      return visibleRoles;
    }
    return visibleRoles.filter((role) => role.role === roleFilter);
  }, [roleFilter, visibleRoles]);

  const roleOptions = React.useMemo(() => {
    const options = visibleRoles.map((role) => role.role);
    return [viewMode === "business" ? ALL_BUSINESS_ROLES : ALL_TECHNICAL_ROLES, ...options];
  }, [visibleRoles, viewMode]);

  const moduleOptions = React.useMemo(() => {
    const modules = new Set<string>();
    for (const role of filteredRoles.length > 0 ? filteredRoles : visibleRoles) {
      for (const permission of role.permissions) {
        const metadata = buildPermissionMetadata(permission);
        if (search.trim()) {
          const haystack = [metadata.key, metadata.label, metadata.description, metadata.module].join(" ").toLowerCase();
          if (!haystack.includes(search.trim().toLowerCase())) continue;
        }
        if (sensitiveOnly && !metadata.sensitive) continue;
        modules.add(metadata.module);
      }
    }
    return ["All modules", ...Array.from(modules).sort((left, right) => left.localeCompare(right))];
  }, [filteredRoles, search, sensitiveOnly, visibleRoles]);

  const selectedRole = React.useMemo(() => {
    if (filteredRoles.length === 0) return null;
    const exact = filteredRoles.find((role) => role.role === selectedRoleKey);
    return exact || filteredRoles[0] || null;
  }, [filteredRoles, selectedRoleKey]);

  React.useEffect(() => {
    if (!filteredRoles.length) {
      return;
    }
    if (!filteredRoles.some((role) => role.role === selectedRoleKey)) {
      setSelectedRoleKey(filteredRoles[0].role);
    }
  }, [filteredRoles, selectedRoleKey]);

  React.useEffect(() => {
    if (!roleOptions.includes(roleFilter)) {
      setRoleFilter(viewMode === "business" ? ALL_BUSINESS_ROLES : ALL_TECHNICAL_ROLES);
    }
  }, [roleFilter, roleOptions, viewMode]);

  React.useEffect(() => {
    if (selectedRole?.role) {
      const modules = new Set<string>();
      for (const permission of selectedRole.permissions) {
        const metadata = buildPermissionMetadata(permission);
        if (search.trim()) {
          const haystack = [metadata.key, metadata.label, metadata.description, metadata.module].join(" ").toLowerCase();
          if (!haystack.includes(search.trim().toLowerCase())) continue;
        }
        if (sensitiveOnly && !metadata.sensitive) continue;
        if (moduleFilter !== "All modules" && metadata.module !== moduleFilter) continue;
        modules.add(metadata.module);
      }
      if (modules.size === 0) {
        setExpandedModules(new Set());
      } else if (expandedModules.size === 0) {
        setExpandedModules(new Set(modules));
      }
    }
  }, [expandedModules.size, moduleFilter, search, selectedRole, sensitiveOnly]);

  const visibleRoleCount = filteredRoles.length;
  const totalPermissions = filteredRoles.reduce((total, role) => total + role.permissions.length, 0);
  const sensitivePermissions = filteredRoles.reduce((total, role) => total + getSensitivePermissionCount(role.permissions), 0);
  const businessRoleCount = visibleRoles.filter((role) => BUSINESS_ROLE_KEYS.includes(role.role as (typeof BUSINESS_ROLE_KEYS)[number])).length;

  const toggleAllModules = (expanded: boolean) => {
    if (!selectedRole) return;
    if (expanded) {
      const modules = groupPermissionsByModule(selectedRole.permissions, search, moduleFilter, sensitiveOnly).map((group) => group.module);
      setExpandedModules(new Set(modules));
      return;
    }
    setExpandedModules(new Set());
  };

  return (
    <Stack spacing={2.5}>
      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h5" sx={{ fontWeight: 900 }}>
                Roles & Permissions
              </Typography>
              <Typography variant="body2" color="text.secondary">
                View effective system permissions for each tenant role. This page is read-only.
              </Typography>
            </Box>
            <Alert severity="info">
              Clinic Admin is the standard tenant-facing admin role. ADMIN and TENANT_ADMIN are compatibility roles retained for technical and legacy workflows.
            </Alert>
            <Box
              sx={{
                display: "grid",
                gap: 2,
                gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))", md: "repeat(4, minmax(0, 1fr))" },
              }}
            >
              <Box>
                <Card variant="outlined">
                  <CardContent>
                    <Typography variant="overline" color="text.secondary">Visible roles</Typography>
                    <Typography variant="h5" sx={{ fontWeight: 900 }}>{visibleRoleCount}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {viewMode === "business" ? "Tenant business roles" : "Technical and compatibility roles"}
                    </Typography>
                  </CardContent>
                </Card>
              </Box>
              <Box>
                <Card variant="outlined">
                  <CardContent>
                    <Typography variant="overline" color="text.secondary">Permissions</Typography>
                    <Typography variant="h5" sx={{ fontWeight: 900 }}>{totalPermissions}</Typography>
                    <Typography variant="caption" color="text.secondary">Across the current filtered role set</Typography>
                  </CardContent>
                </Card>
              </Box>
              <Box>
                <Card variant="outlined">
                  <CardContent>
                    <Typography variant="overline" color="text.secondary">Sensitive</Typography>
                    <Typography variant="h5" sx={{ fontWeight: 900 }}>{sensitivePermissions}</Typography>
                    <Typography variant="caption" color="text.secondary">High-risk permissions in the current view</Typography>
                  </CardContent>
                </Card>
              </Box>
              <Box>
                <Card variant="outlined">
                  <CardContent>
                    <Typography variant="overline" color="text.secondary">Business roles</Typography>
                    <Typography variant="h5" sx={{ fontWeight: 900 }}>{businessRoleCount}</Typography>
                    <Typography variant="caption" color="text.secondary">Clinic-facing roles shown by default</Typography>
                  </CardContent>
                </Card>
              </Box>
            </Box>

            <Stack spacing={1.5}>
              <Stack direction={{ xs: "column", lg: "row" }} spacing={1.5} alignItems={{ xs: "stretch", lg: "center" }}>
                <FormControl size="small" sx={{ minWidth: 180 }}>
                  <InputLabel>View</InputLabel>
                  <Select
                    label="View"
                    value={viewMode}
                    onChange={(event) => {
                      const next = event.target.value as ViewMode;
                      setViewMode(next);
                      setRoleFilter(next === "business" ? ALL_BUSINESS_ROLES : ALL_TECHNICAL_ROLES);
                      setExpandedModules(new Set());
                    }}
                  >
                    <MenuItem value="business">Business View</MenuItem>
                    <MenuItem value="technical">Technical View</MenuItem>
                  </Select>
                </FormControl>

                <FormControl size="small" sx={{ minWidth: 240 }}>
                  <InputLabel>Role filter</InputLabel>
                  <Select
                    label="Role filter"
                    value={roleFilter}
                    onChange={(event) => {
                      const next = event.target.value as string;
                      setRoleFilter(next);
                      if (next !== ALL_BUSINESS_ROLES && next !== ALL_TECHNICAL_ROLES) {
                        setSelectedRoleKey(next);
                      }
                    }}
                  >
                    <MenuItem value={viewMode === "business" ? ALL_BUSINESS_ROLES : ALL_TECHNICAL_ROLES}>
                      {viewMode === "business" ? "All business roles" : "All technical roles"}
                    </MenuItem>
                    {visibleRoles.map((role) => (
                      <MenuItem key={role.role} value={role.role}>
                        {formatRoleLabel(role.role)}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <FormControl size="small" sx={{ minWidth: 220 }}>
                  <InputLabel>Module filter</InputLabel>
                  <Select
                    label="Module filter"
                    value={moduleFilter}
                    onChange={(event) => {
                      setModuleFilter(event.target.value as string);
                      setExpandedModules(new Set());
                    }}
                  >
                    {moduleOptions.map((module) => (
                      <MenuItem key={module} value={module}>
                        {module}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  size="small"
                  fullWidth
                  label="Search permissions"
                  value={search}
                  onChange={(event) => {
                    setSearch(event.target.value);
                    setExpandedModules(new Set());
                  }}
                />

                <FormControlLabel
                  control={<Switch checked={sensitiveOnly} onChange={(event) => { setSensitiveOnly(event.target.checked); setExpandedModules(new Set()); }} />}
                  label="Sensitive only"
                />
              </Stack>

              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Button variant="outlined" onClick={() => toggleAllModules(true)} disabled={!selectedRole || groupPermissionsByModule(selectedRole.permissions, search, moduleFilter, sensitiveOnly).length === 0}>
                  Expand All
                </Button>
                <Button variant="outlined" onClick={() => toggleAllModules(false)} disabled={!selectedRole}>
                  Collapse All
                </Button>
                <Chip variant="outlined" label={viewMode === "business" ? "Business view hides platform/internal roles" : "Technical view includes compatibility roles"} />
              </Stack>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Box
        sx={{
          display: "grid",
          gap: 2,
          gridTemplateColumns: { xs: "1fr", md: "minmax(0, 1fr) minmax(0, 2fr)" },
          alignItems: "start",
        }}
      >
        <Box>
          <Stack spacing={1.5} sx={{ position: { md: "sticky" }, top: { md: 16 } }}>
            <Typography variant="subtitle2" color="text.secondary" sx={{ fontWeight: 800 }}>
              Roles
            </Typography>
            {filteredRoles.length === 0 ? (
              <Alert severity="info">No roles match the current filters.</Alert>
            ) : (
              filteredRoles.map((role) => (
                <RoleCard
                  key={role.role}
                  role={role}
                  selected={selectedRole?.role === role.role}
                  onSelect={setSelectedRoleKey}
                />
              ))
            )}
          </Stack>
        </Box>

        <Box>
          <RoleDetails
            role={selectedRole}
            search={search}
            moduleFilter={moduleFilter}
            sensitiveOnly={sensitiveOnly}
            expandedModules={expandedModules}
            onToggleModule={(module) => {
              setExpandedModules((current) => {
                const next = new Set(current);
                if (next.has(module)) {
                  next.delete(module);
                } else {
                  next.add(module);
                }
                return next;
              });
            }}
          />
        </Box>
      </Box>
    </Stack>
  );
}
