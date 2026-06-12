package com.deepthoughtnet.clinic.api.clinic;

import com.deepthoughtnet.clinic.api.clinic.dto.ClinicClockResponse;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinic")
public class ClinicClockController {
    private static final Logger log = LoggerFactory.getLogger(ClinicClockController.class);
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;

    public ClinicClockController(ClinicTimeZoneResolver clinicTimeZoneResolver) {
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
    }

    @GetMapping("/clock")
    @PreAuthorize("isAuthenticated()")
    public ClinicClockResponse clock() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        String tenantRole = RequestContextHolder.require().tenantRole();
        ZoneId clinicZone = clinicTimeZoneResolver.resolve(tenantId);
        Instant now = Instant.now();
        OffsetDateTime clinicNow = now.atZone(clinicZone).toOffsetDateTime();
        OffsetDateTime serverNowUtc = now.atOffset(ZoneOffset.UTC);
        log.info("clinic.clock tenantId={} role={} clinicTimezone={} clinicNow={} serverNowUtc={}",
                tenantId,
                tenantRole,
                clinicZone.getId(),
                clinicNow,
                serverNowUtc);
        return new ClinicClockResponse(clinicZone.getId(), clinicNow, serverNowUtc);
    }
}
