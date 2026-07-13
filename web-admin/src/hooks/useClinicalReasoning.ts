import * as React from "react";
import {
  generateClinicalReasoning,
  getClinicalReasoning,
  type ClinicalReasoningMetadata,
  type ClinicalReasoningResult,
  type ClinicalReasoningResponse,
} from "../api/clinicApi";

type ClinicalReasoningRequest = Parameters<typeof generateClinicalReasoning>[3];
type ClinicalReasoningQuality = "COMPLETE" | "PARTIAL_FALLBACK" | "FAILED";
type ClinicalReasoningStatus = "NOT_GENERATED" | "CURRENT" | "STALE" | "FAILED" | "SUPERSEDED" | string | null;

type UseClinicalReasoningOptions = {
  accessToken: string | null;
  tenantId: string | null;
  buildRequest: (consultationId: string) => ClinicalReasoningRequest | null;
};

type ClinicalReasoningState = {
  clinicalReasoningResult: ClinicalReasoningResult | null;
  reasoningId: string | null;
  reasoningVersion: number | null;
  reasoningStatus: ClinicalReasoningStatus;
  contextHash: string | null;
  staleReason: string | null;
  generatedByAppUserId: string | null;
  generatedByDisplayName: string | null;
  loading: boolean;
  hasLoaded: boolean;
  error: string | null;
  warning: string | null;
  lastGeneratedAt: string | null;
  metadata: ClinicalReasoningMetadata | null;
  resultQuality: ClinicalReasoningQuality | null;
  generateReasoning: (consultationId: string) => Promise<ClinicalReasoningResult | null>;
  refreshReasoning: (consultationId: string) => Promise<ClinicalReasoningResult | null>;
  loadReasoning: (consultationId: string) => Promise<ClinicalReasoningResult | null>;
  resetReasoning: () => void;
};

