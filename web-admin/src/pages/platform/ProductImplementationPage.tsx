import * as React from "react";
import { useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  LinearProgress,
  Paper,
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

import { useAuth } from "../../auth/useAuth";
import { CompactEmptyState, CompactFilterCard, CompactTableFrame } from "../../components/compact/CompactUi";
import {
  readinessModel,
  type PriorityItem,
  type ReadinessChecklistItem,
  type ReadinessScore,
} from "./productImplementation/readinessModel";

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

function parseTab(value: string | null): ProductImplementationTab {
  if (value === "modules" || value === "workflows" || value === "features" || value === "uat" || value === "readiness" || value === "releases") {
    return value;
  }
  return "overview";
}

function recommendationTone(label: string) {
  if (label === "GO") return "success" as const;
  if (label === "CONDITIONAL GO") return "warning" as const;
  return "error" as const;
}

function scoreTone(score: ReadinessScore) {
  if (score.tone === "success") return "success" as const;
  if (score.tone === "warning") return "warning" as const;
  if (score.tone === "error") return "error" as const;
  if (score.tone === "info") return "info" as const;
  return "primary" as const;
}

function moduleStatusTone(status: string) {
  if (status === "READY") return "success" as const;
  if (status === "READY WITH CONDITIONS") return "warning" as const;
  if (status === "NEEDS FOCUSED UAT") return "warning" as const;
  if (status === "BLOCKED") return "error" as const;
  return "default" as const;
}

function remainingWorkTone(value: string) {
  if (value === "P0") return "error" as const;
  if (value === "P1") return "warning" as const;
  if (value === "P2") return "info" as const;
  return "default" as const;
}

function TabPanel({
  value,
  selected,
  children,
}: {
  value: ProductImplementationTab;
  selected: ProductImplementationTab;
  children: React.ReactNode;
}) {
  if (value !== selected) return null;
  return <Box>{children}</Box>;
}

function ScoreCard({ score }: { score: ReadinessScore }) {
  return (
    <Card variant="outlined" sx={{ borderRadius: 2, height: "100%" }}>
      <CardContent sx={{ "&:last-child": { pb: 2 } }}>
        <Stack spacing={1}>
          <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
            <Chip size="small" label={score.label} color={scoreTone(score)} variant="outlined" />
            <Chip size="small" label={score.statusLabel} color={scoreTone(score)} variant={score.tone === "success" ? "filled" : "outlined"} />
          </Stack>
          <Typography variant="h4" sx={{ fontWeight: 900, lineHeight: 1.05 }}>
            {score.percentage}%
          </Typography>
          <LinearProgress
            variant="determinate"
            value={score.percentage}
            color={score.tone}
            sx={{
              height: 8,
              borderRadius: 999,
              bgcolor: "action.hover",
            }}
          />
          <Typography variant="body2" sx={{ fontWeight: 800 }}>
            {score.statusLabel}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.45 }}>
            {score.helper}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}

function PrioritySection({
  title,
  subtitle,
  items,
  emptyTitle,
  emptySubtitle,
  tone,
}: {
  title: string;
  subtitle: string;
  items: PriorityItem[];
  emptyTitle: string;
  emptySubtitle: string;
  tone: "error" | "warning" | "info" | "success";
}) {
  return (
    <CompactFilterCard title={title} subtitle={subtitle}>
      {items.length ? (
        <Stack spacing={1}>
          {items.map((item) => (
            <Paper key={`${title}-${item.module}-${item.title}`} variant="outlined" sx={{ p: 1.15, borderRadius: 2 }}>
              <Stack spacing={0.5}>
                <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap" useFlexGap>
                  <Chip size="small" label={item.module} color={tone} variant="outlined" />
                  <Typography variant="body2" sx={{ fontWeight: 800 }}>
                    {item.title}
                  </Typography>
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {item.detail}
                </Typography>
              </Stack>
            </Paper>
          ))}
        </Stack>
      ) : (
        <CompactEmptyState title={emptyTitle} subtitle={emptySubtitle} />
      )}
    </CompactFilterCard>
  );
}

