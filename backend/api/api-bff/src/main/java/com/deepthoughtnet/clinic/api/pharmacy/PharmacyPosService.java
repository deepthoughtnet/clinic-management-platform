package com.deepthoughtnet.clinic.api.pharmacy;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyCashierShiftEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacyCashierShiftRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleItemEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleItemRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePaymentEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePaymentRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePrescriptionEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePrescriptionRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PharmacyPosService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter PDF_DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a", Locale.ENGLISH);
    private static final Duration PRESCRIPTION_DOWNLOAD_TTL = Duration.ofMinutes(10);
    private static final long MAX_PRESCRIPTION_SIZE_BYTES = 25L * 1024L * 1024L;
    private static final Map<String, String> PRESCRIPTION_MEDIA_TYPES = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "dll", "bat", "cmd", "com", "msi", "ps1", "vbs", "js", "jar", "sh", "php", "pl", "scr", "hta", "apk", "bin"
    );

    private final InventoryService inventoryService;
    private final MedicineRepository medicineRepository;
    private final StockRepository stockRepository;
    private final InventoryLocationRepository locationRepository;
    private final PatientRepository patientRepository;
    private final ClinicProfileService clinicProfileService;
    private final PharmacyCashierShiftRepository cashierShiftRepository;
    private final PharmacySaleRepository saleRepository;
    private final PharmacySaleItemRepository saleItemRepository;
    private final PharmacySalePaymentRepository salePaymentRepository;
    private final PharmacySaleReturnRepository saleReturnRepository;
    private final PharmacySalePrescriptionRepository salePrescriptionRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectStorageService storageService;
    private final ObjectMapper objectMapper;

    public PharmacyPosService(
            InventoryService inventoryService,
            MedicineRepository medicineRepository,
            StockRepository stockRepository,
            InventoryLocationRepository locationRepository,
            PatientRepository patientRepository,
            ClinicProfileService clinicProfileService,
            PharmacyCashierShiftRepository cashierShiftRepository,
            PharmacySaleRepository saleRepository,
            PharmacySaleItemRepository saleItemRepository,
            PharmacySalePaymentRepository salePaymentRepository,
            PharmacySaleReturnRepository saleReturnRepository,
            AuditEventPublisher auditEventPublisher,
            PharmacySalePrescriptionRepository salePrescriptionRepository,
            ObjectStorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.inventoryService = inventoryService;
        this.medicineRepository = medicineRepository;
        this.stockRepository = stockRepository;
        this.locationRepository = locationRepository;
        this.patientRepository = patientRepository;
        this.clinicProfileService = clinicProfileService;
        this.cashierShiftRepository = cashierShiftRepository;
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.salePaymentRepository = salePaymentRepository;
        this.saleReturnRepository = saleReturnRepository;
        this.salePrescriptionRepository = salePrescriptionRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PharmacyPosMedicineResponse> searchMedicines(UUID tenantId, String query) {
        InventoryLocationEntity location = ensureDefaultLocation(tenantId);
        String term = normalizeSearch(query);
        Map<UUID, MedicineEntity> medicines = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .filter(MedicineEntity::isActive)
                .collect(Collectors.toMap(MedicineEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, List<StockEntity>> stockByMedicine = stockRepository.findByTenantIdAndLocationIdOrderByUpdatedAtDesc(tenantId, location.getId()).stream()
                .filter(StockEntity::isActive)
                .collect(Collectors.groupingBy(StockEntity::getMedicineId, LinkedHashMap::new, Collectors.toList()));
        return medicines.values().stream()
                .filter(medicine -> term.isBlank() || matchesMedicine(term, medicine, stockByMedicine.get(medicine.getId())))
                .map(medicine -> toMedicineResponse(medicine, stockByMedicine.get(medicine.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PharmacyPosBatchResponse> availableBatches(UUID tenantId, UUID medicineId) {
        requireId(medicineId, "medicineId");
        InventoryLocationEntity location = ensureDefaultLocation(tenantId);
        return sortForFefo(stockRepository.findByTenantIdAndMedicineIdAndLocationId(tenantId, medicineId, location.getId())).stream()
                .filter(stock -> stock.isActive() && stock.getQuantityOnHand() > 0 && !isExpired(stock))
                .map(stock -> toBatchResponse(stock, location))
                .toList();
    }

    @Transactional
    public PharmacyPosPrescriptionUploadResponse uploadPrescription(UUID tenantId, MultipartFile file, UUID actorAppUserId) throws IOException {
        validatePrescriptionFile(file);
        String fileName = sanitizeFilename(file.getOriginalFilename());
        String mediaType = normalizePrescriptionMediaType(file.getContentType(), fileName);
        byte[] bytes = file.getBytes();
        String storageKey = storageService.buildDocumentStorageKey(tenantId, "pharmacy-pos-prescription-" + fileName);
        if (salePrescriptionRepository.existsByTenantIdAndStorageKey(tenantId, storageKey)) {
            throw new IllegalStateException("Prescription storage key already exists");
        }
        storageService.putObject(storageKey, mediaType, bytes);
        try {
            PharmacySalePrescriptionEntity saved = salePrescriptionRepository.save(PharmacySalePrescriptionEntity.create(
                    tenantId,
                    actorAppUserId,
                    fileName,
                    mediaType,
                    bytes.length,
                    sha256(bytes),
                    storageKey
            ));
            audit("pharmacy.sale.prescription_uploaded", tenantId, saved.getId(), actorAppUserId, "Uploaded pharmacy POS prescription", Map.of(
                    "fileName", saved.getOriginalFilename(),
                    "sizeBytes", saved.getSizeBytes()
            ));
            return new PharmacyPosPrescriptionUploadResponse(
                    saved.getId(),
                    saved.getOriginalFilename(),
                    saved.getMediaType(),
                    saved.getSizeBytes(),
                    saved.getCreatedAt()
            );
        } catch (RuntimeException ex) {
            storageService.deleteObjectQuietly(storageKey);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public PharmacyPosPrescriptionDownloadUrlResponse prescriptionDownloadUrl(UUID tenantId, UUID documentId) {
        requireId(documentId, "documentId");
        PharmacySalePrescriptionEntity prescription = findPrescription(tenantId, documentId);
        return new PharmacyPosPrescriptionDownloadUrlResponse(
                storageService.generatePresignedDownloadUrl(prescription.getStorageKey(), PRESCRIPTION_DOWNLOAD_TTL),
                String.valueOf(PRESCRIPTION_DOWNLOAD_TTL.toSeconds())
        );
    }

    @Transactional
    public PharmacyPosShiftResponse openShift(UUID tenantId, UUID actorAppUserId, PharmacyPosOpenShiftRequest request) {
        if (cashierShiftRepository.existsByTenantIdAndCashierUserIdAndStatus(tenantId, actorAppUserId, "OPEN")) {
            throw new IllegalArgumentException("An open cashier shift already exists for this user");
        }
        BigDecimal openingCashAmount = money(request == null ? null : request.openingCashAmount());
        PharmacyCashierShiftEntity shift = PharmacyCashierShiftEntity.open(
                tenantId,
                actorAppUserId,
                actorAppUserId,
                openingCashAmount,
                normalizeNullable(request == null ? null : request.notes())
        );
        try {
            cashierShiftRepository.save(shift);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("An open cashier shift already exists for this user", ex);
        }
        audit("pharmacy.shift.opened", tenantId, shift.getId(), actorAppUserId, "Opened pharmacy cashier shift", Map.of(
                "cashierUserId", actorAppUserId,
                "openingCashAmount", openingCashAmount
        ));
        return mapShift(tenantId, shift);
    }

    @Transactional(readOnly = true)
    public PharmacyPosShiftResponse getCurrentShift(UUID tenantId, UUID actorAppUserId) {
        return cashierShiftRepository.findByTenantIdAndCashierUserIdAndStatus(tenantId, actorAppUserId, "OPEN")
                .map(shift -> mapShift(tenantId, shift))
                .orElse(null);
    }

    @Transactional
    public PharmacyPosShiftResponse closeShift(UUID tenantId, UUID shiftId, UUID actorAppUserId, boolean adminOverride, PharmacyPosCloseShiftRequest request) {
        requireId(shiftId, "shiftId");
        PharmacyCashierShiftEntity shift = cashierShiftRepository.findByTenantIdAndId(tenantId, shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Cashier shift not found"));
        if (!"OPEN".equals(shift.getStatus())) {
            throw new IllegalArgumentException("Only open cashier shifts can be closed");
        }
        if (!adminOverride && !shift.getCashierUserId().equals(actorAppUserId)) {
            throw new IllegalArgumentException("Only the owning cashier can close this shift");
        }
        ShiftTotals expected = calculateShiftTotals(tenantId, shift.getId());
        BigDecimal actualCashAmount = money(request == null ? null : request.actualCashAmount());
        BigDecimal actualUpiAmount = money(request == null ? null : request.actualUpiAmount());
        BigDecimal actualCardAmount = money(request == null ? null : request.actualCardAmount());
        BigDecimal actualOtherAmount = money(request == null ? null : request.actualOtherAmount());
        BigDecimal varianceAmount = actualCashAmount.add(actualUpiAmount).add(actualCardAmount).add(actualOtherAmount)
                .subtract(expected.total())
                .setScale(2, RoundingMode.HALF_UP);
        shift.close(
                actorAppUserId,
                expected.cash(),
                expected.upi(),
                expected.card(),
                expected.other(),
                actualCashAmount,
                actualUpiAmount,
                actualCardAmount,
                actualOtherAmount,
                varianceAmount,
                normalizeNullable(request == null ? null : request.closeNotes())
        );
        cashierShiftRepository.save(shift);
        audit("pharmacy.shift.closed", tenantId, shift.getId(), actorAppUserId, "Closed pharmacy cashier shift", Map.of(
                "varianceAmount", varianceAmount,
                "expectedTotalAmount", expected.total()
        ));
        return mapShift(tenantId, shift);
    }

    @Transactional(readOnly = true)
    public List<PharmacyPosShiftResponse> listShifts(
            UUID tenantId,
            UUID actorAppUserId,
            boolean adminOverride,
            String dateFrom,
            String dateTo,
            String status,
            UUID cashier
    ) {
        List<PharmacyCashierShiftEntity> shifts = adminOverride
                ? cashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId)
                : cashierShiftRepository.findByTenantIdAndCashierUserIdOrderByOpenedAtDesc(tenantId, actorAppUserId);
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        String normalizedStatus = normalizeNullable(status);
        return shifts.stream()
                .filter(shift -> adminOverride || shift.getCashierUserId().equals(actorAppUserId))
                .filter(shift -> cashier == null || shift.getCashierUserId().equals(cashier))
                .filter(shift -> normalizedStatus == null || shift.getStatus().equalsIgnoreCase(normalizedStatus))
                .filter(shift -> from == null || !shift.getOpenedAt().toLocalDate().isBefore(from))
                .filter(shift -> to == null || !shift.getOpenedAt().toLocalDate().isAfter(to))
                .map(shift -> mapShift(tenantId, shift))
                .toList();
    }

    @Transactional
    public PharmacyPosSaleResponse createSale(UUID tenantId, PharmacyPosCreateSaleRequest request, UUID actorAppUserId) {
        validateCreateRequest(request);
        InventoryLocationEntity location = ensureDefaultLocation(tenantId);
        PatientEntity patient = resolvePatient(tenantId, request.patientId());
        PharmacySalePrescriptionEntity prescription = request.prescriptionDocumentId() == null
                ? null
                : findAvailablePrescription(tenantId, request.prescriptionDocumentId());

        List<AllocationDraft> allocationDrafts = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        BigDecimal lineDiscountTotal = ZERO;
        BigDecimal lineTaxTotal = ZERO;
        for (PharmacyPosSaleLineRequest line : request.items()) {
            AllocationPlan plan = planAllocation(tenantId, location.getId(), line);
            allocationDrafts.addAll(plan.allocations());
            subtotal = subtotal.add(plan.grossAmount()).setScale(2, RoundingMode.HALF_UP);
            lineDiscountTotal = lineDiscountTotal.add(plan.discountAmount()).setScale(2, RoundingMode.HALF_UP);
            lineTaxTotal = lineTaxTotal.add(plan.taxAmount()).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal extraDiscount = money(request.discount());
        BigDecimal extraTax = money(request.tax());
        BigDecimal totalDiscount = lineDiscountTotal.add(extraDiscount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTax = lineTaxTotal.add(extraTax).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(totalDiscount).add(totalTax).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(ZERO) < 0) {
            total = ZERO;
        }
        if (request.paidAmount() == null) {
            throw new IllegalArgumentException("paidAmount is required");
        }
        BigDecimal paidAmount = money(request.paidAmount());
        if (paidAmount.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("Paid amount cannot be negative.");
        }
        if (paidAmount.compareTo(total) < 0 && total.compareTo(ZERO) > 0) {
            throw new IllegalArgumentException("Full payment is required before completing the pharmacy sale.");
        }
        if (paidAmount.compareTo(total) > 0) {
            throw new IllegalArgumentException("Paid amount cannot exceed sale total.");
        }
        if (request.paymentMode() == null) {
            throw new IllegalArgumentException("Payment mode is required.");
        }
        if (total.compareTo(ZERO) > 0 && paidAmount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Full payment is required before completing the pharmacy sale.");
        }
        PharmacyCashierShiftEntity activeShift = paidAmount.compareTo(ZERO) > 0 ? requireOpenShift(tenantId, actorAppUserId) : null;
        if (request.paymentMode() != null && request.paymentMode() != PaymentMode.CASH && !StringUtils.hasText(request.paymentReference()) && paidAmount.compareTo(ZERO) > 0) {
            throw new IllegalArgumentException("Payment reference is required for non-cash payments.");
        }
        BigDecimal dueAmount = total.subtract(paidAmount).setScale(2, RoundingMode.HALF_UP);
        PharmacySaleEntity sale = saleRepository.save(PharmacySaleEntity.create(
                tenantId,
                generateSaleNumber(tenantId),
                patient == null ? null : patient.getId(),
                normalizeNullable(request.customerName()),
                normalizeNullable(request.customerMobile()),
                request.saleDateTime() == null ? OffsetDateTime.now() : request.saleDateTime(),
                location.getId(),
                subtotal,
                totalDiscount,
                totalTax,
                total,
                paidAmount,
                dueAmount,
                statusForSale(total, paidAmount, false, false),
                normalizeNullable(request.notes()),
                actorAppUserId
        ));

        for (AllocationDraft allocation : allocationDrafts) {
            saleItemRepository.save(PharmacySaleItemEntity.create(
                    tenantId,
                    sale.getId(),
                    allocation.medicine().getId(),
                    allocation.stock().getId(),
                    normalizeNullable(allocation.stock().getBatchNumber()),
                    allocation.stock().getExpiryDate(),
                    allocation.quantity(),
                    allocation.unitPrice(),
                    allocation.discountAmount(),
                    allocation.taxAmount(),
                    allocation.lineTotal()
            ));
            inventoryService.createTransaction(tenantId, new InventoryTransactionCommand(
                    allocation.medicine().getId(),
                    allocation.stock().getId(),
                    location.getId(),
                    null,
                    InventoryTransactionType.SALE,
                    allocation.quantity(),
                    "Pharmacy POS sale " + sale.getSaleNumber(),
                    "PHARMACY_SALE",
                    sale.getId(),
                    actorAppUserId,
                    "FEFO sale allocation from batch " + defaultString(allocation.stock().getBatchNumber(), "NA")
            ), actorAppUserId);
        }

        if (paidAmount.compareTo(ZERO) > 0) {
            salePaymentRepository.save(PharmacySalePaymentEntity.create(
                    tenantId,
                    sale.getId(),
                    activeShift == null ? null : activeShift.getId(),
                sale.getSaleDateTime().toLocalDate(),
                    sale.getSaleDateTime(),
                    paidAmount,
                    request.paymentMode().name(),
                    normalizeNullable(request.paymentReference()),
                    generateReceiptNumber(tenantId),
                    normalizeNullable(request.paymentNotes()),
                    actorAppUserId
            ));
        }
        if (prescription != null) {
            prescription.linkToSale(sale.getId());
            salePrescriptionRepository.save(prescription);
            sale.attachPrescription(prescription.getId(), prescription.getOriginalFilename(), prescription.getCreatedAt());
            saleRepository.save(sale);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("saleNumber", sale.getSaleNumber());
        details.put("total", sale.getTotal());
        details.put("paidAmount", sale.getPaidAmount());
        details.put("dueAmount", sale.getDueAmount());
        details.put("locationId", location.getId());
        if (prescription != null) {
            details.put("prescriptionDocumentId", prescription.getId());
        }
        audit("pharmacy.sale.created", tenantId, sale.getId(), actorAppUserId, "Created pharmacy POS sale", details);
        return getSale(tenantId, sale.getId());
    }

    @Transactional(readOnly = true)
    public List<PharmacyPosSaleResponse> listSales(UUID tenantId) {
        return saleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(sale -> mapSale(tenantId, sale))
                .toList();
    }

    @Transactional(readOnly = true)
    public PharmacyPosSaleResponse getSale(UUID tenantId, UUID saleId) {
        requireId(saleId, "saleId");
        PharmacySaleEntity sale = saleRepository.findByTenantIdAndId(tenantId, saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));
        return mapSale(tenantId, sale);
    }

    @Transactional
    public PharmacyPosSaleResponse recordPayment(UUID tenantId, UUID saleId, PharmacyPosPaymentRequest request, UUID actorAppUserId) {
        requireId(saleId, "saleId");
        if (request == null || request.amount() == null || request.amount().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("amount is required");
        }
        if (request.paymentMode() == null) {
            throw new IllegalArgumentException("paymentMode is required");
        }
        if (request.paymentMode() != PaymentMode.CASH && !StringUtils.hasText(request.referenceNumber())) {
            throw new IllegalArgumentException("referenceNumber is required for non-cash payments");
        }
        PharmacyCashierShiftEntity activeShift = requireOpenShift(tenantId, actorAppUserId);
        PharmacySaleEntity sale = saleRepository.findByTenantIdAndId(tenantId, saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));
        if (request.amount().compareTo(sale.getDueAmount()) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed due amount");
        }
        PharmacySalePaymentEntity payment = salePaymentRepository.save(PharmacySalePaymentEntity.create(
                tenantId,
                saleId,
                activeShift.getId(),
                request.paymentDate() == null ? LocalDate.now() : request.paymentDate(),
                request.paymentDateTime() == null ? OffsetDateTime.now() : request.paymentDateTime(),
                money(request.amount()),
                request.paymentMode().name(),
                normalizeNullable(request.referenceNumber()),
                generateReceiptNumber(tenantId),
                normalizeNullable(request.notes()),
                actorAppUserId
        ));
        BigDecimal paidAmount = sale.getPaidAmount().add(payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dueAmount = sale.getTotal().subtract(paidAmount).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        sale.updateFinancials(sale.getSubtotal(), sale.getDiscount(), sale.getTax(), sale.getTotal(), paidAmount, dueAmount, statusForSale(sale.getTotal(), paidAmount, hasReturns(tenantId, saleId), isFullyReturned(tenantId, saleId)));
        saleRepository.save(sale);
        audit("pharmacy.sale.payment_recorded", tenantId, saleId, actorAppUserId, "Recorded pharmacy sale payment", Map.of(
                "paymentId", payment.getId(),
                "amount", payment.getAmount(),
                "receiptNumber", payment.getReceiptNumber()
        ));
        return mapSale(tenantId, sale);
    }

    @Transactional
    public PharmacyPosSaleResponse returnSale(UUID tenantId, UUID saleId, PharmacyPosReturnRequest request, UUID actorAppUserId) {
        requireId(saleId, "saleId");
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new IllegalArgumentException("reason is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("At least one return line is required");
        }
        PharmacySaleEntity sale = saleRepository.findByTenantIdAndId(tenantId, saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));
        Map<UUID, PharmacySaleItemEntity> itemsById = saleItemRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, saleId).stream()
                .collect(Collectors.toMap(PharmacySaleItemEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<ReturnDraft> drafts = new ArrayList<>();
        BigDecimal returnGross = ZERO;
        BigDecimal returnDiscount = ZERO;
        BigDecimal returnTax = ZERO;
        BigDecimal returnValue = ZERO;
        for (PharmacyPosReturnLineRequest line : request.items()) {
            PharmacySaleItemEntity item = itemsById.get(line.saleItemId());
            if (item == null) {
                throw new IllegalArgumentException("Sale item not found for return");
            }
            int quantity = line.quantity() == null ? 0 : line.quantity();
            if (quantity <= 0) {
                throw new IllegalArgumentException("Return quantity must be positive");
            }
            int returnable = item.getQuantity() - item.getReturnedQuantity();
            if (quantity > returnable) {
                throw new IllegalArgumentException("Return quantity exceeds sold quantity for item");
            }
            BigDecimal gross = item.getUnitPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal discount = prorated(item.getDiscount(), item.getQuantity(), quantity);
            BigDecimal tax = prorated(item.getTax(), item.getQuantity(), quantity);
            BigDecimal value = gross.subtract(discount).add(tax).setScale(2, RoundingMode.HALF_UP);
            drafts.add(new ReturnDraft(item, quantity, gross, discount, tax, value, line.reusable()));
            returnGross = returnGross.add(gross).setScale(2, RoundingMode.HALF_UP);
            returnDiscount = returnDiscount.add(discount).setScale(2, RoundingMode.HALF_UP);
            returnTax = returnTax.add(tax).setScale(2, RoundingMode.HALF_UP);
            returnValue = returnValue.add(value).setScale(2, RoundingMode.HALF_UP);
        }

        String returnNumber = generateReturnNumber(tenantId);

        for (int index = 0; index < drafts.size(); index++) {
            ReturnDraft draft = drafts.get(index);
            PharmacySaleItemEntity item = draft.item();
            item.addReturnedQuantity(draft.quantity());
            saleItemRepository.save(item);
            BigDecimal refundAmount = draft.returnValue();
            saleReturnRepository.save(PharmacySaleReturnEntity.create(
                    tenantId,
                    saleId,
                    item.getId(),
                    returnNumber,
                    item.getMedicineId(),
                    item.getStockBatchId(),
                    draft.quantity(),
                    draft.grossAmount(),
                    draft.discountAmount(),
                    draft.taxAmount(),
                    refundAmount,
                    draft.reusable(),
                    normalize(request.reason()),
                    request.refundMode() == null ? null : request.refundMode().name(),
                    normalizeNullable(request.referenceNumber()),
                    normalizeNullable(request.notes()),
                    actorAppUserId
            ));
            if (draft.reusable()) {
                StockEntity batch = item.getStockBatchId() == null ? null : stockRepository.findByTenantIdAndId(tenantId, item.getStockBatchId()).orElse(null);
                if (batch == null) {
                    throw new IllegalArgumentException("Stock batch not found");
                }
                if (isExpired(batch)) {
                    throw new IllegalArgumentException("Batch expired and cannot be sold or dispensed.");
                }
                inventoryService.createTransaction(tenantId, new InventoryTransactionCommand(
                        item.getMedicineId(),
                        item.getStockBatchId(),
                        sale.getLocationId(),
                        null,
                        InventoryTransactionType.CUSTOMER_RETURN_IN,
                        draft.quantity(),
                        "Customer return " + returnNumber,
                        "PHARMACY_SALE_RETURN",
                        saleId,
                        actorAppUserId,
                        "Reusable customer return for sale " + sale.getSaleNumber()
                ), actorAppUserId);
            } else {
                inventoryService.createTransaction(tenantId, new InventoryTransactionCommand(
                        item.getMedicineId(),
                        item.getStockBatchId(),
                        sale.getLocationId(),
                        null,
                        InventoryTransactionType.CUSTOMER_RETURN_NON_SELLABLE,
                        draft.quantity(),
                        "Customer return " + returnNumber,
                        "PHARMACY_SALE_RETURN",
                        saleId,
                        actorAppUserId,
                        "Non-sellable customer return for sale " + sale.getSaleNumber()
                ), actorAppUserId);
            }
        }
        audit("pharmacy.sale.return_created", tenantId, saleId, actorAppUserId, "Processed pharmacy sale return", Map.of(
                "returnNumber", returnNumber,
                "returnValue", returnValue,
                "refundIssued", ZERO
        ));
        return mapSale(tenantId, sale);
    }

    @Transactional(readOnly = true)
    public PharmacyPosReceiptPdf generateReceipt(UUID tenantId, UUID saleId, UUID actorAppUserId) {
        PharmacyPosSaleResponse sale = getSale(tenantId, saleId);
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        audit("pharmacy.sale.receipt_generated", tenantId, saleId, actorAppUserId, "Generated pharmacy sale receipt", Map.of("saleNumber", sale.saleNumber()));
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float pageWidth = page.getMediaBox().getWidth();
                float margin = 42f;
                float y = page.getMediaBox().getHeight() - 42f;
                float contentWidth = pageWidth - (margin * 2);
                float rightColumnX = margin + 300f;

                y = drawText(content, safeClinicName(clinic), 18, margin, y, true);
                y = drawText(content, "PHARMACY SALE RECEIPT", 12, margin, y - 16f, true);
                y = drawText(content, "Sale No: " + sale.saleNumber(), 10, rightColumnX, y + 16f, false);
                y = drawText(content, "Date: " + PDF_DATE_TIME.format(sale.saleDateTime()), 10, rightColumnX, y + 4f, false);
                drawDivider(content, margin, y - 8f, contentWidth);
                y -= 24f;

                y = drawKeyValue(content, "Customer", defaultString(sale.patientName(), defaultString(sale.customerName(), "Walk-in customer")), margin, y, 11);
                y = drawKeyValue(content, "Mobile", defaultString(sale.customerMobile(), "-"), margin, y - 14f, 10);
                if (StringUtils.hasText(sale.prescriptionFileName())) {
                    y = drawKeyValue(content, "Prescription", sale.prescriptionFileName(), margin, y - 14f, 10);
                }
                y = drawKeyValue(content, "Status", sale.status(), rightColumnX, y + 28f, 10);
                y = drawKeyValue(content, "FEFO", "Non-expired earliest batch allocation", rightColumnX, y + 14f, 10);

                y -= 26f;
                float[] columns = {margin, margin + 250f, margin + 330f, margin + 410f, margin + 480f};
                drawTableHeader(content, columns, y, List.of("Medicine", "Batch", "Qty", "Rate", "Amount"));
                y -= 18f;
                for (PharmacyPosSaleItemResponse item : sale.items()) {
                    drawTableRow(content, columns, y, List.of(
                            defaultString(item.medicineName(), "Medicine"),
                            defaultString(item.batchNumber(), "-"),
                            String.valueOf(item.quantity()),
                            moneyText(item.unitPrice()),
                            moneyText(item.lineTotal())
                    ));
                    y -= 16f;
                }

                y -= 8f;
                drawDivider(content, margin, y, contentWidth);
                y -= 18f;
                y = drawAmountRow(content, "Subtotal", sale.subtotal(), rightColumnX, y, false);
                y = drawAmountRow(content, "Discount", sale.discount(), rightColumnX, y - 12f, false);
                y = drawAmountRow(content, "Tax", sale.tax(), rightColumnX, y - 12f, false);
                y = drawAmountRow(content, "Total", sale.total(), rightColumnX, y - 14f, true);
                y = drawAmountRow(content, "Paid", sale.paidAmount(), rightColumnX, y - 14f, false);
                y = drawAmountRow(content, "Due", sale.dueAmount(), rightColumnX, y - 12f, false);

                y -= 20f;
                if (!sale.payments().isEmpty()) {
                    y = drawText(content, "Payments", 10, margin, y, true);
                    y -= 14f;
                    for (PharmacyPosPaymentResponse payment : sale.payments()) {
                        drawTableRow(content, new float[]{margin, margin + 170f, margin + 300f, margin + 430f}, y, List.of(
                                defaultString(payment.receiptNumber(), "-"),
                                defaultString(payment.paymentMode() == null ? null : payment.paymentMode().name(), "NA"),
                                payment.paymentDateTime() == null ? defaultString(payment.paymentDate() == null ? null : payment.paymentDate().toString(), "-") : PDF_DATE_TIME.format(payment.paymentDateTime()),
                                moneyText(payment.amount())
                        ));
                        y -= 14f;
                    }
                    y -= 8f;
                }
                if (!sale.returns().isEmpty()) {
                    y = drawText(content, "Returns / Refundable Value", 10, margin, y, true);
                    y -= 14f;
                    for (PharmacyPosReturnResponse returned : sale.returns()) {
                        drawTableRow(content, new float[]{margin, margin + 150f, margin + 270f, margin + 360f, margin + 460f}, y, List.of(
                                defaultString(returned.returnNumber(), "-"),
                                defaultString(returned.refundMode() == null ? null : returned.refundMode().name(), "NA"),
                                "Qty " + returned.quantity(),
                                returned.reusable() ? "Reusable" : "Discard",
                                moneyText(returned.refundAmount())
                        ));
                        y -= 14f;
                    }
                }
                y -= 10f;
                drawDivider(content, margin, y, contentWidth);
                drawText(content, "Printed from CuraPilot Pharmacy POS", 8, margin, y - 14f, false);
            }
            document.save(output);
            return new PharmacyPosReceiptPdf(safeFilename(sale.saleNumber()) + "-receipt.pdf", output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate receipt PDF", ex);
        }
    }

    private PharmacyPosSaleResponse mapSale(UUID tenantId, PharmacySaleEntity sale) {
        List<PharmacySaleItemEntity> items = saleItemRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, sale.getId());
        List<PharmacySalePaymentEntity> payments = salePaymentRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, sale.getId());
        List<PharmacySaleReturnEntity> returns = saleReturnRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, sale.getId());
        Map<UUID, MedicineEntity> medicines = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .collect(Collectors.toMap(MedicineEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        PatientEntity patient = sale.getPatientId() == null ? null : patientRepository.findByTenantIdAndId(tenantId, sale.getPatientId()).orElse(null);
        return new PharmacyPosSaleResponse(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getPatientId(),
                patientName(patient),
                sale.getCustomerName(),
                sale.getCustomerMobile(),
                sale.getPrescriptionDocumentId(),
                sale.getPrescriptionFileName(),
                sale.getPrescriptionUploadedAt(),
                sale.getSaleDateTime(),
                sale.getSubtotal(),
                sale.getDiscount(),
                sale.getTax(),
                sale.getTotal(),
                sale.getPaidAmount(),
                sale.getDueAmount(),
                sale.getStatus(),
                sale.getNotes(),
                "Stock allocated by FEFO from the earliest non-expired batch, splitting across batches when required.",
                sale.getCreatedAt(),
                items.stream().map(item -> new PharmacyPosSaleItemResponse(
                        item.getId(),
                        item.getMedicineId(),
                        medicines.get(item.getMedicineId()) == null ? "Medicine" : medicines.get(item.getMedicineId()).getMedicineName(),
                        item.getStockBatchId(),
                        item.getBatchNumber(),
                        item.getExpiryDate(),
                        item.getQuantity(),
                        item.getReturnedQuantity(),
                        item.getUnitPrice(),
                        item.getDiscount(),
                        item.getTax(),
                        item.getLineTotal()
                )).toList(),
                payments.stream().map(payment -> new PharmacyPosPaymentResponse(
                        payment.getId(),
                        payment.getCashierShiftId(),
                        payment.getAmount(),
                        paymentModeOf(payment.getPaymentMode()),
                        payment.getReferenceNumber(),
                        payment.getReceiptNumber(),
                        payment.getPaymentDate(),
                        payment.getPaymentDateTime(),
                        payment.getCreatedAt()
                )).toList(),
                returns.stream().map(item -> new PharmacyPosReturnResponse(
                        item.getId(),
                        item.getReturnNumber(),
                        item.getSaleItemId(),
                        item.getMedicineId(),
                        item.getStockBatchId(),
                        item.getQuantity(),
                        item.getGrossAmount(),
                        item.getDiscountAmount(),
                        item.getTaxAmount(),
                        item.getRefundAmount(),
                        item.isReusable(),
                        item.getReason(),
                        paymentModeOf(item.getRefundMode()),
                        item.getReferenceNumber(),
                        item.getNotes(),
                        item.getCreatedAt()
                )).toList()
        );
    }

    private AllocationPlan planAllocation(UUID tenantId, UUID locationId, PharmacyPosSaleLineRequest line) {
        requireId(line.medicineId(), "medicineId");
        if (line.quantity() == null || line.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, line.medicineId())
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        List<StockEntity> allBatches = sortForFefo(stockRepository.findByTenantIdAndMedicineIdAndLocationId(tenantId, line.medicineId(), locationId));
        List<StockEntity> batches = stockRepository.findSellableBatchesForUpdate(tenantId, locationId, line.medicineId()).stream()
                .filter(stock -> !isExpired(stock))
                .toList();
        if (batches.isEmpty() && allBatches.stream().anyMatch(stock -> stock.isActive() && stock.getQuantityOnHand() > 0 && isExpired(stock))) {
            throw new IllegalArgumentException("Batch expired and cannot be sold or dispensed.");
        }
        int available = batches.stream().mapToInt(StockEntity::getQuantityOnHand).sum();
        if (available < line.quantity()) {
            throw new IllegalArgumentException("Insufficient stock for " + medicine.getMedicineName());
        }
        BigDecimal lineDiscount = money(line.discount());
        BigDecimal lineTax = money(line.tax());
        BigDecimal unitPrice = line.unitPrice() == null || line.unitPrice().compareTo(ZERO) <= 0
                ? preferredPrice(medicine, batches)
                : money(line.unitPrice());
        List<AllocationDraft> allocations = new ArrayList<>();
        int remaining = line.quantity();
        BigDecimal discountAllocated = ZERO;
        BigDecimal taxAllocated = ZERO;
        for (StockEntity batch : batches) {
            if (remaining <= 0) {
                break;
            }
            int quantity = Math.min(batch.getQuantityOnHand(), remaining);
            boolean last = remaining == quantity;
            BigDecimal discount = last ? lineDiscount.subtract(discountAllocated).setScale(2, RoundingMode.HALF_UP) : prorated(lineDiscount, line.quantity(), quantity);
            BigDecimal tax = last ? lineTax.subtract(taxAllocated).setScale(2, RoundingMode.HALF_UP) : prorated(lineTax, line.quantity(), quantity);
            BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = gross.subtract(discount).add(tax).setScale(2, RoundingMode.HALF_UP);
            allocations.add(new AllocationDraft(medicine, batch, quantity, unitPrice, discount, tax, lineTotal));
            discountAllocated = discountAllocated.add(discount).setScale(2, RoundingMode.HALF_UP);
            taxAllocated = taxAllocated.add(tax).setScale(2, RoundingMode.HALF_UP);
            remaining -= quantity;
        }
        return new AllocationPlan(allocations, unitPrice.multiply(BigDecimal.valueOf(line.quantity())).setScale(2, RoundingMode.HALF_UP), lineDiscount, lineTax);
    }

    private PharmacyPosMedicineResponse toMedicineResponse(MedicineEntity medicine, List<StockEntity> stocks) {
        List<StockEntity> usable = sortForFefo(stocks == null ? List.of() : stocks).stream()
                .filter(StockEntity::isActive)
                .filter(stock -> stock.getQuantityOnHand() > 0)
                .filter(stock -> !isExpired(stock))
                .toList();
        int totalAvailable = usable.stream().mapToInt(StockEntity::getQuantityOnHand).sum();
        LocalDate earliest = usable.stream().map(StockEntity::getExpiryDate).filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        return new PharmacyPosMedicineResponse(
                medicine.getId(),
                medicine.getMedicineName(),
                medicine.getGenericName(),
                medicine.getBrandName(),
                medicine.getBarcode(),
                medicine.getQrCode(),
                medicine.getExternalCode(),
                totalAvailable,
                preferredPrice(medicine, usable),
                money(medicine.getTaxRate()),
                earliest
        );
    }

    private ShiftTotals calculateShiftTotals(UUID tenantId, UUID shiftId) {
        BigDecimal cash = ZERO;
        BigDecimal upi = ZERO;
        BigDecimal card = ZERO;
        BigDecimal other = ZERO;
        for (PharmacySalePaymentEntity payment : salePaymentRepository.findByTenantIdAndCashierShiftIdOrderByCreatedAtAsc(tenantId, shiftId)) {
            PaymentBucket bucket = paymentBucket(payment.getPaymentMode());
            if (bucket == PaymentBucket.CASH) {
                cash = cash.add(payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
            } else if (bucket == PaymentBucket.UPI) {
                upi = upi.add(payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
            } else if (bucket == PaymentBucket.CARD) {
                card = card.add(payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
            } else {
                other = other.add(payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return new ShiftTotals(cash, upi, card, other);
    }

    private PharmacyPosShiftResponse mapShift(UUID tenantId, PharmacyCashierShiftEntity shift) {
        ShiftTotals expected = "OPEN".equals(shift.getStatus())
                ? calculateShiftTotals(tenantId, shift.getId())
                : new ShiftTotals(
                money(shift.getExpectedCashAmount()),
                money(shift.getExpectedUpiAmount()),
                money(shift.getExpectedCardAmount()),
                money(shift.getExpectedOtherAmount())
        );
        BigDecimal actualCash = money(shift.getActualCashAmount());
        BigDecimal actualUpi = money(shift.getActualUpiAmount());
        BigDecimal actualCard = money(shift.getActualCardAmount());
        BigDecimal actualOther = money(shift.getActualOtherAmount());
        return new PharmacyPosShiftResponse(
                shift.getId(),
                shift.getCashierUserId(),
                shift.getOpenedAt(),
                shift.getOpenedBy(),
                money(shift.getOpeningCashAmount()),
                shift.getClosedAt(),
                shift.getClosedBy(),
                shift.getStatus(),
                expected.cash(),
                expected.upi(),
                expected.card(),
                expected.other(),
                expected.total(),
                actualCash,
                actualUpi,
                actualCard,
                actualOther,
                actualCash.add(actualUpi).add(actualCard).add(actualOther).setScale(2, RoundingMode.HALF_UP),
                money(shift.getVarianceAmount()),
                shift.getOpenNotes(),
                shift.getCloseNotes(),
                shift.getCreatedAt(),
                shift.getUpdatedAt()
        );
    }

    private PharmacyCashierShiftEntity requireOpenShift(UUID tenantId, UUID actorAppUserId) {
        return cashierShiftRepository.findByTenantIdAndCashierUserIdAndStatus(tenantId, actorAppUserId, "OPEN")
                .orElseThrow(() -> new IllegalArgumentException("Open cashier shift required before collecting payment"));
    }

    private PharmacyPosBatchResponse toBatchResponse(StockEntity stock, InventoryLocationEntity location) {
        return new PharmacyPosBatchResponse(
                stock.getId(),
                stock.getMedicineId(),
                stock.getBatchNumber(),
                stock.getExpiryDate(),
                stock.getQuantityOnHand(),
                money(stock.getSellingPrice()),
                isExpired(stock),
                location == null ? null : location.getLocationName()
        );
    }

    private List<StockEntity> sortForFefo(Collection<StockEntity> stocks) {
        return stocks.stream()
                .sorted(Comparator
                        .comparing((StockEntity stock) -> stock.getExpiryDate() == null ? LocalDate.MAX : stock.getExpiryDate())
                        .thenComparing(StockEntity::getUpdatedAt))
                .toList();
    }

    private boolean matchesMedicine(String term, MedicineEntity medicine, List<StockEntity> stocks) {
        if (contains(medicine.getMedicineName(), term)
                || contains(medicine.getGenericName(), term)
                || contains(medicine.getBrandName(), term)
                || contains(medicine.getBarcode(), term)
                || contains(medicine.getQrCode(), term)
                || contains(medicine.getExternalCode(), term)) {
            return true;
        }
        return stocks != null && stocks.stream().anyMatch(stock ->
                contains(stock.getBatchNumber(), term)
                        || contains(stock.getBarcode(), term)
                        || contains(stock.getQrCode(), term)
                        || contains(stock.getExternalCode(), term)
        );
    }

    private BigDecimal preferredPrice(MedicineEntity medicine, List<StockEntity> batches) {
        return money(batches.stream()
                .map(StockEntity::getSellingPrice)
                .filter(value -> value != null && value.compareTo(ZERO) >= 0)
                .findFirst()
                .orElse(medicine.getDefaultPrice()));
    }

    private PatientEntity resolvePatient(UUID tenantId, UUID patientId) {
        if (patientId == null) {
            return null;
        }
        return patientRepository.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
    }

    private PharmacySalePrescriptionEntity findPrescription(UUID tenantId, UUID documentId) {
        return salePrescriptionRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription document not found"));
    }

    private PharmacySalePrescriptionEntity findAvailablePrescription(UUID tenantId, UUID documentId) {
        PharmacySalePrescriptionEntity prescription = findPrescription(tenantId, documentId);
        if (prescription.getLinkedSaleId() != null) {
            throw new IllegalArgumentException("Prescription document is already linked to another sale");
        }
        return prescription;
    }

    private InventoryLocationEntity ensureDefaultLocation(UUID tenantId) {
        return locationRepository.findByTenantIdAndDefaultLocationTrue(tenantId)
                .orElseGet(() -> locationRepository.findByTenantIdAndLocationNameIgnoreCase(tenantId, "Main Pharmacy")
                        .map(existing -> {
                            if (!existing.isDefaultLocation()) {
                                existing.update(existing.getLocationName(), existing.getLocationCode(), existing.getLocationType(), true, existing.isActive());
                                return locationRepository.save(existing);
                            }
                            return existing;
                        })
                        .orElseGet(() -> locationRepository.save(InventoryLocationEntity.create(tenantId, "Main Pharmacy", "MAIN_PHARMACY", "PHARMACY", true))));
    }

    private boolean hasReturns(UUID tenantId, UUID saleId) {
        return !saleReturnRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, saleId).isEmpty();
    }

    private boolean isFullyReturned(UUID tenantId, UUID saleId) {
        List<PharmacySaleItemEntity> items = saleItemRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, saleId);
        return !items.isEmpty() && items.stream().allMatch(item -> item.getReturnedQuantity() >= item.getQuantity());
    }

    private String statusForSale(BigDecimal total, BigDecimal paidAmount, boolean hasReturns, boolean fullyReturned) {
        if (fullyReturned) {
            return "RETURNED";
        }
        if (hasReturns) {
            return "PARTIALLY_RETURNED";
        }
        if (total.compareTo(ZERO) == 0) {
            return "PAID";
        }
        if (paidAmount.compareTo(ZERO) == 0) {
            return "UNPAID";
        }
        if (paidAmount.compareTo(total) >= 0) {
            return "PAID";
        }
        return "PARTIALLY_PAID";
    }

    private void validateCreateRequest(PharmacyPosCreateSaleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("At least one sale item is required");
        }
        for (PharmacyPosSaleLineRequest item : request.items()) {
            if (item == null || item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
        if (request.patientId() == null && !StringUtils.hasText(request.customerName())) {
            throw new IllegalArgumentException("customerName is required for walk-in sales");
        }
    }

    private void validatePrescriptionFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Prescription file is required");
        }
        if (file.getSize() > MAX_PRESCRIPTION_SIZE_BYTES) {
            throw new IllegalArgumentException("Prescription file must be 25 MB or smaller");
        }
        String fileName = sanitizeFilename(file.getOriginalFilename());
        String extension = fileExtension(fileName);
        String mediaType = normalizePrescriptionMediaType(file.getContentType(), fileName);
        validatePrescriptionFileType(extension, mediaType);
    }

    private String normalizePrescriptionMediaType(String mediaType, String filename) {
        String value = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
        if (value.equals("image/jpg")) {
            return "image/jpeg";
        }
        if (!value.isBlank()) {
            return value;
        }
        return PRESCRIPTION_MEDIA_TYPES.getOrDefault(fileExtension(filename), "application/octet-stream");
    }

    private void validatePrescriptionFileType(String extension, String mediaType) {
        if (!StringUtils.hasText(extension)) {
            throw new IllegalArgumentException("Prescription file extension is required");
        }
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Executable files are not allowed");
        }
        if (!PRESCRIPTION_MEDIA_TYPES.containsKey(extension)) {
            throw new IllegalArgumentException("Only PDF, JPG, JPEG, PNG, and WEBP files are allowed");
        }
        if (!PRESCRIPTION_MEDIA_TYPES.get(extension).equals(mediaType)) {
            throw new IllegalArgumentException("Prescription file type does not match the allowed MIME types");
        }
    }

    private String sanitizeFilename(String filename) {
        String value = filename == null ? "" : filename.trim();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Filename is required");
        }
        String normalized = value.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String fileExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        int index = lower.lastIndexOf('.');
        if (index < 0 || index == lower.length() - 1) {
            return "";
        }
        return lower.substring(index + 1);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to checksum prescription document", ex);
        }
    }

    private BigDecimal allocatePortion(BigDecimal totalRefund, BigDecimal totalReturnValue, BigDecimal currentValue, boolean last, List<BigDecimal> priorValues) {
        if (totalRefund.compareTo(ZERO) <= 0 || totalReturnValue.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        if (last) {
            BigDecimal allocated = priorValues.stream()
                    .map(value -> totalRefund.multiply(value).divide(totalReturnValue, 2, RoundingMode.HALF_UP))
                    .reduce(ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            return totalRefund.subtract(allocated).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        return totalRefund.multiply(currentValue).divide(totalReturnValue, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal prorated(BigDecimal amount, int baseQuantity, int targetQuantity) {
        if (amount == null || amount.compareTo(ZERO) == 0 || baseQuantity <= 0 || targetQuantity <= 0) {
            return ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(targetQuantity))
                .divide(BigDecimal.valueOf(baseQuantity), 2, RoundingMode.HALF_UP);
    }

    private boolean isExpired(StockEntity stock) {
        return stock.getExpiryDate() != null && stock.getExpiryDate().isBefore(LocalDate.now());
    }

    private String patientName(PatientEntity patient) {
        if (patient == null) {
            return null;
        }
        return (defaultString(patient.getFirstName(), "") + " " + defaultString(patient.getLastName(), "")).trim();
    }

    private void audit(String action, UUID tenantId, UUID entityId, UUID actorAppUserId, String message, Map<String, Object> details) {
        try {
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    "PHARMACY_SALE",
                    entityId,
                    action,
                    actorAppUserId,
                    OffsetDateTime.now(),
                    message,
                    objectMapper.writeValueAsString(details)
            ));
        } catch (JsonProcessingException ex) {
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    "PHARMACY_SALE",
                    entityId,
                    action,
                    actorAppUserId,
                    OffsetDateTime.now(),
                    message,
                    "{}"
            ));
        }
    }

    private String safeClinicName(ClinicProfileRecord clinic) {
        if (clinic == null) {
            return "Clinic";
        }
        if (StringUtils.hasText(clinic.displayName())) {
            return clinic.displayName();
        }
        return defaultString(clinic.clinicName(), "Clinic");
    }

    private float drawText(PDPageContentStream content, String text, int fontSize, float x, float y, boolean bold) throws IOException {
        content.beginText();
        content.setFont(bold ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD) : new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text == null ? "" : text);
        content.endText();
        return y;
    }

    private void drawDivider(PDPageContentStream content, float x, float y, float width) throws IOException {
        content.moveTo(x, y);
        content.lineTo(x + width, y);
        content.stroke();
    }

    private float drawKeyValue(PDPageContentStream content, String label, String value, float x, float y, int valueFontSize) throws IOException {
        drawText(content, label, 8, x, y, true);
        drawText(content, defaultString(value, "-"), valueFontSize, x, y - 11f, false);
        return y - 11f;
    }

    private void drawTableHeader(PDPageContentStream content, float[] columns, float y, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            drawText(content, values.get(index), 9, columns[index], y, true);
        }
    }

    private void drawTableRow(PDPageContentStream content, float[] columns, float y, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            drawText(content, defaultString(values.get(index), "-"), 8, columns[index], y, false);
        }
    }

    private float drawAmountRow(PDPageContentStream content, String label, BigDecimal amount, float x, float y, boolean bold) throws IOException {
        drawText(content, label, bold ? 10 : 9, x, y, bold);
        drawText(content, moneyText(amount), bold ? 10 : 9, x + 110f, y, bold);
        return y;
    }

    private String moneyText(BigDecimal value) {
        return "INR " + money(value).toPlainString();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("value is required");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private PaymentMode paymentModeOf(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return PaymentMode.valueOf(value);
    }

    private PaymentBucket paymentBucket(String value) {
        PaymentMode mode = paymentModeOf(value);
        if (mode == null) {
            return PaymentBucket.OTHER;
        }
        return switch (mode) {
            case CASH -> PaymentBucket.CASH;
            case CARD -> PaymentBucket.CARD;
            case UPI, PHONEPE, GOOGLE_PAY, PAYTM -> PaymentBucket.UPI;
            default -> PaymentBucket.OTHER;
        };
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private String safeFilename(String value) {
        return defaultString(value, "receipt").replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String generateSaleNumber(UUID tenantId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = "PS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
            if (saleRepository.findByTenantIdAndSaleNumber(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate sale number");
    }

    private String generateReceiptNumber(UUID tenantId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = "PSR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
            if (salePaymentRepository.findByTenantIdAndReceiptNumber(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate receipt number");
    }

    private String generateReturnNumber(UUID tenantId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = "PSRET-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            if (saleReturnRepository.findByTenantIdAndReturnNumber(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate return number");
    }

    private void requireId(UUID id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private record AllocationPlan(
            List<AllocationDraft> allocations,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount
    ) {
    }

    private record AllocationDraft(
            MedicineEntity medicine,
            StockEntity stock,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {
    }

    private record ReturnDraft(
            PharmacySaleItemEntity item,
            int quantity,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal returnValue,
            boolean reusable
    ) {
    }

    private record ShiftTotals(
            BigDecimal cash,
            BigDecimal upi,
            BigDecimal card,
            BigDecimal other
    ) {
        private BigDecimal total() {
            return cash.add(upi).add(card).add(other).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private enum PaymentBucket {
        CASH,
        UPI,
        CARD,
        OTHER
    }
}
