export function normalizeLookupKey(value) {
  return String(value || "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

export function normalizeInvestigationCanonicalKey(value) {
  const normalized = normalizeLookupKey(value);
  if (!normalized) return "";
  if (/(hba1c|hba1 c|a1c|hemoglobin a1c)/.test(normalized)) return "hba1c";
  if (/(blood sugar|blood glucose|glucose|random blood sugar|fasting blood sugar|rbs|fbs|ppbs)/.test(normalized)) return "blood sugar";
  if (/(cbc|complete blood count)/.test(normalized)) return "cbc";
  if (/(crp|c reactive protein|c reactive)/.test(normalized)) return "crp";
  if (/(lipid profile|cholesterol|triglyceride|triglycerides|hdl|ldl|lipid)/.test(normalized)) return "lipid profile";
  if (/(chest x ray|xray chest|x ray chest|cxr|chest xray)/.test(normalized)) return "chest x ray";
  if (/(covid|sars cov 2|sars cov2|sarscov2)/.test(normalized)) return "covid 19";
  if (/(influenza|flu)/.test(normalized)) return "influenza";
  if (/(creatinine|rft|renal function|kidney function)/.test(normalized)) return "creatinine";
  return normalized;
}

export function investigationAliasesForCanonicalKey(key) {
  switch (key) {
    case "hba1c":
      return ["hba1c", "hb a1c", "hemoglobin a1c", "a1c"];
    case "blood sugar":
      return ["blood sugar", "blood glucose", "glucose", "random blood sugar", "fasting blood sugar", "rbs", "fbs", "ppbs"];
    case "cbc":
      return ["cbc", "complete blood count"];
    case "crp":
      return ["crp", "c reactive protein", "c-reactive protein", "c reactive"];
    case "lipid profile":
      return ["lipid profile", "cholesterol", "triglyceride", "triglycerides", "hdl", "ldl", "lipid"];
    case "chest x ray":
      return ["chest x ray", "xray chest", "x ray chest", "cxr", "chest xray"];
    case "covid 19":
      return ["covid", "covid 19", "covid-19", "sars cov 2", "sars cov2", "sarscov2"];
    case "influenza":
      return ["influenza", "flu"];
    case "creatinine":
      return ["creatinine", "rft", "renal function", "kidney function"];
    default:
      return [key];
  }
}

function uniqueById(candidates) {
  const byId = new Map();
  candidates.forEach((candidate) => {
    if (!candidate || !candidate.id) {
      return;
    }
    const existing = byId.get(candidate.id);
    if (!existing || (existing.matchType === "ALIAS" && candidate.matchType === "EXACT")) {
      byId.set(candidate.id, candidate);
    }
  });
  return [...byId.values()];
}

export function resolveLabRecommendationMatch(recommendationText, labTests) {
  const sourceRecommendation = String(recommendationText || "").trim();
  const normalizedRecommendation = normalizeLookupKey(sourceRecommendation);
  const canonicalKey = normalizeInvestigationCanonicalKey(sourceRecommendation);
  const canonicalAliases = investigationAliasesForCanonicalKey(canonicalKey).map((alias) => normalizeLookupKey(alias)).filter(Boolean);
  const aliasSet = new Set([normalizedRecommendation, normalizeLookupKey(canonicalKey), ...canonicalAliases].filter(Boolean));

  if (!normalizedRecommendation) {
    return {
      sourceRecommendation,
      normalizedRecommendation,
      canonicalKey,
      matchState: "UNMAPPED",
      matchType: null,
      matchedTests: [],
      matchedTestIds: [],
      matchedCatalogNames: [],
      displayLabel: sourceRecommendation,
      mappingLabel: "No safe catalog match. Select a test manually.",
      safeToAutoSelect: false,
    };
  }

  const candidates = uniqueById((labTests || []).flatMap((test) => {
    if (!test || !test.id) {
      return [];
    }
    const testName = normalizeLookupKey(test.testName);
    const testCode = normalizeLookupKey(test.testCode);
    const exact = testName === normalizedRecommendation || testCode === normalizedRecommendation;
    const alias = !exact && (aliasSet.has(testName) || aliasSet.has(testCode));
    if (!exact && !alias) {
      return [];
    }
    return [{
      id: test.id,
      testName: test.testName,
      testCode: test.testCode ?? null,
      matchType: exact ? "EXACT" : "ALIAS",
    }];
  }));

  if (candidates.length === 1) {
    const match = candidates[0];
    return {
      sourceRecommendation,
      normalizedRecommendation,
      canonicalKey,
      matchState: "MAPPED",
      matchType: match.matchType,
      matchedTests: candidates,
      matchedTestIds: [match.id],
      matchedCatalogNames: [match.testName],
      displayLabel: match.testName,
      mappingLabel: match.matchType === "EXACT"
        ? `Mapped exactly to ${match.testName}`
        : `Mapped via alias to ${match.testName}`,
      safeToAutoSelect: true,
    };
  }

  if (candidates.length > 1) {
    return {
      sourceRecommendation,
      normalizedRecommendation,
      canonicalKey,
      matchState: "AMBIGUOUS",
      matchType: null,
      matchedTests: candidates,
      matchedTestIds: [],
      matchedCatalogNames: candidates.map((candidate) => candidate.testName),
      displayLabel: sourceRecommendation,
      mappingLabel: `Ambiguous catalog matches: ${candidates.map((candidate) => candidate.testName).join(", ")}. Select manually.`,
      safeToAutoSelect: false,
    };
  }

  return {
    sourceRecommendation,
    normalizedRecommendation,
    canonicalKey,
    matchState: "UNMAPPED",
    matchType: null,
    matchedTests: [],
    matchedTestIds: [],
    matchedCatalogNames: [],
    displayLabel: sourceRecommendation,
    mappingLabel: "No safe catalog match. Select a test manually.",
    safeToAutoSelect: false,
  };
}
