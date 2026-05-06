package com.deepthoughtnet.clinic.api.reliability.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", indexes = {
        @Index(name = "ix_idempotency_keys_tenant_created", columnList = "tenant_id,created_at")
})
public class IdempotencyKeyEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 256)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "response_json", columnDefinition = "text")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyKeyEntity() {
    }

    public static IdempotencyKeyEntity create(UUID tenantId, String key, String requestHash, String responseJson) {
        IdempotencyKeyEntity e = new IdempotencyKeyEntity();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.key = key;
        e.requestHash = requestHash;
        e.responseJson = responseJson;
        e.createdAt = OffsetDateTime.now();
        return e;
    }

    public UUID getTenantId() { return tenantId; }
    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public String getResponseJson() { return responseJson; }
}
