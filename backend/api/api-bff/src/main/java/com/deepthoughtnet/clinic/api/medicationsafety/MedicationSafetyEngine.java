package com.deepthoughtnet.clinic.api.medicationsafety;

import com.deepthoughtnet.clinic.api.medicationsafety.MedicationSafetyEvaluationRequest.AllergySnapshot;
import com.deepthoughtnet.clinic.api.medicationsafety.MedicationSafetyEvaluationRequest.HepaticSnapshot;
import com.deepthoughtnet.clinic.api.medicationsafety.MedicationSafetyEvaluationRequest.RenalSnapshot;
import com.deepthoughtnet.clinic.api.medicationsafety.MedicationSafetyEvaluationResult.SourceSnapshotMetadata;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MedicationSafetyEngine {
    private static final String RULES_VERSION = "med-safety-v1";
    private static final DateTimeFormatter HUMAN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH);
    private static final Set<String> RENEAL_SENSITIVE_INGREDIENTS = Set.of("metformin", "diclofenac", "ibuprofen", "naproxen", "ketorolac", "indomethacin", "aceclofenac");
    private static final Set<String> HEPATIC_SENSITIVE_INGREDIENTS = Set.of("paracetamol", "acetaminophen", "isoniazid", "methotrexate", "valproate", "simvastatin", "atorvastatin");
    private static final Set<String> NSAID_HINTS = Set.of("nsaid", "diclofenac", "ibuprofen", "naproxen", "ketorolac", "indomethacin", "aceclofenac", "etoricoxib");
    private static final Set<String> STEROID_HINTS = Set.of("predni", "dexa", "methylpred", "hydrocortisone", "betamethasone");
    private static final Set<String> SPECIFIC_THERAPEUTIC_CLASSES = Set.of(
            "nsaid",
            "ace inhibitor",
            "arb",
            "angiotensin receptor blocker",
            "benzodiazepine",
            "ssri",
            "statin",
            "ppi",
            "proton pump inhibitor",
            "sulfonylurea",
            "beta blocker",
            "calcium channel blocker",
            "biguanide",
            "insulin",
            "anticoagulant",
            "antiplatelet",
            "loop diuretic",
            "thiazide diuretic"
    );
    private static final Set<String> INTERACTION_UNAVAILABLE_HINTS = Set.of("comprehensive interaction checking is not available");

    String rulesVersion() {
        return RULES_VERSION;
    }

    public MedicationSafetyEvaluationResult evaluate(MedicationSafetyEvaluationRequest request) {
        List<MedicationSafetyMedicationItem> proposed = request == null ? List.of() : safeMedications(request.proposedMedications());
        List<MedicationSafetyMedicationItem> current = dedupeMedications(request == null ? List.of() : request.currentMedications());
        List<MedicationSafetyFinding> findings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        boolean exactDuplicateEvaluated = !proposed.isEmpty();
        boolean ingredientDuplicateEvaluated = hasAnyIngredientMetadata(proposed);
        boolean classDuplicateEvaluated = hasAnyClassMetadata(proposed);
        boolean allergyEvaluated = request != null && request.allergies() != null;
        boolean conditionRulesEvaluated = request != null;
        boolean renalEvaluated = request != null && request.renalContext() != null;
        boolean hepaticEvaluated = request != null && request.hepaticContext() != null;
        boolean doseEvaluated = !proposed.isEmpty();
        boolean interactionEvaluated = false;
        boolean currentMedicationOverlapEvaluated = !current.isEmpty();

        evaluateExactDuplicates(request, proposed, findings, warnings);
        evaluateIngredientDuplicates(request, proposed, findings, warnings);
        evaluateClassDuplicates(request, proposed, findings, warnings);
        evaluateAllergies(request, proposed, findings, warnings);
        evaluateConditionRules(request, proposed, findings, warnings);
        evaluateRenalRules(request, proposed, findings, warnings);
        evaluateHepaticRules(request, proposed, findings, warnings);
        evaluateDoseAndFrequency(request, proposed, findings, warnings);
        evaluateInteractionFoundation(request, proposed, findings, warnings);
        evaluateCurrentMedicationOverlap(request, proposed, current, findings, warnings);
        evaluateRenalContextTransparency(request, findings);

        MedicationSafetySeverity overallSeverity = overallSeverity(findings, warnings);
        String evaluationId = deterministicEvaluationId(request, proposed, current);
        return new MedicationSafetyEvaluationResult(
                evaluationId,
                OffsetDateTime.now(),
                request == null ? null : request.prescriptionId(),
                overallSeverity,
                findings,
                warnings.stream().distinct().toList(),
                new MedicationSafetyCoverage(
                        exactDuplicateEvaluated,
                        ingredientDuplicateEvaluated,
                        classDuplicateEvaluated,
                        allergyEvaluated,
                        conditionRulesEvaluated,
                        renalEvaluated,
                        hepaticEvaluated,
                        doseEvaluated,
                        interactionEvaluated,
                        currentMedicationOverlapEvaluated,
                        renalCoverageStatus(request, proposed)
                ),
                RULES_VERSION,
                new SourceSnapshotMetadata(
                        request == null ? null : request.tenantId(),
                        request == null ? null : request.patientId(),
                        request == null ? null : request.consultationId(),
                        request == null ? null : request.prescriptionId(),
                        request == null ? null : request.prescriptionStatus()
                )
        );
    }

    private void evaluateExactDuplicates(MedicationSafetyEvaluationRequest request,
                                         List<MedicationSafetyMedicationItem> proposed,
                                         List<MedicationSafetyFinding> findings,
                                         List<String> warnings) {
        if (proposed.size() < 2) {
            return;
        }
        int size = proposed.size();
        int[] parent = new int[size];
        for (int i = 0; i < size; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                if (isExactDuplicateMatch(proposed.get(i), proposed.get(j))) {
                    union(parent, i, j);
                }
            }
        }
        Map<Integer, List<MedicationSafetyMedicationItem>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            grouped.computeIfAbsent(find(parent, i), ignored -> new ArrayList<>()).add(proposed.get(i));
        }
        for (List<MedicationSafetyMedicationItem> duplicates : grouped.values()) {
            if (duplicates.size() < 2) {
                continue;
            }
            List<String> names = duplicates.stream().map(MedicationSafetyMedicationItem::medicineName).filter(this::hasText).distinct().toList();
            findings.add(finding(
                    request,
                    "MED_DUPLICATE_EXACT",
                    MedicationSafetyFindingCategory.DUPLICATE_MEDICATION,
                    MedicationSafetySeverity.WARNING,
                    "Duplicate medication",
                    "The same medicine appears more than once in the current prescription draft.",
                    "Repeated medicine entries should be reviewed before finalizing the prescription.",
                    duplicates,
                    names,
                    List.of("Same normalized medicine identity was seen more than once."),
                    sourceRefs(duplicates),
                    leastTrustedStatus(duplicates),
                    false,
                    false,
                    "Confirm whether this is intentional split dosing or remove the duplicate entry.",
                    List.of()
            ));
        }
    }

    private boolean isExactDuplicateMatch(MedicationSafetyMedicationItem left, MedicationSafetyMedicationItem right) {
        if (left == null || right == null) {
            return false;
        }
        String leftKey = exactIdentityKey(left);
        String rightKey = exactIdentityKey(right);
        return hasText(leftKey) && leftKey.equals(rightKey);
    }

    private void evaluateIngredientDuplicates(MedicationSafetyEvaluationRequest request,
                                              List<MedicationSafetyMedicationItem> proposed,
                                              List<MedicationSafetyFinding> findings,
                                              List<String> warnings) {
        Map<String, List<MedicationSafetyMedicationItem>> grouped = new LinkedHashMap<>();
        boolean missingIngredientMetadata = false;
        for (MedicationSafetyMedicationItem item : proposed) {
            List<String> ingredients = normalizeIngredients(item.activeIngredients());
            if (ingredients.isEmpty()) {
                missingIngredientMetadata = true;
                continue;
            }
            for (String ingredient : ingredients) {
                grouped.computeIfAbsent(ingredient, ignored -> new ArrayList<>()).add(item);
            }
        }
        if (missingIngredientMetadata) {
            warnings.add("Active ingredient metadata is unavailable for one or more medicines.");
        }
        for (Map.Entry<String, List<MedicationSafetyMedicationItem>> entry : grouped.entrySet()) {
            List<MedicationSafetyMedicationItem> items = distinctItems(entry.getValue());
            if (items.size() < 2) {
                continue;
            }
            String ingredientLabel = displayIngredientLabel(entry.getKey(), items);
            findings.add(finding(
                    request,
                    "MED_DUPLICATE_INGREDIENT",
                    MedicationSafetyFindingCategory.DUPLICATE_INGREDIENT,
                    MedicationSafetySeverity.WARNING,
                    "Duplicate active ingredient",
                    "Multiple products in the current prescription contain " + ingredientLabel + ".",
                    "Two products with the same ingredient can unintentionally duplicate therapy.",
                    items,
                    items.stream().map(MedicationSafetyMedicationItem::medicineName).filter(this::hasText).distinct().toList(),
                    List.of("Overlapping ingredient: " + ingredientLabel),
                    sourceRefs(items),
                    leastTrustedStatus(items),
                    false,
                    false,
                    "Review whether one of the products should be removed or replaced.",
                    List.of()
            ));
        }
    }

    private void evaluateClassDuplicates(MedicationSafetyEvaluationRequest request,
                                         List<MedicationSafetyMedicationItem> proposed,
                                         List<MedicationSafetyFinding> findings,
                                         List<String> warnings) {
        Map<String, List<MedicationSafetyMedicationItem>> grouped = new LinkedHashMap<>();
        boolean missingClassMetadata = false;
        for (MedicationSafetyMedicationItem item : proposed) {
            String classKey = specificTherapeuticClassKey(item.therapeuticClass());
            if (!hasText(classKey)) {
                if (hasText(item.therapeuticClass())) {
                    missingClassMetadata = true;
                }
                continue;
            }
            grouped.computeIfAbsent(classKey, ignored -> new ArrayList<>()).add(item);
        }
        if (missingClassMetadata) {
            warnings.add("Therapeutic class metadata is too broad or unavailable for one or more medicines.");
        }
        for (Map.Entry<String, List<MedicationSafetyMedicationItem>> entry : grouped.entrySet()) {
            List<MedicationSafetyMedicationItem> items = distinctItems(entry.getValue());
            if (items.size() < 2) {
                continue;
            }
            findings.add(finding(
                    request,
                    "MED_DUPLICATE_CLASS",
                    MedicationSafetyFindingCategory.DUPLICATE_CLASS,
                    MedicationSafetySeverity.WARNING,
                    "Duplicate therapeutic class",
                    "Multiple medicines from the same therapeutic class appear in the draft: " + entry.getKey() + ".",
                    "Class duplication may be appropriate in some cases, but it should be reviewed deliberately.",
                    items,
                    items.stream().map(MedicationSafetyMedicationItem::medicineName).filter(this::hasText).distinct().toList(),
                    List.of("Therapeutic class: " + entry.getKey()),
                    sourceRefs(items),
                    leastTrustedStatus(items),
                    false,
                    false,
                    "Confirm whether the class overlap is intentional.",
                    List.of()
            ));
        }
    }

    private void evaluateAllergies(MedicationSafetyEvaluationRequest request,
                                   List<MedicationSafetyMedicationItem> proposed,
                                   List<MedicationSafetyFinding> findings,
                                   List<String> warnings) {
        AllergySnapshot allergies = request == null ? null : request.allergies();
        if (allergies == null) {
            return;
        }
        if (allergies.unknown()) {
            warnings.add("Allergy status is not recorded.");
            findings.add(dataQualityFinding(request, "MED_ALLERGY_STATUS_UNKNOWN", "Allergy status is not recorded.", "No structured allergy entry is available for this patient.", "Record allergies or confirm no known allergy before relying on the allergy screen.", MedicationSafetySeverity.INFO));
            return;
        }
        if (allergies.noKnownAllergy()) {
            return;
        }
        List<String> allergyTerms = allergies.terms() == null ? List.of() : allergies.terms().stream().filter(this::hasText).distinct().toList();
        if (allergyTerms.isEmpty()) {
            warnings.add("Allergy text is present but could not be structured into discrete terms.");
            findings.add(dataQualityFinding(request, "MED_ALLERGY_STATUS_UNKNOWN", "Allergy status requires review.", "Allergy text is present but could not be structured into discrete terms.", "Review the patient allergy field.", MedicationSafetySeverity.INFO));
            return;
        }
        for (MedicationSafetyMedicationItem item : proposed) {
            List<String> candidateTerms = new ArrayList<>();
            candidateTerms.addAll(normalizeIngredients(item.activeIngredients()));
            if (candidateTerms.isEmpty()) {
                candidateTerms.add(normalizeTerm(item.medicineName()));
            }
            for (String allergyTerm : allergyTerms) {
                String normalizedAllergyTerm = normalizeTerm(allergyTerm);
                if (candidateTerms.stream().filter(this::hasText).anyMatch(candidate -> matchesAllergyTerm(candidate, normalizedAllergyTerm))) {
                    findings.add(finding(
                            request,
                            "MED_ALLERGY_EXACT",
                            MedicationSafetyFindingCategory.ALLERGY_CONFLICT,
                            allergySeverity(allergies.verificationStatus()),
                            "Recorded allergy conflict",
                            "Patient has a recorded allergy to " + allergyTerm.trim() + ". The proposed medication may contain the same ingredient or a matching medicine name.",
                            "Use the recorded allergy list as a hard safety screen and verify the product identity before prescribing.",
                            List.of(item),
                            List.of(item.medicineName()),
                            List.of("Allergy term: " + allergyTerm),
                            sourceRefs(List.of(item)),
                            parseVerification(allergies.verificationStatus(), item.verificationStatus()),
                            false,
                            false,
                            "Select an alternative only after verifying the allergy history and product ingredients.",
                            List.of(allergies.rawText())
                    ));
                }
            }
        }
    }

    private void evaluateConditionRules(MedicationSafetyEvaluationRequest request,
                                        List<MedicationSafetyMedicationItem> proposed,
                                        List<MedicationSafetyFinding> findings,
                                        List<String> warnings) {
        List<String> conditions = normalizeConditions(request == null ? List.of() : request.activeConditions());
        boolean hasDiabetes = conditions.stream().anyMatch(value -> value.contains("diabet"));
        boolean hasRenalDisease = conditions.stream().anyMatch(value -> value.contains("ckd") || value.contains("kidney") || value.contains("renal"));
        for (MedicationSafetyMedicationItem item : proposed) {
            String medicationKey = medicationKey(item);
            if (hasDiabetes && containsAny(medicationKey, STEROID_HINTS)) {
                findings.add(finding(
                        request,
                        "MED_CONDITION_STEROID_DIABETES",
                        MedicationSafetyFindingCategory.CONDITION_CONTRAINDICATION,
                        MedicationSafetySeverity.WARNING,
                        "Steroid caution in diabetes",
                        "The draft contains a steroid-like medicine in a patient with diabetes or a diabetes-related condition.",
                        "Steroids can worsen glycemic control and should be reviewed carefully when diabetes is present.",
                        List.of(item),
                        List.of(item.medicineName()),
                        List.of("Active condition: diabetes", "Medicine hint: " + medicationKey),
                        sourceRefs(List.of(item)),
                        item.verificationStatus(),
                        false,
                        false,
                        "Confirm the necessity, duration, and monitoring plan.",
                        List.of()
                ));
            }
            if (hasRenalDisease && containsAny(medicationKey, NSAID_HINTS)) {
                findings.add(finding(
                        request,
                        "MED_CONDITION_NSAID_CKD",
                        MedicationSafetyFindingCategory.CONDITION_CONTRAINDICATION,
                        MedicationSafetySeverity.WARNING,
                        "NSAID caution with kidney disease",
                        "The draft contains an NSAID-like medicine in a patient with kidney disease or renal impairment.",
                        "NSAIDs can worsen kidney function and should be reviewed carefully when renal disease is present.",
                        List.of(item),
                        List.of(item.medicineName()),
                        List.of("Active condition: kidney disease", "Medicine hint: " + medicationKey),
                        sourceRefs(List.of(item)),
                        item.verificationStatus(),
                        false,
                        false,
                        "Review renal risk and avoid NSAIDs if a safer option exists.",
                        List.of()
                ));
            }
        }
    }

    private void evaluateRenalRules(MedicationSafetyEvaluationRequest request,
                                    List<MedicationSafetyMedicationItem> proposed,
                                    List<MedicationSafetyFinding> findings,
                                    List<String> warnings) {
        RenalSnapshot renal = request == null ? null : request.renalContext();
        boolean renalSensitive = proposed.stream().anyMatch(item -> isRenalSensitive(item));
        if (!renalSensitive) {
            return;
        }
        if (renal == null || !hasText(renal.egfr()) && !hasText(renal.creatinine())) {
            warnings.add("Renal-sensitive medicine prescribed, but no structured renal context is available.");
            findings.add(dataQualityFinding(request, "MED_RENAL_DATA_MISSING", "Renal review is not fully evaluated.", "No current renal context is available for this renal-sensitive medicine.", "Review renal function before finalizing.", MedicationSafetySeverity.INFO));
            return;
        }
        Double egfr = parseDouble(renal.egfr());
        Integer stale = renal.stalenessDays();
        if (stale != null && stale > 180) {
            warnings.add("Renal result is historical.");
            findings.add(dataQualityFinding(request, "MED_RENAL_STALE", "Renal result is historical.", "The latest renal result is historical and may not reflect current status.", "Recheck renal function if clinically indicated.", MedicationSafetySeverity.INFO));
        }
        for (MedicationSafetyMedicationItem item : proposed) {
            if (!isRenalSensitive(item)) {
                continue;
            }
            if (egfr != null && egfr < 30d) {
                findings.add(finding(
                        request,
                        "MED_RENAL_EGFR_LOW",
                        MedicationSafetyFindingCategory.RENAL_CAUTION,
                        MedicationSafetySeverity.CRITICAL,
                        "Severe renal caution",
                        "Previous eGFR was " + renal.egfr() + (hasText(renal.egfrDate()) ? " on " + renal.egfrDate() : "") + ". This medicine needs urgent renal review.",
                        "A very low eGFR can substantially increase risk for renal-sensitive medicines.",
                        List.of(item),
                        List.of(item.medicineName()),
                        List.of("eGFR: " + renal.egfr()),
                        sourceRefs(List.of(item), renal.sourceDocumentIds()),
                        parseVerification(renal.verificationStatus(), item.verificationStatus()),
                        false,
                        false,
                        "Review dose or select a safer option based on current renal function.",
                        List.of()
                ));
            } else if (egfr != null && egfr < 60d) {
                findings.add(finding(
                        request,
                        "MED_RENAL_EGFR_REVIEW",
                        MedicationSafetyFindingCategory.RENAL_CAUTION,
                        MedicationSafetySeverity.WARNING,
                        "Renal caution",
                        "Previous eGFR was " + renal.egfr() + (hasText(renal.egfrDate()) ? " on " + renal.egfrDate() : "") + ". This medicine should be reviewed in the context of kidney function.",
                        "Renal-sensitive medicines may require review when eGFR is below 60 mL/min/1.73m2.",
                        List.of(item),
                        List.of(item.medicineName()),
                        List.of("eGFR: " + renal.egfr()),
                        sourceRefs(List.of(item), renal.sourceDocumentIds()),
                        parseVerification(renal.verificationStatus(), item.verificationStatus()),
                        false,
                        false,
                        "Review renal dosing or consider an alternative.",
                        List.of()
                ));
            }
        }
    }

    private void evaluateRenalContextTransparency(MedicationSafetyEvaluationRequest request,
                                                  List<MedicationSafetyFinding> findings) {
        RenalSnapshot renal = request == null ? null : request.renalContext();
        if (renal == null || (!hasText(renal.creatinine()) && !hasText(renal.egfr()))) {
            return;
        }
        String referenceDate = firstNonBlank(renal.creatinineDate(), renal.egfrDate());
        List<String> evidence = new ArrayList<>();
        if (hasText(renal.creatinine())) {
            evidence.add("Creatinine: " + renal.creatinine());
        }
        if (hasText(renal.egfr())) {
            evidence.add("eGFR: " + renal.egfr());
        }
        if (hasText(referenceDate)) {
            evidence.add("Observed on: " + formatHumanDate(referenceDate));
        }
        findings.add(finding(
                request,
                "MED_RENAL_HISTORY_AVAILABLE",
                MedicationSafetyFindingCategory.DATA_QUALITY,
                MedicationSafetySeverity.INFO,
                "Historical renal context available",
                buildHistoricalRenalSummary(renal, referenceDate),
                "Historical renal context should be used as background information and does not establish current renal status.",
                List.of(),
                List.of(),
                evidence,
                sourceRefs(List.of(), renal.sourceDocumentIds()),
                parseVerification(renal.verificationStatus()),
                false,
                false,
                "Use the historical renal result as context and recheck renal function if clinically indicated.",
                List.of("Historical renal data are not a substitute for current renal function.")
        ));
    }

    private void evaluateHepaticRules(MedicationSafetyEvaluationRequest request,
                                      List<MedicationSafetyMedicationItem> proposed,
                                      List<MedicationSafetyFinding> findings,
                                      List<String> warnings) {
        boolean hepaticSensitive = proposed.stream().anyMatch(item -> isHepaticSensitive(item));
        if (!hepaticSensitive) {
            return;
        }
        HepaticSnapshot hepatic = request == null ? null : request.hepaticContext();
        if (hepatic == null || !hasText(hepatic.alt()) && !hasText(hepatic.ast()) && !hasText(hepatic.bilirubin())) {
            warnings.add("Hepatic data unavailable.");
            findings.add(dataQualityFinding(request, "MED_HEPATIC_DATA_MISSING", "Hepatic review is not fully evaluated.", "No structured hepatic context is available for this medicine.", "Review liver function if clinically indicated.", MedicationSafetySeverity.INFO));
            return;
        }
        Integer stale = hepatic.stalenessDays();
        if (stale != null && stale > 180) {
            warnings.add("Hepatic result is historical.");
            findings.add(dataQualityFinding(request, "MED_HEPATIC_STALE", "Hepatic result is historical.", "The latest hepatic result is historical and may not reflect current status.", "Recheck liver function if clinically indicated.", MedicationSafetySeverity.INFO));
        }
    }

    private void evaluateDoseAndFrequency(MedicationSafetyEvaluationRequest request,
                                          List<MedicationSafetyMedicationItem> proposed,
                                          List<MedicationSafetyFinding> findings,
                                          List<String> warnings) {
        for (MedicationSafetyMedicationItem item : proposed) {
            if (!hasText(item.dose()) || !hasText(item.frequency())) {
                findings.add(dataQualityFinding(request, "MED_DOSE_NOT_NORMALIZED", "Dose or frequency could not be normalized.", "The draft medicine line is missing structured dose or frequency information.", "Complete structured dosing fields before relying on dose validation.", MedicationSafetySeverity.INFO, item));
                continue;
            }
            if (!looksLikeStructuredDose(item.dose())) {
                findings.add(dataQualityFinding(request, "MED_DOSE_NOT_NORMALIZED", "Dose or frequency could not be normalized.", "The dose field does not look like a normalized structured dose.", "Review the entered dose text.", MedicationSafetySeverity.INFO, item));
            }
            if (!looksLikeStructuredFrequency(item.frequency())) {
                findings.add(dataQualityFinding(request, "MED_FREQUENCY_NOT_NORMALIZED", "Frequency could not be normalized.", "The frequency field does not look like a normalized structured frequency.", "Review the entered frequency text.", MedicationSafetySeverity.INFO, item));
            }
        }
    }

    private void evaluateInteractionFoundation(MedicationSafetyEvaluationRequest request,
                                               List<MedicationSafetyMedicationItem> proposed,
                                               List<MedicationSafetyFinding> findings,
                                               List<String> warnings) {
        if (proposed.size() > 1) {
            warnings.add("Interaction checking is unavailable because no trusted interaction reference is configured.");
        }
    }

    private void evaluateCurrentMedicationOverlap(MedicationSafetyEvaluationRequest request,
                                                  List<MedicationSafetyMedicationItem> proposed,
                                                  List<MedicationSafetyMedicationItem> current,
                                                  List<MedicationSafetyFinding> findings,
                                                  List<String> warnings) {
        if (current.isEmpty()) {
            warnings.add("Current medication list is incomplete.");
            findings.add(dataQualityFinding(request, "MED_CURRENT_MED_LIST_INCOMPLETE", "Current medication list is incomplete.", "The structured current medication list is missing or empty.", "Review the current medication history before finalizing.", MedicationSafetySeverity.INFO));
            return;
        }
        Map<String, MedicationSafetyMedicationItem> currentByKey = current.stream()
                .filter(item -> hasText(item.normalizedMedicineName()) || hasText(item.medicineName()))
                .collect(Collectors.toMap(item -> firstNonBlank(item.normalizedMedicineName(), normalizeMedicationName(item.medicineName())), item -> item, (left, right) -> left, LinkedHashMap::new));
        for (MedicationSafetyMedicationItem item : proposed) {
            String key = firstNonBlank(item.normalizedMedicineName(), normalizeMedicationName(item.medicineName()));
            MedicationSafetyMedicationItem overlap = currentByKey.get(key);
            if (overlap != null) {
                findings.add(finding(
                        request,
                        "MED_CURRENT_MEDICATION_OVERLAP",
                        MedicationSafetyFindingCategory.CURRENT_MEDICATION_OVERLAP,
                        MedicationSafetySeverity.INFO,
                        "Current medication overlap",
                        "The proposed medicine matches a medication already present in the current medication list.",
                        "This may represent intended continuation rather than accidental duplication.",
                        List.of(item, overlap),
                        List.of(item.medicineName(), overlap.medicineName()),
                        List.of("Current medication match: " + key),
                        sourceRefs(List.of(item, overlap)),
                        leastTrustedStatus(List.of(item, overlap)),
                        false,
                        false,
                        "Confirm whether this is a continuation or a duplicate entry.",
                        List.of()
                ));
            }
        }
    }

    private MedicationSafetySeverity overallSeverity(List<MedicationSafetyFinding> findings, List<String> warnings) {
        if (findings.stream().anyMatch(finding -> finding.severity() == MedicationSafetySeverity.CRITICAL)) {
            return MedicationSafetySeverity.CRITICAL;
        }
        if (findings.stream().anyMatch(finding -> finding.severity() == MedicationSafetySeverity.WARNING)) {
            return MedicationSafetySeverity.WARNING;
        }
        if (findings.stream().anyMatch(finding -> finding.severity() == MedicationSafetySeverity.INFO)) {
            return MedicationSafetySeverity.INFO;
        }
        if (warnings != null && !warnings.isEmpty()) {
            return MedicationSafetySeverity.NOT_EVALUATED;
        }
        return MedicationSafetySeverity.NONE;
    }

    private MedicationSafetyFinding dataQualityFinding(MedicationSafetyEvaluationRequest request,
                                                       String ruleCode,
                                                       String title,
                                                       String summary,
                                                       String suggestedAction,
                                                       MedicationSafetySeverity severity,
                                                       MedicationSafetyMedicationItem... items) {
        List<MedicationSafetyMedicationItem> list = items == null ? List.of() : Arrays.stream(items).filter(Objects::nonNull).toList();
        return finding(
                request,
                ruleCode,
                MedicationSafetyFindingCategory.DATA_QUALITY,
                severity,
                title,
                summary,
                summary,
                list,
                list.stream().map(MedicationSafetyMedicationItem::medicineName).filter(this::hasText).distinct().toList(),
                List.of(summary),
                sourceRefs(list),
                leastTrustedStatus(list),
                false,
                false,
                suggestedAction,
                List.of(summary)
        );
    }

    private MedicationSafetyFinding finding(MedicationSafetyEvaluationRequest request,
                                            String ruleCode,
                                            MedicationSafetyFindingCategory category,
                                            MedicationSafetySeverity severity,
                                            String title,
                                            String summary,
                                            String rationale,
                                            List<MedicationSafetyMedicationItem> items,
                                            List<String> medicineNames,
                                            List<String> evidence,
                                            List<String> sourceReferences,
                                            String verificationStatus,
                                            boolean acknowledgementRequired,
                                            boolean overrideAllowed,
                                            String suggestedAction,
                                            List<String> dataQualityNotes) {
        List<String> itemIds = items == null ? List.of() : items.stream().map(MedicationSafetyMedicationItem::prescriptionItemId).filter(this::hasText).distinct().toList();
        List<String> names = medicineNames == null ? List.of() : medicineNames.stream().filter(this::hasText).distinct().toList();
        List<String> sources = sourceReferences == null ? List.of() : sourceReferences.stream().filter(this::hasText).distinct().toList();
        return new MedicationSafetyFinding(
                deterministicFindingId(request, ruleCode, category, severity, itemIds, names, sources),
                ruleCode,
                category,
                severity,
                title,
                summary,
                rationale,
                itemIds,
                names,
                evidence == null ? List.of() : evidence.stream().filter(this::hasText).distinct().toList(),
                sources,
                verificationStatus,
                acknowledgementRequired,
                overrideAllowed,
                suggestedAction,
                dataQualityNotes == null ? List.of() : dataQualityNotes.stream().filter(this::hasText).distinct().toList()
        );
    }

    private String deterministicFindingId(MedicationSafetyEvaluationRequest request,
                                          String ruleCode,
                                          MedicationSafetyFindingCategory category,
                                          MedicationSafetySeverity severity,
                                          List<String> itemIds,
                                          List<String> names,
                                          List<String> sources) {
        String seed = String.join("|",
                RULES_VERSION,
                firstNonBlank(request == null ? null : String.valueOf(request.tenantId()), ""),
                firstNonBlank(request == null ? null : String.valueOf(request.prescriptionId()), ""),
                ruleCode,
                category.name(),
                severity.name(),
                itemIds == null ? "" : String.join(",", itemIds),
                names == null ? "" : String.join(",", names),
                sources == null ? "" : String.join(",", sources));
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private List<String> sourceRefs(Collection<MedicationSafetyMedicationItem> items) {
        return sourceRefs(items, List.of());
    }

    private List<String> sourceRefs(Collection<MedicationSafetyMedicationItem> items, Collection<String> extra) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        if (items != null) {
            for (MedicationSafetyMedicationItem item : items) {
                if (item == null) {
                    continue;
                }
                if (hasText(item.sourceDocumentTitle())) {
                    refs.add(item.sourceDocumentTitle());
                }
                if (hasText(item.sourceDocumentId())) {
                    refs.add(item.sourceDocumentId());
                }
                if (hasText(item.source())) {
                    refs.add(item.source());
                }
                if (hasText(item.medicineName())) {
                    refs.add(item.medicineName());
                }
            }
        }
        if (extra != null) {
            extra.stream().filter(this::hasText).forEach(refs::add);
        }
        return new ArrayList<>(refs);
    }

    private String leastTrustedStatus(Collection<MedicationSafetyMedicationItem> items) {
        if (items == null || items.isEmpty()) {
            return "UNKNOWN";
        }
        boolean hasUnknown = false;
        boolean hasPending = false;
        boolean hasVerified = false;
        for (MedicationSafetyMedicationItem item : items) {
            String status = normalizeStatus(item == null ? null : item.verificationStatus());
            if ("VERIFIED".equals(status)) {
                hasVerified = true;
            } else if ("PENDING_VERIFICATION".equals(status)) {
                hasPending = true;
            } else {
                hasUnknown = true;
            }
        }
        if (hasUnknown) {
            return "UNKNOWN";
        }
        if (hasPending) {
            return "PENDING_VERIFICATION";
        }
        return hasVerified ? "VERIFIED" : "UNKNOWN";
    }

    private MedicationSafetySeverity allergySeverity(String allergyVerificationStatus) {
        String normalized = normalizeStatus(allergyVerificationStatus);
        return "VERIFIED".equals(normalized) ? MedicationSafetySeverity.CRITICAL : MedicationSafetySeverity.WARNING;
    }

    private String parseVerification(String... statuses) {
        if (statuses == null || statuses.length == 0) {
            return "UNKNOWN";
        }
        boolean hasPending = false;
        boolean hasVerified = false;
        for (String status : statuses) {
            String normalized = normalizeStatus(status);
            if ("VERIFIED".equals(normalized)) {
                hasVerified = true;
            } else if ("PENDING_VERIFICATION".equals(normalized)) {
                hasPending = true;
            } else if (StringUtils.hasText(normalized)) {
                hasPending = true;
            }
        }
        if (hasPending) {
            return "PENDING_VERIFICATION";
        }
        return hasVerified ? "VERIFIED" : "UNKNOWN";
    }

    private String normalizeStatus(String value) {
        if (!hasText(value)) {
            return "UNKNOWN";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "VERIFIED", "APPROVED", "ACCEPTED" -> "VERIFIED";
            case "PENDING_REVIEW", "PENDING_VERIFICATION", "REVIEW_REQUIRED", "AI_REVIEW_REQUIRED", "UNVERIFIED" -> "PENDING_VERIFICATION";
            case "REJECTED" -> "REJECTED";
            default -> "UNKNOWN";
        };
    }

    private List<MedicationSafetyMedicationItem> dedupeMedications(List<MedicationSafetyMedicationItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, MedicationSafetyMedicationItem> deduped = new LinkedHashMap<>();
        for (MedicationSafetyMedicationItem item : items) {
            if (item == null) {
                continue;
            }
            String key = exactIdentityKey(item);
            if (!hasText(key)) {
                key = firstNonBlank(item.prescriptionItemId(), item.medicineName(), UUID.randomUUID().toString());
            }
            deduped.putIfAbsent(key, item);
        }
        return new ArrayList<>(deduped.values());
    }

    private List<MedicationSafetyMedicationItem> safeMedications(List<MedicationSafetyMedicationItem> items) {
        return items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
    }

    private List<MedicationSafetyMedicationItem> distinctItems(Collection<MedicationSafetyMedicationItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, MedicationSafetyMedicationItem> deduped = new LinkedHashMap<>();
        for (MedicationSafetyMedicationItem item : items) {
            deduped.putIfAbsent(exactIdentityKey(item), item);
        }
        return new ArrayList<>(deduped.values());
    }

    private boolean hasAnyIngredientMetadata(List<MedicationSafetyMedicationItem> items) {
        return items.stream().anyMatch(item -> item.activeIngredients() != null && item.activeIngredients().stream().anyMatch(this::hasText));
    }

    private boolean hasAnyClassMetadata(List<MedicationSafetyMedicationItem> items) {
        return items.stream().anyMatch(item -> hasText(specificTherapeuticClassKey(item.therapeuticClass())));
    }

    private String exactIdentityKey(MedicationSafetyMedicationItem item) {
        if (item == null) {
            return null;
        }
        if (hasText(item.medicineId())) {
            return "id:" + item.medicineId().trim();
        }
        return "sig:" + String.join("|",
                normalizeProductName(item.medicineName()),
                normalizeExactValue(item.strength()),
                normalizeExactValue(item.strengthUnit()),
                normalizeExactValue(item.dose()),
                normalizeExactValue(item.doseUnit()),
                normalizeExactValue(item.frequency()),
                normalizeExactValue(item.duration()),
                normalizeExactValue(item.timing()));
    }

    private int find(int[] parent, int index) {
        if (parent[index] != index) {
            parent[index] = find(parent, parent[index]);
        }
        return parent[index];
    }

    private void union(int[] parent, int left, int right) {
        int leftRoot = find(parent, left);
        int rightRoot = find(parent, right);
        if (leftRoot != rightRoot) {
            parent[rightRoot] = leftRoot;
        }
    }

    private boolean isRenalSensitive(MedicationSafetyMedicationItem item) {
        String key = medicationKey(item);
        return containsAny(key, RENEAL_SENSITIVE_INGREDIENTS) || containsAny(specificTherapeuticClassKey(item.therapeuticClass()), Set.of("renal"));
    }

    private boolean isHepaticSensitive(MedicationSafetyMedicationItem item) {
        String key = medicationKey(item);
        return containsAny(key, HEPATIC_SENSITIVE_INGREDIENTS) || containsAny(specificTherapeuticClassKey(item.therapeuticClass()), Set.of("hepatic", "liver"));
    }

    private String medicationKey(MedicationSafetyMedicationItem item) {
        if (item == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add(normalizeMedicationName(item.medicineName()));
        if (item.activeIngredients() != null) {
            parts.addAll(normalizeIngredients(item.activeIngredients()));
        }
        parts.add(normalizeMedicationClass(item.therapeuticClass()));
        return parts.stream().filter(this::hasText).collect(Collectors.joining(" "));
    }

    private List<String> normalizeIngredients(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        return ingredients.stream().filter(this::hasText).map(this::normalizeTerm).filter(this::hasText).distinct().toList();
    }

    private List<String> normalizeConditions(List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        return conditions.stream().filter(this::hasText).map(this::normalizeTerm).filter(this::hasText).distinct().toList();
    }

    private String normalizeMedicationClass(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String specificTherapeuticClassKey(String value) {
        String normalized = normalizeMedicationClass(value);
        if (!hasText(normalized)) {
            return "";
        }
        if (SPECIFIC_THERAPEUTIC_CLASSES.contains(normalized)) {
            return normalized;
        }
        if (normalized.contains("nsaid")) {
            return "nsaid";
        }
        if (normalized.contains("ace inhibitor")) {
            return "ace inhibitor";
        }
        if (normalized.contains("angiotensin receptor blocker") || normalized.equals("arb")) {
            return "arb";
        }
        if (normalized.contains("benzodiazepine")) {
            return "benzodiazepine";
        }
        if (normalized.contains("selective serotonin reuptake inhibitor") || normalized.equals("ssri")) {
            return "ssri";
        }
        if (normalized.contains("proton pump inhibitor") || normalized.equals("ppi")) {
            return "ppi";
        }
        if (normalized.contains("sulfonylurea")) {
            return "sulfonylurea";
        }
        if (normalized.contains("calcium channel blocker")) {
            return "calcium channel blocker";
        }
        if (normalized.contains("beta blocker")) {
            return "beta blocker";
        }
        if (normalized.contains("biguanide")) {
            return "biguanide";
        }
        if (normalized.contains("statin")) {
            return "statin";
        }
        if (normalized.contains("anticoagulant")) {
            return "anticoagulant";
        }
        if (normalized.contains("antiplatelet")) {
            return "antiplatelet";
        }
        if (normalized.contains("loop diuretic")) {
            return "loop diuretic";
        }
        if (normalized.contains("thiazide diuretic")) {
            return "thiazide diuretic";
        }
        if (normalized.contains("insulin")) {
            return "insulin";
        }
        return "";
    }

    private String normalizeTerm(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\s*(mg|mcg|ml|g|iu|units|tablet|tab|capsule|cap|syrup|suspension|drop|drops|ointment|cream|injection|inj|dose|daily|od|bd|bid|tid|qid)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeMedicationName(String value) {
        if (!hasText(value)) {
            return "";
        }
        return normalizeTerm(value);
    }

    private String normalizeProductName(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeExactValue(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean matchesAllergyTerm(String candidate, String allergyTerm) {
        String normalizedCandidate = normalizeTerm(candidate);
        String normalizedAllergy = normalizeTerm(allergyTerm);
        if (!hasText(normalizedCandidate) || !hasText(normalizedAllergy)) {
            return false;
        }
        if (normalizedCandidate.equals(normalizedAllergy)) {
            return true;
        }
        return containsWholeWord(normalizedCandidate, normalizedAllergy) || containsWholeWord(normalizedAllergy, normalizedCandidate);
    }

    private boolean containsWholeWord(String text, String term) {
        if (!hasText(text) || !hasText(term)) {
            return false;
        }
        return (" " + text + " ").contains(" " + term + " ");
    }

    private String displayIngredientLabel(String normalizedIngredient, List<MedicationSafetyMedicationItem> items) {
        if (items == null || items.isEmpty()) {
            return normalizedIngredient;
        }
        for (MedicationSafetyMedicationItem item : items) {
            if (item == null || item.activeIngredients() == null) {
                continue;
            }
            for (String ingredient : item.activeIngredients()) {
                if (hasText(ingredient) && normalizeTerm(ingredient).equals(normalizedIngredient)) {
                    return ingredient.trim();
                }
            }
        }
        return normalizedIngredient;
    }

    private boolean looksLikeStructuredDose(String value) {
        return hasText(value) && value.trim().matches("(?i).*\\d.*");
    }

    private boolean looksLikeStructuredFrequency(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.matches(".*\\b(od|bd|bid|tid|qid|daily|once|twice|thrice|nightly|morning|afternoon|evening|weekly|weekly|hourly|every)\\b.*") || normalized.matches(".*\\d.*");
    }

    private String buildHistoricalRenalSummary(RenalSnapshot renal, String referenceDate) {
        List<String> parts = new ArrayList<>();
        if (renal != null) {
            if (hasText(renal.creatinine())) {
                parts.add("Previous creatinine was " + renal.creatinine());
            }
            if (hasText(renal.egfr())) {
                parts.add("eGFR was " + renal.egfr());
            }
        }
        String summary = String.join(" and ", parts);
        if (hasText(referenceDate)) {
            summary = summary + " on " + formatHumanDate(referenceDate);
        }
        return summary + ".";
    }

    private String formatHumanDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value).format(HUMAN_DATE_FORMAT);
        } catch (RuntimeException ex) {
            return value;
        }
    }

    private Double parseDouble(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(value.trim());
            if (!matcher.find()) {
                return null;
            }
            return Double.parseDouble(matcher.group());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean containsAny(String text, Collection<String> values) {
        if (!hasText(text) || values == null || values.isEmpty()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (hasText(value) && normalized.contains(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, Set<String> values) {
        return containsAny(text, (Collection<String>) values);
    }

    private String deterministicEvaluationId(MedicationSafetyEvaluationRequest request,
                                             List<MedicationSafetyMedicationItem> proposed,
                                             List<MedicationSafetyMedicationItem> current) {
        String seed = String.join("|",
                RULES_VERSION,
                String.valueOf(request == null ? null : request.tenantId()),
                String.valueOf(request == null ? null : request.patientId()),
                String.valueOf(request == null ? null : request.consultationId()),
                String.valueOf(request == null ? null : request.prescriptionId()),
                String.valueOf(proposed == null ? 0 : proposed.size()),
                String.valueOf(current == null ? 0 : current.size()));
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String renalCoverageStatus(MedicationSafetyEvaluationRequest request, List<MedicationSafetyMedicationItem> proposed) {
        RenalSnapshot renal = request == null ? null : request.renalContext();
        if (renal == null || (!hasText(renal.creatinine()) && !hasText(renal.egfr()))) {
            return "UNAVAILABLE";
        }
        boolean renalSensitive = proposed != null && proposed.stream().anyMatch(this::isRenalSensitive);
        if (!renalSensitive) {
            return "PARTIAL";
        }
        Double egfr = parseDouble(renal.egfr());
        if (egfr != null) {
            return "EVALUATED";
        }
        return hasText(renal.creatinine()) ? "PARTIAL" : "UNAVAILABLE";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
