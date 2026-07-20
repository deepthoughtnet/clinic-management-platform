import * as React from "react";
import { useSearchParams } from "react-router-dom";
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
import { useAuth } from "../../../auth/useAuth";
import { ENGAGE_PRODUCT_NAME } from "../shared/engageBranding";
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
const taskTypes: Array<CareAiReceptionistTaskType | ""> = ["", "CALLBACK_REQUEST", "ESCALATION", "APPOINTMENT_HANDOFF", "HUMAN_HANDOFF"];
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
  syncUrlState?: boolean;
};

function normalizeTaskTypeQuery(value: string | null | undefined): CareAiReceptionistTaskType | "" {
  const raw = (value || "").trim().toLowerCase();
  if (raw === "callback" || raw === "callback_request") return "CALLBACK_REQUEST";
  if (raw === "escalation") return "ESCALATION";
  if (raw === "appointment-handoff" || raw === "appointment_handoff") return "APPOINTMENT_HANDOFF";
  if (raw === "human-handoff" || raw === "human_handoff") return "HUMAN_HANDOFF";
  return "";
}

function normalizeTaskStatusQuery(value: string | null | undefined): CareAiReceptionistTaskStatus | "" {
  const raw = (value || "").trim().toLowerCase();
  if (raw === "open") return "OPEN";
  if (raw === "assigned") return "ASSIGNED";
  if (raw === "in-progress" || raw === "in_progress") return "IN_PROGRESS";
  if (raw === "resolved") return "RESOLVED";
  if (raw === "cancelled" || raw === "canceled") return "CANCELLED";
  return "";
}

function normalizeTaskPriorityQuery(value: string | null | undefined): CareAiReceptionistTaskPriority | "" {
  const raw = (value || "").trim().toLowerCase();
  if (raw === "low") return "LOW";
  if (raw === "medium") return "MEDIUM";
  if (raw === "high") return "HIGH";
  if (raw === "urgent") return "URGENT";
  return "";
}

function taskTypeQueryValue(type: CareAiReceptionistTaskType | "") {
  if (type === "CALLBACK_REQUEST") return "callback";
  if (type === "ESCALATION") return "escalation";
  if (type === "APPOINTMENT_HANDOFF") return "appointment-handoff";
  if (type === "HUMAN_HANDOFF") return "human-handoff";
  return "";
}

function taskStatusQueryValue(status: CareAiReceptionistTaskStatus | "") {
  if (status === "OPEN") return "open";
  if (status === "ASSIGNED") return "assigned";
  if (status === "IN_PROGRESS") return "in-progress";
  if (status === "RESOLVED") return "resolved";
  if (status === "CANCELLED") return "cancelled";
  return "";
}

function taskPriorityQueryValue(priority: CareAiReceptionistTaskPriority | "") {
  if (priority === "LOW") return "low";
  if (priority === "MEDIUM") return "medium";
  if (priority === "HIGH") return "high";
  if (priority === "URGENT") return "urgent";
  return "";
}

function taskTypeLabel(type: CareAiReceptionistTaskType | "ALL") {
  if (type === "CALLBACK_REQUEST") return "Callback";
  if (type === "ESCALATION") return "Escalation";
  if (type === "APPOINTMENT_HANDOFF") return "Appointment Handoff";
  if (type === "HUMAN_HANDOFF") return "Human Handoff";
  if (type === "ALL") return "All";
  return type;
}

function taskStatusLabel(status: CareAiReceptionistTaskStatus | "ALL") {
  if (status === "OPEN") return "Open";
  if (status === "ASSIGNED") return "Assigned";
  if (status === "IN_PROGRESS") return "In Progress";
  if (status === "RESOLVED") return "Resolved";
  if (status === "CANCELLED") return "Cancelled";
  if (status === "ALL") return "All";
  return status;
}

function taskPriorityLabel(priority: CareAiReceptionistTaskPriority | "ALL") {
  if (priority === "LOW") return "Low";
  if (priority === "MEDIUM") return "Medium";
  if (priority === "HIGH") return "High";
  if (priority === "URGENT") return "Urgent";
  if (priority === "ALL") return "All";
  return priority;
}

