import * as React from "react";
import { Box, Card, CardContent, Chip, Grid, LinearProgress, Stack, Typography } from "@mui/material";

import { CompactFilterCard, compactCardContentSx, compactChipSx, compactPanelSx } from "../../../components/compact/CompactUi";
import type { Capability, ProductModule, Release, Workflow, WorkflowStep, ImplementationStatus } from "./productImplementationData";

function statusTone(status: ImplementationStatus) {
  if (status === "Complete") return "success" as const;
  if (status === "In Progress") return "primary" as const;
  if (status === "Review") return "warning" as const;
  if (status === "Blocked") return "error" as const;
  return "default" as const;
}

export function ImplementationStatusBadge({ status }: { status: ImplementationStatus }) {
  return <Chip size="small" label={status} color={statusTone(status)} variant={status === "Complete" ? "filled" : "outlined"} sx={compactChipSx} />;
}

export function ImplementationProgressBar({
  label,
  value,
  helper,
  tone = "primary",
}: {
  label: string;
  value: number;
  helper?: string;
  tone?: "primary" | "success" | "warning" | "error" | "info";
}) {
  return (
    <Stack spacing={0.35}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
          {label}
        </Typography>
        <Typography variant="caption" sx={{ fontWeight: 800 }}>
          {value}%
        </Typography>
      </Stack>
      <LinearProgress
        variant="determinate"
        value={value}
        color={tone}
        sx={{
          height: 8,
          borderRadius: 999,
          bgcolor: "action.hover",
        }}
      />
      {helper ? (
        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.15 }}>
          {helper}
        </Typography>
      ) : null}
    </Stack>
  );
}

export function ImplementationKpiCard({
  label,
  value,
  tone,
  helper,
}: {
  label: string;
  value: React.ReactNode;
  tone?: "primary" | "success" | "warning" | "error" | "info";
  helper?: React.ReactNode;
}) {
  return (
    <Card variant="outlined" sx={compactPanelSx}>
      <CardContent sx={compactCardContentSx}>
        <Stack spacing={0.4}>
          <Chip size="small" label={label} color={tone || "default"} variant="outlined" sx={compactChipSx} />
          <Typography variant="h6" sx={{ fontWeight: 900, lineHeight: 1.1 }}>
            {value}
          </Typography>
          {helper ? (
            <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
              {helper}
            </Typography>
          ) : null}
        </Stack>
      </CardContent>
    </Card>
  );
}

export function ModuleMaturityCard({ module }: { module: ProductModule }) {
  return (
    <Card variant="outlined" sx={{ height: "100%", ...compactPanelSx }}>
      <CardContent sx={compactCardContentSx}>
        <Stack spacing={1}>
          <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 900, lineHeight: 1.15 }}>
                {module.name}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {module.area} · Owner {module.owner}
              </Typography>
            </Box>
            <ImplementationStatusBadge status={module.status} />
          </Stack>

          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
            <Chip size="small" label={`Release ${module.release}`} variant="outlined" sx={compactChipSx} />
            <Chip size="small" label={`${module.completionPercent}% complete`} color="primary" variant="outlined" sx={compactChipSx} />
            <Chip size="small" label={`${module.productionReadinessPercent}% ready`} color="success" variant="outlined" sx={compactChipSx} />
            <Chip size="small" label={`${module.openDefects} open defects`} color={module.openDefects > 0 ? "warning" : "success"} variant="outlined" sx={compactChipSx} />
          </Stack>

          <ImplementationProgressBar label="Completion" value={module.completionPercent} tone="primary" />
          <ImplementationProgressBar label="Production readiness" value={module.productionReadinessPercent} tone="success" />
          <ImplementationProgressBar label="UAT" value={module.uatPercent} tone="warning" />

          <Grid container spacing={1}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <ImplementationProgressBar label="Backend" value={module.backendPercent} tone="primary" />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <ImplementationProgressBar label="Frontend" value={module.frontendPercent} tone="primary" />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <ImplementationProgressBar label="API" value={module.apiPercent} tone="primary" />
            </Grid>
          </Grid>

          <Grid container spacing={1}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <ImplementationProgressBar label="RBAC" value={module.rbacPercent} tone="info" />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <ImplementationProgressBar label="Audit" value={module.auditPercent} tone="info" />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <ImplementationProgressBar label="PDF" value={module.pdfPercent} tone="warning" />
            </Grid>
          </Grid>

          <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.45 }}>
            {module.notes}
          </Typography>

          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
            <Chip size="small" label={`Critical ${module.criticalDefects}`} color={module.criticalDefects > 0 ? "error" : "default"} variant="outlined" sx={compactChipSx} />
            <Chip size="small" label={`Technical debt ${module.technicalDebt}`} variant="outlined" sx={compactChipSx} />
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

