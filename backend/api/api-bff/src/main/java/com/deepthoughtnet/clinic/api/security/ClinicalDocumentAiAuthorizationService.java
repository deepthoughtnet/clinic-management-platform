package com.deepthoughtnet.clinic.api.security;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.security.Roles;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClinicalDocumentAiAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(ClinicalDocumentAiAuthorizationService.class);

    private final ClinicalDocumentRepository documentRepository;
    private final PermissionChecker permissionChecker;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public ClinicalDocumentAiAuthorizationService(ClinicalDocumentRepository documentRepository,
                                                  PermissionChecker permissionChecker,
                                                  DoctorAssignmentSecurityService doctorAssignmentSecurityService) {
        this.documentRepository = documentRepository;
        this.permissionChecker = permissionChecker;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    public void requireReprocessAccess(UUID tenantId, UUID documentId) {
        requireAccess("REPROCESS_AI", tenantId, documentId, true);
    }

    public void requireRepairMemoryAccess(UUID tenantId, UUID documentId) {
        requireAccess("REPAIR_MEMORY", tenantId, documentId, false);
    }

    private void requireAccess(String action, UUID tenantId, UUID documentId, boolean receptionistAllowed) {
        RequestContext context = RequestContextHolder.require();
        String username = username(context);
        Set<String> authorities = currentAuthorities(context);

        if (tenantId == null) {
            logDecision(action, documentId, tenantId, username, authorities, false, "Missing tenant context");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing tenant context for document AI action");
        }

        ClinicalDocumentEntity document = documentRepository.findByTenantIdAndId(tenantId, documentId).orElse(null);
        if (document == null) {
            boolean existsElsewhere = documentRepository.existsById(documentId);
            String reason = existsElsewhere
                    ? "Selected tenant does not own this document"
                    : "Document not found";
            logDecision(action, documentId, tenantId, username, authorities, false, reason);
            if (existsElsewhere) {
                throw new ForbiddenException(primaryRole(context) + " is not authorized for document AI " + humanAction(action) + " because " + reason.toLowerCase(Locale.ROOT));
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }

        String denialReason = null;
        if (permissionChecker.hasAnyRole(Roles.CLINIC_ADMIN, Roles.TENANT_ADMIN)) {
            logDecision(action, documentId, tenantId, username, authorities, true, "Tenant admin role allowed");
            return;
        }
        if (permissionChecker.hasRole(Roles.PLATFORM_ADMIN)) {
            logDecision(action, documentId, tenantId, username, authorities, true, "Platform admin with tenant context allowed");
            return;
        }
        if (permissionChecker.hasRole(Roles.DOCTOR)) {
            try {
                if (document.getConsultationId() != null) {
                    doctorAssignmentSecurityService.requireConsultationAccess(tenantId, document.getConsultationId());
                } else {
                    doctorAssignmentSecurityService.requirePatientAccess(tenantId, document.getPatientId());
                }
                logDecision(action, documentId, tenantId, username, authorities, true, "Doctor assigned to consultation/patient");
                return;
            } catch (ResponseStatusException ex) {
                denialReason = ex.getReason() == null ? "Doctor is not assigned to this document" : ex.getReason();
            }
        } else if (permissionChecker.hasRole(Roles.RECEPTIONIST)) {
            if (receptionistAllowed) {
                logDecision(action, documentId, tenantId, username, authorities, true, "Receptionist allowed to reprocess AI");
                return;
            }
            denialReason = "Receptionist can retry AI processing but cannot repair clinical memory";
        }

        if (denialReason == null) {
            denialReason = "Required role is CLINIC_ADMIN, TENANT_ADMIN, PLATFORM_ADMIN, or assigned DOCTOR";
        }
        logDecision(action, documentId, tenantId, username, authorities, false, denialReason);
        throw new ForbiddenException(primaryRole(context) + " is not authorized for document AI " + humanAction(action) + " because " + denialReason);
    }

    private void logDecision(String action,
                             UUID documentId,
                             UUID tenantId,
                             String username,
                             Set<String> authorities,
                             boolean allowed,
                             String reason) {
        log.info("[AI-DOCUMENT-ACTION-AUTH] action={} documentId={} tenantId={} username={} authorities={} allowed={} reason={}",
                action,
                documentId,
                tenantId,
                username,
                authorities,
                allowed,
                reason);
    }

    private Set<String> currentAuthorities(RequestContext context) {
        Set<String> authorities = new LinkedHashSet<>();
        if (context != null) {
            addAuthority(authorities, context.tenantRole());
            if (context.tokenRoles() != null) {
                context.tokenRoles().forEach(role -> addAuthority(authorities, role));
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                addAuthority(authorities, authority == null ? null : authority.getAuthority());
            }
        }
        return authorities;
    }

    private String username(RequestContext context) {
        if (context == null || context.keycloakSub() == null || context.keycloakSub().isBlank()) {
            return "unknown";
        }
        return context.keycloakSub();
    }

    private String primaryRole(RequestContext context) {
        String role = context == null ? null : context.tenantRole();
        if (role == null || role.isBlank()) {
            Set<String> authorities = currentAuthorities(context);
            return authorities.isEmpty() ? "Current user" : authorities.iterator().next();
        }
        return normalize(role);
    }

    private String humanAction(String action) {
        return switch (action) {
            case "REPAIR_MEMORY" -> "repair";
            case "REPROCESS_AI" -> "reprocess";
            default -> action.toLowerCase(Locale.ROOT);
        };
    }

    private void addAuthority(Set<String> target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        target.add(normalize(value));
    }

    private String normalize(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }
}
