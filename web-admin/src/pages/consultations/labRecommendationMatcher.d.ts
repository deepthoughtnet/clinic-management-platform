export type LabRecommendationMatchState = "MAPPED" | "AMBIGUOUS" | "UNMAPPED";
export type LabRecommendationMatchType = "EXACT" | "ALIAS" | null;

export interface LabRecommendationMatchTest {
  id: string;
  testName: string;
  testCode: string | null;
  matchType: Exclude<LabRecommendationMatchType, null>;
}

export interface LabRecommendationMatchResolution {
  sourceRecommendation: string;
  normalizedRecommendation: string;
  canonicalKey: string;
  matchState: LabRecommendationMatchState;
  matchType: LabRecommendationMatchType;
  matchedTests: LabRecommendationMatchTest[];
  matchedTestIds: string[];
  matchedCatalogNames: string[];
  displayLabel: string;
  mappingLabel: string;
  safeToAutoSelect: boolean;
}

export function normalizeLookupKey(value: unknown): string;
export function normalizeInvestigationCanonicalKey(value: unknown): string;
export function investigationAliasesForCanonicalKey(key: unknown): string[];
export function resolveLabRecommendationMatch(recommendationText: unknown, labTests: unknown[]): LabRecommendationMatchResolution;
