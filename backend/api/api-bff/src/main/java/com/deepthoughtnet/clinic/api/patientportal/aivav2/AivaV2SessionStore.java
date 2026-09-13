package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class AivaV2SessionStore {
    private final ConcurrentHashMap<String, SessionProjection> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    AivaV2SessionStore() {
        this(Clock.systemUTC());
    }

    AivaV2SessionStore(Clock clock) {
        this.clock = clock;
    }

    Optional<SessionProjection> find(String key) {
        SessionProjection value = sessions.get(key);
        if (value != null && value.expiresAt().isBefore(Instant.now(clock))) {
            sessions.remove(key, value);
            return Optional.empty();
        }
        return Optional.ofNullable(value);
    }

    SessionProjection update(String key, Function<SessionProjection, SessionProjection> updater) {
        return sessions.compute(key, (ignored, current) -> updater.apply(current));
    }

    void clear() {
        sessions.clear();
    }
}
