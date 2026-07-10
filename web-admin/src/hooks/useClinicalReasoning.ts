import * as React from "react";
import {
  generateClinicalReasoning,
  type ClinicalReasoningMetadata,
  type ClinicalReasoningResult,
  type ClinicalReasoningResponse,
} from "../api/clinicApi";

type ClinicalReasoningRequest = Parameters<typeof generateClinicalReasoning>[3];
type ClinicalReasoningQuality = "COMPLETE" | "PARTIAL_FALLBACK" | "FAILED";

type UseClinicalReasoningOptions = {
  accessToken: string | null;
  tenantId: string | null;
  buildRequest: (consultationId: string) => ClinicalReasoningRequest | null;
};

type ClinicalReasoningState = {
  clinicalReasoningResult: ClinicalReasoningResult | null;
  loading: boolean;
  error: string | null;
  warning: string | null;
  lastGeneratedAt: string | null;
  metadata: ClinicalReasoningMetadata | null;
  resultQuality: ClinicalReasoningQuality | null;
  generateReasoning: (consultationId: string) => Promise<ClinicalReasoningResult | null>;
  refreshReasoning: (consultationId: string) => Promise<ClinicalReasoningResult | null>;
  resetReasoning: () => void;
};

function resolveReasoningMetadata(
  response: ClinicalReasoningResponse,
  result: ClinicalReasoningResult | null,
): ClinicalReasoningMetadata | null {
  return result?.metadata || response.metadata || null;
}

function hasReasoningContent(result: ClinicalReasoningResult | null): boolean {
  if (!result) return false;
  return Boolean(
    result.primaryDiagnosis?.name
    || result.differentialDiagnoses.length
    || result.supportingEvidence.length
    || result.missingInformation.length
    || result.redFlags.length
    || result.recommendedTests.length
    || result.safetyNotes.length
    || result.reasoningSummary,
  );
}

function deriveResultQuality(
  result: ClinicalReasoningResult | null,
  metadata: ClinicalReasoningMetadata | null,
): ClinicalReasoningQuality {
  const explicitQuality = metadata?.resultQuality;
  if (explicitQuality === "COMPLETE" || explicitQuality === "PARTIAL_FALLBACK" || explicitQuality === "FAILED") {
    return explicitQuality;
  }
  const parseStatus = metadata?.parseStatus?.toUpperCase();
  if (parseStatus === "VALID" && result?.primaryDiagnosis?.name && result.confidence && result.confidence !== "UNKNOWN") {
    return "COMPLETE";
  }
  if (hasReasoningContent(result)) {
    return "PARTIAL_FALLBACK";
  }
  return "FAILED";
}

function partialWarningMessage(_metadata: ClinicalReasoningMetadata | null): string {
  return "AI reasoning was only partially generated. Review all suggestions carefully and retry for a complete assessment.";
}

function hardErrorMessage(metadata: ClinicalReasoningMetadata | null): string {
  if (metadata?.errorMessage?.trim()) {
    return metadata.errorMessage.trim();
  }
  return "Unable to generate AI reasoning. You may continue consultation normally.";
}

