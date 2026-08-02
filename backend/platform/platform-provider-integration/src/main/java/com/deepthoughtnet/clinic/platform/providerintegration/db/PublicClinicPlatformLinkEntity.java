package com.deepthoughtnet.clinic.platform.providerintegration.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "public_clinic_platform_links",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_public_clinic_platform_links_natural_key", columnNames = {
                        "public_clinic_reference",
                        "tenant_reference",
                        "platform_clinic_reference"
                }),
                @UniqueConstraint(name = "uq_public_clinic_platform_links_booking_reference", columnNames = {
                        "booking_reference"
                })
        }
)
public class PublicClinicPlatformLinkEntity extends AbstractProviderLinkEntity {

    public PublicClinicPlatformLinkEntity() {
    }

    @Column(name = "public_clinic_reference", nullable = false, length = 160)
    private String publicClinicReference;

    @Override
    public String naturalKey() {
        return String.join("|",
                value(publicClinicReference),
                value(getTenantReference()),
                value(getPlatformClinicReference())
        );
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public String getPublicClinicReference() {
        return publicClinicReference;
    }

    public void setPublicClinicReference(String publicClinicReference) {
        this.publicClinicReference = publicClinicReference;
    }
}
