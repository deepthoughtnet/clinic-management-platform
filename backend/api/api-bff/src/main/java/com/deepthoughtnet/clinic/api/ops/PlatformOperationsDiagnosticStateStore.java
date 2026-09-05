package com.deepthoughtnet.clinic.api.ops;

import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthStatus;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsDiagnosticComponent;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsDiagnosticResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class PlatformOperationsDiagnosticStateStore {
    private final ConcurrentMap<PlatformOperationsDiagnosticComponent, PlatformOperationsDiagnosticResponse> latestByComponent = new ConcurrentHashMap<>();

    public void record(PlatformOperationsDiagnosticResponse response) {
        PlatformOperationsDiagnosticComponent component = PlatformOperationsDiagnosticComponent.fromAny(response == null ? null : response.component());
        if (component != null && response != null) {
            latestByComponent.put(component, response);
        }
    }

    public Optional<PlatformOperationsDiagnosticResponse> latest(PlatformOperationsDiagnosticComponent component) {
        return Optional.ofNullable(latestByComponent.get(component));
    }

    public ComponentHealthResponse overlay(ComponentHealthResponse passive) {
        if (passive == null) {
            return null;
        }
        PlatformOperationsDiagnosticComponent component = PlatformOperationsDiagnosticComponent.fromAny(passive.component());
        if (component == null || passive.status() != ComponentHealthStatus.UNKNOWN) {
            return passive;
        }
        PlatformOperationsDiagnosticResponse latest = latestByComponent.get(component);
        if (latest == null) {
            return passive;
        }
        Map<String, Object> details = latest.details() == null ? Map.of() : new LinkedHashMap<>(latest.details());
        Instant testedAt = latest.testedAt();
        Instant lastSuccessAt = latest.success() ? testedAt : null;
        Instant lastFailureAt = latest.success() ? null : testedAt;
        return new ComponentHealthResponse(
                passive.component(),
                latest.status() == null ? ComponentHealthStatus.UNKNOWN : latest.status(),
                latest.message() == null || latest.message().isBlank() ? passive.reason() : latest.message(),
                testedAt == null ? passive.lastCheckedAt() : testedAt,
                lastSuccessAt,
                lastFailureAt,
                latest.latencyMs(),
                details
        );
    }
}
