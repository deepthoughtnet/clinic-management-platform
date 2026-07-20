import * as React from "react";
import { useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  Stack,
  Tab,
  Tabs,
  Typography,
} from "@mui/material";
import { useAuth } from "../../../auth/useAuth";
import { hasTenantModule } from "../../../auth/moduleEntitlements";
import {
  getCarePilotAiCallAnalyticsSummary,
  getCarePilotAiCallSchedulerHealth,
  listActiveCareAiConversations,
  listCareAiReceptionistTasks,
  type CareAiReceptionistTask,
  type CareAiReceptionistTaskPriority,
  type CareAiReceptionistTaskSlaStatus,
  type CareAiReceptionistTaskStatus,
  type CareAiReceptionistTaskType,
  type CarePilotAiCallAnalyticsSummary,
  type CarePilotAiCallSchedulerHealth,
  type CareAiConversationSummary,
} from "../../../api/clinicApi";
import AiCallsPage from "../ai-calls/AiCallsPage";
import ActiveConversationsPage from "../receptionist-queue/ActiveConversationsPage";
import ReceptionistQueuePage from "../receptionist-queue/ReceptionistQueuePage";

type AiOperationsTab = "overview" | "calls" | "conversations" | "work-queue";
type WorkQueueType = CareAiReceptionistTaskType | "ALL";
type WorkQueueStatus = CareAiReceptionistTaskStatus | "ALL";
type WorkQueuePriority = CareAiReceptionistTaskPriority | "ALL";

type OverviewState = {
  conversations: CareAiConversationSummary[];
  tasks: CareAiReceptionistTask[];
  aiCallsSummary: CarePilotAiCallAnalyticsSummary | null;
  schedulerHealth: CarePilotAiCallSchedulerHealth | null;
};

const TAB_LABELS: Array<{ value: AiOperationsTab; label: string }> = [
  { value: "overview", label: "Overview" },
  { value: "calls", label: "AI Calls" },
  { value: "conversations", label: "Conversations" },
  { value: "work-queue", label: "Work Queue" },
];

function normalizeTab(value: string | null | undefined): AiOperationsTab {
  const raw = (value || "").trim().toLowerCase();
  if (raw === "calls") return "calls";
  if (raw === "conversations") return "conversations";
  if (raw === "work-queue" || raw === "work_queue" || raw === "queue") return "work-queue";
  return "overview";
}

function normalizeTaskType(value: string | null | undefined): WorkQueueType {
  const raw = (value || "").trim().toLowerCase();
  if (raw === "callback" || raw === "callback_request") return "CALLBACK_REQUEST";
  if (raw === "escalation") return "ESCALATION";
  if (raw === "appointment-handoff" || raw === "appointment_handoff") return "APPOINTMENT_HANDOFF";
  if (raw === "human-handoff" || raw === "human_handoff") return "HUMAN_HANDOFF";
  return "ALL";
}

function normalizeTaskStatus(value: string | null | undefined): WorkQueueStatus {
  const raw = (value || "").trim().toLowerCase();
  if (raw === "open") return "OPEN";
  if (raw === "assigned") return "ASSIGNED";
  if (raw === "in-progress" || raw === "in_progress") return "IN_PROGRESS";
  if (raw === "resolved") return "RESOLVED";
  if (raw === "cancelled" || raw === "canceled") return "CANCELLED";
  return "ALL";
}

function normalizeTaskPriority(value: string | null | undefined): WorkQueuePriority {
  const raw = (value || "").trim().toLowerCase();
  if (raw === "low") return "LOW";
  if (raw === "medium") return "MEDIUM";
  if (raw === "high") return "HIGH";
  if (raw === "urgent") return "URGENT";
  return "ALL";
}

function taskTypeLabel(type: CareAiReceptionistTaskType | "ALL") {
  if (type === "CALLBACK_REQUEST") return "Callback";
  if (type === "ESCALATION") return "Escalation";
  if (type === "APPOINTMENT_HANDOFF") return "Appointment Handoff";
  if (type === "HUMAN_HANDOFF") return "Human Handoff";
  return "All";
}

