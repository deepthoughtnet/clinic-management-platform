package com.deepthoughtnet.clinic.platform.spring.lock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * PostgreSQL advisory lock implementation.
 *
 * <p>Locks are held per DB session/connection and released explicitly when callback completes.
 */
@Service
public class PostgresAdvisoryLockService implements DistributedLockService {
    private static final Logger log = LoggerFactory.getLogger(PostgresAdvisoryLockService.class);

    private final DataSource dataSource;
    private final Duration pollInterval;

    public PostgresAdvisoryLockService(
            DataSource dataSource,
            @Value("${platform.locks.poll-interval:PT0.2S}") Duration pollInterval
    ) {
        this.dataSource = dataSource;
        this.pollInterval = pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()
                ? Duration.ofMillis(200)
                : pollInterval;
    }

    @Override
    public boolean executeWithLock(String lockKey, Duration waitTimeout, Supplier<Void> callback) {
        Duration effectiveWait = (waitTimeout == null || waitTimeout.isNegative()) ? Duration.ZERO : waitTimeout;
        Instant deadline = Instant.now().plus(effectiveWait);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            long advisoryKey = hashToLong(lockKey);
            while (true) {
                if (tryAcquire(connection, advisoryKey)) {
                    try {
                        callback.get();
                        return true;
                    } finally {
                        release(connection, advisoryKey);
                    }
                }
                if (Instant.now().isAfter(deadline)) {
                    return false;
                }
                Thread.sleep(Math.max(10L, pollInterval.toMillis()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            log.warn("Distributed lock failure. lockKey={}, reason={}", lockKey, ex.getMessage());
            return false;
        }
    }

    private boolean tryAcquire(Connection connection, long advisoryKey) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            ps.setLong(1, advisoryKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private void release(Connection connection, long advisoryKey) {
        try (PreparedStatement ps = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            ps.setLong(1, advisoryKey);
            ps.executeQuery();
        } catch (SQLException ex) {
            log.warn("Advisory lock release failed. key={}, reason={}", advisoryKey, ex.getMessage());
        }
    }

    private long hashToLong(String value) {
        if (value == null) {
            return 0L;
        }
        long h = 1125899906842597L;
        for (int i = 0; i < value.length(); i++) {
            h = 31 * h + value.charAt(i);
        }
        return h;
    }
}
