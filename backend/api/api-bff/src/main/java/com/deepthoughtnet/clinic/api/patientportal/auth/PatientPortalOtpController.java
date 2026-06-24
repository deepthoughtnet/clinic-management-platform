package com.deepthoughtnet.clinic.api.patientportal.auth;

import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpRequestRequest;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpRequestResponse;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpVerifyRequest;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpVerifyResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-portal/auth/otp")
public class PatientPortalOtpController {
    private final PatientPortalOtpService patientPortalOtpService;

    public PatientPortalOtpController(PatientPortalOtpService patientPortalOtpService) {
        this.patientPortalOtpService = patientPortalOtpService;
    }

    @PostMapping("/request")
    public PatientPortalOtpRequestResponse requestOtp(@Valid @RequestBody PatientPortalOtpRequestRequest request) {
        return patientPortalOtpService.requestOtp(request.mobile(), request.context());
    }

    @PostMapping("/verify")
    public PatientPortalOtpVerifyResponse verifyOtp(@Valid @RequestBody PatientPortalOtpVerifyRequest request) {
        return patientPortalOtpService.verifyOtp(request.mobile(), request.otp(), request.context());
    }
}
