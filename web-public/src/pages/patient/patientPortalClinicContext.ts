export type PatientPortalClinicContext = {
  clinicId: string | null;
  clinicCode: string;
  clinicName: string | null;
  clinicSlug: string | null;
  locked: boolean;
  source: "query" | "slug" | "tenant" | "manual" | "route" | "storage" | "dev";
  doctorId: string | null;
  doctorSlug: string | null;
  doctorName: string | null;
  tenantId: string | null;
  isResolved: boolean;
  needsClinicSelection: boolean;
  nextPath: string | null;
};

export type PatientPortalLinkContext = {
  clinicId?: string | null;
  clinicCode?: string | null;
  clinicSlug?: string | null;
  clinicName?: string | null;
  tenantId?: string | null;
  tenantSlug?: string | null;
  doctorId?: string | null;
  doctorSlug?: string | null;
  doctorName?: string | null;
  nextPath?: string | null;
};

export type PatientPortalRouteSelectedClinicState = {
  clinicId: string | null;
  clinicCode: string;
  clinicName: string | null;
  clinicSlug: string | null;
  doctorId: string | null;
  doctorSlug: string | null;
  doctorName: string | null;
  tenantId: string | null;
  tenantSlug: string | null;
  nextPath: string | null;
};

export type PatientPortalRouteState = {
  selectedClinic?: PatientPortalRouteSelectedClinicState | null;
};

export const MISSING_CLINIC_CODE_MESSAGE = "Clinic context could not be resolved. Please go back and select the clinic again.";

const DEMO_CLINIC_CODE = "demo-clinic";
const DEMO_CLINIC_NAME = "Demo Clinic";
const PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY = "clinic-web-public-patient-portal-clinic-context";

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

function normalizeRouteSelectedClinicState(value: unknown): PatientPortalRouteSelectedClinicState | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const source = value as Record<string, unknown>;
  const selectedClinic =
    source.selectedClinic && typeof source.selectedClinic === "object"
      ? (source.selectedClinic as Record<string, unknown>)
      : source;
  const clinicId = normalizeClinicCode(typeof selectedClinic.clinicId === "string" ? selectedClinic.clinicId : null);
  const clinicCode = normalizeClinicCode(
    typeof selectedClinic.clinicCode === "string"
      ? selectedClinic.clinicCode
      : typeof selectedClinic.tenantCode === "string"
        ? selectedClinic.tenantCode
        : typeof selectedClinic.clinicSlug === "string"
          ? selectedClinic.clinicSlug
          : clinicId,
  );
  if (!clinicCode) {
    return null;
  }
  return {
    clinicId: clinicId || null,
    clinicCode,
    clinicName: normalizeClinicCode(typeof selectedClinic.clinicName === "string" ? selectedClinic.clinicName : null) || null,
    clinicSlug: normalizeClinicCode(typeof selectedClinic.clinicSlug === "string" ? selectedClinic.clinicSlug : null) || null,
    doctorId: normalizeClinicCode(typeof selectedClinic.doctorId === "string" ? selectedClinic.doctorId : null) || null,
    doctorSlug: normalizeClinicCode(typeof selectedClinic.doctorSlug === "string" ? selectedClinic.doctorSlug : null) || null,
    doctorName: normalizeClinicCode(typeof selectedClinic.doctorName === "string" ? selectedClinic.doctorName : null) || null,
    tenantId: normalizeClinicCode(typeof selectedClinic.tenantId === "string" ? selectedClinic.tenantId : null) || null,
    tenantSlug: normalizeClinicCode(typeof selectedClinic.tenantSlug === "string" ? selectedClinic.tenantSlug : null) || null,
    nextPath: normalizeNextPath(typeof selectedClinic.nextPath === "string" ? selectedClinic.nextPath : null) || null,
  };
}

function readStoredClinicContext(): PatientPortalRouteSelectedClinicState | null {
  if (typeof window === "undefined") {
    return null;
  }
  for (const storage of [window.sessionStorage, window.localStorage]) {
    try {
      const raw = storage.getItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY);
      if (!raw) {
        continue;
      }
      const parsed = JSON.parse(raw) as unknown;
      const normalized = normalizeRouteSelectedClinicState(parsed);
      if (normalized) {
        return normalized;
      }
    } catch {
      // Ignore malformed stored context.
    }
  }
  return null;
}