function taskStatusLabel(status: CareAiReceptionistTaskStatus | "ALL") {
  if (status === "OPEN") return "Open";
  if (status === "ASSIGNED") return "Assigned";
  if (status === "IN_PROGRESS") return "In Progress";
  if (status === "RESOLVED") return "Resolved";
  if (status === "CANCELLED") return "Cancelled";
  return "All";
}

function taskPriorityLabel(priority: CareAiReceptionistTaskPriority | "ALL") {
  if (priority === "LOW") return "Low";
  if (priority === "MEDIUM") return "Medium";
  if (priority === "HIGH") return "High";
  if (priority === "URGENT") return "Urgent";
  return "All";
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function taskIsOverdue(task: CareAiReceptionistTask) {
  return task.slaStatus === "OVERDUE" || task.slaStatus === "BREACHED";
}

function taskIsOpen(task: CareAiReceptionistTask) {
  return task.status === "OPEN" || task.status === "ASSIGNED" || task.status === "IN_PROGRESS";
}

function tabLabel(label: string, count?: number | null) {
  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <span>{label}</span>
      {typeof count === "number" ? <Chip size="small" variant="outlined" label={count} sx={{ height: 22 }} /> : null}
    </Stack>
  );
}

function summaryCard(title: string, value: number | string, subtext?: string) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="caption" color="text.secondary">{title}</Typography>
        <Typography variant="h5" sx={{ fontWeight: 900 }}>{value}</Typography>
        {subtext ? <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{subtext}</Typography> : null}
      </CardContent>
    </Card>
  );
}

function shortcutButton(
  label: string,
  params: Record<string, string>,
  onJump: (next: Record<string, string>) => void,
) {
  return (
    <Button variant="outlined" onClick={() => onJump(params)}>
      {label}
    </Button>
  );
}

