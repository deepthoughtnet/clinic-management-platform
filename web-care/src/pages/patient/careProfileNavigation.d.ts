export declare const CARE_PROFILE_FALLBACK_PATH: "/patient/doctors";

export type CareProfileBackState = {
  careBackTo: string;
};

export declare function buildCareProfileBackState(currentPath: string): CareProfileBackState;
export declare function hasCareProfileBackState(value: unknown): value is CareProfileBackState;
export declare function resolveCareProfileBackTarget(value: unknown): string;
export declare function resolveCareProfileFallbackPath(): "/patient/doctors";
