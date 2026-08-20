package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import com.deepthoughtnet.clinic.identity.service.TenantIdentityConflictException;
import com.deepthoughtnet.clinic.identity.service.keycloak.KeycloakAdminProvisioner;
import com.deepthoughtnet.clinic.identity.service.model.CreateTenantUserCommand;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.identity.service.model.UpdateTenantUserProfileCommand;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.regex.Pattern;

@Service
public class TenantUserManagementService {
    private static final Logger log = LoggerFactory.getLogger(TenantUserManagementService.class);
    private static final String INDIAN_MOBILE_PATTERN = "^[0-9]{10}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[^\\s\\x00-\\x1F\\x7F]{3,128}$");
    private static final Pattern EMPLOYEE_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$");
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final int MAX_FIRST_NAME_LENGTH = 128;
    private static final int MAX_DISPLAY_NAME_LENGTH = 256;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_LOGIN_ID_LENGTH = 128;
    private static final int MAX_EMPLOYEE_CODE_LENGTH = 64;
    private static final int MAX_DEPARTMENT_LENGTH = 128;
    private static final int MAX_ROLE_LENGTH = 64;

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "TENANT_ADMIN",
            "ADMIN",
            "CLINIC_ADMIN",
            "ENGAGE_MANAGER",
            "ENGAGE_EXECUTIVE",
            "DOCTOR",
            "RECEPTIONIST",
            "BILLING_USER",
            "PHARMA",
            "PHARMACY",
            "PHARMACIST",
            "PHARMACY_INVENTORY_MANAGER",
            "PHARMACY_POS_USER",
            "LAB_TECHNICIAN",
            "LAB_ASSISTANT",
            "LAB_APPROVER",
            "LAB_FRONT_DESK",
            "PLATFORM_ADMIN",
            "CLINIC_REVIEWER",
            "CLINIC_APPROVER",
            "CLINIC_AUDITOR",
            "CLINIC_GENERATION_CREATOR",
            "CLINIC_GENERATION_APPROVER",
            "CLINIC_GENERATION_MANAGER",
            "CLINIC_GENERATION_VIEWER",
            "RECONCILIATION_OPERATOR",
            "RECONCILIATION_REVIEWER",
            "RECONCILIATION_MANAGER",
            "RECONCILIATION_VIEWER",
            "AGENT_OPERATOR",
            "DECISIONING_MANAGER",
            "DECISIONING_VIEWER",
            "AUDITOR",
            "CLINIC_VIEWER",
            "VIEWER",
            "SERVICE_AGENT"
    );

    private final AppUserRepository appUserRepository;
    private final TenantMembershipRepository membershipRepository;
    private final KeycloakAdminProvisioner keycloakAdminProvisioner;

    public TenantUserManagementService(
            AppUserRepository appUserRepository,
            TenantMembershipRepository membershipRepository,
            KeycloakAdminProvisioner keycloakAdminProvisioner
    ) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.keycloakAdminProvisioner = keycloakAdminProvisioner;
    }

    @Transactional(readOnly = true)
    public List<TenantUserRecord> list(UUID tenantId) {
        requireTenant(tenantId);

        List<TenantMembershipEntity> memberships = membershipRepository.findByTenantId(tenantId);
        List<UUID> appUserIds = memberships.stream()
                .map(TenantMembershipEntity::getAppUserId)
                .distinct()
                .toList();

        if (appUserIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, AppUserEntity> users = appUserRepository.findByTenantIdAndIdIn(tenantId, appUserIds)
                .stream()
                .collect(Collectors.toMap(AppUserEntity::getId, Function.identity(), (a, b) -> a));

        return memberships.stream()
                .map(membership -> {
                    AppUserEntity user = users.get(membership.getAppUserId());
                    return user == null ? null : toRecord(user, membership, "EXISTING");
                })
                .filter(record -> record != null)
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    @Transactional
    public TenantUserRecord createOrInvite(CreateTenantUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireTenant(command.tenantId());

        String role = normalizeRole(command.role());
        String firstName = normalizeRequiredText(command.firstName(), "First name", MAX_FIRST_NAME_LENGTH);
        String lastName = normalizeOptionalText(command.lastName(), "Last name", MAX_FIRST_NAME_LENGTH);
        String displayName = normalizeRequiredText(command.displayName(), "Name", MAX_DISPLAY_NAME_LENGTH);
        String email = normalizeRequiredEmail(command.email());
        String username = normalizeOptionalLoginId(command.username());
        String employeeCode = normalizeOptionalEmployeeCode(command.employeeCode());
        String mobile = normalizeMobile(command.mobile());
        String department = normalizeRequiredText(command.department(), "Department", MAX_DEPARTMENT_LENGTH);
        String correlationId = correlationId();
        UUID tenantId = command.tenantId();
        validateDepartmentCompatibility(role, department);
        validateSupplementalUniqueness(tenantId, null, username, employeeCode);

        try {
            log.info(
                    "tenant.user.provision stage=keycloak tenantId={} email={} username={} role={} correlationId={}",
                    tenantId,
                    mask(email),
                    mask(username),
                    role,
                    correlationId
            );
            String keycloakSub = keycloakAdminProvisioner.createOrGetTenantUserId(
                    tenantId,
                    email,
                    username,
                    firstName,
                    lastName,
                    displayName,
                    command.tempPassword(),
                    StringUtils.hasText(email)
            );
            keycloakAdminProvisioner.ensureRealmRole(keycloakSub, role);
            log.info(
                    "tenant.user.provision stage=keycloak_complete tenantId={} email={} username={} role={} keycloakSub={} correlationId={}",
                    tenantId,
                    mask(email),
                    mask(username),
                    role,
                    keycloakSub,
                    correlationId
            );

            AppUserEntity user = upsertTenantUser(tenantId, keycloakSub, email, username, displayName, employeeCode, mobile, department);
            log.info(
                    "tenant.user.provision stage=app_user_persisted tenantId={} appUserId={} email={} username={} correlationId={}",
                    tenantId,
                    user.getId(),
                    mask(email),
                    mask(username),
                    correlationId
            );

            TenantMembershipEntity membership = membershipRepository.findByTenantIdAndAppUserId(
                            tenantId,
                            user.getId()
                    )
                    .orElseGet(() -> membershipRepository.save(TenantMembershipEntity.create(
                            tenantId,
                            user.getId(),
                            role
                    )));

            membership.setRole(role);
            membership.setStatus("ACTIVE");

            log.info(
                    "tenant.user.provision stage=membership_persisted tenantId={} appUserId={} role={} correlationId={}",
                    tenantId,
                    user.getId(),
                    role,
                    correlationId
            );

            return toRecord(user, membership, "KEYCLOAK_USER_READY");
        } catch (TenantIdentityConflictException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                    "tenant.user.provision stage=failed tenantId={} email={} username={} role={} correlationId={}",
                    tenantId,
                    mask(email),
                    mask(username),
                    role,
                    correlationId,
                    ex
            );
            throw new TenantProvisioningException("tenant.user.provision", "User provisioning failed at stage '" + failedStage(ex) + "'", ex);
        }
    }

    @Transactional
    public TenantUserRecord updateRole(UUID tenantId, UUID appUserId, String role) {
        requireTenant(tenantId);
        if (appUserId == null) {
            throw new IllegalArgumentException("appUserId is required");
        }

        String normalizedRole = normalizeRole(role);
        AppUserEntity user = findTenantUser(tenantId, appUserId);
        TenantMembershipEntity membership = findMembership(tenantId, appUserId);
        membership.setRole(normalizedRole);
        return toRecord(user, membership, "ROLE_UPDATED");
    }

    @Transactional
    public TenantUserRecord updateStatus(UUID tenantId, UUID appUserId, boolean active) {
        requireTenant(tenantId);
        if (appUserId == null) {
            throw new IllegalArgumentException("appUserId is required");
        }

        AppUserEntity user = findTenantUser(tenantId, appUserId);
        TenantMembershipEntity membership = findMembership(tenantId, appUserId);
        membership.setStatus(active ? "ACTIVE" : "DISABLED");
        return toRecord(user, membership, active ? "MEMBERSHIP_REACTIVATED" : "MEMBERSHIP_DISABLED");
    }

    @Transactional
    public TenantUserRecord resetPassword(UUID tenantId, UUID appUserId, String tempPassword, boolean temporary) {
        requireTenant(tenantId);
        if (appUserId == null) {
            throw new IllegalArgumentException("appUserId is required");
        }
        if (!StringUtils.hasText(tempPassword)) {
            throw new IllegalArgumentException("tempPassword is required");
        }

        AppUserEntity user = findTenantUser(tenantId, appUserId);
        if (!StringUtils.hasText(user.getKeycloakSub())) {
            throw new IllegalArgumentException("Cannot reset password: keycloakSub is missing for user");
        }
        keycloakAdminProvisioner.resetPassword(user.getKeycloakSub(), tempPassword.trim(), temporary);
        TenantMembershipEntity membership = findMembership(tenantId, appUserId);
        return toRecord(user, membership, "PASSWORD_RESET");
    }

    @Transactional
    public TenantUserRecord updateUserProfile(UpdateTenantUserProfileCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireTenant(command.tenantId());
        if (command.appUserId() == null) {
            throw new IllegalArgumentException("appUserId is required");
        }

        AppUserEntity user = findTenantUser(command.tenantId(), command.appUserId());
        TenantMembershipEntity membership = findMembership(command.tenantId(), command.appUserId());

        String displayName = normalizeRequiredText(command.displayName(), "Name", MAX_DISPLAY_NAME_LENGTH);
        String email = normalizeRequiredEmail(command.email());
        String username = normalizeOptionalLoginId(command.username());
        String employeeCode = normalizeOptionalEmployeeCode(command.employeeCode());
        String mobile = normalizeMobile(command.mobile());
        String department = normalizeRequiredText(command.department(), "Department", MAX_DEPARTMENT_LENGTH);
        String role = normalizeRequiredRole(command.role());

        validateSupplementalUniqueness(command.tenantId(), user.getId(), null, employeeCode);
        validateDepartmentCompatibility(role, department);

        keycloakAdminProvisioner.updateTenantUserIdentity(
                user.getKeycloakSub(),
                email,
                username,
                null,
                null,
                true
        );

        user.updateProfile(email, displayName);
        user.updateIdentity(username, department);
        user.updateContactDetails(employeeCode, mobile);

        membership.setRole(role);
        if (command.active() != null) {
            membership.setStatus(Boolean.TRUE.equals(command.active()) ? "ACTIVE" : "DISABLED");
        }

        return toRecord(user, membership, "PROFILE_UPDATED");
    }

    private AppUserEntity findTenantUser(UUID tenantId, UUID appUserId) {
        return appUserRepository.findByTenantIdAndId(tenantId, appUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found for tenant"));
    }

    private TenantMembershipEntity findMembership(UUID tenantId, UUID appUserId) {
        return membershipRepository.findByTenantIdAndAppUserId(tenantId, appUserId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant membership not found"));
    }

    private TenantUserRecord toRecord(
            AppUserEntity user,
            TenantMembershipEntity membership,
            String provisioningStatus
    ) {
        return new TenantUserRecord(
                user.getId(),
                user.getTenantId(),
                user.getKeycloakSub(),
                user.getEmail(),
                user.getUsername(),
                user.getDepartment(),
                user.getDisplayName(),
                user.getStatus(),
                membership == null ? null : membership.getRole(),
                membership == null ? null : membership.getStatus(),
                user.getEmployeeCode(),
                user.getMobile(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                membership == null ? user.getUpdatedAt() : membership.getUpdatedAt(),
                provisioningStatus
        );
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("Role is required.");
        }
        String normalized = role.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_ROLE_LENGTH) {
            throw new IllegalArgumentException("Role must be " + MAX_ROLE_LENGTH + " characters or fewer.");
        }
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported tenant role: " + role);
        }
        return normalized;
    }

    private String normalizeRequiredRole(String value) {
        return normalizeRole(value);
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeRequiredText(String value, String label, int maxLength) {
        String normalized = normalizeNullable(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        }
        if (CONTROL_CHAR_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException(label + " contains invalid characters.");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, String label, int maxLength) {
        String normalized = normalizeNullable(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        }
        if (CONTROL_CHAR_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException(label + " contains invalid characters.");
        }
        return normalized;
    }

    private String normalizeRequiredEmail(String value) {
        String normalized = normalizeNullable(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (normalized.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Email must be " + MAX_EMAIL_LENGTH + " characters or fewer.");
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalLoginId(String value) {
        String normalized = normalizeOptionalText(value, "Login ID", MAX_LOGIN_ID_LENGTH);
        if (normalized == null) {
            return null;
        }
        if (!LOGIN_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid login ID.");
        }
        return normalized;
    }

    private String normalizeOptionalEmployeeCode(String value) {
        String normalized = normalizeOptionalText(value, "Employee code", MAX_EMPLOYEE_CODE_LENGTH);
        if (normalized == null) {
            return null;
        }
        if (!EMPLOYEE_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid employee code.");
        }
        return normalized;
    }

    private String normalizeMobile(String mobile) {
        String normalized = normalizeNullable(mobile);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (!normalized.matches(INDIAN_MOBILE_PATTERN)) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number.");
        }
        return normalized;
    }

    private void validateDepartmentCompatibility(String role, String department) {
        if (!StringUtils.hasText(role) || !StringUtils.hasText(department)) {
            return;
        }
        if (isSuspiciousDepartmentForRole(role, department)) {
            throw new IllegalArgumentException("Choose a matching department for this role.");
        }
    }

    private boolean isSuspiciousDepartmentForRole(String role, String department) {
        String normalizedRole = normalizeRole(role);
        String normalizedDepartment = department.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedRole) {
            case "DOCTOR" -> containsAny(normalizedDepartment, "reception", "billing", "pharmacy", "lab", "laboratory", "inventory", "admin", "administration", "engage", "care");
            case "LAB_TECHNICIAN", "LAB_ASSISTANT", "LAB_APPROVER", "LAB_FRONT_DESK" -> !containsAny(normalizedDepartment, "lab", "laboratory", "pathology", "diagnostic");
            case "PHARMA", "PHARMACIST", "PHARMACY", "PHARMACY_INVENTORY_MANAGER", "PHARMACY_POS_USER" -> !containsAny(normalizedDepartment, "pharmacy", "inventory", "dispens");
            case "ENGAGE_MANAGER", "ENGAGE_EXECUTIVE" -> !containsAny(normalizedDepartment, "engage", "care", "carepilot");
            default -> false;
        };
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String displayNameFor(String email, String username, String displayName) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        if (StringUtils.hasText(email)) {
            return email.trim();
        }
        return username == null ? "User" : username.trim();
    }

    private AppUserEntity upsertTenantUser(UUID tenantId, String keycloakSub, String email, String username, String displayName, String employeeCode, String mobile, String department) {
        String resolvedName = displayNameFor(email, username, displayName);
        String normalizedSub = StringUtils.hasText(keycloakSub) ? keycloakSub.trim() : null;

        if (normalizedSub != null) {
            var bySub = appUserRepository.findByTenantIdAndKeycloakSub(tenantId, normalizedSub);
            if (bySub.isPresent()) {
                AppUserEntity existing = bySub.get();
                existing.updateProfile(email, resolvedName);
                validateSupplementalUniqueness(tenantId, existing.getId(), username, employeeCode);
                applySupplementalUpdates(existing, username, employeeCode, mobile, department);
                return existing;
            }
        }

        if (StringUtils.hasText(email)) {
            var byEmail = appUserRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email);
            if (byEmail.isPresent()) {
                AppUserEntity existing = byEmail.get();
                if (normalizedSub != null) {
                    existing.setKeycloakSub(normalizedSub);
                }
                existing.updateProfile(email, resolvedName);
                validateSupplementalUniqueness(tenantId, existing.getId(), username, employeeCode);
                applySupplementalUpdates(existing, username, employeeCode, mobile, department);
                return existing;
            }
        }

        try {
            AppUserEntity created = AppUserEntity.create(tenantId, normalizedSub, email, resolvedName);
            validateSupplementalUniqueness(tenantId, null, username, employeeCode);
            applySupplementalUpdates(created, username, employeeCode, mobile, department);
            return appUserRepository.save(created);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedSub != null) {
                var existingBySub = appUserRepository.findByTenantIdAndKeycloakSub(tenantId, normalizedSub);
                if (existingBySub.isPresent()) {
                    AppUserEntity existing = existingBySub.get();
                    existing.updateProfile(email, resolvedName);
                    applySupplementalUpdates(existing, username, employeeCode, mobile, department);
                    return existing;
                }
            }
            if (StringUtils.hasText(email)) {
                var existingByEmail = appUserRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email);
                if (existingByEmail.isPresent()) {
                    AppUserEntity existing = existingByEmail.get();
                    if (normalizedSub != null) {
                        existing.setKeycloakSub(normalizedSub);
                    }
                    existing.updateProfile(email, resolvedName);
                    applySupplementalUpdates(existing, username, employeeCode, mobile, department);
                    return existing;
                }
            }
            throw ex;
        }
    }

    private void applySupplementalUpdates(AppUserEntity user, String username, String employeeCode, String mobile, String department) {
        if (StringUtils.hasText(username) || StringUtils.hasText(department)) {
            user.updateIdentity(StringUtils.hasText(username) ? username : user.getUsername(), StringUtils.hasText(department) ? department : user.getDepartment());
        }
        if (StringUtils.hasText(employeeCode) || StringUtils.hasText(mobile)) {
            user.updateContactDetails(StringUtils.hasText(employeeCode) ? employeeCode : user.getEmployeeCode(), StringUtils.hasText(mobile) ? mobile : user.getMobile());
        }
    }

    private void validateSupplementalUniqueness(UUID tenantId, UUID currentUserId, String username, String employeeCode) {
        if (StringUtils.hasText(username)) {
            appUserRepository.findByTenantIdAndUsernameIgnoreCase(tenantId, username)
                    .filter(existing -> currentUserId == null || !currentUserId.equals(existing.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("This login ID is already in use.");
                    });
        }
        if (StringUtils.hasText(employeeCode)) {
            appUserRepository.findByTenantIdAndEmployeeCodeIgnoreCase(tenantId, employeeCode)
                    .filter(existing -> currentUserId == null || !currentUserId.equals(existing.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Employee code already exists for this clinic.");
                    });
        }
    }

    private String correlationId() {
        return org.slf4j.MDC.get("correlationId");
    }

    private String failedStage(Throwable ex) {
        String message = ex == null ? null : ex.getMessage();
        if (message == null) {
            return "unknown";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("keycloak")) {
            return "keycloak";
        }
        if (lower.contains("membership")) {
            return "membership";
        }
        if (lower.contains("employee code") || lower.contains("username") || lower.contains("duplicate")) {
            return "validation";
        }
        if (lower.contains("app user") || lower.contains("app_user")) {
            return "app_user";
        }
        return "persistence";
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
