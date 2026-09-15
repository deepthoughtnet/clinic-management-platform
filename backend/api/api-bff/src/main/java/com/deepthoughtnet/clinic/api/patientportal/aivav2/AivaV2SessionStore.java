package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
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
    private final ConcurrentHashMap<String, PresentationMetadata> presentation = new ConcurrentHashMap<>();
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
        presentation.clear();
    }

    PresentationMetadata updatePresentation(String key, NormalizedUserTurn turn, Instant expiresAt) {
        Instant now = Instant.now(clock);
        PresentationMetadata old = presentation.get(key);
        if (old != null && (old.expiresAt() == null || old.expiresAt().isBefore(now))) {
            presentation.remove(key, old);
            old = null;
        }
        boolean noScriptSignal = turn.rawText().codePoints().noneMatch(Character::isLetter);
        PresentationMetadata current = noScriptSignal && old != null
                ? new PresentationMetadata(old.responseLanguage(), old.responseStyle(), expiresAt)
                : new PresentationMetadata(turn.responseLanguage(), turn.responseStyle(), expiresAt);
        presentation.put(key, current);
        return current;
    }

    record PresentationMetadata(String responseLanguage, ResponseStyle responseStyle, Instant expiresAt) { }
}
