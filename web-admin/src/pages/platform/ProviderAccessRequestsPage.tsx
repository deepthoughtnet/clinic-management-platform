import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Chip,
  Typography,
} from "@mui/material";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import VisibilityRoundedIcon from "@mui/icons-material/VisibilityRounded";
import CheckCircleRoundedIcon from "@mui/icons-material/CheckCircleRounded";
import BlockRoundedIcon from "@mui/icons-material/BlockRounded";
import PauseCircleRoundedIcon from "@mui/icons-material/PauseCircleRounded";
import { useSearchParams } from "react-router-dom";

import { useAuth } from "../../auth/useAuth";
import {
  approveProviderAccessRequest,
  getProviderAccessRequest,
  listProviderAccessRequests,
  rejectProviderAccessRequest,
  revokeProviderAccessRequest,
  type ProviderAccessRequestResponse,
} from "../../api/clinicApi";

const STATUS_OPTIONS = ["ALL", "REQUESTED", "APPROVED", "REJECTED", "REVOKED"] as const;

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function statusColor(status: string | null | undefined): "default" | "info" | "success" | "warning" | "error" {
  switch ((status || "").toUpperCase()) {
    case "REQUESTED":
      return "warning";
    case "APPROVED":
      return "success";
    case "REJECTED":
    case "REVOKED":
      return "error";
    default:
      return "default";
  }
}

