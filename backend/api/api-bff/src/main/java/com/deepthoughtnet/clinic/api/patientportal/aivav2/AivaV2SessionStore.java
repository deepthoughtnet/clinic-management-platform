package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class AivaV2SessionStore {
    private static final Logger log = LoggerFactory.getLogger(AivaV2SessionStore.class);
    private static final int EXPIRED_CLEANUP_BATCH_SIZE = 32;
    private static final int RECENT_TURN_HISTORY_SIZE = 16;
    private static final String REDIS_KEY_PREFIX = "aiva:v2:session:";
    private static final String SCHEMA_VERSION = "aiva-v2-session-v1";
    private static final Duration RESERVATION_LEASE = Duration.ofSeconds(90);
    private static final DefaultRedisScript<List> ADMIT_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local initialExpiry = tonumber(ARGV[2])
            local leaseUntil = tonumber(ARGV[3])
            local clientTurnId = ARGV[4]
            local sequence = ARGV[5]
            local protocolTurn = ARGV[6] == '1'
            local reservationId = ARGV[7]
            local exists = redis.call('EXISTS', key)
            if exists == 1 then
              local expiresAt = tonumber(redis.call('HGET', key, 'expiresAt'))
              if expiresAt == nil or expiresAt <= now then
                redis.call('DEL', key)
                if protocolTurn then return {'CONVERSATION_EXPIRED'} end
                exists = 0
              end
            end
            if exists == 0 then
              if protocolTurn and tonumber(sequence) ~= 1 then return {'SEQUENCE_GAP', '0'} end
              redis.call('HSET', key, 'schemaVersion', 'aiva-v2-session-v1',
                'expiresAt', initialExpiry, 'lastSequence', '0', 'version', '0',
                'reservationId', reservationId, 'reservationClientTurnId', clientTurnId,
                'reservationSequence', sequence, 'reservationLeaseUntil', leaseUntil)
              redis.call('PEXPIRE', key, math.max(1, initialExpiry - now))
              return {'ACCEPT', '', '', '0', tostring(initialExpiry), reservationId}
            end
            local receipt = redis.call('HGET', key, 'receipt:' .. clientTurnId)
            local lastSequence = tonumber(redis.call('HGET', key, 'lastSequence') or '0')
            if receipt ~= false and receipt ~= nil then
              return {'DUPLICATE_REPLAY', receipt, tostring(lastSequence)}
            end
            local currentReservation = redis.call('HGET', key, 'reservationId')
            if currentReservation ~= false and currentReservation ~= nil then
              local currentLease = tonumber(redis.call('HGET', key, 'reservationLeaseUntil') or '0')
              local reservedClient = redis.call('HGET', key, 'reservationClientTurnId')
              if currentLease > now or reservedClient ~= clientTurnId then
                return {'IN_FLIGHT', tostring(lastSequence)}
              end
              redis.call('HDEL', key, 'reservationId', 'reservationClientTurnId',
                'reservationSequence', 'reservationLeaseUntil')
            end
            if protocolTurn and tonumber(sequence) <= lastSequence then
              return {'STALE_TURN', tostring(lastSequence)}
            end
            if protocolTurn and tonumber(sequence) ~= lastSequence + 1 then
              return {'SEQUENCE_GAP', tostring(lastSequence)}
            end
            redis.call('HSET', key, 'reservationId', reservationId,
              'reservationClientTurnId', clientTurnId, 'reservationSequence', sequence,
              'reservationLeaseUntil', leaseUntil)
            redis.call('PEXPIRE', key, math.max(1, tonumber(redis.call('HGET', key, 'expiresAt')) - now))
            return {'ACCEPT', redis.call('HGET', key, 'state') or '',
              redis.call('HGET', key, 'presentation') or '', tostring(lastSequence),
              redis.call('HGET', key, 'expiresAt'), reservationId}
            """, List.class);
    private static final DefaultRedisScript<List> FINALIZE_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local reservationId = ARGV[2]
            local state = ARGV[3]
            local presentation = ARGV[4]
            local clientTurnId = ARGV[5]
            local sequence = ARGV[6]
            local response = ARGV[7]
            local protocolTurn = ARGV[8] == '1'
            if redis.call('HGET', key, 'reservationId') ~= reservationId then return {'CONFLICT'} end
            local expiresAt = tonumber(redis.call('HGET', key, 'expiresAt'))
            if expiresAt == nil or expiresAt <= now then
              redis.call('DEL', key)
              return {'EXPIRED'}
            end
            redis.call('HSET', key, 'schemaVersion', 'aiva-v2-session-v1', 'state', state,
              'presentation', presentation, 'version',
              tostring(tonumber(redis.call('HGET', key, 'version') or '0') + 1))
            if protocolTurn then
              redis.call('HSET', key, 'lastSequence', sequence, 'receipt:' .. clientTurnId, response)
              local order = redis.call('HGET', key, 'receiptOrder') or ''
              order = order == '' and clientTurnId or order .. ',' .. clientTurnId
              local ids = {}
              for id in string.gmatch(order, '[^,]+') do table.insert(ids, id) end
              while #ids > 16 do
                redis.call('HDEL', key, 'receipt:' .. table.remove(ids, 1))
              end
              redis.call('HSET', key, 'receiptOrder', table.concat(ids, ','))
            end
            redis.call('HDEL', key, 'reservationId', 'reservationClientTurnId',
              'reservationSequence', 'reservationLeaseUntil')
            redis.call('PEXPIRE', key, math.max(1, expiresAt - now))
            return {'OK', tostring(expiresAt)}
            """, List.class);
    private static final DefaultRedisScript<List> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'reservationId') == ARGV[1] then
              redis.call('HDEL', KEYS[1], 'reservationId', 'reservationClientTurnId',
                'reservationSequence', 'reservationLeaseUntil')
              return {'RELEASED'}
            end
            return {'IGNORED'}
            """, List.class);
    private final ConcurrentHashMap<String, SessionProjection> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PresentationMetadata> presentation = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TurnHistory> turnHistory = new ConcurrentHashMap<>();
    private final Clock clock;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final boolean distributed;
    private final ThreadLocal<DistributedTurnContext> activeDistributedTurn = new ThreadLocal<>();

    @Autowired
    AivaV2SessionStore(ObjectProvider<StringRedisTemplate> redisProvider,
                       ObjectMapper objectMapper,
                       @Value("${clinic.ai.aiva-v2.session-store:redis}") String sessionStoreMode) {
        this(Clock.systemUTC(), redisProvider.getIfAvailable(), objectMapper, sessionStoreMode);
    }

    AivaV2SessionStore(Clock clock) {
        this(clock, null, null, "memory");
    }

    AivaV2SessionStore(Clock clock, StringRedisTemplate redis, ObjectMapper objectMapper, String sessionStoreMode) {
        this.clock = clock;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.distributed = "redis".equalsIgnoreCase(sessionStoreMode);
        if (distributed && (redis == null || objectMapper == null)) {
            throw new IllegalStateException("AIVA V2 Redis session store requires Redis and ObjectMapper beans");
        }
        log.info("AIVA_V2_SESSION_STORE type={} reservationLeaseSeconds={}",
                distributed ? "redis" : "memory", RESERVATION_LEASE.toSeconds());
    }

    Optional<SessionProjection> find(String key) {
        if (distributed) return findRedis(key);
        Instant now = Instant.now(clock);
        SessionProjection value = sessions.get(key);
        if (isExpired(value, now)) {
            removeExpired(key, value);
            value = null;
        }
        cleanupExpired(now, EXPIRED_CLEANUP_BATCH_SIZE);
        return Optional.ofNullable(value);
    }

    /** Returns the authoritative accepted-turn sequence for a resumed voice conversation. */
    long lastAcceptedSequence(String key) {
        if (distributed) {
            try {
                String redisKey = redisKey(key);
                Object expiryValue = redis.opsForHash().get(redisKey, "expiresAt");
                long expiresAt = expiryValue == null ? 0L : Long.parseLong(expiryValue.toString());
                if (expiresAt <= Instant.now(clock).toEpochMilli()) {
                    redis.delete(redisKey);
                    return 0L;
                }
                Object sequence = redis.opsForHash().get(redisKey, "lastSequence");
                return sequence == null ? 0L : Long.parseLong(sequence.toString());
            } catch (RuntimeException ex) {
                log.warn("AIVA_V2_SESSION_STORE sequence read failed safely. keyHash={}", safeKeyHash(key));
                throw new IllegalStateException("AIVA V2 session state unavailable", ex);
            }
        }
        Instant now = Instant.now(clock);
        SessionProjection value = sessions.get(key);
        if (isExpired(value, now)) {
            removeExpired(key, value);
            return 0L;
        }
        return turnHistory.getOrDefault(key, TurnHistory.empty()).lastAcceptedSequence();
    }

    SessionProjection update(String key, Function<SessionProjection, SessionProjection> updater) {
        if (distributed) {
            TurnExchange exchange = updateTurnRedis(key, null, null,
                    current -> new TurnExecution(updater.apply(current), null));
            return findRedis(key).orElse(null);
        }
        Instant now = Instant.now(clock);
        SessionProjection updated = sessions.compute(key, (ignored, current) -> {
            if (isExpired(current, now)) {
                presentation.remove(key);
                turnHistory.remove(key);
                current = null;
            }
            return updater.apply(current);
        });
        cleanupExpired(now, EXPIRED_CLEANUP_BATCH_SIZE);
        return updated;
    }

    TurnExchange updateTurn(String key, String clientTurnId, Long clientTurnSequence,
                            Function<SessionProjection, TurnExecution> updater) {
        if (distributed) return updateTurnRedis(key, clientTurnId, clientTurnSequence, updater);
        Instant now = Instant.now(clock);
        java.util.concurrent.atomic.AtomicReference<TurnExchange> exchange = new java.util.concurrent.atomic.AtomicReference<>();
        boolean protocolTurn = clientTurnId != null && clientTurnSequence != null;
        sessions.compute(key, (ignored, current) -> {
            if (isExpired(current, now)) {
                presentation.remove(key);
                turnHistory.remove(key);
                if (protocolTurn) {
                    exchange.set(new TurnExchange(TurnDisposition.CONVERSATION_EXPIRED, null, 0));
                    return null;
                }
                current = null;
            }
            if (!protocolTurn) {
                TurnExecution execution = updater.apply(current);
                exchange.set(new TurnExchange(TurnDisposition.ACCEPTED, execution.response(), 0));
                return execution.session();
            }
            TurnHistory history = turnHistory.getOrDefault(key, TurnHistory.empty());
            TurnReceipt duplicate = history.receipts().stream()
                    .filter(receipt -> clientTurnId.equals(receipt.clientTurnId()))
                    .findFirst().orElse(null);
            if (duplicate != null) {
                exchange.set(new TurnExchange(TurnDisposition.DUPLICATE_REPLAY, duplicate.response(), history.lastAcceptedSequence()));
                return current;
            }
            if (clientTurnSequence <= history.lastAcceptedSequence()) {
                exchange.set(new TurnExchange(TurnDisposition.STALE_TURN, null, history.lastAcceptedSequence()));
                return current;
            }
            if (clientTurnSequence != history.lastAcceptedSequence() + 1) {
                exchange.set(new TurnExchange(TurnDisposition.SEQUENCE_GAP, null, history.lastAcceptedSequence()));
                return current;
            }
            TurnExecution execution = updater.apply(current);
            turnHistory.put(key, history.accept(clientTurnId, clientTurnSequence, execution.response()));
            exchange.set(new TurnExchange(TurnDisposition.ACCEPTED, execution.response(), clientTurnSequence));
            return execution.session();
        });
        cleanupExpired(now, EXPIRED_CLEANUP_BATCH_SIZE);
        return exchange.get();
    }

    void clear() {
        if (distributed) return;
        sessions.clear();
        presentation.clear();
        turnHistory.clear();
    }

    PresentationMetadata updatePresentation(String key, NormalizedUserTurn turn, Instant expiresAt) {
        DistributedTurnContext context = activeDistributedTurn.get();
        if (distributed && context != null && context.key().equals(key)) {
            Instant now = Instant.now(clock);
            PresentationMetadata old = context.presentation();
            if (old != null && (old.expiresAt() == null || !old.expiresAt().isAfter(now))) old = null;
            boolean noScriptSignal = turn.rawText().codePoints().noneMatch(Character::isLetter);
            PresentationMetadata current = noScriptSignal && old != null
                    ? new PresentationMetadata(old.responseLanguage(), old.responseStyle(), expiresAt)
                    : new PresentationMetadata(turn.responseLanguage(), turn.responseStyle(), expiresAt);
            context.setPresentation(current);
            return current;
        }
        if (distributed) throw new IllegalStateException("AIVA V2 presentation update is outside a distributed turn");
        Instant now = Instant.now(clock);
        PresentationMetadata old = presentation.get(key);
        if (old != null && (old.expiresAt() == null || !old.expiresAt().isAfter(now))) {
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

    private boolean isExpired(SessionProjection session, Instant now) {
        return session != null && (session.expiresAt() == null || !session.expiresAt().isAfter(now));
    }

    private void removeExpired(String key, SessionProjection session) {
        if (session == null || sessions.remove(key, session)) {
            presentation.remove(key);
            turnHistory.remove(key);
        }
    }

    private void cleanupExpired(Instant now, int limit) {
        int removed = 0;
        for (var entry : sessions.entrySet()) {
            if (removed >= limit) break;
            if (isExpired(entry.getValue(), now) && sessions.remove(entry.getKey(), entry.getValue())) {
                presentation.remove(entry.getKey());
                turnHistory.remove(entry.getKey());
                removed++;
            }
        }
    }

    private Optional<SessionProjection> findRedis(String key) {
        try {
            String redisKey = redisKey(key);
            Object expiryValue = redis.opsForHash().get(redisKey, "expiresAt");
            String expiry = expiryValue == null ? null : expiryValue.toString();
            if (expiry == null || Long.parseLong(expiry) <= Instant.now(clock).toEpochMilli()) {
                redis.delete(redisKey);
                return Optional.empty();
            }
            Object state = redis.opsForHash().get(redisKey, "state");
            return state == null || state.toString().isBlank()
                    ? Optional.empty() : Optional.of(read(state.toString(), SessionProjection.class));
        } catch (RuntimeException ex) {
            log.warn("AIVA_V2_SESSION_STORE read failed; state rejected safely. keyHash={}", safeKeyHash(key));
            redis.delete(redisKey(key));
            return Optional.empty();
        }
    }

    private TurnExchange updateTurnRedis(String key, String clientTurnId, Long clientTurnSequence,
                                          Function<SessionProjection, TurnExecution> updater) {
        boolean protocolTurn = clientTurnId != null && clientTurnSequence != null;
        String effectiveClientTurnId = protocolTurn ? clientTurnId : "legacy-" + UUID.randomUUID();
        long now = Instant.now(clock).toEpochMilli();
        long initialExpiry = now + Duration.ofMinutes(30).toMillis();
        String reservationId = UUID.randomUUID().toString();
        List<?> admission = redis.execute(ADMIT_SCRIPT, List.of(redisKey(key)),
                Long.toString(now), Long.toString(initialExpiry),
                Long.toString(now + RESERVATION_LEASE.toMillis()), effectiveClientTurnId,
                protocolTurn ? Long.toString(clientTurnSequence) : "0", protocolTurn ? "1" : "0", reservationId);
        if (admission == null || admission.isEmpty()) throw new IllegalStateException("AIVA V2 session admission unavailable");
        String disposition = String.valueOf(admission.get(0));
        if ("DUPLICATE_REPLAY".equals(disposition)) {
            return new TurnExchange(TurnDisposition.DUPLICATE_REPLAY,
                    read(String.valueOf(admission.get(1)), MessageResponse.class), number(admission, 2));
        }
        if (!"ACCEPT".equals(disposition)) {
            TurnDisposition mapped = switch (disposition) {
                case "CONVERSATION_EXPIRED" -> TurnDisposition.CONVERSATION_EXPIRED;
                case "STALE_TURN" -> TurnDisposition.STALE_TURN;
                case "SEQUENCE_GAP" -> TurnDisposition.SEQUENCE_GAP;
                case "IN_FLIGHT" -> TurnDisposition.IN_FLIGHT;
                default -> TurnDisposition.IN_FLIGHT;
            };
            return new TurnExchange(mapped, null, number(admission, 1));
        }
        try {
            SessionProjection current = blank(admission, 1) ? null : read(String.valueOf(admission.get(1)), SessionProjection.class);
            PresentationMetadata presentationMetadata = blank(admission, 2)
                    ? null : read(String.valueOf(admission.get(2)), PresentationMetadata.class);
            activeDistributedTurn.set(new DistributedTurnContext(key, presentationMetadata));
            TurnExecution execution = updater.apply(current);
            DistributedTurnContext context = activeDistributedTurn.get();
            String result = finalizeRedis(key, reservationId, effectiveClientTurnId, protocolTurn,
                    clientTurnSequence == null ? 0 : clientTurnSequence, execution,
                    context == null ? null : context.presentation());
            if (!"OK".equals(result)) throw new IllegalStateException("AIVA V2 session finalization failed: " + result);
            return new TurnExchange(TurnDisposition.ACCEPTED, execution.response(),
                    protocolTurn ? clientTurnSequence : 0);
        } catch (RuntimeException ex) {
            releaseRedis(key, reservationId);
            throw ex;
        } finally {
            activeDistributedTurn.remove();
        }
    }

    private String finalizeRedis(String key, String reservationId, String clientTurnId, boolean protocolTurn,
                                 long sequence, TurnExecution execution, PresentationMetadata presentationMetadata) {
        return first(redis.execute(FINALIZE_SCRIPT, List.of(redisKey(key)),
                Long.toString(Instant.now(clock).toEpochMilli()), reservationId,
                write(execution.session()), write(presentationMetadata), clientTurnId,
                Long.toString(sequence), write(execution.response()), protocolTurn ? "1" : "0"));
    }

    private void releaseRedis(String key, String reservationId) {
        try { redis.execute(RELEASE_SCRIPT, List.of(redisKey(key)), reservationId); }
        catch (RuntimeException ignored) { }
    }

    private String redisKey(String key) { return REDIS_KEY_PREFIX + key; }

    private boolean blank(List<?> values, int index) {
        return values.size() <= index || values.get(index) == null || values.get(index).toString().isBlank();
    }

    private long number(List<?> values, int index) {
        return values.size() <= index || values.get(index) == null ? 0 : Long.parseLong(values.get(index).toString());
    }

    private String first(List<?> values) {
        return values == null || values.isEmpty() ? "" : String.valueOf(values.get(0));
    }

    private <T> T read(String value, Class<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid AIVA V2 session payload", ex); }
    }

    private String write(Object value) {
        try { return value == null ? "" : objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("AIVA V2 session payload is not serializable", ex); }
    }

    private String safeKeyHash(String key) { return Integer.toHexString(key == null ? 0 : key.hashCode()); }

    private static final class DistributedTurnContext {
        private final String key;
        private PresentationMetadata presentation;

        private DistributedTurnContext(String key, PresentationMetadata presentation) {
            this.key = key;
            this.presentation = presentation;
        }

        private String key() { return key; }
        private PresentationMetadata presentation() { return presentation; }
        private void setPresentation(PresentationMetadata value) { presentation = value; }
    }

    record PresentationMetadata(String responseLanguage, ResponseStyle responseStyle, Instant expiresAt) { }

    enum TurnDisposition { ACCEPTED, DUPLICATE_REPLAY, STALE_TURN, SEQUENCE_GAP, CONVERSATION_EXPIRED, IN_FLIGHT }

    record TurnExecution(SessionProjection session, MessageResponse response) { }

    record TurnExchange(TurnDisposition disposition, MessageResponse response, long lastAcceptedSequence) { }

    private record TurnReceipt(String clientTurnId, long sequence, MessageResponse response) { }

    private record TurnHistory(long lastAcceptedSequence, java.util.List<TurnReceipt> receipts) {
        static TurnHistory empty() { return new TurnHistory(0, java.util.List.of()); }

        TurnHistory accept(String clientTurnId, long sequence, MessageResponse response) {
            java.util.ArrayList<TurnReceipt> updated = new java.util.ArrayList<>(receipts);
            updated.add(new TurnReceipt(clientTurnId, sequence, response));
            if (updated.size() > RECENT_TURN_HISTORY_SIZE) {
                updated.subList(0, updated.size() - RECENT_TURN_HISTORY_SIZE).clear();
            }
            return new TurnHistory(sequence, java.util.List.copyOf(updated));
        }
    }
}
