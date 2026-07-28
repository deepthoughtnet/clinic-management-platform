package com.deepthoughtnet.clinic.api.publicsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicHospitalSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialitySummaryResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicCatalogControllerTest {

    @Test
    void routesPublicDirectoryQueriesThroughFacade() {
        PublicCatalogFacade facade = mock(PublicCatalogFacade.class);
        PublicCatalogController controller = new PublicCatalogController(facade);

        var doctor = new PublicDoctorSummaryResponse(
                "0f8fad5b-d9cb-469f-a165-70867728950e",
                "dr-asha-menon",
                "/discover/doctors/dr-asha-menon",
                "Dr. Asha Menon",
                null,
                "Dermatology",
                8,
                List.of("English"),
                "Baner",
                "Pune",
                "Experienced doctor",
                "Doctor summary",
                "Sunrise Clinic",
                "sunrise-clinic",
                true,
                "Today · 10:30 AM"
        );
        var clinic = new PublicClinicSummaryResponse(
                "sunrise-clinic",
                "/discover/clinics/sunrise-clinic",
                "Sunrise Clinic",
                null,
                null,
                "Baner, Pune, Maharashtra",
                "Baner",
                "Pune",
                1,
                2,
                3,
                4,
                true,
                List.of("Dermatology"),
                "Clinic",
                "Clinic summary",
                true
        );
        var hospital = new PublicHospitalSummaryResponse(
                "city-care-hospital",
                "/discover/hospitals/city-care-hospital",
                "City Care Hospital",
                null,
                null,
                "Baner",
                "Pune",
                2,
                4,
                5,
                6,
                false,
                List.of("Cardiology"),
                "Hospital",
                "Hospital summary"
        );
        var doctorsPage = new PublicPageResponse<>(List.of(doctor), 0, 12, 1, 1);
        var clinicsPage = new PublicPageResponse<>(List.of(clinic), 0, 12, 1, 1);
        var hospitalsPage = new PublicPageResponse<>(List.of(hospital), 0, 12, 1, 1);
        var speciality = new PublicSpecialitySummaryResponse("Dermatology", "dermatology", 1, 1, 1);
        var search = new PublicSearchResponse(doctorsPage, clinicsPage, hospitalsPage, List.of(speciality));

        when(facade.listClinics("skin", "pune", "baner", "Dermatology", "sunrise", 0, 12)).thenReturn(clinicsPage);
        when(facade.listDoctors("skin", "pune", "baner", "Dermatology", "sunrise", "demo", 0, 12)).thenReturn(doctorsPage);
        when(facade.listHospitals("skin", "pune", "baner", "Dermatology", "demo", 0, 12)).thenReturn(hospitalsPage);
        when(facade.listSpecialities("skin", "pune", "demo")).thenReturn(List.of(speciality));
        when(facade.search("skin", "pune", "baner", "demo", 0, 6)).thenReturn(search);

        assertThat(controller.clinics("skin", "pune", "baner", "Dermatology", "sunrise", 0, 12)).isEqualTo(clinicsPage);
        assertThat(controller.doctors("skin", "pune", "baner", "Dermatology", "sunrise", "demo", 0, 12)).isEqualTo(doctorsPage);
        assertThat(controller.hospitals("skin", "pune", "baner", "Dermatology", "demo", 0, 12)).isEqualTo(hospitalsPage);
        assertThat(controller.specialities("skin", "pune", "demo")).containsExactly(speciality);
        assertThat(controller.search("skin", "pune", "baner", "demo", 0, 6)).isEqualTo(search);
    }
}