function resolveReasoningMetadata(
  response: ClinicalReasoningResponse | null,
  result: ClinicalReasoningResult | null,
): ClinicalReasoningMetadata | null {
  return result?.metadata || response?.metadata || null;
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

function emptyState(): Pick<
  ClinicalReasoningState,
  | "clinicalReasoningResult"
  | "reasoningId"
  | "reasoningVersion"
  | "reasoningStatus"
  | "contextHash"
  | "staleReason"
  | "generatedByAppUserId"
  | "generatedByDisplayName"
  | "error"
  | "warning"
  | "lastGeneratedAt"
  | "metadata"
  | "resultQuality"
> {
  return {
    clinicalReasoningResult: null,
    reasoningId: null,
    reasoningVersion: null,
    reasoningStatus: "NOT_GENERATED",
    contextHash: null,
    staleReason: null,
    generatedByAppUserId: null,
    generatedByDisplayName: null,
    error: null,
    warning: null,
    lastGeneratedAt: null,
    metadata: null,
    resultQuality: null,
  };
}

export function useClinicalReasoning(options: UseClinicalReasoningOptions): ClinicalReasoningState {
  const { accessToken, tenantId, buildRequest } = options;
  const [clinicalReasoningResult, setClinicalReasoningResult] = React.useState<ClinicalReasoningResult | null>(null);
  const [reasoningId, setReasoningId] = React.useState<string | null>(null);
  const [reasoningVersion, setReasoningVersion] = React.useState<number | null>(null);
  const [reasoningStatus, setReasoningStatus] = React.useState<ClinicalReasoningStatus>("NOT_GENERATED");
  const [contextHash, setContextHash] = React.useState<string | null>(null);
  const [staleReason, setStaleReason] = React.useState<string | null>(null);
  const [generatedByAppUserId, setGeneratedByAppUserId] = React.useState<string | null>(null);
  const [generatedByDisplayName, setGeneratedByDisplayName] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [hasLoaded, setHasLoaded] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [warning, setWarning] = React.useState<string | null>(null);
  const [lastGeneratedAt, setLastGeneratedAt] = React.useState<string | null>(null);
  const [metadata, setMetadata] = React.useState<ClinicalReasoningMetadata | null>(null);
  const [resultQuality, setResultQuality] = React.useState<ClinicalReasoningQuality | null>(null);
  const requestIdRef = React.useRef(0);

  const syncFromResponse = React.useCallback((response: ClinicalReasoningResponse) => {
    const result = response.reasoningResult || null;
    const resultMetadata = resolveReasoningMetadata(response, result);
    const quality = result ? deriveResultQuality(result, resultMetadata) : null;
    setClinicalReasoningResult(result);
    setReasoningId(response.reasoningId ?? null);
    setReasoningVersion(response.reasoningVersion ?? null);
    setReasoningStatus(response.reasoningStatus ?? (result ? "CURRENT" : "NOT_GENERATED"));
    setContextHash(response.contextHash ?? null);
    setStaleReason(response.staleReason ?? null);
    setGeneratedByAppUserId(response.generatedByAppUserId ?? null);
    setGeneratedByDisplayName(response.generatedByDisplayName ?? null);
    setMetadata(resultMetadata);
    setLastGeneratedAt(result?.generatedAt || null);
    setResultQuality(quality);
    return result;
  }, []);

  const clearReasoning = React.useCallback(() => {
    requestIdRef.current += 1;
    const next = emptyState();
    setClinicalReasoningResult(next.clinicalReasoningResult);
    setReasoningId(next.reasoningId);
    setReasoningVersion(next.reasoningVersion);
    setReasoningStatus(next.reasoningStatus);
    setContextHash(next.contextHash);
    setStaleReason(next.staleReason);
    setGeneratedByAppUserId(next.generatedByAppUserId);
    setGeneratedByDisplayName(next.generatedByDisplayName);
    setLoading(false);
    setHasLoaded(false);
    setError(next.error);
    setWarning(next.warning);
    setLastGeneratedAt(next.lastGeneratedAt);
    setMetadata(next.metadata);
    setResultQuality(next.resultQuality);
  }, []);

  const loadReasoning = React.useCallback(async (consultationId: string) => {
    if (!accessToken || !tenantId) {
      clearReasoning();
      setHasLoaded(true);
      setError("Clinical reasoning is unavailable for this session.");
      return null;
    }

    const requestId = ++requestIdRef.current;
    setLoading(true);
    setHasLoaded(false);
    setError(null);
    setWarning(null);
    setClinicalReasoningResult(null);
    setReasoningId(null);
    setReasoningVersion(null);
    setReasoningStatus("NOT_GENERATED");
    setContextHash(null);
    setStaleReason(null);
    setGeneratedByAppUserId(null);
    setGeneratedByDisplayName(null);
    setLastGeneratedAt(null);
    setMetadata(null);
    setResultQuality(null);

    try {
      const response = await getClinicalReasoning(accessToken, tenantId, consultationId);
      if (requestId !== requestIdRef.current) {
        return response.reasoningResult || null;
      }
      setHasLoaded(true);
      const result = syncFromResponse(response);
      if (response.reasoningStatus === "STALE" && response.staleReason) {
        setWarning(response.staleReason);
      } else {
        setWarning(null);
      }
      if (response.reasoningStatus === "FAILED" && !result) {
        setError(hardErrorMessage(response.metadata || null));
      }
      return result;
    } catch (err) {
      if (requestId !== requestIdRef.current) {
        return null;
      }
      setHasLoaded(true);
      setError(err instanceof Error ? err.message : "Unable to load clinical reasoning.");
      return null;
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [accessToken, clearReasoning, syncFromResponse, tenantId]);

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
    const previousResult = clinicalReasoningResult;
    const previousMetadata = metadata;
    const previousGeneratedAt = lastGeneratedAt;
    const previousReasoningId = reasoningId;
    const previousReasoningVersion = reasoningVersion;
    const previousReasoningStatus = reasoningStatus;
    const previousContextHash = contextHash;
    const previousStaleReason = staleReason;
    const previousGeneratedByAppUserId = generatedByAppUserId;
    const previousGeneratedByDisplayName = generatedByDisplayName;

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

      if (response.reasoningStatus === "NOT_GENERATED" || (response.reasoningStatus === "FAILED" && !result)) {
        if (previousResult) {
          setClinicalReasoningResult(previousResult);
          setMetadata(previousMetadata);
          setLastGeneratedAt(previousGeneratedAt);
          setReasoningId(previousReasoningId);
          setReasoningVersion(previousReasoningVersion);
          setReasoningStatus(previousReasoningStatus);
          setContextHash(previousContextHash);
          setStaleReason(previousStaleReason);
          setGeneratedByAppUserId(previousGeneratedByAppUserId);
          setGeneratedByDisplayName(previousGeneratedByDisplayName);
          setResultQuality(previousMetadata ? deriveResultQuality(previousResult, previousMetadata) : null);
        } else {
          setClinicalReasoningResult(null);
          setMetadata(null);
          setLastGeneratedAt(null);
          setReasoningId(null);
          setReasoningVersion(null);
          setReasoningStatus(response.reasoningStatus || "FAILED");
          setContextHash(response.contextHash ?? null);
          setStaleReason(response.staleReason ?? null);
          setGeneratedByAppUserId(null);
          setGeneratedByDisplayName(null);
          setResultQuality(null);
        }
        setWarning(null);
        setError(hardErrorMessage(resultMetadata));
        setHasLoaded(true);
        return previousResult;
      }

      if (quality === "COMPLETE" && result) {
        syncFromResponse(response);
        setError(null);
        setWarning(response.reasoningStatus === "STALE" ? response.staleReason || null : null);
        setHasLoaded(true);
        return result;
      }

      if (quality === "PARTIAL_FALLBACK" && result) {
        syncFromResponse(response);
        setWarning(partialWarningMessage(resultMetadata));
        setError(null);
        setHasLoaded(true);
        return result;
      }

      if (previousResult) {
        setClinicalReasoningResult(previousResult);
        setMetadata(previousMetadata);
        setLastGeneratedAt(previousGeneratedAt);
        setReasoningId(previousReasoningId);
        setReasoningVersion(previousReasoningVersion);
        setReasoningStatus(previousReasoningStatus);
        setContextHash(previousContextHash);
        setStaleReason(previousStaleReason);
        setGeneratedByAppUserId(previousGeneratedByAppUserId);
        setGeneratedByDisplayName(previousGeneratedByDisplayName);
        setResultQuality(previousMetadata ? deriveResultQuality(previousResult, previousMetadata) : null);
        setWarning(mode === "refresh" ? "Refresh failed. Showing the last successfully generated reasoning." : null);
        setError(hardErrorMessage(resultMetadata));
        setHasLoaded(true);
        return previousResult;
      }

      setClinicalReasoningResult(null);
      setMetadata(null);
      setLastGeneratedAt(null);
      setReasoningId(null);
      setReasoningVersion(null);
      setReasoningStatus(response.reasoningStatus || "FAILED");
      setContextHash(response.contextHash ?? null);
      setStaleReason(response.staleReason ?? null);
      setGeneratedByAppUserId(null);
      setGeneratedByDisplayName(null);
      setResultQuality("FAILED");
      setWarning(null);
      setError(hardErrorMessage(resultMetadata));
      setHasLoaded(true);
      return null;
    } catch (err) {
      if (requestId !== requestIdRef.current) {
        return null;
      }
      if (previousResult) {
        setClinicalReasoningResult(previousResult);
        setMetadata(previousMetadata);
        setLastGeneratedAt(previousGeneratedAt);
        setReasoningId(previousReasoningId);
        setReasoningVersion(previousReasoningVersion);
        setReasoningStatus(previousReasoningStatus);
        setContextHash(previousContextHash);
        setStaleReason(previousStaleReason);
        setGeneratedByAppUserId(previousGeneratedByAppUserId);
        setGeneratedByDisplayName(previousGeneratedByDisplayName);
        setResultQuality(previousMetadata ? deriveResultQuality(previousResult, previousMetadata) : null);
        setWarning(mode === "refresh" ? "Refresh failed. Showing the last successfully generated reasoning." : null);
        setError(err instanceof Error ? err.message : "Unable to generate AI reasoning. You may continue consultation normally.");
        setHasLoaded(true);
        return previousResult;
      }

      setClinicalReasoningResult(null);
      setMetadata(null);
      setLastGeneratedAt(null);
      setReasoningId(null);
      setReasoningVersion(null);
      setReasoningStatus("FAILED");
      setContextHash(null);
      setStaleReason(null);
      setGeneratedByAppUserId(null);
      setGeneratedByDisplayName(null);
      setResultQuality("FAILED");
      setWarning(null);
      setError(err instanceof Error ? err.message : "Unable to generate AI reasoning. You may continue consultation normally.");
      setHasLoaded(true);
      return null;
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [
    accessToken,
    buildRequest,
    clinicalReasoningResult,
    contextHash,
    generatedByAppUserId,
    generatedByDisplayName,
    lastGeneratedAt,
    metadata,
    reasoningId,
    reasoningStatus,
    reasoningVersion,
    staleReason,
    tenantId,
  ]);

  const generateReasoning = React.useCallback((consultationId: string) => runReasoning(consultationId, "generate"), [runReasoning]);
  const refreshReasoning = React.useCallback((consultationId: string) => runReasoning(consultationId, "refresh"), [runReasoning]);

  return {
    clinicalReasoningResult,
    reasoningId,
    reasoningVersion,
    reasoningStatus,
    contextHash,
    staleReason,
    generatedByAppUserId,
    generatedByDisplayName,
    loading,
    hasLoaded,
    error,
    warning,
    lastGeneratedAt,
    metadata,
    resultQuality,
    generateReasoning,
    refreshReasoning,
    loadReasoning,
    resetReasoning: clearReasoning,
  };
}
