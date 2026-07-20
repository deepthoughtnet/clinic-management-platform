import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("patient engagement separates engagement levels from actionable cohorts", () => {
  const source = readSource("products/carepilot/engagement/PatientEngagementPage.tsx");
  const apiSource = readSource("api/clinicApi.ts");

  assert.ok(source.includes('useSearchParams'));
  assert.ok(source.includes('listCarePilotEngagementProfiles'));
  assert.ok(source.includes('listCarePilotCampaigns'));
  assert.ok(source.includes('All Scored Patients'));
  assert.ok(source.includes('High Engagement'));
  assert.ok(source.includes('Medium Engagement'));
  assert.ok(source.includes('Low Engagement'));
  assert.ok(source.includes('Critical Engagement'));
  assert.ok(source.includes('High-Risk Patients'));
  assert.ok(source.includes('Vaccination Due/Risk'));
  assert.ok(source.includes('const levelCount = levelRows.length;'));
  assert.ok(source.includes('const cohortCount = cohortRows.length;'));
  assert.ok(source.includes('CardActionArea'));
  assert.ok(source.includes('Open Patient'));
  assert.ok(source.includes('patientNumber'));
  assert.ok(source.includes('Last evaluated'));
  assert.ok(source.includes('formatCarePilotDateTime(row.generatedAt, clinicTimeZone)'));
  assert.ok(source.includes('patientCountLabel(levelCount)'));
  assert.ok(source.includes('patientCountLabel(cohortCount)'));
  assert.ok(source.includes('No actionable risk rule triggered.'));
  assert.ok(!source.includes('Low/Critical'));
  assert.ok(apiSource.includes('highEngagementCount: number;'));
  assert.ok(apiSource.includes('criticalEngagementCount: number;'));
  assert.ok(apiSource.includes('cohortCounts: Record<string, number>;'));
  assert.ok(apiSource.includes('selectedLevel: CarePilotEngagementSelection;'));
});
