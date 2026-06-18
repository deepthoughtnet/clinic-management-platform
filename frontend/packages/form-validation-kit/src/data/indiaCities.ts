export const indiaCities = [
  "Pune",
  "Mumbai",
  "Navi Mumbai",
  "Thane",
  "Bengaluru",
  "Delhi",
  "New Delhi",
  "Ahmedabad",
  "Surat",
  "Jaipur",
  "Lucknow",
  "Indore",
  "Chennai",
  "Hyderabad",
  "Kolkata",
  "Kochi",
  "Gurugram",
  "Noida",
  "Chandigarh",
] as const;

export type IndiaCity = (typeof indiaCities)[number];
