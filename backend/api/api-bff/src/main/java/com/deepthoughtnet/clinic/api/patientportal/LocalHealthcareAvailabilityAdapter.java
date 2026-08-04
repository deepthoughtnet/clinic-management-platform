package com.deepthoughtnet.clinic.api.patientportal;

import com.deepthoughtnet.clinic.api.appointment.AppointmentTimingRules;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingSlotSummary;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.port.HealthcareAvailabilityPort;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.port.PlatformConnectionPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocalHealthcareAvailabilityAdapter implements HealthcareAvailabilityPort {
    private final PlatformConnectionPort platformConnectionPort;
    private final AppointmentService appointmentService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;

    public LocalHealthcareAvailabilityAdapter(
            PlatformConnectionPort platformConnectionPort,
            AppointmentService appointmentService,
            ClinicTimeZoneResolver clinicTimeZoneResolver
    ) {
        this.platformConnectionPort = platformConnectionPort;
        this.appointmentService = appointmentService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
    }

    @Override
    public Optional<BookingTargetResolution> resolveBookingTarget(BookingTargetReference bookingTargetReference) {
        return platformConnectionPort.findActivePracticeLink(bookingTargetReference);
    }

    @Override
    public AvailabilityState getAvailabilityState(BookingTargetReference bookingTargetReference) {
        Optional<BookingTargetResolution> resolution = resolveBookingTarget(bookingTargetReference);
        if (resolution.isEmpty() || resolution.get().tenantDoctorUserReference() == null) {
            return AvailabilityState.UNKNOWN;
        }
        BookingTargetResolution target = resolution.get();
        ZoneId zone = resolveTenantZone(target.tenantReference());
        List<DoctorAvailabilitySlotRecord> todaySlots = appointmentService.listSlots(
                tenantId(target.tenantReference()),
                doctorUserId(target.tenantDoctorUserReference()),
                LocalDate.now(zone),
                zone
        );
        ZonedDateTime now = ZonedDateTime.now(zone);
        boolean todayAvailable = todaySlots.stream()
                .anyMatch(slot -> AppointmentTimingRules.isSlotBookableForPatient(slot.appointmentDate(), slot.slotTime(), zone, now) && slot.selectable());
        if (todayAvailable) {
            return AvailabilityState.AVAILABLE_TODAY;
        }
        if (nextSevenDaysHaveBookableSlot(target, zone, now)) {
            return AvailabilityState.NEXT_AVAILABLE;
        }
        return AvailabilityState.NO_SLOTS_IN_RANGE;
    }

    @Override
    public List<BookingSlotSummary> getBookableSlots(BookingTargetReference bookingTargetReference, LocalDate date) {
        Optional<BookingTargetResolution> resolution = resolveBookingTarget(bookingTargetReference);
        if (resolution.isEmpty() || resolution.get().tenantDoctorUserReference() == null || date == null) {
            return List.of();
        }
        BookingTargetResolution target = resolution.get();
        ZoneId zone = resolveTenantZone(target.tenantReference());
        ZonedDateTime now = ZonedDateTime.now(zone);
        return appointmentService.listSlots(
                        tenantId(target.tenantReference()),
                        doctorUserId(target.tenantDoctorUserReference()),
                        date,
                        zone
                ).stream()
                .filter(slot -> AppointmentTimingRules.isSlotBookableForPatient(slot.appointmentDate(), slot.slotTime(), zone, now))
                .filter(DoctorAvailabilitySlotRecord::selectable)
                .map(slot -> new BookingSlotSummary(
                        slotId(target, slot),
                        LocalDateTime.of(slot.appointmentDate(), slot.slotTime()),
                        slot.slotEndTime() == null ? null : LocalDateTime.of(slot.appointmentDate(), slot.slotEndTime()),
                        slot.slotTime().toString(),
                        true
                ))
                .toList();
    }

    private boolean nextSevenDaysHaveBookableSlot(BookingTargetResolution target, ZoneId zone, ZonedDateTime now) {
        for (int offset = 1; offset <= 7; offset++) {
            LocalDate date = now.toLocalDate().plusDays(offset);
            List<DoctorAvailabilitySlotRecord> slots = appointmentService.listSlots(
                    tenantId(target.tenantReference()),
                    doctorUserId(target.tenantDoctorUserReference()),
                    date,
                    zone
            );
            boolean available = slots.stream()
                    .filter(slot -> AppointmentTimingRules.isSlotBookableForPatient(slot.appointmentDate(), slot.slotTime(), zone, now))
                    .anyMatch(DoctorAvailabilitySlotRecord::selectable);
            if (available) {
                return true;
            }
        }
        return false;
    }

    private UUID tenantId(String tenantReference) {
        if (!StringUtils.hasText(tenantReference)) {
            return null;
        }
        return UUID.fromString(tenantReference.trim());
    }

    private UUID doctorUserId(String doctorUserReference) {
        if (!StringUtils.hasText(doctorUserReference)) {
            return null;
        }
        return UUID.fromString(doctorUserReference.trim());
    }

    private ZoneId resolveTenantZone(String tenantReference) {
        UUID tenantId = tenantId(tenantReference);
        return tenantId == null ? ZoneId.of("Asia/Kolkata") : clinicTimeZoneResolver.resolve(tenantId);
    }

    private String slotId(BookingTargetResolution target, DoctorAvailabilitySlotRecord slot) {
        return String.join(":",
                target.bookingTargetReference() == null ? "" : target.bookingTargetReference().opaqueBookingReference(),
                slot.appointmentDate().toString(),
                slot.slotTime().toString()
        );
    }
}