export function persistPatientPortalClinicContext(context: PatientPortalLinkContext | PatientPortalRouteSelectedClinicState | null) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    if (!context) {
      window.sessionStorage.removeItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY);
      window.localStorage.removeItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY);
      return;
    }
    const clinicCode = normalizeClinicCode(context.clinicCode || context.clinicSlug);
    if (!clinicCode) {
      return;
    }
    const payload = JSON.stringify({
      clinicId: normalizeClinicCode(context.clinicId) || null,
      clinicCode,
      clinicName: normalizeClinicCode(context.clinicName) || null,
      clinicSlug: normalizeClinicCode(context.clinicSlug) || null,
      doctorId: normalizeClinicCode(context.doctorId) || null,
      doctorSlug: normalizeClinicCode(context.doctorSlug) || null,
      doctorName: normalizeClinicCode(context.doctorName) || null,
      tenantId: normalizeClinicCode(context.tenantId) || null,
      tenantSlug: normalizeClinicCode(context.tenantSlug) || null,
      nextPath: normalizeNextPath(context.nextPath) || null,
    });
    window.sessionStorage.setItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY, payload);
    window.localStorage.setItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY, payload);
  } catch {
    // Ignore storage failures.
  }
}

function normalizeNextPath(value: string | null | undefined): string | null {
  const normalized = normalizeClinicCode(value);
  if (!normalized || !normalized.startsWith("/")) {
    return null;
  }
  return normalized;
}

function resolvePatientPortalDevContext(): PatientPortalClinicContext | null {
  if (typeof window === "undefined") {
    return null;
  }
  const hostname = window.location.hostname.toLowerCase();
  if (hostname !== "localhost" && hostname !== "127.0.0.1") {
    return null;
  }
  return {
    clinicId: null,
    clinicCode: DEMO_CLINIC_CODE,
    clinicName: DEMO_CLINIC_NAME,
    clinicSlug: DEMO_CLINIC_CODE,
    locked: true,
    source: "dev",
    doctorId: null,
    doctorSlug: null,
    doctorName: null,
    tenantId: null,
    isResolved: true,
    needsClinicSelection: false,
    nextPath: null,
  };
}

export function resolvePatientPortalContext(
  searchParams: URLSearchParams,
  routeState?: unknown,
): PatientPortalClinicContext {
  const routeSelectedClinic = normalizeRouteSelectedClinicState(routeState);
  const storedClinic = readStoredClinicContext();
  const clinicId = normalizeClinicCode(searchParams.get("clinicId"));
  const queryCode = normalizeClinicCode(searchParams.get("clinicCode"));
  const clinicCodeAlias = normalizeClinicCode(searchParams.get("clinic"));
  const querySlug = normalizeClinicCode(searchParams.get("clinicSlug"));
  const tenantId = normalizeClinicCode(searchParams.get("tenantId"));
  const tenantCode = normalizeClinicCode(searchParams.get("tenant"));
  const tenantSlug = normalizeClinicCode(searchParams.get("tenantSlug"));
  const doctorId = normalizeClinicCode(searchParams.get("doctorId"));
  const doctorSlug = normalizeClinicCode(searchParams.get("doctorSlug"));
  const doctorName = normalizeClinicCode(searchParams.get("doctorName"));
  const clinicCode =
    routeSelectedClinic?.clinicCode
      || clinicId
      || queryCode
      || clinicCodeAlias
      || querySlug
      || tenantCode
      || tenantSlug
      || storedClinic?.clinicCode
      || storedClinic?.clinicSlug
      || "";
  const clinicName = routeSelectedClinic?.clinicName
    || normalizeClinicCode(searchParams.get("clinicName"))
    || storedClinic?.clinicName
    || null;
  const resolvedDoctorId = routeSelectedClinic?.doctorId || doctorId || storedClinic?.doctorId || null;
  const resolvedDoctorSlug = routeSelectedClinic?.doctorSlug
    || doctorSlug
    || storedClinic?.doctorSlug
    || null;
  const resolvedDoctorName = routeSelectedClinic?.doctorName
    || doctorName
    || storedClinic?.doctorName
    || null;
  const nextPath = routeSelectedClinic?.nextPath
    || normalizeNextPath(searchParams.get("next"))
    || storedClinic?.nextPath
    || null;
  const clinicSlug = routeSelectedClinic?.clinicSlug
    || querySlug
    || storedClinic?.clinicSlug
    || clinicCode;
  const resolvedTenantId = routeSelectedClinic?.tenantId
    || tenantId
    || storedClinic?.tenantId
    || null;
  const needsClinicSelection = Boolean((resolvedDoctorSlug || resolvedDoctorId) && !clinicCode);
  if (!clinicCode) {
    const devFallback = resolvePatientPortalDevContext();
    if (devFallback) {
      return devFallback;
    }
  }
  return {
    clinicId: routeSelectedClinic?.clinicId || clinicId || storedClinic?.clinicId || null,
    clinicCode,
    clinicName,
    clinicSlug: clinicSlug || null,
    locked: Boolean(clinicCode),
    source: routeSelectedClinic
      ? "route"
      : clinicId || queryCode || clinicCodeAlias
        ? "query"
      : querySlug
        ? "slug"
        : tenantId || tenantCode || tenantSlug
          ? "tenant"
          : storedClinic
            ? "storage"
          : "manual",
    doctorId: resolvedDoctorId,
    doctorSlug: resolvedDoctorSlug,
    doctorName: resolvedDoctorName,
    tenantId: resolvedTenantId,
    isResolved: Boolean(clinicCode),
    needsClinicSelection,
    nextPath,
  };
}

