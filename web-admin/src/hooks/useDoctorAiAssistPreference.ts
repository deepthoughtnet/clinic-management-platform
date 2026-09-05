import * as React from "react";
import type { AuthContextValue } from "../auth/AuthContext";

const DOCTOR_AI_ASSIST_STORAGE_PREFIX = "doctor.ai-assist";

function doctorAiAssistStorageKey(tenantId: string | null | undefined, appUserId: string | null | undefined, username: string | null | undefined) {
  const tenantPart = (tenantId || "").trim();
  const userPart = (appUserId || username || "").trim();
  if (!tenantPart || !userPart) {
    return null;
  }
  return `${DOCTOR_AI_ASSIST_STORAGE_PREFIX}.${tenantPart}.${userPart}`;
}

function readStoredPreference(storageKey: string | null, fallback: boolean): boolean {
  if (!storageKey || typeof window === "undefined") {
    return fallback;
  }
  try {
    const raw = window.localStorage.getItem(storageKey);
    if (raw === null) {
      return fallback;
    }
    const parsed = raw.trim().toLowerCase();
    if (parsed === "true") return true;
    if (parsed === "false") return false;
    return fallback;
  } catch {
    return fallback;
  }
}

function writeStoredPreference(storageKey: string | null, enabled: boolean) {
  if (!storageKey || typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.setItem(storageKey, enabled ? "true" : "false");
  } catch {
    // Ignore storage failures so the workspace remains usable.
  }
}

export function useDoctorAiAssistPreference(
  auth: Pick<AuthContextValue, "tenantId" | "appUserId" | "username">,
): readonly [boolean, React.Dispatch<React.SetStateAction<boolean>>, string | null] {
  const storageKey = React.useMemo(
    () => doctorAiAssistStorageKey(auth.tenantId, auth.appUserId, auth.username),
    [auth.appUserId, auth.tenantId, auth.username],
  );
  const [enabled, setEnabled] = React.useState<boolean>(() => readStoredPreference(storageKey, true));

  React.useEffect(() => {
    setEnabled(readStoredPreference(storageKey, true));
  }, [storageKey]);

  React.useEffect(() => {
    writeStoredPreference(storageKey, enabled);
  }, [enabled, storageKey]);

  return [enabled, setEnabled, storageKey] as const;
}
