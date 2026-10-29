package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class AivaV2SessionStoreReadFailureTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void unavailableRedisReadIsNotReportedAsMissingAndDoesNotDeleteSession() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn((HashOperations) hashes);
        when(hashes.get(anyString(), eq("expiresAt")))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC(), redis,
                new ObjectMapper().findAndRegisterModules(), "redis");

        assertThatThrownBy(() -> store.find("VOICE|tenant-a|call-a"))
                .isInstanceOf(AivaV2SessionStore.SessionStoreUnavailableException.class);
        verify(redis, never()).delete(anyString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void corruptRedisSessionIsDistinctFromMissingAndRetainedForRecovery() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn((HashOperations) hashes);
        when(hashes.get(anyString(), eq("expiresAt")))
                .thenReturn(Long.toString(System.currentTimeMillis() + 60_000));
        when(hashes.get(anyString(), eq("state"))).thenReturn("not-json");
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC(), redis,
                new ObjectMapper().findAndRegisterModules(), "redis");

        assertThatThrownBy(() -> store.find("VOICE|tenant-a|call-a"))
                .isInstanceOf(AivaV2SessionStore.SessionStoreCorruptException.class);
        verify(redis, never()).delete(anyString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void absentRedisSessionRemainsAnOrdinaryNotFound() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn((HashOperations) hashes);
        when(hashes.get(anyString(), eq("expiresAt"))).thenReturn(null);
        when(redis.hasKey(anyString())).thenReturn(false);
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC(), redis,
                new ObjectMapper().findAndRegisterModules(), "redis");

        assertThat(store.find("VOICE|tenant-a|call-a")).isEmpty();
    }
}
