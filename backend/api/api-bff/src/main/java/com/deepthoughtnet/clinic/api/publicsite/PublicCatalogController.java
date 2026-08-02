package com.deepthoughtnet.clinic.api.publicsite;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicHospitalDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicHospitalSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialityDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialitySummaryResponse;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProfileMediaContent;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) Integer radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return publicCatalogFacade.listClinics(q, city, area, speciality, tenantCode, lat, lng, radiusKm, page, size);
    }

    @GetMapping("/clinics/{clinicSlug}")
    public PublicClinicDetailResponse clinic(@PathVariable String clinicSlug) {
        return publicCatalogFacade.clinicDetail(clinicSlug);
    }

    @GetMapping("/clinics/{clinicSlug}/logo")
    public ResponseEntity<byte[]> clinicLogo(@PathVariable String clinicSlug) {
        return inline(publicCatalogFacade.clinicLogo(clinicSlug));
    }

    @GetMapping("/clinics/{clinicSlug}/cover")
    public ResponseEntity<byte[]> clinicCover(@PathVariable String clinicSlug) {
        return inline(publicCatalogFacade.clinicCover(clinicSlug));
    }

    @GetMapping("/clinics/{clinicSlug}/gallery/{index}")
    public ResponseEntity<byte[]> clinicGalleryImage(@PathVariable String clinicSlug, @PathVariable int index) {
        return inline(publicCatalogFacade.clinicGalleryImage(clinicSlug, index));
    }

    @GetMapping("/doctors")
    public PublicPageResponse<PublicDoctorSummaryResponse> doctors(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String speciality,
            @RequestParam(required = false) String clinic,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) Integer radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return publicCatalogFacade.listDoctors(q, city, area, speciality, clinic, tenantCode, lat, lng, radiusKm, page, size);
    }

    @GetMapping("/doctors/{doctorSlug}")
    public PublicDoctorDetailResponse doctor(@PathVariable String doctorSlug) {
        return publicCatalogFacade.doctorDetail(doctorSlug);
    }

    @GetMapping("/doctors/{doctorSlug}/photo")
    public ResponseEntity<byte[]> doctorPhoto(@PathVariable String doctorSlug) {
        return inline(publicCatalogFacade.doctorPhoto(doctorSlug));
    }

    @GetMapping("/doctors/{doctorSlug}/cover")
    public ResponseEntity<byte[]> doctorCover(@PathVariable String doctorSlug) {
        return inline(publicCatalogFacade.doctorCover(doctorSlug));
    }

    @GetMapping("/doctors/{doctorSlug}/gallery/{index}")
    public ResponseEntity<byte[]> doctorGalleryImage(@PathVariable String doctorSlug, @PathVariable int index) {
        return inline(publicCatalogFacade.doctorGalleryImage(doctorSlug, index));
    }

    @GetMapping("/hospitals")
    public PublicPageResponse<PublicHospitalSummaryResponse> hospitals(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String speciality,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) Integer radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return publicCatalogFacade.listHospitals(q, city, area, speciality, tenantCode, lat, lng, radiusKm, page, size);
    }

    @GetMapping("/hospitals/{hospitalSlug}")
    public PublicHospitalDetailResponse hospital(@PathVariable String hospitalSlug) {
        return publicCatalogFacade.hospitalDetail(hospitalSlug);
    }

    @GetMapping("/hospitals/{hospitalSlug}/logo")
    public ResponseEntity<byte[]> hospitalLogo(@PathVariable String hospitalSlug) {
        return inline(publicCatalogFacade.hospitalLogo(hospitalSlug));
    }

    @GetMapping("/hospitals/{hospitalSlug}/cover")
    public ResponseEntity<byte[]> hospitalCover(@PathVariable String hospitalSlug) {
        return inline(publicCatalogFacade.hospitalCover(hospitalSlug));
    }

    @GetMapping("/hospitals/{hospitalSlug}/gallery/{index}")
    public ResponseEntity<byte[]> hospitalGalleryImage(@PathVariable String hospitalSlug, @PathVariable int index) {
        return inline(publicCatalogFacade.hospitalGalleryImage(hospitalSlug, index));
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
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) Integer radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return publicCatalogFacade.specialityDetail(specialitySlug, q, city, area, clinic, tenantCode, lat, lng, radiusKm, page, size);
    }

    @GetMapping("/search")
    public PublicSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) Integer radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {
        return publicCatalogFacade.search(q, city, area, tenantCode, lat, lng, radiusKm, page, size);
    }

    private ResponseEntity<byte[]> inline(PublicProfileMediaContent media) {
        String contentType = media.contentType() == null || media.contentType().isBlank()
                ? "application/octet-stream"
                : media.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "inline; filename=\"" + media.originalFilename() + "\"")
                .body(media.bytes());
    }
}
