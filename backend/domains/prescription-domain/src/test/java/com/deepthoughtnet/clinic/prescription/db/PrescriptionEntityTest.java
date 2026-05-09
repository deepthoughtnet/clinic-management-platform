package com.deepthoughtnet.clinic.prescription.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrescriptionEntityTest {
    @Test
    void previewAndFinalizeTrackLifecycleMetadata() {
        UUID doctorId = UUID.randomUUID();
        PrescriptionEntity prescription = newPrescription();

        prescription.preview();
        prescription.finalizePrescription(doctorId);

        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.FINALIZED);
        assertThat(prescription.getFinalizedByDoctorUserId()).isEqualTo(doctorId);
        assertThat(prescription.getFinalizedAt()).isNotNull();
    }

    @Test
    void correctionVersionLinksToFinalizedParentWithoutMutatingParent() {
        PrescriptionEntity parent = newPrescription();
        parent.finalizePrescription(UUID.randomUUID());

        PrescriptionEntity correction = PrescriptionEntity.create(
                parent.getTenantId(),
                parent.getPatientId(),
                parent.getDoctorUserId(),
                parent.getConsultationId(),
                parent.getAppointmentId(),
                "RX-CORRECTION"
        );
        correction.makeCorrectionVersion(parent.getId(), 2, "Same-day dosage correction", "SAME_DAY_CORRECTION");

        assertThat(parent.getStatus()).isEqualTo(PrescriptionStatus.FINALIZED);
        assertThat(correction.getStatus()).isEqualTo(PrescriptionStatus.DRAFT);
        assertThat(correction.getParentPrescriptionId()).isEqualTo(parent.getId());
        assertThat(correction.getVersionNumber()).isEqualTo(2);
        assertThat(correction.getCorrectionReason()).isEqualTo("Same-day dosage correction");
        assertThat(correction.getFlowType()).isEqualTo("SAME_DAY_CORRECTION");
    }

    @Test
    void correctedAndSupersededMetadataIsTrackedOnTheSameRow() {
        PrescriptionEntity prescription = newPrescription();
        UUID replacementId = UUID.randomUUID();

        prescription.finalizePrescription(UUID.randomUUID());
        prescription.markCorrected();
        prescription.markSuperseded(replacementId);

        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.SUPERSEDED);
        assertThat(prescription.getSupersededByPrescriptionId()).isEqualTo(replacementId);
        assertThat(prescription.getCorrectedAt()).isNotNull();
        assertThat(prescription.getSupersededAt()).isNotNull();
    }

    private static PrescriptionEntity newPrescription() {
        return PrescriptionEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RX-" + UUID.randomUUID()
        );
    }
}
