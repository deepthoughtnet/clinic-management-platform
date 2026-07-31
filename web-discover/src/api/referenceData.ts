import { discoverConfig } from "../config";
import type { ProviderType } from "./providerOnboarding";

export type DiscoverReferenceOption = {
  id: string;
  code: string;
  displayName: string;
  providerTypes: ProviderType[];
  displayOrder: number;
  active: boolean;
};

export type DiscoverReferenceCatalog = {
  specialities: DiscoverReferenceOption[];
  services: DiscoverReferenceOption[];
  facilities: DiscoverReferenceOption[];
  ownerships: DiscoverReferenceOption[];
  organisationTypes: DiscoverReferenceOption[];
  languages: DiscoverReferenceOption[];
  countries: DiscoverReferenceOption[];
  states: DiscoverReferenceOption[];
  medicalCouncils: DiscoverReferenceOption[];
};

function buildUrl(path: string) {
  return new URL(`${discoverConfig.apiBaseUrl}${path}`, window.location.origin).toString();
}

async function request<T>(path: string): Promise<T> {
  const response = await fetch(buildUrl(path), {
    method: "GET",
    cache: "no-store",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    try {
      const body = (await response.json()) as { message?: string };
      throw new Error(body.message ?? `Request failed with status ${response.status}`);
    } catch {
      throw new Error(`Request failed with status ${response.status}`);
    }
  }
  return response.json() as Promise<T>;
}

export function loadDiscoverReferenceCatalog() {
  return Promise.all([
    request<DiscoverReferenceOption[]>("/api/discover/reference/specialities"),
    request<DiscoverReferenceOption[]>("/api/discover/reference/services"),
    request<DiscoverReferenceOption[]>("/api/discover/reference/facilities"),
    request<DiscoverReferenceOption[]>("/api/discover/reference/ownerships"),
    request<DiscoverReferenceOption[]>("/api/discover/reference/organisation-types"),
    request<DiscoverReferenceOption[]>("/api/discover/reference/languages"),
    request<DiscoverReferenceOption[]>("/api/discover/reference/countries"),
    request<DiscoverReferenceOption[]>("/api/discover/reference/states"),
    request<DiscoverReferenceOption[]>("/api/discover/reference/medical-councils"),
  ]).then(([specialities, services, facilities, ownerships, organisationTypes, languages, countries, states, medicalCouncils]) => ({
    specialities,
    services,
    facilities,
    ownerships,
    organisationTypes,
    languages,
    countries,
    states,
    medicalCouncils,
  }) satisfies DiscoverReferenceCatalog);
}
