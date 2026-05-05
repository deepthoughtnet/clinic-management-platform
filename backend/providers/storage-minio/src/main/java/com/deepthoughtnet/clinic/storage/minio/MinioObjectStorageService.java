package com.deepthoughtnet.clinic.storage.minio;

import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MinioObjectStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorageService.class);

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;
    private volatile boolean bucketChecked = false;

    public MinioObjectStorageService(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public String buildDocumentStorageKey(UUID tenantId, String originalFilename) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String year = String.format(Locale.ROOT, "%04d", now.getYear());
        String month = String.format(Locale.ROOT, "%02d", now.getMonthValue());
        String safeFilename = sanitizeFilename(originalFilename);

        return "tenants/%s/documents/%s/%s/%s-%s".formatted(
                tenantId,
                year,
                month,
                UUID.randomUUID(),
                safeFilename
        );
    }

    @Override
    public void putObject(String storageKey, String contentType, byte[] bytes) {
        ensureBucketExists();

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(storageKey)
                            .stream(in, (long) bytes.length, -1L)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store object in MinIO", ex);
        }
    }

    @Override
    public byte[] getObjectBytes(String storageKey) {
        ensureBucketExists();

        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(storageKey)
                        .build()
        )) {
            return in.readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read object from MinIO", ex);
        }
    }

    @Override
    public void deleteObjectQuietly(String storageKey) {
        try {
            ensureBucketExists();
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(storageKey)
                            .build()
            );
        } catch (Exception ex) {
            log.warn(
                    "Failed to delete object from MinIO during cleanup. bucket={}, key={}",
                    properties.getBucket(),
                    storageKey,
                    ex
            );
        }
    }

    @Override
    public String generatePresignedDownloadUrl(String storageKey, Duration ttl) {
        ensureBucketExists();

        try {
            int expirySeconds = (int) ttl.getSeconds();

            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Http.Method.GET)
                            .bucket(properties.getBucket())
                            .object(storageKey)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed generating presigned URL", ex);
        }
    }

    private void ensureBucketExists() {
        if (bucketChecked) {
            return;
        }

        synchronized (this) {
            if (bucketChecked) {
                return;
            }

            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder()
                                .bucket(properties.getBucket())
                                .build()
                );

                if (!exists) {
                    if (!properties.isAutoCreateBucket()) {
                        throw new IllegalStateException("MinIO bucket does not exist: " + properties.getBucket());
                    }

                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(properties.getBucket())
                                    .build()
                    );
                }

                bucketChecked = true;
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to initialize MinIO bucket: " + properties.getBucket(), ex);
            }
        }
    }

    private String sanitizeFilename(String value) {
        String source = (value == null || value.isBlank()) ? "file" : value.trim();
        String sanitized = source.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.length() > 180 ? sanitized.substring(sanitized.length() - 180) : sanitized;
    }
}
