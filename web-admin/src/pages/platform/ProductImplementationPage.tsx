import * as React from "react";
import { useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  MenuItem,
  Paper,
  Select,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import type { SelectChangeEvent } from "@mui/material";
import AutoAwesomeMotionRoundedIcon from "@mui/icons-material/AutoAwesomeMotionRounded";

import { useAuth } from "../../auth/useAuth";
import {
  CompactEmptyState,
  CompactFilterCard,
  CompactStatCard,
  CompactTableFrame,
  WorkflowGuide,
} from "../../components/compact/CompactUi";
import {
  CapabilityCoverageCard,
  ImplementationKpiCard,
  ImplementationProgressBar,
  ImplementationStatusBadge,
  ModuleMaturityCard,
  ReleaseCard,
  WorkflowStepStrip,
} from "./productImplementation/productImplementationComponents";
import {
  capabilities,
  features,
  productModules,
  releases,
  uatScenarios,
  workflows,
  type ProductModule,
} from "./productImplementation/productImplementationData";

type ProductImplementationTab = "overview" | "modules" | "workflows" | "features" | "uat" | "readiness" | "releases";

const TABS: Array<{ value: ProductImplementationTab; label: string }> = [
  { value: "overview", label: "Overview" },
  { value: "modules", label: "Modules" },
  { value: "workflows", label: "Workflows" },
  { value: "features", label: "Feature Matrix" },
  { value: "uat", label: "UAT & Regression" },
  { value: "readiness", label: "Production Readiness" },
  { value: "releases", label: "Releases" },
];

const AREA_ORDER = [
  "Operations",
  "Clinical",
  "Pharmacy",
  "Finance",
  "Engage",
  "AI Receptionist",
  "Administration",
  "Platform",
];

const STATUS_OPTIONS = ["All", "Complete", "In Progress", "Review", "Blocked", "Pending"] as const;
const RELEASE_OPTIONS = ["All", "R2", "R3", "R4"] as const;
const COMPLETION_BUCKETS = ["All", "0-49%", "50-79%", "80-94%", "95-100%"] as const;
const CURRENT_MILESTONE = "jeevanam-v1-opd-uat-ready";

type ExecutiveModuleStatus = {
  module: string;
  completion: number;
  uat: number;
  production: number;
  uatStatus: "Ready" | "In progress" | "Needs review" | "Blocked";
  productionStatus: "Hardening" | "In progress" | "Needs review" | "Blocked";
  notes: string;
  nextAction: string;
};

const EXECUTIVE_METRICS = [
  { label: "Feature Completion", value: "90%", tone: "success" as const, helper: "Integrated OPD scope is feature-complete for UAT." },
  { label: "UAT Readiness", value: "83%", tone: "primary" as const, helper: "Ready for customer validation with targeted fixes." },
  { label: "Production Readiness", value: "68%", tone: "warning" as const, helper: "Hardening remains before production certification." },
  { label: "Risk Level", value: "Medium", tone: "warning" as const, helper: "Main risks are lab, billing, and production operations." },
  { label: "Next Phase", value: "Customer UAT + Production Hardening", tone: "info" as const, helper: "Validated workflows move into customer review." },
] as const;

const MILESTONE_CHECKPOINTS = [
  "Validation Framework",
  "Clinical Intelligence v1",
  "Pharmacy UAT Ready",
  "Doctor Workspace v1.0",
  "OPD UAT Ready",
] as const;

const EXECUTIVE_MODULE_STATUS: ExecutiveModuleStatus[] = [
  {
    module: "Platform Foundation",
    completion: 92,
    uat: 84,
    production: 71,
    uatStatus: "Ready",
    productionStatus: "Hardening",
    notes: "Multi-tenancy, RBAC, help, validation, audit, and document foundations are established.",
    nextAction: "Run full regression and production security sweep.",
  },
  {
    module: "Reception",
    completion: 88,
    uat: 83,
    production: 72,
    uatStatus: "Ready",
    productionStatus: "Hardening",
    notes: "Registration, appointment, queue, intake, vitals, and document upload are integrated.",
    nextAction: "Validate duplicate registration and clinical intake edge cases.",
  },
  {
    module: "Doctor Workspace",
    completion: 97,
    uat: 94,
    production: 82,
    uatStatus: "Ready",
    productionStatus: "In progress",
    notes: "Consultation, prescription, investigations, lab orders, AI assist, documentation, and completion are frozen.",
    nextAction: "Customer UAT and defect-driven hardening only.",
  },
  {
    module: "Laboratory",
    completion: 82,
    uat: 78,
    production: 66,
    uatStatus: "Ready",
    productionStatus: "Hardening",
    notes: "Order, payment, sample, result, publication, and history integration are in place.",
    nextAction: "Complete report publication and structured comparison validation.",
  },
  {
    module: "Billing",
    completion: 78,
    uat: 73,
    production: 65,
    uatStatus: "In progress",
    productionStatus: "Hardening",
    notes: "Consultation, lab, and pharmacy billing are integrated with payment and receipt flows.",
    nextAction: "Focus on payment reconciliation and regression coverage.",
  },
  {
    module: "Pharmacy",
    completion: 89,
    uat: 84,
    production: 76,
    uatStatus: "Ready",
    productionStatus: "Hardening",
    notes: "Inventory, procurement, POS, dispensing, and reconciliation are operational.",
    nextAction: "Close polish items and run dispensing / inventory regression.",
  },
  {
    module: "Patient Portal / Public Booking",
    completion: 76,
    uat: 70,
    production: 58,
    uatStatus: "In progress",
    productionStatus: "Needs review",
    notes: "Booking, visibility, and communication are present but not production-certified.",
    nextAction: "Validate document visibility and patient-facing flows.",
  },
  {
    module: "AI / AIVA",
    completion: 81,
    uat: 76,
    production: 60,
    uatStatus: "Ready",
    productionStatus: "In progress",
    notes: "Clinical context, drafting, and AI-assisted review are implemented; voice and production ops remain behind.",
    nextAction: "Harden safety, observability, and prompt/response reliability.",
  },
  {
    module: "Reports / Admin",
    completion: 66,
    uat: 60,
    production: 49,
    uatStatus: "In progress",
    productionStatus: "Needs review",
    notes: "Operational reporting is usable; analytics and admin hardening remain.",
    nextAction: "Add UAT evidence and reporting regression coverage.",
  },
  {
    module: "Production Operations",
    completion: 57,
    uat: 52,
    production: 41,
    uatStatus: "Needs review",
    productionStatus: "Hardening",
    notes: "Deployment, monitoring, backup, restore, and security review are still incomplete.",
    nextAction: "Complete production hardening checklist before certification.",
  },
];

const CUSTOMER_UAT_CHECKLIST = [
  "End-to-end OPD scenario testing",
  "Role-based testing",
  "Document / PDF verification",
  "Billing / payment verification",
  "Lab result / report verification",
  "Pharmacy dispense / POS verification",
  "Patient communication verification",
] as const;

const PRODUCTION_HARDENING_CHECKLIST = [
  "Full backend regression suite",
  "Security review",
  "Backup / restore validation",
  "Monitoring / logging",
  "Performance test",
  "Error tracking",
  "Deployment automation",
  "Production data seeding",
  "Disaster recovery plan",
] as const;

const PLATFORM_RISKS = [
  "Laboratory report publication and trend handling need final UAT attention.",
  "Billing and payment reconciliation still need broader regression coverage.",
  "Production operations still need monitoring, backup, and DR validation.",
] as const;

function parseTab(value: string | null): ProductImplementationTab {
  if (value === "modules" || value === "workflows" || value === "features" || value === "uat" || value === "readiness" || value === "releases") {
    return value;
  }
  return "overview";
}

function moduleCompletionBucket(value: number): (typeof COMPLETION_BUCKETS)[number] {
  if (value < 50) return "0-49%";
  if (value < 80) return "50-79%";
  if (value < 95) return "80-94%";
  return "95-100%";
}

function statusTone(status: string) {
  if (status === "Complete" || status === "Pass") return "success" as const;
  if (status === "In Progress") return "primary" as const;
  if (status === "Review") return "warning" as const;
  if (status === "Blocked") return "error" as const;
  return "default" as const;
}

function groupedModules(modules: ProductModule[]) {
  return AREA_ORDER.map((area) => ({
    area,
    rows: modules.filter((module) => module.area === area),
  })).filter((group) => group.rows.length > 0);
}

function ModuleFilters({
  area,
  setArea,
  status,
  setStatus,
  release,
  setRelease,
  bucket,
  setBucket,
}: {
  area: string;
  setArea: (value: string) => void;
  status: string;
  setStatus: (value: string) => void;
  release: string;
  setRelease: (value: string) => void;
  bucket: string;
  setBucket: (value: string) => void;
}) {
  return (
    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
      <Select size="small" value={area} onChange={(event: SelectChangeEvent) => setArea(event.target.value)} sx={{ minWidth: 160 }}>
        <MenuItem value="All">All Areas</MenuItem>
        {AREA_ORDER.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
      </Select>
      <Select size="small" value={status} onChange={(event: SelectChangeEvent) => setStatus(event.target.value)} sx={{ minWidth: 160 }}>
        {STATUS_OPTIONS.map((option) => <MenuItem key={option} value={option}>{option === "All" ? "All Statuses" : option}</MenuItem>)}
      </Select>
      <Select size="small" value={release} onChange={(event: SelectChangeEvent) => setRelease(event.target.value)} sx={{ minWidth: 120 }}>
        {RELEASE_OPTIONS.map((option) => <MenuItem key={option} value={option}>{option === "All" ? "All Releases" : option}</MenuItem>)}
      </Select>
      <Select size="small" value={bucket} onChange={(event: SelectChangeEvent) => setBucket(event.target.value)} sx={{ minWidth: 160 }}>
        {COMPLETION_BUCKETS.map((option) => <MenuItem key={option} value={option}>{option === "All" ? "All Completion" : option}</MenuItem>)}
      </Select>
    </Stack>
  );
}

export function ProductImplementationTabs({
  tab,
  setTab,
}: {
  tab: ProductImplementationTab;
  setTab: (next: ProductImplementationTab) => void;
}) {
  return (
    <Tabs
      value={tab}
      onChange={(_, next: ProductImplementationTab) => setTab(next)}
      variant="scrollable"
      scrollButtons="auto"
      sx={{
        minHeight: 40,
        "& .MuiTab-root": {
          minHeight: 40,
          fontWeight: 800,
          textTransform: "none",
        },
      }}
    >
      {TABS.map((entry) => <Tab key={entry.value} value={entry.value} label={entry.label} />)}
    </Tabs>
  );
}

export function ProductImplementationOverview() {
  return (
    <Stack spacing={2.5}>
      <WorkflowGuide
        title="Jeevanam Product Implementation Status"
        subtitle="OPD platform is feature-complete for UAT and entering customer validation / production hardening."
        steps={[
          { label: "Validation", tone: "primary" },
          { label: "UAT", tone: "primary" },
          { label: "Hardening", tone: "primary" },
          { label: "Release", tone: "primary" },
        ]}
      />

      <Alert severity="info" variant="outlined" sx={{ borderRadius: 2 }}>
        <Stack spacing={0.35}>
          <Typography variant="body2" sx={{ fontWeight: 800 }}>
            Current milestone: {CURRENT_MILESTONE}
          </Typography>
          <Typography variant="body2">
            Status: Ready for integrated OPD UAT. Not yet production-certified.
          </Typography>
        </Stack>
      </Alert>

      <Grid container spacing={2}>
        {EXECUTIVE_METRICS.map((metric) => (
          <Grid key={metric.label} size={{ xs: 12, sm: 6, md: 4, xl: 2.4 }}>
            <ImplementationKpiCard label={metric.label} value={metric.value} tone={metric.tone} helper={metric.helper} />
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 7 }}>
          <CompactFilterCard title="Milestone section" subtitle="The release milestones below are the current executive checkpoints for OPD UAT readiness.">
            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
              {MILESTONE_CHECKPOINTS.map((milestone, index) => (
                <Chip
                  key={milestone}
                  size="small"
                  label={`${index + 1}. ${milestone}`}
                  color={index === MILESTONE_CHECKPOINTS.length - 1 ? "success" : "primary"}
                  variant={index === MILESTONE_CHECKPOINTS.length - 1 ? "filled" : "outlined"}
                  sx={{ borderRadius: 999 }}
                />
              ))}
            </Stack>
            <Stack spacing={1.2} sx={{ mt: 1.5 }}>
              <ImplementationProgressBar label="OPD UAT readiness" value={83} tone="primary" helper="Customer validation is the current focus." />
              <ImplementationProgressBar label="Production readiness" value={68} tone="warning" helper="Production hardening remains incomplete." />
            </Stack>
          </CompactFilterCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 5 }}>
          <CompactFilterCard title="Current risk posture" subtitle="Conservative read on the final hardening work that remains before production certification.">
            <Stack spacing={1}>
              {PLATFORM_RISKS.map((risk) => (
                <Stack key={risk} direction="row" spacing={1} alignItems="flex-start">
                  <Chip size="small" label="Risk" color="warning" variant="outlined" sx={{ borderRadius: 999 }} />
                  <Typography variant="body2" color="text.secondary">{risk}</Typography>
                </Stack>
              ))}
            </Stack>
          </CompactFilterCard>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12 }}>
          <CompactFilterCard title="Module status overview" subtitle="Conservative executive estimates across the platform after Doctor Workspace v1.0 and OPD UAT readiness.">
            <CompactTableFrame maxHeight={520}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Module</TableCell>
                    <TableCell>Completion</TableCell>
                    <TableCell>UAT</TableCell>
                    <TableCell>Production</TableCell>
                    <TableCell>Notes</TableCell>
                    <TableCell>Next action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {EXECUTIVE_MODULE_STATUS.map((module) => (
                    <TableRow key={module.module} hover>
                      <TableCell sx={{ fontWeight: 800 }}>{module.module}</TableCell>
                      <TableCell>
                        <ImplementationProgressBar label="Completion" value={module.completion} tone={module.completion >= 90 ? "success" : module.completion >= 75 ? "primary" : "warning"} />
                      </TableCell>
                      <TableCell>
                        <Chip size="small" label={`${module.uat}% · ${module.uatStatus}`} color={module.uatStatus === "Ready" ? "success" : module.uatStatus === "Blocked" ? "error" : module.uatStatus === "Needs review" ? "warning" : "primary"} variant="outlined" />
                      </TableCell>
                      <TableCell>
                        <Chip size="small" label={`${module.production}% · ${module.productionStatus}`} color={module.productionStatus === "Hardening" ? "warning" : module.productionStatus === "Blocked" ? "error" : module.productionStatus === "Needs review" ? "warning" : "primary"} variant="outlined" />
                      </TableCell>
                      <TableCell sx={{ minWidth: 360 }}>
                        <Typography variant="body2" color="text.secondary">{module.notes}</Typography>
                      </TableCell>
                      <TableCell sx={{ minWidth: 280 }}>
                        <Typography variant="body2">{module.nextAction}</Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CompactTableFrame>
          </CompactFilterCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <CompactFilterCard title="Customer UAT checklist" subtitle="The next stage is integrated customer validation across the OPD workflow.">
            <Stack spacing={0.75}>
              {CUSTOMER_UAT_CHECKLIST.map((item) => (
                <Stack key={item} direction="row" spacing={1} alignItems="center">
                  <Chip size="small" label="UAT" color="primary" variant="outlined" sx={{ borderRadius: 999 }} />
                  <Typography variant="body2">{item}</Typography>
                </Stack>
              ))}
            </Stack>
          </CompactFilterCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <CompactFilterCard title="Production hardening checklist" subtitle="These items remain before production certification.">
            <Stack spacing={0.75}>
              {PRODUCTION_HARDENING_CHECKLIST.map((item) => (
                <Stack key={item} direction="row" spacing={1} alignItems="center">
                  <Chip size="small" label="Hardening" color="warning" variant="outlined" sx={{ borderRadius: 999 }} />
                  <Typography variant="body2">{item}</Typography>
                </Stack>
              ))}
            </Stack>
          </CompactFilterCard>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        {capabilities.filter((capability) => ["workflow-header", "validation-framework", "help-cms", "rbac", "audit-trail", "pdf-framework"].includes(capability.id)).map((capability) => (
          <Grid key={capability.id} size={{ xs: 12, md: 6 }}>
            <CapabilityCoverageCard capability={capability} />
          </Grid>
        ))}
      </Grid>

      <CompactFilterCard title="Roadmap posture" subtitle="The roadmap remains visible, but the current milestone is OPD UAT readiness.">
        <Grid container spacing={1.5}>
          {releases.map((release) => (
            <Grid key={release.id} size={{ xs: 12, md: 4 }}>
              <Card variant="outlined" sx={{ borderRadius: 2 }}>
                <CardContent sx={{ p: 1.2, "&:last-child": { pb: 1.2 } }}>
                  <Stack spacing={1}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
                      <Typography variant="body2" sx={{ fontWeight: 800 }}>{release.name}</Typography>
                      <Chip size="small" label={release.status} color={release.status === "In Progress" ? "primary" : release.status === "Planned" ? "warning" : "default"} variant="outlined" />
                    </Stack>
                    <ImplementationProgressBar label="Progress" value={release.progressPercent} tone={release.id === "r2" ? "success" : "primary"} />
                    <Typography variant="caption" color="text.secondary">
                      {release.target}
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </CompactFilterCard>
    </Stack>
  );
}

export function ModuleRegistryTab({
  area,
  setArea,
  status,
  setStatus,
  release,
  setRelease,
  bucket,
  setBucket,
}: {
  area: string;
  setArea: (value: string) => void;
  status: string;
  setStatus: (value: string) => void;
  release: string;
  setRelease: (value: string) => void;
  bucket: string;
  setBucket: (value: string) => void;
}) {
  const filteredModules = React.useMemo(() => productModules.filter((module) => {
    if (area !== "All" && module.area !== area) return false;
    if (status !== "All" && module.status !== status) return false;
    if (release !== "All" && module.release !== release) return false;
    if (bucket !== "All" && moduleCompletionBucket(module.completionPercent) !== bucket) return false;
    return true;
  }), [area, bucket, release, status]);

  const moduleGroups = React.useMemo(() => groupedModules(filteredModules), [filteredModules]);

  return (
    <Stack spacing={2.25}>
      <CompactFilterCard title="Module registry" subtitle="Filter by area, status, release, and completion bucket.">
        <ModuleFilters area={area} setArea={setArea} status={status} setStatus={setStatus} release={release} setRelease={setRelease} bucket={bucket} setBucket={setBucket} />
      </CompactFilterCard>

      {moduleGroups.length ? moduleGroups.map((group) => (
        <CompactFilterCard key={group.area} title={group.area} subtitle={`${group.rows.length} module${group.rows.length === 1 ? "" : "s"} in view`}>
          <Grid container spacing={1.5}>
            {group.rows.map((module) => (
              <Grid key={module.id} size={{ xs: 12, md: 6, xl: 4 }}>
                <ModuleMaturityCard module={module} />
              </Grid>
            ))}
          </Grid>
        </CompactFilterCard>
      )) : (
        <CompactEmptyState title="No modules match the current filters." subtitle="Reset the filters to view the full implementation register." />
      )}
    </Stack>
  );
}

export function WorkflowRegistryTab() {
  const workflowGroups = React.useMemo(() => workflows, []);
  return (
    <Stack spacing={2.25}>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 4 }}><CompactStatCard label="Workflows Tracked" value={workflowGroups.length} tone="primary" /></Grid>
        <Grid size={{ xs: 12, sm: 4 }}><CompactStatCard label="Blocked Workflows" value={workflowGroups.filter((workflow) => workflow.status === "Blocked").length} tone="error" /></Grid>
        <Grid size={{ xs: 12, sm: 4 }}><CompactStatCard label="Production-Ready" value={workflowGroups.filter((workflow) => workflow.productionReadinessPercent >= 90).length} tone="success" /></Grid>
      </Grid>

      {workflowGroups.map((workflow) => (
        <CompactFilterCard
          key={workflow.id}
          title={workflow.name}
          subtitle={`${workflow.area} · Release ${workflow.release} · Owner ${workflow.owner}`}
          actions={<ImplementationStatusBadge status={workflow.status} />}
        >
          <Stack spacing={1.2}>
            <Grid container spacing={1.5}>
              <Grid size={{ xs: 12, sm: 4 }}><ImplementationProgressBar label="Completion" value={workflow.completionPercent} tone="primary" /></Grid>
              <Grid size={{ xs: 12, sm: 4 }}><ImplementationProgressBar label="Production readiness" value={workflow.productionReadinessPercent} tone="success" /></Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Box>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>Blockers</Typography>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                    {workflow.blockers.map((blocker) => <Chip key={blocker} size="small" label={blocker} color="warning" variant="outlined" />)}
                  </Stack>
                </Box>
              </Grid>
            </Grid>
            <WorkflowStepStrip steps={workflow.steps} />
            <Typography variant="body2" color="text.secondary">
              {workflow.notes}
            </Typography>
          </Stack>
        </CompactFilterCard>
      ))}
    </Stack>
  );
}

export function FeatureMatrixTab() {
  return (
    <Stack spacing={2}>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 4 }}><CompactStatCard label="Features Tracked" value={features.length} tone="primary" /></Grid>
        <Grid size={{ xs: 12, sm: 4 }}><CompactStatCard label="Complete" value={features.filter((feature) => feature.status === "Complete").length} tone="success" /></Grid>
        <Grid size={{ xs: 12, sm: 4 }}><CompactStatCard label="Pending / Review" value={features.filter((feature) => feature.status === "Pending" || feature.status === "Review").length} tone="warning" /></Grid>
      </Grid>

      <CompactFilterCard title="Feature matrix" subtitle="Backend, frontend, API, UAT, and production readiness are tracked as static Phase 1 data.">
        <CompactTableFrame maxHeight={720}>
          <Table stickyHeader size="small">
            <TableHead>
              <TableRow>
                <TableCell>Feature</TableCell>
                <TableCell>Module</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>Release</TableCell>
                <TableCell>Backend</TableCell>
                <TableCell>Frontend</TableCell>
                <TableCell>API</TableCell>
                <TableCell>UAT</TableCell>
                <TableCell>Production</TableCell>
                <TableCell>Defects</TableCell>
                <TableCell>Notes</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {features.map((feature) => (
                <TableRow key={feature.id} hover>
                  <TableCell sx={{ fontWeight: 700 }}>{feature.name}</TableCell>
                  <TableCell>{feature.module}</TableCell>
                  <TableCell><Chip size="small" label={feature.priority} variant="outlined" color={feature.priority === "Critical" ? "error" : feature.priority === "High" ? "warning" : "default"} /></TableCell>
                  <TableCell>{feature.release}</TableCell>
                  <TableCell><ImplementationStatusBadge status={feature.backendStatus} /></TableCell>
                  <TableCell><ImplementationStatusBadge status={feature.frontendStatus} /></TableCell>
                  <TableCell><ImplementationStatusBadge status={feature.apiStatus} /></TableCell>
                  <TableCell><ImplementationStatusBadge status={feature.uatStatus} /></TableCell>
                  <TableCell><ImplementationStatusBadge status={feature.productionStatus} /></TableCell>
                  <TableCell>{feature.defects}</TableCell>
                  <TableCell sx={{ minWidth: 280 }}>{feature.notes}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CompactTableFrame>
      </CompactFilterCard>
    </Stack>
  );
}

export function UatRegressionTab() {
  const passed = 126;
  const failed = 6;
  const pending = 28;
  const blocked = 4;

  return (
    <Stack spacing={2.25}>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="UAT Progress" value="82%" tone="success" helper="Phase 1 target coverage" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Regression Coverage" value="74%" tone="primary" helper="Core regression subset" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Passed Scenarios" value={passed} tone="success" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Failed Scenarios" value={failed} tone="error" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Pending Scenarios" value={pending} tone="warning" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Blocked Scenarios" value={blocked} tone="error" /></Grid>
      </Grid>

      <CompactFilterCard title="UAT & regression matrix" subtitle="Read-only control center for scenario status, evidence, and execution notes.">
        <CompactTableFrame maxHeight={720}>
          <Table stickyHeader size="small">
            <TableHead>
              <TableRow>
                <TableCell>Scenario</TableCell>
                <TableCell>Module</TableCell>
                <TableCell>Workflow</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Tester</TableCell>
                <TableCell>Evidence</TableCell>
                <TableCell>Notes</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {uatScenarios.map((scenario) => (
                <TableRow key={scenario.id} hover>
                  <TableCell sx={{ fontWeight: 700 }}>{scenario.scenario}</TableCell>
                  <TableCell>{scenario.module}</TableCell>
                  <TableCell>{scenario.workflow}</TableCell>
                  <TableCell><Chip size="small" label={scenario.priority} variant="outlined" color={scenario.priority === "Critical" ? "error" : scenario.priority === "High" ? "warning" : "default"} /></TableCell>
                  <TableCell><Chip size="small" label={scenario.status} variant="outlined" color={statusTone(scenario.status)} /></TableCell>
                  <TableCell>{scenario.tester}</TableCell>
                  <TableCell><Chip size="small" label={scenario.evidenceStatus} variant="outlined" /></TableCell>
                  <TableCell sx={{ minWidth: 320 }}>{scenario.notes}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CompactTableFrame>
      </CompactFilterCard>
    </Stack>
  );
}

export function ProductionReadinessTab() {
  const hardeningChecklist = [
    "Complete RBAC validation",
    "Complete audit for all high-risk actions",
    "Add PDF generation for PO, GRN, invoice, lab report",
    "Add payment workflow",
    "Validate performance for dashboards",
    "Add monitoring and system health",
    "Complete regression suite",
    "Freeze R2 scope before release candidate",
  ];

  return (
    <Stack spacing={2.25}>
      <Grid container spacing={2}>
        {capabilities.map((capability) => (
          <Grid key={capability.id} size={{ xs: 12, sm: 6, xl: 4 }}>
            <CapabilityCoverageCard capability={capability} />
          </Grid>
        ))}
      </Grid>

      <CompactFilterCard title="Production hardening checklist" subtitle="These items are the final gates before the production candidate can be frozen.">
        <Grid container spacing={1.5}>
          {hardeningChecklist.map((item) => (
            <Grid key={item} size={{ xs: 12, md: 6 }}>
              <Paper variant="outlined" sx={{ p: 1.25, borderRadius: 2 }}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <AutoAwesomeMotionRoundedIcon fontSize="small" color="primary" />
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                    {item}
                  </Typography>
                </Stack>
              </Paper>
            </Grid>
          ))}
        </Grid>
      </CompactFilterCard>
    </Stack>
  );
}

export function ReleaseCenterTab() {
  return (
    <Stack spacing={2.25}>
      <Grid container spacing={2}>
        {releases.map((release) => (
          <Grid key={release.id} size={{ xs: 12, lg: 4 }}>
            <ReleaseCard release={release} />
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}

export default function ProductImplementationPage() {
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [area, setArea] = React.useState("All");
  const [status, setStatus] = React.useState("All");
  const [release, setRelease] = React.useState("All");
  const [bucket, setBucket] = React.useState("All");

  const tab = parseTab(searchParams.get("tab"));
  const loading = false;

  const setTab = React.useCallback((next: ProductImplementationTab) => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", next);
    setSearchParams(nextParams, { replace: true });
  }, [searchParams, setSearchParams]);

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  if (loading) {
    return <Stack alignItems="center" sx={{ py: 6 }}><Typography>Loading product implementation…</Typography></Stack>;
  }

  return (
    <Stack spacing={2.5}>
      <Stack spacing={0.75}>
        <Typography variant="h4" sx={{ fontWeight: 900, lineHeight: 1.1 }}>
          Product Implementation
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Living implementation register, release tracker, UAT control center, and production readiness cockpit for Jeevanam.
        </Typography>
      </Stack>

      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent sx={{ py: 1.25, "&:last-child": { pb: 1.25 } }}>
          <ProductImplementationTabs tab={tab} setTab={setTab} />
        </CardContent>
      </Card>

      {tab === "overview" ? <ProductImplementationOverview /> : null}
      {tab === "modules" ? <ModuleRegistryTab area={area} setArea={setArea} status={status} setStatus={setStatus} release={release} setRelease={setRelease} bucket={bucket} setBucket={setBucket} /> : null}
      {tab === "workflows" ? <WorkflowRegistryTab /> : null}
      {tab === "features" ? <FeatureMatrixTab /> : null}
      {tab === "uat" ? <UatRegressionTab /> : null}
      {tab === "readiness" ? <ProductionReadinessTab /> : null}
      {tab === "releases" ? <ReleaseCenterTab /> : null}
    </Stack>
  );
}
