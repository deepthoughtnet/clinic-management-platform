import * as React from "react";
import { Accordion, AccordionDetails, AccordionSummary, Box, Button, Card, CardContent, Chip, Collapse, Grid, IconButton, Stack, Typography } from "@mui/material";
import type { ChipProps } from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import ReviewRoundedIcon from "@mui/icons-material/VerifiedRounded";
import PublishRoundedIcon from "@mui/icons-material/PublishRounded";
import ReceiptRoundedIcon from "@mui/icons-material/ReceiptRounded";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import KeyboardArrowRightRoundedIcon from "@mui/icons-material/KeyboardArrowRightRounded";

import { CompactFilterCard, CompactStatCard, compactChipSx, compactCardContentSx, compactPanelSx } from "../../components/compact/CompactUi";

export type LabDashboardActionKey =
  | "new-orders"
  | "payment-pending"
  | "pending-collection"
  | "work-queue"
  | "pending-review"
  | "ready-to-publish"
  | "published-today"
  | "critical-results"
  | "tat-breached"
  | "sample-rejected"
  | "recollection-required"
  | "quick-new-order"
  | "quick-collect-payment"
  | "quick-collect"
  | "quick-enter-results"
  | "quick-verify"
  | "quick-publish"
  | "work-pending-payment"
  | "work-walk-in-orders-today"
  | "work-new-orders"
  | "work-pending-sample-collection"
  | "work-recollection-required"
  | "work-sample-rejected"
  | "work-work-queue"
  | "work-samples-collected"
  | "work-results-pending-entry"
  | "work-critical-results"
  | "work-pending-lab-review"
  | "work-ready-to-publish"
  | "work-published-today"
  | "work-tat-breached"
  | "sparkline-orders"
  | "sparkline-reports";

type DemoMetric = {
  key: LabDashboardActionKey;
  label: string;
  value: React.ReactNode;
  helper?: React.ReactNode;
  tone?: ChipProps["color"];
};

type SparklineMetric = {
  key: LabDashboardActionKey;
  label: string;
  subtitle: string;
  series: number[];
  tone?: ChipProps["color"];
};

export type LabDashboardData = {
  myWorkToday: DemoMetric[];
  analytics: {
    alerts: DemoMetric[];
    activity: DemoMetric[];
    sparklines: SparklineMetric[];
  };
};

export type LabDashboardPermissions = {
  canCreateOrders: boolean;
  canCollectPayment: boolean;
  canCollectSample: boolean;
  canEnterResults: boolean;
  canReviewReport: boolean;
  canGenerateReport: boolean;
  canManageTests: boolean;
};

type LabDashboardProps = {
  permissions: LabDashboardPermissions;
  data?: LabDashboardData;
  onAction?: (key: LabDashboardActionKey) => void;
};

type LabAnalyticsPanelProps = {
  data: LabDashboardData["analytics"];
  defaultExpanded?: boolean;
  onAction?: (key: LabDashboardActionKey) => void;
};

const WORKFLOW_STATE_KEY = "lab.dashboard.myWorkToday.expanded";

function readStoredBoolean(key: string, fallback: boolean) {
  try {
    const raw = window.localStorage.getItem(key);
    if (raw == null) return fallback;
    return raw === "true";
  } catch {
    return fallback;
  }
}

function usePersistentBooleanState(key: string, fallback: boolean) {
  const [value, setValue] = React.useState(() => {
    if (typeof window === "undefined") return fallback;
    return readStoredBoolean(key, fallback);
  });

  React.useEffect(() => {
    try {
      window.localStorage.setItem(key, String(value));
    } catch {
      // Ignore storage failures and keep the UI functional.
    }
  }, [key, value]);

  return [value, setValue] as const;
}

