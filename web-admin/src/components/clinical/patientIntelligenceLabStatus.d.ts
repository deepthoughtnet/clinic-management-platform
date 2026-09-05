export function resolveTrustedLabStatus(concept: {
  label?: string | null;
  valueText?: string | null;
  interpretation?: string | null;
} | null | undefined): { label: "HIGH" | "LOW" | "NORMAL" | "UNKNOWN"; tone: "default" | "success" | "warning" | "error" };
