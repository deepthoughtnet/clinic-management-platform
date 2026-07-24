import type { NotificationCenterCategory, NotificationCenterPriority, NotificationCenterQuery } from "../../api/clinicApi";

export type NotificationCenterTab = "all" | "unread" | "requires-action";

export type NotificationCenterRouteState = {
  tab: NotificationCenterTab;
  search: string;
  category: NotificationCenterCategory | "";
  priority: NotificationCenterPriority | "";
  from: string;
  to: string;
  page: number;
  size: number;
  notificationId: string;
};

export type NotificationCenterCategoryPresentation = {
  label: string;
  iconKey: "calendar" | "clinical" | "lab" | "pharmacy" | "billing" | "system" | "engage" | "ai" | "other";
  tone: "primary" | "secondary" | "success" | "warning" | "error" | "info" | "default";
};

export type NotificationCenterPriorityPresentation = {
  label: string;
  iconKey: "low" | "normal" | "high" | "critical";
  tone: "default" | "warning" | "error";
};

export type NotificationCenterActionPresentation = {
  label: string;
  route: string;
  targetId: string | null;
};

export declare const NOTIFICATION_CENTER_TAB_OPTIONS: ReadonlyArray<{ key: NotificationCenterTab; label: string }>;
export declare const NOTIFICATION_CENTER_CATEGORY_OPTIONS: ReadonlyArray<{ value: NotificationCenterCategory | ""; label: string }>;
export declare const NOTIFICATION_CENTER_PRIORITY_OPTIONS: ReadonlyArray<{ value: NotificationCenterPriority | ""; label: string }>;
export declare const NOTIFICATION_CENTER_QUERY_KEYS: {
  root(tenantId?: string | null, userId?: string | null): string[];
  unreadCount(tenantId?: string | null, userId?: string | null): string[];
  preview(tenantId?: string | null, userId?: string | null): string[];
  summary(tenantId?: string | null, userId?: string | null): string[];
  list(tenantId?: string | null, userId?: string | null, filters?: unknown): string[];
  detail(tenantId?: string | null, userId?: string | null, notificationId?: string | null): string[];
};
export declare const NOTIFICATION_CENTER_REFRESH_EVENT = "notification-center:refresh";

export declare function normalizeNotificationCategory(value: string | null | undefined): NotificationCenterCategory | "OTHER";
export declare function normalizeNotificationPriority(value: string | null | undefined): NotificationCenterPriority | "NORMAL";
export declare function getNotificationCategoryPresentation(value: string | null | undefined): NotificationCenterCategoryPresentation;
export declare function getNotificationPriorityPresentation(value: string | null | undefined): NotificationCenterPriorityPresentation;
export declare function getNotificationActionPresentation(
  actionRoute: string | null | undefined,
  actionLabel?: string | null | undefined,
  actionTargetId?: string | null | undefined,
): NotificationCenterActionPresentation | null;
export declare function formatNotificationExactTimestamp(value: string | null | undefined, timeZone?: string | null): string;
export declare function formatNotificationRelativeTime(value: string | null | undefined, timeZone?: string | null, nowValue?: string | null): string;
export declare function formatNotificationDateKey(value: string | null | undefined, timeZone?: string | null): string;
export declare function buildNotificationCenterInboxQuery(filters: {
  tab?: NotificationCenterTab;
  category?: NotificationCenterCategory | "";
  priority?: NotificationCenterPriority | "";
  search?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}, timeZone?: string | null): NotificationCenterQuery;
export declare function parseNotificationCenterRouteState(searchParams?: URLSearchParams | null): NotificationCenterRouteState;
export declare function buildNotificationCenterSearchParams(state?: Partial<NotificationCenterRouteState> | null): URLSearchParams;
export declare function formatNotificationCenterPageTitle(): string;
