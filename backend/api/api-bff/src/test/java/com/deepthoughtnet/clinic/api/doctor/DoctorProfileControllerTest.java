package com.deepthoughtnet.clinic.api.doctor;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.doctor.dto.DoctorProfileResponse;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfilePhotoRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DoctorProfileControllerTest {
    private DoctorProfileService doctorProfileService;
    private TenantUserManagementService tenantUserManagementService;
    private MockMvc mockMvc;
    private UUID tenantId;
    private UUID doctorUserId;

    @BeforeEach
    void setUp() {
        doctorProfileService = mock(DoctorProfileService.class);
        tenantUserManagementService = mock(TenantUserManagementService.class);
        DoctorProfileController controller = new DoctorProfileController(doctorProfileService, tenantUserManagementService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        tenantId = UUID.randomUUID();
        doctorUserId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), doctorUserId, "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(new TenantUserRecord(
                doctorUserId,
                tenantId,
                "kc-sub",
                "doctor@example.test",
                "doctor-user",
                "Laboratory",
                "Amit Verma",
                "ACTIVE",
                "DOCTOR",
                "ACTIVE",
                "DOC-001",
                "9999999999",
                null,
                OffsetDateTime.parse("2026-07-06T04:00:00Z"),
                OffsetDateTime.parse("2026-07-06T04:00:00Z"),
                "READY"
        )));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void getProfileReturnsPhotoMetadata() throws Exception {
        when(doctorProfileService.findByDoctorUserIdWithPhotoRepair(tenantId, doctorUserId)).thenReturn(java.util.Optional.of(profile()));

        mockMvc.perform(get("/api/doctors/{doctorUserId}/profile", doctorUserId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"photoUrl\":\"/api/doctors/" + doctorUserId + "/photo?v=1751774400000\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"photoSizeBytes\":2048")));
    }

    @Test
    void photoStreamsStoredBytes() throws Exception {
        when(doctorProfileService.downloadPhoto(tenantId, doctorUserId)).thenReturn(new DoctorProfilePhotoRecord(
                "profile.webp",
                "image/webp",
                4L,
                new byte[] {1, 2, 3, 4}
        ));

        mockMvc.perform(get("/api/doctors/{doctorUserId}/photo", doctorUserId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("profile.webp")))
                .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));
    }

    private DoctorProfileRecord profile() {
        return new DoctorProfileRecord(
                UUID.randomUUID(),
                tenantId,
                doctorUserId,
                "9999999999",
                "General Physician",
                List.of("General Physician"),
                "MBBS, MD",
                "MMC-2026-10001",
                "Room 2",
                BigDecimal.valueOf(600),
                BigDecimal.valueOf(600),
                null,
                null,
                12,
                45,
                true,
                true,
                "dr-amit-verma",
                "/api/doctors/" + doctorUserId + "/photo?v=1751774400000",
                "profile.webp",
                "image/webp",
                2048L,
                OffsetDateTime.parse("2026-07-06T04:00:00Z"),
                OffsetDateTime.parse("2026-07-06T04:00:00Z")
        );
    }
}
