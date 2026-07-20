import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useAuth } from "../../../auth/useAuth";
import {
  listActiveCareAiConversations,
  listCareAiConversationMessages,
  type CareAiConversationMessage,
  type CareAiConversationSummary,
} from "../../../api/clinicApi";

function formatDateTime(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function ActiveConversationsPage() {
  const auth = useAuth();
  const [rows, setRows] = React.useState<CareAiConversationSummary[]>([]);
  const [messages, setMessages] = React.useState<CareAiConversationMessage[]>([]);
  const [selected, setSelected] = React.useState<CareAiConversationSummary | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  const canView = auth.hasPermission("engage.reception.operate") || auth.hasPermission("engage.view");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      setRows(await listActiveCareAiConversations(auth.accessToken, auth.tenantId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load active conversations.");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId]);

  const openConversation = React.useCallback(async (row: CareAiConversationSummary) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSelected(row);
    setError(null);
    try {
      setMessages(await listCareAiConversationMessages(auth.accessToken, auth.tenantId, row.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load conversation transcript.");
    }
  }, [auth.accessToken, auth.tenantId]);

  React.useEffect(() => {
    void load();
  }, [load]);

  if (!auth.tenantId) {
    return <Alert severity="info">Select a tenant to view active AIVA conversations.</Alert>;
  }
  if (!canView) {
    return <Alert severity="error">You do not have access to AIVA operational conversations.</Alert>;
  }

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>AI Receptionist Active Conversations</Typography>
        <Typography variant="body2" color="text.secondary">
          Review active AIVA conversations across chat and voice before taking over a callback, escalation, or handoff.
        </Typography>
      </Box>
      {error ? <Alert severity="error">{error}</Alert> : null}
      <Stack direction="row" spacing={1}>
        <Chip label={`Active ${rows.length}`} />
        <Button variant="outlined" onClick={() => void load()} disabled={loading}>Refresh</Button>
      </Stack>
      {loading ? <Alert severity="info">Loading active conversations…</Alert> : null}
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Updated</TableCell>
            <TableCell>Channel</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Patient</TableCell>
            <TableCell>Workflow</TableCell>
            <TableCell>Summary</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id} hover>
              <TableCell>{formatDateTime(row.updatedAt)}</TableCell>
              <TableCell>{row.channel}</TableCell>
              <TableCell>{row.status}</TableCell>
              <TableCell>{row.patientId ? "Patient record" : "-"}</TableCell>
              <TableCell>{row.currentWorkflowId ? "Workflow record" : "-"}</TableCell>
              <TableCell>{row.summary || "-"}</TableCell>
              <TableCell align="right">
                <Button size="small" onClick={() => void openConversation(row)}>View Transcript</Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Dialog open={Boolean(selected)} onClose={() => setSelected(null)} maxWidth="md" fullWidth>
        <DialogTitle>Active Conversation</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={1.5}>
            <Typography variant="body2">Conversation: Active record</Typography>
            <Typography variant="body2">Channel: {selected?.channel}</Typography>
            <Typography variant="body2">Patient: {selected?.patientId ? "Patient record" : "-"}</Typography>
            <Typography variant="body2">Summary: {selected?.summary || "-"}</Typography>
            {messages.length === 0 ? <Typography variant="body2" color="text.secondary">No messages available.</Typography> : null}
            {messages.map((message) => (
              <Box key={message.id} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 1.5, p: 1.5 }}>
                <Typography variant="caption" color="text.secondary">{message.speaker} · {message.channel} · {formatDateTime(message.createdAt)}</Typography>
                <Typography variant="body2">{message.content}</Typography>
              </Box>
            ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelected(null)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
