package com.deepthoughtnet.clinic.api.discover.reference;

import com.deepthoughtnet.clinic.api.discover.reference.dto.DiscoverReferenceOptionResponse;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceOptionRecord;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discover/reference")
public class DiscoverReferenceController {
    private final DiscoverReferenceDataService referenceDataService;

    public DiscoverReferenceController(DiscoverReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/specialities")
    public List<DiscoverReferenceOptionResponse> specialities() {
        return toResponse(referenceDataService.listSpecialities());
    }

    @GetMapping("/services")
    public List<DiscoverReferenceOptionResponse> services() {
        return toResponse(referenceDataService.listServices());
    }

    @GetMapping("/facilities")
    public List<DiscoverReferenceOptionResponse> facilities() {
        return toResponse(referenceDataService.listFacilities());
    }

    @GetMapping("/languages")
    public List<DiscoverReferenceOptionResponse> languages() {
        return toResponse(referenceDataService.listLanguages());
    }

    @GetMapping("/countries")
    public List<DiscoverReferenceOptionResponse> countries() {
        return toResponse(referenceDataService.listCountries());
    }

    @GetMapping("/states")
    public List<DiscoverReferenceOptionResponse> states() {
        return toResponse(referenceDataService.listStates());
    }

    @GetMapping("/medical-councils")
    public List<DiscoverReferenceOptionResponse> medicalCouncils() {
        return toResponse(referenceDataService.listMedicalCouncils());
    }

    private List<DiscoverReferenceOptionResponse> toResponse(List<DiscoverReferenceOptionRecord> records) {
        return records.stream()
                .map(record -> new DiscoverReferenceOptionResponse(
                        record.id(),
                        record.code(),
                        record.displayName(),
                        record.providerTypes(),
                        record.displayOrder(),
                        record.active(),
                        record.category()
                ))
                .toList();
    }
}