function slaLabel(status: CareAiReceptionistTaskSlaStatus) {
  if (status === "ON_TIME") return "On Time";
  if (status === "DUE_SOON") return "Due Soon";
  if (status === "OVERDUE") return "Overdue";
  if (status === "BREACHED") return "Breached";
  return status;
}

export default function ReceptionistQueuePage({
  title = `${ENGAGE_PRODUCT_NAME} Receptionist Queue`,
  description = "Review escalations, callbacks, and human handoffs raised from AIVA chat and voice conversations.",
  forcedType,
  hideTypeFilter = false,
  syncUrlState = false,
}: ReceptionistQueuePageProps) {
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const canView = auth.hasPermission("engage.reception.operate") || auth.hasPermission("engage.view");
  const canMutate = auth.hasPermission("engage.reception.operate");

  const [rows, setRows] = React.useState<CareAiReceptionistTask[]>([]);
  const [selectedTaskId, setSelectedTaskId] = React.useState<string | null>(syncUrlState ? searchParams.get("taskId") : null);
  const [detail, setDetail] = React.useState<CareAiReceptionistTaskDetail | null>(null);
  const [events, setEvents] = React.useState<CareAiReceptionistTaskEvent[]>([]);
  const [statusFilter, setStatusFilter] = React.useState<CareAiReceptionistTaskStatus | "">(syncUrlState ? normalizeTaskStatusQuery(searchParams.get("status")) : "");
  const [typeFilter, setTypeFilter] = React.useState<CareAiReceptionistTaskType | "">(forcedType ?? (syncUrlState ? normalizeTaskTypeQuery(searchParams.get("type")) : ""));
  const [priorityFilter, setPriorityFilter] = React.useState<CareAiReceptionistTaskPriority | "">(syncUrlState ? normalizeTaskPriorityQuery(searchParams.get("priority")) : "");
  const [assignedToMe, setAssignedToMe] = React.useState(syncUrlState ? searchParams.get("assignedToMe") === "true" : false);
  const [overdueOnly, setOverdueOnly] = React.useState(syncUrlState ? searchParams.get("overdueOnly") === "true" : false);
  const [dueSoonOnly, setDueSoonOnly] = React.useState(syncUrlState ? searchParams.get("dueSoonOnly") === "true" : false);
  const [searchText, setSearchText] = React.useState(syncUrlState ? (searchParams.get("search") || "") : "");
  const [resolutionNotes, setResolutionNotes] = React.useState("");
  const [staffNote, setStaffNote] = React.useState("");
  const [callbackTimePreference, setCallbackTimePreference] = React.useState("");
  const [callbackDueAt, setCallbackDueAt] = React.useState("");
  const [loading, setLoading] = React.useState(true);
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!syncUrlState) return;
    const nextType = forcedType ?? normalizeTaskTypeQuery(searchParams.get("type"));
    const nextStatus = normalizeTaskStatusQuery(searchParams.get("status"));
    const nextPriority = normalizeTaskPriorityQuery(searchParams.get("priority"));
    const nextAssignedToMe = searchParams.get("assignedToMe") === "true";
    const nextOverdueOnly = searchParams.get("overdueOnly") === "true";
    const nextDueSoonOnly = searchParams.get("dueSoonOnly") === "true";
    const nextSearchText = searchParams.get("search") || "";
    const nextSelectedTaskId = searchParams.get("taskId") || null;

    setTypeFilter((current) => (current === nextType ? current : nextType));
    setStatusFilter((current) => (current === nextStatus ? current : nextStatus));
    setPriorityFilter((current) => (current === nextPriority ? current : nextPriority));
    setAssignedToMe(nextAssignedToMe);
    setOverdueOnly(nextOverdueOnly);
    setDueSoonOnly(nextDueSoonOnly);
    setSearchText(nextSearchText);
    setSelectedTaskId((current) => (current === nextSelectedTaskId ? current : nextSelectedTaskId));
  }, [forcedType, searchParams, syncUrlState]);

  const updateSearchParams = React.useCallback((next: Record<string, string | null>, replace = false) => {
    const params = new URLSearchParams(searchParams);
    for (const [key, value] of Object.entries(next)) {
      if (value == null || value === "") {
        params.delete(key);
      } else {
        params.set(key, value);
      }
    }
    if (!params.get("taskId")) {
      params.delete("taskId");
    }
    setSearchParams(params, { replace });
  }, [searchParams, setSearchParams]);

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

  const loadDetail = React.useCallback(async (taskId: string) => {
    if (!auth.accessToken || !auth.tenantId) return;
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

  const openDetail = React.useCallback((taskId: string) => {
    if (syncUrlState) {
      updateSearchParams({ taskId }, false);
      return;
    }
    setSelectedTaskId(taskId);
    void loadDetail(taskId);
  }, [loadDetail, syncUrlState, updateSearchParams]);

  const closeDetail = React.useCallback(() => {
    if (syncUrlState) {
      updateSearchParams({ taskId: null }, false);
    }
    setSelectedTaskId(null);
    setDetail(null);
    setEvents([]);
    setResolutionNotes("");
    setStaffNote("");
    setCallbackTimePreference("");
    setCallbackDueAt("");
  }, [syncUrlState, updateSearchParams]);

  React.useEffect(() => {
    if (!syncUrlState) return;
    if (!selectedTaskId) {
      setDetail(null);
      setEvents([]);
      setResolutionNotes("");
      setStaffNote("");
      setCallbackTimePreference("");
      setCallbackDueAt("");
      return;
    }
    void loadDetail(selectedTaskId);
  }, [loadDetail, selectedTaskId, syncUrlState]);

  React.useEffect(() => {
    void loadRows();
  }, [loadRows]);

  React.useEffect(() => {
    if (!selectedTaskId) return;
    const handle = window.setInterval(() => {
      void loadRows();
      void loadDetail(selectedTaskId);
    }, 30000);
    return () => window.clearInterval(handle);
  }, [loadDetail, loadRows, selectedTaskId]);

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
        await loadDetail(taskId);
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

  const filteredRows = React.useMemo(() => {
    const query = searchText.trim().toLowerCase();
    return rows.filter((row) => {
      if (!query) return true;
      const haystack = [
        row.patientId,
        row.leadId,
        row.appointmentId,
        row.conversationId,
        row.workflowId,
        row.reason,
        row.latestUserMessage,
        row.channel,
        row.assignedUserId,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return haystack.includes(query);
    });
  }, [rows, searchText]);

  const openCount = filteredRows.filter((row) => row.status === "OPEN" || row.status === "ASSIGNED" || row.status === "IN_PROGRESS").length;
  const overdueCount = filteredRows.filter((row) => row.slaStatus === "OVERDUE" || row.slaStatus === "BREACHED").length;
  const dueSoonCount = filteredRows.filter((row) => row.slaStatus === "DUE_SOON").length;
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
      <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} flexWrap="wrap">
        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel>Status</InputLabel>
          <Select
            value={statusFilter}
            label="Status"
            onChange={(event) => {
              const next = event.target.value as CareAiReceptionistTaskStatus | "";
              setStatusFilter(next);
              if (syncUrlState) updateSearchParams({ status: taskStatusQueryValue(next) || null });
            }}
          >
            {statuses.map((status) => <MenuItem key={status || "ALL"} value={status}>{taskStatusLabel(status || "ALL")}</MenuItem>)}
          </Select>
        </FormControl>
        {hideTypeFilter ? null : (
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel>Type</InputLabel>
            <Select
              value={typeFilter}
              label="Type"
              onChange={(event) => {
                const next = event.target.value as CareAiReceptionistTaskType | "";
                setTypeFilter(next);
                if (syncUrlState) updateSearchParams({ type: taskTypeQueryValue(next) || null });
              }}
            >
              {taskTypes.map((type) => <MenuItem key={type || "ALL"} value={type}>{taskTypeLabel(type || "ALL")}</MenuItem>)}
            </Select>
          </FormControl>
        )}
        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel>Priority</InputLabel>
          <Select
            value={priorityFilter}
            label="Priority"
            onChange={(event) => {
              const next = event.target.value as CareAiReceptionistTaskPriority | "";
              setPriorityFilter(next);
              if (syncUrlState) updateSearchParams({ priority: taskPriorityQueryValue(next) || null });
            }}
          >
            {priorities.map((priority) => <MenuItem key={priority || "ALL"} value={priority}>{taskPriorityLabel(priority || "ALL")}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel>Assignment</InputLabel>
          <Select
            value={assignedToMe ? "ME" : "ALL"}
            label="Assignment"
            onChange={(event) => {
              const next = event.target.value === "ME";
              setAssignedToMe(next);
              if (syncUrlState) updateSearchParams({ assignedToMe: next ? "true" : null });
            }}
          >
            <MenuItem value="ALL">All</MenuItem>
            <MenuItem value="ME">My Tasks</MenuItem>
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel>SLA</InputLabel>
          <Select
            value={overdueOnly ? "OVERDUE" : dueSoonOnly ? "DUE_SOON" : "ALL"}
            label="SLA"
            onChange={(event) => {
              const value = event.target.value;
              const nextOverdue = value === "OVERDUE";
              const nextDueSoon = value === "DUE_SOON";
              setOverdueOnly(nextOverdue);
              setDueSoonOnly(nextDueSoon);
              if (syncUrlState) {
                updateSearchParams({
                  overdueOnly: nextOverdue ? "true" : null,
                  dueSoonOnly: nextDueSoon ? "true" : null,
                });
              }
            }}
          >
            <MenuItem value="ALL">All</MenuItem>
            <MenuItem value="DUE_SOON">Due Soon</MenuItem>
            <MenuItem value="OVERDUE">Overdue</MenuItem>
          </Select>
        </FormControl>
        <TextField
          size="small"
          label="Search"
          placeholder="Patient, reason, contact"
          value={searchText}
          onChange={(event) => {
            const next = event.target.value;
            setSearchText(next);
            if (syncUrlState) updateSearchParams({ search: next || null });
          }}
          sx={{ minWidth: 240, flex: 1 }}
        />
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
          {filteredRows.map((task) => (
            <TableRow key={task.id} hover>
              <TableCell>{formatDateTime(task.createdAt)}</TableCell>
              <TableCell>{taskTypeLabel(task.taskType)}</TableCell>
              <TableCell><Chip size="small" label={taskStatusLabel(task.status)} color={statusColor(task.status)} /></TableCell>
              <TableCell><Chip size="small" label={slaLabel(task.slaStatus)} color={slaColor(task.slaStatus)} /></TableCell>
              <TableCell>{taskPriorityLabel(task.priority)}</TableCell>
              <TableCell>{task.patientId ? "Patient record" : "-"}</TableCell>
              <TableCell>{task.channel || "-"}</TableCell>
              <TableCell>{task.reason || "-"}</TableCell>
              <TableCell>{task.assignedUserId ? "Assigned" : "-"}</TableCell>
              <TableCell>{formatDateTime(task.dueAt || task.callbackDueAt)}</TableCell>
              <TableCell align="right">
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button size="small" onClick={() => void openDetail(task.id)}>View Details</Button>
                  {canMutate ? <Button size="small" onClick={() => void handleMutation("assign", task.id)} disabled={submitting}>Assign</Button> : null}
                </Stack>
              </TableCell>
            </TableRow>
          ))}
          {filteredRows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={11}>
                <Alert severity="info">No receptionist tasks match the selected filters.</Alert>
              </TableCell>
            </TableRow>
          ) : null}
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
                  <Typography variant="body2">Type: {taskTypeLabel(detail.task.taskType)}</Typography>
                  <Typography variant="body2">Status: {taskStatusLabel(detail.task.status)}</Typography>
                  <Typography variant="body2">Priority: {taskPriorityLabel(detail.task.priority)}</Typography>
                  <Typography variant="body2">SLA: {slaLabel(detail.task.slaStatus)}</Typography>
                  <Typography variant="body2">Handling: {detail.task.handlingMode}</Typography>
                  <Typography variant="body2">Patient: {detail.task.patientId ? "Patient record" : "-"}</Typography>
                  <Typography variant="body2">Lead: {detail.task.leadId ? "Linked lead" : "-"}</Typography>
                  <Typography variant="body2">Appointment: {detail.task.appointmentId ? "Linked appointment" : "-"}</Typography>
                  <Typography variant="body2">Conversation: {detail.task.conversationId ? "Conversation record" : "-"}</Typography>
                  <Typography variant="body2">Workflow: {detail.task.workflowId ? "Workflow record" : "-"}</Typography>
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