function ChecklistSection({
  title,
  subtitle,
  items,
  tone,
}: {
  title: string;
  subtitle: string;
  items: ReadinessChecklistItem[];
  tone: "primary" | "warning" | "info";
}) {
  return (
    <CompactFilterCard title={title} subtitle={subtitle}>
      <Stack spacing={0.75}>
        {items.map((item) => (
          <Paper key={item.label} variant="outlined" sx={{ p: 1.15, borderRadius: 2 }}>
            <Stack spacing={0.35}>
              <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap" useFlexGap>
                <Chip size="small" label={item.label} color={tone} variant="outlined" />
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {item.detail}
              </Typography>
            </Stack>
          </Paper>
        ))}
      </Stack>
    </CompactFilterCard>
  );
}

function ReadinessPreamble() {
  return (
    <Stack spacing={1.25}>
      <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
        <Chip size="small" label={`Last assessed ${readinessModel.lastAssessmentDate}`} color="primary" variant="outlined" />
        <Chip size="small" label={readinessModel.releaseTarget} color="info" variant="outlined" />
        <Chip size="small" label={readinessModel.pilotRecommendation.label} color={recommendationTone(readinessModel.pilotRecommendation.label)} variant={readinessModel.pilotRecommendation.label === "GO" ? "filled" : "outlined"} />
        <Chip size="small" label="Evidence-based static dashboard" variant="outlined" />
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.55 }}>
        Detailed evidence and release criteria are maintained in `PRODUCT_READINESS.md` at the repository root.
      </Typography>
    </Stack>
  );
}

function PilotRecommendationBanner() {
  return (
    <Alert severity={recommendationTone(readinessModel.pilotRecommendation.label)} variant="outlined" sx={{ borderRadius: 2 }}>
      <Stack spacing={0.5}>
        <Typography variant="body2" sx={{ fontWeight: 900 }}>
          Pilot recommendation: {readinessModel.pilotRecommendation.label}
        </Typography>
        <Typography variant="body2">
          {readinessModel.pilotRecommendation.rationale}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Controlled pilot posture: core OPD workflows can proceed with conditions; the broader platform still has P0 and P1 work remaining.
        </Typography>
      </Stack>
    </Alert>
  );
}

