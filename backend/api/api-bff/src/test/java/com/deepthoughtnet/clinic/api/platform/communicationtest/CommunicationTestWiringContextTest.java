package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceConversationAdapter;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceMediaStreamClient;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceProviderConfig;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.VoiceConversationContextRegistry;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.VoiceResponseInterpreter;
import com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService;
import com.deepthoughtnet.clinic.messaging.email.Msg91EmailMessageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.deepthoughtnet.clinic.communication.service.CommunicationChannelResolver;
import com.deepthoughtnet.clinic.communication.service.CommunicationInteractionService;
import com.deepthoughtnet.clinic.patient.service.PatientService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class CommunicationTestWiringContextTest {

    @Test
    void communicationTestVoiceBeansAreConstructedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WiringConfiguration.class)) {
            assertNotNull(context.getBean(CommunicationTestDotVoiceInboundHandler.class));
            assertNotNull(context.getBean(DotVoiceMediaStreamClient.class));
            assertNotNull(context.getBean(DotVoiceInboundListenerLifecycle.class));
            assertNotNull(context.getBean(CommunicationTestService.class));
            VoiceReadOnlyAivaService aiva = context.getBean(VoiceReadOnlyAivaService.class);
            assertNotNull(aiva);
            org.junit.jupiter.api.Assertions.assertTrue(
                    context.getBean(CommunicationTestService.class).isVoiceReadOnlyAivaEnabled());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({DotVoiceProviderConfig.class, CommunicationTestDotVoiceInboundHandler.class,
            DotVoiceInboundListenerLifecycle.class, VoiceReadOnlyAivaService.class})
    static class WiringConfiguration {
        @Bean
        VoiceTestProperties voiceTestProperties() {
            return new VoiceTestProperties();
        }

        @Bean
        ClinicTimeZoneResolver clinicTimeZoneResolver() {
            return mock(ClinicTimeZoneResolver.class);
        }

        @Bean
        CarePilotMessagingStatusService messagingStatusService() {
            return mock(CarePilotMessagingStatusService.class);
        }

        @Bean
        MessageOrchestratorService messageOrchestratorService() {
            return mock(MessageOrchestratorService.class);
        }

        @Bean
        Msg91EmailMessageProvider msg91EmailMessageProvider() {
            return mock(Msg91EmailMessageProvider.class);
        }

        @Bean
        ReminderVoiceRenderer reminderVoiceRenderer() {
            return mock(ReminderVoiceRenderer.class);
        }

        @Bean
        VoiceOrchestratorService voiceOrchestratorService() {
            return mock(VoiceOrchestratorService.class);
        }

        @Bean
        VoiceConversationContextRegistry voiceConversationContextRegistry() {
            return new VoiceConversationContextRegistry();
        }

        @Bean
        VoiceResponseInterpreter voiceResponseInterpreter() {
            return new VoiceResponseInterpreter();
        }

        @Bean
        HealthcareAiReadOnlyToolGateway healthcareAiReadOnlyToolGateway() {
            return mock(HealthcareAiReadOnlyToolGateway.class);
        }

        @Bean
        AivaV2VoiceConversationAdapter aivaV2VoiceConversationAdapter() {
            return mock(AivaV2VoiceConversationAdapter.class);
        }

        @Bean
        CommunicationChannelResolver communicationChannelResolver() {
            return mock(CommunicationChannelResolver.class);
        }

        @Bean
        CommunicationTestService communicationTestService(
                CarePilotMessagingStatusService messagingStatusService,
                MessageOrchestratorService messageOrchestratorService,
                Msg91EmailMessageProvider msg91EmailMessageProvider,
                ReminderVoiceRenderer reminderVoiceRenderer,
                DotVoiceMediaStreamClient dotVoiceMediaStreamClient,
                VoiceOrchestratorService voiceOrchestratorService,
                VoiceConversationContextRegistry contextRegistry,
                VoiceResponseInterpreter responseInterpreter,
                CommunicationChannelResolver communicationChannelResolver,
                PatientService patientService,
                CommunicationInteractionService interactionService,
                VoiceReadOnlyAivaService voiceReadOnlyAivaService) {
            return new CommunicationTestService(
                    messagingStatusService,
                    messageOrchestratorService,
                    java.util.List.of(),
                    java.util.List.of(),
                    msg91EmailMessageProvider,
                    reminderVoiceRenderer,
                    dotVoiceMediaStreamClient,
                    voiceOrchestratorService,
                    contextRegistry,
                    responseInterpreter,
                    communicationChannelResolver,
                    patientService,
                    interactionService,
                    voiceReadOnlyAivaService);
        }

        @Bean
        PatientService patientService() {
            return mock(PatientService.class);
        }

        @Bean
        CommunicationInteractionService communicationInteractionService() {
            return mock(CommunicationInteractionService.class);
        }
    }
}
