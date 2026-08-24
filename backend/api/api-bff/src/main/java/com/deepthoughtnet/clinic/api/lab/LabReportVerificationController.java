package com.deepthoughtnet.clinic.api.lab;

import com.deepthoughtnet.clinic.api.lab.dto.LabReportVerificationResponse;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/lab/reports")
public class LabReportVerificationController {
    private final LabService labService;

    public LabReportVerificationController(LabService labService) {
        this.labService = labService;
    }

    @GetMapping("/{verificationToken}/verify")
    public LabReportVerificationResponse verify(@PathVariable String verificationToken) {
        return labService.verifyPublishedReport(verificationToken);
    }
}
