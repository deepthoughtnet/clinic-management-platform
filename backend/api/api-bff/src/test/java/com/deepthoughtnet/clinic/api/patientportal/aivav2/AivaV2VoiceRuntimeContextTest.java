package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.Daypart;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceAvailabilityData;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceScheduleWindow;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceReadOnlyAivaService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

class AivaV2VoiceRuntimeContextTest {
    @Test
    void singleMatchVoiceCallerRemainsIdentifiedButUnauthenticatedWithoutInstallingPortalContexts() {
        RequestContextHolder.clear();
        SecurityContextHolder.clearContext();
        var store = new AivaV2SessionStore(Clock.systemUTC());
        var adapter = new AivaV2VoiceConversationAdapter(store,
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        var runtime = new VoiceReadOnlyAivaService(mock(HealthcareAiReadOnlyToolGateway.class), adapter);
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        var call = new VoiceReadOnlyAivaService.Context("auth-boundary-call", UUID.randomUUID(), tenant,
                null, null, null, "hi-IN", "SINGLE_MATCH", "IDENTIFIED_PATIENT", patient);

        var result = runtime.continueTurn(call, "मेरा appointment book कर दीजिए");

        assertThat(result.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.policyDecision()).isEqualTo("DENIED");
        assertThat(result.toolExecutionStatus()).isEqualTo("BLOCKED");
        assertThat(result.runtimeDiagnostics().get("transactionAuthorization")).isNotNull();
        assertThat(store.find("VOICE|" + tenant + "|auth-boundary-call").orElseThrow().patientId()).isEqualTo(patient);
        assertThat(RequestContextHolder.get()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void exactDotVoiceServiceSequenceCarriesFilteredSingleProviderIntoTomorrowAvailability() {
        HealthcareAiReadOnlyToolGateway gateway = mock(HealthcareAiReadOnlyToolGateway.class);
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        var adapter = new AivaV2VoiceConversationAdapter(store,
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        VoiceReadOnlyAivaService runtime = new VoiceReadOnlyAivaService(gateway, adapter);
        UUID tenant = UUID.randomUUID();
        String providerCallId = "provider-call-exact-session-continuity";
        var call = new VoiceReadOnlyAivaService.Context(providerCallId, null, tenant,
                null, null, null, "hi-IN", "UNMATCHED", "GUEST_CALLER", null);
        when(gateway.execute(eq("FIND_DOCTOR"), any(), any())).thenReturn(
                new HealthcareAiReadOnlyToolGateway.ToolResult("FIND_DOCTOR", true, true,
                        List.of(pediatrician()), 1, 2, "sourceCount=2; filteredCount=1"));
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        VoiceAvailabilityData availability = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                AivaV2VoiceToolExecutor.QueryType.EFFECTIVE_DATE_AVAILABILITY, tomorrow, null,
                List.of(new VoiceScheduleWindow(tomorrow.getDayOfWeek(), LocalTime.of(9, 0),
                        LocalTime.NOON, null, null, 30)), List.of(), List.of(Daypart.MORNING),
                List.of(), false, 0);
        when(gateway.execute(eq("FIND_SLOTS"), any(), any())).thenReturn(
                new HealthcareAiReadOnlyToolGateway.ToolResult("FIND_SLOTS", true, true,
                        availability, 0, 0, "availability"));

        var first = runtime.continueTurn(call, "बच्चों का डॉक्टर अवेलेबल है।");
        var afterFirst = store.find("VOICE|" + tenant + "|" + providerCallId).orElseThrow();
        var second = runtime.continueTurn(call, "कल अवेलेबल हैं क्या?");

        assertThat(first.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(afterFirst.latestProviderResult().candidates()).hasSize(1);
        assertThat(afterFirst.latestProviderResult().resolvedProvider().displayName()).isEqualTo("Ravi Mehta");
        assertThat(afterFirst.activeVoiceDoctor()).isNotNull();
        assertThat(afterFirst.activeVoiceDoctor().displayName()).isEqualTo("Ravi Mehta");
        assertThat(second.tool()).isEqualTo("FIND_SLOTS");
        assertThat(second.response()).contains("सुबह");
        ArgumentCaptor<HealthcareAiReadOnlyToolGateway.ToolInput> input =
                ArgumentCaptor.forClass(HealthcareAiReadOnlyToolGateway.ToolInput.class);
        verify(gateway).execute(eq("FIND_SLOTS"), any(), input.capture());
        assertThat(input.getValue().doctorId()).isEqualTo("doctor-ravi");
        assertThat(input.getValue().date()).isEqualTo(tomorrow);
        var afterEmptyProviderProjection = store.find("VOICE|" + tenant + "|" + providerCallId).orElseThrow();
        assertThat(afterEmptyProviderProjection.activeVoiceDoctor().displayName()).isEqualTo("Ravi Mehta");
        assertThat(afterEmptyProviderProjection.latestAvailabilityResult()).isNotNull();
    }

    @Test
    void englishDiscoveryInHiInSessionGetsEnglishVoiceResponse() {
        HealthcareAiReadOnlyToolGateway gateway = mock(HealthcareAiReadOnlyToolGateway.class);
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        VoiceReadOnlyAivaService runtime = new VoiceReadOnlyAivaService(gateway, adapter);
        UUID tenant = UUID.randomUUID();
        var call = new VoiceReadOnlyAivaService.Context("english-child-specialist-call", null, tenant,
                null, null, null, "hi-IN", "UNMATCHED", "GUEST_CALLER", null);
        when(gateway.execute(eq("FIND_DOCTOR"), any(), any())).thenReturn(
                new HealthcareAiReadOnlyToolGateway.ToolResult("FIND_DOCTOR", true, true,
                        List.of(pediatrician()), 1, 2, "sourceCount=2; filteredCount=1"));

        var response = runtime.continueTurn(call, "Any child specialist available?");

        assertThat(response.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(response.toolInputSpeciality()).isEqualTo("Pediatrics");
        assertThat(response.response()).contains("Dr. Ravi Mehta", "pediatrician")
                .doesNotContain("डॉ.", "उपलब्ध", "बच्चों के डॉक्टर");
    }

    private PublicDoctorSummaryResponse pediatrician() {
        return new PublicDoctorSummaryResponse("doctor-ravi", null, null, "Ravi Mehta", null, null,
                "Pediatrics", null, null, List.of(), null, null, null, null, null,
                null, null, true, null, null, null, false);
    }
}
