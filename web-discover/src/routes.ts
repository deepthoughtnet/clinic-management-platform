import type { ReactNode } from "react";

export type DiscoverRoute = {
  path: string;
  label: string;
  nav?: boolean;
  footer?: boolean;
};

export const DISCOVER_ROUTES = {
  home: { path: "/", label: "Home", nav: true },
  doctors: { path: "/doctors", label: "Doctors", nav: true, footer: true },
  clinics: { path: "/clinics", label: "Clinics", nav: true, footer: true },
  hospitals: { path: "/hospitals", label: "Hospitals", nav: true, footer: true },
  specialities: { path: "/specialities", label: "Specialities", nav: true, footer: true },
  services: { path: "/services", label: "Services", footer: true },
  healthcare: { path: "/healthcare", label: "Jeevanam Healthcare", footer: true },
  pricing: { path: "/pricing", label: "Pricing", nav: true, footer: true },
  listPractice: { path: "/list-your-practice", label: "For Providers", nav: true, footer: true },
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
  DISCOVER_ROUTES.home,
  DISCOVER_ROUTES.doctors,
  DISCOVER_ROUTES.clinics,
  DISCOVER_ROUTES.hospitals,
  DISCOVER_ROUTES.specialities,
  DISCOVER_ROUTES.listPractice,
  DISCOVER_ROUTES.pricing,
  DISCOVER_ROUTES.login,
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

export type RoutePage = {
  route: DiscoverRoute;
  element: ReactNode;
};