const DEFAULT_DATA: LabDashboardData = {
  myWorkToday: [
    { key: "work-pending-payment", label: "Pending Payment", value: 7, helper: "Awaiting billing clearance", tone: "warning" },
    { key: "work-walk-in-orders-today", label: "Walk-in Orders Today", value: 9, helper: "Front-desk registrations", tone: "info" },
    { key: "work-new-orders", label: "New Orders", value: 14, helper: "Created since opening", tone: "info" },
    { key: "work-pending-sample-collection", label: "Pending Sample Collection", value: 12, helper: "Ready for collection", tone: "info" },
    { key: "work-recollection-required", label: "Recollection Required", value: 4, helper: "Return to collection", tone: "warning" },
    { key: "work-sample-rejected", label: "Sample Rejected", value: 1, helper: "Requires follow-up", tone: "error" },
    { key: "work-work-queue", label: "Work Queue", value: 19, helper: "Active lab workflow", tone: "default" },
    { key: "work-samples-collected", label: "Samples Collected", value: 18, helper: "Collected today", tone: "info" },
    { key: "work-results-pending-entry", label: "Results Pending Entry", value: 14, helper: "Awaiting result entry", tone: "warning" },
    { key: "work-critical-results", label: "Critical Results", value: 2, helper: "Immediate attention", tone: "error" },
    { key: "work-pending-lab-review", label: "Pending Lab Review", value: 6, helper: "Awaiting verification", tone: "warning" },
    { key: "work-ready-to-publish", label: "Ready to Publish", value: 4, helper: "Report actions pending", tone: "success" },
    { key: "work-published-today", label: "Published Today", value: 11, helper: "Delivered to patients", tone: "success" },
    { key: "work-tat-breached", label: "TAT Breached", value: 3, helper: "Needs escalation", tone: "warning" },
  ],
  analytics: {
    alerts: [
      { key: "critical-results", label: "Critical Results", value: 2, helper: "Immediate attention", tone: "error" },
      { key: "tat-breached", label: "TAT Breached", value: 3, helper: "Needs escalation", tone: "warning" },
      { key: "sample-rejected", label: "Sample Rejected", value: 1, helper: "Requires follow-up", tone: "error" },
      { key: "recollection-required", label: "Recollection Required", value: 4, helper: "Return to collection", tone: "warning" },
    ],
    activity: [
      { key: "new-orders", label: "Orders", value: 23, helper: "Today", tone: "info" },
      { key: "work-queue", label: "Collections", value: 18, helper: "Samples logged", tone: "info" },
      { key: "pending-review", label: "Results Entered", value: 14, helper: "Moved to review", tone: "success" },
      { key: "ready-to-publish", label: "Reports Published", value: 9, helper: "Completed", tone: "success" },
      { key: "payment-pending", label: "Revenue", value: "₹18,750", helper: "Collected today", tone: "success" },
    ],
    sparklines: [
      { key: "sparkline-orders", label: "Orders", subtitle: "7-day trend", series: [8, 12, 10, 14, 15, 18, 16], tone: "info" },
      { key: "sparkline-reports", label: "Reports Published", subtitle: "7-day trend", series: [3, 4, 5, 4, 6, 7, 9], tone: "success" },
    ],
  },
};

function Sparkline({ values, tone = "info" }: { values: number[]; tone?: ChipProps["color"] }) {
  const width = 220;
  const height = 64;
  const padding = 6;
  const max = Math.max(...values, 1);
  const min = Math.min(...values, 0);
  const range = Math.max(max - min, 1);
  const points = values.map((value, index) => {
    const x = padding + (index * ((width - (padding * 2)) / Math.max(values.length - 1, 1)));
    const normalized = (value - min) / range;
    const y = height - padding - (normalized * (height - (padding * 2)));
    return `${x},${y}`;
  }).join(" ");
  const palette: Record<NonNullable<ChipProps["color"]>, string> = {
    default: "#6b7280",
    primary: "#0f766e",
    secondary: "#6d28d9",
    error: "#b91c1c",
    info: "#0369a1",
    success: "#15803d",
    warning: "#b45309",
  };
  const stroke = palette[tone || "info"];

  return (
    <Box sx={{ width: "100%", maxWidth: 220 }}>
      <svg viewBox={`0 0 ${width} ${height}`} width="100%" height="64" aria-hidden="true" focusable="false">
        <path
          d={`M ${points.split(" ").join(" L ")}`}
          fill="none"
          stroke={stroke}
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </Box>
  );
}

function DashboardTile({
  metric,
  onClick,
}: {
  metric: DemoMetric;
  onClick?: () => void;
}) {
  return (
    <CompactStatCard
      label={metric.label}
      value={metric.value}
      helper={metric.helper}
      tone={metric.tone}
      onClick={onClick}
    />
  );
}

