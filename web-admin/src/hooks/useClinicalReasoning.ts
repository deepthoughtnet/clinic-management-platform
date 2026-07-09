import * as React from "react";
import {
  generateClinicalReasoning,
  type ClinicalReasoningMetadata,
  type ClinicalReasoningResult,
  type ClinicalReasoningResponse,
} from "../api/clinicApi";

type ClinicalReasoningRequest = Parameters<typeof generateClinicalReasoning>[3];

type UseClinicalReasoningOptions = {
  accessToken: string | null;
  tenantId: string | null;
  buildRequest: (consultationId: string) => ClinicalReasoningRequest | null;
};

type ClinicalReasoningState = {
  clinicalReasoningResult: ClinicalReasoningResult | null;
  loading: boolean;
  error: string | null;
  lastGeneratedAt: string | null;
  metadata: ClinicalReasoningMetadata | null;
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

export function useClinicalReasoning(options: UseClinicalReasoningOptions): ClinicalReasoningState {
  const { accessToken, tenantId, buildRequest } = options;
  const [clinicalReasoningResult, setClinicalReasoningResult] = React.useState<ClinicalReasoningResult | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [lastGeneratedAt, setLastGeneratedAt] = React.useState<string | null>(null);
  const [metadata, setMetadata] = React.useState<ClinicalReasoningMetadata | null>(null);
  const requestIdRef = React.useRef(0);

  const resetReasoning = React.useCallback(() => {
    requestIdRef.current += 1;
    setClinicalReasoningResult(null);
    setLoading(false);
    setError(null);
    setLastGeneratedAt(null);
    setMetadata(null);
  }, []);

  const runReasoning = React.useCallback(async (consultationId: string) => {
    if (!accessToken || !tenantId) {
      setError("Clinical reasoning is unavailable for this session.");
      return null;
    }

    const requestBody = buildRequest(consultationId);
    if (!requestBody) {
      setError("Clinical context is incomplete. Save the consultation and try again.");
      return null;
    }

    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);

    try {
      const response = await generateClinicalReasoning(accessToken, tenantId, consultationId, requestBody);
      if (requestId !== requestIdRef.current) {
        return response.reasoningResult || null;
      }

      const result = response.reasoningResult || null;
      const resultMetadata = resolveReasoningMetadata(response, result);

      if (result) {
        setClinicalReasoningResult(result);
        setMetadata(resultMetadata);
        setLastGeneratedAt(result.generatedAt || null);
      } else {
        setError(resultMetadata?.errorMessage || "Unable to generate AI reasoning. You may continue consultation normally.");
        return null;
      }

      if (resultMetadata?.parseStatus && resultMetadata.parseStatus !== "VALID") {
        setError(resultMetadata.errorMessage || "Unable to generate AI reasoning. You may continue consultation normally.");
      }

      return result;
    } catch (err) {
      if (requestId !== requestIdRef.current) {
        return null;
      }
      setError(err instanceof Error ? err.message : "Unable to generate AI reasoning. You may continue consultation normally.");
      return null;
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [accessToken, buildRequest, tenantId]);

  const generateReasoning = React.useCallback((consultationId: string) => runReasoning(consultationId), [runReasoning]);
  const refreshReasoning = React.useCallback((consultationId: string) => runReasoning(consultationId), [runReasoning]);

  return {
    clinicalReasoningResult,
    loading,
    error,
    lastGeneratedAt,
    metadata,
    generateReasoning,
    refreshReasoning,
    resetReasoning,
  };
}
