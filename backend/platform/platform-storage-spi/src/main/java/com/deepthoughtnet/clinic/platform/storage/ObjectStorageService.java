package com.deepthoughtnet.clinic.platform.storage;

import java.time.Duration;
import java.util.UUID;

public interface ObjectStorageService {

    String buildDocumentStorageKey(UUID tenantId, String originalFilename);

    void putObject(String storageKey, String contentType, byte[] bytes);

    byte[] getObjectBytes(String storageKey);

    void deleteObjectQuietly(String storageKey);

    String generatePresignedDownloadUrl(String storageKey, Duration ttl);
}