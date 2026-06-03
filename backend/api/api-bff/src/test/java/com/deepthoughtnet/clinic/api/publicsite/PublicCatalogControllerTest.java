package com.deepthoughtnet.clinic.api.publicsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialityDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialitySummaryResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicCatalogControllerTest {

    @Test
    void routesPublicDirectoryQueriesThroughFacade() {
        PublicCatalogFacade facade = mock(PublicCatalogFacade.class);
        PublicCatalogController controller = new PublicCatalogController(facade);

        var doctor = new PublicDoctorSummaryResponse(
                "dr-asha-menon",
                "Dr. Asha Menon",
                null,
                "Dermatology",
                8,
                List.of(),
                "Sunrise Clinic",
                "sunrise-clinic",
                "Baner",
                "Pune",
                true,
                "Today · 10:30 AM"
        );
        var clinic = new PublicClinicSummaryResponse(
                "sunrise-clinic",
                "Sunrise Clinic",
                null,
                "Baner, Pune, Maharashtra",
                "Baner",
                "Pune",
                1,
                true,
                List.of("Dermatology")
        );
        var doctorsPage = new PublicPageResponse<>(List.of(doctor), 0, 12, 1, 1);
        var clinicsPage = new PublicPageResponse<>(List.of(clinic), 0, 12, 1, 1);
        var speciality = new PublicSpecialitySummaryResponse("Dermatology", "dermatology", 1, 1);
        var search = new PublicSearchResponse(doctorsPage, clinicsPage, List.of(speciality));
        var doctorDetail = new PublicDoctorDetailResponse(
                "dr-asha-menon",
                "Dr. Asha Menon",
                null,
                "MBBS",
                8,
                List.of("Dermatology"),
                List.of(),
                List.of(),
                List.of("Monday"),
                List.of("Today · 10:30 AM"),
                true
        );
        var clinicDetail = new PublicClinicDetailResponse(
                "sunrise-clinic",
                "Sunrise Clinic",
                null,
                "Baner, Pune",
                "Baner",
                "Pune",
                List.of("Mon: 9:00 AM - 5:00 PM"),
                List.of(doctor),
                List.of("Dermatology"),
                true
        );
        var specialityDetail = new PublicSpecialityDetailResponse("Dermatology", "dermatology", doctorsPage);

        when(facade.listClinics("skin", "pune", "baner", "Dermatology", "sunrise", 0, 12)).thenReturn(clinicsPage);
        when(facade.listDoctors("skin", "pune", "baner", "Dermatology", "sunrise", "demo", 0, 12)).thenReturn(doctorsPage);
        when(facade.listSpecialities("skin", "pune", "demo")).thenReturn(List.of(speciality));
        when(facade.search("skin", "pune", "baner", "demo", 0, 6)).thenReturn(search);
        when(facade.doctorDetail("dr-asha-menon")).thenReturn(doctorDetail);
        when(facade.clinicDetail("sunrise-clinic")).thenReturn(clinicDetail);
        when(facade.specialityDetail("dermatology", "asha", "pune", "baner", "sunrise", "demo", 0, 12)).thenReturn(specialityDetail);

        assertThat(controller.clinics("skin", "pune", "baner", "Dermatology", "sunrise", 0, 12)).isEqualTo(clinicsPage);
        assertThat(controller.doctors("skin", "pune", "baner", "Dermatology", "sunrise", "demo", 0, 12)).isEqualTo(doctorsPage);
        assertThat(controller.specialities("skin", "pune", "demo")).containsExactly(speciality);
        assertThat(controller.search("skin", "pune", "baner", "demo", 0, 6)).isEqualTo(search);
        assertThat(controller.doctor("dr-asha-menon")).isEqualTo(doctorDetail);
        assertThat(controller.clinic("sunrise-clinic")).isEqualTo(clinicDetail);
        assertThat(controller.speciality("dermatology", "asha", "pune", "baner", "sunrise", "demo", 0, 12)).isEqualTo(specialityDetail);
    }
}
