const env = import.meta.env;

export const branding = {
  productName: env.VITE_PRODUCT_NAME?.trim() || "Jeevanam Care",
  tagline: env.VITE_PRODUCT_TAGLINE?.trim() || "Your appointments, prescriptions, reports, and care journey in one place.",
  companyName: env.VITE_COMPANY_NAME?.trim() || "DeepThoughtNet",
  aiPlatformName: env.VITE_AI_PLATFORM_NAME?.trim() || "AIVA",
};

export function productTitle() {
  return `${branding.productName} | ${branding.tagline}`;
}

export function productAndTagline() {
  return `${branding.productName} — ${branding.tagline}`;
}

export function footerBrandingLine() {
  return `${branding.productName} · ${branding.tagline}`;
}
