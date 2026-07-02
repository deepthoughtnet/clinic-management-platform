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
  criticalBlockers,
  defects,
  features,
  overviewNotes,
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
  const moduleHighlights = [
    { name: "Reception", percent: 98 },
    { name: "Doctor Consultation", percent: 95 },
    { name: "Billing", percent: 94 },
    { name: "Pharmacy", percent: 97 },
    { name: "Inventory", percent: 97 },
    { name: "Laboratory", percent: 45 },
    { name: "Patient Portal", percent: 90 },
    { name: "Platform", percent: 85 },
  ];

  const releaseTrend = releases.filter((release) => release.id === "r2" || release.id === "r3" || release.id === "r4");
  const totalOpenDefects = defects.filter((defect) => defect.status !== "Closed" && defect.status !== "Resolved").length;
  const criticalDefectCount = defects.filter((defect) => defect.priority === "Critical" && defect.status !== "Closed" && defect.status !== "Resolved").length;
  const totalTechnicalDebt = productModules.reduce((sum, module) => sum + module.technicalDebt, 0);
  const blockedAreas = new Set(productModules.filter((module) => module.status === "Blocked").map((module) => module.area)).size;

  return (
    <Stack spacing={2.5}>
      <WorkflowGuide
        title="Jeevanam Product OS"
        subtitle="R2 Implementation Register"
        steps={[
          { label: "Modules", tone: "primary" },
          { label: "Workflows", tone: "primary" },
          { label: "UAT", tone: "primary" },
          { label: "Production Readiness", tone: "primary" },
          { label: "Release", tone: "primary" },
        ]}
      />

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Overall Completion" value="92%" tone="success" helper="Static phase 1 register" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Production Readiness" value="88%" tone="primary" helper="Cross-cutting hardening baseline" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="R2 UAT Progress" value="82%" tone="warning" helper="Regression and UAT phase" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Open Defects" value={totalOpenDefects} tone="warning" helper={`${criticalDefectCount} critical`} /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Technical Debt" value={totalTechnicalDebt} tone="info" helper="Module-level aggregate" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Modules Tracked" value={productModules.length} tone="primary" helper="Across platform and product areas" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Blocked Areas" value={blockedAreas} tone="error" helper="Laboratory remains blocked" /></Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}><ImplementationKpiCard label="Release Tracks" value={releases.length} tone="primary" helper="R2 / R3 / R4 roadmap" /></Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 7 }}>
          <CompactFilterCard title="Module maturity summary" subtitle="The highest-signal modules for R2 are tracked here as a release cockpit.">
            <Grid container spacing={1.5}>
              {moduleHighlights.map((module) => (
                <Grid key={module.name} size={{ xs: 12, sm: 6 }}>
                  <Card variant="outlined" sx={{ borderRadius: 2 }}>
                    <CardContent sx={{ p: 1.2, "&:last-child": { pb: 1.2 } }}>
                      <Stack spacing={1}>
                        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 800 }}>{module.name}</Typography>
                            <Typography variant="caption" color="text.secondary">Product maturity band</Typography>
                          </Box>
                          <ImplementationStatusBadge status={module.percent >= 95 ? "Complete" : module.percent >= 80 ? "In Progress" : module.percent >= 50 ? "Review" : "Blocked"} />
                        </Stack>
                        <ImplementationProgressBar label="Completion" value={module.percent} tone={module.percent >= 95 ? "success" : module.percent >= 80 ? "primary" : module.percent >= 50 ? "warning" : "error"} />
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </CompactFilterCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 5 }}>
          <CompactFilterCard title="Release readiness trend" subtitle="Static placeholder that will later be connected to persisted release telemetry.">
            <Stack spacing={1.5}>
              {releaseTrend.map((release) => (
                <ImplementationProgressBar key={release.id} label={release.name} value={release.progressPercent} tone={release.id === "r2" ? "success" : "primary"} />
              ))}
            </Stack>
          </CompactFilterCard>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 6 }}>
          <CompactFilterCard title="Critical blockers" subtitle="These items are the current release and hardening constraints.">
            <Stack spacing={1}>
              {criticalBlockers.map((blocker) => (
                <Stack key={blocker} direction="row" spacing={1} alignItems="center">
                  <Chip size="small" label="Blocker" color="error" variant="outlined" sx={{ borderRadius: 999 }} />
                  <Typography variant="body2">{blocker}</Typography>
                </Stack>
              ))}
            </Stack>
          </CompactFilterCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <CompactFilterCard title="Recent implementation notes" subtitle="Static notes for the living register.">
            <Stack spacing={1}>
              {overviewNotes.map((note) => (
                <Typography key={note} variant="body2" color="text.secondary">
                  • {note}
                </Typography>
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

      <CompactFilterCard title="Roadmap posture" subtitle="R2 is production-candidate sized; R3 and R4 remain scoped and visible.">
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
