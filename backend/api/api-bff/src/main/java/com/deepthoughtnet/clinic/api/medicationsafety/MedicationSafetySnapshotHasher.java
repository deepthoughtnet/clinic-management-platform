package com.deepthoughtnet.clinic.api.medicationsafety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
final class MedicationSafetySnapshotHasher {
    private static final Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(String::compareTo);

    String prescriptionHash(MedicationSafetyEvaluationRequest request, Integer versionNumber) {
        return sha256(String.join("|",
                "prescription",
                stringValue(request == null ? null : request.tenantId()),
                stringValue(request == null ? null : request.patientId()),
                stringValue(request == null ? null : request.consultationId()),
                stringValue(request == null ? null : request.prescriptionId()),
                stringValue(versionNumber),
                canonicalMedicationList(request == null ? List.of() : request.proposedMedications())
        ));
    }

    String patientContextHash(MedicationSafetyEvaluationRequest request) {
        return sha256(String.join("|",
                "patient-context",
                stringValue(request == null ? null : request.tenantId()),
                stringValue(request == null ? null : request.patientId()),
                canonicalMedicationList(request == null ? List.of() : request.currentMedications()),
                canonicalAllergy(request == null ? null : request.allergies()),
                canonicalStringList(request == null ? List.of() : request.activeConditions()),
                canonicalRenal(request == null ? null : request.renalContext()),
                canonicalHepatic(request == null ? null : request.hepaticContext()),
                stringValue(request == null ? null : request.ageYears()),
                stringValue(request == null ? null : request.gender()),
                stringValue(request == null ? null : request.pregnancyStatus()),
                canonicalMetadata(request == null ? null : request.sourceVerificationMetadata())
        ));
    }

    String evaluationHash(String prescriptionHash, String patientContextHash, String rulesVersion) {
        return sha256(String.join("|",
                "evaluation",
                stringValue(prescriptionHash),
                stringValue(patientContextHash),
                stringValue(rulesVersion)
        ));
    }

    String snapshotHash(MedicationSafetyEvaluationRequest request, Integer versionNumber, String rulesVersion) {
        String prescriptionHash = prescriptionHash(request, versionNumber);
        String patientContextHash = patientContextHash(request);
        return evaluationHash(prescriptionHash, patientContextHash, rulesVersion);
    }

    private String canonicalMedicationList(List<MedicationSafetyMedicationItem> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        List<String> values = items.stream()
                .filter(Objects::nonNull)
                .map(this::canonicalMedication)
                .sorted(STRING_COMPARATOR)
                .toList();
        return "[" + String.join(";", values) + "]";
    }

    private String canonicalMedication(MedicationSafetyMedicationItem item) {
        return String.join("|",
                stringValue(item.medicineId()),
                stringValue(item.medicineName()),
                stringValue(item.normalizedMedicineName()),
                canonicalStringList(item.activeIngredients()),
                stringValue(item.therapeuticClass()),
                stringValue(item.strength()),
                stringValue(item.strengthUnit()),
                stringValue(item.dose()),
                stringValue(item.doseUnit()),
                stringValue(item.frequency()),
                stringValue(item.duration()),
                stringValue(item.timing()),
                stringValue(item.indication()),
                Boolean.toString(item.prn()),
                stringValue(item.source()),
                stringValue(item.verificationStatus()),
                stringValue(item.confidence()),
                stringValue(item.sourceDocumentId()),
                stringValue(item.sourceDocumentTitle()),
                stringValue(item.sourceDocumentDate())
        );
    }

    private String canonicalAllergy(MedicationSafetyEvaluationRequest.AllergySnapshot allergy) {
        if (allergy == null) {
            return "null";
        }
        return String.join("|",
                stringValue(allergy.rawText()),
                canonicalStringList(allergy.terms()),
                Boolean.toString(allergy.unknown()),
                Boolean.toString(allergy.noKnownAllergy()),
                stringValue(allergy.verificationStatus())
        );
    }

    private String canonicalRenal(MedicationSafetyEvaluationRequest.RenalSnapshot renal) {
        if (renal == null) {
            return "null";
        }
        return String.join("|",
                stringValue(renal.creatinine()),
                stringValue(renal.creatinineDate()),
                stringValue(renal.egfr()),
                stringValue(renal.egfrDate()),
                stringValue(renal.verificationStatus()),
                stringValue(renal.stalenessDays()),
                canonicalStringList(renal.sourceDocumentIds())
        );
    }

    private String canonicalHepatic(MedicationSafetyEvaluationRequest.HepaticSnapshot hepatic) {
        if (hepatic == null) {
            return "null";
        }
        return String.join("|",
                stringValue(hepatic.alt()),
                stringValue(hepatic.altDate()),
                stringValue(hepatic.ast()),
                stringValue(hepatic.astDate()),
                stringValue(hepatic.bilirubin()),
                stringValue(hepatic.bilirubinDate()),
                stringValue(hepatic.verificationStatus()),
                stringValue(hepatic.stalenessDays()),
                canonicalStringList(hepatic.sourceDocumentIds())
        );
    }

    private String canonicalMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        Map<String, Object> ordered = new LinkedHashMap<>();
        metadata.keySet().stream().sorted().forEach(key -> ordered.put(key, metadata.get(key)));
        return ordered.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + stringValue(entry.getValue()))
                .collect(Collectors.joining("|", "{", "}"));
    }

    private String canonicalStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> ordered = values.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .sorted(STRING_COMPARATOR)
                .toList();
        return "[" + String.join(",", ordered) + "]";
    }

    private String stringValue(Object value) {
        return value == null ? "" : normalize(String.valueOf(value));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
