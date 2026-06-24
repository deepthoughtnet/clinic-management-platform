import {
  type PatientPortalSession,
  isPatientPortalPatientSession,
  isPatientPortalRegistrationSession,
} from "./patientPortal";

export type PublicPageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type PublicDoctorSummaryResponse = {
  publicDoctorId: string;
  doctorSlug: string;
  doctorDisplayName: string;
  photoUrl: string | null;
  speciality: string | null;
  yearsOfExperience: number | null;
  languages: string[];
  clinicDisplayName: string;
  clinicSlug: string;
  area: string | null;
  city: string | null;
  availableToday: boolean;
  nextAvailableSlotSummary: string | null;
};

export type PublicClinicSummaryResponse = {
  clinicSlug: string;
  clinicDisplayName: string;
  logoUrl: string | null;
  address: string | null;
  area: string | null;
  city: string | null;
  doctorsCount: number;
  availableToday: boolean;
  specialities: string[];
};

export type PublicClinicMiniResponse = {
  clinicSlug: string;
  clinicDisplayName: string;
  area: string | null;
  city: string | null;
};

export type PublicDoctorClinicSummaryResponse = PublicClinicMiniResponse;

export type PublicDoctorDetailResponse = {
  publicDoctorId: string;
  doctorSlug: string;
  doctorDisplayName: string;
  photoUrl: string | null;
  qualification: string | null;
  yearsOfExperience: number | null;
  specialities: string[];
  languages: string[];
  clinics: PublicDoctorClinicSummaryResponse[];
  availableDays: string[];
  nextAvailableSlots: string[];
  availableToday: boolean;
};

export type PublicClinicDetailResponse = {
  clinicSlug: string;
  clinicDisplayName: string;
  logoUrl: string | null;
  address: string | null;
  area: string | null;
  city: string | null;
  timings: string[];
  doctors: PublicDoctorSummaryResponse[];
  specialities: string[];
  availableToday: boolean;
};

export type PublicSpecialitySummaryResponse = {
  speciality: string;
  specialitySlug: string;
  doctorsCount: number;
  clinicsCount: number;
};

export type PublicSpecialityDetailResponse = {
  speciality: string;
  specialitySlug: string;
  doctors: PublicPageResponse<PublicDoctorSummaryResponse>;
};

export type PublicSearchResponse = {
  doctors: PublicPageResponse<PublicDoctorSummaryResponse>;
  clinics: PublicPageResponse<PublicClinicSummaryResponse>;
  specialities: PublicSpecialitySummaryResponse[];
};

const apiBaseUrl = import.meta.env.VITE_PUBLIC_API_BASE_URL?.trim() ?? "";

function buildUrl(path: string, params?: Record<string, string | number | undefined | null>) {
  const url = new URL(`${apiBaseUrl}${path}`, window.location.origin);
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}`.trim()) {
      url.searchParams.set(key, `${value}`.trim());
    }
  });
  return url.toString();
}

async function parseError(response: Response) {
  const fallback = `Request failed with status ${response.status}`;
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? fallback;
  } catch {
    return fallback;
  }
}

export async function fetchPublicJson<T>(
  path: string,
  params?: Record<string, string | number | undefined | null>,
  signal?: AbortSignal,
): Promise<T> {
  const response = await fetch(buildUrl(path, params), {
    headers: {
      Accept: "application/json",
    },
    signal,
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}

export function patientBookingPath(session: PatientPortalSession | null, nextPath = "/patient/book-appointment") {
  if (isPatientPortalPatientSession(session)) {
    return nextPath;
  }
  if (isPatientPortalRegistrationSession(session)) {
    return `/patient/register?next=${encodeURIComponent(nextPath)}`;
  }
  return `/patient/login?next=${encodeURIComponent(nextPath)}`;
}
