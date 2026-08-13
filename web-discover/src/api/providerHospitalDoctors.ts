import { discoverConfig } from "../config";
import { fetchPublicJson, normalizePublicPageResponse, type PublicDoctorSummaryResponse, type PublicPageResponse } from "./publicCatalog";

export type ProviderHospitalDoctorAssociationResponse = {
  publicDoctorReference: string;
  doctorDisplayName: string;
  speciality: string | null;
  qualification: string | null;
  registrationNumber: string | null;
  yearsOfExperience: number | null;
  publicPath: string | null;
  associationStatus: string;
  hospitalDisplayName: string | null;
  hospitalSlug: string | null;
  languages: string[];
};

export type ProviderHospitalDoctorUpsertRequest = {
  publicDoctorReference: string;
};

function buildProviderUrl(path: string) {
  return new URL(`${discoverConfig.apiBaseUrl}${path}`, window.location.origin).toString();
}

async function parseError(response: Response) {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? `Request failed with status ${response.status}`;
  } catch {
    return `Request failed with status ${response.status}`;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(buildProviderUrl(path), {
    ...options,
    cache: "no-store",
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function loadProviderHospitalDoctors(profileReference: string) {
  return request<ProviderHospitalDoctorAssociationResponse[]>(`/api/provider/public-profiles/${encodeURIComponent(profileReference)}/hospital-doctors`);
}

export function addProviderHospitalDoctor(profileReference: string, publicDoctorReference: string) {
  return request<ProviderHospitalDoctorAssociationResponse[]>(`/api/provider/public-profiles/${encodeURIComponent(profileReference)}/hospital-doctors`, {
    method: "POST",
    body: JSON.stringify({ publicDoctorReference }),
  });
}

export function removeProviderHospitalDoctor(profileReference: string, publicDoctorReference: string) {
  return request<ProviderHospitalDoctorAssociationResponse[]>(`/api/provider/public-profiles/${encodeURIComponent(profileReference)}/hospital-doctors/${encodeURIComponent(publicDoctorReference)}/remove`, {
    method: "POST",
  });
}

export async function searchPublicDoctors(query: string, city?: string | null) {
  const page = await fetchPublicJson<PublicPageResponse<PublicDoctorSummaryResponse>>("/api/public/doctors", {
    q: query,
    city: city ?? undefined,
    page: 0,
    size: 12,
  });
  return normalizePublicPageResponse(page);
}
