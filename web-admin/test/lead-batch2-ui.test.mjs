import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("lead shared labels drive source, status, priority, and timeline presentation", () => {
  const page = readSource("products/carepilot/leads/LeadsPage.tsx");
  const formatting = readSource("products/carepilot/shared/leadFormatting.ts");
  const campaignField = readSource("products/carepilot/components/CampaignLookupField.tsx");

  assert.ok(formatting.includes('label: "Website"'));
  assert.ok(formatting.includes('label: "Follow-up Required"'));
  assert.ok(formatting.includes('label: "High"'));
  assert.ok(formatting.includes('Follow-up Scheduled'));
  assert.ok(formatting.includes('LEAD_SELECT_MENU_PROPS'));
  assert.ok(formatting.includes('formatLeadTimelineDescription'));
  assert.ok(formatting.includes('Source:'));
  assert.ok(formatting.includes('Status:'));
  assert.ok(formatting.includes('Next follow-up:'));

  assert.ok(page.includes('LEAD_SELECT_MENU_PROPS'));
  assert.ok(page.includes('LEAD_SOURCE_OPTIONS.map'));
  assert.ok(page.includes('LEAD_STATUS_OPTIONS.map'));
  assert.ok(page.includes('LEAD_PRIORITY_OPTIONS.map'));
  assert.ok(page.includes('leadSourceLabel(lead.source)'));
  assert.ok(page.includes('leadStatusLabel(lead.status)'));
  assert.ok(page.includes('leadPriorityLabel(lead.priority)'));
  assert.ok(page.includes('leadActivityLabel(a.activityType)'));
  assert.ok(page.includes('formatLeadTimelineDescription(a, clinicTimeZone)'));
  assert.ok(page.includes('title={leadSourceLabel(lead.source)}'));
  assert.ok(page.includes('label={leadStatusLabel(lead.status)}'));
  assert.ok(page.includes('label={leadPriorityLabel(lead.priority)}'));
  assert.ok(page.includes('label={leadActivityLabel(a.activityType)}'));
  assert.ok(page.includes('role="alert"'));
  assert.ok(page.includes('aria-live="assertive"'));
  assert.ok(page.includes('Please correct'));
  assert.ok(page.includes('highlighted field'));
  assert.ok(page.includes('Optional. Assign this lead to an active Engage user.'));
  assert.ok(page.includes('Select assignee') || page.includes('renderValue'));
  assert.ok(page.includes('Example: Google Search, referral name, event name'));
  assert.ok(page.includes('Add tags separated by commas.'));
  assert.ok(page.includes('Tenant time:'));
  assert.ok(page.includes('—'));
  assert.ok(page.includes('component="time"'));
  assert.ok(page.includes('dateTime={a.createdAt}'));
  assert.ok(!page.includes('>{s}</MenuItem>'));
  assert.ok(!page.includes('Unknown user'));
  assert.ok(!page.includes('Source: {lead.source}'));
  assert.ok(!page.includes('Next follow-up: {lead.nextFollowUpAt ? formatLeadDateTime(lead.nextFollowUpAt) : "-"'));
  assert.ok(campaignField.includes('placeholder = "Select campaign"'));
  assert.ok(campaignField.includes('placeholder={placeholder}'));
});

test("lead validation summary and focus behavior are accessible and non-destructive", () => {
  const page = readSource("products/carepilot/leads/LeadsPage.tsx");
  const utils = readSource("products/carepilot/leads/leadFormUtils.js");

  assert.ok(page.includes('validationSummaryMessage(Object.keys(validation.fieldErrors).length)'));
  assert.ok(page.includes('validationSummaryMessage(Object.keys(mappedErrors).length)'));
  assert.ok(page.includes('setSaveError(summary)'));
  assert.ok(page.includes('setToast(summary)'));
  assert.ok(page.includes('focusLeadField(Object.keys(validation.fieldErrors)[0])'));
  assert.ok(page.includes('focusLeadField(Object.keys(mappedErrors)[0])'));
  assert.ok(page.includes('editorContentRef.current?.querySelector'));
  assert.ok(page.includes('scrollIntoView({ block: "center" })'));
  assert.ok(page.includes('Optional follow-up date and time.'));
  assert.ok(page.includes('Optional. Associate this lead with a campaign.'));
  assert.ok(page.includes('Optional. Assign this lead to an active Engage user.'));
  assert.ok(page.includes('Optional follow-up date and time. Tenant time:'));
  assert.ok(page.includes('Optional note saved to the lead timeline.'));
  assert.ok(page.includes('followUpDraft.note.trim() || null'));
  assert.ok(utils.includes('Enter a valid 10-digit mobile number.'));
  assert.ok(utils.includes('LEAD_PHONE_MESSAGE'));
});
