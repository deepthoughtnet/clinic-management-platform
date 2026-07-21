import type { CarePilotWebinar, CarePilotWebinarStatus, CarePilotWebinarType } from "../../../api/clinicApi";

export declare function validateWebinarDraft(draft: Record<string, unknown>): Record<string, string>;
export declare function buildWebinarPayload(form: Record<string, unknown>): Partial<CarePilotWebinar> & {
  title: string;
  description: string | null;
  webinarType: CarePilotWebinarType;
  campaignId: string | null;
  webinarUrl: string | null;
  organizerName: string | null;
  organizerEmail: string | null;
  scheduledStartAt: string | null;
  scheduledEndAt: string | null;
  timezone: string;
  capacity: number | null;
  registrationEnabled: boolean;
  reminderEnabled: boolean;
  followupEnabled: boolean;
  tags: string | null;
  status: CarePilotWebinarStatus;
};
export declare function getWebinarDateFieldErrors(draft: Record<string, unknown>): {
  scheduledStartAt?: string;
  scheduledEndAt?: string;
};
