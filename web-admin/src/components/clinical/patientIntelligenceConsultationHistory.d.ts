export interface ConsultationHistoryEntry {
  consultationId: string;
  consultationDate: string | null;
  status: string | null;
  doctor: string | null;
  primaryText: string | null;
  followUp: string | null;
  detail: string | null;
  isCurrent: boolean;
}

export function buildConsultationHistoryEntries(
  previousVisits: Array<{
    consultationId?: string | null;
    id?: string | null;
    createdAt?: string | null;
    consultationDate?: string | null;
    status?: string | null;
    doctorName?: string | null;
    diagnosis?: string | null;
    chiefComplaints?: string | null;
    treatmentSummary?: string | null;
    clinicalNotes?: string | null;
    advice?: string | null;
    followUpDate?: string | null;
  }> | null | undefined,
  currentConsultation: {
    id: string;
    createdAt: string;
    completedAt: string | null;
    status: string;
    doctorName: string | null;
    diagnosis: string | null;
    chiefComplaints: string | null;
    clinicalNotes: string | null;
    advice: string | null;
    followUpDate: string | null;
  } | null | undefined,
  currentConsultationId: string | null | undefined,
): ConsultationHistoryEntry[];

export function getVisibleConsultationHistoryItems<T>(items: T[], expanded: boolean, limit: number): T[];
export function shouldShowConsultationHistoryToggle(total: number, limit: number): boolean;
export function getConsultationHistorySectionTitle(total: number): string;
export function getConsultationHistoryToggleLabel(expanded: boolean): string;
