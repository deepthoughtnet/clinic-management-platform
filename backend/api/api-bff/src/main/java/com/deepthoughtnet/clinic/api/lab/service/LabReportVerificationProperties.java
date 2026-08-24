package com.deepthoughtnet.clinic.api.lab.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinic.report.verification")
public class LabReportVerificationProperties {
    /**
     * Absolute public base URL used when rendering the published report verification QR/link.
     * Example: https://app.example.com
     */
    private String publicBaseUrl;

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
