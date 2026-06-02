package com.deepthoughtnet.clinic.api.patientportal;

import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalBillLineResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalBillResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalMeResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionMedicineResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionTestResponse;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillLineRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.core.errors.UnauthorizedException;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientPortalService {
    private final AppUserRepository appUserRepository;
    private final PatientRepository patientRepository;
    private final AppointmentService appointmentService;
    private final PrescriptionService prescriptionService;
    private final BillingService billingService;

    public PatientPortalService(
            AppUserRepository appUserRepository,
            PatientRepository patientRepository,
            AppointmentService appointmentService,
            PrescriptionService prescriptionService,
            BillingService billingService
    ) {
        this.appUserRepository = appUserRepository;
        this.patientRepository = patientRepository;
        this.appointmentService = appointmentService;
        this.prescriptionService = prescriptionService;
        this.billingService = billingService;
    }

    public PatientPortalMeResponse me() {
        PatientAccess access = requireCurrentPatientAccess();
        PatientEntity patient = access.patient();
        return new PatientPortalMeResponse(
                patient.getId().toString(),
                patient.getPatientNumber(),
                patient.getFirstName(),
                patient.getLastName(),
                fullName(patient.getFirstName(), patient.getLastName()),
                patient.getGender() == null ? null : patient.getGender().name(),
                patient.getDateOfBirth(),
                patient.getAgeYears(),
                patient.getMobile(),
                patient.getEmail(),
                patient.getBloodGroup(),
                patient.getAllergies(),
                patient.getExistingConditions(),
                patient.getLongTermMedications(),
                patient.getSurgicalHistory()
        );
    }

    public List<PatientPortalAppointmentResponse> appointments() {
        PatientAccess access = requireCurrentPatientAccess();
        return appointmentService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .map(record -> new PatientPortalAppointmentResponse(
                        record.id().toString(),
                        record.appointmentDate(),
                        record.appointmentTime(),
                        record.doctorName(),
                        record.reason(),
                        record.type() == null ? null : record.type().name(),
                        record.status() == null ? null : record.status().name(),
                        record.createdAt(),
                        record.updatedAt()
                ))
                .toList();
    }

    public List<PatientPortalPrescriptionResponse> prescriptions() {
        PatientAccess access = requireCurrentPatientAccess();
        return prescriptionService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .map(this::toPrescriptionResponse)
                .toList();
    }

    public List<PatientPortalBillResponse> bills() {
        PatientAccess access = requireCurrentPatientAccess();
        return billingService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .map(this::toBillResponse)
                .toList();
    }

    private PatientAccess requireCurrentPatientAccess() {
        var context = RequestContextHolder.require();
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = context.appUserId();
        if (appUserId == null) {
            throw new UnauthorizedException("Authenticated patient context is missing");
        }

        AppUserEntity appUser = appUserRepository.findByTenantIdAndId(tenantId, appUserId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated patient account was not found"));
        UUID patientId = appUser.getPatientId();
        if (patientId == null) {
            throw new ForbiddenException("Patient portal access is not configured for this account");
        }

        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, patientId)
                .filter(PatientEntity::isActive)
                .orElseThrow(() -> new ForbiddenException("Patient portal access is not configured for this tenant"));
        return new PatientAccess(tenantId, patient);
    }

    private PatientPortalPrescriptionResponse toPrescriptionResponse(PrescriptionRecord record) {
        return new PatientPortalPrescriptionResponse(
                record.id().toString(),
                record.prescriptionNumber(),
                record.versionNumber(),
                record.doctorName(),
                record.diagnosisSnapshot(),
                record.advice(),
                record.followUpDate(),
                record.status() == null ? null : record.status().name(),
                record.finalizedAt(),
                record.createdAt(),
                record.updatedAt(),
                record.medicines().stream().map(this::toPrescriptionMedicineResponse).toList(),
                record.recommendedTests().stream().map(this::toPrescriptionTestResponse).toList()
        );
    }

    private PatientPortalPrescriptionMedicineResponse toPrescriptionMedicineResponse(PrescriptionMedicineRecord record) {
        return new PatientPortalPrescriptionMedicineResponse(
                record.medicineName(),
                record.medicineType() == null ? null : record.medicineType().name(),
                record.strength(),
                record.dosage(),
                record.frequency(),
                record.duration(),
                record.timing() == null ? null : record.timing().name(),
                record.instructions(),
                record.sortOrder()
        );
    }

    private PatientPortalPrescriptionTestResponse toPrescriptionTestResponse(PrescriptionTestRecord record) {
        return new PatientPortalPrescriptionTestResponse(
                record.testName(),
                record.instructions(),
                record.sortOrder()
        );
    }

    private PatientPortalBillResponse toBillResponse(BillRecord record) {
        return new PatientPortalBillResponse(
                record.id().toString(),
                record.billNumber(),
                record.billDate(),
                record.status() == null ? null : record.status().name(),
                record.subtotalAmount(),
                record.discountAmount(),
                record.taxAmount(),
                record.totalAmount(),
                record.paidAmount(),
                record.refundedAmount(),
                record.dueAmount(),
                record.createdAt(),
                record.updatedAt(),
                record.lines().stream().map(this::toBillLineResponse).toList()
        );
    }

    private PatientPortalBillLineResponse toBillLineResponse(BillLineRecord record) {
        return new PatientPortalBillLineResponse(
                record.itemType() == null ? null : record.itemType().name(),
                record.itemName(),
                record.quantity(),
                record.unitPrice(),
                record.totalPrice(),
                record.lineDiscountAmount(),
                record.sortOrder()
        );
    }

    private String fullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private record PatientAccess(UUID tenantId, PatientEntity patient) {
    }
}
