package com.deepthoughtnet.clinic.api.pharmacy;

import com.deepthoughtnet.clinic.api.inventory.service.PrescriptionDispensingService;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptEntity;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationRepository;
import com.deepthoughtnet.clinic.inventory.db.PurchaseOrderEntity;
import com.deepthoughtnet.clinic.inventory.db.PurchaseOrderRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierEntity;
import com.deepthoughtnet.clinic.inventory.db.SupplierRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierInvoiceEntity;
import com.deepthoughtnet.clinic.inventory.db.SupplierInvoiceRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationUpsertCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransferCommand;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineUpsertCommand;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.ocr.spi.OcrDocument;
import com.deepthoughtnet.clinic.ocr.spi.OcrProvider;
import com.deepthoughtnet.clinic.ocr.spi.OcrResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.ObjectProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PharmacyOperationsService {
    private static final int EXPIRY_WARNING_DAYS = 30;
    private static final Logger log = LoggerFactory.getLogger(PharmacyOperationsService.class);
    private static final Pattern SUPPLIER_NAME_PATTERN = Pattern.compile(".*[A-Za-z0-9].*");
    private static final Pattern GSTIN_PATTERN = Pattern.compile("^\\d{2}[A-Z]{5}\\d{4}[A-Z][A-Z0-9]Z[A-Z0-9]$");
    private static final Pattern INDIAN_MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern LETTER_OR_NUMBER_PATTERN = Pattern.compile(".*[A-Za-z0-9].*");
    private static final Pattern LETTER_ONLY_PATTERN = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern INVOICE_REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9/ _-]+$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9/_-]+$");
    private static final Pattern BATCH_PATTERN = Pattern.compile("^[A-Za-z0-9/_-]+$");

    private final InventoryService inventoryService;
    private final MedicineRepository medicineRepository;
    private final StockRepository stockRepository;
    private final SupplierRepository supplierRepository;
    private final PharmacyReconciliationRepository reconciliationRepository;
    private final InventoryLocationRepository locationRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PrescriptionDispensingService dispensingService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectStorageService storageService;
    private final ObjectProvider<OcrProvider> ocrProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public PharmacyOperationsService(
            InventoryService inventoryService,
            MedicineRepository medicineRepository,
            StockRepository stockRepository,
            SupplierRepository supplierRepository,
            PharmacyReconciliationRepository reconciliationRepository,
            InventoryLocationRepository locationRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            SupplierInvoiceRepository supplierInvoiceRepository,
            GoodsReceiptRepository goodsReceiptRepository,
            PrescriptionDispensingService dispensingService,
            AuditEventPublisher auditEventPublisher,
            ObjectStorageService storageService,
            ObjectProvider<OcrProvider> ocrProvider,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.inventoryService = inventoryService;
        this.medicineRepository = medicineRepository;
        this.stockRepository = stockRepository;
        this.supplierRepository = supplierRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.locationRepository = locationRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierInvoiceRepository = supplierInvoiceRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.dispensingService = dispensingService;
        this.auditEventPublisher = auditEventPublisher;
        this.storageService = storageService;
        this.ocrProvider = ocrProvider;
        this.objectMapper = objectMapper;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<MedicineRecord> searchMedicines(UUID tenantId, String codeOrTerm) {
        String term = normalize(codeOrTerm);
        List<MedicineRecord> medicines = inventoryService.listMedicines(tenantId);
        if (term.isBlank()) {
            return medicines;
        }
        return medicines.stream()
                .filter(medicine -> matchesMedicine(medicine, term))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockRecord> searchStocks(UUID tenantId, String codeOrTerm) {
        String term = normalize(codeOrTerm);
        List<StockRecord> stocks = inventoryService.listStocks(tenantId);
        if (term.isBlank()) {
            return stocks;
        }
        return stocks.stream()
                .filter(stock -> matchesStock(stock, term))
                .toList();
    }

    @Transactional
    public List<InventoryLocationRecord> listLocations(UUID tenantId) {
        return inventoryService.listLocations(tenantId).stream()
                .map(location -> new InventoryLocationRecord(location.id(), location.tenantId(), location.locationName(), location.locationCode(), location.locationType(), location.defaultLocation(), location.active(), location.createdAt(), location.updatedAt()))
                .toList();
    }

    @Transactional
    public InventoryLocationRecord saveLocation(UUID tenantId, UUID id, InventoryLocationUpsertRequest request, UUID actorAppUserId) {
        InventoryLocationUpsertCommand command = new InventoryLocationUpsertCommand(request.locationName(), request.locationCode(), request.locationType(), request.defaultLocation(), request.active());
        com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationRecord record = inventoryService.saveLocation(tenantId, id, command, actorAppUserId);
        return new InventoryLocationRecord(record.id(), record.tenantId(), record.locationName(), record.locationCode(), record.locationType(), record.defaultLocation(), record.active(), record.createdAt(), record.updatedAt());
    }

    public MedicineImportResult importMedicines(UUID tenantId, byte[] csvBytes, UUID actorAppUserId) {
        if (csvBytes == null || csvBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is required");
        }
        List<MedicineImportRowResult> rows = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        ImportDuplicateTracker duplicateTracker = buildDuplicateTracker(tenantId);

        try (CSVParser parser = CSVParser.parse(new String(csvBytes, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build())) {
            int rowNumber = 1;
            for (CSVRecord record : parser) {
                rowNumber++;
                try {
                    ParsedMedicineImportRow parsedRow = parseMedicineImportRow(record, rowNumber);
                    String duplicateMessage = duplicateTracker.duplicateMessage(parsedRow.command());
                    if (duplicateMessage != null) {
                        skipped++;
                        rows.add(new MedicineImportRowResult(rowNumber, parsedRow.medicineName(), "SKIPPED", duplicateMessage, null, null));
                        continue;
                    }
                    MedicineImportOutcome outcome = requiresNewTransactionTemplate.execute(status -> persistMedicineImportRow(tenantId, parsedRow, actorAppUserId));
                    duplicateTracker.registerSuccessfulImport(parsedRow.command());
                    rows.add(new MedicineImportRowResult(rowNumber, outcome.medicineName, outcome.status, outcome.message, outcome.medicineId, outcome.stockId));
                    switch (outcome.status) {
                        case "CREATED" -> created++;
                        case "UPDATED" -> updated++;
                        case "SKIPPED" -> skipped++;
                        default -> failed++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    String medicineName = value(record, "medicineName");
                    rows.add(new MedicineImportRowResult(rowNumber, medicineName, "FAILED", friendlyMessage(ex), null, null));
                }
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to parse medicine CSV");
        }

        String failedRowsCsv = buildFailedRowsCsv(rows);
        int total = created + updated + skipped + failed;
        log.info("Medicine CSV import summary tenantId={} totalRows={} successCount={} failedCount={}", tenantId, total, created + updated + skipped, failed);
        audit(
                "pharmacy.medicine.imported",
                "MEDICINE_IMPORT",
                tenantId,
                tenantId,
                actorAppUserId,
                "Imported medicine master CSV",
                Map.of(
                        "totalRows", total,
                        "createdCount", created,
                        "updatedCount", updated,
                        "skippedCount", skipped,
                        "failedCount", failed
                )
        );
        return new MedicineImportResult(total, created, updated, skipped, failed, rows, failedRowsCsv);
    }

    @Transactional(readOnly = true)
    public String medicineImportTemplateCsv() {
        try (StringWriter writer = new StringWriter(); CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(
                "medicineName",
                "genericName",
                "brandName",
                "category",
                "type",
                "strength",
                "unit",
                "defaultDosage",
                "defaultFrequency",
                "defaultDurationDays",
                "defaultTiming",
                "instructions",
                "manufacturer",
                "barcode",
                "qrCode",
                "externalCode",
                "defaultPrice",
                "taxPercent",
                "active"
        ).build())) {
            printer.printRecord(
                    "Paracetamol 650",
                    "Paracetamol",
                    "Dolo",
                    "Analgesic",
                    "Tablet",
                    "650",
                    "mg",
                    "1 tablet",
                    "Twice daily",
                    "5",
                    "AFTER_FOOD",
                    "Take after meals",
                    "Micro Labs",
                    "PARA-650-001",
                    "PARA-650-001",
                    "PARA-650-001",
                    "25.00",
                    "5",
                    "true"
            );
            printer.flush();
            return writer.toString();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate medicine template");
        }
    }

    @Transactional(readOnly = true)
    public List<SupplierRecord> listSuppliers(UUID tenantId) {
        return supplierRepository.findByTenantIdOrderBySupplierNameAsc(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional
    public SupplierRecord saveSupplier(UUID tenantId, UUID id, SupplierUpsertRequest request, UUID actorAppUserId) {
        validateSupplier(request);
        SupplierEntity entity;
        if (id == null) {
            ensureUniqueSupplier(tenantId, request, null);
            entity = SupplierEntity.create(tenantId, normalize(request.supplierName()));
        } else {
            entity = supplierRepository.findByTenantIdAndId(tenantId, id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
            ensureUniqueSupplier(tenantId, request, id);
        }
        entity.update(normalize(request.supplierName()), normalizeNullable(request.contactPerson()), normalizePhone(request.phone()), normalizeNullable(request.email()), normalizeNullable(request.gstNumber()), normalizeNullable(request.address()), normalizeNullable(request.notes()), request.active());
        SupplierEntity saved = supplierRepository.save(entity);
        return toRecord(saved);
    }

    @Transactional(readOnly = true)
    public List<PharmacyReconciliationRecord> listReconciliations(UUID tenantId) {
        Map<UUID, MedicineEntity> medicines = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .collect(Collectors.toMap(MedicineEntity::getId, m -> m, (a, b) -> a, LinkedHashMap::new));
        Map<UUID, SupplierEntity> suppliers = supplierRepository.findByTenantIdOrderBySupplierNameAsc(tenantId).stream()
                .collect(Collectors.toMap(SupplierEntity::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
        return reconciliationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(entity -> toRecord(entity, medicines.get(entity.getMedicineId()), suppliers.get(entity.getSupplierId())))
                .toList();
    }

    @Transactional
    public PharmacyReconciliationRecord createReconciliation(UUID tenantId, ReconciliationCreateRequest request, UUID actorAppUserId) {
        if (request == null || request.medicineId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Medicine is required");
        }
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, request.medicineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine not found"));
        ensureActiveMedicine(medicine, "Selected medicine is inactive");
        UUID locationId = resolveLocationId(tenantId, request.locationId());
        StockEntity stock = request.stockBatchId() == null ? null : stockRepository.findByTenantIdAndId(tenantId, request.stockBatchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock batch not found"));
        if (stock != null && !stock.getMedicineId().equals(medicine.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected stock batch does not belong to the selected medicine");
        }
        SupplierEntity supplier = request.supplierId() == null ? null : supplierRepository.findByTenantIdAndId(tenantId, request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        ensureActiveSupplier(supplier, "Inactive supplier cannot be used for reconciliation");
        int systemQuantity = stock != null ? stock.getQuantityOnHand() : stockRepository.findByTenantIdAndMedicineId(tenantId, medicine.getId()).stream().mapToInt(StockEntity::getQuantityOnHand).sum();
        if (request.physicalQuantity() != null && request.physicalQuantity() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Physical quantity cannot be negative");
        }
        PharmacyReconciliationEntity entity = reconciliationRepository.save(PharmacyReconciliationEntity.create(
                tenantId,
                medicine.getId(),
                stock == null ? null : stock.getId(),
                supplier == null ? null : supplier.getId(),
                locationId,
                systemQuantity,
                actorAppUserId
        ));
        if (request.physicalQuantity() != null || StringUtils.hasText(request.reason())) {
            int physical = request.physicalQuantity() == null ? systemQuantity : request.physicalQuantity();
            entity.captureCount(physical, physical - systemQuantity, normalizeNullable(request.reason()));
            entity = reconciliationRepository.save(entity);
        }
        auditReconciliation("pharmacy.reconciliation.created", tenantId, entity, actorAppUserId, "Created pharmacy reconciliation");
        return toRecord(entity, medicine, supplier);
    }

    @Transactional
    public PharmacyReconciliationRecord updateReconciliation(UUID tenantId, UUID id, ReconciliationCreateRequest request, UUID actorAppUserId) {
        PharmacyReconciliationEntity entity = reconciliationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation session not found"));
        if (!"DRAFT".equalsIgnoreCase(entity.getStatus()) && !"REJECTED".equalsIgnoreCase(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or rejected reconciliations can be updated");
        }
        if (request == null || request.medicineId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Medicine is required");
        }
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, request.medicineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine not found"));
        ensureActiveMedicine(medicine, "Selected medicine is inactive");
        UUID locationId = resolveLocationId(tenantId, request.locationId());
        StockEntity stock = request.stockBatchId() == null ? null : stockRepository.findByTenantIdAndId(tenantId, request.stockBatchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock batch not found"));
        if (stock != null && !stock.getMedicineId().equals(medicine.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected stock batch does not belong to the selected medicine");
        }
        SupplierEntity supplier = request.supplierId() == null ? null : supplierRepository.findByTenantIdAndId(tenantId, request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        ensureActiveSupplier(supplier, "Inactive supplier cannot be used for reconciliation");
        int systemQuantity = stock != null ? stock.getQuantityOnHand() : stockRepository.findByTenantIdAndMedicineId(tenantId, medicine.getId()).stream().mapToInt(StockEntity::getQuantityOnHand).sum();
        Integer physicalQuantity = request.physicalQuantity();
        Integer varianceQuantity = physicalQuantity == null ? entity.getVarianceQuantity() : physicalQuantity - systemQuantity;
        entity = reconciliationRepository.save(entity);
        entity.captureCount(physicalQuantity == null ? entity.getPhysicalQuantity() : physicalQuantity, varianceQuantity == null ? 0 : varianceQuantity, normalizeNullable(request.reason()));
        entity = reconciliationRepository.save(entity);
        auditReconciliation("pharmacy.reconciliation.updated", tenantId, entity, actorAppUserId, "Updated pharmacy reconciliation");
        return toRecord(entity, medicine, supplier);
    }

    @Transactional
    public PharmacyReconciliationRecord submitReconciliation(UUID tenantId, UUID id, UUID actorAppUserId) {
        PharmacyReconciliationEntity entity = reconciliationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation session not found"));
        if (!"DRAFT".equalsIgnoreCase(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft reconciliations can be submitted");
        }
        if (entity.getMedicineId() != null && entity.getPhysicalQuantity() == null) {
            entity.captureCount(entity.getSystemQuantity(), 0, entity.getReason());
        }
        entity.submit(actorAppUserId);
        PharmacyReconciliationEntity saved = reconciliationRepository.save(entity);
        auditReconciliation("pharmacy.reconciliation.submitted", tenantId, saved, actorAppUserId, "Submitted pharmacy reconciliation");
        return toRecord(saved,
                saved.getMedicineId() == null ? null : medicineRepository.findByTenantIdAndId(tenantId, saved.getMedicineId()).orElse(null),
                saved.getSupplierId() == null ? null : supplierRepository.findByTenantIdAndId(tenantId, saved.getSupplierId()).orElse(null));
    }

    @Transactional
    public PharmacyReconciliationRecord approveReconciliation(UUID tenantId, UUID id, ReconciliationDecisionRequest request, UUID actorAppUserId) {
        PharmacyReconciliationEntity entity = reconciliationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation session not found"));
        ensureSubmitted(entity);
        ensureDifferentReviewer(entity, actorAppUserId);
        entity.approve(actorAppUserId, normalizeNullable(request == null ? null : request.reason()));
        PharmacyReconciliationEntity saved = reconciliationRepository.save(entity);
        auditReconciliation("pharmacy.reconciliation.approved", tenantId, saved, actorAppUserId, "Approved pharmacy reconciliation");
        return toRecord(saved,
                saved.getMedicineId() == null ? null : medicineRepository.findByTenantIdAndId(tenantId, saved.getMedicineId()).orElse(null),
                saved.getSupplierId() == null ? null : supplierRepository.findByTenantIdAndId(tenantId, saved.getSupplierId()).orElse(null));
    }

    @Transactional
    public PharmacyReconciliationRecord rejectReconciliation(UUID tenantId, UUID id, ReconciliationDecisionRequest request, UUID actorAppUserId) {
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        PharmacyReconciliationEntity entity = reconciliationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation session not found"));
        ensureSubmitted(entity);
        ensureDifferentReviewer(entity, actorAppUserId);
        entity.reject(actorAppUserId, normalizeNullable(request.reason()));
        PharmacyReconciliationEntity saved = reconciliationRepository.save(entity);
        auditReconciliation("pharmacy.reconciliation.rejected", tenantId, saved, actorAppUserId, "Rejected pharmacy reconciliation");
        return toRecord(saved,
                saved.getMedicineId() == null ? null : medicineRepository.findByTenantIdAndId(tenantId, saved.getMedicineId()).orElse(null),
                saved.getSupplierId() == null ? null : supplierRepository.findByTenantIdAndId(tenantId, saved.getSupplierId()).orElse(null));
    }

    @Transactional
    public PharmacyReconciliationRecord postReconciliation(UUID tenantId, UUID id, ReconciliationPostRequest request, UUID actorAppUserId) {
        PharmacyReconciliationEntity entity = reconciliationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation session not found"));
        if (!"APPROVED".equalsIgnoreCase(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved reconciliations can be posted");
        }
        if (entity.getMedicineId() == null) {
            if (!StringUtils.hasText(entity.getExtractedRowsJson())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload and review extracted rows before posting this stock sheet.");
            }
            applyReviewedRows(tenantId, entity, request == null ? null : request.reason(), actorAppUserId);
        } else {
            applyCountAdjustment(tenantId, entity, request, actorAppUserId);
        }
        entity.post(actorAppUserId);
        PharmacyReconciliationEntity saved = reconciliationRepository.save(entity);
        auditReconciliation("pharmacy.reconciliation.posted", tenantId, saved, actorAppUserId, "Posted pharmacy reconciliation");
        return toRecord(saved,
                saved.getMedicineId() == null ? null : medicineRepository.findByTenantIdAndId(tenantId, saved.getMedicineId()).orElse(null),
                saved.getSupplierId() == null ? null : supplierRepository.findByTenantIdAndId(tenantId, saved.getSupplierId()).orElse(null));
    }

    @Transactional
    public ReconciliationUploadResponse uploadReconciliationSheet(UUID tenantId, UUID reconciliationId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sheet file is required");
        }
        PharmacyReconciliationEntity entity = reconciliationRepository.findByTenantIdAndId(tenantId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation session not found"));
        String filename = sanitizeFilename(file.getOriginalFilename());
        String storageKey = storageService.buildDocumentStorageKey(tenantId, "stock-sheet-" + filename);
        storageService.putObject(storageKey, file.getContentType() == null ? "application/octet-stream" : file.getContentType(), file.getBytes());
        List<OcrExtractionRowRecord> rows = extractSheetRows(file.getBytes(), filename, file.getContentType());
        String rowsJson = objectMapper.writeValueAsString(rows);
        BigDecimal confidence = rows.stream().map(OcrExtractionRowRecord::confidence).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!rows.isEmpty()) {
            confidence = confidence.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
        }
        String extractionProvider = rows.isEmpty() ? "NONE" : "OCR";
        String extractionStatus = rows.isEmpty() ? "FAILED" : "EXTRACTED";
        entity.attachSheet(null, filename, file.getContentType(), storageKey, extractionStatus, extractionProvider, confidence, rowsJson);
        reconciliationRepository.save(entity);
        auditReconciliation(
                "pharmacy.reconciliation.sheet_uploaded",
                tenantId,
                entity,
                null,
                "Uploaded stock reconciliation sheet",
                Map.of(
                        "fileName", filename,
                        "extractionStatus", extractionStatus,
                        "extractionProvider", extractionProvider,
                        "rowCount", rows.size()
                )
        );
        return new ReconciliationUploadResponse(entity.getId(), filename, file.getContentType(), storageKey, extractionStatus, extractionProvider, confidence, rows);
    }

    @Transactional
    public PharmacyReconciliationRecord reviewReconciliationSheet(UUID tenantId, UUID reconciliationId, StockSheetReviewRequest request, UUID actorAppUserId) {
        PharmacyReconciliationEntity entity = reconciliationRepository.findByTenantIdAndId(tenantId, reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation session not found"));
        String rowsJson = serializeSheetRows(request == null ? List.of() : request.rows());
        entity.reviewExtraction(rowsJson);
        if (request != null && StringUtils.hasText(request.reviewNote())) {
            entity.captureCount(entity.getPhysicalQuantity(), entity.getVarianceQuantity() == null ? 0 : entity.getVarianceQuantity(), normalizeNullable(request.reviewNote()));
        }
        reconciliationRepository.save(entity);
        Map<String, Object> reviewAuditDetails = new LinkedHashMap<>();
        reviewAuditDetails.put("rowCount", request == null || request.rows() == null ? 0 : request.rows().size());
        reviewAuditDetails.put("reviewNote", normalizeNullable(request == null ? null : request.reviewNote()));
        auditReconciliation(
                "pharmacy.reconciliation.sheet_reviewed",
                tenantId,
                entity,
                actorAppUserId,
                "Reviewed stock reconciliation extraction",
                reviewAuditDetails
        );
        SupplierEntity supplier = entity.getSupplierId() == null ? null : supplierRepository.findByTenantIdAndId(tenantId, entity.getSupplierId()).orElse(null);
        MedicineEntity medicine = entity.getMedicineId() == null ? null : medicineRepository.findByTenantIdAndId(tenantId, entity.getMedicineId()).orElse(null);
        return toRecord(entity, medicine, supplier);
    }

    @Transactional
    public PharmacyReconciliationRecord confirmReconciliation(UUID tenantId, UUID id, ReconciliationConfirmRequest request, UUID actorAppUserId) {
        return postReconciliation(tenantId, id, new ReconciliationPostRequest(request == null ? null : request.physicalQuantity(), request == null ? null : request.reason()), actorAppUserId);
    }

    @Transactional
    public StockRecord inwardStock(UUID tenantId, StockInwardRequest request, UUID actorAppUserId) {
        if (request == null || request.medicineId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Medicine is required");
        }
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, request.medicineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine not found"));
        ensureActiveMedicine(medicine, "Inactive medicine cannot receive new stock");
        SupplierEntity supplier = request.supplierId() == null ? null : supplierRepository.findByTenantIdAndId(tenantId, request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        ensureActiveSupplier(supplier, "Inactive supplier cannot be used for stock inward");
        validateStockInwardFields(request);
        LocalDate expiryDate = parseDate(request.expiryDate(), "expiryDate");
        LocalDate purchaseDate = parseDate(request.purchaseDate(), "purchaseDate");
        String batchNumber = normalizeNullable(request.batchNumber());
        String purchaseReferenceNumber = normalizeNullable(request.purchaseReferenceNumber());
        int quantity = request.quantity();

        UUID locationId = resolveLocationId(tenantId, request.locationId());
        StockEntity existing = findStockBatch(tenantId, medicine.getId(), locationId, batchNumber, purchaseReferenceNumber).orElse(null);
        String barcode = normalizeNullable(request.barcode());
        String qrCode = normalizeNullable(request.qrCode());
        String externalCode = normalizeNullable(request.externalCode());
        Integer lowStockThreshold = request.lowStockThreshold() == null ? (existing == null ? null : existing.getLowStockThreshold()) : request.lowStockThreshold();
        BigDecimal unitCost = request.unitCost() == null ? (existing == null ? null : existing.getUnitCost()) : request.unitCost();
        BigDecimal sellingPrice = request.sellingPrice() == null ? (existing == null ? null : existing.getSellingPrice()) : request.sellingPrice();
        StockUpsertCommand command = new StockUpsertCommand(
                medicine.getId(),
                locationId,
                barcode,
                qrCode,
                externalCode,
                batchNumber,
                purchaseReferenceNumber,
                expiryDate,
                purchaseDate,
                supplier == null ? null : supplier.getSupplierName(),
                existing == null ? quantity : existing.getQuantityReceived() + quantity,
                existing == null ? 0 : existing.getQuantityOnHand(),
                lowStockThreshold,
                unitCost,
                unitCost,
                sellingPrice,
                true
        );
        StockRecord saved = existing == null
                ? inventoryService.createStock(tenantId, command, actorAppUserId)
                : inventoryService.updateStock(tenantId, existing.getId(), command, actorAppUserId);

        InventoryTransactionType inwardTransactionType = StringUtils.hasText(request.purchaseReferenceNumber())
                ? InventoryTransactionType.PURCHASE
                : InventoryTransactionType.STOCK_IN;
        if (quantity > 0 && existing == null) {
            inventoryService.createTransaction(
                    tenantId,
                    new InventoryTransactionCommand(
                            medicine.getId(),
                            saved.id(),
                            locationId,
                            null,
                            inwardTransactionType,
                            quantity,
                            "Opening stock inward",
                            "STOCK_INWARD",
                            null,
                            actorAppUserId,
                            request.purchaseReferenceNumber()
                    ),
                    actorAppUserId
            );
        } else if (quantity > 0) {
            inventoryService.createTransaction(
                    tenantId,
                    new InventoryTransactionCommand(
                            medicine.getId(),
                            saved.id(),
                            locationId,
                            null,
                            inwardTransactionType,
                            quantity,
                            "Purchase stock inward",
                            "STOCK_INWARD",
                            null,
                            actorAppUserId,
                            request.purchaseReferenceNumber()
                    ),
                    actorAppUserId
            );
        }
        return inventoryService.findStock(tenantId, saved.id()).orElse(saved);
    }

    @Transactional
    public InventoryTransactionRecord transferStock(UUID tenantId, InventoryTransferCommand request, UUID actorAppUserId) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer request is required");
        }
        return inventoryService.transferStock(tenantId, request, actorAppUserId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderRecord> listPurchaseOrders(UUID tenantId) {
        return purchaseOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(entity -> toRecord(entity, supplierRepository.findByTenantIdAndId(tenantId, entity.getSupplierId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderRecord getPurchaseOrder(UUID tenantId, UUID id) {
        PurchaseOrderEntity entity = purchaseOrderRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
        SupplierEntity supplier = supplierRepository.findByTenantIdAndId(tenantId, entity.getSupplierId()).orElse(null);
        return toRecord(entity, supplier);
    }

    @Transactional
    public PurchaseOrderRecord savePurchaseOrder(UUID tenantId, PurchaseOrderRequest request, UUID actorAppUserId) {
        if (request == null || request.supplierId() == null || !StringUtils.hasText(request.poNumber()) || !StringUtils.hasText(request.orderDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier, PO number, and order date are required");
        }
        SupplierEntity supplier = supplierRepository.findByTenantIdAndId(tenantId, request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        ensureActiveSupplier(supplier, "Inactive supplier cannot be used for procurement");
        validatePurchaseOrder(tenantId, request);
        String itemsJson = serializeItems(request.items());
        PurchaseOrderEntity entity = purchaseOrderRepository.findByTenantIdAndPoNumberIgnoreCase(tenantId, request.poNumber())
                .orElseGet(() -> PurchaseOrderEntity.create(tenantId, supplier.getId(), normalize(request.poNumber()), parseDate(request.orderDate(), "orderDate"), parseDate(request.expectedDeliveryDate(), "expectedDeliveryDate"), itemsJson, actorAppUserId));
        entity.upsertHeaderAndItems(
                supplier.getId(),
                normalize(request.poNumber()),
                parseDate(request.orderDate(), "orderDate"),
                parseDate(request.expectedDeliveryDate(), "expectedDeliveryDate"),
                itemsJson
        );
        entity.review(matchStatusForPurchaseOrder(itemsJson, null), null, normalizeNullable(request.approvalNote()));
        PurchaseOrderEntity saved = purchaseOrderRepository.save(entity);
        return toRecord(saved, supplier);
    }

    @Transactional
    public PurchaseOrderRecord cancelPurchaseOrder(UUID tenantId, UUID id, String reason, UUID actorAppUserId) {
        if (!StringUtils.hasText(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancel reason is required");
        }
        PurchaseOrderEntity entity = purchaseOrderRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
        SupplierEntity supplier = supplierRepository.findByTenantIdAndId(tenantId, entity.getSupplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        entity.review("CANCELLED", entity.getVarianceSummary(), normalizeNullable("CANCELLED:" + reason.trim()));
        PurchaseOrderEntity saved = purchaseOrderRepository.save(entity);
        return toRecord(saved, supplier);
    }

    @Transactional(readOnly = true)
    public List<SupplierInvoiceRecord> listSupplierInvoices(UUID tenantId) {
        return supplierInvoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(entity -> toRecord(entity, supplierRepository.findByTenantIdAndId(tenantId, entity.getSupplierId()).orElse(null)))
                .toList();
    }

    @Transactional
    public SupplierInvoiceRecord saveSupplierInvoice(UUID tenantId, SupplierInvoiceRequest request, UUID actorAppUserId) {
        if (request == null || request.supplierId() == null || !StringUtils.hasText(request.invoiceNumber()) || !StringUtils.hasText(request.invoiceDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier, invoice number, and invoice date are required");
        }
        SupplierEntity supplier = supplierRepository.findByTenantIdAndId(tenantId, request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        ensureActiveSupplier(supplier, "Inactive supplier cannot be used for procurement");
        PurchaseOrderEntity po = request.purchaseOrderId() == null ? null : purchaseOrderRepository.findByTenantIdAndId(tenantId, request.purchaseOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
        validateSupplierInvoice(tenantId, request);
        String itemsJson = serializeItems(request.items());
        SupplierInvoiceEntity entity = supplierInvoiceRepository.findByTenantIdAndInvoiceNumberIgnoreCase(tenantId, request.invoiceNumber())
                .orElseGet(() -> SupplierInvoiceEntity.create(tenantId, supplier.getId(), po == null ? null : po.getId(), normalize(request.invoiceNumber()), parseDate(request.invoiceDate(), "invoiceDate"), request.taxAmount(), request.totalAmount(), itemsJson, actorAppUserId));
        entity.review(matchStatusForInvoice(po, itemsJson), varianceSummaryForInvoice(po, itemsJson), normalizeNullable(request.approvalNote()));
        SupplierInvoiceEntity saved = supplierInvoiceRepository.save(entity);
        return toRecord(saved, supplier);
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptRecord> listGoodsReceipts(UUID tenantId) {
        return goodsReceiptRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(entity -> toRecord(
                        entity,
                        supplierRepository.findByTenantIdAndId(tenantId, entity.getSupplierId()).orElse(null),
                        entity.getLocationId() == null ? null : locationRepository.findByTenantIdAndId(tenantId, entity.getLocationId()).orElse(null)
                ))
                .toList();
    }

    @Transactional
    public GoodsReceiptRecord saveGoodsReceipt(UUID tenantId, GoodsReceiptRequest request, UUID actorAppUserId) {
        if (request == null || request.supplierId() == null || !StringUtils.hasText(request.receiptNumber()) || !StringUtils.hasText(request.receivedAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier, receipt number, and received time are required");
        }
        SupplierEntity supplier = supplierRepository.findByTenantIdAndId(tenantId, request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        ensureActiveSupplier(supplier, "Inactive supplier cannot be used for procurement");
        PurchaseOrderEntity po = request.purchaseOrderId() == null ? null : purchaseOrderRepository.findByTenantIdAndId(tenantId, request.purchaseOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
        SupplierInvoiceEntity invoice = request.supplierInvoiceId() == null ? null : supplierInvoiceRepository.findByTenantIdAndId(tenantId, request.supplierInvoiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier invoice not found"));
        InventoryLocationEntity location = locationRepository.findByTenantIdAndId(tenantId, resolveLocationId(tenantId, request.locationId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found"));
        validateGoodsReceipt(tenantId, request);
        String itemsJson = serializeItems(request.items());
        GoodsReceiptEntity entity = goodsReceiptRepository.findByTenantIdAndReceiptNumberIgnoreCase(tenantId, request.receiptNumber())
                .orElseGet(() -> GoodsReceiptEntity.create(tenantId, supplier.getId(), po == null ? null : po.getId(), invoice == null ? null : invoice.getId(), normalize(request.receiptNumber()), parseDateTime(request.receivedAt()), location.getId(), itemsJson, actorAppUserId));
        entity.review(matchStatusForReceipt(po, invoice, itemsJson), varianceSummaryForReceipt(po, invoice, itemsJson), normalizeNullable(request.approvalNote()));
        GoodsReceiptEntity saved = goodsReceiptRepository.save(entity);
        return toRecord(saved, supplier, location);
    }

    @Transactional
    public GoodsReceiptRecord confirmGoodsReceipt(UUID tenantId, UUID id, String approvalNote, UUID actorAppUserId) {
        GoodsReceiptEntity entity = goodsReceiptRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goods receipt not found"));
        SupplierEntity supplier = supplierRepository.findByTenantIdAndId(tenantId, entity.getSupplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        InventoryLocationEntity location = locationRepository.findByTenantIdAndId(tenantId, entity.getLocationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found"));
        List<ProcurementLineRequest> items = deserializeItems(entity.getItemsJson());
        for (ProcurementLineRequest item : items) {
            if (item.medicineId() == null) {
                continue;
            }
            MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, item.medicineId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine not found"));
            ensureActiveMedicine(medicine, "Inactive medicine cannot receive stock");
            validateProcurementLine(tenantId, item);
            StockEntity existing = findStockBatch(tenantId, medicine.getId(), location.getId(), item.batchNumber(), entity.getReceiptNumber()).orElse(null);
            StockUpsertCommand command = new StockUpsertCommand(
                    medicine.getId(),
                    location.getId(),
                    null,
                    null,
                    null,
                    normalizeNullable(item.batchNumber()),
                    entity.getReceiptNumber(),
                    parseDate(item.expiryDate(), "expiryDate"),
                    entity.getReceivedAt().toLocalDate(),
                    supplier.getSupplierName(),
                    existing == null ? item.quantity() : existing.getQuantityReceived() + item.quantity(),
                    existing == null ? 0 : existing.getQuantityOnHand(),
                    null,
                    item.unitCost(),
                    item.unitCost(),
                    item.sellingPrice(),
                    true
            );
            StockRecord stock = existing == null
                    ? inventoryService.createStock(tenantId, command, actorAppUserId)
                    : inventoryService.updateStock(tenantId, existing.getId(), command, actorAppUserId);
            inventoryService.createTransaction(
                    tenantId,
                    new InventoryTransactionCommand(
                            medicine.getId(),
                            stock.id(),
                            location.getId(),
                            null,
                            InventoryTransactionType.PURCHASE,
                            item.quantity(),
                            "GRN",
                            "GRN",
                            entity.getId(),
                            actorAppUserId,
                            normalizeNullable(approvalNote)
                    ),
                    actorAppUserId
            );
        }
        entity.confirm(actorAppUserId);
        entity.review(entity.getMatchingStatus(), entity.getVarianceSummary(), normalizeNullable(approvalNote));
        GoodsReceiptEntity saved = goodsReceiptRepository.save(entity);
        return toRecord(saved, supplier, location);
    }

    @Transactional(readOnly = true)
    public PharmacyDashboardResponse dashboard(UUID tenantId) {
        List<MedicineRecord> medicines = inventoryService.listMedicines(tenantId);
        List<StockRecord> stocks = inventoryService.listStocks(tenantId);
        List<InventoryTransactionRecord> transactions = inventoryService.listTransactions(tenantId);
        List<com.deepthoughtnet.clinic.api.inventory.dto.PrescriptionDispenseResponse> dispensingQueue = dispensingService.queue(tenantId);
        int partiallyDispensed = (int) dispensingQueue.stream().filter(row -> row.lines().stream().anyMatch(line -> "PARTIALLY_DISPENSED".equalsIgnoreCase(line.status()))).count();
        int pendingDispensing = (int) dispensingQueue.stream().filter(row -> row.lines().stream().anyMatch(line -> "NOT_DISPENSED".equalsIgnoreCase(line.status()))).count();
        int todayDispensed = (int) transactions.stream()
                .filter(row -> row.transactionType() == InventoryTransactionType.DISPENSED)
                .filter(row -> row.createdAt() != null && row.createdAt().toLocalDate().isEqual(LocalDate.now()))
                .count();
        return new PharmacyDashboardResponse(
                medicines.size(),
                stocks.size(),
                inventoryService.listLowStock(tenantId).size(),
                inventoryService.listExpiredStocks(tenantId).size(),
                inventoryService.listExpiringStocks(tenantId, EXPIRY_WARNING_DAYS).size(),
                pendingDispensing,
                partiallyDispensed,
                todayDispensed,
                transactions.stream().limit(10).toList()
        );
    }

    @Transactional(readOnly = true)
    public PharmacyAnalyticsResponse analytics(UUID tenantId) {
        List<StockRecord> stocks = inventoryService.listStocks(tenantId);
        Map<UUID, Integer> dispensed = new HashMap<>();
        for (InventoryTransactionRecord transaction : inventoryService.listTransactions(tenantId)) {
            if (transaction.transactionType() == InventoryTransactionType.DISPENSED) {
                dispensed.merge(transaction.medicineId(), transaction.quantity(), Integer::sum);
            }
        }
        List<FastMovingMedicineRecord> fastMoving = dispensed.entrySet().stream()
                .map(entry -> {
                    MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, entry.getKey()).orElse(null);
                    int available = stocks.stream().filter(stock -> stock.medicineId().equals(entry.getKey())).mapToInt(StockRecord::quantityOnHand).sum();
                    return new FastMovingMedicineRecord(entry.getKey(), medicine == null ? null : medicine.getMedicineName(), entry.getValue(), available);
                })
                .sorted(Comparator.comparingInt(FastMovingMedicineRecord::dispensedQuantity).reversed())
                .limit(10)
                .toList();
        List<StockRecord> expiryRisk = inventoryService.listExpiringStocks(tenantId, EXPIRY_WARNING_DAYS);
        BigDecimal stockValue = stocks.stream()
                .filter(stock -> stock.unitCost() != null)
                .map(stock -> stock.unitCost().multiply(BigDecimal.valueOf(stock.quantityOnHand())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new PharmacyAnalyticsResponse(fastMoving, inventoryService.listLowStock(tenantId), expiryRisk, stockValue);
    }

    @Transactional(readOnly = true)
    public List<SubstituteSuggestionRecord> suggestSubstitutes(UUID tenantId, UUID medicineId) {
        MedicineEntity target = medicineRepository.findByTenantIdAndId(tenantId, medicineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine not found"));
        String generic = normalize(target.getGenericName());
        String strength = normalize(target.getStrength());
        String form = normalize(target.getDosageForm() == null ? target.getMedicineType() : target.getDosageForm());
        return medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .filter(m -> !m.getId().equals(target.getId()))
                .filter(MedicineEntity::isActive)
                .filter(m -> normalize(m.getGenericName()).equalsIgnoreCase(generic) || (!generic.isBlank() && normalize(m.getGenericName()).equalsIgnoreCase(generic)))
                .filter(m -> normalize(m.getStrength()).equalsIgnoreCase(strength) || (!strength.isBlank() && normalize(m.getStrength()).equalsIgnoreCase(strength)))
                .filter(m -> normalize(m.getDosageForm() == null ? m.getMedicineType() : m.getDosageForm()).equalsIgnoreCase(form))
                .map(medicine -> {
                    int available = stockRepository.findByTenantIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(tenantId, medicine.getId(), 0).stream()
                            .filter(stock -> !isExpired(stock))
                            .mapToInt(StockEntity::getQuantityOnHand)
                            .sum();
                    String expiryStatus = expiryStatus(stockRepository.findByTenantIdAndMedicineId(tenantId, medicine.getId()).stream()
                            .map(StockEntity::getExpiryDate)
                            .filter(date -> date != null)
                            .min(LocalDate::compareTo)
                            .orElse(null));
                    String availability = available <= 0 ? "OUT_OF_STOCK" : available <= 5 ? "LOW_STOCK" : "AVAILABLE";
                    return new SubstituteSuggestionRecord(medicine.getId(), medicine.getMedicineName(), medicine.getGenericName(), medicine.getBrandName(), medicine.getDosageForm(), medicine.getStrength(), available, availability, expiryStatus, stockRepository.findByTenantIdAndMedicineId(tenantId, medicine.getId()).stream().map(StockEntity::getExpiryDate).filter(date -> date != null).min(LocalDate::compareTo).map(LocalDate::toString).orElse(null));
                })
                .sorted(Comparator.comparingInt(SubstituteSuggestionRecord::availableQuantity).reversed())
                .limit(5)
                .toList();
    }

    private ParsedMedicineImportRow parseMedicineImportRow(CSVRecord record, int rowNumber) {
        String medicineName = required(record, "medicineName");
        String genericName = value(record, "genericName");
        String brandName = value(record, "brandName");
        String form = requiredAny(record, "form", "type");
        String strength = value(record, "strength");
        String category = value(record, "category");
        String unit = value(record, "unit");
        String manufacturer = value(record, "manufacturer");
        String defaultDosage = value(record, "defaultDosage");
        String defaultFrequency = value(record, "defaultFrequency");
        Integer defaultDurationDays = parseInteger(value(record, "defaultDurationDays"), "defaultDurationDays");
        String defaultTiming = value(record, "defaultTiming");
        String defaultInstructions = valueAny(record, "defaultInstructions", "instructions");
        BigDecimal defaultPrice = parseDecimal(value(record, "defaultPrice"), "defaultPrice");
        BigDecimal taxPercent = parseDecimal(valueAny(record, "taxPercent", "taxRate"), "taxPercent");
        String barcode = value(record, "barcode");
        String qrCode = value(record, "qrCode");
        String externalCode = value(record, "externalCode");
        boolean active = parseBoolean(value(record, "active"), true);

        MedicineUpsertCommand command = new MedicineUpsertCommand(
                medicineName,
                medicineTypeFromForm(form),
                barcode,
                qrCode,
                externalCode,
                genericName,
                brandName,
                category,
                form,
                strength,
                unit,
                manufacturer,
                defaultDosage,
                defaultFrequency,
                defaultDurationDays,
                defaultTiming,
                defaultInstructions,
                defaultPrice,
                taxPercent,
                active
        );

        ParsedStockImportData stockData = hasStockColumns(record)
                ? new ParsedStockImportData(
                        parseInteger(value(record, "quantityOnHand"), "quantityOnHand"),
                        parseInteger(value(record, "lowStockThreshold"), "lowStockThreshold"),
                        parseDecimal(value(record, "unitCost"), "unitCost"),
                        parseDecimal(value(record, "sellingPrice"), "sellingPrice"),
                        value(record, "batchNumber"),
                        value(record, "purchaseReferenceNumber"),
                        parseDate(value(record, "expiryDate"), "expiryDate"),
                        parseDate(value(record, "purchaseDate"), "purchaseDate"),
                        value(record, "supplierName"),
                        value(record, "barcode"),
                        value(record, "qrCode"),
                        value(record, "externalCode")
                )
                : null;
        return new ParsedMedicineImportRow(rowNumber, medicineName, command, stockData);
    }

    private MedicineImportOutcome persistMedicineImportRow(UUID tenantId, ParsedMedicineImportRow row, UUID actorAppUserId) {
        MedicineRecord saved = inventoryService.createMedicine(tenantId, row.command(), actorAppUserId);

        UUID stockId = null;
        if (row.stockData() != null) {
            StockUpsertCommand stockCommand = row.stockData().toCommand(tenantId, saved.id());
            StockRecord stockRecord = inventoryService.createStock(tenantId, stockCommand, actorAppUserId);
            stockId = stockRecord.id();
        }

        return new MedicineImportOutcome("CREATED", "Created medicine", saved.id(), stockId, saved.medicineName());
    }

    private StockUpsertCommand buildStockCommand(CSVRecord record, UUID tenantId, UUID medicineId, String medicineName, MedicineEntity existingMedicine, UUID actorAppUserId) {
        Integer quantityOnHand = parseInteger(value(record, "quantityOnHand"), "quantityOnHand");
        Integer lowStockThreshold = parseInteger(value(record, "lowStockThreshold"), "lowStockThreshold");
        BigDecimal unitCost = parseDecimal(value(record, "unitCost"), "unitCost");
        BigDecimal sellingPrice = parseDecimal(value(record, "sellingPrice"), "sellingPrice");
        String batchNumber = value(record, "batchNumber");
        String purchaseReferenceNumber = value(record, "purchaseReferenceNumber");
        LocalDate expiryDate = parseDate(value(record, "expiryDate"), "expiryDate");
        return new StockUpsertCommand(
                medicineId,
                resolveLocationId(tenantId, null),
                value(record, "barcode"),
                value(record, "qrCode"),
                value(record, "externalCode"),
                batchNumber,
                purchaseReferenceNumber,
                expiryDate,
                parseDate(value(record, "purchaseDate"), "purchaseDate"),
                value(record, "supplierName"),
                quantityOnHand == null ? 0 : quantityOnHand,
                quantityOnHand == null ? 0 : quantityOnHand,
                lowStockThreshold,
                unitCost,
                unitCost,
                sellingPrice,
                true
        );
    }

    private Optional<MedicineEntity> findMedicineForImport(UUID tenantId, String medicineName, String form, String strength) {
        String normalizedName = normalize(medicineName).toLowerCase(Locale.ROOT);
        String normalizedForm = normalizeNullable(form);
        String normalizedStrength = normalizeNullable(strength);
        return medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .filter(medicine -> normalize(medicine.getMedicineName()).toLowerCase(Locale.ROOT).equals(normalizedName))
                .filter(medicine -> !StringUtils.hasText(normalizedForm) || normalize(medicine.getDosageForm()).equalsIgnoreCase(normalizedForm) || normalize(medicine.getMedicineType()).equalsIgnoreCase(medicineTypeFromForm(form)))
                .filter(medicine -> !StringUtils.hasText(normalizedStrength) || normalize(medicine.getStrength()).equalsIgnoreCase(normalizedStrength))
                .findFirst();
    }

    private Optional<StockEntity> findStockForImport(UUID tenantId, UUID medicineId, StockUpsertCommand command) {
        if (StringUtils.hasText(command.batchNumber())) {
            return stockRepository.findByTenantIdAndMedicineId(tenantId, medicineId).stream()
                    .filter(stock -> Objects.equals(stock.getLocationId(), command.locationId()))
                    .filter(stock -> command.batchNumber().equalsIgnoreCase(normalizeNullable(stock.getBatchNumber())))
                    .findFirst();
        }
        if (StringUtils.hasText(command.purchaseReferenceNumber())) {
            return stockRepository.findByTenantIdAndMedicineId(tenantId, medicineId).stream()
                    .filter(stock -> Objects.equals(stock.getLocationId(), command.locationId()))
                    .filter(stock -> command.purchaseReferenceNumber().equalsIgnoreCase(normalizeNullable(stock.getPurchaseReferenceNumber())))
                    .findFirst();
        }
        return stockRepository.findByTenantIdAndMedicineId(tenantId, medicineId).stream()
                .filter(stock -> Objects.equals(stock.getLocationId(), command.locationId()))
                .filter(stock -> stock.getBatchNumber() == null && stock.getPurchaseReferenceNumber() == null)
                .findFirst();
    }

    private boolean hasStockColumns(CSVRecord record) {
        return Stream.of("batchNumber", "expiryDate", "quantityOnHand", "lowStockThreshold", "unitCost", "sellingPrice", "purchaseReferenceNumber")
                .anyMatch(name -> StringUtils.hasText(value(record, name)));
    }

    private String buildFailedRowsCsv(List<MedicineImportRowResult> rows) {
        List<MedicineImportRowResult> failedRows = rows.stream().filter(row -> "FAILED".equals(row.status())).toList();
        if (failedRows.isEmpty()) {
            return "";
        }
        try (StringWriter writer = new StringWriter(); CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader("rowNumber", "medicineName", "status", "message").build())) {
            for (MedicineImportRowResult row : failedRows) {
                printer.printRecord(row.rowNumber(), row.medicineName(), row.status(), row.message());
            }
            printer.flush();
            return writer.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean matchesMedicine(MedicineRecord medicine, String term) {
        return contains(medicine.medicineName(), term)
                || contains(medicine.genericName(), term)
                || contains(medicine.brandName(), term)
                || contains(medicine.category(), term)
                || contains(medicine.dosageForm(), term)
                || contains(medicine.strength(), term)
                || contains(medicine.barcode(), term)
                || contains(medicine.qrCode(), term)
                || contains(medicine.externalCode(), term);
    }

    private boolean matchesStock(StockRecord stock, String term) {
        return contains(stock.medicineName(), term)
                || contains(stock.batchNumber(), term)
                || contains(stock.purchaseReferenceNumber(), term)
                || contains(stock.barcode(), term)
                || contains(stock.qrCode(), term)
                || contains(stock.externalCode(), term)
                || contains(stock.supplierName(), term);
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private SupplierRecord toRecord(SupplierEntity entity) {
        return new SupplierRecord(entity.getId(), entity.getTenantId(), entity.getSupplierName(), entity.getContactPerson(), entity.getPhone(), entity.getEmail(), entity.getGstNumber(), entity.getAddress(), entity.getNotes(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private PharmacyReconciliationRecord toRecord(PharmacyReconciliationEntity entity, MedicineEntity medicine, SupplierEntity supplier) {
        String batchNumber = entity.getStockBatchId() == null
                ? null
                : stockRepository.findByTenantIdAndId(entity.getTenantId(), entity.getStockBatchId()).map(StockEntity::getBatchNumber).orElse(null);
        InventoryLocationEntity location = entity.getLocationId() == null ? null : locationRepository.findByTenantIdAndId(entity.getTenantId(), entity.getLocationId()).orElse(null);
        return new PharmacyReconciliationRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getMedicineId(),
                medicine == null ? null : medicine.getMedicineName(),
                entity.getStockBatchId(),
                batchNumber,
                entity.getSupplierId(),
                supplier == null ? null : supplier.getSupplierName(),
                entity.getSystemQuantity(),
                entity.getPhysicalQuantity(),
                entity.getVarianceQuantity(),
                entity.getReason(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getSubmittedBy(),
                entity.getSubmittedAt(),
                entity.getReviewedBy(),
                entity.getReviewDecision(),
                entity.getReviewReason(),
                entity.getPostedBy(),
                entity.getPostedAt(),
                entity.getAdjustedBy(),
                entity.getSheetDocumentId(),
                entity.getSheetFilename(),
                entity.getSheetMediaType(),
                entity.getSheetStorageKey(),
                entity.getExtractionStatus(),
                entity.getExtractionProvider(),
                entity.getExtractionConfidence(),
                deserializeOcrRows(entity.getExtractedRowsJson()),
                entity.getLocationId(),
                location == null ? null : location.getLocationName(),
                entity.getConfirmedAt(),
                entity.getReviewedAt(),
                entity.getAppliedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<OcrExtractionRowRecord> deserializeOcrRows(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<OcrExtractionRowRecord>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private boolean sameMedicine(MedicineEntity existing, MedicineUpsertCommand command) {
        return normalize(existing.getMedicineName()).equalsIgnoreCase(normalize(command.medicineName()))
                && normalize(existing.getMedicineType()).equalsIgnoreCase(normalize(command.medicineType()))
                && normalize(existing.getBarcode()).equalsIgnoreCase(normalize(command.barcode()))
                && normalize(existing.getQrCode()).equalsIgnoreCase(normalize(command.qrCode()))
                && normalize(existing.getExternalCode()).equalsIgnoreCase(normalize(command.externalCode()))
                && normalize(existing.getGenericName()).equalsIgnoreCase(normalize(command.genericName()))
                && normalize(existing.getBrandName()).equalsIgnoreCase(normalize(command.brandName()))
                && normalize(existing.getCategory()).equalsIgnoreCase(normalize(command.category()))
                && normalize(existing.getDosageForm()).equalsIgnoreCase(normalize(command.dosageForm()))
                && normalize(existing.getStrength()).equalsIgnoreCase(normalize(command.strength()))
                && normalize(existing.getUnit()).equalsIgnoreCase(normalize(command.unit()))
                && normalize(existing.getManufacturer()).equalsIgnoreCase(normalize(command.manufacturer()))
                && normalize(existing.getDefaultDosage()).equalsIgnoreCase(normalize(command.defaultDosage()))
                && normalize(existing.getDefaultFrequency()).equalsIgnoreCase(normalize(command.defaultFrequency()))
                && Objects.equals(existing.getDefaultDurationDays(), command.defaultDurationDays())
                && normalize(existing.getDefaultTiming()).equalsIgnoreCase(normalize(command.defaultTiming()))
                && normalize(existing.getDefaultInstructions()).equalsIgnoreCase(normalize(command.defaultInstructions()))
                && Objects.equals(existing.getDefaultPrice(), normalizeMoney(command.defaultPrice()))
                && Objects.equals(existing.getTaxRate(), normalizeMoney(command.taxRate()));
    }

    private String medicineTypeFromForm(String form) {
        if (!StringUtils.hasText(form)) {
            return "OTHER";
        }
        String normalized = form.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return switch (normalized) {
            case "TABLET" -> "TABLET";
            case "CAPSULE" -> "CAPSULE";
            case "SYRUP" -> "SYRUP";
            case "INJECTION" -> "INJECTION";
            case "DROP", "DROPS" -> "DROP";
            case "OINTMENT", "CREAM", "GEL" -> "OINTMENT";
            default -> "OTHER";
        };
    }

    private void validateSupplier(SupplierUpsertRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier request is required");
        }
        String supplierName = normalize(request.supplierName());
        if (!StringUtils.hasText(supplierName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier name is required");
        }
        if (supplierName.length() < 3 || supplierName.length() > 80 || !SUPPLIER_NAME_PATTERN.matcher(supplierName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier name must be 3 to 80 characters and include a letter or number");
        }
        if (StringUtils.hasText(request.gstNumber()) && !GSTIN_PATTERN.matcher(request.gstNumber().trim().toUpperCase(Locale.ROOT)).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GSTIN must be a valid 15-character Indian GST number");
        }
        if (StringUtils.hasText(request.contactPerson()) && (!LETTER_OR_NUMBER_PATTERN.matcher(request.contactPerson().trim()).matches() || request.contactPerson().trim().length() > 80)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact person must be 80 characters or fewer and include a letter or number");
        }
        if (!StringUtils.hasText(request.phone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone is required");
        }
        if (!INDIAN_MOBILE_PATTERN.matcher(normalizePhone(request.phone())).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone must be a valid 10-digit Indian mobile number");
        }
        if (StringUtils.hasText(request.email()) && request.email().trim().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be 120 characters or fewer");
        }
        if (StringUtils.hasText(request.email()) && !request.email().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        }
        if (StringUtils.hasText(request.address()) && request.address().trim().length() > 250) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address must be 250 characters or fewer");
        }
        if (StringUtils.hasText(request.notes()) && request.notes().trim().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notes must be 500 characters or fewer");
        }
    }

    private void ensureUniqueSupplier(UUID tenantId, SupplierUpsertRequest request, UUID id) {
        String normalizedName = normalize(request.supplierName());
        String normalizedGst = StringUtils.hasText(request.gstNumber()) ? request.gstNumber().trim().toUpperCase(Locale.ROOT) : null;
        String normalizedPhone = normalizePhone(request.phone());
        String normalizedEmail = StringUtils.hasText(request.email()) ? request.email().trim() : null;
        boolean duplicateName = id == null
                ? supplierRepository.findByTenantIdAndSupplierNameIgnoreCase(tenantId, normalizedName).isPresent()
                : supplierRepository.existsByTenantIdAndSupplierNameIgnoreCaseAndIdNot(tenantId, normalizedName, id);
        boolean duplicateGst = StringUtils.hasText(normalizedGst)
                && (id == null
                ? supplierRepository.findByTenantIdAndGstNumberIgnoreCase(tenantId, normalizedGst).isPresent()
                : supplierRepository.existsByTenantIdAndGstNumberIgnoreCaseAndIdNot(tenantId, normalizedGst, id));
        boolean duplicatePhone = StringUtils.hasText(normalizedPhone)
                && (id == null
                ? supplierRepository.findByTenantIdAndPhone(tenantId, normalizedPhone).isPresent()
                : supplierRepository.existsByTenantIdAndPhoneAndIdNot(tenantId, normalizedPhone, id));
        boolean duplicateEmail = StringUtils.hasText(normalizedEmail)
                && (id == null
                ? supplierRepository.findByTenantIdAndEmailIgnoreCase(tenantId, normalizedEmail).isPresent()
                : supplierRepository.existsByTenantIdAndEmailIgnoreCaseAndIdNot(tenantId, normalizedEmail, id));
        if (duplicateName || duplicateGst || duplicatePhone || duplicateEmail) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier already exists");
        }
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private void ensureUniqueSupplier(UUID tenantId, String supplierName, UUID id) {
        String normalized = normalize(supplierName);
        if (id == null) {
            if (supplierRepository.findByTenantIdAndSupplierNameIgnoreCase(tenantId, normalized).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier already exists");
            }
            return;
        }
        if (supplierRepository.existsByTenantIdAndSupplierNameIgnoreCaseAndIdNot(tenantId, normalized, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier already exists");
        }
    }

    private void ensureActiveSupplier(SupplierEntity supplier, String message) {
        if (supplier != null && !supplier.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void ensureActiveMedicine(MedicineEntity medicine, String message) {
        if (medicine != null && !medicine.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void validateStockInwardFields(StockInwardRequest request) {
        validateReferenceField(request.purchaseReferenceNumber(), 60, "Invoice number must be 60 characters or fewer and can include letters, numbers, dashes, underscores, slashes, and spaces.", INVOICE_REFERENCE_PATTERN, "invoice number");
        validateReferenceField(request.batchNumber(), 30, "GRN number must be 3 to 30 characters and use letters, numbers, dashes, underscores, or slashes.", BATCH_PATTERN, "GRN number", 3);
        if (StringUtils.hasText(request.barcode()) && !request.barcode().trim().matches("^\\d{8,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Barcode must be 8 to 20 digits");
        }
        if (StringUtils.hasText(request.qrCode()) && request.qrCode().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR code must be 100 characters or fewer");
        }
        if (StringUtils.hasText(request.externalCode()) && (request.externalCode().trim().length() > 50 || !CODE_PATTERN.matcher(request.externalCode().trim()).matches())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "External code must be 50 characters or fewer and use letters, numbers, dashes, underscores, or slashes");
        }
        if (request.lowStockThreshold() != null && request.lowStockThreshold() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Threshold cannot be negative");
        }
        if (request.unitCost() != null && request.unitCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit cost cannot be negative");
        }
        if (request.sellingPrice() != null && request.sellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selling price cannot be negative");
        }
        if (request.unitCost() != null && request.sellingPrice() != null && request.sellingPrice().compareTo(request.unitCost()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selling price cannot be less than unit cost.");
        }
        if (request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
        LocalDate purchaseDate = parseDate(request.purchaseDate(), "purchaseDate");
        if (purchaseDate != null && purchaseDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inward date cannot be in the future");
        }
        LocalDate expiryDate = parseDate(request.expiryDate(), "expiryDate");
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date cannot be in the past");
        }
    }

    private void validateReferenceField(String value, int maxLength, String message, Pattern pattern, String fieldLabel) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength || !pattern.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void validateReferenceField(String value, int maxLength, String message, Pattern pattern, String fieldLabel, int minLength) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldLabel + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() < minLength || trimmed.length() > maxLength || !pattern.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void validatePurchaseOrder(UUID tenantId, PurchaseOrderRequest request) {
        validateProcurementHeader(request.poNumber(), request.orderDate(), request.expectedDeliveryDate(), "PO number", "order date");
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one procurement line");
        }
        for (ProcurementLineRequest item : request.items()) {
            validateProcurementLine(tenantId, item);
        }
    }

    private void validateSupplierInvoice(UUID tenantId, SupplierInvoiceRequest request) {
        validateProcurementHeader(request.invoiceNumber(), request.invoiceDate(), null, "Invoice number", "invoice date");
        if (request.taxAmount() != null && request.taxAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tax amount cannot be negative");
        }
        if (request.totalAmount() != null && request.totalAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total amount cannot be negative");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one procurement line");
        }
        for (ProcurementLineRequest item : request.items()) {
            validateProcurementLine(tenantId, item);
        }
    }

    private void validateGoodsReceipt(UUID tenantId, GoodsReceiptRequest request) {
        validateProcurementHeader(request.receiptNumber(), request.receivedAt(), null, "Receipt number", "receivedAt");
        if (request.locationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one procurement line");
        }
        for (ProcurementLineRequest item : request.items()) {
            validateProcurementLine(tenantId, item);
        }
    }

    private void validateProcurementHeader(String reference, String date, String expectedDeliveryDate, String referenceLabel, String dateLabel) {
        if (!StringUtils.hasText(reference)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, referenceLabel + " is required");
        }
        if (reference.trim().length() > 60 || !INVOICE_REFERENCE_PATTERN.matcher(reference.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, referenceLabel + " must be 60 characters or fewer and can include letters, numbers, dashes, slashes, underscores, and spaces");
        }
        LocalDate parsedDate = parseDate(date, dateLabel);
        if (parsedDate != null && parsedDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, dateLabel + " cannot be in the future");
        }
        if (StringUtils.hasText(expectedDeliveryDate)) {
            LocalDate delivery = parseDate(expectedDeliveryDate, "expectedDeliveryDate");
            if (parsedDate != null && delivery != null && delivery.isBefore(parsedDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected delivery date must be on or after order date");
            }
        }
    }

    private void validateProcurementLine(UUID tenantId, ProcurementLineRequest item) {
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Procurement line is required");
        }
        if (!StringUtils.hasText(item.medicineName()) || item.medicineName().trim().length() > 100 || !LETTER_OR_NUMBER_PATTERN.matcher(item.medicineName().trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Line item name must be 1 to 100 characters and include a letter or number");
        }
        if (item.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qty must be a whole number greater than zero.");
        }
        if (item.quantity() > 999999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qty must be 999999 or less");
        }
        if (item.expectedUnitCost() != null && item.expectedUnitCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected/unit cost cannot be negative");
        }
        if (item.unitCost() != null && item.unitCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit cost cannot be negative");
        }
        if (item.taxPercent() != null && (item.taxPercent().compareTo(BigDecimal.ZERO) < 0 || item.taxPercent().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tax % must be between 0 and 100");
        }
        if (item.batchNumber() != null && (item.batchNumber().trim().length() > 30 || !BATCH_PATTERN.matcher(item.batchNumber().trim()).matches())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch must be 30 characters or fewer and use letters, numbers, dashes, underscores, or slashes");
        }
        if (item.expiryDate() != null) {
            LocalDate expiryDate = parseDate(item.expiryDate(), "expiryDate");
            if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date must be in the future");
            }
        }
        if (item.sellingPrice() != null && item.sellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selling price cannot be negative");
        }
        if (item.expectedUnitCost() != null && item.sellingPrice() != null && item.sellingPrice().compareTo(item.expectedUnitCost()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selling price cannot be less than expected/unit cost.");
        }
        if (item.unitCost() != null && item.sellingPrice() != null && item.sellingPrice().compareTo(item.unitCost()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selling price cannot be less than unit cost.");
        }
        if (item.medicineId() != null && tenantId != null) {
            MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, item.medicineId()).orElse(null);
            ensureActiveMedicine(medicine, "Inactive medicine cannot be used");
        }
    }

    private String friendlyMessage(String message) {
        return message == null || message.isBlank() ? "Import row failed" : message;
    }

    private String friendlyMessage(Throwable throwable) {
        if (throwable instanceof ResponseStatusException responseStatusException && StringUtils.hasText(responseStatusException.getReason())) {
            return responseStatusException.getReason();
        }
        return friendlyMessage(throwable == null ? null : throwable.getMessage());
    }

    private String required(CSVRecord record, String column) {
        String value = value(record, column);
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, column + " is required");
        }
        return value.trim();
    }

    private String requiredAny(CSVRecord record, String... columns) {
        String value = valueAny(record, columns);
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("/", columns) + " is required");
        }
        return value.trim();
    }

    private String value(CSVRecord record, String column) {
        try {
            return record.isMapped(column) ? record.get(column) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String valueAny(CSVRecord record, String... columns) {
        for (String column : columns) {
            String value = value(record, column);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer parseInteger(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be numeric");
        }
    }

    private BigDecimal parseDecimal(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be numeric");
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1" -> true;
            case "false", "no", "n", "0" -> false;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active must be true or false");
        };
    }

    private LocalDate parseDate(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be YYYY-MM-DD");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void auditReconciliation(String action, UUID tenantId, PharmacyReconciliationEntity entity, UUID actorAppUserId, String summary) {
        auditReconciliation(action, tenantId, entity, actorAppUserId, summary, Map.of());
    }

    private void auditReconciliation(String action, UUID tenantId, PharmacyReconciliationEntity entity, UUID actorAppUserId, String summary, Map<String, Object> extraDetails) {
        if (entity == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reconciliationId", entity.getId());
        details.put("status", entity.getStatus());
        details.put("medicineId", entity.getMedicineId());
        details.put("stockBatchId", entity.getStockBatchId());
        details.put("locationId", entity.getLocationId());
        details.put("supplierId", entity.getSupplierId());
        details.put("systemQuantity", entity.getSystemQuantity());
        details.put("physicalQuantity", entity.getPhysicalQuantity());
        details.put("varianceQuantity", entity.getVarianceQuantity());
        details.put("reviewDecision", entity.getReviewDecision());
        details.put("sheetFilename", entity.getSheetFilename());
        details.put("correlationId", currentCorrelationId());
        details.putAll(extraDetails);
        audit(action, "PHARMACY_RECONCILIATION", tenantId, entity.getId(), actorAppUserId, summary, details);
    }

    private void audit(String action, String entityType, UUID tenantId, UUID entityId, UUID actorAppUserId, String summary, Map<String, Object> details) {
        try {
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    entityType,
                    entityId,
                    action,
                    actorAppUserId,
                    OffsetDateTime.now(),
                    summary,
                    objectMapper.writeValueAsString(details)
            ));
        } catch (JsonProcessingException ex) {
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    entityType,
                    entityId,
                    action,
                    actorAppUserId,
                    OffsetDateTime.now(),
                    summary,
                    "{}"
            ));
        }
    }

    private String currentCorrelationId() {
        try {
            return RequestContextHolder.get() == null ? null : RequestContextHolder.get().correlationId();
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String sanitizeFilename(String filename) {
        String value = filename == null ? "" : filename.trim();
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required");
        }
        int index = value.lastIndexOf('/') ;
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private UUID resolveLocationId(UUID tenantId, UUID requestedLocationId) {
        if (requestedLocationId != null) {
            return locationRepository.findByTenantIdAndId(tenantId, requestedLocationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found"))
                    .getId();
        }
        return locationRepository.findByTenantIdAndDefaultLocationTrue(tenantId)
                .orElseGet(() -> locationRepository.save(InventoryLocationEntity.create(tenantId, "Main Pharmacy", "MAIN_PHARMACY", "PHARMACY", true)))
                .getId();
    }

    private List<OcrExtractionRowRecord> extractSheetRows(byte[] bytes, String filename, String mediaType) {
        String text = extractText(bytes, filename, mediaType);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<OcrExtractionRowRecord> rows = new ArrayList<>();
        String[] lines = text.split("\\R");
        int rowNumber = 1;
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || line.length() < 4) {
                continue;
            }
            if (looksLikeHeader(line)) {
                continue;
            }
            rows.add(parseSheetRow(rowNumber++, line));
        }
        return rows;
    }

    private String extractText(byte[] bytes, String filename, String mediaType) {
        OcrProvider provider = ocrProvider == null ? null : ocrProvider.getIfAvailable();
        if (provider != null) {
            try {
                provider.validateReady();
                OcrResult result = provider.extractText(new OcrDocument(filename, mediaType, bytes));
                return normalizeWhitespace(result.text());
            } catch (RuntimeException ex) {
                // fall through to the PDFBox fallback below
            }
        }
        String normalizedMediaType = normalize(mediaType);
        if (normalizedMediaType.equals("application/pdf")) {
            try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                stripper.setSortByPosition(true);
                return normalizeWhitespace(stripper.getText(pdf));
            } catch (Exception ex) {
                return "";
            }
        }
        return "";
    }

    private boolean looksLikeHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("medicine") && (lower.contains("qty") || lower.contains("quantity"))
                || lower.contains("batch") && lower.contains("expiry");
    }

    private OcrExtractionRowRecord parseSheetRow(int rowNumber, String line) {
        String cleaned = line.replace("|", " ").replace("\t", " ");
        String[] tokens = cleaned.split("\\s{2,}|,");
        String joined = cleaned.trim();
        String medicineName = tokens.length > 0 ? tokens[0].trim() : joined;
        String medicineCode = null;
        String batchNumber = null;
        Integer physicalQuantity = null;
        String expiryDate = null;
        String notes = null;
        BigDecimal confidence = BigDecimal.valueOf(0.55);

        for (String token : tokens) {
            String value = token.trim();
            if (value.isBlank()) {
                continue;
            }
            if (physicalQuantity == null && value.matches("^\\d+$")) {
                physicalQuantity = Integer.parseInt(value);
                continue;
            }
            if (expiryDate == null && value.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                expiryDate = value;
                continue;
            }
            if (expiryDate == null && value.matches("^\\d{2}[/-]\\d{2}[/-]\\d{4}$")) {
                try {
                    String normalized = value.replace('/', '-');
                    String[] parts = normalized.split("-");
                    expiryDate = parts[2] + "-" + parts[1] + "-" + parts[0];
                } catch (Exception ex) {
                    expiryDate = null;
                }
                continue;
            }
            if (batchNumber == null && value.length() <= 32 && value.matches("^[A-Za-z0-9._-]+$") && !value.equalsIgnoreCase(medicineName)) {
                batchNumber = value;
                continue;
            }
            if (medicineCode == null && value.length() <= 32 && value.matches("^[A-Za-z0-9._-]+$") && !value.equalsIgnoreCase(batchNumber)) {
                medicineCode = value;
            }
        }

        boolean needsReview = physicalQuantity == null || !StringUtils.hasText(batchNumber) || !StringUtils.hasText(expiryDate);
        if (physicalQuantity != null) {
            confidence = confidence.add(BigDecimal.valueOf(0.2));
        }
        if (StringUtils.hasText(batchNumber)) {
            confidence = confidence.add(BigDecimal.valueOf(0.1));
        }
        if (StringUtils.hasText(expiryDate)) {
            confidence = confidence.add(BigDecimal.valueOf(0.1));
        }
        if (confidence.compareTo(BigDecimal.ONE) > 0) {
            confidence = BigDecimal.ONE;
        }
        if (needsReview) {
            notes = "Needs review";
        }
        return new OcrExtractionRowRecord(rowNumber, medicineCode, medicineName, batchNumber, physicalQuantity, expiryDate, notes, confidence, needsReview);
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', '\n').replaceAll("\n{3,}", "\n\n").trim();
    }

    private OffsetDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ex) {
            try {
                return LocalDate.parse(value).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
            } catch (Exception ignored) {
                return OffsetDateTime.now();
            }
        }
    }

    private String serializeItems(List<ProcurementLineRequest> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to serialize procurement items");
        }
    }

    private String serializeSheetRows(List<OcrExtractionRowRecord> rows) {
        try {
            return objectMapper.writeValueAsString(rows == null ? List.of() : rows);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to serialize sheet rows");
        }
    }

    private List<ProcurementLineRequest> deserializeItems(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProcurementLineRequest>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void applyCountAdjustment(UUID tenantId, PharmacyReconciliationEntity entity, ReconciliationPostRequest request, UUID actorAppUserId) {
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, entity.getMedicineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine not found"));
        StockEntity stock = entity.getStockBatchId() == null ? null : stockRepository.findByTenantIdAndId(tenantId, entity.getStockBatchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock batch not found"));
        int physical = request != null && request.physicalQuantity() != null ? request.physicalQuantity() : (entity.getPhysicalQuantity() == null ? entity.getSystemQuantity() : entity.getPhysicalQuantity());
        int variance = physical - entity.getSystemQuantity();
        if (request != null && StringUtils.hasText(request.reason())) {
            entity.captureCount(physical, variance, normalizeNullable(request.reason()));
        } else if (entity.getPhysicalQuantity() == null || !Objects.equals(entity.getPhysicalQuantity(), physical) || !Objects.equals(entity.getVarianceQuantity(), variance)) {
            entity.captureCount(physical, variance, entity.getReason());
        }
        if (variance != 0 && stock != null) {
            inventoryService.createTransaction(
                    tenantId,
                    new InventoryTransactionCommand(
                            medicine.getId(),
                            stock.getId(),
                            stock.getLocationId(),
                            null,
                            variance > 0 ? InventoryTransactionType.ADJUSTMENT_IN : InventoryTransactionType.ADJUSTMENT_OUT,
                            Math.abs(variance),
                            entity.getReason(),
                            "RECONCILIATION",
                            entity.getId(),
                            actorAppUserId,
                            "Reconciliation adjustment"
                    ),
                    actorAppUserId
            );
        }
    }

    private void applyReviewedRows(UUID tenantId, PharmacyReconciliationEntity entity, String note, UUID actorAppUserId) {
        List<OcrExtractionRowRecord> rows = deserializeOcrRows(entity.getExtractedRowsJson());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No extracted stock sheet rows are available for review");
        }
        UUID locationId = resolveLocationId(tenantId, entity.getLocationId());
        List<String> variances = new ArrayList<>();
        for (OcrExtractionRowRecord row : rows) {
            MedicineEntity medicine = findMedicineForSheetRow(tenantId, row)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine not found for sheet row " + row.rowNumber()));
            StockEntity stock = findStockBatch(tenantId, medicine.getId(), locationId, row.batchNumber(), null).orElse(null);
            int physical = row.physicalQuantity() == null ? 0 : row.physicalQuantity();
            if (stock == null) {
                if (physical <= 0) {
                    variances.add("Row " + row.rowNumber() + ": missing stock batch and zero physical quantity");
                    continue;
                }
                StockUpsertCommand command = new StockUpsertCommand(
                        medicine.getId(),
                        locationId,
                        null,
                        null,
                        null,
                        normalizeNullable(row.batchNumber()),
                        null,
                        parseDate(row.expiryDate(), "expiryDate"),
                        entity.getCreatedAt() == null ? LocalDate.now() : entity.getCreatedAt().toLocalDate(),
                        null,
                        physical,
                        physical,
                        null,
                        null,
                        null,
                        null,
                        true
                );
                StockRecord saved = inventoryService.createStock(tenantId, command, actorAppUserId);
                inventoryService.createTransaction(
                        tenantId,
                        new InventoryTransactionCommand(
                                medicine.getId(),
                                saved.id(),
                                locationId,
                                null,
                                InventoryTransactionType.OPENING,
                                physical,
                                "Stock sheet reconciliation",
                                "RECONCILIATION",
                                entity.getId(),
                                actorAppUserId,
                                note == null ? row.notes() : note
                        ),
                        actorAppUserId
                );
                continue;
            }
            int variance = physical - stock.getQuantityOnHand();
            if (variance != 0) {
                inventoryService.createTransaction(
                        tenantId,
                        new InventoryTransactionCommand(
                                medicine.getId(),
                                stock.getId(),
                                locationId,
                                null,
                                variance > 0 ? InventoryTransactionType.ADJUSTMENT_IN : InventoryTransactionType.ADJUSTMENT_OUT,
                                Math.abs(variance),
                                "Stock sheet reconciliation",
                                "RECONCILIATION",
                                entity.getId(),
                                actorAppUserId,
                                note == null ? row.notes() : note
                        ),
                        actorAppUserId
                );
            }
        }
        entity.applyExtraction();
    }

    private void ensureSubmitted(PharmacyReconciliationEntity entity) {
        if (!"SUBMITTED".equalsIgnoreCase(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only submitted reconciliations can be reviewed");
        }
    }

    private void ensureDifferentReviewer(PharmacyReconciliationEntity entity, UUID reviewerId) {
        if (entity.getCreatedBy() != null && entity.getCreatedBy().equals(reviewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Maker cannot approve own reconciliation.");
        }
    }

    private Optional<MedicineEntity> findMedicineForSheetRow(UUID tenantId, OcrExtractionRowRecord row) {
        if (row == null) {
            return Optional.empty();
        }
        if (StringUtils.hasText(row.medicineCode())) {
            return medicineRepository.findByTenantIdAndBarcodeIgnoreCase(tenantId, row.medicineCode())
                    .or(() -> medicineRepository.findByTenantIdAndExternalCodeIgnoreCase(tenantId, row.medicineCode()));
        }
        if (StringUtils.hasText(row.medicineName())) {
            return medicineRepository.findByTenantIdAndMedicineNameIgnoreCase(tenantId, row.medicineName());
        }
        return Optional.empty();
    }

    private String matchStatusForPurchaseOrder(String itemsJson, String varianceSummary) {
        return StringUtils.hasText(varianceSummary) ? "REVIEW_REQUIRED" : "MATCHED";
    }

    private String matchStatusForInvoice(PurchaseOrderEntity purchaseOrder, String itemsJson) {
        if (purchaseOrder == null) {
            return "MISSING_PO";
        }
        return varianceSummaryForInvoice(purchaseOrder, itemsJson).isBlank() ? "MATCHED" : "REVIEW_REQUIRED";
    }

    private String matchStatusForReceipt(PurchaseOrderEntity purchaseOrder, SupplierInvoiceEntity invoice, String itemsJson) {
        if (purchaseOrder == null && invoice == null) {
            return "DIRECT_RECEIPT";
        }
        if (invoice == null) {
            return "MISSING_INVOICE";
        }
        return varianceSummaryForReceipt(purchaseOrder, invoice, itemsJson).isBlank() ? "MATCHED" : "REVIEW_REQUIRED";
    }

    private String varianceSummaryForInvoice(PurchaseOrderEntity purchaseOrder, String itemsJson) {
        if (purchaseOrder == null) {
            return "Missing purchase order linkage";
        }
        return Stream.of(
                compareQuantities(deserializeItems(purchaseOrder.getItemsJson()), deserializeItems(itemsJson)),
                compareCosts(deserializeItems(purchaseOrder.getItemsJson()), deserializeItems(itemsJson))
        ).filter(StringUtils::hasText).filter(value -> !"OK".equals(value)).collect(Collectors.joining(" | "));
    }

    private String varianceSummaryForReceipt(PurchaseOrderEntity purchaseOrder, SupplierInvoiceEntity invoice, String itemsJson) {
        String fromPo = purchaseOrder == null ? "Missing purchase order" : compareQuantities(deserializeItems(purchaseOrder.getItemsJson()), deserializeItems(itemsJson));
        String fromInvoice = invoice == null ? "Missing invoice" : compareQuantities(deserializeItems(invoice.getItemsJson()), deserializeItems(itemsJson));
        String priceVariance = invoice == null ? "Missing invoice" : compareCosts(deserializeItems(invoice.getItemsJson()), deserializeItems(itemsJson));
        return Stream.of(fromPo, fromInvoice, priceVariance).filter(StringUtils::hasText).filter(value -> !"OK".equals(value)).collect(Collectors.joining(" | "));
    }

    private String compareQuantities(List<ProcurementLineRequest> expected, List<ProcurementLineRequest> received) {
        Map<UUID, Integer> expectedQty = expected.stream().filter(item -> item.medicineId() != null).collect(Collectors.toMap(ProcurementLineRequest::medicineId, ProcurementLineRequest::quantity, Integer::sum));
        Map<UUID, Integer> receivedQty = received.stream().filter(item -> item.medicineId() != null).collect(Collectors.toMap(ProcurementLineRequest::medicineId, ProcurementLineRequest::quantity, Integer::sum));
        List<String> variances = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : expectedQty.entrySet()) {
            int actual = receivedQty.getOrDefault(entry.getKey(), 0);
            if (actual != entry.getValue()) {
                variances.add("Qty " + entry.getKey() + " expected " + entry.getValue() + " received " + actual);
            }
        }
        for (Map.Entry<UUID, Integer> entry : receivedQty.entrySet()) {
            if (!expectedQty.containsKey(entry.getKey())) {
                variances.add("Unexpected item " + entry.getKey() + " received " + entry.getValue());
            }
        }
        return variances.isEmpty() ? "OK" : String.join("; ", variances);
    }

    private String compareCosts(List<ProcurementLineRequest> expected, List<ProcurementLineRequest> received) {
        Map<UUID, BigDecimal> expectedCost = expected.stream()
                .filter(item -> item.medicineId() != null && item.expectedUnitCost() != null)
                .collect(Collectors.toMap(ProcurementLineRequest::medicineId, ProcurementLineRequest::expectedUnitCost, (left, right) -> right));
        Map<UUID, BigDecimal> receivedCost = received.stream()
                .filter(item -> item.medicineId() != null && item.unitCost() != null)
                .collect(Collectors.toMap(ProcurementLineRequest::medicineId, ProcurementLineRequest::unitCost, (left, right) -> right));
        List<String> variances = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : expectedCost.entrySet()) {
            BigDecimal actual = receivedCost.get(entry.getKey());
            if (actual != null && entry.getValue().compareTo(actual) != 0) {
                variances.add("Price " + entry.getKey() + " expected " + entry.getValue() + " received " + actual);
            }
        }
        return variances.isEmpty() ? "OK" : String.join("; ", variances);
    }

    private PurchaseOrderRecord toRecord(PurchaseOrderEntity entity, SupplierEntity supplier) {
        return new PurchaseOrderRecord(entity.getId(), entity.getTenantId(), entity.getSupplierId(), supplier == null ? null : supplier.getSupplierName(), entity.getPoNumber(), entity.getOrderDate() == null ? null : entity.getOrderDate().toString(), entity.getExpectedDeliveryDate() == null ? null : entity.getExpectedDeliveryDate().toString(), entity.getItemsJson(), entity.getMatchingStatus(), entity.getVarianceSummary(), entity.getApprovalNote(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private SupplierInvoiceRecord toRecord(SupplierInvoiceEntity entity, SupplierEntity supplier) {
        return new SupplierInvoiceRecord(entity.getId(), entity.getTenantId(), entity.getSupplierId(), supplier == null ? null : supplier.getSupplierName(), entity.getPurchaseOrderId(), entity.getInvoiceNumber(), entity.getInvoiceDate() == null ? null : entity.getInvoiceDate().toString(), entity.getTaxAmount(), entity.getTotalAmount(), entity.getItemsJson(), entity.getMatchingStatus(), entity.getVarianceSummary(), entity.getApprovalNote(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private GoodsReceiptRecord toRecord(GoodsReceiptEntity entity, SupplierEntity supplier, InventoryLocationEntity location) {
        return new GoodsReceiptRecord(entity.getId(), entity.getTenantId(), entity.getSupplierId(), supplier == null ? null : supplier.getSupplierName(), entity.getPurchaseOrderId(), entity.getSupplierInvoiceId(), entity.getReceiptNumber(), entity.getReceivedAt() == null ? null : entity.getReceivedAt().toString(), entity.getLocationId(), location == null ? null : location.getLocationName(), entity.getItemsJson(), entity.getMatchingStatus(), entity.getVarianceSummary(), entity.getApprovalNote(), entity.getConfirmedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String expiryStatus(LocalDate expiryDate) {
        if (expiryDate == null) {
            return "NONE";
        }
        LocalDate today = LocalDate.now();
        if (expiryDate.isBefore(today)) {
            return "EXPIRED";
        }
        if (!expiryDate.isAfter(today.plusDays(EXPIRY_WARNING_DAYS))) {
            return "NEAR_EXPIRY";
        }
        return "OK";
    }

    private boolean isExpired(StockEntity stock) {
        return stock != null && stock.getExpiryDate() != null && stock.getExpiryDate().isBefore(LocalDate.now());
    }

    private Optional<StockEntity> findStockBatch(UUID tenantId, UUID medicineId, UUID locationId, String batchNumber, String purchaseReferenceNumber) {
        return stockRepository.findByTenantIdAndMedicineIdAndLocationId(tenantId, medicineId, locationId).stream()
                .filter(stock -> (batchNumber != null && batchNumber.equalsIgnoreCase(normalizeNullable(stock.getBatchNumber())))
                        || (purchaseReferenceNumber != null && purchaseReferenceNumber.equalsIgnoreCase(normalizeNullable(stock.getPurchaseReferenceNumber()))))
                .findFirst();
    }

    private record MedicineImportOutcome(String status, String message, UUID medicineId, UUID stockId, String medicineName) {
    }

    private ImportDuplicateTracker buildDuplicateTracker(UUID tenantId) {
        ImportDuplicateTracker tracker = new ImportDuplicateTracker();
        for (MedicineEntity medicine : medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)) {
            tracker.registerExisting(medicine.getMedicineName(), medicine.getBarcode(), medicine.getExternalCode());
        }
        return tracker;
    }

    private record ParsedMedicineImportRow(int rowNumber, String medicineName, MedicineUpsertCommand command, ParsedStockImportData stockData) {
    }

    private record ParsedStockImportData(
            Integer quantityOnHand,
            Integer lowStockThreshold,
            BigDecimal unitCost,
            BigDecimal sellingPrice,
            String batchNumber,
            String purchaseReferenceNumber,
            LocalDate expiryDate,
            LocalDate purchaseDate,
            String supplierName,
            String barcode,
            String qrCode,
            String externalCode
    ) {
        private StockUpsertCommand toCommand(UUID tenantId, UUID medicineId) {
            return new StockUpsertCommand(
                    medicineId,
                    null,
                    barcode,
                    qrCode,
                    externalCode,
                    batchNumber,
                    purchaseReferenceNumber,
                    expiryDate,
                    purchaseDate,
                    supplierName,
                    quantityOnHand == null ? 0 : quantityOnHand,
                    quantityOnHand == null ? 0 : quantityOnHand,
                    lowStockThreshold,
                    unitCost,
                    unitCost,
                    sellingPrice,
                    true
            );
        }
    }

    private static final class ImportDuplicateTracker {
        private final Map<String, String> existingKeys = new HashMap<>();
        private final Map<String, String> importedKeys = new HashMap<>();

        private void registerExisting(String medicineName, String barcode, String externalCode) {
            registerKey(existingKeys, "name", medicineName);
            registerKey(existingKeys, "barcode", barcode);
            registerKey(existingKeys, "externalCode", externalCode);
        }

        private String duplicateMessage(MedicineUpsertCommand command) {
            if (hasKey(existingKeys, "name", command.medicineName())
                    || hasKey(existingKeys, "barcode", command.barcode())
                    || hasKey(existingKeys, "externalCode", command.externalCode())
                    || hasKey(importedKeys, "name", command.medicineName())
                    || hasKey(importedKeys, "barcode", command.barcode())
                    || hasKey(importedKeys, "externalCode", command.externalCode())) {
                return "Medicine already exists";
            }
            return null;
        }

        private void registerSuccessfulImport(MedicineUpsertCommand command) {
            registerKey(importedKeys, "name", command.medicineName());
            registerKey(importedKeys, "barcode", command.barcode());
            registerKey(importedKeys, "externalCode", command.externalCode());
        }

        private static boolean hasKey(Map<String, String> keys, String field, String value) {
            String normalized = normalizeKey(field, value);
            return normalized != null && keys.containsKey(normalized);
        }

        private static void registerKey(Map<String, String> keys, String field, String value) {
            String normalized = normalizeKey(field, value);
            if (normalized != null) {
                keys.put(normalized, normalized);
            }
        }

        private static String normalizeKey(String field, String value) {
            return StringUtils.hasText(value) ? field + ":" + value.trim().toLowerCase(Locale.ROOT) : null;
        }
    }
}
