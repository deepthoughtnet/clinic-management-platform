package com.deepthoughtnet.clinic.api.medicationsafety;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class MedicationSafetyGuardException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final UUID prescriptionId;
    private final String evaluationStatus;
    private final String requiredAction;
    private final List<String> findingIds;

    public MedicationSafetyGuardException(HttpStatus status,
                                          String code,
                                          String message,
                                          UUID prescriptionId,
                                          String evaluationStatus,
                                          String requiredAction,
                                          List<String> findingIds) {
        super(message);
        this.status = status;
        this.code = code;
        this.prescriptionId = prescriptionId;
        this.evaluationStatus = evaluationStatus;
        this.requiredAction = requiredAction;
        this.findingIds = findingIds == null ? List.of() : List.copyOf(findingIds);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public UUID getPrescriptionId() { return prescriptionId; }
    public String getEvaluationStatus() { return evaluationStatus; }
    public String getRequiredAction() { return requiredAction; }
    public List<String> getFindingIds() { return findingIds; }
}
