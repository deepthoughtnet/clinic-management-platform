package com.deepthoughtnet.clinic.carepilot.webinar.analytics;

import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates webinar-level operational and engagement metrics. */
@Service
public class WebinarAnalyticsService {
    private final WebinarRepository webinarRepository;
    private final WebinarRegistrationRepository registrationRepository;
    private final LeadRepository leadRepository;

    public WebinarAnalyticsService(
            WebinarRepository webinarRepository,
            WebinarRegistrationRepository registrationRepository,
            LeadRepository leadRepository
    ) {
        this.webinarRepository = webinarRepository;
        this.registrationRepository = registrationRepository;
        this.leadRepository = leadRepository;
    }

    @Transactional(readOnly = true)
    public WebinarAnalyticsRecord summary(UUID tenantId) {
        long totalWebinars = webinarRepository.countByTenantId(tenantId);
        long upcoming = webinarRepository.findByTenantIdAndStatusInAndScheduledStartAtBetween(
                tenantId,
                java.util.List.of(WebinarStatus.SCHEDULED, WebinarStatus.LIVE),
                OffsetDateTime.now().minusYears(1),
                OffsetDateTime.now().plusYears(1)
        ).stream().filter(w -> w.getScheduledStartAt().isAfter(OffsetDateTime.now())).count();
        long completed = webinarRepository.countByTenantIdAndStatus(tenantId, WebinarStatus.COMPLETED);

        long totalRegs = registrationRepository.countByTenantId(tenantId);
        long attended = registrationRepository.countByTenantIdAndAttendedTrue(tenantId);
        long noShow = registrationRepository.countByTenantIdAndRegistrationStatus(tenantId, WebinarRegistrationStatus.NO_SHOW);
        double attendanceRate = totalRegs == 0 ? 0D : (attended * 100D) / totalRegs;
        double noShowRate = totalRegs == 0 ? 0D : (noShow * 100D) / totalRegs;

        var sourceMap = registrationRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.groupingBy(r -> r.getSource().name(), Collectors.counting()));

        return new WebinarAnalyticsRecord(
                totalWebinars,
                upcoming,
                completed,
                totalRegs,
                attended,
                noShow,
                attendanceRate,
                noShowRate,
                leadRepository.countByTenantIdAndConvertedPatientIdIsNotNullAndBookedAppointmentIdIsNotNull(tenantId),
                sourceMap,
                attended
        );
    }
}
