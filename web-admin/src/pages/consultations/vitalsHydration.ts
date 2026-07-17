import type { ClinicalIntakeResponse } from "../../api/clinicApi";

export type ConsultationVitalsFieldState = {
  bloodPressureSystolic: string;
  bloodPressureDiastolic: string;
  pulseRate: string;
  temperature: string;
  temperatureUnit: "CELSIUS" | "FAHRENHEIT" | "";
  weightKg: string;
  heightCm: string;
  spo2: string;
  respiratoryRate: string;
};

export type IntakeVitalsSnapshot = {
  bloodPressureSystolic: number | null;
  bloodPressureDiastolic: number | null;
  pulseRate: number | null;
  temperature: number | null;
  temperatureUnit: string | null;
  spo2: number | null;
  respiratoryRate: number | null;
  weightKg: number | null;
  heightCm: number | null;
  bmi: number | null;
  randomBloodSugar: number | null;
  painScore: number | null;
};

type ConsultationVitalsEditableState = ConsultationVitalsFieldState & Record<string, unknown>;

function assignIfBlank<T extends ConsultationVitalsEditableState>(
  target: T,
  key: keyof ConsultationVitalsFieldState,
  value: number | string | null | undefined,
) {
  if (value == null || value === "") {
    return;
  }
  const current = String(target[key] ?? "").trim();
  if (current) {
    return;
  }
  (target as Record<string, unknown>)[key] = key === "temperatureUnit"
    ? (String(value).trim().toUpperCase() as ConsultationVitalsFieldState["temperatureUnit"])
    : String(value);
}

export function mergeConsultationVitalsFromIntake<T extends ConsultationVitalsEditableState>(
  form: T,
  intakeVitals: IntakeVitalsSnapshot | null | undefined,
): T {
  if (!intakeVitals) {
    return form;
  }
  const next = { ...form } as T;
  assignIfBlank(next, "bloodPressureSystolic", intakeVitals.bloodPressureSystolic);
  assignIfBlank(next, "bloodPressureDiastolic", intakeVitals.bloodPressureDiastolic);
  assignIfBlank(next, "pulseRate", intakeVitals.pulseRate);
  assignIfBlank(next, "temperature", intakeVitals.temperature);
  assignIfBlank(next, "temperatureUnit", intakeVitals.temperatureUnit);
  assignIfBlank(next, "weightKg", intakeVitals.weightKg);
  assignIfBlank(next, "heightCm", intakeVitals.heightCm);
  assignIfBlank(next, "spo2", intakeVitals.spo2);
  assignIfBlank(next, "respiratoryRate", intakeVitals.respiratoryRate);
  return next;
}

export function clinicalIntakeToVitalsSnapshot(intake: ClinicalIntakeResponse | null | undefined): IntakeVitalsSnapshot | null {
  if (!intake) {
    return null;
  }
  return {
    bloodPressureSystolic: intake.bloodPressureSystolic ?? null,
    bloodPressureDiastolic: intake.bloodPressureDiastolic ?? null,
    pulseRate: intake.pulseRate ?? null,
    temperature: intake.temperature ?? null,
    temperatureUnit: intake.temperatureUnit ?? null,
    spo2: intake.spo2 ?? null,
    respiratoryRate: intake.respiratoryRate ?? null,
    weightKg: intake.weightKg ?? null,
    heightCm: intake.heightCm ?? null,
    bmi: intake.bmi ?? null,
    randomBloodSugar: intake.randomBloodSugar ?? null,
    painScore: intake.painScore ?? null,
  };
}
