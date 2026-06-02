package com.deepthoughtnet.clinic.api.publicsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicCatalogControllerTest {

    @Test
    void routesReadOnlyQueriesThroughFacade() {
        PublicCatalogFacade facade = mock(PublicCatalogFacade.class);
        PublicCatalogController controller = new PublicCatalogController(facade);
        var clinic = new PublicClinicSummaryResponse("Sunrise Clinic", "Pune", "Pune, Maharashtra, India", List.of("Dermatology"));
        var doctor = new PublicDoctorSummaryResponse("Dr. Asha Menon", "Sunrise Clinic", "Pune", "Dermatology", null, List.of(), null);
        var search = new PublicSearchResponse(List.of(clinic), List.of(doctor), List.of("Dermatology"));

        when(facade.listClinics("skin", "pune", "Dermatology", "sunrise")).thenReturn(List.of(clinic));
        when(facade.listDoctors("skin", "pune", "Dermatology", "sunrise")).thenReturn(List.of(doctor));
        when(facade.listSpecialities("sunrise")).thenReturn(List.of("Dermatology"));
        when(facade.search("skin", "pune", "sunrise")).thenReturn(search);

        assertThat(controller.clinics("skin", "pune", "Dermatology", "sunrise")).containsExactly(clinic);
        assertThat(controller.doctors("skin", "pune", "Dermatology", "sunrise")).containsExactly(doctor);
        assertThat(controller.specialities("sunrise")).containsExactly("Dermatology");
        assertThat(controller.search("skin", "pune", "sunrise")).isEqualTo(search);
    }
}
