export type WorkflowStatusTone = "default" | "primary" | "secondary" | "info" | "success" | "warning" | "error";

export type WorkflowActionContext = {
  status?: string | null;
  paymentStatus?: string | null;
  feeStatus?: string | null;
  billStatus?: string | null;
  billDueAmount?: number | null;
};

export type PatientJourneyContext = WorkflowActionContext & {
  appointmentStatus?: string | null;
  consultationStatus?: string | null;
  prescriptionStatus?: string | null;
  labStatus?: string | null;
  pharmacyStatus?: string | null;
  followUpScheduled?: boolean | null;
};

export type PatientJourneyStage =
  | "APPOINTMENT"
  | "REGISTRATION"
  | "PAYMENT"
  | "CHECK_IN"
  | "WAITING"
  | "CONSULTATION"
  | "PRESCRIPTION"
  | "LABORATORY"
  | "PHARMACY"
  | "BILLING_COMPLETE"
  | "COMPLETED";

export function normalizeWorkflowValue(value?: string | null): string;
export function getWorkflowStatusLabel(status?: string | null): string | null;
export function getWorkflowStatusTone(status?: string | null): WorkflowStatusTone;
export function getAppointmentTokenValue(appointment?: { displayReference?: string | null; tokenNumber?: number | null; token?: string | null } | null): string | null;
export function getAppointmentTokenLabel(appointment?: { displayReference?: string | null; tokenNumber?: number | null; token?: string | null } | null): string;
export function formatRelativeBookingTime(createdAt?: string | Date | null, now?: string | number | Date): string | null;
export function getNextWorkflowAction(context?: WorkflowActionContext): { key: string; label: string; tone: WorkflowStatusTone; helperText: string | null };
export function derivePatientJourneyStage(context?: PatientJourneyContext): PatientJourneyStage;
export function getPatientJourneyStageLabel(stage: PatientJourneyStage): string;
export function getPatientJourneyStages(): PatientJourneyStage[];
export function getPatientJourneyStageIndex(stage?: string | null): number;
