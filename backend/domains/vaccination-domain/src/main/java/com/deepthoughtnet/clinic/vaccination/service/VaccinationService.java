package com.deepthoughtnet.clinic.vaccination.service;

import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.BillUpsertCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationEntity;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationRepository;
import com.deepthoughtnet.clinic.vaccination.db.VaccineMasterEntity;
import com.deepthoughtnet.clinic.vaccination.db.VaccineMasterRepository;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationCommand;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineMasterRecord;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VaccinationService {
    private static final String MASTER_ENTITY_TYPE = "VACCINE";
    private static final String RECORD_ENTITY_TYPE = "PATIENT_VACCINATION";
    private static final java.util.Set<String> ALLOWED_RECOMMENDATION_POLICIES = java.util.Set.of(
            "STANDARD_CHILDHOOD",
            "CHILDHOOD_CATCHUP",
            "ADULT_ROUTINE",
            "ADULT_RISK_BASED",
            "PREGNANCY",
            "TRAVEL",
            "OCCUPATIONAL",
            "RECURRING",
            "CLINIC_CUSTOM"
    );
    private static final java.util.Set<String> ALLOWED_CATCH_UP_POLICIES = java.util.Set.of(
            "NONE",
            "ALLOWED_UNTIL_AGE",
            "LIFETIME",
            "CLINICIAN_DECISION"
    );
    private static final java.util.Set<String> ALLOWED_APPLICABLE_AGE_GROUPS = java.util.Set.of(
            "NEWBORN",
            "INFANT",
            "TODDLER",
            "CHILD",
            "ADOLESCENT",
            "ADULT",
            "OLDER_ADULT",
            "ALL"
    );

    private final VaccineMasterRepository vaccineMasterRepository;
    private final PatientVaccinationRepository patientVaccinationRepository;
    private final PatientRepository patientRepository;
    private final ClinicProfileService clinicProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final BillingService billingService;
    private final InventoryService inventoryService;
    private final NotificationHistoryService notificationHistoryService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public VaccinationService(
            VaccineMasterRepository vaccineMasterRepository,
            PatientVaccinationRepository patientVaccinationRepository,
            PatientRepository patientRepository,
            ClinicProfileService clinicProfileService,
            TenantUserManagementService tenantUserManagementService,
            BillingService billingService,
            InventoryService inventoryService,
            NotificationHistoryService notificationHistoryService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.vaccineMasterRepository = vaccineMasterRepository;
        this.patientVaccinationRepository = patientVaccinationRepository;
        this.patientRepository = patientRepository;
        this.clinicProfileService = clinicProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.billingService = billingService;
        this.inventoryService = inventoryService;
        this.notificationHistoryService = notificationHistoryService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<VaccineMasterRecord> listVaccines(UUID tenantId) {
        requireTenant(tenantId);
        return vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(tenantId).stream().map(this::toMasterRecord).toList();
    }

    @Transactional(readOnly = true)
    public Optional<VaccineMasterRecord> findVaccine(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return vaccineMasterRepository.findByTenantIdAndId(tenantId, id).map(this::toMasterRecord);
    }

    @Transactional
    public VaccineMasterRecord createVaccine(UUID tenantId, VaccineUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateMaster(command);
        ensureUniqueName(tenantId, normalize(command.vaccineName()), null);
        ensureUniqueMasterCombination(tenantId, command, null);
        VaccineMasterEntity entity = VaccineMasterEntity.create(tenantId, normalize(command.vaccineName()));
        applyVaccineMetadata(entity, command);
        VaccineMasterEntity saved = vaccineMasterRepository.save(entity);
        auditMaster(tenantId, saved, "vaccine.created", actorAppUserId, "Created vaccine master");
        return toMasterRecord(saved);
    }

    @Transactional
    public VaccineMasterRecord updateVaccine(UUID tenantId, UUID id, VaccineUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validateMaster(command);
        VaccineMasterEntity entity = vaccineMasterRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Vaccine not found"));
        ensureUniqueName(tenantId, normalize(command.vaccineName()), id);
        ensureUniqueMasterCombination(tenantId, command, id);
        applyVaccineMetadata(entity, command);
        VaccineMasterEntity saved = vaccineMasterRepository.save(entity);
        auditMaster(tenantId, saved, "vaccine.updated", actorAppUserId, "Updated vaccine master");
        return toMasterRecord(saved);
    }

    @Transactional
    public VaccineMasterRecord deactivateVaccine(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        VaccineMasterEntity entity = vaccineMasterRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Vaccine not found"));
        entity.update(
                entity.getVaccineName(),
                entity.getDescription(),
                entity.getManufacturer(),
                entity.getBrandName(),
                entity.getVaccineGroup(),
                entity.getDoseNumber(),
                entity.getRoute(),
                entity.getAdministrationSite(),
                entity.getStorageTemperature(),
                entity.getNdcBarcode(),
                entity.getScheduleType(),
                entity.getAgeGroup(),
                entity.getMinAgeDays(),
                entity.getRecommendedAgeDays(),
                entity.getMaxAgeDays(),
                entity.getRecommendedGapDays(),
                entity.getBoosterGapDays(),
                entity.getBoosterRules(),
                entity.isRecurring(),
                entity.getRecurrenceDays(),
                entity.getRecommendationPolicy(),
                entity.getCatchUpPolicy(),
                entity.getCatchUpMaxAgeDays(),
                entity.getApplicableAgeGroup(),
                entity.getClinicalIndications(),
                entity.getDefaultPrice(),
                false
        );
        VaccineMasterEntity saved = vaccineMasterRepository.save(entity);
        auditMaster(tenantId, saved, "vaccine.deactivated", actorAppUserId, "Deactivated vaccine master");
        return toMasterRecord(saved);
    }

    @Transactional(readOnly = true)
    public List<PatientVaccinationRecord> listByPatient(UUID tenantId, UUID patientId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        return mapRecords(tenantId, patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(tenantId, patientId), billHistoryByVaccination(tenantId));
    }

    @Transactional(readOnly = true)
    public List<PatientVaccinationRecord> listHistory(UUID tenantId) {
        requireTenant(tenantId);
        return mapRecords(
                tenantId,
                patientVaccinationRepository.findByTenantIdOrderByGivenDateDesc(tenantId),
                billHistoryByVaccination(tenantId)
        );
    }

    /**
     * Returns one tenant-scoped patient vaccination record by id.
     */
    @Transactional(readOnly = true)
    public Optional<PatientVaccinationRecord> findById(UUID tenantId, UUID vaccinationId) {
        requireTenant(tenantId);
        requireId(vaccinationId, "vaccinationId");
        return patientVaccinationRepository.findByTenantIdAndId(tenantId, vaccinationId)
                .map(entity -> toRecord(entity, tenantData(tenantId), billHistoryByVaccination(tenantId).get(vaccinationId)));
    }

    @Transactional
    public PatientVaccinationRecord recordVaccination(UUID tenantId, UUID patientId, PatientVaccinationCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        validateRecord(command);
        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        String source = normalizeSource(command.source());
        VaccineMasterEntity vaccine = null;
        if (command.vaccineId() != null) {
            vaccine = vaccineMasterRepository.findByTenantIdAndId(tenantId, command.vaccineId())
                    .orElseThrow(() -> new IllegalArgumentException("Vaccine not found"));
            if (!vaccine.isActive()) {
                throw new IllegalArgumentException("Vaccine is inactive");
            }
        }
        if ("EXTERNAL".equals(source) && vaccine == null && StringUtils.hasText(command.vaccineName())) {
            vaccine = vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(tenantId).stream()
                    .filter(candidate -> candidate.isActive() && candidate.getVaccineName() != null && candidate.getVaccineName().trim().equalsIgnoreCase(command.vaccineName().trim()))
                    .findFirst()
                    .orElse(null);
        }

        UUID administeredBy = command.administeredByUserId() == null ? actorAppUserId : command.administeredByUserId();
        LocalDate givenDate = command.givenDate() == null ? LocalDate.now() : command.givenDate();
        LocalDate nextDueDate = command.nextDueDate();
        if (!"EXTERNAL".equals(source) && nextDueDate == null && vaccine != null && vaccine.getRecommendedGapDays() != null) {
            nextDueDate = givenDate.plusDays(vaccine.getRecommendedGapDays());
        }
        String vaccineNameSnapshot = resolveVaccineNameSnapshot(command, vaccine);
        String verifiedStatus = normalizeVerifiedStatus(command.verifiedStatus(), source);

        PatientVaccinationEntity entity = PatientVaccinationEntity.create(
                tenantId,
                patientId,
                vaccine == null ? null : vaccine.getId(),
                vaccineNameSnapshot,
                source,
                normalizeNullable(command.externalPlace()),
                command.proofDocumentId(),
                verifiedStatus,
                "VERIFIED".equalsIgnoreCase(verifiedStatus) ? actorAppUserId : null,
                "VERIFIED".equalsIgnoreCase(verifiedStatus) ? OffsetDateTime.now() : null,
                command.doseNumber(),
                givenDate,
                nextDueDate,
                normalizeNullable(command.batchNumber()),
                normalizeNullable(command.notes()),
                administeredBy,
                actorAppUserId
        );
        PatientVaccinationEntity saved = patientVaccinationRepository.save(entity);

        List<String> warnings = new ArrayList<>();
        BillLinkResult billLink = "EXTERNAL".equals(source) ? null : linkBillIfNeeded(tenantId, saved, vaccine, command, actorAppUserId);
        InventoryLinkResult inventoryLink = "EXTERNAL".equals(source) ? null : linkInventoryIfAvailable(tenantId, saved, patient, vaccine, command, actorAppUserId, warnings);
        ReminderLinkResult reminderLink = "EXTERNAL".equals(source) ? null : queueReminderIfNeeded(tenantId, saved, patient, actorAppUserId);

        if (billLink != null) {
            saved.linkBill(billLink.billId(), billLink.billLineId(), billLink.billNumber(), billLink.billStatus(), actorAppUserId);
        }
        if (inventoryLink != null) {
            saved.linkInventory(
                    inventoryLink.inventoryTransactionId(),
                    inventoryLink.stockBatchId(),
                    inventoryLink.batchNumber(),
                    inventoryLink.manufacturer(),
                    inventoryLink.expiryDate(),
                    actorAppUserId
            );
        }
        if (reminderLink != null) {
            saved.linkReminder(reminderLink.notificationId(), reminderLink.status(), reminderLink.queuedAt(), actorAppUserId);
        }
        patientVaccinationRepository.save(saved);

        auditRecord(tenantId, saved, "vaccination.recorded", actorAppUserId, "Recorded patient vaccination");
        return toRecord(saved, tenantData(tenantId), billHistoryByVaccination(tenantId).get(saved.getId()), warnings);
    }

    @Transactional(readOnly = true)
    public List<PatientVaccinationRecord> listDue(UUID tenantId) {
        requireTenant(tenantId);
        LocalDate today = LocalDate.now();
        return mapRecords(tenantId, patientVaccinationRepository.findByTenantIdOrderByGivenDateDesc(tenantId)
                .stream()
                .filter(record -> record.getNextDueDate() != null && !record.getNextDueDate().isBefore(today))
                .toList(), billHistoryByVaccination(tenantId));
    }

    @Transactional(readOnly = true)
    public List<PatientVaccinationRecord> listOverdue(UUID tenantId) {
        requireTenant(tenantId);
        LocalDate today = LocalDate.now();
        return mapRecords(tenantId, patientVaccinationRepository.findByTenantIdOrderByGivenDateDesc(tenantId)
                .stream()
                .filter(record -> record.getNextDueDate() != null && record.getNextDueDate().isBefore(today))
                .toList(), billHistoryByVaccination(tenantId));
    }

    @Transactional
    public PatientVaccinationRecord updateExternalVaccination(
            UUID tenantId,
            UUID patientId,
            UUID vaccinationId,
            String externalPlace,
            UUID proofDocumentId,
            String verifiedStatus,
            UUID actorAppUserId
    ) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        requireId(vaccinationId, "vaccinationId");
        PatientVaccinationEntity entity = patientVaccinationRepository.findByTenantIdAndId(tenantId, vaccinationId)
                .orElseThrow(() -> new IllegalArgumentException("Vaccination record not found"));
        if (!patientId.equals(entity.getPatientId())) {
            throw new IllegalArgumentException("Vaccination record does not belong to patient");
        }
        String source = normalizeSource(entity.getSource());
        String normalizedStatus = normalizeVerifiedStatus(verifiedStatus, source);
        UUID verifiedBy = "VERIFIED".equalsIgnoreCase(normalizedStatus) ? actorAppUserId : null;
        OffsetDateTime verifiedAt = "VERIFIED".equalsIgnoreCase(normalizedStatus) ? OffsetDateTime.now() : null;
        entity.updateExternalDetails(
                normalizeNullable(externalPlace),
                proofDocumentId,
                normalizedStatus,
                verifiedBy,
                verifiedAt,
                actorAppUserId
        );
        PatientVaccinationEntity saved = patientVaccinationRepository.save(entity);
        auditRecord(tenantId, saved, "vaccination.external.updated", actorAppUserId, "Updated external vaccination history");
        return toRecord(saved, tenantData(tenantId), billHistoryByVaccination(tenantId).get(saved.getId()));
    }

    private BillLinkResult linkBillIfNeeded(UUID tenantId, PatientVaccinationEntity saved, VaccineMasterEntity vaccine, PatientVaccinationCommand command, UUID actorAppUserId) {
        if (!command.addToBill()) {
            return null;
        }
        BigDecimal price = command.billItemUnitPrice() != null
                ? normalizeMoney(command.billItemUnitPrice())
                : normalizeMoney(vaccine.getDefaultPrice() == null ? BigDecimal.ZERO : vaccine.getDefaultPrice());
        BillRecord linkedBill;
        if (command.billId() != null) {
            linkedBill = addVaccinationLineToBill(tenantId, command.billId(), saved, vaccine, price, actorAppUserId);
        } else {
            linkedBill = findReusableVaccinationBill(tenantId, saved.getPatientId())
                    .map(bill -> addVaccinationLineToBill(tenantId, bill.id(), saved, vaccine, price, actorAppUserId))
                    .orElseGet(() -> createVaccinationBill(tenantId, saved, vaccine, price, actorAppUserId));
        }
        return linkedBill.lines().stream()
                .filter(line -> line.itemType() == BillItemType.VACCINATION && saved.getId().equals(line.referenceId()))
                .findFirst()
                .map(line -> new BillLinkResult(linkedBill.id(), line.id(), linkedBill.billNumber(), linkedBill.status().name()))
                .orElse(new BillLinkResult(linkedBill.id(), null, linkedBill.billNumber(), linkedBill.status().name()));
    }

    private BillRecord addVaccinationLineToBill(UUID tenantId, UUID billId, PatientVaccinationEntity saved, VaccineMasterEntity vaccine, BigDecimal price, UUID actorAppUserId) {
        billingService.addLineItem(
                tenantId,
                billId,
                new BillLineCommand(
                        BillItemType.VACCINATION,
                        vaccine.getVaccineName(),
                        1,
                        price,
                        saved.getId(),
                        null,
                        BigDecimal.ZERO,
                        normalizeNullable(saved.getBatchNumber()),
                        null
                ),
                actorAppUserId
        );
        return billingService.findById(tenantId, billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
    }

    private BillRecord createVaccinationBill(UUID tenantId, PatientVaccinationEntity saved, VaccineMasterEntity vaccine, BigDecimal price, UUID actorAppUserId) {
        BillRecord created = billingService.createDraft(tenantId, new BillUpsertCommand(
                saved.getPatientId(),
                null,
                null,
                saved.getGivenDate(),
                com.deepthoughtnet.clinic.billing.service.model.DiscountType.NONE,
                BigDecimal.ZERO,
                null,
                null,
                null,
                BigDecimal.ZERO,
                "Vaccination billing",
                List.of(new BillLineCommand(
                        BillItemType.VACCINATION,
                        vaccine.getVaccineName(),
                        1,
                        price,
                        saved.getId(),
                        1,
                        BigDecimal.ZERO,
                        normalizeNullable(saved.getBatchNumber()),
                        null
                ))
        ), actorAppUserId);
        return created;
    }

    private Optional<BillRecord> findReusableVaccinationBill(UUID tenantId, UUID patientId) {
        List<BillRecord> bills = billingService.listByPatient(tenantId, patientId);
        return bills.stream()
                .filter(bill -> !isClosedBillStatus(bill.status()))
                .filter(bill -> bill.dueAmount() != null && bill.dueAmount().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .or(() -> bills.stream().filter(bill -> !isClosedBillStatus(bill.status())).findFirst());
    }

    private boolean isClosedBillStatus(BillStatus status) {
        return status == BillStatus.CANCELLED
                || status == BillStatus.REFUND_PENDING
                || status == BillStatus.CANCELLED_REFUNDED
                || status == BillStatus.PAID
                || status == BillStatus.REFUNDED;
    }

    private InventoryLinkResult linkInventoryIfAvailable(UUID tenantId, PatientVaccinationEntity saved, PatientEntity patient, VaccineMasterEntity vaccine, PatientVaccinationCommand command, UUID actorAppUserId, List<String> warnings) {
        if (saved.getInventoryTransactionId() != null) {
            return new InventoryLinkResult(
                    saved.getInventoryTransactionId(),
                    saved.getInventoryStockBatchId(),
                    saved.getInventoryBatchNumberSnapshot(),
                    saved.getInventoryBatchManufacturerSnapshot(),
                    saved.getInventoryBatchExpiryDate()
            );
        }
        Optional<MedicineRecord> medicine = resolveVaccineMedicine(tenantId, vaccine);
        if (medicine.isEmpty()) {
            warnings.add("No matching inventory medicine was found for vaccine '" + vaccine.getVaccineName() + "'.");
            return null;
        }
        List<StockRecord> stocks = inventoryService.listStocks(tenantId).stream()
                .filter(stock -> medicine.get().id().equals(stock.medicineId()))
                .filter(stock -> stock.quantityOnHand() > 0)
                .sorted(Comparator
                        .comparing(StockRecord::expiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StockRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (stocks.isEmpty()) {
            warnings.add("No available stock was found for vaccine '" + vaccine.getVaccineName() + "'.");
            return new InventoryLinkResult(
                    null,
                    null,
                    normalizeNullable(command.batchNumber()),
                    medicine.get().manufacturer(),
                    null
            );
        }

        StockRecord selected = selectStock(stocks, command.batchNumber());
        if (selected == null) {
            warnings.add("No available batch could be selected for vaccine '" + vaccine.getVaccineName() + "'.");
            return new InventoryLinkResult(null, null, normalizeNullable(command.batchNumber()), medicine.get().manufacturer(), null);
        }

        if (selected.expiryDate() != null && selected.expiryDate().isBefore(LocalDate.now())) {
            warnings.add("Selected stock batch " + selected.batchNumber() + " is expired.");
            return new InventoryLinkResult(null, null, selected.batchNumber(), medicine.get().manufacturer(), selected.expiryDate());
        }

        if (selected.quantityOnHand() <= 0) {
            return new InventoryLinkResult(null, null, selected.batchNumber(), medicine.get().manufacturer(), selected.expiryDate());
        }

        InventoryTransactionRecord transaction = inventoryService.createTransaction(
                tenantId,
                new InventoryTransactionCommand(
                        selected.medicineId(),
                        selected.id(),
                        selected.locationId(),
                        null,
                        InventoryTransactionType.DISPENSED,
                        1,
                        "Vaccination recorded for " + patient.getPatientNumber(),
                        "PATIENT_VACCINATION",
                        saved.getId(),
                        actorAppUserId,
                        command.notes()
                ),
                actorAppUserId
        );
        return new InventoryLinkResult(
                transaction.id(),
                selected.id(),
                selected.batchNumber(),
                medicine.get().manufacturer(),
                selected.expiryDate()
        );
    }

    private ReminderLinkResult queueReminderIfNeeded(UUID tenantId, PatientVaccinationEntity saved, PatientEntity patient, UUID actorAppUserId) {
        if (saved.getReminderNotificationId() != null || saved.getNextDueDate() == null) {
            return saved.getReminderNotificationId() == null ? null : new ReminderLinkResult(saved.getReminderNotificationId(), saved.getReminderStatus(), saved.getReminderQueuedAt());
        }
        String channel = StringUtils.hasText(patient.getEmail()) ? "email" : "sms";
        String recipient = StringUtils.hasText(patient.getEmail()) ? patient.getEmail() : patient.getMobile();
        if (!StringUtils.hasText(recipient)) {
            return null;
        }
        String subject = "Vaccination follow-up due";
        String message = "Vaccination due for " + saved.getVaccineNameSnapshot() + " on " + saved.getNextDueDate();
        var result = notificationHistoryService.queueDetailed(
                tenantId,
                patient.getId(),
                "VACCINATION_REMINDER",
                channel,
                recipient,
                subject,
                message,
                "PATIENT_VACCINATION",
                saved.getId(),
                actorAppUserId
        );
        return new ReminderLinkResult(
                result.notification().id(),
                result.notification().status(),
                result.notification().createdAt()
        );
    }

    private Optional<MedicineRecord> resolveVaccineMedicine(UUID tenantId, VaccineMasterEntity vaccine) {
        String vaccineName = normalizeNullable(vaccine.getVaccineName());
        if (!StringUtils.hasText(vaccineName)) {
            return Optional.empty();
        }
        String normalizedVaccineName = vaccineName.toLowerCase(java.util.Locale.ROOT);
        return inventoryService.listMedicines(tenantId).stream()
                .filter(MedicineRecord::active)
                .filter(medicine -> medicineMatchesVaccine(medicine, normalizedVaccineName))
                .findFirst();
    }

    private boolean medicineMatchesVaccine(MedicineRecord medicine, String vaccineName) {
        return containsMatch(medicine.medicineName(), vaccineName)
                || containsMatch(medicine.genericName(), vaccineName)
                || containsMatch(medicine.brandName(), vaccineName)
                || containsMatch(medicine.manufacturer(), vaccineName);
    }

    private boolean containsMatch(String value, String needle) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(needle)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals(needle) || normalized.contains(needle) || needle.contains(normalized);
    }

    private StockRecord selectStock(List<StockRecord> stocks, String preferredBatchNumber) {
        if (StringUtils.hasText(preferredBatchNumber)) {
            return stocks.stream()
                    .filter(stock -> preferredBatchNumber.trim().equalsIgnoreCase(normalizeNullable(stock.batchNumber())))
                    .findFirst()
                    .orElse(stocks.getFirst());
        }
        return stocks.getFirst();
    }

    private VaccineMasterRecord toMasterRecord(VaccineMasterEntity entity) {
        return new VaccineMasterRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getVaccineName(),
                entity.getDescription(),
                entity.getManufacturer(),
                entity.getBrandName(),
                entity.getVaccineGroup(),
                entity.getDoseNumber(),
                entity.getRoute(),
                entity.getAdministrationSite(),
                entity.getStorageTemperature(),
                entity.getNdcBarcode(),
                entity.getScheduleType(),
                entity.getAgeGroup(),
                entity.getMinAgeDays(),
                entity.getRecommendedAgeDays(),
                entity.getMaxAgeDays(),
                entity.getRecommendedGapDays(),
                entity.getGapDays(),
                entity.getBoosterGapDays(),
                entity.getBoosterRules(),
                entity.isRecurring(),
                entity.getRecurrenceDays(),
                entity.getRecommendationPolicy(),
                entity.getCatchUpPolicy(),
                entity.getCatchUpMaxAgeDays(),
                entity.getApplicableAgeGroup(),
                entity.getClinicalIndications(),
                entity.getDefaultPrice(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PatientVaccinationRecord toRecord(PatientVaccinationEntity entity, VaccinationTenantData data, BillHistoryRecord billHistory) {
        return toRecord(entity, data, billHistory, List.of());
    }

    private PatientVaccinationRecord toRecord(PatientVaccinationEntity entity, VaccinationTenantData data, BillHistoryRecord billHistory, List<String> workflowWarnings) {
        PatientEntity patient = data.patients().get(entity.getPatientId());
        TenantUserRecord admin = data.users().get(entity.getAdministeredByUserId());
        TenantUserRecord createdBy = data.users().get(entity.getCreatedByUserId());
        TenantUserRecord updatedBy = data.users().get(entity.getUpdatedByUserId());
        TenantUserRecord verifiedBy = data.users().get(entity.getVerifiedByUserId());
        return new PatientVaccinationRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientId(),
                patient == null ? null : patient.getPatientNumber(),
                patient == null ? null : patient.getFirstName() + " " + patient.getLastName(),
                patient == null ? null : patient.getMobile(),
                patient == null ? null : patient.getAgeYears(),
                patient == null || patient.getGender() == null ? null : patient.getGender().name(),
                patient == null ? null : patient.getAllergies(),
                entity.getVaccineId(),
                entity.getVaccineNameSnapshot(),
                entity.getSource(),
                entity.getExternalPlace(),
                entity.getProofDocumentId(),
                entity.getVerifiedStatus(),
                entity.getVerifiedByUserId(),
                verifiedBy == null ? null : verifiedBy.displayName(),
                entity.getVerifiedAt(),
                entity.getDoseNumber(),
                entity.getGivenDate(),
                entity.getNextDueDate(),
                entity.getBatchNumber(),
                entity.getNotes(),
                entity.getAdministeredByUserId(),
                admin == null ? null : admin.displayName(),
                entity.getCreatedByUserId(),
                createdBy == null ? null : createdBy.displayName(),
                entity.getUpdatedByUserId(),
                updatedBy == null ? null : updatedBy.displayName(),
                entity.getUpdatedAt(),
                billHistory == null ? null : billHistory.billId(),
                billHistory == null ? null : billHistory.billNumber(),
                billHistory == null || billHistory.billStatus() == null ? null : billHistory.billStatus().name(),
                entity.getBillLineId(),
                entity.getInventoryTransactionId(),
                entity.getInventoryStockBatchId(),
                entity.getInventoryBatchNumberSnapshot(),
                entity.getInventoryBatchManufacturerSnapshot(),
                entity.getInventoryBatchExpiryDate(),
                entity.getReminderNotificationId(),
                entity.getReminderQueuedAt(),
                entity.getReminderStatus(),
                workflowWarnings == null ? List.of() : List.copyOf(workflowWarnings),
                entity.getCreatedByUserId(),
                createdBy == null ? null : createdBy.displayName(),
                entity.getCreatedAt()
        );
    }

    private List<PatientVaccinationRecord> mapRecords(UUID tenantId, List<PatientVaccinationEntity> entities, Map<UUID, BillHistoryRecord> billHistory) {
        if (entities.isEmpty()) {
            return List.of();
        }
        VaccinationTenantData data = tenantData(tenantId);
        return entities.stream().map(entity -> toRecord(entity, data, billHistory.get(entity.getId()))).toList();
    }

    private Map<UUID, BillHistoryRecord> billHistoryByVaccination(UUID tenantId) {
        return billingService.list(tenantId, new BillingSearchCriteria(null, null, null, null, null, null, null)).stream()
                .flatMap(bill -> bill.lines().stream()
                        .filter(line -> line.itemType() == BillItemType.VACCINATION && line.referenceId() != null)
                        .map(line -> new BillHistoryRecord(line.referenceId(), bill.id(), bill.billNumber(), bill.status())))
                .collect(Collectors.toMap(BillHistoryRecord::vaccinationId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private VaccinationTenantData tenantData(UUID tenantId) {
        List<PatientVaccinationEntity> vaccinations = patientVaccinationRepository.findByTenantIdOrderByGivenDateDesc(tenantId);
        Map<UUID, PatientEntity> patients = patientRepository.findByTenantIdAndIdIn(tenantId, vaccinations.stream().map(PatientVaccinationEntity::getPatientId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PatientEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, TenantUserRecord> users = tenantUserManagementService.list(tenantId).stream()
                .collect(Collectors.toMap(TenantUserRecord::appUserId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        String clinicName = clinic == null ? null : clinic.clinicName();
        String displayName = clinic == null ? null : clinic.displayName();
        String address = clinic == null ? null : formatAddress(clinic);
        String tenantName = StringUtils.hasText(displayName) ? displayName : (StringUtils.hasText(clinicName) ? clinicName : "Clinic");
        return new VaccinationTenantData(patients, users, tenantName, clinicName, displayName, address);
    }

    private void validateMaster(VaccineUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (!StringUtils.hasText(command.vaccineName())) {
            throw new IllegalArgumentException("vaccineName is required");
        }
        validateNonNegative(command.doseNumber(), "doseNumber");
        validateNonNegative(command.minAgeDays(), "minAgeDays");
        validateNonNegative(command.recommendedAgeDays(), "recommendedAgeDays");
        validateNonNegative(command.maxAgeDays(), "maxAgeDays");
        validateNonNegative(command.gapDays(), "gapDays");
        validateNonNegative(command.recommendedGapDays(), "recommendedGapDays");
        validateNonNegative(command.boosterGapDays(), "boosterGapDays");
        validateNonNegative(command.recurrenceDays(), "recurrenceDays");
        validateNonNegative(command.catchUpMaxAgeDays(), "catchUpMaxAgeDays");
        validateRoute(command.route());
        validateScheduleType(command.scheduleType());
        validateRecommendationPolicy(command.recommendationPolicy());
        validateCatchUpPolicy(command.catchUpPolicy());
        validateApplicableAgeGroup(command.applicableAgeGroup());
        if (command.defaultPrice() != null && command.defaultPrice().signum() < 0) {
            throw new IllegalArgumentException("defaultPrice must be 0 or greater");
        }
    }

    private void validateRecord(PatientVaccinationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        String source = normalizeSource(command.source());
        if (!"EXTERNAL".equals(source)) {
            requireId(command.vaccineId(), "vaccineId");
        }
        if ("EXTERNAL".equals(source) && !StringUtils.hasText(command.vaccineName()) && command.vaccineId() == null) {
            throw new IllegalArgumentException("vaccineName is required for external vaccination history");
        }
        normalizeVerifiedStatus(command.verifiedStatus(), source);
    }

    private void ensureUniqueName(UUID tenantId, String vaccineName, UUID currentId) {
        vaccineMasterRepository.findByTenantIdAndVaccineNameIgnoreCase(tenantId, vaccineName)
                .filter(entity -> currentId == null || !currentId.equals(entity.getId()))
                .ifPresent(entity -> {
                    throw new IllegalArgumentException("Vaccine name already exists for tenant");
                });
    }

    private void ensureUniqueMasterCombination(UUID tenantId, VaccineUpsertCommand command, UUID currentId) {
        String group = normalizeNullable(command.vaccineGroup());
        Integer doseNumber = command.doseNumber();
        String scheduleType = normalizeScheduleTypeValue(command.scheduleType());
        if (!StringUtils.hasText(group) && doseNumber == null && !StringUtils.hasText(scheduleType)) {
            return;
        }
        vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(tenantId).stream()
                .filter(entity -> currentId == null || !currentId.equals(entity.getId()))
                .filter(entity -> Objects.equals(normalizeNullable(entity.getVaccineGroup()), group))
                .filter(entity -> Objects.equals(entity.getDoseNumber(), doseNumber))
                .filter(entity -> Objects.equals(normalizeScheduleTypeValue(entity.getScheduleType()), scheduleType))
                .findFirst()
                .ifPresent(entity -> {
                    throw new IllegalArgumentException("Duplicate vaccine group/dose/schedule combination already exists for tenant");
                });
    }

    private void auditMaster(UUID tenantId, VaccineMasterEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                MASTER_ENTITY_TYPE,
                entity.getId(),
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                message,
                detailsJson(entity)
        ));
    }

    private void auditRecord(UUID tenantId, PatientVaccinationEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                RECORD_ENTITY_TYPE,
                entity.getId(),
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                message,
                detailsJson(entity)
        ));
    }

    private String detailsJson(VaccineMasterEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("tenantId", entity.getTenantId());
        details.put("vaccineName", entity.getVaccineName());
        details.put("active", entity.isActive());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private String detailsJson(PatientVaccinationEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("tenantId", entity.getTenantId());
        details.put("patientId", entity.getPatientId());
        details.put("vaccineId", entity.getVaccineId());
        details.put("nextDueDate", entity.getNextDueDate());
        details.put("createdByUserId", entity.getCreatedByUserId());
        details.put("updatedByUserId", entity.getUpdatedByUserId());
        details.put("billId", entity.getBillId());
        details.put("billLineId", entity.getBillLineId());
        details.put("inventoryTransactionId", entity.getInventoryTransactionId());
        details.put("inventoryStockBatchId", entity.getInventoryStockBatchId());
        details.put("reminderNotificationId", entity.getReminderNotificationId());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeSource(String value) {
        String normalized = normalizeNullable(value);
        if (!StringUtils.hasText(normalized)) {
            return "INTERNAL";
        }
        String upper = normalized.toUpperCase(java.util.Locale.ROOT);
        if (!"INTERNAL".equals(upper) && !"EXTERNAL".equals(upper)) {
            throw new IllegalArgumentException("source must be INTERNAL or EXTERNAL");
        }
        return upper;
    }

    private String normalizeVerifiedStatus(String value, String source) {
        String normalized = normalizeNullable(value);
        if (!StringUtils.hasText(normalized)) {
            return "EXTERNAL".equals(source) ? "UNVERIFIED" : "VERIFIED";
        }
        String upper = normalized.toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("UNVERIFIED", "VERIFIED", "REJECTED").contains(upper)) {
            throw new IllegalArgumentException("verifiedStatus must be UNVERIFIED, VERIFIED, or REJECTED");
        }
        return upper;
    }

    private String resolveVaccineNameSnapshot(PatientVaccinationCommand command, VaccineMasterEntity vaccine) {
        if (StringUtils.hasText(command.vaccineName())) {
            return command.vaccineName().trim();
        }
        if (vaccine != null && StringUtils.hasText(vaccine.getVaccineName())) {
            return vaccine.getVaccineName();
        }
        throw new IllegalArgumentException("vaccineName is required");
    }

    private void applyVaccineMetadata(VaccineMasterEntity entity, VaccineUpsertCommand command) {
        entity.update(
                normalize(command.vaccineName()),
                normalizeNullable(command.description()),
                normalizeNullable(command.manufacturer()),
                normalizeNullable(command.brandName()),
                normalizeNullable(command.vaccineGroup()),
                command.doseNumber(),
                normalizeRoute(command.route()),
                normalizeNullable(command.administrationSite()),
                normalizeNullable(command.storageTemperature()),
                normalizeNullable(command.ndcBarcode()),
                normalizeScheduleTypeValue(command.scheduleType()),
                normalizeNullable(command.ageGroup()),
                command.minAgeDays(),
                command.recommendedAgeDays(),
                command.maxAgeDays(),
                firstNonNull(command.gapDays(), command.recommendedGapDays(), command.recommendedAgeDays()),
                command.boosterGapDays(),
                normalizeNullable(command.boosterRules()),
                command.recurring(),
                command.recurrenceDays(),
                resolveRecommendationPolicy(command),
                resolveCatchUpPolicy(command),
                command.catchUpMaxAgeDays(),
                resolveApplicableAgeGroup(command),
                normalizeNullable(command.clinicalIndications()),
                normalizeMoney(command.defaultPrice()),
                command.active()
        );
    }

    private void validateNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be 0 or greater");
        }
    }

    private void validateRoute(String route) {
        if (normalizeNullable(route) == null) {
            return;
        }
        normalizeRoute(route);
    }

    private String normalizeRoute(String route) {
        String normalized = normalizeNullable(route);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("IM", "SC", "ORAL", "NASAL", "ID").contains(upper)) {
            throw new IllegalArgumentException("route must be one of IM, SC, ORAL, NASAL, ID");
        }
        return upper;
    }

    private void validateScheduleType(String scheduleType) {
        if (normalizeNullable(scheduleType) == null) {
            return;
        }
        normalizeScheduleTypeValue(scheduleType);
    }

    private String normalizeScheduleTypeValue(String scheduleType) {
        String normalized = normalizeNullable(scheduleType);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("UIP", "IAP", "CLINIC_CUSTOM", "TRAVEL", "ADULT").contains(upper)) {
            throw new IllegalArgumentException("scheduleType must be one of UIP, IAP, CLINIC_CUSTOM, TRAVEL, ADULT");
        }
        return upper;
    }

    private void validateRecommendationPolicy(String policy) {
        if (normalizeNullable(policy) == null) {
            return;
        }
        normalizeRecommendationPolicyValue(policy);
    }

    private void validateCatchUpPolicy(String policy) {
        if (normalizeNullable(policy) == null) {
            return;
        }
        normalizeCatchUpPolicyValue(policy);
    }

    private void validateApplicableAgeGroup(String ageGroup) {
        if (normalizeNullable(ageGroup) == null) {
            return;
        }
        normalizeApplicableAgeGroupValue(ageGroup);
    }

    private String resolveRecommendationPolicy(VaccineUpsertCommand command) {
        String explicit = normalizeRecommendationPolicyValue(command.recommendationPolicy());
        if (explicit != null) {
            return explicit;
        }
        String scheduleType = normalizeScheduleTypeValue(command.scheduleType());
        if ("ADULT".equals(scheduleType)) {
            return "ADULT_ROUTINE";
        }
        if (command.recurring()) {
            return "RECURRING";
        }
        String inferredAgeGroup = normalizeNullable(command.ageGroup());
        if (looksChildhoodSchedule(inferredAgeGroup, command.recommendedAgeDays())) {
            return "STANDARD_CHILDHOOD";
        }
        return "CLINIC_CUSTOM";
    }

    private String resolveCatchUpPolicy(VaccineUpsertCommand command) {
        String explicit = normalizeCatchUpPolicyValue(command.catchUpPolicy());
        return explicit == null ? "NONE" : explicit;
    }

    private String resolveApplicableAgeGroup(VaccineUpsertCommand command) {
        String explicit = normalizeApplicableAgeGroupValue(command.applicableAgeGroup());
        if (explicit != null) {
            return explicit;
        }
        String ageGroup = normalizeNullable(command.ageGroup());
        if (!StringUtils.hasText(ageGroup)) {
            return null;
        }
        String token = ageGroup.toUpperCase(java.util.Locale.ROOT);
        if (token.contains("NEWBORN") || token.contains("BIRTH")) {
            return "NEWBORN";
        }
        if (token.contains("INFANT") || token.contains("6 WEEK") || token.contains("10 WEEK") || token.contains("14 WEEK") || token.contains("9 MONTH")) {
            return "INFANT";
        }
        if (token.contains("TODDLER") || token.contains("1 YEAR") || token.contains("2 YEAR") || token.contains("3 YEAR")) {
            return "TODDLER";
        }
        if (token.contains("CHILD") || token.contains("4 YEAR") || token.contains("5 YEAR") || token.contains("6 YEAR") || token.contains("7 YEAR") || token.contains("8 YEAR") || token.contains("9 YEAR")) {
            return "CHILD";
        }
        if (token.contains("ADOLESCENT") || token.contains("10 YEAR") || token.contains("11 YEAR") || token.contains("12 YEAR") || token.contains("13 YEAR") || token.contains("14 YEAR") || token.contains("15 YEAR") || token.contains("16 YEAR") || token.contains("17 YEAR")) {
            return "ADOLESCENT";
        }
        if (token.contains("OLDER") || token.contains("SENIOR") || token.contains("60")) {
            return "OLDER_ADULT";
        }
        if (token.contains("ADULT")) {
            return "ADULT";
        }
        return "ALL";
    }

    private boolean looksChildhoodSchedule(String ageGroup, Integer recommendedAgeDays) {
        if (recommendedAgeDays != null && recommendedAgeDays <= 365 * 3) {
            return true;
        }
        if (!StringUtils.hasText(ageGroup)) {
            return false;
        }
        String normalized = ageGroup.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("NEWBORN")
                || normalized.contains("INFANT")
                || normalized.contains("TODDLER")
                || normalized.contains("CHILD")
                || normalized.contains("ADOLESCENT")
                || normalized.contains("WEEK")
                || normalized.contains("MONTH");
    }

    private String normalizeRecommendationPolicyValue(String policy) {
        String normalized = normalizeNullable(policy);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_RECOMMENDATION_POLICIES.contains(upper)) {
            throw new IllegalArgumentException("recommendationPolicy must be one of " + String.join(", ", ALLOWED_RECOMMENDATION_POLICIES));
        }
        return upper;
    }

    private String normalizeCatchUpPolicyValue(String policy) {
        String normalized = normalizeNullable(policy);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_CATCH_UP_POLICIES.contains(upper)) {
            throw new IllegalArgumentException("catchUpPolicy must be one of " + String.join(", ", ALLOWED_CATCH_UP_POLICIES));
        }
        return upper;
    }

    private String normalizeApplicableAgeGroupValue(String ageGroup) {
        String normalized = normalizeNullable(ageGroup);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_APPLICABLE_AGE_GROUPS.contains(upper)) {
            throw new IllegalArgumentException("applicableAgeGroup must be one of " + String.join(", ", ALLOWED_APPLICABLE_AGE_GROUPS));
        }
        return upper;
    }

    private Integer firstNonNull(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String formatAddress(ClinicProfileRecord clinic) {
        if (clinic == null) {
            return null;
        }
        List<String> parts = java.util.stream.Stream.of(
                clinic.addressLine1(),
                clinic.addressLine2(),
                clinic.city(),
                clinic.state(),
                clinic.country(),
                clinic.postalCode()
        ).filter(StringUtils::hasText).toList();
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireId(UUID id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private record VaccinationTenantData(
            Map<UUID, PatientEntity> patients,
            Map<UUID, TenantUserRecord> users,
            String tenantName,
            String clinicName,
            String clinicDisplayName,
            String clinicAddress
    ) {
    }

    private record BillHistoryRecord(UUID vaccinationId, UUID billId, String billNumber, BillStatus billStatus) {
    }

    private record BillLinkResult(UUID billId, UUID billLineId, String billNumber, String billStatus) {
    }

    private record InventoryLinkResult(UUID inventoryTransactionId, UUID stockBatchId, String batchNumber, String manufacturer, LocalDate expiryDate) {
    }

    private record ReminderLinkResult(UUID notificationId, String status, OffsetDateTime queuedAt) {
    }
}
