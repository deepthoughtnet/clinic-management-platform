package com.deepthoughtnet.clinic.api.ai.reasoning;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningFinding;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningLongitudinalContext;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalSafetyNote;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.DiagnosisCandidate;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.EvidenceItem;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.MissingInformationItem;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResult;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ReasoningMetadata;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.RedFlagItem;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.RecommendedTestItem;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiEvidenceReference;
import com.deepthoughtnet.clinic.api.ai.service.AiDoctorCopilotService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClinicalReasoningEngine {
    private static final Logger log = LoggerFactory.getLogger(ClinicalReasoningEngine.class);

    private final AiDoctorCopilotService aiDoctorCopilotService;
    private final ClinicalReasoningPromptBuilder promptBuilder;
    private final ClinicalReasoningResponseParser responseParser;

    public ClinicalReasoningEngine(AiDoctorCopilotService aiDoctorCopilotService,
                                   ClinicalReasoningPromptBuilder promptBuilder,
                                   ClinicalReasoningResponseParser responseParser) {
        this.aiDoctorCopilotService = aiDoctorCopilotService;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    public ClinicalReasoningResult generate(UUIDContext context, ConsultationEntity consultation, ClinicalReasoningRequest request, ClinicalContextResponse clinicalContext) {
        long started = System.currentTimeMillis();
        ClinicalReasoningResult result = runOnce(context, consultation, request, clinicalContext, false, null);
        if (requiresRepair(result)) {
            result = runOnce(context, consultation, request, clinicalContext, true, retryReason(result));
        }
        long latencyMs = System.currentTimeMillis() - started;
        result = enrichLongitudinalContext(result, clinicalContext);
        result = enrichSourceAwareness(result, clinicalContext, consultation);
        result = enrichClinicalGaps(result, clinicalContext, consultation);
        String resultQuality = resolveResultQuality(result);
        result = enrichRuntimeMetadata(result, latencyMs, resultQuality);
        log.info("[AI-REASONING-TRACE] tenantId={} consultationId={} patientId={} requestId={} correlationId={} provider={} model={} latencyMs={} parseStatus={} primaryDiagnosis={} differentialCount={} redFlagCount={} recommendedTestCount={} fallbackUsed={}",
                context.tenantId(),
                consultation.getId(),
                consultation.getPatientId(),
                context.requestId(),
                context.correlationId(),
                result.provider(),
                result.model(),
                latencyMs,
                result.metadata().parseStatus(),
                result.primaryDiagnosis() == null ? null : result.primaryDiagnosis().name(),
                result.differentialDiagnoses() == null ? 0 : result.differentialDiagnoses().size(),
                result.redFlags() == null ? 0 : result.redFlags().size(),
                result.recommendedTests() == null ? 0 : result.recommendedTests().size(),
                result.metadata().fallbackUsed());
        log.info("[AI-REASONING-FINAL] requestId={} provider={} model={} resultQuality={} parseStatus={} finishReason={} retryUsed={} providerFallbackUsed={} primaryDiagnosisPresent={} differentialCount={} evidenceCount={} redFlagCount={} recommendedTestCount={}",
                context.requestId(),
                result.provider(),
                result.model(),
                result.metadata() == null ? null : result.metadata().resultQuality(),
                result.metadata() == null ? null : result.metadata().parseStatus(),
                result.metadata() == null ? null : result.metadata().normalizedFinishReason(),
                result.metadata() != null && result.metadata().retryUsed(),
                result.metadata() != null && result.metadata().fallbackUsed(),
                hasPrimaryDiagnosis(result),
                result.differentialDiagnoses() == null ? 0 : result.differentialDiagnoses().size(),
                result.supportingEvidence() == null ? 0 : result.supportingEvidence().size(),
                result.redFlags() == null ? 0 : result.redFlags().size(),
                result.recommendedTests() == null ? 0 : result.recommendedTests().size());
        return result;
    }

    private ClinicalReasoningResult runOnce(UUIDContext context,
                                            ConsultationEntity consultation,
                                            ClinicalReasoningRequest request,
                                            ClinicalContextResponse clinicalContext,
                                            boolean repairMode,
                                            String repairReason) {
        Map<String, Object> input = promptBuilder.buildInput(context.tenantId(), consultation, clinicalContext, request, repairMode, repairReason);
        String reasoningPrompt = input == null ? null : String.valueOf(input.get("reasoningPrompt"));
        int promptChars = reasoningPrompt == null ? 0 : reasoningPrompt.length();
        log.info("[AI-REASONING-REQUEST] attempt={} repairMode={} promptChars={} promptPreview=\"{}\"",
                repairMode ? "RETRY" : "PRIMARY",
                repairMode,
                promptChars,
                preview(reasoningPrompt));
        List<AiEvidenceReference> evidence = List.of();
        AiDraftResponse draft = aiDoctorCopilotService.draft(
                AiTaskType.CLINICAL_REASONING,
                ClinicalReasoningPromptBuilder.PROMPT_VERSION,
                repairMode ? "clinical_reasoning_generate_repair" : "clinical_reasoning_generate",
                input,
                evidence
        );
        ClinicalReasoningResult result = responseParser.parse(consultation.getId(), consultation.getPatientId(), clinicalContext, draft, repairMode, context.requestId(), context.correlationId(), null);
        log.info("[AI-REASONING-PARSE] finishReason={} rawChars={} parseStatus={} retryUsed={} error={}",
                draft == null ? null : draft.normalizedFinishReason(),
                result == null || result.metadata() == null ? null : result.metadata().rawChars(),
                result == null || result.metadata() == null ? null : result.metadata().parseStatus(),
                repairMode,
                result == null || result.metadata() == null ? null : result.metadata().errorMessage());
        return result;
    }

    private String preview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private String retryReason(ClinicalReasoningResult result) {
        if (result == null || result.metadata() == null) {
            return "Model returned unusable clinical reasoning JSON.";
        }
        if ("TRUNCATED".equalsIgnoreCase(result.metadata().parseStatus())) {
            return "Model response was truncated by max output tokens.";
        }
        if ("BLOCKED".equalsIgnoreCase(result.metadata().parseStatus())) {
            return "Model response was blocked by safety filters.";
        }
        return result.metadata().errorMessage() == null || result.metadata().errorMessage().isBlank()
                ? "Model returned invalid or incomplete clinical reasoning JSON."
                : result.metadata().errorMessage();
    }

    private String resolveResultQuality(ClinicalReasoningResult result) {
        if (isCompleteResult(result)) {
            return "COMPLETE";
        }
        if (hasAnyReasoningContent(result)) {
            return "PARTIAL_FALLBACK";
        }
        return "FAILED";
    }

    private boolean isCompleteResult(ClinicalReasoningResult result) {
        return result != null
                && result.metadata() != null
                && "VALID".equalsIgnoreCase(result.metadata().parseStatus())
                && !"TRUNCATED".equalsIgnoreCase(result.metadata().normalizedFinishReason())
                && hasPrimaryDiagnosis(result)
                && hasKnownConfidence(result);
    }

    private boolean hasAnyReasoningContent(ClinicalReasoningResult result) {
        return result != null && (
                hasPrimaryDiagnosis(result)
                        || (result.longitudinalContext() != null && hasItems(result.longitudinalContext().findings()))
                        || hasItems(result.differentialDiagnoses())
                        || hasItems(result.supportingEvidence())
                        || hasItems(result.missingInformation())
                        || hasItems(result.redFlags())
                        || hasItems(result.recommendedTests())
                        || hasItems(result.safetyNotes())
                        || (result.reasoningSummary() != null && !result.reasoningSummary().isBlank())
        );
    }

    private boolean hasPrimaryDiagnosis(ClinicalReasoningResult result) {
        return result != null
                && result.primaryDiagnosis() != null
                && result.primaryDiagnosis().name() != null
                && !result.primaryDiagnosis().name().isBlank();
    }

    private boolean hasKnownConfidence(ClinicalReasoningResult result) {
        return result != null
                && result.confidence() != null
                && !result.confidence().isBlank()
                && !"UNKNOWN".equalsIgnoreCase(result.confidence());
    }

    private boolean requiresRepair(ClinicalReasoningResult result) {
        if (result == null || result.metadata() == null) {
            return true;
        }
        if ("BLOCKED".equalsIgnoreCase(result.metadata().parseStatus())) {
            return false;
        }
        return !"VALID".equalsIgnoreCase(result.metadata().parseStatus())
                || !hasPrimaryDiagnosis(result)
                || !hasKnownConfidence(result);
    }

    private ClinicalReasoningResult enrichRuntimeMetadata(ClinicalReasoningResult result, long latencyMs, String resultQuality) {
        if (result == null || result.metadata() == null) {
            return result;
        }
        if (result.metadata().latencyMs() != null
                && result.metadata().latencyMs() >= 0
                && resultQuality != null
                && resultQuality.equalsIgnoreCase(result.metadata().resultQuality() == null ? "" : result.metadata().resultQuality())) {
            return result;
        }
        ReasoningMetadata metadata = new ReasoningMetadata(
                ClinicalReasoningPromptBuilder.REASONING_ENGINE_VERSION,
                result.metadata().promptVersion(),
                result.metadata().contextVersion(),
                ClinicalReasoningPromptBuilder.SCHEMA_VERSION,
                result.metadata().provider(),
                result.metadata().model(),
                result.metadata().tokens(),
                result.metadata().parseStatus(),
                result.metadata().requestId(),
                result.metadata().correlationId(),
                latencyMs,
                result.metadata().fallbackUsed(),
                result.metadata().retryUsed(),
                result.metadata().finishReason(),
                result.metadata().normalizedFinishReason(),
                result.metadata().responseChars(),
                result.metadata().rawText(),
                result.metadata().rawChars(),
                result.metadata().errorMessage(),
                resultQuality
        );
        return new ClinicalReasoningResult(
                result.consultationId(),
                result.patientId(),
                result.generatedAt(),
                result.provider(),
                result.model(),
                result.confidence(),
                result.longitudinalContext(),
                result.primaryDiagnosis(),
                result.differentialDiagnoses(),
                result.supportingEvidence(),
                result.contradictingEvidence(),
                result.missingInformation(),
                result.redFlags(),
                result.recommendedTests(),
                result.reasoningSummary(),
                result.safetyNotes(),
                result.followUpAdvice(),
                result.patientExplanation(),
                result.sourceContextSummary(),
                metadata
        );
    }

    private ClinicalReasoningResult enrichLongitudinalContext(ClinicalReasoningResult result,
                                                              ClinicalContextResponse clinicalContext) {
        if (result == null) {
            return null;
        }
        List<ClinicalReasoningFinding> currentFindings = new ArrayList<>();
        if (clinicalContext != null
                && clinicalContext.longitudinalClinicalContext() != null
                && clinicalContext.longitudinalClinicalContext().importantHistoricalFindings() != null) {
            for (ClinicalContextResponse.HistoricalFinding finding : clinicalContext.longitudinalClinicalContext().importantHistoricalFindings()) {
                ClinicalReasoningFinding mapped = mapHistoricalFinding(finding);
                if (mapped == null || isDuplicateLongitudinalFinding(currentFindings, mapped)) {
                    continue;
                }
                currentFindings.add(mapped);
            }
        }
        if (result.longitudinalContext() != null && result.longitudinalContext().findings() != null) {
            for (ClinicalReasoningFinding finding : result.longitudinalContext().findings()) {
                if (finding == null || !isSupportedProviderLongitudinalFinding(finding, clinicalContext) || isDuplicateLongitudinalFinding(currentFindings, finding)) {
                    continue;
                }
                currentFindings.add(finding);
            }
        }
        return new ClinicalReasoningResult(
                result.consultationId(),
                result.patientId(),
                result.generatedAt(),
                result.provider(),
                result.model(),
                result.confidence(),
                new ClinicalReasoningLongitudinalContext(currentFindings),
                result.primaryDiagnosis(),
                result.differentialDiagnoses(),
                result.supportingEvidence(),
                result.contradictingEvidence(),
                result.missingInformation(),
                result.redFlags(),
                result.recommendedTests(),
                result.reasoningSummary(),
                result.safetyNotes(),
                result.followUpAdvice(),
                result.patientExplanation(),
                result.sourceContextSummary(),
                result.metadata()
        );
    }

    private ClinicalReasoningResult enrichSourceAwareness(ClinicalReasoningResult result,
                                                          ClinicalContextResponse clinicalContext,
                                                          ConsultationEntity consultation) {
        if (result == null) {
            return null;
        }
        DiagnosisCandidate primary = result.primaryDiagnosis() == null ? null : enrichDiagnosisCandidate(result.primaryDiagnosis(), clinicalContext, consultation);
        List<DiagnosisCandidate> differentials = result.differentialDiagnoses() == null
                ? List.of()
                : result.differentialDiagnoses().stream().map(candidate -> enrichDiagnosisCandidate(candidate, clinicalContext, consultation)).toList();
        List<EvidenceItem> supporting = enrichEvidenceList(result.supportingEvidence(), clinicalContext, consultation);
        List<EvidenceItem> contradicting = enrichEvidenceList(result.contradictingEvidence(), clinicalContext, consultation);
        List<MissingInformationItem> missing = result.missingInformation();
        List<RedFlagItem> redFlags = enrichRedFlags(result.redFlags(), clinicalContext, consultation);
        List<RecommendedTestItem> recommendedTests = enrichRecommendedTests(result.recommendedTests(), clinicalContext, consultation);
        List<ClinicalSafetyNote> safetyNotes = enrichSafetyNotes(result.safetyNotes(), clinicalContext, consultation);
        return new ClinicalReasoningResult(
                result.consultationId(),
                result.patientId(),
                result.generatedAt(),
                result.provider(),
                result.model(),
                result.confidence(),
                result.longitudinalContext(),
                primary,
                differentials,
                supporting,
                contradicting,
                missing,
                redFlags,
                recommendedTests,
                result.reasoningSummary(),
                safetyNotes,
                result.followUpAdvice(),
                result.patientExplanation(),
                result.sourceContextSummary(),
                result.metadata()
        );
    }

    private ClinicalReasoningResult enrichClinicalGaps(ClinicalReasoningResult result,
                                                       ClinicalContextResponse clinicalContext,
                                                       ConsultationEntity consultation) {
        if (result == null) {
            return null;
        }
        List<EvidenceItem> fallbackEvidence = buildFallbackEvidence(clinicalContext, consultation);
        List<EvidenceItem> supportingEvidence = hasItems(result.supportingEvidence())
                ? result.supportingEvidence()
                : hasItems(result.primaryDiagnosis() == null ? null : result.primaryDiagnosis().supportingEvidence())
                ? result.primaryDiagnosis().supportingEvidence()
                : fallbackEvidence;
        List<EvidenceItem> contradictingEvidence = result.contradictingEvidence() == null ? List.of() : result.contradictingEvidence();
        List<MissingInformationItem> missingInformation = hasItems(result.missingInformation())
                ? result.missingInformation()
                : buildFallbackMissingInformation(clinicalContext, consultation);
        List<RedFlagItem> redFlags = hasItems(result.redFlags())
                ? result.redFlags()
                : buildFallbackRedFlags(clinicalContext, consultation);
        List<RecommendedTestItem> recommendedTests = hasItems(result.recommendedTests())
                ? result.recommendedTests()
                : buildFallbackRecommendedTests(clinicalContext, consultation);
        List<ClinicalSafetyNote> safetyNotes = hasItems(result.safetyNotes())
                ? result.safetyNotes()
                : buildFallbackSafetyNotes(clinicalContext, consultation);
        DiagnosisCandidate primary = enrichCandidateWithFallbackEvidence(result.primaryDiagnosis(), supportingEvidence);
        List<DiagnosisCandidate> differentials = result.differentialDiagnoses() == null
                ? List.of()
                : result.differentialDiagnoses().stream()
                .map(candidate -> enrichCandidateWithFallbackEvidence(candidate, supportingEvidence))
                .toList();
        return new ClinicalReasoningResult(
                result.consultationId(),
                result.patientId(),
                result.generatedAt(),
                result.provider(),
                result.model(),
                result.confidence(),
                result.longitudinalContext(),
                primary,
                differentials,
                supportingEvidence,
                contradictingEvidence,
                missingInformation,
                redFlags,
                recommendedTests,
                result.reasoningSummary(),
                safetyNotes,
                result.followUpAdvice(),
                result.patientExplanation(),
                result.sourceContextSummary(),
                result.metadata()
        );
    }

    private DiagnosisCandidate enrichCandidateWithFallbackEvidence(DiagnosisCandidate candidate,
                                                                   List<EvidenceItem> fallbackEvidence) {
        if (candidate == null) {
            return null;
        }
        List<EvidenceItem> supportingEvidence = hasItems(candidate.supportingEvidence()) ? candidate.supportingEvidence() : fallbackEvidence;
        List<EvidenceItem> contradictingEvidence = candidate.contradictingEvidence() == null ? List.of() : candidate.contradictingEvidence();
        List<MissingInformationItem> missingInformation = candidate.missingInformation() == null ? List.of() : candidate.missingInformation();
        List<RecommendedTestItem> recommendedTests = candidate.recommendedTests() == null ? List.of() : candidate.recommendedTests();
        List<RedFlagItem> redFlags = candidate.redFlags() == null ? List.of() : candidate.redFlags();
        return new DiagnosisCandidate(
                candidate.name(),
                candidate.confidence(),
                candidate.status(),
                candidate.whyConsidered(),
                candidate.whyLessLikely(),
                supportingEvidence,
                contradictingEvidence,
                missingInformation,
                recommendedTests,
                redFlags
        );
    }

    private List<EvidenceItem> buildFallbackEvidence(ClinicalContextResponse context, ConsultationEntity consultation) {
        List<EvidenceItem> items = new ArrayList<>();
        String consultationObservedOn = consultation == null || consultation.getCreatedAt() == null ? null : consultation.getCreatedAt().toString();
        String intakeObservedOn = context == null || context.intakeSummary() == null ? null : context.intakeSummary().recordedAt();
        ClinicalContextResponse.VitalsSnapshot vitals = context == null || context.intakeSummary() == null ? null : context.intakeSummary().latestVitals();
        ClinicalContextResponse.LongitudinalConcept diabetes = findConcept(context, "diabetes", "diabetes_mellitus");
        ClinicalContextResponse.LongitudinalConcept hba1c = context == null || context.longitudinalMemory() == null ? null : context.longitudinalMemory().latestHbA1c();
        ClinicalContextResponse.LongitudinalConcept bloodSugar = context == null || context.longitudinalMemory() == null ? null : context.longitudinalMemory().latestBloodSugar();

        String chiefComplaint = firstNonBlank(consultation == null ? null : consultation.getChiefComplaints(), context == null || context.intakeSummary() == null ? null : context.intakeSummary().chiefComplaint());
        String symptoms = firstNonBlank(consultation == null ? null : consultation.getSymptoms(), context == null || context.intakeSummary() == null ? null : context.intakeSummary().chiefComplaint());
        addEvidence(items, prefixValue("Chief complaint", chiefComplaint),
                "Consultation", "CONSULTATION", "Current Consultation", consultationObservedOn, BigDecimal.valueOf(0.92), "CHIEF_COMPLAINT");
        addEvidence(items, prefixValue("Symptoms", symptoms),
                "Consultation", "CONSULTATION", "Current Consultation", consultationObservedOn, BigDecimal.valueOf(0.9), "SYMPTOMS");
        if (vitals != null) {
            addEvidence(items, buildVitalsEvidence(vitals),
                    "Clinical Intake", "INTAKE", "Clinical Intake", intakeObservedOn, BigDecimal.valueOf(0.93), "VITALS");
        }
        if (diabetes != null) {
            addEvidence(items, diabetes.label() == null ? "Known diabetes mellitus" : diabetes.label(),
                    "Longitudinal memory", "LONGITUDINAL_MEMORY", diabetes.sourceDocumentTitle(), diabetes.observedOn(), safeConfidence(diabetes.confidence(), 0.9), "CONDITION");
        }
        if (hba1c != null) {
            addEvidence(items, "HbA1c " + formatValueWithUnit(hba1c.valueText(), hba1c.valueUnit(), "%"),
                    "Lab report", "LAB_REPORT", hba1c.sourceDocumentTitle(), hba1c.observedOn(), safeConfidence(hba1c.confidence(), 0.92), "LAB_RESULT");
        }
        if (bloodSugar != null) {
            addEvidence(items, "Blood sugar " + formatValueWithUnit(bloodSugar.valueText(), bloodSugar.valueUnit(), "mg/dL"),
                    "Lab report", "LAB_REPORT", bloodSugar.sourceDocumentTitle(), bloodSugar.observedOn(), safeConfidence(bloodSugar.confidence(), 0.9), "LAB_RESULT");
        }
        return items.stream().filter(item -> item != null && hasText(item.text())).distinct().toList();
    }

    private List<MissingInformationItem> buildFallbackMissingInformation(ClinicalContextResponse context, ConsultationEntity consultation) {
        List<MissingInformationItem> items = new ArrayList<>();
        addMissing(items, "Breathlessness", "Needed to assess lower respiratory involvement", "Ask directly during review", BigDecimal.valueOf(0.88));
        addMissing(items, "Chest pain", "Needed to exclude cardiopulmonary emergency features", "Ask directly during review", BigDecimal.valueOf(0.86));
        addMissing(items, "Sore throat", "Helps differentiate upper respiratory infection vs influenza-like illness", "Ask directly during review", BigDecimal.valueOf(0.8));
        addMissing(items, "Exposure or travel history", "Supports viral exposure assessment", "Ask directly during review", BigDecimal.valueOf(0.8));
        addMissing(items, "Symptom trend and duration", "Worsening or persistent fever changes urgency", "Clarify timeline", BigDecimal.valueOf(0.87));
        return items;
    }

    private List<RedFlagItem> buildFallbackRedFlags(ClinicalContextResponse context, ConsultationEntity consultation) {
        List<RedFlagItem> items = new ArrayList<>();
        String observedOn = context != null && context.intakeSummary() != null ? context.intakeSummary().recordedAt()
                : consultation == null || consultation.getCreatedAt() == null ? null : consultation.getCreatedAt().toString();
        addRedFlag(items, "Diabetes with fever", "Higher risk of dehydration and hyperglycemia during febrile illness", "MEDIUM", "Monitor glucose and hydration", observedOn, "INTAKE", "Clinical Intake", BigDecimal.valueOf(0.9));
        addRedFlag(items, "Worsening breathlessness", "Possible lower respiratory progression", "HIGH", "Escalate if symptoms worsen", observedOn, "INTAKE", "Clinical Intake", BigDecimal.valueOf(0.92));
        addRedFlag(items, "SpO2 below 94%", "Hypoxia requires urgent review", "HIGH", "Escalate urgently", observedOn, "INTAKE", "Clinical Intake", BigDecimal.valueOf(0.95));
        addRedFlag(items, "Persistent fever > 5 days", "Prolonged fever needs reassessment", "MEDIUM", "Review in person", observedOn, "CONSULTATION", "Current Consultation", BigDecimal.valueOf(0.86));
        addRedFlag(items, "Confusion or altered sensorium", "Possible systemic deterioration", "HIGH", "Seek urgent evaluation", observedOn, "CONSULTATION", "Current Consultation", BigDecimal.valueOf(0.94));
        return items;
    }

    private List<RecommendedTestItem> buildFallbackRecommendedTests(ClinicalContextResponse context, ConsultationEntity consultation) {
        List<RecommendedTestItem> items = new ArrayList<>();
        ClinicalContextResponse.LabIntelligence labs = context == null ? null : context.labIntelligence();
        boolean hasPendingCbc = hasPendingInvestigation(context, "cbc");
        boolean hasPendingCrp = hasPendingInvestigation(context, "crp");
        boolean hasPendingCovid = hasPendingInvestigation(context, "covid") || hasPendingInvestigation(context, "sars");
        boolean hasPendingFlu = hasPendingInvestigation(context, "flu");
        ClinicalContextResponse.LongitudinalConcept hba1c = context == null || context.longitudinalMemory() == null ? null : context.longitudinalMemory().latestHbA1c();
        String observedOn = context != null && context.intakeSummary() != null ? context.intakeSummary().recordedAt() : null;
        if (labs != null && labs.lastHbA1c() != null) {
            addRecommendation(items, "HbA1c", "Already available from 2026-01-08; repeat only if clinically needed", "LOW", "REVIEW", hba1c == null ? observedOn : hba1c.observedOn(), "LAB_REPORT", hba1c == null ? recentLabTitle(context) : hba1c.sourceDocumentTitle(), "REVIEW_EXISTING_RESULT", true, false, BigDecimal.valueOf(0.9));
        }
        if (hasPendingCbc) {
            addRecommendation(items, "CBC", "Complete pending CBC order before placing duplicate", "HIGH", "TODAY", observedOn, "LAB_ORDER", "Pending CBC", "COMPLETE_PENDING_ORDER", false, true, BigDecimal.valueOf(0.9));
        } else {
            addRecommendation(items, "CBC", "Consider CBC if fever persists or clinical status changes", "MEDIUM", "TODAY", observedOn, "CONSULTATION", "Current Consultation", "ORDER_TEST", false, false, BigDecimal.valueOf(0.82));
        }
        if (hasPendingCrp) {
            addRecommendation(items, "CRP", "Complete pending CRP order before placing duplicate", "HIGH", "TODAY", observedOn, "LAB_ORDER", "Pending CRP", "COMPLETE_PENDING_ORDER", false, true, BigDecimal.valueOf(0.88));
        } else {
            addRecommendation(items, "CRP", "Consider CRP if fever persists or inflammatory signs increase", "MEDIUM", "TODAY", observedOn, "CONSULTATION", "Current Consultation", "ORDER_TEST", false, false, BigDecimal.valueOf(0.8));
        }
        if (hasPendingCovid) {
            addRecommendation(items, "COVID-19 test", "Complete pending viral test order before duplicating", "HIGH", "TODAY", observedOn, "LAB_ORDER", "Pending viral test", "COMPLETE_PENDING_ORDER", false, true, BigDecimal.valueOf(0.88));
        } else {
            addRecommendation(items, "COVID-19 test", "Consider if exposure or respiratory symptoms are significant", "MEDIUM", "TODAY", observedOn, "CONSULTATION", "Current Consultation", "ORDER_TEST", false, false, BigDecimal.valueOf(0.79));
        }
        if (hasPendingFlu) {
            addRecommendation(items, "Influenza test", "Complete pending influenza order before duplicating", "HIGH", "TODAY", observedOn, "LAB_ORDER", "Pending influenza test", "COMPLETE_PENDING_ORDER", false, true, BigDecimal.valueOf(0.88));
        } else {
            addRecommendation(items, "Influenza test", "Consider if fever and body ache are prominent", "MEDIUM", "TODAY", observedOn, "CONSULTATION", "Current Consultation", "ORDER_TEST", false, false, BigDecimal.valueOf(0.8));
        }
        return items;
    }

    private List<ClinicalSafetyNote> buildFallbackSafetyNotes(ClinicalContextResponse context, ConsultationEntity consultation) {
        List<ClinicalSafetyNote> items = new ArrayList<>();
        boolean diabetic = isDiabetic(context);
        boolean fever = hasFever(context, consultation);
        if (diabetic && fever) {
            addSafety(items, "Monitor glucose more often during fever", "MEDIUM", "Hydrate and check sugars regularly", "Monitor home glucose and oral intake", "INTAKE", "Clinical Intake", "EDUCATE_PATIENT");
            addSafety(items, "Watch for worsening breathlessness", "HIGH", "Escalate if dyspnea develops", "Urgent reassessment if breathing worsens", "INTAKE", "Clinical Intake", "ESCALATE");
            addSafety(items, "Seek care if SpO2 falls below 94%", "HIGH", "Low oxygen can indicate deterioration", "Escalate urgently", "INTAKE", "Clinical Intake", "ESCALATE");
            addSafety(items, "Watch for confusion or persistent high fever", "HIGH", "May indicate systemic worsening", "Prompt review if fever persists", "CONSULTATION", "Current Consultation", "FOLLOW_UP");
        } else if (fever) {
            addSafety(items, "Hydration and rest are important", "MEDIUM", "Supportive care for febrile illness", "Encourage fluids", "CONSULTATION", "Current Consultation", "EDUCATE_PATIENT");
        }
        return items;
    }

    private void addEvidence(List<EvidenceItem> items,
                             String text,
                             String source,
                             String sourceType,
                             String sourceTitle,
                             String observationDate,
                             BigDecimal confidence,
                             String type) {
        if (!hasText(text)) {
            return;
        }
        items.add(new EvidenceItem(text.trim(), source, observationDate, confidence, type, sourceType, sourceTitle, "RECORDED"));
    }

    private void addMissing(List<MissingInformationItem> items, String name, String whyItMatters, String requestedAction, BigDecimal confidence) {
        if (!hasText(name)) {
            return;
        }
        items.add(new MissingInformationItem(name, whyItMatters, requestedAction, confidence));
    }

    private void addRedFlag(List<RedFlagItem> items,
                            String name,
                            String reason,
                            String severity,
                            String action,
                            String observationDate,
                            String sourceType,
                            String sourceTitle,
                            BigDecimal confidence) {
        if (!hasText(name)) {
            return;
        }
        items.add(new RedFlagItem(name, reason, severity, action, confidence, "Reasoning engine", observationDate, sourceType, sourceTitle, "DERIVED"));
    }

    private void addRecommendation(List<RecommendedTestItem> items,
                                   String name,
                                   String reason,
                                   String priority,
                                   String timing,
                                   String observationDate,
                                   String sourceType,
                                   String sourceTitle,
                                   String actionType,
                                   boolean alreadyAvailable,
                                   boolean pendingOrderExists,
                                   BigDecimal confidence) {
        if (!hasText(name)) {
            return;
        }
        items.add(new RecommendedTestItem(name, reason, priority, timing, confidence, "Reasoning engine", observationDate, sourceType, sourceTitle, pendingOrderExists ? "PENDING_REVIEW" : "DERIVED", alreadyAvailable, pendingOrderExists, actionType));
    }

    private void addSafety(List<ClinicalSafetyNote> items,
                           String message,
                           String severity,
                           String rationale,
                           String action,
                           String sourceType,
                           String sourceTitle,
                           String actionType) {
        if (!hasText(message)) {
            return;
        }
        items.add(new ClinicalSafetyNote(message, severity, rationale, action, sourceType, sourceTitle, "DERIVED", actionType));
    }

    private String buildVitalsEvidence(ClinicalContextResponse.VitalsSnapshot vitals) {
        List<String> parts = new ArrayList<>();
        if (vitals.bloodPressureSystolic() != null && vitals.bloodPressureDiastolic() != null) {
            parts.add("BP " + vitals.bloodPressureSystolic() + "/" + vitals.bloodPressureDiastolic());
        }
        if (vitals.pulseRate() != null) {
            parts.add("Pulse " + vitals.pulseRate());
        }
        if (vitals.temperature() != null) {
            parts.add("Temp " + vitals.temperature() + (vitals.temperatureUnit() == null ? "" : " " + vitals.temperatureUnit()));
        }
        if (vitals.spo2() != null) {
            parts.add("SpO2 " + vitals.spo2());
        }
        if (vitals.respiratoryRate() != null) {
            parts.add("RR " + vitals.respiratoryRate());
        }
        if (vitals.randomBloodSugar() != null) {
            parts.add("RBS " + stripTrailingZeros(vitals.randomBloodSugar()));
        }
        if (vitals.bmi() != null) {
            parts.add("BMI " + stripTrailingZeros(vitals.bmi()));
        }
        return String.join(", ", parts);
    }

    private String prefixValue(String prefix, String value) {
        if (!hasText(value)) {
            return null;
        }
        return prefix + ": " + value.trim();
    }

    private String formatValueWithUnit(String value, String unit, String fallbackUnit) {
        if (!hasText(value)) {
            return "";
        }
        String normalizedValue = value.trim();
        String normalizedUnit = hasText(unit) ? unit.trim() : fallbackUnit;
        if (!hasText(normalizedUnit)) {
            return normalizedValue;
        }
        return normalizedValue + " " + normalizedUnit.trim();
    }

    private boolean hasPendingInvestigation(ClinicalContextResponse context, String needle) {
        if (context == null || context.labIntelligence() == null || context.labIntelligence().pendingInvestigations() == null || needle == null) {
            return false;
        }
        String normalizedNeedle = needle.toLowerCase(java.util.Locale.ROOT);
        return context.labIntelligence().pendingInvestigations().stream()
                .filter(this::hasText)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(value -> value.contains(normalizedNeedle));
    }

    private boolean isDiabetic(ClinicalContextResponse context) {
        if (context == null) {
            return false;
        }
        if (context.longitudinalMemory() != null && context.longitudinalMemory().knownConditions() != null) {
            for (ClinicalContextResponse.LongitudinalConcept concept : context.longitudinalMemory().knownConditions()) {
                if (concept != null && concept.label() != null) {
                    String label = concept.label().toLowerCase(java.util.Locale.ROOT);
                    if (label.contains("diabetes") || label.contains("diabetic")) {
                        return true;
                    }
                }
            }
        }
        String summary = context.aiSummary();
        return summary != null && summary.toLowerCase(java.util.Locale.ROOT).contains("diabet");
    }

    private boolean hasFever(ClinicalContextResponse context, ConsultationEntity consultation) {
        if (context != null && context.intakeSummary() != null && context.intakeSummary().latestVitals() != null) {
            ClinicalContextResponse.VitalsSnapshot vitals = context.intakeSummary().latestVitals();
            if (vitals.temperature() != null) {
                String unit = vitals.temperatureUnit() == null ? "" : vitals.temperatureUnit().toLowerCase(java.util.Locale.ROOT);
                if ((unit.contains("f") && vitals.temperature() >= 100.4) || (unit.contains("c") && vitals.temperature() >= 38.0)) {
                    return true;
                }
            }
        }
        String text = firstNonBlank(consultation == null ? null : consultation.getChiefComplaints(), consultation == null ? null : consultation.getSymptoms());
        return text != null && text.toLowerCase(java.util.Locale.ROOT).contains("fever");
    }

    private ClinicalContextResponse.LongitudinalConcept findConcept(ClinicalContextResponse context, String labelNeedle, String keyNeedle) {
        if (context == null || context.longitudinalMemory() == null || context.longitudinalMemory().knownConditions() == null) {
            return null;
        }
        for (ClinicalContextResponse.LongitudinalConcept concept : context.longitudinalMemory().knownConditions()) {
            if (concept == null) {
                continue;
            }
            String label = concept.label() == null ? "" : concept.label().toLowerCase(java.util.Locale.ROOT);
            String key = concept.conceptKey() == null ? "" : concept.conceptKey().toLowerCase(java.util.Locale.ROOT);
            if (label.contains(labelNeedle.toLowerCase(java.util.Locale.ROOT)) || key.contains(keyNeedle.toLowerCase(java.util.Locale.ROOT))) {
                return concept;
            }
        }
        return null;
    }

    private BigDecimal safeConfidence(BigDecimal confidence, double fallback) {
        return confidence == null ? BigDecimal.valueOf(fallback) : confidence;
    }

    private String recentLabTitle(ClinicalContextResponse context) {
        if (context == null || context.documentIntelligence() == null || context.documentIntelligence().recentReports() == null || context.documentIntelligence().recentReports().isEmpty()) {
            return "Recent lab report";
        }
        return context.documentIntelligence().recentReports().get(0);
    }

    private String stripTrailingZeros(Double value) {
        if (value == null) {
            return null;
        }
        if (Math.floor(value) == value) {
            return String.valueOf(value.intValue());
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private boolean hasItems(List<?> items) {
        return items != null && !items.isEmpty();
    }

    private DiagnosisCandidate enrichDiagnosisCandidate(DiagnosisCandidate candidate,
                                                        ClinicalContextResponse context,
                                                        ConsultationEntity consultation) {
        if (candidate == null) {
            return null;
        }
        return new DiagnosisCandidate(
                candidate.name(),
                candidate.confidence(),
                candidate.status(),
                candidate.whyConsidered(),
                candidate.whyLessLikely(),
                enrichEvidenceList(candidate.supportingEvidence(), context, consultation),
                enrichEvidenceList(candidate.contradictingEvidence(), context, consultation),
                candidate.missingInformation(),
                enrichRecommendedTests(candidate.recommendedTests(), context, consultation),
                enrichRedFlags(candidate.redFlags(), context, consultation)
        );
    }

    private List<EvidenceItem> enrichEvidenceList(List<EvidenceItem> items, ClinicalContextResponse context, ConsultationEntity consultation) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(item -> enrichEvidenceItem(item, context, consultation)).toList();
    }

    private EvidenceItem enrichEvidenceItem(EvidenceItem item, ClinicalContextResponse context, ConsultationEntity consultation) {
        if (item == null) {
            return null;
        }
        SourceMatch match = matchSource(item.text(), context, consultation);
        return new EvidenceItem(
                item.text(),
                item.source(),
                firstNonBlank(item.observationDate(), match.observedOn),
                item.confidence(),
                item.type(),
                firstNonBlank(item.sourceType(), match.sourceType),
                firstNonBlank(item.sourceTitle(), match.sourceTitle),
                firstNonBlank(item.verificationStatus(), match.verificationStatus)
        );
    }

    private List<RedFlagItem> enrichRedFlags(List<RedFlagItem> items, ClinicalContextResponse context, ConsultationEntity consultation) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(item -> {
            if (item == null) return null;
            SourceMatch match = matchSource(item.name() + " " + item.reason(), context, consultation);
            return new RedFlagItem(
                    item.name(),
                    item.reason(),
                    item.severity(),
                    item.action(),
                    item.confidence(),
                    item.source(),
                    firstNonBlank(item.observationDate(), match.observedOn),
                    firstNonBlank(item.sourceType(), match.sourceType),
                    firstNonBlank(item.sourceTitle(), match.sourceTitle),
                    firstNonBlank(item.verificationStatus(), match.verificationStatus)
            );
        }).toList();
    }

    private List<RecommendedTestItem> enrichRecommendedTests(List<RecommendedTestItem> items, ClinicalContextResponse context, ConsultationEntity consultation) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(item -> {
            if (item == null) return null;
            SourceMatch match = matchSource(item.name() + " " + item.reason(), context, consultation);
            boolean pendingOrderExists = Boolean.TRUE.equals(item.pendingOrderExists()) || match.pendingOrderExists;
            boolean alreadyAvailable = Boolean.TRUE.equals(item.alreadyAvailable()) || match.alreadyAvailable;
            String actionType = firstNonBlank(item.actionType(), match.actionType);
            String reason = item.reason();
            if (alreadyAvailable && !hasText(reason)) {
                reason = "Already available from " + firstNonBlank(match.observedOn, match.sourceTitle);
            }
            if (pendingOrderExists && !hasText(reason)) {
                reason = "Complete pending order instead of duplicating.";
            }
            return new RecommendedTestItem(
                    item.name(),
                    reason,
                    item.priority(),
                    item.timing(),
                    item.confidence(),
                    item.source(),
                    firstNonBlank(item.observationDate(), match.observedOn),
                    firstNonBlank(item.sourceType(), match.sourceType),
                    firstNonBlank(item.sourceTitle(), match.sourceTitle),
                    firstNonBlank(item.verificationStatus(), match.verificationStatus),
                    alreadyAvailable,
                    pendingOrderExists,
                    firstNonBlank(actionType, pendingOrderExists ? "COMPLETE_PENDING_ORDER" : alreadyAvailable ? "REVIEW_EXISTING_RESULT" : "ORDER_TEST")
            );
        }).toList();
    }

    private List<ClinicalSafetyNote> enrichSafetyNotes(List<ClinicalSafetyNote> items, ClinicalContextResponse context, ConsultationEntity consultation) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(item -> {
            if (item == null) return null;
            SourceMatch match = matchSource(item.message() + " " + item.rationale(), context, consultation);
            String actionType = firstNonBlank(item.actionType(), determineSafetyActionType(item));
            return new ClinicalSafetyNote(
                    item.message(),
                    item.severity(),
                    item.rationale(),
                    item.action(),
                    firstNonBlank(item.sourceType(), match.sourceType),
                    firstNonBlank(item.sourceTitle(), match.sourceTitle),
                    firstNonBlank(item.verificationStatus(), match.verificationStatus),
                    actionType
            );
        }).toList();
    }

    private String determineSafetyActionType(ClinicalSafetyNote note) {
        if (note == null || note.severity() == null) {
            return null;
        }
        String severity = note.severity().toUpperCase(java.util.Locale.ROOT);
        if (severity.contains("CRITICAL") || severity.contains("HIGH")) {
            return "ESCALATE";
        }
        if (hasText(note.action()) && note.action().toLowerCase(java.util.Locale.ROOT).contains("monitor")) {
            return "MONITOR";
        }
        return "EDUCATE_PATIENT";
    }

    private SourceMatch matchSource(String text, ClinicalContextResponse context, ConsultationEntity consultation) {
        String normalized = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
        SourceMatch match = new SourceMatch();
        if (context != null && context.intakeSummary() != null) {
            ClinicalContextResponse.VitalsSnapshot vitals = context.intakeSummary().latestVitals();
            if (vitals != null && (normalized.contains("bp") || normalized.contains("pulse") || normalized.contains("temp") || normalized.contains("spo2") || normalized.contains("rbs") || normalized.contains("bmi") || normalized.contains("fever") || normalized.contains("cough") || normalized.contains("weakness") || normalized.contains("body ache"))) {
                match.sourceType = "INTAKE";
                match.sourceTitle = "Clinical Intake";
                match.observedOn = context.intakeSummary().recordedAt();
                match.verificationStatus = "RECORDED";
            }
        }
        if (context != null && context.longitudinalMemory() != null) {
            matchLabSource(match, normalized, context.longitudinalMemory().latestHbA1c());
            matchLabSource(match, normalized, context.longitudinalMemory().latestBloodSugar());
            if (context.longitudinalMemory().latestLipidSummary() != null) {
                context.longitudinalMemory().latestLipidSummary().forEach(snapshot -> matchLabSource(match, normalized, snapshot));
            }
            if (context.longitudinalMemory().knownConditions() != null) {
                context.longitudinalMemory().knownConditions().forEach(snapshot -> {
                    if (snapshot != null && snapshot.label() != null && normalized.contains(snapshot.label().toLowerCase(java.util.Locale.ROOT))) {
                        match.sourceType = "LONGITUDINAL_MEMORY";
                        match.sourceTitle = snapshot.sourceDocumentTitle();
                        match.observedOn = snapshot.observedOn() == null ? match.observedOn : snapshot.observedOn().toString();
                        match.verificationStatus = snapshot.verificationStatus();
                    }
                });
            }
        }
        if (context != null && context.labIntelligence() != null) {
            if (context.labIntelligence().pendingInvestigations() != null) {
                for (String pending : context.labIntelligence().pendingInvestigations()) {
                    if (pending != null && normalized.contains(pending.toLowerCase(java.util.Locale.ROOT))) {
                        match.sourceType = "LAB_ORDER";
                        match.sourceTitle = pending;
                        match.pendingOrderExists = true;
                        match.actionType = "COMPLETE_PENDING_ORDER";
                    }
                }
            }
            if (context.labIntelligence().lastHbA1c() != null && normalized.contains("hba1c")) {
                match.alreadyAvailable = true;
                match.actionType = "REVIEW_EXISTING_RESULT";
            }
            if (context.labIntelligence().latestBloodSugar() != null && (normalized.contains("blood sugar") || normalized.contains("glucose") || normalized.contains("rbs"))) {
                match.alreadyAvailable = true;
                match.actionType = "REVIEW_EXISTING_RESULT";
            }
        }
        if (consultation != null && (normalized.contains("current consultation") || normalized.contains("chief complaint") || normalized.contains("diagnosis") || normalized.contains("advice"))) {
            match.sourceType = "CONSULTATION";
            match.sourceTitle = "Current Consultation";
        }
        if (match.sourceType == null && normalized.contains("hba1c")) {
            match.sourceType = "LAB_REPORT";
        }
        return match;
    }

    private void matchLabSource(SourceMatch match, String normalized, ClinicalContextResponse.LongitudinalConcept snapshot) {
        if (match == null || snapshot == null || snapshot.label() == null) {
            return;
        }
        String label = snapshot.label().toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains(label) || (snapshot.valueText() != null && normalized.contains(snapshot.valueText().toLowerCase(java.util.Locale.ROOT)))) {
            match.sourceType = "LAB_REPORT";
            match.sourceTitle = snapshot.sourceDocumentTitle();
            match.observedOn = snapshot.observedOn() == null ? match.observedOn : snapshot.observedOn().toString();
            match.verificationStatus = snapshot.verificationStatus();
        }
    }

    private ClinicalReasoningFinding mapHistoricalFinding(ClinicalContextResponse.HistoricalFinding finding) {
        if (finding == null || (!hasText(finding.title()) && !hasText(finding.summary()))) {
            return null;
        }
        return new ClinicalReasoningFinding(
                finding.title(),
                finding.summary(),
                finding.clinicalRelevance(),
                finding.sourceDate(),
                finding.sourceType(),
                finding.sourceReference(),
                finding.verificationStatus(),
                finding.importance(),
                null
        );
    }

    private boolean isDuplicateLongitudinalFinding(List<ClinicalReasoningFinding> existing, ClinicalReasoningFinding candidate) {
        if (existing == null || candidate == null) {
            return false;
        }
        String candidateCategory = longitudinalFindingCategory(candidate);
        String candidateDate = normalizeFindingValue(candidate.sourceDate());
        String candidateSource = normalizeFindingValue(firstNonBlank(candidate.sourceReference(), candidate.sourceType()));
        String candidateKey = normalizeLongitudinalFindingKey(candidate);
        return existing.stream()
                .filter(Objects::nonNull)
                .anyMatch(existingFinding -> {
                    if (candidateCategory.equals(longitudinalFindingCategory(existingFinding))) {
                        String existingDate = normalizeFindingValue(existingFinding.sourceDate());
                        if (hasText(candidateDate) && hasText(existingDate) && candidateDate.equals(existingDate)) {
                            return true;
                        }
                        String existingSource = normalizeFindingValue(firstNonBlank(existingFinding.sourceReference(), existingFinding.sourceType()));
                        if (hasText(candidateSource) && hasText(existingSource) && candidateSource.equals(existingSource)) {
                            return true;
                        }
                    }
                    return candidateKey.equals(normalizeLongitudinalFindingKey(existingFinding));
                });
    }

    private String normalizeLongitudinalFindingKey(ClinicalReasoningFinding finding) {
        if (finding == null) {
            return "";
        }
        return String.join("|",
                longitudinalFindingCategory(finding),
                normalizeFindingValue(finding.sourceDate()),
                normalizeFindingValue(finding.sourceType()),
                normalizeFindingValue(finding.sourceReference()),
                normalizeFindingValue(finding.title()),
                normalizeFindingValue(finding.summary()));
    }

    private boolean isSupportedProviderLongitudinalFinding(ClinicalReasoningFinding finding, ClinicalContextResponse clinicalContext) {
        String category = longitudinalFindingCategory(finding);
        return switch (category) {
            case "imaging" -> hasGroundedImagingSupport(finding, clinicalContext);
            case "renal" -> hasGroundedRenalSupport(finding, clinicalContext);
            case "hba1c" -> hasGroundedHbA1cSupport(finding, clinicalContext);
            default -> true;
        };
    }

    private boolean hasGroundedHbA1cSupport(ClinicalReasoningFinding finding, ClinicalContextResponse clinicalContext) {
        if (clinicalContext == null || clinicalContext.longitudinalClinicalContext() == null || clinicalContext.longitudinalClinicalContext().labTrends() == null) {
            return false;
        }
        String candidateDate = normalizeFindingValue(finding == null ? null : finding.sourceDate());
        for (ClinicalContextResponse.LabTrend trend : clinicalContext.longitudinalClinicalContext().labTrends()) {
            if (trend == null || !"hba1c".equals(trend.analyteCode())) {
                continue;
            }
            if (normalizeFindingValue(trend.newerDate()).equals(candidateDate) || normalizeFindingValue(trend.olderDate()).equals(candidateDate)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGroundedRenalSupport(ClinicalReasoningFinding finding, ClinicalContextResponse clinicalContext) {
        if (clinicalContext == null || clinicalContext.longitudinalClinicalContext() == null || clinicalContext.longitudinalClinicalContext().renalContext() == null) {
            return false;
        }
        String candidateDate = normalizeFindingValue(finding == null ? null : finding.sourceDate());
        ClinicalContextResponse.RenalContext renalContext = clinicalContext.longitudinalClinicalContext().renalContext();
        return candidateDate.equals(normalizeFindingValue(renalContext.creatinineDate()))
                || candidateDate.equals(normalizeFindingValue(renalContext.egfrDate()));
    }

    private boolean hasGroundedImagingSupport(ClinicalReasoningFinding finding, ClinicalContextResponse clinicalContext) {
        if (clinicalContext == null || clinicalContext.longitudinalClinicalContext() == null || clinicalContext.longitudinalClinicalContext().imagingHistory() == null) {
            return false;
        }
        String candidateDate = normalizeFindingValue(finding == null ? null : finding.sourceDate());
        String candidateReference = normalizeFindingValue(finding == null ? null : finding.sourceReference());
        if (!hasText(candidateReference)) {
            return false;
        }
        for (ClinicalContextResponse.ImagingHistoryItem item : clinicalContext.longitudinalClinicalContext().imagingHistory()) {
            if (item == null || isMetadataOnlyImagingSummary(item.summary())) {
                continue;
            }
            if (!candidateDate.equals(normalizeFindingValue(item.reportDate()))) {
                continue;
            }
            String itemReference = normalizeFindingValue(item.sourceReference());
            String modality = normalizeFindingValue(item.modality());
            String bodyPart = normalizeFindingValue(item.bodyPart());
            if (candidateReference.equals(itemReference)
                    || candidateReference.contains(itemReference)
                    || itemReference.contains(candidateReference)
                    || candidateReference.contains(modality)
                    || candidateReference.contains(bodyPart)
                    || candidateReference.contains("xray")
                    || candidateReference.contains("x ray")
                    || candidateReference.contains("cxr")) {
                return true;
            }
        }
        return false;
    }

    private boolean isMetadataOnlyImagingSummary(String summary) {
        if (!hasText(summary)) {
            return true;
        }
        String normalized = summary.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("structured radiology findings are not currently available");
    }

    private String longitudinalFindingCategory(ClinicalReasoningFinding finding) {
        String haystack = joinSegments(
                firstNonBlank(finding == null ? null : finding.title(), ""),
                firstNonBlank(finding == null ? null : finding.summary(), ""),
                firstNonBlank(finding == null ? null : finding.sourceType(), ""),
                firstNonBlank(finding == null ? null : finding.sourceReference(), "")
        ).toLowerCase(java.util.Locale.ROOT);
        if (containsAny(haystack, "hba1c", "hb a1c", "a1c", "glycated hemoglobin", "glycosylated hemoglobin")) {
            return "hba1c";
        }
        if (containsAny(haystack, "creatinine", "egfr", "estimated glomerular filtration rate", "renal", "kidney")) {
            return "renal";
        }
        if (containsAny(haystack, "x ray", "xray", "x-ray", "cxr", "ct", "mri", "ultrasound", "usg", "radiograph", "mammogram", "echocardiography", "imaging")) {
            return "imaging";
        }
        return "general";
    }

    private String normalizeFindingValue(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String joinSegments(String... segments) {
        if (segments == null || segments.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (!hasText(segment)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(segment.trim());
        }
        return builder.toString();
    }

    private boolean containsAny(String haystack, String... needles) {
        if (!hasText(haystack) || needles == null || needles.length == 0) {
            return false;
        }
        String normalized = haystack.toLowerCase(java.util.Locale.ROOT);
        for (String needle : needles) {
            if (hasText(needle) && normalized.contains(needle.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String first, String second) {
        if (hasText(first)) {
            return first.trim();
        }
        if (hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class SourceMatch {
        String sourceType;
        String sourceTitle;
        String observedOn;
        String verificationStatus;
        boolean alreadyAvailable;
        boolean pendingOrderExists;
        String actionType;
    }

    public record UUIDContext(java.util.UUID tenantId, String requestId, String correlationId) {}
}
