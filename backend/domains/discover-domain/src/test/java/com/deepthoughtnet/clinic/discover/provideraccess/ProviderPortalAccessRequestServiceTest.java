package com.deepthoughtnet.clinic.discover.provideraccess;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.discover.provideraccess.db.ProviderPortalAccessRequestEntity;
import com.deepthoughtnet.clinic.discover.provideraccess.db.ProviderPortalAccessRequestRepository;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderPortalAccessRequestServiceTest {
    @Mock
    private ProviderPortalAccessRequestRepository requestRepository;
    @Mock
    private ProviderApplicationRepository providerApplicationRepository;
    @Mock
    private DiscoverProviderAccountRepository providerAccountRepository;
    @Mock
    private DiscoverVerificationService verificationService;
    @Mock
    private AuditEventPublisher auditEventPublisher;

    private ProviderPortalAccessRequestService service;

    @BeforeEach
    void setUp() {
        service = new ProviderPortalAccessRequestService(
                requestRepository,
                providerApplicationRepository,
                providerAccountRepository,
                verificationService,
                auditEventPublisher,
                new ObjectMapper()
        );
    }

    @Test
    void submitRejectsDuplicatePendingRequests() {
        ProviderPortalAccessRequestEntity existing = ProviderPortalAccessRequestEntity.create(
                ProviderType.HOSPITAL,
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "provider@example.com",
                "9876501111",
                "9876501111",
                null,
                null
        );
        when(requestRepository.findAll()).thenReturn(List.of(existing));

        ProviderPortalAccessRequestConflictException exception = assertThrows(
                ProviderPortalAccessRequestConflictException.class,
                () -> service.submit(new ProviderPortalAccessRequestCommand(
                        "Jeevanam Multispeciality Hospital",
                        "provider@example.com",
                        "9876501111",
                        ProviderType.HOSPITAL,
                        null,
                        null
                ))
        );

        assertEquals("An access request is already pending.", exception.getMessage());
    }

    @Test
    void submitValidatesRequiredProviderAccessFieldsAndNormalizesIdentifiers() {
        when(requestRepository.findAll()).thenReturn(List.of());
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditEventPublisher.record(any())).thenReturn(UUID.randomUUID());

        ProviderPortalAccessRequestRecord submitted = service.submit(new ProviderPortalAccessRequestCommand(
                "  Jeevanam   Multispeciality Hospital  ",
                "Provider@Example.com",
                "+91 98765 01200",
                ProviderType.HOSPITAL,
                "JEV-2026-001",
                "  Please review access  "
        ));

        assertEquals("Jeevanam Multispeciality Hospital", submitted.fullName());
        assertEquals("provider@example.com", submitted.email());
        assertEquals("9876501200", submitted.mobile());
        assertEquals("JEV-2026-001", submitted.providerApplicationReference());
        assertEquals("Please review access", submitted.note());
    }

    @Test
    void submitRejectsInvalidProviderAccessFields() {
        IllegalArgumentException referenceException = assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(new ProviderPortalAccessRequestCommand(
                        "Jeevanam Multispeciality Hospital",
                        "provider@example.com",
                        "9876501111",
                        ProviderType.HOSPITAL,
                        "abc@@@",
                        null
                ))
        );

        assertEquals("Enter a valid provider application reference.", referenceException.getMessage());

        IllegalArgumentException emailException = assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(new ProviderPortalAccessRequestCommand(
                        "Jeevanam Multispeciality Hospital",
                        "not-an-email",
                        "9876501111",
                        ProviderType.HOSPITAL,
                        null,
                        null
                ))
        );
        assertEquals("Enter a valid email address.", emailException.getMessage());

        IllegalArgumentException mobileException = assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(new ProviderPortalAccessRequestCommand(
                        "Jeevanam Multispeciality Hospital",
                        "provider@example.com",
                        "98abc123",
                        ProviderType.HOSPITAL,
                        null,
                        null
                ))
        );
        assertEquals("Enter a valid 10-digit Indian mobile number.", mobileException.getMessage());
    }

    @Test
    void authenticateValidatesRegisteredEmailOrMobileAndEightDigitAccessCode() {
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create("provider@example.com", "9876501111");
        ProviderPortalAccessRequestEntity request = ProviderPortalAccessRequestEntity.create(
                ProviderType.HOSPITAL,
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "provider@example.com",
                "9876501111",
                "9876501111",
                null,
                null
        );
        request.approve(
                UUID.randomUUID(),
                "Platform Admin",
                account.getId(),
                "provider@example.com",
                "JEV-2026-001",
                hashAccessCode("12345678"),
                java.time.OffsetDateTime.now(),
                java.time.OffsetDateTime.now().plusDays(7)
        );

        when(requestRepository.findAll()).thenReturn(List.of(request));
        when(providerAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        ProviderPortalAccessGrantRecord byEmail = service.authenticate("Provider@Example.com", "12345678");
        assertEquals(account.getId(), byEmail.providerAccountId());

        ProviderPortalAccessGrantRecord byMobile = service.authenticate("+91 98765 01111", "12345678");
        assertEquals(account.getId(), byMobile.providerAccountId());

        IllegalArgumentException identifierException = assertThrows(
                IllegalArgumentException.class,
                () -> service.authenticate("abc@", "12345678")
        );
        assertEquals("Enter a valid registered email address or mobile number.", identifierException.getMessage());

        IllegalArgumentException accessCodeException = assertThrows(
                IllegalArgumentException.class,
                () -> service.authenticate("provider@example.com", "12ab5678")
        );
        assertEquals("Enter the 8-digit temporary access code.", accessCodeException.getMessage());
    }

    @Test
    void approveCreatesImmediateLoginAccessForApprovedProviders() {
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create("provider@example.com", "9876501111");
        ProviderApplicationEntity application = ProviderApplicationEntity.create(
                UUID.randomUUID(),
                "JEV-2026-001",
                ProviderType.HOSPITAL,
                "token-hash",
                "provider@example.com",
                "9876501111",
                "password",
                true,
                true
        );
        application.setProviderAccountId(account.getId());

        ProviderPortalAccessRequestEntity request = ProviderPortalAccessRequestEntity.create(
                ProviderType.HOSPITAL,
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "provider@example.com",
                "9876501111",
                "9876501111",
                "JEV-2026-001",
                null
        );

        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerApplicationRepository.findAll()).thenReturn(List.of(application));
        when(providerAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(auditEventPublisher.record(any())).thenReturn(UUID.randomUUID());

        ProviderPortalAccessRequestRecord approved = service.approve(
                request.getId(),
                UUID.randomUUID(),
                "Platform Admin",
                "Approved for preview",
                null
        );

        assertEquals(ProviderPortalAccessRequestStatus.APPROVED, approved.status());
        assertNotNull(approved.temporaryAccessCode());
        assertEquals(8, approved.temporaryAccessCode().length());
        assertEquals(account.getId(), approved.linkedProviderAccountId());
        assertEquals("JEV-2026-001", approved.linkedProviderApplicationReference());

        when(requestRepository.findAll()).thenReturn(List.of(request));
        ProviderPortalAccessGrantRecord grant = service.authenticate("provider@example.com", approved.temporaryAccessCode());
        assertEquals(account.getId(), grant.providerAccountId());
        assertEquals("JEV-2026-001", grant.providerApplicationReference());
    }

    @Test
    void approveRejectsRequestsThatAreNoLongerPending() {
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create("provider@example.com", "9876501111");
        ProviderPortalAccessRequestEntity request = ProviderPortalAccessRequestEntity.create(
                ProviderType.HOSPITAL,
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "provider@example.com",
                "9876501111",
                "9876501111",
                null,
                null
        );
        request.approve(
                UUID.randomUUID(),
                "Platform Admin",
                account.getId(),
                "provider@example.com",
                "JEV-2026-001",
                hashAccessCode("12345678"),
                java.time.OffsetDateTime.now(),
                java.time.OffsetDateTime.now().plusDays(7)
        );

        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        ProviderPortalAccessRequestConflictException exception = assertThrows(
                ProviderPortalAccessRequestConflictException.class,
                () -> service.approve(request.getId(), UUID.randomUUID(), "Platform Admin", "Approved again", null)
        );

        assertEquals("This access request can only be approved while it is pending review.", exception.getMessage());
    }

    @Test
    void rejectRejectsRequestsThatAreNoLongerPending() {
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create("provider@example.com", "9876501111");
        ProviderPortalAccessRequestEntity request = ProviderPortalAccessRequestEntity.create(
                ProviderType.HOSPITAL,
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "provider@example.com",
                "9876501111",
                "9876501111",
                null,
                null
        );
        request.approve(
                UUID.randomUUID(),
                "Platform Admin",
                account.getId(),
                "provider@example.com",
                "JEV-2026-001",
                hashAccessCode("12345678"),
                java.time.OffsetDateTime.now(),
                java.time.OffsetDateTime.now().plusDays(7)
        );

        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        ProviderPortalAccessRequestConflictException exception = assertThrows(
                ProviderPortalAccessRequestConflictException.class,
                () -> service.reject(request.getId(), UUID.randomUUID(), "Platform Admin", "Rejected again")
        );

        assertEquals("This access request can only be rejected while it is pending review.", exception.getMessage());
    }

    @Test
    void revokeRejectsRequestsThatAreNotApproved() {
        ProviderPortalAccessRequestEntity request = ProviderPortalAccessRequestEntity.create(
                ProviderType.HOSPITAL,
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "provider@example.com",
                "9876501111",
                "9876501111",
                null,
                null
        );

        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        ProviderPortalAccessRequestConflictException exception = assertThrows(
                ProviderPortalAccessRequestConflictException.class,
                () -> service.revoke(request.getId(), UUID.randomUUID(), "Platform Admin", "Revoked too early")
        );

        assertEquals("This access request can only be revoked after approval.", exception.getMessage());
    }

    @Test
    void revokeMarksRequestRevokedAndBlocksLogin() {
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create("provider@example.com", "9876501111");
        ProviderPortalAccessRequestEntity request = ProviderPortalAccessRequestEntity.create(
                ProviderType.HOSPITAL,
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "provider@example.com",
                "9876501111",
                "9876501111",
                null,
                null
        );
        request.approve(
                UUID.randomUUID(),
                "Platform Admin",
                account.getId(),
                "provider@example.com",
                "JEV-2026-001",
                hashAccessCode("12345678"),
                java.time.OffsetDateTime.now(),
                java.time.OffsetDateTime.now().plusDays(7)
        );

        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        doNothing().when(verificationService).revokeSessionsForAccount(account.getId());
        when(auditEventPublisher.record(any())).thenReturn(UUID.randomUUID());

        ProviderPortalAccessRequestRecord revoked = service.revoke(request.getId(), UUID.randomUUID(), "Platform Admin", "No longer needed");
        assertEquals(ProviderPortalAccessRequestStatus.REVOKED, revoked.status());

        when(requestRepository.findAll()).thenReturn(List.of(request));
        assertThrows(
                ProviderPortalAccessRequestConflictException.class,
                () -> service.authenticate("provider@example.com", "12345678")
        );
    }

    private static String hashAccessCode(String accessCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(accessCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
