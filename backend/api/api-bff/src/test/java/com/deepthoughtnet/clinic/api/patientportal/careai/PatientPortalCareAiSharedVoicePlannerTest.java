package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiSharedVoicePlannerTest {
    @Test
    void voiceUsesSharedPlannerAndKeepsConversationStateWithoutPortalRequestContext() {
        PatientPortalCareAiPlanner planner = mock(PatientPortalCareAiPlanner.class);
        PatientPortalCareAiSharedVoicePlanner runtime = new PatientPortalCareAiSharedVoicePlanner(
                planner, new PatientPortalCareAiTurnInterpreter(new PatientPortalCareAiEntityExtractor()));
        UUID tenantId = UUID.randomUUID();
        AivaConversationContext conversation = new AivaConversationContext(
                "call-1", "VOICE", tenantId, "hi-IN", "trace-1", java.util.Map.of());
        HealthcareAiExecutionContext healthcare = new HealthcareAiExecutionContext(
                tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE, null, null, null, "hi-IN");

        var clarification = runtime.plan(conversation, healthcare, "मुझे डॉक्टर चाहिए");
        var doctor = runtime.plan(conversation, healthcare, "कार्डियोलॉजिस्ट");

        assertEquals("FIND_DOCTOR", clarification.intent());
        assertTrue(clarification.clarification() != null || "FIND_DOCTOR".equals(clarification.tool()));
        assertEquals("FIND_DOCTOR", doctor.tool());
        verify(planner, org.mockito.Mockito.times(2)).plan(any(PatientPortalCareAiPlanningContext.class), any(AivaConversationContext.class));
    }

    @Test
    void mutationPlannerDecisionIsConvertedToReadOnlyPlan() {
        PatientPortalCareAiPlanner planner = mock(PatientPortalCareAiPlanner.class);
        org.mockito.Mockito.when(planner.plan(any(), any())).thenReturn(new PatientPortalCareAiPlannerDecision(
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, null, null, null, null,
                PatientPortalCareAiPlannerConfirmationDecision.NONE, "", false, null));
        PatientPortalCareAiSharedVoicePlanner runtime = new PatientPortalCareAiSharedVoicePlanner(
                planner, new PatientPortalCareAiTurnInterpreter(new PatientPortalCareAiEntityExtractor()));
        UUID tenantId = UUID.randomUUID();
        var plan = runtime.plan(
                new AivaConversationContext("call-2", "VOICE", tenantId, "hi-IN", "trace-2", java.util.Map.of()),
                new HealthcareAiExecutionContext(tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                        null, null, null, "hi-IN"),
                "मेरी appointment book कर दो");

        assertTrue(plan.mutation());
        assertEquals("BOOK_APPOINTMENT", plan.intent());
    }

    @Test
    void genericDoctorDiscoveryDoesNotReusePreviousSpecialty() {
        PatientPortalCareAiPlanner planner = mock(PatientPortalCareAiPlanner.class);
        PatientPortalCareAiSharedVoicePlanner runtime = new PatientPortalCareAiSharedVoicePlanner(
                planner, new PatientPortalCareAiTurnInterpreter(new PatientPortalCareAiEntityExtractor()));
        UUID tenantId = UUID.randomUUID();
        var conversation = new AivaConversationContext("call-generic", "VOICE", tenantId, "hi-IN", "trace", java.util.Map.of());
        var context = new HealthcareAiExecutionContext(tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                null, null, null, "hi-IN");

        runtime.plan(conversation, context, "मुझे बच्चों के डॉक्टर चाहिए");
        var generic = runtime.plan(conversation, context, "कौन से डॉक्टर अवेलेबल हैं?");

        assertEquals("FIND_DOCTOR", generic.intent());
        assertEquals("FIND_DOCTOR", generic.tool());
        assertEquals(null, generic.speciality());
    }

    @Test
    void thanksEndsVoiceConversationWithoutInvokingHealthcarePlanning() {
        PatientPortalCareAiPlanner planner = mock(PatientPortalCareAiPlanner.class);
        PatientPortalCareAiSharedVoicePlanner runtime = new PatientPortalCareAiSharedVoicePlanner(
                planner, new PatientPortalCareAiTurnInterpreter(new PatientPortalCareAiEntityExtractor()));
        UUID tenantId = UUID.randomUUID();

        var plan = runtime.plan(
                new AivaConversationContext("call-close", "VOICE", tenantId, "hi-IN", "trace", java.util.Map.of()),
                new HealthcareAiExecutionContext(tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                        null, null, null, "hi-IN"), "धन्यवाद");

        assertTrue(plan.closing());
        assertEquals("END_CONVERSATION", plan.intent());
        assertEquals("धन्यवाद। स्वस्थ रहिए।", plan.clarification());
        verify(planner, org.mockito.Mockito.never()).plan(any(PatientPortalCareAiPlanningContext.class), any(AivaConversationContext.class));
    }
}
