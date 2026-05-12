package com.deepthoughtnet.clinic.api.platform.service;

import com.deepthoughtnet.clinic.clinic.db.ClinicProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileUpsertCommand;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantPlanRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.db.TenantSubscriptionEntity;
import com.deepthoughtnet.clinic.identity.db.TenantSubscriptionRepository;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.CreateTenantUserCommand;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesCommand;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.identity.service.provisioning.TenantProvisioningRequest;
import com.deepthoughtnet.clinic.identity.service.provisioning.TenantProvisioningResult;
import com.deepthoughtnet.clinic.identity.service.provisioning.TenantProvisioningService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PlatformTenantService {
    private static final Logger log = LoggerFactory.getLogger(PlatformTenantService.class);

    private final PlatformTenantManagementService platformTenantManagementService;
    private final TenantProvisioningService tenantProvisioningService;
    private final TenantUserManagementService tenantUserManagementService;
    private final TenantRepository tenantRepository;
    private final TenantPlanRepository tenantPlanRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantModuleService tenantModuleService;
    private final ClinicProfileService clinicProfileService;
    private final ClinicProfileRepository clinicProfileRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public PlatformTenantService(
            PlatformTenantManagementService platformTenantManagementService,
            TenantProvisioningService tenantProvisioningService,
            TenantUserManagementService tenantUserManagementService,
            TenantRepository tenantRepository,
            TenantPlanRepository tenantPlanRepository,
            TenantSubscriptionRepository tenantSubscriptionRepository,
            TenantModuleService tenantModuleService,
            ClinicProfileService clinicProfileService,
            ClinicProfileRepository clinicProfileRepository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.platformTenantManagementService = platformTenantManagementService;
        this.tenantProvisioningService = tenantProvisioningService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.tenantRepository = tenantRepository;
        this.tenantPlanRepository = tenantPlanRepository;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.tenantModuleService = tenantModuleService;
        this.clinicProfileService = clinicProfileService;
        this.clinicProfileRepository = clinicProfileRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<PlatformTenantRecord> listTenants() {
        return platformTenantManagementService.list();
    }

    @Transactional(readOnly = true)
    public PlatformTenantDetail getTenant(UUID tenantId) {
        PlatformTenantRecord tenant = platformTenantManagementService.get(tenantId);
        ClinicProfileRecord profile = clinicProfileService.findByTenantId(tenantId).orElse(null);
        TenantSubscriptionEntity subscription = tenantSubscriptionRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId).orElse(null);
        Map<String, Boolean> modules = tenantModuleService.findForTenant(tenantId);
        List<TenantUserRecord> users = tenantUserManagementService.list(tenantId);
        long adminCount = users.stream()
                .filter(u -> u.membershipRole() != null && u.membershipRole().contains("ADMIN"))
                .count();

        return new PlatformTenantDetail(tenant, profile, subscription, modules, users.size(), adminCount);
    }

    @Transactional
    public PlatformTenantDetail createTenant(CreateTenantCommand command) {
        validateCreate(command);

        String planId = normalizePlanId(command.planId());
        String displayName = StringUtils.hasText(command.displayName()) ? command.displayName().trim() : command.clinicName().trim();
        String adminDisplayName = StringUtils.hasText(command.adminFirstName()) || StringUtils.hasText(command.adminLastName())
                ? (nullToEmpty(command.adminFirstName()) + " " + nullToEmpty(command.adminLastName())).trim()
                : null;

        var provisioningRequest = new TenantProvisioningRequest(
                command.tenantCode().trim().toLowerCase(Locale.ROOT),
                command.clinicName().trim(),
                planId,
                normalizeNullable(command.adminEmail()),
                adminDisplayName,
                normalizeNullable(command.tempPassword())
        );

        final TenantProvisioningResult provisioned;
        try {
            provisioned = tenantProvisioningService.provisionTenant(provisioningRequest);
        } catch (RuntimeException ex) {
            if (StringUtils.hasText(command.adminEmail())) {
                throw new IllegalStateException("Keycloak provisioning failed: role/client/realm missing or admin access denied", ex);
            }
            throw ex;
        }

        UUID tenantId = provisioned.tenantId();
        log.info("tenant.create provisioned tenantId={} tenantCode={} planId={}", tenantId, provisioned.tenantCode(), planId);

        if (tenantSubscriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).isEmpty()) {
            log.info("tenant.create inserting subscription tenantId={} planId={} trial=true", tenantId, planId);
            insertSubscription(tenantId, planId, true);
        }

        clinicProfileService.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        command.clinicName().trim(),
                        displayName,
                        defaultIfBlank(command.phone(), "9999999999"),
                        normalizeNullable(command.clinicEmail()),
                        defaultIfBlank(command.addressLine1(), command.city().trim() + " Clinic"),
                        normalizeNullable(command.addressLine2()),
                        command.city().trim(),
                        defaultIfBlank(command.state(), "Maharashtra"),
                        command.country().trim(),
                        defaultIfBlank(command.postalCode(), "411001"),
                        null,
                        null,
                        null,
                        true
                ),
                currentActorAppUserId()
        );

        if (command.modules() != null && !command.modules().isEmpty()) {
            Map<String, Boolean> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Boolean> entry : command.modules().entrySet()) {
                normalized.put(tenantModuleService.normalizeModule(entry.getKey()), Boolean.TRUE.equals(entry.getValue()));
            }
            tenantModuleService.upsertForTenant(tenantId, normalized);
        }

        audit(tenantId, tenantId, "platform.tenant.create", Map.of(
                "tenantCode", command.tenantCode(),
                "planId", planId,
                "hasAdminEmail", StringUtils.hasText(command.adminEmail())
        ));

        return getTenant(tenantId);
    }

    @Transactional
    public PlatformTenantRecord updateStatus(UUID tenantId, boolean active) {
        PlatformTenantRecord updated = active
                ? platformTenantManagementService.activate(tenantId)
                : platformTenantManagementService.suspend(tenantId);

        audit(tenantId, tenantId, "platform.tenant.status", Map.of("active", active));
        return updated;
    }

    @Transactional
    public PlatformTenantRecord updatePlan(UUID tenantId, String planId) {
        String normalizedPlanId = normalizePlanId(planId);
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        tenant.updatePlanId(normalizedPlanId);
        insertSubscription(tenantId, normalizedPlanId, false);

        audit(tenantId, tenantId, "platform.tenant.plan.update", Map.of("planId", normalizedPlanId));
        return platformTenantManagementService.get(tenantId);
    }

    @Transactional
    public PlatformTenantRecord updateModules(UUID tenantId, Map<String, Boolean> modules) {
        if (modules == null || modules.isEmpty()) {
            throw new IllegalArgumentException("modules are required");
        }

        boolean appointments = moduleEnabled(modules, "APPOINTMENTS");
        boolean consultation = moduleEnabled(modules, "CONSULTATION");
        boolean prescription = moduleEnabled(modules, "PRESCRIPTION");
        boolean billing = moduleEnabled(modules, "BILLING");
        boolean vaccination = moduleEnabled(modules, "VACCINATION");
        boolean inventory = moduleEnabled(modules, "INVENTORY");
        boolean aiCopilot = moduleEnabled(modules, "AI_COPILOT");
        boolean carePilot = moduleEnabled(modules, "CAREPILOT");

        PlatformTenantRecord record = platformTenantManagementService.configureModules(
                tenantId,
                new TenantModulesCommand(
                        appointments || consultation || prescription || billing || vaccination || inventory,
                        false,
                        false,
                        appointments,
                        aiCopilot,
                        false,
                        false,
                        false,
                        false,
                        carePilot
                )
        );

        Map<String, Boolean> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : modules.entrySet()) {
            normalized.put(tenantModuleService.normalizeModule(entry.getKey()), Boolean.TRUE.equals(entry.getValue()));
        }
        tenantModuleService.upsertForTenant(tenantId, normalized);

        audit(tenantId, tenantId, "platform.tenant.modules.update", normalized);
        return record;
    }

    @Transactional
    public TenantUserRecord createAdminUser(UUID tenantId, CreateAdminUserCommand command) {
        if (!StringUtils.hasText(command.email())) {
            throw new IllegalArgumentException("admin email is required");
        }
        String displayName = (nullToEmpty(command.firstName()) + " " + nullToEmpty(command.lastName())).trim();
        if (!StringUtils.hasText(displayName)) {
            displayName = command.email().trim();
        }

        TenantUserRecord created = tenantUserManagementService.createOrInvite(
                new CreateTenantUserCommand(
                        tenantId,
                        command.email().trim().toLowerCase(Locale.ROOT),
                        command.email().trim().toLowerCase(Locale.ROOT),
                        displayName,
                        command.tempPassword(),
                        "CLINIC_ADMIN"
                )
        );

        audit(tenantId, created.appUserId(), "platform.tenant.admin-user.create", Map.of("email", created.email()));
        return created;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return tenantPlanRepository.findAll().stream()
                .map(plan -> new PlanResponse(plan.getId(), plan.getName(), serializePlanFeatures(plan.getFeatures())))
                .toList();
    }

    private void validateCreate(CreateTenantCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!StringUtils.hasText(command.clinicName())) {
            throw new IllegalArgumentException("clinicName is required");
        }
        if (!StringUtils.hasText(command.tenantCode())) {
            throw new IllegalArgumentException("tenantCode is required");
        }
        if (!StringUtils.hasText(command.city())) {
            throw new IllegalArgumentException("city is required");
        }
        if (!StringUtils.hasText(command.country())) {
            throw new IllegalArgumentException("country is required");
        }
        if (!StringUtils.hasText(command.adminEmail())) {
            throw new IllegalArgumentException("Admin email is required to provision clinic admin.");
        }

        String code = command.tenantCode().trim().toLowerCase(Locale.ROOT);
        if (tenantRepository.existsByCode(code)) {
            throw new IllegalArgumentException("tenantCode already exists: " + code);
        }

        if (StringUtils.hasText(command.clinicEmail()) && clinicProfileRepository.existsByEmailIgnoreCase(command.clinicEmail().trim())) {
            throw new IllegalArgumentException("clinic email already exists: " + command.clinicEmail().trim());
        }
    }

    private String normalizePlanId(String planId) {
        String normalized = StringUtils.hasText(planId) ? planId.trim().toUpperCase(Locale.ROOT) : "FREE";
        tenantPlanRepository.findById(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Unknown planId: " + normalized));
        return normalized;
    }

    private String serializePlanFeatures(Map<String, Object> features) {
        try {
            return objectMapper.writeValueAsString(features == null ? Map.of() : features);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tenant plan features", e);
        }
    }

    private void insertSubscription(UUID tenantId, String planId, boolean trial) {
        jdbcTemplate.update(
                """
                insert into tenant_subscriptions (id, tenant_id, plan_id, start_date, end_date, status, trial, created_at)
                values (?, ?, ?, ?, ?, ?, ?, now())
                """,
                UUID.randomUUID(),
                tenantId,
                planId,
                LocalDate.now(),
                null,
                trial ? "TRIAL" : "ACTIVE",
                trial
        );
    }

    private boolean moduleEnabled(Map<String, Boolean> modules, String moduleCode) {
        for (Map.Entry<String, Boolean> entry : modules.entrySet()) {
            if (moduleCode.equals(tenantModuleService.normalizeModule(entry.getKey()))) {
                return Boolean.TRUE.equals(entry.getValue());
            }
        }
        return false;
    }

    private void audit(UUID tenantId, UUID entityId, String action, Object details) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                "PLATFORM_TENANT",
                entityId,
                action,
                currentActorAppUserId(),
                OffsetDateTime.now(),
                action,
                toJson(details)
        ));
    }

    private UUID currentActorAppUserId() {
        var ctx = RequestContextHolder.get();
        return ctx == null ? null : ctx.appUserId();
    }

    private String toJson(Object details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    public record CreateTenantCommand(
            String clinicName,
            String tenantCode,
            String displayName,
            String city,
            String state,
            String country,
            String postalCode,
            String phone,
            String clinicEmail,
            String addressLine1,
            String addressLine2,
            String planId,
            Map<String, Boolean> modules,
            String adminEmail,
            String adminFirstName,
            String adminLastName,
            String tempPassword
    ) {}

    public record CreateAdminUserCommand(String email, String firstName, String lastName, String tempPassword) {}

    public record PlatformTenantDetail(
            PlatformTenantRecord tenant,
            ClinicProfileRecord clinicProfile,
            TenantSubscriptionEntity latestSubscription,
            Map<String, Boolean> modules,
            int userCount,
            long adminCount
    ) {}

    public record PlanResponse(String id, String name, String features) {}
}