function ModuleMatrixTable() {
  return (
    <CompactFilterCard
      title="Module readiness matrix"
      subtitle="Implementation, integration, UAT confidence, pilot status, and the main remaining gap are captured from the current repository assessment."
    >
      <CompactTableFrame maxHeight={760}>
        <Table stickyHeader size="small">
          <TableHead>
            <TableRow>
              <TableCell>Module</TableCell>
              <TableCell>Implementation</TableCell>
              <TableCell>Integration</TableCell>
              <TableCell>UAT confidence</TableCell>
              <TableCell>Pilot status</TableCell>
              <TableCell>Remaining work</TableCell>
              <TableCell>Top gap</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {readinessModel.moduleRows.map((row) => (
              <TableRow key={row.module} hover>
                <TableCell sx={{ fontWeight: 800, whiteSpace: "nowrap" }}>{row.module}</TableCell>
                <TableCell>
                  <Chip size="small" label={`${row.implementationPercent}%`} color={row.implementationPercent >= 90 ? "success" : row.implementationPercent >= 75 ? "primary" : "warning"} variant="outlined" />
                </TableCell>
                <TableCell>
                  <Chip size="small" label={`${row.integrationPercent}%`} color={row.integrationPercent >= 88 ? "success" : row.integrationPercent >= 70 ? "primary" : "warning"} variant="outlined" />
                </TableCell>
                <TableCell>
                  <Chip size="small" label={`${row.uatConfidencePercent}%`} color={row.uatConfidencePercent >= 85 ? "success" : row.uatConfidencePercent >= 70 ? "primary" : "warning"} variant="outlined" />
                </TableCell>
                <TableCell>
                  <Chip size="small" label={row.pilotStatus} color={moduleStatusTone(row.pilotStatus)} variant={row.pilotStatus === "READY" ? "filled" : "outlined"} />
                </TableCell>
                <TableCell>
                  <Chip size="small" label={row.remainingWorkClass} color={remainingWorkTone(row.remainingWorkClass)} variant="outlined" />
                </TableCell>
                <TableCell sx={{ minWidth: 340 }}>
                  <Typography variant="body2" color="text.secondary">
                    {row.topGap}
                  </Typography>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CompactTableFrame>
    </CompactFilterCard>
  );
}

function CapabilityLists() {
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, lg: 6 }}>
        <CompactFilterCard title="Verified major capabilities" subtitle="These capabilities are already implemented enough to support a controlled pilot, subject to the scope limits and P0 items below.">
          <Stack spacing={0.75}>
            {readinessModel.verifiedCapabilities.map((item) => (
              <Paper key={item} variant="outlined" sx={{ p: 1.1, borderRadius: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  {item}
                </Typography>
              </Paper>
            ))}
          </Stack>
        </CompactFilterCard>
      </Grid>
      <Grid size={{ xs: 12, lg: 6 }}>
        <CompactFilterCard title="Partially verified capabilities" subtitle="These are real implementations, but the evidence trail or operational hardening is still incomplete.">
          <Stack spacing={0.75}>
            {readinessModel.partialCapabilities.map((item) => (
              <Paper key={item} variant="outlined" sx={{ p: 1.1, borderRadius: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  {item}
                </Typography>
              </Paper>
            ))}
          </Stack>
        </CompactFilterCard>
      </Grid>
    </Grid>
  );
}

function PriorityWorkBoard() {
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 6 }}>
        <PrioritySection
          title="P0 Pilot Blockers"
          subtitle="Only items that materially block a controlled pilot belong here."
          items={readinessModel.p0Items}
          emptyTitle="No P0 blockers identified."
          emptySubtitle="The assessment did not find a blocking item, but the current pilot is still conditional because other readiness work remains."
          tone="error"
        />
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <PrioritySection
          title="P1 Pilot Preparation"
          subtitle="High-value work that should be finished before expanding scope."
          items={readinessModel.p1Items}
          emptyTitle="No P1 preparation items found."
          emptySubtitle="The pilot-prep list is currently empty."
          tone="warning"
        />
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <PrioritySection
          title="P2 Pilot Improvements"
          subtitle="Valuable refinements that can follow once the pilot is stable."
          items={readinessModel.p2Items}
          emptyTitle="No P2 improvements found."
          emptySubtitle="The post-pilot improvement list is currently empty."
          tone="info"
        />
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <PrioritySection
          title="P3 Post-Pilot Roadmap"
          subtitle="These are legitimate roadmap items, but they are not pilot gates."
          items={readinessModel.p3Items}
          emptyTitle="No P3 roadmap items found."
          emptySubtitle="The post-pilot roadmap list is currently empty."
          tone="success"
        />
      </Grid>
    </Grid>
  );
}

function Batch5PhaseSection() {
  return (
    <CompactFilterCard
      title="Doctor Consultation AI final phase"
      subtitle="Batch 5 is an integration, refinement, and pilot-readiness phase, not a new feature expansion."
    >
      <Stack spacing={1.25}>
        <Alert severity="info" variant="outlined" sx={{ borderRadius: 2 }}>
          <Typography variant="body2">
            No large new AI feature batch is planned here. The remaining work is completion alignment, provenance, fallback behavior, deterministic safety, persistence, and end-to-end validation.
          </Typography>
        </Alert>
        <Stack spacing={0.75}>
          {readinessModel.batch5Sequence.map((step, index) => (
            <Paper key={step} variant="outlined" sx={{ p: 1.15, borderRadius: 2 }}>
              <Stack direction="row" spacing={1} alignItems="flex-start">
                <Chip size="small" label={`${index + 1}`} color="primary" variant="outlined" />
                <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.5 }}>
                  {step}
                </Typography>
              </Stack>
            </Paper>
          ))}
        </Stack>
      </Stack>
    </CompactFilterCard>
  );
}

