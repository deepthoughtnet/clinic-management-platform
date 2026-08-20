package com.deepthoughtnet.clinic.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.admin.dto.AdminNotificationSettingsDtos.UpdateNotificationSettingsRequest;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.db.TenantNotificationSettingsRepository;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminNotificationSettingsControllerTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private TenantNotificationSettingsRepository repository;
    private TenantNotificationSettingsService settingsService;
    private CarePilotMessagingStatusService messagingStatusService;
    private ClinicTimeZoneResolver clinicTimeZoneResolver;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = mock(TenantNotificationSettingsRepository.class);
        settingsService = new TenantNotificationSettingsService(repository, new ObjectMapper());
        messagingStatusService = mock(CarePilotMessagingStatusService.class);
        clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminNotificationSettingsController(settingsService, messagingStatusService, clinicTimeZoneResolver)
        ).setControllerAdvice(new com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler()).build();

        when(clinicTimeZoneResolver.normalizeForPersistence(eq(tenantId), anyString())).thenReturn("UTC");
        when(messagingStatusService.providerStatuses()).thenReturn(List.of(
                new ProviderStatusResponse(MessageChannel.EMAIL, "email", true, true, true, ProviderReadinessStatus.READY, List.of(), "ready", true, OffsetDateTime.now(), true, true, false, false),
                new ProviderStatusResponse(MessageChannel.SMS, "sms", false, false, false, ProviderReadinessStatus.NOT_CONFIGURED, List.of(), "not configured", true, OffsetDateTime.now(), false, false, false, false),
                new ProviderStatusResponse(MessageChannel.WHATSAPP, "whatsapp", false, false, false, ProviderReadinessStatus.NOT_CONFIGURED, List.of(), "not configured", true, OffsetDateTime.now(), false, false, false, false)
        ));

        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void updateRejectsUnreadyFallbackWithBusinessFriendlyMessage() throws Exception {
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                null,
                null,
                "UTC",
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.SMS,
                false,
                true,
                true,
                5,
                "{}"
        );

        mockMvc.perform(
                        put("/api/admin/notification-settings")
                                .contentType("application/json")
                                .content(new ObjectMapper().writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Fallback channel must be enabled and ready for use."));

        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsNegativeRateLimitWithBusinessFriendlyMessage() throws Exception {
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                null,
                null,
                "UTC",
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.IN_APP,
                false,
                true,
                true,
                5,
                policyJsonWithRateLimitOverride("overallMessagesPerDay", -1)
        );

        mockMvc.perform(
                        put("/api/admin/notification-settings")
                                .contentType("application/json")
                                .content(new ObjectMapper().writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Overall messages/day value cannot be negative."));

        verify(repository, never()).save(any());
    }

    private String policyJsonWithRateLimitOverride(String fieldName, Object value) throws Exception {
        ObjectNode root = new ObjectMapper().createObjectNode();
        ObjectNode rateLimits = root.putObject("rateLimits");
        rateLimits.put("overallMessagesPerDay", 100);
        rateLimits.put("marketingPerDay", 20);
        rateLimits.put("reminderPerDay", 40);
        rateLimits.put("maximumPerHour", 12);
        rateLimits.put("perPatientPerDay", 5);
        if (value instanceof Integer integer) {
            rateLimits.put(fieldName, integer);
        } else if (value instanceof Long longValue) {
            rateLimits.put(fieldName, longValue);
        } else if (value instanceof Double doubleValue) {
            rateLimits.put(fieldName, doubleValue);
        } else if (value instanceof Float floatValue) {
            rateLimits.put(fieldName, floatValue);
        } else if (value == null) {
            rateLimits.putNull(fieldName);
        } else {
            rateLimits.put(fieldName, value.toString());
        }
        return new ObjectMapper().writeValueAsString(root);
    }
}
