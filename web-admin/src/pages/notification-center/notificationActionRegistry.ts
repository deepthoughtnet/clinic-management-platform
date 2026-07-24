export type NotificationActionPresentation = {
  label: string;
  route: string;
  targetId: string | null;
};

export { NOTIFICATION_ACTION_REGISTRY, getNotificationActionPresentation, normalizeNotificationActionRoute } from "./notificationActionRegistry.runtime.js";