function ReadinessChecklistSection() {
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, lg: 7 }}>
        <ChecklistSection
          title="Pilot entry criteria"
          subtitle="These are the minimum gates for a controlled pilot entry."
          items={readinessModel.pilotEntryCriteria}
          tone="primary"
        />
      </Grid>
      <Grid size={{ xs: 12, lg: 5 }}>
        <ChecklistSection
          title="Production hardening"
          subtitle="These are production-only gaps so they do not distort the pilot readiness call."
          items={readinessModel.productionHardening}
          tone="warning"
        />
      </Grid>
    </Grid>
  );
}

function CurrentPhaseSummary() {
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, lg: 6 }}>
        <CompactFilterCard title="Major blockers and next steps" subtitle="These are the remaining actions that most directly shape pilot entry.">
          <Stack spacing={1.2}>
            {readinessModel.knownRisks.map((risk) => (
              <Paper key={risk} variant="outlined" sx={{ p: 1.15, borderRadius: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  {risk}
                </Typography>
              </Paper>
            ))}
          </Stack>
        </CompactFilterCard>
      </Grid>
      <Grid size={{ xs: 12, lg: 6 }}>
        <CompactFilterCard title="Batch 5 conclusion" subtitle="What remains before the consultation-AI phase can be called complete.">
          <Stack spacing={1.1}>
            <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.55 }}>
              Batch 5 is the integration, refinement, and pilot-readiness phase. It should not be treated as a new major feature batch.
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.55 }}>
              The remaining consultation-AI sequence is a controlled pilot validation path: fresh appointment, consultation completion verification, canonical context reuse, provenance and contradictory-observation checks, deterministic safety validation, AI-disabled fallback, and tenant/role/persistence gates.
            </Typography>
          </Stack>
        </CompactFilterCard>
      </Grid>
    </Grid>
  );
}

function ReadinessHistory() {
  return (
    <CompactFilterCard title="Readiness history" subtitle="The current decision is history-aware and should be interpreted against the earlier repository inspection.">
      <CompactTableFrame maxHeight={320}>
        <Table stickyHeader size="small">
          <TableHead>
            <TableRow>
              <TableCell>Date</TableCell>
              <TableCell>Functional Completion</TableCell>
              <TableCell>Pilot Readiness</TableCell>
              <TableCell>Production Readiness</TableCell>
              <TableCell>Major Change</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {readinessModel.history.map((row) => (
              <TableRow key={row.date} hover>
                <TableCell sx={{ whiteSpace: "nowrap", fontWeight: 700 }}>{row.date}</TableCell>
                <TableCell>{row.functionalCompletion}%</TableCell>
                <TableCell>{row.pilotReadiness}%</TableCell>
                <TableCell>{row.productionReadiness}%</TableCell>
                <TableCell>{row.majorChange}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CompactTableFrame>
    </CompactFilterCard>
  );
}

function SourceOfTruthNote() {
  return (
    <CompactFilterCard title="Source of truth" subtitle="Repository-level readiness documentation and the dashboard are intentionally aligned.">
      <Alert severity="info" variant="outlined" sx={{ borderRadius: 2 }}>
        Detailed evidence and release criteria are maintained in `PRODUCT_READINESS.md` at the repository root.
      </Alert>
    </CompactFilterCard>
  );
}

function OverviewTab() {
  return (
    <Stack spacing={2.5}>
      <Grid container spacing={2}>
        {readinessModel.scores.map((score) => (
          <Grid key={score.id} size={{ xs: 12, sm: 6, lg: 4, xl: 2.4 }}>
            <ScoreCard score={score} />
          </Grid>
        ))}
      </Grid>
      <PilotRecommendationBanner />
      <CurrentPhaseSummary />
    </Stack>
  );
}

function ModulesTab() {
  return (
    <Stack spacing={2.5}>
      <ModuleMatrixTable />
      <CapabilityLists />
    </Stack>
  );
}

function WorkflowsTab() {
  return (
    <Stack spacing={2.5}>
      <Batch5PhaseSection />
      <ReadinessChecklistSection />
    </Stack>
  );
}

function FeaturesTab() {
  return (
    <Stack spacing={2.5}>
      <CapabilityLists />
      <CompactFilterCard title="Feature-level summary" subtitle="The implementation is broad, but some features remain pilot-prep or post-pilot items.">
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.55 }}>
          The platform already exposes persisted reasoning, deterministic medication safety, longitudinal memory, provenance, AIVA consultation awareness, and AI-disabled fallback. The remaining work is largely around operational tightening, evidence clarity, and pilot scope discipline.
        </Typography>
      </CompactFilterCard>
    </Stack>
  );
}

function UatTab() {
  return (
    <Stack spacing={2.5}>
      <PriorityWorkBoard />
      <CompactFilterCard title="UAT and regression framing" subtitle="These gates define what still needs exercise before a wider pilot.">
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.55 }}>
          The biggest UAT signal is that the consultation completion flow still needs a backend/frontend agreement. The other priorities are pilot-preparation rather than broad product rewrites.
        </Typography>
      </CompactFilterCard>
    </Stack>
  );
}

