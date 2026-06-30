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
    private static final Set<String> POS_ROLES = Set.of("CLINIC_ADMIN", "PHARMACIST", "PHARMACY", "PHARMA", "PHARMACY_POS_USER", "PHARMACY_INVENTORY_MANAGER");
    private static final String POS_ROLE_PREAUTHORIZE = "@permissionChecker.hasAnyRole('CLINIC_ADMIN', 'PHARMACIST', 'PHARMACY', 'PHARMA', 'PHARMACY_POS_USER', 'PHARMACY_INVENTORY_MANAGER')";

    private final PharmacyPosService service;
    private final PermissionChecker permissionChecker;

    public PharmacyPosController(PharmacyPosService service, PermissionChecker permissionChecker) {
        this.service = service;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping("/search-medicines")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public List<PharmacyPosMedicineResponse> searchMedicines(@RequestParam(name = "q", required = false) String query) {
        assertPosRole();
        return service.searchMedicines(RequestContextHolder.requireTenantId(), query);
    }

    @GetMapping("/available-batches")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public List<PharmacyPosBatchResponse> availableBatches(@RequestParam UUID medicineId) {
        assertPosRole();
        return service.availableBatches(RequestContextHolder.requireTenantId(), medicineId);
    }

    @PostMapping("/sales")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public PharmacyPosSaleResponse createSale(@RequestBody PharmacyPosCreateSaleRequest request) {
        String normalizedRole = assertPosRole();
        assertCheckoutPermission(normalizedRole);
        return service.createSale(RequestContextHolder.requireTenantId(), request, RequestContextHolder.require().appUserId());
    }

    @PostMapping(value = "/prescriptions/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public PharmacyPosPrescriptionUploadResponse uploadPrescription(@RequestParam("file") MultipartFile file) throws Exception {
        assertPosRole();
        return service.uploadPrescription(RequestContextHolder.requireTenantId(), file, RequestContextHolder.require().appUserId());
    }

    @GetMapping("/prescriptions/{documentId}/download-url")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public PharmacyPosPrescriptionDownloadUrlResponse prescriptionDownloadUrl(@PathVariable UUID documentId) {
        assertPosRole();
        return service.prescriptionDownloadUrl(RequestContextHolder.requireTenantId(), documentId);
    }

    @GetMapping("/sales")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public List<PharmacyPosSaleResponse> listSales() {
        assertPosRole();
        return service.listSales(RequestContextHolder.requireTenantId());
    }

    @GetMapping("/sales/{id}")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public PharmacyPosSaleResponse getSale(@PathVariable UUID id) {
        assertPosRole();
        return service.getSale(RequestContextHolder.requireTenantId(), id);
    }

    @PostMapping("/sales/{id}/payment")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public PharmacyPosSaleResponse addPayment(@PathVariable UUID id, @RequestBody PharmacyPosPaymentRequest request) {
        String normalizedRole = assertPosRole();
        assertCheckoutPermission(normalizedRole);
        return service.recordPayment(RequestContextHolder.requireTenantId(), id, request, RequestContextHolder.require().appUserId());
    }

    @PostMapping("/shifts/open")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public PharmacyPosShiftResponse openShift(@RequestBody PharmacyPosOpenShiftRequest request) {
        String normalizedRole = assertPosRole();
        assertCheckoutPermission(normalizedRole);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.openShift(RequestContextHolder.requireTenantId(), actorAppUserId, request);
    }

    @GetMapping("/shifts/current")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public PharmacyPosShiftResponse currentShift() {
        assertPosRole();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.getCurrentShift(RequestContextHolder.requireTenantId(), actorAppUserId);
    }

    @PostMapping("/shifts/{shiftId}/close")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public PharmacyPosShiftResponse closeShift(@PathVariable UUID shiftId, @RequestBody PharmacyPosCloseShiftRequest request) {
        String normalizedRole = assertPosRole();
        assertCheckoutPermission(normalizedRole);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.closeShift(
                RequestContextHolder.requireTenantId(),
                shiftId,
                actorAppUserId,
                permissionChecker.hasRole("CLINIC_ADMIN"),
                request
        );
    }

    @GetMapping("/shifts")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public List<PharmacyPosShiftResponse> listShifts(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID cashier
    ) {
        assertPosRole();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.listShifts(
                RequestContextHolder.requireTenantId(),
                actorAppUserId,
                permissionChecker.hasRole("CLINIC_ADMIN"),
                dateFrom,
                dateTo,
                status,
                cashier
        );
    }

    @PostMapping("/sales/{id}/return")
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public PharmacyPosSaleResponse returnSale(@PathVariable UUID id, @RequestBody PharmacyPosReturnRequest request) {
        assertPosRole();
        return service.returnSale(RequestContextHolder.requireTenantId(), id, request, RequestContextHolder.require().appUserId());
    }

    @GetMapping(value = "/sales/{id}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(POS_ROLE_PREAUTHORIZE)
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
        assertPosRole();
        PharmacyPosReceiptPdf pdf = service.generateReceipt(RequestContextHolder.requireTenantId(), id, RequestContextHolder.require().appUserId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    private String assertPosRole() {
        String role = RequestContextHolder.require().tenantRole();
        if (role == null || !POS_ROLES.contains(role.trim().toUpperCase(Locale.ROOT))) {
            throw new ForbiddenException("Only clinic admin and pharmacy counter roles can access Pharmacy POS");
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private void assertCheckoutPermission(String normalizedRole) {
        if (permissionChecker.hasRole("CLINIC_ADMIN")) {
            return;
        }
        if (("PHARMACY_INVENTORY_MANAGER".equals(normalizedRole) || "PHARMACY_POS_USER".equals(normalizedRole))
                && !permissionChecker.hasPermission("billing.create")
                && !permissionChecker.hasPermission("payment.collect")) {
            throw new ForbiddenException("Pharmacy POS access requires pharmacy checkout permissions");
        }
    }
}
