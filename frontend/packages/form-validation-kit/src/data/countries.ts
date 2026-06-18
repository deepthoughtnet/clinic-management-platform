export const countries = [
  "India",
  "United States",
  "United Kingdom",
  "Canada",
  "Australia",
  "Singapore",
  "United Arab Emirates",
] as const;

export type Country = (typeof countries)[number];
