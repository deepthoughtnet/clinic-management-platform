package com.deepthoughtnet.clinic.platform.providerintegration.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "public_doctor_practice_platform_links",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_public_doctor_practice_platform_links_natural_key", columnNames = {
                        "public_doctor_reference",
                        "public_practice_reference",
                        "tenant_reference",
                        "platform_clinic_reference",
                        "tenant_doctor_user_reference"
                }),
                @UniqueConstraint(name = "uq_public_doctor_practice_platform_links_booking_reference", columnNames = {
                        "booking_reference"
                })
        }
)
public class PublicDoctorPracticePlatformLinkEntity extends AbstractProviderLinkEntity {

    public PublicDoctorPracticePlatformLinkEntity() {
    }

    @Column(name = "public_doctor_reference", nullable = false, length = 160)
    private String publicDoctorReference;

    @Column(name = "public_practice_reference", nullable = false, length = 160)
    private String publicPracticeReference;

    @Column(name = "tenant_doctor_user_reference", length = 160)
    private String tenantDoctorUserReference;

    @Column(name = "tenant_doctor_profile_reference", length = 160)
    private String tenantDoctorProfileReference;

    @Override
    public String naturalKey() {
        return String.join("|",
                value(publicDoctorReference),
                value(publicPracticeReference),
                value(getTenantReference()),
                value(getPlatformClinicReference()),
                value(tenantDoctorUserReference)
        );
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public String getPublicDoctorReference() {
        return publicDoctorReference;
    }

    public void setPublicDoctorReference(String publicDoctorReference) {
        this.publicDoctorReference = publicDoctorReference;
    }

    public String getPublicPracticeReference() {
        return publicPracticeReference;
    }

    public void setPublicPracticeReference(String publicPracticeReference) {
        this.publicPracticeReference = publicPracticeReference;
    }

    public String getTenantDoctorUserReference() {
        return tenantDoctorUserReference;
    }

    public void setTenantDoctorUserReference(String tenantDoctorUserReference) {
        this.tenantDoctorUserReference = tenantDoctorUserReference;
    }

    public String getTenantDoctorProfileReference() {
        return tenantDoctorProfileReference;
    }

    public void setTenantDoctorProfileReference(String tenantDoctorProfileReference) {
        this.tenantDoctorProfileReference = tenantDoctorProfileReference;
    }
}
