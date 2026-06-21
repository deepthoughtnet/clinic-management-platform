export type PatientPortalClinicContext = {
  clinicCode: string;
  clinicName: string | null;
  locked: boolean;
  source: "query" | "slug" | "tenant" | "manual" | "route" | "dev";
  doctorSlug: string | null;
  doctorName: string | null;
  nextPath: string | null;
};

export type PatientPortalLinkContext = {
  clinicId?: string | null;
  clinicCode?: string | null;
  clinicSlug?: string | null;
  clinicName?: string | null;
  tenantId?: string | null;
  doctorSlug?: string | null;
  doctorName?: string | null;
  nextPath?: string | null;
};

export type PatientPortalRouteSelectedClinicState = {
  clinicId: string | null;
  clinicCode: string;
  clinicName: string | null;
  doctorSlug: string | null;
  doctorName: string | null;
  tenantId: string | null;
  nextPath: string | null;
};

export type PatientPortalRouteState = {
  selectedClinic?: PatientPortalRouteSelectedClinicState | null;
};

export const MISSING_CLINIC_CODE_MESSAGE = "Please select a clinic or doctor before requesting OTP.";

const DEMO_CLINIC_CODE = "demo-clinic";
const DEMO_CLINIC_NAME = "Demo Clinic";

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
          : typeof selectedClinic.tenantId === "string"
            ? selectedClinic.tenantId
          : clinicId,
  );
  if (!clinicCode) {
    return null;
  }
  return {
    clinicId: clinicId || null,
    clinicCode,
    clinicName: normalizeClinicCode(typeof selectedClinic.clinicName === "string" ? selectedClinic.clinicName : null) || null,
    doctorSlug: normalizeClinicCode(typeof selectedClinic.doctorSlug === "string" ? selectedClinic.doctorSlug : null) || null,
    doctorName: normalizeClinicCode(typeof selectedClinic.doctorName === "string" ? selectedClinic.doctorName : null) || null,
    tenantId: normalizeClinicCode(typeof selectedClinic.tenantId === "string" ? selectedClinic.tenantId : null) || null,
    nextPath: normalizeNextPath(typeof selectedClinic.nextPath === "string" ? selectedClinic.nextPath : null) || null,
  };
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
    clinicCode: DEMO_CLINIC_CODE,
    clinicName: DEMO_CLINIC_NAME,
    locked: true,
    source: "dev",
    doctorSlug: null,
    doctorName: null,
    nextPath: null,
  };
}

export function resolvePatientPortalClinicContext(
  searchParams: URLSearchParams,
  routeState?: unknown,
): PatientPortalClinicContext {
  const routeSelectedClinic = normalizeRouteSelectedClinicState(routeState);
  const clinicId = normalizeClinicCode(searchParams.get("clinicId"));
  const queryCode = normalizeClinicCode(searchParams.get("clinicCode"));
  const clinicCodeAlias = normalizeClinicCode(searchParams.get("clinic"));
  const querySlug = normalizeClinicCode(searchParams.get("clinicSlug"));
  const tenantId = normalizeClinicCode(searchParams.get("tenantId"));
  const tenantCode = normalizeClinicCode(searchParams.get("tenant"));
  const tenantSlug = normalizeClinicCode(searchParams.get("tenantSlug"));
  const clinicCode =
    routeSelectedClinic?.clinicCode || clinicId || queryCode || clinicCodeAlias || querySlug || tenantId || tenantCode || tenantSlug;
  const clinicName = routeSelectedClinic?.clinicName || normalizeClinicCode(searchParams.get("clinicName")) || null;
  const doctorSlug = routeSelectedClinic?.doctorSlug || normalizeClinicCode(searchParams.get("doctorSlug")) || null;
  const doctorName = routeSelectedClinic?.doctorName || normalizeClinicCode(searchParams.get("doctorName")) || null;
  const nextPath = routeSelectedClinic?.nextPath || normalizeNextPath(searchParams.get("next"));
  if (!clinicCode) {
    const devFallback = resolvePatientPortalDevContext();
    if (devFallback) {
      return devFallback;
    }
  }
  return {
    clinicCode,
    clinicName,
    locked: Boolean(clinicCode),
    source: routeSelectedClinic
      ? "route"
      : clinicId || queryCode || clinicCodeAlias
        ? "query"
      : querySlug
        ? "slug"
        : tenantId || tenantCode || tenantSlug
          ? "tenant"
          : "manual",
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
    clinicId: context.clinicId,
    clinicCode: context.clinicCode || context.clinicSlug,
    clinicName: context.clinicName,
    tenantId: context.tenantId,
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
      doctorSlug: normalizeClinicCode(context?.doctorSlug) || null,
      doctorName: normalizeClinicCode(context?.doctorName) || null,
      tenantId: normalizeClinicCode(context?.tenantId) || null,
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
    clinicName: context.clinicName,
    tenantId: context.tenantId,
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
