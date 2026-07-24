import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import {
  buildNotificationCenterInboxQuery,
  buildNotificationCenterSearchParams,
  formatNotificationDateKey,
  formatNotificationExactTimestamp,
  formatNotificationRelativeTime,
  getNotificationActionPresentation,
  getNotificationCategoryPresentation,
  getNotificationPriorityPresentation,
  normalizeNotificationPage,
  normalizeNotificationPreview,
  normalizeNotificationSummary,
  parseNotificationCenterRouteState,
} from "../src/pages/notification-center/notificationCenterModel.js";
import {
  getNotificationActionPresentation as getRegistryActionPresentation,
  normalizeNotificationActionRoute,
} from "../src/pages/notification-center/notificationActionRegistry.runtime.js";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("notification center page is URL-driven, summary-backed, and detail-capable", () => {
  const page = readSource("pages/notification-center/NotificationCenterPage.tsx");
  const app = readSource("app/App.tsx");
  const topBar = readSource("layout/TopBar.tsx");

  assert.ok(page.includes("useSearchParams"));
  assert.ok(page.includes("My Notifications"));
  assert.ok(page.includes("NOTIFICATION_CENTER_TAB_OPTIONS"));
  assert.ok(page.includes("getNotificationCenterSummary"));
  assert.ok(page.includes("getNotificationCenterInbox"));
  assert.ok(page.includes("getNotificationCenterItem"));
  assert.ok(page.includes("TablePagination"));
  assert.ok(page.includes("Technical details"));
  assert.ok(page.includes("Mark all as read"));
  assert.ok(page.includes("Tabs"));
  assert.ok(page.includes("Drawer"));
  assert.ok(page.includes("Unread"));
  assert.ok(page.includes("Requires Action"));
  assert.ok(page.includes("Today"));
  assert.ok(page.includes("formatNotificationRelativeTime"));
  assert.ok(page.includes("formatNotificationExactTimestamp"));
  assert.ok(page.includes("CompactFilterCard"));
  assert.ok(page.includes("CompactEmptyState"));
  assert.ok(page.includes("CompactTableFrame"));
  assert.ok(page.includes("Reset filters"));
  assert.ok(page.includes("normalizeNotificationSummary(summaryRes)"));
  assert.ok(page.includes("normalizeNotificationPage(pageRes)"));
  assert.ok(page.includes("updateNotificationCenterPageMarkAll"));
  assert.ok(page.includes("routeState.page > maxPage"));
  assert.ok(page.includes("CardContent"));
  assert.ok(page.includes("useMediaQuery"));
  assert.ok(page.includes("CardActionArea") === false);
  assert.ok(app.includes('path="/notification-center"'));
  assert.ok(app.includes("My Notifications"));
  assert.ok(app.includes("RouteErrorBoundary"));
  assert.ok(app.indexOf("<AppShell>") < app.indexOf("<RouteErrorBoundary>"));
  assert.ok(app.includes("Something went wrong"));
  assert.ok(topBar.includes("Mark all as read"));
  assert.ok(topBar.includes("Notifications,"));
});

test("top bar bell is accessible and keeps notification-center preview flow compact", () => {
  const topBar = readSource("layout/TopBar.tsx");
  const page = readSource("pages/notification-center/NotificationCenterPage.tsx");

  assert.ok(topBar.includes("Notifications,"));
  assert.ok(topBar.includes("aria-label={buttonLabel}"));
  assert.ok(topBar.includes("aria-haspopup=\"menu\""));
  assert.ok(topBar.includes("Mark all as read"));
  assert.ok(topBar.includes("View all notifications"));
  assert.ok(topBar.includes("Popover"));
  assert.ok(topBar.includes("Badge"));
  assert.ok(topBar.includes("NotificationsRoundedIcon"));
  assert.ok(topBar.includes("formatNotificationRelativeTime"));
  assert.ok(topBar.includes("formatNotificationExactTimestamp"));
  assert.ok(topBar.includes("setItems((current) => current.filter((row) => row.id !== item.id))"));
  assert.ok(topBar.includes("setItems([])"));
  assert.ok(!topBar.includes('toast ? <Alert severity={toast.severity} variant="filled" sx={{ width: "100%" }}>{toast.message}</Alert> : <></>'));
  assert.ok(topBar.includes('visibility: toast ? "visible" : "hidden"'));
  assert.ok(!page.includes('snackbar ? <Alert severity={snackbar.severity} variant="filled" sx={{ width: "100%" }}>{snackbar.message}</Alert> : <></>'));
  assert.ok(page.includes('visibility: snackbar ? "visible" : "hidden"'));
});

