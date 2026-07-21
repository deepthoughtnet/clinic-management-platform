import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("webinar page uses view permission for the shell, drafts tab, and defers privileged reads", () => {
  const app = readSource("app/App.tsx");
  const nav = readSource("layout/nav.ts");
  const page = readSource("products/carepilot/webinars/WebinarsPage.tsx");

  assert.ok(app.includes('path="/carepilot/webinars"'));
  assert.ok(app.includes('anyPermissions={[ENGAGE_WEBINAR_VIEW, ENGAGE_WEBINAR_VIEW_ANALYTICS, ENGAGE_WEBINAR_VIEW_AUDIT]}'));
  assert.ok(nav.includes('permissionsAny: [ENGAGE_WEBINAR_VIEW, ENGAGE_WEBINAR_VIEW_ANALYTICS, ENGAGE_WEBINAR_VIEW_AUDIT]'));
  assert.ok(page.includes('useSearchParams'));
  assert.ok(page.includes('type WebinarTab = "DRAFTS" | "UPCOMING" | "LIVE" | "COMPLETED" | "CANCELLED";'));
  assert.ok(page.includes('const WEBINAR_STATUS_OPTIONS: Array<{ value: CarePilotWebinarStatus | ""; label: string }>'));
  assert.ok(page.includes('label: "Draft"'));
  assert.ok(page.includes('label: "Upcoming"'));
  assert.ok(page.includes('function statusToTab(status: CarePilotWebinarStatus): WebinarTab {'));
  assert.ok(page.includes('function tabToStatus(tab: WebinarTab): CarePilotWebinarStatus | "" {'));
  assert.ok(page.includes('webinarTabLabel(item)'));
  assert.ok(page.includes('webinarTabEmptyState(tab)'));
  assert.ok(page.includes('setTabAndUrl'));
  assert.ok(page.includes('setStatusAndMaybeTab'));
  assert.ok(page.includes('DRAFTS'));
  assert.ok(page.includes('const canViewAnalytics = auth.hasPermission(ENGAGE_WEBINAR_VIEW_ANALYTICS);'));
  assert.ok(page.includes('const canViewCampaigns = auth.hasPermission("engage.campaign.view")'));
  assert.ok(page.includes('const [pageError, setPageError] = React.useState<string | null>(null);'));
  assert.ok(page.includes('const [pageAccessDenied, setPageAccessDenied] = React.useState(false);'));
  assert.ok(page.includes('if (pageAccessDenied)'));
  assert.ok(page.includes('setPageAccessDenied(true);'));
  assert.ok(page.includes('setPageError(err instanceof Error ? err.message : "Failed to load webinars");'));
  assert.ok(page.includes('if (canViewAnalytics)'));
  assert.ok(page.includes('getCarePilotWebinarAnalyticsSummary(auth.accessToken, auth.tenantId)'));
  assert.ok(page.includes('const loadCampaigns = React.useCallback(async () => {'));
  assert.ok(page.includes('if (!auth.accessToken || !auth.tenantId || !canViewCampaigns) return;'));
  assert.ok(page.includes('void loadCampaigns();'));
  assert.ok(page.includes('formatCarePilotDateTimeInput(row.scheduledStartAt, row.timezone)'));
  assert.ok(page.includes('formatCarePilotDateTimeInput(row.scheduledEndAt, row.timezone)'));
  assert.ok(page.includes('const visibleRows = React.useMemo(() => {'));
  assert.ok(page.includes('const basicSummary = React.useMemo(() => {'));
  assert.ok(page.includes('if (tab === "DRAFTS") filtered = filtered.filter((r) => r.status === "DRAFT");'));
  assert.ok(page.includes('if (tab === "UPCOMING") filtered = filtered.filter((r) => r.status === "SCHEDULED");'));
  assert.ok(page.includes('formatCarePilotDateTime(row.scheduledStartAt, row.timezone)'));
  assert.ok(page.includes('formatCarePilotDateTime(row.scheduledEndAt, row.timezone)'));
  assert.ok(page.includes('webinarTypeLabel(row.webinarType)'));
  assert.ok(page.includes('webinarStatusLabel(row.status)'));
  assert.ok(page.includes('No draft webinars found.'));
  assert.ok(page.includes('No upcoming webinars found.'));
  assert.ok(page.includes('navigate(`/carepilot/leads?tab=converted&status=converted&leadId=${encodeURIComponent(row.leadId)}`)'));
  assert.ok(page.includes('row.status === "DRAFT" ? <Button size="small" onClick={() => void quickStatus(row, "SCHEDULED")}>Publish</Button> : null'));
  assert.ok(page.includes('row.status === "SCHEDULED" ? <Button size="small" onClick={() => void quickStatus(row, "LIVE")}>Start</Button> : null'));
  assert.ok(page.includes('loading ? <Box sx={{ minHeight: 220, display: "grid", placeItems: "center" }}><CircularProgress /></Box> : null'));
  assert.ok(page.includes('!loading && pageError'));
  assert.ok(page.includes('visibleRows.length === 0'));
  assert.ok(!page.includes('No webinars found for selected filters.'));
  assert.ok(!page.includes('>{row.status}</Chip>'));
  assert.ok(!page.includes('>{row.webinarType}</TableCell>'));
});