export function useClinicalReasoning(options: UseClinicalReasoningOptions): ClinicalReasoningState {
  const { accessToken, tenantId, buildRequest } = options;
  const [clinicalReasoningResult, setClinicalReasoningResult] = React.useState<ClinicalReasoningResult | null>(null);
  const [lastSuccessfulResult, setLastSuccessfulResult] = React.useState<ClinicalReasoningResult | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [warning, setWarning] = React.useState<string | null>(null);
  const [lastGeneratedAt, setLastGeneratedAt] = React.useState<string | null>(null);
  const [metadata, setMetadata] = React.useState<ClinicalReasoningMetadata | null>(null);
  const [resultQuality, setResultQuality] = React.useState<ClinicalReasoningQuality | null>(null);
  const requestIdRef = React.useRef(0);

  const resetReasoning = React.useCallback(() => {
    requestIdRef.current += 1;
    setClinicalReasoningResult(null);
    setLastSuccessfulResult(null);
    setLoading(false);
    setError(null);
    setWarning(null);
    setLastGeneratedAt(null);
    setMetadata(null);
    setResultQuality(null);
  }, []);

  const runReasoning = React.useCallback(async (consultationId: string, mode: "generate" | "refresh") => {
    if (!accessToken || !tenantId) {
      setError("Clinical reasoning is unavailable for this session.");
      setWarning(null);
      return null;
    }

    const requestBody = buildRequest(consultationId);
    if (!requestBody) {
      setError("Clinical context is incomplete. Save the consultation and try again.");
      setWarning(null);
      return null;
    }

    const requestId = ++requestIdRef.current;
    const previousSuccessfulResult = lastSuccessfulResult;
    const previousSuccessfulMetadata = previousSuccessfulResult?.metadata || null;
    setLoading(true);
    setError(null);
    setWarning(null);

    try {
      const response = await generateClinicalReasoning(accessToken, tenantId, consultationId, requestBody);
      if (requestId !== requestIdRef.current) {
        return response.reasoningResult || null;
      }

      const result = response.reasoningResult || null;
      const resultMetadata = resolveReasoningMetadata(response, result);
      const quality = deriveResultQuality(result, resultMetadata);

      if (quality === "COMPLETE" && result) {
        setClinicalReasoningResult(result);
        setLastSuccessfulResult(result);
        setMetadata(resultMetadata);
        setLastGeneratedAt(result.generatedAt || null);
        setResultQuality("COMPLETE");
        setError(null);
        setWarning(null);
        return result;
      }

      if (quality === "PARTIAL_FALLBACK" && result) {
        if (mode === "refresh" && previousSuccessfulResult) {
          setClinicalReasoningResult(previousSuccessfulResult);
          setMetadata(previousSuccessfulMetadata);
          setLastGeneratedAt(previousSuccessfulResult.generatedAt || null);
          setResultQuality("COMPLETE");
          setError(null);
          setWarning("Refresh failed. Showing the last successfully generated reasoning.");
          return previousSuccessfulResult;
        }
        setClinicalReasoningResult(result);
        setMetadata(resultMetadata);
        setLastGeneratedAt(result.generatedAt || null);
        setResultQuality("PARTIAL_FALLBACK");
        setError(null);
        setWarning(partialWarningMessage(resultMetadata));
        return result;
      }

      if (mode === "refresh" && previousSuccessfulResult) {
        setClinicalReasoningResult(previousSuccessfulResult);
        setMetadata(previousSuccessfulMetadata);
        setLastGeneratedAt(previousSuccessfulResult.generatedAt || null);
        setResultQuality("COMPLETE");
        setError(null);
        setWarning("Refresh failed. Showing the last successfully generated reasoning.");
        return previousSuccessfulResult;
      }

      setClinicalReasoningResult(null);
      setMetadata(null);
      setLastGeneratedAt(null);
      setResultQuality("FAILED");
      setWarning(null);
      setError(hardErrorMessage(resultMetadata));
      return null;
    } catch (err) {
      if (requestId !== requestIdRef.current) {
        return null;
      }
      if (mode === "refresh" && previousSuccessfulResult) {
        setClinicalReasoningResult(previousSuccessfulResult);
        setMetadata(previousSuccessfulMetadata);
        setLastGeneratedAt(previousSuccessfulResult.generatedAt || null);
        setResultQuality("COMPLETE");
        setError(null);
        setWarning("Refresh failed. Showing the last successfully generated reasoning.");
        return previousSuccessfulResult;
      }
      setClinicalReasoningResult(null);
      setMetadata(null);
      setLastGeneratedAt(null);
      setResultQuality("FAILED");
      setWarning(null);
      setError(err instanceof Error ? err.message : "Unable to generate AI reasoning. You may continue consultation normally.");
      return null;
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [accessToken, buildRequest, lastSuccessfulResult, tenantId]);

  const generateReasoning = React.useCallback((consultationId: string) => runReasoning(consultationId, "generate"), [runReasoning]);
  const refreshReasoning = React.useCallback((consultationId: string) => runReasoning(consultationId, "refresh"), [runReasoning]);

  return {
    clinicalReasoningResult,
    loading,
    error,
    warning,
    lastGeneratedAt,
    metadata,
    resultQuality,
    generateReasoning,
    refreshReasoning,
    resetReasoning,
  };
}