function LabQuickActions({
  permissions,
  onAction,
}: {
  permissions: LabDashboardPermissions;
  onAction?: (key: LabDashboardActionKey) => void;
}) {
  const quickActions = [
    {
      key: "quick-new-order" as const,
      label: "New Lab Order",
      icon: <AddRoundedIcon fontSize="small" />,
      visible: permissions.canCreateOrders,
    },
    {
      key: "quick-collect-payment" as const,
      label: "Collect Payment",
      icon: <ReceiptRoundedIcon fontSize="small" />,
      visible: permissions.canCollectPayment,
    },
    {
      key: "quick-collect" as const,
      label: "Collect Sample",
      icon: <ScienceRoundedIcon fontSize="small" />,
      visible: permissions.canCollectSample,
    },
    {
      key: "quick-enter-results" as const,
      label: "Enter Results",
      icon: <ReceiptRoundedIcon fontSize="small" />,
      visible: permissions.canEnterResults,
    },
    {
      key: "quick-verify" as const,
      label: "Verify Results",
      icon: <ReviewRoundedIcon fontSize="small" />,
      visible: permissions.canReviewReport,
    },
    {
      key: "quick-publish" as const,
      label: "Publish Report",
      icon: <PublishRoundedIcon fontSize="small" />,
      visible: permissions.canGenerateReport,
    },
  ].filter((action) => action.visible);

  return (
    <CompactFilterCard
      title="Quick Actions"
      subtitle="Only the actions you can perform are shown."
    >
      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
        {quickActions.map((action) => (
          <Button
            key={action.key}
            size="small"
            variant="outlined"
            startIcon={action.icon}
            onClick={() => onAction?.(action.key)}
          >
            {action.label}
          </Button>
        ))}
      </Stack>
    </CompactFilterCard>
  );
}

function LabMyWorkToday({
  cards,
  expanded,
  onToggle,
  onAction,
}: {
  cards: DemoMetric[];
  expanded: boolean;
  onToggle: () => void;
  onAction?: (key: LabDashboardActionKey) => void;
}) {
  const pendingCount = React.useMemo(() => {
    return cards.reduce((sum, card) => {
      if (typeof card.value === "number" && Number.isFinite(card.value)) {
        return sum + card.value;
      }
      return sum;
    }, 0);
  }, [cards]);

  return (
    <Card variant="outlined" sx={compactPanelSx}>
      <CardContent sx={compactCardContentSx}>
        <Stack spacing={1}>
          <Box
            role="button"
            tabIndex={0}
            onClick={onToggle}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                onToggle();
              }
            }}
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 1,
              cursor: "pointer",
              borderRadius: 1.5,
              px: 0.25,
              py: 0.15,
              "&:focus-visible": { outline: "2px solid", outlineColor: "primary.main", outlineOffset: 2 },
            }}
          >
            <Stack spacing={0.15}>
              <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.1 }}>
                My Work Today
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.1 }}>
                {expanded ? "Role-aware cards based on your current permissions." : `${pendingCount} pending items`}
              </Typography>
            </Stack>
            <IconButton
              size="small"
              onClick={(event) => {
                event.stopPropagation();
                onToggle();
              }}
              aria-label={expanded ? "Collapse My Work Today" : "Expand My Work Today"}
            >
              {expanded ? <ExpandMoreRoundedIcon fontSize="small" /> : <KeyboardArrowRightRoundedIcon fontSize="small" />}
            </IconButton>
          </Box>
          <Collapse in={expanded} timeout={180} unmountOnExit>
            <Grid container spacing={1}>
              {cards.map((metric) => (
                <Grid key={metric.key} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
                  <DashboardTile metric={metric} onClick={() => onAction?.(metric.key)} />
                </Grid>
              ))}
            </Grid>
          </Collapse>
        </Stack>
      </CardContent>
    </Card>
  );
}

