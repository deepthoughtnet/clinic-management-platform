export const indiaStates = [
  "Maharashtra",
  "Karnataka",
  "Delhi",
  "Gujarat",
  "Rajasthan",
  "Uttar Pradesh",
  "Madhya Pradesh",
  "Tamil Nadu",
  "Telangana",
  "West Bengal",
  "Kerala",
  "Haryana",
  "Punjab",
] as const;

export type IndiaState = (typeof indiaStates)[number];