function OverviewTab({
  loading,
  error,
  overview,
  aiCopilotEnabled,
  canViewCalls,
  canViewOperationalQueues,
  onRefresh,
  onJump,
}: {
  loading: boolean;
  error: string | null;
  overview: OverviewState | null;
  aiCopilotEnabled: boolean;
  canViewCalls: boolean;
  canViewOperationalQueues: boolean;
  onRefresh: () => void;
  onJump: (next: Record<string, string>) => void;
}) {
  const conversations = canViewOperationalQueues ? (overview?.conversations ?? []) : [];
  const tasks = canViewOperationalQueues ? (overview?.tasks ?? []) : [];
  const openCallbacks = tasks.filter((task) => task.taskType === "CALLBACK_REQUEST").length;
  const openEscalations = tasks.filter((task) => task.taskType === "ESCALATION").length;
  const appointmentHandoffs = tasks.filter((task) => task.taskType === "APPOINTMENT_HANDOFF").length;
  const overdueTasks = tasks.filter(taskIsOverdue).length;
  const unassignedTasks = tasks.filter((task) => !task.assignedUserId).length;
  const openTasks = tasks.filter(taskIsOpen).length;
  const recentTasks = [...tasks].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 5);
  const recentConversations = [...conversations].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 5);
  const recentActivity = [
    ...recentTasks.map((task) => ({
      id: `task:${task.id}`,
      title: `${taskTypeLabel(task.taskType)} task`,
      subtitle: `${task.reason || task.latestUserMessage || "Operational work item"} · ${taskStatusLabel(task.status)}`,
      timestamp: task.updatedAt,
      action: () => onJump({ tab: "work-queue", type: task.taskType === "CALLBACK_REQUEST" ? "callback" : task.taskType === "ESCALATION" ? "escalation" : task.taskType === "APPOINTMENT_HANDOFF" ? "appointment-handoff" : "human-handoff", taskId: task.id }),
    })),
    ...recentConversations.map((conversation) => ({
      id: `conversation:${conversation.id}`,
      title: `${conversation.channel} conversation`,
      subtitle: conversation.summary || conversation.status || "Active conversation",
      timestamp: conversation.updatedAt,
      action: () => onJump({ tab: "conversations" }),
    })),
  ]
    .sort((a, b) => b.timestamp.localeCompare(a.timestamp))
    .slice(0, 6);

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} justifyContent="space-between" alignItems={{ xs: "stretch", md: "center" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>AI Operations</Typography>
          <Typography variant="body2" color="text.secondary">
            Unified operational workspace for AI calls, live conversations, callbacks, escalations, and appointment handoffs.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={onRefresh} disabled={loading}>Refresh</Button>
      </Stack>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {!aiCopilotEnabled ? <Alert severity="info">AI call creation is disabled for this clinic. Operational tasks and transcripts remain available.</Alert> : null}
      {loading ? <Alert severity="info">Loading AI Operations overview…</Alert> : null}

      <Grid container spacing={1.5}>
        {canViewOperationalQueues ? <Grid size={{ xs: 6, md: 3 }}>{summaryCard("Active Conversations", conversations.length, "Live voice and chat threads")}</Grid> : null}
        {canViewOperationalQueues ? <Grid size={{ xs: 6, md: 3 }}>{summaryCard("Open Callbacks", openCallbacks, "Callback requests needing follow-up")}</Grid> : null}
        {canViewOperationalQueues ? <Grid size={{ xs: 6, md: 3 }}>{summaryCard("Open Escalations", openEscalations, "Urgent items awaiting review")}</Grid> : null}
        {canViewOperationalQueues ? <Grid size={{ xs: 6, md: 3 }}>{summaryCard("Appointment Handoffs", appointmentHandoffs, "Booking flows handed to staff")}</Grid> : null}
        {canViewOperationalQueues ? <Grid size={{ xs: 6, md: 3 }}>{summaryCard("Overdue Tasks", overdueTasks, "SLA-breached operational work")}</Grid> : null}
        {canViewOperationalQueues ? <Grid size={{ xs: 6, md: 3 }}>{summaryCard("Unassigned Tasks", unassignedTasks, "Items still waiting for ownership")}</Grid> : null}
        {canViewOperationalQueues ? <Grid size={{ xs: 6, md: 3 }}>{summaryCard("Open Tasks", openTasks, "Visible across the work queue")}</Grid> : null}
        {canViewCalls ? <Grid size={{ xs: 6, md: 3 }}>{summaryCard("AI Calls", overview?.aiCallsSummary?.totalCalls ?? 0, aiCopilotEnabled ? `Queued ${overview?.aiCallsSummary?.queuedCalls ?? 0}` : "Not available")}</Grid> : null}
      </Grid>

      {canViewOperationalQueues ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 6 }}>
            <Card variant="outlined">
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Urgent / Overdue Work</Typography>
                <Stack spacing={1}>
                  {tasks.filter(taskIsOverdue).slice(0, 4).map((task) => (
                    <Card key={task.id} variant="outlined">
                      <CardContent sx={{ py: 1.5 }}>
                        <Stack direction="row" justifyContent="space-between" gap={1} alignItems="flex-start">
                          <Box>
                            <Typography variant="subtitle2">{taskTypeLabel(task.taskType)}</Typography>
                            <Typography variant="body2" color="text.secondary">
                              {task.reason || task.latestUserMessage || "Operational work item"}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              Due {formatDateTime(task.dueAt || task.callbackDueAt)}
                            </Typography>
                          </Box>
                          <Button size="small" onClick={() => onJump({ tab: "work-queue", type: task.taskType === "CALLBACK_REQUEST" ? "callback" : task.taskType === "ESCALATION" ? "escalation" : task.taskType === "APPOINTMENT_HANDOFF" ? "appointment-handoff" : "human-handoff", taskId: task.id })}>
                            Open
                          </Button>
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                  {tasks.filter(taskIsOverdue).length === 0 ? <Alert severity="info">No overdue tasks.</Alert> : null}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 6 }}>
            <Card variant="outlined">
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Recent Activity</Typography>
                <Stack spacing={1}>
                  {recentActivity.map((item) => (
                    <Card key={item.id} variant="outlined">
                      <CardContent sx={{ py: 1.5 }}>
                        <Stack direction="row" justifyContent="space-between" gap={1} alignItems="flex-start">
                          <Box>
                            <Typography variant="subtitle2">{item.title}</Typography>
                            <Typography variant="body2" color="text.secondary">{item.subtitle}</Typography>
                            <Typography variant="caption" color="text.secondary">{formatDateTime(item.timestamp)}</Typography>
                          </Box>
                          <Button size="small" onClick={item.action}>Open</Button>
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                  {recentActivity.length === 0 ? <Alert severity="info">No recent operational activity found.</Alert> : null}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card variant="outlined">
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Provider / Worker Readiness</Typography>
                {aiCopilotEnabled ? (
                  <Stack spacing={0.75}>
                    <Typography variant="body2">{overview?.schedulerHealth?.workerLabel || "AI Calls Dispatch Worker"}: {overview?.schedulerHealth?.enabled ? "Enabled" : "Disabled"}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Disabled means queued AI calls will not be dispatched automatically. Manual calls, live conversations, and receptionist work queues remain available when permitted.
                    </Typography>
                    <Typography variant="body2">Last run: {formatDateTime(overview?.schedulerHealth?.lastRunAt)}</Typography>
                    <Typography variant="body2">Next estimated run: {formatDateTime(overview?.schedulerHealth?.nextEstimatedRunAt)}</Typography>
                    <Typography variant="body2">Last processed: {overview?.schedulerHealth?.lastProcessedCount ?? 0}</Typography>
                    <Typography variant="body2">Last dispatched: {overview?.schedulerHealth?.lastDispatchedCount ?? 0}</Typography>
                    <Typography variant="body2">Last failed: {overview?.schedulerHealth?.lastFailedCount ?? 0}</Typography>
                    <Typography variant="body2">Last skipped: {overview?.schedulerHealth?.lastSkippedCount ?? 0}</Typography>
                  </Stack>
                ) : (
                <Alert severity="info">AI call dispatch is disabled. Existing operational work remains available.</Alert>
                )}
              </CardContent>
            </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>Shortcuts</Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {canViewCalls ? shortcutButton("Open Calls", { tab: "calls" }, onJump) : null}
                {canViewOperationalQueues ? shortcutButton("Open Conversations", { tab: "conversations" }, onJump) : null}
                {canViewOperationalQueues ? shortcutButton("Callbacks", { tab: "work-queue", type: "callback", status: "open" }, onJump) : null}
                {canViewOperationalQueues ? shortcutButton("Escalations", { tab: "work-queue", type: "escalation", status: "open" }, onJump) : null}
                {canViewOperationalQueues ? shortcutButton("Hand-offs", { tab: "work-queue", type: "appointment-handoff", status: "open" }, onJump) : null}
                {canViewOperationalQueues ? shortcutButton("Overdue Work", { tab: "work-queue", status: "open" }, onJump) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}

export default function AiOperationsPage() {
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const aiCopilotEnabled = hasTenantModule(auth, "aiCopilot");
  const canViewCalls = aiCopilotEnabled && auth.hasPermission("engage.ai.operate");
  const canViewOperationalQueues = auth.hasPermission("engage.reception.operate") || auth.hasPermission("engage.view");
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [overview, setOverview] = React.useState<OverviewState | null>(null);

  const visibleTabs = React.useMemo(() => {
    const tabs: AiOperationsTab[] = ["overview"];
    if (canViewCalls) {
      tabs.splice(1, 0, "calls");
    }
    if (canViewOperationalQueues) {
      tabs.push("conversations", "work-queue");
    }
    return tabs;
  }, [aiCopilotEnabled, canViewCalls, canViewOperationalQueues]);

  const currentTab = normalizeTab(searchParams.get("tab"));
  const activeTab = visibleTabs.includes(currentTab) ? currentTab : visibleTabs[0];

  const updateSearchParams = React.useCallback((next: Record<string, string | null>, replace = false) => {
    const params = new URLSearchParams(searchParams);
    for (const [key, value] of Object.entries(next)) {
      if (value == null || value === "") {
        params.delete(key);
      } else {
        params.set(key, value);
      }
    }
    if (!params.get("tab")) {
      params.set("tab", "overview");
    }
    setSearchParams(params, { replace });
  }, [searchParams, setSearchParams]);

  React.useEffect(() => {
    if (!searchParams.get("tab")) {
      const params = new URLSearchParams(searchParams);
      params.set("tab", "overview");
      setSearchParams(params, { replace: true });
      return;
    }
    if (!visibleTabs.includes(currentTab)) {
      const params = new URLSearchParams(searchParams);
      params.set("tab", visibleTabs[0]);
      setSearchParams(params, { replace: true });
    }
  }, [currentTab, searchParams, setSearchParams, visibleTabs]);

  const loadOverview = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [conversationRows, taskRows, aiCallsSummary, schedulerHealth] = await Promise.all([
        canViewOperationalQueues
          ? listActiveCareAiConversations(auth.accessToken, auth.tenantId)
          : Promise.resolve([] as CareAiConversationSummary[]),
        canViewOperationalQueues
          ? listCareAiReceptionistTasks(auth.accessToken, auth.tenantId)
          : Promise.resolve([] as CareAiReceptionistTask[]),
        canViewCalls
          ? getCarePilotAiCallAnalyticsSummary(auth.accessToken, auth.tenantId)
          : Promise.resolve(null),
        canViewCalls
          ? getCarePilotAiCallSchedulerHealth(auth.accessToken, auth.tenantId)
          : Promise.resolve(null),
      ]);
      setOverview({
        conversations: conversationRows,
        tasks: taskRows,
        aiCallsSummary,
        schedulerHealth,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load AI Operations overview.");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, canViewCalls, canViewOperationalQueues]);

  React.useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  const badgeCounts = React.useMemo(() => ({
    calls: overview?.aiCallsSummary?.totalCalls ?? null,
    conversations: overview?.conversations.length ?? null,
    workQueue: overview?.tasks.length ?? null,
  }), [overview]);

  if (!auth.tenantId) {
    return <Alert severity="info">Select a tenant to view AI Operations.</Alert>;
  }

  if (!auth.hasPermission("engage.ai.operate") && !auth.hasPermission("engage.reception.operate") && !auth.hasPermission("engage.view")) {
    return <Alert severity="error">You do not have access to AI Operations.</Alert>;
  }

  if (!visibleTabs.includes(activeTab)) {
    return <Alert severity="info">No AI Operations tabs are available for your role.</Alert>;
  }

  return (
    <Stack spacing={2}>
      <Box>
        <Tabs
          value={activeTab}
          onChange={(_, nextTab: AiOperationsTab) => updateSearchParams({ tab: nextTab }, false)}
          variant="scrollable"
          allowScrollButtonsMobile
          sx={{ mb: 1 }}
        >
          {visibleTabs.map((tab) => (
            <Tab
              key={tab}
              value={tab}
              label={tab === "overview"
                ? tabLabel("Overview")
                : tab === "calls"
                  ? tabLabel("AI Calls", badgeCounts.calls)
                  : tab === "conversations"
                    ? tabLabel("Conversations", badgeCounts.conversations)
                    : tabLabel("Work Queue", badgeCounts.workQueue)}
            />
          ))}
        </Tabs>
      </Box>

      {activeTab === "overview" ? (
        <OverviewTab
          loading={loading}
          error={error}
          overview={overview}
          aiCopilotEnabled={aiCopilotEnabled}
          canViewCalls={canViewCalls}
          canViewOperationalQueues={canViewOperationalQueues}
          onRefresh={() => void loadOverview()}
          onJump={(next) => updateSearchParams(next, false)}
        />
      ) : null}

      {activeTab === "calls" ? (
        <Box>
          <AiCallsPage />
        </Box>
      ) : null}

      {activeTab === "conversations" ? (
        <Box>
          <ActiveConversationsPage />
        </Box>
      ) : null}

      {activeTab === "work-queue" ? (
        <ReceptionistQueuePage
          title="AI Operations Work Queue"
          description="Manage callbacks, escalations, appointment handoffs, and receptionist work in one queue."
          syncUrlState
        />
      ) : null}

      <Divider />
    </Stack>
  );
}