export function WorkflowStepStrip({ steps }: { steps: WorkflowStep[] }) {
  return (
    <Stack spacing={1} sx={{ overflowX: "auto", pb: 0.5 }}>
      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
        {steps.map((step) => (
          <Card key={step.name} variant="outlined" sx={{ minWidth: 180, flex: "1 1 180px", ...compactPanelSx }}>
            <CardContent sx={compactCardContentSx}>
              <Stack spacing={0.7}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                  <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.25 }}>
                    {step.name}
                  </Typography>
                  <ImplementationStatusBadge status={step.status} />
                </Stack>
                <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                  <Chip size="small" label={`BE ${step.backendStatus}`} variant="outlined" sx={compactChipSx} />
                  <Chip size="small" label={`FE ${step.frontendStatus}`} variant="outlined" sx={compactChipSx} />
                  <Chip size="small" label={`API ${step.apiStatus}`} variant="outlined" sx={compactChipSx} />
                  <Chip size="small" label={`UAT ${step.uatStatus}`} variant="outlined" sx={compactChipSx} />
                  <Chip size="small" label={`PROD ${step.productionStatus}`} variant="outlined" sx={compactChipSx} />
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>
    </Stack>
  );
}

export function CapabilityCoverageCard({ capability }: { capability: Capability }) {
  return (
    <Card variant="outlined" sx={{ height: "100%", ...compactPanelSx }}>
      <CardContent sx={compactCardContentSx}>
        <Stack spacing={1}>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
            <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
              {capability.name}
            </Typography>
            <ImplementationStatusBadge status={capability.status} />
          </Stack>
          <ImplementationProgressBar label="Coverage" value={capability.coveragePercent} tone={capability.status === "Blocked" ? "error" : capability.status === "Review" ? "warning" : "success"} />
          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
            {capability.modulesCovered.map((module) => <Chip key={module} size="small" label={module} variant="outlined" sx={compactChipSx} />)}
          </Stack>
          {capability.gaps.length ? (
            <Typography variant="caption" color="text.secondary">
              Gaps: {capability.gaps.join(" · ")}
            </Typography>
          ) : null}
          <Typography variant="body2" color="text.secondary">
            {capability.notes}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}

export function ReleaseCard({ release }: { release: Release }) {
  return (
    <Card variant="outlined" sx={{ height: "100%", ...compactPanelSx }}>
      <CardContent sx={compactCardContentSx}>
        <Stack spacing={1}>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
                {release.name}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Target: {release.target}
              </Typography>
            </Box>
            <Chip size="small" label={release.status} color={release.status === "In Progress" ? "primary" : release.status === "Planned" ? "warning" : "default"} variant="outlined" sx={compactChipSx} />
          </Stack>
          <ImplementationProgressBar label="Progress" value={release.progressPercent} tone={release.status === "Future" ? "warning" : "success"} />
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
            Included modules
          </Typography>
          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
            {release.includedModules.map((module) => <Chip key={module} size="small" label={module} variant="outlined" sx={compactChipSx} />)}
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
            Blockers
          </Typography>
          <Stack spacing={0.25}>
            {release.blockers.map((blocker) => (
              <Typography key={blocker} variant="body2" color="text.secondary">
                • {blocker}
              </Typography>
            ))}
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
            Exit criteria
          </Typography>
          <Stack spacing={0.25}>
            {release.exitCriteria.map((criteria) => (
              <Typography key={criteria} variant="body2" color="text.secondary">
                • {criteria}
              </Typography>
            ))}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

export function ProductImplementationSectionCard({
  title,
  subtitle,
  children,
  actions,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <CompactFilterCard title={title} subtitle={subtitle} actions={actions}>
      {children}
    </CompactFilterCard>
  );
}
