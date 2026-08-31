package com.deepthoughtnet.clinic.api.consultation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiPrescriptionSuggestionRequest;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiPrescriptionSuggestionResponse;
import com.deepthoughtnet.clinic.api.consultation.service.ConsultationAiPrescriptionSuggestionService;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConsultationAiPrescriptionSuggestionControllerTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void getDelegatesToServiceWithTenantContext() {
        ConsultationAiPrescriptionSuggestionService service = mock(ConsultationAiPrescriptionSuggestionService.class);
        DoctorAssignmentSecurityService securityService = mock(DoctorAssignmentSecurityService.class);
        ConsultationAiPrescriptionSuggestionController controller = new ConsultationAiPrescriptionSuggestionController(service, securityService);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "doctor@example.com", Set.of("DOCTOR"), "DOCTOR", "corr-ai-controller"));

        ConsultationAiPrescriptionSuggestionResponse response = new ConsultationAiPrescriptionSuggestionResponse(
                "suggestion-1",
                consultationId.toString(),
                1,
                "CURRENT",
                "source-hash",
                "source-hash",
                false,
                "Summary",
                "Raw",
                false,
                "OPENAI",
                "gpt-4o",
                actorId.toString(),
                "Doctor Clinician",
                OffsetDateTime.parse("2026-08-30T08:00:00Z"),
                List.of(),
                OffsetDateTime.parse("2026-08-30T08:00:00Z"),
                OffsetDateTime.parse("2026-08-30T08:00:00Z")
        );
        when(service.get(eq(tenantId), eq(consultationId))).thenReturn(response);

        ConsultationAiPrescriptionSuggestionResponse actual = controller.get(consultationId);

        assertThat(actual).isEqualTo(response);
        verify(securityService).requireConsultationAccess(eq(tenantId), eq(consultationId));
        verify(service).get(eq(tenantId), eq(consultationId));
    }

    @Test
    void saveDelegatesToServiceWithTenantContext() {
        ConsultationAiPrescriptionSuggestionService service = mock(ConsultationAiPrescriptionSuggestionService.class);
        DoctorAssignmentSecurityService securityService = mock(DoctorAssignmentSecurityService.class);
        ConsultationAiPrescriptionSuggestionController controller = new ConsultationAiPrescriptionSuggestionController(service, securityService);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "doctor@example.com", Set.of("DOCTOR"), "DOCTOR", "corr-ai-controller"));

        ConsultationAiPrescriptionSuggestionRequest request = new ConsultationAiPrescriptionSuggestionRequest(
                "Summary",
                "Raw",
                false,
                "OPENAI",
                "gpt-4o",
                OffsetDateTime.parse("2026-08-30T08:00:00Z"),
                List.of()
        );
        ConsultationAiPrescriptionSuggestionResponse response = new ConsultationAiPrescriptionSuggestionResponse(
                "suggestion-2",
                consultationId.toString(),
                2,
                "CURRENT",
                "source-hash",
                "source-hash",
                false,
                "Summary",
                "Raw",
                false,
                "OPENAI",
                "gpt-4o",
                actorId.toString(),
                "Doctor Clinician",
                OffsetDateTime.parse("2026-08-30T08:00:00Z"),
                List.of(),
                OffsetDateTime.parse("2026-08-30T08:00:00Z"),
                OffsetDateTime.parse("2026-08-30T08:00:00Z")
        );
        when(service.save(eq(tenantId), eq(consultationId), eq(request))).thenReturn(response);

        ConsultationAiPrescriptionSuggestionResponse actual = controller.save(consultationId, request);

        assertThat(actual).isEqualTo(response);
        verify(securityService).requireConsultationAccess(eq(tenantId), eq(consultationId));
        verify(service).save(eq(tenantId), eq(consultationId), eq(request));
    }
}
