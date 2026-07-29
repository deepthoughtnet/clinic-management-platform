package com.deepthoughtnet.clinic.discover.onboarding.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_locations")
public class ProviderLocationEntity {
    @Id
    private UUID id;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(length = 128)
    private String label;
    @Column(nullable = false, length = 512)
    private String address;
    @Column(nullable = false, length = 128)
    private String city;
    @Column(nullable = false, length = 128)
    private String state;
    @Column(nullable = false, length = 128)
    private String country;
    @Column(name = "pin_code", nullable = false, length = 32)
    private String pinCode;
    @Column(name = "working_hours", length = 512)
    private String workingHours;
    @Column(name = "parking_available", nullable = false)
    private boolean parkingAvailable;
    @Column(name = "accessibility_available", nullable = false)
    private boolean accessibilityAvailable;
    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;
    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    protected ProviderLocationEntity() {
    }

    public ProviderLocationEntity(UUID id, UUID providerId, String label, String address, String city, String state, String country, String pinCode, String workingHours, boolean parkingAvailable, boolean accessibilityAvailable, BigDecimal latitude, BigDecimal longitude) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.providerId = providerId;
        this.label = label;
        this.address = address;
        this.city = city;
        this.state = state;
        this.country = country;
        this.pinCode = pinCode;
        this.workingHours = workingHours;
        this.parkingAvailable = parkingAvailable;
        this.accessibilityAvailable = accessibilityAvailable;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public String getLabel() { return label; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getCountry() { return country; }
    public String getPinCode() { return pinCode; }
    public String getWorkingHours() { return workingHours; }
    public boolean isParkingAvailable() { return parkingAvailable; }
    public boolean isAccessibilityAvailable() { return accessibilityAvailable; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
}
