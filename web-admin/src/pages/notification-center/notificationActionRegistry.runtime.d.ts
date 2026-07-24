export type NotificationActionPresentation = {
  label: string;
  route: string;
  targetId: string | null;
};

export declare const NOTIFICATION_ACTION_REGISTRY: Record<string, { label: string; route: string }>;
export declare function normalizeNotificationActionRoute(routeKey: string | null | undefined): string;
export declare function getNotificationActionPresentation(
  actionRoute: string | null | undefined,
  actionLabel?: string | null | undefined,
  actionTargetId?: string | null | undefined,
): NotificationActionPresentation | null;
