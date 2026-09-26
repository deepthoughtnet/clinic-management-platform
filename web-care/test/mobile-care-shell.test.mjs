import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const pages = fs.readFileSync(path.join(root, "src/pages/patient/PatientPortalPages.tsx"), "utf8");
const app = fs.readFileSync(path.join(root, "src/App.tsx"), "utf8");
const shell = fs.readFileSync(path.join(root, "src/components/CareShell.tsx"), "utf8");
const styles = fs.readFileSync(path.join(root, "src/styles.css"), "utf8");

test("mobile Care navigation groups the portal into Home, Visits, AIVA, Records, and More", () => {
  assert.match(pages, /const mobilePatientNavItems = \[/);
  for (const label of ["Home", "Visits", "AIVA", "Records", "More"]) {
    assert.match(pages, new RegExp(`label: "${label}"`));
  }
  assert.match(pages, /function isMobilePatientNavActive\(/);
  assert.match(styles, /grid-template-columns: repeat\(5, minmax\(0, 1fr\)\)/);
  assert.match(styles, /\.patient-portal-page \.patient-sidebar \{[\s\S]*display: none/);
});

test("Records and More are presentation-only entries using existing routes", () => {
  assert.match(app, /path="\/patient\/records" element={<PatientRecordsPage/);
  assert.match(app, /path="\/patient\/more" element={<PatientMorePage/);
  assert.match(pages, /to="\/patient\/prescriptions"/);
  assert.match(pages, /to="\/patient\/lab"/);
  assert.match(pages, /to="\/patient\/bills"/);
  assert.match(pages, /to="\/patient\/notifications"/);
  assert.match(pages, /to="\/patient\/dashboard#care-network"/);
  assert.match(pages, /to="\/patient\/profile"/);
  assert.match(pages, /to="\/help-centre"/);
  assert.match(pages, /to="\/privacy-policy"/);
});

test("mobile header retains notification and profile access", () => {
  assert.match(shell, /care-authenticated-header__mobile-actions/);
  assert.match(shell, /to="\/patient\/notifications" aria-label="Notifications"/);
  assert.match(shell, /<PatientProfileMenu session={session}/);
});

test("authenticated mobile Care surfaces use compact shared refinement rules", () => {
  assert.match(shell, /care-footer--authenticated/);
  assert.match(styles, /\.patient-portal-page \.patient-panel,[\s\S]*padding: 0\.85rem/);
  assert.match(styles, /\.patient-appointments-page \.patient-filter-row \{[\s\S]*flex-wrap: nowrap;[\s\S]*overflow-x: auto/);
  assert.match(styles, /\.patient-appointments-page \.patient-record-card \.patient-inline-note \{[\s\S]*display: none/);
  assert.match(styles, /\.patient-notifications-page \.portal-list-card,[\s\S]*padding: 0\.75rem/);
  assert.match(styles, /\.care-footer--authenticated \.care-footer__column:nth-child\(4\)/);
});

test("notifications use the patient-scoped persisted read contract and surface failures", () => {
  const api = fs.readFileSync(path.join(root, "src/api/patientPortal.ts"), "utf8");
  assert.match(api, /markPatientNotificationRead[\s\S]*notifications\/\$\{id\}\/read/);
  assert.match(api, /method: "POST"/);
  assert.match(api, /buildHeaders\(session\)/);
  assert.match(pages, /await markPatientNotificationRead\(portalSession, id\)/);
  assert.match(pages, /updatedNotification\.readAt/);
  assert.match(pages, /setReadAtOverrides/);
  assert.match(pages, /catch \{[\s\S]*Unable to mark this notification as read/);
  assert.match(pages, /setRefreshKey\(\(current\) => current \+ 1\)/);
});

test("notifications have compact metrics, state styling, and intrinsic action buttons", () => {
  assert.match(pages, /className={`portal-list-card \$\{isRead\(notification\) \? "is-read" : "is-unread"\}`}/);
  assert.match(styles, /\.patient-notifications-page \.portal-list \{[\s\S]*max-height: min\(calc\(100dvh - 15rem\), 42rem\);[\s\S]*overflow-y: auto/);
  assert.match(styles, /\.patient-notifications-page \.portal-list-card\.is-unread/);
  assert.match(styles, /\.patient-notifications-page \.portal-list-card \.cta-row > \* \{[\s\S]*flex: 0 1 auto/);
});

test("Care mobile typography and alignment use a shared scoped scale", () => {
  assert.match(styles, /\.patient-portal-page \{[\s\S]*--care-text-page:[\s\S]*--care-text-meta:/);
  assert.match(styles, /\.patient-portal-page \.patient-panel-heading h2,[\s\S]*font-size: var\(--care-text-section\)/);
  assert.match(styles, /\.patient-portal-page \.record-card-meta,[\s\S]*font-size: var\(--care-text-meta\)/);
  assert.match(styles, /\.patient-portal-page \.status-pill \{[\s\S]*white-space: nowrap/);
  assert.match(styles, /\.patient-portal-page \.patient-dashboard-network \.patient-panel-heading,[\s\S]*display: grid/);
});

test("AIVA mobile composer reuses existing text and voice handlers", () => {
  assert.doesNotMatch(pages, /patient-careai-mobile-voice-actions/);
  assert.match(pages, /className={`patient-careai-tool-card patient-careai-voice-card voice-session-card/);
  assert.match(pages, /onClick={handleVoiceStart}/);
  assert.match(pages, /onClick={handleVoiceToggleMute}/);
  assert.match(pages, /onClick={handleVoiceEndSession}/);
  assert.match(pages, /className="primary-button patient-careai-send-button"[^>]*type="submit"/);
  assert.match(styles, /\.patient-careai-page \.voice-session-card:not\(\.is-active\) \{[\s\S]*display: none/);
});

test("AIVA mobile interaction modes are presentation-only and default to Text", () => {
  assert.match(pages, /const \[interactionMode, setInteractionMode\] = useState<"text" \| "voice">\("text"\)/);
  assert.match(pages, /patient-careai-mode-selector/);
  assert.match(pages, /Text/);
  assert.match(pages, /Voice/);
  assert.match(pages, /handleInteractionModeChange\("voice"\)/);
  assert.match(pages, /handleInteractionModeChange\("text"\)/);
  assert.match(pages, /setInteractionMode\("text"\)/);
  assert.match(styles, /\.patient-careai-page \.aiva-mobile-voice-mode \.aiva-mobile-text-composer/);
  assert.match(styles, /\.patient-careai-page \.aiva-mobile-voice-mode \.voice-session-card\.is-mobile-voice-mode/);
});

test("AIVA mobile mode contract switches the rendered surface without changing the conversation", () => {
  assert.match(pages, /className={`patient-panel patient-panel-wide patient-careai-shell aiva-workspace-card aiva-mobile-\$\{interactionMode\}-mode`}/);
  assert.match(pages, /className="patient-careai-form patient-careai-composer aiva-composer aiva-mobile-text-composer"/);
  assert.match(pages, /className={`patient-careai-tool-card patient-careai-voice-card voice-session-card\$\{interactionMode === "voice" \? " is-mobile-voice-mode" : ""\}/);
  assert.match(styles, /\.patient-careai-page \.aiva-mobile-voice-mode \.aiva-mobile-text-composer \{[\s\S]*display: none/);
  assert.match(styles, /\.patient-careai-page \.aiva-mobile-text-mode \.voice-session-card \{[\s\S]*display: none/);
  assert.match(styles, /\.patient-careai-page \.aiva-mobile-voice-mode \.voice-session-card\.is-mobile-voice-mode \{[\s\S]*display: grid/);
  assert.match(pages, /function handleInteractionModeChange\(nextMode: "text" \| "voice"\)/);
  assert.doesNotMatch(pages, /handleInteractionModeChange\([\s\S]*setV2ConversationId\(null\)/);
});

test("AIVA mobile transcript is bounded and independently scrollable", () => {
  assert.match(styles, /\.patient-careai-page \.patient-careai-conversation \{[\s\S]*height: auto;[\s\S]*min-height: 0;/);
  assert.match(styles, /\.patient-careai-page \.aiva-transcript \{[\s\S]*min-height: 10rem;[\s\S]*max-height: 42dvh;[\s\S]*overflow-y: auto;[\s\S]*overscroll-behavior: contain;/);
  assert.match(styles, /\.patient-careai-page \.aiva-mobile-voice-mode \.aiva-transcript \{[\s\S]*max-height: 34dvh;/);
});

test("mode switching reuses existing voice lifecycle handlers without auto-starting", () => {
  assert.match(pages, /function handleInteractionModeChange\(nextMode: "text" \| "voice"\)/);
  assert.match(pages, /handleVoiceEndSession\(\);[\s\S]*setInteractionMode\(nextMode\)/);
  assert.match(pages, /onClick=\{handleVoiceStart\}/);
  assert.match(pages, /onClick=\{handleVoiceToggleMute\}/);
  assert.match(pages, /onClick=\{handleVoiceEndSession\}/);
  assert.doesNotMatch(pages, /handleInteractionModeChange\([\s\S]*handleVoiceStart\(\)/);
});

test("desktop navigation remains separate from the mobile five-item navigation", () => {
  assert.match(pages, /patientNavItems\.map/);
  assert.match(pages, /mobilePatientNavItems\.map/);
  assert.match(styles, /\.patient-mobile-nav \{[\s\S]*display: none/);
  assert.match(styles, /\.patient-mobile-nav \{[\s\S]*display: grid[\s\S]*repeat\(5/);
});

test("Care desktop and tablet pages share a responsive shell topology", () => {
  assert.match(styles, /@media \(min-width: 760px\) \{[\s\S]*--care-shell-gap:[\s\S]*--care-page-title-size:/);
  assert.match(styles, /\.patient-portal-page \.patient-portal-shell \{[\s\S]*gap: var\(--care-shell-gap\)/);
  assert.match(styles, /\.patient-portal-page \.patient-topbar h1 \{[\s\S]*font-size: var\(--care-page-title-size\)/);
  assert.match(styles, /@media \(min-width: 760px\) and \(max-width: 979px\)/);
  assert.match(styles, /@media \(min-width: 980px\)/);
});

test("Appointments uses the shared search/action row and natural page-flow cards", () => {
  assert.match(pages, /className="patient-appointments-search-actions"/);
  assert.match(pages, /className="primary-button" to="\/patient\/book-appointment"/);
  assert.equal((pages.match(/To cancel or reschedule, please use AIVA or contact receptionist\./g) || []).length, 1);
  assert.match(styles, /\.patient-appointments-page \.patient-main \{[\s\S]*overflow: visible/);
  assert.match(styles, /\.patient-appointments-page \.patient-appointment-list \{[\s\S]*max-height: none;[\s\S]*overflow: visible/);
  assert.match(styles, /\.patient-appointments-page \.patient-appointments-search-actions \{[\s\S]*grid-template-columns: minmax\(0, 1fr\) auto/);
});
