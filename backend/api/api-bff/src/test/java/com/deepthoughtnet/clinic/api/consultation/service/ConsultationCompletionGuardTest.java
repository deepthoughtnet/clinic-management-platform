package com.deepthoughtnet.clinic.api.consultation.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsultationCompletionGuardTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CONSULTATION_ID = UUID.randomUUID();

    private PrescriptionService prescriptionService;
    private ConsultationCompletionGuard guard;

    @BeforeEach
    void setUp() {
        prescriptionService = mock(PrescriptionService.class);
        guard = new ConsultationCompletionGuard(prescriptionService);
    }

    @Test
    void rejectsCompletionWhenPrescriptionIsMissing() {
        when(prescriptionService.findByConsultationId(TENANT_ID, CONSULTATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.ensurePrescriptionReady(TENANT_ID, CONSULTATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ConsultationCompletionGuard.PRESCRIPTION_INCOMPLETE_MESSAGE);
    }

    @Test
    void rejectsCompletionWhenLatestPrescriptionIsNotFinalized() {
        when(prescriptionService.findByConsultationId(TENANT_ID, CONSULTATION_ID))
                .thenReturn(Optional.of(prescription(PrescriptionStatus.PREVIEWED)));

        assertThatThrownBy(() -> guard.ensurePrescriptionReady(TENANT_ID, CONSULTATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ConsultationCompletionGuard.PRESCRIPTION_INCOMPLETE_MESSAGE);
    }

    @Test
    void allowsCompletionWhenLatestPrescriptionIsFinalized() {
        when(prescriptionService.findByConsultationId(TENANT_ID, CONSULTATION_ID))
                .thenReturn(Optional.of(prescription(PrescriptionStatus.FINALIZED)));

        assertThatCode(() -> guard.ensurePrescriptionReady(TENANT_ID, CONSULTATION_ID))
                .doesNotThrowAnyException();
    }

    private PrescriptionRecord prescription(PrescriptionStatus status) {
        return new PrescriptionRecord(
                UUID.randomUUID(),
                TENANT_ID,
                UUID.randomUUID(),
                "PAT-001",
                "Anita Patel",
                UUID.randomUUID(),
                "Doctor One",
                CONSULTATION_ID,
                UUID.randomUUID(),
                "RX-001",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                "Diagnosis",
                "Advice",
                null,
                status,
                status == PrescriptionStatus.FINALIZED ? OffsetDateTime.now() : null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(),
                List.of()
        );
    }
}