function LabWorkflowGuide({ permissions }: { permissions: LabDashboardPermissions }) {
  const steps = [
    { label: "Registration", active: permissions.canCreateOrders, tone: "info" as const },
    { label: "Billing", active: permissions.canCreateOrders, tone: "info" as const },
    { label: "Payment", active: permissions.canCreateOrders || permissions.canCollectPayment, tone: "warning" as const },
    { label: "Sample Collection", active: permissions.canCollectSample, tone: "primary" as const },
    { label: "Result Entry", active: permissions.canEnterResults, tone: "primary" as const },
    { label: "Lab Approval", active: permissions.canReviewReport, tone: "secondary" as const },
    { label: "Report Published", active: permissions.canGenerateReport, tone: "success" as const },
  ];

  return (
    <CompactFilterCard title="Workflow guide">
      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" alignItems="center">
        {steps.map((step, index) => (
          <React.Fragment key={step.label}>
            {index > 0 ? (
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, px: 0.15 }}>
                →
              </Typography>
            ) : null}
            <Chip
              size="small"
              label={step.label}
              variant={step.active ? "filled" : "outlined"}
              color={step.active ? step.tone : "default"}
              sx={compactChipSx}
            />
          </React.Fragment>
        ))}
      </Stack>
    </CompactFilterCard>
  );
}

export function LabAnalyticsPanel({ data, defaultExpanded = false, onAction }: LabAnalyticsPanelProps) {
  return (
    <Accordion defaultExpanded={defaultExpanded} sx={compactPanelSx}>
      <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
        <Stack spacing={0.25}>
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
            Dashboard Analytics
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Today's activity, alerts, trends, revenue, and report throughput.
          </Typography>
        </Stack>
      </AccordionSummary>
      <AccordionDetails sx={{ pt: 0 }}>
        <Stack spacing={1.25}>
          <Grid container spacing={1.25}>
            <Grid size={{ xs: 12, md: 8 }}>
              <CompactFilterCard title="Operational Alerts" subtitle="Items that need immediate attention this shift.">
                <Grid container spacing={1}>
                  {data.alerts.map((metric) => (
                    <Grid key={metric.key} size={{ xs: 12, sm: 6 }}>
                      <DashboardTile metric={metric} onClick={() => onAction?.(metric.key)} />
                    </Grid>
                  ))}
                </Grid>
              </CompactFilterCard>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <CompactFilterCard title="Today's Activity" subtitle="Compact operational totals for the day.">
                <Stack spacing={1}>
                  {data.activity.map((metric) => (
                    <CompactStatCard
                      key={metric.key}
                      label={metric.label}
                      value={metric.value}
                      helper={metric.helper}
                      tone={metric.tone}
                      onClick={() => onAction?.(metric.key)}
                    />
                  ))}
                </Stack>
              </CompactFilterCard>
            </Grid>
          </Grid>

          <Grid container spacing={1.25}>
            <Grid size={{ xs: 12, md: 8 }}>
              <CompactFilterCard title="Trend Snapshot" subtitle="Seven-day operational trend placeholders for the current shift.">
                <Grid container spacing={1}>
                  {data.sparklines.map((sparkline) => (
                    <Grid key={sparkline.key} size={{ xs: 12, sm: 6 }}>
                      <Card variant="outlined" sx={compactPanelSx}>
                        <CardContent sx={compactCardContentSx}>
                          <Stack spacing={1}>
                            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 1 }}>
                              <Box>
                                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{sparkline.label}</Typography>
                                <Typography variant="caption" color="text.secondary">{sparkline.subtitle}</Typography>
                              </Box>
                              <Chip size="small" label="7d" color={sparkline.tone || "default"} variant="outlined" sx={compactChipSx} />
                            </Box>
                            <Sparkline values={sparkline.series} tone={sparkline.tone} />
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>
                  ))}
                </Grid>
              </CompactFilterCard>
            </Grid>
          </Grid>
        </Stack>
      </AccordionDetails>
    </Accordion>
  );
}

export default function LabDashboard({
  permissions,
  data = DEFAULT_DATA,
  onAction,
}: LabDashboardProps) {
  const [myWorkExpanded, setMyWorkExpanded] = usePersistentBooleanState(WORKFLOW_STATE_KEY, true);

  return (
    <Stack spacing={1.25}>
      <LabQuickActions permissions={permissions} onAction={onAction} />
      <LabMyWorkToday
        cards={data.myWorkToday}
        expanded={myWorkExpanded}
        onToggle={() => setMyWorkExpanded((current) => !current)}
        onAction={onAction}
      />
      <LabWorkflowGuide permissions={permissions} />
    </Stack>
  );
}
