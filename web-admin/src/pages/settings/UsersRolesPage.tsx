import * as React from "react";
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { getClinicRoles, getClinicUsers, type ClinicRole, type ClinicUser } from "../../api/clinicApi";

function roleLabel(role: string) {
  return role
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

export default function UsersRolesPage() {
  const auth = useAuth();
  const [users, setUsers] = React.useState<ClinicUser[]>([]);
  const [roles, setRoles] = React.useState<ClinicRole[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;

    async function load() {
      if (!auth.accessToken || !auth.tenantId) {
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

        if (!cancelled) {
          setUsers(userRows);
          setRoles(roleRows);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load users and roles");
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
  }, [auth.accessToken, auth.tenantId]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
          Users & Roles
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Tenant user roster and the default role-permission catalog used by the clinic platform.
        </Typography>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {!auth.hasPermission("user.manage") ? <Alert severity="info">You can view the roster but not manage it.</Alert> : null}

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Clinic users
            </Typography>
            {loading ? (
              <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                <CircularProgress />
              </Box>
            ) : users.length === 0 ? (
              <Alert severity="info">No clinic users were found for this tenant.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>User</TableCell>
                    <TableCell>Role</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Created</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {users.map((user) => (
                    <TableRow key={user.appUserId}>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {user.displayName || user.email || user.keycloakSub || user.appUserId}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {user.email || user.keycloakSub || "No contact details"}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" label={user.membershipRole || "Unassigned"} />
                      </TableCell>
                      <TableCell>{user.membershipStatus || user.userStatus}</TableCell>
                      <TableCell>{new Date(user.createdAt).toLocaleString()}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Role permissions
            </Typography>
            <Typography variant="body2" color="text.secondary">
              These are the default role-to-permission mappings currently wired into the backend RBAC layer.
            </Typography>
            {loading ? (
              <Box sx={{ display: "grid", placeItems: "center", minHeight: 180 }}>
                <CircularProgress />
              </Box>
            ) : (
              <Stack spacing={2}>
                {roles.map((role) => (
                  <Card key={role.role} variant="outlined">
                    <CardContent>
                      <Stack spacing={1.5}>
                        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                            {role.displayName}
                          </Typography>
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
    </Stack>
  );
}
