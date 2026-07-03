import * as React from "react";
import { Box, Button, Card, CardContent, Chip, Grid, Stack, Typography } from "@mui/material";
import type { ChipProps } from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import ReviewRoundedIcon from "@mui/icons-material/VerifiedRounded";
import PublishRoundedIcon from "@mui/icons-material/PublishRounded";
import ReceiptRoundedIcon from "@mui/icons-material/ReceiptRounded";

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
  | "quick-collect"
  | "quick-enter-results"
  | "quick-verify"
  | "quick-publish"
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
  workflowSummary: DemoMetric[];
  alerts: DemoMetric[];
  activity: DemoMetric[];
  sparklines: SparklineMetric[];
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

const DEFAULT_DATA: LabDashboardData = {
  workflowSummary: [
    { key: "new-orders", label: "New Orders", value: 14, helper: "Created since opening", tone: "info" },
    { key: "payment-pending", label: "Payment Pending", value: 7, helper: "Awaiting billing clearance", tone: "warning" },
    { key: "pending-collection", label: "Pending Collection", value: 12, helper: "Ready for sample draw", tone: "info" },
    { key: "work-queue", label: "Work Queue", value: 19, helper: "Active lab workflow", tone: "default" },
    { key: "pending-review", label: "Pending Lab Review", value: 6, helper: "Awaiting verification", tone: "warning" },
    { key: "ready-to-publish", label: "Ready to Publish", value: 4, helper: "PDF/report ready", tone: "success" },
    { key: "published-today", label: "Published Today", value: 11, helper: "Delivered to patients", tone: "success" },
  ],
  alerts: [
    { key: "critical-results", label: "Critical Results", value: 2, helper: "Immediate attention", tone: "error" },
    { key: "tat-breached", label: "TAT Breached", value: 3, helper: "Needs escalation", tone: "warning" },
    { key: "sample-rejected", label: "Sample Rejected", value: 1, helper: "Requires follow-up", tone: "error" },
    { key: "recollection-required", label: "Recollection Required", value: 4, helper: "Return to collection", tone: "warning" },
  ],
  activity: [
    { key: "published-today", label: "Orders", value: 23, helper: "Today", tone: "info" },
    { key: "work-queue", label: "Collections", value: 18, helper: "Samples logged", tone: "info" },
    { key: "pending-review", label: "Results Entered", value: 14, helper: "Moved to review", tone: "success" },
    { key: "ready-to-publish", label: "Reports Published", value: 9, helper: "Completed", tone: "success" },
    { key: "payment-pending", label: "Revenue", value: "₹18,750", helper: "Collected today", tone: "success" },
  ],
  sparklines: [
    { key: "sparkline-orders", label: "Orders", subtitle: "7-day trend", series: [8, 12, 10, 14, 15, 18, 16], tone: "info" },
    { key: "sparkline-reports", label: "Reports Published", subtitle: "7-day trend", series: [3, 4, 5, 4, 6, 7, 9], tone: "success" },
  ],
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

export default function LabDashboard({ permissions, data = DEFAULT_DATA, onAction }: LabDashboardProps) {
  const dataSourceLabel = data === DEFAULT_DATA ? "Demo data" : "Live counts";
  const quickActions = [
    {
      key: "quick-new-order" as const,
      label: "New Lab Order",
      icon: <AddRoundedIcon fontSize="small" />,
      visible: permissions.canCreateOrders,
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
    <Stack spacing={1.5}>
      <CompactFilterCard
        title="Laboratory Dashboard"
        subtitle="Workflow-first snapshot for the current shift, drawn from the active lab query state."
        actions={
          <Chip size="small" label={dataSourceLabel} variant="outlined" sx={compactChipSx} />
        }
      >
        <Grid container spacing={1}>
          {data.workflowSummary.map((metric) => (
            <Grid key={metric.key} size={{ xs: 12, sm: 6, md: 3, lg: 1.7 }}>
              <DashboardTile metric={metric} onClick={() => onAction?.(metric.key)} />
            </Grid>
          ))}
        </Grid>
      </CompactFilterCard>

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
          <CompactFilterCard title="Quick Actions" subtitle="Only the actions you can perform are shown.">
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
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Stack spacing={1.25}>
            {data.sparklines.map((sparkline) => (
              <Card key={sparkline.key} variant="outlined" sx={compactPanelSx}>
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
            ))}
          </Stack>
        </Grid>
      </Grid>

      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
        <Chip size="small" label="Responsive" variant="outlined" sx={compactChipSx} />
        <Chip size="small" label="Workflow-first" variant="outlined" sx={compactChipSx} />
        <Chip size="small" label="Future live API ready" variant="outlined" sx={compactChipSx} />
      </Stack>
    </Stack>
  );
}