export function resolvePatientPortalClinicContext(
  searchParams: URLSearchParams,
  routeState?: unknown,
): PatientPortalClinicContext {
  return resolvePatientPortalContext(searchParams, routeState);
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
    clinicId: context.clinicId,
    clinicCode: context.clinicCode || context.clinicSlug,
    clinic: context.clinicCode || context.clinicSlug,
    clinicSlug: context.clinicSlug,
    clinicName: context.clinicName,
    tenantId: context.tenantId,
    tenant: context.tenantSlug || context.tenantId,
    tenantSlug: context.tenantSlug,
    doctorId: context.doctorId || context.doctorSlug,
    doctorSlug: context.doctorSlug,
    doctorName: context.doctorName,
  });
}

function createBookingRouteState(context?: PatientPortalLinkContext): PatientPortalRouteState | undefined {
  const clinicCode = normalizeClinicCode(context?.clinicCode || context?.clinicSlug);
  if (!clinicCode) {
    return undefined;
  }
  return {
    selectedClinic: {
      clinicId: normalizeClinicCode(context?.clinicId) || null,
      clinicCode,
      clinicName: normalizeClinicCode(context?.clinicName) || null,
      clinicSlug: normalizeClinicCode(context?.clinicSlug) || null,
      doctorId: normalizeClinicCode(context?.doctorId) || null,
      doctorSlug: normalizeClinicCode(context?.doctorSlug) || null,
      doctorName: normalizeClinicCode(context?.doctorName) || null,
      tenantId: normalizeClinicCode(context?.tenantId) || null,
      tenantSlug: normalizeClinicCode(context?.tenantSlug) || null,
      nextPath: normalizeNextPath(context?.nextPath) || null,
    },
  };
}

export function withPatientPortalClinicContext(path: string, context?: PatientPortalLinkContext) {
  if (!context) {
    return path;
  }
  return appendQuery(path, {
    clinicId: context.clinicId,
    clinicCode: context.clinicCode || context.clinicSlug,
    clinic: context.clinicCode || context.clinicSlug,
    clinicSlug: context.clinicSlug,
    clinicName: context.clinicName,
    tenantId: context.tenantId,
    tenant: context.tenantSlug || context.tenantId,
    tenantSlug: context.tenantSlug,
    doctorId: context.doctorId || context.doctorSlug,
    doctorSlug: context.doctorSlug,
    doctorName: context.doctorName,
  });
}

export function patientPortalBookingTo(
  session: import("../../api/patientPortal").PatientPortalSession | null,
  context?: PatientPortalLinkContext,
) {
  const nextPath = withPatientPortalClinicContext("/patient/book-appointment", context);
  const loginContext = {
    ...context,
    nextPath,
  };
  const bookingState = createBookingRouteState(context);

  if (session?.sessionRole === "patient") {
    return nextPath;
  }
  if (session?.sessionRole === "registration") {
    return `/patient/register?next=${encodeURIComponent(nextPath)}`;
  }
  return {
    pathname: "/patient/login",
    search: patientPortalLoginPath(loginContext).replace("/patient/login", ""),
    state: bookingState,
  };
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
