package com.deepthoughtnet.clinic.api.clinicalintake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicalintake.dto.ClinicalIntakeResponse;
import com.deepthoughtnet.clinic.api.clinicalintake.service.ClinicalIntakeService;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ClinicalIntakeControllerTest {
    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void latestReturnsNoContentWhenNoIntakeMatches() {
        ClinicalIntakeService service = mock(ClinicalIntakeService.class);
        DoctorAssignmentSecurityService securityService = mock(DoctorAssignmentSecurityService.class);
        ClinicalIntakeController controller = new ClinicalIntakeController(service, securityService);
        UUID patientId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        when(service.latest(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(patientId), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(Optional.empty());

        ResponseEntity<ClinicalIntakeResponse> response = controller.latest(patientId, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }
}