function ReadinessTab() {
  return (
    <Stack spacing={2.5}>
      <ReadinessChecklistSection />
      <CurrentPhaseSummary />
      <CompactFilterCard title="Security and tenant-isolation readiness" subtitle="The platform is strong here, but pilot entry still requires a final sweep and production-grade evidence.">
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.55 }}>
          Tenant-scoped request context, RBAC, and mutation-level authorization are already implemented. The remaining work is the final negative-security sweep, broader regression coverage, and production-grade penetration testing.
        </Typography>
      </CompactFilterCard>
    </Stack>
  );
}

function ReleasesTab() {
  return (
    <Stack spacing={2.5}>
      <ReadinessHistory />
      <SourceOfTruthNote />
    </Stack>
  );
}

export default function ProductImplementationPage() {
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  const selectedTab = parseTab(searchParams.get("tab"));

  const handleTabChange = React.useCallback(
    (_event: React.SyntheticEvent, nextTab: ProductImplementationTab) => {
      const next = new URLSearchParams(searchParams);
      next.set("tab", nextTab);
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  return (
    <Stack spacing={2.5}>
      <Stack spacing={0.75}>
        <Typography variant="h4" sx={{ fontWeight: 900, lineHeight: 1.1 }}>
          Product Implementation & Release Readiness
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.5 }}>
          Evidence-based view of implementation, integration, UAT, pilot readiness, and production hardening.
        </Typography>
        <ReadinessPreamble />
      </Stack>

      <Paper variant="outlined" sx={{ borderRadius: 2 }}>
        <Tabs
          value={selectedTab}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons="auto"
          allowScrollButtonsMobile
          aria-label="Product implementation readiness tabs"
          sx={{ px: 1.5, pt: 0.5 }}
        >
          {TABS.map((tab) => (
            <Tab key={tab.value} value={tab.value} label={tab.label} />
          ))}
        </Tabs>
      </Paper>

      <TabPanel value="overview" selected={selectedTab}>
        <OverviewTab />
      </TabPanel>
      <TabPanel value="modules" selected={selectedTab}>
        <ModulesTab />
      </TabPanel>
      <TabPanel value="workflows" selected={selectedTab}>
        <WorkflowsTab />
      </TabPanel>
      <TabPanel value="features" selected={selectedTab}>
        <FeaturesTab />
      </TabPanel>
      <TabPanel value="uat" selected={selectedTab}>
        <UatTab />
      </TabPanel>
      <TabPanel value="readiness" selected={selectedTab}>
        <ReadinessTab />
      </TabPanel>
      <TabPanel value="releases" selected={selectedTab}>
        <ReleasesTab />
      </TabPanel>
    </Stack>
  );
}
