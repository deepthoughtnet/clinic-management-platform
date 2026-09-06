import type { PatientPortalNotificationResponse } from "../../api/patientPortal";

export type DashboardActivityItem = {
  id: string;
  title: string;
  detail: string;
  createdAt: string;
  sourceKey: string | null;
};

export declare function buildRecentDashboardActivityItems(
  notifications: PatientPortalNotificationResponse[],
  limit?: number,
): DashboardActivityItem[];

export declare function dashboardNotificationDetail(
  notification: PatientPortalNotificationResponse,
): string;

export declare function dashboardNotificationTitle(
  notification: PatientPortalNotificationResponse,
): string;

export declare function isAttentionNotification(
  notification: PatientPortalNotificationResponse,
): boolean;

export declare function isRecentActivityNotification(
  notification: PatientPortalNotificationResponse,
): boolean;

export declare function notificationSourceKey(
  notification: PatientPortalNotificationResponse,
): string | null;
