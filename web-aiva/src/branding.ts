const env = import.meta.env;

export const branding = {
  productName: env.VITE_PRODUCT_NAME?.trim() || "Arogia",
  tagline: env.VITE_PRODUCT_TAGLINE?.trim() || "Smart Healthcare Management Platform",
  companyName: env.VITE_COMPANY_NAME?.trim() || "DeepThoughtNet",
  aiPlatformName: env.VITE_AI_PLATFORM_NAME?.trim() || "AIVA",
};

export function productTitle() {
  return `${branding.productName} | ${branding.tagline}`;
}