function friendlyLabel(value: string | null | undefined) {
  if (!value) {
    return "—";
  }
  return value
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function formatProviderAccountLabel(request: ProviderAccessRequestResponse) {
  return request.linkedProviderAccountDisplayName || request.linkedProviderApplicationReference || request.providerApplicationReference || "—";
}

export default function ProviderAccessRequestsPage() {
  const auth = useAuth();
  const token = auth.accessToken || "";
  const [searchParams, setSearchParams] = useSearchParams();
  const [requests, setRequests] = React.useState<ProviderAccessRequestResponse[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [selectedRequest, setSelectedRequest] = React.useState<ProviderAccessRequestResponse | null>(null);
  const [detailLoading, setDetailLoading] = React.useState(false);
  const [decisionReason, setDecisionReason] = React.useState("");
  const [decisionProviderApplicationReference, setDecisionProviderApplicationReference] = React.useState("");
  const [decisionPending, setDecisionPending] = React.useState(false);
  const [actionMessage, setActionMessage] = React.useState<string | null>(null);

  const status = (searchParams.get("status") || "REQUESTED").toUpperCase();
  const q = searchParams.get("q") || "";
  const selectedStatus = (selectedRequest?.status || "").toUpperCase();
  const canApproveSelected = selectedStatus === "REQUESTED";
  const canRejectSelected = selectedStatus === "REQUESTED";
  const canRevokeSelected = selectedStatus === "APPROVED";

  React.useEffect(() => {
    const next = new URLSearchParams(searchParams);
    if (!next.get("status")) {
      next.set("status", "REQUESTED");
      setSearchParams(next, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  React.useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listProviderAccessRequests(token, {
      status: status === "ALL" ? null : status,
      q,
    })
      .then((result) => {
        if (cancelled) return;
        setRequests(result);
      })
      .catch((fetchError: unknown) => {
        if (cancelled) return;
        setError(fetchError instanceof Error ? fetchError.message : "Unable to load provider access requests.");
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [q, status, token]);

  async function openRequest(request: ProviderAccessRequestResponse) {
    setActionMessage(null);
    setDetailLoading(true);
    try {
      const result = await getProviderAccessRequest(token, request.id);
      setSelectedRequest(result);
      setDecisionReason(result.rejectionReason || "");
      setDecisionProviderApplicationReference(result.providerApplicationReference || result.linkedProviderApplicationReference || "");
    } catch (detailError) {
      setError(detailError instanceof Error ? detailError.message : "Unable to open the request.");
    } finally {
      setDetailLoading(false);
    }
  }

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const result = await listProviderAccessRequests(token, {
        status: status === "ALL" ? null : status,
        q,
      });
      setRequests(result);
    } catch (refreshError) {
      setError(refreshError instanceof Error ? refreshError.message : "Unable to load provider access requests.");
    } finally {
      setLoading(false);
    }
  }

  async function runDecision(kind: "approve" | "reject" | "revoke") {
    if (!selectedRequest) {
      return;
    }
    setDecisionPending(true);
    setActionMessage(null);
    try {
      const payload = {
        reason: decisionReason.trim() || null,
        providerApplicationReference: decisionProviderApplicationReference.trim() || null,
      };
      const response = kind === "approve"
        ? await approveProviderAccessRequest(token, selectedRequest.id, payload)
        : kind === "reject"
          ? await rejectProviderAccessRequest(token, selectedRequest.id, payload)
          : await revokeProviderAccessRequest(token, selectedRequest.id, payload);
      setSelectedRequest(response);
      setDecisionReason(response.rejectionReason || "");
      setDecisionProviderApplicationReference(response.providerApplicationReference || response.linkedProviderApplicationReference || "");
      setActionMessage(
        kind === "approve"
          ? response.temporaryAccessCode
            ? `Request approved. Temporary access code: ${response.temporaryAccessCode}`
            : "Request approved."
          : kind === "reject"
            ? "Request rejected."
            : "Access revoked.",
      );
      await refresh();
    } catch (decisionError) {
      setError(decisionError instanceof Error ? decisionError.message : "Unable to complete the review action.");
    } finally {
      setDecisionPending(false);
    }
  }

  return (
    <Box sx={{ p: 3, maxWidth: 1320 }}>
      <Stack spacing={2.25}>
        <Box>
          <Typography variant="overline" color="text.secondary">
            Platform Admin
          </Typography>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>
            Provider Access Requests
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Review controlled-access requests for Jeevanam Provider workspace access.
          </Typography>
        </Box>

        <Paper elevation={0} sx={{ p: 2, border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
          <Stack direction={{ xs: "column", md: "row" }} spacing={2} alignItems={{ md: "center" }}>
            <TextField
              select
              label="Status"
              value={status}
              onChange={(event) => {
                const next = new URLSearchParams(searchParams);
                next.set("status", event.target.value);
                setSearchParams(next, { replace: true });
              }}
              size="small"
              sx={{ minWidth: 180 }}
            >
              {STATUS_OPTIONS.map((option) => (
                <MenuItem key={option} value={option}>
                  {option === "ALL" ? "All" : option.charAt(0) + option.slice(1).toLowerCase()}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Search"
              value={q}
              onChange={(event) => {
                const next = new URLSearchParams(searchParams);
                if (event.target.value.trim()) {
                  next.set("q", event.target.value);
                } else {
                  next.delete("q");
                }
                setSearchParams(next, { replace: true });
              }}
              size="small"
              fullWidth
              placeholder="Name, mobile, email, provider account"
            />
            <Button startIcon={<RefreshRoundedIcon />} variant="outlined" onClick={refresh}>
              Refresh
            </Button>
          </Stack>
        </Paper>

        {error ? <Alert severity="error">{error}</Alert> : null}
        {actionMessage ? <Alert severity="success">{actionMessage}</Alert> : null}

        <TableContainer component={Paper} elevation={0} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Mobile</TableCell>
                <TableCell>Email</TableCell>
                <TableCell>Provider Type</TableCell>
                <TableCell>Provider Account / Application</TableCell>
                <TableCell>Requested</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {requests.map((request) => (
                <TableRow key={request.id} hover selected={selectedRequest?.id === request.id}>
                  <TableCell>
                    <Stack spacing={0.5}>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        {request.fullName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {request.note || "—"}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>{request.mobile}</TableCell>
                  <TableCell>{request.email || "—"}</TableCell>
                  <TableCell>{friendlyLabel(request.providerType)}</TableCell>
                  <TableCell>{formatProviderAccountLabel(request)}</TableCell>
                  <TableCell>{formatDateTime(request.requestedAt)}</TableCell>
                  <TableCell>
                    <Chip size="small" label={friendlyLabel(request.status)} color={statusColor(request.status)} />
                  </TableCell>
                  <TableCell align="right">
                    <Button size="small" startIcon={<VisibilityRoundedIcon />} onClick={() => void openRequest(request)} disabled={detailLoading}>
                      Review
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {!loading && requests.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8}>
                    <Typography variant="body2" color="text.secondary">
                      No provider access requests match the current filters.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </TableContainer>
      </Stack>

      <Dialog open={Boolean(selectedRequest)} onClose={() => setSelectedRequest(null)} fullWidth maxWidth="md">
        <DialogTitle>Provider Access Request</DialogTitle>
        <DialogContent dividers>
          {selectedRequest ? (
            <Stack spacing={2}>
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                  {selectedRequest.fullName}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {friendlyLabel(selectedRequest.providerType)} · {selectedRequest.mobile} · {selectedRequest.email || "No email"}
                </Typography>
              </Box>

              <Stack spacing={1}>
                <Typography variant="body2">
                  <strong>Provider account / application:</strong> {formatProviderAccountLabel(selectedRequest)}
                </Typography>
                <Typography variant="body2">
                  <strong>Requested:</strong> {formatDateTime(selectedRequest.requestedAt)}
                </Typography>
                <Typography variant="body2">
                  <strong>Status:</strong> {friendlyLabel(selectedRequest.status)}
                </Typography>
                <Typography variant="body2">
                  <strong>Linked provider account:</strong> {selectedRequest.linkedProviderAccountDisplayName || "—"}
                </Typography>
                <Typography variant="body2">
                  <strong>Linked provider application:</strong> {selectedRequest.linkedProviderApplicationReference || "—"}
                </Typography>
                <Typography variant="body2">
                  <strong>Temporary access code:</strong> {selectedRequest.temporaryAccessCode || "—"}
                </Typography>
              </Stack>

              <TextField
                label="Provider application reference"
                value={decisionProviderApplicationReference}
                onChange={(event) => setDecisionProviderApplicationReference(event.target.value)}
                size="small"
                fullWidth
                helperText="Optional when the matching provider account is already known."
              />

              <TextField
                label="Decision reason"
                value={decisionReason}
                onChange={(event) => setDecisionReason(event.target.value)}
                size="small"
                fullWidth
                multiline
                minRows={3}
              />
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelectedRequest(null)}>Close</Button>
          {canRejectSelected ? (
            <Button
              variant="outlined"
              color="error"
              startIcon={<BlockRoundedIcon />}
              onClick={() => void runDecision("reject")}
              disabled={!selectedRequest || decisionPending}
            >
              Reject
            </Button>
          ) : null}
          {canRevokeSelected ? (
            <Button
              variant="outlined"
              startIcon={<PauseCircleRoundedIcon />}
              onClick={() => void runDecision("revoke")}
              disabled={!selectedRequest || decisionPending}
            >
              Revoke
            </Button>
          ) : null}
          {canApproveSelected ? (
            <Button
              variant="contained"
              color="success"
              startIcon={<CheckCircleRoundedIcon />}
              onClick={() => void runDecision("approve")}
              disabled={!selectedRequest || decisionPending}
            >
              Approve
            </Button>
          ) : null}
        </DialogActions>
      </Dialog>
    </Box>
  );
}
