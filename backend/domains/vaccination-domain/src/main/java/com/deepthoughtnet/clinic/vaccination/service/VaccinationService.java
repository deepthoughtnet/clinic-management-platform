package com.deepthoughtnet.clinic.vaccination.service;

import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final VaccineMasterRepository vaccineMasterRepository;
    private final PatientVaccinationRepository patientVaccinationRepository;
    private final PatientRepository patientRepository;
    private final ClinicProfileService clinicProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final BillingService billingService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public VaccinationService(
            VaccineMasterRepository vaccineMasterRepository,
            PatientVaccinationRepository patientVaccinationRepository,
            PatientRepository patientRepository,
            ClinicProfileService clinicProfileService,
            TenantUserManagementService tenantUserManagementService,
            BillingService billingService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.vaccineMasterRepository = vaccineMasterRepository;
        this.patientVaccinationRepository = patientVaccinationRepository;
        this.patientRepository = patientRepository;
        this.clinicProfileService = clinicProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.billingService = billingService;
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
        VaccineMasterEntity entity = VaccineMasterEntity.create(tenantId, normalize(command.vaccineName()));
        entity.update(
                normalize(command.vaccineName()),
                normalizeNullable(command.description()),
                normalizeNullable(command.ageGroup()),
                command.recommendedGapDays(),
                normalizeMoney(command.defaultPrice()),
                command.active()
        );
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
        entity.update(
                normalize(command.vaccineName()),
                normalizeNullable(command.description()),
                normalizeNullable(command.ageGroup()),
                command.recommendedGapDays(),
                normalizeMoney(command.defaultPrice()),
                command.active()
        );
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
        entity.update(entity.getVaccineName(), entity.getDescription(), entity.getAgeGroup(), entity.getRecommendedGapDays(), entity.getDefaultPrice(), false);
        VaccineMasterEntity saved = vaccineMasterRepository.save(entity);
        auditMaster(tenantId, saved, "vaccine.deactivated", actorAppUserId, "Deactivated vaccine master");
        return toMasterRecord(saved);
    }

    @Transactional(readOnly = true)
    public List<PatientVaccinationRecord> listByPatient(UUID tenantId, UUID patientId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        return mapRecords(tenantId, patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(tenantId, patientId));
    }

    @Transactional
    public PatientVaccinationRecord recordVaccination(UUID tenantId, UUID patientId, PatientVaccinationCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        validateRecord(command);
        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        VaccineMasterEntity vaccine = vaccineMasterRepository.findByTenantIdAndId(tenantId, command.vaccineId())
                .orElseThrow(() -> new IllegalArgumentException("Vaccine not found"));
        if (!vaccine.isActive()) {
            throw new IllegalArgumentException("Vaccine is inactive");
        }

        UUID administeredBy = command.administeredByUserId() == null ? actorAppUserId : command.administeredByUserId();
        LocalDate givenDate = command.givenDate() == null ? LocalDate.now() : command.givenDate();
        LocalDate nextDueDate = command.nextDueDate();
        if (nextDueDate == null && vaccine.getRecommendedGapDays() != null) {
            nextDueDate = givenDate.plusDays(vaccine.getRecommendedGapDays());
        }

        PatientVaccinationEntity entity = PatientVaccinationEntity.create(
                tenantId,
                patientId,
                vaccine.getId(),
                vaccine.getVaccineName(),
                command.doseNumber(),
                givenDate,
                nextDueDate,
                normalizeNullable(command.batchNumber()),
                normalizeNullable(command.notes()),
                administeredBy
        );
        PatientVaccinationEntity saved = patientVaccinationRepository.save(entity);

        if (command.addToBill() && command.billId() != null) {
            BigDecimal price = command.billItemUnitPrice() != null
                    ? normalizeMoney(command.billItemUnitPrice())
                    : normalizeMoney(vaccine.getDefaultPrice() == null ? BigDecimal.ZERO : vaccine.getDefaultPrice());
            billingService.addLineItem(
                    tenantId,
                    command.billId(),
                    new BillLineCommand(
                            BillItemType.VACCINATION,
                            vaccine.getVaccineName(),
                            1,
                            price,
                            saved.getId(),
                            null
                    ),
                    actorAppUserId
            );
        }

        auditRecord(tenantId, saved, "vaccination.recorded", actorAppUserId, "Recorded patient vaccination");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional(readOnly = true)
    public List<PatientVaccinationRecord> listDue(UUID tenantId) {
        requireTenant(tenantId);
        LocalDate today = LocalDate.now();
        return mapRecords(tenantId, patientVaccinationRepository.findByTenantIdOrderByGivenDateDesc(tenantId)
                .stream()
                .filter(record -> record.getNextDueDate() != null && !record.getNextDueDate().isBefore(today))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<PatientVaccinationRecord> listOverdue(UUID tenantId) {
        requireTenant(tenantId);
        LocalDate today = LocalDate.now();
        return mapRecords(tenantId, patientVaccinationRepository.findByTenantIdOrderByGivenDateDesc(tenantId)
                .stream()
                .filter(record -> record.getNextDueDate() != null && record.getNextDueDate().isBefore(today))
                .toList());
    }

    private VaccineMasterRecord toMasterRecord(VaccineMasterEntity entity) {
        return new VaccineMasterRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getVaccineName(),
                entity.getDescription(),
                entity.getAgeGroup(),
                entity.getRecommendedGapDays(),
                entity.getDefaultPrice(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PatientVaccinationRecord toRecord(PatientVaccinationEntity entity, VaccinationTenantData data) {
        PatientEntity patient = data.patients().get(entity.getPatientId());
        TenantUserRecord admin = data.users().get(entity.getAdministeredByUserId());
        return new PatientVaccinationRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientId(),
                patient == null ? null : patient.getPatientNumber(),
                patient == null ? null : patient.getFirstName() + " " + patient.getLastName(),
                entity.getVaccineId(),
                entity.getVaccineNameSnapshot(),
                entity.getDoseNumber(),
                entity.getGivenDate(),
                entity.getNextDueDate(),
                entity.getBatchNumber(),
                entity.getNotes(),
                entity.getAdministeredByUserId(),
                admin == null ? null : admin.displayName(),
                entity.getCreatedAt()
        );
    }

    private List<PatientVaccinationRecord> mapRecords(UUID tenantId, List<PatientVaccinationEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        return entities.stream().map(entity -> toRecord(entity, tenantData(tenantId))).toList();
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
    }

    private void validateRecord(PatientVaccinationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireId(command.vaccineId(), "vaccineId");
    }

    private void ensureUniqueName(UUID tenantId, String vaccineName, UUID currentId) {
        vaccineMasterRepository.findByTenantIdAndVaccineNameIgnoreCase(tenantId, vaccineName)
                .filter(entity -> currentId == null || !currentId.equals(entity.getId()))
                .ifPresent(entity -> {
                    throw new IllegalArgumentException("Vaccine name already exists for tenant");
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
}
