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
  approveCareAccessRequest,
  getCareAccessRequest,
  listCareAccessRequests,
  rejectCareAccessRequest,
  revokeCareAccessRequest,
  type CareAccessRequestResponse,
} from "../../api/clinicApi";

const STATUS_OPTIONS = ["ALL", "REQUESTED", "APPROVED", "ACTIVE", "REJECTED", "REVOKED"] as const;

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
    case "ACTIVE":
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

function formatTenantLabel(request: CareAccessRequestResponse) {
  return request.tenantName || request.tenantCode || request.tenantId;
}

export default function CareAccessRequestsPage() {
  const auth = useAuth();
  const token = auth.accessToken || "";
  const [searchParams, setSearchParams] = useSearchParams();
  const [requests, setRequests] = React.useState<CareAccessRequestResponse[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [selectedRequest, setSelectedRequest] = React.useState<CareAccessRequestResponse | null>(null);
  const [detailLoading, setDetailLoading] = React.useState(false);
  const [decisionReason, setDecisionReason] = React.useState("");
  const [decisionPending, setDecisionPending] = React.useState(false);
  const [actionMessage, setActionMessage] = React.useState<string | null>(null);

  const status = (searchParams.get("status") || "REQUESTED").toUpperCase();
  const q = searchParams.get("q") || "";

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
    listCareAccessRequests(token, {
      status: status === "ALL" ? null : status,
      q,
    })
      .then((result) => {
        if (cancelled) return;
        setRequests(result);
      })
      .catch((fetchError: unknown) => {
        if (cancelled) return;
        setError(fetchError instanceof Error ? fetchError.message : "Unable to load care access requests.");
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

  async function openRequest(request: CareAccessRequestResponse) {
    setActionMessage(null);
    setDetailLoading(true);
    try {
      const result = await getCareAccessRequest(token, request.id);
      setSelectedRequest(result);
      setDecisionReason(result.rejectionReason || "");
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
      const result = await listCareAccessRequests(token, {
        status: status === "ALL" ? null : status,
        q,
      });
      setRequests(result);
    } catch (refreshError) {
      setError(refreshError instanceof Error ? refreshError.message : "Unable to load care access requests.");
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
      const payload = kind === "approve"
        ? { reason: decisionReason.trim() || null }
        : { reason: decisionReason.trim() || null };
      const response = kind === "approve"
        ? await approveCareAccessRequest(token, selectedRequest.id, payload)
        : kind === "reject"
          ? await rejectCareAccessRequest(token, selectedRequest.id, payload)
          : await revokeCareAccessRequest(token, selectedRequest.id, payload);
      setSelectedRequest(response);
      setDecisionReason(response.rejectionReason || "");
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
            Care Access Requests
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Review controlled-access requests for Jeevanam Care Friends & Family access.
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
              placeholder="Name, mobile, email, tenant"
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
                <TableCell>Clinic / Tenant</TableCell>
                <TableCell>Requested</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={7}>Loading care access requests...</TableCell>
                </TableRow>
              ) : requests.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7}>No care access requests found.</TableCell>
                </TableRow>
              ) : requests.map((request) => (
                <TableRow
                  key={request.id}
                  hover
                  sx={{ cursor: "pointer" }}
                  onClick={() => openRequest(request)}
                >
                  <TableCell>{request.fullName}</TableCell>
                  <TableCell>{request.mobile}</TableCell>
                  <TableCell>{request.email || "—"}</TableCell>
                  <TableCell>{formatTenantLabel(request)}</TableCell>
                  <TableCell>{formatDateTime(request.requestedAt)}</TableCell>
                  <TableCell>
                    <Chip size="small" color={statusColor(request.status)} label={friendlyLabel(request.status)} />
                  </TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      variant="text"
                      startIcon={<VisibilityRoundedIcon />}
                      onClick={(event) => {
                        event.stopPropagation();
                        void openRequest(request);
                      }}
                    >
                      Review
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Stack>

      <Dialog open={Boolean(selectedRequest)} onClose={() => setSelectedRequest(null)} fullWidth maxWidth="md">
        <DialogTitle>Care access request</DialogTitle>
        <DialogContent dividers>
          {detailLoading ? (
            <Alert severity="info">Loading request details...</Alert>
          ) : selectedRequest ? (
            <Stack spacing={2}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>
                  {selectedRequest.fullName}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {selectedRequest.mobile} · {selectedRequest.email || "No email"} · {formatTenantLabel(selectedRequest)}
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip size="small" label={friendlyLabel(selectedRequest.status)} color={statusColor(selectedRequest.status)} />
                <Chip size="small" label={friendlyLabel(selectedRequest.requestType)} variant="outlined" />
                {selectedRequest.temporaryAccessCode ? (
                  <Chip size="small" label={`Temporary code: ${selectedRequest.temporaryAccessCode}`} color="info" />
                ) : null}
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Requested: {formatDateTime(selectedRequest.requestedAt)} · Reviewed: {formatDateTime(selectedRequest.reviewedAt)}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Approved: {formatDateTime(selectedRequest.approvedAt)} · Activated: {formatDateTime(selectedRequest.activatedAt)} · Revoked: {formatDateTime(selectedRequest.revokedAt)}
              </Typography>
              <TextField
                label="Reason / note"
                value={decisionReason}
                onChange={(event) => setDecisionReason(event.target.value)}
                multiline
                rows={4}
                fullWidth
              />
              {selectedRequest.note ? (
                <Alert severity="info">Request note: {selectedRequest.note}</Alert>
              ) : null}
              {selectedRequest.rejectionReason ? (
                <Alert severity="warning">Rejection / revocation reason: {selectedRequest.rejectionReason}</Alert>
              ) : null}
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ justifyContent: "space-between", px: 3, py: 2 }}>
          <Button onClick={() => setSelectedRequest(null)}>Close</Button>
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {selectedRequest?.status === "REQUESTED" ? (
              <>
                <Button
                  startIcon={<BlockRoundedIcon />}
                  color="inherit"
                  onClick={() => void runDecision("reject")}
                  disabled={decisionPending}
                >
                  Reject
                </Button>
                <Button
                  startIcon={<CheckCircleRoundedIcon />}
                  variant="contained"
                  onClick={() => void runDecision("approve")}
                  disabled={decisionPending}
                >
                  Approve
                </Button>
              </>
            ) : null}
            {selectedRequest?.status === "APPROVED" || selectedRequest?.status === "ACTIVE" ? (
              <Button
                startIcon={<PauseCircleRoundedIcon />}
                variant="outlined"
                color="warning"
                onClick={() => void runDecision("revoke")}
                disabled={decisionPending}
              >
                Revoke
              </Button>
            ) : null}
          </Stack>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
