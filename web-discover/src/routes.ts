import type { ReactNode } from "react";

export type DiscoverRoute = {
  path: string;
  label: string;
  nav?: boolean;
  footer?: boolean;
};

export const DISCOVER_ROUTES = {
  home: { path: "/", label: "Home", nav: true },
  doctors: { path: "/discover/doctors", label: "Doctors", nav: true, footer: true },
  clinics: { path: "/discover/clinics", label: "Clinics", nav: true, footer: true },
  hospitals: { path: "/discover/hospitals", label: "Hospitals", nav: true, footer: true },
  specialities: { path: "/discover/specialities", label: "Specialities", nav: true, footer: true },
  services: { path: "/discover/services", label: "Services", footer: true },
  healthcare: { path: "/healthcare", label: "Jeevanam Healthcare", footer: true },
  pricing: { path: "/pricing", label: "Pricing", nav: true, footer: true },
  listPractice: { path: "/list-your-practice", label: "For Providers", nav: true, footer: true },
  providerDashboard: { path: "/provider/dashboard", label: "Provider Dashboard" },
  providerOnboarding: { path: "/provider/onboarding/:applicationId/:step", label: "Provider Onboarding" },
  registerDoctor: { path: "/register/doctor", label: "Register as Doctor" },
  registerClinic: { path: "/register/clinic", label: "Register a Clinic" },
  registerHospital: { path: "/register/hospital", label: "Register a Hospital" },
  login: { path: "/login", label: "Login", nav: true },
  about: { path: "/about", label: "About", footer: true },
  contact: { path: "/contact", label: "Contact", footer: true },
  privacy: { path: "/privacy", label: "Privacy", footer: true },
  terms: { path: "/terms", label: "Terms", footer: true },
} satisfies Record<string, DiscoverRoute>;

export const primaryNavigationRoutes = [
  DISCOVER_ROUTES.doctors,
  DISCOVER_ROUTES.clinics,
  DISCOVER_ROUTES.hospitals,
  DISCOVER_ROUTES.specialities,
];

export const footerRoutes = [
  DISCOVER_ROUTES.doctors,
  DISCOVER_ROUTES.clinics,
  DISCOVER_ROUTES.hospitals,
  DISCOVER_ROUTES.specialities,
  DISCOVER_ROUTES.healthcare,
  DISCOVER_ROUTES.listPractice,
  DISCOVER_ROUTES.pricing,
  DISCOVER_ROUTES.about,
  DISCOVER_ROUTES.contact,
  DISCOVER_ROUTES.privacy,
  DISCOVER_ROUTES.terms,
];

export const DISCOVER_DETAIL_PATHS = {
  doctor: (slug: string) => `/discover/doctors/${slug}`,
  clinic: (slug: string) => `/discover/clinics/${slug}`,
  hospital: (slug: string) => `/discover/hospitals/${slug}`,
  speciality: (slug: string) => `/discover/specialities/${slug}`,
} as const;

export const LEGACY_DETAIL_PATHS = {
  doctor: (slug: string) => `/doctors/${slug}`,
  clinic: (slug: string) => `/clinics/${slug}`,
  hospital: (slug: string) => `/hospitals/${slug}`,
  speciality: (slug: string) => `/specialities/${slug}`,
} as const;

export type RoutePage = {
  route: DiscoverRoute;
  element: ReactNode;
};
