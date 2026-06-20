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
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { branding } from "../../../branding";
import { useAuth } from "../../../auth/useAuth";
import {
  addCareAiReceptionistStaffNote,
  assignCareAiReceptionistTaskToMe,
  cancelCareAiReceptionistTask,
  getCareAiReceptionistTask,
  listCareAiReceptionistTaskEvents,
  listCareAiReceptionistTasks,
  markCareAiReceptionistTaskInProgress,
  resolveCareAiReceptionistTask,
  resumeCareAiReceptionistTask,
  returnCareAiReceptionistTaskToAi,
  scheduleCareAiReceptionistTaskCallback,
  type CareAiReceptionistTask,
  type CareAiReceptionistTaskDetail,
  type CareAiReceptionistTaskEvent,
  type CareAiReceptionistTaskPriority,
  type CareAiReceptionistTaskSlaStatus,
  type CareAiReceptionistTaskStatus,
  type CareAiReceptionistTaskType,
} from "../../../api/clinicApi";

const statuses: Array<CareAiReceptionistTaskStatus | ""> = ["", "OPEN", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CANCELLED"];
const taskTypes: Array<CareAiReceptionistTaskType | ""> = ["", "HUMAN_HANDOFF", "APPOINTMENT_HANDOFF", "CALLBACK_REQUEST", "ESCALATION"];
const priorities: Array<CareAiReceptionistTaskPriority | ""> = ["", "LOW", "MEDIUM", "HIGH", "URGENT"];

function formatDateTime(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function statusColor(status: CareAiReceptionistTaskStatus): "default" | "success" | "warning" | "error" {
  if (status === "RESOLVED") return "success";
  if (status === "IN_PROGRESS" || status === "ASSIGNED") return "warning";
  if (status === "CANCELLED") return "error";
  return "default";
}

function slaColor(status: CareAiReceptionistTaskSlaStatus): "default" | "warning" | "error" | "success" {
  if (status === "BREACHED") return "error";
  if (status === "OVERDUE" || status === "DUE_SOON") return "warning";
  return "success";
}

function contextValue(context: Record<string, unknown> | undefined, key: string) {
  const value = context?.[key];
  return value == null || value === "" ? "-" : String(value);
}

type ReceptionistQueuePageProps = {
  title?: string;
  description?: string;
  forcedType?: CareAiReceptionistTaskType;
  hideTypeFilter?: boolean;
};

export default function ReceptionistQueuePage({
  title = `${branding.productName} Receptionist Queue`,
  description = "Review escalations, callbacks, and human handoffs raised from AIVA chat and voice conversations.",
  forcedType,
  hideTypeFilter = false,
}: ReceptionistQueuePageProps) {
  const auth = useAuth();
  const canView =
    auth.rolesUpper.includes("CLINIC_ADMIN") ||
    auth.rolesUpper.includes("RECEPTIONIST") ||
    auth.rolesUpper.includes("AUDITOR") ||
    (auth.rolesUpper.includes("PLATFORM_ADMIN") && auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT") && Boolean(auth.tenantId));
  const canMutate =
    auth.rolesUpper.includes("CLINIC_ADMIN") ||
    auth.rolesUpper.includes("RECEPTIONIST") ||
    (auth.rolesUpper.includes("PLATFORM_ADMIN") && auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT") && Boolean(auth.tenantId));

  const [rows, setRows] = React.useState<CareAiReceptionistTask[]>([]);
  const [selectedTaskId, setSelectedTaskId] = React.useState<string | null>(null);
  const [detail, setDetail] = React.useState<CareAiReceptionistTaskDetail | null>(null);
  const [events, setEvents] = React.useState<CareAiReceptionistTaskEvent[]>([]);
  const [statusFilter, setStatusFilter] = React.useState<CareAiReceptionistTaskStatus | "">("");
  const [typeFilter, setTypeFilter] = React.useState<CareAiReceptionistTaskType | "">(forcedType ?? "");
  const [priorityFilter, setPriorityFilter] = React.useState<CareAiReceptionistTaskPriority | "">("");
  const [assignedToMe, setAssignedToMe] = React.useState(false);
  const [overdueOnly, setOverdueOnly] = React.useState(false);
  const [dueSoonOnly, setDueSoonOnly] = React.useState(false);
  const [resolutionNotes, setResolutionNotes] = React.useState("");
  const [staffNote, setStaffNote] = React.useState("");
  const [callbackTimePreference, setCallbackTimePreference] = React.useState("");
  const [callbackDueAt, setCallbackDueAt] = React.useState("");
  const [loading, setLoading] = React.useState(true);
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);

  const loadRows = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await listCareAiReceptionistTasks(auth.accessToken, auth.tenantId, {
        status: statusFilter,
        type: forcedType ?? typeFilter,
        priority: priorityFilter,
        assignedToMe,
        overdueOnly,
        dueSoonOnly,
      });
      setRows(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load receptionist queue.");
    } finally {
      setLoading(false);
    }
  }, [assignedToMe, auth.accessToken, auth.tenantId, dueSoonOnly, forcedType, overdueOnly, priorityFilter, statusFilter, typeFilter]);

  const openDetail = React.useCallback(async (taskId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSelectedTaskId(taskId);
    setError(null);
    try {
      const [taskDetail, taskEvents] = await Promise.all([
        getCareAiReceptionistTask(auth.accessToken, auth.tenantId, taskId),
        listCareAiReceptionistTaskEvents(auth.accessToken, auth.tenantId, taskId),
      ]);
      setDetail(taskDetail);
      setEvents(taskEvents);
      setCallbackTimePreference(taskDetail.task.callbackTimePref ?? "");
      setCallbackDueAt(taskDetail.task.callbackDueAt ? taskDetail.task.callbackDueAt.slice(0, 16) : "");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load task details.");
    }
  }, [auth.accessToken, auth.tenantId]);

  const closeDetail = React.useCallback(() => {
    setSelectedTaskId(null);
    setDetail(null);
    setEvents([]);
    setResolutionNotes("");
    setStaffNote("");
    setCallbackTimePreference("");
    setCallbackDueAt("");
  }, []);

  React.useEffect(() => {
    void loadRows();
  }, [loadRows]);

  React.useEffect(() => {
    const handle = window.setInterval(() => {
      void loadRows();
      if (selectedTaskId) {
        void openDetail(selectedTaskId);
      }
    }, 30000);
    return () => window.clearInterval(handle);
  }, [loadRows, openDetail, selectedTaskId]);

  const handleMutation = React.useCallback(async (action: "assign" | "progress" | "resume" | "return" | "resolve" | "cancel" | "note" | "callback", taskId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setSubmitting(true);
    setError(null);
    setSuccess(null);
    try {
      if (action === "assign") {
        await assignCareAiReceptionistTaskToMe(auth.accessToken, auth.tenantId, taskId);
        setSuccess("Task assigned to you.");
      } else if (action === "progress") {
        await markCareAiReceptionistTaskInProgress(auth.accessToken, auth.tenantId, taskId);
        setSuccess("Task marked in progress.");
      } else if (action === "resume") {
        await resumeCareAiReceptionistTask(auth.accessToken, auth.tenantId, taskId);
        setSuccess("Task resumed with AIVA context.");
      } else if (action === "return") {
        await returnCareAiReceptionistTaskToAi(auth.accessToken, auth.tenantId, taskId);
        setSuccess("Conversation returned to AIVA.");
      } else if (action === "resolve") {
        await resolveCareAiReceptionistTask(auth.accessToken, auth.tenantId, taskId, resolutionNotes);
        setSuccess("Task resolved.");
      } else if (action === "cancel") {
        await cancelCareAiReceptionistTask(auth.accessToken, auth.tenantId, taskId, resolutionNotes);
        setSuccess("Task cancelled.");
      } else if (action === "note") {
        await addCareAiReceptionistStaffNote(auth.accessToken, auth.tenantId, taskId, staffNote);
        setStaffNote("");
        setSuccess("Staff note added.");
      } else {
        await scheduleCareAiReceptionistTaskCallback(
          auth.accessToken,
          auth.tenantId,
          taskId,
          callbackTimePreference || null,
          callbackDueAt ? new Date(callbackDueAt).toISOString() : null,
        );
        setSuccess("Callback updated.");
      }
      await loadRows();
      if (selectedTaskId === taskId) {
        await openDetail(taskId);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Task update failed.");
    } finally {
      setSubmitting(false);
    }
  }, [auth.accessToken, auth.tenantId, callbackDueAt, callbackTimePreference, loadRows, openDetail, resolutionNotes, selectedTaskId, staffNote]);

  if (!auth.tenantId) {
    return <Alert severity="info">Select a tenant to use the receptionist queue.</Alert>;
  }
  if (!canView) {
    return <Alert severity="error">You do not have access to the receptionist queue.</Alert>;
  }

  const openCount = rows.filter((row) => row.status === "OPEN" || row.status === "ASSIGNED" || row.status === "IN_PROGRESS").length;
  const overdueCount = rows.filter((row) => row.slaStatus === "OVERDUE" || row.slaStatus === "BREACHED").length;
  const dueSoonCount = rows.filter((row) => row.slaStatus === "DUE_SOON").length;
  const workflowContext = detail?.resumeContext.workflow?.context as Record<string, unknown> | undefined;

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>{title}</Typography>
        <Typography variant="body2" color="text.secondary">
          {description}
        </Typography>
      </Box>
      <Stack direction="row" spacing={1} flexWrap="wrap">
        <Chip label={`Open ${openCount}`} />
        <Chip label={`Due Soon ${dueSoonCount}`} color={dueSoonCount > 0 ? "warning" : "default"} />
        <Chip label={`Overdue ${overdueCount}`} color={overdueCount > 0 ? "error" : "default"} />
      </Stack>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}
      <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel>Status</InputLabel>
          <Select value={statusFilter} label="Status" onChange={(event) => setStatusFilter(event.target.value as CareAiReceptionistTaskStatus | "")}>
            {statuses.map((status) => <MenuItem key={status || "ALL"} value={status}>{status || "All"}</MenuItem>)}
          </Select>
        </FormControl>
        {hideTypeFilter ? null : (
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel>Type</InputLabel>
            <Select value={typeFilter} label="Type" onChange={(event) => setTypeFilter(event.target.value as CareAiReceptionistTaskType | "")}>
              {taskTypes.map((type) => <MenuItem key={type || "ALL"} value={type}>{type || "All"}</MenuItem>)}
            </Select>
          </FormControl>
        )}
        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel>Priority</InputLabel>
          <Select value={priorityFilter} label="Priority" onChange={(event) => setPriorityFilter(event.target.value as CareAiReceptionistTaskPriority | "")}>
            {priorities.map((priority) => <MenuItem key={priority || "ALL"} value={priority}>{priority || "All"}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel>Assignment</InputLabel>
          <Select value={assignedToMe ? "ME" : "ALL"} label="Assignment" onChange={(event) => setAssignedToMe(event.target.value === "ME")}>
            <MenuItem value="ALL">All</MenuItem>
            <MenuItem value="ME">My Tasks</MenuItem>
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel>SLA</InputLabel>
          <Select value={overdueOnly ? "OVERDUE" : dueSoonOnly ? "DUE_SOON" : "ALL"} label="SLA" onChange={(event) => {
            const value = event.target.value;
            setOverdueOnly(value === "OVERDUE");
            setDueSoonOnly(value === "DUE_SOON");
          }}>
            <MenuItem value="ALL">All</MenuItem>
            <MenuItem value="DUE_SOON">Due Soon</MenuItem>
            <MenuItem value="OVERDUE">Overdue</MenuItem>
          </Select>
        </FormControl>
        <Button variant="outlined" onClick={() => void loadRows()} disabled={loading}>Refresh</Button>
      </Stack>
      {loading ? <Alert severity="info">Loading receptionist tasks…</Alert> : null}
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Created At</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>SLA</TableCell>
            <TableCell>Priority</TableCell>
            <TableCell>Patient</TableCell>
            <TableCell>Channel</TableCell>
            <TableCell>Reason</TableCell>
            <TableCell>Assigned To</TableCell>
            <TableCell>Due At</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((task) => (
            <TableRow key={task.id} hover>
              <TableCell>{formatDateTime(task.createdAt)}</TableCell>
              <TableCell>{task.taskType}</TableCell>
              <TableCell><Chip size="small" label={task.status} color={statusColor(task.status)} /></TableCell>
              <TableCell><Chip size="small" label={task.slaStatus} color={slaColor(task.slaStatus)} /></TableCell>
              <TableCell>{task.priority}</TableCell>
              <TableCell>{task.patientId || "-"}</TableCell>
              <TableCell>{task.channel || "-"}</TableCell>
              <TableCell>{task.reason || "-"}</TableCell>
              <TableCell>{task.assignedUserId || "-"}</TableCell>
              <TableCell>{formatDateTime(task.dueAt || task.callbackDueAt)}</TableCell>
              <TableCell align="right">
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button size="small" onClick={() => void openDetail(task.id)}>View Details</Button>
                  {canMutate ? <Button size="small" onClick={() => void handleMutation("assign", task.id)} disabled={submitting}>Assign</Button> : null}
                </Stack>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Dialog open={Boolean(selectedTaskId)} onClose={closeDetail} maxWidth="lg" fullWidth>
        <DialogTitle>Receptionist Task Details</DialogTitle>
        <DialogContent dividers>
          {detail ? (
            <Stack spacing={2}>
              <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle2">Task</Typography>
                  <Typography variant="body2">Type: {detail.task.taskType}</Typography>
                  <Typography variant="body2">Status: {detail.task.status}</Typography>
                  <Typography variant="body2">Priority: {detail.task.priority}</Typography>
                  <Typography variant="body2">SLA: {detail.task.slaStatus}</Typography>
                  <Typography variant="body2">Handling: {detail.task.handlingMode}</Typography>
                  <Typography variant="body2">Patient: {detail.task.patientId || "-"}</Typography>
                  <Typography variant="body2">Lead: {detail.task.leadId || "-"}</Typography>
                  <Typography variant="body2">Appointment: {detail.task.appointmentId || "-"}</Typography>
                  <Typography variant="body2">Conversation: {detail.task.conversationId || "-"}</Typography>
                  <Typography variant="body2">Workflow: {detail.task.workflowId || "-"}</Typography>
                  <Typography variant="body2">Latest Message: {detail.task.latestUserMessage || "-"}</Typography>
                  <Typography variant="body2">Due At: {formatDateTime(detail.task.dueAt || detail.task.callbackDueAt)}</Typography>
                </Box>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle2">Resume Context</Typography>
                  <Typography variant="body2">Summary: {detail.resumeContext.conversation?.summary || "-"}</Typography>
                  <Typography variant="body2">Workflow State: {detail.resumeContext.workflow?.state || "-"}</Typography>
                  <Typography variant="body2">Recommended Prompt: {detail.resumeContext.recommendedNextPrompt || "-"}</Typography>
                  <Typography variant="body2">Doctor: {contextValue(workflowContext, "doctorName")}</Typography>
                  <Typography variant="body2">Date: {contextValue(workflowContext, "preferredDate")}</Typography>
                  <Typography variant="body2">Time Preference: {contextValue(workflowContext, "preferredTimeWindow")}</Typography>
                  <Typography variant="body2">Slot: {contextValue(workflowContext, "selectedSlot")}</Typography>
                  <Typography variant="body2">Pending Confirmation: {contextValue(workflowContext, "activeConfirmationScopeKey")}</Typography>
                </Box>
              </Stack>
              <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle2">Actions</Typography>
                  <Stack spacing={1}>
                    {canMutate ? <Button variant="outlined" onClick={() => detail && void handleMutation("assign", detail.task.id)} disabled={submitting}>Assign to Me</Button> : null}
                    {canMutate ? <Button variant="outlined" onClick={() => detail && void handleMutation("progress", detail.task.id)} disabled={submitting}>Mark In Progress</Button> : null}
                    {canMutate ? <Button variant="outlined" onClick={() => detail && void handleMutation("resume", detail.task.id)} disabled={submitting}>Resume With AIVA</Button> : null}
                    {canMutate ? <Button variant="outlined" onClick={() => detail && void handleMutation("return", detail.task.id)} disabled={submitting}>Return To AI</Button> : null}
                  </Stack>
                </Box>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle2">Staff Note</Typography>
                  <Stack spacing={1}>
                    <TextField size="small" label="Staff Note" value={staffNote} onChange={(event) => setStaffNote(event.target.value)} multiline minRows={2} />
                    {canMutate ? <Button variant="outlined" onClick={() => detail && void handleMutation("note", detail.task.id)} disabled={submitting || !staffNote.trim()}>Send Message / Add Staff Note</Button> : null}
                  </Stack>
                </Box>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle2">Callback</Typography>
                  <Stack spacing={1}>
                    <TextField size="small" label="Callback Preference" value={callbackTimePreference} onChange={(event) => setCallbackTimePreference(event.target.value)} />
                    <TextField size="small" type="datetime-local" label="Callback Due At" InputLabelProps={{ shrink: true }} value={callbackDueAt} onChange={(event) => setCallbackDueAt(event.target.value)} />
                    {canMutate ? <Button variant="outlined" onClick={() => detail && void handleMutation("callback", detail.task.id)} disabled={submitting}>Schedule Callback</Button> : null}
                  </Stack>
                </Box>
              </Stack>
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>Transcript</Typography>
                <Stack spacing={1}>
                  {detail.messages.length === 0 ? <Typography variant="body2" color="text.secondary">No transcript linked.</Typography> : null}
                  {detail.messages.map((message) => (
                    <Box key={message.id} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 1.5, p: 1.5 }}>
                      <Typography variant="caption" color="text.secondary">{message.speaker} · {message.channel} · {formatDateTime(message.createdAt)}</Typography>
                      <Typography variant="body2">{message.content}</Typography>
                    </Box>
                  ))}
                </Stack>
              </Box>
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>Task Events</Typography>
                <Stack spacing={1}>
                  {events.length === 0 ? <Typography variant="body2" color="text.secondary">No events yet.</Typography> : null}
                  {events.map((event) => (
                    <Box key={event.id} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 1.5, p: 1.5 }}>
                      <Typography variant="caption" color="text.secondary">{event.eventType} · {formatDateTime(event.createdAt)}</Typography>
                      <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>{event.payloadJson}</Typography>
                    </Box>
                  ))}
                </Stack>
              </Box>
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>Close Task</Typography>
                <Stack spacing={1}>
                  <TextField size="small" label="Resolution Notes" value={resolutionNotes} onChange={(event) => setResolutionNotes(event.target.value)} multiline minRows={2} />
                  <Stack direction="row" spacing={1}>
                    {canMutate ? <Button variant="contained" onClick={() => detail && void handleMutation("resolve", detail.task.id)} disabled={submitting}>Resolve</Button> : null}
                    {canMutate ? <Button color="error" variant="outlined" onClick={() => detail && void handleMutation("cancel", detail.task.id)} disabled={submitting}>Cancel</Button> : null}
                  </Stack>
                </Stack>
              </Box>
            </Stack>
          ) : <Alert severity="info">Loading task details…</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDetail}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
