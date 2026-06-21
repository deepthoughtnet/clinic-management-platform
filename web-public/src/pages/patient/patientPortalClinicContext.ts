export type PatientPortalClinicContext = {
  clinicCode: string;
  clinicName: string | null;
  locked: boolean;
  source: "query" | "slug" | "tenant" | "manual";
  doctorSlug: string | null;
  doctorName: string | null;
  nextPath: string | null;
};

export type PatientPortalLinkContext = {
  clinicCode?: string | null;
  clinicSlug?: string | null;
  clinicName?: string | null;
  doctorSlug?: string | null;
  doctorName?: string | null;
  nextPath?: string | null;
};

export const MISSING_CLINIC_CODE_MESSAGE = "Please select a clinic first or enter the clinic code.";

export function normalizeClinicCode(value: string | null | undefined): string {
  return (value ?? "").trim();
}

export function normalizeClinicCodeFallback(value: string | null | undefined): string {
  const normalized = normalizeClinicCode(value);
  if (!normalized) {
    return "";
  }
  if (/^[a-z0-9][a-z0-9-]{0,59}$/i.test(normalized)) {
    return normalized;
  }
  try {
    const url = new URL(normalized, window.location.origin);
    const directValues = [
      url.searchParams.get("clinicCode"),
      url.searchParams.get("clinic"),
      url.searchParams.get("clinicSlug"),
      url.searchParams.get("tenant"),
      url.searchParams.get("tenantSlug"),
    ];
    for (const candidate of directValues) {
      const trimmed = normalizeClinicCode(candidate);
      if (trimmed && /^[a-z0-9][a-z0-9-]{0,59}$/i.test(trimmed)) {
        return trimmed;
      }
    }
    const pathSlug = normalizeClinicCode(url.pathname.split("/").filter(Boolean).at(-1));
    if (pathSlug && /^[a-z0-9][a-z0-9-]{0,59}$/i.test(pathSlug)) {
      return pathSlug;
    }
  } catch {
    return "";
  }
  return "";
}

function normalizeNextPath(value: string | null | undefined): string | null {
  const normalized = normalizeClinicCode(value);
  if (!normalized || !normalized.startsWith("/")) {
    return null;
  }
  return normalized;
}

export function resolvePatientPortalClinicContext(searchParams: URLSearchParams): PatientPortalClinicContext {
  const queryCode = normalizeClinicCode(searchParams.get("clinicCode"));
  const clinicCodeAlias = normalizeClinicCode(searchParams.get("clinic"));
  const querySlug = normalizeClinicCode(searchParams.get("clinicSlug"));
  const tenantCode = normalizeClinicCode(searchParams.get("tenant"));
  const tenantSlug = normalizeClinicCode(searchParams.get("tenantSlug"));
  const clinicCode = queryCode || clinicCodeAlias || querySlug || tenantCode || tenantSlug;
  const clinicName = normalizeClinicCode(searchParams.get("clinicName")) || null;
  const doctorSlug = normalizeClinicCode(searchParams.get("doctorSlug")) || null;
  const doctorName = normalizeClinicCode(searchParams.get("doctorName")) || null;
  const nextPath = normalizeNextPath(searchParams.get("next"));
  return {
    clinicCode,
    clinicName,
    locked: Boolean(clinicCode),
    source: queryCode || clinicCodeAlias ? "query" : querySlug ? "slug" : tenantCode || tenantSlug ? "tenant" : "manual",
    doctorSlug,
    doctorName,
    nextPath,
  };
}

function appendQuery(path: string, params: Record<string, string | null | undefined>) {
  const url = new URL(path, window.location.origin);
  for (const [key, value] of Object.entries(params)) {
    const normalized = normalizeClinicCode(value);
    if (normalized) {
      url.searchParams.set(key, normalized);
    }
  }
  return `${url.pathname}${url.search}`;
}

export function patientPortalLoginPath(context?: PatientPortalLinkContext) {
  if (!context) {
    return "/patient/login";
  }
  return appendQuery("/patient/login", {
    next: context.nextPath,
    clinicCode: context.clinicCode || context.clinicSlug,
    clinicName: context.clinicName,
    doctorSlug: context.doctorSlug,
    doctorName: context.doctorName,
  });
}

export function withPatientPortalClinicContext(path: string, context?: PatientPortalLinkContext) {
  if (!context) {
    return path;
  }
  return appendQuery(path, {
    clinicCode: context.clinicCode || context.clinicSlug,
    clinicName: context.clinicName,
    doctorSlug: context.doctorSlug,
    doctorName: context.doctorName,
  });
}

export function patientPortalBookingPath(
  session: import("../../api/patientPortal").PatientPortalSession | null,
  context?: PatientPortalLinkContext,
) {
  const nextPath = withPatientPortalClinicContext("/patient/book-appointment", context);
  const loginContext = {
    ...context,
    nextPath,
  };
  const registrationPath = `/patient/register?next=${encodeURIComponent(nextPath)}`;

  if (session?.sessionRole === "patient") {
    return nextPath;
  }
  if (session?.sessionRole === "registration") {
    return registrationPath;
  }
  return patientPortalLoginPath(loginContext);
}
