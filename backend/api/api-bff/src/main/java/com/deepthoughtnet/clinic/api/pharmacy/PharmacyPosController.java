package com.deepthoughtnet.clinic.api.pharmacy;

import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/pharmacy/pos")
public class PharmacyPosController {
    private static final Set<String> POS_ROLES = Set.of("CLINIC_ADMIN", "PHARMACIST", "PHARMACY", "PHARMA");

    private final PharmacyPosService service;
    private final PermissionChecker permissionChecker;

    public PharmacyPosController(PharmacyPosService service, PermissionChecker permissionChecker) {
        this.service = service;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping("/search-medicines")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public List<PharmacyPosMedicineResponse> searchMedicines(@RequestParam(name = "q", required = false) String query) {
        assertPosRole();
        return service.searchMedicines(RequestContextHolder.requireTenantId(), query);
    }

    @GetMapping("/available-batches")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public List<PharmacyPosBatchResponse> availableBatches(@RequestParam UUID medicineId) {
        assertPosRole();
        return service.availableBatches(RequestContextHolder.requireTenantId(), medicineId);
    }

    @PostMapping("/sales")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public PharmacyPosSaleResponse createSale(@RequestBody PharmacyPosCreateSaleRequest request) {
        assertPosRole();
        return service.createSale(RequestContextHolder.requireTenantId(), request, RequestContextHolder.require().appUserId());
    }

    @PostMapping(value = "/prescriptions/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public PharmacyPosPrescriptionUploadResponse uploadPrescription(@RequestParam("file") MultipartFile file) throws Exception {
        assertPosRole();
        return service.uploadPrescription(RequestContextHolder.requireTenantId(), file, RequestContextHolder.require().appUserId());
    }

    @GetMapping("/prescriptions/{documentId}/download-url")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public PharmacyPosPrescriptionDownloadUrlResponse prescriptionDownloadUrl(@PathVariable UUID documentId) {
        assertPosRole();
        return service.prescriptionDownloadUrl(RequestContextHolder.requireTenantId(), documentId);
    }

    @GetMapping("/sales")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public List<PharmacyPosSaleResponse> listSales() {
        assertPosRole();
        return service.listSales(RequestContextHolder.requireTenantId());
    }

    @GetMapping("/sales/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public PharmacyPosSaleResponse getSale(@PathVariable UUID id) {
        assertPosRole();
        return service.getSale(RequestContextHolder.requireTenantId(), id);
    }

    @PostMapping("/sales/{id}/payment")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public PharmacyPosSaleResponse addPayment(@PathVariable UUID id, @RequestBody PharmacyPosPaymentRequest request) {
        assertPosRole();
        return service.recordPayment(RequestContextHolder.requireTenantId(), id, request, RequestContextHolder.require().appUserId());
    }

    @PostMapping("/sales/{id}/return")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public PharmacyPosSaleResponse returnSale(@PathVariable UUID id, @RequestBody PharmacyPosReturnRequest request) {
        assertPosRole();
        return service.returnSale(RequestContextHolder.requireTenantId(), id, request, RequestContextHolder.require().appUserId());
    }

    @GetMapping(value = "/sales/{id}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PHARMACIST') or @permissionChecker.hasRole('PHARMACY') or @permissionChecker.hasRole('PHARMA')")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
        assertPosRole();
        PharmacyPosReceiptPdf pdf = service.generateReceipt(RequestContextHolder.requireTenantId(), id, RequestContextHolder.require().appUserId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    private void assertPosRole() {
        String role = RequestContextHolder.require().tenantRole();
        if (role == null || !POS_ROLES.contains(role.trim().toUpperCase(Locale.ROOT))) {
            throw new ForbiddenException("Only clinic admin and pharmacy counter roles can access Pharmacy POS");
        }
        if (!permissionChecker.hasPermission("inventory.manage")
                && !permissionChecker.hasPermission("billing.create")
                && !permissionChecker.hasRole("CLINIC_ADMIN")) {
            throw new ForbiddenException("Pharmacy POS access requires pharmacy operational permissions");
        }
    }
}
