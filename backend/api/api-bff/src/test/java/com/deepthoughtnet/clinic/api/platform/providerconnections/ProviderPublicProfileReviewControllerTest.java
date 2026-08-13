package com.deepthoughtnet.clinic.api.platform.providerconnections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ProviderPublicProfileReviewControllerTest {
    @Mock
    private ProviderPublicProfileModerationService service;

    private ProviderPublicProfileReviewController controller;

    @BeforeEach
    void setUp() {
        controller = new ProviderPublicProfileReviewController(service);
    }

    @Test
    void listForwardsCanonicalQueueFilters() {
        when(service.listQueue("HOSPITAL", "Jeevanam", "Pune")).thenReturn(List.of());

        ResponseEntity<List<?>> response = controller.list("HOSPITAL", "Jeevanam", "Pune");

        assertThat(response.getBody()).isEmpty();
        verify(service).listQueue("HOSPITAL", "Jeevanam", "Pune");
    }
}
