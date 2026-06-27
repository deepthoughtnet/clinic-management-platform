import type { NotificationHistory, Patient } from "../../api/clinicApi";

export declare function formatNotificationTargetLabel(
  row: Pick<NotificationHistory, "patientId" | "recipient">,
  patients: Patient[],
): string;

export declare function formatNotificationSourceLabel(
  row: Pick<NotificationHistory, "subject" | "sourceType" | "eventType">,
): string;
