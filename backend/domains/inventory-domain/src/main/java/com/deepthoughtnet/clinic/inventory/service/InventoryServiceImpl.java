package com.deepthoughtnet.clinic.inventory.service;

import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.PhysicalCountSessionEntity;
import com.deepthoughtnet.clinic.inventory.db.PhysicalCountSessionRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationUpsertCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransferCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import com.deepthoughtnet.clinic.inventory.service.model.LowStockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineUpsertCommand;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountAuditFields;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountReviewChecklist;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountSessionLine;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountSessionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountSessionSaveCommand;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InventoryServiceImpl implements InventoryService {
    private static final String MEDICINE_ENTITY = "MEDICINE";
    private static final String STOCK_ENTITY = "INVENTORY_STOCK";
    private static final String TRANSACTION_ENTITY = "INVENTORY_TRANSACTION";
    private static final String ACTIVE_SELLABLE_MEDICINE_TYPES = "TABLET,CAPSULE,SYRUP,INJECTION,DROP,DROPS,OINTMENT,SACHET";
    private static final String BATCH_NUMBER_REGEX = "^[A-Za-z0-9/_-]{3,30}$";
    private static final String PURCHASE_REFERENCE_REGEX = "^[A-Za-z0-9/_\\-\\s]{1,60}$";
    private static final String ALPHANUMERIC_CODE_REGEX = "^[A-Za-z0-9/_-]{1,50}$";
    private static final String DIGITS_ONLY_REGEX = "^[0-9]{8,20}$";
    private static final int MEDICINE_NAME_MAX_LENGTH = 60;
    private static final int MEDICINE_TEXT_MAX_LENGTH = 60;
    private static final int MEDICINE_INSTRUCTIONS_MAX_LENGTH = 250;
    private static final int MEDICINE_DURATION_MAX_DAYS = 365;
    private static final int MEDICINE_PRICE_MAX_INTEGER_DIGITS = 6;
    private static final BigDecimal MEDICINE_PRICE_MAX = new BigDecimal("999999.99");
    private static final BigDecimal MEDICINE_TAX_MAX = new BigDecimal("100");
    private static final Pattern LETTER_OR_NUMBER_PATTERN = Pattern.compile(".*[A-Za-z0-9].*");
    private static final Pattern MEDICINE_BARCODE_PATTERN = Pattern.compile("^[A-Za-z0-9/_-]+$");
    private static final List<String> SUPPORTED_MEDICINE_TYPES = List.of("TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROP", "OINTMENT", "SACHET", "OTHER");
    private static final List<String> SUPPORTED_TIMING_VALUES = List.of("BEFORE_FOOD", "AFTER_FOOD", "WITH_FOOD", "ANYTIME");

    private final MedicineRepository medicineRepository;
    private final StockRepository stockRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryLocationRepository locationRepository;
    private final PhysicalCountSessionRepository physicalCountSessionRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public InventoryServiceImpl(
            MedicineRepository medicineRepository,
            StockRepository stockRepository,
            InventoryTransactionRepository transactionRepository,
            InventoryLocationRepository locationRepository,
            PhysicalCountSessionRepository physicalCountSessionRepository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.medicineRepository = medicineRepository;
        this.stockRepository = stockRepository;
        this.transactionRepository = transactionRepository;
        this.locationRepository = locationRepository;
        this.physicalCountSessionRepository = physicalCountSessionRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MedicineRecord> listMedicines(UUID tenantId) {
        requireTenant(tenantId);
        return medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MedicineRecord> findMedicine(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return medicineRepository.findByTenantIdAndId(tenantId, id).map(this::toRecord);
    }

    @Override
    @Transactional
    public MedicineRecord createMedicine(UUID tenantId, MedicineUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateMedicine(command);
        ensureUniqueMedicine(tenantId, command, null);
        MedicineEntity entity = MedicineEntity.create(tenantId, normalize(command.medicineName()), normalizeType(command.medicineType()));
        applyMedicine(entity, command);
        MedicineEntity saved = medicineRepository.save(entity);
        auditMedicine(tenantId, saved, "medicine.created", actorAppUserId, "Created medicine");
        return toRecord(saved);
    }

    @Override
    @Transactional
    public MedicineRecord updateMedicine(UUID tenantId, UUID id, MedicineUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validateMedicine(command);
        MedicineEntity entity = medicineRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        ensureUniqueMedicine(tenantId, command, id);
        applyMedicine(entity, command);
        MedicineEntity saved = medicineRepository.save(entity);
        auditMedicine(tenantId, saved, "medicine.updated", actorAppUserId, "Updated medicine");
        return toRecord(saved);
    }

    @Override
    @Transactional
    public MedicineRecord deactivateMedicine(UUID tenantId, UUID id, UUID actorAppUserId) {
        return setActive(tenantId, id, actorAppUserId, false);
    }

    @Override
    @Transactional
    public MedicineRecord activateMedicine(UUID tenantId, UUID id, UUID actorAppUserId) {
        return setActive(tenantId, id, actorAppUserId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRecord> listStocks(UUID tenantId) {
        requireTenant(tenantId);
        return mapStocks(tenantId, stockRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRecord> listStocks(UUID tenantId, UUID locationId) {
        requireTenant(tenantId);
        if (locationId == null) {
            return listStocks(tenantId);
        }
        return mapStocks(tenantId, stockRepository.findByTenantIdAndLocationIdOrderByUpdatedAtDesc(tenantId, locationId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockRecord> findStock(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return stockRepository.findByTenantIdAndId(tenantId, id).map(stock -> toRecord(stock, medicineRepository.findByTenantIdAndId(tenantId, stock.getMedicineId()).orElse(null)));
    }

    @Override
    @Transactional
    public StockRecord createStock(UUID tenantId, StockUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateStock(tenantId, command, true, null);
        UUID locationId = resolveLocationId(tenantId, command.locationId());
        ensureUniqueStockBatch(tenantId, command.medicineId(), locationId, command.batchNumber(), null);
        StockEntity entity = StockEntity.create(tenantId, command.medicineId(), locationId);
        int beforeQuantity = entity.getQuantityOnHand();
        applyStock(entity, command);
        StockEntity saved;
        try {
            saved = stockRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw translateStockIntegrityViolation(ex);
        }
        recordStockQuantityAudit(tenantId, saved, beforeQuantity, saved.getQuantityOnHand(), command, actorAppUserId, true);
        auditStock(tenantId, saved, "stock.created", actorAppUserId, "Created stock batch");
        return toRecord(saved, medicineRepository.findByTenantIdAndId(tenantId, saved.getMedicineId()).orElse(null));
    }

    @Override
    @Transactional
    public StockRecord updateStock(UUID tenantId, UUID id, StockUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        StockEntity entity = stockRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found"));
        validateStock(tenantId, command, false, entity);
        UUID locationId = resolveLocationId(tenantId, command.locationId());
        ensureUniqueStockBatch(tenantId, command.medicineId(), locationId, command.batchNumber(), id);
        if (transactionRepository.existsByTenantIdAndStockBatchId(tenantId, id)) {
            if (!entity.getMedicineId().equals(command.medicineId())) {
                throw new IllegalArgumentException("Medicine cannot be changed after stock movements exist.");
            }
            if (!Objects.equals(entity.getLocationId(), locationId)) {
                throw new IllegalArgumentException("Location cannot be changed after stock movements exist.");
            }
            if (!StringUtils.hasText(entity.getBatchNumber()) ? StringUtils.hasText(command.batchNumber()) : !entity.getBatchNumber().trim().equalsIgnoreCase(normalizeNullable(command.batchNumber()))) {
                throw new IllegalArgumentException("Batch number cannot be changed after stock movements exist.");
            }
        }
        int beforeQuantity = entity.getQuantityOnHand();
        applyStock(entity, command);
        StockEntity saved;
        try {
            saved = stockRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw translateStockIntegrityViolation(ex);
        }
        recordStockQuantityAudit(tenantId, saved, beforeQuantity, saved.getQuantityOnHand(), command, actorAppUserId, false);
        auditStock(tenantId, saved, "stock.updated", actorAppUserId, "Updated stock batch");
        return toRecord(saved, medicineRepository.findByTenantIdAndId(tenantId, saved.getMedicineId()).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryTransactionRecord> listTransactions(UUID tenantId) {
        requireTenant(tenantId);
        return transactionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional
    public InventoryTransactionRecord createTransaction(UUID tenantId, InventoryTransactionCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateTransaction(tenantId, command);
        String normalizedReferenceType = normalizeNullable(command.referenceType());
        String transactionType = normalizeTransactionType(command.transactionType()).name();
        if (normalizedReferenceType != null && command.referenceId() != null && command.stockBatchId() != null) {
            return transactionRepository
                    .findFirstByTenantIdAndReferenceTypeAndReferenceIdAndStockBatchIdAndTransactionTypeOrderByCreatedAtDesc(
                            tenantId,
                            normalizedReferenceType,
                            command.referenceId(),
                            command.stockBatchId(),
                            transactionType
                    )
                    .map(this::toRecord)
                    .orElseGet(() -> createTransactionInternal(tenantId, command, actorAppUserId, transactionType, normalizedReferenceType));
        }
        return createTransactionInternal(tenantId, command, actorAppUserId, transactionType, normalizedReferenceType);
    }

    private InventoryTransactionRecord createTransactionInternal(UUID tenantId, InventoryTransactionCommand command, UUID actorAppUserId, String transactionType, String normalizedReferenceType) {
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, command.medicineId())
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        StockEntity stock = command.stockBatchId() == null ? null : stockRepository.findByTenantIdAndId(tenantId, command.stockBatchId())
                .orElseThrow(() -> new IllegalArgumentException("Stock batch not found"));
        UUID locationId = resolveLocationId(tenantId, command.locationId() != null ? command.locationId() : (stock == null ? null : stock.getLocationId()));

        int delta = Math.abs(command.quantity());
        Integer beforeQuantity = null;
        Integer afterQuantity = null;
        if (stock != null) {
            InventoryTransactionType normalizedType = InventoryTransactionType.valueOf(transactionType);
            if ((normalizedType == InventoryTransactionType.SALE || normalizedType == InventoryTransactionType.DISPENSED || normalizedType == InventoryTransactionType.VACCINATION_ADMINISTERED) && !stock.isActive()) {
                throw new IllegalArgumentException("Inactive batch cannot be sold or dispensed.");
            }
            if (isExpired(stock) && (normalizedType == InventoryTransactionType.SALE || normalizedType == InventoryTransactionType.DISPENSED || normalizedType == InventoryTransactionType.VACCINATION_ADMINISTERED)) {
                throw new IllegalArgumentException("Batch expired and cannot be sold or dispensed.");
            }
            int current = stock.getQuantityOnHand();
            int nextQuantity = switch (normalizedType) {
                case DISPENSED, VACCINATION_ADMINISTERED, ADJUSTMENT_OUT, EXPIRED, SALE, VENDOR_RETURN_OUT, WRITE_OFF -> current - delta;
                case CUSTOMER_RETURN_NON_SELLABLE -> current;
                case ADJUSTMENT -> current + delta;
                default -> current + delta;
            };
            if (nextQuantity < 0) {
                throw new IllegalArgumentException("Insufficient stock available.");
            }
            beforeQuantity = current;
            afterQuantity = nextQuantity;
            stock.setQuantityOnHand(nextQuantity);
            stockRepository.save(stock);
        }

        InventoryTransactionEntity entity = transactionRepository.save(InventoryTransactionEntity.create(
                tenantId,
                medicine.getId(),
                stock == null ? null : stock.getId(),
                locationId,
                command.targetLocationId(),
                transactionType,
                delta,
                beforeQuantity,
                afterQuantity,
                normalizedReferenceType,
                command.referenceId(),
                command.createdBy() == null ? actorAppUserId : command.createdBy(),
                normalizeNullable(command.reason()),
                normalizeNullable(command.notes()),
                normalizeNullable(command.businessReference())
        ));
        auditTransaction(tenantId, entity, "inventory.transaction.created", actorAppUserId, "Created inventory transaction");
        return toRecord(entity);
    }

    @Override
    @Transactional
    public InventoryTransactionRecord transferStock(UUID tenantId, InventoryTransferCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        InventoryLocationEntity fromLocation = resolveLocation(tenantId, command.fromLocationId());
        InventoryLocationEntity toLocation = resolveLocation(tenantId, command.toLocationId());
        if (!fromLocation.isActive()) {
            throw new IllegalArgumentException("Source location is inactive");
        }
        if (!toLocation.isActive()) {
            throw new IllegalArgumentException("Destination location is inactive");
        }
        UUID fromLocationId = fromLocation.getId();
        UUID toLocationId = toLocation.getId();
        if (fromLocationId.equals(toLocationId)) {
            throw new IllegalArgumentException("source and destination locations must differ");
        }
        StockEntity source = command.stockBatchId() == null
                ? stockRepository.findByTenantIdAndLocationIdAndBatchNumberIgnoreCase(tenantId, fromLocationId, null).orElse(null)
                : stockRepository.findByTenantIdAndId(tenantId, command.stockBatchId())
                .orElseThrow(() -> new IllegalArgumentException("Stock batch not found"));
        if (source == null || !source.getLocationId().equals(fromLocationId)) {
            throw new IllegalArgumentException("Source stock not found in selected location");
        }
        if (!source.isActive()) {
            throw new IllegalArgumentException("Inactive batch cannot be transferred.");
        }
        if (isExpired(source)) {
            throw new IllegalArgumentException("Expired batch cannot be transferred.");
        }
        int quantity = Math.max(0, command.quantity());
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (source.getQuantityOnHand() < quantity) {
            throw new IllegalArgumentException("Insufficient stock available.");
        }
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, command.medicineId())
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        int sourceBefore = source.getQuantityOnHand();
        source.setQuantityOnHand(sourceBefore - quantity);
        stockRepository.save(source);

        StockEntity target = stockRepository.findByTenantIdAndMedicineIdAndLocationId(tenantId, medicine.getId(), toLocationId).stream()
                .filter(stock -> source.getBatchNumber() == null || source.getBatchNumber().equalsIgnoreCase(normalizeNullable(stock.getBatchNumber())))
                .findFirst()
                .orElseGet(() -> StockEntity.create(tenantId, medicine.getId(), toLocationId));
        if (target.getId() == null) {
            // defensive, but StockEntity.create always assigns an id
        }
        target.update(
                target.getLocationId() == null ? toLocationId : target.getLocationId(),
                source.getBarcode(),
                source.getQrCode(),
                source.getExternalCode(),
                source.getBatchNumber(),
                source.getPurchaseReferenceNumber(),
                source.getExpiryDate(),
                source.getPurchaseDate(),
                source.getSupplierName(),
                target.getQuantityReceived() + quantity,
                target.getQuantityOnHand() + quantity,
                target.getLowStockThreshold(),
                target.getUnitCost(),
                target.getPurchasePrice(),
                target.getSellingPrice(),
                true
        );
        StockEntity savedTarget = stockRepository.save(target);

        InventoryTransactionEntity entity = transactionRepository.save(InventoryTransactionEntity.create(
                tenantId,
                medicine.getId(),
                source.getId(),
                fromLocationId,
                toLocationId,
                InventoryTransactionType.TRANSFER_OUT.name(),
                quantity,
                sourceBefore,
                source.getQuantityOnHand(),
                "TRANSFER",
                null,
                actorAppUserId,
                normalizeNullable(command.reason()),
                "Transferred stock to another location"
        ));
        auditTransaction(tenantId, entity, "inventory.transfer.created", actorAppUserId, "Transferred stock between locations");
        return toRecord(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LowStockRecord> listLowStock(UUID tenantId) {
        requireTenant(tenantId);
        return stockRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .filter(stock -> stock.isActive() && !isExpired(stock) && stock.getQuantityOnHand() <= effectiveThreshold(stock))
                .map(stock -> toLowStockRecord(stock, medicineRepository.findByTenantIdAndId(tenantId, stock.getMedicineId()).orElse(null)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRecord> listExpiredStocks(UUID tenantId) {
        return listStocks(tenantId).stream()
                .filter(stock -> stock.expiryDate() != null && stock.expiryDate().isBefore(today()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRecord> listExpiringStocks(UUID tenantId, int days) {
        LocalDate current = today();
        LocalDate end = current.plusDays(Math.max(days, 1));
        return listStocks(tenantId).stream()
                .filter(stock -> stock.expiryDate() != null && !stock.expiryDate().isBefore(current) && !stock.expiryDate().isAfter(end))
                .toList();
    }

    @Override
    @Transactional
    public List<InventoryLocationRecord> listLocations(UUID tenantId) {
        requireTenant(tenantId);
        ensureDefaultLocation(tenantId);
        return locationRepository.findByTenantIdOrderByDefaultLocationDescLocationNameAsc(tenantId).stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional
    public InventoryLocationRecord saveLocation(UUID tenantId, UUID id, InventoryLocationUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        if (command == null || !StringUtils.hasText(command.locationName()) || !StringUtils.hasText(command.locationType())) {
            throw new IllegalArgumentException("location name and type are required");
        }
        String normalizedName = normalize(command.locationName());
        if (id == null) {
            locationRepository.findByTenantIdAndLocationNameIgnoreCase(tenantId, normalizedName)
                    .ifPresent(existing -> { throw new IllegalArgumentException("Location already exists with this name"); });
        } else if (locationRepository.existsByTenantIdAndLocationNameIgnoreCaseAndIdNot(tenantId, normalizedName, id)) {
            throw new IllegalArgumentException("Location already exists with this name");
        }
        InventoryLocationEntity entity = id == null
                ? InventoryLocationEntity.create(tenantId, normalizedName, normalizeNullable(command.locationCode()), normalize(command.locationType()), command.defaultLocation())
                : locationRepository.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Location not found"));
        if (id != null) {
            entity.update(normalize(command.locationName()), normalizeNullable(command.locationCode()), normalize(command.locationType()), command.defaultLocation(), command.active());
        }
        if (command.defaultLocation()) {
            locationRepository.findByTenantIdOrderByDefaultLocationDescLocationNameAsc(tenantId).stream()
                    .filter(location -> !location.getId().equals(entity.getId()) && location.isDefaultLocation())
                    .findFirst()
                    .ifPresent(previous -> {
                        previous.update(previous.getLocationName(), previous.getLocationCode(), previous.getLocationType(), false, previous.isActive());
                        locationRepository.save(previous);
                    });
        }
        InventoryLocationEntity saved = locationRepository.save(entity);
        return toRecord(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhysicalCountSessionRecord> listPhysicalCountSessions(UUID tenantId) {
        requireTenant(tenantId);
        return physicalCountSessionRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PhysicalCountSessionRecord> findPhysicalCountSession(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return physicalCountSessionRepository.findByTenantIdAndId(tenantId, id).map(this::toRecord);
    }

    @Override
    @Transactional
    public PhysicalCountSessionRecord savePhysicalCountSession(UUID tenantId, UUID id, PhysicalCountSessionSaveCommand command, UUID actorAppUserId, Set<String> actorRoles) {
        requireTenant(tenantId);
        requireId(id, "id");
        validatePhysicalCountSession(command);
        PhysicalCountSessionEntity existingEntity = physicalCountSessionRepository.findByTenantIdAndId(tenantId, id).orElse(null);
        PhysicalCountSessionRecord existingRecord = existingEntity == null ? null : toRecord(existingEntity);
        validatePhysicalCountSessionTransition(existingRecord, command, actorRoles);
        InventoryLocationEntity location = resolveLocation(tenantId, command.locationId());

        PhysicalCountSessionRecord record = toRecord(tenantId, id, command, location.getLocationName());
        String sessionJson = detailsJson(record);
        PhysicalCountSessionEntity entity = Optional.ofNullable(existingEntity)
                .map(existing -> {
                    existing.update(
                            record.sessionName(),
                            record.locationId(),
                            record.locationName(),
                            record.scope(),
                            record.scopeLabel(),
                            record.reason(),
                            record.status(),
                            sessionJson,
                            actorAppUserId
                    );
                    return existing;
                })
                .orElseGet(() -> PhysicalCountSessionEntity.create(
                        tenantId,
                        id,
                        record.sessionName(),
                        record.locationId(),
                        record.locationName(),
                        record.scope(),
                        record.scopeLabel(),
                        record.reason(),
                        record.status(),
                        sessionJson,
                        actorAppUserId,
                        actorAppUserId
                ));

        PhysicalCountSessionEntity saved = physicalCountSessionRepository.save(entity);
        return toRecord(saved);
    }

    private InventoryTransactionType normalizeTransactionType(InventoryTransactionType type) {
        if (type == null) {
            return InventoryTransactionType.OPENING;
        }
        return switch (type) {
            case SALE -> InventoryTransactionType.DISPENSED;
            case PURCHASE -> InventoryTransactionType.STOCK_IN;
            default -> type;
        };
    }

    private MedicineRecord setActive(UUID tenantId, UUID id, UUID actorAppUserId, boolean active) {
        requireTenant(tenantId);
        requireId(id, "id");
        MedicineEntity entity = medicineRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        entity.update(entity.getMedicineName(), entity.getMedicineType(), entity.getBarcode(), entity.getQrCode(), entity.getExternalCode(), entity.getGenericName(), entity.getBrandName(), entity.getCategory(), entity.getDosageForm(), entity.getStrength(), entity.getUnit(), entity.getManufacturer(), entity.getDefaultDosage(), entity.getDefaultFrequency(), entity.getDefaultDurationDays(), entity.getDefaultTiming(), entity.getDefaultInstructions(), entity.getDefaultPrice(), entity.getTaxRate(), active);
        MedicineEntity saved = medicineRepository.save(entity);
        auditMedicine(tenantId, saved, active ? "medicine.activated" : "medicine.deactivated", actorAppUserId, active ? "Activated medicine" : "Deactivated medicine");
        return toRecord(saved);
    }

    private MedicineRecord toRecord(MedicineEntity entity) {
        return new MedicineRecord(entity.getId(), entity.getTenantId(), entity.getMedicineName(), entity.getMedicineType(), entity.getBarcode(), entity.getQrCode(), entity.getExternalCode(), entity.getGenericName(), entity.getBrandName(), entity.getCategory(), entity.getDosageForm(), entity.getStrength(), entity.getUnit(), entity.getManufacturer(), entity.getDefaultDosage(), entity.getDefaultFrequency(), entity.getDefaultDurationDays(), entity.getDefaultTiming(), entity.getDefaultInstructions(), entity.getDefaultPrice(), entity.getTaxRate(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private StockRecord toRecord(StockEntity stock, MedicineEntity medicine) {
        InventoryLocationEntity location = stock.getLocationId() == null ? null : locationRepository.findByTenantIdAndId(stock.getTenantId(), stock.getLocationId()).orElse(null);
        return new StockRecord(stock.getId(), stock.getTenantId(), stock.getMedicineId(), stock.getLocationId(), location == null ? null : location.getLocationName(), medicine == null ? null : medicine.getMedicineName(), medicine == null ? null : medicine.getMedicineType(), stock.getBarcode(), stock.getQrCode(), stock.getExternalCode(), stock.getBatchNumber(), stock.getPurchaseReferenceNumber(), stock.getExpiryDate(), stock.getPurchaseDate(), stock.getSupplierName(), stock.getQuantityReceived(), stock.getQuantityOnHand(), stock.getLowStockThreshold(), stock.getUnitCost(), stock.getPurchasePrice(), stock.getSellingPrice(), stock.isActive(), stock.getCreatedAt(), stock.getUpdatedAt());
    }

    private LowStockRecord toLowStockRecord(StockEntity stock, MedicineEntity medicine) {
        return new LowStockRecord(stock.getId(), stock.getMedicineId(), medicine == null ? null : medicine.getMedicineName(), medicine == null ? null : medicine.getMedicineType(), stock.getBatchNumber(), stock.getExpiryDate(), stock.getQuantityOnHand(), stock.getLowStockThreshold(), stock.getSellingPrice());
    }

    private InventoryTransactionRecord toRecord(InventoryTransactionEntity entity) {
        return new InventoryTransactionRecord(entity.getId(), entity.getTenantId(), entity.getMedicineId(), entity.getStockBatchId(), entity.getLocationId(), entity.getTargetLocationId(), InventoryTransactionType.valueOf(entity.getTransactionType()), entity.getQuantity(), entity.getBeforeQuantity(), entity.getAfterQuantity(), entity.getReason(), entity.getReferenceType(), entity.getReferenceId(), entity.getCreatedBy(), entity.getNotes(), entity.getCreatedAt(), entity.getBusinessReference());
    }

    private InventoryLocationRecord toRecord(InventoryLocationEntity entity) {
        return new InventoryLocationRecord(entity.getId(), entity.getTenantId(), entity.getLocationName(), entity.getLocationCode(), entity.getLocationType(), entity.isDefaultLocation(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private List<StockRecord> mapStocks(UUID tenantId, List<StockEntity> stocks) {
        Map<UUID, MedicineEntity> medicines = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .collect(Collectors.toMap(MedicineEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return stocks.stream().map(stock -> toRecord(stock, medicines.get(stock.getMedicineId()))).toList();
    }

    private void applyMedicine(MedicineEntity entity, MedicineUpsertCommand command) {
        entity.update(normalize(command.medicineName()), normalizeType(command.medicineType()), normalizeNullable(command.barcode()), normalizeNullable(command.qrCode()), normalizeNullable(command.externalCode()), normalizeNullable(command.genericName()), normalizeNullable(command.brandName()), normalizeNullable(command.category()), normalizeNullable(command.dosageForm()), normalizeNullable(command.strength()), normalizeNullable(command.unit()), normalizeNullable(command.manufacturer()), normalizeNullable(command.defaultDosage()), normalizeNullable(command.defaultFrequency()), command.defaultDurationDays(), normalizeNullable(command.defaultTiming()), normalizeNullable(command.defaultInstructions()), normalizeMoney(command.defaultPrice()), normalizeMoney(command.taxRate()), command.active());
    }

    private void applyStock(StockEntity entity, StockUpsertCommand command) {
        int quantityOnHand = command.quantityOnHand();
        int quantityReceived = command.quantityReceived() == null ? quantityOnHand : command.quantityReceived();
        entity.update(resolveLocationId(entity.getTenantId(), command.locationId()), normalizeNullable(command.barcode()), normalizeNullable(command.qrCode()), normalizeNullable(command.externalCode()), normalizeNullable(command.batchNumber()), normalizeNullable(command.purchaseReferenceNumber()), command.expiryDate(), command.purchaseDate(), normalizeNullable(command.supplierName()), quantityReceived, quantityOnHand, command.lowStockThreshold(), normalizeMoney(command.unitCost()), normalizeMoney(command.purchasePrice()), normalizeMoney(command.sellingPrice()), command.active());
    }

    private void recordStockQuantityAudit(
            UUID tenantId,
            StockEntity stock,
            int beforeQuantity,
            int afterQuantity,
            StockUpsertCommand command,
            UUID actorAppUserId,
            boolean created
    ) {
        int delta = afterQuantity - beforeQuantity;
        if (delta == 0) {
            return;
        }

        InventoryTransactionType transactionType = delta > 0
                ? stockInTransactionType(command)
                : InventoryTransactionType.ADJUSTMENT_OUT;
        String reason = delta > 0
                ? (created ? "Stock batch created" : "Stock batch quantity increased")
                : "Stock batch quantity reduced";
        String notes = stockAuditReference(command, stock);

        InventoryTransactionEntity entity = transactionRepository.save(InventoryTransactionEntity.create(
                tenantId,
                stock.getMedicineId(),
                stock.getId(),
                stock.getLocationId(),
                null,
                transactionType.name(),
                Math.abs(delta),
                beforeQuantity,
                afterQuantity,
                "STOCK_INWARD",
                null,
                actorAppUserId,
                reason,
                notes
        ));
        auditTransaction(tenantId, entity, "inventory.transaction.created", actorAppUserId, "Created inventory transaction");
    }

    private InventoryTransactionType stockInTransactionType(StockUpsertCommand command) {
        return StringUtils.hasText(command.purchaseReferenceNumber())
                ? InventoryTransactionType.PURCHASE
                : InventoryTransactionType.STOCK_IN;
    }

    private String stockAuditReference(StockUpsertCommand command, StockEntity stock) {
        if (StringUtils.hasText(command.purchaseReferenceNumber())) {
            return command.purchaseReferenceNumber().trim();
        }
        if (StringUtils.hasText(stock.getBatchNumber())) {
            return "Batch " + stock.getBatchNumber().trim();
        }
        return "Inventory stock update";
    }

    private void validateMedicine(MedicineUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        String medicineName = normalizeNullable(command.medicineName());
        String medicineType = normalizeType(command.medicineType());
        String strength = normalizeNullable(command.strength());
        validateRequiredText(medicineName, MEDICINE_NAME_MAX_LENGTH, 2, "medicineName", true, true);
        validateMedicineType(medicineType);
        validateOptionalText(command.barcode(), 60, MEDICINE_BARCODE_PATTERN, "barcode", "Barcode can use letters, numbers, dashes, underscores, and slashes only.");
        validateOptionalText(command.qrCode(), 60, null, "qrCode");
        validateOptionalText(command.externalCode(), 60, null, "externalCode");
        validateOptionalText(command.genericName(), MEDICINE_TEXT_MAX_LENGTH, LETTER_OR_NUMBER_PATTERN, "genericName", "Generic name must include a letter or number.");
        validateOptionalText(command.brandName(), MEDICINE_TEXT_MAX_LENGTH, LETTER_OR_NUMBER_PATTERN, "brandName", "Brand name must include a letter or number.");
        validateOptionalText(command.category(), MEDICINE_TEXT_MAX_LENGTH, null, "category");
        validateOptionalText(command.dosageForm(), MEDICINE_TEXT_MAX_LENGTH, null, "dosageForm");
        validateRequiredText(strength, MEDICINE_TEXT_MAX_LENGTH, 1, "strength", true, true);
        validateOptionalText(command.unit(), MEDICINE_TEXT_MAX_LENGTH, null, "unit");
        validateOptionalText(command.manufacturer(), MEDICINE_TEXT_MAX_LENGTH, null, "manufacturer");
        validateOptionalText(command.defaultDosage(), MEDICINE_TEXT_MAX_LENGTH, null, "defaultDosage");
        validateOptionalText(command.defaultFrequency(), MEDICINE_TEXT_MAX_LENGTH, null, "defaultFrequency");
        validateOptionalDuration(command.defaultDurationDays());
        validateOptionalTiming(command.defaultTiming());
        validateOptionalText(command.defaultInstructions(), MEDICINE_INSTRUCTIONS_MAX_LENGTH, null, "defaultInstructions");
        validateOptionalMoney(command.defaultPrice(), "defaultPrice", MEDICINE_PRICE_MAX, MEDICINE_PRICE_MAX_INTEGER_DIGITS);
        validateOptionalMoney(command.taxRate(), "taxRate", MEDICINE_TAX_MAX, 3);
    }

    private void validateRequiredText(String value, int maxLength, int minLength, String field, boolean requireLetterOrNumber, boolean rejectBlankOnly) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() < minLength || trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be between " + minLength + " and " + maxLength + " characters");
        }
        if (rejectBlankOnly && !LETTER_OR_NUMBER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(field + " must include a letter or number");
        }
        if (requireLetterOrNumber && !LETTER_OR_NUMBER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(field + " must include a letter or number");
        }
    }

    private void validateOptionalText(String value, int maxLength, Pattern allowedPattern, String field) {
        validateOptionalText(value, maxLength, allowedPattern, field, field + " has an invalid format");
    }

    private void validateOptionalText(String value, int maxLength, Pattern allowedPattern, String field, String invalidFormatMessage) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be " + maxLength + " characters or fewer");
        }
        if (allowedPattern != null && !allowedPattern.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(invalidFormatMessage);
        }
    }

    private void validateOptionalDuration(Integer value) {
        if (value == null) {
            return;
        }
        if (value < 1 || value > MEDICINE_DURATION_MAX_DAYS) {
            throw new IllegalArgumentException("defaultDurationDays must be between 1 and " + MEDICINE_DURATION_MAX_DAYS);
        }
    }

    private void validateOptionalTiming(String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String normalized = normalize(value).toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TIMING_VALUES.contains(normalized)) {
            throw new IllegalArgumentException("defaultTiming must be one of BEFORE_FOOD, AFTER_FOOD, WITH_FOOD, ANYTIME");
        }
    }

    private void validateMedicineType(String medicineType) {
        if (!StringUtils.hasText(medicineType)) {
            throw new IllegalArgumentException("medicineType is required");
        }
        if (!SUPPORTED_MEDICINE_TYPES.contains(medicineType.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("medicineType must be one of TABLET, CAPSULE, SYRUP, INJECTION, DROP, OINTMENT, SACHET, OTHER");
        }
    }

    private void validateOptionalMoney(BigDecimal value, String field, BigDecimal maxValue, int maxIntegerDigits) {
        if (value == null) {
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " must be zero or greater");
        }
        if (value.compareTo(maxValue) > 0) {
            throw new IllegalArgumentException(field + " exceeds the allowed maximum");
        }
        if (value.scale() > 2 && value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(field + " must use at most 2 decimals");
        }
        if (value.precision() - value.scale() > maxIntegerDigits) {
            throw new IllegalArgumentException(field + " exceeds the allowed maximum");
        }
    }

    private void validateStock(UUID tenantId, StockUpsertCommand command, boolean creating, StockEntity currentStock) {
        if (command == null) throw new IllegalArgumentException("command is required");
        requireId(command.medicineId(), "medicineId");
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, command.medicineId())
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        if (!medicine.isActive()) throw new IllegalArgumentException("Cannot add stock for an inactive medicine.");
        if (creating && effectiveCreatedQuantity(command) <= 0) throw new IllegalArgumentException("quantityOnHand must be positive");
        if (!creating && command.quantityOnHand() < 0) throw new IllegalArgumentException("quantityOnHand cannot be negative");
        if (command.quantityReceived() != null && command.quantityReceived() < 0) throw new IllegalArgumentException("quantityReceived cannot be negative");
        if (!StringUtils.hasText(command.batchNumber())) throw new IllegalArgumentException("batchNumber is required");
        String batchNumber = normalizeNullable(command.batchNumber());
        if (batchNumber == null || !batchNumber.matches(BATCH_NUMBER_REGEX)) throw new IllegalArgumentException("Batch number must be 3 to 30 characters using letters, numbers, dashes, underscores, or slashes.");
        if (StringUtils.hasText(command.purchaseReferenceNumber()) && !normalizeNullable(command.purchaseReferenceNumber()).matches(PURCHASE_REFERENCE_REGEX)) {
            throw new IllegalArgumentException("Purchase reference must be 60 characters or fewer and can include spaces, letters, numbers, dashes, underscores, and slashes.");
        }
        if (StringUtils.hasText(command.barcode()) && !normalizeNullable(command.barcode()).matches(DIGITS_ONLY_REGEX)) {
            throw new IllegalArgumentException("Barcode must be 8 to 20 digits.");
        }
        if (StringUtils.hasText(command.qrCode()) && normalizeNullable(command.qrCode()).length() > 100) {
            throw new IllegalArgumentException("QR code must be 100 characters or fewer.");
        }
        if (StringUtils.hasText(command.externalCode()) && !normalizeNullable(command.externalCode()).matches(ALPHANUMERIC_CODE_REGEX)) {
            throw new IllegalArgumentException("External code must be 50 characters or fewer and use letters, numbers, dashes, underscores, or slashes.");
        }
        if (command.lowStockThreshold() != null && command.lowStockThreshold() < 0) {
            throw new IllegalArgumentException("Reorder level cannot be negative.");
        }
        if (command.unitCost() != null && command.unitCost().signum() < 0) {
            throw new IllegalArgumentException("Purchase rate cannot be negative.");
        }
        if (command.purchasePrice() != null && command.purchasePrice().signum() < 0) {
            throw new IllegalArgumentException("Purchase rate cannot be negative.");
        }
        if (command.sellingPrice() != null && command.sellingPrice().signum() < 0) {
            throw new IllegalArgumentException("MRP cannot be negative.");
        }
        if (command.sellingPrice() != null && command.unitCost() != null && command.sellingPrice().compareTo(command.unitCost()) < 0) {
            throw new IllegalArgumentException("MRP cannot be less than purchase rate.");
        }
        if (isSellableMedicine(medicine.getMedicineType()) && command.expiryDate() == null) {
            throw new IllegalArgumentException("Expiry date is required for sellable medicines.");
        }
        if (command.expiryDate() != null && command.active() && command.expiryDate().isBefore(today())) {
            throw new IllegalArgumentException("Expiry date cannot be in the past for active stock.");
        }
        ensureUniqueStockIdentifiers(tenantId, command, currentStock == null ? null : currentStock.getId());
    }

    private void ensureUniqueStockBatch(UUID tenantId, UUID medicineId, UUID locationId, String batchNumber, UUID id) {
        if (!StringUtils.hasText(batchNumber)) {
            return;
        }
        boolean duplicate = id == null
                ? stockRepository.findByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCase(tenantId, medicineId, locationId, batchNumber).isPresent()
                : stockRepository.existsByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCaseAndIdNot(tenantId, medicineId, locationId, batchNumber, id);
        if (duplicate) {
            String medicineName = medicineRepository.findByTenantIdAndId(tenantId, medicineId).map(MedicineEntity::getMedicineName).orElse("this medicine");
            String locationName = locationRepository.findByTenantIdAndId(tenantId, locationId).map(InventoryLocationEntity::getLocationName).orElse("this location");
            throw new IllegalArgumentException("Batch " + batchNumber.trim().toUpperCase(Locale.ROOT) + " already exists for " + medicineName + " at " + locationName + ".");
        }
    }

    private void ensureUniqueStockIdentifiers(UUID tenantId, StockUpsertCommand command, UUID currentStockId) {
        if (StringUtils.hasText(command.barcode())) {
            stockRepository.findByTenantIdAndBarcodeIgnoreCase(tenantId, normalizeNullable(command.barcode()))
                    .filter(stock -> currentStockId == null || !stock.getId().equals(currentStockId))
                    .ifPresent(stock -> { throw new IllegalArgumentException("Stock barcode already exists"); });
        }
        if (StringUtils.hasText(command.qrCode())) {
            stockRepository.findByTenantIdAndQrCodeIgnoreCase(tenantId, normalizeNullable(command.qrCode()))
                    .filter(stock -> currentStockId == null || !stock.getId().equals(currentStockId))
                    .ifPresent(stock -> { throw new IllegalArgumentException("Stock QR code already exists"); });
        }
        if (StringUtils.hasText(command.externalCode())) {
            stockRepository.findByTenantIdAndExternalCodeIgnoreCase(tenantId, normalizeNullable(command.externalCode()))
                    .filter(stock -> currentStockId == null || !stock.getId().equals(currentStockId))
                    .ifPresent(stock -> { throw new IllegalArgumentException("Stock external code already exists"); });
        }
    }

    private int effectiveCreatedQuantity(StockUpsertCommand command) {
        if (command.quantityOnHand() > 0) {
            return command.quantityOnHand();
        }
        return command.quantityReceived() == null ? 0 : command.quantityReceived();
    }

    private void validateTransaction(UUID tenantId, InventoryTransactionCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        requireId(command.medicineId(), "medicineId");
        if (command.transactionType() == null) throw new IllegalArgumentException("transactionType is required");
        if (command.quantity() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (requiresReason(command.transactionType()) && !StringUtils.hasText(command.reason())) {
            throw new IllegalArgumentException("reason is required");
        }
        if (requiresBatch(command.transactionType()) && command.stockBatchId() == null) {
            throw new IllegalArgumentException("stockBatchId is required");
        }
        if (StringUtils.hasText(command.businessReference()) && command.businessReference().trim().length() > 160) {
            throw new IllegalArgumentException("businessReference must be 160 characters or fewer");
        }
        if (medicineRepository.findByTenantIdAndId(tenantId, command.medicineId()).isEmpty()) throw new IllegalArgumentException("Medicine not found");
    }

    private boolean requiresReason(InventoryTransactionType type) {
        return switch (type) {
            case ADJUSTMENT_IN, ADJUSTMENT_OUT, ADJUSTMENT, RETURN, CUSTOMER_RETURN_IN, CUSTOMER_RETURN_NON_SELLABLE, VENDOR_RETURN_OUT, WRITE_OFF, EXPIRED, DISPENSED, VACCINATION_ADMINISTERED, SALE -> true;
            default -> false;
        };
    }

    private boolean requiresBatch(InventoryTransactionType type) {
        return switch (type) {
            case CUSTOMER_RETURN_IN, CUSTOMER_RETURN_NON_SELLABLE, VENDOR_RETURN_OUT, WRITE_OFF, DISPENSED, VACCINATION_ADMINISTERED, SALE, RETURN, EXPIRED, ADJUSTMENT_IN, ADJUSTMENT_OUT, ADJUSTMENT -> true;
            default -> false;
        };
    }

    private void ensureUniqueMedicine(UUID tenantId, MedicineUpsertCommand command, UUID id) {
        String normalizedName = normalize(command.medicineName());
        String normalizedType = normalizeType(command.medicineType());
        String normalizedStrength = normalize(command.strength());
        boolean duplicate = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .filter(medicine -> id == null || !medicine.getId().equals(id))
                .anyMatch(medicine ->
                        normalize(medicine.getMedicineName()).equalsIgnoreCase(normalizedName)
                                && normalizeType(medicine.getMedicineType()).equalsIgnoreCase(normalizedType)
                                && normalize(medicine.getStrength()).equalsIgnoreCase(normalizedStrength));
        if (duplicate) {
            throw new IllegalArgumentException("Medicine already exists with the same name, type, and strength");
        }
        if (StringUtils.hasText(command.barcode()) && medicineRepository.findByTenantIdAndBarcodeIgnoreCase(tenantId, normalizeNullable(command.barcode())).filter(medicine -> id == null || !medicine.getId().equals(id)).isPresent()) {
            throw new IllegalArgumentException("Medicine barcode already exists");
        }
        if (StringUtils.hasText(command.externalCode()) && medicineRepository.findByTenantIdAndExternalCodeIgnoreCase(tenantId, normalizeNullable(command.externalCode())).filter(medicine -> id == null || !medicine.getId().equals(id)).isPresent()) {
            throw new IllegalArgumentException("Medicine external code already exists");
        }
    }

    private boolean isSellableMedicine(String medicineType) {
        if (!StringUtils.hasText(medicineType)) {
            return false;
        }
        String type = medicineType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROP", "DROPS", "OINTMENT", "SACHET" -> true;
            default -> false;
        };
    }

    private String normalize(String value) { return value == null ? null : value.trim(); }
    private String normalizeNullable(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String normalizeType(String value) { return normalize(value).toUpperCase(Locale.ROOT); }
    private BigDecimal normalizeMoney(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }
    private int effectiveThreshold(StockEntity stock) { return stock.getLowStockThreshold() == null ? 5 : Math.max(0, stock.getLowStockThreshold()); }
    private LocalDate today() { return LocalDate.now(); }

    private UUID resolveLocationId(UUID tenantId, UUID requestedLocationId) {
        return resolveLocation(tenantId, requestedLocationId).getId();
    }

    private InventoryLocationEntity resolveLocation(UUID tenantId, UUID requestedLocationId) {
        if (requestedLocationId != null) {
            return locationRepository.findByTenantIdAndId(tenantId, requestedLocationId)
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));
        }
        return ensureDefaultLocation(tenantId);
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

    private PhysicalCountSessionRecord toRecord(PhysicalCountSessionEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("physical count session not found");
        }
        PhysicalCountSessionRecord persisted = deserializePhysicalCountSession(entity.getSessionJson());
        return new PhysicalCountSessionRecord(
                entity.getId(),
                entity.getTenantId(),
                persisted.sessionName(),
                persisted.locationId(),
                persisted.locationName(),
                persisted.scope(),
                persisted.scopeLabel(),
                persisted.reason(),
                persisted.status(),
                persisted.lines(),
                persisted.audit(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PhysicalCountSessionRecord toRecord(UUID tenantId, UUID id, PhysicalCountSessionSaveCommand command, String locationName) {
        return new PhysicalCountSessionRecord(
                id,
                tenantId,
                normalize(command.sessionName()),
                command.locationId(),
                normalize(locationName),
                normalize(command.scope()).toUpperCase(Locale.ROOT),
                normalize(command.scopeLabel()),
                normalize(command.reason()),
                normalize(command.status()).toUpperCase(Locale.ROOT),
                command.lines().stream()
                        .map(this::normalizePhysicalCountLine)
                        .toList(),
                normalizePhysicalCountAudit(command.audit()),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private PhysicalCountSessionLine normalizePhysicalCountLine(PhysicalCountSessionLine line) {
        return new PhysicalCountSessionLine(
                normalize(line.id()),
                line.medicineId(),
                normalize(line.medicineName()),
                normalize(line.batchNumber()),
                line.locationId(),
                normalize(line.locationName()),
                line.stockBatchId(),
                line.systemQty(),
                normalizeNullable(line.countedQty()),
                normalizeNullable(line.reason()),
                normalizeNullable(line.reviewerRemarks()),
                line.flagged(),
                line.reviewed()
        );
    }

    private PhysicalCountAuditFields normalizePhysicalCountAudit(PhysicalCountAuditFields audit) {
        return new PhysicalCountAuditFields(
                normalizeNullable(audit.createdBy()),
                normalizeNullable(audit.createdAt()),
                normalizeNullable(audit.startedBy()),
                normalizeNullable(audit.startedAt()),
                normalizeNullable(audit.lastUpdatedAt()),
                normalizeNullable(audit.submittedBy()),
                normalizeNullable(audit.submittedAt()),
                normalizeNullable(audit.reviewedBy()),
                normalizeNullable(audit.reviewedAt()),
                normalizeNullable(audit.reviewer()),
                normalizeNullable(audit.reviewedDate()),
                normalizeNullable(audit.approvedBy()),
                normalizeNullable(audit.approvedAt()),
                normalizeNullable(audit.approvalNotes()),
                normalizeNullable(audit.rejectedBy()),
                normalizeNullable(audit.rejectedAt()),
                normalizeNullable(audit.rejectionReason()),
                normalizeNullable(audit.returnedBy()),
                normalizeNullable(audit.returnedAt()),
                normalizeNullable(audit.returnReason()),
                normalizeNullable(audit.postedBy()),
                normalizeNullable(audit.postedAt()),
                normalizeNullable(audit.sessionDuration()),
                normalizeNullable(audit.generalNotes()),
                normalizeNullable(audit.counterNotes()),
                normalizeNullable(audit.reviewerNotes()),
                normalizeNullable(audit.auditNotes()),
                audit.reviewChecklist() == null
                        ? new PhysicalCountReviewChecklist(false, false, false, false)
                        : audit.reviewChecklist()
        );
    }

    private PhysicalCountSessionRecord deserializePhysicalCountSession(String sessionJson) {
        if (!StringUtils.hasText(sessionJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(sessionJson, PhysicalCountSessionRecord.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to read physical count session payload", ex);
        }
    }

    private void validatePhysicalCountSession(PhysicalCountSessionSaveCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        validateRequiredText(normalizeNullable(command.sessionName()), 256, 1, "sessionName", false, true);
        requireId(command.locationId(), "locationId");
        validateRequiredText(normalizeNullable(command.locationName()), 256, 1, "locationName", false, true);
        validateRequiredText(normalizeNullable(command.scope()), 32, 1, "scope", false, true);
        validateRequiredText(normalizeNullable(command.scopeLabel()), 128, 1, "scopeLabel", false, true);
        validateRequiredText(normalizeNullable(command.reason()), 64, 1, "reason", false, true);
        validateRequiredText(normalizeNullable(command.status()), 24, 1, "status", false, true);
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("lines are required");
        }
        String status = normalize(command.status()).toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "IN_PROGRESS", "SUBMITTED", "REVIEWED", "APPROVED", "POSTED", "REJECTED").contains(status)) {
            throw new IllegalArgumentException("status must be one of DRAFT, IN_PROGRESS, SUBMITTED, REVIEWED, APPROVED, POSTED, REJECTED");
        }
        String scope = normalize(command.scope()).toUpperCase(Locale.ROOT);
        if (!List.of("ENTIRE_INVENTORY", "CATEGORY", "SELECTED_MEDICINES").contains(scope)) {
            throw new IllegalArgumentException("scope must be one of ENTIRE_INVENTORY, CATEGORY, SELECTED_MEDICINES");
        }
        if (List.of("SUBMITTED", "REVIEWED", "APPROVED", "POSTED").contains(status)) {
            for (PhysicalCountSessionLine line : command.lines()) {
                if (!StringUtils.hasText(line.countedQty())) {
                    throw new IllegalArgumentException("counted quantity is required for submitted sessions");
                }
                parseCountedQuantity(line.countedQty());
            }
        }
        for (PhysicalCountSessionLine line : command.lines()) {
            validatePhysicalCountSessionLine(line);
        }
        validatePhysicalCountAudit(command.audit());
    }

    private void validatePhysicalCountSessionLine(PhysicalCountSessionLine line) {
        if (line == null) {
            throw new IllegalArgumentException("line is required");
        }
        validateRequiredText(normalizeNullable(line.id()), 120, 1, "line.id", false, true);
        requireId(line.medicineId(), "line.medicineId");
        validateRequiredText(normalizeNullable(line.medicineName()), 256, 1, "line.medicineName", false, true);
        validateRequiredText(normalizeNullable(line.batchNumber()), 128, 1, "line.batchNumber", false, true);
        requireId(line.locationId(), "line.locationId");
        validateRequiredText(normalizeNullable(line.locationName()), 256, 1, "line.locationName", false, true);
        requireId(line.stockBatchId(), "line.stockBatchId");
        if (line.systemQty() < 0) {
            throw new IllegalArgumentException("line.systemQty must be zero or greater");
        }
        if (StringUtils.hasText(line.countedQty())) {
            parseCountedQuantity(line.countedQty());
        }
    }

    private void validatePhysicalCountAudit(PhysicalCountAuditFields audit) {
        if (audit == null) {
            throw new IllegalArgumentException("audit is required");
        }
        validateOptionalText(audit.createdBy(), 120, null, "audit.createdBy");
        validateOptionalText(audit.createdAt(), 40, null, "audit.createdAt");
        validateOptionalText(audit.startedBy(), 120, null, "audit.startedBy");
        validateOptionalText(audit.startedAt(), 40, null, "audit.startedAt");
        validateOptionalText(audit.lastUpdatedAt(), 40, null, "audit.lastUpdatedAt");
        validateOptionalText(audit.submittedBy(), 120, null, "audit.submittedBy");
        validateOptionalText(audit.submittedAt(), 40, null, "audit.submittedAt");
        validateOptionalText(audit.reviewedBy(), 120, null, "audit.reviewedBy");
        validateOptionalText(audit.reviewedAt(), 40, null, "audit.reviewedAt");
        validateOptionalText(audit.reviewer(), 120, null, "audit.reviewer");
        validateOptionalText(audit.reviewedDate(), 40, null, "audit.reviewedDate");
        validateOptionalText(audit.approvedBy(), 120, null, "audit.approvedBy");
        validateOptionalText(audit.approvedAt(), 40, null, "audit.approvedAt");
        validateOptionalText(audit.approvalNotes(), 500, null, "audit.approvalNotes");
        validateOptionalText(audit.rejectedBy(), 120, null, "audit.rejectedBy");
        validateOptionalText(audit.rejectedAt(), 40, null, "audit.rejectedAt");
        validateOptionalText(audit.rejectionReason(), 500, null, "audit.rejectionReason");
        validateOptionalText(audit.returnedBy(), 120, null, "audit.returnedBy");
        validateOptionalText(audit.returnedAt(), 40, null, "audit.returnedAt");
        validateOptionalText(audit.returnReason(), 500, null, "audit.returnReason");
        validateOptionalText(audit.postedBy(), 120, null, "audit.postedBy");
        validateOptionalText(audit.postedAt(), 40, null, "audit.postedAt");
        validateOptionalText(audit.sessionDuration(), 120, null, "audit.sessionDuration");
        validateOptionalText(audit.generalNotes(), 500, null, "audit.generalNotes");
        validateOptionalText(audit.counterNotes(), 500, null, "audit.counterNotes");
        validateOptionalText(audit.reviewerNotes(), 500, null, "audit.reviewerNotes");
        validateOptionalText(audit.auditNotes(), 500, null, "audit.auditNotes");
    }

    private void validatePhysicalCountSessionTransition(PhysicalCountSessionRecord current, PhysicalCountSessionSaveCommand command, Set<String> actorRoles) {
        Set<String> roles = normalizeRoles(actorRoles);
        boolean clinicAdmin = roles.contains("CLINIC_ADMIN");
        boolean makerRole = clinicAdmin || hasAnyRole(roles, "PHARMACIST", "PHARMA", "PHARMACY");
        boolean checkerRole = clinicAdmin || hasAnyRole(roles, "PHARMACY_INVENTORY_MANAGER");
        if (!makerRole && !checkerRole) {
            throw new IllegalArgumentException("You do not have permission to save physical count sessions.");
        }

        String nextStatus = normalize(command.status()).toUpperCase(Locale.ROOT);
        if (current == null) {
            if (!List.of("DRAFT", "IN_PROGRESS", "SUBMITTED").contains(nextStatus)) {
                throw new IllegalArgumentException("New physical count sessions must start in DRAFT, IN_PROGRESS, or SUBMITTED state.");
            }
            if (!makerRole) {
                throw new IllegalArgumentException("You do not have permission to create physical count sessions.");
            }
            validatePhysicalCountSessionCompletion(command, nextStatus);
            return;
        }

        String currentStatus = normalize(current.status()).toUpperCase(Locale.ROOT);
        if (List.of("POSTED", "REJECTED").contains(currentStatus) && !clinicAdmin) {
            throw new IllegalArgumentException(capitalize(currentStatus) + " physical count sessions cannot be modified.");
        }

        if (List.of("DRAFT", "IN_PROGRESS").contains(currentStatus)) {
            if (!makerRole) {
                throw new IllegalArgumentException("You do not have permission to edit physical count sessions.");
            }
            if (!List.of("DRAFT", "IN_PROGRESS", "SUBMITTED").contains(nextStatus)) {
                throw new IllegalArgumentException("Draft or in-progress sessions can only be saved as DRAFT, IN_PROGRESS, or SUBMITTED.");
            }
            validatePhysicalCountSessionCompletion(command, nextStatus);
            return;
        }

        if ("SUBMITTED".equals(currentStatus)) {
            if (!checkerRole) {
                throw new IllegalArgumentException("You do not have permission to review physical count sessions.");
            }
            if (!List.of("SUBMITTED", "REVIEWED").contains(nextStatus)) {
                throw new IllegalArgumentException("Submitted sessions can only be saved as SUBMITTED or REVIEWED.");
            }
            ensureMakerFieldsUnchanged(current, command);
            return;
        }

        if ("REVIEWED".equals(currentStatus)) {
            if (!checkerRole) {
                throw new IllegalArgumentException("You do not have permission to continue reviewed physical count sessions.");
            }
            if (!List.of("REVIEWED", "APPROVED", "REJECTED", "IN_PROGRESS").contains(nextStatus)) {
                throw new IllegalArgumentException("Reviewed sessions can only be saved as REVIEWED, APPROVED, REJECTED, or IN_PROGRESS.");
            }
            ensureMakerFieldsUnchanged(current, command);
            return;
        }

        if ("APPROVED".equals(currentStatus)) {
            if (!checkerRole) {
                throw new IllegalArgumentException("You do not have permission to post approved physical count sessions.");
            }
            if (!List.of("APPROVED", "POSTED").contains(nextStatus)) {
                throw new IllegalArgumentException("Approved sessions can only be saved as APPROVED or POSTED.");
            }
            ensureMakerFieldsUnchanged(current, command);
            return;
        }

        throw new IllegalArgumentException("Unsupported physical count session status transition.");
    }

    private void validatePhysicalCountSessionCompletion(PhysicalCountSessionSaveCommand command, String nextStatus) {
        if (!"SUBMITTED".equals(nextStatus)) {
            return;
        }
        for (PhysicalCountSessionLine line : command.lines()) {
            if (!StringUtils.hasText(line.countedQty())) {
                throw new IllegalArgumentException("counted quantity is required for submitted sessions");
            }
            parseCountedQuantity(line.countedQty());
        }
    }

    private void ensureMakerFieldsUnchanged(PhysicalCountSessionRecord current, PhysicalCountSessionSaveCommand command) {
        if (current.lines() == null || command.lines() == null || current.lines().size() != command.lines().size()) {
            throw new IllegalArgumentException("Physical count lines cannot be added or removed in the current workflow state.");
        }

        for (int i = 0; i < current.lines().size(); i++) {
            PhysicalCountSessionLine existingLine = current.lines().get(i);
            PhysicalCountSessionLine incomingLine = command.lines().get(i);
            if (!Objects.equals(normalize(existingLine.id()), normalize(incomingLine.id()))
                    || !Objects.equals(normalizeNullable(existingLine.countedQty()), normalizeNullable(incomingLine.countedQty()))
                    || !Objects.equals(normalizeNullable(existingLine.reason()), normalizeNullable(incomingLine.reason()))) {
                throw new IllegalArgumentException("Counted quantities and maker line details cannot be modified in the current workflow state.");
            }
        }
    }

    private Set<String> normalizeRoles(Set<String> actorRoles) {
        if (actorRoles == null || actorRoles.isEmpty()) {
            return Set.of();
        }
        return actorRoles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean hasAnyRole(Set<String> roles, String... expectedRoles) {
        if (roles == null || roles.isEmpty() || expectedRoles == null || expectedRoles.length == 0) {
            return false;
        }
        for (String role : expectedRoles) {
            if (role != null && roles.contains(role.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String capitalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private Integer parseCountedQuantity(String countedQty) {
        try {
            return Integer.valueOf(normalize(countedQty));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("countedQty must be a whole number", ex);
        }
    }

    private void auditMedicine(UUID tenantId, MedicineEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, MEDICINE_ENTITY, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private void auditStock(UUID tenantId, StockEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, STOCK_ENTITY, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private void auditTransaction(UUID tenantId, InventoryTransactionEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, TRANSACTION_ENTITY, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private IllegalArgumentException translateStockIntegrityViolation(DataIntegrityViolationException ex) {
        if (ex != null && ex.getMessage() != null && ex.getMessage().contains("uq_inventory_stocks_tenant_medicine_location_batch")) {
            return new IllegalArgumentException("Stock batch already exists for this medicine and location. Edit existing batch or use a different batch number.", ex);
        }
        return new IllegalArgumentException("Stock could not be saved", ex);
    }

    private String detailsJson(Object entity) {
        try { return objectMapper.writeValueAsString(entity); }
        catch (JsonProcessingException ex) { return "{}"; }
    }

    private void requireTenant(UUID tenantId) { if (tenantId == null) throw new IllegalArgumentException("tenantId is required"); }
    private void requireId(UUID id, String field) { if (id == null) throw new IllegalArgumentException(field + " is required"); }
    private boolean isExpired(StockEntity stock) { return stock != null && stock.getExpiryDate() != null && stock.getExpiryDate().isBefore(today()); }
}
