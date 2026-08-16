package com.deepthoughtnet.clinic.discover.provideraccess;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.discover.provideraccess.db.ProviderPortalAccessRequestEntity;
import com.deepthoughtnet.clinic.discover.provideraccess.db.ProviderPortalAccessRequestRepository;
import com.deepthoughtnet.clinic.discover.verification.DiscoverContactNormalizer;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.deepthoughtnet.clinic.discover.verification.VerificationChannel;

@Service
public class ProviderPortalAccessRequestService {
    private static final String ENTITY_TYPE = "PROVIDER_PORTAL_ACCESS_REQUEST";
    private static final Duration ACCESS_CODE_TTL = Duration.ofDays(7);
    private static final UUID FALLBACK_AUDIT_TENANT_ID = UUID.nameUUIDFromBytes("discover-provider-access".getBytes(StandardCharsets.UTF_8));
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PROVIDER_REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 _./-]{0,79}$");
    private static final Pattern MOBILE_ALLOWED_PATTERN = Pattern.compile("^[0-9+\\s()-]+$");

    private final ProviderPortalAccessRequestRepository requestRepository;
    private final ProviderApplicationRepository providerApplicationRepository;
    private final DiscoverProviderAccountRepository providerAccountRepository;
    private final DiscoverVerificationService verificationService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public ProviderPortalAccessRequestService(
            ProviderPortalAccessRequestRepository requestRepository,
            ProviderApplicationRepository providerApplicationRepository,
            DiscoverProviderAccountRepository providerAccountRepository,
            DiscoverVerificationService verificationService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.requestRepository = requestRepository;
        this.providerApplicationRepository = providerApplicationRepository;
        this.providerAccountRepository = providerAccountRepository;
        this.verificationService = verificationService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    public DiscoverVerificationService getVerificationService() {
        return verificationService;
    }

    @Transactional
    public ProviderPortalAccessRequestRecord submit(ProviderPortalAccessRequestCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Access request is required");
        }
        ProviderType providerType = command.providerType();
        if (providerType == null) {
            throw new IllegalArgumentException("Provider type is required");
        }
        String fullName = normalizeProviderName(command.fullName());
        String email = normalizeRequiredEmail(command.email());
        String emailNormalized = normalizeEmail(email);
        String mobile = normalizeRequiredMobile(command.mobile());
        String mobileNormalized = normalizeMobile(mobile);
        String providerApplicationReference = normalizeOptionalReference(command.providerApplicationReference());
        String note = normalizeOptionalNote(command.note());

        ensureUnique(providerType, emailNormalized, mobileNormalized, providerApplicationReference);

        ProviderPortalAccessRequestEntity saved = requestRepository.save(ProviderPortalAccessRequestEntity.create(
                providerType,
                fullName,
                email,
                emailNormalized,
                mobile,
                mobileNormalized,
                providerApplicationReference,
                note
        ));
        recordAudit(saved.getId(), "PROVIDER_ACCESS_REQUESTED", null, "Provider access requested", detailsJson(saved));
        return toRecord(saved, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<ProviderPortalAccessRequestRecord> list(String status, String q) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedQuery = truncateOptional(q, 128);
        List<ProviderPortalAccessRequestEntity> all = requestRepository.findAll();

        return all.stream()
                .filter(request -> normalizedStatus == null || request.getStatus().name().equalsIgnoreCase(normalizedStatus))
                .filter(request -> matchesQuery(request, normalizedQuery))
                .sorted(Comparator.comparing(ProviderPortalAccessRequestEntity::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(request -> toRecord(request, null, null, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProviderPortalAccessRequestRecord> find(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return requestRepository.findById(id).map(request -> toRecord(request, null, null, null));
    }

    @Transactional
    public ProviderPortalAccessRequestRecord approve(
            UUID id,
            UUID actorAppUserId,
            String reviewedByDisplayName,
            String reason,
            String providerApplicationReference
    ) {
        ProviderPortalAccessRequestEntity entity = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found"));
        if (entity.getStatus() != ProviderPortalAccessRequestStatus.REQUESTED) {
            throw new ProviderPortalAccessRequestConflictException("This access request can only be approved while it is pending review.");
        }

        ProviderLinkResolution link = resolveProviderLink(entity, providerApplicationReference);
        String accessCode = generateAccessCode();
        OffsetDateTime now = OffsetDateTime.now();
        entity.approve(
                actorAppUserId,
                reviewedByDisplayName,
                link.providerAccount().getId(),
                providerAccountDisplayName(link.providerAccount()),
                link.providerApplicationReference(),
                hashAccessCode(accessCode),
                now,
                now.plus(ACCESS_CODE_TTL)
        );
        requestRepository.save(entity);
        recordAudit(entity.getId(), "PROVIDER_ACCESS_APPROVED", actorAppUserId, "Provider access approved", detailsJson(entity));
        return toRecord(entity, link.providerAccount(), link.providerApplicationReference(), accessCode);
    }

    @Transactional
    public ProviderPortalAccessRequestRecord reject(UUID id, UUID actorAppUserId, String reviewedByDisplayName, String reason) {
        ProviderPortalAccessRequestEntity entity = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found"));
        if (entity.getStatus() != ProviderPortalAccessRequestStatus.REQUESTED) {
            throw new ProviderPortalAccessRequestConflictException("This access request can only be rejected while it is pending review.");
        }
        entity.reject(actorAppUserId, reviewedByDisplayName, normalizeOptional(reason, 512, "Reason must be 512 characters or fewer."));
        requestRepository.save(entity);
        recordAudit(entity.getId(), "PROVIDER_ACCESS_REJECTED", actorAppUserId, "Provider access rejected", detailsJson(entity));
        return toRecord(entity, accountFor(entity.getLinkedProviderAccountId()), entity.getLinkedProviderApplicationReference(), null);
    }

    @Transactional
    public ProviderPortalAccessRequestRecord revoke(UUID id, UUID actorAppUserId, String reviewedByDisplayName, String reason) {
        ProviderPortalAccessRequestEntity entity = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found"));
        if (entity.getStatus() != ProviderPortalAccessRequestStatus.APPROVED) {
            throw new ProviderPortalAccessRequestConflictException("This access request can only be revoked after approval.");
        }
        entity.revoke(actorAppUserId, reviewedByDisplayName, normalizeOptional(reason, 512, "Reason must be 512 characters or fewer."));
        requestRepository.save(entity);
        if (entity.getLinkedProviderAccountId() != null) {
            verificationService.revokeSessionsForAccount(entity.getLinkedProviderAccountId());
        }
        recordAudit(entity.getId(), "PROVIDER_ACCESS_REVOKED", actorAppUserId, "Provider access revoked", detailsJson(entity));
        return toRecord(entity, accountFor(entity.getLinkedProviderAccountId()), entity.getLinkedProviderApplicationReference(), null);
    }

    @Transactional
    public ProviderPortalAccessGrantRecord authenticate(String identifier, String accessCode) {
        if (!StringUtils.hasText(identifier)) {
            throw new IllegalArgumentException("Email address or mobile number is required");
        }
        if (!StringUtils.hasText(accessCode)) {
            throw new IllegalArgumentException("Access code is required");
        }
        String normalizedIdentifier = normalizeProviderLoginIdentifier(identifier);
        String normalizedAccessCode = normalizeAccessCode(accessCode);
        VerificationLookup lookup = lookup(normalizedIdentifier);
        ProviderPortalAccessRequestEntity entity = requestRepository.findAll().stream()
                .filter(request -> request.getStatus() == ProviderPortalAccessRequestStatus.APPROVED)
                .filter(request -> matchesLookup(request, lookup))
                .sorted(Comparator.comparing(ProviderPortalAccessRequestEntity::getApprovedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProviderPortalAccessRequestEntity::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElseThrow(() -> new ProviderPortalAccessRequestConflictException("This access request is not currently active."));

        if (entity.getAccessCodeHash() == null || entity.getAccessCodeExpiresAt() == null || entity.getAccessCodeExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ProviderPortalAccessRequestConflictException("This access code has expired. Please request a new approval.");
        }
        if (!matchesAccessCode(normalizedAccessCode, entity.getAccessCodeHash())) {
            throw new ProviderPortalAccessRequestConflictException("The access code is invalid.");
        }
        if (entity.getLinkedProviderAccountId() == null) {
            ProviderLinkResolution link = resolveProviderLink(entity, entity.getProviderApplicationReference());
            entity.setLinkedProviderAccountId(link.providerAccount().getId());
            entity.setLinkedProviderAccountDisplayName(providerAccountDisplayName(link.providerAccount()));
            if (!Objects.equals(entity.getLinkedProviderApplicationReference(), link.providerApplicationReference())) {
                entity.approve(entity.getReviewedBy(), entity.getReviewedByDisplayName(), link.providerAccount().getId(), providerAccountDisplayName(link.providerAccount()), link.providerApplicationReference(), entity.getAccessCodeHash(), entity.getAccessCodeIssuedAt(), entity.getAccessCodeExpiresAt());
            } else {
                requestRepository.save(entity);
            }
        }
        DiscoverProviderAccountEntity account = accountFor(entity.getLinkedProviderAccountId());
        if (account == null) {
            throw new ProviderPortalAccessRequestConflictException("This access request is not currently active.");
        }
        verificationService.revokeSessionsForAccount(account.getId());
        return new ProviderPortalAccessGrantRecord(account.getId(), providerAccountDisplayName(account), entity.getLinkedProviderApplicationReference());
    }

    private ProviderLinkResolution resolveProviderLink(ProviderPortalAccessRequestEntity request, String overrideApplicationReference) {
        String applicationReference = normalizeOptional(
                overrideApplicationReference != null ? overrideApplicationReference : request.getProviderApplicationReference(),
                64,
                "Provider application reference must be 64 characters or fewer."
        );
        Optional<ProviderApplicationEntity> explicitApplication = findApplication(applicationReference);
        if (explicitApplication.isPresent()) {
            return linkApplicationToAccount(explicitApplication.get(), request);
        }

        List<ProviderApplicationEntity> candidates = providerApplicationRepository.findAll().stream()
                .filter(application -> application.getProviderType() == request.getProviderType())
                .filter(application -> matchesContact(application, request))
                .toList();
        List<UUID> candidateAccountIds = candidates.stream()
                .map(ProviderApplicationEntity::getProviderAccountId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (candidateAccountIds.size() > 1) {
            throw new ProviderPortalAccessRequestConflictException("Multiple provider workspaces match this request. Provide a provider application reference.");
        }
        if (!candidateAccountIds.isEmpty()) {
            DiscoverProviderAccountEntity account = accountFor(candidateAccountIds.get(0));
            if (account != null) {
                linkApplicationsToAccount(candidates, account);
                return new ProviderLinkResolution(account, firstApplicationReference(candidates, account.getId()));
            }
        }

        if (!candidates.isEmpty()) {
            ProviderApplicationEntity application = candidates.get(0);
            return linkApplicationToAccount(application, request);
        }

        DiscoverProviderAccountEntity existingAccount = findExistingAccount(request);
        if (existingAccount != null) {
            linkApplicationsToAccount(candidates, existingAccount);
            return new ProviderLinkResolution(existingAccount, firstApplicationReference(providerApplicationRepository.findAll(), existingAccount.getId()));
        }

        DiscoverProviderAccountEntity created = DiscoverProviderAccountEntity.create(
                request.getEmailNormalized(),
                request.getMobileNormalized()
        );
        if (request.getEmailNormalized() != null) {
            created.markEmailVerified();
        }
        if (request.getMobileNormalized() != null) {
            created.markPhoneVerified();
        }
        created = providerAccountRepository.save(created);
        return new ProviderLinkResolution(created, null);
    }

    private ProviderLinkResolution linkApplicationToAccount(ProviderApplicationEntity application, ProviderPortalAccessRequestEntity request) {
        DiscoverProviderAccountEntity account = application.getProviderAccountId() == null
                ? findExistingAccount(request)
                : accountFor(application.getProviderAccountId());
        if (account == null) {
            account = DiscoverProviderAccountEntity.create(request.getEmailNormalized(), request.getMobileNormalized());
            if (request.getEmailNormalized() != null) {
                account.markEmailVerified();
            }
            if (request.getMobileNormalized() != null) {
                account.markPhoneVerified();
            }
            account = providerAccountRepository.save(account);
        }
        if (!account.getId().equals(application.getProviderAccountId())) {
            application.setProviderAccountId(account.getId());
            providerApplicationRepository.save(application);
        }
        linkApplicationsToAccount(providerApplicationRepository.findAll().stream()
                .filter(candidate -> candidate.getProviderType() == request.getProviderType())
                .filter(candidate -> matchesContact(candidate, request))
                .toList(), account);
        return new ProviderLinkResolution(account, application.getReferenceNumber());
    }

    private void linkApplicationsToAccount(List<ProviderApplicationEntity> applications, DiscoverProviderAccountEntity account) {
        for (ProviderApplicationEntity application : applications) {
            if (application.getProviderAccountId() == null || !account.getId().equals(application.getProviderAccountId())) {
                application.setProviderAccountId(account.getId());
                providerApplicationRepository.save(application);
            }
        }
    }

    private DiscoverProviderAccountEntity findExistingAccount(ProviderPortalAccessRequestEntity request) {
        if (StringUtils.hasText(request.getEmailNormalized())) {
            Optional<DiscoverProviderAccountEntity> emailAccount = providerAccountRepository.findByNormalizedEmail(request.getEmailNormalized());
            if (emailAccount.isPresent()) {
                return emailAccount.get();
            }
        }
        if (StringUtils.hasText(request.getMobileNormalized())) {
            Optional<DiscoverProviderAccountEntity> phoneAccount = providerAccountRepository.findByNormalizedPhone(request.getMobileNormalized());
            if (phoneAccount.isPresent()) {
                return phoneAccount.get();
            }
        }
        return null;
    }

    private Optional<ProviderApplicationEntity> findApplication(String referenceNumber) {
        if (!StringUtils.hasText(referenceNumber)) {
            return Optional.empty();
        }
        return providerApplicationRepository.findByReferenceNumber(referenceNumber.trim());
    }

    private boolean matchesContact(ProviderApplicationEntity application, ProviderPortalAccessRequestEntity request) {
        boolean emailMatch = StringUtils.hasText(request.getEmailNormalized()) && StringUtils.hasText(application.getEmail())
                && request.getEmailNormalized().equalsIgnoreCase(application.getEmail().trim().toLowerCase(Locale.ROOT));
        boolean phoneMatch = StringUtils.hasText(request.getMobileNormalized()) && StringUtils.hasText(application.getPhone())
                && request.getMobileNormalized().equalsIgnoreCase(DiscoverContactNormalizer.normalizeRecipient(application.getPhone(), com.deepthoughtnet.clinic.discover.verification.VerificationChannel.SMS));
        return emailMatch || phoneMatch;
    }

    private boolean matchesLookup(ProviderPortalAccessRequestEntity request, VerificationLookup lookup) {
        if (lookup.channel() == VerificationChannel.EMAIL) {
            return StringUtils.hasText(request.getEmailNormalized()) && request.getEmailNormalized().equals(lookup.normalizedIdentifier());
        }
        return request.getMobileNormalized().equals(lookup.normalizedIdentifier());
    }

    private VerificationLookup lookup(String identifier) {
        VerificationChannel channel = identifier.contains("@") ? VerificationChannel.EMAIL : VerificationChannel.SMS;
        String normalized = DiscoverContactNormalizer.normalizeRecipient(identifier, channel);
        return new VerificationLookup(channel, normalized);
    }

    private void ensureUnique(ProviderType providerType, String emailNormalized, String mobileNormalized, String providerApplicationReference) {
        requestRepository.findAll().stream()
                .filter(request -> request.getProviderType() == providerType)
                .filter(request -> isDuplicate(request, emailNormalized, mobileNormalized, providerApplicationReference))
                .filter(request -> request.getStatus() == ProviderPortalAccessRequestStatus.REQUESTED
                        || request.getStatus() == ProviderPortalAccessRequestStatus.APPROVED)
                .findFirst()
                .ifPresent(request -> {
                    throw new ProviderPortalAccessRequestConflictException(
                            request.getStatus() == ProviderPortalAccessRequestStatus.APPROVED
                                    ? "Access has already been approved for this account."
                                    : "An access request is already pending."
                    );
                });
    }

    private boolean isDuplicate(
            ProviderPortalAccessRequestEntity request,
            String emailNormalized,
            String mobileNormalized,
            String providerApplicationReference
    ) {
        boolean emailMatch = StringUtils.hasText(emailNormalized) && emailNormalized.equals(request.getEmailNormalized());
        boolean mobileMatch = mobileNormalized.equals(request.getMobileNormalized());
        boolean applicationMatch = StringUtils.hasText(providerApplicationReference)
                && providerApplicationReference.equalsIgnoreCase(request.getProviderApplicationReference());
        return emailMatch || mobileMatch || applicationMatch;
    }

    private ProviderPortalAccessRequestRecord toRecord(
            ProviderPortalAccessRequestEntity entity,
            DiscoverProviderAccountEntity providerAccount,
            String linkedProviderApplicationReference,
            String temporaryAccessCode
    ) {
        return new ProviderPortalAccessRequestRecord(
                entity.getId(),
                entity.getRequestType(),
                entity.getProviderType(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getMobile(),
                entity.getProviderApplicationReference(),
                entity.getNote(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getLinkedProviderAccountId(),
                entity.getLinkedProviderAccountDisplayName(),
                linkedProviderApplicationReference != null ? linkedProviderApplicationReference : entity.getLinkedProviderApplicationReference(),
                entity.getReviewedBy(),
                entity.getReviewedByDisplayName(),
                temporaryAccessCode,
                entity.getRequestedAt(),
                entity.getReviewedAt(),
                entity.getApprovedAt(),
                entity.getRevokedAt(),
                entity.getAccessCodeIssuedAt(),
                entity.getAccessCodeExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    private DiscoverProviderAccountEntity accountFor(UUID providerAccountId) {
        if (providerAccountId == null) {
            return null;
        }
        return providerAccountRepository.findById(providerAccountId).orElse(null);
    }

    private String providerAccountDisplayName(DiscoverProviderAccountEntity account) {
        if (account == null) {
            return null;
        }
        if (StringUtils.hasText(account.getNormalizedEmail())) {
            return account.getNormalizedEmail();
        }
        if (StringUtils.hasText(account.getNormalizedPhone())) {
            return account.getNormalizedPhone();
        }
        return account.getId().toString();
    }

    private String firstApplicationReference(List<ProviderApplicationEntity> applications, UUID accountId) {
        return applications.stream()
                .filter(application -> Objects.equals(application.getProviderAccountId(), accountId))
                .map(ProviderApplicationEntity::getReferenceNumber)
                .findFirst()
                .orElse(null);
    }

    private String detailsJson(ProviderPortalAccessRequestEntity entity) {
        try {
            Map<String, Object> details = new java.util.LinkedHashMap<>();
            details.put("id", entity.getId().toString());
            details.put("requestType", entity.getRequestType().name());
            details.put("providerType", entity.getProviderType().name());
            details.put("fullName", entity.getFullName());
            details.put("email", entity.getEmail());
            details.put("mobile", entity.getMobile());
            details.put("status", entity.getStatus().name());
            details.put("providerApplicationReference", entity.getProviderApplicationReference());
            details.put("linkedProviderAccountId", entity.getLinkedProviderAccountId() == null ? null : entity.getLinkedProviderAccountId().toString());
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private void recordAudit(UUID entityId, String action, UUID actorAppUserId, String summary, String detailsJson) {
        auditEventPublisher.record(new AuditEventCommand(
                auditTenantId(),
                ENTITY_TYPE,
                entityId,
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                summary,
                detailsJson
        ));
    }

    private UUID auditTenantId() {
        return FALLBACK_AUDIT_TENANT_ID;
    }

    private String hashAccessCode(String accessCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(accessCode.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash provider access code.", ex);
        }
    }

    private boolean matchesAccessCode(String accessCode, String accessCodeHash) {
        return accessCodeHash != null && hashAccessCode(accessCode).equals(accessCodeHash);
    }

    private String generateAccessCode() {
        int code = ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
        return String.valueOf(code);
    }

    private String normalizeProviderName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Provider name is required");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2 || normalized.length() > 120) {
            throw new IllegalArgumentException("Enter a provider name between 2 and 120 characters.");
        }
        return normalized;
    }

    private String normalizeRequiredMobile(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 20) {
            throw new IllegalArgumentException("Enter a valid 10-digit Indian mobile number.");
        }
        if (!MOBILE_ALLOWED_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid 10-digit Indian mobile number.");
        }
        String digits = normalized.replaceAll("[\\s()-]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (!digits.matches("[6-9]\\d{9}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit Indian mobile number.");
        }
        return digits;
    }

    private String normalizeMobile(String value) {
        return DiscoverContactNormalizer.normalizeRecipient(value, com.deepthoughtnet.clinic.discover.verification.VerificationChannel.SMS);
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredEmail(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Email address is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return normalized;
    }

    private String normalizeOptionalReference(String value) {
        String normalized = normalizeOptional(value, 80, "Enter a valid provider application reference.");
        if (normalized == null) {
            return null;
        }
        if (!PROVIDER_REFERENCE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid provider application reference.");
        }
        return normalized;
    }

    private String normalizeOptionalNote(String value) {
        return normalizeOptional(value, 500, "Note must be 500 characters or fewer.");
    }

    private String normalizeProviderLoginIdentifier(String value) {
        String normalized = normalizeOptional(value, 254, "Enter a valid registered email address or mobile number.");
        if (normalized == null) {
            throw new IllegalArgumentException("Enter a valid registered email address or mobile number.");
        }
        if (normalized.contains("@")) {
            if (!EMAIL_PATTERN.matcher(normalized).matches()) {
                throw new IllegalArgumentException("Enter a valid registered email address or mobile number.");
            }
            return normalized.toLowerCase(Locale.ROOT);
        }
        return normalizeRequiredMobile(normalized);
    }

    private String normalizeAccessCode(String value) {
        String normalized = normalizeOptional(value, 8, "Enter the 8-digit temporary access code.");
        if (normalized == null || !normalized.matches("\\d{8}")) {
            throw new IllegalArgumentException("Enter the 8-digit temporary access code.");
        }
        return normalized;
    }

    private String normalizeOptional(String value, Integer maxLength, String invalidMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (maxLength != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(invalidMessage);
        }
        return normalized;
    }

    private String truncateOptional(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status) || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        return status.trim();
    }

    private boolean matchesQuery(ProviderPortalAccessRequestEntity request, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return matches(request.getFullName(), normalized)
                || matches(request.getEmail(), normalized)
                || matches(request.getMobile(), normalized)
                || matches(request.getProviderApplicationReference(), normalized)
                || matches(request.getLinkedProviderAccountDisplayName(), normalized)
                || matches(request.getStatus().name(), normalized)
                || matches(request.getProviderType().name(), normalized);
    }

    private boolean matches(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean isTerminal(ProviderPortalAccessRequestStatus status) {
        return status == ProviderPortalAccessRequestStatus.REJECTED || status == ProviderPortalAccessRequestStatus.REVOKED;
    }

    private record VerificationLookup(com.deepthoughtnet.clinic.discover.verification.VerificationChannel channel, String normalizedIdentifier) {
    }

    private record ProviderLinkResolution(DiscoverProviderAccountEntity providerAccount, String providerApplicationReference) {
    }
}
