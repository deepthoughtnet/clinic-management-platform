package com.deepthoughtnet.clinic.api.publicsite;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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
    public List<PublicClinicSummaryResponse> clinics(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String speciality,
            @RequestParam(required = false) String tenantCode
    ) {
        return publicCatalogFacade.listClinics(q, city, speciality, tenantCode);
    }

    @GetMapping("/doctors")
    public List<PublicDoctorSummaryResponse> doctors(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String speciality,
            @RequestParam(required = false) String tenantCode
    ) {
        return publicCatalogFacade.listDoctors(q, city, speciality, tenantCode);
    }

    @GetMapping("/specialities")
    public List<String> specialities(@RequestParam(required = false) String tenantCode) {
        return publicCatalogFacade.listSpecialities(tenantCode);
    }

    @GetMapping("/search")
    public PublicSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String tenantCode
    ) {
        return publicCatalogFacade.search(q, city, tenantCode);
    }
}