test("notification center helper model normalizes labels, routing, and time formatting", () => {
  const routeState = parseNotificationCenterRouteState(new URLSearchParams("tab=requires-action&category=APPOINTMENT&priority=CRITICAL&search=smita&from=2026-07-24&to=2026-07-24&page=2&size=25&notificationId=n1"));
  assert.equal(routeState.tab, "requires-action");
  assert.equal(routeState.category, "APPOINTMENT");
  assert.equal(routeState.priority, "CRITICAL");
  assert.equal(routeState.search, "smita");
  assert.equal(routeState.from, "2026-07-24");
  assert.equal(routeState.to, "2026-07-24");
  assert.equal(routeState.page, 2);
  assert.equal(routeState.size, 25);
  assert.equal(routeState.notificationId, "n1");

  const nextParams = buildNotificationCenterSearchParams({
    tab: "unread",
    search: "Amit Verma",
    category: "BILLING",
    priority: "HIGH",
    from: "2026-07-24",
    to: "2026-07-24",
    page: 1,
    size: 50,
    notificationId: "abc",
  });
  assert.equal(nextParams.get("tab"), "unread");
  assert.equal(nextParams.get("search"), "Amit Verma");
  assert.equal(nextParams.get("category"), "BILLING");
  assert.equal(nextParams.get("priority"), "HIGH");
  assert.equal(nextParams.get("notificationId"), "abc");

  const exactTime = formatNotificationExactTimestamp("2026-07-24T04:30:00.000Z", "Asia/Kolkata");
  assert.equal(exactTime, "24 Jul 2026, 10:00 AM");
  assert.equal(formatNotificationRelativeTime("2026-07-24T10:00:00.000Z", "UTC", "2026-07-24T10:00:30.000Z"), "Now");
  assert.equal(formatNotificationRelativeTime("2026-07-23T10:00:00.000Z", "UTC", "2026-07-24T10:00:00.000Z"), "Yesterday");
  assert.equal(formatNotificationRelativeTime("2026-07-22T10:00:00.000Z", "UTC", "2026-07-24T10:00:00.000Z"), "2 days ago");
  assert.equal(formatNotificationDateKey("2026-07-23T18:30:00.000Z", "Asia/Kolkata"), "2026-07-24");

  assert.equal(getNotificationCategoryPresentation("appointment").label, "Appointments");
  assert.equal(getNotificationCategoryPresentation("unknown").label, "Other");
  assert.equal(getNotificationPriorityPresentation("critical").label, "Critical");
  assert.equal(getNotificationPriorityPresentation("unrecognised").label, "Normal");

  const action = getNotificationActionPresentation("/appointments/day-board", "Open day board", "123");
  assert.equal(action?.route, "/appointments/day-board");
  assert.equal(action?.label, "Open day board");
  assert.equal(action?.targetId, "123");
  assert.equal(getNotificationActionPresentation("/not-supported"), null);

  assert.equal(normalizeNotificationActionRoute("  /billing?tenant=1  "), "/billing");
  assert.equal(normalizeNotificationActionRoute("open_payment"), "OPEN_PAYMENT");

  const registryAction = getRegistryActionPresentation("OPEN_NOTIFICATION_DETAIL", null, "notif-123");
  assert.equal(registryAction?.route, "/notification-center?notificationId=notif-123");
  assert.equal(getRegistryActionPresentation("/patients", null, "patient-123")?.route, "/patients/patient-123");
});

test("notification center mark-all API handles 204 and empty 200 responses without JSON parse crashes", async () => {
  const restClient = readSource("api/restClient.ts");
  const clinicApi = readSource("api/clinicApi.ts");
  assert.ok(restClient.includes("if (res.status === 204)"));
  assert.ok(restClient.includes("return undefined as T;"));
  assert.ok(clinicApi.includes('return httpPost<NotificationCenterUnreadCount>("/api/notification-center/read-all"'));
});

test("notification center helper model preserves response shapes for optimistic updates", () => {
  assert.deepEqual(normalizeNotificationSummary(undefined), {
    unreadCount: 0,
    requiresActionCount: 0,
    criticalCount: 0,
    todayCount: 0,
  });
  assert.deepEqual(normalizeNotificationSummary({
    unreadCount: "3",
    requiresActionCount: null,
    criticalCount: undefined,
    todayCount: 7,
  }), {
    unreadCount: 3,
    requiresActionCount: 0,
    criticalCount: 0,
    todayCount: 7,
  });

  assert.deepEqual(normalizeNotificationPreview({ items: null }).items, []);
  assert.deepEqual(normalizeNotificationPreview({ items: [null, { id: "n1" }] }).items, [{ id: "n1" }]);

  assert.deepEqual(normalizeNotificationPage(undefined), {
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  assert.deepEqual(normalizeNotificationPage({
    items: undefined,
    page: "2",
    size: "0",
    totalElements: "5",
    totalPages: "-1",
  }), {
    items: [],
    page: 2,
    size: 20,
    totalElements: 5,
    totalPages: 0,
  });
});
