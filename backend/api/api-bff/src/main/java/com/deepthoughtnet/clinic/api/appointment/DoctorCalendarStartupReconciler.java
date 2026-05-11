package com.deepthoughtnet.clinic.api.appointment;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DoctorCalendarStartupReconciler {
    private static final Logger log = LoggerFactory.getLogger(DoctorCalendarStartupReconciler.class);

    private final AppointmentService appointmentService;
    private final TenantRepository tenantRepository;

    public DoctorCalendarStartupReconciler(AppointmentService appointmentService, TenantRepository tenantRepository) {
        this.appointmentService = appointmentService;
        this.tenantRepository = tenantRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        tenantRepository.findAllByOrderByCreatedAtDesc().forEach(tenant -> {
            if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
                return;
            }
            var result = appointmentService.reconcileDoctorCalendars(tenant.getId(), null, "startup.reconcile");
            log.info(
                    "Startup doctor calendar reconcile finished. tenantId={}, createdCount={}, skippedCount={}",
                    result.tenantId(),
                    result.createdCount(),
                    result.skippedCount()
            );
        });
    }
}
