package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalEntityResolverTest {
    private final PatientPortalCareAiEntityRegistry entityRegistry = new PatientPortalCareAiEntityRegistry();
    private final DoctorResolver doctorResolver = new DoctorResolver(entityRegistry);
    private final ClinicResolver clinicResolver = new ClinicResolver(entityRegistry);
    private final ServiceResolver serviceResolver = new ServiceResolver(entityRegistry);
    private final LocationResolver locationResolver = new LocationResolver(entityRegistry);
    private final SelectionResolver selectionResolver = new SelectionResolver();

    @Test
    void doctorResolverHandlesExactAliasPartialAmbiguousAndUnresolvedMatches() {
        List<CanonicalEntityCandidate> candidates = List.of(
                new CanonicalEntityCandidate("doctor-akshu", "Dr Akshu Kumar", "Dr Akshu Kumar · General Medicine · Jeevanam Automation Lab", List.of("UAT doctor", "Akshu")),
                new CanonicalEntityCandidate("doctor-ashish", "Dr Ashish Kumar", "Dr Ashish Kumar · General Medicine · Demo Clinic", List.of("Ashish")),
                new CanonicalEntityCandidate("doctor-neha", "Dr Neha Mehta", "Dr Neha Mehta · Cardiology · Demo Clinic", List.of("Neha"))
        );

        assertThat(doctorResolver.resolve("Doc Akshu Kumar", candidates, null).resolved()).isTrue();
        assertThat(doctorResolver.resolve("Akshu", candidates, null).canonicalValue()).isEqualTo("Dr Akshu Kumar");
        assertThat(doctorResolver.resolve("the UAT doctor", candidates, null).canonicalValue()).isEqualTo("Dr Akshu Kumar");
        assertThat(doctorResolver.resolve("Dr A", candidates, null).status()).isEqualTo(CanonicalResolutionStatus.AMBIGUOUS);
        assertThat(doctorResolver.resolve("quantum doctor", candidates, null).status()).isEqualTo(CanonicalResolutionStatus.UNRESOLVED);
    }

    @Test
    void clinicResolverHandlesExactAliasPartialAmbiguousAndUnresolvedMatches() {
        List<CanonicalEntityCandidate> candidates = List.of(
                new CanonicalEntityCandidate("clinic-baner", "Jeevanam Automation Lab", "Jeevanam Automation Lab · Baner · Pune", List.of("Baner", "Pune")),
                new CanonicalEntityCandidate("clinic-kharadi", "Demo Clinic", "Demo Clinic · Kharadi · Pune", List.of("Kharadi", "Pune")),
                new CanonicalEntityCandidate("clinic-mumbai", "Sunrise Clinic", "Sunrise Clinic · Mumbai", List.of("Mumbai"))
        );

        assertThat(clinicResolver.resolve("Demo Clinic", candidates, null).canonicalValue()).isEqualTo("Demo Clinic");
        assertThat(clinicResolver.resolve("Baner", candidates, null).canonicalValue()).isEqualTo("Jeevanam Automation Lab");
        assertThat(clinicResolver.resolve("Pune clinic", candidates, null).status()).isEqualTo(CanonicalResolutionStatus.AMBIGUOUS);
        assertThat(clinicResolver.resolve("Quantum Clinic", candidates, null).status()).isEqualTo(CanonicalResolutionStatus.UNRESOLVED);
    }

    @Test
    void serviceResolverHandlesCatalogAliasesAndRejectsUnsupportedValues() {
        List<CanonicalEntityCandidate> candidates = List.of(
                new CanonicalEntityCandidate("service-consultation", "consultation", "Consultation", List.of("consultation")),
                new CanonicalEntityCandidate("service-health-check", "health check", "Health Check", List.of("health check")),
                new CanonicalEntityCandidate("service-teleconsultation", "teleconsultation", "Teleconsultation", List.of("teleconsultation"))
        );

        assertThat(serviceResolver.resolve("consultation", candidates, null).canonicalValue()).isEqualTo("consultation");
        assertThat(serviceResolver.resolve("teleconsultation", candidates, null).canonicalValue()).isEqualTo("teleconsultation");
        assertThat(serviceResolver.resolve("tele", candidates, null).canonicalValue()).isEqualTo("teleconsultation");
        assertThat(serviceResolver.resolve("surgery", candidates, null).status()).isEqualTo(CanonicalResolutionStatus.UNRESOLVED);
    }

    @Test
    void locationResolverHandlesCityAreaAndLocalityCandidates() {
        List<CanonicalEntityCandidate> candidates = List.of(
                new CanonicalEntityCandidate("city-pune", "Pune", "Pune", List.of("Pune")),
                new CanonicalEntityCandidate("area-baner", "Baner", "Baner", List.of("Baner")),
                new CanonicalEntityCandidate("area-kharadi", "Kharadi", "Kharadi", List.of("Kharadi"))
        );

        assertThat(locationResolver.resolve("Pune", candidates, null).canonicalValue()).isEqualTo("Pune");
        assertThat(locationResolver.resolve("near Kharadi", candidates, null).canonicalValue()).isEqualTo("Kharadi");
        assertThat(locationResolver.resolve("Ban", candidates, null).canonicalValue()).isEqualTo("Baner");
        assertThat(locationResolver.resolve("quantum locality", candidates, null).status()).isEqualTo(CanonicalResolutionStatus.UNRESOLVED);
    }

    @Test
    void selectionResolverUsesCurrentCandidatesNotGlobalData() {
        List<CanonicalEntityCandidate> doctorCandidates = List.of(
                new CanonicalEntityCandidate("doctor-akshu", "Dr Akshu Kumar", "Dr Akshu Kumar", List.of()),
                new CanonicalEntityCandidate("doctor-neha", "Dr Neha Mehta", "Dr Neha Mehta", List.of()),
                new CanonicalEntityCandidate("doctor-ashish", "Dr Ashish Kumar", "Dr Ashish Kumar", List.of())
        );
        List<CanonicalEntityCandidate> slotCandidates = List.of(
                new CanonicalEntityCandidate("09:00", "09:00", "09:00", List.of("09:00")),
                new CanonicalEntityCandidate("10:30", "10:30", "10:30", List.of("10:30")),
                new CanonicalEntityCandidate("11:00", "11:00", "11:00", List.of("11:00"))
        );

        CanonicalResolution secondDoctor = selectionResolver.resolve("second doctor", doctorCandidates, null, new CanonicalResolverSupport()::normalize);
        CanonicalResolution thatDoctor = selectionResolver.resolve("that doctor", doctorCandidates, "doctor-akshu", new CanonicalResolverSupport()::normalize);
        CanonicalResolution lastSlot = selectionResolver.resolve("last slot", slotCandidates, null, new CanonicalResolverSupport()::normalize);

        assertThat(secondDoctor.resolved()).isTrue();
        assertThat(secondDoctor.canonicalValue()).isEqualTo("Dr Neha Mehta");
        assertThat(thatDoctor.resolved()).isTrue();
        assertThat(thatDoctor.canonicalValue()).isEqualTo("Dr Akshu Kumar");
        assertThat(lastSlot.canonicalValue()).isEqualTo("11:00");
    }
}
