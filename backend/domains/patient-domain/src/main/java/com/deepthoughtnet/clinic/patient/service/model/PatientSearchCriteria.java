package com.deepthoughtnet.clinic.patient.service.model;

public record PatientSearchCriteria(
        String patientNumber,
        String mobile,
        String name,
        Boolean active
) {
}
