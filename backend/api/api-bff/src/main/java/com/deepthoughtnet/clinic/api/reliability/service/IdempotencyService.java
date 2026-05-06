package com.deepthoughtnet.clinic.api.reliability.service;

import com.deepthoughtnet.clinic.api.reliability.db.IdempotencyKeyEntity;
import com.deepthoughtnet.clinic.api.reliability.db.IdempotencyKeyRepository;
import com.deepthoughtnet.clinic.platform.core.errors.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {
    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<String> findCachedResponse(UUID tenantId, String idempotencyKey, String requestBody) {
        if (tenantId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String requestHash = hash(requestBody == null ? "" : requestBody);
        return repository.findByTenantIdAndKey(tenantId, idempotencyKey)
                .map(entity -> {
                    if (!requestHash.equals(entity.getRequestHash())) {
                        throw new BadRequestException("Idempotency key reused with different payload");
                    }
                    return entity.getResponseJson();
                });
    }

    @Transactional
    public void storeResponse(UUID tenantId, String idempotencyKey, String requestBody, String responseJson) {
        if (tenantId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        String requestHash = hash(requestBody == null ? "" : requestBody);
        repository.findByTenantIdAndKey(tenantId, idempotencyKey)
                .ifPresentOrElse(existing -> {
                    if (!requestHash.equals(existing.getRequestHash())) {
                        throw new BadRequestException("Idempotency key reused with different payload");
                    }
                }, () -> repository.save(IdempotencyKeyEntity.create(tenantId, idempotencyKey, requestHash, responseJson)));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
