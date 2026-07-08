package com.deepthoughtnet.clinic.api.consultation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiSummaryResponse;
import com.deepthoughtnet.clinic.consultation.db.ConsultationAiSummaryEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationAiSummaryRepository;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultationAiSummaryServiceTest {

    @Test
    void getReturnsEmptyResponseWhenSummaryIsAbsent() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ConsultationAiSummaryRepository repository = mock(ConsultationAiSummaryRepository.class);
        ConsultationAiSummaryService service = new ConsultationAiSummaryService(consultationService, repository);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(consultationRecord(tenantId, consultationId)));
        when(repository.findByTenantIdAndConsultationId(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());

        ConsultationAiSummaryResponse response = service.get(tenantId, consultationId);

        assertThat(response.consultationId()).isEqualTo(consultationId.toString());
        assertThat(response.summary()).isNull();
        assertThat(response.provider()).isNull();
        assertThat(response.model()).isNull();
        assertThat(response.generatedAt()).isNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void getReturnsPersistedSummaryWhenPresent() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ConsultationAiSummaryRepository repository = mock(ConsultationAiSummaryRepository.class);
        ConsultationAiSummaryService service = new ConsultationAiSummaryService(consultationService, repository);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        ConsultationAiSummaryEntity entity = ConsultationAiSummaryEntity.create(tenantId, consultationId);
        entity.update("Reviewed summary", "GEMINI", "gemini-1.5-flash", OffsetDateTime.parse("2026-07-08T09:00:00Z"));
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(consultationRecord(tenantId, consultationId)));
        when(repository.findByTenantIdAndConsultationId(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(entity));

        ConsultationAiSummaryResponse response = service.get(tenantId, consultationId);

        assertThat(response.summary()).isEqualTo("Reviewed summary");
        assertThat(response.provider()).isEqualTo("GEMINI");
        assertThat(response.model()).isEqualTo("gemini-1.5-flash");
        assertThat(response.generatedAt()).isEqualTo(OffsetDateTime.parse("2026-07-08T09:00:00Z"));
    }

    @Test
    void getStillThrowsForInvalidConsultation() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ConsultationAiSummaryRepository repository = mock(ConsultationAiSummaryRepository.class);
        ConsultationAiSummaryService service = new ConsultationAiSummaryService(consultationService, repository);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.get(tenantId, consultationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consultation not found");
    }

    private ConsultationRecord consultationRecord(UUID tenantId, UUID consultationId) {
        return new ConsultationRecord(
                consultationId,
                tenantId,
                UUID.randomUUID(),
                "PAT-1",
                "Sample Patient",
                UUID.randomUUID(),
                "Doctor",
                UUID.randomUUID(),
                "Chief complaint",
                "Symptoms",
                "Diagnosis",
                "Notes",
                "Advice",
                LocalDate.of(2026, 7, 8),
                ConsultationStatus.DRAFT,
                120,
                80,
                72,
                36.8,
                null,
                70.0,
                172.0,
                98,
                18,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
