package com.deepthoughtnet.clinic.api.publicsite;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialityDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialitySummaryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicCatalogController {
    private final PublicCatalogFacade publicCatalogFacade;

    public PublicCatalogController(PublicCatalogFacade publicCatalogFacade) {
        this.publicCatalogFacade = publicCatalogFacade;
    }

    @GetMapping("/clinics")
    public PublicPageResponse<PublicClinicSummaryResponse> clinics(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String speciality,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return publicCatalogFacade.listClinics(q, city, area, speciality, tenantCode, page, size);
    }

    @GetMapping("/clinics/{clinicSlug}")
    public PublicClinicDetailResponse clinic(@PathVariable String clinicSlug) {
        return publicCatalogFacade.clinicDetail(clinicSlug);
    }

    @GetMapping("/doctors")
    public PublicPageResponse<PublicDoctorSummaryResponse> doctors(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String speciality,
            @RequestParam(required = false) String clinic,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return publicCatalogFacade.listDoctors(q, city, area, speciality, clinic, tenantCode, page, size);
    }

    @GetMapping("/doctors/{doctorSlug}")
    public PublicDoctorDetailResponse doctor(@PathVariable String doctorSlug) {
        return publicCatalogFacade.doctorDetail(doctorSlug);
    }

    @GetMapping("/specialities")
    public List<PublicSpecialitySummaryResponse> specialities(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String tenantCode
    ) {
        return publicCatalogFacade.listSpecialities(q, city, tenantCode);
    }

    @GetMapping("/specialities/{specialitySlug}")
    public PublicSpecialityDetailResponse speciality(
            @PathVariable String specialitySlug,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String clinic,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return publicCatalogFacade.specialityDetail(specialitySlug, q, city, area, clinic, tenantCode, page, size);
    }

    @GetMapping("/search")
    public PublicSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {
        return publicCatalogFacade.search(q, city, area, tenantCode, page, size);
    }
}
