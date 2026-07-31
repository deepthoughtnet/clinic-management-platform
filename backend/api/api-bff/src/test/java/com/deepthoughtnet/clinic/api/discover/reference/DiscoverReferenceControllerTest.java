package com.deepthoughtnet.clinic.api.discover.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.discover.reference.dto.DiscoverReferenceOptionResponse;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceCategory;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceOptionRecord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DiscoverReferenceControllerTest {
    @Test
    void exposesPublicGetOnlyReferenceCatalogRoutes() throws NoSuchMethodException {
        RequestMapping mapping = DiscoverReferenceController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/discover/reference");

        for (var method : DiscoverReferenceController.class.getDeclaredMethods()) {
            if (method.getName().equals("toResponse") || method.isSynthetic() || method.isBridge()) {
                continue;
            }
            assertThat(method.getAnnotation(GetMapping.class)).as(method.getName()).isNotNull();
            assertThat(method.getAnnotation(PreAuthorize.class)).as(method.getName()).isNull();
        }
    }

    @Test
    void returnsSpecialitiesFromReferenceService() {
        DiscoverReferenceDataService service = Mockito.mock(DiscoverReferenceDataService.class);
        var option = new DiscoverReferenceOptionRecord(UUID.randomUUID(), DiscoverReferenceCategory.SPECIALITY, "GENERAL_MEDICINE", "General Medicine", List.of(ProviderType.INDIVIDUAL_DOCTOR, ProviderType.CLINIC), 1, true);
        when(service.listSpecialities()).thenReturn(List.of(option));

        DiscoverReferenceController controller = new DiscoverReferenceController(service);

        List<DiscoverReferenceOptionResponse> response = controller.specialities();
        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("GENERAL_MEDICINE");
            assertThat(item.displayName()).isEqualTo("General Medicine");
            assertThat(item.providerTypes()).contains(ProviderType.INDIVIDUAL_DOCTOR, ProviderType.CLINIC);
            assertThat(item.category()).isEqualTo(DiscoverReferenceCategory.SPECIALITY);
        });
    }

    @Test
    void returnsOwnershipsAndOrganisationTypesFromReferenceService() {
        DiscoverReferenceDataService service = Mockito.mock(DiscoverReferenceDataService.class);
        var ownership = new DiscoverReferenceOptionRecord(UUID.randomUUID(), DiscoverReferenceCategory.OWNERSHIP, "PRIVATE", "Private", List.of(ProviderType.CLINIC), 1, true);
        var organisationType = new DiscoverReferenceOptionRecord(UUID.randomUUID(), DiscoverReferenceCategory.ORGANISATION_TYPE, "STANDALONE_CLINIC", "Standalone clinic", List.of(ProviderType.CLINIC), 1, true);
        when(service.listOwnerships()).thenReturn(List.of(ownership));
        when(service.listOrganisationTypes()).thenReturn(List.of(organisationType));

        DiscoverReferenceController controller = new DiscoverReferenceController(service);

        assertThat(controller.ownerships()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("PRIVATE");
            assertThat(item.displayName()).isEqualTo("Private");
            assertThat(item.category()).isEqualTo(DiscoverReferenceCategory.OWNERSHIP);
        });
        assertThat(controller.organisationTypes()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("STANDALONE_CLINIC");
            assertThat(item.displayName()).isEqualTo("Standalone clinic");
            assertThat(item.category()).isEqualTo(DiscoverReferenceCategory.ORGANISATION_TYPE);
        });
    }
}
