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
  publicPath?: string;
  doctorDisplayName: string;
  photoUrl: string | null;
  contactPhone?: string | null;
  speciality: string | null;
  yearsOfExperience: number | null;
  consultationFee?: number | string | null;
  languages: string[];
  clinicDisplayName: string;
  clinicSlug: string;
  area: string | null;
  city: string | null;
  bookingMode?: string | null;
  subtitle?: string | null;
  summary?: string | null;
  availableToday: boolean;
  nextAvailableSlotSummary: string | null;
  distanceKm?: number | string | null;
};

export type PublicClinicSummaryResponse = {
  clinicSlug: string;
  publicPath?: string;
  clinicDisplayName: string;
  logoUrl: string | null;
  coverUrl?: string | null;
  contactPhone?: string | null;
  address: string | null;
  area: string | null;
  city: string | null;
  bookingMode?: string | null;
  doctorsCount: number;
  serviceCount?: number;
  departmentCount?: number;
  galleryCount?: number;
  emergencyAvailable?: boolean;
  subtitle?: string | null;
  summary?: string | null;
  availableToday: boolean;
  distanceKm?: number | string | null;
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
  canonicalSlug?: string;
  publicPath?: string;
  doctorDisplayName: string;
  photoUrl: string | null;
  bookingMode?: string | null;
  qualification: string | null;
  medicalCouncil?: string | null;
  yearsOfExperience: number | null;
  summary?: string | null;
  biography?: string | null;
  specialities: string[];
  subSpecialities?: string[];
  languages: string[];
  consultationModes?: string[];
  services?: string[];
  locations?: PublicProviderLocationResponse[];
  galleryImageUrls?: string[];
  coverUrl?: string | null;
  logoUrl?: string | null;
  contactPhone?: string | null;
  contactEmail?: string | null;
  website?: string | null;
  area?: string | null;
  city?: string | null;
  state?: string | null;
  country?: string | null;
  primarySpeciality?: string | null;
  reviewsComingSoon?: boolean;
  subtitle?: string | null;
  bookingSummary?: string | null;
  clinics: PublicDoctorClinicSummaryResponse[];
  availableDays: string[];
  nextAvailableSlots: string[];
  availableToday: boolean;
};

export type PublicClinicDetailResponse = {
  clinicSlug: string;
  canonicalSlug?: string;
  publicPath?: string;
  clinicDisplayName: string;
  logoUrl: string | null;
  coverUrl?: string | null;
  bookingMode?: string | null;
  address: string | null;
  area: string | null;
  city: string | null;
  summary?: string | null;
  description?: string | null;
  services?: string[];
  departments?: string[];
  facilities?: string[];
  consultationModes?: string[];
  locations?: PublicProviderLocationResponse[];
  galleryImageUrls?: string[];
  contactPhone?: string | null;
  contactEmail?: string | null;
  website?: string | null;
  timings: string[];
  doctors: PublicDoctorSummaryResponse[];
  specialities: string[];
  availableToday: boolean;
  reviewsComingSoon?: boolean;
  subtitle?: string | null;
};

export type PublicProviderLocationResponse = {
  label: string | null;
  address: string | null;
  city: string | null;
  state: string | null;
  country: string | null;
  pinCode: string | null;
  workingHours: string | null;
  parkingAvailable: boolean;
  accessibilityAvailable: boolean;
  latitude: number | null;
  longitude: number | null;
};

export type PublicHospitalSummaryResponse = {
  hospitalSlug: string;
  publicPath?: string;
  hospitalDisplayName: string;
  logoUrl: string | null;
  coverUrl?: string | null;
  contactPhone?: string | null;
  area: string | null;
  city: string | null;
  bookingMode?: string | null;
  doctorsCount: number;
  serviceCount?: number;
  departmentCount?: number;
  galleryCount?: number;
  emergencyAvailable: boolean;
  departments: string[];
  subtitle?: string | null;
  summary?: string | null;
  distanceKm?: number | string | null;
};

export type PublicHospitalDetailResponse = {
  hospitalSlug: string;
  canonicalSlug?: string;
  publicPath?: string;
  hospitalDisplayName: string;
  logoUrl: string | null;
  coverUrl?: string | null;
  bookingMode?: string | null;
  address: string | null;
  area: string | null;
  city: string | null;
  summary?: string | null;
  description?: string | null;
  departments: string[];
  facilities: string[];
  services: string[];
  consultationModes: string[];
  locations: PublicProviderLocationResponse[];
  galleryImageUrls: string[];
  doctors: PublicDoctorSummaryResponse[];
  contactPhone?: string | null;
  contactEmail?: string | null;
  website?: string | null;
  emergencyAvailable: boolean;
  reviewsComingSoon: boolean;
  subtitle?: string | null;
};

export type PublicSpecialitySummaryResponse = {
  speciality: string;
  specialitySlug: string;
  doctorsCount: number;
  clinicsCount: number;
  hospitalsCount?: number;
};

export type PublicSpecialityDetailResponse = {
  speciality: string;
  specialitySlug: string;
  doctors: PublicPageResponse<PublicDoctorSummaryResponse>;
};

export type PublicSearchResponse = {
  doctors: PublicPageResponse<PublicDoctorSummaryResponse>;
  clinics: PublicPageResponse<PublicClinicSummaryResponse>;
  hospitals?: PublicPageResponse<PublicHospitalSummaryResponse>;
  specialities: PublicSpecialitySummaryResponse[];
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() ?? "";

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
    headers: { Accept: "application/json" },
    signal,
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json() as Promise<T>;
}
