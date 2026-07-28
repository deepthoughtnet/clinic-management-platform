import {
  type PatientPortalRegistrationSession,
  type PatientPortalSession,
  isPatientPortalRegistrationSession,
} from "../../api/patientPortal";
import { clearPublicBookingContext } from "./patientPortalClinicContext";

export const PATIENT_PORTAL_SESSION_STORAGE_KEY = "clinic-web-public-patient-session";
export const PATIENT_PORTAL_PENDING_REGISTRATION_STORAGE_KEY = "clinic-web-public-patient-portal-pending-registration";
export const PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY = "clinic-web-public-patient-portal-clinic-context";
const LEGACY_REGISTRATION_KEYS = [
  "verifiedMobile",
  "patientOtpSession",
  "patientRegistrationToken",
  "registrationRequired",
  "pendingPatientRegistration",
  "temporaryPatientSession",
  "staleOnboardingState",
] as const;

const REGISTRATION_SESSION_TTL_MS = 30 * 60 * 1000;

export function isPatientRegistrationSessionActive(
  session: PatientPortalRegistrationSession | null | undefined,
  now = Date.now(),
) {
  if (!isPatientPortalRegistrationSession(session)) {
    return false;
  }
  const createdAtMs = Date.parse(session.createdAt);
  if (!Number.isFinite(createdAtMs)) {
    return false;
  }
  return now - createdAtMs < REGISTRATION_SESSION_TTL_MS;
}

export function isStoredPatientSessionActive(
  session: PatientPortalSession | null | undefined,
  now = Date.now(),
) {
  if (!session) {
    return false;
  }
  if (isPatientPortalRegistrationSession(session)) {
    return isPatientRegistrationSessionActive(session, now);
  }
  return true;
}

function clearKeys(storage: Storage, keys: readonly string[]) {
  keys.forEach((key) => storage.removeItem(key));
}

export function clearPatientRegistrationSession(options?: {
  clearBookingContext?: boolean;
  clearAllPatientSessions?: boolean;
}) {
  if (typeof window === "undefined") {
    return;
  }
  const { clearBookingContext = true, clearAllPatientSessions = true } = options ?? {};
  for (const storage of [window.sessionStorage, window.localStorage]) {
    clearKeys(storage, LEGACY_REGISTRATION_KEYS);
    storage.removeItem(PATIENT_PORTAL_PENDING_REGISTRATION_STORAGE_KEY);
    if (clearBookingContext) {
      storage.removeItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY);
    }
  }

  const rawSession = window.localStorage.getItem(PATIENT_PORTAL_SESSION_STORAGE_KEY);
  if (!rawSession) {
    return;
  }
  try {
    const parsed = JSON.parse(rawSession) as PatientPortalSession;
    if (clearAllPatientSessions || isPatientPortalRegistrationSession(parsed)) {
      window.localStorage.removeItem(PATIENT_PORTAL_SESSION_STORAGE_KEY);
    }
  } catch {
    window.localStorage.removeItem(PATIENT_PORTAL_SESSION_STORAGE_KEY);
  }
}

export function clearPatientAuthSession() {
  clearPatientRegistrationSession({
    clearBookingContext: true,
    clearAllPatientSessions: true,
  });
  clearPublicBookingContext();
}
