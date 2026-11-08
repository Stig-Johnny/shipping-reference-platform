package io.github.stigjohnny.shipping.http;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The application's clock in tests: fixed at an instant that a test sets, so that issue and expiry instants are exact
 * and a test can move past an expiry. It replaces ShippingApplication's system clock as the primary Clock bean.
 */
public final class TestClock extends Clock {

    /** The contract's example instant, 2026-11-10T09:00:00.000Z. */
    public static final Instant START = Instant.parse("2026-11-10T09:00:00.000Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);

    /** Sets the instant the application sees from now on. */
    public void set(Instant instant) {
        now.set(Objects.requireNonNull(instant, "instant"));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("The test clock is in UTC only.");
    }

    /** Registers the test clock as the primary Clock bean. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }
}
